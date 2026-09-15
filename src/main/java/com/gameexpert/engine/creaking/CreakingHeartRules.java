package com.gameexpert.engine.creaking;

import com.gameexpert.engine.WorldClock;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.terrain.Blocks;

/**
 * 크리킹 하트 블록의 <b>상태 없는</b> 규칙 정본. 근거는 [B] minecraft.wiki
 * «Creaking Heart» · «Resin Clump»(조회 2026-08-10)이며 발췌 핀은
 * {@code docs/research/mc-pale-garden-1214.md} §7 이다.
 *
 * <p><b>세 상태</b>. 바닐라는 {@code uprooted} · {@code dormant} · {@code awake} 셋이다.
 * 이 저장소는 겉모습 스왑을 ID 쌍으로 표현하므로({@link Blocks#CREAKING_HEART} ↔
 * {@link Blocks#CREAKING_HEART_ACTIVE}) 상태는 <b>파생</b>이다:
 * <ul>
 *   <li>정렬되지 않았다 → uprooted → 비활성 ID</li>
 *   <li>정렬됐고 낮이다 → dormant → 비활성 ID</li>
 *   <li>정렬됐고 밤이다 → awake → 활성 ID, 크리킹 하나를 거느린다</li>
 * </ul>
 * 겉모습이 갈리는 자리는 {@code uprooted|dormant} 대 {@code awake} 이고 그것이 곧 ID 쌍이다.
 * 바닐라도 dormant 와 awake 만 텍스처가 다르고 uprooted 는 1.21.5 에서 dormant 텍스처를
 * 다시 나눴는데([A] 1.21.5 changelog "Block Changes: creaking heart dormant" — 핀
 * {@code mc-1215/SPRING_TO_LIFE.md} §8), 이 저장소는 두 장만 그린다([C] divergence).
 *
 * <p><b>정렬 판정에 축 속성을 쓰지 않는다.</b> 바닐라는 설치 시점의 {@code axis} 를 굳히고 그
 * 축만 본다. 여기서는 저장하지 않고 매 틱 세 축을 본다 — 이유와 결과 차이는
 * {@link Blocks#CREAKING_HEART} 주석(divergence 2)이 소유한다.
 *
 * <p>정적판 사본은 {@code client/src/backend/standalone/StandaloneCreakingRules.ts} 이고
 * <b>같은 순서·같은 분기</b>여야 한다.
 */
public final class CreakingHeartRules {

    private CreakingHeartRules() {
    }

    /** 정렬을 이루는 이웃 쌍의 축(0=Y, 1=X, 2=Z). 정렬돼 있지 않으면 이 값이다. */
    public static final int AXIS_NONE = -1;

    /**
     * 수지 덩어리 드랍의 최소 개수. [B] «Creaking Heart» — 실크터치 없이 캐면 1~3 이다.
     */
    public static final int RESIN_CLUMP_MIN = 1;
    /** 무보정 굴림 폭(배타). {@code MIN + floor(roll * SPAN)} → 1~3. */
    public static final int RESIN_CLUMP_SPAN = 3;
    /**
     * 행운 레벨당 늘어나는 굴림 폭. [B] — "Fortune can increase the maximum drops by 1 per
     * level (up to 6 with Fortune 3)". 즉 폭이 {@code SPAN + level} 이라 행운 3 에서 1~6 이다.
     * 바닐라 {@code uniform_bonus_count} 그대로이며 광석의 ordinary 배율과는 <b>다른 함수</b>다
     * — 그래서 {@code OreRules.fortuneApplies} 를 건드리지 않는다.
     */
    public static final int RESIN_CLUMP_FORTUNE_SPAN_PER_LEVEL = 1;

    /**
     * 대저택 상자 pool 2 의 수지 덩어리 최소·최대 개수. [B] «Resin Clump» — "2–4 items, 53.8%
     * chance". 가중치 50 은 이 저장소의 {@code ExplorationLoot.MANSION_SUPPLIES} 에 <b>이미</b>
     * EMPTY 자리표로 서 있었으므로 확률 질량이 움직이지 않는다.
     */
    public static final int MANSION_CLUMP_MIN = 2;
    public static final int MANSION_CLUMP_MAX = 4;

    /**
     * 이 하트가 정렬돼 있는 축, 또는 {@link #AXIS_NONE}.
     *
     * <p>[B] «Creaking Heart» — "aligned between two pale oak logs, stripped pale oak logs,
     * pale oak wood, or stripped pale oak wood with the same orientation". 이 저장소에는
     * {@code wood}(껍질 6면) 변형이 없으므로 원목·벗긴 원목 두 계열만 본다.
     *
     * <p>축 주사 순서는 <b>Y → X → Z</b> 로 고정한다. 두 축이 동시에 성립할 때 어느 쪽을
     * 고르는지가 두 권위에서 같아야 하기 때문이다(값 자체는 활성 여부에 영향이 없지만
     * 트레이스가 축을 싣는다).
     */
    public static int alignedAxis(BlockLookup lookup, int x, int y, int z) {
        if (isPaleOakLogLike(lookup.blockAt(x, y - 1, z))
                && isPaleOakLogLike(lookup.blockAt(x, y + 1, z))) return 0;
        if (isPaleOakLogLike(lookup.blockAt(x - 1, y, z))
                && isPaleOakLogLike(lookup.blockAt(x + 1, y, z))) return 1;
        if (isPaleOakLogLike(lookup.blockAt(x, y, z - 1))
                && isPaleOakLogLike(lookup.blockAt(x, y, z + 1))) return 2;
        return AXIS_NONE;
    }

    /** 창백한 참나무 원목·벗긴 원목인가(축 무관). 비상주·범위 밖(음수)은 아니다. */
    public static boolean isPaleOakLogLike(int blockId) {
        return blockId >= 0
                && (Blocks.isPaleOakLog(blockId) || Blocks.isStrippedPaleOakLog(blockId));
    }

    /**
     * 지금 이 하트가 <b>깨어 있어야 하는가</b>. 정렬 + 밤이 전부이며 난수를 쓰지 않는다.
     *
     * <p>밤 창은 이 저장소의 {@link WorldClock#isNight(long)} 하나를 그대로 쓴다 — 바닐라의
     * 12600~23400(24000 하루)을 다시 옮기지 않는 이유는 그러면 몹 스폰·잠자기·팬텀이 보는
     * 밤과 크리킹이 보는 밤이 서로 다른 창이 되기 때문이다(divergence — 값이 아니라 기준을
     * 재사용한다).
     */
    public static boolean awake(BlockLookup lookup, int x, int y, int z, long worldTime) {
        return alignedAxis(lookup, x, y, z) != AXIS_NONE && WorldClock.isNight(worldTime);
    }

    /** 깨어 있는 상태에 대응하는 블록 ID. 겉모습 스왑의 유일한 정본이다. */
    public static int blockIdFor(boolean awake) {
        return awake ? Blocks.CREAKING_HEART_ACTIVE : Blocks.CREAKING_HEART;
    }

    /**
     * 실크터치 없이 캤을 때의 수지 덩어리 개수. {@code roll} 은 [0,1) 균등이고 난수는
     * <b>호출부가 한 번만</b> 주입한다(광석 수량 계약과 같은 규약).
     */
    public static int resinClumpDropCount(double roll, int fortuneLevel) {
        double normalized = Math.max(0.0, Math.min(Math.nextDown(1.0), roll));
        int span = RESIN_CLUMP_SPAN
                + Math.max(0, fortuneLevel) * RESIN_CLUMP_FORTUNE_SPAN_PER_LEVEL;
        return RESIN_CLUMP_MIN + (int) (normalized * span);
    }

    /**
     * 채굴 수량 곱셈 항. 크리킹 하트가 아니면 <b>1</b> 이라 다른 블록에 영향이 없다
     * ({@code SulfurRules.minedDropCount} 가 {@code ItemEntitySystem} 에 끼어드는 것과 같은
     * 모양이다). 실크 터치가 걸려 있으면 하트 자신 한 개가 나가야 하므로 여기서도 1 이다.
     *
     * <p>행운은 <b>광석의 ordinary 배율이 아니라</b> 바닐라 {@code uniform_bonus_count} 다 —
     * 그래서 {@code OreRules.fortuneApplies} 를 넓히지 않고 이 함수가 직접 폭을 늘린다.
     * 난수는 호출부가 이미 뽑아 둔 채굴 굴림 하나를 그대로 받는다(새 난수를 만들지 않는다).
     */
    public static int minedDropCount(int blockId, long enchantments, double roll) {
        if (!Blocks.isCreakingHeart(blockId)) return 1;
        if (EnchantmentRules.enchantLevel(enchantments, EnchantmentRules.SILK_TOUCH) > 0) return 1;
        return resinClumpDropCount(
                roll, EnchantmentRules.enchantLevel(enchantments, EnchantmentRules.FORTUNE));
    }

    /** 이 시스템이 월드에 요구하는 전부. {@code DriedGhastHydrationSystem.World} 와 같은 폭이다. */
    @FunctionalInterface
    public interface BlockLookup {
        /** 비상주·범위 밖이면 음수. */
        int blockAt(int x, int y, int z);
    }
}
