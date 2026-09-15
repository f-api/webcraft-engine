package com.gameexpert.engine.persistence.finalcarrier.bees;

import com.gameexpert.authority.versioned.NeutralFinalChunk;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.security.MessageDigest;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Immutable terminal receipt for one schema-4 canonical BEES lane installation. */
@Getter
@Entity
@Table(name = "world_canonical_bee_installations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_canonical_bees_installation",
                columnNames = {"world_id", "chunk_x", "chunk_z", "installation_identity"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldCanonicalBeeInstallation {
    public enum Status { ACKNOWLEDGED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private long worldId;
    @Column(nullable = false) private int chunkX;
    @Column(nullable = false) private int chunkZ;
    @Column(nullable = false, length = 255) private String installationIdentity;
    @Column(nullable = false, length = 64) private String sourceFingerprint;
    @Column(nullable = false, length = 64) private String payloadFingerprint;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private Status status;
    @Lob @Column(nullable = false, columnDefinition = "longblob") private byte[] canonicalReceipt;
    @Lob @Column(nullable = false, columnDefinition = "longblob") private byte[] occupantPayload;

    public WorldCanonicalBeeInstallation(long worldId, int chunkX, int chunkZ,
            String installationIdentity, String sourceFingerprint, String payloadFingerprint,
            Status status, byte[] canonicalReceipt, byte[] occupantPayload) {
        if (worldId <= 0L) throw new IllegalArgumentException("positive world ID required");
        this.worldId = worldId;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.installationIdentity = requireText(installationIdentity, "installation identity");
        this.sourceFingerprint = requireFingerprint(sourceFingerprint, "source fingerprint");
        this.payloadFingerprint = requireFingerprint(payloadFingerprint, "payload fingerprint");
        this.status = java.util.Objects.requireNonNull(status, "BEES terminal status");
        this.canonicalReceipt = requireBytes(canonicalReceipt, "canonical receipt");
        this.occupantPayload = requireBytes(occupantPayload, "occupant payload");
        CanonicalBeePayload.decode(this.occupantPayload);
    }

    public boolean samePayload(String sourceFingerprint, String payloadFingerprint,
            byte[] occupantPayload) {
        return this.sourceFingerprint.equals(sourceFingerprint)
                && this.payloadFingerprint.equals(payloadFingerprint)
                && MessageDigest.isEqual(this.occupantPayload, occupantPayload);
    }

    public List<NeutralFinalChunk.BeeNest> exactNests() {
        return CanonicalBeePayload.decode(occupantPayload);
    }

    public byte[] getCanonicalReceipt() {
        return canonicalReceipt.clone();
    }

    public byte[] getOccupantPayload() {
        return occupantPayload.clone();
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException(label + " required");
        }
        return value;
    }

    private static String requireFingerprint(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be SHA-256");
        }
        return value;
    }

    private static byte[] requireBytes(byte[] value, String label) {
        if (value == null || value.length == 0) throw new IllegalArgumentException(label + " required");
        return value.clone();
    }
}
