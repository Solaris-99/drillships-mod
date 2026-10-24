package com.solaris99.drillship.ship;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.phys.Vec3;

/**
 * Keeps coupled sections from passing through each other. Two neighbours overlap when a corner of one's end lies
 * inside the other's solid volume: the hull with its ends narrowed by the tier's taper, and the tracks below it, which
 * stop short of the ends. This matches the exterior model, slightly enlarged (the model's body is inset from the hull
 * and its top edges are bevelled), so a joint is refused a little before the model's surfaces would actually touch.
 */
public final class ShipJoints {
    // The tracks and belly end this far from a section's ends (the model's track end gap).
    private static final double TRACK_END = 0.4;
    // Points closer than this to a surface don't count as inside it: sections may touch, just not cross.
    private static final double MARGIN = 0.05;

    /** Whether the section behind overlaps the one in front of it where they're coupled. */
    public static boolean overlap(ShipTier tier, ShipShape front, ShipShape.Frame frontFrame, boolean frontTapered,
            ShipShape back, ShipShape.Frame backFrame) {
        double taper = tier.coupling().taper();
        for (Vec3 local : endPoints(front, taper, front.back, 1)) {
            if (inside(back, taper, true, backFrame.toLocal(frontFrame.toWorld(local.x, local.y, local.z)))) {
                return true;
            }
        }
        for (Vec3 local : endPoints(back, taper, back.hullFront, -1)) {
            if (inside(front, taper, frontTapered, frontFrame.toLocal(backFrame.toWorld(local.x, local.y, local.z)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the drillhead may not turn from {@code current} to {@code next}: it would start overlapping the first
     * wagon, or bend away from it past the tier's limit. A joint that is already past it (after a fall, say) may always
     * straighten out, so the ship never gets stuck.
     */
    public static boolean headBlocked(ShipState ship, ShipShape.Frame current, ShipShape.Frame next) {
        if (ship.wagonCount() == 0) {
            return false;
        }
        ShipTier tier = ship.tier();
        ShipShape.Frame wagon = ship.wagonPose(1).frame();
        double bend = bend(next, wagon);
        if (bend > tier.coupling().maxBend() && bend > bend(current, wagon) + 1.0E-3) {
            return true;
        }
        ShipShape head = ShipShape.drillhead(tier), wagonShape = ShipShape.wagon(tier);
        return overlap(tier, head, next, false, wagonShape, wagon) && !overlap(tier, head, current, false, wagonShape, wagon);
    }

    private static double bend(ShipShape.Frame a, ShipShape.Frame b) {
        return Math.toDegrees(Math.acos(Math.min(1.0, a.forward().dot(b.forward()))));
    }

    /**
     * Whether driving the drillhead to this position would make two coupled sections start overlapping, with every
     * wagon placed where the path would then put it. A train that already overlaps (after a fall) may drive on.
     */
    public static boolean trainBlocked(ShipState ship, Vec3 headPosition, float yaw, float pitch) {
        return trainOverlaps(ship, headPosition, yaw, pitch) && !trainOverlaps(ship, ship.position(), ship.yaw(), ship.pitch());
    }

    private static boolean trainOverlaps(ShipState ship, Vec3 headPosition, float yaw, float pitch) {
        ShipTier tier = ship.tier();
        ShipShape head = ShipShape.drillhead(tier), wagon = ShipShape.wagon(tier);
        ShipShape.Frame leader = ShipShape.Frame.of(headPosition, yaw, pitch);
        ShipShape leaderShape = head;
        for (int i = 1; i <= ship.wagonCount(); i++) {
            ShipShape.Frame frame = ship.wagonPose(i, headPosition, yaw, pitch).frame();
            if (overlap(tier, leaderShape, leader, leaderShape != head, wagon, frame)) {
                return true;
            }
            leader = frame;
            leaderShape = wagon;
        }
        return false;
    }

    // Corners and midpoints around one end of a section, in its own coordinates. inward: +1 for the back end.
    private static List<Vec3> endPoints(ShipShape shape, double taper, double end, int inward) {
        double hw = shape.halfWidth, top = shape.height - taper, endHalf = hw - taper;
        double ring = end + inward * taper, tracks = end + inward * TRACK_END;
        List<Vec3> points = new ArrayList<>();
        for (int side = -1; side <= 1; side += 2) {
            points.add(new Vec3(side * endHalf, 1, end));
            points.add(new Vec3(side * endHalf, top, end));
            points.add(new Vec3(side * endHalf, (1 + top) / 2, end));
            points.add(new Vec3(side * endHalf / 2, top, end));
            points.add(new Vec3(side * hw, 1, ring));
            points.add(new Vec3(side * hw, shape.height, ring));
            points.add(new Vec3(side * hw, 0, tracks));
            points.add(new Vec3(side * hw, 1, tracks));
        }
        points.add(new Vec3(0, top, end));
        points.add(new Vec3(0, 1, end));
        points.add(new Vec3(0, 0, tracks));
        return points;
    }

    private static boolean inside(ShipShape shape, double taper, boolean frontTapered, Vec3 p) {
        if (p.z < shape.back + MARGIN || p.z > shape.hullFront - MARGIN || p.y < MARGIN || p.y > shape.height - MARGIN) {
            return false;
        }
        if (p.y < 1) {
            double front = shape.hullFront - (frontTapered ? TRACK_END : 0);
            return Math.abs(p.x) < shape.halfWidth - MARGIN && p.z > shape.back + TRACK_END + MARGIN && p.z < front - MARGIN;
        }
        double inset = Math.max(0, taper - (p.z - shape.back));
        if (frontTapered) {
            inset = Math.max(inset, taper - (shape.hullFront - p.z));
        }
        return Math.abs(p.x) < shape.halfWidth - inset - MARGIN && p.y < shape.height - inset - MARGIN;
    }

    private ShipJoints() {
    }
}
