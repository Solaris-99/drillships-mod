package com.solaris99.drillship.block;

import com.solaris99.drillship.menu.ShipCraftingMenu;
import com.solaris99.drillship.menu.ShipStorageMenu;
import com.solaris99.drillship.menu.StatusMenu;
import com.solaris99.drillship.ship.ShipManager;
import com.solaris99.drillship.ship.ShipState;
import com.solaris99.drillship.ship.ShipTravel;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** A built-in fixture embedded in the interior hull. Which ship it belongs to is derived from its position. */
public class ShipInteriorBlock extends Block {
    /** Status panel: a warning is active. Generator: burning fuel. Unused by the other fixtures. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public enum Function {
        HELM, STATUS, GENERATOR, STORAGE, CRAFTING, DOOR
    }

    private final Function function;

    public ShipInteriorBlock(Function function, Properties properties) {
        super(properties);
        this.function = function;
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ShipState ship = ShipManager.get(serverPlayer.server).byInteriorPos(pos);
        if (ship == null) {
            return InteractionResult.FAIL;
        }
        switch (function) {
            case HELM -> ShipTravel.startDriving(serverPlayer, ship);
            case STATUS, GENERATOR -> StatusMenu.open(serverPlayer, ship, pos);
            case STORAGE -> ShipStorageMenu.open(serverPlayer, ship, pos);
            case CRAFTING -> serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new ShipCraftingMenu(id, inventory, ContainerLevelAccess.create(level, pos)),
                    Component.translatable("container.crafting")));
            case DOOR -> ShipTravel.exitSection(serverPlayer, ship, Math.max(0, ship.layout().sectionAt(pos)));
        }
        return InteractionResult.CONSUME;
    }
}
