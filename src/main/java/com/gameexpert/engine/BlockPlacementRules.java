package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;

/** 플레이어 인벤토리에서 월드에 직접 놓을 수 있는 등록 블록의 단일 판정점. */
public final class BlockPlacementRules {

    private BlockPlacementRules() {
    }

    /** Blocks의 등록·런타임 상태 분류를 설치 정본으로 사용한다. */
    public static boolean isPlaceable(int blockId) {
        return Blocks.isPlaceableBlock(blockId);
    }
}
