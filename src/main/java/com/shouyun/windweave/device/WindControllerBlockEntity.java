package com.shouyun.windweave.device;

import com.shouyun.windweave.wind.WindSources;
import com.shouyun.windweave.wind.WindEvents;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class WindControllerBlockEntity extends BlockEntity implements MenuProvider {
    public int bearing = 90, speed = 12, radius = 24;
    public boolean enabled;
    public WindControllerBlockEntity(BlockPos pos, BlockState state) { super(WindDevices.FIELD.get(), pos, state); }
    public final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return switch (index) { case 0 -> bearing; case 1 -> speed; case 2 -> radius; default -> enabled ? 1 : 0; }; }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return 4; }
    };
    public void configure(int bearing, int speed, int radius, boolean enabled) {
        this.bearing = Math.floorMod(bearing, 360); this.speed = Math.clamp(speed, 0, 80);
        this.radius = Math.clamp(radius, 8, 64); this.enabled = enabled;
        refresh(); setChanged();
        if (level instanceof ServerLevel server) {
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            WindEvents.sync(server);
        }
    }
    private void refresh() { if (level instanceof ServerLevel server) WindSources.put(server, new WindSources.Source(worldPosition, bearing, speed, radius), enabled); }
    @Override public void onLoad() { super.onLoad(); refresh(); }
    @Override public void setRemoved() { if (level instanceof ServerLevel server) WindSources.remove(server, worldPosition); super.setRemoved(); }
    @Override public void onChunkUnloaded() { if (level instanceof ServerLevel server) WindSources.remove(server, worldPosition); super.onChunkUnloaded(); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.putInt("bearing", bearing); tag.putInt("speed", speed); tag.putInt("radius", radius); tag.putBoolean("enabled", enabled);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); bearing = Math.floorMod(tag.getInt("bearing"), 360);
        speed = Math.clamp(tag.getInt("speed"), 0, 80); radius = Math.clamp(tag.getInt("radius"), 8, 64); enabled = tag.getBoolean("enabled");
        refresh();
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public Component getDisplayName() { return Component.translatable("block.windweave.wind_controller"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new WindControllerMenu(id, inventory, this); }
}
