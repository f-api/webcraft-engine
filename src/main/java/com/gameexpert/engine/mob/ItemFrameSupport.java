package com.gameexpert.engine.mob;

import com.gameexpert.engine.Fluids;
import com.gameexpert.terrain.Blocks;

/**
 * [EC-MOBS] {@code ItemFrame.survives()} 의 지지 칸 판정. 바닐라는 {@code BlockState.isSolid()}(legacy solid:
 * 충돌 형상이 칸을 충분히 채우는 블록) 또는 수평 방향일 때 {@code DiodeBlock}(중계기·비교기)을 본다. 이 저장소의
 * 충돌 고체 판정({@link Fluids#isSolid})이 그 근사이며, 다이오드는 중계기뿐이다(비교기가 없다). 정적판 짝은
 * {@code StandaloneItemFrameRules.ts} 의 같은 두 함수다.
 */
public final class ItemFrameSupport {
    private ItemFrameSupport() {
    }

    public static boolean solid(int block) {
        // 중계기(1×0.125×1)는 legacy solid 가 아니다: 평균 크기 0.708 < 0.7292, 높이 < 1.
        return block != Blocks.AIR && !diode(block) && Fluids.isSolid(block);
    }

    public static boolean diode(int block) {
        return block == Blocks.REPEATER;
    }
}
