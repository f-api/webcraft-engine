package com.gameexpert.state.service;

import com.gameexpert.state.entity.PlayerDimensionTravel;
import com.gameexpert.state.entity.PlayerWorldState;
import com.gameexpert.state.repository.PlayerDimensionTravelRepository;
import com.gameexpert.state.repository.PlayerWorldStateRepository;
import com.gameexpert.state.service.inventory.StaleInventoryMutationException;
import com.gameexpert.api.persistence.PlayerStore;
import com.gameexpert.api.persistence.WorldAccess;
import com.gameexpert.world.entity.WorldDimension;
import com.gameexpert.world.repository.WorldDimensionRepository;
import com.gameexpert.api.persistence.WorldStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 이동과 저장 세대의 정본. 플레이어 행→travel 행 순서로 잠가 동등 inventory revision도 차단한다. */
@Service
@RequiredArgsConstructor
public class DimensionTravelPersistence {
    private final WorldStore worlds;
    private final WorldDimensionRepository dimensions;
    private final PlayerStore players;
    private final PlayerWorldStateRepository states;
    private final PlayerDimensionTravelRepository travels;

    private final com.gameexpert.world.dimension.DimensionRegistry registry;
    private final com.gameexpert.world.dimension.DimensionProviders providers;

    @Transactional
    public WorldAccess ensureChild(long rootWorldId, String dimension) {
        registry.requireEnabled(dimension);
        providers.require(dimension);
        WorldAccess root = worlds.findByIdForUpdate(rootWorldId).orElseThrow();
        if (dimensions.findByChildId(rootWorldId).isPresent()) {
            throw new IllegalArgumentException("child cannot own another dimension");
        }
        var prior = dimensions.findByRootIdAndDimensionKey(rootWorldId, dimension);
        if (prior.isPresent()) {
            WorldAccess child = prior.get().getChild();
            if (child.generationProfile() != root.generationProfile()) {
                throw new IllegalStateException("DIMENSION_GENERATION_PROFILE_MISMATCH");
            }
            return child;
        }
        WorldAccess child = worlds.saveAndFlush(worlds.create(dimension, root.getSeed(),
                root.getDifficulty(), root.getOwnerNickname(), root.generationProfile()));
        dimensions.saveAndFlush(new WorldDimension(root, child, dimension));
        return child;
    }

    @Transactional(readOnly = true)
    public long rootFor(long runtimeWorldId) {
        return dimensions.findByChildId(runtimeWorldId)
                .map(mapping -> mapping.getRoot().getId()).orElse(runtimeWorldId);
    }

    @Transactional(readOnly = true)
    public String dimensionFor(long runtimeWorldId) {
        return dimensions.findByChildId(runtimeWorldId).map(WorldDimension::getDimensionKey)
                .orElse("overworld");
    }

    @Transactional(readOnly = true)
    public boolean isChild(long worldId) { return dimensions.findByChildId(worldId).isPresent(); }

    @Transactional(readOnly = true)
    public PlayerDimensionIdentity current(long rootWorldId, long playerId) {
        if (rootFor(rootWorldId) != rootWorldId) throw new IllegalArgumentException("root world required");
        return travels.findCurrent(rootWorldId, playerId)
                .map(t -> new PlayerDimensionIdentity(rootWorldId, t.getActiveWorldId(), t.getDimension(), t.getTravelRevision()))
                .orElseGet(() -> new PlayerDimensionIdentity(rootWorldId, rootWorldId, 0));
    }

    /** Locking observation waits for an unknown commit outcome instead of reading an older MVCC view. */
    @Transactional
    public PlayerDimensionIdentity recoverAfterCommitFailure(long rootWorldId, long playerId) {
        states.findLockedByPlayerIdAndWorldId(playerId, rootWorldId).orElseThrow();
        return travels.findLocked(rootWorldId, playerId)
                .map(t -> new PlayerDimensionIdentity(rootWorldId, t.getActiveWorldId(),
                        t.getDimension(), t.getTravelRevision()))
                .orElseGet(() -> new PlayerDimensionIdentity(rootWorldId, rootWorldId, 0));
    }

    /** Calling transaction retains the player lock until every coupled spatial mutation finishes. */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = StaleInventoryMutationException.class)
    public PlayerWorldState requireCurrent(long playerId, PlayerDimensionIdentity expected) {
        if (rootFor(expected.runtimeWorldId()) != expected.rootWorldId()
                || !dimensionFor(expected.runtimeWorldId()).equals(expected.dimension())) {
            throw new StaleInventoryMutationException("dimension/root relationship changed");
        }
        PlayerWorldState state = states.findLockedByPlayerIdAndWorldId(
                playerId, expected.rootWorldId()).orElseThrow();
        PlayerDimensionTravel travel = travels.findLocked(expected.rootWorldId(), playerId).orElse(null);
        long active = travel == null ? expected.rootWorldId() : travel.getActiveWorldId();
        long revision = travel == null ? 0 : travel.getTravelRevision();
        if (active != expected.runtimeWorldId() || revision != expected.travelRevision()) {
            throw new StaleInventoryMutationException("stale player dimension generation");
        }
        return state;
    }

    @Transactional(readOnly = true)
    public double[] returnPose(long rootWorldId, long playerId) {
        PlayerDimensionTravel t = travels.findCurrent(rootWorldId, playerId).orElseThrow();
        if (t.getReturnX() == null || t.getReturnY() == null || t.getReturnZ() == null
                || t.getReturnYaw() == null || t.getReturnPitch() == null) {
            throw new IllegalStateException("return origin unavailable");
        }
        return new double[] {t.getReturnX(), t.getReturnY(), t.getReturnZ(), t.getReturnYaw(), t.getReturnPitch()};
    }

    /** Source has finished its exact durable leave; target terrain was prepared before this CAS. */
    @Transactional
    public PlayerDimensionIdentity commit(long playerId, PlayerDimensionIdentity expected,
            long sourceInventoryRevision, long targetWorldId, double[] targetPose, double[] returnPose) {
        return commitTravel(playerId, expected, sourceInventoryRevision, targetWorldId, targetPose, returnPose, false, true);
    }

    @Transactional
    public PlayerDimensionIdentity commitRespawn(long playerId, PlayerDimensionIdentity expected,
            long sourceInventoryRevision, double[] targetPose, boolean bed) {
        if (!"void_end".equals(expected.dimension())) throw new IllegalArgumentException("End respawn required");
        return commitTravel(playerId, expected, sourceInventoryRevision, expected.rootWorldId(), targetPose, null, true, bed);
    }

    private PlayerDimensionIdentity commitTravel(long playerId, PlayerDimensionIdentity expected,
            long sourceInventoryRevision, long targetWorldId, double[] targetPose, double[] returnPose,
            boolean respawn, boolean bed) {
        PlayerWorldState state = requireCurrent(playerId, expected);
        if (respawn && state.getHealth() > 0) throw new IllegalStateException("respawn requires settled death");
        if (state.getInventoryPersistenceRevision() != sourceInventoryRevision) {
            throw new StaleInventoryMutationException("source inventory changed during travel");
        }
        String targetDimension = dimensionFor(targetWorldId);
        registry.requireEnabled(targetDimension);
        if (!"overworld".equals(targetDimension)) providers.require(targetDimension);
        if ((expected.runtimeWorldId() == expected.rootWorldId())
                == (targetWorldId == expected.rootWorldId())) {
            throw new IllegalArgumentException("travel must enter from or return to root");
        }
        requirePose(targetPose);
        if (rootFor(targetWorldId) != expected.rootWorldId()
                || targetWorldId == expected.runtimeWorldId()) {
            throw new IllegalArgumentException("invalid dimension destination");
        }
        PlayerDimensionTravel travel = travels.findLocked(expected.rootWorldId(), playerId)
                .orElseGet(() -> new PlayerDimensionTravel(
                        worlds.getReferenceById(expected.rootWorldId()), players.getReferenceById(playerId)));
        if (targetWorldId == expected.rootWorldId()) travel.returnToRoot();
        else {
            requirePose(returnPose);
            travel.enter(targetWorldId, targetDimension, returnPose[0], returnPose[1], returnPose[2],
                    (float) returnPose[3], (float) returnPose[4]);
        }
        if (respawn) {
            state.updateHealth(20);
            state.updateHunger(com.gameexpert.engine.HungerRules.INITIAL_FOOD,
                    com.gameexpert.engine.HungerRules.INITIAL_SATURATION_MILLI);
            state.updateXpTotal(0);
            state.updateTimeSinceRest(0);
            state.updateStatusEffects(java.util.List.of());
            state.updateEffectClocks(new com.gameexpert.engine.effect.StatusEffects.PersistentPlayerEffectClocks(0,0,0,0,0));
            state.updateFireState(0,0);
            if (!bed) state.updateSpawn(null,null,null);
        }
        // Ordinary portal travel retains vitals; death respawn resets them atomically with the destination.
        state.updatePosition(targetPose[0], targetPose[1], targetPose[2],
                (float) targetPose[3], (float) targetPose[4]);
        travels.saveAndFlush(travel);
        return new PlayerDimensionIdentity(expected.rootWorldId(), targetWorldId, targetDimension, travel.getTravelRevision());
    }

    private static void requirePose(double[] pose) {
        if (pose == null || pose.length != 5) throw new IllegalArgumentException("complete pose required");
        for (double value : pose) if (!Double.isFinite(value)) throw new IllegalArgumentException("finite pose required");
    }
}
