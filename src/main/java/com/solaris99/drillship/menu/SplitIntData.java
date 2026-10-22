package com.solaris99.drillship.menu;

import java.util.function.IntUnaryOperator;

import javax.annotation.Nullable;

import net.minecraft.world.inventory.ContainerData;

/**
 * Container data that syncs full ints. Vanilla sends each data slot as a short, so every int is split into two
 * 16-bit halves. On the server, values come from {@code source}; on the client, they are reassembled in {@code values}.
 */
public class SplitIntData implements ContainerData {
    private final int[] values;
    @Nullable
    private final IntUnaryOperator source;

    public SplitIntData(int count) {
        this(count, null);
    }

    public SplitIntData(int count, @Nullable IntUnaryOperator source) {
        this.values = new int[count];
        this.source = source;
    }

    public int getInt(int index) {
        return source != null ? source.applyAsInt(index) : values[index];
    }

    @Override
    public int get(int slot) {
        int value = getInt(slot >> 1);
        return (slot & 1) == 0 ? value & 0xFFFF : value >>> 16;
    }

    @Override
    public void set(int slot, int half) {
        int index = slot >> 1;
        int value = values[index];
        values[index] = (slot & 1) == 0
                ? (value & 0xFFFF0000) | (half & 0xFFFF)
                : (value & 0x0000FFFF) | ((half & 0xFFFF) << 16);
    }

    @Override
    public int getCount() {
        return values.length * 2;
    }
}
