package com.shouyun.windweave.mixin;

import com.shouyun.windweave.wind.WindFireBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CampfireBlockEntity.class)
public abstract class CampfireBlockEntityMixin {
    @Inject(method = "cookTick", at = @At("HEAD"), cancellable = true)
    private static void windweave$wind(Level level, BlockPos pos, BlockState state, CampfireBlockEntity entity, CallbackInfo ci) {
        if (level instanceof ServerLevel server && WindFireBehavior.tickCampfire(server, pos, state)) ci.cancel();
    }
}
