package com.gameexpert.engine;

import com.gameexpert.engine.blocks.P6Rules;
import com.gameexpert.engine.blocks.CandleRules;
import com.gameexpert.terrain.Blocks;

/** 26.3-snapshot-7 공식 충돌 박스. 렌더 외곽과 다른 폭도 그대로 보존한다. */
final class DecorativeCollisionShapes {
    private static final double[][] CHAIN = axisBoxes(6.5);
    private static final double[][] ROD = axisBoxes(6);
    private static final double[] LILY_PAD = {1, 0, 1, 15, 1.5, 15};
    private static final double[] SEA_PICKLE = {6, 0, 6, 10, 6, 10};
    /** index = up(bit0) | down(bit1): 후렴 식물 state 의 up=16·down=32 비트. */
    private static final double[][] CHORUS_PLANT = {
        {3, 3, 3, 13, 13, 13}, {3, 3, 3, 13, 16, 13}, {3, 0, 3, 13, 13, 13}, {3, 0, 3, 13, 16, 13}
    };
    private static final double[][] CANDLES = {
        {7, 0, 7, 9, 6, 9}, {5, 0, 6, 11, 6, 9},
        {5, 0, 6, 10, 6, 11}, {5, 0, 5, 11, 6, 10}
    };
    private static final double[][][] AMETHYST = {
        facingBoxes(3, 4), facingBoxes(4, 3), facingBoxes(5, 3), facingBoxes(7, 3)
    };

    private DecorativeCollisionShapes() { }

    /** [DRAGON] 드래곤 알 {@code Block.column(14, 0, 16)}. */
    private static final double[] DRAGON_EGG = {1, 0, 1, 15, 16, 15};
    /** [END-CITY] 드래곤 머리: 바닥 {@code SkullBlock.SHAPE = Block.column(8, 0, 8)}. */
    private static final double[] DRAGON_HEAD_FLOOR = {4, 0, 4, 12, 8, 12};
    /**
     * [END-CITY] 벽 드래곤 머리 {@code WallSkullBlock} 모양(북·동·남·서): 북쪽을 보면 벽은 남쪽이라
     * box(4,4,8,12,12,16), 나머지는 수평 회전.
     */
    private static final double[][] DRAGON_HEAD_WALL = {
        {4, 4, 8, 12, 12, 16}, {0, 4, 4, 8, 12, 12}, {4, 4, 0, 12, 12, 8}, {8, 4, 4, 16, 12, 12},
    };

    /** 반환 좌표의 단위는 1/16이며 배열을 변경하지 않는다. */
    static double[] box(int id, int state) {
        if (CandleRules.isCandle(id)) return CANDLES[CandleRules.count(state) - 1];
        if (Blocks.isChainBlock(id) || Blocks.isCopperChain(id)) {
            int axis = id == Blocks.CHAIN_X ? 1 : id == Blocks.CHAIN_Z ? 2
                    : Math.max(0, Blocks.copperChainAxis(id));
            return CHAIN[axis];
        }
        // [VOID-END] 엔드 막대는 번개막대와 같은 RodBlock 4px 축 기둥, 후렴 식물은 3..13 중심 상자에
        // 위·아래 연결 팔만 더한 단일 상자다(수평 팔 생략, 정적판 DecorationGeometry 와 같은 값).
        if (id == Blocks.END_ROD) {
            int facing = state & 7;
            return ROD[facing == 3 || facing == 5 ? 1 : facing == 2 || facing == 4 ? 2 : 0];
        }
        if (id == Blocks.CHORUS_PLANT) {
            return CHORUS_PLANT[state >> 4 & 3];
        }
        // [DRAGON] 바닐라 DragonEggBlock SHAPE = Block.column(14, 0, 16) = box(1,0,1,15,16,15).
        if (id == Blocks.DRAGON_EGG) return DRAGON_EGG;
        if (id == Blocks.DRAGON_HEAD) return state >= 16 ? DRAGON_HEAD_WALL[state - 16 & 3] : DRAGON_HEAD_FLOOR;
        if (CopperAgeRules.isLightningRod(id)) {
            int facing = state & 7;
            return ROD[facing == 3 || facing == 5 ? 1 : facing == 2 || facing == 4 ? 2 : 0];
        }
        if (id == Blocks.LILY_PAD) return LILY_PAD;
        // 현재 설치/저장 규약에 개수 상태가 없는 불우렁쉥이는 공식 기본 한 개다.
        if (id == Blocks.SEA_PICKLE) return SEA_PICKLE;
        if (P6Rules.isAmethystBud(id)) {
            int facing = state >= 0 && state <= 5 ? state : 0;
            return AMETHYST[id - Blocks.SMALL_AMETHYST_BUD][facing];
        }
        return null;
    }

    private static double[][] axisBoxes(double inset) {
        double far = 16 - inset;
        return new double[][] {
            {inset, 0, inset, far, 16, far},
            {0, inset, inset, 16, far, far},
            {inset, inset, 0, far, far, 16}
        };
    }

    private static double[][] facingBoxes(double height, double inset) {
        double far = 16 - inset;
        return new double[][] {
            {inset, 0, inset, far, height, far},
            {inset, 16 - height, inset, far, 16, far},
            {inset, inset, 16 - height, far, far, 16},
            {0, inset, inset, height, far, far},
            {inset, inset, 0, far, far, height},
            {16 - height, inset, inset, 16, far, far}
        };
    }

    /** Mth.getSeed(x,0,z): X 곱셈은 int, 이후 연산은 오버플로를 포함한 long이다. */
    private static long bambooSeed(int x, int z) {
        long seed = (long) (x * 3129871) ^ (long) z * 116129781L;
        return (seed * seed * 42317861L + seed * 11L) >> 16;
    }

    static double bambooOffsetX(int x, int z) {
        return (((float) (bambooSeed(x, z) & 15) / 15.0f) - 0.5) * 0.5;
    }

    static double bambooOffsetZ(int x, int z) {
        return (((float) ((bambooSeed(x, z) >> 8) & 15) / 15.0f) - 0.5) * 0.5;
    }
}
