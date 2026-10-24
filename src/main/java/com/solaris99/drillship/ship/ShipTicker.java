package com.solaris99.drillship.ship;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.solaris99.drillship.block.ShipInteriorBlock;
import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.entity.WagonEntity;
import com.solaris99.drillship.registry.ModKeys;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Per-tick work for every ship, whether or not its exterior entity is loaded. */
public final class ShipTicker {
    // Drivers who dismounted and should be sent back to the helm next tick.
    private static final List<PendingReturn> PENDING_RETURNS = new ArrayList<>();

    private record PendingReturn(ServerPlayer player, UUID shipId) {
    }

    public static void register(IEventBus bus) {
        bus.addListener(ShipTicker::onServerTick);
        bus.addListener(ShipTicker::onServerStopped);
    }

    public static void scheduleReturn(ServerPlayer player, UUID shipId) {
        PENDING_RETURNS.add(new PendingReturn(player, shipId));
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ShipManager manager = ShipManager.get(server);
        long gameTime = server.overworld().getGameTime();
        for (ShipState ship : manager.ships()) {
            ship.tickGenerator();
            ensureEntity(server, ship);
            ChunkLoading.update(server, ship);
            updateIndicators(server, ship, gameTime);
        }
        processReturns(server, manager);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        ChunkLoading.clear();
        PENDING_RETURNS.clear();
    }

    /** Respawns each section's exterior whenever its chunk is loaded and the entity is missing. */
    private static void ensureEntity(MinecraftServer server, ShipState ship) {
        ServerLevel level = server.getLevel(ship.dimension());
        if (level == null) {
            return;
        }
        if (level.getEntity(ship.id()) == null && level.isPositionEntityTicking(BlockPos.containing(ship.position()))) {
            level.addFreshEntity(DrillshipEntity.create(level, ship));
        }
        for (int wagon = 1; wagon <= ship.wagonCount(); wagon++) {
            if (level.getEntity(ship.wagonId(wagon)) == null
                    && level.isPositionEntityTicking(BlockPos.containing(ship.wagonPose(wagon).position()))) {
                level.addFreshEntity(WagonEntity.create(level, ship, wagon));
            }
        }
    }

    // The status panel lights up on warnings, the generator lights up while burning, and the module panels show their
    // modules and the radio's station.
    private static void updateIndicators(MinecraftServer server, ShipState ship, long gameTime) {
        ServerLevel interior = server.getLevel(ModKeys.INTERIOR);
        if (interior == null) {
            return;
        }
        InteriorLayout layout = ship.layout();
        setActive(interior, layout.statusPanel(), ship.warnings(gameTime) != 0);
        setActive(interior, layout.generator(), ship.isBurning());
        if (ship.panelsChanged() && Interiors.showModules(interior, layout, ship)) {
            ship.clearPanelsChanged();
        }
    }

    private static void setActive(ServerLevel level, BlockPos pos, boolean active) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(ShipInteriorBlock.ACTIVE) && state.getValue(ShipInteriorBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(ShipInteriorBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    private static void processReturns(MinecraftServer server, ShipManager manager) {
        if (PENDING_RETURNS.isEmpty()) {
            return;
        }
        List<PendingReturn> returns = List.copyOf(PENDING_RETURNS);
        PENDING_RETURNS.clear();
        for (PendingReturn pending : returns) {
            ServerPlayer player = pending.player();
            ShipState ship = manager.get(pending.shipId());
            // Skip players who left, got back in, or were teleported away (e.g. by a command) while dismounting.
            if (ship == null || player.hasDisconnected() || player.isPassenger()
                    || player.level().dimension() != ship.dimension()
                    || player.position().distanceToSqr(ship.position()) > 32 * 32) {
                continue;
            }
            ShipTravel.returnToHelm(player, ship);
        }
    }

    private ShipTicker() {
    }
}
