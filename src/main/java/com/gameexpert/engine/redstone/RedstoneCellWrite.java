package com.gameexpert.engine.redstone;

/** [REDSTONE] 엔진이 권위 월드에 쓴 셀 하나(TS {@code RedstoneCellWrite}). */
public final class RedstoneCellWrite {
    public final int x;
    public final int y;
    public final int z;
    public final int blockType;
    public final int state;

    public RedstoneCellWrite(int x, int y, int z, int blockType, int state) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockType = blockType;
        this.state = state;
    }

    @Override
    public String toString() {
        return "RedstoneCellWrite[" + x + "," + y + "," + z + " " + blockType + ":" + state + "]";
    }
}
