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

/** Fail-closed source and bytecode manifest for the pinned 26.3 structure-index receipt. */
public final class Mc263StructureSourceManifest {
    private static final Map<String, String> BYTECODE_HASHES = Map.of(
            "net/minecraft/world/level/chunk/ChunkGenerator.class",
                    Mc263StructureIndexReceipt.CHUNK_GENERATOR_CLASS_SHA256,
            "net/minecraft/resources/ResourceManagerRegistryLoadTask.class",
                    Mc263StructureIndexReceipt
                            .RESOURCE_MANAGER_REGISTRY_LOAD_TASK_CLASS_SHA256,
            "net/minecraft/core/MappedRegistry.class",
                    Mc263StructureIndexReceipt.MAPPED_REGISTRY_CLASS_SHA256);

    private Mc263StructureSourceManifest() { }

    public static List<String> sourcePaths() {
        List<String> paths = new ArrayList<>(Mc263StructureIndexReceipt.SOURCE_FILE_COUNT);
        paths.add("data/minecraft/tags/worldgen/biome/is_overworld.json");
        for (Mc263StructureIndexReceipt.Entry entry : Mc263StructureIndexReceipt.entries()) {
            String path = entry.key().substring("minecraft:".length());
            paths.add("data/minecraft/worldgen/structure/" + path + ".json");
            paths.add("data/minecraft/tags/worldgen/biome/has_structure/"
                    + tagPath(path) + ".json");
        }
        paths.sort(String::compareTo);
        if (paths.size() != Mc263StructureIndexReceipt.SOURCE_FILE_COUNT) {
            throw new IllegalStateException("structure source count mismatch: " + paths.size());
        }
        return List.copyOf(paths);
    }

    private static String tagPath(String structurePath) {
        return switch (structurePath) {
            case "fortress" -> "nether_fortress";
            case "jungle_pyramid" -> "jungle_temple";
            case "mansion" -> "woodland_mansion";
            case "monument" -> "ocean_monument";
            case "ruined_portal" -> "ruined_portal_standard";
            default -> structurePath;
        };
    }

    public static void verifyExtractedData(Path extractedJarRoot) throws IOException {
        MessageDigest aggregate = digest("SHA-256");
        for (String relative : sourcePaths()) {
            Path file = extractedJarRoot.resolve(relative);
            if (!Files.isRegularFile(file)) {
                throw new IOException("missing pinned structure source: " + relative);
            }
            aggregate.update(relative.getBytes(StandardCharsets.UTF_8));
            aggregate.update((byte) 0);
            try (InputStream input = Files.newInputStream(file)) {
                input.transferTo(new DigestOutputStream(aggregate));
            }
            aggregate.update((byte) 0);
        }
        String actual = HexFormat.of().formatHex(aggregate.digest());
        if (!Mc263StructureIndexReceipt.SOURCE_AGGREGATE_SHA256.equals(actual)) {
            throw new IOException("official 26.3 structure source aggregate mismatch: " + actual);
        }
    }

    public static void verifyBytecode(Path innerServerJar) throws IOException {
        try (ZipFile zip = new ZipFile(innerServerJar.toFile())) {
            for (Map.Entry<String, String> expected : BYTECODE_HASHES.entrySet()) {
                ZipEntry entry = zip.getEntry(expected.getKey());
                if (entry == null) throw new IOException("missing pinned bytecode: " + expected.getKey());
                MessageDigest hash = digest("SHA-256");
                try (InputStream input = zip.getInputStream(entry)) {
                    input.transferTo(new DigestOutputStream(hash));
                }
                String actual = HexFormat.of().formatHex(hash.digest());
                if (!expected.getValue().equals(actual)) {
                    throw new IOException("official bytecode hash mismatch for "
                            + expected.getKey() + ": " + actual);
                }
            }
        }
    }

    private static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(algorithm + " unavailable", exception);
        }
    }

    /** Tiny sink that keeps source hashing streaming without retaining the pinned files. */
    private static final class DigestOutputStream extends java.io.OutputStream {
        private final MessageDigest digest;

        private DigestOutputStream(MessageDigest digest) { this.digest = digest; }

        @Override
        public void write(int value) { digest.update((byte) value); }

        @Override
        public void write(byte[] values, int offset, int length) {
            digest.update(values, offset, length);
        }
    }
}
