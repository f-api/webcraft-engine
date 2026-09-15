package com.gameexpert.engine;

/** Authority-tick bandaging. Only the post-consumption healing remainder is durable. */
public final class FleshBandage {
    private String hand;
    private int useTicks;
    private int healingTicks;
    private long damageRevision;
    private boolean ready;

    public boolean begin(String hand, long damageRevision) {
        if (this.hand != null || ready || healingTicks != 0) return false;
        this.hand = java.util.Objects.requireNonNull(hand);
        this.damageRevision = damageRevision;
        useTicks = 20;
        return true;
    }

    /** Returns true once; the caller must atomically consume before activating healing. */
    public boolean advanceUse(String currentHand, boolean held, boolean alive, long damageRevision) {
        if (hand == null) return false;
        if (!alive || !held || !hand.equals(currentHand) || this.damageRevision != damageRevision) {
            cancelUse();
            return false;
        }
        if (ready) return false;
        if (--useTicks > 0) return false;
        ready = true;
        return true;
    }

    public void consumed() {
        if (healingTicks != 0) throw new IllegalStateException("bandage healing already active");
        healingTicks = 60;
        cancelUse();
    }

    /** Four one-HP pulses, 15 online authority ticks apart; no wall-clock catch-up. */
    public boolean advanceHealing(boolean alive) {
        if (!alive) { healingTicks = 0; cancelUse(); return false; }
        if (healingTicks == 0) return false;
        return --healingTicks % 15 == 0;
    }

    public int healingTicks() { return healingTicks; }
    public void restore(int ticks) {
        if (ticks < 0 || ticks > 60) throw new IllegalArgumentException("invalid bandage remainder");
        cancelUse();
        healingTicks = ticks;
    }
    public boolean ready() { return ready; }
    public void cancelUse() { hand = null; useTicks = 0; ready = false; }
}
