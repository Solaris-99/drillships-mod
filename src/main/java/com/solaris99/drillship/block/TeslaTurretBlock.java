package com.solaris99.drillship.block;

import com.solaris99.drillship.ship.TeslaTurret;

/** A tesla turret's mount in a room's ceiling; the turret itself is on the section's roof outside (see TeslaTurretBlockEntity). */
public class TeslaTurretBlock extends ModuleBlock {
    private final TeslaTurret turret;

    public TeslaTurretBlock(TeslaTurret turret, Properties properties) {
        super(Kind.TESLA_TURRET, properties);
        this.turret = turret;
    }

    public TeslaTurret turret() {
        return turret;
    }
}
