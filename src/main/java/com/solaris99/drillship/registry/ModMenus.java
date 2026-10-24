package com.solaris99.drillship.registry;

import com.solaris99.drillship.Drillships;
import com.solaris99.drillship.menu.CraftingStorageMenu;
import com.solaris99.drillship.menu.EnergyIoMenu;
import com.solaris99.drillship.menu.FurnaceModuleMenu;
import com.solaris99.drillship.menu.InventoryIoMenu;
import com.solaris99.drillship.menu.ShipStorageMenu;
import com.solaris99.drillship.menu.StatusMenu;
import com.solaris99.drillship.menu.UpgradePanelMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Drillships.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<StatusMenu>> STATUS = MENUS.register("status",
            () -> IMenuTypeExtension.create(StatusMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<ShipStorageMenu>> SHIP_STORAGE = MENUS.register("ship_storage",
            () -> IMenuTypeExtension.create(ShipStorageMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<UpgradePanelMenu>> UPGRADE_PANEL = MENUS.register("upgrade_panel",
            () -> IMenuTypeExtension.create(UpgradePanelMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<CraftingStorageMenu>> CRAFTING_STORAGE = MENUS.register("crafting_storage",
            () -> IMenuTypeExtension.create(CraftingStorageMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<InventoryIoMenu>> INVENTORY_IO = MENUS.register("inventory_io",
            () -> IMenuTypeExtension.create(InventoryIoMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<FurnaceModuleMenu>> FURNACE_MODULE = MENUS.register("furnace_module",
            () -> IMenuTypeExtension.create(FurnaceModuleMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<EnergyIoMenu>> ENERGY_IO = MENUS.register("energy_io",
            () -> IMenuTypeExtension.create(EnergyIoMenu::new));

    private ModMenus() {
    }
}
