def generate_rgb565_checkerboard():
    width = 128
    height = 128
    square_size = 8

    # RGB565 colors
    black = 0x0000
    # For the other color, let's use M5Stack's own orange color for fun
    # Orange: R=255, G=165, B=0
    # R (5 bits): 255 >> 3 = 31
    # G (6 bits): 165 >> 2 = 41
    # B (5 bits): 0 >> 3 = 0
    # orange = (31 << 11) | (41 << 5) | 0 = 0b1111110100100000 = 0xFD20
    orange = 0xFD20

    bitmap = []
    for y in range(height):
        for x in range(width):
            color = black
            if (x // square_size) % 2 == (y // square_size) % 2:
                color = black
            else:
                color = orange
            bitmap.append(color)

    # Format as a C array
    print("const uint16_t myBitmap[] PROGMEM = {")
    for i, color in enumerate(bitmap):
        if i % 16 == 0:
            print("  ", end="")
        print(f"0x{color:04x}, ", end="")
        if (i + 1) % 16 == 0:
            print()
    print("};")

if __name__ == "__main__":
    generate_rgb565_checkerboard()
