package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Dormant exact coordinator for the complete pinned step-nine FEATURES prefix. */
public final class Mc263FeaturesStep9Stage {
    public static final int TERMINAL_STEP = 9;
    public static final int STEP_9_REGISTRATION_COUNT = 109;
    public static final String FEATURE_INDEX_RECEIPT_SHA256 =
            Mc263FeaturesStep8Stage.FEATURE_INDEX_RECEIPT_SHA256;

    private static final List<String> EXPECTED_STEP_9 =
            Mc263FeatureIndexReceipt.step(TERMINAL_STEP).featureKeys();
    private static final List<Registration> REGISTRATIONS = registrationsInOrder();

    private Mc263FeaturesStep9Stage() { }

    public static List<Registration> registrations() {
        verifyReceipt();
        return REGISTRATIONS;
    }

    public static Mc263FeatureDispatcher createDispatcher() {
        verifyReceipt();
        Mc263FeatureDispatcher dispatcher = Mc263FeaturesStep8Stage.createDispatcher();
        for (Registration registration : REGISTRATIONS) {
            dispatcher.registerFeature(registration.featureKey(), registration.executor());
        }
        dispatcher.registerStagePreflight(TERMINAL_STEP,
                Mc263FeaturesStep9Stage::preflightSelected);
        return dispatcher;
    }

    public static Mc263FeatureDispatcher.DispatchResult dispatch(long worldSeed,
            Mc263FeaturesRegion region) {
        return createDispatcher().dispatchThroughStep(
                TERMINAL_STEP, worldSeed, Objects.requireNonNull(region, "region"));
    }

    private static List<Registration> registrationsInOrder() {
        List<Registration> registrations = new ArrayList<>(STEP_9_REGISTRATION_COUNT);
        for (int index = 0; index < EXPECTED_STEP_9.size(); index++) {
            int globalIndex = index;
            String key = EXPECTED_STEP_9.get(index);
            registrations.add(new Registration(index, key,
                    context -> placeRegistered(context, globalIndex, key)));
        }
        return List.copyOf(registrations);
    }

    private static void preflightSelected(long worldSeed, Mc263FeaturesRegion region) {
        BitSet selected = selectedIndices(region);
        Mc263FeatureWorldAdapter adapter = new Mc263FeatureWorldAdapter(worldSeed, region);
        for (int index = selected.nextSetBit(0); index >= 0;
                index = selected.nextSetBit(index + 1)) {
            preflight(index, adapter);
        }
    }

    private static BitSet selectedIndices(Mc263FeaturesRegion region) {
        BitSet selected = new BitSet(STEP_9_REGISTRATION_COUNT);
        for (Mc263FeaturesRegion.SourceChunk source : region.sourceSchedule()) {
            for (int index : selectedIndicesForBiomes(
                    region.biomesForSource(source.chunkX(), source.chunkZ()))) {
                selected.set(index);
            }
        }
        return selected;
    }

    static List<Integer> selectedIndicesForBiomes(Set<String> biomes) {
        BitSet selected = new BitSet(STEP_9_REGISTRATION_COUNT);
        for (String biome : biomes) {
            for (Mc263FeatureIndexReceipt.FeatureReference reference
                    : Mc263FeatureIndexReceipt.biome(biome).featuresAtStep(TERMINAL_STEP)) {
                selected.set(reference.globalIndex());
            }
        }
        return selected.stream().boxed().toList();
    }

    private static void preflight(int index, Mc263FeatureWorldAdapter adapter) {
        if (isTree(index)) {
            preflightTree(index, adapter);
        } else if (index == 0) {
            Mc263GlowLichenFeature.preflight(adapter.glowLichenWorld());
        } else if (Mc263BambooForestFlowersFeature.coveredIndices().contains(index)) {
            Mc263BambooForestFlowersFeature.preflightPlaced(
                    index, adapter.bambooForestFlowersWorld());
        } else if (Mc263SimpleVegetationFeature.coveredIndices().contains(index)) {
            Mc263SimpleVegetationFeature.preflight(index, adapter.simpleVegetationWorld());
        } else if (Mc263CaveRootVegetationFeature.coveredIndices().contains(index)) {
            Mc263CaveRootVegetationFeature.preflight(index, adapter.caveRootVegetationWorld());
        } else if (index == 34) {
            Mc263RootedAzaleaFeature.preflight(adapter.rootedAzaleaWorld());
        } else if (index == 36) {
            Mc263ClassicVinesCaveFeature.preflight(adapter.classicVinesCaveWorld());
        } else if (Mc263AquaticVegetationFeature.coveredIndices().contains(index)) {
            Mc263AquaticVegetationFeature.preflightPlaced(index, adapter.aquaticVegetationWorld());
        } else if (Mc263BlockColumnVegetationFeature.coveredIndices().contains(index)) {
            Mc263BlockColumnVegetationFeature.preflight(index,
                    adapter.blockColumnVegetationWorld());
        } else if (index == 82) {
            Mc263HugeMushroomFeature.preflight(adapter.hugeMushroomWorld());
        } else if (Mc263NearWaterFireflyFeature.coveredIndices().contains(index)) {
            Mc263NearWaterFireflyFeature.preflight(index, adapter.nearWaterFireflyWorld());
        } else if (Mc263SeagrassFeature.coveredIndices().contains(index)) {
            Mc263SeagrassFeature.preflight(index, adapter.seagrassWorld());
        } else if (index == Mc263VinesFeature.GLOBAL_INDEX) {
            Mc263VinesFeature.preflightPlaced(adapter.vinesWorld());
        } else if (Mc263KelpFeature.coveredIndices().contains(index)) {
            Mc263KelpFeature.preflight(index, adapter.kelpWorld());
        } else {
            throw new IllegalStateException("unowned FEATURES step-nine index " + index);
        }
    }

    private static void placeRegistered(Mc263FeatureDispatcher.FeaturePlacementContext context,
            int index, String key) {
        requireContext(context, index, key);
        int sourceX = context.sourceChunkX() * Blocks.CHUNK_X;
        int sourceZ = context.sourceChunkZ() * Blocks.CHUNK_Z;
        Mc263FeatureWorldAdapter adapter =
                new Mc263FeatureWorldAdapter(context.worldSeed(), context.region());
        if (isTree(index)) {
            placeTree(index, sourceX, sourceZ, context, adapter);
        } else if (index == 0) {
            Mc263GlowLichenFeature.placeWithFeatureRandom(sourceX, sourceZ,
                    adapter.glowLichenWorld(), context.random(),
                    Mc263GlowLichenFeature.TraceSink.disabled());
        } else if (Mc263BambooForestFlowersFeature.coveredIndices().contains(index)) {
            Mc263BambooForestFlowersFeature.placeWithFeatureRandom(index, context.random(),
                    sourceX, 0, sourceZ, adapter.bambooForestFlowersWorld());
        } else if (Mc263SimpleVegetationFeature.coveredIndices().contains(index)) {
            Mc263SimpleVegetationFeature.placeWithFeatureRandom(index, context.random(),
                    sourceX, 0, sourceZ, adapter.simpleVegetationWorld());
        } else if (Mc263CaveRootVegetationFeature.coveredIndices().contains(index)) {
            Mc263CaveRootVegetationFeature.placeWithFeatureRandom(index, context.random(),
                    sourceX, 0, sourceZ, adapter.caveRootVegetationWorld());
        } else if (index == 34) {
            Mc263RootedAzaleaFeature.placeWithFeatureRandom(context.random(), sourceX, 0,
                    sourceZ, adapter.rootedAzaleaWorld());
        } else if (index == 36) {
            Mc263ClassicVinesCaveFeature.placeWithFeatureRandom(context.random(), sourceX, 0,
                    sourceZ, adapter.classicVinesCaveWorld());
        } else if (Mc263AquaticVegetationFeature.coveredIndices().contains(index)) {
            Mc263AquaticVegetationFeature.placeWithFeatureRandom(index, context.random(),
                    sourceX, 0, sourceZ, adapter.aquaticVegetationWorld());
        } else if (Mc263BlockColumnVegetationFeature.coveredIndices().contains(index)) {
            Mc263BlockColumnVegetationFeature.placeWithFeatureRandom(index, context.random(),
                    sourceX, 0, sourceZ, adapter.blockColumnVegetationWorld());
        } else if (index == 82) {
            Mc263HugeMushroomFeature.placeWithFeatureRandom(context.random(), sourceX, 0,
                    sourceZ, adapter.hugeMushroomWorld());
        } else if (Mc263NearWaterFireflyFeature.coveredIndices().contains(index)) {
            Mc263NearWaterFireflyFeature.placeWithFeatureRandom(index, context.random(),
                    sourceX, 0, sourceZ, adapter.nearWaterFireflyWorld());
        } else if (Mc263SeagrassFeature.coveredIndices().contains(index)) {
            Mc263SeagrassFeature.placeWithFeatureRandom(index, context.random(),
                    sourceX, 0, sourceZ, adapter.seagrassWorld());
        } else if (index == Mc263VinesFeature.GLOBAL_INDEX) {
            Mc263VinesFeature.placeWithFeatureRandom(context.random(), sourceX, 0,
                    sourceZ, adapter.vinesWorld());
        } else if (Mc263KelpFeature.coveredIndices().contains(index)) {
            Mc263KelpFeature.placeWithFeatureRandom(index, context.random(),
                    sourceX, 0, sourceZ, adapter.kelpWorld());
        } else {
            throw new IllegalStateException("unowned FEATURES step-nine index " + index);
        }
    }

    private static boolean isTree(int index) {
        return switch (index) {
            case 1, 3, 4, 5, 8, 10, 12, 16, 20, 22, 26, 27, 28, 40, 42, 44, 45,
                    46, 47, 48, 49, 50, 51, 53, 55, 59 -> true;
            default -> false;
        };
    }

    private static void preflightTree(int index, Mc263FeatureWorldAdapter adapter) {
        adapter.preflightTreeIndex(index);
    }

    private static void placeTree(int index, int sourceX, int sourceZ,
            Mc263FeatureDispatcher.FeaturePlacementContext context,
            Mc263FeatureWorldAdapter adapter) {
        adapter.placeTreeIndex(index, sourceX, 0, sourceZ, context.random());
    }

    private static void requireContext(Mc263FeatureDispatcher.FeaturePlacementContext context,
            int index, String key) {
        Objects.requireNonNull(context, "context");
        if (context.step() != TERMINAL_STEP || context.globalIndex() != index
                || !context.featureKey().equals(key)
                || !Mc263FeatureIndexReceipt.step(TERMINAL_STEP).featureAt(index).equals(key)) {
            throw new IllegalStateException("FEATURES step-nine context mismatch for " + key);
        }
    }

    static void verifyReceipt() {
        if (!FEATURE_INDEX_RECEIPT_SHA256.equals(Mc263FeatureIndexReceipt.RAW_SHA256)
                || EXPECTED_STEP_9.size() != STEP_9_REGISTRATION_COUNT
                || REGISTRATIONS.size() != STEP_9_REGISTRATION_COUNT) {
            throw new IllegalStateException("pinned Minecraft 26.3 FEATURES step nine changed");
        }
        for (int index = 0; index < REGISTRATIONS.size(); index++) {
            Registration registration = REGISTRATIONS.get(index);
            if (registration.globalIndex() != index
                    || !EXPECTED_STEP_9.get(index).equals(registration.featureKey())) {
                throw new IllegalStateException("FEATURES step-nine registration changed at "
                        + index);
            }
        }
    }

    public record Registration(int globalIndex, String featureKey,
                               Mc263FeatureDispatcher.FeatureExecutor executor) {
        public Registration {
            if (globalIndex < 0) throw new IllegalArgumentException("negative global index");
            featureKey = Mc263FeatureBlockState.requireCanonicalResourceKey(
                    featureKey, "feature key");
            Objects.requireNonNull(executor, "executor");
        }
    }
}
