#!python3

from lib.feyrle_svc import routes,app  # routes import removed

# Flask import removed as it's not directly used here
import os

# main driver function
if __name__ == "__main__":
    # run() method of Flask class runs the application
    # on the local development server

    # Secret key is set in feyrle_svc/__init__.py from an environment variable.
    app.config["SESSION_TYPE"] = "filesystem"

    # Debug mode should be controlled by an environment variable for safety.
    app.debug = os.environ.get("FLASK_DEBUG", "False").lower() == "true"

    for rule in app.url_map.iter_rules():
        print(f"Endpoint: {rule.endpoint}, Methods: {', '.join(rule.methods)}, Rule: {rule}")
    # The Flask development server is not for production.
    # Use a production WSGI server (e.g., Gunicorn) for deployment.
    # Port 80 typically requires root privileges; using a higher port (e.g., 8081) for development.
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", 8081)))
