package com.gameexpert.engine;

import java.util.Objects;
import java.util.function.Supplier;

import com.gameexpert.world.service.WorldSpawnPersistenceService;

/**
 * 정본 스폰 좌표를 "저장값 우선, 없으면 탐색 후 새김" 규칙으로 정합니다.
 *
 * <p>{@link WorldSpawn#find} 는 시드만 입력으로 받는 순수 함수라 저장값과 재계산 결과가 항상
 * 같습니다. 그래서 저장값을 믿어도 결과는 바뀌지 않고, 저장 월드에 콜드로 접속할 때 수백 청크를
 * 동기 생산하던 비용만 사라집니다.
 *
 * <p>규칙을 {@link WorldEngineManager} 밖의 이 자리에 두는 이유는 하나입니다 — 매니저 전체를
 * 조립하지 않고도 저장·재사용·폴백 세 갈래를 그대로 검증할 수 있어야 하기 때문입니다.
 */
final class CanonicalSpawnResolution {

    private CanonicalSpawnResolution() {
    }

    /**
     * @param persistence 영속화 협력자. {@code null} 이면(직접 조립한 테스트 매니저) 예전처럼
     *                    매번 탐색만 합니다.
     * @param search      저장값이 없을 때만 호출되는 정본 스폰 탐색
     */
    static int[] resolve(Long worldId, WorldSpawnPersistenceService persistence,
            Supplier<int[]> search) {
        Objects.requireNonNull(search, "canonical spawn search");
        if (persistence != null) {
            int[] stored = persistence.load(worldId);
            if (stored != null) return stored;
        }
        int[] found = Objects.requireNonNull(search.get(), "canonical spawn search result");
        if (persistence != null) persistence.save(worldId, found);
        return found;
    }
}
