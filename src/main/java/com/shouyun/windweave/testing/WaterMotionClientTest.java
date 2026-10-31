package com.shouyun.windweave.testing;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.shouyun.windweave.Windweave;
import com.shouyun.windweave.client.WindTerrainShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Samples water from the real world framebuffer and inspects the actual translucent program. */
@EventBusSubscriber(modid = Windweave.MODID, value = Dist.CLIENT)
public final class WaterMotionClientTest {
    private static int ticks, samples;
    private static final StringBuilder REPORT = new StringBuilder();
    private static final ByteBuffer PIXELS = BufferUtils.createByteBuffer(128 * 128 * 4);
    private static byte[] previous;
    private static double nextSample;
    private static double totalDifference, maximumClockError;
    private static int movingSamples;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("windweave.waterMotionClientTest")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.isPaused()) return;
        ticks++;
        if (ticks == 1) {
            mc.options.hideGui = true;
            mc.options.bobView().set(false);
            mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FANCY);
            var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
            server.execute(() -> {
                var source = server.createCommandSourceStack();
                for (String command : new String[]{
                        "fill 35 95 -15 80 99 25 minecraft:stone",
                        "fill 35 100 -15 80 100 25 minecraft:water",
                        "fill 35 101 -15 80 114 25 minecraft:air",
                        "time set noon", "weather clear", "gamerule doDaylightCycle false",
                        "windweave set east 12", "gamemode spectator " + server.getPlayerList().getPlayer(uuid).getScoreboardName(),
                        "tp " + server.getPlayerList().getPlayer(uuid).getScoreboardName() + " 56.5 104 6.5 180 90"
                }) server.getCommands().performPrefixedCommand(source, command);
                server.getCommands().performPrefixedCommand(server.getPlayerList().getPlayer(uuid).createCommandSourceStack(), "windweave flow off");
            });
        }
        if (ticks >= 160) {
            double mean = totalDifference / Math.max(1, samples - 1);
            boolean pass = samples >= 30 && movingSamples >= samples - 2 && maximumClockError < .001 && mean > 1.05;
            REPORT.append("samples=").append(samples).append(" meanRgbMotion=").append(mean)
                    .append(" movingSamples=").append(movingSamples).append(" maxGpuClockError=").append(maximumClockError)
                    .append('\n').append(pass ? "PASS\n" : "FAIL: Water must visibly move in the real world framebuffer\n");
            try { Files.writeString(Path.of("water-motion-result.txt"), REPORT.toString()); }
            catch (Exception error) { throw new RuntimeException(error); }
            LogUtils.getLogger().info("WATER WORLD MOTION\n{}", REPORT);
            mc.stop();
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (!Boolean.getBoolean("windweave.waterMotionClientTest") || ticks < 60 || ticks > 150
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        double time = WindTerrainShaders.animationSeconds();
        if (time < nextSample) return;
        nextSample = time + .1;
        var mc = Minecraft.getInstance(); var shader = RenderSystem.getShader();
        int program = shader.getId();
        float gpuTime = GL20.glGetUniformf(program, GL20.glGetUniformLocation(program, "WindTime"));
        if (!shader.getName().equals("windweave:terrain_translucent")) throw new IllegalStateException("Real water pass must use wind shader");
        maximumClockError = Math.max(maximumClockError, Math.abs(gpuTime - time));
        GL11.glReadPixels(mc.getWindow().getWidth() / 2 - 64, mc.getWindow().getHeight() / 2 - 64,
                128, 128, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, PIXELS.clear());
        byte[] current = new byte[PIXELS.capacity()]; PIXELS.get(current);
        long difference = 0; int changed = 0;
        if (previous != null) for (int i = 0; i < current.length; i++) if (i % 4 != 3) {
            int delta = Math.abs((current[i] & 255) - (previous[i] & 255)); difference += delta; if (delta > 1) changed++;
        }
        totalDifference += difference / (128.0 * 128 * 3);
        if (changed > 3000) movingSamples++;
        REPORT.append("sample=").append(samples).append(" program=").append(shader.getName())
                .append(" cpuTime=").append(time).append(" gpuTime=").append(gpuTime)
                .append(" meanRgbChange=").append(difference / (128.0 * 128 * 3))
                .append(" changedRgb=").append(changed).append('\n');
        previous = current;
        if (samples == 0 || samples == 20) Screenshot.grab(mc.gameDirectory, "water-live-" + samples + ".png", mc.getMainRenderTarget(), message -> {});
        samples++;
    }
}
