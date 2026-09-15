package com.gameexpert.engine.trial.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [TRIAL-GAP] One vault's {@code VaultServerData.stateUpdatingResumesAt} (26.3 CODEC field
 * {@code state_updating_resumes_at}, absolute MC game time). The other saved vault fields live
 * elsewhere: rewarded players are GRANTED {@code trial_vault_v1:} receipts, items to eject are
 * {@code trial_vault_eject_v1:} delivery rows and the vault state is its block-state diff.
 * {@code lastInsertFailTimestamp} is transient in vanilla (not in the CODEC) and is not stored.
 */
@Getter
@Entity
@Table(name = "world_trial_vault_timers",
        uniqueConstraints = @UniqueConstraint(name = "uk_world_trial_vault_timer_position",
                columnNames = {"world_id", "block_x", "block_y", "block_z"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldTrialVaultTimer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "world_id", nullable = false) private Long worldId;
    @Column(name = "block_x", nullable = false) private int x;
    @Column(name = "block_y", nullable = false) private int y;
    @Column(name = "block_z", nullable = false) private int z;
    @Column(name = "resumes_at_mc", nullable = false) private long resumesAtMc;

    WorldTrialVaultTimer(Long worldId, int x, int y, int z, long resumesAtMc) {
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.resumesAtMc = resumesAtMc;
    }

    void resumeAt(long resumesAtMc) {
        this.resumesAtMc = resumesAtMc;
    }
}
