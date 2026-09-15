package com.gameexpert.engine.raid;

/**
 * One durable victory prize slot, identified by {@code (worldId, raidId, rewardToken)} exactly as
 * {@code docs/MC-REFERENCE.md} "Raid 권위 계약" requires.
 *
 * <p>The receipt is written in the same persistence boundary that confirms the victory, and a
 * second boundary conditionally flips {@code PENDING → GRANTED}. This prevents duplicate claims
 * and keeps retries deterministic. The later inventory/drop persistence is not yet in that same
 * transaction; its post-commit crash window is tracked as {@code reward-delivery-atomicity}.</p>
 *
 * <p>{@code rewardSeed} is fixed when the raid is armed. Persisting it here means the prize roll is
 * decided once; a retried claim reads the same seed instead of rerolling.</p>
 */
public record RaidRewardReceipt(
        long raidId,
        String rewardToken,
        long rewardSeed,
        String recipientNickname,
        long recordedTick,
        String state) {

    public static final String STATE_PENDING = "PENDING";
    public static final String STATE_GRANTED = "GRANTED";

    public RaidRewardReceipt {
        if (rewardToken == null || rewardToken.isBlank()) {
            throw new IllegalArgumentException("rewardToken is required");
        }
        if (!STATE_PENDING.equals(state) && !STATE_GRANTED.equals(state)) {
            throw new IllegalArgumentException("unknown receipt state " + state);
        }
    }

    public static RaidRewardReceipt pending(RaidLedger.Instance instance, long recordedTick) {
        return new RaidRewardReceipt(instance.raidId(), RaidLedger.VICTORY_REWARD_TOKEN,
                instance.rewardSeed(), instance.heroNickname(), recordedTick, STATE_PENDING);
    }

    public boolean granted() {
        return STATE_GRANTED.equals(state);
    }

    /** Outcome of one claim attempt. Only {@code GRANTED} may hand out items. */
    public enum ClaimOutcome { GRANTED, ALREADY_GRANTED, UNKNOWN_RECEIPT }
}
