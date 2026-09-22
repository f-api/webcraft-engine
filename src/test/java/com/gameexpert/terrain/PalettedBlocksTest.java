package com.gameexpert.terrain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Every storage form (uniform, 1/2/4/8-bit palette, plain) must read back the exact chunk. */
class PalettedBlocksTest {

    @Test
    void readsBackEverySectionForm() {
        SplittableRandom random = new SplittableRandom(5);
        int[] distinct = {1, 2, 3, 4, 5, 16, 17, 200, 256, 257, 3000, 1, 2, 9, 70, 256, 1, 1, 4, 30, 300, 2, 1, 128};
        short[] blocks = new short[Blocks.CHUNK_BLOCKS];
        for (int section = 0; section < distinct.length; section++) {
            short[] palette = new short[distinct[section]];
            for (int i = 0; i < palette.length; i++) palette[i] = (short) (65535 - random.nextInt(65536));
            int start = section * PalettedBlocks.SECTION_BLOCKS;
            for (int i = 0; i < PalettedBlocks.SECTION_BLOCKS; i++) {
                blocks[start + i] = palette[i < palette.length ? i : random.nextInt(palette.length)];
            }
        }
        PalettedBlocks packed = PalettedBlocks.pack(blocks);
        for (int index = 0; index < blocks.length; index++) {
            assertEquals(Short.toUnsignedInt(blocks[index]), packed.get(index), "cell " + index);
        }
        assertArrayEquals(blocks, packed.toArray());
        short[] slice = new short[5000];
        packed.copyTo(4000, slice, 0, slice.length);
        for (int i = 0; i < slice.length; i++) assertEquals(blocks[4000 + i], slice[i]);
        assertEquals(Short.toUnsignedInt(blocks[0]), packed.uniformBlock(0));
        assertEquals(-1, packed.uniformBlock(1));
        assertTrue(packed.retainedBytes() < 2L * blocks.length);
        // A second pack on the same thread starts from a clean slot table.
        assertArrayEquals(blocks, PalettedBlocks.pack(blocks).toArray());
    }
}
