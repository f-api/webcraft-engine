package com.gameexpert.engine.blocks;

import com.gameexpert.terrain.Blocks;

/** p1-surface 블록의 서버 규칙 확장점. */
public final class P1Rules {
    private P1Rules() {}

    /** 삽으로 상호작한 블록의 결과. 변환이 없으면 입력 ID를 그대로 돌려준다. */
    public static int shovelResult(int blockId) {
        if (blockId == Blocks.DIRT || blockId == Blocks.GRASS) return Blocks.DIRT_PATH;
        return blockId;
    }

    /**
     * 물병을 사용한 블록의 결과. 변환이 없으면 입력 ID를 그대로 돌려준다. [CONTAINER-MENUS] 대상은
     * 바닐라 {@code BlockTags.CONVERTIBLE_TO_MUD}(흙 · 거친 흙 · 뿌리 내린 흙) 전부다.
     */
    public static int waterBottleResult(int blockId) {
        if (blockId == Blocks.DIRT || blockId == Blocks.COARSE_DIRT
                || blockId == Blocks.ROOTED_DIRT) return Blocks.MUD;
        return blockId;
    }

    /** 뿌리 박힌 흙 등에 뼈티가루를 사용했을 때 아래 칸에 놓을 블록. */
    public static int boneMealPlacement(int blockId, int blockBelow) {
        if (blockId == Blocks.ROOTED_DIRT && blockBelow == Blocks.AIR) return Blocks.HANGING_ROOTS;
        return 0;
    }

    /** 기존 채굴 드랍 ID를 팩 규칙으로 보정한다. */
    public static int minedDrop(int blockId, int selectedItem, double randomRoll, int currentDrop) {
        if (blockId == Blocks.CLAY) return Blocks.CLAY_BALL;
        if (blockId == Blocks.DEEPSLATE) {
            return currentDrop == 0 ? 0 : Blocks.COBBLED_DEEPSLATE;
        }
        return currentDrop;
    }

    /** [SURV-X] 인챈트 마스크를 함께 받는 채굴 드랍(현재 팩 규칙은 마스크에 의존하지 않는다). */
    public static int minedDrop(int blockId, int selectedItem, long enchantments,
            double randomRoll, int currentDrop) {
        return minedDrop(blockId, selectedItem, randomRoll, currentDrop);
    }

    /** [SURV-X] 인챈트 마스크를 함께 받는 수량(행운 배율은 OreRules 가 따로 곱한다). */
    public static int minedDropCount(int blockId, int selectedItem, long enchantments,
            double randomRoll) {
        return minedDropCount(blockId, selectedItem, randomRoll);
    }

    /** 확정된 드랍의 스택 수량. 일반 블록은 1개를 유지한다. */
    public static int minedDropCount(int blockId, int selectedItem, double randomRoll) {
        if (blockId == Blocks.CLAY) return 4;
        // [ENDER-SHULKER] 엔더 상자를 실크 터치 없이 캐면 흑요석 **8** 이다
        // ([A] blocks/ender_chest). 실크 터치 갈래는 이 계산에 오지 않는다 —
        // ItemEntitySystem 이 실크 터치를 먼저 처리하고 한 개만 내보내기 때문이다.
        if (blockId == Blocks.ENDER_CHEST) return 8;
        return 1;
    }

    /** 잠재적으로 잠식되는 셀의 잔디 확산을 팩이 거부할 수 있다. */
    public static boolean canGrassSpreadTo(int blockId, boolean current) {
        if (blockId == Blocks.PODZOL || blockId == Blocks.MYCELIUM || blockId == Blocks.COARSE_DIRT) return false;
        return current;
    }

    /** 밟았을 때 트래플링되는 블록의 결과. */
    public static int blockAfterStep(int blockId) {
        if (blockId == Blocks.DIRT_PATH) return Blocks.DIRT;
        return blockId;
    }

    /** 흙길·진흙 등의 서버 충돌 높이 보정. */
    public static double collisionHeight(int blockId, int state, double current) {
        if (blockId == Blocks.POWDER_SNOW) return 0.0;
        if (blockId == Blocks.MUD) return 14.0 / 16.0;
        // [FROST-SOUL] SoulSandBlock.SHAPE = Block.column(16, 0, 14): 진흙과 같은 14/16 충돌 윗면.
        if (blockId == Blocks.SOUL_SAND) return 14.0 / 16.0;
        if (blockId == Blocks.DIRT_PATH) return 15.0 / 16.0;
        return current;
    }
}
