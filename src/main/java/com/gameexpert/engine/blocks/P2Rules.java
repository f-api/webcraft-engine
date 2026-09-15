package com.gameexpert.engine.blocks;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;

/** p2-plant 블록의 작고 순수한 서버 규칙 모음. */
public final class P2Rules {
    public static final int BAMBOO_MAX_HEIGHT = 16;
    public static final int BAMBOO_BELOW = 1;
    public static final int BAMBOO_ABOVE = 2;

    public static final int VINE_NORTH = 1;
    public static final int VINE_EAST = 2;
    public static final int VINE_SOUTH = 4;
    public static final int VINE_WEST = 8;
    public static final int VINE_HANGING = 16;

    private P2Rules() {}

    public static double collisionHeight(int blockId, int state, double current) {
        // 바닐라 CarpetBlock SHAPE = box(0,0,0,16,1,16).
        return blockId == Blocks.MOSS_CARPET ? 1.0 / 16.0 : current;
    }

    public static boolean isClimbable(int blockId) {
        return blockId == Blocks.VINE;
    }

    public static boolean hasRequiredSupport(int blockId, boolean supportedBelow, boolean attachedToWall,
                                             boolean upperSupport) {
        if (blockId == Blocks.VINE) return attachedToWall || upperSupport;
        if (blockId == Blocks.MOSS_CARPET || blockId == Blocks.FERN || blockId == Blocks.BUSH) {
            return supportedBelow;
        }
        if (blockId == Blocks.BAMBOO) return supportedBelow;
        return true;
    }

    public static int bambooHeightLimit(int randomValue) {
        return 12 + Math.floorMod(randomValue, BAMBOO_MAX_HEIGHT - 11);
    }

    public static boolean canBambooGrow(int columnHeight, int heightLimit, boolean airAbove, int lightAbove) {
        return columnHeight > 0 && heightLimit >= 12 && heightLimit <= BAMBOO_MAX_HEIGHT
                && columnHeight < heightLimit && airAbove && lightAbove >= 9;
    }

    /** Java Edition 뼛가루는 대나무를 1~2블록 자라게 한다. */
    public static int bambooBoneMealGrowth(int randomValue) {
        return 1 + Math.floorMod(randomValue, 2);
    }

    public static int bambooHarvestCount(int brokenSegmentCount) {
        return Math.max(0, brokenSegmentCount);
    }

    public static boolean canVineGrowDown(boolean replaceableBelow) {
        return replaceableBelow;
    }

    /** 드랍 0은 없음. 고사리는 일반 파괴 시 1/8 확률로 밀 씨앗을 준다. */
    /** [SURV-X] 인챈트 마스크를 함께 받는 채굴 드랍(현재 팩 규칙은 마스크에 의존하지 않는다). */
    public static int minedDrop(int blockId, boolean shears, long enchantments, double randomRoll) {
        return minedDrop(blockId, shears, randomRoll);
    }

    public static int minedDrop(int blockId, boolean shears, double randomRoll) {
        if (blockId == Blocks.VINE) return shears ? Blocks.VINE : 0;
        if (blockId == Blocks.FERN) {
            if (shears) return Blocks.FERN;
            return randomRoll >= 0.0 && randomRoll < 0.125 ? PlayerInventory.WHEAT_SEEDS : 0;
        }
        if (blockId == Blocks.BUSH) return shears ? Blocks.BUSH : 0;
        if (blockId == Blocks.BAMBOO || blockId == Blocks.MOSS_CARPET
                || blockId == Blocks.MANGROVE_ROOTS) return blockId;
        return blockId;
    }
}
