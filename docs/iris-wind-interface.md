# Iris wind uniforms

Optional integration for Iris's `CommonUniforms.addNonDynamicUniforms`. Validated with Iris 1.8.14-beta.1 on NeoForge 1.21.1. The mixin is pseudo/optional when Iris is absent; when Iris is present, an incompatible registration signature fails visibly rather than pretending the bridge is active.

| GLSL uniform | Type | Meaning |
| --- | --- | --- |
| `windweaveActive` | float | 1 with an active client world; defaults to 0 without the mod |
| `windweaveWind` | vec3 | Smoothed camera-local wind in blocks/second, Minecraft world axes |
| `windweaveTime` | float | Monotonic animation seconds, frozen on pause |
| `windweaveTravel` | vec2 | Continuous integral of horizontal smoothed wind, scaled by 0.06 |
| `windweaveEffects` | vec3 | Vegetation, water, fire switches, each 0 or 1; global wind disable clears all |

Uniforms update with Iris's PER_FRAME schedule and are registered again when programs rebuild. The clock advances once at the beginning of LevelRenderer.renderLevel, before the world rendering passes. Source snapshot and shaders share the Minecraft world axes: +X east, +Z south. A shader should use cumulative travel rather than speed multiplied by absolute time to avoid a phase jump when direction changes.

The iterationRP patch uses its existing material IDs, texture coordinates and lighting inputs. It modifies vegetation in both the terrain and shadow/voxel vertex paths. Water retains the pack's normal/parallax implementation. Vertex lighting metadata used by the vanilla renderer is never sent into Sodium/Iris's terrain format.

Shader packs should check `windweaveActive` and keep their own fallback when it is zero. Original packs are not patched automatically; the supplied patcher requires the exact user-provided iterationRP archive hash and writes a new private copy.
