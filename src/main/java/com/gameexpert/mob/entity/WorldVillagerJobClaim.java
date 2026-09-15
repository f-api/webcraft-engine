package com.gameexpert.mob.entity;

import com.gameexpert.engine.mob.villager.VillagerJobSitePolicy;
import com.gameexpert.mob.dto.VillagerJobClaimSnapshot;
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

/**
 * One villager's durable job state.
 *
 * <p>The two unique constraints are the invariants themselves: one row per villager, and one
 * villager per station cell. The cell columns are nullable so that a nitwit row and a
 * trade-locked row that lost its station carry no cell — MySQL and H2 both treat NULLs in a
 * unique index as distinct, so any number of site-less rows coexist.
 */
@Getter
@Entity
@Table(
        name = "world_villager_job_claims",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_world_villager_job_claim_mob",
                    columnNames = { "world_id", "mob_id" }),
            @UniqueConstraint(
                    name = "uk_world_villager_job_claim_cell",
                    columnNames = { "world_id", "station_code", "site_x", "site_y", "site_z" })
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldVillagerJobClaim {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long worldId;

    @Column(nullable = false)
    private long mobId;

    @Column(nullable = false)
    private int stationCode;

    @Column
    private Integer siteX;

    @Column
    private Integer siteY;

    @Column
    private Integer siteZ;

    @Column(nullable = false, length = 24)
    private String profession;

    @Column(nullable = false)
    private boolean lockedByTrade;

    @Column(nullable = false)
    private int policyVersion;

    public WorldVillagerJobClaim(Long worldId, VillagerJobClaimSnapshot snapshot) {
        this.worldId = worldId;
        this.mobId = snapshot.mobId();
        this.stationCode = snapshot.stationCode();
        this.siteX = snapshot.hasSite() ? snapshot.x() : null;
        this.siteY = snapshot.hasSite() ? snapshot.y() : null;
        this.siteZ = snapshot.hasSite() ? snapshot.z() : null;
        this.profession = snapshot.profession().name();
        this.lockedByTrade = snapshot.lockedByTrade();
        this.policyVersion = snapshot.policyVersion();
    }

    /**
     * Frees the cell columns without touching the profession. The flush lane empties every row it
     * is about to rewrite so a cell handed from one villager to another in the same flush cannot
     * violate the cell constraint mid-write.
     */
    public void clearSite() {
        this.siteX = null;
        this.siteY = null;
        this.siteZ = null;
    }

    /** Applies a newer decision for the same villager onto the existing row. */
    public void apply(VillagerJobClaimSnapshot snapshot) {
        this.stationCode = snapshot.stationCode();
        this.siteX = snapshot.hasSite() ? snapshot.x() : null;
        this.siteY = snapshot.hasSite() ? snapshot.y() : null;
        this.siteZ = snapshot.hasSite() ? snapshot.z() : null;
        this.profession = snapshot.profession().name();
        this.lockedByTrade = snapshot.lockedByTrade();
        this.policyVersion = snapshot.policyVersion();
    }

    /** Returns null for a row this build can no longer interpret, so restore skips it. */
    public VillagerJobClaimSnapshot toSnapshot() {
        VillagerJobSitePolicy.Profession parsed;
        try {
            parsed = VillagerJobSitePolicy.Profession.valueOf(profession);
        } catch (IllegalArgumentException unknownProfession) {
            return null;
        }
        boolean hasSite = siteX != null && siteY != null && siteZ != null;
        try {
            return new VillagerJobClaimSnapshot(mobId, stationCode, hasSite,
                    hasSite ? siteX : 0, hasSite ? siteY : 0, hasSite ? siteZ : 0,
                    parsed, lockedByTrade, policyVersion);
        } catch (IllegalArgumentException invalidRow) {
            return null;
        }
    }
}
