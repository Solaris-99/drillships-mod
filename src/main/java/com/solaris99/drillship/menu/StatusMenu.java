package com.solaris99.drillship.menu;

import javax.annotation.Nullable;

import com.solaris99.drillship.item.DrillItem;
import com.solaris99.drillship.registry.ModMenus;
import com.solaris99.drillship.ship.ShipState;
import com.solaris99.drillship.ship.ShipTravel;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** The status panel: ship readouts, warnings, drill and fuel slots, drill/auto-fuel toggles and pickup. */
public class StatusMenu extends AbstractContainerMenu {
    public static final int BUTTON_TOGGLE_DRILL = 0;
    public static final int BUTTON_TOGGLE_AUTO_FUEL = 1;
    public static final int BUTTON_PICK_UP = 2;

    public static final int ENERGY = 0;
    public static final int CAPACITY = 1;
    public static final int GENERATION = 2;
    public static final int BURN_TIME = 3;
    public static final int BURN_DURATION = 4;
    public static final int FUEL_SECONDS = 5;
    public static final int DRILL_DAMAGE = 6;
    public static final int DRILL_MAX = 7;
    public static final int SPEED_TENTHS = 8;
    public static final int X = 9;
    public static final int Y = 10;
    public static final int Z = 11;
    public static final int WARNINGS = 12;
    public static final int FLAGS = 13;
    private static final int DATA_COUNT = 14;

    public static final int FLAG_DRILL_ON = 1;
    public static final int FLAG_AUTO_FUEL = 1 << 1;

    public static final int DRILL_SLOT_X = 152;
    public static final int DRILL_SLOT_Y = 18;
    public static final int FUEL_SLOT_X = 152;
    public static final int FUEL_SLOT_Y = 52;
    public static final int INVENTORY_Y = 140;

    private static final int SHIP_SLOTS = 2;

    @Nullable
    private final ShipState ship;
    private final BlockPos fixture;
    private final SplitIntData data;

    public static void open(ServerPlayer player, ShipState ship, BlockPos fixture) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new StatusMenu(id, inventory, ship, fixture),
                Component.translatable("container.drillships.status")), buf -> buf.writeBlockPos(fixture));
    }

    public StatusMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, buf.readBlockPos(), new ItemStackHandler(1), new ItemStackHandler(1), new SplitIntData(DATA_COUNT));
    }

    private StatusMenu(int id, Inventory inventory, ShipState ship, BlockPos fixture) {
        this(id, inventory, ship, fixture, ship.drillSlot(), ship.fuelSlot(), null);
    }

    private StatusMenu(int id, Inventory inventory, @Nullable ShipState ship, BlockPos fixture,
            IItemHandlerModifiable drill, IItemHandlerModifiable fuel, @Nullable SplitIntData clientData) {
        super(ModMenus.STATUS.get(), id);
        this.ship = ship;
        this.fixture = fixture;
        this.data = clientData != null ? clientData : new SplitIntData(DATA_COUNT, index -> compute(ship, inventory.player, index));

        addSlot(new SlotItemHandler(drill, 0, DRILL_SLOT_X, DRILL_SLOT_Y));
        addSlot(new SlotItemHandler(fuel, 0, FUEL_SLOT_X, FUEL_SLOT_Y));
        ShipMenus.addPlayerInventory(this::addSlot, inventory, INVENTORY_Y);
        addDataSlots(data);
    }

    private static int compute(ShipState ship, Player player, int index) {
        ItemStack drill = ship.drill();
        return switch (index) {
            case ENERGY -> ship.energy();
            case CAPACITY -> ship.tier().energyCapacity();
            case GENERATION -> ship.tier().generation();
            case BURN_TIME -> ship.burnTime();
            case BURN_DURATION -> ship.burnDuration();
            case FUEL_SECONDS -> ship.fuelSeconds();
            case DRILL_DAMAGE -> drill.isEmpty() ? 0 : drill.getDamageValue();
            case DRILL_MAX -> drill.isEmpty() ? 0 : drill.getMaxDamage();
            case SPEED_TENTHS -> (int) Math.round(ship.speed() * 10.0);
            case X -> Mth.floor(ship.position().x);
            case Y -> Mth.floor(ship.position().y);
            case Z -> Mth.floor(ship.position().z);
            case WARNINGS -> ship.warnings(player.level().getGameTime());
            case FLAGS -> (ship.isDrillOn() ? FLAG_DRILL_ON : 0) | (ship.isAutoFuel() ? FLAG_AUTO_FUEL : 0);
            default -> 0;
        };
    }

    public int get(int index) {
        return data.getInt(index);
    }

    public boolean hasFlag(int flag) {
        return (get(FLAGS) & flag) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (ship == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        switch (id) {
            case BUTTON_TOGGLE_DRILL -> ship.setDrillOn(!ship.isDrillOn());
            case BUTTON_TOGGLE_AUTO_FUEL -> ship.setAutoFuel(!ship.isAutoFuel());
            case BUTTON_PICK_UP -> ShipTravel.pickUp(serverPlayer, ship);
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
        if (index < SHIP_SLOTS) {
            if (!moveItemStackTo(stack, SHIP_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof DrillItem) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getBurnTime(RecipeType.SMELTING) > 0) {
            if (!moveItemStackTo(stack, 1, 2, false)) {
                return ItemStack.EMPTY;
            }
        } else {
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
        return ShipMenus.stillValid(player, ship, fixture);
    }
}
