package com.gameexpert.engine.mob;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;

/**
 * 말 계열(Horse·Donkey·Mule·Zombie Horse)의 <b>상태 없는</b> 바닐라 규칙. 근거는 pinned
 * Minecraft Java 26.3-snapshot-7 의
 * {@code AbstractHorse}·{@code Horse}·{@code RunAroundLikeCrazyGoal} 이며, 각 상수 옆에 원문
 * 식과 클래스를 적어 둔다. 정적판 사본은
 * {@code client/src/backend/standalone/StandaloneHorseRules.ts} 이고, 두 사본의 상수·결정
 * 동일성은 {@code StandaloneHorseRules.test.ts} 가 이 파일 원문을 읽어 강제한다.
 *
 * <p>WebCraft divergence 는 파일 끝 주석에 모아 둔다.
 */
public final class HorseRules {

    private HorseRules() {
    }

    /** Horse body-armor items in ascending material order. */
    public static boolean isArmorItem(short itemType) {
        return itemType == PlayerInventory.LEATHER_HORSE_ARMOR
                || itemType == PlayerInventory.COPPER_HORSE_ARMOR
                || itemType == PlayerInventory.IRON_HORSE_ARMOR
                || itemType == PlayerInventory.GOLDEN_HORSE_ARMOR
                || itemType == PlayerInventory.DIAMOND_HORSE_ARMOR
                || itemType == PlayerInventory.NETHERITE_HORSE_ARMOR;
    }

    /** Only the ordinary Horse exposes the vanilla BODY armor slot. */
    public static boolean canWearArmor(MobType type) {
        return type == MobType.HORSE;
    }

    /** Vanilla BODY defense values: leather 3, copper 4, iron 5, gold 7, diamond 11, netherite 12. */
    public static int armorPoints(short itemType) {
        if (itemType == PlayerInventory.LEATHER_HORSE_ARMOR) return 3;
        if (itemType == PlayerInventory.COPPER_HORSE_ARMOR) return 4;
        if (itemType == PlayerInventory.IRON_HORSE_ARMOR) return 5;
        if (itemType == PlayerInventory.GOLDEN_HORSE_ARMOR) return 7;
        if (itemType == PlayerInventory.DIAMOND_HORSE_ARMOR) return 11;
        if (itemType == PlayerInventory.NETHERITE_HORSE_ARMOR) return 12;
        return 0;
    }

    /** Netherite BODY armor alone contributes armor toughness 3. */
    public static int armorToughness(short itemType) {
        return itemType == PlayerInventory.NETHERITE_HORSE_ARMOR ? 3 : 0;
    }

    /** Netherite BODY armor contributes knockback resistance 0.1. */
    public static double armorKnockbackResistance(short itemType) {
        return itemType == PlayerInventory.NETHERITE_HORSE_ARMOR ? 0.1 : 0.0;
    }

    // ── 길들이기(temper) ───────────────────────────────────────────────
    /**
     * 바닐라 {@code AbstractHorse#getMaxTemper()} = 100. 말·당나귀·노새가 이 값을 쓰고,
     * 라마만 {@code Llama#getMaxTemper()} 오버라이드로 30 이다({@link LlamaRules#MAX_TEMPER}).
     * 종별 상한은 {@link #maxTemper(MobType)} 한 곳에서만 갈린다.
     */
    public static final int MAX_TEMPER = 100;

    /** 종별 max temper. 라마만 30 이고 나머지 말 계열은 100 이다. */
    public static int maxTemper(MobType type) {
        return type == MobType.LLAMA ? LlamaRules.MAX_TEMPER : MAX_TEMPER;
    }

    /** 종별 상한으로 클램프한다({@code AbstractHorse#modifyTemper}). */
    public static int clampTemper(MobType type, int temper) {
        int max = maxTemper(type);
        return temper < 0 ? 0 : Math.min(temper, max);
    }

    /**
     * 바닐라 {@code RunAroundLikeCrazyGoal#tick()}: 미길들임 상태로 승객을 태우고 있는 동안
     * <b>MC 틱마다</b> {@code random.nextInt(50) == 0} 일 때만 길들이기 판정을 한 번 굴린다.
     */
    public static final int TAME_ATTEMPT_ROLL_BOUND = 50;

    /**
     * 판정 실패 시 temper 상승폭. 바닐라 {@code this.horse.modifyTemper(5)} 다.
     * 즉 최악의 경우에도 20번의 실패면 max temper 에 닿아 다음 판정이 반드시 성공한다.
     */
    public static final int TAME_FAILURE_TEMPER_GAIN = 5;

    /**
     * 길들이기 판정. 바닐라 {@code RunAroundLikeCrazyGoal#tick()} 의
     * {@code j > 0 && random.nextInt(j) < i} (i=temper, j=maxTemper) 그대로다.
     *
     * @param temperRoll {@code random.nextInt(MAX_TEMPER)} 의 결과
     */
    public static boolean tamesOnAttempt(int temper, int temperRoll) {
        return temperRoll < temper;
    }

    /** temper 는 {@code AbstractHorse#modifyTemper} 에서 [0, maxTemper] 로 클램프된다. */
    public static int clampTemper(int temper) {
        return temper < 0 ? 0 : Math.min(temper, MAX_TEMPER);
    }

    // ── 먹이 표({@code AbstractHorse#handleEating}) ──────────────────────
    /**
     * 먹이 한 개가 주는 temper. 바닐라 {@code AbstractHorse#handleEating(Player, ItemStack)} 의
     * 지역변수 {@code j} 다: 밀 3, 설탕 3, 건초더미 0, 사과 3, 황금 당근 5, 황금 사과 10.
     * 표에 없는 아이템은 먹이가 아니다(-1).
     */
    public static int feedTemperGain(short itemType) {
        if (itemType == PlayerInventory.WHEAT) return 3;
        if (itemType == PlayerInventory.SUGAR) return 3;
        if (itemType == (short) Blocks.HAY_BLOCK) return 0;
        if (itemType == PlayerInventory.APPLE) return 3;
        if (itemType == PlayerInventory.GOLDEN_CARROT) return 5;
        if (itemType == PlayerInventory.GOLDEN_APPLE) return 10;
        return -1;
    }

    /**
     * 같은 표의 회복량({@code f}, 하트 절반 단위 = HP). 밀 2, 설탕 1, 건초더미 20, 사과 3,
     * 황금 당근 4, 황금 사과 10.
     */
    public static double feedHealAmount(short itemType) {
        if (itemType == PlayerInventory.WHEAT) return 2.0;
        if (itemType == PlayerInventory.SUGAR) return 1.0;
        if (itemType == (short) Blocks.HAY_BLOCK) return 20.0;
        if (itemType == PlayerInventory.APPLE) return 3.0;
        if (itemType == PlayerInventory.GOLDEN_CARROT) return 4.0;
        if (itemType == PlayerInventory.GOLDEN_APPLE) return 10.0;
        return 0.0;
    }

    /**
     * 같은 표의 새끼 성장 단축(초, {@code i}). 밀 20, 설탕 30, 건초더미 180, 사과 60,
     * 황금 당근 60, 황금 사과 240.
     */
    public static int feedAgeUpSeconds(short itemType) {
        if (itemType == PlayerInventory.WHEAT) return 20;
        if (itemType == PlayerInventory.SUGAR) return 30;
        if (itemType == (short) Blocks.HAY_BLOCK) return 180;
        if (itemType == PlayerInventory.APPLE) return 60;
        if (itemType == PlayerInventory.GOLDEN_CARROT) return 60;
        if (itemType == PlayerInventory.GOLDEN_APPLE) return 240;
        return 0;
    }

    /** 이 아이템이 말 먹이 표에 있는가. */
    public static boolean isFood(short itemType) {
        return feedTemperGain(itemType) >= 0;
    }

    /**
     * 같은 표의 {@code flag}(love mode 진입 가능). 바닐라에서 황금 당근·황금 사과만 참이며
     * 이는 {@link MobType#isBreedingFood} 의 말·당나귀 항목과 정확히 같은 집합이다.
     */
    public static boolean isBreedingFood(short itemType) {
        return itemType == PlayerInventory.GOLDEN_CARROT
                || itemType == PlayerInventory.GOLDEN_APPLE;
    }

    // ── 개체 스탯 롤({@code AbstractHorse#generate*}) ────────────────────
    /** {@code generateMaxHealth} 의 고정항. 식은 {@code 15 + nextInt(8) + nextInt(9)} 다. */
    public static final int MAX_HEALTH_BASE = 15;
    public static final int MAX_HEALTH_ROLL_A_BOUND = 8;
    public static final int MAX_HEALTH_ROLL_B_BOUND = 9;
    /** 위 식의 결과 구간(닫힘): 15 ~ 30. */
    public static final double MIN_MAX_HEALTH = 15.0;
    public static final double MAX_MAX_HEALTH = 30.0;

    /** {@code generateJumpStrength} = {@code 0.4 + d*0.2 + d*0.2 + d*0.2} → [0.4, 1.0). */
    public static final double JUMP_STRENGTH_BASE = 0.4;
    public static final double JUMP_STRENGTH_ROLL_SPAN = 0.2;
    public static final double MIN_JUMP_STRENGTH = 0.4;
    public static final double MAX_JUMP_STRENGTH = 1.0;

    /**
     * {@code generateSpeed} = {@code (0.45 + d*0.3 + d*0.3 + d*0.3) * 0.25} → [0.1125, 0.3375).
     * 이 값은 movement-speed <b>속성</b> 공간이며 실제 블록/초 환산은
     * {@link FarmAnimalRules#MOVEMENT_SPEED_BLOCKS_PER_SECOND} 계수를 쓴다
     * (docs/MC-REFERENCE.md 「말 최고 탑승 속도」: 속성 0.3375 → 약 14.23블록/초).
     */
    public static final double SPEED_BASE = 0.45;
    public static final double SPEED_ROLL_SPAN = 0.3;
    public static final double SPEED_SCALE = 0.25;
    public static final double MIN_SPEED = 0.1125;
    public static final double MAX_SPEED = 0.3375;

    /** 바닐라 {@code AbstractHorse#generateMaxHealth}. 굴림 순서(8 먼저, 그다음 9)까지 같다. */
    public static double generateMaxHealth(MobRandom rng) {
        return MAX_HEALTH_BASE + rng.nextInt(MAX_HEALTH_ROLL_A_BOUND)
                + rng.nextInt(MAX_HEALTH_ROLL_B_BOUND);
    }

    /** 바닐라 {@code AbstractHorse#generateJumpStrength}. 독립 난수 3개를 순서대로 소비한다. */
    public static double generateJumpStrength(MobRandom rng) {
        return JUMP_STRENGTH_BASE + rng.nextDouble() * JUMP_STRENGTH_ROLL_SPAN
                + rng.nextDouble() * JUMP_STRENGTH_ROLL_SPAN
                + rng.nextDouble() * JUMP_STRENGTH_ROLL_SPAN;
    }

    /** 바닐라 {@code AbstractHorse#generateSpeed}. 독립 난수 3개를 순서대로 소비한다. */
    public static double generateSpeed(MobRandom rng) {
        return (SPEED_BASE + rng.nextDouble() * SPEED_ROLL_SPAN
                + rng.nextDouble() * SPEED_ROLL_SPAN
                + rng.nextDouble() * SPEED_ROLL_SPAN) * SPEED_SCALE;
    }

    /**
     * 바닐라 {@code Horse#randomizeAttributes(RandomSource)} 의 소비 순서: 체력 → 속도 → 점프.
     * 골든 테스트가 이 순서를 붙잡으므로 호출부는 이 메서드만 쓴다.
     */
    public static Stats rollStats(MobRandom rng) {
        double maxHealth = generateMaxHealth(rng);
        double speed = generateSpeed(rng);
        double jumpStrength = generateJumpStrength(rng);
        return new Stats(maxHealth, speed, jumpStrength);
    }

    /**
     * 바닐라 {@code AbstractChestedHorse#createBaseChestedHorseAttributes()} 가 못박는 고정 점프
     * 강도: {@code .add(Attributes.JUMP_STRENGTH, 0.5)}. 당나귀·노새·라마는 말과 달리 점프를
     * <b>굴리지 않는다</b>.
     */
    public static final double CHESTED_JUMP_STRENGTH = 0.5;

    /**
     * 같은 식의 고정 이동 속도 {@code .add(Attributes.MOVEMENT_SPEED, 0.175F)}. 값은
     * {@code MobType.DONKEY.baseSpeed()} 와 <b>같은 수</b>여야 하며 그 사실은
     * {@code ChestedHorseRulesTest} 가 강제한다.
     */
    public static final double CHESTED_SPEED = 0.174999997;

    /**
     * 종별 개체 스탯 롤. 바닐라는 {@code AbstractHorse#randomizeAttributes} 를 종마다 오버라이드한다:
     * <ul>
     *   <li>{@code Horse#randomizeAttributes}: 체력 → 속도 → 점프를 모두 굴린다.</li>
     *   <li>{@code AbstractChestedHorse#randomizeAttributes}: <b>체력만</b> 굴리고 속도·점프는
     *       {@code createBaseChestedHorseAttributes} 의 고정값(0.175 / 0.5)을 그대로 둔다.
     *       당나귀·노새·라마가 여기에 해당한다.</li>
     * </ul>
     * 난수 소비 개수가 종마다 다르다는 사실 자체가 계약이라 호출부는 이 메서드만 쓴다.
     */
    public static Stats rollStats(MobType type, MobRandom rng) {
        return switch (type) {
            case HORSE -> rollStats(rng);
            case DONKEY, MULE, LLAMA ->
                    new Stats(generateMaxHealth(rng), CHESTED_SPEED, CHESTED_JUMP_STRENGTH);
            // 26.3 ZombieHorse 는 점프 3회 다음 속도 3회를 굴린다.
            case ZOMBIE_HORSE -> ZombieHorseRules.rollStats(rng);
            default -> throw new IllegalArgumentException("not a horse-family species: " + type);
        };
    }

    /**
     * 이 종이 말 계열(개체 스탯·temper·좌석 상태를 갖는 종)인가.
     *
     * <p>좀비 말도 여기에 든다 — 바닐라 {@code ZombieHorse extends AbstractHorse} 이고, WebCraft
     * 에서도 좌석·안장·영속 상태를 말과 <b>같은 상태 기계</b>가 소유한다. 이 집합에서 빼면
     * 재시작마다 안장을 잃는다({@code WorldMob.horseFamilyType} 이 같은 집합을 다시 건다).
     * 다만 좀비 말은 태어날 때부터 길들여져 있어 temper·낙마 판정이 서지 않는다.
     */
    public static boolean isHorseFamily(MobType type) {
        return switch (type) {
            case HORSE, DONKEY, MULE, LLAMA, ZOMBIE_HORSE -> true;
            default -> false;
        };
    }

    /**
     * 개체 스탯 전용 RNG 시드 솔트. 몹 ID 만으로 시드하면 다른 ID 공간(예: 청크 시드)과 겹칠 수
     * 있어 임의의 고정 상수를 섞는다. 값 자체에 게임 의미는 없고, 두 권위가 <b>같은 값</b>을
     * 쓴다는 사실만이 계약이다.
     */
    public static final long STAT_SEED_SALT = 0x486f727365537431L; // "HorseSt1"

    /**
     * 몹 ID 를 시드 공간 전체로 흩는 홀수 상수(황금비 64비트). 값 자체에 게임 의미는 없고,
     * {@link LlamaRules#STRENGTH_SEED_MIX} 와 <b>같은 수</b>여야 한다는 사실이 계약이다
     * (그 동일성은 {@code HorseRulesTest} 가 강제한다 — 두 사본이 따로 있는 이유는 정적판에서
     * {@code StandaloneHorseRules} ↔ {@code StandaloneLlamaRules} 가 서로를 import 하면 순환이
     * 되기 때문이다).
     *
     * <p><b>왜 필요한가.</b> {@code new Random(salt ^ mobId)} 처럼 낮은 비트만 다른 시드를 주면
     * {@code java.util.Random} 의 <b>첫 draw</b> 들이 시드와 강하게 상관된다. 실제로 솔트 XOR 만
     * 쓰면 mobId 1~2000 에서 {@code 15 + nextInt(8) + nextInt(9)} 가 18~27 만 내놓아 바닐라
     * 체력 15·16·17·28·29·30 인 말이 초기 월드에 <b>구조적으로</b> 존재할 수 없었다. ID 를 곱해
     * 흩으면 같은 2000 개체에서 15~30 전 구간이 모두 출현한다.
     */
    public static final long STAT_SEED_MIX = 0x9e3779b97f4a7c15L;

    /** 개체 스탯 전용 결정론 RNG. 같은 몹 ID 는 언제나 같은 스탯을 낸다. */
    public static MobRandom statRandom(long mobId) {
        return new JavaMobRandom(STAT_SEED_SALT ^ mobId * STAT_SEED_MIX);
    }

    /** 한 개체의 말 스탯. 영속·번식 계승의 단위다. */
    public record Stats(double maxHealth, double speed, double jumpStrength) {
        public boolean valid() {
            return maxHealth >= MIN_MAX_HEALTH && maxHealth <= MAX_MAX_HEALTH
                    && speed >= MIN_SPEED && speed <= MAX_SPEED
                    && jumpStrength >= MIN_JUMP_STRENGTH && jumpStrength <= MAX_JUMP_STRENGTH;
        }
    }

    // ── 번식 계승({@code AbstractHorse#createOffspringAttribute}) ────────
    /** 위 식의 폭 계수. 바닐라 {@code double d = 0.15 * (max - min);} 다. */
    public static final double OFFSPRING_SPREAD_FACTOR = 0.15;

    /**
     * 바닐라 {@code AbstractHorse#createOffspringAttribute(double,double,double,double,RandomSource)}:
     * <pre>
     *   parentA = clamp(parentA, min, max); parentB = clamp(parentB, min, max);
     *   d = 0.15 * (max - min);
     *   e = abs(parentA - parentB) + d * 2;
     *   f = (parentA + parentB) / 2;
     *   g = (r() + r() + r()) / 3 - 0.5;
     *   h = f + e * g;
     *   범위를 벗어나면 경계에서 되접는다(reflect).
     * </pre>
     * 난수 3개를 순서대로 소비하며, 속성별 [min,max] 는 체력 15~30, 점프 0.4~1.0,
     * 속도 0.1125~0.3375 다({@code setOffspringAttributes}).
     */
    public static double createOffspringAttribute(double parentA, double parentB,
            double min, double max, MobRandom rng) {
        if (max <= min) throw new IllegalArgumentException("bad attribute range");
        double a = clamp(parentA, min, max);
        double b = clamp(parentB, min, max);
        double spread = OFFSPRING_SPREAD_FACTOR * (max - min);
        double width = Math.abs(a - b) + spread * 2.0;
        double mid = (a + b) / 2.0;
        double bias = (rng.nextDouble() + rng.nextDouble() + rng.nextDouble()) / 3.0 - 0.5;
        double value = mid + width * bias;
        if (value > max) return max - (value - max);
        if (value < min) return min + (min - value);
        return value;
    }

    /**
     * 바닐라 {@code AbstractHorse#setOffspringAttributes} 의 소비 순서: 체력 → 점프 → 속도.
     * (스탯 <b>롤</b>과 순서가 다르다 — 바닐라 원문 그대로다.)
     */
    public static Stats breedStats(Stats a, Stats b, MobRandom rng) {
        double maxHealth = createOffspringAttribute(
                a.maxHealth(), b.maxHealth(), MIN_MAX_HEALTH, MAX_MAX_HEALTH, rng);
        double jumpStrength = createOffspringAttribute(
                a.jumpStrength(), b.jumpStrength(), MIN_JUMP_STRENGTH, MAX_JUMP_STRENGTH, rng);
        double speed = createOffspringAttribute(
                a.speed(), b.speed(), MIN_SPEED, MAX_SPEED, rng);
        return new Stats(maxHealth, speed, jumpStrength);
    }

    /**
     * {@code AbstractHorse#setOffspringAttributes} 를 구체 Horse가 아닌 말 계열 공통 경계에
     * 적용한다. 따라서 말×당나귀의 노새, 당나귀 및 라마 새끼도 같은 체력→점프→속도 RNG
     * 소비 순서로 부모 스탯을 계승한다.
     *
     * @return 세 개체가 모두 말 계열이어서 계승을 적용했으면 true
     */
    public static boolean applyOffspringStats(Mob child, Mob firstParent, Mob secondParent,
            MobRandom rng) {
        if (!(child instanceof AbstractHorseMob offspring)
                || !(firstParent instanceof AbstractHorseMob first)
                || !(secondParent instanceof AbstractHorseMob second)) return false;
        offspring.applyStats(breedStats(first.stats(), second.stats(), rng));
        offspring.setPersistenceRequired(true);
        return true;
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    // ── 점프 차지({@code AbstractHorse#onPlayerJump} / {@code executeRidersJump}) ──
    /**
     * 클라가 올리는 차지 게이지의 정수 상한. 바닐라 {@code ServerboundPlayerCommandPacket} 의
     * {@code Mth.floor(jumpRidingScale * 100)} 라 0~100 이다.
     */
    public static final int MAX_JUMP_CHARGE = 100;
    /** 바닐라 {@code onPlayerJump}: 90 이상이면 배율이 1.0 으로 포화한다. */
    public static final int JUMP_CHARGE_SATURATION = 90;
    /** 같은 식의 하한 배율과 그 위 선형 구간의 폭. {@code 0.4 + 0.4 * charge / 90}. */
    public static final double JUMP_SCALE_BASE = 0.4;
    public static final double JUMP_SCALE_SPAN = 0.4;

    /**
     * 바닐라 {@code AbstractHorse#onPlayerJump(int)}:
     * {@code charge >= 90 ? 1.0 : 0.4 + 0.4 * charge / 90}. 0 미만은 0 으로 잘린다.
     * 권위는 이 식으로 클라가 주장한 차지를 배율로 바꾼 뒤 강도만 검증한다.
     */
    public static double jumpScale(int charge) {
        int clamped = charge < 0 ? 0 : Math.min(charge, MAX_JUMP_CHARGE);
        if (clamped >= JUMP_CHARGE_SATURATION) return 1.0;
        return JUMP_SCALE_BASE + JUMP_SCALE_SPAN * clamped / (double) JUMP_CHARGE_SATURATION;
    }

    /**
     * 바닐라 {@code AbstractHorse#executeRidersJump}: 수직 초기 속도(블록/MC 틱) =
     * {@code jumpStrength * scale}(블록 점프 계수 1.0, 점프 강화 없음). 권위는 이 값을 상한으로
     * 삼아 기수가 주장한 점프를 검증한다.
     */
    public static double jumpVelocityPerMcTick(double jumpStrength, int charge) {
        return jumpStrength * jumpScale(charge);
    }

    /** 클라 물리는 초 단위라 20 TPS 로 환산해 쓴다(블록/초). */
    public static double jumpVelocityBlocksPerSecond(double jumpStrength, int charge) {
        return jumpVelocityPerMcTick(jumpStrength, charge) * 20.0;
    }

    // ── 탑승 속도 ──────────────────────────────────────────────────────
    /**
     * 탑승 중 말의 실제 수평 속도(블록/초). 바닐라 {@code AbstractHorse#getRiddenSpeed} 는
     * movement-speed 속성을 그대로 쓰므로(돼지처럼 0.225 를 곱하지 않는다) 개체 속도 스탯이
     * 곧 탑승 속도다. 환산 계수는 돼지와 같은 한 곳
     * ({@link FarmAnimalRules#MOVEMENT_SPEED_BLOCKS_PER_SECOND})에서 온다.
     */
    public static double riddenSpeedBlocksPerSecond(double speed) {
        return speed * FarmAnimalRules.MOVEMENT_SPEED_BLOCKS_PER_SECOND;
    }

    // ── WebCraft divergence ────────────────────────────────────────────
    // 1. 바닐라는 안장 없이도 미길들임 말에 올라타 길들이기를 시도한다. WebCraft 도 같다 —
    //    안장은 "탑승 자체"의 조건이 아니다(돼지와 다른 점). 다만 <b>조종</b>은 바닐라와 같이
    //    안장이 있어야 한다({@code AbstractHorse#getControllingPassenger} 의 {@code isSaddled()}
    //    가드 = {@link MobMountRules#steerable}).
    // 2. 바닐라 {@code RunAroundLikeCrazyGoal} 은 판정 실패 시 말이 실제로 날뛰며(makeMad)
    //    이동한다. WebCraft 권위는 좌석만 비우고 낙마시키며, 날뜀 애니메이션은 클라 표현이다.
    // 3. 말 변종(색 7 × 무늬 5)은 렌더 계약이 색만 소비하므로 무늬는 아직 권위 상태가 아니다.
}
