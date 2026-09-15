package com.gameexpert.engine.mob;

/**
 * 좀비 염소(WebCraft 창작몹, stableId 79)의 <b>상태 없는</b> 자체 계약.
 *
 * <p>근거 등급 C. 공유 계약은 {@link UndeadAnimalRules} 가 소유하고, <b>돌진</b>은 그 클래스의
 * {@code CHARGE_*} 상수를 <b>그대로 재사용</b>한다 — 무덤 사슴·부패 멧돼지와 같은 돌진이다.
 * 정적판 사본은 {@code StandaloneMobRules.ts} 의 {@code ZOMBIE_GOAT_*} 상수다.
 *
 * <p><b>정체성</b> — 산악 중간 티어. 살아 있는 염소의 들이받기(바닐라 {@code Goat} 의 ram goal)
 * 가 부패해서도 남았다는 것이 이 종의 유일한 특수 행동이며, WebCraft 는 그 들이받기를
 * {@link UndeadAnimalRules} 의 공통 돌진으로 표현한다(종별 사본 금지).
 *
 * <p>좀비 보정: 체력 10 + 4 = {@value #MAX_HEALTH}, 배회 0.20 × 0.8 =
 * {@value #IDLE_BLOCKS_PER_TICK}.
 */
public final class ZombieGoatRules {

    private ZombieGoatRules() {
    }

    /** 염소와 같은 AABB 폭. */
    public static final double WIDTH = 0.9;
    /** 염소와 같은 AABB 높이. */
    public static final double HEIGHT = 1.3;
    /** 염소와 같은 눈높이. 양(1.235)보다 낮다 — 골격이 같아 보여도 머리를 낮게 든다. */
    public static final double EYE_HEIGHT = 1.105;
    /** 최대 체력. 염소 10 + 4. */
    public static final int MAX_HEALTH = 14;
    /** 방어도. */
    public static final double ARMOR = 2.0;
    /** 방어구 관통 저항. */
    public static final double TOUGHNESS = 0.0;
    /** 배회 속도(블록/틱). 염소 0.20 × 0.8. */
    public static final double IDLE_BLOCKS_PER_TICK = 0.16;
    /** 추격 속도(블록/틱) = 4.2블록/초. 돌진 중에는 2배가 된다. */
    public static final double PURSUIT_BLOCKS_PER_TICK = 0.42;
    /** 추적 감지 반경(블록). */
    public static final double DETECT_RANGE = 35.0;
    /** 공격 사거리(블록). */
    public static final double ATTACK_RANGE = 1.6;
    /** 한 대 피해. 돌진 중에는 UndeadAnimalRules.CHARGE_DAMAGE_BONUS 만큼 더 아프다. */
    public static final int ATTACK_DAMAGE = 3;
    /** 공격 간격(10TPS 틱). */
    public static final int ATTACK_COOLDOWN_TICKS = 12;
    /** 썩은 살점 굴림 상한(배타). */
    public static final int ROTTEN_FLESH_ROLL = 3;
    // 썩은 가죽은 공통 pool(중형 티어 — 0~1).
}
