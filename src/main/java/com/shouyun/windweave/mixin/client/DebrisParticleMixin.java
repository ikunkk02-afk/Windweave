package com.shouyun.windweave.mixin.client;

import com.shouyun.windweave.ClientConfig;
import com.shouyun.windweave.Config;
import com.shouyun.windweave.wind.WindField;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Only physical vanilla debris: never portal, UI, spell or underwater particles. */
@Mixin(Particle.class)
public abstract class DebrisParticleMixin {
    @Shadow @Final protected ClientLevel level;
    @Shadow protected int age;
    @Unique private Vec3 windweave$debrisDrift = Vec3.ZERO;
    @Unique private Vec3 windweave$debrisTarget = Vec3.ZERO;

    @ModifyArgs(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;move(DDD)V"))
    private void windweave$debris(Args args) {
        Particle particle = (Particle) (Object) this;
        boolean heavy = particle instanceof TerrainParticle;
        if (!heavy && !(particle instanceof DustParticleBase || particle instanceof AshParticle
                || particle instanceof WhiteAshParticle || particle instanceof SnowflakeParticle
                || particle instanceof DustPlumeParticle)) return;
        if (!ClientConfig.PARTICLE_PHYSICS.get() || !Config.ENABLED.get()
                || !level.getFluidState(BlockPos.containing(particle.getPos())).isEmpty()) {
            windweave$debrisDrift = windweave$debrisTarget = Vec3.ZERO; return;
        }
        if (age <= 1 || age % 3 == 0) windweave$debrisTarget = WindField.sample(level, particle.getPos()).scale(heavy ? .025 : .05);
        windweave$debrisDrift = windweave$debrisDrift.lerp(windweave$debrisTarget, heavy ? .055 : .12);
        args.set(0, (double) args.get(0) + windweave$debrisDrift.x);
        args.set(2, (double) args.get(2) + windweave$debrisDrift.z);
    }
}
