package com.solaris99.drillship.registry;

import com.solaris99.drillship.Drillships;
import com.solaris99.drillship.entity.DrillshipEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Drillships.MODID);

    // Never saved to chunks: the ship's state lives in ShipManager and the entity is respawned from it.
    public static final DeferredHolder<EntityType<?>, EntityType<DrillshipEntity>> DRILLSHIP = ENTITIES.register("drillship",
            () -> EntityType.Builder.<DrillshipEntity>of(DrillshipEntity::new, MobCategory.MISC)
                    .sized(5.0F, 5.0F)
                    .fireImmune()
                    .noSave()
                    .noSummon()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("drillship"));

    private ModEntities() {
    }
}
