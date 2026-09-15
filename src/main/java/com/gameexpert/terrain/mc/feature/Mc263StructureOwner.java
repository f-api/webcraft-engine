package com.gameexpert.terrain.mc.feature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** Current-only deterministic owner identity for one canonical STR structure start. */
final class Mc263StructureOwner {
    private Mc263StructureOwner() { }

    static long owner(String structureKey, String startKey) {
        Objects.requireNonNull(structureKey, "structure key");
        Objects.requireNonNull(startKey, "structure start key");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is required by the Java runtime", exception);
        }
        digest.update(structureKey.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        byte[] hash = digest.digest(startKey.getBytes(StandardCharsets.UTF_8));
        long owner = 0L;
        for (int index = 0; index < Long.BYTES; index++) {
            owner = (owner << Byte.SIZE) | Byte.toUnsignedLong(hash[index]);
        }
        return owner;
    }
}
