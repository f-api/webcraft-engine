package com.gameexpert.engine.persistence.finalcarrier.reference;

import com.gameexpert.authority.versioned.CanonicalStructureSnapshot;
import com.gameexpert.authority.versioned.LateLootOutcome;
import com.gameexpert.world.WorldGenerationProfile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BiFunction;

/** Logical V1 bytes remain unchanged; physical storage may be split into bounded pages. */
public final class LateLootReferenceCodec {
    public static final int MEMBERSHIP_MAGIC = 0x574c5331;
    public static final int CLAIMS_MAGIC = 0x574c4331;
    private LateLootReferenceCodec() { }

    public static byte[] membership(CanonicalStructureSnapshot snapshot) {
        return encode(output -> writeMembership(snapshot, output));
    }
    public static void writeMembership(CanonicalStructureSnapshot snapshot, OutputStream output) throws IOException {
        DataOutputStream out = new DataOutputStream(output);
        out.writeInt(MEMBERSHIP_MAGIC); out.writeByte(1); out.writeInt(snapshot.rows().size());
        for (CanonicalStructureSnapshot.Row row : snapshot.rows()) {
            out.writeInt(row.chunkX()); out.writeInt(row.chunkZ()); out.writeUTF(sha256(row.carrier()));
        }
        out.flush();
    }
    public static byte[] claims(List<LateLootOutcome.Claim> input) {
        return encode(output -> writeClaims(input, output));
    }
    public static void writeClaims(List<LateLootOutcome.Claim> input, OutputStream output) throws IOException {
        List<LateLootOutcome.Claim> ordered = ordered(input);
        DataOutputStream out = new DataOutputStream(output);
        out.writeInt(CLAIMS_MAGIC); out.writeByte(1); out.writeInt(ordered.size());
        for (LateLootOutcome.Claim row : ordered) {
            out.writeUTF(row.structureId()); out.writeInt(row.originChunkX()); out.writeInt(row.originChunkZ());
            out.writeUTF(row.structureRowSha256());
        }
        out.flush();
    }
    public static String claimsFingerprint(List<LateLootOutcome.Claim> input) {
        return hash(output -> writeClaims(input, output));
    }
    public static List<LateLootOutcome.Claim> readClaims(byte[] bytes) {
        return readClaims(new ByteArrayInputStream(bytes));
    }
    public static List<LateLootOutcome.Claim> readClaims(InputStream bytes) {
        try (DataInputStream in = read(bytes, CLAIMS_MAGIC)) {
            int count = count(in);
            List<LateLootOutcome.Claim> result = new ArrayList<>();
            String previous = null;
            for (int i = 0; i < count; i++) {
                LateLootOutcome.Claim row = new LateLootOutcome.Claim(in.readUTF(), in.readInt(), in.readInt(), in.readUTF());
                String key = key(row);
                if (previous != null && previous.compareTo(key) >= 0) throw new IOException("claim order drift");
                result.add(row); previous = key;
            }
            if (in.read() != -1) throw new IOException("trailing claims");
            return List.copyOf(result);
        } catch (IOException failure) {
            throw new IllegalArgumentException("invalid saved reference claims", failure);
        }
    }

    public static CanonicalStructureSnapshot restore(WorldLootReferenceSnapshot saved, CanonicalStructureSnapshot current) {
        Map<Long, CanonicalStructureSnapshot.Row> available = new HashMap<>();
        current.rows().forEach(row -> available.put(key(row.chunkX(), row.chunkZ()), row));
        return restore(saved, current.worldId(), current.profile(), new ByteArrayInputStream(saved.getMembershipPayload()),
                new ByteArrayInputStream(saved.getClaimsPayload()), (x, z) -> available.get(key(x, z)));
    }

    public static CanonicalStructureSnapshot restore(WorldLootReferenceSnapshot saved, long worldId,
            WorldGenerationProfile profile, InputStream membership, InputStream claimBytes,
            BiFunction<Integer, Integer, CanonicalStructureSnapshot.Row> lookup) {
        if (saved.getWorldId() != worldId || !saved.getBaselineId().equals(profile.getBaselineId())) {
            throw new IllegalArgumentException("snapshot world/profile differs");
        }
        try (DataInputStream in = read(membership, MEMBERSHIP_MAGIC)) {
            int count = count(in);
            List<CanonicalStructureSnapshot.Row> rows = new ArrayList<>();
            int previousX = 0, previousZ = 0;
            for (int i = 0; i < count; i++) {
                int x = in.readInt(), z = in.readInt(); String expected = in.readUTF();
                if (i > 0 && (x < previousX || x == previousX && z <= previousZ)) throw new IOException("membership order drift");
                previousX = x; previousZ = z;
                CanonicalStructureSnapshot.Row row = lookup.apply(x, z);
                if (row == null || !sha256(row.carrier()).equals(expected)) throw new IOException("immutable structure member missing or changed");
                rows.add(row);
            }
            if (in.read() != -1) throw new IOException("trailing snapshot membership");
            CanonicalStructureSnapshot restored = new CanonicalStructureSnapshot(worldId, profile, rows);
            List<LateLootOutcome.Claim> before = readClaims(claimBytes);
            if (!restored.receipt().equals(saved.getStructureSnapshotReceipt())
                    || !claimsFingerprint(before).equals(saved.getClaimsFingerprint())
                    || !epoch(restored.receipt(), before).equals(saved.getSnapshotIdentity())) {
                throw new IOException("historical snapshot receipt differs");
            }
            return restored;
        } catch (IOException failure) {
            throw new IllegalArgumentException("invalid immutable replay membership", failure);
        }
    }

    public static String epoch(String structureReceipt, List<LateLootOutcome.Claim> claims) {
        if (structureReceipt == null || !structureReceipt.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid structure receipt");
        List<LateLootOutcome.Claim> ordered = ordered(claims);
        return hash(output -> {
            DataOutputStream out = new DataOutputStream(output);
            out.write("WEBCRAFT-STRUCTURE-CLAIM-SNAPSHOT-V1\0".getBytes(StandardCharsets.US_ASCII));
            out.writeUTF(structureReceipt); out.writeInt(ordered.size());
            for (LateLootOutcome.Claim row : ordered) { out.writeUTF(key(row)); out.writeUTF(row.structureRowSha256()); }
            out.flush();
        });
    }
    public static String sha256(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static List<LateLootOutcome.Claim> ordered(List<LateLootOutcome.Claim> rows) {
        Objects.requireNonNull(rows, "claims");
        List<LateLootOutcome.Claim> result = rows.stream().sorted(Comparator.comparing(LateLootReferenceCodec::key)).toList();
        for (int i = 1; i < result.size(); i++) {
            if (key(result.get(i - 1)).equals(key(result.get(i)))) throw new IllegalArgumentException("duplicate claim");
        }
        return result;
    }
    private static String key(LateLootOutcome.Claim row) { return row.structureId() + "\0" + row.originChunkX() + "\0" + row.originChunkZ(); }
    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
    private static DataInputStream read(InputStream bytes, int magic) throws IOException {
        DataInputStream in = new DataInputStream(Objects.requireNonNull(bytes));
        if (in.readInt() != magic || in.readUnsignedByte() != 1) throw new IOException("snapshot schema differs");
        return in;
    }
    private static int count(DataInputStream in) throws IOException {
        int count = in.readInt(); if (count < 0) throw new IOException("snapshot row bounds"); return count;
    }
    private static byte[] encode(SegmentedReferencePayload.Writer writer) {
        try { ByteArrayOutputStream bytes = new ByteArrayOutputStream(); writer.write(bytes); return bytes.toByteArray(); }
        catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String hash(SegmentedReferencePayload.Writer writer) {
        MessageDigest digest = digest();
        try { writer.write(new DigestOutputStream(OutputStream.nullOutputStream(), digest)); return HexFormat.of().formatHex(digest.digest()); }
        catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
}
