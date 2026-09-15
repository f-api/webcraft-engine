package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.feature.Mc263SpringFeature.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dormant exact coordinator for the Minecraft 26.3 FEATURES prefix through step 8.
 *
 * <p>The complete {@code FLUID_SPRINGS} global feature list is registered in pinned index order.
 * Each executor forwards the dispatcher-owned stream directly to the spring leaf with tracing
 * disabled. This class creates no random wrapper, performs no reseed, registers no structure,
 * and has no canonical caller.</p>
 */
public final class Mc263FeaturesStep8Stage {
    public static final int TERMINAL_STEP = 8;
    public static final int STEP_8_REGISTRATION_COUNT = 3;
    public static final String FEATURE_INDEX_RECEIPT_SHA256 =
            Mc263FeaturesStep7Stage.FEATURE_INDEX_RECEIPT_SHA256;

    private static final List<String> EXPECTED_STEP_8 = List.of(
            Source.WATER.featureKey(), Source.LAVA.featureKey(), Source.FROZEN.featureKey());
    private static final Mc263SpringFeature.TraceSink NO_SPRING_TRACE =
            Mc263SpringFeature.TraceSink.disabled();
    private static final List<Registration> STEP_8_REGISTRATIONS = registrationsInOrder();

    private Mc263FeaturesStep8Stage() {
    }

    /** Returns the immutable pinned step-8 registrations in global-index order. */
    public static List<Registration> registrations() {
        verifyReceipt();
        return STEP_8_REGISTRATIONS;
    }

    /** Creates a fresh fail-closed dispatcher containing the complete feature prefix through 8. */
    public static Mc263FeatureDispatcher createDispatcher() {
        verifyReceipt();
        Mc263FeatureDispatcher dispatcher = Mc263FeaturesStep7Stage.createDispatcher();
        for (Registration registration : STEP_8_REGISTRATIONS) {
            dispatcher.registerFeature(registration.featureKey(), registration.executor());
        }
        dispatcher.registerStagePreflight(TERMINAL_STEP,
                Mc263FeaturesStep8Stage::preflightSelected);
        return dispatcher;
    }

    private static void preflightSelected(long worldSeed, Mc263FeaturesRegion region) {
        var selected = Mc263FeaturesPrefixStage.selectedIndices(region, TERMINAL_STEP);
        Mc263FeatureWorldAdapter adapter = new Mc263FeatureWorldAdapter(worldSeed, region);
        for (Source source : Source.values()) {
            if (selected.get(source.globalIndex())) {
                Mc263SpringFeature.preflight(source, adapter::supportsExactOutputState,
                        adapter.supportsTickSidecars());
            }
        }
    }

    /** Executes the exact FEATURES feature prefix 0..8 against one live region. */
    public static Mc263FeatureDispatcher.DispatchResult dispatch(long worldSeed,
            Mc263FeaturesRegion region) {
        return createDispatcher().dispatchThroughStep(
                TERMINAL_STEP, worldSeed, Objects.requireNonNull(region, "region"));
    }

    private static List<Registration> registrationsInOrder() {
        List<Registration> registrations = new ArrayList<>(STEP_8_REGISTRATION_COUNT);
        for (Source source : Source.values()) {
            registrations.add(new Registration(source.globalIndex(), source.featureKey(),
                    context -> placeSpring(context, source)));
        }
        return List.copyOf(registrations);
    }

    private static void placeSpring(Mc263FeatureDispatcher.FeaturePlacementContext context,
            Source source) {
        requireContext(context, source.globalIndex(), source.featureKey());
        Mc263SpringFeature.placeWithFeatureRandom(source,
                context.sourceChunkX() * Blocks.CHUNK_X,
                context.sourceChunkZ() * Blocks.CHUNK_Z,
                new Mc263FeatureWorldAdapter(context.worldSeed(), context.region()).springWorld(),
                context.random(), NO_SPRING_TRACE);
    }

    private static void requireContext(Mc263FeatureDispatcher.FeaturePlacementContext context,
            int expectedIndex, String expectedKey) {
        Objects.requireNonNull(context, "context");
        if (context.step() != TERMINAL_STEP || context.globalIndex() != expectedIndex
                || !context.featureKey().equals(expectedKey)) {
            throw new IllegalStateException("FEATURES step-8 context mismatch for " + expectedKey
                    + ": step=" + context.step() + ", index=" + context.globalIndex()
                    + ", key=" + context.featureKey());
        }
        if (!Mc263FeatureIndexReceipt.step(TERMINAL_STEP).featureAt(expectedIndex)
                .equals(expectedKey)) {
            throw new IllegalStateException("pinned FEATURES step-8 receipt changed for "
                    + expectedKey);
        }
    }

    static void verifyReceipt() {
        if (!FEATURE_INDEX_RECEIPT_SHA256.equals(Mc263FeatureIndexReceipt.RAW_SHA256)
                || !Mc263FeatureIndexReceipt.step(TERMINAL_STEP).featureKeys()
                .equals(EXPECTED_STEP_8)
                || STEP_8_REGISTRATIONS.size() != STEP_8_REGISTRATION_COUNT) {
            throw new IllegalStateException("pinned Minecraft 26.3 FEATURES step 8 changed");
        }
        for (int index = 0; index < STEP_8_REGISTRATIONS.size(); index++) {
            Registration registration = STEP_8_REGISTRATIONS.get(index);
            if (registration.globalIndex() != index
                    || !EXPECTED_STEP_8.get(index).equals(registration.featureKey())) {
                throw new IllegalStateException("FEATURES step-8 registration changed: "
                        + registration.featureKey());
            }
        }
    }

    /** One exact guarded dispatcher registration at feature-only fluid-springs step 8. */
    public record Registration(int globalIndex, String featureKey,
                               Mc263FeatureDispatcher.FeatureExecutor executor) {
        public Registration {
            if (globalIndex < 0) {
                throw new IllegalArgumentException("negative FEATURES step-8 global index");
            }
            featureKey = Mc263FeatureBlockState.requireCanonicalResourceKey(
                    featureKey, "feature key");
            Objects.requireNonNull(executor, "executor");
        }
    }
}
