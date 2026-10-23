package com.solaris99.drillship.ship;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where a ship's interior sits in the interior dimension and where its built-ins go. Every ship owns a region spaced
 * {@link #REGION_SPACING} blocks apart along X. Local +Z points to the front of the ship. The drillhead's room (section
 * 0) starts at the origin; wagon rooms (sections 1..n) line up behind it along -Z, joined by 2-block-high passages.
 */
public record InteriorLayout(ShipTier tier, BlockPos origin) {
    public static final int REGION_SPACING = 1024;
    public static final int BASE_Y = 64;

    public static InteriorLayout of(ShipTier tier, int index) {
        return new InteriorLayout(tier, new BlockPos(index * REGION_SPACING, BASE_Y, 0));
    }

    public static int indexOf(BlockPos pos) {
        return Math.floorDiv(pos.getX(), REGION_SPACING);
    }

    public int sizeX() {
        return tier.exteriorWidth();
    }

    public int sizeY() {
        return tier.exteriorHeight();
    }

    public int sizeZ() {
        return tier.drillheadExteriorLength();
    }

    /** A box-shaped room: walls on its outer layer, air inside. */
    public record Room(BlockPos origin, int sizeX, int sizeY, int sizeZ) {
        public BlockPos maxCorner() {
            return origin.offset(sizeX - 1, sizeY - 1, sizeZ - 1);
        }

        public boolean contains(BlockPos pos) {
            int x = pos.getX() - origin.getX();
            int y = pos.getY() - origin.getY();
            int z = pos.getZ() - origin.getZ();
            return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
        }

        public boolean isWall(BlockPos pos) {
            int x = pos.getX() - origin.getX();
            int y = pos.getY() - origin.getY();
            int z = pos.getZ() - origin.getZ();
            return x == 0 || y == 0 || z == 0 || x == sizeX - 1 || y == sizeY - 1 || z == sizeZ - 1;
        }

        public AABB bounds() {
            return AABB.encapsulatingFullBlocks(origin, maxCorner());
        }

        /** The middle of the floor. */
        public Vec3 entryPoint() {
            return new Vec3(origin.getX() + sizeX / 2 + 0.5, origin.getY() + 1, origin.getZ() + sizeZ / 2 + 0.5);
        }
    }

    /** Section 0 is the drillhead, 1..n are wagons. */
    public Room room(int section) {
        if (section == 0) {
            return new Room(origin, sizeX(), sizeY(), sizeZ());
        }
        int length = tier.wagonExteriorLength();
        return new Room(origin.offset(0, 0, -section * length), sizeX(), sizeY(), length);
    }

    /** The four blocks cut through the back wall of {@code section - 1} and the front wall of {@code section}. */
    public List<BlockPos> passage(int section) {
        int frontWallOfWagon = room(section).maxCorner().getZ();
        int backWallOfPrevious = room(section - 1).origin().getZ();
        List<BlockPos> blocks = new ArrayList<>();
        for (int z : new int[] {frontWallOfWagon, backWallOfPrevious}) {
            for (int y = 1; y <= 2; y++) {
                blocks.add(new BlockPos(origin.getX() + centerX(), origin.getY() + y, z));
            }
        }
        return blocks;
    }

    private int centerX() {
        return sizeX() / 2;
    }

    // Front wall: status panel and helm at eye level.
    public BlockPos helm() {
        return origin.offset(centerX(), 2, sizeZ() - 1);
    }

    public BlockPos statusPanel() {
        return origin.offset(centerX() - 1, 2, sizeZ() - 1);
    }

    // Left wall: door and generator.
    public BlockPos doorLower() {
        return origin.offset(0, 1, 2);
    }

    public BlockPos doorUpper() {
        return origin.offset(0, 2, 2);
    }

    public BlockPos generator() {
        return origin.offset(0, 2, sizeZ() - 3);
    }

    // Right wall: storage access and crafting table.
    public BlockPos storage() {
        return origin.offset(sizeX() - 1, 2, sizeZ() - 3);
    }

    public BlockPos crafting() {
        return origin.offset(sizeX() - 1, 2, 2);
    }

    public BlockPos lamp() {
        return origin.offset(centerX(), sizeY() - 1, sizeZ() / 2);
    }

    /** Where players appear when entering through the exterior. */
    public Vec3 entryPoint() {
        return new Vec3(origin.getX() + centerX() + 0.5, origin.getY() + 1, origin.getZ() + 2.5);
    }

    /** Where the driver stands when they stop driving. */
    public Vec3 helmPoint() {
        return new Vec3(origin.getX() + centerX() + 0.5, origin.getY() + 1, origin.getZ() + sizeZ() - 1.5);
    }

    /** Everything the ship could ever use: the drillhead room plus room for the tier's maximum number of wagons. */
    public AABB bounds() {
        return room(0).bounds().minmax(room(tier.maxWagons()).bounds());
    }

    public boolean contains(BlockPos pos) {
        return bounds().contains(Vec3.atCenterOf(pos));
    }
}
