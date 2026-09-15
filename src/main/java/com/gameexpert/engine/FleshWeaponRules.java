package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.engine.mob.MobType;

/** Both jab and kinetic attacks apply the same custom bonus before target armor. */
public final class FleshWeaponRules {
    private FleshWeaponRules() { }
    public static double damage(double damage, int weapon, MobType target) {
        if (!FleshNetherRules.isFleshMob(target.name())) return damage;
        return weapon == Blocks.FLESH_HOOKED_SPEAR ? damage * 1.4
                : weapon == Blocks.FLESH_BONE_SPEAR ? damage * 1.25 : damage;
    }
}
