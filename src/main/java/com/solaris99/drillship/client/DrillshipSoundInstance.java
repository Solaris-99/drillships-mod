package com.solaris99.drillship.client;

import java.util.function.Predicate;

import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * A looping sound that follows a drillship for as long as it exists, fading in while {@code active} holds and out
 * otherwise. Engine idle and engine working crossfade this way.
 */
public class DrillshipSoundInstance extends AbstractTickableSoundInstance {
    private static final float FADE_PER_TICK = 0.1F;

    private final DrillshipEntity ship;
    private final Predicate<DrillshipEntity> active;
    private final boolean playsStopSound;
    private boolean wasEngineOn;

    private DrillshipSoundInstance(DrillshipEntity ship, SoundEvent sound, Predicate<DrillshipEntity> active, boolean playsStopSound) {
        super(sound, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.ship = ship;
        this.active = active;
        this.playsStopSound = playsStopSound;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        follow();
    }

    /** Starts every looping sound of a ship that just appeared on the client. */
    public static void startAll(DrillshipEntity ship) {
        var sounds = Minecraft.getInstance().getSoundManager();
        sounds.play(new DrillshipSoundInstance(ship, ModSounds.ENGINE_IDLE.get(), s -> s.isEngineOn() && !s.isMoving(), true));
        sounds.play(new DrillshipSoundInstance(ship, ModSounds.ENGINE_WORKING.get(), s -> s.isEngineOn() && s.isMoving(), false));
        sounds.play(new DrillshipSoundInstance(ship, ModSounds.DIGGING.get(), DrillshipEntity::isDrillSpinning, false));
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (ship.isRemoved()) {
            stop();
            return;
        }
        follow();
        float target = active.test(ship) ? 1.0F : 0.0F;
        volume = target > volume ? Math.min(target, volume + FADE_PER_TICK) : Math.max(target, volume - FADE_PER_TICK);

        // Only one of the loops announces the engine shutting down.
        if (playsStopSound) {
            if (wasEngineOn && !ship.isEngineOn()) {
                ship.level().playLocalSound(ship.getX(), ship.getY(), ship.getZ(), ModSounds.ENGINE_STOP.get(), SoundSource.NEUTRAL, 1.0F, 1.0F, false);
            }
            wasEngineOn = ship.isEngineOn();
        }
    }

    private void follow() {
        x = ship.getX();
        y = ship.getY() + ship.shape().height / 2;
        z = ship.getZ();
    }
}
