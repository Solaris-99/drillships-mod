package com.solaris99.drillship.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-player settings (drillships-client.toml), changed from the controls panel. */
public final class ClientConfig {
    public enum DrillMode {
        /** A press starts the drill, the next one stops it. */
        TOGGLE,
        /** The drill spins while the key is held. */
        HOLD
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<DrillMode> DRILL_MODE = BUILDER
            .comment("How the drill key works: TOGGLE (press to start, press again to stop) or HOLD (spins while held)")
            .defineEnum("drillMode", DrillMode.TOGGLE);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
