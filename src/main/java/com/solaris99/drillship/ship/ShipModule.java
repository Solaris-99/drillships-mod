package com.solaris99.drillship.ship;

/**
 * Modules, installed into the module panels on the drillhead's left wall, four to a panel. The radar is cumulative:
 * every radar module installed adds to its range (see ShipRadar). One radio is enough; more change nothing. Both
 * also show on the exterior: the radar as a pod under the nose and a search coil under the tail, the radio as an
 * antenna on the roof.
 */
public enum ShipModule {
    /** Shows the water, the creatures and the metal ores around the ship on the driver's HUD. */
    RADAR,
    /** Plays a station inside the ship and out of it. */
    RADIO;

    public int bit() {
        return 1 << ordinal();
    }

    public boolean in(int mask) {
        return (mask & bit()) != 0;
    }
}
