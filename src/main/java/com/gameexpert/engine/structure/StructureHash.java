package com.gameexpert.engine.structure;

/** Pure int32 hash lanes. Every semantic lane starts from an independently mixed salt. */
public final class StructureHash {
    private StructureHash() {}

    public static int seedSalt(int seed, int salt) {
        return mix32(seed ^ salt);
    }

    public static int hash3(int seed, int x, int y, int z) {
        int h = mix32(seed ^ x * 0x9e3779b9);
        h = mix32(h ^ y * 0x85ebca6b);
        return mix32(h ^ z * 0xc2b2ae35);
    }

    public static int mix32(int value) {
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        return value ^ value >>> 16;
    }

    public static long unsigned(int value) {
        return Integer.toUnsignedLong(value);
    }
}
