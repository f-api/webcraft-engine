package com.gameexpert.engine.persistence.finalcarrier.reference;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "world_loot_reference_pages", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_loot_reference_page", columnNames = {
                "world_id", "baseline_id", "snapshot_identity", "payload_kind", "page_number"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldLootReferencePage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private long worldId;
    @Column(nullable = false, updatable = false, length = 128) private String baselineId;
    @Column(nullable = false, updatable = false, length = 64) private String snapshotIdentity;
    @Column(nullable = false, updatable = false, length = 16) private String payloadKind;
    @Column(nullable = false, updatable = false) private int pageNumber;
    @Lob @Column(nullable = false, updatable = false, columnDefinition = "MEDIUMBLOB") private byte[] payload;

    public WorldLootReferencePage(long worldId, String baselineId, String snapshotIdentity,
            String payloadKind, int pageNumber, byte[] payload) {
        if (worldId <= 0 || pageNumber < 0 || payload == null || payload.length == 0
                || payload.length > SegmentedReferencePayload.PAGE_BYTES) {
            throw new IllegalArgumentException("invalid reference page");
        }
        this.worldId = worldId;
        this.baselineId = baselineId;
        this.snapshotIdentity = snapshotIdentity;
        this.payloadKind = payloadKind;
        this.pageNumber = pageNumber;
        this.payload = payload.clone();
    }
}
