package com.gameexpert.engine.redstone;

/** [REDSTONE] 움직이는 피스톤 블록 엔티티({@code PistonMovingBlockEntity}, TS {@code RedstoneMovingBlock}). */
public final class RedstoneMovingBlock {
    public final int x;
    public final int y;
    public final int z;
    public int movedBlock;
    public int movedState;
    /** DIR. */
    public int direction;
    public boolean extending;
    public boolean source;
    public double progress;
    public double progressO;
    public long lastTicked;

    public RedstoneMovingBlock(int x, int y, int z, int movedBlock, int movedState, int direction, boolean extending,
            boolean source, double progress, double progressO, long lastTicked) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.movedBlock = movedBlock;
        this.movedState = movedState;
        this.direction = direction;
        this.extending = extending;
        this.source = source;
        this.progress = progress;
        this.progressO = progressO;
        this.lastTicked = lastTicked;
    }

    RedstoneMovingBlock copy(long lastTicked) {
        return new RedstoneMovingBlock(x, y, z, movedBlock, movedState, direction, extending, source, progress,
                progressO, lastTicked);
    }
}
