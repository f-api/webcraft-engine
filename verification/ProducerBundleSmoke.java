package com.gameexpert.authority.versioned;

import com.gameexpert.world.WorldGenerationProfiles;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ProducerBundleSmoke {
    public static void main(String[] args) throws Exception {
        Path directory = ProducerBundle.directory();
        if (!directory.equals(ProducerBundle.directory())) throw new AssertionError("Cache reuse failed");
        Properties manifest = new Properties();
        try (InputStream input = ProducerBundleSmoke.class.getResourceAsStream(
                "/generation-producers/runtime-worker.properties")) { manifest.load(input); }
        Properties originalManifest = new Properties();
        try (InputStream input = ProducerBundleSmoke.class.getResourceAsStream(
                "/generation-producers/current-worker.properties")) { originalManifest.load(input); }
        for (String key : originalManifest.stringPropertyNames()) {
            if (!key.startsWith("jar.0.") && !originalManifest.getProperty(key).equals(manifest.getProperty(key))) {
                throw new AssertionError("Immutable producer identity changed: " + key);
            }
        }
        String cacheKey = "abdfa334ea533f85172b437bc11b9340a7735d896817ce8ec6e17632c6aee6f7-"
                + manifest.getProperty("jar.0.sha256");
        if (!directory.getFileName().toString().equals(cacheKey)) {
            throw new AssertionError("Worker adapter does not have an isolated cache");
        }
        int count = Integer.parseInt(manifest.getProperty("jar.count"));
        for (int i = 0; i < count; i++) {
            if (!Files.isRegularFile(directory.resolve(manifest.getProperty("jar." + i + ".file")))) {
                throw new AssertionError("Missing producer JAR " + i);
            }
        }
        try {
            if (ProducerAuthorities.defaultState(WorldGenerationProfiles.CURRENT, 1) == null) {
                throw new AssertionError("Producer returned no state");
            }
        } finally { ProducerAuthorities.closeAll(); }
        Path adapter = directory.resolve(manifest.getProperty("jar.0.file"));
        byte[] original = Files.readAllBytes(adapter);
        try {
            Files.write(adapter, new byte[] { 1, 2, 3 });
            try {
                ProducerAuthorities.defaultState(WorldGenerationProfiles.CURRENT, 1);
                throw new AssertionError("Corrupt producer JAR was accepted");
            } catch (IllegalStateException expected) {
                System.out.println("Corrupt producer JAR rejected");
            }
        } finally {
            ProducerAuthorities.closeAll();
            Files.write(adapter, original);
        }
        System.out.println("Producer bundle extraction, cache reuse and integrity checks passed");
    }
}
