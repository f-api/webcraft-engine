package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.feature.Mc263SpeleothemKernel.Kind;
import com.gameexpert.terrain.mc.ore.Mc263OreCatalog;
import com.gameexpert.terrain.mc.ore.Mc263OreFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dormant exact coordinator for the Minecraft 26.3 FEATURES prefix through step 7.
 *
 * <p>The complete {@code UNDERGROUND_DECORATION} global feature list is registered in pinned
 * index order. Each executor forwards the dispatcher-owned stream directly to its leaf. This
 * class creates no random wrapper, performs no reseed, registers no structure, and has no
 * canonical caller.</p>
 */
public final class Mc263FeaturesStep7Stage {
    public static final int TERMINAL_STEP = 7;
    public static final int STEP_7_REGISTRATION_COUNT = 7;
    public static final String FEATURE_INDEX_RECEIPT_SHA256 =
            Mc263FeaturesStep6Stage.FEATURE_INDEX_RECEIPT_SHA256;

    private static final List<String> EXPECTED_STEP_7 = List.of(
            Mc263SculkVeinFeature.SCULK_VEIN_FEATURE,
            Mc263SculkPatchFeature.FEATURE,
            "minecraft:sulfur_spike_cluster",
            "minecraft:sulfur_spike",
            "minecraft:dripstone_cluster",
            "minecraft:pointed_dripstone",
            "minecraft:ore_infested");

    private static final Mc263SculkVeinFeature.TraceSink NO_SCULK_VEIN_TRACE =
            Mc263SculkVeinFeature.TraceSink.disabled();
    private static final Mc263SculkPatchFeature.TraceSink NO_SCULK_PATCH_TRACE =
            Mc263SculkPatchFeature.TraceSink.disabled();
    private static final Mc263SpeleothemKernel.TraceSink NO_SPELEOTHEM_TRACE =
            Mc263SpeleothemKernel.TraceSink.disabled();
    private static final Mc263OreFeature.TraceSink NO_ORE_TRACE =
            Mc263OreFeature.TraceSink.disabled();

    private static final List<Registration> STEP_7_REGISTRATIONS = registrationsInOrder();

    private Mc263FeaturesStep7Stage() {
    }

    /** Returns the immutable pinned step-7 registrations in global-index order. */
    public static List<Registration> registrations() {
        verifyReceipt();
        return STEP_7_REGISTRATIONS;
    }

    /** Creates a fresh fail-closed dispatcher containing the complete feature prefix through 7. */
    public static Mc263FeatureDispatcher createDispatcher() {
        verifyReceipt();
        Mc263FeatureDispatcher dispatcher = Mc263FeaturesStep6Stage.createDispatcher();
        for (Registration registration : STEP_7_REGISTRATIONS) {
            dispatcher.registerFeature(registration.featureKey(), registration.executor());
        }
        dispatcher.registerStagePreflight(TERMINAL_STEP,
                Mc263FeaturesStep7Stage::preflightSelected);
        return dispatcher;
    }

    private static void preflightSelected(long worldSeed, Mc263FeaturesRegion region) {
        var selected = Mc263FeaturesPrefixStage.selectedIndices(region, TERMINAL_STEP);
        Mc263FeatureWorldAdapter adapter = new Mc263FeatureWorldAdapter(worldSeed, region);
        if (selected.get(0)) {
            Mc263SculkVeinFeature.preflight(adapter::supportsExactOutputState,
                    adapter.supportsPostprocessingSidecar());
        }
        if (selected.get(1)) {
            Mc263SculkPatchFeature.preflight(adapter::supportsExactOutputState,
                    adapter.supportsPostprocessingSidecar(), adapter.supportsSculkPayloads());
        }
        if (selected.get(2) || selected.get(3)) {
            Mc263SpeleothemKernel.preflight(Kind.SULFUR, adapter::supportsExactOutputState);
        }
        if (selected.get(4) || selected.get(5)) {
            Mc263SpeleothemKernel.preflight(Kind.DRIPSTONE, adapter::supportsExactOutputState);
        }
        if (selected.get(6)) {
            Mc263OreFeature.preflight(Mc263OreCatalog.placedFeature(EXPECTED_STEP_7.get(6)),
                    adapter::supportsExactOutputState);
        }
    }

    /** Executes the exact FEATURES feature prefix 0..7 against one live region. */
    public static Mc263FeatureDispatcher.DispatchResult dispatch(long worldSeed,
            Mc263FeaturesRegion region) {
        return createDispatcher().dispatchThroughStep(
                TERMINAL_STEP, worldSeed, Objects.requireNonNull(region, "region"));
    }

    private static List<Registration> registrationsInOrder() {
        List<Registration> registrations = new ArrayList<>(STEP_7_REGISTRATION_COUNT);
        registrations.add(new Registration(0, EXPECTED_STEP_7.get(0),
                Mc263FeaturesStep7Stage::placeSculkVein));
        registrations.add(new Registration(1, EXPECTED_STEP_7.get(1),
                Mc263FeaturesStep7Stage::placeSculkPatch));
        registrations.add(new Registration(2, EXPECTED_STEP_7.get(2),
                context -> placeSpeleothemCluster(context, 2, Kind.SULFUR)));
        registrations.add(new Registration(3, EXPECTED_STEP_7.get(3),
                context -> placeSpeleothem(context, 3, Kind.SULFUR)));
        registrations.add(new Registration(4, EXPECTED_STEP_7.get(4),
                context -> placeSpeleothemCluster(context, 4, Kind.DRIPSTONE)));
        registrations.add(new Registration(5, EXPECTED_STEP_7.get(5),
                context -> placeSpeleothem(context, 5, Kind.DRIPSTONE)));
        registrations.add(new Registration(6, EXPECTED_STEP_7.get(6),
                Mc263FeaturesStep7Stage::placeInfestedOre));
        return List.copyOf(registrations);
    }

    private static void placeSculkVein(Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 0, EXPECTED_STEP_7.get(0));
        Mc263SculkVeinFeature.placeWithFeatureRandom(
                sourceBlockX(context), sourceBlockZ(context), world(context).sculkVeinWorld(),
                context.random(), NO_SCULK_VEIN_TRACE);
    }

    private static void placeSculkPatch(Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 1, EXPECTED_STEP_7.get(1));
        Mc263SculkPatchFeature.placeWithFeatureRandom(
                sourceBlockX(context), sourceBlockZ(context), world(context).sculkPatchWorld(),
                context.random(), NO_SCULK_PATCH_TRACE);
    }

    private static void placeSpeleothemCluster(
            Mc263FeatureDispatcher.FeaturePlacementContext context,
            int expectedIndex, Kind kind) {
        requireContext(context, expectedIndex, EXPECTED_STEP_7.get(expectedIndex));
        Mc263SpeleothemClusterFeature.placeWithFeatureRandom(kind,
                sourceBlockX(context), sourceBlockZ(context), world(context).speleothemWorld(),
                context.random(), NO_SPELEOTHEM_TRACE);
    }

    private static void placeSpeleothem(Mc263FeatureDispatcher.FeaturePlacementContext context,
            int expectedIndex, Kind kind) {
        requireContext(context, expectedIndex, EXPECTED_STEP_7.get(expectedIndex));
        Mc263SpeleothemFeature.placeWithFeatureRandom(kind,
                sourceBlockX(context), sourceBlockZ(context), world(context).speleothemWorld(),
                context.random(), NO_SPELEOTHEM_TRACE);
    }

    private static void placeInfestedOre(
            Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 6, EXPECTED_STEP_7.get(6));
        Mc263OreFeature.placeWithFeatureRandom(
                Mc263OreCatalog.placedFeature(EXPECTED_STEP_7.get(6)), TERMINAL_STEP,
                sourceBlockX(context), sourceBlockZ(context), world(context).oreWorld(),
                context.random(), NO_ORE_TRACE);
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
            throw new IllegalStateException("FEATURES step-7 context mismatch for " + expectedKey
                    + ": step=" + context.step() + ", index=" + context.globalIndex()
                    + ", key=" + context.featureKey());
        }
        if (!Mc263FeatureIndexReceipt.step(TERMINAL_STEP).featureAt(expectedIndex)
                .equals(expectedKey)) {
            throw new IllegalStateException("pinned FEATURES step-7 receipt changed for "
                    + expectedKey);
        }
    }

    static void verifyReceipt() {
        if (!FEATURE_INDEX_RECEIPT_SHA256.equals(Mc263FeatureIndexReceipt.RAW_SHA256)
                || !Mc263FeatureIndexReceipt.step(TERMINAL_STEP).featureKeys()
                .equals(EXPECTED_STEP_7)
                || STEP_7_REGISTRATIONS.size() != STEP_7_REGISTRATION_COUNT) {
            throw new IllegalStateException("pinned Minecraft 26.3 FEATURES step 7 changed");
        }
        for (int index = 0; index < STEP_7_REGISTRATIONS.size(); index++) {
            Registration registration = STEP_7_REGISTRATIONS.get(index);
            if (registration.globalIndex() != index
                    || !EXPECTED_STEP_7.get(index).equals(registration.featureKey())) {
                throw new IllegalStateException("FEATURES step-7 registration changed: "
                        + registration.featureKey());
            }
        }
    }

    /** One exact guarded dispatcher registration at feature-only underground-decoration step 7. */
    public record Registration(int globalIndex, String featureKey,
                               Mc263FeatureDispatcher.FeatureExecutor executor) {
        public Registration {
            if (globalIndex < 0) {
                throw new IllegalArgumentException("negative FEATURES step-7 global index");
            }
            featureKey = Mc263FeatureBlockState.requireCanonicalResourceKey(
                    featureKey, "feature key");
            Objects.requireNonNull(executor, "executor");
        }
    }
}
