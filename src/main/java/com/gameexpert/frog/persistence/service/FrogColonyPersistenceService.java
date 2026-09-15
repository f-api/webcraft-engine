package com.gameexpert.frog.persistence.service;

import com.gameexpert.frog.persistence.dto.FrogColonyCooldown;
import com.gameexpert.frog.persistence.dto.FrogColonyHydration;
import com.gameexpert.frog.persistence.dto.FrogConversionIntent;
import com.gameexpert.frog.persistence.entity.WorldFrogConversionSettlement;
import com.gameexpert.frog.persistence.entity.WorldRafflesiaFrogColony;
import com.gameexpert.frog.persistence.repository.FrogWorldLockRepository;
import com.gameexpert.frog.persistence.repository.WorldFrogConversionSettlementRepository;
import com.gameexpert.frog.persistence.repository.WorldRafflesiaFrogColonyRepository;
import com.gameexpert.mob.dto.MobPersistenceSnapshot;
import com.gameexpert.mob.entity.WorldMob;
import com.gameexpert.mob.repository.WorldMobRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable write-ahead boundary for Rafflesia colony frog conversions. */
@Service
public class FrogColonyPersistenceService {
    public static final long COLONY_COOLDOWN_MC_TICKS = 240_000L;
    public static final int SCHEMA_VERSION = 1;

    private final WorldRafflesiaFrogColonyRepository colonies;
    private final WorldFrogConversionSettlementRepository settlements;
    private final FrogWorldLockRepository worldLocks;
    private final WorldMobRepository mobs;

    public FrogColonyPersistenceService(WorldRafflesiaFrogColonyRepository colonies,
            WorldFrogConversionSettlementRepository settlements,
            FrogWorldLockRepository worldLocks, WorldMobRepository mobs) {
        this.colonies = colonies;
        this.settlements = settlements;
        this.worldLocks = worldLocks;
        this.mobs = mobs;
    }

    /** Restores cooldowns and replayable PENDING conversions for an activated world. */
    @Transactional(readOnly = true)
    public FrogColonyHydration hydrate(Long worldId) {
        requireWorldId(worldId);
        List<FrogColonyCooldown> cooldowns = colonies
                .findAllByWorldIdOrderByIdAsc(worldId).stream()
                .map(FrogColonyPersistenceService::toCooldown).toList();
        return new FrogColonyHydration(cooldowns, reconcile(worldId));
    }

    /** A missing row is a never-used colony and is therefore ready. */
    @Transactional(readOnly = true)
    public boolean isReady(Long worldId, int rafflesiaX, int rafflesiaY, int rafflesiaZ,
            long currentMcTick) {
        requireWorldId(worldId);
        requireTick(currentMcTick);
        return colonies.findByWorldIdAndRafflesiaXAndRafflesiaYAndRafflesiaZ(
                        worldId, rafflesiaX, rafflesiaY, rafflesiaZ)
                .map(row -> {
                    requireCurrentSchema(row.getSchemaVersion());
                    return currentMcTick >= row.getNextReadyMcTick();
                }).orElse(true);
    }

    /**
     * Persists PENDING before returning authority to the caller and reserves the colony cooldown.
     * An exact retry returns the original identity without extending the cooldown.
     */
    @Transactional
    public Optional<FrogConversionIntent> begin(Long worldId, long sourceMobId, long sequence,
            int colonyX, int colonyY, int colonyZ, int hostX, int hostY, int hostZ,
            String deterministicVariant, long currentMcTick) {
        validateBegin(worldId, sourceMobId, sequence, deterministicVariant, currentMcTick);
        lockWorld(worldId);

        WorldFrogConversionSettlement prior = settlements
                .findByWorldIdAndSourceMobIdAndSequence(worldId, sourceMobId, sequence)
                .orElse(null);
        if (prior != null) {
            requireCurrentSchema(prior.getSchemaVersion());
            requireSameIdentity(prior, colonyX, colonyY, colonyZ,
                    hostX, hostY, hostZ, deterministicVariant);
            return Optional.of(toIntent(prior));
        }

        WorldRafflesiaFrogColony colony = colonies
                .findLockedByWorldIdAndRafflesiaXAndRafflesiaYAndRafflesiaZ(
                        worldId, colonyX, colonyY, colonyZ)
                .orElse(null);
        if (colony != null) {
            requireCurrentSchema(colony.getSchemaVersion());
            if (currentMcTick < colony.getNextReadyMcTick()) return Optional.empty();
        } else {
            colony = new WorldRafflesiaFrogColony(worldId, colonyX, colonyY, colonyZ,
                    currentMcTick, SCHEMA_VERSION);
        }

        long nextReady = Math.addExact(currentMcTick, COLONY_COOLDOWN_MC_TICKS);
        colony.reserveUntil(nextReady);
        colonies.save(colony);
        WorldFrogConversionSettlement created = settlements.save(
                new WorldFrogConversionSettlement(worldId, sourceMobId, sequence,
                        colonyX, colonyY, colonyZ, hostX, hostY, hostZ,
                        deterministicVariant, SCHEMA_VERSION));
        colonies.flush();
        settlements.flush();
        persistenceBoundary("BEGIN");
        return Optional.of(toIntent(created));
    }

    /**
     * Replaces the source row with the same-mobId poison-frog snapshot and advances to APPLIED in
     * one transaction. A retry of an already APPLIED row is idempotent only for the same identity.
     */
    @Transactional
    public boolean markApplied(Long worldId, long sourceMobId, long sequence,
            MobPersistenceSnapshot poisonFrogSnapshot) {
        requireIdentity(worldId, sourceMobId, sequence);
        if (poisonFrogSnapshot == null
                || poisonFrogSnapshot.getMobId() != sourceMobId
                || !"POISON_DART_FROG".equals(poisonFrogSnapshot.getType())) {
            throw new IllegalArgumentException(
                    "applied snapshot must be a POISON_DART_FROG with the source mobId");
        }
        lockWorld(worldId);
        WorldFrogConversionSettlement row = settlements
                .findByWorldIdAndSourceMobIdAndSequence(worldId, sourceMobId, sequence)
                .orElse(null);
        if (row == null) return false;
        requireCurrentSchema(row.getSchemaVersion());
        if (!row.getDeterministicVariant().equals(poisonFrogSnapshot.getVariant())) {
            throw new IllegalStateException("applied frog variant differs from durable intent");
        }
        if (!row.isPending()) return true;

        WorldMob mob = mobs.findLockedByWorldIdAndMobId(worldId, sourceMobId)
                .orElseGet(() -> new WorldMob(worldId, poisonFrogSnapshot));
        mob.apply(poisonFrogSnapshot);
        mobs.saveAndFlush(mob);
        row.markApplied();
        settlements.saveAndFlush(row);
        persistenceBoundary("APPLY");
        return true;
    }

    /** Marks an APPLIED authority effect acknowledged; repeated commits are idempotent. */
    @Transactional
    public boolean commit(Long worldId, long sourceMobId, long sequence) {
        requireIdentity(worldId, sourceMobId, sequence);
        lockWorld(worldId);
        WorldFrogConversionSettlement row = settlements
                .findByWorldIdAndSourceMobIdAndSequence(worldId, sourceMobId, sequence)
                .orElse(null);
        if (row == null) return false;
        requireCurrentSchema(row.getSchemaVersion());
        if (row.getStatus() == WorldFrogConversionSettlement.Status.COMMITTED) return true;
        if (!row.isApplied()) return false;
        row.commit();
        settlements.saveAndFlush(row);
        persistenceBoundary("COMMIT");
        return true;
    }

    /** Returns PENDING and APPLIED checkpoints in durable order for restart reconciliation. */
    @Transactional(readOnly = true)
    public List<FrogConversionIntent> reconcile(Long worldId) {
        requireWorldId(worldId);
        return settlements.findAllByWorldIdAndStatusNotOrderByIdAsc(
                        worldId, WorldFrogConversionSettlement.Status.COMMITTED).stream()
                .map(FrogColonyPersistenceService::toIntent).toList();
    }

    @Transactional
    public void deleteWorld(Long worldId) {
        requireWorldId(worldId);
        settlements.deleteAllByWorldId(worldId);
        colonies.deleteAllByWorldId(worldId);
    }

    void persistenceBoundary(String phase) {
    }

    private void lockWorld(Long worldId) {
        if (worldLocks.lockById(worldId).isEmpty()) {
            throw new IllegalArgumentException("unknown worldId");
        }
    }

    private static FrogColonyCooldown toCooldown(WorldRafflesiaFrogColony row) {
        requireCurrentSchema(row.getSchemaVersion());
        return new FrogColonyCooldown(row.getRafflesiaX(), row.getRafflesiaY(),
                row.getRafflesiaZ(), row.getNextReadyMcTick());
    }

    private static FrogConversionIntent toIntent(WorldFrogConversionSettlement row) {
        requireCurrentSchema(row.getSchemaVersion());
        return new FrogConversionIntent(row.getSourceMobId(), row.getSequence(),
                row.getColonyX(), row.getColonyY(), row.getColonyZ(),
                row.getHostX(), row.getHostY(), row.getHostZ(),
                row.getDeterministicVariant(), switch (row.getStatus()) {
                    case PENDING -> FrogConversionIntent.Phase.PENDING;
                    case APPLIED -> FrogConversionIntent.Phase.APPLIED;
                    case COMMITTED -> FrogConversionIntent.Phase.COMMITTED;
                });
    }

    private static void requireSameIdentity(WorldFrogConversionSettlement row,
            int colonyX, int colonyY, int colonyZ, int hostX, int hostY, int hostZ,
            String deterministicVariant) {
        if (row.getColonyX() != colonyX || row.getColonyY() != colonyY
                || row.getColonyZ() != colonyZ || row.getHostX() != hostX
                || row.getHostY() != hostY || row.getHostZ() != hostZ
                || !row.getDeterministicVariant().equals(deterministicVariant)) {
            throw new IllegalStateException("frog conversion identity collision");
        }
    }

    private static void validateBegin(Long worldId, long sourceMobId, long sequence,
            String deterministicVariant, long currentMcTick) {
        requireIdentity(worldId, sourceMobId, sequence);
        requireTick(currentMcTick);
        if (deterministicVariant == null || deterministicVariant.isBlank()
                || deterministicVariant.length() > 40) {
            throw new IllegalArgumentException("deterministicVariant must be 1..40 characters");
        }
    }

    private static void requireIdentity(Long worldId, long sourceMobId, long sequence) {
        requireWorldId(worldId);
        if (sourceMobId <= 0) throw new IllegalArgumentException("sourceMobId must be positive");
        if (sequence < 0) throw new IllegalArgumentException("sequence must be non-negative");
    }

    private static void requireWorldId(Long worldId) {
        if (worldId == null || worldId <= 0) {
            throw new IllegalArgumentException("worldId must be positive");
        }
    }

    private static void requireTick(long currentMcTick) {
        if (currentMcTick < 0) throw new IllegalArgumentException("MC tick must be non-negative");
    }

    private static void requireCurrentSchema(int schemaVersion) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalStateException("unsupported frog persistence schema " + schemaVersion);
        }
    }
}
