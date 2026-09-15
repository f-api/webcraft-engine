package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import java.util.List;
import java.util.Objects;

/**
 * Dormant exact coordinator for the Minecraft 26.3 FEATURES prefix through step 4.
 *
 * <p>This feature-only {@code SURFACE_STRUCTURES} registration extends the complete step 0..3
 * coordinator with the four pinned configured/placed-feature leaves at global indices 0..3. It
 * does not claim complete structure placement or canonical activation; it has no canonical caller.
 * </p>
 */
public final class Mc263FeaturesStep4Stage {
    public static final int TERMINAL_STEP = 4;
    public static final int STEP_4_REGISTRATION_COUNT = 4;
    public static final String FEATURE_INDEX_RECEIPT_SHA256 =
            Mc263FeaturesStep3Stage.FEATURE_INDEX_RECEIPT_SHA256;

    private static final List<String> EXPECTED_STEP_4 = List.of(
            Mc263IceSpikeFeature.ICE_SPIKE,
            Mc263IcePatchFeature.ICE_PATCH,
            Mc263DesertWellFeature.DESERT_WELL,
            Mc263BlueIceFeature.BLUE_ICE_FEATURE);
    private static final Mc263IceSpikeFeature.TraceSink NO_ICE_SPIKE_TRACE =
            new Mc263IceSpikeFeature.TraceSink() {
                @Override
                public void record(String phase, long... values) {
                    throw new AssertionError("disabled ice-spike trace emitted");
                }

                @Override
                public boolean enabled() {
                    return false;
                }
            };
    private static final Mc263IcePatchFeature.TraceSink NO_ICE_PATCH_TRACE =
            Mc263IcePatchFeature.TraceSink.disabled();
    private static final Mc263DesertWellFeature.TraceSink NO_DESERT_WELL_TRACE =
            Mc263DesertWellFeature.TraceSink.disabled();
    private static final Mc263BlueIceFeature.TraceSink NO_BLUE_ICE_TRACE =
            Mc263BlueIceFeature.TraceSink.disabled();

    private static final List<Registration> STEP_4_REGISTRATIONS = List.of(
            new Registration(0, Mc263IceSpikeFeature.ICE_SPIKE,
                    Mc263FeaturesStep4Stage::placeIceSpike),
            new Registration(1, Mc263IcePatchFeature.ICE_PATCH,
                    Mc263FeaturesStep4Stage::placeIcePatch),
            new Registration(2, Mc263DesertWellFeature.DESERT_WELL,
                    Mc263FeaturesStep4Stage::placeDesertWell),
            new Registration(3, Mc263BlueIceFeature.BLUE_ICE_FEATURE,
                    Mc263FeaturesStep4Stage::placeBlueIce));

    private Mc263FeaturesStep4Stage() {
    }

    /** Returns the immutable pinned step-4 feature registrations in global-index order. */
    public static List<Registration> registrations() {
        verifyReceipt();
        return STEP_4_REGISTRATIONS;
    }

    /** Creates a fresh fail-closed dispatcher containing the complete feature prefix through 4. */
    public static Mc263FeatureDispatcher createDispatcher() {
        verifyReceipt();
        Mc263FeatureDispatcher dispatcher = Mc263FeaturesStep3Stage.createDispatcher();
        for (Registration registration : STEP_4_REGISTRATIONS) {
            dispatcher.registerFeature(registration.featureKey(), registration.executor());
        }
        dispatcher.registerStagePreflight(TERMINAL_STEP,
                Mc263FeaturesStep4Stage::preflightSelected);
        return dispatcher;
    }

    private static void preflightSelected(long worldSeed, Mc263FeaturesRegion region) {
        var selected = Mc263FeaturesPrefixStage.selectedIndices(region, TERMINAL_STEP);
        Mc263FeatureWorldAdapter adapter = new Mc263FeatureWorldAdapter(worldSeed, region);
        if (selected.get(0)) Mc263IceSpikeFeature.preflight(adapter::supportsExactOutputState);
        if (selected.get(1)) Mc263IcePatchFeature.preflight(adapter::supportsExactOutputState);
        if (selected.get(2)) {
            Mc263DesertWellFeature.preflight(adapter::supportsExactOutputState,
                    adapter.supportsSuspiciousSandPayload());
        }
        if (selected.get(3)) Mc263BlueIceFeature.preflight(adapter::supportsExactOutputState);
    }

    /** Executes only the complete pinned FEATURES feature prefix 0..4 against one live region. */
    public static Mc263FeatureDispatcher.DispatchResult dispatch(long worldSeed,
            Mc263FeaturesRegion region) {
        return createDispatcher().dispatchThroughStep(
                TERMINAL_STEP, worldSeed, Objects.requireNonNull(region, "region"));
    }

    private static void placeIceSpike(Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 0, Mc263IceSpikeFeature.ICE_SPIKE);
        Mc263IceSpikeFeature.placeWithFeatureRandom(sourceBlockX(context), sourceBlockZ(context),
                world(context), context.random(), NO_ICE_SPIKE_TRACE);
    }

    private static void placeIcePatch(Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 1, Mc263IcePatchFeature.ICE_PATCH);
        Mc263IcePatchFeature.placeWithFeatureRandom(sourceBlockX(context), sourceBlockZ(context),
                world(context), context.random(), NO_ICE_PATCH_TRACE);
    }

    private static void placeDesertWell(Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 2, Mc263DesertWellFeature.DESERT_WELL);
        Mc263DesertWellFeature.placeWithFeatureRandom(sourceBlockX(context), sourceBlockZ(context),
                world(context), context.random(), NO_DESERT_WELL_TRACE);
    }

    private static void placeBlueIce(Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 3, Mc263BlueIceFeature.BLUE_ICE_FEATURE);
        Mc263BlueIceFeature.placeWithFeatureRandom(sourceBlockX(context), sourceBlockZ(context),
                world(context), context.random(), NO_BLUE_ICE_TRACE);
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
            int expectedIndex, String expectedKey) {
        Objects.requireNonNull(context, "context");
        if (context.step() != TERMINAL_STEP || context.globalIndex() != expectedIndex
                || !context.featureKey().equals(expectedKey)) {
            throw new IllegalStateException("FEATURES step-4 context mismatch for " + expectedKey
                    + ": step=" + context.step() + ", index=" + context.globalIndex()
                    + ", key=" + context.featureKey());
        }
        if (!Mc263FeatureIndexReceipt.step(TERMINAL_STEP).featureAt(expectedIndex)
                .equals(expectedKey)) {
            throw new IllegalStateException("pinned FEATURES step-4 receipt changed for "
                    + expectedKey);
        }
    }

    private static void verifyReceipt() {
        if (!FEATURE_INDEX_RECEIPT_SHA256.equals(Mc263FeatureIndexReceipt.RAW_SHA256)
                || !Mc263FeatureIndexReceipt.step(TERMINAL_STEP).featureKeys()
                .equals(EXPECTED_STEP_4)
                || STEP_4_REGISTRATIONS.size() != STEP_4_REGISTRATION_COUNT) {
            throw new IllegalStateException("pinned Minecraft 26.3 FEATURES step 4 changed");
        }
        for (int index = 0; index < STEP_4_REGISTRATIONS.size(); index++) {
            Registration registration = STEP_4_REGISTRATIONS.get(index);
            if (registration.globalIndex() != index
                    || !EXPECTED_STEP_4.get(index).equals(registration.featureKey())) {
                throw new IllegalStateException("FEATURES step-4 registration changed: "
                        + registration.featureKey());
            }
        }
    }

    /** One exact guarded dispatcher registration at feature-only surface-structures step 4. */
    public record Registration(int globalIndex, String featureKey,
                               Mc263FeatureDispatcher.FeatureExecutor executor) {
        public Registration {
            if (globalIndex < 0) {
                throw new IllegalArgumentException("negative FEATURES step-4 global index");
            }
            featureKey = Mc263FeatureBlockState.requireCanonicalResourceKey(
                    featureKey, "feature key");
            Objects.requireNonNull(executor, "executor");
        }
    }
}
