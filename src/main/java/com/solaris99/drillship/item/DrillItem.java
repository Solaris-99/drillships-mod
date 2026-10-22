package com.solaris99.drillship.item;

import java.util.List;

import com.solaris99.drillship.Config;
import com.solaris99.drillship.ship.ShipTier;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;

/** A drillship's drill. It never breaks: at 0 durability it is just very slow and drops nothing. */
public class DrillItem extends Item {
    private static final float BROKEN_SPEED_FACTOR = 0.1F;
    private static final float WORN_FRACTION = 0.25F;

    private final ShipTier tier;

    public DrillItem(ShipTier tier, Properties properties) {
        super(properties.durability(tier.drillDurability()));
        this.tier = tier;
    }

    public ShipTier tier() {
        return tier;
    }

    @Override
    public int getEnchantmentValue() {
        return 10;
    }

    public static boolean isBroken(ItemStack drill) {
        return drill.isDamageableItem() && drill.getDamageValue() >= drill.getMaxDamage();
    }

    /** At or below 25% durability left. */
    public static boolean isWorn(ItemStack drill) {
        return drill.isDamageableItem() && drill.getMaxDamage() - drill.getDamageValue() <= drill.getMaxDamage() * WORN_FRACTION;
    }

    /** Vanilla tool speed rules: tier speed, plus Efficiency's level² + 1, times the config multiplier. */
    public static float miningSpeed(ItemStack drill, ServerLevel level) {
        if (!(drill.getItem() instanceof DrillItem item)) {
            return 0;
        }
        float speed = item.tier.drillSpeed();
        Holder<Enchantment> efficiency = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.EFFICIENCY);
        int efficiencyLevel = EnchantmentHelper.getItemEnchantmentLevel(efficiency, drill);
        if (efficiencyLevel > 0) {
            speed += efficiencyLevel * efficiencyLevel + 1;
        }
        speed *= Config.MINING_SPEED_MULTIPLIER.get().floatValue();
        return isBroken(drill) ? speed * BROKEN_SPEED_FACTOR : speed;
    }

    public static boolean isCorrectFor(ItemStack drill, BlockState state) {
        if (!state.requiresCorrectToolForDrops()) {
            return true;
        }
        return drill.getItem() instanceof DrillItem item && !state.is(item.tier.miningTier().getIncorrectBlocksForDrops());
    }

    /** One point of wear per mined block. Unbreaking applies. */
    public static void wear(ItemStack drill, ServerLevel level) {
        if (!drill.isDamageableItem() || isBroken(drill)) {
            return;
        }
        int amount = EnchantmentHelper.processDurabilityChange(level, drill, 1);
        drill.setDamageValue(Math.min(drill.getMaxDamage(), drill.getDamageValue() + amount));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int left = stack.getMaxDamage() - stack.getDamageValue();
        tooltip.add(Component.translatable("tooltip.drillships.durability", left, stack.getMaxDamage()).withStyle(ChatFormatting.GRAY));
        if (isBroken(stack)) {
            tooltip.add(Component.translatable("tooltip.drillships.drill_broken").withStyle(ChatFormatting.RED));
        }
    }
}
