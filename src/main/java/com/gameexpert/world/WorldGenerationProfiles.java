package com.gameexpert.world;

import java.util.List;

/** Only verified, locally bundled generation authorities may own persisted worlds. */
public final class WorldGenerationProfiles {
    public static final WorldGenerationProfile LEGACY_SNAPSHOT7 = new WorldGenerationProfile(
            "minecraft-java-26.3-snapshot-7+wv5009+dp115+rp95",
            "d3a137719011bec1c4c891693f0c82064e15672ebee7f14dc4b68ee9db532a5a",
            "7f461999fb81a5c3de449f1c170e4532ebb11f56518ddb616432e610c7fef5e9",
            5009, 115, 95, 1073742153);
    public static final WorldGenerationProfile CURRENT = new WorldGenerationProfile(
            "webcraft-26.3-post7-v1+ga0cb4e2379cd73082d2bebe0",
            "f32a9620a9f1d4fb987a256f90bd271c69409561ea79ff6eeff1e934351c7a7a",
            "0d4da081991f468d72a6f0773b17f652bb62a119d7656ae6b0320d865da06a3a",
            5009,
            115,
            95,
            1073742153);
    private static final List<WorldGenerationProfile> SUPPORTED = List.of(CURRENT);

    private WorldGenerationProfiles() { }

    /** Only this generation is supported; obsolete world data is cleared at startup. */
    public static WorldGenerationProfile newWorldProfile() { return CURRENT; }

    public static WorldGenerationProfile requireSupported(String id, String input,
            String generator, Integer world, Integer dataPack, Integer resourcePack,
            Integer protocol) {
        for (WorldGenerationProfile profile : SUPPORTED) {
            if (profile.matches(id, input, generator, world, dataPack, resourcePack, protocol)) {
                return profile;
            }
        }
        throw new IllegalStateException("WORLD_GENERATION_PROFILE_UNSUPPORTED");
    }

    /** Routing only: callers must separately verify the world's complete persisted tuple. */
    public static WorldGenerationProfile requireSupportedBaselineId(String baselineId) {
        for (WorldGenerationProfile profile : SUPPORTED) {
            if (profile.getBaselineId().equals(baselineId)) return profile;
        }
        throw new IllegalStateException("WORLD_GENERATION_PROFILE_UNSUPPORTED");
    }

    public static WorldGenerationProfile requireSupported(WorldGenerationProfile profile) {
        if (profile == null) throw new IllegalStateException("WORLD_GENERATION_PROFILE_UNSUPPORTED");
        return requireSupported(profile.getBaselineId(), profile.getInputFingerprintSha256(),
                profile.getGeneratorSourceSha256(), profile.getWorldVersion(),
                profile.getDataPackMajor(), profile.getResourcePackMajor(), profile.getProtocolVersion());
    }
}
