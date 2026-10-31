package com.shouyun.windweave.wind;

import net.minecraft.world.phys.Vec3;

public final class WindMath {
    private WindMath() {}

    /** Compass bearing of where air MOVES TO: 0 north, 90 east, 180 south, 270 west. */
    public static Vec3 fromBearing(double degrees, double speed) {
        double radians = Math.toRadians(degrees);
        return new Vec3(Math.sin(radians) * speed, 0, -Math.cos(radians) * speed);
    }
    public static double bearing(Vec3 wind) {
        return (Math.toDegrees(Math.atan2(wind.x, -wind.z)) + 360.0) % 360.0;
    }

    /**
     * Correction to quadratic drag, subtracting still-air drag already approximated
     * by vanilla. Units: blocks/tick. Calm is exactly vanilla; crosswind bends flight,
     * headwind slows it, tailwind reduces drag. Gravity/vertical velocity stay vanilla.
     */
    public static Vec3 dragCorrection(Vec3 velocity, Vec3 wind, double coefficient) {
        return dragCorrection(velocity, wind, coefficient, 1);
    }

    public static Vec3 dragCorrection(Vec3 velocity, Vec3 wind, double coefficient, double response) {
        if (wind.horizontalDistanceSqr() < 1.0E-12 || coefficient == 0) return Vec3.ZERO;
        Vec3 horizontal = new Vec3(velocity.x, 0, velocity.z);
        Vec3 relative = new Vec3(wind.x - velocity.x, 0, wind.z - velocity.z);
        // Never subtract more still-air resistance than vanilla's 1% tick drag.
        // Otherwise a tailwind slower than a fast arrow could incorrectly speed it up.
        double effectiveDrag = Math.min(coefficient, 0.01 / Math.max(horizontal.length(), 0.01));
        Vec3 delta = relative.scale(relative.length()).add(horizontal.scale(horizontal.length())).scale(effectiveDrag);
        if (horizontal.lengthSqr() > 1E-8) {
            Vec3 alongFlight = horizontal.scale(delta.dot(horizontal) / horizontal.lengthSqr());
            delta = alongFlight.add(delta.subtract(alongFlight).scale(response));
        } else {
            delta = delta.scale(response);
        }
        double length = delta.length();
        return length > 0.08 ? delta.scale(0.08 / length) : delta;
    }
    public static Vec3 smokeStep(Vec3 drift, Vec3 target, double response) {
        return drift.add(target.subtract(drift).scale(response));
    }
    /** Ground friction needs a 5 m/s threshold; larger stacks have greater inertia. */
    public static Vec3 itemCorrection(Vec3 velocity, Vec3 wind, boolean grounded, int count) {
        double speed = wind.horizontalDistance();
        double threshold = grounded ? 5 : .5;
        if (speed <= threshold) return Vec3.ZERO;
        Vec3 target = wind.scale((1 - threshold / speed) / 20 * (grounded ? .45 : .85));
        double response = (grounded ? .12 : .055) / Math.sqrt(Math.max(1, count));
        Vec3 correction = new Vec3(target.x - velocity.x, 0, target.z - velocity.z).scale(response);
        double length = correction.length();
        return length > .045 ? correction.scale(.045 / length) : correction;
    }
}
