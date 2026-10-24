package com.solaris99.drillship.block.entity;

import javax.annotation.Nullable;

import com.solaris99.drillship.Config;
import com.solaris99.drillship.registry.ModBlockEntities;
import com.solaris99.drillship.ship.ShipState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * The Energy IO module: the ship's energy buffer, for other blocks to push FE into (input), pull it out of (output) or
 * both. It never pushes on its own. What it moves per tick, in each direction, is capped by its own limit and the
 * server's.
 */
public class EnergyIoBlockEntity extends ModuleBlockEntity {
    public static final int MAX_LIMIT = 1_000_000_000;

    private IoMode mode = IoMode.INPUT;
    // FE per tick; 0 for no limit.
    private int limit;
    private long tick = -1;
    private int received;
    private int extracted;

    public EnergyIoBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENERGY_IO.get(), pos, state);
    }

    public IoMode mode() {
        return mode;
    }

    public void cycleMode() {
        mode = mode.next();
        setChanged();
    }

    public int limit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = Math.max(0, Math.min(MAX_LIMIT, limit));
        setChanged();
    }

    /** FE per tick it moves each way: the lower of its own limit and the server's cap, where either is set. */
    public int rate() {
        return IoRates.combine(limit, Config.ENERGY_IO_MAX_RATE.get());
    }

    @Nullable
    public IEnergyStorage energyStorage() {
        ShipState ship = ship();
        return ship == null ? null : new IEnergyStorage() {
            @Override
            public int receiveEnergy(int max, boolean simulate) {
                if (!mode.canInsert()) {
                    return 0;
                }
                startTick();
                int amount = ship.receiveEnergy(Math.min(max, rate() - received), simulate);
                if (!simulate) {
                    received += amount;
                }
                return amount;
            }

            @Override
            public int extractEnergy(int max, boolean simulate) {
                if (!mode.canExtract()) {
                    return 0;
                }
                startTick();
                int amount = ship.extractEnergy(Math.min(max, rate() - extracted), simulate);
                if (!simulate) {
                    extracted += amount;
                }
                return amount;
            }

            @Override
            public int getEnergyStored() {
                return ship.energy();
            }

            @Override
            public int getMaxEnergyStored() {
                return ship.tier().energyCapacity();
            }

            @Override
            public boolean canExtract() {
                return mode.canExtract();
            }

            @Override
            public boolean canReceive() {
                return mode.canInsert();
            }
        };
    }

    private void startTick() {
        long now = level == null ? 0 : level.getGameTime();
        if (now != tick) {
            tick = now;
            received = 0;
            extracted = 0;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Mode", mode.ordinal());
        tag.putInt("Limit", limit);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mode = IoMode.byId(tag.getInt("Mode"));
        limit = tag.getInt("Limit");
    }
}
