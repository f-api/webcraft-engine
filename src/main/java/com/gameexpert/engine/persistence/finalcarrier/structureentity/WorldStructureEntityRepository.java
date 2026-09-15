package com.gameexpert.engine.persistence.finalcarrier.structureentity;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldStructureEntityRepository
        extends JpaRepository<WorldStructureEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<WorldStructureEntity> findAllByLaneInstallationIdentityOrderByEncounterOrdinal(
            String laneInstallationIdentity);

    /** Stable read-only evidence projection; runtime recovery uses the locked variant above. */
    @Query("select row from WorldStructureEntity row "
            + "where row.laneInstallationIdentity = :identity order by row.encounterOrdinal")
    List<WorldStructureEntity> findEvidenceByLaneInstallationIdentityOrderByEncounterOrdinal(
            @Param("identity") String laneInstallationIdentity);

    /** Enumerates the complete chunk residue before any current-installation projection. */
    @Query("select row from WorldStructureEntity row where row.worldId = :worldId "
            + "and row.chunkX = :chunkX and row.chunkZ = :chunkZ "
            + "order by row.encounterOrdinal, row.id")
    List<WorldStructureEntity> findEvidenceByWorldIdAndChunkOrderByEncounterOrdinal(
            @Param("worldId") long worldId, @Param("chunkX") int chunkX,
            @Param("chunkZ") int chunkZ, Pageable limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorldStructureEntity> findByWorldIdAndAuthoritativeEntityId(
            long worldId, long authoritativeEntityId);

    @Query("select max(row.authoritativeEntityId) from WorldStructureEntity row "
            + "where row.worldId = :worldId")
    Long findMaximumAuthoritativeEntityIdByWorldId(@Param("worldId") long worldId);

    /**
     * Removes every canonical row this world owns. World deletion must clear the canonical
     * tables in the same transaction, otherwise the rows outlive the world and a re-created
     * world id inherits them.
     */
    @Modifying
    @Query("delete from WorldStructureEntity row where row.worldId = :worldId")
    int deleteByWorldId(@Param("worldId") long worldId);
}
