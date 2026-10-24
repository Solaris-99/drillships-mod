package com.solaris99.drillship.material;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.neoforged.neoforge.common.conditions.ICondition;

/** A recipe condition: true while we make at least one of the materials (see Material). */
public record MaterialCondition(List<Material> materials) implements ICondition {
    public static final MapCodec<MaterialCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Material.CODEC.listOf().fieldOf("materials").forGetter(MaterialCondition::materials)).apply(instance, MaterialCondition::new));

    @Override
    public boolean test(IContext context) {
        return materials.stream().anyMatch(material -> material.isOurs(context::getTag));
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
