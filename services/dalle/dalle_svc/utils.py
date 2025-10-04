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
