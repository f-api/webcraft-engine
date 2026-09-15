package com.gameexpert.qa.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/** Durable one-shot identity for an authority-evidence creation capability. */
@Entity
@Table(name = "authority_evidence_creation_receipts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_authority_evidence_receipt_world",
                columnNames = "world_id"),
        @UniqueConstraint(name = "uk_authority_evidence_receipt_digest",
                columnNames = "receipt_digest")
})
public class AuthorityEvidenceCreationReceipt {
    public enum State {
        PENDING,
        CONSUMED
    }

    private static final String NICKNAME_PATTERN = "[A-Za-z0-9_]{2,12}";
    private static final String SHA256_PATTERN = "[0-9a-f]{64}";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "world_id", nullable = false)
    private Long worldId;

    @Column(nullable = false, length = 12)
    private String nickname;

    /** SHA-256 of the bearer receipt; raw bearer material is never stored. */
    @Column(name = "receipt_digest", nullable = false, length = 64)
    private String receiptDigest;

    @Enumerated(EnumType.STRING)
    @Column(name = "receipt_state", nullable = false, length = 8)
    private State state;

    /**
     * The durable authority-evidence aggregate.  It is an opaque, versioned binary envelope
     * owned and validated by AuthorityEvidenceJournal; the bearer receipt is never included.
     */
    @Lob
    @Column(name = "evidence_state", columnDefinition = "longblob")
    private byte[] evidenceState;

    /** Exact committed revision encoded by the journal envelope. */
    @Column(name = "evidence_revision", nullable = false,
            columnDefinition = "bigint not null default 0")
    private long evidenceRevision;

    protected AuthorityEvidenceCreationReceipt() {
    }

    public AuthorityEvidenceCreationReceipt(Long worldId, String nickname,
            String receiptDigest) {
        requireIdentity(worldId, nickname, receiptDigest, State.PENDING, 0);
        this.worldId = worldId;
        this.nickname = nickname;
        this.receiptDigest = receiptDigest;
        this.state = State.PENDING;
        this.evidenceRevision = 0;
    }

    public Long id() {
        return id;
    }

    public Long worldId() {
        return worldId;
    }

    public String nickname() {
        return nickname;
    }

    public String receiptDigest() {
        return receiptDigest;
    }

    public State state() {
        return state;
    }

    public long version() {
        return version;
    }

    public byte[] evidenceState() {
        return evidenceState == null ? null : evidenceState.clone();
    }

    public long evidenceRevision() {
        return evidenceRevision;
    }

    public void consume() {
        if (state != State.PENDING) {
            throw new IllegalStateException("authority evidence receipt is not pending");
        }
        state = State.CONSUMED;
    }

    /** Stores one complete journal image and its exact commit revision. */
    public void replaceEvidenceState(byte[] stateBytes, long revision) {
        if (stateBytes == null || stateBytes.length == 0 || revision <= 0
                || revision == Long.MAX_VALUE || state != State.CONSUMED
                || evidenceRevision == Long.MAX_VALUE || revision != evidenceRevision + 1) {
            throw new IllegalArgumentException("authority evidence durable state is invalid");
        }
        evidenceState = stateBytes.clone();
        evidenceRevision = revision;
    }

    /** Exact pending binding check for callers before the repository-level state CAS. */
    public void requirePendingBinding(Long expectedWorldId, String expectedNickname,
            String expectedReceiptDigest) {
        if (!isUsableWorldId(expectedWorldId) || !isNickname(expectedNickname)
                || !isSha256(expectedReceiptDigest)
                || state != State.PENDING || !worldId.equals(expectedWorldId)
                || !nickname.equals(expectedNickname)
                || !receiptDigest.equals(expectedReceiptDigest)) {
            throw new IllegalStateException("authority evidence receipt binding is not pending");
        }
    }

    @PostLoad
    private void validateLoadedState() {
        requireIdentity(worldId, nickname, receiptDigest, state, version);
        if (id == null || id <= 0 || id == Long.MAX_VALUE) {
            throw new IllegalStateException("stored authority evidence receipt id is invalid");
        }
        if (evidenceRevision < 0 || evidenceRevision == Long.MAX_VALUE
                || (evidenceState == null ? evidenceRevision != 0 : evidenceRevision <= 0)) {
            throw new IllegalStateException("stored authority evidence revision is invalid");
        }
    }

    private static void requireIdentity(Long worldId, String nickname, String receiptDigest,
            State state, long version) {
        if (!isUsableWorldId(worldId) || !isNickname(nickname) || !isSha256(receiptDigest)
                || state == null || version < 0 || version == Long.MAX_VALUE) {
            throw new IllegalArgumentException("authority evidence receipt identity is invalid");
        }
    }

    private static boolean isUsableWorldId(Long value) {
        return value != null && value > 0 && value < Long.MAX_VALUE;
    }

    private static boolean isNickname(String value) {
        return value != null && value.matches(NICKNAME_PATTERN);
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches(SHA256_PATTERN);
    }
}
