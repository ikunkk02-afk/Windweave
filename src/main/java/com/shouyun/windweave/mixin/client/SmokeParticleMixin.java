package com.shouyun.windweave.mixin.client;

import com.shouyun.windweave.Config;
import com.shouyun.windweave.wind.WindField;
import com.shouyun.windweave.wind.WindMath;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SmokeParticle;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Normal and large black smoke use Particle.tick, unlike CampfireSmokeParticle's custom tick. */
@Mixin(Particle.class)
public abstract class SmokeParticleMixin {
    @Shadow @Final protected ClientLevel level;
    @Shadow protected int age;
    @Unique private Vec3 windweave$smokeDrift = Vec3.ZERO;
    @Unique private Vec3 windweave$smokeTarget = Vec3.ZERO;

    @ModifyArgs(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;move(DDD)V"))
    private void windweave$blackSmoke(Args args) {
        Particle particle = (Particle) (Object) this;
        if (!(particle instanceof SmokeParticle)) return;
        if (age <= 1 || age % 3 == 0) {
            windweave$smokeTarget = WindField.sample(level, particle.getPos()).scale(1.0 / 20);
        }
        windweave$smokeDrift = WindMath.smokeStep(windweave$smokeDrift, windweave$smokeTarget, Config.SMOKE_RESPONSE.get());
        args.set(0, (double) args.get(0) + windweave$smokeDrift.x);
        args.set(2, (double) args.get(2) + windweave$smokeDrift.z);
    }

}
