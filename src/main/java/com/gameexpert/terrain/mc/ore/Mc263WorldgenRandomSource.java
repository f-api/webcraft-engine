package com.gameexpert.terrain.mc.ore;

/**
 * Minimal caller-owned random stream consumed by the exact 26.3 configured-feature kernels.
 * Implementations must preserve their native state and draw accounting; callers never bridge by
 * copying or reseeding state.
 */
public interface Mc263WorldgenRandomSource {
    int nextInt(int bound);
    boolean nextBoolean();
    float nextFloat();
}
