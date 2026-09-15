package com.gameexpert.engine;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 월드 난이도(생성 시 고정, 이후 변경 없음). 바닐라 {@code Difficulty} 의 EASY/NORMAL/HARD 만 도입하고
 * PEACEFUL 은 도입하지 않는다(적대 몹 스폰 제거는 범위 밖).
 *
 * <p>지역 난이도(local difficulty, 월드 경과 시간·청크 체류·달 위상으로 오르는 배율)는 <b>범위 제외</b>다.
 * 아래 값은 전부 전역 난이도만 보는 고정 표이며, 바닐라의 지역 난이도 의존 항목(장비 착용 확률 등)은
 * 기존 매핑을 그대로 둔다.
 *
 * <p>양판(Java·정적판)이 같은 표를 쓰므로 값을 바꿀 때 정적판 {@code StandaloneDifficulty.ts} 도
 * 함께 갱신해야 한다.
 */
public enum Difficulty {
    EASY("easy", 1),
    NORMAL("normal", 2),
    HARD("hard", 3);

    /** 저장·프로토콜에 쓰는 소문자 키. */
    private final String key;
    /** 바닐라 {@code Difficulty#getId} 와 같은 번호. 스켈레톤 부정확도 공식이 직접 쓴다. */
    private final int id;

    Difficulty(String key, int id) {
        this.key = key;
        this.id = id;
    }

    @JsonValue
    public String key() {
        return key;
    }

    public int id() {
        return id;
    }

    /** 난이도 미지정(구형 월드·구형 요청)의 기본값. */
    public static final Difficulty DEFAULT = NORMAL;

    /**
     * 저장 값·요청 값 해석. null/빈 값은 {@link #DEFAULT}(normal)로 보아 난이도 도입 전에 만든 월드도
     * 그대로 열 수 있게 한다. 알 수 없는 문자열은 조용히 기본값으로 바꾸지 않고 거절한다.
     */
    @JsonCreator
    public static Difficulty fromKey(String key) {
        if (key == null || key.isBlank()) return DEFAULT;
        for (Difficulty difficulty : values()) {
            if (difficulty.key.equalsIgnoreCase(key)) return difficulty;
        }
        throw new IllegalArgumentException("unknown difficulty: " + key);
    }

    /** 저장된 값이 null 이면 기본값. 구형 행(row) 호환 전용. */
    public static Difficulty orDefault(Difficulty difficulty) {
        return difficulty == null ? DEFAULT : difficulty;
    }

    /**
     * 몹 접촉 피해 배율(바닐라 {@code Player#hurt}: easy {@code min(d/2+1, d)}, normal 그대로,
     * hard {@code d*3/2}). 피해 파이프라인이 정수라 배율 결과는 내림하고, 원래 피해가 있으면 최소 1을
     * 남긴다(0으로 무력화되지 않게).
     *
     * <p>크리퍼 폭발 피해는 바닐라와 같이 난이도 배율을 적용하지 않는다(폭발은 자체 감쇠 공식).
     */
    public int scaleContactDamage(int damage) {
        if (damage <= 0) return damage;
        double scaled = switch (this) {
            case EASY -> Math.min(damage / 2.0 + 1.0, damage);
            case NORMAL -> damage;
            case HARD -> damage * 3.0 / 2.0;
        };
        return Math.max(1, (int) Math.floor(scaled));
    }

    /**
     * 동굴거미 근접 독 지속(10 TPS 서버 틱). 바닐라 {@code CaveSpider#doHurtTarget}:
     * easy 없음 / normal 7초 / hard 15초.
     */
    public int caveSpiderPoisonTicks() {
        return switch (this) {
            case EASY -> 0;
            case NORMAL -> 70;
            case HARD -> 150;
        };
    }

    /**
     * 스트레이 화살 감속 지속(10 TPS 서버 틱). 바닐라는 난이도와 무관한 30초 고정이지만,
     * PRD Track DIFF 가 팁 화살 지속의 난이도 분기를 요구하므로 normal 을 바닐라 값(30초)에 두고
     * easy 절반·hard 2배로 넓힌 명시적 divergence 다.
     */
    public int strayArrowSlownessTicks() {
        return switch (this) {
            case EASY -> 150;
            case NORMAL -> 300;
            case HARD -> 600;
        };
    }

    /**
     * 보그드 화살 독 지속(10 TPS 서버 틱). 스트레이와 같은 근거로 바닐라 5초를 normal 에 두고
     * easy 절반·hard 2배로 분기한다.
     */
    public int boggedArrowPoisonTicks() {
        return switch (this) {
            case EASY -> 25;
            case NORMAL -> 50;
            case HARD -> 100;
        };
    }

    /**
     * [PARCHED-FAMILY] 파치드 나약함 화살 지속(10 TPS 서버 틱). 원문은 난이도와 무관한
     * "Weakness for 30 seconds" 고정이지만, 스트레이·보그드가 이미 세운 규약대로 normal 을
     * 바닐라 값(30초 = 300틱)에 두고 easy 절반·hard 2배로 넓힌 명시적 divergence 다.
     */
    public int parchedWeaknessArrowTicks() {
        return switch (this) {
            case EASY -> 150;
            case NORMAL -> 300;
            case HARD -> 600;
        };
    }

    /**
     * [PARCHED-FAMILY] 파치드 사격 주기(10 TPS 서버 틱). 여기는 divergence 가 아니라
     * <b>원문 그대로</b>다 — "every 3.5 seconds on Easy and Normal and every 2.5 seconds on
     * Hard" 이므로 easy/normal 35틱 · hard 25틱이다. 스켈레톤의 20틱(2초)보다 느리다.
     */
    public int parchedShootIntervalTicks() {
        return this == HARD
                ? com.gameexpert.engine.mob.ParchedRules.SHOOT_INTERVAL_TICKS_HARD
                : com.gameexpert.engine.mob.ParchedRules.SHOOT_INTERVAL_TICKS;
    }

    /**
     * 스켈레톤 화살 부정확도. 바닐라 {@code AbstractSkeleton#performRangedAttack} 의
     * {@code 14 - difficulty.getId() * 4} = easy 10 / normal 6 / hard 2.
     */
    public double skeletonArrowInaccuracy() {
        return 14 - id * 4.0;
    }

    /**
     * [PHANTOM] 팬텀 불면 굴림이 쓰는 유효 난이도. 바닐라 {@code PhantomSpawner} 는
     * {@code DifficultyInstance.isHarderThan(rng.nextFloat() * 3.0F)} 로 <b>지역</b> 난이도를 보는데,
     * 이 저장소는 지역 난이도를 도입하지 않는다(이 클래스 상단 javadoc).
     *
     * <p>바닐라 {@code DifficultyInstance.calculateDifficulty} 는 월드 경과 0 · 청크 체류 0 ·
     * 초승달에서 {@code id * 0.75F} 로 수렴하므로 그 <b>하한</b>을 고정 유효 난이도로 쓴다
     * (스폰이 덜 나오는 방향이라 안전한 divergence 다 — docs/research/mc-phantom-insomnia.md §1.1).
     * 통과 확률은 {@code effective / 3} = easy 0.25 / normal 0.50 / hard 0.75.
     */
    public float phantomEffectiveDifficulty() {
        return id * 0.75f;
    }

    /**
     * [PHANTOM] 무리 크기 굴림의 상한. 바닐라 {@code 1 + rng.nextInt(difficulty.getId() + 1)} 의
     * {@code nextInt} 인자로, easy 1~2 · normal 1~3 · hard 1~4 가 된다.
     */
    public int phantomGroupSizeBound() {
        return id + 1;
    }

    /** 좀비의 나무 문 부수기는 바닐라와 같이 hard 에서만 완료된다. */
    public boolean zombiesBreakDoors() {
        return this == HARD;
    }

    /**
     * 굶주림 피해가 멈추는 체력 하한(20점 기준). 바닐라 {@code FoodData#tick}:
     * easy 10점 초과, normal 1점 초과에서만 피해를 주고 hard 는 하한 없이 사망까지 간다.
     *
     * <p>이 값 자체를 소비하는 굶주림 구현은 Track SURV-H 소유다. Track DIFF 는 조회 API만 노출한다.
     */
    public int starvationMinHealth() {
        return switch (this) {
            case EASY -> 10;
            case NORMAL -> 1;
            case HARD -> 0;
        };
    }
}
