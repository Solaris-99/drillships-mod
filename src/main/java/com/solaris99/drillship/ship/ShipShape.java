package com.solaris99.drillship.ship;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Geometry of one ship section's exterior (the drillhead or a wagon). Local coordinates: x across (right is positive),
 * y up from the hull's bottom, z along the ship (forward is positive). The origin is the center of the hull's bottom face.
 */
public final class ShipShape {
    public final double halfWidth;
    public final double height;
    public final double back;
    public final double hullFront;
    /** Equal to {@link #hullFront} for sections without a drill. */
    public final double drillFront;

    private ShipShape(ShipTier tier, int length, double drillDepth) {
        this.halfWidth = tier.exteriorWidth() / 2.0;
        this.height = tier.exteriorHeight();
        this.back = -length / 2.0;
        this.hullFront = length / 2.0;
        this.drillFront = hullFront + drillDepth;
    }

    public static ShipShape drillhead(ShipTier tier) {
        return new ShipShape(tier, tier.drillheadExteriorLength(), 1.0);
    }

    public static ShipShape wagon(ShipTier tier) {
        return new ShipShape(tier, tier.wagonExteriorLength(), 0.0);
    }

    public double hullLength() {
        return hullFront - back;
    }

    public int faceArea() {
        return Mth.ceil(halfWidth * 2) * Mth.ceil(height);
    }

    /** Hull plus drill: everything that must be free of solid blocks. */
    public Box whole() {
        return new Box(-halfWidth, 0, back, halfWidth, height, drillFront);
    }

    public Box hull() {
        return new Box(-halfWidth, 0, back, halfWidth, height, hullFront);
    }

    /** The drill itself plus one block ahead of it. Everything in here is mined while the drill spins. */
    public Box drillZone() {
        return new Box(-halfWidth, 0, hullFront, halfWidth, height, drillFront + 1.0);
    }

    /** A thin slab under the ship: a block whose center is in here touches the ship's bottom. */
    public Box below() {
        return new Box(-halfWidth, -0.6, back, halfWidth, 0, drillFront);
    }

    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public boolean contains(Vec3 local) {
            return local.x > minX && local.x < maxX && local.y > minY && local.y < maxY && local.z > minZ && local.z < maxZ;
        }

        public Box inflate(double amount) {
            return new Box(minX - amount, minY - amount, minZ - amount, maxX + amount, maxY + amount, maxZ + amount);
        }
    }

    /** An oriented placement of the ship in the world. */
    public record Frame(Vec3 origin, Vec3 right, Vec3 up, Vec3 forward) {
        public static Frame of(Vec3 origin, float yaw, float pitch) {
            Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
            Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 up = right.cross(forward);
            return new Frame(origin, right, up, forward);
        }

        public Vec3 toWorld(double x, double y, double z) {
            return origin.add(right.scale(x)).add(up.scale(y)).add(forward.scale(z));
        }

        public Vec3 toLocal(Vec3 world) {
            Vec3 d = world.subtract(origin);
            return new Vec3(d.dot(right), d.dot(up), d.dot(forward));
        }

        public boolean contains(Box box, Vec3 world) {
            return box.contains(toLocal(world));
        }

        public AABB bounds(Box box) {
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 8; i++) {
                Vec3 corner = toWorld(
                        (i & 1) == 0 ? box.minX() : box.maxX(),
                        (i & 2) == 0 ? box.minY() : box.maxY(),
                        (i & 4) == 0 ? box.minZ() : box.maxZ());
                minX = Math.min(minX, corner.x);
                minY = Math.min(minY, corner.y);
                minZ = Math.min(minZ, corner.z);
                maxX = Math.max(maxX, corner.x);
                maxY = Math.max(maxY, corner.y);
                maxZ = Math.max(maxZ, corner.z);
            }
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        /** Block positions whose center lies inside the box. */
        public List<BlockPos> blocksIn(Box box) {
            AABB bounds = bounds(box);
            List<BlockPos> result = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(
                    Mth.floor(bounds.minX), Mth.floor(bounds.minY), Mth.floor(bounds.minZ),
                    Mth.floor(bounds.maxX), Mth.floor(bounds.maxY), Mth.floor(bounds.maxZ))) {
                if (contains(box, Vec3.atCenterOf(pos))) {
                    result.add(pos.immutable());
                }
            }
            return result;
        }
    }
}
