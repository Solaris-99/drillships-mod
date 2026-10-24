package com.solaris99.drillship.client;

import com.solaris99.drillship.menu.FurnaceModuleMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A furnace module: inputs, a progress arrow and output; the push and pull toggles; and the whitelist of what it pulls. */
public class FurnaceModuleScreen extends AbstractContainerScreen<FurnaceModuleMenu> {
    private static final int TEXT = 0x404040;
    private static final int WARNING = 0xB02020;
    private static final int ARROW = 0xFF555555;
    private static final int ARROW_FILLED = 0xFFFFFFFF;
    private static final int ARROW_X = FurnaceModuleMenu.INPUT_X + 22;
    private static final int ARROW_WIDTH = FurnaceModuleMenu.OUTPUT_X - 4 - ARROW_X;

    private Button push;
    private Button pull;

    public FurnaceModuleScreen(FurnaceModuleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = FurnaceModuleMenu.INVENTORY_Y + 82;
        this.inventoryLabelY = FurnaceModuleMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        push = addRenderableWidget(Button.builder(Component.empty(), b -> press(FurnaceModuleMenu.BUTTON_PUSH))
                .bounds(leftPos + 122, topPos + 14, 48, 14).build());
        pull = addRenderableWidget(Button.builder(Component.empty(), b -> press(FurnaceModuleMenu.BUTTON_PULL))
                .bounds(leftPos + 122, topPos + 30, 48, 14).build());
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        push.setMessage(Component.translatable(menu.get(FurnaceModuleMenu.PUSH) == 1 ? "gui.drillships.push_on" : "gui.drillships.push_off"));
        pull.setMessage(Component.translatable(menu.get(FurnaceModuleMenu.PULL) == 1 ? "gui.drillships.pull_on" : "gui.drillships.pull_off"));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        PanelDrawing.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        PanelDrawing.activeSlots(graphics, leftPos, topPos, menu);
        int total = menu.get(FurnaceModuleMenu.TOTAL);
        int filled = total > 0 ? ARROW_WIDTH * menu.get(FurnaceModuleMenu.PROGRESS) / total : 0;
        int y = topPos + FurnaceModuleMenu.SLOTS_Y + 6;
        graphics.fill(leftPos + ARROW_X, y, leftPos + ARROW_X + ARROW_WIDTH, y + 4, ARROW);
        graphics.fill(leftPos + ARROW_X, y, leftPos + ARROW_X + filled, y + 4, ARROW_FILLED);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        if (menu.get(FurnaceModuleMenu.STARVED) == 1) {
            graphics.drawString(font, Component.translatable("gui.drillships.no_power"), FurnaceModuleMenu.INPUT_X, FurnaceModuleMenu.SLOTS_Y + 22, WARNING, false);
        }
        graphics.drawString(font, Component.translatable("gui.drillships.pull_list"), 8, FurnaceModuleMenu.FILTER_Y - 10, TEXT, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
