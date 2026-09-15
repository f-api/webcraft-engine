package com.gameexpert.dispenser.repository;

import com.gameexpert.dispenser.entity.WorldDispenser;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldDispenserRepository extends JpaRepository<WorldDispenser, Long> {
    List<WorldDispenser> findAllByWorldId(Long worldId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct dispenser from WorldDispenser dispenser left join fetch dispenser.items "
            + "where dispenser.worldId = :worldId and dispenser.posX = :x "
            + "and dispenser.posY = :y and dispenser.posZ = :z")
    Optional<WorldDispenser> findLockedByWorldIdAndPosXAndPosYAndPosZ(
            @Param("worldId") Long worldId, @Param("x") int posX,
            @Param("y") int posY, @Param("z") int posZ);

    /** Locks a row incarnation when the coordinate lookup is absent or points elsewhere. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct dispenser from WorldDispenser dispenser left join fetch dispenser.items "
            + "where dispenser.id = :id")
    Optional<WorldDispenser> findLockedById(@Param("id") Long id);

    void deleteAllByWorldId(Long worldId);
}
