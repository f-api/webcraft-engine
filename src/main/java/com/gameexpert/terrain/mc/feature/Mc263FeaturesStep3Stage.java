package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import java.util.List;
import java.util.Objects;

/**
 * Dormant exact coordinator for the Minecraft 26.3 FEATURES prefix through step 3.
 *
 * <p>The complete step 0..2 coordinator supplies its existing guarded registrations. This class
 * adds exactly the four pinned underground-structure feature indices, forwarding each
 * dispatcher-owned random stream once to the corresponding leaf. It has no canonical caller.</p>
 */
public final class Mc263FeaturesStep3Stage {
    public static final int TERMINAL_STEP = 3;
    public static final int STEP_3_REGISTRATION_COUNT = 4;
    public static final String FEATURE_INDEX_RECEIPT_SHA256 =
            Mc263FeaturesPrefixStage.FEATURE_INDEX_RECEIPT_SHA256;

    private static final List<String> EXPECTED_STEP_3 = List.of(
            Mc263FossilFeature.FOSSIL_UPPER,
            Mc263FossilFeature.FOSSIL_LOWER,
            Mc263MonsterRoomFeature.MONSTER_ROOM,
            Mc263MonsterRoomFeature.MONSTER_ROOM_DEEP);
    private static final Mc263FossilFeature.OfficialTraceSink NO_FOSSIL_OFFICIAL_TRACE =
            new Mc263FossilFeature.OfficialTraceSink() {
                @Override
                public void record(String phase, long... values) {
                }

                @Override
                public boolean enabled() {
                    return false;
                }
            };
    private static final Mc263FossilFeature.OriginalityTraceSink NO_FOSSIL_ORIGINALITY_TRACE =
            new Mc263FossilFeature.OriginalityTraceSink() {
                @Override
                public void record(String phase, long... values) {
                }

                @Override
                public boolean enabled() {
                    return false;
                }
            };
    private static final Mc263MonsterRoomFeature.TraceSink NO_MONSTER_ROOM_TRACE =
            Mc263MonsterRoomFeature.TraceSink.disabled();

    private static final List<Registration> STEP_3_REGISTRATIONS = List.of(
            new Registration(0, Mc263FossilFeature.FOSSIL_UPPER,
                    Mc263FeaturesStep3Stage::placeUpperFossil),
            new Registration(1, Mc263FossilFeature.FOSSIL_LOWER,
                    Mc263FeaturesStep3Stage::placeLowerFossil),
            new Registration(2, Mc263MonsterRoomFeature.MONSTER_ROOM,
                    Mc263FeaturesStep3Stage::placeMonsterRoom),
            new Registration(3, Mc263MonsterRoomFeature.MONSTER_ROOM_DEEP,
                    Mc263FeaturesStep3Stage::placeDeepMonsterRoom));

    private Mc263FeaturesStep3Stage() {
    }

    /** Returns the immutable pinned step-3 registrations in global-index order. */
    public static List<Registration> registrations() {
        verifyReceipt();
        return STEP_3_REGISTRATIONS;
    }

    /** Creates a fresh fail-closed dispatcher containing the complete prefix through step 3. */
    public static Mc263FeatureDispatcher createDispatcher() {
        verifyReceipt();
        Mc263FeatureDispatcher dispatcher = Mc263FeaturesPrefixStage.createDispatcher();
        for (Registration registration : STEP_3_REGISTRATIONS) {
            dispatcher.registerFeature(registration.featureKey(), registration.executor());
        }
        dispatcher.registerStagePreflight(TERMINAL_STEP,
                Mc263FeaturesStep3Stage::preflightSelected);
        return dispatcher;
    }

    private static void preflightSelected(long worldSeed, Mc263FeaturesRegion region) {
        var selected = Mc263FeaturesPrefixStage.selectedIndices(region, TERMINAL_STEP);
        Mc263FeatureWorldAdapter adapter = new Mc263FeatureWorldAdapter(worldSeed, region);
        if (selected.get(0) || selected.get(1)) {
            Mc263FossilFeature.preflight(adapter::supportsExactOutputState);
        }
        if (selected.get(2) || selected.get(3)) {
            Mc263MonsterRoomFeature.preflight(adapter::supportsExactOutputState,
                    adapter.supportsMonsterRoomPayloads());
        }
    }

    /** Executes only the complete pinned FEATURES prefix 0..3 against one live region. */
    public static Mc263FeatureDispatcher.DispatchResult dispatch(long worldSeed,
            Mc263FeaturesRegion region) {
        return createDispatcher().dispatchThroughStep(
                TERMINAL_STEP, worldSeed, Objects.requireNonNull(region, "region"));
    }

    private static void placeUpperFossil(
            Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 0, Mc263FossilFeature.FOSSIL_UPPER);
        Mc263FossilFeature.placeWithFeatureRandom(context.worldSeed(), sourceBlockX(context),
                sourceBlockZ(context), Mc263FossilFeature.FOSSIL_UPPER, world(context),
                context.random(), NO_FOSSIL_OFFICIAL_TRACE, NO_FOSSIL_ORIGINALITY_TRACE);
    }

    private static void placeLowerFossil(
            Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 1, Mc263FossilFeature.FOSSIL_LOWER);
        Mc263FossilFeature.placeWithFeatureRandom(context.worldSeed(), sourceBlockX(context),
                sourceBlockZ(context), Mc263FossilFeature.FOSSIL_LOWER, world(context),
                context.random(),
                NO_FOSSIL_OFFICIAL_TRACE, NO_FOSSIL_ORIGINALITY_TRACE);
    }

    private static void placeMonsterRoom(
            Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 2, Mc263MonsterRoomFeature.MONSTER_ROOM);
        Mc263MonsterRoomFeature.placeWithFeatureRandom(Mc263MonsterRoomFeature.MONSTER_ROOM,
                sourceBlockX(context), sourceBlockZ(context), world(context), context.random(),
                NO_MONSTER_ROOM_TRACE);
    }

    private static void placeDeepMonsterRoom(
            Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 3, Mc263MonsterRoomFeature.MONSTER_ROOM_DEEP);
        Mc263MonsterRoomFeature.placeWithFeatureRandom(
                Mc263MonsterRoomFeature.MONSTER_ROOM_DEEP, sourceBlockX(context),
                sourceBlockZ(context), world(context), context.random(), NO_MONSTER_ROOM_TRACE);
    }

    private static Mc263FeatureWorldAdapter world(
            Mc263FeatureDispatcher.FeaturePlacementContext context) {
        return new Mc263FeatureWorldAdapter(context.worldSeed(), context.region(),
                context.productionContexts());
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
            throw new IllegalStateException("FEATURES step-3 context mismatch for " + expectedKey
                    + ": step=" + context.step() + ", index=" + context.globalIndex()
                    + ", key=" + context.featureKey());
        }
        if (!Mc263FeatureIndexReceipt.step(TERMINAL_STEP).featureAt(expectedIndex)
                .equals(expectedKey)) {
            throw new IllegalStateException("pinned FEATURES step-3 receipt changed for "
                    + expectedKey);
        }
    }

    private static void verifyReceipt() {
        if (!FEATURE_INDEX_RECEIPT_SHA256.equals(Mc263FeatureIndexReceipt.RAW_SHA256)
                || !Mc263FeatureIndexReceipt.step(TERMINAL_STEP).featureKeys()
                .equals(EXPECTED_STEP_3)
                || STEP_3_REGISTRATIONS.size() != STEP_3_REGISTRATION_COUNT) {
            throw new IllegalStateException("pinned Minecraft 26.3 FEATURES step 3 changed");
        }
        for (int index = 0; index < STEP_3_REGISTRATIONS.size(); index++) {
            Registration registration = STEP_3_REGISTRATIONS.get(index);
            if (registration.globalIndex() != index
                    || !EXPECTED_STEP_3.get(index).equals(registration.featureKey())) {
                throw new IllegalStateException("FEATURES step-3 registration changed: "
                        + registration.featureKey());
            }
        }
    }

    /** One exact guarded dispatcher registration at underground-structures step 3. */
    public record Registration(int globalIndex, String featureKey,
                               Mc263FeatureDispatcher.FeatureExecutor executor) {
        public Registration {
            if (globalIndex < 0) {
                throw new IllegalArgumentException("negative FEATURES step-3 global index");
            }
            featureKey = Mc263FeatureBlockState.requireCanonicalResourceKey(
                    featureKey, "feature key");
            Objects.requireNonNull(executor, "executor");
        }
    }
}
