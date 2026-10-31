package com.shouyun.windweave.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerFlightAccessor {
    @Accessor("aboveGroundTickCount")
    void windweave$setUnsupportedFlightTicks(int ticks);
}
