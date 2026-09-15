package com.gameexpert.ground.repository;

import com.gameexpert.ground.entity.WorldGroundXpOrb;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldGroundXpOrbRepository extends JpaRepository<WorldGroundXpOrb, Long> {
    List<WorldGroundXpOrb> findAllByWorldId(Long worldId);
    Optional<WorldGroundXpOrb> findByWorldIdAndEntityId(Long worldId, long entityId);

    @Modifying
    @Query("delete from WorldGroundXpOrb orb where orb.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
