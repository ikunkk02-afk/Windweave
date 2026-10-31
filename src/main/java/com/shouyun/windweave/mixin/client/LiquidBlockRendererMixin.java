package com.shouyun.windweave.mixin.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.shouyun.windweave.client.TerrainWindData;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LiquidBlockRenderer.class)
public abstract class LiquidBlockRendererMixin {
    @ModifyVariable(method = "tesselate", at = @At("HEAD"), argsOnly = true)
    private VertexConsumer windweave$vertices(VertexConsumer consumer, BlockAndTintGetter level, BlockPos pos,
                                             VertexConsumer original, BlockState state, FluidState fluid) {
        return TerrainWindData.water(level, pos, state, fluid, consumer);
    }
}
