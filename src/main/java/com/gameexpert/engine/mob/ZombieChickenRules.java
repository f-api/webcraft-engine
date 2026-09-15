package com.gameexpert.engine.mob;

/**
 * 좀비 닭(WebCraft 창작몹, stableId 81)의 <b>상태 없는</b> 자체 계약.
 *
 * <p>근거 등급 C. 공유 계약은 {@link UndeadAnimalRules} 가 소유한다. 정적판 사본은
 * {@code StandaloneMobRules.ts} 의 {@code ZOMBIE_CHICKEN_*} 상수다.
 *
 * <p><b>상한 달걀(썩은 달걀).</b> 이 종의 유일한 특수 계약이며 아이템은
 * {@code Blocks.SPOILED_EGG}(1410) 다. 세 가지가 함께 성립해야 계약이 닫힌다:
 * <ol>
 *   <li><b>사망 드랍</b> — {@code nextInt(}{@value #SPOILED_EGG_ROLL}{@code ) == 0} 이면 1개,
 *       아니면 0개다(= 1/4 확률로 0~1). 살아 있는 닭의 주기적 산란({@code Chicken#tick})은
 *       <b>없다</b> — 부패한 닭은 알을 품지 않는다.</li>
 *   <li><b>투척 가능·부화 확률 0</b> — 던지는 경로는 살아 있는 달걀의 {@code eggThrow} 를
 *       그대로 재사용하되 부화 굴림만 통과시키지 않는다
 *       ({@code FarmAnimalRules#eggHatches} 가 이 아이템에 대해 항상 거짓).</li>
 *   <li><b>제련 불가</b> — 바닐라 달걀이 제련 대상이 아닌 것과 같은 파리티다(양표 미등록).</li>
 * </ol>
 * <b>섭취</b>는 썩은 살점과 <b>같은 정본 상수</b>를 참조한다(사본 금지) — 영양 4 · saturation
 * 0.8 · 허기 효과까지 {@code PlayerInventory.ROTTEN_FLESH} 의 등록값 그 자체를 읽는다.
 *
 * <p>좀비 보정: 체력 4 + 4 = {@value #MAX_HEALTH}, 배회 0.25 × 0.8 =
 * {@value #IDLE_BLOCKS_PER_TICK}. 날개가 썩어 <b>활공하지 않는다</b>(살아 있는 닭의 낙하
 * 감속 계약 밖 — 지상 이동만 한다).
 */
public final class ZombieChickenRules {

    private ZombieChickenRules() {
    }

    /** 닭과 같은 AABB 폭. */
    public static final double WIDTH = 0.4;
    /** 닭과 같은 AABB 높이. */
    public static final double HEIGHT = 0.7;
    /** 닭과 같은 눈높이. */
    public static final double EYE_HEIGHT = 0.644;
    /** 최대 체력. 닭 4 + 4. 좀비 동물 통틀어 가장 약하다. */
    public static final int MAX_HEALTH = 8;
    /** 방어도. */
    public static final double ARMOR = 2.0;
    /** 방어구 관통 저항. */
    public static final double TOUGHNESS = 0.0;
    /** 배회 속도(블록/틱). 닭 0.25 × 0.8. */
    public static final double IDLE_BLOCKS_PER_TICK = 0.20;
    /** 추격 속도(블록/틱) = 3.6블록/초. 좀비 동물 중 가장 느린 추격이다. */
    public static final double PURSUIT_BLOCKS_PER_TICK = 0.36;
    /** 추적 감지 반경(블록). */
    public static final double DETECT_RANGE = 35.0;
    /** 공격 사거리(블록). 가장 작은 체구다. */
    public static final double ATTACK_RANGE = 1.2;
    /** 한 대 피해. 쪼는 수준이다. */
    public static final int ATTACK_DAMAGE = 2;
    /** 공격 간격(10TPS 틱). */
    public static final int ATTACK_COOLDOWN_TICKS = 12;
    /** 썩은 살점 굴림 상한(배타). 작은 새라 좀비 계열(3)보다 인색한 0~1 이다. */
    public static final int ROTTEN_FLESH_ROLL = 2;
    /** 상한 달걀 굴림 상한(배타). {@code nextInt(4) == 0} → 1/4 확률로 1개. */
    public static final int SPOILED_EGG_ROLL = 4;
    // 썩은 가죽은 공통 pool(조류 티어 — 1/4 확률로 1장).
}
