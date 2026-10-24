package com.solaris99.drillship.event;

import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.block.ModuleBlock;
import com.solaris99.drillship.item.DrillItem;
import com.solaris99.drillship.registry.ModTags;
import com.solaris99.drillship.ship.ShipManager;
import com.solaris99.drillship.ship.ShipModules;
import com.solaris99.drillship.ship.ShipState;
import com.solaris99.drillship.ship.ShipTicker;
import com.solaris99.drillship.ship.ShipTravel;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class CommonEvents {
    // Set on players who log out while driving, so they log back in at the helm.
    private static final String RETURN_TAG = "drillships:return_to_helm";

    public static void register(IEventBus bus) {
        bus.addListener(CommonEvents::onMount);
        bus.addListener(CommonEvents::onLogout);
        bus.addListener(CommonEvents::onLogin);
        bus.addListener(CommonEvents::onDamage);
        bus.addListener(CommonEvents::onRightClickBlock);
        bus.addListener(CommonEvents::onLeftClickBlock);
        bus.addListener(CommonEvents::onAnvilUpdate);
    }

    private static void onMount(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityMounting() instanceof ServerPlayer player
                && event.getEntityBeingMounted() instanceof DrillshipEntity ship) {
            ShipTicker.scheduleReturn(player, ship.getUUID());
        }
    }

    // Vanilla would otherwise save the ship into the player's file and remove it from the world.
    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getVehicle() instanceof DrillshipEntity ship) {
            player.getPersistentData().putUUID(RETURN_TAG, ship.getUUID());
            player.stopRiding();
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (!data.hasUUID(RETURN_TAG)) {
            return;
        }
        ShipState ship = ShipManager.get(player.server).get(data.getUUID(RETURN_TAG));
        data.remove(RETURN_TAG);
        if (ship != null) {
            ShipTravel.returnToHelm(player, ship);
        }
    }

    private static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().getVehicle() instanceof DrillshipEntity) {
            event.setCanceled(true);
        }
    }

    // The driver is looking through the periscope and can't reach the world. Shift + right-click with any wrench takes
    // a module out of the hull; vanilla would skip the block's own handling while sneaking with an item.
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().getVehicle() instanceof DrillshipEntity) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity().isSecondaryUseActive() && event.getItemStack().is(ModTags.WRENCHES)
                && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof ModuleBlock) {
            if (event.getLevel() instanceof ServerLevel level) {
                ShipModules.remove(level, event.getPos(), event.getEntity());
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        }
    }

    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity().getVehicle() instanceof DrillshipEntity) {
            event.setCanceled(true);
        }
    }

    /** Each block of the drill's primary material restores 10% durability. */
    private static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (!(left.getItem() instanceof DrillItem drill) || !right.is(drill.tier().repairBlock()) || !left.isDamaged()) {
            return;
        }
        int perBlock = Math.max(1, left.getMaxDamage() / 10);
        int used = Math.min(right.getCount(), Mth.ceil(left.getDamageValue() / (float) perBlock));
        ItemStack output = left.copy();
        output.setDamageValue(Math.max(0, left.getDamageValue() - used * perBlock));
        String name = event.getName();
        if (name != null && !name.isBlank() && !name.equals(left.getHoverName().getString())) {
            output.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
        event.setOutput(output);
        event.setCost(used);
        event.setMaterialCost(used);
    }

    private CommonEvents() {
    }
}
