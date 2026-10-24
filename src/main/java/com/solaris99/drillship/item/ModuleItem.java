package com.solaris99.drillship.item;

import java.util.List;
import java.util.function.Supplier;

import com.solaris99.drillship.block.ModuleBlock;
import com.solaris99.drillship.registry.ModBlocks;
import com.solaris99.drillship.ship.ShipModules;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/** A module, installed by right-clicking a free block of a ship's hull from the inside (see ShipModules). */
public class ModuleItem extends Item {
    public enum Placement {
        /** A side or end wall block. */
        WALL,
        /** Two wall blocks, one above the other. */
        DOOR,
        /** A floor or ceiling block. */
        HATCH,
        /** A ceiling block; one per section (the tesla turrets). */
        CEILING
    }

    private final Supplier<? extends ModuleBlock> block;
    private final Placement placement;

    public ModuleItem(Supplier<? extends ModuleBlock> block, Placement placement, Properties properties) {
        super(properties);
        this.block = block;
        this.placement = placement;
    }

    public ModuleBlock block() {
        return block.get();
    }

    public Placement placement() {
        return placement;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return ModBlocks.isShell(context.getLevel().getBlockState(context.getClickedPos())) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        Component error = ShipModules.install(level, context.getClickedPos(), this);
        if (error != null) {
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(error, true);
            }
            return InteractionResult.FAIL;
        }
        context.getItemInHand().consume(1, context.getPlayer());
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.drillships." + BuiltInRegistries.ITEM.getKey(this).getPath()).withStyle(ChatFormatting.GRAY));
        String how = switch (placement) {
            case HATCH -> "tooltip.drillships.module_hatch";
            case CEILING -> "tooltip.drillships.module_ceiling";
            default -> "tooltip.drillships.module";
        };
        tooltip.add(Component.translatable(how).withStyle(ChatFormatting.DARK_GRAY));
    }
}
