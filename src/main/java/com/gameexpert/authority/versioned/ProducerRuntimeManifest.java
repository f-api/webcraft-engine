package com.gameexpert.authority.versioned;

import com.gameexpert.world.WorldGenerationProfile;
import com.gameexpert.world.WorldGenerationProfiles;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/** App-packaged allowlist; external jar bytes are independently verified by the isolated session. */
final class ProducerRuntimeManifest {
    private ProducerRuntimeManifest() { }
    static List<IsolatedProducerSession.PinnedJar> read(InputStream input, Path root,
            WorldGenerationProfile requested) throws IOException {
        if (input == null) throw new IOException("producer runtime manifest is missing");
        Properties values = new Properties();
        values.load(input);
        try {
            if (!"1".equals(values.getProperty("schema"))) throw new IllegalArgumentException("manifest schema");
            WorldGenerationProfile profile = WorldGenerationProfiles.requireSupported(
                    values.getProperty("baselineId"), values.getProperty("inputFingerprintSha256"),
                    values.getProperty("generatorSourceSha256"), integer(values,"worldVersion"),
                    integer(values,"dataPackMajor"), integer(values,"resourcePackMajor"), integer(values,"protocolVersion"));
            if (WorldGenerationProfiles.requireSupported(requested) != profile) throw new IllegalArgumentException("manifest profile mismatch");
            int count = integer(values,"jar.count");
            if (count < 2 || count > 128) throw new IllegalArgumentException("manifest jar count");
            Set<String> keys = new HashSet<>(Set.of("schema","baselineId","inputFingerprintSha256",
                    "generatorSourceSha256","worldVersion","dataPackMajor","resourcePackMajor","protocolVersion","jar.count"));
            Set<Path> paths = new HashSet<>();
            List<IsolatedProducerSession.PinnedJar> jars = new ArrayList<>();
            Path base = root.toAbsolutePath().normalize();
            for (int index = 0; index < count; index++) {
                String fileKey="jar."+index+".file", shaKey="jar."+index+".sha256";
                keys.add(fileKey); keys.add(shaKey);
                String relative=values.getProperty(fileKey);
                if(relative==null || relative.isBlank() || Path.of(relative).isAbsolute()) throw new IllegalArgumentException("manifest jar path");
                Path path=base.resolve(relative).normalize();
                if(!path.startsWith(base) || !path.toString().endsWith(".jar") || !paths.add(path)) throw new IllegalArgumentException("manifest jar escapes or duplicates");
                jars.add(new IsolatedProducerSession.PinnedJar(path,values.getProperty(shaKey)));
            }
            if(!values.stringPropertyNames().equals(keys)) throw new IllegalArgumentException("manifest fields");
            return List.copyOf(jars);
        } catch(RuntimeException invalid) { throw new IOException("invalid producer runtime manifest",invalid); }
    }
    private static int integer(Properties values,String name) { return Integer.parseInt(values.getProperty(name)); }
}
