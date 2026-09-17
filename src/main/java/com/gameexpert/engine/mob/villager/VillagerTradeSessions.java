package com.gameexpert.engine.mob.villager;

import com.gameexpert.engine.mob.villager.VillagerTradeRules.Profession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.inventory.ContainerAccess;
import com.gameexpert.engine.mob.villager.VillagerTradeState.Rejection;
import com.gameexpert.engine.mob.villager.VillagerTradeState.TradeResult;
import com.gameexpert.mob.dto.VillagerTradeSnapshot;

/**
 * 월드 하나의 주민 거래 상태 보관소다. 주민 id → {@link VillagerTradeState} 와
 * "지금 누가 어느 주민과 거래 중인가"를 함께 소유해, 거래 실행 권위가 세션 밖에서 오는 요청을
 * 조용히 통과시키지 못하게 한다.
 *
 * <p>거리·생존 검증은 호출자(틱 루프)가 이미 끝낸 상태로 들어온다. 여기서는 세션 존재 여부와
 * 오퍼 판정만 본다.
 */
public final class VillagerTradeSessions {

    /** 주민 한 명의 화면용 오퍼 한 줄(가격은 이미 수요 보정을 반영한 값이다). */
    public record OfferView(
            short costItem, int costCount,
            short costBItem, int costBCount,
            short resultItem, int resultCount,
            int uses, int maxUses, boolean locked,
            PlayerInventory.StackSnapshot costPrototype, PlayerInventory.StackSnapshot costBPrototype, PlayerInventory.StackSnapshot resultPrototype) {
    }

    /** 열린 거래 화면의 권위 상태. */
    public record TradeView(long mobId, int level, int tradeXp, List<OfferView> offers) {
    }

    /**
     * 재입고로 오퍼가 바뀐 주민을 지금 보고 있는 화면 하나. 바닐라
     * {@code Villager#resendOffersToTradingPlayer} 의 대상과 같으며, 실제 전송은 메시지
     * 표면을 가진 틱 루프가 {@link #drainOfferResends()} 로 가져가 한다.
     */
    public record OfferResend(String nickname, long mobId) {
    }

    /**
     * 영웅(마을의 영웅) 효과가 없다는 뜻의 amplifier. 효과가 있는 플레이어는
     * {@link #bindHeroAmplifiers} 가 그 증폭을 준다([RAID-OMEN]).
     */
    private static final int NO_HERO_EFFECT = -1;

    private final Map<Long, VillagerTradeState> states = new HashMap<>();
    private final Map<String, Long> openSessions = new LinkedHashMap<>();
    private final Map<String, Integer> selectedOffers = new HashMap<>();
    private final Map<String, PlayerInventory> payments = new HashMap<>();
    private VillagerReputationSource reputations = VillagerReputationSource.NONE;
    /** 저장이 승인되지 않은 행. 직업 lane 과 같은 delta 계약이다. */
    private final Map<Long, VillagerTradeSnapshot> unpersistedUpserts = new ConcurrentHashMap<>();
    private final Map<Long, Long> unpersistedRemovals = new ConcurrentHashMap<>();
    /** 아직 밀지 않은 재입고 갱신. 세션과 같이 영속 대상이 아니다. */
    private final List<OfferResend> pendingOfferResends = new ArrayList<>();

    /**
     * [RAID-OMEN] 플레이어의 영웅(Hero of the Village) 증폭(없으면 −1). 바닐라 {@code Villager#updateSpecialPrices} 는
     * 영웅 효과가 있으면 {@code 0.3 + 0.0625·amplifier} 만큼 깎는다({@code VillagerGossipRules#heroOfTheVillagePriceDiff}).
     */
    private java.util.function.ToIntFunction<String> heroAmplifiers = nickname -> NO_HERO_EFFECT;

    public void bindHeroAmplifiers(java.util.function.ToIntFunction<String> source) {
        this.heroAmplifiers = source == null ? nickname -> NO_HERO_EFFECT : source;
    }

    /**
     * 평판 원장을 배선한다. 정본은 주민 각자의 gossip 원장이며, 배선 전에는 평판 0
     * (=할인 없음)으로 동작한다.
     */
    public void bindReputations(VillagerReputationSource source) {
        this.reputations = source == null ? VillagerReputationSource.NONE : source;
    }

    /** 주민 상태를 만들거나 가져온다. 직업이 바뀌었으면 오퍼를 새로 만든다. */
    public VillagerTradeState stateFor(
            long mobId, VillagerTradeRules.Profession profession, IntUnaryOperator nextInt) {
        VillagerTradeState state = states.get(mobId);
        if (state == null) {
            state = new VillagerTradeState(profession);
            state.assignProfession(profession, nextInt);
            states.put(mobId, state);
            markDirty(mobId);
            return state;
        }
        if (state.profession() != profession) {
            state.assignProfession(profession, nextInt);
            markDirty(mobId);
        }
        return state;
    }

    public VillagerTradeState peek(long mobId) {
        return states.get(mobId);
    }

    /**
     * 거래 화면을 연다. 거래하지 않는 직업이면 세션을 만들지 않고 null 을 돌려준다.
     * 같은 플레이어가 다른 주민을 열면 이전 세션은 조용히 닫힌다(바닐라도 화면이 하나다).
     */
    public TradeView open(
            String nickname, long mobId,
            VillagerTradeRules.Profession profession, IntUnaryOperator nextInt, PlayerInventory inventory) {
        if (!VillagerTradeRules.tradesAtAll(profession)) return null;
        VillagerTradeState state = stateFor(mobId, profession, nextInt);
        if (state.offerCount() == 0) return null;
        openSessions.put(nickname, mobId);
        selectedOffers.put(nickname, -1);
        payments.put(nickname, java.util.Objects.requireNonNull(inventory, "merchant inventory"));
        return view(mobId, state, reputations.reputationOf(mobId, nickname),
                heroAmplifiers.applyAsInt(nickname));
    }

    /** 이 플레이어가 지금 이 주민과 거래 중인가. */
    public boolean hasSession(String nickname, long mobId) {
        Long open = openSessions.get(nickname);
        return open != null && open == mobId;
    }

    public Long openMobId(String nickname) {
        return openSessions.get(nickname);
    }

    public void close(String nickname) {
        openSessions.remove(nickname);
        selectedOffers.remove(nickname);
        payments.remove(nickname);
    }

    /** 주민이 사라지면 빈 세션을 닫고, 결제금이 남은 세션은 자동 반환까지 유지한다. */
    public void closeAllFor(long mobId) {
        openSessions.entrySet().removeIf(entry -> {
            if (entry.getValue() != mobId) return false;
            ContainerAccess held = paymentAccess(entry.getKey());
            // Keep a funded session reachable until the owner's automatic close returns it.
            if (held != null && (held.count(0) > 0 || held.count(1) > 0)) return false;
            selectedOffers.remove(entry.getKey());
            payments.remove(entry.getKey());
            return true;
        });
    }

    /** 오퍼 선택은 화면 입력만 바꾸며 거래를 실행하지 않는다. */
    public boolean select(String nickname, long mobId, int offerIndex) {
        if (!hasSession(nickname, mobId)) return false;
        VillagerTradeState state = states.get(mobId);
        if (state == null || offerIndex < 0 || offerIndex >= state.offerCount()) return false;
        selectedOffers.put(nickname, offerIndex);
        return true;
    }

    public int selectedOffer(String nickname) {
        return selectedOffers.getOrDefault(nickname, -1);
    }

    public ContainerAccess paymentAccess(String nickname) {
        PlayerInventory value = payments.get(nickname);
        return value == null ? null : value.merchantPayments();
    }

    /** Existing inputs are folded first, then exact plain stacks are moved into the two payments. */
    public boolean selectAndFill(String nickname, long mobId, int offerIndex,
            PlayerInventory inventory) {
        if (!select(nickname, mobId, offerIndex) || payments.get(nickname) != inventory) return false;
        return inventory.mutateMerchantPayments(() -> {
            if (!foldPayments(nickname, inventory)) return false;
            VillagerTradeState state = states.get(mobId);
            VillagerTradeState.Slot slot = state.slots().get(offerIndex);
            int reputation = reputations.reputationOf(mobId, nickname);
            ContainerAccess held = paymentAccess(nickname);
            takeMatching(inventory, held, 0, slot.offer().costPrototype(),
                    slot.currentCostCount(discount(state, slot, reputation,
                            heroAmplifiers.applyAsInt(nickname))));
            if (slot.offer().hasSecondCost()) {
                takeMatching(inventory, held, 1, slot.offer().costBPrototype(), slot.offer().costBCount());
            }
            return true;
        });
    }

    public boolean foldPayments(String nickname, PlayerInventory inventory) {
        ContainerAccess held = paymentAccess(nickname);
        if (held == null) return true;
        if (payments.get(nickname) != inventory) return false;
        boolean[] complete = {true};
        boolean accepted = inventory.mutateMerchantPayments(() -> {
            for (int slot = 0; slot < 2; slot++) {
                if (held.count(slot) == 0) continue;
                PlayerInventory.StackSnapshot stack = paymentStack(held, slot);
                held.take(slot, stack.count());
                int inserted = inventory.addItem(stack.itemType(), stack.count(), stack.durability(),
                        stack.enchantments(), stack.mapId(), stack.shulkerId(),
                        stack.bucketMobData(), stack.itemComponentData());
                if (inserted != stack.count()) {
                    held.put(slot, stack.itemType(), stack.count() - inserted, stack.durability(),
                            stack.enchantments(), stack.mapId(), stack.shulkerId(),
                            stack.bucketMobData(), stack.itemComponentData());
                    complete[0] = false;
                }
            }
            return true;
        });
        return accepted && complete[0];
    }

    /** Closing returns what fits and exposes the remaining exact stacks for a ground drop. */
    public List<PlayerInventory.DroppedStack> returnPayments(String nickname, PlayerInventory inventory) {
        ContainerAccess held = paymentAccess(nickname);
        if (held == null) return List.of();
        if (payments.get(nickname) != inventory) throw new IllegalStateException("merchant inventory changed");
        List<PlayerInventory.DroppedStack> overflow = new ArrayList<>();
        boolean accepted = inventory.mutateMerchantPayments(() -> {
            foldPayments(nickname, inventory);
            for (int slot = 0; slot < 2; slot++) {
                if (held.count(slot) == 0) continue;
                PlayerInventory.StackSnapshot stack = paymentStack(held, slot);
                overflow.add(PlayerInventory.DroppedStack.exact(stack.itemType(), stack.count(),
                        stack.durability(), stack.enchantments(), stack.mapId(), stack.shulkerId(),
                        stack.bucketMobData(), stack.itemComponentData()));
                held.take(slot, stack.count());
            }
            return true;
        });
        return accepted ? List.copyOf(overflow) : List.of();
    }

    private static PlayerInventory.StackSnapshot paymentStack(ContainerAccess held, int slot) {
        return new PlayerInventory.StackSnapshot(held.itemType(slot), held.count(slot), held.durability(slot),
                held.enchantments(slot), held.mapId(slot), held.shulkerId(slot),
                held.bucketMobData(slot), held.itemComponentData(slot));
    }

    private static void takeMatching(PlayerInventory inventory, ContainerAccess held, int paymentSlot,
            PlayerInventory.StackSnapshot required, int wanted) {
        int remaining=wanted;
        for(int index=0;index<PlayerInventory.SLOTS && remaining>0;index++) {
            var stack=VillagerTradeStacks.at(inventory,index);
            if(!VillagerTradeStacks.matches(required,stack)) continue;
            int moved=Math.min(remaining,stack.count());
            int inserted=held.put(paymentSlot,stack.itemType(),moved,stack.durability(),stack.enchantments(),stack.mapId(),stack.shulkerId(),stack.bucketMobData(),stack.itemComponentData());
            if(inserted>0) { inventory.takeFromSlot(index,inserted);remaining-=inserted; }
        }
    }

    /** 주민 자체가 영구히 사라졌을 때만 상태를 버린다. 저장된 행도 함께 지운다. */
    public VillagerTradeState forget(long mobId) {
        closeAllFor(mobId);
        VillagerTradeState removed = states.remove(mobId);
        if (removed != null) {
            unpersistedUpserts.remove(mobId);
            unpersistedRemovals.put(mobId, mobId);
        }
        return removed;
    }

    /**
     * 거래 한 번을 확정한다. 세션이 없으면 오퍼를 보지도 않고 거절한다.
     * 인벤토리 이동·재고·XP·승급은 {@link VillagerTradeState#trade} 가 원자적으로 처리한다.
     *
     * <p>결제 가격은 화면에 보낸 {@link #view} 와 <b>같은 특별 가격 차</b>로 계산한다. 표시와
     * 결제가 갈리면 플레이어가 본 값과 다른 개수가 빠져나가므로 두 경로가 한 함수를 쓴다.
     * 성사되면 바닐라 {@code Villager#onVillagerTrade} 처럼 그 주민의 원장에 TRADING 이 쌓인다.
     */
    public TradeResult trade(
            String nickname, long mobId, int offerIndex,
            PlayerInventory inventory, IntUnaryOperator nextInt) {
        if (!hasSession(nickname, mobId)) {
            return TradeResult.rejected(Rejection.NO_SUCH_OFFER);
        }
        VillagerTradeState state = states.get(mobId);
        if (state == null) return TradeResult.rejected(Rejection.NO_PROFESSION);
        int reputation = reputations.reputationOf(mobId, nickname);
        List<VillagerTradeState.Slot> slots = state.slots();
        int specialPriceDiff = offerIndex >= 0 && offerIndex < slots.size()
                ? discount(state,slots.get(offerIndex), reputation,
                        heroAmplifiers.applyAsInt(nickname)) : 0;
        TradeResult result = state.trade(inventory, offerIndex, specialPriceDiff);
        if (result.leveledUp()) state.onLevelUp(nextInt);
        // 재고·수요·XP·레벨이 이 한 번으로 바뀐다. 거절이면 상태를 손대지 않았으므로 dirty 도 아니다.
        if (result.accepted()) {
            markDirty(mobId);
            if(state.profession()!=Profession.WANDERING_TRADER) reputations.recordTrade(mobId, nickname);
        }
        return result;
    }

    /** 열린 화면의 현재 상태. 세션이 없으면 null. */
    public TradeView viewFor(String nickname, long mobId) {
        if (!hasSession(nickname, mobId)) return null;
        VillagerTradeState state = states.get(mobId);
        return state == null ? null
                : view(mobId, state, reputations.reputationOf(mobId, nickname),
                        heroAmplifiers.applyAsInt(nickname));
    }

    /**
     * 화면용 오퍼. 가격은 수요 보정에 이어 <b>보는 플레이어의 평판 할인</b>까지 반영한 값이라
     * 같은 주민이라도 플레이어마다 다르다(바닐라 {@code Villager#updateSpecialPrices}).
     */
    private static TradeView view(long mobId, VillagerTradeState state, int reputation, int heroAmplifier) {
        List<OfferView> offers = state.slots().stream()
                .map(slot -> new OfferView(
                        slot.offer().costItem(),
                        slot.currentCostCount(discount(state,slot, reputation, heroAmplifier)),
                        slot.offer().costBItem(), slot.offer().costBCount(),
                        slot.offer().resultItem(), slot.offer().resultCount(),
                        slot.uses(), slot.offer().maxUses(),
                        slot.level() > state.level(),slot.offer().costPrototype(),slot.offer().costBPrototype(),slot.offer().resultPrototype()))
                .toList();
        return new TradeView(mobId, state.level(), state.totalXp(), offers);
    }

    /**
     * 오퍼 한 줄의 특별 가격 차. 바닐라와 같이 오퍼 자신의 {@code priceMultiplier} 와 기본
     * 비용으로 계산하며, 음수가 할인이다. 하한 1 은 {@code currentCostCount} 가 건다.
     */
    private static int discount(VillagerTradeState state,VillagerTradeState.Slot slot,int reputation,int hero) {
        return state.profession()==Profession.WANDERING_TRADER?0:specialPriceDiff(slot,reputation,hero);
    }

    private static int specialPriceDiff(VillagerTradeState.Slot slot, int reputation, int heroAmplifier) {
        return VillagerGossipRules.specialPriceDiffMilli(
                reputation, slot.offer().priceMultiplierMilli(),
                heroAmplifier, slot.offer().costCount());
    }

    /**
     * 이번 틱 자기 작업대를 쓴 주민들의 재입고다. 바닐라 {@code WorkAtPoi.start} 끝의
     * {@code if (villager.shouldRestock()) villager.restock();} 과 같으며, 규칙 자체는
     * {@link VillagerTradeState#tickWorkAtJobSite} 가 소유한다.
     *
     * <p>아직 거래 상태가 없는 주민(=거래 화면이 한 번도 열린 적 없는 주민)은 채울 재고도 없어
     * 건너뛴다. 그런 주민의 오퍼는 첫 개시 때 uses 0 으로 만들어지므로 결과가 같다.
     *
     * <p>재입고가 실제로 일어난 주민을 <b>지금 보고 있는 화면</b>은 갱신 대상으로 쌓인다
     * (바닐라 {@code Villager#restock} → {@code resendOffersToTradingPlayer}). 전송은
     * 메시지 표면을 가진 틱 루프가 {@link #drainOfferResends()} 로 가져가 한다.
     *
     * @return 실제로 재입고가 일어난 주민 수
     */
    public int restockWorkingVillagers(Collection<Long> mobIds, long gameTime, long dayTime) {
        if (mobIds == null || mobIds.isEmpty()) return 0;
        int restocked = 0;
        for (Long mobId : mobIds) {
            if (mobId == null) continue;
            VillagerTradeState state = states.get(mobId);
            if (state == null) continue;
            if (!state.tickWorkAtJobSite(gameTime, dayTime)) continue;
            markDirty(mobId);
            queueOfferResend(mobId);
            restocked++;
        }
        return restocked;
    }

    /** 이 주민을 보고 있는 화면을 갱신 대기열에 올린다. 같은 틱에 두 번 쌓이지 않는다. */
    private void queueOfferResend(long mobId) {
        for (Map.Entry<String, Long> session : openSessions.entrySet()) {
            if (session.getValue() != mobId) continue;
            OfferResend resend = new OfferResend(session.getKey(), mobId);
            if (!pendingOfferResends.contains(resend)) pendingOfferResends.add(resend);
        }
    }

    /**
     * 밀어야 할 갱신을 가져가고 비운다. 화면이 그 사이 닫혔으면 {@link #viewFor} 가 null 을
     * 돌려주므로 호출자가 조용히 버린다.
     */
    public List<OfferResend> drainOfferResends() {
        if (pendingOfferResends.isEmpty()) return List.of();
        List<OfferResend> drained = List.copyOf(pendingOfferResends);
        pendingOfferResends.clear();
        return drained;
    }

    // ── 영속 lane ─────────────────────────────────────────────────────────────
    // 세션(누가 보고 있는가)은 영속 대상이 아니다. 거래 진행도만 durable 행이 된다.

    /**
     * 이 주민의 현재 상태를 다음 flush 대상으로 표시한다. 거래로 바뀐 상태는 {@link #trade}
     * 가 스스로 표시하고, 상태를 밖에서 바꾸는 경로(재입고 틱)가 이 표면을 쓴다.
     */
    public void markDirty(long mobId) {
        VillagerTradeState state = states.get(mobId);
        if (state == null) return;
        unpersistedUpserts.put(mobId, new VillagerTradeSnapshot(mobId, state.snapshot()));
        unpersistedRemovals.remove(mobId);
    }

    public boolean hasUnpersistedRows() {
        return !unpersistedUpserts.isEmpty() || !unpersistedRemovals.isEmpty();
    }

    public Map<Long, VillagerTradeSnapshot> unpersistedUpserts() {
        return Map.copyOf(unpersistedUpserts);
    }

    public Map<Long, Long> unpersistedRemovals() {
        return Map.copyOf(unpersistedRemovals);
    }

    /** 저장이 실제로 성공한 뒤에만 부른다. 그 사이 상태가 또 바뀌었으면 값이 달라 남는다. */
    public void acknowledgePersisted(Map<Long, VillagerTradeSnapshot> upserts,
            Map<Long, Long> removals) {
        upserts.forEach(unpersistedUpserts::remove);
        removals.forEach(unpersistedRemovals::remove);
    }

    /**
     * 재접속·언로드·재기동 복구: 저장된 행을 그대로 되살린다. 복원된 행은 방금 DB 에서 온
     * 것이므로 dirty 가 아니다.
     */
    public void restore(Collection<VillagerTradeSnapshot> rows) {
        states.clear();
        openSessions.clear();
        pendingOfferResends.clear();
        unpersistedUpserts.clear();
        unpersistedRemovals.clear();
        for (VillagerTradeSnapshot row : rows) {
            if (row == null) continue;
            states.put(row.mobId(), VillagerTradeState.restore(row.state()));
        }
    }
}
