package com.gameexpert.terrain.mc.ore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;

/** Fail-closed source receipt for the exact official files projected by the ore catalog. */
public final class Mc263OreSourceManifest {
    public static final String VERSION = "26.3-snapshot-7";
    public static final String SERVER_SHA1 = "06157fedd67ff4dd6e0e6fa4a9dd0af296f0dd61";
    public static final String INNER_SERVER_SHA1 = "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final int WORLD_VERSION = 5009;
    public static final int DATA_PACK_VERSION = 115;
    public static final int RESOURCE_PACK_VERSION = 95;
    public static final int SOURCE_FILE_COUNT = 150;

    /**
     * SHA-256 over each sorted UTF-8 relative path, NUL, exact file bytes, NUL. The selection binds
     * the Overworld biome tag and biome lists, ore/fossil definitions, placement, replacement tags,
     * vein density/noise settings, processors, and fossil NBT inputs.
     */
    public static final String SOURCE_AGGREGATE_SHA256 =
            "8dafd25679f880d8beae640595e7d59de6b7c04860ff3f0445641fbd2f6637af";

    private static final List<String> SOURCE_PATHS = buildSourcePaths();

    private Mc263OreSourceManifest() {
    }

    public static List<String> sourcePaths() {
        return SOURCE_PATHS;
    }

    public static void verifyOfficialServerJar(Path jar) throws IOException {
        verifyFileHash(jar, "SHA-1", SERVER_SHA1);
    }

    public static void verifyOfficialInnerServerJar(Path jar) throws IOException {
        verifyFileHash(jar, "SHA-1", INNER_SERVER_SHA1);
    }

    public static void verifyExtractedData(Path extractedJarRoot) throws IOException {
        MessageDigest digest = digest("SHA-256");
        for (String relative : SOURCE_PATHS) {
            Path file = extractedJarRoot.resolve(relative);
            if (!Files.isRegularFile(file)) {
                throw new IOException("missing pinned ore source: " + relative);
            }
            digest.update(relative.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0; ) {
                    digest.update(buffer, 0, read);
                }
            }
            digest.update((byte) 0);
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!SOURCE_AGGREGATE_SHA256.equals(actual)) {
            throw new IOException("official 26.3 ore source aggregate mismatch: " + actual);
        }
    }

    private static void verifyFileHash(Path file, String algorithm, String expected) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("missing pinned server jar: " + file);
        }
        MessageDigest digest = digest(algorithm);
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0; ) {
                digest.update(buffer, 0, read);
            }
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!expected.equals(actual)) {
            throw new IOException("official server hash mismatch: " + actual);
        }
    }

    private static List<String> buildSourcePaths() {
        TreeSet<String> paths = new TreeSet<>();
        paths.add("data/minecraft/tags/worldgen/biome/is_overworld.json");
        for (String biome : Mc263OreCatalog.overworldBiomes()) {
            paths.add("data/minecraft/worldgen/biome/" + biome + ".json");
        }
        for (Mc263OreCatalog.PlacedFeature placed : Mc263OreCatalog.placedFeatures()) {
            paths.add("data/minecraft/worldgen/placed_feature/" + placed.key() + ".json");
        }
        for (Mc263OreCatalog.FossilFeature fossil : Mc263OreCatalog.fossils()) {
            paths.add("data/minecraft/worldgen/placed_feature/" + fossil.placedKey() + ".json");
            paths.add("data/minecraft/worldgen/feature/" + fossil.configuredKey() + ".json");
        }
        for (Mc263OreCatalog.ConfiguredOre configured : Mc263OreCatalog.configuredOres()) {
            paths.add("data/minecraft/worldgen/feature/" + configured.key() + ".json");
        }
        paths.add("data/minecraft/worldgen/noise_settings/overworld.json");
        for (String noise : List.of("ore_gap", "ore_vein_a", "ore_vein_b", "ore_veininess")) {
            paths.add("data/minecraft/worldgen/noise/" + noise + ".json");
        }
        for (String density : List.of(
                "copper_density", "gap", "iron_density", "mask", "richness", "toggle")) {
            paths.add("data/minecraft/worldgen/density_function/overworld/ore_vein/"
                    + density + ".json");
        }
        for (String processor : List.of("fossil_coal", "fossil_diamonds", "fossil_rot")) {
            paths.add("data/minecraft/worldgen/processor_list/" + processor + ".json");
        }
        for (String tag : List.of(
                "base_stone_overworld", "deepslate_ore_replaceables", "features_cannot_replace",
                "height_specific_ore_replaceables", "stone_ore_replaceables")) {
            paths.add("data/minecraft/tags/block/" + tag + ".json");
        }
        for (String structure : fossilStructureNames()) {
            paths.add("data/minecraft/structure/fossil/" + structure + ".nbt");
        }
        List<String> result = List.copyOf(paths);
        if (result.size() != SOURCE_FILE_COUNT) {
            throw new ExceptionInInitializerError("ore source receipt expected " + SOURCE_FILE_COUNT
                    + " files, got " + result.size());
        }
        return result;
    }

    private static List<String> fossilStructureNames() {
        List<String> names = new ArrayList<>(16);
        for (String kind : List.of("spine", "skull")) {
            for (int i = 1; i <= 4; i++) {
                names.add(kind + "_" + i);
                names.add(kind + "_" + i + "_coal");
            }
        }
        return names;
    }

    private static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(algorithm + " unavailable", exception);
        }
    }
}
