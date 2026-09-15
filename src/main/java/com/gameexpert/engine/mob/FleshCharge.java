package com.gameexpert.engine.mob;

/** Ephemeral 10-Hz guardian action; no reload catch-up and no passive touch damage. */
final class FleshCharge {
    private String phase = "idle", targetName;
    private int ticks, cooldown;
    private double startX, startZ, dx, dz;
    private boolean hit;
    String phase() { return phase; }
    int ticks() { return ticks; }
    double damageMultiplier() { return "recovery".equals(phase) ? 1.5 : 1; }
    interface Path { boolean canStep(double dx, double dz); }
    static final class Step {
        final boolean handled, attack;
        final double moveX, moveZ;
        Step(boolean handled, double moveX, double moveZ, boolean attack) {
            this.handled = handled; this.moveX = moveX; this.moveZ = moveZ; this.attack = attack;
        }
    }
    private static final Step HOLD = new Step(true, 0, 0, false);
    private static final Step IDLE = new Step(false, 0, 0, false);
    private Step recover() { phase = "recovery"; ticks = 20; return HOLD; }
    Step advance(double x, double y, double z, boolean grounded, PlayerSnapshot target, boolean visible, Path path) {
        cooldown = Math.max(0, cooldown - 1);
        double distance = target == null ? Double.POSITIVE_INFINITY : Math.hypot(target.x() - x, target.z() - z);
        if ("recovery".equals(phase)) {
            if (--ticks <= 0) { phase = "idle"; ticks = 0; targetName = null; }
            return HOLD;
        }
        if ("active".equals(phase)) {
            double remaining = 8 - Math.hypot(x - startX, z - startZ), step = Math.min(.8, remaining);
            if (!grounded || ticks <= 0 || remaining <= .001 || !path.canStep(dx * step, dz * step)) return recover();
            boolean attack = !hit && target != null && target.nickname().equals(targetName) && visible
                    && distance <= 2.8 && Math.abs(target.y() - y) <= 2.8;
            ticks--; hit |= attack;
            return new Step(true, dx * step, dz * step, attack);
        }
        if ("anticipation".equals(phase)) {
            if (target == null || !target.nickname().equals(targetName) || !visible || !grounded
                    || distance > 18 || Math.abs(target.y() - y) > 1) return recover();
            if (--ticks > 0) return HOLD;
            if (distance < .001) return recover();
            double nextX = (target.x() - x) / distance, nextZ = (target.z() - z) / distance;
            if (!path.canStep(nextX * .8, nextZ * .8)) return recover();
            phase = "active"; ticks = 10; cooldown = 50; startX = x; startZ = z;
            dx = nextX; dz = nextZ; hit = false;
            return HOLD;
        }
        if (cooldown == 0 && target != null && visible && grounded && distance >= 2 && distance <= 12
                && Math.abs(target.y() - y) <= 1) {
            phase = "anticipation"; ticks = 12; targetName = target.nickname(); hit = false;
            return HOLD;
        }
        return IDLE;
    }
}
