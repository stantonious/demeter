"""
Routes for the Demeter Dalle Service, handling DALL-E image generation and product updates.
"""

import random
import sys
import os
import numpy as np
import io
import json
import urllib.parse
import urllib.request
import cv2
from flask import request
from flask_cors import cross_origin  # Removed CORS as it's handled in __init__
from . import app, utils
import logging
from lib.heph_data import db, game_5e, srd  # auth removed
import time
from PIL import Image, ImageDraw

from google.cloud import storage, secretmanager
import google_crc32c
from openai import OpenAI

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
# storage_client = storage.Client()
# storage_bucket = storage_client.bucket(db.bucket_name)
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
        return [_n.split(",") for _n in aois_list]

    def _get_bb(x, y, s):
        return int(x - (s / 2)), int(y - (s / 2)), int(x + (s / 2)), int(y + (s / 2))

    """Creates DALL-E images ."""
    aois = _decode_aois(request.args.getlist("aois"))
    plant_name = request.args.get("plant_name")
    plant_type = request.args.get("plant_type")
    mask_size = request.args.get("mask_size")
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

        in_image = Image.open(request.files["scene_img"])
        try:
            print("Generating dalle image")
            mask = Image.new("RGBA", in_image.size, (0, 0, 0, 255))
            draw = ImageDraw.Draw(mask)
            # (x1,y1,x2,y2)
            print(f"========== Masking {g_aoi_x} {g_aoi_y} {mask.size}")
            bb = _get_bb(*aois[0], mask_size)
            draw.rectangle(bb, fill=(0, 0, 0, 0))
            img_bytes = io.BytesIO()
            in_image.convert("RGBA").save("./img.png", format="PNG")
            img_bytes.seek(0)
            mask.convert("RGBA").save("./mask.png", format="PNG")

            with open("./img.png", "rb") as image_f, open("./mask.png", "rb") as mask_f:
                response = client.images.edit(
                    model="dall-e-2",
                    image=image_f,
                    mask=mask_f,
                    prompt=f"A clear, high-quality image of a fully grown {plant_name} {g_plant_type} plant that is firmly planted and part of the natural surroundings.  The view point should be from 6 ft. above and at a 20 degree angle.",
                    n=1,  # Number of images to generate
                    size="512x512",  # Image resolution
                )
                # The generated image URL is in the response
                image_url = response.data[0].url
                print(f"Generated image URL: {image_url}")

                # Download the image
                with httpx.stream("GET", image_url) as r:
                    image_data = bytearray()
                    for chunk in r.iter_bytes():
                        image_data.extend(chunk)

                    g_generated_plant_image_data = image_data
                    print("Successfully downloaded generated image.")

        except Exception as e:
            return (
                json.dumps({"success": False, "reason": f"Dalle error. {e}"}),
                500,
                {"ContentType": "application/json"},
            )
    return (
        json.dumps(
            {
                "success": True,
                "productIDs": productIDs,
                "assetURIs": dalle_uris,
                "characters": characters,
                "prompt": final_prompt,
            }
        ),
        200,
        {"ContentType": "application/json"},
    )
