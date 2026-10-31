uniform vec2 Wind;
uniform float WindTime;
uniform vec2 WindTravel;
uniform vec3 WindCamera;
uniform vec3 WindEffects;
uniform float WindShelter;

#moj_import <windweave:water_waves.glsl>

float wind_exposure(vec3 encoded, vec2 direction) {
    if (WindShelter < 0.5) return 1.0;
    ivec3 bytes = ivec3(round(encoded * 127.0));
    int shelterBits = bytes.x | (bytes.y << 7) | (bytes.z << 14);
    float xOpen = float((shelterBits >> (direction.x > 0.0 ? 15 : 5)) & 31) / 31.0;
    float zOpen = float((shelterBits >> (direction.y > 0.0 ? 0 : 10)) & 31) / 31.0;
    vec2 contribution = direction * direction;
    return xOpen * contribution.x + zOpen * contribution.y;
}

vec3 wind_deform(vec3 pos, ivec2 light, vec3 exposure, out float waterStrength) {
    waterStrength = 0.0;
    if ((light.x & 16384) == 0) return pos;
    int kind = (light.y >> 12) & 7;
    float weight = float((light.x >> 8) & 31) / 31.0;
    float speed = length(Wind);
    if (speed < 0.001 || weight < 0.001) return pos;
    vec2 direction = Wind / speed;
    vec2 crosswind = vec2(-direction.y, direction.x);
    float strength = min(speed / 12.0, 2.5) * wind_exposure(exposure, direction);
    vec3 world = pos + WindCamera;
    // Fixed spatial carriers + integrated travel: gust updates cannot reset the phase.
    vec2 advected = world.xz - WindTravel;
    float phase = dot(advected, vec2(0.72, 0.43)) - WindTime * 1.65;
    float gust = sin(phase) + 0.25 * sin(dot(advected, vec2(-0.35, 1.1)) - WindTime * 2.15);
    if (kind == 1 && WindEffects.x > 0.5) {
        float bend = weight * weight * strength;
        pos.xz += direction * bend * (0.12 + 0.05 * gust);
        pos.xz += crosswind * bend * 0.018 * sin(phase * 0.8 + WindTime * 0.2);
        pos.y -= bend * 0.018 * (0.5 + 0.5 * sin(phase));
    } else if (kind == 2 && WindEffects.x > 0.5) {
        float flutter = sin(dot(advected, vec2(1.2, 0.9)) + world.y * 0.7 - WindTime * 2.1);
        pos.xz += direction * strength * (0.02 + 0.03 * gust);
        pos.xz += crosswind * strength * 0.01 * flutter;
        pos.y += strength * 0.012 * sin(phase + world.y * 0.4);
    } else if (kind == 3 && WindEffects.z > 0.5) {
        float flicker = sin(WindTime * 6.5 + world.x * 2.0 + world.z * 1.5);
        pos.xz += direction * weight * strength * (0.22 + 0.05 * gust + 0.018 * flicker);
        pos.xz += crosswind * weight * strength * 0.02 * flicker;
        pos.y -= weight * strength * 0.03;
    } else if (kind == 4 && WindEffects.y > 0.5) {
        waterStrength = strength * weight;
        // Wavelengths stay well above one-block mesh spacing, avoiding checkerboard folding.
        // Visible centimetre-scale waves. Cap below the source-water-to-bank height clearance.
        pos.y += weight * 0.055 * min(strength, 1.4) * water_waves(advected, WindTime).x;
    }
    return pos;
}
