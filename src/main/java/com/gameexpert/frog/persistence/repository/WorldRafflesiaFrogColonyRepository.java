package com.gameexpert.frog.persistence.repository;

import com.gameexpert.frog.persistence.entity.WorldRafflesiaFrogColony;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface WorldRafflesiaFrogColonyRepository
        extends JpaRepository<WorldRafflesiaFrogColony, Long> {
    Optional<WorldRafflesiaFrogColony> findByWorldIdAndRafflesiaXAndRafflesiaYAndRafflesiaZ(
            Long worldId, int rafflesiaX, int rafflesiaY, int rafflesiaZ);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorldRafflesiaFrogColony> findLockedByWorldIdAndRafflesiaXAndRafflesiaYAndRafflesiaZ(
            Long worldId, int rafflesiaX, int rafflesiaY, int rafflesiaZ);

    List<WorldRafflesiaFrogColony> findAllByWorldIdOrderByIdAsc(Long worldId);

    void deleteAllByWorldId(Long worldId);
}
