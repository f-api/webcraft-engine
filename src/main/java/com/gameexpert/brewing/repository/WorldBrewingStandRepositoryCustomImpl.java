package com.gameexpert.brewing.repository;

import java.util.Collection;
import java.util.List;

import com.gameexpert.brewing.entity.WorldBrewingStand;
import com.gameexpert.common.PositionTupleQuery;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class WorldBrewingStandRepositoryCustomImpl implements WorldBrewingStandRepositoryCustom {

    private static final String SELECT = "select stand from WorldBrewingStand stand";
    private final EntityManager entityManager;

    @Override
    public List<WorldBrewingStand> findDirtyByWorldId(Long worldId, Collection<int[]> positions) {
        return PositionTupleQuery.findByWorldPositions(
                entityManager, WorldBrewingStand.class, SELECT, "stand", worldId, positions);
    }
}
