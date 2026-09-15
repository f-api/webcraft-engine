package com.gameexpert.engine.mob;

import java.util.Random;

/** java.util.Random 기반 {@link MobRandom} 구현. 시드 주입으로 재현 가능. */
public final class JavaMobRandom implements MobRandom {
    private final Random random;

    public JavaMobRandom() { this.random = new Random(); }
    public JavaMobRandom(long seed) { this.random = new Random(seed); }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) return 0;
        return random.nextInt(bound);
    }

    @Override
    public float nextFloat() { return random.nextFloat(); }

    @Override
    public double nextDouble() { return random.nextDouble(); }
}
