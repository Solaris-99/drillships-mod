package com.solaris99.drillship.client;

import java.util.Locale;

import com.solaris99.drillship.block.entity.IoMode;
import com.solaris99.drillship.menu.InventoryIoMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The Inventory IO module: mode, rate limit in items per second, and the filter with its whitelist/blacklist toggle. */
public class InventoryIoScreen extends AbstractContainerScreen<InventoryIoMenu> {
    private static final int TEXT = 0x404040;
    private static final int MUTED = 0x707070;

    private Button mode;
    private Button filterMode;

    public InventoryIoScreen(InventoryIoMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = InventoryIoMenu.INVENTORY_Y + 82;
        this.inventoryLabelY = InventoryIoMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        mode = addRenderableWidget(Button.builder(Component.empty(), b -> press(InventoryIoMenu.BUTTON_MODE))
                .bounds(leftPos + 7, topPos + 16, 162, 16).build());
        for (int i = 0; i < InventoryIoMenu.STEPS.length; i++) {
            int step = InventoryIoMenu.STEPS[i];
            int button = i + 1;
            addRenderableWidget(Button.builder(Component.literal((step > 0 ? "+" : "") + step), b -> press(button))
                    .bounds(leftPos + 7 + i * 27, topPos + 46, 26, 14).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.drillships.no_limit"), b -> press(InventoryIoMenu.BUTTON_NO_LIMIT))
                .bounds(leftPos + 7, topPos + 62, 80, 14).build());
        filterMode = addRenderableWidget(Button.builder(Component.empty(), b -> press(InventoryIoMenu.BUTTON_FILTER_MODE))
                .bounds(leftPos + 109, topPos + 62, 60, 14).build());
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        IoMode io = IoMode.byId(menu.get(InventoryIoMenu.MODE));
        mode.setMessage(Component.translatable("gui.drillships.io_mode", Component.translatable("gui.drillships.io_mode." + io.name().toLowerCase(Locale.ROOT))));
        filterMode.setMessage(Component.translatable(menu.get(InventoryIoMenu.WHITELIST) == 1 ? "gui.drillships.whitelist" : "gui.drillships.blacklist"));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        PanelDrawing.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        PanelDrawing.activeSlots(graphics, leftPos, topPos, menu);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        int limit = menu.get(InventoryIoMenu.LIMIT), cap = menu.get(InventoryIoMenu.CAP);
        graphics.drawString(font, limit == 0 ? Component.translatable("gui.drillships.limit_none")
                : Component.translatable("gui.drillships.item_limit", limit), 8, 36, TEXT, false);
        if (cap > 0) {
            Component capText = Component.translatable("gui.drillships.limit_cap", cap);
            graphics.drawString(font, capText, imageWidth - 8 - font.width(capText), 36, MUTED, false);
        }
        graphics.drawString(font, Component.translatable("gui.drillships.filter_hint"), 8, InventoryIoMenu.FILTER_Y - 10, MUTED, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
