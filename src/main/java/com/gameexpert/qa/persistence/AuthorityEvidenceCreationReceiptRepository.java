package com.gameexpert.qa.persistence;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AuthorityEvidenceCreationReceiptRepository
        extends JpaRepository<AuthorityEvidenceCreationReceipt, Long> {
    Optional<AuthorityEvidenceCreationReceipt> findByWorldId(Long worldId);

    /** Serializes every durable evidence transition for one immutable world binding. */
    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select receipt from AuthorityEvidenceCreationReceipt receipt "
            + "where receipt.worldId = :worldId")
    Optional<AuthorityEvidenceCreationReceipt> findByWorldIdForUpdate(
            @Param("worldId") Long worldId);

    Optional<AuthorityEvidenceCreationReceipt> findByReceiptDigest(String receiptDigest);

    /**
     * Exact PENDING-to-CONSUMED compare-and-set. World, nickname, digest, expected state and
     * optimistic version all participate, so replay and cross-binding attempts return zero.
     *
     * <p>The byte comparison names an explicit length: H2 reads a bare {@code BINARY} cast as
     * {@code BINARY(1)} and would compare only the first byte. Both databases zero-pad
     * {@code BINARY(64)}, so the equal character lengths keep the comparison exact.</p>
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update authority_evidence_creation_receipts "
            + "set receipt_state = 'CONSUMED', version = version + 1 "
            + "where world_id = :worldId "
            + "and char_length(nickname) = char_length(:nickname) "
            + "and cast(nickname as binary(64)) = cast(:nickname as binary(64)) "
            + "and char_length(receipt_digest) = char_length(:receiptDigest) "
            + "and cast(receipt_digest as binary(64)) = cast(:receiptDigest as binary(64)) "
            + "and receipt_state = 'PENDING' "
            + "and version = :expectedVersion", nativeQuery = true)
    int consumePendingExact(
            @Param("worldId") Long worldId,
            @Param("nickname") String nickname,
            @Param("receiptDigest") String receiptDigest,
            @Param("expectedVersion") long expectedVersion);
}
