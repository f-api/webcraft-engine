package com.gameexpert.mob.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gameexpert.mob.entity.WorldMobMutation;

/** 몹 변형 저널 행 저장소입니다. */
public interface WorldMobMutationRepository extends JpaRepository<WorldMobMutation, Long> {

    List<WorldMobMutation> findAllByWorldIdOrderBySequenceAsc(Long worldId);

    Optional<WorldMobMutation> findByWorldIdAndEntryKey(Long worldId, String entryKey);

    List<WorldMobMutation> findAllByWorldIdAndEntryKeyIn(
            Long worldId, Collection<String> entryKeys);

    @Modifying
    @Query("update WorldMobMutation row set row.applied = true "
            + "where row.worldId = :worldId and row.entryKey in :entryKeys")
    int markAppliedAll(@Param("worldId") Long worldId,
            @Param("entryKeys") Collection<String> entryKeys);

    @Modifying
    @Query("update WorldMobMutation row set row.applied = true, row.superseded = true "
            + "where row.worldId = :worldId and row.entryKey in :entryKeys")
    int markSupersededAll(@Param("worldId") Long worldId,
            @Param("entryKeys") Collection<String> entryKeys);

    @Modifying
    @Query("delete from WorldMobMutation row "
            + "where row.worldId = :worldId and row.entryKey in :entryKeys")
    int deleteAllByWorldIdAndEntryKeyIn(@Param("worldId") Long worldId,
            @Param("entryKeys") Collection<String> entryKeys);

    @Modifying
    @Query("delete from WorldMobMutation row where row.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
