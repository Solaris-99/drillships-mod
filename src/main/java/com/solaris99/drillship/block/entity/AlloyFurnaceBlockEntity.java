package com.solaris99.drillship.block.entity;

import java.util.Optional;

import com.solaris99.drillship.alloy.AlloyInput;
import com.solaris99.drillship.alloy.AlloyRecipe;
import com.solaris99.drillship.block.AlloyFurnaceBlock;
import com.solaris99.drillship.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The alloy furnace: burns furnace fuel like a furnace, one burn-tick per tick, lighting new fuel only when it has an
 * alloy to make. Hoppers and pipes fill the inputs from above, the fuel from the sides, and take the output from below.
 */
public class AlloyFurnaceBlockEntity extends BlockEntity {
    public static final int FIRST_INPUT = 0;
    public static final int SECOND_INPUT = 1;
    public static final int FUEL = 2;
    public static final int OUTPUT = 3;

    private final ItemStackHandler items = new ItemStackHandler(4) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot != OUTPUT && (slot != FUEL || stack.getBurnTime(RecipeType.SMELTING) > 0);
        }
    };
    private int burnTime;
    private int burnDuration;
    private int progress;
    private int total;

    public AlloyFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALLOY_FURNACE.get(), pos, state);
    }

    public ItemStackHandler items() {
        return items;
    }

    public int burnTime() {
        return burnTime;
    }

    public int burnDuration() {
        return burnDuration;
    }

    public int progress() {
        return progress;
    }

    public int total() {
        return total;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AlloyFurnaceBlockEntity furnace) {
        furnace.tick(level);
        boolean lit = furnace.burnTime > 0;
        if (state.getValue(AlloyFurnaceBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(AlloyFurnaceBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    private void tick(Level level) {
        if (burnTime > 0) {
            burnTime--;
        }
        AlloyInput input = new AlloyInput(items.getStackInSlot(FIRST_INPUT), items.getStackInSlot(SECOND_INPUT));
        Optional<RecipeHolder<AlloyRecipe>> recipe = AlloyRecipe.find(level, input);
        ItemStack result = recipe.map(holder -> holder.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
        boolean canMake = !result.isEmpty() && fits(result);
        if (canMake && burnTime == 0) {
            lightFuel();
        }
        if (canMake && burnTime > 0) {
            AlloyRecipe alloy = recipe.get().value();
            total = alloy.time();
            if (++progress >= total) {
                progress = 0;
                alloy.consume(input, alloy.slotsFor(input));
                ItemStack output = items.getStackInSlot(OUTPUT);
                items.setStackInSlot(OUTPUT, output.isEmpty() ? result.copy() : output.copyWithCount(output.getCount() + result.getCount()));
                items.setStackInSlot(FIRST_INPUT, input.first());
                items.setStackInSlot(SECOND_INPUT, input.second());
            }
        } else {
            progress = canMake ? Math.max(0, progress - 2) : 0;
        }
        setChanged();
    }

    private void lightFuel() {
        ItemStack fuel = items.getStackInSlot(FUEL);
        int time = fuel.getBurnTime(RecipeType.SMELTING);
        if (time <= 0) {
            return;
        }
        burnTime = time;
        burnDuration = time;
        ItemStack remainder = fuel.getCraftingRemainingItem();
        fuel.shrink(1);
        items.setStackInSlot(FUEL, fuel.isEmpty() ? remainder : fuel);
    }

    private boolean fits(ItemStack result) {
        ItemStack output = items.getStackInSlot(OUTPUT);
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    /** What hoppers and pipes on {@code side} see: the inputs from above, the fuel from the sides, the output from below. */
    public IItemHandler itemHandler(Direction side) {
        int[] slots = side == Direction.UP ? new int[] {FIRST_INPUT, SECOND_INPUT} : side == Direction.DOWN ? new int[] {OUTPUT} : new int[] {FUEL};
        boolean output = side == Direction.DOWN;
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return slots.length;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return items.getStackInSlot(slots[slot]);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return output ? stack : items.insertItem(slots[slot], stack, simulate);
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return output ? items.extractItem(slots[slot], amount, simulate) : ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return items.getSlotLimit(slots[slot]);
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return !output && items.isItemValid(slots[slot], stack);
            }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnDuration", burnDuration);
        tag.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("Items"));
        burnTime = tag.getInt("BurnTime");
        burnDuration = tag.getInt("BurnDuration");
        progress = tag.getInt("Progress");
    }
}
