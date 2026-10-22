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

    public static void build(ServerLevel level, InteriorLayout layout) {
        for (BlockPos pos : BlockPos.betweenClosed(layout.origin(), layout.maxCorner())) {
            level.setBlock(pos, layout.isWall(pos) ? ModBlocks.HULL.get().defaultBlockState() : Blocks.AIR.defaultBlockState(), FLAGS);
        }
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

    public static void clear(ServerLevel level, InteriorLayout layout) {
        for (BlockPos pos : BlockPos.betweenClosed(layout.origin(), layout.maxCorner())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    /** True when the player built nothing inside and nobody but {@code except} is in there. */
    public static boolean isEmpty(ServerLevel level, InteriorLayout layout, Entity except) {
        for (BlockPos pos : BlockPos.betweenClosed(layout.origin(), layout.maxCorner())) {
            if (!layout.isWall(pos) && !level.getBlockState(pos).isAir()) {
                return false;
            }
        }
        return level.getEntities(except, layout.bounds()).isEmpty();
    }

    private Interiors() {
    }
}
