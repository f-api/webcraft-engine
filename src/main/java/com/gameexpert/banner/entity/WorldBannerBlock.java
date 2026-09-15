package com.gameexpert.banner.entity;

import com.gameexpert.api.persistence.WorldAccess;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Durable patterned-banner block entity. Base colour remains the colocated block diff's block ID. */
@Getter
@Entity
@Table(name = "world_banner_blocks",
        uniqueConstraints = @UniqueConstraint(name = "uq_world_banner_xyz",
                columnNames = {"world_id", "x", "y", "z"}),
        indexes = @Index(name = "idx_world_banner_chunk",
                columnList = "world_id, chunk_x, chunk_z"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldBannerBlock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "world_id", nullable = false)
    private WorldAccess world;

    private int x;
    private int y;
    private int z;

    @Column(name = "chunk_x", nullable = false)
    private int chunkX;

    @Column(name = "chunk_z", nullable = false)
    private int chunkZ;

    /** Strict comma-separated Loom pattern wire-name/color pairs; empty string means no overlays. */
    @Column(name = "pattern_data", nullable = false, length = 512)
    private String patternData;

    public WorldBannerBlock(WorldAccess world, int x, int y, int z, String patternData) {
        if (world == null || patternData == null) throw new IllegalArgumentException("banner state required");
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.patternData = patternData;
    }

    public void replacePatternData(String patternData) {
        if (patternData == null) throw new IllegalArgumentException("banner pattern data required");
        this.patternData = patternData;
    }

    @PrePersist
    @PreUpdate
    private void computeChunkCoordinates() {
        chunkX = Math.floorDiv(x, 16);
        chunkZ = Math.floorDiv(z, 16);
    }
}
