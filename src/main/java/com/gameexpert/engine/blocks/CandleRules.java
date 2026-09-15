package com.gameexpert.engine.blocks;

import com.gameexpert.terrain.Blocks;

/** 양초 묶음 수량. 기존 기본 state 0은 한 개이며 낮은 두 비트만 수량에 쓴다. */
public final class CandleRules {
    private CandleRules() { }

    public static boolean isCandle(int blockId) {
        return blockId == Blocks.CANDLE || blockId >= Blocks.RED_CANDLE && blockId <= Blocks.BROWN_CANDLE;
    }

    public static int count(int state) {
        return (state & 3) + 1;
    }

    public static int withCount(int state, int count) {
        if (count < 1 || count > 4) throw new IllegalArgumentException("양초 묶음은 1~4개입니다");
        return (state & 0xfc) | (count - 1);
    }

    /** 같은 아이템의 일반 사용만 묶음에 추가한다. 보조 사용은 인접 칸 설치를 유지한다. */
    public static boolean canAdd(int currentId, int currentState, int placedId, boolean secondaryUse) {
        return !secondaryUse && isCandle(currentId) && currentId == placedId && count(currentState) < 4;
    }
}
