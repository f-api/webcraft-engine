package com.gameexpert.terrain.mc.feature;

import java.util.List;
import java.util.Objects;

/**
 * Dormant exact coordinator for the Minecraft 26.3 FEATURES prefix through empty step 5.
 *
 * <p>The pinned {@code STRONGHOLDS} step contains neither a feature reference in any
 * Overworld biome nor a structure registration in this feature-only carrier. Consequently this
 * stage delegates the complete step 0..4 registrations unchanged and only advances the dispatch
 * boundary. It creates no executor, decoration seed, or feature seed and has no canonical caller.
 * </p>
 */
public final class Mc263FeaturesStep5Stage {
    public static final int TERMINAL_STEP = 5;
    public static final int STEP_5_REGISTRATION_COUNT = 0;
    public static final String FEATURE_INDEX_RECEIPT_SHA256 =
            Mc263FeaturesStep4Stage.FEATURE_INDEX_RECEIPT_SHA256;

    private static final List<String> EXPECTED_STEP_5 = List.of();

    private Mc263FeaturesStep5Stage() {
    }

    /** Creates a fresh fail-closed dispatcher containing the complete feature prefix through 5. */
    public static Mc263FeatureDispatcher createDispatcher() {
        verifyReceipt(FEATURE_INDEX_RECEIPT_SHA256);
        return Mc263FeaturesStep4Stage.createDispatcher()
                .registerStagePreflight(TERMINAL_STEP, (seed, region) -> { });
    }

    /** Executes the exact FEATURES feature prefix 0..5 against one live region. */
    public static Mc263FeatureDispatcher.DispatchResult dispatchThroughStep5(long worldSeed,
            Mc263FeaturesRegion region) {
        return createDispatcher().dispatchThroughStep(
                TERMINAL_STEP, worldSeed, Objects.requireNonNull(region, "region"));
    }

    static void verifyReceipt(String receiptSha256) {
        if (!FEATURE_INDEX_RECEIPT_SHA256.equals(receiptSha256)
                || !FEATURE_INDEX_RECEIPT_SHA256.equals(Mc263FeatureIndexReceipt.RAW_SHA256)
                || !Mc263FeatureIndexReceipt.step(TERMINAL_STEP).featureKeys()
                .equals(EXPECTED_STEP_5)) {
            throw new IllegalStateException("pinned Minecraft 26.3 FEATURES step 5 changed");
        }
        for (Mc263FeatureIndexReceipt.BiomeFeatureData biome
                : Mc263FeatureIndexReceipt.biomes()) {
            if (!biome.featuresAtStep(TERMINAL_STEP).isEmpty()) {
                throw new IllegalStateException("pinned Minecraft 26.3 biome step 5 changed: "
                        + biome.biomeKey());
            }
        }
    }
}
