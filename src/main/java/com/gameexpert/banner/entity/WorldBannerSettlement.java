package com.gameexpert.banner.entity;

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

/** Durable idempotency receipt for a player/banner placement transaction. */
@Getter
@Entity
@Table(name = "world_banner_settlements",
        uniqueConstraints = @UniqueConstraint(name = "uq_world_banner_settlement",
                columnNames = {"world_id", "settlement_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldBannerSettlement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "world_id", nullable = false)
    private Long worldId;
    @Column(name = "settlement_id", nullable = false)
    private long settlementId;
    @Column(name = "command_fingerprint", nullable = false, length = 64)
    private String commandFingerprint;

    public WorldBannerSettlement(Long worldId, long settlementId, String commandFingerprint) {
        if (worldId == null || settlementId <= 0 || commandFingerprint == null
                || commandFingerprint.length() != 64) {
            throw new IllegalArgumentException("banner settlement receipt identity required");
        }
        this.worldId = worldId;
        this.settlementId = settlementId;
        this.commandFingerprint = commandFingerprint;
    }

    public boolean matches(String fingerprint) { return commandFingerprint.equals(fingerprint); }
}
