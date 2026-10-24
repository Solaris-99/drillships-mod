package com.solaris99.drillship.item;

import java.util.List;

import com.solaris99.drillship.ship.ShipUpgrade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** An upgrade, installed by putting it into one of the upgrade panels on the drillhead's left wall. */
public class UpgradeItem extends Item {
    private final ShipUpgrade upgrade;

    public UpgradeItem(ShipUpgrade upgrade, Properties properties) {
        super(properties);
        this.upgrade = upgrade;
    }

    public ShipUpgrade upgrade() {
        return upgrade;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String name = upgrade.name().toLowerCase(java.util.Locale.ROOT);
        tooltip.add(Component.translatable("tooltip.drillships.upgrade." + name).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.drillships.upgrade").withStyle(ChatFormatting.DARK_GRAY));
    }
}
