package com.solaris99.drillship.block.entity;

import com.solaris99.drillship.block.TeslaTurretBlock;
import com.solaris99.drillship.entity.ShipSectionEntity;
import com.solaris99.drillship.registry.ModBlockEntities;
import com.solaris99.drillship.registry.ModKeys;
import com.solaris99.drillship.ship.ShipShape;
import com.solaris99.drillship.ship.ShipState;
import com.solaris99.drillship.ship.ShipWagons;
import com.solaris99.drillship.ship.TeslaTurret;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A tesla turret: every 0.25 s, while the ship's turrets are on, it zaps every target in a box around its section's
 * exterior, enlarged by its reach, paying 100 FE per hit and stopping when the buffer can't. It fires from the top of
 * the coil on the section's roof, whether or not the coil has room to rise.
 */
public class TeslaTurretBlockEntity extends ModuleBlockEntity {
    // Above the roof, where the arcs start.
    private static final double COIL_TOP = 1.2;
    private static final int SPARKS_PER_BLOCK = 4;

    public TeslaTurretBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TESLA_TURRET.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TeslaTurretBlockEntity turretEntity) {
        ShipState ship = turretEntity.ship();
        if (level.getGameTime() % TeslaTurret.INTERVAL_TICKS != 0 || ship == null || !ship.areTurretsOn()
                || !(state.getBlock() instanceof TeslaTurretBlock block)) {
            return;
        }
        int section = ship.layout().sectionAt(pos);
        MinecraftServer server = level.getServer();
        ServerLevel outside = server == null ? null : server.getLevel(ship.dimension());
        ShipSectionEntity exterior = section == 0 ? ship.findEntity(server) : section > 0 && section <= ship.wagonCount()
                ? ShipWagons.findEntity(server, ship, section) : null;
        if (outside == null || exterior == null) {
            return;
        }
        TeslaTurret turret = block.turret();
        ShipShape shape = exterior.shape();
        ShipShape.Frame frame = exterior.frame();
        AABB area = frame.bounds(shape.hull()).inflate(turret.reach());
        Vec3 top = frame.toWorld(0, shape.height + COIL_TOP, (shape.back + shape.hullFront) / 2);
        DamageSource source = new DamageSource(outside.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ModKeys.TESLA_DAMAGE), exterior);
        boolean fired = false;
        for (LivingEntity target : outside.getEntitiesOfClass(LivingEntity.class, area, target -> isTarget(ship, target))) {
            if (!ship.consumeEnergy(TeslaTurret.ENERGY_PER_HIT)) {
                break;
            }
            target.hurt(source, turret.damage());
            arc(outside, top, target.getBoundingBox().getCenter());
            fired = true;
        }
        if (fired) {
            outside.playSound(null, top.x, top.y, top.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.2F, 1.8F);
        }
    }

    private static boolean isTarget(ShipState ship, LivingEntity target) {
        return target.isAlive() && !target.isSpectator() && !(target.getVehicle() instanceof ShipSectionEntity)
                && !(target instanceof Player player && player.isCreative()) && ship.turretTargets().test(target);
    }

    // A jagged line of sparks from the coil to the target.
    private static void arc(ServerLevel level, Vec3 from, Vec3 to) {
        int steps = Math.max(2, (int) (from.distanceTo(to) * SPARKS_PER_BLOCK));
        for (int i = 0; i <= steps; i++) {
            Vec3 point = from.lerp(to, i / (double) steps);
            double jitter = i == 0 || i == steps ? 0 : 0.15;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x + (level.random.nextDouble() - 0.5) * 2 * jitter,
                    point.y + (level.random.nextDouble() - 0.5) * 2 * jitter, point.z + (level.random.nextDouble() - 0.5) * 2 * jitter, 1, 0, 0, 0, 0);
        }
    }
}
