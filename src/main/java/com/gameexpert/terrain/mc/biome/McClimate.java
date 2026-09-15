package com.gameexpert.terrain.mc.biome;

import java.util.Arrays;

/** 바이옴 검색에 쓰는 Minecraft 6축 기후 점이다. */
public final class McClimate {
    public static final int AXES = 6;

    private final long[] values;

    public McClimate(long temperature, long humidity, long continentalness,
            long erosion, long depth, long weirdness) {
        values = new long[] {temperature, humidity, continentalness, erosion, depth, weirdness};
    }

    public static McClimate fromFloats(float temperature, float humidity, float continentalness,
            float erosion, float depth, float weirdness) {
        return new McClimate(quantize(temperature), quantize(humidity), quantize(continentalness),
                quantize(erosion), quantize(depth), quantize(weirdness));
    }

    /** C의 유한 float→int64 cast와 같은 0 방향 절삭이다. */
    public static long quantize(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("기후 값은 유한해야 합니다");
        }
        return (long) (10000.0F * value);
    }

    public long value(int axis) {
        return values[axis];
    }

    public long[] values() {
        return Arrays.copyOf(values, values.length);
    }
}
