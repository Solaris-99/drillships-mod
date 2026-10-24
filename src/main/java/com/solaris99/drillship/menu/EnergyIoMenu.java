package com.solaris99.drillship.menu;

import javax.annotation.Nullable;

import com.solaris99.drillship.Config;
import com.solaris99.drillship.block.entity.EnergyIoBlockEntity;
import com.solaris99.drillship.registry.ModMenus;
import com.solaris99.drillship.ship.ShipManager;
import com.solaris99.drillship.ship.ShipState;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** The Energy IO module: its mode, its rate limit, and the ship's buffer. */
public class EnergyIoMenu extends AbstractContainerMenu {
    public static final int BUTTON_MODE = 0;
    /** Buttons 1 to 6 change the limit by these many FE/t. */
    public static final int[] STEPS = {-1000, -100, -10, 10, 100, 1000};
    public static final int BUTTON_NO_LIMIT = STEPS.length + 1;
    // Data values.
    public static final int MODE = 0;
    public static final int LIMIT = 1;
    public static final int CAP = 2;
    public static final int ENERGY = 3;
    public static final int CAPACITY = 4;
    private static final int DATA_COUNT = 5;

    private final BlockPos pos;
    @Nullable
    private final EnergyIoBlockEntity module;
    private final SplitIntData data;

    public static void open(ServerPlayer player, BlockPos pos) {
        ShipState ship = ShipManager.get(player.server).byInteriorPos(pos);
        if (ship != null && player.level().getBlockEntity(pos) instanceof EnergyIoBlockEntity module) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new EnergyIoMenu(id, pos, module, ship),
                    Component.translatable("block.drillships.energy_io_module")), buf -> buf.writeBlockPos(pos));
        }
    }

    public EnergyIoMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, buf.readBlockPos(), null, null);
    }

    private EnergyIoMenu(int id, BlockPos pos, @Nullable EnergyIoBlockEntity module, @Nullable ShipState ship) {
        super(ModMenus.ENERGY_IO.get(), id);
        this.pos = pos;
        this.module = module;
        this.data = module == null || ship == null ? new SplitIntData(DATA_COUNT) : new SplitIntData(DATA_COUNT, index -> switch (index) {
            case MODE -> module.mode().ordinal();
            case LIMIT -> module.limit();
            case CAP -> Config.ENERGY_IO_MAX_RATE.get();
            case ENERGY -> ship.energy();
            case CAPACITY -> ship.tier().energyCapacity();
            default -> 0;
        });
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
        } else if (id >= 1 && id <= STEPS.length) {
            module.setLimit(module.limit() + STEPS[id - 1]);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return (module == null || !module.isRemoved()) && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64.0;
    }
}
