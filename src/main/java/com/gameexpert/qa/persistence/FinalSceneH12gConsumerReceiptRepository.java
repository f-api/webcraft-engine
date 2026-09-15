package com.gameexpert.qa.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinalSceneH12gConsumerReceiptRepository
        extends JpaRepository<FinalSceneH12gConsumerReceipt, Long> {
    Optional<FinalSceneH12gConsumerReceipt>
            findByWorldIdAndSourceIdentityAndPhaseAndIdempotencyKey(
                    Long worldId, String sourceIdentity, String phase, String idempotencyKey);

    /** Serializes the exact world/source/phase/idempotency replay decision. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select receipt from FinalSceneH12gConsumerReceipt receipt "
            + "where receipt.worldId = :worldId "
            + "and receipt.sourceIdentity = :sourceIdentity "
            + "and receipt.phase = :phase "
            + "and receipt.idempotencyKey = :idempotencyKey")
    Optional<FinalSceneH12gConsumerReceipt> findByWorldIdAndSourceIdentityAndPhaseAndIdempotencyKeyForUpdate(
            @Param("worldId") Long worldId,
            @Param("sourceIdentity") String sourceIdentity,
            @Param("phase") String phase,
            @Param("idempotencyKey") String idempotencyKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from FinalSceneH12gConsumerReceipt receipt where receipt.worldId = :worldId")
    int deleteAllByWorldId(@Param("worldId") Long worldId);
}
