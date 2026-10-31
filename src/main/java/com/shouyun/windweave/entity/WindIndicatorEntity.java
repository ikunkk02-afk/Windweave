package com.shouyun.windweave.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Retain the old type so saved 0.1.0 worlds can load and retire their fixed markers. */
public class WindIndicatorEntity extends Entity {
    public WindIndicatorEntity(EntityType<? extends WindIndicatorEntity> type, Level level) { super(type, level); }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide) discard();
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
