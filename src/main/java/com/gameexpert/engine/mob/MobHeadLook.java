package com.gameexpert.engine.mob;

import java.util.List;

/**
 * [MOB-LOOK] The replicated head look of a mob: vanilla {@code LookControl} + {@code BodyRotationControl}
 * limits driven by a deterministic look target. The standalone twin is {@code StandaloneMobHeadLook.ts}
 * with the same constants and arithmetic.
 *
 * <ul>
 *   <li>Target: the mob's tracked player target when that player is alive, else the nearest living
 *       player within {@link #LOOK_RANGE} blocks of the mob's eyes ({@code LookAtPlayerGoal} with
 *       lookDistance 8). Vanilla starts that goal with probability 0.02 per tick for 40..79 ticks; the
 *       authorities keep it deterministic (no RNG draw) so neither authority's mob random stream moves —
 *       the documented divergence is that an idle mob keeps looking at a nearby player instead of
 *       glancing at them now and then.</li>
 *   <li>Turn rate: {@code LookControl} rotates the head by at most 10 degrees per MC tick
 *       ({@code Mob.getHeadRotSpeed}), i.e. {@link #TURN_PER_AUTHORITY_TICK} per authority tick (two MC
 *       ticks). Without a target the head returns to the body yaw and the pitch to level.</li>
 *   <li>Limits: pitch within +/-{@code getMaxHeadXRot} = 40 degrees, head yaw within +/-75 degrees of the body
 *       ({@code BodyRotationControl} / {@code getMaxHeadYRot}).</li>
 *   <li>Wire ({@code headYaw}, {@code headPitch}, radians): vanilla {@code netHeadYaw = yHeadRot - yBodyRot}
 *       and {@code headPitch = xRot}, positive pitch = looking down, which the client applies to the head
 *       part exactly as vanilla {@code setupAnim} does.</li>
 * </ul>
 */
public final class MobHeadLook {
    public static final double LOOK_RANGE = 8.0;
    public static final double TURN_PER_AUTHORITY_TICK = Math.toRadians(10.0) * 2.0;
    public static final double MAX_HEAD_YAW = Math.toRadians(75.0);
    public static final double MAX_HEAD_PITCH = Math.toRadians(40.0);
    /** Vanilla {@code Player} standing / crouching eye heights. */
    public static final double PLAYER_EYE = 1.62;
    public static final double PLAYER_CROUCH_EYE = 1.27;
    /** Vanilla {@code EntityDimensions.eyeHeight} default: 0.85 of the height. */
    public static final double MOB_EYE_FRACTION = 0.85;

    private MobHeadLook() {}

    /** Advances {@code m.headYawAbs} / {@code m.headPitch} by one authority tick. */
    public static void step(Mob m, List<PlayerSnapshot> players, String trackedTarget) {
        double eyeY = m.y + m.height() * MOB_EYE_FRACTION;
        PlayerSnapshot target = null;
        if (trackedTarget != null && players != null) {
            for (PlayerSnapshot p : players) {
                if (p.alive() && trackedTarget.equals(p.nickname())) { target = p; break; }
            }
        }
        if (target == null && players != null) {
            double best = LOOK_RANGE * LOOK_RANGE;
            for (PlayerSnapshot p : players) {
                if (!p.alive()) continue;
                double dx = p.x() - m.x;
                double dy = p.y() + eye(p) - eyeY;
                double dz = p.z() - m.z;
                double d = dx * dx + dy * dy + dz * dz;
                if (d <= best) { best = d; target = p; }
            }
        }
        double[] next = target == null
                ? stepToward(m.yaw, m.headYawAbs, m.headPitch, false, 0, 0, 0)
                : stepToward(m.yaw, m.headYawAbs, m.headPitch, true,
                        target.x() - m.x, target.y() + eye(target) - eyeY, target.z() - m.z);
        m.headYawAbs = next[0];
        m.headPitch = next[1];
    }

    private static double eye(PlayerSnapshot p) {
        return p.crouching() ? PLAYER_CROUCH_EYE : PLAYER_EYE;
    }

    /**
     * Pure step: returns {headYawAbs, headPitch}. Yaw convention is the authority's (forward =
     * (-sin yaw, -cos yaw)); a NaN head yaw starts at the body yaw.
     */
    public static double[] stepToward(double bodyYaw, double headYawAbs, double headPitch,
            boolean hasTarget, double dx, double dy, double dz) {
        double head = Double.isFinite(headYawAbs) ? headYawAbs : bodyYaw;
        double pitch = Double.isFinite(headPitch) ? headPitch : 0.0;
        double wantedYaw = bodyYaw;
        double wantedPitch = 0.0;
        if (hasTarget) {
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal > 1e-5 || Math.abs(dy) > 1e-5) {
                wantedYaw = horizontal > 1e-5 ? Math.atan2(-dx, -dz) : head;
                wantedPitch = -Math.atan2(dy, horizontal);
            }
        }
        head = rotateTowards(head, wantedYaw, TURN_PER_AUTHORITY_TICK);
        pitch = rotateTowards(pitch, clamp(wantedPitch, MAX_HEAD_PITCH), TURN_PER_AUTHORITY_TICK);
        double relative = clamp(wrap(head - bodyYaw), MAX_HEAD_YAW);
        return new double[] {wrap(bodyYaw + relative), pitch};
    }

    /** Vanilla {@code netHeadYaw} (radians) of the stored head yaw. */
    public static double netHeadYaw(double bodyYaw, double headYawAbs) {
        if (!Double.isFinite(headYawAbs)) return 0.0;
        // Vanilla yRot = PI - authority yaw, so yHeadRot - yBodyRot = bodyYaw - headYawAbs.
        return clamp(wrap(bodyYaw - headYawAbs), MAX_HEAD_YAW);
    }

    /** Wire quantisation: 1/1000 radian. */
    public static double quantize(double radians) {
        return Math.round(radians * 1000.0) / 1000.0;
    }

    static double rotateTowards(double from, double to, double maxStep) {
        double delta = wrap(to - from);
        if (delta > maxStep) delta = maxStep;
        if (delta < -maxStep) delta = -maxStep;
        return wrap(from + delta);
    }

    static double wrap(double angle) {
        double a = angle % (Math.PI * 2);
        if (a > Math.PI) a -= Math.PI * 2;
        if (a < -Math.PI) a += Math.PI * 2;
        return a;
    }

    private static double clamp(double value, double limit) {
        return Math.max(-limit, Math.min(limit, value));
    }
}
