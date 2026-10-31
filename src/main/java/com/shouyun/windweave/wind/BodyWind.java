package com.shouyun.windweave.wind;

import com.shouyun.windweave.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class BodyWind {
    private BodyWind() {}

    public static Vec3 correction(Vec3 movement, Vec3 wind, boolean grounded, boolean crouching, double size) {
        double speed = wind.horizontalDistance();
        // Ordinary rain must not interfere with walking or cause velocity packets.
        if (speed <= 16) return Vec3.ZERO;
        double resistance = Math.clamp(1 / Math.sqrt(Math.max(.5, size)), .25, 1.35);
        double brace = crouching && grounded ? .3 : 1;
        double excess = speed - 16;
        double gain = Math.min(.018, excess * excess * .000025) * resistance * brace * (grounded ? .45 : 1);
        Vec3 horizontal = new Vec3((wind.x / 20 - movement.x) * gain, 0, (wind.z / 20 - movement.z) * gain);
        double maxPush = grounded ? .035 : .06;
        if (horizontal.length() > maxPush) horizontal = horizontal.normalize().scale(maxPush);
        double lift = Math.clamp((speed - Config.LIFT_SPEED.get()) / 20, 0, 1) * .13 * resistance * brace;
        lift += Math.max(0, wind.y) / 20 * .08 * resistance * brace;
        return horizontal.add(0, Math.min(lift, Math.max(0, .65 - movement.y)), 0);
    }

    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity body) || !(body.level() instanceof ServerLevel level)
                || !Config.ENABLED.get() || !Config.LIVING_WIND.get() || body.isRemoved() || !body.isAlive()
                || body.isInWaterOrBubble() || body.isInLava() || body.isPassenger() || body.isSleeping()
                || body instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
                || body instanceof net.minecraft.world.entity.boss.wither.WitherBoss
                || body instanceof Player player && (player.isSpectator() || player.getAbilities().flying)) return;
        Vec3 wind = WindField.sample(level, body.position().add(0, body.getBbHeight() * .65, 0));
        Vec3 delta = correction(body.getDeltaMovement(), wind, body.onGround(), body.isShiftKeyDown(), body.getBbWidth() * body.getBbHeight());
        if (delta.lengthSqr() < 1E-10) return;
        body.setDeltaMovement(body.getDeltaMovement().add(delta));
        body.hasImpulse = true;
        // Velocity must reach the controlling client too, not only other trackers.
        if (body instanceof ServerPlayer player) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new com.shouyun.windweave.network.BodyWindPayload(level.dimension().location(), delta));
            // Only an authoritative local lifting force can support this flight.
            // Do not grant creative flight or globally disable server movement checks.
            if (delta.y > .075 && !body.onGround())
                ((com.shouyun.windweave.mixin.ServerFlightAccessor) player.connection).windweave$setUnsupportedFlightTicks(0);
        }
    }
}
