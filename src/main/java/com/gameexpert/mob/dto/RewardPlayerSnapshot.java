package com.gameexpert.mob.dto;

import com.gameexpert.engine.inventory.PlayerInventory;

/** 보상 트랜잭션이 잠긴 플레이어 행에 적용할 현재 런타임 인벤토리 스냅샷. */
public record RewardPlayerSnapshot(
        Long playerId, String nickname,
        PlayerInventory.CompletePersistenceSnapshot inventorySnapshot,
        PlayerInventory.HandRef trialKeyHand) {

    public RewardPlayerSnapshot {
        if (playerId == null || nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("reward player identity is required");
        }
        if (inventorySnapshot == null) {
            throw new IllegalArgumentException("complete reward inventory snapshot is required");
        }
    }

    public PlayerInventory inventory() {
        return inventorySnapshot.detachedInventory();
    }

    public long inventoryRevision() { return inventorySnapshot.revision(); }
}
