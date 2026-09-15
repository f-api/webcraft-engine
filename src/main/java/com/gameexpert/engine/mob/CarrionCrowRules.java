package com.gameexpert.engine.mob;

/**
 * 시체 까마귀(WebCraft 창작몹, stableId 84)의 <b>상태 없는</b> 자체 계약.
 *
 * <p>근거 등급 C. 정적판 사본은 {@code StandaloneMobRules.ts} 의 {@code CARRION_CROW_*} 상수다.
 *
 * <p><b>정체성 — 이 종은 위협이 아니라 신호다.</b> 공격하지 않고, 도망치지도 않고, 오직
 * <b>다른 좀비 동물 무리 상공을 선회</b>한다. 밤에 멀리서 까마귀가 도는 게 보이면 그 아래에
 * 무리가 있다는 뜻이다 — 플레이어가 썩은 가죽을 모으려고 밤에 나설 때 "어디로 갈지"를 알려
 * 주는 유일한 장치이며, 그래서 스폰이 <b>독립 굴림이 아니라 무리 굴림에 부수</b>한다
 * ({@code MobSpawner#tryZombieHerdPack} 안에서 {@code nextInt(}{@value #HERD_ESCORT_ROLL}
 * {@code ) == 0} 이면 무리 위에 한 마리가 얹힌다).
 *
 * <p><b>선회</b>는 무리 중심을 축으로 반경 {@value #ORBIT_RADIUS} 블록,
 * 고도 {@value #ORBIT_ALTITUDE} 블록에서 틱당 {@value #ORBIT_RADIANS_PER_TICK} 라디안씩 도는
 * 결정적 궤도다 — 난수를 소비하지 않으므로 양 권위의 결정 트레이스가 갈리지 않는다.
 * 무리가 전멸하면 축을 잃고 제자리 배회로 떨어진다.
 *
 * <p><b>공격 계약</b>: {@link UndeadNeutralityRules#isUndead} 대상이지만 원래 <b>선공하지 않는</b>
 * 종이라 썩은 가죽 풀세트 무선공은 이 종에 대해 <b>눈에 보이는 차이를 만들지 않는다</b>
 * (좀비 말과 같은 이유로 계약에 명시한다). 공격력 상수 자체가 없다.
 */
public final class CarrionCrowRules {

    private CarrionCrowRules() {
    }

    /** AABB 폭. 앵무(0.5)와 같은 새 골격이다. */
    public static final double WIDTH = 0.5;
    /** AABB 높이. */
    public static final double HEIGHT = 0.9;
    /** 눈높이. 높이의 0.7778…이 아니라 앵무와 같은 비율로 잡은 0.7 이다. */
    public static final double EYE_HEIGHT = 0.7;
    /** 최대 체력. 박쥐(6)와 같다 — 신호용 종이라 싸움이 성립하면 안 된다. */
    public static final int MAX_HEALTH = 6;
    /** 방어도. 깃털뿐이라 없다. */
    public static final double ARMOR = 0.0;
    /** 방어구 관통 저항. */
    public static final double TOUGHNESS = 0.0;
    /** 비행 속도(블록/10TPS 틱). 앵무(0.4) 계열의 새다. */
    public static final double FLY_BLOCKS_PER_TICK = 0.30;
    /** 선회 반경(블록). 무리 중심에서 이만큼 떨어져 돈다. */
    public static final double ORBIT_RADIUS = 6.0;
    /** 선회 고도(블록). 무리 발밑 y 에서 이만큼 위다 — 멀리서도 보이는 높이다. */
    public static final double ORBIT_ALTITUDE = 12.0;
    /** 선회 각속도(라디안/10TPS 틱). 한 바퀴에 약 63틱(6.3초)이다. */
    public static final double ORBIT_RADIANS_PER_TICK = 0.10;
    /**
     * 무리 굴림에 까마귀가 얹힐 확률의 배타 상한.
     * {@code nextInt(3) == 0} → 성공한 무리 셋 중 하나에 한 마리다.
     */
    public static final int HERD_ESCORT_ROLL = 3;
    /** 썩은 살점 굴림 상한(배타). 마른 새라 0~1 이다. */
    public static final int ROTTEN_FLESH_ROLL = 2;
    /** 깃털 굴림 상한(배타). 바닐라 닭 깃털과 같은 형태의 0~1 이다. */
    public static final int FEATHER_ROLL = 2;
    // 썩은 가죽은 공통 pool(조류 티어 — 1/4 확률로 1장).
}
