package com.solaris99.drillship.gametest;

import com.solaris99.drillship.Drillships;
import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.entity.WagonEntity;
import com.solaris99.drillship.item.DrillshipItem;
import com.solaris99.drillship.item.WagonItem;
import com.solaris99.drillship.registry.ModBlocks;
import com.solaris99.drillship.registry.ModComponents;
import com.solaris99.drillship.registry.ModItems;
import com.solaris99.drillship.registry.ModKeys;
import com.solaris99.drillship.ship.InteriorLayout;
import com.solaris99.drillship.ship.ShipManager;
import com.solaris99.drillship.ship.ShipState;
import com.solaris99.drillship.ship.ShipTier;
import com.solaris99.drillship.ship.ShipTravel;
import com.solaris99.drillship.ship.ShipWagons;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
        assertBlock(helper, interior, layout.doorLower(), ModBlocks.SHIP_DOOR.get());
        assertBlock(helper, interior, layout.origin(), ModBlocks.HULL.get());
        helper.assertTrue(interior.getBlockState(BlockPos.containing(layout.entryPoint())).isAir(), "Entry point should be free");
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
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ShipTravel.enterShip(player, ship);
        ShipTravel.pickUp(player, ship);

        helper.assertTrue(ShipManager.get(helper.getLevel().getServer()).get(ship.id()) == null, "Ship should be gone");
        ItemStack item = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.BASIC_DRILLSHIP.get())) {
                item = player.getInventory().getItem(i);
            }
        }
        helper.assertTrue(!item.isEmpty(), "Player should get the drillship item back");
        helper.assertTrue(item.get(ModComponents.INSTALLED_DRILL.get()).copyOne().is(ModItems.BASIC_DRILL.get()), "The drill should be stored in the item");
        helper.succeed();
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
            // Drillhead half length 3.5 + gap 0.5 + wagon half length 4.5.
            double gap = drillhead.getZ() - wagon.getZ();
            helper.assertTrue(Math.abs(gap - 8.5) < 0.6, "The wagon should trail 8.5 blocks behind, but trails " + gap);
            helper.assertTrue(Math.abs(wagon.getX() - drillhead.getX()) < 0.01 && Math.abs(wagon.getYRot()) < 0.01,
                    "The wagon should be lined up behind the drillhead");
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
                assertBlock(helper, interior, pos, ModBlocks.HULL.get());
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
        entity(helper, ship).setInput(0, 0, false, false);
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
        assertBlock(helper, interior, room.origin(), ModBlocks.HULL.get());
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
        entity.setInput(1.0F, 0.0F, false, false);
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

    private static void assertBlock(GameTestHelper helper, ServerLevel level, BlockPos pos, Block block) {
        helper.assertTrue(level.getBlockState(pos).is(block), "Expected " + block + " at " + pos + " but found " + level.getBlockState(pos));
    }
}
