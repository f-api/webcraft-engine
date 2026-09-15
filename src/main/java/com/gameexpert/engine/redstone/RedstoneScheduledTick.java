package com.gameexpert.engine.redstone;

/** [REDSTONE] 예약된 블록 틱({@code ScheduledTick}, TS {@code RedstoneScheduledTick}). */
public final class RedstoneScheduledTick {
    public final int x;
    public final int y;
    public final int z;
    /** {@link RedstoneEngine#redstoneVanillaBlock(int)} 대표 ID. */
    public final int block;
    public final long triggerTick;
    public final int priority;
    public final long subTickOrder;

    public RedstoneScheduledTick(int x, int y, int z, int block, long triggerTick, int priority, long subTickOrder) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.block = block;
        this.triggerTick = triggerTick;
        this.priority = priority;
        this.subTickOrder = subTickOrder;
    }
}
