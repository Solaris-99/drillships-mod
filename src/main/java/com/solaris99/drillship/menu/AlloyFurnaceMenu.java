package com.solaris99.drillship.menu;

import javax.annotation.Nullable;

import com.solaris99.drillship.block.entity.AlloyFurnaceBlockEntity;
import com.solaris99.drillship.registry.ModMenus;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** The alloy furnace, laid out like vanilla's furnace with a second input. */
public class AlloyFurnaceMenu extends AbstractContainerMenu {
    public static final int FIRST_INPUT_X = 38;
    public static final int SECOND_INPUT_X = 56;
    public static final int INPUT_Y = 17;
    public static final int FUEL_X = 47;
    public static final int FUEL_Y = 53;
    public static final int OUTPUT_X = 116;
    public static final int OUTPUT_Y = 35;
    public static final int INVENTORY_Y = 84;
    // Data slots.
    public static final int BURN_TIME = 0;
    public static final int BURN_DURATION = 1;
    public static final int PROGRESS = 2;
    public static final int TOTAL = 3;
    private static final int DATA_COUNT = 4;
    private static final int MACHINE_SLOTS = 4;

    @Nullable
    private final AlloyFurnaceBlockEntity furnace;
    private final ContainerData data;

    public static void open(ServerPlayer player, AlloyFurnaceBlockEntity furnace) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new AlloyFurnaceMenu(id, inventory, furnace, furnace.items()),
                Component.translatable("block.drillships.alloy_furnace")));
    }

    public AlloyFurnaceMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, new ItemStackHandler(MACHINE_SLOTS));
    }

    private AlloyFurnaceMenu(int id, Inventory inventory, @Nullable AlloyFurnaceBlockEntity furnace, IItemHandlerModifiable items) {
        super(ModMenus.ALLOY_FURNACE.get(), id);
        this.furnace = furnace;
        this.data = furnace == null ? new SimpleContainerData(DATA_COUNT) : new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case BURN_TIME -> furnace.burnTime();
                    case BURN_DURATION -> furnace.burnDuration();
                    case PROGRESS -> furnace.progress();
                    case TOTAL -> furnace.total();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
        addSlot(new SlotItemHandler(items, AlloyFurnaceBlockEntity.FIRST_INPUT, FIRST_INPUT_X, INPUT_Y));
        addSlot(new SlotItemHandler(items, AlloyFurnaceBlockEntity.SECOND_INPUT, SECOND_INPUT_X, INPUT_Y));
        addSlot(new SlotItemHandler(items, AlloyFurnaceBlockEntity.FUEL, FUEL_X, FUEL_Y));
        addSlot(new SlotItemHandler(items, AlloyFurnaceBlockEntity.OUTPUT, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        ShipMenus.addPlayerInventory(this::addSlot, inventory, INVENTORY_Y);
        addDataSlots(data);
    }

    public int get(int index) {
        return data.get(index);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved;
        if (index < MACHINE_SLOTS) {
            moved = moveItemStackTo(stack, MACHINE_SLOTS, slots.size(), true);
        } else if (stack.getBurnTime(RecipeType.SMELTING) > 0) {
            moved = moveItemStackTo(stack, AlloyFurnaceBlockEntity.FUEL, AlloyFurnaceBlockEntity.FUEL + 1, false)
                    || moveItemStackTo(stack, AlloyFurnaceBlockEntity.FIRST_INPUT, AlloyFurnaceBlockEntity.SECOND_INPUT + 1, false);
        } else {
            moved = moveItemStackTo(stack, AlloyFurnaceBlockEntity.FIRST_INPUT, AlloyFurnaceBlockEntity.SECOND_INPUT + 1, false);
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return furnace == null || !furnace.isRemoved() && player.distanceToSqr(Vec3.atCenterOf(furnace.getBlockPos())) <= 64.0;
    }
}
