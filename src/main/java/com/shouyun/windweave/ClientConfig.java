package com.shouyun.windweave;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Visual switches do not change server wind or fire physics. */
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue WIND_INDICATOR = BUILDER
            .comment("Show moving wind-direction flow lines. Disable to hide them; wind physics and instruments are unaffected.")
            .define("windDirectionIndicator", true);
    public static final ModConfigSpec.BooleanValue VEGETATION = BUILDER
            .comment("Animate grass, ferns, flowers, crops and leaves in the vanilla terrain renderer.")
            .define("vegetationSway", true);
    public static final ModConfigSpec.BooleanValue WATER = BUILDER
            .comment("Render wind-driven water surface waves and ripples. Does not move fluid blocks.")
            .define("waterRipples", true);
    public static final ModConfigSpec.BooleanValue FIRE = BUILDER
            .comment("Bend fire geometry and flame particles with the wind.")
            .define("fireSway", true);
    public static final ModConfigSpec.BooleanValue RAIN = BUILDER
            .comment("Replace vanilla rain sheets with wind-driven, world-space rain particles.")
            .define("particleRain", true);
    public static final ModConfigSpec.BooleanValue SNOW = BUILDER
            .comment("Replace vanilla snow sheets with tumbling, wind-driven snowflake particles.")
            .define("particleSnow", true);
    public static final ModConfigSpec.DoubleValue WEATHER_DENSITY = BUILDER
            .comment("Fair-weather rain/snow density. Thunderstorms use a bounded pool of up to 3200 particles.")
            .defineInRange("weatherParticleDensity", 1.0, 0.25, 2.0);
    public static final ModConfigSpec.BooleanValue STORM_SPRAY = BUILDER
            .comment("Wind-driven ground spray in outdoor thunderstorms, bounded to 256 live particles.")
            .define("stormSpray", true);
    public static final ModConfigSpec.BooleanValue STORM_FOG = BUILDER
            .comment("Biome-colored storm haze: yellow desert, grey-blue rain, pale snow. Fades with weather and sky exposure.")
            .define("stormFog", true);
    public static final ModConfigSpec.BooleanValue PARTICLE_PHYSICS = BUILDER
            .comment("Advect vanilla dust, ash, snowflakes and block debris with local wind and inertia.")
            .define("particlePhysics", true);
    public static final ModConfigSpec.BooleanValue SAND_DRIFT = BUILDER
            .comment("Emit wind-dragged grains and low dust trails from exposed dry sand and red sand.")
            .define("sandDrift", true);
    public static final ModConfigSpec.BooleanValue SANDSTORM = BUILDER
            .comment("Strong wind over deserts raises airborne dust and warm distance fog.")
            .define("sandstorms", true);
    public static final ModConfigSpec.DoubleValue SAND_DENSITY = BUILDER
            .defineInRange("sandParticleDensity", 1.0, .25, 2.0);
    public static final ModConfigSpec.BooleanValue WIND_SOUND = BUILDER
            .comment("Quiet wind ambience: silent up to 4 m/s, grows with local wind; both layers share a loudness cap.")
            .define("windSounds", true);
    public static final ModConfigSpec.DoubleValue WIND_VOLUME = BUILDER
            .defineInRange("windVolume", .65, 0, 1);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private ClientConfig() {}
}
