package com.solaris99.drillship.ship;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.solaris99.drillship.registry.ModSounds;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * What the radio upgrade plays, in the order clicking it goes through them. FM Tumbera plays the mod's own track
 * ("Cumbia del Taladro", art-source/music); the others play vanilla music discs. Each station loops.
 */
public enum RadioStation {
    OFF(null),
    TUMBERA(ModSounds.RADIO_TUMBERA::get),
    CAT(() -> SoundEvents.MUSIC_DISC_CAT.value()),
    BLOCKS(() -> SoundEvents.MUSIC_DISC_BLOCKS.value()),
    CHIRP(() -> SoundEvents.MUSIC_DISC_CHIRP.value()),
    MALL(() -> SoundEvents.MUSIC_DISC_MALL.value()),
    PIGSTEP(() -> SoundEvents.MUSIC_DISC_PIGSTEP.value());

    @Nullable
    private final Supplier<SoundEvent> sound;

    RadioStation(@Nullable Supplier<SoundEvent> sound) {
        this.sound = sound;
    }

    public static RadioStation byId(int id) {
        RadioStation[] values = values();
        return id >= 0 && id < values.length ? values[id] : OFF;
    }

    public RadioStation next() {
        return byId((ordinal() + 1) % values().length);
    }

    public RadioStation previous() {
        return byId((ordinal() + values().length - 1) % values().length);
    }

    @Nullable
    public SoundEvent sound() {
        return sound == null ? null : sound.get();
    }

    public Component title() {
        return Component.translatable("radio.drillships." + name().toLowerCase(java.util.Locale.ROOT));
    }
}
