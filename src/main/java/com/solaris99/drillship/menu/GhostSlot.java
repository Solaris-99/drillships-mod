package com.solaris99.drillship.menu;

import com.solaris99.drillship.block.entity.ItemFilter;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A filter entry: shows a ghost copy of an item. Clicking it with an item lists that item; with an empty hand, clears it. */
class GhostSlot extends Slot {
    GhostSlot(ItemFilter filter, int index, int x, int y) {
        super(filter, index, x, y);
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    /** Handles a click if it was on a ghost slot, returning whether it was. */
    static boolean click(AbstractContainerMenu menu, int slotId, ClickType type) {
        if (slotId < 0 || slotId >= menu.slots.size() || !(menu.slots.get(slotId) instanceof GhostSlot slot)) {
            return false;
        }
        if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
            ItemStack carried = menu.getCarried();
            slot.set(carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
        }
        return true;
    }

    /** Lists an item in the first free entry, as shift-clicking it from the player's inventory does. */
    static void list(ItemFilter filter, ItemStack stack) {
        if (stack.isEmpty() || filter.lists(stack)) {
            return;
        }
        for (int i = 0; i < ItemFilter.SIZE; i++) {
            if (filter.getItem(i).isEmpty()) {
                filter.setItem(i, stack.copyWithCount(1));
                return;
            }
        }
    }
}
