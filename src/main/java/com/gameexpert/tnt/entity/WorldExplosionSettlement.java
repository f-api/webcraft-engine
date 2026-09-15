package com.gameexpert.tnt.entity;

import com.gameexpert.ground.dto.GroundMutationCommand;
import com.gameexpert.tnt.dto.ExplosionSettlementCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Permanent commit-last receipt and allocator high-water for one complete explosion plan. */
@Getter
@Entity
@Table(name = "world_explosion_settlements", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_explosion_settlement", columnNames = {"world_id", "explosion_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldExplosionSettlement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long worldId;

    @Column(nullable = false)
    private long explosionId;

    @Column(nullable = false, length = 64)
    private String commandHash;

    @Column(nullable = false)
    private long highestGroundEntityId;

    @Column(nullable = false)
    private long highestTntId;

    public WorldExplosionSettlement(Long worldId, long explosionId, String commandHash,
            long highestGroundEntityId, long highestTntId) {
        if (!isValid(worldId, explosionId, commandHash,
                highestGroundEntityId, highestTntId)) {
            throw new IllegalArgumentException("explosion settlement receipt is invalid");
        }
        this.worldId = worldId;
        this.explosionId = explosionId;
        this.commandHash = commandHash;
        this.highestGroundEntityId = highestGroundEntityId;
        this.highestTntId = highestTntId;
    }

    /** Replay identity includes the command and every allocator high-water it derives. */
    public boolean matches(ExplosionSettlementCommand command) {
        return command != null
                && Objects.equals(worldId, command.worldId())
                && explosionId == command.explosionId()
                && Objects.equals(commandHash, command.commandHash())
                && highestGroundEntityId == command.highestReservedGroundEntityId()
                && highestTntId == command.highestTntId();
    }

    @PostLoad
    private void validateLoadedState() {
        if (!isValid(worldId, explosionId, commandHash,
                highestGroundEntityId, highestTntId)) {
            throw new IllegalStateException("stored explosion settlement receipt is invalid");
        }
    }

    private static boolean isValid(Long worldId, long explosionId, String commandHash,
            long highestGroundEntityId, long highestTntId) {
        return worldId != null && worldId > 0 && worldId < Long.MAX_VALUE
                && explosionId > 0 && explosionId < Long.MAX_VALUE
                && commandHash != null && commandHash.matches("[0-9a-f]{64}")
                && !commandHash.equals("0".repeat(64))
                && highestGroundEntityId >= 0
                && highestGroundEntityId <= GroundMutationCommand.MAX_GROUND_ENTITY_ID
                && highestTntId >= 0 && highestTntId < Long.MAX_VALUE;
    }
}
