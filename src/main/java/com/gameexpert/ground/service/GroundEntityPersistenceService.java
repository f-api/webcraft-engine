package com.gameexpert.ground.service;

import com.gameexpert.common.NotFoundException;
import com.gameexpert.ground.dto.GroundItemSnapshot;
import com.gameexpert.ground.dto.GroundXpOrbSnapshot;
import com.gameexpert.ground.entity.WorldGroundItem;
import com.gameexpert.ground.entity.WorldGroundXpOrb;
import com.gameexpert.ground.entity.WorldGroundRevision;
import com.gameexpert.ground.repository.WorldGroundItemRepository;
import com.gameexpert.ground.repository.WorldGroundRevisionRepository;
import com.gameexpert.ground.repository.WorldGroundXpOrbRepository;
import com.gameexpert.api.persistence.WorldStore;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/** One transactional checkpoint for both kinds of live ground entity. */
@Service
public class GroundEntityPersistenceService {
    private final WorldGroundItemRepository itemRepository;
    private final WorldGroundXpOrbRepository xpRepository;
    private final WorldGroundRevisionRepository revisionRepository;
    private final WorldStore worldRepository;

    public GroundEntityPersistenceService(WorldGroundItemRepository itemRepository,
            WorldGroundXpOrbRepository xpRepository,
            WorldGroundRevisionRepository revisionRepository, WorldStore worldRepository) {
        this.itemRepository = itemRepository;
        this.xpRepository = xpRepository;
        this.revisionRepository = revisionRepository;
        this.worldRepository = worldRepository;
    }

    @Transactional(readOnly = true)
    public GroundEntitySnapshot loadWorld(Long worldId) {
        requireValidWorldId(worldId);
        List<GroundItemSnapshot> items = itemRepository.findAllByWorldId(worldId).stream()
                .map(WorldGroundItem::toSnapshot)
                .sorted(Comparator.comparingLong(GroundItemSnapshot::entityId)).toList();
        List<GroundXpOrbSnapshot> xpOrbs = xpRepository.findAllByWorldId(worldId).stream()
                .map(WorldGroundXpOrb::toSnapshot)
                .sorted(Comparator.comparingLong(GroundXpOrbSnapshot::entityId)).toList();
        long revision = revisionRepository.findById(worldId)
                .map(WorldGroundRevision::getGroundRevision).orElse(0L);
        return new GroundEntitySnapshot(items, xpOrbs, revision);
    }

    /** Optimistic whole-world checkpoint; stale snapshots never erase a newer settlement. */
    @Transactional
    public GroundMutationOutcome replaceWorld(Long worldId, long expectedGroundRevision,
            long committedGroundRevision, Collection<GroundItemSnapshot> itemSnapshots,
            Collection<GroundXpOrbSnapshot> xpSnapshots) {
        requireValidWorldId(worldId);
        long successor = WorldGroundRevision.requireSuccessor(expectedGroundRevision);
        if (committedGroundRevision != successor) {
            throw new IllegalArgumentException("ground revision must advance by one");
        }
        GroundEntitySnapshot checkpoint = new GroundEntitySnapshot(
                copyItems(itemSnapshots), copyXpOrbs(xpSnapshots));
        WorldGroundRevision revision = lockRevision(worldId);
        if (revision.getGroundRevision() != expectedGroundRevision) {
            return GroundMutationOutcome.STALE;
        }
        List<WorldGroundItem> storedItems = itemRepository.findAllByWorldId(worldId);
        List<WorldGroundXpOrb> storedXpOrbs = xpRepository.findAllByWorldId(worldId);
        // Hydrate and validate the complete persisted union before applying or deleting any row.
        new GroundEntitySnapshot(
                storedItems.stream().map(WorldGroundItem::toSnapshot).toList(),
                storedXpOrbs.stream().map(WorldGroundXpOrb::toSnapshot).toList(),
                revision.getGroundRevision());
        if (!revision.advance(expectedGroundRevision, committedGroundRevision)) {
            return GroundMutationOutcome.STALE;
        }
        replaceItems(worldId, checkpoint.items(), storedItems);
        replaceXpOrbs(worldId, checkpoint.xpOrbs(), storedXpOrbs);
        revisionRepository.saveAndFlush(revision);
        return GroundMutationOutcome.COMMITTED;
    }

    /** 외부 원자 정산 트랜잭션에 안정 ID 지면 아이템 하나를 삽입한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public long insertStableItemJoiningTransaction(Long worldId, GroundItemSnapshot snapshot) {
        requireValidWorldId(worldId);
        if (snapshot == null) throw new IllegalArgumentException("ground item snapshot is required");
        WorldGroundRevision revision = lockRevision(worldId);
        if (xpRepository.findByWorldIdAndEntityId(worldId, snapshot.entityId()).isPresent()) {
            throw new IllegalStateException("ground entity identity collision");
        }
        WorldGroundItem existing = itemRepository
                .findByWorldIdAndEntityId(worldId, snapshot.entityId()).orElse(null);
        if (existing != null) {
            if (!existing.matches(snapshot)) {
                throw new IllegalStateException("ground item identity collision");
            }
            return revision.getGroundRevision();
        }
        long committedRevision = WorldGroundRevision.requireSuccessor(
                revision.getGroundRevision());
        if (!revision.advance(revision.getGroundRevision(), committedRevision)) {
            throw new IllegalStateException("ground revision changed while locked");
        }
        itemRepository.save(new WorldGroundItem(worldId, snapshot));
        revisionRepository.saveAndFlush(revision);
        return committedRevision;
    }

    private static void requireValidWorldId(Long worldId) {
        if (worldId == null || worldId <= 0 || worldId == Long.MAX_VALUE) {
            throw new IllegalArgumentException("world id must be positive");
        }
    }

    private static void validateUnion(List<GroundItemSnapshot> items,
            List<GroundXpOrbSnapshot> xpOrbs) {
        Set<Long> itemIds = new HashSet<>();
        for (GroundItemSnapshot snapshot : items) {
            if (!itemIds.add(snapshot.entityId())) {
                throw new IllegalArgumentException("duplicate ground item id");
            }
        }
        Set<Long> xpIds = new HashSet<>();
        for (GroundXpOrbSnapshot snapshot : xpOrbs) {
            if (!xpIds.add(snapshot.entityId())) {
                throw new IllegalArgumentException("duplicate XP orb id");
            }
        }
        itemIds.retainAll(xpIds);
        if (!itemIds.isEmpty()) {
            throw new IllegalArgumentException("ground entity identity collision");
        }
    }

    private static List<GroundItemSnapshot> copyItems(
            Collection<GroundItemSnapshot> snapshots) {
        if (snapshots == null) {
            throw new IllegalArgumentException("ground item snapshots are required");
        }
        return List.copyOf(snapshots);
    }

    private static List<GroundXpOrbSnapshot> copyXpOrbs(
            Collection<GroundXpOrbSnapshot> snapshots) {
        if (snapshots == null) {
            throw new IllegalArgumentException("ground XP orb snapshots are required");
        }
        return List.copyOf(snapshots);
    }

    private void replaceItems(Long worldId, Collection<GroundItemSnapshot> snapshots,
            List<WorldGroundItem> stored) {
        Map<Long, WorldGroundItem> byId = new HashMap<>();
        for (WorldGroundItem row : stored) byId.put(row.getEntityId(), row);
        Set<Long> live = new HashSet<>();
        List<WorldGroundItem> changed = new ArrayList<>();
        for (GroundItemSnapshot snapshot : snapshots) {
            if (!live.add(snapshot.entityId())) throw new IllegalArgumentException("duplicate ground item id");
            WorldGroundItem row = byId.get(snapshot.entityId());
            if (row == null) changed.add(new WorldGroundItem(worldId, snapshot));
            else if (!row.matches(snapshot)) {
                row.apply(snapshot);
                changed.add(row);
            }
        }
        if (!changed.isEmpty()) itemRepository.saveAll(changed);
        List<WorldGroundItem> removed = stored.stream().filter(row -> !live.contains(row.getEntityId())).toList();
        if (!removed.isEmpty()) itemRepository.deleteAll(removed);
    }

    private void replaceXpOrbs(Long worldId, Collection<GroundXpOrbSnapshot> snapshots,
            List<WorldGroundXpOrb> stored) {
        Map<Long, WorldGroundXpOrb> byId = new HashMap<>();
        for (WorldGroundXpOrb row : stored) byId.put(row.getEntityId(), row);
        Set<Long> live = new HashSet<>();
        List<WorldGroundXpOrb> changed = new ArrayList<>();
        for (GroundXpOrbSnapshot snapshot : snapshots) {
            if (!live.add(snapshot.entityId())) throw new IllegalArgumentException("duplicate XP orb id");
            WorldGroundXpOrb row = byId.get(snapshot.entityId());
            if (row == null) changed.add(new WorldGroundXpOrb(worldId, snapshot));
            else if (!row.matches(snapshot)) {
                row.apply(snapshot);
                changed.add(row);
            }
        }
        if (!changed.isEmpty()) xpRepository.saveAll(changed);
        List<WorldGroundXpOrb> removed = stored.stream().filter(row -> !live.contains(row.getEntityId())).toList();
        if (!removed.isEmpty()) xpRepository.deleteAll(removed);
    }

    private WorldGroundRevision lockRevision(Long worldId) {
        WorldGroundRevision revision = revisionRepository.findLockedByWorldId(worldId).orElse(null);
        if (revision != null) return revision;
        worldRepository.findByIdForShare(worldId)
                .orElseThrow(() -> new NotFoundException("WORLD_NOT_FOUND"));
        revision = revisionRepository.findLockedByWorldId(worldId).orElse(null);
        return revision != null ? revision : new WorldGroundRevision(worldId);
    }

    public record GroundEntitySnapshot(List<GroundItemSnapshot> items,
            List<GroundXpOrbSnapshot> xpOrbs, long groundRevision) {
        public GroundEntitySnapshot(
                List<GroundItemSnapshot> items, List<GroundXpOrbSnapshot> xpOrbs) {
            this(items, xpOrbs, 0);
        }

        public GroundEntitySnapshot {
            if (items == null || xpOrbs == null) {
                throw new IllegalArgumentException("complete ground snapshot is required");
            }
            items = List.copyOf(items);
            xpOrbs = List.copyOf(xpOrbs);
            if (groundRevision < 0 || groundRevision == Long.MAX_VALUE) {
                throw new IllegalArgumentException("ground revision is invalid");
            }
            validateUnion(items, xpOrbs);
        }
    }
}
