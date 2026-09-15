package com.gameexpert.terrain.mc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Minecraft의 Xoroshiro128++ 난수원과 위치 기반 파생을 구현한다. */
public final class McRandom {
    private static final long SILVER_RATIO_64 = 0x6A09E667F3BCC909L;
    private static final long GOLDEN_RATIO_64 = 0x9E3779B97F4A7C15L;
    private static final double DOUBLE_UNIT = 0x1.0p-53;

    private long seedLo;
    private long seedHi;

    public McRandom(long seed) {
        long lo = seed ^ SILVER_RATIO_64;
        long hi = lo + GOLDEN_RATIO_64;
        setState(mixStafford13(lo), mixStafford13(hi));
    }

    public McRandom(long seedLo, long seedHi) {
        setState(seedLo, seedHi);
    }

    private void setState(long lo, long hi) {
        if ((lo | hi) == 0L) {
            this.seedLo = GOLDEN_RATIO_64;
            this.seedHi = SILVER_RATIO_64;
        } else {
            this.seedLo = lo;
            this.seedHi = hi;
        }
    }

    public static long mixStafford13(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    public long nextLong() {
        long s0 = seedLo;
        long s1 = seedHi;
        long result = Long.rotateLeft(s0 + s1, 17) + s0;
        s1 ^= s0;
        seedLo = Long.rotateLeft(s0, 49) ^ s1 ^ s1 << 21;
        seedHi = Long.rotateLeft(s1, 28);
        return result;
    }

    public int nextInt() {
        return (int) nextLong();
    }

    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        int random = (int) nextLong();
        long product = Integer.toUnsignedLong(random) * bound;
        int low = (int) product;
        if (Integer.compareUnsigned(low, bound) < 0) {
            int threshold = Integer.remainderUnsigned(-bound, bound);
            while (Integer.compareUnsigned(low, threshold) < 0) {
                random = (int) nextLong();
                product = Integer.toUnsignedLong(random) * bound;
                low = (int) product;
            }
        }
        return (int) (product >>> 32);
    }

    public double nextDouble() {
        return (nextLong() >>> 11) * DOUBLE_UNIT;
    }

    public float nextFloat() {
        return (nextLong() >>> 40) * 0x1.0p-24f;
    }

    public boolean nextBoolean() {
        return (nextLong() & 1L) != 0L;
    }

    public McRandom fork() {
        return new McRandom(nextLong(), nextLong());
    }

    public PositionalFactory forkPositional() {
        return new PositionalFactory(nextLong(), nextLong());
    }

    /**
     * Exact {@code net.minecraft.util.Mth#getSeed(int, int, int)} of the pinned 26.3-snapshot-7
     * runtime. The x term is multiplied in {@code int} and only then widened, so it wraps for
     * {@code |x| > 686}; widening first silently changes every positional random beyond that
     * distance from the origin.
     */
    public static long coordinateSeed(int x, int y, int z) {
        long value = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
        value = value * value * 42317861L + value * 11L;
        return value >> 16;
    }

    /** 고정된 128비트 상태에서 좌표나 문자열별 독립 난수원을 만든다. */
    public static final class PositionalFactory {
        private final long seedLo;
        private final long seedHi;

        private PositionalFactory(long seedLo, long seedHi) {
            this.seedLo = seedLo;
            this.seedHi = seedHi;
        }

        public McRandom at(int x, int y, int z) {
            return new McRandom(seedLo ^ coordinateSeed(x, y, z), seedHi);
        }

        public McRandom fromHashOf(String text) {
            byte[] digest;
            try {
                digest = MessageDigest.getInstance("MD5")
                        .digest(text.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("MD5 is required by the Java runtime", exception);
            }
            ByteBuffer buffer = ByteBuffer.wrap(digest).order(ByteOrder.BIG_ENDIAN);
            return new McRandom(seedLo ^ buffer.getLong(), seedHi ^ buffer.getLong());
        }
    }
}
