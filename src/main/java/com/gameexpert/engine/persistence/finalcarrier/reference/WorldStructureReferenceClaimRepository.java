package com.gameexpert.engine.persistence.finalcarrier.reference;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldStructureReferenceClaimRepository extends JpaRepository<WorldStructureReferenceClaim, Long> {
    Optional<WorldStructureReferenceClaim> findByWorldIdAndBaselineIdAndStructureIdAndOriginChunkXAndOriginChunkZ(
            long worldId, String baselineId, String structureId, int originChunkX, int originChunkZ);
    List<WorldStructureReferenceClaim> findAllByWorldIdAndBaselineId(long worldId, String baselineId);

    @Modifying
    @Query("delete from WorldStructureReferenceClaim row where row.worldId = :worldId")
    int deleteByWorldId(@Param("worldId") long worldId);
}
