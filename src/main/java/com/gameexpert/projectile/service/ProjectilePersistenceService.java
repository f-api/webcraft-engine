package com.gameexpert.projectile.service;

import com.gameexpert.projectile.dto.ProjectilePersistenceSnapshot;
import com.gameexpert.projectile.entity.WorldProjectile;
import com.gameexpert.projectile.repository.WorldProjectileRepository;
import com.gameexpert.ground.dto.GroundItemSnapshot;
import com.gameexpert.ground.dto.GroundXpOrbSnapshot;
import com.gameexpert.ground.service.GroundEntityPersistenceService;
import com.gameexpert.ground.service.GroundMutationOutcome;
import com.gameexpert.state.service.PlayerWorldStateService;
import com.gameexpert.state.service.inventory.PlayerInventoryMutationSnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/** Transactional live-projectile ledger. */
@Service
public class ProjectilePersistenceService {
    private final WorldProjectileRepository repository;
    private final GroundEntityPersistenceService groundEntities;
    private final PlayerWorldStateService playerStates;

    public ProjectilePersistenceService(WorldProjectileRepository repository,
            GroundEntityPersistenceService groundEntities, PlayerWorldStateService playerStates) {
        this.repository = repository;
        this.groundEntities = groundEntities;
        this.playerStates = playerStates;
    }

    @Transactional(readOnly = true)
    public List<ProjectilePersistenceSnapshot> loadWorld(Long worldId) {
        return repository.findAllByWorldId(worldId).stream().map(WorldProjectile::toSnapshot)
                .sorted(Comparator.comparingLong(ProjectilePersistenceSnapshot::projectileId)).toList();
    }

    @Transactional
    public void replaceWorld(Long worldId, Collection<ProjectilePersistenceSnapshot> snapshots) {
        replaceProjectiles(worldId, snapshots);
    }

    /** Terminal removal and its recovery item share one commit, preventing trident loss/duplication. */
    @Transactional
    public GroundMutationOutcome replaceWorldWithGround(Long worldId,
            long expectedGroundRevision, long committedGroundRevision,
            Collection<ProjectilePersistenceSnapshot> snapshots,
            Collection<GroundItemSnapshot> items, Collection<GroundXpOrbSnapshot> xpOrbs) {
        GroundMutationOutcome outcome = groundEntities.replaceWorld(worldId,
                expectedGroundRevision, committedGroundRevision, items, xpOrbs);
        if (outcome == GroundMutationOutcome.STALE) return outcome;
        replaceProjectiles(worldId, snapshots);
        return outcome;
    }

    /** A consumed throwable/trident and the resulting live projectile become durable together. */
    @Transactional
    public GroundMutationOutcome replaceWorldWithPlayerAndGround(Long worldId,
            long expectedGroundRevision, long committedGroundRevision,
            long expectedPlayerRevision,
            Collection<ProjectilePersistenceSnapshot> snapshots,
            Collection<GroundItemSnapshot> items, Collection<GroundXpOrbSnapshot> xpOrbs,
            PlayerInventoryMutationSnapshot player) {
        if (!worldId.equals(player.worldId())) throw new IllegalArgumentException("world mismatch");
        GroundMutationOutcome outcome = groundEntities.replaceWorld(worldId,
                expectedGroundRevision, committedGroundRevision, items, xpOrbs);
        if (outcome == GroundMutationOutcome.STALE) return outcome;
        long persistedPlayerRevision = playerStates.lockInventoryPersistenceRevisionJoiningTransaction(
                player.playerId(), worldId);
        if (persistedPlayerRevision != expectedPlayerRevision) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return GroundMutationOutcome.STALE;
        }
        playerStates.replaceExactSnapshotJoiningTransaction(player);
        replaceProjectiles(worldId, snapshots);
        return outcome;
    }

    private void replaceProjectiles(Long worldId,
            Collection<ProjectilePersistenceSnapshot> snapshots) {
        List<WorldProjectile> stored = repository.findAllByWorldId(worldId);
        Map<Long, WorldProjectile> byId = new HashMap<>();
        for (WorldProjectile row : stored) byId.put(row.getProjectileId(), row);
        Set<Long> live = new HashSet<>();
        List<WorldProjectile> changed = new ArrayList<>();
        for (ProjectilePersistenceSnapshot snapshot : snapshots) {
            if (!live.add(snapshot.projectileId())) throw new IllegalArgumentException("duplicate projectile id");
            WorldProjectile row = byId.get(snapshot.projectileId());
            if (row == null) changed.add(new WorldProjectile(worldId, snapshot));
            else if (!row.matches(snapshot)) { row.apply(snapshot); changed.add(row); }
        }
        if (!changed.isEmpty()) repository.saveAll(changed);
        List<WorldProjectile> removed = stored.stream()
                .filter(row -> !live.contains(row.getProjectileId())).toList();
        if (!removed.isEmpty()) repository.deleteAll(removed);
    }

    @Transactional
    public void deleteWorld(Long worldId) { repository.deleteAllByWorldId(worldId); }
}
