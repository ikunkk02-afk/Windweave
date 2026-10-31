package com.shouyun.windweave.client;

import com.shouyun.windweave.ClientConfig;
import com.shouyun.windweave.Config;
import com.shouyun.windweave.sound.WindSounds;
import com.shouyun.windweave.wind.WindField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Two ambient loops, smoothly mixed. Never spawns a new sound every gust or particle. */
public final class WindAudio {
    private static ClientLevel level;
    private static Loop wind, sand;
    private static int ticks;
    private static float windTarget, sandTarget;
    private WindAudio() {}
    public static void clear() {
        if (wind != null) wind.finish();
        if (sand != null) sand.finish();
        wind = sand = null; level = null; windTarget = sandTarget = 0; ticks = 0;
    }
    public static float windGain() { return wind == null ? 0 : wind.gain(); }
    public static float sandGain() { return sand == null ? 0 : sand.gain(); }
    public static boolean playing() {
        return wind != null && Minecraft.getInstance().getSoundManager().isActive(wind);
    }
    /** Storm texture shares the wind's loudness budget instead of adding another loud loop. */
    public record Mix(float wind, float sand) {}
    public static Mix mix(double speed, float storm, float volume) {
        double ramp = Mth.clamp((speed - 4) / 24, 0, 1);
        float total = (float) (Math.pow(ramp, 1.65) * .38) * Mth.clamp(volume, 0, 1);
        float sandShare = .35F * Mth.clamp(storm, 0, 1) * (float) Mth.clamp((speed - 8) / 14, 0, 1);
        return new Mix(total * (1 - sandShare), total * sandShare);
    }
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (level != mc.level) { clear(); level = mc.level; }
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        boolean enabled = Config.ENABLED.get() && ClientConfig.WIND_SOUND.get();
        double speed = enabled && mc.gameRenderer.getMainCamera().getFluidInCamera() == FogType.NONE
                ? WindField.sample(mc.level, mc.gameRenderer.getMainCamera().getPosition()).length() : 0;
        float volume = ClientConfig.WIND_VOLUME.get().floatValue();
        Mix mix = mix(speed, WindSand.intensity(), volume);
        windTarget = mix.wind(); sandTarget = mix.sand();
        if (!enabled || volume == 0) { clear(); level = mc.level; return; }
        // Also recover after F3+T, a sound-device switch or unmuting the weather category.
        if (++ticks % 10 == 1) {
            var manager = mc.getSoundManager();
            if (windTarget > .001F && (wind == null || !manager.isActive(wind))) {
                if (wind != null) wind.finish();
                wind = new Loop(false); manager.play(wind);
            }
            if (sandTarget > .001F && (sand == null || !manager.isActive(sand))) {
                if (sand != null) sand.finish();
                sand = new Loop(true); manager.play(sand);
            }
        }
    }
    private static final class Loop extends AbstractTickableSoundInstance {
        private final boolean dusty;
        private final ClientLevel owner;
        Loop(boolean dusty) {
            super(dusty ? WindSounds.SAND.get() : WindSounds.WIND.get(), SoundSource.WEATHER, RandomSource.create());
            this.dusty = dusty; owner = level; looping = true; delay = 0;
            relative = true; attenuation = Attenuation.NONE; volume = 0;
        }
        @Override public boolean canStartSilent() { return true; }
        @Override public void tick() {
            if (Minecraft.getInstance().level != owner || !ClientConfig.WIND_SOUND.get()) { finish(); return; }
            float target = dusty ? sandTarget : windTarget;
            volume += (target - volume) * (target < volume ? .10F : .04F);
            if (target == 0 && volume < .0008F) { finish(); return; }
            pitch = dusty ? .92F : .86F + .35F * windTarget;
        }
        float gain() { return volume; }
        void finish() { stop(); Minecraft.getInstance().getSoundManager().stop(this); }
    }
}
