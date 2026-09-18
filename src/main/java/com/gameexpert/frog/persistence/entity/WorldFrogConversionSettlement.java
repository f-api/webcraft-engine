package com.gameexpert.frog.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Write-ahead identity for one ordinary-frog to poison-frog conversion. */
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "world_frog_conversion_settlements", uniqueConstraints = @UniqueConstraint(
        name = "uk_frog_conversion_source_sequence",
        columnNames = {"world_id", "source_mob_id", "conversion_sequence"}))
public class WorldFrogConversionSettlement {
    public enum Status {
        PENDING,
        APPLIED,
        COMMITTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "world_id", nullable = false)
    private Long worldId;

    @Column(name = "source_mob_id", nullable = false)
    private long sourceMobId;

    @Column(name = "conversion_sequence", nullable = false)
    private long sequence;

    @Column(name = "colony_x", nullable = false)
    private int colonyX;

    @Column(name = "colony_y", nullable = false)
    private int colonyY;

    @Column(name = "colony_z", nullable = false)
    private int colonyZ;

    @Column(name = "host_x", nullable = false)
    private int hostX;

    @Column(name = "host_y", nullable = false)
    private int hostY;

    @Column(name = "host_z", nullable = false)
    private int hostZ;

    @Column(name = "deterministic_variant", nullable = false, length = 40)
    private String deterministicVariant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    protected WorldFrogConversionSettlement() {
    }

    public WorldFrogConversionSettlement(Long worldId, long sourceMobId, long sequence,
            int colonyX, int colonyY, int colonyZ, int hostX, int hostY, int hostZ,
            String deterministicVariant, int schemaVersion) {
        this.worldId = worldId;
        this.sourceMobId = sourceMobId;
        this.sequence = sequence;
        this.colonyX = colonyX;
        this.colonyY = colonyY;
        this.colonyZ = colonyZ;
        this.hostX = hostX;
        this.hostY = hostY;
        this.hostZ = hostZ;
        this.deterministicVariant = deterministicVariant;
        this.status = Status.PENDING;
        this.schemaVersion = schemaVersion;
    }

    public Long getWorldId() { return worldId; }
    public long getSourceMobId() { return sourceMobId; }
    public long getSequence() { return sequence; }
    public int getColonyX() { return colonyX; }
    public int getColonyY() { return colonyY; }
    public int getColonyZ() { return colonyZ; }
    public int getHostX() { return hostX; }
    public int getHostY() { return hostY; }
    public int getHostZ() { return hostZ; }
    public String getDeterministicVariant() { return deterministicVariant; }
    public Status getStatus() { return status; }
    public int getSchemaVersion() { return schemaVersion; }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public boolean isApplied() {
        return status == Status.APPLIED;
    }

    public void markApplied() {
        if (status != Status.PENDING) {
            throw new IllegalStateException("only a pending frog conversion can be applied");
        }
        status = Status.APPLIED;
    }

    public void commit() {
        if (status != Status.APPLIED) {
            throw new IllegalStateException("only an applied frog conversion can be committed");
        }
        status = Status.COMMITTED;
    }
}
