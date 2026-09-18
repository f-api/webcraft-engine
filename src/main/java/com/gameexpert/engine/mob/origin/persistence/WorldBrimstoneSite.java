package com.gameexpert.engine.mob.origin.persistence;

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

@Getter
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "world_brimstone_sites", uniqueConstraints = {
        @UniqueConstraint(name = "uk_brimstone_world_site", columnNames = {"world_id", "site_key"}) })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldBrimstoneSite {
    public static final int SCHEMA_VERSION = 1;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "world_id", nullable = false) private Long worldId;
    @Column(name = "site_key", nullable = false) private long siteKey;
    @Column(name = "cell_x", nullable = false) private int cellX;
    @Column(name = "cell_z", nullable = false) private int cellZ;
    @Column(name = "spawn_x", nullable = false) private int spawnX;
    @Column(name = "spawn_y", nullable = false) private int spawnY;
    @Column(name = "spawn_z", nullable = false) private int spawnZ;
    @Column(name = "active_mob_id", nullable = false) private long activeMobId;
    @Column(name = "last_kill_authority_tick", nullable = false) private long lastKillAuthorityTick = -1;
    @Column(name = "next_eligible_authority_tick", nullable = false) private long nextEligibleAuthorityTick;
    @Column(name = "schema_version", nullable = false) private int schemaVersion = SCHEMA_VERSION;

    public WorldBrimstoneSite(Long worldId, long siteKey, int cellX, int cellZ,
            int spawnX, int spawnY, int spawnZ, long activeMobId) {
        if (worldId == null || activeMobId <= 0) throw new IllegalArgumentException("invalid site");
        this.worldId = worldId; this.siteKey = siteKey; this.cellX = cellX; this.cellZ = cellZ;
        this.spawnX = spawnX; this.spawnY = spawnY; this.spawnZ = spawnZ;
        this.activeMobId = activeMobId;
    }

    void validateCurrentSchema() {
        if (schemaVersion != SCHEMA_VERSION) throw new IllegalStateException("invalid Brimstone schema");
    }

    boolean hasOriginFacts(int expectedCellX, int expectedCellZ,
            int expectedSpawnX, int expectedSpawnY, int expectedSpawnZ) {
        validateCurrentSchema();
        return cellX == expectedCellX && cellZ == expectedCellZ
                && spawnX == expectedSpawnX && spawnY == expectedSpawnY && spawnZ == expectedSpawnZ;
    }

    boolean claim(long mobId, long now) {
        validateCurrentSchema();
        if (activeMobId == mobId) return true;
        if (activeMobId != 0 || now < nextEligibleAuthorityTick) return false;
        activeMobId = mobId;
        return true;
    }

    boolean killed(long mobId, long now, long delay) {
        validateCurrentSchema();
        if (activeMobId != mobId) return false;
        activeMobId = 0;
        lastKillAuthorityTick = now;
        nextEligibleAuthorityTick = Math.addExact(now, delay);
        return true;
    }
}
