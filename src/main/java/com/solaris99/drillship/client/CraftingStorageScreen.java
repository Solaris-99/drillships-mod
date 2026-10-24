package com.solaris99.drillship.client;

import com.solaris99.drillship.menu.CraftingStorageMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The crafting & inventory access module: a crafting grid on the left, a page of the ship's inventory on the right. */
public class CraftingStorageScreen extends AbstractContainerScreen<CraftingStorageMenu> {
    private static final int TEXT = 0x404040;
    private static final int ARROW = 0xFF8B8B8B;

    public CraftingStorageScreen(CraftingStorageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = CraftingStorageMenu.WIDTH;
        this.imageHeight = CraftingStorageMenu.INVENTORY_Y + 82;
        this.inventoryLabelX = CraftingStorageMenu.INVENTORY_X;
        this.inventoryLabelY = CraftingStorageMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("<"), b -> press(CraftingStorageMenu.BUTTON_PREVIOUS))
                .bounds(leftPos + imageWidth - 50, topPos + 4, 12, 12).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> press(CraftingStorageMenu.BUTTON_NEXT))
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
        // An arrow from the grid to the result.
        int x = leftPos + CraftingStorageMenu.GRID_X + 3 * 18 + 4, y = topPos + CraftingStorageMenu.RESULT_Y + 6;
        graphics.fill(x, y, x + 18, y + 4, ARROW);
        for (int i = 0; i < 5; i++) {
            graphics.fill(x + 18 + i, y - 3 + i, x + 19 + i, y + 7 - i, ARROW);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        graphics.drawString(font, Component.translatable("container.crafting"), CraftingStorageMenu.GRID_X, CraftingStorageMenu.GRID_Y - 10, TEXT, false);
        String page = (menu.page() + 1) + "/" + menu.pageCount();
        graphics.drawString(font, page, imageWidth - 29 - font.width(page) / 2, 6, TEXT, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
