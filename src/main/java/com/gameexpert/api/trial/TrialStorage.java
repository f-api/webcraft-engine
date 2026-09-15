package com.gameexpert.api.trial;

import com.gameexpert.engine.trial.TrialSpawnerRuntime;
import java.util.List;
import java.util.Map;

public interface TrialStorage {
    enum SettlementOutcome { COMMITTED, IDEMPOTENT, REJECTED }
    @FunctionalInterface
    interface RewardMutation { void persist(); }

    List<TrialSpawnerRuntime.SiteSnapshot> hydrateWorld(Long worldId);
    Map<Long, Long> hydrateVaultTimers(Long worldId);
    void saveWorld(Long worldId, List<TrialSpawnerRuntime.SiteSnapshot> snapshots);
    void saveWorldWithMutation(Long worldId, List<TrialSpawnerRuntime.SiteSnapshot> snapshots,
            RewardMutation mutation);
    void saveWorldWithMutation(Long worldId, List<TrialSpawnerRuntime.SiteSnapshot> snapshots,
            List<TrialSpawnerRuntime.StateChange> stateChanges, RewardMutation mutation);
    SettlementOutcome settleReward(Long worldId, long trialId, String rewardIdentity,
            RewardMutation mutation);
    void deleteWorld(Long worldId);
}
