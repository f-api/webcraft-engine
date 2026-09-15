package com.gameexpert.map.repository;

import com.gameexpert.map.entity.WorldPlayerMapSettlement;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldPlayerMapSettlementRepository
        extends JpaRepository<WorldPlayerMapSettlement, Long> {
    interface SettlementState {
        Long getPlayerId();
        long getSourceInventoryRevision();
        long getCommittedInventoryRevision();
        int getMapId();
        long getGroundEntityId();
        String getCommandFingerprint();
    }

    Optional<WorldPlayerMapSettlement> findByWorldIdAndSettlementId(Long worldId, long settlementId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select receipt from WorldPlayerMapSettlement receipt
            where receipt.worldId = :worldId and receipt.settlementId = :settlementId
            """)
    Optional<WorldPlayerMapSettlement> findForUpdate(
            @Param("worldId") Long worldId, @Param("settlementId") long settlementId);

    @Query("""
            select receipt.playerId as playerId,
                   receipt.sourceInventoryRevision as sourceInventoryRevision,
                   receipt.committedInventoryRevision as committedInventoryRevision,
                   receipt.mapId as mapId,
                   receipt.groundEntityId as groundEntityId,
                   receipt.commandFingerprint as commandFingerprint
            from WorldPlayerMapSettlement receipt
            where receipt.worldId = :worldId and receipt.settlementId = :settlementId
            """)
    Optional<SettlementState> findState(
            @Param("worldId") Long worldId, @Param("settlementId") long settlementId);

    @Query("""
            select max(receipt.mapId) from WorldPlayerMapSettlement receipt
            where receipt.worldId = :worldId
            """)
    Integer findMaximumReservedMapId(@Param("worldId") Long worldId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update WorldPlayerMapSettlement receipt
            set receipt.committedInventoryRevision = :committedRevision,
                receipt.groundEntityId = :groundEntityId,
                receipt.commandFingerprint = :terminalFingerprint
            where receipt.worldId = :worldId
              and receipt.settlementId = :settlementId
              and receipt.playerId = :playerId
              and receipt.sourceInventoryRevision = :sourceRevision
              and receipt.committedInventoryRevision = :sourceRevision
              and receipt.mapId = :mapId
              and receipt.groundEntityId = :pendingGroundEntityId
              and receipt.commandFingerprint = :reservationFingerprint
            """)
    int completePending(
            @Param("worldId") Long worldId,
            @Param("settlementId") long settlementId,
            @Param("playerId") Long playerId,
            @Param("sourceRevision") long sourceRevision,
            @Param("committedRevision") long committedRevision,
            @Param("mapId") int mapId,
            @Param("pendingGroundEntityId") long pendingGroundEntityId,
            @Param("groundEntityId") long groundEntityId,
            @Param("reservationFingerprint") String reservationFingerprint,
            @Param("terminalFingerprint") String terminalFingerprint);

    long countByWorldId(Long worldId);

    @Modifying
    @Query("delete from WorldPlayerMapSettlement receipt where receipt.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
