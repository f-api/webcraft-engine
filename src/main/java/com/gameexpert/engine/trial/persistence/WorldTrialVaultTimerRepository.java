package com.gameexpert.engine.trial.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** [TRIAL-GAP] Per-vault {@code stateUpdatingResumesAt} rows (see {@link WorldTrialVaultTimer}). */
public interface WorldTrialVaultTimerRepository extends JpaRepository<WorldTrialVaultTimer, Long> {
    List<WorldTrialVaultTimer> findAllByWorldId(Long worldId);
    Optional<WorldTrialVaultTimer> findByWorldIdAndXAndYAndZ(Long worldId, int x, int y, int z);
    void deleteAllByWorldId(Long worldId);
}
