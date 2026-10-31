package com.shouyun.windweave.device;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class WindControllerMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final WindControllerBlockEntity controller;
    public final BlockPos pos;
    public WindControllerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(WindDevices.MENU.get(), id); pos = buffer.readBlockPos(); controller = null;
        data = new SimpleContainerData(4); addDataSlots(data);
    }
    public WindControllerMenu(int id, Inventory inventory, WindControllerBlockEntity controller) {
        super(WindDevices.MENU.get(), id); this.controller = controller; pos = controller.getBlockPos(); data = controller.data; addDataSlots(data);
    }
    public int value(int index) { return data.get(index); }
    @Override public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64
                && player.level().getBlockState(pos).is(WindDevices.CONTROLLER.get());
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (controller == null || !stillValid(player) || id < 0 || id > 12) return false;
        int direction = controller.bearing, speed = controller.speed, radius = controller.radius;
        boolean enabled = controller.enabled;
        switch (id) {
            case 0, 1, 2, 3 -> direction = id * 90;
            case 4 -> direction -= 15;
            case 5 -> direction += 15;
            case 6 -> speed--;
            case 7 -> speed++;
            case 8 -> speed -= 5;
            case 9 -> speed += 5;
            case 10 -> radius -= 8;
            case 11 -> radius += 8;
            case 12 -> enabled = !enabled;
        }
        controller.configure(direction, speed, radius, enabled); broadcastChanges(); return true;
    }
}
