package com.solaris99.drillship.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** The furnace module, lit while it smelts (see FurnaceModuleBlockEntity). */
public class FurnaceModuleBlock extends ModuleBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public FurnaceModuleBlock(Properties properties) {
        super(Kind.FURNACE, properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}
