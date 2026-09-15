package com.gameexpert.engine;

import com.gameexpert.tnt.dto.PrimedTntSnapshot;

/** Mutable owner-thread state for Minecraft Java 1.21.4's PrimedTnt entity. */
final class PrimedTntEntity {
    final long id;
    double x;
    double y;
    double z;
    double vx;
    double vy;
    double vz;
    int fuse;

    PrimedTntEntity(long id, double x, double y, double z,
            double vx, double vy, double vz, int fuse) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.fuse = fuse;
    }

    static PrimedTntEntity restore(PrimedTntSnapshot snapshot) {
        return new PrimedTntEntity(snapshot.tntId(), snapshot.x(), snapshot.y(), snapshot.z(),
                snapshot.velocityX(), snapshot.velocityY(), snapshot.velocityZ(), snapshot.fuse());
    }

    PrimedTntSnapshot snapshot() {
        return new PrimedTntSnapshot(id, x, y, z, vx, vy, vz, fuse);
    }
}
