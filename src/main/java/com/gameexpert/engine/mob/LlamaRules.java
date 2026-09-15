package com.gameexpert.engine.mob;

import com.gameexpert.terrain.Blocks;

/**
 * 라마의 <b>상태 없는</b> 바닐라 규칙. 근거는 Minecraft Java 1.21.4 의 {@code Llama},
 * {@code LlamaSpit}, {@code LlamaFollowCaravanGoal} 이며 각 상수 옆에 원문 식을 적어 둔다.
 *
 * <p>라마는 말 계열이지만 {@link HorseRules} 와 다른 세 가지를 갖는다:
 * <ol>
 *   <li>max temper 가 100 이 아니라 30 이다({@code Llama#getMaxTemper}).</li>
 *   <li>안장이 없고 {@code getControllingPassenger()} 가 언제나 null 이라 <b>타지만 조종할 수
 *       없다</b>. 좌석 계약은 {@link MobMountRules#controllable} 로 이 사실을 표현한다.</li>
 *   <li>힘 스탯(1~5)이 화물 열 수이고, 카펫 장식·침 뱉기·캐러밴이 라마 전용이다.</li>
 * </ol>
 *
 * <p>정적판 사본은 {@code client/src/backend/standalone/StandaloneLlamaRules.ts} 이고, 두 사본의
 * 상수·판정 동일성은 {@code StandaloneLlamaRules.test.ts} 가 이 파일 원문을 읽어 강제한다.
 */
public final class LlamaRules {

    private LlamaRules() {
    }

    // ── 길들이기 ───────────────────────────────────────────────────────
    /**
     * 바닐라 {@code Llama#getMaxTemper()} = 30. 말 계열 공통 판정
     * ({@code random.nextInt(maxTemper) < temper})은 {@link HorseRules#tamesOnAttempt} 그대로이고,
     * 상한만 이 값으로 바뀐다 — 즉 라마는 말보다 훨씬 빨리 길들여진다.
     */
    public static final int MAX_TEMPER = 30;

    /** temper 는 {@code AbstractHorse#modifyTemper} 에서 [0, 30] 으로 클램프된다. */
    public static int clampTemper(int temper) {
        return temper < 0 ? 0 : Math.min(temper, MAX_TEMPER);
    }

    // ── 힘 스탯({@code Llama#setRandomStrength}) ─────────────────────────
    /**
     * 바닐라 {@code Llama#setRandomStrength(RandomSource)}:
     * <pre>int i = random.nextFloat() &lt; 0.04F ? 5 : 3; this.setStrength(1 + random.nextInt(i));</pre>
     * 즉 4% 확률로 [1,5], 나머지 96% 는 [1,3] 이다.
     */
    public static final float STRENGTH_HIGH_ROLL_CHANCE = 0.04F;
    /** 위 식의 {@code i} 두 갈래. */
    public static final int STRENGTH_HIGH_BOUND = 5;
    public static final int STRENGTH_LOW_BOUND = 3;
    /** 바닐라 {@code Llama.MAX_STRENGTH} = 5, 하한은 식의 고정항 1 이다. */
    public static final int MIN_STRENGTH = 1;
    public static final int MAX_STRENGTH = 5;

    /** 힘은 [1,5] 로 클램프된다. 영속 복구가 범위 밖 값을 들고 와도 계약이 깨지지 않는다. */
    public static int clampStrength(int strength) {
        if (strength < MIN_STRENGTH) return MIN_STRENGTH;
        return Math.min(strength, MAX_STRENGTH);
    }

    /** 힘 값이 바닐라 구간 안인가. */
    public static boolean isValidStrength(int strength) {
        return strength >= MIN_STRENGTH && strength <= MAX_STRENGTH;
    }

    /**
     * 바닐라 {@code setRandomStrength} 그대로. 난수 소비 순서(먼저 float, 그다음 int)까지 같아야
     * 두 권위가 같은 개체를 낸다.
     */
    public static int rollStrength(MobRandom rng) {
        int bound = rng.nextFloat() < STRENGTH_HIGH_ROLL_CHANCE
                ? STRENGTH_HIGH_BOUND : STRENGTH_LOW_BOUND;
        return MIN_STRENGTH + rng.nextInt(bound);
    }

    /**
     * 힘 스탯 전용 시드 솔트. {@link HorseRules#STAT_SEED_SALT} 와 같은 이유로 존재하며
     * 두 권위가 <b>같은 값</b>을 쓴다는 사실만이 계약이다.
     */
    public static final long STRENGTH_SEED_SALT = 0x4c6c616d61537431L; // "LlamaSt1"

    /**
     * 몹 ID 를 시드 공간 전체로 흩는 홀수 상수(황금비 64비트). 값 자체에 게임 의미는 없다.
     *
     * <p><b>왜 필요한가.</b> {@code new Random(salt ^ mobId)} 처럼 낮은 비트만 다른 시드를 주면
     * {@code java.util.Random} 의 <b>첫 draw</b> 가 시드와 강하게 상관된다. 실제로 솔트 XOR 만
     * 쓰면 mobId 1~2000 에서 첫 {@code nextFloat()} 가 0.2865 아래로 내려오지 않아
     * {@code nextFloat() < 0.04F} 갈래가 <b>한 번도</b> 서지 않는다 — 힘 4·5 인 라마가 초기
     * 월드에서 구조적으로 존재할 수 없게 된다. ID 를 곱해 흩으면 20,000 개체에서 4·5 비율이
     * 1.63% 로, 바닐라 기대값(4% × 2/5 = 1.6%)과 맞는다.
     */
    public static final long STRENGTH_SEED_MIX = 0x9e3779b97f4a7c15L;

    /** 힘 스탯 전용 결정론 RNG. 같은 몹 ID 는 언제나 같은 힘을 낸다. */
    public static MobRandom strengthRandom(long mobId) {
        return new JavaMobRandom(STRENGTH_SEED_SALT ^ mobId * STRENGTH_SEED_MIX);
    }

    // ── 카펫 장식({@code Llama#isBodyArmorItem}) ─────────────────────────
    /** 장식 없음을 뜻하는 색 인덱스. 바닐라의 빈 body-armor 슬롯이다. */
    public static final int NO_CARPET = -1;

    /**
     * 바닐라 {@code Llama#isBodyArmorItem(ItemStack)} = {@code stack.is(ItemTags.WOOL_CARPETS)}.
     * WebCraft 의 16색 카펫은 {@link Blocks#CARPET_BY_DYE_COLOR} 한 표에서만 온다 —
     * 이끼 카펫({@code MOSS_CARPET})은 {@code #wool_carpets} 태그에 없어 장식이 되지 않는다.
     *
     * @return 색 인덱스(0~15, MC {@code DyeColor} 네트워크 ID) 또는 {@link #NO_CARPET}
     */
    public static int carpetColorIndex(short itemType) {
        for (int color = 0; color < Blocks.CARPET_BY_DYE_COLOR.length; color++) {
            if (itemType == (short) Blocks.CARPET_BY_DYE_COLOR[color]) return color;
        }
        return NO_CARPET;
    }

    /** 이 아이템이 라마 장식 카펫인가. */
    public static boolean isCarpet(short itemType) {
        return carpetColorIndex(itemType) != NO_CARPET;
    }

    /** 색 인덱스를 다시 카펫 블록 ID 로 되돌린다(사망 드랍이 쓴다). */
    public static short carpetItem(int colorIndex) {
        if (colorIndex < 0 || colorIndex >= Blocks.CARPET_BY_DYE_COLOR.length) {
            throw new IllegalArgumentException("invalid carpet color " + colorIndex);
        }
        return (short) Blocks.CARPET_BY_DYE_COLOR[colorIndex];
    }

    /** 영속·프로토콜이 들고 온 색 인덱스가 계약 안인가(장식 없음 포함). */
    public static boolean isValidCarpetColor(int colorIndex) {
        return colorIndex == NO_CARPET
                || colorIndex >= 0 && colorIndex < Blocks.CARPET_BY_DYE_COLOR.length;
    }

    // ── 침 뱉기({@code LlamaSpit}) ───────────────────────────────────────
    /**
     * 바닐라 {@code LlamaSpit#onHitEntity}:
     * {@code entity.hurt(damageSources().mobProjectile(this, owner), 1.0F)}. 피해 1 고정이며
     * 방어구·거리로 감쇠하지 않는다.
     */
    public static final int SPIT_DAMAGE = 1;

    /**
     * 바닐라 {@code Llama#spit(LivingEntity)} 의 {@code llamaspit.shoot(dx, dy + d3, dz, 1.5F, 10.0F)}
     * — 발사 속도 1.5, 부정확도 10.0 이고 조준 보정 {@code d3 = sqrt(dx² + dz²) * 0.2} 다.
     */
    public static final double SPIT_LAUNCH_SPEED = 1.5;
    public static final double SPIT_INACCURACY = 10.0;
    public static final double SPIT_AIM_ELEVATION_FACTOR = 0.2;
    /** 같은 식의 조준 높이 비율: {@code target.getY(0.3333333333333333)}. */
    public static final double SPIT_AIM_TARGET_HEIGHT_FRACTION = 1.0 / 3.0;

    /**
     * 바닐라 {@code Llama#spit} 의 조준 벡터(정규화 전). 두 권위가 같은 발사각을 내도록 한 곳에만
     * 둔다. 부정확도 적용은 호출부(투사체 계약)가 자기 RNG 로 한다.
     *
     * @return {@code {dx, dy, dz}} — 라마 눈 위치에서 대상 조준점까지의 보정된 방향
     */
    public static double[] spitAim(double llamaX, double llamaY, double llamaZ,
            double targetX, double targetFeetY, double targetHeight, double targetZ) {
        double dx = targetX - llamaX;
        double dz = targetZ - llamaZ;
        double aimY = targetFeetY + targetHeight * SPIT_AIM_TARGET_HEIGHT_FRACTION;
        double elevation = Math.sqrt(dx * dx + dz * dz) * SPIT_AIM_ELEVATION_FACTOR;
        return new double[] { dx, aimY - llamaY + elevation, dz };
    }

    /**
     * 바닐라 {@code Llama#registerGoals} 의 {@code new RangedAttackGoal(this, 1.25, 40, 20.0F)}:
     * 발사 주기 40 MC 틱(= attackIntervalMin, 라마는 min/max 가 같다)과 사거리 20 블록이다.
     * 권위는 10 TPS 라 {@link #SPIT_INTERVAL_AUTHORITY_TICKS} 로 환산해 쓴다.
     */
    public static final int SPIT_INTERVAL_MC_TICKS = 40;
    /** 권위 한 틱(10 TPS)이 굴리는 바닐라 틱 수. {@code ZombifiedPiglin} 이 쓰는 값과 같다. */
    public static final int MC_TICKS_PER_AUTHORITY_TICK = 2;
    /** 바닐라 20 TPS 주기를 권위 10 TPS 로 환산한 값. 두 권위가 같은 주기를 쓴다. */
    public static final int SPIT_INTERVAL_AUTHORITY_TICKS =
            SPIT_INTERVAL_MC_TICKS / MC_TICKS_PER_AUTHORITY_TICK;
    /** 같은 goal 의 {@code attackRadius} = 20.0F. 이 거리 밖에서는 침을 뱉지 않는다. */
    public static final double SPIT_ATTACK_RADIUS = 20.0;

    /**
     * 바닐라 {@code Mob.createMobAttributes()} 의 기본 {@code FOLLOW_RANGE} = 16.0.
     * {@code AbstractHorse} 는 이 값을 덮지 않으므로 라마의 표적 유지 거리가 그대로 16 이다
     * ({@code TargetGoal#canContinueToUse} 가 이 거리를 본다). 사거리(20)보다 짧아서, 표적이
     * 16 블록 밖으로 나가면 사거리 안이어도 추적 자체가 끊긴다.
     */
    public static final double TARGET_FOLLOW_RANGE = 16.0;

    /**
     * 바닐라 {@code Projectile#shoot(x, y, z, velocity, inaccuracy)} 의 발사 벡터:
     * 조준 벡터를 정규화하고 오차를 더한 뒤 발사 속도를 곱한다. 오차 표본
     * ({@code inaccuracy × 0.0172275} 스케일이 이미 곱해진 값)은 호출부 RNG 가 준다 —
     * 그래야 이 함수 자체는 두 권위에서 한 글자도 다르지 않은 순수 함수로 남는다.
     *
     * @return {@code {vx, vy, vz}} 블록/틱, 조준 벡터가 영벡터면 null
     */
    public static double[] spitVelocity(double[] aim,
            double noiseX, double noiseY, double noiseZ) {
        double length = Math.sqrt(aim[0] * aim[0] + aim[1] * aim[1] + aim[2] * aim[2]);
        if (!(length > 1e-9)) return null;
        return new double[] {
            (aim[0] / length + noiseX) * SPIT_LAUNCH_SPEED,
            (aim[1] / length + noiseY) * SPIT_LAUNCH_SPEED,
            (aim[2] / length + noiseZ) * SPIT_LAUNCH_SPEED,
        };
    }

    // ── 캐러밴({@code LlamaFollowCaravanGoal}) ───────────────────────────
    /**
     * 바닐라 {@code LlamaFollowCaravanGoal#canUse()} 가 이웃 라마를 찾는 AABB 팽창값:
     * {@code this.llama.getBoundingBox().inflate(9.0, 4.0, 9.0)}.
     */
    public static final double CARAVAN_SEARCH_HORIZONTAL = 9.0;
    public static final double CARAVAN_SEARCH_VERTICAL = 4.0;

    /**
     * 같은 goal 의 재귀 게이트 {@code firstIsLeashed(llama, leashedQueueLength)}: 줄 앞이 8 마리를
     * 넘으면 합류하지 않는다. 즉 캐러밴 최대 길이는 리더 뒤로 8 이다.
     */
    public static final int MAX_CARAVAN_LENGTH = 8;

    /**
     * 캐러밴이 성립하려면 <b>줄의 맨 앞이 리드로 묶여 있어야</b> 한다
     * ({@code firstIsLeashed} 가 리더까지 거슬러 올라가 {@code isLeashed()} 를 요구한다).
     * WebCraft 에는 리드 아이템이 없으므로 이 조건이 절대 참이 되지 않는다 — 이 메서드는
     * 언제나 false 를 돌려주고, 그 사실 자체가 아래 divergence 1 의 실행 가능한 증거다.
     *
     * @param leaderLeashed 줄 맨 앞 라마가 리드로 묶여 있는가
     */
    public static boolean caravanJoinable(boolean leaderLeashed, int leashedQueueLength,
            double horizontalDistance, double verticalDistance) {
        return leaderLeashed && leashedQueueLength <= MAX_CARAVAN_LENGTH
                && horizontalDistance <= CARAVAN_SEARCH_HORIZONTAL
                && verticalDistance <= CARAVAN_SEARCH_VERTICAL;
    }

    // ── WebCraft divergence ────────────────────────────────────────────
    // 1. [캐러밴 미도달] 바닐라 캐러밴은 `LlamaFollowCaravanGoal` 이 줄의 맨 앞까지 거슬러 올라가
    //    `isLeashed()` 를 요구한다. WebCraft 에는 리드(lead) 아이템·엔티티 묶기 계약 자체가 없어
    //    이 조건이 성립할 수 없다. 규칙(탐색 범위·최대 길이·합류 판정)은 여기에 바닐라 근거와 함께
    //    두되 실제 caravan 상태는 만들지 않는다. 리드 트랙이 들어오면 `caravanJoinable` 의
    //    leaderLeashed 인자에 실제 값을 물리는 것만으로 켜진다.
    // 2. 바닐라 라마 변종은 `Llama.Variant` 4종(CREAMY·WHITE·BROWN·GRAY)이고 자연 스폰은 무리
    //    첫 개체의 변종을 `LlamaGroupData` 로 무리 전체에 복사한다. WebCraft 는 다른 종과 같은
    //    좌표 해시 변종 계약을 쓰므로 한 무리가 섞인 색을 가질 수 있다.
    // 3. 바닐라 침은 `LlamaSpit` 전용 엔티티다. WebCraft 는 기존 투사체 계약의 한 종류로 싣고
    //    피해 1·발사 속도 1.5 만 바닐라와 맞춘다(중력·항력은 투사체 정본이 소유한다).
}
