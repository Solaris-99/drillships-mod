package com.solaris99.drillship.registry;

import com.solaris99.drillship.Drillships;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

// Keys for data-driven registry entries defined in data/drillships/.
public final class ModKeys {
    public static final ResourceKey<Level> INTERIOR = ResourceKey.create(Registries.DIMENSION, Drillships.id("interior"));

    public static final ResourceKey<DamageType> DRILL_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Drillships.id("drill"));
    public static final ResourceKey<DamageType> SQUASH_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Drillships.id("squash"));
    public static final ResourceKey<DamageType> TESLA_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Drillships.id("tesla"));

    private ModKeys() {
    }
}
