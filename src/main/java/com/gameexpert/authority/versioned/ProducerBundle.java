package com.gameexpert.authority.versioned;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
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
        Path target = cache.resolve(SHA256);
        try {
            Files.createDirectories(cache);
            try (FileChannel channel = FileChannel.open(cache.resolve(SHA256 + ".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    var lock = channel.lock()) {
                if (!Files.exists(target)) extract(cache, target);
                if (!SHA256.equals(Files.readString(target.resolve(".complete")))) {
                    throw new IOException("Incomplete producer bundle: " + target);
                }
            }
            resolved = target;
            return target;
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot prepare verified world generator", failure);
        }
    }

    private static void extract(Path cache, Path target) throws IOException {
        Path staging = Files.createTempDirectory(cache, ".extract-");
        try {
            MessageDigest digest;
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
            Path archive = staging.resolve("runtime.zip");
            try (InputStream resource = ProducerBundle.class.getResourceAsStream(
                    "/generation-producers/runtime.zip")) {
                if (resource == null) throw new IOException("Producer bundle resource missing");
                try (var input = new DigestInputStream(resource, digest)) {
                    Files.copy(input, archive);
                }
            }
            if (!SHA256.equals(HexFormat.of().formatHex(digest.digest()))) {
                throw new IOException("Producer bundle checksum mismatch");
            }
            try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    Path file = staging.resolve(entry.getName()).normalize();
                    if (!file.startsWith(staging) || !entry.getName().endsWith(".jar")
                            || entry.isDirectory()) throw new IOException("Invalid producer entry");
                    Files.createDirectories(file.getParent());
                    Files.copy(zip, file);
                }
            }
            Files.delete(archive);
            Files.writeString(staging.resolve(".complete"), SHA256);
            try { Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(staging, target); }
        } finally {
            if (Files.exists(staging)) {
                try (var paths = Files.walk(staging)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                }
            }
        }
    }
}
