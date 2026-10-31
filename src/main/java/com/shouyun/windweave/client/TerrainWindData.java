package com.shouyun.windweave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import com.shouyun.windweave.wind.VisualWindExposure;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;

import java.util.concurrent.atomic.AtomicLongArray;
import java.lang.ref.WeakReference;

/** Chunk-build metadata in unused light-coordinate bits; low-byte vanilla lighting is preserved.
 * No time or wind direction is baked into the mesh. Animation runs entirely on the GPU. */
public final class TerrainWindData {
    public static final int GRASS = 1, LEAVES = 2, FIRE = 3, WATER = 4;
    private static final AtomicLongArray MARKED_BLOCKS = new AtomicLongArray(5);
    private static final ThreadLocal<RegionCache> CACHE = new ThreadLocal<>();
    private TerrainWindData() {}

    public static long markedBlocks(int kind) { return MARKED_BLOCKS.get(kind); }

    public static VertexConsumer block(BlockAndTintGetter region, BlockState state, BlockPos pos, VertexConsumer original) {
        if (!(region instanceof RenderChunkRegion) || !WindTerrainShaders.available()) return original;
        int kind = state.is(BlockTags.LEAVES) ? LEAVES
                : state.getBlock() instanceof BaseFireBlock || state.getBlock() instanceof CampfireBlock ? FIRE
                : state.getBlock() instanceof BushBlock && !state.is(Blocks.LILY_PAD) ? GRASS : 0;
        if (kind == 0) return original;
        if (!state.getFluidState().isEmpty()) return original;
        boolean campfire = state.getBlock() instanceof CampfireBlock;
        if (campfire && !state.getValue(CampfireBlock.LIT)) return original;
        boolean tall = kind == GRASS && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
        float upper = tall && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? 1 : 0;
        MARKED_BLOCKS.incrementAndGet(kind);
        return new WindVertices(original, region, cache(region, pos), kind, pos.getY() & 15,
                tall ? .5F : 1, upper, campfire);
    }

    public static VertexConsumer water(BlockAndTintGetter region, BlockPos pos, BlockState state, FluidState fluid, VertexConsumer original) {
        if (!(region instanceof RenderChunkRegion) || !WindTerrainShaders.available()
                || !fluid.is(FluidTags.WATER) || !state.is(Blocks.WATER)
                || region.getFluidState(pos.above()).is(FluidTags.WATER)) return original;
        MARKED_BLOCKS.incrementAndGet(WATER);
        return new WindVertices(original, region, cache(region, pos), WATER, pos.getY() & 15, 1, 0, false);
    }

    private static RegionCache cache(BlockAndTintGetter region, BlockPos pos) {
        RegionCache cache = CACHE.get();
        long section = SectionPos.asLong(pos);
        if (cache == null || cache.region.get() != region || cache.section != section) {
            cache = new RegionCache(region, section, pos.getX() & ~15, pos.getY() & ~15, pos.getZ() & ~15);
            CACHE.set(cache);
        }
        return cache;
    }

    private static final class RegionCache {
        private final WeakReference<BlockAndTintGetter> region;
        private final long section;
        private final int originX, originY, originZ;
        private final VisualWindExposure field = new VisualWindExposure();
        private final Long2IntOpenHashMap vertices = new Long2IntOpenHashMap();
        private RegionCache(BlockAndTintGetter region, long section, int x, int y, int z) {
            this.region = new WeakReference<>(region);
            this.section = section;
            originX = x; originY = y; originZ = z;
        }
        private int vertex(BlockAndTintGetter region, float x, float y, float z) {
            // Snap only the field sample, never the model. Both faces of a shared edge use identical values.
            int qx = Math.round(x * 64), qy = Math.round(y * 64), qz = Math.round(z * 64);
            long key = BlockPos.asLong(qx, qy, qz);
            if (vertices.containsKey(key)) return vertices.get(key);
            int value = field.sample(region, originX + qx / 64.0, originY + qy / 64.0 + .15, originZ + qz / 64.0);
            vertices.put(key, value);
            return value;
        }
    }

    private static final class WindVertices implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int kind;
        private final BlockAndTintGetter region;
        private final RegionCache cache;
        private final float baseY, scale, upper;
        private final boolean campfire;
        private float weight;
        private boolean mark = true;
        private int exposure;

        private WindVertices(VertexConsumer delegate, BlockAndTintGetter region, RegionCache cache, int kind, float baseY, float scale, float upper, boolean campfire) {
            this.delegate = delegate;
            this.region = region;
            this.cache = cache;
            this.kind = kind;
            this.baseY = baseY;
            this.scale = scale;
            this.upper = upper;
            this.campfire = campfire;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            weight = kind == LEAVES ? 1 : kind == WATER ? (y - baseY > .05F ? 1 : 0)
                    : campfire ? Mth.clamp((y - baseY - .4375F) / .5625F, 0, 1)
                    : Mth.clamp((y - baseY + upper) * scale, 0, 1);
            exposure = mark && weight > 0 ? cache.vertex(region, x, y, z) : 0;
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            // Both attributes stay positive signed shorts. Bit 14 identifies our metadata.
            if (mark) delegate.setUv2((u & 255) | 0x4000 | (Math.round(weight * 31) << 8),
                    (v & 255) | (kind << 12));
            else delegate.setUv2(u, v);
            return this;
        }

        @Override
        public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness,
                                float red, float green, float blue, float alpha, int[] light, int overlay, boolean readColor) {
            // Campfire logs stay rigid; only the flame sprite gets a bending weight.
            mark = !campfire || quad.getSprite().contents().name().getPath().endsWith("campfire_fire");
            if (mark) VertexConsumer.super.putBulkData(pose, quad, brightness, red, green, blue, alpha, light, overlay, readColor);
            else delegate.putBulkData(pose, quad, brightness, red, green, blue, alpha, light, overlay, readColor);
            mark = true;
        }

        @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r, g, b, a); return this; }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) {
            // Vanilla terrain lighting is already baked; its unused normal attribute carries 4x5-bit exposure.
            if (mark) delegate.setNormal(channel(exposure & 127), channel((exposure >> 7) & 127), channel((exposure >> 14) & 127));
            else delegate.setNormal(x, y, z);
            return this;
        }
        private static float channel(int bits) { return Math.min(1F, (bits + .25F) / 127F); }
    }
}
