package com.solaris99.drillship.registry;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.solaris99.drillship.Drillships;
import com.solaris99.drillship.alloy.AlloyRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Drillships.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Drillships.MODID);

    public static final Supplier<RecipeType<AlloyRecipe>> ALLOYING = TYPES.register("alloying", () -> RecipeType.simple(Drillships.id("alloying")));
    public static final Supplier<RecipeSerializer<AlloyRecipe>> ALLOYING_SERIALIZER = SERIALIZERS.register("alloying", () -> new RecipeSerializer<>() {
        @Override
        public MapCodec<AlloyRecipe> codec() {
            return AlloyRecipe.CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, AlloyRecipe> streamCodec() {
            return AlloyRecipe.STREAM_CODEC;
        }
    });

    private ModRecipes() {
    }
}
