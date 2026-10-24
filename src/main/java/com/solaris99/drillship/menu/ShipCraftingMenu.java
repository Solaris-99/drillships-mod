package com.solaris99.drillship.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.level.block.Block;

/** A vanilla crafting table menu that stays open for the ship's crafting tables: the built-in one and the crafting module. */
public class ShipCraftingMenu extends CraftingMenu {
    private final ContainerLevelAccess access;
    private final Block block;

    public ShipCraftingMenu(int id, Inventory inventory, ContainerLevelAccess access, Block block) {
        super(id, inventory, access);
        this.access = access;
        this.block = block;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, block);
    }
}
