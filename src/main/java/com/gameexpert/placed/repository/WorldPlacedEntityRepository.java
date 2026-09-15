package com.gameexpert.placed.repository;

import com.gameexpert.placed.entity.WorldPlacedEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** [CONTAINER-MENUS] Stored player-placed armor stands and minecarts. */
public interface WorldPlacedEntityRepository extends JpaRepository<WorldPlacedEntity, Long> {

    /** Spring Data returns an empty list, never null, when a world has no rows. */
    List<WorldPlacedEntity> findAllByWorldId(Long worldId);
}
