package com.gameexpert.block.snapshot;

import static org.junit.jupiter.api.Assertions.*;

import com.gameexpert.terrain.Blocks;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** The per-section palette table is bounded; a section of 4,096 distinct values must still round-trip. */
class ChunkSnapshotCodecPaletteTest {

    @Test
    void roundTripsSectionsFromOneToEveryDistinctValue() {
        List<Integer> ids = new ArrayList<>();
        for (int id = 0; id < 65536; id++) if (Blocks.isWorldBlockId(id)) ids.add(id);
        SplittableRandom random = new SplittableRandom(11);
        for (int distinctIds : new int[] {1, 5, 300, ids.size()}) {
            short[] types = new short[Blocks.CHUNK_BLOCKS];
            byte[] states = new byte[Blocks.CHUNK_BLOCKS];
            for (int cell = 0; cell < types.length; cell++) {
                types[cell] = (short) (int) ids.get(random.nextInt(Math.min(distinctIds, ids.size())));
                states[cell] = (byte) (distinctIds == ids.size() ? random.nextInt(256) : 0);
            }
            ChunkSnapshot snapshot = new ChunkSnapshot(3L, 4, -5, 0L, 9L, new byte[256], new short[256],
                    types, states);
            byte[] encoded = ChunkSnapshotCodec.encode(snapshot);
            ChunkSnapshot decoded = ChunkSnapshotCodec.decode(encoded);
            assertArrayEquals(types, decoded.getBlockTypes(), "distinct ids " + distinctIds);
            assertArrayEquals(states, decoded.getBlockStates(), "distinct ids " + distinctIds);
            assertArrayEquals(encoded, ChunkSnapshotCodec.encode(snapshot));
        }
    }
}
