package com.solaris99.drillship.menu;

import javax.annotation.Nullable;

import com.solaris99.drillship.registry.ModMenus;
import com.solaris99.drillship.ship.ShipState;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** The ship's inventory, shown one page of 54 slots at a time. The page is synced to the client as a data slot. */
public class ShipStorageMenu extends AbstractContainerMenu {
    public static final int BUTTON_PREVIOUS = 0;
    public static final int BUTTON_NEXT = 1;
    public static final int ROWS = 6;
    public static final int PAGE_SIZE = ROWS * 9;
    public static final int GRID_TOP = 18;
    public static final int INVENTORY_Y = GRID_TOP + ROWS * ShipMenus.SLOT_SIZE + 14;

    @Nullable
    private final ShipState ship;
    private final BlockPos fixture;
    private final IItemHandlerModifiable storage;
    private final DataSlot page = DataSlot.standalone();

    public static void open(ServerPlayer player, ShipState ship, BlockPos fixture) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ShipStorageMenu(id, inventory, ship, fixture, ship.inventory()),
                Component.translatable("container.drillships.storage")), buf -> {
                    buf.writeBlockPos(fixture);
                    buf.writeVarInt(ship.inventory().getSlots());
                });
    }

    public ShipStorageMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, buf.readBlockPos(), new ItemStackHandler(buf.readVarInt()));
    }

    private ShipStorageMenu(int id, Inventory inventory, @Nullable ShipState ship, BlockPos fixture, IItemHandlerModifiable storage) {
        super(ModMenus.SHIP_STORAGE.get(), id);
        this.ship = ship;
        this.fixture = fixture;
        this.storage = storage;

        PageView view = new PageView();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new SlotItemHandler(view, col + row * 9, 8 + col * ShipMenus.SLOT_SIZE, GRID_TOP + row * ShipMenus.SLOT_SIZE) {
                    @Override
                    public boolean isActive() {
                        return view.toStorage(getContainerSlot()) >= 0;
                    }
                });
            }
        }
        ShipMenus.addPlayerInventory(this::addSlot, inventory, INVENTORY_Y);
        addDataSlot(page);
    }

    public int page() {
        return page.get();
    }

    public int pageCount() {
        return Math.max(1, (storage.getSlots() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        switch (id) {
            case BUTTON_PREVIOUS -> page.set(Math.max(0, page.get() - 1));
            case BUTTON_NEXT -> page.set(Math.min(pageCount() - 1, page.get() + 1));
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < PAGE_SIZE) {
            if (!moveItemStackTo(stack, PAGE_SIZE, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Into any page, not just the visible one.
            ItemStack left = ItemHandlerHelper.insertItemStacked(storage, stack.copy(), false);
            if (left.getCount() == stack.getCount()) {
                return ItemStack.EMPTY;
            }
            stack.setCount(left.getCount());
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
        return ShipMenus.stillValid(player, ship, fixture);
    }

    /** The visible page of the ship's inventory. Slots past the end of the inventory are empty and inactive. */
    private class PageView implements IItemHandlerModifiable {
        int toStorage(int slot) {
            int index = page.get() * PAGE_SIZE + slot;
            return index < storage.getSlots() ? index : -1;
        }

        @Override
        public int getSlots() {
            return PAGE_SIZE;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            int index = toStorage(slot);
            return index >= 0 ? storage.getStackInSlot(index) : ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            int index = toStorage(slot);
            if (index >= 0) {
                storage.setStackInSlot(index, stack);
            }
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            int index = toStorage(slot);
            return index >= 0 ? storage.insertItem(index, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int index = toStorage(slot);
            return index >= 0 ? storage.extractItem(index, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            int index = toStorage(slot);
            return index >= 0 ? storage.getSlotLimit(index) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            int index = toStorage(slot);
            return index >= 0 && storage.isItemValid(index, stack);
        }
    }
}
