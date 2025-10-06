from PIL import Image

# cv2 and numpy are not directly used in these utility functions.
# They operate on data types (like NumPy arrays or PIL Images)
# that are created/handled by the calling code in routes.py.

def crop(img, boundaries):
    minx, miny, maxx, maxy = boundaries
    return img[miny:maxy, minx:maxx]


def scale_box(box, xscale, yscale):
    return (
        int(box[0] * xscale),
        int(box[1] * yscale),
        int(box[2] * xscale),
        int(box[3] * yscale),
    )


def thumb_from_png(png_f, thumb_size):
    t_img = Image.open(png_f)
    t_img.thumbnail((thumb_size))
    return t_img




def generate_plant_prompt(
    n_mgkg, p_mgkg, k_mgkg, ph, moisture, sun_intensity,
    lat, lon, plant_type, max_plants=3
):
    prompt = (
        f"Suggest {max_plants} {plant_type} plant types for a location at ({lat}, {lon}).\n"
        f"Soil composition is: N={n_mgkg}mg/kg, P={p_mgkg}mg/kg, K={k_mgkg}mg/kg, pH={ph}, soil moisture={moisture} %.\n"
        f"Sun intesity: {sun_intensity} lux.\n"
        f"Reply in {max_plants} short bullet points, with the name of the plant types only."
    )
    return prompt
