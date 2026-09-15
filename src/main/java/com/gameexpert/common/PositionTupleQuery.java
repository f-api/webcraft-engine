package com.gameexpert.common;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 월드 좌표 튜플 목록으로만 엔티티를 조회하는 공용 JPA 질의 보조입니다.
 *
 * <p>X/Y/Z 를 각각 {@code in} 으로 묶으면 후보 행이 좌표 집합의 교차곱까지 늘어나 요청하지 않은
 * 좌표의 행과 그 딸린 컬렉션까지 읽어옵니다. 여기서는 {@code (posX, posY, posZ) in ((..),(..))}
 * 형태의 튜플 조건을 만들어 요청한 좌표만 읽습니다.
 */
public final class PositionTupleQuery {

    /**
     * 한 질의에 담을 좌표 튜플 수 상한 — 바인딩 파라미터 수를 제한합니다.
     * dirty 조회 계약은 단일 select 유지이므로(테스트 최대 256좌표 = 768파라미터)
     * 실사용 dirty 집합이 한 배치에 들어오는 크기로 둔다.
     */
    private static final int BATCH = 1024;

    private PositionTupleQuery() {
    }

    /**
     * {@code selectClause} 뒤에 월드/좌표 튜플 조건을 붙여 실행합니다.
     *
     * @param selectClause {@code from} 절까지 포함한 select 문장(where 절 없음)
     * @param alias        select 절에서 대상 엔티티에 붙인 별칭
     * @param positions    {@code {x, y, z}} 3원소 배열 목록
     */
    public static <T> List<T> findByWorldPositions(EntityManager entityManager, Class<T> type,
            String selectClause, String alias, Long worldId, Collection<int[]> positions) {
        List<int[]> requested = new ArrayList<>(positions);
        List<T> found = new ArrayList<>();
        for (int start = 0; start < requested.size(); start += BATCH) {
            List<int[]> batch = requested.subList(start, Math.min(start + BATCH, requested.size()));
            StringBuilder hql = new StringBuilder(selectClause)
                    .append(" where ").append(alias).append(".worldId = :worldId and (")
                    .append(alias).append(".posX, ")
                    .append(alias).append(".posY, ")
                    .append(alias).append(".posZ) in (");
            for (int index = 0; index < batch.size(); index++) {
                if (index > 0) hql.append(", ");
                hql.append("(:x").append(index)
                        .append(", :y").append(index)
                        .append(", :z").append(index).append(')');
            }
            hql.append(')');
            TypedQuery<T> query = entityManager.createQuery(hql.toString(), type)
                    .setParameter("worldId", worldId);
            for (int index = 0; index < batch.size(); index++) {
                int[] position = batch.get(index);
                query.setParameter("x" + index, position[0]);
                query.setParameter("y" + index, position[1]);
                query.setParameter("z" + index, position[2]);
            }
            found.addAll(query.getResultList());
        }
        return found;
    }
}
