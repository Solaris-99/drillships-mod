package com.solaris99.drillship.block.entity;

import javax.annotation.Nullable;

import com.solaris99.drillship.Config;
import com.solaris99.drillship.registry.ModBlockEntities;
import com.solaris99.drillship.ship.ShipState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The Inventory IO module: the ship's inventory, as far as its filter lets it through, for pipes and hoppers to insert
 * into (input), extract from (output) or both. Items the filter holds back are hidden from them. It never moves items on
 * its own. What it lets through each second, in each direction, is capped by its own limit and the server's.
 */
public class InventoryIoBlockEntity extends ModuleBlockEntity {
    public static final int MAX_LIMIT = 1_000_000;
    private static final int TICKS_PER_SECOND = 20;

    private final ItemFilter filter = new ItemFilter(false, this::setChanged);
    private IoMode mode = IoMode.BOTH;
    // Items per second; 0 for no limit.
    private int limit;
    private long second = -1;
    private int inserted;
    private int extracted;

    public InventoryIoBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INVENTORY_IO.get(), pos, state);
    }

    public ItemFilter filter() {
        return filter;
    }

    public IoMode mode() {
        return mode;
    }

    public void cycleMode() {
        mode = mode.next();
        setChanged();
    }

    public int limit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = Math.max(0, Math.min(MAX_LIMIT, limit));
        setChanged();
    }

    /** Items per second it lets through each way: the lower of its own limit and the server's cap, where either is set. */
    public int rate() {
        return IoRates.combine(limit, Config.INVENTORY_IO_MAX_RATE.get());
    }

    @Nullable
    public IItemHandler itemHandler() {
        ShipState ship = ship();
        return ship == null ? null : new Filtered(ship.inventory());
    }

    private void startSecond() {
        long now = level == null ? 0 : level.getGameTime() / TICKS_PER_SECOND;
        if (now != second) {
            second = now;
            inserted = 0;
            extracted = 0;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        filter.save(tag, registries);
        tag.putInt("Mode", mode.ordinal());
        tag.putInt("Limit", limit);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        filter.load(tag, registries);
        mode = tag.contains("Mode") ? IoMode.byId(tag.getInt("Mode")) : IoMode.BOTH;
        limit = tag.getInt("Limit");
    }

    private class Filtered implements IItemHandler {
        private final IItemHandler inventory;

        Filtered(IItemHandler inventory) {
            this.inventory = inventory;
        }

        @Override
        public int getSlots() {
            return inventory.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            ItemStack stack = inventory.getStackInSlot(slot);
            return filter.test(stack) ? stack : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!mode.canInsert() || !filter.test(stack) || stack.isEmpty()) {
                return stack;
            }
            startSecond();
            int allowed = Math.min(stack.getCount(), rate() - inserted);
            if (allowed <= 0) {
                return stack;
            }
            ItemStack left = inventory.insertItem(slot, stack.copyWithCount(allowed), simulate);
            int moved = allowed - left.getCount();
            if (!simulate) {
                inserted += moved;
            }
            return stack.copyWithCount(stack.getCount() - moved);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!mode.canExtract() || !filter.test(inventory.getStackInSlot(slot))) {
                return ItemStack.EMPTY;
            }
            startSecond();
            int allowed = Math.min(amount, rate() - extracted);
            if (allowed <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = inventory.extractItem(slot, allowed, simulate);
            if (!simulate) {
                extracted += taken.getCount();
            }
            return taken;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return mode.canInsert() && filter.test(stack) && inventory.isItemValid(slot, stack);
        }
    }
}
