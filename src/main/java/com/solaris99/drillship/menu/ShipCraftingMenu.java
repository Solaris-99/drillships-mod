package com.solaris99.drillship.menu;

import com.solaris99.drillship.registry.ModBlocks;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;

/** A vanilla crafting table menu that stays open for the ship's built-in crafting table. */
public class ShipCraftingMenu extends CraftingMenu {
    private final ContainerLevelAccess access;

    public ShipCraftingMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(id, inventory, access);
        this.access = access;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.SHIP_CRAFTING.get());
    }
}
