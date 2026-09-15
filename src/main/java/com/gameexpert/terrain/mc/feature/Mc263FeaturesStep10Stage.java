package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import java.util.Objects;

/** Dormant exact coordinator for the complete pinned FEATURES schedule through step ten. */
public final class Mc263FeaturesStep10Stage {
    public static final int TERMINAL_STEP = 10;
    public static final int GLOBAL_INDEX = 0;
    public static final String FEATURE_KEY = "minecraft:freeze_top_layer";

    private Mc263FeaturesStep10Stage() { }

    public static Mc263FeatureDispatcher createDispatcher() {
        verifyReceipt();
        Mc263FeatureDispatcher dispatcher = Mc263FeaturesStep9Stage.createDispatcher();
        dispatcher.registerFeature(FEATURE_KEY, Mc263FeaturesStep10Stage::place);
        dispatcher.registerStagePreflight(TERMINAL_STEP, (seed, region) ->
                Mc263FreezeTopLayerFeature.preflight(
                        new Mc263FeatureWorldAdapter(seed, region).freezeTopLayerWorld()));
        return dispatcher;
    }

    public static Mc263FeatureDispatcher.DispatchResult dispatch(long worldSeed,
            Mc263FeaturesRegion region) {
        return createDispatcher().dispatchThroughStep(
                TERMINAL_STEP, worldSeed, Objects.requireNonNull(region, "region"));
    }

    private static void place(Mc263FeatureDispatcher.FeaturePlacementContext context) {
        if (context.step() != TERMINAL_STEP || context.globalIndex() != GLOBAL_INDEX
                || !context.featureKey().equals(FEATURE_KEY)) {
            throw new IllegalStateException("FEATURES step-ten context mismatch");
        }
        Mc263FreezeTopLayerFeature.placeWithFeatureRandom(context.random(),
                context.sourceChunkX() * Blocks.CHUNK_X, 0,
                context.sourceChunkZ() * Blocks.CHUNK_Z,
                new Mc263FeatureWorldAdapter(context.worldSeed(), context.region())
                        .freezeTopLayerWorld());
    }

    static void verifyReceipt() {
        Mc263FeaturesStep9Stage.verifyReceipt();
        if (Mc263FeatureIndexReceipt.step(TERMINAL_STEP).featureKeys().size() != 1
                || !Mc263FeatureIndexReceipt.step(TERMINAL_STEP).featureAt(GLOBAL_INDEX)
                        .equals(FEATURE_KEY)) {
            throw new IllegalStateException("pinned Minecraft 26.3 FEATURES step ten changed");
        }
    }
}
