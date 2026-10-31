package com.shouyun.windweave.mixin.client;

import com.shouyun.windweave.client.IrisWindBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
public abstract class IrisCommonUniformsMixin {
    @Inject(method = "addNonDynamicUniforms", at = @At("RETURN"), require = 1, remap = false)
    private static void windweave$uniforms(@Coerce Object holder, @Coerce Object ids,
            @Coerce Object directives, @Coerce Object notifier, CallbackInfo ci) {
        IrisWindBridge.register(holder);
    }
}
