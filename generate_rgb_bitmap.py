from PIL import Image

def generate_rgb565_from_image(image_path, output_path):
    width = 128
    height = 128

    try:
        image = Image.open(image_path)
    except FileNotFoundError:
        print(f"Error: Image file not found at {image_path}")
        return

    # Resize and crop the image to 128x128, focusing on the center
    img_width, img_height = image.size
    crop_size = min(img_width, img_height)
    left = (img_width - crop_size) // 2
    top = (img_height - crop_size) // 2
    right = (img_width + crop_size) // 2
    bottom = (img_height + crop_size) // 2
    image = image.crop((left, top, right, bottom))
    image = image.resize((width, height))

    # Convert to RGB if it's not already
    image = image.convert("RGB")

    bitmap = []
    for y in range(height):
        for x in range(width):
            r, g, b = image.getpixel((x, y))

            # Convert to RGB565
            r5 = (r >> 3) & 0x1F
            g6 = (g >> 2) & 0x3F
            b5 = (b >> 3) & 0x1F

            color = (r5 << 11) | (g6 << 5) | b5
            bitmap.append(color)

    with open(output_path, "w") as f:
        f.write("const uint16_t myBitmap[] PROGMEM = {\n")
        for i, color in enumerate(bitmap):
            if i % 16 == 0:
                f.write("  ")
            f.write(f"0x{color:04x}, ")
            if (i + 1) % 16 == 0:
                f.write("\n")
        f.write("\n};")

if __name__ == "__main__":
    generate_rgb565_from_image("daisy.jpg", "arduino/demeter-client/bitmap_data.h")
    print("Bitmap data successfully generated and written to arduino/demeter-client/bitmap_data.h")
