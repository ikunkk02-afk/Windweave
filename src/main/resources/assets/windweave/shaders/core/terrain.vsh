#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>
#moj_import <windweave:wind.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec3 windWorldPos;
out float windWaterStrength;
out float windWaterMaterial;

void main() {
    vec3 base = Position + ChunkOffset;
    windWaterMaterial = (UV2.x & 16384) != 0 && ((UV2.y >> 12) & 7) == 4 && WindEffects.y > 0.5 ? 1.0 : 0.0;
    vec3 pos = wind_deform(base, UV2, Normal, windWaterStrength);
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    vertexDistance = fog_distance(pos, FogShape);
    // Animation flags never enter the vanilla lightmap lookup.
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2 & ivec2(255));
    texCoord0 = UV0;
    windWorldPos = base + WindCamera;
}
