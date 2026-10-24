package com.solaris99.drillship.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModTags {
    // Any mod's wrench takes modules out, ours included.
    public static final TagKey<Item> WRENCHES = ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "tools/wrench"));

    private ModTags() {
    }
}
