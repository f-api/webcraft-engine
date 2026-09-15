package com.gameexpert.engine.mob;

/** WebCraft raid standard bearer. */
public final class StandardBearer extends RaidMeleeMob {
    public StandardBearer(long id, double x, double y, double z) {
        super(id, MobType.STANDARD_BEARER, x, y, z, 4, 2.5);
        setNativeEquipment("standard");
    }

    @Override public void commitAcceptedMeleeAttack() {
        commitRoleAction("command", 1);
    }
}
