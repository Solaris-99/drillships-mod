package com.solaris99.drillship.registry;

import com.solaris99.drillship.Drillships;
import com.solaris99.drillship.item.DrillItem;
import com.solaris99.drillship.item.DrillshipItem;
import com.solaris99.drillship.item.WagonItem;
import com.solaris99.drillship.ship.ShipTier;

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

    public static ItemStack newDrill(ShipTier tier) {
        return switch (tier) {
            case BASIC -> new ItemStack(BASIC_DRILL.get());
            // Standard and hardened drills come with the other tiers.
            case STANDARD, HARDENED -> ItemStack.EMPTY;
        };
    }

    private ModItems() {
    }
}
