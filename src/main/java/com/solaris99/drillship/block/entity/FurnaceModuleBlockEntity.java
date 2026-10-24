package com.solaris99.drillship.block.entity;

import java.util.List;
import java.util.Optional;

import com.solaris99.drillship.block.FurnaceModuleBlock;
import com.solaris99.drillship.registry.ModBlockEntities;
import com.solaris99.drillship.ship.ShipState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The furnace module: smelts at vanilla speed on FE from the ship's buffer instead of fuel, drawing only while it
 * smelts. Once a second it can push its output into the ship's inventory and pull input from it, but only items on its
 * whitelist (an empty one pulls nothing).
 */
public class FurnaceModuleBlockEntity extends ModuleBlockEntity {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    public static final int ENERGY_PER_TICK = 20;
    private static final int TRANSFER_TICKS = 20;

    private final ItemStackHandler items = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == INPUT;
        }
    };
    private final ItemFilter pullFilter = new ItemFilter(true, this::setChanged);
    private boolean pushOutput;
    private boolean pullInput;
    private int progress;
    private int total;
    // Something to smelt, but not enough energy in the buffer.
    private boolean starved;
    private ItemStack smelting = ItemStack.EMPTY;

    public FurnaceModuleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FURNACE_MODULE.get(), pos, state);
    }

    public ItemStackHandler items() {
        return items;
    }

    public ItemFilter pullFilter() {
        return pullFilter;
    }

    public boolean pushesOutput() {
        return pushOutput;
    }

    public boolean pullsInput() {
        return pullInput;
    }

    public void togglePush() {
        pushOutput = !pushOutput;
        setChanged();
    }

    public void togglePull() {
        pullInput = !pullInput;
        setChanged();
    }

    public int progress() {
        return progress;
    }

    public int total() {
        return total;
    }

    public boolean isStarved() {
        return starved;
    }

    @Override
    public List<ItemStack> contents() {
        return List.of(items.getStackInSlot(INPUT), items.getStackInSlot(OUTPUT));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FurnaceModuleBlockEntity furnace) {
        ShipState ship = furnace.ship();
        if (ship == null) {
            return;
        }
        if (level.getGameTime() % TRANSFER_TICKS == 0) {
            furnace.transfer(ship);
        }
        boolean lit = furnace.smelt((ServerLevel) level, ship);
        if (state.getValue(FurnaceModuleBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(FurnaceModuleBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    // Returns whether it smelted this tick.
    private boolean smelt(ServerLevel level, ShipState ship) {
        ItemStack input = items.getStackInSlot(INPUT);
        if (!ItemStack.isSameItemSameComponents(input, smelting)) {
            smelting = input.copyWithCount(1);
            progress = 0;
        }
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        Optional<RecipeHolder<SmeltingRecipe>> recipe = input.isEmpty() ? Optional.empty()
                : level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, recipeInput, level);
        ItemStack result = recipe.map(holder -> holder.value().assemble(recipeInput, level.registryAccess())).orElse(ItemStack.EMPTY);
        if (result.isEmpty() || !fits(result)) {
            progress = 0;
            total = 0;
            starved = false;
            return false;
        }
        total = recipe.get().value().getCookingTime();
        starved = !ship.consumeEnergy(ENERGY_PER_TICK);
        if (starved) {
            return false;
        }
        if (++progress >= total) {
            progress = 0;
            items.extractItem(INPUT, 1, false);
            ItemStack output = items.getStackInSlot(OUTPUT);
            if (output.isEmpty()) {
                items.setStackInSlot(OUTPUT, result.copy());
            } else {
                output.grow(result.getCount());
                items.setStackInSlot(OUTPUT, output);
            }
        }
        setChanged();
        return true;
    }

    private boolean fits(ItemStack result) {
        ItemStack output = items.getStackInSlot(OUTPUT);
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void transfer(ShipState ship) {
        ItemStack output = items.getStackInSlot(OUTPUT);
        if (pushOutput && !output.isEmpty()) {
            items.setStackInSlot(OUTPUT, ship.store(output.copy()));
        }
        if (!pullInput) {
            return;
        }
        IItemHandler inventory = ship.inventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack input = items.getStackInSlot(INPUT);
            ItemStack stored = inventory.getStackInSlot(slot);
            if (stored.isEmpty() || !pullFilter.lists(stored) || !input.isEmpty() && !ItemStack.isSameItemSameComponents(input, stored)) {
                continue;
            }
            int room = input.isEmpty() ? stored.getMaxStackSize() : input.getMaxStackSize() - input.getCount();
            if (room <= 0) {
                return;
            }
            ItemStack taken = inventory.extractItem(slot, room, false);
            if (input.isEmpty()) {
                items.setStackInSlot(INPUT, taken);
            } else {
                input.grow(taken.getCount());
                items.setStackInSlot(INPUT, input);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        pullFilter.save(tag, registries);
        tag.putBoolean("PushOutput", pushOutput);
        tag.putBoolean("PullInput", pullInput);
        tag.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("Items"));
        pullFilter.load(tag, registries);
        pushOutput = tag.getBoolean("PushOutput");
        pullInput = tag.getBoolean("PullInput");
        progress = tag.getInt("Progress");
        smelting = items.getStackInSlot(INPUT).copyWithCount(1);
    }
}
