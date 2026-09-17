package com.gameexpert.world.service;

import java.security.SecureRandom;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.gameexpert.block.repository.WorldBlockDiffRepository;
import com.gameexpert.banner.service.BannerBlockPersistenceService;
import com.gameexpert.banner.repository.WorldBannerSettlementRepository;
import com.gameexpert.lectern.repository.WorldLecternRepository;
import com.gameexpert.sign.repository.WorldSignBlockRepository;
import com.gameexpert.campfire.repository.WorldCampfireRepository;
import com.gameexpert.campfire.service.CampfirePersistenceService;
import com.gameexpert.boat.repository.WorldBoatRepository;
import com.gameexpert.cushion.repository.WorldCushionRepository;
import com.gameexpert.api.WorldContentCleanup;
import com.gameexpert.chest.repository.WorldChestRepository;
import com.gameexpert.container.persistence.WorldContainerSettlementRepository;
import com.gameexpert.furnace.repository.WorldFurnaceRepository;
import com.gameexpert.brewing.service.BrewingPersistenceService;
import com.gameexpert.ground.repository.WorldGroundItemRepository;
import com.gameexpert.ground.repository.WorldGroundMutationReceiptRepository;
import com.gameexpert.ground.repository.WorldGroundRevisionRepository;
import com.gameexpert.ground.repository.WorldGroundXpOrbRepository;
import com.gameexpert.map.repository.WorldPlayerMapSettlementRepository;
import com.gameexpert.map.repository.WorldMapRepository;
import com.gameexpert.common.ConflictException;
import com.gameexpert.config.EngineProperties;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.engine.persistence.finalcarrier.CanonicalWorldRowPurger;
import com.gameexpert.engine.persistence.animal.AnimalBlockTickPersistenceService;
import com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService;
import com.gameexpert.api.trial.TrialStorage;
import com.gameexpert.engine.qa.WorldQaFixtureReceiptRepository;
import com.gameexpert.frog.persistence.service.FrogColonyPersistenceService;
import com.gameexpert.common.InvalidRequestException;
import com.gameexpert.mob.repository.WorldMobMutationRepository;
import com.gameexpert.mob.repository.WorldMobRepository;
import com.gameexpert.mob.repository.WorldPopulatedChunkRepository;
import com.gameexpert.mob.repository.WorldRaidMemberRepository;
import com.gameexpert.mob.repository.WorldRaidRepository;
import com.gameexpert.mob.repository.WorldStructureOccupantClaimRepository;
import com.gameexpert.mob.repository.WorldVillagerJobClaimRepository;
import com.gameexpert.mob.repository.WorldVillagerTradeStateRepository;
import com.gameexpert.mob.service.MobPersistenceService;
import com.gameexpert.mob.service.RaidPersistenceService;
import com.gameexpert.mob.service.VillagerSocietyPersistenceService;
import com.gameexpert.api.PresenceOperations;
import com.gameexpert.projectile.repository.WorldProjectileRepository;
import com.gameexpert.qa.FinalSceneH12gTerminalService;
import com.gameexpert.shulker.repository.ShulkerContentsRepository;
import com.gameexpert.state.repository.PlayerWorldStateRepository;
import com.gameexpert.state.service.PlayerWorldStateService;
import com.gameexpert.api.persistence.WorldAccess;
import com.gameexpert.api.persistence.WorldStore;
import com.gameexpert.world.WorldBaselineReadiness;
import com.gameexpert.tnt.repository.WorldPrimedTntRepository;

import lombok.RequiredArgsConstructor;

import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class WorldOperations {
    private com.gameexpert.world.repository.WorldDimensionRepository dimensions;
    private com.gameexpert.state.repository.PlayerDimensionTravelRepository dimensionTravelRows;

    public void attachDimensions(com.gameexpert.world.repository.WorldDimensionRepository dimensions,
            com.gameexpert.state.repository.PlayerDimensionTravelRepository travelRows) {
        if (this.dimensions != null) throw new IllegalStateException("dimension storage already attached");
        this.dimensions = dimensions;
        this.dimensionTravelRows = travelRows;
    }


    private final WorldStore worldRepository;
    /** 시계 전용 행. worlds 행과 함께 지워야 고아가 남지 않는다. */
    private final com.gameexpert.world.repository.WorldClockStateRepository worldClockStates;
    private final PresenceOperations presenceService;
    private final WorldEngineManager engineManager;
    private final WorldBaselineReadiness baselineReadiness;

    // 월드 삭제 시 함께 지워야 할 자식 데이터들의 저장소
    private final WorldBlockDiffRepository blockDiffRepository;
    private final WorldBannerSettlementRepository bannerSettlements;
    private final WorldLecternRepository worldLecternRepository;
    private final ObjectProvider<WorldSignBlockRepository> worldSignBlockRepository;
    private final WorldContentCleanup chatMessageRepository;
    private final PlayerWorldStateRepository playerWorldStateRepository;
    private final PlayerWorldStateService playerWorldStateService;
    private final WorldChestRepository worldChestRepository;
    private final ShulkerContentsRepository shulkerContentsRepository;
    private final WorldFurnaceRepository worldFurnaceRepository;
    private final WorldCampfireRepository worldCampfireRepository;
    private final WorldBoatRepository worldBoatRepository;
    private final WorldCushionRepository worldCushionRepository;
    private final WorldMobRepository worldMobRepository;
    private final MobPersistenceService mobPersistenceService;
    private final WorldMobMutationRepository worldMobMutationRepository;
    private final WorldPopulatedChunkRepository worldPopulatedChunkRepository;
    private final WorldStructureOccupantClaimRepository worldStructureOccupantClaimRepository;
    private final WorldVillagerJobClaimRepository worldVillagerJobClaimRepository;
    private final WorldVillagerTradeStateRepository worldVillagerTradeStateRepository;
    private final VillagerSocietyPersistenceService villagerSocietyPersistenceService;
    private final RaidPersistenceService raidPersistenceService;
    private final WorldRaidRepository worldRaidRepository;
    private final WorldRaidMemberRepository worldRaidMemberRepository;
    private final WorldPrimedTntRepository worldPrimedTntRepository;
    private final WorldGroundItemRepository worldGroundItemRepository;
    private final WorldGroundXpOrbRepository worldGroundXpOrbRepository;
    private final WorldGroundMutationReceiptRepository groundMutationReceipts;
    private final WorldGroundRevisionRepository groundRevisions;
    private final WorldContainerSettlementRepository containerSettlements;
    private final WorldPlayerMapSettlementRepository playerMapSettlements;
    private final WorldMapRepository worldMapRepository;
    private final WorldProjectileRepository worldProjectileRepository;
    private final WorldQaFixtureReceiptRepository qaFixtureReceipts;
    private final CanonicalWorldRowPurger canonicalWorldRowPurger;
    private final FinalSceneH12gTerminalService finalSceneH12gTerminalService;
    private final ObjectProvider<AnimalBlockTickPersistenceService> animalBlockTickPersistence;
    private final ObjectProvider<AnimalSettlementPersistenceService> animalSettlementPersistence;
    private final ObjectProvider<TrialStorage> trialPersistence;
    private final ObjectProvider<FrogColonyPersistenceService> frogColonyPersistence;
    private final ObjectProvider<BannerBlockPersistenceService> bannerPersistence;
    private final ObjectProvider<BrewingPersistenceService> brewingPersistence;
    private final ObjectProvider<CampfirePersistenceService> campfirePersistence;
    /** [END-GATEWAY] 엔드 관문 출구 행. 월드 행을 지우기 전에 같은 트랜잭션에서 지운다. */
    private final ObjectProvider<com.gameexpert.endgateway.service.EndGatewayPersistenceService> endGatewayPersistence;
    /** [DRAGON] 드래곤전 상태 행. 월드 행을 지우기 전에 같은 트랜잭션에서 지운다. */
    private final ObjectProvider<com.gameexpert.dragonfight.service.DragonFightPersistenceService> dragonFightPersistence;
    /** [JUKEBOX] world_jukeboxes 행(음반 스택 · 곡 시계). */
    private final ObjectProvider<com.gameexpert.jukebox.service.JukeboxPersistenceService> jukeboxPersistence;
    /** [CONTAINER-MENUS] Stored player-placed armor stands and minecarts. */
    private final ObjectProvider<com.gameexpert.placed.service.PlacedEntityPersistenceService>
            placedEntityPersistence;
    /** [CONTAINER-MENUS] Crafter disabled-slot masks ({@code world_crafters}). */
    private final ObjectProvider<com.gameexpert.crafter.service.CrafterPersistenceService>
            crafterPersistence;
    private final EngineProperties engineProperties;


    private final SecureRandom secureRandom = new SecureRandom();
    private final WorldCreationGuard worldCreationGuard;
    private static final Pattern EXPLICIT_SEED_OWNER_NICKNAME = Pattern.compile("[A-Za-z0-9_]{2,12}");

    public long worldOnlineCount(Long worldId) {
        long count = presenceService.onlineCount(worldId);
        if (dimensions == null) return count;
        return count + dimensions.findChildIdsByRootId(worldId).stream()
                .mapToLong(presenceService::onlineCount)
                .sum();
    }

    public void deleteWorld(Long id, Runnable deleteWorldRow) {
        if (presenceService.onlineCount(id) > 0) throw new ConflictException("WORLD_IN_USE");
        boolean coordinated = WorldDeletionCoordinator.beforeDelete(id);
        com.gameexpert.cluster.ClusterRuntime cluster = com.gameexpert.cluster.ClusterRuntime.current();
        if (!coordinated && cluster != null && cluster.deleteWorldOnOwner(id)) return;

        if (presenceService.onlineCount(id) > 0
                || (!coordinated && !engineManager.beginWorldDeletion(id))) {
            throw new ConflictException("WORLD_IN_USE");
        }
        boolean transactionSynchronization = TransactionSynchronizationManager.isSynchronizationActive();
        if (transactionSynchronization && !coordinated) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    engineManager.endWorldDeletion(id);
                }
            });
        }
        try {
            deleteWorldAggregate(id, deleteWorldRow);
        } finally {
            if (!transactionSynchronization && !coordinated) engineManager.endWorldDeletion(id);
        }
    }

    @Transactional
    public int purgeObsoleteWorlds() {
        int removed = 0;
        for (WorldAccess world : worldRepository.findAll()) {
            if (world.usesCurrentBaseline() || !worldRepository.existsById(world.getId())) continue;
            // A root deletion owns its child dimensions and their persisted aggregates.
            if (dimensions != null && dimensions.findByChildId(world.getId()).isPresent()) continue;
            deleteWorldAggregate(world.getId(), () -> worldRepository.delete(world));
            removed++;
        }
        return removed;
    }

    private void deleteWorldAggregate(Long id, Runnable deleteWorldRow) {
        if (dimensions != null) {
            java.util.List<com.gameexpert.world.entity.WorldDimension> children = dimensions.findByRootId(id);
            WorldDeletionCoordinator.requireChildrenReserved(id,
                    children.stream().map(mapping -> mapping.getChild().getId()).toList());
            // Reserve every child before changing any aggregate; rollback releases reservations.
            for (com.gameexpert.world.entity.WorldDimension mapping : children) {
                long childId = mapping.getChild().getId();
                boolean reserved = WorldDeletionCoordinator.hasReservation(childId);
                if (presenceService.onlineCount(childId) > 0
                        || (!reserved && !engineManager.beginWorldDeletion(childId))) {
                    throw new ConflictException("WORLD_IN_USE");
                }
                if (!reserved) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void afterCompletion(int status) { engineManager.endWorldDeletion(childId); }
                    });
                }
            }
            dimensionTravelRows.deleteForRoot(id);
            dimensions.deleteAll(children);
            dimensions.flush();
            for (var mapping : children) {
                deleteWorldAggregate(mapping.getChild().getId(), () -> worldRepository.delete(mapping.getChild()));
            }
        }
        chatMessageRepository.deleteByWorldId(id);
        blockDiffRepository.deleteByWorldId(id);
        playerWorldStateRepository.deleteAll(playerWorldStateRepository.findAllByWorldId(id));
        shulkerContentsRepository.deleteAllByWorld_Id(id);
        worldChestRepository.deleteAllByWorldId(id);
        worldFurnaceRepository.deleteAllByWorldId(id);
        brewingPersistence.ifAvailable(service -> service.deleteWorld(id));
        CampfirePersistenceService campfire = campfirePersistence.getIfAvailable();
        if (campfire != null) campfire.deleteWorld(id);
        else worldCampfireRepository.deleteAllByWorldId(id);
        worldCushionRepository.deleteAllByWorldId(id);
        worldBoatRepository.deleteAllByWorldId(id);
        placedEntityPersistence.ifAvailable(service -> service.deleteWorld(id));
        worldMobRepository.deleteAllByWorldId(id);
        mobPersistenceService.deleteBrimstoneWorld(id);
        mobPersistenceService.forgetWorld(id);
        worldMobMutationRepository.deleteAllByWorldId(id);
        worldPopulatedChunkRepository.deleteAllByWorldId(id);
        worldStructureOccupantClaimRepository.deleteAllByWorldId(id);
        worldVillagerJobClaimRepository.deleteAllByWorldId(id);
        worldVillagerTradeStateRepository.deleteAllByWorldId(id);
        villagerSocietyPersistenceService.deleteAllByWorldId(id);
        worldRaidMemberRepository.deleteAllByWorldId(id);
        worldRaidRepository.deleteAllByWorldId(id);
        raidPersistenceService.forgetWorld(id);
        worldPrimedTntRepository.deleteAllByWorldId(id);
        containerSettlements.deleteAllByWorldId(id);
        playerMapSettlements.deleteAllByWorldId(id);
        worldMapRepository.deleteAllByWorld_Id(id);
        groundMutationReceipts.deleteAllByWorldId(id);
        worldGroundItemRepository.deleteAllByWorldId(id);
        worldGroundXpOrbRepository.deleteAllByWorldId(id);
        worldClockStates.deleteById(id);
        groundRevisions.deleteById(id);
        worldProjectileRepository.deleteAllByWorldId(id);
        qaFixtureReceipts.deleteAllByWorldId(id);
        animalBlockTickPersistence.ifAvailable(service -> service.deleteWorld(id));
        animalSettlementPersistence.ifAvailable(service -> service.deleteWorld(id));
        trialPersistence.ifAvailable(service -> service.deleteWorld(id));
        frogColonyPersistence.ifAvailable(service -> service.deleteWorld(id));
        bannerPersistence.ifAvailable(service -> service.deleteWorld(id));
        bannerSettlements.deleteAllByWorldId(id);
        worldLecternRepository.deleteAllByWorldId(id);
        jukeboxPersistence.ifAvailable(service -> service.deleteWorld(id));
        crafterPersistence.ifAvailable(service -> service.deleteWorld(id));
        worldSignBlockRepository.ifAvailable(repository -> repository.deleteAllByWorldId(id));
        endGatewayPersistence.ifAvailable(service -> service.deleteWorld(id));
        dragonFightPersistence.ifAvailable(service -> service.deleteWorld(id));
        // H12g terminal rows are world-scoped; its service locks but never deletes the global MAC
        // key, and participates in this REQUIRED transaction before the world row is removed.
        finalSceneH12gTerminalService.purgeWorld(id);
        // 정본(canonical worldgen / final carrier) 행은 월드 행과 같은 트랜잭션에서 지웁니다.
        // 남겨두면 삭제된 월드의 정본 커밋이 무한히 쌓이고, 재사용된 world id 가 남의 커밋을
        // 물려받습니다.
        canonicalWorldRowPurger.purgeWorld(id);
        deleteWorldRow.run();
        playerWorldStateService.evictWorldRuntimeInventories(id);
    }

    public void requireReady() {
        baselineReadiness.requireReady();
    }

    public void validateSeed(String owner, Long debugSeed) {
        if (debugSeed != null && (owner == null || !EXPLICIT_SEED_OWNER_NICKNAME.matcher(owner).matches())) {
            throw new InvalidRequestException("INVALID_REQUEST_BODY");
        }
        ExplicitWorldSeed.requireAcceptable(engineProperties, debugSeed);
    }

    public long seed(String name, Long debugSeed) {
        return ExplicitWorldSeed.resolve(engineProperties, name, debugSeed, secureRandom::nextInt);
    }

    @Transactional
    public <T> T duringCreation(Supplier<T> action) {
        worldCreationGuard.lock();
        return action.get();
    }

    public boolean hasParticipants(Long worldId) {
        return !playerWorldStateRepository.findAllByWorldId(worldId).isEmpty();
    }

    public boolean hasParticipated(Long playerId, Long worldId) {
        return playerWorldStateRepository.findByPlayerIdAndWorldId(playerId, worldId).isPresent();
    }
}
