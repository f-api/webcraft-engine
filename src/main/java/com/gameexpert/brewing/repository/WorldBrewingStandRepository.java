package com.gameexpert.brewing.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.gameexpert.brewing.entity.WorldBrewingStand;

import jakarta.persistence.LockModeType;

public interface WorldBrewingStandRepository extends JpaRepository<WorldBrewingStand, Long>,
        WorldBrewingStandRepositoryCustom {

    List<WorldBrewingStand> findAllByWorldId(Long worldId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorldBrewingStand> findLockedByWorldIdAndPosXAndPosYAndPosZ(
            Long worldId, int posX, int posY, int posZ);

    void deleteAllByWorldId(Long worldId);
}
