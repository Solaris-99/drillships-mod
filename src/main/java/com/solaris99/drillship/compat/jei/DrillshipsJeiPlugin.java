package com.solaris99.drillship.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.solaris99.drillship.Drillships;
import com.solaris99.drillship.material.Material;
import com.solaris99.drillship.registry.ModMaterials;
import com.solaris99.drillship.registry.ModRecipes;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/** Loaded by JEI only: shows the alloy furnace's recipes, and hides our materials where another mod provides them. */
@JeiPlugin
public class DrillshipsJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return Drillships.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new AlloyingCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (Minecraft.getInstance().level != null) {
            registration.addRecipes(AlloyingCategory.TYPE, Minecraft.getInstance().level.getRecipeManager().getAllRecipesFor(ModRecipes.ALLOYING.get()));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(AlloyingCategory.TYPE, AlloyingCategory.catalysts().toArray(ItemStack[]::new));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        List<ItemStack> hidden = new ArrayList<>();
        for (Material material : Material.values()) {
            if (!material.isOurs()) {
                for (ItemLike item : ModMaterials.of(material).all()) {
                    hidden.add(new ItemStack(item));
                }
            }
        }
        if (!hidden.isEmpty()) {
            runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
        }
    }
}
