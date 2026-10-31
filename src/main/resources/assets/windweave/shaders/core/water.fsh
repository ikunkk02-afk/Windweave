#version 150

#moj_import <fog.glsl>
#moj_import <windweave:water_waves.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float WindTime;
uniform vec2 WindTravel;
uniform sampler2D WindStillFrames;
uniform sampler2D WindFlowFrames;
uniform vec4 WindStillRect;
uniform vec4 WindFlowRect;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 windWorldPos;
in float windWaterStrength;
in float windWaterMaterial;
out vec4 fragColor;

bool in_sprite(vec2 uv, vec4 rect) {
    return all(greaterThanEqual(uv, rect.xy)) && all(lessThanEqual(uv, rect.xy + rect.zw));
}

vec4 smooth_frames(sampler2D frames, vec2 uv, float framesPerSecond) {
    ivec2 size = textureSize(frames, 0);
    float count = max(1.0, floor(float(size.y) / float(size.x)));
    float halfTexel = 0.5 / float(size.x);
    uv = clamp(fract(uv), vec2(halfTexel), vec2(1.0 - halfTexel));
    float frame = WindTime * framesPerSecond;
    float a = mod(floor(frame), count);
    float b = mod(a + 1.0, count);
    vec4 first = texture(frames, vec2(uv.x, (a + uv.y) / count));
    vec4 second = texture(frames, vec2(uv.x, (b + uv.y) / count));
    return mix(first, second, fract(frame));
}

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    if (windWaterMaterial > 0.5) {
        if (in_sprite(texCoord0, WindStillRect)) {
            vec2 uv = (texCoord0 - WindStillRect.xy) / WindStillRect.zw;
            vec4 resting = smooth_frames(WindStillFrames, uv, 10.0);
            // Integrated world-space wind translates the pattern continuously. Do not multiply
            // accumulated travel by local wind strength: that would reintroduce phase jumps.
            vec2 p = windWorldPos.xz - WindTravel;
            vec2 distortion = 0.055 * water_waves(p, WindTime).yz;
            vec4 travelling = smooth_frames(WindStillFrames, uv - WindTravel * 0.20 + distortion, 10.0);
            texel = mix(resting, travelling, smoothstep(0.0, 0.9, windWaterStrength));
        } else if (in_sprite(texCoord0, WindFlowRect)) {
            texel = smooth_frames(WindFlowFrames, (texCoord0 - WindFlowRect.xy) / WindFlowRect.zw, 20.0);
        }
    }
    vec4 color = texel * vertexColor * ColorModulator;
    if (windWaterStrength > 0.0001) {
        vec2 p = windWorldPos.xz - WindTravel;
        vec3 waves = water_waves(p, WindTime);
        // Low-amplitude capillary detail fades with pixel footprint instead of shimmering at a distance.
        float footprint = max(length(dFdx(p)), length(dFdy(p)));
        float detail = exp(-3.0 * footprint);
        vec2 slope = waves.yz;
        slope += detail * 0.035 * vec2(2.1, 1.6) * cos(dot(p, vec2(2.1, 1.6)) - WindTime * 1.8);
        slope += detail * 0.018 * vec2(-1.8, 2.6) * cos(dot(p, vec2(-1.8, 2.6)) - WindTime * 2.3);
        vec3 normal = normalize(vec3(-slope.x * 0.30, 1.0, -slope.y * 0.30));
        float shading = dot(normal.xz, vec2(0.55, 0.8));
        // Retain vanilla animated texture, biome tint and opacity; avoid bright travelling stripes.
        color.rgb *= 1.0 + clamp(shading * min(windWaterStrength, 2.0), -0.06, 0.06);
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
