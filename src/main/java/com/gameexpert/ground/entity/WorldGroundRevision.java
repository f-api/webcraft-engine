package com.gameexpert.ground.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;

/** Durable compare-and-set cursor for every mutation of one world's ground aggregate. */
@Entity
@Table(name = "world_ground_revisions")
public class WorldGroundRevision {
    @Id
    @Column(name = "world_id", nullable = false)
    private Long worldId;

    @Column(name = "ground_revision", nullable = false)
    private long groundRevision;

    protected WorldGroundRevision() {
    }

    public WorldGroundRevision(Long worldId) {
        if (!isValidWorldId(worldId)) {
            throw new IllegalArgumentException("world id is required");
        }
        this.worldId = worldId;
    }

    public long getGroundRevision() {
        return groundRevision;
    }

    public synchronized boolean advance(long expectedRevision, long committedRevision) {
        if (!isValidSuccessor(expectedRevision, committedRevision)) {
            throw new IllegalArgumentException("ground revision must advance by one");
        }
        if (groundRevision != expectedRevision) return false;
        groundRevision = committedRevision;
        return true;
    }

    /** Rejects creating Long.MAX_VALUE - 1, which has no valid reserved-value successor. */
    public static boolean isValidSuccessor(long expectedRevision, long committedRevision) {
        return expectedRevision >= 0 && expectedRevision < Long.MAX_VALUE - 2
                && committedRevision == expectedRevision + 1;
    }

    public static long requireSuccessor(long expectedRevision) {
        if (expectedRevision < 0 || expectedRevision >= Long.MAX_VALUE - 2) {
            throw new IllegalArgumentException("ground revision has no valid successor");
        }
        return expectedRevision + 1;
    }

    @PostLoad
    private void validateLoadedState() {
        if (!isValidWorldId(worldId) || !isValidStoredRevision(groundRevision)) {
            throw new IllegalStateException("stored ground revision is invalid");
        }
    }

    private static boolean isValidWorldId(Long worldId) {
        return worldId != null && worldId > 0 && worldId < Long.MAX_VALUE;
    }

    private static boolean isValidStoredRevision(long revision) {
        return revision >= 0 && revision < Long.MAX_VALUE;
    }
}
