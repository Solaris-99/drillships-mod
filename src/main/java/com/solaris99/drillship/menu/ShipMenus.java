package com.solaris99.drillship.menu;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.solaris99.drillship.registry.ModKeys;
import com.solaris99.drillship.ship.ShipManager;
import com.solaris99.drillship.ship.ShipState;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.Vec3;

final class ShipMenus {
    static final int SLOT_SIZE = 18;

    /** Server-side check shared by the ship menus: the ship still exists and the player is still at the fixture. */
    static boolean stillValid(Player player, @Nullable ShipState ship, BlockPos fixture) {
        if (ship == null || !(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        return ShipManager.get(serverPlayer.server).get(ship.id()) == ship
                && player.level().dimension() == ModKeys.INTERIOR
                && player.distanceToSqr(Vec3.atCenterOf(fixture)) <= 64.0;
    }

    static void addPlayerInventory(Consumer<Slot> addSlot, Inventory inventory, int top) {
        addPlayerInventory(addSlot, inventory, 8, top);
    }

    static void addPlayerInventory(Consumer<Slot> addSlot, Inventory inventory, int left, int top) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot.accept(new Slot(inventory, col + row * 9 + 9, left + col * SLOT_SIZE, top + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot.accept(new Slot(inventory, col, left + col * SLOT_SIZE, top + 58));
        }
    }

    private ShipMenus() {
    }
}
