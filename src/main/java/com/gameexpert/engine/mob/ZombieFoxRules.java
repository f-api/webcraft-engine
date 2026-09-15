package com.gameexpert.engine.mob;

/**
 * 좀비 여우(WebCraft 창작몹, stableId 80)의 <b>상태 없는</b> 자체 계약.
 *
 * <p>근거 등급 C. 공유 계약은 {@link UndeadAnimalRules} 가 소유한다. 정적판 사본은
 * {@code StandaloneMobRules.ts} 의 {@code ZOMBIE_FOX_*} 상수다.
 *
 * <p><b>정체성</b> — 희귀 재미 티어. 개체가 작고 빨라서 "떨어뜨릴 수 없는 한 마리"다.
 * 좀비 늑대가 무리로 위협이 된다면 이 종은 <b>혼자서</b> 끝까지 따라붙는다 — 추격 속도가
 * 좀비 계열 통틀어 가장 빠른 {@value #PURSUIT_BLOCKS_PER_TICK}(5.2블록/초)이며, 그래도
 * 플레이어 질주 5.6블록/초보다는 <b>느리다</b>(WebCraft 좀비 동물의 불변 계약).
 * 대신 체력이 낮아 한 번 붙잡히면 금방 죽는다.
 *
 * <p>여우 변종(red/snow)은 물려받지 않는다 — 부패한 개체는 털색이 남지 않는다는 것이
 * 좀비 동물 전체의 영속 계약이다({@code MOB_VARIANTS_BY_KIND} 미등록 = 프로토콜 null).
 *
 * <p>좀비 보정: 체력 10 + 4 = {@value #MAX_HEALTH}, 배회 0.300000012 × 0.8 ≈
 * {@value #IDLE_BLOCKS_PER_TICK}(소수 둘째 자리 반올림 — 좀비 늑대와 같은 값이 된다).
 */
public final class ZombieFoxRules {

    private ZombieFoxRules() {
    }

    /** 여우와 같은 AABB 폭. */
    public static final double WIDTH = 0.6;
    /** 여우와 같은 AABB 높이. */
    public static final double HEIGHT = 0.7;
    /** 여우와 같은 눈높이(WebCraft 등록값 0.4 — 원래도 머리가 낮은 종이다). */
    public static final double EYE_HEIGHT = 0.4;
    /** 최대 체력. 여우 10 + 4. */
    public static final int MAX_HEALTH = 14;
    /** 방어도. */
    public static final double ARMOR = 2.0;
    /** 방어구 관통 저항. */
    public static final double TOUGHNESS = 0.0;
    /** 배회 속도(블록/틱). 여우 0.3 × 0.8. */
    public static final double IDLE_BLOCKS_PER_TICK = 0.24;
    /** 추격 속도(블록/틱) = 5.2블록/초. 질주 5.6 보다는 여전히 느리다. */
    public static final double PURSUIT_BLOCKS_PER_TICK = 0.52;
    /** 추적 감지 반경(블록). */
    public static final double DETECT_RANGE = 35.0;
    /** 공격 사거리(블록). 작은 체구라 늑대(1.6)보다 짧다. */
    public static final double ATTACK_RANGE = 1.4;
    /** 한 대 피해. 작지만 물어뜯는다. */
    public static final int ATTACK_DAMAGE = 3;
    /** 공격 간격(10TPS 틱). 늑대와 같은 빠른 리듬. */
    public static final int ATTACK_COOLDOWN_TICKS = 10;
    /** 썩은 살점 굴림 상한(배타). */
    public static final int ROTTEN_FLESH_ROLL = 3;
    // 썩은 가죽은 공통 pool(중형 티어 — 0~1).
}
