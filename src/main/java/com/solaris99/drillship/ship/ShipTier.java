package com.solaris99.drillship.ship;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;

public enum ShipTier {
    //       interior w/h, wagon len, head len, wagons, slots, energy buffer, FE/t, blocks/s, drill speed, mining level, drill durability, repair block
    BASIC(3, 3, 7, 5, 3, 100, 1_000_000, 240, 8.0, 6.0F, Tiers.STONE, 1_000_000, "copper", new Coupling(1, 1, 2, 1.0F, 35.0F)),
    STANDARD(5, 5, 10, 7, 5, 150, 2_000_000, 480, 10.0, 8.0F, Tiers.IRON, 2_000_000, "bronze", new Coupling(2, 3, 3, 1.25F, 40.0F)),
    HARDENED(9, 9, 15, 9, 8, 200, 5_000_000, 1024, 12.0, 10.0F, Tiers.NETHERITE, 3_000_000, "steel", new Coupling(3, 5, 4, 1.5F, 35.0F));

    /**
     * How sections join. {@code gap}: blocks between consecutive sections, bridged by bellows outside and by a passage
     * inside. {@code passageWidth} x {@code passageHeight}: the interior opening, from the floor up. {@code taper}: how
     * much each section's ends narrow over their last {@code taper} blocks; it never cuts into the interior room.
     * {@code maxBend}: how far, in degrees, the drillhead may turn or pitch away from the first wagon, a few degrees
     * short of where their ends would touch (38 / 45 / 39 for turning, at least as much up and down), so the path never
     * gets a kink the wagons can't follow.
     */
    public record Coupling(int gap, int passageWidth, int passageHeight, float taper, float maxBend) {
    }

    private final int interiorWidth;
    private final int interiorHeight;
    private final int wagonLength;
    private final int drillheadLength;
    private final Coupling coupling;
    private final int maxWagons;
    private final int inventorySlots;
    private final int energyCapacity;
    private final int generation;
    private final double topSpeed;
    private final float drillSpeed;
    private final Tiers miningTier;
    private final int drillDurability;
    private final TagKey<Item> repairBlock;

    ShipTier(int interiorWidth, int interiorHeight, int wagonLength, int drillheadLength, int maxWagons, int inventorySlots, int energyCapacity,
            int generation, double topSpeed, float drillSpeed, Tiers miningTier, int drillDurability, String primaryMaterial, Coupling coupling) {
        this.interiorWidth = interiorWidth;
        this.interiorHeight = interiorHeight;
        this.wagonLength = wagonLength;
        this.drillheadLength = drillheadLength;
        this.coupling = coupling;
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
        return drillheadLength + 2;
    }

    public Coupling coupling() {
        return coupling;
    }

    /** Upgrade panels on the drillhead's left wall, one more per tier, each holding four upgrades. */
    public int upgradePanels() {
        return ordinal() + 1;
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
