package com.solaris99.drillship;

import net.neoforged.neoforge.common.ModConfigSpec;

// Server config: values are synced from the server and only read on the logical server.
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue CHUNK_LOADING = BUILDER
            .comment("Keep the chunks around each drillship and its interior loaded, even with no player nearby.")
            .define("chunkLoading", true);

    public static final ModConfigSpec.DoubleValue MINING_SPEED_MULTIPLIER = BUILDER
            .comment("Multiplier applied to every drill's mining speed. 2.0 makes a basic drill cruise through stone at ~67% of top speed.")
            .defineInRange("miningSpeedMultiplier", 2.0, 0.01, 100.0);

    public static final ModConfigSpec.BooleanValue DROP_WITHOUT_CORRECT_TIER = BUILDER
            .comment("If true, blocks that need a higher drill tier still drop their items (they are always mined slowly).")
            .define("dropWithoutCorrectTier", false);

    public static final ModConfigSpec.BooleanValue MINE_BLOCK_ENTITIES = BUILDER
            .comment("If true, drills mine blocks with inventories (chests, machines) and spill their contents. If false, such blocks stop the ship.")
            .define("mineBlockEntities", true);

    public static final ModConfigSpec.BooleanValue VOID_WHEN_FULL = BUILDER
            .comment("If true, mined items that don't fit in the ship's inventory are destroyed instead of dropped.")
            .define("voidWhenFull", false);

    public static final ModConfigSpec.IntValue MOVE_ENERGY = BUILDER
            .comment("FE per tick consumed while the ship is moving.")
            .defineInRange("moveEnergy", 1, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue DRILL_ENERGY_PER_BLOCK = BUILDER
            .comment("FE per tick consumed by a spinning drill, per block of drill face.")
            .defineInRange("drillEnergyPerBlock", 1, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue DRILL_DAMAGE = BUILDER
            .comment("Damage dealt to entities touching a spinning drill.")
            .defineInRange("drillDamage", 20.0, 0.0, 10000.0);

    public static final ModConfigSpec.DoubleValue SQUASH_DAMAGE = BUILDER
            .comment("Damage dealt to entities squashed between a moving ship and a solid block.")
            .defineInRange("squashDamage", 100.0, 0.0, 10000.0);

    public static final ModConfigSpec.IntValue ENERGY_IO_MAX_RATE = BUILDER
            .comment("The most FE per tick an Energy IO module moves each way, whatever its own limit. 0 means no cap.")
            .defineInRange("energyIoMaxRate", 0, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue INVENTORY_IO_MAX_RATE = BUILDER
            .comment("The most items per second an Inventory IO module lets through each way, whatever its own limit. 0 means no cap.")
            .defineInRange("inventoryIoMaxRate", 0, 0, Integer.MAX_VALUE);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
