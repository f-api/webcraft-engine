package com.gameexpert.authority.versioned;

import com.gameexpert.world.WorldGenerationProfiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ProducerBundleSmoke {
    public static void main(String[] args) throws Exception {
        Path directory = ProducerBundle.directory();
        if (!directory.equals(ProducerBundle.directory())) throw new AssertionError("Cache reuse failed");
        Properties manifest = new Properties();
        try (var input = ProducerBundleSmoke.class.getResourceAsStream(
                "/generation-producers/current-worker.properties")) { manifest.load(input); }
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
