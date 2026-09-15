package com.gameexpert.authority.versioned;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Separate producer namespace: only bounded byte arrays cross the authority boundary. */
public final class IsolatedProducerSession implements AutoCloseable {
    public static final int MAX_MESSAGE_BYTES = 64 * 1024 * 1024;
    private final URLClassLoader loader;
    private final Method dispatch;

    /** Every jar, including the adapter and dependencies, must have a reviewed exact digest. */
    public static final class PinnedJar {
        private final Path path;
        private final String sha256;
        public PinnedJar(Path path, String sha256) {
            this.path = Objects.requireNonNull(path).toAbsolutePath().normalize();
            if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("producer jar SHA-256 is required");
            }
            this.sha256 = sha256;
        }
        public String sha256() { return sha256; }
    }

    private final Path privateDirectory;
    private boolean closed;

    public IsolatedProducerSession(List<PinnedJar> jars,
            com.gameexpert.world.WorldGenerationProfile requestedProfile,
            String producerArchiveSha256) throws IOException {
        this(jars, requestedProfile, producerArchiveSha256, new byte[0]);
    }

    public IsolatedProducerSession(List<PinnedJar> jars,
            com.gameexpert.world.WorldGenerationProfile requestedProfile,
            String producerArchiveSha256, byte[] canonicalSourceGraph) throws IOException {
        if (canonicalSourceGraph == null || canonicalSourceGraph.length > 1024 * 1024) {
            throw new IllegalArgumentException("bounded source graph required");
        }
        canonicalSourceGraph = canonicalSourceGraph.clone();
        var profile = com.gameexpert.world.WorldGenerationProfiles.requireSupported(requestedProfile);
        if (producerArchiveSha256 == null || !producerArchiveSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("reviewed producer archive SHA-256 is required");
        }
        if (jars == null || jars.isEmpty() || jars.size() > 128) {
            throw new IllegalArgumentException("bounded complete producer classpath required");
        }
        privateDirectory = Files.createTempDirectory("webcraft-producer-");
        URLClassLoader created = null;
        try {
            List<URL> urls = new ArrayList<>();
            int index = 0;
            for (PinnedJar jar : List.copyOf(jars)) {
                // Load the verified private copy, never the mutable source path checked earlier.
                Path copy = privateDirectory.resolve(Integer.toString(index++) + ".jar");
                Files.copy(jar.path, copy);
                String actual;
                try (var input = Files.newInputStream(copy)) {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] buffer = new byte[65536];
                    int count;
                    while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
                    actual = HexFormat.of().formatHex(digest.digest());
                }
                if (!jar.sha256.equals(actual)) {
                    throw new IllegalStateException("producer artifact digest mismatch");
                }
                urls.add(copy.toUri().toURL());
            }
            // Platform parent excludes every application class and dependency. Producer classes,
            // their getResource byte receipts and all dependency classes resolve in this bundle.
            created = new URLClassLoader(urls.toArray(URL[]::new),
                    ClassLoader.getPlatformClassLoader());
            Class<?> worker = Class.forName(
                    "com.gameexpert.authority.versioned.worker.LegacyProducerWorker", true, created);
            var configuration = new java.io.ByteArrayOutputStream();
            try (var output = new java.io.DataOutputStream(configuration)) {
                output.writeInt(0x57504331); output.writeByte(1);
                output.writeUTF(profile.getBaselineId());
                output.writeUTF(profile.getInputFingerprintSha256());
                output.writeUTF(profile.getGeneratorSourceSha256());
                output.writeInt(profile.getWorldVersion()); output.writeInt(profile.getDataPackMajor());
                output.writeInt(profile.getResourcePackMajor()); output.writeInt(profile.getProtocolVersion());
                output.writeUTF(producerArchiveSha256);
                output.writeInt(canonicalSourceGraph.length);
                output.write(canonicalSourceGraph);
            }
            worker.getMethod("configure", byte[].class).invoke(null, (Object) configuration.toByteArray());
            dispatch = worker.getMethod("dispatch", byte[].class);
            if (dispatch.getReturnType() != byte[].class) {
                throw new IllegalStateException("producer worker has a non-neutral response");
            }
            loader = created;
        } catch (ReflectiveOperationException | NoSuchAlgorithmException | RuntimeException
                | IOException failure) {
            if (created != null) created.close();
            removePrivateCopies();
            throw new IOException("isolated producer could not be opened", failure);
        }
    }

    public synchronized byte[] exchange(byte[] request) {
        if (closed) throw new IllegalStateException("producer session is closed");
        if (request == null || request.length == 0 || request.length > MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("producer request exceeds bounds");
        }
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(loader);
            byte[] response = (byte[]) dispatch.invoke(null, (Object) request.clone());
            if (response == null || response.length == 0 || response.length > MAX_MESSAGE_BYTES) {
                throw new IllegalStateException("producer response exceeds bounds");
            }
            return response.clone();
        } catch (InvocationTargetException failure) {
            // Do not leak private producer exceptions/objects across the boundary.
            Throwable cause = failure.getCause();
            throw new IllegalStateException("isolated producer rejected request: "
                    + cause.getClass().getName() + ": " + cause.getMessage());
        } catch (IllegalAccessException impossible) {
            throw new IllegalStateException("producer dispatch is inaccessible", impossible);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        try {
            dispatch.getDeclaringClass().getMethod("close").invoke(null);
        } catch (ReflectiveOperationException failure) {
            throw new IOException("producer resource cleanup failed", failure);
        } finally {
            try { loader.close(); } finally { removePrivateCopies(); }
        }
    }

    private void removePrivateCopies() throws IOException {
        try (var entries = Files.list(privateDirectory)) {
            for (Path path : entries.toList()) Files.deleteIfExists(path);
        }
        Files.deleteIfExists(privateDirectory);
    }
}
