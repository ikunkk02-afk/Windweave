package com.shouyun.windweave.network;

import com.shouyun.windweave.Windweave;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WindPayload(ResourceLocation dimension, double x, double z,
                          java.util.List<com.shouyun.windweave.wind.WindSources.Source> sources) implements CustomPacketPayload {
    public WindPayload(ResourceLocation dimension, double x, double z) { this(dimension, x, z, java.util.List.of()); }
    public WindPayload { sources = java.util.List.copyOf(sources); }
    public static final Type<WindPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Windweave.MODID, "wind"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WindPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public WindPayload decode(RegistryFriendlyByteBuf buffer) {
            var dimension = buffer.readResourceLocation();
            double x = buffer.readDouble(), z = buffer.readDouble();
            int count = buffer.readVarInt();
            if (count < 0 || count > 1024) throw new IllegalArgumentException("Invalid wind source count");
            var sources = new java.util.ArrayList<com.shouyun.windweave.wind.WindSources.Source>(count);
            for (int i = 0; i < count; i++) sources.add(new com.shouyun.windweave.wind.WindSources.Source(
                    buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
            return new WindPayload(dimension, x, z, sources);
        }
        @Override
        public void encode(RegistryFriendlyByteBuf buffer, WindPayload payload) {
            buffer.writeResourceLocation(payload.dimension);
            buffer.writeDouble(payload.x);
            buffer.writeDouble(payload.z);
            buffer.writeVarInt(payload.sources.size());
            for (var source : payload.sources) {
                buffer.writeBlockPos(source.pos()); buffer.writeVarInt(source.bearing());
                buffer.writeVarInt(source.speed()); buffer.writeVarInt(source.radius());
            }
        }
    };
    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
