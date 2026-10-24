package com.solaris99.drillship.registry;

import com.solaris99.drillship.Drillships;
import com.solaris99.drillship.item.DrillItem;
import com.solaris99.drillship.item.DrillshipItem;
import com.solaris99.drillship.item.UpgradeItem;
import com.solaris99.drillship.item.WagonItem;
import com.solaris99.drillship.ship.ShipTier;
import com.solaris99.drillship.ship.ShipUpgrade;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Drillships.MODID);

    // Basic tier parts (primary: copper, secondary: iron)
    public static final DeferredItem<Item> BASIC_WHEEL = ITEMS.registerSimpleItem("basic_wheel");
    public static final DeferredItem<Item> BASIC_TREAD = ITEMS.registerSimpleItem("basic_tread");
    public static final DeferredItem<Item> BASIC_HULL = ITEMS.registerSimpleItem("basic_hull");
    public static final DeferredItem<Item> BASIC_DRILL_PIECE = ITEMS.registerSimpleItem("basic_drill_piece");
    public static final DeferredItem<Item> BASIC_DRILL_SHAFT = ITEMS.registerSimpleItem("basic_drill_shaft");
    public static final DeferredItem<Item> BASIC_ENGINE = ITEMS.registerSimpleItem("basic_engine");

    public static final DeferredItem<DrillItem> BASIC_DRILL = ITEMS.register("basic_drill",
            () -> new DrillItem(ShipTier.BASIC, new Item.Properties()));
    public static final DeferredItem<DrillshipItem> BASIC_DRILLSHIP = ITEMS.register("basic_drillship",
            () -> new DrillshipItem(ShipTier.BASIC, new Item.Properties()));
    public static final DeferredItem<WagonItem> BASIC_WAGON = ITEMS.register("basic_wagon",
            () -> new WagonItem(ShipTier.BASIC, new Item.Properties()));

    // Standard tier parts (primary: bronze, secondary: brass)
    public static final DeferredItem<Item> STANDARD_WHEEL = ITEMS.registerSimpleItem("standard_wheel");
    public static final DeferredItem<Item> STANDARD_TREAD = ITEMS.registerSimpleItem("standard_tread");
    public static final DeferredItem<Item> STANDARD_HULL = ITEMS.registerSimpleItem("standard_hull");
    public static final DeferredItem<Item> STANDARD_DRILL_PIECE = ITEMS.registerSimpleItem("standard_drill_piece");
    public static final DeferredItem<Item> STANDARD_DRILL_SHAFT = ITEMS.registerSimpleItem("standard_drill_shaft");
    public static final DeferredItem<Item> STANDARD_ENGINE = ITEMS.registerSimpleItem("standard_engine");

    public static final DeferredItem<DrillItem> STANDARD_DRILL = ITEMS.register("standard_drill",
            () -> new DrillItem(ShipTier.STANDARD, new Item.Properties()));
    public static final DeferredItem<DrillshipItem> STANDARD_DRILLSHIP = ITEMS.register("standard_drillship",
            () -> new DrillshipItem(ShipTier.STANDARD, new Item.Properties()));
    public static final DeferredItem<WagonItem> STANDARD_WAGON = ITEMS.register("standard_wagon",
            () -> new WagonItem(ShipTier.STANDARD, new Item.Properties()));

    // Hardened tier parts (primary: steel, secondary: diamond)
    public static final DeferredItem<Item> HARDENED_WHEEL = ITEMS.registerSimpleItem("hardened_wheel");
    public static final DeferredItem<Item> HARDENED_TREAD = ITEMS.registerSimpleItem("hardened_tread");
    public static final DeferredItem<Item> HARDENED_HULL = ITEMS.registerSimpleItem("hardened_hull");
    public static final DeferredItem<Item> HARDENED_DRILL_PIECE = ITEMS.registerSimpleItem("hardened_drill_piece");
    public static final DeferredItem<Item> HARDENED_DRILL_SHAFT = ITEMS.registerSimpleItem("hardened_drill_shaft");
    public static final DeferredItem<Item> HARDENED_ENGINE = ITEMS.registerSimpleItem("hardened_engine");

    public static final DeferredItem<DrillItem> HARDENED_DRILL = ITEMS.register("hardened_drill",
            () -> new DrillItem(ShipTier.HARDENED, new Item.Properties()));
    public static final DeferredItem<DrillshipItem> HARDENED_DRILLSHIP = ITEMS.register("hardened_drillship",
            () -> new DrillshipItem(ShipTier.HARDENED, new Item.Properties()));
    public static final DeferredItem<WagonItem> HARDENED_WAGON = ITEMS.register("hardened_wagon",
            () -> new WagonItem(ShipTier.HARDENED, new Item.Properties()));

    // Upgrades for the upgrade panels.
    public static final DeferredItem<UpgradeItem> RADAR_UPGRADE = ITEMS.register("radar_upgrade",
            () -> new UpgradeItem(ShipUpgrade.RADAR, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<UpgradeItem> RADIO_UPGRADE = ITEMS.register("radio_upgrade",
            () -> new UpgradeItem(ShipUpgrade.RADIO, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<UpgradeItem> ENGINE_OVERDRIVE_UPGRADE = ITEMS.register("engine_overdrive_upgrade",
            () -> new UpgradeItem(ShipUpgrade.ENGINE_OVERDRIVE, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<UpgradeItem> ANTIGRAVITY_UPGRADE = ITEMS.register("antigravity_upgrade",
            () -> new UpgradeItem(ShipUpgrade.ANTIGRAVITY, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<UpgradeItem> INVENTORY_UPGRADE = ITEMS.register("inventory_upgrade",
            () -> new UpgradeItem(ShipUpgrade.INVENTORY, new Item.Properties().stacksTo(16)));

    // Upgrades used to be called modules.
    static {
        ITEMS.addAlias(Drillships.id("radar_module"), Drillships.id("radar_upgrade"));
        ITEMS.addAlias(Drillships.id("radio_module"), Drillships.id("radio_upgrade"));
    }

    public static ItemStack upgrade(ShipUpgrade upgrade) {
        return new ItemStack(switch (upgrade) {
            case RADAR -> RADAR_UPGRADE.get();
            case RADIO -> RADIO_UPGRADE.get();
            case ENGINE_OVERDRIVE -> ENGINE_OVERDRIVE_UPGRADE.get();
            case ANTIGRAVITY -> ANTIGRAVITY_UPGRADE.get();
            case INVENTORY -> INVENTORY_UPGRADE.get();
        });
    }

    public static ItemStack newDrill(ShipTier tier) {
        return new ItemStack(switch (tier) {
            case BASIC -> BASIC_DRILL.get();
            case STANDARD -> STANDARD_DRILL.get();
            case HARDENED -> HARDENED_DRILL.get();
        });
    }

    public static Item drillship(ShipTier tier) {
        return switch (tier) {
            case BASIC -> BASIC_DRILLSHIP.get();
            case STANDARD -> STANDARD_DRILLSHIP.get();
            case HARDENED -> HARDENED_DRILLSHIP.get();
        };
    }

    public static Item wagon(ShipTier tier) {
        return switch (tier) {
            case BASIC -> BASIC_WAGON.get();
            case STANDARD -> STANDARD_WAGON.get();
            case HARDENED -> HARDENED_WAGON.get();
        };
    }

    private ModItems() {
    }
}
