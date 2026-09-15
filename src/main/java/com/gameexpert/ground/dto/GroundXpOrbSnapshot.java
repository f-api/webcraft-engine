package com.gameexpert.ground.dto;

import com.gameexpert.engine.XpRules;

/** Exact durable state of one server-authoritative experience orb. */
public record GroundXpOrbSnapshot(
        long entityId,
        int amount,
        double x,
        double y,
        double z,
        double velocityX,
        double velocityY,
        double velocityZ,
        int age) {
    public GroundXpOrbSnapshot {
        if (entityId <= 0 || entityId > GroundMutationCommand.MAX_GROUND_ENTITY_ID) {
            throw new IllegalArgumentException("ground XP orb entity id is invalid");
        }
        if (amount <= 0 || amount > XpRules.XP_ORB_MAX_AMOUNT) {
            throw new IllegalArgumentException("ground XP orb amount is out of range");
        }
        if (age < 0 || age > XpRules.XP_ORB_DESPAWN_AGE) {
            throw new IllegalArgumentException("ground XP orb age is out of range");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(velocityX) || !Double.isFinite(velocityY)
                || !Double.isFinite(velocityZ)) {
            throw new IllegalArgumentException("ground XP orb position or velocity is not finite");
        }
    }
}
