package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;

/**
 * 새 Copper Age 블록을 기존 구리 산화 정본에 덧붙이는 순수 규칙.
 *
 * <p>프로토콜 ID는 append-only라 기존 {@link Blocks} 표의 행 순서를 바꾸지 않고, 새 계열만
 * 여기서 합성한다. 실제 블록 교체 호출부는 {@link CopperChange#state()}를 그대로 적용해야 한다.
 */
public final class CopperAgeRules {
    public static final int COPPER_TORCH_LIGHT = 14;
    public static final int COPPER_LANTERN_LIGHT = 15;

    private static final int[][] OXIDATION_FAMILIES = {
            { Blocks.COPPER_LANTERN, Blocks.EXPOSED_COPPER_LANTERN,
                    Blocks.WEATHERED_COPPER_LANTERN, Blocks.OXIDIZED_COPPER_LANTERN },
            { Blocks.LIGHTNING_ROD, Blocks.EXPOSED_LIGHTNING_ROD,
                    Blocks.WEATHERED_LIGHTNING_ROD, Blocks.OXIDIZED_LIGHTNING_ROD },
            { Blocks.COPPER_GOLEM_STATUE, Blocks.EXPOSED_COPPER_GOLEM_STATUE,
                    Blocks.WEATHERED_COPPER_GOLEM_STATUE, Blocks.OXIDIZED_COPPER_GOLEM_STATUE },
    };
    private static final int[][] WAXED_FAMILIES = {
            { Blocks.WAXED_COPPER_LANTERN, Blocks.WAXED_EXPOSED_COPPER_LANTERN,
                    Blocks.WAXED_WEATHERED_COPPER_LANTERN, Blocks.WAXED_OXIDIZED_COPPER_LANTERN },
            { Blocks.WAXED_LIGHTNING_ROD, Blocks.WAXED_EXPOSED_LIGHTNING_ROD,
                    Blocks.WAXED_WEATHERED_LIGHTNING_ROD, Blocks.WAXED_OXIDIZED_LIGHTNING_ROD },
            { Blocks.WAXED_COPPER_GOLEM_STATUE, Blocks.WAXED_EXPOSED_COPPER_GOLEM_STATUE,
                    Blocks.WAXED_WEATHERED_COPPER_GOLEM_STATUE, Blocks.WAXED_OXIDIZED_COPPER_GOLEM_STATUE },
    };

    private CopperAgeRules() {
    }

    private static int packedSlot(int[][] families, int blockId) {
        for (int family = 0; family < families.length; family++) {
            for (int age = 0; age < Blocks.COPPER_OXIDATION_STAGES; age++) {
                if (families[family][age] == blockId) {
                    return family * Blocks.COPPER_OXIDATION_STAGES + age;
                }
            }
        }
        return -1;
    }

    public static boolean isOxidizable(int blockId) {
        return Blocks.isOxidizableCopper(blockId) || packedSlot(OXIDATION_FAMILIES, blockId) >= 0;
    }

    public static boolean isWaxed(int blockId) {
        return Blocks.isWaxedCopper(blockId) || packedSlot(WAXED_FAMILIES, blockId) >= 0;
    }

    public static int oxidationAge(int blockId) {
        int existing = Blocks.copperOxidationAge(blockId);
        if (existing >= 0) return existing;
        int packed = packedSlot(OXIDATION_FAMILIES, blockId);
        if (packed < 0) packed = packedSlot(WAXED_FAMILIES, blockId);
        return packed < 0 ? -1 : packed % Blocks.COPPER_OXIDATION_STAGES;
    }

    public static int nextOxidationStage(int blockId) {
        int existing = Blocks.nextOxidationStage(blockId);
        if (existing >= 0) return existing;
        int packed = packedSlot(OXIDATION_FAMILIES, blockId);
        if (packed < 0 || packed % Blocks.COPPER_OXIDATION_STAGES == 3) return -1;
        return OXIDATION_FAMILIES[packed / Blocks.COPPER_OXIDATION_STAGES]
                [packed % Blocks.COPPER_OXIDATION_STAGES + 1];
    }

    public static int previousOxidationStage(int blockId) {
        int existing = Blocks.previousOxidationStage(blockId);
        if (existing >= 0) return existing;
        int packed = packedSlot(OXIDATION_FAMILIES, blockId);
        if (packed < 0 || packed % Blocks.COPPER_OXIDATION_STAGES == 0) return -1;
        return OXIDATION_FAMILIES[packed / Blocks.COPPER_OXIDATION_STAGES]
                [packed % Blocks.COPPER_OXIDATION_STAGES - 1];
    }

    /** 낙뢰 직격이 쓰는 완전 환원 결과. 밀랍·비구리 블록은 -1이다. */
    public static int firstOxidationStage(int blockId) {
        int existing = Blocks.firstOxidationStage(blockId);
        if (existing >= 0) return existing;
        int packed = packedSlot(OXIDATION_FAMILIES, blockId);
        return packed < 0 ? -1 : OXIDATION_FAMILIES[packed / Blocks.COPPER_OXIDATION_STAGES][0];
    }

    public static int waxedFor(int blockId) {
        int existing = Blocks.waxedCopperFor(blockId);
        if (existing >= 0) return existing;
        int packed = packedSlot(OXIDATION_FAMILIES, blockId);
        return packed < 0 ? -1 : WAXED_FAMILIES[packed / Blocks.COPPER_OXIDATION_STAGES]
                [packed % Blocks.COPPER_OXIDATION_STAGES];
    }

    public static int unwaxedFor(int blockId) {
        int existing = Blocks.unwaxedCopperFor(blockId);
        if (existing >= 0) return existing;
        int packed = packedSlot(WAXED_FAMILIES, blockId);
        return packed < 0 ? -1 : OXIDATION_FAMILIES[packed / Blocks.COPPER_OXIDATION_STAGES]
                [packed % Blocks.COPPER_OXIDATION_STAGES];
    }

    public static int scrapeResult(int blockId) {
        int unwaxed = unwaxedFor(blockId);
        return unwaxed >= 0 ? unwaxed : previousOxidationStage(blockId);
    }

    /** 변환 뒤에도 방향·자세·점등 및 향후 waterlogged state를 그대로 보존한다. */
    public static CopperChange waxPlan(int blockId, int state) {
        return change(waxedFor(blockId), state);
    }

    /** 변환 뒤에도 방향·자세·점등 및 향후 waterlogged state를 그대로 보존한다. */
    public static CopperChange scrapePlan(int blockId, int state) {
        return change(scrapeResult(blockId), state);
    }

    private static CopperChange change(int result, int state) {
        return result < 0 ? null : new CopperChange(result, state & 0xff);
    }

    /** 현재 엔진이 waterlogged state를 추가할 때 같은 전이 계획으로 보존해야 하는 블록군. */
    public static boolean canWaterlog(int blockId) {
        return blockId == Blocks.COPPER_GRATE || blockId == Blocks.EXPOSED_COPPER_GRATE
                || blockId == Blocks.WEATHERED_COPPER_GRATE || blockId == Blocks.OXIDIZED_COPPER_GRATE
                || blockId == Blocks.WAXED_COPPER_GRATE || blockId == Blocks.WAXED_EXPOSED_COPPER_GRATE
                || blockId == Blocks.WAXED_WEATHERED_COPPER_GRATE || blockId == Blocks.WAXED_OXIDIZED_COPPER_GRATE
                || Blocks.isCopperBars(blockId) || Blocks.isCopperChain(blockId)
                // [VANILLA-FLAME] 철 랜턴도 같은 LanternBlock(SimpleWaterloggedBlock)이다.
                || BuildingBlockRules.isLantern(blockId) || isLightningRod(blockId) || isStatue(blockId)
                // [TRIAL-GAP] 26.3 HeavyCoreBlock implements SimpleWaterloggedBlock(WATERLOGGED 하나).
                || blockId == Blocks.HEAVY_CORE;
    }

    public static int lightEmission(int blockId) {
        if (blockId == Blocks.COPPER_TORCH || blockId >= Blocks.COPPER_WALL_TORCH_N
                && blockId <= Blocks.COPPER_WALL_TORCH_W) return COPPER_TORCH_LIGHT;
        return isLantern(blockId) ? COPPER_LANTERN_LIGHT : 0;
    }

    private static boolean isLantern(int blockId) {
        return blockId == Blocks.COPPER_LANTERN
                || blockId >= Blocks.EXPOSED_COPPER_LANTERN && blockId <= Blocks.WAXED_OXIDIZED_COPPER_LANTERN;
    }

    public static boolean isLightningRod(int blockId) {
        return blockId == Blocks.LIGHTNING_ROD
                || blockId >= Blocks.EXPOSED_LIGHTNING_ROD && blockId <= Blocks.WAXED_OXIDIZED_LIGHTNING_ROD;
    }

    private static boolean isStatue(int blockId) {
        return blockId >= Blocks.COPPER_GOLEM_STATUE && blockId <= Blocks.WAXED_OXIDIZED_COPPER_GOLEM_STATUE;
    }

    public static final class CopperChange {
        private final int blockId;
        private final int state;

        private CopperChange(int blockId, int state) {
            this.blockId = blockId;
            this.state = state;
        }

        public int blockId() {
            return blockId;
        }

        public int state() {
            return state;
        }
    }
}
