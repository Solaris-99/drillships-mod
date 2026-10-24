package com.solaris99.drillship.ship;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
    // The hardened cockpit pod is this tall inside, roof above; the standard bulkhead's opening ends under its header.
    private static final int POD_HEIGHT = 3;
    private static final int BULKHEAD_HEADER = 4;

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

    /**
     * Section 0 is the drillhead, 1..n are wagons. Rooms are spaced by the tier's coupling gap, like the sections
     * outside, and joined across it by a passage.
     */
    public Room room(int section) {
        if (section == 0) {
            return new Room(origin, sizeX(), sizeY(), sizeZ());
        }
        int length = tier.wagonExteriorLength();
        return new Room(origin.offset(0, 0, -section * (length + tier.coupling().gap())), sizeX(), sizeY(), length);
    }

    /** The section whose room contains {@code pos}, or -1. */
    public int sectionAt(BlockPos pos) {
        for (int section = 0; section <= tier.maxWagons(); section++) {
            if (room(section).contains(pos)) {
                return section;
            }
        }
        return -1;
    }

    /**
     * The opening into {@code section} from the one in front: through the back wall of {@code section - 1}, across
     * the gap and through the front wall of {@code section}. As wide and high as the tier's passage, from the floor up.
     */
    public List<BlockPos> passage(int section) {
        List<BlockPos> blocks = new ArrayList<>();
        int x0 = passageX0(), y1 = tier.coupling().passageHeight();
        for (int z = room(section).maxCorner().getZ(); z <= room(section - 1).origin().getZ(); z++) {
            for (int x = x0; x < x0 + tier.coupling().passageWidth(); x++) {
                for (int y = 1; y <= y1; y++) {
                    blocks.add(new BlockPos(origin.getX() + x, origin.getY() + y, z));
                }
            }
        }
        return blocks;
    }

    /**
     * The lining of the passage across the gap: a floor under it and bellows on its sides and ceiling, the same
     * accordion that joins the sections outside. Maps each block to whether it is floor.
     */
    public Map<BlockPos, Boolean> passageLining(int section) {
        Map<BlockPos, Boolean> blocks = new LinkedHashMap<>();
        int x0 = passageX0() - 1, x1 = passageX0() + tier.coupling().passageWidth(), y1 = tier.coupling().passageHeight() + 1;
        for (int z = room(section).maxCorner().getZ() + 1; z < room(section - 1).origin().getZ(); z++) {
            for (int x = x0; x <= x1; x++) {
                for (int y = 0; y <= y1; y++) {
                    if (x == x0 || x == x1 || y == 0 || y == y1) {
                        blocks.put(new BlockPos(origin.getX() + x, origin.getY() + y, z), y == 0);
                    }
                }
            }
        }
        return blocks;
    }

    private int passageX0() {
        return centerX() - tier.coupling().passageWidth() / 2;
    }

    private int centerX() {
        return sizeX() / 2;
    }

    // Front wall: status panel and helm at eye level, above the cockpit's desk.
    public BlockPos helm() {
        return origin.offset(centerX(), 2, sizeZ() - 1);
    }

    public BlockPos statusPanel() {
        return origin.offset(centerX() - 1, 2, sizeZ() - 1);
    }

    /** The driver's chair, facing the front, two blocks back from the front wall with the desk in between. */
    public BlockPos chair() {
        return origin.offset(centerX(), 1, sizeZ() - 3);
    }

    /** A piece of the cockpit (see {@link #cockpit()}), facing where its operator is. */
    public record CockpitPart(BlockPos pos, Kind kind, Direction facing) {
        public enum Kind {
            DESK, CONSOLE, SCREEN, SKIRT, GLASS, HEADER, PIPES, POST, ROOF, ROOF_LAMP, LAMP
        }
    }

    /**
     * Everything around the chair. In every tier: a desk across the cockpit's width in front of the chair, a console
     * on either side of it and screens filling the front wall beside the helm and status panel. Then, by tier:
     * <ul>
     * <li>basic: the cabin is three blocks wide, so the cockpit is just its front two rows, flanked by the generator
     * and the storage access on the walls;</li>
     * <li>standard: a glazed bulkhead across the cabin behind the chair, with a three-wide opening under a header;</li>
     * <li>hardened: the cabin is too wide to wall off whole, so the cockpit is a three-wide glazed pod with a roof and
     * its own lamp, leaving a bay on either side: the generator's on the right, the upgrade panels' on the left.</li>
     * </ul>
     */
    public List<CockpitPart> cockpit() {
        List<CockpitPart> parts = new ArrayList<>();
        int cx = centerX(), chairZ = chair().getZ() - origin.getZ(), deskZ = chairZ + 1, front = sizeZ() - 1;
        boolean pod = isPodded();
        int x0 = pod ? cx - 1 : 1, x1 = pod ? cx + 1 : sizeX() - 2;
        for (int x = x0; x <= x1; x++) {
            parts.add(part(x, 1, deskZ, CockpitPart.Kind.DESK, Direction.NORTH));
            if (x != cx && x != cx - 1) {
                parts.add(part(x, 2, front, CockpitPart.Kind.SCREEN, Direction.NORTH));
            }
        }
        parts.add(part(x0, 1, chairZ, CockpitPart.Kind.CONSOLE, Direction.EAST));
        parts.add(part(x1, 1, chairZ, CockpitPart.Kind.CONSOLE, Direction.WEST));
        if (pod) {
            // Glazed up to the roof, a post at each back corner and a header over the two-high door.
            int left = x0 - 1, right = x1 + 1, back = chairZ - 2, top = POD_HEIGHT;
            for (int z = back; z <= deskZ; z++) {
                for (int x = left; x <= right; x++) {
                    boolean side = x == left || x == right, rear = z == back;
                    for (int y = 1; (side || rear) && y <= top; y++) {
                        CockpitPart.Kind kind = side && rear ? CockpitPart.Kind.POST : rear && x == cx ? (y == top ? CockpitPart.Kind.HEADER : null)
                                : y == 1 ? CockpitPart.Kind.SKIRT : CockpitPart.Kind.GLASS;
                        if (kind != null) {
                            parts.add(part(x, y, z, kind, Direction.NORTH));
                        }
                    }
                    parts.add(part(x, top + 1, z, x == cx && z == chairZ ? CockpitPart.Kind.ROOF_LAMP : CockpitPart.Kind.ROOF, Direction.NORTH));
                }
            }
        } else if (tier.interiorWidth() > 3) {
            int z = chairZ - 1;
            for (int x = 1; x <= sizeX() - 2; x++) {
                for (int y = 1; y <= sizeY() - 2; y++) {
                    boolean opening = Math.abs(x - cx) <= 1 && y < BULKHEAD_HEADER;
                    CockpitPart.Kind kind = opening ? null : y == 1 ? CockpitPart.Kind.SKIRT : y < BULKHEAD_HEADER ? CockpitPart.Kind.GLASS
                            : y == BULKHEAD_HEADER ? CockpitPart.Kind.HEADER : CockpitPart.Kind.PIPES;
                    if (kind != null) {
                        parts.add(part(x, y, z, kind, Direction.NORTH));
                    }
                }
            }
            parts.add(part(cx, sizeY() - 1, chairZ, CockpitPart.Kind.LAMP, Direction.NORTH));
        }
        return parts;
    }

    // Cabins wider than this get a pod rather than a bulkhead from wall to wall.
    private boolean isPodded() {
        return tier.interiorWidth() > 5;
    }

    private CockpitPart part(int x, int y, int z, CockpitPart.Kind kind, Direction facing) {
        return new CockpitPart(origin.offset(x, y, z), kind, facing);
    }

    /** Upgrade panels in the left wall at eye level, from the front back: beside the cockpit, within the driver's reach. */
    public List<BlockPos> upgradePanels() {
        List<BlockPos> panels = new ArrayList<>();
        for (int i = 0; i < tier.upgradePanels(); i++) {
            panels.add(origin.offset(sizeX() - 1, 2, sizeZ() - 2 - i));
        }
        return panels;
    }

    // Min-X wall, on your right when facing the helm (and on the ship's right outside): door and generator. Every
    // section has its door here, third block from the back, where the exterior shows it.
    public BlockPos doorLower(int section) {
        return room(section).origin().offset(0, 1, 2);
    }

    public BlockPos doorUpper(int section) {
        return room(section).origin().offset(0, 2, 2);
    }

    /** Just inside a section's door, facing into the room. */
    public Vec3 doorEntry(int section) {
        BlockPos door = doorLower(section);
        return new Vec3(door.getX() + 1.5, door.getY(), door.getZ() + 0.5);
    }

    public BlockPos generator() {
        return origin.offset(0, 2, sizeZ() - 3);
    }

    // Max-X wall: crafting table and storage access at the back, facing the door; the upgrade panels at the front.
    public BlockPos storage() {
        return origin.offset(sizeX() - 1, 2, 3);
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

    /** Where the driver stands when they stop driving: just behind the chair, facing the front. */
    public Vec3 helmPoint() {
        BlockPos chair = chair();
        return new Vec3(chair.getX() + 0.5, chair.getY(), chair.getZ() - 0.5);
    }

    /** Everything the ship could ever use: the drillhead room plus room for the tier's maximum number of wagons. */
    public AABB bounds() {
        return room(0).bounds().minmax(room(tier.maxWagons()).bounds());
    }

    public boolean contains(BlockPos pos) {
        return bounds().contains(Vec3.atCenterOf(pos));
    }
}
