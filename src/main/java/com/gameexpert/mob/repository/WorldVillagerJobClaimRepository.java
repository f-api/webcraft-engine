package com.gameexpert.mob.repository;

import com.gameexpert.mob.entity.WorldVillagerJobClaim;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldVillagerJobClaimRepository
        extends JpaRepository<WorldVillagerJobClaim, Long> {
    List<WorldVillagerJobClaim> findAllByWorldId(Long worldId);

    Optional<WorldVillagerJobClaim> findByWorldIdAndMobId(Long worldId, long mobId);

    @Modifying
    @Query("delete from WorldVillagerJobClaim claim where claim.worldId = :worldId"
            + " and claim.mobId in :mobIds")
    void deleteByWorldIdAndMobIds(@Param("worldId") Long worldId,
            @Param("mobIds") Iterable<Long> mobIds);

    @Modifying
    @Query("delete from WorldVillagerJobClaim claim where claim.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
