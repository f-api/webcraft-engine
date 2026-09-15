package com.gameexpert.engine.persistence.finalcarrier.reference;

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

/** A settled exploration-map claim; original generated structure rows remain immutable. */
@Getter
@Entity
@Table(name = "world_structure_reference_claims", uniqueConstraints = {
        @UniqueConstraint(name = "uk_world_structure_reference_claim", columnNames = {
                "world_id", "baseline_id", "structure_id", "origin_chunk_x", "origin_chunk_z"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldStructureReferenceClaim {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private long worldId;
    @Column(nullable = false, length = 128) private String baselineId;
    @Column(nullable = false, length = 128) private String structureId;
    @Column(nullable = false) private int originChunkX;
    @Column(nullable = false) private int originChunkZ;
    @Column(nullable = false, length = 64) private String carrierFingerprint;
    @Column(nullable = false, length = 64) private String structureRowSha256;

    public WorldStructureReferenceClaim(long worldId, String baselineId, String structureId,
            int originChunkX, int originChunkZ, String carrierFingerprint, String structureRowSha256) {
        if (worldId <= 0 || baselineId == null || baselineId.isBlank() || baselineId.length() > 128
                || structureId == null || !structureId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || structureId.length() > 128 || !isDigest(carrierFingerprint) || !isDigest(structureRowSha256)) {
            throw new IllegalArgumentException("invalid structure reference claim evidence");
        }
        this.worldId = worldId;
        this.baselineId = baselineId;
        this.structureId = structureId;
        this.originChunkX = originChunkX;
        this.originChunkZ = originChunkZ;
        this.carrierFingerprint = carrierFingerprint;
        this.structureRowSha256 = structureRowSha256;
    }

    /** Presence of a committed row represents the only allowed transition, zero to one. */
    public int referenceCount() { return 1; }

    public boolean matchesEvidence(String carrierFingerprint, String structureRowSha256) {
        return this.carrierFingerprint.equals(carrierFingerprint)
                && this.structureRowSha256.equals(structureRowSha256);
    }

    private static boolean isDigest(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }
}
