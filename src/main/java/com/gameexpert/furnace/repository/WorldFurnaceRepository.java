package com.gameexpert.furnace.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import com.gameexpert.furnace.entity.WorldFurnace;

/** 좌표별 화로 블록 엔티티 저장소입니다. */
public interface WorldFurnaceRepository
        extends JpaRepository<WorldFurnace, Long>, WorldFurnaceRepositoryCustom {

    List<WorldFurnace> findAllByWorldId(Long worldId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorldFurnace> findLockedByWorldIdAndPosXAndPosYAndPosZ(
            Long worldId, int posX, int posY, int posZ);

    void deleteAllByWorldId(Long worldId);
}
