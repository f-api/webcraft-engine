package com.gameexpert.engine.dragon;

/**
 * [DRAGON] 드래곤전 전용 48비트 LCG({@code java.util.Random} 과 같은 수열). 두 권위가 같은 시드에서 같은
 * 판단을 내리도록 정적판 {@code DragonRandom.ts} 가 같은 연산을 BigInt 없이 옮긴다. 바닐라 엔티티 난수
 * 원천(Xoroshiro)의 수열 자체는 옮기지 않는다 — 분포·호출 자리만 바닐라와 같다.
 */
public final class DragonRandom {
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long MASK = (1L << 48) - 1;
    private long seed;

    public DragonRandom(long seed) {
        setSeed(seed);
    }

    public void setSeed(long seed) {
        this.seed = (seed ^ MULTIPLIER) & MASK;
    }

    /** 현재 48비트 상태(영속·검사용). */
    public long state() {
        return seed;
    }

    public void restoreState(long state) {
        this.seed = state & MASK;
    }

    private int next(int bits) {
        seed = seed * MULTIPLIER + 0xBL & MASK;
        return (int) (seed >>> 48 - bits);
    }

    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
        if ((bound & -bound) == bound) return (int) ((long) bound * next(31) >> 31);
        int bits;
        int value;
        do {
            bits = next(31);
            value = bits % bound;
        } while (bits - value + (bound - 1) < 0);
        return value;
    }

    public float nextFloat() {
        return next(24) / (float) (1 << 24);
    }

    public double nextDouble() {
        return (((long) next(26) << 27) + next(27)) * 0x1.0p-53;
    }

    public long nextLong() {
        return ((long) next(32) << 32) + next(32);
    }
}
