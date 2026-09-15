package com.gameexpert.engine;

/**
 * [SURV-H] 바닐라 허기(food/saturation/exhaustion)의 순수 상수·판정 모음(MC-REFERENCE §7 허기 조항).
 *
 * <p><b>고정소수 규약</b>: 바닐라 {@code FoodData}는 saturation·exhaustion을 {@code float}로 다루지만,
 * Java판과 정적판(JS {@code number} = double)이 같은 값을 내려면 부동소수 누적을 피해야 한다.
 * 그래서 두 값 모두 <b>1/1000 단위 정수(milli)</b>로만 계산한다. 바닐라 수치가 모두 0.005의 배수라
 * milli 정수로 손실 없이 표현되고, 누적·비교가 정수 연산이라 양판 결과가 정확히 일치한다.
 *
 * <p><b>10 TPS 환산</b>: 서버는 10 TPS, 바닐라는 20 TPS다. 바닐라 {@code FoodData.tickTimer}의
 * 임계 10틱·80틱은 모두 짝수라 서버 틱 5·40으로 나머지 없이 환산된다. exhaustion 소모는
 * MC 틱당 1회 판정이므로 서버 1틱마다 {@link #EXHAUSTION_STEPS_PER_TICK}회 돌린다.
 * 이동 exhaustion은 <b>거리 비례</b>라 TPS와 무관하다.
 */
public final class HungerRules {

    private HungerRules() {
    }

    /** saturation·exhaustion 고정소수 배율(1.0 = 1000). */
    public static final int MILLI = 1000;

    /** 허기 상한(바닐라 20 = 드럼스틱 10개). */
    public static final int MAX_FOOD = 20;

    /** 신규·리스폰 플레이어의 허기(만복). */
    public static final int INITIAL_FOOD = 20;

    /** 신규·리스폰 플레이어의 saturation 5.0. 바닐라 {@code FoodData} 기본값이다. */
    public static final int INITIAL_SATURATION_MILLI = 5 * MILLI;

    /** 이 값을 넘으면 4.0을 덜어 saturation(없으면 허기) 1을 깎는다. */
    public static final int EXHAUSTION_THRESHOLD_MILLI = 4 * MILLI;

    // ── exhaustion 소스 ──
    /** WebCraft 이동 규칙: 걷기·스프린트·수영·점프의 수평 이동 1블록당 0.01. */
    public static final int MOVEMENT_EXHAUSTION_PER_BLOCK_MILLI = 10;
    /** 블록 파괴 1회 0.005. */
    public static final int BREAK_EXHAUSTION_MILLI = 5;
    /** 근접 공격 1회 0.1. */
    public static final int ATTACK_EXHAUSTION_MILLI = 100;
    /** 느린 자연 회복 1점당 6.0. */
    public static final int REGEN_EXHAUSTION_MILLI = 6 * MILLI;

    // ── 회복·굶주림 타이밍(서버 10 TPS 틱) ──
    /** 만복+saturation 구간의 빠른 회복 주기. 바닐라 MC 10틱. */
    public static final int SATURATED_REGEN_INTERVAL_TICKS = 5;
    /** 허기 18 이상 구간의 느린 회복 주기. 바닐라 MC 80틱(4초). */
    public static final int REGEN_INTERVAL_TICKS = 40;
    /** 허기 0에서의 굶주림 피해 주기. 바닐라 MC 80틱(4초). */
    public static final int STARVE_INTERVAL_TICKS = 40;
    /** 서버 1틱은 MC 2틱이므로 exhaustion 소모 판정을 2회 돌린다. */
    public static final int EXHAUSTION_STEPS_PER_TICK = 2;

    /** 느린 자연 회복이 걸리는 최소 허기. */
    public static final int REGEN_FOOD_THRESHOLD = 18;
    /** 빠른(포화) 회복에 필요한 허기 = 만복. */
    public static final int SATURATED_REGEN_FOOD_THRESHOLD = MAX_FOOD;
    /** 이 값 이하면 스프린트가 불가능하다(바닐라 {@code Player.canSprint}: food &gt; 6). */
    public static final int SPRINT_FOOD_THRESHOLD = 6;
    /** 빠른 회복 1회가 소모하는 saturation 상한 6.0. 회복량은 이 값의 1/6 HP다. */
    public static final int SATURATED_REGEN_MAX_SATURATION_MILLI = 6 * MILLI;
    /** 굶주림 피해량(1점 = 반 하트). */
    public static final int STARVE_DAMAGE = 1;

    /**
     * 난이도별 굶주림 피해 하한 중 normal 값. 바닐라 {@code FoodData.tick}의
     * {@code health > 10 || HARD || (health > 1 && NORMAL)} 분기를 "이 체력 초과일 때만 피해"로 정리한 것이다.
     *
     * <p>난이도 배선은 끝났다 — {@code WorldRuntime} 이 {@link Difficulty#starvationMinHealth()}
     * (easy 10 / normal 1 / hard 0)를 {@code EnvironmentSystem} 에 넘기고, 거기서
     * {@link #starvationDamageAllowed(int, int)} 로 들어간다. 이 상수는 난이도를 모르는 호출부
     * (단일 인자 오버로드·테스트)의 기본값으로만 남는다.
     */
    public static final int STARVE_MIN_HEALTH_NORMAL = 1;

    /** 굶주림 피해를 줄 수 있는가(현재 체력이 난이도 하한을 넘는가). */
    public static boolean starvationDamageAllowed(int health, int minHealth) {
        return health > minHealth;
    }

    /** normal 난이도 기준 굶주림 판정. 난이도를 모르는 호출부(테스트)용 편의 오버로드다. */
    public static boolean starvationDamageAllowed(int health) {
        return starvationDamageAllowed(health, STARVE_MIN_HEALTH_NORMAL);
    }

    /** food &gt; 6 에서만 스프린트할 수 있다. */
    public static boolean canSprint(int food) {
        return food > SPRINT_FOOD_THRESHOLD;
    }

    /**
     * 이동 exhaustion의 1/100 milli 값. 낮은 단일 비율에서 이동 메시지마다 milli로 버리면 전송
     * 분할에 따라 총량이 달라지므로, cm 정수화한 값을 돌려 플레이어 상태가 나머지를 이월하게 한다.
     *
     * @param perBlockMilli 블록당 exhaustion(milli)
     * @param distanceBlocks 이번 구간의 수평 이동 거리(블록)
     */
    public static int movementExhaustionHundredthsMilli(int perBlockMilli, double distanceBlocks) {
        if (!(distanceBlocks > 0.0)) return 0;
        int centimeters = (int) Math.round(distanceBlocks * 100.0);
        if (centimeters <= 0) return 0;
        return perBlockMilli * centimeters;
    }

    /** 빠른 회복 1회의 회복량(milli HP). 바닐라 {@code heal(min(sat,6)/6)}의 고정소수판이다. */
    public static int saturatedRegenHealMilli(int saturationMilli) {
        int used = Math.min(saturationMilli, SATURATED_REGEN_MAX_SATURATION_MILLI);
        return used / 6;
    }
}
