package com.gameexpert.chest.repository;

import com.gameexpert.chest.entity.WorldChest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** 좌표 튜플 조건으로 요청한 상자만 읽는 구현입니다. */
@RequiredArgsConstructor
class WorldChestRepositoryCustomImpl implements WorldChestRepositoryCustom {

    private static final int BATCH = 1024;
    private static final String SELECT =
            "select distinct chest from WorldChest chest left join fetch chest.items";

    private final EntityManager entityManager;

    @Override
    public List<WorldChest> findDirtyByWorldId(Long worldId, Collection<int[]> positions) {
        if (worldId == null) throw new IllegalArgumentException("world ID is required");
        if (positions == null) throw new IllegalArgumentException("dirty positions are required");
        List<int[]> requested = new ArrayList<>(positions);
        List<WorldChest> found = new ArrayList<>();
        for (int start = 0; start < requested.size(); start += BATCH) {
            List<int[]> batch = requested.subList(start, Math.min(start + BATCH, requested.size()));
            StringBuilder hql = new StringBuilder(SELECT)
                    .append(" where chest.worldId = :worldId and (")
                    .append("chest.posX, chest.posY, chest.posZ) in (");
            for (int index = 0; index < batch.size(); index++) {
                if (index > 0) hql.append(", ");
                hql.append("(:x").append(index)
                        .append(", :y").append(index)
                        .append(", :z").append(index).append(')');
            }
            hql.append(')');
            TypedQuery<WorldChest> query = entityManager.createQuery(
                    hql.toString(), WorldChest.class)
                    .setParameter("worldId", worldId)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE);
            for (int index = 0; index < batch.size(); index++) {
                int[] position = batch.get(index);
                if (position == null || position.length != 3) {
                    throw new IllegalArgumentException("dirty position must contain x, y, z");
                }
                query.setParameter("x" + index, position[0]);
                query.setParameter("y" + index, position[1]);
                query.setParameter("z" + index, position[2]);
            }
            found.addAll(query.getResultList());
        }
        return found;
    }
}
