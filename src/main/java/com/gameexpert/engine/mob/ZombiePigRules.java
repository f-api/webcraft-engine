package com.gameexpert.engine.mob;

/**
 * 좀비 돼지(WebCraft 창작몹, stableId 77)의 <b>상태 없는</b> 자체 계약.
 *
 * <p>근거 등급 C. 공유 계약은 {@link UndeadAnimalRules} 가 소유한다. 정적판 사본은
 * {@code StandaloneMobRules.ts} 의 {@code ZOMBIE_PIG_*} 상수다.
 *
 * <p><b>좀비 피그맨과는 다른 종이다.</b> {@link MobType#ZOMBIE_PIGMAN} 은 죽지 않은
 * <b>돼지 인간형</b>이고 이 종은 네발로 걷는 <b>가축 돼지</b>가 부패한 것이다. 무리 연쇄
 * 분노({@code ZombiePigman}) 계약도 여기에는 없다 — 흔한 티어의 잡몹이지 무리 보복형이 아니다.
 *
 * <p>좀비 보정: 체력 10 + 4 = {@value #MAX_HEALTH}, 배회 0.25 × 0.8 =
 * {@value #IDLE_BLOCKS_PER_TICK}. 낮고 빠른 체형이라 흔한 티어에서 가장 잘 붙는다.
 */
public final class ZombiePigRules {

    private ZombiePigRules() {
    }

    /** 돼지와 같은 AABB 폭. */
    public static final double WIDTH = 0.9;
    /** 돼지와 같은 AABB 높이. */
    public static final double HEIGHT = 0.9;
    /** 돼지와 같은 눈높이(바닐라 0.9 × 0.965…의 WebCraft 등록값). */
    public static final double EYE_HEIGHT = 0.86875;
    /** 최대 체력. 돼지 10 + 4. */
    public static final int MAX_HEALTH = 14;
    /** 방어도. */
    public static final double ARMOR = 2.0;
    /** 방어구 관통 저항. */
    public static final double TOUGHNESS = 0.0;
    /** 배회 속도(블록/틱). 돼지 0.25 × 0.8. */
    public static final double IDLE_BLOCKS_PER_TICK = 0.20;
    /** 추격 속도(블록/틱) = 4.4블록/초. 흔한 티어에서 가장 빠르되 질주보다는 느리다. */
    public static final double PURSUIT_BLOCKS_PER_TICK = 0.44;
    /** 추적 감지 반경(블록). */
    public static final double DETECT_RANGE = 35.0;
    /** 공격 사거리(블록). 낮은 체형이라 소(1.8)보다 짧다. */
    public static final double ATTACK_RANGE = 1.5;
    /** 한 대 피해. 소(4)보다 작은 3. */
    public static final int ATTACK_DAMAGE = 3;
    /** 공격 간격(10TPS 틱). */
    public static final int ATTACK_COOLDOWN_TICKS = 12;
    /** 썩은 살점 굴림 상한(배타). */
    public static final int ROTTEN_FLESH_ROLL = 3;
    // 썩은 가죽은 공통 pool 이 붙인다(중형 티어 — 0~1).
}
