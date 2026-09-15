package com.gameexpert.engine.blocks;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;

/** 자연·지질 확장 블록의 상태, 지지, 드랍 규칙. */
public final class P6Rules {
    public static final int DRIPSTONE_UP = 1;
    public static final int DRIPSTONE_THICKNESS_MASK = 14;
    public static final int DRIPSTONE_TIP = 0;
    public static final int DRIPSTONE_TIP_MERGE = 2;
    public static final int DRIPSTONE_FRUSTUM = 4;
    public static final int DRIPSTONE_MIDDLE = 6;
    public static final int DRIPSTONE_BASE = 8;
    public static final int CAVE_VINES_BERRIES = 1;
    public static final int CAVE_VINES_AGE_SHIFT = 1;
    public static final int CAVE_VINES_AGE_MASK = 0x3e;
    public static final int CAVE_VINES_MAX_AGE = 25;
    public static final int PROPAGULE_HANGING = 1;
    public static final int PROPAGULE_AGE_MASK = 14;
    public static final int LOG_AXIS_MASK = 3;
    public static final int AMETHYST_FACING_UP = 0;
    public static final int AMETHYST_FACING_DOWN = 1;
    public static final int AMETHYST_FACING_NORTH = 2;
    public static final int AMETHYST_FACING_EAST = 3;
    public static final int AMETHYST_FACING_SOUTH = 4;
    public static final int AMETHYST_FACING_WEST = 5;

    private P6Rules() {
    }

    public static boolean requiresSupport(int blockId) {
        return isSpeleothem(blockId)
                || blockId == Blocks.CAVE_VINES
                || blockId == Blocks.CAVE_VINES_PLANT
                || blockId == Blocks.MANGROVE_PROPAGULE
                || isAmethystBud(blockId);
    }

    public static boolean hasRequiredSupport(int blockId, int state,
            int below, int above, boolean belowSolid, boolean aboveSolid) {
        if (isSpeleothem(blockId)) {
            return (state & DRIPSTONE_UP) != 0
                    ? belowSolid || below == blockId
                    : aboveSolid || above == blockId;
        }
        if (blockId == Blocks.CAVE_VINES || blockId == Blocks.CAVE_VINES_PLANT) {
            return aboveSolid || above == Blocks.CAVE_VINES || above == Blocks.CAVE_VINES_PLANT;
        }
        if (blockId == Blocks.MANGROVE_PROPAGULE) {
            return (state & PROPAGULE_HANGING) != 0
                    ? above == Blocks.MANGROVE_LEAVES
                    : isSaplingSoil(below);
        }
        return true;
    }

    /** Java 26.3 SpeleothemBlock family; both blocks share the same ten direction/thickness states. */
    public static boolean isSpeleothem(int blockId) {
        return blockId == Blocks.POINTED_DRIPSTONE || blockId == Blocks.SULFUR_SPIKE;
    }

    public static boolean isSapling(int blockId) {
        return blockId >= Blocks.SPRUCE_SAPLING && blockId <= Blocks.CHERRY_SAPLING;
    }

    public static boolean isSaplingSoil(int blockId) {
        return blockId == Blocks.DIRT || blockId == Blocks.GRASS
                || blockId == Blocks.COARSE_DIRT || blockId == Blocks.PODZOL
                || blockId == Blocks.MYCELIUM || blockId == Blocks.ROOTED_DIRT
                || blockId == Blocks.MOSS_BLOCK || blockId == Blocks.MUD
                // [PALE-GARDEN] 창백한 이끼 블록도 바닐라 #minecraft:dirt 태그다.
                || blockId == Blocks.PALE_MOSS_BLOCK
                || blockId == Blocks.CLAY || blockId == Blocks.MUDDY_MANGROVE_ROOTS;
    }

    public static int caveVinesAge(int state) {
        return Math.min(CAVE_VINES_MAX_AGE, (state & CAVE_VINES_AGE_MASK) >> CAVE_VINES_AGE_SHIFT);
    }

    public static int caveVinesState(int age, boolean berries) {
        return Math.min(CAVE_VINES_MAX_AGE, Math.max(0, age)) << CAVE_VINES_AGE_SHIFT
                | (berries ? CAVE_VINES_BERRIES : 0);
    }

    /** 단일 산출 드랍. 독립 다중 풀은 BlockLoot가 별도로 방출한다. */
    public static int minedDrop(int blockId, int selectedItem, double randomRoll, int currentDrop) {
        return switch (blockId) {
            case Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT -> Blocks.AIR;
            case Blocks.MANGROVE_LEAVES -> Blocks.AIR;
            case Blocks.BROWN_MUSHROOM_BLOCK -> Blocks.MUSHROOM_BROWN;
            case Blocks.RED_MUSHROOM_BLOCK -> Blocks.MUSHROOM_RED;
            case Blocks.MUSHROOM_STEM, Blocks.BUDDING_AMETHYST,
                    Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD,
                    Blocks.LARGE_AMETHYST_BUD -> Blocks.AIR;
            case Blocks.AMETHYST_CLUSTER -> PlayerInventory.AMETHYST_SHARD;
            // [CREAKING] 실크 터치가 없으면 크리킹 하트는 자기 자신이 아니라 수지 덩어리를
            // 떨군다 [B] «Creaking Heart». 실크 터치 경로는 이 표보다 앞서 갈라지므로
            // (InventoryRules.silkTouchDropFor) 여기 오는 것은 무보정 채굴뿐이다.
            // 개수(1~3, 행운 레벨당 최대 +1)는 CreakingHeartRules 가 소유한다.
            case Blocks.CREAKING_HEART, Blocks.CREAKING_HEART_ACTIVE -> Blocks.RESIN_CLUMP;
            default -> currentDrop;
        };
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
        double roll = Math.max(0.0, Math.min(Math.nextDown(1.0), randomRoll));
        if (blockId == Blocks.COPPER_ORE || blockId == Blocks.DEEPSLATE_COPPER_ORE) {
            return 2 + (int) Math.floor(roll * 4.0);
        }
        if (blockId == Blocks.DEEPSLATE_LAPIS_ORE) return 4 + (int) Math.floor(roll * 6.0);
        if (blockId == Blocks.DEEPSLATE_REDSTONE_ORE) return 4 + (int) Math.floor(roll * 2.0);
        if (blockId == Blocks.BROWN_MUSHROOM_BLOCK
                || blockId == Blocks.RED_MUSHROOM_BLOCK) {
            return Math.max(0, (int) Math.floor(roll * 9.0) - 6);
        }
        if (blockId == Blocks.AMETHYST_CLUSTER) return isPickaxe(selectedItem) ? 4 : 2;
        return 1;
    }

    public static boolean isAmethystBud(int blockId) {
        return blockId >= Blocks.SMALL_AMETHYST_BUD && blockId <= Blocks.AMETHYST_CLUSTER;
    }

    private static boolean isPickaxe(int item) {
        return item == PlayerInventory.PICKAXE
                || item == PlayerInventory.STONE_PICKAXE
                || item == PlayerInventory.IRON_PICKAXE
                || item == PlayerInventory.GOLD_PICKAXE
                || item == PlayerInventory.DIAMOND_PICKAXE;
    }
}
