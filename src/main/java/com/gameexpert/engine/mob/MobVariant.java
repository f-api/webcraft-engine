package com.gameexpert.engine.mob;

/** RNG 상태를 소비하지 않는 몹 변종 선택용 공통 좌표 해시. */
public final class MobVariant {
    private MobVariant() {}

    public static int deterministicIndex(int worldSeed, long mobId,
                                         double spawnX, double spawnZ, int choices) {
        if (choices <= 0) throw new IllegalArgumentException("choices must be positive");
        long hash = mix64(worldSeed);
        hash = mix64(hash ^ mobId);
        hash = mix64(hash ^ Double.doubleToLongBits(spawnX));
        hash = mix64(hash ^ Double.doubleToLongBits(spawnZ));
        return (int) Long.remainderUnsigned(hash, choices);
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
