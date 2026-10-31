package com.shouyun.windweave.mixin.client;

import com.shouyun.windweave.client.WindTerrainShaders;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getRendertypeSolidShader", at = @At("RETURN"), cancellable = true)
    private static void windweave$solid(CallbackInfoReturnable<ShaderInstance> ci) {
        ci.setReturnValue(WindTerrainShaders.select("solid", ci.getReturnValue()));
    }
    @Inject(method = "getRendertypeCutoutShader", at = @At("RETURN"), cancellable = true)
    private static void windweave$cutout(CallbackInfoReturnable<ShaderInstance> ci) {
        ci.setReturnValue(WindTerrainShaders.select("cutout", ci.getReturnValue()));
    }
    @Inject(method = "getRendertypeCutoutMippedShader", at = @At("RETURN"), cancellable = true)
    private static void windweave$cutoutMipped(CallbackInfoReturnable<ShaderInstance> ci) {
        ci.setReturnValue(WindTerrainShaders.select("cutout_mipped", ci.getReturnValue()));
    }
    @Inject(method = "getRendertypeTranslucentShader", at = @At("RETURN"), cancellable = true)
    private static void windweave$translucent(CallbackInfoReturnable<ShaderInstance> ci) {
        ci.setReturnValue(WindTerrainShaders.select("translucent", ci.getReturnValue()));
    }
}
