package com.gameexpert.terrain;

import java.util.Arrays;

/**
 * Immutable 4,096 unsigned 16-bit values of one section, with the same forms as {@link PalettedBlocks}: a single
 * value, a palette with 1, 2, 4 or 8 bits per cell, or the plain values when more than 256 are distinct.
 */
public final class PalettedSection {
    public static final int CELLS = PalettedBlocks.SECTION_BLOCKS;
    private static final ThreadLocal<int[]> SLOT_OF = ThreadLocal.withInitial(() -> new int[65536]);

    private final int uniform;
    private final short[] palette;
    private final long[] words;
    private final int shift;
    private final short[] raw;

    private PalettedSection(int uniform, short[] palette, long[] words, int shift, short[] raw) {
        this.uniform = uniform;
        this.palette = palette;
        this.words = words;
        this.shift = shift;
        this.raw = raw;
    }

    public static PalettedSection pack(short[] values) {
        if (values.length != CELLS) throw new IllegalArgumentException("section must hold " + CELLS + " cells");
        int[] slotOf = SLOT_OF.get();
        short[] found = new short[256];
        int size = 0;
        boolean dense = false;
        try {
            for (short value : values) {
                int key = Short.toUnsignedInt(value);
                if (slotOf[key] != 0) continue;
                if (size == 256) {
                    dense = true;
                    break;
                }
                found[size++] = value;
                slotOf[key] = size;
            }
            if (dense) return new PalettedSection(0, null, null, 0, values.clone());
            if (size == 1) return new PalettedSection(Short.toUnsignedInt(found[0]), null, null, 0, null);
            int shift = size <= 2 ? 0 : size <= 4 ? 1 : size <= 16 ? 2 : 3;
            int perWordShift = 6 - shift;
            int perWordMask = (1 << perWordShift) - 1;
            long[] words = new long[CELLS >>> perWordShift];
            for (int local = 0; local < CELLS; local++) {
                long slot = slotOf[Short.toUnsignedInt(values[local])] - 1;
                words[local >>> perWordShift] |= slot << ((local & perWordMask) << shift);
            }
            return new PalettedSection(0, Arrays.copyOf(found, size), words, shift, null);
        } finally {
            for (int slot = 0; slot < size; slot++) slotOf[Short.toUnsignedInt(found[slot])] = 0;
        }
    }

    /** Unsigned value at a section-local index (0..4095). */
    public int get(int local) {
        if (words == null) return raw == null ? uniform : Short.toUnsignedInt(raw[local]);
        int perWordShift = 6 - shift;
        long word = words[local >>> perWordShift];
        int slot = (int) (word >>> ((local & ((1 << perWordShift) - 1)) << shift)) & ((1 << (1 << shift)) - 1);
        return Short.toUnsignedInt(palette[slot]);
    }

    /** True when every cell holds {@code value}. */
    public boolean isUniform(int value) {
        return words == null && raw == null && uniform == value;
    }
}
