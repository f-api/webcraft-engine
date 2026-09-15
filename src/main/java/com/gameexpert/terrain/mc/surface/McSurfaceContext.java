package com.gameexpert.terrain.mc.surface;

/** Surface-rule evaluation에 필요한 서버 생성 상태의 읽기 전용 view다. */
public interface McSurfaceContext {
    int blockX();
    int blockY();
    int blockZ();
    int minY();
    int height();
    int seaLevel();
    int stoneDepthAbove();
    int stoneDepthBelow();
    int waterHeight();
    int surfaceDepth();
    double secondarySurfaceNoise();
    String biome();
    float adjustedTemperature();

    /** 청크 로컬 (0..15, 0..15)의 motion-blocking 최상단 exclusive Y다. */
    int topBlockHeightExclusive(int localX, int localZ);
}
