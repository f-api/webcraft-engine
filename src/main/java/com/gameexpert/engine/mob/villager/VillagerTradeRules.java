package com.gameexpert.engine.mob.villager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;

/**
 * 주민 거래의 상태 없는 규칙이다. 오퍼 풀(직업 × 레벨), 레벨 문턱, 수요 기반 가격, 재입고 창을
 * 모두 순수 함수로만 판정한다. 상태(사용 횟수·수요·XP·재입고 시각)는
 * {@link VillagerTradeState} 가 소유하고, RNG 는 항상 호출자가 소비한다.
 *
 * <p>근거는 Minecraft Java 1.21.4 의 {@code VillagerTrades}, {@code MerchantOffer},
 * {@code VillagerData}, {@code Villager#shouldRestock/restock} 이다. 여기 tick 단위는
 * {@link VillagerBrainRules} 와 같은 <b>MC 20TPS tick</b> 이며, WebCraft 10TPS 권위는
 * 가상 tick 두 번으로 환산한다.
 *
 * <p>WebCraft divergence(문서 정본은 {@code docs/MC-REFERENCE.md} 「주민 거래」 절):
 * <ul>
 *   <li>바닐라 오퍼 중 <b>WebCraft 에 등록되지 않은 아이템</b>을 쓰는 행은 풀에서 제외했다.
 *       제외 목록은 MC-REFERENCE 가 전수 기록한다.</li>
 *   <li>무작위 인챈트를 굽는 listing({@code EnchantedItemForEmeralds},
 *       {@code EnchantedBookForEmeralds})과 염색 가죽({@code DyedArmorForEmeralds}),
 *       지도 listing 은 이번 트랙 범위 밖이라 풀에 없다.</li>
 *   <li>gossip/영웅 효과가 만드는 {@code specialPriceDiff} 는 아직 생산되지 않아 항상 0 이지만,
 *       가격 식 자체는 바닐라와 같은 자리에 그 항을 남겨 둔다.</li>
 * </ul>
 */
public final class VillagerTradeRules {

    /** 바닐라 직업. WebCraft 의 실제 직업 배정은 ① villager-poi 트랙이 소유한다. */
    public enum Profession {
        NONE, NITWIT,
        ARMORER, BUTCHER, CARTOGRAPHER, CLERIC, FARMER, FISHERMAN, FLETCHER,
        LEATHERWORKER, LIBRARIAN, MASON, SHEPHERD, TOOLSMITH, WEAPONSMITH, WANDERING_TRADER
    }

    /** 바닐라 최대 직업 레벨(NOVICE..MASTER). */
    public static final int MAX_LEVEL = 5;
    public static final int MIN_LEVEL = 1;

    /**
     * 현재 레벨에서 다음 레벨로 오르는 데 필요한 누적 거래 경험치다.
     * 인덱스는 현재 레벨이고 0번은 쓰지 않는다(바닐라 {@code VillagerData#getMaxXpPerLevel}).
     */
    public static final int[] XP_TO_NEXT_LEVEL = { 0, 10, 70, 150, 250 };

    /** 한 레벨에서 주민이 실제로 갖는 오퍼 수(바닐라 {@code addOffersFromItemListings(…, 2)}). */
    public static final int OFFERS_PER_LEVEL = 2;

    /** 바닐라 {@code MerchantOffer#priceMultiplier} 기본값 0.05 를 1/1000 정수로 담은 값이다. */
    public static final int PRICE_MULTIPLIER_MILLI = 50;
    private static final int PRICE_MULTIPLIER_SCALE = 1_000;

    /** 하루에 허용되는 재입고 횟수(바닐라 {@code Villager#allowedToRestock}). */
    public static final int MAX_RESTOCKS_PER_DAY = 2;
    /** 두 번째 재입고까지 필요한 최소 간격(MC tick). */
    public static final long RESTOCK_MIN_INTERVAL_TICKS = 2_400L;
    /** 이 간격을 넘기면 일일 재입고 카운터가 풀린다(MC tick). */
    public static final long RESTOCK_DAY_INTERVAL_TICKS = 12_000L;
    /** 하루 경계 판정에 쓰는 MC 하루 길이. */
    public static final long DAY_LENGTH_TICKS = VillagerBrainRules.DAY_LENGTH_TICKS;

    /** 거래가 성립하려면 오퍼가 요구하는 두 번째 비용이 없다는 뜻의 빈 아이템. */
    public static final short NO_ITEM = PlayerInventory.EMPTY;

    /**
     * 오퍼 한 줄. {@code costItem × costCount}(+ 선택적 {@code costBItem × costBCount}) 를 내고
     * {@code resultItem × resultCount} 를 받는다. 에메랄드를 파는 행은 결과가 에메랄드고,
     * 에메랄드를 받는 행은 비용이 에메랄드다.
     */
    public record Offer(
            short costItem, int costCount,
            short costBItem, int costBCount,
            short resultItem, int resultCount,
            int maxUses, int xp, int priceMultiplierMilli,
            PlayerInventory.StackSnapshot costPrototype,
            PlayerInventory.StackSnapshot costBPrototype,
            PlayerInventory.StackSnapshot resultPrototype) {

        public Offer(short costItem,int costCount,short costBItem,int costBCount,short resultItem,int resultCount,int maxUses,int xp,int priceMultiplierMilli) {
            this(costItem,costCount,costBItem,costBCount,resultItem,resultCount,maxUses,xp,priceMultiplierMilli,
                    VillagerTradeStacks.plain(costItem),VillagerTradeStacks.plain(costBItem),VillagerTradeStacks.plain(resultItem));
        }
        public Offer {
            if(costPrototype==null || costBPrototype==null || resultPrototype==null
                    || costPrototype.itemType()!=costItem || costBPrototype.itemType()!=costBItem || resultPrototype.itemType()!=resultItem) throw new IllegalArgumentException("offer stack prototype identity mismatch");
            if (costCount <= 0 || resultCount <= 0 || maxUses <= 0 || xp < 0) {
                throw new IllegalArgumentException("오퍼 수치는 양수여야 합니다.");
            }
            if (costBItem != NO_ITEM && costBCount <= 0) {
                throw new IllegalArgumentException("두 번째 비용은 개수가 있어야 합니다.");
            }
        }

        public boolean hasSecondCost() {
            return costBItem != NO_ITEM;
        }
    }

    private VillagerTradeRules() {
    }

    // ── 레벨·경험치 ────────────────────────────────────────────────────────────

    /** 바닐라 {@code VillagerData#canLevelUp}: 1 이상 5 미만에서만 승급 가능하다. */
    public static boolean canLevelUp(int level) {
        return level >= MIN_LEVEL && level < MAX_LEVEL;
    }

    /** 현재 레벨에서 승급에 필요한 누적 XP. 승급 불가 레벨이면 0 이다. */
    public static int xpToNextLevel(int level) {
        return canLevelUp(level) ? XP_TO_NEXT_LEVEL[level] : 0;
    }

    /** 바닐라 {@code Villager#shouldIncreaseLevel}. */
    public static boolean shouldIncreaseLevel(int level, int totalXp) {
        return canLevelUp(level) && totalXp >= xpToNextLevel(level);
    }

    /** 거래 한 번이 주는 XP 는 오퍼가 갖고 있다. 여기서는 누적 상한만 없다는 사실을 고정한다. */
    public static int addTradeXp(int totalXp, int offerXp) {
        long sum = (long) totalXp + Math.max(0, offerXp);
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    // ── 수요·가격 ─────────────────────────────────────────────────────────────

    /** 바닐라 {@code MerchantOffer#updateDemand}: 재입고 시점에 사용/잔여로 수요를 갱신한다. */
    public static int updatedDemand(int demand, int uses, int maxUses) {
        return demand + uses - (maxUses - uses);
    }

    /**
     * 바닐라 {@code MerchantOffer#getModifiedCostCount} 와 같은 값이다.
     * {@code clamp(base + floor(base × demand × 0.05) + specialPriceDiff, 1, stackMax)} 이며,
     * 0.05f 가 0.05 보다 크므로 유리수 floor 와 float floor 가 갈리지 않는다(정수 산술로 고정).
     */
    public static int discountedCostCount(
            int baseCount, int demand, int priceMultiplierMilli, int specialPriceDiff, int stackMax) {
        long scaled = (long) baseCount * demand * priceMultiplierMilli;
        long surcharge = Math.max(0L, Math.floorDiv(scaled, PRICE_MULTIPLIER_SCALE));
        long total = (long) baseCount + surcharge + specialPriceDiff;
        if (total < 1L) return 1;
        return total > stackMax ? stackMax : (int) total;
    }

    /** 오퍼 한 줄의 현재 요구 개수. 두 번째 비용은 수요 보정을 받지 않는다(바닐라와 같다). */
    public static int currentCostCount(Offer offer, int demand, int specialPriceDiff) {
        return discountedCostCount(
                offer.costCount(), demand, offer.priceMultiplierMilli(), specialPriceDiff,
                PlayerInventory.stackMax(offer.costItem()));
    }

    // ── 재고·재입고 ───────────────────────────────────────────────────────────

    public static boolean outOfStock(int uses, int maxUses) {
        return uses >= maxUses;
    }

    /** 바닐라 {@code MerchantOffer#needsRestock}. */
    public static boolean needsRestock(int uses) {
        return uses > 0;
    }

    /** 바닐라 {@code Villager#allowedToRestock}. */
    public static boolean allowedToRestock(
            int restocksToday, long gameTime, long lastRestockGameTime) {
        if (restocksToday == 0) return true;
        return restocksToday < MAX_RESTOCKS_PER_DAY
                && gameTime > lastRestockGameTime + RESTOCK_MIN_INTERVAL_TICKS;
    }

    /**
     * 바닐라 {@code Villager#shouldRestock} 의 일일 카운터 해제 조건이다.
     * 마지막 재입고에서 12000 tick 이 지났거나, 마지막 확인 이후 하루 경계를 넘었으면 푼다.
     */
    public static boolean dailyRestockCounterExpired(
            long gameTime, long lastRestockGameTime,
            long dayTime, long lastRestockCheckDayTime) {
        if (gameTime > lastRestockGameTime + RESTOCK_DAY_INTERVAL_TICKS) return true;
        if (lastRestockCheckDayTime <= 0L) return false;
        return Math.floorDiv(dayTime, DAY_LENGTH_TICKS)
                > Math.floorDiv(lastRestockCheckDayTime, DAY_LENGTH_TICKS);
    }

    // ── 오퍼 선택 ─────────────────────────────────────────────────────────────

    /**
     * 바닐라 {@code Villager#addOffersFromItemListings}: 풀에서 무작위로 하나씩 <b>제거</b>하며
     * 최대 {@code count} 개를 뽑는다. RNG 는 호출자가 소유하고 여기서는 그 결과만 소비한다.
     * 반환값은 원래 풀에서의 인덱스이며 뽑은 순서를 보존한다.
     */
    public static int[] pickOfferIndices(int poolSize, int count, IntUnaryOperator nextInt) {
        if (poolSize <= 0 || count <= 0) return new int[0];
        List<Integer> remaining = new ArrayList<>(poolSize);
        for (int index = 0; index < poolSize; index++) remaining.add(index);
        int taken = Math.min(count, poolSize);
        int[] picked = new int[taken];
        for (int slot = 0; slot < taken; slot++) {
            int bound = remaining.size();
            int draw = nextInt.applyAsInt(bound);
            if (draw < 0 || draw >= bound) {
                throw new IllegalArgumentException("오퍼 추첨 값이 [0," + bound + ") 밖입니다.");
            }
            picked[slot] = remaining.remove(draw);
        }
        return picked;
    }

    /** 오퍼 추첨 salt. 바꾸면 아직 오퍼를 뽑지 않은 주민만 달라진다(뽑은 오퍼는 영속된다). */
    private static final int OFFER_DRAW_SALT = 0x7b3d5f11;

    /**
     * 주민 id · 레벨 · 순번으로 결정되는 추첨 값이다. 양 권위가 같은 주민에게 같은 오퍼를 주도록
     * 월드 RNG 대신 이 순수 해시를 쓴다({@code GlitchSignalPolicy} 와 같은 방식).
     */
    public static int offerDraw(long villagerId, int level, int step, int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound는 양수여야 합니다.");
        int state = mix((int) villagerId ^ (int) (villagerId >>> 32) ^ OFFER_DRAW_SALT);
        state = mix(state + level * 0x9e3779b1);
        state = mix(state + (step + 1) * 0x6d2b79f5);
        return Integer.remainderUnsigned(state, bound);
    }

    /** {@link #offerDraw} 를 순번 0부터 차례로 소비하는 추첨기를 만든다. */
    public static IntUnaryOperator offerDraws(long villagerId, int level) {
        int[] step = { 0 };
        return bound -> offerDraw(villagerId, level, step[0]++, bound);
    }

    private static int mix(int value) {
        int mixed = value;
        mixed = (mixed ^ mixed >>> 16) * 0x7feb352d;
        mixed = (mixed ^ mixed >>> 15) * 0x846ca68b;
        return mixed ^ mixed >>> 16;
    }

    // ── 오퍼 표 ───────────────────────────────────────────────────────────────

    /** 직업·레벨별 오퍼 풀. 레벨은 1..5 이고, 정의가 없으면 빈 배열이다. */
    public static Offer[] offerPool(Profession profession, int level) {
        if (level < MIN_LEVEL || level > MAX_LEVEL) return new Offer[0];
        Offer[][] byLevel = POOLS[profession.ordinal()];
        return byLevel[level - MIN_LEVEL].clone();
    }

    /** 직업이 거래를 하는지. NONE/NITWIT 은 바닐라와 같이 오퍼가 없다. */
    public static boolean tradesAtAll(Profession profession) {
        for (int level = MIN_LEVEL; level <= MAX_LEVEL; level++) {
            if (offerPool(profession, level).length > 0) return true;
        }
        return false;
    }

    private static final short EMERALD = PlayerInventory.EMERALD;

    /** 아이템 n 개를 에메랄드 1 개로 사들이는 행. */
    private static Offer buy(short item, int count, int maxUses, int xp) {
        return new Offer(item, count, NO_ITEM, 0, EMERALD, 1, maxUses, xp, PRICE_MULTIPLIER_MILLI);
    }

    /** 에메랄드 n 개로 아이템 m 개를 파는 행. */
    private static Offer sell(int emeralds, short item, int count, int maxUses, int xp) {
        return new Offer(EMERALD, emeralds, NO_ITEM, 0, item, count, maxUses, xp,
                PRICE_MULTIPLIER_MILLI);
    }

    /** 재료 + 에메랄드 1 개로 가공품을 파는 행(바닐라 {@code ItemsAndEmeraldsToItems}). */
    private static Offer convert(
            short from, int fromCount, short to, int toCount, int maxUses, int xp) {
        return new Offer(from, fromCount, EMERALD, 1, to, toCount, maxUses, xp,
                PRICE_MULTIPLIER_MILLI);
    }

    private static final Offer[] NONE_LEVEL = new Offer[0];

    private static Offer[][] levels(Offer[] one, Offer[] two, Offer[] three, Offer[] four, Offer[] five) {
        return new Offer[][] { one, two, three, four, five };
    }

    private static Offer[][] noTrades() {
        return levels(NONE_LEVEL, NONE_LEVEL, NONE_LEVEL, NONE_LEVEL, NONE_LEVEL);
    }

    private static final Offer[][][] POOLS = new Offer[Profession.values().length][][];

    static {
        POOLS[Profession.NONE.ordinal()] = noTrades();
        POOLS[Profession.NITWIT.ordinal()] = noTrades();

        POOLS[Profession.FARMER.ordinal()] = levels(
                new Offer[] {
                        buy(PlayerInventory.WHEAT, 20, 16, 2),
                        buy(PlayerInventory.POTATO, 26, 16, 2),
                        buy(PlayerInventory.CARROT, 22, 16, 2),
                        buy(PlayerInventory.BEETROOT, 15, 16, 2),
                        sell(1, PlayerInventory.BREAD, 6, 16, 1),
                },
                new Offer[] {
                        buy((short) Blocks.PUMPKIN, 6, 12, 10),
                        sell(1, PlayerInventory.APPLE, 4, 16, 5),
                },
                NONE_LEVEL,
                NONE_LEVEL,
                new Offer[] {
                        sell(3, PlayerInventory.GOLDEN_CARROT, 3, 12, 30),
                });

        POOLS[Profession.FISHERMAN.ordinal()] = levels(
                new Offer[] {
                        buy(PlayerInventory.STRING, 20, 16, 2),
                        buy(PlayerInventory.COAL, 10, 16, 2),
                        convert(PlayerInventory.COD_RAW, 6, PlayerInventory.COD_COOKED, 6, 16, 1),
                        sell(3, PlayerInventory.COD_BUCKET, 1, 16, 1),
                },
                new Offer[] {
                        buy(PlayerInventory.COD_RAW, 15, 16, 10),
                        convert(PlayerInventory.SALMON_RAW, 6, PlayerInventory.SALMON_COOKED, 6, 16, 5),
                        sell(2, (short) Blocks.CAMPFIRE, 1, 12, 5),
                },
                new Offer[] {
                        buy(PlayerInventory.SALMON_RAW, 13, 16, 20),
                },
                new Offer[] {
                        buy(PlayerInventory.TROPICAL_FISH, 6, 12, 30),
                },
                new Offer[] {
                        buy(PlayerInventory.PUFFERFISH, 4, 12, 30),
                });

        POOLS[Profession.SHEPHERD.ordinal()] = levels(
                new Offer[] {
                        buy(PlayerInventory.WOOL, 18, 16, 2),
                        sell(2, PlayerInventory.SHEARS, 1, 12, 1),
                },
                dyeBuyPool(),
                NONE_LEVEL,
                NONE_LEVEL,
                NONE_LEVEL);

        POOLS[Profession.FLETCHER.ordinal()] = levels(
                new Offer[] {
                        buy(PlayerInventory.STICK, 32, 16, 2),
                        sell(1, PlayerInventory.ARROW, 16, 12, 1),
                        convert((short) Blocks.GRAVEL, 10, PlayerInventory.FLINT, 10, 12, 1),
                },
                new Offer[] {
                        buy(PlayerInventory.FLINT, 26, 12, 10),
                        sell(2, PlayerInventory.BOW, 1, 12, 5),
                },
                new Offer[] {
                        buy(PlayerInventory.STRING, 14, 16, 20),
                },
                new Offer[] {
                        buy(PlayerInventory.FEATHER, 24, 16, 30),
                },
                new Offer[] {
                        buy(PlayerInventory.TRIPWIRE_HOOK, 8, 12, 30),
                });

        POOLS[Profession.LIBRARIAN.ordinal()] = levels(
                new Offer[] {
                        buy(PlayerInventory.PAPER, 24, 16, 2),
                        sell(9, (short) Blocks.BOOKSHELF, 1, 12, 1),
                },
                new Offer[] {
                        buy(PlayerInventory.BOOK, 4, 12, 10),
                },
                new Offer[] {
                        buy(PlayerInventory.INK_SAC, 20, 12, 20),
                        sell(1, (short) Blocks.GLASS, 4, 12, 10),
                },
                new Offer[] {
                        sell(5, PlayerInventory.CLOCK, 1, 12, 15),
                        sell(4, PlayerInventory.COMPASS, 1, 12, 15),
                },
                new Offer[] {
                        sell(20, PlayerInventory.NAME_TAG, 1, 12, 30),
                });

        POOLS[Profession.CARTOGRAPHER.ordinal()] = levels(
                new Offer[] {
                        buy(PlayerInventory.PAPER, 24, 16, 2),
                        sell(7, PlayerInventory.MAP, 1, 12, 1),
                },
                new Offer[] {
                        buy((short) Blocks.GLASS_PANE, 11, 12, 10),
                },
                NONE_LEVEL,
                NONE_LEVEL,
                NONE_LEVEL);

        POOLS[Profession.CLERIC.ordinal()] = levels(
                new Offer[] {
                        buy(PlayerInventory.ROTTEN_FLESH, 32, 16, 2),
                        sell(1, PlayerInventory.REDSTONE_DUST, 2, 12, 1),
                },
                new Offer[] {
                        buy(PlayerInventory.GOLD_INGOT, 36, 12, 10),
                        sell(1, PlayerInventory.LAPIS_LAZULI, 1, 12, 5),
                },
                new Offer[] {
                        buy(PlayerInventory.RABBIT_FOOT, 4, 12, 20),
                },
                new Offer[] {
                        buy(PlayerInventory.GLASS_BOTTLE, 16, 12, 30),
                        sell(5, PlayerInventory.ENDER_PEARL, 1, 12, 15),
                },
                NONE_LEVEL);

        POOLS[Profession.ARMORER.ordinal()] = levels(
                new Offer[] {
                        buy(PlayerInventory.COAL, 15, 16, 2),
                        sell(5, PlayerInventory.IRON_HELMET, 1, 12, 1),
                        sell(9, PlayerInventory.IRON_CHESTPLATE, 1, 12, 1),
                        sell(7, PlayerInventory.IRON_LEGGINGS, 1, 12, 1),
                        sell(4, PlayerInventory.IRON_BOOTS, 1, 12, 1),
                },
                new Offer[] {
                        buy(PlayerInventory.IRON_INGOT, 4, 12, 10),
                        sell(1, PlayerInventory.CHAINMAIL_BOOTS, 1, 12, 5),
                        sell(3, PlayerInventory.CHAINMAIL_LEGGINGS, 1, 12, 5),
                },
                new Offer[] {
                        buy(PlayerInventory.LAVA_BUCKET, 1, 12, 20),
                        buy(PlayerInventory.DIAMOND, 1, 12, 20),
                        sell(1, PlayerInventory.CHAINMAIL_HELMET, 1, 12, 10),
                        sell(4, PlayerInventory.CHAINMAIL_CHESTPLATE, 1, 12, 10),
                        sell(5, PlayerInventory.SHIELD, 1, 12, 10),
                },
                NONE_LEVEL,
                NONE_LEVEL);

        POOLS[Profession.TOOLSMITH.ordinal()] = levels(
                new Offer[] {
                        buy(PlayerInventory.COAL, 15, 16, 2),
                        sell(1, PlayerInventory.STONE_AXE, 1, 12, 1),
                        sell(1, PlayerInventory.STONE_SHOVEL, 1, 12, 1),
                        sell(1, PlayerInventory.STONE_PICKAXE, 1, 12, 1),
                },
                new Offer[] {
                        buy(PlayerInventory.IRON_INGOT, 4, 12, 10),
                },
                new Offer[] {
                        buy(PlayerInventory.FLINT, 30, 12, 20),
                },
                new Offer[] {
                        buy(PlayerInventory.DIAMOND, 1, 12, 30),
                },
                NONE_LEVEL);

        POOLS[Profession.WEAPONSMITH.ordinal()] = levels(
                new Offer[] {
                        buy(PlayerInventory.COAL, 15, 16, 2),
                        sell(3, PlayerInventory.IRON_AXE, 1, 12, 1),
                },
                new Offer[] {
                        buy(PlayerInventory.IRON_INGOT, 4, 12, 10),
                },
                NONE_LEVEL,
                new Offer[] {
                        buy(PlayerInventory.DIAMOND, 1, 12, 30),
                },
                NONE_LEVEL);

        POOLS[Profession.BUTCHER.ordinal()] = levels(
                new Offer[] {
                        buy(PlayerInventory.CHICKEN_RAW, 14, 16, 2),
                        buy(PlayerInventory.PORK_RAW, 7, 16, 2),
                        sell(1, PlayerInventory.RABBIT_STEW, 1, 12, 1),
                },
                new Offer[] {
                        buy(PlayerInventory.COAL, 15, 16, 10),
                        sell(1, PlayerInventory.PORK_COOKED, 5, 16, 5),
                        sell(1, PlayerInventory.CHICKEN_COOKED, 8, 16, 5),
                },
                new Offer[] {
                        buy(PlayerInventory.MUTTON_RAW, 7, 16, 20),
                        buy(PlayerInventory.BEEF_RAW, 10, 16, 20),
                },
                NONE_LEVEL,
                NONE_LEVEL);

        POOLS[Profession.LEATHERWORKER.ordinal()] = levels(
                new Offer[] {
                        buy(PlayerInventory.LEATHER, 6, 16, 2),
                        sell(3, PlayerInventory.LEATHER_LEGGINGS, 1, 12, 1),
                },
                new Offer[] {
                        buy(PlayerInventory.FLINT, 26, 12, 10),
                },
                new Offer[] {
                        buy(PlayerInventory.RABBIT_HIDE, 9, 12, 20),
                },
                NONE_LEVEL,
                new Offer[] {
                        sell(6, PlayerInventory.SADDLE, 1, 12, 30),
                });

        POOLS[Profession.MASON.ordinal()] = levels(
                new Offer[] {
                        buy(PlayerInventory.CLAY_BALL, 10, 16, 2),
                },
                new Offer[] {
                        buy((short) Blocks.STONE, 20, 16, 10),
                },
                new Offer[] {
                        buy((short) Blocks.GRANITE, 16, 16, 20),
                        buy((short) Blocks.ANDESITE, 16, 16, 20),
                        buy((short) Blocks.DIORITE, 16, 16, 20),
                },
                NONE_LEVEL,
                NONE_LEVEL);

        POOLS[Profession.WANDERING_TRADER.ordinal()] = levels(WanderingTraderOffers.pool(), NONE_LEVEL, NONE_LEVEL, NONE_LEVEL, NONE_LEVEL);
        for (Profession profession : Profession.values()) {
            if (POOLS[profession.ordinal()] == null) {
                throw new IllegalStateException("직업 오퍼 표가 비어 있습니다: " + profession);
            }
        }
    }

    /** 양치기 2단계: 바닐라 16색 염료를 각각 16개씩 사들이는 풀이다. */
    private static Offer[] dyeBuyPool() {
        int first = Short.toUnsignedInt(PlayerInventory.WHITE_DYE);
        int last = Short.toUnsignedInt(PlayerInventory.BLACK_DYE);
        Offer[] pool = new Offer[last - first + 1];
        for (int id = first; id <= last; id++) {
            pool[id - first] = buy((short) id, 16, 12, 10);
        }
        return pool;
    }
}
