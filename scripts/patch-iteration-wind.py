"""Create a private Windweave-adapted copy of the user's iterationRP zip.

The original pack is required, verified by SHA-256, and never overwritten.
No original shader-pack files are distributed by Windweave.
"""
import argparse
import hashlib
from pathlib import Path
import zipfile

parser=argparse.ArgumentParser()
parser.add_argument('source',type=Path)
parser.add_argument('output',type=Path)
args=parser.parse_args()
EXPECTED='b73e180401eca11bce696cf6e1697271ef332e6e7d6427a738efa446080693bc'
assert args.source.resolve()!=args.output.resolve(), 'Source and output must differ'
assert hashlib.sha256(args.source.read_bytes()).hexdigest()==EXPECTED, 'Unsupported pack revision; refusing to patch'
assert not args.output.exists(), 'Output already exists; select a new output file'

bridge='''// Original Windweave integration, used only in a privately patched shader pack.
#ifndef WINDWEAVE_BRIDGE
#define WINDWEAVE_BRIDGE
uniform float windweaveActive;
uniform float windweaveTime;
uniform vec3 windweaveWind;
uniform vec2 windweaveTravel;
uniform vec3 windweaveEffects;

void WindweavePlants(inout vec4 p, float sky) {
    float id = mc_Entity.x;
    bool leaves = id == 4.0 || id == 7040.0;
    bool grass = abs(id - 7020.0) < 10.5;
    bool fire = abs(id - 244.0) < 3.5;
    if (!(leaves || grass || fire)) return;
    float enabled = fire ? windweaveEffects.z : windweaveEffects.x;
    vec2 wind = windweaveWind.xz;
    float speed = length(wind);
    if (enabled < 0.5 || speed < 0.001) return;
    vec3 absolutePos = p.xyz + cameraPosition;
    float anchor = leaves ? 1.0 : step(gl_MultiTexCoord0.y, mc_midTexCoord.y);
    if (id == 7020.0) anchor *= 0.5;
    if (id == 7021.0) anchor = anchor * 0.5 + 0.5;
    float exposure = clamp((sky - 0.35) * 1.7, 0.0, 1.0);
    float phase = dot(absolutePos.xz, vec2(0.63,0.49)) - dot(windweaveTravel, vec2(0.9,0.7));
    float flutter = sin(phase + windweaveTime * 2.6) * 0.22 + sin(phase*1.73-windweaveTime*1.9)*0.12;
    float amount = min(speed * 0.012, leaves ? 0.22 : 0.42) * anchor * exposure;
    p.xz += wind / speed * amount * (0.66 + flutter);
    if (!leaves) p.y -= amount * 0.1;
}
#endif
'''

def replace_once(text,old,new,label):
    assert text.count(old)==1, f'Unexpected source layout: {label}'
    return text.replace(old,new,1)

changes={}
with zipfile.ZipFile(args.source) as src:
    plant='shaders/Lib/IndividualFunctions/WavingPlants.glsl'
    s=src.read(plant).decode().replace('\r','')
    s='#include "/Lib/Windweave.glsl"\n'+s
    s=replace_once(s,'void WavingPlants(inout vec4 worldPos, float lightmap){',
        'void WavingPlants(inout vec4 worldPos, float lightmap){\n'
        '    if (windweaveActive > 0.5) { WindweavePlants(worldPos, lightmap); return; }',plant)
    changes[plant]=s
    wave='shaders/Lib/IndividualFunctions/WaterWaves.glsl'
    s=src.read(wave).decode().replace('\r','')
    # Water shaders do not have the terrain mc_Entity input; declare only the shared uniforms here.
    s='''#ifndef WINDWEAVE_WATER_UNIFORMS
#define WINDWEAVE_WATER_UNIFORMS
uniform float windweaveActive;
uniform vec2 windweaveTravel;
uniform vec3 windweaveWind;
uniform vec3 windweaveEffects;
#endif
'''+s
    needle='\n\n\tvec2 waveCoord ='
    s=replace_once(s,needle,'\n    if (windweaveActive > 0.5) waveTime = -windweaveTravel * (0.02 * WAVE_SPEED) * windweaveEffects.y;\n\n\tvec2 waveCoord =',wave)
    s=replace_once(s,'\n\treturn wave;',
        '\n    if (windweaveActive > 0.5) wave *= windweaveEffects.y * clamp(length(windweaveWind.xz) / 12.0, 0.0, 1.8);\n\treturn wave;',wave)
    changes[wave]=s
    # Match grass, leaves and fire in the camera and shadow/voxel passes, including if native waving is disabled.
    terrain='shaders/Lib/Programs/Gbuffers/Terrain_VS.glsl'
    shadow='shaders/Lib/PathTracing/Voxelizer/Shadow.glsl'
    for path in [terrain,shadow]:
        s=src.read(path).decode().replace('\r','')
        if path==terrain:
            old='#ifdef WAVING_PLANTS\n\t#include "/Lib/IndividualFunctions/WavingPlants.glsl"\n#endif'
            s=replace_once(s,old,'#include "/Lib/IndividualFunctions/WavingPlants.glsl"',path)
            old='\t#ifdef WAVING_PLANTS\n\t\tWavingPlants(worldPos, g_blockLight.y);\n\t#endif'
            new='    if (windweaveActive > 0.5) WindweavePlants(worldPos, g_blockLight.y);\n'+old.replace('WavingPlants(worldPos, g_blockLight.y);','if (windweaveActive < 0.5) WavingPlants(worldPos, g_blockLight.y);')
        else:
            old='\t#ifdef WAVING_PLANTS\n\t\t#include "/Lib/IndividualFunctions/WavingPlants.glsl"\n\t#endif'
            s=replace_once(s,old,'\t#include "/Lib/IndividualFunctions/WavingPlants.glsl"',path)
            old='\t\t#ifdef WAVING_PLANTS\n\t\t#ifdef SHADOW_WAVING_PLANTS\n\t\t\tWavingPlants(worldPos, skylightmap);\n\t\t#endif\n\t\t#endif'
            new='        if (windweaveActive > 0.5) WindweavePlants(worldPos, skylightmap);\n'+old.replace('WavingPlants(worldPos, skylightmap);','if (windweaveActive < 0.5) WavingPlants(worldPos, skylightmap);')
        changes[path]=replace_once(s,old,new,path)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.output,'w',zipfile.ZIP_DEFLATED) as dst:
        for entry in src.infolist(): dst.writestr(entry,changes.get(entry.filename,src.read(entry)))
        dst.writestr('shaders/Lib/Windweave.glsl',bridge)
        dst.writestr('WINDWEAVE_PRIVATE_PATCH.txt','Private local adaptation for Windweave 0.6.0. Original iterationRP by Tahnass. Original license remains in LICENSE.md. Do not redistribute this archive.\n')
print(args.output)
