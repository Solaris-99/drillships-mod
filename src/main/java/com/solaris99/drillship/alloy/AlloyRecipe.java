package com.solaris99.drillship.alloy;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.solaris99.drillship.registry.ModRecipes;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * An alloy furnace recipe: one or two ingredients, each with a count, in either input slot, making {@code result} in
 * {@code time} ticks. A one-ingredient recipe needs the other slot empty.
 */
public record AlloyRecipe(List<SizedIngredient> ingredients, ItemStack result, int time) implements Recipe<AlloyInput> {
    public static final int DEFAULT_TIME = 200;

    public static final MapCodec<AlloyRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            SizedIngredient.FLAT_CODEC.listOf(1, 2).fieldOf("ingredients").forGetter(AlloyRecipe::ingredients),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(AlloyRecipe::result),
            Codec.INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(AlloyRecipe::time)).apply(instance, AlloyRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, AlloyRecipe> STREAM_CODEC = StreamCodec.composite(
            SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), AlloyRecipe::ingredients,
            ItemStack.STREAM_CODEC, AlloyRecipe::result,
            ByteBufCodecs.VAR_INT, AlloyRecipe::time,
            AlloyRecipe::new);

    public static Optional<RecipeHolder<AlloyRecipe>> find(Level level, AlloyInput input) {
        if (input.first().isEmpty() && input.second().isEmpty()) {
            return Optional.empty();
        }
        return level.getRecipeManager().getRecipeFor(ModRecipes.ALLOYING.get(), input, level);
    }

    /** Which input slot each ingredient is taken from, or null when the inputs don't make this. */
    @Nullable
    public int[] slotsFor(AlloyInput input) {
        if (ingredients.size() == 1) {
            for (int slot = 0; slot < 2; slot++) {
                if (takes(0, input.getItem(slot)) && input.getItem(1 - slot).isEmpty()) {
                    return new int[] {slot};
                }
            }
            return null;
        }
        if (takes(0, input.first()) && takes(1, input.second())) {
            return new int[] {0, 1};
        }
        return takes(0, input.second()) && takes(1, input.first()) ? new int[] {1, 0} : null;
    }

    private boolean takes(int ingredient, ItemStack stack) {
        SizedIngredient sized = ingredients.get(ingredient);
        return sized.ingredient().test(stack) && stack.getCount() >= sized.count();
    }

    /** Takes the ingredients out of the input slots. */
    public void consume(AlloyInput input, int[] slots) {
        for (int i = 0; i < slots.length; i++) {
            input.getItem(slots[i]).shrink(ingredients.get(i).count());
        }
    }

    @Override
    public boolean matches(AlloyInput input, Level level) {
        return slotsFor(input) != null;
    }

    @Override
    public ItemStack assemble(AlloyInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        ingredients.forEach(sized -> list.add(sized.ingredient()));
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.ALLOYING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.ALLOYING.get();
    }
}
