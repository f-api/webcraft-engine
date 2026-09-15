package com.gameexpert.tnt.dto;

/** Durable Java 1.21.4 PrimedTnt state. Position is the bottom-center of the 0.98 cube. */
public record PrimedTntSnapshot(
        long tntId,
        double x,
        double y,
        double z,
        double velocityX,
        double velocityY,
        double velocityZ,
        int fuse) {

    public PrimedTntSnapshot {
        if (tntId <= 0 || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(velocityX) || !Double.isFinite(velocityY)
                || !Double.isFinite(velocityZ) || fuse < 0 || fuse > 80) {
            throw new IllegalArgumentException("invalid primed TNT snapshot");
        }
    }
}
