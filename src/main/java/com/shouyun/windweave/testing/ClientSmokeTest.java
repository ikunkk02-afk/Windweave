package com.shouyun.windweave.testing;

import com.mojang.logging.LogUtils;
import com.shouyun.windweave.Windweave;
import com.shouyun.windweave.wind.ClientWindState;
import com.shouyun.windweave.client.WindFlowVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Development-only end-to-end particle + payload check. Excluded from distributable JAR. */
@EventBusSubscriber(modid = Windweave.MODID, value = Dist.CLIENT)
public final class ClientSmokeTest {
    private static int ticks;
    private static Particle cosy, signal;
    private static Vec3 cosyStart, signalStart;
    private static final StringBuilder REPORT = new StringBuilder();

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("windweave.clientSmokeTest")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.isPaused()) return;
        ticks++;
        try {
            if (ticks == 1) {
                var uuid = mc.player.getUUID();
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    var source = server.createCommandSourceStack();
                    String[] commands = {
                            "fill -12 100 -12 24 100 12 minecraft:white_concrete",
                            "fill -12 101 -12 24 112 12 minecraft:air",
                            "setblock 0 101 0 minecraft:campfire",
                            "setblock 0 100 -4 minecraft:hay_block",
                            "setblock 0 101 -4 minecraft:soul_campfire",
                            "time set noon", "weather clear", "gamerule doDaylightCycle false",
                            "kill @e[type=windweave:wind_indicator]",
                            "windweave set east 12",
                            "give @a minecraft:bow", "give @a minecraft:arrow 64", "give @a minecraft:snowball 64",
                            "tp " + server.getPlayerList().getPlayer(uuid).getScoreboardName() + " 3 103 9 180 14"
                    };
                    for (String command : commands) server.getCommands().performPrefixedCommand(source, command);
                });
            }
            if (ticks == 60 || ticks == 150 || ticks == 240) {
                double expected = ticks == 60 ? 12 : ticks == 150 ? -12 : 0;
                check(Math.abs(ClientWindState.ambient(mc.level).x - expected) < .01, "Server wind payload synchronized: " + expected);
                cosy = mc.particleEngine.createParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, 0, 105, 0, 0, .07, 0);
                signal = mc.particleEngine.createParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, 0, 105, -4, 0, .07, 0);
                check(cosy != null && signal != null, "Both vanilla campfire smoke providers loaded");
                cosyStart = cosy.getPos();
                signalStart = signal.getPos();
            }
            if (ticks == 100 || ticks == 190 || ticks == 280) {
                double dx = cosy.getPos().x - cosyStart.x;
                double sx = signal.getPos().x - signalStart.x;
                check(cosy.getPos().y > cosyStart.y + 1, "Vanilla smoke buoyancy retained");
                if (ticks == 100) {
                    check(dx > 10 && sx > 10, "East wind moves both real smoke particles east: " + dx + ", " + sx);
                    check(WindFlowVisuals.streakCount() > 10 && WindFlowVisuals.averageVelocity().x > .2,
                            "Moving flow trails follow east wind");
                    check(WindFlowVisuals.renderedSegments() > 0, "Flow trail geometry reached the world render pass");
                    command(mc, "windweave set west 12");
                } else if (ticks == 190) {
                    check(dx < -10 && sx < -10, "West wind reverses both real smoke particles: " + dx + ", " + sx);
                    check(WindFlowVisuals.averageVelocity().x < -.2, "Moving flow trails reverse with west wind");
                    command(mc, "windweave calm");
                } else {
                    check(Math.abs(dx) < 1 && Math.abs(sx) < 1, "Calm preserves small vanilla smoke drift: " + dx + ", " + sx);
                    check(WindFlowVisuals.streakCount() == 0, "Flow trails fade away in calm air");
                    command(mc, "windweave set east 12");
                    java.nio.file.Files.writeString(java.nio.file.Path.of("smoke-test-result.txt"), REPORT + "PASS\n");
                    LogUtils.getLogger().info("WINDWEAVE CLIENT SMOKE TEST PASS\n{}", REPORT);
                }
            }
            if (ticks == 305) net.minecraft.client.Screenshot.grab(mc.gameDirectory, "flow-lines.png", mc.getMainRenderTarget(), message -> {});
            if (ticks == 310) playerCommand(mc, "windweave flow off");
            if (ticks == 330) {
                check(!ClientWindState.flowEnabled() && WindFlowVisuals.streakCount() == 0, "Per-player flow off switch clears trails");
                check(ClientWindState.ambient(mc.level).x > 11.9, "Flow off does not disable physics");
                playerCommand(mc, "windweave flow on");
            }
            if (ticks == 355) {
                check(ClientWindState.flowEnabled() && WindFlowVisuals.streakCount() > 10, "Flow on restores moving trails");
                java.nio.file.Files.writeString(java.nio.file.Path.of("smoke-test-result.txt"), REPORT + "PASS\n");
            }
        } catch (Throwable error) {
            LogUtils.getLogger().error("WINDWEAVE CLIENT SMOKE TEST FAILED at tick " + ticks, error);
            try { java.nio.file.Files.writeString(java.nio.file.Path.of("smoke-test-result.txt"), REPORT + "FAIL: " + error); }
            catch (java.io.IOException ignored) { }
            ticks = 10000;
        }
    }

    private static void command(Minecraft mc, String command) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static void playerCommand(Minecraft mc, String command) {
        var server = mc.getSingleplayerServer();
        var uuid = mc.player.getUUID();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.getPlayerList().getPlayer(uuid).createCommandSourceStack(), command));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        REPORT.append("PASS: ").append(message).append('\n');
    }
}
