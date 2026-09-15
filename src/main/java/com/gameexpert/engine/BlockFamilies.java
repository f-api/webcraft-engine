package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;

/** Shared semantic block families used by runtime rules. */
public final class BlockFamilies {

    private BlockFamilies() {
    }

    public static boolean isLeaves(int block) {
        return block == Blocks.LEAVES || block == Blocks.BIRCH_LEAVES
                || block == Blocks.SPRUCE_LEAVES || block == Blocks.JUNGLE_LEAVES
                || block == Blocks.ACACIA_LEAVES || block == Blocks.DARK_OAK_LEAVES
                || block == Blocks.CHERRY_LEAVES || block == Blocks.MANGROVE_LEAVES
                // [TREE-NESTED-26.3] Azalea tree foliage is a LeavesBlock pair, not the
                // bush-form AZALEA/FLOWERING_AZALEA pair used as saplings.
                || block == Blocks.AZALEA_LEAVES || block == Blocks.FLOWERING_AZALEA_LEAVES
                // [POPLAR] 26.3 포플러 잎 세 변종(1536~1538). 경도·가연·묘목 드랍이
                // 바닐라 잎 계약과 같은 표를 쓴다.
                || Blocks.isPoplarLeaves(block)
                // [PALE-GARDEN] 창백한 참나무 잎(1503). 다른 수종 잎과 같은 표를 쓴다 —
                // 다만 바이옴 틴트만은 받지 않는다(바닐라도 고정색이다).
                || block == Blocks.PALE_OAK_LEAVES;
    }

    /** Registered one-block flower identities used by the pinned FEATURES flower patches. */
    public static boolean isSmallFlower(int block) {
        return block == Blocks.FLOWER_RED || block == Blocks.FLOWER_YELLOW
                || block == Blocks.BLUE_ORCHID || block == Blocks.CLOSED_EYEBLOSSOM || block == Blocks.OPEN_EYEBLOSSOM
                || block == Blocks.LILY_OF_THE_VALLEY || block == Blocks.TORCHFLOWER
                || block == Blocks.ALLIUM || block == Blocks.AZURE_BLUET
                || block == Blocks.RED_TULIP || block == Blocks.ORANGE_TULIP
                || block == Blocks.WHITE_TULIP || block == Blocks.PINK_TULIP
                || block == Blocks.OXEYE_DAISY || block == Blocks.CORNFLOWER;
    }

    public static boolean isWoodLog(int block) {
        return block == Blocks.LOG || block == Blocks.LOG_X || block == Blocks.LOG_Z
                || block == Blocks.BIRCH_LOG || block == Blocks.BIRCH_LOG_X
                || block == Blocks.BIRCH_LOG_Z || block == Blocks.SPRUCE_LOG
                || block == Blocks.JUNGLE_LOG || block == Blocks.ACACIA_LOG
                || block == Blocks.DARK_OAK_LOG || block == Blocks.CHERRY_LOG
                || block == Blocks.MANGROVE_LOG
                // [POPLAR] 포플러 원목·벗긴 포플러 원목(1530~1535)도 바닐라
                // #minecraft:logs 다. 여덟 수종 표 밖이라 명시 갈래를 쓴다.
                || Blocks.isPoplarLog(block) || Blocks.isStrippedPoplarLog(block)
                || block == Blocks.POPLAR_WOOD || block == Blocks.STRIPPED_POPLAR_WOOD
                // [PALE-GARDEN] 창백한 참나무 원목·벗긴 원목(1490~1495)도 바닐라
                // #minecraft:logs 다. 같은 이유로 여덟 수종 표 밖이라 명시 갈래를 쓴다.
                || Blocks.isPaleOakLog(block) || Blocks.isStrippedPaleOakLog(block)
                // [STRIPPED-LOG] 바닐라 #minecraft:logs 태그는 벗긴 원목도 포함한다. 경도
                // 2.0 · 폭발 저항 2.0 · 자기 자신 드랍 · 잎 유지 · 인화성이 통나무와 한 표다.
                || Blocks.isStrippedLog(block);
    }

    /** Species logs whose axis is encoded in block state rather than a legacy block ID. */
    public static boolean isStateOrientedWoodLog(int block) {
        return block == Blocks.SPRUCE_LOG || block == Blocks.JUNGLE_LOG
                || block == Blocks.ACACIA_LOG || block == Blocks.DARK_OAK_LOG
                || block == Blocks.CHERRY_LOG || block == Blocks.MANGROVE_LOG
                || block == Blocks.POPLAR_WOOD || block == Blocks.STRIPPED_POPLAR_WOOD;
    }

    /** Inventory item produced by a log block; legacy axis variants collapse to their base log. */
    public static int woodLogItem(int block) {
        if (block == Blocks.LOG || block == Blocks.LOG_X || block == Blocks.LOG_Z) {
            return Blocks.LOG;
        }
        if (block == Blocks.BIRCH_LOG || block == Blocks.BIRCH_LOG_X
                || block == Blocks.BIRCH_LOG_Z) {
            return Blocks.BIRCH_LOG;
        }
        // [POPLAR] 포플러는 여덟 수종 표 밖이라 자기 산술로 접는다(통나무·벗긴 원목 둘 다).
        int poplar = Blocks.poplarLogItem(block);
        if (poplar != Blocks.AIR) return poplar;
        // [PALE-GARDEN] 창백한 참나무도 여덟 수종 표 밖이라 자기 산술로 접는다.
        int paleOak = Blocks.paleOakLogItem(block);
        if (paleOak != Blocks.AIR) return paleOak;
        // [STRIPPED-LOG] 벗긴 원목도 축 변형이 별도 ID 라 y축 대표 ID 로 접는다.
        if (Blocks.isStrippedLog(block)) return Blocks.strippedLogItem(block);
        return isWoodLog(block) ? block : Blocks.AIR;
    }

    /**
     * [QUARTZ] 석영 기둥의 인벤토리 아이템 ID. 통나무와 같이 축 변형이 별도 ID 라
     * y축 대표 ID 로 접는다. 석영 기둥이 아니면 {@link Blocks#AIR} 다.
     */
    public static int quartzPillarItem(int block) {
        return Blocks.isQuartzPillar(block) ? Blocks.QUARTZ_PILLAR : Blocks.AIR;
    }

    /**
     * [PROP-MATERIAL] 사슬 축 변형 → y축 대표 ID. 통나무·석영 기둥과 같은 이유로 축은 ID 로
     * 표현하지만 아이템은 하나뿐이라, 드랍·인벤토리는 항상 {@link Blocks#CHAIN} 으로 접힌다.
     */
    public static int chainItem(int block) {
        return Blocks.isChainBlock(block) ? Blocks.CHAIN : Blocks.AIR;
    }

    /**
     * [COPPER-CHAIN] 구리 사슬 축 변형 → 같은 산화 단계·같은 밀랍 여부의 <b>y축 대표 ID</b>.
     * 철 사슬과 달리 아이템이 하나가 아니라 <b>여덟</b>(산화 4 × 밀랍 2)이라, 축만 접고
     * 산화·밀랍은 보존해야 한다 — 전부 접으면 산화한 사슬을 캘 때 새 사슬이 나온다.
     * 구리 사슬이 아니면 {@link Blocks#AIR} 다.
     */
    public static int copperChainItem(int block) {
        int folded = Blocks.copperChainWithAxis(block, 0);
        return folded < 0 ? Blocks.AIR : folded;
    }
}
