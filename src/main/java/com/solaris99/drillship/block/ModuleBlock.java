package com.solaris99.drillship.block;

import java.util.Locale;

import javax.annotation.Nullable;

import com.solaris99.drillship.block.entity.EnergyIoBlockEntity;
import com.solaris99.drillship.block.entity.FurnaceModuleBlockEntity;
import com.solaris99.drillship.block.entity.InventoryIoBlockEntity;
import com.solaris99.drillship.block.entity.TeslaTurretBlockEntity;
import com.solaris99.drillship.block.entity.IoMode;
import com.solaris99.drillship.menu.CraftingStorageMenu;
import com.solaris99.drillship.menu.EnergyIoMenu;
import com.solaris99.drillship.menu.FurnaceModuleMenu;
import com.solaris99.drillship.menu.InventoryIoMenu;
import com.solaris99.drillship.menu.ShipCraftingMenu;
import com.solaris99.drillship.menu.ShipStorageMenu;
import com.solaris99.drillship.menu.StatusMenu;
import com.solaris99.drillship.registry.ModBlockEntities;
import com.solaris99.drillship.registry.ModTags;
import com.solaris99.drillship.ship.ShipManager;
import com.solaris99.drillship.ship.ShipState;
import com.solaris99.drillship.ship.ShipTravel;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A module installed in the hull from the inside, in place of a wall block (or, for the hatch, a floor or ceiling
 * block). Indestructible like the hull; it comes out with a wrench (see ShipModules). Which ship it serves is derived
 * from its position.
 */
public class ModuleBlock extends Block implements EntityBlock {
    public enum Kind {
        CRAFTING, CRAFTING_INVENTORY_ACCESS, INVENTORY_ACCESS, INVENTORY_IO, FURNACE, ENERGY_IO, DOOR, HATCH, TESLA_TURRET
    }

    private final Kind kind;

    public ModuleBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** The block a door's lower half is in, which is where it leads out from. */
    public BlockPos base(BlockState state, BlockPos pos) {
        return pos;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (kind) {
            case INVENTORY_IO -> new InventoryIoBlockEntity(pos, state);
            case FURNACE -> new FurnaceModuleBlockEntity(pos, state);
            case ENERGY_IO -> new EnergyIoBlockEntity(pos, state);
            case TESLA_TURRET -> new TeslaTurretBlockEntity(pos, state);
            default -> null;
        };
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        if (type == ModBlockEntities.FURNACE_MODULE.get()) {
            return (BlockEntityTicker<T>) (BlockEntityTicker<FurnaceModuleBlockEntity>) FurnaceModuleBlockEntity::serverTick;
        }
        if (type == ModBlockEntities.TESLA_TURRET.get()) {
            return (BlockEntityTicker<T>) (BlockEntityTicker<TeslaTurretBlockEntity>) TeslaTurretBlockEntity::serverTick;
        }
        return null;
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
        switch (kind) {
            case CRAFTING -> serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new ShipCraftingMenu(id, inventory, ContainerLevelAccess.create(level, pos), this),
                    Component.translatable("container.crafting")));
            case CRAFTING_INVENTORY_ACCESS -> CraftingStorageMenu.open(serverPlayer, ship, pos);
            case INVENTORY_ACCESS -> ShipStorageMenu.open(serverPlayer, ship, pos);
            case INVENTORY_IO -> InventoryIoMenu.open(serverPlayer, pos);
            case FURNACE -> FurnaceModuleMenu.open(serverPlayer, pos);
            case ENERGY_IO -> EnergyIoMenu.open(serverPlayer, pos);
            case DOOR, HATCH -> ShipTravel.exitThroughModule(serverPlayer, ship, base(state, pos));
            // The turrets' settings are on the status panel.
            case TESLA_TURRET -> StatusMenu.open(serverPlayer, ship, pos);
        }
        return InteractionResult.CONSUME;
    }

    // A wrench steps an IO module through input, output and both.
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        if (kind != Kind.ENERGY_IO && kind != Kind.INVENTORY_IO || !stack.is(ModTags.WRENCHES)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        IoMode mode = null;
        if (level.getBlockEntity(pos) instanceof EnergyIoBlockEntity energyIo) {
            energyIo.cycleMode();
            mode = energyIo.mode();
        } else if (level.getBlockEntity(pos) instanceof InventoryIoBlockEntity inventoryIo) {
            inventoryIo.cycleMode();
            mode = inventoryIo.mode();
        }
        if (!level.isClientSide() && mode != null) {
            player.displayClientMessage(Component.translatable("message.drillships.io_mode", getName(),
                    Component.translatable("gui.drillships.io_mode." + mode.name().toLowerCase(Locale.ROOT))), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
}
