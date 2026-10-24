package com.solaris99.drillship.item;

import java.util.List;

import com.solaris99.drillship.registry.ModItems;
import com.solaris99.drillship.ship.ShipTier;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A wagon, attached to a ship of the same tier through its status panel. */
public class WagonItem extends Item {
    private final ShipTier tier;

    public WagonItem(ShipTier tier, Properties properties) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public ShipTier tier() {
        return tier;
    }

    public static ItemStack create(ShipTier tier) {
        return new ItemStack(ModItems.wagon(tier));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.drillships.wagon").withStyle(ChatFormatting.GRAY));
    }
}
