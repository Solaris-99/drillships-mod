package com.solaris99.drillship.ship;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.item.DrillshipItem;
import com.solaris99.drillship.registry.ModKeys;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

/** Moving players between the interior, the exterior and the driver's seat. */
public final class ShipTravel {
    public static void enterShip(ServerPlayer player, ShipState ship) {
        enterSection(player, ship, 0);
    }

    /** Section 0 is the drillhead, 1..n are wagons. */
    public static void enterSection(ServerPlayer player, ShipState ship, int section) {
        ServerLevel interior = player.server.getLevel(ModKeys.INTERIOR);
        if (interior == null) {
            return;
        }
        Vec3 point = section == 0 ? ship.layout().entryPoint() : ship.layout().room(section).entryPoint();
        player.teleportTo(interior, point.x, point.y, point.z, 0.0F, 0.0F);
    }

    public static void returnToHelm(ServerPlayer player, ShipState ship) {
        ServerLevel interior = player.server.getLevel(ModKeys.INTERIOR);
        if (interior == null) {
            return;
        }
        Vec3 point = ship.layout().helmPoint();
        player.teleportTo(interior, point.x, point.y, point.z, 0.0F, 0.0F);
    }

    public static void startDriving(ServerPlayer player, ShipState ship) {
        DrillshipEntity entity = ship.findEntity(player.server);
        if (entity == null) {
            player.displayClientMessage(Component.translatable("message.drillships.unreachable"), true);
            return;
        }
        if (entity.isVehicle()) {
            player.displayClientMessage(Component.translatable("message.drillships.occupied"), true);
            return;
        }
        player.teleportTo((ServerLevel) entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), 0.0F);
        player.startRiding(entity, true);
    }

    public static void exitShip(ServerPlayer player, ShipState ship) {
        Vec3 exit = findExit(player, ship);
        ServerLevel level = player.server.getLevel(ship.dimension());
        if (exit == null || level == null) {
            player.displayClientMessage(Component.translatable("message.drillships.exit_blocked"), true);
            return;
        }
        player.teleportTo(level, exit.x, exit.y, exit.z, ship.yaw(), 0.0F);
    }

    /** Beside, behind or on top of the ship, whichever has room for the player. */
    @Nullable
    private static Vec3 findExit(ServerPlayer player, ShipState ship) {
        ServerLevel level = player.server.getLevel(ship.dimension());
        if (level == null) {
            return null;
        }
        ShipShape shape = ShipShape.drillhead(ship.tier());
        ShipShape.Frame frame = ShipShape.Frame.of(ship.position(), ship.yaw(), 0.0F);
        double side = shape.halfWidth + 0.8;
        List<Vec3> candidates = new ArrayList<>(List.of(frame.toWorld(-side, 0, 0), frame.toWorld(side, 0, 0)));
        // Behind the drillhead is the first wagon, if there is one.
        if (ship.wagonCount() == 0) {
            candidates.add(frame.toWorld(0, 0, shape.back - 0.8));
        }
        candidates.add(frame.toWorld(0, shape.height + 0.05, 0));
        for (Vec3 candidate : candidates) {
            if (level.noCollision(null, player.getDimensions(Pose.STANDING).makeBoundingBox(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    /** Turns an empty ship back into an item. The player must be inside its interior. */
    public static void pickUp(ServerPlayer player, ShipState ship) {
        ServerLevel interior = player.server.getLevel(ModKeys.INTERIOR);
        if (interior == null) {
            return;
        }
        if (ship.wagonCount() > 0) {
            player.displayClientMessage(Component.translatable("message.drillships.has_wagons"), true);
            return;
        }
        if (!ship.hasNoItems() || !Interiors.isEmpty(interior, ship.layout().room(0), player)) {
            player.displayClientMessage(Component.translatable("message.drillships.not_empty"), true);
            return;
        }
        Vec3 exit = findExit(player, ship);
        ServerLevel level = player.server.getLevel(ship.dimension());
        if (exit == null || level == null) {
            player.displayClientMessage(Component.translatable("message.drillships.exit_blocked"), true);
            return;
        }
        player.closeContainer();
        player.teleportTo(level, exit.x, exit.y, exit.z, ship.yaw(), 0.0F);
        dismantle(player.server, ship);
        player.getInventory().placeItemBackInInventory(DrillshipItem.create(ship.tier(), ship.drill().copy()));
    }

    /** Removes a ship from the world entirely: exterior, interior, chunk tickets and saved state. */
    public static void dismantle(MinecraftServer server, ShipState ship) {
        DrillshipEntity entity = ship.findEntity(server);
        if (entity != null) {
            entity.ejectPassengers();
            entity.discard();
        }
        for (int wagon = ship.wagonCount(); wagon >= 1; wagon--) {
            ShipWagons.removeExterior(server, ship, wagon);
        }
        ChunkLoading.release(server, ship);
        ServerLevel interior = server.getLevel(ModKeys.INTERIOR);
        if (interior != null) {
            Interiors.clear(interior, ship.layout());
        }
        ShipManager.get(server).remove(ship);
    }

    private ShipTravel() {
    }
}
