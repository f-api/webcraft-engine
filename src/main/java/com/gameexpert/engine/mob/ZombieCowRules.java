package com.gameexpert.engine.mob;

/**
 * 좀비 소(WebCraft 창작몹, stableId 76)의 <b>상태 없는</b> 자체 계약.
 *
 * <p>근거 등급 C — 바닐라에 {@code zombie_cow} 는 없다. 공유 계약은
 * {@link UndeadAnimalRules} 가 소유하고 여기에는 그 계약을 소({@link MobType#COW})에 적용한
 * 결과만 리터럴로 남는다(정적판 사본 {@code ZOMBIE_COW_*} 와 상수 대 상수로 대조된다).
 *
 * <p><b>정체성</b> — 좀비 동물 흔한 티어의 맏이다. 소는 밤 목장에서 가장 먼저 부패하는
 * 가축이고, 체구가 커서 무리에 섞이면 벽처럼 앞을 막는다. 그래서 이 종만 흔한 티어에서
 * <b>대형 썩은 가죽</b>(0~2)을 떨군다 — 돼지·양은 중형(0~1)이다.
 *
 * <p>좀비 보정: 체력 10 + {@value UndeadAnimalRules#HEALTH_BONUS} = {@value #MAX_HEALTH},
 * 배회 0.20 × {@value UndeadAnimalRules#IDLE_SPEED_SCALE} = {@value #IDLE_BLOCKS_PER_TICK},
 * 감지 {@value UndeadAnimalRules#DETECT_RANGE}. AABB·눈높이는 살아 있는 소와 글자 그대로 같다.
 */
public final class ZombieCowRules {

    private ZombieCowRules() {
    }

    /** 소와 같은 AABB 폭. */
    public static final double WIDTH = 0.9;
    /** 소와 같은 AABB 높이. */
    public static final double HEIGHT = 1.4;
    /** 소와 같은 눈높이. 네발 가축은 부패해도 자세가 바뀌지 않는다. */
    public static final double EYE_HEIGHT = 1.3;
    /** 최대 체력. 소 10 + 부패로 굳은 근막 4. */
    public static final int MAX_HEALTH = 14;
    /** 방어도. 좀비 계열과 같은 2. */
    public static final double ARMOR = 2.0;
    /** 방어구 관통 저항. 없다. */
    public static final double TOUGHNESS = 0.0;
    /** 배회 속도(블록/10TPS 틱). 소 0.20 × 0.8. */
    public static final double IDLE_BLOCKS_PER_TICK = 0.16;
    /** 추격 속도(블록/틱) = 4.0블록/초. 플레이어 질주 5.6 보다 확실히 느린 "벽"이다. */
    public static final double PURSUIT_BLOCKS_PER_TICK = 0.40;
    /** 추적 감지 반경(블록). 좀비 계열과 같다. */
    public static final double DETECT_RANGE = 35.0;
    /** 공격 사거리(블록). 체구가 커서 늑대(1.6)보다 길다. */
    public static final double ATTACK_RANGE = 1.8;
    /** 한 대 피해. 머리로 들이받는 큰 가축이라 늑대와 같은 4 다. */
    public static final int ATTACK_DAMAGE = 4;
    /** 공격 간격(10TPS 틱). 늑대(10)보다 굼뜨고 곰(14)보다는 빠르다. */
    public static final int ATTACK_COOLDOWN_TICKS = 12;
    /** 썩은 살점 굴림 상한(배타). 좀비 계열과 같은 0~2. */
    public static final int ROTTEN_FLESH_ROLL = 3;
    // 썩은 가죽은 종별 표가 아니라 RottenLeatherDropRules 공통 pool 이 붙인다(대형 티어).
}
