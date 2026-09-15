package com.gameexpert.engine.persistence.finalcarrier.loot;

/** Exact authoritative inventory shape owned by one canonical LOOT assignment. */
public enum CanonicalLootContainerKind {
    CHEST(27), BARREL(27), DISPENSER(9), DECORATED_POT(1);

    private final int slots;

    CanonicalLootContainerKind(int slots) {
        this.slots = slots;
    }

    public int slots() {
        return slots;
    }

    /** True only for a persisted canonical container shape. */
    public static boolean supportsSlots(int slots) {
        for (CanonicalLootContainerKind kind : values()) {
            if (kind.slots == slots) return true;
        }
        return false;
    }
}
