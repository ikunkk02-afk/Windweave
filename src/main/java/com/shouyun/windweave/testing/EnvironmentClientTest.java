package com.shouyun.windweave.testing;

import com.mojang.logging.LogUtils;
import com.shouyun.windweave.Windweave;
import com.shouyun.windweave.client.TerrainWindData;
import com.shouyun.windweave.client.WindTerrainShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.opengl.GL20;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Isolated scene: real chunk compilation, GPU shader linking/draws, payloads and flame particles. */
@EventBusSubscriber(modid = Windweave.MODID, value = Dist.CLIENT)
public final class EnvironmentClientTest {
    private static int ticks;
    private static Particle flame, smoke, largeSmoke;
    private static Vec3 start, smokeStart, largeSmokeStart;
    private static CompletableFuture<Void> reloading;
    private static final StringBuilder REPORT = new StringBuilder();

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("windweave.environmentClientTest")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.isPaused()) return;
        ticks++;
        try {
            if (ticks == 1) {
                var uuid = mc.player.getUUID();
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    var source = server.createCommandSourceStack();
                    for (String command : new String[]{
                            "fill 38 99 -16 86 99 16 minecraft:dirt",
                            "fill 38 100 -16 86 100 16 minecraft:grass_block",
                            "fill 38 101 -16 86 114 16 minecraft:air",
                            "fill 43 101 -12 59 101 -4 minecraft:short_grass",
                            "fill 61 101 -12 63 101 -4 minecraft:tall_grass[half=lower]",
                            "fill 61 102 -12 63 102 -4 minecraft:tall_grass[half=upper]",
                            "fill 43 100 1 65 100 12 minecraft:water",
                            "fill 69 101 -5 69 105 -5 minecraft:oak_log",
                            "fill 67 104 -7 71 107 -3 minecraft:oak_leaves[persistent=true]",
                            "fill 68 108 -6 70 108 -4 minecraft:oak_leaves[persistent=true]",
                            "setblock 76 100 0 minecraft:netherrack",
                            "setblock 76 101 0 minecraft:fire",
                            "setblock 74 101 6 minecraft:campfire",
                            "time set noon", "weather clear", "gamerule doDaylightCycle false", "gamerule doFireTick false",
                            "windweave set east 12", "gamemode spectator " + server.getPlayerList().getPlayer(uuid).getScoreboardName(),
                            "tp " + server.getPlayerList().getPlayer(uuid).getScoreboardName() + " 64 105 21 180 19"
                    }) server.getCommands().performPrefixedCommand(source, command);
                    server.getCommands().performPrefixedCommand(server.getPlayerList().getPlayer(uuid).createCommandSourceStack(), "windweave flow off");
                });
                mc.options.hideGui = true;
                mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FANCY);
            }
            if (ticks == 70 || ticks == 160 || ticks == 250) {
                double target = ticks == 70 ? 12 : ticks == 160 ? -12 : 0;
                check(Math.abs(WindTerrainShaders.visualWind().x - target) < .02, "Visual wind follows synchronized target " + target);
                flame = mc.particleEngine.createParticle(ParticleTypes.FLAME, 64, 103, -1, 0, .02, 0);
                check(flame != null, "Vanilla flame provider created a particle");
                flame.setLifetime(80);
                start = flame.getPos();
                smoke = mc.particleEngine.createParticle(ParticleTypes.SMOKE, 64, 103, -1, 0, .02, 0);
                largeSmoke = mc.particleEngine.createParticle(ParticleTypes.LARGE_SMOKE, 64, 103, -1, 0, .02, 0);
                check(smoke != null && largeSmoke != null, "Ordinary fire's normal and large smoke providers loaded");
                smoke.setLifetime(80); largeSmoke.setLifetime(80);
                smokeStart = smoke.getPos(); largeSmokeStart = largeSmoke.getPos();
            }
            if (ticks == 90 || ticks == 180 || ticks == 270) {
                double drift = flame.getPos().x - start.x;
                double smokeDrift = smoke.getPos().x - smokeStart.x, largeDrift = largeSmoke.getPos().x - largeSmokeStart.x;
                if (ticks == 90) {
                    check(drift > 1, "Real flame particle bends east: " + drift);
                    check(smokeDrift > 3 && largeDrift > 3, "Ordinary black smoke and large black smoke drift east: " + smokeDrift + ", " + largeDrift);
                    check(smoke.getPos().y > smokeStart.y && largeSmoke.getPos().y > largeSmokeStart.y, "Black smoke retains vanilla buoyancy");
                    check(WindTerrainShaders.available(), "All four terrain shaders compiled and linked on the GPU");
                    check(mc.gameRenderer.getMainCamera().getPosition().y > 104, "Scene camera remains above the test terrain");
                    check(WindTerrainShaders.uniformUploads() > 100, "Terrain shaders receive live per-frame uniforms");
                    check(WindTerrainShaders.frameCount() > ticks, "Animation advances at render frames, above the game tick rate");
                    for (int kind = 1; kind <= 4; kind++) check(TerrainWindData.markedBlocks(kind) > 0,
                            "Chunk compiler encoded terrain kind " + kind + ": " + TerrainWindData.markedBlocks(kind));
                    var shader = GameRenderer.getRendertypeCutoutShader();
                    check(shader.getName().equals("windweave:terrain_cutout"), "Vanilla cutout draw uses namespaced wind shader");
                    check(GL20.glGetUniformf(shader.getId(), GL20.glGetUniformLocation(shader.getId(), "Wind")) > 11.9,
                            "Actual GPU wind uniform is east 12 m/s");
                    REPORT.append(TerrainGpuProbe.verify(shader));
                    REPORT.append(WaterTextureGpuProbe.verify(GameRenderer.getRendertypeTranslucentShader()));
                    Screenshot.grab(mc.gameDirectory, "environment-east.png", mc.getMainRenderTarget(), message -> {});
                    command(mc, "windweave set west 12");
                } else if (ticks == 180) {
                    check(drift < -1, "Real flame particle reverses west: " + drift);
                    check(smokeDrift < -3 && largeDrift < -3, "Ordinary black smoke and large black smoke reverse west: " + smokeDrift + ", " + largeDrift);
                    Screenshot.grab(mc.gameDirectory, "environment-west.png", mc.getMainRenderTarget(), message -> {});
                    command(mc, "windweave calm");
                } else {
                    check(Math.abs(drift) < .15, "Calm keeps only vanilla flame drift: " + drift);
                    check(Math.abs(smokeDrift) < .15 && Math.abs(largeDrift) < .15, "Calm preserves vanilla black smoke drift");
                    check(WindTerrainShaders.visualWind().lengthSqr() < 1E-8, "All terrain wind deformation settles to zero in calm");
                    Screenshot.grab(mc.gameDirectory, "environment-calm.png", mc.getMainRenderTarget(), message -> {});
                    reloading = mc.reloadResourcePacks();
                }
            }
            if (ticks == 320) {
                check(reloading != null && reloading.isDone() && !reloading.isCompletedExceptionally(), "Resource pack reload completed");
                check(WindTerrainShaders.available(), "Wind shaders re-register after resource reload");
                mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FAST);
                mc.levelRenderer.allChanged();
                command(mc, "windweave set east 24");
            }
            if (ticks == 380) {
                check(GameRenderer.getRendertypeSolidShader().getName().equals("windweave:terrain_solid"), "Fast leaves use the solid wind shader");
                check(Math.abs(WindTerrainShaders.visualWind().x - 24) < .02, "Strong wind also reaches terrain shaders");
                Screenshot.grab(mc.gameDirectory, "environment-strong-fast.png", mc.getMainRenderTarget(), message -> {});
                Files.writeString(Path.of("environment-test-result.txt"), REPORT + "PASS\n");
                LogUtils.getLogger().info("WINDWEAVE ENVIRONMENT CLIENT PASS\n{}", REPORT);
                mc.stop();
            }
        } catch (Throwable error) {
            LogUtils.getLogger().error("WINDWEAVE ENVIRONMENT CLIENT FAIL at tick " + ticks, error);
            try { Files.writeString(Path.of("environment-test-result.txt"), REPORT + "FAIL: " + error); }
            catch (java.io.IOException ignored) {}
            mc.stop();
        }
    }

    private static void command(Minecraft mc, String command) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        REPORT.append("PASS: ").append(message).append('\n');
    }
}
