package com.solaris99.drillship.ship;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * What the radar can be set to look for. A tier 1 radar finds metals; from tier 2 every ore. Kinds come from the
 * common ore tags, so other mods' ores are found too: their metals (tin, lead...) count as other metals.
 */
public enum RadarOre {
    IRON(1, 0xD8AF93, Tags.Blocks.ORES_IRON),
    COPPER(1, 0xE77C56, Tags.Blocks.ORES_COPPER),
    GOLD(1, 0xFCEE4B, Tags.Blocks.ORES_GOLD),
    NETHERITE(1, 0xB07AD0, Tags.Blocks.ORES_NETHERITE_SCRAP),
    OTHER_METAL(1, 0xDDDDDD, null),
    COAL(2, 0x9A9A9A, Tags.Blocks.ORES_COAL),
    REDSTONE(2, 0xFF4040, Tags.Blocks.ORES_REDSTONE),
    LAPIS(2, 0x4F6BFF, Tags.Blocks.ORES_LAPIS),
    DIAMOND(2, 0x5CF0E8, Tags.Blocks.ORES_DIAMOND),
    EMERALD(2, 0x2CD860, Tags.Blocks.ORES_EMERALD),
    QUARTZ(2, 0xF0EAE0, Tags.Blocks.ORES_QUARTZ);

    private final int tier;
    private final int color;
    @Nullable
    private final TagKey<Block> tag;

    RadarOre(int tier, int color, @Nullable TagKey<Block> tag) {
        this.tier = tier;
        this.color = color;
        this.tag = tag;
    }

    public static RadarOre byId(int id) {
        RadarOre[] values = values();
        return id >= 0 && id < values.length ? values[id] : IRON;
    }

    /** The radar tier it takes to find this. */
    public int tier() {
        return tier;
    }

    public boolean foundAt(int radarTier) {
        return radarTier >= tier;
    }

    /** RGB, for the HUD. */
    public int color() {
        return color;
    }

    /** The next kind a radar of this tier finds, {@code step} places on (negative goes back), wrapping around. */
    public RadarOre next(int radarTier, int step) {
        RadarOre[] values = values();
        int at = ordinal();
        for (int tries = 0; tries < values.length; tries++) {
            at = Math.floorMod(at + Integer.signum(step), values.length);
            if (values[at].foundAt(radarTier)) {
                return values[at];
            }
        }
        return this;
    }

    public Component title() {
        return Component.translatable("radar.drillships.ore." + name().toLowerCase(java.util.Locale.ROOT));
    }

    /** Every ore block by kind. Read from the tags each time, so it follows datapack reloads. */
    public static Map<Block, RadarOre> blocks() {
        Map<Block, RadarOre> kinds = new HashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            if (state.is(Tags.Blocks.ORES)) {
                kinds.put(block, of(state));
            }
        }
        return kinds;
    }

    private static RadarOre of(BlockState state) {
        for (RadarOre kind : values()) {
            if (kind.tag != null && state.is(kind.tag)) {
                return kind;
            }
        }
        return OTHER_METAL;
    }
}
