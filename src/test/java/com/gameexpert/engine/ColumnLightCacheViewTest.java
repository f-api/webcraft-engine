package com.gameexpert.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.gameexpert.authority.versioned.NeutralFinalChunk;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.PalettedBlocks;
import com.gameexpert.terrain.TerrainAccessor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * The light cache keeps shared, packed and expanded section forms; every form must read exactly what its
 * snapshot source reads, including after an in-place rebind to a mutated source.
 */
class ColumnLightCacheViewTest {

    private static Object cell(int type, int state) throws Exception {
        Class<?> cellClass = Class.forName("com.gameexpert.terrain.TerrainAccessor$SnapshotCell");
        Constructor<?> ctor = cellClass.getDeclaredConstructor(int.class, int.class);
        ctor.setAccessible(true);
        return ctor.newInstance(type, state);
    }

    private static TerrainAccessor.SnapshotSource source(short[] generated,
            TerrainAccessor.ReplayableChunkPatch patch, Map<Integer, Object> overrides,
            NeutralFinalChunk carrier) throws Exception {
        Constructor<?> ctor = Arrays.stream(TerrainAccessor.SnapshotSource.class.getDeclaredConstructors())
                .filter(c -> c.getParameterCount() == 7).findFirst().orElseThrow();
        ctor.setAccessible(true);
        return (TerrainAccessor.SnapshotSource) ctor.newInstance(0, 0, PalettedBlocks.pack(generated), patch,
                overrides, new short[256], carrier);
    }

    private static void assertSameView(Object cache, TerrainAccessor.SnapshotSource source) throws Exception {
        Method type = cache.getClass().getDeclaredMethod("blockTypeAt", int.class);
        Method state = cache.getClass().getDeclaredMethod("blockStateAt", int.class);
        type.setAccessible(true);
        state.setAccessible(true);
        for (int index = 0; index < Blocks.CHUNK_BLOCKS; index++) {
            assertEquals(source.blockTypeAt(index), (int) type.invoke(cache, index), "type " + index);
            assertEquals(source.blockStateAt(index), (int) state.invoke(cache, index), "state " + index);
        }
    }

    @Test
    void everySectionFormReadsLikeItsSource() throws Exception {
        SplittableRandom random = new SplittableRandom(3);
        short[] generated = new short[Blocks.CHUNK_BLOCKS];
        for (int index = 0; index < generated.length; index++) {
            int section = index >>> 12;
            generated[index] = (short) (section < 6 ? 1 : section < 12 ? 1 + random.nextInt(5) : 0);
        }
        TerrainAccessor.ReplayableChunkPatch patch;
        try (var builder = TerrainAccessor.ReplayableChunkPatch.builder(0, 0)) {
            for (int i = 0; i < 40; i++) {
                builder.put(random.nextInt(16), Blocks.MIN_Y + 16 * 3 + random.nextInt(16), random.nextInt(16),
                        7 + random.nextInt(3), random.nextInt(4));
            }
            patch = builder.build();
        }
        Map<Integer, NeutralFinalChunk.StateOverride> finalStates = new HashMap<>();
        for (int i = 0; i < 30; i++) {
            finalStates.put(Blocks.blockIndex(random.nextInt(16), Blocks.MIN_Y + 16 * 8 + random.nextInt(16),
                    random.nextInt(16)), new NeutralFinalChunk.StateOverride(0, 1, 1 + random.nextInt(5), "x:y"));
        }
        NeutralFinalChunk carrier = new NeutralFinalChunk(0, 0, generated, finalStates,
                new int[256], new int[256], new int[256], NeutralFinalChunk.Sidecars.EMPTY);
        Map<Integer, Object> overrides = new HashMap<>();
        overrides.put(Blocks.blockIndex(4, Blocks.MIN_Y + 16 * 14 + 2, 9), cell(33, 2));
        TerrainAccessor.SnapshotSource first = source(generated, patch, overrides, carrier);

        Class<?> cacheClass = Class.forName("com.gameexpert.engine.MobLightEngine$ColumnLightCache");
        Constructor<?> ctor = cacheClass.getDeclaredConstructor(TerrainAccessor.SnapshotSource.class);
        ctor.setAccessible(true);
        Object cache = ctor.newInstance(first);
        assertSameView(cache, first);

        // A block edit in one column: invalidate it, then rebind to the republished source.
        Map<Integer, Object> edited = new HashMap<>(overrides);
        edited.put(Blocks.blockIndex(4, Blocks.MIN_Y + 5, 9), cell(44, 3));
        edited.put(Blocks.blockIndex(4, Blocks.MIN_Y + 16 * 8 + 1, 9), cell(45, 0));
        TerrainAccessor.SnapshotSource second = source(generated, patch, edited, carrier);
        Method invalidate = cacheClass.getDeclaredMethod("invalidateColumn", int.class, int.class);
        Method rebind = cacheClass.getDeclaredMethod("rebind", TerrainAccessor.SnapshotSource.class);
        invalidate.setAccessible(true);
        rebind.setAccessible(true);
        invalidate.invoke(cache, 4, 9);
        rebind.invoke(cache, second);
        assertSameView(cache, second);
    }
}
