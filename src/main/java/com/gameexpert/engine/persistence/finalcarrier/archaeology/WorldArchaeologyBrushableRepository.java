package com.gameexpert.engine.persistence.finalcarrier.archaeology;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldArchaeologyBrushableRepository
        extends JpaRepository<WorldArchaeologyBrushable, Long> {
    Optional<WorldArchaeologyBrushable> findByInstallationIdentity(String installationIdentity);
    Optional<WorldArchaeologyBrushable> findByWorldIdAndTargetXAndTargetYAndTargetZ(
            long worldId, int targetX, int targetY, int targetZ);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorldArchaeologyBrushable> findLockedByWorldIdAndTargetXAndTargetYAndTargetZ(
            long worldId, int targetX, int targetY, int targetZ);
    List<WorldArchaeologyBrushable> findAllByLaneInstallationIdentityOrderByPacked(
            String laneInstallationIdentity);

    /**
     * Removes every canonical row this world owns. World deletion must clear the canonical
     * tables in the same transaction, otherwise the rows outlive the world and a re-created
     * world id inherits them.
     */
    @Modifying
    @Query("delete from WorldArchaeologyBrushable row where row.worldId = :worldId")
    int deleteByWorldId(@Param("worldId") long worldId);
}
