package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;

/**
 * 제련로 변형(화로 · 용광로 · 훈연기)의 <b>단일 표</b>입니다.
 *
 * <p>세 변형이 다른 것은 정확히 세 가지 — 제련 시간, 받아 주는 입력, 경험치 배율 — 이고 그 셋을
 * 이 열거형이 통째로 소유합니다. 새 변형(바닐라에는 더 없다)이 생겨도 상수 한 줄이 늘 뿐,
 * {@link FurnaceInventory}·{@link FurnaceRules}·WS 프로토콜·정적판 미러 어디에도 변형별 분기가
 * 생기지 않습니다.
 *
 * <h2>바닐라 1.21.4 근거</h2>
 * <ul>
 *   <li><b>제련 시간</b>: {@code FurnaceBlockEntity} 의 레시피 cookingTime 은 200틱(10초),
 *       {@code BlastFurnaceBlockEntity}/{@code SmokerBlockEntity} 는
 *       {@code AbstractCookingRecipe} 의 blasting/smoking 기본값 100틱(5초)이다. 즉 정확히
 *       절반이며 이 저장소는 10 TPS 라 {@link FurnaceRules#TICKS_PER_SMELT}(100틱=10초)를
 *       {@link #cookDivisor} 로 나눈다.</li>
 *   <li><b>입력</b>: 바닐라는 레시피 <i>종류</i>로 가른다. blast_furnace 는 {@code blasting}
 *       레시피(광석·원석·금속)만, smoker 는 {@code smoking} 레시피(음식)만 받고, furnace 는
 *       {@code smelting} 전부를 받는다. 이 저장소는 제련표의 각 항목에
 *       {@link FurnaceRules.SmeltCategory} 를 달아 같은 분할을 낸다.</li>
 *   <li><b>경험치</b>: blasting/smoking 레시피의 experience 값은 대응하는 smelting 레시피와
 *       같다(예: raw_iron→iron_ingot 은 세 종류 모두 0.7). 그래서 배율은 셋 다 1.000 이다.
 *       그럼에도 배율을 표에 두는 이유는, 시간만 다르고 경험치는 같다는 사실이 어딘가에
 *       기록되지 않으면 다음 편집자가 "시간이 절반이니 경험치도 절반" 으로 바로잡으려 들기
 *       때문이다.</li>
 * </ul>
 *
 * <h2>점화 쌍둥이와 점유 불변식</h2>
 * <p>각 변형은 미점화/점화 두 블록 ID 를 가진다. lit ID(841·842)는 직업 스테이션 연속 구간
 * 521–533 밖이므로, {@code VillagerJobSitePolicy.stationCode} 는 반드시
 * {@link #baseBlockId(int)} 정규화를 먼저 거쳐야 점화 중에도 주민 점유가 유지된다.
 */
public enum FurnaceVariant {

    /** 화로(33/34). 제련표 전부를 받고 한 건에 100틱(10초) 쓴다. */
    FURNACE(0, Blocks.FURNACE, Blocks.FURNACE_LIT, 1, 1000),
    /** 용광로(522/841). 광석·원석·금속만 받고 화로의 절반인 50틱(5초) 쓴다. */
    BLAST(1, Blocks.BLAST_FURNACE, Blocks.BLAST_FURNACE_LIT, 2, 1000),
    /** 훈연기(532/842). 음식만 받고 화로의 절반인 50틱(5초) 쓴다. */
    SMOKER(2, Blocks.SMOKER, Blocks.SMOKER_LIT, 2, 1000);

    /**
     * 영속 스키마에 기록되는 안정 코드입니다. <b>ordinal 이 아니라 이 값</b>이 저장되므로
     * 상수를 재정렬해도 저장된 화로가 다른 변형으로 되살아나지 않습니다.
     *
     * <p>{@link #FURNACE} 가 0 인 것이 마이그레이션 계약이다 — 변형 열이 없던 시절의 행과
     * MySQL 이 새 NOT NULL int 열에 채우는 암묵 기본값 0 이 모두 화로로 읽힌다.
     */
    private final int code;
    private final int blockId;
    private final int litBlockId;
    private final int cookDivisor;
    private final int xpMultiplierMilli;

    FurnaceVariant(int code, int blockId, int litBlockId, int cookDivisor, int xpMultiplierMilli) {
        this.code = code;
        this.blockId = blockId;
        this.litBlockId = litBlockId;
        this.cookDivisor = cookDivisor;
        this.xpMultiplierMilli = xpMultiplierMilli;
    }

    public int code() {
        return code;
    }

    /** 미점화 블록 ID. */
    public int blockId() {
        return blockId;
    }

    /** 점화 블록 ID. */
    public int litBlockId() {
        return litBlockId;
    }

    /** 점화 여부에 따른 블록 ID. 틱 루프의 스왑 대상이다. */
    public int blockId(boolean lit) {
        return lit ? litBlockId : blockId;
    }

    /** 이 변형에서 제련 한 건에 드는 프로젝트 틱 수. 화로 100, 용광로·훈연기 50. */
    public int cookTotalTicks() {
        return FurnaceRules.TICKS_PER_SMELT / cookDivisor;
    }

    /** 제련 경험치 배율(1/1000). 바닐라는 셋 다 1.000 이다. */
    public int xpMultiplierMilli() {
        return xpMultiplierMilli;
    }

    /** 이 변형이 받아 주는 제련 분류인가. */
    public boolean accepts(FurnaceRules.SmeltCategory category) {
        if (category == null) return false;
        return switch (this) {
            case FURNACE -> true;
            case BLAST -> category == FurnaceRules.SmeltCategory.ORE;
            case SMOKER -> category == FurnaceRules.SmeltCategory.FOOD;
        };
    }

    /** 미점화/점화 어느 ID 든 그 변형. 제련로가 아니면 {@code null}. */
    public static FurnaceVariant of(int blockId) {
        int id = blockId & 0xFFFF;
        for (FurnaceVariant variant : values()) {
            if (id == variant.blockId || id == variant.litBlockId) return variant;
        }
        return null;
    }

    /** 저장된 안정 코드로 되돌립니다. 알 수 없는 코드는 화로로 읽습니다(구형 행 기본값). */
    public static FurnaceVariant fromCode(int code) {
        for (FurnaceVariant variant : values()) {
            if (variant.code == code) return variant;
        }
        return FURNACE;
    }

    /**
     * 점화 쌍둥이를 미점화 원본으로 정규화합니다. 제련로가 아닌 ID 는 그대로 돌려줍니다.
     *
     * <p>직업 스테이션 판정·드랍·채굴 시간·폭발 저항이 모두 이 한 함수를 거치므로, 점화 중인
     * 용광로가 "다른 블록" 으로 보이는 경로가 하나도 남지 않습니다.
     */
    public static int baseBlockId(int blockId) {
        FurnaceVariant variant = of(blockId);
        return variant == null ? blockId : variant.blockId;
    }

    /** 런타임 전용 점화 쌍둥이 ID 인가. */
    public static boolean isLitBlockId(int blockId) {
        FurnaceVariant variant = of(blockId);
        return variant != null && (blockId & 0xFFFF) == variant.litBlockId;
    }
}
