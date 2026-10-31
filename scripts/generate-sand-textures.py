"""Original procedural sprites, deterministic; no third-party texture assets."""
from pathlib import Path
from PIL import Image
import random

output = Path(__file__).resolve().parent.parent / 'src/main/resources/assets/windweave/textures/particle'
output.mkdir(parents=True, exist_ok=True)
random_source = random.Random(410)
dust = Image.new('RGBA', (32, 32))
for y in range(32):
    for x in range(32):
        radius_squared = ((x - 15.5) / 15.5) ** 2 + ((y - 15.5) / 15.5) ** 2
        alpha = max(0, 1 - radius_squared) ** 2 * (.68 + .32 * random_source.random())
        dust.putpixel((x, y), (255, 255, 255, int(alpha * 235)))
dust.save(output / 'sand_dust.png')
grain = Image.new('RGBA', (8, 8))
for y in range(2, 6):
    for x in range(2, 6):
        if (x, y) not in [(2, 2), (5, 5)]:
            grain.putpixel((x, y), (255, 255, 255, 240))
grain.save(output / 'sand_grain.png')
