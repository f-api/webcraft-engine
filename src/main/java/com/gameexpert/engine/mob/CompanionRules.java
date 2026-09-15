package com.gameexpert.engine.mob;

import com.gameexpert.engine.inventory.PlayerInventory;

/** Cat/Parrot mobInteract and FollowOwnerGoal, pinned Java 26.3-snapshot-7. */
public final class CompanionRules {
    private CompanionRules() { }

    public static boolean isCompanion(MobType type) {
        return type == MobType.CAT || type == MobType.PARROT;
    }

    public static boolean isFood(MobType type, short item) {
        if (type == MobType.CAT) {
            return item == PlayerInventory.COD_RAW || item == PlayerInventory.SALMON_RAW;
        }
        return type == MobType.PARROT && (item == PlayerInventory.WHEAT_SEEDS
                || item == PlayerInventory.MELON_SEEDS || item == PlayerInventory.PUMPKIN_SEEDS
                || item == PlayerInventory.BEETROOT_SEEDS || item == PlayerInventory.TORCHFLOWER_SEEDS
                || item == PlayerInventory.PITCHER_POD);
    }

    public static double followStart(MobType type) { return type == MobType.CAT ? 10 : 5; }
    public static double followStop(MobType type) { return type == MobType.CAT ? 5 : 1; }
}
