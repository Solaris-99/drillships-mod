package com.solaris99.drillship.block.entity;

import java.util.List;

import javax.annotation.Nullable;

import com.solaris99.drillship.ship.ShipManager;
import com.solaris99.drillship.ship.ShipState;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** A module that keeps settings or items of its own. Which ship it serves is derived from its position. */
public abstract class ModuleBlockEntity extends BlockEntity {
    protected ModuleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The ship it is installed in; always null on the client. */
    @Nullable
    protected ShipState ship() {
        return level instanceof ServerLevel server ? ShipManager.get(server.getServer()).byInteriorPos(worldPosition) : null;
    }

    /** What it holds, given back along with it when it's taken out. */
    public List<ItemStack> contents() {
        return List.of();
    }
}
