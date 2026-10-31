package com.shouyun.windweave;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Enable wind physics. Server settings are synchronized to clients.")
            .define("enabled", true);
    public static final ModConfigSpec.DoubleValue BASE_SPEED = BUILDER
            .comment("Automatic fair-weather wind speed in metres/second (one block = one metre).")
            .defineInRange("baseSpeed", 6.0, 0.0, 40.0);
    public static final ModConfigSpec.BooleanValue SHELTER = BUILDER
            .comment("Eight-block upwind collision shelter, with smooth wind recovery beyond two metres.")
            .define("shelter", true);
    public static final ModConfigSpec.DoubleValue SMOKE_RESPONSE = BUILDER
            .comment("Fraction of remaining wind velocity acquired by smoke each tick.")
            .defineInRange("smokeResponse", 0.06, 0.001, 1.0);
    public static final ModConfigSpec.DoubleValue ARROW_DRAG = BUILDER
            .comment("Wind-relative drag coefficient for arrows; calm retains vanilla flight.")
            .defineInRange("arrowDrag", 0.012, 0.0, 0.1);
    public static final ModConfigSpec.DoubleValue THROWN_DRAG = BUILDER
            .comment("Wind drag for snowballs, eggs, pearls, potions and experience bottles.")
            .defineInRange("thrownDrag", 0.025, 0.0, 0.1);
    public static final ModConfigSpec.DoubleValue TRIDENT_DRAG = BUILDER
            .comment("Wind drag for outbound tridents. Loyalty return is excluded.")
            .defineInRange("tridentDrag", 0.004, 0.0, 0.1);
    public static final ModConfigSpec.DoubleValue PROJECTILE_RESPONSE = BUILDER
            .comment("Crosswind response multiplier. 4 makes normal-speed player shots visibly bend. 1 retains the subtle 0.1.0 response.")
            .defineInRange("projectileResponse", 4.0, 0.0, 12.0);
    public static final ModConfigSpec.BooleanValue WIND_FIRE_SPREAD = BUILDER
            .comment("Wind helps ordinary fire and ordinary campfires ignite nearby downwind fuel. Respects doFireTick.")
            .define("windFireSpread", true);
    public static final ModConfigSpec.BooleanValue WIND_EXTINGUISH = BUILDER
            .comment("Strong local wind can extinguish ordinary fire and lit campfires. Eternal fire sources are protected. Respects doFireTick.")
            .define("windExtinguish", true);
    public static final ModConfigSpec.DoubleValue FIRE_EXTINGUISH_SPEED = BUILDER
            .comment("Local wind speed in metres/second at which blowout starts. Fuel-fed fire resists blowout.")
            .defineInRange("fireExtinguishSpeed", 18.0, 4.0, 40.0);
    public static final ModConfigSpec.DoubleValue FIRE_SPREAD_RESPONSE = BUILDER
            .comment("Multiplier for additional downwind ignition probability. Zero disables extra spread.")
            .defineInRange("fireSpreadResponse", 1.0, 0.0, 4.0);
    public static final ModConfigSpec.BooleanValue ITEM_WIND = BUILDER
            .comment("Wind pushes dropped items. Ground friction and stack size reduce motion; fluids are excluded.")
            .define("itemWind", true);
    public static final ModConfigSpec.BooleanValue TERRAIN_WIND = BUILDER
            .comment("Guide wind through narrow, tall-sided valleys and accelerate aligned channel flow.")
            .define("terrainWind", true);
    public static final ModConfigSpec.BooleanValue LIVING_WIND = BUILDER
            .comment("Strong wind pushes living entities; extreme gusts can lift them. Crouching helps resist wind.")
            .define("livingWind", true);
    public static final ModConfigSpec.DoubleValue LIFT_SPEED = BUILDER
            .comment("Wind speed where lifting force starts. Sustained lift requires overcoming vanilla gravity.")
            .defineInRange("liftStartSpeed", 36.0, 20, 70);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private Config() {}
}
