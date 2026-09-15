package com.gameexpert.engine.persistence.finalcarrier;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinalCarrierScheduledTickRepository
        extends JpaRepository<FinalCarrierScheduledTick, Long> {
    /**
     * Scalar-only view used before any current-hash entity query. Boxed values preserve SQL NULL
     * from a legacy row without invoking entity hydration or lifecycle validation.
     */
    interface LegacyScheduledTickIdProjection {
        Long getId();
        Long getDurableOrder();
        String getSourceFingerprint();
        String getPayloadFingerprint();
    }

    /** Finds incomplete legacy rows before current-hash reads are allowed to hydrate entities. */
    @Query("""
            select row.id as id,
                   row.durableOrder as durableOrder,
                   row.sourceFingerprint as sourceFingerprint,
                   row.payloadFingerprint as payloadFingerprint
              from FinalCarrierScheduledTick row
             where row.worldId = :worldId
               and (row.durableOrder is null
                    or row.sourceFingerprint is null
                    or row.payloadFingerprint is null)
             order by row.id asc
            """)
    List<LegacyScheduledTickIdProjection> findIncompleteLegacyRowsByWorldId(
            @Param("worldId") long worldId, Pageable limit);

    /** Bounded world/lane queue read in durable admission order. The caller supplies limit plus one. */
    List<FinalCarrierScheduledTick> findAllByWorldIdAndLaneOrderByDurableOrderAscIdAsc(
            long worldId, String lane, Pageable limit);

    /** Bounded exact world/chunk/lane queue read in durable admission order. The caller supplies limit plus one. */
    List<FinalCarrierScheduledTick>
            findAllByWorldIdAndChunkXAndChunkZAndLaneOrderByDurableOrderAscIdAsc(
                    long worldId, int chunkX, int chunkZ, String lane, Pageable limit);

    /** Bounded world history read in durable admission order. The caller supplies limit plus one. */
    List<FinalCarrierScheduledTick> findAllByWorldIdOrderByDurableOrderAscIdAsc(
            long worldId, Pageable limit);

    /** Bounded exact world/chunk history read in durable admission order. The caller supplies limit plus one. */
    List<FinalCarrierScheduledTick>
            findAllByWorldIdAndChunkXAndChunkZOrderByDurableOrderAscIdAsc(
                    long worldId, int chunkX, int chunkZ, Pageable limit);

    /** Bounded current-source world/lane history read in durable admission order. The caller supplies limit plus one. */
    List<FinalCarrierScheduledTick>
            findAllByWorldIdAndLaneAndSourceFingerprintOrderByDurableOrderAscIdAsc(
                    long worldId, String lane, String sourceFingerprint, Pageable limit);

    /** Bounded current-source world/chunk/lane history read in durable admission order. The caller supplies limit plus one. */
    List<FinalCarrierScheduledTick>
            findAllByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprintOrderByDurableOrderAscIdAsc(
                    long worldId, int chunkX, int chunkZ, String lane,
                    String sourceFingerprint, Pageable limit);

    /** Locks the exact key and current source fingerprint for concurrent settlement. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FinalCarrierScheduledTick>
            findLockedByWorldIdAndChunkXAndChunkZAndLaneAndXAndYAndZAndTypeKeyAndSourceFingerprint(
                    long worldId, int chunkX, int chunkZ, String lane,
                    int x, int y, int z, String typeKey, String sourceFingerprint);

    Optional<FinalCarrierScheduledTick> findFirstByWorldIdAndLaneOrderByDurableOrderDescIdDesc(
            long worldId, String lane);

    Optional<FinalCarrierScheduledTick>
            findFirstByWorldIdAndLaneAndSourceFingerprintOrderByDurableOrderDescIdDesc(
                    long worldId, String lane, String sourceFingerprint);

    long countByWorldIdAndLane(long worldId, String lane);

    long countByWorldIdAndLaneAndSourceFingerprint(
            long worldId, String lane, String sourceFingerprint);

    /**
     * Removes every canonical row this world owns. World deletion must clear the canonical
     * tables in the same transaction, otherwise the rows outlive the world and a re-created
     * world id inherits them.
     */
    @Modifying
    @Query("delete from FinalCarrierScheduledTick row where row.worldId = :worldId")
    int deleteByWorldId(@Param("worldId") long worldId);
}
