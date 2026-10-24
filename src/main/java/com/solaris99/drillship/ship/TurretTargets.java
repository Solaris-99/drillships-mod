package com.solaris99.drillship.ship;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/** What the ship's tesla turrets shoot at: one setting for all of them, on the status panel. */
public enum TurretTargets {
    /** Hostile mobs only. */
    HOSTILES,
    /** Any mob except pets, villagers and iron golems; never players. */
    MOBS,
    /** Mobs and players alike. */
    EVERYTHING;

    public boolean test(LivingEntity entity) {
        return switch (this) {
            case HOSTILES -> entity instanceof Enemy;
            case MOBS -> entity instanceof Mob && !isFriend(entity);
            case EVERYTHING -> entity instanceof Mob || entity instanceof Player;
        };
    }

    private static boolean isFriend(LivingEntity entity) {
        return entity instanceof OwnableEntity pet && pet.getOwnerUUID() != null || entity instanceof AbstractVillager || entity instanceof IronGolem;
    }

    public TurretTargets next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static TurretTargets byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : HOSTILES;
    }
}
