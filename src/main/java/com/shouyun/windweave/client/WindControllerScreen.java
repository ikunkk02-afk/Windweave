package com.shouyun.windweave.client;

import com.shouyun.windweave.device.WindControllerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class WindControllerScreen extends AbstractContainerScreen<WindControllerMenu> {
    private Button enable;
    public WindControllerScreen(WindControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 248; imageHeight = 226;
    }
    @Override protected void init() {
        super.init();
        String[] directions = {"north", "east", "south", "west"};
        for (int i = 0; i < 4; i++) button(Component.translatable("windweave.direction." + directions[i]), i, 12 + i * 57, 49, 53);
        button(Component.literal("-15°"), 4, 12, 75, 106); button(Component.literal("+15°"), 5, 130, 75, 106);
        button(Component.literal("-5"), 8, 12, 121, 53); button(Component.literal("-1"), 6, 69, 121, 49);
        button(Component.literal("+1"), 7, 130, 121, 49); button(Component.literal("+5"), 9, 183, 121, 53);
        button(Component.literal("-8"), 10, 12, 166, 106); button(Component.literal("+8"), 11, 130, 166, 106);
        enable = button(Component.empty(), 12, 12, 197, 224); updateLabel();
    }
    private Button button(Component name, int id, int x, int y, int width) {
        return addRenderableWidget(Button.builder(name, button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id))
                .bounds(leftPos + x, topPos + y, width, 20).build());
    }
    private void updateLabel() { enable.setMessage(Component.translatable(menu.value(3) == 1 ? "windweave.controller.disable" : "windweave.controller.enable")); }
    @Override protected void containerTick() { super.containerTick(); updateLabel(); }
    @Override protected void renderBg(GuiGraphics gui, float partial, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        gui.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, 0xFF1A2732);
        gui.fill(x, y, x + imageWidth, y + imageHeight, 0xFFDBE5E9);
        gui.drawString(font, Component.translatable("windweave.controller.direction", menu.value(0)), x + 12, y + 33, 0x253846, false);
        gui.drawString(font, Component.translatable("windweave.controller.speed", menu.value(1)), x + 12, y + 105, 0x253846, false);
        gui.drawString(font, Component.translatable("windweave.controller.radius", menu.value(2)), x + 12, y + 151, 0x253846, false);
    }
    @Override protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) { gui.drawString(font, title, 12, 12, 0x203C4C, false); }
    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        renderBackground(gui, mouseX, mouseY, partial); super.render(gui, mouseX, mouseY, partial); renderTooltip(gui, mouseX, mouseY);
    }
}
