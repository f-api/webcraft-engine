package com.gameexpert.mob.repository;

import com.gameexpert.mob.entity.WorldRaid;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 재시작 뒤 복원할 레이드 인스턴스 원장 저장소입니다. */
public interface WorldRaidRepository extends JpaRepository<WorldRaid, Long> {

    List<WorldRaid> findAllByWorldId(Long worldId);

    @Modifying
    @Query("delete from WorldRaid raid where raid.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
