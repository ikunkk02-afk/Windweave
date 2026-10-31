package com.shouyun.windweave.client;

import net.minecraft.world.phys.Vec3;

/** Monotonic per-frame time and exact integration of a smoothed wind velocity.
 * A change in wind changes phase velocity, never the accumulated phase itself. */
public final class WindAnimationClock {
    private static final double RESPONSE_SECONDS = .30;
    private long lastNanos;
    private double seconds, travelX, travelZ;
    private Vec3 wind = Vec3.ZERO;

    public void advance(long now, boolean paused, Vec3 target) {
        if (lastNanos == 0) { lastNanos = now; return; }
        double delta = Math.min(.1, Math.max(0, (now - lastNanos) * 1E-9));
        lastNanos = now;
        if (paused) return;
        double decay = Math.exp(-delta / RESPONSE_SECONDS);
        // Integral of target + (old-target)*exp(-t/tau), rather than Euler stepping at game ticks.
        Vec3 difference = wind.subtract(target);
        travelX += .06 * (target.x * delta + difference.x * RESPONSE_SECONDS * (1 - decay));
        travelZ += .06 * (target.z * delta + difference.z * RESPONSE_SECONDS * (1 - decay));
        wind = target.add(difference.scale(decay));
        if (wind.distanceToSqr(target) < 1E-12) wind = target;
        seconds += delta;
    }

    public Vec3 wind() { return wind; }
    public double seconds() { return seconds; }
    public double travelX() { return travelX; }
    public double travelZ() { return travelZ; }
}
