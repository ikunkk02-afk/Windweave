package com.shouyun.windweave.testing;

import com.shouyun.windweave.Windweave;
import com.shouyun.windweave.wind.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;

/** Compare actual keyboard-controlled survival walking against rain/headwind, not just a force formula. */
@EventBusSubscriber(modid = Windweave.MODID, value = Dist.CLIENT)
public final class BodyWindClientTest {
    private static int ticks;
    private static double start, clearDistance;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("windweave.bodyWindClientTest")) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused() || mc.getSingleplayerServer() == null) return;
        ticks++;
        try {
            if (ticks == 1 || ticks == 100) {
                boolean rain = ticks == 100;
                var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
                server.execute(() -> {
                    var p = server.getPlayerList().getPlayer(uuid); var level = p.serverLevel();
                    p.setGameMode(GameType.SURVIVAL); p.setInvulnerable(true);
                    p.getFoodData().setFoodLevel(20); p.getAbilities().flying = false; p.onUpdateAbilities();
                    var commands = server.getCommands(); var source = server.createCommandSourceStack();
                    for (String command : new String[]{"fill -24 100 -8 24 100 24 minecraft:stone",
                            "fill -24 101 -8 24 105 24 minecraft:air", "weather " + (rain ? "rain" : "clear"),
                            "tp " + p.getScoreboardName() + " 0 101 8 90 0"})
                        commands.performPrefixedCommand(source, command);
                    level.setRainLevel(rain ? 1 : 0); level.setThunderLevel(0);
                    // A full 14 m/s headwind is above all default ordinary-rain speeds.
                    WindSavedData.get(level).setFixed(90, rain ? 14 : 0); WindEvents.sync(level);
                    p.setDeltaMovement(Vec3.ZERO); p.hurtMarked = true;
                });
            }
            if (ticks == 30 || ticks == 130) {
                start = mc.player.getX(); mc.options.keyUp.setDown(true);
            }
            if (ticks == 70 || ticks == 170) {
                mc.options.keyUp.setDown(false);
                double distance = start - mc.player.getX();
                if (ticks == 70) clearDistance = distance;
                else {
                    if (clearDistance < 6 || distance < clearDistance * .97 || distance > clearDistance * 1.03)
                        throw new IllegalStateException("Walking changed: calm=" + clearDistance + " rain/headwind=" + distance);
                    Files.writeString(Path.of("body-wind-client-result.txt"), "PASS: actual survival keyboard walking, 40 ticks each\n"
                            + "calmDistance=" + clearDistance + " blocks\nrain14mpsHeadwindDistance=" + distance
                            + " blocks\nratio=" + distance / clearDistance + "\nPASS\n");
                    var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
                    server.execute(() -> WindSavedData.get(server.getPlayerList().getPlayer(uuid).serverLevel()).setAutomatic());
                    mc.stop();
                }
            }
            if (ticks > 240) throw new IllegalStateException("Walking test timeout");
        } catch (Throwable error) {
            mc.options.keyUp.setDown(false);
            try { Files.writeString(Path.of("body-wind-client-result.txt"), "FAIL: " + error); } catch (Exception ignored) {}
            com.mojang.logging.LogUtils.getLogger().error("BODY WIND WALKING TEST FAILED", error); mc.stop();
        }
    }
}
