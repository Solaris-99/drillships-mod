package com.solaris99.drillship.registry;

import com.solaris99.drillship.Drillships;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Drillships.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_IDLE = register("engine_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_WORKING = register("engine_working");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_STOP = register("engine_stop");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIGGING = register("digging");
    // The radio upgrade's own station.
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_TUMBERA = register("music.cumbia_del_taladro");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Drillships.id(name)));
    }

    private ModSounds() {
    }
}
