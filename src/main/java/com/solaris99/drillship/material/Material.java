package com.solaris99.drillship.material;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.solaris99.drillship.Drillships;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;

/**
 * The metals the tiers need. Other mods' are preferred: ours are only made (recipes, ore generation) while no other mod
 * provides the material, judged by whether a popular mod that has it is loaded, or another mod's ingot is in its
 * {@code c:ingots/...} tag. Our items are always registered (Minecraft can't register them conditionally); when another
 * mod provides the material they're hidden from JEI.
 */
public enum Material implements StringRepresentable {
    TIN(true, "mekanism", "thermal"),
    ZINC(true, "create"),
    BRONZE(false, "mekanism", "thermal"),
    BRASS(false, "create"),
    STEEL(false, "mekanism", "immersiveengineering");

    public static final Codec<Material> CODEC = StringRepresentable.fromEnum(Material::values);

    private final boolean hasOre;
    private final List<String> modsWithIt;
    private final TagKey<Item> ingots;

    Material(boolean hasOre, String... modsWithIt) {
        this.hasOre = hasOre;
        this.modsWithIt = List.of(modsWithIt);
        this.ingots = ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "ingots/" + id()));
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return id();
    }

    /** Tin and zinc are mined; the alloys are made in the alloy furnace. */
    public boolean hasOre() {
        return hasOre;
    }

    /** Whether we make it, with the tags as loaded now. */
    public boolean isOurs() {
        return isOurs(tag -> BuiltInRegistries.ITEM.getTag(tag).<Collection<Holder<Item>>>map(set -> set.stream().toList()).orElse(List.of()));
    }

    /** Whether we make it, with tags looked up through {@code tags} (as recipe conditions see them while loading). */
    public boolean isOurs(Function<TagKey<Item>, Collection<Holder<Item>>> tags) {
        if (modsWithIt.stream().anyMatch(ModList.get()::isLoaded)) {
            return false;
        }
        return tags.apply(ingots).stream().allMatch(holder -> holder.unwrapKey().map(key -> key.location().getNamespace().equals(Drillships.MODID)).orElse(true));
    }
}
