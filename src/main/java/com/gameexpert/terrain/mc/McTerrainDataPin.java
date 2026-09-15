package com.gameexpert.terrain.mc;

import java.util.Arrays;

/**
 * Compile-time identity of the official terrain inputs used for the 26.3 migration.
 *
 * <p>Each category digest is SHA-256 over a UTF-8 manifest sorted by archive path. Every
 * manifest line is {@code hex-sha256 + two spaces + archive-path + '\n'}, where the digest is
 * over the unmodified JSON entry bytes from the official inner server JAR. The aggregate input
 * fingerprint is SHA-256 over the newline-terminated {@link #fingerprintSource()} string. This
 * makes the version boundary portable without requiring the external server JAR at runtime.</p>
 */
public final class McTerrainDataPin {
    public static final String VERSION_ID = "26.3-snapshot-7";
    public static final int WORLD_VERSION = 5009;
    public static final int DATA_PACK_MAJOR = 115;
    public static final int RESOURCE_PACK_MAJOR = 95;
    public static final int PROTOCOL_VERSION = 1073742153;

    public static final String OUTER_SERVER_SHA1 =
            "06157fedd67ff4dd6e0e6fa4a9dd0af296f0dd61";
    public static final String INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";

    public static final int REGISTERED_BIOME_COUNT = 67;
    public static final int OVERWORLD_BIOME_COUNT = 56;
    private static final String[] REGISTERED_BIOMES = {
        "badlands", "bamboo_jungle", "basalt_deltas", "beach", "birch_forest",
        "cherry_grove", "cold_ocean", "crimson_forest", "dappled_forest", "dark_forest",
        "deep_cold_ocean", "deep_dark", "deep_frozen_ocean", "deep_lukewarm_ocean",
        "deep_ocean", "desert", "dripstone_caves", "end_barrens", "end_highlands",
        "end_midlands", "eroded_badlands", "flower_forest", "forest", "frozen_ocean",
        "frozen_peaks", "frozen_river", "grove", "ice_spikes", "jagged_peaks", "jungle",
        "lukewarm_ocean", "lush_caves", "mangrove_swamp", "meadow", "mushroom_fields",
        "nether_wastes", "ocean", "old_growth_birch_forest", "old_growth_pine_taiga",
        "old_growth_spruce_taiga", "pale_garden", "plains", "river", "savanna",
        "savanna_plateau", "small_end_islands", "snowy_beach", "snowy_plains",
        "snowy_slopes", "snowy_taiga", "soul_sand_valley", "sparse_jungle",
        "stony_peaks", "stony_shore", "sulfur_caves", "sunflower_plains", "swamp",
        "taiga", "the_end", "the_void", "warm_ocean", "warped_forest", "windswept_forest",
        "windswept_gravelly_hills", "windswept_hills", "windswept_savanna",
        "wooded_badlands"
    };
    public static final int CLIMATE_POINT_COUNT = 7594;
    public static final int CLIMATE_TREE_NODE_COUNT = 9113;
    public static final String CLIMATE_TREE_SHA256 =
            "283b1cbdfa8aee924fef8701e843f0974dddd65bd845aed83d49736363f97cd3";

    public static final int DENSITY_FUNCTION_COUNT = 52;
    public static final String DENSITY_FUNCTION_MANIFEST_SHA256 =
            "197fdc652061fa5ba301240f24989dc616a73edc4d22b5137cdaaa96e9331dcf";
    public static final int NOISE_PARAMETER_COUNT = 64;
    public static final String NOISE_PARAMETER_MANIFEST_SHA256 =
            "1ae6ec85e2300f78b28ecfbfd3eae789b57701fd0f63412755d881f229a4a746";
    public static final int NOISE_SETTINGS_COUNT = 1;
    public static final String NOISE_SETTINGS_MANIFEST_SHA256 =
            "aade0719e491d28bea8e3e5d0ee91ed85d993fa8c22fed568bf3e2af788c5abd";
    public static final int MATERIAL_RULE_COUNT = 40;
    public static final String MATERIAL_RULE_MANIFEST_SHA256 =
            "aa647f507777742fa587dec20b5b3754113fa618b0be13916e2d99ad0b1fcab9";
    public static final int BIOME_DEFINITION_COUNT = 67;
    public static final String BIOME_MANIFEST_SHA256 =
            "5576095ff90bf02b169fa2a5e21a634010c89d53564d8e81cf2628716742d457";
    public static final String DENSITY_RUNTIME_SHA256 =
            "0539bbe0b996aa8960b70dcbf78a280d518351eeafdb4eeb1920f22a11c19893";
    public static final String BIOME_RUNTIME_SHA256 =
            "c704a52985582d644df74d5977d609de1700cb60a8c86a7f6c9a1640f05faf14";
    public static final String MATERIAL_RULE_RUNTIME_SHA256 =
            "9479f1f53ea42f9d64c8e65ac24f0130fdf7e5c1a2b41b26118b925d5351dc53";

    public static final String INPUT_FINGERPRINT_SHA256 =
            "d3a137719011bec1c4c891693f0c82064e15672ebee7f14dc4b68ee9db532a5a";

    private McTerrainDataPin() {
    }

    /** Sorted official registry keys without the implicit {@code minecraft:} namespace. */
    public static String[] registeredBiomes() {
        return Arrays.copyOf(REGISTERED_BIOMES, REGISTERED_BIOMES.length);
    }

    /** Exact newline-terminated preimage of {@link #INPUT_FINGERPRINT_SHA256}. */
    public static String fingerprintSource() {
        return "version=" + VERSION_ID + "\n"
                + "world_version=" + WORLD_VERSION + "\n"
                + "data_pack=" + DATA_PACK_MAJOR + "\n"
                + "resource_pack=" + RESOURCE_PACK_MAJOR + "\n"
                + "protocol=" + PROTOCOL_VERSION + "\n"
                + "registry_biomes=" + REGISTERED_BIOME_COUNT + "\n"
                + "overworld_biomes=" + OVERWORLD_BIOME_COUNT + "\n"
                + "climate_points=" + CLIMATE_POINT_COUNT + "\n"
                + "climate_tree_nodes=" + CLIMATE_TREE_NODE_COUNT + "\n"
                + "climate_tree_sha256=" + CLIMATE_TREE_SHA256 + "\n"
                + "density_manifest_sha256=" + DENSITY_FUNCTION_MANIFEST_SHA256 + "\n"
                + "noise_manifest_sha256=" + NOISE_PARAMETER_MANIFEST_SHA256 + "\n"
                + "noise_settings_manifest_sha256=" + NOISE_SETTINGS_MANIFEST_SHA256 + "\n"
                + "material_rule_manifest_sha256=" + MATERIAL_RULE_MANIFEST_SHA256 + "\n"
                + "biome_manifest_sha256=" + BIOME_MANIFEST_SHA256 + "\n"
                + "density_runtime_sha256=" + DENSITY_RUNTIME_SHA256 + "\n"
                + "material_rule_runtime_sha256=" + MATERIAL_RULE_RUNTIME_SHA256 + "\n"
                + "biome_runtime_sha256=" + BIOME_RUNTIME_SHA256 + "\n";
    }
}
