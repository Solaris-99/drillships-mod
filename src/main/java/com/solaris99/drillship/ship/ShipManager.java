package com.solaris99.drillship.ship;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/** All drillships on the server, saved with the overworld. */
public class ShipManager extends SavedData {
    private static final String NAME = "drillships_ships";

    private final Map<UUID, ShipState> ships = new LinkedHashMap<>();
    private final Map<Integer, ShipState> byInterior = new HashMap<>();
    private final Deque<Integer> freeInteriors = new ArrayDeque<>();
    private int nextInterior;

    public static ShipManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(ShipManager::new, ShipManager::load), NAME);
    }

    public ShipState create(ShipTier tier, ResourceKey<Level> dimension, Vec3 position, float yaw) {
        int interior = freeInteriors.isEmpty() ? nextInterior++ : freeInteriors.poll();
        ShipState ship = new ShipState(this, UUID.randomUUID(), tier, interior, dimension, position, yaw);
        add(ship);
        setDirty();
        return ship;
    }

    public void remove(ShipState ship) {
        ships.remove(ship.id());
        byInterior.remove(ship.interiorIndex());
        freeInteriors.add(ship.interiorIndex());
        setDirty();
    }

    private void add(ShipState ship) {
        ships.put(ship.id(), ship);
        byInterior.put(ship.interiorIndex(), ship);
    }

    @Nullable
    public ShipState get(UUID id) {
        return ships.get(id);
    }

    /** The ship whose interior contains this position in the interior dimension. */
    @Nullable
    public ShipState byInteriorPos(BlockPos pos) {
        ShipState ship = byInterior.get(InteriorLayout.indexOf(pos));
        return ship != null && ship.layout().contains(pos) ? ship : null;
    }

    /** A snapshot, so ships can be removed while iterating. */
    public Collection<ShipState> ships() {
        return List.copyOf(ships.values());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ShipState ship : ships.values()) {
            list.add(ship.save(registries));
        }
        tag.put("Ships", list);
        tag.putInt("NextInterior", nextInterior);
        tag.putIntArray("FreeInteriors", new ArrayList<>(freeInteriors));
        return tag;
    }

    private static ShipManager load(CompoundTag tag, HolderLookup.Provider registries) {
        ShipManager manager = new ShipManager();
        for (Tag entry : tag.getList("Ships", Tag.TAG_COMPOUND)) {
            manager.add(ShipState.load(manager, (CompoundTag) entry, registries));
        }
        manager.nextInterior = tag.getInt("NextInterior");
        if (tag.get("FreeInteriors") instanceof IntArrayTag free) {
            for (int index : free.getAsIntArray()) {
                manager.freeInteriors.add(index);
            }
        }
        return manager;
    }
}
