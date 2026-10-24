package com.solaris99.drillship.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Interior wall plating. Like the exterior's, one panel spans 2 x 2 blocks: each block shows a quarter of it, picked
 * when the room is built from the block's place in its wall (see Interiors).
 */
public class HullPanelBlock extends Block {
    /** Which quarter: across + 2 * down, each 0 or 1. */
    public static final IntegerProperty PANEL = IntegerProperty.create("panel", 0, 3);

    public HullPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PANEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PANEL);
    }
}
