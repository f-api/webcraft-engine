package com.gameexpert.engine.mob;

import com.gameexpert.engine.BuildingBlockRules;
import com.gameexpert.terrain.Blocks;

/**
 * [EC-MOBS] {@code Block.isFaceSturdy(level, pos, face, SupportType.FULL)} 의 이 저장소 블록 형상 판정.
 * 셜커 부착({@code loadedAndEntityCanStandOnFace})과 탄환 조향({@code loadedAndEntityCanStandOn}, 윗면)이
 * 쓴다. 정적판 짝은 {@code StandaloneShulkerSupport.ts} 다.
 *
 * <ul>
 *   <li>풀 큐브({@link BuildingBlockRules#hasFullSquareFace})와 유리: 여섯 면 모두.</li>
 *   <li>반 블록: 아래 반은 아랫면, 위 반은 윗면, 겹반은 여섯 면.</li>
 *   <li>계단: 아래 계단은 아랫면, 위 계단은 윗면, 그리고 등판(facing 쪽 면)이 온전한 면이다.</li>
 * </ul>
 * 방향은 바닐라 {@code Direction.get3DDataValue}(DOWN 0 · UP 1 · NORTH 2 · SOUTH 3 · WEST 4 · EAST 5)다.
 */
public final class ShulkerSupport {
    private ShulkerSupport() {
    }

    /** 계단 facing 상태(북 0 · 동 1 · 남 2 · 서 3) → Direction 3D 값. */
    private static final int[] STAIR_FACING_3D = {ItemFrameRules.NORTH, ItemFrameRules.EAST,
        ItemFrameRules.SOUTH, ItemFrameRules.WEST};

    public static boolean sturdyFace(int block, int state, int face) {
        if (block == Blocks.AIR) return false;
        if (Blocks.isGlassBlock(block) || BuildingBlockRules.hasFullSquareFace(block)) return true;
        if (BuildingBlockRules.isSlab(block)) {
            int type = state & 0x03;
            if (type == BuildingBlockRules.SLAB_DOUBLE) return true;
            return type == BuildingBlockRules.SLAB_TOP ? face == ItemFrameRules.UP : face == ItemFrameRules.DOWN;
        }
        if (BuildingBlockRules.isStairs(block)) {
            boolean top = (state & BuildingBlockRules.STAIR_TOP) != 0;
            if (face == (top ? ItemFrameRules.UP : ItemFrameRules.DOWN)) return true;
            return face == STAIR_FACING_3D[state & BuildingBlockRules.FACING_MASK];
        }
        return false;
    }
}
