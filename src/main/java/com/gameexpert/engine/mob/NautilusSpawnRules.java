package com.gameexpert.engine.mob;

/**
 * Pinned 26.3 Nautilus natural-spawn placement facts not already owned by the biome table.
 */
public final class NautilusSpawnRules {
    private NautilusSpawnRules() {}

    /** 스폰 가능 최저 Y(위키 "between Y-levels 38 and 58", 포함). */
    public static final int MIN_SPAWN_Y = 38;
    /** 스폰 가능 최고 Y(포함). */
    public static final int MAX_SPAWN_Y = 58;
    /** 수심 조건. 일반 수생 분기(SEA_LEVEL-13..SEA_LEVEL)와 달리 38..58 이다. */
    public static boolean withinSpawnDepth(int y) {
        return y >= MIN_SPAWN_Y && y <= MAX_SPAWN_Y;
    }
}
