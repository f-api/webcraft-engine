package com.gameexpert.mob.dto;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.raid.RaidRewardReceipt;

/** 한 보상 트랜잭션의 커밋 결과. insertedCount만 런타임에 반영한다. */
public record RewardSettlement(
        RaidRewardReceipt.ClaimOutcome outcome, short itemType, int insertedCount,
        int durability, long enchantments, int remainingCount,
        PlayerInventory.HandRef consumedKeyHand,
        InventoryCommitOutcome inventoryCommitOutcome, long committedInventoryRevision,
        PlayerInventory.CompletePersistenceSnapshot committedInventory) {

    public static final long NO_COMMITTED_INVENTORY_REVISION = -1L;

    public enum InventoryCommitOutcome { UNCHANGED, COMMITTED, STALE }

    public RewardSettlement {
        if (inventoryCommitOutcome == null) {
            throw new IllegalArgumentException("inventory commit outcome is required");
        }
        boolean committed = inventoryCommitOutcome == InventoryCommitOutcome.COMMITTED;
        if (committed != (committedInventoryRevision >= 0)) {
            throw new IllegalArgumentException("committed inventory revision does not match outcome");
        }
        if (committed != (committedInventory != null)) {
            throw new IllegalArgumentException("committed inventory snapshot does not match outcome");
        }
        if (committed && committedInventory.revision() != committedInventoryRevision) {
            throw new IllegalArgumentException("committed inventory snapshot revision does not match");
        }
    }

    public boolean keyConsumed() {
        return consumedKeyHand != null;
    }

    public static RewardSettlement outcome(RaidRewardReceipt.ClaimOutcome outcome) {
        return unchanged(outcome);
    }

    public static RewardSettlement unchanged(RaidRewardReceipt.ClaimOutcome outcome) {
        return new RewardSettlement(outcome, (short) 0, 0, 0, 0, 0, null,
                InventoryCommitOutcome.UNCHANGED, NO_COMMITTED_INVENTORY_REVISION, null);
    }

    public static RewardSettlement stale() {
        return new RewardSettlement(RaidRewardReceipt.ClaimOutcome.UNKNOWN_RECEIPT,
                (short) 0, 0, 0, 0, 0, null,
                InventoryCommitOutcome.STALE, NO_COMMITTED_INVENTORY_REVISION, null);
    }
}
