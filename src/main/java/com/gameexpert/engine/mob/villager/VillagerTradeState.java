package com.gameexpert.engine.mob.villager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntUnaryOperator;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.mob.villager.VillagerTradeRules.Offer;
import com.gameexpert.engine.mob.villager.VillagerTradeRules.Profession;

/**
 * 주민 한 명의 거래 상태다. 오퍼 목록(레벨별 2개), 오퍼별 사용 횟수·수요, 누적 거래 XP,
 * 재입고 시각·횟수를 소유하고 거래 실행 권위를 한 곳에서 확정한다.
 *
 * <p>이 클래스는 <b>월드/네트워크에 의존하지 않는다</b>. 권위(Spring 틱 루프 또는 standalone
 * 런타임)는 (1) 주민 참조와 거리 검증, (2) 직업 조회, (3) 결과 브로드캐스트만 담당하고
 * 재고·가격·아이템 이동은 전부 여기서 확정된다.
 *
 * <p>영속 경계는 {@link Snapshot} 이며 오퍼는 값이 아니라 <b>(레벨, 풀 인덱스)</b> 로 저장한다.
 * 오퍼 표가 append-only 로 늘어도 과거 주민의 오퍼가 그대로 복원된다.
 */
public final class VillagerTradeState {

    /** 거래 거절 사유. 프로토콜 error 코드로 그대로 나간다. */
    public enum Rejection {
        NO_PROFESSION, NO_SUCH_OFFER, LOCKED_LEVEL, OUT_OF_STOCK, INSUFFICIENT_ITEMS
    }

    /** 거래 한 번의 결과. {@code overflow} 는 인벤토리에 못 담아 바닥에 떨어질 몫이다. */
    public record TradeResult(
            boolean accepted, Rejection rejection,
            short resultItem, int resultCount, int paidCount, int paidSecondCount,
            int overflowCount, int gainedXp, boolean leveledUp,
            PlayerInventory.StackSnapshot resultPrototype) {
        public TradeResult(boolean accepted, Rejection rejection,short resultItem,int resultCount,int paidCount,int paidSecondCount,int overflowCount,int gainedXp,boolean leveledUp) {
            this(accepted,rejection,resultItem,resultCount,paidCount,paidSecondCount,overflowCount,gainedXp,leveledUp,VillagerTradeStacks.plain(resultItem));
        }

        public static TradeResult rejected(Rejection rejection) {
            return new TradeResult(false, rejection, PlayerInventory.EMPTY, 0, 0, 0, 0, 0, false);
        }
    }

    /** 영속 스냅샷. 배열 네 개는 항상 같은 길이이며 오퍼 순서를 보존한다. */
    public record Snapshot(
            String profession, int level, int totalXp,
            long lastRestockGameTime, long lastRestockCheckDayTime, int restocksToday,
            int[] offerLevels, int[] offerPoolIndices, int[] uses, int[] demand, int[] variantSeeds) {

        public Snapshot(String profession, int level, int totalXp, long lastRestockGameTime,
                long lastRestockCheckDayTime, int restocksToday, int[] offerLevels,
                int[] offerPoolIndices, int[] uses, int[] demand) {
            this(profession, level, totalXp, lastRestockGameTime, lastRestockCheckDayTime,
                    restocksToday, offerLevels, offerPoolIndices, uses, demand, new int[offerLevels.length]);
        }

        public Snapshot {
            if (offerLevels.length != offerPoolIndices.length
                    || offerLevels.length != uses.length
                    || offerLevels.length != demand.length || offerLevels.length != variantSeeds.length) {
                throw new IllegalArgumentException("오퍼 스냅샷 배열 길이가 다릅니다.");
            }
            offerLevels = offerLevels.clone();
            offerPoolIndices = offerPoolIndices.clone();
            uses = uses.clone();
            demand = demand.clone();
            variantSeeds = variantSeeds.clone();
        }

        @Override public int[] variantSeeds() { return variantSeeds.clone(); }

        @Override
        public int[] offerLevels() {
            return offerLevels.clone();
        }

        @Override
        public int[] offerPoolIndices() {
            return offerPoolIndices.clone();
        }

        @Override
        public int[] uses() {
            return uses.clone();
        }

        @Override
        public int[] demand() {
            return demand.clone();
        }
    }

    /** 주민이 실제로 들고 있는 오퍼 한 줄의 런타임 상태다. */
    public static final class Slot {
        private final int variantSeed;
        private final int level;
        private final int poolIndex;
        private final Offer offer;
        private int uses;
        private int demand;

        private Slot(int level, int poolIndex, Offer offer, int uses, int demand, int variantSeed) {
            this.variantSeed = variantSeed;
            this.level = level;
            this.poolIndex = poolIndex;
            this.offer = offer;
            this.uses = uses;
            this.demand = demand;
        }

        public int level() { return level; }
        public int poolIndex() { return poolIndex; }
        public Offer offer() { return offer; }
        public int uses() { return uses; }
        public int demand() { return demand; }
        public boolean outOfStock() { return VillagerTradeRules.outOfStock(uses, offer.maxUses()); }

        /** 수요 보정만 반영한 현재 1차 비용 개수다(평판 할인 없음). */
        public int currentCostCount() {
            return VillagerTradeRules.currentCostCount(offer, demand, 0);
        }

        /**
         * 평판·영웅 할인까지 반영한 실제 요구 개수. 바닐라 {@code MerchantOffer#getModifiedCostCount}
         * 와 같이 {@code specialPriceDiff} 는 수요 가산 뒤에 더해지고 1 미만으로 내려가지 않는다.
         */
        public int currentCostCount(int specialPriceDiff) {
            return VillagerTradeRules.currentCostCount(offer, demand, specialPriceDiff);
        }
    }

    private Profession profession;
    private int level = VillagerTradeRules.MIN_LEVEL;
    private int totalXp;
    private long lastRestockGameTime = Long.MIN_VALUE;
    private long lastRestockCheckDayTime;
    private int restocksToday;
    private final List<Slot> slots = new ArrayList<>();

    public VillagerTradeState(Profession profession) {
        this.profession = profession == null ? Profession.NONE : profession;
    }

    public Profession profession() { return profession; }
    public int level() { return level; }
    public int totalXp() { return totalXp; }
    public int restocksToday() { return restocksToday; }
    public long lastRestockGameTime() { return lastRestockGameTime; }
    public List<Slot> slots() { return List.copyOf(slots); }
    public int offerCount() { return slots.size(); }

    /**
     * 직업이 바뀌면 오퍼·레벨·XP 를 전부 버리고 1단계부터 새로 만든다
     * (바닐라 {@code Villager#setVillagerData} 의 직업 변경 경로).
     */
    public void assignProfession(Profession next, IntUnaryOperator nextInt) {
        this.profession = next == null ? Profession.NONE : next;
        this.level = VillagerTradeRules.MIN_LEVEL;
        this.totalXp = 0;
        this.slots.clear();
        this.restocksToday = 0;
        this.lastRestockGameTime = Long.MIN_VALUE;
        this.lastRestockCheckDayTime = 0L;
        ensureOffersForLevel(VillagerTradeRules.MIN_LEVEL, nextInt);
    }

    /**
     * 해당 레벨의 오퍼가 아직 없으면 풀에서 최대 {@code OFFERS_PER_LEVEL} 개를 뽑아 붙인다.
     * 이미 뽑아 둔 레벨은 다시 뽑지 않는다(바닐라도 승급 때 한 번만 붙인다).
     */
    public void ensureOffersForLevel(int targetLevel, IntUnaryOperator nextInt) {
        if (profession == Profession.WANDERING_TRADER && targetLevel != 1) return;
        for (Slot slot : slots) {
            if (slot.level == targetLevel) return;
        }
        Offer[] pool = VillagerTradeRules.offerPool(profession, targetLevel);
        int[] picked = profession == Profession.WANDERING_TRADER
                ? WanderingTraderOffers.pick(nextInt)
                : VillagerTradeRules.pickOfferIndices(pool.length, VillagerTradeRules.OFFERS_PER_LEVEL, nextInt);
        for (int index : picked) {
            int variantSeed = profession == Profession.WANDERING_TRADER ? nextInt.applyAsInt(Integer.MAX_VALUE) : 0;
            Offer offer = profession == Profession.WANDERING_TRADER
                    ? WanderingTraderOfferComponents.materialize(pool[index], index, variantSeed) : pool[index];
            slots.add(new Slot(targetLevel, index, offer, 0, 0, variantSeed));
        }
    }

    // ── 재입고 ────────────────────────────────────────────────────────────────

    /**
     * 바닐라 {@code Villager#shouldRestock}: 일일 카운터를 먼저 해제한 뒤 허용 여부를 돌려준다.
     * 이 호출은 {@code lastRestockCheckDayTime} 을 갱신하는 부수효과가 있다(바닐라와 같다).
     */
    public boolean shouldRestock(long gameTime, long dayTime) {
        if (profession == Profession.WANDERING_TRADER) return false;
        if (VillagerTradeRules.dailyRestockCounterExpired(
                gameTime, lastRestockGameTime, dayTime, lastRestockCheckDayTime)) {
            restocksToday = 0;
        }
        lastRestockCheckDayTime = dayTime;
        return VillagerTradeRules.allowedToRestock(restocksToday, gameTime, lastRestockGameTime);
    }

    /** 오퍼 중 하나라도 사용된 적이 있으면 재입고가 필요하다. */
    public boolean needsRestock() {
        for (Slot slot : slots) {
            if (VillagerTradeRules.needsRestock(slot.uses)) return true;
        }
        return false;
    }

    /**
     * 주민이 작업장에서 일할 때 호출한다. 수요를 먼저 갱신하고 사용 횟수를 0 으로 되돌린다
     * (바닐라 {@code Villager#restock} 순서 그대로).
     */
    public void restock(long gameTime) {
        if (profession == Profession.WANDERING_TRADER) return;
        for (Slot slot : slots) {
            slot.demand = VillagerTradeRules.updatedDemand(
                    slot.demand, slot.uses, slot.offer.maxUses());
        }
        for (Slot slot : slots) {
            slot.uses = 0;
        }
        lastRestockGameTime = gameTime;
        restocksToday++;
    }

    /** 작업 중인 주민의 한 틱: 재입고 조건을 모두 만족할 때만 실제로 재입고한다. */
    public boolean tickWorkAtJobSite(long gameTime, long dayTime) {
        if (!needsRestock()) {
            shouldRestock(gameTime, dayTime);
            return false;
        }
        if (!shouldRestock(gameTime, dayTime)) return false;
        restock(gameTime);
        return true;
    }

    // ── 거래 실행 권위 ────────────────────────────────────────────────────────

    /**
     * 클라이언트가 고른 오퍼 하나를 권위가 검증하고 인벤토리를 옮긴다.
     * 순서는 <b>재고 → 잠금 레벨 → 보유량(수요 보정 가격 기준) → 차감 → 지급 → 사용/XP</b> 이고,
     * 어느 단계에서 거절해도 인벤토리는 손대지 않는다.
     */
    public TradeResult trade(PlayerInventory inventory, int offerIndex) {
        return trade(inventory, offerIndex, 0);
    }

    /**
     * 평판·영웅 할인을 반영한 거래. {@code specialPriceDiff} 는
     * {@link VillagerGossipRules#specialPriceDiff} 가 계산한 값이며 음수가 할인이다.
     */
    public TradeResult trade(PlayerInventory inventory, int offerIndex, int specialPriceDiff) {
        synchronized (inventory) {
            if (inventory.settlementLeased()) return TradeResult.rejected(Rejection.INSUFFICIENT_ITEMS);
            return tradeLocked(inventory, offerIndex, specialPriceDiff);
        }
    }

    private TradeResult tradeLocked(PlayerInventory inventory, int offerIndex, int specialPriceDiff) {
        if (profession == Profession.NONE || profession == Profession.NITWIT || slots.isEmpty()) {
            return TradeResult.rejected(Rejection.NO_PROFESSION);
        }
        if (offerIndex < 0 || offerIndex >= slots.size()) {
            return TradeResult.rejected(Rejection.NO_SUCH_OFFER);
        }
        Slot slot = slots.get(offerIndex);
        if (slot.level > level) {
            return TradeResult.rejected(Rejection.LOCKED_LEVEL);
        }
        if (slot.outOfStock()) {
            return TradeResult.rejected(Rejection.OUT_OF_STOCK);
        }
        Offer offer = slot.offer;
        int costCount = slot.currentCostCount(specialPriceDiff);
        int costBCount = offer.hasSecondCost() ? offer.costBCount() : 0;
        int[] payments=new int[PlayerInventory.SLOTS];
        if (!reserve(inventory,offer.costPrototype(),costCount,payments)
                || offer.hasSecondCost() && !reserve(inventory,offer.costBPrototype(),costBCount,payments)) {
            return TradeResult.rejected(Rejection.INSUFFICIENT_ITEMS);
        }
        for(int index=0;index<payments.length;index++) if(payments[index]>0) inventory.dropFromSlot(index,payments[index]);
        int paid=costCount, paidSecond=costBCount;
        var result=offer.resultPrototype();
        int accepted=inventory.addItem(result.itemType(),offer.resultCount(),result.durability(),result.enchantments(),result.mapId(),result.shulkerId(),result.bucketMobData(),result.itemComponentData());
        int overflow = offer.resultCount() - accepted;

        slot.uses++;
        int before = totalXp;
        totalXp = profession == Profession.WANDERING_TRADER ? 0 : VillagerTradeRules.addTradeXp(totalXp, offer.xp());
        boolean leveled = false;
        while (VillagerTradeRules.shouldIncreaseLevel(level, totalXp)) {
            level++;
            leveled = true;
        }
        return new TradeResult(true, null, offer.resultItem(), offer.resultCount(),
                paid, paidSecond, overflow, totalXp - before, leveled, offer.resultPrototype());
    }

    /** 승급 직후 새 레벨 오퍼를 붙인다. RNG 소비 지점을 거래 실행과 분리해 둔다. */
    public void onLevelUp(IntUnaryOperator nextInt) {
        for (int unlocked = VillagerTradeRules.MIN_LEVEL; unlocked <= level; unlocked++) {
            ensureOffersForLevel(unlocked, nextInt);
        }
    }

    private static boolean reserve(PlayerInventory inventory,PlayerInventory.StackSnapshot required,int count,int[] reserved) {
        int remaining=count;
        for(int index=0;index<PlayerInventory.SLOTS && remaining>0;index++) {
            var stack=VillagerTradeStacks.at(inventory,index);
            if(!VillagerTradeStacks.matches(required,stack))continue;
            int amount=Math.min(remaining,stack.count()-reserved[index]);
            reserved[index]+=amount;remaining-=amount;
        }
        return remaining==0;
    }

    // ── 영속 ──────────────────────────────────────────────────────────────────

    public Snapshot snapshot() {
        int size = slots.size();
        int[] levels = new int[size];
        int[] indices = new int[size];
        int[] uses = new int[size];
        int[] demand = new int[size];
        int[] variantSeeds = new int[size];
        for (int i = 0; i < size; i++) {
            Slot slot = slots.get(i);
            levels[i] = slot.level;
            indices[i] = slot.poolIndex;
            uses[i] = slot.uses;
            demand[i] = slot.demand;
            variantSeeds[i] = slot.variantSeed;
        }
        return new Snapshot(profession.name(), level, totalXp,
                lastRestockGameTime, lastRestockCheckDayTime, restocksToday,
                levels, indices, uses, demand, variantSeeds);
    }

    /** 스냅샷을 그대로 복원한다. 풀에서 사라진 인덱스는 조용히 버린다(표는 append-only 다). */
    public static VillagerTradeState restore(Snapshot snapshot) {
        Profession profession;
        try {
            profession = Profession.valueOf(snapshot.profession());
        } catch (IllegalArgumentException unknown) {
            profession = Profession.NONE;
        }
        VillagerTradeState state = new VillagerTradeState(profession);
        state.level = Math.clamp(snapshot.level(),
                VillagerTradeRules.MIN_LEVEL, VillagerTradeRules.MAX_LEVEL);
        state.totalXp = Math.max(0, snapshot.totalXp());
        state.lastRestockGameTime = snapshot.lastRestockGameTime();
        state.lastRestockCheckDayTime = snapshot.lastRestockCheckDayTime();
        state.restocksToday = Math.max(0, snapshot.restocksToday());
        int[] levels = snapshot.offerLevels();
        int[] indices = snapshot.offerPoolIndices();
        int[] uses = snapshot.uses();
        int[] demand = snapshot.demand();
        for (int i = 0; i < levels.length; i++) {
            Offer[] pool = VillagerTradeRules.offerPool(profession, levels[i]);
            if (indices[i] < 0 || indices[i] >= pool.length) continue;
            int variantSeed = snapshot.variantSeeds()[i];
            Offer offer = profession == Profession.WANDERING_TRADER
                    ? WanderingTraderOfferComponents.materialize(pool[indices[i]], indices[i], variantSeed) : pool[indices[i]];
            state.slots.add(new Slot(levels[i], indices[i], offer,
                    Math.clamp(uses[i], 0, offer.maxUses()), demand[i], variantSeed));
        }
        return state;
    }

    @Override
    public String toString() {
        return "VillagerTradeState[" + profession + " lv" + level + " xp" + totalXp
                + " offers=" + Arrays.toString(slots.stream().map(Slot::poolIndex).toArray()) + "]";
    }
}
