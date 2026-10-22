package com.solaris99.drillship.client;

import com.solaris99.drillship.menu.ShipStorageMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ShipStorageScreen extends AbstractContainerScreen<ShipStorageMenu> {
    private static final int TEXT = 0x404040;

    public ShipStorageScreen(ShipStorageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = ShipStorageMenu.INVENTORY_Y + 82;
        this.inventoryLabelY = ShipStorageMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("<"), b -> press(ShipStorageMenu.BUTTON_PREVIOUS))
                .bounds(leftPos + imageWidth - 50, topPos + 4, 12, 12).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> press(ShipStorageMenu.BUTTON_NEXT))
                .bounds(leftPos + imageWidth - 20, topPos + 4, 12, 12).build());
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        PanelDrawing.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        PanelDrawing.activeSlots(graphics, leftPos, topPos, menu);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        String page = (menu.page() + 1) + "/" + menu.pageCount();
        graphics.drawString(font, page, imageWidth - 29 - font.width(page) / 2, 6, TEXT, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
