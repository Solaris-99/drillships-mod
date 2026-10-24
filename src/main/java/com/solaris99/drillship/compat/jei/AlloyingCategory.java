package com.solaris99.drillship.compat.jei;

import java.util.Arrays;
import java.util.List;

import com.solaris99.drillship.Drillships;
import com.solaris99.drillship.alloy.AlloyRecipe;
import com.solaris99.drillship.registry.ModItems;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/** The alloy furnace's recipes in JEI: the inputs, an arrow that fills in the recipe's time, and the result. */
class AlloyingCategory implements IRecipeCategory<RecipeHolder<AlloyRecipe>> {
    static final RecipeType<RecipeHolder<AlloyRecipe>> TYPE = RecipeType.createRecipeHolderType(Drillships.id("alloying"));

    private final IDrawable icon;

    AlloyingCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModItems.ALLOY_FURNACE.get());
    }

    @Override
    public RecipeType<RecipeHolder<AlloyRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.drillships.alloy_furnace");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return 84;
    }

    @Override
    public int getHeight() {
        return 18;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<AlloyRecipe> recipe, IFocusGroup focuses) {
        List<SizedIngredient> ingredients = recipe.value().ingredients();
        for (int i = 0; i < ingredients.size(); i++) {
            SizedIngredient ingredient = ingredients.get(i);
            builder.addInputSlot(1 + i * 18, 1).setStandardSlotBackground()
                    .addItemStacks(Arrays.stream(ingredient.ingredient().getItems()).map(stack -> stack.copyWithCount(ingredient.count())).toList());
        }
        builder.addOutputSlot(67, 1).setOutputSlotBackground().addItemStack(recipe.value().result());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<AlloyRecipe> recipe, IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(recipe.value().time()).setPosition(40, 1);
    }

    static List<ItemStack> catalysts() {
        return List.of(new ItemStack(ModItems.ALLOY_FURNACE.get()), new ItemStack(ModItems.ALLOY_FURNACE_MODULE.get()));
    }
}
