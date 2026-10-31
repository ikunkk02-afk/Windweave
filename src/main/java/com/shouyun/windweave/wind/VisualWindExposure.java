package com.shouyun.windweave.wind;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/** Continuous visual shelter field, sampled from a bounded render-region snapshot.
 * Grid values are shared across blocks/sections. Never queries a live client level or loads chunks. */
public final class VisualWindExposure {
    private static final Direction[] DIRECTIONS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private final Long2IntOpenHashMap nodes = new Long2IntOpenHashMap();

    /** Four five-bit directional exposure values, N/E/S/W. */
    public int sample(BlockGetter level, double x, double y, double z) {
        int gx = Mth.floor((x - .5) / 2), gy = Mth.floor((y - .5) / 2), gz = Mth.floor((z - .5) / 2);
        double fx = smooth((x - .5) / 2 - gx), fy = smooth((y - .5) / 2 - gy), fz = smooth((z - .5) / 2 - gz);
        double[] exposure = new double[4];
        for (int dx = 0; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = 0; dz <= 1; dz++) {
            int value = node(level, gx + dx, gy + dy, gz + dz);
            double weight = (dx == 0 ? 1 - fx : fx) * (dy == 0 ? 1 - fy : fy) * (dz == 0 ? 1 - fz : fz);
            for (int direction = 0; direction < 4; direction++) exposure[direction] += ((value >> (direction * 5)) & 31) * weight;
        }
        int packed = 0;
        for (int direction = 0; direction < 4; direction++) packed |= Mth.clamp((int) Math.round(exposure[direction]), 0, 31) << (direction * 5);
        return packed;
    }

    private int node(BlockGetter level, int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        if (nodes.containsKey(key)) return nodes.get(key);
        Vec3 center = new Vec3(x * 2 + .5, y * 2 + .5, z * 2 + .5);
        int value = 0;
        for (int i = 0; i < 4; i++) {
            Direction direction = DIRECTIONS[i];
            double exposure = 0;
            // A small beam bundle softens the edge of a wall's wind shadow.
            for (int beam = -1; beam <= 1; beam++) {
                Vec3 start = center.add(direction.getStepZ() * beam * 1.25, 0, direction.getStepX() * beam * 1.25);
                exposure += ray(level, start, direction) / 3;
            }
            value |= (int) Math.round(exposure * 31) << (i * 5);
        }
        nodes.put(key, value);
        return value;
    }

    private static double ray(BlockGetter level, Vec3 start, Direction direction) {
        Vec3 end = start.add(direction.getStepX() * 8, 0, direction.getStepZ() * 8);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int step = 0; step <= 8; step++) {
            cursor.set(start.x + direction.getStepX() * step, start.y, start.z + direction.getStepZ() * step);
            var state = level.getBlockState(cursor);
            if (state.is(BlockTags.LEAVES)) continue;
            var hit = state.getCollisionShape(level, cursor).clip(start, end, cursor);
            if (hit != null) return smooth(Mth.clamp(start.distanceTo(hit.getLocation()) / 8, 0, 1));
        }
        return 1;
    }

    private static double smooth(double x) { return x * x * (3 - 2 * x); }
}
