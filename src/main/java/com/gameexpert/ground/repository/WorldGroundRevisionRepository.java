package com.gameexpert.ground.repository;

import com.gameexpert.ground.entity.WorldGroundRevision;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldGroundRevisionRepository extends JpaRepository<WorldGroundRevision, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select revision from WorldGroundRevision revision where revision.worldId = :worldId")
    Optional<WorldGroundRevision> findLockedByWorldId(@Param("worldId") Long worldId);
}
