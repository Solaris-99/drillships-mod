package com.solaris99.drillship.registry;

import com.solaris99.drillship.Drillships;
import com.solaris99.drillship.block.ShipDoorBlock;
import com.solaris99.drillship.block.ShipInteriorBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Drillships.MODID);

    // Interior walls. Indestructible so players can't dig out of the pocket dimension.
    public static final DeferredBlock<Block> HULL = BLOCKS.register("hull", () -> new Block(interior()));
    public static final DeferredBlock<Block> HULL_LAMP = BLOCKS.register("hull_lamp", () -> new Block(interior().lightLevel(state -> 15)));

    public static final DeferredBlock<ShipInteriorBlock> HELM = builtIn("helm", ShipInteriorBlock.Function.HELM, interior());
    public static final DeferredBlock<ShipInteriorBlock> STATUS_PANEL = builtIn("status_panel", ShipInteriorBlock.Function.STATUS,
            interior().lightLevel(state -> state.getValue(ShipInteriorBlock.ACTIVE) ? 7 : 0));
    public static final DeferredBlock<ShipInteriorBlock> GENERATOR = builtIn("generator", ShipInteriorBlock.Function.GENERATOR,
            interior().lightLevel(state -> state.getValue(ShipInteriorBlock.ACTIVE) ? 13 : 0));
    public static final DeferredBlock<ShipInteriorBlock> SHIP_STORAGE = builtIn("ship_storage", ShipInteriorBlock.Function.STORAGE, interior());
    public static final DeferredBlock<ShipInteriorBlock> SHIP_CRAFTING = builtIn("ship_crafting", ShipInteriorBlock.Function.CRAFTING, interior());
    public static final DeferredBlock<ShipDoorBlock> SHIP_DOOR = BLOCKS.register("ship_door", () -> new ShipDoorBlock(interior()));

    private static DeferredBlock<ShipInteriorBlock> builtIn(String name, ShipInteriorBlock.Function function, BlockBehaviour.Properties properties) {
        return BLOCKS.register(name, () -> new ShipInteriorBlock(function, properties));
    }

    private static BlockBehaviour.Properties interior() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .sound(SoundType.METAL)
                .strength(-1.0F, 3_600_000.0F)
                .noLootTable()
                .isValidSpawn(Blocks::never);
    }

    private ModBlocks() {
    }
}
