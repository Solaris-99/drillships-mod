package com.solaris99.drillship.registry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.solaris99.drillship.Drillships;
import com.solaris99.drillship.material.Material;
import com.solaris99.drillship.material.MaterialCondition;
import com.solaris99.drillship.material.MaterialFilter;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Our own tin, zinc, bronze, brass and steel (see Material): ingots, nuggets and blocks of each, and for tin and zinc
 * their ores, raw metal and blocks of raw metal, like iron's.
 */
public final class ModMaterials {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Drillships.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Drillships.MODID);
    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS = DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE,
            Drillships.MODID);
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS = DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS,
            Drillships.MODID);

    public static final Supplier<PlacementModifierType<MaterialFilter>> MATERIAL_FILTER = PLACEMENT_MODIFIERS.register("material",
            () -> () -> MaterialFilter.CODEC);
    public static final Supplier<MapCodec<MaterialCondition>> MATERIAL_CONDITION = CONDITIONS.register("material", () -> MaterialCondition.CODEC);

    /** One material's items and blocks; the raw metal and ores are null for the alloys. */
    public record Set(DeferredItem<Item> ingot, DeferredItem<Item> nugget, DeferredBlock<Block> block, @Nullable DeferredItem<Item> raw,
            @Nullable DeferredBlock<Block> rawBlock, @Nullable DeferredBlock<Block> ore, @Nullable DeferredBlock<Block> deepslateOre) {
        /** Everything, in the order the creative tab shows it. */
        public List<ItemLike> all() {
            List<ItemLike> all = new ArrayList<>(List.of(ingot.get(), nugget.get(), block.get()));
            if (raw != null) {
                all.addAll(List.of(raw.get(), rawBlock.get(), ore.get(), deepslateOre.get()));
            }
            return all;
        }
    }

    private static final Map<Material, Set> SETS = new EnumMap<>(Material.class);

    static {
        for (Material material : Material.values()) {
            String id = material.id();
            DeferredItem<Item> raw = null;
            DeferredBlock<Block> rawBlock = null, ore = null, deepslateOre = null;
            if (material.hasOre()) {
                raw = ITEMS.registerSimpleItem("raw_" + id);
                rawBlock = block("raw_" + id + "_block", BlockBehaviour.Properties.of().mapColor(MapColor.RAW_IRON)
                        .instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(5.0F, 6.0F));
                ore = block(id + "_ore", BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
                        .requiresCorrectToolForDrops().strength(3.0F, 3.0F));
                deepslateOre = block("deepslate_" + id + "_ore", BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)
                        .instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE));
            }
            SETS.put(material, new Set(ITEMS.registerSimpleItem(id + "_ingot"), ITEMS.registerSimpleItem(id + "_nugget"),
                    block(id + "_block", BlockBehaviour.Properties.of().mapColor(MapColor.METAL).instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                            .requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.METAL)),
                    raw, rawBlock, ore, deepslateOre));
        }
    }

    public static Set of(Material material) {
        return SETS.get(material);
    }

    private static DeferredBlock<Block> block(String name, BlockBehaviour.Properties properties) {
        DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(name, properties);
        ITEMS.registerSimpleBlockItem(block);
        return block;
    }

    private ModMaterials() {
    }
}
