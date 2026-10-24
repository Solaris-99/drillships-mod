package com.solaris99.drillship.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.solaris99.drillship.alloy.AlloyInput;
import com.solaris99.drillship.alloy.AlloyRecipe;
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
 * The furnace modules: the furnace (one input, smelting recipes) and the alloy furnace (two inputs, alloy recipes).
 * They run at their recipes' speed on FE from the ship's buffer instead of fuel, drawing only while they work. Once a
 * second they can push their output into the ship's inventory and pull input from it, but only items on their
 * whitelist (an empty one pulls nothing).
 */
public class FurnaceModuleBlockEntity extends ModuleBlockEntity {
    public static final int INPUT = 0;
    public static final int ENERGY_PER_TICK = 20;
    private static final int TRANSFER_TICKS = 20;

    private final boolean alloy;
    private final int inputs;
    private final ItemStackHandler items;
    private final ItemFilter pullFilter = new ItemFilter(true, this::setChanged);
    private boolean pushOutput;
    private boolean pullInput;
    private int progress;
    private int total;
    // Something to make, but not enough energy in the buffer.
    private boolean starved;
    private List<ItemStack> making = List.of();

    private record Job(ItemStack result, int time, Runnable consume) {
    }

    public static FurnaceModuleBlockEntity smelting(BlockPos pos, BlockState state) {
        return new FurnaceModuleBlockEntity(pos, state, false);
    }

    public static FurnaceModuleBlockEntity alloying(BlockPos pos, BlockState state) {
        return new FurnaceModuleBlockEntity(pos, state, true);
    }

    private FurnaceModuleBlockEntity(BlockPos pos, BlockState state, boolean alloy) {
        super(alloy ? ModBlockEntities.ALLOY_FURNACE_MODULE.get() : ModBlockEntities.FURNACE_MODULE.get(), pos, state);
        this.alloy = alloy;
        this.inputs = alloy ? 2 : 1;
        this.items = new ItemStackHandler(inputs + 1) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return slot < inputs;
            }
        };
    }

    public ItemStackHandler items() {
        return items;
    }

    public int inputs() {
        return inputs;
    }

    public int outputSlot() {
        return inputs;
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
        List<ItemStack> contents = new ArrayList<>();
        for (int slot = 0; slot < items.getSlots(); slot++) {
            contents.add(items.getStackInSlot(slot));
        }
        return contents;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FurnaceModuleBlockEntity furnace) {
        ShipState ship = furnace.ship();
        if (ship == null) {
            return;
        }
        if (level.getGameTime() % TRANSFER_TICKS == 0) {
            furnace.transfer(ship);
        }
        boolean lit = furnace.work((ServerLevel) level, ship);
        if (state.getValue(FurnaceModuleBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(FurnaceModuleBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    // Returns whether it worked this tick.
    private boolean work(ServerLevel level, ShipState ship) {
        List<ItemStack> inputsNow = new ArrayList<>();
        for (int slot = 0; slot < inputs; slot++) {
            inputsNow.add(items.getStackInSlot(slot).copyWithCount(1));
        }
        if (!sameItems(inputsNow, making)) {
            making = inputsNow;
            progress = 0;
        }
        Optional<Job> job = alloy ? alloyJob(level) : smeltingJob(level);
        if (job.isEmpty() || !fits(job.get().result())) {
            progress = 0;
            total = 0;
            starved = false;
            return false;
        }
        total = job.get().time();
        starved = !ship.consumeEnergy(ENERGY_PER_TICK);
        if (starved) {
            return false;
        }
        if (++progress >= total) {
            progress = 0;
            job.get().consume().run();
            ItemStack output = items.getStackInSlot(outputSlot());
            ItemStack result = job.get().result();
            items.setStackInSlot(outputSlot(), output.isEmpty() ? result.copy() : output.copyWithCount(output.getCount() + result.getCount()));
        }
        setChanged();
        return true;
    }

    private Optional<Job> smeltingJob(ServerLevel level) {
        ItemStack input = items.getStackInSlot(INPUT);
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        Optional<RecipeHolder<SmeltingRecipe>> recipe = input.isEmpty() ? Optional.empty()
                : level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, recipeInput, level);
        return recipe.map(holder -> new Job(holder.value().assemble(recipeInput, level.registryAccess()), holder.value().getCookingTime(),
                () -> items.extractItem(INPUT, 1, false)));
    }

    private Optional<Job> alloyJob(ServerLevel level) {
        AlloyInput input = new AlloyInput(items.getStackInSlot(0), items.getStackInSlot(1));
        return AlloyRecipe.find(level, input).map(holder -> new Job(holder.value().assemble(input, level.registryAccess()), holder.value().time(), () -> {
            holder.value().consume(input, holder.value().slotsFor(input));
            items.setStackInSlot(0, input.first());
            items.setStackInSlot(1, input.second());
        }));
    }

    private static boolean sameItems(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.isSameItemSameComponents(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean fits(ItemStack result) {
        ItemStack output = items.getStackInSlot(outputSlot());
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void transfer(ShipState ship) {
        ItemStack output = items.getStackInSlot(outputSlot());
        if (pushOutput && !output.isEmpty()) {
            items.setStackInSlot(outputSlot(), ship.store(output.copy()));
        }
        if (!pullInput) {
            return;
        }
        IItemHandler inventory = ship.inventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stored = inventory.getStackInSlot(slot);
            int input = stored.isEmpty() || !pullFilter.lists(stored) ? -1 : inputFor(stored);
            if (input < 0) {
                continue;
            }
            ItemStack held = items.getStackInSlot(input);
            int room = held.isEmpty() ? stored.getMaxStackSize() : held.getMaxStackSize() - held.getCount();
            if (room <= 0) {
                continue;
            }
            ItemStack taken = inventory.extractItem(slot, room, false);
            items.setStackInSlot(input, held.isEmpty() ? taken : held.copyWithCount(held.getCount() + taken.getCount()));
        }
    }

    // The input slot already holding this item, else an empty one: an item never takes up two inputs.
    private int inputFor(ItemStack stack) {
        int empty = -1;
        for (int slot = 0; slot < inputs; slot++) {
            ItemStack held = items.getStackInSlot(slot);
            if (ItemStack.isSameItemSameComponents(held, stack)) {
                return slot;
            }
            if (held.isEmpty() && empty < 0) {
                empty = slot;
            }
        }
        return empty;
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
        CompoundTag itemsTag = tag.getCompound("Items");
        itemsTag.remove("Size");
        items.deserializeNBT(registries, itemsTag);
        pullFilter.load(tag, registries);
        pushOutput = tag.getBoolean("PushOutput");
        pullInput = tag.getBoolean("PullInput");
        progress = tag.getInt("Progress");
        List<ItemStack> loaded = new ArrayList<>();
        for (int slot = 0; slot < inputs; slot++) {
            loaded.add(items.getStackInSlot(slot).copyWithCount(1));
        }
        making = loaded;
    }
}
