package com.solaris99.drillship.registry;

import java.util.function.Supplier;

import com.solaris99.drillship.Drillships;
import com.solaris99.drillship.block.entity.EnergyIoBlockEntity;
import com.solaris99.drillship.block.entity.FurnaceModuleBlockEntity;
import com.solaris99.drillship.block.entity.InventoryIoBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Drillships.MODID);

    public static final Supplier<BlockEntityType<InventoryIoBlockEntity>> INVENTORY_IO = BLOCK_ENTITIES.register("inventory_io",
            () -> BlockEntityType.Builder.of(InventoryIoBlockEntity::new, ModBlocks.INVENTORY_IO_MODULE.get()).build(null));
    public static final Supplier<BlockEntityType<FurnaceModuleBlockEntity>> FURNACE_MODULE = BLOCK_ENTITIES.register("furnace_module",
            () -> BlockEntityType.Builder.of(FurnaceModuleBlockEntity::new, ModBlocks.FURNACE_MODULE.get()).build(null));
    public static final Supplier<BlockEntityType<EnergyIoBlockEntity>> ENERGY_IO = BLOCK_ENTITIES.register("energy_io",
            () -> BlockEntityType.Builder.of(EnergyIoBlockEntity::new, ModBlocks.ENERGY_IO_MODULE.get()).build(null));

    // Exposed from any side: only the one facing into the room can be reached.
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, INVENTORY_IO.get(), (module, side) -> module.itemHandler());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ENERGY_IO.get(), (module, side) -> module.energyStorage());
    }

    private ModBlockEntities() {
    }
}
