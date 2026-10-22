package com.solaris99.drillship.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.solaris99.drillship.Config;
import com.solaris99.drillship.item.DrillItem;
import com.solaris99.drillship.registry.ModEntities;
import com.solaris99.drillship.registry.ModKeys;
import com.solaris99.drillship.ship.ShipManager;
import com.solaris99.drillship.ship.ShipShape;
import com.solaris99.drillship.ship.ShipState;
import com.solaris99.drillship.ship.ShipTier;
import com.solaris99.drillship.ship.ShipTravel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The drillship as seen from the outside world. It holds no persistent data: {@link ShipState} is the source of truth
 * and the entity is respawned from it whenever its chunk loads. Movement is server-authoritative; the driver's client
 * only sends input.
 */
public class DrillshipEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(DrillshipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DRILL_ON = SynchedEntityData.defineId(DrillshipEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_DRILL_WORN = SynchedEntityData.defineId(DrillshipEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_MOVING = SynchedEntityData.defineId(DrillshipEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ENGINE_ON = SynchedEntityData.defineId(DrillshipEntity.class, EntityDataSerializers.BOOLEAN);

    private static final double ACCELERATION = 0.02;
    private static final double REVERSE_FACTOR = 0.25;
    private static final double GRAVITY = 0.08;
    private static final double MAX_FALL_SPEED = 2.0;
    private static final float TURN_RATE = 2.0F;
    private static final float PITCH_RATE = 1.0F;
    private static final float MAX_PITCH = 30.0F;
    // How far the driver may look away from the ship's heading while it moves.
    private static final float DRIVER_LOOK_LIMIT = 70.0F;
    private static final double[] STEP_FRACTIONS = {1.0, 0.5, 0.25};

    // Server
    private float inputForward;
    private float inputLeft;
    private boolean inputUp;
    private boolean inputDown;
    private double speed;
    private double fallSpeed;
    private final Map<BlockPos, MiningProgress> mining = new HashMap<>();
    private final Set<BlockPos> blockers = new HashSet<>();
    private int nextBreakerSlot;

    // Client
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;
    private float drillAngle;
    private float drillAngleO;

    private static final class MiningProgress {
        final int breakerId;
        float progress;
        int stage = -1;
        boolean denied;

        MiningProgress(int breakerId) {
            this.breakerId = breakerId;
        }
    }

    public DrillshipEntity(EntityType<? extends DrillshipEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    public static DrillshipEntity create(ServerLevel level, ShipState ship) {
        DrillshipEntity entity = new DrillshipEntity(ModEntities.DRILLSHIP.get(), level);
        entity.setUUID(ship.id());
        entity.entityData.set(DATA_TIER, ship.tier().ordinal());
        entity.moveTo(ship.position().x, ship.position().y, ship.position().z, ship.yaw(), ship.pitch());
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TIER, 0);
        builder.define(DATA_DRILL_ON, false);
        builder.define(DATA_DRILL_WORN, false);
        builder.define(DATA_MOVING, false);
        builder.define(DATA_ENGINE_ON, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_TIER.equals(key)) {
            setBoundingBox(makeBoundingBox());
        }
    }

    public ShipTier tier() {
        return ShipTier.byId(entityData.get(DATA_TIER));
    }

    public ShipShape shape() {
        return new ShipShape(tier());
    }

    public ShipShape.Frame frame() {
        return ShipShape.Frame.of(position(), getYRot(), getXRot());
    }

    public boolean isDrillSpinning() {
        return entityData.get(DATA_DRILL_ON);
    }

    public boolean isDrillWorn() {
        return entityData.get(DATA_DRILL_WORN);
    }

    public boolean isMoving() {
        return entityData.get(DATA_MOVING);
    }

    /** The engine runs while someone is driving and the ship has energy. */
    public boolean isEngineOn() {
        return entityData.get(DATA_ENGINE_ON);
    }

    public float getDrillAngle(float partialTick) {
        return Mth.lerp(partialTick, drillAngleO, drillAngle);
    }

    @Override
    protected AABB makeBoundingBox() {
        return frame().bounds(shape().whole());
    }

    // Input from the driver's client

    public void setInput(float forward, float left, boolean up, boolean down) {
        this.inputForward = Mth.clamp(forward, -1.0F, 1.0F);
        this.inputLeft = Mth.clamp(left, -1.0F, 1.0F);
        this.inputUp = up;
        this.inputDown = down;
    }

    @Nullable
    public ServerPlayer getDriver() {
        return getFirstPassenger() instanceof ServerPlayer player ? player : null;
    }

    // Ticking

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            tickClient();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        ShipState ship = ShipManager.get(level.getServer()).get(getUUID());
        if (ship == null) {
            discard();
            return;
        }
        tickServer(level, ship);
    }

    private void tickClient() {
        if (lerpSteps > 0) {
            lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, lerpYRot, lerpXRot);
            lerpSteps--;
            setBoundingBox(makeBoundingBox());
        }
        drillAngleO = drillAngle;
        if (isDrillSpinning()) {
            drillAngle += 25.0F;
        }
    }

    private void tickServer(ServerLevel level, ShipState ship) {
        ServerPlayer driver = getDriver();
        if (driver == null) {
            setInput(0, 0, false, false);
        }
        ShipShape shape = shape();
        blockers.clear();

        boolean drilling = ship.isDrillOn() && !ship.drill().isEmpty()
                && ship.consumeEnergy(shape.faceArea() * Config.DRILL_ENERGY_PER_BLOCK.get());

        updateSpeed(ship);
        Vec3 start = position();
        rotate(level, shape);
        move(level, shape, drilling);
        applyGravity(level, shape);

        if (drilling) {
            mine(level, ship, shape, driver);
            hurtEntitiesInDrill(level, shape);
        } else {
            clearMining(level);
        }
        Vec3 displacement = position().subtract(start);
        if (displacement.lengthSqr() > 1.0E-6) {
            pushEntities(level, shape);
        }

        ItemStack drill = ship.drill();
        entityData.set(DATA_DRILL_ON, drilling);
        entityData.set(DATA_DRILL_WORN, !drill.isEmpty() && DrillItem.isWorn(drill));
        entityData.set(DATA_MOVING, Math.abs(speed) > 1.0E-3);
        entityData.set(DATA_ENGINE_ON, driver != null && ship.energy() > 0);
        ship.updatePose(level.dimension(), position(), getYRot(), getXRot(), displacement.length() * 20.0);
    }

    private void updateSpeed(ShipState ship) {
        double max = ship.tier().topSpeed() / 20.0;
        if (inputForward > 0) {
            speed = Math.min(max, speed + ACCELERATION);
        } else if (inputForward < 0) {
            speed = Math.max(-max * REVERSE_FACTOR, speed - ACCELERATION);
        } else {
            speed = Math.signum(speed) * Math.max(0, Math.abs(speed) - ACCELERATION);
        }
        if (speed != 0 && !ship.consumeEnergy(Config.MOVE_ENERGY.get())) {
            speed = 0;
        }
    }

    private void rotate(ServerLevel level, ShipShape shape) {
        float yaw = getYRot() - inputLeft * TURN_RATE;
        float pitch = getXRot();
        if (inputUp) {
            pitch -= PITCH_RATE;
        }
        if (inputDown) {
            pitch += PITCH_RATE;
        }
        pitch = Mth.clamp(pitch, -MAX_PITCH, MAX_PITCH);
        if (yaw == getYRot() && pitch == getXRot()) {
            return;
        }
        if (isClear(level, ShipShape.Frame.of(position(), yaw, pitch), shape, true)) {
            setYRot(yaw);
            setXRot(pitch);
            setBoundingBox(makeBoundingBox());
        }
    }

    private void move(ServerLevel level, ShipShape shape, boolean drilling) {
        if (Math.abs(speed) < 1.0E-4) {
            return;
        }
        Vec3 step = frame().forward().scale(speed);
        for (double fraction : STEP_FRACTIONS) {
            Vec3 target = position().add(step.scale(fraction));
            if (isClear(level, ShipShape.Frame.of(target, getYRot(), getXRot()), shape, true)) {
                setPos(target);
                return;
            }
        }
        // Climb 1-block steps when the drill is off and only the bottom layer is in the way.
        if (!drilling && onlyBottomLayerBlocked()) {
            Vec3 target = position().add(0, 1, 0).add(step);
            if (isClear(level, ShipShape.Frame.of(target, getYRot(), getXRot()), shape, false)) {
                setPos(target);
            }
        }
    }

    private boolean onlyBottomLayerBlocked() {
        if (blockers.isEmpty()) {
            return false;
        }
        ShipShape.Frame frame = frame();
        for (BlockPos pos : blockers) {
            if (frame.toLocal(Vec3.atCenterOf(pos)).y >= 1.0) {
                return false;
            }
        }
        return true;
    }

    private void applyGravity(ServerLevel level, ShipShape shape) {
        if (isSupported(level, shape)) {
            fallSpeed = 0;
            return;
        }
        fallSpeed = Math.min(MAX_FALL_SPEED, fallSpeed + GRAVITY);
        double remaining = fallSpeed;
        while (remaining > 0) {
            double drop = Math.min(0.25, remaining);
            Vec3 target = position().add(0, -drop, 0);
            if (!isClear(level, ShipShape.Frame.of(target, getYRot(), getXRot()), shape, false)) {
                land(level, shape);
                return;
            }
            setPos(target);
            if (isSupported(level, shape)) {
                land(level, shape);
                return;
            }
            remaining -= drop;
        }
    }

    private void land(ServerLevel level, ShipShape shape) {
        fallSpeed = 0;
        Vec3 snapped = new Vec3(getX(), Math.round(getY()), getZ());
        if (isClear(level, ShipShape.Frame.of(snapped, getYRot(), getXRot()), shape, false)) {
            setPos(snapped);
        }
    }

    private boolean isSupported(ServerLevel level, ShipShape shape) {
        for (BlockPos pos : frame().blocksIn(shape.below())) {
            if (isSolid(level, pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if no solid block is inside the ship at this placement. With {@code remember}, the solid blocks found become
     * drill targets; gravity checks don't remember, so a drilling ship doesn't dig into the floor.
     */
    private boolean isClear(ServerLevel level, ShipShape.Frame frame, ShipShape shape, boolean remember) {
        boolean clear = true;
        for (BlockPos pos : frame.blocksIn(shape.whole())) {
            if (isSolid(level, pos)) {
                if (!remember) {
                    return false;
                }
                blockers.add(pos);
                clear = false;
            }
        }
        return clear;
    }

    // Unloaded chunks count as solid so the ship never loads chunks by itself.
    private static boolean isSolid(ServerLevel level, BlockPos pos) {
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

    // Mining

    private void mine(ServerLevel level, ShipState ship, ShipShape shape, @Nullable ServerPlayer driver) {
        Set<BlockPos> targets = new HashSet<>(blockers);
        for (BlockPos pos : frame().blocksIn(shape.drillZone())) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !(state.getBlock() instanceof LiquidBlock)) {
                targets.add(pos);
            }
        }
        forgetMiningExcept(level, targets);

        ItemStack drill = ship.drill();
        float drillSpeed = DrillItem.miningSpeed(drill, level);
        Player breaker = driver != null ? driver : FakePlayerFactory.getMinecraft(level);
        long gameTime = level.getGameTime();

        for (BlockPos pos : targets) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
                continue;
            }
            float hardness = state.getDestroySpeed(level, pos);
            if (hardness < 0 || (!Config.MINE_BLOCK_ENTITIES.get() && level.getBlockEntity(pos) != null)) {
                ship.markBlocked(gameTime);
                continue;
            }
            MiningProgress progress = mining.computeIfAbsent(pos, p -> new MiningProgress(nextBreakerId()));
            if (progress.denied) {
                ship.markBlocked(gameTime);
                continue;
            }
            boolean correctTier = DrillItem.isCorrectFor(drill, state);
            progress.progress += hardness == 0 ? 1.0F : drillSpeed / hardness / (correctTier ? 30.0F : 100.0F);
            if (progress.progress < 1.0F) {
                int stage = (int) (progress.progress * 10.0F);
                if (stage != progress.stage) {
                    progress.stage = stage;
                    level.destroyBlockProgress(progress.breakerId, pos, stage);
                }
                continue;
            }
            // Claims and spawn protection are only checked when the block is about to break.
            if (!level.mayInteract(breaker, pos) || NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, breaker)).isCanceled()) {
                progress.denied = true;
                ship.markBlocked(gameTime);
                continue;
            }
            breakBlock(level, ship, pos, state, drill, correctTier, breaker);
            level.destroyBlockProgress(progress.breakerId, pos, -1);
            mining.remove(pos);
        }
    }

    private void breakBlock(ServerLevel level, ShipState ship, BlockPos pos, BlockState state, ItemStack drill, boolean correctTier, Player breaker) {
        if (!DrillItem.isBroken(drill) && (correctTier || Config.DROP_WITHOUT_CORRECT_TIER.get())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            for (ItemStack stack : Block.getDrops(state, level, pos, blockEntity, breaker, drill)) {
                ItemStack left = ship.store(stack);
                if (!left.isEmpty()) {
                    ship.markInventoryFull(level.getGameTime());
                    if (!Config.VOID_WHEN_FULL.get()) {
                        Block.popResource(level, pos, left);
                    }
                }
            }
            state.spawnAfterBreak(level, pos, drill, true);
        }
        // Containers spill their contents here, through the block's onRemove.
        level.destroyBlock(pos, false, this);
        DrillItem.wear(drill, level);
        ship.markDirty();
    }

    private void forgetMiningExcept(ServerLevel level, Set<BlockPos> keep) {
        Iterator<Map.Entry<BlockPos, MiningProgress>> it = mining.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, MiningProgress> entry = it.next();
            if (!keep.contains(entry.getKey())) {
                level.destroyBlockProgress(entry.getValue().breakerId, entry.getKey(), -1);
                it.remove();
            }
        }
    }

    private void clearMining(ServerLevel level) {
        forgetMiningExcept(level, Set.of());
    }

    // Each crack overlay needs its own breaker id; negative ids never clash with real entities.
    private int nextBreakerId() {
        nextBreakerSlot = (nextBreakerSlot + 1) & 1023;
        return -1 - (getId() * 1024 + nextBreakerSlot);
    }

    // Entities

    private void hurtEntitiesInDrill(ServerLevel level, ShipShape shape) {
        ShipShape.Frame frame = frame();
        ShipShape.Box zone = shape.drillZone();
        DamageSource source = damageSource(level, ModKeys.DRILL_DAMAGE);
        float damage = Config.DRILL_DAMAGE.get().floatValue();
        for (Entity entity : level.getEntities(this, frame.bounds(zone), this::isOutsider)) {
            if (frame.contains(zone, entity.getBoundingBox().getCenter())) {
                entity.hurt(source, damage);
            }
        }
    }

    /** Pushes entities out of the ship's way, or squashes them if there is nowhere to go. */
    private void pushEntities(ServerLevel level, ShipShape shape) {
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

    // The shortest way out of the ship's box: through a side, the front or the back.
    private static Vec3 escapeVector(ShipShape.Frame frame, ShipShape shape, Vec3 local, double margin) {
        double side = shape.halfWidth + margin - Math.abs(local.x) + 0.01;
        double front = shape.drillFront + margin - local.z + 0.01;
        double back = local.z - (shape.back - margin) + 0.01;
        if (side <= front && side <= back) {
            return frame.right().scale(Math.signum(local.x) * side);
        }
        return front <= back ? frame.forward().scale(front) : frame.forward().scale(-back);
    }

    private boolean isOutsider(Entity entity) {
        return entity.isAlive() && !entity.isSpectator() && !hasPassenger(entity) && !(entity instanceof DrillshipEntity);
    }

    private DamageSource damageSource(ServerLevel level, ResourceKey<DamageType> type) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), this);
    }

    // Passengers: the driver sits at the front, looking through the periscope.

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        if (!hasPassenger(passenger)) {
            return;
        }
        ShipShape shape = shape();
        Vec3 eye = frame().toWorld(0, shape.height * 0.5, shape.hullFront - 0.3);
        callback.accept(passenger, eye.x, eye.y - passenger.getEyeHeight(), eye.z);
        float turned = Mth.wrapDegrees(getYRot() - yRotO);
        if (turned != 0) {
            passenger.setYRot(passenger.getYRot() + turned);
            passenger.setYHeadRot(passenger.getYHeadRot() + turned);
        }
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        if (!isMoving()) {
            return;
        }
        float offset = Mth.wrapDegrees(passenger.getYRot() - getYRot());
        float clamped = Mth.clamp(offset, -DRIVER_LOOK_LIMIT, DRIVER_LOOK_LIMIT);
        passenger.yRotO += clamped - offset;
        passenger.setYRot(passenger.getYRot() + clamped - offset);
        passenger.setYHeadRot(passenger.getYRot());
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hasPassenger(player)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            ShipState ship = ShipManager.get(serverPlayer.server).get(getUUID());
            if (ship != null) {
                ShipTravel.enterShip(serverPlayer, ship);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    // Client interpolation

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

    // The ship is immune to everything (spec: no health for now).

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

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel level) {
            clearMining(level);
        }
        super.remove(reason);
    }

    // Nothing to save: see ShipState.

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
