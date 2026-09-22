package com.gameexpert.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.ChunkGenerator;
import com.gameexpert.terrain.TerrainAccessor;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** The bounded daylight test must agree with the full propagated sky level everywhere. */
class SunlightThresholdTest {

    private static TerrainAccessor caveWorld() {
        TerrainAccessor accessor = new TerrainAccessor(7, null, null, (seed, chunkX, chunkZ) -> {
            SplittableRandom random = new SplittableRandom(chunkX * 31L + chunkZ * 7919L);
            short[] blocks = new short[Blocks.CHUNK_BLOCKS];
            int[] palette = {Blocks.AIR, Blocks.AIR, Blocks.AIR, Blocks.STONE, Blocks.STONE, Blocks.GLASS,
                    Blocks.DIRT, Blocks.OAK_SLAB};
            for (int y = 40; y < 90; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int id = y < 50 ? Blocks.STONE : y > 80 && random.nextInt(4) != 0 ? Blocks.AIR
                                : palette[random.nextInt(palette.length)];
                        blocks[Blocks.blockIndex(x, y, z)] = (short) id;
                    }
                }
            }
            for (int y = Blocks.MIN_Y; y < 40; y++) {
                for (int i = 0; i < 256; i++) blocks[Blocks.blockIndex(i & 15, y, i >> 4)] = (short) Blocks.STONE;
            }
            short[] heights = new short[256];
            java.util.Arrays.fill(heights, (short) 89);
            return ChunkGenerator.GeneratedChunk.customDimension(chunkX, chunkZ, blocks, heights);
        });
        for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) accessor.generatedChunkForScan(cx, cz);
        return accessor;
    }

    @Test
    void boundedThresholdMatchesFullPropagation() {
        MobLightEngine light = new MobLightEngine(caveWorld());
        SplittableRandom random = new SplittableRandom(99);
        int above = 0;
        for (int i = 0; i < 4000; i++) {
            int x = random.nextInt(-16, 32), y = random.nextInt(45, 92), z = random.nextInt(-16, 32);
            long time = random.nextInt(4) == 0 ? 7_000 : 1_000;
            for (int threshold : new int[] {11, 5, 14}) {
                boolean expected = light.sunlightLevel(x, y, z, time) > threshold;
                assertEquals(expected, light.sunlightAbove(x, y, z, time, threshold),
                        "at " + x + "," + y + "," + z + " time " + time + " threshold " + threshold);
                if (threshold == 11 && expected) above++;
            }
        }
        assertTrue(above > 50, "sample must include lit cells: " + above);
    }
}
