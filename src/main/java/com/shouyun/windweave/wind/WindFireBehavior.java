package com.shouyun.windweave.wind;

import com.shouyun.windweave.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Runs only from vanilla fire/block-entity ticks: no world scans or chunk loads. */
public final class WindFireBehavior {
    private WindFireBehavior() {}

    private static boolean active(ServerLevel level) {
        return Config.ENABLED.get() && level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
    }

    public static boolean blowOutFire(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!active(level) || !Config.WIND_EXTINGUISH.get() || !level.getBlockState(pos).is(Blocks.FIRE)) return false;
        BlockPos below = pos.below();
        if (level.getBlockState(below).isFireSource(level, below, Direction.UP)) return false;
        double speed = WindField.sample(level, Vec3.atCenterOf(pos)).length();
        double chance = blowoutChance(speed, hasFuel(level, pos), false);
        if (chance <= 0 || random.nextFloat() >= chance) return false;
        level.removeBlock(pos, false);
        level.levelEvent(1009, pos, 0);
        level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
        return true;
    }

    public static double blowoutChance(double speed, boolean fuel, boolean campfire) {
        double threshold = Config.FIRE_EXTINGUISH_SPEED.get();
        if (speed < threshold) return 0;
        double excess = Mth.clamp((speed - threshold) / 16, 0, 1);
        return campfire ? .08 + .22 * excess : fuel ? .025 + .075 * excess : .55 + .4 * excess;
    }

    public static boolean hasFuel(ServerLevel level, BlockPos pos) {
        for (Direction face : Direction.values()) {
            BlockPos neighbor = pos.relative(face);
            if (level.hasChunkAt(neighbor) && level.getBlockState(neighbor).isFlammable(level, neighbor, face.getOpposite())) return true;
        }
        return false;
    }

    public static void fanFire(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockState(pos).getBlock() instanceof FireBlock)) return;
        spread(level, pos, Vec3.atCenterOf(pos), random);
    }

    private static void spread(ServerLevel level, BlockPos pos, Vec3 origin, RandomSource random) {
        if (!active(level) || !Config.WIND_FIRE_SPREAD.get()) return;
        Vec3 wind = WindField.sample(level, origin);
        double speed = wind.length();
        if (speed < 3) return;
        double chance = Mth.clamp((speed - 3) * .025 * Config.FIRE_SPREAD_RESPONSE.get(), 0, .85);
        if (random.nextFloat() >= chance) return;
        Vec3 direction = wind.normalize();
        // One successful ignition per vanilla tick. Targets must be air touching real fuel.
        int reach = speed >= 22 ? 3 : 2;
        for (int distance = reach; distance >= 1; distance--) {
            BlockPos target = BlockPos.containing(origin.add(direction.scale(distance)));
            if (target.equals(pos) || !loadedPath(level, pos, target)) continue;
            if (!level.isEmptyBlock(target) || !hasFuel(level, target) || level.isRainingAt(target)) continue;
            Vec3 end = Vec3.atCenterOf(target);
            if (level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY,
                    CollisionContext.empty())).getType() != HitResult.Type.MISS) continue;
            BlockState fire = BaseFireBlock.getState(level, target);
            if (!fire.is(Blocks.FIRE) || !fire.canSurvive(level, target)) continue;
            level.setBlock(target, fire.setValue(FireBlock.AGE, 0), 3);
            level.gameEvent(null, GameEvent.BLOCK_PLACE, target);
            return;
        }
    }

    private static boolean loadedPath(ServerLevel level, BlockPos a, BlockPos b) {
        for (int x = Math.min(a.getX() >> 4, b.getX() >> 4); x <= Math.max(a.getX() >> 4, b.getX() >> 4); x++) {
            for (int z = Math.min(a.getZ() >> 4, b.getZ() >> 4); z <= Math.max(a.getZ() >> 4, b.getZ() >> 4); z++) {
                if (!level.getChunkSource().hasChunk(x, z)) return false;
            }
        }
        return true;
    }

    /** Called every 40 ticks per campfire, staggered by position. Returns true after blowout. */
    public static boolean tickCampfire(ServerLevel level, BlockPos pos, BlockState state) {
        if (!active(level) || !state.getValue(CampfireBlock.LIT)
                || Math.floorMod(level.getGameTime() + pos.asLong(), 40) != 0) return false;
        // Probe above the logs, so the campfire does not shelter its own flame.
        Vec3 origin = Vec3.atBottomCenterOf(pos).add(0, .85, 0);
        double speed = WindField.sample(level, origin).length();
        double chance = Config.WIND_EXTINGUISH.get() ? blowoutChance(speed, true, true) : 0;
        if (chance > 0 && level.random.nextFloat() < chance) {
            CampfireBlock.dowse(null, level, pos, state);
            level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), 3);
            level.levelEvent(1009, pos, 0);
            level.gameEvent(null, GameEvent.BLOCK_CHANGE, pos);
            return true;
        }
        // Soul fire retains its vanilla non-spreading identity.
        if (state.is(Blocks.CAMPFIRE)) spread(level, pos, origin, level.random);
        return false;
    }
}
