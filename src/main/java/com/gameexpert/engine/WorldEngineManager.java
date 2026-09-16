package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.WebSocketSession;

import com.gameexpert.block.persistence.BlockDiffBuffer;
import com.gameexpert.block.persistence.BlockDiffFlusher;
import com.gameexpert.block.repository.WorldBlockDiffRepository;
import com.gameexpert.banner.service.BannerBlockPersistenceService;
import com.gameexpert.sign.service.SignBlockPersistenceService;
import com.gameexpert.banner.service.BannerPlacementSettlementService;
import com.gameexpert.lectern.service.LecternPersistenceService;
import com.gameexpert.lectern.service.LecternMiningSettlementService;
import com.gameexpert.campfire.service.CampfirePersistenceService;
import com.gameexpert.cushion.service.CushionPersistenceService;
import com.gameexpert.chest.service.ChestPersistenceService;
import com.gameexpert.furnace.service.FurnacePersistenceService;
import com.gameexpert.brewing.service.BrewingPersistenceService;
import com.gameexpert.enchanting.service.EnchantingPersistenceService;
import com.gameexpert.ground.service.GroundEntityPersistenceService;
import com.gameexpert.ground.service.GroundMutationSettlementService;
import com.gameexpert.projectile.service.ProjectilePersistenceService;
import com.gameexpert.qa.AuthorityEvidenceRuntimeCallbacks;
import com.gameexpert.qa.FinalSceneH12fOutcomeService;
import com.gameexpert.qa.FinalSceneH12gOutcomeService;
import com.gameexpert.mob.service.MobMutationJournalService;
import com.gameexpert.mob.service.MobPersistenceService;
import com.gameexpert.mob.service.VillagerSocietyPersistenceService;
import com.gameexpert.map.dto.WorldMapData;
import com.gameexpert.map.service.WorldMapPersistenceService;
import com.gameexpert.common.ConflictException;
import com.gameexpert.config.EngineProperties;
import com.gameexpert.engine.inventory.ArmorSlot;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.effect.StatusEffects;
import com.gameexpert.engine.persistence.animal.AnimalBlockTickPersistenceService;
import com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService;
import com.gameexpert.engine.persistence.finalcarrier.FinalCarrierPersistenceService;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.GeneratedChestMinecartLootResolutionService;
import com.gameexpert.engine.qa.ContentQaFixtureSettlementService;
import com.gameexpert.api.trial.TrialStorage;
import com.gameexpert.frog.persistence.service.FrogColonyPersistenceService;
import com.gameexpert.state.entity.InventoryItem;
import com.gameexpert.state.entity.PlayerWorldState;
import com.gameexpert.state.service.PlayerWorldStateService;
import com.gameexpert.state.service.inventory.PlayerContainerSettlementPersistenceService;
import com.gameexpert.container.persistence.ContainerSettlementPersistenceService;
import com.gameexpert.ws.GameTransport;
import com.gameexpert.api.SessionRegistry;
import com.gameexpert.ws.dto.WsMessages.BoatDto;
import com.gameexpert.ws.dto.WsMessages.CushionDto;
import com.gameexpert.ws.dto.WsMessages.CampfireSnapshot;
import com.gameexpert.ws.dto.WsMessages.FireBlock;
import com.gameexpert.ws.dto.WsMessages.ItemEntityDto;
import com.gameexpert.ws.dto.WsMessages.MapState;
import com.gameexpert.ws.dto.WsMessages.MobSpawnDto;
import com.gameexpert.ws.dto.WsMessages.PlayerPose;
import com.gameexpert.ws.dto.WsMessages.WeatherState;
import com.gameexpert.ws.dto.WsMessages.XpOrbDto;
import com.gameexpert.world.service.WorldSpawnPersistenceService;
import com.gameexpert.world.service.WorldTimePersistenceService;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.CanonicalOriginChunkProductSource;
import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import com.gameexpert.terrain.persistence.InMemoryCanonicalWorldgenStore;
import com.gameexpert.tnt.service.PrimedTntPersistenceService;
import com.gameexpert.ws.dto.WsMessages.PrimedTntDto;
import com.gameexpert.ws.dto.WsMessages.ProjectileSpawn;
import com.gameexpert.ws.dto.WsMessages.GeneratedEntitySnapshot;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import jakarta.annotation.PreDestroy;

/**
 * [제공코드] 월드별 틱 런타임의 수명주기 관리자.
 *
 * 첫 세션 입장 시 {@link WorldRuntime}와 월드당 단일 스레드 executor(100ms 고정 주기)를 만들고,
 * 세션이 0이 되면 100틱 유예 뒤 런타임 스스로 상태 저장·diff flush·executor 종료·폐기합니다.
 * WS 스레드는 여기 {@code onPlayerJoin/onPlayerLeave/enqueue}로만 런타임과 상호작용합니다.
 */
@Component
@org.springframework.context.annotation.DependsOn("chatHistoryIndexRequirement")
public class WorldEngineManager
        implements FinalSceneH12fOutcomeService.CompletedLedgerProvider,
        FinalSceneH12gOutcomeService.CompletedLedgerProvider {
    /** 임시 접속 단계 계측. -Dwebcraft.snapshotStageTrace=true 로만 켜지고 확정되면 지운다. */
    private static final boolean JOIN_STAGE_TRACE =
            Boolean.getBoolean("webcraft.snapshotStageTrace");
    private static final org.slf4j.Logger JOIN_TRACE_LOG =
            org.slf4j.LoggerFactory.getLogger(WorldEngineManager.class);


    private static final long H12F_RESERVATION_TIMEOUT_MILLIS = 2_000L;
    private static final long H12G_RESERVATION_TIMEOUT_MILLIS = 2_000L;

    private volatile com.gameexpert.state.service.DimensionTravelPersistence dimensionTravel;
    private volatile com.gameexpert.world.dimension.DimensionRegistry dimensionRegistry;
    private volatile com.gameexpert.world.dimension.DimensionProviders dimensionProviders;

    public void attachDimensions(com.gameexpert.state.service.DimensionTravelPersistence travel,
            com.gameexpert.world.dimension.DimensionRegistry registry,
            com.gameexpert.world.dimension.DimensionProviders providers) {
        if (dimensionTravel != null) throw new IllegalStateException("dimensions already attached");
        dimensionTravel = travel; dimensionRegistry = registry; dimensionProviders = providers;
    }

    private boolean customDimension(Long worldId) {
        return dimensionTravel != null && dimensionTravel.isChild(worldId);
    }

    private volatile java.util.function.Consumer<com.gameexpert.world.dimension.PortalTravelRequest> dimensionGateway;

    public void attachDimensionGateway(
            java.util.function.Consumer<com.gameexpert.world.dimension.PortalTravelRequest> gateway) {
        dimensionGateway = java.util.Objects.requireNonNull(gateway);
    }

    public void prepareDimensionTarget(long worldId, int seed, Difficulty difficulty, double[] pose) {
        WorldRuntime runtime = acquireDimensionTarget(worldId, seed, difficulty);
        try { runtime.prepareDimensionArrival(pose); }
        catch (RuntimeException | Error failure) { runtime.releaseDimensionArrival(); throw failure; }
    }

    public RespawnRules.Spawn prepareDimensionRespawnTarget(long worldId, int seed, Difficulty difficulty,
            PlayerTickState player) {
        WorldRuntime runtime = acquireDimensionTarget(worldId, seed, difficulty);
        try { return runtime.prepareDimensionRespawn(player); }
        catch (RuntimeException | Error failure) { runtime.releaseDimensionArrival(); throw failure; }
    }

    private WorldRuntime acquireDimensionTarget(long worldId, int seed, Difficulty difficulty) {
        WorldRuntime runtime;
        synchronized (lifecycleLocks.computeIfAbsent(worldId, ignored -> new Object())) {
            if (deletingWorlds.contains(worldId)) throw new ConflictException("WORLD_IN_USE");
            runtime = runtimes.computeIfAbsent(worldId, id -> {
                boolean combinedCheckpoint = primedTntPersistence != null;
                if (combinedCheckpoint) context.blockDiffFlusher().claimCombinedCheckpoint(id);
                try {
                    context.blockDiffFlusher().flushWorldBlocking(id);
                    return createRuntime(id, seed, difficulty);
                } catch (RuntimeException | Error failure) {
                    if (combinedCheckpoint) context.blockDiffFlusher().releaseCombinedCheckpoint(id);
                    throw failure;
                }
            });
            runtime.pinDimensionArrival();
        }
        return runtime;
    }

    public void releaseDimensionTarget(long worldId) {
        WorldRuntime runtime = runtimes.get(worldId);
        if (runtime != null) runtime.releaseDimensionArrival();
    }

    public double[] dimensionReturnOrigin(long worldId, String nickname, String connectionId, int portalBlock) {
        WorldRuntime runtime = runtimes.get(worldId);
        if (runtime == null) throw new IllegalStateException("source runtime unavailable");
        return runtime.dimensionReturnOrigin(nickname, connectionId, portalBlock);
    }

    public PlayerTickState departForDimension(long worldId, String nickname, String connectionId, int portalBlock) {
        WorldRuntime runtime = runtimes.get(worldId);
        if (runtime == null) throw new IllegalStateException("source runtime unavailable");
        return runtime.departForDimension(nickname, connectionId, portalBlock);
    }

    public PlayerTickState departForRespawn(long worldId, String nickname, String connectionId) {
        WorldRuntime runtime = runtimes.get(worldId);
        if (runtime == null) throw new IllegalStateException("source runtime unavailable");
        return runtime.departForDimension(nickname, connectionId, com.gameexpert.terrain.Blocks.END_PORTAL, true);
    }

    public void restoreDimensionDeparture(long worldId, String nickname, String connectionId) {
        WorldRuntime runtime = runtimes.get(worldId);
        if (runtime == null) throw new IllegalStateException("source recovery runtime unavailable");
        runtime.restoreDimensionDeparture(nickname, connectionId);
    }

    public void completeDimensionDeparture(long worldId, String nickname) {
        WorldRuntime runtime = runtimes.get(worldId);
        if (runtime != null) runtime.completeDimensionDeparture(nickname);
    }

    private volatile com.gameexpert.engine.persistence.animal.FleshNetherPersistenceService fleshNetherPersistence;
    private volatile com.gameexpert.engine.persistence.animal.FleshColonySettlementService fleshColonySettlements;

    public void attachFleshColonySettlements(
            com.gameexpert.engine.persistence.animal.FleshColonySettlementService service) {
        fleshColonySettlements = java.util.Objects.requireNonNull(service);
    }

    public void attachFleshNetherPersistence(
            com.gameexpert.engine.persistence.animal.FleshNetherPersistenceService service) {
        fleshNetherPersistence = java.util.Objects.requireNonNull(service);
    }

    private final EngineContext context;
    private final ConcurrentHashMap<Long, WorldRuntime> runtimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CanonicalOriginChunkProductSource> canonicalSources =
            new ConcurrentHashMap<>();
    /**
     * One canonical prefetch scheduler per live world. Producing a canonical chunk costs hundreds
     * of milliseconds while replaying a committed one costs about a millisecond, so the scheduler
     * commits the spiral around current demand ahead of need and leaves every consumer — the world
     * owner above all — on the lock-free replay path. It is an accelerator only: the product seam,
     * its bounded memos and its fail-closed identity are unchanged.
     */
    private final ConcurrentHashMap<Long, CanonicalChunkPrefetchScheduler> canonicalPrefetchers =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, int[]> canonicalSpawns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, PlayerSnapshot>> departedPlayers =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Object> lifecycleLocks = new ConcurrentHashMap<>();
    private final java.util.Set<Long> deletingWorlds = ConcurrentHashMap.newKeySet();
    private final AtomicInteger threadSeq = new AtomicInteger();
    private final ChestPersistenceService chestPersistence;
    private final FurnacePersistenceService furnacePersistence;
    private BrewingPersistenceService brewingPersistence;
    private final CampfirePersistenceService campfirePersistence;
    private final EnchantingPersistenceService enchantingPersistence;
    private final MobPersistenceService mobPersistence;
    private final MobMutationJournalService mobMutationJournals;
    private final WorldTimePersistenceService worldTimePersistence;
    private WorldSpawnPersistenceService worldSpawnPersistence;
    private final FinalCarrierPersistenceService finalCarrierPersistence;
    private final CanonicalWorldgenStore canonicalWorldgenStore;
    private final GeneratedStructureEntityMutationCoordinator generatedCushionMutations;
    private GeneratedChestMinecartLootResolutionService generatedMinecartLootResolutions;
    private final WorldMapPersistenceService worldMapPersistence;
    private PrimedTntPersistenceService primedTntPersistence;
    private CushionPersistenceService cushionPersistence;
    private com.gameexpert.boat.service.BoatPersistenceService boatPersistence;
    private com.gameexpert.placed.service.PlacedEntityPersistenceService placedEntityPersistence;
    private VillagerSocietyPersistenceService villagerSocietyPersistence;
    private GroundEntityPersistenceService groundEntityPersistence;
    private GroundMutationSettlementService groundMutationSettlements;
    private com.gameexpert.map.service.EmptyMapSettlementService emptyMapSettlements;
    private ProjectilePersistenceService projectilePersistence;
    private AnimalBlockTickPersistenceService animalBlockTickPersistence;
    private AnimalSettlementPersistenceService animalSettlementPersistence;
    private TrialStorage trialPersistence;
    private FrogColonyPersistenceService frogColonyPersistence;
    private PlayerContainerSettlementPersistenceService playerContainerSettlements;
    private ContainerSettlementPersistenceService generatedArmorStandEquipmentSettlements;
    private BannerBlockPersistenceService bannerPersistence;
    private SignBlockPersistenceService signPersistence;
    private BannerPlacementSettlementService bannerPlacementSettlements;
    private LecternPersistenceService lecternPersistence;
    private com.gameexpert.jukebox.service.JukeboxPersistenceService jukeboxPersistence;
    private com.gameexpert.crafter.service.CrafterPersistenceService crafterPersistence;
    private LecternMiningSettlementService lecternMiningSettlements;
    private com.gameexpert.block.service.PlayerBlockSettlementService playerBlockSettlements;
    private ContentQaFixtureSettlementService contentQaFixtureSettlements;
    private AuthorityEvidenceRuntimeCallbacks authorityEvidenceRuntimeCallbacks;

    /**
     * Reserves one completed H12f ledger through the world's owner thread. The registry is checked
     * on both sides of the bounded wait so a reconnect cannot inherit another connection's result;
     * the runtime performs the same connection fence again on its owner thread.
     */
    @Override
    public Optional<FinalSceneH12fOutcomeService.PreparedLedger> consume(
            FinalSceneH12fOutcomeService.ActiveBinding binding) {
        java.util.Objects.requireNonNull(binding, "H12f active binding");
        WorldRuntime runtime = runtimes.get(binding.worldId());
        if (runtime == null || !runtime.ownerTurnMayContinue()
                || !hasExactActiveConnection(binding)) {
            return Optional.empty();
        }

        java.util.function.BooleanSupplier liveBinding = () ->
                runtimes.get(binding.worldId()) == runtime
                        && runtime.ownerTurnMayContinue()
                        && hasExactActiveConnection(binding);
        CompletableFuture<Optional<FinalSceneH12fOutcomeService.PreparedLedger>> reservation =
                runtime.reserveFinalSceneH12fCompletedLedger(binding, liveBinding);
        if (reservation == null) return Optional.empty();
        try {
            Optional<FinalSceneH12fOutcomeService.PreparedLedger> prepared =
                    reservation.get(H12F_RESERVATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (prepared == null) return Optional.empty();
            if (runtimes.get(binding.worldId()) == runtime && runtime.ownerTurnMayContinue()
                    && hasExactActiveConnection(binding)) {
                return prepared;
            }
            prepared.ifPresent(FinalSceneH12fOutcomeService.PreparedLedger::dispose);
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            cancelH12fReservation(reservation);
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (TimeoutException timeout) {
            cancelH12fReservation(reservation);
            return Optional.empty();
        } catch (ExecutionException failure) {
            return Optional.empty();
        }
    }

    private static void cancelH12fReservation(
            CompletableFuture<Optional<FinalSceneH12fOutcomeService.PreparedLedger>> future) {
        if (future.cancel(true)) return;
        Optional<FinalSceneH12fOutcomeService.PreparedLedger> completed =
                future.getNow(Optional.empty());
        if (completed != null) {
            completed.ifPresent(FinalSceneH12fOutcomeService.PreparedLedger::dispose);
        }
    }

    private boolean hasExactActiveConnection(
            FinalSceneH12fOutcomeService.ActiveBinding binding) {
        SessionRegistry registry = context.registry();
        if (registry == null) return false;
        SessionRegistry.Entry entry = registry.get(binding.worldId(), binding.nickname());
        return entry != null && entry.session().isOpen()
                && binding.connectionId().equals(entry.connectionId());
    }

    /** Reserves one exact H12g tracker receipt and durable minecart projection. */
    @Override
    public Optional<FinalSceneH12gOutcomeService.PreparedLedger> consume(
            FinalSceneH12gOutcomeService.ActiveBinding binding) {
        return consume(binding, H12G_RESERVATION_TIMEOUT_MILLIS);
    }

    Optional<FinalSceneH12gOutcomeService.PreparedLedger> consume(
            FinalSceneH12gOutcomeService.ActiveBinding binding, long timeoutMillis) {
        java.util.Objects.requireNonNull(binding, "H12g active binding");
        if (timeoutMillis <= 0L) throw new IllegalArgumentException("H12g timeout must be positive");
        WorldRuntime runtime = runtimes.get(binding.worldId());
        if (runtime == null || !runtime.ownerTurnMayContinue()
                || !hasExactActiveConnection(binding)) {
            return Optional.empty();
        }
        java.util.function.BooleanSupplier liveBinding = () ->
                runtimes.get(binding.worldId()) == runtime
                        && runtime.ownerTurnMayContinue()
                        && hasExactActiveConnection(binding);
        CompletableFuture<Optional<FinalSceneH12gOutcomeService.PreparedLedger>> reservation =
                runtime.reserveFinalSceneH12gCompletedLedger(binding, liveBinding);
        if (reservation == null) return Optional.empty();
        try {
            Optional<FinalSceneH12gOutcomeService.PreparedLedger> prepared =
                    reservation.get(timeoutMillis, TimeUnit.MILLISECONDS);
            if (prepared == null) return Optional.empty();
            if (runtimes.get(binding.worldId()) == runtime && runtime.ownerTurnMayContinue()
                    && hasExactActiveConnection(binding)) {
                return prepared;
            }
            prepared.ifPresent(FinalSceneH12gOutcomeService.PreparedLedger::dispose);
            return Optional.empty();
        } catch (InterruptedException interrupted) {
            cancelH12gReservation(reservation);
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (TimeoutException timeout) {
            cancelH12gReservation(reservation);
            return Optional.empty();
        } catch (ExecutionException failure) {
            return Optional.empty();
        }
    }

    private static void cancelH12gReservation(
            CompletableFuture<Optional<FinalSceneH12gOutcomeService.PreparedLedger>> future) {
        if (future.cancel(true)) return;
        Optional<FinalSceneH12gOutcomeService.PreparedLedger> completed =
                future.getNow(Optional.empty());
        if (completed != null) {
            completed.ifPresent(FinalSceneH12gOutcomeService.PreparedLedger::dispose);
        }
    }

    private boolean hasExactActiveConnection(
            FinalSceneH12gOutcomeService.ActiveBinding binding) {
        SessionRegistry registry = context.registry();
        if (registry == null) return false;
        SessionRegistry.Entry entry = registry.get(binding.worldId(), binding.nickname());
        return entry != null && entry.session().isOpen()
                && binding.connectionId().equals(entry.connectionId());
    }

    /**
     * 정본 스폰 좌표 영속화. 이 매니저를 직접 조립하는 테스트는 이 협력자 없이도 돌아가야 하므로
     * 선택 주입이며, 없으면 예전처럼 접속마다 탐색합니다.
     */
    @Autowired(required = false)
    void setWorldSpawnPersistence(WorldSpawnPersistenceService service) {
        this.worldSpawnPersistence = service;
    }

    @Autowired(required = false)
    void setAuthorityEvidenceRuntimeCallbacks(AuthorityEvidenceRuntimeCallbacks callbacks) {
        this.authorityEvidenceRuntimeCallbacks = callbacks;
    }

    @Autowired(required = false)
    void setContentQaFixtureSettlements(ContentQaFixtureSettlementService service) {
        this.contentQaFixtureSettlements = service;
    }

    @Autowired(required = false)
    void setBrewingPersistence(BrewingPersistenceService service) {
        this.brewingPersistence = service;
    }

    @Autowired(required = false)
    void setPrimedTntPersistence(PrimedTntPersistenceService service) {
        this.primedTntPersistence = service;
    }

    @Autowired(required = false)
    void setCushionPersistence(CushionPersistenceService service) {
        this.cushionPersistence = service;
    }

    @Autowired(required = false)
    void setBoatPersistence(com.gameexpert.boat.service.BoatPersistenceService service) {
        this.boatPersistence = service;
    }

    /** [CONTAINER-MENUS] Stored armor stands and minecarts; absent in database-free unit tests. */
    @Autowired(required = false)
    void setPlacedEntityPersistence(
            com.gameexpert.placed.service.PlacedEntityPersistenceService service) {
        this.placedEntityPersistence = service;
    }

    @Autowired(required = false)
    void setBannerPersistence(BannerBlockPersistenceService service) {
        this.bannerPersistence = service;
    }

    @Autowired(required = false)
    void setSignPersistence(SignBlockPersistenceService service) {
        this.signPersistence = service;
    }

    /** [END-GATEWAY] 엔드 차원 엔드 관문 출구 행. DB 없는 단위 테스트 매니저에서는 비어 있다. */
    private com.gameexpert.endgateway.service.EndGatewayPersistenceService endGatewayPersistence;

    @Autowired(required = false)
    void setEndGatewayPersistence(com.gameexpert.endgateway.service.EndGatewayPersistenceService service) {
        this.endGatewayPersistence = service;
    }

    /** [DRAGON] 엔드 차원 드래곤전 상태 행. DB 없는 단위 테스트 매니저에서는 비어 있다. */
    private com.gameexpert.dragonfight.service.DragonFightPersistenceService dragonFightPersistence;

    @Autowired(required = false)
    void setDragonFightPersistence(com.gameexpert.dragonfight.service.DragonFightPersistenceService service) {
        this.dragonFightPersistence = service;
    }

    @Autowired(required = false)
    void setBannerPlacementSettlements(BannerPlacementSettlementService service) {
        this.bannerPlacementSettlements = service;
    }

    @Autowired(required = false)
    void setLecternPersistence(LecternPersistenceService service) {
        this.lecternPersistence = service;
    }

    /** [CONTAINER-MENUS] Crafter disabled-slot masks; absent in DB-less test managers. */
    @Autowired(required = false)
    void setCrafterPersistence(com.gameexpert.crafter.service.CrafterPersistenceService service) {
        this.crafterPersistence = service;
    }

    @Autowired(required = false)
    void setJukeboxPersistence(com.gameexpert.jukebox.service.JukeboxPersistenceService service) {
        this.jukeboxPersistence = service;
    }

    @Autowired(required = false)
    void setLecternMiningSettlements(LecternMiningSettlementService service) {
        this.lecternMiningSettlements = service;
    }

    @Autowired(required = false)
    void setPlayerBlockSettlements(
            com.gameexpert.block.service.PlayerBlockSettlementService service) {
        this.playerBlockSettlements = service;
    }

    @Autowired(required = false)
    void setVillagerSocietyPersistence(VillagerSocietyPersistenceService service) {
        this.villagerSocietyPersistence = service;
    }

    @Autowired(required = false)
    void setGroundEntityPersistence(GroundEntityPersistenceService service) {
        this.groundEntityPersistence = service;
    }

    @Autowired(required = false)
    void setGroundMutationSettlements(GroundMutationSettlementService service) {
        this.groundMutationSettlements = service;
    }

    @Autowired(required = false)
    void setEmptyMapSettlements(com.gameexpert.map.service.EmptyMapSettlementService service) {
        this.emptyMapSettlements = service;
    }

    @Autowired(required = false)
    void setProjectilePersistence(ProjectilePersistenceService service) {
        this.projectilePersistence = service;
    }

    @Autowired(required = false)
    void setAnimalBlockTickPersistence(AnimalBlockTickPersistenceService service) {
        this.animalBlockTickPersistence = service;
    }

    @Autowired(required = false)
    void setAnimalSettlementPersistence(AnimalSettlementPersistenceService service) {
        this.animalSettlementPersistence = service;
    }

    @Autowired(required = false)
    void setTrialPersistence(TrialStorage service) {
        this.trialPersistence = service;
    }

    @Autowired(required = false)
    void setFrogColonyPersistence(FrogColonyPersistenceService service) {
        this.frogColonyPersistence = service;
    }

    @Autowired(required = false)
    void setPlayerContainerSettlements(PlayerContainerSettlementPersistenceService service) {
        this.playerContainerSettlements = service;
    }

    @Autowired(required = false)
    void setGeneratedMinecartLootResolutions(
            GeneratedChestMinecartLootResolutionService service) {
        this.generatedMinecartLootResolutions = service;
    }

    @Autowired(required = false)
    void setGeneratedArmorStandEquipmentSettlements(
            ContainerSettlementPersistenceService service) {
        this.generatedArmorStandEquipmentSettlements = service;
    }

    public WorldEngineManager(GameTransport broadcaster, SessionRegistry registry,
            WorldBlockDiffRepository diffRepository, BlockDiffBuffer blockDiffBuffer,
            BlockDiffFlusher blockDiffFlusher, PersistenceExecutor persistenceExecutor,
            PlayerWorldStateService stateService, EngineProperties properties,
            ChestPersistenceService chestPersistence, FurnacePersistenceService furnacePersistence,
            CampfirePersistenceService campfirePersistence, MobPersistenceService mobPersistence,
            MobMutationJournalService mobMutationJournals,
            WorldTimePersistenceService worldTimePersistence) {
        this(broadcaster, registry, diffRepository, blockDiffBuffer, blockDiffFlusher,
                persistenceExecutor, stateService, properties, chestPersistence,
                furnacePersistence, campfirePersistence, mobPersistence, mobMutationJournals,
                worldTimePersistence, null, null);
    }

    public WorldEngineManager(GameTransport broadcaster, SessionRegistry registry,
            WorldBlockDiffRepository diffRepository, BlockDiffBuffer blockDiffBuffer,
            BlockDiffFlusher blockDiffFlusher, PersistenceExecutor persistenceExecutor,
            PlayerWorldStateService stateService, EngineProperties properties,
            ChestPersistenceService chestPersistence, FurnacePersistenceService furnacePersistence,
            CampfirePersistenceService campfirePersistence, MobPersistenceService mobPersistence,
            MobMutationJournalService mobMutationJournals,
            WorldTimePersistenceService worldTimePersistence,
            WorldMapPersistenceService worldMapPersistence) {
        this(broadcaster, registry, diffRepository, blockDiffBuffer, blockDiffFlusher,
                persistenceExecutor, stateService, properties, chestPersistence,
                furnacePersistence, campfirePersistence, mobPersistence, mobMutationJournals,
                worldTimePersistence, worldMapPersistence, null);
    }

    public WorldEngineManager(GameTransport broadcaster, SessionRegistry registry,
            WorldBlockDiffRepository diffRepository, BlockDiffBuffer blockDiffBuffer,
            BlockDiffFlusher blockDiffFlusher, PersistenceExecutor persistenceExecutor,
            PlayerWorldStateService stateService, EngineProperties properties,
            ChestPersistenceService chestPersistence, FurnacePersistenceService furnacePersistence,
            CampfirePersistenceService campfirePersistence, MobPersistenceService mobPersistence,
            MobMutationJournalService mobMutationJournals,
            WorldTimePersistenceService worldTimePersistence,
            WorldMapPersistenceService worldMapPersistence,
            FinalCarrierPersistenceService finalCarrierPersistence) {
        this(broadcaster, registry, diffRepository, blockDiffBuffer, blockDiffFlusher,
                persistenceExecutor, stateService, properties, chestPersistence,
                furnacePersistence, campfirePersistence, mobPersistence, mobMutationJournals,
                worldTimePersistence, worldMapPersistence, finalCarrierPersistence,
                new InMemoryCanonicalWorldgenStore(), null);
    }

    public WorldEngineManager(GameTransport broadcaster, SessionRegistry registry,
            WorldBlockDiffRepository diffRepository, BlockDiffBuffer blockDiffBuffer,
            BlockDiffFlusher blockDiffFlusher, PersistenceExecutor persistenceExecutor,
            PlayerWorldStateService stateService, EngineProperties properties,
            ChestPersistenceService chestPersistence, FurnacePersistenceService furnacePersistence,
            CampfirePersistenceService campfirePersistence, MobPersistenceService mobPersistence,
            MobMutationJournalService mobMutationJournals,
            WorldTimePersistenceService worldTimePersistence,
            WorldMapPersistenceService worldMapPersistence,
            FinalCarrierPersistenceService finalCarrierPersistence,
            CanonicalWorldgenStore canonicalWorldgenStore) {
        this(broadcaster, registry, diffRepository, blockDiffBuffer, blockDiffFlusher,
                persistenceExecutor, stateService, properties, chestPersistence,
                furnacePersistence, campfirePersistence, mobPersistence, mobMutationJournals,
                worldTimePersistence, worldMapPersistence, finalCarrierPersistence,
                canonicalWorldgenStore, null);
    }

    public WorldEngineManager(GameTransport broadcaster, SessionRegistry registry,
            WorldBlockDiffRepository diffRepository, BlockDiffBuffer blockDiffBuffer,
            BlockDiffFlusher blockDiffFlusher, PersistenceExecutor persistenceExecutor,
            PlayerWorldStateService stateService, EngineProperties properties,
            ChestPersistenceService chestPersistence, FurnacePersistenceService furnacePersistence,
            CampfirePersistenceService campfirePersistence, MobPersistenceService mobPersistence,
            MobMutationJournalService mobMutationJournals,
            WorldTimePersistenceService worldTimePersistence,
            WorldMapPersistenceService worldMapPersistence,
            FinalCarrierPersistenceService finalCarrierPersistence,
            CanonicalWorldgenStore canonicalWorldgenStore,
            GeneratedStructureEntityMutationCoordinator generatedCushionMutations) {
        this(broadcaster, registry, diffRepository, blockDiffBuffer, blockDiffFlusher,
                persistenceExecutor, stateService, properties, chestPersistence,
                furnacePersistence, campfirePersistence, mobPersistence, mobMutationJournals,
                worldTimePersistence, worldMapPersistence, finalCarrierPersistence,
                canonicalWorldgenStore, generatedCushionMutations, null, false);
    }

    @Autowired
    public WorldEngineManager(GameTransport broadcaster, SessionRegistry registry,
            WorldBlockDiffRepository diffRepository, BlockDiffBuffer blockDiffBuffer,
            BlockDiffFlusher blockDiffFlusher, PersistenceExecutor persistenceExecutor,
            PlayerWorldStateService stateService, EngineProperties properties,
            ChestPersistenceService chestPersistence, FurnacePersistenceService furnacePersistence,
            CampfirePersistenceService campfirePersistence, MobPersistenceService mobPersistence,
            MobMutationJournalService mobMutationJournals,
            WorldTimePersistenceService worldTimePersistence,
            WorldMapPersistenceService worldMapPersistence,
            FinalCarrierPersistenceService finalCarrierPersistence,
            CanonicalWorldgenStore canonicalWorldgenStore,
            GeneratedStructureEntityMutationCoordinator generatedCushionMutations,
            EnchantingPersistenceService enchantingPersistence) {
        this(broadcaster, registry, diffRepository, blockDiffBuffer, blockDiffFlusher,
                persistenceExecutor, stateService, properties, chestPersistence,
                furnacePersistence, campfirePersistence, mobPersistence, mobMutationJournals,
                worldTimePersistence, worldMapPersistence, finalCarrierPersistence,
                canonicalWorldgenStore, generatedCushionMutations,
                enchantingPersistence, true);
    }

    private WorldEngineManager(GameTransport broadcaster, SessionRegistry registry,
            WorldBlockDiffRepository diffRepository, BlockDiffBuffer blockDiffBuffer,
            BlockDiffFlusher blockDiffFlusher, PersistenceExecutor persistenceExecutor,
            PlayerWorldStateService stateService, EngineProperties properties,
            ChestPersistenceService chestPersistence, FurnacePersistenceService furnacePersistence,
            CampfirePersistenceService campfirePersistence, MobPersistenceService mobPersistence,
            MobMutationJournalService mobMutationJournals,
            WorldTimePersistenceService worldTimePersistence,
            WorldMapPersistenceService worldMapPersistence,
            FinalCarrierPersistenceService finalCarrierPersistence,
            CanonicalWorldgenStore canonicalWorldgenStore,
            GeneratedStructureEntityMutationCoordinator generatedCushionMutations,
            EnchantingPersistenceService enchantingPersistence,
            boolean productionWiring) {
        this.context = new EngineContext(broadcaster, registry, diffRepository, blockDiffBuffer,
                blockDiffFlusher, persistenceExecutor, stateService, properties);
        this.chestPersistence = chestPersistence;
        this.furnacePersistence = furnacePersistence;
        this.campfirePersistence = campfirePersistence;
        this.enchantingPersistence = productionWiring
                ? java.util.Objects.requireNonNull(
                        enchantingPersistence, "enchanting persistence")
                : enchantingPersistence;
        this.mobPersistence = mobPersistence;
        this.mobMutationJournals = mobMutationJournals;
        this.worldTimePersistence = worldTimePersistence;
        this.worldMapPersistence = worldMapPersistence;
        this.finalCarrierPersistence = finalCarrierPersistence;
        this.canonicalWorldgenStore = java.util.Objects.requireNonNull(
                canonicalWorldgenStore, "canonical worldgen store");
        this.generatedCushionMutations = generatedCushionMutations;
    }

    /** Resolves and memoizes a noise-selected, canonical-checked spawn for one world. */
    public int[] worldSpawn(Long worldId, int seed) {
        if (customDimension(worldId)) {
            var arrival = dimensionRegistry.requireEnabled(dimensionTravel.dimensionFor(worldId)).arrival();
            dimensionProviders.require(dimensionTravel.dimensionFor(worldId));
            return new int[] {(int) Math.floor(arrival.x()), (int) Math.floor(arrival.y()),
                    (int) Math.floor(arrival.z())};
        }
        int[] spawn = canonicalSpawns.computeIfAbsent(worldId,
                ignored -> resolveCanonicalSpawn(worldId, seed));
        // The spawn neighbourhood is the one chunk window every first session demands. Centring
        // the prefetch spiral on it here means entry preparation replays committed products
        // instead of waiting behind their production.
        prewarmCanonicalSpawn(worldId, seed, spawn);
        return spawn.clone();
    }

    /**
     * 저장된 정본 스폰이 있으면 그것을 쓰고, 없을 때만 탐색한 뒤 새깁니다.
     *
     * <p>신규 월드는 노이즈로 후보를 고릅니다. 이전 탐색과 좌표가 달라질 수 있으므로 기존
     * 월드는 재계산하지 않고 저장된 스폰을 그대로 사용합니다.
     *
     * <p>제품 소스는 저장값을 쓸 때도 반드시 세웁니다. {@link #prewarmCanonicalSpawn} 의
     * 프리페치 스케줄러가 그 소스에 묶여 있어, 세우지 않으면 빠른 경로에서 프리워밍이 조용히
     * 꺼집니다.
     */
    private int[] resolveCanonicalSpawn(Long worldId, int seed) {
        CanonicalOriginChunkProductSource products = canonicalSource(worldId, seed);
        return CanonicalSpawnResolution.resolve(worldId, worldSpawnPersistence,
                () -> WorldSpawn.findSampled(seed, products));
    }

    private void prewarmCanonicalSpawn(Long worldId, int seed, int[] spawn) {
        if (spawn == null || spawn.length < 3) return;
        CanonicalChunkPrefetchScheduler scheduler = canonicalPrefetcher(worldId, seed);
        if (scheduler == null) return;
        scheduler.prewarm(Math.floorDiv(spawn[0], Blocks.CHUNK_X),
                Math.floorDiv(spawn[2], Blocks.CHUNK_Z));
    }

    public com.gameexpert.world.WorldGenerationProfile generationProfileFor(long worldId) {
        if (finalCarrierPersistence == null) throw new IllegalStateException("world profile service is missing");
        return finalCarrierPersistence.generationProfileFor(worldId);
    }

    private CanonicalOriginChunkProductSource canonicalSource(Long worldId, int seed) {
        CanonicalOriginChunkProductSource source = canonicalSources.computeIfAbsent(worldId,
                ignored -> {
                    long gameTime = worldTimePersistence == null
                            ? 0L : worldTimePersistence.loadGameTimeMcTicks(worldId);
                    if (finalCarrierPersistence == null) {
                        throw new IllegalStateException("canonical world profile service is missing");
                    }
                    return new CanonicalOriginChunkProductSource(canonicalWorldgenStore,
                            worldId, seed, finalCarrierPersistence.generationProfileFor(worldId), gameTime);
                });
        canonicalPrefetcher(worldId, seed);
        return source;
    }

    /**
     * Creates, or returns, the single prefetch scheduler bound to this world's product source. A
     * scheduler left over from a disposed runtime is replaced rather than reused: it would be
     * producing into the previous source's store.
     */
    private CanonicalChunkPrefetchScheduler canonicalPrefetcher(Long worldId, int seed) {
        CanonicalOriginChunkProductSource source = canonicalSources.get(worldId);
        if (source == null) return null;
        List<CanonicalChunkPrefetchScheduler> replaced = new ArrayList<>(1);
        CanonicalChunkPrefetchScheduler scheduler = canonicalPrefetchers.compute(worldId,
                (ignored, existing) -> {
                    if (existing != null && existing.boundSource() == source) return existing;
                    if (existing != null) replaced.add(existing);
                    return new CanonicalChunkPrefetchScheduler(source, worldId, seed);
                });
        for (CanonicalChunkPrefetchScheduler stale : replaced) stale.close();
        return scheduler;
    }

    /** Closes the scheduler bound to {@code source}; a scheduler for a newer source is retained. */
    private void closeCanonicalPrefetcher(Long worldId, CanonicalOriginChunkProductSource source) {
        CanonicalChunkPrefetchScheduler scheduler = canonicalPrefetchers.get(worldId);
        if (scheduler == null) return;
        if (source != null && scheduler.boundSource() != source) return;
        if (canonicalPrefetchers.remove(worldId, scheduler)) scheduler.close();
    }

    /** 입장 결과: welcome에 실을 현재 월드 시간·다른 접속자 pose·현재 몹·드랍 아이템·보트 스냅샷. */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    public static class JoinResult {
        private final PlayerWorldState selfState;
        private final long worldTime;
        private final long dayCount;
        private final List<PlayerPose> otherPlayers;
        private final List<MobSpawnDto> mobs;
        private final List<ItemEntityDto> items;
        private final List<ProjectileSpawn> projectiles;
        private final List<BoatDto> boats;
        private final List<CushionDto> cushions;
        private final List<FireBlock> burningBlocks;
        private final long fireRevision;
        private final List<CampfireSnapshot> campfires;
        private final List<com.gameexpert.ws.dto.WsMessages.ShelfUpdate> shelves;
        private final WeatherState weather;
        /** 런타임이 보유한 권위 난이도. 핸드셰이크 속성이 낡아도 이 값이 welcome 에 실린다. */
        private final Difficulty difficulty;
        /** [SURV-X] 입장 시점의 경험치 구슬 스냅샷. */
        private final List<XpOrbDto> xpOrbs;
        private final List<PrimedTntDto> primedTnt;
        private final List<GeneratedEntitySnapshot> generatedEntities;
        /** Internal barrier cursor paired atomically with generatedEntities; never serialized. */
        private final long generatedEntitySequence;
    }

    /** WS 스레드에서 입장 처리: 저장 상태 복원 → 최신 런타임 확보 → 플레이어 게시 → welcome 재료 반환. */
    public JoinResult onPlayerJoin(Long worldId, int seed, Difficulty difficulty, Long playerId,
            String nickname, String connectionId, double spawnX, double spawnY, double spawnZ) {
        return onDimensionJoin(new com.gameexpert.state.service.PlayerDimensionIdentity(worldId, worldId, 0),
                seed, difficulty, playerId, nickname, connectionId, spawnX, spawnY, spawnZ);
    }

    public JoinResult onDimensionJoin(com.gameexpert.state.service.PlayerDimensionIdentity identity,
            int seed, Difficulty difficulty, Long playerId, String nickname, String connectionId,
            double spawnX, double spawnY, double spawnZ) {
        Long worldId = identity.runtimeWorldId();
        JoinAttempt attempt = new JoinAttempt(worldId, seed, difficulty, playerId, nickname,
                connectionId, spawnX, spawnY, spawnZ, identity);
        while (true) {
            WorldRuntime disposing;
            synchronized (lifecycleLocks.computeIfAbsent(worldId, ignored -> new Object())) {
                if (deletingWorlds.contains(worldId)) {
                    throw new ConflictException("WORLD_IN_USE");
                }
                JoinResult result = attempt.tryJoin();
                if (result != null) {
                    return result;
                }
                disposing = attempt.pendingDisposal;
            }
            // 폐기 대기는 수명주기 락 밖에서 한다. 최종 저장이 오래 걸려도 같은 월드의 삭제 예약이나
            // 다른 입장 시도가 이 스레드 뒤에 무한정 줄서지 않는다.
            if (!disposing.awaitDisposal()) {
                throw new ConflictException("WORLD_DISPOSAL_STALLED");
            }
            runtimes.remove(worldId, disposing);
        }
    }

    /**
     * 한 입장 요청의 재시도 상태입니다. 퇴장 스냅샷·DB 상태는 첫 시도에서 한 번만 확정하므로,
     * 폐기 대기 뒤 재시도해도 아직 커밋되지 않은 퇴장 상태를 잃지 않습니다.
     */
    private final class JoinAttempt {
        private final Long worldId;
        private final int seed;
        private final Difficulty difficulty;
        private final Long playerId;
        private final String nickname;
        private final String connectionId;
        private final double spawnX;
        private final double spawnY;
        private final double spawnZ;
        private PlayerWorldState state;
        private PlayerInventory inventory;
        private WorldRuntime pendingDisposal;
        private final com.gameexpert.state.service.PlayerDimensionIdentity identity;

        private JoinAttempt(Long worldId, int seed, Difficulty difficulty, Long playerId,
                String nickname, String connectionId, double spawnX, double spawnY, double spawnZ,
                com.gameexpert.state.service.PlayerDimensionIdentity identity) {
            this.identity = identity;
            this.worldId = worldId;
            this.seed = seed;
            this.difficulty = Difficulty.orDefault(difficulty);
            this.playerId = playerId;
            this.nickname = nickname;
            this.connectionId = connectionId;
            this.spawnX = spawnX;
            this.spawnY = spawnY;
            this.spawnZ = spawnZ;
        }

        /** 수명주기 락 안에서 한 번 시도합니다. 폐기 대기가 필요하면 null을 돌려줍니다. */
        private JoinResult tryJoin() {
            long traceStart = JOIN_STAGE_TRACE ? System.nanoTime() : 0L;
            resolvePersistedState();
            long traceStateResolved = JOIN_STAGE_TRACE ? System.nanoTime() : 0L;
            pendingDisposal = null;
            WorldRuntime runtime = runtimes.computeIfAbsent(worldId, id -> {
                // Only a replacement runtime reloads persisted diffs. Flush exactly at that construction
                // boundary so an explicit AIR tombstone cannot be missed, without blocking every later join.
                boolean combinedCheckpoint = primedTntPersistence != null;
                if (combinedCheckpoint) {
                    context.blockDiffFlusher().claimCombinedCheckpoint(id);
                }
                try {
                    // claim 뒤의 persistence-writer 장벽이 이미 제출된 독립 flush까지 모두 끝낸다.
                    context.blockDiffFlusher().flushWorldBlocking(id);
                    return createRuntime(id, seed, difficulty);
                } catch (RuntimeException | Error exception) {
                    if (combinedCheckpoint) {
                        context.blockDiffFlusher().releaseCombinedCheckpoint(id);
                    }
                    throw exception;
                }
            });
            long traceRuntimeReady = JOIN_STAGE_TRACE ? System.nanoTime() : 0L;
            // WorldSpawn.find already ran on this non-tick connection path. Retain that deterministic result so
            // respawn never regenerates a full terrain chunk inside the 100 ms world-owner budget.
            runtime.initializeWorldSpawn(spawnX, spawnY, spawnZ);
            PlayerTickState added = runtime.addDimensionPlayer(playerId, nickname, connectionId,
                    state.getPosX(), state.getPosY(), state.getPosZ(),
                    state.getYaw(), state.getPitch(), state.getHealth(), inventory,
                    state.getSpawnX(), state.getSpawnY(), state.getSpawnZ(),
                    state.hungerOrFull(), state.saturationMilliOrDefault(),
                    state.xpTotalOrZero(), state.enchantSeedOrNull(), identity);
            // [PHANTOM] 불면 시간은 addPlayer 서명을 넓히지 않고 복원한다 — 그 서명은 이미
            // 네 겹 호환 오버로드 사슬이라, 늘리면 전 호출부가 흔들린다.
            if (added != null) {
                added.restoreTimeSinceRest(state.timeSinceRestOrZero());
                added.restoreStrawBedSleepCount(state.sleepInStrawBedOrZero());
                added.restoreStatusEffects(state.statusEffectsSnapshot());
                added.restoreEffectClocks(state.effectClocksSnapshot());
                added.restoreFireState(state.getFireTicks(), state.getFireDamageAccum());
                // [ENDER-SHULKER] 엔더 상자 27칸도 같은 이유로 서명 밖에서 복원한다.
                restoreEnderChestInto(added, state);
                added.inventory().setOffhand(state.offhandSnapshot());
                if (authorityEvidenceRuntimeCallbacks != null) {
                    authorityEvidenceRuntimeCallbacks.sessionJoined(
                            worldId, nickname, connectionId);
                }
            }
            long tracePlayerAdded = JOIN_STAGE_TRACE ? System.nanoTime() : 0L;
            if (added == null) {
                // 폐기 실패로 격리된 월드는 기다려도 열리지 않는다. 상한만큼 붙잡지 않고 즉시 거부한다.
                if (runtime.isDisposalStalled()) {
                    throw new ConflictException("WORLD_DISPOSAL_STALLED");
                }
                // A disposed runtime remains registered until its final writer barrier succeeds.  A reconnect must
                // not remove it early and reload stale day/block/entity state while a failed final write is retrying.
                pendingDisposal = runtime;
                return null;
            }
            JoinResult joined;
            try {
                joined = completeJoin(runtime);
            } catch (RuntimeException | Error failure) {
                runtime.requestPlayerLeave(nickname, connectionId);
                throw failure;
            }
            if (JOIN_STAGE_TRACE) {
                long done = System.nanoTime();
                JOIN_TRACE_LOG.info("join internals: world={} nickname={} 저장상태복원+{}ms 런타임생성+{}ms"
                                + " 플레이어추가+{}ms welcome스냅샷+{}ms",
                        worldId, nickname, (traceStateResolved - traceStart) / 1_000_000L,
                        (traceRuntimeReady - traceStart) / 1_000_000L,
                        (tracePlayerAdded - traceStart) / 1_000_000L,
                        (done - traceStart) / 1_000_000L);
            }
            return joined;
        }

        private void resolvePersistedState() {
            if (state != null) return;
            // 퇴장 저장은 DB 쓰기 스레드에서 비동기로 처리됩니다. 같은 프로세스에서 즉시 재접속하면
            // 커밋 전 DB 값을 읽을 수 있으므로, DB 조회 전에 퇴장 순간 스냅샷을 먼저 확보합니다.
            PlayerSnapshot departed = identity.travelRevision() == 0
                    ? takeDeparted(worldId, nickname) : null;
            PlayerWorldState resolved = dimensionTravel == null
                    ? context.stateService().findOrCreate(playerId, worldId, spawnX, spawnY, spawnZ)
                    : context.stateService().findOrCreateDimension(playerId, identity, spawnX, spawnY, spawnZ);
            if (departed != null) {
                departed.applyTo(resolved);
            }
            inventory = departed == null ? restoreInventory(resolved) : departed.inventory();
            state = resolved;
        }

        private JoinResult completeJoin(WorldRuntime runtime) {
            return joinResult(runtime, state, nickname);
        }
    }

    private JoinResult joinResult(WorldRuntime runtime, PlayerWorldState state, String nickname) {
        runtime.prepareWelcomeSnapshotForJoin(nickname);
        WorldTickLoop.WelcomeFireSnapshot fire = runtime.tickLoop().welcomeFireSnapshot();
        WorldRuntime.GeneratedEntityWelcomeView generated = runtime.generatedEntityWelcomeView();
        JoinResult result = new JoinResult(state, runtime.worldTime(), runtime.dayCount(), runtime.poses(nickname),
                runtime.mobSystem().welcomeSnapshot(), runtime.itemSystem().welcomeSnapshot(),
                runtime.mobSystem().welcomeProjectileSnapshot(),
                runtime.boatSystem().welcomeSnapshot(), runtime.cushionSystem().welcomeSnapshot(),
                fire.blocks(), fire.revision(),
                runtime.campfireSnapshots(), runtime.shelfSnapshots(), runtime.weatherSystem().snapshot(),
                runtime.difficulty(), runtime.xpOrbSystem().welcomeSnapshot(),
                runtime.tickLoop().primedTntSnapshot(),
                generated.snapshots(), generated.sequence());
        runtime.start();
        return result;
    }

    /**
     * 삭제 트랜잭션과 새 입장이 같은 월드에서 서로 추월하지 못하게 하는 수명주기 예약입니다.
     * 접속자가 없는 유예 런타임은 마지막 저장 장벽까지 비운 뒤 삭제를 허용합니다.
     */
    public boolean beginWorldDeletion(Long worldId) {
        WorldRuntime idleRuntime;
        synchronized (lifecycleLocks.computeIfAbsent(worldId, ignored -> new Object())) {
            if (deletingWorlds.contains(worldId)) return false;
            idleRuntime = runtimes.get(worldId);
            if (idleRuntime != null && !idleRuntime.isEmpty()) return false;
            deletingWorlds.add(worldId);
            if (idleRuntime != null) idleRuntime.dispose();
        }
        // 대기는 락 밖에서 한다. 상한을 넘겨 폐기가 격리되면 마지막 저장이 아직 디스크에 닿지 않은
        // 상태이므로 삭제를 진행하지 않고 예약을 되돌린다(격리 해제 뒤 다시 시도할 수 있다).
        if (idleRuntime != null && !idleRuntime.awaitDisposal()) {
            endWorldDeletion(worldId);
            return false;
        }
        return true;
    }

    public void endWorldDeletion(Long worldId) {
        synchronized (lifecycleLocks.computeIfAbsent(worldId, ignored -> new Object())) {
            deletingWorlds.remove(worldId);
        }
    }

    /**
     * 저장된 일반 인벤토리 36칸과 착용 방어구 4칸, 선택 슬롯을 복원합니다.
     * 인챈트 마스크 유실 회귀를 직접 검증할 수 있도록 패키지 가시성입니다.
     */
    /**
     * [ENDER-SHULKER] 저장된 엔더 상자 밴드({@code ENDER_SLOT_BASE..+27})를 접속한 플레이어에게
     * 되돌린다. 이 밴드의 행이 없던 옛 세이브는 아무것도 복원하지 않아 빈 27칸이 된다 —
     * 그래서 {@code DATABASE_VERSION} 을 올리지 않는다.
     *
     * <p>{@code state} 는 이미 퇴장 스냅샷이 적용된 뒤이므로(resolvePersistedState), 커밋 전
     * 즉시 재접속도 이 한 경로로 같은 27칸을 본다.
     */
    static void restoreEnderChestInto(PlayerTickState player, PlayerWorldState state) {
        int slots = PlayerInventory.ENDER_CHEST_SLOTS;
        short[] itemTypes = new short[slots];
        int[] counts = new int[slots];
        int[] durabilities = new int[slots];
        long[] enchantments = new long[slots];
        int[] mapIds = new int[slots];
        int[] shulkerIds = new int[slots];
        String[] bucketMobData = new String[slots];
        String[] itemComponentData = new String[slots];
        for (InventoryItem item : state.getInventory()) {
            int slot = item.getSlot() - PlayerInventory.ENDER_SLOT_BASE;
            if (slot < 0 || slot >= slots) continue;
            itemTypes[slot] = item.getItemType();
            counts[slot] = item.getItemCount();
            durabilities[slot] = item.getDurability() == null ? 0 : item.getDurability();
            enchantments[slot] = item.enchantmentMaskOrZero();
            mapIds[slot] = item.mapIdOrZero();
            shulkerIds[slot] = item.shulkerIdOrZero();
            bucketMobData[slot] = item.getBucketMobData();
            itemComponentData[slot] = item.getItemComponentData();
        }
        player.restoreEnderChest(
                itemTypes, counts, durabilities, enchantments, mapIds, shulkerIds,
                bucketMobData, itemComponentData);
    }

    static PlayerInventory restoreInventory(PlayerWorldState state) {
        short[] itemTypes = new short[PlayerInventory.SLOTS];
        int[] counts = new int[PlayerInventory.SLOTS];
        int[] durabilities = new int[PlayerInventory.SLOTS];
        // [SURV-X] 인챈트 마스크도 함께 복원한다. 여기서 빠뜨리면 재접속 때 인챈트가 조용히 사라진다.
        long[] enchantmentMasks = new long[PlayerInventory.SLOTS];
        int[] mapIds = new int[PlayerInventory.SLOTS];
        // [SHULKER-CONTENTS] 27칸 참조도 함께 복원한다. 여기서 빠뜨리면 재접속 때 셜커 상자가
        // 빈 상자로 되살아나고 그 27칸은 어느 상자도 가리키지 않는 고아 행이 된다.
        int[] shulkerIds = new int[PlayerInventory.SLOTS];
        String[] bucketMobData = new String[PlayerInventory.SLOTS];
        String[] itemComponentData = new String[PlayerInventory.SLOTS];
        short[] equippedTypes = new short[ArmorSlot.values().length];
        int[] equippedDurabilities = new int[ArmorSlot.values().length];
        long[] equippedEnchantmentMasks = new long[ArmorSlot.values().length];
        String[] equippedItemComponentData = new String[ArmorSlot.values().length];
        for (InventoryItem item : state.getInventory()) {
            int slot = item.getSlot();
            if (slot >= 0 && slot < PlayerInventory.SLOTS) {
                itemTypes[slot] = item.getItemType();
                counts[slot] = item.getItemCount();
                durabilities[slot] = item.getDurability() == null ? 0 : item.getDurability();
                enchantmentMasks[slot] = item.enchantmentMaskOrZero();
                mapIds[slot] = item.mapIdOrZero();
                shulkerIds[slot] = item.shulkerIdOrZero();
                bucketMobData[slot] = item.getBucketMobData();
                itemComponentData[slot] = item.getItemComponentData();
            } else if (slot >= PlayerInventory.EQUIPPED_SLOT_BASE
                    && slot < PlayerInventory.PERSISTED_SLOTS) {
                int equipmentSlot = slot - PlayerInventory.EQUIPPED_SLOT_BASE;
                equippedTypes[equipmentSlot] = item.getItemType();
                equippedDurabilities[equipmentSlot] =
                        item.getDurability() == null ? 0 : item.getDurability();
                equippedEnchantmentMasks[equipmentSlot] = item.enchantmentMaskOrZero();
                equippedItemComponentData[equipmentSlot] = item.getItemComponentData();
            }
        }
        PlayerInventory restored = new PlayerInventory(
                itemTypes, counts, durabilities, enchantmentMasks, mapIds,
                shulkerIds, bucketMobData, itemComponentData,
                equippedTypes, equippedDurabilities,
                equippedEnchantmentMasks, equippedItemComponentData,
                state.offhandSnapshot(), state.getSelectedSlot(),
                state.getInventoryPersistenceRevision());
        return restored;
    }

    /** WS 스레드에서 퇴장을 요청하고 틱 소유자가 확정한 마지막 상태의 후처리만 연결합니다. */
    public void onPlayerLeave(Long worldId, String nickname, String connectionId) {
        WorldRuntime runtime = runtimes.get(worldId);
        if (runtime == null) {
            return;
        }
        PlayerTickState removed = runtime.requestPlayerLeave(nickname, connectionId).join();
        if (removed != null) {
            if (authorityEvidenceRuntimeCallbacks != null) {
                authorityEvidenceRuntimeCallbacks.sessionLeft(
                        worldId, nickname, connectionId);
            }
            PlayerSnapshot snapshot = PlayerSnapshot.of(removed);
            Long playerId = removed.playerId();
            departedPlayers.computeIfAbsent(worldId, ignored -> new ConcurrentHashMap<>())
                    .put(nickname, snapshot);
            // TickLoop가 이미 이 exact state generation을 durable lane에 admit했다. 여기서는
            // 같은 generation에 후처리만 coalesce한다. 이 호출은 두 번째 writer transaction을
            // 제출하지 않으며, fast commit이면 Runtime이 callback을 즉시 완료한다.
            runtime.saveDepartedState(removed, () -> {
                context.stateService().evictRuntimeInventory(playerId, worldId);
                discardDeparted(worldId, nickname, snapshot);
            });
        }
        // 세션 0 이후 유예·폐기는 틱 루프가 담당합니다.
    }

    // ── WS 스레드가 보트 명령을 BoatSystem 큐에 넣는 진입점(enqueue only) ──
    /** 보트 설치는 핫바 선택과 같은 액션 큐를 지나 도착 순서대로 손을 잡은 뒤 보트 큐로 넘어간다. */
    public void placeBoat(Long worldId, String nickname, double x, double y, double z, double yaw,
            PlayerAction.Hand hand) {
        enqueue(worldId, new PlayerAction.PlaceBoat(nickname, x, y, z, yaw,
                hand == PlayerAction.Hand.OFFHAND ? PlayerAction.Hand.OFFHAND
                        : PlayerAction.Hand.MAIN));
    }

    public void boardBoat(Long worldId, String nickname, long boatId) {
        WorldRuntime runtime = runtimes.get(worldId);
        if (runtime != null) {
            runtime.boatSystem().enqueueBoard(nickname, boatId);
        }
    }

    public void leaveBoat(Long worldId, String nickname) {
        WorldRuntime runtime = runtimes.get(worldId);
        if (runtime != null) {
            runtime.boatSystem().enqueueLeave(nickname);
        }
    }

    public void boatPos(Long worldId, String nickname, long boatId,
            double x, double y, double z, double yaw) {
        WorldRuntime runtime = runtimes.get(worldId);
        if (runtime != null) {
            runtime.boatSystem().enqueuePos(nickname, boatId, x, y, z, yaw);
        }
    }

    public void breakBoat(Long worldId, String nickname, long boatId) {
        WorldRuntime runtime = runtimes.get(worldId);
        if (runtime != null) {
            runtime.boatSystem().enqueueBreak(nickname, boatId);
        }
    }

    /** 쿠션 설치도 {@link #placeBoat} 와 같이 액션 큐에서 도착 순서대로 손을 잡는다. */
    public void placeCushion(Long worldId, String nickname, double x, double y, double z,
            PlayerAction.Hand hand) {
        enqueue(worldId, new PlayerAction.PlaceCushion(nickname, x, y, z,
                hand == PlayerAction.Hand.OFFHAND ? PlayerAction.Hand.OFFHAND
                        : PlayerAction.Hand.MAIN));
    }

    public void sitCushion(Long worldId, String nickname, long cushionId) {
        WorldRuntime runtime = runtimes.get(worldId);
        if (runtime != null) runtime.cushionSystem().enqueueSit(nickname, cushionId);
    }

    public void leaveCushion(Long worldId, String nickname) {
        WorldRuntime runtime = runtimes.get(worldId);
        if (runtime != null) runtime.cushionSystem().enqueueLeave(nickname);
    }

    public void breakCushion(Long worldId, String nickname, long cushionId) {
        WorldRuntime runtime = runtimes.get(worldId);
        if (runtime != null) runtime.cushionSystem().enqueueBreak(nickname, cushionId);
    }

    /** WS 스레드가 액션을 큐에 넣는 단일 진입점. */
    public void enqueue(Long worldId, PlayerAction action) {
        WorldRuntime runtime = runtimes.get(worldId);
        if (runtime != null) {
            runtime.enqueue(action);
        }
    }

    /** welcome 직후 prepared spawn/resume 3×3 halo를 별도 client round-trip 없이 큐에 넣습니다. */
    public void enqueueSpawnChunkSnapshots(Long worldId, WebSocketSession session, double x, double z) {
        WorldRuntime runtime = runtimes.get(worldId);
        if (runtime != null) runtime.enqueueSpawnHaloSnapshots(session, x, z);
    }

    /** full snapshot 재요청은 world owner가 resident 상태만 복사하도록 전달합니다. */
    public void requestChunkSnapshot(Long worldId, WebSocketSession session, int chunkX, int chunkZ) {
        WorldRuntime runtime = runtimes.get(worldId);
        if (runtime != null) runtime.enqueueChunkSnapshotRequest(session, chunkX, chunkZ);
    }

    /** 스트리밍 범위에서 빠진 미완료 snapshot 요청을 좌표 단위로 취소합니다. */
    public void cancelChunkSnapshot(Long worldId, WebSocketSession session, int chunkX, int chunkZ) {
        WorldRuntime runtime = runtimes.get(worldId);
        if (runtime != null) runtime.cancelChunkSnapshotRequest(session, chunkX, chunkZ);
    }

    /** welcome/디버그용 현재 월드 시간(런타임 없으면 0). */
    public long worldTime(Long worldId) {
        WorldRuntime runtime = runtimes.get(worldId);
        return runtime == null ? 0 : runtime.worldTime();
    }

    /** /pos 조회용: 지정 플레이어의 서버 추적 위치(없으면 null). */
    public PlayerPose playerPose(Long worldId, String nickname) {
        WorldRuntime runtime = runtimes.get(worldId);
        return runtime == null ? null : runtime.pose(nickname);
    }

    /** 선택 핫바가 채워진 지도라면 현재 권위 전체 상태를 반환합니다. */
    public MapState selectedMapState(Long worldId, String nickname) {
        WorldRuntime runtime = runtimes.get(worldId);
        if (runtime == null) return null;
        PlayerTickState player = runtime.players().get(nickname);
        if (player == null) return null;
        PlayerInventory inventory = player.inventory();
        int slot = inventory.selectedSlot();
        if (!PlayerInventory.isFilledMapItem(inventory.itemType(slot))) return null;
        WorldMapData map = runtime.mapState(inventory.mapId(slot));
        return WorldTickLoop.mapStateMessage(map);
    }

    private WorldRuntime createRuntime(Long worldId, int seed, Difficulty difficulty) {
        com.gameexpert.cluster.WorldAuthority.requireRuntime(worldId);
        long traceCreateStart = JOIN_STAGE_TRACE ? System.nanoTime() : 0L;
        long traceLegacy = 0L;
        long traceConstructed = 0L;
        long traceFinalCarrier = 0L;
        if (primedTntPersistence != null) {
            primedTntPersistence.migrateLegacyPrimedBlocks(worldId);
        }
        if (animalSettlementPersistence != null) {
            animalSettlementPersistence.recoverPendingMobDeaths(worldId);
        }
        if (JOIN_STAGE_TRACE) traceLegacy = System.nanoTime();
        boolean custom = customDimension(worldId);
        com.gameexpert.terrain.ChunkProductSource products = custom
                ? new com.gameexpert.world.dimension.DimensionChunkProductSource(dimensionProviders,
                        dimensionTravel.dimensionFor(worldId), seed,
                        finalCarrierPersistence.generationProfileFor(worldId))
                : canonicalSource(worldId, seed);
        WorldRuntime runtime = new WorldRuntime(
                worldId, seed, context, chestPersistence, furnacePersistence,
                campfirePersistence, mobPersistence, worldTimePersistence, difficulty,
                products, worldMapPersistence);
        runtime.attachAuthorityEvidenceRuntimeCallbacks(authorityEvidenceRuntimeCallbacks);
        runtime.attachDimensionGateway(dimensionRegistry, dimensionProviders, dimensionGateway);
        runtime.attachVillagerSocietyPersistence(villagerSocietyPersistence);
        if (fleshNetherPersistence != null) runtime.attachFleshNether(fleshNetherPersistence, mobPersistence);
        if (fleshColonySettlements != null) runtime.attachFleshColonySettlements(fleshColonySettlements);
        runtime.attachGroundEntityPersistence(groundEntityPersistence);
        runtime.attachGroundMutationSettlements(groundMutationSettlements);
        runtime.attachEmptyMapSettlements(emptyMapSettlements);
        runtime.attachProjectilePersistence(projectilePersistence);
        if (JOIN_STAGE_TRACE) traceConstructed = System.nanoTime();
        runtime.installAnimalBlockTickPersistence(animalBlockTickPersistence);
        runtime.installAnimalSettlementPersistence(animalSettlementPersistence);
        runtime.installFrogColonyPersistence(frogColonyPersistence);
        runtime.installTrialPersistence(trialPersistence);
        runtime.attachPlayerContainerSettlements(playerContainerSettlements);
        runtime.installBrewingPersistence(brewingPersistence);
        if (enchantingPersistence != null) {
            runtime.installEnchantingPersistence(enchantingPersistence);
        }
        runtime.installBannerPersistence(bannerPersistence);
        runtime.installSignPersistence(signPersistence);
        if (custom) runtime.installEndGatewayPersistence(endGatewayPersistence);
        if (custom) runtime.installDragonFightPersistence(dragonFightPersistence);
        runtime.installBannerPlacementSettlements(bannerPlacementSettlements);
        runtime.installLecternPersistence(lecternPersistence);
        runtime.installJukeboxPersistence(jukeboxPersistence);
        runtime.installCrafterPersistence(crafterPersistence);
        runtime.installLecternMiningSettlements(lecternMiningSettlements);
        runtime.installPlayerBlockSettlements(playerBlockSettlements);
        runtime.installContentQaFixtureSettlements(contentQaFixtureSettlements);
        runtime.installCushionPersistence(cushionPersistence);
        runtime.installBoatPersistence(boatPersistence);
        runtime.installPlacedEntityPersistence(placedEntityPersistence);
        if (!custom) runtime.installFinalCarrierAuthorities(finalCarrierPersistence);
        if (JOIN_STAGE_TRACE) traceFinalCarrier = System.nanoTime();
        runtime.attachGeneratedCushionMutations(generatedCushionMutations);
        runtime.attachGeneratedMinecartLootResolutions(generatedMinecartLootResolutions);
        runtime.attachGeneratedArmorStandEquipmentSettlements(
                generatedArmorStandEquipmentSettlements);
        runtime.tickLoop().attachPrimedTntPersistence(primedTntPersistence);
        // 몹 변형 저널을 MySQL 저장소로 교체하고 복구한다. 첫 틱 전에 한 번만 일어난다.
        // DB 없는 단위 테스트 매니저는 런타임 기본 저장소를 그대로 쓴다.
        if (mobMutationJournals != null) {
            runtime.attachMobMutationJournalStore(mobMutationJournals.storeFor(worldId));
        }
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "world-tick-" + worldId + "-" + threadSeq.incrementAndGet());
            thread.setDaemon(true);
            // Initial chunk preparation is deliberately lower priority. The fixed-rate authority owner
            // must retain its 100 ms deadline without reducing simulation, spawning, or streaming work.
            thread.setPriority(Thread.NORM_PRIORITY + 1);
            return thread;
        });
        CanonicalOriginChunkProductSource source = custom ? null : canonicalSource(worldId, seed);
        runtime.attach(executor, () -> {
            runtimes.remove(worldId, runtime);
            // Drop the source first: a join racing this disposal then binds a fresh source and a
            // fresh scheduler instead of inheriting the one about to be closed.
            if (source != null) {
                canonicalSources.remove(worldId, source);
                closeCanonicalPrefetcher(worldId, source);
            }
            canonicalSpawns.remove(worldId);
        });
        if (JOIN_STAGE_TRACE) {
            long done = System.nanoTime();
            JOIN_TRACE_LOG.info("createRuntime: world={} 레거시복구+{}ms 런타임구성+{}ms"
                            + " final-carrier복구+{}ms 나머지+{}ms",
                    worldId, (traceLegacy - traceCreateStart) / 1_000_000L,
                    (traceConstructed - traceCreateStart) / 1_000_000L,
                    (traceFinalCarrier - traceCreateStart) / 1_000_000L,
                    (done - traceCreateStart) / 1_000_000L);
        }
        return runtime;
    }

    /** Quarantine only this root's runtimes; never stop unrelated worlds or another process. */
    public void abandonClusterAuthority(long root) {
        for (java.util.Map.Entry<Long, WorldRuntime> entry : List.copyOf(runtimes.entrySet())) {
            if (com.gameexpert.cluster.WorldAuthority.rootOf(entry.getKey()) != root) continue;
            entry.getValue().abandonClusterAuthority();
            closeCanonicalPrefetcher(entry.getKey(), null);
            canonicalSources.remove(entry.getKey());
            canonicalSpawns.remove(entry.getKey());
        }
    }

    @PreDestroy
    public void shutdown() {
        for (WorldRuntime runtime : List.copyOf(runtimes.values())) {
            runtime.disposeForShutdown();
        }
        runtimes.clear();
        for (Long worldId : List.copyOf(canonicalPrefetchers.keySet())) {
            closeCanonicalPrefetcher(worldId, null);
        }
        canonicalSources.clear();
        canonicalSpawns.clear();
        departedPlayers.clear();
        deletingWorlds.clear();
    }

    private PlayerSnapshot takeDeparted(Long worldId, String nickname) {
        ConcurrentHashMap<String, PlayerSnapshot> world = departedPlayers.get(worldId);
        if (world == null) {
            return null;
        }
        PlayerSnapshot snapshot = world.remove(nickname);
        if (world.isEmpty()) {
            departedPlayers.remove(worldId, world);
        }
        return snapshot;
    }

    private void discardDeparted(Long worldId, String nickname, PlayerSnapshot snapshot) {
        ConcurrentHashMap<String, PlayerSnapshot> world = departedPlayers.get(worldId);
        if (world == null) {
            return;
        }
        world.remove(nickname, snapshot);
        if (world.isEmpty()) {
            departedPlayers.remove(worldId, world);
        }
    }

    /** DB 커밋 전 즉시 재접속에도 welcome과 새 런타임이 같은 퇴장 상태를 쓰게 하는 짧은 수명 스냅샷. */
    private static final class PlayerSnapshot {
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private final int health;
        private final short[] itemTypes;
        private final int[] counts;
        private final int[] durabilities;
        private final short[] equippedTypes;
        private final int[] equippedDurabilities;
        private final Integer spawnX;
        private final Integer spawnY;
        private final Integer spawnZ;
        // 허기(SURV-H)도 실어야 커밋 전 즉시 재접속이 만복으로 되감기지 않는다.
        private final int hunger;
        private final int saturationMilli;
        // [SURV-X] 인챈트 마스크와 누적 경험치도 같은 스냅샷에 실어 즉시 재접속의 되감기를 막는다.
        private final long[] enchantments;
        private final int[] mapIds;
        private final int[] shulkerIds;
        private final String[] bucketMobData;
        private final String[] itemComponentData;
        private final long[] equippedEnchantments;
        private final String[] equippedItemComponentData;
        private final int xpTotal;
        private final int enchantSeed;
        // [PHANTOM] 불면 시간도 실어야 커밋 전 즉시 재접속이 "방금 잔 것"으로 되감기지 않는다.
        private final long timeSinceRestMcTicks;
        private final List<StatusEffects.PersistentEffect> statusEffects;
        private final StatusEffects.PersistentPlayerEffectClocks effectClocks;
        private final int selectedSlot;
        private final int fireTicks;
        private final int fireAccum;
        // [ENDER-SHULKER] 엔더 상자 27칸도 같은 스냅샷에 실어야 커밋 전 즉시 재접속이
        // 빈 엔더 상자로 되감기지 않는다(인벤토리·허기·경험치와 같은 이유).
        private final ChestInventory.Snapshot enderChest;
        private final PlayerInventory.StackSnapshot offhand;
        private final long inventoryPersistenceRevision;

        private PlayerSnapshot(PlayerTickState state) {
            PlayerInventory.PersistenceSnapshot inventory =
                    state.inventory().persistenceSnapshot();
            this.x = state.x();
            this.y = state.y();
            this.z = state.z();
            this.yaw = state.yaw();
            this.pitch = state.pitch();
            this.health = state.health();
            this.itemTypes = inventory.itemTypes();
            this.counts = inventory.counts();
            this.durabilities = inventory.durabilities();
            this.enchantments = inventory.enchantments();
            this.mapIds = inventory.mapIds();
            this.shulkerIds = inventory.shulkerIds();
            this.bucketMobData = inventory.bucketMobData();
            this.itemComponentData = inventory.itemComponentData();
            this.equippedTypes = state.inventory().equippedTypes();
            this.equippedDurabilities = state.inventory().equippedDurabilities();
            this.equippedEnchantments = state.inventory().equippedEnchantments();
            this.equippedItemComponentData = state.inventory().equippedItemComponentData();
            this.spawnX = state.hasBedSpawn() ? state.bedSpawnX() : null;
            this.spawnY = state.hasBedSpawn() ? state.bedSpawnY() : null;
            this.spawnZ = state.hasBedSpawn() ? state.bedSpawnZ() : null;
            this.hunger = state.food();
            this.saturationMilli = state.saturationMilli();
            this.xpTotal = state.xpTotal();
            this.enchantSeed = state.enchantSeed();
            this.timeSinceRestMcTicks = state.timeSinceRestMcTicks();
            this.statusEffects = state.statusEffects().persistenceSnapshot();
            this.effectClocks = state.effectClocksSnapshot();
            this.selectedSlot = state.inventory().selectedSlot();
            this.fireTicks = state.fireTicks();
            this.fireAccum = state.fireAccum();
            this.enderChest = state.enderChest().snapshot();
            this.offhand = inventory.offhand();
            this.inventoryPersistenceRevision = state.inventory().revision();
        }

        static PlayerSnapshot of(PlayerTickState state) {
            return new PlayerSnapshot(state);
        }

        PlayerInventory inventory() {
            PlayerInventory restored = new PlayerInventory(
                    itemTypes, counts, durabilities, enchantments, mapIds,
                    shulkerIds, bucketMobData, itemComponentData,
                    equippedTypes, equippedDurabilities,
                    equippedEnchantments, equippedItemComponentData,
                    offhand, selectedSlot, inventoryPersistenceRevision);
            return restored;
        }

        int xpTotal() {
            return xpTotal;
        }

        void applyTo(PlayerWorldState state) {
            state.updatePosition(x, y, z, yaw, pitch);
            state.updateHealth(health);
            state.updateInventory(itemTypes, counts, durabilities, enchantments, mapIds,
                    shulkerIds, bucketMobData, itemComponentData, equippedTypes,
                    equippedDurabilities, equippedEnchantments, equippedItemComponentData);
            // [ENDER-SHULKER] 엔더 상자는 같은 테이블의 다른 슬롯 밴드라 별도 호출이다 —
            // 두 메서드가 서로의 밴드를 건드리지 않으므로 순서는 상관없다.
            state.updateEnderChest(enderChest.itemTypes(), enderChest.counts(),
                    enderChest.durabilities(), enderChest.enchantments(), enderChest.mapIds(),
                    enderChest.shulkerIds(), enderChest.bucketMobData(),
                    enderChest.itemComponentData());
            state.updateOffhand(offhand);
            state.updateSpawn(spawnX, spawnY, spawnZ);
            state.updateHunger(hunger, saturationMilli);
            state.updateXpTotal(xpTotal);
            state.updateEnchantSeed(enchantSeed);
            state.updateTimeSinceRest(timeSinceRestMcTicks);
            state.updateStatusEffects(statusEffects);
            state.updateEffectClocks(effectClocks);
            state.updateSelectedSlot(selectedSlot);
            state.updateFireState(fireTicks, fireAccum);
        }
    }
}
