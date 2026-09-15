package com.gameexpert.terrain.mc.feature;

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
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Fail-closed receipt for the official files used by the 26.3 feature-index projection. */
public final class Mc263FeatureSourceManifest {
    public static final String VERSION = "26.3-snapshot-7";
    public static final String SERVER_SHA1 = "06157fedd67ff4dd6e0e6fa4a9dd0af296f0dd61";
    public static final String INNER_SERVER_SHA1 = "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String FEATURE_SORTER_CLASS_SHA256 =
            "b1bcaf807348b6d558cc09d310cf19b6bd9e34afdea9529524e5ce9510925419";
    public static final String FEATURE_DATA_CLASS_SHA256 =
            "0ec8f2eabd7bf8bd229254ee965809bff0d36e38e2e25bb4dcd3eeb4b5efd6ec";
    public static final String STEP_FEATURE_DATA_CLASS_SHA256 =
            "c9af358ae20b365bf98f7186de71e2f56ca449af8cab2ffd9a5fcc1852b003da";
    public static final String GRAPH_CLASS_SHA256 =
            "e48bbe1f9d34811c4240a6473c2fd838dc25bed7e3d6ea5cac60908ba1e5df8c";
    public static final String SOURCE_AGGREGATE_SHA256 =
            "184622a75d543567e1004e8f6f4b40fc1828f0e5c695e2f827c777ebddd5da4c";
    public static final int SOURCE_FILE_COUNT = 57;

    private static final Map<String, String> BYTECODE_HASHES = Map.of(
            "net/minecraft/world/level/biome/FeatureSorter.class", FEATURE_SORTER_CLASS_SHA256,
            "net/minecraft/world/level/biome/FeatureSorter$1FeatureData.class",
                    FEATURE_DATA_CLASS_SHA256,
            "net/minecraft/world/level/biome/FeatureSorter$StepFeatureData.class",
                    STEP_FEATURE_DATA_CLASS_SHA256,
            "net/minecraft/util/Graph.class", GRAPH_CLASS_SHA256);

    private Mc263FeatureSourceManifest() {
    }

    public static List<String> sourcePaths() {
        List<String> paths = new ArrayList<>(SOURCE_FILE_COUNT);
        paths.add("data/minecraft/tags/worldgen/biome/is_overworld.json");
        for (Mc263FeatureIndexReceipt.BiomeFeatureData biome
                : Mc263FeatureIndexReceipt.biomes()) {
            paths.add("data/minecraft/worldgen/biome/"
                    + biome.biomeKey().substring("minecraft:".length()) + ".json");
        }
        paths.sort(String::compareTo);
        if (paths.size() != SOURCE_FILE_COUNT) {
            throw new IllegalStateException("feature source count mismatch: " + paths.size());
        }
        return List.copyOf(paths);
    }

    public static void verifyOfficialServerJar(Path jar) throws IOException {
        verifyFileHash(jar, "SHA-1", SERVER_SHA1);
    }

    public static void verifyOfficialInnerServerJar(Path jar) throws IOException {
        verifyFileHash(jar, "SHA-1", INNER_SERVER_SHA1);
        verifyFeatureSorterBytecode(jar);
    }

    /** Verifies the extracted Overworld tag and all 56 exact biome definitions. */
    public static void verifyExtractedData(Path extractedJarRoot) throws IOException {
        MessageDigest aggregate = digest("SHA-256");
        for (String relative : sourcePaths()) {
            Path file = extractedJarRoot.resolve(relative);
            if (!Files.isRegularFile(file)) {
                throw new IOException("missing pinned feature source: " + relative);
            }
            aggregate.update(relative.getBytes(StandardCharsets.UTF_8));
            aggregate.update((byte) 0);
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0; ) {
                    aggregate.update(buffer, 0, read);
                }
            }
            aggregate.update((byte) 0);
        }
        String actual = HexFormat.of().formatHex(aggregate.digest());
        if (!SOURCE_AGGREGATE_SHA256.equals(actual)) {
            throw new IOException("official 26.3 feature source aggregate mismatch: " + actual);
        }
    }

    /** Verifies every class whose bytecode defines the reproduced sort and DFS semantics. */
    public static void verifyFeatureSorterBytecode(Path innerServerJar) throws IOException {
        try (ZipFile zip = new ZipFile(innerServerJar.toFile())) {
            for (Map.Entry<String, String> expected : BYTECODE_HASHES.entrySet()) {
                ZipEntry entry = zip.getEntry(expected.getKey());
                if (entry == null) throw new IOException("missing pinned bytecode: " + expected.getKey());
                MessageDigest hash = digest("SHA-256");
                try (InputStream input = zip.getInputStream(entry)) {
                    byte[] buffer = new byte[8192];
                    for (int read; (read = input.read(buffer)) >= 0; ) hash.update(buffer, 0, read);
                }
                String actual = HexFormat.of().formatHex(hash.digest());
                if (!expected.getValue().equals(actual)) {
                    throw new IOException("official bytecode hash mismatch for "
                            + expected.getKey() + ": " + actual);
                }
            }
        }
    }

    private static void verifyFileHash(Path file, String algorithm, String expected)
            throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("missing pinned source: " + file);
        MessageDigest hash = digest(algorithm);
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0; ) hash.update(buffer, 0, read);
        }
        String actual = HexFormat.of().formatHex(hash.digest());
        if (!expected.equals(actual)) {
            throw new IOException("official source hash mismatch for " + file + ": " + actual);
        }
    }

    private static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(algorithm + " unavailable", exception);
        }
    }
}
