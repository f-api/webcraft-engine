package com.gameexpert.engine.blocks;

import com.gameexpert.terrain.Blocks;

/** Java 26.3 shelf-mushroom AGE state contract. */
public final class P29Rules {
    public static final int AGE = 0x04;
    public static final double BOUNCE_RESTITUTION = 0.75;
    public static final double FALL_DISTANCE_REDUCTION = 0.5;
    private P29Rules() {
    }

    /** AGE=0 only; the grown form is the same block with AGE=1. */
    public static boolean isBoneMealTarget(int blockId, int state) {
        return blockId == Blocks.SHELF_MUSHROOM && (state & AGE) == 0;
    }

    /** Returns the next state, or -1 when bonemeal is not applicable. */
    public static int boneMealStateResult(int blockId, int state) {
        return isBoneMealTarget(blockId, state) ? state | AGE : -1;
    }

    /** ShelfMushroomBlock.bounceOn excludes only ItemEntity and PrimedTnt. */
    public static boolean playsBounceSound(boolean itemEntity, boolean primedTnt) {
        return !itemEntity && !primedTnt;
    }
}
