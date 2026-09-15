package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.engine.mob.MobType;

/** The guardian has only charge attacks; ordinary hits and projectiles keep their knockback. */
public final class FleshArmorRules {
    private FleshArmorRules() { }
    public static double knockbackScale(int chestItem, MobType attacker) {
        return chestItem == Blocks.FLESH_BONE_CHESTPLATE && attacker == MobType.BONE_PROCESSION ? .5 : 1;
    }
}
