package com.solaris99.drillship.client;

import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.network.ModNetwork.RadarScan;
import com.solaris99.drillship.ship.RadarOre;
import com.solaris99.drillship.ship.ShipRadar;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * The radar on the driver's HUD: a compass line, like Fallout's, at the top of the screen between the readouts and the
 * attitude indicator. It scrolls with the heading (N, E, S, W and the points between, ticks every 15 degrees, half a
 * turn across) and marks the nearest deposits of the ore the radar looks for where they lie, with an arrow above or
 * below when they are higher or lower than the ship; the nearest one sticks to the edge when it is behind. Under the
 * line: the target with, from tier 2, its distance and, at tier 3, its exact height; tier 3 also puts creatures and
 * hostiles on the line and shows the water's depth and the shore ahead. It shows the latest {@link RadarScan}; with
 * no radar upgrade none arrive and it stays hidden.
 */
final class RadarHud {
    private static final int MAX_WIDTH = 220;
    private static final int MIN_WIDTH = 140;
    private static final int PADDING = 4;
    private static final int STRIP = 20;
    private static final int LINE_HEIGHT = 10;
    private static final float FIELD_OF_VIEW = 180.0F;
    private static final int MARKERS = 4;
    private static final int LEVEL_TOLERANCE = 2;
    private static final long STALE_MILLIS = 1500;
    private static final int RED = 0xFFFF5555;
    // Creatures, vessels, hostiles, players.
    private static final int[] CONTACT_COLORS = {0xFF55FFFF, 0xFFFFFFFF, 0xFFFF5555, 0xFFFFFF55};
    private static final String[] POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final String[] NUMERALS = {"", "I", "II", "III"};

    /**
     * Draws the radar centred on the screen between {@code left} and {@code right} at {@code top}; when that is too
     * narrow, right-aligned at {@code fallbackRight} under {@code fallbackTop}.
     */
    static void render(GuiGraphics graphics, Font font, DrillshipEntity ship, float partialTick, int left, int right, int top,
            int fallbackRight, int fallbackTop) {
        RadarScan scan = RadarScan.latest;
        if (scan == null || scan.tier() == 0 || System.currentTimeMillis() - RadarScan.receivedAt > STALE_MILLIS) {
            return;
        }
        float yaw = Mth.rotLerp(partialTick, ship.yRotO, ship.getYRot());
        RadarOre target = RadarOre.byId(scan.target());
        String[][] lines = readouts(scan, target);
        int width, x, y;
        if (right - left >= MIN_WIDTH) {
            width = Math.min(MAX_WIDTH, right - left);
            x = Mth.clamp(graphics.guiWidth() / 2 - width / 2, left, right - width);
            y = top;
        } else {
            width = Math.min(MAX_WIDTH, fallbackRight - 2 * PADDING);
            x = fallbackRight - width;
            y = fallbackTop;
        }
        int height = STRIP + lines.length * LINE_HEIGHT + 3;
        DrillshipHud.panel(graphics, x, y, width, height);

        int x0 = x + PADDING, x1 = x + width - PADDING, center = (x0 + x1) / 2;
        float pixelsPerDegree = (x1 - x0) / FIELD_OF_VIEW;
        graphics.enableScissor(x0, y + 1, x1, y + STRIP);
        compass(graphics, font, yaw, center, y, pixelsPerDegree);
        for (int contact : scan.contacts()) {
            float bearing = bearing(RadarScan.contactX(contact), RadarScan.contactZ(contact), yaw);
            if (Math.abs(bearing) <= FIELD_OF_VIEW / 2) {
                int px = center + Math.round(bearing * pixelsPerDegree);
                graphics.fill(px, y + 13, px + 1, y + 17, CONTACT_COLORS[RadarScan.contactType(contact)]);
            }
        }
        int[] finds = scan.finds();
        for (int k = Math.min(MARKERS, finds.length) - 1; k >= 0; k--) {
            float bearing = bearing(RadarScan.findX(finds[k]), RadarScan.findZ(finds[k]), yaw);
            boolean nearest = k == 0;
            if (Math.abs(bearing) <= FIELD_OF_VIEW / 2) {
                marker(graphics, center + Math.round(bearing * pixelsPerDegree), y + 14, RadarScan.findY(finds[k]), target.color(), nearest);
            } else if (nearest) {
                edge(graphics, bearing > 0 ? x1 - 3 : x0 + 2, y + 14, bearing > 0, 0xFF000000 | target.color());
            }
        }
        graphics.disableScissor();
        // The heading: a caret over the middle of the line.
        graphics.fill(center - 2, y + 1, center + 3, y + 2, DrillshipHud.GREEN);
        graphics.fill(center - 1, y + 2, center + 2, y + 3, DrillshipHud.GREEN);
        graphics.fill(center, y + 3, center + 1, y + 4, DrillshipHud.GREEN);
        graphics.hLine(x0, x1 - 1, y + STRIP, DrillshipHud.DIM_GREEN);

        int lineY = y + STRIP + 3;
        for (String[] line : lines) {
            graphics.drawString(font, DrillshipHud.terminal(line[0]), x0, lineY, line[0].startsWith(name(target)) ? 0xFF000000 | target.color()
                    : DrillshipHud.GREEN, false);
            if (line.length > 1) {
                int color = line[1].startsWith("HOS") && !line[1].endsWith(" 0") ? RED : DrillshipHud.GREEN;
                graphics.drawString(font, DrillshipHud.terminal(line[1]), x1 - font.width(DrillshipHud.terminal(line[1])), lineY, color, false);
            }
            lineY += LINE_HEIGHT;
        }
    }

    // Labels for the eight points of the compass, and ticks every 15 degrees.
    private static void compass(GuiGraphics graphics, Font font, float yaw, int center, int y, float pixelsPerDegree) {
        float heading = yaw + 180.0F;
        for (int degrees = 0; degrees < 360; degrees += 15) {
            float offset = Mth.wrapDegrees(degrees - heading);
            if (Math.abs(offset) > FIELD_OF_VIEW / 2 + 10) {
                continue;
            }
            int px = center + Math.round(offset * pixelsPerDegree);
            if (degrees % 45 == 0) {
                String point = POINTS[degrees / 45];
                int color = degrees % 90 == 0 ? DrillshipHud.GREEN : DrillshipHud.DIM_GREEN;
                var text = DrillshipHud.terminal(point);
                graphics.drawString(font, text, px - font.width(text) / 2 + 1, y + 4, color, false);
                graphics.fill(px, y + STRIP - 4, px + 1, y + STRIP, color);
            } else {
                graphics.fill(px, y + STRIP - 2, px + 1, y + STRIP, DrillshipHud.DIM_GREEN);
            }
        }
    }

    // Degrees right (positive) or left of the heading to an offset from the ship.
    private static float bearing(int dx, int dz, float yaw) {
        return Mth.wrapDegrees((float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG) - yaw);
    }

    // A diamond at the find, bigger and brighter for the nearest, with an arrow above it if the ore is higher than
    // the ship and below it if lower.
    private static void marker(GuiGraphics graphics, int px, int py, int dy, int rgb, boolean nearest) {
        int color = (nearest ? 0xFF000000 : 0xA0000000) | rgb;
        int half = nearest ? 2 : 1;
        for (int row = -half; row <= half; row++) {
            int span = half - Math.abs(row);
            graphics.fill(px - span, py + row, px + span + 1, py + row + 1, color);
        }
        if (dy > LEVEL_TOLERANCE) {
            graphics.fill(px, py - half - 4, px + 1, py - half - 3, color);
            graphics.fill(px - 1, py - half - 3, px + 2, py - half - 2, color);
        } else if (dy < -LEVEL_TOLERANCE) {
            graphics.fill(px - 1, py + half + 2, px + 2, py + half + 3, color);
            graphics.fill(px, py + half + 3, px + 1, py + half + 4, color);
        }
    }

    // A chevron at the edge of the line, pointing the way to turn.
    private static void edge(GuiGraphics graphics, int px, int py, boolean right, int color) {
        for (int row = -2; row <= 2; row++) {
            int dx = (2 - Math.abs(row)) * (right ? 1 : -1);
            graphics.fill(px + dx, py + row, px + dx + 1, py + row + 1, color);
        }
    }

    // Each line: left text, optionally right text.
    private static String[][] readouts(RadarScan scan, RadarOre target) {
        String rdr = "RDR " + NUMERALS[scan.tier()] + " [" + DriveControls.RADAR_TARGET.getTranslatedKeyMessage().getString().toUpperCase(java.util.Locale.ROOT) + "]";
        String find = name(target) + " ---";
        if (scan.finds().length > 0) {
            int nearest = scan.finds()[0];
            int dx = RadarScan.findX(nearest), dy = RadarScan.findY(nearest), dz = RadarScan.findZ(nearest);
            String height = dy > LEVEL_TOLERANCE ? "▲" : dy < -LEVEL_TOLERANCE ? "▼" : "=";
            if (scan.tier() >= ShipRadar.MAX_TIER) {
                height = (dy > 0 ? "▲" : dy < 0 ? "▼" : "=") + Math.abs(dy);
            }
            String distance = scan.tier() >= 2 ? String.format(" %dM", Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz))) : "";
            find = name(target) + distance + " " + height;
        }
        if (scan.tier() < ShipRadar.MAX_TIER) {
            return new String[][] {{find, rdr}};
        }
        int hostiles = 0;
        for (int contact : scan.contacts()) {
            hostiles += RadarScan.contactType(contact) == ShipRadar.HOSTILE ? 1 : 0;
        }
        String water = scan.depth() < 0 ? "LAND" : String.format("DPT %dM %s", scan.depth(), scan.shore() >= 0 ? "SHR " + scan.shore() + "M" : "OPEN");
        return new String[][] {{find, rdr}, {water, "HOS " + hostiles}};
    }

    private static String name(RadarOre target) {
        return target == RadarOre.OTHER_METAL ? "METAL" : target.name();
    }

    private RadarHud() {
    }
}
