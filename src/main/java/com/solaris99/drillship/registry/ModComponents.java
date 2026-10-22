package com.solaris99.drillship.registry;

import com.solaris99.drillship.Drillships;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Drillships.MODID);

    // The drill stored in a picked-up drillship item. Absent on a freshly crafted ship, which gets a new drill of its tier.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> INSTALLED_DRILL = COMPONENTS.registerComponentType(
            "installed_drill",
            builder -> builder.persistent(ItemContainerContents.CODEC).networkSynchronized(ItemContainerContents.STREAM_CODEC));

    private ModComponents() {
    }
}
