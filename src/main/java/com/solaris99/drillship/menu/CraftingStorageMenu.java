package com.solaris99.drillship.menu;

import java.util.Optional;

import javax.annotation.Nullable;

import com.solaris99.drillship.registry.ModMenus;
import com.solaris99.drillship.ship.ShipState;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * The crafting & inventory access module: a crafting grid beside a page of the ship's inventory (see InventoryPage),
 * with the player's inventory below. Shift-clicking moves between the player's inventory and the ship's; what's crafted
 * goes to the player, or the ship when the player is full.
 */
public class CraftingStorageMenu extends AbstractContainerMenu {
    public static final int BUTTON_PREVIOUS = 0;
    public static final int BUTTON_NEXT = 1;
    public static final int WIDTH = 290;
    public static final int GRID_X = 8;
    public static final int GRID_Y = 30;
    public static final int RESULT_X = 94;
    public static final int RESULT_Y = 48;
    public static final int PAGE_X = 120;
    public static final int PAGE_Y = 18;
    public static final int INVENTORY_X = (WIDTH - 9 * ShipMenus.SLOT_SIZE) / 2;
    public static final int INVENTORY_Y = PAGE_Y + InventoryPage.ROWS * ShipMenus.SLOT_SIZE + 14;
    // Menu slots: the result, the grid, the ship's page, then the player's inventory.
    private static final int FIRST_GRID_SLOT = 1;
    private static final int FIRST_PAGE_SLOT = FIRST_GRID_SLOT + 9;
    private static final int FIRST_PLAYER_SLOT = FIRST_PAGE_SLOT + InventoryPage.SIZE;

    @Nullable
    private final ShipState ship;
    private final BlockPos fixture;
    private final Player player;
    private final InventoryPage page;
    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();

    public static void open(ServerPlayer player, ShipState ship, BlockPos fixture) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new CraftingStorageMenu(id, inventory, ship, fixture, new InventoryPage(ship.inventory())),
                Component.translatable("block.drillships.crafting_inventory_access_module")), buf -> {
                    buf.writeBlockPos(fixture);
                    InventoryPage.write(buf, ship.inventory());
                });
    }

    public CraftingStorageMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, buf.readBlockPos(), new InventoryPage(buf));
    }

    private CraftingStorageMenu(int id, Inventory inventory, @Nullable ShipState ship, BlockPos fixture, InventoryPage page) {
        super(ModMenus.CRAFTING_STORAGE.get(), id);
        this.ship = ship;
        this.fixture = fixture;
        this.player = inventory.player;
        this.page = page;
        addSlot(new ResultSlot(player, craftSlots, resultSlots, 0, RESULT_X, RESULT_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(craftSlots, col + row * 3, GRID_X + col * ShipMenus.SLOT_SIZE, GRID_Y + row * ShipMenus.SLOT_SIZE));
            }
        }
        page.addSlots(this::addSlot, PAGE_X, PAGE_Y);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * ShipMenus.SLOT_SIZE, INVENTORY_Y + row * ShipMenus.SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * ShipMenus.SLOT_SIZE, INVENTORY_Y + 58));
        }
        page.addDataSlots(this::addDataSlot);
    }

    public int page() {
        return page.page();
    }

    public int pageCount() {
        return page.pageCount();
    }

    // As vanilla's crafting table does it.
    @Override
    public void slotsChanged(Container container) {
        if (container != craftSlots || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        CraftingInput input = craftSlots.asCraftInput();
        ItemStack result = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> recipe = serverPlayer.server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input,
                serverPlayer.level(), (RecipeHolder<CraftingRecipe>) null);
        if (recipe.isPresent() && resultSlots.setRecipeUsed(serverPlayer.level(), serverPlayer, recipe.get())) {
            ItemStack assembled = recipe.get().value().assemble(input, serverPlayer.level().registryAccess());
            if (assembled.isItemEnabled(serverPlayer.level().enabledFeatures())) {
                result = assembled;
            }
        }
        resultSlots.setItem(0, result);
        setRemoteSlot(0, result);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), 0, result));
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
        if (index == 0) {
            stack.getItem().onCraftedBy(stack, player.level(), player);
            if (!toPlayerThenShip(stack, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
        } else if (index < FIRST_PAGE_SLOT) {
            if (!toPlayerThenShip(stack, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < FIRST_PLAYER_SLOT) {
            if (!moveItemStackTo(stack, FIRST_PLAYER_SLOT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!toShip(stack)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        if (index == 0) {
            player.drop(stack, false);
        }
        return original;
    }

    // What doesn't fit in the player's inventory goes to the ship's. Returns whether anything moved.
    private boolean toPlayerThenShip(ItemStack stack, boolean hotbarFirst) {
        boolean moved = moveItemStackTo(stack, FIRST_PLAYER_SLOT, slots.size(), hotbarFirst);
        return (!stack.isEmpty() && toShip(stack)) || moved;
    }

    // Into any page of the ship's inventory. Returns whether anything moved.
    private boolean toShip(ItemStack stack) {
        ItemStack left = page.store(stack.copy());
        boolean moved = left.getCount() < stack.getCount();
        stack.setCount(left.getCount());
        return moved;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, craftSlots);
    }

    @Override
    public boolean stillValid(Player player) {
        return ShipMenus.stillValid(player, ship, fixture);
    }
}
