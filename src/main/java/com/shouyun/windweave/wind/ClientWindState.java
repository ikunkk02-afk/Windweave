package com.shouyun.windweave.wind;

import com.shouyun.windweave.network.WindPayload;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Client network handler owns this cache; never reads an integrated server. */
public final class ClientWindState {
    private static WindPayload latest;
    private static boolean flowEnabled = true;
    private ClientWindState() {}
    public static void accept(WindPayload payload) { latest = payload; }
    public static void clear() { latest = null; flowEnabled = true; }
    public static void setFlowEnabled(boolean enabled) { flowEnabled = enabled; }
    public static boolean flowEnabled() { return flowEnabled; }
    public static java.util.List<WindSources.Source> sources(Level level) {
        return latest != null && latest.dimension().equals(level.dimension().location()) ? latest.sources() : java.util.List.of();
    }
    public static Vec3 ambient(Level level) {
        return latest != null && latest.dimension().equals(level.dimension().location())
                ? new Vec3(latest.x(), 0, latest.z()) : Vec3.ZERO;
    }
}
