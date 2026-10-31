package com.shouyun.windweave.wind;

import com.shouyun.windweave.Config;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/** One independent, persistent control state per dimension. */
public final class WindSavedData extends SavedData {
    private boolean automatic = true;
    private double bearing = 90;
    private double speed = 0;

    public static WindSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(WindSavedData::new, WindSavedData::load), "windweave_wind");
    }
    private static WindSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WindSavedData data = new WindSavedData();
        data.automatic = !tag.contains("automatic") || tag.getBoolean("automatic");
        data.bearing = tag.getDouble("bearing");
        data.speed = tag.getDouble("speed");
        if (!Double.isFinite(data.bearing)) data.bearing = 90;
        if (!Double.isFinite(data.speed)) data.speed = 0;
        data.speed = Math.clamp(data.speed, 0, 80);
        return data;
    }
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("automatic", automatic);
        tag.putDouble("bearing", bearing);
        tag.putDouble("speed", speed);
        return tag;
    }
    public void setFixed(double bearing, double speed) {
        this.automatic = false;
        this.bearing = (bearing % 360 + 360) % 360;
        this.speed = speed;
        setDirty();
    }
    public void setAutomatic() { automatic = true; setDirty(); }
    public boolean isAutomatic() { return automatic; }
    public Vec3 ambient(ServerLevel level) {
        if (!Config.ENABLED.get()) return Vec3.ZERO;
        if (!automatic) return WindMath.fromBearing(bearing, speed);
        // Fixed test wind may explicitly override the lack of surface atmosphere in other dimensions.
        if (!level.dimension().equals(Level.OVERWORLD)) return Vec3.ZERO;
        double t = level.getGameTime() / 20.0;
        double phase = (level.getSeed() & 65535L) * (Math.PI * 2 / 65536.0);
        return WeatherWind.sample(t, phase, Config.BASE_SPEED.get(), level.getRainLevel(1), level.getThunderLevel(1));
    }
}
