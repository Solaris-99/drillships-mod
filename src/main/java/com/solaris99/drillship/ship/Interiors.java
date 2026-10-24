package com.solaris99.drillship.ship;

import java.util.List;
import java.util.Map;

import com.solaris99.drillship.block.CockpitBlock;
import com.solaris99.drillship.block.HullPanelBlock;
import com.solaris99.drillship.block.ModulePanelBlock;
import com.solaris99.drillship.block.ShipDoorBlock;
import com.solaris99.drillship.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

/** Builds, inspects and removes interiors in the interior dimension. */
public final class Interiors {
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    // Rooms at least this tall (counting floor and ceiling) get pipes along the top of their walls.
    private static final int CONDUIT_MIN_HEIGHT = 6;

    /** The drillhead's room with all its built-in fixtures. */
    public static void build(ServerLevel level, InteriorLayout layout) {
        buildRoom(level, layout.room(0));
        level.setBlock(layout.lamp(), ModBlocks.HULL_LAMP.get().defaultBlockState(), FLAGS);
        level.setBlock(layout.helm(), ModBlocks.HELM.get().defaultBlockState(), FLAGS);
        level.setBlock(layout.statusPanel(), ModBlocks.STATUS_PANEL.get().defaultBlockState(), FLAGS);
        level.setBlock(layout.generator(), ModBlocks.GENERATOR.get().defaultBlockState(), FLAGS);
        level.setBlock(layout.storage(), ModBlocks.SHIP_STORAGE.get().defaultBlockState(), FLAGS);
        level.setBlock(layout.crafting(), ModBlocks.SHIP_CRAFTING.get().defaultBlockState(), FLAGS);
        for (InteriorLayout.CockpitPart part : layout.cockpit()) {
            level.setBlock(part.pos(), cockpitBlock(layout, part), FLAGS);
        }
        level.setBlock(layout.chair(), ModBlocks.COCKPIT_CHAIR.get().defaultBlockState().setValue(CockpitBlock.FACING, Direction.SOUTH), FLAGS);
        for (BlockPos panel : layout.modulePanels()) {
            level.setBlock(panel, ModBlocks.MODULE_PANEL.get().defaultBlockState(), FLAGS);
        }
        placeDoor(level, layout, 0);
    }

    private static BlockState cockpitBlock(InteriorLayout layout, InteriorLayout.CockpitPart part) {
        return switch (part.kind()) {
            case DESK -> ModBlocks.COCKPIT_DESK.get().defaultBlockState().setValue(CockpitBlock.FACING, part.facing());
            case CONSOLE -> ModBlocks.COCKPIT_CONSOLE.get().defaultBlockState().setValue(CockpitBlock.FACING, part.facing());
            // The radar repeater right beside the helm, telemetry further out.
            case SCREEN -> Math.abs(part.pos().getX() - layout.helm().getX()) == 1 ? ModBlocks.COCKPIT_SCREEN.get().defaultBlockState()
                    : ModBlocks.COCKPIT_TELEMETRY.get().defaultBlockState();
            case SKIRT -> ModBlocks.HULL_SKIRT.get().defaultBlockState();
            case GLASS -> ModBlocks.COCKPIT_GLASS.get().defaultBlockState();
            case HEADER -> ModBlocks.BULKHEAD.get().defaultBlockState();
            case PIPES -> ModBlocks.HULL_CONDUIT.get().defaultBlockState();
            case POST -> ModBlocks.HULL_FRAME.get().defaultBlockState();
            case ROOF -> ModBlocks.COCKPIT_ROOF.get().defaultBlockState();
            case ROOF_LAMP -> ModBlocks.COCKPIT_LAMP.get().defaultBlockState();
            case LAMP -> ModBlocks.HULL_LAMP.get().defaultBlockState();
        };
    }

    /**
     * Shows each module panel's modules, and the radio's station on the panel holding the first radio, where it is
     * heard inside. Returns false, changing nothing, while the panels aren't loaded.
     */
    public static boolean showModules(ServerLevel level, InteriorLayout layout, ShipState ship) {
        List<BlockPos> panels = layout.modulePanels();
        if (panels.isEmpty() || !level.isLoaded(panels.get(0))) {
            return false;
        }
        int radioPanel = -1;
        for (int slot = 0; slot < ship.modules().getSlots() && radioPanel < 0; slot++) {
            if (ship.moduleAt(slot) == ShipModule.RADIO) {
                radioPanel = slot / ShipState.MODULES_PER_PANEL;
            }
        }
        for (int panel = 0; panel < panels.size(); panel++) {
            BlockState current = level.getBlockState(panels.get(panel));
            if (current.getBlock() instanceof ModulePanelBlock block) {
                BlockState shown = block.showing(ship, panel, panel == radioPanel ? ship.radioStation() : 0);
                if (shown != current) {
                    level.setBlock(panels.get(panel), shown, Block.UPDATE_CLIENTS);
                }
            }
        }
        return true;
    }

    /** An empty wagon room with its door, joined to the section in front of it by a bellows-lined passage. */
    public static void buildWagon(ServerLevel level, InteriorLayout layout, int wagon) {
        buildRoom(level, layout.room(wagon));
        placeDoor(level, layout, wagon);
        for (Map.Entry<BlockPos, Boolean> lining : layout.passageLining(wagon).entrySet()) {
            BlockState state = lining.getValue() ? ModBlocks.HULL_FLOOR.get().defaultBlockState() : ModBlocks.BELLOWS.get().defaultBlockState();
            level.setBlock(lining.getKey(), state, FLAGS);
        }
        for (BlockPos pos : layout.passage(wagon)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    /** Clears a wagon room and its passage, and seals the wall of the section in front of it. */
    public static void removeWagon(ServerLevel level, InteriorLayout layout, int wagon) {
        fill(level, layout.room(wagon).bounds(), Blocks.AIR.defaultBlockState());
        for (BlockPos pos : layout.passageLining(wagon).keySet()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        }
        InteriorLayout.Room previous = layout.room(wagon - 1);
        for (BlockPos pos : layout.passage(wagon)) {
            level.setBlock(pos, previous.contains(pos) ? shell(previous, pos) : Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    /** Removes every room and passage the ship could have. */
    public static void clear(ServerLevel level, InteriorLayout layout) {
        fill(level, layout.bounds(), Blocks.AIR.defaultBlockState());
    }

    /** True when the player built nothing inside the room and nobody but {@code except} is in there. */
    public static boolean isEmpty(ServerLevel level, InteriorLayout.Room room, Entity except) {
        for (BlockPos pos : BlockPos.betweenClosed(room.origin(), room.maxCorner())) {
            BlockState state = level.getBlockState(pos);
            if (!room.isWall(pos) && !state.isAir() && !ModBlocks.isBuiltIn(state)) {
                return false;
            }
        }
        return level.getEntities(except, room.bounds()).isEmpty();
    }

    /** A wagon can go when its room and the passage leading into it are both empty. */
    public static boolean isWagonEmpty(ServerLevel level, InteriorLayout layout, int wagon, Entity except) {
        InteriorLayout.Room room = layout.room(wagon);
        for (BlockPos pos : layout.passage(wagon)) {
            if (!room.contains(pos) && !layout.room(wagon - 1).contains(pos)
                    && (!level.getBlockState(pos).isAir() || !level.getEntities(except, new AABB(pos)).isEmpty())) {
                return false;
            }
        }
        return isEmpty(level, room, except);
    }

    private static void placeDoor(ServerLevel level, InteriorLayout layout, int section) {
        BlockState door = ModBlocks.SHIP_DOOR.get().defaultBlockState();
        level.setBlock(layout.doorLower(section), door.setValue(ShipDoorBlock.HALF, DoubleBlockHalf.LOWER), FLAGS);
        level.setBlock(layout.doorUpper(section), door.setValue(ShipDoorBlock.HALF, DoubleBlockHalf.UPPER), FLAGS);
    }

    private static void buildRoom(ServerLevel level, InteriorLayout.Room room) {
        for (BlockPos pos : BlockPos.betweenClosed(room.origin(), room.maxCorner())) {
            level.setBlock(pos, room.isWall(pos) ? shell(room, pos) : Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    /**
     * The shell block for a place in a room's walls: floor and ceiling layers, beams in the vertical corners, a kick
     * plate along the bottom of the walls, pipes along their top in rooms tall enough, and plating in between, laid out
     * as 2 x 2 panels from the floor up.
     */
    static BlockState shell(InteriorLayout.Room room, BlockPos pos) {
        int x = pos.getX() - room.origin().getX(), y = pos.getY() - room.origin().getY(), z = pos.getZ() - room.origin().getZ();
        int maxX = room.sizeX() - 1, maxY = room.sizeY() - 1, maxZ = room.sizeZ() - 1;
        if (y == 0) {
            return ModBlocks.HULL_FLOOR.get().defaultBlockState();
        }
        if (y == maxY) {
            return ModBlocks.HULL_CEILING.get().defaultBlockState();
        }
        boolean sideWall = x == 0 || x == maxX, endWall = z == 0 || z == maxZ;
        if (sideWall && endWall) {
            return ModBlocks.HULL_FRAME.get().defaultBlockState();
        }
        if (y == 1) {
            return ModBlocks.HULL_SKIRT.get().defaultBlockState();
        }
        if (y == maxY - 1 && maxY >= CONDUIT_MIN_HEIGHT) {
            return ModBlocks.HULL_CONDUIT.get().defaultBlockState();
        }
        // Rows pair up from the floor: the lower block of a pair shows the panel's bottom half.
        int across = sideWall ? z : x, down = y - 1;
        return ModBlocks.HULL.get().defaultBlockState().setValue(HullPanelBlock.PANEL, (across & 1) + 2 * (down & 1));
    }

    private static void fill(ServerLevel level, AABB box, BlockState state) {
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX - 1.0E-3, box.maxY - 1.0E-3, box.maxZ - 1.0E-3);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            level.setBlock(pos, state, FLAGS);
        }
    }

    private Interiors() {
    }
}
