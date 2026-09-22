package com.gameexpert.authority.versioned;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** The retained block IDs are palette-packed; every read must return the exact input. */
class NeutralFinalChunkPackingTest {
    private static final int CELLS = 16 * 16 * 384;

    private static NeutralFinalChunk chunk(short[] ids) {
        return new NeutralFinalChunk(3, -7, ids, Map.of(), new int[256], new int[256], new int[256],
                NeutralFinalChunk.Sidecars.EMPTY);
    }

    @Test
    void roundTripsEveryPaletteWidth() {
        SplittableRandom random = new SplittableRandom(42);
        for (int distinct : new int[] {1, 2, 3, 17, 64, 255, 257, 1000, 40000}) {
            short[] palette = new short[distinct];
            for (int i = 0; i < distinct; i++) palette[i] = (short) random.nextInt(65536);
            short[] ids = new short[CELLS];
            for (int i = 0; i < CELLS; i++) ids[i] = palette[random.nextInt(distinct)];
            NeutralFinalChunk chunk = chunk(ids);
            assertArrayEquals(ids, chunk.blockIds(), "distinct=" + distinct);
            assertEquals(CELLS, chunk.blockIdCount());
            for (int i = 0; i < CELLS; i += 97) assertEquals(ids[i], chunk.blockIdAt(i));
            assertEquals(ids[CELLS - 1], chunk.blockIdAt(CELLS - 1));
        }
    }

    @Test
    void equalityAndHashFollowTheBlockIds() {
        short[] ids = new short[CELLS];
        Arrays.fill(ids, (short) 9);
        ids[1234] = (short) 0xfffe;
        NeutralFinalChunk first = chunk(ids);
        NeutralFinalChunk same = chunk(ids.clone());
        ids[1234] = (short) 0xfffd;
        NeutralFinalChunk other = chunk(ids);
        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, other);
    }

    @Test
    void readsDoNotExposeTheRetainedCopy() {
        short[] ids = new short[CELLS];
        NeutralFinalChunk chunk = chunk(ids);
        ids[0] = 5;
        chunk.blockIds()[1] = 6;
        assertEquals(0, chunk.blockIdAt(0));
        assertEquals(0, chunk.blockIdAt(1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> chunk.blockIdAt(CELLS));
    }
}
