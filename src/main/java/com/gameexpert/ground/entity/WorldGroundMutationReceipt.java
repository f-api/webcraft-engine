package com.gameexpert.ground.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Permanent exactly-once receipt for a committed ground aggregate mutation. */
@Entity
@Table(name = "world_ground_mutation_receipts", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_ground_mutation_receipt", columnNames = {"world_id", "mutation_id"}))
public class WorldGroundMutationReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "world_id", nullable = false)
    private Long worldId;

    @Column(name = "mutation_id", nullable = false)
    private long mutationId;

    @Column(name = "highest_entity_id", nullable = false)
    private long highestEntityId;

    @Column(name = "command_fingerprint", nullable = false, length = 64)
    private String commandFingerprint;

    protected WorldGroundMutationReceipt() {
    }

    public WorldGroundMutationReceipt(Long worldId, long mutationId, long highestEntityId,
            String commandFingerprint) {
        if (!isUsableId(worldId) || !isUsableId(mutationId)
                || !isUsableHighWater(highestEntityId) || !isSha256(commandFingerprint)) {
            throw new IllegalArgumentException("ground mutation receipt identity is invalid");
        }
        this.worldId = worldId;
        this.mutationId = mutationId;
        this.highestEntityId = highestEntityId;
        this.commandFingerprint = commandFingerprint;
    }

    public boolean matches(String expectedFingerprint) {
        return commandFingerprint.equals(expectedFingerprint);
    }

    public long mutationId() {
        return mutationId;
    }

    public long highestEntityId() {
        return highestEntityId;
    }

    @PostLoad
    private void validateLoadedState() {
        if (!isUsableId(worldId) || !isUsableId(mutationId)
                || !isUsableHighWater(highestEntityId) || !isSha256(commandFingerprint)) {
            throw new IllegalStateException("stored ground mutation receipt is invalid");
        }
    }

    private static boolean isUsableId(Long value) {
        return value != null && value > 0 && value < Long.MAX_VALUE;
    }

    private static boolean isUsableId(long value) {
        return value > 0 && value < Long.MAX_VALUE;
    }

    private static boolean isUsableHighWater(long value) {
        return value >= 0 && value < Long.MAX_VALUE;
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }
}
