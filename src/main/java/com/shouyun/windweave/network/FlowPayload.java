package com.shouyun.windweave.network;

import com.shouyun.windweave.Windweave;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A per-player visualization switch. It never changes server physics. */
public record FlowPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<FlowPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Windweave.MODID, "flow"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlowPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public FlowPayload decode(RegistryFriendlyByteBuf buffer) { return new FlowPayload(buffer.readBoolean()); }
        @Override public void encode(RegistryFriendlyByteBuf buffer, FlowPayload value) { buffer.writeBoolean(value.enabled); }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
