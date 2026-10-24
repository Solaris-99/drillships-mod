package com.solaris99.drillship.block.entity;

/** Which way the IO modules let other blocks move things: into the ship, out of it, or both. */
public enum IoMode {
    INPUT, OUTPUT, BOTH;

    public boolean canInsert() {
        return this != OUTPUT;
    }

    public boolean canExtract() {
        return this != INPUT;
    }

    public IoMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static IoMode byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : INPUT;
    }
}
