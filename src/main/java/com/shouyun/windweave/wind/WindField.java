package com.shouyun.windweave.wind;

import com.shouyun.windweave.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class WindField {
    private WindField() {}
    /** Metres/second. Smoke, projectiles and indicators share this local query. */
    public static Vec3 sample(Level level, Vec3 position) {
        if (!Config.ENABLED.get()) return Vec3.ZERO;
        Vec3 ambient = level instanceof ServerLevel server
                ? WindSavedData.get(server).ambient(server) : ClientWindState.ambient(level);
        ambient = WindSources.apply(level, position, ambient);
        if (Config.TERRAIN_WIND.get()) ambient = TerrainWind.guide(level, position, ambient);
        if (ambient.lengthSqr() < 1.0E-10 || !Config.SHELTER.get()) return ambient;
        Vec3 upwind = position.subtract(ambient.normalize().scale(8));
        // Include intermediate chunks at diagonal boundaries, not just both endpoints.
        BlockPos start = BlockPos.containing(position), end = BlockPos.containing(upwind);
        for (int cx = Math.min(start.getX() >> 4, end.getX() >> 4); cx <= Math.max(start.getX() >> 4, end.getX() >> 4); cx++) {
            for (int cz = Math.min(start.getZ() >> 4, end.getZ() >> 4); cz <= Math.max(start.getZ() >> 4, end.getZ() >> 4); cz++) {
                if (!level.getChunkSource().hasChunk(cx, cz)) return Vec3.ZERO;
            }
        }
        HitResult hit = level.clip(new ClipContext(position, upwind,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) return ambient;
        // The first two metres remain fully sheltered. Farther downwind, mixing
        // recovers smoothly instead of snapping from zero to full speed at 8 m.
        double recovery = net.minecraft.util.Mth.clamp((position.distanceTo(hit.getLocation()) - 2) / 6, 0, 1);
        return recovery == 0 ? Vec3.ZERO : ambient.scale(recovery * recovery * (3 - 2 * recovery));
    }
}
