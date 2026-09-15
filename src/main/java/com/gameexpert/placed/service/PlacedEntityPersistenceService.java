package com.gameexpert.placed.service;

import com.gameexpert.placed.dto.PlacedEntitySnapshot;
import com.gameexpert.placed.entity.WorldPlacedEntity;
import com.gameexpert.placed.repository.WorldPlacedEntityRepository;
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

/**
 * [CONTAINER-MENUS] The transaction boundary of a world's placed armor stands and minecarts. The
 * live set is the stored set, exactly like {@code BoatPersistenceService}: unchanged rows are not
 * rewritten and rows missing from the live set (broken, exploded) are deleted.
 */
@Service
@RequiredArgsConstructor
public class PlacedEntityPersistenceService {
    private final WorldPlacedEntityRepository repository;
    private final com.gameexpert.mob.service.MobPersistenceService mobs;

    @Transactional(readOnly = true)
    public List<PlacedEntitySnapshot> loadWorld(Long worldId) {
        return repository.findAllByWorldId(worldId).stream()
                .map(WorldPlacedEntity::toSnapshot)
                .sorted(Comparator.comparingLong(PlacedEntitySnapshot::entityId))
                .toList();
    }

    @Transactional
    public void replaceWorld(Long worldId, Collection<PlacedEntitySnapshot> snapshots) {
        List<WorldPlacedEntity> stored = repository.findAllByWorldId(worldId);
        Map<Long, WorldPlacedEntity> byId = new HashMap<>();
        for (WorldPlacedEntity row : stored) byId.put(row.getEntityId(), row);
        Set<Long> live = new HashSet<>();
        Set<Long> passengers = new HashSet<>();
        List<WorldPlacedEntity> changed = new ArrayList<>();
        for (PlacedEntitySnapshot snapshot : snapshots) {
            if (snapshot.mobPassengerId() != 0 && !passengers.add(snapshot.mobPassengerId())) throw new IllegalArgumentException("duplicate minecart passenger");
            if (!live.add(snapshot.entityId())) {
                throw new IllegalArgumentException("duplicate placed entity id " + snapshot.entityId());
            }
            WorldPlacedEntity row = byId.get(snapshot.entityId());
            if (row == null) {
                changed.add(new WorldPlacedEntity(worldId, snapshot));
            } else if (!row.matches(snapshot)) {
                row.apply(snapshot);
                changed.add(row);
            }
        }
        if (!changed.isEmpty()) repository.saveAll(changed);
        List<WorldPlacedEntity> removed = stored.stream()
                .filter(row -> !live.contains(row.getEntityId())).toList();
        if (!removed.isEmpty()) repository.deleteAll(removed);
    }

    @Transactional
    public void replaceWorldWithPassengers(Long worldId, Collection<PlacedEntitySnapshot> snapshots,
            Collection<com.gameexpert.mob.dto.MobPersistenceSnapshot> passengers) {
        for (var passenger : passengers) mobs.upsertPlacedVehiclePassengerJoiningTransaction(worldId, passenger);
        replaceWorld(worldId, snapshots);
    }

    @Transactional
    public void deleteWorld(Long worldId) {
        repository.deleteAll(repository.findAllByWorldId(worldId));
    }
}
