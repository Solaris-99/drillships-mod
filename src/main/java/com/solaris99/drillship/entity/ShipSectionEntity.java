package com.solaris99.drillship.entity;

import java.util.List;

import com.solaris99.drillship.Config;
import com.solaris99.drillship.registry.ModKeys;
import com.solaris99.drillship.ship.ShipShape;
import com.solaris99.drillship.ship.ShipTier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One section of a drillship as seen from the outside: the drillhead or a wagon. Sections hold no persistent data
 * (see ShipState) and are respawned whenever their chunk loads. They are immune to everything.
 */
public abstract class ShipSectionEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(ShipSectionEntity.class, EntityDataSerializers.INT);

    // Client interpolation
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;

    protected ShipSectionEntity(EntityType<? extends ShipSectionEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TIER, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_TIER.equals(key)) {
            setBoundingBox(makeBoundingBox());
        }
    }

    protected void setTier(ShipTier tier) {
        entityData.set(DATA_TIER, tier.ordinal());
    }

    public ShipTier tier() {
        return ShipTier.byId(entityData.get(DATA_TIER));
    }

    public abstract ShipShape shape();

    public ShipShape.Frame frame() {
        return ShipShape.Frame.of(position(), getYRot(), getXRot());
    }

    @Override
    protected AABB makeBoundingBox() {
        return frame().bounds(shape().whole());
    }

    /** Moves and rotates the section, keeping its rotated bounding box up to date. */
    protected void setPose(Vec3 position, float yaw, float pitch) {
        setYRot(yaw);
        setXRot(pitch);
        setPos(position);
    }

    // Blocks

    // Unloaded chunks count as solid so a ship never loads chunks by itself.
    protected static boolean isSolid(ServerLevel level, BlockPos pos) {
        return !level.isLoaded(pos) || !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    public static boolean isSpaceClear(ServerLevel level, ShipShape.Frame frame, ShipShape shape) {
        for (BlockPos pos : frame.blocksIn(shape.whole())) {
            if (isSolid(level, pos)) {
                return false;
            }
        }
        return true;
    }

    // Entities

    /** Pushes entities out of the section's way, or squashes them if there is nowhere to go. */
    protected void pushEntities(ServerLevel level) {
        ShipShape shape = shape();
        ShipShape.Frame frame = frame();
        DamageSource squash = damageSource(level, ModKeys.SQUASH_DAMAGE);
        float damage = Config.SQUASH_DAMAGE.get().floatValue();
        List<Entity> entities = level.getEntities(this, frame.bounds(shape.whole()), this::isOutsider);
        for (Entity entity : entities) {
            double margin = entity.getBbWidth() / 2.0;
            Vec3 local = frame.toLocal(entity.getBoundingBox().getCenter());
            if (!shape.whole().inflate(margin).contains(local)) {
                continue;
            }
            Vec3 push = escapeVector(frame, shape, local, margin);
            if (level.noCollision(entity, entity.getBoundingBox().move(push))) {
                if (entity instanceof ServerPlayer) {
                    entity.setDeltaMovement(entity.getDeltaMovement().add(push));
                    entity.hurtMarked = true;
                } else {
                    entity.setPos(entity.position().add(push));
                }
            } else {
                entity.hurt(squash, damage);
            }
        }
    }

    // The shortest way out of the section's box: through a side, the front or the back.
    private static Vec3 escapeVector(ShipShape.Frame frame, ShipShape shape, Vec3 local, double margin) {
        double side = shape.halfWidth + margin - Math.abs(local.x) + 0.01;
        double front = shape.drillFront + margin - local.z + 0.01;
        double back = local.z - (shape.back - margin) + 0.01;
        if (side <= front && side <= back) {
            return frame.right().scale(Math.signum(local.x) * side);
        }
        return front <= back ? frame.forward().scale(front) : frame.forward().scale(-back);
    }

    /** Entities a section may push or hurt: anything but ship sections and their passengers. */
    protected boolean isOutsider(Entity entity) {
        return entity.isAlive() && !entity.isSpectator() && !(entity instanceof ShipSectionEntity)
                && !(entity.getVehicle() instanceof ShipSectionEntity);
    }

    protected DamageSource damageSource(ServerLevel level, ResourceKey<DamageType> type) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), this);
    }

    // Client interpolation

    protected void tickLerp() {
        if (lerpSteps > 0) {
            lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, lerpYRot, lerpXRot);
            lerpSteps--;
            setBoundingBox(makeBoundingBox());
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = steps;
    }

    @Override
    public double lerpTargetX() {
        return lerpSteps > 0 ? lerpX : getX();
    }

    @Override
    public double lerpTargetY() {
        return lerpSteps > 0 ? lerpY : getY();
    }

    @Override
    public double lerpTargetZ() {
        return lerpSteps > 0 ? lerpZ : getZ();
    }

    @Override
    public float lerpTargetXRot() {
        return lerpSteps > 0 ? (float) lerpXRot : getXRot();
    }

    @Override
    public float lerpTargetYRot() {
        return lerpSteps > 0 ? (float) lerpYRot : getYRot();
    }

    // Ships have no health for now: immune to everything.

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public void kill() {
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // Nothing to save: see ShipState.

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
