package com.gameexpert.engine.mob.villager;

/**
 * 주민 사회 기억(gossip)과 그것이 거래 가격에 반영되는 방식을 담은 상태 없는 규칙이다.
 *
 * <p>바닐라 근거는 Minecraft Java 1.21.4 의
 * {@code net.minecraft.world.entity.ai.gossip.GossipType} /
 * {@code GossipContainer} / {@code Villager#onReputationEventFrom} /
 * {@code Villager#updateSpecialPrices} / {@code MerchantOffer#getCostA} 이다.
 * 상태와 RNG 는 {@link VillagerGossips} 및 호출자가 소유하고, 여기에는 순수 판정만 둔다.
 */
public final class VillagerGossipRules {

    /**
     * 다섯 gossip 종류. 생성자 인자는 바닐라 {@code GossipType(id, weight, max, decayPerDay,
     * decayPerTransfer)} 와 같은 순서다.
     *
     * <ul>
     *   <li>{@code weight}: 평판 합산 계수(음수면 값이 커질수록 평판이 나빠진다).</li>
     *   <li>{@code max}: 한 플레이어에 대해 이 종류가 가질 수 있는 상한.</li>
     *   <li>{@code decayPerDay}: 24000틱마다 빠지는 양. MAJOR_POSITIVE 는 0이라 영구히 남는다.</li>
     *   <li>{@code decayPerTransfer}: 다른 주민에게 전파될 때 깎이는 양. MAJOR_POSITIVE 는
     *       상한과 같은 20이라 전파 후 값이 항상 임계값 미만이 되어 결코 퍼지지 않는다.</li>
     * </ul>
     */
    public enum GossipType {
        MAJOR_NEGATIVE(-5, 100, 10, 10),
        MINOR_NEGATIVE(-1, 200, 20, 20),
        MINOR_POSITIVE(1, 25, 1, 5),
        MAJOR_POSITIVE(5, 20, 0, 20),
        TRADING(1, 25, 2, 20);

        private final int weight;
        private final int max;
        private final int decayPerDay;
        private final int decayPerTransfer;

        GossipType(int weight, int max, int decayPerDay, int decayPerTransfer) {
            this.weight = weight;
            this.max = max;
            this.decayPerDay = decayPerDay;
            this.decayPerTransfer = decayPerTransfer;
        }

        public int weight() { return weight; }

        public int max() { return max; }

        public int decayPerDay() { return decayPerDay; }

        public int decayPerTransfer() { return decayPerTransfer; }
    }

    /**
     * 평판을 움직이는 계기. 각 계기가 어떤 종류를 몇 점 올리는지는
     * {@code Villager#onReputationEventFrom} 과 같다.
     */
    public enum ReputationEvent {
        /** 좀비 주민 치료: MAJOR_POSITIVE +20 과 MINOR_POSITIVE +25 를 함께 얻는다. */
        ZOMBIE_VILLAGER_CURED,
        /** 거래 한 번마다 TRADING +2. */
        TRADE,
        /** 주민(아기 포함) 공격: MINOR_NEGATIVE +25. */
        VILLAGER_HURT,
        /** 주민(아기 포함) 살해: MAJOR_NEGATIVE +25. */
        VILLAGER_KILLED,
        /** 철 골렘 살해: MAJOR_NEGATIVE +25. */
        GOLEM_KILLED
    }

    /** 값이 이 미만이 되면 항목을 버린다. 바닐라 {@code GossipContainer} 의 2와 같다. */
    public static final int DISCARD_THRESHOLD = 2;

    /** 감쇠 주기. 24000틱(=1 MC일)마다 한 번 {@code decay()} 가 돈다. */
    public static final long DECAY_INTERVAL_TICKS = 24_000L;

    /** 두 주민이 다시 gossip 을 나눌 수 있게 되기까지의 간격. */
    public static final long GOSSIP_COOLDOWN_TICKS = 1_200L;

    /** 한 번 만났을 때 상대에게서 뽑아오는 항목 추첨 횟수({@code transferFrom(..., 10)}). */
    public static final int GOSSIP_TRANSFER_DRAWS = 10;

    /**
     * 두 주민이 gossip 을 나누는 거리. 바닐라 MEET 묶음의
     * {@code InteractWith.of(EntityType.VILLAGER, 8, INTERACTION_TARGET, speed, 2)} 에서
     * 실제로 상호작용이 성립하는 마지막 인자(2블록)다. 8은 후보를 찾는 반경이라 여기서는 쓰지
     * 않는다(후보 탐색 없이 매 틱 가까운 짝을 직접 본다).
     */
    public static final int GOSSIP_INTERACTION_RANGE_BLOCKS = 2;

    /**
     * 살해·피격을 목격해 악평을 함께 얻는 주민의 범위.
     * {@code NearestLivingEntitySensor} 가 쓰는 상자라 구형이 아니라 각 축 ±16 정육면체다.
     */
    public static final int WITNESS_BOX_RADIUS_BLOCKS = 16;

    private VillagerGossipRules() {}

    /** 이 계기가 올리는 종류들. 치료만 두 종류를 함께 올린다. */
    public static GossipType[] gossipTypesFor(ReputationEvent event) {
        return switch (event) {
            case ZOMBIE_VILLAGER_CURED ->
                    new GossipType[] { GossipType.MAJOR_POSITIVE, GossipType.MINOR_POSITIVE };
            case TRADE -> new GossipType[] { GossipType.TRADING };
            case VILLAGER_HURT -> new GossipType[] { GossipType.MINOR_NEGATIVE };
            case VILLAGER_KILLED, GOLEM_KILLED -> new GossipType[] { GossipType.MAJOR_NEGATIVE };
        };
    }

    /** 이 계기가 그 종류에 더하는 점수. 해당 없으면 0이다. */
    public static int gossipGain(ReputationEvent event, GossipType type) {
        return switch (event) {
            case ZOMBIE_VILLAGER_CURED -> type == GossipType.MAJOR_POSITIVE ? 20
                    : type == GossipType.MINOR_POSITIVE ? 25 : 0;
            case TRADE -> type == GossipType.TRADING ? 2 : 0;
            case VILLAGER_HURT -> type == GossipType.MINOR_NEGATIVE ? 25 : 0;
            case VILLAGER_KILLED, GOLEM_KILLED -> type == GossipType.MAJOR_NEGATIVE ? 25 : 0;
        };
    }

    /** 상한을 넘기지 않게 더한다. 이미 상한을 넘긴 값은 줄이지 않는다. */
    public static int mergeForAddition(GossipType type, int current, int added) {
        long sum = (long) current + added;
        if (sum > type.max()) return Math.max(current, type.max());
        return (int) sum;
    }

    /** 전파로 받은 값은 기존 값과 큰 쪽을 남긴다. */
    public static int mergeForTransfer(int current, int incoming) {
        return Math.max(current, incoming);
    }

    /** 저장·유지 대상인가. 임계값 미만이면 버린다. */
    public static boolean retains(int value) {
        return value >= DISCARD_THRESHOLD;
    }

    /** 상한으로 자른 뒤 임계값 미만이면 0(=삭제)으로 만든다. */
    public static int clampStoredValue(GossipType type, int value) {
        int capped = Math.min(value, type.max());
        return retains(capped) ? capped : 0;
    }

    /** 하루치 감쇠 후 값. 임계값 미만이면 0(=삭제)이다. */
    public static int decayedValue(GossipType type, int value) {
        int decayed = value - type.decayPerDay();
        return retains(decayed) ? decayed : 0;
    }

    /** 전파 시 상대가 받는 값. 임계값 미만이면 0(=전파되지 않음)이다. */
    public static int transferredValue(GossipType type, int value) {
        int transferred = value - type.decayPerTransfer();
        return retains(transferred) ? transferred : 0;
    }

    /** 항목 하나의 평판 기여분. */
    public static int weightedValue(GossipType type, int value) {
        return value * type.weight();
    }

    /** 감쇠 시각이 되었는가. 시계가 뒤로 가면 돌지 않는다. */
    public static boolean decayDue(long gameTime, long lastDecayTime) {
        if (gameTime < lastDecayTime) return false;
        if (lastDecayTime > Long.MAX_VALUE - DECAY_INTERVAL_TICKS) return false;
        return gameTime >= lastDecayTime + DECAY_INTERVAL_TICKS;
    }

    /**
     * 두 주민이 지금 gossip 을 나눌 수 있는가. 바닐라와 같이 양쪽 모두 쿨다운이 끝나 있어야 하며,
     * 아직 한 번도 나눈 적 없는 주민({@code Long.MIN_VALUE})은 항상 자격이 있다.
     */
    public static boolean gossipExchangeDue(long gameTime, long selfLast, long otherLast) {
        return cooldownElapsed(gameTime, selfLast) && cooldownElapsed(gameTime, otherLast);
    }

    private static boolean cooldownElapsed(long gameTime, long lastTime) {
        if (lastTime == Long.MIN_VALUE) return true;
        if (gameTime < lastTime) return true;
        if (lastTime > Long.MAX_VALUE - GOSSIP_COOLDOWN_TICKS) return false;
        return gameTime >= lastTime + GOSSIP_COOLDOWN_TICKS;
    }

    /** 두 주민이 서로 gossip 사정거리 안인가. */
    public static boolean withinGossipRange(double distanceSquared) {
        return distanceSquared
                <= (double) GOSSIP_INTERACTION_RANGE_BLOCKS * GOSSIP_INTERACTION_RANGE_BLOCKS;
    }

    /**
     * 만난 두 주민 중 <b>작은 id 쪽이 듣는가</b>(=상대의 기억을 받아오는가).
     *
     * <p>바닐라 {@code Villager#gossip} 은 그 틱 먼저 행동한 주민이 상대에게서 받아오고 쿨다운은
     * 양쪽 모두 갱신되므로 방향은 엔티티 틱 순서에 달려 있다. WebCraft 는 두 권위가 같은 결론을
     * 내야 하므로 그 순서 대신 (gameTime, 두 id) 에서 결정적으로 뽑는다. 방향이 한쪽으로 굳으면
     * 큰 id 주민이 영원히 아무것도 배우지 못하므로 매 만남마다 다시 뽑는다.
     */
    public static boolean lowerIdListens(long gameTime, long lowerId, long higherId) {
        int coin = mix32(mix32((int) gameTime ^ 0x5bf03635)
                ^ mix32((int) lowerId) ^ mix32((int) higherId * 3));
        return (coin & 1) == 0;
    }

    /**
     * 전파 추첨용 난수.
     *
     * <p>WebCraft divergence: 바닐라는 {@code villager.random} 을 쓰지만 두 권위가 각자의 난수
     * lane 을 갖고 있어 같은 항목이 옮겨진다는 보장이 없다. 그래서 POI 백오프 지터와 같은 방식
     * 으로 (gameTime, 두 mob id) 에서 결정적으로 뽑는다. 시퀀스는 구조물 점유 명단이 쓰는 것과
     * 같은 32비트 mix 이며 Java/TypeScript 가 같은 값을 낸다.
     */
    public static VillagerGossips.IntBound transferRandom(
            long gameTime, long firstId, long secondId) {
        int[] state = { mix32(mix32((int) gameTime) ^ mix32((int) firstId)
                ^ mix32((int) secondId)) };
        return bound -> {
            state[0] = mix32(state[0] + 0x6d2b79f5);
            return Integer.remainderUnsigned(state[0], bound);
        };
    }

    private static int mix32(int value) {
        int mixed = value;
        mixed = (mixed ^ mixed >>> 16) * 0x7feb352d;
        mixed = (mixed ^ mixed >>> 15) * 0x846ca68b;
        return mixed ^ mixed >>> 16;
    }

    /** 목격자 판정. 구가 아니라 각 축 ±16 상자다. */
    public static boolean withinWitnessBox(double dx, double dy, double dz) {
        return Math.abs(dx) <= WITNESS_BOX_RADIUS_BLOCKS
                && Math.abs(dy) <= WITNESS_BOX_RADIUS_BLOCKS
                && Math.abs(dz) <= WITNESS_BOX_RADIUS_BLOCKS;
    }

    /**
     * 평판이 만드는 특별 가격 차. 바닐라 {@code Villager#updateSpecialPrices} 의
     * {@code -Mth.floor(reputation * priceMultiplier)} 이며 음수가 할인이다. 바닐라는 float 를
     * 쓰지만 TypeScript 권위가 double 뿐이라 두 권위가 갈리지 않도록 여기서도 double 로 계산한다
     * (MC-REFERENCE divergence 계약).
     */
    public static int reputationPriceDiff(int reputation, double priceMultiplier) {
        if (reputation == 0) return 0;
        return -floor((double) reputation * priceMultiplier);
    }

    /**
     * ③ 거래 트랙의 {@code VillagerTradeRules} 는 가격 계수를 1/1000 정수
     * ({@code priceMultiplierMilli}) 로 들고 있다. 부동소수를 아예 태우지 않는 이 형태가
     * 두 권위에서 항상 같은 값을 내므로 배선에는 이쪽을 쓴다.
     */
    public static int reputationPriceDiffMilli(int reputation, int priceMultiplierMilli) {
        if (reputation == 0) return 0;
        return (int) -Math.floorDiv((long) reputation * priceMultiplierMilli, 1_000L);
    }

    /** 평판 할인과 영웅 할인을 합친 정수 전용 특별 가격 차. */
    public static int specialPriceDiffMilli(
            int reputation, int priceMultiplierMilli, int heroAmplifier, int baseCostCount) {
        int diff = reputationPriceDiffMilli(reputation, priceMultiplierMilli);
        if (heroAmplifier >= 0) diff += heroOfTheVillagePriceDiff(heroAmplifier, baseCostCount);
        return diff;
    }

    /**
     * 마을의 영웅(주민 구조 보상)이 만드는 할인. {@code amplifier} 는 0부터이며,
     * 효과가 없으면 이 함수를 부르지 않는다. 항상 최소 1은 깎인다.
     */
    public static int heroOfTheVillagePriceDiff(int amplifier, int baseCostCount) {
        double factor = 0.3 + 0.0625 * amplifier;
        int discount = floor(factor * baseCostCount);
        return -Math.max(discount, 1);
    }

    /**
     * 두 할인을 합친 특별 가격 차. {@code heroAmplifier} 가 음수면 영웅 효과가 없다는 뜻이다.
     */
    public static int specialPriceDiff(
            int reputation, double priceMultiplier, int heroAmplifier, int baseCostCount) {
        int diff = reputationPriceDiff(reputation, priceMultiplier);
        if (heroAmplifier >= 0) diff += heroOfTheVillagePriceDiff(heroAmplifier, baseCostCount);
        return diff;
    }

    /** 수요 가산분. {@code MerchantOffer#getCostA} 와 같이 음수가 되지 않는다. */
    public static int demandSurcharge(int baseCostCount, int demand, double priceMultiplier) {
        return Math.max(0, floor((double) baseCostCount * demand * priceMultiplier));
    }

    /**
     * 실제로 요구되는 첫 번째 재료 개수.
     * {@code clamp(base + demandSurcharge + specialPriceDiff, 1, maxStackSize)} 다.
     */
    public static int effectiveCostCount(
            int baseCostCount, int demand, double priceMultiplier,
            int specialPriceDiff, int maxStackSize) {
        long total = (long) baseCostCount
                + demandSurcharge(baseCostCount, demand, priceMultiplier)
                + specialPriceDiff;
        if (total < 1L) return 1;
        return (int) Math.min(total, maxStackSize);
    }

    /** 평판만으로 계산한 최종 가격. ③트랙이 배선하기 전에도 값이 검증되도록 함께 둔다. */
    public static int effectiveCostCountForReputation(
            int baseCostCount, int demand, double priceMultiplier,
            int reputation, int heroAmplifier, int maxStackSize) {
        return effectiveCostCount(
                baseCostCount, demand, priceMultiplier,
                specialPriceDiff(reputation, priceMultiplier, heroAmplifier, baseCostCount),
                maxStackSize);
    }

    private static int floor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }
}
