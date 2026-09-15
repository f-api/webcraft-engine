package com.gameexpert.engine.persistence.finalcarrier.reference;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldLootReferenceSnapshotRepository extends JpaRepository<WorldLootReferenceSnapshot, Long> {
    Optional<WorldLootReferenceSnapshot> findByWorldIdAndBaselineIdAndSnapshotIdentity(
            long worldId, String baselineId, String snapshotIdentity);
    @Modifying
    @Query("delete from WorldLootReferenceSnapshot row where row.worldId = :worldId")
    int deleteByWorldId(@Param("worldId") long worldId);
}
