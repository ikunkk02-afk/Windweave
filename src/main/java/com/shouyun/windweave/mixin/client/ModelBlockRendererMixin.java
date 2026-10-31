package com.shouyun.windweave.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.shouyun.windweave.client.TerrainWindData;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    @ModifyVariable(method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At("HEAD"), argsOnly = true)
    private VertexConsumer windweave$vertices(VertexConsumer consumer, BlockAndTintGetter level, BakedModel model,
                                             BlockState state, BlockPos pos, PoseStack poses, VertexConsumer original,
                                             boolean checkSides, RandomSource random, long seed, int overlay,
                                             ModelData data, RenderType renderType) {
        return TerrainWindData.block(level, state, pos, consumer);
    }
}
