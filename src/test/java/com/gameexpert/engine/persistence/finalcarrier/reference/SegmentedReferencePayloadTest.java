package com.gameexpert.engine.persistence.finalcarrier.reference;

import com.gameexpert.authority.versioned.LateLootOutcome;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SegmentedReferencePayloadTest {
    @Test
    void streamsPastOldAggregateLimitWithOnePageInMemory() throws Exception {
        int length = 65 * 1024 * 1024 + 17;
        byte[] block = new byte[SegmentedReferencePayload.PAGE_BYTES];
        Arrays.fill(block, (byte) 37);
        int[] written = {0};
        byte[] marker = SegmentedReferencePayload.write(LateLootReferenceCodec.MEMBERSHIP_MAGIC, output -> {
            int remaining = length;
            while (remaining > 0) {
                int size = Math.min(remaining, block.length);
                output.write(block, 0, size);
                remaining -= size;
            }
        }, page -> {
            assertTrue(page.length <= SegmentedReferencePayload.PAGE_BYTES);
            written[0]++;
        });
        assertEquals(66, written[0]);
        assertEquals(49, marker.length);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = SegmentedReferencePayload.open(marker, number -> {
            byte[] page = new byte[Math.min(block.length, length - number * block.length)];
            Arrays.fill(page, (byte) 37);
            return page;
        })) {
            byte[] buffer = new byte[8192];
            long read = 0;
            for (int count; (count = input.read(buffer)) != -1;) {
                digest.update(buffer, 0, count);
                read += count;
            }
            assertEquals(length, read);
        }
        assertArrayEquals(Arrays.copyOfRange(marker, 17, 49), digest.digest());
    }

    @Test
    void rejectsMissingAndChangedPages() throws Exception {
        List<byte[]> pages = new ArrayList<>();
        byte[] marker = SegmentedReferencePayload.write(LateLootReferenceCodec.CLAIMS_MAGIC,
                output -> output.write(new byte[SegmentedReferencePayload.PAGE_BYTES + 12]), pages::add);
        try (InputStream missing = SegmentedReferencePayload.open(marker, number -> number == 0 ? pages.get(0) : null)) {
            assertThrows(IOException.class, () -> missing.transferTo(OutputStream.nullOutputStream()));
        }
        pages.get(1)[0] = 1;
        try (InputStream changed = SegmentedReferencePayload.open(marker, pages::get)) {
            assertThrows(IOException.class, () -> changed.transferTo(OutputStream.nullOutputStream()));
        }
    }

    @Test
    void keepsLegacyClaimsAndEpochBytesAndReadsBothStorageLayouts() throws Exception {
        List<LateLootOutcome.Claim> claims = List.of(
                new LateLootOutcome.Claim("minecraft:village", -4, 9, "ab".repeat(32)),
                new LateLootOutcome.Claim("minecraft:fortress", 7, -2, "cd".repeat(32)));
        byte[] legacy = LateLootReferenceCodec.claims(claims);
        assertEquals(LateLootReferenceCodec.sha256(legacy), LateLootReferenceCodec.claimsFingerprint(claims));
        List<byte[]> pages = new ArrayList<>();
        byte[] marker = SegmentedReferencePayload.write(LateLootReferenceCodec.CLAIMS_MAGIC,
                output -> LateLootReferenceCodec.writeClaims(claims, output), pages::add);
        List<LateLootOutcome.Claim> old = LateLootReferenceCodec.readClaims(legacy);
        List<LateLootOutcome.Claim> restored = LateLootReferenceCodec.readClaims(SegmentedReferencePayload.open(marker, pages::get));
        assertArrayEquals(LateLootReferenceCodec.claims(old), LateLootReferenceCodec.claims(restored));
        String receipt = "12".repeat(32);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.write("WEBCRAFT-STRUCTURE-CLAIM-SNAPSHOT-V1\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        output.writeUTF(receipt); output.writeInt(old.size());
        for (LateLootOutcome.Claim row : old) {
            output.writeUTF(row.structureId() + "\0" + row.originChunkX() + "\0" + row.originChunkZ());
            output.writeUTF(row.structureRowSha256());
        }
        assertEquals(LateLootReferenceCodec.sha256(bytes.toByteArray()), LateLootReferenceCodec.epoch(receipt, claims));
    }

    @Test
    void persistedV1EvidenceMatchesPagedV2WithoutChangingIdentity() {
        List<LateLootOutcome.Claim> claims = List.of();
        byte[] membership = java.nio.ByteBuffer.allocate(9).putInt(LateLootReferenceCodec.MEMBERSHIP_MAGIC).put((byte) 1).putInt(0).array();
        byte[] claimBytes = LateLootReferenceCodec.claims(claims);
        String receipt = "aa".repeat(32);
        WorldLootReferenceSnapshot saved = new WorldLootReferenceSnapshot(1, "baseline",
                LateLootReferenceCodec.epoch(receipt, claims), receipt, LateLootReferenceCodec.sha256(claimBytes), membership, claimBytes);
        byte[] memberMarker = SegmentedReferencePayload.write(LateLootReferenceCodec.MEMBERSHIP_MAGIC, output -> output.write(membership), ignored -> {});
        byte[] claimMarker = SegmentedReferencePayload.write(LateLootReferenceCodec.CLAIMS_MAGIC, output -> output.write(claimBytes), ignored -> {});
        assertTrue(saved.matchesEvidence(receipt, LateLootReferenceCodec.claimsFingerprint(claims), memberMarker, claimMarker));
    }
}
