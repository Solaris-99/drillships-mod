package com.solaris99.drillship.client;

import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * A looping sound that follows a drillship for as long as it exists, fading in while {@code active} holds and out
 * otherwise. Engine idle and engine working crossfade this way. Loops are kept well below full volume, and the driver,
 * sitting inside the hull, hears them muffled.
 */
public class DrillshipSoundInstance extends AbstractTickableSoundInstance {
    private static final float FADE_PER_TICK = 0.08F;
    private static final float DRIVER_MUFFLE = 0.6F;
    private static final float STOP_VOLUME = 0.5F;

    private final DrillshipEntity ship;
    private final Predicate<DrillshipEntity> active;
    private final float maxVolume;
    private final ToDoubleFunction<DrillshipEntity> pitchOf;
    private final boolean playsStopSound;
    private float level;
    private boolean wasEngineOn;

    private DrillshipSoundInstance(DrillshipEntity ship, SoundEvent sound, Predicate<DrillshipEntity> active, float maxVolume,
            ToDoubleFunction<DrillshipEntity> pitchOf, boolean playsStopSound) {
        super(sound, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.ship = ship;
        this.active = active;
        this.maxVolume = maxVolume;
        this.pitchOf = pitchOf;
        this.playsStopSound = playsStopSound;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        follow();
    }

    /** Starts every looping sound of a ship that just appeared on the client. */
    public static void startAll(DrillshipEntity ship) {
        var sounds = Minecraft.getInstance().getSoundManager();
        sounds.play(new DrillshipSoundInstance(ship, ModSounds.ENGINE_IDLE.get(), s -> s.isEngineOn() && !s.isMoving(), 0.35F, s -> 1.0, true));
        // The engine works harder, and a little higher, the faster the ship goes.
        sounds.play(new DrillshipSoundInstance(ship, ModSounds.ENGINE_WORKING.get(), s -> s.isEngineOn() && s.isMoving(), 0.5F,
                s -> 0.85 + 0.3 * Math.min(1.0, Math.abs(s.travelledLastTick()) * 20 / s.tier().topSpeed()), false));
        // Only while the drill bites into rock, not while it spins in the air.
        sounds.play(new DrillshipSoundInstance(ship, ModSounds.DIGGING.get(), DrillshipEntity::isCutting, 0.55F, s -> 1.0, false));
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
        level = target > level ? Math.min(target, level + FADE_PER_TICK) : Math.max(target, level - FADE_PER_TICK);
        volume = level * maxVolume * (isDriverInside() ? DRIVER_MUFFLE : 1.0F);
        pitch = (float) pitchOf.applyAsDouble(ship);

        // Only one of the loops announces the engine shutting down.
        if (playsStopSound) {
            if (wasEngineOn && !ship.isEngineOn()) {
                ship.level().playLocalSound(ship.getX(), ship.getY(), ship.getZ(), ModSounds.ENGINE_STOP.get(), SoundSource.NEUTRAL, STOP_VOLUME, 1.0F, false);
            }
            wasEngineOn = ship.isEngineOn();
        }
    }

    // The driver looking through the periscope is inside the hull.
    private boolean isDriverInside() {
        Minecraft minecraft = Minecraft.getInstance();
        return ship.hasPassenger(minecraft.player) && minecraft.options.getCameraType().isFirstPerson();
    }

    private void follow() {
        x = ship.getX();
        y = ship.getY() + ship.shape().height / 2;
        z = ship.getZ();
    }
}
