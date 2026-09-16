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

public interface FinalCarrierLaneMutationRepository
        extends JpaRepository<FinalCarrierLaneMutation, Long> {
    Optional<FinalCarrierLaneMutation> findByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprint(
            long worldId, int chunkX, int chunkZ, String lane, String sourceFingerprint);

    /** Locks the exact current-source mutation during settlement. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FinalCarrierLaneMutation>
            findLockedByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprint(
                    long worldId, int chunkX, int chunkZ, String lane,
                    String sourceFingerprint);

    /** Bounded world/lane history; id is the schema's append-only durable order. */
    List<FinalCarrierLaneMutation> findAllByWorldIdAndLaneOrderById(
            long worldId, String lane, Pageable limit);

    /** Bounded world/chunk/lane history; id is the schema's append-only durable order. */
    List<FinalCarrierLaneMutation> findAllByWorldIdAndChunkXAndChunkZAndLaneOrderById(
            long worldId, int chunkX, int chunkZ, String lane, Pageable limit);

    /** Bounded current-source world/lane history. The caller supplies limit plus one. */
    List<FinalCarrierLaneMutation> findAllByWorldIdAndLaneAndSourceFingerprintOrderById(
            long worldId, String lane, String sourceFingerprint, Pageable limit);

    /** Bounded world history; id is the schema's append-only durable order. The caller supplies limit plus one. */
    List<FinalCarrierLaneMutation> findAllByWorldIdOrderById(
            long worldId, Pageable limit);

    /** Bounded world/chunk history; id is the schema's append-only durable order. The caller supplies limit plus one. */
    List<FinalCarrierLaneMutation> findAllByWorldIdAndChunkXAndChunkZOrderById(
            long worldId, int chunkX, int chunkZ, Pageable limit);

    /** Stable ID-cursor history; callers start at 0 and advance to the last returned ID. */
    List<FinalCarrierLaneMutation> findAllByWorldIdAndIdGreaterThanOrderById(
            long worldId, long afterId, Pageable pageable);

    List<FinalCarrierLaneMutation>
            findAllByWorldIdAndChunkXAndChunkZAndIdGreaterThanOrderById(
                    long worldId, int chunkX, int chunkZ, long afterId, Pageable pageable);

    List<FinalCarrierLaneMutation> findAllByWorldIdAndLaneAndIdGreaterThanOrderById(
            long worldId, String lane, long afterId, Pageable pageable);

    List<FinalCarrierLaneMutation>
            findAllByWorldIdAndChunkXAndChunkZAndLaneAndIdGreaterThanOrderById(
                    long worldId, int chunkX, int chunkZ, String lane,
                    long afterId, Pageable pageable);

    /** Bounded current-source world/chunk/lane history. The caller supplies limit plus one. */
    List<FinalCarrierLaneMutation>
            findAllByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprintOrderById(
                    long worldId, int chunkX, int chunkZ, String lane,
                    String sourceFingerprint, Pageable limit);

    /** Bounded world/lane history restricted to explicit lifecycle states. The caller supplies limit plus one. */
    List<FinalCarrierLaneMutation>
            findAllByWorldIdAndLaneAndActivationStatusInOrderById(
                    long worldId, String lane,
                    Collection<FinalCarrierLaneMutation.ActivationStatus> activationStatuses,
                    Pageable limit);

    /** Bounded world/chunk/lane history restricted to explicit lifecycle states. The caller supplies limit plus one. */
    List<FinalCarrierLaneMutation>
            findAllByWorldIdAndChunkXAndChunkZAndLaneAndActivationStatusInOrderById(
                    long worldId, int chunkX, int chunkZ, String lane,
                    Collection<FinalCarrierLaneMutation.ActivationStatus> activationStatuses,
                    Pageable limit);

    @Query("select row from FinalCarrierLaneMutation row where row.worldId = :worldId "
            + "and row.chunkX = :chunkX and row.chunkZ = :chunkZ and row.lane = 'ENTITIES' "
            + "and row.activationStatus = :status "
            + "and row.installationIdentity = :identity order by row.id")
    List<FinalCarrierLaneMutation> findInstalledEntityMutation(@Param("worldId") long worldId,
            @Param("chunkX") int chunkX, @Param("chunkZ") int chunkZ,
            @Param("identity") String identity,
            @Param("status") FinalCarrierLaneMutation.ActivationStatus status, Pageable pageable);

    /**
     * Removes every canonical row this world owns. World deletion must clear the canonical
     * tables in the same transaction, otherwise the rows outlive the world and a re-created
     * world id inherits them.
     */
    @Modifying
    @Query("delete from FinalCarrierLaneMutation row where row.worldId = :worldId")
    int deleteByWorldId(@Param("worldId") long worldId);
}
