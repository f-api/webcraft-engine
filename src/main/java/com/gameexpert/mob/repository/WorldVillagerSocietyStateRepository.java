package com.gameexpert.mob.repository;

import com.gameexpert.mob.entity.WorldVillagerSocietyState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldVillagerSocietyStateRepository
        extends JpaRepository<WorldVillagerSocietyState, Long> {
    List<WorldVillagerSocietyState> findAllByWorldId(Long worldId);

    Optional<WorldVillagerSocietyState> findByWorldIdAndVillagerId(Long worldId, long villagerId);

    @Modifying
    @Query("delete from WorldVillagerSocietyState state where state.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
