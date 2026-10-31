package com.shouyun.windweave.device;

import com.shouyun.windweave.wind.WindMath;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class WindReadout {
    private WindReadout() {}
    public static Component message(Vec3 wind) {
        if (wind.length() < .05) return Component.translatable("windweave.readout.calm");
        return Component.translatable("windweave.readout", String.format(java.util.Locale.ROOT, "%.1f", wind.length()),
                Math.round(WindMath.bearing(wind)), Component.translatable(grade(wind.length())));
    }
    public static String grade(double speed) {
        return "windweave.grade." + (speed >= 44 ? "extreme" : speed >= 24 ? "storm" : speed >= 12 ? "strong" : "breeze");
    }
}
