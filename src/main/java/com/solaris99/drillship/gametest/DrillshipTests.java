package com.solaris99.drillship.gametest;

import java.util.List;

import com.solaris99.drillship.Config;
import com.solaris99.drillship.Drillships;
import com.solaris99.drillship.block.ModuleBlock;
import com.solaris99.drillship.block.UpgradePanelBlock;
import com.solaris99.drillship.block.entity.AlloyFurnaceBlockEntity;
import com.solaris99.drillship.block.entity.EnergyIoBlockEntity;
import com.solaris99.drillship.block.entity.FurnaceModuleBlockEntity;
import com.solaris99.drillship.block.entity.InventoryIoBlockEntity;
import com.solaris99.drillship.block.entity.IoMode;
import com.solaris99.drillship.block.entity.ItemFilter;
import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.entity.WagonEntity;
import com.solaris99.drillship.item.DrillshipItem;
import com.solaris99.drillship.item.ModuleItem;
import com.solaris99.drillship.item.WagonItem;
import com.solaris99.drillship.material.Material;
import com.solaris99.drillship.registry.ModBlocks;
import com.solaris99.drillship.registry.ModComponents;
import com.solaris99.drillship.registry.ModItems;
import com.solaris99.drillship.registry.ModKeys;
import com.solaris99.drillship.registry.ModMaterials;
import com.solaris99.drillship.ship.InteriorLayout;
import com.solaris99.drillship.ship.RadarOre;
import com.solaris99.drillship.ship.ShipManager;
import com.solaris99.drillship.ship.ShipModules;
import com.solaris99.drillship.ship.ShipRadar;
import com.solaris99.drillship.ship.ShipShape;
import com.solaris99.drillship.ship.ShipState;
import com.solaris99.drillship.ship.ShipTier;
import com.solaris99.drillship.ship.ShipTravel;
import com.solaris99.drillship.ship.ShipUpgrade;
import com.solaris99.drillship.ship.ShipWagons;
import com.solaris99.drillship.ship.TeslaTurret;
import com.solaris99.drillship.ship.TurretTargets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;



/** Run with {@code ./gradlew runGameTestServer}. Each test uses an empty 13x10x28 area; the ship faces +Z. */
@GameTestHolder(Drillships.MODID)
@PrefixGameTestTemplate(false)
public class DrillshipTests {
    private static final String TEMPLATE = "empty";
    // 15x10x48, for tests that need room for wagons.
    private static final String LONG_TEMPLATE = "empty_long";
    // Block-center aligned, so the basic hull spans x 4..9 and z 2..9 (drill 9..10) exactly on block boundaries.
    private static final Vec3 ORIGIN = new Vec3(6.5, 1, 5.5);

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void drillsThroughStone(GameTestHelper helper) {
        floor(helper);
        wall(helper, Blocks.STONE, 11, 13);
        ShipState ship = place(helper, ORIGIN);
        ship.setDrillOn(true);
        drive(helper, ship);

        helper.succeedWhen(() -> {
            for (int x = 4; x <= 8; x++) {
                for (int y = 1; y <= 5; y++) {
                    for (int z = 11; z <= 13; z++) {
                        helper.assertBlockNotPresent(Blocks.STONE, x, y, z);
                    }
                }
            }
            helper.assertTrue(contains(ship, Items.COBBLESTONE), "Mined cobblestone should be in the ship's inventory");
            helper.assertTrue(relative(helper, entity(helper, ship).position()).z > 12, "Ship should have moved past the wall");
            remove(helper, ship);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void fallsAndLands(GameTestHelper helper) {
        floor(helper);
        ShipState ship = place(helper, ORIGIN.add(0, 4, 0));
        helper.succeedWhen(() -> {
            Vec3 position = relative(helper, entity(helper, ship).position());
            helper.assertTrue(Math.abs(position.y - 1.0) < 1.0E-3, "Ship should rest on the floor, but is at y=" + position.y);
            remove(helper, ship);
        });
    }

    // Physics: out in the open a tilted ship falls and levels out onto its tracks; in terrain it keeps its angle.

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void levelsOutOnTheSurface(GameTestHelper helper) {
        floor(helper);
        ShipState ship = place(helper, ORIGIN.add(0, 3, 0));
        DrillshipEntity entity = entity(helper, ship);
        entity.moveTo(entity.getX(), entity.getY(), entity.getZ(), 0.0F, -30.0F);
        helper.succeedWhen(() -> {
            double y = relative(helper, entity.position()).y;
            helper.assertTrue(Math.abs(entity.getXRot()) < 1.0E-3 && Math.abs(y - 1.0) < 1.0E-3,
                    "The ship should rest level on the floor, but is at y=" + y + " pitched " + entity.getXRot());
            remove(helper, ship);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void keepsItsAngleWhileDrilling(GameTestHelper helper) {
        floor(helper);
        wall(helper, Blocks.STONE, 10, 13);
        ShipState ship = place(helper, ORIGIN);
        DrillshipEntity entity = entity(helper, ship);
        entity.moveTo(entity.getX(), entity.getY(), entity.getZ(), 0.0F, -20.0F);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(Math.abs(entity.getXRot() + 20.0F) < 1.0E-3, "A drill in the rock should hold the angle, but pitch is " + entity.getXRot());
            remove(helper, ship);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void steersTowardTheLook(GameTestHelper helper) {
        floor(helper);
        ShipState ship = place(helper, ORIGIN);
        ServerPlayer driver = drive(helper, ship);
        DrillshipEntity entity = entity(helper, ship);
        entity.setInput(0.0F);
        driver.setYRot(-120.0F);
        helper.runAfterDelay(50, () -> {
            // The look was clamped to 60 degrees off the heading, after the ship's first 2.7-degree turn toward it.
            helper.assertTrue(Math.abs(Mth.wrapDegrees(driver.getYRot() + 62.7F)) < 1.0E-3, "The driver can't look more than 60 degrees off the heading, but looks at " + driver.getYRot());
            helper.assertTrue(Math.abs(Mth.wrapDegrees(entity.getYRot() - driver.getYRot())) < 1.0E-3, "The ship should turn to face the look, but is at " + entity.getYRot());
            helper.assertTrue(Math.abs(entity.getXRot()) < 1.0E-3, "A level look on flat ground keeps the ship level");
            remove(helper, ship);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void wrongTierMinesWithoutDrops(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(6, 2, 10, Blocks.DIAMOND_ORE);
        ShipState ship = place(helper, ORIGIN);
        ship.setDrillOn(true);
        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(Blocks.DIAMOND_ORE, 6, 2, 10);
            helper.assertFalse(contains(ship, Items.DIAMOND), "A basic drill must not get diamonds");
            remove(helper, ship);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void bedrockStopsTheShip(GameTestHelper helper) {
        floor(helper);
        wall(helper, Blocks.BEDROCK, 11, 11);
        ShipState ship = place(helper, ORIGIN);
        ship.setDrillOn(true);
        drive(helper, ship);
        helper.runAfterDelay(60, () -> {
            // It may creep forward until the bedrock reaches the drill face (origin z 7.0), but no further.
            helper.assertTrue(relative(helper, entity(helper, ship).position()).z <= 7.0, "Ship should be stopped by bedrock");
            helper.assertTrue((ship.warnings(helper.getLevel().getGameTime()) & ShipState.WARN_BLOCKED) != 0, "Blocked warning should be on");
            remove(helper, ship);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void interiorIsBuilt(GameTestHelper helper) {
        floor(helper);
        ShipState ship = place(helper, ORIGIN);
        ServerLevel interior = helper.getLevel().getServer().getLevel(ModKeys.INTERIOR);
        InteriorLayout layout = ship.layout();
        assertBlock(helper, interior, layout.helm(), ModBlocks.HELM.get());
        assertBlock(helper, interior, layout.statusPanel(), ModBlocks.STATUS_PANEL.get());
        assertBlock(helper, interior, layout.generator(), ModBlocks.GENERATOR.get());
        assertBlock(helper, interior, layout.storage(), ModBlocks.SHIP_STORAGE.get());
        assertBlock(helper, interior, layout.crafting(), ModBlocks.SHIP_CRAFTING.get());
        assertBlock(helper, interior, layout.doorLower(0), ModBlocks.SHIP_DOOR.get());
        assertBlock(helper, interior, layout.origin(), ModBlocks.HULL_FLOOR.get());
        assertBlock(helper, interior, layout.chair(), ModBlocks.COCKPIT_CHAIR.get());
        for (InteriorLayout.CockpitPart part : layout.cockpit()) {
            helper.assertTrue(ModBlocks.isBuiltIn(interior.getBlockState(part.pos())), "Cockpit part missing at " + part.pos());
        }
        helper.assertTrue(layout.upgradePanels().size() == ShipTier.BASIC.upgradePanels(), "The basic drillhead has one upgrade panel");
        for (BlockPos panel : layout.upgradePanels()) {
            assertBlock(helper, interior, panel, ModBlocks.UPGRADE_PANEL.get());
        }
        helper.assertTrue(interior.getBlockState(BlockPos.containing(layout.entryPoint())).isAir(), "Entry point should be free");
        helper.assertTrue(interior.getBlockState(BlockPos.containing(layout.helmPoint())).isAir(), "Where the driver gets up should be free");
        remove(helper, ship);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void radarUpgradesAddUp(GameTestHelper helper) {
        floor(helper);
        ShipState ship = place(helper, ORIGIN);
        IItemHandlerModifiable upgrades = ship.upgrades();
        helper.assertTrue(upgrades.getSlots() == ShipState.UPGRADES_PER_PANEL, "One panel of four bays");
        helper.assertTrue(!upgrades.isItemValid(0, new ItemStack(Items.COAL)), "Only upgrades go into a panel");
        upgrades.setStackInSlot(0, ModItems.upgrade(ShipUpgrade.RADAR));
        helper.assertTrue(ship.radarTier() == 1, "One radar upgrade: tier 1");
        ship.cycleRadarTarget(-1);
        helper.assertTrue(ship.radarTarget() == RadarOre.OTHER_METAL, "Tier 1 cycles through metals only");
        upgrades.setStackInSlot(1, ModItems.upgrade(ShipUpgrade.RADAR));
        upgrades.setStackInSlot(3, ModItems.upgrade(ShipUpgrade.RADIO));
        helper.assertTrue(ship.upgradeCount(ShipUpgrade.RADAR) == 2 && ship.radarTier() == 2, "Two radar upgrades: tier 2");
        ship.cycleRadarTarget(1);
        helper.assertTrue(ship.radarTarget() == RadarOre.COAL, "Tier 2 finds every ore");
        upgrades.setStackInSlot(1, ItemStack.EMPTY);
        helper.assertTrue(ship.radarTarget() == RadarOre.IRON, "Back at tier 1, an ore it can't find falls back to iron");
        upgrades.setStackInSlot(1, ModItems.upgrade(ShipUpgrade.RADAR));
        helper.assertTrue(ShipRadar.tier(5) == ShipRadar.MAX_TIER, "The tier stops at 3");
        helper.assertTrue(ShipRadar.oreRange(3) > ShipRadar.oreRange(2) && ShipRadar.oreRange(2) > ShipRadar.oreRange(1), "Each tier reaches further");
        ship.setRadioStation(1);
        helper.assertTrue(ship.radioStation() == 1, "The radio tunes");
        upgrades.setStackInSlot(3, ItemStack.EMPTY);
        helper.assertTrue(ship.radioStation() == 0, "Taking the radio out switches it off");
        helper.succeedWhen(() -> {
            helper.assertTrue(entity(helper, ship).upgrades() == ShipUpgrade.RADAR.bit(), "The exterior shows the radar");
            ServerLevel interior = helper.getLevel().getServer().getLevel(ModKeys.INTERIOR);
            BlockState panel = interior.getBlockState(ship.layout().upgradePanels().get(0));
            helper.assertTrue(panel.getValue(UpgradePanelBlock.BAYS.get(1)) == UpgradePanelBlock.Bay.RADAR, "The panel shows its upgrades");
            remove(helper, ship);
        });
    }

    @GameTest(template = TEMPLATE)
    public static void engineOverdrivesStackUpToThree(GameTestHelper helper) {
        floor(helper);
        ShipState ship = place(helper, ORIGIN);
        double base = ShipTier.BASIC.topSpeed();
        for (int bay = 0; bay < ShipState.UPGRADES_PER_PANEL; bay++) {
            ship.upgrades().setStackInSlot(bay, ModItems.upgrade(ShipUpgrade.ENGINE_OVERDRIVE));
        }
        helper.assertTrue(ship.topSpeed() == base * 2.5, "Three overdrives give 250% top speed, and a fourth changes nothing");
        helper.assertTrue(Math.abs(ship.accelerationFactor() - 1.3) < 1.0E-9, "Three overdrives give 130% acceleration");
        helper.assertTrue(ship.moveEnergy() == Config.MOVE_ENERGY.get() + 30, "Each overdrive adds 10 FE/t to moving");
        ship.upgrades().setStackInSlot(3, ItemStack.EMPTY);
        ship.upgrades().setStackInSlot(2, ItemStack.EMPTY);
        helper.assertTrue(ship.topSpeed() == base * 2.0 && ship.moveEnergy() == Config.MOVE_ENERGY.get() + 20, "Two overdrives: 200%, +20 FE/t");
        ship.upgrades().setStackInSlot(2, ModItems.upgrade(ShipUpgrade.ENGINE_OVERDRIVE));
        drive(helper, ship);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(ship.speed() > base + 1, "An overdriven ship outruns the tier's top speed, but went " + ship.speed());
            remove(helper, ship);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void antigravityHoldsTheShipUp(GameTestHelper helper) {
        floor(helper);
        Vec3 aloft = ORIGIN.add(0, 3, 0);
        ShipState ship = place(helper, aloft);
        ship.upgrades().setStackInSlot(0, ModItems.upgrade(ShipUpgrade.ANTIGRAVITY));
        ship.upgrades().setStackInSlot(1, ModItems.upgrade(ShipUpgrade.ANTIGRAVITY));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(ship.isFloating(), "A powered antigravity engine turns gravity off");
            helper.assertTrue(Math.abs(relative(helper, entity(helper, ship).position()).y - aloft.y) < 1.0E-3, "The ship should hover where it was");
            // The generator makes 240 FE/t and only one of the two engines draws its 200 FE/t.
            int perTick = ShipTier.BASIC.generation() - ShipUpgrade.ANTIGRAVITY_ENERGY;
            helper.assertTrue(ship.energy() > 0 && ship.energy() % perTick == 0, "One engine draws 200 FE/t, but energy is " + ship.energy());
            ship.upgrades().setStackInSlot(0, ItemStack.EMPTY);
            ship.upgrades().setStackInSlot(1, ItemStack.EMPTY);
            helper.succeedWhen(() -> {
                helper.assertTrue(Math.abs(relative(helper, entity(helper, ship).position()).y - ORIGIN.y) < 1.0E-3, "Without the engine the ship falls");
                remove(helper, ship);
            });
        });
    }

    @GameTest(template = TEMPLATE)
    public static void antigravityWithoutEnergyFalls(GameTestHelper helper) {
        floor(helper);
        ShipState ship = place(helper, ORIGIN.add(0, 3, 0));
        ship.fuelSlot().setStackInSlot(0, ItemStack.EMPTY);
        ship.upgrades().setStackInSlot(0, ModItems.upgrade(ShipUpgrade.ANTIGRAVITY));
        helper.succeedWhen(() -> {
            helper.assertTrue(!ship.isFloating(), "An antigravity engine without energy doesn't hold the ship up");
            helper.assertTrue(Math.abs(relative(helper, entity(helper, ship).position()).y - ORIGIN.y) < 1.0E-3, "The ship should land on the floor");
            remove(helper, ship);
        });
    }

    @GameTest(template = TEMPLATE)
    public static void inventoryUpgradesAddSlots(GameTestHelper helper) {
        floor(helper);
        ShipState ship = place(helper, ORIGIN);
        IItemHandlerModifiable upgrades = ship.upgrades();
        IItemHandlerModifiable inventory = ship.inventory();
        int base = ShipTier.BASIC.inventorySlots(), added = ShipUpgrade.INVENTORY_SLOTS;
        inventory.setStackInSlot(base - 1, new ItemStack(Items.COBBLESTONE));
        upgrades.setStackInSlot(0, ModItems.upgrade(ShipUpgrade.INVENTORY));
        upgrades.setStackInSlot(1, ModItems.upgrade(ShipUpgrade.INVENTORY));
        helper.assertTrue(inventory.getSlots() == base + 2 * added, "Each inventory upgrade adds 50 slots");
        helper.assertTrue(inventory.getStackInSlot(base - 1).is(Items.COBBLESTONE), "Growing keeps what the inventory holds");
        inventory.setStackInSlot(base + added, new ItemStack(Items.DIRT));
        helper.assertTrue(upgrades.extractItem(0, 1, true).isEmpty() && upgrades.extractItem(1, 1, true).isEmpty(),
                "No inventory upgrade comes out while the last 50 slots hold items");
        inventory.setStackInSlot(base + added, ItemStack.EMPTY);
        inventory.setStackInSlot(base, new ItemStack(Items.DIRT));
        helper.assertTrue(!upgrades.extractItem(1, 1, false).isEmpty(), "One comes out once the last 50 slots are empty");
        helper.assertTrue(inventory.getSlots() == base + added && inventory.getStackInSlot(base).is(Items.DIRT), "Shrinking keeps the slots left");
        helper.assertTrue(upgrades.extractItem(0, 1, true).isEmpty(), "The other one holds items now");
        remove(helper, ship);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void exteriorRespawnsAfterRemoval(GameTestHelper helper) {
        floor(helper);
        ShipState ship = place(helper, ORIGIN);
        entity(helper, ship).discard();
        helper.succeedWhen(() -> {
            entity(helper, ship);
            remove(helper, ship);
        });
    }

    @GameTest(template = TEMPLATE)
    public static void pickUpReturnsItemWithDrill(GameTestHelper helper) {
        floor(helper);
        ShipState ship = place(helper, ORIGIN);
        ship.fuelSlot().setStackInSlot(0, ItemStack.EMPTY);
        ship.upgrades().setStackInSlot(2, ModItems.upgrade(ShipUpgrade.RADAR));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ShipTravel.enterShip(player, ship);
        ShipTravel.pickUp(player, ship);

        helper.assertTrue(ShipManager.get(helper.getLevel().getServer()).get(ship.id()) == null, "Ship should be gone");
        ItemStack item = ItemStack.EMPTY;
        boolean radar = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.BASIC_DRILLSHIP.get())) {
                item = player.getInventory().getItem(i);
            }
            radar |= player.getInventory().getItem(i).is(ModItems.RADAR_UPGRADE.get());
        }
        helper.assertTrue(!item.isEmpty(), "Player should get the drillship item back");
        helper.assertTrue(radar, "Player should get the upgrades back");
        helper.assertTrue(item.get(ModComponents.INSTALLED_DRILL.get()).copyOne().is(ModItems.BASIC_DRILL.get()), "The drill should be stored in the item");
        helper.succeed();
    }

    // Modules

    @GameTest(template = TEMPLATE)
    public static void modulesGoInAndComeOut(GameTestHelper helper) {
        floor(helper);
        ShipState ship = place(helper, ORIGIN);
        ServerLevel interior = interior(helper);
        InteriorLayout.Room room = ship.layout().room(0);
        BlockPos wall = freeWall(helper, ship);
        BlockState shell = interior.getBlockState(wall);
        helper.assertTrue(ShipModules.install(interior, wall, ModItems.FURNACE_MODULE.get()) == null, "A furnace module goes into a wall");
        helper.assertTrue(interior.getBlockState(wall).is(ModBlocks.FURNACE_MODULE.get()), "The wall block became the module");
        helper.assertTrue(interior.getBlockState(wall).getValue(ModuleBlock.FACING) == Direction.EAST, "It faces into the room, away from the min-X wall");
        FurnaceModuleBlockEntity furnace = (FurnaceModuleBlockEntity) interior.getBlockEntity(wall);
        furnace.items().setStackInSlot(FurnaceModuleBlockEntity.INPUT, new ItemStack(Items.RAW_IRON, 3));
        BlockPos floor = room.origin().offset(room.sizeX() / 2, 0, room.sizeZ() / 2);
        helper.assertTrue(ShipModules.install(interior, floor, ModItems.CRAFTING_MODULE.get()) != null, "Wall modules don't go into the floor");
        helper.assertTrue(ShipModules.install(interior, room.origin().offset(0, 2, 0), ModItems.CRAFTING_MODULE.get()) != null,
                "Modules don't go into the corner beams");
        helper.assertTrue(ShipModules.install(interior, floor, ModItems.HATCH_MODULE.get()) == null, "A hatch goes into the floor");
        helper.assertTrue(interior.getBlockState(floor).getValue(ModuleBlock.FACING) == Direction.UP, "A floor hatch faces up into the room");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ship.fuelSlot().setStackInSlot(0, ItemStack.EMPTY);
        ShipTravel.pickUp(player, ship);
        helper.assertTrue(ShipManager.get(helper.getLevel().getServer()).get(ship.id()) == ship, "A ship with modules can't be picked up");
        helper.assertTrue(ShipModules.remove(interior, wall, player), "A wrench takes the furnace out");
        helper.assertTrue(interior.getBlockState(wall) == shell, "The hull is back as it was");
        helper.assertTrue(player.getInventory().countItem(ModItems.FURNACE_MODULE.get()) == 1 && player.getInventory().countItem(Items.RAW_IRON) == 3,
                "The player gets the module and what it held");
        helper.assertTrue(ShipModules.remove(interior, floor, player) && interior.getBlockState(floor).is(ModBlocks.HULL_FLOOR.get()),
                "The hatch comes out too");
        helper.assertTrue(!ShipModules.hasModules(interior, room), "No modules are left");
        remove(helper, ship);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void inventoryIoFiltersAndLimits(GameTestHelper helper) {
        floor(helper);
        ShipState ship = place(helper, ORIGIN);
        ServerLevel interior = interior(helper);
        BlockPos wall = freeWall(helper, ship);
        ShipModules.install(interior, wall, ModItems.INVENTORY_IO_MODULE.get());
        InventoryIoBlockEntity module = (InventoryIoBlockEntity) interior.getBlockEntity(wall);
        IItemHandler access = interior.getCapability(Capabilities.ItemHandler.BLOCK, wall, null);
        helper.assertTrue(access != null, "The module exposes the ship's inventory");
        helper.assertTrue(ItemHandlerHelper.insertItem(access, new ItemStack(Items.STONE, 5), false).isEmpty(), "An empty blacklist lets anything in");
        helper.assertTrue(contains(ship, Items.STONE), "What goes in lands in the ship's inventory");
        ItemFilter filter = module.filter();
        filter.setItem(0, new ItemStack(Items.DIRT));
        filter.setWhitelist(true);
        helper.assertTrue(ItemHandlerHelper.insertItem(access, new ItemStack(Items.COBBLESTONE), false).getCount() == 1,
                "A whitelist keeps out what it doesn't list");
        helper.assertTrue(ItemHandlerHelper.insertItem(access, new ItemStack(Items.DIRT), false).isEmpty(), "A whitelist lets in what it lists");
        boolean stoneShown = false;
        for (int slot = 0; slot < access.getSlots(); slot++) {
            stoneShown |= access.getStackInSlot(slot).is(Items.STONE) || access.extractItem(slot, 64, true).is(Items.STONE);
        }
        helper.assertTrue(!stoneShown, "Items the filter holds back can't be seen or taken");
        filter.setItem(0, ItemStack.EMPTY);
        filter.setWhitelist(false);
        helper.runAfterDelay(20, () -> {
            module.setLimit(10);
            helper.assertTrue(ItemHandlerHelper.insertItem(access, new ItemStack(Items.GRAVEL, 64), false).getCount() == 54,
                    "It lets no more than its limit in each second");
            helper.assertTrue(!ItemHandlerHelper.insertItem(access, new ItemStack(Items.SAND, 1), false).isEmpty(), "The limit holds within the second");
            module.cycleMode();
            helper.assertTrue(module.mode() == IoMode.INPUT, "From both, the mode goes to input");
            boolean extracted = false;
            for (int slot = 0; slot < access.getSlots(); slot++) {
                extracted |= !access.extractItem(slot, 1, true).isEmpty();
            }
            helper.assertTrue(!extracted, "In input mode nothing comes out");
            remove(helper, ship);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void energyIoKeepsItsLimit(GameTestHelper helper) {
        floor(helper);
        ShipState ship = place(helper, ORIGIN);
        ServerLevel interior = interior(helper);
        BlockPos wall = freeWall(helper, ship);
        ShipModules.install(interior, wall, ModItems.ENERGY_IO_MODULE.get());
        EnergyIoBlockEntity module = (EnergyIoBlockEntity) interior.getBlockEntity(wall);
        IEnergyStorage energy = interior.getCapability(Capabilities.EnergyStorage.BLOCK, wall, null);
        helper.assertTrue(energy != null && energy.canReceive() && !energy.canExtract(), "It starts in input mode");
        module.setLimit(100);
        helper.assertTrue(energy.receiveEnergy(1000, false) == 100, "It takes no more than its limit");
        helper.assertTrue(energy.receiveEnergy(1000, false) == 0, "The limit is per tick");
        module.cycleMode();
        helper.assertTrue(energy.receiveEnergy(1000, false) == 0, "In output mode nothing goes in");
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(energy.extractEnergy(50, false) == 50, "In output mode FE comes out");
            module.cycleMode();
            helper.assertTrue(energy.canReceive() && energy.canExtract() && energy.receiveEnergy(30, false) == 30,
                    "Both ways, with a limit of its own for each");
            module.setLimit(0);
            helper.assertTrue(module.rate() == Integer.MAX_VALUE, "0 means no limit");
            remove(helper, ship);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void furnaceModuleSmeltsOnEnergy(GameTestHelper helper) {
        floor(helper);
        ShipState ship = place(helper, ORIGIN);
        ServerLevel interior = interior(helper);
        BlockPos wall = freeWall(helper, ship);
        ShipModules.install(interior, wall, ModItems.FURNACE_MODULE.get());
        FurnaceModuleBlockEntity furnace = (FurnaceModuleBlockEntity) interior.getBlockEntity(wall);
        ship.inventory().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 2));
        furnace.pullFilter().setItem(0, new ItemStack(Items.RAW_IRON));
        furnace.togglePull();
        helper.runAfterDelay(240, () -> {
            helper.assertTrue(furnace.items().getStackInSlot(furnace.outputSlot()).is(Items.IRON_INGOT),
                    "It pulled the raw iron from the ship and smelted it on the ship's energy");
            helper.assertTrue(ship.energyUse() == FurnaceModuleBlockEntity.ENERGY_PER_TICK,
                    "The status panel's use should be the furnace's 20 FE/t, but is " + ship.energyUse());
            furnace.togglePush();
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(contains(ship, Items.IRON_INGOT) && furnace.items().getStackInSlot(furnace.outputSlot()).isEmpty(),
                        "It pushes its output into the ship's inventory");
                remove(helper, ship);
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE)
    public static void doorModuleLeadsOutside(GameTestHelper helper) {
        floor(helper);
        ShipState ship = place(helper, ORIGIN);
        ServerLevel interior = interior(helper);
        BlockPos wall = freeWall(helper, ship);
        helper.assertTrue(ShipModules.install(interior, wall, ModItems.DOOR_MODULE.get()) == null, "A door module goes into two wall blocks");
        helper.assertTrue(interior.getBlockState(wall.above()).is(ModBlocks.DOOR_MODULE.get()), "Its upper half is above");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ShipTravel.exitThroughModule(player, ship, wall);
        Vec3 feet = relative(helper, player.position());
        double across = Math.abs(feet.x - ORIGIN.x);
        helper.assertTrue(player.level() == helper.getLevel() && Math.abs(across - (ShipShape.drillhead(ShipTier.BASIC).halfWidth + 0.8)) < 1.0E-3
                && Math.abs(feet.y - ORIGIN.y) < 1.0E-3, "The player should step out beside the door module, but is at " + feet);
        remove(helper, ship);
        helper.succeed();
    }

    // Tesla turrets

    @GameTest(template = TEMPLATE)
    public static void teslaTurretZapsHostilesThroughArmor(GameTestHelper helper) {
        floor(helper);
        ShipState ship = place(helper, ORIGIN);
        installTurret(helper, ship, ModItems.SMALL_TESLA_TURRET.get());
        Husk husk = target(helper, EntityType.HUSK, 11.0);
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            husk.setItemSlot(slot, new ItemStack(switch (slot) {
                case HEAD -> Items.DIAMOND_HELMET;
                case CHEST -> Items.DIAMOND_CHESTPLATE;
                case LEGS -> Items.DIAMOND_LEGGINGS;
                default -> Items.DIAMOND_BOOTS;
            }));
        }
        Cow cow = target(helper, EntityType.COW, 2.0);
        // Once its interior is ticking, a few volleys in.
        helper.succeedWhen(() -> {
            float taken = husk.getMaxHealth() - husk.getHealth();
            helper.assertTrue(taken >= 8.0F && taken % 4.0F == 0.0F, "Full 4-damage hits through diamond armor, but it took " + taken);
            helper.assertTrue(cow.getHealth() == cow.getMaxHealth(), "Hostiles only: the cow is left alone");
            remove(helper, ship);
        });
    }

    @GameTest(template = TEMPLATE)
    public static void teslaTurretSettings(GameTestHelper helper) {
        floor(helper);
        ShipState ship = place(helper, ORIGIN);
        ServerLevel interior = interior(helper);
        BlockPos mount = installTurret(helper, ship, ModItems.MEDIUM_TESLA_TURRET.get());
        helper.assertTrue(ShipModules.install(interior, mount.east(), ModItems.SMALL_TESLA_TURRET.get()) != null, "One turret per section");
        helper.assertTrue(ship.turret(0) == TeslaTurret.MEDIUM, "The ship knows its turret");
        ship.setTurretsOn(false);
        Cow cow = target(helper, EntityType.COW, 2.0);
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(cow.getHealth() == cow.getMaxHealth(), "Switched off, the turret doesn't fire");
            ship.setTurretsOn(true);
            ship.setTurretTargets(TurretTargets.EVERYTHING);
            helper.succeedWhen(() -> {
                helper.assertTrue(cow.getHealth() < cow.getMaxHealth(), "Targeting everything, it zaps the cow");
                ServerPlayer player = helper.makeMockServerPlayerInLevel();
                helper.assertTrue(ShipModules.remove(interior, mount, player) && ship.turret(0) == null
                        && interior.getBlockState(mount).is(ModBlocks.HULL_CEILING.get()), "The wrench takes the turret out");
                remove(helper, ship);
            });
        });
    }

    // Materials and alloys

    @GameTest(template = TEMPLATE)
    public static void materialsAreOursWhenNoOtherModHasThem(GameTestHelper helper) {
        for (Material material : Material.values()) {
            helper.assertTrue(material.isOurs(), material.id() + " should be ours in a pack without other material mods");
        }
        Item ourTin = ModMaterials.of(Material.TIN).ingot().get();
        helper.assertTrue(Material.TIN.isOurs(tag -> List.of(BuiltInRegistries.ITEM.wrapAsHolder(ourTin))), "Our own ingot in the tag doesn't count");
        helper.assertTrue(!Material.TIN.isOurs(tag -> List.of(BuiltInRegistries.ITEM.wrapAsHolder(ourTin), BuiltInRegistries.ITEM.wrapAsHolder(Items.IRON_INGOT))),
                "Another mod's ingot in the tag means it's theirs");
        RecipeManager recipes = helper.getLevel().getRecipeManager();
        for (String id : new String[] {"bronze_ingot_from_alloying", "brass_ingot_from_alloying", "steel_ingot_from_alloying", "alloy_furnace",
                "tin_block", "tin_ingot_from_smelting_raw"}) {
            helper.assertTrue(recipes.byKey(Drillships.id(id)).isPresent(), "The recipe " + id + " should be on");
        }
        helper.assertTrue(helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE).containsKey(Drillships.id("ore_tin_middle")),
                "Tin ore should generate");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void alloyFurnaceMakesBronze(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.ALLOY_FURNACE.get());
        AlloyFurnaceBlockEntity furnace = (AlloyFurnaceBlockEntity) helper.getBlockEntity(pos);
        furnace.items().setStackInSlot(AlloyFurnaceBlockEntity.FIRST_INPUT, new ItemStack(ModMaterials.of(Material.TIN).ingot().get()));
        furnace.items().setStackInSlot(AlloyFurnaceBlockEntity.SECOND_INPUT, new ItemStack(Items.COPPER_INGOT, 3));
        furnace.items().setStackInSlot(AlloyFurnaceBlockEntity.FUEL, new ItemStack(Items.COAL));
        helper.runAfterDelay(210, () -> {
            ItemStack output = furnace.items().getStackInSlot(AlloyFurnaceBlockEntity.OUTPUT);
            helper.assertTrue(output.is(ModMaterials.of(Material.BRONZE).ingot().get()) && output.getCount() == 4,
                    "3 copper and 1 tin, in either slot, make 4 bronze, but made " + output);
            helper.assertTrue(furnace.items().getStackInSlot(AlloyFurnaceBlockEntity.FIRST_INPUT).isEmpty()
                    && furnace.items().getStackInSlot(AlloyFurnaceBlockEntity.SECOND_INPUT).isEmpty(), "The inputs are used up");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void alloyFurnaceModuleMakesSteel(GameTestHelper helper) {
        floor(helper);
        ShipState ship = place(helper, ORIGIN);
        ServerLevel interior = interior(helper);
        BlockPos wall = freeWall(helper, ship);
        helper.assertTrue(ShipModules.install(interior, wall, ModItems.ALLOY_FURNACE_MODULE.get()) == null, "The alloy furnace module goes into a wall");
        FurnaceModuleBlockEntity furnace = (FurnaceModuleBlockEntity) interior.getBlockEntity(wall);
        helper.assertTrue(furnace.inputs() == 2, "It has two inputs");
        furnace.items().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        furnace.items().setStackInSlot(1, new ItemStack(Items.CHARCOAL, 2));
        helper.runAfterDelay(210, () -> {
            helper.assertTrue(furnace.items().getStackInSlot(furnace.outputSlot()).is(ModMaterials.of(Material.STEEL).ingot().get()),
                    "1 iron and 2 coals make steel on the ship's energy");
            remove(helper, ship);
            helper.succeed();
        });
    }

    // Wagons

    private static final Vec3 LONG_ORIGIN = new Vec3(7.5, 1, 16.5);

    @GameTest(template = LONG_TEMPLATE, timeoutTicks = 200)
    public static void wagonFollowsDrillhead(GameTestHelper helper) {
        floor(helper, 15, 48);
        ShipState ship = place(helper, LONG_ORIGIN);
        attach(helper, ship);
        assertWagonRoom(helper, ship, 1);
        drive(helper, ship);

        helper.runAfterDelay(40, () -> {
            DrillshipEntity drillhead = entity(helper, ship);
            WagonEntity wagon = ShipWagons.findEntity(helper.getLevel().getServer(), ship, 1);
            helper.assertTrue(wagon != null, "The wagon's exterior should exist");
            helper.assertTrue(drillhead.getZ() - LONG_ORIGIN.z - helper.absoluteVec(Vec3.ZERO).z > 5, "The drillhead should have moved forward");
            // Drillhead half length + coupling gap + wagon half length.
            ShipTier tier = ShipTier.BASIC;
            double expected = tier.drillheadExteriorLength() / 2.0 + tier.coupling().gap() + tier.wagonExteriorLength() / 2.0;
            double gap = drillhead.getZ() - wagon.getZ();
            helper.assertTrue(Math.abs(gap - expected) < 0.6, "The wagon should trail " + expected + " blocks behind, but trails " + gap);
            helper.assertTrue(Math.abs(wagon.getX() - drillhead.getX()) < 0.01 && Math.abs(wagon.getYRot()) < 0.01,
                    "The wagon should be lined up behind the drillhead");
            remove(helper, ship);
            helper.succeed();
        });
    }

    @GameTest(template = LONG_TEMPLATE, timeoutTicks = 100)
    public static void wagonLimitsTurningInPlace(GameTestHelper helper) {
        floor(helper, 15, 48);
        ShipState ship = place(helper, LONG_ORIGIN);
        attach(helper, ship);
        ServerPlayer driver = drive(helper, ship);
        DrillshipEntity drillhead = entity(helper, ship);
        drillhead.setInput(0.0F);
        driver.setYRot(-90.0F);
        helper.runAfterDelay(60, () -> {
            WagonEntity wagon = ShipWagons.findEntity(helper.getLevel().getServer(), ship, 1);
            helper.assertTrue(wagon != null, "The wagon's exterior should exist");
            float bend = Math.abs(Mth.wrapDegrees(drillhead.getYRot() - wagon.getYRot()));
            float limit = ShipTier.BASIC.coupling().maxBend();
            helper.assertTrue(bend > limit - 3 && bend <= limit + 0.5F, "Turning in place should stop at " + limit + " degrees, but bent " + bend);
            remove(helper, ship);
            helper.succeed();
        });
    }

    @GameTest(template = LONG_TEMPLATE)
    public static void blockedWagonIsNotAttached(GameTestHelper helper) {
        floor(helper, 15, 48);
        for (int x = 3; x <= 11; x++) {
            for (int y = 1; y <= 5; y++) {
                helper.setBlock(x, y, 8, Blocks.STONE);
            }
        }
        ShipState ship = place(helper, LONG_ORIGIN);
        helper.assertTrue(ShipWagons.attach(helper.getLevel().getServer(), ship, WagonItem.create(ShipTier.BASIC)) != null,
                "Attaching into a wall should fail");
        helper.assertTrue(ship.wagonCount() == 0, "No wagon should be attached");
        remove(helper, ship);
        helper.succeed();
    }

    @GameTest(template = LONG_TEMPLATE)
    public static void detachingReturnsWagonAndSealsHull(GameTestHelper helper) {
        floor(helper, 15, 48);
        ShipState ship = place(helper, LONG_ORIGIN);
        attach(helper, ship);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(ShipWagons.detachLast(player, ship) == null, "Detaching an empty wagon should work");
        helper.assertTrue(ship.wagonCount() == 0, "The wagon should be gone");
        ServerLevel interior = helper.getLevel().getServer().getLevel(ModKeys.INTERIOR);
        for (BlockPos pos : ship.layout().passage(1)) {
            if (ship.layout().room(0).contains(pos)) {
                helper.assertTrue(ModBlocks.isShell(interior.getBlockState(pos)), "The passage should be sealed at " + pos);
            } else {
                helper.assertTrue(interior.getBlockState(pos).isAir(), "The wagon room should be cleared");
            }
        }
        helper.assertTrue(player.getInventory().contains(new ItemStack(ModItems.BASIC_WAGON.get())), "The player should get the wagon back");
        remove(helper, ship);
        helper.succeed();
    }

    // Headlights

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void headlightFollowsEngine(GameTestHelper helper) {
        floor(helper);
        ShipState ship = place(helper, ORIGIN);
        ServerPlayer driver = drive(helper, ship);
        entity(helper, ship).setInput(0.0F);
        helper.runAfterDelay(5, () -> {
            BlockPos light = ship.headlight();
            helper.assertTrue(light != null && helper.getLevel().getBlockState(light).is(Blocks.LIGHT), "The headlight should be on while driving");
            driver.stopRiding();
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(ship.headlight() == null && !helper.getLevel().getBlockState(light).is(Blocks.LIGHT),
                        "The headlight should go out when the driver leaves");
                // The driver was sent back to the helm; don't leave them in an interior a later test may reuse.
                Vec3 outside = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
                driver.teleportTo(helper.getLevel(), outside.x, outside.y, outside.z, 0.0F, 0.0F);
                remove(helper, ship);
                helper.succeed();
            });
        });
    }

    // Helpers

    private static void floor(GameTestHelper helper) {
        floor(helper, 13, 28);
    }

    private static void floor(GameTestHelper helper, int sizeX, int sizeZ) {
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
    }

    private static void attach(GameTestHelper helper, ShipState ship) {
        Component error = ShipWagons.attach(helper.getLevel().getServer(), ship, WagonItem.create(ShipTier.BASIC));
        helper.assertTrue(error == null, "Attaching should work, but: " + (error == null ? "" : error.getString()));
    }

    private static void assertWagonRoom(GameTestHelper helper, ShipState ship, int wagon) {
        ServerLevel interior = helper.getLevel().getServer().getLevel(ModKeys.INTERIOR);
        InteriorLayout.Room room = ship.layout().room(wagon);
        assertBlock(helper, interior, room.origin(), ModBlocks.HULL_FLOOR.get());
        assertBlock(helper, interior, ship.layout().doorLower(wagon), ModBlocks.SHIP_DOOR.get());
        helper.assertTrue(interior.getBlockState(BlockPos.containing(room.entryPoint())).isAir(), "The wagon room should be empty inside");
        for (BlockPos pos : ship.layout().passage(wagon)) {
            helper.assertTrue(interior.getBlockState(pos).isAir(), "The passage to the wagon should be open at " + pos);
        }
    }

    private static void wall(GameTestHelper helper, Block block, int fromZ, int toZ) {
        for (int x = 3; x <= 9; x++) {
            for (int y = 1; y <= 6; y++) {
                for (int z = fromZ; z <= toZ; z++) {
                    helper.setBlock(x, y, z, block);
                }
            }
        }
    }

    private static ShipState place(GameTestHelper helper, Vec3 relativeOrigin) {
        ShipState ship = DrillshipItem.place(helper.getLevel(), ShipTier.BASIC, helper.absoluteVec(relativeOrigin), 0.0F, ModItems.newDrill(ShipTier.BASIC));
        if (ship == null) {
            boolean hasInterior = helper.getLevel().getServer().getLevel(ModKeys.INTERIOR) != null;
            throw new GameTestAssertException(hasInterior ? "The drillship did not fit" : "The interior dimension is missing");
        }
        ship.fuelSlot().setStackInSlot(0, new ItemStack(Items.COAL, 16));
        return ship;
    }

    // Ships outlive the test area: without this a driven ship keeps going and force-loads chunks forever.
    private static void remove(GameTestHelper helper, ShipState ship) {
        ShipTravel.dismantle(helper.getLevel().getServer(), ship);
    }

    private static ServerPlayer drive(GameTestHelper helper, ShipState ship) {
        ServerPlayer driver = helper.makeMockServerPlayerInLevel();
        DrillshipEntity entity = entity(helper, ship);
        driver.teleportTo(entity.getX(), entity.getY(), entity.getZ());
        driver.startRiding(entity, true);
        entity.setInput(1.0F);
        return driver;
    }

    private static DrillshipEntity entity(GameTestHelper helper, ShipState ship) {
        DrillshipEntity entity = ship.findEntity(helper.getLevel().getServer());
        if (entity == null) {
            throw new GameTestAssertException("The drillship's exterior is missing");
        }
        return entity;
    }

    private static Vec3 relative(GameTestHelper helper, Vec3 absolute) {
        return absolute.subtract(helper.absoluteVec(Vec3.ZERO));
    }

    private static boolean contains(ShipState ship, net.minecraft.world.item.Item item) {
        for (int i = 0; i < ship.inventory().getSlots(); i++) {
            if (ship.inventory().getStackInSlot(i).is(item)) {
                return true;
            }
        }
        return false;
    }

    // Into the first free block of the drillhead's ceiling (the middle one holds the lamp).
    private static BlockPos installTurret(GameTestHelper helper, ShipState ship, ModuleItem turret) {
        InteriorLayout.Room room = ship.layout().room(0);
        for (int x = 1; x < room.sizeX() - 1; x++) {
            for (int z = 1; z < room.sizeZ() - 1; z++) {
                BlockPos mount = room.origin().offset(x, room.sizeY() - 1, z);
                if (interior(helper).getBlockState(mount).is(ModBlocks.HULL_CEILING.get())) {
                    helper.assertTrue(ShipModules.install(interior(helper), mount, turret) == null, "A turret goes into the ceiling");
                    return mount;
                }
            }
        }
        throw new GameTestAssertException("No free ceiling block in the drillhead");
    }

    // A mob standing still beside the ship, at relative x.
    private static <T extends Mob> T target(GameTestHelper helper, EntityType<T> type, double x) {
        T mob = helper.spawn(type, new Vec3(x, 1, ORIGIN.z));
        mob.setNoAi(true);
        return mob;
    }

    private static ServerLevel interior(GameTestHelper helper) {
        return helper.getLevel().getServer().getLevel(ModKeys.INTERIOR);
    }

    /** The first free block of the drillhead's min-X (door side) wall, from the floor up, with a free wall block above it. */
    private static BlockPos freeWall(GameTestHelper helper, ShipState ship) {
        ServerLevel interior = interior(helper);
        InteriorLayout.Room room = ship.layout().room(0);
        for (int y = 1; y < room.sizeY() - 1; y++) {
            for (int z = 1; z < room.sizeZ() - 1; z++) {
                BlockPos pos = room.origin().offset(0, y, z);
                BlockState state = interior.getBlockState(pos);
                if ((state.is(ModBlocks.HULL.get()) || state.is(ModBlocks.HULL_SKIRT.get()))
                        && interior.getBlockState(pos.above()).is(ModBlocks.HULL.get())) {
                    return pos;
                }
            }
        }
        throw new GameTestAssertException("No free wall block in the drillhead");
    }

    private static void assertBlock(GameTestHelper helper, ServerLevel level, BlockPos pos, Block block) {
        helper.assertTrue(level.getBlockState(pos).is(block), "Expected " + block + " at " + pos + " but found " + level.getBlockState(pos));
    }
}
