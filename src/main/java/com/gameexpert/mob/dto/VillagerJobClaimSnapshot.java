package com.gameexpert.mob.dto;

import com.gameexpert.engine.mob.villager.VillagerJobSitePolicy;
import java.util.Objects;

/**
 * One durable row of the villager job lane. Three shapes share the lane:
 * <ul>
 *   <li><b>site claim</b> — a job profession with {@code hasSite}: this villager occupies that
 *       station cell. Exactly one villager per cell, exactly one cell per villager.</li>
 *   <li><b>unbound profession</b> — a job profession without a site: the villager traded at least
 *       once, so vanilla keeps the profession permanently even after the station is gone. The row
 *       survives so the lock survives a reconnect.</li>
 *   <li><b>nitwit</b> — {@code stationCode == 0}: the villager never takes a job and owns no
 *       cell. The row exists only so the state survives a reconnect.</li>
 * </ul>
 */
public final class VillagerJobClaimSnapshot {
    private final long mobId;
    private final int stationCode;
    private final boolean hasSite;
    private final int x;
    private final int y;
    private final int z;
    private final VillagerJobSitePolicy.Profession profession;
    private final boolean lockedByTrade;
    private final int policyVersion;

    public VillagerJobClaimSnapshot(long mobId, int stationCode, boolean hasSite,
            int x, int y, int z, VillagerJobSitePolicy.Profession profession,
            boolean lockedByTrade, int policyVersion) {
        this.profession = Objects.requireNonNull(profession, "profession");
        if (mobId <= 0 || policyVersion <= 0) {
            throw new IllegalArgumentException("invalid villager job claim identity");
        }
        if (profession == VillagerJobSitePolicy.Profession.NONE) {
            throw new IllegalArgumentException("NONE is the absence of a row, not a row");
        }
        boolean nitwit = profession == VillagerJobSitePolicy.Profession.NITWIT;
        if (nitwit && (stationCode != 0 || hasSite || lockedByTrade)) {
            throw new IllegalArgumentException("a nitwit owns no station and never trades");
        }
        if (!nitwit) {
            if (VillagerJobSitePolicy.professionFor(stationCode)
                    == VillagerJobSitePolicy.Profession.NONE) {
                throw new IllegalArgumentException("unknown station code " + stationCode);
            }
            if (!hasSite && !lockedByTrade) {
                throw new IllegalArgumentException(
                        "an unlocked profession without a site is a deleted row");
            }
        }
        if (!hasSite && (x != 0 || y != 0 || z != 0)) {
            throw new IllegalArgumentException("a row without a site carries no coordinates");
        }
        this.mobId = mobId;
        this.stationCode = stationCode;
        this.hasSite = hasSite;
        this.x = x;
        this.y = y;
        this.z = z;
        this.lockedByTrade = lockedByTrade;
        this.policyVersion = policyVersion;
    }

    public static VillagerJobClaimSnapshot nitwit(long mobId) {
        return new VillagerJobClaimSnapshot(mobId, 0, false, 0, 0, 0,
                VillagerJobSitePolicy.Profession.NITWIT, false,
                VillagerJobSitePolicy.POLICY_VERSION);
    }

    public static VillagerJobClaimSnapshot siteClaim(long mobId, int stationCode,
            int x, int y, int z, boolean lockedByTrade) {
        return new VillagerJobClaimSnapshot(mobId, stationCode, true, x, y, z,
                VillagerJobSitePolicy.professionFor(stationCode), lockedByTrade,
                VillagerJobSitePolicy.POLICY_VERSION);
    }

    public long mobId() { return mobId; }

    public int stationCode() { return stationCode; }

    public boolean hasSite() { return hasSite; }

    public int x() { return x; }

    public int y() { return y; }

    public int z() { return z; }

    public VillagerJobSitePolicy.Profession profession() { return profession; }

    public boolean lockedByTrade() { return lockedByTrade; }

    public int policyVersion() { return policyVersion; }

    /** Cell identity inside the lane; rows without a site have none. */
    public String cellKey() {
        return hasSite ? VillagerJobSitePolicy.identityKey(stationCode, x, y, z) : null;
    }

    /** Row identity: one row per villager. */
    public String identityKey() {
        return VillagerJobSitePolicy.CLAIM_KIND + ':' + mobId;
    }

    public VillagerJobClaimSnapshot withLockedByTrade(boolean locked) {
        return locked == lockedByTrade ? this
                : new VillagerJobClaimSnapshot(mobId, stationCode, hasSite, x, y, z, profession,
                        locked, policyVersion);
    }

    /** The locked-profession row that survives losing the station cell. */
    public VillagerJobClaimSnapshot withoutSite() {
        return hasSite
                ? new VillagerJobClaimSnapshot(mobId, stationCode, false, 0, 0, 0, profession,
                        lockedByTrade, policyVersion)
                : this;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof VillagerJobClaimSnapshot claim)) return false;
        return mobId == claim.mobId && stationCode == claim.stationCode
                && hasSite == claim.hasSite && x == claim.x && y == claim.y && z == claim.z
                && profession == claim.profession && lockedByTrade == claim.lockedByTrade
                && policyVersion == claim.policyVersion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mobId, stationCode, hasSite, x, y, z, profession, lockedByTrade,
                policyVersion);
    }

    @Override
    public String toString() {
        return "VillagerJobClaimSnapshot[mob=" + mobId + " profession=" + profession
                + " site=" + (hasSite ? stationCode + "@" + x + ',' + y + ',' + z : "none")
                + " locked=" + lockedByTrade + ']';
    }
}
