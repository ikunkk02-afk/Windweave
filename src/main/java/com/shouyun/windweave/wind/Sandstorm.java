package com.shouyun.windweave.wind;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.util.Mth;

/** A desert storm is driven by the existing authoritative wind, including fixed test winds. */
public final class Sandstorm {
    private Sandstorm() {}
    public static double strength(double speed) {
        double t = Mth.clamp((speed - 10) / 12, 0, 1);
        return t * t * (3 - 2 * t);
    }
    public static boolean arid(Level level, BlockPos pos) {
        var biome = level.getBiome(pos);
        return biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BADLANDS);
    }
}
