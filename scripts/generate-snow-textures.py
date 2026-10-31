"""Generate six original pixel-art snowflake silhouettes (Pillow)."""
from pathlib import Path
from PIL import Image, ImageDraw
import math

target = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/windweave/textures/particle'
target.mkdir(parents=True, exist_ok=True)
names = ['fern', 'stellar', 'needle', 'plate', 'grain', 'cluster']
for kind, name in enumerate(names):
    image = Image.new('RGBA', (32, 32))
    draw = ImageDraw.Draw(image)
    center = (15.5, 15.5)
    def point(radius, angle):
        return (center[0] + radius * math.cos(angle), center[1] + radius * math.sin(angle))
    if kind in (0, 1, 2):
        for arm in range(6):
            angle = arm * math.tau / 6
            draw.line([center, point(14, angle)], fill=(255, 255, 255, 255), width=2)
            for radius in ((5, 9, 12) if kind == 0 else (7, 11) if kind == 1 else (10,)):
                root = point(radius, angle)
                length = 4 if kind == 0 else 5 if kind == 1 else 2.5
                for side in (-1, 1):
                    branch = angle + side * math.pi / 3
                    end = (root[0] + length * math.cos(branch), root[1] + length * math.sin(branch))
                    draw.line([root, end], fill=(245, 252, 255, 240), width=2 if kind != 2 else 1)
        if kind == 1:
            draw.polygon([point(6, a * math.tau / 6) for a in range(6)], fill=(235, 247, 255, 210), outline=(255, 255, 255, 255))
        draw.ellipse((13, 13, 18, 18), fill=(255, 255, 255, 255))
    elif kind == 3:
        draw.polygon([point(13, a * math.tau / 6) for a in range(6)], fill=(230, 243, 255, 185), outline=(255, 255, 255, 255), width=2)
        for arm in range(6):
            draw.line([center, point(12, arm * math.tau / 6)], fill=(255, 255, 255, 235), width=1)
        draw.polygon([point(6, a * math.tau / 6) for a in range(6)], outline=(255, 255, 255, 245), width=1)
    elif kind == 4:
        draw.polygon([(15, 4), (23, 9), (26, 18), (19, 26), (10, 25), (5, 17), (8, 8)], fill=(245, 250, 255, 235))
        draw.line([(15, 5), (12, 15), (24, 18)], fill=(255, 255, 255, 255), width=2)
    else:
        for x, y, r in [(10, 11, 7), (21, 13, 6), (16, 23, 6), (8, 22, 4)]:
            draw.ellipse((x-r, y-r, x+r, y+r), fill=(235, 246, 255, 200), outline=(255, 255, 255, 255), width=2)
            draw.line([(x-r+2, y), (x+r-2, y)], fill=(255, 255, 255, 245), width=1)
    image.save(target / f'snowflake_{name}.png')
