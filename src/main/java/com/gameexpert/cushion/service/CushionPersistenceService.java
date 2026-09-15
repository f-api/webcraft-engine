package com.gameexpert.cushion.service;

import com.gameexpert.cushion.dto.CushionSnapshot;
import com.gameexpert.cushion.entity.WorldCushion;
import com.gameexpert.cushion.repository.WorldCushionRepository;
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

/** Transactional per-world cushion entity set. */
@Service
@RequiredArgsConstructor
public class CushionPersistenceService {
    private final WorldCushionRepository repository;

    @Transactional(readOnly = true)
    public List<CushionSnapshot> loadWorld(Long worldId) {
        return repository.findAllByWorldId(worldId).stream()
                .map(WorldCushion::toSnapshot)
                .sorted(Comparator.comparingLong(CushionSnapshot::getCushionId))
                .toList();
    }

    @Transactional
    public void replaceWorld(Long worldId, Collection<CushionSnapshot> snapshots) {
        List<WorldCushion> stored = repository.findAllByWorldId(worldId);
        Map<Long, WorldCushion> byId = new HashMap<>();
        for (WorldCushion row : stored) byId.put(row.getCushionId(), row);
        Set<Long> live = new HashSet<>();
        List<WorldCushion> changed = new java.util.ArrayList<>();
        for (CushionSnapshot snapshot : snapshots) {
            if (!live.add(snapshot.getCushionId())) {
                throw new IllegalArgumentException("duplicate cushion id " + snapshot.getCushionId());
            }
            WorldCushion row = byId.get(snapshot.getCushionId());
            if (row == null) changed.add(new WorldCushion(worldId, snapshot));
            else {
                row.apply(snapshot);
                changed.add(row);
            }
        }
        if (!changed.isEmpty()) repository.saveAll(changed);
        List<WorldCushion> removed = stored.stream()
                .filter(row -> !live.contains(row.getCushionId())).toList();
        if (!removed.isEmpty()) repository.deleteAll(removed);
    }
}
