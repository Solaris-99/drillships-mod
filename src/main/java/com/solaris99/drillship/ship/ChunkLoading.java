package com.solaris99.drillship.ship;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.solaris99.drillship.Config;
import com.solaris99.drillship.Drillships;
import com.solaris99.drillship.registry.ModKeys;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/** Keeps each ship's surroundings and interior loaded. Tickets are recreated every session from ShipManager. */
public final class ChunkLoading {
    // Saved tickets are dropped on load; ShipTicker re-forces what is still needed.
    private static final TicketController TICKETS = new TicketController(Drillships.id("ships"),
            (level, helper) -> Set.copyOf(helper.getEntityTickets().keySet()).forEach(helper::removeAllTickets));
    // A ship's interior and exterior use different ticket owners so they can live in different dimensions.
    private static final Map<UUID, Forced> EXTERIOR = new HashMap<>();
    private static final Map<UUID, Forced> INTERIOR = new HashMap<>();

    private record Forced(ResourceKey<Level> dimension, Set<Long> chunks) {
    }

    public static void registerControllers(RegisterTicketControllersEvent event) {
        event.register(TICKETS);
    }

    public static void update(MinecraftServer server, ShipState ship) {
        if (!Config.CHUNK_LOADING.get()) {
            release(server, ship);
            return;
        }
        AABB around = ShipShape.Frame.of(ship.position(), ship.yaw(), ship.pitch())
                .bounds(new ShipShape(ship.tier()).whole())
                .inflate(16);
        apply(server, EXTERIOR, ship.id(), ship.dimension(), chunksIn(around));
        if (!INTERIOR.containsKey(interiorOwner(ship))) {
            apply(server, INTERIOR, interiorOwner(ship), ModKeys.INTERIOR, chunksIn(ship.layout().bounds()));
        }
    }

    public static void release(MinecraftServer server, ShipState ship) {
        apply(server, EXTERIOR, ship.id(), ship.dimension(), Set.of());
        apply(server, INTERIOR, interiorOwner(ship), ModKeys.INTERIOR, Set.of());
    }

    public static void clear() {
        EXTERIOR.clear();
        INTERIOR.clear();
    }

    private static UUID interiorOwner(ShipState ship) {
        return new UUID(ship.id().getMostSignificantBits(), ~ship.id().getLeastSignificantBits());
    }

    private static Set<Long> chunksIn(AABB box) {
        Set<Long> chunks = new HashSet<>();
        for (int x = Mth.floor(box.minX) >> 4; x <= Mth.floor(box.maxX) >> 4; x++) {
            for (int z = Mth.floor(box.minZ) >> 4; z <= Mth.floor(box.maxZ) >> 4; z++) {
                chunks.add(ChunkPos.asLong(x, z));
            }
        }
        return chunks;
    }

    private static void apply(MinecraftServer server, Map<UUID, Forced> forced, UUID owner, ResourceKey<Level> dimension, Set<Long> wanted) {
        Forced current = forced.get(owner);
        if (current != null && current.dimension().equals(dimension) && current.chunks().equals(wanted)) {
            return;
        }
        if (current != null) {
            ServerLevel level = server.getLevel(current.dimension());
            if (level != null) {
                for (long chunk : current.chunks()) {
                    if (!current.dimension().equals(dimension) || !wanted.contains(chunk)) {
                        TICKETS.forceChunk(level, owner, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false, true);
                    }
                }
            }
        }
        ServerLevel level = server.getLevel(dimension);
        if (level == null || wanted.isEmpty()) {
            forced.remove(owner);
            return;
        }
        for (long chunk : wanted) {
            if (current == null || !current.dimension().equals(dimension) || !current.chunks().contains(chunk)) {
                TICKETS.forceChunk(level, owner, ChunkPos.getX(chunk), ChunkPos.getZ(chunk), true, true);
            }
        }
        forced.put(owner, new Forced(dimension, Set.copyOf(wanted)));
    }

    private ChunkLoading() {
    }
}
