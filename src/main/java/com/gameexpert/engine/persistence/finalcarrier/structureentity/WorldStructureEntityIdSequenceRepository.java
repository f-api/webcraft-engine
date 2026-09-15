package com.gameexpert.engine.persistence.finalcarrier.structureentity;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldStructureEntityIdSequenceRepository
        extends JpaRepository<WorldStructureEntityIdSequence, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorldStructureEntityIdSequence> findLockedByWorldId(Long worldId);

    /**
     * Removes every canonical row this world owns. World deletion must clear the canonical
     * tables in the same transaction, otherwise the rows outlive the world and a re-created
     * world id inherits them.
     */
    @Modifying
    @Query("delete from WorldStructureEntityIdSequence row where row.worldId = :worldId")
    int deleteByWorldId(@Param("worldId") long worldId);
}
