package com.gameexpert.world.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameexpert.api.persistence.WorldAccess;
import com.gameexpert.api.persistence.WorldStore;

import lombok.RequiredArgsConstructor;

/**
 * 월드의 정본 스폰 좌표를 {@code worlds} 행에 읽고 쓰는 트랜잭션 경계입니다.
 *
 * <p>{@code WorldSpawn.find} 는 시드만 입력으로 받는 순수 함수라 매번 같은 좌표를 돌려줍니다.
 * 그런데 마른 땅이 원점에서 멀면 그 탐색이 청크를 동기 생산하며, 저장 월드에 콜드로 접속할 때마다
 * 그 비용을 다시 냅니다. 그래서 처음 구한 좌표를 한 번 새기고 이후 접속은 읽기만 합니다.
 *
 * <p>구형 월드는 컬럼이 비어 있으므로 마이그레이션이 필요 없습니다 — 첫 접속이 계산 후 채웁니다.
 */
@Service
@RequiredArgsConstructor
public class WorldSpawnPersistenceService {

    private final WorldStore worldRepository;

    /**
     * 저장된 정본 스폰 좌표를 돌려줍니다. 아직 없거나 월드 행이 사라졌으면 {@code null} 입니다.
     * 없음은 오류가 아니라 "계산해서 채워라"는 뜻이므로 예외를 던지지 않습니다.
     */
    @Transactional(readOnly = true)
    public int[] load(Long worldId) {
        if (worldId == null) return null;
        return worldRepository.findById(worldId).map(WorldAccess::canonicalSpawn).orElse(null);
    }

    /**
     * 계산된 정본 스폰 좌표를 새깁니다. 이미 새겨져 있으면 그대로 둡니다(값이 결정론적이라 같습니다).
     * 월드 행이 사라졌으면 조용히 넘어갑니다 — 삭제 중인 월드에 쓰기를 강요할 이유가 없습니다.
     */
    @Transactional
    public void save(Long worldId, int[] spawn) {
        Objects.requireNonNull(spawn, "canonical spawn");
        if (spawn.length != 3) {
            throw new IllegalArgumentException("canonical spawn must be {x, y, z}");
        }
        if (worldId == null) return;
        worldRepository.findById(worldId).ifPresent(
                world -> world.assignCanonicalSpawnIfAbsent(spawn[0], spawn[1], spawn[2]));
    }
}
