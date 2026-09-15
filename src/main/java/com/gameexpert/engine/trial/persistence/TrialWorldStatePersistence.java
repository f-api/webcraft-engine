package com.gameexpert.engine.trial.persistence;

import com.gameexpert.engine.trial.TrialSpawnerRuntime;
import com.gameexpert.terrain.Blocks;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrialWorldStatePersistence {
    private final WorldTrialVaultTimerRepository vaultTimers;
    private final JdbcTemplate jdbc;

    public Map<Long, Long> hydrateVaultTimers(Long worldId) {
        Map<Long, Long> timers = new LinkedHashMap<>();
        for (WorldTrialVaultTimer row : vaultTimers.findAllByWorldId(worldId)) {
            timers.put(TrialSpawnerRuntime.positionKey(row.getX(), row.getY(), row.getZ()),
                    row.getResumesAtMc());
        }
        return timers;
    }

    public void saveChanges(Long worldId, List<TrialSpawnerRuntime.StateChange> stateChanges) {
        for (TrialSpawnerRuntime.StateChange change : stateChanges) {
            if (change == null) throw new IllegalArgumentException("trial state change must not be null");
            jdbc.update("""
                    INSERT INTO world_block_diffs
                        (world_id,x,y,z,chunk_x,chunk_z,block_type,block_state,mob_mutation_key,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,NULL,CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE block_type=VALUES(block_type),
                        block_state=VALUES(block_state),mob_mutation_key=NULL,
                        updated_at=CURRENT_TIMESTAMP
                    """, worldId, change.x(), change.y(), change.z(),
                    Math.floorDiv(change.x(), 16), Math.floorDiv(change.z(), 16),
                    change.blockType(), change.state());
            // [TRIAL-GAP] 금고 전이는 그 순간의 stateUpdatingResumesAt 을 같은 커밋에 싣는다.
            if (change.blockType() == Blocks.VAULT && change.vaultResumesAtMc() >= 0L) {
                WorldTrialVaultTimer timer = vaultTimers.findByWorldIdAndXAndYAndZ(
                        worldId, change.x(), change.y(), change.z()).orElse(null);
                if (timer == null) {
                    timer = new WorldTrialVaultTimer(worldId, change.x(), change.y(), change.z(),
                            change.vaultResumesAtMc());
                } else {
                    timer.resumeAt(change.vaultResumesAtMc());
                }
                vaultTimers.save(timer);
            }
        }
    }

    public void deleteWorld(Long worldId) {
        vaultTimers.deleteAllByWorldId(worldId);
    }
}
