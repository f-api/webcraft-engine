package com.gameexpert.terrain;

import static org.junit.jupiter.api.Assertions.*;

import com.gameexpert.authority.versioned.NeutralFinalChunk;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Section reads used by the light cache must agree with the full-chunk merge they replace. */
class SnapshotSourceSectionTest {
    private static final int SECTION = 4096;

    private static TerrainAccessor.SnapshotSource source(short[] generated,
            TerrainAccessor.ReplayableChunkPatch patch, NeutralFinalChunk carrier) throws Exception {
        Constructor<?> ctor = Arrays.stream(TerrainAccessor.SnapshotSource.class.getDeclaredConstructors())
                .filter(c -> c.getParameterCount() == 7).findFirst().orElseThrow();
        ctor.setAccessible(true);
        return (TerrainAccessor.SnapshotSource) ctor.newInstance(0, 0, generated, patch, Map.of(),
                new short[256], carrier);
    }

    @Test
    void sectionReadsMatchTheFullMerge() throws Exception {
        short[] generated = new short[Blocks.CHUNK_BLOCKS];
        for (int i = 0; i < generated.length; i++) generated[i] = (short) (i % 7);
        TerrainAccessor.ReplayableChunkPatch patch;
        try (var builder = TerrainAccessor.ReplayableChunkPatch.builder(0, 0)) {
            builder.put(3, Blocks.MIN_Y + 16 * 2 + 5, 4, 42, 0);      // section 2: type only
            builder.put(1, Blocks.MIN_Y + 16 * 5 + 1, 1, 43, 3);      // section 5: type and state
            patch = builder.build();
        }
        int stateCell = Blocks.blockIndex(2, Blocks.MIN_Y + 16 * 9 + 7, 2);   // section 9: state only
        NeutralFinalChunk carrier = new NeutralFinalChunk(0, 0, generated,
                Map.of(stateCell, new NeutralFinalChunk.StateOverride(0, 1, 6, "minecraft:x[a=b]")),
                new int[256], new int[256], new int[256], NeutralFinalChunk.Sidecars.EMPTY);
        TerrainAccessor.SnapshotSource source = source(generated, patch, carrier);

        short[] fullTypes = new short[Blocks.CHUNK_BLOCKS];
        byte[] fullStates = new byte[Blocks.CHUNK_BLOCKS];
        source.copyCellsTo(fullTypes, fullStates);
        for (int section = 0; section < Blocks.CHUNK_Y / 16; section++) {
            int start = section * SECTION;
            short[] types = new short[SECTION];
            byte[] states = new byte[SECTION];
            source.copySectionInto(section, types, states);
            assertArrayEquals(Arrays.copyOfRange(fullTypes, start, start + SECTION), types, "types " + section);
            assertArrayEquals(Arrays.copyOfRange(fullStates, start, start + SECTION), states, "states " + section);

            byte[] statesOnly = new byte[SECTION];
            source.copySectionInto(section, null, statesOnly);
            assertArrayEquals(states, statesOnly);

            short[] shared = source.unchangedSectionTypes(section);
            if (shared != null) {
                assertSame(generated, shared);
                assertArrayEquals(types, Arrays.copyOfRange(shared, start, start + SECTION));
            }
            boolean noStates = true;
            for (byte state : states) noStates &= state == 0;
            assertEquals(noStates, source.sectionHasNoStates(section), "states flag " + section);
        }
        assertNull(source.unchangedSectionTypes(2));
        assertNull(source.unchangedSectionTypes(5));
        assertSame(generated, source.unchangedSectionTypes(9));
        assertFalse(source.sectionHasNoStates(5));
        assertFalse(source.sectionHasNoStates(9));
        assertTrue(source.sectionHasNoStates(2));
    }
}
