package com.solaris99.drillship.item;

import java.util.List;

import com.solaris99.drillship.ship.ShipModule;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A module, installed by putting it into one of the module panels on the drillhead's left wall. */
public class ModuleItem extends Item {
    private final ShipModule module;

    public ModuleItem(ShipModule module, Properties properties) {
        super(properties);
        this.module = module;
    }

    public ShipModule module() {
        return module;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String name = module.name().toLowerCase(java.util.Locale.ROOT);
        tooltip.add(Component.translatable("tooltip.drillships.module." + name).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.drillships.module").withStyle(ChatFormatting.DARK_GRAY));
    }
}
