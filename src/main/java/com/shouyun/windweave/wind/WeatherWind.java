package com.shouyun.windweave.wind;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Shared natural-weather model; server sends its resulting vector to every visual and physics consumer. */
public final class WeatherWind {
    private WeatherWind() {}
    public static Vec3 sample(double seconds, double phase, double baseSpeed, float rainLevel, float thunderLevel) {
        double rain = smooth(Mth.clamp(rainLevel, 0, 1));
        double storm = smooth(Mth.clamp(thunderLevel, 0, 1)) * rain;
        double direction = 90 + 65 * Math.sin(seconds / 180 + phase) + 15 * Math.sin(seconds / 47 + phase)
                + rain * (32 + 12 * Math.sin(seconds / 39 + phase))
                + storm * (48 + 25 * Math.sin(seconds / 18 + phase));
        double gust = 1 + .24 * Math.sin(seconds / 7 + phase) + .12 * Math.sin(seconds / 2.7 + phase);
        if (baseSpeed <= 0) return Vec3.ZERO;
        double ordinary = baseSpeed * gust * (1 + .65 * rain);
        // Normal thunder is playable. Extreme fronts are selected once per two-minute
        // window, never independently every tick. The stateless seed keeps reloads stable.
        double front = Math.pow(.5 + .5 * Math.sin(seconds / 11 + phase), 4);
        double stormSpeed = Math.min(32, Math.max(ordinary * 1.2, 10 + baseSpeed * .65) + 6 * front);
        double extreme = extremeFront(seconds, phase);
        stormSpeed = Mth.lerp(extreme, stormSpeed, 80);
        double speed = Mth.lerp(storm, ordinary, stormSpeed);
        return WindMath.fromBearing(direction, Math.min(80, Math.max(0, speed)));
    }
    private static double smooth(double value) { return value * value * (3 - 2 * value); }

    private static double extremeFront(double seconds, double phase) {
        long window = (long) Math.floor(seconds / 120);
        if (random(window, phase, 0x243f6a8885a308d3L) >= .06) return 0;
        double elapsed = seconds - window * 120 - (20 + random(window, phase, 0x13198a2e03707344L) * 16);
        // 20 seconds rising, two seconds at the cap, 32 seconds receding. Zero slope at every join.
        if (elapsed < 0 || elapsed >= 54) return 0;
        if (elapsed < 20) return smooth(elapsed / 20);
        if (elapsed < 22) return 1;
        return 1 - smooth((elapsed - 22) / 32);
    }

    private static double random(long window, double phase, long salt) {
        long bits = window * 0x9e3779b97f4a7c15L ^ Double.doubleToLongBits(phase) ^ salt;
        bits = (bits ^ (bits >>> 30)) * 0xbf58476d1ce4e5b9L;
        bits = (bits ^ (bits >>> 27)) * 0x94d049bb133111ebL;
        bits ^= bits >>> 31;
        return (bits >>> 11) * 0x1.0p-53;
    }
}
