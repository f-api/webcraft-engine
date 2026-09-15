package com.gameexpert.engine.redstone;

import java.util.List;

/** [REDSTONE] 피스톤 이동 애니메이션 이벤트(TS {@code RedstonePistonMove}). */
public final class RedstonePistonMove {
    /** 피스톤이 옮기는 블록 하나(TS {@code RedstonePistonMovedBlock}). 좌표는 출발 칸. */
    public static final class MovedBlock {
        public final int x;
        public final int y;
        public final int z;
        public final int blockType;
        public final int state;

        public MovedBlock(int x, int y, int z, int blockType, int state) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockType = blockType;
            this.state = state;
        }
    }

    public final int x;
    public final int y;
    public final int z;
    /** F6 facing. */
    public final int facing;
    public final boolean extending;
    public final boolean sticky;
    /** 이동 방향(DIR). 늘어나면 facing, 줄어들면 반대. */
    public final int moveDir;
    public final List<MovedBlock> blocks;
    /** 절대 게임 틱(애니메이션 시작). */
    public final long gameTime;

    public RedstonePistonMove(int x, int y, int z, int facing, boolean extending, boolean sticky, int moveDir,
            List<MovedBlock> blocks, long gameTime) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
        this.extending = extending;
        this.sticky = sticky;
        this.moveDir = moveDir;
        this.blocks = List.copyOf(blocks);
        this.gameTime = gameTime;
    }
}
