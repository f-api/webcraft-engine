package com.gameexpert.engine.mob;

/**
 * 부패 멧돼지(WebCraft 창작몹, stableId 83)의 <b>상태 없는</b> 자체 계약.
 *
 * <p>근거 등급 C — 바닐라에 멧돼지는 없다. 정적판 사본은 {@code StandaloneMobRules.ts} 의
 * {@code CARRION_BOAR_*} 상수다.
 *
 * <p><b>좀비 돼지({@link ZombiePigRules})와 별개 종이다.</b> 좀비 돼지는 흔한 티어의 가축이고
 * 이 종은 창작 희귀 티어의 <b>대형 야생종</b>이다 — 체구가 넓고(폭 {@value #WIDTH}) 낮으며,
 * 체력·방어도·피해가 모두 한 단계 위다. 둘을 한 종으로 묶지 않는 이유가 이것이다.
 *
 * <p><b>정체성 — 엄니 돌진.</b> 처음부터 적대이며, 붙기 전에 {@link UndeadAnimalRules} 의 공통
 * 돌진을 <b>횟수 제한 없이</b> 반복한다(무덤 사슴만 1회 제한이 있다). 정면으로 받으면 아프지만
 * 방향 전환이 굼떠 옆으로 흘리면 지나간다 — 배회 속도 {@value #IDLE_BLOCKS_PER_TICK} 가 좀비
 * 동물 중 가장 느린 이유다.
 */
public final class CarrionBoarRules {

    private CarrionBoarRules() {
    }

    /** AABB 폭. 넓적한 대형 야생 돼지라 좀비 돼지(0.9)보다 넓다. */
    public static final double WIDTH = 1.2;
    /** AABB 높이. 넓지만 낮다. */
    public static final double HEIGHT = 1.1;
    /** 눈높이. 머리를 낮게 들고 다니는 자세다. */
    public static final double EYE_HEIGHT = 0.95;
    /** 최대 체력. 창작 희귀 대형이라 무덤 사슴(20)보다 질기다. */
    public static final int MAX_HEALTH = 26;
    /** 방어도. 두꺼운 부패 가죽이라 좀비 계열 2 보다 높은 4 다. */
    public static final double ARMOR = 4.0;
    /** 방어구 관통 저항. */
    public static final double TOUGHNESS = 0.0;
    /** 배회 속도(블록/틱). 좀비 동물 중 가장 굼뜨다(좀비곰 0.10 다음). */
    public static final double IDLE_BLOCKS_PER_TICK = 0.14;
    /** 추격 속도(블록/틱) = 5.8블록/초 … 가 아니라 5.0. 돌진 중에만 질주를 넘어선다. */
    public static final double PURSUIT_BLOCKS_PER_TICK = 0.50;
    /** 추적 감지 반경(블록). 좀비 계열과 같다. */
    public static final double DETECT_RANGE = 35.0;
    /** 공격 사거리(블록). 엄니가 길다. */
    public static final double ATTACK_RANGE = 2.0;
    /** 한 대 피해. 돌진 중에는 CHARGE_DAMAGE_BONUS 가 더 붙어 8 이 된다(좀비곰 7 위). */
    public static final int ATTACK_DAMAGE = 6;
    /** 공격 간격(10TPS 틱). 좀비곰(14)보다도 느린 한 방 위주다. */
    public static final int ATTACK_COOLDOWN_TICKS = 18;
    /** 썩은 살점 굴림 상한(배타). */
    public static final int ROTTEN_FLESH_ROLL = 3;
    /** 플레이어 처치 시에만 굴리는 창작 희귀 드랍(뼈 1). {@code nextInt(6) == 0} → 16.7%. */
    public static final int BONE_RARE_ROLL = 6;
    // 썩은 가죽은 공통 pool(대형 티어 — 0~2).
}
