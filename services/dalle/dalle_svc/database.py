
import logging
import firebase_admin
from firebase_admin import firestore

# Application Default credentials are automatically created.
app = firebase_admin.initialize_app()
db = firestore.client()
logger = logging.getLogger('heph-db')
logger.setLevel(logging.DEBUG)
handler = logging.StreamHandler(sys.stdout)
handler.setLevel(logging.DEBUG)
formatter = logging.Formatter(
    '%(asctime)s - %(name)s - %(levelname)s - %(message)s')
handler.setFormatter(formatter)
logger.addHandler(handler)

PLANT_TYPES = [
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
"Flowerin",
"Conifer",
"Legume",
]

PLANT_CHARACTERISTICS = [
"Full sun" ,
"Partial shade",
"Low ligh",
"Drought-toleran", 
"Moisture-lovin",
"Moderate waterin",
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

PLANT_MATURITY = [
    "Young",
    "Established"]

FB_DEMETER_COLLECTION = 'demeter_plants'

def get_plant_rec(plant_name):
    try:
        doc_ref = db.collection(FB_DEMETER_COLLECTION).document(plant_name)
        return doc_ref.get().to_dict()
    except:
        return None

def save_plant_data_fb(plant_name, plant_prompt, **kwargs):
    doc_ref = db.collection(FB_DEMETER_COLLECTION).document()
    doc_data = kwargs
    doc_data['plant_name'] = plant_name
    doc_data['plant_prompt'] = plant_prompt
    doc_ref.set(
        doc_data
    )
    return doc_data