// Shared by geometry and surface shading. Returns height and analytic X/Z slopes.
vec3 water_waves(vec2 p, float time) {
    vec2 a = vec2(0.26, 0.11);
    vec2 b = vec2(-0.19, 0.44);
    vec2 c = vec2(0.70, -0.42);
    float pa = dot(p, a) - time * 0.65;
    float pb = dot(p, b) - time * 0.93 + 1.7;
    float pc = dot(p, c) - time * 1.55 + 0.8;
    float height = 0.68 * sin(pa) + 0.24 * sin(pb) + 0.08 * sin(pc);
    vec2 slope = 0.68 * cos(pa) * a + 0.24 * cos(pb) * b + 0.08 * cos(pc) * c;
    return vec3(height, slope);
}
