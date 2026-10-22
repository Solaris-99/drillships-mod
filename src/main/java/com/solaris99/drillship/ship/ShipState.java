package com.solaris99.drillship.ship;

import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.item.DrillItem;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Everything about one drillship that must survive while its exterior entity is unloaded. */
public class ShipState {
    public static final int WARN_LOW_ENERGY = 1;
    public static final int WARN_NO_ENERGY = 1 << 1;
    public static final int WARN_INVENTORY_FULL = 1 << 2;
    public static final int WARN_NO_DRILL = 1 << 3;
    public static final int WARN_DRILL_WORN = 1 << 4;
    public static final int WARN_DRILL_BROKEN = 1 << 5;
    public static final int WARN_BLOCKED = 1 << 6;

    // How long transient warnings stay lit after the last time they happened.
    private static final int WARNING_TICKS = 100;

    private final ShipManager manager;
    private final UUID id;
    private final ShipTier tier;
    private final int interiorIndex;
    private final ItemStackHandler inventory;
    private final ItemStackHandler fuel;
    private final ItemStackHandler drill;

    private ResourceKey<Level> dimension;
    private Vec3 position;
    private float yaw;
    private float pitch;
    private int energy;
    private int burnTime;
    private int burnDuration;
    private boolean drillOn;
    private boolean autoFuel = true;

    // Not saved
    private double speed;
    private long inventoryFullUntil;
    private long blockedUntil;

    ShipState(ShipManager manager, UUID id, ShipTier tier, int interiorIndex, ResourceKey<Level> dimension, Vec3 position, float yaw) {
        this.manager = manager;
        this.id = id;
        this.tier = tier;
        this.interiorIndex = interiorIndex;
        this.dimension = dimension;
        this.position = position;
        this.yaw = yaw;
        this.inventory = handler(tier.inventorySlots(), stack -> true);
        this.fuel = handler(1, stack -> stack.getBurnTime(RecipeType.SMELTING) > 0);
        this.drill = handler(1, stack -> stack.getItem() instanceof DrillItem);
    }

    private ItemStackHandler handler(int size, Predicate<ItemStack> valid) {
        return new ItemStackHandler(size) {
            @Override
            protected void onContentsChanged(int slot) {
                manager.setDirty();
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return valid.test(stack);
            }
        };
    }

    public UUID id() {
        return id;
    }

    public ShipTier tier() {
        return tier;
    }

    public int interiorIndex() {
        return interiorIndex;
    }

    public InteriorLayout layout() {
        return InteriorLayout.of(tier, interiorIndex);
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public Vec3 position() {
        return position;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    /** Actual speed in blocks per second, as last measured by the exterior entity. */
    public double speed() {
        return speed;
    }

    public void updatePose(ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch, double speed) {
        this.dimension = dimension;
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
        this.speed = speed;
        manager.setDirty();
    }

    @Nullable
    public DrillshipEntity findEntity(MinecraftServer server) {
        ServerLevel level = server.getLevel(dimension);
        return level != null && level.getEntity(id) instanceof DrillshipEntity entity ? entity : null;
    }

    // Inventories

    public IItemHandlerModifiable inventory() {
        return inventory;
    }

    public IItemHandlerModifiable fuelSlot() {
        return fuel;
    }

    public IItemHandlerModifiable drillSlot() {
        return drill;
    }

    public ItemStack drill() {
        return drill.getStackInSlot(0);
    }

    /** Stores mined items. Returns what did not fit. */
    public ItemStack store(ItemStack stack) {
        return ItemHandlerHelper.insertItemStacked(inventory, stack, false);
    }

    public boolean hasNoItems() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return fuel.getStackInSlot(0).isEmpty();
    }

    public void markDirty() {
        manager.setDirty();
    }

    // Energy and generator

    public int energy() {
        return energy;
    }

    public boolean consumeEnergy(int amount) {
        if (energy < amount) {
            return false;
        }
        energy -= amount;
        manager.setDirty();
        return true;
    }

    public int burnTime() {
        return burnTime;
    }

    public int burnDuration() {
        return burnDuration;
    }

    public boolean isBurning() {
        return burnTime > 0;
    }

    /** Seconds of generation left from the fuel being burnt plus the fuel slot. */
    public int fuelSeconds() {
        ItemStack stack = fuel.getStackInSlot(0);
        long ticks = burnTime + (long) stack.getBurnTime(RecipeType.SMELTING) * stack.getCount();
        return (int) Math.min(Integer.MAX_VALUE, ticks / 20);
    }

    /** The generator burns one burn-tick of fuel per game tick, but only while the buffer has room. */
    public void tickGenerator() {
        if (energy >= tier.energyCapacity()) {
            return;
        }
        if (burnTime <= 0 && !consumeFuel()) {
            return;
        }
        burnTime--;
        energy = Math.min(tier.energyCapacity(), energy + tier.generation());
        manager.setDirty();
    }

    private boolean consumeFuel() {
        IItemHandlerModifiable source = fuel;
        int slot = 0;
        if (fuel.getStackInSlot(0).isEmpty()) {
            if (!autoFuel || (slot = findFuel()) < 0) {
                return false;
            }
            source = inventory;
        }
        ItemStack stack = source.getStackInSlot(slot);
        int time = stack.getBurnTime(RecipeType.SMELTING);
        if (time <= 0) {
            return false;
        }
        ItemStack remainder = stack.getCraftingRemainingItem();
        source.extractItem(slot, 1, false);
        if (!remainder.isEmpty()) {
            if (source.getStackInSlot(slot).isEmpty()) {
                source.setStackInSlot(slot, remainder);
            } else {
                store(remainder);
            }
        }
        burnTime = time;
        burnDuration = time;
        return true;
    }

    private int findFuel() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (inventory.getStackInSlot(i).getBurnTime(RecipeType.SMELTING) > 0) {
                return i;
            }
        }
        return -1;
    }

    // Settings

    public boolean isDrillOn() {
        return drillOn;
    }

    public void setDrillOn(boolean drillOn) {
        this.drillOn = drillOn;
        manager.setDirty();
    }

    public boolean isAutoFuel() {
        return autoFuel;
    }

    public void setAutoFuel(boolean autoFuel) {
        this.autoFuel = autoFuel;
        manager.setDirty();
    }

    // Warnings

    public void markInventoryFull(long gameTime) {
        inventoryFullUntil = gameTime + WARNING_TICKS;
    }

    public void markBlocked(long gameTime) {
        blockedUntil = gameTime + WARNING_TICKS;
    }

    public int warnings(long gameTime) {
        int warnings = 0;
        if (energy <= 0) {
            warnings |= WARN_NO_ENERGY;
        } else if (energy < tier.energyCapacity() / 10) {
            warnings |= WARN_LOW_ENERGY;
        }
        ItemStack drillStack = drill();
        if (drillStack.isEmpty()) {
            warnings |= WARN_NO_DRILL;
        } else if (DrillItem.isBroken(drillStack)) {
            warnings |= WARN_DRILL_BROKEN;
        } else if (DrillItem.isWorn(drillStack)) {
            warnings |= WARN_DRILL_WORN;
        }
        if (gameTime < inventoryFullUntil) {
            warnings |= WARN_INVENTORY_FULL;
        }
        if (gameTime < blockedUntil) {
            warnings |= WARN_BLOCKED;
        }
        return warnings;
    }

    // Persistence

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Tier", tier.name());
        tag.putInt("Interior", interiorIndex);
        tag.putString("Dimension", dimension.location().toString());
        tag.putDouble("X", position.x);
        tag.putDouble("Y", position.y);
        tag.putDouble("Z", position.z);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        tag.putInt("Energy", energy);
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnDuration", burnDuration);
        tag.putBoolean("DrillOn", drillOn);
        tag.putBoolean("AutoFuel", autoFuel);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.put("Fuel", fuel.serializeNBT(registries));
        tag.put("Drill", drill.serializeNBT(registries));
        return tag;
    }

    static ShipState load(ShipManager manager, CompoundTag tag, HolderLookup.Provider registries) {
        ShipTier tier = ShipTier.valueOf(tag.getString("Tier"));
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dimension")));
        Vec3 position = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        ShipState ship = new ShipState(manager, tag.getUUID("Id"), tier, tag.getInt("Interior"), dimension, position, tag.getFloat("Yaw"));
        ship.pitch = tag.getFloat("Pitch");
        ship.energy = tag.getInt("Energy");
        ship.burnTime = tag.getInt("BurnTime");
        ship.burnDuration = tag.getInt("BurnDuration");
        ship.drillOn = tag.getBoolean("DrillOn");
        ship.autoFuel = tag.getBoolean("AutoFuel");
        ship.inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        ship.fuel.deserializeNBT(registries, tag.getCompound("Fuel"));
        ship.drill.deserializeNBT(registries, tag.getCompound("Drill"));
        return ship;
    }
}
