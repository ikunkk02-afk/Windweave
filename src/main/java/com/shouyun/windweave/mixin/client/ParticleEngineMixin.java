package com.shouyun.windweave.mixin.client;

import com.shouyun.windweave.client.WindWeather;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "clearParticles", at = @At("HEAD"))
    private void windweave$clearWeather(CallbackInfo ci) {
        WindWeather.clear(); com.shouyun.windweave.client.WindSand.clear();
        com.shouyun.windweave.client.WindAudio.clear();
    }
}
