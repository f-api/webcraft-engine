package com.gameexpert.authority.versioned;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class ProducerBundle {
    private static final String SHA256 = "abdfa334ea533f85172b437bc11b9340a7735d896817ce8ec6e17632c6aee6f7";
    private static Path resolved;

    private ProducerBundle() { }

    static synchronized Path directory() {
        String override = System.getProperty("game.generation-producer-root");
        if (override != null) return Path.of(override);
        if (resolved != null) return resolved;
        Path cache = Path.of(System.getProperty("user.home"), ".webcraft", "engine");
        try {
            Properties manifest = new Properties();
            try (InputStream input = ProducerBundle.class.getResourceAsStream(
                    "/generation-producers/runtime-worker.properties")) {
                if (input == null) throw new IOException("Worker manifest resource missing");
                manifest.load(input);
            }
            String adapterSha = manifest.getProperty("jar.0.sha256", "");
            String adapterFile = manifest.getProperty("jar.0.file", "");
            if (!adapterSha.matches("[a-f0-9]{64}")
                    || !adapterFile.equals("worker-adapter." + adapterSha + ".jar")) {
                throw new IOException("Invalid worker adapter identity");
            }
            String identity = SHA256 + "-" + adapterSha;
            Path target = cache.resolve(identity);
            Files.createDirectories(cache);
            try (FileChannel channel = FileChannel.open(cache.resolve(identity + ".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    FileLock lock = channel.lock()) {
                if (!Files.exists(target)) extract(cache, target, adapterFile, adapterSha, identity);
                if (!identity.equals(Files.readString(target.resolve(".complete")))) {
                    throw new IOException("Incomplete producer bundle: " + target);
                }
                verify(target.resolve(adapterFile), adapterSha);
            }
            resolved = target;
            return target;
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot prepare verified world generator", failure);
        }
    }

    private static void extract(Path cache, Path target, String adapterFile,
            String adapterSha, String identity) throws IOException {
        Path staging = Files.createTempDirectory(cache, ".extract-");
        try {
            MessageDigest digest;
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
            Path archive = staging.resolve("runtime.zip");
            try (InputStream resource = ProducerBundle.class.getResourceAsStream(
                    "/generation-producers/runtime.zip")) {
                if (resource == null) throw new IOException("Producer bundle resource missing");
                try (DigestInputStream input = new DigestInputStream(resource, digest)) {
                    Files.copy(input, archive);
                }
            }
            if (!SHA256.equals(HexFormat.of().formatHex(digest.digest()))) {
                throw new IOException("Producer bundle checksum mismatch");
            }
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
                for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    Path file = staging.resolve(entry.getName()).normalize();
                    if (!file.startsWith(staging) || !entry.getName().endsWith(".jar")
                            || entry.isDirectory()) throw new IOException("Invalid producer entry");
                    Files.createDirectories(file.getParent());
                    Files.copy(zip, file);
                }
            }
            Files.delete(archive);
            try (InputStream adapter = ProducerBundle.class.getResourceAsStream(
                    "/generation-producers/" + adapterFile)) {
                if (adapter == null) throw new IOException("Worker adapter resource missing");
                Files.copy(adapter, staging.resolve(adapterFile));
            }
            verify(staging.resolve(adapterFile), adapterSha);
            Files.writeString(staging.resolve(".complete"), identity);
            try { Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(staging, target); }
        } finally {
            if (Files.exists(staging)) {
                try (Stream<Path> paths = Files.walk(staging)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                }
            }
        }
    }
    private static void verify(Path file, String expectedSha) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        try (DigestInputStream input = new DigestInputStream(Files.newInputStream(file), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        if (!expectedSha.equals(HexFormat.of().formatHex(digest.digest()))) {
            throw new IOException("Worker adapter checksum mismatch");
        }
    }

}
