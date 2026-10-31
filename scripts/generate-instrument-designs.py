"""Original Windweave instrument geometry and pixel materials for Blockbench.

Writes construction specifications; Blockbench creates and exports the actual models.
Run from any directory with Python and Pillow installed.
"""
from pathlib import Path
import json
import math
import random
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/windweave'
ART = ROOT / 'art/instruments'
ART.mkdir(parents=True, exist_ok=True)
TEX = ASSETS / 'textures/block/instruments'
TEX.mkdir(parents=True, exist_ok=True)
PALETTE = {
    'housing': (43, 65, 74), 'steel': (173, 194, 194),
    'copper': (187, 111, 66), 'rubber': (29, 37, 42),
    'red': (190, 64, 51), 'cyan': (76, 198, 187),
    'ivory': (222, 219, 186),
}
for index, (name, color) in enumerate(PALETTE.items()):
    rng = random.Random(510 + index)
    im = Image.new('RGB', (32, 32))
    for y in range(32):
        for x in range(32):
            n = rng.choice([-3, -1, 0, 0, 1, 3])
            if y == 0 or x == 0:
                n += 13
            elif y == 31 or x == 31:
                n -= 15
            im.putpixel((x, y), tuple(max(0, min(255, c+n)) for c in color))
    im.save(TEX / f'{name}.png')

# Instrument displays are deliberately graphic, not fake live numerical readouts.
for name in ['screen', 'dial']:
    im = Image.new('RGB', (32, 32), (22, 44, 49))
    draw = ImageDraw.Draw(im)
    draw.rectangle((0, 0, 31, 31), outline=(119, 158, 158), width=2)
    if name == 'screen':
        for x in range(5, 29, 5):
            draw.line((x, 4, x, 27), fill=(30, 61, 65))
        for y in range(7, 28, 5):
            draw.line((4, y, 27, y), fill=(30, 61, 65))
        draw.line([(4, 20), (8, 20), (11, 12), (14, 23), (18, 9), (22, 16), (27, 16)], fill=(116, 233, 212), width=2)
        draw.rectangle((4, 4, 12, 5), fill=(184, 204, 180))
    else:
        draw.arc((4, 4, 27, 27), 180, 358, fill=(197, 215, 195), width=2)
        for a in [180, 210, 240, 270, 300, 330, 355]:
            r = math.radians(a)
            draw.line((16+8*math.cos(r), 17+8*math.sin(r), 16+11*math.cos(r), 17+11*math.sin(r)), fill=(201, 218, 196))
        draw.line((16, 17, 23, 10), fill=(235, 151, 77), width=2)
        draw.rectangle((5, 24, 26, 26), fill=(60, 115, 112))
    im.save(TEX / f'{name}.png')

designs = {}
def cube(parts, name, a, b, material, group='body'):
    parts.append(dict(name=f'{group}/{name}', from_=a, to=b, material=material, group=group))

def stand(parts, vane=False):
    cube(parts, 'rubber_foot', [3, 0, 3], [13, .75, 13], 'rubber')
    cube(parts, 'cast_base', [3, .75, 3], [13, 2.25, 13], 'housing')
    cube(parts, 'brass_base_inset', [4, 2.25, 4], [12, 2.75, 12], 'copper')
    cube(parts, 'neck', [6, 2.75, 6], [10, 4, 10], 'housing')
    cube(parts, 'mast', [7.25, 4, 7.25], [8.75, 12.75, 8.75], 'steel')
    cube(parts, 'mast_collar', [6.75, 5, 6.75], [9.25, 6, 9.25], 'copper')
    cube(parts, 'bearing', [6.75, 12, 6.75], [9.25, 13.1, 9.25], 'housing')
    for x in [4, 11]:
        for z in [4, 11]:
            cube(parts, f'bolt_{x}_{z}', [x, 2.75, z], [x+1, 3.1, z+1], 'steel')
    if vane:
        cube(parts, 'compass_ns', [7.7, 7, 3.25], [8.3, 7.5, 12.75], 'copper')
        cube(parts, 'compass_ew', [3.25, 7, 7.7], [12.75, 7.5, 8.3], 'copper')
        cube(parts, 'north_marker', [7.3, 7.5, 3.25], [8.7, 8.1, 4.25], 'red')

p=[]
stand(p)
cube(p, 'hub', [7, 13.15, 7], [9, 14.6, 9], 'copper', 'rotor')
cube(p, 'cap', [7.5, 14.6, 7.5], [8.5, 15, 8.5], 'steel', 'rotor')
arm=[]
cube(arm, 'arm', [8.5, 13.6, 7.65], [13, 14.15, 8.35], 'steel', 'rotor')
cube(arm, 'cup_floor', [11.75, 13, 6.5], [15, 13.55, 9.5], 'copper', 'rotor')
cube(arm, 'cup_back', [11.75, 13.55, 6.5], [15, 15.5, 7.05], 'copper', 'rotor')
cube(arm, 'cup_inner', [12.3, 13.55, 7.05], [14.45, 13.7, 9.5], 'rubber', 'rotor')
cube(arm, 'cup_left', [11.75, 13.55, 7.05], [12.3, 15.2, 9.5], 'copper', 'rotor')
cube(arm, 'cup_right', [14.45, 13.55, 7.05], [15, 15.2, 9.5], 'copper', 'rotor')
for k in range(4):
    for part in arm:
        a, b = part['from_'], part['to']
        corners=[]
        for x0 in [a[0], b[0]]:
            for z0 in [a[2], b[2]]:
                x,z=x0,z0
                for _ in range(k): x,z=16-z,x
                corners.append((x,z))
        cube(p, part['name'].split('/')[-1]+str(k), [min(x for x,z in corners),a[1],min(z for x,z in corners)], [max(x for x,z in corners),b[1],max(z for x,z in corners)], part['material'], 'rotor')
designs['anemometer']=p

p=[]
stand(p, True)
cube(p, 'hub', [7,13.15,7], [9,14.7,9], 'copper','head')
cube(p, 'shaft', [7.65,13.8,1.25], [8.35,14.4,14.25], 'steel','head')
for i, width in enumerate([1,2,3,4]):
    cube(p, f'arrow_step_{i}', [8-width/2,13.65,1+i*.7], [8+width/2,14.55,1+(i+1)*.7], 'copper','head')
cube(p, 'tail_root', [7.6,13.2,11], [8.4,15,12], 'copper','head')
cube(p, 'tail_fin', [7.55,12.6,12], [8.45,15.7,14.5], 'red','head')
cube(p, 'tail_stripe', [7.45,13.9,13], [8.55,14.35,14.4], 'ivory','head')
designs['wind_vane']=p

p=[]
for x in [2,11]:
    for z in [2,11]: cube(p,f'foot_{x}_{z}',[x,0,z],[x+3,1,z+3],'rubber')
cube(p,'base',[1,1,1],[15,2.25,15],'copper')
cube(p,'cabinet',[1.5,2.25,2],[14.5,11.5,14.5],'housing')
cube(p,'top_lip',[1,11.5,1.5],[15,12.1,15],'steel')
cube(p,'screen_bezel',[2.5,6,1.5],[10.6,10.7,2],'rubber')
cube(p,'screen',[3,6.5,1.35],[10.1,10.2,1.5],'screen')
cube(p,'dial',[11.25,7.5,1.25],[13.5,9.75,2],'copper')
cube(p,'dial_tick',[12.15,8.7,1.1],[12.6,9.5,1.25],'ivory')
for i,mat in enumerate(['cyan','ivory','red']):
    cube(p,f'button_frame_{i}',[3+i*3.5,3.4,1.5],[5+i*3.5,5,2],'rubber')
    cube(p,f'button_{i}',[3.3+i*3.5,3.7,1.2],[4.7+i*3.5,4.7,1.5],mat)
for i in range(5): cube(p,f'cooling_rib_{i}',[4,12.1,5+i*1.45],[12,12.65,5.6+i*1.45],'rubber')
for x in [1,14]:
    cube(p,f'handle_post_front_{x}',[x,4.5,5],[x+1,5.5,6],'copper')
    cube(p,f'handle_post_back_{x}',[x,4.5,10],[x+1,5.5,11],'copper')
    cube(p,f'handle_{x}',[x,5.5,5],[x+1,6.2,11],'copper')
for i in range(4): cube(p,f'back_vent_{i}',[4,4+i*1.3,14.5],[12,4.55+i*1.3,14.7],'rubber')
designs['wind_controller']=p

p=[]
cube(p,'grip',[5.5,1,6.5],[10.5,8.8,9.5],'housing')
cube(p,'grip_bottom',[5.5,.5,6.5],[10.5,1.5,9.5],'rubber')
cube(p,'left_trim',[5,2,6.4],[5.5,8,9.6],'copper')
cube(p,'right_trim',[10.5,2,6.4],[11,8,9.6],'copper')
cube(p,'dial_bezel',[6,4.8,6.1],[10,8.3,6.5],'rubber')
cube(p,'analog_dial',[6.35,5.1,5.95],[9.65,8,6.1],'dial')
cube(p,'button',[7.1,2.4,6],[8.9,3.8,6.5],'cyan')
cube(p,'sensor_neck',[7,8.8,7],[9,10,9],'steel')
# Connected octagonal sensor ring with the Java format's supported 45-degree rotations.
for i in range(8):
    angle = math.radians(i*45)
    cx,cy=8+2.6*math.sin(angle),12.4+2.6*math.cos(angle)
    vertical = i in [2,6]
    w,h=(.8,2.3) if vertical else (2.3,.8)
    cube(p,f'guard_{i}',[cx-w/2,cy-h/2,6.9],[cx+w/2,cy+h/2,9.1],'copper')
    if i%2:
        p[-1]['rotation'] = dict(axis='z',angle=-45 if i in [1,5] else 45,origin=[cx,cy,8])
cube(p,'fan_hub',[7.45,11.85,7.55],[8.55,12.95,8.45],'steel')
for i,(a,b) in enumerate([
    ([7.7,12.9,7.65],[8.3,14.1,8.35]),
    ([7.7,10.7,7.65],[8.3,11.9,8.35]),
    ([6.3,12.1,7.65],[7.5,12.7,8.35]),
    ([8.5,12.1,7.65],[9.7,12.7,8.35])]): cube(p,f'fan_blade_{i}',a,b,'housing')
designs['wind_meter']=p

for name,parts in designs.items():
    for part in parts: part['from'] = part.pop('from_')
    (ART / f'{name}.design.json').write_text(json.dumps(parts,indent=2),encoding='utf-8')
print('Wrote four original instrument designs and nine pixel materials.')
