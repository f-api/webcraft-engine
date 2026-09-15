package com.gameexpert.mob.dto;

import java.util.Objects;

/** Immutable durable decision for one accepted structure site's initial occupants. */
public final class StructureOccupantClaimSnapshot {
    private final String siteKind;
    private final int cellX;
    private final int cellZ;
    private final long siteKey;
    private final int policyVersion;
    private final int occupantCount;

    public StructureOccupantClaimSnapshot(String siteKind, int cellX, int cellZ,
            long siteKey, int policyVersion, int occupantCount) {
        this.siteKind = Objects.requireNonNull(siteKind, "siteKind");
        if (siteKey < 0 || siteKey > 0xffff_ffffL) {
            throw new IllegalArgumentException("siteKey must be unsigned 32-bit");
        }
        if (policyVersion <= 0 || occupantCount < 0) {
            throw new IllegalArgumentException("invalid structure occupant claim");
        }
        this.cellX = cellX;
        this.cellZ = cellZ;
        this.siteKey = siteKey;
        this.policyVersion = policyVersion;
        this.occupantCount = occupantCount;
    }

    public String siteKind() { return siteKind; }
    public int cellX() { return cellX; }
    public int cellZ() { return cellZ; }
    public long siteKey() { return siteKey; }
    public int policyVersion() { return policyVersion; }
    public int occupantCount() { return occupantCount; }

    public String identityKey() { return siteKind + ':' + cellX + ':' + cellZ; }
}
