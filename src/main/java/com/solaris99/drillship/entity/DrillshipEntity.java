package com.solaris99.drillship.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import com.solaris99.drillship.Config;
import com.solaris99.drillship.item.DrillItem;
import com.solaris99.drillship.network.ModNetwork;
import com.solaris99.drillship.registry.ModEntities;
import com.solaris99.drillship.registry.ModKeys;
import com.solaris99.drillship.ship.ShipJoints;
import com.solaris99.drillship.ship.ShipManager;
import com.solaris99.drillship.ship.ShipRadar;
import com.solaris99.drillship.ship.ShipShape;
import com.solaris99.drillship.ship.ShipState;
import com.solaris99.drillship.ship.ShipTravel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The drillhead as seen from the outside world. It drives, drills and lights the way; wagons follow its path. Movement
 * is server-authoritative; the driver's client only sends input.
 */
public class DrillshipEntity extends ShipSectionEntity {
    private static final EntityDataAccessor<Boolean> DATA_DRILL_ON = SynchedEntityData.defineId(DrillshipEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_DRILL_WORN = SynchedEntityData.defineId(DrillshipEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_MOVING = SynchedEntityData.defineId(DrillshipEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ENGINE_ON = SynchedEntityData.defineId(DrillshipEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_WARNING = SynchedEntityData.defineId(DrillshipEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BURNING = SynchedEntityData.defineId(DrillshipEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CUTTING = SynchedEntityData.defineId(DrillshipEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_UPGRADES = SynchedEntityData.defineId(DrillshipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RADIO = SynchedEntityData.defineId(DrillshipEntity.class, EntityDataSerializers.INT);

    private static final double ACCELERATION = 0.02;
    private static final double REVERSE_FACTOR = 0.25;
    private static final double GRAVITY = 0.08;
    private static final double MAX_FALL_SPEED = 2.0;
    // Falls faster than this (blocks per tick) land with a thud and dust; stepping down a block doesn't.
    private static final double LANDING_EFFECT_SPEED = 0.35;
    // Levelling out, in degrees per tick: it starts slowly and speeds up, like a falling body.
    private static final float LEVEL_ACCELERATION = 0.25F;
    private static final float MAX_LEVEL_SPEED = 4.0F;
    private static final float TURN_RATE = 2.0F;
    private static final float PITCH_RATE = 1.0F;
    private static final float MAX_PITCH = 30.0F;
    // How far the driver may look away from the ship's heading while it moves.
    private static final float DRIVER_LOOK_LIMIT = 70.0F;
    // How far behind the hull's front face the driver's eye sits. Blocks being drilled have their centers ahead of the
    // hull, so they can reach at most sqrt(3)/2 into it: from here the eye is never inside one.
    private static final double DRIVER_EYE_SETBACK = 1.0;
    private static final double[] STEP_FRACTIONS = {1.0, 0.5, 0.25};
    private static final int HUD_UPDATE_TICKS = 5;
    // Per tick: blocks broken with their sound and dust, and further ones with just dust.
    private static final int BREAK_EFFECTS_PER_TICK = 2;
    private static final int BREAK_DUST_PER_TICK = 10;

    // Server
    private float inputForward;
    private float inputLeft;
    private boolean inputUp;
    private boolean inputDown;
    private double speed;
    private double fallSpeed;
    private float levelSpeed;
    private final Map<BlockPos, MiningProgress> mining = new HashMap<>();
    private final Set<BlockPos> blockers = new HashSet<>();
    // Server side: the radar shown on the driver's HUD.
    private final ShipRadar radar = new ShipRadar();
    private int nextBreakerSlot;
    private int breakEffectsLeft;
    private int breakDustLeft;
    // The drill bit into at least one block this tick.
    private boolean cutting;

    // Client
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
    }

    public static DrillshipEntity create(ServerLevel level, ShipState ship) {
        DrillshipEntity entity = new DrillshipEntity(ModEntities.DRILLSHIP.get(), level);
        entity.setUUID(ship.id());
        entity.setTier(ship.tier());
        entity.moveTo(ship.position().x, ship.position().y, ship.position().z, ship.yaw(), ship.pitch());
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DRILL_ON, false);
        builder.define(DATA_DRILL_WORN, false);
        builder.define(DATA_MOVING, false);
        builder.define(DATA_ENGINE_ON, false);
        builder.define(DATA_WARNING, false);
        builder.define(DATA_BURNING, false);
        builder.define(DATA_CUTTING, false);
        builder.define(DATA_UPGRADES, 0);
        builder.define(DATA_RADIO, 0);
    }

    @Override
    public ShipShape shape() {
        return ShipShape.drillhead(tier());
    }

    // The rendered drill's cone sticks out past the drill face by less than half the hull's width.
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(shape().halfWidth);
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

    /** The engine runs while someone is driving and the ship has energy. The headlights are on while it runs. */
    public boolean isEngineOn() {
        return entityData.get(DATA_ENGINE_ON);
    }

    /** A warning is lit on the status panel; the roof beacon flashes. */
    public boolean isWarning() {
        return entityData.get(DATA_WARNING);
    }

    /** The generator is burning fuel: the exhaust stack smokes. */
    public boolean isBurning() {
        return entityData.get(DATA_BURNING);
    }

    /** The drill is biting into blocks right now, not just spinning. */
    public boolean isCutting() {
        return entityData.get(DATA_CUTTING);
    }

    /** Installed upgrades, as ShipUpgrade bits: they show on the exterior. */
    public int upgrades() {
        return entityData.get(DATA_UPGRADES);
    }

    /** The radio's station (RadioStation ordinal), 0 for off: the exterior plays it too. */
    public int radioStation() {
        return entityData.get(DATA_RADIO);
    }

    public float getDrillAngle(float partialTick) {
        return Mth.lerp(partialTick, drillAngleO, drillAngle);
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
            tickLerp();
            drillAngleO = drillAngle;
            if (isDrillSpinning()) {
                drillAngle += 25.0F;
            }
            ShipEffects.drillhead(this);
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

    private void tickServer(ServerLevel level, ShipState ship) {
        ServerPlayer driver = getDriver();
        if (driver == null) {
            setInput(0, 0, false, false);
        }
        ShipShape shape = shape();
        blockers.clear();
        breakEffectsLeft = BREAK_EFFECTS_PER_TICK;
        breakDustLeft = BREAK_DUST_PER_TICK;
        cutting = false;

        boolean drilling = ship.isDrillOn() && !ship.drill().isEmpty()
                && ship.consumeEnergy(shape.faceArea() * Config.DRILL_ENERGY_PER_BLOCK.get());

        updateSpeed(ship);
        Vec3 start = position();
        rotate(level, ship, shape);
        move(level, ship, shape, drilling);
        applyPhysics(level, ship, shape);

        if (drilling) {
            mine(level, ship, shape, driver);
            hurtEntitiesInDrill(level, shape);
        } else {
            clearMining(level);
        }
        Vec3 displacement = position().subtract(start);
        if (displacement.lengthSqr() > 1.0E-6) {
            pushEntities(level);
        }

        boolean engineOn = driver != null && ship.energy() > 0;
        updateHeadlight(level, ship, engineOn);

        ItemStack drill = ship.drill();
        entityData.set(DATA_DRILL_ON, drilling);
        entityData.set(DATA_DRILL_WORN, !drill.isEmpty() && DrillItem.isWorn(drill));
        entityData.set(DATA_MOVING, Math.abs(speed) > 1.0E-3);
        entityData.set(DATA_ENGINE_ON, engineOn);
        entityData.set(DATA_WARNING, ship.warnings(level.getGameTime()) != 0);
        entityData.set(DATA_BURNING, ship.isBurning());
        entityData.set(DATA_CUTTING, cutting);
        entityData.set(DATA_UPGRADES, ship.upgradeMask());
        entityData.set(DATA_RADIO, ship.radioStation());
        ship.updatePose(level.dimension(), position(), getYRot(), getXRot(), displacement.length() * 20.0);

        // Clients without the mod (or test players) never negotiated the channel.
        if (driver != null && tickCount % HUD_UPDATE_TICKS == 0 && driver.connection.hasChannel(ModNetwork.ShipStatus.TYPE)) {
            PacketDistributor.sendToPlayer(driver, ModNetwork.ShipStatus.of(ship, level.getGameTime()));
        }
        if (driver != null) {
            radar.tick(level, ship, driver, level.getGameTime());
        }
    }

    private void updateSpeed(ShipState ship) {
        double max = ship.topSpeed() / 20.0;
        double acceleration = ACCELERATION * ship.accelerationFactor();
        if (inputForward > 0) {
            speed = Math.min(max, speed + acceleration);
        } else if (inputForward < 0) {
            speed = Math.max(-max * REVERSE_FACTOR, speed - acceleration);
        } else {
            speed = Math.signum(speed) * Math.max(0, Math.abs(speed) - acceleration);
        }
        if (speed != 0 && !ship.consumeEnergy(ship.moveEnergy())) {
            speed = 0;
        }
    }

    private void rotate(ServerLevel level, ShipState ship, ShipShape shape) {
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
        // The whole turn, else each axis alone: steering still works when only the pitch would bend a joint too far.
        for (float[] pose : new float[][] {{yaw, pitch}, {yaw, getXRot()}, {getYRot(), pitch}}) {
            if (pose[0] == getYRot() && pose[1] == getXRot()) {
                continue;
            }
            ShipShape.Frame frame = ShipShape.Frame.of(position(), pose[0], pose[1]);
            if (ShipJoints.headBlocked(ship, frame(), frame)) {
                ship.markBlocked(level.getGameTime());
                continue;
            }
            if (isClear(level, frame, shape, true)) {
                setPose(position(), pose[0], pose[1]);
                return;
            }
        }
    }

    private void move(ServerLevel level, ShipState ship, ShipShape shape, boolean drilling) {
        if (Math.abs(speed) < 1.0E-4) {
            return;
        }
        Vec3 step = frame().forward().scale(speed);
        for (double fraction : STEP_FRACTIONS) {
            Vec3 target = position().add(step.scale(fraction));
            if (isClear(level, ShipShape.Frame.of(target, getYRot(), getXRot()), shape, true)) {
                moveTrain(level, ship, target);
                return;
            }
        }
        // Climb 1-block steps when the drill is off and only the bottom layer is in the way.
        if (!drilling && onlyBottomLayerBlocked()) {
            Vec3 target = position().add(0, 1, 0).add(step);
            if (isClear(level, ShipShape.Frame.of(target, getYRot(), getXRot()), shape, false)) {
                moveTrain(level, ship, target);
            }
        }
    }

    // Driving never bends a joint so far that two sections cross; the driver has to straighten out first.
    private void moveTrain(ServerLevel level, ShipState ship, Vec3 target) {
        if (ShipJoints.trainBlocked(ship, target, getYRot(), getXRot())) {
            ship.markBlocked(level.getGameTime());
            return;
        }
        setPos(target);
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

    // Physics. What holds the ship up is whatever is under its tracks, or its drill biting into terrain; with nothing
    // at all it falls. Out in the open (the drill biting into nothing) and left alone by the driver, a tilted ship
    // levels out onto its tracks: coming out of a tunnel on the surface it doesn't stay pointing at the sky, it drops
    // its nose and settles. While its drill is in the ground it keeps whatever angle it was drilling at. A powered
    // antigravity engine turns all of this off: the ship stays where it is driven, at whatever angle it is pitched.

    /** The rear-most and front-most points (local z) the tracks rest on, and whether the drill is in terrain. */
    private record Support(double back, double front, boolean drillInTerrain) {
    }

    @Nullable
    private Support support(ServerLevel level, ShipShape shape) {
        ShipShape.Frame frame = frame();
        double back = Double.MAX_VALUE, front = -Double.MAX_VALUE;
        for (BlockPos pos : frame.blocksIn(shape.below())) {
            if (isSolid(level, pos)) {
                double z = frame.toLocal(Vec3.atCenterOf(pos)).z;
                back = Math.min(back, Math.max(shape.back, z - 0.5));
                front = Math.max(front, Math.min(shape.drillFront, z + 0.5));
            }
        }
        boolean drillInTerrain = false;
        for (BlockPos pos : frame.blocksIn(shape.drillZone())) {
            if (isSolid(level, pos)) {
                drillInTerrain = true;
                break;
            }
        }
        if (front < back) {
            return drillInTerrain ? new Support(shape.drillFront, shape.drillFront, true) : null;
        }
        return new Support(back, front, drillInTerrain);
    }

    private void applyPhysics(ServerLevel level, ShipState ship, ShipShape shape) {
        if (ship.isFloating()) {
            fallSpeed = 0;
            levelSpeed = 0;
            return;
        }
        Support support = support(level, shape);
        if (support == null) {
            levelSpeed = 0;
            fall(level, shape);
            return;
        }
        fallSpeed = 0;
        if (support.drillInTerrain() || inputUp || inputDown || Math.abs(getXRot()) < 1.0E-3F) {
            levelSpeed = 0;
            return;
        }
        levelOut(level, ship, shape, support);
    }

    /**
     * Turns the ship a step towards level, hinged on its front-most support: the nose drops (lifting the back off the
     * lip of a tunnel) or the back comes down (after landing nose first). Failing that, on its rear-most support or its
     * middle. Where none of them is free (resting on a slope, still inside the tunnel) it stays as it is.
     */
    private void levelOut(ServerLevel level, ShipState ship, ShipShape shape, Support support) {
        levelSpeed = Math.min(MAX_LEVEL_SPEED, levelSpeed + LEVEL_ACCELERATION);
        float pitch = getXRot() > 0 ? Math.max(0, getXRot() - levelSpeed) : Math.min(0, getXRot() + levelSpeed);
        for (double pivot : new double[] {support.front(), support.back(), 0}) {
            Vec3 hinge = frame().toWorld(0, 0, pivot);
            Vec3 origin = hinge.subtract(ShipShape.Frame.of(Vec3.ZERO, getYRot(), pitch).forward().scale(pivot));
            ShipShape.Frame frame = ShipShape.Frame.of(origin, getYRot(), pitch);
            if (isClear(level, frame, shape, false) && !ShipJoints.headBlocked(ship, frame(), frame)) {
                setPose(origin, getYRot(), pitch);
                if (pitch == 0) {
                    land(level, shape);
                }
                return;
            }
        }
        levelSpeed = 0;
    }

    private void fall(ServerLevel level, ShipShape shape) {
        fallSpeed = Math.min(MAX_FALL_SPEED, fallSpeed + GRAVITY);
        double remaining = fallSpeed;
        while (remaining > 0) {
            double drop = Math.min(0.25, remaining);
            Vec3 target = position().add(0, -drop, 0);
            if (!isClear(level, ShipShape.Frame.of(target, getYRot(), getXRot()), shape, false)) {
                touchDown(level, shape);
                return;
            }
            setPos(target);
            if (support(level, shape) != null) {
                touchDown(level, shape);
                return;
            }
            remaining -= drop;
        }
    }

    // A real drop (not a step down) lands with a thud and a puff of dust from the ground.
    private void touchDown(ServerLevel level, ShipShape shape) {
        if (fallSpeed > LANDING_EFFECT_SPEED) {
            float weight = (float) shape.halfWidth / 2.5F;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.35F, 0.55F / weight);
            BlockState ground = level.getBlockState(BlockPos.containing(getX(), getY() - 0.5, getZ()));
            if (!ground.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.1, getZ(),
                        (int) (30 * weight), shape.halfWidth, 0.1, shape.hullFront, 0.15);
            }
        }
        land(level, shape);
    }

    // A level landing snaps to the block grid; a tilted one levels out from there.
    private void land(ServerLevel level, ShipShape shape) {
        fallSpeed = 0;
        if (Math.abs(getXRot()) > 0.5F) {
            return;
        }
        Vec3 snapped = new Vec3(getX(), Math.round(getY()), getZ());
        if (isClear(level, ShipShape.Frame.of(snapped, getYRot(), getXRot()), shape, false)) {
            setPos(snapped);
        }
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

    // Mining

    private void mine(ServerLevel level, ShipState ship, ShipShape shape, @Nullable ServerPlayer driver) {
        Set<BlockPos> targets = new HashSet<>(blockers);
        for (BlockPos pos : frame().blocksIn(shape.drillZone())) {
            if (isMineable(level.getBlockState(pos))) {
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
            if (!isMineable(state)) {
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
            cutting = true;
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

    // Fluids flow back in, and light blocks are invisible (and may be our own headlight).
    private static boolean isMineable(BlockState state) {
        return !state.isAir() && !(state.getBlock() instanceof LiquidBlock) && !(state.getBlock() instanceof LightBlock);
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
        // A big drill can break a hundred blocks in one tick; each playing its own break sound would exhaust the sound
        // engine. Only a few per tick get the full effect, a few more just their dust, the rest go quietly.
        if (breakEffectsLeft > 0) {
            breakEffectsLeft--;
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        } else if (breakDustLeft > 0) {
            breakDustLeft--;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    6, 0.3, 0.3, 0.3, 0.05);
        }
        // Containers spill their contents here, through the block's onRemove.
        level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), Block.UPDATE_ALL);
        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(this, state));
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

    // Headlights: a single invisible light block that travels inside the front of the hull.

    private void updateHeadlight(ServerLevel level, ShipState ship, boolean on) {
        BlockPos target = null;
        if (on) {
            ShipShape shape = shape();
            target = BlockPos.containing(frame().toWorld(0, shape.height / 2, shape.hullFront - 1.0));
        }
        if (Objects.equals(target, ship.headlight())) {
            return;
        }
        removeHeadlight(level, ship);
        if (target != null && placeLight(level, target)) {
            ship.setHeadlight(target);
        }
    }

    private static boolean placeLight(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LightBlock.MAX_LEVEL);
        if (state.isAir()) {
            return level.setBlock(pos, light, Block.UPDATE_ALL);
        }
        if (state.is(Blocks.WATER) && state.getFluidState().isSource()) {
            return level.setBlock(pos, light.setValue(BlockStateProperties.WATERLOGGED, true), Block.UPDATE_ALL);
        }
        return false;
    }

    /** Removes the light block, if it is still there. Does nothing while the chunk is unloading. */
    public static void removeHeadlight(ServerLevel level, ShipState ship) {
        BlockPos pos = ship.headlight();
        if (pos == null || !level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.LIGHT)) {
            boolean water = state.getValue(BlockStateProperties.WATERLOGGED);
            level.setBlock(pos, water ? Fluids.WATER.defaultFluidState().createLegacyBlock() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        ship.setHeadlight(null);
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

    // Passengers: the driver sits at the front on the drill's axis, looking through the periscope. The view turns and
    // pitches with the ship, so the crosshair stays where the drill is heading.

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
        Vec3 eye = frame().toWorld(0, shape.height * 0.5, shape.hullFront - DRIVER_EYE_SETBACK);
        callback.accept(passenger, eye.x, eye.y - passenger.getEyeHeight(), eye.z);
        float turned = Mth.wrapDegrees(getYRot() - yRotO);
        if (turned != 0) {
            passenger.setYRot(passenger.getYRot() + turned);
            passenger.setYHeadRot(passenger.getYHeadRot() + turned);
        }
        float pitched = getXRot() - xRotO;
        if (pitched != 0) {
            passenger.setXRot(Mth.clamp(passenger.getXRot() + pitched, -90.0F, 90.0F));
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

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel level) {
            clearMining(level);
            ShipState ship = ShipManager.get(level.getServer()).get(getUUID());
            if (ship != null) {
                removeHeadlight(level, ship);
            }
        }
        super.remove(reason);
    }
}
