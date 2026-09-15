package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;

/** 표지판 state의 부착 방향으로 배치와 지지 상실을 함께 판정한다. */
public final class SignSupportRules {
    private static final int WALL = 0x10;
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DZ = {-1, 0, 1, 0};

    private SignSupportRules() {}

    public static boolean isSign(int block) {
        return block == Blocks.POPLAR_SIGN || block == Blocks.POPLAR_HANGING_SIGN;
    }

    public static boolean isSupported(int block, int state, int x, int y, int z,
            SupportRules.BlockLookup lookup) {
        if ((state & WALL) == 0) {
            return solid(lookup.getBlock(x, y + (block == Blocks.POPLAR_SIGN ? -1 : 1), z));
        }
        int facing = state & 3;
        if (block == Blocks.POPLAR_SIGN) {
            int behind = (facing + 2) & 3;
            return solid(lookup.getBlock(x + DX[behind], y, z + DZ[behind]));
        }
        int side = (facing + 1) & 3;
        return solid(lookup.getBlock(x + DX[side], y, z + DZ[side]))
                || solid(lookup.getBlock(x - DX[side], y, z - DZ[side]));
    }

    private static boolean solid(int block) {
        // 비상주 이웃은 기존 지지 상실 연쇄의 보존 규약을 따른다.
        return block == WorldTickLoop.UNAVAILABLE_BLOCK || Fluids.isSolid(block);
    }
}
