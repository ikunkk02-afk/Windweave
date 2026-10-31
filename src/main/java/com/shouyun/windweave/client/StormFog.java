package com.shouyun.windweave.client;

import com.shouyun.windweave.ClientConfig;
import com.shouyun.windweave.wind.Sandstorm;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Biome-aware haze. Sunlight is independent of vanilla's weather-darkened fog RGB. */
public final class StormFog {
    private static long terrainFrames;
    private static float lastEnd, desert, snow;
    private static Vec3 lastColor = Vec3.ZERO;
    private static ClientLevel currentLevel;
    private StormFog() {}
    public static long terrainFrames() { return terrainFrames; }
    public static float lastEnd() { return lastEnd; }
    public static Vec3 lastColor() { return lastColor; }
    public static void clear() { currentLevel = null; desert = snow = 0; lastColor = Vec3.ZERO; }

    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level != currentLevel) { clear(); currentLevel = mc.level; }
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        var pos = mc.gameRenderer.getMainCamera().getBlockPosition();
        if (!mc.level.hasChunkAt(pos)) return;
        float arid = Sandstorm.arid(mc.level, pos) ? 1 : 0;
        float cold = mc.level.getBiome(pos).value().getPrecipitationAt(pos) == Biome.Precipitation.SNOW ? 1 : 0;
        desert += (arid - desert) * .08F;
        snow += (cold - snow) * .08F;
    }

    private static float strength(Camera camera, float partial) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimensionType().hasSkyLight()
                || camera.getFluidInCamera() != FogType.NONE) return 0;
        if (camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) return 0;
        float sky = Mth.clamp((level.getBrightness(LightLayer.SKY, camera.getBlockPosition()) - 2) / 13F, 0, 1);
        float rain = level.getRainLevel(partial);
        float weather = ClientConfig.STORM_FOG.get()
                ? Mth.lerp(WindWeather.stormStrength(level, partial), rain * rain * .4F, 1) : 0;
        return Math.max(weather, WindSand.intensity()) * sky;
    }

    public static Vec3 palette(float daylight, float arid, float cold) {
        // Night stays dim; daytime fog must not receive rain/thunder darkening twice.
        float light = Mth.lerp(Mth.clamp(daylight, 0, 1), .42F, 1);
        float r = Mth.lerp(cold, .60F, .70F), g = Mth.lerp(cold, .66F, .76F), b = Mth.lerp(cold, .70F, .81F);
        return new Vec3(Mth.lerp(arid, r, .78F) * light, Mth.lerp(arid, g, .64F) * light, Mth.lerp(arid, b, .39F) * light);
    }

    private static float sandTint() { return Math.max(desert, WindSand.intensity()); }

    public static void color(ViewportEvent.ComputeFogColor event) {
        float partial = (float) event.getPartialTick();
        float strength = strength(event.getCamera(), partial);
        if (strength <= .001F) return;
        var level = Minecraft.getInstance().level;
        float daylight = Mth.clamp(Mth.cos(level.getTimeOfDay(partial) * Mth.TWO_PI) * 2 + .5F, 0, 1);
        Vec3 target = palette(daylight, sandTint(), snow);
        float mix = strength * .94F;
        event.setRed(Mth.lerp(mix, event.getRed(), (float) target.x));
        event.setGreen(Mth.lerp(mix, event.getGreen(), (float) target.y));
        event.setBlue(Mth.lerp(mix, event.getBlue(), (float) target.z));
        lastColor = new Vec3(event.getRed(), event.getGreen(), event.getBlue());
    }

    public static void render(ViewportEvent.RenderFog event) {
        float strength = strength(event.getCamera(), (float) event.getPartialTick());
        if (strength <= .001F) return;
        float end = Math.min(event.getFarPlaneDistance(), Mth.lerp(sandTint(), 52, 30));
        float start = Math.min(event.getNearPlaneDistance(), Mth.lerp(sandTint(), 5, 3));
        event.setFarPlaneDistance(Mth.lerp(strength, event.getFarPlaneDistance(), end));
        event.setNearPlaneDistance(Mth.lerp(strength, event.getNearPlaneDistance(), start));
        event.setCanceled(true);
        if (event.getMode() == net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) {
            terrainFrames++; lastEnd = event.getFarPlaneDistance();
        }
    }
}
