package com.gameexpert.mob.repository;

import com.gameexpert.mob.entity.WorldVillagerBedClaim;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldVillagerBedClaimRepository
        extends JpaRepository<WorldVillagerBedClaim, Long> {
    List<WorldVillagerBedClaim> findAllByWorldId(Long worldId);

    Optional<WorldVillagerBedClaim> findByWorldIdAndBedXAndBedYAndBedZ(
            Long worldId, int bedX, int bedY, int bedZ);

    @Modifying
    @Query("delete from WorldVillagerBedClaim claim where claim.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
