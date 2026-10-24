package com.solaris99.drillship.client;

import java.util.ArrayList;
import java.util.List;

import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.network.ModNetwork;
import com.solaris99.drillship.ship.ShipState;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The driver's HUD, styled like an old military terminal: green monospace text on dark panels with scanlines. Shows the
 * ship's attitude, drill, power and warnings, and the radar (RadarHud) when the ship has radar upgrades. Readouts come from {@link ModNetwork.ShipStatus}; attitude comes straight
 * from the interpolated entity so it moves smoothly.
 */
public final class DrillshipHud {
    static final int GREEN = 0xFF33FF33;
    static final int DIM_GREEN = 0xFF1C8C1C;
    private static final int BACKGROUND = 0xA0000000;
    private static final int SCANLINE = 0x30000000;
    private static final int MARGIN = 6;
    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int BAR_CELLS = 12;

    private static final int ATTITUDE_SIZE = 84;
    private static final float PIXELS_PER_DEGREE = 1.5F;
    private static final int LADDER_STEP = 10;

    private static final int[] WARNING_FLAGS = {
            ShipState.WARN_INVENTORY_FULL, ShipState.WARN_NO_ENERGY, ShipState.WARN_LOW_ENERGY, ShipState.WARN_NO_DRILL,
            ShipState.WARN_DRILL_BROKEN, ShipState.WARN_DRILL_WORN, ShipState.WARN_BLOCKED,
    };
    private static final String[] WARNING_TEXT = {
            "INVENTORY FULL", "NO POWER", "LOW POWER", "NO DRILL", "DRILL BROKEN", "DRILL WORN", "PATH BLOCKED",
    };

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || !(minecraft.player.getVehicle() instanceof DrillshipEntity ship)) {
            return;
        }
        Font font = minecraft.font;
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        boolean blinkOn = (minecraft.level.getGameTime() / 10) % 2 == 0;
        ModNetwork.ShipStatus status = ModNetwork.ShipStatus.latest;

        int[] readouts = renderReadouts(graphics, font, status);
        int bottom = readouts[1];
        if (status != null) {
            renderWarnings(graphics, font, status.warnings(), bottom + 2, blinkOn);
        }
        int attitudeLeft = graphics.guiWidth() - MARGIN - ATTITUDE_SIZE;
        renderAttitude(graphics, font, ship, partialTick, attitudeLeft, MARGIN);
        // The radar line goes between the readouts and the attitude indicator, or under the latter when that is too narrow.
        RadarHud.render(graphics, font, ship, partialTick, readouts[0] + MARGIN, attitudeLeft - MARGIN, MARGIN,
                graphics.guiWidth() - MARGIN, MARGIN + ATTITUDE_SIZE + 2 + LINE_HEIGHT + PADDING + 4);
    }

    // Top left: power, drill and speed. Returns the panel's right and bottom edges.
    private static int[] renderReadouts(GuiGraphics graphics, Font font, ModNetwork.ShipStatus status) {
        List<String> lines = new ArrayList<>();
        lines.add("DRILLSHIP // LINK " + (status == null ? "...." : "OK"));
        if (status != null) {
            float power = status.capacity() > 0 ? (float) status.energy() / status.capacity() : 0;
            lines.add(String.format("PWR [%s] %3d%%", bar(power), Math.round(power * 100)));
            lines.add(String.format("    %s/%s FE", compact(status.energy()), compact(status.capacity())));
            String durability = status.drillMax() > 0
                    ? String.format("%3d%%", 100 - (int) (status.drillDamage() * 100L / status.drillMax()))
                    : "---";
            lines.add(String.format("DRL %-3s  DUR %s", status.drillOn() ? "ON" : "OFF", durability));
            lines.add(String.format("SPD %4.1f B/S", status.speed()));
        }
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(terminal(line)));
        }
        int height = lines.size() * LINE_HEIGHT;
        panel(graphics, MARGIN, MARGIN, width + PADDING * 2, height + PADDING * 2);
        int y = MARGIN + PADDING;
        for (String line : lines) {
            graphics.drawString(font, terminal(line), MARGIN + PADDING, y, GREEN, false);
            y += LINE_HEIGHT;
        }
        return new int[] {MARGIN + width + PADDING * 2, MARGIN + height + PADDING * 2};
    }

    // Under the readouts, blinking.
    private static void renderWarnings(GuiGraphics graphics, Font font, int warnings, int top, boolean blinkOn) {
        int y = top;
        for (int i = 0; i < WARNING_FLAGS.length; i++) {
            if ((warnings & WARNING_FLAGS[i]) == 0) {
                continue;
            }
            Component text = terminal("!! " + WARNING_TEXT[i] + " !!");
            int width = font.width(text) + PADDING * 2;
            graphics.fill(MARGIN, y, MARGIN + width, y + LINE_HEIGHT + 2, BACKGROUND);
            if (blinkOn) {
                graphics.drawString(font, text, MARGIN + PADDING, y + 2, GREEN, false);
            }
            y += LINE_HEIGHT + 3;
        }
    }

    // Top right: an artificial horizon with a pitch ladder, plus heading and pitch readouts.
    private static void renderAttitude(GuiGraphics graphics, Font font, DrillshipEntity ship, float partialTick, int left, int top) {
        float yaw = Mth.rotLerp(partialTick, ship.yRotO, ship.getYRot());
        float pitchUp = -Mth.lerp(partialTick, ship.xRotO, ship.getXRot());
        int size = ATTITUDE_SIZE;
        int centerX = left + size / 2;
        int centerY = top + size / 2;

        panel(graphics, left, top, size, size);
        graphics.enableScissor(left + 1, top + 1, left + size - 1, top + size - 1);
        // Nose up moves the horizon down, like a real attitude indicator.
        int horizon = centerY + Math.round(pitchUp * PIXELS_PER_DEGREE);
        graphics.hLine(left, left + size, horizon, GREEN);
        for (int degrees = -90; degrees <= 90; degrees += LADDER_STEP) {
            if (degrees == 0) {
                continue;
            }
            int y = horizon - Math.round(degrees * PIXELS_PER_DEGREE);
            int halfWidth = degrees % 20 == 0 ? 14 : 8;
            graphics.hLine(centerX - halfWidth, centerX + halfWidth, y, DIM_GREEN);
            if (degrees % 20 == 0) {
                Component label = terminal(Integer.toString(Math.abs(degrees)));
                graphics.drawString(font, label, centerX + halfWidth + 3, y - 4, DIM_GREEN, false);
            }
        }
        graphics.disableScissor();

        // The fixed ship symbol: two wings and a center dot.
        graphics.hLine(centerX - 20, centerX - 6, centerY, GREEN);
        graphics.hLine(centerX + 6, centerX + 20, centerY, GREEN);
        graphics.vLine(centerX - 6, centerY, centerY + 4, GREEN);
        graphics.vLine(centerX + 6, centerY, centerY + 4, GREEN);
        graphics.fill(centerX - 1, centerY - 1, centerX + 1, centerY + 1, GREEN);

        // Minecraft yaw 0 faces south; compass headings count from north.
        int heading = Math.floorMod(Math.round(yaw) + 180, 360);
        Component readout = terminal(String.format("HDG %03d %-2s PIT %+03d", heading, cardinal(heading), Math.round(pitchUp)));
        int width = font.width(readout) + PADDING * 2;
        int readoutLeft = left + size - width;
        panel(graphics, readoutLeft, top + size + 2, width, LINE_HEIGHT + PADDING);
        graphics.drawString(font, readout, readoutLeft + PADDING, top + size + 2 + PADDING / 2 + 1, GREEN, false);
    }

    static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, BACKGROUND);
        for (int row = y + 1; row < y + height; row += 2) {
            graphics.hLine(x, x + width - 1, row, SCANLINE);
        }
        graphics.renderOutline(x, y, width, height, DIM_GREEN);
    }

    static Component terminal(String text) {
        return Component.literal(text).withStyle(style -> style.withFont(Minecraft.UNIFORM_FONT));
    }

    private static String bar(float fraction) {
        int filled = Mth.clamp(Math.round(fraction * BAR_CELLS), 0, BAR_CELLS);
        return "#".repeat(filled) + "-".repeat(BAR_CELLS - filled);
    }

    private static String compact(int value) {
        if (value >= 1_000_000) {
            return String.format("%.2fM", value / 1_000_000.0);
        }
        if (value >= 10_000) {
            return String.format("%.1fK", value / 1_000.0);
        }
        return Integer.toString(value);
    }

    private static String cardinal(int heading) {
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[Math.floorMod(Math.round(heading / 45.0F), 8)];
    }

    private DrillshipHud() {
    }
}
