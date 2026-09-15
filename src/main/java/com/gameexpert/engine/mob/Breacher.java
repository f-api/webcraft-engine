package com.gameexpert.engine.mob;

import com.gameexpert.engine.inventory.PlayerInventory;

/** WebCraft raid shield breacher. */
public final class Breacher extends RaidMeleeMob {
    public Breacher(long id, double x, double y, double z) {
        super(id, MobType.BREACHER, x, y, z, 6, 2.5);
        setNativeEquipment("breach_shield");
    }

    @Override public short heldItem() { return PlayerInventory.IRON_AXE; }
    @Override public int heldItemDurability() {
        return PlayerInventory.initialDurability(PlayerInventory.IRON_AXE);
    }

    @Override public void commitAcceptedMeleeAttack() {
        commitRoleAction("breach", 8);
    }
}
