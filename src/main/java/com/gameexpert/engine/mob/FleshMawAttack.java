package com.gameexpert.engine.mob;

/** Ephemeral 10-Hz telegraph. Losing the selected target never redirects a charged shot. */
final class FleshMawAttack {
    private String target, phase = "idle";
    private int ticks, cooldown;

    String phase() { return phase; }
    int ticks() { return ticks; }

    boolean advance(String candidate, double distance, boolean visible) {
        cooldown = Math.max(0, cooldown - 1);
        boolean eligible = candidate != null && visible && Double.isFinite(distance) && distance <= 18 && distance >= 0;
        if ("active".equals(phase)) { phase = "idle"; ticks = 0; }
        if ("anticipation".equals(phase)) {
            if (!eligible || !candidate.equals(target)) {
                phase = "idle"; ticks = 0; target = null; cooldown = 30;
                return false;
            }
            if (--ticks > 0) return false;
            phase = "active"; ticks = 1; cooldown = 30; target = null;
            return true;
        }
        if (eligible && cooldown == 0) {
            target = candidate; phase = "anticipation"; ticks = 10;
        }
        return false;
    }
}
