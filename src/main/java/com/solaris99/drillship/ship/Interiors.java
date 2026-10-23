package com.solaris99.drillship.ship;

import com.solaris99.drillship.block.ShipDoorBlock;
import com.solaris99.drillship.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Builds, inspects and removes interiors in the interior dimension. */
public final class Interiors {
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    /** The drillhead's room with all its built-in fixtures. */
    public static void build(ServerLevel level, InteriorLayout layout) {
        buildRoom(level, layout.room(0));
        level.setBlock(layout.lamp(), ModBlocks.HULL_LAMP.get().defaultBlockState(), FLAGS);
        level.setBlock(layout.helm(), ModBlocks.HELM.get().defaultBlockState(), FLAGS);
        level.setBlock(layout.statusPanel(), ModBlocks.STATUS_PANEL.get().defaultBlockState(), FLAGS);
        level.setBlock(layout.generator(), ModBlocks.GENERATOR.get().defaultBlockState(), FLAGS);
        level.setBlock(layout.storage(), ModBlocks.SHIP_STORAGE.get().defaultBlockState(), FLAGS);
        level.setBlock(layout.crafting(), ModBlocks.SHIP_CRAFTING.get().defaultBlockState(), FLAGS);
        BlockState door = ModBlocks.SHIP_DOOR.get().defaultBlockState();
        level.setBlock(layout.doorLower(), door.setValue(ShipDoorBlock.HALF, DoubleBlockHalf.LOWER), FLAGS);
        level.setBlock(layout.doorUpper(), door.setValue(ShipDoorBlock.HALF, DoubleBlockHalf.UPPER), FLAGS);
    }

    /** An empty wagon room, opened up to the section in front of it. */
    public static void buildWagon(ServerLevel level, InteriorLayout layout, int wagon) {
        buildRoom(level, layout.room(wagon));
        for (BlockPos pos : layout.passage(wagon)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    /** Clears a wagon room and seals the passage in the section in front of it. */
    public static void removeWagon(ServerLevel level, InteriorLayout layout, int wagon) {
        fill(level, layout.room(wagon), Blocks.AIR.defaultBlockState());
        InteriorLayout.Room previous = layout.room(wagon - 1);
        for (BlockPos pos : layout.passage(wagon)) {
            if (previous.contains(pos)) {
                level.setBlock(pos, ModBlocks.HULL.get().defaultBlockState(), FLAGS);
            }
        }
    }

    /** Removes every room the ship could have. */
    public static void clear(ServerLevel level, InteriorLayout layout) {
        for (int section = 0; section <= layout.tier().maxWagons(); section++) {
            fill(level, layout.room(section), Blocks.AIR.defaultBlockState());
        }
    }

    /** True when the player built nothing inside the room and nobody but {@code except} is in there. */
    public static boolean isEmpty(ServerLevel level, InteriorLayout.Room room, Entity except) {
        for (BlockPos pos : BlockPos.betweenClosed(room.origin(), room.maxCorner())) {
            if (!room.isWall(pos) && !level.getBlockState(pos).isAir()) {
                return false;
            }
        }
        return level.getEntities(except, room.bounds()).isEmpty();
    }

    private static void buildRoom(ServerLevel level, InteriorLayout.Room room) {
        for (BlockPos pos : BlockPos.betweenClosed(room.origin(), room.maxCorner())) {
            level.setBlock(pos, room.isWall(pos) ? ModBlocks.HULL.get().defaultBlockState() : Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    private static void fill(ServerLevel level, InteriorLayout.Room room, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(room.origin(), room.maxCorner())) {
            level.setBlock(pos, state, FLAGS);
        }
    }

    private Interiors() {
    }
}
