import logging
import sys

import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore

# Initialize Firebase Admin SDK
# App Engine will automatically use Application Default Credentials.
# We check if it's already initialized to prevent errors in some App Engine setups.
if not firebase_admin._apps:
    # Set your project ID explicitly for clarity, though it's often auto-detected.
    cred = credentials.ApplicationDefault()
    firebase_admin.initialize_app(cred, {"projectId": "heph2-338519"})
db = firestore.client()
logger = logging.getLogger("heph-db")
logger.setLevel(logging.DEBUG)
handler = logging.StreamHandler(sys.stdout)
handler.setLevel(logging.DEBUG)
formatter = logging.Formatter("%(asctime)s - %(name)s - %(levelname)s - %(message)s")
handler.setFormatter(formatter)
logger.addHandler(handler)

PLANT_TYPES = sorted(
    [
        "Trees",
        "Shrub",
        "Herb",
        "Grasse",
        "Vine",
        "Fern",
        "Mosse",
        "Succulent",
        "Cacti",
        "Bulb",
        "Tuber",
        "Aquati",
        "Epiphyte",
        "Annual",
        "Perennial",
        "Biennial",
        "Flowering",
        "Conifer",
        "Legume",
    ]
)

PLANT_CHARACTERISTICS = sorted(
    [
        "Full sun",
        "Partial shade",
        "Low light",
        "Drought-tolerant",
        "Moisture-loving",
        "Moderate watering",
        "Upright",
        "Trailing",
        "Climbing",
        "Spreading",
        "Tall",
        "Short",
        "Wide",
        "Low-maintenance",
        "High-care",
        "Full Bloom",
        "Moderate Bloom",
        "No Bloom",
        "Edibility",
        "Culinary",
        "Medicinal",
        "Aromatic",
        "Ornamental",
        "Pest Resistance",
        "Disease Resistance",
    ]
)

PLANT_MATURITY = sorted(["Young", "Established"])

FB_DEMETER_COLLECTION = "demeter_plants"


def get_plant_rec(plant_name):
    try:
        doc_ref = db.collection(FB_DEMETER_COLLECTION)
        query = doc_ref.where(u'plant_name',u'==',plant_name)
        results = query.get()
        if len(results):
            return results[0].to_dict()
    except:
        return None


def save_plant_data_fb(plant_name, plant_prompt, **kwargs):
    doc_ref = db.collection(FB_DEMETER_COLLECTION).document()
    doc_data = kwargs
    doc_data["plant_name"] = plant_name
    doc_data["plant_prompt"] = plant_prompt
    doc_ref.set(doc_data)
    return doc_data
