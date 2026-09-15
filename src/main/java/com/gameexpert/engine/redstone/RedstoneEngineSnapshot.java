package com.gameexpert.engine.redstone;

import java.util.List;

/**
 * [REDSTONE] 권위가 영속하는 엔진 상태(TS {@code RedstoneEngineSnapshot}). 움직이는 블록의
 * {@code lastTicked} 는 영속하지 않는다(복원 때 −1).
 */
public final class RedstoneEngineSnapshot {
    public final List<RedstoneScheduledTick> ticks;
    public final List<RedstoneMovingBlock> movingBlocks;
    /** 각 원소는 [x, y, z]. */
    public final List<int[]> daylightDetectors;
    public final long subTickCounter;

    public RedstoneEngineSnapshot(List<RedstoneScheduledTick> ticks, List<RedstoneMovingBlock> movingBlocks,
            List<int[]> daylightDetectors, long subTickCounter) {
        this.ticks = List.copyOf(ticks);
        this.movingBlocks = List.copyOf(movingBlocks);
        this.daylightDetectors = List.copyOf(daylightDetectors);
        this.subTickCounter = subTickCounter;
    }
}
