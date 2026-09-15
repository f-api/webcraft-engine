package com.gameexpert.campfire.repository;

import com.gameexpert.campfire.entity.WorldCampfire;
import com.gameexpert.common.PositionTupleQuery;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** 좌표 튜플 조건으로 요청한 모닥불만 읽는 구현입니다. */
@RequiredArgsConstructor
class WorldCampfireRepositoryCustomImpl implements WorldCampfireRepositoryCustom {

    private static final String SELECT = "select campfire from WorldCampfire campfire";

    private final EntityManager entityManager;

    @Override
    public List<WorldCampfire> findDirtyByWorldId(Long worldId, Collection<int[]> positions) {
        return PositionTupleQuery.findByWorldPositions(
                entityManager, WorldCampfire.class, SELECT, "campfire", worldId, positions);
    }
}
