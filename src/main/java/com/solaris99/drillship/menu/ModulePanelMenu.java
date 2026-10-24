package com.solaris99.drillship.menu;

import javax.annotation.Nullable;

import com.solaris99.drillship.item.ModuleItem;
import com.solaris99.drillship.registry.ModMenus;
import com.solaris99.drillship.ship.RadioStation;
import com.solaris99.drillship.ship.ShipModule;
import com.solaris99.drillship.ship.ShipState;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;

/**
 * One module panel's four bays, laid out like the panel itself, plus what the ship's modules add up to: the radar's
 * tier from every radar module in every panel and what it looks for, and the radio, which can be tuned from any panel.
 */
public class ModulePanelMenu extends AbstractContainerMenu {
    public static final int BUTTON_PREVIOUS_STATION = 0;
    public static final int BUTTON_NEXT_STATION = 1;
    public static final int BUTTON_PREVIOUS_TARGET = 2;
    public static final int BUTTON_NEXT_TARGET = 3;
    public static final int BAYS_X = 26;
    public static final int BAYS_Y = 24;
    public static final int INVENTORY_Y = 84;
    // Data slots.
    public static final int RADARS = 0;
    public static final int RADIOS = 1;
    public static final int STATION = 2;
    public static final int TARGET = 3;

    @Nullable
    private final ShipState ship;
    private final BlockPos fixture;
    private final int panel;
    private final ContainerData data;

    public static void open(ServerPlayer player, ShipState ship, BlockPos fixture, int panel) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ModulePanelMenu(id, inventory, ship, fixture, panel,
                new RangedWrapper(ship.modules(), panel * ShipState.MODULES_PER_PANEL, (panel + 1) * ShipState.MODULES_PER_PANEL)),
                Component.translatable("container.drillships.module_panel", panel + 1, ship.tier().modulePanels())), buf -> {
                    buf.writeBlockPos(fixture);
                    buf.writeVarInt(panel);
                });
    }

    public ModulePanelMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, null, buf.readBlockPos(), buf.readVarInt(), new ItemStackHandler(ShipState.MODULES_PER_PANEL) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return stack.getItem() instanceof ModuleItem;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        });
    }

    private ModulePanelMenu(int id, Inventory inventory, @Nullable ShipState ship, BlockPos fixture, int panel, IItemHandlerModifiable bays) {
        super(ModMenus.MODULE_PANEL.get(), id);
        this.ship = ship;
        this.fixture = fixture;
        this.panel = panel;
        this.data = ship == null ? new SimpleContainerData(4) : new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case RADARS -> ship.moduleCount(ShipModule.RADAR);
                    case RADIOS -> ship.moduleCount(ShipModule.RADIO);
                    case STATION -> ship.radioStation();
                    case TARGET -> ship.radarTarget().ordinal();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 4;
            }
        };
        for (int bay = 0; bay < ShipState.MODULES_PER_PANEL; bay++) {
            addSlot(new SlotItemHandler(bays, bay, BAYS_X + (bay % 2) * ShipMenus.SLOT_SIZE, BAYS_Y + (bay / 2) * ShipMenus.SLOT_SIZE));
        }
        ShipMenus.addPlayerInventory(this::addSlot, inventory, INVENTORY_Y);
        addDataSlots(data);
    }

    public int panel() {
        return panel;
    }

    public int get(int index) {
        return data.get(index);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (ship != null && (id == BUTTON_PREVIOUS_TARGET || id == BUTTON_NEXT_TARGET) && ship.radarTier() > 0) {
            ship.cycleRadarTarget(id == BUTTON_NEXT_TARGET ? 1 : -1);
            player.level().playSound(null, fixture, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5F, 1.6F);
            return true;
        }
        if (ship == null || !ship.hasModule(ShipModule.RADIO)) {
            return false;
        }
        RadioStation station = RadioStation.byId(ship.radioStation());
        switch (id) {
            case BUTTON_PREVIOUS_STATION -> ship.setRadioStation(station.previous().ordinal());
            case BUTTON_NEXT_STATION -> ship.setRadioStation(station.next().ordinal());
            default -> {
                return false;
            }
        }
        player.level().playSound(null, fixture, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5F, 1.4F);
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
        int bays = ShipState.MODULES_PER_PANEL;
        if (index < bays ? !moveItemStackTo(stack, bays, slots.size(), true) : !moveItemStackTo(stack, 0, bays, false)) {
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
