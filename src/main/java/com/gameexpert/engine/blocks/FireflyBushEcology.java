package com.gameexpert.engine.blocks;

import com.gameexpert.terrain.Blocks;

/** Pure shared facts for the 1.21.5 firefly-bush particle and ambience host. */
public final class FireflyBushEcology {
    public static final int MAX_PARTICLE_INTERNAL_LIGHT = 13;
    public static final double PARTICLE_CHANCE_PER_GAME_TICK = 0.70;
    // WebCraft authority runs 10 TPS, so each stored clock tick is two Minecraft game ticks.
    public static final int AMBIENCE_START_TICK = 6_300;
    public static final int AMBIENCE_END_TICK = 11_700;
    public static final int DAY_TICKS = 12_000;

    private FireflyBushEcology() {
    }

    /** A hosted firefly is renewable presentation, so consuming it never mutates the bush. */
    public static boolean isConsumableHost(int blockId, int internalLight) {
        return blockId == Blocks.FIREFLY_BUSH
                && internalLight >= 0
                && internalLight <= MAX_PARTICLE_INTERNAL_LIGHT;
    }

    public static boolean emitsParticle(int blockId, int internalLight, double unitRoll) {
        if (!Double.isFinite(unitRoll) || unitRoll < 0.0 || unitRoll >= 1.0) {
            throw new IllegalArgumentException("unitRoll must be finite and in [0, 1)");
        }
        return isConsumableHost(blockId, internalLight)
                && unitRoll < PARTICLE_CHANCE_PER_GAME_TICK;
    }

    public static boolean playsAmbientSound(int blockId, long worldTick, boolean collisionClearAbove) {
        long dayTick = Math.floorMod(worldTick, DAY_TICKS);
        return blockId == Blocks.FIREFLY_BUSH
                && collisionClearAbove
                && dayTick >= AMBIENCE_START_TICK
                && dayTick <= AMBIENCE_END_TICK;
    }
}
