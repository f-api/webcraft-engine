package com.gameexpert.engine.mob;

import com.gameexpert.terrain.Blocks;

/**
 * 좀비 주민 치료 규칙(MOB.md §2). 정적판 사본은
 * {@code client/src/backend/standalone/StandaloneZombieVillagerCure.ts} 이며 상수는 두 파일이
 * 같은 값을 가져야 한다. 근거는 Minecraft Java 1.21.4 {@code ZombieVillagerEntity}.
 *
 * <ul>
 *   <li>{@code interactMob}: 손에 황금 사과 + 대상이 {@code StatusEffects.WEAKNESS} 보유 →
 *       사과 1개 소비 후 {@code setConverting(uuid, random.nextInt(2401) + 3600)}.</li>
 *   <li>{@code setConverting}: {@code conversionTimer = delay}, {@code CONVERTING} 트래커 on
 *       (클라 몸 떨림 표현), {@code WEAKNESS} 제거.</li>
 *   <li>{@code tick}: 게임 틱마다 {@code conversionTimer -= getConversionRate()} 하고 0 이하가
 *       되면 {@code finishConversion} 으로 Villager 가 된다.</li>
 *   <li>{@code getConversionRate}: 기본 1. 게임 틱마다 1% 확률로 자기 위치 기준 8×8×8(±4) 상자를
 *       x→y→z 순서로 훑어 철창·침대를 최대 14개까지 세고, 그 하나마다 30% 확률로 +1 한다.</li>
 *   <li>{@code finishConversion} → {@code handleInteraction(ZOMBIE_VILLAGER_CURED)} → 치료된
 *       주민이 치료자에게 {@code MAJOR_POSITIVE 20} · {@code MINOR_POSITIVE 25} gossip 을 건다.</li>
 * </ul>
 *
 * <p>WebCraft divergence(MC-REFERENCE "좀비 주민 치료" 절 계약):
 * 바닐라 {@code setConverting} 이 함께 부여하는 {@code STRENGTH}(앰프
 * {@code min(difficulty.getId()-1, 0)})는 WebCraft {@code StatusEffect} 에 강화가 없어 부여하지
 * 않는다. 난이도가 치료에 개입하는 곳은 바닐라에서도 이 앰프뿐이라 카운트다운 길이는 난이도와
 * 무관하다. 완료 시 바닐라 {@code NAUSEA 200} 과 {@code entity.zombie_villager.converted} 음성도
 * 각각 상태이상·음성 계열이 등록돼 있지 않아 생략하며, 전환 사실은 Tadpole→Frog 변태와 같은
 * Converted/despawn 쌍이 그대로 나른다.
 */
public final class ZombieVillagerCureRules {
    /** 20 TPS 게임 틱 기준 카운트다운 최소값. */
    public static final int CONVERSION_MIN_MC_TICKS = 3_600;
    /** 카운트다운 상한(포함). */
    public static final int CONVERSION_MAX_MC_TICKS = 6_000;
    /** 바닐라 {@code random.nextInt(2401)} 의 bound. */
    public static final int CONVERSION_RANDOM_BOUND = 2_401;
    /** 10 TPS 권위 틱 1회가 진행하는 20 TPS 게임 틱 수. */
    public static final int MC_TICKS_PER_AUTHORITY_TICK = 2;
    /** {@code getConversionRate} 의 기본 감소량. */
    public static final int CONVERSION_BASE_RATE = 1;
    /** 게임 틱마다 주변 상자를 훑을 확률(바닐라 {@code nextFloat() < 0.01F}). */
    public static final float CONVERSION_SCAN_CHANCE = 0.01f;
    /** 상자 반경. 바닐라는 {@code (int)getX()-4} 부터 {@code (int)getX()+4} 미만까지 훑는다. */
    public static final int CONVERSION_SCAN_RADIUS = 4;
    /** 상자 안에서 세는 철창·침대의 최대 개수. */
    public static final int CONVERSION_SCAN_MAX_BLOCKS = 14;
    /** 센 블록 하나가 감소량을 +1 할 확률(바닐라 {@code nextFloat() < 0.3F}). */
    public static final float CONVERSION_BLOCK_CHANCE = 0.3f;

    /** 치료된 주민이 치료자에게 거는 {@code MAJOR_POSITIVE} gossip 값. */
    public static final int CURED_MAJOR_POSITIVE_GOSSIP = 20;
    /** 치료된 주민이 치료자에게 거는 {@code MINOR_POSITIVE} gossip 값. */
    public static final int CURED_MINOR_POSITIVE_GOSSIP = 25;
    /** {@code VillagerGossipType.MAJOR_POSITIVE} 의 평판 가중치. */
    public static final int GOSSIP_MAJOR_POSITIVE_WEIGHT = 5;
    /** {@code VillagerGossipType.MINOR_POSITIVE} 의 평판 가중치. */
    public static final int GOSSIP_MINOR_POSITIVE_WEIGHT = 1;
    /**
     * 치료 직후 치료자가 얻는 평판 총합(= 거래 할인 폭의 정본).
     * 바닐라 {@code VillagerEntity.getReputation} 이 gossip 값 × 가중치를 더한 값과 같다:
     * 20×5 + 25×1 = 125.
     */
    public static final int CURED_REPUTATION_DISCOUNT =
            CURED_MAJOR_POSITIVE_GOSSIP * GOSSIP_MAJOR_POSITIVE_WEIGHT
                    + CURED_MINOR_POSITIVE_GOSSIP * GOSSIP_MINOR_POSITIVE_WEIGHT;

    private ZombieVillagerCureRules() {}

    /**
     * 전환 완료 훅. <b>직업 승계와 gossip 부여의 배선은 주민 트랙이 소유한다.</b> 몹 권위는 완료
     * 사실만 넘기고 직업·평판 상태는 건드리지 않는다. 인자 순서는 정적판
     * {@code StandaloneCuredVillager} 의 필드 순서와 같다.
     *
     * @param villagerMobId       새로 만들어진 Villager 의 몹 id
     * @param zombieVillagerMobId 사라진 ZombieVillager 의 몹 id
     * @param curedBy             카운트다운을 시작시킨 플레이어 닉네임. 복구 실패 시에만 null
     * @param profession          승계할 직업. ZombieVillager 가 아직 직업을 들고 있지 않아 현재는
     *                            항상 null 이며, 주민 트랙이 직업 상태를 붙이면 그 값이 실린다
     * @param discount            치료자가 얻는 평판(= 거래 할인) 총합. 항상
     *                            {@link #CURED_REPUTATION_DISCOUNT}
     */
    @FunctionalInterface
    public interface CuredVillagerHook {
        void onCuredVillager(long villagerMobId, long zombieVillagerMobId, String curedBy,
                String profession, int discount);

        /** 주민 트랙이 배선하기 전의 기본값. 아무것도 하지 않는다. */
        CuredVillagerHook NOOP = (villagerMobId, zombieVillagerMobId, curedBy, profession,
                discount) -> { };
    }

    /** 카운트다운 초기값. 바닐라 {@code random.nextInt(2401) + 3600}. */
    public static int rollConversionMcTicks(MobRandom rng) {
        return CONVERSION_MIN_MC_TICKS + rng.nextInt(CONVERSION_RANDOM_BOUND);
    }

    /** 저장된 카운트다운이 계약 범위 안인가. 0 은 "전환 중 아님"이다. */
    public static boolean conversionMcTicksValid(int value) {
        return value >= 0 && value <= CONVERSION_MAX_MC_TICKS;
    }

    /** 철창·침대인가. 바닐라 {@code isOf(IRON_BARS) || block instanceof BedBlock}. */
    public static boolean acceleratorBlock(short blockId) {
        return blockId == Blocks.IRON_BARS || Blocks.isBed(blockId);
    }

    /**
     * 게임 틱 1회의 감소량. 자기 위치 기준 상자를 바닐라와 같은 x→y→z 순서로 훑는다.
     * 난수 소비 순서도 같다: 상자 진입 1회 → 자격 블록마다 1회.
     */
    public static int conversionRate(MobRandom rng, MobWorldView world,
            double x, double y, double z) {
        int rate = CONVERSION_BASE_RATE;
        if (rng.nextFloat() >= CONVERSION_SCAN_CHANCE) return rate;
        int counted = 0;
        int originX = (int) x;
        int originY = (int) y;
        int originZ = (int) z;
        for (int bx = originX - CONVERSION_SCAN_RADIUS;
                bx < originX + CONVERSION_SCAN_RADIUS && counted < CONVERSION_SCAN_MAX_BLOCKS;
                bx++) {
            for (int by = originY - CONVERSION_SCAN_RADIUS;
                    by < originY + CONVERSION_SCAN_RADIUS && counted < CONVERSION_SCAN_MAX_BLOCKS;
                    by++) {
                for (int bz = originZ - CONVERSION_SCAN_RADIUS;
                        bz < originZ + CONVERSION_SCAN_RADIUS
                                && counted < CONVERSION_SCAN_MAX_BLOCKS;
                        bz++) {
                    if (!acceleratorBlock(world.getBlock(bx, by, bz))) continue;
                    if (rng.nextFloat() < CONVERSION_BLOCK_CHANCE) rate++;
                    counted++;
                }
            }
        }
        return rate;
    }
}
