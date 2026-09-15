package com.gameexpert.boat.service;

import com.gameexpert.boat.dto.BoatSnapshot;
import com.gameexpert.boat.entity.WorldBoat;
import com.gameexpert.boat.repository.WorldBoatRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 월드 단위 보트 집합의 트랜잭션 경계. 살아 있는 목록이 곧 저장된 목록이다. */
@Service
@RequiredArgsConstructor
public class BoatPersistenceService {
    private final WorldBoatRepository repository;

    /** 보트 id 오름차순 — 재입장 스냅샷 순서를 같은 세션 순서와 같게 만든다. */
    @Transactional(readOnly = true)
    public List<BoatSnapshot> loadWorld(Long worldId) {
        return repository.findAllByWorldId(worldId).stream()
                .map(WorldBoat::toSnapshot)
                .sorted(Comparator.comparingLong(BoatSnapshot::getBoatId))
                .toList();
    }

    /**
     * 살아 있는 보트 전체를 그대로 새긴다. 좌표가 그대로인 행은 다시 쓰지 않고, 목록에서 빠진
     * 보트(파괴됨)는 행을 지운다.
     */
    @Transactional
    public void replaceWorld(Long worldId, Collection<BoatSnapshot> snapshots) {
        List<WorldBoat> stored = repository.findAllByWorldId(worldId);
        Map<Long, WorldBoat> byId = new HashMap<>();
        for (WorldBoat row : stored) byId.put(row.getBoatId(), row);
        Set<Long> live = new HashSet<>();
        List<WorldBoat> changed = new ArrayList<>();
        for (BoatSnapshot snapshot : snapshots) {
            if (!live.add(snapshot.getBoatId())) {
                throw new IllegalArgumentException("duplicate boat id " + snapshot.getBoatId());
            }
            WorldBoat row = byId.get(snapshot.getBoatId());
            if (row == null) changed.add(new WorldBoat(worldId, snapshot));
            else if (!row.matches(snapshot)) {
                row.apply(snapshot);
                changed.add(row);
            }
        }
        if (!changed.isEmpty()) repository.saveAll(changed);
        List<WorldBoat> removed = stored.stream()
                .filter(row -> !live.contains(row.getBoatId())).toList();
        if (!removed.isEmpty()) repository.deleteAll(removed);
    }

    @Transactional
    public void deleteWorld(Long worldId) {
        repository.deleteAllByWorldId(worldId);
    }
}
