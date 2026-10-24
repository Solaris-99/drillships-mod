package com.solaris99.drillship;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.solaris99.drillship.event.CommonEvents;
import com.solaris99.drillship.network.ModNetwork;
import com.solaris99.drillship.registry.ModBlockEntities;
import com.solaris99.drillship.registry.ModBlocks;
import com.solaris99.drillship.registry.ModComponents;
import com.solaris99.drillship.registry.ModCreativeTabs;
import com.solaris99.drillship.registry.ModEntities;
import com.solaris99.drillship.registry.ModItems;
import com.solaris99.drillship.registry.ModMenus;
import com.solaris99.drillship.registry.ModSounds;
import com.solaris99.drillship.ship.ChunkLoading;
import com.solaris99.drillship.ship.ShipTicker;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Drillships.MODID)
public class Drillships {
    public static final String MODID = "drillships";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Drillships(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModComponents.COMPONENTS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        modEventBus.addListener(ModNetwork::register);
        modEventBus.addListener(ChunkLoading::registerControllers);
        modEventBus.addListener(ModBlockEntities::registerCapabilities);

        ShipTicker.register(NeoForge.EVENT_BUS);
        CommonEvents.register(NeoForge.EVENT_BUS);

        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
