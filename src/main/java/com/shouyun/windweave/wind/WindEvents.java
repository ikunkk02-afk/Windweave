package com.shouyun.windweave.wind;

import com.shouyun.windweave.Config;
import com.shouyun.windweave.Windweave;
import com.shouyun.windweave.command.WindCommands;
import com.shouyun.windweave.mixin.AbstractArrowAccessor;
import com.shouyun.windweave.network.WindPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WindEvents {
    public static final TagKey<EntityType<?>> IMMUNE = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Windweave.MODID, "wind_immune_projectiles"));
    private WindEvents() {}
    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) { WindCommands.register(event.getDispatcher()); }

    @SubscribeEvent
    public static void projectileTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Projectile projectile)
                || !(projectile.level() instanceof ServerLevel level)
                || !Config.ENABLED.get() || projectile.isRemoved() || projectile.isInWaterOrBubble()
                || projectile.onGround() || projectile.getType().is(IMMUNE)) return;
        double coefficient;
        if (projectile instanceof AbstractArrow arrow) {
            if (((AbstractArrowAccessor) arrow).windweave$isInGround() || arrow.isNoPhysics()) return;
            coefficient = arrow instanceof ThrownTrident ? Config.TRIDENT_DRAG.get() : Config.ARROW_DRAG.get();
        } else if (projectile instanceof ThrowableProjectile) {
            coefficient = Config.THROWN_DRAG.get();
        } else {
            // Fireballs, fireworks and fishing hooks have special flight systems.
            return;
        }
        Vec3 correction = WindMath.dragCorrection(projectile.getDeltaMovement(),
                WindField.sample(level, projectile.position()).scale(1.0 / 20), coefficient, Config.PROJECTILE_RESPONSE.get());
        if (correction.lengthSqr() > 1.0E-12) {
            projectile.setDeltaMovement(projectile.getDeltaMovement().add(correction));
            projectile.hasImpulse = true;
        }
    }
    @SubscribeEvent
    public static void itemTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item)
                || !(item.level() instanceof ServerLevel level) || !Config.ITEM_WIND.get()
                || !Config.ENABLED.get() || item.isRemoved() || item.isInWaterOrBubble() || item.isInLava()
                || item.noPhysics) return;
        Vec3 wind = WindField.sample(level, item.position().add(0, .15, 0));
        Vec3 delta = WindMath.itemCorrection(item.getDeltaMovement(), wind, item.onGround(), item.getItem().getCount());
        if (delta.lengthSqr() > 1E-12) {
            item.setDeltaMovement(item.getDeltaMovement().add(delta));
            item.hasImpulse = true;
        }
    }
    @SubscribeEvent
    public static void levelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 10 == 0) sync(level);
    }
    public static void sync(ServerLevel level) {
        for (ServerPlayer player : level.players()) PacketDistributor.sendToPlayer(player, payload(player));
    }
    private static WindPayload payload(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 wind = WindSavedData.get(level).ambient(level);
        return new WindPayload(level.dimension().location(), wind.x, wind.z, WindSources.nearby(level, player.position()));
    }
    private static void syncPlayer(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player, payload(player));
    }
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) { syncPlayer(event); }
    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { syncPlayer(event); }
    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) { syncPlayer(event); }
}
