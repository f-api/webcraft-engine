package com.gameexpert.engine.mob;

import java.util.function.BooleanSupplier;

/** Ephemeral 10-TPS action controller. Reload starts with a new, complete telegraph. */
final class FleshPounce {
    private String phase = "idle", targetName;
    private int ticks, cooldown;
    private double startX, startZ, dx, dz, distance;
    private boolean hit;

    String phase() { return phase; }
    int ticks() { return ticks; }

    static final class Step {
        final boolean handled, launch, attack;
        final double moveX, moveZ;
        Step(boolean handled, double moveX, double moveZ, boolean launch, boolean attack) {
            this.handled = handled; this.moveX = moveX; this.moveZ = moveZ;
            this.launch = launch; this.attack = attack;
        }
    }
    private static final Step IDLE = new Step(false, 0, 0, false, false);
    private static final Step HOLD = new Step(true, 0, 0, false, false);
    private Step recover() { phase = "recovery"; ticks = 10; return HOLD; }

    Step advance(double x, double y, double z, boolean grounded, PlayerSnapshot target,
            boolean visible, BooleanSupplier pathClear) {
        cooldown = Math.max(0, cooldown - 1);
        double reach = target == null ? Double.POSITIVE_INFINITY
                : Math.sqrt(Math.pow(target.x() - x, 2) + Math.pow(target.y() - y, 2) + Math.pow(target.z() - z, 2));
        if ("recovery".equals(phase)) {
            if (--ticks > 0) return HOLD;
            phase = "idle"; ticks = 0; targetName = null; return IDLE;
        }
        if ("active".equals(phase)) {
            double remaining = distance - Math.hypot(x - startX, z - startZ);
            if (grounded || ticks <= 0 || remaining <= .001) return recover();
            boolean attack = !hit && target != null && target.nickname().equals(targetName) && visible && reach <= 1.5;
            ticks--; hit |= attack;
            double movement = Math.min(.75, remaining);
            return new Step(true, dx * movement, dz * movement, false, attack);
        }
        if ("anticipation".equals(phase)) {
            if (target == null || !target.nickname().equals(targetName) || !visible || reach > 5 || !grounded) return recover();
            if (--ticks > 0) return HOLD;
            if (!pathClear.getAsBoolean()) return recover();
            double length = Math.hypot(target.x() - x, target.z() - z);
            if (length < .001) return recover();
            phase = "active"; ticks = 10; cooldown = 40; startX = x; startZ = z;
            dx = (target.x() - x) / length; dz = (target.z() - z) / length;
            distance = Math.min(5, length); hit = false;
            return new Step(true, 0, 0, true, false);
        }
        if (cooldown == 0 && grounded && target != null && visible && reach >= 2 && reach <= 5
                && Math.abs(target.y() - y) <= 1 && pathClear.getAsBoolean()) {
            phase = "anticipation"; ticks = 7; targetName = target.nickname(); hit = false;
            return HOLD;
        }
        return IDLE;
    }
}
