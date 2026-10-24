package com.solaris99.drillship.menu;

import javax.annotation.Nullable;

import com.solaris99.drillship.Config;
import com.solaris99.drillship.block.entity.InventoryIoBlockEntity;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** The Inventory IO module: its mode, its rate limit and its filter. */
public class InventoryIoMenu extends AbstractContainerMenu {
    public static final int BUTTON_MODE = 0;
    /** Buttons 1 to 6 change the limit by these many items per second. */
    public static final int[] STEPS = {-64, -8, -1, 1, 8, 64};
    public static final int BUTTON_NO_LIMIT = STEPS.length + 1;
    public static final int BUTTON_FILTER_MODE = STEPS.length + 2;
    public static final int FILTER_X = 8;
    public static final int FILTER_Y = 92;
    public static final int INVENTORY_Y = 124;
    // Data values.
    public static final int MODE = 0;
    public static final int LIMIT = 1;
    public static final int CAP = 2;
    public static final int WHITELIST = 3;
    private static final int DATA_COUNT = 4;

    private final BlockPos pos;
    @Nullable
    private final InventoryIoBlockEntity module;
    private final ItemFilter filter;
    private final SplitIntData data;

    public static void open(ServerPlayer player, BlockPos pos) {
        if (player.level().getBlockEntity(pos) instanceof InventoryIoBlockEntity module) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new InventoryIoMenu(id, inventory, pos, module),
                    Component.translatable("block.drillships.inventory_io_module")), buf -> buf.writeBlockPos(pos));
        }
    }

    public InventoryIoMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), null);
    }

    private InventoryIoMenu(int id, Inventory inventory, BlockPos pos, @Nullable InventoryIoBlockEntity module) {
        super(ModMenus.INVENTORY_IO.get(), id);
        this.pos = pos;
        this.module = module;
        this.filter = module != null ? module.filter() : new ItemFilter(false, () -> {
        });
        this.data = module == null ? new SplitIntData(DATA_COUNT) : new SplitIntData(DATA_COUNT, index -> switch (index) {
            case MODE -> module.mode().ordinal();
            case LIMIT -> module.limit();
            case CAP -> Config.INVENTORY_IO_MAX_RATE.get();
            case WHITELIST -> module.filter().isWhitelist() ? 1 : 0;
            default -> 0;
        });
        for (int i = 0; i < ItemFilter.SIZE; i++) {
            addSlot(new GhostSlot(filter, i, FILTER_X + i * ShipMenus.SLOT_SIZE, FILTER_Y));
        }
        ShipMenus.addPlayerInventory(this::addSlot, inventory, INVENTORY_Y);
        addDataSlots(data);
    }

    public int get(int index) {
        return data.getInt(index);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (module == null) {
            return false;
        }
        if (id == BUTTON_MODE) {
            module.cycleMode();
        } else if (id == BUTTON_NO_LIMIT) {
            module.setLimit(0);
        } else if (id == BUTTON_FILTER_MODE) {
            module.filter().setWhitelist(!module.filter().isWhitelist());
        } else if (id >= 1 && id <= STEPS.length) {
            module.setLimit(module.limit() + STEPS[id - 1]);
        } else {
            return false;
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
        if (index >= ItemFilter.SIZE) {
            GhostSlot.list(filter, slots.get(index).getItem());
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return (module == null || !module.isRemoved()) && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64.0;
    }
}
