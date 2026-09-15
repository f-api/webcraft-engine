package com.gameexpert.engine.shelf;

/** Pure pinned-26.3 ShelfBlock state, selection, chain and analog rules. */
public final class ShelfRules {
    public static final int SLOTS = 3;
    public static final int POWERED = 0x04;
    public static final int CHAIN_SHIFT = 3;
    public static final int CHAIN_MASK = 0x18;
    public static final int WATERLOGGED = 0x80;
    public static final int UNCONNECTED = 0;
    public static final int RIGHT = 1;
    public static final int CENTER = 2;
    public static final int LEFT = 3;
    public static final int MAX_CHAIN_LENGTH = 3;

    private ShelfRules() {}

    public static int state(int facing, boolean powered, int chainPart, boolean waterlogged) {
        return (facing & 3) | (powered ? POWERED : 0)
                | ((chainPart & 3) << CHAIN_SHIFT) | (waterlogged ? WATERLOGGED : 0);
    }

    public static int facing(int state) { return state & 3; }
    public static boolean powered(int state) { return (state & POWERED) != 0; }
    public static boolean waterlogged(int state) { return (state & WATERLOGGED) != 0; }
    public static int chainPart(int state) { return (state & CHAIN_MASK) >>> CHAIN_SHIFT; }

    /** Comparator reads only through the back face; occupied slots form bits 0..2. */
    public static int analogOutput(int state, int queriedDirection, boolean[] occupied) {
        if (occupied == null || occupied.length != SLOTS
                || queriedDirection != ((facing(state) + 2) & 3)) return 0;
        return (occupied[0] ? 1 : 0) | (occupied[1] ? 2 : 0) | (occupied[2] ? 4 : 0);
    }

    /** Exact SelectableSlotContainer front-face projection. */
    public static int hitSlot(int state, int faceX, int faceZ, double localX, double localZ) {
        int facing = facing(state);
        int frontX = facing == 1 ? 1 : facing == 3 ? -1 : 0;
        int frontZ = facing == 2 ? 1 : facing == 0 ? -1 : 0;
        if (faceX != frontX || faceZ != frontZ) return -1;
        double u = switch (facing) {
            case 0 -> 1.0 - localX;
            case 1 -> 1.0 - localZ;
            case 2 -> localX;
            default -> localZ;
        };
        return Math.max(0, Math.min(2, (int) Math.floor(u * SLOTS)));
    }

    /** State part for a contiguous powered run, capped to the official three-shelf maximum. */
    public static int chainPart(int index, int length) {
        if (length <= 1 || length > MAX_CHAIN_LENGTH || index < 0 || index >= length) {
            return UNCONNECTED;
        }
        if (index == 0) return LEFT;
        if (index == length - 1) return RIGHT;
        return CENTER;
    }
}
