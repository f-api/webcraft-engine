package com.gameexpert.engine.mob.villager;

import java.util.function.LongFunction;

/**
 * 거래 lane 이 읽는 평판 원장이다. 정본은 <b>거래 상대 주민 한 마리의 gossip 원장</b>
 * ({@link VillagerGossips})이고, 이 표면은 그 원장을 (1) 가격 계산용으로 읽고
 * (2) 거래가 성사되면 TRADING 을 쌓는 두 가지 일만 한다.
 *
 * <p>바닐라 근거(Java 1.21.4): 가격은 {@code Villager#updateSpecialPrices} 가
 * {@code getPlayerReputation(tradingPlayer)} 로 <b>거래 중인 그 주민의</b> gossip 을 읽어
 * 정하고, 거래 성사는 {@code Villager#onVillagerTrade} → {@code gossips.add(TRADING, 2)} 로
 * 같은 원장에 쌓인다. 그래서 읽기와 쓰기가 한 원장에 붙어 있어야 한다.
 *
 * <p>{@link VillagerProfessionSource} 와 같은 자리의 배선 지점이며, 배선 전에는
 * 평판 0(=할인 없음)인 {@link #NONE} 을 쓴다.
 */
public interface VillagerReputationSource {

    /** 배선 전 기본값. 어떤 주민도 평판을 기억하지 않는다. */
    VillagerReputationSource NONE = new VillagerReputationSource() {
        @Override
        public int reputationOf(long villagerId, String playerKey) {
            return 0;
        }

        @Override
        public void recordTrade(long villagerId, String playerKey) {
            // 원장이 없으면 쌓을 곳도 없다.
        }
    };

    /** 그 주민이 이 플레이어에게 매기는 평판. 원장이 없으면 0이다. */
    int reputationOf(long villagerId, String playerKey);

    /** 거래 한 번을 그 주민의 원장에 남긴다(바닐라 {@code TRADING +2}). */
    void recordTrade(long villagerId, String playerKey);

    /**
     * 정본 배선: 주민 id → 그 주민의 gossip 원장 조회기를 감싼다. 조회기가 {@code null} 을
     * 돌려주는 주민(비활성·좀비 등)은 평판이 없는 것과 같게 다룬다.
     */
    static VillagerReputationSource ofGossips(LongFunction<VillagerGossips> lookup) {
        if (lookup == null) return NONE;
        return new VillagerReputationSource() {
            @Override
            public int reputationOf(long villagerId, String playerKey) {
                if (playerKey == null) return 0;
                VillagerGossips gossips = lookup.apply(villagerId);
                return gossips == null ? 0 : gossips.reputation(playerKey);
            }

            @Override
            public void recordTrade(long villagerId, String playerKey) {
                if (playerKey == null) return;
                VillagerGossips gossips = lookup.apply(villagerId);
                if (gossips == null) return;
                gossips.applyReputationEvent(
                        playerKey, VillagerGossipRules.ReputationEvent.TRADE);
            }
        };
    }
}
