package com.solaris99.drillship.menu;

import javax.annotation.Nullable;

import com.solaris99.drillship.block.entity.FurnaceModuleBlockEntity;
import com.solaris99.drillship.block.entity.ItemFilter;
import com.solaris99.drillship.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** The furnace module: its input and output, the push and pull toggles, and the whitelist of what it pulls. */
public class FurnaceModuleMenu extends AbstractContainerMenu {
    public static final int BUTTON_PUSH = 0;
    public static final int BUTTON_PULL = 1;
    public static final int INPUT_X = 44;
    public static final int OUTPUT_X = 98;
    public static final int SLOTS_Y = 22;
    public static final int FILTER_X = 8;
    public static final int FILTER_Y = 62;
    public static final int INVENTORY_Y = 96;
    // Data slots.
    public static final int PROGRESS = 0;
    public static final int TOTAL = 1;
    public static final int PUSH = 2;
    public static final int PULL = 3;
    public static final int STARVED = 4;
    private static final int DATA_COUNT = 5;
    // Menu slots: input, output, the whitelist, then the player's inventory.
    private static final int FIRST_FILTER_SLOT = 2;
    private static final int FIRST_PLAYER_SLOT = FIRST_FILTER_SLOT + ItemFilter.SIZE;

    private final BlockPos pos;
    @Nullable
    private final FurnaceModuleBlockEntity furnace;
    private final ItemFilter filter;
    private final ContainerData data;

    public static void open(ServerPlayer player, BlockPos pos) {
        if (player.level().getBlockEntity(pos) instanceof FurnaceModuleBlockEntity furnace) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new FurnaceModuleMenu(id, inventory, pos, furnace, furnace.items()),
                    Component.translatable("block.drillships.furnace_module")), buf -> buf.writeBlockPos(pos));
        }
    }

    public FurnaceModuleMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), null, new ItemStackHandler(2));
    }

    private FurnaceModuleMenu(int id, Inventory inventory, BlockPos pos, @Nullable FurnaceModuleBlockEntity furnace, IItemHandlerModifiable items) {
        super(ModMenus.FURNACE_MODULE.get(), id);
        this.pos = pos;
        this.furnace = furnace;
        this.filter = furnace != null ? furnace.pullFilter() : new ItemFilter(true, () -> {
        });
        this.data = furnace == null ? new SimpleContainerData(DATA_COUNT) : new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case PROGRESS -> furnace.progress();
                    case TOTAL -> furnace.total();
                    case PUSH -> furnace.pushesOutput() ? 1 : 0;
                    case PULL -> furnace.pullsInput() ? 1 : 0;
                    case STARVED -> furnace.isStarved() ? 1 : 0;
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
        addSlot(new SlotItemHandler(items, FurnaceModuleBlockEntity.INPUT, INPUT_X, SLOTS_Y));
        addSlot(new SlotItemHandler(items, FurnaceModuleBlockEntity.OUTPUT, OUTPUT_X, SLOTS_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        for (int i = 0; i < ItemFilter.SIZE; i++) {
            addSlot(new GhostSlot(filter, i, FILTER_X + i * ShipMenus.SLOT_SIZE, FILTER_Y));
        }
        ShipMenus.addPlayerInventory(this::addSlot, inventory, INVENTORY_Y);
        addDataSlots(data);
    }

    public int get(int index) {
        return data.get(index);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (furnace == null) {
            return false;
        }
        switch (id) {
            case BUTTON_PUSH -> furnace.togglePush();
            case BUTTON_PULL -> furnace.togglePull();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!GhostSlot.click(this, slotId, clickType)) {
            super.clicked(slotId, button, clickType, player);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index >= FIRST_FILTER_SLOT && index < FIRST_PLAYER_SLOT) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < FIRST_FILTER_SLOT ? moveItemStackTo(stack, FIRST_PLAYER_SLOT, slots.size(), true)
                : moveItemStackTo(stack, FurnaceModuleBlockEntity.INPUT, FurnaceModuleBlockEntity.INPUT + 1, false);
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
        return (furnace == null || !furnace.isRemoved()) && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64.0;
    }
}
