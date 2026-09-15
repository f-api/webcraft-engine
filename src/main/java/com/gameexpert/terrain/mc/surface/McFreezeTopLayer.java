package com.gameexpert.terrain.mc.surface;

import static com.gameexpert.terrain.Blocks.AIR;
import static com.gameexpert.terrain.Blocks.ICE;
import static com.gameexpert.terrain.Blocks.PACKED_ICE;
import static com.gameexpert.terrain.Blocks.SNOW;
import static com.gameexpert.terrain.Blocks.WATER_SOURCE;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.LegacyRand;
import com.gameexpert.terrain.mc.OctaveSimplexNoiseSampler;
import com.gameexpert.terrain.mc.biome.McBiomeRegistry;

/** Vanilla {@code freeze_top_layer} feature and biome-weather temperature calculation. */
public final class McFreezeTopLayer {
    public static final float FREEZE_TEMPERATURE = 0.15F;
    private static final int SEA_LEVEL_OFFSET = 17;

    // These are intentionally legacy Java-Random/simplex samplers, not the modern terrain noise
    // stack. Their fixed construction comes from Pumpkin's biome-weather implementation.
    private static final OctaveSimplexNoiseSampler TEMPERATURE_NOISE =
            new OctaveSimplexNoiseSampler(LegacyRand.fromSeed(1234L), new int[] {0});
    private static final OctaveSimplexNoiseSampler FROZEN_OCEAN_NOISE =
            new OctaveSimplexNoiseSampler(LegacyRand.fromSeed(3456L), new int[] {-2, -1, 0});
    private static final OctaveSimplexNoiseSampler FOLIAGE_NOISE =
            new OctaveSimplexNoiseSampler(LegacyRand.fromSeed(2345L), new int[] {0});

    private McFreezeTopLayer() {
    }

    /** Applies the vanilla frozen temperature modifier; non-frozen biomes return the base value. */
    public static float convertTemperature(int x, int z, float temperature, boolean frozenModifier) {
        if (!frozenModifier) return temperature;

        double threshold = FROZEN_OCEAN_NOISE.sample(x * 0.05D, z * 0.05D, false) * 7.0D
                + FOLIAGE_NOISE.sample(x * 0.2D, z * 0.2D, false);
        if (threshold < 0.3D && FOLIAGE_NOISE.sample(x * 0.09D, z * 0.09D, false) < 0.8D) {
            return 0.2F;
        }
        return temperature;
    }

    /**
     * Vanilla biome weather temperature. The f32 operations after the sampler widening point are
     * deliberately written as float expressions, matching Pumpkin/Java's temperature path.
     */
    public static float computeTemperature(int x, int y, int z, float temperature,
            boolean frozenModifier, int seaLevel) {
        float modifiedTemperature = convertTemperature(x, z, temperature, frozenModifier);
        int offsetSeaLevel = seaLevel + SEA_LEVEL_OFFSET;
        if (y <= offsetSeaLevel) return modifiedTemperature;

        float temperatureNoise = (float) (TEMPERATURE_NOISE.sample(x / 8.0D, z / 8.0D, false)
                * 8.0D);
        return modifiedTemperature - (temperatureNoise + (float) y - (float) offsetSeaLevel)
                * 0.05F / 40.0F;
    }

    /** Ice uses the unmodified base temperature and an inclusive comparison. */
    public static boolean shouldFreezeWater(float baseTemperature, int belowBlock) {
        return baseTemperature <= FREEZE_TEMPERATURE && belowBlock == WATER_SOURCE;
    }

    /** Snow uses adjusted temperature and a strict comparison. */
    public static boolean shouldPlaceSnow(float adjustedTemperature, int topBlock, int belowBlock) {
        return adjustedTemperature < FREEZE_TEMPERATURE && topBlock == AIR && belowBlock != AIR
                && !cannotSupportSnowLayer(belowBlock);
    }

    /**
     * The pinned tag's vanilla members are ice, packed ice, and barrier. Only the first two have
     * project IDs. An unknown/undefined project ID fails closed: it cannot receive generated snow.
     */
    public static boolean cannotSupportSnowLayer(int block) {
        if (!Blocks.isWorldBlockId(block)) return true;
        return block == ICE || block == PACKED_ICE;
    }

    /** Applies the feature's sixteen-by-sixteen top-motion-blocking column pass. */
    public static void freezeTopLayer(short[] blocks, int baseX, int baseZ, BiomeResolver biomes) {
        for (int localX = 0; localX < Blocks.CHUNK_X; localX++) {
            for (int localZ = 0; localZ < Blocks.CHUNK_Z; localZ++) {
                int y = topMotionBlockingBlockHeightExclusive(blocks, localX, localZ);
                int belowY = y - 1;
                if (belowY < Blocks.MIN_Y || belowY > Blocks.MAX_Y) continue;

                int x = baseX + localX;
                int z = baseZ + localZ;
                McBiomeRegistry.Biome biome = biomes.biomeAt(x, y, z);
                int belowIndex = Blocks.blockIndex(localX, belowY, localZ);
                if (shouldFreezeWater(biome.temperature(), Short.toUnsignedInt(blocks[belowIndex]))) {
                    blocks[belowIndex] = (short) ICE;
                }

                // A top height at the exclusive build ceiling still has a valid below block for
                // ice, but no addressable block slot for a snow layer in this byte chunk.
                if (y > Blocks.MAX_Y) continue;
                int topIndex = Blocks.blockIndex(localX, y, localZ);
                float topTemperature = computeTemperature(x, y, z, biome.temperature(),
                        biome.frozenModifier(), Blocks.SEA_LEVEL);
                if (shouldPlaceSnow(topTemperature, Short.toUnsignedInt(blocks[topIndex]),
                        Short.toUnsignedInt(blocks[belowIndex]))) {
                    blocks[topIndex] = (short) SNOW;
                }
            }
        }
    }

    /**
     * At this pipeline point the generated palette contains no non-air non-motion block; water is
     * motion-blocking in the vanilla heightmap. Therefore the first non-air cell is the required
     * MOTION_BLOCKING heightmap top.
     */
    private static int topMotionBlockingBlockHeightExclusive(short[] blocks, int localX,
            int localZ) {
        for (int y = Blocks.MAX_Y; y >= Blocks.MIN_Y; y--) {
            if (Short.toUnsignedInt(blocks[Blocks.blockIndex(localX, y, localZ)]) != AIR) return y + 1;
        }
        return Blocks.MIN_Y;
    }

    @FunctionalInterface
    public interface BiomeResolver {
        McBiomeRegistry.Biome biomeAt(int x, int y, int z);
    }
}
