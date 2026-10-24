package com.solaris99.drillship.registry;

import com.solaris99.drillship.Drillships;
import com.solaris99.drillship.material.Material;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Drillships.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.drillships"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> ModItems.BASIC_DRILLSHIP.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                // One row per tier, then the upgrades, the modules, the alloy furnace and the materials.
                for (String tier : new String[] {"basic", "standard", "hardened"}) {
                    for (String part : new String[] {"drillship", "wagon", "drill", "engine", "hull", "tread", "wheel", "drill_piece", "drill_shaft"}) {
                        output.accept(BuiltInRegistries.ITEM.get(Drillships.id(tier + "_" + part)));
                    }
                }
                output.accept(ModItems.RADAR_UPGRADE.get());
                output.accept(ModItems.RADIO_UPGRADE.get());
                output.accept(ModItems.ENGINE_OVERDRIVE_UPGRADE.get());
                output.accept(ModItems.ANTIGRAVITY_UPGRADE.get());
                output.accept(ModItems.INVENTORY_UPGRADE.get());
                output.accept(ModItems.WRENCH.get());
                output.accept(ModItems.MODULE_BASE.get());
                output.accept(ModItems.CRAFTING_MODULE.get());
                output.accept(ModItems.FURNACE_MODULE.get());
                output.accept(ModItems.ALLOY_FURNACE_MODULE.get());
                output.accept(ModItems.INVENTORY_ACCESS_MODULE.get());
                output.accept(ModItems.INVENTORY_IO_MODULE.get());
                output.accept(ModItems.CRAFTING_INVENTORY_ACCESS_MODULE.get());
                output.accept(ModItems.ENERGY_IO_MODULE.get());
                output.accept(ModItems.DOOR_MODULE.get());
                output.accept(ModItems.HATCH_MODULE.get());
                output.accept(ModItems.SMALL_TESLA_TURRET.get());
                output.accept(ModItems.MEDIUM_TESLA_TURRET.get());
                output.accept(ModItems.SHIP_LOCATOR.get());
                output.accept(ModItems.ALLOY_FURNACE.get());
                for (Material material : Material.values()) {
                    ModMaterials.of(material).all().forEach(output::accept);
                }
            }).build());

    private ModCreativeTabs() {
    }
}
