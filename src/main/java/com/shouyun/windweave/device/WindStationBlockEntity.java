package com.shouyun.windweave.device;

import com.shouyun.windweave.wind.WindField;
import com.shouyun.windweave.wind.WindMath;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class WindStationBlockEntity extends BlockEntity {
    public double angle, oldAngle;
    public float bearing, oldBearing;
    public WindStationBlockEntity(BlockPos pos, BlockState state) { super(WindDevices.STATION.get(), pos, state); }
    public static void tick(Level level, BlockPos pos, BlockState state, WindStationBlockEntity device) {
        Vec3 wind = WindField.sample(level, Vec3.atCenterOf(pos).add(0, .7, 0));
        device.oldAngle = device.angle;
        device.angle += Math.min(280, wind.length() * 4);
        device.oldBearing = device.bearing;
        if (wind.length() > .05) device.bearing = Mth.rotLerp(.2F, device.bearing, (float) WindMath.bearing(wind));
    }
}
