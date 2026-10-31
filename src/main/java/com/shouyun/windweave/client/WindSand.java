package com.shouyun.windweave.client;

import com.shouyun.windweave.ClientConfig;
import com.shouyun.windweave.Config;
import com.shouyun.windweave.particle.WeatherParticles;
import com.shouyun.windweave.wind.Sandstorm;
import com.shouyun.windweave.wind.WindField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import java.util.ArrayList;
import java.util.List;

/** Two bounded layers: saltating surface grains and suspended storm dust. No block changes. */
public final class WindSand {
    public static final int MAX_PARTICLES = 1000;
    private static final List<WindSandParticle> ACTIVE = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static ClientLevel currentLevel;
    private static float intensity;
    private static long rendered;
    private WindSand() {}
    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(WeatherParticles.SAND.get(), sprites -> new WindSandParticle.Provider(false, sprites));
        event.registerSpriteSet(WeatherParticles.DUST.get(), sprites -> new WindSandParticle.Provider(true, sprites));
    }
    public static void clear() {
        ACTIVE.forEach(WindSandParticle::remove); ACTIVE.clear(); currentLevel = null; intensity = 0;
    }
    static boolean track(WindSandParticle p) {
        if (ACTIVE.size() >= MAX_PARTICLES) ACTIVE.removeIf(q -> !q.isAlive());
        if (ACTIVE.size() >= MAX_PARTICLES) return false;
        ACTIVE.add(p); return true;
    }
    public static float intensity() { return intensity; }
    public static int count() { return (int) ACTIVE.stream().filter(WindSandParticle::isAlive).count(); }
    public static long rendered() { return rendered; }
    static void renderedParticle() { rendered++; }
    public static boolean enabled(boolean storm) { return Config.ENABLED.get() && (storm ? ClientConfig.SANDSTORM.get() : ClientConfig.SAND_DRIFT.get()); }

    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level != currentLevel) { clear(); currentLevel = mc.level; }
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        BlockPos center = BlockPos.containing(camera);
        double target = 0;
        if (enabled(true) && mc.level.hasChunkAt(center) && Sandstorm.arid(mc.level, center)
                && mc.level.canSeeSky(center) && mc.level.getFluidState(center).isEmpty()) {
            int surface = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, center.getX(), center.getZ());
            target = Sandstorm.strength(WindField.sample(mc.level, camera).length())
                    * Mth.clamp((surface + 32 - camera.y) / 20, 0, 1);
        }
        intensity += ((float) target - intensity) * .06F;
        if (!enabled(true)) intensity = 0;
        double quality = switch (mc.options.particles().get()) { case ALL -> 1; case DECREASED -> .5; case MINIMAL -> .2; };
        int budget = Math.min(MAX_PARTICLES, (int) ((180 + 820 * intensity) * quality * ClientConfig.SAND_DENSITY.get()));
        ACTIVE.removeIf(p -> {
            if (!p.isAlive()) return true;
            if (!enabled(p.storm()) || p.getPos().distanceToSqr(camera) > 40 * 40) { p.remove(); return true; }
            return false;
        });
        while (ACTIVE.size() > budget) ACTIVE.removeLast().remove();
        if (!enabled(false) && !enabled(true)) return;
        int attempts = (int) ((26 + intensity * 110) * quality * ClientConfig.SAND_DENSITY.get());
        for (int i = 0; i < attempts && ACTIVE.size() < budget; i++) {
            double x = camera.x + (RANDOM.nextDouble() * 2 - 1) * 22;
            double z = camera.z + (RANDOM.nextDouble() * 2 - 1) * 22;
            BlockPos column = BlockPos.containing(x, camera.y, z);
            if (!mc.level.hasChunkAt(column)) continue;
            int y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
            if (y < camera.y - 22 || y > camera.y + 10 || y >= mc.level.getMaxBuildHeight()) continue;
            BlockPos air = new BlockPos(column.getX(), y, column.getZ());
            var surface = mc.level.getBlockState(air.below());
            if (!surface.is(BlockTags.SAND) || !mc.level.getFluidState(air).isEmpty() || mc.level.isRainingAt(air)) continue;
            Vec3 wind = WindField.sample(mc.level, new Vec3(x, y + .15, z));
            if (wind.length() < 3) continue;
            boolean storm = enabled(true) && Sandstorm.arid(mc.level, air)
                    && RANDOM.nextDouble() < Sandstorm.strength(wind.length()) * .8;
            if (!storm && !enabled(false)) continue;
            boolean dust = storm || RANDOM.nextFloat() < .22F;
            double py = storm ? Math.max(y + .3, camera.y - 3) + RANDOM.nextDouble() * 11 : y + .06;
            BlockPos spawn = BlockPos.containing(x, py, z);
            if (py >= mc.level.getMaxBuildHeight() || !mc.level.getBlockState(spawn).isAir()) continue;
            var p = mc.particleEngine.createParticle(dust ? WeatherParticles.DUST.get() : WeatherParticles.SAND.get(),
                    x, py, z, surface.is(Blocks.RED_SAND) ? 1 : 0, storm ? 1 : 0, 0);
            if (p instanceof WindSandParticle sand) sand.natural();
        }
    }
}
