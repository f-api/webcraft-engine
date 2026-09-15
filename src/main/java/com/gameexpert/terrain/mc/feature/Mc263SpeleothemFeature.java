package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.feature.Mc263SpeleothemKernel.BlockPos;
import com.gameexpert.terrain.mc.feature.Mc263SpeleothemKernel.ConfiguredResult;
import com.gameexpert.terrain.mc.feature.Mc263SpeleothemKernel.Direction;
import com.gameexpert.terrain.mc.feature.Mc263SpeleothemKernel.Kind;
import com.gameexpert.terrain.mc.feature.Mc263SpeleothemKernel.TraceSink;
import com.gameexpert.terrain.mc.feature.Mc263SpeleothemKernel.WorldAccess;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;

/** Exact placed wrappers for Step-7 sulfur-spike and pointed-dripstone features. */
public final class Mc263SpeleothemFeature {
    public static final int STEP = 7;
    public static final int SULFUR_INDEX = 3;
    public static final int DRIPSTONE_INDEX = 5;
    public static final String FEATURE_CLASS_SHA256 =
            "cdb8d7ec8c53f68d9bb102f143e8ca9ddc90a50ea0a1e7fab514a5a72381c09a";
    public static final String SELECTOR_CLASS_SHA256 =
            "68ccaacb1b6202ab376067a0ed8b4c6287ad50ca5d014ce0100d963352650302";
    public static final String SULFUR_CONFIGURED_SHA256 =
            "4319d1ca08955a81078916ff8c1b03b9d67829d3e6cbdc1c78858127bbd1bc14";
    public static final String SULFUR_PLACED_SHA256 =
            "1a2bd4f3866dffd99864f7351b5da533cf407504be9b1cf3c37557f0fe3558d9";
    public static final String DRIPSTONE_CONFIGURED_SHA256 =
            "a074eed54c1f5f20e3f1d7baa88cc80c3b1ce8e57326a090b55c2f39043c8fc8";
    public static final String DRIPSTONE_PLACED_SHA256 =
            "f8530ba014c93b70f66a543c60795b18991b788e9538eeaa33cb4a9c0079311d";

    private Mc263SpeleothemFeature() {
    }

    public record PlacementCounts(int outerCandidates, int generatedCandidates,
                                  int configuredSuccesses, int attemptedWrites,
                                  int retainedWrites) {
    }

    public static PlacementCounts placeWithFeatureRandom(Kind kind, int sourceX, int sourceZ,
            WorldAccess world, WorldgenRandom random, TraceSink trace) {
        if (kind == null || world == null || random == null || trace == null) {
            throw new IllegalArgumentException("speleothem inputs required");
        }
        int outer = random.nextInt(65) + 192;
        int generated = 0;
        int successes = 0;
        int attempted = 0;
        int retained = 0;
        if (trace.enabled()) trace.record("placed_count", kind.ordinal(), outer);
        for (int i = 0; i < outer; i++) {
            int baseX = sourceX + random.nextInt(16);
            int baseZ = sourceZ + random.nextInt(16);
            int baseY = random.nextInt(321) - 64;
            int duplicates = random.nextInt(5) + 1;
            for (int duplicate = 0; duplicate < duplicates; duplicate++) {
                generated++;
                int x = baseX + clampedNormalInt(random, 3.0F, -10, 10);
                int y = baseY + clampedNormalInt(random, 0.6F, -2, 2);
                int z = baseZ + clampedNormalInt(random, 3.0F, -10, 10);
                boolean biome = world.biomeKey(x, y, z).equals(kind.biome);
                if (trace.enabled()) {
                    trace.record("candidate", kind.ordinal(), i, duplicate, x, y, z,
                            biome ? 1 : 0);
                }
                if (!biome) continue;
                Direction search = random.nextInt(2) == 0 ? Direction.DOWN : Direction.UP;
                BlockPos scanned = Mc263SpeleothemKernel.scanForSingle(world,
                        new BlockPos(x, y, z), search, trace);
                if (scanned == null) continue;
                ConfiguredResult result = Mc263SpeleothemKernel.placeSingle(kind, random,
                        scanned, world, trace);
                if (result.placed()) successes++;
                attempted += result.attemptedWrites();
                retained += result.retainedWrites();
            }
        }
        if (trace.enabled()) {
            trace.record("placed_result", kind.ordinal(), outer, generated, successes,
                    attempted, retained);
        }
        return new PlacementCounts(outer, generated, successes, attempted, retained);
    }

    private static int clampedNormalInt(WorldgenRandom random, float deviation, int min, int max) {
        float value = (float) random.nextGaussian() * deviation;
        return (int) Math.min(Math.max(value, min), max);
    }
}
