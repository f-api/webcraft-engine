package com.gameexpert.engine.persistence.animal;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldCopperGolemStatueRepository
        extends JpaRepository<WorldCopperGolemStatue, Long> {
    Optional<WorldCopperGolemStatue> findByWorldIdAndXAndYAndZ(
            Long worldId, int x, int y, int z);

    @Modifying
    @Query("delete from WorldCopperGolemStatue statue where statue.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
