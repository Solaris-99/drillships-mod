package com.solaris99.drillship.ship;

/**
 * The tesla turrets' sizes. Every 0.25 s a turret zaps every target in a box around its own section, enlarged by its
 * reach, for 100 FE each; armor doesn't stop it and line of sight isn't needed. It doesn't fire without the energy.
 */
public enum TeslaTurret {
    SMALL(4.0F, 3.0),
    MEDIUM(6.0F, 7.0);

    public static final int INTERVAL_TICKS = 5;
    public static final int ENERGY_PER_HIT = 100;

    private final float damage;
    private final double reach;

    TeslaTurret(float damage, double reach) {
        this.damage = damage;
        this.reach = reach;
    }

    public float damage() {
        return damage;
    }

    public double reach() {
        return reach;
    }

    /** As synced to clients and saved: 0 for no turret. */
    public int id() {
        return ordinal() + 1;
    }

    public static TeslaTurret byId(int id) {
        return id >= 1 && id <= values().length ? values()[id - 1] : null;
    }
}
