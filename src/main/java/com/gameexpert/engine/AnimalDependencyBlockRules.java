package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;

/** Registered animal-dependent blocks whose lifecycle does not belong to mob AI. */
public final class AnimalDependencyBlockRules {
    public static final int FROGSPAWN_MIN_HATCH_TICKS = 3_600;
    public static final int FROGSPAWN_MAX_HATCH_TICKS = 12_000;
    public static final int SNIFFER_EGG_HATCH_TICKS = 24_000;
    public static final int SNIFFER_EGG_BOOSTED_HATCH_TICKS = 12_000;
    public static final int HATCH_AGE_MASK = 0x03;
    public static final int SNIFFER_FINAL_AGE = 2;
    public static final int CROP_AGE_MASK = 0x07;
    public static final int TORCHFLOWER_MAX_AGE = 2;
    public static final int PITCHER_MAX_AGE = 4;

    public enum SpawnKind { TADPOLE, SNIFFER }

    public static final class ScheduledStep {
        private final int nextState;
        private final int nextDelay;
        private final SpawnKind spawn;

        private ScheduledStep(int nextState, int nextDelay, SpawnKind spawn) {
            this.nextState = nextState;
            this.nextDelay = nextDelay;
            this.spawn = spawn;
        }
        public int nextState() { return nextState; }
        public int nextDelay() { return nextDelay; }
        public SpawnKind spawn() { return spawn; }
        public boolean requestsSpawn() { return spawn != null; }
    }

    private AnimalDependencyBlockRules() {}

    public static int frogspawnHatchDelay(IntBoundRandom random) {
        return FROGSPAWN_MIN_HATCH_TICKS + random.nextInt(
                FROGSPAWN_MAX_HATCH_TICKS - FROGSPAWN_MIN_HATCH_TICKS + 1);
    }

    public static int deterministicFrogspawnHatchDelay(
            int worldSeed, int x, int y, int z, long absoluteGameTime) {
        int hash = mix(worldSeed ^ x * 0x1f123bb5 ^ y * 0x6d2b79f5
                ^ z * 0x5bd1e995 ^ (int) absoluteGameTime);
        int bound = FROGSPAWN_MAX_HATCH_TICKS - FROGSPAWN_MIN_HATCH_TICKS + 1;
        long range = 1L << 32;
        long limit = range - range % bound;
        long unsigned = Integer.toUnsignedLong(hash);
        while (unsigned >= limit) {
            hash = mix(hash + 0x9e3779b9);
            unsigned = Integer.toUnsignedLong(hash);
        }
        return FROGSPAWN_MIN_HATCH_TICKS + (int) (unsigned % bound);
    }

    private static int mix(int value) {
        int mixed = value;
        mixed = (mixed ^ mixed >>> 16) * 0x7feb352d;
        mixed = (mixed ^ mixed >>> 15) * 0x846ca68b;
        return mixed ^ mixed >>> 16;
    }

    public static int snifferEggHatchDelay(boolean boosted) {
        return boosted ? SNIFFER_EGG_BOOSTED_HATCH_TICKS : SNIFFER_EGG_HATCH_TICKS;
    }

    public static int snifferEggNextDelay(boolean boosted) {
        return snifferEggHatchDelay(boosted) / 3;
    }

    public static ScheduledStep frogspawnStep() {
        return new ScheduledStep(0, 1, SpawnKind.TADPOLE);
    }

    public static ScheduledStep snifferEggStep(int state, boolean boosted) {
        int age = state & HATCH_AGE_MASK;
        if (age >= SNIFFER_FINAL_AGE) return new ScheduledStep(age, 1, SpawnKind.SNIFFER);
        return new ScheduledStep(age + 1, snifferEggNextDelay(boosted), null);
    }

    public static int cropMaxAge(int block) {
        return block == Blocks.TORCHFLOWER_CROP ? TORCHFLOWER_MAX_AGE
                : block == Blocks.PITCHER_CROP ? PITCHER_MAX_AGE : -1;
    }

    public static int cropAge(int state) { return state & CROP_AGE_MASK; }

    public static int cropDropItem(int block, int state) {
        if (block == Blocks.TORCHFLOWER_CROP) {
            return cropAge(state) >= TORCHFLOWER_MAX_AGE
                    ? Blocks.TORCHFLOWER : Blocks.TORCHFLOWER_SEEDS;
        }
        // [PITCHER] blocks/pitcher_crop.json: the lower half only — pod at age 0..3, pitcher plant at 4.
        if (block == Blocks.PITCHER_CROP) {
            return PitcherRules.isUpper(state) ? Blocks.AIR : PitcherRules.cropLootItem(state);
        }
        if (block == Blocks.TORCHFLOWER) return Blocks.TORCHFLOWER;
        return Blocks.AIR;
    }

    public static boolean hasSelfDrop(int block) {
        return block != Blocks.FROGSPAWN && block != Blocks.BEE_NEST;
    }

    public static double hardness(int block) {
        return block == Blocks.BEE_NEST ? 0.3
                : block == Blocks.BEEHIVE || block == Blocks.HONEYCOMB_BLOCK ? 0.6
                : block == Blocks.HONEY_BLOCK ? 0.0 : -1.0;
    }

    public static boolean axeEffective(int block) {
        return block == Blocks.BEE_NEST || block == Blocks.BEEHIVE;
    }

    public static boolean honeyBlockSticky() { return true; }
    public static double honeyBlockHorizontalMultiplier() { return 0.4; }
    public static double honeyBlockJumpMultiplier() { return 0.5; }
    public static double honeyBlockSlideSpeed() { return 0.05; }
    public static double honeyBlockFallDamageMultiplier() { return 0.2; }
    public static double honeyBlockCollisionInset() { return 1.0 / 16.0; }

    @FunctionalInterface
    public interface IntBoundRandom { int nextInt(int bound); }
}
