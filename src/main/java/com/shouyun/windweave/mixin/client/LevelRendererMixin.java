package com.shouyun.windweave.mixin.client;

import com.shouyun.windweave.client.WindTerrainShaders;
import com.shouyun.windweave.client.WindWeather;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow public abstract void setSectionDirty(int sectionX, int sectionY, int sectionZ);

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void windweave$frame(CallbackInfo ci) {
        WindTerrainShaders.beginFrame();
    }

    // Keep vanilla weather sounds/splashes and dimension hooks. Only replace the enabled sheet kind.
    @Redirect(method = "renderSnowAndRain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation windweave$replaceWeatherSheets(Biome biome, BlockPos pos) {
        var kind = biome.getPrecipitationAt(pos);
        return WindWeather.enabled(kind) ? Biome.Precipitation.NONE : kind;
    }

    @Inject(method = "blockChanged", at = @At("RETURN"))
    private void windweave$refreshShelter(BlockGetter level, BlockPos pos, BlockState before, BlockState after, int flags, CallbackInfo ci) {
        if (!WindTerrainShaders.available()) return;
        var a = before.is(BlockTags.LEAVES) ? net.minecraft.world.phys.shapes.Shapes.empty() : before.getCollisionShape(level, pos);
        var b = after.is(BlockTags.LEAVES) ? net.minecraft.world.phys.shapes.Shapes.empty() : after.getCollisionShape(level, pos);
        if (a == b) return;
        // Visit each affected section once, instead of calling the vanilla block-range loop thousands of times.
        for (int x = (pos.getX() - 12) >> 4; x <= (pos.getX() + 12) >> 4; x++) {
            for (int y = (pos.getY() - 3) >> 4; y <= (pos.getY() + 3) >> 4; y++) {
                for (int z = (pos.getZ() - 12) >> 4; z <= (pos.getZ() + 12) >> 4; z++) setSectionDirty(x, y, z);
            }
        }
    }
}
