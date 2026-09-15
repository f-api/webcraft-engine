package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/**
 * Dormant, exact coordinator for the complete Minecraft 26.3 FEATURES prefix through step 2.
 *
 * <p>The dispatcher remains the sole owner of decoration/feature seeding and source ordering.
 * Every registration consumes the independently seeded random stream supplied in its placement
 * context and forwards that same object exactly once to its leaf. This class is not wired into the
 * canonical terrain generator.</p>
 */
public final class Mc263FeaturesPrefixStage {
    public static final int TERMINAL_STEP = 2;
    public static final int REGISTRATION_COUNT = 9;
    public static final String FEATURE_INDEX_RECEIPT_SHA256 =
            "c32a12ce6a6efbc873a1e4a20986bb27106cdf9d03f8ccfaeade7010b8a724cf";

    private static final List<String> EXPECTED_STEP_0 = List.of();
    private static final List<String> EXPECTED_STEP_1 = List.of(
            Mc263LakeFeature.LAKE_LAVA_UNDERGROUND,
            Mc263LakeFeature.LAKE_LAVA_SURFACE,
            Mc263RootedSulfurSpringFeature.ROOTED_SULFUR_SPRING,
            Mc263SulfurPoolFeature.SULFUR_POOL);
    private static final List<String> EXPECTED_STEP_2 = List.of(
            Mc263IcebergFeature.ICEBERG_PACKED,
            Mc263IcebergFeature.ICEBERG_BLUE,
            Mc263GeodeFeature.AMETHYST_GEODE,
            Mc263LargeDripstoneFeature.LARGE_DRIPSTONE,
            Mc263ForestRockFeature.FOREST_ROCK);

    private static final Mc263LakeFeature.TraceSink NO_LAKE_TRACE =
            new Mc263LakeFeature.TraceSink() {
                @Override
                public void record(String phase, long... values) {
                }

                @Override
                public boolean enabled() {
                    return false;
                }
            };
    private static final Mc263RootedSulfurSpringFeature.OfficialTraceSink NO_ROOTED_OFFICIAL_TRACE =
            new Mc263RootedSulfurSpringFeature.OfficialTraceSink() {
                @Override
                public void record(String phase, long... values) {
                }

                @Override
                public boolean enabled() {
                    return false;
                }
            };
    private static final Mc263RootedSulfurSpringFeature.OriginalityTraceSink
            NO_ROOTED_ORIGINALITY_TRACE =
            new Mc263RootedSulfurSpringFeature.OriginalityTraceSink() {
                @Override
                public void record(String phase, long... values) {
                }

                @Override
                public boolean enabled() {
                    return false;
                }
            };
    private static final Mc263IcebergFeature.TraceSink NO_ICEBERG_TRACE =
            new Mc263IcebergFeature.TraceSink() {
                @Override
                public void record(String phase, long... values) {
                }

                @Override
                public boolean enabled() {
                    return false;
                }
            };
    private static final Mc263LargeDripstoneFeature.TraceSink NO_LARGE_DRIPSTONE_TRACE =
            new Mc263LargeDripstoneFeature.TraceSink() {
                @Override
                public void record(String phase, long... values) {
                }

                @Override
                public boolean enabled() {
                    return false;
                }
            };
    private static final Mc263ForestRockFeature.TraceSink NO_FOREST_ROCK_TRACE =
            new Mc263ForestRockFeature.TraceSink() {
                @Override
                public void record(String phase, long... values) {
                }

                @Override
                public boolean enabled() {
                    return false;
                }
            };

    private static final List<Registration> REGISTRATIONS = List.of(
            new Registration(1, 0, Mc263LakeFeature.LAKE_LAVA_UNDERGROUND,
                    Mc263FeaturesPrefixStage::placeUndergroundLavaLake),
            new Registration(1, 1, Mc263LakeFeature.LAKE_LAVA_SURFACE,
                    Mc263FeaturesPrefixStage::placeSurfaceLavaLake),
            new Registration(1, 2, Mc263RootedSulfurSpringFeature.ROOTED_SULFUR_SPRING,
                    Mc263FeaturesPrefixStage::placeRootedSulfurSpring),
            new Registration(1, 3, Mc263SulfurPoolFeature.SULFUR_POOL,
                    Mc263FeaturesPrefixStage::placeSulfurPool),
            new Registration(2, 0, Mc263IcebergFeature.ICEBERG_PACKED,
                    Mc263FeaturesPrefixStage::placePackedIceberg),
            new Registration(2, 1, Mc263IcebergFeature.ICEBERG_BLUE,
                    Mc263FeaturesPrefixStage::placeBlueIceberg),
            new Registration(2, 2, Mc263GeodeFeature.AMETHYST_GEODE,
                    Mc263FeaturesPrefixStage::placeAmethystGeode),
            new Registration(2, 3, Mc263LargeDripstoneFeature.LARGE_DRIPSTONE,
                    Mc263FeaturesPrefixStage::placeLargeDripstone),
            new Registration(2, 4, Mc263ForestRockFeature.FOREST_ROCK,
                    Mc263FeaturesPrefixStage::placeForestRock));

    private Mc263FeaturesPrefixStage() {
    }

    /** Returns the immutable official step/global-index registrations in execution order. */
    public static List<Registration> registrations() {
        verifyReceipt();
        return REGISTRATIONS;
    }

    /** Creates a fresh fail-closed dispatcher containing exactly the nine prefix executors. */
    public static Mc263FeatureDispatcher createDispatcher() {
        verifyReceipt();
        Mc263FeatureDispatcher dispatcher = new Mc263FeatureDispatcher();
        for (Registration registration : REGISTRATIONS) {
            dispatcher.registerFeature(registration.featureKey(), registration.executor());
        }
        dispatcher.registerStagePreflight(0, (seed, region) -> { });
        dispatcher.registerStagePreflight(1, Mc263FeaturesPrefixStage::preflightStepOne);
        dispatcher.registerStagePreflight(2, Mc263FeaturesPrefixStage::preflightStepTwo);
        return dispatcher;
    }

    private static void preflightStepOne(long worldSeed, Mc263FeaturesRegion region) {
        BitSet selected = selectedIndices(region, 1);
        Mc263FeatureWorldAdapter adapter = new Mc263FeatureWorldAdapter(worldSeed, region);
        if (selected.get(0) || selected.get(1)) {
            Mc263LakeFeature.preflight(adapter::supportsExactOutputState,
                    adapter.supportsTickSidecars(), adapter.supportsPostprocessingSidecar());
        }
        if (selected.get(2)) {
            Mc263RootedSulfurSpringFeature.preflight(adapter::supportsExactOutputState);
        }
        if (selected.get(3)) {
            Mc263SulfurPoolFeature.preflight(adapter::supportsExactOutputState,
                    adapter.supportsTickSidecars(), adapter.supportsPostprocessingSidecar(),
                    adapter.supportsPotentSulfurPayload());
        }
    }

    private static void preflightStepTwo(long worldSeed, Mc263FeaturesRegion region) {
        BitSet selected = selectedIndices(region, 2);
        Mc263FeatureWorldAdapter adapter = new Mc263FeatureWorldAdapter(worldSeed, region);
        if (selected.get(0) || selected.get(1)) {
            Mc263IcebergFeature.preflight(adapter::supportsExactOutputState);
        }
        if (selected.get(2)) {
            Mc263GeodeFeature.preflight(adapter::supportsExactOutputState,
                    adapter.supportsTickSidecars());
        }
        if (selected.get(3)) {
            Mc263LargeDripstoneFeature.preflight(adapter::supportsExactOutputState);
        }
        if (selected.get(4)) {
            Mc263ForestRockFeature.preflight(adapter::supportsExactOutputState);
        }
    }

    static BitSet selectedIndices(Mc263FeaturesRegion region, int step) {
        BitSet selected = new BitSet();
        for (Mc263FeaturesRegion.SourceChunk source : region.sourceSchedule()) {
            for (String biome : region.biomesForSource(source.chunkX(), source.chunkZ())) {
                for (Mc263FeatureIndexReceipt.FeatureReference reference
                        : Mc263FeatureIndexReceipt.biome(biome).featuresAtStep(step)) {
                    selected.set(reference.globalIndex());
                }
            }
        }
        return selected;
    }

    /** Executes only the complete pinned FEATURES prefix 0..2 against the supplied live region. */
    public static Mc263FeatureDispatcher.DispatchResult dispatch(long worldSeed,
            Mc263FeaturesRegion region) {
        return createDispatcher().dispatchThroughStep(
                TERMINAL_STEP, worldSeed, Objects.requireNonNull(region, "region"));
    }

    private static void placeUndergroundLavaLake(
            Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 1, 0, Mc263LakeFeature.LAKE_LAVA_UNDERGROUND);
        Mc263LakeFeature.placeWithFeatureRandom(Mc263LakeFeature.LAKE_LAVA_UNDERGROUND,
                sourceBlockX(context), sourceBlockZ(context), world(context), context.random(),
                NO_LAKE_TRACE);
    }

    private static void placeSurfaceLavaLake(
            Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 1, 1, Mc263LakeFeature.LAKE_LAVA_SURFACE);
        Mc263LakeFeature.placeWithFeatureRandom(Mc263LakeFeature.LAKE_LAVA_SURFACE,
                sourceBlockX(context), sourceBlockZ(context), world(context), context.random(),
                NO_LAKE_TRACE);
    }

    private static void placeRootedSulfurSpring(
            Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 1, 2,
                Mc263RootedSulfurSpringFeature.ROOTED_SULFUR_SPRING);
        Mc263FeatureWorldAdapter adapter = world(context);
        Mc263RootedSulfurSpringFeature.placeWithFeatureRandom(context.worldSeed(),
                sourceBlockX(context), sourceBlockZ(context), adapter.rootedSulfurWorld(),
                context.random(), NO_ROOTED_OFFICIAL_TRACE, NO_ROOTED_ORIGINALITY_TRACE);
    }

    private static void placeSulfurPool(
            Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 1, 3, Mc263SulfurPoolFeature.SULFUR_POOL);
        Mc263SulfurPoolFeature.placeWithFeatureRandom(sourceBlockX(context),
                sourceBlockZ(context), world(context), context.random(), NO_LAKE_TRACE);
    }

    private static void placePackedIceberg(
            Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 2, 0, Mc263IcebergFeature.ICEBERG_PACKED);
        Mc263IcebergFeature.placeWithFeatureRandom(Mc263IcebergFeature.ICEBERG_PACKED,
                sourceBlockX(context), sourceBlockZ(context), world(context), context.random(),
                NO_ICEBERG_TRACE);
    }

    private static void placeBlueIceberg(
            Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 2, 1, Mc263IcebergFeature.ICEBERG_BLUE);
        Mc263IcebergFeature.placeWithFeatureRandom(Mc263IcebergFeature.ICEBERG_BLUE,
                sourceBlockX(context), sourceBlockZ(context), world(context), context.random(),
                NO_ICEBERG_TRACE);
    }

    private static void placeAmethystGeode(
            Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 2, 2, Mc263GeodeFeature.AMETHYST_GEODE);
        Mc263FeatureWorldAdapter adapter = world(context);
        Mc263GeodeFeature.placeWithFeatureRandom(sourceBlockX(context), sourceBlockZ(context),
                adapter.geodeWorld(), context.random());
    }

    private static void placeLargeDripstone(
            Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 2, 3, Mc263LargeDripstoneFeature.LARGE_DRIPSTONE);
        Mc263LargeDripstoneFeature.placeWithFeatureRandom(sourceBlockX(context),
                sourceBlockZ(context), world(context), context.random(),
                NO_LARGE_DRIPSTONE_TRACE);
    }

    private static void placeForestRock(
            Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 2, 4, Mc263ForestRockFeature.FOREST_ROCK);
        Mc263ForestRockFeature.placeWithFeatureRandom(sourceBlockX(context),
                sourceBlockZ(context), world(context), context.random(), NO_FOREST_ROCK_TRACE);
    }

    private static Mc263FeatureWorldAdapter world(
            Mc263FeatureDispatcher.FeaturePlacementContext context) {
        return new Mc263FeatureWorldAdapter(context.worldSeed(), context.region());
    }

    private static int sourceBlockX(Mc263FeatureDispatcher.FeaturePlacementContext context) {
        return context.sourceChunkX() * Blocks.CHUNK_X;
    }

    private static int sourceBlockZ(Mc263FeatureDispatcher.FeaturePlacementContext context) {
        return context.sourceChunkZ() * Blocks.CHUNK_Z;
    }

    private static void requireContext(Mc263FeatureDispatcher.FeaturePlacementContext context,
            int expectedStep, int expectedIndex, String expectedKey) {
        Objects.requireNonNull(context, "context");
        if (context.step() != expectedStep || context.globalIndex() != expectedIndex
                || !context.featureKey().equals(expectedKey)) {
            throw new IllegalStateException("FEATURES prefix context mismatch for " + expectedKey
                    + ": step=" + context.step() + ", index=" + context.globalIndex()
                    + ", key=" + context.featureKey());
        }
        if (!Mc263FeatureIndexReceipt.step(expectedStep).featureAt(expectedIndex)
                .equals(expectedKey)) {
            throw new IllegalStateException("pinned FEATURES prefix receipt changed for "
                    + expectedKey);
        }
    }

    private static void verifyReceipt() {
        if (!FEATURE_INDEX_RECEIPT_SHA256.equals(Mc263FeatureIndexReceipt.RAW_SHA256)
                || !Mc263FeatureIndexReceipt.step(0).featureKeys().equals(EXPECTED_STEP_0)
                || !Mc263FeatureIndexReceipt.step(1).featureKeys().equals(EXPECTED_STEP_1)
                || !Mc263FeatureIndexReceipt.step(2).featureKeys().equals(EXPECTED_STEP_2)
                || REGISTRATIONS.size() != REGISTRATION_COUNT) {
            throw new IllegalStateException("pinned Minecraft 26.3 FEATURES prefix changed");
        }
        for (Registration registration : REGISTRATIONS) {
            if (!Mc263FeatureIndexReceipt.step(registration.step())
                    .featureAt(registration.globalIndex()).equals(registration.featureKey())) {
                throw new IllegalStateException("FEATURES prefix registration changed: "
                        + registration.featureKey());
            }
        }
    }

    /** One exact dispatcher registration, including the guarded executor bound to that receipt. */
    public record Registration(int step, int globalIndex, String featureKey,
                               Mc263FeatureDispatcher.FeatureExecutor executor) {
        public Registration {
            if (step < 0 || step > TERMINAL_STEP || globalIndex < 0) {
                throw new IllegalArgumentException("invalid FEATURES prefix registration");
            }
            featureKey = Mc263FeatureBlockState.requireCanonicalResourceKey(
                    featureKey, "feature key");
            Objects.requireNonNull(executor, "executor");
        }

        @Override
        public String toString() {
            return "Registration[step=" + step + ", globalIndex=" + globalIndex
                    + ", featureKey=" + featureKey + "]";
        }
    }
}
