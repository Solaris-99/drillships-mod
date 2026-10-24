package com.solaris99.drillship.ship;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.entity.ShipSectionEntity;
import com.solaris99.drillship.item.DrillshipItem;
import com.solaris99.drillship.item.ShipLocatorItem;
import com.solaris99.drillship.registry.ModKeys;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Moving players between the interior, the exterior and the driver's seat. */
public final class ShipTravel {
    public static void enterShip(ServerPlayer player, ShipState ship) {
        enterSection(player, ship, 0);
    }

    /** Section 0 is the drillhead, 1..n are wagons. The player comes in through the section's door. */
    public static void enterSection(ServerPlayer player, ShipState ship, int section) {
        ServerLevel interior = player.server.getLevel(ModKeys.INTERIOR);
        if (interior == null) {
            return;
        }
        openDoor(player.server, ship, section);
        ShipLocatorItem.remember(player, ship);
        Vec3 point = ship.layout().doorEntry(section);
        // Facing into the room, away from the door in the min-X wall.
        player.teleportTo(interior, point.x, point.y, point.z, -90.0F, 0.0F);
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
        player.teleportTo((ServerLevel) entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
        player.startRiding(entity, true);
        ShipLocatorItem.remember(player, ship);
    }

    /** Leaves through a section's door: the player steps out beside it, facing away from the ship. */
    public static void exitSection(ServerPlayer player, ShipState ship, int section) {
        Vec3 exit = findExit(player, ship, section);
        ServerLevel level = player.server.getLevel(ship.dimension());
        if (exit == null || level == null) {
            player.displayClientMessage(Component.translatable("message.drillships.exit_blocked"), true);
            return;
        }
        float yaw = section == 0 ? ship.yaw() : ship.wagonPose(section).yaw();
        player.teleportTo(level, exit.x, exit.y, exit.z, yaw + 90.0F, 0.0F);
        openDoor(player.server, ship, section);
    }

    /**
     * Outside the section's door (on its right, third block from the back), standing on the ground or on the track.
     * If rock is in the way the door cuts a way out, as long as those blocks may be broken; failing that the other
     * side, behind the last section, or on top. Only blocks count: the ship's own collision is what we step off.
     */
    @Nullable
    private static Vec3 findExit(ServerPlayer player, ShipState ship, int section) {
        ServerLevel level = player.server.getLevel(ship.dimension());
        if (level == null) {
            return null;
        }
        ShipShape shape = shape(ship, section);
        ShipShape.Frame frame = exitFrame(ship, section);
        double side = shape.halfWidth + 0.8, door = shape.back + 2.5;
        Vec3 atDoor = frame.toWorld(side, 0, door);
        for (Vec3 candidate : List.of(atDoor, frame.toWorld(side, 1, door))) {
            if (level.noBlockCollision(null, standing(player, candidate))) {
                return candidate;
            }
        }
        if (clearDoorway(player, ship, level, standing(player, atDoor))) {
            return atDoor;
        }
        List<Vec3> candidates = new ArrayList<>(List.of(frame.toWorld(-side, 0, door), frame.toWorld(-side, 1, door)));
        // Behind a section is the next wagon, if there is one.
        if (section == ship.wagonCount()) {
            candidates.add(frame.toWorld(0, 0, shape.back - ship.tier().coupling().gap() - 0.8));
        }
        candidates.add(frame.toWorld(0, shape.height + 0.05, door));
        for (Vec3 candidate : candidates) {
            if (level.noBlockCollision(null, standing(player, candidate))) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Leaves through a door or hatch module at {@code pos} (a door's lower half): out of the hull beside a door module,
     * onto the roof above a ceiling hatch, or under the ship below a floor hatch. Blocks in the way are cut as at the
     * section's door. Failing that, or where a door module would open onto the next section, the player leaves through
     * the section's own door.
     */
    public static void exitThroughModule(ServerPlayer player, ShipState ship, BlockPos pos) {
        int section = ship.layout().sectionAt(pos);
        ServerLevel level = player.server.getLevel(ship.dimension());
        if (section < 0 || level == null) {
            return;
        }
        InteriorLayout.Room room = ship.layout().room(section);
        ShipShape shape = shape(ship, section);
        ShipShape.Frame frame = exitFrame(ship, section);
        int x = pos.getX() - room.origin().getX(), y = pos.getY() - room.origin().getY(), z = pos.getZ() - room.origin().getZ();
        // The interior's min-X wall is the exterior's right side, where the section's own door is.
        double across = shape.halfWidth - (x + 0.5), along = shape.back + z + 0.5;
        float yaw = section == 0 ? ship.yaw() : ship.wagonPose(section).yaw();
        List<Vec3> candidates = new ArrayList<>();
        if (y == 0) {
            candidates.add(frame.toWorld(across, -player.getDimensions(Pose.STANDING).height() - 0.05, along));
        } else if (y == room.sizeY() - 1) {
            candidates.add(frame.toWorld(across, shape.height + 0.05, along));
        } else if (x == 0 || x == room.sizeX() - 1) {
            double side = (x == 0 ? 1 : -1) * (shape.halfWidth + 0.8);
            candidates.add(frame.toWorld(side, y - 1, along));
            candidates.add(frame.toWorld(side, y, along));
            yaw += x == 0 ? 90.0F : -90.0F;
        } else if (z == 0 && section == ship.wagonCount()) {
            candidates.add(frame.toWorld(across, y - 1, shape.back - 0.8));
            candidates.add(frame.toWorld(across, y, shape.back - 0.8));
            yaw += 180.0F;
        } else if (z == room.sizeZ() - 1 && section == 0) {
            candidates.add(frame.toWorld(across, y - 1, shape.drillFront + 0.8));
            candidates.add(frame.toWorld(across, y, shape.drillFront + 0.8));
        }
        Vec3 exit = null;
        for (Vec3 candidate : candidates) {
            if (level.noBlockCollision(null, standing(player, candidate))) {
                exit = candidate;
                break;
            }
        }
        if (exit == null && !candidates.isEmpty() && clearDoorway(player, ship, level, standing(player, candidates.get(0)))) {
            exit = candidates.get(0);
        }
        if (exit == null) {
            exitSection(player, ship, section);
            return;
        }
        player.teleportTo(level, exit.x, exit.y, exit.z, yaw, 0.0F);
    }

    private static ShipShape shape(ShipState ship, int section) {
        return section == 0 ? ShipShape.drillhead(ship.tier()) : ShipShape.wagon(ship.tier());
    }

    // Level, whatever the section's pitch.
    private static ShipShape.Frame exitFrame(ShipState ship, int section) {
        return section == 0
                ? ShipShape.Frame.of(ship.position(), ship.yaw(), 0.0F)
                : ShipShape.Frame.of(ship.wagonPose(section).position(), ship.wagonPose(section).yaw(), 0.0F);
    }

    private static AABB standing(ServerPlayer player, Vec3 feet) {
        return player.getDimensions(Pose.STANDING).makeBoundingBox(feet);
    }

    /**
     * Cuts the blocks in the way of someone stepping out of a door, like the drill would: drops go to the ship's
     * inventory. Refuses (and breaks nothing) if any of them can't be broken: unbreakable, holding items, or protected.
     */
    private static boolean clearDoorway(ServerPlayer player, ShipState ship, ServerLevel level, AABB box) {
        List<BlockPos> solid = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState state = level.getBlockState(pos);
            if (state.getCollisionShape(level, pos).isEmpty()) {
                continue;
            }
            if (state.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null || !level.mayInteract(player, pos)
                    || NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled()) {
                return false;
            }
            solid.add(pos.immutable());
        }
        ItemStack tool = ship.drill().isEmpty() ? ItemStack.EMPTY : ship.drill();
        for (BlockPos pos : solid) {
            BlockState state = level.getBlockState(pos);
            for (ItemStack drop : Block.getDrops(state, level, pos, null, player, tool)) {
                ItemStack left = ship.store(drop);
                if (!left.isEmpty()) {
                    Block.popResource(level, pos, left);
                }
            }
            level.destroyBlock(pos, false, player);
        }
        return true;
    }

    /** Slides the section's exterior door open for a moment, if the exterior is loaded. */
    private static void openDoor(MinecraftServer server, ShipState ship, int section) {
        ShipSectionEntity entity = section == 0 ? ship.findEntity(server) : ShipWagons.findEntity(server, ship, section);
        if (entity != null) {
            entity.openDoor();
        }
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
        if (ShipModules.hasModules(interior, ship.layout().room(0))) {
            player.displayClientMessage(Component.translatable("message.drillships.has_modules"), true);
            return;
        }
        if (!ship.hasNoItems() || !Interiors.isEmpty(interior, ship.layout().room(0), player)) {
            player.displayClientMessage(Component.translatable("message.drillships.not_empty"), true);
            return;
        }
        Vec3 exit = findExit(player, ship, 0);
        ServerLevel level = player.server.getLevel(ship.dimension());
        if (exit == null || level == null) {
            player.displayClientMessage(Component.translatable("message.drillships.exit_blocked"), true);
            return;
        }
        player.closeContainer();
        player.teleportTo(level, exit.x, exit.y, exit.z, ship.yaw(), 0.0F);
        List<ItemStack> upgrades = ship.takeUpgrades();
        dismantle(player.server, ship);
        player.getInventory().placeItemBackInInventory(DrillshipItem.create(ship.tier(), ship.drill().copy()));
        upgrades.forEach(player.getInventory()::placeItemBackInInventory);
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
