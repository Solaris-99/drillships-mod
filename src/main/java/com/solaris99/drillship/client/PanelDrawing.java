package com.solaris99.drillship.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/** Draws vanilla-looking panels and slots without textures, until real GUI art exists. */
final class PanelDrawing {
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int FACE = 0xFFC6C6C6;
    private static final int SHADOW = 0xFF555555;
    private static final int OUTLINE = 0xFF000000;
    private static final int SLOT_FACE = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;

    static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, OUTLINE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, LIGHT);
        graphics.fill(x + 3, y + 3, x + width - 1, y + height - 1, SHADOW);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, FACE);
    }

    static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_SHADOW);
        graphics.fill(x, y, x + 17, y + 17, LIGHT);
        graphics.fill(x, y, x + 16, y + 16, SLOT_FACE);
    }

    static void activeSlots(GuiGraphics graphics, int left, int top, AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                slot(graphics, left + slot.x, top + slot.y);
            }
        }
    }

    private PanelDrawing() {
    }
}
