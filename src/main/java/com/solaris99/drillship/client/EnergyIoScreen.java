package com.solaris99.drillship.client;

import java.util.Locale;

import com.solaris99.drillship.block.entity.IoMode;
import com.solaris99.drillship.menu.EnergyIoMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The Energy IO module: mode, rate limit and the ship's buffer. No inventory: there's nothing to put in it. */
public class EnergyIoScreen extends AbstractContainerScreen<EnergyIoMenu> {
    private static final int TEXT = 0x404040;

    private Button mode;

    public EnergyIoScreen(EnergyIoMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 96;
    }

    @Override
    protected void init() {
        super.init();
        mode = addRenderableWidget(Button.builder(Component.empty(), b -> press(EnergyIoMenu.BUTTON_MODE))
                .bounds(leftPos + 7, topPos + 16, 162, 16).build());
        for (int i = 0; i < EnergyIoMenu.STEPS.length; i++) {
            int step = EnergyIoMenu.STEPS[i];
            int button = i + 1;
            addRenderableWidget(Button.builder(Component.literal((step > 0 ? "+" : "") + step), b -> press(button))
                    .bounds(leftPos + 7 + i * 27, topPos + 50, 26, 14).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.drillships.no_limit"), b -> press(EnergyIoMenu.BUTTON_NO_LIMIT))
                .bounds(leftPos + 7, topPos + 66, 80, 14).build());
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        IoMode io = IoMode.byId(menu.get(EnergyIoMenu.MODE));
        mode.setMessage(Component.translatable("gui.drillships.io_mode", Component.translatable("gui.drillships.io_mode." + io.name().toLowerCase(Locale.ROOT))));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        PanelDrawing.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        int limit = menu.get(EnergyIoMenu.LIMIT), cap = menu.get(EnergyIoMenu.CAP);
        Component limitText = limit == 0 ? Component.translatable("gui.drillships.limit_none") : Component.translatable("gui.drillships.limit", limit);
        graphics.drawString(font, limitText, 8, 38, TEXT, false);
        if (cap > 0) {
            Component capText = Component.translatable("gui.drillships.limit_cap", cap);
            graphics.drawString(font, capText, imageWidth - 8 - font.width(capText), 38, 0x707070, false);
        }
        graphics.drawString(font, Component.translatable("gui.drillships.energy", menu.get(EnergyIoMenu.ENERGY), menu.get(EnergyIoMenu.CAPACITY)),
                92, 69, TEXT, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
