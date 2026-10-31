package com.shouyun.windweave.mixin.client;

import com.shouyun.windweave.ClientConfig;
import com.shouyun.windweave.wind.WindField;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** FlameParticle owns move rather than tick. Includes ordinary and small flames. */
@Mixin(FlameParticle.class)
public abstract class FlameParticleMixin extends Particle {
    @Unique private Vec3 windweave$drift = Vec3.ZERO;
    @Unique private Vec3 windweave$target = Vec3.ZERO;
    protected FlameParticleMixin(ClientLevel level, double x, double y, double z) { super(level, x, y, z); }

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double windweave$x(double dx) {
        if (!ClientConfig.FIRE.get()) { windweave$drift = Vec3.ZERO; return dx; }
        if (age <= 1 || age % 4 == 0) windweave$target = WindField.sample(level, new Vec3(x, y, z)).scale(.018);
        windweave$drift = windweave$drift.lerp(windweave$target, .14);
        return dx + windweave$drift.x;
    }

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private double windweave$z(double dz) { return dz + windweave$drift.z; }
}
