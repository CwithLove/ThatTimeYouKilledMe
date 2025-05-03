from PIL import Image

# Load the image
image_path = "Crack_Future.png"
image = Image.open(image_path).convert("RGBA")

# Create a new image with transparent background
transparent_image = Image.new("RGBA", image.size, (0, 0, 0, 0))

# Process pixels
pixels = image.load()
transparent_pixels = transparent_image.load()
width, height = image.size

for y in range(height):
    for x in range(width):
        print("x:", x, "y:", y)
        r, g, b, a = pixels[x, y]
        # Keep only pixels that are not almost black
        if r > 10 or g > 10 or b > 10:
            transparent_pixels[x, y] = (0, 0, 0, 255)  # Draw crack in black
        else:
            transparent_pixels[x, y] = (0, 0, 0, 0)    # Make background transparent

# Save result
output_path = "Crack_Transparent_1024.png"
transparent_image.save(output_path)

output_path
