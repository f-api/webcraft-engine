package com.gameexpert.frog.persistence.dto;

/** Immutable replay input for one durably begun conversion. */
public final class FrogConversionIntent {
    public enum Phase {
        PENDING,
        APPLIED,
        COMMITTED
    }

    private final long sourceMobId;
    private final long sequence;
    private final int colonyX;
    private final int colonyY;
    private final int colonyZ;
    private final int hostX;
    private final int hostY;
    private final int hostZ;
    private final String deterministicVariant;
    private final Phase phase;

    public FrogConversionIntent(long sourceMobId, long sequence,
            int colonyX, int colonyY, int colonyZ, int hostX, int hostY, int hostZ,
            String deterministicVariant, Phase phase) {
        this.sourceMobId = sourceMobId;
        this.sequence = sequence;
        this.colonyX = colonyX;
        this.colonyY = colonyY;
        this.colonyZ = colonyZ;
        this.hostX = hostX;
        this.hostY = hostY;
        this.hostZ = hostZ;
        this.deterministicVariant = deterministicVariant;
        this.phase = phase;
    }

    public long getSourceMobId() { return sourceMobId; }
    public long getSequence() { return sequence; }
    public int getColonyX() { return colonyX; }
    public int getColonyY() { return colonyY; }
    public int getColonyZ() { return colonyZ; }
    public int getHostX() { return hostX; }
    public int getHostY() { return hostY; }
    public int getHostZ() { return hostZ; }
    public String getDeterministicVariant() { return deterministicVariant; }
    public Phase getPhase() { return phase; }
    public boolean isCommitted() { return phase == Phase.COMMITTED; }
}
