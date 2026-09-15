package com.gameexpert.engine.persistence.animal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Durable write-ahead identity for cross-aggregate animal settlements. */
@Getter
@Entity
@Table(name = "world_animal_settlements", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_animal_settlement_key", columnNames = { "world_id", "settlement_key" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldAnimalSettlement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "world_id", nullable = false) private Long worldId;
    @Column(name = "settlement_key", nullable = false, length = 160) private String settlementKey;
    @Column(nullable = false, length = 24) private String kind;
    @Column(name = "source_mob_id", nullable = false) private long sourceMobId;
    @Column(nullable = false) private long token;
    @Column(name = "entity_id", nullable = false) private long entityId;
    @Column(name = "item_type", nullable = false) private short itemType;
    @Column(nullable = false) private double x;
    @Column(nullable = false) private double y;
    @Column(nullable = false) private double z;
    @Column(nullable = false) private boolean completed;
    @Lob @Column(columnDefinition = "LONGTEXT") private String payload;

    public WorldAnimalSettlement(Long worldId, String key, String kind, long sourceMobId,
            long token, long entityId, short itemType, double x, double y, double z) {
        this.worldId = worldId; this.settlementKey = key; this.kind = kind;
        this.sourceMobId = sourceMobId; this.token = token; this.entityId = entityId;
        this.itemType = itemType; this.x = x; this.y = y; this.z = z;
    }

    void complete() { completed = true; }
    void setPayload(String payload) { this.payload = payload; }
}
