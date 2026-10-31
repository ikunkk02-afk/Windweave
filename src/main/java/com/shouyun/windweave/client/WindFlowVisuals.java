package com.shouyun.windweave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.shouyun.windweave.ClientConfig;
import com.shouyun.windweave.wind.ClientWindState;
import com.shouyun.windweave.wind.WindField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/** Bounded client-only moving trails. Every head follows the same local wind as smoke. */
public final class WindFlowVisuals {
    private static final int MAX_STREAKS = 72;
    private static final List<Streak> STREAKS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static ClientLevel currentLevel;
    private static long renderedSegments;

    private WindFlowVisuals() {}
    public static void clear() { STREAKS.clear(); currentLevel = null; }
    public static int streakCount() { return STREAKS.size(); }
    public static long renderedSegments() { return renderedSegments; }
    public static Vec3 averageVelocity() {
        Vec3 sum = Vec3.ZERO;
        for (Streak streak : STREAKS) sum = sum.add(streak.velocity);
        return STREAKS.isEmpty() ? Vec3.ZERO : sum.scale(1.0 / STREAKS.size());
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != currentLevel) { clear(); currentLevel = mc.level; }
        if (mc.level == null || mc.player == null || !ClientConfig.WIND_INDICATOR.get() || !ClientWindState.flowEnabled()) { STREAKS.clear(); return; }
        if (mc.isPaused()) return;
        Vec3 center = mc.gameRenderer.getMainCamera().getPosition();
        STREAKS.removeIf(streak -> !streak.tick(mc.level) || streak.head().distanceToSqr(center) > 36 * 36);
        if (ClientWindState.ambient(mc.level).lengthSqr() < .01 && ClientWindState.sources(mc.level).isEmpty()) return;
        for (int attempt = 0; attempt < 5 && STREAKS.size() < MAX_STREAKS; attempt++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double radius = 3 + RANDOM.nextDouble() * 17;
            Vec3 origin = center.add(Math.cos(angle) * radius, RANDOM.nextDouble() * 10 - 4, Math.sin(angle) * radius);
            Vec3 wind = WindField.sample(mc.level, origin).scale(1.0 / 20);
            if (wind.lengthSqr() > .0001 && mc.level.getBlockState(net.minecraft.core.BlockPos.containing(origin)).getCollisionShape(mc.level,
                    net.minecraft.core.BlockPos.containing(origin)).isEmpty()) STREAKS.add(new Streak(origin, wind));
        }
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || STREAKS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != currentLevel || !ClientConfig.WIND_INDICATOR.get() || !ClientWindState.flowEnabled()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(RenderType.lines());
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        PoseStack.Pose pose = poses.last();
        for (Streak streak : STREAKS) {
            double fade = Math.min(1, (streak.age + partial) / 8.0) * Math.min(1, (streak.life - streak.age - partial) / 12.0);
            fade *= Math.min(1, streak.head().distanceTo(camera) / 3);
            double travelled = 0;
            for (int i = 0; i < streak.points.size() - 1; i++) {
                Vec3 a = streak.points.get(i);
                Vec3 b = streak.points.get(i + 1);
                // Shift the complete trail between ticks so motion stays smooth.
                Vec3 offset = streak.velocity.scale(partial);
                double length = a.distanceTo(b);
                double maxLength = 2.2 + Math.min(streak.velocity.length(), 1) * 2;
                if (travelled + length > maxLength) b = a.lerp(b, Math.max(0, maxLength - travelled) / Math.max(.00001, length));
                int alpha = (int) (220 * fade * Math.max(0, 1 - travelled / maxLength));
                if (alpha > 2 && length > 1E-6) {
                    line(vertices, pose, a.add(offset), b.add(offset), alpha);
                    renderedSegments++;
                }
                travelled += length;
                if (travelled >= maxLength) break;
            }
        }
        poses.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void line(VertexConsumer consumer, PoseStack.Pose pose, Vec3 a, Vec3 b, int alpha) {
        Vec3 normal = b.subtract(a).normalize();
        consumer.addVertex(pose.pose(), (float) a.x, (float) a.y, (float) a.z).setColor(190, 240, 250, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        consumer.addVertex(pose.pose(), (float) b.x, (float) b.y, (float) b.z).setColor(190, 240, 250, Math.max(0, alpha - 24))
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static final class Streak {
        private final List<Vec3> points = new ArrayList<>();
        private Vec3 velocity;
        private int age;
        private int life = 55 + RANDOM.nextInt(35);
        private final double phase = RANDOM.nextDouble() * Math.PI * 2;

        private Streak(Vec3 origin, Vec3 wind) { points.add(origin); velocity = wind; }
        private Vec3 head() { return points.getFirst(); }
        private boolean tick(ClientLevel level) {
            if (++age >= life) return false;
            Vec3 target = WindField.sample(level, head()).scale(1.0 / 20);
            if (target.lengthSqr() < .0001) life = Math.min(life, age + 10);
            velocity = velocity.lerp(target, .25);
            Vec3 next = head().add(velocity).add(0, Math.sin(age * .16 + phase) * Math.min(.025, velocity.length() * .04), 0);
            if (level.clip(new ClipContext(head(), next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() != HitResult.Type.MISS) return false;
            points.addFirst(next);
            if (points.size() > 14) points.removeLast();
            return true;
        }
    }
}
