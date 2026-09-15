package com.gameexpert.ground.repository;

import com.gameexpert.ground.entity.WorldGroundItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldGroundItemRepository extends JpaRepository<WorldGroundItem, Long> {
    List<WorldGroundItem> findAllByWorldId(Long worldId);
    Optional<WorldGroundItem> findByWorldIdAndEntityId(Long worldId, long entityId);

    @Modifying
    @Query("delete from WorldGroundItem item where item.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
