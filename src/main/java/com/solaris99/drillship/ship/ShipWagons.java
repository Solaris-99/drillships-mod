package com.solaris99.drillship.ship;

import javax.annotation.Nullable;

import com.solaris99.drillship.entity.ShipSectionEntity;
import com.solaris99.drillship.entity.WagonEntity;
import com.solaris99.drillship.item.WagonItem;
import com.solaris99.drillship.registry.ModKeys;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Attaching wagons to a ship and detaching them. Only the last wagon can be detached, so the chain never has gaps. */
public final class ShipWagons {
    /** Attaches a wagon behind the last section. Returns why it failed, or null on success. */
    @Nullable
    public static Component attach(MinecraftServer server, ShipState ship, ItemStack wagonItem) {
        if (!(wagonItem.getItem() instanceof WagonItem item) || item.tier() != ship.tier()) {
            return Component.translatable("message.drillships.wrong_wagon_tier");
        }
        if (ship.wagonCount() >= ship.tier().maxWagons()) {
            return Component.translatable("message.drillships.max_wagons", ship.tier().maxWagons());
        }
        ServerLevel level = server.getLevel(ship.dimension());
        ServerLevel interior = server.getLevel(ModKeys.INTERIOR);
        if (level == null || interior == null) {
            return Component.translatable("message.drillships.unreachable");
        }
        int wagon = ship.wagonCount() + 1;
        if (!ShipSectionEntity.isSpaceClear(level, ship.wagonPose(wagon).frame(), ShipShape.wagon(ship.tier()))) {
            return Component.translatable("message.drillships.wagon_blocked");
        }
        Interiors.buildWagon(interior, ship.layout(), wagon);
        couplingSound(level, ship, wagon, SoundEvents.CHAIN_PLACE);
        ship.setWagonCount(wagon);
        // The exterior is spawned by ShipTicker on the next tick, like any section whose entity is missing.
        return null;
    }

    /** Detaches the last wagon if its room is empty. Returns why it failed, or null on success. */
    @Nullable
    public static Component detachLast(ServerPlayer player, ShipState ship) {
        int wagon = ship.wagonCount();
        if (wagon == 0) {
            return Component.translatable("message.drillships.no_wagons");
        }
        ServerLevel interior = player.server.getLevel(ModKeys.INTERIOR);
        if (interior == null) {
            return Component.translatable("message.drillships.unreachable");
        }
        if (ShipModules.hasModules(interior, ship.layout().room(wagon))) {
            return Component.translatable("message.drillships.wagon_has_modules");
        }
        if (!Interiors.isWagonEmpty(interior, ship.layout(), wagon, player)) {
            return Component.translatable("message.drillships.wagon_not_empty");
        }
        ServerLevel level = player.server.getLevel(ship.dimension());
        if (level != null) {
            couplingSound(level, ship, wagon, SoundEvents.CHAIN_BREAK);
        }
        removeExterior(player.server, ship, wagon);
        Interiors.removeWagon(interior, ship.layout(), wagon);
        ship.setWagonCount(wagon - 1);
        player.getInventory().placeItemBackInInventory(WagonItem.create(ship.tier()));
        return null;
    }

    public static void removeExterior(MinecraftServer server, ShipState ship, int wagon) {
        WagonEntity entity = findEntity(server, ship, wagon);
        if (entity != null) {
            entity.discard();
        }
    }

    @Nullable
    public static WagonEntity findEntity(MinecraftServer server, ShipState ship, int wagon) {
        ServerLevel level = server.getLevel(ship.dimension());
        return level != null && level.getEntity(ship.wagonId(wagon)) instanceof WagonEntity entity ? entity : null;
    }

    // At the coupling in front of the wagon, half way up.
    private static void couplingSound(ServerLevel level, ShipState ship, int wagon, SoundEvent sound) {
        ShipState.Pose pose = ship.wagonPose(wagon);
        ShipShape shape = ShipShape.wagon(ship.tier());
        Vec3 coupling = pose.frame().toWorld(0, shape.height / 2, shape.hullFront);
        level.playSound(null, coupling.x, coupling.y, coupling.z, sound, SoundSource.NEUTRAL, 1.0F, 0.8F);
    }

    private ShipWagons() {
    }
}
