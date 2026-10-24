package com.solaris99.drillship.client;

import java.util.ArrayList;
import java.util.List;

import com.solaris99.drillship.menu.StatusMenu;
import com.solaris99.drillship.ship.ShipState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class StatusScreen extends AbstractContainerScreen<StatusMenu> {
    private static final int TEXT = 0x404040;
    private static final int WARNING = 0xB02020;
    private static final int LINE_HEIGHT = 10;
    private static final int BUTTONS_TOP = StatusMenu.INVENTORY_Y - 54;
    private static final int[] WARNING_FLAGS = {
            ShipState.WARN_NO_ENERGY, ShipState.WARN_LOW_ENERGY, ShipState.WARN_INVENTORY_FULL, ShipState.WARN_NO_DRILL,
            ShipState.WARN_DRILL_BROKEN, ShipState.WARN_DRILL_WORN, ShipState.WARN_BLOCKED,
    };
    private static final String[] WARNING_KEYS = {
            "no_energy", "low_energy", "inventory_full", "no_drill", "drill_broken", "drill_worn", "blocked",
    };

    private Button drillButton;
    private Button autoFuelButton;

    public StatusScreen(StatusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = StatusMenu.INVENTORY_Y + 82;
        this.inventoryLabelY = StatusMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        int top = topPos + BUTTONS_TOP;
        drillButton = addRenderableWidget(Button.builder(Component.empty(), b -> press(StatusMenu.BUTTON_TOGGLE_DRILL))
                .bounds(leftPos + 7, top, 80, 18).build());
        autoFuelButton = addRenderableWidget(Button.builder(Component.empty(), b -> press(StatusMenu.BUTTON_TOGGLE_AUTO_FUEL))
                .bounds(leftPos + 89, top, 80, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.drillships.detach_wagon"), b -> press(StatusMenu.BUTTON_DETACH_WAGON))
                .bounds(leftPos + 7, top + 20, 80, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.drillships.pick_up"), b -> press(StatusMenu.BUTTON_PICK_UP))
                .bounds(leftPos + 89, top + 20, 80, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.drillships.controls"), b -> minecraft.setScreen(new DriveControlsScreen(this)))
                .bounds(leftPos + imageWidth - 64, topPos + 3, 60, 14).build());
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        drillButton.setMessage(Component.translatable(menu.hasFlag(StatusMenu.FLAG_DRILL_ON) ? "gui.drillships.drill_on" : "gui.drillships.drill_off"));
        autoFuelButton.setMessage(Component.translatable(menu.hasFlag(StatusMenu.FLAG_AUTO_FUEL) ? "gui.drillships.auto_fuel_on" : "gui.drillships.auto_fuel_off"));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        PanelDrawing.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        PanelDrawing.activeSlots(graphics, leftPos, topPos, menu);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        int x = 8;
        int y = 18;
        for (Component line : readouts()) {
            graphics.drawString(font, line, x, y, TEXT, false);
            y += LINE_HEIGHT;
        }
        graphics.drawString(font, Component.translatable("gui.drillships.drill_slot"), StatusMenu.DRILL_SLOT_X - 2, StatusMenu.DRILL_SLOT_Y + 18, TEXT, false);
        graphics.drawString(font, Component.translatable("gui.drillships.fuel_slot"), StatusMenu.FUEL_SLOT_X - 2, StatusMenu.FUEL_SLOT_Y + 18, TEXT, false);
        graphics.drawString(font, Component.translatable("gui.drillships.wagon_slot"), StatusMenu.WAGON_SLOT_X - 6, StatusMenu.WAGON_SLOT_Y + 18, TEXT, false);

        y += 2;
        int warnings = menu.get(StatusMenu.WARNINGS);
        for (int i = 0; i < WARNING_FLAGS.length && y + LINE_HEIGHT <= BUTTONS_TOP; i++) {
            if ((warnings & WARNING_FLAGS[i]) != 0) {
                graphics.drawString(font, Component.literal("⚠ ").append(Component.translatable("warning.drillships." + WARNING_KEYS[i])), x, y, WARNING, false);
                y += LINE_HEIGHT;
            }
        }
    }

    private List<Component> readouts() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.drillships.energy", compact(menu.get(StatusMenu.ENERGY)), compact(menu.get(StatusMenu.CAPACITY))));
        lines.add(menu.get(StatusMenu.BURN_TIME) > 0
                ? Component.translatable("gui.drillships.generating", menu.get(StatusMenu.GENERATION))
                : Component.translatable("gui.drillships.idle"));
        lines.add(Component.translatable("gui.drillships.energy_use", menu.get(StatusMenu.ENERGY_USE)));
        lines.add(Component.translatable("gui.drillships.fuel", duration(menu.get(StatusMenu.FUEL_SECONDS))));
        int max = menu.get(StatusMenu.DRILL_MAX);
        lines.add(max > 0
                ? Component.translatable("gui.drillships.drill", 100 - menu.get(StatusMenu.DRILL_DAMAGE) * 100L / max)
                : Component.translatable("gui.drillships.drill_none"));
        lines.add(Component.translatable("gui.drillships.wagons", menu.get(StatusMenu.WAGONS), menu.get(StatusMenu.MAX_WAGONS)));
        lines.add(Component.translatable("gui.drillships.speed", String.format("%.1f", menu.get(StatusMenu.SPEED_TENTHS) / 10.0)));
        lines.add(Component.translatable("gui.drillships.position", menu.get(StatusMenu.X), menu.get(StatusMenu.Y), menu.get(StatusMenu.Z))
                .withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private static String compact(int value) {
        if (value >= 1_000_000) {
            return String.format("%.2fM", value / 1_000_000.0);
        }
        if (value >= 10_000) {
            return String.format("%.1fk", value / 1_000.0);
        }
        return Integer.toString(value);
    }

    // Long estimates are capped: past an hour the exact figure stops mattering.
    private static String duration(int seconds) {
        if (seconds >= 3600) {
            return "1h+";
        }
        return seconds >= 60 ? String.format("%dm %02ds", seconds / 60, seconds % 60) : seconds + "s";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
