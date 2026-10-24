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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The ship's inventory, one page at a time (see InventoryPage). Opened by the storage access and the inventory access module. */
public class ShipStorageMenu extends AbstractContainerMenu {
    public static final int BUTTON_PREVIOUS = 0;
    public static final int BUTTON_NEXT = 1;
    public static final int ROWS = InventoryPage.ROWS;
    public static final int PAGE_SIZE = InventoryPage.SIZE;
    public static final int GRID_TOP = 18;
    public static final int INVENTORY_Y = GRID_TOP + ROWS * ShipMenus.SLOT_SIZE + 14;

    @Nullable
    private final ShipState ship;
    private final BlockPos fixture;
    private final InventoryPage page;

    public static void open(ServerPlayer player, ShipState ship, BlockPos fixture) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ShipStorageMenu(id, inventory, ship, fixture, new InventoryPage(ship.inventory())),
                Component.translatable("container.drillships.storage")), buf -> {
                    buf.writeBlockPos(fixture);
                    InventoryPage.write(buf, ship.inventory());
                });
    }

    public ShipStorageMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, buf.readBlockPos(), new InventoryPage(buf));
    }

    private ShipStorageMenu(int id, Inventory inventory, @Nullable ShipState ship, BlockPos fixture, InventoryPage page) {
        super(ModMenus.SHIP_STORAGE.get(), id);
        this.ship = ship;
        this.fixture = fixture;
        this.page = page;
        page.addSlots(this::addSlot, 8, GRID_TOP);
        ShipMenus.addPlayerInventory(this::addSlot, inventory, INVENTORY_Y);
        page.addDataSlots(this::addDataSlot);
    }

    public int page() {
        return page.page();
    }

    public int pageCount() {
        return page.pageCount();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        switch (id) {
            case BUTTON_PREVIOUS -> page.turn(-1);
            case BUTTON_NEXT -> page.turn(1);
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
            ItemStack left = page.store(stack.copy());
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
}
