package com.shouyun.windweave.wind;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Loaded controllers only. The client receives nearby immutable source snapshots. */
public final class WindSources {
    public record Source(BlockPos pos, int bearing, int speed, int radius) {
        public Source { pos = pos.immutable(); }
    }
    private static final Map<ServerLevel, Map<BlockPos, Source>> LOADED = new WeakHashMap<>();
    private WindSources() {}
    public static void put(ServerLevel level, Source source, boolean enabled) {
        var map = LOADED.computeIfAbsent(level, key -> new HashMap<>());
        if (enabled) map.put(source.pos, source); else map.remove(source.pos);
    }
    public static void remove(ServerLevel level, BlockPos pos) { var map = LOADED.get(level); if (map != null) map.remove(pos); }
    public static List<Source> nearby(ServerLevel level, Vec3 position) {
        return LOADED.getOrDefault(level, Map.of()).values().stream()
                .filter(s -> Vec3.atCenterOf(s.pos).distanceToSqr(position) < 128 * 128)
                .sorted(Comparator.comparingDouble(s -> Vec3.atCenterOf(s.pos).distanceToSqr(position)))
                .limit(1024).toList();
    }
    public static Vec3 apply(Level level, Vec3 position, Vec3 ambient) {
        Collection<Source> sources = level instanceof ServerLevel server
                ? LOADED.getOrDefault(server, Map.of()).values() : ClientWindState.sources(level);
        Vec3 weighted = Vec3.ZERO;
        double total = 0, strongest = 0;
        for (Source source : sources) {
            double distance = position.distanceTo(Vec3.atCenterOf(source.pos));
            if (distance >= source.radius) continue;
            double edge = Math.clamp((source.radius - distance) / (source.radius * .4), 0, 1);
            double weight = edge * edge * (3 - 2 * edge);
            weighted = weighted.add(WindMath.fromBearing(source.bearing, source.speed).scale(weight));
            total += weight; strongest = Math.max(strongest, weight);
        }
        return total == 0 ? ambient : ambient.lerp(weighted.scale(1 / total), strongest);
    }
}
