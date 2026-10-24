package com.solaris99.drillship.client;

import com.solaris99.drillship.menu.AlloyFurnaceMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The alloy furnace: two inputs, the fuel under them with its flame, and a progress arrow to the output. */
public class AlloyFurnaceScreen extends AbstractContainerScreen<AlloyFurnaceMenu> {
    private static final int EMPTY = 0xFF8B8B8B;
    private static final int FLAME = 0xFFFF9A1F;
    private static final int ARROW = 0xFFFFFFFF;
    private static final int FLAME_SIZE = 13;
    private static final int ARROW_X = 80;
    private static final int ARROW_WIDTH = 24;

    public AlloyFurnaceScreen(AlloyFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = AlloyFurnaceMenu.INVENTORY_Y + 82;
        this.inventoryLabelY = AlloyFurnaceMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        PanelDrawing.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        PanelDrawing.activeSlots(graphics, leftPos, topPos, menu);
        // The flame between the inputs and the fuel, burning down.
        int duration = menu.get(AlloyFurnaceMenu.BURN_DURATION);
        int flame = duration > 0 ? FLAME_SIZE * menu.get(AlloyFurnaceMenu.BURN_TIME) / duration : 0;
        int fx = leftPos + AlloyFurnaceMenu.FUEL_X + 2, fy = topPos + AlloyFurnaceMenu.FUEL_Y - FLAME_SIZE - 3;
        graphics.fill(fx, fy, fx + FLAME_SIZE, fy + FLAME_SIZE, EMPTY);
        graphics.fill(fx, fy + FLAME_SIZE - flame, fx + FLAME_SIZE, fy + FLAME_SIZE, FLAME);
        int total = menu.get(AlloyFurnaceMenu.TOTAL);
        int filled = total > 0 ? ARROW_WIDTH * menu.get(AlloyFurnaceMenu.PROGRESS) / total : 0;
        int y = topPos + AlloyFurnaceMenu.OUTPUT_Y + 6;
        graphics.fill(leftPos + ARROW_X, y, leftPos + ARROW_X + ARROW_WIDTH, y + 4, EMPTY);
        graphics.fill(leftPos + ARROW_X, y, leftPos + ARROW_X + filled, y + 4, ARROW);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
