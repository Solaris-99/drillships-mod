package com.solaris99.drillship.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.solaris99.drillship.registry.ModEntities;
import com.solaris99.drillship.ship.ShipManager;
import com.solaris99.drillship.ship.ShipShape;
import com.solaris99.drillship.ship.ShipState;
import com.solaris99.drillship.ship.ShipTravel;
import com.solaris99.drillship.ship.ShipWagons;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A wagon's exterior. It doesn't move by itself: every tick it takes the pose ShipState computes from the drillhead's
 * path, like a train car following the locomotive. Wagons ride through the tunnel the drillhead dug, so they don't
 * check for blocks.
 */
public class WagonEntity extends ShipSectionEntity {
    // The section in front (the drillhead or the previous wagon), so the client can draw the bellows between them.
    private static final EntityDataAccessor<Integer> DATA_LEADER = SynchedEntityData.defineId(WagonEntity.class, EntityDataSerializers.INT);

    // Server only: which ship and which wagon this is. Not needed on the client.
    private UUID shipId;
    private int wagon;

    public WagonEntity(EntityType<? extends WagonEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LEADER, -1);
    }

    /** The section this wagon is coupled behind, if the client has it. */
    @Nullable
    public ShipSectionEntity leader() {
        return level().getEntity(entityData.get(DATA_LEADER)) instanceof ShipSectionEntity leader ? leader : null;
    }

    public static WagonEntity create(ServerLevel level, ShipState ship, int wagon) {
        WagonEntity entity = new WagonEntity(ModEntities.WAGON.get(), level);
        entity.setUUID(ship.wagonId(wagon));
        entity.shipId = ship.id();
        entity.wagon = wagon;
        entity.setTier(ship.tier());
        ShipState.Pose pose = ship.wagonPose(wagon);
        entity.moveTo(pose.position().x, pose.position().y, pose.position().z, pose.yaw(), pose.pitch());
        return entity;
    }

    @Override
    public ShipShape shape() {
        return ShipShape.wagon(tier());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            tickLerp();
            ShipEffects.trackDust(this);
            return;
        }
        ShipState ship = shipId == null ? null : ShipManager.get(((ServerLevel) level()).getServer()).get(shipId);
        if (ship == null || wagon > ship.wagonCount()) {
            discard();
            return;
        }
        MinecraftServer server = ((ServerLevel) level()).getServer();
        Entity leader = wagon == 1 ? ship.findEntity(server) : ShipWagons.findEntity(server, ship, wagon - 1);
        entityData.set(DATA_LEADER, leader != null ? leader.getId() : -1);
        Vec3 start = position();
        setTurret(ship.turret(wagon));
        ShipState.Pose pose = ship.wagonPose(wagon);
        setPose(pose.position(), pose.yaw(), pose.pitch());
        if (position().distanceToSqr(start) > 1.0E-6) {
            pushEntities((ServerLevel) level());
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer && shipId != null) {
            ShipState ship = ShipManager.get(serverPlayer.server).get(shipId);
            if (ship != null) {
                ShipTravel.enterSection(serverPlayer, ship, wagon);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }
}
