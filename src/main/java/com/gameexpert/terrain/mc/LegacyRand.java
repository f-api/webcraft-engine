package com.gameexpert.terrain.mc;

/** Minecraft's legacy 48-bit Java random source. */
public final class LegacyRand {
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long ADDEND = 0xBL;
    private static final long MASK = (1L << 48) - 1L;

    private long seed;
    private final Observer observer;

    public LegacyRand(long seed) {
        this(seed, null);
    }

    private LegacyRand(long seed, Observer observer) {
        this.observer = observer;
        setSeed(seed);
    }

    public static LegacyRand fromSeed(long seed) {
        return new LegacyRand(seed);
    }

    /** Creates one legacy stream whose native transitions are observed without extra draws. */
    public static LegacyRand observed(long seed, Observer observer) {
        if (observer == null) throw new IllegalArgumentException("random observer is required");
        return new LegacyRand(seed, observer);
    }

    public void setSeed(long seed) {
        this.seed = (seed ^ MULTIPLIER) & MASK;
        if (observer != null) observer.onSetSeed(seed);
    }

    public int nextInt() {
        return next(32);
    }

    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        if ((bound & -bound) == bound) {
            return (int) ((bound * (long) next(31)) >> 31);
        }
        int bits;
        int value;
        do {
            bits = next(31);
            value = bits % bound;
        } while (bits - value + (bound - 1) < 0);
        return value;
    }

    public long nextLong() {
        return ((long) next(32) << 32) + next(32);
    }

    public float nextFloat() {
        return next(24) * 0x1.0p-24F;
    }

    public double nextDouble() {
        long value = ((long) next(26) << 27) + next(27);
        return value * 0x1.0p-53;
    }

    public boolean nextBoolean() {
        return next(1) != 0;
    }

    /**
     * Minecraft {@code LegacyRandomSource.forkPositional}: consumes one legacy {@code nextLong}
     * and retains that signed 64-bit value as the positional factory seed.
     */
    public PositionalFactory forkPositional() {
        return new PositionalFactory(nextLong());
    }

    /** Consumes the exact number of 32-bit draws used by skipped Minecraft noise octaves. */
    public void consume(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        for (int index = 0; index < count; index++) {
            next(32);
        }
    }

    /** Current native 48-bit state, without advancing the stream. */
    public long state48() {
        return seed;
    }

    /**
     * Computes a signed {@code nextLong} continuation from the current state without mutating or
     * observing this stream. This advances only a local scalar state and never replays callers.
     */
    public long[] continuationNextLong(int count) {
        if (count < 0) throw new IllegalArgumentException("count must not be negative");
        long state = seed;
        long[] values = new long[count];
        for (int index = 0; index < count; index++) {
            state = nextState(state);
            int high = (int) (state >>> 16);
            state = nextState(state);
            int low = (int) (state >>> 16);
            values[index] = ((long) high << 32) + low;
        }
        return values;
    }

    private int next(int bits) {
        seed = nextState(seed);
        int result = (int) (seed >>> (48 - bits));
        if (observer != null) observer.onNextBits(bits, result, seed);
        return result;
    }

    private static long nextState(long state) {
        return (state * MULTIPLIER + ADDEND) & MASK;
    }

    /** Read-only callback for the exact native transitions of one observed legacy stream. */
    public interface Observer {
        void onSetSeed(long seed);
        void onNextBits(int bits, int result, long state48After);
    }

    /** Minecraft's legacy, Java-String-hash positional random factory. */
    public static final class PositionalFactory {
        private final long seed;

        private PositionalFactory(long seed) {
            this.seed = seed;
        }

        /**
         * Unlike the modern Xoroshiro positional factory, the legacy factory uses
         * {@link String#hashCode()} rather than MD5.
         */
        public LegacyRand fromHashOf(String text) {
            if (text == null) throw new IllegalArgumentException("text is required");
            return new LegacyRand(seed ^ (long) text.hashCode());
        }

        public long seed() {
            return seed;
        }
    }
}
