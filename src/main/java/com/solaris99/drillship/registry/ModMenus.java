package com.solaris99.drillship.registry;

import com.solaris99.drillship.Drillships;
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

    private ModMenus() {
    }
}
