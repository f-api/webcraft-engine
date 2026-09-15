package com.gameexpert.furnace.repository;

import com.gameexpert.common.PositionTupleQuery;
import com.gameexpert.furnace.entity.WorldFurnace;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** 좌표 튜플 조건으로 요청한 화로만 읽는 구현입니다. */
@RequiredArgsConstructor
class WorldFurnaceRepositoryCustomImpl implements WorldFurnaceRepositoryCustom {

    private static final String SELECT = "select furnace from WorldFurnace furnace";

    private final EntityManager entityManager;

    @Override
    public List<WorldFurnace> findDirtyByWorldId(Long worldId, Collection<int[]> positions) {
        return PositionTupleQuery.findByWorldPositions(
                entityManager, WorldFurnace.class, SELECT, "furnace", worldId, positions);
    }
}
