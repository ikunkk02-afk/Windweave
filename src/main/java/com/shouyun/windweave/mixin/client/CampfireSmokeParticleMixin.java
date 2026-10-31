package com.shouyun.windweave.mixin.client;

import com.shouyun.windweave.Config;
import com.shouyun.windweave.wind.WindField;
import com.shouyun.windweave.wind.WindMath;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.CampfireSmokeParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(CampfireSmokeParticle.class)
public abstract class CampfireSmokeParticleMixin extends Particle {
    @Unique private Vec3 windweave$drift = Vec3.ZERO;
    @Unique private Vec3 windweave$target = Vec3.ZERO;
    protected CampfireSmokeParticleMixin(ClientLevel level, double x, double y, double z) { super(level, x, y, z); }
    @ModifyArgs(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/CampfireSmokeParticle;move(DDD)V"))
    private void windweave$advect(Args args) {
        // Cache shelter queries for five ticks per smoke particle. Modify displacement
        // only: vanilla buoyancy, random drift, collision and fade stay intact.
        if (age == 1 || age % 5 == 0) {
            windweave$target = WindField.sample(level, new Vec3(x, y, z)).scale(1.0 / 20);
        }
        windweave$drift = WindMath.smokeStep(windweave$drift, windweave$target, Config.SMOKE_RESPONSE.get());
        args.set(0, (double) args.get(0) + windweave$drift.x);
        args.set(2, (double) args.get(2) + windweave$drift.z);
    }
}
