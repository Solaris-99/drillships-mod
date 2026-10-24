package com.solaris99.drillship.ship;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.solaris99.drillship.Config;
import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.item.DrillItem;
import com.solaris99.drillship.item.UpgradeItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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

    public static final int UPGRADES_PER_PANEL = 4;

    // How long transient warnings stay lit after the last time they happened.
    private static final int WARNING_TICKS = 100;
    // Wagons follow the drillhead's recorded path; a point is recorded every PATH_SPACING blocks travelled.
    private static final double PATH_SPACING = 0.25;

    private final ShipManager manager;
    private final UUID id;
    private final ShipTier tier;
    private final int interiorIndex;
    private final Storage inventory;
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
    private int wagonCount;
    // The upgrades in the panels, four per panel, the radio's station (0 for off) and what the radar looks for.
    private final ItemStackHandler upgrades;
    private int radioStation;
    private RadarOre radarTarget = RadarOre.IRON;
    // Set when the upgrades or the station change, so the panels inside get redrawn.
    private boolean panelsChanged = true;
    // Positions of the drillhead's centre at half its height, oldest first.
    private final List<Vec3> path = new ArrayList<>();
    // The light block placed by the headlights, so it can be removed even after a restart.
    @Nullable
    private BlockPos headlight;

    // Not saved
    private double speed;
    private long inventoryFullUntil;
    private long blockedUntil;
    // The antigravity engine didn't get its energy last tick, so gravity is back.
    private boolean antigravityStarved;

    ShipState(ShipManager manager, UUID id, ShipTier tier, int interiorIndex, ResourceKey<Level> dimension, Vec3 position, float yaw) {
        this.manager = manager;
        this.id = id;
        this.tier = tier;
        this.interiorIndex = interiorIndex;
        this.dimension = dimension;
        this.position = position;
        this.yaw = yaw;
        this.inventory = new Storage(tier.inventorySlots());
        this.fuel = handler(1, stack -> stack.getBurnTime(RecipeType.SMELTING) > 0);
        this.drill = handler(1, stack -> stack.getItem() instanceof DrillItem);
        this.upgrades = new ItemStackHandler(tier.upgradePanels() * UPGRADES_PER_PANEL) {
            @Override
            protected void onContentsChanged(int slot) {
                if (!hasUpgrade(ShipUpgrade.RADIO)) {
                    radioStation = 0;
                }
                resizeInventory();
                panelsChanged = true;
                manager.setDirty();
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return canRemoveUpgrade(slot) ? super.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return stack.getItem() instanceof UpgradeItem;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
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

    /** The ship's inventory, which grows and shrinks with its inventory upgrades, keeping what it holds. */
    private class Storage extends ItemStackHandler {
        Storage(int size) {
            super(size);
        }

        void resize(int size) {
            NonNullList<ItemStack> resized = NonNullList.withSize(size, ItemStack.EMPTY);
            for (int slot = 0; slot < Math.min(size, stacks.size()); slot++) {
                resized.set(slot, stacks.get(slot));
            }
            stacks = resized;
            manager.setDirty();
        }

        @Override
        protected void onContentsChanged(int slot) {
            manager.setDirty();
        }
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
        recordPath();
        manager.setDirty();
    }

    // Upgrades

    /** The upgrade panels' slots: panel {@code p} holds slots {@code 4p} to {@code 4p + 3}. */
    public IItemHandlerModifiable upgrades() {
        return upgrades;
    }

    /** The upgrade in a slot, or null when it is empty. */
    @Nullable
    public ShipUpgrade upgradeAt(int slot) {
        return upgrades.getStackInSlot(slot).getItem() instanceof UpgradeItem item ? item.upgrade() : null;
    }

    public int upgradeCount(ShipUpgrade upgrade) {
        int count = 0;
        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            if (upgradeAt(slot) == upgrade) {
                count++;
            }
        }
        return count;
    }

    public boolean hasUpgrade(ShipUpgrade upgrade) {
        return upgradeCount(upgrade) > 0;
    }

    /** How many of an upgrade take effect: as many as are installed, up to its limit. */
    public int effectiveUpgrades(ShipUpgrade upgrade) {
        return upgrade.effective(upgradeCount(upgrade));
    }

    /** An inventory upgrade can only come out while the slots it adds, the last ones, are empty. */
    public boolean canRemoveUpgrade(int slot) {
        if (upgradeAt(slot) != ShipUpgrade.INVENTORY) {
            return true;
        }
        for (int i = inventory.getSlots() - ShipUpgrade.INVENTORY_SLOTS; i < inventory.getSlots(); i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Top speed in blocks per second: the tier's, raised by each engine overdrive. */
    public double topSpeed() {
        return tier.topSpeed() * (1 + ShipUpgrade.OVERDRIVE_SPEED_BONUS * effectiveUpgrades(ShipUpgrade.ENGINE_OVERDRIVE));
    }

    /** How much faster than normal the ship gains and sheds speed, with each engine overdrive. */
    public double accelerationFactor() {
        return 1 + ShipUpgrade.OVERDRIVE_ACCELERATION_BONUS * effectiveUpgrades(ShipUpgrade.ENGINE_OVERDRIVE);
    }

    /** FE per tick while moving: the configured cost plus each engine overdrive's. */
    public int moveEnergy() {
        return Config.MOVE_ENERGY.get() + ShipUpgrade.OVERDRIVE_MOVE_ENERGY * effectiveUpgrades(ShipUpgrade.ENGINE_OVERDRIVE);
    }

    /** Gravity is off: an antigravity engine is installed and got its energy last tick. */
    public boolean isFloating() {
        return hasUpgrade(ShipUpgrade.ANTIGRAVITY) && !antigravityStarved;
    }

    /** The upgrades' running costs, drawn every tick whether or not anyone is aboard. */
    public void tickUpgrades() {
        antigravityStarved = hasUpgrade(ShipUpgrade.ANTIGRAVITY) && !consumeEnergy(ShipUpgrade.ANTIGRAVITY_ENERGY);
    }

    /** The kinds of upgrade installed, as ShipUpgrade bits: they show on the exterior. */
    public int upgradeMask() {
        int mask = 0;
        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            ShipUpgrade upgrade = upgradeAt(slot);
            mask |= upgrade == null ? 0 : upgrade.bit();
        }
        return mask;
    }

    /** Empties the panels, returning what was in them. */
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> taken = new ArrayList<>();
        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            if (!upgrades.getStackInSlot(slot).isEmpty()) {
                taken.add(upgrades.getStackInSlot(slot));
                upgrades.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        return taken;
    }

    public int radioStation() {
        return radioStation;
    }

    /** Tunes the radio; without one it stays off. */
    public void setRadioStation(int station) {
        radioStation = hasUpgrade(ShipUpgrade.RADIO) ? station : 0;
        panelsChanged = true;
        manager.setDirty();
    }

    /** The radar's tier: one per radar upgrade installed, up to ShipRadar.MAX_TIER; 0 without a radar. */
    public int radarTier() {
        return ShipRadar.tier(upgradeCount(ShipUpgrade.RADAR));
    }

    /** What the radar looks for. A kind the radar's tier can't find falls back to iron. */
    public RadarOre radarTarget() {
        return radarTarget.foundAt(Math.max(1, radarTier())) ? radarTarget : RadarOre.IRON;
    }

    /** Moves the radar's target to the next (or, with a negative step, previous) kind its tier finds. */
    public void cycleRadarTarget(int step) {
        radarTarget = radarTarget().next(Math.max(1, radarTier()), step);
        manager.setDirty();
    }

    /** The upgrades or the station changed since the panels inside last showed them. */
    public boolean panelsChanged() {
        return panelsChanged;
    }

    public void clearPanelsChanged() {
        panelsChanged = false;
    }

    // Wagons

    public int wagonCount() {
        return wagonCount;
    }

    public void setWagonCount(int wagonCount) {
        this.wagonCount = wagonCount;
        extendPath();
        trimPath();
        manager.setDirty();
    }

    /** Each wagon's exterior entity has a fixed id derived from the ship's. Wagons are numbered from 1. */
    public UUID wagonId(int wagon) {
        return UUID.nameUUIDFromBytes((id + "/wagon/" + wagon).getBytes(StandardCharsets.UTF_8));
    }

    public record Pose(Vec3 position, float yaw, float pitch) {
        public ShipShape.Frame frame() {
            return ShipShape.Frame.of(position, yaw, pitch);
        }
    }

    /**
     * Where a wagon sits, like a train car: both ends of its centre line, at half its height, lie on the path of the
     * drillhead's centre, and its body spans the chord between them. Joining sections at half height (rather than at
     * the bottom) lets them bend up or down about twice as far before their ends touch.
     */
    public Pose wagonPose(int wagon) {
        return wagonPose(wagon, position, yaw, pitch);
    }

    /** Where a wagon would sit if the drillhead were at this pose; the path itself isn't changed. */
    public Pose wagonPose(int wagon, Vec3 headPosition, float headYaw, float headPitch) {
        double length = tier.wagonExteriorLength();
        double gap = tier.coupling().gap();
        double front = -ShipShape.drillhead(tier).back + gap + (wagon - 1) * (length + gap);
        Vec3 headCenter = center(headPosition, headYaw, headPitch);
        Vec3 frontEnd = pointBehind(headCenter, headYaw, headPitch, front);
        Vec3 backEnd = pointBehind(headCenter, headYaw, headPitch, front + length);
        Vec3 direction = frontEnd.subtract(backEnd);
        float wagonYaw = (float) (Mth.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG);
        float wagonPitch = (float) (-Math.asin(Mth.clamp(direction.y / direction.length(), -1.0, 1.0)) * Mth.RAD_TO_DEG);
        Vec3 up = ShipShape.Frame.of(Vec3.ZERO, wagonYaw, wagonPitch).up();
        return new Pose(frontEnd.add(backEnd).scale(0.5).subtract(up.scale(tier.exteriorHeight() / 2.0)), wagonYaw, wagonPitch);
    }

    // The middle of a section at half its height: what the path follows.
    private Vec3 center(Vec3 origin, float sectionYaw, float sectionPitch) {
        return ShipShape.Frame.of(origin, sectionYaw, sectionPitch).toWorld(0, tier.exteriorHeight() / 2.0, 0);
    }

    /**
     * The point {@code distance} blocks behind {@code start} along the path, extended straight back past its end.
     * Recorded points ahead of {@code start} are skipped, as when reversing.
     */
    private Vec3 pointBehind(Vec3 start, float headYaw, float headPitch, double distance) {
        Vec3 forward = ShipShape.Frame.of(Vec3.ZERO, headYaw, headPitch).forward();
        Vec3 previous = start;
        Vec3 direction = forward.reverse();
        double remaining = distance;
        int i = path.size() - 1;
        while (i >= 0 && path.get(i).subtract(start).dot(forward) > 1.0E-3) {
            i--;
        }
        for (; i >= 0; i--) {
            Vec3 next = path.get(i);
            double segment = previous.distanceTo(next);
            if (segment < 1.0E-6) {
                continue;
            }
            if (remaining <= segment) {
                return previous.lerp(next, remaining / segment);
            }
            remaining -= segment;
            direction = next.subtract(previous).normalize();
            previous = next;
        }
        return previous.add(direction.scale(remaining));
    }

    private void recordPath() {
        // Reversing: drop recorded points that are now ahead of the drillhead, so wagons get pushed back along the path.
        Vec3 forward = ShipShape.Frame.of(Vec3.ZERO, yaw, pitch).forward();
        Vec3 center = center(position, yaw, pitch);
        while (!path.isEmpty() && path.get(path.size() - 1).subtract(center).dot(forward) > 1.0E-3) {
            path.remove(path.size() - 1);
        }
        if (path.isEmpty() || center.distanceTo(path.get(path.size() - 1)) >= PATH_SPACING) {
            path.add(center);
            trimPath();
        }
    }

    // Enough path for the last wagon to ride on.
    private double neededPath() {
        return -ShipShape.drillhead(tier).back + wagonCount * (tier.wagonExteriorLength() + tier.coupling().gap()) + 1.0;
    }

    /**
     * Lays the path out straight back past its oldest point, as far as the last wagon needs. Without it a wagon coupled
     * to a ship that hasn't driven yet would have no path to ride on and would be put straight behind wherever the
     * drillhead turns, so turning in place could swing it around.
     */
    private void extendPath() {
        Vec3 center = center(position, yaw, pitch);
        if (path.isEmpty()) {
            path.add(center);
        }
        double length = 0;
        Vec3 previous = center;
        for (int i = path.size() - 1; i >= 0; i--) {
            length += previous.distanceTo(path.get(i));
            previous = path.get(i);
        }
        Vec3 direction = path.size() >= 2 ? path.get(0).subtract(path.get(1)) : Vec3.ZERO;
        direction = direction.lengthSqr() > 1.0E-6 ? direction.normalize() : ShipShape.Frame.of(Vec3.ZERO, yaw, pitch).forward().reverse();
        List<Vec3> added = new ArrayList<>();
        Vec3 tail = path.get(0);
        for (; length < neededPath(); length += PATH_SPACING) {
            tail = tail.add(direction.scale(PATH_SPACING));
            added.add(0, tail);
        }
        path.addAll(0, added);
    }

    // Keep just enough path for the last wagon.
    private void trimPath() {
        double needed = neededPath();
        double length = 0;
        Vec3 previous = center(position, yaw, pitch);
        for (int i = path.size() - 1; i >= 0; i--) {
            length += previous.distanceTo(path.get(i));
            previous = path.get(i);
            if (length > needed) {
                path.subList(0, i).clear();
                return;
            }
        }
    }

    // Headlights

    @Nullable
    public BlockPos headlight() {
        return headlight;
    }

    public void setHeadlight(@Nullable BlockPos headlight) {
        this.headlight = headlight;
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

    /** The tier's slots plus each inventory upgrade's. */
    public int inventorySlots() {
        return tier.inventorySlots() + ShipUpgrade.INVENTORY_SLOTS * effectiveUpgrades(ShipUpgrade.INVENTORY);
    }

    /** What the inventory grows to with an inventory upgrade in every bay. */
    public int maxInventorySlots() {
        return tier.inventorySlots() + ShipUpgrade.INVENTORY_SLOTS * upgrades.getSlots();
    }

    private void resizeInventory() {
        if (inventory.getSlots() != inventorySlots()) {
            inventory.resize(inventorySlots());
        }
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
        tag.putInt("Wagons", wagonCount);
        tag.put("Upgrades", upgrades.serializeNBT(registries));
        tag.putInt("RadioStation", radioStation);
        tag.putString("RadarTarget", radarTarget.name());
        ListTag pathTag = new ListTag();
        for (Vec3 point : path) {
            CompoundTag pointTag = new CompoundTag();
            pointTag.putDouble("X", point.x);
            pointTag.putDouble("Y", point.y);
            pointTag.putDouble("Z", point.z);
            pathTag.add(pointTag);
        }
        tag.put("Path", pathTag);
        if (headlight != null) {
            tag.put("Headlight", NbtUtils.writeBlockPos(headlight));
        }
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
        ship.wagonCount = tag.getInt("Wagons");
        ship.radioStation = tag.getInt("RadioStation");
        for (RadarOre kind : RadarOre.values()) {
            if (kind.name().equals(tag.getString("RadarTarget"))) {
                ship.radarTarget = kind;
            }
        }
        for (Tag entry : tag.getList("Path", Tag.TAG_COMPOUND)) {
            CompoundTag point = (CompoundTag) entry;
            ship.path.add(new Vec3(point.getDouble("X"), point.getDouble("Y"), point.getDouble("Z")));
        }
        if (ship.wagonCount > 0) {
            ship.extendPath();
        }
        ship.headlight = NbtUtils.readBlockPos(tag, "Headlight").orElse(null);
        ship.inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        ship.fuel.deserializeNBT(registries, tag.getCompound("Fuel"));
        ship.drill.deserializeNBT(registries, tag.getCompound("Drill"));
        // Upgrades used to be called modules. Ships saved before the panels kept them as bits; those are gone.
        String upgradesKey = tag.contains("Upgrades", Tag.TAG_COMPOUND) ? "Upgrades" : "Modules";
        if (tag.contains(upgradesKey, Tag.TAG_COMPOUND)) {
            CompoundTag upgradesTag = tag.getCompound(upgradesKey);
            upgradesTag.remove("Size");
            ship.upgrades.deserializeNBT(registries, upgradesTag);
        }
        ship.resizeInventory();
        return ship;
    }
}
