package com.solaris99.drillship.ship;

/**
 * Upgrades, installed into the upgrade panels on the drillhead's left wall, four to a panel. Each kind counts up to
 * its own limit and more change nothing: the radar's tier rises with each one (see ShipRadar), one radio is enough,
 * overdrives stack up to three, one antigravity engine is enough, and inventory upgrades have no limit. The radar and
 * the radio also show on the exterior: the radar as a pod under the nose and a search coil under the tail, the radio
 * as an antenna on the roof.
 */
public enum ShipUpgrade {
    /** Shows the water, the creatures and the metal ores around the ship on the driver's HUD. */
    RADAR(ShipRadar.MAX_TIER),
    /** Plays a station inside the ship and out of it. */
    RADIO(1),
    /** Raises the top speed and the acceleration, and the cost of moving. */
    ENGINE_OVERDRIVE(3),
    /** Turns off gravity while it has energy, so the ship flies where its nose points. */
    ANTIGRAVITY(1),
    /** Adds slots to the ship's inventory. */
    INVENTORY(Integer.MAX_VALUE);

    public static final double OVERDRIVE_SPEED_BONUS = 0.5;
    public static final double OVERDRIVE_ACCELERATION_BONUS = 0.1;
    public static final int OVERDRIVE_MOVE_ENERGY = 10;
    public static final int ANTIGRAVITY_ENERGY = 200;
    public static final int INVENTORY_SLOTS = 50;

    private final int limit;

    ShipUpgrade(int limit) {
        this.limit = limit;
    }

    /** How many of {@code installed} take effect. */
    public int effective(int installed) {
        return Math.min(limit, installed);
    }

    public int bit() {
        return 1 << ordinal();
    }

    public boolean in(int mask) {
        return (mask & bit()) != 0;
    }
}
