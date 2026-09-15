package com.gameexpert.engine.persistence.finalcarrier.bees;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldCanonicalBeeInstallationRepository
        extends JpaRepository<WorldCanonicalBeeInstallation, Long> {
    Optional<WorldCanonicalBeeInstallation>
            findByWorldIdAndChunkXAndChunkZAndInstallationIdentity(
                    long worldId, int chunkX, int chunkZ, String installationIdentity);

    List<WorldCanonicalBeeInstallation> findAllByWorldIdAndChunkXAndChunkZOrderById(
            long worldId, int chunkX, int chunkZ);

    List<WorldCanonicalBeeInstallation> findAllByWorldIdOrderById(long worldId);

    List<WorldCanonicalBeeInstallation> findAllByWorldIdAndIdGreaterThanOrderById(
            long worldId, long afterId, Pageable pageable);

    List<WorldCanonicalBeeInstallation>
            findAllByWorldIdAndChunkXAndChunkZAndIdGreaterThanOrderById(
                    long worldId, int chunkX, int chunkZ, long afterId, Pageable pageable);

    /**
     * Removes every canonical row this world owns. World deletion must clear the canonical
     * tables in the same transaction, otherwise the rows outlive the world and a re-created
     * world id inherits them.
     */
    @Modifying
    @Query("delete from WorldCanonicalBeeInstallation row where row.worldId = :worldId")
    int deleteByWorldId(@Param("worldId") long worldId);
}
