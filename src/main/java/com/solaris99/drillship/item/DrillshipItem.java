package com.solaris99.drillship.item;

import java.util.List;

import javax.annotation.Nullable;

import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.registry.ModComponents;
import com.solaris99.drillship.registry.ModItems;
import com.solaris99.drillship.registry.ModKeys;
import com.solaris99.drillship.ship.Interiors;
import com.solaris99.drillship.ship.ShipManager;
import com.solaris99.drillship.ship.ShipShape;
import com.solaris99.drillship.ship.ShipState;
import com.solaris99.drillship.ship.ShipTier;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** Places a drillhead. The ship appears in front of the clicked block, facing the player's direction. */
public class DrillshipItem extends Item {
    private final ShipTier tier;

    public DrillshipItem(ShipTier tier, Properties properties) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public static ItemStack create(ShipTier tier, ItemStack drill) {
        ItemStack stack = new ItemStack(ModItems.drillship(tier));
        stack.set(ModComponents.INSTALLED_DRILL.get(), ItemContainerContents.fromItems(List.of(drill)));
        return stack;
    }

    private ItemStack installedDrill(ItemStack stack) {
        ItemContainerContents contents = stack.get(ModComponents.INSTALLED_DRILL.get());
        return contents == null ? ModItems.newDrill(tier) : contents.copyOne();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (level.dimension() == ModKeys.INTERIOR) {
            message(player, "message.drillships.cannot_place_inside");
            return InteractionResult.FAIL;
        }
        // Snap to the nearest cardinal direction so new tunnels start straight.
        float yaw = player != null ? Math.round(player.getYRot() / 90.0F) * 90.0F : 0.0F;
        BlockPos base = context.getClickedPos().relative(context.getClickedFace());
        Vec3 forward = ShipShape.Frame.of(Vec3.ZERO, yaw, 0.0F).forward();
        Vec3 origin = Vec3.atBottomCenterOf(base).add(forward.scale(-ShipShape.drillhead(tier).back));
        if (place(level, tier, origin, yaw, installedDrill(context.getItemInHand())) == null) {
            message(player, "message.drillships.no_space");
            return InteractionResult.FAIL;
        }
        context.getItemInHand().consume(1, player);
        return InteractionResult.CONSUME;
    }

    /** Creates a ship with its interior and exterior. Returns null if the ship doesn't fit at {@code origin}. */
    @Nullable
    public static ShipState place(ServerLevel level, ShipTier tier, Vec3 origin, float yaw, ItemStack drill) {
        ServerLevel interior = level.getServer().getLevel(ModKeys.INTERIOR);
        if (interior == null || !DrillshipEntity.isSpaceClear(level, ShipShape.Frame.of(origin, yaw, 0.0F), ShipShape.drillhead(tier))) {
            return null;
        }
        ShipState ship = ShipManager.get(level.getServer()).create(tier, level.dimension(), origin, yaw);
        ship.drillSlot().setStackInSlot(0, drill);
        Interiors.build(interior, ship.layout());
        level.addFreshEntity(DrillshipEntity.create(level, ship));
        return ship;
    }

    private static void message(Player player, String key) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ItemContainerContents contents = stack.get(ModComponents.INSTALLED_DRILL.get());
        if (contents != null) {
            ItemStack drill = contents.copyOne();
            tooltip.add(drill.isEmpty()
                    ? Component.translatable("tooltip.drillships.no_drill").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("tooltip.drillships.installed_drill", drill.getHoverName()).withStyle(ChatFormatting.GRAY));
        }
    }
}
