package com.gameexpert.engine.blocks;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;

/** p5-ice 블록의 서버 규칙 확장점. */
public final class P5Rules {
    private P5Rules() {}

    /** 기존 채굴 드랍 ID를 네더 금광석 규칙으로 보정한다. */
    public static int minedDrop(int blockId, int selectedItem, double randomRoll, int currentDrop) {
        if (blockId == Blocks.NETHER_GOLD_ORE) {
            return isPickaxe(selectedItem) ? Blocks.GOLD_NUGGET : 0;
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

    public static int minedDropCount(int blockId, int selectedItem, double randomRoll) {
        if (blockId == Blocks.NETHER_GOLD_ORE && isPickaxe(selectedItem)) {
            return Math.max(2, Math.min(6, 2 + (int) Math.floor(randomRoll * 5.0)));
        }
        return 1;
    }

    private static boolean isPickaxe(int item) {
        return item == PlayerInventory.PICKAXE
                || item == PlayerInventory.STONE_PICKAXE
                || item == PlayerInventory.IRON_PICKAXE
                || item == PlayerInventory.GOLD_PICKAXE
                || item == PlayerInventory.DIAMOND_PICKAXE;
    }
}
