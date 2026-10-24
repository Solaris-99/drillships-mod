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

/** A furnace module (the furnace or the alloy furnace): its inputs and output, the push and pull toggles, and the whitelist of what it pulls. */
public class FurnaceModuleMenu extends AbstractContainerMenu {
    public static final int BUTTON_PUSH = 0;
    public static final int BUTTON_PULL = 1;
    // The last input sits here; an alloy furnace's first is one slot to its left.
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

    private final BlockPos pos;
    @Nullable
    private final FurnaceModuleBlockEntity furnace;
    private final ItemFilter filter;
    private final ContainerData data;
    private final int inputs;
    // Menu slots: the inputs, the output, the whitelist, then the player's inventory.
    private final int firstFilterSlot;
    private final int firstPlayerSlot;

    public static void open(ServerPlayer player, BlockPos pos) {
        if (player.level().getBlockEntity(pos) instanceof FurnaceModuleBlockEntity furnace) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new FurnaceModuleMenu(id, inventory, pos, furnace, furnace.items(), furnace.inputs()),
                    player.level().getBlockState(pos).getBlock().getName()), buf -> {
                        buf.writeBlockPos(pos);
                        buf.writeVarInt(furnace.inputs());
                    });
        }
    }

    public FurnaceModuleMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), null, buf.readVarInt());
    }

    private FurnaceModuleMenu(int id, Inventory inventory, BlockPos pos, @Nullable FurnaceModuleBlockEntity furnace, int inputs) {
        this(id, inventory, pos, furnace, new ItemStackHandler(inputs + 1), inputs);
    }

    private FurnaceModuleMenu(int id, Inventory inventory, BlockPos pos, @Nullable FurnaceModuleBlockEntity furnace, IItemHandlerModifiable items,
            int inputs) {
        super(ModMenus.FURNACE_MODULE.get(), id);
        this.pos = pos;
        this.furnace = furnace;
        this.inputs = inputs;
        this.firstFilterSlot = inputs + 1;
        this.firstPlayerSlot = firstFilterSlot + ItemFilter.SIZE;
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
        for (int input = 0; input < inputs; input++) {
            addSlot(new SlotItemHandler(items, input, INPUT_X - (inputs - 1 - input) * ShipMenus.SLOT_SIZE, SLOTS_Y));
        }
        addSlot(new SlotItemHandler(items, inputs, OUTPUT_X, SLOTS_Y) {
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
        if (!slot.hasItem() || index >= firstFilterSlot && index < firstPlayerSlot) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < firstFilterSlot ? moveItemStackTo(stack, firstPlayerSlot, slots.size(), true)
                : moveItemStackTo(stack, 0, inputs, false);
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
