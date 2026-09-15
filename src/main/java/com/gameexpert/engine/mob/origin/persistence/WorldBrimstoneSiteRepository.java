package com.gameexpert.engine.mob.origin.persistence;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldBrimstoneSiteRepository extends JpaRepository<WorldBrimstoneSite, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select site from WorldBrimstoneSite site where site.worldId=:worldId and site.siteKey=:siteKey")
    Optional<WorldBrimstoneSite> findLocked(@Param("worldId") Long worldId,
            @Param("siteKey") long siteKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select site from WorldBrimstoneSite site where site.worldId=:worldId and site.activeMobId=:mobId")
    Optional<WorldBrimstoneSite> findLockedByMob(@Param("worldId") Long worldId,
            @Param("mobId") long mobId);

    List<WorldBrimstoneSite> findAllByWorldIdOrderBySiteKeyAsc(Long worldId);

    @Modifying
    @Query("delete from WorldBrimstoneSite site where site.worldId=:worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
