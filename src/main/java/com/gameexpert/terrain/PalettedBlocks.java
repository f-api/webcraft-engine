package com.gameexpert.terrain;

import java.util.Arrays;

/**
 * Immutable block IDs of one chunk, stored per 16-high section. A section of one block is a single value; a
 * section of up to 256 distinct blocks keeps a palette and 1, 2, 4 or 8 bits per cell (never straddling a
 * word, so a read is shifts and masks); a denser section keeps its plain 4,096 shorts. Generated terrain is
 * mostly air, stone and a few ores per section, so a resident chunk costs a few kilobytes instead of 196KB.
 */
public final class PalettedBlocks {
    public static final int SECTION_BLOCKS = 16 * Blocks.CHUNK_X * Blocks.CHUNK_Z;
    private static final int SECTION_MASK = SECTION_BLOCKS - 1;
    private static final int SECTION_COUNT = Blocks.CHUNK_Y / 16;

    /** Section value when uniform (words and raw both null). */
    private final short[] uniform = new short[SECTION_COUNT];
    private final short[][] palettes = new short[SECTION_COUNT][];
    private final long[][] words = new long[SECTION_COUNT][];
    /** log2 of bits per cell for a packed section. */
    private final byte[] bitShift = new byte[SECTION_COUNT];
    private final short[][] raw = new short[SECTION_COUNT][];

    private PalettedBlocks() { }

    /** Packs a full chunk array; the array is not retained. */
    public static PalettedBlocks pack(short[] blocks) {
        if (blocks == null || blocks.length != Blocks.CHUNK_BLOCKS) {
            throw new IllegalArgumentException("chunk block array must hold " + Blocks.CHUNK_BLOCKS + " cells");
        }
        PalettedBlocks packed = new PalettedBlocks();
        int[] slotOf = SLOT_OF.get();
        short[] palette = new short[257];
        for (int section = 0; section < SECTION_COUNT; section++) {
            int start = section * SECTION_BLOCKS;
            int size = 0;
            boolean dense = false;
            for (int index = start; index < start + SECTION_BLOCKS; index++) {
                int id = Short.toUnsignedInt(blocks[index]);
                if (slotOf[id] != 0) continue;
                if (size == 256) {
                    dense = true;
                    break;
                }
                palette[size++] = (short) id;
                slotOf[id] = size;
            }
            try {
                if (dense) {
                    packed.raw[section] = Arrays.copyOfRange(blocks, start, start + SECTION_BLOCKS);
                } else if (size == 1) {
                    packed.uniform[section] = palette[0];
                } else {
                    int shift = size <= 2 ? 0 : size <= 4 ? 1 : size <= 16 ? 2 : 3;
                    int bits = 1 << shift;
                    int perWordShift = 6 - shift;
                    long[] packedWords = new long[SECTION_BLOCKS >>> perWordShift];
                    int perWordMask = (1 << perWordShift) - 1;
                    for (int local = 0; local < SECTION_BLOCKS; local++) {
                        long slot = slotOf[Short.toUnsignedInt(blocks[start + local])] - 1;
                        packedWords[local >>> perWordShift] |= slot << ((local & perWordMask) << shift);
                    }
                    packed.palettes[section] = Arrays.copyOf(palette, size);
                    packed.words[section] = packedWords;
                    packed.bitShift[section] = (byte) shift;
                    assert bits <= 8;
                }
            } finally {
                for (int slot = 0; slot < size; slot++) slotOf[Short.toUnsignedInt(palette[slot])] = 0;
            }
        }
        return packed;
    }

    private static final ThreadLocal<int[]> SLOT_OF = ThreadLocal.withInitial(() -> new int[65536]);

    /** Unsigned block ID at a chunk block index. */
    public int get(int index) {
        int section = index >>> 12;
        long[] packedWords = words[section];
        if (packedWords == null) {
            short[] plain = raw[section];
            return Short.toUnsignedInt(plain == null ? uniform[section] : plain[index & SECTION_MASK]);
        }
        int shift = bitShift[section];
        int perWordShift = 6 - shift;
        int local = index & SECTION_MASK;
        long word = packedWords[local >>> perWordShift];
        int slot = (int) (word >>> ((local & ((1 << perWordShift) - 1)) << shift)) & ((1 << (1 << shift)) - 1);
        return Short.toUnsignedInt(palettes[section][slot]);
    }

    /** Copies {@code length} cells starting at chunk index {@code start} into {@code target}. */
    public void copyTo(int start, short[] target, int targetOffset, int length) {
        int index = start;
        int end = start + length;
        int out = targetOffset;
        while (index < end) {
            int section = index >>> 12;
            int sectionEnd = Math.min(end, (section + 1) * SECTION_BLOCKS);
            if (words[section] == null && raw[section] == null) {
                Arrays.fill(target, out, out + (sectionEnd - index), uniform[section]);
                out += sectionEnd - index;
                index = sectionEnd;
            } else if (raw[section] != null) {
                System.arraycopy(raw[section], index & SECTION_MASK, target, out, sectionEnd - index);
                out += sectionEnd - index;
                index = sectionEnd;
            } else {
                while (index < sectionEnd) target[out++] = (short) get(index++);
            }
        }
    }

    public short[] toArray() {
        short[] blocks = new short[Blocks.CHUNK_BLOCKS];
        copyTo(0, blocks, 0, blocks.length);
        return blocks;
    }

    /** The block of a single-block section, or -1 when the section holds more than one block. */
    public int uniformBlock(int section) {
        return words[section] == null && raw[section] == null ? Short.toUnsignedInt(uniform[section]) : -1;
    }

    /** Approximate heap bytes held, for diagnostics. */
    public long retainedBytes() {
        long bytes = 16 + 5 * (16 + 8L * SECTION_COUNT);
        for (int section = 0; section < SECTION_COUNT; section++) {
            if (words[section] != null) bytes += 16 + 8L * words[section].length + 16 + 2L * palettes[section].length;
            if (raw[section] != null) bytes += 16 + 2L * SECTION_BLOCKS;
        }
        return bytes;
    }
}
