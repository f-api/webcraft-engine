package com.gameexpert.qa.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * One immutable durable result of consuming an authenticated H12g product or publication.
 * The receipt is the only committed effect owned by this boundary.
 */
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "`final_scene_h12g_consumer_receipts`", uniqueConstraints =
        @UniqueConstraint(name = "uk_h12g_consumer_receipt_identity",
                columnNames = {"world_id", "source_identity", "phase", "idempotency_key"}))
public class FinalSceneH12gConsumerReceipt {
    public static final String PRODUCT_PHASE = "PRODUCT";
    public static final String PUBLICATION_PHASE = "PUBLICATION";
    public static final int SOURCE_IDENTITY_BYTES = 64;
    public static final int MAX_OPERATION_ID_BYTES = 512;
    public static final int MAX_REQUEST_FINGERPRINT_BYTES = 262_144;
    public static final int MAX_AUTHORIZATION_BYTES = FinalSceneH12gTerminal.MAX_ENVELOPE_BYTES;

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "world_id", nullable = false)
    private Long worldId;

    @Column(name = "source_identity", nullable = false, length = SOURCE_IDENTITY_BYTES)
    private String sourceIdentity;

    @Column(nullable = false, length = 16)
    private String phase;

    @Column(name = "idempotency_key", nullable = false, length = SOURCE_IDENTITY_BYTES)
    private String idempotencyKey;

    @Column(name = "operation_id", nullable = false, length = MAX_OPERATION_ID_BYTES)
    private String operationId;

    @Lob
    @Column(name = "`authorization`", nullable = false, columnDefinition = "longtext")
    private String authorization;

    @Lob
    @Column(name = "request_fingerprint", nullable = false, columnDefinition = "longtext")
    private String requestFingerprint;

    @Column(nullable = false, length = 32)
    private String result;

    protected FinalSceneH12gConsumerReceipt() {
    }

    public FinalSceneH12gConsumerReceipt(Long worldId, String sourceIdentity, String phase,
            String idempotencyKey, String operationId, String authorization,
            String requestFingerprint, String result) {
        requireIdentity(worldId, sourceIdentity, phase, idempotencyKey, operationId,
                authorization, requestFingerprint, result, 0);
        this.worldId = worldId;
        this.sourceIdentity = sourceIdentity;
        this.phase = phase;
        this.idempotencyKey = idempotencyKey;
        this.operationId = operationId;
        this.authorization = authorization;
        this.requestFingerprint = requestFingerprint;
        this.result = result;
    }

    public Long id() {
        return id;
    }

    public long version() {
        return version;
    }

    public Long worldId() {
        return worldId;
    }

    public String sourceIdentity() {
        return sourceIdentity;
    }

    public String phase() {
        return phase;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String operationId() {
        return operationId;
    }

    public String authorization() {
        return authorization;
    }

    public String requestFingerprint() {
        return requestFingerprint;
    }

    public String result() {
        return result;
    }

    public boolean isProduct() {
        return PRODUCT_PHASE.equals(phase);
    }

    public boolean isPublication() {
        return PUBLICATION_PHASE.equals(phase);
    }

    public boolean matches(String expectedOperationId, String expectedIdempotencyKey,
            String expectedAuthorization, String expectedRequestFingerprint) {
        return operationId.equals(expectedOperationId)
                && idempotencyKey.equals(expectedIdempotencyKey)
                && authorization.equals(expectedAuthorization)
                && requestFingerprint.equals(expectedRequestFingerprint);
    }

    @PostLoad
    private void validateLoadedState() {
        requireIdentity(worldId, sourceIdentity, phase, idempotencyKey, operationId,
                authorization, requestFingerprint, result, version);
        if (id == null || id <= 0 || id == Long.MAX_VALUE) {
            throw new IllegalStateException("stored H12g consumer receipt id is invalid");
        }
    }

    private static void requireIdentity(Long worldId, String sourceIdentity, String phase,
            String idempotencyKey, String operationId, String authorization,
            String requestFingerprint, String result, long version) {
        if (worldId == null || worldId <= 0 || worldId == Long.MAX_VALUE
                || version < 0 || version == Long.MAX_VALUE
                || sourceIdentity == null || !SHA256.matcher(sourceIdentity).matches()
                || idempotencyKey == null || !SHA256.matcher(idempotencyKey).matches()
                || !canonical(operationId, MAX_OPERATION_ID_BYTES)
                || !bounded(authorization, MAX_AUTHORIZATION_BYTES)
                || !canonical(requestFingerprint, MAX_REQUEST_FINGERPRINT_BYTES)
                || (!PRODUCT_PHASE.equals(phase) && !PUBLICATION_PHASE.equals(phase))
                || !validResult(phase, result)) {
            throw new IllegalArgumentException("H12g consumer receipt identity is invalid");
        }
    }

    private static boolean validResult(String phase, String result) {
        if (PRODUCT_PHASE.equals(phase)) {
            return "SUCCESS".equals(result) || "RETRY".equals(result)
                    || "REJECTED".equals(result) || "CLASSIFIED_FAILURE".equals(result);
        }
        return "ACKNOWLEDGED".equals(result) || "RETRY".equals(result);
    }

    private static boolean bounded(String value, int maximumBytes) {
        return value != null && !value.isEmpty()
                && FinalSceneH12gTerminal.strictUtf8Length(value) <= maximumBytes;
    }

    private static boolean canonical(String value, int maximumBytes) {
        return bounded(value, maximumBytes)
                && value.equals(Normalizer.normalize(value, Normalizer.Form.NFC))
                && value.equals(value.strip())
                && value.chars().noneMatch(character -> character <= 0x1f || character == 0x7f);
    }
}
