package com.gameexpert.engine.mob;

/**
 * 낙타 husk(stableId 85)의 <b>상태 없는</b> 계약.
 *
 * <p><b>이 종은 바닐라 실존 종이다</b> — Java Edition 1.21.11 "Mounts of Mayhem" 의
 * {@code camel_husk}. 그래서 개체 수치의 근거 등급은 <b>A(바닐라 원문)</b> 이고, 발췌·출처는
 * {@code docs/research/mc-camel-husk-1-21-11.md} 가 소유한다. 정적판 사본은
 * {@code StandaloneMobRules.ts} 의 {@code CAMEL_HUSK_*} 상수다.
 *
 * <p><b>바닐라 그대로(등급 A)</b> — AABB {@value #WIDTH} × {@value #HEIGHT} 와 최대 체력
 * {@value #MAX_HEALTH}, 이동 속도 {@value #MOVEMENT_SPEED} 가 <b>살아 있는 낙타
 * ({@link CamelRules})와 글자 그대로 같다</b>. 썩은 살점은 바닐라 전리품 표의 2~3
 * ({@value #ROTTEN_FLESH_MIN} + {@code nextInt(}{@value #ROTTEN_FLESH_ROLL}{@code )}) 이며,
 * <b>주간에 타지 않는다</b>(위키 원문: "Unlike most undead mobs, camel husks do not burn in
 * sunlight"). 단독 개체는 <b>선공하지 않는다</b> — 바닐라에서도 적대성은 기수에게서 온다.
 *
 * <p><b>WebCraft divergence(등급 C)</b> — 바닐라는 husk 자연 스폰의 10%를 낙타 기수로
 * 승격시키지만 WebCraft 에는 아직 창·parched·기수 승격 계약이 없다. 그래서
 * <b>기수 없이 본체만</b> 등록하고, 스폰은 {@code MobSpawner#tryCamelHuskEncounter} 의
 * <b>독립 굴림</b>(사막·밤)으로 세운다. 선공하지 않으므로 카테고리도 좀비 말과 같은
 * {@link MobCategory#CREATURE} 다. 탑승·대시는 이 트랙 밖이다(모델 트랙과 함께 다음 단계).
 */
public final class CamelHuskRules {

    private CamelHuskRules() {
    }

    /** 바닐라 히트박스 폭 1.7. 살아 있는 낙타와 같다. */
    public static final double WIDTH = 1.7;
    /** 바닐라 히트박스 높이 2.375. */
    public static final double HEIGHT = 2.375;
    /** 눈높이. 살아 있는 낙타({@code CamelRules.EYE_HEIGHT})와 같다. */
    public static final double EYE_HEIGHT = 2.275;
    /** 바닐라 MAX_HEALTH 32. */
    public static final int MAX_HEALTH = 32;
    /** 방어도. 바닐라 attribute 가 없어 0 이다(낙타와 같다). */
    public static final double ARMOR = 0.0;
    /** 방어구 관통 저항. */
    public static final double TOUGHNESS = 0.0;
    /** 바닐라 MOVEMENT_SPEED 0.09. */
    public static final double MOVEMENT_SPEED = 0.09;
    /** 썩은 살점 최소 개수. 바닐라 전리품 표의 2~3 중 2. */
    public static final int ROTTEN_FLESH_MIN = 2;
    /** 썩은 살점 굴림 상한(배타). {@code 2 + nextInt(2)} → 2~3. */
    public static final int ROTTEN_FLESH_ROLL = 2;
    // 썩은 가죽은 바닐라에 없는 WebCraft 경제라 공통 pool 이 붙인다(대형 티어 — 0~2).
}
