package com.solaris99.drillship.material;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.solaris99.drillship.registry.ModMaterials;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Places our ore only while we make the material. Checked as chunks generate: unlike recipe conditions, worldgen data
 * loads before tags, so it can't be switched off any earlier.
 */
public class MaterialFilter extends PlacementFilter {
    public static final MapCodec<MaterialFilter> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Material.CODEC.fieldOf("material").forGetter(filter -> filter.material)).apply(instance, MaterialFilter::new));

    private final Material material;

    public MaterialFilter(Material material) {
        this.material = material;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        return material.isOurs();
    }

    @Override
    public PlacementModifierType<?> type() {
        return ModMaterials.MATERIAL_FILTER.get();
    }
}
