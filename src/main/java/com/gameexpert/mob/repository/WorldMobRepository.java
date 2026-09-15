package com.gameexpert.mob.repository;

import com.gameexpert.mob.entity.WorldMob;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.repository.query.Param;

/** 재시작 뒤 복원할 현재 몹을 담는 저장소입니다. */
public interface WorldMobRepository extends JpaRepository<WorldMob, Long> {

    /** Spring Data의 컬렉션 조회 계약에 따라 결과가 없으면 null이 아니라 빈 목록을 반환합니다. */
    List<WorldMob> findAllByWorldId(Long worldId);
    long countByWorldIdAndType(Long worldId, String type);

    /** 정체성 최고수위만 필요할 때 행 전체를 읽지 않습니다. 몹이 없으면 null입니다. */
    @Query("select max(mob.mobId) from WorldMob mob where mob.worldId = :worldId")
    Long findMaximumMobIdByWorldId(@Param("worldId") Long worldId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorldMob> findLockedByWorldIdAndMobId(Long worldId, long mobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mob from WorldMob mob where mob.worldId = :worldId and mob.mobId in :mobIds")
    List<WorldMob> findAllByWorldIdAndMobIds(@Param("worldId") Long worldId,
            @Param("mobIds") Collection<Long> mobIds);

    @Modifying
    @Query("delete from WorldMob mob where mob.worldId = :worldId and mob.mobId in :mobIds")
    void deleteByWorldIdAndMobIds(@Param("worldId") Long worldId,
            @Param("mobIds") Collection<Long> mobIds);

    @Modifying
    @Query("delete from WorldMob mob where mob.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
