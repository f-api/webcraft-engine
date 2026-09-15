package com.gameexpert.furnace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Exact world-owned fractional furnace XP suffix; there is exactly one row per world. */
@Getter
@Entity
@Table(name = "world_furnace_xp_carry")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldFurnaceXpCarry {
    @Id
    @Column(name = "world_id", nullable = false)
    private Long worldId;

    @Column(name = "carry_milli", nullable = false)
    private int carryMilli;

    @Column(name = "persistence_revision", nullable = false)
    private long persistenceRevision;

    public WorldFurnaceXpCarry(Long worldId) {
        requireWorldId(worldId);
        this.worldId = worldId;
    }

    /** Applies a strictly newer owner generation while the repository holds the row lock. */
    public boolean replaceIfNewer(int amount, long revision) {
        validate(amount, revision);
        if (revision <= persistenceRevision) return false;
        carryMilli = amount;
        persistenceRevision = revision;
        return true;
    }

    public boolean matches(int amount, long revision) {
        validate(amount, revision);
        return carryMilli == amount && persistenceRevision == revision;
    }

    private static void validate(int amount, long revision) {
        if (amount < 0 || amount >= 1000) {
            throw new IllegalArgumentException("furnace XP carry must be in [0, 1000)");
        }
        if (revision < 0 || revision == Long.MAX_VALUE) {
            throw new IllegalArgumentException("invalid furnace XP carry revision");
        }
    }

    private static void requireWorldId(Long worldId) {
        if (worldId == null || worldId <= 0L) {
            throw new IllegalArgumentException("positive world ID required");
        }
    }
}
