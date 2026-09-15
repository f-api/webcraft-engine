package com.gameexpert.campfire.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import com.gameexpert.campfire.entity.WorldCampfire;

/** 음식이 올라간 좌표별 모닥불 저장소입니다. */
public interface WorldCampfireRepository
        extends JpaRepository<WorldCampfire, Long>, WorldCampfireRepositoryCustom {

    List<WorldCampfire> findAllByWorldId(Long worldId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorldCampfire> findLockedByWorldIdAndPosXAndPosYAndPosZ(
            Long worldId, int posX, int posY, int posZ);

    void deleteAllByWorldId(Long worldId);
}
