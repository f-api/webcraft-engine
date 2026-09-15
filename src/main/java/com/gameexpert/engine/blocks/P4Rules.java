package com.gameexpert.engine.blocks;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;

/** p4-utility 블록의 서버 규칙 확장점. */
public final class P4Rules {
    private P4Rules() {}

    /**
     * 건초더미 등 착지 블록이 서버 낙하 피해에 적용할 배율.
     *
     * <p>바닐라 {@code HayBlock} 은 {@code fallOn(...)} 에서
     * {@code entity.causeFallDamage(fallDistance, 0.2F, ...)} 를 부른다 — 배율 0.2F [B].
     * <p>쿠션은 충돌 없는 엔티티이므로 이 착지 블록 판정에 참여하지 않는다.
     */
    public static double fallDamageMultiplier(int blockId) {
        return blockId == Blocks.HAY_BLOCK ? 0.2 : 1.0;
    }

    /** 가위 상호작 후 현재 칸의 블록. 변환이 없으면 입력 ID를 유지한다. */
    public static int shearsResult(int blockId, int state) {
        return blockId == Blocks.PUMPKIN ? Blocks.CARVED_PUMPKIN : blockId;
    }

    /** 가위 상호작이 성립했을 때 바닥에 스폰할 부산물 ID. */
    public static int shearsDrop(int blockId, int state) {
        return blockId == Blocks.PUMPKIN ? Blocks.PUMPKIN_SEEDS : 0;
    }

    public static int shearsDropCount(int blockId, int state) {
        return blockId == Blocks.PUMPKIN ? 4 : 0;
    }

    /** [SURV-X] 인챈트 마스크를 함께 받는 채굴 드랍(현재 팩 규칙은 마스크에 의존하지 않는다). */
    public static int minedDrop(int blockId, int selectedItem, long enchantments,
            double randomRoll, int currentDrop) {
        return minedDrop(blockId, selectedItem, randomRoll, currentDrop);
    }

    /** 거미줄의 검/가위 채집 등 도구별 드랍 보정. */
    public static int minedDrop(int blockId, int selectedItem, double randomRoll, int currentDrop) {
        if (blockId == Blocks.COBWEB) {
            if (selectedItem == Blocks.SHEARS) return Blocks.COBWEB;
            if (isSword(selectedItem)) return PlayerInventory.STRING;
            return 0;
        }
        return currentDrop;
    }

    private static boolean isSword(int itemId) {
        return itemId == PlayerInventory.SWORD_ITEM
                || itemId == PlayerInventory.STONE_SWORD
                || itemId == PlayerInventory.IRON_SWORD
                || itemId == PlayerInventory.GOLD_SWORD
                || itemId == PlayerInventory.DIAMOND_SWORD;
    }
}
