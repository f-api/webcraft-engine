package com.gameexpert.engine.mob;

/** Shared, allocation-free constants for ordinary animal goal ordering. */
final class AnimalGoalRules {
    static final double TEMPT_RADIUS = 10.0;
    static final double TEMPT_RADIUS_SQUARED = TEMPT_RADIUS * TEMPT_RADIUS;
    static final int TEMPT_COOLDOWN_TICKS = 50; // 100 MC ticks at the 10 TPS authority.
    static final double FOLLOW_PARENT_HORIZONTAL = 8.0;
    static final double FOLLOW_PARENT_VERTICAL = 4.0;
    static final double FOLLOW_PARENT_STOP_DISTANCE_SQUARED = 9.0;
    static final double FOLLOW_PARENT_SPEED_MULTIPLIER = 1.1;

    private AnimalGoalRules() {}

    static double panicSpeedMultiplier(MobType type) {
        return switch (type) {
            case COW -> 2.0;
            case PIG, SHEEP -> 1.25;
            case CHICKEN -> 1.4;
            case RABBIT, FOX -> 2.2;
            case WOLF -> 1.5;
            default -> 1.5;
        };
    }

    static double temptSpeedMultiplier(MobType type) {
        return switch (type) {
            case COW -> 1.25;
            case PIG -> 1.2;
            case SHEEP -> 1.1;
            case CHICKEN, RABBIT -> 1.0;
            // Cat and Ocelot register the same 0.6 TemptGoal speed. Goat/Frog/Sniffer
            // brain FollowTemptation behaviours use 1.25 in Java 1.21.4.
            case CAT, OCELOT -> 0.6;
            case FROG, GOAT, SNIFFER -> 1.25;
            default -> 1.0;
        };
    }

    /** Species whose 1.21.4 Tempt constructor speed is pinned in the local reference set. */
    static boolean hasPinnedTemptGoal(MobType type) {
        return switch (type) {
            case COW, PIG, SHEEP, CHICKEN, RABBIT, CAT, FROG, GOAT, OCELOT,
                    SNIFFER -> true;
            default -> false;
        };
    }

    static boolean followsParent(MobType type) {
        return switch (type) {
            // Frog breeding places frogspawn and has no baby-Frog follow goal.
            case COW, PIG, SHEEP, CHICKEN, RABBIT, CAT, GOAT, SNIFFER -> true;
            default -> false;
        };
    }

    /** Exact FollowParent/BabyFollowAdult movement multiplier for the registered goal. */
    static double followParentSpeedMultiplier(MobType type) {
        return switch (type) {
            case GOAT, SNIFFER -> 1.25;
            default -> FOLLOW_PARENT_SPEED_MULTIPLIER;
        };
    }
}
