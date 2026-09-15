package com.gameexpert.dragonfight.repository;

import com.gameexpert.dragonfight.entity.WorldDragonFight;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** [DRAGON] 월드별 드래곤전 상태 행(월드당 하나). */
public interface WorldDragonFightRepository extends JpaRepository<WorldDragonFight, Long> {
    @Query("SELECT f FROM WorldDragonFight f WHERE f.world.id = :worldId")
    Optional<WorldDragonFight> findByWorldId(@Param("worldId") Long worldId);

    @Modifying
    @Query("DELETE FROM WorldDragonFight f WHERE f.world.id = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
