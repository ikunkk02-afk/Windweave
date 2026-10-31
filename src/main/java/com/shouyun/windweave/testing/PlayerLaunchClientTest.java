package com.shouyun.windweave.testing;

import com.mojang.logging.LogUtils;
import com.shouyun.windweave.Windweave;
import com.shouyun.windweave.wind.WindEvents;
import com.shouyun.windweave.wind.WindSavedData;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** End-to-end: real ServerPlayer item release -> server physics -> tracking packets -> client entity. */
@EventBusSubscriber(modid = Windweave.MODID, value = Dist.CLIENT)
public final class PlayerLaunchClientTest {
    private static int ticks, windCase, wait;
    private static volatile List<Launch> flying;
    private static volatile List<Measurement> arrived;
    private static final StringBuilder REPORT = new StringBuilder();
    private static boolean finished;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("windweave.clientSmokeTest") || finished) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.isPaused()) return;
        ticks++;
        try {
            if (ticks == 365) launch(mc);
            List<Measurement> measured = arrived;
            if (measured != null && ++wait >= 3) {
                for (Measurement m : measured) {
                    var client = mc.level.getEntity(m.id);
                    check(client != null, "Client tracks real player launch: " + m.type);
                    double clientDeflection = client.getX() - m.calmX;
                    if (windCase < 2) {
                        int sign = windCase == 0 ? 1 : -1;
                        check(m.deflection * sign > 3, "Server normal-speed crosswind displacement exceeds 3 blocks");
                        check(clientDeflection * sign > 2, "Client-visible crosswind displacement exceeds 2 blocks");
                    } else {
                        // Client may be a few ticks ahead: account for original launch scatter.
                        check(Math.abs(m.deflection) < .001, "Calm retains vanilla server trajectory");
                        check(Math.abs(clientDeflection) < .5, "Calm retains vanilla client trajectory");
                    }
                    REPORT.append("windCase=").append(windCase).append(' ').append(m.type)
                            .append(" server20TickDeflection=").append(m.deflection)
                            .append(" clientObservedDeflection=").append(clientDeflection).append('\n');
                }
                arrived = null;
                wait = 0;
                if (++windCase < 3) launch(mc);
                else {
                    Files.writeString(Path.of("player-launch-client-result.txt"), REPORT + "PASS\n");
                    LogUtils.getLogger().info("WINDWEAVE REAL PLAYER CLIENT LAUNCH TEST PASS\n{}", REPORT);
                    finished = true;
                    if (Boolean.getBoolean("windweave.clientTestExit")) mc.stop();
                }
            }
        } catch (Throwable error) {
            finished = true;
            LogUtils.getLogger().error("WINDWEAVE REAL PLAYER CLIENT LAUNCH TEST FAIL", error);
            try { Files.writeString(Path.of("player-launch-client-result.txt"), REPORT + "FAIL: " + error); }
            catch (java.io.IOException ignored) {}
            if (Boolean.getBoolean("windweave.clientTestExit")) mc.stop();
        }
    }

    private static void launch(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        var uuid = mc.player.getUUID();
        int mode = windCase;
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(uuid);
            var level = player.serverLevel();
            WindSavedData.get(level).setFixed(mode == 1 ? 270 : 90, mode == 2 ? 0 : 12);
            WindEvents.sync(level);
            player.getInventory().add(new ItemStack(Items.ARROW, 64));
            List<Launch> launches = new ArrayList<>();
            Item[] items = {Items.BOW, Items.SNOWBALL, Items.TRIDENT};
            for (int i = 0; i < items.length; i++) {
                player.moveTo(-5 + i * 10, 125, 9, 180, 0);
                player.setDeltaMovement(Vec3.ZERO);
                var stack = new ItemStack(items[i]);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                items[i].use(level, player, InteractionHand.MAIN_HAND);
                if (items[i] != Items.SNOWBALL) items[i].releaseUsing(stack, level, player, items[i].getUseDuration(stack, player) - 20);
                Vec3 pos = player.position();
                Projectile projectile = level.getEntitiesOfClass(Projectile.class, new AABB(pos.add(-2, -2, -2), pos.add(2, 4, 2)))
                        .stream().filter(p -> p.getOwner() == player && p.tickCount == 0).findFirst().orElseThrow();
                launches.add(new Launch(projectile, projectile.position(), projectile.getDeltaMovement()));
                player.stopUsingItem();
            }
            // Restore the viewing position; only this isolated test world's player is involved.
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), "tp @s 3 103 9 180 14");
            flying = List.copyOf(launches);
        });
    }

    @SubscribeEvent
    public static void levelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel) || flying == null) return;
        List<Launch> active = flying;
        if (active.getFirst().entity.tickCount < 20) return;
        List<Measurement> samples = new ArrayList<>();
        for (Launch launch : active) {
            int n = launch.entity.tickCount;
            double calmX = launch.start.x + launch.velocity.x * (1 - Math.pow(.99, n)) / .01;
            samples.add(new Measurement(launch.entity.getId(), launch.entity.getType().toString(), calmX, launch.entity.getX() - calmX));
        }
        flying = null;
        arrived = List.copyOf(samples);
    }

    private static void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private record Launch(Projectile entity, Vec3 start, Vec3 velocity) {}
    private record Measurement(int id, String type, double calmX, double deflection) {}
}
