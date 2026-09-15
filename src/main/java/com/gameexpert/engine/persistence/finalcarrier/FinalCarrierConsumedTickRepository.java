package com.gameexpert.engine.persistence.finalcarrier;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinalCarrierConsumedTickRepository
        extends JpaRepository<FinalCarrierConsumedTick, Long> {
    /**
     * Scalar-only view used before PublicationState filtering. Every field that was added to the
     * consumed schema is nullable here so a legacy row can be exposed without hydrating its
     * malformed entity or invoking entity lifecycle validation.
     */
    interface LegacyConsumedTickIdProjection {
        Long getId();
        Integer getChunkX();
        Integer getChunkZ();
        Integer getExpectedBlockId();
        Long getDueTick();
        Integer getPriority();
        Long getSubTickOrder();
        Long getDurableOrder();
        String getSourceFingerprint();
        String getPayloadFingerprint();
        String getDisposition();
        String getPublicationKey();
        String getPublicationDigest();
        byte[] getPublicationBody();
        FinalCarrierConsumedTick.PublicationState getPublicationState();
    }

    /** Finds incomplete legacy rows before any PublicationState-filtered entity query. */
    @Query("""
            select row.id as id,
                   row.chunkX as chunkX,
                   row.chunkZ as chunkZ,
                   row.expectedBlockId as expectedBlockId,
                   row.dueTick as dueTick,
                   row.priority as priority,
                   row.subTickOrder as subTickOrder,
                   row.durableOrder as durableOrder,
                   row.sourceFingerprint as sourceFingerprint,
                   row.payloadFingerprint as payloadFingerprint,
                   row.disposition as disposition,
                   row.publicationKey as publicationKey,
                   row.publicationDigest as publicationDigest,
                   row.publicationBody as publicationBody,
                   row.publicationState as publicationState
              from FinalCarrierConsumedTick row
             where row.worldId = :worldId
               and (row.chunkX is null
                    or row.chunkZ is null
                    or row.expectedBlockId is null
                    or row.dueTick is null
                    or row.priority is null
                    or row.subTickOrder is null
                    or row.durableOrder is null
                    or row.sourceFingerprint is null
                    or row.payloadFingerprint is null
                    or row.disposition is null
                    or row.publicationKey is null
                    or row.publicationDigest is null
                    or row.publicationBody is null
                    or row.publicationState is null)
             order by row.id asc
            """)
    List<LegacyConsumedTickIdProjection> findIncompleteLegacyRowsByWorldId(
            @Param("worldId") long worldId, Pageable limit);

    /** Bounded current-hash outbox read for one world/lane in durable order. The caller supplies limit plus one. */
    List<FinalCarrierConsumedTick>
            findAllByWorldIdAndLaneAndPublicationStateInOrderByDurableOrderAscIdAsc(
                    long worldId, String lane,
                    Collection<FinalCarrierConsumedTick.PublicationState> publicationStates,
                    Pageable limit);

    /** Bounded current-hash outbox read for one exact world/chunk/lane partition. The caller supplies limit plus one. */
    List<FinalCarrierConsumedTick>
            findAllByWorldIdAndChunkXAndChunkZAndLaneAndPublicationStateInOrderByDurableOrderAscIdAsc(
                    long worldId, int chunkX, int chunkZ, String lane,
                    Collection<FinalCarrierConsumedTick.PublicationState> publicationStates,
                    Pageable limit);

    /** Bounded current-hash outbox read for a world. The caller supplies limit plus one. */
    List<FinalCarrierConsumedTick>
            findAllByWorldIdAndPublicationStateInOrderByDurableOrderAscIdAsc(
                    long worldId,
                    Collection<FinalCarrierConsumedTick.PublicationState> publicationStates,
                    Pageable limit);

    /** Bounded current-hash outbox read for one exact world/chunk partition. The caller supplies limit plus one. */
    List<FinalCarrierConsumedTick>
            findAllByWorldIdAndChunkXAndChunkZAndPublicationStateInOrderByDurableOrderAscIdAsc(
                    long worldId, int chunkX, int chunkZ,
                    Collection<FinalCarrierConsumedTick.PublicationState> publicationStates,
                    Pageable limit);

    /** Bounded current-source outbox read for one world/lane in durable order. The caller supplies limit plus one. */
    List<FinalCarrierConsumedTick>
            findAllByWorldIdAndLaneAndSourceFingerprintAndPublicationStateInOrderByDurableOrderAscIdAsc(
                    long worldId, String lane, String sourceFingerprint,
                    Collection<FinalCarrierConsumedTick.PublicationState> publicationStates,
                    Pageable limit);

    /** Bounded current-source outbox read for one exact world/chunk/lane partition. The caller supplies limit plus one. */
    List<FinalCarrierConsumedTick>
            findAllByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprintAndPublicationStateInOrderByDurableOrderAscIdAsc(
                    long worldId, int chunkX, int chunkZ, String lane,
                    String sourceFingerprint,
                    Collection<FinalCarrierConsumedTick.PublicationState> publicationStates,
                    Pageable limit);

    /** Bounded current-source candidate-plus-one read before PublicationState filtering. */
    List<FinalCarrierConsumedTick>
            findAllByWorldIdAndLaneAndSourceFingerprintOrderByDurableOrderAscIdAsc(
                    long worldId, String lane, String sourceFingerprint, Pageable limit);

    /** Bounded exact current-source candidate-plus-one read before PublicationState filtering. */
    List<FinalCarrierConsumedTick>
            findAllByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprintOrderByDurableOrderAscIdAsc(
                    long worldId, int chunkX, int chunkZ, String lane,
                    String sourceFingerprint, Pageable limit);

    /** Bounded current-hash spatial outbox read. The caller supplies limit plus one. */
    List<FinalCarrierConsumedTick>
            findAllByWorldIdAndLaneAndXBetweenAndZBetweenAndPublicationStateInOrderByDurableOrderAscIdAsc(
                    long worldId, String lane, int minX, int maxX, int minZ, int maxZ,
                    Collection<FinalCarrierConsumedTick.PublicationState> publicationStates,
                    Pageable limit);

    Optional<FinalCarrierConsumedTick>
            findFirstByWorldIdAndLaneAndPublicationStateInOrderByDurableOrderDescIdDesc(
                    long worldId, String lane,
                    Collection<FinalCarrierConsumedTick.PublicationState> publicationStates);

    Optional<FinalCarrierConsumedTick>
            findFirstByWorldIdAndLaneAndSourceFingerprintAndPublicationStateInOrderByDurableOrderDescIdDesc(
                    long worldId, String lane, String sourceFingerprint,
                    Collection<FinalCarrierConsumedTick.PublicationState> publicationStates);

    /** Locks the exact key and current source fingerprint for concurrent settlement. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FinalCarrierConsumedTick>
            findLockedByWorldIdAndChunkXAndChunkZAndLaneAndXAndYAndZAndTypeKeyAndSourceFingerprint(
                    long worldId, int chunkX, int chunkZ, String lane,
                    int x, int y, int z, String typeKey, String sourceFingerprint);

    /** Legal UNACKNOWLEDGED -> OUTCOME_UNKNOWN transition for the exact current-hash row. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update FinalCarrierConsumedTick row
               set row.publicationState =
                   com.gameexpert.engine.persistence.finalcarrier.FinalCarrierConsumedTick.PublicationState.OUTCOME_UNKNOWN
             where row.worldId = :worldId
               and row.chunkX = :chunkX
               and row.chunkZ = :chunkZ
               and row.lane = :lane
               and row.x = :x
               and row.y = :y
               and row.z = :z
               and row.typeKey = :typeKey
               and row.disposition = :disposition
               and row.durableOrder = :durableOrder
               and row.sourceFingerprint = :sourceFingerprint
               and row.payloadFingerprint = :payloadFingerprint
               and row.publicationKey = :publicationKey
               and row.publicationDigest = :publicationDigest
               and row.publicationState =
                   com.gameexpert.engine.persistence.finalcarrier.FinalCarrierConsumedTick.PublicationState.UNACKNOWLEDGED
            """)
    int markOutcomeUnknown(
            @Param("worldId") long worldId,
            @Param("chunkX") int chunkX,
            @Param("chunkZ") int chunkZ,
            @Param("lane") String lane,
            @Param("x") int x,
            @Param("y") int y,
            @Param("z") int z,
            @Param("typeKey") String typeKey,
            @Param("disposition") String disposition,
            @Param("durableOrder") long durableOrder,
            @Param("sourceFingerprint") String sourceFingerprint,
            @Param("payloadFingerprint") String payloadFingerprint,
            @Param("publicationKey") String publicationKey,
            @Param("publicationDigest") String publicationDigest);

    /** Legal OUTCOME_UNKNOWN -> ACKNOWLEDGED transition for the exact current-hash row. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update FinalCarrierConsumedTick row
               set row.publicationState =
                   com.gameexpert.engine.persistence.finalcarrier.FinalCarrierConsumedTick.PublicationState.ACKNOWLEDGED
             where row.worldId = :worldId
               and row.chunkX = :chunkX
               and row.chunkZ = :chunkZ
               and row.lane = :lane
               and row.x = :x
               and row.y = :y
               and row.z = :z
               and row.typeKey = :typeKey
               and row.disposition = :disposition
               and row.durableOrder = :durableOrder
               and row.sourceFingerprint = :sourceFingerprint
               and row.payloadFingerprint = :payloadFingerprint
               and row.publicationKey = :publicationKey
               and row.publicationDigest = :publicationDigest
               and row.publicationState =
                   com.gameexpert.engine.persistence.finalcarrier.FinalCarrierConsumedTick.PublicationState.OUTCOME_UNKNOWN
            """)
    int acknowledgePublication(
            @Param("worldId") long worldId,
            @Param("chunkX") int chunkX,
            @Param("chunkZ") int chunkZ,
            @Param("lane") String lane,
            @Param("x") int x,
            @Param("y") int y,
            @Param("z") int z,
            @Param("typeKey") String typeKey,
            @Param("disposition") String disposition,
            @Param("durableOrder") long durableOrder,
            @Param("sourceFingerprint") String sourceFingerprint,
            @Param("payloadFingerprint") String payloadFingerprint,
            @Param("publicationKey") String publicationKey,
            @Param("publicationDigest") String publicationDigest);

    /** Legal UNACKNOWLEDGED/OUTCOME_UNKNOWN -> REJECTED transition for the exact current-hash row. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update FinalCarrierConsumedTick row
               set row.publicationState =
                   com.gameexpert.engine.persistence.finalcarrier.FinalCarrierConsumedTick.PublicationState.REJECTED
             where row.worldId = :worldId
               and row.chunkX = :chunkX
               and row.chunkZ = :chunkZ
               and row.lane = :lane
               and row.x = :x
               and row.y = :y
               and row.z = :z
               and row.typeKey = :typeKey
               and row.disposition = :disposition
               and row.durableOrder = :durableOrder
               and row.sourceFingerprint = :sourceFingerprint
               and row.payloadFingerprint = :payloadFingerprint
               and row.publicationKey = :publicationKey
               and row.publicationDigest = :publicationDigest
               and row.publicationState in (
                   com.gameexpert.engine.persistence.finalcarrier.FinalCarrierConsumedTick.PublicationState.UNACKNOWLEDGED,
                   com.gameexpert.engine.persistence.finalcarrier.FinalCarrierConsumedTick.PublicationState.OUTCOME_UNKNOWN)
            """)
    int rejectPublication(
            @Param("worldId") long worldId,
            @Param("chunkX") int chunkX,
            @Param("chunkZ") int chunkZ,
            @Param("lane") String lane,
            @Param("x") int x,
            @Param("y") int y,
            @Param("z") int z,
            @Param("typeKey") String typeKey,
            @Param("disposition") String disposition,
            @Param("durableOrder") long durableOrder,
            @Param("sourceFingerprint") String sourceFingerprint,
            @Param("payloadFingerprint") String payloadFingerprint,
            @Param("publicationKey") String publicationKey,
            @Param("publicationDigest") String publicationDigest);

    /**
     * Removes every canonical row this world owns. World deletion must clear the canonical
     * tables in the same transaction, otherwise the rows outlive the world and a re-created
     * world id inherits them.
     */
    @Modifying
    @Query("delete from FinalCarrierConsumedTick row where row.worldId = :worldId")
    int deleteByWorldId(@Param("worldId") long worldId);
}
