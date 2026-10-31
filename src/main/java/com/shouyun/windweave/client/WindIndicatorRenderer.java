package com.shouyun.windweave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shouyun.windweave.entity.WindIndicatorEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Invisible during the one tick before obsolete 0.1.0 markers are retired. */
public class WindIndicatorRenderer extends EntityRenderer<WindIndicatorEntity> {
    public WindIndicatorRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public void render(WindIndicatorEntity entity, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {}
    @Override public ResourceLocation getTextureLocation(WindIndicatorEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}
