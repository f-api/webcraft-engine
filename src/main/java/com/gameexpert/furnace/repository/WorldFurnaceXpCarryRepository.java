package com.gameexpert.furnace.repository;

import com.gameexpert.furnace.entity.WorldFurnaceXpCarry;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldFurnaceXpCarryRepository
        extends JpaRepository<WorldFurnaceXpCarry, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from WorldFurnaceXpCarry c where c.worldId = :worldId")
    Optional<WorldFurnaceXpCarry> findLockedByWorldId(@Param("worldId") Long worldId);
}
