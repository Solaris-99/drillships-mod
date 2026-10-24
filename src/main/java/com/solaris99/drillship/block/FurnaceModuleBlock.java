package com.solaris99.drillship.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** The furnace and alloy furnace modules, lit while they work (see FurnaceModuleBlockEntity). */
public class FurnaceModuleBlock extends ModuleBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public FurnaceModuleBlock(Kind kind, Properties properties) {
        super(kind, properties);
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }
}
