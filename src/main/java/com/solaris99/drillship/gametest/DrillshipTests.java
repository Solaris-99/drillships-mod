package com.solaris99.drillship.gametest;

import com.solaris99.drillship.Drillships;
import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.item.DrillshipItem;
import com.solaris99.drillship.registry.ModBlocks;
import com.solaris99.drillship.registry.ModComponents;
import com.solaris99.drillship.registry.ModItems;
import com.solaris99.drillship.registry.ModKeys;
import com.solaris99.drillship.ship.InteriorLayout;
import com.solaris99.drillship.ship.ShipManager;
import com.solaris99.drillship.ship.ShipState;
import com.solaris99.drillship.ship.ShipTier;
import com.solaris99.drillship.ship.ShipTravel;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
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

    // Helpers

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 13; x++) {
            for (int z = 0; z < 28; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
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

    private static void drive(GameTestHelper helper, ShipState ship) {
        ServerPlayer driver = helper.makeMockServerPlayerInLevel();
        DrillshipEntity entity = entity(helper, ship);
        driver.teleportTo(entity.getX(), entity.getY(), entity.getZ());
        driver.startRiding(entity, true);
        entity.setInput(1.0F, 0.0F, false, false);
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
