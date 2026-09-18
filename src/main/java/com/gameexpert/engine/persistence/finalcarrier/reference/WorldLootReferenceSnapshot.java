package com.gameexpert.engine.persistence.finalcarrier.reference;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Immutable membership and prior-claim evidence used to replay a selected producer's late loot. */
@Getter
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "world_loot_reference_snapshots", uniqueConstraints =
        @UniqueConstraint(name = "uk_world_loot_reference_snapshot", columnNames = {
                "world_id", "baseline_id", "snapshot_identity"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldLootReferenceSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private long worldId;
    @Column(nullable = false, updatable = false, length = 128) private String baselineId;
    @Column(nullable = false, updatable = false, length = 64) private String snapshotIdentity;
    @Column(nullable = false, updatable = false, length = 64) private String structureSnapshotReceipt;
    @Column(nullable = false, updatable = false, length = 64) private String claimsFingerprint;
    @Getter(AccessLevel.NONE) @Lob @Column(nullable = false, updatable = false, columnDefinition = "LONGBLOB")
    private byte[] membershipPayload;
    @Getter(AccessLevel.NONE) @Lob @Column(nullable = false, updatable = false, columnDefinition = "LONGBLOB")
    private byte[] claimsPayload;

    public WorldLootReferenceSnapshot(long worldId, String baselineId, String snapshotIdentity,
            String structureSnapshotReceipt, String claimsFingerprint,
            byte[] membershipPayload, byte[] claimsPayload) {
        if (worldId <= 0 || baselineId == null || baselineId.isBlank() || baselineId.length() > 128
                || !digest(snapshotIdentity) || !digest(structureSnapshotReceipt) || !digest(claimsFingerprint)) {
            throw new IllegalArgumentException("Invalid late loot reference identity");
        }
        requirePayload(membershipPayload, 0x574c5331);
        requirePayload(claimsPayload, 0x574c4331);
        this.worldId = worldId;
        this.baselineId = baselineId;
        this.snapshotIdentity = snapshotIdentity;
        this.structureSnapshotReceipt = structureSnapshotReceipt;
        this.claimsFingerprint = claimsFingerprint;
        this.membershipPayload = membershipPayload.clone();
        this.claimsPayload = claimsPayload.clone();
    }

    public byte[] getMembershipPayload() { return membershipPayload.clone(); }
    public byte[] getClaimsPayload() { return claimsPayload.clone(); }

    public boolean matchesEvidence(String structureReceipt, String claimsHash,
            byte[] membership, byte[] claims) {
        return structureSnapshotReceipt.equals(structureReceipt) && claimsFingerprint.equals(claimsHash)
                && logicalHash(membershipPayload).equals(logicalHash(membership))
                && logicalHash(claimsPayload).equals(logicalHash(claims));
    }

    private static String logicalHash(byte[] payload) {
        return SegmentedReferencePayload.segmented(payload)
                ? java.util.HexFormat.of().formatHex(java.util.Arrays.copyOfRange(payload, 17, 49))
                : LateLootReferenceCodec.sha256(payload);
    }

    private static boolean digest(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }
    private static void requirePayload(byte[] bytes, int magic) {
        if (bytes == null || bytes.length < 9) {
            throw new IllegalArgumentException("Invalid late reference payload bounds");
        }
        java.nio.ByteBuffer reader = java.nio.ByteBuffer.wrap(bytes);
        if (reader.getInt() != magic) throw new IllegalArgumentException("Invalid late reference payload magic");
        byte version = reader.get();
        if ((version != 1 && !(version == 2 && SegmentedReferencePayload.segmented(bytes))) || reader.getInt() < 0) {
            throw new IllegalArgumentException("Invalid late reference payload envelope");
        }
        // The selected worker validates the complete ordered rows and authenticated receipts.
    }
}
