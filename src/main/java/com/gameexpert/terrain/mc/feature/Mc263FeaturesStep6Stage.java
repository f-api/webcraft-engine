package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.ore.Mc263OreCatalog;
import com.gameexpert.terrain.mc.ore.Mc263OreFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dormant exact coordinator for the Minecraft 26.3 FEATURES prefix through step 6.
 *
 * <p>The complete {@code UNDERGROUND_ORES} global feature list is registered in pinned index
 * order. Every executor consumes the dispatcher-supplied feature stream directly; this class has
 * no independent seed path and no canonical caller.</p>
 */
public final class Mc263FeaturesStep6Stage {
    public static final int TERMINAL_STEP = 6;
    public static final int STEP_6_REGISTRATION_COUNT = 34;
    public static final String FEATURE_INDEX_RECEIPT_SHA256 =
            Mc263FeaturesStep5Stage.FEATURE_INDEX_RECEIPT_SHA256;

    private static final List<String> EXPECTED_STEP_6 = List.of(
            "minecraft:ore_dirt",
            "minecraft:ore_gravel",
            "minecraft:ore_granite_upper",
            "minecraft:ore_granite_lower",
            "minecraft:ore_diorite_upper",
            "minecraft:ore_diorite_lower",
            "minecraft:ore_andesite_upper",
            "minecraft:ore_andesite_lower",
            "minecraft:ore_tuff",
            "minecraft:ore_coal_upper",
            "minecraft:ore_coal_lower",
            "minecraft:ore_iron_upper",
            "minecraft:ore_iron_middle",
            "minecraft:ore_iron_small",
            "minecraft:ore_gold",
            "minecraft:ore_gold_lower",
            "minecraft:ore_redstone",
            "minecraft:ore_redstone_lower",
            "minecraft:ore_diamond",
            "minecraft:ore_diamond_medium",
            "minecraft:ore_diamond_large",
            "minecraft:ore_diamond_buried",
            "minecraft:ore_lapis",
            "minecraft:ore_lapis_buried",
            "minecraft:ore_copper_large",
            "minecraft:ore_copper",
            Mc263UnderwaterMagmaFeature.FEATURE,
            "minecraft:ore_clay",
            "minecraft:ore_gold_extra",
            Mc263DiskGrassFeature.DISK_GRASS,
            Mc263DiskSandFeature.DISK_SAND,
            Mc263DiskClayFeature.DISK_CLAY,
            Mc263DiskGravelFeature.DISK_GRAVEL,
            "minecraft:ore_emerald");

    private static final Mc263OreFeature.TraceSink NO_ORE_TRACE =
            Mc263OreFeature.TraceSink.disabled();
    private static final Mc263UnderwaterMagmaFeature.TraceSink NO_MAGMA_TRACE =
            Mc263UnderwaterMagmaFeature.TraceSink.disabled();
    private static final Mc263DiskGrassFeature.TraceSink NO_GRASS_TRACE =
            Mc263DiskGrassFeature.TraceSink.disabled();
    private static final Mc263DiskSandFeature.TraceSink NO_SAND_TRACE =
            Mc263DiskSandFeature.TraceSink.disabled();
    private static final Mc263DiskClayFeature.TraceSink NO_CLAY_TRACE =
            Mc263DiskClayFeature.TraceSink.disabled();
    private static final Mc263DiskGravelFeature.TraceSink NO_GRAVEL_TRACE =
            Mc263DiskGravelFeature.TraceSink.disabled();

    private static final List<Registration> STEP_6_REGISTRATIONS = registrationsInOrder();

    private Mc263FeaturesStep6Stage() {
    }

    /** Returns the immutable pinned step-6 registrations in global-index order. */
    public static List<Registration> registrations() {
        verifyReceipt();
        return STEP_6_REGISTRATIONS;
    }

    /** Creates a fresh fail-closed dispatcher containing the complete feature prefix through 6. */
    public static Mc263FeatureDispatcher createDispatcher() {
        verifyReceipt();
        Mc263FeatureDispatcher dispatcher = Mc263FeaturesStep5Stage.createDispatcher();
        for (Registration registration : STEP_6_REGISTRATIONS) {
            dispatcher.registerFeature(registration.featureKey(), registration.executor());
        }
        dispatcher.registerStagePreflight(TERMINAL_STEP,
                Mc263FeaturesStep6Stage::preflightSelected);
        return dispatcher;
    }

    private static void preflightSelected(long worldSeed, Mc263FeaturesRegion region) {
        var selected = Mc263FeaturesPrefixStage.selectedIndices(region, TERMINAL_STEP);
        Mc263FeatureWorldAdapter adapter = new Mc263FeatureWorldAdapter(worldSeed, region);
        for (int index = selected.nextSetBit(0); index >= 0;
                index = selected.nextSetBit(index + 1)) {
            String key = EXPECTED_STEP_6.get(index);
            switch (index) {
                case 26 -> Mc263UnderwaterMagmaFeature.preflight(
                        adapter::supportsExactOutputState);
                case 29 -> Mc263DiskGrassFeature.preflight(adapter::supportsExactOutputState);
                case 30 -> Mc263DiskSandFeature.preflight(adapter::supportsExactOutputState);
                case 31 -> Mc263DiskClayFeature.preflight(adapter::supportsExactOutputState);
                case 32 -> Mc263DiskGravelFeature.preflight(adapter::supportsExactOutputState);
                default -> Mc263OreFeature.preflight(Mc263OreCatalog.placedFeature(key),
                        adapter::supportsExactOutputState);
            }
        }
    }

    /** Executes the exact FEATURES feature prefix 0..6 against one live region. */
    public static Mc263FeatureDispatcher.DispatchResult dispatch(long worldSeed,
            Mc263FeaturesRegion region) {
        return createDispatcher().dispatchThroughStep(
                TERMINAL_STEP, worldSeed, Objects.requireNonNull(region, "region"));
    }

    private static List<Registration> registrationsInOrder() {
        List<Registration> registrations = new ArrayList<>(STEP_6_REGISTRATION_COUNT);
        for (int index = 0; index < EXPECTED_STEP_6.size(); index++) {
            int featureIndex = index;
            String key = EXPECTED_STEP_6.get(index);
            Mc263FeatureDispatcher.FeatureExecutor executor = switch (index) {
                case 26 -> Mc263FeaturesStep6Stage::placeUnderwaterMagma;
                case 29 -> Mc263FeaturesStep6Stage::placeDiskGrass;
                case 30 -> Mc263FeaturesStep6Stage::placeDiskSand;
                case 31 -> Mc263FeaturesStep6Stage::placeDiskClay;
                case 32 -> Mc263FeaturesStep6Stage::placeDiskGravel;
                default -> context -> placeOre(context, featureIndex, key);
            };
            registrations.add(new Registration(index, key, executor));
        }
        return List.copyOf(registrations);
    }

    private static void placeOre(Mc263FeatureDispatcher.FeaturePlacementContext context,
            int expectedIndex, String expectedKey) {
        requireContext(context, expectedIndex, expectedKey);
        Mc263OreFeature.placeWithFeatureRandom(Mc263OreCatalog.placedFeature(expectedKey),
                TERMINAL_STEP, sourceBlockX(context), sourceBlockZ(context),
                world(context).oreWorld(), context.random(), NO_ORE_TRACE);
    }

    private static void placeUnderwaterMagma(
            Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 26, Mc263UnderwaterMagmaFeature.FEATURE);
        Mc263UnderwaterMagmaFeature.placeWithFeatureRandom(
                sourceBlockX(context), sourceBlockZ(context),
                world(context).underwaterMagmaWorld(), context.random(), NO_MAGMA_TRACE);
    }

    private static void placeDiskGrass(Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 29, Mc263DiskGrassFeature.DISK_GRASS);
        Mc263DiskGrassFeature.placeWithFeatureRandom(sourceBlockX(context), sourceBlockZ(context),
                world(context).diskGrassWorld(), context.random(), NO_GRASS_TRACE);
    }

    private static void placeDiskSand(Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 30, Mc263DiskSandFeature.DISK_SAND);
        Mc263DiskSandFeature.placeWithFeatureRandom(sourceBlockX(context), sourceBlockZ(context),
                world(context), context.random(), NO_SAND_TRACE);
    }

    private static void placeDiskClay(Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 31, Mc263DiskClayFeature.DISK_CLAY);
        Mc263DiskClayFeature.placeWithFeatureRandom(sourceBlockX(context), sourceBlockZ(context),
                world(context), context.random(), NO_CLAY_TRACE);
    }

    private static void placeDiskGravel(Mc263FeatureDispatcher.FeaturePlacementContext context) {
        requireContext(context, 32, Mc263DiskGravelFeature.DISK_GRAVEL);
        Mc263DiskGravelFeature.placeWithFeatureRandom(sourceBlockX(context), sourceBlockZ(context),
                world(context), context.random(), NO_GRAVEL_TRACE);
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
            throw new IllegalStateException("FEATURES step-6 context mismatch for " + expectedKey
                    + ": step=" + context.step() + ", index=" + context.globalIndex()
                    + ", key=" + context.featureKey());
        }
        if (!Mc263FeatureIndexReceipt.step(TERMINAL_STEP).featureAt(expectedIndex)
                .equals(expectedKey)) {
            throw new IllegalStateException("pinned FEATURES step-6 receipt changed for "
                    + expectedKey);
        }
    }

    static void verifyReceipt() {
        if (!FEATURE_INDEX_RECEIPT_SHA256.equals(Mc263FeatureIndexReceipt.RAW_SHA256)
                || !Mc263FeatureIndexReceipt.step(TERMINAL_STEP).featureKeys()
                .equals(EXPECTED_STEP_6)
                || STEP_6_REGISTRATIONS.size() != STEP_6_REGISTRATION_COUNT) {
            throw new IllegalStateException("pinned Minecraft 26.3 FEATURES step 6 changed");
        }
        for (int index = 0; index < STEP_6_REGISTRATIONS.size(); index++) {
            Registration registration = STEP_6_REGISTRATIONS.get(index);
            if (registration.globalIndex() != index
                    || !EXPECTED_STEP_6.get(index).equals(registration.featureKey())) {
                throw new IllegalStateException("FEATURES step-6 registration changed: "
                        + registration.featureKey());
            }
        }
    }

    /** One exact guarded dispatcher registration at feature-only underground-ores step 6. */
    public record Registration(int globalIndex, String featureKey,
                               Mc263FeatureDispatcher.FeatureExecutor executor) {
        public Registration {
            if (globalIndex < 0) {
                throw new IllegalArgumentException("negative FEATURES step-6 global index");
            }
            featureKey = Mc263FeatureBlockState.requireCanonicalResourceKey(
                    featureKey, "feature key");
            Objects.requireNonNull(executor, "executor");
        }
    }
}
