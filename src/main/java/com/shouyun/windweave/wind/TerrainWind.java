package com.shouyun.windweave.wind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.*;

/** Cached coarse terrain probes, smoothly interpolated. This is a channel approximation, not CFD. */
public final class TerrainWind {
    private record Profile(double alongX, double alongZ) {}
    private record Entry(long tick, Profile profile) {}
    private static final Map<Level, LinkedHashMap<BlockPos, Entry>> CACHE = new WeakHashMap<>();
    private TerrainWind() {}
    public static void clear(Level level) { CACHE.remove(level); }

    public static Vec3 guide(Level level, Vec3 pos, Vec3 wind) {
        if (wind.lengthSqr() < 1E-10) return wind;
        BlockPos block = BlockPos.containing(pos);
        if (!level.getChunkSource().hasChunk(block.getX() >> 4, block.getZ() >> 4)) return wind;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, block.getX(), block.getZ());
        if (pos.y > ground + 14) return wind;
        double gx = (pos.x - .5) / 4, gy = (pos.y - .5) / 4, gz = (pos.z - .5) / 4;
        int x = (int) Math.floor(gx), y = (int) Math.floor(gy), z = (int) Math.floor(gz);
        double cx = 0, cz = 0;
        for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) for (int c = 0; c < 2; c++) {
            double weight = (a == 0 ? 1 - (gx - x) : gx - x) * (b == 0 ? 1 - (gy - y) : gy - y) * (c == 0 ? 1 - (gz - z) : gz - z);
            Profile p = profile(level, new BlockPos(x + a, y + b, z + c));
            cx += p.alongX * weight; cz += p.alongZ * weight;
        }
        double strength = Math.max(cx, cz);
        if (strength < .01 || wind.horizontalDistanceSqr() < 1E-8) return wind;
        Vec3 axis = cx > cz ? new Vec3(Math.copySign(1, wind.x), 0, 0) : new Vec3(0, 0, Math.copySign(1, wind.z));
        double alignment = Math.abs(wind.normalize().dot(axis));
        if (alignment < .15) return wind;
        Vec3 direction = wind.normalize().lerp(axis, strength * .65).normalize();
        return direction.scale(Math.min(100, wind.length() * (1 + strength * alignment * 1.2)));
    }

    private static Profile profile(Level level, BlockPos cell) {
        var cache = CACHE.computeIfAbsent(level, key -> new LinkedHashMap<>(256, .75F, true));
        Entry old = cache.get(cell);
        if (old != null && level.getGameTime() - old.tick < 20) return old.profile;
        Vec3 center = new Vec3(cell.getX() * 4 + .5, cell.getY() * 4 + .5, cell.getZ() * 4 + .5);
        double[] lengths = new double[4];
        Vec3[] directions = {new Vec3(1, 0, 0), new Vec3(0, 0, 1), new Vec3(-1, 0, 0), new Vec3(0, 0, -1)};
        for (int i = 0; i < 4; i++) {
            // A cliff must persist three metres above the sample; single blocks do not form canyons.
            lengths[i] = Math.max(distance(level, center, directions[i]), distance(level, center.add(0, 3, 0), directions[i]));
        }
        double x = channel(lengths[1], lengths[3], lengths[0], lengths[2]);
        double z = channel(lengths[0], lengths[2], lengths[1], lengths[3]);
        Profile value = new Profile(x, z);
        cache.put(cell.immutable(), new Entry(level.getGameTime(), value));
        if (cache.size() > 4096) cache.remove(cache.keySet().iterator().next());
        return value;
    }
    private static double channel(double left, double right, double ahead, double behind) {
        if (left >= 16 || right >= 16) return 0;
        double open = Math.min(ahead, behind) / 16;
        return Math.clamp((1 - (left + right) / 32) * open, 0, 1);
    }
    private static double distance(Level level, Vec3 from, Vec3 direction) {
        Vec3 to = from.add(direction.scale(16));
        BlockPos a = BlockPos.containing(from), b = BlockPos.containing(to);
        if (!level.getChunkSource().hasChunk(a.getX() >> 4, a.getZ() >> 4)
                || !level.getChunkSource().hasChunk(b.getX() >> 4, b.getZ() >> 4)) return 16;
        var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? 16 : from.distanceTo(hit.getLocation());
    }
}
