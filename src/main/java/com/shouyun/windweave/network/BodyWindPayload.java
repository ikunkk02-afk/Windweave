package com.shouyun.windweave.network;

import com.shouyun.windweave.Windweave;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Add only the authoritative wind impulse, preserving client walking/jumping velocity. */
public record BodyWindPayload(ResourceLocation dimension, Vec3 impulse) implements CustomPacketPayload {
    public static final Type<BodyWindPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Windweave.MODID, "body_wind"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyWindPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public BodyWindPayload decode(RegistryFriendlyByteBuf b) {
            return new BodyWindPayload(b.readResourceLocation(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()));
        }
        @Override public void encode(RegistryFriendlyByteBuf b, BodyWindPayload p) {
            b.writeResourceLocation(p.dimension); b.writeDouble(p.impulse.x); b.writeDouble(p.impulse.y); b.writeDouble(p.impulse.z);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
