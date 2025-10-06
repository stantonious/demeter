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
from . import app, utils

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


@app.route("/demeter/product/create", methods=["POST"])
@cross_origin()
def create_dalle():
    def _decode_aois(aois_list):
        return [tuple(map(int, _n.split(","))) for _n in aois_list]

    def _get_bb(x, y, s):
        return int(x - s / 2), int(y - s / 2), int(x + s / 2), int(y + s / 2)

    print("args", request.args)
    aois = _decode_aois(request.args.getlist("aois"))
    print("aois", aois)
    plant_name = request.args.get("plant_name")
    plant_type = request.args.get("plant_type")
    mask_size = int(request.args.get("mask_size"))
    productID_base = str(int(time.time()))

    api_key = _get_openai_api_key()
    oai_client = OpenAI(api_key=api_key)

    if request.method == "POST":
        if "scene_img" not in request.files:
            return json.dumps({"success": False, "reason": "No file provided."}), 500, {"ContentType": "application/json"}

        in_image = Image.open(request.files["scene_img"]).convert("RGBA").resize((512, 512))

        try:
            cur_img = in_image
            for idx, aoi in enumerate(aois):
                bb = _get_bb(*aoi, mask_size)
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

                prompt = (
                    f"A mature, vibrant {plant_name} {plant_type} plant in a natural scene with shadows cast onto the surrounding terrain. "
                    "The plant should emerge organically from the terrain, its foliage interacting naturally with its surroundings. "
                    "The plant’s colors and textures harmonize with the surrounding palette, enhancing the realism. "
                    "Appears as a native resident of this landscape."
                )

                print ('prompt',prompt)

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
                demeter_bucket.blob(img_name).upload_from_string(response_data, content_type="image/png")
                demeter_bucket.blob("mask.png").upload_from_string(mask_bytes.getvalue(), content_type="image/png")

                cur_img = Image.open(io.BytesIO(response_data))
                final_uri = asset_uri

        except Exception as e:
            return json.dumps({"success": False, "reason": f"Dalle error. {e}"}), 500, {"ContentType": "application/json"}

    return json.dumps({"success": True, "assetURI": final_uri}), 200, {"ContentType": "application/json"}
