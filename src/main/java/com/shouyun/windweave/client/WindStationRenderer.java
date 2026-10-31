package com.shouyun.windweave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.shouyun.windweave.Windweave;
import com.shouyun.windweave.device.WindDevices;
import com.shouyun.windweave.device.WindStationBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

public final class WindStationRenderer implements BlockEntityRenderer<WindStationBlockEntity> {
    public static final ModelResourceLocation ROTOR = model("anemometer_rotor");
    public static final ModelResourceLocation VANE = model("wind_vane_head");

    public WindStationRenderer(BlockEntityRendererProvider.Context context) {}

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Windweave.MODID, "block/" + name));
    }

    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(ROTOR);
        event.register(VANE);
    }

    @Override
    public void render(WindStationBlockEntity device, float partial, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        boolean anemometer = device.getBlockState().is(WindDevices.ANEMOMETER.get());
        float angle = anemometer
                ? (float) (Mth.lerp(partial, device.oldAngle, device.angle) % 360)
                : -Mth.rotLerp(partial, device.oldBearing, device.bearing);
        var mc = Minecraft.getInstance();
        // Fetch the current baked model so resource reloads cannot leave stale atlas UVs.
        var model = mc.getModelManager().getModel(anemometer ? ROTOR : VANE);
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.translate(-.5, 0, -.5);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.solid()),
                device.getBlockState(), model, 1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.solid());
        pose.popPose();
    }
}
