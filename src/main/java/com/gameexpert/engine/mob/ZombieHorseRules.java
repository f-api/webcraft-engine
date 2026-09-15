package com.gameexpert.engine.mob;

/**
 * 좀비 말(stableId 74)의 <b>상태 없는</b> 규칙.
 *
 * <p>Minecraft Java 26.3-snapshot-7 원문의 좀비 말 수치와 개체 롤을 소유한다.
 * 자연 스폰은 평원 계열 MONSTER 바이옴 표가 소유하며, 자연 finalizeSpawn 은
 * 철 창을 든 좀비 기수를 생성한다.
 *
 * <p>정적판 사본은 {@code client/src/backend/standalone/StandaloneMobRules.ts} 의
 * {@code ZOMBIE_HORSE_*} 상수이고, 두 사본의 동일성은
 * {@code StandaloneMobRules.species.test.ts} 가 이 파일 원문을 읽어 강제한다.
 *
 * <p><b>바닐라 원문 대응.</b>
 * <ul>
 *   <li>{@code EntityType.ZOMBIE_HORSE = register(... EntityType.Builder.of(ZombieHorse::new,
 *       MobCategory.MONSTER).sized(1.3964844F, 1.6F) ...)} → {@link #WIDTH}·{@link #HEIGHT}.
 *       말과 <b>글자 그대로 같은</b> AABB 다.</li>
 *   <li>{@code ZombieHorse#createAttributes()} 의 HP 25와 {@code randomizeAttributes}의
 *       점프 3회 → 속도 3회 난수 소비를 그대로 따른다.</li>
 *   <li>{@code AbstractHorse#createBaseHorseAttributes()} 의
 *       {@code .add(Attributes.JUMP_STRENGTH, 0.7)} → {@link #JUMP_STRENGTH}.</li>
 *   <li>{@code entities/zombie_horse.json}: {@code minecraft:rotten_flesh} uniform 0~2 하나뿐 →
 *       {@link #ROTTEN_FLESH_ROLL}.</li>
 * </ul>
 *
 * <p><b>WebCraft 자체 계약 1 — 언데드 말은 길들이기·temper 를 굴리지 않는다.</b>
 * 말 계열 상태 기계({@code AbstractHorseMob})는 그대로 재사용하되 개체는 <b>태어날 때부터
 * tamed</b> 다. 바닐라 {@code RunAroundLikeCrazyGoal} 의 낙마·temper 누적은 "야생마를 길들이는
 * 과정"의 표현인데, 이미 죽은 말에게 그 과정을 붙일 근거가 없기 때문이다. 결과적으로
 * <b>낙마가 없고</b>({@code buckPending} 이 서지 않는다) temper 는 언제나 상한에 굳어 있다.
 * 그 대신 <b>안장 게이트는 말과 글자 그대로 같다</b> — 안장이 없으면
 * {@code MobMountRules.steerable} 이 거짓이라 탈 수는 있어도 조종되지 않는다.
 *
 * <p><b>WebCraft 자체 계약 2 — 먹지 않는다.</b> 언데드라 먹이 표
 * ({@code HorseRules#feedTemperGain})를 통과시키지 않는다. 먹이 상호작용은 실패하고 아이템도
 * 소비되지 않는다. 번식도 없다({@code MobType.BREEDABLE} 밖).
 *
 * <p><b>WebCraft 자체 계약 3 — 주간 소각은 하지 않는다.</b> 좀비곰과 같은 이유다
 * ({@code Mob#tickFireEnvironment} 의 {@code burnsInSun} 명단 밖). 희소 조우로 얻은 탈것이
 * 첫 아침에 재가 되면 조우가 사건이 되지 못한다.
 *
 * <p><b>WebCraft 자체 계약 4 — 언데드 판정.</b> {@link RottenLeatherDropRules#rottenLeatherDrop}
 * (썩은 가죽 드랍) · {@link UndeadNeutralityRules#isUndead}(언데드 선공 억제) 두 술어의 대상이다.
 * 이 종은 중립이라 애초에 선공하지 않으므로 후자는 계약 명시로만 의미가 있다.
 *
 * <p><b>스폰 계약</b>은 {@link MobSpawner}의 26.3 바이옴 MONSTER 표가 소유한다.
 */
public final class ZombieHorseRules {

    private ZombieHorseRules() {
    }

    // ── 개체 상수(바닐라 원문) ────────────────────────────────────────
    /** {@code sized(1.3964844F, 1.6F)} 의 폭. 살아 있는 말과 같다. */
    public static final double WIDTH = 1.3964844;
    /** 같은 식의 높이. */
    public static final double HEIGHT = 1.6;
    /**
     * 눈높이. 바닐라는 {@code getEyeHeight} 를 재정의하지 않아 기본 {@code height * 0.85} =
     * 1.36 이지만, WebCraft 말 계열은 {@code Mob#eyeHeight} 에 말 1.52 를 못박아 두었고 이 종은
     * 같은 골격이라 <b>같은 값</b>을 쓴다(정적판도 같다).
     */
    public static final double EYE_HEIGHT = 1.52;
    /** 26.3 {@code ZombieHorse#createAttributes} 의 {@code MAX_HEALTH 25.0}. */
    public static final double MAX_HEALTH = 25.0;
    /** 롤 전 {@code createBaseHorseAttributes} 의 기본 이동 속도. */
    public static final double MOVEMENT_SPEED = 0.22499999403953552;
    /** 롤 전 {@code createBaseHorseAttributes} 의 기본 점프 강도. */
    public static final double JUMP_STRENGTH = 0.7;
    public static final double RANDOM_JUMP_BASE = 0.5;
    public static final double RANDOM_JUMP_SPAN = 0.06666666666666667;
    public static final double RANDOM_SPEED_BASE = 9.0;
    public static final double RANDOM_SPEED_DIVISOR = 42.15999984741211;
    /** 방어도. 바닐라에 attribute 가 없어 0 이다(말과 같다). */
    public static final double ARMOR = 0.0;
    /** 방어구 관통 저항. 같은 이유로 0 이다. */
    public static final double TOUGHNESS = 0.0;

    // ── 드랍 ─────────────────────────────────────────────────────────
    /**
     * 썩은 살점 굴림 상한(배타). 바닐라 {@code entities/zombie_horse.json} 의
     * {@code uniform 0~2} → {@code nextInt(3)} 이다.
     */
    public static final int ROTTEN_FLESH_ROLL = 3;
    // 썩은 가죽은 이 종의 표가 아니라 {@code RottenLeatherDropRules} 공통 pool 이 붙인다
    // (썩은 가죽 트랙). 이 종은 그 술어의 대상이라는 사실만 계약으로 갖는다.

    /** 26.3 소비 순서: 점프 3회 다음 속도 3회. 체력은 고정 25다. */
    public static HorseRules.Stats rollStats(MobRandom rng) {
        double jump = RANDOM_JUMP_BASE
                + rng.nextDouble() * RANDOM_JUMP_SPAN
                + rng.nextDouble() * RANDOM_JUMP_SPAN
                + rng.nextDouble() * RANDOM_JUMP_SPAN;
        double speed = (RANDOM_SPEED_BASE
                + rng.nextDouble() + rng.nextDouble() + rng.nextDouble())
                / RANDOM_SPEED_DIVISOR;
        return new HorseRules.Stats(MAX_HEALTH, speed, jump);
    }
}
