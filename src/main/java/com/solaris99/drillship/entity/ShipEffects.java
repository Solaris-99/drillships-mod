package com.solaris99.drillship.entity;

import com.solaris99.drillship.ship.ShipShape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Particles the sections give off, spawned on the client each tick: exhaust smoke, debris from the rock the drill
 * cuts, bubbles under water and dust kicked up by the tracks. Positions follow the exterior model (SectionModel).
 */
final class ShipEffects {
    // Rock this hard or harder throws sparks.
    private static final float SPARKING_HARDNESS = 3.0F;

    static void drillhead(DrillshipEntity ship) {
        Level level = ship.level();
        RandomSource random = ship.getRandom();
        ShipShape shape = ship.shape();
        ShipShape.Frame frame = ship.frame();
        if (ship.isBurning()) {
            // The exhaust stack's top, above the generator on the ship's right.
            boolean working = ship.isMoving() || ship.isCutting();
            if (working || ship.tickCount % 3 == 0) {
                Vec3 top = frame.toWorld(shape.halfWidth - 0.475, shape.height - 0.05, shape.hullFront - 2.5);
                level.addParticle(working ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, top.x, top.y, top.z,
                        (random.nextDouble() - 0.5) * 0.02, 0.06, (random.nextDouble() - 0.5) * 0.02);
            }
        }
        if (ship.isCutting()) {
            for (int i = 0; i < 3; i++) {
                // Somewhere on the drill face, in the rock just ahead of it; chips fly back and out.
                double x = (random.nextDouble() * 2 - 1) * shape.halfWidth * 0.9, y = random.nextDouble() * shape.height;
                Vec3 at = frame.toWorld(x, y, shape.drillFront + 0.3);
                BlockPos pos = BlockPos.containing(at);
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) {
                    continue;
                }
                Vec3 out = frame.toWorld(x * 1.3, y, shape.drillFront - 0.5).subtract(at).normalize().scale(0.15);
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, out.x, out.y + 0.05, out.z);
                if (random.nextFloat() < 0.2F && state.getDestroySpeed(level, pos) >= SPARKING_HARDNESS) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, out.x * 2, 0.1, out.z * 2);
                }
            }
        }
        if (ship.isDrillSpinning()) {
            Vec3 nose = frame.toWorld(0, shape.height / 2, shape.drillFront);
            if (level.getFluidState(BlockPos.containing(nose)).is(FluidTags.WATER)) {
                Vec3 at = frame.toWorld((random.nextDouble() * 2 - 1) * shape.halfWidth, random.nextDouble() * shape.height, shape.drillFront);
                level.addParticle(ParticleTypes.BUBBLE, at.x, at.y, at.z, 0, 0.1, 0);
            }
        }
        trackDust(ship);
    }

    /** Dust from the ground behind the tracks while the section rolls over it. */
    static void trackDust(ShipSectionEntity section) {
        float moved = section.travelledLastTick();
        if (Math.abs(moved) < 0.02F || section.tickCount % 2 != 0) {
            return;
        }
        Level level = section.level();
        RandomSource random = section.getRandom();
        ShipShape shape = section.shape();
        ShipShape.Frame frame = section.frame();
        // Behind the tracks, whichever way the section rolls.
        double z = moved > 0 ? shape.back + 0.5 : shape.hullFront - 0.5;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 at = frame.toWorld(side * (shape.halfWidth - 0.6), 0.05, z);
            BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.3, at.z));
            if (ground.isAir() || ground.getRenderShape() == RenderShape.INVISIBLE) {
                continue;
            }
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y, at.z,
                    (random.nextDouble() - 0.5) * 0.1, 0.12, (random.nextDouble() - 0.5) * 0.1);
        }
    }

    private ShipEffects() {
    }
}
