package com.gameexpert.mob.entity;

import com.gameexpert.mob.dto.StructureOccupantClaimSnapshot;
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

/** Exactly-once initial-occupant decision for one deterministic structure site. */
@Getter
@Entity
@Table(
        name = "world_structure_occupant_claims",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_world_structure_occupant_claim",
                columnNames = { "world_id", "site_kind", "cell_x", "cell_z" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldStructureOccupantClaim {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long worldId;

    @Column(nullable = false, length = 48)
    private String siteKind;

    @Column(nullable = false)
    private int cellX;

    @Column(nullable = false)
    private int cellZ;

    @Column(nullable = false)
    private long siteKey;

    @Column(nullable = false)
    private int policyVersion;

    @Column(nullable = false)
    private int occupantCount;

    public WorldStructureOccupantClaim(Long worldId, StructureOccupantClaimSnapshot snapshot) {
        this.worldId = worldId;
        this.siteKind = snapshot.siteKind();
        this.cellX = snapshot.cellX();
        this.cellZ = snapshot.cellZ();
        this.siteKey = snapshot.siteKey();
        this.policyVersion = snapshot.policyVersion();
        this.occupantCount = snapshot.occupantCount();
    }

    public StructureOccupantClaimSnapshot toSnapshot() {
        return new StructureOccupantClaimSnapshot(
                siteKind, cellX, cellZ, siteKey, policyVersion, occupantCount);
    }
}
