package com.solaris99.drillship.ship;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.solaris99.drillship.block.ModuleBlock;
import com.solaris99.drillship.block.ModuleDoorBlock;
import com.solaris99.drillship.block.TeslaTurretBlock;
import com.solaris99.drillship.block.entity.ModuleBlockEntity;
import com.solaris99.drillship.item.ModuleItem;
import com.solaris99.drillship.registry.ModBlocks;
import com.solaris99.drillship.registry.ModKeys;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Installing modules in a ship's hull and taking them out. A module replaces a free shell block of a room, drillhead or
 * wagon: a side or end wall block (plating, kick plate or pipes, not the corner beams), or for the hatch a floor or
 * ceiling block away from the walls, and for a tesla turret a ceiling block of a section that has none yet. Taking it out
 * with a wrench puts that shell block back.
 */
public final class ShipModules {
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    /** Installs a module at {@code pos}. Returns why it can't go there, or null. */
    @Nullable
    public static Component install(ServerLevel level, BlockPos pos, ModuleItem item) {
        Component cantGoHere = Component.translatable("message.drillships.module_cant_go_here");
        Place place = place(level, pos);
        if (place == null) {
            return cantGoHere;
        }
        InteriorLayout.Room room = place.room();
        BlockState module = item.block().defaultBlockState().setValue(ModuleBlock.FACING, intoRoom(room, pos));
        switch (item.placement()) {
            case WALL -> {
                if (!isFreeWall(level, room, pos)) {
                    return cantGoHere;
                }
                level.setBlock(pos, module, FLAGS);
            }
            case HATCH -> {
                if (!isFreeFloorOrCeiling(level, room, pos)) {
                    return cantGoHere;
                }
                level.setBlock(pos, module, FLAGS);
            }
            case CEILING -> {
                if (!isFreeFloorOrCeiling(level, room, pos) || !level.getBlockState(pos).is(ModBlocks.HULL_CEILING.get())) {
                    return cantGoHere;
                }
                if (place.ship().turret(place.section()) != null) {
                    return Component.translatable("message.drillships.one_turret");
                }
                level.setBlock(pos, module, FLAGS);
                place.ship().setTurret(place.section(), ((TeslaTurretBlock) item.block()).turret());
            }
            case DOOR -> {
                BlockPos lower = isFreeWall(level, room, pos.above()) ? pos : pos.below();
                if (!isFreeWall(level, room, pos) || !isFreeWall(level, room, lower) || !isFreeWall(level, room, lower.above())) {
                    return cantGoHere;
                }
                level.setBlock(lower, module.setValue(ModuleDoorBlock.HALF, DoubleBlockHalf.LOWER), FLAGS);
                level.setBlock(lower.above(), module.setValue(ModuleDoorBlock.HALF, DoubleBlockHalf.UPPER), FLAGS);
            }
        }
        level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        return null;
    }

    /** Takes out the module at {@code pos}, giving the player the module and whatever it held. Returns false if there is none. */
    public static boolean remove(ServerLevel level, BlockPos pos, Player player) {
        BlockState state = level.getBlockState(pos);
        Place place = place(level, pos);
        if (!(state.getBlock() instanceof ModuleBlock module) || place == null) {
            return false;
        }
        InteriorLayout.Room room = place.room();
        if (module instanceof TeslaTurretBlock) {
            place.ship().setTurret(place.section(), null);
        }
        List<ItemStack> returned = new ArrayList<>();
        returned.add(new ItemStack(BuiltInRegistries.ITEM.get(BuiltInRegistries.BLOCK.getKey(module))));
        if (level.getBlockEntity(pos) instanceof ModuleBlockEntity entity) {
            entity.contents().forEach(stack -> returned.add(stack.copy()));
        }
        List<BlockPos> blocks = module instanceof ModuleDoorBlock door ? List.of(pos, door.otherHalf(state, pos)) : List.of(pos);
        for (BlockPos block : blocks) {
            level.setBlock(block, Interiors.shell(room, block), FLAGS);
        }
        returned.stream().filter(stack -> !stack.isEmpty()).forEach(player.getInventory()::placeItemBackInInventory);
        level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /** Whether any module is installed in the room's shell. */
    public static boolean hasModules(ServerLevel level, InteriorLayout.Room room) {
        for (BlockPos pos : BlockPos.betweenClosed(room.origin(), room.maxCorner())) {
            if (room.isWall(pos) && level.getBlockState(pos).getBlock() instanceof ModuleBlock) {
                return true;
            }
        }
        return false;
    }

    // From a shell block towards the inside of its room.
    private static Direction intoRoom(InteriorLayout.Room room, BlockPos pos) {
        int x = pos.getX() - room.origin().getX(), y = pos.getY() - room.origin().getY(), z = pos.getZ() - room.origin().getZ();
        if (y == 0) {
            return Direction.UP;
        }
        if (y == room.sizeY() - 1) {
            return Direction.DOWN;
        }
        if (x == 0) {
            return Direction.EAST;
        }
        if (x == room.sizeX() - 1) {
            return Direction.WEST;
        }
        return z == 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private record Place(ShipState ship, int section, InteriorLayout.Room room) {
    }

    // The attached section whose room contains pos.
    @Nullable
    private static Place place(ServerLevel level, BlockPos pos) {
        ShipState ship = level.dimension() == ModKeys.INTERIOR ? ShipManager.get(level.getServer()).byInteriorPos(pos) : null;
        int section = ship == null ? -1 : ship.layout().sectionAt(pos);
        return section < 0 || section > ship.wagonCount() ? null : new Place(ship, section, ship.layout().room(section));
    }

    private static boolean isFreeWall(ServerLevel level, InteriorLayout.Room room, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return room.contains(pos) && room.isWall(pos)
                && (state.is(ModBlocks.HULL.get()) || state.is(ModBlocks.HULL_SKIRT.get()) || state.is(ModBlocks.HULL_CONDUIT.get()));
    }

    private static boolean isFreeFloorOrCeiling(ServerLevel level, InteriorLayout.Room room, BlockPos pos) {
        int x = pos.getX() - room.origin().getX(), z = pos.getZ() - room.origin().getZ();
        BlockState state = level.getBlockState(pos);
        return room.contains(pos) && x > 0 && x < room.sizeX() - 1 && z > 0 && z < room.sizeZ() - 1
                && (state.is(ModBlocks.HULL_FLOOR.get()) || state.is(ModBlocks.HULL_CEILING.get()));
    }

    private ShipModules() {
    }
}
