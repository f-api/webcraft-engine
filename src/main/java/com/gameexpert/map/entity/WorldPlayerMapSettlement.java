package com.gameexpert.map.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 빈 지도 사용 명령의 영구 멱등성 영수증. */
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "world_player_map_settlements", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_player_map_settlement", columnNames = {"world_id", "settlement_id"}))
public class WorldPlayerMapSettlement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "world_id", nullable = false)
    private Long worldId;

    @Column(name = "settlement_id", nullable = false)
    private long settlementId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "source_inventory_revision", nullable = false)
    private long sourceInventoryRevision;

    @Column(name = "committed_inventory_revision", nullable = false)
    private long committedInventoryRevision;

    @Column(name = "map_id", nullable = false)
    private int mapId;

    @Column(name = "ground_entity_id", nullable = false)
    private long groundEntityId;

    @Column(name = "command_fingerprint", nullable = false, length = 64)
    private String commandFingerprint;

    protected WorldPlayerMapSettlement() {
    }

    public WorldPlayerMapSettlement(Long worldId, long settlementId, Long playerId,
            long sourceInventoryRevision, long committedInventoryRevision, int mapId,
            long groundEntityId, String commandFingerprint) {
        this.worldId = worldId;
        this.settlementId = settlementId;
        this.playerId = playerId;
        this.sourceInventoryRevision = sourceInventoryRevision;
        this.committedInventoryRevision = committedInventoryRevision;
        this.mapId = mapId;
        this.groundEntityId = groundEntityId;
        this.commandFingerprint = commandFingerprint;
    }

    public boolean matches(String expectedFingerprint) {
        return commandFingerprint.equals(expectedFingerprint);
    }
}
