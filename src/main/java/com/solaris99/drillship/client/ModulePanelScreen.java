package com.solaris99.drillship.client;

import com.solaris99.drillship.menu.ModulePanelMenu;
import com.solaris99.drillship.ship.RadarOre;
import com.solaris99.drillship.ship.RadioStation;
import com.solaris99.drillship.ship.ShipRadar;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A module panel: its four bays on the left; the ship's radar, with what it looks for, and radio on the right. */
public class ModulePanelScreen extends AbstractContainerScreen<ModulePanelMenu> {
    private static final int TEXT = 0x404040;
    private static final int MUTED = 0x707070;
    private static final int INFO_X = 72;
    private static final int TARGET_Y = 30;
    private static final int STATION_Y = 60;
    private static final int BAY_BACKING = 0xFF3B3F45;
    private static final String[] NUMERALS = {"", "I", "II", "III"};

    private Button previousTarget;
    private Button nextTarget;
    private Button previousStation;
    private Button nextStation;

    public ModulePanelScreen(ModulePanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = ModulePanelMenu.INVENTORY_Y + 82;
        this.inventoryLabelY = ModulePanelMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        previousTarget = arrow("<", TARGET_Y, ModulePanelMenu.BUTTON_PREVIOUS_TARGET, INFO_X);
        nextTarget = arrow(">", TARGET_Y, ModulePanelMenu.BUTTON_NEXT_TARGET, imageWidth - 20);
        previousStation = arrow("<", STATION_Y, ModulePanelMenu.BUTTON_PREVIOUS_STATION, INFO_X);
        nextStation = arrow(">", STATION_Y, ModulePanelMenu.BUTTON_NEXT_STATION, imageWidth - 20);
    }

    private Button arrow(String label, int y, int button, int x) {
        return addRenderableWidget(Button.builder(Component.literal(label), b -> press(button)).bounds(leftPos + x, topPos + y, 12, 12).build());
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    private int tier() {
        return ShipRadar.tier(menu.get(ModulePanelMenu.RADARS));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean radar = tier() > 0, radio = menu.get(ModulePanelMenu.RADIOS) > 0;
        previousTarget.visible = nextTarget.visible = radar;
        previousStation.visible = nextStation.visible = radio;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        PanelDrawing.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        // The bays sit in a dark plate, like on the wall.
        int x = leftPos + ModulePanelMenu.BAYS_X - 5, y = topPos + ModulePanelMenu.BAYS_Y - 5;
        graphics.fill(x, y, x + 2 * 18 + 8, y + 2 * 18 + 8, BAY_BACKING);
        PanelDrawing.activeSlots(graphics, leftPos, topPos, menu);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        int tier = tier();
        if (tier > 0) {
            graphics.drawString(font, Component.translatable("gui.drillships.radar", NUMERALS[tier], ShipRadar.oreRange(tier)), INFO_X, 18, TEXT, false);
            RadarOre target = RadarOre.byId(menu.get(ModulePanelMenu.TARGET));
            between(graphics, target.title().getString(), TARGET_Y + 2, 0xFF000000 | darker(target.color()));
        } else {
            graphics.drawString(font, Component.translatable("gui.drillships.no_radar"), INFO_X, 18, MUTED, false);
        }
        if (menu.get(ModulePanelMenu.RADIOS) > 0) {
            graphics.drawString(font, Component.translatable("gui.drillships.radio"), INFO_X, STATION_Y - 10, TEXT, false);
            between(graphics, RadioStation.byId(menu.get(ModulePanelMenu.STATION)).title().getString(), STATION_Y + 2, TEXT);
        } else {
            graphics.drawString(font, Component.translatable("gui.drillships.no_radio"), INFO_X, STATION_Y - 10, MUTED, false);
        }
    }

    // Text centred between a row's arrows, cut short when it doesn't fit.
    private void between(GuiGraphics graphics, String text, int y, int color) {
        int room = imageWidth - 22 - (INFO_X + 14);
        String shown = font.width(text) <= room ? text : font.plainSubstrByWidth(text, room - font.width("..")) + "..";
        graphics.drawString(font, shown, INFO_X + 14 + (room - font.width(shown)) / 2, y, color, false);
    }

    // The HUD's bright colours, darkened to read on the panel's light grey.
    private static int darker(int rgb) {
        return (rgb >> 16 & 255) * 3 / 5 << 16 | (rgb >> 8 & 255) * 3 / 5 << 8 | (rgb & 255) * 3 / 5;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // The full station name, when it had to be cut short.
        if (menu.get(ModulePanelMenu.RADIOS) > 0 && mouseX >= leftPos + INFO_X + 14 && mouseX < leftPos + imageWidth - 22
                && mouseY >= topPos + STATION_Y && mouseY < topPos + STATION_Y + 12) {
            graphics.renderTooltip(font, RadioStation.byId(menu.get(ModulePanelMenu.STATION)).title(), mouseX, mouseY);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }
}
