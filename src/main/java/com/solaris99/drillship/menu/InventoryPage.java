package com.solaris99.drillship.menu;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The ship's inventory in a menu, one page of 54 slots at a time. The page and the inventory's size, which changes with
 * its inventory upgrades, are synced to the client as data slots. The client holds only the page on screen, slot for
 * slot: a page's contents may arrive before its number, so they mustn't depend on it.
 */
final class InventoryPage implements IItemHandlerModifiable {
    static final int ROWS = 6;
    static final int SIZE = ROWS * 9;

    @Nullable
    private final IItemHandlerModifiable inventory;
    // The ship's inventory on the server, the page on screen on the client.
    private final IItemHandlerModifiable storage;
    private final DataSlot page = DataSlot.standalone();
    private final DataSlot size;

    /** On the server. */
    InventoryPage(IItemHandlerModifiable inventory) {
        this.inventory = inventory;
        this.storage = inventory;
        this.size = new DataSlot() {
            @Override
            public int get() {
                return inventory.getSlots();
            }

            @Override
            public void set(int value) {
            }
        };
    }

    /** On the client, with the size written by {@link #write}. */
    InventoryPage(RegistryFriendlyByteBuf buf) {
        this.inventory = null;
        this.storage = new ItemStackHandler(SIZE);
        this.size = DataSlot.standalone();
        size.set(buf.readVarInt());
    }

    static void write(RegistryFriendlyByteBuf buf, IItemHandlerModifiable inventory) {
        buf.writeVarInt(inventory.getSlots());
    }

    void addSlots(Consumer<Slot> addSlot, int left, int top) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot.accept(new SlotItemHandler(this, col + row * 9, left + col * ShipMenus.SLOT_SIZE, top + row * ShipMenus.SLOT_SIZE) {
                    @Override
                    public boolean isActive() {
                        return toStorage(getContainerSlot()) >= 0;
                    }
                });
            }
        }
    }

    void addDataSlots(Consumer<DataSlot> addDataSlot) {
        addDataSlot.accept(page);
        addDataSlot.accept(size);
    }

    int page() {
        return page.get();
    }

    int pageCount() {
        return Math.max(1, (size.get() + SIZE - 1) / SIZE);
    }

    void turn(int step) {
        page.set(Math.max(0, Math.min(pageCount() - 1, page.get() + step)));
    }

    /** Puts a stack into any page, not just the visible one. Returns what didn't fit. */
    ItemStack store(ItemStack stack) {
        return ItemHandlerHelper.insertItemStacked(storage, stack, false);
    }

    // Slots past the end of the inventory are empty and inactive.
    private int toStorage(int slot) {
        int index = page.get() * SIZE + slot;
        if (index >= size.get()) {
            return -1;
        }
        return inventory == null ? slot : index;
    }

    @Override
    public int getSlots() {
        return SIZE;
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
