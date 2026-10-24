package com.solaris99.drillship.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * One block of a section's collision. A section is covered by a grid of these, each the axis-aligned bounds of its
 * own slice of the rotated hull, so players and mobs bump into and stand on the hull itself instead of one big box
 * around the whole rotated section. Clicking a part clicks the section. Parts are never saved or sent: the section
 * creates them on both sides and lays them out every time it moves.
 */
public class ShipPartEntity extends PartEntity<ShipSectionEntity> {
    private AABB box;

    public ShipPartEntity(ShipSectionEntity parent) {
        super(parent);
        this.noPhysics = true;
        this.blocksBuilding = true;
    }

    void place(AABB box) {
        this.box = box;
        setPosRaw((box.minX + box.maxX) / 2, box.minY, (box.minZ + box.maxZ) / 2);
        setBoundingBox(box);
    }

    @Override
    protected AABB makeBoundingBox() {
        // Null while Entity's constructor runs.
        return box != null ? box : super.makeBoundingBox();
    }

    @Override
    public boolean canBeCollidedWith() {
        return !getParent().isRemoved();
    }

    @Override
    public boolean isPickable() {
        return !getParent().isRemoved();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return getParent().interact(player, hand);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || getParent() == entity;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
