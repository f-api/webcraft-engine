package com.gameexpert.engine.mob;

/**
 * 좀비 양(WebCraft 창작몹, stableId 78)의 <b>상태 없는</b> 자체 계약.
 *
 * <p>근거 등급 C. 공유 계약은 {@link UndeadAnimalRules} 가 소유한다. 정적판 사본은
 * {@code StandaloneMobRules.ts} 의 {@code ZOMBIE_SHEEP_*} 상수다.
 *
 * <p><b>전단할 수 없다.</b> 이 종의 유일한 특수 계약이다. 살아 있는 양의 가위 전단
 * ({@code MobSystem} 의 양털 경로)은 종을 {@link MobType#SHEEP} 로 못박고 있고 이 종은 그 문에
 * 닿지 않는다. 부패한 양털은 <b>썩은 가죽</b>으로만 회수된다 — 언데드에서 멀쩡한 양털이
 * 나오면 썩은 가죽 경제의 근거가 무너지기 때문이다. 번식도 하지 않는다.
 *
 * <p>좀비 보정: 체력 8 + 4 = {@value #MAX_HEALTH}, 배회 0.23 × 0.8 ≈
 * {@value #IDLE_BLOCKS_PER_TICK}(소수 둘째 자리 반올림).
 */
public final class ZombieSheepRules {

    private ZombieSheepRules() {
    }

    /** 양과 같은 AABB 폭. */
    public static final double WIDTH = 0.9;
    /** 양과 같은 AABB 높이. */
    public static final double HEIGHT = 1.3;
    /** 양과 같은 눈높이. */
    public static final double EYE_HEIGHT = 1.235;
    /** 최대 체력. 양 8 + 4. 흔한 티어에서 가장 약하다. */
    public static final int MAX_HEALTH = 12;
    /** 방어도. */
    public static final double ARMOR = 2.0;
    /** 방어구 관통 저항. */
    public static final double TOUGHNESS = 0.0;
    /** 배회 속도(블록/틱). 양 0.23 × 0.8 = 0.184 → 0.18. */
    public static final double IDLE_BLOCKS_PER_TICK = 0.18;
    /** 추격 속도(블록/틱) = 4.2블록/초. */
    public static final double PURSUIT_BLOCKS_PER_TICK = 0.42;
    /** 추적 감지 반경(블록). */
    public static final double DETECT_RANGE = 35.0;
    /** 공격 사거리(블록). */
    public static final double ATTACK_RANGE = 1.6;
    /** 한 대 피해. */
    public static final int ATTACK_DAMAGE = 3;
    /** 공격 간격(10TPS 틱). */
    public static final int ATTACK_COOLDOWN_TICKS = 12;
    /** 썩은 살점 굴림 상한(배타). */
    public static final int ROTTEN_FLESH_ROLL = 3;
    // 양털은 나오지 않는다(전단 불가 계약). 썩은 가죽은 공통 pool(중형 티어 — 0~1).
}
