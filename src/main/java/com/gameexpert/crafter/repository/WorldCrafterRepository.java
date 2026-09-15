package com.gameexpert.crafter.repository;

import com.gameexpert.crafter.entity.WorldCrafter;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldCrafterRepository extends JpaRepository<WorldCrafter, Long> {
    List<WorldCrafter> findAllByWorldId(Long worldId);

    Optional<WorldCrafter> findByWorldIdAndPosXAndPosYAndPosZ(
            Long worldId, int posX, int posY, int posZ);

    @Modifying
    @Query("DELETE FROM WorldCrafter c WHERE c.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
