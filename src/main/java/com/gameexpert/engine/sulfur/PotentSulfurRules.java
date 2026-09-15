package com.gameexpert.engine.sulfur;

import com.gameexpert.terrain.Blocks;

/**
 * Pure authority contract for {@code minecraft:potent_sulfur} in Java
 * 26.3-snapshot-7. Values are pinned from {@code PotentSulfurBlock} and
 * {@code PotentSulfurBlockEntity} in the verified server JAR.
 */
public final class PotentSulfurRules {
    public static final int MAX_WATER_BLOCKS_ABOVE = 4;
    /** Official 20-TPS cadence for refreshing noxious-gas effects. */
    public static final int EFFECT_FREQUENCY_TICKS = 10;
    /** Official nausea duration in 20-TPS Minecraft ticks. */
    public static final int NAUSEA_DURATION_TICKS = 80;
    /** Horizontal block radius around a valid potent-sulfur water column. */
    public static final double EFFECT_RANGE = 3.0;
    public static final int PARTICLE_FREQUENCY_TICKS = 20;
    public static final int SOUND_FREQUENCY_TICKS = 40;
    public static final double BASE_LAUNCH_SPEED = 0.3;
    public static final double LAUNCH_FORCE_PER_TICK = 0.2;
    public static final long GEYSER_SALT = -904_011_478L;

    public enum State { DRY, WET, DORMANT, ERUPTING, CONTINUOUS }

    private PotentSulfurRules() {}

    /** Exact placement/neighbour state selection. */
    public static State state(boolean sourceWaterImmediatelyAbove, int blockBelow,
            boolean belowFluidIsSource, State previous) {
        if (!sourceWaterImmediatelyAbove) return State.DRY;
        if (blockBelow == Blocks.LAVA_SOURCE && belowFluidIsSource) return State.CONTINUOUS;
        if (blockBelow == Blocks.MAGMA) {
            return previous == State.ERUPTING ? State.ERUPTING : State.DORMANT;
        }
        return State.WET;
    }

    /** The official plume scans at most six passable cells per source-water block. */
    public static int maxPlumeBlocks(int waterBlocks) {
        return Math.max(0, Math.min(MAX_WATER_BLOCKS_ABOVE, waterBlocks)) * 6;
    }

    /** Maximum vertical velocity before the +0.2/tick force stops being applied. */
    public static double launchVelocityLimit(int waterBlocks) {
        return BASE_LAUNCH_SPEED + Math.max(0, waterBlocks) * 0.1;
    }

    /**
     * Vertical knockback input for one authority tick inside an erupting plume. The shared client
     * composes repeated knockback as {@code velocity / 2 + impulse}, so the official per-tick force
     * joins the base launch speed here and is clamped by the water-column velocity ceiling.
     */
    public static double launchImpulse(int waterBlocks) {
        return Math.min(launchVelocityLimit(waterBlocks),
                BASE_LAUNCH_SPEED + LAUNCH_FORCE_PER_TICK);
    }

    /** Countdown values are decremented once per second by the official ticker. */
    public static int nextCountdown(State state, int waterBlocks, int inclusiveRandom) {
        int heightTerm = Math.max(0, waterBlocks - 1);
        return switch (state) {
            case DORMANT -> 10 * heightTerm + clamp(inclusiveRandom, 15, 30);
            case ERUPTING -> heightTerm + clamp(inclusiveRandom, 1, 2);
            default -> -1;
        };
    }

    public static State togglePeriodic(State state) {
        return state == State.DORMANT ? State.ERUPTING : State.DORMANT;
    }

    /** Deterministic loaded-world projection of the official dormant/erupting countdown. */
    public static boolean periodicErupting(long authorityTick, int waterBlocks,
            int x, int y, int z) {
        int dormantSeconds = nextCountdown(State.DORMANT, waterBlocks,
                15 + Math.floorMod(lane(x, y, z, 1), 16));
        int eruptingSeconds = nextCountdown(State.ERUPTING, waterBlocks,
                1 + Math.floorMod(lane(x, y, z, 2), 2));
        int cycleAuthorityTicks = (dormantSeconds + eruptingSeconds) * 10;
        long phase = Math.floorMod(authorityTick
                + Math.floorMod(lane(x, y, z, 3), cycleAuthorityTicks), cycleAuthorityTicks);
        return phase >= (long) dormantSeconds * 10;
    }

    private static int lane(int x, int y, int z, int salt) {
        int hash = x * 0x9e3779b9;
        hash = Integer.rotateLeft(hash ^ y * 0x85ebca6b, 13);
        hash ^= z * 0xc2b2ae35;
        hash ^= (int) GEYSER_SALT * salt;
        return hash ^ hash >>> 16;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
