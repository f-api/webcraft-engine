package com.gameexpert.engine.mob;

/**
 * 양 전단·염색·재성장, 닭 산란·달걀 부화, 돼지 안장·부스트의 <b>상태 없는</b> 바닐라 규칙.
 *
 * <p>근거는 Minecraft Java 1.21.4 의 {@code Sheep}·{@code EatBlockGoal}·{@code DyeItem}·
 * {@code Chicken}·{@code ThrownEgg}·{@code Pig}·{@code ItemBasedSteering} 이다. 이 클래스는
 * 두 권위(Spring 서버 / 정적판)가 같은 결정을 내리도록 수치를 한 곳에 못박는 정본이며,
 * 정적판 사본은 {@code client/src/backend/standalone/StandaloneFarmAnimalRules.ts} 다.
 * 두 사본의 상수 동일성은 {@code FarmAnimalRulesParityTest} 가 강제한다.
 */
public final class FarmAnimalRules {
    private FarmAnimalRules() {}

    /** 권위 틱 레이트(10 TPS). 바닐라 20 TPS 커서는 한 권위 틱에 두 번 전진한다. */
    public static final int AUTHORITY_TICKS_PER_SECOND = 10;

    // ── 양 ─────────────────────────────────────────────────────────────
    /** MC DyeColor 네트워크 ID 개수. 양 색·염료 색 모두 이 범위(0..15)를 쓴다. */
    public static final int DYE_COLORS = 16;
    public static final int DYE_WHITE = 0;
    public static final int DYE_ORANGE = 1;
    public static final int DYE_MAGENTA = 2;
    public static final int DYE_LIGHT_BLUE = 3;
    public static final int DYE_YELLOW = 4;
    public static final int DYE_LIME = 5;
    public static final int DYE_PINK = 6;
    public static final int DYE_GRAY = 7;
    public static final int DYE_LIGHT_GRAY = 8;
    public static final int DYE_CYAN = 9;
    public static final int DYE_PURPLE = 10;
    public static final int DYE_BLUE = 11;
    public static final int DYE_BROWN = 12;
    public static final int DYE_GREEN = 13;
    public static final int DYE_RED = 14;
    public static final int DYE_BLACK = 15;

    /** 바닐라 {@code Sheep.getRandomSheepColor} 의 첫 굴림 상한. */
    public static final int SHEEP_COLOR_ROLL_BOUND = 100;
    /** 흰 양이 드물게 분홍이 되는 두 번째 굴림 상한(1/500). */
    public static final int SHEEP_PINK_ROLL_BOUND = 500;
    /** 전단 양털 개수 {@code 1 + nextInt(3)} 의 최소치. */
    public static final int SHEEP_SHEAR_WOOL_MIN = 1;
    /** 전단 양털 개수 굴림 상한(배타). 1~3 개가 나온다. */
    public static final int SHEEP_SHEAR_WOOL_ROLL_BOUND = 3;
    /** 바닐라 {@code EatBlockGoal} 성체 양의 매 MC 틱 시도 상한(1/1000). */
    public static final int EAT_BLOCK_ADULT_ROLL_BOUND = 1000;
    /** 바닐라 {@code EatBlockGoal} 새끼 양의 매 MC 틱 시도 상한(1/50). */
    public static final int EAT_BLOCK_BABY_ROLL_BOUND = 50;
    /** 바닐라 {@code EatBlockGoal.EAT_ANIMATION_TICKS} — 40 MC 틱 뒤에 실제로 먹는다. */
    public static final int EAT_BLOCK_ANIMATION_MC_TICKS = 40;
    /** 바닐라 {@code Sheep.ate()} 가 새끼 양의 성장을 앞당기는 초. */
    public static final int EAT_BLOCK_BABY_AGE_UP_SECONDS = 60;

    /**
     * 바닐라 {@code Sheep.getRandomSheepColor(RandomSource)}.
     * 굴림 순서(먼저 {@code nextInt(100)}, 흰색 후보일 때만 {@code nextInt(500)})까지 그대로다.
     */
    public static int randomSheepColor(MobRandom rng) {
        int roll = rng.nextInt(SHEEP_COLOR_ROLL_BOUND);
        if (roll < 5) return DYE_BLACK;
        if (roll < 10) return DYE_GRAY;
        if (roll < 15) return DYE_LIGHT_GRAY;
        if (roll < 18) return DYE_BROWN;
        return rng.nextInt(SHEEP_PINK_ROLL_BOUND) == 0 ? DYE_PINK : DYE_WHITE;
    }

    /**
     * 스폰 시점 양 색.
     *
     * <p>WebCraft divergence: 바닐라는 월드 {@code RandomSource} 를 소비하지만 두 권위가
     * 정확히 같은 색을 내야 하므로 Armadillo scute 커서와 같은 {@code mobId} 해시를 쓴다.
     * 분포는 {@link #randomSheepColor}(= 바닐라 표)와 같고 공유 난수열을 흔들지 않는다.
     */
    public static int initialSheepColor(long mobId) {
        int roll = (int) (Integer.toUnsignedLong(hash32(mobId, SHEEP_COLOR_SALT))
                % SHEEP_COLOR_ROLL_BOUND);
        if (roll < 5) return DYE_BLACK;
        if (roll < 10) return DYE_GRAY;
        if (roll < 15) return DYE_LIGHT_GRAY;
        if (roll < 18) return DYE_BROWN;
        int pink = (int) (Integer.toUnsignedLong(hash32(mobId, SHEEP_PINK_SALT))
                % SHEEP_PINK_ROLL_BOUND);
        return pink == 0 ? DYE_PINK : DYE_WHITE;
    }

    /** 스폰 시점 산란 커서. 양 색과 같은 이유로 {@code mobId} 해시를 쓴다(6,000~11,999). */
    public static int initialChickenEggMcTicks(long mobId) {
        return CHICKEN_EGG_MIN_MC_TICKS
                + (int) (Integer.toUnsignedLong(hash32(mobId, CHICKEN_EGG_SALT))
                        % CHICKEN_EGG_ROLL_BOUND);
    }

    static final int SHEEP_COLOR_SALT = 0x5EE95A1;
    static final int SHEEP_PINK_SALT = 0x9107C0DE;
    static final int CHICKEN_EGG_SALT = 0xC81CE66;

    /** Armadillo scute 커서와 같은 32비트 혼합. 두 권위가 비트 단위로 같은 값을 낸다. */
    static int hash32(long mobId, int salt) {
        int hash = (int) (mobId ^ (mobId >>> 32)) ^ salt;
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        return hash;
    }

    /** 전단이 떨구는 양털 개수. 바닐라 {@code 1 + random.nextInt(3)}. */
    public static int shearWoolCount(MobRandom rng) {
        return SHEEP_SHEAR_WOOL_MIN + rng.nextInt(SHEEP_SHEAR_WOOL_ROLL_BOUND);
    }

    /**
     * 바닐라 2입력 염료 조합식(1.21.4 {@code recipes/*_dye.json} 중 재료가 정확히 둘인 9개).
     * {@code -1} 이면 그 조합에 대응하는 제작식이 없다.
     */
    public static int mixDyes(int left, int right) {
        int low = Math.min(left, right);
        int high = Math.max(left, right);
        // 아래 아홉 줄이 바닐라 2입력 염료 제작식 전부다. 나머지 색은 채취·제련으로만 얻는다.
        if (low == DYE_YELLOW && high == DYE_RED) return DYE_ORANGE;      // orange_dye
        if (low == DYE_WHITE && high == DYE_GREEN) return DYE_LIME;       // lime_dye
        if (low == DYE_WHITE && high == DYE_BLACK) return DYE_GRAY;       // gray_dye
        if (low == DYE_WHITE && high == DYE_BLUE) return DYE_LIGHT_BLUE;  // light_blue_dye
        if (low == DYE_BLUE && high == DYE_GREEN) return DYE_CYAN;        // cyan_dye
        if (low == DYE_BLUE && high == DYE_RED) return DYE_PURPLE;        // purple_dye
        if (low == DYE_PINK && high == DYE_PURPLE) return DYE_MAGENTA;    // magenta_dye
        if (low == DYE_WHITE && high == DYE_RED) return DYE_PINK;         // pink_dye
        if (low == DYE_WHITE && high == DYE_GRAY) return DYE_LIGHT_GRAY;  // light_gray_dye
        return -1;
    }

    /**
     * 바닐라 {@code Sheep.getOffspringColor(Animal father, Animal mother)}.
     * 두 부모 색의 염료 조합식이 있으면 그 색, 없으면 {@code random.nextBoolean()} 으로
     * 부모 중 하나를 고른다. 굴림은 조합식이 없을 때만 소비한다.
     */
    public static int offspringSheepColor(int fatherColor, int motherColor, MobRandom rng) {
        if (fatherColor == motherColor) return fatherColor;
        int mixed = mixDyes(fatherColor, motherColor);
        if (mixed >= 0) return mixed;
        // Java 의 nextBoolean() 은 nextInt(2)==0 과 같은 한 번의 굴림이다.
        return rng.nextInt(2) == 0 ? fatherColor : motherColor;
    }

    // ── 닭 ─────────────────────────────────────────────────────────────
    /** 바닐라 {@code Chicken.eggTime = random.nextInt(6000) + 6000} 의 하한. */
    public static final int CHICKEN_EGG_MIN_MC_TICKS = 6_000;
    /** 같은 식의 굴림 상한(배타). 결과 구간은 6,000~11,999 MC 틱이다. */
    public static final int CHICKEN_EGG_ROLL_BOUND = 6_000;
    /** 바닐라 알 투척 부화 확률의 분모(1/8). */
    public static final int EGG_HATCH_ROLL_BOUND = 8;
    /** 부화가 났을 때 4마리가 되는 추가 굴림의 분모(1/32). */
    public static final int EGG_QUAD_ROLL_BOUND = 32;
    /** 4마리 분기의 병아리 수. 기본 분기는 1마리다. */
    public static final int EGG_QUAD_CHICKS = 4;

    /** 다음 산란까지의 MC 틱. 바닐라 {@code random.nextInt(6000) + 6000}. */
    public static int nextChickenEggMcTicks(MobRandom rng) {
        return rng.nextInt(CHICKEN_EGG_ROLL_BOUND) + CHICKEN_EGG_MIN_MC_TICKS;
    }

    /**
     * 26.3 {@code gameplay/chicken_lay}: temperate lays egg, warm lays brown egg, cold lays blue egg.
     * The chicken's persisted climate variant is the loot-table component tested by vanilla.
     */
    public static ChickenLayEgg chickenLayEgg(String variant) {
        return switch (variant) {
            case "temperate" -> ChickenLayEgg.EGG;
            case "warm" -> ChickenLayEgg.BROWN_EGG;
            case "cold" -> ChickenLayEgg.BLUE_EGG;
            default -> throw new IllegalArgumentException("invalid chicken variant: " + variant);
        };
    }

    public enum ChickenLayEgg { EGG, BLUE_EGG, BROWN_EGG }

    /**
     * 던진 달걀이 낳는 병아리 수(0·1·4). 바닐라 {@code ThrownEgg.onHit} 의 굴림 순서를 지켜
     * 먼저 {@code nextInt(8)} 을, 그것이 0 일 때만 {@code nextInt(32)} 를 소비한다.
     */
    /**
     * [ZOMBIE-ANIMAL] 상한 달걀이 부화하는가. <b>항상 거짓</b>이며 굴림을 하나도 소비하지
     * 않는다 — 확률 0 을 "0/8 굴림"으로 표현하면 난수 수열이 갈리기 때문이다. 계약 정본은
     * {@code ZombieChickenRules} 이고 정적판 사본은 {@code StandaloneFarmAnimalRules} 다.
     */
    public static boolean spoiledEggHatches() {
        return false;
    }

    public static int eggHatchChickCount(MobRandom rng) {
        if (rng.nextInt(EGG_HATCH_ROLL_BOUND) != 0) return 0;
        return rng.nextInt(EGG_QUAD_ROLL_BOUND) == 0 ? EGG_QUAD_CHICKS : 1;
    }

    /**
     * 이 종결 사유가 바닐라 {@code ThrownEgg.onHit} 에 해당하는가. 바닐라는 블록·엔티티에
     * 닿았을 때만 부화를 굴리고, 수명이 다해 사라지는 알은 굴리지 않는다. 두 권위가 같은
     * 프로토콜 사유 문자열을 쓰므로 이 판정도 한 곳에서만 정의한다.
     */
    public static boolean eggHatchesOnTerminal(String terminalReason) {
        return "block_hit".equals(terminalReason)
                || "mob_hit".equals(terminalReason)
                || "player_hit".equals(terminalReason) || "placed_entity_hit".equals(terminalReason);
    }

    // ── 돼지 ───────────────────────────────────────────────────────────
    /** 바닐라 {@code ItemBasedSteering.boost} 지속의 하한(MC 틱). */
    public static final int PIG_BOOST_MIN_MC_TICKS = 140;
    /** 같은 식의 굴림 상한(배타). 결과 구간은 140~980 MC 틱이다. */
    public static final int PIG_BOOST_ROLL_BOUND = 841;
    /** 바닐라 {@code boostFactor} 의 진폭. 최대 배율은 1 + 1.15 = 2.15 다. */
    public static final double PIG_BOOST_AMPLITUDE = 1.15;
    /** 바닐라 {@code Pig.getRiddenSpeed} 의 이동 속도 계수. */
    public static final double PIG_RIDDEN_SPEED_FACTOR = 0.225;
    /** 바닐라 pig 의 {@code generic.movement_speed}. */
    public static final double PIG_MOVEMENT_SPEED = 0.25;

    /** 부스트 총 지속(MC 틱). 바닐라 {@code random.nextInt(841) + 140}. */
    public static int nextPigBoostMcTicks(MobRandom rng) {
        return rng.nextInt(PIG_BOOST_ROLL_BOUND) + PIG_BOOST_MIN_MC_TICKS;
    }

    /**
     * 바닐라 {@code ItemBasedSteering.boostFactor()} =
     * {@code 1 + 1.15 * sin(boostTime / boostTimeTotal * PI)}. 부스트 중이 아니면 1 이다.
     */
    public static double pigBoostFactor(int boostMcTicks, int boostTotalMcTicks) {
        if (boostMcTicks <= 0 || boostTotalMcTicks <= 0) return 1.0;
        double phase = (double) boostMcTicks / (double) boostTotalMcTicks;
        return 1.0 + PIG_BOOST_AMPLITUDE * Math.sin(phase * Math.PI);
    }

    /** 탑승 중인 돼지의 이동 속도(movement-speed 속성 공간). */
    public static double pigRiddenSpeed(int boostMcTicks, int boostTotalMcTicks) {
        return PIG_MOVEMENT_SPEED * PIG_RIDDEN_SPEED_FACTOR
                * pigBoostFactor(boostMcTicks, boostTotalMcTicks);
    }

    /**
     * movement-speed 속성 1 단위의 실제 수평 속도(블록/초). {@code docs/MC-REFERENCE.md}
     * 「말 최고 탑승 속도」 절이 못박은 바닐라 환산(속성 0.3375 → 14.23 블록/초)과 같은 계수다.
     * 조종은 클라가 초 단위로 적분하므로 속성 공간 값을 이 계수로 옮겨 쓴다.
     */
    public static final double MOVEMENT_SPEED_BLOCKS_PER_SECOND = 42.16;

    /**
     * 탑승 중인 돼지의 실제 이동 속도(블록/초). 부스트가 없으면 약 2.372,
     * 최대 배율 2.15 에서 약 5.099 블록/초다(플레이어 스프린트 5.6 보다 느리다).
     * 이 최댓값이 곧 좌표 업링크가 견뎌야 할 속도 상한의 근거다.
     */
    public static double pigRiddenSpeedBlocksPerSecond(int boostMcTicks, int boostTotalMcTicks) {
        return pigRiddenSpeed(boostMcTicks, boostTotalMcTicks) * MOVEMENT_SPEED_BLOCKS_PER_SECOND;
    }

    /**
     * 기수 pose 와 돼지 좌표 사이에 허용하는 최대 간격(블록). 보트 운전자 범위
     * ({@code BoatSystem.DRIVER_POS_RANGE})와 <b>같은 값</b>이며 그 동일성은
     * {@code PigRideUplinkAuthorityTest} 가 강제한다. 기수 pose 가 이미
     * {@code MovementLimits} 로 속도 상한을 받으므로 이 leash 가 곧 돼지의 속도 상한이다.
     */
    public static final double PIG_RIDER_POS_RANGE = 16.0;

    /**
     * 업링크 좌표가 기수 pose 근처인가. 좌석 계약이 종 비의존으로 올라간 뒤로 판정 정본은
     * {@link MobMountRules#withinRiderRange} 하나뿐이고 돼지는 그것을 그대로 위임한다
     * (동작 불변 — 값·식이 같다).
     */
    public static boolean withinPigRiderRange(double riderX, double riderY, double riderZ,
            double x, double y, double z) {
        return MobMountRules.withinRiderRange(riderX, riderY, riderZ, x, y, z);
    }
}
