package com.gameexpert.mob.repository;

import com.gameexpert.mob.entity.WorldStructureOccupantClaim;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldStructureOccupantClaimRepository
        extends JpaRepository<WorldStructureOccupantClaim, Long> {
    List<WorldStructureOccupantClaim> findAllByWorldId(Long worldId);

    boolean existsByWorldIdAndSiteKindAndCellXAndCellZ(
            Long worldId, String siteKind, int cellX, int cellZ);

    @Modifying
    @Query("delete from WorldStructureOccupantClaim claim where claim.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
