package com.shouyun.windweave.client;

import com.shouyun.windweave.ClientConfig;
import com.shouyun.windweave.particle.WeatherParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

import java.util.ArrayList;
import java.util.List;

/** Bounded weather emitter. ParticleEngine owns ticking, lighting, collision and rendering. */
public final class WindWeather {
    public static final int MAX_PARTICLES = 3200;
    public static final int MAX_SPRAY = 256;
    private static final List<StormSprayParticle> SPRAY = new ArrayList<>();
    private static final List<WindWeatherParticle> ACTIVE = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static ClientLevel currentLevel;
    private static long renderedQuads;
    private WindWeather() {}

    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(WeatherParticles.RAIN.get(), sprites -> new WindWeatherParticle.Provider(false, sprites));
        event.registerSpriteSet(WeatherParticles.SNOW.get(), sprites -> new WindWeatherParticle.Provider(true, sprites));
        event.registerSpriteSet(WeatherParticles.SPRAY.get(), StormSprayParticle.Provider::new);
    }

    public static boolean enabled(Biome.Precipitation kind) {
        return kind == Biome.Precipitation.RAIN ? ClientConfig.RAIN.get()
                : kind == Biome.Precipitation.SNOW && ClientConfig.SNOW.get();
    }

    public static void clear() {
        ACTIVE.forEach(WindWeatherParticle::remove);
        ACTIVE.clear(); currentLevel = null;
        SPRAY.forEach(StormSprayParticle::remove); SPRAY.clear();
    }

    static boolean track(WindWeatherParticle particle) {
        if (ACTIVE.size() >= MAX_PARTICLES) return false;
        ACTIVE.add(particle); return true;
    }
    static void rendered(int quads) { renderedQuads += quads; }
    static boolean trackSpray(StormSprayParticle particle) { if (SPRAY.size() >= MAX_SPRAY) return false; SPRAY.add(particle); return true; }
    public static int sprayCount() { return SPRAY.size(); }
    public static long renderedQuads() { return renderedQuads; }
    public static int count(boolean snow) { return (int) ACTIVE.stream().filter(p -> p.isAlive() && p.snow() == snow).count(); }
    public static long snowVariants() { return ACTIVE.stream().filter(p -> p.isAlive() && p.snow()).map(WindWeatherParticle::variant).distinct().count(); }
    /** Thunder overrides density/quality to the maximum shared pool; explicit visual switches still apply. */
    public static int budget(ClientLevel level, double quality) {
        float rain = level.getRainLevel(1);
        double normal = 1000 * quality * rain * ClientConfig.WEATHER_DENSITY.get();
        float storm = stormStrength(level, 1);
        return Mth.clamp((int) Math.round(Mth.lerp(storm, normal, MAX_PARTICLES * rain)), 0, MAX_PARTICLES);
    }
    public static float stormStrength(ClientLevel level, float partial) {
        float t = Mth.clamp(level.getThunderLevel(partial) / .95F, 0, 1);
        return t * t * (3 - 2 * t);
    }
    public static Vec3 averageVelocity(boolean snow) {
        Vec3 sum = Vec3.ZERO; int count = 0;
        for (var particle : ACTIVE) if (particle.isAlive() && particle.snow() == snow) { sum = sum.add(particle.velocity()); count++; }
        return count == 0 ? Vec3.ZERO : sum.scale(1.0 / count);
    }

    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level != currentLevel) { clear(); currentLevel = mc.level; }
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        Vec3 center = mc.gameRenderer.getMainCamera().getPosition();
        float rain = mc.level.getRainLevel(1);
        double quality = switch (mc.options.particles().get()) { case ALL -> 1; case DECREASED -> .5; case MINIMAL -> .2; };
        int budget = budget(mc.level, quality);
        ACTIVE.removeIf(p -> {
            if (!p.isAlive()) return true;
            if (rain <= .001 || !enabled(p.snow() ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN)
                    || p.getPos().distanceToSqr(center) > 32 * 32) { p.remove(); return true; }
            return false;
        });
        SPRAY.removeIf(p -> {
            if (!p.isAlive()) return true;
            if (rain < .01 || !ClientConfig.STORM_SPRAY.get() || !ClientConfig.RAIN.get()
                    || p.getPos().distanceToSqr(center) > 28 * 28) { p.remove(); return true; }
            return false;
        });
        // Apply a reduced density/quality budget to already existing particles too.
        while (ACTIVE.size() > budget) ACTIVE.removeLast().remove();
        if (budget == 0 || (!ClientConfig.RAIN.get() && !ClientConfig.SNOW.get())) return;
        int attempts = (int) Math.ceil(Mth.lerp(stormStrength(mc.level, 1),
                120 * quality * rain * ClientConfig.WEATHER_DENSITY.get(), 720 * rain));
        for (int i = 0; i < attempts && ACTIVE.size() < budget; i++) {
            double x = center.x + (RANDOM.nextDouble() * 2 - 1) * 18;
            double z = center.z + (RANDOM.nextDouble() * 2 - 1) * 18;
            double y = center.y - 6 + RANDOM.nextDouble() * 22;
            BlockPos pos = BlockPos.containing(x, y, z);
            // Never load chunks. The heightmap keeps initial particles above roofs, leaves and fluids.
            if (!mc.level.hasChunkAt(pos) || y < mc.level.getMinBuildHeight() || y >= mc.level.getMaxBuildHeight()
                    || y < mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ())) continue;
            var kind = mc.level.getBiome(pos).value().getPrecipitationAt(pos);
            if (!enabled(kind)) continue;
            var type = kind == Biome.Precipitation.SNOW ? WeatherParticles.SNOW.get() : WeatherParticles.RAIN.get();
            var particle = mc.particleEngine.createParticle(type, x, y, z, 0, 0, 0);
            if (particle instanceof WindWeatherParticle weather) weather.natural();
        }
        if (stormStrength(mc.level, 1) > .3 && ClientConfig.STORM_SPRAY.get() && ClientConfig.RAIN.get()) {
            for (int i = 0; i < 12 && SPRAY.size() < MAX_SPRAY; i++) {
                int x = (int) Math.floor(center.x + (RANDOM.nextDouble() * 2 - 1) * 15);
                int z = (int) Math.floor(center.z + (RANDOM.nextDouble() * 2 - 1) * 15);
                if (!mc.level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
                int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos pos = new BlockPos(x, ground, z);
                if (Math.abs(ground - center.y) > 7 || mc.level.getBiome(pos).value().getPrecipitationAt(pos) != Biome.Precipitation.RAIN) continue;
                mc.particleEngine.createParticle(WeatherParticles.SPRAY.get(), x + RANDOM.nextDouble(), ground + .12, z + RANDOM.nextDouble(), 0, 0, 0);
            }
        }
    }
}
