package com.gameexpert.mob.repository;

import com.gameexpert.mob.entity.WorldVillagerTradeState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldVillagerTradeStateRepository
        extends JpaRepository<WorldVillagerTradeState, Long> {
    List<WorldVillagerTradeState> findAllByWorldId(Long worldId);

    Optional<WorldVillagerTradeState> findByWorldIdAndMobId(Long worldId, long mobId);

    @Modifying
    @Query("delete from WorldVillagerTradeState state where state.worldId = :worldId"
            + " and state.mobId in :mobIds")
    void deleteByWorldIdAndMobIds(@Param("worldId") Long worldId,
            @Param("mobIds") Iterable<Long> mobIds);

    @Modifying
    @Query("delete from WorldVillagerTradeState state where state.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
