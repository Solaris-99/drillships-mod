package com.solaris99.drillship.ship;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;

public enum ShipTier {
    //       interior w/h, wagon len, wagons, slots, energy buffer, FE/t, blocks/s, drill speed, mining level, drill durability, repair block
    BASIC(3, 3, 7, 3, 100, 1_000_000, 240, 8.0, 6.0F, Tiers.STONE, 1_000_000, "copper"),
    STANDARD(5, 5, 10, 5, 150, 2_000_000, 480, 10.0, 8.0F, Tiers.IRON, 2_000_000, "bronze"),
    HARDENED(9, 9, 15, 8, 200, 5_000_000, 1024, 12.0, 10.0F, Tiers.NETHERITE, 3_000_000, "steel");

    // Interior length of the drillhead, the same for every tier.
    public static final int DRILLHEAD_LENGTH = 5;

    private final int interiorWidth;
    private final int interiorHeight;
    private final int wagonLength;
    private final int maxWagons;
    private final int inventorySlots;
    private final int energyCapacity;
    private final int generation;
    private final double topSpeed;
    private final float drillSpeed;
    private final Tiers miningTier;
    private final int drillDurability;
    private final TagKey<Item> repairBlock;

    ShipTier(int interiorWidth, int interiorHeight, int wagonLength, int maxWagons, int inventorySlots, int energyCapacity, int generation,
            double topSpeed, float drillSpeed, Tiers miningTier, int drillDurability, String primaryMaterial) {
        this.interiorWidth = interiorWidth;
        this.interiorHeight = interiorHeight;
        this.wagonLength = wagonLength;
        this.maxWagons = maxWagons;
        this.inventorySlots = inventorySlots;
        this.energyCapacity = energyCapacity;
        this.generation = generation;
        this.topSpeed = topSpeed;
        this.drillSpeed = drillSpeed;
        this.miningTier = miningTier;
        this.drillDurability = drillDurability;
        this.repairBlock = ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "storage_blocks/" + primaryMaterial));
    }

    public static ShipTier byId(int id) {
        ShipTier[] values = values();
        return values[Math.floorMod(id, values.length)];
    }

    public int interiorWidth() {
        return interiorWidth;
    }

    public int interiorHeight() {
        return interiorHeight;
    }

    public int wagonLength() {
        return wagonLength;
    }

    public int maxWagons() {
        return maxWagons;
    }

    public int exteriorWidth() {
        return interiorWidth + 2;
    }

    public int exteriorHeight() {
        return interiorHeight + 2;
    }

    public int drillheadExteriorLength() {
        return DRILLHEAD_LENGTH + 2;
    }

    public int wagonExteriorLength() {
        return wagonLength + 2;
    }

    public int inventorySlots() {
        return inventorySlots;
    }

    public int energyCapacity() {
        return energyCapacity;
    }

    public int generation() {
        return generation;
    }

    /** Top speed in blocks per second. */
    public double topSpeed() {
        return topSpeed;
    }

    public float drillSpeed() {
        return drillSpeed;
    }

    public Tiers miningTier() {
        return miningTier;
    }

    public int drillDurability() {
        return drillDurability;
    }

    public TagKey<Item> repairBlock() {
        return repairBlock;
    }
}
