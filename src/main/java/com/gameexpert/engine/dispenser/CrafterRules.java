package com.gameexpert.engine.dispenser;

/**
 * Pure state rules of the pinned 26.3-snapshot-7 {@code CrafterBlock} (javap). The standalone
 * twin is {@code StandaloneDispenserRules.ts}.
 *
 * <p>State byte: bits 0..3 {@code ORIENTATION} as {@code (FrontAndTop ordinal + 2) % 12}, so the
 * block's default {@code NORTH_UP} (ordinal 10) is code 0 and a state-less crafter faces north:
 * 0 NORTH_UP, 1 SOUTH_UP, 2 DOWN_EAST, 3 DOWN_NORTH, 4 DOWN_SOUTH, 5 DOWN_WEST, 6 UP_EAST,
 * 7 UP_NORTH, 8 UP_SOUTH, 9 UP_WEST, 10 WEST_UP, 11 EAST_UP. Bit 4 is {@code TRIGGERED}, bit 5
 * {@code CRAFTING}.
 */
public final class CrafterRules {

    public static final int ORIENTATION_MASK = 0x0f;
    public static final int TRIGGERED = 0x10;
    public static final int CRAFTING = 0x20;
    /** {@code CrafterBlock} default state: {@code ORIENTATION = NORTH_UP} (code 0). */
    public static final int NORTH_UP = 0;
    public static final int ORIENTATIONS = 12;
    /** {@code CrafterBlockEntity.CONTAINER_WIDTH * CONTAINER_HEIGHT}. */
    public static final int SLOTS = 9;
    /** Every slot of the nine-bit disabled-slot mask. */
    public static final int ALL_SLOTS = 0x1ff;
    /** {@code CrafterBlock#dispenseFrom}: {@code setCraftingTicksRemaining(6)}. */
    public static final int CRAFTING_TICKS = 6;
    /** {@code CrafterBlock#neighborChanged}: {@code scheduleTick(pos, this, 4)}. */
    public static final int TRIGGER_DURATION = 4;

    private CrafterRules() {
    }

    public static int orientation(int state) {
        int orientation = state & ORIENTATION_MASK;
        return orientation < ORIENTATIONS ? orientation : NORTH_UP;
    }

    public static int normalizeState(int state) {
        return orientation(state) | state & (TRIGGERED | CRAFTING);
    }

    public static boolean triggered(int state) {
        return (state & TRIGGERED) != 0;
    }

    public static boolean crafting(int state) {
        return (state & CRAFTING) != 0;
    }

    public static int withTriggered(int state, boolean triggered) {
        int normalized = normalizeState(state);
        return triggered ? normalized | TRIGGERED : normalized & ~TRIGGERED;
    }

    public static int withCrafting(int state, boolean crafting) {
        int normalized = normalizeState(state);
        return crafting ? normalized | CRAFTING : normalized & ~CRAFTING;
    }

    /** The {@code FrontAndTop} ordinal of the state's orientation code. */
    public static int frontAndTopOrdinal(int state) {
        return (orientation(state) + 10) % ORIENTATIONS;
    }

    /** The orientation code of a {@code FrontAndTop} ordinal. */
    public static int orientationCode(int frontAndTopOrdinal) {
        return (frontAndTopOrdinal + 2) % ORIENTATIONS;
    }

    public static boolean slotDisabled(int mask, int slot) {
        return slot >= 0 && slot < SLOTS && (mask >>> slot & 1) != 0;
    }

    /**
     * {@code CrafterBlockEntity#getRedstoneSignal}: the number of slots that hold an item or are
     * disabled (a missing row reads as nine empty slots).
     */
    public static int redstoneSignal(com.gameexpert.engine.ChestInventory inventory, int mask) {
        int signal = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            boolean empty = inventory == null || slot >= inventory.slots()
                    || inventory.itemType(slot) == com.gameexpert.engine.inventory.PlayerInventory.EMPTY
                    || inventory.count(slot) <= 0;
            if (!empty || slotDisabled(mask, slot)) signal++;
        }
        return signal;
    }

    /**
     * {@code CrafterBlockEntity#canPlaceItem} (with {@code smallerStackExist}): a disabled slot
     * refuses; a full slot refuses; an empty slot accepts; otherwise the slot accepts only when
     * no later enabled slot is empty or holds the same item and components with a smaller count.
     *
     * @param counts    slot counts (0 = empty)
     * @param maxStacks {@code ItemStack.getMaxStackSize} of each slot's stack
     * @param sameAs    whether each slot holds the same item and components as {@code slot}
     */
    public static boolean canPlaceItem(int mask, int slot, int[] counts, int[] maxStacks,
            boolean[] sameAs) {
        if (slotDisabled(mask, slot)) return false;
        int count = counts[slot];
        if (count > 0 && count >= maxStacks[slot]) return false;
        if (count <= 0) return true;
        for (int other = slot + 1; other < SLOTS; other++) {
            if (slotDisabled(mask, other)) continue;
            if (counts[other] <= 0) return false;
            if (counts[other] < count && sameAs[other]) return false;
        }
        return true;
    }

    /**
     * {@code FrontAndTop#front()} in the dispenser facing code (0 N, 1 E, 2 S, 3 W, 4 up, 5 down):
     * the side the crafter ejects its result from.
     */
    public static int front(int state) {
        return switch (frontAndTopOrdinal(state)) {
            case 0, 1, 2, 3 -> DispenserRules.DOWN;
            case 4, 5, 6, 7 -> DispenserRules.UP;
            case 8 -> DispenserRules.WEST;
            case 9 -> DispenserRules.EAST;
            case 10 -> DispenserRules.NORTH;
            default -> DispenserRules.SOUTH;
        };
    }
}
