package com.gameexpert.engine.persistence.finalcarrier.loot;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldCanonicalLootAssignmentRepository
        extends JpaRepository<WorldCanonicalLootAssignment, Long> {
    /**
     * Bounded complete assignments in stable spatial order. The evidence reader deliberately
     * receives managed entities rather than a partial projection so it can validate the whole
     * authenticated definition and, for resolved rows, decode the stored terminal result.
     * Callers pass the authenticated candidate count plus one as the pageable size so an extra
     * durable row remains an explicit overflow sentinel.
     */
    @Query("""
            select row
              from WorldCanonicalLootAssignment row
             where row.worldId = :worldId
               and row.chunkX between :minChunkX and :maxChunkX
               and row.chunkZ between :minChunkZ and :maxChunkZ
             order by row.chunkX asc, row.chunkZ asc, row.packed asc, row.id asc
            """)
    List<WorldCanonicalLootAssignment>
            findAllByWorldIdAndChunkXBetweenAndChunkZBetweenOrderByChunkXAscChunkZAscPackedAscIdAsc(
                    @Param("worldId") long worldId,
                    @Param("minChunkX") int minChunkX,
                    @Param("maxChunkX") int maxChunkX,
                    @Param("minChunkZ") int minChunkZ,
                    @Param("maxChunkZ") int maxChunkZ,
                    Pageable limit);

    /** Reads the complete entity, including the authenticated context LOB, in stable row order. */
    @Query("""
            select row
              from WorldCanonicalLootAssignment row
             where row.laneInstallationIdentity = :laneInstallationIdentity
             order by row.packed asc, row.id asc
            """)
    List<WorldCanonicalLootAssignment> findAllByLaneInstallationIdentityOrderByPacked(
            @Param("laneInstallationIdentity") String laneInstallationIdentity);
    Optional<WorldCanonicalLootAssignment> findByWorldIdAndPosXAndPosYAndPosZ(
            long worldId, int posX, int posY, int posZ);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorldCanonicalLootAssignment> findLockedByWorldIdAndPosXAndPosYAndPosZ(
            long worldId, int posX, int posY, int posZ);

    /**
     * Removes every canonical row this world owns. World deletion must clear the canonical
     * tables in the same transaction, otherwise the rows outlive the world and a re-created
     * world id inherits them.
     */
    @Modifying
    @Query("delete from WorldCanonicalLootAssignment row where row.worldId = :worldId")
    int deleteByWorldId(@Param("worldId") long worldId);
}
