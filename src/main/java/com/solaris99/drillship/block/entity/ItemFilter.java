package com.solaris99.drillship.block.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Up to nine items, each held as a single ghost copy, that let through either only those items (a whitelist) or
 * everything but them (a blacklist). Items match by kind, whatever their components.
 */
public class ItemFilter extends SimpleContainer {
    public static final int SIZE = 9;

    private final Runnable onChange;
    private boolean whitelist;

    public ItemFilter(boolean whitelist, Runnable onChange) {
        super(SIZE);
        this.whitelist = whitelist;
        this.onChange = onChange;
    }

    public boolean isWhitelist() {
        return whitelist;
    }

    public void setWhitelist(boolean whitelist) {
        this.whitelist = whitelist;
        setChanged();
    }

    public boolean test(ItemStack stack) {
        return lists(stack) == whitelist;
    }

    public boolean lists(ItemStack stack) {
        for (int i = 0; i < SIZE; i++) {
            if (!getItem(i).isEmpty() && ItemStack.isSameItem(getItem(i), stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        onChange.run();
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag filter = new CompoundTag();
        ContainerHelper.saveAllItems(filter, getItems(), registries);
        filter.putBoolean("Whitelist", whitelist);
        tag.put("Filter", filter);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag filter = tag.getCompound("Filter");
        getItems().clear();
        ContainerHelper.loadAllItems(filter, getItems(), registries);
        if (filter.contains("Whitelist")) {
            whitelist = filter.getBoolean("Whitelist");
        }
    }
}
