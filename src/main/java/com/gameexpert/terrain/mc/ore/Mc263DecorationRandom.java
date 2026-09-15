package com.gameexpert.terrain.mc.ore;

import com.gameexpert.terrain.mc.McRandom;
import com.gameexpert.terrain.mc.feature.Mc263FeatureIndexReceipt;

/** Exact Xoroshiro decoration/feature seed derivation used by the pinned 26.3 generator. */
public final class Mc263DecorationRandom {
    private Mc263DecorationRandom() {
    }

    public static long decorationSeed(long worldSeed, int sourceBlockX, int sourceBlockZ) {
        return new WorldgenRandom(0L).setDecorationSeed(
                worldSeed, sourceBlockX, sourceBlockZ);
    }

    public static long featureSeed(long decorationSeed, int globalIndex, int step) {
        if (globalIndex < 0) {
            throw new IllegalArgumentException("global feature index must be non-negative");
        }
        if (step < 0 || step >= Mc263FeatureIndexReceipt.STEP_COUNT) {
            throw new IllegalArgumentException("generation step outside pinned range: " + step);
        }
        return decorationSeed + globalIndex + (long) step * 10_000L;
    }

    public static int globalIndex(int step, String placedFeatureKey) {
        if (placedFeatureKey == null || placedFeatureKey.isBlank()) {
            throw new IllegalArgumentException("placed feature key is required");
        }
        String key = placedFeatureKey.startsWith("minecraft:")
                ? placedFeatureKey : "minecraft:" + placedFeatureKey;
        return Mc263FeatureIndexReceipt.step(step).indexOf(key);
    }

    public static FeatureRandom forFeature(long worldSeed, int sourceBlockX, int sourceBlockZ,
            int step, String placedFeatureKey) {
        int globalIndex = globalIndex(step, placedFeatureKey);
        long decorationSeed = decorationSeed(worldSeed, sourceBlockX, sourceBlockZ);
        long featureSeed = featureSeed(decorationSeed, globalIndex, step);
        return new FeatureRandom(decorationSeed, featureSeed, globalIndex,
                new WorldgenRandom(featureSeed));
    }

    public record FeatureRandom(long decorationSeed, long featureSeed, int globalIndex,
                                WorldgenRandom random) {
        public FeatureRandom {
            if (random == null) {
                throw new IllegalArgumentException("random is required");
            }
        }
    }

    /**
     * Exact {@code WorldgenRandom(new XoroshiroRandomSource(seed))} bit-consumption adapter.
     * WorldgenRandom inherits BitRandomSource's legacy combination algorithms rather than calling
     * XoroshiroRandomSource's public convenience methods.
     */
    public static final class WorldgenRandom implements Mc263WorldgenRandomSource {
        private static final float FLOAT_UNIT = 0x1.0p-24f;
        private static final double DOUBLE_UNIT = 0x1.0p-53;
        private McRandom xoroshiro;
        private boolean haveNextNextGaussian;
        private double nextNextGaussian;

        public WorldgenRandom(long seed) {
            this.xoroshiro = new McRandom(seed);
        }

        /**
         * Mirrors {@code WorldgenRandom#setSeed}: reseed the delegate without clearing the
         * Gaussian cache inherited by the official wrapper from {@code LegacyRandomSource}.
         */
        public void setSeed(long seed) {
            this.xoroshiro = new McRandom(seed);
        }

        public long setDecorationSeed(long seed, int sourceBlockX, int sourceBlockZ) {
            setSeed(seed);
            long xScale = nextLong() | 1L;
            long zScale = nextLong() | 1L;
            long result = (long) sourceBlockX * xScale
                    + (long) sourceBlockZ * zScale ^ seed;
            setSeed(result);
            return result;
        }

        public void setFeatureSeed(long decorationSeed, int globalIndex, int step) {
            setSeed(featureSeed(decorationSeed, globalIndex, step));
        }

        public int next(int bits) {
            if (bits < 1 || bits > 32) {
                throw new IllegalArgumentException("bit count must be in 1..32");
            }
            return (int) (xoroshiro.nextLong() >>> (64 - bits));
        }

        public int nextInt() {
            return next(32);
        }

        public int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("bound must be positive");
            }
            if ((bound & (bound - 1)) == 0) {
                return (int) ((long) bound * (long) next(31) >> 31);
            }
            int sample;
            int modulo;
            do {
                sample = next(31);
                modulo = sample % bound;
            } while (sample - modulo + (bound - 1) < 0);
            return modulo;
        }

        public long nextLong() {
            int upper = next(32);
            int lower = next(32);
            return ((long) upper << 32) + (long) lower;
        }

        public boolean nextBoolean() {
            return next(1) != 0;
        }

        public float nextFloat() {
            return (float) next(24) * FLOAT_UNIT;
        }

        public double nextDouble() {
            int upper = next(26);
            int lower = next(27);
            return (((long) upper << 27) + (long) lower) * DOUBLE_UNIT;
        }

        /** Exact wrapper-owned {@code MarsagliaPolarGaussian} state and draw order. */
        public double nextGaussian() {
            if (haveNextNextGaussian) {
                haveNextNextGaussian = false;
                return nextNextGaussian;
            }
            double x;
            double y;
            double radiusSquared;
            do {
                x = 2.0 * nextDouble() - 1.0;
                y = 2.0 * nextDouble() - 1.0;
                radiusSquared = x * x + y * y;
            } while (radiusSquared >= 1.0 || radiusSquared == 0.0);
            double multiplier = Math.sqrt(-2.0 * Math.log(radiusSquared) / radiusSquared);
            nextNextGaussian = y * multiplier;
            haveNextNextGaussian = true;
            return x * multiplier;
        }
    }
}
