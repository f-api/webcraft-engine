package com.gameexpert.engine.mob;

/**
 * 무덤 사슴(WebCraft 창작몹, stableId 82)의 <b>상태 없는</b> 자체 계약.
 *
 * <p>근거 등급 C — 바닐라에는 사슴 자체가 없다. 그래서 "원본 동물의 좀비 보정"이 아니라
 * <b>처음부터 창작</b>이며, {@link UndeadAnimalRules} 에서 가져오는 것은 방어도·돌진뿐이다.
 * 정적판 사본은 {@code StandaloneMobRules.ts} 의 {@code CARRION_STAG_*} 상수다.
 *
 * <p><b>정체성 — 도망가다 반격한다.</b> 이 종은 플레이어를 보면 <b>먼저 공격하지 않는다</b>.
 * 감지 반경 안에 들어오면 {@value #FLEE_BLOCKS_PER_TICK} 블록/틱으로 <b>등을 보이고 달아나며</b>
 * (플레이어 질주 0.56 보다 빠르다 — 쫓아가서 잡을 수 없다), 그 상태에서 한 대라도 맞으면
 * 그때부터 적대로 돌아서 <b>뿔 돌진 {@value #CHARGE_LIMIT}회</b>를 쓴다. 돌진을 다 쓰면 다시
 * 달아난다. "따라가서 잡는 사냥감"이 아니라 "잘못 건드리면 한 번 크게 받히는 사고"가 설계다.
 *
 * <p>돌진은 {@link UndeadAnimalRules} 의 공통 {@code CHARGE_*} 를 그대로 쓰되 횟수만
 * {@value #CHARGE_LIMIT} 로 조인다(표적을 새로 잡으면 다시 채워진다).
 *
 * <p><b>썩은 가죽 풀세트 무선공</b>: 이 종은 원래도 선공하지 않으므로 세트 착용은 눈에 보이는
 * 차이를 만들지 않는다. 다만 {@link UndeadNeutralityRules#isUndead} 대상이라는 사실은
 * 계약으로 남는다(반격 원한도 개체별로 같은 술어를 지난다).
 */
public final class CarrionStagRules {

    private CarrionStagRules() {
    }

    /** AABB 폭. 말(1.396)보다 좁고 소(0.9)보다 넓은 사슴 체형이다. */
    public static final double WIDTH = 0.9;
    /** AABB 높이. 뿔을 뺀 어깨높이 기준으로 소(1.4)보다 크다. */
    public static final double HEIGHT = 1.6;
    /** 눈높이. 목이 긴 종이라 높이의 0.9375 다(= 1.5). */
    public static final double EYE_HEIGHT = 1.5;
    /** 최대 체력. 대형 창작 희귀 티어라 좀비 소(14)보다 질기고 좀비곰(34)보다는 약하다. */
    public static final int MAX_HEALTH = 20;
    /** 방어도. 좀비 계열과 같은 2. */
    public static final double ARMOR = 2.0;
    /** 방어구 관통 저항. */
    public static final double TOUGHNESS = 0.0;
    /** 배회 속도(블록/틱). 큰 초식동물의 느린 걸음이다. */
    public static final double IDLE_BLOCKS_PER_TICK = 0.18;
    /**
     * 도주 속도(블록/틱) = 6.4블록/초. 플레이어 질주 5.6블록/초보다 <b>빠르다</b> —
     * 이 종은 쫓아가서 잡는 사냥감이 아니다.
     */
    public static final double FLEE_BLOCKS_PER_TICK = 0.64;
    /** 반격 추격 속도(블록/틱) = 5.4블록/초. 돌진 중에는 2배가 된다. */
    public static final double PURSUIT_BLOCKS_PER_TICK = 0.54;
    /**
     * 감지 반경(블록). 좀비 계열의 35 보다 <b>짧다</b> — 이 종은 붙는 종이 아니라 달아나는
     * 종이라 "달아나기 시작하는 거리"로 읽어야 한다.
     */
    public static final double DETECT_RANGE = 24.0;
    /** 공격 사거리(블록). 뿔이 길다. */
    public static final double ATTACK_RANGE = 2.0;
    /** 한 대 피해. 돌진 중에는 CHARGE_DAMAGE_BONUS 가 더 붙어 7 이 된다. */
    public static final int ATTACK_DAMAGE = 5;
    /** 공격 간격(10TPS 틱). 크게 한 번 받는 리듬이라 느리다. */
    public static final int ATTACK_COOLDOWN_TICKS = 16;
    /** 표적 하나당 허용되는 뿔 돌진 횟수. 다 쓰면 다시 달아난다. */
    public static final int CHARGE_LIMIT = 1;
    /** 썩은 살점 굴림 상한(배타). */
    public static final int ROTTEN_FLESH_ROLL = 3;
    /** 플레이어 처치 시에만 굴리는 창작 희귀 드랍(뼈 1). {@code nextInt(6) == 0} → 16.7%. */
    public static final int BONE_RARE_ROLL = 6;
    // 썩은 가죽은 공통 pool(대형 티어 — 0~2).
}
