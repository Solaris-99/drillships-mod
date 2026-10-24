package com.solaris99.drillship.registry;

import com.solaris99.drillship.Drillships;
import com.solaris99.drillship.block.CockpitBlock;
import com.solaris99.drillship.block.FurnaceModuleBlock;
import com.solaris99.drillship.block.HullPanelBlock;
import com.solaris99.drillship.block.ModuleBlock;
import com.solaris99.drillship.block.ModuleDoorBlock;
import com.solaris99.drillship.block.ShipDoorBlock;
import com.solaris99.drillship.block.ShipInteriorBlock;
import com.solaris99.drillship.block.TeslaTurretBlock;
import com.solaris99.drillship.ship.TeslaTurret;
import com.solaris99.drillship.block.UpgradePanelBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Drillships.MODID);

    // Interior walls. Indestructible so players can't dig out of the pocket dimension.
    // The inside of the hull, by where it is in a room (see Interiors): plated walls, a tread-plate floor, a ceiling of
    // vented panels, beams in the corners, a kick plate along the bottom of the walls and pipes along their top.
    public static final DeferredBlock<HullPanelBlock> HULL = BLOCKS.register("hull", () -> new HullPanelBlock(interior()));
    public static final DeferredBlock<Block> HULL_FLOOR = BLOCKS.register("hull_floor", () -> new Block(interior()));
    public static final DeferredBlock<Block> HULL_CEILING = BLOCKS.register("hull_ceiling", () -> new Block(interior()));
    public static final DeferredBlock<Block> HULL_FRAME = BLOCKS.register("hull_frame", () -> new Block(interior()));
    public static final DeferredBlock<Block> HULL_SKIRT = BLOCKS.register("hull_skirt", () -> new Block(interior()));
    public static final DeferredBlock<Block> HULL_CONDUIT = BLOCKS.register("hull_conduit", () -> new Block(interior()));
    public static final DeferredBlock<Block> HULL_LAMP = BLOCKS.register("hull_lamp", () -> new Block(interior().lightLevel(state -> 15)));
    // Lines the passages between sections: the inside of the bellows that join them outside.
    public static final DeferredBlock<Block> BELLOWS = BLOCKS.register("ship_bellows", () -> new Block(interior().sound(SoundType.WOOL)));

    public static final DeferredBlock<ShipInteriorBlock> HELM = builtIn("helm", ShipInteriorBlock.Function.HELM, interior());
    public static final DeferredBlock<ShipInteriorBlock> STATUS_PANEL = builtIn("status_panel", ShipInteriorBlock.Function.STATUS,
            interior().lightLevel(state -> state.getValue(ShipInteriorBlock.ACTIVE) ? 7 : 0));
    public static final DeferredBlock<ShipInteriorBlock> GENERATOR = builtIn("generator", ShipInteriorBlock.Function.GENERATOR,
            interior().lightLevel(state -> state.getValue(ShipInteriorBlock.ACTIVE) ? 13 : 0));
    public static final DeferredBlock<ShipInteriorBlock> SHIP_STORAGE = builtIn("ship_storage", ShipInteriorBlock.Function.STORAGE, interior());
    public static final DeferredBlock<ShipInteriorBlock> SHIP_CRAFTING = builtIn("ship_crafting", ShipInteriorBlock.Function.CRAFTING, interior());
    public static final DeferredBlock<ShipDoorBlock> SHIP_DOOR = BLOCKS.register("ship_door", () -> new ShipDoorBlock(interior()));

    // Upgrade panels in the drillhead's left wall, one per tier, four upgrades each.
    public static final DeferredBlock<UpgradePanelBlock> UPGRADE_PANEL = BLOCKS.register("upgrade_panel",
            () -> new UpgradePanelBlock(interior().lightLevel(state -> state.getValue(UpgradePanelBlock.STATION) > 0 ? 6 : 3)));

    static {
        BLOCKS.addAlias(Drillships.id("module_panel"), Drillships.id("upgrade_panel"));
    }

    // Modules, installed in the hull in place of shell blocks (see ShipModules).
    public static final DeferredBlock<ModuleBlock> CRAFTING_MODULE = module("crafting_module", ModuleBlock.Kind.CRAFTING);
    public static final DeferredBlock<ModuleBlock> CRAFTING_INVENTORY_ACCESS_MODULE = module("crafting_inventory_access_module",
            ModuleBlock.Kind.CRAFTING_INVENTORY_ACCESS);
    public static final DeferredBlock<ModuleBlock> INVENTORY_ACCESS_MODULE = module("inventory_access_module", ModuleBlock.Kind.INVENTORY_ACCESS);
    public static final DeferredBlock<ModuleBlock> INVENTORY_IO_MODULE = module("inventory_io_module", ModuleBlock.Kind.INVENTORY_IO);
    public static final DeferredBlock<FurnaceModuleBlock> FURNACE_MODULE = BLOCKS.register("furnace_module",
            () -> new FurnaceModuleBlock(interior().lightLevel(state -> state.getValue(FurnaceModuleBlock.LIT) ? 13 : 0)));
    public static final DeferredBlock<ModuleBlock> ENERGY_IO_MODULE = module("energy_io_module", ModuleBlock.Kind.ENERGY_IO);
    public static final DeferredBlock<ModuleDoorBlock> DOOR_MODULE = BLOCKS.register("door_module", () -> new ModuleDoorBlock(interior()));
    public static final DeferredBlock<ModuleBlock> HATCH_MODULE = module("hatch_module", ModuleBlock.Kind.HATCH);
    public static final DeferredBlock<TeslaTurretBlock> SMALL_TESLA_TURRET = BLOCKS.register("small_tesla_turret",
            () -> new TeslaTurretBlock(TeslaTurret.SMALL, interior()));
    public static final DeferredBlock<TeslaTurretBlock> MEDIUM_TESLA_TURRET = BLOCKS.register("medium_tesla_turret",
            () -> new TeslaTurretBlock(TeslaTurret.MEDIUM, interior()));

    // The cockpit (see InteriorLayout.cockpit): the driver's chair, the desk across the front, the consoles beside the
    // chair, screens on the front wall, and the glazed bulkhead that sets it apart from the rest of the cabin.
    public static final DeferredBlock<CockpitBlock> COCKPIT_CHAIR = BLOCKS.register("cockpit_chair",
            () -> new CockpitBlock(CockpitBlock.Kind.CHAIR, interior().noOcclusion()));
    public static final DeferredBlock<CockpitBlock> COCKPIT_DESK = BLOCKS.register("cockpit_desk",
            () -> new CockpitBlock(CockpitBlock.Kind.DESK, interior().noOcclusion().lightLevel(state -> 4)));
    public static final DeferredBlock<CockpitBlock> COCKPIT_CONSOLE = BLOCKS.register("cockpit_console",
            () -> new CockpitBlock(CockpitBlock.Kind.CONSOLE, interior().noOcclusion().lightLevel(state -> 3)));
    public static final DeferredBlock<ShipInteriorBlock> COCKPIT_SCREEN = builtIn("cockpit_screen", ShipInteriorBlock.Function.STATUS,
            interior().lightLevel(state -> 5));
    public static final DeferredBlock<TransparentBlock> COCKPIT_GLASS = BLOCKS.register("cockpit_glass", () -> new TransparentBlock(interior()
            .sound(SoundType.GLASS).noOcclusion().isViewBlocking(ModBlocks::never).isSuffocating(ModBlocks::never)
            .isRedstoneConductor(ModBlocks::never)));
    public static final DeferredBlock<Block> BULKHEAD = BLOCKS.register("bulkhead", () -> new Block(interior()));
    public static final DeferredBlock<ShipInteriorBlock> COCKPIT_TELEMETRY = builtIn("cockpit_telemetry", ShipInteriorBlock.Function.STATUS,
            interior().lightLevel(state -> 5));
    public static final DeferredBlock<Block> COCKPIT_ROOF = BLOCKS.register("cockpit_roof", () -> new Block(interior()));
    public static final DeferredBlock<Block> COCKPIT_LAMP = BLOCKS.register("cockpit_lamp", () -> new Block(interior().lightLevel(state -> 15)));

    /** Any of the hull's own blocks: what a room's shell is made of. */
    public static boolean isShell(BlockState state) {
        return state.is(HULL.get()) || state.is(HULL_FLOOR.get()) || state.is(HULL_CEILING.get()) || state.is(HULL_FRAME.get())
                || state.is(HULL_SKIRT.get()) || state.is(HULL_CONDUIT.get());
    }

    /** Anything the ship builds for itself. None of these can be obtained, so a player never placed one. */
    public static boolean isBuiltIn(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(Drillships.MODID);
    }

    private static boolean never(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    private static DeferredBlock<ModuleBlock> module(String name, ModuleBlock.Kind kind) {
        return BLOCKS.register(name, () -> new ModuleBlock(kind, interior()));
    }

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
