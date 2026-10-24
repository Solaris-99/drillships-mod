package com.solaris99.drillship.client;

import java.util.HashMap;
import java.util.Map;

import com.solaris99.drillship.block.UpgradePanelBlock;
import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.registry.ModBlocks;
import com.solaris99.drillship.registry.ModKeys;
import com.solaris99.drillship.ship.RadioStation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Plays the radio's station: at the upgrade panel holding it, near the player inside a ship, and from the outside of every
 * drillship whose radio is on, loud enough for the neighbours. Each loop stops by itself when its radio is retuned,
 * switched off or gone.
 */
public final class RadioSounds {
    private static final int SCAN_TICKS = 10;
    private static final int SCAN_RADIUS = 16;
    private static final float INSIDE_VOLUME = 1.0F;
    // Above 1, a sound carries further: 1.5 is heard about 24 blocks away.
    private static final float OUTSIDE_VOLUME = 1.5F;

    private static final Map<BlockPos, Loop> inside = new HashMap<>();
    private static final Map<Integer, Loop> outside = new HashMap<>();
    private static ClientLevel lastLevel;

    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        SoundManager sounds = minecraft.getSoundManager();
        if (level != lastLevel || minecraft.player == null) {
            inside.values().forEach(sounds::stop);
            outside.values().forEach(sounds::stop);
            inside.clear();
            outside.clear();
            lastLevel = level;
        }
        if (level == null || minecraft.player == null) {
            return;
        }
        // A loop can also end without being stopped: the game cuts every sound when it changes dimension (going in or
        // out of a ship), and a full sound engine drops new ones. Letting those go makes the next scan start them again.
        inside.values().removeIf(loop -> loop.isStopped() || !sounds.isActive(loop));
        outside.values().removeIf(loop -> loop.isStopped() || !sounds.isActive(loop));
        if (level.getGameTime() % SCAN_TICKS != 0) {
            return;
        }
        if (level.dimension() == ModKeys.INTERIOR) {
            scanInterior(minecraft, level, minecraft.player.blockPosition());
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof DrillshipEntity ship && ship.radioStation() > 0 && !outside.containsKey(ship.getId())) {
                SoundEvent sound = RadioStation.byId(ship.radioStation()).sound();
                if (sound != null) {
                    Loop loop = new Loop(sound, OUTSIDE_VOLUME, null, null, ship, ship.radioStation());
                    outside.put(ship.getId(), loop);
                    minecraft.getSoundManager().play(loop);
                }
            }
        }
    }

    private static void scanInterior(Minecraft minecraft, ClientLevel level, BlockPos center) {
        for (int cx = (center.getX() - SCAN_RADIUS) >> 4; cx <= (center.getX() + SCAN_RADIUS) >> 4; cx++) {
            for (int cz = (center.getZ() - SCAN_RADIUS) >> 4; cz <= (center.getZ() + SCAN_RADIUS) >> 4; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                for (int sy = (center.getY() - SCAN_RADIUS) >> 4; sy <= (center.getY() + SCAN_RADIUS) >> 4; sy++) {
                    int index = chunk.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= chunk.getSectionsCount()) {
                        continue;
                    }
                    LevelChunkSection section = chunk.getSection(index);
                    if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(ModBlocks.UPGRADE_PANEL.get()))) {
                        continue;
                    }
                    for (BlockPos pos : BlockPos.betweenClosed(cx << 4, sy << 4, cz << 4, (cx << 4) + 15, (sy << 4) + 15, (cz << 4) + 15)) {
                        int station = station(level, pos);
                        if (station > 0 && !inside.containsKey(pos)) {
                            SoundEvent sound = RadioStation.byId(station).sound();
                            if (sound != null) {
                                Loop loop = new Loop(sound, INSIDE_VOLUME, level, pos.immutable(), null, station);
                                inside.put(pos.immutable(), loop);
                                minecraft.getSoundManager().play(loop);
                            }
                        }
                    }
                }
            }
        }
    }

    private static int station(ClientLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.is(ModBlocks.UPGRADE_PANEL.get()) ? state.getValue(UpgradePanelBlock.STATION) : 0;
    }

    /** A station looping at an upgrade panel, or following a drillship outside. */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        private final BlockPos pos;
        private final DrillshipEntity ship;
        private final int station;

        Loop(SoundEvent sound, float volume, ClientLevel level, BlockPos pos, DrillshipEntity ship, int station) {
            super(sound, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.level = level;
            this.pos = pos;
            this.ship = ship;
            this.station = station;
            this.volume = volume;
            this.looping = true;
            this.delay = 0;
            follow();
        }

        @Override
        public void tick() {
            boolean playing = ship != null
                    ? !ship.isRemoved() && ship.radioStation() == station
                    : Minecraft.getInstance().level == level && station(level, pos) == station;
            if (!playing) {
                stop();
                return;
            }
            follow();
        }

        private void follow() {
            if (ship != null) {
                x = ship.getX();
                y = ship.getY() + ship.shape().height / 2;
                z = ship.getZ();
            } else {
                x = pos.getX() + 0.5;
                y = pos.getY() + 0.5;
                z = pos.getZ() + 0.5;
            }
        }
    }

    private RadioSounds() {
    }
}
