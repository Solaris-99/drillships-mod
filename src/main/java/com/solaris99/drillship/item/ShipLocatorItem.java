package com.solaris99.drillship.item;

import java.util.List;
import java.util.Objects;

import com.solaris99.drillship.registry.ModComponents;
import com.solaris99.drillship.ship.ShipManager;
import com.solaris99.drillship.ship.ShipState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A compass whose needle points to the drillhead of the last ship its holder entered. Like a compass in the Nether, it
 * spins in other dimensions (inside the ship among them). Which ship that was is kept on the player, through death.
 */
public class ShipLocatorItem extends Item {
    private static final String LAST_SHIP = "drillships:last_ship";
    private static final int UPDATE_TICKS = 20;

    public ShipLocatorItem(Properties properties) {
        super(properties);
    }

    public static void remember(Player player, ShipState ship) {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putUUID(LAST_SHIP, ship.id());
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof Player player) || level.getGameTime() % UPDATE_TICKS != 0) {
            return;
        }
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        ShipState ship = persisted.hasUUID(LAST_SHIP) ? ShipManager.get(server.getServer()).get(persisted.getUUID(LAST_SHIP)) : null;
        GlobalPos target = ship == null ? null : GlobalPos.of(ship.dimension(), BlockPos.containing(ship.position()));
        if (Objects.equals(target, stack.get(ModComponents.LOCATOR_TARGET.get()))) {
            return;
        }
        if (target == null) {
            stack.remove(ModComponents.LOCATOR_TARGET.get());
        } else {
            stack.set(ModComponents.LOCATOR_TARGET.get(), target);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.drillships.ship_locator").withStyle(ChatFormatting.GRAY));
    }
}
