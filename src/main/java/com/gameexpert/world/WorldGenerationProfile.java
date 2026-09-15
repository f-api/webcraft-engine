package com.gameexpert.world;

import java.util.Objects;
import lombok.Getter;

/** Immutable complete generation identity; never inferred from a display version alone. */
@Getter
public final class WorldGenerationProfile {
    private final String baselineId;
    private final String inputFingerprintSha256;
    private final String generatorSourceSha256;
    private final int worldVersion;
    private final int dataPackMajor;
    private final int resourcePackMajor;
    private final int protocolVersion;

    public WorldGenerationProfile(String baselineId, String inputFingerprintSha256,
            String generatorSourceSha256, int worldVersion, int dataPackMajor,
            int resourcePackMajor, int protocolVersion) {
        this.baselineId = Objects.requireNonNull(baselineId);
        this.inputFingerprintSha256 = Objects.requireNonNull(inputFingerprintSha256);
        this.generatorSourceSha256 = Objects.requireNonNull(generatorSourceSha256);
        this.worldVersion = worldVersion;
        this.dataPackMajor = dataPackMajor;
        this.resourcePackMajor = resourcePackMajor;
        this.protocolVersion = protocolVersion;
    }

    public boolean matches(String id, String input, String generator, Integer world,
            Integer dataPack, Integer resourcePack, Integer protocol) {
        return baselineId.equals(id) && inputFingerprintSha256.equals(input)
                && generatorSourceSha256.equals(generator)
                && Integer.valueOf(worldVersion).equals(world)
                && Integer.valueOf(dataPackMajor).equals(dataPack)
                && Integer.valueOf(resourcePackMajor).equals(resourcePack)
                && Integer.valueOf(protocolVersion).equals(protocol);
    }
}
