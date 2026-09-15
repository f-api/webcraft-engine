package com.gameexpert.tnt.repository;

import com.gameexpert.tnt.entity.WorldPrimedTnt;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldPrimedTntRepository extends JpaRepository<WorldPrimedTnt, Long> {
    List<WorldPrimedTnt> findAllByWorldId(Long worldId);

    /** Returns zero for an empty world, or a negative sentinel for corrupt nonpositive IDs. */
    @Query("select case when min(tnt.tntId) <= 0 then -1L "
            + "else coalesce(max(tnt.tntId), 0L) end "
            + "from WorldPrimedTnt tnt where tnt.worldId = :worldId")
    long findValidatedHighWaterByWorldId(@Param("worldId") Long worldId);

    List<WorldPrimedTnt> findAllByWorldIdAndTntIdIn(Long worldId, Collection<Long> tntIds);

    /** Locks one exact durable TNT identity for an aggregate settlement. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select tnt from WorldPrimedTnt tnt where tnt.worldId = :worldId "
            + "and tnt.tntId = :tntId")
    Optional<WorldPrimedTnt> findLockedByWorldIdAndTntId(
            @Param("worldId") Long worldId, @Param("tntId") long tntId);

    @Modifying
    @Query("delete from WorldPrimedTnt tnt where tnt.worldId = :worldId and tnt.tntId in :tntIds")
    void deleteAllByWorldIdAndTntIdIn(@Param("worldId") Long worldId,
            @Param("tntIds") Collection<Long> tntIds);

    @Modifying
    @Query("delete from WorldPrimedTnt tnt where tnt.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
