"""
Routes for the Demeter Dalle Service, handling DALL-E image generation and product updates.
"""

import sys
import os
import io
import json
from flask import request
import urllib.request
from flask_cors import cross_origin  # Removed CORS as it's handled in __init__
import logging
import time
from PIL import Image, ImageDraw, ImageFilter
from . import app, utils, database

from google.cloud import storage, secretmanager
import google_crc32c
from openai import OpenAI
import httpx

# --- Constants ---
GOOGLE_PROJECT_ID = os.environ.get("GOOGLE_PROJECT_ID", "69816494671")
OPENAI_SECRET_NAME = os.environ.get("OPENAI_SECRET_NAME", "heph2-openai")
OPENAI_SECRET_VERSION = os.environ.get("OPENAI_SECRET_VERSION", "latest")
SECRET_RESOURCE_NAME_FORMAT = (
    "projects/{project_id}/secrets/{secret_name}/versions/{version}"
)

OPENAI_IMAGE_MODEL = "dall-e-2"
DEFAULT_IMAGE_SIZE = "512x512"
DEFAULT_DALLE_QUALITY = "standard"
DALLE_THUMB_SIZE = (400, 400)
MAX_IMAGES_PER_REQUEST = 4

GCS_URL_FORMAT = "https://storage.googleapis.com/{bucket_name}/{blob_name}"


# Logger setup (ensure it's configured once)
logger = logging.getLogger(__name__)
if not logger.handlers:
    logger.setLevel(logging.DEBUG)
    handler = logging.StreamHandler(sys.stdout)
    handler.setLevel(logging.DEBUG)
    formatter = logging.Formatter(
        "%(asctime)s - %(name)s - %(levelname)s - %(message)s"
    )
    handler.setFormatter(formatter)
    logger.addHandler(handler)

# Google Cloud clients
storage_client = storage.Client()
demeter_bucket_name = "stantonious-demeter"
demeter_bucket = storage_client.bucket(demeter_bucket_name)
demeter_plants_bucket_name = "stantonious-demeter-plants"
demeter_plants_bucket = storage_client.bucket(demeter_plants_bucket_name)
# feyrle_bucket = storage_client.bucket(db.feyrle_bucket_name)


# --- Helper Functions ---
def _get_openai_api_key():
    """Fetches the OpenAI API key from Secret Manager."""
    try:
        sm_client = secretmanager.SecretManagerServiceClient()
        secret_name = SECRET_RESOURCE_NAME_FORMAT.format(
            project_id=GOOGLE_PROJECT_ID,
            secret_name=OPENAI_SECRET_NAME,
            version=OPENAI_SECRET_VERSION,
        )
        response = sm_client.access_secret_version(request={"name": secret_name})

        crc32c = google_crc32c.Checksum()
        crc32c.update(response.payload.data)
        if response.payload.data_crc32c != int(crc32c.hexdigest(), 16):
            logger.error("OpenAI API Key: Data corruption detected.")
            return None
        return response.payload.data.decode("UTF-8")
    except Exception as e:
        logger.error(f"Failed to retrieve OpenAI API key: {e}", exc_info=True)
        return None


def _construct_gcs_url(bucket_name, blob_name):
    """Constructs a public GCS URL."""
    # Ensure blob_name doesn't have a leading slash if bucket_name is already just the bucket.
    if blob_name.startswith("/"):
        blob_name = blob_name[1:]
    return GCS_URL_FORMAT.format(bucket_name=bucket_name, blob_name=blob_name)


@app.route("/demeter/plant/suggest", methods=["GET"])
@cross_origin()
def suggest_plant():
    """
    Suggests plant types based on environmental parameters.
    """
    try:
        # Extract parameters from request
        n_mgkg = request.args.get("n_mgkg", default=0, type=float)
        p_mgkg = request.args.get("p_mgkg", default=0, type=float)
        k_mgkg = request.args.get("k_mgkg", default=0, type=float)
        ph = request.args.get("ph", default=7.0, type=float)
        moisture = request.args.get("moisture", default=50, type=float)
        sun_intensity = request.args.get("sun_intensity", default=50000, type=float)
        lat = request.args.get("lat", default=0.0, type=float)
        lon = request.args.get("lon", default=0.0, type=float)
        plant_type = request.args.get("plant_type", default="tbd", type=str)
        max_plants = request.args.get("max_plants", default=3, type=int)

        # Generate prompt
        prompt = utils.generate_plant_prompt(
            n_mgkg,
            p_mgkg,
            k_mgkg,
            ph,
            moisture,
            sun_intensity,
            lat,
            lon,
            plant_type,
            max_plants,
        )

        # Get API key and client
        api_key = _get_openai_api_key()
        if not api_key:
            return json.dumps({"success": False, "reason": "Missing API key."}), 500

        oai_client = OpenAI(api_key=api_key)

        # Call OpenAI
        completion = oai_client.chat.completions.create(
            model="gpt-3.5-turbo",
            messages=[
                {"role": "system", "content": "You are a helpful assistant."},
                {"role": "user", "content": prompt},
            ],
        )
        response = completion.choices[0].message.content

        # Format and return response
        suggestions = [s.strip() for s in response.strip().split("\n") if s.strip()]
        return json.dumps({"success": True, "suggestions": suggestions}), 200

    except Exception as e:
        logger.error(f"Error in plant suggestion: {e}", exc_info=True)
        return (
            json.dumps({"success": False, "reason": "An internal error occurred."}),
            500,
        )


@app.route("/demeter/data/types", methods=["GET"])
@cross_origin()
def plant_types():
    return json.dumps({"success": True, "types": database.PLANT_TYPES}), 200


@app.route("/demeter/data/characteristics", methods=["GET"])
@cross_origin()
def plant_characteristics():
    return json.dumps({"success": True, "types": database.PLANT_CHARACTERISTICS}), 200


@app.route("/demeter/data/maturity", methods=["GET"])
@cross_origin()
def plant_maturity():
    return json.dumps({"success": True, "types": database.PLANT_MATURITY}), 200


def _create_plant_image(plant_name, plant_type):
    prompt = (
        f"A single, well-trimmed {plant_name} {plant_type} isolated on a white background,"
        "top-down soft lighting, no shadows, high-resolution botanical detail, suitable "
        "for compositing into landscape or garden scenes, realistic texture and leaf structure,"
        "centered composition"
    )
    api_key = _get_openai_api_key()
    if not api_key:
        return json.dumps({"success": False, "reason": "Missing API key."}), 500
    oai_client = OpenAI(api_key=api_key)
    response = oai_client.images.generate(
        model="dall-e-3", prompt=prompt, n=1, size="1024x1024", quality="standard"
    )
    dalle_url = response.data[0].url
    logger.debug(f"Fetching DALL-E image from URL: {dalle_url}")

    with urllib.request.urlopen(dalle_url) as img_response:
        img_bytes = img_response.read()

    img_name = f"{plant_name}.png"
    asset_uri = _construct_gcs_url(demeter_plants_bucket_name, img_name)

    blob_full = demeter_plants_bucket.blob(img_name)
    blob_full.upload_from_string(img_bytes, content_type="image/png")
    return database.save_plant_data_fb(
        plant_name, plant_prompt=prompt, plant_type=plant_type,uri=asset_uri
    )


@app.route("/demeter/plant/img", methods=["GET"])
@cross_origin()
def plant_image():
    plant_name = request.args.get("plant_name", type=str)
    plant_type = request.args.get("plant_type", type=str)

    doc = database.get_plant_rec(plant_name)

    if doc is None:
        doc = _create_plant_image(plant_name,plant_type)

    return (
        json.dumps({"success": True, "assetURI": doc['uri']}),
        200,
        {"ContentType": "application/json"},
    )
    




@app.route("/demeter/plant/feasibility", methods=["GET"])
@cross_origin()
def plant_feasibility():
    """
    Provides a detailed feasibility analysis for growing a specific plant.
    """
    try:
        # Extract parameters from request
        plant_type = request.args.get("plant_type", type=str)
        if not plant_type:
            return (
                json.dumps(
                    {"success": False, "reason": "Missing 'plant_type' parameter."}
                ),
                400,
            )

        n_mgkg = request.args.get("n_mgkg", default=0, type=float)
        p_mgkg = request.args.get("p_mgkg", default=0, type=float)
        k_mgkg = request.args.get("k_mgkg", default=0, type=float)
        ph = request.args.get("ph", default=7.0, type=float)
        moisture = request.args.get("moisture", default=50, type=float)
        sun_intensity = request.args.get("sun_intensity", default=50000, type=float)
        lat = request.args.get("lat", default=0.0, type=float)
        lon = request.args.get("lon", default=0.0, type=float)

        # Generate prompt
        prompt = utils.generate_feasibility_prompt(
            plant_type, n_mgkg, p_mgkg, k_mgkg, ph, moisture, sun_intensity, lat, lon
        )

        # Get API key and client
        api_key = _get_openai_api_key()
        if not api_key:
            return json.dumps({"success": False, "reason": "Missing API key."}), 500

        oai_client = OpenAI(api_key=api_key)

        # Call OpenAI
        completion = oai_client.chat.completions.create(
            model="gpt-3.5-turbo",
            response_format={"type": "json_object"},
            messages=[
                {
                    "role": "system",
                    "content": "You are a helpful assistant designed to output JSON.",
                },
                {"role": "user", "content": prompt},
            ],
        )
        response_data = json.loads(completion.choices[0].message.content)

        return json.dumps({"success": True, "feasibility": response_data}), 200

    except Exception as e:
        logger.error(f"Error in plant feasibility: {e}", exc_info=True)
        return (
            json.dumps({"success": False, "reason": "An internal error occurred."}),
            500,
        )


@app.route("/demeter/product/create", methods=["POST"])
@cross_origin()
def create_dalle():
    def _decode_aois(aois_list):
        return [tuple(map(int, _n.split(","))) for _n in aois_list]

    def _get_bb(x, y, s):
        return int(x - s / 2), int(y - s / 2), int(x + s / 2), int(y + s / 2)

    def _scale_aoi(x, y, x_scale, y_scale):
        return int(x * x_scale), int(y * y_scale)

    print("args", request.args)
    aois = _decode_aois(request.args.getlist("aois"))
    print("aois", aois)
    plant_names = request.args.getlist("plant_name")
    plant_type = request.args.get("plant_type")
    sub_type = request.args.get("sub_type")
    age = request.args.get("age")
    mask_size = int(request.args.get("mask_size"))
    productID_base = str(int(time.time()))

    api_key = _get_openai_api_key()
    oai_client = OpenAI(api_key=api_key)

    if request.method == "POST":
        if "scene_img" not in request.files:
            return (
                json.dumps({"success": False, "reason": "No file provided."}),
                500,
                {"ContentType": "application/json"},
            )

        in_image = (
            Image.open(request.files["scene_img"]).convert("RGBA").resize((512, 512))
        )
        print("image size", in_image.size)
        x_scale = 512 / in_image.size[0]
        y_scale = 512 / in_image.size[1]

        try:
            cur_img = in_image
            for idx, aoi in enumerate(aois):
                scaled_aoi = _scale_aoi(*aoi, x_scale, y_scale)
                bb = _get_bb(*scaled_aoi, mask_size)
                print(f"AOI {idx}: Bounding box {bb}")

                # Create binary alpha mask: transparent where we want edits, opaque elsewhere
                mask = Image.new("L", cur_img.size, 255)
                draw = ImageDraw.Draw(mask)
                draw.rectangle(bb, fill=0)

                # Convert to RGBA with alpha channel
                rgba_mask = Image.new("RGBA", cur_img.size)
                rgba_mask.putalpha(mask)

                # Prepare image and mask bytes
                img_bytes = io.BytesIO()
                cur_img.save(img_bytes, format="PNG")
                img_bytes.seek(0)
                img_bytes.name = "image.png"

                mask_bytes = io.BytesIO()
                rgba_mask.save(mask_bytes, format="PNG")
                mask_bytes.seek(0)
                mask_bytes.name = "mask.png"

                if plant_names[idx] in plant_names[:idx]:
                    prompt = (
                        f"Inpaint another {plant_names[idx]} {plant_type} plant of the same type as already in the image, "
                        f"This plant is a {sub_type} and is {age}. "
                        "maintaining a consistent style, lighting, and appearance. "
                        "The new plant should look like it belongs with the others in the scene."
                    )
                else:
                    prompt = (
                        f"A {age}, vibrant {plant_names[idx]} {plant_type} plant. This plant is a {sub_type}. "
                        "The plant is in a natural scene with shadows cast onto the surrounding terrain. "
                        "The plant should emerge organically from the terrain, its foliage interacting naturally with its surroundings. "
                        "The plant’s colors and textures harmonize with the surrounding palette, enhancing the realism. "
                        "Appears as a native resident of this landscape."
                    )

                print("prompt", prompt)

                response = oai_client.images.edit(
                    model="dall-e-2",
                    image=img_bytes,
                    mask=mask_bytes,
                    prompt=prompt,
                    n=1,
                    size="512x512",
                )

                image_url = response.data[0].url
                response_data = urllib.request.urlopen(image_url).read()

                img_name = f"{productID_base}-{idx}.png"
                asset_uri = _construct_gcs_url(demeter_bucket_name, img_name)
                demeter_bucket.blob(img_name).upload_from_string(
                    response_data, content_type="image/png"
                )
                demeter_bucket.blob("mask.png").upload_from_string(
                    mask_bytes.getvalue(), content_type="image/png"
                )

                cur_img = Image.open(io.BytesIO(response_data))
                final_uri = asset_uri
                print("final uri", final_uri)

        except Exception as e:
            return (
                json.dumps({"success": False, "reason": f"Dalle error. {e}"}),
                500,
                {"ContentType": "application/json"},
            )

    return (
        json.dumps({"success": True, "assetURI": final_uri}),
        200,
        {"ContentType": "application/json"},
    )
