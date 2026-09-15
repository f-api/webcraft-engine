package com.gameexpert.engine.persistence.finalcarrier;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Exact durable semantic mutation installed from one canonical final-carrier lane. */
@Getter
@Entity
@Table(name = "final_carrier_lane_mutations", uniqueConstraints = @UniqueConstraint(
        name = "uk_final_carrier_lane_source",
        columnNames = {"world_id", "chunk_x", "chunk_z", "lane", "source_fingerprint"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinalCarrierLaneMutation {
    public enum ActivationStatus {
        PENDING, INSTALLED, REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private long worldId;
    @Column(nullable = false) private int chunkX;
    @Column(nullable = false) private int chunkZ;
    @Column(nullable = false, length = 20) private String lane;
    @Column(nullable = false, length = 64) private String sourceFingerprint;
    @Column(nullable = false, length = 64) private String payloadFingerprint;
    @Column(nullable = false, length = 255) private String installationIdentity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private ActivationStatus activationStatus;
    @Lob @Column(nullable = false, columnDefinition = "longblob") private byte[] typedPayload;

    public FinalCarrierLaneMutation(long worldId, int chunkX, int chunkZ, String lane,
            String sourceFingerprint, String payloadFingerprint, byte[] typedPayload) {
        this.worldId = worldId;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.lane = lane;
        this.sourceFingerprint = sourceFingerprint;
        this.payloadFingerprint = payloadFingerprint;
        this.installationIdentity = worldId + ":" + chunkX + ":" + chunkZ + ":" + lane + ":"
                + sourceFingerprint + ":" + payloadFingerprint;
        this.activationStatus = ActivationStatus.PENDING;
        this.typedPayload = typedPayload.clone();
    }

    public boolean matches(String payloadFingerprint, byte[] typedPayload) {
        return this.payloadFingerprint.equals(payloadFingerprint)
                && java.security.MessageDigest.isEqual(this.typedPayload, typedPayload);
    }

    public byte[] getTypedPayload() {
        return typedPayload.clone();
    }

    public void markInstalled() {
        activationStatus = ActivationStatus.INSTALLED;
    }

    public void markRejected() {
        activationStatus = ActivationStatus.REJECTED;
    }
}
