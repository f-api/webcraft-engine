package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.feature.Mc263SpeleothemKernel.BlockPos;
import com.gameexpert.terrain.mc.feature.Mc263SpeleothemKernel.ConfiguredResult;
import com.gameexpert.terrain.mc.feature.Mc263SpeleothemKernel.Kind;
import com.gameexpert.terrain.mc.feature.Mc263SpeleothemKernel.TraceSink;
import com.gameexpert.terrain.mc.feature.Mc263SpeleothemKernel.WorldAccess;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;

/** Exact placed wrappers for Step-7 sulfur-spike and dripstone clusters. */
public final class Mc263SpeleothemClusterFeature {
    public static final int STEP = 7;
    public static final int SULFUR_INDEX = 2;
    public static final int DRIPSTONE_INDEX = 4;
    public static final String FEATURE_CLASS_SHA256 =
            "e7489e2cc428122b4f34002a2996c6d67e300c245321f09b04dd2f40ddec4e8b";
    public static final String UTILS_CLASS_SHA256 =
            "c5488d74ef7d6c38ba5b34284f8fdaaf094011c4ab7446d900aec8585baf338e";
    public static final String SULFUR_CONFIGURED_SHA256 =
            "9bd3aa29832a442a2d9492a167e3ef6d86bccd1046884afc46dc2d6bdc9e6432";
    public static final String SULFUR_PLACED_SHA256 =
            "6d918f73d3980b3655a0f3515c784a0bb074a4af97d3e80dfd71224d7e5d31cf";
    public static final String DRIPSTONE_CONFIGURED_SHA256 =
            "a2e9b1dfd83e8aa2bbcec4d499f8163a9efc88c66d991f45a35496e1a783e948";
    public static final String DRIPSTONE_PLACED_SHA256 =
            "a318dd0e0de016ae94b9341c152070a53a4e4d2c0d85941081bb351f411ff8ca";

    private Mc263SpeleothemClusterFeature() {
    }

    public record PlacementCounts(int candidates, int configuredSuccesses,
                                  int attemptedWrites, int retainedWrites) {
    }

    public static PlacementCounts placeWithFeatureRandom(Kind kind, int sourceX, int sourceZ,
            WorldAccess world, WorldgenRandom random, TraceSink trace) {
        if (kind == null || world == null || random == null || trace == null) {
            throw new IllegalArgumentException("cluster inputs required");
        }
        int count = random.nextInt(49) + 48;
        if (trace.enabled()) trace.record("placed_count", kind.ordinal(), count);
        int successes = 0;
        int attempted = 0;
        int retained = 0;
        for (int i = 0; i < count; i++) {
            int x = sourceX + random.nextInt(16);
            int z = sourceZ + random.nextInt(16);
            int y = random.nextInt(321) - 64;
            boolean biome = world.biomeKey(x, y, z).equals(kind.biome);
            if (trace.enabled()) {
                trace.record("candidate", kind.ordinal(), i, x, y, z, biome ? 1 : 0);
            }
            if (!biome) continue;
            ConfiguredResult result = Mc263SpeleothemKernel.placeCluster(kind, random,
                    new BlockPos(x, y, z), world, trace);
            if (result.placed()) successes++;
            attempted += result.attemptedWrites();
            retained += result.retainedWrites();
        }
        if (trace.enabled()) {
            trace.record("placed_result", kind.ordinal(), count, successes, attempted,
                    retained);
        }
        return new PlacementCounts(count, successes, attempted, retained);
    }
}
