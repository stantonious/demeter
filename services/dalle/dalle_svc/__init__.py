from flask import Flask
from flask_cors import CORS
import os

app = Flask(__name__)
CORS(app)
app.config["CORS_HEADERS"] = "Content-Type"
app.config["MAX_CONTENT_LENGTH"] = 16 * 1024 * 1024
app.secret_key = os.environ.get("APP_SECRET_KEY")
