package com.solaris99.drillship;

import com.solaris99.drillship.client.AlloyFurnaceScreen;
import com.solaris99.drillship.client.ClientConfig;
import com.solaris99.drillship.client.ClientEvents;
import com.solaris99.drillship.client.CraftingStorageScreen;
import com.solaris99.drillship.client.DrillshipHud;
import com.solaris99.drillship.client.EnergyIoScreen;
import com.solaris99.drillship.client.FurnaceModuleScreen;
import com.solaris99.drillship.client.InventoryIoScreen;
import com.solaris99.drillship.client.ShipSectionRenderer;
import com.solaris99.drillship.client.ShipStorageScreen;
import com.solaris99.drillship.client.StatusScreen;
import com.solaris99.drillship.client.UpgradePanelScreen;
import com.solaris99.drillship.registry.ModComponents;
import com.solaris99.drillship.registry.ModEntities;
import com.solaris99.drillship.registry.ModItems;
import com.solaris99.drillship.registry.ModMenus;

import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = Drillships.MODID, dist = Dist.CLIENT)
public class DrillshipsClient {
    public DrillshipsClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        modEventBus.addListener(DrillshipsClient::registerRenderers);
        modEventBus.addListener(DrillshipsClient::registerScreens);
        modEventBus.addListener(ClientEvents::registerKeys);
        modEventBus.addListener(DrillshipsClient::registerHud);
        modEventBus.addListener(DrillshipsClient::setup);
        ClientEvents.register(NeoForge.EVENT_BUS);
    }

    // The ship locator's needle works like a lodestone compass's, pointed at the holder's last ship.
    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ModItems.SHIP_LOCATOR.get(), ResourceLocation.withDefaultNamespace("angle"),
                new CompassItemPropertyFunction((level, stack, entity) -> stack.get(ModComponents.LOCATOR_TARGET.get()))));
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DRILLSHIP.get(), ShipSectionRenderer::new);
        event.registerEntityRenderer(ModEntities.WAGON.get(), ShipSectionRenderer::new);
    }

    private static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Drillships.id("hud"), DrillshipHud::render);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.STATUS.get(), StatusScreen::new);
        event.register(ModMenus.SHIP_STORAGE.get(), ShipStorageScreen::new);
        event.register(ModMenus.UPGRADE_PANEL.get(), UpgradePanelScreen::new);
        event.register(ModMenus.CRAFTING_STORAGE.get(), CraftingStorageScreen::new);
        event.register(ModMenus.INVENTORY_IO.get(), InventoryIoScreen::new);
        event.register(ModMenus.FURNACE_MODULE.get(), FurnaceModuleScreen::new);
        event.register(ModMenus.ENERGY_IO.get(), EnergyIoScreen::new);
        event.register(ModMenus.ALLOY_FURNACE.get(), AlloyFurnaceScreen::new);
    }
}
