package com.gameexpert.engine;

import com.gameexpert.authority.versioned.ProducerAuthorities;

import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

import com.gameexpert.block.entity.WorldBlockDiff;
import com.gameexpert.block.BlockStateStorage;
import com.gameexpert.block.snapshot.CarrierStateProjection;
import com.gameexpert.block.snapshot.ChunkSnapshot;
import com.gameexpert.block.snapshot.ChunkSnapshotCodec;
import com.gameexpert.block.snapshot.ChunkSnapshotResult;
import com.gameexpert.block.snapshot.ChunkVersionLedger;
import com.gameexpert.engine.diagnostics.ArchitectureTurnEvent;
import com.gameexpert.engine.diagnostics.WorldWorkEvent;
import com.gameexpert.banner.service.BannerBlockData;
import com.gameexpert.banner.service.BannerBlockPersistenceService;
import com.gameexpert.banner.service.BannerPlacementSettlementService;
import com.gameexpert.sign.dto.SignBlockData;
import com.gameexpert.sign.service.SignBlockPersistenceService;
import com.gameexpert.lectern.service.LecternPersistenceService;
import com.gameexpert.lectern.service.LecternMiningSettlementService;
import com.gameexpert.campfire.service.CampfirePersistenceService;
import com.gameexpert.cushion.service.CushionPersistenceService;
import com.gameexpert.chest.service.ChestPersistenceService;
import com.gameexpert.furnace.service.FurnacePersistenceService;
import com.gameexpert.enchanting.service.EnchantingPersistenceService;
import com.gameexpert.brewing.service.BrewingPersistenceService;
import com.gameexpert.ground.service.GroundEntityPersistenceService;
import com.gameexpert.ground.dto.GroundMutationCommand;
import com.gameexpert.ground.entity.WorldGroundRevision;
import com.gameexpert.ground.service.GroundMutationOutcome;
import com.gameexpert.ground.service.GroundMutationSettlementService;
import com.gameexpert.ground.service.GroundPickupAudit;
import com.gameexpert.common.LongObjectOpenHashMap;
import com.gameexpert.projectile.service.ProjectilePersistenceService;
import com.gameexpert.qa.AuthorityEvidenceRuntimeCallbacks;
import com.gameexpert.state.service.inventory.PlayerInventoryMutationSnapshot;
import com.gameexpert.state.service.inventory.PlayerContainerSettlementCommand;
import com.gameexpert.state.service.inventory.InventoryMutationTarget;
import com.gameexpert.state.service.inventory.PlayerContainerSettlementPersistenceService;
import com.gameexpert.container.persistence.ContainerSettlementPersistenceService;
import com.gameexpert.state.service.PlayerWorldStateService;
import com.gameexpert.map.dto.WorldMapData;
import com.gameexpert.map.service.WorldMapPersistenceService;
import com.gameexpert.mob.dto.MobPersistenceSnapshot;
import com.gameexpert.mob.dto.RewardDeliverySnapshot;
import com.gameexpert.mob.dto.RewardPlayerSnapshot;
import com.gameexpert.mob.dto.RewardSettlement;
import com.gameexpert.mob.dto.VillagerTradeSnapshot;
import com.gameexpert.mob.service.MobPersistenceService;
import com.gameexpert.mob.service.VillagerSocietyPersistenceService;
import com.gameexpert.engine.mob.MobMutationJournal;
import com.gameexpert.engine.mob.Mob;
import com.gameexpert.engine.mob.MobRuntime;
import com.gameexpert.engine.mob.MobType;
import com.gameexpert.engine.persistence.animal.AnimalBlockTickPersistenceService;
import com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService;
import com.gameexpert.engine.persistence.finalcarrier.archaeology.ArchaeologyBrushableAggregate;
import com.gameexpert.engine.persistence.finalcarrier.archaeology.ArchaeologyLootResolver;
import com.gameexpert.engine.persistence.finalcarrier.FinalCarrierDurableStateException;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootContainerKind;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootFirstOpenResult;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.StructureEntityAggregate;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.GeneratedChestMinecartLootResolutionService;
import com.gameexpert.engine.persistence.tick.FinalCarrierTickScheduler;
import com.gameexpert.engine.persistence.tick.Mc263FinalCarrierTickSemantics;
import com.gameexpert.engine.persistence.finalcarrier.spawner.SpawnerAggregate;
import com.gameexpert.engine.persistence.finalcarrier.blockentity.FinalCarrierBlockEntityPlan;
import com.gameexpert.frog.persistence.service.FrogColonyPersistenceService;
import com.gameexpert.engine.qa.ContentQaFixturePlan;
import com.gameexpert.engine.qa.ContentQaFixtureSettlementService;
import com.gameexpert.engine.qa.DenseWorldPerformanceFixturePlan;
import com.gameexpert.engine.effect.StatusEffects;
import com.gameexpert.engine.inventory.InventoryRules;
import com.gameexpert.engine.inventory.ItemComponentData;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.raid.RaidRewardReceipt;
import com.gameexpert.engine.trial.TrialVaultContract;
import com.gameexpert.api.trial.TrialStorage;
import com.gameexpert.engine.structure.AcceptedStructureSite;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.ChunkGenerator;
import com.gameexpert.terrain.ChunkProductSource;
import com.gameexpert.terrain.TerrainAccessor;
import com.gameexpert.terrain.mc.feature.Mc263BeeSidecarPlan;
import com.gameexpert.authority.versioned.NeutralFinalChunk;
import com.gameexpert.terrain.mc.loot.Mc263ProductionContextCatalog;
import com.gameexpert.terrain.mc.loot.Mc263ProductionContextCatalogPins;
import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority;
import com.gameexpert.world.service.WorldTimePersistenceService;
import com.gameexpert.api.SessionRegistry;
import com.gameexpert.ws.GameTransport.ChunkSnapshotBarrier;
import com.gameexpert.ws.dto.WsMessages.Block;
import com.gameexpert.ws.dto.WsMessages.BlockUpdate;
import com.gameexpert.ws.dto.WsMessages.ChunkSnapshotUnavailable;
import com.gameexpert.ws.dto.WsMessages.BoatDto;
import com.gameexpert.ws.dto.WsMessages.PlayerPose;
import com.gameexpert.ws.dto.WsMessages.CampfireSlot;
import com.gameexpert.ws.dto.WsMessages.CampfireSnapshot;
import com.gameexpert.ws.dto.WsMessages.BannerBlockState;
import com.gameexpert.ws.dto.WsMessages.BannerPatternLayer;
import com.gameexpert.ws.dto.WsMessages.BannerRemove;
import com.gameexpert.ws.dto.WsMessages.BannerUpdate;
import com.gameexpert.ws.dto.WsMessages.SignBlockState;
import com.gameexpert.ws.dto.WsMessages.SignOpen;
import com.gameexpert.ws.dto.WsMessages.SignRemove;
import com.gameexpert.ws.dto.WsMessages.SignUpdate;
import com.gameexpert.ws.dto.WsMessages.GeneratedEntityRemove;
import com.gameexpert.ws.dto.WsMessages.GeneratedEntitySpawn;
import com.gameexpert.ws.dto.WsMessages.GeneratedEntityUpdate;
import com.gameexpert.ws.dto.WsMessages.GeneratedEntitySnapshot;
import com.gameexpert.ws.dto.WsMessages.GeneratedEntityTarget;
import com.gameexpert.ws.dto.WsMessages.ArmorStandSnapshot;
import com.gameexpert.ws.dto.WsMessages.GeneratedCushionSnapshot;
import com.gameexpert.ws.dto.WsMessages.ChestMinecartSnapshot;

/**
 * [제공코드] 월드 하나의 서버 권위 런타임 상태(월드당 단일 틱 스레드 전용).
 *
 * <b>스레딩 불변식</b>:
 * <ul>
 *   <li>런타임 상태(플레이어 pose/체력/추적기, 유체 스케줄, 오버레이, 시계)는 <b>틱 스레드에서만 변경</b>합니다.</li>
 *   <li>WS 스레드는 {@link ActionQueue}에 enqueue만 합니다(채팅은 기존대로 즉시 브로드캐스트).
 *       예외적으로 입장 시 {@link #addPlayer}로 새 플레이어를 게시(publish)하고 welcome용 pose를 <i>읽기</i>만 합니다.</li>
 *   <li>DB 쓰기는 {@link PersistenceExecutor} 전용입니다(주기 저장·diff flush).</li>
 * </ul>
 * 몹/투사체 런타임(P6 예약)은 이 클래스에 필드를 추가하고 틱 순서 ③.5(유체 뒤, 환경 데미지 앞)에 끼웁니다.
 */
public final class WorldRuntime {

    private static final Logger log = LoggerFactory.getLogger(WorldRuntime.class);
    private static final Logger dropAudit = LoggerFactory.getLogger("gameexpert.audit.drop");
    private AuthorityEvidenceRuntimeCallbacks authorityEvidenceRuntimeCallbacks;
    private static final int GRACE_TICKS = 100; // 세션 0 이후 유예
    private volatile boolean keepLoadedWhenEmpty;
    private static final double BOAT_HALF_FOOTPRINT = 0.75;
    static final int DECORATION_UPDATES_PER_TICK = 96;
    private static final long STRUCTURE_APPLY_BUDGET_NANOS = 2_000_000L;
    /** Visible snapshot hydration may briefly spend more owner time so complete structures enter one snapshot. */
    private static final long SNAPSHOT_STRUCTURE_APPLY_BUDGET_NANOS = 8_000_000L;
    private static final long STRUCTURE_SEND_BUDGET_NANOS = 2_000_000L;
    /**
     * 준비된 활성화를 owner 턴에 적용하는 예산.
     *
     * <p>재접속 81장 계측에서 이 단계가 유일한 병목이었다. 플래닝은 청크당 0~1ms 로 사실상
     * 공짜이고(플래너 in-flight p50 0), 채택 대기도 p50 101ms 인데, 채택→표시준비가 p50 2.0초
     * ·최대 6.2초였다. 그 사이 적용 대기열은 18~47장으로 계속 차 있었고 틱당 개수 상한(64)에는
     * 한 번도 닿지 않았다. 즉 매 틱 할 일이 있는데 2ms 에서 끊긴 것이다. 8ms 는 같은 파일의
     * 스냅샷 구조물 적용 예산과 같은 값이며, 틱 예산(100ms)의 8%다.</p>
     *
     * <p>16ms 도 실측했는데 재접속 6.81초·콜드 28.0초로 8ms(6.82초·27.8초)와 같았다. 더 준다고
     * 더 나아가지 않으므로, 같은 성능에서 owner 턴을 덜 잡는 8ms 로 둔다.</p>
     *
     * <p>다만 이 수치는 빠른 개발 장비에서 잰 것이다. 코어가 느린 서버에서는 8ms 안에 들어가는
     * 활성화 수가 적어 진입이 길어지므로, {@code -Dwebcraft.activationDrainBudgetMs} 로 올릴 수
     * 있게 열어 둔다. 올리면 틱당 owner 턴을 더 잡으므로 틱 예산 초과와 함께 봐야 한다.</p>
     */
    private static final long ACTIVATION_DRAIN_BUDGET_NANOS =
            Math.max(1L, Long.getLong("webcraft.activationDrainBudgetMs", 8L)) * 1_000_000L;
    /** The tick only applies this many already-planned activation cells at most. */
    private static final int ACTIVATION_APPLICATIONS_PER_TICK = 64;
    /** One owner-side activation slice batches cheap committed cells while the outer 2 ms deadline remains hard. */
    private static final int ACTIVATION_CELLS_PER_SLICE = 64;
    /** A ready structure advances only this many candidates before another ready chunk gets a turn. */
    private static final int STRUCTURE_PLACEMENTS_PER_SLICE = 64;
    private static final int STREAMED_UPDATE_BATCH_LIMIT = 4096;
    private static final int MAX_PENDING_DECORATION_UPDATES = STREAMED_UPDATE_BATCH_LIMIT * 2;
    private static final int MAX_BLOCKS_PER_OUTBOUND_UPDATE = 512;
    /** The owner only captures this many immutable source references per tick; composition/encoding stay on workers. */
    private static final int SNAPSHOT_CAPTURES_PER_TICK = 8;
    /**
     * 청크 하나가 요청 접수부터 송신 완료까지 어느 단계에서 시간을 쓰는지 한 줄로 남기는 임시
     * 계측. 기본은 꺼져 있고 {@code -Dwebcraft.snapshotStageTrace=true} 로만 켠다. 배달 페이싱의
     * 상한이 어느 상수인지 확정되면 이 플래그와 스탬프는 통째로 지운다.
     */
    private static final boolean SNAPSHOT_STAGE_TRACE =
            Boolean.getBoolean("webcraft.snapshotStageTrace");
    /** 활성화 파이프라인 단계 인덱스. 같은 플래그로만 켜지는 임시 계측이다. */
    private static final int ACT_DEMAND = 0;
    private static final int ACT_PLAN_START = 1;
    private static final int ACT_PLAN_END = 2;
    private static final int ACT_ADOPTED = 3;
    private static final int ACT_BASE_READY = 4;
    private static final int ACT_STRUCTURE_READY = 5;
    private static final int ACT_STAGES = 6;
    /** O(1) cold-request admission only: this never scans, generates, hydrates, or sends on the owner. */
    private static final int SNAPSHOT_PREPARATION_ADMISSIONS_PER_TICK = 64;
    /**
     * The whole queued batch is drained before any of it is admitted so admission can follow
     * canonical ring order. This caps the per-turn sort; a cold entry demands ~81-200 coordinates.
     */
    private static final int SNAPSHOT_PREPARATION_BATCH_LIMIT = 1024;
    /** Admission telemetry cadence, matched to the production-progress line's 32-chunk cadence. */
    private static final int SNAPSHOT_ADMISSION_PROGRESS_INTERVAL = 32;
    /** Missing 3x3 tree-planning sources share the parallel snapshot lane but never expand the visible load count. */
    private static final int TREE_SOURCE_PREPARATION_ADMISSIONS_PER_TICK = 32;
    /** Includes queued, running and completed-but-unadopted snapshot/tree-source material. */
    private static final int MAX_PENDING_SNAPSHOT_PREPARATIONS =
            TREE_SOURCE_PREPARATION_ADMISSIONS_PER_TICK;
    /** Adopt one detached snapshot source before one O(1) source/version handoff, avoiding LRU churn ahead of send. */
    private static final int SNAPSHOT_PREPARED_ADOPTIONS_PER_TICK = SNAPSHOT_CAPTURES_PER_TICK;
    /**
     * 준비된 청크를 소유 턴에 채택하는 데 쓰는 벽시계 예산.
     *
     * <p>이 단계는 원래 건수(8건)로만 묶여 있었는데, 첫 진입처럼 청크가 차가운 구간에서는 한 건
     * 채택이 100ms를 넘기기도 해 8건이 통째로 1초 가까이 걸렸다(관측 최대 959ms). 건수는 일의
     * 크기를 재지 못하므로 시간으로도 묶는다. 진행이 아예 멈추면 안 되니 첫 한 건은 예산과
     * 무관하게 반드시 처리하고, 그 다음부터 예산을 본다. 남은 건은 큐에 그대로 있다가 다음
     * 턴에 이어서 채택된다.
     */
    private static final long PREPARED_SNAPSHOT_ADOPTION_BUDGET_NANOS = 25_000_000L;

    /**
     * 스냅샷 준비 요청을 승인하는 데 쓰는 벽시계 예산.
     *
     * <p>채택 단계와 같은 결함이었다. 건수(64건)와 배치 상한(1024건)만 있고 시간 제한이 없어,
     * 정지한 플레이어 한 명이 있는 유휴 구간에서도 이 단계가 최대 236ms 를 먹었다(60초 창에서
     * 합계 3,938ms). 승인하지 못한 요청은 아래 꼬리 루프가 전부 되돌려 넣고 다음 턴에 거리순으로
     * 다시 정렬되므로, 도중에 끊어도 가까운 청크가 굶지 않는다.
     */
    private static final long SNAPSHOT_PREPARATION_BUDGET_NANOS = 15_000_000L;
    private static final int MAX_SNAPSHOT_PREPARATION_FAILURES = 3;
    private static final int MAX_ACTIVATION_PLANNING_FAILURES = 3;
    private static final int PROXIMITY_PREPARE_RADIUS = 10;
    /** Distant presentation shares the existing preparers without taking every near-demand slot. */
    private static final int MAX_PENDING_PRESENTATION_PREPARATIONS = 1;
    private static final int MAX_READY_PRESENTATION_SNAPSHOTS = SNAPSHOT_CAPTURES_PER_TICK;
    private static final int PROXIMITY_PREPARES_PER_PLAYER_TICK = 1;
    /**
     * [DRAGON] {@code DRAGON} 티켓의 강제 적재: 아레나 −8..8 청크 가운데 비상주 청크를 한 틱에 이만큼 준비·채택한다
     * (플레이어 반경 준비와 따로 센다 — 도착 발판 (100, 49, 0) 은 아레나 서쪽 끝에서 14 청크라 플레이어 반경만으로는
     * 싸움의 {@code isArenaLoaded} 가 영영 참이 되지 않았다).
     */
    private static final int DRAGON_ARENA_PREPARES_PER_TICK = 2;
    /** 아레나 준비의 가상 요구자(플레이어 닉네임과 겹치지 않는 이름). */
    private static final String DRAGON_ARENA_DEMAND = "\u0000dragon-arena";
    private static final int TELEPORT_CHUNK_DISTANCE = 2;
    private static final int[][] PROXIMITY_PREPARE_OFFSETS = proximityPrepareOffsets();
    private static final ThreadPoolExecutor STRUCTURE_PLANNERS = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "structure-planner");
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    /** Full-site bridge: never overlap two potentially 850k-voxel city rasters across worlds. */
    private static final AtomicBoolean HEAVY_STRUCTURE_PLAN_ACTIVE = new AtomicBoolean();
    private static final ThreadPoolExecutor CHUNK_PREPARERS = daemonExecutor(
            1, 512, "chunk-preparer", Thread.NORM_PRIORITY - 1);
    /**
     * Snapshot, activation, and proximity preparation share the host with the browser and world owner. Reserve
     * two logical processors where possible; an 8-core M1 therefore runs 3 snapshot + 2 activation + 1 proximity
     * worker instead of saturating all eight cores before rendering or simulation work begins.
     */
    private static final int STREAMING_WORKER_BUDGET = Math.max(3,
            Math.min(8, Runtime.getRuntime().availableProcessors() - 2));
    /**
     * Activation scans must not sit behind the full radius-10 terrain queue. Three bounded workers let independent
     * entry chunks use the thread-safe source caches concurrently while low-core hosts retain one worker.
     */
    private static final int ACTIVATION_PLANNER_THREADS = Math.max(1,
            Math.min(3, (STREAMING_WORKER_BUDGET - 1) / 2));
    private static final ThreadPoolExecutor ACTIVATION_PLANNERS = daemonExecutor(
            ACTIVATION_PLANNER_THREADS, 512, "chunk-activation-planner", Thread.NORM_PRIORITY - 1);
    /** Canonical BTIK/FTIK validation and atomic receipt persistence never run on the world owner. */
    private static final ThreadPoolExecutor FINAL_CARRIER_LANE_WORKERS = daemonExecutor(
            1, 512, "final-carrier-lane", Thread.NORM_PRIORITY - 1);
    /** LOOT/OWNERS have no resident callback; their transactions must not stall the owner. */
    private static final ThreadPoolExecutor FINAL_CARRIER_PAYLOAD_WORKERS = daemonExecutor(
            1, 32, "final-carrier-payload", Thread.NORM_PRIORITY - 1);
    private final java.util.concurrent.atomic.AtomicInteger finalCarrierPayloadWorkCount =
            new java.util.concurrent.atomic.AtomicInteger();
    /**
     * SPAWNERS/ENTITIES with real rows: their transactions take hundreds of ms (id reservation,
     * mob rows). They get their own worker so LOOT/OWNERS writes and chunk recovery reads, which
     * entry waits on, never queue behind them.
     */
    private static final ThreadPoolExecutor FINAL_CARRIER_GAMEPLAY_PAYLOAD_WORKERS = daemonExecutor(
            1, 32, "final-carrier-gameplay-payload", Thread.NORM_PRIORITY - 1);
    private final java.util.concurrent.atomic.AtomicInteger finalCarrierGameplayPayloadWorkCount =
            new java.util.concurrent.atomic.AtomicInteger();
    /**
     * Durable scheduled-tick settlement steps.
     *
     * <p>Each step locks the world row, and the world row is the per-world write mutex a dozen
     * unrelated services already contend for. The owner turn hands the step here and waits only
     * inside its own drain slice, so the lock is taken by a worker that may wait for it instead of
     * by the thread the whole world is running on. It is deliberately not the lane-worker pool:
     * settlement must not queue behind carrier admission, and a queued step must not be discarded
     * on a lifecycle change while a drain is waiting for its answer.</p>
     */
    private static final ThreadPoolExecutor FINAL_CARRIER_SETTLEMENT_WORKERS = daemonExecutor(
            2, 1024, "final-carrier-settlement", Thread.NORM_PRIORITY - 1);
    /**
     * Wall-clock slice the owner turn lends to durable final-carrier scheduled-tick settlement. Settlement is
     * one database transaction per due row and the owner turn budget is 100 ms, so this phase must never take
     * more than a small fraction of it; the remainder of the queue stays due and is settled by later turns in
     * exactly the same order.
     */
    private static final long FINAL_CARRIER_TICK_DRAIN_BUDGET_NANOS =
            java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(8);
    /** Bounded admission wait; once the owner starts, commit is a non-blocking in-memory fence. */
    private static final long H12G_LEDGER_COMMIT_TIMEOUT_MILLIS = 2_000L;
    /**
     * Initial render snapshots are independent pure chunk generations. Use a bounded number of cores while
     * leaving at least two processors to the browser/OS on small hosts; owner adoption below applies completed
     * chunk-local results without allowing a slow request to block unrelated ready chunks.
     */
    private static final int SNAPSHOT_PREPARER_THREADS = Math.max(1,
            Math.min(4, STREAMING_WORKER_BUDGET - 1 - ACTIVATION_PLANNER_THREADS));
    /** Snapshot hydration must not queue behind proximity preparation or its activation-plan follow-up work. */
    private static final ThreadPoolExecutor SNAPSHOT_PREPARERS = daemonExecutor(
            SNAPSHOT_PREPARER_THREADS, 512, "chunk-snapshot-preparer", Thread.NORM_PRIORITY - 1);
    /**
     * 지형 준비 큐는 cold burst 때 512개까지 찰 수 있으므로 인코딩을 그 뒤에 세우지 않습니다. 전 월드가
     * 공유하는 작은 전용 큐가 immutable source 보유량을 32개로 제한하고 sender는 전송만 담당합니다.
     */
    private static final int SNAPSHOT_ENCODER_THREADS = Math.max(1, Math.min(2, SNAPSHOT_PREPARER_THREADS));
    private static final ThreadPoolExecutor SNAPSHOT_ENCODERS = daemonExecutor(
            SNAPSHOT_ENCODER_THREADS, 32, "chunk-snapshot-encoder", Thread.NORM_PRIORITY - 1);
    /**
     * per-connection FIFO는 세션 lane이 in-flight 1을 보장해 유지되므로 스레드 수와 무관합니다. 단일
     * 스레드였을 때는 멈춘 피어 한 명의 블로킹 소켓 쓰기가 모든 세션의 snapshot 전송을 막았습니다.
     */
    private static final int SNAPSHOT_SENDER_THREADS = Math.max(2,
            Math.min(4, SNAPSHOT_PREPARER_THREADS + 1));
    private static final ThreadPoolExecutor SNAPSHOT_SENDERS = daemonExecutor(
            SNAPSHOT_SENDER_THREADS, 64, "chunk-snapshot-sender");
    /** 정체 세션은 close()마저 블로킹될 수 있으므로 틱·송신 스레드와 분리된 곳에서 회수합니다. */
    private static final ThreadPoolExecutor SNAPSHOT_STALL_RECOVERY = daemonExecutor(
            2, 64, "chunk-snapshot-stall-recovery");
    /**
     * 한 세션의 소켓 쓰기가 이 시간을 넘기면 그 세션만 격리하고 재동기화 경로로 되돌립니다.
     * 늦게 잡아도 손해는 sender 스레드 하나뿐이므로, 오탐으로 멀쩡한 연결을 끊지 않도록 넉넉히 잡습니다.
     */
    private static final long DEFAULT_SNAPSHOT_SEND_STALL_MILLIS = 10_000;
    /** 최종 저장 장벽 대기 상한. 만료하면 호출자는 락을 잡은 채 무한정 서 있지 않고 격리로 넘어갑니다. */
    private static final long DEFAULT_DISPOSAL_AWAIT_TIMEOUT_MILLIS = 30_000;
    private static final long DEFAULT_OWNER_QUIESCENCE_TIMEOUT_MILLIS = 30_000;
    private static final int FORCED_CUSHION_LOGOUT_ATTEMPTS = 3;
    /** Runtime admissions use the same world-wide bound as player actions. */
    private static final int MAX_RUNTIME_ADMISSIONS = ActionQueue.DEFAULT_MAX_TOTAL;
    /** A single owner turn may lend only one player admission's budget to completion callbacks. */
    private static final int PERSISTENCE_DRAIN_BATCH_LIMIT = ActionQueue.DEFAULT_MAX_PER_PLAYER;
    private static final int SNAPSHOT_FRAME_CACHE_MAX_ENTRIES = 512;
    private static final long SNAPSHOT_FRAME_CACHE_BYTE_BUDGET = 32L * 1024 * 1024;
    private static final AtomicLong WORLD_EPOCH_SEQUENCE = new AtomicLong();

    private final Long worldId;
    private final int seed;
    /** 월드 난이도(생성 시 고정). 몹 규칙·굶주림 하한이 이 값을 읽는다. */
    private final Difficulty difficulty;
    /** Deterministic world spawn, computed on the connection path before this runtime starts ticking. */
    private volatile int[] worldSpawn;
    /** runtime 재생성 경계. 같은 월드 ID라도 새 런타임이면 클라이언트 cache를 재사용할 수 없다. */
    private final long worldEpoch;
    private final EngineContext ctx;
    private final ChestPersistenceService chestPersistence;
    private final FurnacePersistenceService furnacePersistence;
    private BrewingPersistenceService brewingPersistence;
    private final CampfirePersistenceService campfirePersistence;
    private final MobPersistenceService mobPersistence;
    /** 주민 사회/HOME 상태는 런타임 생성 직후 관리자가 붙여 첫 틱 전에 복구한다. */
    private VillagerSocietyPersistenceService villagerSocietyPersistence;
    private final WorldTimePersistenceService worldTimePersistence;
    private final WorldMapPersistenceService worldMapPersistence;
    private final WorldMapRuntime worldMaps;
    /** Durable placed-banner aggregate, attached and hydrated before the first runtime tick. */
    private BannerBlockPersistenceService bannerPersistence;
    private BannerPlacementSettlementService bannerPlacementSettlements;
    private com.gameexpert.block.service.PlayerBlockSettlementService playerBlockSettlements;
    private final Map<BlockPos, BannerBlockData> bannerBlocks = new HashMap<>();
    /** Placement payload captured before the source stack is consumed. */
    private final Map<BlockPos, BannerBlockData> stagedBannerPlacements = new HashMap<>();
    private final Set<BlockPos> bannerPersistenceInFlight = new HashSet<>();
    private final Map<BlockPos, PendingBannerPersistence> pendingBannerPersistence = new HashMap<>();
    private SignBlockPersistenceService signPersistence;
    private final Map<BlockPos, SignBlockData> signBlocks = new HashMap<>();
    private final Map<BlockPos, SignBlockData> stagedSignPlacements = new HashMap<>();
    private final Set<BlockPos> signPersistenceInFlight = new HashSet<>();
    private final Map<BlockPos, PendingSignPersistence> pendingSignPersistence = new HashMap<>();
    private LecternPersistenceService lecternPersistence;
    /** [JUKEBOX] world_jukeboxes 저장소. 없으면(단위 테스트) 주크박스 블록 엔티티는 메모리에만 산다. */
    private com.gameexpert.jukebox.service.JukeboxPersistenceService jukeboxPersistence;
    /** [BLOCK-SHAPES] has_book 비트를 아직 못 세운 책 든 옛 독서대(청크 키별). */
    private final Map<Long, List<BlockPos>> legacyBookLecterns = new HashMap<>();
    private LecternMiningSettlementService lecternMiningSettlements;

    private final WorldClock clock;
    private final ActionQueue actionQueue = new ActionQueue();
    private final ConcurrentHashMap<String, PlayerTickState> players = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> playerConnections = new ConcurrentHashMap<>();
    private final TerrainAccessor accessor;
    private final ChunkProductSource chunkProductSource;
    /** [END-GATEWAY] 엔드 차원 엔드 관문 블록 엔티티(다른 차원에서는 비어 있고 틱도 즉시 돌아간다). */
    private final EndGatewaySystem endGateways = new EndGatewaySystem(this);

    EndGatewaySystem endGateways() { return endGateways; }

    void installEndGatewayPersistence(com.gameexpert.endgateway.service.EndGatewayPersistenceService service) {
        endGateways.install(service);
    }

    /** [DRAGON] 엔드 차원 드래곤전(다른 차원에서는 싸움이 없고, 엔드 수정 피해만 받는다). */
    private final DragonFightSystem dragonFight = new DragonFightSystem(this);

    DragonFightSystem dragonFight() { return dragonFight; }

    void installDragonFightPersistence(com.gameexpert.dragonfight.service.DragonFightPersistenceService service) {
        dragonFight.install(service);
    }

    /**
     * [DRAGON] {@code DRAGON} 티켓(바닐라 {@code addTicketWithRadius(DRAGON, (0,0), 9)}): 보스바를 보는 플레이어가 있으면
     * 아레나 −8..8 청크를 시뮬레이션 집합에 붙잡는다.
     */
    private volatile boolean dragonArenaHeld;
    private boolean dragonArenaApplied;

    void setDragonArenaHeld(boolean hold) {
        if (dragonArenaHeld == hold) return;
        dragonArenaHeld = hold;
        refreshActiveSimulationChunks();
    }

    /**
     * [END-GATEWAY] 틱 밖 청크 준비 작업자에서 비상주 청크 하나를 준비한다(생성 + 저장 diff, 상주 상태 무변경).
     * 결과·실패는 작업자 스레드에서 콜백으로 넘어가며, 채택은 호출자가 소유자 턴에 한다.
     */
    void submitDetachedChunkPreparation(int chunkX, int chunkZ,
            java.util.function.Consumer<TerrainAccessor.PreparedChunk> ready,
            java.util.function.Consumer<RuntimeException> failed) {
        executeOwned(CHUNK_PREPARERS, () -> {
            try {
                ready.accept(prepareChunkOffTick(chunkX, chunkZ));
            } catch (RuntimeException failure) {
                failed.accept(failure);
            }
        }, () -> failed.accept(new java.util.concurrent.CancellationException("end gateway preparation discarded")),
                true, chunkX, chunkZ, 0L);
    }

    private FleshNetherRuntime fleshNether;
    private com.gameexpert.engine.persistence.animal.FleshColonySettlementService fleshColonySettlements;
    private final Map<String, FleshColonyProgress.State> fleshColonyStates = new HashMap<>();
    private final Set<BlockPos> fleshColonyReservations = new HashSet<>();

    void attachFleshColonySettlements(
            com.gameexpert.engine.persistence.animal.FleshColonySettlementService service) {
        if (!"flesh_nether".equals(dimensionKey())) return;
        if (fleshColonySettlements != null) throw new IllegalStateException("colony settlements already attached");
        fleshColonySettlements = service;
        fleshColonyStates.putAll(service.loadStates(worldId, seed()));
    }

    boolean isFleshColonyOrgan(BlockPos pos, int type) {
        if (fleshColonySettlements == null || type != Blocks.FLESH_ANCHOR && type != Blocks.HEART_CORE) return false;
        var layout = com.gameexpert.world.dimension.flesh.FleshColonyLayout.forCoordinate(seed(), pos.x(), pos.z());
        var point = new com.gameexpert.world.dimension.flesh.FleshColonyLayout.Point(pos.x(), pos.y(), pos.z());
        return type == Blocks.HEART_CORE ? layout.core().equals(point) : layout.anchors().contains(point);
    }

    FleshColonyProgress.State fleshColonyState(BlockPos pos) {
        String cell = com.gameexpert.world.dimension.flesh.FleshColonyLayout
                .forCoordinate(seed(), pos.x(), pos.z()).cellKey();
        return fleshColonyStates.getOrDefault(cell, FleshColonyProgress.initial());
    }

    boolean submitFleshColonyMining(BlockPos pos, long expectedRevision,
            com.gameexpert.ground.dto.GroundMutationCommand ground, Runnable committed, Runnable rejected) {
        if (fleshColonySettlements == null || !worldId.equals(ground.worldId())) return false;
        var layout = com.gameexpert.world.dimension.flesh.FleshColonyLayout.forCoordinate(seed(), pos.x(), pos.z());
        var point = new com.gameexpert.world.dimension.flesh.FleshColonyLayout.Point(pos.x(), pos.y(), pos.z());
        int anchor = layout.anchors().indexOf(point);
        var state = fleshColonyState(pos);
        var next = anchor < 0 ? FleshColonyProgress.extractCore(state, expectedRevision)
                : FleshColonyProgress.severAnchor(state, anchor, expectedRevision);
        if (!next.accepted() || anchor < 0 && !layout.core().equals(point)) return false;
        synchronized (this) {
            if (ground.expectedGroundRevision() != groundRevision || !beginGroundSettlement()) return false;
        }
        fleshColonyReservations.add(pos);
        GroundSettlementOperation operation = new GroundSettlementOperation(ground, () -> {
            fleshColonyReservations.remove(pos);
            fleshColonyStates.put(layout.cellKey(), next.next());
            applyJournaledMobMutation(pos.x(), pos.y(), pos.z(), Blocks.AIR, (short) 0,
                    com.gameexpert.engine.persistence.animal.FleshColonySettlementService.key(layout.cellKey(), anchor));
            committed.run();
        }, () -> { fleshColonyReservations.remove(pos); rejected.run(); });
        try {
            claimGroundMutationIdentities(ground);
        } catch (RuntimeException | Error failure) {
            operation.reject(); throw failure;
        }
        Runnable transaction = () -> {
            boolean accepted;
            try {
                accepted = fleshColonySettlements.settleMining(seed(), pos, expectedRevision, ground)
                        == ground.committedGroundRevision();
            } catch (RuntimeException | Error failure) {
                log.warn("World {} colony mining settlement failed at {}: {}", worldId, pos, failure.toString());
                accepted = false;
            }
            enqueuePersistenceCompletion(accepted ? operation::commit : operation::reject, operation::reject);
        };
        PersistenceExecutor writer = ctx.persistenceExecutor();
        if (writer == null) { transaction.run(); drainPersistenceCompletions(); return true; }
        try {
            if (writer.trySubmit(transaction)) return true;
        } catch (RuntimeException | Error failure) {
            operation.reject(); throw failure;
        }
        fleshColonyReservations.remove(pos);
        completeGroundSettlement(ground.committedGroundRevision(), false);
        return false;
    }

    void attachFleshNether(com.gameexpert.engine.persistence.animal.FleshNetherPersistenceService service,
            MobPersistenceService mobs) {
        if (!"flesh_nether".equals(dimensionKey())) return;
        if (fleshNether != null) throw new IllegalStateException("flesh runtime already attached");
        durableBlockDiffSources.forEach((pos, source) -> {
            if (source.startsWith(com.gameexpert.engine.persistence.animal.FleshNetherPersistenceService.SOURCE)
                    && (durableBlockDiffCells.getOrDefault(pos, 0) >>> 16) != Blocks.AIR) {
                protectedDecorationEdits.remove(pos);
                mobMutationSites.add(pos);
            }
        });
        fleshNether = new FleshNetherRuntime(this, service, mobs);
    }

    boolean fleshCellReserved(int x, int y, int z) {
        return fleshColonyReservations.contains(new BlockPos(x, y, z))
                || fleshNether != null && fleshNether.reserved(x, y, z);
    }

    boolean fleshPlayerEdited(BlockPos pos) {
        return protectedDecorationEdits.contains(pos)
                || WorldTickLoop.residentBlockType(accessor, pos.x(), pos.y(), pos.z()) == Blocks.AIR
                    && (mobMutationSites.contains(pos) || durableBlockDiffCells.containsKey(pos));
    }

    void tickFleshNether() { if (fleshNether != null) fleshNether.tick(); }
    List<BlockPos> notifyFleshColonySettlement(BlockPos pos) {
        return fleshNether == null ? List.of() : fleshNether.onColonySettlement(pos);
    }

    void installFleshCell(com.gameexpert.engine.persistence.animal.FleshNetherPersistenceService.Cell cell,
            String key) {
        BlockPos p = cell.pos();
        applyJournaledMobMutation(p.x(), p.y(), p.z(), cell.after(), cell.afterState(),
                com.gameexpert.engine.persistence.animal.FleshNetherPersistenceService.SOURCE + key);
        fluidSim.onBlockChanged(p.x(), p.y(), p.z());
    }
    private final BlockStateStorage blockStates;
    private final FluidSimulator fluidSim;
    private final GeneratedFluidActivation generatedFluidActivation;
    /** A strict detached final-carrier fluid plan must not treat a cold read as solid terrain. */
    private final ThreadLocal<Boolean> strictFinalCarrierFluidReads = new ThreadLocal<>();
    private final FallingBlockSystem fallingBlockSystem;
    private final SurfaceDecorator surfaceDecorator;
    private final ExplorationDecorator explorationDecorator;
    private final EnvironmentSystem environment;
    /** One irreversible identity lane shared by every durable ground entity kind. */
    private final GroundEntityIdAllocator groundEntityIds = new GroundEntityIdAllocator();
    private final ItemEntitySystem itemSystem;
    /** [SURV-X] 경험치 구슬 엔티티(아이템 드랍과 같은 틱 단계에서 처리). */
    private final XpOrbSystem xpOrbSystem = new XpOrbSystem(this, groundEntityIds);
    /** Attached by the Spring lifecycle manager before the first tick/join snapshot. */
    private GroundEntityPersistenceService groundEntityPersistence;
    private GroundMutationSettlementService groundMutationSettlements;
    private ContentQaFixtureSettlementService contentQaFixtureSettlements;
    private com.gameexpert.map.service.EmptyMapSettlementService emptyMapSettlements;
    /** Last ground aggregate revision known committed in the database. */
    private volatile long groundRevision;
    private boolean groundSettlementInFlight;
    /** One frozen command owns both spawn queues until its durable receipt is confirmed. */
    private PendingGroundSpawnBatch pendingGroundSpawnBatch;
    private long groundDirtyRevision;
    private long groundPersistedRevision;
    private long observedGroundItemRevision;
    private long observedGroundXpRevision;
    private boolean groundSaveInFlight;
    /** Durable projectile ledger, attached before the first welcome. */
    private ProjectilePersistenceService projectilePersistence;
    private long projectileDirtyRevision;
    private long projectilePersistedRevision;
    private long observedProjectileRevision;
    private boolean projectileSaveInFlight;
    private boolean projectileLedgerMayBeNonEmpty;
    /** [SURV-X] 인챈트 테이블 좌표별 입력 칸(틱 스레드 전용). */
    private final EnchantingStorage enchantingStorage = new EnchantingStorage();
    /** Required production authority; synchronously hydrates before this runtime can be exposed. */
    private EnchantingPersistenceService enchantingPersistence;
    /** [SURV-X] 접속·인챈트 성공마다 재추첨하는 플레이어별 enchantSeed 소스. */
    private final java.util.Random enchantSeedRandom = new java.util.Random();
    // 상자 내용물(§10.2 상자). 좌표별 27칸 보관 + dirty 추적. 영속은 world_chests/world_chest_items.
    private final ChestStorage chestStorage = new ChestStorage();
    /**
     * [SHULKER-CONTENTS] 아이템으로 이동 중인 셜커 27칸의 참조 저장소. 놓여 있는 셜커의 27칸은
     * 여기 없다 — 그동안은 {@link #chestStorage} 가 좌표로 소유한다. 두 저장소의 영속은
     * {@code ChestPersistenceService.flushDirty} 가 한 트랜잭션으로 커밋한다.
     */
    private final ShulkerContentsStorage shulkerStorage = new ShulkerContentsStorage();
    private final FurnaceStorage furnaceStorage = new FurnaceStorage();
    private final BrewingStorage brewingStorage = new BrewingStorage();
    private final CampfireStorage campfireStorage = new CampfireStorage();
    /**
     * 피뢰침 원장. 낙뢰 유인({@link WeatherSystem#setStrikeHook})이 읽는 유일한 출처이며,
     * 블록 변경 깔때기·청크 활성화/축출이 갱신한다({@link LightningRodIndex} 문서 참조).
     */
    private final LightningRodIndex lightningRods = new LightningRodIndex();
    /** [HOPPER] HopperBlockEntity owner: position index, cooldowns and the transfer tick. */
    private final HopperSystem hopperSystem = new HopperSystem(this);
    /** [CONTAINER-MENUS] DispenserBlock/DropperBlock trigger edge and scheduled dispenses. */
    private final DispenserSystem dispenserSystem = new DispenserSystem(this);
    /** [CONTAINER-MENUS] CrafterBlock trigger edge, scheduled crafts and the CRAFTING countdown. */
    private final CrafterSystem crafterSystem = new CrafterSystem(this);
    /**
     * [CONTAINER-MENUS] {@code CrafterBlockEntity.disabled_slots} of every crafter with at least
     * one disabled slot (nine-bit mask); the nine item slots live in {@link #chestStorage}.
     */
    private final Map<BlockPos, Integer> crafterDisabledSlots = new HashMap<>();
    /** Masks changed since the last flush, in change order (0 = delete the row). */
    private final LinkedHashMap<BlockPos, Integer> dirtyCrafterMasks = new LinkedHashMap<>();
    private com.gameexpert.crafter.service.CrafterPersistenceService crafterPersistence;
    /**
     * [BLOCK-ENTITY] Cells whose block changed to another block-entity family through the
     * overlay funnel. {@link #sweepReplacedBlockEntities} drops the rows no path spilled.
     */
    private final java.util.LinkedHashMap<BlockPos, Integer> replacedBlockEntityCells =
            new java.util.LinkedHashMap<>();
    /** Cells targeted by the one in-flight player container settlement (FIFO of one). */
    private volatile Set<BlockPos> playerContainerSettlementCells = Set.of();
    private final MobSystem mobSystem;
    private final BoatSystem boatSystem;
    /** [CONTAINER-MENUS] Player-placed armor stands and minecarts. */
    private final PlacedEntitySystem placedEntities;
    private final CushionSystem cushionSystem;
    private final WeatherSystem weatherSystem;
    private final WorldTickLoop tickLoop;
    /** Owner-thread-only primitive accumulator; reused so on-budget turns allocate nothing for timing. */
    private final WorldTickLoop.TickTiming tickTiming = new WorldTickLoop.TickTiming();
    /** Diagnostic state only; no event or auxiliary per-turn state is allocated while JFR is disabled. */
    private ArchitectureTurnEvent architectureTurn;
    private long architectureOwnerTurnStartNanos;
    private long architectureNextTurnDeadlineNanos;
    private boolean architectureScheduleAnchorKnown;

    // 이번 틱에 바뀐 블록(브로드캐스트 + diff 버퍼 병합용). 틱 스레드 전용.
    // Activation planners read this set off-thread to reject cross-chunk natural features while
    // the tick owner records new edits. A concurrent key set keeps that boundary race-free.
    private final Set<BlockPos> protectedDecorationEdits = ConcurrentHashMap.newKeySet();
    /** 시작 시 repository에서 읽은 durable diff 셀 스냅샷. 저널 복구 외에는 판정에 쓰지 않습니다. */
    private final Map<BlockPos, Integer> durableBlockDiffCells = new HashMap<>();
    /** durable 셀과 같은 행에 원자적으로 저장된 몹 저널 entry key(null은 일반 편집). */
    private final Map<BlockPos, String> durableBlockDiffSources = new HashMap<>();
    /**
     * 몹 유발 변형이 소유한 좌표(§39cd 저널 provenance). 플레이어 편집과 <b>다른 집합</b>이라
     * 우선순위 계약(플레이어 편집 &gt; 몹 변형 &gt; 구조물 &gt; 자연 overlay)을 그대로 표현한다.
     * 구조물·자연 경로는 이 집합도 덮지 않고, 저널 요청은 플레이어 편집 집합만 거부 근거로 본다.
     */
    private final Set<BlockPos> mobMutationSites = ConcurrentHashMap.newKeySet();
    /** 저널이 확정한 변형을 적용하는 동안만 참. 그 편집은 플레이어 편집으로 기록되지 않는다. */
    private boolean applyingMobMutation;
    /** 현재 apply가 block diff에 함께 실을 정확한 저널 entry key. */
    private String applyingMobMutationKey;
    /** 몹 유발 변형의 write-ahead 저널. 기본은 프로세스 내 저장소이고 운영은 MySQL 저장소로 교체한다. */
    private MobMutationJournal mobMutationJournal;
    private final Set<Long> protectedDecorationEditChunks = new HashSet<>();
    private final Set<Long> replayableOverlayChunks = new HashSet<>();
    private final Set<Long> compactedReplayableChunks = new HashSet<>();
    private final Set<Long> rehydratingReplayableChunks = new HashSet<>();
    private final Map<BlockPos, Short> tickBlockChanges = new DecorationAwareBlockChanges();
    /** 이번 틱 최종 셀별 저널 출처. 일반 변경이 같은 좌표를 덮으면 즉시 제거됩니다. */
    private final Map<BlockPos, String> tickBlockMutationKeys = new HashMap<>();
    /** 환경 단계 뒤 생긴 지지면 변경을 다음 틱의 공용 제한 연쇄로 넘긴다. */
    private final ArrayDeque<BlockPos> environmentSupportChanges = new ArrayDeque<>();
    // 초기 자연 장식·구조물은 완성된 권위 스냅샷에 합성한다. 이미 공개된 청크의 후속 변화만
    // 실제 revision 발생 순서대로 한 큐에 넣어 편집·롤백이 이전 구조물 갱신을 추월하지 않게 한다.
    private final Deque<Block> pendingLiveBlockUpdates = new ArrayDeque<>();
    /** Deferred structure work is keyed by source chunk so stale exploration cannot make nearest selection grow forever. */
    private Map<Long, DeferredStructureDecoration> pendingStructureDecorations = new LinkedHashMap<>();
    private final Map<BlockPos, ExplorationDecorator.Kind> naturalLootChests = new HashMap<>();
    /** Final-carrier LOOT coordinates are the only generated containers that use canonical first-open. */
    private final Map<BlockPos, CanonicalLootContainerKind> canonicalLootCandidates = new HashMap<>();
    /**
     * Canonical container first-open reservations are keyed by the exact vanilla ordered halves.
     * The value remains owned until the writer result has crossed the owner completion queue.
     */
    private final Map<CanonicalLootOpenKey, CanonicalLootOpenReservation>
            canonicalLootOpenReservations = new LinkedHashMap<>();
    /** Rejected submissions and unknown writer outcomes re-enter this owner FIFO for exact replay. */
    private final ArrayDeque<CanonicalLootOpenReservation> canonicalLootOpenRetries =
            new ArrayDeque<>();
    /** A player may publish only the newest request for its exact live session. */
    private final IdentityHashMap<PlayerTickState, CanonicalLootOpenRequest> pendingCanonicalLootOpenRequests =
            new IdentityHashMap<>();
    /** Reference-counted coordinate fence shared by edits and explosion admission. */
    private final Map<BlockPos, Integer> pendingCanonicalLootCoordinates = new HashMap<>();
    /** Durable first-open decisions observed by this runtime; no second lazy roll is allowed. */
    private final Set<CanonicalLootOpenHalf> canonicalLootFirstOpenCompleted = new HashSet<>();
    private final Set<CanonicalLootOpenHalf> canonicalLootFirstOpenRejected = new HashSet<>();
    private long canonicalLootOpenRequestSequence;
    private final Map<BlockPos, ArchaeologyBrushableAggregate> archaeologyBrushables =
            new HashMap<>();
    private final Map<String, ArchaeologyBrushableAggregate> archaeologyBrushablesByIdentity =
            new HashMap<>();
    private final Map<String, StructureEntityAggregate> structureEntityInstallations =
            new HashMap<>();
    private final Map<Long, StructureEntityAggregate.PlannedEntity> structureEntitiesById =
            new HashMap<>();
    /** Strict schema-2 QA evidence is owned by the Runtime facts it observes, never by a request. */
    private final Map<GeneratedEvidenceKey, GeneratedEvidenceLane> generatedFinalSceneEvidence =
            new HashMap<>();
    private final ConcurrentHashMap<String, Long> generatedEvidenceConnectionGenerations =
            new ConcurrentHashMap<>();
    private long finalCarrierReservedEntityHighWater;
    private final LongObjectOpenHashMap<Map<BlockPos, OverlayValue>> authoritativeOverlay =
            new LongObjectOpenHashMap<>(256);
    // 자연 표면/영속 diff와 구분되는 구조물 placement의 authoritative overlay 좌표다.
    private final Set<Long> structureOverlaySites = new HashSet<>();
    private long structureApplySequence;
    /**
     * At most one outstanding full snapshot exists for a session/chunk.  A request stays pending until its
     * binary delivery succeeds, so a cold chunk or a saturated sender cannot silently lose a client retry.
     */
    private final ConcurrentHashMap<ChunkSnapshotRequestKey, ChunkSnapshotRequest> pendingChunkSnapshotRequests =
            new ConcurrentHashMap<>();
    /**
     * Chunk-key index over {@link #pendingChunkSnapshotRequests}.  Prepared-snapshot adoption asks "is this
     * chunk still wanted" and "requeue this chunk" once per adopted chunk; without an index each of those was a
     * full scan of every pending request, so the 25ms adoption budget was spent on lookup rather than on the
     * eight adoptions it is meant to buy.  Maintained only where the pending map itself is inserted into,
     * removed from and cleared.
     */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<ChunkSnapshotRequestKey, ChunkSnapshotRequest>>
            pendingChunkSnapshotRequestsByChunk = new ConcurrentHashMap<>();
    /** Requests awaiting O(1) resident probe or off-owner detached preparation admission. */
    private final ConcurrentLinkedQueue<ChunkSnapshotRequest> snapshotPreparationRequests =
            new ConcurrentLinkedQueue<>();
    /** Owner-installed residents awaiting the bounded full-cell copy. */
    private final ConcurrentLinkedQueue<ChunkSnapshotRequest> readyChunkSnapshotRequests =
            new ConcurrentLinkedQueue<>();
    /** Identity ownership prevents a cancelled queue node from consuming a replacement request's slot. */
    private final ConcurrentHashMap<ChunkSnapshotRequestKey, ChunkSnapshotRequest>
            queuedChunkSnapshotRequests = new ConcurrentHashMap<>();
    private final Set<Long> pendingPresentationSnapshotPreparations = ConcurrentHashMap.newKeySet();
    /** Complete immutable products await capture only; encoded deliveries retain no chunk arrays here. */
    private final ConcurrentHashMap<Long, PreparedSnapshotChunk> preparedPresentationSnapshots =
            new ConcurrentHashMap<>();
    /** global revision이 아닌 chunk-local revision. blockUpdate의 revision과 snapshot.toVersion의 정본이다. */
    private final ChunkVersionLedger chunkVersions = new ChunkVersionLedger();
    /** 동일 chunk/version의 완료 frame은 32 MiB LRU에 두고, 진행 중인 동일 인코딩도 한 future로 합칩니다. */
    private final SnapshotFrameCache snapshotFrameCache = new SnapshotFrameCache(
            SNAPSHOT_FRAME_CACHE_MAX_ENTRIES, SNAPSHOT_FRAME_CACHE_BYTE_BUDGET);
    private final ConcurrentHashMap<SnapshotFrameKey, CompletableFuture<List<byte[]>>> snapshotFrameEncodes =
            new ConcurrentHashMap<>();
    /** 비동기 완료 순서가 달라도 한 연결의 sender 제출 순서는 요청 FIFO를 따릅니다. */
    private final ConcurrentHashMap<String, SnapshotSendLane> snapshotSendLanes = new ConcurrentHashMap<>();
    private final AtomicLong snapshotFrameEncodeCount = new AtomicLong();
    // WS 종료 요청은 연결 ID와 함께 틱 소유자에게 넘긴다. 제거·제련 정산 뒤에만 완료된다.
    private final Map<String, PlayerTickState> dimensionDepartures = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger dimensionArrivalPins = new java.util.concurrent.atomic.AtomicInteger();
    private volatile java.util.function.Consumer<com.gameexpert.world.dimension.PortalTravelRequest> dimensionGateway;
    private volatile com.gameexpert.world.dimension.DimensionRegistry dimensions;
    private volatile com.gameexpert.world.dimension.DimensionProviders dimensionProviders;

    void attachDimensionGateway(com.gameexpert.world.dimension.DimensionRegistry registry,
            com.gameexpert.world.dimension.DimensionProviders providers,
            java.util.function.Consumer<com.gameexpert.world.dimension.PortalTravelRequest> gateway) {
        dimensions = registry; dimensionProviders = providers; dimensionGateway = gateway;
    }

    boolean dimensionPortalEligible(int block) {
        if (dimensions == null || dimensionProviders == null || dimensionGateway == null) return false;
        return dimensions.destination(dimensionKey(), block)
                .filter(target -> "overworld".equals(target.key()) || dimensionProviders.available(target.key()))
                .isPresent();
    }

    void requestDimensionTravel(PlayerTickState player, int block) {
        var gateway = dimensionGateway;
        String connection = playerConnections.get(player.nickname());
        if (gateway != null && connection != null) gateway.accept(
                new com.gameexpert.world.dimension.PortalTravelRequest(worldId, player.nickname(), connection, block));
    }

    <T> T dimensionOwner(java.util.function.Supplier<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Runnable work = () -> {
            try {
                if (!ownerTurnMayContinue()) throw new IllegalStateException("dimension owner unavailable");
                result.complete(operation.get());
            } catch (Throwable failure) { result.completeExceptionally(failure); }
        };
        synchronized (this) {
            if (!started) { work.run(); return result.join(); }
        }
        if (!enqueueOwnerTask(work)) throw new IllegalStateException("dimension owner admission rejected");
        return result.join();
    }

    /** Off-owner preparation followed by owner-only residency/collision validation. No source edits. */
    void requestDimensionRespawn(PlayerTickState player) {
        var gateway = dimensionGateway;
        if (gateway == null) throw new IllegalStateException("dimension gateway unavailable");
        gateway.accept(new com.gameexpert.world.dimension.PortalTravelRequest(
                worldId, player.nickname(), playerConnections.get(player.nickname()), Blocks.END_PORTAL, true));
    }

    RespawnRules.Spawn prepareDimensionRespawn(PlayerTickState player) {
        if (customDimension() || !player.isDead()) throw new IllegalStateException("invalid respawn target");
        int[] spawn = worldSpawn();
        List<TerrainAccessor.PreparedChunk> chunks = new ArrayList<>();
        int[][] centers = player.hasBedSpawn()
                ? new int[][] {spawn, {player.bedSpawnX(), player.bedSpawnY(), player.bedSpawnZ()}}
                : new int[][] {spawn};
        for (int[] center : centers) {
            int cx = Math.floorDiv(center[0], Blocks.CHUNK_X), cz = Math.floorDiv(center[2], Blocks.CHUNK_Z);
            for (int dx=-1; dx<=1; dx++) for (int dz=-1; dz<=1; dz++)
                chunks.add(prepareChunkOffTick(cx+dx, cz+dz));
        }
        return dimensionOwner(() -> {
            for (var prepared : chunks) accessor.adoptPreparedChunkForSnapshot(prepared);
            return tickLoop.resolveRespawn(player);
        });
    }

    /**
     * 아무도 접속하지 않은 동안 스폰 주변 청크를 미리 준비해 둔다.
     *
     * <p>첫 진입이 오래 걸리는 이유는 저장된 청크를 처음 읽어 올리는 비용이다(같은 월드에 두
     * 번째로 들어가면 실측 80초 → 14초). 여기서 미리 읽어 두면 첫 사람이 그 값을 치르지 않는다.
     * 한 청크씩 owner 턴에 넘기므로, 접속자가 있어도 틱을 길게 잡지 않는다.</p>
     */
    void warmSpawnArea(int centerChunkX, int centerChunkZ, int radius) {
        for (int distance = 0; distance <= radius; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                for (int dz = -distance; dz <= distance; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != distance) continue;
                    var prepared = prepareChunkOffTick(centerChunkX + dx, centerChunkZ + dz);
                    dimensionOwner(() -> {
                        accessor.adoptPreparedChunkForSnapshot(prepared);
                        return null;
                    });
                }
            }
        }
    }

    void prepareDimensionArrival(double[] pose) {
        int x = (int) Math.floor(pose[0]), z = (int) Math.floor(pose[2]);
        List<TerrainAccessor.PreparedChunk> chunks = new ArrayList<>();
        int cx = Math.floorDiv(x, Blocks.CHUNK_X), cz = Math.floorDiv(z, Blocks.CHUNK_Z);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            chunks.add(prepareChunkOffTick(cx + dx, cz + dz));
        }
        dimensionOwner(() -> {
            for (var prepared : chunks) accessor.adoptPreparedChunkForSnapshot(prepared);
            // [DIMENSION-EXT] 콘텐츠 도착 공간(예: 엔드 흑요석 발판)을 안전 검사 전에 다시 세운다.
            if (chunkProductSource instanceof com.gameexpert.world.dimension.DimensionChunkProductSource custom) {
                rebuildDimensionArrival(custom.arrivalCells());
            }
            if (!safeDimensionArrival(pose)) throw new IllegalStateException("dimension landing blocked");
            return null;
        });
    }

    /**
     * [DIMENSION-EXT] 바닐라 {@code EndPlatformFeature.createEndPlatform(level, pos, true)}: 목표와 다른
     * 칸만 {@code destroyBlock(pos, true)}(공기·유체가 아니면 블록 드랍) 뒤 목표 블록으로 바꾼다.
     * 변경은 일반 틱 블록 변경으로 영속·방송된다(엔드 차원문 개방과 같은 깔때기).
     */
    void rebuildDimensionArrival(List<com.gameexpert.world.dimension.DimensionChunkProvider.Cell> cells) {
        for (var cell : cells) {
            int current = WorldTickLoop.residentBlockType(accessor, cell.x(), cell.y(), cell.z());
            if (current == WorldTickLoop.UNAVAILABLE_BLOCK) {
                throw new IllegalStateException("dimension arrival cell is not resident");
            }
            if (current == cell.blockType()) continue;
            if (current != Blocks.AIR && !Fluids.isFluid(current)) {
                itemSystem().spawnBlockDrop((short) current, cell.x() + 0.5, cell.y() + 0.5, cell.z() + 0.5);
            }
            fluidSim().applyChange(cell.x(), cell.y(), cell.z(), cell.blockType());
            setBlockState(cell.x(), cell.y(), cell.z(), cell.blockType(), 0);
            tickBlockChanges().put(new BlockPos(cell.x(), cell.y(), cell.z()), (short) cell.blockType());
        }
    }

    private boolean safeDimensionArrival(double[] pose) {
        int y = (int) Math.floor(pose[1]);
        if (pose[1] != y || y <= Blocks.MIN_Y || y >= Blocks.MAX_Y) return false;
        for (int x = (int) Math.floor(pose[0] - .3); x <= (int) Math.floor(pose[0] + .3); x++) {
            for (int z = (int) Math.floor(pose[2] - .3); z <= (int) Math.floor(pose[2] + .3); z++) {
                if (!WorldTickLoop.isSafeRespawnSpace(accessor, x, y, z)
                        || WorldTickLoop.residentBlockType(accessor, x, y, z) != Blocks.AIR
                        || WorldTickLoop.residentBlockType(accessor, x, y + 1, z) != Blocks.AIR
                        || portalFloor(WorldTickLoop.residentBlockType(accessor, x, y - 1, z))) return false;
            }
        }
        return true;
    }

    /**
     * 바닐라 엔드 차원문·엔드 관문·지옥문은 충돌 상자가 없어 그 위에 설 수 없다(밟으면 다시 포털에 든다).
     * {@code Fluids.isSolid} 는 유체 규칙이라 엔드 차원문을 막힌 칸으로 보므로 착지 바닥에서 따로 뺀다.
     */
    private static boolean portalFloor(int block) {
        return block == Blocks.END_PORTAL || block == Blocks.END_GATEWAY || block == Blocks.NETHER_PORTAL;
    }

    double[] dimensionReturnOrigin(String nickname, String connectionId, int portalBlock) {
        return dimensionOwner(() -> {
            PlayerTickState player = playerForConnection(nickname, connectionId);
            if (player == null || player.isDead() || !tickLoop.touchesDimensionPortal(player, portalBlock)) {
                throw new IllegalStateException("source portal contact changed");
            }
            int x = (int) Math.floor(player.x()), y = (int) Math.floor(player.y()),
                    z = (int) Math.floor(player.z());
            // 링마다 먼저 같은 발 높이, 그다음 한 칸 위를 본다. 바닐라 EnderDragonFight.spawnExitPortal 은
            // 귀환 포털 층을 (0,0) 지표 꼭대기 칸에 두므로(EndPodiumFeature) 포털 층 바깥은 기반암 테와
            // 지표 엔드 돌이고, 걸어 나갈 자리는 그 위(발 y+1)다. 지옥문처럼 바닥에 선 포털은 같은 높이에서 찾는다.
            for (int radius = 1; radius <= 3; radius++) {
                for (int step = 0; step <= 1; step++) {
                    for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        double[] pose = {x + dx + .5, y + step, z + dz + .5, player.yaw(), player.pitch()};
                        if (safeDimensionArrival(pose)) return pose;
                    }
                }
            }
            // 한 칸 올라선 지옥문 하단 프레임 밖의 지표는 발보다 낮다. 기존 귀환 위치를 먼저
            // 유지하고, 같은 충돌·지지·포털 제외 검사를 통과한 한 칸 아래 이웃만 보완한다.
            for (int radius = 1; radius <= 3; radius++) {
                for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    double[] pose = {x + dx + .5, y - 1, z + dz + .5, player.yaw(), player.pitch()};
                    if (safeDimensionArrival(pose)) return pose;
                }
            }
            throw new IllegalStateException("no safe neighbor beside source portal");
        });
    }

    PlayerTickState departForDimension(String nickname, String connectionId, int portalBlock) {
        return departForDimension(nickname, connectionId, portalBlock, false);
    }

    PlayerTickState departForDimension(String nickname, String connectionId, int portalBlock, boolean respawn) {
        PlayerTickState retained = dimensionOwner(() -> {
            PlayerTickState player = playerForConnection(nickname, connectionId);
            boolean eligible = player != null && (respawn
                    ? "void_end".equals(dimensionKey()) && player.isDead() && !player.justDied()
                    : !player.isDead() && tickLoop.touchesDimensionPortal(player, portalBlock));
            if (!eligible || dimensionDepartures.putIfAbsent(nickname, player) != null) {
                throw new IllegalStateException("dimension departure unavailable");
            }
            actionQueue.discardPlayer(nickname);
            return player;
        });
        PlayerTickState departed;
        try {
            departed = requestPlayerLeave(nickname, connectionId).join();
        } catch (RuntimeException failure) {
            restoreDimensionDeparture(nickname, connectionId);
            throw failure;
        }
        if (departed != retained) {
            restoreDimensionDeparture(nickname, connectionId);
            throw new IllegalStateException("dimension source checkpoint failed");
        }
        return departed;
    }

    void restoreDimensionDeparture(String nickname, String connectionId) {
        dimensionOwner(() -> {
            PlayerTickState retained = dimensionDepartures.remove(nickname);
            if (retained != null && !players.containsKey(nickname)) {
                playerConnections.put(nickname, connectionId);
                players.put(nickname, retained);
            }
            return null;
        });
    }

    void completeDimensionDeparture(String nickname) {
        dimensionOwner(() -> { dimensionDepartures.remove(nickname); return null; });
    }

    void pinDimensionArrival() { dimensionArrivalPins.incrementAndGet(); }
    void releaseDimensionArrival() {
        if (dimensionArrivalPins.decrementAndGet() < 0) throw new IllegalStateException("dimension pin underflow");
    }

    private final ConcurrentLinkedQueue<PlayerLeaveRequest> playerLeaveRequests = new ConcurrentLinkedQueue<>();
    /** Requests already admitted by the owner and waiting for a durable ground callback. */
    private final Set<PlayerLeaveRequest> activePlayerLeaveRequests = ConcurrentHashMap.newKeySet();
    /** Exact live player/inventory authority held across a durable leave or death ground commit. */
    private final ConcurrentHashMap<String, PlayerInventorySettlementReservation>
            playerInventorySettlementReservations = new ConcurrentHashMap<>();
    // persistence worker의 성공/실패 승인을 틱 소유권으로 되돌리는 단방향 큐.
    // A terminal drain may retain only the generic bookkeeping half of a completion. The live
    // half is deliberately discarded after DISPOSED so a late writer cannot mutate the old world.
    private final ConcurrentLinkedQueue<PersistenceCompletion> persistenceCompletions =
            new ConcurrentLinkedQueue<>();
    private final java.util.concurrent.atomic.AtomicInteger animalSettlementWriterTasks =
            new java.util.concurrent.atomic.AtomicInteger();
    /** 블록 컨테이너 액션은 하나의 owner FIFO에서 계획·정산되어 입력 순서를 보존한다. */
    private final ArrayDeque<Runnable> playerContainerActions = new ArrayDeque<>();
    private volatile boolean playerContainerSettlementInFlight;
    private PlayerContainerSettlementPersistenceService playerContainerSettlements;
    // 지도 패치는 폐기 뒤에도 마지막 writer 장벽이 승인/거부를 적용해야 하므로 별도 큐로 추적한다.
    private final ConcurrentLinkedQueue<PersistenceCompletion> mapPersistenceCompletions =
            new ConcurrentLinkedQueue<>();
    /** [TRIAL] 영속 lane이 열쇠 소비와 보상을 함께 정산할 금고 개봉 시도. */
    private final ConcurrentLinkedQueue<PendingVaultUnlock> pendingVaultUnlocks =
            new ConcurrentLinkedQueue<>();
    /** One demand/result/application per source chunk. Each map is compacted to the current player union on movement. */
    private volatile ConcurrentHashMap<Long, ChunkActivationDemand> chunkActivationDemands =
            new ConcurrentHashMap<>();
    /** Completed detached plans cross from the preparer to the world-owner tick here. */
    private volatile ConcurrentHashMap<Long, PreparedChunkActivation> preparedChunkActivations =
            new ConcurrentHashMap<>();
    /** Deterministic planner defects must degrade one chunk, not retry forever and hold entry presentation open. */
    private volatile ConcurrentHashMap<Long, Integer> activationPlanningFailures =
            new ConcurrentHashMap<>();
    /** Tick-owned FIFO: application mutates fluid/overlay state but never plans or scans a chunk. */
    private Map<Long, PreparedChunkActivation> pendingChunkActivationApplications = new LinkedHashMap<>();
    /** Chunk key to active-neighborhood generation; conditional removal prevents reconnect ABA races. */
    private volatile ConcurrentHashMap<Long, Long> pendingChunkPreparations =
            new ConcurrentHashMap<>();
    /** Proximity terrain material is prepared detached, then installed by the owner with no cache race. */
    private volatile ConcurrentHashMap<Long, PreparedChunkDemand> preparedChunkDemands =
            new ConcurrentHashMap<>();
    /** Cold snapshot demand owns one preparation task per chunk, shared by all requesting sessions. */
    private final Set<Long> pendingSnapshotChunkPreparations = ConcurrentHashMap.newKeySet();
    /** Ordinary unknown snapshot production only; dedup ownership above outlives worker completion. */
    private final Map<Long, Object> pendingSnapshotGenerationPermits = new ConcurrentHashMap<>();
    /** Owner-turn only: admission telemetry counters, never read off the owner thread. */
    private long orderedCanonicalDemandAdmissions;
    private long deferredCanonicalDemandAdmissions;
    /**
     * Detached DB+terrain results are chunk-local and immutable, so the owner may adopt whichever worker finishes
     * first. A completion queue prevents one slow source chunk from blocking every already-ready entry chunk.
     */
    private final ConcurrentLinkedQueue<PreparedSnapshotChunk> preparedSnapshotChunks =
            new ConcurrentLinkedQueue<>();
    /** Owner-only bounded retry count; successful preparation or terminal response removes the key. */
    private final Map<Long, Integer> snapshotPreparationFailures = new HashMap<>();
    /** A broken optional neighbour must not hold an otherwise usable entry ring forever. */
    private final Map<Long, Integer> treeSourcePreparationFailures = new HashMap<>();
    private volatile Set<Long> unavailableTreePlanningSources = ConcurrentHashMap.newKeySet();
    /** Chunk key to active-neighborhood generation for detached activation planners. */
    private volatile ConcurrentHashMap<Long, Long> pendingChunkActivationPlans =
            new ConcurrentHashMap<>();
    /** Generation-local deliveries retained through retries until every claimed lane is acknowledged. */
    private volatile ConcurrentHashMap<Long, PendingFinalCarrierClaim> pendingFinalCarrierClaims =
            new ConcurrentHashMap<>();
    /** One detached durable admission per generation-local tick lane. */
    private final Set<FinalCarrierTickAdmissionKey> inFlightFinalCarrierTickAdmissions =
            ConcurrentHashMap.newKeySet();
    /** Malformed lanes remain unacknowledged and are reported once per residency generation. */
    private final Set<FinalCarrierTickAdmissionKey> failedFinalCarrierTickAdmissions =
            ConcurrentHashMap.newKeySet();
    /** Fair bounded owner admission ring; backoff/in-flight keys rotate behind ready siblings. */
    private final ConcurrentLinkedQueue<Long> pendingFinalCarrierTickAdmissionOrder =
            new ConcurrentLinkedQueue<>();
    private final Set<Long> queuedFinalCarrierTickAdmissionChunks = ConcurrentHashMap.newKeySet();
    /** Transient writer failures back off per exact generation-local lane without starving siblings. */
    private final Map<FinalCarrierTickAdmissionKey, Integer> finalCarrierTickAdmissionRetryAttempts =
            new HashMap<>();
    private final Map<FinalCarrierTickAdmissionKey, Long> finalCarrierTickAdmissionRetryAfter =
            new HashMap<>();
    /** Worker results are accepted only by the owner, which also owns the TerrainAccessor receipt ledger. */
    private final ConcurrentLinkedQueue<FinalCarrierTickAdmissionCompletion>
            finalCarrierTickAdmissionCompletions = new ConcurrentLinkedQueue<>();
    /**
     * Optional durable scheduled-tick authority. It stays absent until a production transaction owner can
     * atomically admit a lane with its receipt and settle a due row with every resulting world mutation.
     */
    private FinalCarrierTickScheduler finalCarrierTickScheduler;
    private FinalCarrierTickScheduler.LiveTypes finalCarrierTickLiveTypes;
    private FinalCarrierTickScheduler.TickSemantics finalCarrierTickSemantics;
    private FinalCarrierTickScheduler.CommittedMutationSink finalCarrierTickMutationSink;
    /** Optional exact-payload transaction owner for the six non-tick, non-BEES semantic lanes. */
    private FinalCarrierLaneInstaller finalCarrierLaneInstaller;
    /** Installed schema-4 SPWN definitions, indexed both by durable identity and occupied cell. */
    private final Map<String, SpawnerAggregate> finalCarrierSpawnerInstallations = new HashMap<>();
    private final Map<BlockPos, SpawnerAggregate.PlannedSpawner> finalCarrierSpawnersByPosition =
            new HashMap<>();
    /** Durable BENT remains authority; these are resident decorated-pot interaction projections. */
    private final Map<String, List<GeneratedDecoratedPotRuntime>>
            finalCarrierDecoratedPotInstallations = new HashMap<>();
    private final Map<BlockPos, GeneratedDecoratedPotRuntime> finalCarrierDecoratedPotsByPosition =
            new HashMap<>();
    /** Spring ENTS after-commit/death lifecycle owner; absent in standalone and focused fixtures. */
    private com.gameexpert.engine.persistence.finalcarrier.FinalCarrierPersistenceService
            finalCarrierPersistence;
    private ArchaeologyResultPersistenceForTest archaeologyResultPersistenceForTest;
    private com.gameexpert.engine.persistence.finalcarrier.FinalCarrierPersistenceService
            .CommittedEntityActivationPublisher finalCarrierEntityPublisher;
    private final GeneratedStructureEntitySystem generatedStructureEntities;
    /** At most one prepared/committed generated-cushion publication per authoritative entity. */
    private final Map<Long, GeneratedCushionPersistencePublication>
            generatedCushionPersistencePublications = new HashMap<>();
    /** One exact generated-Cushion durable revision may reach the live registry only once. */
    private final Set<GeneratedCushionUpdateIdentity> appliedGeneratedCushionUpdates = new HashSet<>();
    /** Exact one-use semantic actions whose policy facts were observed by this runtime. */
    private final IdentityHashMap<GeneratedCushionActionEvidence, GeneratedCushionActionEvidenceState>
            generatedCushionActionEvidence = new IdentityHashMap<>();
    /** Spring proxy owning the generated-Cushion durable transaction; attached before first tick. */
    private GeneratedStructureEntityMutationCoordinator generatedCushionMutations;
    /** Exact Spring first-open transaction owner; aggregate identity remains Runtime-private. */
    private GeneratedChestMinecartLootResolutionService generatedMinecartLootResolutions;
    /** Atomic Spring player-hand/Armor-Stand settlement owner. */
    private ContainerSettlementPersistenceService generatedArmorStandEquipmentSettlements;
    /** Durable after-commit events retained until idempotent owner-state publication succeeds. */
    private final Deque<GeneratedStructureEntityMutationCoordinator.CommittedCushionMutation>
            pendingCommittedGeneratedCushionMutations = new ArrayDeque<>();
    /** Rider cleanup intents survive submit rejection until their exact committed update is visible. */
    private final Map<Long, PendingGeneratedCushionRiderCleanup>
            pendingGeneratedCushionRiderCleanups = new LinkedHashMap<>();
    /** DRAINING logout authority is one exact action, actor, entity and live snapshot. */
    private final Map<Long, DrainingCushionLogoutAdmission>
            drainingCushionLogoutAdmissions = new HashMap<>();
    /** Retained until the bounded tick-outbound queue accepts the exact after-commit delta. */
    private final Deque<PendingGeneratedEntityMessage> pendingGeneratedEntityMessages =
            new ArrayDeque<>();
    /** Entity IDs visible in the current global simulation union. */
    private volatile Set<Long> visibleGeneratedEntityIds = Set.of();
    /** One atomic owner-published snapshot/cursor pair; WS readers never touch mutable indexes. */
    private volatile GeneratedEntityWelcomeView generatedEntityWelcomeView =
            new GeneratedEntityWelcomeView(List.of(), 0L);
    /** Owner-thread retry set; the persistence FIFO makes each idempotent DEAD transition ordered. */
    private final Set<Long> pendingStructureEntityDeaths = new HashSet<>();
    private final Set<Long> durableDeadStructureEntityIds = new HashSet<>();
    private volatile Set<Long> preparedActivationChunks = ConcurrentHashMap.newKeySet();
    /** Base natural placements are committed; full deferred structures have a separate completion barrier. */
    private volatile Set<Long> snapshotBaseReadyChunks = ConcurrentHashMap.newKeySet();
    private volatile Set<Long> snapshotStructureReadyChunks = ConcurrentHashMap.newKeySet();
    /**
     * Pre-decoration immutable sources let the activation planner reuse terrain already generated for delivery.
     * Values share the resident byte arrays; this map adds references, not another 96 KiB copy per chunk.
     */
    private volatile ConcurrentHashMap<Long, TerrainAccessor.SnapshotSource> activationPlanningSources =
            new ConcurrentHashMap<>();
    /**
     * The ordered activation lane reuses deterministic fallback terrain while one player-union is stationary.
     * Without this, every chunk plan creates a fresh detached view and regenerates the same missing boundary
     * neighbors. Replacing the view when the active union moves bounds its retained byte arrays to the current
     * streaming neighborhood instead of the total explored world.
     */
    private volatile SurfaceDecorator.BlockView activationPlanningFallback;
    /** Structure shape is derived only from canonical generated terrain; player edits win at application. */
    private volatile SurfaceDecorator.BlockView activationStructurePlanningFallback;
    /** Immutable union of chunks near at least one connected player, refreshed before every simulation turn. */
    private volatile Set<Long> activeSimulationChunks = Set.of();
    /** Per-key lifecycle tokens reject detached work from an earlier leave/re-enter cycle without cancelling overlap. */
    private volatile Map<Long, Long> activeChunkGenerations = Map.of();
    private long activationGenerationSequence;
    /** Last owner-observed player chunk centers; avoids rebuilding the 21x21 union when nobody crossed a boundary. */
    private Map<String, Long> activePlayerChunkCenters;
    /** Last owner-observed saved-bed chunks; keeps distant respawn data resident without simulating it. */
    private Map<String, Long> activePlayerBedChunks;
    /** Stable player order for shared preparation admission. */
    private List<String> activePlayerOrder = List.of();
    /** Active union plus one source-only border required to decide trees crossing its outer chunk boundaries. */
    private volatile Set<Long> treePlanningSourceChunks = Set.of();
    /**
     * Direct engine fixtures run before a scheduled owner exists.  They explicitly register only the chunks they
     * exercise, so their mob/boat/item assertions use the same bounded bucket path as a live player neighborhood.
     * Production start clears this bootstrap-only set before its first owner turn.
     */
    private final Set<Long> testSimulationChunks = new HashSet<>();
    private final ConcurrentHashMap<String, PlayerDemandCursor> playerDemandCursors =
            new ConcurrentHashMap<>();

    private long tickNo;
    private int emptyTicks;
    private volatile boolean disposed;
    /** Prevents new session/publication admission while shutdown drains already-owned callbacks. */
    private volatile TerminalPhase terminalPhase = TerminalPhase.RUNNING;
    /** Guarded by this runtime's monitor; closes live persistence admission before final drain. */
    private boolean persistenceCallbackAdmissionClosed;
    /** A leave transaction rejected while terminal draining; teardown must preserve the live player. */
    private volatile boolean terminalLeaveSettlementRejected;
    private volatile boolean started;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> tickTask;
    private Runnable onDispose;
    private final CompletableFuture<Void> disposalCompletion = new CompletableFuture<>();
    /** Lifecycle generation invalidates detached workers that observed RUNNING before teardown. */
    private volatile long lifecycleEpoch;
    /** One disposal flight owns the terminal transition; concurrent callers only join its futures. */
    private boolean disposalFlightStarted;
    private final AtomicBoolean disposalOwnerActionScheduled = new AtomicBoolean();
    private final AtomicBoolean disposalOwnerActionStarted = new AtomicBoolean();
    private final CompletableFuture<Boolean> disposalOwnerCompletion = new CompletableFuture<>();
    private final CompletableFuture<Boolean> disposalFlightCompletion = new CompletableFuture<>();
    private final AtomicBoolean terminalOwnerDrainScheduled = new AtomicBoolean();
    private final AtomicBoolean persistenceOwnerDrainScheduled = new AtomicBoolean();
    private final AtomicBoolean abortedOwnerCleanupDone = new AtomicBoolean();
    private volatile boolean terminalDeadlineReached;
    /** 상한을 넘긴 폐기 대기가 남긴 격리 표식. 재입장을 거부하되 백그라운드 재시도는 계속합니다. */
    private volatile boolean disposalStalled;
    private volatile long snapshotSendStallNanos =
            TimeUnit.MILLISECONDS.toNanos(DEFAULT_SNAPSHOT_SEND_STALL_MILLIS);
    private volatile boolean disposalPersistenceFailed;
    /** Callback failures are collected so one broken completion cannot strand later callbacks. */
    private final ConcurrentLinkedQueue<Throwable> persistenceCompletionFailures =
            new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Throwable> terminalCleanupFailures =
            new ConcurrentLinkedQueue<>();
    private final AtomicBoolean disposalPersistenceFailureReported = new AtomicBoolean();
    private final AtomicLong persistenceCompletionsRun = new AtomicLong();
    private volatile boolean terminalizingPersistenceCompletions;
    private volatile TerminalOutcome terminalOutcome =
            new TerminalOutcome(TerminalOutcomeKind.NONE, 0L, List.of());
    private final Map<FinalCarrierTickScheduler.TickKey, FinalCarrierTickPublicationProgress>
            pendingFinalCarrierTickPublications = new HashMap<>();
    private volatile FinalCarrierTickPublicationOutcome lastFinalCarrierTickPublicationOutcome =
            FinalCarrierTickPublicationOutcome.NONE;
    private final EnumSet<FinalCarrierTickScheduler.DrainStatus> finalCarrierDrainStatuses =
            EnumSet.noneOf(FinalCarrierTickScheduler.DrainStatus.class);
    /** Only the scheduled owner, or a pre-start direct fixture, may execute owner callbacks. */
    private volatile Thread ownerThread;
    /** Final physical ground state is dirtied once; retries must not manufacture a new revision. */
    private boolean disposalGroundCheckpointPrepared;
    /** Final projectile ledger is dirtied once; retries resubmit that same frozen revision. */
    private boolean disposalProjectileCheckpointPrepared;
    private List<PlayerTickState> disposalPlayers = List.of();
    private long persistedDayCount;
    private long persistedWorldTime;
    private long persistedGameTimeMcTicks;
    private long persistedTraderNextAttemptTick;
    private int persistedTraderChancePercent;
    private boolean clockStatePersistencePending;
    private AnimalBlockTickPersistenceService.WorldStore animalBlockTickStore;
    private AnimalSettlementPersistenceService animalSettlements;
    /** Complete durable Trial Spawner aggregate, hydrated before the first owner tick. */
    private TrialStorage trialPersistence;
    /** Prevents identical unacknowledged Trial edge prefixes from entering the serial writer twice. */
    private boolean trialPersistenceInFlight;
    private static final int CLOCK_CHECKPOINT_INTERVAL_MC_TICKS = 200;
    /** Exact state generations reserved for a leave save; the admission is bounded before submission. */
    private final ConcurrentHashMap<DepartedSaveKey, DepartedSave> pendingDepartedSaves =
            new ConcurrentHashMap<>();
    /** Retains only the bounded set of committed generations so a fast commit can coalesce late callbacks. */
    private final LinkedHashMap<DepartedSaveKey, Boolean> completedDepartedSaveGenerations =
            new LinkedHashMap<>();

    /**
     * Durable result for one exact final-carrier lane payload. Implementations must use the supplied stable
     * source fingerprint and commit the complete payload exactly once before returning a terminal result.
     */
    public enum FinalCarrierInstallResult {
        COMMITTED, ALREADY_COMMITTED, REJECTED, RETRY
    }

    /** Result published only after the exact canonical first-open pair has crossed the owner. */
    enum CanonicalLootOpenStatus { READY, REJECTED, TERMINAL }

    /**
     * Immutable first-open request facts. The player object is the live session identity; the
     * connection ID, request token, clicked block, ordered topology, and initial reach result are
     * all retained until the owner decides whether a UI may be published.
     */
    record CanonicalLootOpenRequest(
            PlayerTickState session,
            String connectionId,
            long requestToken,
            int clickedBlock,
            boolean reached,
            BlockPos clicked,
            List<BlockPos> orderedHalves,
            CanonicalLootContainerKind containerKind,
            Consumer<CanonicalLootOpenCompletion> completion) {
        CanonicalLootOpenRequest {
            if (session == null && connectionId != null) {
                throw new IllegalArgumentException("connection requires a live session");
            }
            if (requestToken < 0L) {
                throw new IllegalArgumentException("canonical LOOT request token must be non-negative");
            }
            if (clicked == null || orderedHalves == null || orderedHalves.isEmpty()
                    || orderedHalves.size() > 2) {
                throw new IllegalArgumentException("one or two canonical LOOT halves are required");
            }
            if (orderedHalves.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("canonical LOOT topology contains null");
            }
            if (containerKind == null || completion == null) {
                throw new NullPointerException("canonical LOOT request boundary is incomplete");
            }
            orderedHalves = List.copyOf(orderedHalves);
        }
    }

    record CanonicalLootOpenCompletion(
            CanonicalLootOpenRequest request, CanonicalLootOpenStatus status) {
        boolean ready() {
            return status == CanonicalLootOpenStatus.READY;
        }
    }

    private record CanonicalLootOpenHalf(
            BlockPos position, CanonicalLootContainerKind containerKind,
            boolean playerOverride) {}

    private record CanonicalLootOpenKey(List<CanonicalLootOpenHalf> orderedHalves) {
        CanonicalLootOpenKey {
            orderedHalves = List.copyOf(orderedHalves);
        }
    }

    private static final class CanonicalLootOpenReservation {
        private final CanonicalLootOpenKey key;
        private final List<CanonicalLootOpenRequest> requests = new ArrayList<>();
        private boolean submissionReserved;
        private boolean retryQueued;
        private boolean cancelled;

        private CanonicalLootOpenReservation(CanonicalLootOpenKey key) {
            this.key = key;
        }
    }

    /**
     * Transaction boundary still missing from the Spring repositories. A runtime cache, dirty buffer, or a
     * sequence of independent service calls is not a valid implementation: payload and stable receipt must be
     * committed atomically. OWNERS is collision provenance only and must never be interpreted as gameplay or
     * presentation ownership.
     */
    @FunctionalInterface
    public interface FinalCarrierLaneInstaller {
        FinalCarrierInstallResult installAndCommit(long worldId, int chunkX, int chunkZ,
                TerrainAccessor.FinalLiveCarrierLane lane, String sourceFingerprint,
                NeutralFinalChunk.Sidecars exactPayload,
                FinalCarrierGameplayInstaller gameplayInstaller);

        default void recoverWorld(long worldId, FinalCarrierGameplayInstaller gameplayInstaller) {
        }

        default void recoverChunk(long worldId, int chunkX, int chunkZ,
                FinalCarrierGameplayInstaller gameplayInstaller) {
        }
    }

    /** Only authorities with no live-runtime callbacks may opt into detached payload writes. */
    public interface FinalCarrierDurablePayloadInstaller extends FinalCarrierLaneInstaller {
        boolean supportsDurablePayload(TerrainAccessor.FinalLiveCarrierLane lane);

        /** A lane whose exact payload needs no resident activation may also run detached. */
        default boolean supportsDurablePayload(TerrainAccessor.FinalLiveCarrierLane lane,
                NeutralFinalChunk.Sidecars exactPayload) {
            return supportsDurablePayload(lane);
        }

        FinalCarrierInstallResult installDurablePayloadAndCommit(long worldId,
                int chunkX, int chunkZ, TerrainAccessor.FinalLiveCarrierLane lane,
                String sourceFingerprint, NeutralFinalChunk.Sidecars exactPayload);

        /**
         * A gameplay lane whose durable commit may run detached while the owner applies the resident
         * install afterwards. The worker never sees resident state: {@code verdict} must decide from
         * an owner-made snapshot, and any verdict other than COMMITTED/ALREADY_COMMITTED rolls the
         * transaction back so the owner decides the lane itself.
         */
        default boolean supportsDetachedGameplayPayload(TerrainAccessor.FinalLiveCarrierLane lane,
                NeutralFinalChunk.Sidecars exactPayload) {
            return false;
        }

        default FinalCarrierInstallResult installDetachedGameplayAndCommit(long worldId,
                int chunkX, int chunkZ, TerrainAccessor.FinalLiveCarrierLane lane,
                String sourceFingerprint, NeutralFinalChunk.Sidecars exactPayload,
                FinalCarrierGameplayInstaller verdict) {
            throw new UnsupportedOperationException("detached gameplay payload");
        }

        default boolean supportsDetachedChunkRecovery() {
            return false;
        }

        /** Replays a chunk without resident callbacks, or fails when one would be needed. */
        default void recoverChunkDetached(long worldId, int chunkX, int chunkZ) {
            throw new UnsupportedOperationException("detached chunk recovery");
        }

        /**
         * Replays a chunk on a worker, deciding each resident activation with {@code verdict}, an
         * owner-made snapshot of the chunk. Any verdict other than COMMITTED/ALREADY_COMMITTED rolls
         * the replay back; the owner then applies exactly the approved activations, or replays the
         * chunk itself when the snapshot could not prove one.
         */
        default void recoverChunkDetached(long worldId, int chunkX, int chunkZ,
                FinalCarrierGameplayInstaller verdict) {
            recoverChunkDetached(worldId, chunkX, chunkZ);
        }
    }

    /** Idempotent authoritative aggregate activation keyed by the supplied durable identity. */
    @FunctionalInterface
    public interface FinalCarrierGameplayInstaller {
        FinalCarrierInstallResult install(String installationIdentity,
                TerrainAccessor.FinalLiveCarrierLane lane, NeutralFinalChunk.Sidecars exactPayload,
                List<ArchaeologyBrushableAggregate> archaeologyBrushables);

        default FinalCarrierInstallResult installPrepared(String installationIdentity,
                TerrainAccessor.FinalLiveCarrierLane lane, NeutralFinalChunk.Sidecars exactPayload,
                List<ArchaeologyBrushableAggregate> archaeologyBrushables,
                List<SpawnerAggregate> spawnerAggregates) {
            return install(installationIdentity, lane, exactPayload, archaeologyBrushables);
        }

        /** BENT remains durable authority; this is only its resident server projection. */
        default FinalCarrierInstallResult installBlockEntityPlan(FinalCarrierBlockEntityPlan plan) {
            return FinalCarrierInstallResult.COMMITTED;
        }
    }

    /**
     * Test-only archaeology persistence seam. Production consumes through the bound final-carrier
     * service, while focused runtime tests may inject one typed result authority without enabling
     * the standalone in-memory entity fallback.
     */
    @FunctionalInterface
    interface ArchaeologyResultPersistenceForTest {
        com.gameexpert.engine.persistence.finalcarrier.FinalCarrierPersistenceService
                .ArchaeologyResultSettlement consume(long worldId, int x, int y, int z,
                        String installationIdentity, String candidateExactBlockState,
                        long candidateTargetRevision, String candidateResultIdentity,
                        long proposedEntityId);
    }

    public WorldRuntime(Long worldId, int seed, EngineContext ctx) {
        this(worldId, seed, ctx, null, null, null);
    }

    public WorldRuntime(Long worldId, int seed, EngineContext ctx, ChestPersistenceService chestPersistence) {
        this(worldId, seed, ctx, chestPersistence, null, null);
    }

    public WorldRuntime(Long worldId, int seed, EngineContext ctx, ChestPersistenceService chestPersistence,
                        MobPersistenceService mobPersistence) {
        this(worldId, seed, ctx, chestPersistence, null, mobPersistence);
    }

    public WorldRuntime(Long worldId, int seed, EngineContext ctx, ChestPersistenceService chestPersistence,
                        FurnacePersistenceService furnacePersistence,
                        MobPersistenceService mobPersistence) {
        this(worldId, seed, ctx, chestPersistence, furnacePersistence, null, mobPersistence);
    }

    public WorldRuntime(Long worldId, int seed, EngineContext ctx, ChestPersistenceService chestPersistence,
                        FurnacePersistenceService furnacePersistence,
                        CampfirePersistenceService campfirePersistence,
                        MobPersistenceService mobPersistence) {
        this(worldId, seed, ctx, chestPersistence, furnacePersistence, campfirePersistence,
                mobPersistence, null);
    }

    public WorldRuntime(Long worldId, int seed, EngineContext ctx, ChestPersistenceService chestPersistence,
                        FurnacePersistenceService furnacePersistence,
                        CampfirePersistenceService campfirePersistence,
                        MobPersistenceService mobPersistence,
                        WorldTimePersistenceService worldTimePersistence) {
        this(worldId, seed, ctx, chestPersistence, furnacePersistence, campfirePersistence,
                mobPersistence, worldTimePersistence, Difficulty.DEFAULT);
    }

    public WorldRuntime(Long worldId, int seed, EngineContext ctx, ChestPersistenceService chestPersistence,
                        FurnacePersistenceService furnacePersistence,
                        CampfirePersistenceService campfirePersistence,
                        MobPersistenceService mobPersistence,
                        WorldTimePersistenceService worldTimePersistence,
                        Difficulty difficulty,
                        WorldMapPersistenceService... worldMapPersistenceServices) {
        this(worldId, seed, ctx, chestPersistence, furnacePersistence, campfirePersistence,
                mobPersistence, worldTimePersistence, difficulty,
                detachedCanonicalSource(worldId, seed),
                worldMapPersistenceServices);
    }

    private static ChunkProductSource detachedCanonicalSource(Long worldId, int seed) {
        long effectiveWorldId = worldId == null ? 1L : worldId;
        return new com.gameexpert.terrain.CanonicalOriginChunkProductSource(
                new com.gameexpert.terrain.persistence.InMemoryCanonicalWorldgenStore(),
                effectiveWorldId, seed,
                com.gameexpert.world.WorldGenerationProfiles.newWorldProfile(), 0L);
    }

    WorldRuntime(Long worldId, int seed, EngineContext ctx, ChestPersistenceService chestPersistence,
                        FurnacePersistenceService furnacePersistence,
                        CampfirePersistenceService campfirePersistence,
                        MobPersistenceService mobPersistence,
                        WorldTimePersistenceService worldTimePersistence,
                        Difficulty difficulty,
                        ChunkProductSource chunkProductSource,
                        WorldMapPersistenceService... worldMapPersistenceServices) {
        this.worldId = worldId;
        this.generatedStructureEntities = new GeneratedStructureEntitySystem(worldId);
        this.seed = seed;
        this.difficulty = Difficulty.orDefault(difficulty);
        this.worldEpoch = WORLD_EPOCH_SEQUENCE.incrementAndGet();
        this.ctx = ctx;
        this.chestPersistence = chestPersistence;
        this.furnacePersistence = furnacePersistence;
        this.campfirePersistence = campfirePersistence;
        this.mobPersistence = mobPersistence;
        this.worldTimePersistence = worldTimePersistence;
        if (worldMapPersistenceServices != null && worldMapPersistenceServices.length > 1) {
            throw new IllegalArgumentException("only one world map persistence service is supported");
        }
        this.worldMapPersistence = worldMapPersistenceServices == null
                || worldMapPersistenceServices.length == 0
                        ? null : worldMapPersistenceServices[0];
        List<WorldMapData> persistedMaps = this.worldMapPersistence == null
                ? List.of() : this.worldMapPersistence.loadWorld(worldId);
        this.worldMaps = new WorldMapRuntime(
                worldId, persistedMaps == null ? List.of() : persistedMaps);
        long initialDayCount = worldTimePersistence == null
                ? 0
                : worldTimePersistence.loadDayCount(worldId);
        long initialWorldTime = worldTimePersistence == null
                ? 0
                : worldTimePersistence.loadWorldTime(worldId);
        long initialGameTimeMcTicks = worldTimePersistence == null
                ? 0
                : worldTimePersistence.loadGameTimeMcTicks(worldId);
        WorldTimePersistenceService.TraderWindow initialTraderWindow =
                worldTimePersistence == null ? null : worldTimePersistence.loadTraderWindow(worldId);
        this.persistedDayCount = initialDayCount;
        this.persistedWorldTime = initialWorldTime;
        this.persistedGameTimeMcTicks = initialGameTimeMcTicks;
        int qaFrozenTime = ctx.properties().qaFrozenTime();
        this.clock = qaFrozenTime >= 0 && qaFrozenTime < WorldClock.DAY_LENGTH
                ? new WorldClock(qaFrozenTime, initialDayCount, initialGameTimeMcTicks, true)
                : new WorldClock(initialWorldTime, initialDayCount, initialGameTimeMcTicks, false);
        List<WorldBlockDiff> persistedDiffs = ctx.diffRepository().findByWorldId(worldId);
        for (WorldBlockDiff diff : persistedDiffs) {
            BlockPos pos = new BlockPos(diff.getX(), diff.getY(), diff.getZ());
            durableBlockDiffCells.put(pos,
                    packedBlockCell(diff.getBlockType(), diff.getBlockState()));
            if (diff.getMobMutationKey() != null) {
                durableBlockDiffSources.put(pos, diff.getMobMutationKey());
            }
            protectedDecorationEdits.add(pos);
            long editedChunkKey = blockChunkKey(pos.x(), pos.z());
            protectedDecorationEditChunks.add(editedChunkKey);
            replayableOverlayChunks.remove(editedChunkKey);
            indexOverlay(pos, diff.getBlockType(), diff.getBlockState());
        }
        this.chunkProductSource = java.util.Objects.requireNonNull(
                chunkProductSource, "world chunk product source");
        this.accessor = new TerrainAccessor(seed, worldId, ctx.diffRepository(),
                this.chunkProductSource);
        this.accessor.setPlanningDiffSnapshot(persistedDiffs);
        this.activationPlanningFallback = accessor.detachedPlanningView();
        this.activationStructurePlanningFallback = accessor.detachedGeneratedPlanningView();
        this.blockStates = new BlockStateStorage(persistedDiffs);
        this.blockStates.setUnwrittenStateSource(this::generatedCarrierState);
        this.surfaceDecorator = new SurfaceDecorator(seed);
        this.explorationDecorator = new ExplorationDecorator(seed);
        this.fluidSim = new FluidSimulator(new FluidSimulator.FluidWorld() {
            @Override
            public int getBlock(int x, int y, int z) {
                int block = WorldTickLoop.residentBlockType(accessor, x, y, z);
                if (Boolean.TRUE.equals(strictFinalCarrierFluidReads.get())
                        && block == WorldTickLoop.UNAVAILABLE_BLOCK) {
                    throw new FinalCarrierTickScheduler.UnavailableNeighborhood(
                            "final-carrier fluid plan crossed resident chunks");
                }
                return block;
            }

            @Override
            public boolean isChunkActivated(int chunkX, int chunkZ) {
                return fluidSimulationReady(chunkX, chunkZ);
            }

            @Override
            public boolean isWaterloggedSource(int x, int y, int z) {
                int blockId = WorldTickLoop.residentBlockType(accessor, x, y, z);
                if (blockId == WorldTickLoop.UNAVAILABLE_BLOCK) return false;
                return WaterloggedStates.isWaterloggedAt(accessor, blockId,
                        blockStates.get(x, y, z, blockId), x, y, z);
            }

            @Override
            public void beforeNaturalFluidReplacement(
                    int x, int y, int z, int replacedBlock, boolean dropResources) {
                if (!dropResources) return;
                var crop = com.gameexpert.engine.crop.CropRules.forCrop(replacedBlock);
                if (crop != null) {
                    int state = blockStates.get(x, y, z, replacedBlock);
                    itemSystem.spawnCropDrops(crop, state, x + 0.5, y + 0.5, z + 0.5);
                } else {
                    itemSystem.spawnBlockDrop(
                            (short) replacedBlock, x + 0.5, y + 0.5, z + 0.5);
                }
            }

            @Override
            public void setBlock(int x, int y, int z, int blockType) {
                if (fleshCellReserved(x, y, z)) return;
                int current = WorldTickLoop.residentBlockType(accessor, x, y, z);
                if (current == WorldTickLoop.UNAVAILABLE_BLOCK) return;
                if (blockType == Blocks.LILY_PAD) {
                    boolean supported = current == Blocks.AIR
                            && WorldTickLoop.residentBlockType(accessor, x, y - 1, z) == Blocks.WATER_SOURCE;
                    if (!supported || !InventoryRules.commitPendingLilyPlace()) {
                        InventoryRules.cancelPendingLilyPlace();
                        broadcastDirectBlock(x, y, z, current);
                        return;
                    }
                } else {
                    // FluidSimulator의 no-op 등으로 남은 이전 요청이 다음 설치에 섞이지 않게 한다.
                    InventoryRules.cancelPendingLilyPlace();
                }
                // [DIMENSION-EXT] 교체 직전에 아직 열리지 않은 콘텐츠 상자를 채워 뒤따르는 쏟기가 보게 한다.
                if (blockType != current) materializeDimensionContainerBeforeReplacement(x, y, z);
                // 채굴·폭발·지지 상실·외부 교체가 모두 거치는 단일 변경 경계에서 남은 음식을 보존한다.
                if (current == Blocks.CAMPFIRE && blockType != Blocks.CAMPFIRE) {
                    WorldTickLoop.dropCampfireContents(WorldRuntime.this, x, y, z);
                }
                if (current == Blocks.BREWING_STAND && blockType != Blocks.BREWING_STAND) {
                    WorldTickLoop.dropBrewingContents(WorldRuntime.this, x, y, z);
                }
                if ((current == Blocks.BEE_NEST || current == Blocks.BEEHIVE)
                        && blockType != current && mobSystem != null) {
                    mobSystem.releaseBeesFromHive(x, y, z, null);
                }
                setIndexedOverlay(x, y, z, blockType);
                setBlockState(x, y, z, blockType, 0);
                tickBlockChanges.put(new BlockPos(x, y, z), (short) blockType);

                // 수원 지지가 사라지면 위 수련잎도 정상 편집 경로(AIR diff + 드랍)로 제거한다.
                if (blockType != Blocks.WATER_SOURCE && y + 1 <= Blocks.MAX_Y
                        && WorldTickLoop.residentBlockType(accessor, x, y + 1, z) == Blocks.LILY_PAD) {
                    setIndexedOverlay(x, y + 1, z, Blocks.AIR);
                    removeBlockState(x, y + 1, z);
                    tickBlockChanges.put(new BlockPos(x, y + 1, z), (short) Blocks.AIR);
                    itemSystem.spawnBlockDrop((short) Blocks.LILY_PAD,
                            x + 0.5, y + 1.5, z + 0.5);
                }
            }
        });
        this.fallingBlockSystem = new FallingBlockSystem(
                (x, y, z) -> WorldTickLoop.residentBlockType(accessor, x, y, z), fluidSim::applyChange);
        this.generatedFluidActivation = new GeneratedFluidActivation(new GeneratedFluidActivation.World() {
            @Override public int lookup(int x, int y, int z) {
                int cx=Math.floorDiv(x,Blocks.CHUNK_X), cz=Math.floorDiv(z,Blocks.CHUNK_Z);
                if (!snapshotStructureReadyChunks.contains(chunkKey(cx,cz)) || !accessor.isChunkActivated(cx,cz)) return -1;
                var block=accessor.residentBlock(x,y,z);
                return block.isAvailable() ? block.blockType() : -1;
            }
            @Override public void visit(int cx,int cz,GeneratedFluidActivation.Edge edge,GeneratedFluidActivation.Visitor visitor) {
                visitGeneratedFluids(cx,cz,edge,visitor);
            }
            @Override public void activate(int x,int y,int z,int block) {
                fluidSim.activateGeneratedFluid(x,y,z,block);
            }
        });
        this.accessor.setChunkAccessListener(this::enqueueChunkActivation);
        this.accessor.setChunkEvictionListener(this::handleChunkEviction);
        this.environment = new EnvironmentSystem(accessor, this::blockState, (x, y, z) -> false,
                (x, y, z, blockType, blockState) -> {
                    setBlockState(x, y, z, blockType, blockState);
                    tickBlockChanges.put(new BlockPos(x, y, z), (short) blockType);
                    environmentSupportChanges.addLast(new BlockPos(x, y, z));
                }, ctx.properties().qaInvulnerable(), difficulty().starvationMinHealth());
        this.itemSystem = new ItemEntitySystem(this, groundEntityIds);
        // MobWorldViewAdapter captures this system in MobSystem's constructor (Enderman rain checks), so weather
        // must exist before the adapter is created.  The reverse order previously threw an NPE mid mob phase and
        // made the WorldTickLoop warning misreport its elapsed time as an unmeasured remainder.
        // 낙뢰 파이프라인: 후보 → [훅: 피뢰침 유인(구리 트랙)] → 효과. 효과는 몹 시스템 큐로
        // 넘겨 몹 틱 시작 지점에서 적용한다(같은 틱의 스폰/디스폰 diff 에 실린다).
        this.weatherSystem = new WeatherSystem(worldId, seed, ctx.broadcaster(), this::nextEventId,
                this::lightningTargets, this::lightningStrikeHeight,
                this::onLightningStrike);
        this.mobSystem = new MobSystem(this, mobPersistence);
        if (initialTraderWindow != null) {
            this.mobSystem.restoreTraderWindow(initialTraderWindow.nextAttemptTick(),
                    initialTraderWindow.chancePercent());
        }
        long[] traderWindow = this.mobSystem.traderWindowState();
        this.persistedTraderNextAttemptTick = traderWindow[0];
        this.persistedTraderChancePercent = (int) traderWindow[1];
        // 피뢰침 유인은 몹 시스템의 하이트맵을 읽으므로 그것이 선 뒤에 건다. 훅은 RNG lane 밖이라
        // 이 배선이 붙어도 날씨 난수열은 그대로다.
        this.weatherSystem.setStrikeHook(this::redirectToLightningRod);
        this.boatSystem = new BoatSystem(this);
        this.placedEntities = new PlacedEntitySystem(this);
        this.cushionSystem = new CushionSystem(this);
        this.tickLoop = new WorldTickLoop(this);
        if (worldTimePersistence != null) this.tickLoop.restoreRedstone(worldTimePersistence.loadRedstoneState(worldId));
        // 주민 직업의 정본은 POI 점유 원장이다. 거래 트랙은 여기서 그 원장을 읽도록 배선된다.
        this.tickLoop.bindVillagerProfessions(this.mobSystem.villagerProfessionSource());
        // 거래 진행도(레벨·XP·오퍼별 재고·수요·재입고 시각)는 직업과 별도 lane 이다. 직업만 살아
        // 돌아오고 재고가 0 으로 리셋되지 않도록 여기서 함께 복원한다.
        if (mobPersistence != null) {
            List<VillagerTradeSnapshot> restoredTrades =
                    mobPersistence.loadVillagerTradeStates(worldId);
            this.tickLoop.villagerTrades().restore(restoredTrades);
            // Trade rows are a third transaction after the mob aggregate. A crash may leave one
            // behind after conversion/death; converge it to a durable removal before an unrelated
            // future mob can reuse the id and inherit the old offers/reputation-independent stock.
            for (VillagerTradeSnapshot trade : restoredTrades) {
                if (!this.mobSystem.isLiveMerchant(trade.mobId())) {
                    this.tickLoop.villagerTrades().forget(trade.mobId());
                }
            }
        }
        // 평판의 정본은 주민 각자의 gossip 원장이다. 가격 할인과 거래 후 TRADING 적립이
        // 같은 원장을 본다.
        this.tickLoop.bindVillagerReputations(this.mobSystem.villagerReputationSource());
        // [RAID-OMEN] 마을의 영웅 효과의 증폭이 그 플레이어의 주민 거래 가격을 깎는다(Villager#updateSpecialPrices).
        this.tickLoop.villagerTrades().bindHeroAmplifiers(nickname -> {
            PlayerTickState hero = players.get(nickname);
            if (hero == null || !hero.statusEffects().has(
                    com.gameexpert.engine.effect.StatusEffect.HERO_OF_THE_VILLAGE)) return -1;
            return hero.statusEffects().amplifier(com.gameexpert.engine.effect.StatusEffect.HERO_OF_THE_VILLAGE);
        });
        if (chestPersistence != null) {
            chestPersistence.loadWorld(worldId, chestStorage, shulkerStorage);
        }
        if (furnacePersistence != null) {
            FurnacePersistenceService.CarryState carry =
                    furnacePersistence.loadWorld(worldId, furnaceStorage);
            xpOrbSystem.restoreFurnaceXpCarryMilli(carry.amount(), carry.revision());
        }
        if (campfirePersistence != null) campfirePersistence.loadWorld(worldId, campfireStorage);
        attachMobMutationJournalStore(new InMemoryMobMutationStore());
    }

    /**
     * 몹 변형 저널의 durable 저장소를 붙이고 복구합니다. 첫 틱 전에 한 번만 호출합니다.
     *
     * <p>복구는 두 가지를 한다. (1) 기록만 되고 적용되지 못한 항목을 pending 으로 되살려 다음
     * 틱에 멱등 적용하고, (2) applied 행과 실제 DB diff가 일치하는 좌표만 DB diff 복원이 넣어 둔
     * 플레이어 편집 집합에서 몹 변형 집합으로 되돌려 출처 오염을 없앤다.
     */
    public void attachMobMutationJournalStore(MobMutationJournal.Store store) {
        this.mobMutationJournal = new MobMutationJournal(store, new RuntimeMobMutationWorld());
        this.mobMutationJournal.recover();
        for (MobMutationJournal.Entry entry : this.mobMutationJournal.entriesForTest()) {
            if (!this.mobMutationJournal.ownsPosition(entry.x(), entry.y(), entry.z())) continue;
            BlockPos pos = new BlockPos(entry.x(), entry.y(), entry.z());
            if (protectedDecorationEdits.remove(pos)) mobMutationSites.add(pos);
        }
    }

    /** Spring 런타임의 주민 사회 상태를 첫 틱 전에 복구하고 기존 몹 저장 레인에 결합한다. */
    void attachVillagerSocietyPersistence(VillagerSocietyPersistenceService service) {
        villagerSocietyPersistence = service;
        if (service == null) return;
        mobSystem.restoreVillagerSociety(
                service.loadStates(worldId), service.loadBedClaims(worldId));
    }

    /** 몹 유발 변형 저널. 역할 행동은 이 저널을 통해서만 월드를 바꾼다. */
    public MobMutationJournal mobMutationJournal() {
        return mobMutationJournal;
    }

    /** durable 확인된 저널 항목을 적용합니다. 틱 소유 스레드에서만 호출합니다. */
    int pumpMobMutationJournal() {
        if (terminalPhase != TerminalPhase.RUNNING || mobMutationJournal == null) return 0;
        return mobMutationJournal.pump();
    }

    /** 저널 소유 좌표인가. 구조물 재생성·자연 overlay가 덮으면 안 되는 칸이다. */
    boolean isMobMutationSite(int x, int y, int z) {
        return mobMutationSites.contains(new BlockPos(x, y, z));
    }

    /** 저널 전용 편집 경로. 플레이어 편집 집합을 오염시키지 않고 영속·브로드캐스트는 그대로 탄다. */
    private void applyJournaledMobMutation(
            int x, int y, int z, int blockType, int blockState, String sourceKey) {
        if (!ownerTurnMayContinue()) return;
        applyingMobMutation = true;
        applyingMobMutationKey = sourceKey;
        try {
            setIndexedOverlay(x, y, z, blockType, blockState);
            if (!ownerTurnMayContinue()) return;
            setBlockState(x, y, z, blockType, blockState);
            if (!ownerTurnMayContinue()) return;
            tickBlockChanges.put(new BlockPos(x, y, z), (short) blockType);
        } finally {
            applyingMobMutationKey = null;
            applyingMobMutation = false;
        }
    }

    /** {@link MobMutationJournal}이 보는 월드 포트. */
    private final class RuntimeMobMutationWorld implements MobMutationJournal.World {
        @Override public short blockAt(int x, int y, int z) {
            return (short) WorldTickLoop.residentBlockType(accessor, x, y, z);
        }

        @Override public short stateAt(int x, int y, int z) {
            int block = WorldTickLoop.residentBlockType(accessor, x, y, z);
            if (block < 0) return 0;
            return (short) blockStates.get(x, y, z, block);
        }

        @Override public boolean isPlayerEdit(int x, int y, int z) {
            return protectedDecorationEdits.contains(new BlockPos(x, y, z));
        }

        @Override public boolean hasDurableBlockDiff(int x, int y, int z) {
            // constructor가 한 번 읽은 repository 스냅샷만 본다. mutable overlay/provenance 집합이나
            // tick별 DB 조회로 marker-before-diff 크래시를 잘못 숨기지 않는다.
            return durableBlockDiffCells.containsKey(new BlockPos(x, y, z));
        }

        @Override public boolean durableBlockDiffMatches(
                int x, int y, int z, short blockType, short blockState) {
            Integer persisted = durableBlockDiffCells.get(new BlockPos(x, y, z));
            return persisted != null && persisted == packedBlockCell(blockType, blockState);
        }

        @Override public String durableBlockDiffSourceKey(int x, int y, int z) {
            return durableBlockDiffSources.get(new BlockPos(x, y, z));
        }

        @Override public void claimDurableMobMutation(
                int x, int y, int z, String sourceKey) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!sourceKey.equals(durableBlockDiffSources.get(pos))) return;
            protectedDecorationEdits.remove(pos);
            mobMutationSites.add(pos);
        }

        @Override public boolean isStructureOwned(int x, int y, int z) {
            return structureOverlaySites.contains(structurePositionKey(x, y, z));
        }

        @Override public void applyMobMutation(int x, int y, int z, int blockType, int blockState) {
            applyJournaledMobMutation(x, y, z, blockType, blockState, null);
        }

        @Override public void applyMobMutation(
                int x, int y, int z, int blockType, int blockState, String sourceKey) {
            applyJournaledMobMutation(x, y, z, blockType, blockState, sourceKey);
        }
    }

    /**
     * 저장소가 붙기 전(그리고 단위 테스트)의 기본 저장소. 프로세스 밖 durability 는 없지만
     * "기록 → 적용" 순서와 중복 거부 의미는 동일하다.
     */
    private static final class InMemoryMobMutationStore implements MobMutationJournal.Store {
        private final Map<String, MobMutationJournal.Entry> entries = new LinkedHashMap<>();

        @Override public boolean append(MobMutationJournal.Entry entry, Runnable onDurable) {
            entries.put(entry.key(), entry);
            onDurable.run();
            return true;
        }

        @Override public void markApplied(MobMutationJournal.Entry entry) { }

        @Override public void markSuperseded(MobMutationJournal.Entry entry) { }

        @Override public void remove(MobMutationJournal.Entry entry) {
            entries.remove(entry.key());
        }

        @Override public List<MobMutationJournal.Entry> loadAll() {
            return List.copyOf(entries.values());
        }
    }

    private void handleChunkEviction(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        generatedFluidActivation.onChunkEvicted(chunkX,chunkZ);
        if (finalCarrierTickScheduler != null) {
            finalCarrierTickScheduler.evictChunk(chunkX, chunkZ);
        }
        preparedActivationChunks.remove(key);
        pendingChunkActivationPlans.remove(key);
        pendingFinalCarrierClaims.remove(key);
        structureReadyPasses.remove(key);
        deferredStructureReadyPasses.remove(key);
        deferredRecoveryLockRetries.remove(key);
        deferredActivationLockRetries.remove(key);
        queuedFinalCarrierTickAdmissionChunks.remove(key);
        pendingFinalCarrierTickAdmissionOrder.removeIf(queuedKey -> queuedKey == key);
        pendingChunkPreparations.remove(key);
        chunkActivationDemands.remove(key);
        preparedChunkActivations.remove(key);
        activationPlanningFailures.remove(key);
        preparedChunkDemands.remove(key);
        pendingChunkActivationApplications.remove(key);
        pendingStructureDecorations.remove(key);
        canonicalLootCandidates.keySet().removeIf(position ->
                blockChunkKey(position.x(), position.z()) == key);
        snapshotBaseReadyChunks.remove(key);
        snapshotStructureReadyChunks.remove(key);
        chunkRecoveryWorks.remove(key);
        tickActivationPreparations.remove(key);
        activationPlanningSources.remove(key);
        invalidateSnapshotFrames(chunkX, chunkZ);
        invalidateLightChunkCaches(chunkX, chunkZ);
        mobSystem.onChunkEvicted(chunkX, chunkZ);
        fluidSim.onChunkDeactivated(chunkX, chunkZ);
        furnaceStorage.deactivateChunk(chunkX, chunkZ);
        brewingStorage.deactivateChunk(chunkX, chunkZ);
        campfireStorage.deactivateChunk(chunkX, chunkZ);
        hopperSystem.deactivateChunk(chunkX, chunkZ);
        tickLoop.onFireChunkReleased(chunkX, chunkZ);
        // 시뮬레이션에서 빠진 청크의 피뢰침은 유인 후보에서 빠진다(바닐라도 로드된 청크만 친다).
        // 재활성화 때 persisted diff 로 그대로 되살아난다 — enqueueChunkActivation 참조.
        lightningRods.forgetChunk(chunkX, chunkZ);
    }

    /**
     * Chest attachment/equipment use changes the live menu before the periodic mob checkpoint.
     * Queue those exact pre-menu baselines before the first menu settlement enters the same FIFO.
     */
    void queueMobMenuBaseline(PlayerTickState player) {
        queuePlayerInventoryBaseline(player);
        flushPersistentMobs();
    }

    private boolean populationPublicationFlushInFlight;

    /** A requested first snapshot waits for the existing aggregate writer, never for a gameplay tick. */
    private boolean preparePopulationForSnapshot(int chunkX, int chunkZ) {
        Set<Long> requested = Set.of(chunkKey(chunkX, chunkZ));
        drainPersistenceCompletions();
        mobSystem.preparePopulationForPublication(requested);
        if (!mobSystem.populationPublicationPending(requested)) return true;
        if (populationPublicationFlushInFlight) return false;
        MobSystem.PopulationPersistenceSnapshot snapshot = mobSystem.populationPersistenceSnapshot();
        populationPublicationFlushInFlight = true;
        Runnable persist = () -> {
            try {
                if (mobPersistence == null) mobSystem.acknowledgePopulationPersistence(snapshot);
                else persistMobsAndPopulation(snapshot);
            } finally {
                enqueuePersistenceCompletion(() -> populationPublicationFlushInFlight = false);
            }
        };
        PersistenceExecutor writer = ctx.persistenceExecutor();
        if (writer == null) {
            persist.run();
            drainPersistenceCompletions();
        } else if (!writer.trySubmit(persist)) {
            populationPublicationFlushInFlight = false;
        }
        return !mobSystem.populationPublicationPending(requested);
    }

    /** Before the first owner tick, join may wait on the same serial writer's completion barrier. */
    private void settleWelcomePopulation(Set<Long> requested) {
        mobSystem.preparePopulationForPublication(requested);
        if (!mobSystem.populationPublicationPending(requested)) return;
        MobSystem.PopulationPersistenceSnapshot snapshot = mobSystem.populationPersistenceSnapshot();
        Runnable persist = () -> {
            if (mobPersistence == null) mobSystem.acknowledgePopulationPersistence(snapshot);
            else persistMobsAndPopulation(snapshot);
        };
        PersistenceExecutor writer = ctx.persistenceExecutor();
        if (writer == null) persist.run();
        else {
            try {
                writer.submitFuture(persist).get();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("initial population persistence interrupted", interrupted);
            } catch (java.util.concurrent.ExecutionException failed) {
                throw new IllegalStateException("initial population persistence failed", failed.getCause());
            }
        }
        drainPersistenceCompletions();
        if (mobSystem.populationPublicationPending(requested)) {
            throw new IllegalStateException("initial population publication is still pending");
        }
    }

    /** 틱 스레드에서는 복사만 하고 실제 JPA 쓰기는 기존 직렬 persistence writer가 수행한다. */
    void flushPersistentMobs() {
        if (mobPersistence == null
                || terminalPhase != TerminalPhase.RUNNING && terminalPhase != TerminalPhase.DISPOSED) return;
        boolean trialEdges = trialPersistence != null
                && mobSystem.trialSpawners().hasPendingPersistenceEdges();
        if (trialEdges && trialPersistenceInFlight) return;
        // 새 무리가 runtime.tick에서 실체화된 뒤 같은 owner 시점의 몹/청크 표식을 함께 복사한다.
        // writer가 각각을 나중에 읽으면 다음 틱 상태와 섞여 원자 트랜잭션이어도 불일치가 생긴다.
        MobSystem.PopulationPersistenceSnapshot snapshot =
                mobSystem.populationPersistenceSnapshot();
        if (trialEdges) trialPersistenceInFlight = true;
        PersistenceExecutor writer = ctx.persistenceExecutor();
        if (writer == null) {
            try {
                persistMobsAndPopulation(snapshot);
                drainPersistenceCompletions();
            } catch (RuntimeException | Error failure) {
                if (trialEdges) trialPersistenceInFlight = false;
                throw failure;
            }
            return;
        }
        if (!writer.trySubmit(() -> {
            try {
                persistMobsAndPopulation(snapshot);
            } catch (RuntimeException | Error failure) {
                if (trialEdges) enqueuePersistenceCompletion(
                        () -> trialPersistenceInFlight = false,
                        () -> trialPersistenceInFlight = false);
                throw failure;
            }
        })) {
            if (trialEdges) trialPersistenceInFlight = false;
            log.warn("월드 {} 몹 스냅샷 저장 제출이 거부되어 다음 flush에서 재시도합니다", worldId);
        }
    }

    private void persistMobsAndPopulation(MobSystem.PopulationPersistenceSnapshot snapshot) {
        // 주민 사회를 먼저 커밋한다. 출산 몹을 먼저 쓴 뒤 여기서 프로세스가 죽으면 부모의
        // 음식·구애 상태가 과거로 남아 같은 출산을 다시 만들 수 있다. 반대 순서의 고아 사회
        // 행은 활성화 때 살아 있는 주민 ID 필터가 제거하므로 중복 없는 방향이다.
        if (villagerSocietyPersistence != null && snapshot.villagerSociety() != null) {
            villagerSocietyPersistence.flush(worldId,
                    snapshot.villagerSociety().states(),
                    snapshot.villagerSociety().bedClaims());
        }
        Runnable persistMobAggregate = () -> mobPersistence.flushWorld(
                worldId, snapshot.mobs(), snapshot.chunks(), snapshot.claims().values(),
                snapshot.raids(), snapshot.raidReceipts());
        if (trialPersistence == null) {
            persistMobAggregate.run();
        } else {
            trialPersistence.saveWorldWithMutation(worldId, snapshot.trials(),
                    snapshot.trialStateChanges(), persistMobAggregate::run);
        }
        // 직업 해제와 NONE 직업, 첫 거래 잠금과 XP를 따로 커밋하면 중단 뒤 서로 어긋난다.
        // 기존 두 저장 lane을 같은 서비스 트랜잭션으로 쓰고 성공한 뒤에만 승인한다.
        mobPersistence.flushVillagerJobsAndTrades(worldId,
                snapshot.villagerJobUpserts().values(), snapshot.villagerJobRemovals().keySet(),
                snapshot.villagerTradeUpserts().values(),
                snapshot.villagerTradeRemovals().keySet());
        mobSystem.acknowledgePopulationPersistence(snapshot);
        if (!snapshot.trialStateChanges().isEmpty()) {
            enqueuePersistenceCompletion(() -> {
                if (terminalPhase != TerminalPhase.RUNNING) {
                    trialPersistenceInFlight = false;
                    return;
                }
                var trials = mobSystem.trialSpawners();
                trials.acknowledgeStateChanges(snapshot.trialStateChanges());
                for (var change : snapshot.trialStateChanges()) {
                    applyTrialFixtureState(change.x(), change.y(), change.z(),
                            change.blockType(), change.state());
                }
                trialPersistenceInFlight = false;
            }, () -> trialPersistenceInFlight = false);
        } else if (!snapshot.trials().isEmpty()) {
            enqueuePersistenceCompletion(() -> trialPersistenceInFlight = false,
                    () -> trialPersistenceInFlight = false);
        }
        // 지급은 receipt 가 durable 해진 뒤에만 시도한다. 승인 직후 같은 persistence 스레드에서
        // 도는 이유는, 지급 시도가 DB 트랜잭션(조건부 PENDING→GRANTED)이라 틱 스레드에 올릴 수
        // 없기 때문이다.
        claimPendingRewardDeliveries();
        claimRaidVictoryPrizes();
        claimTrialVaultPrizes();
    }

    /**
     * [TRIAL] 금고 개봉 lane(persistence 스레드 전용).
     *
     * <p>[TRIAL-GAP] 열쇠 소비·영수증 전이·배출 outbox(굴린 전리품마다 한 행)를 한 DB 트랜잭션으로
     * 커밋하고, 커밋 뒤 틱 스레드에서 금고를 UNLOCKING 으로 넘긴다(바닐라 {@code unlock}). 이미 연
     * 금고는 열쇠를 전혀 소비하지 않고 거부한다.</p>
     */
    private void claimTrialVaultPrizes() {
        if (mobPersistence == null || terminalPhase != TerminalPhase.RUNNING) return;
        PendingVaultUnlock pending;
        while ((pending = pendingVaultUnlocks.poll()) != null) {
            if (terminalPhase != TerminalPhase.RUNNING) return;
            String nickname = pending.nickname();
            int x = pending.x();
            int y = pending.y();
            int z = pending.z();
            long vaultId = TrialVaultContract.vaultId(seed, x, y, z);
            RaidRewardReceipt receipt =
                    TrialVaultContract.pending(vaultId, nickname, clock.tickCount());
            PlayerTickState player = players.get(nickname);
            if (player == null
                    || !player.inventory().stack(pending.hand()).equals(pending.expectedKey())) {
                enqueuePersistenceCompletion(() -> abortVaultUnlock(x, y, z));
                continue;
            }
            RewardPlayerSnapshot playerSnapshot = rewardPlayerSnapshot(player, pending.hand());
            if (playerSnapshot == null) {
                PendingVaultUnlock retry = pending;
                enqueuePersistenceCompletion(() -> pendingVaultUnlocks.add(retry));
                continue;
            }
            RewardSettlement settlement;
            try {
                settlement = mobPersistence.settleTrialVaultUnlock(
                        worldId, receipt, playerSnapshot, pending.ominous());
            } catch (RuntimeException failure) {
                player.inventory().releaseSettlementLease(playerSnapshot.inventorySnapshot());
                enqueuePersistenceCompletion(() -> abortVaultUnlock(x, y, z));
                continue;
            }
            if (settlement.inventoryCommitOutcome()
                    == RewardSettlement.InventoryCommitOutcome.STALE) {
                player.inventory().releaseSettlementLease(playerSnapshot.inventorySnapshot());
                PendingVaultUnlock retry = pending;
                enqueuePersistenceCompletion(() -> {
                    if (players.get(nickname) == player
                            && player.inventory().stack(retry.hand())
                                    .equals(retry.expectedKey())) {
                        pendingVaultUnlocks.add(retry);
                    } else {
                        abortVaultUnlock(x, y, z);
                    }
                });
                continue;
            }
            if (settlement.outcome() == RaidRewardReceipt.ClaimOutcome.GRANTED) {
                List<com.gameexpert.engine.trial.TrialSpawnerRuntime.VaultItem> items =
                        vaultUnlockItems(nickname, pending.ominous(), receipt.rewardSeed());
                enqueuePersistenceCompletion(() -> {
                    applyCommittedReward(player, playerSnapshot.inventorySnapshot(), settlement);
                    mobSystem.trialSpawners().completeVaultUnlock(null, x, y, z, nickname, items);
                }, () -> {
                    player.inventory().releaseSettlementLease(playerSnapshot.inventorySnapshot());
                    abortVaultUnlock(x, y, z);
                });
            } else if (settlement.outcome()
                    == RaidRewardReceipt.ClaimOutcome.ALREADY_GRANTED) {
                player.inventory().releaseSettlementLease(playerSnapshot.inventorySnapshot());
                enqueuePersistenceCompletion(() -> {
                    abortVaultUnlock(x, y, z);
                    mobSystem.trialSpawners().markVaultRewarded(x, y, z, nickname);
                    notifyVaultAlreadyClaimed(player);
                });
            } else {
                player.inventory().releaseSettlementLease(playerSnapshot.inventorySnapshot());
                enqueuePersistenceCompletion(() -> abortVaultUnlock(x, y, z));
            }
        }
    }

    /** 영수증 시드로 굴린 금고 전리품(바닐라 VaultConfig.lootTable)과 각 행의 outbox 토큰. */
    private static List<com.gameexpert.engine.trial.TrialSpawnerRuntime.VaultItem> vaultUnlockItems(
            String nickname, boolean ominous, long rewardSeed) {
        List<com.gameexpert.engine.trial.TrialLootTables.Stack> stacks =
                com.gameexpert.engine.trial.TrialLootTables.vaultReward(ominous, rewardSeed);
        List<com.gameexpert.engine.trial.TrialSpawnerRuntime.VaultItem> items =
                new ArrayList<>(stacks.size());
        for (int index = 0; index < stacks.size(); index++) {
            items.add(new com.gameexpert.engine.trial.TrialSpawnerRuntime.VaultItem(
                    TrialVaultContract.ejectToken(nickname, index), stacks.get(index)));
        }
        return items;
    }

    private void abortVaultUnlock(int x, int y, int z) {
        mobSystem.trialSpawners().abortVaultUnlock(x, y, z);
    }

    private void notifyVaultAlreadyClaimed(PlayerTickState expectedPlayer) {
        String nickname = expectedPlayer.nickname();
        if (players.get(nickname) != expectedPlayer) return;
        WebSocketSession session = session(nickname);
        if (session != null) {
            ctx.broadcaster().enqueueSendToFromTick(worldId, session,
                    new com.gameexpert.ws.dto.WsMessages.Error("VAULT_ALREADY_CLAIMED"));
        }
    }

    /** 금고 우클릭 하나를 영속 lane 으로 넘긴다(틱 스레드에서만 호출). */
    void requestVaultUnlock(String nickname, int x, int y, int z,
            PlayerInventory.HandRef hand, PlayerInventory.StackSnapshot expectedKey) {
        if (terminalPhase != TerminalPhase.RUNNING || hand == null || expectedKey == null
                || !TrialVaultContract.isVaultKey(expectedKey.itemType())) {
            abortVaultUnlock(x, y, z);
            return;
        }
        pendingVaultUnlocks.add(new PendingVaultUnlock(nickname, x, y, z, hand, expectedKey,
                mobSystem.trialSpawners().vaultOminous(x, y, z)));
    }

    /** 퇴장 플레이어의 아직 정산하지 않은(따라서 열쇠도 소비하지 않은) 시도를 버린다. */
    private void refundQueuedVaultUnlocks(String nickname) {
        if (nickname == null || pendingVaultUnlocks.isEmpty()) return;
        for (PendingVaultUnlock pending : List.copyOf(pendingVaultUnlocks)) {
            if (!nickname.equals(pending.nickname()) || !pendingVaultUnlocks.remove(pending)) {
                continue;
            }
            abortVaultUnlock(pending.x(), pending.y(), pending.z());
        }
    }

    /** 영속 트랜잭션이 열쇠 소비 여부를 결정할 개봉 시도 하나. */
    private record PendingVaultUnlock(String nickname, int x, int y, int z,
            PlayerInventory.HandRef hand, PlayerInventory.StackSnapshot expectedKey,
            boolean ominous) {}

    /**
     * 승리 보상 지급 lane(persistence 스레드 전용).
     *
     * <p>receipt 전이, durable delivery 생성/감액, 플레이어 인벤토리 저장은 한 DB 트랜잭션이다.
     * 들어가지 못한 수량은 delivery에 남고 다음 flush 또는 재기동 뒤 다시 시도한다.</p>
     *
     * <p>수령자는 receipt 에 영속된 hero 닉네임이다. hero 가 접속해 있지 않으면 아무것도
     * 소비하지 않고 다음 flush 로 미룬다. 그래서 재접속 뒤 첫 flush 에서 정확히 한 번 지급된다.</p>
     */
    private void claimRaidVictoryPrizes() {
        if (mobPersistence == null || terminalPhase != TerminalPhase.RUNNING) return;
        for (Map.Entry<Long, String> owed : mobSystem.unclaimedRaidPrizes().entrySet()) {
            if (terminalPhase != TerminalPhase.RUNNING) return;
            long raidId = owed.getKey();
            PlayerTickState recipient = players.get(owed.getValue());
            if (recipient == null) continue;
            RewardPlayerSnapshot playerSnapshot = rewardPlayerSnapshot(recipient);
            if (playerSnapshot == null) continue;
            RewardSettlement settlement;
            try {
                settlement = mobPersistence.settleRaidVictoryPrize(
                        worldId, raidId, playerSnapshot);
            } catch (RuntimeException failure) {
                recipient.inventory().releaseSettlementLease(playerSnapshot.inventorySnapshot());
                continue;
            }
            if (settlement.inventoryCommitOutcome()
                    == RewardSettlement.InventoryCommitOutcome.COMMITTED) {
                enqueuePersistenceCompletion(() -> applyCommittedReward(
                        recipient, playerSnapshot.inventorySnapshot(), settlement),
                        () -> recipient.inventory().releaseSettlementLease(
                                playerSnapshot.inventorySnapshot()));
            } else {
                recipient.inventory().releaseSettlementLease(playerSnapshot.inventorySnapshot());
            }
            if (settlement.outcome() == RaidRewardReceipt.ClaimOutcome.GRANTED
                    || settlement.outcome() == RaidRewardReceipt.ClaimOutcome.ALREADY_GRANTED) {
                mobSystem.forgetUnclaimedRaidPrize(raidId);
            }
        }
    }

    private void claimPendingRewardDeliveries() {
        if (mobPersistence == null || terminalPhase != TerminalPhase.RUNNING) return;
        for (RewardDeliverySnapshot delivery : mobPersistence.loadPendingRewardDeliveries(worldId)) {
            if (terminalPhase != TerminalPhase.RUNNING) return;
            PlayerTickState recipient = players.get(delivery.recipientNickname());
            if (recipient == null) continue;
            RewardPlayerSnapshot playerSnapshot = rewardPlayerSnapshot(recipient);
            if (playerSnapshot == null) continue;
            RewardSettlement settlement;
            try {
                settlement = mobPersistence.settlePendingRewardDelivery(
                        worldId, delivery, playerSnapshot);
            } catch (RuntimeException failure) {
                recipient.inventory().releaseSettlementLease(playerSnapshot.inventorySnapshot());
                continue;
            }
            if (settlement.inventoryCommitOutcome()
                    == RewardSettlement.InventoryCommitOutcome.COMMITTED) {
                enqueuePersistenceCompletion(() -> applyCommittedReward(
                        recipient, playerSnapshot.inventorySnapshot(), settlement),
                        () -> recipient.inventory().releaseSettlementLease(
                                playerSnapshot.inventorySnapshot()));
            } else {
                recipient.inventory().releaseSettlementLease(playerSnapshot.inventorySnapshot());
            }
        }
    }

    private RewardPlayerSnapshot rewardPlayerSnapshot(PlayerTickState player) {
        return rewardPlayerSnapshot(player, null);
    }

    private RewardPlayerSnapshot rewardPlayerSnapshot(
            PlayerTickState player, PlayerInventory.HandRef trialKeyHand) {
        PlayerInventory.CompletePersistenceSnapshot inventory =
                player.inventory().acquireSettlementLease();
        return inventory == null ? null : new RewardPlayerSnapshot(
                player.playerId(), player.nickname(), inventory, trialKeyHand);
    }

    /** DB 커밋으로 확정된 수량만 런타임에 반영한다. 바닥 드롭은 durable delivery를 대신하지 않는다. */
    private void applyCommittedReward(PlayerTickState expectedPlayer,
            PlayerInventory.CompletePersistenceSnapshot sourceInventory,
            RewardSettlement settlement) {
        String nickname = expectedPlayer.nickname();
        if (terminalPhase != TerminalPhase.RUNNING
                || players.get(nickname) != expectedPlayer) {
            expectedPlayer.inventory().releaseSettlementLease(sourceInventory);
            return;
        }
        PlayerTickState player = expectedPlayer;
        if (!player.inventory().installCommittedSettlement(
                sourceInventory, settlement.committedInventory())) {
            player.inventory().releaseSettlementLease(sourceInventory);
            log.error("Committed reward snapshot could not be installed for {}", nickname);
            return;
        }
        WebSocketSession session = session(nickname);
        if (terminalPhase == TerminalPhase.RUNNING && session != null) {
            ctx.broadcaster().enqueueSendToFromTick(worldId, session,
                    WorldTickLoop.inventoryMessage(player));
        }
    }

    /** 절대 일수와 떠돌이 상인 창을 하나의 월드-행 트랜잭션으로 저장합니다. */
    void persistWorldDayIfChanged() {
        persistWorldClockIfChanged();
    }

    private long persistedRedstoneRevision;

    void persistWorldClockIfChanged() {
        if (terminalPhase != TerminalPhase.RUNNING) return;
        PersistenceExecutor persistence = ctx.persistenceExecutor();
        long dayCount = clock.dayCount();
        long worldTime = clock.worldTime();
        long gameTimeMcTicks = clock.gameTimeMcTicks();
        long[] traderWindow = mobSystem.traderWindowState();
        long nextAttemptTick = traderWindow[0];
        int chancePercent = (int) traderWindow[1];
        long redstoneVersion = tickLoop.redstoneRevision();
        boolean scheduleChanged = redstoneVersion != persistedRedstoneRevision || dayCount != persistedDayCount
                || nextAttemptTick != persistedTraderNextAttemptTick
                || chancePercent != persistedTraderChancePercent;
        boolean periodicClockCheckpoint = gameTimeMcTicks != persistedGameTimeMcTicks
                && Math.floorMod(gameTimeMcTicks, CLOCK_CHECKPOINT_INTERVAL_MC_TICKS) == 0;
        if (worldTimePersistence == null || persistence == null || disposed
                || clockStatePersistencePending
                || !scheduleChanged && !periodicClockCheckpoint) {
            return;
        }
        clockStatePersistencePending = true;
        WorldTimePersistenceService.ClockState snapshot =
                new WorldTimePersistenceService.ClockState(dayCount, worldTime, gameTimeMcTicks,
                        new WorldTimePersistenceService.TraderWindow(
                                nextAttemptTick, chancePercent));
        String redstoneSnapshot = tickLoop.redstoneSnapshot();
        boolean accepted = persistence.trySubmit(() -> {
            try {
                worldTimePersistence.saveClockState(worldId, snapshot, redstoneSnapshot);
                Runnable acknowledge = () -> {
                    persistedRedstoneRevision = redstoneVersion;
                    persistedDayCount = snapshot.dayCount();
                    persistedWorldTime = snapshot.worldTime();
                    persistedGameTimeMcTicks = snapshot.gameTimeMcTicks();
                    persistedTraderNextAttemptTick = snapshot.traderWindow().nextAttemptTick();
                    persistedTraderChancePercent = snapshot.traderWindow().chancePercent();
                    clockStatePersistencePending = false;
                };
                enqueuePersistenceCompletion(() -> {
                    acknowledge.run();
                    if (terminalPhase == TerminalPhase.RUNNING) persistWorldClockIfChanged();
                }, acknowledge);
            } catch (RuntimeException | Error exception) {
                Runnable reject = () -> {
                    clockStatePersistencePending = false;
                    ScheduledExecutorService current = executor;
                    if (current != null && !current.isShutdown()
                            && terminalPhase == TerminalPhase.RUNNING) {
                        current.schedule(this::persistWorldClockIfChanged, 1, TimeUnit.SECONDS);
                    }
                };
                enqueuePersistenceCompletion(reject, () -> clockStatePersistencePending = false);
                throw exception;
            }
        });
        if (!accepted) {
            clockStatePersistencePending = false;
            ScheduledExecutorService current = executor;
            if (current != null && !current.isShutdown()
                    && terminalPhase == TerminalPhase.RUNNING) {
                current.schedule(this::persistWorldClockIfChanged, 1, TimeUnit.SECONDS);
            }
        }
    }

    /**
     * 영속 편집은 정상 변경 경로로 복구한다. 자연 유체는 구조물/FTIK 완료 경계에서 노출된 면만
     * 별도로 깨우며, 하늘만 열린 바다·강 표면 전체를 불안정 셀로 예약하지 않는다.
     */
    private void activateGeneratedChunk(int chunkX, int chunkZ, List<WorldBlockDiff> persistedDiffs) {
        if (!ownerTurnMayContinue()) return;
        retainActivationPlanningSource(chunkX, chunkZ);
        if (!ownerTurnMayContinue()) return;
        fluidSim.onChunkActivated(chunkX, chunkZ);
        if (!ownerTurnMayContinue()) return;
        mobSystem.onChunkActivated(chunkX, chunkZ);
        activatePersistedFluidChanges(persistedFluidWakeups(persistedDiffs));
        if (!ownerTurnMayContinue()) return;
        decorateGeneratedChunk(chunkX, chunkZ, persistedDiffs);
        if (!ownerTurnMayContinue()) return;
        tickLoop.onChunkActivatedForSculk(chunkX, chunkZ);
        for (WorldBlockDiff diff : persistedDiffs) {
            if (!ownerTurnMayContinue()) return;
            int loadedBlock = Short.toUnsignedInt(diff.getBlockType());
            if (loadedBlock == Blocks.FIRE) {
                tickLoop.trackLoadedFire(diff.getX(), diff.getY(), diff.getZ());
            } else if (loadedBlock == Blocks.DRIED_GHAST) {
                // [DEEP-DARK] 말린 가스트는 자연 지형에 없고 영속 diff 로만 돌아온다. 이 한 줄이
                // 없으면 재접속한 월드의 수화가 영원히 멈춘다(다음 블록 편집이 우연히 옆 칸을
                // 건드릴 때까지).
                tickLoop.trackLoadedDriedGhast(diff.getX(), diff.getY(), diff.getZ());
            } else if (Blocks.isCreakingHeart(loadedBlock)) {
                // [CREAKING] 크리킹 하트도 자연 지형에 없고(이 저장소에서는 제작 전용) 영속
                // diff 로만 돌아온다. 이 한 줄이 없으면 재접속한 월드의 하트가 영원히 깨어나지
                // 않는다 — 말린 가스트와 같은 이유·같은 자리다. 활성 쌍둥이로 저장된 칸도 같이
                // 잡아야 하므로 상수 비교가 아니라 술어로 본다.
                tickLoop.trackLoadedCreakingHeart(diff.getX(), diff.getY(), diff.getZ());
            } else if (com.gameexpert.engine.redstone.RedstoneState.isRedstoneComponent(loadedBlock)) {
                tickLoop.trackLoadedRedstone(diff.getX(), diff.getY(), diff.getZ(),
                        loadedBlock, Short.toUnsignedInt(diff.getBlockState()));
            }
        }
        if (!legacyBookLecterns.isEmpty()) restoreLegacyLecternBooks(chunkKey(chunkX, chunkZ));
        // [BLOCK-SHAPES] 이 청크에 저장된 양조대의 has_bottle 비트를 병 칸에 맞춘다(옛 저장분 포함).
        tickLoop.syncStoredBrewingBottles(new int[] {chunkX, chunkZ});
        if (authorityEvidenceRuntimeCallbacks != null) {
            authorityEvidenceRuntimeCallbacks.chunkActivated(worldId, chunkX, chunkZ);
        }
    }

    /** 최초 읽기 콜백은 요구만 기록하며 계획·스캔·장식 작업을 호출자 스레드에서 하지 않습니다. */
    private void enqueueChunkActivation(TerrainAccessor.PreparedActivationClaim activation) {
        if (!ownerTurnMayContinue()) return;
        int chunkX = activation.chunkX();
        int chunkZ = activation.chunkZ();
        List<WorldBlockDiff> persistedDiffs = activation.persistedDiffs();
        long key = chunkKey(chunkX, chunkZ);
        // A late worker handoff from a neighborhood a player has already left must not resurrect simulation.
        if (started && !activeSimulationChunks.contains(key)) return;
        // 피뢰침은 지형 생성이 놓지 않는 제작 전용 블록이라 그 청크의 피뢰침은 전부 persisted diff
        // 안에 있다. 컬럼 전수 스캔 없이 원장을 언로드 이전 상태로 되돌리는 자리다.
        recoverLightningRods(chunkX, chunkZ, persistedDiffs);
        if (!ownerTurnMayContinue()) return;
        retainActivationPlanningSource(chunkX, chunkZ);
        preparedActivationChunks.add(key);
        if (!ownerTurnMayContinue()) return;
        long generation = activeChunkGeneration(key);
        if (finalCarrierTickScheduler != null) {
            activateFinalCarrierTickChunkOrDefer(finalCarrierTickScheduler, key, chunkX, chunkZ,
                    generation);
        }
        if (!ownerTurnMayContinue()) return;
        activation.finalLiveCarrierClaim().ifPresent(claim -> {
            PendingFinalCarrierClaim pending = new PendingFinalCarrierClaim(generation, claim);
            pendingFinalCarrierClaims.put(key, pending);
            queueFinalCarrierTickAdmission(key, pending);
        });
        ChunkActivationDemand demand = new ChunkActivationDemand(
                generation, chunkX, chunkZ, List.copyOf(persistedDiffs));
        // REST/입장 준비 같은 비틱 생명주기는 호출을 반환하기 전에 요구를 확정한다. 틱 읽기는 큐잉만 한다.
        if (!started && !TickSafetyTelemetry.isTickThread()) {
            activateGeneratedChunk(demand.chunkX, demand.chunkZ, demand.persistedDiffs);
            return;
        }
        if (!activationTreeNeighborhoodReady(chunkX, chunkZ)) {
            if (!ownerTurnMayContinue()) return;
            publishChunkActivationDemand(key, demand);
            return;
        }
        if (!ownerTurnMayContinue()) return;
        if (!submitChunkActivationPlan(demand)) publishChunkActivationDemand(key, demand);
    }

    private void drainChunkActivationDemandsWithinBudget() {
        if (!ownerTurnMayContinue() || chunkActivationDemands.isEmpty()) return;
        long deadline = System.nanoTime() + ACTIVATION_DRAIN_BUDGET_NANOS;
        List<Map.Entry<Long, ChunkActivationDemand>> ordered =
                new ArrayList<>(chunkActivationDemands.entrySet());
        ordered.sort((left, right) -> compareDemandedChunks(left.getKey(), right.getKey()));
        for (Map.Entry<Long, ChunkActivationDemand> entry : ordered) {
            if (!ownerTurnMayContinue()) return;
            long key = entry.getKey();
            ChunkActivationDemand demand = entry.getValue();
            if (demand.generation != activeChunkGeneration(key)
                    || !activeSimulationChunks.contains(key)) {
                chunkActivationDemands.remove(key, demand);
                continue;
            }
            // 예산 확인을 루프 앞으로 옮겨 "미준비 항목 건너뛰기"까지 예산에 걸리게 해 봤으나,
            // 목록이 매 턴 거리순으로 다시 정렬되어 앞부분이 계속 미준비면 뒤쪽의 준비된 청크에
            // 영영 닿지 못한다. 실측에서 소비자 수가 27→3 으로 무너지고 첫 진입이 120초 넘게
            // 0% 에 머물렀다. 예산은 실제 제출을 한 뒤에만 본다.
            if (!activationTreeNeighborhoodReady(demand.chunkX, demand.chunkZ)) continue;
            if (!submitChunkActivationPlan(demand)) {
                break;
            }
            chunkActivationDemands.remove(key, demand);
            if (System.nanoTime() >= deadline) break;
        }
    }

    /**
     * Full activation planning uses the same bounded preparer as terrain readiness. It only builds immutable
     * coordinates from a detached deterministic view: no FluidSimulator, overlay, player state, or broadcaster
     * is touched here.
     */
    private boolean submitChunkActivationPlan(ChunkActivationDemand demand) {
        if (!ownerTurnMayContinue()) return true;
        long key = chunkKey(demand.chunkX, demand.chunkZ);
        stampActivationStage(key, ACT_DEMAND);
        if (started && (demand.generation != activeChunkGeneration(key)
                || !activeSimulationChunks.contains(key))) return true;
        Long inFlightGeneration = pendingChunkActivationPlans.putIfAbsent(key, demand.generation);
        if (inFlightGeneration != null) {
            if (inFlightGeneration.longValue() == demand.generation) return true;
            if (!pendingChunkActivationPlans.replace(
                    key, inFlightGeneration, demand.generation)) return false;
        }
        try {
            executeOwned(ACTIVATION_PLANNERS, () -> prepareChunkActivationPlan(demand, key),
                    null, true, demand.chunkX, demand.chunkZ, demand.generation);
            return true;
        } catch (RejectedExecutionException saturated) {
            pendingChunkActivationPlans.remove(key, demand.generation);
            return false;
        }
    }

    private void prepareChunkActivationPlan(ChunkActivationDemand demand, long key) {
        if (chunkProductSource instanceof com.gameexpert.world.dimension.DimensionChunkProductSource custom) {
            try {
                if (!ownerTurnMayContinue()) return;
                publishPreparedChunkActivation(key, new PreparedChunkActivation(
                        demand.generation, demand.chunkX, demand.chunkZ,
                        persistedFluidWakeups(demand.persistedDiffs),
                        custom.initialStates(seed, demand.chunkX, demand.chunkZ),
                        List.of(), List.of(), false));
            } finally {
                pendingChunkActivationPlans.remove(key, demand.generation);
            }
            return;
        }
        stampActivationStage(key, ACT_PLAN_START);
        CompletableFuture<?> retryAfterSitePlan = null;
        try {
            if (!ownerTurnMayContinue() || disposed || started && (demand.generation != activeChunkGeneration(key)
                    || !activeSimulationChunks.contains(key))) return;
            SurfaceDecorator.BlockView planningWorld = activationPlanningView();
            SurfaceDecorator.BlockView structureWorld = activationStructurePlanningView();
            Set<BlockPos> persistentEdits = persistentEdits(demand.persistedDiffs);
            // Shared structure sites may still be owned by another planner. Resolve that cheap dependency
            // before natural decoration so a normal SitePlanPending retry does not discard a full chunk scan.
            List<AcceptedStructureSite> acceptedSites = new ArrayList<>();
            List<ExplorationDecorator.Placement> criticalPlacements =
                    explorationDecorator.activationBasePlacements(
                            demand.chunkX, demand.chunkZ, structureWorld, acceptedSites::add);
            boolean hasDeferredCandidate =
                    explorationDecorator.hasDeferredCandidate(demand.chunkX, demand.chunkZ);
            SurfaceDecorator.NaturalWaterPlan ponds = surfaceDecorator.customPondFeatures(
                    demand.chunkX, demand.chunkZ, planningWorld, persistentEdits::contains);
            SurfaceDecorator.BlockView naturalWorld = ponds.overlay(planningWorld);
            java.util.function.Predicate<BlockPos> naturalClaims =
                    pos -> persistentEdits.contains(pos) || ponds.claims(pos);
            List<SurfaceDecorator.Decoration> custom =
                    surfaceDecorator.customSurfaceDecorations(demand.chunkX, demand.chunkZ,
                            naturalWorld, naturalClaims);
            TerrainAccessor.ReplayableChunkPatch patch = naturalPatch(
                    demand.chunkX, demand.chunkZ, ponds.decorations(), custom);
            stampActivationStage(key, ACT_PLAN_END);
            PreparedChunkActivation prepared = new PreparedChunkActivation(
                    demand.generation, demand.chunkX, demand.chunkZ,
                    persistedFluidWakeups(demand.persistedDiffs),
                    patch,
                    criticalPlacements, acceptedSites, hasDeferredCandidate);
            if (ownerTurnMayContinue() && (!started || demand.generation == activeChunkGeneration(key)
                    && activeSimulationChunks.contains(key))) {
                activationPlanningFailures.remove(key);
                publishPreparedChunkActivation(key, prepared);
            }
        } catch (ExplorationDecorator.SitePlanPendingException pending) {
            // Another activation worker owns the same deterministic structure site. Waiting on that worker
            // is expected coordination, not a planning defect; retry once its shared result is available.
            retryAfterSitePlan = pending.completion();
        } catch (RuntimeException planningError) {
            if (ownerTurnMayContinue() && (!started || demand.generation == activeChunkGeneration(key)
                    && activeSimulationChunks.contains(key))) {
                int failures = activationPlanningFailures.merge(key, 1, Integer::sum);
                if (failures < MAX_ACTIVATION_PLANNING_FAILURES) {
                    // Preserve the demand for a bounded worker retry; the tick must never plan synchronously.
                    publishChunkActivationDemand(key, demand);
                    log.warn("청크 활성화 계획 실패, 워커에서 재시도합니다: world={} chunk={},{} attempt={}",
                            worldId, demand.chunkX, demand.chunkZ, failures, planningError);
                } else {
                    activationPlanningFailures.remove(key);
                    // Keep authoritative terrain and persisted fluid edits playable. Optional natural/structure
                    // decoration is isolated to this chunk after repeated deterministic planner failure.
                    publishPreparedChunkActivation(key, new PreparedChunkActivation(
                            demand.generation, demand.chunkX, demand.chunkZ,
                            persistedFluidWakeups(demand.persistedDiffs),
                            TerrainAccessor.ReplayableChunkPatch.builder(
                                    demand.chunkX, demand.chunkZ).build(),
                            List.of(), List.of(), false));
                    log.error("청크 활성화 계획을 세 번 실패해 기본 지형으로 격리합니다: world={} chunk={},{}",
                            worldId, demand.chunkX, demand.chunkZ, planningError);
                }
            }
        } finally {
            pendingChunkActivationPlans.remove(key, demand.generation);
            CompletableFuture<?> dependency = retryAfterSitePlan;
            if (dependency != null) {
                // Register only after releasing this key. If the future is already complete, whenComplete runs
                // inline and still cannot race the old task's finally into deleting the replacement task.
                dependency.whenComplete((ignored, failure) -> {
                    if (ownerTurnMayContinue() && (!started || demand.generation == activeChunkGeneration(key)
                            && activeSimulationChunks.contains(key))) {
                        publishChunkActivationDemand(key, demand);
                    }
                });
            }
        }
    }

    private static Set<BlockPos> persistentEdits(List<WorldBlockDiff> persistedDiffs) {
        Set<BlockPos> edits = new HashSet<>();
        for (WorldBlockDiff diff : persistedDiffs) {
            edits.add(new BlockPos(diff.getX(), diff.getY(), diff.getZ()));
        }
        return Set.copyOf(edits);
    }

    private void publishChunkActivationDemand(long key, ChunkActivationDemand demand) {
        stampActivationStage(key, ACT_DEMAND);
        if (!ownerTurnMayContinue()) return;
        chunkActivationDemands.compute(key, (ignored, current) ->
                current == null || current.generation < demand.generation ? demand : current);
    }

    private void publishPreparedChunkActivation(long key, PreparedChunkActivation prepared) {
        if (!ownerTurnMayContinue()) return;
        preparedChunkActivations.compute(key, (ignored, current) ->
                current == null || current.generation < prepared.generation ? prepared : current);
    }

    /**
     * Folds the surviving project-custom natural decoration into one target-chunk-local primitive
     * patch. Every vanilla lane this patch used to carry is produced by the 26.3 FEATURES product.
     */
    private static TerrainAccessor.ReplayableChunkPatch naturalPatch(int chunkX, int chunkZ,
            List<SurfaceDecorator.Decoration> ponds,
            List<SurfaceDecorator.Decoration> custom) {
        try (TerrainAccessor.ReplayableChunkPatch.Builder patch =
                TerrainAccessor.ReplayableChunkPatch.builder(chunkX, chunkZ)) {
            appendNaturalDecorations(patch, ponds);
            appendNaturalDecorations(patch, custom);
            return patch.build();
        }
    }

    private static void appendNaturalDecorations(TerrainAccessor.ReplayableChunkPatch.Builder patch,
            List<SurfaceDecorator.Decoration> decorations) {
        for (SurfaceDecorator.Decoration decoration : decorations) {
            BlockPos pos = decoration.pos();
            patch.put(pos.x(), pos.y(), pos.z(), decoration.blockType(), decoration.state());
        }
    }

    /**
     * A persisted diff is the only load-time evidence that fluid topology changed after generation.
     * Wake the changed coordinate through the normal six-neighbor path; sorting removes repository-order
     * nondeterminism without allocating anything proportional to chunk volume.
     */
    private static List<BlockPos> persistedFluidWakeups(List<WorldBlockDiff> persistedDiffs) {
        List<BlockPos> wakeups = new ArrayList<>(persistedDiffs.size());
        for (WorldBlockDiff diff : persistedDiffs) {
            wakeups.add(new BlockPos(diff.getX(), diff.getY(), diff.getZ()));
        }
        wakeups.sort(java.util.Comparator.comparingInt(BlockPos::y)
                .thenComparingInt(BlockPos::z)
                .thenComparingInt(BlockPos::x));
        return List.copyOf(wakeups);
    }

    private void activatePersistedFluidChanges(List<BlockPos> wakeups) {
        for (BlockPos pos : wakeups) {
            if (!ownerTurnMayContinue()) return;
            fluidSim.onBlockChanged(pos.x(), pos.y(), pos.z());
        }
    }

    /**
     * Applies a bounded number of immutable plan cells on the world owner. No full chunk scan, decoration
     * planning, generation, persistence read, or activation demand is performed by this tick-side method.
     */
    private void drainPreparedChunkActivationsWithinBudget() {
        if (!ownerTurnMayContinue()) return;
        int adopted = 0;
        if (!preparedChunkActivations.isEmpty()) {
            List<Map.Entry<Long, PreparedChunkActivation>> ordered =
                    new ArrayList<>(preparedChunkActivations.entrySet());
            ordered.sort((left, right) -> compareDemandedChunks(left.getKey(), right.getKey()));
            for (Map.Entry<Long, PreparedChunkActivation> entry : ordered) {
                if (!ownerTurnMayContinue()) return;
                if (adopted >= ACTIVATION_APPLICATIONS_PER_TICK) break;
                long key = entry.getKey();
                PreparedChunkActivation prepared = entry.getValue();
                if (prepared.generation != activeChunkGeneration(key)
                        || !activeSimulationChunks.contains(key)) {
                    preparedChunkActivations.remove(key, prepared);
                    continue;
                }
                if (preparedChunkActivations.remove(key, prepared)) {
                    pendingChunkActivationApplications.putIfAbsent(key, prepared);
                }
                stampActivationStage(key, ACT_ADOPTED);
                adopted++;
                if (architectureTurn != null) architectureTurn.activationPlansAdopted++;
            }
        }
        if (pendingChunkActivationApplications.isEmpty()) return;
        // One activation slice can enqueue at most one outbound block per cell. Leave that complete
        // headroom before advancing any cursor so a slow socket cannot turn exploration into an
        // unbounded Block object backlog.
        if (pendingLiveBlockUpdates.size()
                > MAX_PENDING_DECORATION_UPDATES - ACTIVATION_CELLS_PER_SLICE) {
            return;
        }
        long deadline = System.nanoTime() + ACTIVATION_DRAIN_BUDGET_NANOS;
        int applications = 0;
        // applyNext can submit deferred lifecycle work, whose callbacks may compact/replace the owner FIFO.  Do
        // not keep a LinkedHashMap iterator live across that call: self-removal otherwise throws CME on the next
        // iterator step.  Conditional removal also leaves a replacement FIFO untouched.
        Map<Long, PreparedChunkActivation> applicationsAtStart = pendingChunkActivationApplications;
        List<Map.Entry<Long, PreparedChunkActivation>> applicationSnapshot =
                new ArrayList<>(ACTIVATION_APPLICATIONS_PER_TICK);
        // 입장 우선 게이트에 멈춘 활성화는 슬라이스를 받아도 게이트만 다시 묻고 끝난다. 그런 항목이
        // 창(64)을 다 채우면 그 뒤의 welcome 대상은 영영 적용되지 않고, welcome 요청은 그 대상이
        // 배달돼야 끝나므로 게이트도 풀리지 않는다(입장 교착). 게이트 대기 항목은 창에서 건너뛰고
        // 나머지는 FIFO 순서 그대로 둔다. 이들은 게이트가 풀린 뒤 제자리에서 이어서 적용된다.
        Set<Long> welcomeTargets = welcomeCanonicalWorkTargets();
        var applicationIterator = applicationsAtStart.entrySet().iterator();
        while (applicationIterator.hasNext()
                && applicationSnapshot.size() < ACTIVATION_APPLICATIONS_PER_TICK) {
            Map.Entry<Long, PreparedChunkActivation> entry = applicationIterator.next();
            PreparedChunkActivation current = entry.getValue();
            if (welcomeTargets != null && current.generation == activeChunkGeneration(entry.getKey())
                    && activeSimulationChunks.contains(entry.getKey())
                    && current.waitsAtWelcomeGate(welcomeTargets)) {
                continue;
            }
            applicationSnapshot.add(entry);
        }
        for (Map.Entry<Long, PreparedChunkActivation> entry : applicationSnapshot) {
            if (!ownerTurnMayContinue()) return;
            if (applications >= ACTIVATION_APPLICATIONS_PER_TICK || System.nanoTime() >= deadline) break;
            long key = entry.getKey();
            PreparedChunkActivation current = entry.getValue();
            if (current.generation != activeChunkGeneration(key)
                    || !activeSimulationChunks.contains(key)
                    || current.applyNext(this)) {
                if (pendingChunkActivationApplications == applicationsAtStart
                        && applicationsAtStart.get(key) == current) {
                    applicationsAtStart.remove(key);
                }
            }
            applications++;
            if (architectureTurn != null) architectureTurn.activationSlicesApplied++;
        }
    }

    /**
     * Installs all deterministic natural cells below dynamic edits in one owner operation. Only non-default block
     * states need entries in the sparse gameplay state store; snapshot bytes live directly in the immutable patch.
     */
    private void installPreparedNaturalPatch(int chunkX, int chunkZ,
            TerrainAccessor.ReplayableChunkPatch patch) {
        if (!ownerTurnMayContinue()) return;
        accessor.installReplayablePatch(chunkX, chunkZ, patch);
        if (!ownerTurnMayContinue()) return;
        invalidateLightChunkCaches(chunkX, chunkZ);
        long chunkKey = chunkKey(chunkX, chunkZ);
        boolean rehydrating = compactedReplayableChunks.remove(chunkKey);
        if (rehydrating) rehydratingReplayableChunks.add(chunkKey);
        if (patch.isEmpty()) return;
        replayableOverlayChunks.add(chunkKey);
        if (!rehydrating) chunkVersions.advance(chunkX, chunkZ);
        if (!ownerTurnMayContinue()) return;
        boolean mayContainProtectedEdits = protectedDecorationEditChunks.contains(chunkKey);
        int baseX = chunkX * Blocks.CHUNK_X;
        int baseZ = chunkZ * Blocks.CHUNK_Z;
        for (int ordinal = 0; ordinal < patch.size(); ordinal++) {
            if (!ownerTurnMayContinue()) return;
            int blockState = patch.blockStateAtOrdinal(ordinal);
            if (blockState == 0) continue;
            int index = patch.blockIndexAt(ordinal);
            int x = baseX + (index & 15);
            int z = baseZ + ((index >>> 4) & 15);
            int y = Blocks.MIN_Y + (index >>> 8);
            if ((mayContainProtectedEdits && protectedDecorationEdits.contains(new BlockPos(x, y, z)))
                    || mobMutationSites.contains(new BlockPos(x, y, z))
                    || structureOverlaySites.contains(structurePositionKey(x, y, z))) {
                continue;
            }
            if (!ownerTurnMayContinue()) return;
            blockStates.set(x, y, z, patch.blockTypeAtOrdinal(ordinal), blockState);
        }
    }

    /**
     * Initial activation output belongs to the authoritative snapshot. Broadcasting those same revisions before
     * presentation is ready makes clients buffer thousands of redundant cells and can expose partial scenery.
     * Only a genuinely later change to an already-presented chunk travels through the live delta queue.
     */
    private void enqueuePresentedDecoration(int x, int y, int z, int blockType) {
        int chunkX = Math.floorDiv(x, Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv(z, Blocks.CHUNK_Z);
        if (!isSnapshotPresentationReady(chunkX, chunkZ)) return;
        enqueueCurrentOverlayBlock(x, y, z, blockType);
    }

    private void markSnapshotBaseReady(int chunkX, int chunkZ) {
        if (!ownerTurnMayContinue()) return;
        snapshotBaseReadyChunks.add(chunkKey(chunkX, chunkZ));
        stampActivationStage(chunkKey(chunkX, chunkZ), ACT_BASE_READY);
    }

    /**
     * 레인 소비는 한 청크에 최대 아홉 번의 DB 왕복이라 한 owner turn에 몰면 틱 예산을 넘긴다.
     * 이 진행자는 없는 레인을 건너뛰고 호출당 남은 레인 하나만 시도한다. 모든 레인을 지난 뒤에야 복구와
     * 구조물 완료 표시를 한다. 실패한 레인은 지금과 똑같이 미승인 상태로 남고 분류도 그대로다.
     *
     * @return 이 청크의 구조물 완료까지 끝냈으면 true, 아직 남은 레인이 있으면 false
     */
    private boolean advanceSnapshotStructureReady(int chunkX, int chunkZ) {
        if (!ownerTurnMayContinue()) return false;
        // The welcome targets keep their complete durable lanes. Unrelated chunks may wait:
        // starting their LOOT/map/entity receipts here competes with the first snapshot's DB work.
        // No claim or cursor is advanced until that priority window ends.
        if (!mayAdvanceWelcomeCanonicalWork(chunkX, chunkZ)) return false;
        long key = chunkKey(chunkX, chunkZ);
        StructureReadyPass pass = structureReadyPasses.get(key);
        if (pass == null || pass.generation != activeChunkGeneration(key)) {
            pass = new StructureReadyPass(activeChunkGeneration(key));
            structureReadyPasses.put(key, pass);
        }
        while (pass.laneIndex < STRUCTURE_READY_LANE_ORDER.length
                && pass.generation == activeChunkGeneration(key)) {
            if (!ownerTurnMayContinue()) return false;
            TerrainAccessor.FinalLiveCarrierLane lane =
                    STRUCTURE_READY_LANE_ORDER[pass.laneIndex++];
            PendingFinalCarrierClaim pending = pendingFinalCarrierClaims.get(key);
            // 재접속에서 이미 승인된 레인은 작업 없이 다음 틱을 기다릴 이유가 없다.
            // 실제 시도 뒤의 양보와 마지막 복구 경계는 그대로 유지한다.
            if (pending == null || pending.generation != activeChunkGeneration(key)
                    || !pending.remaining.contains(lane)) continue;
            long laneStart = System.nanoTime();
            boolean detachedCompletion = pass.payloadWork != null && pass.payloadWork.ready;
            consumeFinalCarrierLane(chunkX, chunkZ, key, lane, pass);
            // A detached transaction has not acknowledged this lane yet. Keep its cursor here;
            // sibling chunks may advance, but this chunk cannot publish a partially ready view.
            if (pass.payloadAwaiting) pass.laneIndex--;
            long laneNanos = System.nanoTime() - laneStart;
            if (laneNanos > 50_000_000L) {
                log.warn("구조물완료 레인 지연: world={} chunk={},{} lane={} {}ms",
                        worldId, chunkX, chunkZ, lane, laneNanos / 1_000_000L);
            }
            // Accepting a completed write is just owner bookkeeping, not another DB attempt.
            // Do not charge an extra full tick before the next lane can use this turn's slice.
            if (detachedCompletion && !pass.payloadAwaiting) continue;
            return false;
        }
        structureReadyPasses.remove(key);
        concludeFinalLiveCarrierDelivery(key);
        if (!ownerTurnMayContinue()) return false;
        // 복구는 월드 행 잠금을 잡는다. 틱 스레드는 그 잠금을 기다리지 않고 물러나며, 미뤄진
        // 청크는 다음 틱의 retryDeferredFinalCarrierRecoveries 가 이어서 끝낸다.
        if (!recoverFinalCarrierChunkOrDefer(key, chunkX, chunkZ)) return false;
        completeSnapshotStructureReady(key, chunkX, chunkZ);
        return true;
    }

    /**
     * 복구가 끝난 청크의 완료 경계. 미뤄졌던 청크는 재시도 드레인이 여기로 들어오므로 한 번만
     * 실행해야 한다. 저장 컨테이너를 두 번 깨우지 않도록 준비 집합 진입으로 문지기를 세운다.
     */
    private void completeSnapshotStructureReady(long key, int chunkX, int chunkZ) {
        if (!ownerTurnMayContinue()) return;
        if (!snapshotStructureReadyChunks.add(key)) return;
        // Terrain residency can precede structure/FTIK completion by several owner turns.
        // Resume parked fluid only when its entire read window can observe settled chunks.
        fluidSim.onChunkActivated(chunkX, chunkZ);
        var source=accessor.snapshotSource(chunkX,chunkZ);
        if (source != null) source.finalLiveCarrier().ifPresent(carrier ->
                generatedFluidActivation.onChunkReady(chunkX,chunkZ,
                        carrier.sidecars().fluidTicks().stream().map(tick -> tick.packed()).toList()));
        restoreCanonicalLootCandidates(chunkX, chunkZ);
        stampActivationStage(key, ACT_STRUCTURE_READY);
        rehydratingReplayableChunks.remove(key);
        // 구조물 배치가 끝나기 전에 저장 컨테이너를 깨우면 아직 복원되지 않은 블록을 파괴로
        // 오인할 수 있습니다. 직접/단계적 활성화 모두 이 완료 경계에서만 진행을 재개합니다.
        if (!ownerTurnMayContinue()) return;
        furnaceStorage.activateChunk(chunkX, chunkZ);
        if (!ownerTurnMayContinue()) return;
        brewingStorage.activateChunk(chunkX, chunkZ);
        if (!ownerTurnMayContinue()) return;
        campfireStorage.activateChunk(chunkX, chunkZ);
        if (!ownerTurnMayContinue()) return;
        hopperSystem.activateChunk(chunkX, chunkZ);
    }

    boolean fluidSimulationReady(int chunkX, int chunkZ) {
        return accessor.isChunkActivated(chunkX, chunkZ)
                && (customDimension() || snapshotStructureReadyChunks.contains(chunkKey(chunkX, chunkZ)));
    }

    /** Read only the resident final view after structures settle; explicit edits keep their own scheduling. */
    private void visitGeneratedFluids(int cx,int cz,GeneratedFluidActivation.Edge edge,GeneratedFluidActivation.Visitor visitor) {
        if (!accessor.isChunkActivated(cx,cz)) return;
        var source=accessor.snapshotSource(cx,cz);
        if (source==null) return;
        int minX=edge==GeneratedFluidActivation.Edge.EAST?15:0;
        int maxX=edge==GeneratedFluidActivation.Edge.WEST?0:15;
        int minZ=edge==GeneratedFluidActivation.Edge.SOUTH?15:0;
        int maxZ=edge==GeneratedFluidActivation.Edge.NORTH?0:15;
        for (int y=Blocks.MIN_Y;y<=Blocks.MAX_Y;y++) for (int z=minZ;z<=maxZ;z++) for (int x=minX;x<=maxX;x++) {
            int index=Blocks.blockIndex(x,y,z);
            int block=source.blockTypeAt(index);
            if (!Fluids.isFluid(block)) continue;
            int wx=cx*16+x,wz=cz*16+z;
            if (source.hasDynamicOverrideAt(index)
                    && !structureOverlaySites.contains(structurePositionKey(wx,y,wz))) continue;
            BlockPos pos=new BlockPos(wx,y,wz);
            if (protectedDecorationEdits.contains(pos) || mobMutationSites.contains(pos)) continue;
            visitor.accept(wx,y,wz,block);
        }
    }

    /** 진행 가능한 레인은 같은 턴에 소비하고, 외부 조건을 기다리는 청크는 다음 틱에 재개한다. */
    private void markSnapshotStructureReady(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        long generation = activeChunkGeneration(key);
        while (ownerTurnMayContinue() && generation == activeChunkGeneration(key)) {
            // 우선순위 대기는 pass 생성 전에도 발생한다. false를 레인 진행으로 취급해
            // 반복하면 welcome 요청을 전달할 다음 owner 단계에도 도달하지 못한다.
            if (!mayAdvanceWelcomeCanonicalWork(chunkX, chunkZ)) {
                deferredStructureReadyPasses.put(key, generation);
                return;
            }
            if (advanceSnapshotStructureReady(chunkX, chunkZ)) {
                deferredStructureReadyPasses.remove(key, generation);
                return;
            }
            // 진행자가 레인 하나씩 소비한다. 복구가 미뤄졌다면 같은 턴에 다시 잠그려고
            // 돌지 않는다. 남은 마무리는 다음 틱의 재시도 드레인이 맡는다.
            if (deferredFinalCarrierRecoveries.containsKey(key)) return;
            StructureReadyPass pass = structureReadyPasses.get(key);
            if (pass != null && pass.payloadAwaiting) {
                // 검증 실패도 payloadAwaiting을 유지한다. 기존 미승인 claim/cursor를
                // 보존하며 재시도 드레인이 새 쓰기를 발행하지 않도록 그대로 넘긴다.
                deferredStructureReadyPasses.put(key, pass.generation);
                return;
            }
        }
    }

    /** Direct decoration yields across welcome priority and detached payload writes. */
    private void retryDeferredStructureReadyPasses() {
        int remaining = 8;
        for (var entry : new ArrayList<>(deferredStructureReadyPasses.entrySet())) {
            if (!ownerTurnMayContinue() || remaining-- <= 0) return;
            long key = entry.getKey();
            if (entry.getValue() != activeChunkGeneration(key)
                    || advanceSnapshotStructureReady((int) (key >> 32), (int) key)) {
                deferredStructureReadyPasses.remove(key, entry.getValue());
            } else if (deferredStructureReadyPasses.remove(key, entry.getValue())) {
                // Yielded or invalid payloads retain their claim but cannot monopolize
                // the next turn's bounded slice ahead of unrelated ready chunks.
                deferredStructureReadyPasses.put(key, entry.getValue());
            }
        }
    }

    /**
     * 청크 복구를 시도하고, 월드 행 잠금이 정산 워커에 잡혀 있으면 미룬다.
     *
     * @return 복구가 끝나 다음 단계로 진행해도 되면 true, 미뤄졌으면 false
     */
    private boolean recoverFinalCarrierChunkOrDefer(long key, int chunkX, int chunkZ) {
        FinalCarrierLaneInstaller laneInstaller = finalCarrierLaneInstaller;
        if (laneInstaller == null) {
            deferredFinalCarrierRecoveries.remove(key);
            return true;
        }
        long generation = activeChunkGeneration(key);
        if (!finalCarrierLockRetryReady(deferredRecoveryLockRetries, key, generation)) {
            deferredFinalCarrierRecoveries.put(key, generation);
            return false;
        }
        if (TickSafetyTelemetry.isTickThread()
                && laneInstaller instanceof FinalCarrierDurablePayloadInstaller durable
                && durable.supportsDetachedChunkRecovery()) {
            // 복구 재시도 드레인과 구조물완료 진행자가 같은 청크를 번갈아 부른다. 한쪽이 이미
            // 결과를 받아 완료 경계를 넘겼으면 다른 쪽이 새 복구를 제출하지 않게 한다.
            if (snapshotStructureReadyChunks.contains(key)) {
                deferredFinalCarrierRecoveries.remove(key);
                return true;
            }
            Boolean detached = recoverFinalCarrierChunkFromWorker(durable, key, chunkX, chunkZ,
                    generation);
            if (detached != null) return detached;
        }
        long recoverStart = System.nanoTime();
        try {
            laneInstaller.recoverChunk(worldId, chunkX, chunkZ, finalCarrierGameplayInstaller());
        } catch (com.gameexpert.engine.persistence.finalcarrier
                .FinalCarrierPersistenceService.FinalCarrierLaneRetryable retryable) {
            deferredFinalCarrierRecoveries.put(key, activeChunkGeneration(key));
            if (retryable.isWorldLockContention()) {
                deferFinalCarrierWorldLock(deferredRecoveryLockRetries, key, generation);
            }
            return false;
        }
        deferredFinalCarrierRecoveries.remove(key);
        deferredRecoveryLockRetries.remove(key);
        long recoverNanos = System.nanoTime() - recoverStart;
        if (recoverNanos > 50_000_000L) {
            log.warn("구조물완료 복구 지연: world={} chunk={},{} {}ms",
                    worldId, chunkX, chunkZ, recoverNanos / 1_000_000L);
        }
        return true;
    }

    /** One chunk's recovery running on a worker, awaiting owner-side acceptance. */
    private static final class ChunkRecoveryWork {
        private final long generation;
        private final long epoch;
        /** Owner snapshot deciding the replay's resident activations; worker-confined until ready. */
        private final DetachedGameplayVerdict verdict;
        private volatile Throwable failure;
        private volatile boolean ready;

        private ChunkRecoveryWork(long generation, long epoch, DetachedGameplayVerdict verdict) {
            this.generation = generation;
            this.epoch = epoch;
            this.verdict = verdict;
        }
    }

    private final Map<Long, ChunkRecoveryWork> chunkRecoveryWorks = new HashMap<>();
    private final AtomicInteger chunkRecoveryWorkCount = new AtomicInteger();
    private static final int MAX_CHUNK_RECOVERY_WORKS = 8;

    /**
     * 틱 스레드에서 청크 복구(월드 잠금·변경 행·정본 스냅샷 읽기)가 청크마다 수십~수백 ms를
     * 먹었다. 복구는 워커에서 끝내고, 틱은 다음 재시도에서 결과만 받는다. 스포너·솔질 블록·
     * 장식 항아리처럼 상주 활성화가 필요한 행은 제출 시점의 owner 스냅샷으로 워커가 판정하고,
     * 승인된 활성화만 여기서 owner가 그대로 설치한다. 스냅샷으로 증명하지 못한 판정이나 설치가
     * 어긋난 경우에는 null을 돌려 기존 틱 복구로 넘긴다.
     *
     * @return 끝났으면 true, 기다려야 하면 false, 틱 스레드에서 복구해야 하면 null
     */
    private Boolean recoverFinalCarrierChunkFromWorker(FinalCarrierDurablePayloadInstaller installer,
            long key, int chunkX, int chunkZ, long generation) {
        ChunkRecoveryWork work = chunkRecoveryWorks.get(key);
        if (work != null && (work.generation != generation || !lifecycleEpochCurrent(work.epoch))) {
            // 낡은 복구는 계속 돌 수 있지만 결과를 받지 않는다.
            chunkRecoveryWorks.remove(key);
            work = null;
        }
        if (work == null) {
            submitChunkRecoveryWork(installer, key, chunkX, chunkZ, generation);
            deferredFinalCarrierRecoveries.put(key, generation);
            return false;
        }
        if (!work.ready) {
            deferredFinalCarrierRecoveries.put(key, generation);
            return false;
        }
        chunkRecoveryWorks.remove(key);
        Throwable failure = work.failure;
        if (failure == null) {
            // 이 드레인이 결과의 유일한 소비자다(맵에서 먼저 뺐다). 승인된 활성화를 순서대로 설치한다.
            if (!applyApprovedActivations(work.verdict, chunkX, chunkZ)) return null;
            deferredFinalCarrierRecoveries.remove(key);
            deferredRecoveryLockRetries.remove(key);
            return true;
        }
        if (failure instanceof com.gameexpert.engine.persistence.finalcarrier
                .FinalCarrierPersistenceService.OwnerActivationRequired) {
            return null;
        }
        if (failure instanceof com.gameexpert.engine.persistence.finalcarrier
                .FinalCarrierPersistenceService.FinalCarrierLaneRetryable retryable) {
            deferredFinalCarrierRecoveries.put(key, generation);
            if (retryable.isWorldLockContention()) {
                deferFinalCarrierWorldLock(deferredRecoveryLockRetries, key, generation);
            }
            return false;
        }
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
        throw new IllegalStateException("detached chunk recovery failed", failure);
    }

    private void submitChunkRecoveryWork(FinalCarrierDurablePayloadInstaller installer,
            long key, int chunkX, int chunkZ, long generation) {
        if (chunkRecoveryWorkCount.get() >= MAX_CHUNK_RECOVERY_WORKS) return;
        ChunkRecoveryWork work = new ChunkRecoveryWork(generation, lifecycleEpoch,
                snapshotGameplayVerdict(chunkX, chunkZ, DETACHED_RECOVERY_LANES));
        chunkRecoveryWorks.put(key, work);
        chunkRecoveryWorkCount.incrementAndGet();
        Runnable discarded = () -> {
            work.failure = new com.gameexpert.engine.persistence.finalcarrier
                    .FinalCarrierPersistenceService.FinalCarrierLaneRetryable(
                            "detached chunk recovery was discarded");
            work.ready = true;
            chunkRecoveryWorkCount.decrementAndGet();
        };
        try {
            executeOwned(FINAL_CARRIER_PAYLOAD_WORKERS, () -> {
                try {
                    if (!lifecycleEpochCurrent(work.epoch)) {
                        work.failure = new com.gameexpert.engine.persistence.finalcarrier
                                .FinalCarrierPersistenceService.FinalCarrierLaneRetryable(
                                        "detached chunk recovery outlived its runtime");
                        return;
                    }
                    long started = System.nanoTime();
                    installer.recoverChunkDetached(worldId, chunkX, chunkZ, work.verdict);
                    long elapsed = System.nanoTime() - started;
                    if (elapsed > 200_000_000L) {
                        log.info("구조물완료 분리 복구 지연: world={} chunk={},{} {}ms",
                                worldId, chunkX, chunkZ, elapsed / 1_000_000L);
                    }
                } catch (Throwable failure) {
                    work.failure = failure;
                } finally {
                    work.ready = true;
                    chunkRecoveryWorkCount.decrementAndGet();
                }
            }, discarded);
        } catch (RejectedExecutionException full) {
            chunkRecoveryWorks.remove(key, work);
            chunkRecoveryWorkCount.decrementAndGet();
        }
    }

    /**
     * 무대기 잠금 실패로 미뤄 둔 청크 복구를 다음 틱에 이어서 끝낸다. 세대가 바뀐 청크는
     * 이미 언로드/재생성된 것이므로 조용히 버린다.
     */
    private void retryDeferredFinalCarrierRecoveries() {
        reportDeferredFinalCarrierBacklog();
        if (deferredFinalCarrierRecoveries.isEmpty() || !ownerTurnMayContinue()) return;
        var pending = new ArrayList<>(deferredFinalCarrierRecoveries.entrySet());
        for (var entry : pending) {
            if (!ownerTurnMayContinue()) return;
            long key = entry.getKey();
            if (entry.getValue() != activeChunkGeneration(key)) {
                deferredFinalCarrierRecoveries.remove(key, entry.getValue());
                deferredRecoveryLockRetries.remove(key);
                continue;
            }
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            if (!recoverFinalCarrierChunkOrDefer(key, chunkX, chunkZ)) continue;
            completeSnapshotStructureReady(key, chunkX, chunkZ);
        }
    }

    /**
     * 정산 워커가 잡은 월드 행 때문에 미뤄 둔 청크의 스케줄러 활성화. 다음 틱에 재시도한다.
     * 값은 미룰 때의 청크 세대이며, 세대가 바뀌면 버린다.
     */
    private final Map<Long, Long> deferredFinalCarrierRecoveries = new LinkedHashMap<>();
    private final Map<Long, Long> deferredTickSchedulerActivations = new LinkedHashMap<>();
    private final Map<Long, DeferredWorldLockRetry> deferredRecoveryLockRetries = new HashMap<>();
    private final Map<Long, DeferredWorldLockRetry> deferredActivationLockRetries = new HashMap<>();
    private boolean finalCarrierWorldLockBusyThisTurn;

    private static final class DeferredWorldLockRetry {
        private final long generation;
        private int attempts;
        private long retryAfter;

        private DeferredWorldLockRetry(long generation) { this.generation = generation; }
    }

    private boolean finalCarrierLockRetryReady(Map<Long, DeferredWorldLockRetry> retries,
            long key, long generation) {
        if (TickSafetyTelemetry.isTickThread() && finalCarrierWorldLockBusyThisTurn) return false;
        DeferredWorldLockRetry retry = retries.get(key);
        if (retry != null && retry.generation != generation) {
            retries.remove(key);
            retry = null;
        }
        return retry == null || clock.gameTimeMcTicks() >= retry.retryAfter;
    }

    private void deferFinalCarrierWorldLock(Map<Long, DeferredWorldLockRetry> retries,
            long key, long generation) {
        finalCarrierWorldLockBusyThisTurn = TickSafetyTelemetry.isTickThread();
        DeferredWorldLockRetry retry = retries.get(key);
        if (retry == null || retry.generation != generation) {
            retry = new DeferredWorldLockRetry(generation);
            retries.put(key, retry);
        }
        retry.attempts = Math.min(6, retry.attempts + 1);
        long delay = 1L << Math.min(5, retry.attempts - 1);
        long now = clock.gameTimeMcTicks();
        retry.retryAfter = now > Long.MAX_VALUE - delay ? Long.MAX_VALUE : now + delay;
    }

    /** 무대기 잠금 실패로 미뤄 둔 스케줄러 청크 활성화를 다음 틱에 재시도한다. */
    private void retryDeferredTickSchedulerActivations() {
        if (deferredTickSchedulerActivations.isEmpty() || !ownerTurnMayContinue()) return;
        FinalCarrierTickScheduler scheduler = finalCarrierTickScheduler;
        if (scheduler == null) return;
        var pending = new ArrayList<>(deferredTickSchedulerActivations.entrySet());
        for (var entry : pending) {
            if (!ownerTurnMayContinue()) return;
            long key = entry.getKey();
            if (entry.getValue() != activeChunkGeneration(key)) {
                deferredTickSchedulerActivations.remove(key, entry.getValue());
                deferredActivationLockRetries.remove(key);
                continue;
            }
            if (activateFinalCarrierTickChunkOrDefer(scheduler, key,
                    (int) (key >> 32), (int) key, entry.getValue())) {
                deferredTickSchedulerActivations.remove(key, entry.getValue());
            }
        }
    }

    /**
     * 미뤄 둔 복구/활성화가 남아 있으면 60초에 한 줄로 남긴다. 배달이 멈춘 것처럼 보일 때
     * 원인이 잠금 경합 대기열인지 아닌지를 로그만으로 가릴 수 있어야 한다.
     */
    private void reportDeferredFinalCarrierBacklog() {
        if (deferredFinalCarrierRecoveries.isEmpty()
                && deferredTickSchedulerActivations.isEmpty()) return;
        long now = System.nanoTime();
        if (lastDeferredBacklogLogNanos != 0L
                && now - lastDeferredBacklogLogNanos < 60_000_000_000L) return;
        lastDeferredBacklogLogNanos = now;
        // 대기 수에는 워커에서 도는 복구·활성화 읽기도 들어 있다. 경합과 구분하도록 함께 적는다.
        log.info("최종캐리어 잠금 경합 대기열: world={} 복구대기={} 활성화대기={} 분리복구={} 분리활성화={}",
                worldId, deferredFinalCarrierRecoveries.size(),
                deferredTickSchedulerActivations.size(), chunkRecoveryWorks.size(),
                tickActivationPreparations.size());
    }

    private long lastDeferredBacklogLogNanos;
    private long lastPayloadBacklogLogNanos;

    private void reportFinalCarrierPayloadBacklog() {
        long now = System.nanoTime();
        if (lastPayloadBacklogLogNanos != 0L
                && now - lastPayloadBacklogLogNanos < 60_000_000_000L) return;
        int pending = 0, completed = 0, invalid = 0;
        long oldest = 0;
        for (StructureReadyPass pass : structureReadyPasses.values()) {
            if (!pass.payloadAwaiting) continue;
            pending++;
            if (pass.payloadInvalid) invalid++;
            PayloadWork work = pass.payloadWork;
            if (work == null) continue;
            if (work.ready) completed++;
            oldest = Math.max(oldest, now - work.submittedNanos);
        }
        if (pending == 0) return;
        lastPayloadBacklogLogNanos = now;
        log.info("정본 페이로드 대기열: world={} 미완료={} 작업중={} 반영대기={} 손상={} 최장={}ms",
                worldId, pending, finalCarrierPayloadWorkCount.get(), completed, invalid,
                oldest / 1_000_000L);
    }

    /** @return 활성화가 끝났으면 true, 월드 행 잠금 경합으로 미뤘으면 false */
    private boolean activateFinalCarrierTickChunkOrDefer(FinalCarrierTickScheduler scheduler,
            long key, int chunkX, int chunkZ, long generation) {
        if (!finalCarrierLockRetryReady(deferredActivationLockRetries, key, generation)) {
            deferredTickSchedulerActivations.put(key, generation);
            return false;
        }
        if (TickSafetyTelemetry.isTickThread()) {
            return activateFinalCarrierTickChunkFromWorker(scheduler, key, chunkX, chunkZ, generation);
        }
        WorldWorkEvent work = architectureWork("final-carrier-scheduler-activation", true,
                chunkX, chunkZ, generation);
        try {
            scheduler.activateChunk(chunkX, chunkZ);
            deferredActivationLockRetries.remove(key);
            return true;
        } catch (com.gameexpert.engine.persistence.finalcarrier
                .FinalCarrierPersistenceService.FinalCarrierLaneRetryable retryable) {
            WorldWorkEvent.outcome(work, "retry");
            deferActivationRetry(key, generation, retryable);
            return false;
        } catch (RuntimeException | Error failure) {
            WorldWorkEvent.outcome(work, "failed");
            throw failure;
        } finally {
            WorldWorkEvent.finish(work);
        }
    }

    private void deferActivationRetry(long key, long generation,
            com.gameexpert.engine.persistence.finalcarrier
                    .FinalCarrierPersistenceService.FinalCarrierLaneRetryable retryable) {
        deferredTickSchedulerActivations.put(key, generation);
        if (retryable.isWorldLockContention()) {
            deferFinalCarrierWorldLock(deferredActivationLockRetries, key, generation);
        }
    }

    /** One chunk's durable tick recovery read on a worker, awaiting owner-side acceptance. */
    private static final class TickActivationPreparation {
        private final long generation;
        private final long epoch;
        private final long residentStamp;
        private volatile FinalCarrierTickScheduler.PreparedChunkRecovery prepared;
        private volatile Throwable failure;
        private volatile boolean ready;

        private TickActivationPreparation(long generation, long epoch, long residentStamp) {
            this.generation = generation;
            this.epoch = epoch;
            this.residentStamp = residentStamp;
        }
    }

    private final Map<Long, TickActivationPreparation> tickActivationPreparations = new HashMap<>();
    private final AtomicInteger tickActivationPreparationCount = new AtomicInteger();
    private static final int MAX_TICK_ACTIVATION_PREPARATIONS = 8;

    /**
     * Owner-thread activation: the durable recovery read (world lock, tick rows, legacy probe) used to
     * run here, 30-100 ms per chunk, and a streaming burst chained them into owner turns of several
     * hundred milliseconds that held every player's input. The read now runs on a worker; the owner
     * only validates and installs the detached result on a later retry of this same deferred
     * activation. Acceptance requires the chunk generation and its resident-queue stamp to be
     * unchanged since submission, so a row admitted or settled meanwhile forces a fresh read.
     */
    private boolean activateFinalCarrierTickChunkFromWorker(FinalCarrierTickScheduler scheduler,
            long key, int chunkX, int chunkZ, long generation) {
        TickActivationPreparation preparation = tickActivationPreparations.get(key);
        if (preparation != null && (preparation.generation != generation
                || !lifecycleEpochCurrent(preparation.epoch))) {
            // A stale read may still be running; its result is simply never adopted.
            tickActivationPreparations.remove(key);
            preparation = null;
        }
        if (preparation == null) {
            submitTickActivationPreparation(scheduler, key, chunkX, chunkZ, generation);
            deferredTickSchedulerActivations.put(key, generation);
            return false;
        }
        if (!preparation.ready) {
            deferredTickSchedulerActivations.put(key, generation);
            return false;
        }
        tickActivationPreparations.remove(key);
        Throwable failure = preparation.failure;
        if (failure instanceof com.gameexpert.engine.persistence.finalcarrier
                .FinalCarrierPersistenceService.FinalCarrierLaneRetryable retryable) {
            deferActivationRetry(key, generation, retryable);
            return false;
        }
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
        if (failure != null) throw new IllegalStateException("tick activation read failed", failure);
        if (preparation.prepared == null
                || scheduler.residentStamp(chunkX, chunkZ) != preparation.residentStamp) {
            deferredTickSchedulerActivations.put(key, generation);
            return false;
        }
        WorldWorkEvent work = architectureWork("final-carrier-scheduler-activation", true,
                chunkX, chunkZ, generation);
        try {
            scheduler.acceptChunkRecovery(preparation.prepared);
            deferredActivationLockRetries.remove(key);
            return true;
        } catch (RuntimeException | Error acceptFailure) {
            WorldWorkEvent.outcome(work, "failed");
            throw acceptFailure;
        } finally {
            WorldWorkEvent.finish(work);
        }
    }

    private void submitTickActivationPreparation(FinalCarrierTickScheduler scheduler,
            long key, int chunkX, int chunkZ, long generation) {
        if (tickActivationPreparationCount.get() >= MAX_TICK_ACTIVATION_PREPARATIONS) return;
        TickActivationPreparation preparation = new TickActivationPreparation(
                generation, lifecycleEpoch, scheduler.residentStamp(chunkX, chunkZ));
        tickActivationPreparations.put(key, preparation);
        tickActivationPreparationCount.incrementAndGet();
        Runnable discarded = () -> {
            preparation.failure = new com.gameexpert.engine.persistence.finalcarrier
                    .FinalCarrierPersistenceService.FinalCarrierLaneRetryable(
                            "tick activation read was discarded");
            preparation.ready = true;
            tickActivationPreparationCount.decrementAndGet();
        };
        try {
            executeOwned(FINAL_CARRIER_PAYLOAD_WORKERS, () -> {
                try {
                    if (lifecycleEpochCurrent(preparation.epoch)) {
                        preparation.prepared = scheduler.prepareChunkRecovery(chunkX, chunkZ);
                    }
                } catch (Throwable failure) {
                    preparation.failure = failure;
                } finally {
                    preparation.ready = true;
                    tickActivationPreparationCount.decrementAndGet();
                }
            }, discarded);
        } catch (RejectedExecutionException full) {
            tickActivationPreparations.remove(key, preparation);
            tickActivationPreparationCount.decrementAndGet();
        }
    }

    /** 한 청크의 구조물 완료 진행 상태. 세대가 바뀌면 남은 레인은 더 시도하지 않는다. */
    private static final class StructureReadyPass {
        private final long generation;
        private int laneIndex;
        private NeutralFinalChunk visibleCarrier;
        private String sourceFingerprint;
        private boolean lootCandidatesRegistered;
        private PayloadWork payloadWork;
        private boolean payloadAwaiting;
        private boolean payloadInvalid;
        /** 워커가 상주 판정을 증명하지 못해 owner 동기 설치로 되돌린 레인. */
        private TerrainAccessor.FinalLiveCarrierLane payloadOwnerLane;
        private int payloadRetryAttempt;
        private long payloadRetryAfter;

        private StructureReadyPass(long generation) {
            this.generation = generation;
        }

        /** The visible carrier is immutable and fixed for this generation-local pass. */
        private String sourceFingerprint() {
            if (sourceFingerprint == null) {
                sourceFingerprint = finalCarrierSourceFingerprint(visibleCarrier);
            }
            return sourceFingerprint;
        }
    }

    private static final TerrainAccessor.FinalLiveCarrierLane[] STRUCTURE_READY_LANE_ORDER = {
            TerrainAccessor.FinalLiveCarrierLane.BLOCK_TICKS,
            TerrainAccessor.FinalLiveCarrierLane.FLUID_TICKS,
            TerrainAccessor.FinalLiveCarrierLane.LOOT,
            TerrainAccessor.FinalLiveCarrierLane.SPAWNERS,
            TerrainAccessor.FinalLiveCarrierLane.OWNERS,
            TerrainAccessor.FinalLiveCarrierLane.ARCHAEOLOGY,
            TerrainAccessor.FinalLiveCarrierLane.BLOCK_ENTITIES,
            TerrainAccessor.FinalLiveCarrierLane.ENTITIES,
            TerrainAccessor.FinalLiveCarrierLane.BEES
    };

    private final Map<Long, StructureReadyPass> structureReadyPasses = new HashMap<>();
    private final Map<Long, Long> deferredStructureReadyPasses = new LinkedHashMap<>();

    /** Immutable request identity plus a release-published worker result; never a live callback. */
    private static final class PayloadWork {
        private final PendingFinalCarrierClaim pending;
        private final long epoch;
        private final long submittedNanos = System.nanoTime();
        private volatile long startedNanos;
        private long completedNanos;
        private FinalCarrierInstallResult result;
        private Throwable failure;
        /** Detached gameplay lanes only: the owner snapshot that approved the committed activations. */
        private DetachedGameplayVerdict verdict;
        /** Detached BEES only: the committed canonical outcome the owner still has to activate. */
        private com.gameexpert.engine.persistence.finalcarrier.FinalCarrierPersistenceService
                .CanonicalBeeInstallResult beeOutcome;
        private volatile boolean ready;

        private PayloadWork(PendingFinalCarrierClaim pending, long epoch) {
            this.pending = pending;
            this.epoch = epoch;
        }
    }

    /** chunkKey -> 활성화 단계 스탬프(ns). SNAPSHOT_STAGE_TRACE 가 꺼져 있으면 비어 있다. */
    private final Map<Long, long[]> activationStageTrace = new ConcurrentHashMap<>();

    private void stampActivationStage(long key, int stage) {
        if (!SNAPSHOT_STAGE_TRACE) return;
        long[] stamps = activationStageTrace.computeIfAbsent(key, ignored -> new long[ACT_STAGES]);
        if (stamps[stage] == 0L) stamps[stage] = System.nanoTime();
        if (stage == ACT_BASE_READY || stage == ACT_STRUCTURE_READY) {
            reportActivationStageTrace(key, stamps);
        }
    }

    /** 두 준비 조건이 모두 열린 순간 한 줄만 남긴다. */
    private void reportActivationStageTrace(long key, long[] stamps) {
        if (stamps[ACT_BASE_READY] == 0L || stamps[ACT_STRUCTURE_READY] == 0L) return;
        if (activationStageTrace.remove(key) == null) return;
        long from = stamps[ACT_DEMAND] != 0L ? stamps[ACT_DEMAND]
                : Math.min(stamps[ACT_BASE_READY], stamps[ACT_STRUCTURE_READY]);
        log.info("activation stage: world={} chunk={},{} 수요+0 플래너시작+{}ms 플래너종료+{}ms"
                        + " 채택+{}ms 기본준비+{}ms 구조준비+{}ms 계획중={} 채택대기={} 적용대기={}",
                worldId, (int) (key >> 32), (int) key,
                stageMillis(from, stamps[ACT_PLAN_START]), stageMillis(from, stamps[ACT_PLAN_END]),
                stageMillis(from, stamps[ACT_ADOPTED]), stageMillis(from, stamps[ACT_BASE_READY]),
                stageMillis(from, stamps[ACT_STRUCTURE_READY]),
                pendingChunkActivationPlans.size(), preparedChunkActivations.size(),
                pendingChunkActivationApplications.size());
    }

    /**
     * Resolves each independently durable lane after replayable and structure ownership is final. Unsupported or
     * unavailable consumers retain their generation-local claim for retry and remain in TerrainAccessor's ledger
     * for redelivery after eviction; successful lanes are never presented to a later residency again.
     */
    /**
     * 레인 하나만 소비한다. 소유권/세대 검사와 실패 분류는 기존과 동일하다.
     *
     * <p>가시 반송체는 한 pass에서 한 번만 구한다. 레인마다 다시 구하면 앞 레인이 소비한 셀이
     * 뒤 레인의 가시성에서 빠져 기존 의미가 바뀐다.</p>
     */
    private void consumeFinalCarrierLane(int chunkX, int chunkZ, long key,
            TerrainAccessor.FinalLiveCarrierLane lane, StructureReadyPass pass) {
        if (!ownerTurnMayContinue()) return;
        PendingFinalCarrierClaim pending = pendingFinalCarrierClaims.get(key);
        if (pending == null || pending.generation != activeChunkGeneration(key)) return;
        if (!pending.remaining.contains(lane)) return;
        if (pass.visibleCarrier == null) {
            pass.visibleCarrier = accessor.carrierWithVisibleSidecars(
                    chunkX, chunkZ, pending.claim.carrier());
        }
        NeutralFinalChunk visibleCarrier = pass.visibleCarrier;
        if (visibleCarrier == null) return;
        if (lane != TerrainAccessor.FinalLiveCarrierLane.BEES) {
            consumeFinalCarrierNonBeeLane(pending, visibleCarrier, lane, pass);
            return;
        }
        consumeFinalCarrierBeeLane(pending, visibleCarrier, chunkX, chunkZ, pass);
    }

    private void consumeFinalCarrierBeeLane(PendingFinalCarrierClaim pending,
            NeutralFinalChunk visibleCarrier, int chunkX, int chunkZ,
            StructureReadyPass pass) {
        if (pending.remaining.contains(TerrainAccessor.FinalLiveCarrierLane.BEES)) {
            try {
                Mc263BeeSidecarPlan.Plan plan = Mc263BeeSidecarPlan.prepare(
                        chunkX, chunkZ, visibleCarrier.sidecars().bees());
                if (finalCarrierPersistence == null) return;
                NeutralFinalChunk.Sidecars exact = projectFinalCarrierSidecars(
                        visibleCarrier, TerrainAccessor.FinalLiveCarrierLane.BEES);
                com.gameexpert.engine.persistence.finalcarrier.FinalCarrierPersistenceService
                        .CanonicalBeeInstallResult outcome;
                if (TickSafetyTelemetry.isTickThread()) {
                    outcome = advanceDetachedBees(pending, chunkX, chunkZ, exact, pass);
                    if (outcome == null) return;
                } else {
                    outcome = finalCarrierPersistence.installCanonicalBees(
                            worldId, chunkX, chunkZ, pass.sourceFingerprint(), exact);
                }
                if (outcome == com.gameexpert.engine.persistence.finalcarrier
                        .FinalCarrierPersistenceService.CanonicalBeeInstallResult.RETRY) return;
                if (!ownerTurnMayContinue()) return;
                if (outcome == com.gameexpert.engine.persistence.finalcarrier
                                .FinalCarrierPersistenceService.CanonicalBeeInstallResult
                                        .ACKNOWLEDGED
                        || outcome == com.gameexpert.engine.persistence.finalcarrier
                                .FinalCarrierPersistenceService.CanonicalBeeInstallResult
                                        .ALREADY_ACKNOWLEDGED) {
                    mobSystem.installFinalCarrierBeeNestOccupants(plan);
                }
                acknowledgeFinalCarrierLane(
                        pending, TerrainAccessor.FinalLiveCarrierLane.BEES);
            } catch (RuntimeException invalidOrUnavailable) {
                log.error("Final-carrier BEES lane remains unacknowledged for chunk {},{}: {}",
                        chunkX, chunkZ, invalidOrUnavailable.getMessage());
                return;
            }
        }
    }

    /**
     * BEES on the owner tick: the canonical transaction (row lookup, claim, row write, receipt) took
     * up to a few hundred milliseconds. It never reads resident state, so it commits on the gameplay
     * payload worker and the owner only activates the committed outcome, exactly as the synchronous
     * path did right after its transaction. The pass keeps its cursor on BEES while the write is in
     * flight and is the only consumer of the result; a result from another residency or runtime is
     * dropped and the lane is written again, which the transaction answers idempotently.
     *
     * @return the committed outcome to activate now, or null while the lane waits
     */
    private com.gameexpert.engine.persistence.finalcarrier.FinalCarrierPersistenceService
            .CanonicalBeeInstallResult advanceDetachedBees(PendingFinalCarrierClaim pending,
                    int chunkX, int chunkZ, NeutralFinalChunk.Sidecars exact,
                    StructureReadyPass pass) {
        var persistence = finalCarrierPersistence;
        pass.payloadAwaiting = true;
        PayloadWork work = pass.payloadWork;
        if (work != null) {
            if (!work.ready) return null;
            pass.payloadWork = null;
            if (!lifecycleEpochCurrent(work.epoch) || work.pending != pending
                    || pending.generation != activeChunkGeneration(chunkKey(chunkX, chunkZ))) {
                return null;
            }
            // 동기 경로와 같이 결과를 받은 뒤에는 이 레인을 더 기다리지 않는다(RETRY·실패 포함).
            pass.payloadAwaiting = false;
            Throwable failure = work.failure;
            if (failure instanceof RuntimeException runtime) throw runtime;
            if (failure instanceof Error error) throw error;
            if (failure != null) throw new IllegalStateException("detached BEES write failed", failure);
            return java.util.Objects.requireNonNull(work.beeOutcome, "BEES outcome");
        }
        if (finalCarrierGameplayPayloadWorkCount.get() >= 8) return null;
        PayloadWork submitted = new PayloadWork(pending, lifecycleEpoch);
        String sourceFingerprint = pass.sourceFingerprint();
        pass.payloadWork = submitted;
        finalCarrierGameplayPayloadWorkCount.incrementAndGet();
        Runnable discarded = () -> {
            submitted.failure = new com.gameexpert.engine.persistence.finalcarrier
                    .FinalCarrierPersistenceService.FinalCarrierLaneRetryable(
                            "detached BEES write was discarded");
            submitted.completedNanos = System.nanoTime();
            submitted.ready = true;
            finalCarrierGameplayPayloadWorkCount.decrementAndGet();
        };
        try {
            executeOwned(FINAL_CARRIER_GAMEPLAY_PAYLOAD_WORKERS, () -> {
                try {
                    if (!lifecycleEpochCurrent(submitted.epoch)) {
                        submitted.failure = new com.gameexpert.engine.persistence.finalcarrier
                                .FinalCarrierPersistenceService.FinalCarrierLaneRetryable(
                                        "detached BEES write outlived its runtime");
                        return;
                    }
                    submitted.startedNanos = System.nanoTime();
                    submitted.beeOutcome = persistence.installCanonicalBees(
                            worldId, chunkX, chunkZ, sourceFingerprint, exact);
                } catch (Throwable failure) {
                    submitted.failure = failure;
                } finally {
                    submitted.completedNanos = System.nanoTime();
                    submitted.ready = true;
                    finalCarrierGameplayPayloadWorkCount.decrementAndGet();
                }
            }, discarded);
        } catch (RejectedExecutionException full) {
            pass.payloadWork = null;
            finalCarrierGameplayPayloadWorkCount.decrementAndGet();
        }
        return null;
    }

    private void concludeFinalLiveCarrierDelivery(long key) {
        PendingFinalCarrierClaim pending = pendingFinalCarrierClaims.get(key);
        if (pending == null || pending.generation != activeChunkGeneration(key)
                || !pending.remaining.isEmpty()
                || !pendingFinalCarrierClaims.remove(key, pending)) return;
    }

    private void queueFinalCarrierTickAdmission(long key, PendingFinalCarrierClaim pending) {
        if (ownerTurnMayContinue() && pending != null && (pending.remaining.contains(
                TerrainAccessor.FinalLiveCarrierLane.BLOCK_TICKS)
                || pending.remaining.contains(TerrainAccessor.FinalLiveCarrierLane.FLUID_TICKS))
                && queuedFinalCarrierTickAdmissionChunks.add(key)) {
            pendingFinalCarrierTickAdmissionOrder.add(key);
        }
    }

    /**
     * BLOCK then FLUID admission is deliberate; every later lane remains independent after a
     * failure. 순서는 {@link #STRUCTURE_READY_LANE_ORDER}가 소유하고, 여기서는 그 순서로 넘어온
     * 레인 하나만 처리한다.
     */
    private void consumeFinalCarrierNonBeeLane(PendingFinalCarrierClaim pending,
            NeutralFinalChunk visibleCarrier,
            TerrainAccessor.FinalLiveCarrierLane lane, StructureReadyPass pass) {
        if (!ownerTurnMayContinue()) return;
        if (!pending.remaining.contains(lane)) return;
        String sourceFingerprint = pass.sourceFingerprint();
        try {
            boolean durable = switch (lane) {
                case BLOCK_TICKS -> admitFinalCarrierBlockTicks(pending, visibleCarrier,
                        sourceFingerprint);
                case FLUID_TICKS -> admitFinalCarrierFluidTicks(pending, visibleCarrier,
                        sourceFingerprint);
                case LOOT, SPAWNERS, OWNERS, ARCHAEOLOGY, BLOCK_ENTITIES, ENTITIES ->
                        installFinalCarrierPayloadLane(lane, visibleCarrier, sourceFingerprint,
                                pending, pass);
                case BEES -> false;
            };
            if (durable) acknowledgeFinalCarrierLane(pending, lane);
        } catch (RuntimeException invalidOrUnavailable) {
            log.error("Final-carrier {} lane remains unacknowledged for chunk {},{}: {}",
                    lane, visibleCarrier.chunkX(), visibleCarrier.chunkZ(),
                    invalidOrUnavailable.getMessage());
        }
    }

    /**
     * 틱 레인(BLOCK_TICKS/FLUID_TICKS)의 영수증은 <b>가시</b> 사이드카가 아니라 정본 클레임의
     * 반송체에서 뽑아야 합니다.
     *
     * <p>{@code carrierWithVisibleSidecars} 는 나중에 다른 주인이 가져간 셀의 행을 걸러냅니다.
     * 다른 레인에는 그게 맞지만 틱 레인에는 맞지 않습니다. 영속 계층은 승인받은 영수증을
     * 커밋된 정본 청크에서 다시 계산해 글자 그대로 대조하는데(그쪽 {@code requireExactTickAdmission}),
     * 그 정본에는 걸러낸 행이 그대로 남아 있기 때문입니다. 그래서 플레이어가 블록 하나만 바꿔도
     * 그 청크의 틱 레인이 "정확한 정본 레인 투영이 아니다"로 <b>영구 거부</b>되고, 청크마다
     * ERROR 한 줄이 남았습니다.
     *
     * <p>가시성으로 걸러낼 필요도 없습니다. 예약된 틱의 유효성은 이미 소진 시점에
     * {@code finalCarrierTickLiveTypes} 로 확인합니다 — 셀의 블록 종류가 더는 맞지 않으면 그때
     * 버려집니다. 승인 단계에서 미리 걸러내는 것은 중복이면서 정확성 계약만 깨뜨렸습니다.
     */
    private NeutralFinalChunk canonicalTickCarrier(
            PendingFinalCarrierClaim pending, NeutralFinalChunk visible) {
        NeutralFinalChunk canonical =
                pending == null ? null : pending.claim.carrier();
        if (canonical == null) return visible;
        if (canonical.chunkX() != visible.chunkX() || canonical.chunkZ() != visible.chunkZ()) {
            return visible;
        }
        return canonical;
    }

    private boolean admitFinalCarrierBlockTicks(PendingFinalCarrierClaim pending,
            NeutralFinalChunk carrier, String sourceFingerprint) {
        if (!ownerTurnMayContinue()) return false;
        FinalCarrierTickScheduler scheduler = finalCarrierTickScheduler;
        if (scheduler == null) return false;
        NeutralFinalChunk canonical = canonicalTickCarrier(pending, carrier);
        var sidecars = canonical.sidecars();
        var receipt = FinalCarrierTickScheduler.blockReceipt(worldId, canonical.chunkX(),
                canonical.chunkZ(), sourceFingerprint, sidecars.blockTicks());
        if (TickSafetyTelemetry.isTickThread()) {
            submitFinalCarrierTickAdmission(pending, canonical,
                    TerrainAccessor.FinalLiveCarrierLane.BLOCK_TICKS, receipt,
                    clock.gameTimeMcTicks());
            return false;
        }
        if (!ownerTurnMayContinue()) return false;
        scheduler.admitBlockLane(receipt, clock.gameTimeMcTicks(), true, sidecars.blockTicks());
        return true;
    }

    private boolean admitFinalCarrierFluidTicks(PendingFinalCarrierClaim pending,
            NeutralFinalChunk carrier, String sourceFingerprint) {
        if (!ownerTurnMayContinue()) return false;
        FinalCarrierTickScheduler scheduler = finalCarrierTickScheduler;
        if (scheduler == null) return false;
        NeutralFinalChunk canonical = canonicalTickCarrier(pending, carrier);
        var sidecars = canonical.sidecars();
        var receipt = FinalCarrierTickScheduler.fluidReceipt(worldId, canonical.chunkX(),
                canonical.chunkZ(), sourceFingerprint, sidecars.fluidTicks());
        if (TickSafetyTelemetry.isTickThread()) {
            submitFinalCarrierTickAdmission(pending, canonical,
                    TerrainAccessor.FinalLiveCarrierLane.FLUID_TICKS, receipt,
                    clock.gameTimeMcTicks());
            return false;
        }
        if (!ownerTurnMayContinue()) return false;
        scheduler.admitFluidLane(receipt, clock.gameTimeMcTicks(), true, sidecars.fluidTicks());
        return true;
    }

    private boolean submitFinalCarrierTickAdmission(PendingFinalCarrierClaim pending,
            NeutralFinalChunk carrier, TerrainAccessor.FinalLiveCarrierLane lane,
            FinalCarrierTickScheduler.CarrierReceipt receipt, long nowMcTick) {
        if (!ownerTurnMayContinue()) return false;
        FinalCarrierTickScheduler scheduler = finalCarrierTickScheduler;
        if (scheduler == null) return false;
        long key = chunkKey(carrier.chunkX(), carrier.chunkZ());
        FinalCarrierTickAdmissionKey admissionKey = new FinalCarrierTickAdmissionKey(
                key, pending.generation, lane);
        long admissionEpoch;
        synchronized (this) {
            if (!lifecycleEpochCurrent(lifecycleEpoch)
                    || failedFinalCarrierTickAdmissions.contains(admissionKey)
                    || !finalCarrierTickAdmissionRetryReady(admissionKey)
                    || !inFlightFinalCarrierTickAdmissions.add(admissionKey)) return false;
            admissionEpoch = lifecycleEpoch;
        }
        try {
            executeOwned(FINAL_CARRIER_LANE_WORKERS, () -> {
                if (!lifecycleEpochCurrent(admissionEpoch)) {
                    inFlightFinalCarrierTickAdmissions.remove(admissionKey);
                    return;
                }
                FinalCarrierTickScheduler.PreparedAdmission prepared;
                try {
                    prepared = lane == TerrainAccessor.FinalLiveCarrierLane.BLOCK_TICKS
                            ? scheduler.prepareBlockLane(receipt, nowMcTick, true,
                                    carrier.sidecars().blockTicks())
                            : scheduler.prepareFluidLane(receipt, nowMcTick, true,
                                    carrier.sidecars().fluidTicks());
                } catch (Throwable failure) {
                    publishFinalCarrierTickAdmissionCompletion(
                            admissionEpoch,
                            FinalCarrierTickAdmissionCompletion.failed(
                                    admissionKey, pending, failure,
                                    FinalCarrierTickAdmissionFailure.TERMINAL_INVALID));
                    return;
                }
                try {
                    if (!lifecycleEpochCurrent(admissionEpoch)) {
                        inFlightFinalCarrierTickAdmissions.remove(admissionKey);
                        return;
                    }
                    FinalCarrierTickScheduler.Admission admission =
                            scheduler.persistAdmission(prepared);
                    publishFinalCarrierTickAdmissionCompletion(
                            admissionEpoch,
                            FinalCarrierTickAdmissionCompletion.completed(
                                    admissionKey, pending, prepared, admission));
                } catch (Throwable failure) {
                    publishFinalCarrierTickAdmissionCompletion(
                            admissionEpoch,
                            FinalCarrierTickAdmissionCompletion.failed(
                                    admissionKey, pending, failure,
                                    classifyFinalCarrierTickAdmissionFailure(failure)));
                }
            }, () -> inFlightFinalCarrierTickAdmissions.remove(admissionKey));
            return true;
        } catch (RejectedExecutionException saturated) {
            inFlightFinalCarrierTickAdmissions.remove(admissionKey);
            return false;
        }
    }

    private FinalCarrierTickAdmissionFailure classifyFinalCarrierTickAdmissionFailure(
            Throwable failure) {
        return failure instanceof com.gameexpert.engine.persistence.finalcarrier
                .FinalCarrierPersistenceService.FinalCarrierLaneConflict
                ? FinalCarrierTickAdmissionFailure.TERMINAL_REJECTION
                : failure instanceof FinalCarrierDurableStateException
                        || failure instanceof IllegalArgumentException
                        ? FinalCarrierTickAdmissionFailure.TERMINAL_INVALID
                        : FinalCarrierTickAdmissionFailure.TRANSIENT;
    }

    private void publishFinalCarrierTickAdmissionCompletion(long admissionEpoch,
            FinalCarrierTickAdmissionCompletion completion) {
        synchronized (this) {
            if (!lifecycleEpochCurrent(admissionEpoch)) {
                inFlightFinalCarrierTickAdmissions.remove(completion.key);
                return;
            }
            finalCarrierTickAdmissionCompletions.offer(completion);
        }
    }

    private boolean installFinalCarrierPayloadLane(TerrainAccessor.FinalLiveCarrierLane lane,
            NeutralFinalChunk carrier, String sourceFingerprint,
            PendingFinalCarrierClaim pending, StructureReadyPass pass) {
        FinalCarrierLaneInstaller installer = finalCarrierLaneInstaller;
        if (lane == TerrainAccessor.FinalLiveCarrierLane.LOOT) {
            if (!pass.lootCandidatesRegistered) {
                registerCanonicalLootCandidates(carrier);
                pass.lootCandidatesRegistered = true;
            }
        }
        if (installer == null) return false;
        NeutralFinalChunk.Sidecars exact = projectFinalCarrierSidecars(carrier, lane);
        if (TickSafetyTelemetry.isTickThread()
                && installer instanceof FinalCarrierDurablePayloadInstaller durable
                && pass.payloadOwnerLane != lane) {
            if (durable.supportsDurablePayload(lane, exact)) {
                return advanceDurablePayload(durable, lane, carrier, sourceFingerprint, exact,
                        pending, pass, false);
            }
            if (durable.supportsDetachedGameplayPayload(lane, exact)) {
                return advanceDurablePayload(durable, lane, carrier, sourceFingerprint, exact,
                        pending, pass, true);
            }
        }
        // 워커가 판정을 넘긴 레인은 기존처럼 이 턴에서 동기로 끝낸다. 그동안의 대기 표시는 푼다.
        if (pass.payloadOwnerLane == lane) pass.payloadAwaiting = false;
        FinalCarrierInstallResult result = java.util.Objects.requireNonNull(
                installer.installAndCommit(worldId, carrier.chunkX(), carrier.chunkZ(), lane,
                        sourceFingerprint, exact, finalCarrierGameplayInstaller()),
                "final-carrier install result");
        return result == FinalCarrierInstallResult.COMMITTED
                || result == FinalCarrierInstallResult.ALREADY_COMMITTED
                || result == FinalCarrierInstallResult.REJECTED;
    }

    private boolean advanceDurablePayload(FinalCarrierDurablePayloadInstaller installer,
            TerrainAccessor.FinalLiveCarrierLane lane,
            NeutralFinalChunk carrier, String sourceFingerprint,
            NeutralFinalChunk.Sidecars exact, PendingFinalCarrierClaim pending,
            StructureReadyPass pass, boolean detachedGameplay) {
        pass.payloadAwaiting = true;
        if (pass.payloadInvalid) return false;
        PayloadWork work = pass.payloadWork;
        if (work != null) {
            if (!work.ready) return false;
            // 이 pass가 결과의 유일한 소비자다. 먼저 비워서 다른 재시도 경로가 같은 결과를 두 번
            // 받거나(스포너 이중 설치) 받은 결과를 두고 새 쓰기를 내지 않게 한다.
            pass.payloadWork = null;
            if (!lifecycleEpochCurrent(work.epoch) || work.pending != pending
                    || pending.generation != activeChunkGeneration(
                            chunkKey(carrier.chunkX(), carrier.chunkZ()))) return false;
            long queueNanos = work.startedNanos == 0 ? 0
                    : work.startedNanos - work.submittedNanos;
            long executionNanos = work.startedNanos == 0 ? 0
                    : work.completedNanos - work.startedNanos;
            long publicationNanos = System.nanoTime() - work.completedNanos;
            if (Math.max(queueNanos, Math.max(executionNanos, publicationNanos)) > 50_000_000L) {
                log.warn("정본 페이로드 처리 지연: world={} chunk={},{} lane={} 대기={}ms DB={}ms 반영={}ms",
                        worldId, carrier.chunkX(), carrier.chunkZ(), lane,
                        queueNanos / 1_000_000L, executionNanos / 1_000_000L,
                        publicationNanos / 1_000_000L);
            }
            if (work.failure instanceof com.gameexpert.engine.persistence.finalcarrier
                    .FinalCarrierPersistenceService.OwnerActivationRequired) {
                // 스냅샷으로 증명할 수 없는 판정(이미 설치됨 불일치·충돌·구조 거절)은 트랜잭션째
                // 되돌렸다. 결정은 기존 동기 경로가 owner 상태로 내린다.
                pass.payloadOwnerLane = lane;
                return false;
            }
            if (detachedGameplay && work.failure != null
                    && !(work.failure instanceof com.gameexpert.engine.persistence.finalcarrier
                            .FinalCarrierPersistenceService.FinalCarrierLaneRetryable)) {
                // 상주 레인은 동기 설치일 때 예외가 나면 기록만 남기고 다음 레인으로 넘어갔다. 워커가
                // 못 끝낸 쓰기를 무한 재시도하거나 레인을 붙잡지 않도록, 결정을 그 동기 경로에 돌려준다.
                log.warn("Final-carrier {} detached payload failed for chunk {},{}; owner decides: {}",
                        lane, carrier.chunkX(), carrier.chunkZ(), work.failure.toString());
                pass.payloadOwnerLane = lane;
                return false;
            }
            if (work.failure != null) {
                if (work.failure instanceof IllegalArgumentException
                        || work.failure instanceof FinalCarrierDurableStateException
                        || work.failure instanceof com.gameexpert.engine.persistence.finalcarrier
                                .FinalCarrierPersistenceService.FinalCarrierLaneConflict
                        || work.failure instanceof Error) {
                    pass.payloadInvalid = true;
                    log.error("Final-carrier {} payload remains unacknowledged for chunk {},{}",
                            lane, carrier.chunkX(), carrier.chunkZ(), work.failure);
                    return false;
                }
                log.warn("Final-carrier {} payload deferred for chunk {},{}: {}",
                        lane, carrier.chunkX(), carrier.chunkZ(), work.failure.getMessage());
            } else if (work.result == FinalCarrierInstallResult.COMMITTED
                    || work.result == FinalCarrierInstallResult.ALREADY_COMMITTED
                    || work.result == FinalCarrierInstallResult.REJECTED) {
                if (detachedGameplay && work.result != FinalCarrierInstallResult.REJECTED
                        && !applyApprovedActivations(work.verdict, carrier.chunkX(),
                                carrier.chunkZ())) {
                    pass.payloadOwnerLane = lane;
                    return false;
                }
                pass.payloadAwaiting = false;
                pass.payloadRetryAttempt = 0;
                pass.payloadRetryAfter = 0;
                return true;
            }
            int attempt = Math.min(6, ++pass.payloadRetryAttempt);
            long delay = 1L << Math.min(5, attempt - 1);
            long now = clock.gameTimeMcTicks();
            pass.payloadRetryAfter = now > Long.MAX_VALUE - delay ? Long.MAX_VALUE : now + delay;
            return false;
        }
        boolean dedicated = detachedGameplay || gameplayPayloadRows(lane, exact);
        ThreadPoolExecutor workers = dedicated
                ? FINAL_CARRIER_GAMEPLAY_PAYLOAD_WORKERS : FINAL_CARRIER_PAYLOAD_WORKERS;
        java.util.concurrent.atomic.AtomicInteger inFlight = dedicated
                ? finalCarrierGameplayPayloadWorkCount : finalCarrierPayloadWorkCount;
        if (clock.gameTimeMcTicks() < pass.payloadRetryAfter || inFlight.get() >= 8) return false;
        PayloadWork submitted = new PayloadWork(pending, lifecycleEpoch);
        // 상주 판정은 owner 상태의 스냅샷으로만 한다. 워커는 상주 맵을 읽지 않는다.
        DetachedGameplayVerdict verdict = detachedGameplay
                ? snapshotGameplayVerdict(carrier.chunkX(), carrier.chunkZ(), Set.of(lane))
                : null;
        submitted.verdict = verdict;
        pass.payloadWork = submitted;
        inFlight.incrementAndGet();
        Runnable discarded = () -> {
            submitted.result = FinalCarrierInstallResult.RETRY;
            submitted.completedNanos = System.nanoTime();
            submitted.ready = true;
            inFlight.decrementAndGet();
        };
        try {
            executeOwned(workers, () -> {
                try {
                    if (!lifecycleEpochCurrent(submitted.epoch)) {
                        submitted.result = FinalCarrierInstallResult.RETRY;
                        return;
                    }
                    submitted.startedNanos = System.nanoTime();
                    submitted.result = java.util.Objects.requireNonNull(verdict == null
                            ? installer.installDurablePayloadAndCommit(worldId,
                                    carrier.chunkX(), carrier.chunkZ(), lane, sourceFingerprint, exact)
                            : installer.installDetachedGameplayAndCommit(worldId,
                                    carrier.chunkX(), carrier.chunkZ(), lane, sourceFingerprint, exact,
                                    verdict),
                            "durable payload result");
                } catch (Throwable failure) {
                    submitted.failure = failure;
                } finally {
                    submitted.completedNanos = System.nanoTime();
                    submitted.ready = true;
                    inFlight.decrementAndGet();
                }
            }, discarded);
        } catch (RejectedExecutionException full) {
            pass.payloadWork = null;
            inFlight.decrementAndGet();
        }
        return false;
    }

    private static boolean gameplayPayloadRows(TerrainAccessor.FinalLiveCarrierLane lane,
            NeutralFinalChunk.Sidecars exact) {
        return lane == TerrainAccessor.FinalLiveCarrierLane.ENTITIES && !exact.entities().isEmpty()
                || lane == TerrainAccessor.FinalLiveCarrierLane.SPAWNERS
                        && !exact.spawners().isEmpty();
    }

    /** Lanes whose resident activation a detached chunk replay may decide from an owner snapshot. */
    private static final Set<TerrainAccessor.FinalLiveCarrierLane> DETACHED_RECOVERY_LANES = Set.of(
            TerrainAccessor.FinalLiveCarrierLane.SPAWNERS,
            TerrainAccessor.FinalLiveCarrierLane.ARCHAEOLOGY,
            TerrainAccessor.FinalLiveCarrierLane.BLOCK_ENTITIES);

    /** One resident activation a detached verdict approved, replayed verbatim by the owner. */
    private record ApprovedActivation(String installationIdentity,
            TerrainAccessor.FinalLiveCarrierLane lane, NeutralFinalChunk.Sidecars exactPayload,
            List<ArchaeologyBrushableAggregate> archaeology, List<SpawnerAggregate> spawners,
            FinalCarrierBlockEntityPlan blockEntityPlan) {
    }

    /**
     * Owner-made snapshot of one chunk's resident SPAWNERS, ARCHAEOLOGY and decorated-pot state,
     * judged on a worker. It holds every resident installation whose identity names this chunk and
     * every occupied cell in it, which is all the pure install verdicts consult for identities and
     * cells of this chunk; anything reaching outside the chunk, or a lane it was not made for, is
     * answered REJECTED, which the persistence side turns into a rollback and an owner decision.
     *
     * <p>An approved install is also applied to the snapshot, so several rows replayed in one
     * transaction are judged exactly as the owner would judge them one after another. The worker
     * owns the instance until the result is published; the owner then reads {@link #approved}.</p>
     */
    private static final class DetachedGameplayVerdict implements FinalCarrierGameplayInstaller {
        private final int chunkX;
        private final int chunkZ;
        private final String identityPrefix;
        private final Set<TerrainAccessor.FinalLiveCarrierLane> lanes;
        private final Map<String, SpawnerAggregate> spawnerInstallations = new HashMap<>();
        private final Map<BlockPos, SpawnerAggregate.PlannedSpawner> spawnerCells = new HashMap<>();
        private final Map<BlockPos, ArchaeologyBrushableAggregate> archaeologyByPosition =
                new HashMap<>();
        private final Map<String, ArchaeologyBrushableAggregate> archaeologyByIdentity =
                new HashMap<>();
        private final Map<String, List<GeneratedDecoratedPotRuntime>> potInstallations =
                new HashMap<>();
        private final Map<BlockPos, GeneratedDecoratedPotRuntime> potCells = new HashMap<>();
        private final List<ApprovedActivation> approved = new ArrayList<>();

        private DetachedGameplayVerdict(long worldId, int chunkX, int chunkZ,
                Set<TerrainAccessor.FinalLiveCarrierLane> lanes) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.identityPrefix = worldId + ":" + chunkX + ":" + chunkZ + ":";
            this.lanes = Set.copyOf(lanes);
        }

        private boolean inChunk(int x, int z) {
            return Math.floorDiv(x, Blocks.CHUNK_X) == chunkX
                    && Math.floorDiv(z, Blocks.CHUNK_Z) == chunkZ;
        }

        private static boolean succeeded(FinalCarrierInstallResult result) {
            return result == FinalCarrierInstallResult.COMMITTED
                    || result == FinalCarrierInstallResult.ALREADY_COMMITTED;
        }

        @Override
        public FinalCarrierInstallResult install(String installationIdentity,
                TerrainAccessor.FinalLiveCarrierLane lane, NeutralFinalChunk.Sidecars exactPayload,
                List<ArchaeologyBrushableAggregate> archaeologyBrushables) {
            return installPrepared(installationIdentity, lane, exactPayload, archaeologyBrushables,
                    List.of());
        }

        @Override
        public FinalCarrierInstallResult installPrepared(String installationIdentity,
                TerrainAccessor.FinalLiveCarrierLane lane, NeutralFinalChunk.Sidecars exactPayload,
                List<ArchaeologyBrushableAggregate> archaeologyBrushables,
                List<SpawnerAggregate> spawnerAggregates) {
            if (!lanes.contains(lane) || !installationIdentity.startsWith(identityPrefix)) {
                return FinalCarrierInstallResult.REJECTED;
            }
            FinalCarrierInstallResult result;
            if (lane == TerrainAccessor.FinalLiveCarrierLane.SPAWNERS) {
                for (SpawnerAggregate aggregate : spawnerAggregates) {
                    for (SpawnerAggregate.PlannedSpawner planned : aggregate.plannedSpawners()) {
                        if (!inChunk(planned.x(), planned.z())) {
                            return FinalCarrierInstallResult.REJECTED;
                        }
                    }
                }
                result = spawnerInstallVerdict(installationIdentity, exactPayload,
                        archaeologyBrushables, spawnerAggregates, spawnerInstallations::get,
                        spawnerCells::get);
                if (result == FinalCarrierInstallResult.COMMITTED) {
                    SpawnerAggregate candidate = spawnerAggregates.getFirst();
                    for (SpawnerAggregate.PlannedSpawner planned : candidate.plannedSpawners()) {
                        spawnerCells.put(new BlockPos(planned.x(), planned.y(), planned.z()),
                                planned);
                    }
                    spawnerInstallations.put(installationIdentity, candidate);
                }
            } else if (lane == TerrainAccessor.FinalLiveCarrierLane.ARCHAEOLOGY
                    && spawnerAggregates.isEmpty()) {
                for (ArchaeologyBrushableAggregate candidate : archaeologyBrushables) {
                    if (!inChunk(candidate.target().x(), candidate.target().z())) {
                        return FinalCarrierInstallResult.REJECTED;
                    }
                }
                result = archaeologyInstallVerdict(installationIdentity, exactPayload,
                        archaeologyBrushables, archaeologyByPosition::get,
                        archaeologyByIdentity::get);
                if (result == FinalCarrierInstallResult.COMMITTED) {
                    for (ArchaeologyBrushableAggregate candidate : archaeologyBrushables) {
                        BlockPos position = new BlockPos(candidate.target().x(),
                                candidate.target().y(), candidate.target().z());
                        if (archaeologyByPosition.putIfAbsent(position, candidate) == null) {
                            archaeologyByIdentity.put(candidate.installationIdentity(), candidate);
                        }
                    }
                }
            } else {
                return FinalCarrierInstallResult.REJECTED;
            }
            if (succeeded(result)) {
                approved.add(new ApprovedActivation(installationIdentity, lane, exactPayload,
                        List.copyOf(archaeologyBrushables), List.copyOf(spawnerAggregates), null));
            }
            return result;
        }

        @Override
        public FinalCarrierInstallResult installBlockEntityPlan(FinalCarrierBlockEntityPlan plan) {
            if (!lanes.contains(TerrainAccessor.FinalLiveCarrierLane.BLOCK_ENTITIES)
                    || !plan.installationIdentity().startsWith(identityPrefix)) {
                return FinalCarrierInstallResult.REJECTED;
            }
            List<GeneratedDecoratedPotRuntime> candidate = decoratedPotCandidates(plan);
            if (candidate == null) return FinalCarrierInstallResult.REJECTED;
            for (GeneratedDecoratedPotRuntime value : candidate) {
                if (!inChunk(value.x(), value.z())) return FinalCarrierInstallResult.REJECTED;
            }
            FinalCarrierInstallResult result = decoratedPotInstallVerdict(
                    plan.installationIdentity(), candidate, potInstallations::get, potCells::get);
            if (result == FinalCarrierInstallResult.COMMITTED) {
                for (GeneratedDecoratedPotRuntime value : candidate) {
                    potCells.put(value.position(), value);
                }
                potInstallations.put(plan.installationIdentity(), List.copyOf(candidate));
            }
            if (succeeded(result)) {
                approved.add(new ApprovedActivation(plan.installationIdentity(),
                        TerrainAccessor.FinalLiveCarrierLane.BLOCK_ENTITIES, null, List.of(),
                        List.of(), plan));
            }
            return result;
        }
    }

    /** Copies this chunk's resident state for the given lanes; owner thread only. */
    private DetachedGameplayVerdict snapshotGameplayVerdict(int chunkX, int chunkZ,
            Set<TerrainAccessor.FinalLiveCarrierLane> lanes) {
        DetachedGameplayVerdict verdict = new DetachedGameplayVerdict(worldId, chunkX, chunkZ, lanes);
        if (lanes.contains(TerrainAccessor.FinalLiveCarrierLane.SPAWNERS)) {
            for (var entry : finalCarrierSpawnerInstallations.entrySet()) {
                if (entry.getKey().startsWith(verdict.identityPrefix)) {
                    verdict.spawnerInstallations.put(entry.getKey(), entry.getValue());
                }
            }
            for (var entry : finalCarrierSpawnersByPosition.entrySet()) {
                if (verdict.inChunk(entry.getKey().x(), entry.getKey().z())) {
                    verdict.spawnerCells.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (lanes.contains(TerrainAccessor.FinalLiveCarrierLane.ARCHAEOLOGY)) {
            for (var entry : archaeologyBrushables.entrySet()) {
                if (verdict.inChunk(entry.getKey().x(), entry.getKey().z())) {
                    verdict.archaeologyByPosition.put(entry.getKey(), entry.getValue());
                }
            }
            for (var entry : archaeologyBrushablesByIdentity.entrySet()) {
                if (entry.getKey().startsWith(verdict.identityPrefix)) {
                    verdict.archaeologyByIdentity.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (lanes.contains(TerrainAccessor.FinalLiveCarrierLane.BLOCK_ENTITIES)) {
            for (var entry : finalCarrierDecoratedPotInstallations.entrySet()) {
                if (entry.getKey().startsWith(verdict.identityPrefix)) {
                    verdict.potInstallations.put(entry.getKey(), entry.getValue());
                }
            }
            for (var entry : finalCarrierDecoratedPotsByPosition.entrySet()) {
                if (verdict.inChunk(entry.getKey().x(), entry.getKey().z())) {
                    verdict.potCells.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return verdict;
    }

    /**
     * Owner half of a detached commit or replay: installs exactly the activations the committed
     * verdict approved, in their durable order. The verdict saw this chunk's resident state, which
     * only the owner mutates, and every other writer of it installs the same durable identities
     * (claim validation pins each identity to the chunk's canonical source, with one stored
     * mutation per lane and source), so this replay is idempotent. A disagreement means the owner
     * state moved after the snapshot; it is logged and the caller returns to the synchronous owner
     * decision, which replays the committed rows against the live state.
     */
    private boolean applyApprovedActivations(DetachedGameplayVerdict verdict, int chunkX,
            int chunkZ) {
        if (verdict == null) return true;
        FinalCarrierGameplayInstaller live = finalCarrierGameplayInstaller();
        for (ApprovedActivation activation : verdict.approved) {
            FinalCarrierInstallResult applied = activation.blockEntityPlan() != null
                    ? live.installBlockEntityPlan(activation.blockEntityPlan())
                    : live.installPrepared(activation.installationIdentity(), activation.lane(),
                            activation.exactPayload(), activation.archaeology(),
                            activation.spawners());
            if (applied != FinalCarrierInstallResult.COMMITTED
                    && applied != FinalCarrierInstallResult.ALREADY_COMMITTED) {
                log.error("Detached {} activation could not be applied to runtime for chunk {},{}: {}",
                        activation.lane(), chunkX, chunkZ, applied);
                return false;
            }
        }
        return true;
    }

    /**
     * Builds one exact live-carrier lane payload. LDEC is part of the authenticated LOOT/ENTS
     * source contract, so a lane projection must validate the complete source first and then
     * retain only declarations whose exact source rows survive that projection.
     */
    private static NeutralFinalChunk.Sidecars projectFinalCarrierSidecars(
            NeutralFinalChunk carrier, TerrainAccessor.FinalLiveCarrierLane lane) {
        java.util.Objects.requireNonNull(carrier, "final-live carrier");
        java.util.Objects.requireNonNull(lane, "final-live carrier lane");
        NeutralFinalChunk.Sidecars source = carrier.sidecars();
        List<NeutralFinalChunk.Loot> loot = lane
                == TerrainAccessor.FinalLiveCarrierLane.LOOT ? source.loot() : List.of();
        List<NeutralFinalChunk.StructureEntity> entities = lane
                == TerrainAccessor.FinalLiveCarrierLane.ENTITIES ? source.entities() : List.of();
        NeutralFinalChunk.Sidecars selected = new NeutralFinalChunk.Sidecars(
                lane == TerrainAccessor.FinalLiveCarrierLane.BLOCK_TICKS
                        ? source.blockTicks() : List.of(),
                lane == TerrainAccessor.FinalLiveCarrierLane.FLUID_TICKS
                        ? source.fluidTicks() : List.of(),
                loot,
                lane == TerrainAccessor.FinalLiveCarrierLane.SPAWNERS
                        ? source.spawners() : List.of(),
                lane == TerrainAccessor.FinalLiveCarrierLane.OWNERS
                        ? source.owners() : List.of(),
                lane == TerrainAccessor.FinalLiveCarrierLane.ARCHAEOLOGY
                        ? source.archaeology() : List.of(),
                lane == TerrainAccessor.FinalLiveCarrierLane.BEES
                        ? source.bees() : List.of(),
                lane == TerrainAccessor.FinalLiveCarrierLane.BLOCK_ENTITIES
                        ? source.blockEntities() : List.of(),
                entities,
                List.of());
        if (lane != TerrainAccessor.FinalLiveCarrierLane.LOOT
                && lane != TerrainAccessor.FinalLiveCarrierLane.ENTITIES) {
            // Declarations only survive with their LOOT or entity rows, and this lane keeps neither, so the
            // producer's subset projection is exactly `selected`. The carrier was verified when it was loaded;
            // skipping the round trip keeps a carrier inflate, encode and decode per lane off the world tick.
            if (!VERIFY_LOCAL_LANE_PROJECTION) return selected;
            NeutralFinalChunk.Sidecars remote = carrier.projectedSidecars(selected);
            if (!remote.equals(selected)) {
                log.error("Local {} lane projection differs from the producer for chunk {},{}",
                        lane, carrier.chunkX(), carrier.chunkZ());
            }
            return remote;
        }
        return carrier.projectedSidecars(selected);
    }

    /** QA switch: also ask the producer and log any difference from the local lane projection. */
    private static final boolean VERIFY_LOCAL_LANE_PROJECTION =
            Boolean.getBoolean("webcraft.verifyLocalLaneProjection");

    /** Rebuild residency-local eligibility even when the cached LOOT lane was already acknowledged. */
    private void restoreCanonicalLootCandidates(int chunkX, int chunkZ) {
        TerrainAccessor.SnapshotSource source = accessor.snapshotSource(chunkX, chunkZ);
        if (source == null) return;
        NeutralFinalChunk carrier = source.finalLiveCarrier().orElse(null);
        if (carrier == null) return;
        // Use current ownership after decoration/recovery; a same-kind player replacement is not canonical.
        NeutralFinalChunk visible = accessor.carrierWithVisibleSidecars(
                chunkX, chunkZ, carrier);
        // Admission and first-open recovery may already own entries. Do not erase them here;
        // block replacement and residency eviction retain their existing invalidation paths.
        registerCanonicalLootCandidates(visible);
    }

    private void registerCanonicalLootCandidates(NeutralFinalChunk carrier) {
        if (carrier == null) return;
        short[] blockIds = carrier.blockIds();
        synchronized (this) {
            for (NeutralFinalChunk.Loot loot : carrier.sidecars().loot()) {
                int packed = loot.packed();
                if (packed < 0 || packed >= blockIds.length) continue;
                int block = Short.toUnsignedInt(blockIds[packed]);
                CanonicalLootContainerKind kind = switch (block) {
                    case Blocks.CHEST -> CanonicalLootContainerKind.CHEST;
                    case Blocks.BARREL -> CanonicalLootContainerKind.BARREL;
                    case Blocks.DISPENSER -> CanonicalLootContainerKind.DISPENSER;
                    case Blocks.DECORATED_POT -> CanonicalLootContainerKind.DECORATED_POT;
                    default -> Blocks.isDecoratedPot(block)
                            ? CanonicalLootContainerKind.DECORATED_POT : null;
                };
                if (kind == null) continue;
                int localX = packed % Blocks.CHUNK_X;
                int yz = packed / Blocks.CHUNK_X;
                int localZ = yz % Blocks.CHUNK_Z;
                int y = Blocks.MIN_Y + yz / Blocks.CHUNK_Z;
                canonicalLootCandidates.put(new BlockPos(
                        carrier.chunkX() * Blocks.CHUNK_X + localX, y,
                        carrier.chunkZ() * Blocks.CHUNK_Z + localZ), kind);
            }
        }
    }

    private FinalCarrierInstallResult installFinalCarrierGameplayLane(String installationIdentity,
            TerrainAccessor.FinalLiveCarrierLane lane, NeutralFinalChunk.Sidecars exactPayload,
            List<ArchaeologyBrushableAggregate> archaeology) {
        return installFinalCarrierGameplayLane(
                installationIdentity, lane, exactPayload, archaeology, List.of());
    }

    private FinalCarrierGameplayInstaller finalCarrierGameplayInstaller() {
        return new FinalCarrierGameplayInstaller() {
            @Override
            public FinalCarrierInstallResult install(String installationIdentity,
                    TerrainAccessor.FinalLiveCarrierLane lane,
                    NeutralFinalChunk.Sidecars exactPayload,
                    List<ArchaeologyBrushableAggregate> archaeologyBrushables) {
                return installFinalCarrierGameplayLane(installationIdentity, lane, exactPayload,
                        archaeologyBrushables);
            }

            @Override
            public FinalCarrierInstallResult installPrepared(String installationIdentity,
                    TerrainAccessor.FinalLiveCarrierLane lane,
                    NeutralFinalChunk.Sidecars exactPayload,
                    List<ArchaeologyBrushableAggregate> archaeologyBrushables,
                    List<SpawnerAggregate> spawnerAggregates) {
                return installFinalCarrierGameplayLane(installationIdentity, lane, exactPayload,
                        archaeologyBrushables, spawnerAggregates);
            }

            @Override
            public FinalCarrierInstallResult installBlockEntityPlan(FinalCarrierBlockEntityPlan plan) {
                return installFinalCarrierDecoratedPots(plan);
            }
        };
    }

    private FinalCarrierInstallResult installFinalCarrierDecoratedPots(
            FinalCarrierBlockEntityPlan plan) {
        List<GeneratedDecoratedPotRuntime> candidate = decoratedPotCandidates(plan);
        if (candidate == null) return FinalCarrierInstallResult.REJECTED;
        FinalCarrierInstallResult verdict = decoratedPotInstallVerdict(plan.installationIdentity(),
                candidate, finalCarrierDecoratedPotInstallations::get,
                finalCarrierDecoratedPotsByPosition::get);
        if (verdict != FinalCarrierInstallResult.COMMITTED) return verdict;
        for (GeneratedDecoratedPotRuntime value : candidate) {
            finalCarrierDecoratedPotsByPosition.put(value.position(), value);
        }
        finalCarrierDecoratedPotInstallations.put(plan.installationIdentity(), List.copyOf(candidate));
        // The projection is installed before this chunk can be presented. Advance each affected chunk after
        // the map commit so the frame-cache identity can never reuse a pre-sidecar snapshot.
        candidate.stream().map(value -> blockChunkKey(value.x(), value.z())).distinct()
                .forEach(key -> chunkVersions.advance((int) (key >> 32), key.intValue()));
        return FinalCarrierInstallResult.COMMITTED;
    }

    /**
     * The resident projections one BENT plan asks for, or null when a face cannot resolve: every
     * face must resolve before any resident projection can be installed. Pure; any thread.
     */
    private static List<GeneratedDecoratedPotRuntime> decoratedPotCandidates(
            FinalCarrierBlockEntityPlan plan) {
        try {
            return plan.entries().stream()
                    .flatMap(entry -> entry.decoratedPot().stream().map(projection ->
                            new GeneratedDecoratedPotRuntime(plan.installationIdentity() + ":"
                                    + entry.packed(), entry.x(), entry.y(), entry.z(), projection)))
                    .toList();
        } catch (IllegalArgumentException unsupported) {
            return null;
        }
    }

    /**
     * Pure decorated-pot install decision: COMMITTED means "install now", ALREADY_COMMITTED an
     * exact resident replay, REJECTED a conflict. The owner passes its live maps; a detached commit
     * passes an owner-made snapshot of the chunk.
     */
    private static FinalCarrierInstallResult decoratedPotInstallVerdict(String installationIdentity,
            List<GeneratedDecoratedPotRuntime> candidate,
            java.util.function.Function<String, List<GeneratedDecoratedPotRuntime>> installations,
            java.util.function.Function<BlockPos, GeneratedDecoratedPotRuntime> occupied) {
        if (candidate.isEmpty()) return FinalCarrierInstallResult.ALREADY_COMMITTED;
        List<GeneratedDecoratedPotRuntime> existing = installations.apply(installationIdentity);
        if (existing != null) {
            if (!existing.equals(candidate)) return FinalCarrierInstallResult.REJECTED;
            for (GeneratedDecoratedPotRuntime value : candidate) {
                if (!value.equals(occupied.apply(value.position()))) {
                    return FinalCarrierInstallResult.REJECTED;
                }
            }
            return FinalCarrierInstallResult.ALREADY_COMMITTED;
        }
        for (GeneratedDecoratedPotRuntime value : candidate) {
            // 칸 값은 null이 될 수 없으므로 조회 결과로 점유를 판정해도 containsKey와 같다.
            if (occupied.apply(value.position()) != null) return FinalCarrierInstallResult.REJECTED;
        }
        return FinalCarrierInstallResult.COMMITTED;
    }

    /** Narrow server-only lookup for interaction and future snapshot consumers. */
    Optional<GeneratedDecoratedPotRuntime> generatedDecoratedPotAt(int x, int y, int z) {
        return Optional.ofNullable(finalCarrierDecoratedPotsByPosition.get(new BlockPos(x, y, z)));
    }

    /**
     * Immutable resident projections for one chunk, in the final-carrier packed-cell order.
     * A generated projection is never presented over an authoritative/player overlay.
     */
    synchronized List<GeneratedDecoratedPotRuntime> generatedTrialDecoratedPotsInChunk(
            int chunkX, int chunkZ) {
        return finalCarrierDecoratedPotsByPosition.values().stream()
                .filter(value -> Math.floorDiv(value.x(), Blocks.CHUNK_X) == chunkX
                        && Math.floorDiv(value.z(), Blocks.CHUNK_Z) == chunkZ)
                .filter(this::isLiveGeneratedDecoratedPot)
                .sorted(java.util.Comparator.comparingInt(this::packedBlockIndex))
                .toList();
    }

    /**
     * Atomically reserves one live generated-pot projection for the block-break owner and advances the matching
     * chunk version in that owner turn. The caller still owns the replacement overlay write.
     */
    synchronized Optional<GeneratedDecoratedPotRuntime> takeGeneratedTrialDecoratedPotAt(
            int x, int y, int z) {
        BlockPos position = new BlockPos(x, y, z);
        GeneratedDecoratedPotRuntime value = finalCarrierDecoratedPotsByPosition.get(position);
        if (value == null || !isLiveGeneratedDecoratedPot(value)) return Optional.empty();
        Map.Entry<String, List<GeneratedDecoratedPotRuntime>> installationEntry =
                finalCarrierDecoratedPotInstallations.entrySet().stream()
                        .filter(entry -> entry.getValue().contains(value))
                        .findFirst().orElse(null);
        if (installationEntry == null) return Optional.empty();
        String installationIdentity = installationEntry.getKey();
        List<GeneratedDecoratedPotRuntime> installation = installationEntry.getValue();
        finalCarrierDecoratedPotsByPosition.remove(position);
        if (installation.size() == 1) {
            finalCarrierDecoratedPotInstallations.remove(installationIdentity);
        } else {
            finalCarrierDecoratedPotInstallations.put(installationIdentity, installation.stream()
                    .filter(candidate -> !candidate.position().equals(position)).toList());
        }
        // Removing the sidecar projection is a visible block-overlay mutation boundary even before the
        // breaker writes its replacement block; do it in this owner turn so cached frames cannot survive it.
        chunkVersions.advance(Math.floorDiv(x, Blocks.CHUNK_X), Math.floorDiv(z, Blocks.CHUNK_Z));
        return Optional.of(value);
    }

    private boolean isLiveGeneratedDecoratedPot(GeneratedDecoratedPotRuntime value) {
        BlockPos position = value.position();
        Map<BlockPos, OverlayValue> overlay = authoritativeOverlay.get(
                blockChunkKey(position.x(), position.z()));
        return (overlay == null || !overlay.containsKey(position))
                && WorldTickLoop.residentBlockType(accessor, position.x(), position.y(), position.z())
                        == Blocks.DECORATED_POT;
    }

    private int packedBlockIndex(GeneratedDecoratedPotRuntime value) {
        return Blocks.blockIndex(Math.floorMod(value.x(), Blocks.CHUNK_X), value.y(),
                Math.floorMod(value.z(), Blocks.CHUNK_Z));
    }

    static final class GeneratedDecoratedPotRuntime {
        private final String installationIdentity;
        private final int x;
        private final int y;
        private final int z;
        private final FinalCarrierBlockEntityPlan.DecoratedPotProjection projection;
        private final short[] faceItemTypes;

        GeneratedDecoratedPotRuntime(String installationIdentity, int x, int y, int z,
                FinalCarrierBlockEntityPlan.DecoratedPotProjection projection) {
            this.installationIdentity = installationIdentity;
            this.x = x;
            this.y = y;
            this.z = z;
            this.projection = projection;
            this.faceItemTypes = resolveTrialPotFaces(projection);
        }

        private static short[] resolveTrialPotFaces(
                FinalCarrierBlockEntityPlan.DecoratedPotProjection projection) {
            String[] faces = {projection.back(), projection.left(), projection.right(), projection.front()};
            List<String> declaration = ChunkSnapshot.DecoratedPotMotif.declaration();
            for (String face : faces) {
                if (!declaration.contains(face)) {
                    throw new IllegalArgumentException("Trial decorated pot face is wire-unsupported");
                }
            }
            short[] resolved = new short[faces.length];
            for (int index = 0; index < faces.length; index++) {
                resolved[index] = PlayerInventory.resolveCanonicalSimpleItemType(faces[index]);
            }
            return resolved;
        }

        String installationIdentity() { return installationIdentity; }
        int x() { return x; }
        int y() { return y; }
        int z() { return z; }
        FinalCarrierBlockEntityPlan.DecoratedPotProjection projection() { return projection; }
        short[] faceItemTypes() { return faceItemTypes.clone(); }
        BlockPos position() { return new BlockPos(x, y, z); }

        @Override public boolean equals(Object other) {
            if (!(other instanceof GeneratedDecoratedPotRuntime value)) return false;
            return x == value.x && y == value.y && z == value.z
                    && java.util.Objects.equals(installationIdentity, value.installationIdentity)
                    && java.util.Objects.equals(projection, value.projection);
        }

        @Override public int hashCode() {
            int hash = java.util.Objects.hashCode(installationIdentity);
            hash = 31 * hash + Integer.hashCode(x);
            hash = 31 * hash + Integer.hashCode(y);
            hash = 31 * hash + Integer.hashCode(z);
            return 31 * hash + java.util.Objects.hashCode(projection);
        }
    }

    private FinalCarrierInstallResult installFinalCarrierGameplayLane(String installationIdentity,
            TerrainAccessor.FinalLiveCarrierLane lane, NeutralFinalChunk.Sidecars exactPayload,
            List<ArchaeologyBrushableAggregate> archaeology,
            List<SpawnerAggregate> spawners) {
        if (lane == TerrainAccessor.FinalLiveCarrierLane.SPAWNERS) {
            return installFinalCarrierSpawners(
                    installationIdentity, exactPayload, archaeology, spawners);
        }
        if (lane == TerrainAccessor.FinalLiveCarrierLane.ENTITIES) {
            return installStructureEntities(installationIdentity, exactPayload, archaeology);
        }
        if (lane != TerrainAccessor.FinalLiveCarrierLane.ARCHAEOLOGY) {
            // OWNR is settled entirely as a durable provenance receipt by persistence and must
            // never reach this gameplay boundary. The remaining payload shapes have no exact
            // authoritative aggregate consumer yet.
            return FinalCarrierInstallResult.RETRY;
        }
        FinalCarrierInstallResult verdict = archaeologyInstallVerdict(installationIdentity,
                exactPayload, archaeology, archaeologyBrushables::get,
                archaeologyBrushablesByIdentity::get);
        if (verdict != FinalCarrierInstallResult.COMMITTED) return verdict;
        boolean installed = false;
        for (ArchaeologyBrushableAggregate candidate : archaeology) {
            BlockPos position = new BlockPos(candidate.target().x(), candidate.target().y(),
                    candidate.target().z());
            if (!archaeologyBrushables.containsKey(position)) {
                archaeologyBrushables.put(position, candidate);
                archaeologyBrushablesByIdentity.put(candidate.installationIdentity(), candidate);
                installed = true;
            }
        }
        return installed ? FinalCarrierInstallResult.COMMITTED
                : FinalCarrierInstallResult.ALREADY_COMMITTED;
    }

    /**
     * Pure ARCHAEOLOGY install decision: COMMITTED means at least one brushable is not resident
     * yet, ALREADY_COMMITTED an exact resident replay, REJECTED a conflict. Only the immutable
     * installation of a resident aggregate is consulted, so a detached commit may pass an
     * owner-made snapshot of the chunk.
     */
    private static FinalCarrierInstallResult archaeologyInstallVerdict(String installationIdentity,
            NeutralFinalChunk.Sidecars exactPayload,
            List<ArchaeologyBrushableAggregate> archaeology,
            java.util.function.Function<BlockPos, ArchaeologyBrushableAggregate> byPosition,
            java.util.function.Function<String, ArchaeologyBrushableAggregate> byIdentity) {
        if (archaeology.size() != exactPayload.archaeology().size()) {
            return FinalCarrierInstallResult.REJECTED;
        }
        for (ArchaeologyBrushableAggregate candidate : archaeology) {
            int packed;
            try {
                packed = Blocks.blockIndex(Math.floorMod(candidate.target().x(), Blocks.CHUNK_X),
                        candidate.target().y(),
                        Math.floorMod(candidate.target().z(), Blocks.CHUNK_Z));
            } catch (IllegalArgumentException outsideWorld) {
                return FinalCarrierInstallResult.REJECTED;
            }
            boolean exact = candidate.installationIdentity().startsWith(
                    installationIdentity + ":")
                    && exactPayload.archaeology().stream().anyMatch(value ->
                            value.packed() == packed
                                    && value.table().equals(candidate.exactTableKey())
                                    && value.seed() == candidate.rawSeed());
            if (!exact) return FinalCarrierInstallResult.REJECTED;
        }
        boolean absent = false;
        try {
            for (ArchaeologyBrushableAggregate candidate : archaeology) {
                BlockPos position = new BlockPos(candidate.target().x(), candidate.target().y(),
                        candidate.target().z());
                ArchaeologyBrushableAggregate atPosition = byPosition.apply(position);
                if (atPosition != null) atPosition.replayInstall(installation(candidate));
                else absent = true;
                ArchaeologyBrushableAggregate withIdentity = byIdentity.apply(
                        candidate.installationIdentity());
                if (withIdentity != null) withIdentity.replayInstall(installation(candidate));
            }
        } catch (IllegalStateException conflict) {
            return FinalCarrierInstallResult.REJECTED;
        }
        return absent ? FinalCarrierInstallResult.COMMITTED
                : FinalCarrierInstallResult.ALREADY_COMMITTED;
    }

    private FinalCarrierInstallResult installFinalCarrierSpawners(String installationIdentity,
            NeutralFinalChunk.Sidecars exactPayload,
            List<ArchaeologyBrushableAggregate> archaeology,
            List<SpawnerAggregate> spawners) {
        FinalCarrierInstallResult verdict = spawnerInstallVerdict(installationIdentity,
                exactPayload, archaeology, spawners, finalCarrierSpawnerInstallations::get,
                finalCarrierSpawnersByPosition::get);
        if (verdict != FinalCarrierInstallResult.COMMITTED) return verdict;
        SpawnerAggregate candidate = spawners.getFirst();
        for (SpawnerAggregate.PlannedSpawner planned : candidate.plannedSpawners()) {
            finalCarrierSpawnersByPosition.put(
                    new BlockPos(planned.x(), planned.y(), planned.z()), planned);
        }
        finalCarrierSpawnerInstallations.put(installationIdentity, candidate);
        for (SpawnerAggregate.PlannedSpawner planned : candidate.plannedSpawners()) {
            setBlockState(planned.x(), planned.y(), planned.z(), planned.carrierBlockId(),
                    planned.authoritativeState());
        }
        return FinalCarrierInstallResult.COMMITTED;
    }

    /**
     * Pure SPAWNERS install decision: COMMITTED means "install now", ALREADY_COMMITTED an exact
     * resident replay, REJECTED a conflict. The owner passes its live maps; a detached commit
     * passes an owner-made snapshot of the chunk.
     */
    private static FinalCarrierInstallResult spawnerInstallVerdict(String installationIdentity,
            NeutralFinalChunk.Sidecars exactPayload,
            List<ArchaeologyBrushableAggregate> archaeology,
            List<SpawnerAggregate> spawners,
            java.util.function.Function<String, SpawnerAggregate> installations,
            java.util.function.Function<BlockPos, SpawnerAggregate.PlannedSpawner> occupied) {
        if (!archaeology.isEmpty() || spawners.size() > 1) {
            return FinalCarrierInstallResult.REJECTED;
        }
        if (exactPayload.spawners().isEmpty()) {
            return spawners.isEmpty() ? FinalCarrierInstallResult.ALREADY_COMMITTED
                    : FinalCarrierInstallResult.REJECTED;
        }
        if (spawners.size() != 1) return FinalCarrierInstallResult.REJECTED;
        SpawnerAggregate candidate = spawners.getFirst();
        if (!candidate.installationIdentity().equals(installationIdentity)
                || candidate.plannedSpawners().size() != exactPayload.spawners().size()) {
            return FinalCarrierInstallResult.REJECTED;
        }
        for (int index = 0; index < exactPayload.spawners().size(); index++) {
            var source = exactPayload.spawners().get(index);
            var planned = candidate.plannedSpawners().get(index);
            if (planned.packed() != source.packed()
                    || !planned.entityKey().equals(source.entityType())) {
                return FinalCarrierInstallResult.REJECTED;
            }
        }

        SpawnerAggregate existingIdentity = installations.apply(installationIdentity);
        if (existingIdentity != null) {
            if (!existingIdentity.sameDefinition(candidate)) {
                return FinalCarrierInstallResult.REJECTED;
            }
            for (SpawnerAggregate.PlannedSpawner planned : candidate.plannedSpawners()) {
                if (!planned.equals(occupied.apply(
                        new BlockPos(planned.x(), planned.y(), planned.z())))) {
                    return FinalCarrierInstallResult.REJECTED;
                }
            }
            return FinalCarrierInstallResult.ALREADY_COMMITTED;
        }
        for (SpawnerAggregate.PlannedSpawner planned : candidate.plannedSpawners()) {
            // 칸 값은 null이 될 수 없으므로 조회 결과로 점유를 판정해도 containsKey와 같다.
            if (occupied.apply(new BlockPos(planned.x(), planned.y(), planned.z())) != null) {
                return FinalCarrierInstallResult.REJECTED;
            }
        }
        return FinalCarrierInstallResult.COMMITTED;
    }

    private FinalCarrierInstallResult installStructureEntities(String installationIdentity,
            NeutralFinalChunk.Sidecars exactPayload,
            List<ArchaeologyBrushableAggregate> archaeology) {
        if (!archaeology.isEmpty()) return FinalCarrierInstallResult.REJECTED;
        if (exactPayload.entities().isEmpty()) return FinalCarrierInstallResult.ALREADY_COMMITTED;
        StructureEntityAggregate candidate;
        try {
            candidate = StructureEntityAggregate.prepare(
                    new StructureEntityAggregate.Installation(
                            installationIdentity, exactPayload.entities()));
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            return FinalCarrierInstallResult.REJECTED;
        }
        StructureEntityAggregate existing = structureEntityInstallations.get(
                installationIdentity);
        if (existing != null) {
            if (!existing.sourceFingerprint().equals(candidate.sourceFingerprint())
                    || existing.plannedEntities().size() != candidate.plannedEntities().size()) {
                return FinalCarrierInstallResult.REJECTED;
            }
            for (StructureEntityAggregate.PlannedEntity planned : candidate.plannedEntities()) {
                StructureEntityAggregate.PlannedEntity installed =
                        structureEntitiesById.get(planned.authoritativeEntityId());
                if (installed == null
                        || !installed.rowFingerprint().equals(planned.rowFingerprint())) {
                    return FinalCarrierInstallResult.REJECTED;
                }
            }
            return FinalCarrierInstallResult.ALREADY_COMMITTED;
        }
        for (StructureEntityAggregate.PlannedEntity planned : candidate.plannedEntities()) {
            StructureEntityAggregate.PlannedEntity installed =
                    structureEntitiesById.get(planned.authoritativeEntityId());
            if (installed != null) return FinalCarrierInstallResult.REJECTED;
        }
        candidate.commit(candidate.expectedReceipts());
        for (StructureEntityAggregate.PlannedEntity planned : candidate.plannedEntities()) {
            structureEntitiesById.put(planned.authoritativeEntityId(), planned);
        }
        structureEntityInstallations.put(installationIdentity, candidate);
        return FinalCarrierInstallResult.COMMITTED;
    }

    StructureEntityAggregate.PlannedEntity structureEntityById(long authoritativeEntityId) {
        return structureEntitiesById.get(authoritativeEntityId);
    }

    private FinalCarrierInstallResult installCommittedStructureEntities(
            com.gameexpert.engine.persistence.finalcarrier.FinalCarrierPersistenceService
                    .CommittedEntityActivation activation) {
        if (activation.worldId() != worldId) return FinalCarrierInstallResult.REJECTED;
        StructureEntityAggregate candidate = activation.aggregate();
        if (!candidate.installed()
                || !candidate.installationIdentity().equals(activation.installationIdentity())) {
            return FinalCarrierInstallResult.REJECTED;
        }
        StructureEntityAggregate existing = structureEntityInstallations.get(
                activation.installationIdentity());
        if (existing != null) {
            if (!existing.sourceFingerprint().equals(candidate.sourceFingerprint())
                    || existing.plannedEntities().size() != candidate.plannedEntities().size()) {
                return FinalCarrierInstallResult.REJECTED;
            }
            for (StructureEntityAggregate.PlannedEntity planned : candidate.plannedEntities()) {
                StructureEntityAggregate.PlannedEntity installed =
                        structureEntitiesById.get(planned.authoritativeEntityId());
                if (installed == null
                        || !installed.rowFingerprint().equals(planned.rowFingerprint())) {
                    return FinalCarrierInstallResult.REJECTED;
                }
            }
        } else {
            for (StructureEntityAggregate.PlannedEntity planned : candidate.plannedEntities()) {
                if (structureEntitiesById.containsKey(planned.authoritativeEntityId())) {
                    return FinalCarrierInstallResult.REJECTED;
                }
            }
        }

        LinkedHashMap<Long, MobPersistenceSnapshot> snapshotsById = new LinkedHashMap<>();
        for (MobPersistenceSnapshot snapshot : activation.snapshots()) {
            if (snapshotsById.putIfAbsent(snapshot.getMobId(), snapshot) != null) {
                return FinalCarrierInstallResult.REJECTED;
            }
        }
        ArrayList<MobPersistenceSnapshot> installableSnapshots = new ArrayList<>();
        LinkedHashMap<Long, WorldGeneratedStructureEntityState.RuntimeSnapshot>
                generatedSnapshotsById = new LinkedHashMap<>();
        for (WorldGeneratedStructureEntityState.RuntimeSnapshot snapshot
                : activation.generatedSnapshots()) {
            if (generatedSnapshotsById.putIfAbsent(
                    snapshot.binding().entityId(), snapshot) != null) {
                return FinalCarrierInstallResult.REJECTED;
            }
        }
        long highestEntityId = 0L;
        for (StructureEntityAggregate.PlannedEntity planned : candidate.plannedEntities()) {
            long entityId = planned.authoritativeEntityId();
            highestEntityId = Math.max(highestEntityId, entityId);
            MobPersistenceSnapshot snapshot = snapshotsById.remove(entityId);
            WorldGeneratedStructureEntityState.RuntimeSnapshot generated =
                    generatedSnapshotsById.remove(entityId);
            java.util.Optional<com.gameexpert.engine.persistence.finalcarrier.structureentity
                    .GeneratedStructureEntityFacts.Kind> expectedGenerated;
            try {
                expectedGenerated = WorldGeneratedStructureEntityState
                        .classifyPlannedEntity(planned);
            } catch (IllegalArgumentException | IllegalStateException invalidGenerated) {
                return FinalCarrierInstallResult.REJECTED;
            }
            Mob live = mobSystem.mobForBoat(entityId);
            if (expectedGenerated.isPresent()) {
                if (snapshot != null || generated == null
                        || generated.binding().kind() != expectedGenerated.get()
                        || live != null) {
                    return FinalCarrierInstallResult.REJECTED;
                }
                continue;
            }
            if (generated != null) return FinalCarrierInstallResult.REJECTED;
            if (pendingStructureEntityDeaths.contains(entityId)
                    || durableDeadStructureEntityIds.contains(entityId)) continue;
            if (snapshot == null) {
                if (live != null) return FinalCarrierInstallResult.REJECTED;
                continue;
            }
            if (live != null) {
                MobPersistenceSnapshot current = mobSystem.persistenceSnapshot(entityId);
                if (!MobRuntime.sameFinalCarrierBinding(snapshot, current)) {
                    return FinalCarrierInstallResult.REJECTED;
                }
                // This immutable installation and row fingerprint already prove ownership above.
                // Keep the newer live mutable state instead of replaying an asynchronously persisted
                // position/health/AI checkpoint over it.
                continue;
            }
            installableSnapshots.add(snapshot);
        }
        if (!snapshotsById.isEmpty() || !generatedSnapshotsById.isEmpty()
                || highestEntityId == Long.MAX_VALUE) {
            return FinalCarrierInstallResult.REJECTED;
        }

        // Validate the complete mutable durable batch away from every published runtime index.
        MobRuntime detached = new MobRuntime(players -> List.of(), seed);
        detached.installCommittedStructureEntities(installableSnapshots);

        // The shared live allocator must cross every durable generic or mob ID before any entity,
        // including a generated Cushion, enters a published runtime index.  Reserving a durable
        // high-water is conservative and cannot expose gameplay state if the following install
        // rejects.
        if (highestEntityId > finalCarrierReservedEntityHighWater) {
            mobSystem.reserveMobIdThrough(highestEntityId);
            finalCarrierReservedEntityHighWater = highestEntityId;
        }

        GeneratedStructureEntitySystem.InstallDelta generatedDelta;
        try {
            generatedDelta = generatedStructureEntities.install(activation.generatedSnapshots());
        } catch (IllegalArgumentException | IllegalStateException conflict) {
            return FinalCarrierInstallResult.REJECTED;
        }

        // Every operation which can reject has completed. Mob installation is now an idempotent
        // application of the preflighted batch; fact-map writes cannot throw or partially validate.
        mobSystem.installCommittedStructureEntities(installableSnapshots);
        reconcileGeneratedEntityVisibility(activeSimulationChunks, generatedDelta.updated());
        if (existing == null) {
            for (StructureEntityAggregate.PlannedEntity planned : candidate.plannedEntities()) {
                structureEntitiesById.put(planned.authoritativeEntityId(), planned);
            }
            structureEntityInstallations.put(activation.installationIdentity(), candidate);
            return FinalCarrierInstallResult.COMMITTED;
        }
        return FinalCarrierInstallResult.ALREADY_COMMITTED;
    }

    private void reconcileGeneratedEntityVisibility(Set<Long> chunks,
            List<GeneratedEntitySnapshot> updateCandidates) {
        if (!generatedCushionOperationMayProceed()) return;
        observeGeneratedFinalSceneChunkResidency(chunks);
        List<WorldGeneratedStructureEntityState.RuntimeSnapshot> visible =
                generatedStructureEntities.activeRuntimeSnapshotsInChunks(chunks);
        Set<Long> previousVisibleIds = visibleGeneratedEntityIds;
        LinkedHashMap<Long, GeneratedEntitySnapshot> visibleById = new LinkedHashMap<>();
        for (WorldGeneratedStructureEntityState.RuntimeSnapshot snapshot : visible) {
            if (!generatedCushionOperationMayProceed()) return;
            visibleById.put(snapshot.binding().entityId(),
                    GeneratedStructureEntitySystem.toWire(snapshot));
        }
        ArrayList<GeneratedEntitySnapshot> spawned = new ArrayList<>();
        for (Map.Entry<Long, GeneratedEntitySnapshot> entry : visibleById.entrySet()) {
            if (!generatedCushionOperationMayProceed()) return;
            if (!previousVisibleIds.contains(entry.getKey())) spawned.add(entry.getValue());
        }
        ArrayList<GeneratedEntitySnapshot> updated = new ArrayList<>();
        for (GeneratedEntitySnapshot candidate : updateCandidates) {
            if (!generatedCushionOperationMayProceed()) return;
            long id = generatedEntityWireId(candidate);
            if (previousVisibleIds.contains(id) && visibleById.containsKey(id)) {
                updated.add(candidate);
            }
        }
        ArrayList<GeneratedEntityTarget> removed = new ArrayList<>();
        for (long id : previousVisibleIds.stream().sorted().toList()) {
            if (!generatedCushionOperationMayProceed()) return;
            if (!visibleById.containsKey(id)) {
                removed.add(generatedStructureEntities.targetByEntityId(id));
            }
        }
        boolean changed = !spawned.isEmpty() || !updated.isEmpty() || !removed.isEmpty();
        if (!changed) {
            visibleGeneratedEntityIds = Set.copyOf(visibleById.keySet());
            return;
        }
        long sequence = nextGeneratedEntitySequence(generatedEntityWelcomeView.sequence());
        if (!generatedCushionOperationMayProceed()) return;
        visibleGeneratedEntityIds = Set.copyOf(visibleById.keySet());
        generatedEntityWelcomeView = new GeneratedEntityWelcomeView(
                List.copyOf(visibleById.values()), sequence);
        if (!started) return;
        if (!spawned.isEmpty()) {
            pendingGeneratedEntityMessages.addLast(new PendingGeneratedEntityMessage(
                    new GeneratedEntitySpawn(spawned), sequence));
        }
        if (!updated.isEmpty()) {
            pendingGeneratedEntityMessages.addLast(new PendingGeneratedEntityMessage(
                    new GeneratedEntityUpdate(updated), sequence));
        }
        if (!removed.isEmpty()) {
            pendingGeneratedEntityMessages.addLast(new PendingGeneratedEntityMessage(
                    new GeneratedEntityRemove(removed), sequence));
        }
    }

    private static long generatedEntityWireId(GeneratedEntitySnapshot snapshot) {
        if (snapshot instanceof ArmorStandSnapshot armor) return armor.getEntityId();
        if (snapshot instanceof GeneratedCushionSnapshot cushion) return cushion.getEntityId();
        if (snapshot instanceof ChestMinecartSnapshot minecart) return minecart.getEntityId();
        throw new IllegalStateException("unsupported generated entity wire snapshot");
    }

    GeneratedStructureEntitySystem generatedStructureEntities() {
        return generatedStructureEntities;
    }

    private record GeneratedEvidenceKey(String nickname, String kind) {}

    private static final class GeneratedEvidenceLane {
        private final GeneratedFinalSceneEvidenceTracker tracker;
        private final long entityId;
        private final int chunkX;
        private final int chunkZ;
        private long revision;
        private long connectionGeneration;
        private long evidenceGeneration;
        private final String actionNonce;
        private final long deadlineTick;
        private boolean semanticMutation;
        private boolean terminal;
        private boolean cargoMutation;
        private boolean cargoClosed;
        private boolean sawUnload;
        private boolean unloadReload;
        private boolean reconnect;
        private com.gameexpert.ws.dto.WsMessages.FinalSceneGeneratedPrerequisiteReceipt
                frozenReceipt;
        /** 0=available, 1=reserved, 2=committed, 3=disposed/rejected. */
        private final java.util.concurrent.atomic.AtomicInteger h12fLedgerState =
                new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicInteger h12gLedgerState =
                new java.util.concurrent.atomic.AtomicInteger();
        /** Exact unforgeable identity of the sole state-1 reservation. */
        private final AtomicReference<Object> h12gLedgerReservation = new AtomicReference<>();
        private WorldGeneratedStructureEntityState.MinecartRuntimeSnapshot h12gProjection;
        private List<com.gameexpert.ws.dto.WsMessages.InventorySlot> h12gCommittedSlots;
        private com.gameexpert.ws.dto.WsMessages.CraftingStack h12gCommittedCursor;

        private GeneratedEvidenceLane(GeneratedFinalSceneEvidenceTracker tracker,
                WorldGeneratedStructureEntityState.RuntimeSnapshot source,
                long connectionGeneration, long evidenceGeneration, String actionNonce,
                long deadlineTick) {
            this.tracker = tracker;
            entityId = source.binding().entityId();
            chunkX = source.binding().chunkX();
            chunkZ = source.binding().chunkZ();
            revision = source.revision();
            this.connectionGeneration = connectionGeneration;
            this.evidenceGeneration = evidenceGeneration;
            this.actionNonce = java.util.Objects.requireNonNull(actionNonce);
            this.deadlineTick = deadlineTick;
        }
    }

    private GeneratedEvidenceLane beginGeneratedFinalSceneEvidence(String nickname,
            WorldGeneratedStructureEntityState.RuntimeSnapshot source, String actionNonce,
            long deadlineTick) {
        if (nickname == null || source == null
                || actionNonce == null || actionNonce.isBlank() || tickNo >= deadlineTick
                || source.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE
                || !(source instanceof WorldGeneratedStructureEntityState.ArmorStandRuntimeSnapshot
                        || source instanceof WorldGeneratedStructureEntityState.MinecartRuntimeSnapshot)) {
            return null;
        }
        String kind = source.binding().kind().name();
        GeneratedEvidenceKey key = new GeneratedEvidenceKey(nickname, kind);
        GeneratedEvidenceLane existing = generatedFinalSceneEvidence.get(key);
        if (existing != null) {
            return existing.entityId == source.binding().entityId()
                    && existing.revision == source.revision()
                    && existing.deadlineTick == deadlineTick
                    && existing.actionNonce.equals(actionNonce) ? existing : null;
        }
        long connectionGeneration = generatedEvidenceConnectionGenerations.getOrDefault(
                nickname, 0L);
        if (connectionGeneration <= 0L) return null;
        long evidenceGeneration = 1L;
        var binding = source.binding();
        StructureEntityAggregate.PlannedEntity planned =
                structureEntitiesById.get(binding.entityId());
        if (planned == null || planned.encounterOrdinal() != binding.encounterOrdinal()
                || !planned.rowFingerprint().equals(binding.sourceRowFingerprint())) return null;
        String sourceKind = source instanceof WorldGeneratedStructureEntityState
                .MinecartRuntimeSnapshot ? "CHEST_MINECART" : "STRUCTURE_ENTITY";
        String authoritativeId = "final-carrier-ents:" + binding.installationIdentity() + ":"
                + sourceKind + ":" + binding.encounterOrdinal();
        String provenance = binding.sourceRowFingerprint();
        try {
            var natural = new com.gameexpert.ws.dto.WsMessages.FinalSceneNaturalEntityBinding(
                    new com.gameexpert.ws.dto.WsMessages.GeneratedEntityTarget(
                            1, kind, binding.entityId()), binding.worldId(), seed, worldEpoch,
                    binding.installationIdentity(), authoritativeId, binding.encounterOrdinal(),
                    binding.installationSourceFingerprint(), provenance,
                    generatedEntityStateFingerprint(source), source.revision(), binding.chunkX(),
                    binding.chunkZ(), planned.x(), planned.y(), planned.z(),
                    connectionGeneration, evidenceGeneration);
            GeneratedEvidenceLane lane = new GeneratedEvidenceLane(
                    GeneratedFinalSceneEvidenceTracker.begin(natural), source,
                    connectionGeneration, evidenceGeneration, actionNonce, deadlineTick);
            generatedFinalSceneEvidence.put(key, lane);
            lane.tracker.recordVisible(generatedEvidenceStamp(lane, source,
                    ++lane.evidenceGeneration));
            lane.tracker.recordInteractionCommitted(generatedEvidenceStamp(lane, source,
                    ++lane.evidenceGeneration));
            return lane;
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            generatedFinalSceneEvidence.remove(key);
            return null;
        }
    }

    void recordGeneratedArmorStandMutation(String nickname,
            WorldGeneratedStructureEntityState.ArmorStandRuntimeSnapshot source,
            WorldGeneratedStructureEntityState.ArmorStandRuntimeSnapshot committed,
            String actionNonce, long deadlineTick) {
        if (committed == null || generatedStructureEntities.snapshotByEntityId(
                committed.binding().entityId()) != committed) {
            generatedFinalSceneEvidence.remove(
                    new GeneratedEvidenceKey(nickname, "ARMOR_STAND"));
            return;
        }
        GeneratedEvidenceLane lane = beginGeneratedFinalSceneEvidence(
                nickname, source, actionNonce, deadlineTick);
        if (lane == null || lane.semanticMutation) return;
        try {
            lane.tracker.recordH12fSemanticMutationCommitted(generatedEvidenceStamp(
                    lane, committed, ++lane.evidenceGeneration));
            lane.revision = committed.revision();
            lane.semanticMutation = true;
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            generatedFinalSceneEvidence.remove(new GeneratedEvidenceKey(nickname, "ARMOR_STAND"));
        }
    }

    void recordGeneratedArmorStandTerminal(String nickname,
            WorldGeneratedStructureEntityState.RuntimeSnapshot committed,
            String actionNonce, long deadlineTick) {
        GeneratedEvidenceKey key = new GeneratedEvidenceKey(nickname, "ARMOR_STAND");
        GeneratedEvidenceLane lane = generatedFinalSceneEvidence.get(key);
        if (committed == null || generatedStructureEntities.snapshotByEntityId(
                committed.binding().entityId()) != committed) {
            generatedFinalSceneEvidence.remove(key);
            return;
        }
        if (lane == null || lane.terminal || tickNo >= deadlineTick
                || lane.deadlineTick != deadlineTick || !lane.actionNonce.equals(actionNonce)) {
            generatedFinalSceneEvidence.remove(key);
            return;
        }
        try {
            lane.tracker.recordH12fTerminalCommitted(generatedEvidenceStamp(
                    lane, committed, ++lane.evidenceGeneration));
            lane.revision = committed.revision();
            lane.terminal = true;
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            generatedFinalSceneEvidence.remove(key);
        }
    }

    void recordGeneratedMinecartOpen(String nickname,
            WorldGeneratedStructureEntityState.MinecartRuntimeSnapshot current,
            com.gameexpert.ws.dto.WsMessages.MinecartCargoTarget target,
            String actionNonce, long deadlineTick) {
        if (current == null || generatedStructureEntities.snapshotByEntityId(
                current.binding().entityId()) != current) return;
        GeneratedEvidenceLane lane = beginGeneratedFinalSceneEvidence(
                nickname, current, actionNonce, deadlineTick);
        if (lane == null) return;
        try {
            lane.tracker.recordH12gCargoOpened(generatedEvidenceStamp(
                    lane, current, ++lane.evidenceGeneration), target);
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            generatedFinalSceneEvidence.remove(
                    new GeneratedEvidenceKey(nickname, "CHEST_MINECART"));
        }
    }

    void recordGeneratedMinecartMutation(String nickname,
            WorldGeneratedStructureEntityState.MinecartRuntimeSnapshot committed,
            com.gameexpert.ws.dto.WsMessages.MinecartCargoTarget target,
            List<com.gameexpert.ws.dto.WsMessages.InventorySlot> slots,
            com.gameexpert.ws.dto.WsMessages.CraftingStack cursor,
            String actionNonce, long deadlineTick) {
        GeneratedEvidenceKey key = new GeneratedEvidenceKey(nickname, "CHEST_MINECART");
        GeneratedEvidenceLane lane = generatedFinalSceneEvidence.get(key);
        if (committed == null || generatedStructureEntities.snapshotByEntityId(
                committed.binding().entityId()) != committed) {
            generatedFinalSceneEvidence.remove(key);
            return;
        }
        if (lane == null || lane.cargoMutation || tickNo >= deadlineTick
                || lane.deadlineTick != deadlineTick || !lane.actionNonce.equals(actionNonce)) {
            generatedFinalSceneEvidence.remove(key);
            return;
        }
        try {
            lane.tracker.recordH12gCargoMutationCommitted(generatedEvidenceStamp(
                    lane, committed, ++lane.evidenceGeneration), target, slots, cursor);
            lane.revision = committed.revision();
            lane.h12gCommittedSlots = copyGeneratedCargoSlots(slots);
            lane.h12gCommittedCursor = copyGeneratedCargoCursor(cursor);
            lane.cargoMutation = true;
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            generatedFinalSceneEvidence.remove(key);
        }
    }

    void recordGeneratedMinecartClosed(String nickname,
            WorldGeneratedStructureEntityState.RuntimeSnapshot current,
            com.gameexpert.ws.dto.WsMessages.MinecartCargoTarget target,
            String actionNonce, long deadlineTick) {
        GeneratedEvidenceKey key = new GeneratedEvidenceKey(nickname, "CHEST_MINECART");
        GeneratedEvidenceLane lane = generatedFinalSceneEvidence.get(key);
        if (current == null || generatedStructureEntities.snapshotByEntityId(
                current.binding().entityId()) != current) {
            generatedFinalSceneEvidence.remove(key);
            return;
        }
        if (lane == null || lane.cargoClosed || tickNo >= deadlineTick
                || lane.deadlineTick != deadlineTick || !lane.actionNonce.equals(actionNonce)) {
            generatedFinalSceneEvidence.remove(key);
            return;
        }
        try {
            lane.tracker.recordH12gCargoClosed(generatedEvidenceStamp(
                    lane, current, ++lane.evidenceGeneration), target);
            lane.cargoClosed = true;
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            generatedFinalSceneEvidence.remove(key);
        }
    }

    java.util.Optional<com.gameexpert.ws.dto.WsMessages.FinalSceneGeneratedPrerequisiteReceipt>
            freezeGeneratedFinalSceneReceipt(String nickname, String scenario,
                    GeneratedFinalSceneEvidenceTracker.ReceiptHeader header) {
        String kind = "H12f".equals(scenario) ? "ARMOR_STAND"
                : "H12g".equals(scenario) ? "CHEST_MINECART" : "";
        GeneratedEvidenceKey key = new GeneratedEvidenceKey(nickname, kind);
        GeneratedEvidenceLane lane = generatedFinalSceneEvidence.get(key);
        if (lane == null) return java.util.Optional.empty();
        if (tickNo >= lane.deadlineTick || header == null
                || !lane.actionNonce.equals(header.actionNonce())) {
            generatedFinalSceneEvidence.remove(key);
            return java.util.Optional.empty();
        }
        long currentConnection = generatedEvidenceConnectionGenerations.getOrDefault(
                nickname, 0L);
        if (lane.unloadReload && !lane.reconnect
                && currentConnection > lane.connectionGeneration) {
            var current = generatedStructureEntities.snapshotByEntityId(lane.entityId);
            if (current != null && current.revision() == lane.revision) {
                try {
                    lane.connectionGeneration = currentConnection;
                    lane.tracker.recordReconnectPersisted(generatedEvidenceStamp(
                            lane, current, ++lane.evidenceGeneration));
                    lane.reconnect = true;
                } catch (IllegalArgumentException | IllegalStateException invalid) {
                    generatedFinalSceneEvidence.remove(key);
                    return java.util.Optional.empty();
                }
            }
        }
        var receipt = lane.tracker.freezeReceipt(header);
        receipt.ifPresent(value -> {
            lane.frozenReceipt = value;
            if ("H12g".equals(value.getScenario())) {
                var current = generatedStructureEntities.snapshotByEntityId(lane.entityId);
                if (current instanceof WorldGeneratedStructureEntityState
                        .MinecartRuntimeSnapshot minecart
                        && minecart.lifecycle() == WorldGeneratedStructureEntityState
                                .Lifecycle.LIVE
                        && minecart.revision() == lane.revision) {
                    lane.h12gProjection = minecart;
                }
            }
        });
        return receipt;
    }

    void cancelGeneratedFinalSceneEvidence(String nickname, String scenario,
            String actionNonce) {
        String kind = "H12f".equals(scenario) ? "ARMOR_STAND"
                : "H12g".equals(scenario) ? "CHEST_MINECART" : "";
        GeneratedEvidenceKey key = new GeneratedEvidenceKey(nickname, kind);
        GeneratedEvidenceLane lane = generatedFinalSceneEvidence.get(key);
        if (lane != null && java.util.Objects.equals(lane.actionNonce, actionNonce)) {
            generatedFinalSceneEvidence.remove(key);
            lane.h12fLedgerState.compareAndSet(0, 3);
            lane.h12fLedgerState.compareAndSet(1, 3);
            lane.h12gLedgerState.compareAndSet(0, 3);
            lane.h12gLedgerState.compareAndSet(1, 3);
        }
    }

    /**
     * Reserves the one authority-internal completed H12f ledger. The HTTP outcome layer receives
     * only this capability and never the client-visible receipt or mutable tracker.
     */
    java.util.Optional<com.gameexpert.qa.FinalSceneH12fOutcomeService.PreparedLedger>
            reserveGeneratedFinalSceneH12fLedger(
                    com.gameexpert.qa.FinalSceneH12fOutcomeService.ActiveBinding active) {
        return reserveGeneratedFinalSceneH12fLedger(active, this::ownerTurnMayContinue);
    }

    private java.util.Optional<com.gameexpert.qa.FinalSceneH12fOutcomeService.PreparedLedger>
            reserveGeneratedFinalSceneH12fLedger(
                    com.gameexpert.qa.FinalSceneH12fOutcomeService.ActiveBinding active,
                    java.util.function.BooleanSupplier liveBinding) {
        if (started && Thread.currentThread() != ownerThread) return java.util.Optional.empty();
        if (!liveBindingCurrent(liveBinding) || active == null || active.worldId() != worldId
                || !java.util.Objects.equals(active.connectionId(),
                        playerConnections.get(active.nickname()))) {
            return java.util.Optional.empty();
        }
        GeneratedEvidenceKey key = new GeneratedEvidenceKey(active.nickname(), "ARMOR_STAND");
        GeneratedEvidenceLane lane = generatedFinalSceneEvidence.get(key);
        var receipt = lane == null ? null : lane.frozenReceipt;
        if (receipt == null || tickNo >= lane.deadlineTick
                || !lane.actionNonce.equals(receipt.getActionNonce())
                || !"H12f".equals(receipt.getScenario())
                || !"spring".equals(receipt.getAuthority())
                || !active.world().equals(receipt.getWorld())
                || !active.nickname().equals(receipt.getNickname())
                || receipt.getBinding().getWorldId() != worldId
                || receipt.getBinding().getTarget().getEntityId() != lane.entityId
                || receipt.getBinding().getRevision() > lane.revision
                || receipt.getBinding().getConnectionGeneration() != lane.connectionGeneration
                || receipt.getBinding().getEvidenceGeneration() != lane.evidenceGeneration
                || generatedEvidenceConnectionGenerations.getOrDefault(
                        active.nickname(), 0L) != lane.connectionGeneration
                || !lane.h12fLedgerState.compareAndSet(0, 1)) {
            return java.util.Optional.empty();
        }
        var binding = receipt.getBinding();
        var ledger = new com.gameexpert.qa.FinalSceneH12fOutcomeService.CompletedLedger(
                "game-expert.final-scene-prerequisites/v2", "spring", "H12f", worldId,
                active.world(), active.nickname(), active.connectionId(),
                lane.connectionGeneration, lane.evidenceGeneration, receipt.getActionNonce(),
                receipt.getRevision(), binding.getTarget().getKind(), lane.entityId,
                binding.getAuthoritativeId(), binding.getStateProvenanceFingerprint(),
                binding.getRevision(), lane.revision, true, true, true, true, true, true,
                binding.getEncounterOrdinal(), binding.getEncounterOrdinal());
        String connectionId = active.connectionId();
        long connectionGeneration = lane.connectionGeneration;
        return java.util.Optional.of(
                new com.gameexpert.qa.FinalSceneH12fOutcomeService.PreparedLedger() {
                    @Override public com.gameexpert.qa.FinalSceneH12fOutcomeService
                            .CompletedLedger ledger() {
                        return ledger;
                    }

                    @Override public boolean authenticate() {
                        return lane.h12fLedgerState.get() == 1
                                && liveBindingCurrent(liveBinding)
                                && java.util.Objects.equals(connectionId,
                                        playerConnections.get(active.nickname()))
                                && generatedEvidenceConnectionGenerations.getOrDefault(
                                        active.nickname(), 0L) == connectionGeneration;
                    }

                    @Override public com.gameexpert.qa.FinalSceneH12fArmorStandExecutor
                            .OperationStatus commit() {
                        if (!authenticate() || !lane.h12fLedgerState.compareAndSet(1, 2)) {
                            return com.gameexpert.qa.FinalSceneH12fArmorStandExecutor
                                    .OperationStatus.REJECTED;
                        }
                        return com.gameexpert.qa.FinalSceneH12fArmorStandExecutor
                                .OperationStatus.SUCCESS;
                    }

                    @Override public com.gameexpert.qa.FinalSceneH12fArmorStandExecutor
                            .CleanupStatus dispose() {
                        boolean current = liveBindingCurrent(liveBinding);
                        int state = lane.h12fLedgerState.get();
                        if (state == 1) lane.h12fLedgerState.compareAndSet(1, 3);
                        return current
                                ? com.gameexpert.qa.FinalSceneH12fArmorStandExecutor
                                        .CleanupStatus.SUCCESS
                                : com.gameexpert.qa.FinalSceneH12fArmorStandExecutor
                                        .CleanupStatus.REJECTED;
                    }
                });
    }

    private static boolean liveBindingCurrent(java.util.function.BooleanSupplier validator) {
        try {
            return validator != null && validator.getAsBoolean();
        } catch (RuntimeException | Error rejected) {
            return false;
        }
    }

    CompletableFuture<java.util.Optional<com.gameexpert.qa.FinalSceneH12fOutcomeService
            .PreparedLedger>> reserveFinalSceneH12fCompletedLedger(
                    com.gameexpert.qa.FinalSceneH12fOutcomeService.ActiveBinding active) {
        return reserveFinalSceneH12fCompletedLedger(active, this::ownerTurnMayContinue);
    }

    CompletableFuture<java.util.Optional<com.gameexpert.qa.FinalSceneH12fOutcomeService
            .PreparedLedger>> reserveFinalSceneH12fCompletedLedger(
                    com.gameexpert.qa.FinalSceneH12fOutcomeService.ActiveBinding active,
                    java.util.function.BooleanSupplier liveBinding) {
        CompletableFuture<java.util.Optional<com.gameexpert.qa.FinalSceneH12fOutcomeService
                .PreparedLedger>> completion = new CompletableFuture<>();
        if (!ownerTurnMayContinue() || !enqueueOwnerTask(() -> {
            if (completion.isDone() || !ownerTurnMayContinue()
                    || !liveBindingCurrent(liveBinding)) {
                completion.complete(java.util.Optional.empty());
                return;
            }
            try {
                var prepared = reserveGeneratedFinalSceneH12fLedger(active, liveBinding);
                if (!completion.complete(prepared)) {
                    prepared.ifPresent(com.gameexpert.qa.FinalSceneH12fOutcomeService
                            .PreparedLedger::dispose);
                }
            } catch (RuntimeException | Error rejected) {
                completion.complete(java.util.Optional.empty());
            }
        })) {
            completion.complete(java.util.Optional.empty());
        }
        return completion;
    }

    CompletableFuture<java.util.Optional<com.gameexpert.qa.FinalSceneH12gOutcomeService
            .PreparedLedger>> reserveFinalSceneH12gCompletedLedger(
                    com.gameexpert.qa.FinalSceneH12gOutcomeService.ActiveBinding active,
                    java.util.function.BooleanSupplier liveBinding) {
        CompletableFuture<java.util.Optional<com.gameexpert.qa.FinalSceneH12gOutcomeService
                .PreparedLedger>> completion = new CompletableFuture<>();
        return reserveFinalSceneH12gCompletedLedger(active, liveBinding, completion);
    }

    /** Package-local deterministic completion seam for cancellation-race verification. */
    CompletableFuture<java.util.Optional<com.gameexpert.qa.FinalSceneH12gOutcomeService
            .PreparedLedger>> reserveFinalSceneH12gCompletedLedger(
                    com.gameexpert.qa.FinalSceneH12gOutcomeService.ActiveBinding active,
                    java.util.function.BooleanSupplier liveBinding,
                    CompletableFuture<java.util.Optional<com.gameexpert.qa
                            .FinalSceneH12gOutcomeService.PreparedLedger>> completion) {
        java.util.Objects.requireNonNull(completion, "H12g reservation completion");
        if (!ownerTurnMayContinue() || !enqueueOwnerTask(() -> {
            if (completion.isDone() || !ownerTurnMayContinue()
                    || !liveBindingCurrent(liveBinding)) {
                completion.complete(java.util.Optional.empty());
                return;
            }
            try {
                var prepared = reserveGeneratedFinalSceneH12gLedger(active, liveBinding);
                java.util.Optional<com.gameexpert.qa.FinalSceneH12gOutcomeService.PreparedLedger>
                        exposed = prepared.map(value -> value);
                if (!completion.complete(exposed)) {
                    prepared.ifPresent(H12gPreparedLedger::releaseUndelivered);
                }
            } catch (RuntimeException | Error rejected) {
                completion.complete(java.util.Optional.empty());
            }
        })) completion.complete(java.util.Optional.empty());
        return completion;
    }

    private java.util.Optional<H12gPreparedLedger>
            reserveGeneratedFinalSceneH12gLedger(
                    com.gameexpert.qa.FinalSceneH12gOutcomeService.ActiveBinding active,
                    java.util.function.BooleanSupplier liveBinding) {
        if (started && Thread.currentThread() != ownerThread) return java.util.Optional.empty();
        if (!liveBindingCurrent(liveBinding) || active == null || active.worldId() != worldId
                || !java.util.Objects.equals(active.connectionId(),
                        playerConnections.get(active.nickname()))) return java.util.Optional.empty();
        GeneratedEvidenceLane lane = generatedFinalSceneEvidence.get(
                new GeneratedEvidenceKey(active.nickname(), "CHEST_MINECART"));
        var receipt = lane == null ? null : lane.frozenReceipt;
        if (!currentH12gProjection(lane, receipt) || tickNo >= lane.deadlineTick
                || !"spring".equals(receipt.getAuthority())
                || !active.world().equals(receipt.getWorld())
                || !active.nickname().equals(receipt.getNickname())
                || !lane.actionNonce.equals(receipt.getActionNonce())
                || receipt.getBinding().getWorldId() != worldId
                || receipt.getBinding().getConnectionGeneration() != lane.connectionGeneration
                || receipt.getBinding().getEvidenceGeneration() != lane.evidenceGeneration
                || generatedEvidenceConnectionGenerations.getOrDefault(
                        active.nickname(), 0L) != lane.connectionGeneration) {
            return java.util.Optional.empty();
        }
        Object reservationIdentity = new Object();
        if (!lane.h12gLedgerState.compareAndSet(0, 1)) {
            return java.util.Optional.empty();
        }
        if (!lane.h12gLedgerReservation.compareAndSet(null, reservationIdentity)) {
            lane.h12gLedgerState.compareAndSet(1, 0);
            return java.util.Optional.empty();
        }
        var projection = lane.h12gProjection;
        try {
            var ledger = new com.gameexpert.qa.FinalSceneH12gOutcomeService.CompletedLedger(
                    active.connectionId(), receipt,
                    List.of(new com.gameexpert.qa.FinalSceneH12gOutcomeService.DurableCandidate(
                            projection.binding().encounterOrdinal(), projection.binding().entityId(),
                            projection.binding().entityId(), "CHEST_MINECART", "LIVE",
                            projection.revision())));
            return java.util.Optional.of(new H12gPreparedLedger(lane, receipt, ledger, active,
                    liveBinding, reservationIdentity, lane.connectionGeneration));
        } catch (RuntimeException | Error rejected) {
            if (lane.h12gLedgerReservation.compareAndSet(reservationIdentity, null)) {
                lane.h12gLedgerState.compareAndSet(1, 0);
            }
            throw rejected;
        }
    }

    /** Private capability: reservation identity cannot be supplied or forged by HTTP callers. */
    private final class H12gPreparedLedger
            implements com.gameexpert.qa.FinalSceneH12gOutcomeService.PreparedLedger {
        private final GeneratedEvidenceLane lane;
        private final com.gameexpert.ws.dto.WsMessages.FinalSceneGeneratedPrerequisiteReceipt receipt;
        private final com.gameexpert.qa.FinalSceneH12gOutcomeService.CompletedLedger ledger;
        private final com.gameexpert.qa.FinalSceneH12gOutcomeService.ActiveBinding active;
        private final java.util.function.BooleanSupplier liveBinding;
        private final Object reservationIdentity;
        private final long connectionGeneration;

        private H12gPreparedLedger(GeneratedEvidenceLane lane,
                com.gameexpert.ws.dto.WsMessages.FinalSceneGeneratedPrerequisiteReceipt receipt,
                com.gameexpert.qa.FinalSceneH12gOutcomeService.CompletedLedger ledger,
                com.gameexpert.qa.FinalSceneH12gOutcomeService.ActiveBinding active,
                java.util.function.BooleanSupplier liveBinding, Object reservationIdentity,
                long connectionGeneration) {
            this.lane = lane;
            this.receipt = receipt;
            this.ledger = ledger;
            this.active = active;
            this.liveBinding = liveBinding;
            this.reservationIdentity = reservationIdentity;
            this.connectionGeneration = connectionGeneration;
        }

        @Override public com.gameexpert.qa.FinalSceneH12gOutcomeService.CompletedLedger ledger() {
            return ledger;
        }

        @Override public boolean authenticate() {
            return reservationCurrent() && liveBindingCurrent(liveBinding)
                    && java.util.Objects.equals(active.connectionId(),
                            playerConnections.get(active.nickname()))
                    && generatedEvidenceConnectionGenerations.getOrDefault(
                            active.nickname(), 0L) == connectionGeneration
                    && currentH12gProjection(lane, receipt);
        }

        @Override public boolean commit() {
            if (isOwnerThread()) return commitOnOwner();
            CompletableFuture<Boolean> completion = new CompletableFuture<>();
            AtomicInteger admission = new AtomicInteger(); // 0=pending, 1=owner, 2=cancelled
            if (!ownerTurnMayContinue() || !enqueueOwnerTask(() -> {
                if (!admission.compareAndSet(0, 1)) {
                    completion.complete(false);
                    return;
                }
                try {
                    completion.complete(commitOnOwner());
                } catch (RuntimeException | Error rejected) {
                    completion.complete(false);
                }
            })) return false;
            try {
                return completion.get(H12G_LEDGER_COMMIT_TIMEOUT_MILLIS,
                        TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeout) {
                if (admission.compareAndSet(0, 2)) return false;
                return completion.join();
            } catch (InterruptedException interrupted) {
                if (admission.compareAndSet(0, 2)) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                boolean committed = completion.join();
                Thread.currentThread().interrupt();
                return committed;
            } catch (ExecutionException | CancellationException rejected) {
                return false;
            }
        }

        private boolean commitOnOwner() {
            return isOwnerThread() && authenticate()
                    && lane.h12gLedgerState.compareAndSet(1, 2);
        }

        private boolean reservationCurrent() {
            return lane.h12gLedgerState.get() == 1
                    && lane.h12gLedgerReservation.get() == reservationIdentity;
        }

        private void releaseUndelivered() {
            if (lane.h12gLedgerReservation.compareAndSet(reservationIdentity, null)) {
                lane.h12gLedgerState.compareAndSet(1, 0);
            }
        }

        @Override public String dispose() {
            if (lane.h12gLedgerReservation.get() != reservationIdentity) return "SUCCESS";
            lane.h12gLedgerState.compareAndSet(1, 3);
            if (lane.h12gLedgerState.get() != 1) {
                lane.h12gLedgerReservation.compareAndSet(reservationIdentity, null);
            }
            return "SUCCESS";
        }
    }

    private boolean currentH12gProjection(GeneratedEvidenceLane lane,
            com.gameexpert.ws.dto.WsMessages.FinalSceneGeneratedPrerequisiteReceipt receipt) {
        if (lane == null || receipt == null || !"H12g".equals(receipt.getScenario())
                || lane.h12gProjection == null
                || generatedStructureEntities.snapshotByEntityId(lane.entityId)
                        != lane.h12gProjection
                || !(receipt.getEvidence() instanceof com.gameexpert.ws.dto.WsMessages
                        .FinalSceneH12gPrerequisiteEvidence evidence)
                || evidence.getCargoRevision() != lane.h12gProjection.revision()
                || lane.h12gCommittedSlots == null || lane.h12gCommittedCursor == null
                || evidence.getPersistedCursor() == null
                || !sameGeneratedCargoCursor(lane.h12gCommittedCursor,
                        evidence.getPersistedCursor())) return false;
        List<com.gameexpert.ws.dto.WsMessages.InventorySlot> current =
                WorldTickLoop.generatedMinecartCargoSlots(lane.h12gProjection);
        if (current.size() != 27 || evidence.getPersistedSlots().size() != 27
                || lane.h12gCommittedSlots.size() != 27) return false;
        for (int index = 0; index < 27; index++) {
            if (!sameGeneratedCargoSlot(current.get(index),
                    evidence.getPersistedSlots().get(index))
                    || !sameGeneratedCargoSlot(lane.h12gCommittedSlots.get(index),
                            evidence.getPersistedSlots().get(index))) return false;
        }
        return true;
    }

    private static boolean sameGeneratedCargoSlot(
            com.gameexpert.ws.dto.WsMessages.InventorySlot left,
            com.gameexpert.ws.dto.WsMessages.InventorySlot right) {
        return left.getSlot() == right.getSlot() && left.getItemType() == right.getItemType()
                && left.getCount() == right.getCount()
                && java.util.Objects.equals(left.getDurability(), right.getDurability())
                && left.getWideEnchantments().equals(right.getWideEnchantments())
                && java.util.Objects.equals(left.getMapId(), right.getMapId())
                && java.util.Objects.equals(left.getShulkerId(), right.getShulkerId())
                && java.util.Objects.equals(left.getBucketMobData(), right.getBucketMobData())
                && java.util.Objects.equals(left.getCustomName(), right.getCustomName())
                && sameGeneratedBannerPatterns(left.getBannerPatterns(),
                        right.getBannerPatterns())
                && sameGeneratedBook(left.getBook(), right.getBook())
                && left.getAnvilUseCount() == right.getAnvilUseCount()
                && java.util.Objects.equals(left.getLeatherColor(), right.getLeatherColor())
                && java.util.Objects.equals(left.getSuspiciousStewEffect(),
                        right.getSuspiciousStewEffect())
                && java.util.Objects.equals(left.getSuspiciousStewDurationMcTicks(),
                        right.getSuspiciousStewDurationMcTicks())
                && java.util.Objects.equals(left.getOminousBottleAmplifier(),
                        right.getOminousBottleAmplifier());
    }

    private static boolean sameGeneratedCargoCursor(
            com.gameexpert.ws.dto.WsMessages.CraftingStack left,
            com.gameexpert.ws.dto.WsMessages.CraftingStack right) {
        return left.getItemType() == right.getItemType()
                && left.getCount() == right.getCount()
                && java.util.Objects.equals(left.getDurability(), right.getDurability())
                && left.getWideEnchantments().equals(right.getWideEnchantments())
                && java.util.Objects.equals(left.getMapId(), right.getMapId())
                && java.util.Objects.equals(left.getShulkerId(), right.getShulkerId())
                && java.util.Objects.equals(left.getBucketMobData(), right.getBucketMobData())
                && java.util.Objects.equals(left.getCustomName(), right.getCustomName())
                && sameGeneratedBannerPatterns(left.getBannerPatterns(),
                        right.getBannerPatterns())
                && sameGeneratedBook(left.getBook(), right.getBook())
                && left.getAnvilUseCount() == right.getAnvilUseCount()
                && java.util.Objects.equals(left.getLeatherColor(), right.getLeatherColor())
                && java.util.Objects.equals(left.getSuspiciousStewEffect(),
                        right.getSuspiciousStewEffect())
                && java.util.Objects.equals(left.getSuspiciousStewDurationMcTicks(),
                        right.getSuspiciousStewDurationMcTicks())
                && java.util.Objects.equals(left.getOminousBottleAmplifier(),
                        right.getOminousBottleAmplifier());
    }

    private static boolean sameGeneratedBannerPatterns(
            List<com.gameexpert.ws.dto.WsMessages.BannerPatternLayer> left,
            List<com.gameexpert.ws.dto.WsMessages.BannerPatternLayer> right) {
        List<com.gameexpert.ws.dto.WsMessages.BannerPatternLayer> safeLeft =
                left == null ? List.of() : left;
        List<com.gameexpert.ws.dto.WsMessages.BannerPatternLayer> safeRight =
                right == null ? List.of() : right;
        if (safeLeft.size() != safeRight.size()) return false;
        for (int index = 0; index < safeLeft.size(); index++) {
            var a = safeLeft.get(index);
            var b = safeRight.get(index);
            if (a == null || b == null || a.getColor() != b.getColor()
                    || !java.util.Objects.equals(a.getPattern(), b.getPattern())) return false;
        }
        return true;
    }

    private static boolean sameGeneratedBook(
            com.gameexpert.ws.dto.WsMessages.BookComponent left,
            com.gameexpert.ws.dto.WsMessages.BookComponent right) {
        if (left == right) return true;
        return left != null && right != null
                && java.util.Objects.equals(left.getTitle(), right.getTitle())
                && java.util.Objects.equals(left.getAuthor(), right.getAuthor())
                && java.util.Objects.equals(left.getPages(), right.getPages());
    }

    private static List<com.gameexpert.ws.dto.WsMessages.InventorySlot>
            copyGeneratedCargoSlots(
                    List<com.gameexpert.ws.dto.WsMessages.InventorySlot> slots) {
        if (slots == null) return null;
        return slots.stream().map(slot -> new com.gameexpert.ws.dto.WsMessages.InventorySlot(
                slot.getSlot(), slot.getItemType(), slot.getCount(), slot.getDurability(),
                slot.getWideEnchantments(), slot.getMapId(), slot.getShulkerId(),
                slot.getBucketMobData(), slot.getCustomName(),
                copyGeneratedBannerPatterns(slot.getBannerPatterns()),
                copyGeneratedBook(slot.getBook()), slot.getAnvilUseCount(),
                slot.getLeatherColor(), slot.getSuspiciousStewEffect(),
                slot.getSuspiciousStewDurationMcTicks(),
                slot.getOminousBottleAmplifier())).toList();
    }

    private static com.gameexpert.ws.dto.WsMessages.CraftingStack copyGeneratedCargoCursor(
            com.gameexpert.ws.dto.WsMessages.CraftingStack cursor) {
        if (cursor == null) return null;
        return new com.gameexpert.ws.dto.WsMessages.CraftingStack(cursor.getItemType(),
                cursor.getCount(), cursor.getDurability(), cursor.getWideEnchantments(),
                cursor.getMapId(), cursor.getShulkerId(), cursor.getBucketMobData(),
                cursor.getCustomName(), copyGeneratedBannerPatterns(cursor.getBannerPatterns()),
                copyGeneratedBook(cursor.getBook()), cursor.getAnvilUseCount(),
                cursor.getLeatherColor(), cursor.getSuspiciousStewEffect(),
                cursor.getSuspiciousStewDurationMcTicks(),
                cursor.getOminousBottleAmplifier());
    }

    private static List<com.gameexpert.ws.dto.WsMessages.BannerPatternLayer>
            copyGeneratedBannerPatterns(
                    List<com.gameexpert.ws.dto.WsMessages.BannerPatternLayer> patterns) {
        if (patterns == null || patterns.isEmpty()) return List.of();
        return patterns.stream().map(pattern -> new com.gameexpert.ws.dto.WsMessages
                .BannerPatternLayer(pattern.getPattern(), pattern.getColor())).toList();
    }

    private static com.gameexpert.ws.dto.WsMessages.BookComponent copyGeneratedBook(
            com.gameexpert.ws.dto.WsMessages.BookComponent book) {
        return book == null ? null : new com.gameexpert.ws.dto.WsMessages.BookComponent(
                book.getTitle(), book.getAuthor(), book.getPages() == null
                        ? List.of() : List.copyOf(book.getPages()));
    }

    private void observeGeneratedFinalSceneChunkResidency(Set<Long> chunks) {
        for (var entry : List.copyOf(generatedFinalSceneEvidence.entrySet())) {
            GeneratedEvidenceLane lane = entry.getValue();
            boolean active = chunks.contains(chunkKey(lane.chunkX, lane.chunkZ));
            if (!active) {
                lane.sawUnload = true;
                continue;
            }
            if (!lane.sawUnload || lane.unloadReload
                    || !(lane.terminal || lane.cargoClosed)) continue;
            var current = generatedStructureEntities.snapshotByEntityId(lane.entityId);
            if (current == null || current.revision() != lane.revision) continue;
            try {
                lane.tracker.recordUnloadReloadPersisted(generatedEvidenceStamp(
                        lane, current, ++lane.evidenceGeneration));
                lane.unloadReload = true;
            } catch (IllegalArgumentException | IllegalStateException invalid) {
                generatedFinalSceneEvidence.remove(entry.getKey());
            }
        }
    }

    private GeneratedFinalSceneEvidenceTracker.EvidenceStamp generatedEvidenceStamp(
            GeneratedEvidenceLane lane,
            WorldGeneratedStructureEntityState.RuntimeSnapshot snapshot,
            long evidenceGeneration) {
        return new GeneratedFinalSceneEvidenceTracker.EvidenceStamp(worldId,
                snapshot.binding().entityId(), snapshot.revision(), lane.connectionGeneration,
                evidenceGeneration);
    }

    private static String generatedEntityStateFingerprint(
            WorldGeneratedStructureEntityState.RuntimeSnapshot snapshot) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            StringBuilder canonical = new StringBuilder(512)
                    .append(snapshot.binding()).append('|').append(snapshot.revision())
                    .append('|').append(snapshot.lifecycle()).append('|')
                    .append(snapshot.transform());
            if (snapshot instanceof WorldGeneratedStructureEntityState
                    .ArmorStandRuntimeSnapshot armor) {
                canonical.append('|').append(java.util.Arrays.toString(armor.poseHead()))
                        .append('|').append(java.util.Arrays.toString(armor.poseBody()))
                        .append('|').append(armor.equipmentSlot()).append('|')
                        .append(armor.equipmentItem()).append('|').append(armor.showArms())
                        .append('|').append(armor.small()).append('|')
                        .append(armor.noBasePlate()).append('|').append(armor.invisible())
                        .append('|').append(armor.invulnerable()).append('|')
                        .append(armor.disabledSlots()).append('|')
                        .append(Float.floatToRawIntBits(armor.health()));
            } else if (snapshot instanceof WorldGeneratedStructureEntityState
                    .MinecartRuntimeSnapshot minecart) {
                canonical.append('|').append(minecart.lootTable()).append('|')
                        .append(minecart.lootSeed()).append('|').append(minecart.provenance())
                        .append('|').append(minecart.lootStatus()).append('|')
                        .append(minecart.lootDefinitionFingerprint()).append('|')
                        .append(minecart.lootResultFingerprint()).append('|')
                        .append(java.util.HexFormat.of().formatHex(
                                minecart.lootResolution() == null
                                        ? new byte[0] : minecart.lootResolution()));
                for (com.gameexpert.chest.entity.ChestItem item : minecart.cargo()) {
                    canonical.append('|').append(item.getSlot()).append(':')
                            .append(item.getItemType()).append(':').append(item.getItemCount())
                            .append(':').append(item.getDurability()).append(':')
                            .append(item.getEnchantments()).append(':').append(item.getMapId())
                            .append(':').append(item.getShulkerId()).append(':')
                            .append(item.getBucketMobData()).append(':')
                            .append(item.getItemComponentData());
                }
            }
            digest.update(canonical.toString().getBytes(
                    java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /**
     * Resolves one exact live generated Chest Minecart through its Runtime-owned installed
     * aggregate.  Neither the aggregate nor a caller-built snapshot crosses this boundary.
     */
    GeneratedMinecartFirstOpenResult resolveGeneratedChestMinecartFirstOpen(
            long entityId, long expectedSourceRevision) {
        if (!generatedCushionOperationMayProceed()
                || generatedMinecartLootResolutions == null
                || entityId <= 0L || entityId == Long.MAX_VALUE
                || expectedSourceRevision < 0L
                || expectedSourceRevision == Long.MAX_VALUE) {
            throw new IllegalStateException(
                    "generated chest minecart first-open authority is unavailable");
        }
        var resolved = generatedStructureEntities.snapshotByEntityId(entityId);
        if (!(resolved instanceof WorldGeneratedStructureEntityState.MinecartRuntimeSnapshot current)
                || current.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE) {
            throw new IllegalStateException("exact live generated chest minecart is unavailable");
        }
        StructureEntityAggregate aggregate = structureEntityInstallations.get(
                current.binding().installationIdentity());
        if (aggregate == null || !aggregate.installed()) {
            throw new IllegalStateException(
                    "generated chest minecart installed aggregate is unavailable");
        }
        GeneratedChestMinecartLootResolutionService.ResolutionResult committed =
                generatedMinecartLootResolutions.resolveFirstOpen(worldId, entityId,
                        expectedSourceRevision, aggregate);
        if (committed.current().binding().entityId() != entityId
                || committed.current().binding().worldId() != worldId
                || committed.current().lifecycle()
                        != WorldGeneratedStructureEntityState.Lifecycle.LIVE) {
            throw new IllegalStateException(
                    "generated chest minecart first-open returned a foreign state");
        }
        for (WorldMapData map : committed.materializedMaps()) {
            acceptSettledMap(map);
        }
        GeneratedStructureEntitySystem.InstallDelta delta =
                generatedStructureEntities.install(List.of(committed.current()));
        reconcileGeneratedEntityVisibility(activeSimulationChunks, delta.updated());
        return new GeneratedMinecartFirstOpenResult(
                committed.outcome(), committed.current());
    }

    record GeneratedMinecartFirstOpenResult(
            GeneratedChestMinecartLootResolutionService.Outcome outcome,
            WorldGeneratedStructureEntityState.MinecartRuntimeSnapshot current) {
        GeneratedMinecartFirstOpenResult {
            java.util.Objects.requireNonNull(outcome, "minecart first-open outcome");
            java.util.Objects.requireNonNull(current, "minecart first-open current state");
        }
    }

    /** Installs only the exact post-transaction cargo state derived from the current live object. */
    boolean installCommittedGeneratedMinecartCargo(
            WorldGeneratedStructureEntityState.MinecartRuntimeSnapshot expected,
            WorldGeneratedStructureEntityState.MinecartRuntimeSnapshot committed) {
        if (!generatedCushionOperationMayProceed() || expected == null || committed == null
                || generatedStructureEntities.snapshotByEntityId(
                        expected.binding().entityId()) != expected
                || !expected.binding().equals(committed.binding())
                || expected.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE
                || committed.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE
                || expected.revision() == Long.MAX_VALUE
                || committed.revision() != expected.revision() + 1L
                || expected.lootStatus()
                        != WorldGeneratedStructureEntityState.LootStatus.RESOLVED
                || committed.lootStatus() != expected.lootStatus()
                || !java.util.Objects.equals(expected.lootTable(), committed.lootTable())
                || expected.lootSeed() != committed.lootSeed()
                || !java.util.Objects.equals(expected.provenance(), committed.provenance())
                || !java.util.Objects.equals(expected.lootDefinitionFingerprint(),
                        committed.lootDefinitionFingerprint())
                || !java.util.Objects.equals(expected.lootResultFingerprint(),
                        committed.lootResultFingerprint())
                || !java.util.Arrays.equals(expected.lootResolution(),
                        committed.lootResolution())) return false;
        GeneratedStructureEntitySystem.InstallDelta delta =
                generatedStructureEntities.install(List.of(committed));
        reconcileGeneratedEntityVisibility(activeSimulationChunks, delta.updated());
        return generatedStructureEntities.snapshotByEntityId(
                committed.binding().entityId()) == committed;
    }

    /** Plans detached player state, commits both durable rows, then installs both live results. */
    GeneratedArmorStandEquipmentResult settleGeneratedArmorStandEquipment(
            String actor, long entityId, long expectedEntityRevision,
            long expectedInventoryRevision, PlayerAction.Hand requestedHand) {
        PlayerTickState player = actor == null ? null : players.get(actor);
        var raw = generatedStructureEntities.snapshotByEntityId(entityId);
        if (!generatedCushionOperationMayProceed()
                || generatedArmorStandEquipmentSettlements == null
                || player == null || player.isDead()
                || !(raw instanceof WorldGeneratedStructureEntityState
                        .ArmorStandRuntimeSnapshot armor)
                || armor.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE
                || armor.revision() != expectedEntityRevision
                || player.inventory().revision() != expectedInventoryRevision
                || requestedHand == null
                || !CombatRules.withinAuthorityReach(player.x(), player.y(), player.z(),
                        player.crouching(), armor.transform().x(), armor.transform().y(),
                        armor.transform().z(), 0.5, armor.small() ? 0.9875 : 1.975)) {
            return GeneratedArmorStandEquipmentResult.stale(armorOrNull(raw));
        }
        StructureEntityAggregate aggregate = structureEntityInstallations.get(
                armor.binding().installationIdentity());
        if (aggregate == null || !aggregate.installed()) {
            return GeneratedArmorStandEquipmentResult.stale(armor);
        }
        PlayerInventory inventory = player.inventory();
        PlayerInventory.CompletePersistenceSnapshot source = inventory.acquireSettlementLease();
        if (source == null || source.revision() != expectedInventoryRevision) {
            if (source != null) inventory.cancelSettlementLease(source);
            return GeneratedArmorStandEquipmentResult.stale(armor);
        }
        PlayerInventory.Hand hand = requestedHand == PlayerAction.Hand.OFFHAND
                ? PlayerInventory.Hand.OFFHAND : PlayerInventory.Hand.MAIN;
        try {
            PlayerInventory detached = source.detachedInventory();
            PlayerInventory.HandRef detachedHand = detached.capture(hand);
            PlayerInventory.StackSnapshot held = detached.stack(detachedHand);
            String nextEquipment = generatedArmorEquipmentItem(armor.equipmentSlot(), held);
            if (nextEquipment.equals(armor.equipmentItem())
                    || !detached.setStack(detachedHand,
                            generatedArmorEquipmentStack(armor.equipmentItem()))) {
                inventory.cancelSettlementLease(source);
                return GeneratedArmorStandEquipmentResult.stale(armor);
            }
            PlayerInventory.CompletePersistenceSnapshot committed =
                    detached.completePersistenceSnapshot();
            var command = new ContainerSettlementPersistenceService
                    .ArmorStandEquipmentSettlementCommand(source.revision(),
                            playerInventoryMutationSnapshot(player, source),
                            playerInventoryMutationSnapshot(player, committed), armor,
                            nextEquipment, hand, player.crouching(), aggregate);
            java.util.concurrent.atomic.AtomicReference<ContainerSettlementPersistenceService
                    .ArmorStandEquipmentSettlementResult> durable =
                    new java.util.concurrent.atomic.AtomicReference<>();
            Runnable transaction = () -> durable.set(
                    generatedArmorStandEquipmentSettlements
                            .settleArmorStandEquipment(command));
            PersistenceExecutor writer = ctx.persistenceExecutor();
            if (writer == null) transaction.run();
            else writer.submitFuture(transaction).get();
            var result = durable.get();
            if (result == null || result.outcome()
                    != ContainerSettlementPersistenceService
                            .ArmorStandEquipmentOutcome.COMMITTED) {
                inventory.cancelSettlementLease(source);
                return GeneratedArmorStandEquipmentResult.stale(
                        result == null ? armor : result.currentEntity());
            }
            if (!inventory.installCommittedSettlement(source, committed)) {
                throw new IllegalStateException(
                        "committed Armor Stand player settlement lost its live lease");
            }
            GeneratedStructureEntitySystem.InstallDelta delta =
                    generatedStructureEntities.install(List.of(result.currentEntity()));
            reconcileGeneratedEntityVisibility(activeSimulationChunks, delta.updated());
            return GeneratedArmorStandEquipmentResult.committed(
                    result.currentEntity(), committed.revision());
        } catch (InterruptedException interrupted) {
            inventory.cancelSettlementLease(source);
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Armor Stand equipment settlement interrupted", interrupted);
        } catch (ExecutionException failure) {
            inventory.cancelSettlementLease(source);
            throw new IllegalStateException(
                    "Armor Stand equipment settlement failed", failure.getCause());
        } catch (RuntimeException | Error failure) {
            inventory.cancelSettlementLease(source);
            throw failure;
        }
    }

    record GeneratedArmorStandEquipmentResult(boolean committed,
            WorldGeneratedStructureEntityState.ArmorStandRuntimeSnapshot current,
            long inventoryRevision) {
        private static GeneratedArmorStandEquipmentResult committed(
                WorldGeneratedStructureEntityState.ArmorStandRuntimeSnapshot current,
                long inventoryRevision) {
            return new GeneratedArmorStandEquipmentResult(true, current, inventoryRevision);
        }
        private static GeneratedArmorStandEquipmentResult stale(
                WorldGeneratedStructureEntityState.ArmorStandRuntimeSnapshot current) {
            return new GeneratedArmorStandEquipmentResult(false, current, -1L);
        }
    }

    private static WorldGeneratedStructureEntityState.ArmorStandRuntimeSnapshot armorOrNull(
            WorldGeneratedStructureEntityState.RuntimeSnapshot raw) {
        return raw instanceof WorldGeneratedStructureEntityState.ArmorStandRuntimeSnapshot armor
                ? armor : null;
    }

    private static String generatedArmorEquipmentItem(String slot,
            PlayerInventory.StackSnapshot held) {
        if (held.isEmpty()) return "minecraft:air";
        if (held.count() != 1 || held.enchantments() != 0L || held.mapId() != 0
                || held.shulkerId() != 0 || held.bucketMobData() != null
                || held.itemComponentData() != null) {
            throw new IllegalArgumentException("held Armor Stand equipment is not canonical");
        }
        if ("head".equals(slot) && held.itemType() == PlayerInventory.IRON_HELMET
                && held.durability() == PlayerInventory.initialDurability(
                        PlayerInventory.IRON_HELMET)) return "minecraft:iron_helmet";
        if ("chest".equals(slot) && held.itemType() == PlayerInventory.IRON_CHESTPLATE
                && held.durability() == PlayerInventory.initialDurability(
                        PlayerInventory.IRON_CHESTPLATE)) return "minecraft:iron_chestplate";
        throw new IllegalArgumentException("held item does not match Armor Stand slot");
    }

    private static PlayerInventory.StackSnapshot generatedArmorEquipmentStack(String item) {
        short type = switch (item) {
            case "minecraft:air" -> PlayerInventory.EMPTY;
            case "minecraft:iron_helmet" -> PlayerInventory.IRON_HELMET;
            case "minecraft:iron_chestplate" -> PlayerInventory.IRON_CHESTPLATE;
            default -> throw new IllegalArgumentException(
                    "unsupported Armor Stand equipment state");
        };
        return type == PlayerInventory.EMPTY ? PlayerInventory.StackSnapshot.EMPTY
                : new PlayerInventory.StackSnapshot(type, 1,
                        PlayerInventory.initialDurability(type), 0L, 0, 0, null, null);
    }

    /**
     * Commits an authenticated player attack on a generated Armor Stand. Armor Stand death is a
     * durable source tombstone plus state revision before the live registry publishes removal.
     */
    GeneratedEntityActionResult submitGeneratedArmorStandAttackResult(
            WorldGeneratedStructureEntityState.RuntimeSnapshot resolvedTarget, String actor) {
        if (!generatedCushionOperationMayProceed()) {
            return GeneratedEntityActionResult.failed(resolvedTarget, false, "RUNTIME_TERMINAL");
        }
        PlayerTickState player = actor == null ? null : players.get(actor);
        if (generatedCushionMutations == null || player == null || player.isDead()
                || !(resolvedTarget instanceof WorldGeneratedStructureEntityState
                        .ArmorStandRuntimeSnapshot current)
                || current.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE
                || generatedStructureEntities.snapshotByEntityId(
                        current.binding().entityId()) != resolvedTarget) {
            return GeneratedEntityActionResult.failed(
                    resolvedTarget, true, "TARGET_OR_COORDINATOR_UNAVAILABLE");
        }
        if (current.invulnerable()) {
            return GeneratedEntityActionResult.failed(current, false, "INVULNERABLE");
        }
        if (!CombatRules.withinAuthorityReach(player.x(), player.y(), player.z(),
                player.crouching(), current.transform().x(), current.transform().y(),
                current.transform().z(), 0.5, current.small() ? 0.9875 : 1.975)) {
            return GeneratedEntityActionResult.failed(current, false, "OUT_OF_REACH");
        }
        StructureEntityAggregate aggregate = structureEntityInstallations.get(
                current.binding().installationIdentity());
        if (aggregate == null || !aggregate.installed()) {
            return GeneratedEntityActionResult.failed(current, true, "AGGREGATE_UNAVAILABLE");
        }
        try {
            WorldGeneratedStructureEntityState.ArmorStandRuntimeSnapshot committed =
                    generatedCushionMutations.commitArmorStandTerminal(current, aggregate);
            if (!generatedCushionOperationMayProceed()) {
                return GeneratedEntityActionResult.failed(current, false, "RUNTIME_TERMINAL");
            }
            GeneratedStructureEntitySystem.InstallDelta delta =
                    generatedStructureEntities.install(List.of(committed));
            reconcileGeneratedEntityVisibility(activeSimulationChunks, delta.updated());
            return GeneratedEntityActionResult.accepted(committed);
        } catch (RuntimeException | Error rejected) {
            return GeneratedEntityActionResult.failed(current, true, "MUTATION_NOT_COMMITTED");
        }
    }

    record GeneratedEntityActionResult(boolean accepted, boolean retryable, long entityId,
            List<String> failureCodes) {
        GeneratedEntityActionResult {
            failureCodes = List.copyOf(failureCodes);
        }

        private static GeneratedEntityActionResult accepted(
                WorldGeneratedStructureEntityState.RuntimeSnapshot snapshot) {
            return new GeneratedEntityActionResult(true, false,
                    snapshot.binding().entityId(), List.of());
        }

        private static GeneratedEntityActionResult failed(
                WorldGeneratedStructureEntityState.RuntimeSnapshot snapshot,
                boolean retryable, String failureCode) {
            long entityId = snapshot == null ? 0L : snapshot.binding().entityId();
            return new GeneratedEntityActionResult(
                    false, retryable, entityId, List.of(failureCode));
        }
    }

    /**
     * Captures one semantic generated-Cushion action from owner state. Callers choose only the
     * target/action/actor; every policy fact is recomputed here and remains hidden in an exact
     * identity token. Unknown terrain is interpreted in the rejecting direction for both sit and
     * support-loss actions.
     */
    synchronized GeneratedCushionActionEvidence issueGeneratedCushionActionEvidence(
            long authoritativeEntityId, GeneratedCushionActionPolicy.Action action, String actor) {
        if (!generatedCushionOperationMayProceed()) {
            throw new IllegalStateException("generated Cushion action after terminal boundary");
        }
        java.util.Objects.requireNonNull(action, "generated Cushion action");
        WorldGeneratedStructureEntityState.RuntimeSnapshot raw =
                generatedStructureEntities.snapshotByEntityId(authoritativeEntityId);
        if (!(raw instanceof WorldGeneratedStructureEntityState.CushionRuntimeSnapshot current)
                || current.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE) {
            throw new IllegalStateException("live generated cushion authority is required");
        }

        var request = observeGeneratedCushionAction(current, action, actor);
        var evidence = new GeneratedCushionActionEvidence();
        generatedCushionActionEvidence.put(evidence,
                new GeneratedCushionActionEvidenceState(current, request));
        return evidence;
    }

    private GeneratedCushionActionPolicy.Request observeGeneratedCushionAction(
            WorldGeneratedStructureEntityState.CushionRuntimeSnapshot current,
            GeneratedCushionActionPolicy.Action action, String actor) {
        PlayerTickState player = actor == null ? null : players.get(actor);
        boolean forcedLogout = action == GeneratedCushionActionPolicy.Action.RIDER_LOGOUT
                && actor != null && ownsDrainingCushionLogoutAdmission(current, actor, action);
        boolean actorPresent = player != null && !forcedLogout;
        boolean actorAlive = actorPresent && !player.isDead();
        boolean actorCrouching = actorPresent && player.crouching();
        boolean withinReach = actorPresent && CushionSystem.withinReach(player,
                current.transform().x(), current.transform().y(), current.transform().z());
        boolean ridingAnother = actor != null
                && actorRidingAnotherCushion(actor, current.binding().entityId());
        Boolean anchor = CushionSystem.survivesAt(this, current.transform().x(),
                current.transform().y(), current.transform().z());
        boolean supportPresent = Boolean.TRUE.equals(anchor)
                || action == GeneratedCushionActionPolicy.Action.SUPPORT_LOSS && anchor == null;
        boolean forbiddenCollision = generatedCushionHasForeignCollision(current);
        return new GeneratedCushionActionPolicy.Request(action, actor, actorPresent,
                actorAlive, actorCrouching, ridingAnother, withinReach, supportPresent,
                forbiddenCollision);
    }

    /** Claims the exact issued identity once; structural copies and foreign runtimes have no entry. */
    synchronized GeneratedCushionActionClaim claimGeneratedCushionActionEvidence(
            GeneratedCushionActionEvidence evidence, long authoritativeEntityId) {
        if (!generatedCushionOperationMayProceed()) {
            throw new IllegalStateException("generated Cushion action after terminal boundary");
        }
        GeneratedCushionActionEvidenceState state = generatedCushionActionEvidence.get(evidence);
        if (state == null || state.claim != null) {
            throw new IllegalStateException(
                    "generated Cushion action evidence is foreign, forged, stale, or replayed");
        }
        if (state.current.binding().entityId() != authoritativeEntityId
                || !state.current.equals(generatedStructureEntities
                        .snapshotByEntityId(authoritativeEntityId))) {
            generatedCushionActionEvidence.remove(evidence);
            throw new IllegalStateException(
                    "generated Cushion action evidence is foreign, forged, stale, or replayed");
        }
        state.claim = new GeneratedCushionActionClaim(evidence, state.current, state.request);
        return state.claim;
    }

    /** True only while this runtime still owns the exact claimed evidence and live baseline. */
    synchronized boolean ownsGeneratedCushionActionEvidence(GeneratedCushionActionClaim claim) {
        if (claim == null) return false;
        GeneratedCushionActionEvidenceState state =
                generatedCushionActionEvidence.get(claim.evidence);
        return state != null && state.claim == claim
                && state.current.equals(generatedStructureEntities.snapshotByEntityId(
                        state.current.binding().entityId()))
                && state.request.equals(observeGeneratedCushionAction(state.current,
                        state.request.action(), state.request.actor()));
    }

    /** Releases issued or claimed evidence on rejection, rollback, completion, or supersession. */
    synchronized void revokeGeneratedCushionActionEvidence(GeneratedCushionActionClaim claim) {
        if (claim == null) return;
        GeneratedCushionActionEvidenceState state =
                generatedCushionActionEvidence.get(claim.evidence);
        if (state != null && state.claim == claim) {
            generatedCushionActionEvidence.remove(claim.evidence);
        }
    }

    private synchronized void discardGeneratedCushionActionEvidence(
            GeneratedCushionActionEvidence evidence) {
        GeneratedCushionActionEvidenceState state = generatedCushionActionEvidence.get(evidence);
        if (state != null && state.claim == null) generatedCushionActionEvidence.remove(evidence);
    }

    /** Installs the Spring transaction proxy before any generated semantic action is admitted. */
    void attachGeneratedCushionMutations(
            GeneratedStructureEntityMutationCoordinator coordinator) {
        if (coordinator == null) return;
        if (generatedCushionMutations != null && generatedCushionMutations != coordinator) {
            throw new IllegalStateException("generated Cushion coordinator already attached");
        }
        generatedCushionMutations = coordinator;
    }

    /** Installs the Spring first-open transaction proxy before Minecart interaction is admitted. */
    void attachGeneratedMinecartLootResolutions(
            GeneratedChestMinecartLootResolutionService service) {
        if (service == null) return;
        if (generatedMinecartLootResolutions != null
                && generatedMinecartLootResolutions != service) {
            throw new IllegalStateException(
                    "generated chest minecart loot resolver already attached");
        }
        generatedMinecartLootResolutions = service;
    }

    void attachGeneratedArmorStandEquipmentSettlements(
            ContainerSettlementPersistenceService service) {
        if (service == null) return;
        if (generatedArmorStandEquipmentSettlements != null
                && generatedArmorStandEquipmentSettlements != service) {
            throw new IllegalStateException(
                    "generated Armor Stand equipment settlement already attached");
        }
        generatedArmorStandEquipmentSettlements = service;
    }

    /**
     * Sole owner-thread caller for generated-Cushion semantics. The supplied target must be the
     * exact live object returned by the generated resolver; numeric aliases never cross this seam.
     */
    boolean submitGeneratedCushionAction(
            WorldGeneratedStructureEntityState.RuntimeSnapshot resolvedTarget,
            GeneratedCushionActionPolicy.Action action, String actor) {
        return submitGeneratedCushionActionResult(resolvedTarget, action, actor).accepted();
    }

    GeneratedCushionActionResult submitGeneratedCushionActionResult(
            WorldGeneratedStructureEntityState.RuntimeSnapshot resolvedTarget,
            GeneratedCushionActionPolicy.Action action, String actor) {
        if (!generatedCushionOperationMayProceed()) {
            return GeneratedCushionActionResult.failed(action, resolvedTarget,
                    false, "RUNTIME_TERMINAL");
        }
        if (generatedCushionMutations == null
                || !(resolvedTarget instanceof WorldGeneratedStructureEntityState
                        .CushionRuntimeSnapshot current)
                || current.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE
                || generatedStructureEntities.snapshotByEntityId(
                        current.binding().entityId()) != resolvedTarget) {
            return GeneratedCushionActionResult.failed(action, resolvedTarget,
                    true, "TARGET_OR_COORDINATOR_UNAVAILABLE");
        }
        StructureEntityAggregate aggregate = structureEntityInstallations.get(
                current.binding().installationIdentity());
        if (aggregate == null || !aggregate.installed()
                || !aggregate.sourceFingerprint().equals(
                        current.binding().installationSourceFingerprint())
                || current.binding().encounterOrdinal() < 0
                || current.binding().encounterOrdinal() >= aggregate.plannedEntities().size()) {
            return GeneratedCushionActionResult.failed(action, current,
                    true, "AGGREGATE_UNAVAILABLE");
        }
        StructureEntityAggregate.PlannedEntity planned = aggregate.plannedEntities()
                .get(current.binding().encounterOrdinal());
        if (planned.authoritativeEntityId() != current.binding().entityId()
                || !planned.rowFingerprint().equals(current.binding().sourceRowFingerprint())) {
            return GeneratedCushionActionResult.failed(action, current,
                    true, "SOURCE_IDENTITY_MISMATCH");
        }

        GeneratedCushionActionEvidence evidence = issueGeneratedCushionActionEvidence(
                current.binding().entityId(), action, actor);
        GeneratedCushionActionEvidenceState observed = generatedCushionActionEvidence.get(evidence);
        var source = new com.gameexpert.engine.persistence.finalcarrier.structureentity
                .WorldStructureEntity(current.binding().worldId(), current.binding().chunkX(),
                        current.binding().chunkZ(), current.binding().installationIdentity(),
                        current.binding().installationSourceFingerprint(), planned);
        var decision = GeneratedCushionActionPolicy.plan(
                new GeneratedCushionActionPolicy.Authority(source, aggregate, current,
                        current.binding().entityId(), current.revision()), observed.request);
        if (!decision.accepted()) {
            discardGeneratedCushionActionEvidence(evidence);
            return GeneratedCushionActionResult.rejected(action, current,
                    decision.rejections());
        }
        GroundMutationCommand terminalCommand = null;
        boolean terminal = action == GeneratedCushionActionPolicy.Action.BREAK
                || action == GeneratedCushionActionPolicy.Action.SUPPORT_LOSS;
        if (terminal) {
            synchronized (this) {
                if (!groundSettlementAvailable()) {
                    discardGeneratedCushionActionEvidence(evidence);
                    return GeneratedCushionActionResult.failed(action, current,
                            true, "GROUND_SETTLEMENT_BUSY");
                }
                groundSettlementInFlight = true;
            }
            terminalCommand = GeneratedStructureEntityMutationCoordinator.terminalGroundCommand(
                    this, current.binding().entityId(), groundRevision,
                    decision.plan().durable().terminalSettlement());
        }

        try {
            var capability = generatedCushionMutations.prepareCushionAction(
                    this, aggregate, current.binding().entityId(), evidence, terminalCommand);
            generatedCushionMutations.mutateCushionAction(this, capability);
            if (!generatedCushionOperationMayProceed()) {
                discardGeneratedCushionActionAfterTerminal(evidence, terminal);
                return GeneratedCushionActionResult.failed(action, current,
                        false, "RUNTIME_TERMINAL");
            }
            drainCommittedGeneratedCushionMutations();
            if (!generatedCushionOperationMayProceed()) {
                discardGeneratedCushionActionAfterTerminal(evidence, terminal);
                return GeneratedCushionActionResult.failed(action, current,
                        false, "RUNTIME_TERMINAL");
            }
            drainPersistenceCompletions();
            if (action == GeneratedCushionActionPolicy.Action.BREAK) {
                tickLoop.emitDecorationVibration(com.gameexpert.engine.sculk.SculkVibrationRules.Event.BLOCK_BREAK,
                        current.transform().x(), current.transform().y(), current.transform().z(), actor);
            }
            return GeneratedCushionActionResult.accepted(action, current);
        } catch (RuntimeException | Error rejected) {
            discardGeneratedCushionActionAfterTerminal(evidence, terminal);
            if (terminal) completeGroundSettlement(groundRevision, false);
            return GeneratedCushionActionResult.failed(action, current,
                    true, "MUTATION_NOT_COMMITTED");
        }
    }

    private synchronized void discardGeneratedCushionActionAfterTerminal(
            GeneratedCushionActionEvidence evidence, boolean terminal) {
        // The coordinator's afterCompletion callback is allowed to arrive later; removing the
        // identity now makes a failed/aborted owner action unable to be replayed through it.
        generatedCushionActionEvidence.remove(evidence);
        if (terminal && (terminalPhase == TerminalPhase.ABORTED
                || terminalPhase == TerminalPhase.DISPOSED)) {
            groundSettlementInFlight = false;
        }
    }

    void processGeneratedCushionRiderState(String actor,
            GeneratedCushionActionPolicy.Action action) {
        if (actor == null || (action != GeneratedCushionActionPolicy.Action.RIDER_LOGOUT
                && action != GeneratedCushionActionPolicy.Action.RIDER_DEATH)
                || !generatedCushionOperationMayProceed()) return;
        for (WorldGeneratedStructureEntityState.RuntimeSnapshot raw
                : generatedStructureEntities.snapshotsByKind(
                        com.gameexpert.engine.persistence.finalcarrier.structureentity
                                .GeneratedStructureEntityFacts.Kind.CUSHION)) {
            if (!generatedCushionOperationMayProceed()) return;
            if (raw instanceof WorldGeneratedStructureEntityState.CushionRuntimeSnapshot cushion
                    && cushion.lifecycle() == WorldGeneratedStructureEntityState.Lifecycle.LIVE
                    && actor.equals(cushion.rider())) {
                PendingGeneratedCushionRiderCleanup cleanup =
                        new PendingGeneratedCushionRiderCleanup(
                                cushion.binding().entityId(), actor, action);
                if (action == GeneratedCushionActionPolicy.Action.RIDER_LOGOUT
                        && terminalPhase == TerminalPhase.DRAINING) {
                    admitDrainingCushionLogout(cushion, actor, action);
                }
                if (terminalPhase == TerminalPhase.DRAINING) {
                    pendingGeneratedCushionRiderCleanups.put(
                            cushion.binding().entityId(), cleanup);
                } else {
                    pendingGeneratedCushionRiderCleanups.putIfAbsent(
                            cushion.binding().entityId(), cleanup);
                }
            }
        }
        retryPendingGeneratedCushionRiderCleanups();
    }

    private void retryPendingGeneratedCushionRiderCleanups() {
        if (pendingGeneratedCushionRiderCleanups.isEmpty()
                || !generatedCushionOperationMayProceed()) return;
        for (PendingGeneratedCushionRiderCleanup pending
                : List.copyOf(pendingGeneratedCushionRiderCleanups.values())) {
            if (!generatedCushionOperationMayProceed()) return;
            WorldGeneratedStructureEntityState.RuntimeSnapshot raw =
                    generatedStructureEntities.snapshotByEntityId(pending.entityId());
            if (!(raw instanceof WorldGeneratedStructureEntityState.CushionRuntimeSnapshot cushion)
                    || cushion.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE
                    || !pending.actor().equals(cushion.rider())) {
                pendingGeneratedCushionRiderCleanups.remove(pending.entityId(), pending);
                continue;
            }
            boolean drainingLogout = pending.action() == GeneratedCushionActionPolicy.Action.RIDER_LOGOUT
                    && terminalPhase == TerminalPhase.DRAINING;
            if (drainingLogout) {
                admitDrainingCushionLogout(cushion, pending.actor(), pending.action());
            }
            try {
                GeneratedCushionActionResult result = submitGeneratedCushionActionResult(
                        cushion, pending.action(), pending.actor());
                if (!generatedCushionOperationMayProceed()) return;
                if (!result.accepted() && !result.retryable()) {
                    pendingGeneratedCushionRiderCleanups.remove(pending.entityId(), pending);
                    log.warn("Generated Cushion rider cleanup rejected: world={}, entity={}, action={}, "
                                    + "x={}, y={}, z={}, failures={}",
                            worldId, result.entityId(), result.action(), result.x(), result.y(),
                            result.z(), result.failureCodes());
                    continue;
                }
                raw = generatedStructureEntities.snapshotByEntityId(pending.entityId());
                if (!(raw instanceof WorldGeneratedStructureEntityState.CushionRuntimeSnapshot after)
                        || after.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE
                        || !pending.actor().equals(after.rider())) {
                    pendingGeneratedCushionRiderCleanups.remove(pending.entityId(), pending);
                }
            } finally {
                if (drainingLogout) {
                    clearDrainingCushionLogout(
                            pending.entityId(), pending.actor(), pending.action());
                }
            }
        }
    }

    void processGeneratedCushionSupportLoss(long tickNo) {
        if (tickNo % 50L != 0L || !generatedCushionOperationMayProceed()) return;
        for (WorldGeneratedStructureEntityState.RuntimeSnapshot raw
                : generatedStructureEntities.snapshotsByKind(
                        com.gameexpert.engine.persistence.finalcarrier.structureentity
                                .GeneratedStructureEntityFacts.Kind.CUSHION)) {
            if (!generatedCushionOperationMayProceed()) return;
            if (raw instanceof WorldGeneratedStructureEntityState.CushionRuntimeSnapshot cushion
                    && cushion.lifecycle() == WorldGeneratedStructureEntityState.Lifecycle.LIVE) {
                submitGeneratedCushionAction(cushion,
                        GeneratedCushionActionPolicy.Action.SUPPORT_LOSS, null);
            }
        }
    }

    /** Moves after-commit receipts into a retry queue and applies each exact terminal once. */
    void drainCommittedGeneratedCushionMutations() {
        if (generatedCushionMutations == null || !generatedCushionOperationMayProceed()) return;
        GeneratedStructureEntityMutationCoordinator.CommittedCushionMutation committed;
        while (generatedCushionOperationMayProceed()
                && (committed = generatedCushionMutations.pollCommittedCushionMutation(this)) != null) {
            if (!generatedCushionOperationMayProceed()) return;
            pendingCommittedGeneratedCushionMutations.addLast(committed);
        }
        while ((committed = pendingCommittedGeneratedCushionMutations.peekFirst()) != null) {
            if (!generatedCushionOperationMayProceed()) return;
            try {
                boolean applied = committed.terminal()
                        ? applyCommittedGeneratedCushionTerminal(committed)
                        : applyCommittedGeneratedCushionUpdate(committed);
                if (!applied || !generatedCushionOperationMayProceed()) return;
                pendingCommittedGeneratedCushionMutations.removeFirst();
            } catch (RuntimeException | Error deferred) {
                log.error("Committed generated Cushion terminal deferred: world={}, entity={}",
                        worldId, committed.durable().binding().entityId(), deferred);
                return;
            }
        }
    }

    private synchronized boolean applyCommittedGeneratedCushionUpdate(
            GeneratedStructureEntityMutationCoordinator.CommittedCushionMutation committed) {
        if (!generatedCushionOperationMayProceed()) return false;
        if (!(committed.durable() instanceof WorldGeneratedStructureEntityState
                .CushionRuntimeSnapshot live)
                || live.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE) {
            throw new IllegalStateException("invalid committed generated Cushion update");
        }
        GeneratedCushionUpdateIdentity identity = generatedCushionUpdateIdentity(committed);
        if (appliedGeneratedCushionUpdates.contains(identity)) {
            flushGeneratedEntityMessages();
            return pendingGeneratedEntityMessages.isEmpty();
        }
        generatedStructureEntities.install(List.of(live));
        // A prior attempt may have installed the durable revision before visibility reconciliation
        // failed. Reconstruct the exact committed candidate so a same-revision retry still emits
        // the rider update instead of consuming the retained receipt as an empty delta.
        reconcileGeneratedEntityVisibility(activeSimulationChunks,
                List.of(GeneratedStructureEntitySystem.toWire(live)));
        appliedGeneratedCushionUpdates.add(identity);
        consumeGeneratedCushionPublication(identity);
        flushGeneratedEntityMessages();
        if (!pendingGeneratedEntityMessages.isEmpty()) {
            // The live revision is already installed exactly once. The durable committed event
            // stays at the queue head only to retry the already-created outbound message; a retry
            // must not reconstruct another update or advance the welcome sequence again.
            return false;
        }
        return true;
    }

    private synchronized boolean applyCommittedGeneratedCushionTerminal(
            GeneratedStructureEntityMutationCoordinator.CommittedCushionMutation committed) {
        if (!generatedCushionOperationMayProceed()) return false;
        if (!(committed.durable() instanceof WorldGeneratedStructureEntityState
                .CushionRuntimeSnapshot dead)
                || dead.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.DEAD
                || committed.committedGroundRevision() <= 0L) {
            throw new IllegalStateException("invalid committed generated Cushion terminal");
        }
        long expectedGroundRevision = committed.committedGroundRevision() - 1L;
        synchronized (this) {
            if (groundRevision != expectedGroundRevision
                    && groundRevision != committed.committedGroundRevision()) {
                throw new IllegalStateException("committed generated Cushion ground revision drift");
            }
        }
        var item = itemSystem.settlementDropSnapshot(committed.groundEntityId(),
                (short) Blocks.LIME_CUSHION, dead.transform().x(),
                dead.transform().y() + CushionSystem.HEIGHT, dead.transform().z());
        var delta = generatedStructureEntities.install(List.of(dead));
        reconcileGeneratedEntityVisibility(activeSimulationChunks, delta.updated());
        flushGeneratedEntityMessages();
        if (!pendingGeneratedEntityMessages.isEmpty()) {
            throw new IllegalStateException(
                    "committed generated Cushion removal is awaiting ordered publication");
        }
        synchronized (this) {
            groundRevision = committed.committedGroundRevision();
            groundSettlementInFlight = false;
        }
        itemSystem.commitSettlementDrop(item);
        return true;
    }

    private static GeneratedCushionUpdateIdentity generatedCushionUpdateIdentity(
            GeneratedStructureEntityMutationCoordinator.CommittedCushionMutation committed) {
        if (!(committed.durable() instanceof WorldGeneratedStructureEntityState
                .CushionRuntimeSnapshot live) || committed.terminal()) {
            throw new IllegalArgumentException("generated Cushion update identity requires a live update");
        }
        return new GeneratedCushionUpdateIdentity(live.binding().entityId(),
                live.revision() - 1L, live.revision());
    }

    private static GeneratedCushionUpdateIdentity generatedCushionUpdateIdentity(
            GeneratedCushionPersistencePublication publication) {
        return new GeneratedCushionUpdateIdentity(publication.entityId(),
                publication.current().revision(), publication.next().revision());
    }

    private boolean hasPendingCommittedGeneratedCushionUpdate(
            GeneratedCushionUpdateIdentity identity) {
        for (GeneratedStructureEntityMutationCoordinator.CommittedCushionMutation committed
                : pendingCommittedGeneratedCushionMutations) {
            if (!committed.terminal()
                    && identity.equals(generatedCushionUpdateIdentity(committed))) return true;
        }
        return false;
    }

    private void consumeGeneratedCushionPublication(GeneratedCushionUpdateIdentity identity) {
        synchronized (generatedCushionPersistencePublications) {
            GeneratedCushionPersistencePublication publication =
                    generatedCushionPersistencePublications.get(identity.entityId());
            if (publication != null && identity.equals(generatedCushionUpdateIdentity(publication))) {
                generatedCushionPersistencePublications.remove(identity.entityId(), publication);
                publication.state = PublicationState.CONSUMED;
            }
        }
    }

    private boolean actorRidingAnotherCushion(String actor, long targetEntityId) {
        for (var cushion : cushionSystem.welcomeSnapshot()) {
            if (actor.equals(cushion.getRider())) return true;
        }
        for (WorldGeneratedStructureEntityState.RuntimeSnapshot raw
                : generatedStructureEntities.snapshotsByKind(
                        com.gameexpert.engine.persistence.finalcarrier.structureentity
                                .GeneratedStructureEntityFacts.Kind.CUSHION)) {
            var cushion = (WorldGeneratedStructureEntityState.CushionRuntimeSnapshot) raw;
            if (cushion.lifecycle() == WorldGeneratedStructureEntityState.Lifecycle.LIVE
                    && cushion.binding().entityId() != targetEntityId
                    && actor.equals(cushion.rider())) return true;
        }
        return false;
    }

    private boolean generatedCushionHasForeignCollision(
            WorldGeneratedStructureEntityState.CushionRuntimeSnapshot target) {
        double x = target.transform().x(), y = target.transform().y(), z = target.transform().z();
        for (var cushion : cushionSystem.welcomeSnapshot()) {
            if (cushionsOverlap(x, y, z, cushion.getX(), cushion.getY(), cushion.getZ())) return true;
        }
        for (WorldGeneratedStructureEntityState.RuntimeSnapshot raw
                : generatedStructureEntities.snapshotsByKind(
                        com.gameexpert.engine.persistence.finalcarrier.structureentity
                                .GeneratedStructureEntityFacts.Kind.CUSHION)) {
            var cushion = (WorldGeneratedStructureEntityState.CushionRuntimeSnapshot) raw;
            if (cushion.lifecycle() == WorldGeneratedStructureEntityState.Lifecycle.LIVE
                    && cushion.binding().entityId() != target.binding().entityId()
                    && cushionsOverlap(x, y, z, cushion.transform().x(),
                            cushion.transform().y(), cushion.transform().z())) return true;
        }
        return false;
    }

    private static boolean cushionsOverlap(double ax, double ay, double az,
            double bx, double by, double bz) {
        return Math.abs(ax - bx) < CushionSystem.WIDTH
                && Math.abs(az - bz) < CushionSystem.WIDTH
                && ay < by + CushionSystem.HEIGHT && by < ay + CushionSystem.HEIGHT;
    }

    /** Opaque runtime-owned semantic evidence; it deliberately exposes no fields or constructor. */
    static final class GeneratedCushionActionEvidence {
        private GeneratedCushionActionEvidence() {}
    }

    record GeneratedCushionActionResult(boolean accepted, boolean retryable,
            GeneratedCushionActionPolicy.Action action, long entityId,
            double x, double y, double z, List<String> failureCodes) {
        GeneratedCushionActionResult {
            java.util.Objects.requireNonNull(action, "generated Cushion action");
            failureCodes = List.copyOf(failureCodes);
            if (accepted != failureCodes.isEmpty() || accepted && retryable) {
                throw new IllegalArgumentException(
                        "generated Cushion result must be terminal accepted or contain failures");
            }
        }

        private static GeneratedCushionActionResult accepted(
                GeneratedCushionActionPolicy.Action action,
                WorldGeneratedStructureEntityState.CushionRuntimeSnapshot current) {
            return result(true, false, action, current, List.of());
        }

        private static GeneratedCushionActionResult rejected(
                GeneratedCushionActionPolicy.Action action,
                WorldGeneratedStructureEntityState.CushionRuntimeSnapshot current,
                List<GeneratedCushionActionPolicy.Rejection> rejections) {
            return result(false, false, action, current,
                    rejections.stream().map(Enum::name).toList());
        }

        private static GeneratedCushionActionResult failed(
                GeneratedCushionActionPolicy.Action action,
                WorldGeneratedStructureEntityState.RuntimeSnapshot raw,
                boolean retryable, String failureCode) {
            WorldGeneratedStructureEntityState.CushionRuntimeSnapshot current =
                    raw instanceof WorldGeneratedStructureEntityState.CushionRuntimeSnapshot value
                            ? value : null;
            return result(false, retryable, action, current, List.of(failureCode));
        }

        private static GeneratedCushionActionResult result(boolean accepted, boolean retryable,
                GeneratedCushionActionPolicy.Action action,
                WorldGeneratedStructureEntityState.CushionRuntimeSnapshot current,
                List<String> failures) {
            return new GeneratedCushionActionResult(accepted, retryable, action,
                    current == null ? -1L : current.binding().entityId(),
                    current == null ? Double.NaN : current.transform().x(),
                    current == null ? Double.NaN : current.transform().y(),
                    current == null ? Double.NaN : current.transform().z(), failures);
        }
    }

    private record PendingGeneratedCushionRiderCleanup(long entityId, String actor,
            GeneratedCushionActionPolicy.Action action) {
        private PendingGeneratedCushionRiderCleanup {
            if (entityId <= 0L || actor == null
                    || action != GeneratedCushionActionPolicy.Action.RIDER_LOGOUT
                    && action != GeneratedCushionActionPolicy.Action.RIDER_DEATH) {
                throw new IllegalArgumentException("invalid generated Cushion rider cleanup");
            }
        }
    }

    private record DrainingCushionLogoutAdmission(long entityId, String actor,
            WorldGeneratedStructureEntityState.CushionRuntimeSnapshot current,
            GeneratedCushionActionPolicy.Action action) {
        private DrainingCushionLogoutAdmission {
            if (entityId <= 0L || actor == null || current == null
                    || action != GeneratedCushionActionPolicy.Action.RIDER_LOGOUT
                    || current.binding().entityId() != entityId
                    || current.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE
                    || !actor.equals(current.rider())) {
                throw new IllegalArgumentException("invalid draining generated Cushion logout admission");
            }
        }

        private boolean matches(WorldGeneratedStructureEntityState.CushionRuntimeSnapshot candidate,
                String candidateActor, GeneratedCushionActionPolicy.Action candidateAction) {
            return actor.equals(candidateActor) && action == candidateAction
                    && current.equals(candidate)
                    && entityId == candidate.binding().entityId();
        }
    }

    private synchronized void admitDrainingCushionLogout(
            WorldGeneratedStructureEntityState.CushionRuntimeSnapshot current,
            String actor, GeneratedCushionActionPolicy.Action action) {
        if (terminalPhase != TerminalPhase.DRAINING
                || action != GeneratedCushionActionPolicy.Action.RIDER_LOGOUT) return;
        drainingCushionLogoutAdmissions.put(current.binding().entityId(),
                new DrainingCushionLogoutAdmission(
                        current.binding().entityId(), actor, current, action));
    }

    private synchronized boolean ownsDrainingCushionLogoutAdmission(
            WorldGeneratedStructureEntityState.CushionRuntimeSnapshot current,
            String actor, GeneratedCushionActionPolicy.Action action) {
        DrainingCushionLogoutAdmission admission =
                drainingCushionLogoutAdmissions.get(current.binding().entityId());
        return admission != null && admission.matches(current, actor, action)
                && terminalPhase == TerminalPhase.DRAINING;
    }

    private synchronized void clearDrainingCushionLogout(long entityId, String actor,
            GeneratedCushionActionPolicy.Action action) {
        DrainingCushionLogoutAdmission admission = drainingCushionLogoutAdmissions.get(entityId);
        if (admission != null && admission.actor().equals(actor) && admission.action() == action) {
            drainingCushionLogoutAdmissions.remove(entityId, admission);
        }
    }

    /** Exact coordinator claim containing the hidden request reconstructed by this runtime. */
    static final class GeneratedCushionActionClaim {
        private final GeneratedCushionActionEvidence evidence;
        private final WorldGeneratedStructureEntityState.CushionRuntimeSnapshot current;
        private final GeneratedCushionActionPolicy.Request request;

        private GeneratedCushionActionClaim(GeneratedCushionActionEvidence evidence,
                WorldGeneratedStructureEntityState.CushionRuntimeSnapshot current,
                GeneratedCushionActionPolicy.Request request) {
            this.evidence = evidence;
            this.current = current;
            this.request = request;
        }

        WorldGeneratedStructureEntityState.CushionRuntimeSnapshot current() { return current; }
        GeneratedCushionActionPolicy.Request request() { return request; }
    }

    private static final class GeneratedCushionActionEvidenceState {
        private final WorldGeneratedStructureEntityState.CushionRuntimeSnapshot current;
        private final GeneratedCushionActionPolicy.Request request;
        private GeneratedCushionActionClaim claim;

        private GeneratedCushionActionEvidenceState(
                WorldGeneratedStructureEntityState.CushionRuntimeSnapshot current,
                GeneratedCushionActionPolicy.Request request) {
            this.current = current;
            this.request = request;
        }
    }

    /**
     * Issues one exact owner-bound cushion publication without changing the live registry.
     * A newer preparation supersedes an uncommitted token; a committed queued token must drain.
     */
    GeneratedCushionPersistencePublication prepareGeneratedCushionPersistencePublication(
            long authoritativeEntityId, String rider, String customName) {
        WorldGeneratedStructureEntityState.RuntimeSnapshot raw =
                generatedStructureEntities.snapshotByEntityId(authoritativeEntityId);
        if (!(raw instanceof WorldGeneratedStructureEntityState.CushionRuntimeSnapshot current)
                || current.lifecycle() != WorldGeneratedStructureEntityState.Lifecycle.LIVE) {
            throw new IllegalStateException("live generated cushion authority is required");
        }
        boolean admittedTerminalLogout = terminalPhase == TerminalPhase.DRAINING
                && rider == null && current.rider() != null
                && java.util.Objects.equals(customName, current.customName())
                && ownsDrainingCushionLogoutAdmission(current, current.rider(),
                        GeneratedCushionActionPolicy.Action.RIDER_LOGOUT);
        if (terminalPhase != TerminalPhase.RUNNING && !admittedTerminalLogout) {
            throw new IllegalStateException("generated cushion publication after terminal admission");
        }
        String validatedRider = optionalGeneratedCushionText(rider, 12, "rider");
        if (validatedRider != null && !validatedRider.matches("[A-Za-z0-9_]{2,12}")) {
            throw new IllegalArgumentException("invalid generated cushion rider");
        }
        String validatedName = optionalGeneratedCushionText(customName, 64, "custom name");
        if (current.revision() == Long.MAX_VALUE) {
            throw new IllegalStateException("generated cushion revision is exhausted");
        }
        var next = new WorldGeneratedStructureEntityState.CushionRuntimeSnapshot(
                current.binding(), Math.addExact(current.revision(), 1L),
                WorldGeneratedStructureEntityState.Lifecycle.LIVE, current.transform(),
                current.color(), current.blockX(), current.blockY(), current.blockZ(),
                current.invulnerable(), validatedRider, validatedName);
        var publication = new GeneratedCushionPersistencePublication(current, next);
        synchronized (generatedCushionPersistencePublications) {
            GeneratedCushionPersistencePublication existing =
                    generatedCushionPersistencePublications.get(authoritativeEntityId);
            if (existing != null && existing.state == PublicationState.QUEUED) {
                throw new IllegalStateException(
                        "committed generated cushion publication is awaiting owner drain");
            }
            if (existing != null) existing.state = PublicationState.REVOKED;
            generatedCushionPersistencePublications.put(authoritativeEntityId, publication);
        }
        synchronized (this) {
            boolean stillAdmitted = terminalPhase == TerminalPhase.RUNNING
                    || admittedTerminalLogout && terminalPhase == TerminalPhase.DRAINING;
            if (!stillAdmitted) {
                synchronized (generatedCushionPersistencePublications) {
                    if (generatedCushionPersistencePublications.get(authoritativeEntityId)
                            == publication) {
                        generatedCushionPersistencePublications.remove(authoritativeEntityId);
                    }
                    publication.state = PublicationState.REVOKED;
                }
                throw new IllegalStateException(
                        "generated cushion publication crossed terminal admission");
            }
        }
        return publication;
    }

    /** True only while this exact token still owns the entity and its runtime baseline. */
    boolean ownsGeneratedCushionPersistencePublication(
            GeneratedCushionPersistencePublication publication) {
        if (publication == null) return false;
        synchronized (generatedCushionPersistencePublications) {
            return generatedCushionPersistencePublications.get(publication.entityId()) == publication
                    && publication.state == PublicationState.PREPARED
                    && publication.current.equals(generatedStructureEntities
                            .snapshotByEntityId(publication.entityId()));
        }
    }

    /** Nonthrowing, idempotent post-commit handoff; only the owner drain mutates live state. */
    void enqueueCommittedGeneratedCushionPersistencePublication(
            GeneratedCushionPersistencePublication publication) {
        if (publication == null) return;
        boolean admitted = false;
        synchronized (generatedCushionPersistencePublications) {
            if (!generatedCushionOperationMayProceed()) {
                if (generatedCushionPersistencePublications.get(publication.entityId())
                        == publication) {
                    generatedCushionPersistencePublications.remove(publication.entityId());
                }
                publication.state = PublicationState.CONSUMED;
                log.warn("Committed generated cushion publication left for recovery after terminal boundary: "
                                + "world={}, entity={}", worldId, publication.entityId());
                return;
            }
            if (generatedCushionPersistencePublications.get(publication.entityId()) != publication
                    || publication.state == PublicationState.REVOKED
                    || publication.state == PublicationState.CONSUMED) return;
            if (publication.state == PublicationState.QUEUED) return;
            publication.state = PublicationState.QUEUED;
            admitted = true;
        }
        if (admitted && !enqueuePersistenceCompletion(
                () -> applyGeneratedCushionPersistencePublication(publication),
                () -> retryOrDiscardGeneratedCushionPersistencePublication(publication))) {
            synchronized (generatedCushionPersistencePublications) {
                if (generatedCushionPersistencePublications.get(publication.entityId())
                        == publication) {
                    generatedCushionPersistencePublications.remove(publication.entityId(), publication);
                    publication.state = PublicationState.CONSUMED;
                }
            }
        }
    }

    /** Releases an uncommitted token after rollback, rejection, or coordinator supersession. */
    void revokeGeneratedCushionPersistencePublication(
            GeneratedCushionPersistencePublication publication) {
        if (publication == null) return;
        synchronized (generatedCushionPersistencePublications) {
            if (generatedCushionPersistencePublications.get(publication.entityId()) == publication
                    && publication.state == PublicationState.PREPARED) {
                generatedCushionPersistencePublications.remove(publication.entityId());
                publication.state = PublicationState.REVOKED;
            }
        }
    }

    private void applyGeneratedCushionPersistencePublication(
            GeneratedCushionPersistencePublication publication) {
        boolean consumed = false;
        try {
            synchronized (generatedCushionPersistencePublications) {
                if (generatedCushionPersistencePublications.get(publication.entityId())
                        != publication || publication.state != PublicationState.QUEUED
                        || !generatedCushionOperationMayProceed()) return;
            }
            GeneratedCushionUpdateIdentity identity = generatedCushionUpdateIdentity(publication);
            if (appliedGeneratedCushionUpdates.contains(identity)
                    || hasPendingCommittedGeneratedCushionUpdate(identity)) {
                flushGeneratedEntityMessages();
                consumed = true;
                return;
            }
            WorldGeneratedStructureEntityState.RuntimeSnapshot current =
                    generatedStructureEntities.snapshotByEntityId(publication.entityId());
            if (!publication.current.equals(current)) {
                consumed = true;
                return; // Recovery is the fallback.
            }
            if (!generatedCushionOperationMayProceed()) return;
            GeneratedStructureEntitySystem.InstallDelta delta =
                    generatedStructureEntities.install(List.of(publication.next));
            reconcileGeneratedEntityVisibility(activeSimulationChunks, delta.updated());
            flushGeneratedEntityMessages();
            appliedGeneratedCushionUpdates.add(identity);
            consumed = true;
        } catch (RuntimeException | Error rejected) {
            log.error("Committed generated cushion publication deferred to recovery: world={}, entity={}",
                    worldId, publication.entityId(), rejected);
            if (terminalPhase == TerminalPhase.ABORTED || terminalPhase == TerminalPhase.DISPOSED) {
                return;
            }
            throw rejected;
        } finally {
            if (consumed) {
                synchronized (generatedCushionPersistencePublications) {
                    if (generatedCushionPersistencePublications.get(publication.entityId())
                            == publication) {
                        generatedCushionPersistencePublications.remove(publication.entityId());
                    }
                    publication.state = PublicationState.CONSUMED;
                }
            }
        }
    }

    /** Generic terminal bookkeeping for a publication whose live world can no longer be touched. */
    private void discardGeneratedCushionPersistencePublication(
            GeneratedCushionPersistencePublication publication) {
        synchronized (generatedCushionPersistencePublications) {
            if (generatedCushionPersistencePublications.get(publication.entityId()) == publication) {
                generatedCushionPersistencePublications.remove(publication.entityId());
            }
            publication.state = PublicationState.CONSUMED;
        }
    }

    private void retryOrDiscardGeneratedCushionPersistencePublication(
            GeneratedCushionPersistencePublication publication) {
        if (terminalizingPersistenceCompletions
                || terminalPhase == TerminalPhase.ABORTED || terminalPhase == TerminalPhase.DISPOSED) {
            discardGeneratedCushionPersistencePublication(publication);
            return;
        }
        boolean retry = false;
        synchronized (generatedCushionPersistencePublications) {
            if (generatedCushionPersistencePublications.get(publication.entityId()) != publication
                    || publication.state != PublicationState.QUEUED) return;
            retry = true;
        }
        if (retry && !enqueuePersistenceCompletion(
                () -> applyGeneratedCushionPersistencePublication(publication),
                () -> retryOrDiscardGeneratedCushionPersistencePublication(publication))) {
            synchronized (generatedCushionPersistencePublications) {
                if (generatedCushionPersistencePublications.get(publication.entityId())
                        == publication) {
                    generatedCushionPersistencePublications.remove(publication.entityId(), publication);
                    publication.state = PublicationState.CONSUMED;
                }
            }
        }
    }

    private static String optionalGeneratedCushionText(String value, int maxLength, String label) {
        if (value == null) return null;
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException("invalid generated cushion " + label);
        }
        return value;
    }

    /** Immutable one-use proof of the exact owner-runtime transition prepared by this world. */
    static final class GeneratedCushionPersistencePublication {
        private final WorldGeneratedStructureEntityState.CushionRuntimeSnapshot current;
        private final WorldGeneratedStructureEntityState.CushionRuntimeSnapshot next;
        private PublicationState state = PublicationState.PREPARED;

        private GeneratedCushionPersistencePublication(
                WorldGeneratedStructureEntityState.CushionRuntimeSnapshot current,
                WorldGeneratedStructureEntityState.CushionRuntimeSnapshot next) {
            this.current = current;
            this.next = next;
        }

        WorldGeneratedStructureEntityState.CushionRuntimeSnapshot current() {
            return current;
        }
        WorldGeneratedStructureEntityState.CushionRuntimeSnapshot next() { return next; }
        long entityId() { return current.binding().entityId(); }
    }

    private enum PublicationState { PREPARED, QUEUED, REVOKED, CONSUMED }

    private record GeneratedCushionUpdateIdentity(long entityId, long expectedRevision,
            long nextRevision) {
        private GeneratedCushionUpdateIdentity {
            if (entityId <= 0L || expectedRevision < 0L
                    || nextRevision != expectedRevision + 1L) {
                throw new IllegalArgumentException("invalid generated Cushion update identity");
            }
        }
    }

    private enum TerminalPhase { RUNNING, DRAINING, ABORTED, DISPOSED }

    enum TerminalOutcomeKind { NONE, DRAINED, CALLBACK_FAILURE, DEADLINE, ABORTED }

    record TerminalOutcome(TerminalOutcomeKind kind, long callbacksRun,
            List<Throwable> failures,
            Set<FinalCarrierTickScheduler.DrainStatus> schedulerStatuses) {
        TerminalOutcome(TerminalOutcomeKind kind, long callbacksRun, List<Throwable> failures) {
            this(kind, callbacksRun, failures, Set.of());
        }

        TerminalOutcome {
            java.util.Objects.requireNonNull(kind, "terminal outcome kind");
            if (callbacksRun < 0L) throw new IllegalArgumentException("negative callback count");
            failures = List.copyOf(failures);
            schedulerStatuses = Set.copyOf(schedulerStatuses);
        }
    }

    enum FinalCarrierTickPublicationOutcome { NONE, COMMITTED, TERMINAL, PARTIAL }

    static final class FinalCarrierTickPublicationException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final FinalCarrierTickPublicationOutcome outcome;
        private final int appliedBlocks;

        private FinalCarrierTickPublicationException(
                FinalCarrierTickPublicationOutcome outcome, int appliedBlocks,
                String message) {
            super(message);
            this.outcome = outcome;
            this.appliedBlocks = appliedBlocks;
        }

        private FinalCarrierTickPublicationException(
                FinalCarrierTickPublicationOutcome outcome, int appliedBlocks,
                String message, Throwable cause) {
            super(message, cause);
            this.outcome = outcome;
            this.appliedBlocks = appliedBlocks;
        }

        FinalCarrierTickPublicationOutcome outcome() { return outcome; }
        int appliedBlocks() { return appliedBlocks; }
    }

    private static final class FinalCarrierTickPublicationProgress {
        private final FinalCarrierTickScheduler.TickMutation mutation;
        private int nextBlockIndex;
        private boolean replacementDropApplied;

        private FinalCarrierTickPublicationProgress(FinalCarrierTickScheduler.TickMutation mutation) {
            this.mutation = mutation;
        }
    }

    /**
     * A writer completion can contain both live publication and terminal bookkeeping.  Once the
     * runtime is disposed only the latter is safe to run; a live-only callback is consumed without
     * invoking it.  ABORTED uses the same rule for any bookkeeping that was already admitted.
     */
    private static final class PersistenceCompletion {
        private final Runnable live;
        private final Runnable generic;
        private final boolean liveAdmittedAtEnqueue;
        private final AtomicBoolean liveStarted = new AtomicBoolean();
        private final AtomicBoolean genericCompleted = new AtomicBoolean();

        private PersistenceCompletion(Runnable live, Runnable generic,
                boolean liveAdmittedAtEnqueue) {
            this.live = live;
            this.generic = generic;
            this.liveAdmittedAtEnqueue = live != null && liveAdmittedAtEnqueue;
        }

        private boolean admitLive() {
            return liveAdmittedAtEnqueue && liveStarted.compareAndSet(false, true);
        }

        private List<Throwable> run(TerminalPhase phase, boolean liveAdmitted) {
            List<Throwable> failures = new ArrayList<>();
            if (phase == TerminalPhase.DISPOSED || phase == TerminalPhase.ABORTED) {
                failures.addAll(runGenericOnly());
                return failures;
            }
            if (liveAdmitted || liveAdmittedAtEnqueue
                    && liveStarted.compareAndSet(false, true)) {
                try {
                    live.run();
                } catch (Throwable failure) {
                    failures.add(failure);
                    failures.addAll(runGenericOnly());
                }
            } else if (!liveAdmittedAtEnqueue) {
                // 승인 울타리 뒤에 DRAINING 중 들어온 완료는 live 쪽이 없다. 여기서 generic을
                // 돌리지 않으면 그 장부가 종료/접속 종료 정산에서 통째로 사라진다.
                failures.addAll(runGenericOnly());
            }
            return failures;
        }

        private List<Throwable> runGenericOnly() {
            if (generic == null || !genericCompleted.compareAndSet(false, true)) {
                return List.of();
            }
            try {
                generic.run();
                return List.of();
            } catch (Throwable failure) {
                return List.of(failure);
            }
        }

        private boolean liveWasStarted() {
            return liveStarted.get();
        }
    }

    List<GeneratedEntitySnapshot> visibleGeneratedEntitySnapshots() {
        return generatedEntityWelcomeView.snapshots();
    }

    GeneratedEntityWelcomeView generatedEntityWelcomeView() {
        return generatedEntityWelcomeView;
    }

    static long nextGeneratedEntitySequence(long current) {
        if (current < 0L || current == Long.MAX_VALUE) {
            throw new IllegalStateException("generated entity sequence is exhausted");
        }
        return current + 1L;
    }

    static final class GeneratedEntityWelcomeView {
        private final List<GeneratedEntitySnapshot> snapshots;
        private final long sequence;

        private GeneratedEntityWelcomeView(
                List<GeneratedEntitySnapshot> snapshots, long sequence) {
            if (sequence < 0L) throw new IllegalArgumentException("non-negative sequence required");
            this.snapshots = List.copyOf(snapshots);
            this.sequence = sequence;
        }

        List<GeneratedEntitySnapshot> snapshots() { return snapshots; }
        long sequence() { return sequence; }
    }

    private static final class PendingGeneratedEntityMessage {
        private final Object message;
        private final long sequence;

        private PendingGeneratedEntityMessage(Object message, long sequence) {
            this.message = java.util.Objects.requireNonNull(message, "generated entity message");
            if (sequence <= 0L) throw new IllegalArgumentException("positive sequence required");
            this.sequence = sequence;
        }
    }

    private static ArchaeologyBrushableAggregate.Installation installation(
            ArchaeologyBrushableAggregate aggregate) {
        return new ArchaeologyBrushableAggregate.Installation(
                aggregate.installationIdentity(), aggregate.exactTableKey(), aggregate.rawSeed(),
                aggregate.target(), aggregate.canonicalReceiptBytes());
    }

    ArchaeologyBrushableAggregate archaeologyBrushableAt(int x, int y, int z) {
        return archaeologyBrushables.get(new BlockPos(x, y, z));
    }

    record ArchaeologyLootSettlement(
            ArchaeologyLootResolver.ResolvedLoot loot, boolean newlyConsumed) {
        ArchaeologyLootSettlement {
            java.util.Objects.requireNonNull(loot, "archaeology loot");
        }
    }

    /**
     * Resolves and durably consumes one exact representable ARCH result before brush completion
     * mutates the world or queues a ground entity. Missing authority, unknown tables, malformed
     * components and persistence conflicts all fail closed.
     */
    ArchaeologyLootSettlement consumeArchaeologyLoot(int x, int y, int z) {
        if (terminalPhase != TerminalPhase.RUNNING) return null;
        if (groundMutationSettlements != null && !groundSettlementAvailable()) return null;
        ArchaeologyBrushableAggregate aggregate = archaeologyBrushableAt(x, y, z);
        ArchaeologyResultPersistenceForTest testPersistence = archaeologyResultPersistenceForTest;
        if (aggregate == null || aggregate.revoked()
                || finalCarrierPersistence == null && testPersistence == null
                || finalCarrierPersistence != null && groundMutationSettlements == null) {
            return null;
        }
        int currentBlock = WorldTickLoop.residentBlockType(accessor, x, y, z);
        int currentState = blockStates.get(x, y, z, currentBlock);
        String currentExact = exactArchaeologyBlockState(currentBlock, currentState);
        if (currentExact == null || !currentExact.equals(aggregate.currentExactBlockState())) {
            return null;
        }
        ArchaeologyLootResolver.ResolvedLoot loot;
        try {
            loot = ArchaeologyLootResolver.pinned().resolve(
                    aggregate.exactTableKey(), aggregate.rawSeed());
        } catch (IllegalArgumentException invalidResult) {
            return null;
        }
        long expectedGroundRevision = groundRevision();
        long proposedEntityId = groundEntityIds.nextId();
        if (proposedEntityId <= 0L
                || proposedEntityId > GroundEntityIdAllocator.MAX_ALLOCATABLE_ID) {
            return null;
        }
        com.gameexpert.engine.persistence.finalcarrier.FinalCarrierPersistenceService
                .ArchaeologyResultSettlement settlement;
        try {
            if (finalCarrierPersistence != null) {
                settlement = finalCarrierPersistence.consumeArchaeologyResult(
                        worldId, x, y, z, aggregate.installationIdentity(), currentExact,
                        aggregate.targetRevision(), loot.resultIdentity(), proposedEntityId);
            } else {
                settlement = testPersistence.consume(
                        worldId, x, y, z, aggregate.installationIdentity(), currentExact,
                        aggregate.targetRevision(), loot.resultIdentity(), proposedEntityId);
            }
        } catch (RuntimeException rejected) {
            return null;
        }
        if (settlement == null) return null;
        return publishCommittedArchaeologyResult(aggregate, loot, settlement,
                x, y, z, currentExact, expectedGroundRevision, proposedEntityId);
    }

    /** Applies an authenticated ARCH result only after the joining transaction has returned. */
    private synchronized ArchaeologyLootSettlement publishCommittedArchaeologyResult(
            ArchaeologyBrushableAggregate aggregate,
            ArchaeologyLootResolver.ResolvedLoot loot,
            com.gameexpert.engine.persistence.finalcarrier.FinalCarrierPersistenceService
                    .ArchaeologyResultSettlement settlement,
            int x, int y, int z, String currentExact,
            long expectedGroundRevision, long proposedEntityId) {
        if (terminalPhase != TerminalPhase.RUNNING || aggregate.revoked()
                || groundRevision != expectedGroundRevision) {
            return null;
        }
        boolean alreadyConsumed = aggregate.consumed();
        long expectedCommittedRevision;
        try {
            expectedCommittedRevision = alreadyConsumed
                    ? expectedGroundRevision
                    : WorldGroundRevision.requireSuccessor(expectedGroundRevision);
        } catch (IllegalArgumentException exhausted) {
            return null;
        }
        if (settlement.committedGroundRevision() != expectedCommittedRevision) {
            return null;
        }
        ArchaeologyBrushableAggregate.ConsumeOutcome expectedOutcome = alreadyConsumed
                ? ArchaeologyBrushableAggregate.ConsumeOutcome.ALREADY_CONSUMED
                : ArchaeologyBrushableAggregate.ConsumeOutcome.CONSUMED;
        if (settlement.outcome() != expectedOutcome
                || !validArchaeologyGroundItem(settlement.groundItem(), loot, x, y, z)
                || !alreadyConsumed && settlement.groundItem().entityId() != proposedEntityId) {
            return null;
        }
        try {
            aggregate.consume(loot.resultIdentity(), currentExact, aggregate.targetRevision());
            // The archaeology insert committed in the shared durable item/XP namespace.  Claim
            // that exact identity before exposing the row locally so a later allocation in this
            // still-connected runtime cannot reuse it.  Replays of the same item-kind claim are
            // idempotent; cross-kind reuse fails closed.
            groundEntityIds.claimSettlementIdentity(
                    settlement.groundItem().entityId(), GroundEntityKind.ITEM);
            itemSystem.commitSettlementDrop(settlement.groundItem());
        } catch (IllegalArgumentException | IllegalStateException conflict) {
            return null;
        }
        // This is the owner-side post-commit callback: durable revision, local ARCH aggregate,
        // and the live ground item become visible as one publication boundary.
        groundRevision = settlement.committedGroundRevision();
        return new ArchaeologyLootSettlement(loot, !alreadyConsumed);
    }

    private static boolean validArchaeologyGroundItem(
            com.gameexpert.ground.dto.GroundItemSnapshot item,
            ArchaeologyLootResolver.ResolvedLoot loot, int x, int y, int z) {
        if (item == null || loot == null) return false;
        var stack = loot.stack();
        return item.itemType() == stack.itemType()
                && item.count() == stack.count()
                && item.durability() == stack.durability()
                && item.enchantments() == stack.enchantments()
                && item.mapId() == stack.mapId()
                && item.shulkerId() == stack.shulkerId()
                && java.util.Objects.equals(item.bucketMobData(), stack.bucketMobData())
                && java.util.Objects.equals(item.itemComponentData(), stack.itemComponentData())
                && Double.doubleToLongBits(item.x()) == Double.doubleToLongBits(x + 0.5)
                && Double.doubleToLongBits(item.y()) == Double.doubleToLongBits(y + 0.5)
                && Double.doubleToLongBits(item.z()) == Double.doubleToLongBits(z + 0.5)
                && item.velocityX() == 0.0 && item.velocityY() == 0.2
                && item.velocityZ() == 0.0 && !item.playerThrown()
                && item.age() == 0 && item.pickupDelay() == ItemEntitySystem.PICKUP_MIN_AGE
                && item.excludedAllayId() == 0L;
    }

    private void acknowledgeFinalCarrierLane(PendingFinalCarrierClaim pending,
            TerrainAccessor.FinalLiveCarrierLane lane) {
        if (!ownerTurnMayContinue()) return;
        if (!accessor.acknowledgeFinalLiveCarrierLane(pending.claim, lane)) {
            throw new IllegalStateException("stale final-carrier " + lane + " delivery");
        }
        pending.remaining.remove(lane);
    }

    private void drainFinalCarrierTickAdmissionCompletions() {
        if (ownerQueueRequired()) {
            requestTerminalOwnerDrain();
            return;
        }
        markOwnerThread();
        FinalCarrierTickAdmissionCompletion completion;
        int drained = 0;
        while (ownerTurnMayContinue() && drained++ < 64
                && (completion = finalCarrierTickAdmissionCompletions.poll()) != null) {
            if (architectureTurn != null) architectureTurn.finalCarrierAdmissionCompletions++;
            if (!ownerTurnMayContinue()) break;
            inFlightFinalCarrierTickAdmissions.remove(completion.key);
            PendingFinalCarrierClaim current = pendingFinalCarrierClaims.get(
                    completion.key.chunkKey);
            if (current != completion.pending
                    || current.generation != completion.key.generation
                    || !current.remaining.contains(completion.key.lane)) {
                continue;
            }
            if (completion.failure != null) {
                if (completion.failureKind == FinalCarrierTickAdmissionFailure.TRANSIENT) {
                    deferFinalCarrierTickAdmission(completion.key);
                    log.warn("Final-carrier {} lane admission deferred for chunk {},{}: {}",
                            completion.key.lane,
                            (int) (completion.key.chunkKey >> 32),
                            (int) completion.key.chunkKey, completion.failure.getMessage());
                } else if (completion.failureKind
                        == FinalCarrierTickAdmissionFailure.TERMINAL_REJECTION) {
                    acknowledgeFinalCarrierLane(current, completion.key.lane);
                    concludeFinalLiveCarrierDelivery(completion.key.chunkKey);
                    clearFinalCarrierTickAdmissionRetry(completion.key);
                    log.error("Final-carrier {} lane was terminally rejected for chunk {},{}: {}",
                            completion.key.lane,
                            (int) (completion.key.chunkKey >> 32),
                            (int) completion.key.chunkKey, completion.failure.getMessage());
                } else {
                    failedFinalCarrierTickAdmissions.add(completion.key);
                    clearFinalCarrierTickAdmissionRetry(completion.key);
                    log.error("Final-carrier {} lane remains unacknowledged for chunk {},{}: {}",
                            completion.key.lane,
                            (int) (completion.key.chunkKey >> 32),
                            (int) completion.key.chunkKey, completion.failure.getMessage());
                }
                continue;
            }
            if (completion.admission.status()
                    == FinalCarrierTickScheduler.AdmissionStatus.CAPACITY_REJECTED) {
                deferFinalCarrierTickAdmission(completion.key);
                continue;
            }
            WorldWorkEvent work = architectureWork("final-carrier-admission-install", true,
                    (int) (completion.key.chunkKey >> 32), (int) completion.key.chunkKey,
                    completion.key.generation);
            try {
                finalCarrierTickScheduler.acceptAdmission(
                        completion.prepared, completion.admission);
                if (architectureTurn != null) architectureTurn.finalCarrierAdmissionsApplied++;
                if (work != null) work.workUnits = completion.admission.durableTicks().size();
                clearFinalCarrierTickAdmissionRetry(completion.key);
                acknowledgeFinalCarrierLane(current, completion.key.lane);
                concludeFinalLiveCarrierDelivery(completion.key.chunkKey);
            } catch (Throwable invalidAdmission) {
                WorldWorkEvent.outcome(work, "failed");
                failedFinalCarrierTickAdmissions.add(completion.key);
                log.error("Final-carrier {} lane remains unacknowledged for chunk {},{}: {}",
                        completion.key.lane,
                        (int) (completion.key.chunkKey >> 32),
                        (int) completion.key.chunkKey, invalidAdmission.getMessage());
            } finally {
                WorldWorkEvent.finish(work);
            }
        }
    }

    /** Retries capacity-backpressured BTIK/FTIK lanes without performing their work on the owner. */
    private void submitPendingFinalCarrierTickAdmissions() {
        if (!ownerTurnMayContinue()) return;
        int submitted = 0;
        int inspected = Math.min(64, pendingFinalCarrierTickAdmissionOrder.size());
        while (ownerTurnMayContinue() && inspected-- > 0 && submitted < 8) {
            Long queuedKey = pendingFinalCarrierTickAdmissionOrder.poll();
            if (queuedKey == null) return;
            long key = queuedKey;
            queuedFinalCarrierTickAdmissionChunks.remove(key);
            PendingFinalCarrierClaim pending = pendingFinalCarrierClaims.get(key);
            if (pending == null) continue;
            if (pending.generation != activeChunkGeneration(key)
                    || !snapshotStructureReadyChunks.contains(key)) {
                queueFinalCarrierTickAdmission(key, pending);
                continue;
            }
            boolean submitBlock = finalCarrierTickAdmissionPreparationReady(key, pending,
                    TerrainAccessor.FinalLiveCarrierLane.BLOCK_TICKS);
            boolean submitFluid = finalCarrierTickAdmissionPreparationReady(key, pending,
                    TerrainAccessor.FinalLiveCarrierLane.FLUID_TICKS);
            if (!submitBlock && !submitFluid) {
                queueFinalCarrierTickAdmission(key, pending);
                continue;
            }
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            NeutralFinalChunk visible = accessor.carrierWithVisibleSidecars(
                    chunkX, chunkZ, pending.claim.carrier());
            String sourceFingerprint = finalCarrierSourceFingerprint(visible);
            // 틱 레인만은 정본 반송체에서 뽑는다(canonicalTickCarrier 설명 참고).
            NeutralFinalChunk canonical = canonicalTickCarrier(pending, visible);
            if (submitBlock) {
                if (!ownerTurnMayContinue()) return;
                var receipt = FinalCarrierTickScheduler.blockReceipt(worldId, chunkX, chunkZ,
                        sourceFingerprint, canonical.sidecars().blockTicks());
                if (submitFinalCarrierTickAdmission(pending, canonical,
                        TerrainAccessor.FinalLiveCarrierLane.BLOCK_TICKS, receipt,
                        clock.gameTimeMcTicks())) submitted++;
            }
            if (!ownerTurnMayContinue()) return;
            if (submitted < 8 && submitFluid) {
                var receipt = FinalCarrierTickScheduler.fluidReceipt(worldId, chunkX, chunkZ,
                        sourceFingerprint, canonical.sidecars().fluidTicks());
                if (submitFinalCarrierTickAdmission(pending, canonical,
                        TerrainAccessor.FinalLiveCarrierLane.FLUID_TICKS, receipt,
                        clock.gameTimeMcTicks())) submitted++;
            }
            if (!ownerTurnMayContinue()) return;
            queueFinalCarrierTickAdmission(key, pending);
        }
    }

    /** Avoids rebuilding carrier fingerprints for work the atomic submission guard will reject. */
    private boolean finalCarrierTickAdmissionPreparationReady(long key,
            PendingFinalCarrierClaim pending, TerrainAccessor.FinalLiveCarrierLane lane) {
        if (finalCarrierTickScheduler == null || !pending.remaining.contains(lane)) return false;
        FinalCarrierTickAdmissionKey admissionKey = new FinalCarrierTickAdmissionKey(
                key, pending.generation, lane);
        synchronized (this) {
            return lifecycleEpochCurrent(lifecycleEpoch)
                    && !failedFinalCarrierTickAdmissions.contains(admissionKey)
                    && finalCarrierTickAdmissionRetryReady(admissionKey)
                    && !inFlightFinalCarrierTickAdmissions.contains(admissionKey);
        }
    }

    private boolean finalCarrierTickAdmissionRetryReady(FinalCarrierTickAdmissionKey key) {
        return clock.gameTimeMcTicks() >= finalCarrierTickAdmissionRetryAfter.getOrDefault(key, 0L);
    }

    private void deferFinalCarrierTickAdmission(FinalCarrierTickAdmissionKey key) {
        int attempt = Math.min(6,
                finalCarrierTickAdmissionRetryAttempts.getOrDefault(key, 0) + 1);
        finalCarrierTickAdmissionRetryAttempts.put(key, attempt);
        long delay = 1L << Math.min(5, attempt - 1);
        long now = clock.gameTimeMcTicks();
        finalCarrierTickAdmissionRetryAfter.put(key,
                now > Long.MAX_VALUE - delay ? Long.MAX_VALUE : now + delay);
    }

    private void clearFinalCarrierTickAdmissionRetry(FinalCarrierTickAdmissionKey key) {
        finalCarrierTickAdmissionRetryAttempts.remove(key);
        finalCarrierTickAdmissionRetryAfter.remove(key);
    }

    /** Stable producer identity excludes delivery-local lane projection and runtime override filtering. */
    /**
     * Prepares one chunk off the world tick and, while still off it, asks the producer for the identity and
     * lane projections the owner's activation pass would otherwise request inside a tick.
     */
    private TerrainAccessor.PreparedChunk prepareChunkOffTick(int chunkX, int chunkZ) {
        TerrainAccessor.PreparedChunk prepared = accessor.prepareDetachedChunkForActivation(chunkX, chunkZ);
        warmFinalCarrierProjections(prepared.finalLiveCarrier());
        return prepared;
    }

    /** Warms the per-carrier producer answers; a failure here is left for the owner pass to report. */
    private static void warmFinalCarrierProjections(NeutralFinalChunk carrier) {
        if (carrier == null) return;
        try {
            carrier.sourceFingerprintSha256();
            for (TerrainAccessor.FinalLiveCarrierLane lane : TerrainAccessor.FinalLiveCarrierLane.values()) {
                projectFinalCarrierSidecars(carrier, lane);
            }
        } catch (RuntimeException deferred) {
            log.debug("Deferred carrier projection warm-up failure for chunk {},{}",
                    carrier.chunkX(), carrier.chunkZ(), deferred);
        }
    }

    private static String finalCarrierSourceFingerprint(NeutralFinalChunk carrier) {
        return carrier.sourceFingerprintSha256();
    }

    private boolean isSnapshotPresentationReady(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        return snapshotBaseReadyChunks.contains(key) && snapshotStructureReadyChunks.contains(key);
    }

    private void retainActivationPlanningSource(int chunkX, int chunkZ) {
        TerrainAccessor.SnapshotSource source = accessor.snapshotSource(chunkX, chunkZ);
        if (source != null) {
            long key = chunkKey(chunkX, chunkZ);
            activationPlanningSources.putIfAbsent(key, source);
            unavailableTreePlanningSources.remove(key);
        }
    }

    /**
     * The detached view covers a rare not-yet-prepared neighbor and direct-engine fixtures. The normal
     * near-field path reads the immutable source captured before natural decoration, so planning does not
     * regenerate the same terrain once per decorator and source chunk.
     */
    private SurfaceDecorator.BlockView activationPlanningView() {
        SurfaceDecorator.BlockView fallback = activationPlanningFallback;
        SurfaceDecorator.BlockView generatedFallback = activationStructurePlanningFallback;
        boolean allowDetachedFallback = !started;
        return new SurfaceDecorator.BlockView() {
            @Override
            public int getBlock(int x, int y, int z) {
                if (y < Blocks.MIN_Y) return Blocks.BEDROCK;
                if (y > Blocks.MAX_Y) return Blocks.AIR;
                TerrainAccessor.SnapshotSource source = source(x, z);
                if (source == null) return fallback.getBlock(x, y, z);
                return source.blockTypeAt(index(x, y, z));
            }

            @Override
            public int noiseBiomeAt(int x, int y, int z) {
                // Noise biome is a pure function of the world seed, so the detached view answers it
                // exactly whether or not this activation prepared the column's source.
                return fallback.noiseBiomeAt(x, y, z);
            }

            @Override
            public int terrainSurfaceHeight(int x, int z) {
                TerrainAccessor.SnapshotSource source = source(x, z);
                // Production activation owns a prepared source halo. Optional source-based
                // decorations outside it use SurfaceDecorator's exact column sampler instead of
                // synchronously generating a complete hidden chunk on the single planning worker.
                if (source == null) {
                    return allowDetachedFallback
                            ? fallback.terrainSurfaceHeight(x, z)
                            : SurfaceDecorator.UNKNOWN_SURFACE_HEIGHT;
                }
                return source.terrainSurfaceHeightAt(
                        Math.floorMod(x, Blocks.CHUNK_X),
                        Math.floorMod(z, Blocks.CHUNK_Z));
            }

            @Override
            public int getTreePlanningBlock(int x, int y, int z) {
                if (y < Blocks.MIN_Y) return Blocks.BEDROCK;
                if (y > Blocks.MAX_Y) return Blocks.AIR;
                TerrainAccessor.SnapshotSource source = source(x, z);
                if (source != null) return source.blockTypeAt(index(x, y, z));
                // Complete tree validation is rare outside the prepared halo, but rejecting one
                // unknown crown cell can remove an otherwise valid large tree. Preserve the exact
                // terrain oracle for those bounded cross-boundary checks.
                return fallback.getBlock(x, y, z);
            }

            @Override
            public int getTreeSourceBlock(int x, int y, int z) {
                return generatedSourceBlock(x, y, z);
            }

            @Override
            public int getTreeValidationBlock(int x, int y, int z) {
                if (y < Blocks.MIN_Y) return Blocks.BEDROCK;
                if (y > Blocks.MAX_Y) return Blocks.AIR;
                TerrainAccessor.SnapshotSource source = source(x, z);
                return source == null ? -1 : source.blockTypeAt(index(x, y, z));
            }

            @Override
            public int getFeaturePlanningBlock(int x, int y, int z) {
                if (y < Blocks.MIN_Y) return Blocks.BEDROCK;
                if (y > Blocks.MAX_Y) return Blocks.AIR;
                TerrainAccessor.SnapshotSource source = source(x, z);
                if (source != null) return source.blockTypeAt(index(x, y, z));
                // Source plans are cached by source coordinate, so a temporary readiness gap must
                // never become a cached omission. The bounded planning worker uses its shared
                // detached cache for the rare wide-search cell outside the prepared source halo.
                return fallback.getBlock(x, y, z);
            }

            @Override
            public int getFeatureSourceBlock(int x, int y, int z) {
                return generatedSourceBlock(x, y, z);
            }

            @Override
            public boolean isProtectedEdit(int x, int y, int z) {
                // 몹 변형도 자연 overlay 계획보다 우선한다(플레이어 편집 > 몹 변형 > 구조물 > 자연).
                return protectedDecorationEdits.contains(new BlockPos(x, y, z))
                        || mobMutationSites.contains(new BlockPos(x, y, z))
                        || fallback.isProtectedEdit(x, y, z);
            }

            private TerrainAccessor.SnapshotSource source(int x, int z) {
                return activationPlanningSources.get(chunkKey(
                        Math.floorDiv(x, Blocks.CHUNK_X),
                        Math.floorDiv(z, Blocks.CHUNK_Z)));
            }

            private int generatedSourceBlock(int x, int y, int z) {
                if (y < Blocks.MIN_Y) return Blocks.BEDROCK;
                if (y > Blocks.MAX_Y) return Blocks.AIR;
                TerrainAccessor.SnapshotSource source = source(x, z);
                if (source == null) return generatedFallback.getBlock(x, y, z);
                return source.generatedBlockTypeAt(index(x, y, z));
            }

            private int index(int x, int y, int z) {
                return Blocks.blockIndex(Math.floorMod(x, Blocks.CHUNK_X), y,
                        Math.floorMod(z, Blocks.CHUNK_Z));
            }
        };
    }

    /**
     * Structures are stable functions of generated terrain. Natural overlays are composed underneath them,
     * while persisted and live player edits are protected when placements are applied.
     */
    private SurfaceDecorator.BlockView activationStructurePlanningView() {
        SurfaceDecorator.BlockView fallback = activationStructurePlanningFallback;
        return new SurfaceDecorator.BlockView() {
            @Override
            public int getBlock(int x, int y, int z) {
                if (y < Blocks.MIN_Y) return Blocks.BEDROCK;
                if (y > Blocks.MAX_Y) return Blocks.AIR;
                TerrainAccessor.SnapshotSource source = activationPlanningSources.get(chunkKey(
                        Math.floorDiv(x, Blocks.CHUNK_X),
                        Math.floorDiv(z, Blocks.CHUNK_Z)));
                if (source == null) return fallback.getBlock(x, y, z);
                return source.generatedBlockTypeAt(Blocks.blockIndex(
                        Math.floorMod(x, Blocks.CHUNK_X), y,
                        Math.floorMod(z, Blocks.CHUNK_Z)));
            }

            @Override
            public int noiseBiomeAt(int x, int y, int z) {
                return fallback.noiseBiomeAt(x, y, z);
            }

            @Override
            public int terrainSurfaceHeight(int x, int z) {
                TerrainAccessor.SnapshotSource source = activationPlanningSources.get(chunkKey(
                        Math.floorDiv(x, Blocks.CHUNK_X),
                        Math.floorDiv(z, Blocks.CHUNK_Z)));
                if (source == null) return fallback.terrainSurfaceHeight(x, z);
                return source.terrainSurfaceHeightAt(
                        Math.floorMod(x, Blocks.CHUNK_X),
                        Math.floorMod(z, Blocks.CHUNK_Z));
            }
        };
    }

    /**
     * A target is final only when its complete 3x3 tree-source halo is known. Missing halo sources are
     * prepared on the parallel snapshot lane without becoming visible chunks, preventing both partial
     * cross-boundary trees and a larger client loading count.
     */
    private boolean activationTreeNeighborhoodReady(int chunkX, int chunkZ) {
        // Dimension providers already return their complete terrain and do not use Overworld trees.
        if (chunkProductSource instanceof com.gameexpert.world.dimension.DimensionChunkProductSource) return true;
        if (!started || !testSimulationChunks.isEmpty()) return true;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long key = chunkKey(chunkX + dx, chunkZ + dz);
                if (!activationPlanningSources.containsKey(key)
                        && !unavailableTreePlanningSources.contains(key)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasPendingSnapshotRequest(long key) {
        if (terminalPhase != TerminalPhase.RUNNING) return false;
        int chunkX = (int) (key >> 32);
        int chunkZ = (int) key;
        for (ChunkSnapshotRequest request : pendingChunkSnapshotRequestsFor(chunkX, chunkZ)) {
            if (terminalPhase != TerminalPhase.RUNNING) return false;
            if (!request.session.isOpen()) {
                completeChunkSnapshotRequest(request);
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean hasProcessablePendingSnapshotRequest(long key) {
        if (terminalPhase != TerminalPhase.RUNNING) return false;
        int chunkX = (int) (key >> 32);
        int chunkZ = (int) key;
        for (ChunkSnapshotRequest request : pendingChunkSnapshotRequestsFor(chunkX, chunkZ)) {
            if (terminalPhase != TerminalPhase.RUNNING) return false;
            if (pendingChunkSnapshotRequests.get(request.key) != request) continue;
            if (!request.session.isOpen()) {
                completeChunkSnapshotRequest(request);
                continue;
            }
            if (canProcessChunkSnapshotRequest(request)) return true;
        }
        return false;
    }

    /**
     * Rebuilds the only simulation ownership set from current connected players.  It deliberately has no global
     * chunk cap: overlapping radius-10 neighborhoods are deduplicated, while distant connected players retain
     * their own chunks.  All compaction walks the new union, not accumulated historical queues.
     */
    private Set<Long> retainedTickPublicationChunks = Set.of();
    private final Set<Long> pendingPublicationPreparations = ConcurrentHashMap.newKeySet();

    void refreshActiveSimulationChunks() {
        if (!ownerTurnMayContinue()) return;
        Set<Long> publicationChunks = finalCarrierTickScheduler == null ? Set.of()
                : finalCarrierTickScheduler.pendingPublicationChunks();
        preparePendingPublicationChunks(publicationChunks);
        if (started && playerChunkCentersUnchanged() && dragonArenaApplied == dragonArenaHeld
                && publicationChunks.equals(retainedTickPublicationChunks)) return;
        WorldWorkEvent work = architectureWork("neighborhood-rebuild", false, 0, 0, 0L);
        if (architectureTurn != null) architectureTurn.neighborhoodRebuilds++;
        try {
            rebuildActiveSimulationChunks();
            if (work != null) work.workUnits = activeSimulationChunks.size();
        } catch (RuntimeException | Error failure) {
            WorldWorkEvent.outcome(work, "failed");
            throw failure;
        } finally {
            WorldWorkEvent.finish(work);
        }
    }

    /** A restored or evicted in-flight publication can outlive every nearby player. */
    private void preparePendingPublicationChunks(Set<Long> chunks) {
        if (!started) return;
        for (long key : chunks) {
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            if (accessor.isChunkResident(chunkX, chunkZ) || !pendingPublicationPreparations.add(key)) continue;
            try {
                executeOwned(CHUNK_PREPARERS, () -> {
                    try {
                        TerrainAccessor.PreparedChunk prepared = prepareChunkOffTick(chunkX, chunkZ);
                        enqueuePersistenceCompletion(() -> {
                            try {
                                if (ownerTurnMayContinue() && finalCarrierTickScheduler.pendingPublicationChunks().contains(key)) {
                                    accessor.adoptPreparedChunkForSnapshot(prepared);
                                }
                            } finally { pendingPublicationPreparations.remove(key); }
                        }, () -> pendingPublicationPreparations.remove(key));
                    } catch (RuntimeException failure) {
                        pendingPublicationPreparations.remove(key);
                        log.warn("Pending tick publication source preparation failed: world={} chunk={},{}",
                                worldId, chunkX, chunkZ, failure);
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException full) {
                pendingPublicationPreparations.remove(key);
            }
        }
    }

    private void rebuildActiveSimulationChunks() {
        Set<Long> next = new HashSet<>();
        Map<String, Long> nextCenters = null;
        Map<String, Long> nextBedChunks = null;
        if (started) {
            nextCenters = new HashMap<>();
            nextBedChunks = new HashMap<>();
            List<String> orderedPlayers = new ArrayList<>(players.keySet());
            orderedPlayers.sort(String::compareTo);
            for (String nickname : orderedPlayers) {
                if (!ownerTurnMayContinue()) return;
                PlayerTickState player = players.get(nickname);
                if (player == null) continue;
                PlayerPose pose = player.pose();
                int centerX = Math.floorDiv((int) Math.floor(pose.getX()), Blocks.CHUNK_X);
                int centerZ = Math.floorDiv((int) Math.floor(pose.getZ()), Blocks.CHUNK_Z);
                nextCenters.put(nickname, chunkKey(centerX, centerZ));
                nextBedChunks.put(nickname, player.hasBedSpawn()
                        ? chunkKey(Math.floorDiv(player.bedSpawnX(), Blocks.CHUNK_X),
                                Math.floorDiv(player.bedSpawnZ(), Blocks.CHUNK_Z))
                        : Long.MIN_VALUE);
                addSimulationNeighborhood(next, centerX, centerZ);
            }
            // [DRAGON] DRAGON 티켓: 아레나 −8..8 청크.
            if (dragonArenaHeld && !orderedPlayers.isEmpty()) {
                for (int dz = -com.gameexpert.engine.dragon.DragonFight.ARENA_SIZE_CHUNKS;
                        dz <= com.gameexpert.engine.dragon.DragonFight.ARENA_SIZE_CHUNKS; dz++) {
                    for (int dx = -com.gameexpert.engine.dragon.DragonFight.ARENA_SIZE_CHUNKS;
                            dx <= com.gameexpert.engine.dragon.DragonFight.ARENA_SIZE_CHUNKS; dx++) {
                        next.add(chunkKey(dx, dz));
                    }
                }
            }
            dragonArenaApplied = dragonArenaHeld;
        } else {
            next.addAll(testSimulationChunks);
        }
        retainedTickPublicationChunks = finalCarrierTickScheduler == null ? Set.of()
                : finalCarrierTickScheduler.pendingPublicationChunks();
        next.addAll(retainedTickPublicationChunks);
        Set<Long> frozen = Set.copyOf(next);
        boolean bedRetentionChanged = started && !nextBedChunks.equals(activePlayerBedChunks);
        if (started) {
            activePlayerChunkCenters = Map.copyOf(nextCenters);
            activePlayerBedChunks = Map.copyOf(nextBedChunks);
            List<String> orderedPlayers = new ArrayList<>(nextCenters.keySet());
            orderedPlayers.sort(String::compareTo);
            activePlayerOrder = List.copyOf(orderedPlayers);
        }
        if (frozen.equals(activeSimulationChunks) && !bedRetentionChanged) return;

        Map<Long, Long> nextGenerations = new HashMap<>(frozen.size());
        Map<Long, Long> previousGenerations = activeChunkGenerations;
        for (long key : orderedChunkKeys(frozen)) {
            if (!ownerTurnMayContinue()) return;
            Long generation = previousGenerations.get(key);
            if (generation == null) generation = ++activationGenerationSequence;
            nextGenerations.put(key, generation);
        }
        activeChunkGenerations = Map.copyOf(nextGenerations);
        if (!ownerTurnMayContinue()) return;
        activeSimulationChunks = frozen;
        clearInactiveFinalCarrierDecoratedPots(frozen);
        reconcileGeneratedEntityVisibility(frozen, List.of());
        Set<Long> planningSources;
        if (started) {
            Set<Long> retained = new HashSet<>(oneChunkHalo(frozen));
            retained.addAll(retainedRespawnNeighborhoods());
            planningSources = Set.copyOf(retained);
        } else {
            planningSources = frozen;
        }
        treePlanningSourceChunks = planningSources;
        cancelInactiveStructurePlans(frozen);
        // The previous fallback may contain boundary terrain from an old player neighborhood. The activation
        // workers capture one immutable view per submitted plan, so replacing the reference lets the old bounded
        // cache become collectible after its in-flight plans complete.
        activationPlanningFallback = accessor.detachedPlanningView();
        activationStructurePlanningFallback = accessor.detachedGeneratedPlanningView();
        accessor.evictInactiveSimulationChunks(frozen, planningSources);
        compactReplayableInactiveState(frozen);
        preparedActivationChunks = retainActiveKeys(preparedActivationChunks, frozen);
        pendingChunkActivationPlans = retainActiveEntries(pendingChunkActivationPlans, frozen);
        pendingFinalCarrierClaims = retainActiveEntries(pendingFinalCarrierClaims, frozen);
        queuedFinalCarrierTickAdmissionChunks.retainAll(frozen);
        pendingFinalCarrierTickAdmissionOrder.removeIf(
                key -> !frozen.contains(key)
                        || !queuedFinalCarrierTickAdmissionChunks.contains(key));
        failedFinalCarrierTickAdmissions.removeIf(value -> !frozen.contains(value.chunkKey));
        finalCarrierTickAdmissionRetryAttempts.keySet()
                .removeIf(value -> !frozen.contains(value.chunkKey));
        finalCarrierTickAdmissionRetryAfter.keySet()
                .removeIf(value -> !frozen.contains(value.chunkKey));
        pendingChunkPreparations = retainActiveEntries(pendingChunkPreparations, frozen);
        chunkActivationDemands = retainActiveEntries(chunkActivationDemands, frozen);
        preparedChunkActivations = retainActiveEntries(preparedChunkActivations, frozen);
        activationPlanningFailures = retainActiveEntries(activationPlanningFailures, frozen);
        preparedChunkDemands = retainActiveEntries(preparedChunkDemands, frozen);
        snapshotBaseReadyChunks = retainActiveKeys(snapshotBaseReadyChunks, frozen);
        snapshotStructureReadyChunks = retainActiveKeys(snapshotStructureReadyChunks, frozen);
        activationPlanningSources = retainActiveEntries(activationPlanningSources, planningSources);
        unavailableTreePlanningSources = retainActiveKeys(unavailableTreePlanningSources, planningSources);
        treeSourcePreparationFailures.keySet().retainAll(planningSources);

        Map<Long, PreparedChunkActivation> retainedApplications = new LinkedHashMap<>();
        // Preserve the FIFO's established insertion order while dropping inactive keys; iterating the new HashSet
        // would reorder application work between equivalent deactivate/reactivate runs.
        for (Map.Entry<Long, PreparedChunkActivation> entry : pendingChunkActivationApplications.entrySet()) {
            if (!ownerTurnMayContinue()) return;
            if (frozen.contains(entry.getKey())) retainedApplications.put(entry.getKey(), entry.getValue());
        }
        pendingChunkActivationApplications = retainedApplications;

        Map<Long, DeferredStructureDecoration> retainedStructures = new LinkedHashMap<>();
        for (Map.Entry<Long, DeferredStructureDecoration> entry : pendingStructureDecorations.entrySet()) {
            if (!ownerTurnMayContinue()) return;
            if (frozen.contains(entry.getKey())) retainedStructures.put(entry.getKey(), entry.getValue());
        }
        pendingStructureDecorations = retainedStructures;
        refreshChunkSnapshotRequestActivity();
    }

    private void clearInactiveFinalCarrierDecoratedPots(Set<Long> activeChunks) {
        finalCarrierDecoratedPotsByPosition.entrySet().removeIf(entry ->
                !activeChunks.contains(chunkKey(Math.floorDiv(entry.getKey().x(), Blocks.CHUNK_X),
                        Math.floorDiv(entry.getKey().z(), Blocks.CHUNK_Z))));
        finalCarrierDecoratedPotInstallations.entrySet().removeIf(entry -> entry.getValue().stream()
                .anyMatch(value -> !finalCarrierDecoratedPotsByPosition.containsKey(
                        value.position())));
    }

    private static void addSimulationNeighborhood(Set<Long> target, int centerX, int centerZ) {
        for (int dz = -PROXIMITY_PREPARE_RADIUS; dz <= PROXIMITY_PREPARE_RADIUS; dz++) {
            for (int dx = -PROXIMITY_PREPARE_RADIUS; dx <= PROXIMITY_PREPARE_RADIUS; dx++) {
                target.add(chunkKey(centerX + dx, centerZ + dz));
            }
        }
    }

    private boolean playerChunkCentersUnchanged() {
        Map<String, Long> previous = activePlayerChunkCenters;
        Map<String, Long> previousBeds = activePlayerBedChunks;
        if (previous == null || previousBeds == null
                || previous.size() != players.size() || previousBeds.size() != players.size()) {
            return false;
        }
        for (Map.Entry<String, PlayerTickState> entry : players.entrySet()) {
            PlayerPose pose = entry.getValue().pose();
            int centerX = Math.floorDiv((int) Math.floor(pose.getX()), Blocks.CHUNK_X);
            int centerZ = Math.floorDiv((int) Math.floor(pose.getZ()), Blocks.CHUNK_Z);
            Long oldCenter = previous.get(entry.getKey());
            if (oldCenter == null || oldCenter.longValue() != chunkKey(centerX, centerZ)) return false;
            PlayerTickState player = entry.getValue();
            long bedChunk = player.hasBedSpawn()
                    ? chunkKey(Math.floorDiv(player.bedSpawnX(), Blocks.CHUNK_X),
                            Math.floorDiv(player.bedSpawnZ(), Blocks.CHUNK_Z))
                    : Long.MIN_VALUE;
            Long oldBedChunk = previousBeds.get(entry.getKey());
            if (oldBedChunk == null || oldBedChunk.longValue() != bedChunk) return false;
        }
        return true;
    }

    /** Keeps the 3×3 source halo for both possible respawn targets warm without simulating it. */
    private Set<Long> retainedRespawnNeighborhoods() {
        Set<Long> chunks = new HashSet<>();
        int[] defaultSpawn = worldSpawn;
        if (defaultSpawn != null) {
            addChunkNeighborhood(chunks,
                    Math.floorDiv(defaultSpawn[0], Blocks.CHUNK_X),
                    Math.floorDiv(defaultSpawn[2], Blocks.CHUNK_Z));
        }
        for (PlayerTickState player : players.values()) {
            if (!player.hasBedSpawn()) continue;
            int centerX = Math.floorDiv(player.bedSpawnX(), Blocks.CHUNK_X);
            int centerZ = Math.floorDiv(player.bedSpawnZ(), Blocks.CHUNK_Z);
            addChunkNeighborhood(chunks, centerX, centerZ);
        }
        return chunks;
    }

    private static void addChunkNeighborhood(Set<Long> chunks, int centerX, int centerZ) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                chunks.add(chunkKey(centerX + dx, centerZ + dz));
            }
        }
    }

    private void cancelInactiveStructurePlans(Set<Long> active) {
        boolean cancelled = false;
        for (Map.Entry<Long, DeferredStructureDecoration> entry : pendingStructureDecorations.entrySet()) {
            DeferredStructureDecoration deferred = entry.getValue();
            if (!active.contains(entry.getKey()) && deferred.future != null) {
                cancelled |= deferred.cancelPlanning();
            }
        }
        if (cancelled) STRUCTURE_PLANNERS.purge();
    }

    private static Set<Long> oneChunkHalo(Set<Long> chunks) {
        if (chunks.isEmpty()) return Set.of();
        Set<Long> expanded = new HashSet<>(chunks.size() + chunks.size() / 4);
        for (long key : chunks) {
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    expanded.add(chunkKey(chunkX + dx, chunkZ + dz));
                }
            }
        }
        return Set.copyOf(expanded);
    }

    /**
     * Natural decoration is deterministic and may be regenerated. Once no player can observe a chunk, discard
     * its heavy replayable overlay bytes while retaining the world-epoch revision high-water. Reusing a cursor
     * within one epoch lets delayed outbound updates poison a retained client's recovery barrier.
     */
    private void compactReplayableInactiveState(Set<Long> active) {
        if (!ownerTurnMayContinue() || replayableOverlayChunks.isEmpty()) return;
        Set<Long> compactable = new HashSet<>(replayableOverlayChunks);
        compactable.removeAll(active);
        compactable.removeAll(protectedDecorationEditChunks);
        if (compactable.isEmpty()) return;
        replayableOverlayChunks.removeAll(compactable);
        for (long key : compactable) {
            if (!ownerTurnMayContinue()) return;
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            TerrainAccessor.ReplayableChunkPatch naturalPatch =
                    accessor.discardChunkOverlay(chunkX, chunkZ);
            invalidateLightChunkCaches(chunkX, chunkZ);
            int baseX = chunkX * Blocks.CHUNK_X;
            int baseZ = chunkZ * Blocks.CHUNK_Z;
            for (int ordinal = 0; ordinal < naturalPatch.size(); ordinal++) {
                if (!ownerTurnMayContinue()) return;
                if (naturalPatch.blockStateAtOrdinal(ordinal) == 0) continue;
                int index = naturalPatch.blockIndexAt(ordinal);
                int x = baseX + (index & 15);
                int z = baseZ + ((index >>> 4) & 15);
                int y = Blocks.MIN_Y + (index >>> 8);
                if (!structureOverlaySites.contains(structurePositionKey(x, y, z))) {
                    blockStates.remove(x, y, z);
                }
            }
            authoritativeOverlay.remove(key);
            compactedReplayableChunks.add(key);
            rehydratingReplayableChunks.remove(key);
        }
        if (!ownerTurnMayContinue()) return;
        naturalLootChests.keySet().removeIf(pos ->
                compactable.contains(blockChunkKey(pos.x(), pos.z())));
        Iterator<Long> structures = structureOverlaySites.iterator();
        while (structures.hasNext()) {
            if (!ownerTurnMayContinue()) return;
            long positionKey = structures.next();
            int x = (int) (positionKey >> 38 << 6) >> 6;
            int y = (int) ((positionKey >> 26) & 0xfff) - 2048;
            int z = (int) (positionKey << 38 >> 38);
            if (!compactable.contains(blockChunkKey(x, z))) continue;
            blockStates.remove(x, y, z);
            structures.remove();
        }
    }

    private boolean isSimulationChunkActive(int chunkX, int chunkZ) {
        return activeSimulationChunks.contains(chunkKey(chunkX, chunkZ));
    }

    Set<Long> activeSimulationChunksForMobTick() {
        return activeSimulationChunks;
    }

    /** Registers one direct-engine fixture chunk without granting production worlds an entity-driven activation. */
    void activateSimulationChunkForTest(double x, double z) {
        if (started || terminalPhase != TerminalPhase.RUNNING) return;
        int chunkX = Math.floorDiv((int) Math.floor(x), Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv((int) Math.floor(z), Blocks.CHUNK_Z);
        if (testSimulationChunks.add(chunkKey(chunkX, chunkZ))) {
            refreshActiveSimulationChunks();
        }
    }

    int activeSimulationChunkCountForTest() {
        return activeSimulationChunks.size();
    }

    long activationGenerationForTest(int chunkX, int chunkZ) {
        return activeChunkGeneration(chunkKey(chunkX, chunkZ));
    }

    int replayableOverlayChunkCountForTest() {
        return replayableOverlayChunks.size();
    }

    int authoritativeOverlayChunkCountForTest() {
        return authoritativeOverlay.size();
    }

    Set<Integer> runtimeOverlayBlockTypesForTest() {
        Set<Integer> blockTypes = new HashSet<>();
        authoritativeOverlay.forEachValue(chunk -> {
            for (OverlayValue value : chunk.values()) {
                blockTypes.add(Short.toUnsignedInt(value.blockType));
            }
        });
        accessor.addReplayablePatchBlockTypes(blockTypes);
        return blockTypes;
    }

    static int simulationChunkRadiusForTest() {
        return PROXIMITY_PREPARE_RADIUS;
    }

    private static Set<Long> retainActiveKeys(Set<Long> existing, Set<Long> active) {
        Set<Long> retained = ConcurrentHashMap.newKeySet();
        for (long key : orderedChunkKeys(active)) if (existing.contains(key)) retained.add(key);
        return retained;
    }

    private static <T> ConcurrentHashMap<Long, T> retainActiveEntries(
            Map<Long, T> existing, Set<Long> active) {
        ConcurrentHashMap<Long, T> retained = new ConcurrentHashMap<>();
        for (long key : orderedChunkKeys(active)) {
            T value = existing.get(key);
            if (value != null) retained.put(key, value);
        }
        return retained;
    }

    private static List<Long> orderedChunkKeys(Set<Long> keys) {
        List<Long> ordered = new ArrayList<>(keys);
        ordered.sort(Long::compare);
        return ordered;
    }

    /**
     * 틱에서는 플레이어마다 고정된 수의 좌표만 검사·제출한다. 반경 10을 중심부터 링 순서로 순회하며,
     * 청크 이동은 새 세대로 시작하고 큰 점프는 이전 위치의 대기 작업을 워커에서 즉시 폐기한다.
     */
    /**
     * [DRAGON] {@code DRAGON} 티켓(바닐라 {@code addTicketWithRadius(DRAGON, (0, 0), 9)})이 아레나를 강제 적재하듯, 보스바를
     * 보는 플레이어가 있으면 아레나의 비상주 청크를 가운데부터 준비한다. 보이는 청크 스냅샷 요청(입장 임계 경로)이 있으면
     * 쉬고, 같은 준비 레인(prepareDemandedChunk → drainPreparedChunkDemands → 활성화)을 쓴다.
     */
    private void scheduleDragonArenaPreparations() {
        if (!started || !ownerTurnMayContinue() || !dragonArenaHeld || activePlayerOrder.isEmpty()) {
            playerDemandCursors.remove(DRAGON_ARENA_DEMAND);
            return;
        }
        if (hasActivePendingChunkSnapshotRequest()) return;
        PlayerDemandCursor cursor = playerDemandCursors.computeIfAbsent(DRAGON_ARENA_DEMAND,
                ignored -> new PlayerDemandCursor(0, 0));
        long generation = cursor.generation;
        int radius = com.gameexpert.engine.dragon.DragonFight.ARENA_SIZE_CHUNKS;
        int submitted = 0;
        for (int[] offset : PROXIMITY_PREPARE_OFFSETS) {
            if (submitted >= DRAGON_ARENA_PREPARES_PER_TICK || !ownerTurnMayContinue()) return;
            if (Math.abs(offset[0]) > radius || Math.abs(offset[1]) > radius) continue;
            int chunkX = offset[0];
            int chunkZ = offset[1];
            long key = chunkKey(chunkX, chunkZ);
            if (!activeSimulationChunks.contains(key) || preparedActivationChunks.contains(key)) continue;
            long neighborhoodGeneration = activeChunkGeneration(key);
            if (pendingChunkPreparations.putIfAbsent(key, neighborhoodGeneration) != null) continue;
            if (accessor.isChunkResident(chunkX, chunkZ)) {
                // 이미 상주한 청크는 한도를 쓰지 않는다(활성화가 남았을 때만 센다) — 가운데부터 매 틱 다시 훑는다.
                pendingChunkPreparations.remove(key, neighborhoodGeneration);
                TerrainAccessor.PreparedActivationClaim activation =
                        accessor.claimResidentChunkForExplicitActivation(chunkX, chunkZ);
                if (activation != null) {
                    submitted++;
                    retainActivationPlanningSource(chunkX, chunkZ);
                    enqueueChunkActivation(activation);
                }
                continue;
            }
            submitted++;
            try {
                executeOwned(CHUNK_PREPARERS, () -> prepareDemandedChunk(
                        DRAGON_ARENA_DEMAND, generation, neighborhoodGeneration,
                        key, chunkX, chunkZ), null, true, chunkX, chunkZ, neighborhoodGeneration);
            } catch (RejectedExecutionException saturated) {
                pendingChunkPreparations.remove(key, neighborhoodGeneration);
                return;
            }
        }
    }

    private void schedulePlayerProximityPreparations() {
        if (!started || !ownerTurnMayContinue()) return;
        scheduleDragonArenaPreparations();
        // Visible snapshot requests are the entry critical path and their detached material is also claimed for
        // gameplay activation. Do not spend the separate radius-10 lane on invisible chunks until that path
        // drains; this also prevents generating a requested chunk twice.
        if (hasActivePendingChunkSnapshotRequest() || hasActivationBacklog()) return;
        for (String nickname : activePlayerOrder) {
            if (!ownerTurnMayContinue()) return;
            PlayerTickState player = players.get(nickname);
            if (player == null) continue;
            PlayerPose pose = player.pose();
            int centerX = Math.floorDiv((int) Math.floor(pose.getX()), Blocks.CHUNK_X);
            int centerZ = Math.floorDiv((int) Math.floor(pose.getZ()), Blocks.CHUNK_Z);
            PlayerDemandCursor cursor = playerDemandCursors.computeIfAbsent(player.nickname(),
                    ignored -> new PlayerDemandCursor(centerX, centerZ));
            if (cursor.centerX != centerX || cursor.centerZ != centerZ) {
                boolean teleported = Math.max(Math.abs(centerX - cursor.centerX),
                        Math.abs(centerZ - cursor.centerZ)) > TELEPORT_CHUNK_DISTANCE;
                cursor.moveTo(centerX, centerZ, teleported);
            }

            long generation = cursor.generation;
            for (int checked = 0; checked < PROXIMITY_PREPARES_PER_PLAYER_TICK; checked++) {
                if (!ownerTurnMayContinue()) return;
                int[] offset = PROXIMITY_PREPARE_OFFSETS[cursor.nextOffset];
                cursor.nextOffset = (cursor.nextOffset + 1) % PROXIMITY_PREPARE_OFFSETS.length;
                int chunkX = centerX + offset[0];
                int chunkZ = centerZ + offset[1];
                long key = chunkKey(chunkX, chunkZ);
                long neighborhoodGeneration = activeChunkGeneration(key);
                if (preparedActivationChunks.contains(key)
                        || pendingChunkPreparations.putIfAbsent(key, neighborhoodGeneration) != null) continue;
                if (accessor.isChunkResident(chunkX, chunkZ)) {
                    pendingChunkPreparations.remove(key, neighborhoodGeneration);
                    TerrainAccessor.PreparedActivationClaim activation =
                            accessor.claimResidentChunkForExplicitActivation(chunkX, chunkZ);
                    if (activation != null) {
                        retainActivationPlanningSource(chunkX, chunkZ);
                        enqueueChunkActivation(activation);
                    }
                    continue;
                }
                try {
                    executeOwned(CHUNK_PREPARERS, () -> prepareDemandedChunk(
                            player.nickname(), generation, neighborhoodGeneration,
                            key, chunkX, chunkZ), null, true, chunkX, chunkZ, neighborhoodGeneration);
                } catch (RejectedExecutionException saturated) {
                    pendingChunkPreparations.remove(key, neighborhoodGeneration);
                    break;
                }
            }
        }
    }

    /**
     * Invisible radius preparation is background work. Keep at most one activation pipeline in flight so it
     * cannot delay player edits, initial snapshots, or the next world's work on the shared bounded executors.
     */
    private boolean hasActivationBacklog() {
        return !pendingChunkPreparations.isEmpty()
                || !preparedChunkDemands.isEmpty()
                || !chunkActivationDemands.isEmpty()
                || !pendingChunkActivationPlans.isEmpty()
                || !preparedChunkActivations.isEmpty()
                || !pendingChunkActivationApplications.isEmpty()
                || !pendingLiveBlockUpdates.isEmpty()
                || !pendingStructureDecorations.isEmpty();
    }

    private void prepareDemandedChunk(String nickname, long generation,
            long neighborhoodGeneration, long key, int chunkX, int chunkZ) {
        boolean handedOff = false;
        try {
            PlayerDemandCursor current = playerDemandCursors.get(nickname);
            if (current == null || current.generation != generation
                    || activeChunkGeneration(key) != neighborhoodGeneration
                    || !activeSimulationChunks.contains(key)) return;
            TerrainAccessor.PreparedChunk prepared = prepareChunkOffTick(chunkX, chunkZ);
            if (activeChunkGeneration(key) == neighborhoodGeneration
                    && activeSimulationChunks.contains(key)) {
                PreparedChunkDemand completed = new PreparedChunkDemand(
                        nickname, generation, neighborhoodGeneration, key, prepared);
                preparedChunkDemands.compute(key, (ignored, existing) ->
                        existing == null
                                || existing.neighborhoodGeneration < neighborhoodGeneration
                                        ? completed : existing);
                handedOff = true;
            }
        } catch (RuntimeException preparationFailure) {
            log.warn("플레이어 주변 청크 준비 실패: world={} chunk={},{}", worldId, chunkX, chunkZ,
                    preparationFailure);
        } finally {
            if (!handedOff) pendingChunkPreparations.remove(key, neighborhoodGeneration);
        }
    }

    /** Owns cache/overlay adoption for proximity work; detached worker material never touches TerrainAccessor maps. */
    private void drainPreparedChunkDemands() {
        if (!ownerTurnMayContinue() || preparedChunkDemands.isEmpty()) return;
        int adopted = 0;
        int adoptedArena = 0;
        List<Map.Entry<Long, PreparedChunkDemand>> ordered =
                new ArrayList<>(preparedChunkDemands.entrySet());
        ordered.sort((left, right) -> compareDemandedChunks(left.getKey(), right.getKey()));
        for (Map.Entry<Long, PreparedChunkDemand> entry : ordered) {
            if (!ownerTurnMayContinue()) return;
            PreparedChunkDemand prepared = entry.getValue();
            // [DRAGON] 아레나 강제 적재는 플레이어 반경 채택 한도와 따로 센다.
            boolean arena = DRAGON_ARENA_DEMAND.equals(prepared.nickname);
            if (arena ? adoptedArena >= DRAGON_ARENA_PREPARES_PER_TICK
                    : adopted >= PROXIMITY_PREPARES_PER_PLAYER_TICK) continue;
            if (prepared.neighborhoodGeneration != activeChunkGeneration(prepared.key)
                    || !activeSimulationChunks.contains(prepared.key)) {
                preparedChunkDemands.remove(entry.getKey(), prepared);
                pendingChunkPreparations.remove(
                        prepared.key, prepared.neighborhoodGeneration);
                continue;
            }
            if (!preparedChunkDemands.remove(entry.getKey(), prepared)) continue;
            try {
                if (!ownerTurnMayContinue()) return;
                PlayerDemandCursor current = playerDemandCursors.get(prepared.nickname);
                if (current == null || current.generation != prepared.generation
                        || activeChunkGeneration(prepared.key)
                                != prepared.neighborhoodGeneration) continue;
                // This is an explicit demand handoff, not a tick-side block read. Claiming it publishes the
                // resident material and the one-time activation bookkeeping without invoking notifyChunkAccess;
                // the exact same activation plan still runs on CHUNK_PREPARERS below.
                TerrainAccessor.PreparedActivationClaim activation =
                        accessor.adoptPreparedChunkForExplicitActivation(prepared.chunk);
                if (activation != null) {
                    if (!ownerTurnMayContinue()) return;
                    retainActivationPlanningSource(activation.chunkX(), activation.chunkZ());
                    enqueueChunkActivation(activation);
                }
                if (!ownerTurnMayContinue()) return;
                // A snapshot task may already be queued for the same chunk.  Publish this owner-side resident
                // immediately instead of making the client wait for duplicate snapshot generation to finish.
                requeuePendingChunkSnapshotRequests(prepared.chunk.chunkX(), prepared.chunk.chunkZ());
            } catch (RuntimeException adoptionFailure) {
                log.warn("플레이어 주변 청크 준비 결과 적용 실패: world={} chunk={},{}", worldId,
                        prepared.chunk.chunkX(), prepared.chunk.chunkZ(), adoptionFailure);
            } finally {
                pendingChunkPreparations.remove(
                        prepared.key, prepared.neighborhoodGeneration);
            }
            if (arena) adoptedArena++;
            else adopted++;
        }
    }

    private static int[][] proximityPrepareOffsets() {
        List<int[]> offsets = new ArrayList<>((PROXIMITY_PREPARE_RADIUS * 2 + 1)
                * (PROXIMITY_PREPARE_RADIUS * 2 + 1));
        for (int radius = 0; radius <= PROXIMITY_PREPARE_RADIUS; radius++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == radius) {
                        offsets.add(new int[] {dx, dz});
                    }
                }
            }
        }
        return offsets.toArray(int[][]::new);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffff_ffffL);
    }

    private long activeChunkGeneration(long key) {
        return activeChunkGenerations.getOrDefault(key, 0L);
    }

    private static ThreadPoolExecutor daemonExecutor(int threads, int queueCapacity, String name) {
        return daemonExecutor(threads, queueCapacity, name, Thread.NORM_PRIORITY);
    }

    private static ThreadPoolExecutor daemonExecutor(
            int threads, int queueCapacity, String name, int priority) {
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), runnable -> {
                    Thread thread = new Thread(runnable, name);
                    thread.setDaemon(true);
                    thread.setPriority(priority);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    private void executeOwned(ThreadPoolExecutor executor, Runnable task) {
        executeOwned(executor, task, null);
    }

    private void executeOwned(ThreadPoolExecutor executor, Runnable task, Runnable onDiscard) {
        executeOwned(executor, task, onDiscard, false, 0, 0, 0L);
    }

    private void executeOwned(ThreadPoolExecutor executor, Runnable task, Runnable onDiscard,
            boolean hasChunk, int chunkX, int chunkZ, long generation) {
        WorldWorkEvent work = null;
        if (WorldWorkEvent.enabled()) {
            String kind = executor == SNAPSHOT_PREPARERS ? "snapshot-prepare-worker"
                    : executor == SNAPSHOT_ENCODERS ? "snapshot-encode-worker"
                    : executor == ACTIVATION_PLANNERS ? "activation-plan-worker"
                    : executor == CHUNK_PREPARERS ? "proximity-prepare-worker"
                    : executor == FINAL_CARRIER_LANE_WORKERS ? "final-carrier-admission-worker"
                    : executor == FINAL_CARRIER_PAYLOAD_WORKERS ? "final-carrier-payload-worker"
                    : executor == FINAL_CARRIER_GAMEPLAY_PAYLOAD_WORKERS
                            ? "final-carrier-gameplay-payload-worker"
                    : executor == SNAPSHOT_SENDERS ? "snapshot-send-worker" : "owned-worker";
            work = architectureSubmitted(kind, hasChunk, chunkX, chunkZ, generation);
        }
        try {
            executor.execute(new RuntimeTask(this, task, onDiscard, work));
        } catch (RejectedExecutionException rejected) {
            WorldWorkEvent.outcome(work, "rejected");
            WorldWorkEvent.finish(work);
            throw rejected;
        }
    }

    private void discardQueuedWorkerTasks() {
        discardQueuedTasks(CHUNK_PREPARERS);
        discardQueuedTasks(ACTIVATION_PLANNERS);
        discardQueuedTasks(FINAL_CARRIER_LANE_WORKERS);
        discardQueuedTasks(FINAL_CARRIER_PAYLOAD_WORKERS);
        discardQueuedTasks(FINAL_CARRIER_GAMEPLAY_PAYLOAD_WORKERS);
        discardQueuedTasks(SNAPSHOT_PREPARERS);
        discardQueuedTasks(SNAPSHOT_ENCODERS);
        discardQueuedTasks(SNAPSHOT_SENDERS);
    }

    private void discardQueuedTasks(ThreadPoolExecutor executor) {
        List<Runnable> candidates = new ArrayList<>();
        for (Runnable queued : executor.getQueue()) {
            if (queued instanceof RuntimeTask owned && owned.owner == this) {
                candidates.add(queued);
            }
        }
        for (Runnable candidate : candidates) {
            if (executor.getQueue().remove(candidate)) {
                ((RuntimeTask) candidate).discard();
            }
        }
    }

    /**
     * 생성기 바이트를 바꾸지 않고 최초 접근 청크에 자연 장식 오버레이를 재생성한다. 자연 장식은
     * tickBlockChanges에 넣지 않아 DB diff로 영속하지 않으며, 명시적 AIR를 포함한 기존 플레이어 diff
     * 좌표는 후보에서 제외해 파괴한 자연 수련잎이 다음 런타임에서 되살아나지 않게 한다.
     */
    private void decorateGeneratedChunk(int chunkX, int chunkZ, List<WorldBlockDiff> persistedDiffs) {
        if (!ownerTurnMayContinue()) return;
        if (chunkProductSource instanceof com.gameexpert.world.dimension.DimensionChunkProductSource custom) {
            installPreparedNaturalPatch(chunkX, chunkZ, custom.initialStates(seed, chunkX, chunkZ));
            markSnapshotStructureReady(chunkX, chunkZ);
            markSnapshotBaseReady(chunkX, chunkZ);
            return;
        }
        Set<BlockPos> persistentEdits = new HashSet<>();
        for (WorldBlockDiff diff : persistedDiffs) {
            persistentEdits.add(new BlockPos(diff.getX(), diff.getY(), diff.getZ()));
        }
        // As in the detached production planner, resolve a shared structure site before scanning natural
        // decoration. The structure output is still applied last and therefore keeps the same priority.
        List<AcceptedStructureSite> acceptedSites = new ArrayList<>();
        List<ExplorationDecorator.Placement> criticalPlan = explorationDecorator.activationBasePlacements(
                chunkX, chunkZ, activationStructurePlanningFallback, acceptedSites::add);
        SurfaceDecorator.NaturalWaterPlan ponds = surfaceDecorator.customPondFeatures(
                chunkX, chunkZ, activationPlanningFallback, persistentEdits::contains);
        // Bootstrap/direct-engine activation may already contain authoritative in-memory cells
        // (for example a just-created pond fixture). Preserve the established resident view for
        // dependent vegetation while still layering this activation's natural water beneath it.
        SurfaceDecorator.BlockView residentWorld = directActivationResidentView();
        SurfaceDecorator.BlockView naturalWorld = ponds.overlay(residentWorld);
        java.util.function.Predicate<BlockPos> naturalClaims =
                pos -> persistentEdits.contains(pos) || ponds.claims(pos);
        List<SurfaceDecorator.Decoration> custom = surfaceDecorator.customSurfaceDecorations(
                chunkX, chunkZ, naturalWorld, naturalClaims);
        installPreparedNaturalPatch(chunkX, chunkZ, naturalPatch(chunkX, chunkZ,
                ponds.decorations(), custom));
        if (!ownerTurnMayContinue()) return;
        // 전리품 상자와 던전 조명을 포함한 activation-time 구조물은 모든 자연 장식 뒤에 적용한다.
        Set<BlockPos> baseStructurePlacements = new HashSet<>(criticalPlan.size());
        for (ExplorationDecorator.Placement placement : criticalPlan) {
            if (!ownerTurnMayContinue()) return;
            applyStructurePlacement(placement);
            if (!ownerTurnMayContinue()) return;
            baseStructurePlacements.add(placement.pos());
        }
        if (!ownerTurnMayContinue()) return;
        if (explorationDecorator.hasDeferredCandidate(chunkX, chunkZ)) {
            DeferredStructureDecoration deferred = new DeferredStructureDecoration(
                    chunkX, chunkZ, baseStructurePlacements, acceptedSites);
            pendingStructureDecorations.putIfAbsent(chunkKey(chunkX, chunkZ), deferred);
        } else {
            if (!ownerTurnMayContinue()) return;
            mobSystem.acceptStructureSites(chunkX, chunkZ, acceptedSites);
            if (!ownerTurnMayContinue()) return;
            markSnapshotStructureReady(chunkX, chunkZ);
        }
        markSnapshotBaseReady(chunkX, chunkZ);
    }

    private SurfaceDecorator.BlockView directActivationResidentView() {
        SurfaceDecorator.BlockView composedFallback = activationPlanningFallback;
        SurfaceDecorator.BlockView generatedFallback = activationStructurePlanningFallback;
        return new SurfaceDecorator.BlockView() {
            @Override
            public int getBlock(int x, int y, int z) {
                TerrainAccessor.ResidentBlock resident = accessor.residentBlock(x, y, z);
                return resident.isAvailable()
                        ? resident.blockType()
                        : composedFallback.getBlock(x, y, z);
            }

            @Override
            public int getTreeSourceBlock(int x, int y, int z) {
                return generatedSourceBlock(x, y, z);
            }

            @Override
            public int getFeatureSourceBlock(int x, int y, int z) {
                return generatedSourceBlock(x, y, z);
            }

            @Override
            public int terrainSurfaceHeight(int x, int z) {
                int chunkX = Math.floorDiv(x, Blocks.CHUNK_X);
                int chunkZ = Math.floorDiv(z, Blocks.CHUNK_Z);
                TerrainAccessor.SnapshotSource source = accessor.snapshotSource(chunkX, chunkZ);
                return source == null
                        ? composedFallback.terrainSurfaceHeight(x, z)
                        : source.terrainSurfaceHeightAt(
                                Math.floorMod(x, Blocks.CHUNK_X), Math.floorMod(z, Blocks.CHUNK_Z));
            }

            @Override
            public boolean isProtectedEdit(int x, int y, int z) {
                return composedFallback.isProtectedEdit(x, y, z);
            }

            @Override
            public int noiseBiomeAt(int x, int y, int z) {
                // Structure shape reads the generated source, so its biome oracle comes from the
                // same generated-only fallback rather than the edit-composed one.
                return generatedFallback.noiseBiomeAt(x, y, z);
            }

            private int generatedSourceBlock(int x, int y, int z) {
                if (y < Blocks.MIN_Y) return Blocks.BEDROCK;
                if (y > Blocks.MAX_Y) return Blocks.AIR;
                int chunkX = Math.floorDiv(x, Blocks.CHUNK_X);
                int chunkZ = Math.floorDiv(z, Blocks.CHUNK_Z);
                TerrainAccessor.SnapshotSource source = accessor.snapshotSource(chunkX, chunkZ);
                if (source == null) return generatedFallback.getBlock(x, y, z);
                return source.generatedBlockTypeAt(Blocks.blockIndex(
                        Math.floorMod(x, Blocks.CHUNK_X), y,
                        Math.floorMod(z, Blocks.CHUNK_Z)));
            }
        };
    }

    /** 한랭 표면을 제외한 구조물/수중 자연 장식을 FIFO 순서로 시간 예산만큼 방송한다. */
    void flushDecorationUpdates() {
        flushDecorationUpdates(System.nanoTime() + STRUCTURE_SEND_BUDGET_NANOS,
                DECORATION_UPDATES_PER_TICK);
    }

    private void flushDecorationUpdates(long deadline, int batchLimit) {
        if (!ownerTurnMayContinue() || pendingLiveBlockUpdates.isEmpty()) return;
        List<Block> batch = new ArrayList<>(Math.min(batchLimit, pendingLiveBlockUpdates.size()));
        while (!pendingLiveBlockUpdates.isEmpty() && batch.size() < batchLimit
                && System.nanoTime() < deadline && ownerTurnMayContinue()) {
            // 같은 청크의 모든 revision을 순서대로 보내야 최종 셀로 합쳐진 편집도 cursor gap이 없다.
            batch.add(pendingLiveBlockUpdates.removeFirst());
        }
        if (!batch.isEmpty() && ctx.broadcaster() != null) {
            Map<Long, List<Block>> updatesByChunk = new LinkedHashMap<>();
            for (Block update : batch) {
                long chunkKey = blockChunkKey(update.getX(), update.getZ());
                updatesByChunk.computeIfAbsent(chunkKey, ignored -> new ArrayList<>()).add(update);
            }
            List<List<Block>> immutableBatches = new ArrayList<>(updatesByChunk.size());
            for (List<Block> chunkUpdates : updatesByChunk.values()) {
                if (!ownerTurnMayContinue()) return;
                for (int offset = 0; offset < chunkUpdates.size();
                        offset += MAX_BLOCKS_PER_OUTBOUND_UPDATE) {
                    immutableBatches.add(List.copyOf(chunkUpdates.subList(offset,
                            Math.min(offset + MAX_BLOCKS_PER_OUTBOUND_UPDATE, chunkUpdates.size()))));
                }
            }
            for (int batchIndex = 0; batchIndex < immutableBatches.size(); batchIndex++) {
                if (!ownerTurnMayContinue()) return;
                List<Block> chunkUpdates = immutableBatches.get(batchIndex);
                if (!ctx.broadcaster().enqueueRetainedBroadcastFromTick(
                        worldId, new BlockUpdate(chunkUpdates))) {
                    // 아직 handoff하지 않은 fragment만 원래 순서로 다음 틱에 재시도한다.
                    for (int remaining = immutableBatches.size() - 1;
                            remaining >= batchIndex; remaining--) {
                        List<Block> retry = immutableBatches.get(remaining);
                        for (int blockIndex = retry.size() - 1; blockIndex >= 0; blockIndex--) {
                            pendingLiveBlockUpdates.addFirst(retry.get(blockIndex));
                        }
                    }
                    break;
                }
            }
        }
    }

    int pendingDecorationUpdates() {
        return pendingLiveBlockUpdates.size();
    }

    /** 구조물 계획의 기존 정규화/FIFO 순서를 유지하면서 작은 시간 예산만큼 반영한다. */
    void applyDeferredStructureDecorations() {
        applyDeferredStructureDecorations(System.nanoTime() + STRUCTURE_APPLY_BUDGET_NANOS);
    }

    private void applyDeferredStructureDecorations(long deadline) {
        if (!ownerTurnMayContinue()) return;
        admitStructurePlans();
        while (!pendingStructureDecorations.isEmpty()
                && pendingLiveBlockUpdates.size() < MAX_PENDING_DECORATION_UPDATES
                && System.nanoTime() < deadline && ownerTurnMayContinue()) {
            DeferredStructureDecoration deferred = fairestReadyStructureDecoration();
            if (deferred == null) break;
            deferred.lastProgressSequence = ++structureApplySequence;
            if (!deferred.applyCriticalPlacements(this, deadline)) {
                continue;
            }
            int examined = 0;
            while (examined < STRUCTURE_PLACEMENTS_PER_SLICE
                    && pendingLiveBlockUpdates.size() < MAX_PENDING_DECORATION_UPDATES
                    && System.nanoTime() < deadline && ownerTurnMayContinue()) {
                ExplorationDecorator.Placement placement = deferred.next();
                if (placement == null) {
                    break;
                }
                examined++;
                if (!isActivationCriticalStructurePlacement(placement)) {
                    applyStructurePlacement(placement);
                }
            }
            if (deferred.isComplete()) {
                pendingStructureDecorations.remove(chunkKey(deferred.chunkX, deferred.chunkZ), deferred);
                mobSystem.acceptStructureSites(
                        deferred.chunkX, deferred.chunkZ, deferred.acceptedSites());
                markSnapshotStructureReady(deferred.chunkX, deferred.chunkZ);
            }
        }
    }

    /**
     * Chooses work only when one of the two CPU planners is actually free. Keeping no hidden executor FIFO
     * prevents old distant chunks from occupying queued slots ahead of a newly visible chunk, while
     * deterministic distance/age ordering and the ready-apply round robin prevent starvation.
     */
    private void admitStructurePlans() {
        if (!ownerTurnMayContinue()) return;
        int available = STRUCTURE_PLANNERS.getMaximumPoolSize() - STRUCTURE_PLANNERS.getActiveCount();
        if (available <= 0 || pendingStructureDecorations.isEmpty()) return;

        Set<Long> snapshotDemandChunks = new HashSet<>();
        for (ChunkSnapshotRequest request : pendingChunkSnapshotRequests.values()) {
            if (canProcessChunkSnapshotRequest(request)) {
                snapshotDemandChunks.add(chunkKey(request.chunkX, request.chunkZ));
            }
        }
        List<DeferredStructureDecoration> candidates = new ArrayList<>();
        for (DeferredStructureDecoration candidate : pendingStructureDecorations.values()) {
            if (!ownerTurnMayContinue()) return;
            if ((started && !isSimulationChunkActive(candidate.chunkX, candidate.chunkZ))
                    || candidate.isSubmitted() || candidate.placements != null) {
                continue;
            }
            candidates.add(candidate);
        }
        candidates.sort((left, right) -> {
            boolean leftSnapshot = snapshotDemandChunks.contains(chunkKey(left.chunkX, left.chunkZ));
            boolean rightSnapshot = snapshotDemandChunks.contains(chunkKey(right.chunkX, right.chunkZ));
            if (leftSnapshot != rightSnapshot) return leftSnapshot ? -1 : 1;
            int distance = Double.compare(
                    distanceToNearestPlayer(left.chunkX, left.chunkZ),
                    distanceToNearestPlayer(right.chunkX, right.chunkZ));
            if (distance != 0) return distance;
            int age = Long.compare(left.createdNanos, right.createdNanos);
            return age != 0 ? age : compareChunk(left, right);
        });
        for (DeferredStructureDecoration candidate : candidates) {
            if (!ownerTurnMayContinue()) return;
            if (available <= 0) break;
            boolean heavy = explorationDecorator.requiresUncachedHeavyPlan(
                    candidate.chunkX, candidate.chunkZ);
            if (heavy && !HEAVY_STRUCTURE_PLAN_ACTIVE.compareAndSet(false, true)) continue;
            if (!ownerTurnMayContinue()) {
                if (heavy) HEAVY_STRUCTURE_PLAN_ACTIVE.set(false);
                return;
            }
            if (!submitStructurePlan(candidate, heavy)) {
                if (heavy) HEAVY_STRUCTURE_PLAN_ACTIVE.set(false);
                break;
            }
            available--;
        }
    }

    /**
     * 가장 오래 진행하지 못한 READY 청크를 먼저 고르고, 동률일 때만 플레이어 거리를 쓴다.
     * 한 청크가 2ms 예산 전체를 독점하던 nearest-only starvation을 없애면서 근거리 우선순위는 유지한다.
     */
    private DeferredStructureDecoration fairestReadyStructureDecoration() {
        DeferredStructureDecoration selected = null;
        long oldestProgress = Long.MAX_VALUE;
        double selectedDistance = Double.POSITIVE_INFINITY;
        Iterator<Map.Entry<Long, DeferredStructureDecoration>> candidates =
                pendingStructureDecorations.entrySet().iterator();
        while (candidates.hasNext()) {
            DeferredStructureDecoration candidate = candidates.next().getValue();
            if (!candidate.isReady()) {
                Throwable planningFailure = candidate.takePlanningFailure();
                if (planningFailure == null) continue;
                if (candidate.canRetry()) {
                    log.warn("구조물 계획 실패, 한 번 재시도합니다: world={} chunk={},{} attempt={}",
                            worldId, candidate.chunkX, candidate.chunkZ,
                            candidate.planningFailures, planningFailure);
                    // The next admission pass retries according to current visibility priority.
                } else {
                    candidates.remove();
                    markSnapshotStructureReady(candidate.chunkX, candidate.chunkZ);
                    log.error("구조물 계획을 두 번 실패해 해당 청크 계획을 폐기합니다: world={} chunk={},{}",
                            worldId, candidate.chunkX, candidate.chunkZ, planningFailure);
                }
                continue;
            }
            double distance = distanceToNearestPlayer(candidate.chunkX, candidate.chunkZ);
            if (selected == null || candidate.lastProgressSequence < oldestProgress
                    || (candidate.lastProgressSequence == oldestProgress
                            && (distance < selectedDistance
                                    || (distance == selectedDistance
                                            && compareChunk(candidate, selected) < 0)))) {
                selected = candidate;
                oldestProgress = candidate.lastProgressSequence;
                selectedDistance = distance;
            }
        }
        return selected;
    }

    private double distanceToNearestPlayer(int chunkX, int chunkZ) {
        double centerX = chunkX * (double) Blocks.CHUNK_X + Blocks.CHUNK_X * 0.5;
        double centerZ = chunkZ * (double) Blocks.CHUNK_Z + Blocks.CHUNK_Z * 0.5;
        double nearest = Double.POSITIVE_INFINITY;
        for (PlayerTickState player : players.values()) {
            PlayerPose pose = player.pose();
            double dx = pose.getX() - centerX;
            double dz = pose.getZ() - centerZ;
            nearest = Math.min(nearest, dx * dx + dz * dz);
        }
        return nearest;
    }

    private double distanceToNearestPlayer(long chunkKey) {
        return distanceToNearestPlayer((int) (chunkKey >> 32), (int) chunkKey);
    }

    private int compareDemandedChunks(long leftKey, long rightKey) {
        return SnapshotPreparationAdmission.compareDemandedChunks(leftKey, rightKey,
                this::distanceToNearestPlayer);
    }

    private static int compareChunk(DeferredStructureDecoration left,
            DeferredStructureDecoration right) {
        int x = Integer.compare(left.chunkX, right.chunkX);
        return x != 0 ? x : Integer.compare(left.chunkZ, right.chunkZ);
    }

    /** 계획은 틱 상태를 공유하지 않는 뷰에서 계산하고, 완료 순서와 무관하게 활성화 FIFO로 소비한다. */
    private boolean submitStructurePlan(DeferredStructureDecoration deferred, boolean heavy) {
        try {
            HeavyPlanLease heavyLease = heavy ? new HeavyPlanLease() : null;
            deferred.submit(STRUCTURE_PLANNERS.submit(() -> {
                if (heavyLease != null && !heavyLease.start()) {
                    return new PreparedStructurePlan(List.of(), List.of(), List.of());
                }
                try {
                    List<AcceptedStructureSite> acceptedSites = new ArrayList<>();
                    List<ExplorationDecorator.Placement> planned = explorationDecorator.deferredPlacements(
                            deferred.chunkX, deferred.chunkZ, activationStructurePlanningView(),
                            deferred.baseStructurePlacements, acceptedSites::add);
                    List<ExplorationDecorator.Placement> critical = new ArrayList<>();
                    for (ExplorationDecorator.Placement placement : planned) {
                        if (isActivationCriticalStructurePlacement(placement)) {
                            critical.add(placement);
                        }
                    }
                    return new PreparedStructurePlan(
                            List.copyOf(planned), List.copyOf(critical),
                            List.copyOf(acceptedSites));
                } finally {
                    if (heavyLease != null) heavyLease.release();
                }
            }), heavyLease);
            return true;
        } catch (RejectedExecutionException saturated) {
            return false;
        }
    }

    /** 상자와 횃불은 활성화 직후 조회·상호작용 가능한 구조물 계약에 포함된다. */
    private static boolean isActivationCriticalStructurePlacement(ExplorationDecorator.Placement placement) {
        return placement.blockType() != Blocks.ENDER_CHEST
                && (Blocks.isChestShaped(placement.blockType())
                        || placement.blockType() == Blocks.BARREL)
                || placement.blockType() == Blocks.TORCH
                || placement.blockType() >= Blocks.WALL_TORCH_N
                        && placement.blockType() <= Blocks.WALL_TORCH_W;
    }

    /** 영속/현재 플레이어 편집을 보존하면서 구조물 확정 placement 하나를 적용한다. */
    void applyStructurePlacement(ExplorationDecorator.Placement placement) {
        if (!ownerTurnMayContinue()) return;
        BlockPos pos = placement.pos();
        // 최초 DB diff뿐 아니라 큐 대기 중 현재 런타임에서 발생한 블록 변경도 자연 구조물이 덮지 않는다.
        // 저널이 소유한 몹 변형도 구조물보다 우선하므로 구조물 재적용이 그 칸을 되돌리지 않는다.
        if (protectedDecorationEdits.contains(pos) || mobMutationSites.contains(pos)) {
            return;
        }
        // Surface-ruin AIR cells are clearance intent. Most are already empty; only retain and
        // broadcast the ones that actually remove a natural vegetation overlay.
        if (placement.blockType() == Blocks.AIR
                && WorldTickLoop.residentBlockType(accessor, pos.x(), pos.y(), pos.z()) == Blocks.AIR) {
            return;
        }
        setIndexedOverlay(pos.x(), pos.y(), pos.z(),
                placement.blockType(), placement.blockState());
        if (!ownerTurnMayContinue()) return;
        structureOverlaySites.add(structurePositionKey(pos.x(), pos.y(), pos.z()));
        enqueuePresentedDecoration(pos.x(), pos.y(), pos.z(), placement.blockType());
        if (placement.blockType() != Blocks.ENDER_CHEST
                && (Blocks.isChestShaped(placement.blockType())
                        || placement.blockType() == Blocks.BARREL)
                && ExplorationDecorator.hasConfiguredLoot(placement.kind())) {
            naturalLootChests.put(pos, placement.kind());
        }
    }

    /**
     * 거부된 예측 설치를 되돌리는 교정 방송입니다.
     *
     * <p>교정 값이 권위 오버레이와 blockType·state 모두 같아도 revision은 원칙적으로 올려야 합니다.
     * 클라이언트는 청크별 커서보다 크지 않은 delta를 이미 반영한 것으로 보고 버리므로(
     * {@code AuthoritativeChunkReconciler.acceptDelta}: {@code toVersion <= state.version}이면 무시),
     * 같은 revision을 다시 보내면 잘못 예측한 블록이 화면에 남습니다. snapshot의 toVersion은 압축된
     * body 안에 있어 캐시 프레임의 헤더만 고쳐 쓸 수도 없습니다.
     *
     * <p>단 하나 안전한 예외가 있습니다. 같은 값의 오버레이 항목이 아직 전송 대기(live journal)에
     * 남아 있으면 클라이언트 커서는 그 revision보다 작으므로, 대기 중인 항목 자체가 교정이 됩니다.
     * 이때만 revision 증가(=청크 버전 전진 → snapshot 프레임 캐시 무효화 → 전체 재인코딩)를 생략합니다.</p>
     */
    void broadcastDirectBlock(int x, int y, int z, int blockType) {
        if (!ownerTurnMayContinue()) return;
        short state = (short) blockState(x, y, z, blockType);
        if (correctionAlreadyQueued(x, y, z, blockType, state)) return;
        advanceOverlayRevisionForCorrectionWithoutQueue(x, y, z, blockType, state);
        boolean delivered = ctx.broadcaster() != null
                && ctx.broadcaster().enqueueBroadcastFromTick(worldId,
                        new BlockUpdate(List.of(new Block(x, y, z, (short) blockType, state,
                                overlayRevision(x, y, z)))));
        if (!delivered) enqueueCurrentOverlayBlock(x, y, z, blockType);
    }

    // ── 매니저가 붙이는 실행기/폐기 콜백 ──
    void attach(ScheduledExecutorService executor, Runnable onDispose) {
        this.executor = executor;
        this.onDispose = onDispose;
    }

    void attachAuthorityEvidenceRuntimeCallbacks(AuthorityEvidenceRuntimeCallbacks callbacks) {
        if (started) {
            throw new IllegalStateException("authority evidence callbacks must bind before start");
        }
        authorityEvidenceRuntimeCallbacks = callbacks;
        accessor.setCapacityEvictionListener(callbacks == null ? null
                : (chunkX, chunkZ) -> callbacks.capacityChunkEvicted(
                        worldId, chunkX, chunkZ));
    }

    void authorityEvidencePlayerMoved(String nickname, int chunkX, int chunkZ) {
        if (terminalPhase == TerminalPhase.RUNNING && authorityEvidenceRuntimeCallbacks != null) {
            authorityEvidenceRuntimeCallbacks.playerMoved(worldId, nickname, chunkX, chunkZ);
        }
    }

    /** 첫 입장 준비와 welcome 스냅샷 고정 뒤 월드 틱을 한 번만 시작합니다. */
    synchronized void start() {
        if (started || disposed || terminalPhase != TerminalPhase.RUNNING || executor == null) {
            return;
        }
        if (animalSettlements == null) {
            throw new IllegalStateException("animal settlement persistence must bind before start");
        }
        testSimulationChunks.clear();
        started = true;
        refreshActiveSimulationChunks();
        if (ArchitectureTurnEvent.enabled()) {
            architectureNextTurnDeadlineNanos = System.nanoTime() + WorldTickLoop.TICK_BUDGET_NANOS;
            architectureScheduleAnchorKnown = true;
        }
        tickTask = executor.scheduleAtFixedRate(this::runTick, 100, 100, TimeUnit.MILLISECONDS);
    }

    // ── 접근자(틱 루프/매니저용) ──
    boolean customDimension() {
        return chunkProductSource instanceof com.gameexpert.world.dimension.DimensionChunkProductSource;
    }

    String dimensionKey() {
        return chunkProductSource instanceof com.gameexpert.world.dimension.DimensionChunkProductSource custom
                ? custom.dimension() : "overworld";
    }

    Long worldId() {
        return worldId;
    }

    int seed() {
        return seed;
    }

    /**
     * 이 월드의 난이도. 몹 규칙(MobWorldView)과 허기 시스템(Track SURV-H)이 함께 읽는 조회 API다.
     */
    public Difficulty difficulty() {
        return difficulty;
    }

    void initializeWorldSpawn(double x, double y, double z) {
        if (terminalPhase != TerminalPhase.RUNNING) return;
        if (worldSpawn == null) {
            worldSpawn = new int[] { (int) x, (int) y, (int) z };
        }
    }

    int[] worldSpawn() {
        int[] spawn = worldSpawn;
        if (spawn == null) {
            spawn = WorldSpawn.find(seed, chunkProductSource);
            worldSpawn = spawn;
        }
        return spawn;
    }

    EngineContext ctx() {
        return ctx;
    }

    WorldClock clock() {
        return clock;
    }

    ActionQueue actionQueue() {
        return actionQueue;
    }

    Map<String, PlayerTickState> players() {
        return players;
    }

    TerrainAccessor accessor() {
        return accessor;
    }

    BlockStateStorage blockStates() {
        return blockStates;
    }

    /**
     * 권위가 읽는 칸의 상태. {@link #blockStates} 가 엔진이 쓴 값을, 쓴 적 없는 칸은
     * {@link #generatedCarrierState} 가 carrier 투영을 답합니다.
     */
    int blockState(int x, int y, int z, int blockType) {
        return blockStates.get(x, y, z, blockType);
    }

    /**
     * [VANILLA-FLAME] 엔진이 상태를 쓴 적 없는 칸의 상태를 스냅샷과 같은 carrier 투영으로 읽습니다.
     *
     * <p>스냅샷 인코더는 생성 칸의 carrier exact state 를 엔진 의미 바이트로 투영해 보냅니다
     * (CONTRACT §4.1). 권위가 그 칸을 상태 0 으로 읽으면 클라에는 매달린 랜턴이 권위에는 선
     * 랜턴이라, 이웃 갱신의 지지 판정이 아래 칸을 보고 떨어뜨립니다. 발광 이끼의 부착면, 천장
     * 자수정의 방향, 계단·울타리·침대 형상도 같은 이유로 틀어졌습니다. 그래서 투영이 정의된 모든
     * 블록에서 같은 규칙을 씁니다 — 촛불 전용이던 예외를 일반화한 것입니다.
     *
     * <p>범위는 인코더와 같습니다. sparse carrier 상태는 모든 블록을 투영하고, sparse 평면에 없는
     * 칸은 그 칸의 carrier 상태인 코드 0 을 투영합니다 — 코드 0 은 카탈로그 기본 exact state 라
     * 바닐라 기본과 다를 수 있습니다(남향 참나무 계단, 위쪽 끝 뾰족 점적석, 물에 잠긴 산호 …).
     * 정적판 {@code StandaloneWorldChunks} 와 같은 범위입니다. 칸이 생성 블록과 다르거나, 엔진이
     * 명시적으로 쓴 값(0 포함)이 있거나, 플레이어·몹·구조물이 소유한 칸이면 0 입니다.
     */
    private int generatedCarrierState(int x, int y, int z, int blockType) {
        if (customDimension()) {
            if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return 0;
            var source = accessor.snapshotSource(Math.floorDiv(x, Blocks.CHUNK_X), Math.floorDiv(z, Blocks.CHUNK_Z));
            int index = Blocks.blockIndex(Math.floorMod(x, Blocks.CHUNK_X), y, Math.floorMod(z, Blocks.CHUNK_Z));
            return source == null || source.blockTypeAt(index) != blockType ? 0 : source.blockStateAt(index);
        }
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return 0;
        var source = accessor.snapshotSource(Math.floorDiv(x, Blocks.CHUNK_X), Math.floorDiv(z, Blocks.CHUNK_Z));
        var carrier = source == null ? null : source.finalLiveCarrierOrNull();
        if (carrier == null) return 0;
        int index = Blocks.blockIndex(Math.floorMod(x, Blocks.CHUNK_X), y, Math.floorMod(z, Blocks.CHUNK_Z));
        if (source.generatedBlockTypeAt(index) != blockType) return 0;
        var exact = carrier.stateOverrideAt(index);
        Map<BlockPos, OverlayValue> overlay = overlayChunk(blockChunkKey(x, z), false);
        if (exact == null) {
            // Every remaining check below can only lower this cell's state to 0, and an unedited chunk has no
            // explicit overlay to raise it, so a block whose catalogue default projects to 0 is 0 already.
            if (overlay == null && defaultCarrierProjection(carrier, blockType) == 0) return 0;
            exact = carrier.defaultState(blockType);
        } else if (exact.blockId() != blockType) return 0;
        var resident = accessor.residentBlock(x, y, z);
        if (!resident.isAvailable() || resident.blockType() != blockType) return 0;
        BlockPos pos = new BlockPos(x, y, z);
        OverlayValue explicit = overlay == null ? null : overlay.get(pos);
        if (explicit != null && (explicit.blockType & 0xffff) == blockType) return explicit.state & 0xff;
        if (protectedDecorationEdits.contains(pos) || mobMutationSites.contains(pos)
                || structureOverlaySites.contains(structurePositionKey(x, y, z))) return 0;
        return CarrierStateProjection.projectState(blockType, exact);
    }

    /** Projection of a block's catalogue default state, which is the same for every cell of this world. */
    private int defaultCarrierProjection(NeutralFinalChunk carrier, int blockType) {
        if (blockType < 0 || blockType >= defaultCarrierProjections.length) {
            return CarrierStateProjection.projectState(blockType, carrier.defaultState(blockType));
        }
        int known = defaultCarrierProjections[blockType];
        if (known == 0) {
            known = CarrierStateProjection.projectState(blockType, carrier.defaultState(blockType)) + 1;
            defaultCarrierProjections[blockType] = known;
        }
        return known - 1;
    }

    /** Per block ID, {@link #defaultCarrierProjection} plus one; 0 means not asked yet. */
    private final int[] defaultCarrierProjections = new int[Blocks.BLOCK_ID_TABLE_CAPACITY];

    /**
     * owner의 sparse overlay 상태를 고정합니다. 엔진이 명시적으로 쓴 값(0 포함)은 encoder 가
     * carrier 투영으로 덮지 않으며, default carrier 투영은 encoder의 cell scan에 맡깁니다.
     */
    private Map<Integer, Integer> captureExplicitStates(TerrainAccessor.SnapshotSource source) {
        var carrier = source.finalLiveCarrier().orElse(null);
        if (carrier == null) return Map.of();
        Map<Integer, Integer> captured = new HashMap<>();
        Map<BlockPos, OverlayValue> overlay = overlayChunk(chunkKey(source.chunkX(), source.chunkZ()), false);
        if (overlay != null) {
            overlay.forEach((pos, value) -> {
                int index = Blocks.blockIndex(Math.floorMod(pos.x(), Blocks.CHUNK_X), pos.y(),
                        Math.floorMod(pos.z(), Blocks.CHUNK_Z));
                captured.put(index, value.state & 0xff);
            });
        }
        return Map.copyOf(captured);
    }

    /** sparse block-state 저장소와 immutable encoder view를 같은 owner instant로 유지합니다. */
    void setBlockState(int x, int y, int z, int blockType, int blockState) {
        if (tickLoop != null) {
            int before = WorldTickLoop.residentBlockType(accessor, x, y, z);
            if (before >= 0) tickLoop.noteRedstoneChange(x, y, z, before, blockStates.get(x, y, z, before));
        }
        if (terminalPhase != TerminalPhase.RUNNING || fleshCellReserved(x, y, z)) return;
        revokeChangedArchaeologyTarget(x, y, z, blockType, blockState);
        blockStates.set(x, y, z, blockType, blockState);
        accessor.setSnapshotBlockState(x, y, z, blockType, blockState);
        long key = blockChunkKey(x, z);
        if (!rehydratingReplayableChunks.contains(key)) {
            // 상태만 바꾸는 랜덤 틱도 immutable sender 바이트를 바꾼다. 이미 색인된
            // setIndexedOverlay 셀의 revision을 한 번 더 올리지 않도록 같은 깔때기를 사용한다.
            ensureOverlayRevision(x, y, z, blockType, (short) blockState);
        }
        invalidateLightColumnCaches(x, z);
    }

    private void revokeChangedArchaeologyTarget(int x, int y, int z,
            int blockType, int blockState) {
        ArchaeologyBrushableAggregate aggregate = archaeologyBrushableAt(x, y, z);
        if (aggregate == null || aggregate.consumed() || aggregate.revoked()) return;
        String next = exactArchaeologyBlockState(blockType, blockState);
        if (aggregate.currentExactBlockState().equals(next)) return;
        if (finalCarrierPersistence == null || !finalCarrierPersistence.revokeArchaeologyTarget(
                worldId, x, y, z, aggregate.installationIdentity(),
                aggregate.currentExactBlockState(), aggregate.targetRevision())) {
            throw new IllegalStateException("ARCH target override was not durably revoked");
        }
        aggregate.revoke(aggregate.currentExactBlockState(), aggregate.targetRevision());
    }

    boolean advanceArchaeologyBrushTarget(int x, int y, int z,
            int blockType, int blockState) {
        if (terminalPhase != TerminalPhase.RUNNING) return false;
        ArchaeologyBrushableAggregate aggregate = archaeologyBrushableAt(x, y, z);
        if (aggregate == null || aggregate.consumed() || aggregate.revoked()
                || finalCarrierPersistence == null) return false;
        int currentBlock = WorldTickLoop.residentBlockType(accessor, x, y, z);
        int currentState = blockStates.get(x, y, z, currentBlock);
        String currentExact = exactArchaeologyBlockState(currentBlock, currentState);
        String nextExact = exactArchaeologyBlockState(blockType, blockState);
        if (currentExact == null || nextExact == null
                || !aggregate.currentExactBlockState().equals(currentExact)) return false;
        long revision = aggregate.targetRevision();
        if (!finalCarrierPersistence.advanceArchaeologyTarget(worldId, x, y, z,
                aggregate.installationIdentity(), currentExact, revision, nextExact)) return false;
        aggregate.advanceTarget(currentExact, revision, nextExact);
        setBlockState(x, y, z, blockType, blockState);
        return true;
    }

    private static String exactArchaeologyBlockState(int blockType, int blockState) {
        if (blockType == Blocks.SUSPICIOUS_SAND) {
            return "minecraft:suspicious_sand[dusted=" + Blocks.brushDusted(blockState) + "]";
        }
        if (blockType == Blocks.SUSPICIOUS_GRAVEL) {
            return "minecraft:suspicious_gravel[dusted=" + Blocks.brushDusted(blockState) + "]";
        }
        return null;
    }

    /**
     * [TRIAL] 트라이얼 스포너·금고의 권위 상태 비트를 실제 블록에 쓴다.
     *
     * <p>몹 유발 변형({@code applyJournaledMobMutation})과 같은 세 갈래다: overlay 색인으로
     * 스냅샷 바이트를 바꾸고, 희소 상태 저장소를 갱신하고, tick 변경 집합에 넣어 접속 중인
     * 클라이언트에 즉시 나가면서 diff 로 영속한다. 블록 <b>종류</b>는 바뀌지 않고 상태만
     * 바뀌므로 렌더는 {@code stateFaceTile} 로만 갈린다.</p>
     */
    synchronized void applyTrialFixtureState(int x, int y, int z, int blockType, int blockState) {
        if (terminalPhase != TerminalPhase.RUNNING) return;
        setIndexedOverlay(x, y, z, blockType, blockState);
        if (terminalPhase != TerminalPhase.RUNNING) return;
        tickBlockChanges.put(new BlockPos(x, y, z), (short) blockType);
    }

    void removeBlockState(int x, int y, int z) {
        if (terminalPhase != TerminalPhase.RUNNING || fleshCellReserved(x, y, z)) return;
        blockStates.remove(x, y, z);
        accessor.setSnapshotBlockState(x, y, z, Blocks.AIR, 0);
    }

    FluidSimulator fluidSim() {
        return fluidSim;
    }

    FallingBlockSystem fallingBlockSystem() {
        return fallingBlockSystem;
    }

    EnvironmentSystem environment() {
        return environment;
    }

    MobSystem mobSystem() {
        return mobSystem;
    }

    long nextEventId() {
        return mobSystem.nextEventId();
    }

    /** 상자 보관소(틱 스레드 전용). */
    ChestStorage chestStorage() {
        return chestStorage;
    }

    private java.util.List<com.gameexpert.ws.dto.WsMessages.PotDecorationsUpdate> potDecorationSnapshot(
            TerrainAccessor.SnapshotSource source) {
        var out = new java.util.ArrayList<com.gameexpert.ws.dto.WsMessages.PotDecorationsUpdate>();
        for (ChestStorage.PositionedPotComponents positioned : chestStorage.potSnapshots(
                source.chunkX(), source.chunkZ())) {
            String components = positioned.components();
            if (components == null) continue;
            int index = Blocks.blockIndex(Math.floorMod(positioned.x(), Blocks.CHUNK_X),
                    positioned.y(), Math.floorMod(positioned.z(), Blocks.CHUNK_Z));
            if (!Blocks.isDecoratedPot(source.blockTypeAt(index))) continue;
            int[] faces = com.gameexpert.engine.inventory.ItemComponentCodec.decode(
                    (short) Blocks.DECORATED_POT, components).potDecorations().stream()
                    .mapToInt(face -> Short.toUnsignedInt(face.itemType())).toArray();
            if (faces.length == 4) out.add(new com.gameexpert.ws.dto.WsMessages.PotDecorationsUpdate(
                    positioned.x(), positioned.y(), positioned.z(), faces));
        }
        return java.util.List.copyOf(out);
    }

    java.util.List<com.gameexpert.ws.dto.WsMessages.ShelfUpdate> shelfSnapshots() {
        java.util.List<com.gameexpert.ws.dto.WsMessages.ShelfUpdate> out = new java.util.ArrayList<>();
        for (ChestStorage.PositionedInventory positioned : chestStorage.snapshots()) {
            // Warm rejoin can inspect retained spawn chunks before the owner restores its active
            // union. Welcome classification must leave their activation claim for that owner.
            int block = accessor.peekBlockWithoutNotification(
                    positioned.x(), positioned.y(), positioned.z());
            if (!Blocks.isShelf(block) || positioned.inventory().slots() != 3) continue;
            int state = blockStates.get(positioned.x(), positioned.y(), positioned.z(), block);
            out.add(WorldTickLoop.shelfUpdate(positioned.x(), positioned.y(), positioned.z(),
                    state, positioned.inventory()));
        }
        return java.util.List.copyOf(out);
    }

    /**
     * Small shared analog-query boundary. There is no redstone propagation engine yet, so callers
     * may ask an individual block for its current output without manufacturing a second network.
     */
    int analogOutputAt(int x, int y, int z, int queriedDirection) {
        int block = accessor.getBlock(x, y, z);
        if (block < 0) return 0;
        int state = blockStates.get(x, y, z, block);
        if (Blocks.isShelf(block)) {
            ChestInventory shelf = chestStorage.peekAt(x, y, z);
            if (shelf == null || shelf.slots() != com.gameexpert.engine.shelf.ShelfRules.SLOTS) {
                return 0;
            }
            boolean[] occupied = new boolean[com.gameexpert.engine.shelf.ShelfRules.SLOTS];
            for (int slot = 0; slot < occupied.length; slot++) {
                occupied[slot] = shelf.itemType(slot) != PlayerInventory.EMPTY;
            }
            return com.gameexpert.engine.shelf.ShelfRules.analogOutput(
                    state, queriedDirection, occupied);
        }
        // [COMPARATOR] AbstractContainerMenu.getRedstoneSignalFromContainer over the resident
        // block entity. This query never materialises LOOT (a comparator block would first have
        // to go through the hopper's first-open resolution); an unopened table reads empty.
        if (block != Blocks.ENDER_CHEST && Blocks.isChestShaped(block)) {
            return chestAnalogOutput(x, y, z, block);
        }
        if (block == Blocks.BARREL || Blocks.isShulkerBox(block) || Blocks.isDecoratedPot(block)
                || block == Blocks.HOPPER) {
            return containerSignal(chestStorage.peekAt(x, y, z));
        }
        if (block == Blocks.DISPENSER || block == Blocks.DROPPER) {
            return containerSignal(chestStorage.peekAt(x, y, z));
        }
        // CrafterBlock.getAnalogOutputSignal: CrafterBlockEntity.getRedstoneSignal, the number of
        // slots that hold an item or are disabled.
        if (block == Blocks.CRAFTER) {
            return com.gameexpert.engine.dispenser.CrafterRules.redstoneSignal(
                    chestStorage.peekAt(x, y, z), crafterDisabledSlots(x, y, z));
        }
        if (FurnaceRules.isFurnace(block)) {
            FurnaceInventory furnace = furnaceStorage.peekAt(x, y, z);
            if (furnace == null) return 0;
            com.gameexpert.engine.hopper.HopperStack[] slots =
                    new com.gameexpert.engine.hopper.HopperStack[FurnaceInventory.SLOTS];
            for (int slot = 0; slot < slots.length; slot++) {
                slots[slot] = com.gameexpert.engine.hopper.HopperStack.of(
                        furnace.itemType(slot), furnace.count(slot));
            }
            return com.gameexpert.engine.hopper.HopperRules.redstoneSignalFromContainer(
                    slots, com.gameexpert.engine.hopper.HopperRules.DEFAULT_CONTAINER_MAX_STACK);
        }
        if (block == Blocks.BREWING_STAND) {
            BrewingInventory stand = brewingStorage.peekAt(x, y, z);
            if (stand == null) return 0;
            com.gameexpert.engine.hopper.HopperStack[] slots =
                    new com.gameexpert.engine.hopper.HopperStack[BrewingInventory.SLOTS];
            for (int slot = 0; slot < slots.length; slot++) {
                slots[slot] = com.gameexpert.engine.hopper.HopperStack.of(
                        stand.itemType(slot), stand.count(slot));
            }
            return com.gameexpert.engine.hopper.HopperRules.redstoneSignalFromContainer(
                    slots, com.gameexpert.engine.hopper.HopperRules.DEFAULT_CONTAINER_MAX_STACK);
        }
        if (block == Blocks.COMPOSTER) return ComposterRules.level(state);
        // [JUKEBOX] JukeboxBlockEntity.getComparatorOutput: 든 음반 곡의 comparator_output.
        if (block == Blocks.JUKEBOX) {
            return com.gameexpert.engine.jukebox.JukeboxRules.comparatorOutput(
                    tickLoop.jukeboxDisc(x, y, z).itemType());
        }
        if (block == Blocks.CAKE) {
            return com.gameexpert.engine.hopper.HopperRules.cakeSignal(state & 0x07);
        }
        if (block == Blocks.END_PORTAL_FRAME) {
            return (state & EndPortalFrameRules.EYE) != 0 ? 15 : 0;
        }
        if (block == Blocks.BEE_NEST || block == Blocks.BEEHIVE) {
            return (state & BuildingBlockRules.BEEHIVE_HONEY_MASK)
                    >>> BuildingBlockRules.BEEHIVE_HONEY_SHIFT;
        }
        if (block == Blocks.CAULDRON) {
            return com.gameexpert.engine.hopper.HopperRules.cauldronSignal(
                    com.gameexpert.engine.inventory.CauldronRules.kind(state),
                    com.gameexpert.engine.inventory.CauldronRules.level(state));
        }
        if (Blocks.isCopperBulb(block)) return Blocks.isCopperBulbLit(block) ? 15 : 0;
        if (block >= Blocks.COPPER_GOLEM_STATUE
                && block <= Blocks.WAXED_OXIDIZED_COPPER_GOLEM_STATUE) {
            return com.gameexpert.engine.hopper.HopperRules.copperGolemStatueSignal(state >>> 2);
        }
        if (block == Blocks.LECTERN && lecternPersistence != null) {
            return lecternPersistence.load(worldId, x, y, z)
                    .map(lectern -> com.gameexpert.engine.hopper.HopperRules.lecternSignal(true,
                            lectern.page(), lectern.pageCount()))
                    .orElse(0);
        }
        return 0;
    }

    /**
     * {@code ChestBlock.getAnalogOutputSignal}: {@code getContainer(..., ignoreBlocked=false)}. A
     * redstone-conductor block above either half closes the combined container (signal 0).
     */
    private int chestAnalogOutput(int x, int y, int z, int block) {
        List<BlockPos> halves = tickLoop.hopperChestHalves(x, y, z, block);
        List<com.gameexpert.engine.hopper.HopperStack> slots = new ArrayList<>();
        for (BlockPos half : halves) {
            int above = accessor.getBlock(half.x(), half.y() + 1, half.z());
            if (above >= 0 && BuildingBlockRules.isRedstoneConductor(above,
                    blockStates.get(half.x(), half.y() + 1, half.z(), above))) return 0;
        }
        for (BlockPos half : halves) {
            ChestInventory inventory = chestStorage.peekAt(half.x(), half.y(), half.z());
            for (int slot = 0; slot < ChestInventory.SLOTS; slot++) {
                slots.add(inventory == null || slot >= inventory.slots()
                        ? com.gameexpert.engine.hopper.HopperStack.EMPTY
                        : HopperSystem.stackAt(inventory, slot));
            }
        }
        return com.gameexpert.engine.hopper.HopperRules.redstoneSignalFromContainer(
                slots.toArray(new com.gameexpert.engine.hopper.HopperStack[0]),
                com.gameexpert.engine.hopper.HopperRules.DEFAULT_CONTAINER_MAX_STACK);
    }

    private static int containerSignal(ChestInventory inventory) {
        if (inventory == null) return 0;
        com.gameexpert.engine.hopper.HopperStack[] slots =
                new com.gameexpert.engine.hopper.HopperStack[inventory.slots()];
        for (int slot = 0; slot < slots.length; slot++) slots[slot] = HopperSystem.stackAt(inventory, slot);
        return com.gameexpert.engine.hopper.HopperRules.redstoneSignalFromContainer(
                slots, com.gameexpert.engine.hopper.HopperRules.DEFAULT_CONTAINER_MAX_STACK);
    }

    /** [SHULKER-CONTENTS] 셜커 27칸 참조 저장소(틱 스레드 전용). */
    ShulkerContentsStorage shulkerStorage() {
        return shulkerStorage;
    }

    FurnaceStorage furnaceStorage() {
        return furnaceStorage;
    }

    BrewingStorage brewingStorage() {
        return brewingStorage;
    }

    CampfireStorage campfireStorage() {
        return campfireStorage;
    }

    WorldTickLoop tickLoop() {
        return tickLoop;
    }

    /**
     * Returns a resident container only. A final-carrier LOOT candidate must enter
     * {@link #requestCanonicalLootOpen}; this accessor deliberately never calls a persistence
     * service on the owner thread.
     */
    ChestInventory openChest(int x, int y, int z) {
        if (terminalPhase != TerminalPhase.RUNNING) return chestStorage.peekAt(x, y, z);
        BlockPos position = new BlockPos(x, y, z);
        CanonicalLootContainerKind canonicalKind = canonicalLootKindAt(x, y, z);
        if (finalCarrierPersistence != null
                && canonicalLootCandidateAt(position, canonicalKind)
                && !canonicalLootFirstOpenInstalledAt(position, canonicalKind)) {
            return null;
        }
        ChestInventory existing = chestStorage.peekAt(x, y, z);
        if (existing != null && existing.persistenceRevision() > 0L) return existing;
        return openLegacyChest(position);
    }

    /**
     * Canonical dispenser first-use boundary; redstone/action callers share this durable state.
     * After its first-open commit a dispenser is an ordinary nine-slot coordinate row, exactly
     * like a committed LOOT chest, so gameplay writes and restarts use the chest save queue.
     */
    ChestInventory openDispenser(int x, int y, int z) {
        return openContainer(x, y, z);
    }

    ChestInventory openDecoratedPot(int x, int y, int z) {
        return openContainer(x, y, z);
    }

    /**
     * Returns the resident canonical inventory only after first-use has committed. Ordinary
     * dispensers and pots retain the existing generic coordinate-keyed container storage.
     */
    ChestInventory openContainer(int x, int y, int z) {
        // [HOPPER] HopperBlockEntity: five coordinate slots, never a LOOT carrier.
        // [CONTAINER-MENUS] Nor are the (never generated) dropper and the crafter: nine slots.
        int resident = accessor.getBlock(x, y, z);
        if (resident == Blocks.HOPPER || resident == Blocks.DROPPER || resident == Blocks.CRAFTER) {
            return terminalPhase != TerminalPhase.RUNNING ? chestStorage.peekAt(x, y, z)
                    : chestStorage.openAt(x, y, z, BlockEntityRules.chestStorageSlots(resident));
        }
        CanonicalLootContainerKind kind = canonicalLootKindAt(x, y, z);
        if (kind != CanonicalLootContainerKind.DISPENSER
                && kind != CanonicalLootContainerKind.DECORATED_POT) {
            return openChest(x, y, z);
        }
        BlockPos position = new BlockPos(x, y, z);
        if (terminalPhase != TerminalPhase.RUNNING) {
            return chestStorage.peekAt(x, y, z);
        }
        if (canonicalFirstOpenRequired(kind)
                && canonicalLootCandidateAt(position, kind)
                && !canonicalLootFirstOpenInstalledAt(position, kind)) {
            return null;
        }
        return chestStorage.openAt(x, y, z, kind.slots());
    }

    /** Whether this container kind must cross the canonical first-open writer boundary. */
    boolean canonicalFirstOpenRequired(CanonicalLootContainerKind kind) {
        return kind != null && finalCarrierPersistence != null;
    }

    /** Canonical first-open is reserved for generated loot coordinates, not player containers. */
    boolean canonicalFirstOpenRequired(CanonicalLootContainerKind kind, int x, int y, int z) {
        return canonicalFirstOpenRequired(kind)
                && canonicalLootCandidateAt(new BlockPos(x, y, z), kind);
    }

    private boolean canonicalLootCandidateAt(BlockPos position,
            CanonicalLootContainerKind kind) {
        if (position == null || kind == null) return false;
        synchronized (this) {
            if (canonicalLootCandidates.get(position) == kind) return true;
            boolean playerOverride = durableBlockDiffCells.containsKey(position);
            for (CanonicalLootOpenReservation reservation : canonicalLootOpenReservations.values()) {
                if (reservation.key.orderedHalves().stream().anyMatch(half ->
                        half.containerKind() == kind && half.position().equals(position)
                                && half.playerOverride() == playerOverride)) {
                    return true;
                }
            }
            return canonicalLootFirstOpenCompleted.stream().anyMatch(half ->
                    half.containerKind() == kind && half.position().equals(position)
                            && half.playerOverride() == playerOverride)
                    || canonicalLootFirstOpenRejected.stream().anyMatch(half ->
                            half.containerKind() == kind && half.position().equals(position)
                                    && half.playerOverride() == playerOverride);
        }
    }

    private CanonicalLootContainerKind canonicalLootKindAt(int x, int y, int z) {
        int block = accessor.getBlock(x, y, z);
        if (block < 0) return null;
        if (block != Blocks.ENDER_CHEST && Blocks.isChestShaped(block)) {
            return CanonicalLootContainerKind.CHEST;
        }
        if (block == Blocks.BARREL) return CanonicalLootContainerKind.BARREL;
        if (block == Blocks.DISPENSER) return CanonicalLootContainerKind.DISPENSER;
        return Blocks.isDecoratedPot(block) ? CanonicalLootContainerKind.DECORATED_POT : null;
    }

    /**
     * Admits one player-facing canonical first-open request. The writer task replays every ordered
     * half on every retry; the owner installs both results only after that task has completed.
     * A return value of zero is a fail-closed rejection (terminal, missing aggregate service, or
     * missing writer). A positive token means the request is reserved, including a temporarily
     * rejected writer submission that will be retried on a later owner turn.
     */
    long requestCanonicalChestOpen(PlayerTickState session, String connectionId,
            int clickedBlock, boolean reached, BlockPos clicked, List<BlockPos> orderedHalves,
            Consumer<CanonicalLootOpenCompletion> completion) {
        if (clicked == null) return 0L;
        CanonicalLootContainerKind kind = canonicalLootKindAt(
                clicked.x(), clicked.y(), clicked.z());
        if (kind == null || !canonicalFirstOpenRequired(kind)) return 0L;
        return requestCanonicalLootOpen(session, connectionId, clickedBlock, reached,
                clicked, orderedHalves, kind, completion);
    }

    /** Non-UI first-use seam for canonical dispenser callers and integration tests. */
    long requestCanonicalDispenserFirstOpen(int x, int y, int z,
            Consumer<CanonicalLootOpenCompletion> completion) {
        if (!canonicalFirstOpenRequired(CanonicalLootContainerKind.DISPENSER)) return 0L;
        BlockPos position = new BlockPos(x, y, z);
        return requestCanonicalLootOpen(null, null, Blocks.DISPENSER, true,
                position, List.of(position), CanonicalLootContainerKind.DISPENSER, completion);
    }

    private long requestCanonicalLootOpen(PlayerTickState session, String connectionId,
            int clickedBlock, boolean reached, BlockPos clicked, List<BlockPos> orderedHalves,
            CanonicalLootContainerKind kind, Consumer<CanonicalLootOpenCompletion> completion) {
        if (finalCarrierPersistence == null || chestPersistence == null || ctx.persistenceExecutor() == null) {
            return 0L;
        }
        if (clicked == null || orderedHalves == null || orderedHalves.isEmpty()
                || orderedHalves.size() > 2 || completion == null
                || orderedHalves.stream().anyMatch(java.util.Objects::isNull)
                || orderedHalves.stream().distinct().count() != orderedHalves.size()
                || !orderedHalves.contains(clicked)) {
            return 0L;
        }
        CanonicalLootOpenRequest request;
        CanonicalLootOpenReservation reservation = null;
        CanonicalLootOpenCompletion immediate = null;
        boolean submit = false;
        synchronized (this) {
            if (terminalPhase != TerminalPhase.RUNNING || disposed) return 0L;
            if (session != null
                    && (players.get(session.nickname()) != session
                            || !java.util.Objects.equals(connectionId,
                                    playerConnections.get(session.nickname())))) {
                return 0L;
            }
            long token = ++canonicalLootOpenRequestSequence;
            request = new CanonicalLootOpenRequest(session, connectionId, token,
                    clickedBlock, reached, clicked, orderedHalves, kind, completion);
            if (session != null) pendingCanonicalLootOpenRequests.put(session, request);

            List<CanonicalLootOpenHalf> halves = new ArrayList<>(orderedHalves.size());
            for (BlockPos position : orderedHalves) {
                halves.add(new CanonicalLootOpenHalf(position, kind,
                        durableBlockDiffCells.containsKey(position)));
            }
            CanonicalLootOpenKey key = new CanonicalLootOpenKey(halves);
            if (halves.stream().anyMatch(canonicalLootFirstOpenRejected::contains)) {
                immediate = new CanonicalLootOpenCompletion(request,
                        CanonicalLootOpenStatus.REJECTED);
            } else if (canonicalLootFirstOpenCompleted.containsAll(halves)) {
                CanonicalLootOpenStatus status = canonicalLootOpenReady(halves)
                        ? CanonicalLootOpenStatus.READY : CanonicalLootOpenStatus.REJECTED;
                immediate = new CanonicalLootOpenCompletion(request, status);
            } else {
                reservation = canonicalLootOpenReservations.get(key);
                if (reservation == null) {
                    reservation = new CanonicalLootOpenReservation(key);
                    canonicalLootOpenReservations.put(key, reservation);
                    for (CanonicalLootOpenHalf half : halves) {
                        pendingCanonicalLootCoordinates.merge(half.position(), 1, Integer::sum);
                    }
                    submit = true;
                }
                reservation.requests.add(request);
            }
        }
        if (immediate != null) {
            invokeCanonicalLootOpenCompletion(immediate);
            return request.requestToken();
        }
        if (submit && !submitCanonicalLootOpen(reservation)) return 0L;
        return request.requestToken();
    }

    private boolean canonicalLootOpenReady(List<CanonicalLootOpenHalf> halves) {
        for (CanonicalLootOpenHalf half : halves) {
            ChestInventory inventory = chestStorage.peekAt(
                    half.position().x(), half.position().y(), half.position().z());
            if (inventory == null || inventory.persistenceRevision() <= 0L) return false;
        }
        return true;
    }

    private boolean canonicalLootFirstOpenInstalledAt(BlockPos position,
            CanonicalLootContainerKind kind) {
        boolean playerOverride;
        synchronized (this) {
            playerOverride = durableBlockDiffCells.containsKey(position);
            return canonicalLootFirstOpenCompleted.contains(
                    new CanonicalLootOpenHalf(position, kind, playerOverride));
        }
    }

    private boolean submitCanonicalLootOpen(CanonicalLootOpenReservation reservation) {
        if (reservation == null) return false;
        PersistenceExecutor writer = ctx.persistenceExecutor();
        if (writer == null) {
            cancelCanonicalLootOpen(reservation);
            return false;
        }
        synchronized (this) {
            if (canonicalLootOpenReservations.get(reservation.key) != reservation
                    || reservation.cancelled || terminalPhase != TerminalPhase.RUNNING
                    || reservation.submissionReserved) {
                return canonicalLootOpenReservations.get(reservation.key) == reservation
                        && !reservation.cancelled;
            }
            reservation.submissionReserved = true;
            reservation.retryQueued = false;
        }
        boolean accepted;
        try {
            accepted = writer.trySubmit(() -> runCanonicalLootOpen(reservation));
        } catch (RuntimeException | Error failure) {
            log.warn("Canonical LOOT first-open submission failed for world {}", worldId, failure);
            accepted = false;
        }
        if (accepted) return true;
        synchronized (this) {
            if (canonicalLootOpenReservations.get(reservation.key) != reservation) return false;
            reservation.submissionReserved = false;
            if (terminalPhase == TerminalPhase.RUNNING) {
                enqueueCanonicalLootOpenRetryLocked(reservation);
            } else {
                cancelCanonicalLootOpenLocked(reservation);
            }
            return terminalPhase == TerminalPhase.RUNNING;
        }
    }

    private void runCanonicalLootOpen(CanonicalLootOpenReservation reservation) {
        synchronized (this) {
            // A task that was admitted before DRAINING may still be sitting in the writer queue.
            // It is unsent work at that boundary, so do not let it start a late JPA transaction.
            if (reservation == null
                    || reservation.cancelled
                    || canonicalLootOpenReservations.get(reservation.key) != reservation
                    || terminalPhase != TerminalPhase.RUNNING) {
                return;
            }
        }
        List<CanonicalLootFirstOpenResult> results = new ArrayList<>(reservation.key.orderedHalves().size());
        try {
            for (CanonicalLootOpenHalf half : reservation.key.orderedHalves()) {
                BlockPos position = half.position();
                var prepared = finalCarrierPersistence.prepareLateCanonicalLoot(worldId,
                        position.x(),position.y(),position.z(),half.containerKind(),
                        half.playerOverride(),chunkProductSource);
                CanonicalLootFirstOpenResult result = prepared==null
                        ? finalCarrierPersistence.resolveCanonicalLootFirstOpen(
                                worldId, position.x(), position.y(), position.z(),
                                half.containerKind(), half.playerOverride(),
                                chestPersistence::installCanonicalLootJoiningTransaction)
                        : finalCarrierPersistence.resolveCanonicalLootFirstOpen(
                                worldId, position.x(), position.y(), position.z(),
                                half.containerKind(), half.playerOverride(),
                                chestPersistence::installCanonicalLootJoiningTransaction,prepared);
                if (result != null
                        && result.status() == CanonicalLootFirstOpenResult.Status.RESOLVED
                        && result.resolution() == null) {
                    throw new IllegalStateException("resolved canonical LOOT has no resolution");
                }
                results.add(result);
            }
            List<CanonicalLootFirstOpenResult> replay = List.copyOf(results);
            enqueuePersistenceCompletion(
                    () -> completeCanonicalLootOpen(reservation, replay),
                    () -> cancelCanonicalLootOpen(reservation));
        } catch (com.gameexpert.engine.persistence.finalcarrier.reference.LateCanonicalLootPreparationService.Retry retry) {
            enqueuePersistenceCompletion(
                    () -> retryCanonicalLootOpenAfterUnknownOutcome(reservation),
                    () -> cancelCanonicalLootOpen(reservation));
        } catch (RuntimeException | Error unknownOutcome) {
            // A failure may have happened after one half committed. The next task replays the
            // complete ordered pair, so the first half observes ALREADY_COMMITTED and the second
            // half is never silently skipped.
            log.warn("Canonical LOOT first-open outcome is unknown for world {}", worldId,
                    unknownOutcome);
            enqueuePersistenceCompletion(
                    () -> retryCanonicalLootOpenAfterUnknownOutcome(reservation),
                    () -> cancelCanonicalLootOpen(reservation));
        }
    }

    private void retryCanonicalLootOpenAfterUnknownOutcome(
            CanonicalLootOpenReservation reservation) {
        synchronized (this) {
            if (canonicalLootOpenReservations.get(reservation.key) != reservation
                    || reservation.cancelled || terminalPhase != TerminalPhase.RUNNING) {
                cancelCanonicalLootOpenLocked(reservation);
                return;
            }
            reservation.submissionReserved = false;
            enqueueCanonicalLootOpenRetryLocked(reservation);
        }
    }

    /** Owner-only retry admission; one failed submission cannot spin in the same owner turn. */
    void pumpCanonicalLootOpenRetries() {
        if (ownerQueueRequired() || !ownerTurnMayContinue()) return;
        for (int attempt = 0; attempt < 8; attempt++) {
            CanonicalLootOpenReservation reservation;
            synchronized (this) {
                reservation = canonicalLootOpenRetries.pollFirst();
                if (reservation == null) return;
                reservation.retryQueued = false;
                if (canonicalLootOpenReservations.get(reservation.key) != reservation
                        || reservation.cancelled) continue;
            }
            submitCanonicalLootOpen(reservation);
            synchronized (this) {
                if (reservation.retryQueued) return;
            }
        }
    }

    private void enqueueCanonicalLootOpenRetryLocked(CanonicalLootOpenReservation reservation) {
        if (reservation.retryQueued || reservation.cancelled
                || terminalPhase != TerminalPhase.RUNNING) return;
        reservation.retryQueued = true;
        canonicalLootOpenRetries.addLast(reservation);
    }

    private void completeCanonicalLootOpen(CanonicalLootOpenReservation reservation,
            List<CanonicalLootFirstOpenResult> results) {
        if (ownerQueueRequired()) {
            enqueuePersistenceCompletion(
                    () -> completeCanonicalLootOpen(reservation, results),
                    () -> cancelCanonicalLootOpen(reservation));
            return;
        }
        List<CanonicalLootOpenRequest> requests;
        CanonicalLootOpenStatus status;
        synchronized (this) {
            if (canonicalLootOpenReservations.get(reservation.key) != reservation
                    || reservation.cancelled || terminalPhase != TerminalPhase.RUNNING) {
                cancelCanonicalLootOpenLocked(reservation);
                return;
            }
            if (results.size() != reservation.key.orderedHalves().size()) {
                reservation.submissionReserved = false;
                enqueueCanonicalLootOpenRetryLocked(reservation);
                return;
            }
        }

        boolean rejected = results.stream().anyMatch(result -> result == null
                || result.status() == null
                || result.status() == CanonicalLootFirstOpenResult.Status.REJECTED);
        boolean ready = !rejected;
        if (!rejected) {
            try {
                // Prepare both inventories before publishing either one. A durable replay keeps
                // its already-loaded post-open state; only a first commit materializes fixed slots.
                // A paired request never exposes a half-installed container when preparation fails.
                List<ChestInventory> materialized = new ArrayList<>(results.size());
                for (int index = 0; index < results.size(); index++) {
                    CanonicalLootOpenHalf half = reservation.key.orderedHalves().get(index);
                    BlockPos position = half.position();
                    ChestInventory inventory =
                            chestStorage.peekAt(position.x(), position.y(), position.z());
                    if (inventory == null || inventory.persistenceRevision() <= 0L) {
                        inventory = half.containerKind() == CanonicalLootContainerKind.DISPENSER
                                ? chestPersistence.materializeCanonicalLoot(results.get(index).resolution())
                                : chestPersistence.restoreCommittedCanonicalLoot(
                                        worldId, position.x(), position.y(), position.z());
                    }
                    if (inventory == null || inventory.persistenceRevision() <= 0L) {
                        throw new IllegalStateException(
                                "canonical LOOT materialization is not durable");
                    }
                    materialized.add(inventory);
                }
                // The transaction has committed both map rows and container rows. Adopt every
                // exact returned map before exposing a filled-map stack to a live inventory.
                for (CanonicalLootFirstOpenResult result : results) {
                    for (WorldMapData map : result.materializedMaps()) {
                        acceptSettledMap(map);
                    }
                }
                for (int index = 0; index < materialized.size(); index++) {
                    CanonicalLootOpenHalf half = reservation.key.orderedHalves().get(index);
                    ChestInventory inventory = materialized.get(index);
                    BlockPos position = half.position();
                    boolean fresh = chestStorage.peekAt(
                            position.x(), position.y(), position.z()) != inventory;
                    chestStorage.load(position.x(), position.y(), position.z(), inventory);
                    naturalLootChests.remove(position);
                    // The dispenser aggregate commits its LOOT in world_dispensers; its gameplay
                    // row is the nine-slot chest-schema row, first written by the save queue.
                    if (fresh && half.containerKind() == CanonicalLootContainerKind.DISPENSER) {
                        chestStorage.markDirty(position.x(), position.y(), position.z());
                    }
                }
            } catch (RuntimeException | Error ownerInstallationFailure) {
                log.warn("Canonical LOOT first-open owner installation failed for world {}",
                        worldId, ownerInstallationFailure);
                synchronized (this) {
                    if (canonicalLootOpenReservations.get(reservation.key) == reservation
                            && !reservation.cancelled
                            && terminalPhase == TerminalPhase.RUNNING) {
                        reservation.submissionReserved = false;
                        enqueueCanonicalLootOpenRetryLocked(reservation);
                    }
                }
                return;
            }
        }

        synchronized (this) {
            if (canonicalLootOpenReservations.get(reservation.key) != reservation) return;
            for (int index = 0; index < reservation.key.orderedHalves().size(); index++) {
                CanonicalLootOpenHalf half = reservation.key.orderedHalves().get(index);
                CanonicalLootFirstOpenResult result = results.get(index);
                if (result == null || result.status() == null
                        || result.status() == CanonicalLootFirstOpenResult.Status.REJECTED) {
                    canonicalLootFirstOpenRejected.add(half);
                } else if (!rejected) {
                    canonicalLootFirstOpenCompleted.add(half);
                }
            }
            requests = List.copyOf(reservation.requests);
            reservation.requests.clear();
            removeCanonicalLootOpenReservationLocked(reservation);
            status = ready ? CanonicalLootOpenStatus.READY : CanonicalLootOpenStatus.REJECTED;
        }
        for (CanonicalLootOpenRequest request : requests) {
            invokeCanonicalLootOpenCompletion(new CanonicalLootOpenCompletion(request, status));
        }
    }

    private ChestInventory openLegacyChest(BlockPos position) {
        ChestInventory chest = chestStorage.peekAt(position.x(), position.y(), position.z());
        if (chest == null) chest = materializeDimensionContainer(position);
        if (chest == null) chest = chestStorage.openAt(position.x(), position.y(), position.z());
        ExplorationDecorator.Kind kind = naturalLootChests.remove(position);
        if (kind != null) {
            ExplorationLoot.fill(chest, seed, position.x(), position.y(), position.z(), kind);
            chestStorage.markDirty(position.x(), position.y(), position.z());
        }
        return chest;
    }

    /**
     * [DIMENSION-EXT] 사용자 차원 콘텐츠가 선언한 초기 컨테이너를 한 번만 채운 상자 행으로 만든다. 좌표에
     * 상자 행이 이미 있으면(채웠거나 플레이어 상자) 아무것도 하지 않는다 — 한 번 채운 상자는 일반 상자
     * 행으로 영속하므로 재입장·재기동·재생성에서 다시 채워지지 않는다. 플레이어가 부수거나 다시 놓은
     * 칸(편집 보호 좌표)과 생성 블록이 아닌 칸은 채우지 않고 null 을 돌려준다.
     */
    private ChestInventory materializeDimensionContainer(BlockPos position) {
        if (!(chunkProductSource instanceof com.gameexpert.world.dimension.DimensionChunkProductSource custom)
                || protectedDecorationEdits.contains(position) || mobMutationSites.contains(position)
                || chestStorage.peekAt(position.x(), position.y(), position.z()) != null) {
            return null;
        }
        var container = custom.initialContainerAt(position.x(), position.y(), position.z());
        if (container == null || container.blockType() != Blocks.CHEST
                || WorldTickLoop.residentBlockType(accessor,
                position.x(), position.y(), position.z()) != container.blockType()) return null;
        ChestInventory chest = chestStorage.openAt(position.x(), position.y(), position.z());
        for (var slot : container.slots()) {
            short type = (short) slot.itemType();
            int durability = slot.durability() > 0 ? slot.durability()
                    : PlayerInventory.isDurable(type) ? PlayerInventory.initialDurability(type) : 0;
            if (chest.putInSlot(slot.slot(), type, slot.count(), durability, slot.enchantments(), 0, 0, null,
                    slot.componentData()) != slot.count()) {
                throw new IllegalStateException("dimension container slot rejected: " + slot);
            }
        }
        chestStorage.markDirty(position.x(), position.y(), position.z());
        return chest;
    }

    /**
     * [END-CITY] 콘텐츠가 선언한 양조기(엔드 배)를 한 번만 채운다. 상자와 같은 자격(생성 블록 그대로, 편집 보호
     * 좌표 아님, 기존 양조기 행 없음)이며 채운 뒤에는 일반 양조기 행으로 영속한다.
     */
    /** [END-CITY] 사용자 차원 콘텐츠가 이 청크에 처음 두는 개체(엔드 도시 셜커 · 겉날개 액자). */
    java.util.List<com.gameexpert.world.dimension.DimensionChunkProvider.InitialMob> dimensionInitialMobs(
            int chunkX, int chunkZ) {
        if (!(chunkProductSource instanceof com.gameexpert.world.dimension.DimensionChunkProductSource custom)) {
            return java.util.List.of();
        }
        return custom.initialMobs(chunkX, chunkZ);
    }

    void materializeDimensionBrewingStand(int x, int y, int z) {
        BlockPos position = new BlockPos(x, y, z);
        if (!(chunkProductSource instanceof com.gameexpert.world.dimension.DimensionChunkProductSource custom)
                || protectedDecorationEdits.contains(position) || mobMutationSites.contains(position)
                || brewingStorage.peekAt(x, y, z) != null) {
            return;
        }
        var container = custom.initialContainerAt(x, y, z);
        if (container == null || container.blockType() != Blocks.BREWING_STAND
                || WorldTickLoop.residentBlockType(accessor, x, y, z) != Blocks.BREWING_STAND) return;
        BrewingInventory stand = brewingStorage.openAt(x, y, z);
        for (var slot : container.slots()) {
            if (stand.put(slot.slot(), (short) slot.itemType(), slot.count(), 0, 0L, 0, 0) != slot.count()) {
                throw new IllegalStateException("dimension brewing slot rejected: " + slot);
            }
        }
        brewingStorage.markDirty(x, y, z);
    }

    /**
     * [DIMENSION-EXT][FOUNDATION — review] 채굴·폭발·지지 상실·외부 교체가 모두 거치는 단일 블록 변경
     * 경계({@code fluidSim} 의 {@code setBlock})에서, 아직 열리지 않은 콘텐츠 상자를 교체 **직전**(생성 블록이
     * 그대로이고 이번 편집이 아직 보호 좌표가 아닐 때) 먼저 채운다. 그 뒤의 {@code dropRemovedBlockContents}
     * 가 일반 상자 행을 그대로 쏟는다. 이전에는 교체가 먼저 AIR 를 써서 채움 자격 검사가 실패했고, 실제
     * 채굴·폭발에서 겉날개가 조용히 사라졌다. 오버월드는 첫 instanceof 에서 바로 빠진다.
     */
    private void materializeDimensionContainerBeforeReplacement(int x, int y, int z) {
        if (!(chunkProductSource instanceof com.gameexpert.world.dimension.DimensionChunkProductSource)) return;
        materializeDimensionContainer(new BlockPos(x, y, z));
        materializeDimensionBrewingStand(x, y, z);
    }

    private void invokeCanonicalLootOpenCompletion(CanonicalLootOpenCompletion completion) {
        CanonicalLootOpenRequest request = completion.request();
        if (request.session() != null && !canonicalLootOpenRequestCurrent(request)) {
            synchronized (this) {
                if (pendingCanonicalLootOpenRequests.get(request.session()) == request) {
                    pendingCanonicalLootOpenRequests.remove(request.session());
                }
            }
            return;
        }
        try {
            request.completion().accept(completion);
        } finally {
            if (request.session() != null) {
                synchronized (this) {
                    if (pendingCanonicalLootOpenRequests.get(request.session()) == request) {
                        pendingCanonicalLootOpenRequests.remove(request.session());
                    }
                }
            }
        }
    }

    boolean canonicalLootOpenRequestCurrent(CanonicalLootOpenRequest request) {
        if (request == null || request.session() == null) return request != null;
        synchronized (this) {
            return terminalPhase == TerminalPhase.RUNNING
                    && players.get(request.session().nickname()) == request.session()
                    && java.util.Objects.equals(request.connectionId(),
                            playerConnections.get(request.session().nickname()))
                    && pendingCanonicalLootOpenRequests.get(request.session()) == request;
        }
    }

    /** Cancel only this player's presentation intent; shared loot materialization still commits. */
    synchronized void cancelCanonicalChestOpen(PlayerTickState session, int x, int y, int z) {
        CanonicalLootOpenRequest request = pendingCanonicalLootOpenRequests.get(session);
        if (request != null && request.clicked().x() == x
                && request.clicked().y() == y && request.clicked().z() == z) {
            pendingCanonicalLootOpenRequests.remove(session);
        }
    }

    private void cancelCanonicalLootOpen(CanonicalLootOpenReservation reservation) {
        if (reservation == null) return;
        synchronized (this) {
            cancelCanonicalLootOpenLocked(reservation);
        }
    }

    private void cancelCanonicalLootOpenLocked(CanonicalLootOpenReservation reservation) {
        if (reservation == null || reservation.cancelled) return;
        reservation.cancelled = true;
        if (canonicalLootOpenReservations.get(reservation.key) == reservation) {
            removeCanonicalLootOpenReservationLocked(reservation);
        }
        for (CanonicalLootOpenRequest request : reservation.requests) {
            if (request.session() != null
                    && pendingCanonicalLootOpenRequests.get(request.session()) == request) {
                pendingCanonicalLootOpenRequests.remove(request.session());
            }
        }
        reservation.requests.clear();
    }

    private void cancelAllCanonicalLootOpenReservationsLocked() {
        for (CanonicalLootOpenReservation reservation
                : List.copyOf(canonicalLootOpenReservations.values())) {
            cancelCanonicalLootOpenLocked(reservation);
        }
        canonicalLootOpenRetries.clear();
        pendingCanonicalLootOpenRequests.clear();
        pendingCanonicalLootCoordinates.clear();
    }

    private void removeCanonicalLootOpenReservationLocked(
            CanonicalLootOpenReservation reservation) {
        canonicalLootOpenReservations.remove(reservation.key, reservation);
        reservation.submissionReserved = false;
        reservation.retryQueued = false;
        for (CanonicalLootOpenHalf half : reservation.key.orderedHalves()) {
            Integer count = pendingCanonicalLootCoordinates.get(half.position());
            if (count == null || count <= 1) pendingCanonicalLootCoordinates.remove(half.position());
            else pendingCanonicalLootCoordinates.put(half.position(), count - 1);
        }
    }

    boolean isCanonicalLootCoordinatePending(int x, int y, int z) {
        synchronized (this) {
            return pendingCanonicalLootCoordinates.containsKey(new BlockPos(x, y, z));
        }
    }

    boolean hasPendingCanonicalLootCoordinateInBox(int minX, int minZ, int maxX, int maxZ) {
        synchronized (this) {
            for (BlockPos position : pendingCanonicalLootCoordinates.keySet()) {
                if (position.x() >= minX && position.x() <= maxX
                        && position.z() >= minZ && position.z() <= maxZ) return true;
            }
            return false;
        }
    }

    /** 자연 전리품 자격은 해당 상자가 부서지면 소모되어 같은 좌표의 새 상자로 승계되지 않습니다. */
    void discardNaturalChestLoot(int x, int y, int z) {
        BlockPos position = new BlockPos(x, y, z);
        naturalLootChests.remove(position);
        canonicalLootCandidates.remove(position);
    }

    HopperSystem hopperSystem() {
        return hopperSystem;
    }

    DispenserSystem dispenserSystem() {
        return dispenserSystem;
    }

    /**
     * [BLOCK-ENTITY] {@code LevelChunk.setBlockState}: a different block (outside the same
     * block-entity family) removes the old block entity. Every destructive gameplay path spills
     * first; this records the cell so {@link #sweepReplacedBlockEntities} can drop whatever row
     * no path consumed (the QA fixture's {@code /setblock}-style overwrite). Deterministic replay
     * of already-applied overlays is not a new edit.
     */
    private void noteBlockEntityReplacement(int x, int y, int z, int blockType) {
        if (rehydratingReplayableChunks.contains(blockChunkKey(x, z))) return;
        int previous = WorldTickLoop.residentBlockType(accessor, x, y, z);
        if (previous < 0 || previous == blockType) return;
        int before = BlockEntityRules.family(previous);
        if (before == BlockEntityRules.NONE || before == BlockEntityRules.family(blockType)) return;
        replacedBlockEntityCells.putIfAbsent(new BlockPos(x, y, z), before);
    }

    /**
     * Drops the coordinate rows whose block entity was replaced without a spill. The spill paths
     * (mining, explosions, support loss, the campfire/brewing funnel) run synchronously with their
     * block write, so any row of the replaced family still present here belongs to no live block
     * entity. A row of the new block's own shape that the replaced family could not have owned is
     * the new block entity and stays.
     */
    void sweepReplacedBlockEntities() {
        if (replacedBlockEntityCells.isEmpty() || terminalPhase != TerminalPhase.RUNNING) return;
        List<Map.Entry<BlockPos, Integer>> cells =
                List.copyOf(replacedBlockEntityCells.entrySet());
        replacedBlockEntityCells.clear();
        for (Map.Entry<BlockPos, Integer> cell : cells) {
            BlockPos pos = cell.getKey();
            int replaced = cell.getValue();
            int x = pos.x(), y = pos.y(), z = pos.z();
            int current = WorldTickLoop.residentBlockType(accessor, x, y, z);
            if (current == WorldTickLoop.UNAVAILABLE_BLOCK
                    || BlockEntityRules.family(current) == replaced) continue;
            ChestInventory chest = chestStorage.peekAt(x, y, z);
            if (chest != null && (BlockEntityRules.chestStorageSlots(replaced) > 0
                    || chest.slots() != BlockEntityRules.chestStorageSlots(current))) {
                tickLoop.closeChestSubscribersAt(x, y, z);
                chestStorage.removeAt(x, y, z);
            }
            if (BlockEntityRules.isLootContainer(replaced)) discardNaturalChestLoot(x, y, z);
            FurnaceInventory furnace = furnaceStorage.peekAt(x, y, z);
            if (furnace != null && (FurnaceVariant.of(replaced) != null
                    || !FurnaceRules.isFurnace(current)
                    || furnace.variant() != FurnaceVariant.of(current))) {
                tickLoop.closeFurnaceSubscribersAt(x, y, z);
                furnaceStorage.removeAt(x, y, z);
            }
            if (brewingStorage.peekAt(x, y, z) != null
                    && (replaced == Blocks.BREWING_STAND || current != Blocks.BREWING_STAND)) {
                tickLoop.closeCraftingSubscribersAt(x, y, z);
                brewingStorage.removeAt(x, y, z);
            }
            if (campfireStorage.peekAt(x, y, z) != null
                    && (replaced == Blocks.CAMPFIRE || current != Blocks.CAMPFIRE)) {
                campfireStorage.removeAt(x, y, z);
                tickLoop.broadcastCampfireUpdate(x, y, z, null);
            }
            if (replaced == Blocks.LECTERN && lecternPersistence != null
                    && lecternPersistence.remove(worldId, x, y, z).isPresent()) {
                tickLoop.closeLecternSubscribersAt(x, y, z);
            }
            // [JUKEBOX] 흘리지 않고 치환된 주크박스의 블록 엔티티 행(정적판 replacedRowIsOrphan 쌍둥이).
            if (replaced == Blocks.JUKEBOX) tickLoop.discardJukeboxAt(x, y, z);
            // [CONTAINER-MENUS] The removed CrafterBlockEntity takes its disabled slots along.
            if (replaced == Blocks.CRAFTER) {
                setCrafterDisabledSlots(x, y, z, 0);
                crafterSystem.forget(pos);
            }
        }
    }

    /** Test view of the cells awaiting the block-entity sweep. */
    boolean blockEntityReplacementPending(int x, int y, int z) {
        return replacedBlockEntityCells.containsKey(new BlockPos(x, y, z));
    }

    java.util.List<ChestInventory.StoredStack> removeContainerAt(int x, int y, int z) {
        return chestStorage.removeAt(x, y, z);
    }

    void flushDirtyChests() {
        flushContainerCheckpoint();
    }

    void flushDirtyFurnaces() {
        flushContainerCheckpoint();
    }

    private void flushContainerCheckpoint() {
        if (terminalPhase != TerminalPhase.RUNNING && terminalPhase != TerminalPhase.DISPOSED) return;
        if (chestPersistence != null) {
            chestPersistence.flushDirty(worldId, chestStorage, shulkerStorage,
                    furnacePersistence == null ? null : () -> {
                        XpOrbSystem.FurnaceXpCarrySnapshot carry = xpOrbSystem.furnaceXpCarrySnapshot();
                        return furnacePersistence.captureDirty(worldId, furnaceStorage,
                                carry.amount(), carry.revision(), xpOrbSystem.furnaceXpCarryDirty(),
                                xpOrbSystem::acknowledgeFurnaceXpCarryPersistence);
                    });
        } else if (furnacePersistence != null) {
            XpOrbSystem.FurnaceXpCarrySnapshot carry = xpOrbSystem.furnaceXpCarrySnapshot();
            furnacePersistence.flushDirty(worldId, furnaceStorage,
                    carry.amount(), carry.revision(), xpOrbSystem.furnaceXpCarryDirty(),
                    xpOrbSystem::acknowledgeFurnaceXpCarryPersistence);
        }
    }

    /**
     * Attaches the complete durable scheduled-tick boundary before the owner starts. The scheduler restores
     * absolute due times from persistence; activation never recomputes them from a delivery retry.
     */
    public void installFinalCarrierTickAuthority(FinalCarrierTickScheduler.AtomicPersistence persistence,
            FinalCarrierTickScheduler.LiveTypes liveTypes,
            FinalCarrierTickScheduler.TickSemantics semantics,
            FinalCarrierTickScheduler.CommittedMutationSink mutationSink) {
        if (started || finalCarrierTickScheduler != null) {
            throw new IllegalStateException("final-carrier tick authority is already attached or started");
        }
        FinalCarrierTickScheduler scheduler = new FinalCarrierTickScheduler(worldId, persistence,
                id -> ProducerAuthorities.defaultState(chunkProductSource.generationProfile(), id));
        scheduler.restoreWorld();
        finalCarrierTickLiveTypes = java.util.Objects.requireNonNull(liveTypes, "live types");
        finalCarrierTickSemantics = java.util.Objects.requireNonNull(semantics, "tick semantics");
        finalCarrierTickMutationSink = java.util.Objects.requireNonNull(
                mutationSink, "tick mutation sink");
        scheduler.installSettlementExecutor(WorldRuntime::submitFinalCarrierSettlementStep);
        finalCarrierTickScheduler = scheduler;
    }

    /**
     * Hands one durable settlement step to the settlement workers.
     *
     * <p>Returns false when the queue is full, which leaves the row at the head of its lane for
     * the next owner turn. It never runs the step here: the whole point of the handoff is that
     * the world owner does not take the world row lock.</p>
     */
    private static boolean submitFinalCarrierSettlementStep(Runnable step) {
        try {
            FINAL_CARRIER_SETTLEMENT_WORKERS.execute(step);
            return true;
        } catch (java.util.concurrent.RejectedExecutionException full) {
            return false;
        }
    }

    /** Installs the production pinned semantic view while retaining the generic focused-test seam. */
    void installMc263FinalCarrierTickAuthority(
            FinalCarrierTickScheduler.AtomicPersistence persistence) {
        Mc263FinalCarrierTickSemantics.SemanticWorld semanticWorld =
                finalCarrierTickSemanticWorld();
        installFinalCarrierTickAuthority(persistence, semanticWorld,
                new Mc263FinalCarrierTickSemantics(), this::publishFinalCarrierTickMutation);
    }

    /** Attaches the atomic receipt+payload store for LOOT/SPAWNERS/OWNERS/ARCHAEOLOGY/BENT/ENTS. */
    public void installFinalCarrierLaneAuthority(FinalCarrierLaneInstaller installer) {
        if (started || finalCarrierLaneInstaller != null) {
            throw new IllegalStateException("final-carrier lane authority is already attached or started");
        }
        finalCarrierLaneInstaller = java.util.Objects.requireNonNull(installer, "installer");
    }

    /** Production Spring wiring for every durable final-carrier lane. */
    void installFinalCarrierAuthorities(
            com.gameexpert.engine.persistence.finalcarrier.FinalCarrierPersistenceService persistence) {
        java.util.Objects.requireNonNull(persistence, "final-carrier persistence");
        if (groundMutationSettlements == null) {
            throw new IllegalStateException(
                    "ground mutation settlement persistence must bind before final-carrier");
        }
        if (finalCarrierPersistence != null || finalCarrierEntityPublisher != null) {
            throw new IllegalStateException("final-carrier persistence is already attached");
        }
        finalCarrierPersistence = persistence;
        fallingSpeleothems.restore(persistence.loadFallingSpeleothems(worldId));
        finalCarrierEntityPublisher = new com.gameexpert.engine.persistence.finalcarrier
                .FinalCarrierPersistenceService.CommittedEntityActivationPublisher() {
            @Override public void publish(
                    com.gameexpert.engine.persistence.finalcarrier.FinalCarrierPersistenceService
                            .CommittedEntityActivation activation) {
                enqueuePersistenceCompletion(() -> {
                    if (!disposed && activation.worldId() == worldId) {
                        FinalCarrierInstallResult entities =
                                installCommittedStructureEntities(activation);
                        if (entities == FinalCarrierInstallResult.REJECTED) {
                            throw new IllegalStateException(
                                    "committed ENTS facts conflict with runtime");
                        }
                    }
                });
            }

            @Override public long currentLiveEntityIdFloor() {
                long floor = mobSystem.currentMobIdFloor();
                if (floor == Long.MAX_VALUE) {
                    throw new IllegalStateException("live mob identity space is exhausted");
                }
                return floor;
            }

            @Override public boolean claimLiveEntityIds(long minimum, long through) {
                return mobSystem.claimMobIdsFrom(minimum, through);
            }
        };
        persistence.registerWorldEntityPublisher(worldId, finalCarrierEntityPublisher);
        mobSystem.setStructureEntityDeathListener(this::persistStructureEntityDeath);
        Map<String, List<GeneratedDecoratedPotRuntime>> decoratedPotInstallationsBeforeRecovery =
                Map.copyOf(finalCarrierDecoratedPotInstallations);
        Map<BlockPos, GeneratedDecoratedPotRuntime> decoratedPotsByPositionBeforeRecovery =
                Map.copyOf(finalCarrierDecoratedPotsByPosition);
        try {
            for (var recovery : persistence.recoverCanonicalBees(worldId)) {
                if (recovery.worldId() != worldId) {
                    throw new IllegalStateException("canonical BEES recovery crossed worlds");
                }
                mobSystem.installFinalCarrierBeeNestOccupants(Mc263BeeSidecarPlan.prepare(
                        recovery.chunkX(), recovery.chunkZ(), recovery.nests()));
            }
            persistence.recoverWorld(worldId, finalCarrierGameplayInstaller());
            installFinalCarrierLaneAuthority(persistence);
            installMc263FinalCarrierTickAuthority(persistence);
        } catch (RuntimeException failure) {
            restoreFinalCarrierDecoratedPots(decoratedPotInstallationsBeforeRecovery,
                    decoratedPotsByPositionBeforeRecovery);
            persistence.unregisterWorldEntityPublisher(worldId, finalCarrierEntityPublisher);
            mobSystem.setStructureEntityDeathListener(null);
            finalCarrierEntityPublisher = null;
            finalCarrierPersistence = null;
            throw failure;
        }
    }

    private void restoreFinalCarrierDecoratedPots(
            Map<String, List<GeneratedDecoratedPotRuntime>> installationsBeforeRecovery,
            Map<BlockPos, GeneratedDecoratedPotRuntime> potsByPositionBeforeRecovery) {
        finalCarrierDecoratedPotInstallations.entrySet().removeIf(entry ->
                !java.util.Objects.equals(installationsBeforeRecovery.get(entry.getKey()),
                        entry.getValue()));
        installationsBeforeRecovery.forEach(finalCarrierDecoratedPotInstallations::put);
        finalCarrierDecoratedPotsByPosition.entrySet().removeIf(entry ->
                !java.util.Objects.equals(potsByPositionBeforeRecovery.get(entry.getKey()),
                        entry.getValue()));
        potsByPositionBeforeRecovery.forEach(finalCarrierDecoratedPotsByPosition::put);
    }

    /** Installs the typed archaeology seam used only by focused non-Spring runtime tests. */
    void installArchaeologyResultPersistenceForTest(
            ArchaeologyResultPersistenceForTest persistence) {
        if (started) {
            throw new IllegalStateException("test archaeology persistence must bind before start");
        }
        if (finalCarrierPersistence != null || archaeologyResultPersistenceForTest != null) {
            throw new IllegalStateException("archaeology persistence is already attached");
        }
        archaeologyResultPersistenceForTest = java.util.Objects.requireNonNull(
                persistence, "test archaeology persistence");
    }

    private void persistStructureEntityDeath(long mobId) {
        if (terminalPhase != TerminalPhase.RUNNING || finalCarrierPersistence == null
                || !pendingStructureEntityDeaths.add(mobId)) {
            return;
        }
        submitStructureEntityDeath(mobId);
    }

    private void submitStructureEntityDeath(long mobId) {
        PersistenceExecutor writer = ctx.persistenceExecutor();
        if (writer == null) {
            pendingStructureEntityDeaths.remove(mobId);
            throw new IllegalStateException("structure-entity death requires the persistence writer");
        }
        if (!writer.trySubmit(() -> {
            com.gameexpert.engine.persistence.finalcarrier.FinalCarrierPersistenceService
                    .StructureEntityDeathResult result = null;
            try {
                if (finalCarrierPersistence == null) return;
                result = finalCarrierPersistence.markStructureEntityDead(worldId, mobId);
            } finally {
                var completed = result;
                enqueuePersistenceCompletion(() -> {
                    boolean durable = completed == com.gameexpert.engine.persistence.finalcarrier
                            .FinalCarrierPersistenceService.StructureEntityDeathResult.COMMITTED;
                    boolean terminalUnsupported = completed == com.gameexpert.engine.persistence
                            .finalcarrier.FinalCarrierPersistenceService
                            .StructureEntityDeathResult.UNSUPPORTED_TERMINAL;
                    if (durable) durableDeadStructureEntityIds.add(mobId);
                    if (durable || terminalUnsupported || terminalPhase != TerminalPhase.RUNNING) {
                        pendingStructureEntityDeaths.remove(mobId);
                    } else {
                        submitStructureEntityDeath(mobId);
                    }
                }, () -> pendingStructureEntityDeaths.remove(mobId));
            }
        })) {
            enqueuePersistenceCompletion(
                    () -> {
                        if (terminalPhase == TerminalPhase.RUNNING) submitStructureEntityDeath(mobId);
                        else pendingStructureEntityDeaths.remove(mobId);
                    },
                    () -> pendingStructureEntityDeaths.remove(mobId));
        }
    }

    private String finalCarrierCanonicalType(FinalCarrierTickScheduler.Lane lane,
            int x, int y, int z) {
        int blockId = WorldTickLoop.residentBlockType(accessor, x, y, z);
        if (blockId == WorldTickLoop.UNAVAILABLE_BLOCK) {
            throw new FinalCarrierTickScheduler.UnavailableNeighborhood(
                    "final-carrier canonical read crossed resident chunks");
        }
        if (lane == FinalCarrierTickScheduler.Lane.FLUID) {
            return exactFinalCarrierBlockState(x, y, z).fluidTypeKey();
        }
        // A replaced valid block is a vanilla no-op, but an invalid exact-state code is carrier
        // corruption and must remain unsettled. Never disguise it as air and consume the row.
        return exactFinalCarrierBlockState(x, y, z).blockKey();
    }

    /** Exact resident projection used by the pinned semantic scheduler. */
    Mc263FinalCarrierTickSemantics.SemanticWorld finalCarrierTickSemanticWorld() {
        return new Mc263FinalCarrierTickSemantics.SemanticWorld() {
            @Override
            public String canonicalTypeAt(FinalCarrierTickScheduler.Lane lane,
                    int x, int y, int z) {
                return finalCarrierCanonicalType(lane, x, y, z);
            }

            @Override
            public String exactBlockStateAt(int x, int y, int z) {
                return exactFinalCarrierBlockState(x, y, z).exactState();
            }

            @Override
            public FinalCarrierTickScheduler.BlockMutation encodeExactState(
                    int x, int y, int z, String exactState) {
                RuntimeCellState.Cell before = exactFinalCarrierBlockState(x, y, z);
                RuntimeCellState.Cell after = before.isRuntimeOverlay()
                        && exactState.startsWith(before.blockKey())
                        ? RuntimeCellState.playerExact(before.blockId(), exactState)
                        : RuntimeCellState.generated(ProducerAuthorities.exactState(
                                chunkProductSource.generationProfile(), exactState));
                return new FinalCarrierTickScheduler.BlockMutation(x, y, z,
                        before.blockId(), before.persistedState(),
                        after.blockId(), after.persistedState());
            }

            @Override
            public FinalCarrierTickScheduler.TickMutation planFluidTick(
                    FinalCarrierTickScheduler.ScheduledTick tick) {
                strictFinalCarrierFluidReads.set(Boolean.TRUE);
                try {
                    List<FinalCarrierTickScheduler.BlockMutation> mutations = new ArrayList<>();
                    for (FluidSimulator.PlannedBlockChange change
                            : fluidSim.planFluidTick(tick.x(), tick.y(), tick.z())) {
                        RuntimeCellState.Cell before = exactFinalCarrierBlockState(
                                change.x(), change.y(), change.z());
                        mutations.add(new FinalCarrierTickScheduler.BlockMutation(
                                change.x(), change.y(), change.z(),
                                before.blockId(), before.persistedState(),
                                change.blockId(), 0));
                    }
                    return mutations.isEmpty() ? FinalCarrierTickScheduler.TickMutation.NONE
                            : new FinalCarrierTickScheduler.TickMutation(mutations);
                } finally {
                    strictFinalCarrierFluidReads.remove();
                }
            }
        };
    }

    private RuntimeCellState.Cell exactFinalCarrierBlockState(int x, int y, int z) {
        int blockId = WorldTickLoop.residentBlockType(accessor, x, y, z);
        if (blockId == WorldTickLoop.UNAVAILABLE_BLOCK) {
            throw new FinalCarrierTickScheduler.UnavailableNeighborhood(
                    "final-carrier semantic read crossed resident chunks");
        }
        if (RuntimeCellState.supportsPlayerOverlay(blockId)) {
            Map<BlockPos, OverlayValue> chunk = overlayChunk(blockChunkKey(x, z), false);
            OverlayValue explicit = chunk == null ? null : chunk.get(new BlockPos(x, y, z));
            if (explicit != null && Short.toUnsignedInt(explicit.blockType) == blockId) {
                return RuntimeCellState.playerOverlay(blockId, explicit.state & 0xff);
            }
        }
        if (com.gameexpert.engine.persistence.tick.SpeleothemFallIntents.isSpeleothem(blockId)) {
            Map<BlockPos, OverlayValue> chunk = overlayChunk(blockChunkKey(x, z), false);
            OverlayValue explicit = chunk == null ? null : chunk.get(new BlockPos(x, y, z));
            if (explicit != null && Short.toUnsignedInt(explicit.blockType) == blockId) {
                return RuntimeCellState.generated(ProducerAuthorities.exactState(chunkProductSource.generationProfile(),
                        com.gameexpert.falling.dto.RuntimeSpeleothemFall.exactFromCompact(blockId, explicit.state & 0xff)));
            }
        }
        TerrainAccessor.SnapshotSource source = accessor.snapshotSource(
                Math.floorDiv(x, Blocks.CHUNK_X), Math.floorDiv(z, Blocks.CHUNK_Z));
        if (source == null) {
            throw new FinalCarrierTickScheduler.UnavailableNeighborhood(
                    "final-carrier semantic read crossed resident chunks");
        }
        int stateCode = source.blockStateAt(Blocks.blockIndex(
                Math.floorMod(x, Blocks.CHUNK_X), y, Math.floorMod(z, Blocks.CHUNK_Z)));
        try {
            return RuntimeCellState.generated(ProducerAuthorities.decodeState(
                    chunkProductSource.generationProfile(), blockId, stateCode));
        } catch (IllegalArgumentException invalidState) {
            throw new IllegalStateException("resident block/state is outside the exact 26.3 codebook",
                    invalidState);
        }
    }

    private synchronized void publishFinalCarrierTickMutation(
            FinalCarrierTickScheduler.ScheduledTick tick,
            FinalCarrierTickScheduler.TickMutation mutation) {
        if (terminalPhase != TerminalPhase.RUNNING) {
            lastFinalCarrierTickPublicationOutcome = FinalCarrierTickPublicationOutcome.TERMINAL;
            throw new FinalCarrierTickPublicationException(
                    FinalCarrierTickPublicationOutcome.TERMINAL, 0,
                    "final-carrier mutation crossed the terminal boundary");
        }
        java.util.Objects.requireNonNull(tick, "scheduled tick");
        java.util.Objects.requireNonNull(mutation, "tick mutation");
        FinalCarrierTickPublicationProgress progress =
                pendingFinalCarrierTickPublications.get(tick.key());
        if (progress == null) {
            progress = new FinalCarrierTickPublicationProgress(mutation);
            pendingFinalCarrierTickPublications.put(tick.key(), progress);
        } else if (!progress.mutation.equals(mutation)) {
            lastFinalCarrierTickPublicationOutcome = FinalCarrierTickPublicationOutcome.PARTIAL;
            throw new FinalCarrierTickPublicationException(
                    FinalCarrierTickPublicationOutcome.PARTIAL, progress.nextBlockIndex,
                    "final-carrier retry changed its exact mutation");
        }
        try {
            if (tick.lane() == FinalCarrierTickScheduler.Lane.FLUID
                    && WorldTickLoop.residentBlockType(accessor, tick.x(), tick.y(), tick.z())
                            == WorldTickLoop.UNAVAILABLE_BLOCK) {
                throw new FinalCarrierTickPublicationException(
                        FinalCarrierTickPublicationOutcome.PARTIAL, progress.nextBlockIndex,
                        "final-carrier fluid continuation awaits its resident origin");
            }
            while (progress.nextBlockIndex < mutation.blocks().size()) {
                requireFinalCarrierTickPublicationOpen(progress.nextBlockIndex);
                FinalCarrierTickScheduler.BlockMutation block =
                        mutation.blocks().get(progress.nextBlockIndex);
                if (!progress.replacementDropApplied) {
                    publishFinalCarrierFluidReplacementDrop(tick, block);
                    progress.replacementDropApplied = true;
                }
                requireFinalCarrierTickPublicationOpen(progress.nextBlockIndex);
                setIndexedOverlay(block.x(), block.y(), block.z(), block.blockId(), block.blockState());
                requireFinalCarrierTickPublicationOpen(progress.nextBlockIndex);
                tickBlockChanges.put(new BlockPos(block.x(), block.y(), block.z()),
                        (short) block.blockId());
                requireFinalCarrierTickPublicationOpen(progress.nextBlockIndex);
                fluidSim.onBlockChanged(block.x(), block.y(), block.z());
                progress.nextBlockIndex++;
                progress.replacementDropApplied = false;
            }
            if (tick.lane() == FinalCarrierTickScheduler.Lane.FLUID) {
                requireFinalCarrierTickPublicationOpen(progress.nextBlockIndex);
                // A filled falling column can need another tick without producing a block write.
                // Restore that continuation only after its exact outcome is durable and published.
                fluidSim.onBlockChanged(tick.x(), tick.y(), tick.z());
            }
            fallingSpeleothems.restore(com.gameexpert.engine.persistence.tick.SpeleothemFallIntents.from(tick, mutation));
            pendingFinalCarrierTickPublications.remove(tick.key(), progress);
            lastFinalCarrierTickPublicationOutcome = FinalCarrierTickPublicationOutcome.COMMITTED;
        } catch (FinalCarrierTickPublicationException failure) {
            lastFinalCarrierTickPublicationOutcome = failure.outcome();
            throw failure;
        } catch (Throwable failure) {
            lastFinalCarrierTickPublicationOutcome = FinalCarrierTickPublicationOutcome.PARTIAL;
            throw new FinalCarrierTickPublicationException(
                    FinalCarrierTickPublicationOutcome.PARTIAL, progress.nextBlockIndex,
                    "final-carrier mutation was only partially applied", failure);
        }
    }

    private void requireFinalCarrierTickPublicationOpen(int appliedBlocks) {
        if (terminalPhase != TerminalPhase.RUNNING) {
            throw new FinalCarrierTickPublicationException(
                    FinalCarrierTickPublicationOutcome.TERMINAL, appliedBlocks,
                    "final-carrier mutation crossed the terminal boundary");
        }
    }

    private void publishFinalCarrierFluidReplacementDrop(
            FinalCarrierTickScheduler.ScheduledTick tick,
            FinalCarrierTickScheduler.BlockMutation mutation) {
        if (tick.lane() != FinalCarrierTickScheduler.Lane.FLUID) return;
        int replaced = WorldTickLoop.residentBlockType(
                accessor, mutation.x(), mutation.y(), mutation.z());
        if (replaced == WorldTickLoop.UNAVAILABLE_BLOCK || replaced == Blocks.AIR
                || Fluids.isFluid(replaced)) return;
        boolean dropResources = tick.typeKey().equals("minecraft:water")
                || tick.typeKey().equals("minecraft:flowing_water");
        if (!dropResources) return;
        var crop = com.gameexpert.engine.crop.CropRules.forCrop(replaced);
        if (crop != null) {
            int state = blockStates.get(mutation.x(), mutation.y(), mutation.z(), replaced);
            itemSystem.spawnCropDrops(crop, state, mutation.x() + 0.5,
                    mutation.y() + 0.5, mutation.z() + 0.5);
        } else if (Fluids.isReplaceable(replaced)) {
            itemSystem.spawnBlockDrop((short) replaced, mutation.x() + 0.5,
                    mutation.y() + 0.5, mutation.z() + 0.5);
        }
    }

    void installBrewingPersistence(BrewingPersistenceService persistence) {
        if (brewingPersistence != null) {
            throw new IllegalStateException("brewing persistence is already attached");
        }
        brewingPersistence = persistence;
        if (persistence != null) {
            persistence.loadWorld(worldId, brewingStorage);
            // [BLOCK-SHAPES] 이미 상주한 칸의 옛 양조대 병 비트를 맞춘다(나머지는 청크 활성화가 맞춘다).
            if (terminalPhase == TerminalPhase.RUNNING) tickLoop.syncStoredBrewingBottles(null);
        }
    }

    BrewingPersistenceService brewingPersistence() {
        return brewingPersistence;
    }

    void flushDirtyBrewing() {
        if (terminalPhase != TerminalPhase.RUNNING && terminalPhase != TerminalPhase.DISPOSED) return;
        if (brewingPersistence != null) brewingPersistence.flushDirty(worldId, brewingStorage);
    }

    void flushDirtyCampfires() {
        if (terminalPhase != TerminalPhase.RUNNING && terminalPhase != TerminalPhase.DISPOSED) return;
        if (campfirePersistence != null) campfirePersistence.flushDirty(worldId, campfireStorage);
        flushDirtyEnchanting();
        flushDirtyCrafters();
    }

    /** Queues one paused cooking baseline before its player/container settlement. */
    boolean flushDirtyCampfire(int x, int y, int z) {
        if (terminalPhase != TerminalPhase.RUNNING) return false;
        return campfirePersistence == null
                || campfirePersistence.flushDirtyAt(worldId, campfireStorage, x, y, z);
    }

    /** Shares the deterministic periodic and terminal container checkpoint boundary. */
    void flushDirtyEnchanting() {
        if (terminalPhase != TerminalPhase.RUNNING && terminalPhase != TerminalPhase.DISPOSED) return;
        if (enchantingPersistence != null) {
            enchantingPersistence.flushDirty(worldId, enchantingStorage);
        }
    }

    /** [SURV-X] 경험치 구슬 시스템. */
    XpOrbSystem xpOrbSystem() {
        return xpOrbSystem;
    }

    /**
     * Transfers the complete fixed-point XP payload of a removed furnace into the world XP sink.
     * Keeping the drain result typed prevents a caller from publishing only its integer prefix.
     */
    XpOrbSystem.FurnaceXpPublication publishRemovedFurnaceXp(
            FurnaceInventory.DrainResult drained, double x, double y, double z) {
        if (drained == null) {
            throw new IllegalArgumentException("removed furnace drain result is required");
        }
        return xpOrbSystem.publishFurnaceXp(
                new XpOrbSystem.FurnaceXpMilli(drained.xpMilli(), x, y, z));
    }

    enum ContentQaFixtureOutcome { APPLIED, IDEMPOTENT, REJECTED }
    private record PlannedQaMob(long id, ContentQaFixturePlan.MobRequest request,
            MobPersistenceSnapshot snapshot) { }
    private record PendingContentQaInstall(ContentQaFixturePlan plan, PlayerTickState player,
            PlayerInventory.CompletePersistenceSnapshot sourceInventory,
            PlayerInventory.CompletePersistenceSnapshot committedInventory,
            List<PlannedQaMob> mobs,
            List<com.gameexpert.ground.dto.GroundXpOrbSnapshot> xp,
            long committedGroundRevision) { }
    private PendingContentQaInstall pendingContentQaInstall;
    private Runnable contentQaAfterCommitHook = () -> { };

    /** Applies content-v1 only after its complete durable aggregate commits atomically. */
    ContentQaFixtureOutcome applyContentQaFixture(
            ContentQaFixturePlan plan, PlayerTickState player) {
        if (terminalPhase != TerminalPhase.RUNNING
                || plan == null || player == null
                || !ctx.properties().qaSeeding()
                || seed != (int) com.gameexpert.config.EngineProperties.CONTENT_QA_WORLD_SEED
                || !ContentQaFixturePlan.FIXTURE_ID.equals(plan.id())
                || contentQaFixtureSettlements == null
                || ctx.persistenceExecutor() == null) {
            return ContentQaFixtureOutcome.REJECTED;
        }
        try {
            // This caller writes a complete mob table. First settle only already-admitted
            // population plans/markers, including a previously rejected submission, through
            // their aggregate transaction and owner installation before constructing that table.
            settleWelcomePopulation(Set.copyOf(mobSystem.populationPersistenceSnapshot().chunkKeys()));
        } catch (RuntimeException failure) {
            log.warn("content QA fixture waits for population settlement", failure);
            return ContentQaFixtureOutcome.REJECTED;
        }
        if (runContentQaPersistence(() -> contentQaFixtureSettlements.alreadySettled(worldId, plan))) {
            if (pendingContentQaInstall != null && pendingContentQaInstall.plan().checksum() == plan.checksum()) {
                installCommittedContentQa(pendingContentQaInstall);
                return ContentQaFixtureOutcome.IDEMPOTENT;
            }
            // A replacement runtime has already hydrated the durable receipt and all aggregates.
            // If neither fact is true, refuse acknowledgement so the session is forced to reload.
            return contentQaReceiptApplied(plan)
                    ? ContentQaFixtureOutcome.IDEMPOTENT : ContentQaFixtureOutcome.REJECTED;
        }

        awaitPendingGroundSpawnsBeforeBlockingAction();

        PlayerInventory.CompletePersistenceSnapshot source =
                player.inventory().acquireSettlementLease();
        if (source == null) return ContentQaFixtureOutcome.REJECTED;
        PlayerInventory plannedInventory = source.detachedInventory();
        for (ContentQaFixturePlan.Supply supply : plan.supplies()) {
            if (plannedInventory.addItem(supply.itemType(), supply.count(),
                    PlayerInventory.initialDurability(supply.itemType()), 0, 0, 0, null,
                    supply.itemComponentData()) != supply.count()) {
                player.inventory().releaseSettlementLease(source);
                return ContentQaFixtureOutcome.REJECTED;
            }
        }
        if (plan.equipElytra()) {
            int elytraSlot = -1;
            for (int slot = 0; slot < PlayerInventory.SLOTS; slot++) {
                if (plannedInventory.itemType(slot) == PlayerInventory.ELYTRA) {
                    elytraSlot = slot;
                    break;
                }
            }
            if (elytraSlot < 0 || !plannedInventory.equip(elytraSlot)) {
                player.inventory().releaseSettlementLease(source);
                return ContentQaFixtureOutcome.REJECTED;
            }
        }
        PlayerInventory.CompletePersistenceSnapshot committedInventory =
                plannedInventory.completePersistenceSnapshot();

        List<PlannedQaMob> plannedMobs = new ArrayList<>(plan.mobRequests().size());
        for (ContentQaFixturePlan.MobRequest request : plan.mobRequests()) {
            long id = mobSystem.reserveMobId();
            String variant = request.type().deterministicVariant(seed, id, request.x(), request.z());
            boolean hiveOccupant = request.type() == MobType.BEE
                    && request.id().equals(ContentQaFixturePlan.HIVE_OCCUPANT_ID);
            MobPersistenceSnapshot snapshot = new MobPersistenceSnapshot(id,
                    request.type().name(), variant, request.x(), request.y(), request.z(),
                    request.type().maxHp(), false,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    null, null, 0, 0, false, 150, 0,
                    false, false, false, 0, false,
                    hiveOccupant ? request.x() : 0,
                    hiveOccupant ? request.y() : 0,
                    hiveOccupant ? request.z() : 0,
                    true, 0, 0, 0, 0, null, 0, 0, 0, 0)
                    .withBeeHiveTicks(hiveOccupant ? 1 : 0);
            plannedMobs.add(new PlannedQaMob(id, request, snapshot));
        }
        List<MobPersistenceSnapshot> allMobs = new ArrayList<>(
                mobSystem.populationPersistenceSnapshot().mobs());
        plannedMobs.forEach(mob -> allMobs.add(mob.snapshot()));

        ContentQaFixturePlan.Bounds bounds = plan.bounds();
        List<com.gameexpert.ground.dto.GroundXpOrbSnapshot> reservedXp =
                xpOrbSystem.reserveSettlementOrbs(plan.xpAmount(),
                        bounds.anchorX() + WIDTH_CENTER, bounds.floorY() + 2.0,
                        bounds.anchorZ() + WIDTH_CENTER);
        List<com.gameexpert.ground.dto.GroundXpOrbSnapshot> allXp =
                new ArrayList<>(xpOrbSystem.persistenceSnapshot());
        allXp.addAll(reservedXp);
        validateGroundEntitySnapshotIdentities(itemSystem.persistenceSnapshot(), allXp);
        long expectedGroundRevision = groundRevision;
        long[] traderWindow = mobSystem.traderWindowState();
        var command = new ContentQaFixtureSettlementService.Command(worldId, plan,
                source.revision(), playerInventoryMutationSnapshot(player, committedInventory),
                allMobs, expectedGroundRevision, expectedGroundRevision + 1,
                itemSystem.persistenceSnapshot(), allXp,
                new WorldTimePersistenceService.ClockState(clock.dayCount(), plan.worldTime(),
                        clock.gameTimeMcTicks(), new WorldTimePersistenceService.TraderWindow(
                                traderWindow[0], (int) traderWindow[1])));

        ContentQaFixtureSettlementService.Outcome outcome;
        try {
            outcome = runContentQaPersistence(() -> contentQaFixtureSettlements.settle(command));
        } catch (RuntimeException failure) {
            player.inventory().releaseSettlementLease(source);
            log.warn("content QA fixture settlement rejected", failure);
            return ContentQaFixtureOutcome.REJECTED;
        }
        if (outcome != ContentQaFixtureSettlementService.Outcome.COMMITTED) {
            player.inventory().releaseSettlementLease(source);
            return outcome == ContentQaFixtureSettlementService.Outcome.IDEMPOTENT
                    ? ContentQaFixtureOutcome.IDEMPOTENT : ContentQaFixtureOutcome.REJECTED;
        }
        pendingContentQaInstall = new PendingContentQaInstall(plan, player, source,
                committedInventory, List.copyOf(plannedMobs), List.copyOf(reservedXp),
                expectedGroundRevision + 1);
        contentQaAfterCommitHook.run();
        installCommittedContentQa(pendingContentQaInstall);
        return ContentQaFixtureOutcome.APPLIED;
    }

    private void installCommittedContentQa(PendingContentQaInstall install) {
        if (terminalPhase != TerminalPhase.RUNNING) {
            install.player().inventory().releaseSettlementLease(install.sourceInventory());
            pendingContentQaInstall = null;
            return;
        }
        PlayerInventory inventory = install.player().inventory();
        if (!inventory.installCommittedSettlement(install.sourceInventory(),
                install.committedInventory())
                && inventory.revision() != install.committedInventory().revision()) {
            throw new IllegalStateException("committed QA inventory could not be installed");
        }
        for (ContentQaFixturePlan.Cell cell : install.plan().cells()) {
            if (terminalPhase != TerminalPhase.RUNNING) {
                inventory.releaseSettlementLease(install.sourceInventory());
                pendingContentQaInstall = null;
                return;
            }
            int x = cell.point().x();
            int y = cell.point().y();
            int z = cell.point().z();
            setIndexedOverlay(x, y, z, cell.blockId(), cell.state());
            if (terminalPhase != TerminalPhase.RUNNING) {
                inventory.releaseSettlementLease(install.sourceInventory());
                pendingContentQaInstall = null;
                return;
            }
            tickBlockChanges.put(new BlockPos(x, y, z), (short) cell.blockId());
        }
        ContentQaFixturePlan.Bounds bounds = install.plan().bounds();
        int minChunkX = Math.floorDiv(bounds.anchorX(), Blocks.CHUNK_X);
        int maxChunkX = Math.floorDiv(bounds.anchorX() + bounds.width() - 1, Blocks.CHUNK_X);
        int minChunkZ = Math.floorDiv(bounds.anchorZ(), Blocks.CHUNK_Z);
        int maxChunkZ = Math.floorDiv(bounds.anchorZ() + bounds.depth() - 1, Blocks.CHUNK_Z);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (terminalPhase != TerminalPhase.RUNNING) {
                    inventory.releaseSettlementLease(install.sourceInventory());
                    pendingContentQaInstall = null;
                    return;
                }
                invalidateLightChunkCaches(chunkX, chunkZ);
            }
        }
        for (PlannedQaMob mob : install.mobs()) {
            if (terminalPhase != TerminalPhase.RUNNING) {
                inventory.releaseSettlementLease(install.sourceInventory());
                pendingContentQaInstall = null;
                return;
            }
            mobSystem.qaInstallPersistedMob(mob.snapshot());
        }
        if (terminalPhase != TerminalPhase.RUNNING) {
            inventory.releaseSettlementLease(install.sourceInventory());
            pendingContentQaInstall = null;
            return;
        }
        mobSystem.qaPublishCommittedFixtureSnapshot();
        if (terminalPhase != TerminalPhase.RUNNING) {
            inventory.releaseSettlementLease(install.sourceInventory());
            pendingContentQaInstall = null;
            return;
        }
        xpOrbSystem.commitSettlementOrbs(install.xp());
        groundRevision = install.committedGroundRevision();
        clock.setWorldTime(install.plan().worldTime());
        pendingContentQaInstall = null;
    }

    void setContentQaAfterCommitHookForTest(Runnable hook) {
        contentQaAfterCommitHook = hook == null ? () -> { } : hook;
    }

    private <T> T runContentQaPersistence(java.util.concurrent.Callable<T> operation) {
        java.util.concurrent.atomic.AtomicReference<T> result = new java.util.concurrent.atomic.AtomicReference<>();
        try {
            ctx.persistenceExecutor().submitFuture(() -> {
                try {
                    result.set(operation.call());
                } catch (RuntimeException | Error failure) {
                    throw failure;
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            }).get();
            return result.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("content QA persistence interrupted", interrupted);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("content QA persistence failed", cause);
        }
    }

    private static final double WIDTH_CENTER = ContentQaFixturePlan.WIDTH / 2.0;

    boolean contentQaReceiptApplied(ContentQaFixturePlan plan) {
        ContentQaFixturePlan.Cell receipt = plan.receiptCell();
        BlockPos pos = new BlockPos(receipt.point().x(), receipt.point().y(),
                receipt.point().z());
        Map<BlockPos, OverlayValue> chunk = authoritativeOverlay.get(
                blockChunkKey(pos.x(), pos.z()));
        OverlayValue live = chunk == null ? null : chunk.get(pos);
        if (live != null && Short.toUnsignedInt(live.blockType) == receipt.blockId()) return true;
        Integer durable = durableBlockDiffCells.get(pos);
        return durable != null && durable == packedBlockCell((short) receipt.blockId(), (short) 0);
    }

    /** Loads the last complete item+XP checkpoint exactly once during runtime construction. */
    void attachGroundEntityPersistence(GroundEntityPersistenceService persistence) {
        if (persistence == null) return;
        if (groundEntityPersistence != null) {
            throw new IllegalStateException("ground entity persistence is already attached");
        }
        GroundEntityPersistenceService.GroundEntitySnapshot restored =
                persistence.loadWorld(worldId);
        validateGroundEntitySnapshotIdentities(restored.items(), restored.xpOrbs());
        List<ItemEntity> restoredItems = itemSystem.prepareRestore(restored.items());
        List<XpOrb> restoredXpOrbs = xpOrbSystem.prepareRestore(restored.xpOrbs());
        groundEntityIds.restoreIdentities(
                restoredItems.stream().map(item -> item.id).toList(),
                restoredXpOrbs.stream().map(orb -> orb.id).toList());
        itemSystem.installPreparedRestore(restoredItems);
        xpOrbSystem.installPreparedRestore(restoredXpOrbs);
        groundRevision = restored.groundRevision();
        groundEntityPersistence = persistence;
    }

    void installContentQaFixtureSettlements(ContentQaFixtureSettlementService settlements) {
        this.contentQaFixtureSettlements = settlements;
    }

    void attachGroundMutationSettlements(GroundMutationSettlementService settlements) {
        // Standalone/non-Spring fixtures may omit this optional service; production final-carrier
        // binding and archaeology consumption fail closed unless it is present.
        if (settlements == null) return;
        if (groundMutationSettlements != null) {
            throw new IllegalStateException("ground mutation settlements are already attached");
        }
        long receiptHighWater = settlements.highestReservedEntityId(worldId);
        requireGroundHighWater(receiptHighWater);
        itemSystem.reserveEntityIdThrough(receiptHighWater);
        xpOrbSystem.reserveEntityIdThrough(receiptHighWater);
        groundMutationSettlements = settlements;
    }

    void attachPlayerContainerSettlements(PlayerContainerSettlementPersistenceService settlements) {
        if (playerContainerSettlements != null) {
            throw new IllegalStateException("player container settlements are already attached");
        }
        playerContainerSettlements = settlements;
    }

    boolean enqueuePlayerContainerAction(Runnable action) {
        if (action == null) return false;
        synchronized (this) {
            if (terminalPhase != TerminalPhase.RUNNING
                    || playerContainerActions.size() >= MAX_RUNTIME_ADMISSIONS) return false;
            playerContainerActions.addLast(action);
            return true;
        }
    }

    void pumpPlayerContainerActions() {
        Runnable action;
        synchronized (this) {
            if (!ownerTurnMayContinue() || playerContainerSettlementInFlight) return;
            action = playerContainerActions.pollFirst();
        }
        if (action != null) action.run();
    }

    boolean playerContainerSettlementInFlight() {
        return playerContainerSettlementInFlight;
    }

    boolean submitPlayerContainerSettlement(PlayerContainerSettlementCommand command,
            Runnable committed, Runnable rejected) {
        boolean reject;
        synchronized (this) {
            reject = terminalPhase != TerminalPhase.RUNNING || playerContainerSettlements == null;
            if (reject) {
                // Call the rejection outside the runtime monitor; container callbacks may acquire
                // the inventory monitor before returning to the runtime.
            } else if (playerContainerSettlementInFlight) {
                throw new IllegalStateException(
                        "container FIFO submitted more than one in-flight command");
            } else {
                // Reserve the exact FIFO slot before crossing to the asynchronous writer. A terminal
                // transition can therefore wait for or generically settle this command rather than
                // observing a submission gap and disposing its player state underneath it.
                playerContainerSettlementCells = settlementCells(command);
                playerContainerSettlementInFlight = true;
            }
        }
        if (reject) {
            if (rejected != null) rejected.run();
            return false;
        }
        Runnable transaction = () -> {
            PlayerContainerSettlementPersistenceService.Outcome outcome;
            try {
                outcome = playerContainerSettlements.settle(command);
            } catch (RuntimeException | Error failure) {
                log.warn("월드 {} 컨테이너 정산 {} 실행 실패",
                        worldId, command.container().getSettlementId(), failure);
                outcome = PlayerContainerSettlementPersistenceService.Outcome.STALE;
            }
            boolean accepted = outcome == PlayerContainerSettlementPersistenceService.Outcome.COMMITTED
                    || outcome == PlayerContainerSettlementPersistenceService.Outcome.IDEMPOTENT;
            enqueuePersistenceCompletion(() -> {
                playerContainerSettlementInFlight = false;
                if (accepted) committed.run();
                else rejected.run();
            }, () -> playerContainerSettlementInFlight = false);
        };
        PersistenceExecutor writer = ctx.persistenceExecutor();
        if (writer == null) {
            transaction.run();
            drainPersistenceCompletions();
            return true;
        }
        boolean accepted;
        try {
            accepted = writer.trySubmit(transaction);
        } catch (RuntimeException | Error failure) {
            playerContainerSettlementInFlight = false;
            if (rejected != null) rejected.run();
            throw failure;
        }
        if (!accepted) {
            playerContainerSettlementInFlight = false;
            if (rejected != null) rejected.run();
            return false;
        }
        return true;
    }

    private static Set<BlockPos> settlementCells(PlayerContainerSettlementCommand command) {
        // A container-less command (inventory-only settlement) locks no block cell.
        var container = command.container();
        var target = container == null ? null : container.getTarget();
        if (target == null) return Set.of();
        if (target instanceof InventoryMutationTarget.Chests chests) {
            Set<BlockPos> cells = new java.util.HashSet<>();
            for (InventoryMutationTarget.ChestHalf half : chests.halves()) {
                cells.add(new BlockPos(half.position().x(), half.position().y(),
                        half.position().z()));
            }
            return Set.copyOf(cells);
        }
        InventoryMutationTarget.Position position = switch (target) {
            case InventoryMutationTarget.Furnace furnace -> furnace.position();
            case InventoryMutationTarget.Brewing brewing -> brewing.position();
            case InventoryMutationTarget.Campfire campfire -> campfire.position();
            default -> null;
        };
        return position == null ? Set.of()
                : Set.of(new BlockPos(position.x(), position.y(), position.z()));
    }

    /**
     * [HOPPER] True while the one in-flight player container settlement targets this cell. Its
     * completion replaces the live inventory and checks the revision it read; an automatic write
     * in between would make that durable commit unreachable, so hoppers treat the cell as closed.
     */
    boolean containerSettlementTargets(int x, int y, int z) {
        return playerContainerSettlementInFlight
                && playerContainerSettlementCells.contains(new BlockPos(x, y, z));
    }

    static long stablePlayerContainerSettlementId(long playerId, long sourceRevision) {
        long value = playerId * 0x9E3779B97F4A7C15L + sourceRevision;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        value &= Long.MAX_VALUE;
        return value == 0 || value == Long.MAX_VALUE ? 1 : value;
    }

    void attachEmptyMapSettlements(com.gameexpert.map.service.EmptyMapSettlementService service) {
        if (emptyMapSettlements != null) throw new IllegalStateException("map settlements already attached");
        emptyMapSettlements = service;
    }

    com.gameexpert.map.service.EmptyMapSettlementService emptyMapSettlements() {
        return emptyMapSettlements;
    }

    WorldMapData prepareNewMap(double x, double z) {
        return terminalPhase == TerminalPhase.RUNNING ? worldMaps.prepare(x, z) : null;
    }

    void acceptSettledMap(WorldMapData map) {
        if (terminalPhase == TerminalPhase.RUNNING) worldMaps.acceptPersisted(map);
    }

    GroundMutationSettlementService groundMutationSettlements() {
        return groundMutationSettlements;
    }

    GroundEntityIdAllocator groundEntityIdAllocator() {
        return groundEntityIds;
    }

    void requireGroundItemIdentityAvailable(long entityId) {
        if (xpOrbSystem.containsIdentity(entityId)) {
            throw new IllegalStateException("ground entity identity collision");
        }
        groundEntityIds.claimSettlementIdentity(entityId, GroundEntityKind.ITEM);
    }

    void requireGroundXpIdentityAvailable(long entityId) {
        if (itemSystem.containsIdentity(entityId)) {
            throw new IllegalStateException("ground entity identity collision");
        }
        groundEntityIds.claimSettlementIdentity(entityId, GroundEntityKind.XP);
    }

    synchronized long groundRevision() {
        return groundRevision;
    }

    static long stableGroundMutationId(long entityId, int namespace) {
        if (namespace <= 0 || namespace >= GroundEntityIdAllocator.MUTATION_NAMESPACE_LIMIT) {
            throw new IllegalArgumentException("ground mutation identity overflow");
        }
        requireAllocatableGroundEntityId(entityId, "ground mutation identity overflow");
        return (entityId - 1) * GroundEntityIdAllocator.MUTATION_NAMESPACE_LIMIT + namespace;
    }

    private static void requireAllocatableGroundEntityId(long entityId, String message) {
        if (entityId <= 0 || entityId > GroundEntityIdAllocator.MAX_ALLOCATABLE_ID) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireGroundHighWater(long highWater) {
        if (highWater < 0 || highWater > GroundEntityIdAllocator.MAX_ALLOCATABLE_ID) {
            throw new IllegalStateException("stored ground entity high-water is invalid");
        }
    }

    /**
     * Pickup receipts must distinguish later partial mutations of the same surviving entity while
     * producing the identical key when the same frozen command is retried after a crash.
     */
    static long stableGroundMutationId(long entityId, int namespace,
            long expectedGroundRevision, long expectedPlayerRevision) {
        if (namespace <= 0 || namespace >= GroundEntityIdAllocator.MUTATION_NAMESPACE_LIMIT
                || expectedGroundRevision < 0 || expectedGroundRevision >= Long.MAX_VALUE - 1
                || expectedPlayerRevision < 0 || expectedPlayerRevision == Long.MAX_VALUE) {
            throw new IllegalArgumentException("ground mutation causal identity is invalid");
        }
        requireAllocatableGroundEntityId(entityId,
                "ground mutation causal identity is invalid");
        long value = entityId * 0x9E3779B97F4A7C15L;
        value ^= (long) namespace * 0xD6E8FEB86659FD93L;
        value ^= expectedGroundRevision * 0xA0761D6478BD642FL;
        value ^= expectedPlayerRevision * 0xE7037ED1A0B428DBL;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        value &= Long.MAX_VALUE;
        return value == 0 || value == Long.MAX_VALUE ? 1 : value;
    }

    private enum GroundEntityKind { ITEM, XP }

    /**
     * Tick-owner allocator for the single item/XP identity namespace. The ceiling is derived from
     * the widest stable-mutation namespace, so every emitted identity remains encodable; its
     * successor is a terminal sentinel. Batch reservation is compare-and-advance so an XP constructor
     * failure cannot consume identities and another ground kind cannot interleave.
     */
    static final class GroundEntityIdAllocator {
        static final int MUTATION_NAMESPACE_LIMIT = 8;
        static final long MAX_ALLOCATABLE_ID = GroundMutationCommand.MAX_GROUND_ENTITY_ID;
        static final long EXHAUSTED_SENTINEL = Math.addExact(MAX_ALLOCATABLE_ID, 1L);
        private long nextId = 1;
        private final Map<Long, GroundEntityKind> identityKinds = new HashMap<>();
        private boolean allocationStarted;

        synchronized long nextId() {
            return nextId;
        }

        synchronized long allocate() {
            long allocated = nextId;
            nextId = checkedSuccessor(allocated);
            allocationStarted = true;
            return allocated;
        }

        synchronized long preflightBatch(long count) {
            if (count < 0) throw new IllegalArgumentException("ground entity batch is invalid");
            if (count == 0) return nextId;
            if (nextId <= 0 || nextId > MAX_ALLOCATABLE_ID
                    || count > EXHAUSTED_SENTINEL - nextId) {
                throw new IllegalStateException("ground entity identities exhausted for batch");
            }
            return Math.addExact(nextId, count);
        }

        synchronized void commitBatch(long expectedFirstId, long nextIdAfterBatch) {
            if (expectedFirstId != nextId || expectedFirstId <= 0
                    || expectedFirstId > MAX_ALLOCATABLE_ID
                    || nextIdAfterBatch <= expectedFirstId
                    || nextIdAfterBatch > EXHAUSTED_SENTINEL) {
                throw new IllegalStateException("ground entity identity reservation drifted");
            }
            nextId = nextIdAfterBatch;
            allocationStarted = true;
        }

        synchronized void reserveThrough(long entityId) {
            if (entityId < 0 || entityId > MAX_ALLOCATABLE_ID) {
                throw new IllegalArgumentException("ground entity identity high-water is invalid");
            }
            if (entityId >= nextId) nextId = checkedSuccessor(entityId);
        }

        synchronized void claimSettlementIdentity(long entityId, GroundEntityKind kind) {
            if (kind == null) {
                throw new IllegalArgumentException("ground entity kind is required");
            }
            Map<Long, GroundEntityKind> claim = new HashMap<>();
            claim.put(entityId, kind);
            claimSettlementIdentities(claim);
        }

        synchronized void claimSettlementIdentities(Map<Long, GroundEntityKind> claims) {
            if (claims == null) {
                throw new IllegalArgumentException("ground settlement identities are required");
            }
            long maxClaimedId = 0;
            for (Map.Entry<Long, GroundEntityKind> entry : claims.entrySet()) {
                Long entityId = entry.getKey();
                GroundEntityKind kind = entry.getValue();
                if (entityId == null || kind == null) {
                    throw new IllegalArgumentException("ground settlement identity is invalid");
                }
                requireAllocatableGroundEntityId(entityId,
                        "ground settlement identity is invalid");
                GroundEntityKind existing = identityKinds.get(entityId);
                if (existing != null && existing != kind) {
                    throw new IllegalStateException("ground entity identity collision");
                }
                maxClaimedId = Math.max(maxClaimedId, entityId);
            }
            identityKinds.putAll(claims);
            reserveThrough(maxClaimedId);
        }

        synchronized void restoreIdentities(List<Long> identities) {
            restoreIdentities(identities, List.of());
        }

        synchronized void restoreIdentities(List<Long> itemIdentities, List<Long> xpIdentities) {
            if (itemIdentities == null || xpIdentities == null) {
                throw new IllegalArgumentException("restored ground identities are required");
            }
            if (allocationStarted) {
                throw new IllegalStateException("ground identities cannot be restored after allocation");
            }
            Map<Long, GroundEntityKind> batch = new HashMap<>();
            long maxId = 0;
            List<List<Long>> identitiesByKind = List.of(itemIdentities, xpIdentities);
            GroundEntityKind[] kinds = {GroundEntityKind.ITEM, GroundEntityKind.XP};
            for (int kindIndex = 0; kindIndex < identitiesByKind.size(); kindIndex++) {
                for (Long identity : identitiesByKind.get(kindIndex)) {
                    if (identity == null || identity <= 0 || identity > MAX_ALLOCATABLE_ID
                            || batch.putIfAbsent(identity, kinds[kindIndex]) != null) {
                        throw new IllegalArgumentException(
                                "restored ground identity is invalid or duplicate");
                    }
                    if (identityKinds.containsKey(identity)) {
                        throw new IllegalStateException("restored item/XP identity collision");
                    }
                    maxId = Math.max(maxId, identity);
                }
            }
            identityKinds.putAll(batch);
            reserveThrough(maxId);
        }

        private static long checkedSuccessor(long entityId) {
            if (entityId <= 0 || entityId > MAX_ALLOCATABLE_ID) {
                throw new IllegalArgumentException("ground entity identity allocator is exhausted or invalid");
            }
            return Math.addExact(entityId, 1L);
        }
    }

    private void claimGroundMutationIdentities(GroundMutationCommand command) {
        Map<Long, GroundEntityKind> identities = new LinkedHashMap<>();
        for (var snapshot : command.insertedItems()) {
            addGroundMutationIdentity(identities, snapshot.entityId(), GroundEntityKind.ITEM);
        }
        for (Long entityId : command.removedItemIds()) {
            addGroundMutationIdentity(identities, entityId, GroundEntityKind.ITEM);
        }
        for (var snapshot : command.insertedXpOrbs()) {
            addGroundMutationIdentity(identities, snapshot.entityId(), GroundEntityKind.XP);
        }
        for (Long entityId : command.removedXpOrbIds()) {
            addGroundMutationIdentity(identities, entityId, GroundEntityKind.XP);
        }
        for (Map.Entry<Long, GroundEntityKind> entry : identities.entrySet()) {
            if ((entry.getValue() == GroundEntityKind.ITEM
                    && xpOrbSystem.containsIdentity(entry.getKey()))
                    || (entry.getValue() == GroundEntityKind.XP
                    && itemSystem.containsIdentity(entry.getKey()))) {
                throw new IllegalStateException("ground entity identity collision");
            }
        }
        groundEntityIds.claimSettlementIdentities(identities);
    }

    private static void addGroundMutationIdentity(Map<Long, GroundEntityKind> identities,
            long entityId, GroundEntityKind kind) {
        requireAllocatableGroundEntityId(entityId, "ground mutation entity identity is invalid");
        GroundEntityKind previous = identities.putIfAbsent(entityId, kind);
        if (previous != null && previous != kind) {
            throw new IllegalStateException("ground entity identity collision");
        }
    }

    private static void validateGroundEntitySnapshotIdentities(
            List<com.gameexpert.ground.dto.GroundItemSnapshot> items,
            List<com.gameexpert.ground.dto.GroundXpOrbSnapshot> xpOrbs) {
        if (items == null || xpOrbs == null) {
            throw new IllegalArgumentException("complete ground entity snapshot is required");
        }
        Set<Long> itemIds = new HashSet<>();
        for (var snapshot : items) {
            if (snapshot == null) {
                throw new IllegalArgumentException("ground item snapshot is required");
            }
            requireAllocatableGroundEntityId(snapshot.entityId(),
                    "ground item entity identity is invalid");
            if (!itemIds.add(snapshot.entityId())) {
                throw new IllegalArgumentException("duplicate ground item identity");
            }
        }
        Set<Long> xpIds = new HashSet<>();
        for (var snapshot : xpOrbs) {
            if (snapshot == null) {
                throw new IllegalArgumentException("ground XP orb snapshot is required");
            }
            requireAllocatableGroundEntityId(snapshot.entityId(),
                    "ground XP entity identity is invalid");
            if (!xpIds.add(snapshot.entityId())) {
                throw new IllegalArgumentException("duplicate ground XP identity");
            }
        }
        itemIds.retainAll(xpIds);
        if (!itemIds.isEmpty()) {
            throw new IllegalStateException("ground entity identity collision");
        }
    }

    private boolean groundLaneAvailable() {
        return terminalPhase == TerminalPhase.RUNNING
                && groundMutationSettlements != null && !groundSettlementInFlight
                && !groundSaveInFlight && !projectileSaveInFlight;
    }

    private boolean groundSpawnLaneAvailable() {
        return (terminalPhase == TerminalPhase.RUNNING || terminalPhase == TerminalPhase.DRAINING)
                && !persistenceCallbackAdmissionClosed
                && groundMutationSettlements != null && !groundSettlementInFlight
                && !groundSaveInFlight && !projectileSaveInFlight;
    }

    boolean hasPendingGroundSpawns() {
        return groundMutationSettlements != null && (pendingGroundSpawnBatch != null
                || itemSystem.hasPendingSpawns() || xpOrbSystem.hasPendingSpawns());
    }

    synchronized boolean groundSettlementAvailable() {
        // Later allocations must not advance the durable high-water past an older queued spawn.
        return groundLaneAvailable() && !hasPendingGroundSpawns();
    }

    /** Debug-only observation of the same admission fields; never acquires a settlement. */
    void auditGroundPickupLane(long itemId, String reason) {
        if (!GroundPickupAudit.enabled(worldId, itemId)) return;
        synchronized (this) {
            int blockerMask = (terminalPhase == TerminalPhase.RUNNING ? 0 : 1)
                    | (groundMutationSettlements == null ? 2 : 0)
                    | (groundSettlementInFlight ? 4 : 0) | (groundSaveInFlight ? 8 : 0)
                    | (projectileSaveInFlight ? 16 : 0) | (pendingGroundSpawnBatch == null ? 0 : 32)
                    | (itemSystem.hasPendingSpawns() ? 64 : 0) | (xpOrbSystem.hasPendingSpawns() ? 128 : 0);
            GroundPickupAudit.event(worldId, itemId, "lane", reason + "_MASK_" + blockerMask,
                    "groundRevision,running,service,settlementInFlight,groundSaveInFlight,projectileSaveInFlight,pendingBatch,pendingItems,pendingXp,blockerMask",
                    groundRevision, terminalPhase == TerminalPhase.RUNNING ? 1 : 0,
                    groundMutationSettlements == null ? 0 : 1, groundSettlementInFlight ? 1 : 0,
                    groundSaveInFlight ? 1 : 0, projectileSaveInFlight ? 1 : 0,
                    pendingGroundSpawnBatch == null ? 0 : 1, itemSystem.hasPendingSpawns() ? 1 : 0,
                    xpOrbSystem.hasPendingSpawns() ? 1 : 0, blockerMask);
        }
    }

    private static final class PendingGroundSpawnBatch {
        private final GroundMutationCommand command;

        private PendingGroundSpawnBatch(GroundMutationCommand command) {
            this.command = command;
        }
    }

    /** Both kinds share an allocator and therefore must cross the high-water boundary together. */
    synchronized void settlePendingGroundSpawns() {
        if (!groundSpawnLaneAvailable()) return;
        if (pendingGroundSpawnBatch == null) {
            var items = itemSystem.pendingSpawnSnapshots();
            var xp = xpOrbSystem.pendingSpawnSnapshots();
            if (items.isEmpty() && xp.isEmpty()) return;
            long firstItem = items.isEmpty() ? Long.MAX_VALUE : items.getFirst().entityId();
            long firstXp = xp.isEmpty() ? Long.MAX_VALUE : xp.getFirst().entityId();
            long firstId = Math.min(firstItem, firstXp);
            int namespace = firstItem < firstXp ? 5 : 6;
            pendingGroundSpawnBatch = new PendingGroundSpawnBatch(new GroundMutationCommand(
                    stableGroundMutationId(firstId, namespace), GroundMutationCommand.Kind.BLOCK_DROP,
                    worldId, groundRevision, groundRevision + 1, null, null,
                    items, List.of(), xp, List.of()));
        }
        PendingGroundSpawnBatch batch = pendingGroundSpawnBatch;
        submitGroundSettlement(batch.command, () -> {
            if (pendingGroundSpawnBatch != batch) {
                throw new IllegalStateException("committed ground spawn batch lost its owner");
            }
            itemSystem.validatePendingSpawns(batch.command.insertedItems());
            xpOrbSystem.validatePendingSpawns(batch.command.insertedXpOrbs());
            itemSystem.commitPendingSpawns(batch.command.insertedItems());
            xpOrbSystem.commitPendingSpawns(batch.command.insertedXpOrbs());
            pendingGroundSpawnBatch = null;
        }, () -> { }, batch);
    }

    synchronized boolean beginGroundSettlement() {
        if (!groundSettlementAvailable()) return false;
        groundSettlementInFlight = true;
        return true;
    }

    private final class GroundSettlementOperation {
        private final GroundMutationCommand command;
        private final Runnable committed;
        private final Runnable rejected;
        private final boolean inventoryDropAudit;
        private final AtomicBoolean groundSettled = new AtomicBoolean();
        private final AtomicBoolean rejectionDelivered = new AtomicBoolean();

        private GroundSettlementOperation(GroundMutationCommand command,
                Runnable committed, Runnable rejected) {
            this(command, committed, rejected, false);
        }

        private GroundSettlementOperation(GroundMutationCommand command,
                Runnable committed, Runnable rejected, boolean inventoryDropAudit) {
            this.command = command;
            this.committed = committed == null ? () -> { } : committed;
            this.rejected = rejected == null ? () -> { } : rejected;
            this.inventoryDropAudit = inventoryDropAudit
                    && command.kind() == GroundMutationCommand.Kind.PLAYER_DROP;
        }

        private void audit(String phase, Object outcome) {
            if (GroundPickupAudit.enabled(command)) {
                // Existing call sites pass fixed outcomes or exception class names, never messages.
                GroundPickupAudit.event(command, phase, String.valueOf(outcome),
                        "mutationId,playerId,sourceRevision,expectedGroundRevision",
                        command.mutationId(), command.committedPlayer() == null ? -1L : command.committedPlayer().playerId(),
                        command.expectedPlayerRevision() == null ? -1L : command.expectedPlayerRevision(),
                        command.expectedGroundRevision());
            }
            if (!inventoryDropAudit || !dropAudit.isDebugEnabled()) return;
            dropAudit.debug("phase={} world={} mutationId={} playerId={} sourceRevision={} expectedGroundRevision={} outcome={}",
                    phase, worldId, command.mutationId(),
                    command.committedPlayer() == null ? null : command.committedPlayer().playerId(),
                    command.expectedPlayerRevision(), command.expectedGroundRevision(), outcome);
        }

        private void auditAdmission() {
            if (GroundPickupAudit.enabled(command)) {
                GroundPickupAudit.event(command, "admission", "OBSERVED",
                        "mutationId,sourceRevision,expectedGroundRevision,currentGroundRevision,running,service,settlementInFlight,groundSaveInFlight,projectileSaveInFlight,pendingGroundSpawns",
                        command.mutationId(), command.expectedPlayerRevision() == null ? -1L : command.expectedPlayerRevision(), command.expectedGroundRevision(),
                        groundRevision, terminalPhase == TerminalPhase.RUNNING ? 1 : 0,
                        groundMutationSettlements == null ? 0 : 1, groundSettlementInFlight ? 1 : 0,
                        groundSaveInFlight ? 1 : 0, projectileSaveInFlight ? 1 : 0,
                        hasPendingGroundSpawns() ? 1 : 0);
            }
            if (!inventoryDropAudit || !dropAudit.isDebugEnabled()) return;
            dropAudit.debug("phase=admission world={} mutationId={} sourceRevision={} expectedGroundRevision={} currentGroundRevision={} terminalPhase={} serviceAvailable={} groundSettlementInFlight={} groundSaveInFlight={} projectileSaveInFlight={} pendingGroundSpawns={}",
                    worldId, command.mutationId(), command.expectedPlayerRevision(),
                    command.expectedGroundRevision(), groundRevision, terminalPhase,
                    groundMutationSettlements != null, groundSettlementInFlight,
                    groundSaveInFlight, projectileSaveInFlight, hasPendingGroundSpawns());
        }

        private void commit() {
            if (!groundSettled.compareAndSet(false, true)) return;
            audit("terminal", "commit");
            completeGroundSettlement(command.committedGroundRevision(), true);
            try {
                committed.run();
            } catch (Throwable failure) {
                audit("commit-callback-exception", failure.getClass().getName());
                rejectCallback();
                recordPersistenceCompletionFailures(List.of(failure));
            }
        }

        private void reject() {
            if (groundSettled.compareAndSet(false, true)) {
                audit("terminal", "reject");
                completeGroundSettlement(command.committedGroundRevision(), false);
            }
            rejectCallback();
        }

        private void rejectCallback() {
            if (!rejectionDelivered.compareAndSet(false, true)) return;
            try {
                rejected.run();
            } catch (Throwable failure) {
                audit("reject-callback-exception", failure.getClass().getName());
                recordPersistenceCompletionFailures(List.of(failure));
            }
        }
    }

    boolean submitGroundSettlement(GroundMutationCommand command,
            Runnable committed, Runnable rejected) {
        return submitGroundSettlement(command, committed, rejected, null);
    }

    /** 임시 Q 드롭 진단만 활성화하며 정산 동작은 공통 경로가 그대로 소유한다. */
    boolean submitInventoryDropSettlement(GroundMutationCommand command,
            Runnable committed, Runnable rejected) {
        return submitGroundSettlement(command, committed, rejected, null, true);
    }

    private boolean submitGroundSettlement(GroundMutationCommand command,
            Runnable committed, Runnable rejected, PendingGroundSpawnBatch spawnBatch) {
        return submitGroundSettlement(command, committed, rejected, spawnBatch, false);
    }

    private boolean submitGroundSettlement(GroundMutationCommand command,
            Runnable committed, Runnable rejected, PendingGroundSpawnBatch spawnBatch,
            boolean inventoryDropAudit) {
        if (command == null || !worldId.equals(command.worldId())) {
            throw new IllegalArgumentException("ground settlement world mismatch");
        }
        GroundSettlementOperation operation = new GroundSettlementOperation(
                command, committed, rejected, inventoryDropAudit);
        synchronized (this) {
            operation.auditAdmission();
            if ((terminalPhase != TerminalPhase.RUNNING
                    && !(spawnBatch != null && terminalPhase == TerminalPhase.DRAINING))
                    || command.expectedGroundRevision() != groundRevision) {
                operation.audit("submit-refused", "terminal-or-revision");
                return false;
            }
            if (spawnBatch == null) {
                if (!beginGroundSettlement()) {
                    operation.audit("submit-refused", "ground-unavailable");
                    return false;
                }
            } else {
                if (spawnBatch != pendingGroundSpawnBatch || !groundSpawnLaneAvailable()) return false;
                groundSettlementInFlight = true;
            }
        }
        try {
            claimGroundMutationIdentities(command);
        } catch (RuntimeException | Error failure) {
            operation.audit("identity-exception", failure.getClass().getName());
            operation.reject();
            throw failure;
        }
        if (!ensureBlockCheckpointBeforeSettlement()) {
            operation.audit("submit-refused", "block-checkpoint");
            completeGroundSettlement(command.committedGroundRevision(), false);
            return false;
        }
        PersistenceExecutor persistence = ctx.persistenceExecutor();
        if (persistence == null) {
            operation.audit("submit-accepted", "inline");
            GroundMutationOutcome outcome;
            try {
                operation.audit("settle-start", "inline");
                outcome = groundMutationSettlements.settle(command);
            } catch (RuntimeException | Error failure) {
                operation.audit("settle-exception", failure.getClass().getName());
                operation.reject();
                return true;
            }
            boolean success = outcome == GroundMutationOutcome.COMMITTED
                    || outcome == GroundMutationOutcome.IDEMPOTENT;
            operation.audit("settle-outcome", outcome);
            if (success) operation.commit();
            else operation.reject();
            return true;
        }
        boolean accepted;
        try {
            accepted = persistence.trySubmit(() -> {
                GroundMutationOutcome outcome = GroundMutationOutcome.STALE;
                try {
                    operation.audit("settle-start", "async");
                    outcome = groundMutationSettlements.settle(command);
                } catch (RuntimeException | Error failure) {
                    operation.audit("settle-exception", failure.getClass().getName());
                    // The authority-owned completion releases any inventory lease on the tick thread.
                }
                boolean success = outcome == GroundMutationOutcome.COMMITTED
                        || outcome == GroundMutationOutcome.IDEMPOTENT;
                operation.audit("settle-outcome", outcome);
                try {
                    enqueuePersistenceCompletion(success ? operation::commit : operation::reject,
                            operation::reject);
                    operation.audit("completion-enqueued", success ? "commit" : "reject");
                    // 스폰·획득·Q 드롭은 저장이 끝나도 다음 100ms 틱까지 응답을 묵히지 않는다.
                    // 기존 owner 연속 작업에서 커밋된 인벤토리와 엔티티 변경을 함께 공표한다.
                    if (spawnBatch != null
                            || command.kind() == GroundMutationCommand.Kind.PLAYER_PICKUP
                            || command.kind() == GroundMutationCommand.Kind.XP_PICKUP
                            || command.kind() == GroundMutationCommand.Kind.PLAYER_DROP) {
                        requestPersistenceOwnerDrainIfNeeded();
                    }
                } catch (RuntimeException | Error failure) {
                    operation.audit("completion-enqueue-exception", failure.getClass().getName());
                    operation.reject();
                    throw failure;
                }
            });
        } catch (RuntimeException | Error failure) {
            operation.audit("submit-exception", failure.getClass().getName());
            operation.reject();
            throw failure;
        }
        if (!accepted) {
            operation.audit("submit-refused", "persistence-executor");
            completeGroundSettlement(command.committedGroundRevision(), false);
            return false;
        }
        operation.audit("submit-accepted", "async");
        return true;
    }

    /**
     * Orders the durable block change ahead of the settlement that credits its items. Without this
     * boundary an owner crash between the two writes restores a mined block whose drop was already
     * committed, which duplicates the item.
     *
     * Never waits. Settlement runs under this runtime's monitor, and a writer task queued ahead of
     * the checkpoint installs its completion under that same monitor, so waiting here stalls the
     * writer for every world on the node. When the previous checkpoint is still in flight the
     * settlement is refused; the tick retries it, and a blocking action boundary waits outside the
     * monitor before retrying.
     */
    private boolean ensureBlockCheckpointBeforeSettlement() {
        com.gameexpert.block.persistence.BlockDiffBuffer buffer = ctx.blockDiffBuffer();
        if (buffer == null || !buffer.hasPending(worldId)) return true;
        return tickLoop.submitBlockCheckpointForSettlement();
    }

    synchronized long completeGroundSettlement(long committedRevision, boolean committed) {
        if (committed) groundRevision = committedRevision;
        groundSettlementInFlight = false;
        return groundRevision;
    }

    /** Writes both live sets in one transaction; a failure leaves the prior complete checkpoint. */
    synchronized void persistGroundEntities(long tickNo) {
        if (groundEntityPersistence == null || terminalPhase != TerminalPhase.RUNNING) return;
        observeGroundChanges(tickNo % 10 == 0);
        submitGroundCheckpoint();
    }

    /** Loads live projectiles before welcome publication; launch sounds are never replayed. */
    void attachProjectilePersistence(ProjectilePersistenceService persistence) {
        if (persistence == null) return;
        if (projectilePersistence != null) {
            throw new IllegalStateException("projectile persistence is already attached");
        }
        var restored = persistence.loadWorld(worldId);
        mobSystem.restoreProjectiles(restored);
        observedProjectileRevision = mobSystem.projectileRevision();
        projectileLedgerMayBeNonEmpty = !restored.isEmpty();
        projectilePersistence = persistence;
    }

    /** Coalesced exact checkpoint; active projectiles move every tick, so no elapsed-time cadence is used. */
    void persistProjectiles() {
        ProjectileRecoveryCheckpoint recovery;
        synchronized (this) {
            if (projectilePersistence == null || terminalPhase != TerminalPhase.RUNNING) return;
            if (groundSaveInFlight || groundSettlementInFlight || hasPendingGroundSpawns()) return;
            long runtimeRevision = mobSystem.projectileRevision();
            if (runtimeRevision != observedProjectileRevision) {
                observedProjectileRevision = runtimeRevision;
                projectileDirtyRevision++;
            }
            if (!mobSystem.projectileRecoveryPersistencePending()
                    && projectileDirtyRevision <= projectilePersistedRevision) return;
            if (projectileSaveInFlight) return;
            List<com.gameexpert.projectile.dto.ProjectilePersistenceSnapshot> snapshot =
                    mobSystem.projectilePersistenceSnapshot();
            if (!mobSystem.projectileRecoveryPersistencePending()) {
                projectileLedgerMayBeNonEmpty = !snapshot.isEmpty();
                submitProjectileCheckpoint(projectileDirtyRevision, snapshot);
                return;
            }
            List<com.gameexpert.ground.dto.GroundItemSnapshot> items = itemSystem.persistenceSnapshot();
            List<com.gameexpert.ground.dto.GroundXpOrbSnapshot> xpOrbs = xpOrbSystem.persistenceSnapshot();
            validateGroundEntitySnapshotIdentities(items, xpOrbs);
            recovery = new ProjectileRecoveryCheckpoint(groundRevision, snapshot, items, xpOrbs);
            projectileSaveInFlight = true;
        }
        // Earlier writer tasks enqueue completions under this monitor. Keep the owner turn's
        // atomic recovery boundary, but let those tasks finish while waiting for our transaction.
        persistProjectileRecoveryBlocking(recovery);
    }

    private static final class ProjectileRecoveryCheckpoint {
        private final long expectedGroundRevision;
        private final List<com.gameexpert.projectile.dto.ProjectilePersistenceSnapshot> projectiles;
        private final List<com.gameexpert.ground.dto.GroundItemSnapshot> items;
        private final List<com.gameexpert.ground.dto.GroundXpOrbSnapshot> xpOrbs;

        private ProjectileRecoveryCheckpoint(long expectedGroundRevision,
                List<com.gameexpert.projectile.dto.ProjectilePersistenceSnapshot> projectiles,
                List<com.gameexpert.ground.dto.GroundItemSnapshot> items,
                List<com.gameexpert.ground.dto.GroundXpOrbSnapshot> xpOrbs) {
            this.expectedGroundRevision = expectedGroundRevision;
            this.projectiles = projectiles;
            this.items = items;
            this.xpOrbs = xpOrbs;
        }
    }

    private void persistProjectileRecoveryBlocking(ProjectileRecoveryCheckpoint recovery) {
        long committedGroundRevision = recovery.expectedGroundRevision + 1;
        java.util.concurrent.atomic.AtomicReference<GroundMutationOutcome> result =
                new java.util.concurrent.atomic.AtomicReference<>(GroundMutationOutcome.STALE);
        Runnable transaction = () -> result.set(projectilePersistence.replaceWorldWithGround(
                worldId, recovery.expectedGroundRevision, committedGroundRevision,
                recovery.projectiles, recovery.items, recovery.xpOrbs));
        boolean interrupted = false;
        try {
            PersistenceExecutor writer = ctx.persistenceExecutor();
            if (writer == null) {
                transaction.run();
            } else {
                java.util.concurrent.Future<?> pending = writer.submitFuture(transaction);
                // An interrupt does not cancel an already-running DB transaction. Do not release
                // the shared ground lane until its outcome is known; restore the interrupt below.
                boolean completed = false;
                while (!completed) {
                    try {
                        pending.get();
                        completed = true;
                    } catch (InterruptedException interruption) {
                        interrupted = true;
                    } catch (ExecutionException failure) {
                        throw new IllegalStateException("projectile recovery persistence failed", failure.getCause());
                    }
                }
            }
            synchronized (this) {
                if (result.get() != GroundMutationOutcome.COMMITTED) return;
                groundRevision = committedGroundRevision;
                projectileLedgerMayBeNonEmpty = !recovery.projectiles.isEmpty();
                projectilePersistedRevision = ++projectileDirtyRevision;
                if (terminalPhase == TerminalPhase.RUNNING || terminalPhase == TerminalPhase.DRAINING) {
                    mobSystem.acknowledgeProjectileRecoveryPersistence();
                }
            }
        } finally {
            synchronized (this) {
                projectileSaveInFlight = false;
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    /** Synchronous command boundary used only for committed player launches, never for projectile movement. */
    void persistPlayerProjectileLaunch(PlayerTickState state, long expectedPlayerRevision) {
        if (projectilePersistence == null || terminalPhase != TerminalPhase.RUNNING) return;
        awaitPendingGroundSpawnsBeforeBlockingAction();
        List<com.gameexpert.projectile.dto.ProjectilePersistenceSnapshot> projectiles =
                mobSystem.projectilePersistenceSnapshot();
        var items = itemSystem.persistenceSnapshot();
        var xpOrbs = xpOrbSystem.persistenceSnapshot();
        validateGroundEntitySnapshotIdentities(items, xpOrbs);
        java.util.concurrent.atomic.AtomicLong committedGroundRevision =
                new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicReference<GroundMutationOutcome> result =
                new java.util.concurrent.atomic.AtomicReference<>(GroundMutationOutcome.STALE);
        Runnable transaction;
        if (expectedPlayerRevision < 0) {
            transaction = () -> {
                long expected = groundRevision;
                committedGroundRevision.set(expected + 1);
                result.set(projectilePersistence.replaceWorldWithGround(worldId,
                        expected, expected + 1, projectiles, items, xpOrbs));
            };
        } else {
            PlayerInventoryMutationSnapshot player = playerInventoryMutationSnapshot(state);
            transaction = () -> {
                long expected = groundRevision;
                committedGroundRevision.set(expected + 1);
                result.set(projectilePersistence.replaceWorldWithPlayerAndGround(worldId,
                        expected, expected + 1, expectedPlayerRevision,
                        projectiles, items, xpOrbs, player));
            };
        }
        PersistenceExecutor writer = ctx.persistenceExecutor();
        if (writer == null) {
            transaction.run();
        } else {
            // The shared writer orders this launch after any older projectile checkpoint. Blocking
            // is intentional at the rare player action boundary: no launch event is observable
            // until the inventory consumption and projectile row have committed together.
            try {
                writer.submitFuture(transaction).get();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("projectile launch persistence interrupted", interrupted);
            } catch (ExecutionException failure) {
                throw new IllegalStateException("projectile launch persistence failed", failure.getCause());
            }
        }
        if (result.get() != GroundMutationOutcome.COMMITTED) {
            throw new IllegalStateException("stale projectile launch settlement");
        }
        groundRevision = committedGroundRevision.get();
        projectileLedgerMayBeNonEmpty = !projectiles.isEmpty();
        observedProjectileRevision = mobSystem.projectileRevision();
        projectilePersistedRevision = ++projectileDirtyRevision;
    }

    /**
     * The existing synchronous action boundary must include older spawn receipts before taking
     * its whole-ground snapshot. Wait only for writer tasks, then install callbacks on this owner;
     * a writer task must never wait for a callback which needs this same owner thread.
     */
    private void awaitPendingGroundSpawnsBeforeBlockingAction() {
        if (!hasPendingGroundSpawns()) return;
        awaitGroundWriterTasks();
        drainPersistenceCompletions();
        int unsettled = 0;
        while (hasPendingGroundSpawns()) {
            settlePendingGroundSpawns();
            if (!hasPendingGroundSpawns()) break;
            PendingGroundSpawnBatch submitted = pendingGroundSpawnBatch;
            if (submitted == null) {
                throw new IllegalStateException("ground spawn lane is unavailable for action");
            }
            awaitGroundWriterTasks();
            drainPersistenceCompletions();
            // A settlement refused behind an in-flight block checkpoint is retried once the writer
            // has committed that checkpoint; only a batch that keeps failing blocks the action.
            if (pendingGroundSpawnBatch == submitted && ++unsettled > 3) {
                throw new IllegalStateException("ground spawn settlement did not commit before action");
            }
        }
    }

    private void awaitGroundWriterTasks() {
        PersistenceExecutor writer = ctx.persistenceExecutor();
        if (writer == null) return;
        try {
            writer.submitFuture(() -> { }).get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ground spawn action boundary interrupted", interrupted);
        } catch (ExecutionException failure) {
            throw new IllegalStateException("ground spawn action boundary failed", failure.getCause());
        }
    }

    PlayerInventoryMutationSnapshot playerInventoryMutationSnapshot(PlayerTickState state) {
        return playerInventoryMutationSnapshot(
                state, state.inventory().completePersistenceSnapshot());
    }

    PlayerInventoryMutationSnapshot playerInventoryMutationSnapshot(PlayerTickState state,
            PlayerInventory.CompletePersistenceSnapshot complete) {
        return playerInventoryMutationSnapshot(state, complete, state.xpTotal());
    }

    PlayerInventoryMutationSnapshot playerInventoryMutationSnapshot(PlayerTickState state,
            PlayerInventory.CompletePersistenceSnapshot complete, int committedXpTotal) {
        PlayerInventory.PersistenceSnapshot inventory = complete.persistenceSnapshot();
        return new PlayerInventoryMutationSnapshot(state.playerId(), worldId,
                complete.revision(), state.x(), state.y(), state.z(), state.yaw(), state.pitch(),
                state.health(), inventory.itemTypes(), inventory.counts(), inventory.durabilities(),
                inventory.enchantments(), inventory.mapIds(), inventory.shulkerIds(),
                inventory.bucketMobData(), inventory.itemComponentData(),
                complete.equippedTypes(),
                complete.equippedDurabilities(), complete.equippedEnchantments(),
                complete.equippedItemComponentData(),
                complete.offhand(),
                state.hasBedSpawn() ? state.bedSpawnX() : null,
                state.hasBedSpawn() ? state.bedSpawnY() : null,
                state.hasBedSpawn() ? state.bedSpawnZ() : null,
                state.food(), state.saturationMilli(), committedXpTotal, state.enchantSeed(),
                state.timeSinceRestMcTicks(), state.enderChest().snapshot(),
                state.statusEffects().persistenceSnapshot(), state.effectClocksSnapshot(),
                complete.selectedSlot(), state.fireTicks(), state.fireAccum())
                .withDimensionIdentity(state.dimensionIdentity(worldId));
    }

    void enqueuePersistenceCompletion(Runnable completion) {
        enqueuePersistenceCompletion(completion, null);
    }

    /**
     * Enqueues a completion only while its terminal phase can still accept the corresponding work.
     * Generic bookkeeping is retained for an already-disposed/aborted writer result; a live-only
     * completion is rejected at that boundary instead of becoming a late mutation.
     */
    private boolean enqueuePersistenceCompletion(Runnable live, Runnable generic) {
        if (live == null && generic == null) return false;
        PersistenceCompletion completion;
        TerminalPhase phase;
        boolean liveAdmitted;
        synchronized (this) {
            phase = terminalPhase;
            liveAdmitted = (phase == TerminalPhase.RUNNING || phase == TerminalPhase.DRAINING)
                    && !persistenceCallbackAdmissionClosed;
            // Admission and queue insertion share the runtime monitor. A callback that arrives
            // after the fence is represented as generic-only, so a late writer can never publish
            // live state while still retaining the release/bookkeeping half.
            if (!liveAdmitted && generic == null) return false;
            completion = liveAdmitted
                    ? new PersistenceCompletion(live, generic, true)
                    : new PersistenceCompletion(null, generic, false);
            persistenceCompletions.offer(completion);
        }
        if (phase != TerminalPhase.RUNNING) requestTerminalOwnerDrain();
        // A generic-only late node is retained and reported as accepted for callers whose
        // bookkeeping depends on the node being drained, while its live half was not admitted.
        return liveAdmitted || generic != null;
    }

    private void enqueueMapPersistenceCompletion(Runnable live, Runnable generic) {
        if (live == null && generic == null) return;
        PersistenceCompletion completion;
        TerminalPhase phase;
        synchronized (this) {
            phase = terminalPhase;
            boolean liveAdmitted = (phase == TerminalPhase.RUNNING
                    || phase == TerminalPhase.DRAINING)
                    && !persistenceCallbackAdmissionClosed;
            if (!liveAdmitted && generic == null) return;
            completion = liveAdmitted
                    ? new PersistenceCompletion(live, generic, true)
                    : new PersistenceCompletion(null, generic, false);
            mapPersistenceCompletions.offer(completion);
        }
        if (phase != TerminalPhase.RUNNING) requestTerminalOwnerDrain();
    }

    private void runPersistenceCompletion(PersistenceCompletion completion, TerminalPhase phase) {
        if (completion == null) return;
        if (ownerQueueRequired()) {
            persistenceCompletions.offer(completion);
            requestTerminalOwnerDrain();
            return;
        }
        persistenceCompletionsRun.incrementAndGet();
        TerminalPhase admittedPhase;
        boolean liveAdmitted;
        synchronized (this) {
            // ABORTED/DISPOSED is an explicit residual-drain mode even when the phase field is
            // still DRAINING. Normal owner drains use the current phase, not a stale caller
            // snapshot, before admitting a live callback.
            admittedPhase = phase == TerminalPhase.ABORTED || phase == TerminalPhase.DISPOSED
                    ? phase : terminalPhase;
            liveAdmitted = (admittedPhase == TerminalPhase.RUNNING
                    || admittedPhase == TerminalPhase.DRAINING) && completion.admitLive();
        }
        List<Throwable> failures = new ArrayList<>(completion.run(admittedPhase, liveAdmitted));
        // A live callback may cross the terminal boundary while it is executing. Its live
        // operation is never retried; finish only the generic release/bookkeeping half once the
        // callback returns so no admitted completion remains stranded.
        if (completion.liveWasStarted() && terminalPhase != admittedPhase
                && (terminalPhase == TerminalPhase.DRAINING
                        || terminalPhase == TerminalPhase.ABORTED
                        || terminalPhase == TerminalPhase.DISPOSED)) {
            failures.addAll(completion.runGenericOnly());
        }
        recordPersistenceCompletionFailures(failures);
    }

    private void recordPersistenceCompletionFailures(List<Throwable> failures) {
        for (Throwable failure : failures) {
            if (failure == null) continue;
            persistenceCompletionFailures.add(failure);
            log.error("Persistence completion failed for world {}", worldId, failure);
        }
    }

    TerminalOutcome terminalOutcome() {
        return terminalOutcome;
    }

    FinalCarrierTickPublicationOutcome finalCarrierTickPublicationOutcome() {
        return lastFinalCarrierTickPublicationOutcome;
    }

    private void publishTerminalOutcome(TerminalOutcomeKind kind) {
        TerminalOutcomeKind effective = kind;
        if (terminalDeadlineReached || kind == TerminalOutcomeKind.DEADLINE) {
            effective = TerminalOutcomeKind.DEADLINE;
        } else if (kind == TerminalOutcomeKind.CALLBACK_FAILURE
                || !persistenceCompletionFailures.isEmpty()
                || !terminalCleanupFailures.isEmpty()
                || disposalPersistenceFailed) {
            effective = TerminalOutcomeKind.CALLBACK_FAILURE;
        }
        List<Throwable> failures = new ArrayList<>(persistenceCompletionFailures);
        failures.addAll(terminalCleanupFailures);
        Set<FinalCarrierTickScheduler.DrainStatus> schedulerStatuses;
        synchronized (this) {
            schedulerStatuses = finalCarrierDrainStatuses.isEmpty()
                    ? Set.of() : EnumSet.copyOf(finalCarrierDrainStatuses);
        }
        terminalOutcome = new TerminalOutcome(effective, persistenceCompletionsRun.get(),
                failures, schedulerStatuses);
    }

    /** Shared FIFO handoff for animal settlements initiated outside WorldTickLoop. */
    boolean submitAnimalSettlementPersistence(Runnable transaction, Runnable rejected) {
        if (terminalPhase != TerminalPhase.RUNNING) {
            if (rejected != null) rejected.run();
            return false;
        }
        PersistenceExecutor writer = ctx.persistenceExecutor();
        if (writer == null) {
            throw new IllegalStateException("animal settlements require the persistence writer");
        }
        animalSettlementWriterTasks.incrementAndGet();
        boolean submitted = writer.trySubmit(() -> {
            try {
                transaction.run();
            } finally {
                enqueuePersistenceCompletion(animalSettlementWriterTasks::decrementAndGet,
                        animalSettlementWriterTasks::decrementAndGet);
            }
        });
        if (!submitted) {
            animalSettlementWriterTasks.decrementAndGet();
            if (rejected != null) rejected.run();
        }
        return submitted;
    }

    /** Hydrates the durable banner aggregate before the runtime begins publishing chunk snapshots. */
    void installBannerPersistence(BannerBlockPersistenceService service) {
        bannerPersistence = service;
        bannerBlocks.clear();
        if (service == null) return;
        for (BannerBlockData banner : service.loadWorld(worldId)) {
            bannerBlocks.put(new BlockPos(banner.x(), banner.y(), banner.z()), banner);
        }
    }

    void installBannerPlacementSettlements(BannerPlacementSettlementService service) {
        bannerPlacementSettlements = service;
    }

    void installSignPersistence(SignBlockPersistenceService service) {
        signPersistence = service;
        signBlocks.clear();
        if (service == null) return;
        for (SignBlockData sign : service.loadWorld(worldId)) {
            signBlocks.put(new BlockPos(sign.x(), sign.y(), sign.z()), sign);
        }
    }

    void stageSignPlacement(int x, int y, int z) {
        if (terminalPhase != TerminalPhase.RUNNING) return;
        stagedSignPlacements.put(new BlockPos(x, y, z), SignBlockData.empty(x, y, z));
    }

    SignBlockData signAt(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        SignBlockData staged = stagedSignPlacements.get(pos);
        return staged != null ? staged : signBlocks.get(pos);
    }

    void replaceSignText(SignBlockData sign) {
        if (terminalPhase != TerminalPhase.RUNNING || sign == null) return;
        BlockPos pos = new BlockPos(sign.x(), sign.y(), sign.z());
        if (stagedSignPlacements.containsKey(pos)) stagedSignPlacements.put(pos, sign);
        else signBlocks.put(pos, sign);
        persistSign(pos, sign, true);
    }

    BannerPlacementSettlementService bannerPlacementSettlements() {
        return bannerPlacementSettlements;
    }

    BannerBlockData bannerAt(int x, int y, int z) {
        return bannerBlocks.get(new BlockPos(x, y, z));
    }

    /** Uses the existing ground reservation/completion boundary for a durable banner transfer. */
    boolean submitBannerMiningSettlement(
            com.gameexpert.banner.dto.BannerMiningSettlementCommand command,
            Runnable committed, Runnable rejected) {
        if (bannerPlacementSettlements == null || command == null
                || !worldId.equals(command.groundMutation().worldId())) return false;
        var ground = command.groundMutation();
        GroundSettlementOperation operation = new GroundSettlementOperation(ground, committed, rejected);
        synchronized (this) {
            if (ground.expectedGroundRevision() != groundRevision || !beginGroundSettlement()) return false;
        }
        try {
            claimGroundMutationIdentities(ground);
        } catch (RuntimeException | Error failure) {
            operation.reject();
            throw failure;
        }
        Runnable transaction = () -> {
            BannerPlacementSettlementService.Outcome outcome;
            try {
                outcome = bannerPlacementSettlements.settleMining(command);
            } catch (RuntimeException | Error failure) {
                log.warn("월드 {} 배너 채굴 정산 실패 ({},{},{}): {}", worldId,
                        command.expectedBanner().x(), command.expectedBanner().y(),
                        command.expectedBanner().z(), failure.toString());
                outcome = BannerPlacementSettlementService.Outcome.STALE;
            }
            boolean accepted = outcome == BannerPlacementSettlementService.Outcome.COMMITTED
                    || outcome == BannerPlacementSettlementService.Outcome.IDEMPOTENT;
            enqueuePersistenceCompletion(accepted ? operation::commit : operation::reject,
                    operation::reject);
        };
        PersistenceExecutor writer = ctx.persistenceExecutor();
        if (writer == null) {
            transaction.run();
            drainPersistenceCompletions();
            return true;
        }
        try {
            if (writer.trySubmit(transaction)) return true;
        } catch (RuntimeException | Error failure) {
            operation.reject();
            throw failure;
        }
        completeGroundSettlement(ground.committedGroundRevision(), false);
        return false;
    }

    /** The DB transaction already removed this entity; generic block persistence must not repeat it. */
    void commitSettledBannerMining(int x, int y, int z) {
        bannerBlocks.remove(new BlockPos(x, y, z));
        ctx.broadcaster().enqueueBroadcastFromTick(worldId, new BannerRemove(x, y, z));
    }

    void installPlayerBlockSettlements(
            com.gameexpert.block.service.PlayerBlockSettlementService service) {
        if (playerBlockSettlements != null) {
            throw new IllegalStateException("player block settlements already installed");
        }
        playerBlockSettlements = service;
    }

    com.gameexpert.block.service.PlayerBlockSettlementService playerBlockSettlements() {
        return playerBlockSettlements;
    }

    /**
     * [JUKEBOX] 주크박스 블록 엔티티 저장소를 설치하고 이 월드의 world_jukeboxes 행을 적재한다. 곡 시계가
     * 있는 행은 그 자리에서 곡을 이어 간다.
     */
    void installJukeboxPersistence(com.gameexpert.jukebox.service.JukeboxPersistenceService service) {
        jukeboxPersistence = service;
        if (service == null || worldId == null) return;
        List<com.gameexpert.jukebox.dto.JukeboxBlockData> stored = service.loadWorld(worldId);
        tickLoop.installJukeboxes(stored == null ? List.of() : stored);
    }

    com.gameexpert.jukebox.service.JukeboxPersistenceService jukeboxPersistence() {
        return jukeboxPersistence;
    }

    void installLecternPersistence(LecternPersistenceService service) {
        lecternPersistence = service;
        legacyBookLecterns.clear();
        if (service == null || worldId == null) return;
        // [BLOCK-SHAPES] has_book 비트 이전에 책을 올린 독서대는 책 행만 있고 블록 state 는 0 이다.
        // 책 행을 청크별로 한 번 기억해 두고, 그 청크가 상주할 때 비트를 한 번 세운다.
        List<com.gameexpert.lectern.dto.LecternBlockData> stored = service.loadWorld(worldId);
        if (stored == null) return;
        for (var lectern : stored) {
            legacyBookLecterns.computeIfAbsent(chunkKey(Math.floorDiv(lectern.x(), Blocks.CHUNK_X),
                    Math.floorDiv(lectern.z(), Blocks.CHUNK_Z)), ignored -> new ArrayList<>())
                    .add(new BlockPos(lectern.x(), lectern.y(), lectern.z()));
        }
        for (Long key : new ArrayList<>(legacyBookLecterns.keySet())) {
            restoreLegacyLecternBooks(key);
        }
    }

    /**
     * [BLOCK-SHAPES] 이 청크의 책 든 옛 독서대에 {@code LecternBlock.HAS_BOOK} 비트를 세운다
     * (책 모델 LecternRenderer 가 그 비트를 읽는다). 칸이 아직 상주하지 않으면 다음 활성화까지
     * 남겨 두고, 이미 비트가 있거나 독서대가 아니면 버린다. 새로 올리는 책은
     * {@code WorldTickLoop.applyLecternInteract} 가 곧바로 세운다.
     */
    private void restoreLegacyLecternBooks(long key) {
        List<BlockPos> pending = legacyBookLecterns.get(key);
        if (pending == null) return;
        pending.removeIf(pos -> {
            var resident = accessor.residentBlock(pos.x(), pos.y(), pos.z());
            if (!resident.isAvailable()) return false;
            if (resident.blockType() != Blocks.LECTERN) return true;
            int state = blockState(pos.x(), pos.y(), pos.z(), Blocks.LECTERN);
            if ((state & BlockModelShapes.LECTERN_HAS_BOOK) != 0) return true;
            if (terminalPhase != TerminalPhase.RUNNING) return false;
            setBlockState(pos.x(), pos.y(), pos.z(), Blocks.LECTERN,
                    state | BlockModelShapes.LECTERN_HAS_BOOK);
            tickBlockChanges.put(pos, (short) Blocks.LECTERN);
            return true;
        });
        if (pending.isEmpty()) legacyBookLecterns.remove(key);
    }

    /** [CONTAINER-MENUS] Attaches the durable crafter masks and restores them before the first tick. */
    void installCrafterPersistence(com.gameexpert.crafter.service.CrafterPersistenceService service) {
        crafterPersistence = service;
        if (service == null) return;
        for (var state : service.loadWorld(worldId)) {
            if (state.disabledSlots() == 0) continue;
            crafterDisabledSlots.put(new BlockPos(state.x(), state.y(), state.z()),
                    state.disabledSlots());
        }
    }

    CrafterSystem crafterSystem() {
        return crafterSystem;
    }

    /** {@code CrafterBlockEntity} disabled-slot mask of the cell (0 when none is disabled). */
    int crafterDisabledSlots(int x, int y, int z) {
        return crafterDisabledSlots.getOrDefault(new BlockPos(x, y, z), 0);
    }

    /** Replaces the cell's disabled-slot mask and queues it for the next crafter flush. */
    void setCrafterDisabledSlots(int x, int y, int z, int mask) {
        BlockPos pos = new BlockPos(x, y, z);
        int next = mask & com.gameexpert.engine.dispenser.CrafterRules.ALL_SLOTS;
        int previous = crafterDisabledSlots.getOrDefault(pos, 0);
        if (previous == next) return;
        if (next == 0) crafterDisabledSlots.remove(pos);
        else crafterDisabledSlots.put(pos, next);
        dirtyCrafterMasks.remove(pos);
        dirtyCrafterMasks.put(pos, next);
    }

    /**
     * Hands every changed crafter mask to the serial writer. A failed or refused write re-queues
     * the cells whose mask did not change again in the meantime.
     */
    void flushDirtyCrafters() {
        if (terminalPhase != TerminalPhase.RUNNING && terminalPhase != TerminalPhase.DISPOSED) return;
        if (dirtyCrafterMasks.isEmpty()) return;
        List<com.gameexpert.crafter.service.CrafterPersistenceService.CrafterState> batch =
                new ArrayList<>(dirtyCrafterMasks.size());
        for (Map.Entry<BlockPos, Integer> entry : dirtyCrafterMasks.entrySet()) {
            BlockPos pos = entry.getKey();
            batch.add(new com.gameexpert.crafter.service.CrafterPersistenceService.CrafterState(
                    pos.x(), pos.y(), pos.z(), entry.getValue()));
        }
        dirtyCrafterMasks.clear();
        com.gameexpert.crafter.service.CrafterPersistenceService service = crafterPersistence;
        if (service == null) return;
        Runnable requeue = () -> {
            for (var state : batch) {
                BlockPos pos = new BlockPos(state.x(), state.y(), state.z());
                if (!dirtyCrafterMasks.containsKey(pos)
                        && crafterDisabledSlots.getOrDefault(pos, 0) == state.disabledSlots()) {
                    dirtyCrafterMasks.put(pos, state.disabledSlots());
                }
            }
        };
        Runnable write = () -> {
            try {
                service.replaceAll(worldId, batch);
            } catch (RuntimeException failed) {
                log.warn("world {} crafter mask flush failed ({} cells)", worldId, batch.size(),
                        failed);
                enqueuePersistenceCompletion(requeue, requeue);
            }
        };
        PersistenceExecutor writer = ctx.persistenceExecutor();
        if (writer == null) {
            write.run();
            drainPersistenceCompletions();
        } else if (!writer.trySubmit(write)) {
            requeue.run();
        }
    }

    LecternPersistenceService lecternPersistence() { return lecternPersistence; }

    void installLecternMiningSettlements(LecternMiningSettlementService service) {
        if (lecternMiningSettlements != null) {
            throw new IllegalStateException("lectern mining settlements already installed");
        }
        lecternMiningSettlements = service;
    }

    boolean submitLecternMiningSettlement(
            com.gameexpert.lectern.dto.LecternMiningSettlementCommand command,
            Runnable committed, Runnable rejected) {
        if (lecternMiningSettlements == null || command == null
                || !worldId.equals(command.groundMutation().worldId())) return false;
        synchronized (this) {
            if (command.groundMutation().expectedGroundRevision() != groundRevision
                    || !beginGroundSettlement()) return false;
        }
        Runnable transaction = () -> {
            LecternMiningSettlementService.Outcome outcome;
            try {
                outcome = lecternMiningSettlements.settle(command);
            } catch (RuntimeException | Error failure) {
                log.warn("월드 {} 렉턴 채굴 정산 실패 ({},{},{}): {}", worldId,
                        command.expectedLectern().x(), command.expectedLectern().y(),
                        command.expectedLectern().z(), failure.toString());
                outcome = LecternMiningSettlementService.Outcome.STALE;
            }
            boolean accepted = outcome == LecternMiningSettlementService.Outcome.COMMITTED
                    || outcome == LecternMiningSettlementService.Outcome.IDEMPOTENT;
            enqueuePersistenceCompletion(() -> {
                completeGroundSettlement(
                        command.groundMutation().committedGroundRevision(), accepted);
                if (accepted) committed.run();
                else rejected.run();
            }, () -> completeGroundSettlement(
                    command.groundMutation().committedGroundRevision(), false));
        };
        PersistenceExecutor writer = ctx.persistenceExecutor();
        if (writer == null) {
            transaction.run();
            drainPersistenceCompletions();
            return true;
        }
        if (!writer.trySubmit(transaction)) {
            completeGroundSettlement(command.groundMutation().committedGroundRevision(), false);
            return false;
        }
        return true;
    }

    MobPersistenceService mobPersistence() { return mobPersistence; }

    /** Called by placement authority before consuming the exact originating banner stack. */
    void stageBannerPlacement(int x, int y, int z,
            List<ItemComponentData.BannerLayer> patterns) {
        if (terminalPhase != TerminalPhase.RUNNING) return;
        BlockPos pos = new BlockPos(x, y, z);
        stagedBannerPlacements.put(pos, new BannerBlockData(x, y, z, patterns));
    }

    /** Installs and publishes a banner aggregate that is already durable in the settlement DB tx. */
    void commitSettledBannerPlacement(int x, int y, int z,
            List<ItemComponentData.BannerLayer> patterns) {
        if (terminalPhase != TerminalPhase.RUNNING) return;
        BlockPos pos = new BlockPos(x, y, z);
        BannerBlockData banner = new BannerBlockData(x, y, z, patterns);
        stagedBannerPlacements.remove(pos);
        bannerBlocks.put(pos, banner);
        ctx.broadcaster().enqueueBroadcastFromTick(worldId,
                new BannerUpdate(toWireBanner(banner)));
    }

    /** Persist and publish all changed placed-banner entities; callers retain failed stages for retry. */
    void settleBannerBlockChanges(Map<BlockPos, Short> changes) {
        if (bannerPersistence == null || changes.isEmpty()
                || terminalPhase != TerminalPhase.RUNNING) return;
        for (Map.Entry<BlockPos, Short> entry : changes.entrySet()) {
            if (!ownerTurnMayContinue()) return;
            BlockPos pos = entry.getKey();
            int blockType = Short.toUnsignedInt(entry.getValue());
            BannerBlockData staged = stagedBannerPlacements.get(pos);
            BannerBlockData existing = bannerBlocks.get(pos);
            boolean placement = Blocks.isBanner(blockType) && staged != null;
            boolean removal = !Blocks.isBanner(blockType) && existing != null;
            if ((!placement && !removal)) continue;
            short blockState = (short) blockStates.get(pos.x(), pos.y(), pos.z(), blockType);
            persistBanner(pos, placement ? staged : existing, placement,
                    (short) blockType, blockState, false);
        }
    }

    private void persistBanner(BlockPos pos, BannerBlockData banner, boolean placement,
            short blockType, short blockState, boolean disposalRetry) {
        if (bannerPersistence == null || banner == null) return;
        TerminalPhase phase = terminalPhase;
        if (phase != TerminalPhase.RUNNING
                && !(disposalRetry && phase == TerminalPhase.DISPOSED)) return;
        PendingBannerPersistence pending = new PendingBannerPersistence(
                banner, placement, blockType, blockState);
        if (!bannerPersistenceInFlight.add(pos)) {
            pendingBannerPersistence.put(pos, pending);
            return;
        }
        Runnable save = () -> {
            try {
                if (placement) {
                    bannerPersistence.place(worldId, pos.x(), pos.y(), pos.z(),
                            blockType, blockState, banner.patterns());
                } else {
                    bannerPersistence.remove(worldId, pos.x(), pos.y(), pos.z(),
                            blockType, blockState);
                }
                enqueuePersistenceCompletion(() -> completeBannerPersistence(pos, pending),
                        () -> completeBannerPersistenceGenerically(pos));
            } catch (RuntimeException | Error failed) {
                enqueuePersistenceCompletion(
                        () -> failBannerPersistence(pos, pending),
                        () -> failBannerPersistenceGenerically(pos, pending));
                throw failed;
            }
        };
        PersistenceExecutor writer = ctx.persistenceExecutor();
        if (writer == null) save.run();
        else if (!writer.trySubmit(save)) {
            failBannerPersistenceGenerically(pos, pending);
        }
    }

    private void completeBannerPersistence(BlockPos pos, PendingBannerPersistence completed) {
        bannerPersistenceInFlight.remove(pos);
        PendingBannerPersistence pending = pendingBannerPersistence.remove(pos);
        if (terminalPhase != TerminalPhase.RUNNING && terminalPhase != TerminalPhase.DRAINING) {
            if (pending != null) pendingBannerPersistence.put(pos, pending);
            return;
        }
        if (completed.placement()) {
            stagedBannerPlacements.remove(pos, completed.banner());
            bannerBlocks.put(pos, completed.banner());
            if (ctx.broadcaster() != null) {
                ctx.broadcaster().enqueueBroadcastFromTick(worldId,
                        new BannerUpdate(toWireBanner(completed.banner())));
            }
        } else {
            bannerBlocks.remove(pos);
            if (ctx.broadcaster() != null) {
                ctx.broadcaster().enqueueBroadcastFromTick(worldId,
                        new BannerRemove(pos.x(), pos.y(), pos.z()));
            }
        }
        if (pending != null) {
            if (terminalPhase == TerminalPhase.RUNNING) {
                persistBanner(pos, pending.banner(), pending.placement(), pending.blockType(),
                        pending.blockState(), false);
            } else {
                pendingBannerPersistence.put(pos, pending);
            }
        }
    }

    private void completeBannerPersistenceGenerically(BlockPos pos) {
        bannerPersistenceInFlight.remove(pos);
    }

    private void failBannerPersistence(BlockPos pos, PendingBannerPersistence failed) {
        bannerPersistenceInFlight.remove(pos);
        pendingBannerPersistence.putIfAbsent(pos, failed);
    }

    private void failBannerPersistenceGenerically(BlockPos pos, PendingBannerPersistence failed) {
        failBannerPersistence(pos, failed);
        if (terminalPhase == TerminalPhase.DISPOSED) disposalPersistenceFailed = true;
    }

    private void retryPendingBannerPersistence(boolean disposalRetry) {
        for (Map.Entry<BlockPos, PendingBannerPersistence> entry
                : List.copyOf(pendingBannerPersistence.entrySet())) {
            if (!disposalRetry && !ownerTurnMayContinue()) return;
            if (bannerPersistenceInFlight.contains(entry.getKey())
                    || !pendingBannerPersistence.remove(entry.getKey(), entry.getValue())) continue;
            PendingBannerPersistence pending = entry.getValue();
            persistBanner(entry.getKey(), pending.banner(), pending.placement(),
                    pending.blockType(), pending.blockState(), disposalRetry);
        }
    }

    private boolean bannerPersistencePending() {
        return !bannerPersistenceInFlight.isEmpty() || !pendingBannerPersistence.isEmpty();
    }

    private List<BannerBlockState> bannerSnapshot(int chunkX, int chunkZ) {
        return bannerBlocks.values().stream()
                .filter(banner -> Math.floorDiv(banner.x(), Blocks.CHUNK_X) == chunkX
                        && Math.floorDiv(banner.z(), Blocks.CHUNK_Z) == chunkZ)
                .sorted(java.util.Comparator.comparingInt(BannerBlockData::x)
                        .thenComparingInt(BannerBlockData::y)
                        .thenComparingInt(BannerBlockData::z))
                .map(WorldRuntime::toWireBanner)
                .toList();
    }

    private List<SignBlockState> signSnapshot(int chunkX, int chunkZ) {
        return signBlocks.values().stream()
                .filter(sign -> Math.floorDiv(sign.x(), Blocks.CHUNK_X) == chunkX
                        && Math.floorDiv(sign.z(), Blocks.CHUNK_Z) == chunkZ)
                .sorted(java.util.Comparator.comparingInt(SignBlockData::x)
                        .thenComparingInt(SignBlockData::y).thenComparingInt(SignBlockData::z))
                .map(WorldRuntime::toWireSign).toList();
    }

    static SignBlockState toWireSign(SignBlockData sign) {
        return new SignBlockState(sign.x(), sign.y(), sign.z(), sign.lines());
    }

    void settleSignBlockChanges(Map<BlockPos, Short> changes) {
        if (signPersistence == null || changes.isEmpty()
                || terminalPhase != TerminalPhase.RUNNING) return;
        for (Map.Entry<BlockPos, Short> entry : changes.entrySet()) {
            if (!ownerTurnMayContinue()) return;
            BlockPos pos = entry.getKey();
            int id = Short.toUnsignedInt(entry.getValue());
            boolean isSign = id == Blocks.POPLAR_SIGN || id == Blocks.POPLAR_HANGING_SIGN;
            SignBlockData staged = stagedSignPlacements.get(pos);
            SignBlockData existing = signBlocks.get(pos);
            if (isSign && staged != null) persistSign(pos, staged, true);
            else if (!isSign && (existing != null || staged != null)) {
                persistSign(pos, existing != null ? existing : staged, false);
            }
        }
    }

    private void persistSign(BlockPos pos, SignBlockData sign, boolean placement) {
        persistSign(pos, sign, placement, false);
    }

    private void persistSign(BlockPos pos, SignBlockData sign, boolean placement,
            boolean disposalRetry) {
        if (signPersistence == null) return;
        TerminalPhase phase = terminalPhase;
        if (phase != TerminalPhase.RUNNING
                && !(disposalRetry && phase == TerminalPhase.DISPOSED)) return;
        if (!signPersistenceInFlight.add(pos)) {
            pendingSignPersistence.put(pos, new PendingSignPersistence(sign, placement));
            return;
        }
        int blockType = accessor.getBlock(pos.x(), pos.y(), pos.z());
        short state = (short) blockStates.get(pos.x(), pos.y(), pos.z(), blockType);
        Runnable save = () -> {
            try {
                if (placement) signPersistence.place(worldId, pos.x(), pos.y(), pos.z(),
                        (short) blockType, state, sign.lines());
                else signPersistence.remove(worldId, pos.x(), pos.y(), pos.z(),
                        (short) blockType, state);
                enqueuePersistenceCompletion(
                        () -> completeSignPersistence(pos, sign, placement),
                        () -> completeSignPersistenceGenerically(pos));
            } catch (RuntimeException | Error failed) {
                PendingSignPersistence failedPersistence = new PendingSignPersistence(sign, placement);
                enqueuePersistenceCompletion(() -> failSignPersistence(pos, failedPersistence),
                        () -> failSignPersistenceGenerically(pos, failedPersistence));
                throw failed;
            }
        };
        PersistenceExecutor writer = ctx.persistenceExecutor();
        if (writer == null) save.run();
        else if (!writer.trySubmit(save)) {
            failSignPersistenceGenerically(pos, new PendingSignPersistence(sign, placement));
        }
    }

    private void completeSignPersistence(BlockPos pos, SignBlockData sign, boolean placement) {
        signPersistenceInFlight.remove(pos);
        PendingSignPersistence pending = pendingSignPersistence.remove(pos);
        if (terminalPhase != TerminalPhase.RUNNING && terminalPhase != TerminalPhase.DRAINING) {
            if (pending != null) pendingSignPersistence.put(pos, pending);
            return;
        }
        if (placement) {
            signBlocks.put(pos, sign);
            if (sign.equals(stagedSignPlacements.get(pos))) stagedSignPlacements.remove(pos);
            if (pending == null && ctx.broadcaster() != null) {
                ctx.broadcaster().enqueueBroadcastFromTick(worldId,
                        new SignUpdate(toWireSign(sign)));
            }
        } else {
            signBlocks.remove(pos);
            if (pending == null && ctx.broadcaster() != null) {
                ctx.broadcaster().enqueueBroadcastFromTick(worldId,
                        new SignRemove(pos.x(), pos.y(), pos.z()));
            }
        }
        if (pending != null) {
            if (terminalPhase == TerminalPhase.RUNNING) {
                persistSign(pos, pending.sign(), pending.placement(), false);
            } else {
                pendingSignPersistence.put(pos, pending);
            }
        }
    }

    private void completeSignPersistenceGenerically(BlockPos pos) {
        signPersistenceInFlight.remove(pos);
    }

    private void failSignPersistence(BlockPos pos, PendingSignPersistence failed) {
        signPersistenceInFlight.remove(pos);
        pendingSignPersistence.putIfAbsent(pos, failed);
    }

    private void failSignPersistenceGenerically(BlockPos pos, PendingSignPersistence failed) {
        failSignPersistence(pos, failed);
        if (terminalPhase == TerminalPhase.DISPOSED) disposalPersistenceFailed = true;
    }

    private boolean signPersistencePending() {
        return !signPersistenceInFlight.isEmpty() || !pendingSignPersistence.isEmpty();
    }

    private record PendingSignPersistence(SignBlockData sign, boolean placement) {}

    private record PendingBannerPersistence(BannerBlockData banner, boolean placement,
            short blockType, short blockState) {}

    private static BannerBlockState toWireBanner(BannerBlockData banner) {
        return new BannerBlockState(banner.x(), banner.y(), banner.z(),
                banner.patterns().stream()
                        .map(layer -> new BannerPatternLayer(
                                layer.pattern().wireName(), layer.color()))
                        .toList());
    }

    private void submitProjectileCheckpoint(long revision,
            List<com.gameexpert.projectile.dto.ProjectilePersistenceSnapshot> snapshot) {
        if (terminalPhase != TerminalPhase.RUNNING && terminalPhase != TerminalPhase.DISPOSED) return;
        var items = itemSystem.persistenceSnapshot();
        var xpOrbs = xpOrbSystem.persistenceSnapshot();
        validateGroundEntitySnapshotIdentities(items, xpOrbs);
        PersistenceExecutor writer = ctx.persistenceExecutor();
        if (writer == null) {
            long expectedGroundRevision = groundRevision;
            long committedGroundRevision = expectedGroundRevision + 1;
            GroundMutationOutcome outcome = projectilePersistence.replaceWorldWithGround(worldId,
                    expectedGroundRevision, committedGroundRevision, snapshot, items, xpOrbs);
            if (outcome == GroundMutationOutcome.COMMITTED) {
                groundRevision = committedGroundRevision;
                projectilePersistedRevision = revision;
            }
            return;
        }
        long expectedGroundRevision = groundRevision;
        long committedGroundRevision = expectedGroundRevision + 1;
        projectileSaveInFlight = true;
        if (!writer.trySubmit(() -> {
            GroundMutationOutcome outcome = GroundMutationOutcome.STALE;
            try {
                outcome = projectilePersistence.replaceWorldWithGround(worldId,
                        expectedGroundRevision, committedGroundRevision, snapshot, items, xpOrbs);
            } finally {
                completeProjectileCheckpoint(revision, committedGroundRevision,
                        outcome == GroundMutationOutcome.COMMITTED);
            }
        })) {
            projectileSaveInFlight = false;
        }
    }

    private synchronized void completeProjectileCheckpoint(
            long revision, long committedGroundRevision, boolean success) {
        projectileSaveInFlight = false;
        if (success) {
            groundRevision = committedGroundRevision;
            projectilePersistedRevision = Math.max(projectilePersistedRevision, revision);
        }
    }

    private synchronized void forceProjectileCheckpointForDisposal() {
        if (projectilePersistence == null) return;
        if (!disposalProjectileCheckpointPrepared) {
            disposalProjectileCheckpointPrepared = true;
            projectileDirtyRevision++;
        }
        // Ground and projectile checkpoints both CAS the same durable ground revision. They must
        // enter that lane serially or the second snapshot is guaranteed STALE.
        if (groundPersistencePending()) return;
        List<com.gameexpert.projectile.dto.ProjectilePersistenceSnapshot> snapshot =
                mobSystem.projectilePersistenceSnapshot();
        projectileLedgerMayBeNonEmpty = !snapshot.isEmpty();
        if (!projectileSaveInFlight && projectileDirtyRevision > projectilePersistedRevision) {
            submitProjectileCheckpoint(projectileDirtyRevision, snapshot);
        }
    }

    private synchronized boolean projectilePersistencePending() {
        return projectileSaveInFlight || projectileDirtyRevision > projectilePersistedRevision;
    }

    private void observeGroundChanges(boolean periodicCheckpoint) {
        long itemRevision = itemSystem.persistenceRevision();
        long xpRevision = xpOrbSystem.persistenceRevision();
        if (itemRevision != observedGroundItemRevision || xpRevision != observedGroundXpRevision) {
            observedGroundItemRevision = itemRevision;
            observedGroundXpRevision = xpRevision;
            groundDirtyRevision++;
        }
        // Moving entities and their age do not change identity/count revisions. Capture those exact
        // physical fields at most once per second, while semantic changes bypass this cadence.
        if (periodicCheckpoint) groundDirtyRevision++;
    }

    private void submitGroundCheckpoint() {
        if (terminalPhase != TerminalPhase.RUNNING && terminalPhase != TerminalPhase.DISPOSED) return;
        if (groundSaveInFlight || groundSettlementInFlight || projectileSaveInFlight
                || hasPendingGroundSpawns()
                || groundDirtyRevision <= groundPersistedRevision) return;
        PersistenceExecutor persistence = ctx.persistenceExecutor();
        if (persistence == null) {
            persistGroundCheckpointBlocking();
            return;
        }
        long checkpointRevision = groundDirtyRevision;
        long itemRevision = itemSystem.persistenceRevision();
        long xpRevision = xpOrbSystem.persistenceRevision();
        List<com.gameexpert.ground.dto.GroundItemSnapshot> items =
                itemSystem.persistenceSnapshot();
        List<com.gameexpert.ground.dto.GroundXpOrbSnapshot> xpOrbs =
                xpOrbSystem.persistenceSnapshot();
        validateGroundEntitySnapshotIdentities(items, xpOrbs);
        long expectedGroundRevision = groundRevision;
        long committedGroundRevision = expectedGroundRevision + 1;
        groundSaveInFlight = true;
        if (!persistence.trySubmit(() -> {
            GroundMutationOutcome outcome = GroundMutationOutcome.STALE;
            try {
                outcome = groundEntityPersistence.replaceWorld(worldId,
                        expectedGroundRevision, committedGroundRevision, items, xpOrbs);
            } finally {
                completeGroundCheckpoint(checkpointRevision, itemRevision, xpRevision,
                        committedGroundRevision, outcome == GroundMutationOutcome.COMMITTED);
            }
        })) {
            groundSaveInFlight = false;
        }
    }

    private synchronized void completeGroundCheckpoint(long checkpointRevision,
            long itemRevision, long xpRevision, long committedGroundRevision, boolean success) {
        groundSaveInFlight = false;
        if (!success) return;
        groundRevision = committedGroundRevision;
        groundPersistedRevision = Math.max(groundPersistedRevision, checkpointRevision);
        if (terminalPhase == TerminalPhase.RUNNING || terminalPhase == TerminalPhase.DRAINING) {
            itemSystem.acknowledgePersistence(itemRevision);
            xpOrbSystem.acknowledgePersistence(xpRevision);
        }
    }

    private synchronized void forceGroundCheckpointForDisposal() {
        if (groundEntityPersistence == null) return;
        if (!disposalGroundCheckpointPrepared) {
            disposalGroundCheckpointPrepared = true;
            observeGroundChanges(true);
        }
        submitGroundCheckpoint();
    }

    private synchronized void persistGroundCheckpointBlocking() {
        if (groundEntityPersistence == null || groundSettlementInFlight || projectileSaveInFlight
                || groundDirtyRevision <= groundPersistedRevision) return;
        long checkpointRevision = groundDirtyRevision;
        long itemRevision = itemSystem.persistenceRevision();
        long xpRevision = xpOrbSystem.persistenceRevision();
        List<com.gameexpert.ground.dto.GroundItemSnapshot> items =
                itemSystem.persistenceSnapshot();
        List<com.gameexpert.ground.dto.GroundXpOrbSnapshot> xpOrbs =
                xpOrbSystem.persistenceSnapshot();
        validateGroundEntitySnapshotIdentities(items, xpOrbs);
        long expectedGroundRevision = groundRevision;
        long committedGroundRevision = expectedGroundRevision + 1;
        GroundMutationOutcome outcome = groundEntityPersistence.replaceWorld(worldId,
                expectedGroundRevision, committedGroundRevision,
                items, xpOrbs);
        if (outcome != GroundMutationOutcome.COMMITTED) return;
        groundRevision = committedGroundRevision;
        groundPersistedRevision = checkpointRevision;
        if (terminalPhase == TerminalPhase.RUNNING || terminalPhase == TerminalPhase.DRAINING) {
            itemSystem.acknowledgePersistence(itemRevision);
            xpOrbSystem.acknowledgePersistence(xpRevision);
        }
    }

    private synchronized boolean groundPersistencePending() {
        return groundSaveInFlight || groundSettlementInFlight
                || groundDirtyRevision > groundPersistedRevision;
    }

    /** [SURV-X] 인챈트 테이블 저장소. */
    EnchantingStorage enchantingStorage() {
        return enchantingStorage;
    }

    /** Hydrates the complete aggregate before the runtime owner can start or become visible. */
    void installEnchantingPersistence(EnchantingPersistenceService persistence) {
        if (started || disposed || terminalPhase != TerminalPhase.RUNNING
                || enchantingPersistence != null) {
            throw new IllegalStateException(
                    "enchanting persistence is already attached or runtime is not attachable");
        }
        enchantingPersistence = java.util.Objects.requireNonNull(
                persistence, "enchanting persistence");
        try {
            persistence.loadWorld(worldId, enchantingStorage);
        } catch (RuntimeException | Error failure) {
            enchantingPersistence = null;
            throw failure;
        }
    }

    /** [SURV-X] 인챈트 성공 뒤 seed 재추첨에 쓰는 월드 난수. */
    int nextEnchantSeed() {
        return enchantSeedRandom.nextInt();
    }

    ItemEntitySystem itemSystem() {
        return itemSystem;
    }

    WorldMapData mapState(int mapId) {
        return worldMaps.get(mapId);
    }

    byte[] mapWorkingColors(int mapId) {
        return worldMaps.workingColors(mapId);
    }

    boolean applyMapSample(int mapId, int x, int z, int width, int height, byte[] colors) {
        if (terminalPhase != TerminalPhase.RUNNING) return false;
        return worldMaps.applySample(mapId, x, z, width, height, colors);
    }

    WorldMapRuntime.PendingPatch prepareMapPatch(int mapId) {
        return terminalPhase == TerminalPhase.RUNNING
                ? worldMaps.prepareDirtyPatch(mapId) : null;
    }

    com.gameexpert.map.dto.WorldMapData mapData(int mapId) {
        return worldMaps.get(mapId);
    }

    com.gameexpert.map.dto.WorldMapData prepareDerivedMap(int sourceMapId,
            com.gameexpert.engine.inventory.CartographyRules.Operation operation) {
        return terminalPhase == TerminalPhase.RUNNING
                ? worldMaps.prepareDerived(sourceMapId, operation) : null;
    }

    boolean mapNeedsPersistence(int mapId) {
        return worldMaps.needsPersistence(mapId);
    }

    /** 래스터 변경은 전체 스냅샷 DB 커밋 뒤에만 revision을 승격하고 패치를 송신합니다. */
    boolean persistMapPatch(WorldMapRuntime.PendingPatch patch,
            Consumer<WorldMapRuntime.PendingPatch> onDurable, Runnable onFailure) {
        PersistenceExecutor persistence = ctx.persistenceExecutor();
        if (worldMapPersistence == null || persistence == null
                || terminalPhase != TerminalPhase.RUNNING) {
            worldMaps.rejectPatch(patch);
            return false;
        }
        WorldMapData candidate = patch.candidate();
        boolean submitted = persistence.trySubmit(() -> {
            try {
                WorldMapData durable = worldMapPersistence.replaceColors(
                        candidate.getWorldId(), candidate.getMapId(),
                        candidate.getColors(), candidate.getRevision());
                enqueueMapPersistenceCompletion(
                        () -> {
                            worldMaps.acceptPatch(patch, durable);
                            // Map acceptance is bookkeeping for a terminal disposal drain;
                            // user-visible publication belongs only to a live owner turn.
                            if (terminalPhase == TerminalPhase.RUNNING
                                    || terminalPhase == TerminalPhase.DRAINING) {
                                onDurable.accept(patch);
                            }
                        },
                        () -> worldMaps.rejectPatch(patch));
            } catch (RuntimeException | Error failure) {
                enqueueMapPersistenceCompletion(
                        () -> {
                            worldMaps.rejectPatch(patch);
                            if (terminalPhase == TerminalPhase.RUNNING
                                    || terminalPhase == TerminalPhase.DRAINING) {
                                onFailure.run();
                            }
                        },
                        () -> {
                            worldMaps.rejectPatch(patch);
                            markDisposalPersistenceFailed(failure);
                        });
                throw failure;
            }
        });
        if (!submitted) {
            worldMaps.rejectPatch(patch);
            onFailure.run();
        }
        return submitted;
    }

    BoatSystem boatSystem() {
        return boatSystem;
    }

    PlacedEntitySystem placedEntities() {
        return placedEntities;
    }

    /** [CONTAINER-MENUS] Restores stored armor stands and minecarts before the first tick. */
    void installPlacedEntityPersistence(
            com.gameexpert.placed.service.PlacedEntityPersistenceService service) {
        placedEntities.installPersistence(service);
    }

    CushionSystem cushionSystem() {
        return cushionSystem;
    }

    void installCushionPersistence(CushionPersistenceService service) {
        cushionSystem.installPersistence(service);
    }

    /**
     * 저장된 보트를 이 런타임에 되살린다. 첫 틱과 첫 welcome 스냅샷보다 먼저 불려야 재입장한
     * 플레이어가 같은 세션과 같은 목록을 본다.
     */
    void installBoatPersistence(com.gameexpert.boat.service.BoatPersistenceService service) {
        boatSystem.installPersistence(service);
    }

    WeatherSystem weatherSystem() {
        return weatherSystem;
    }

    /**
     * 낙뢰 후보 좌표. 바닐라는 로드된 청크에서만 벼락이 치므로 접속 중인 플레이어만 후보가 된다.
     * 닉네임 정렬로 고정 순서를 만들어 같은 시드·같은 접속 집합이면 같은 낙뢰 좌표가 나온다.
     */
    private List<double[]> lightningTargets() {
        return players.values().stream()
                .sorted(java.util.Comparator.comparing(PlayerTickState::nickname))
                .map(player -> new double[] { player.x(), player.z() })
                .toList();
    }

    /**
     * 확정 타격점의 게임플레이 효과. 몹 시스템 큐로 넘겨 몹 틱 시작에서 한 번에 적용한다.
     * {@code beginTick} 은 몹 시스템 생성 전에 돌 수 없으므로 이 시점의 필드는 항상 준비돼 있다.
     */
    private void onLightningStrike(LightningStrike strike) {
        if (!ownerTurnMayContinue()) return;
        if (mobSystem != null) mobSystem.enqueueNaturalLightningStrike(strike);
        // 바닐라 LightningBolt#tick 은 life==2 에서 발화/피해(몹 큐)와 구리 환원을 함께 한다.
        // 환원은 블록 편집이라 몹 큐가 아니라 여기서 바로 단일 편집 경계로 나간다.
        LightningCopperRules.clearCopperOnLightningStrike(
                lightningCopperWorld, strike,
                LightningCopperRules.deterministicRolls(seed, strike));
    }

    /**
     * 구리 환원이 쓰는 블록 경계. 읽기는 상주 블록, 쓰기는 다른 모든 블록 편집과 같은 깔때기라
     * diff 영속·클라 방송·오버레이 revision 이 자동으로 따라온다.
     */
    private final LightningCopperRules.World lightningCopperWorld =
            new LightningCopperRules.World() {
                @Override
                public int blockAt(int x, int y, int z) {
                    if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return Blocks.AIR;
                    return WorldTickLoop.residentBlockType(accessor, x, y, z);
                }

                @Override
                public int stateAt(int x, int y, int z) {
                    if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return 0;
                    return blockStates.get(x, y, z,
                            WorldTickLoop.residentBlockType(accessor, x, y, z));
                }

                @Override
                public void setBlock(int x, int y, int z, int blockType, int blockState) {
                    if (!ownerTurnMayContinue()) return;
                    // state 를 함께 넘기는 5-arg 깔때기를 쓴다. 4-arg 는 state 를 0 으로 지워
                    // 이중 반 블록이 무너지고 문 윗칸이 사라진다.
                    setIndexedOverlay(x, y, z, blockType, blockState);
                    tickBlockChanges.put(new BlockPos(x, y, z), (short) blockType);
                }
            };

    /**
     * 후보 타격점을 반경 안에서 가장 가까운 피뢰침 위로 옮긴다.
     * 바닐라 {@code ServerLevel#findLightningTargetAround} → {@code findLightningRod} 와 같은 규칙:
     * 피뢰침이 그 컬럼의 최상단일 때만 유인하고, 타격점은 피뢰침 <b>한 칸 위</b>다.
     *
     * <p>훅은 난수를 소비하지 않으므로 유인을 붙여도 양 권위의 난수열은 갈라지지 않는다.
     */
    private LightningStrike redirectToLightningRod(LightningStrike candidate) {
        int[] rod = lightningRods.nearest(
                candidate.blockX(), candidate.blockY(), candidate.blockZ(),
                (x, z) -> mobSystem.worldSurfaceHeight(x, z));
        return rod == null ? candidate : LightningStrike.atBlock(rod[0], rod[1] + 1, rod[2]);
    }

    /** 재활성화된 청크의 피뢰침 원장을 그 청크의 persisted diff 로 다시 세운다. */
    private void recoverLightningRods(int chunkX, int chunkZ, List<WorldBlockDiff> persistedDiffs) {
        List<int[]> rods = new ArrayList<>();
        for (WorldBlockDiff diff : persistedDiffs) {
            if (!CopperAgeRules.isLightningRod(diff.getBlockType())) continue;
            rods.add(new int[] { diff.getX(), diff.getY(), diff.getZ(), Blocks.LIGHTNING_ROD });
        }
        lightningRods.recover(chunkX, chunkZ, rods);
    }

    /**
     * 낙뢰가 꽂힐 컬럼의 지면 높이. 살아 있는 월드의 MOTION_BLOCKING_NO_LEAVES 하이트맵 —
     * 철 골렘 배치가 쓰는 바로 그것 — 을 쓰므로 나무·눈층·물·플레이어 건축물 위에 친다.
     *
     * <p>아직 스냅샷이 없는 컬럼은 판정할 지형이 없으므로 생성 시점의 지형 높이로 폴백한다.
     * 그러지 않으면 하이트맵이 로드 실패를 MAX_Y 로 돌려주어 벼락이 하늘에 뜬다. 낙뢰 후보는
     * 접속자 ±128블록이라 실제로는 대부분 활성 청크에 떨어진다.
     */
    private int lightningStrikeHeight(int x, int z) {
        int chunkX = Math.floorDiv(x, Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv(z, Blocks.CHUNK_Z);
        if (accessor.snapshotSource(chunkX, chunkZ) == null) {
            return ChunkGenerator.surfaceHeight(seed, x, z);
        }
        return mobSystem.motionBlockingNoLeavesHeight(x, z);
    }

    Map<BlockPos, Short> tickBlockChanges() {
        return tickBlockChanges;
    }

    /** broadcastBlocks가 최종 셀과 함께 소비하는 저널 출처입니다. */
    String tickBlockMutationKey(BlockPos pos) {
        return tickBlockMutationKeys.remove(pos);
    }

    List<BlockPos> drainEnvironmentSupportChanges() {
        if (environmentSupportChanges.isEmpty()) return List.of();
        List<BlockPos> changes = new ArrayList<>(environmentSupportChanges);
        environmentSupportChanges.clear();
        return changes;
    }

    long tickNo() {
        return tickNo;
    }

    void installAnimalBlockTickPersistence(AnimalBlockTickPersistenceService service) {
        if (service == null) return;
        if (started) throw new IllegalStateException("animal block ticks must bind before start");
        animalBlockTickStore = service.storeFor(worldId);
        runtimeFallingSpeleothems.install(animalBlockTickStore.runtimeFallingPersistence());
        List<AnimalBlockTickPersistenceService.ScheduledTick> scheduledTicks =
                animalBlockTickStore.repairAndLoad(seed, clock.gameTimeMcTicks());
        tickLoop.hydrateAnimalBlockSchedules(scheduledTicks);
        // [CONTAINER-MENUS] The dispenser/crafter block ticks share the durable block-tick table.
        dispenserSystem.hydrate(scheduledTicks);
        crafterSystem.hydrate(scheduledTicks);
    }

    void installAnimalSettlementPersistence(AnimalSettlementPersistenceService service) {
        if (started) throw new IllegalStateException("animal settlements must bind before start");
        this.animalSettlements = service;
    }

    void installFrogColonyPersistence(FrogColonyPersistenceService service) {
        if (service == null) {
            throw new IllegalStateException("frog colony persistence is required");
        }
        if (started) throw new IllegalStateException("frog colony persistence must bind before start");
        mobSystem.installFrogColonyPersistence(service);
    }

    void installTrialPersistence(TrialStorage service) {
        if (service == null) return;
        if (started) throw new IllegalStateException("trial persistence must bind before start");
        if (trialPersistence != null) {
            throw new IllegalStateException("trial persistence is already attached");
        }
        mobSystem.trialSpawners().restorePersistentSnapshots(
                seed, service.hydrateWorld(worldId));
        // [TRIAL-GAP] 금고별 stateUpdatingResumesAt(바닐라 VaultServerData CODEC 이 저장하는 칸).
        mobSystem.trialSpawners().restoreVaultTimers(service.hydrateVaultTimers(worldId));
        // [TRIAL-GAP] 금고의 보상 받은 집합(영수증 GRANTED)과 아직 배출하지 않은 outbox 행.
        if (mobPersistence != null) {
            var vaults = mobPersistence.loadTrialVaultState(worldId);
            mobSystem.trialSpawners().restoreVaults(vaults.rewarded(), vaults.items());
        }
        trialPersistence = service;
    }

    TrialStorage trialPersistence() { return trialPersistence; }

    /**
     * Settles one Trial Key through the shared ground CAS while holding the exact Trial reward
     * row lock. Both receipts and the stable ground entity commit in one database transaction.
     */
    void settleTrialReward(com.gameexpert.engine.trial.TrialSpawnerRuntime.Victory victory) {
        if (terminalPhase != TerminalPhase.RUNNING || victory == null
                || victory.rewardEntityId() <= 0 || trialPersistence == null
                || groundMutationSettlements == null) return;
        long expectedGroundRevision = groundRevision();
        long committedGroundRevision = Math.addExact(expectedGroundRevision, 1);
        long mutationId = stableGroundMutationId(victory.rewardEntityId(), 4);
        // [TRIAL] 바닐라 ejectReward: 스포너 윗면 중심에서 1.2 위(Vec3.atBottomCenterOf(pos)
        // .relative(UP, 1.2)). 배출 표가 고른 아이템·개수 그대로다.
        var ground = itemSystem.settlementDropSnapshot(victory.rewardEntityId(),
                victory.itemType(), victory.count(), victory.x() + 0.5, victory.y() + 1.2,
                victory.z() + 0.5);
        var command = new com.gameexpert.ground.dto.GroundMutationCommand(
                mutationId,
                com.gameexpert.ground.dto.GroundMutationCommand.Kind.BLOCK_DROP,
                worldId, expectedGroundRevision, committedGroundRevision,
                null, null, List.of(ground), List.of(), List.of(), List.of());
        if (!beginGroundSettlement()) return;
        Runnable settle = () -> {
            var groundOutcome = new java.util.concurrent.atomic.AtomicReference<
                    com.gameexpert.ground.service.GroundMutationOutcome>();
            try {
                var trialOutcome = trialPersistence.settleReward(worldId, victory.trialId(),
                        victory.rewardIdentity(), () -> {
                            var outcome = groundMutationSettlements.settle(command);
                            groundOutcome.set(outcome);
                            if (outcome == com.gameexpert.ground.service.GroundMutationOutcome.STALE) {
                                throw new TrialRewardRetryException();
                            }
                        });
                boolean accepted = trialOutcome
                        != TrialStorage.SettlementOutcome.REJECTED;
                boolean advanced = groundOutcome.get()
                        == com.gameexpert.ground.service.GroundMutationOutcome.COMMITTED;
                enqueuePersistenceCompletion(() -> {
                    completeGroundSettlement(committedGroundRevision, advanced);
                    if (!accepted || terminalPhase != TerminalPhase.RUNNING) return;
                    itemSystem.commitSettlementDrop(ground);
                    mobSystem.trialSpawners().acknowledgeVictory(
                            victory.trialId(), victory.rewardIdentity());
                }, () -> completeGroundSettlement(committedGroundRevision, false));
            } catch (TrialRewardRetryException retry) {
                enqueuePersistenceCompletion(
                        () -> completeGroundSettlement(expectedGroundRevision, false),
                        () -> completeGroundSettlement(expectedGroundRevision, false));
            } catch (RuntimeException | Error failure) {
                enqueuePersistenceCompletion(
                        () -> completeGroundSettlement(expectedGroundRevision, false),
                        () -> completeGroundSettlement(expectedGroundRevision, false));
            }
        };
        PersistenceExecutor writer = ctx.persistenceExecutor();
        if (writer == null) {
            settle.run();
            drainPersistenceCompletions();
        } else if (!writer.trySubmit(settle)) {
            completeGroundSettlement(expectedGroundRevision, false);
        }
    }

    /**
     * [TRIAL-GAP] 금고 배출 한 번(바닐라 {@code VaultState.ejectResultItem}: 윗면 중심 1.2 위에서
     * {@code DefaultDispenseItemBehavior.spawnItem}). 배출 outbox 행 잠금 · 지면 CAS 가 한 트랜잭션이다.
     */
    void settleVaultEjection(com.gameexpert.engine.trial.TrialSpawnerRuntime.VaultEjection ejection) {
        if (terminalPhase != TerminalPhase.RUNNING || ejection == null
                || ejection.entityId() <= 0 || mobPersistence == null
                || groundMutationSettlements == null) return;
        long expectedGroundRevision = groundRevision();
        long committedGroundRevision = Math.addExact(expectedGroundRevision, 1);
        long mutationId = stableGroundMutationId(ejection.entityId(), 4);
        var stack = ejection.stack();
        PlayerInventory.StackSnapshot item = stack.itemType() == PlayerInventory.OMINOUS_BOTTLE
                ? PlayerInventory.ominousBottleStack(stack.count(),
                        Math.max(0, stack.ominousAmplifier()))
                : new PlayerInventory.StackSnapshot(stack.itemType(), stack.count(),
                        stack.durability(), stack.enchantments(), 0, 0, null,
                        stack.itemComponentData());
        var ground = itemSystem.settlementDropSnapshot(ejection.entityId(), item,
                ejection.x() + 0.5, ejection.y() + 1.2, ejection.z() + 0.5);
        var command = new com.gameexpert.ground.dto.GroundMutationCommand(
                mutationId,
                com.gameexpert.ground.dto.GroundMutationCommand.Kind.BLOCK_DROP,
                worldId, expectedGroundRevision, committedGroundRevision,
                null, null, List.of(ground), List.of(), List.of(), List.of());
        if (!beginGroundSettlement()) return;
        long vaultId = ejection.vaultId();
        String token = ejection.token();
        Runnable settle = () -> {
            var groundOutcome = new java.util.concurrent.atomic.AtomicReference<
                    com.gameexpert.ground.service.GroundMutationOutcome>();
            try {
                var outcome = mobPersistence.settleVaultEjection(worldId, vaultId, token, () -> {
                    var result = groundMutationSettlements.settle(command);
                    groundOutcome.set(result);
                    if (result == com.gameexpert.ground.service.GroundMutationOutcome.STALE) {
                        throw new TrialRewardRetryException();
                    }
                });
                boolean advanced = groundOutcome.get()
                        == com.gameexpert.ground.service.GroundMutationOutcome.COMMITTED;
                enqueuePersistenceCompletion(() -> {
                    completeGroundSettlement(committedGroundRevision, advanced);
                    if (terminalPhase != TerminalPhase.RUNNING) return;
                    if (advanced) itemSystem.commitSettlementDrop(ground);
                    // 커밋·이미 소진·행 없음 어느 쪽이든 같은 행을 다시 정산할 일은 없다.
                    mobSystem.trialSpawners().acknowledgeVaultEjection(token);
                }, () -> completeGroundSettlement(committedGroundRevision, false));
            } catch (TrialRewardRetryException retry) {
                enqueuePersistenceCompletion(
                        () -> completeGroundSettlement(expectedGroundRevision, false),
                        () -> completeGroundSettlement(expectedGroundRevision, false));
            } catch (RuntimeException | Error failure) {
                enqueuePersistenceCompletion(
                        () -> completeGroundSettlement(expectedGroundRevision, false),
                        () -> completeGroundSettlement(expectedGroundRevision, false));
            }
        };
        PersistenceExecutor writer = ctx.persistenceExecutor();
        if (writer == null) {
            settle.run();
            drainPersistenceCompletions();
        } else if (!writer.trySubmit(settle)) {
            completeGroundSettlement(expectedGroundRevision, false);
        }
    }

    private static final class TrialRewardRetryException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    AnimalSettlementPersistenceService animalSettlements() { return animalSettlements; }
    boolean hasAnimalSettlements() { return animalSettlements != null; }

    AnimalBlockTickPersistenceService.WorldStore animalBlockTickStore() {
        return animalBlockTickStore;
    }

    int graceTicks() {
        return keepLoadedWhenEmpty ? Integer.MAX_VALUE : GRACE_TICKS;
    }

    /**
     * 사람이 모두 나가도 내리지 않는다. 월드 하나로 운영하는 서버는 다음 사람이 곧 같은 월드로
     * 들어오므로, 내렸다가 다시 올리는 비용(저장된 청크를 처음부터 다시 읽는 것)을 매번 치를 이유가
     * 없다. 실측: 내렸다 올린 월드의 첫 입장 80초, 올라 있는 월드 14초.
     */
    void keepLoadedWhenEmpty(boolean keep) {
        this.keepLoadedWhenEmpty = keep;
    }

    boolean disposed() {
        return disposed;
    }

    /** Generated-Cushion callbacks may run only in the live owner or ordered terminal drain. */
    private boolean generatedCushionOperationMayProceed() {
        return terminalPhase == TerminalPhase.RUNNING || terminalPhase == TerminalPhase.DRAINING;
    }

    /** Volatile fail-closed boundary for every scheduled owner phase. */
    boolean ownerTurnMayContinue() {
        return !disposed && terminalPhase == TerminalPhase.RUNNING
                && com.gameexpert.cluster.WorldAuthority.permitsRuntime(worldId);
    }

    private final AtomicBoolean clusterAuthorityAbandoned = new AtomicBoolean();

    void abandonClusterAuthority() {
        if (clusterAuthorityAbandoned.compareAndSet(false, true)) abortTerminal();
    }

    private void markOwnerThread() {
        ownerThread = Thread.currentThread();
    }

    private boolean isOwnerThread() {
        Thread currentOwner = ownerThread;
        // Before start there is no scheduled owner. Direct fixtures and the synchronous
        // persistence seam are the owner until a real owner turn is established.
        return currentOwner == null ? !started : currentOwner == Thread.currentThread();
    }

    private boolean ownerQueueRequired() {
        if (!started) return false;
        Thread currentOwner = ownerThread;
        return currentOwner == null || currentOwner != Thread.currentThread();
    }

    /** Schedules one coalesced owner callback; completion producers never grow this queue per item. */
    private boolean enqueueOwnerTask(Runnable task) {
        ScheduledExecutorService current = executor;
        if (current == null || current.isShutdown() || current.isTerminated()) return false;
        WorldWorkEvent work = architectureSubmitted("owner-completion-callback", false, 0, 0, 0L);
        try {
            current.execute(() -> {
                markOwnerThread();
                WorldWorkEvent.start(work);
                try {
                    task.run();
                } catch (RuntimeException | Error failure) {
                    WorldWorkEvent.outcome(work, "failed");
                    throw failure;
                } finally {
                    WorldWorkEvent.finish(work);
                }
            });
            return true;
        } catch (RejectedExecutionException rejected) {
            WorldWorkEvent.outcome(work, "rejected");
            WorldWorkEvent.finish(work);
            return false;
        }
    }

    private void requestTerminalOwnerDrain() {
        if (terminalPhase == TerminalPhase.RUNNING
                || disposalOwnerActionScheduled.get()
                || !terminalOwnerDrainScheduled.compareAndSet(false, true)) return;
        // A started owner may be the caller of the current bounded drain. Let its residue roll
        // into another owner turn; pre-start fixtures have no scheduled owner and are drained by
        // their direct caller instead.
        ScheduledExecutorService current = executor;
        if (!ownerQueueRequired()
                && (!started || current == null || current.isShutdown() || current.isTerminated())) {
            terminalOwnerDrainScheduled.set(false);
            return;
        }
        if (!enqueueOwnerTask(() -> {
            try {
                if (terminalPhase == TerminalPhase.DRAINING) {
                    drainMapPersistenceCompletions();
                    drainTerminalPersistenceCompletionBatch();
                } else if (terminalPhase == TerminalPhase.ABORTED
                        || terminalPhase == TerminalPhase.DISPOSED) {
                    if (terminalPhase == TerminalPhase.ABORTED) abortTerminalOwnerCleanup();
                    terminalizeResidualPersistenceCompletions();
                    publishTerminalOutcome(terminalPhase == TerminalPhase.ABORTED
                            ? TerminalOutcomeKind.ABORTED : TerminalOutcomeKind.DRAINED);
                }
            } catch (Throwable failure) {
                recordTerminalCleanupFailure(failure);
                publishTerminalOutcome(terminalDeadlineReached
                        ? TerminalOutcomeKind.DEADLINE : TerminalOutcomeKind.ABORTED);
            } finally {
                terminalOwnerDrainScheduled.set(false);
                if (terminalPersistenceQueueHasResidue()) {
                    requestTerminalOwnerDrain();
                }
            }
        })) {
            terminalOwnerDrainScheduled.set(false);
            recordTerminalCleanupFailure(new IllegalStateException(
                    "owner queue rejected terminal persistence drain"));
            publishTerminalOutcome(terminalPhase == TerminalPhase.DISPOSED
                    ? TerminalOutcomeKind.DRAINED : TerminalOutcomeKind.ABORTED);
        }
    }

    private boolean terminalPersistenceQueueHasResidue() {
        if (!persistenceCompletions.isEmpty() || !mapPersistenceCompletions.isEmpty()) return true;
        synchronized (this) {
            return !playerContainerActions.isEmpty()
                    || !canonicalLootOpenRetries.isEmpty()
                    || !canonicalLootOpenReservations.isEmpty();
        }
    }

    private boolean lifecycleEpochCurrent(long expectedEpoch) {
        return !disposed && terminalPhase == TerminalPhase.RUNNING
                && lifecycleEpoch == expectedEpoch;
    }

    private void recordTerminalCleanupFailure(Throwable failure) {
        if (failure == null) return;
        terminalCleanupFailures.add(failure);
        log.error("Terminal cleanup failed for world {}", worldId, failure);
    }

    private void markDisposalPersistenceFailed() {
        markDisposalPersistenceFailed(new IllegalStateException(
                "disposal persistence did not complete"));
    }

    private void markDisposalPersistenceFailed(Throwable failure) {
        disposalPersistenceFailed = true;
        recordTerminalCleanupFailure(failure);
    }

    /** Package-visible only for pre-start integration/bootstrap paths; scheduled simulation never uses it. */
    boolean started() {
        return started;
    }

    WebSocketSession session(String nickname) {
        SessionRegistry.Entry entry = ctx.registry().get(worldId, nickname);
        return entry == null ? null : entry.session();
    }

    /** 틱 시작: 실제 틱 번호를 올리고 유체 스케줄 기준을 맞춥니다. */
    /**
     * A prompt action turn applies ① of the upcoming tick early: every tick-keyed rule (mining
     * limits, item use windows, fluid scheduling) must see the tick those inputs used to run in,
     * not the one that already finished. The real tick then re-enters the same number.
     */
    void beginPromptActionTurn() {
        tickNo++;
        fluidSim.setClock(tickNo);
    }

    void endPromptActionTurn() {
        tickNo--;
        fluidSim.setClock(tickNo);
    }

    void beginTick() {
        tickNo++;
        fluidSim.setClock(tickNo);
        if (ctx.broadcaster() != null) {
            ctx.broadcaster().retryTickOutbound(worldId);
        }
        weatherSystem.tick(tickNo);
    }

    void incrementEmptyTicks() {
        emptyTicks++;
    }

    void resetEmptyTicks() {
        emptyTicks = 0;
    }

    int emptyTicks() {
        return emptyTicks;
    }

    // ── WS 스레드에서 호출(입장/퇴장) ──
    /** 허기 컬럼이 없던 세이브와 같은 만복 기본값으로 입장시킨다(SURV-H 도입 전 호출부 호환). */
    public PlayerTickState addPlayer(Long playerId, String nickname, String connectionId,
            double x, double y, double z, float yaw, float pitch, int health, PlayerInventory inventory,
            Integer bedSpawnX, Integer bedSpawnY, Integer bedSpawnZ) {
        return addPlayer(playerId, nickname, connectionId, x, y, z, yaw, pitch, health, inventory,
                bedSpawnX, bedSpawnY, bedSpawnZ,
                HungerRules.INITIAL_FOOD, HungerRules.INITIAL_SATURATION_MILLI);
    }

    /** [SURV-X] 경험치 도입 전 호출부 호환(누적 경험치 0). */
    public PlayerTickState addPlayer(Long playerId, String nickname, String connectionId,
            double x, double y, double z, float yaw, float pitch, int health, PlayerInventory inventory,
            Integer bedSpawnX, Integer bedSpawnY, Integer bedSpawnZ,
            int hunger, int saturationMilli) {
        return addPlayer(playerId, nickname, connectionId, x, y, z, yaw, pitch, health, inventory,
                bedSpawnX, bedSpawnY, bedSpawnZ, hunger, saturationMilli, 0);
    }

    /** [SURV-X] 인챈트 시드 도입 전 호출부 호환(접속 시 새로 뽑는다). */
    public PlayerTickState addPlayer(Long playerId, String nickname, String connectionId,
            double x, double y, double z, float yaw, float pitch, int health, PlayerInventory inventory,
            Integer bedSpawnX, Integer bedSpawnY, Integer bedSpawnZ,
            int hunger, int saturationMilli, int xpTotal) {
        return addPlayer(playerId, nickname, connectionId, x, y, z, yaw, pitch, health, inventory,
                bedSpawnX, bedSpawnY, bedSpawnZ, hunger, saturationMilli, xpTotal, null);
    }

    public synchronized PlayerTickState addPlayer(Long playerId, String nickname, String connectionId,
            double x, double y, double z, float yaw, float pitch, int health, PlayerInventory inventory,
            Integer bedSpawnX, Integer bedSpawnY, Integer bedSpawnZ,
            int hunger, int saturationMilli, int xpTotal, Integer enchantSeed) {
        return addDimensionPlayer(playerId, nickname, connectionId, x, y, z, yaw, pitch, health, inventory, bedSpawnX, bedSpawnY, bedSpawnZ, hunger, saturationMilli, xpTotal, enchantSeed, new com.gameexpert.state.service.PlayerDimensionIdentity(worldId, worldId, 0));
    }

    public synchronized PlayerTickState addDimensionPlayer(Long playerId, String nickname, String connectionId,
            double x, double y, double z, float yaw, float pitch, int health, PlayerInventory inventory,
            Integer bedSpawnX, Integer bedSpawnY, Integer bedSpawnZ,
            int hunger, int saturationMilli, int xpTotal, Integer enchantSeed,
            com.gameexpert.state.service.PlayerDimensionIdentity dimensionIdentity) {
        if (disposed || terminalPhase != TerminalPhase.RUNNING
                || playerInventorySettlementReservations.containsKey(nickname)) {
            return null;
        }
        PlayerTickState state = new PlayerTickState(playerId, nickname, x, y, z, yaw, pitch, health, inventory);
        if (dimensionIdentity.runtimeWorldId() != worldId) throw new IllegalArgumentException("dimension/runtime mismatch");
        state.bindDimension(dimensionIdentity);
        // [QA-GODMODE] QA 서버 기동 프로퍼티({@code game.qa-godmode}, 기본 off)를 접속 시 한 번만
        // 실어 준다. qa-seeding 과 같은 가드 계층이라 일반 배포 서버에서는 항상 false 다.
        state.setQaGodmode(ctx.properties().qaGodmode());
        state.restoreHunger(hunger, saturationMilli);
        state.restoreXpTotal(xpTotal);
        // 저장된 시드가 있으면 그대로 이어 쓴다. 접속마다 재추첨하면 제안을 무한 리롤할 수 있다.
        state.reshuffleEnchantSeed(enchantSeed == null ? enchantSeedRandom.nextInt() : enchantSeed);
        if (bedSpawnX != null && bedSpawnY != null && bedSpawnZ != null) {
            state.setBedSpawn(bedSpawnX, bedSpawnY, bedSpawnZ);
        }
        playerConnections.put(nickname, connectionId);
        generatedEvidenceConnectionGenerations.merge(nickname, 1L,
                (previous, ignored) -> Math.incrementExact(previous));
        players.put(nickname, state);
        return state;
    }

    void prepareWelcomeSnapshotForJoin() {
        prepareWelcomeSnapshotForJoin(null);
    }

    /** Bootstrap is lifecycle-serialized; later joins capture and submit only on the existing owner. */
    void prepareWelcomeSnapshotForJoin(String joiningNickname) {
        if (!started) {
            // Do not hold the runtime monitor while the writer enqueues its acknowledgment.
            prepareWelcomeSnapshotOnOwner(joiningNickname, null);
            return;
        }
        CompletableFuture<Void> ready = new CompletableFuture<>();
        if (!enqueueOwnerTask(() -> {
            try {
                prepareWelcomeSnapshotOnOwner(joiningNickname, ready);
            } catch (RuntimeException | Error failure) {
                ready.completeExceptionally(failure);
            }
        })) throw new IllegalStateException("welcome population owner admission rejected");
        try {
            ready.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("welcome population interrupted", interrupted);
        } catch (ExecutionException failed) {
            throw new IllegalStateException("welcome population failed", failed.getCause());
        }
    }

    private void prepareWelcomeSnapshotOnOwner(String joiningNickname, CompletableFuture<Void> ready) {
        if (!ownerTurnMayContinue()) throw new IllegalStateException("welcome population owner closed");
        // Recovery publishes only after its transaction commits. Apply that callback before the
        // first welcome snapshot is frozen so generated entities cannot appear one tick late.
        drainPersistenceCompletions();
        Set<Long> initialChunks = new HashSet<>();
        Set<Long> initialPopulationChunks = new HashSet<>();
        for (PlayerTickState player : players.values()) {
            PlayerPose pose = player.pose();
            int centerX = Math.floorDiv((int) Math.floor(pose.getX()), Blocks.CHUNK_X);
            int centerZ = Math.floorDiv((int) Math.floor(pose.getZ()), Blocks.CHUNK_Z);
            addSimulationNeighborhood(initialChunks, centerX, centerZ);
            if (joiningNickname != null && players.get(joiningNickname) != player) continue;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                int x = centerX + dx, z = centerZ + dz;
                if (accessor.isChunkActivated(x, z) && accessor.isChunkResident(x, z)) {
                    initialPopulationChunks.add(chunkKey(x, z));
                }
            }
        }
        Runnable publish = () -> {
            if (!ownerTurnMayContinue()) throw new IllegalStateException("welcome population owner closed");
            if (mobSystem.populationPublicationPending(initialPopulationChunks)) {
                throw new IllegalStateException("welcome population is still pending");
            }
            reconcileGeneratedEntityVisibility(Set.copyOf(initialChunks), List.of());
            mobSystem.refreshWelcomeSnapshotForChunks(initialChunks);
        };
        if (ready == null) {
            settleWelcomePopulation(initialPopulationChunks);
            publish.run();
            return;
        }
        mobSystem.preparePopulationForPublication(initialPopulationChunks);
        if (!mobSystem.populationPublicationPending(initialPopulationChunks)) {
            publish.run();
            ready.complete(null);
            return;
        }
        // Capture and FIFO admission are one owner operation. Every later full snapshot includes
        // these retained rows until the owner installs them; an older aggregate cannot overtake it.
        var snapshot = mobSystem.populationPersistenceSnapshot();
        Runnable persist = () -> {
            try {
                if (mobPersistence == null) mobSystem.acknowledgePopulationPersistence(snapshot);
                else persistMobsAndPopulation(snapshot);
                enqueuePersistenceCompletion(() -> {
                    try {
                        publish.run();
                        ready.complete(null);
                    } catch (RuntimeException | Error failure) {
                        ready.completeExceptionally(failure);
                    }
                }, () -> ready.completeExceptionally(
                        new IllegalStateException("welcome population owner closed")));
            } catch (RuntimeException | Error failure) {
                ready.completeExceptionally(failure);
            }
        };
        PersistenceExecutor writer = ctx.persistenceExecutor();
        if (writer == null) {
            persist.run();
            drainPersistenceCompletions();
        } else if (!writer.trySubmit(persist)) {
            ready.completeExceptionally(new IllegalStateException("welcome population writer admission rejected"));
        }
    }

    /**
     * 실행 중인 월드는 퇴장을 틱 소유자에게 넘긴다. 완료값은 연결 ID가 여전히 현재 연결일 때만
     * 제거된 최종 플레이어 상태이며, 제련 정산까지 반영되어 있다.
     */
    public CompletableFuture<PlayerTickState> requestPlayerLeave(String nickname, String connectionId) {
        synchronized (this) {
            if (disposed || terminalPhase != TerminalPhase.RUNNING) {
                return CompletableFuture.completedFuture(null);
            }
            if (!started) {
                return CompletableFuture.completedFuture(removePlayer(nickname, connectionId));
            }
            if (playerLeaveRequests.size() >= MAX_RUNTIME_ADMISSIONS) {
                return CompletableFuture.completedFuture(null);
            }
            PlayerLeaveRequest request = new PlayerLeaveRequest(nickname, connectionId);
            request.completion().whenComplete(
                    (ignored, failure) -> activePlayerLeaveRequests.remove(request));
            playerLeaveRequests.add(request);
            return request.completion();
        }
    }

    /** 시작 전 bootstrap/test 경계에서만 직접 제거한다. 실행 중 퇴장은 requestPlayerLeave를 사용한다. */
    PlayerTickState removePlayer(String nickname, String connectionId) {
        return removePlayerOnOwner(nickname, connectionId);
    }

    /** 월드 틱 소유자만 호출한다. */
    PlayerTickState removePlayerOnOwner(String nickname, String connectionId) {
        return removePlayerOnOwner(nickname, connectionId, null);
    }

    /** A durable leave may remove only with the exact reservation that blocked replacements. */
    PlayerTickState removePlayerOnOwner(String nickname, String connectionId,
            PlayerInventorySettlementReservation reservation) {
        PlayerTickState removed;
        synchronized (this) {
            if (terminalPhase == TerminalPhase.ABORTED
                    || terminalPhase == TerminalPhase.DISPOSED
                    || terminalPhase == TerminalPhase.DRAINING && reservation == null) return null;
            if (!java.util.Objects.equals(connectionId, playerConnections.get(nickname))) {
                return null;
            }
            PlayerInventorySettlementReservation active =
                    playerInventorySettlementReservations.get(nickname);
            if (active != null && active != reservation) return null;
            // The key is consumed before the receipt transaction. A still-queued request otherwise
            // disappears after the already-consumed departing inventory has been saved.
            refundQueuedVaultUnlocks(nickname);
            playerConnections.remove(nickname);
            removed = players.remove(nickname);
            if (removed != null) pendingCanonicalLootOpenRequests.remove(removed);
        }
        processGeneratedCushionRiderState(nickname,
                GeneratedCushionActionPolicy.Action.RIDER_LOGOUT);
        if (removed != null) {
            // These calls can acquire the inventory monitor. They must remain outside the runtime
            // monitor because disconnect settlement callbacks acquire inventory first.
            if (reservation != null) {
                removed.inventory().cancelSettlementLease(reservation.source());
            }
            // Merchant payments are player transients and must join the departing inventory
            // before its final persistence snapshot is taken.
            tickLoop.returnVillagerPayments(removed);
        }
        playerDemandCursors.remove(nickname);
        mobSystem.clearPlayerUseState(nickname);
        for (long mobId : mobSystem.detachLeashesHeldBy(nickname)) {
            Mob mob = mobSystem.combat().findAlive(mobId);
            if (mob != null) {
                itemSystem.spawnDeathDrop(PlayerInventory.LEAD, 1, 0,
                        mob.x, mob.y + 0.5, mob.z);
            }
        }
        // 열린 거래 화면은 연결 수명 상태다. 닉네임 재접속이 이전 주민 세션을 상속하면 안 된다.
        tickLoop.villagerTrades().close(nickname);
        // 현재 연결의 퇴장만 보트 자동 하차로 넘긴다. 이전 소켓 종료가 재접속자의 보트를 내리면 안 된다.
        boatSystem.playerLeft(nickname);
        placedEntities.playerLeft(nickname);
        cushionSystem.playerLeft(nickname);
        // [FARM-ANIMAL] 돼지 좌석도 같은 이유로 이 자리에서만 비운다(기수는 비영속이라 세션과 함께 사라진다).
        mobSystem.dismountPig(nickname);
        return removed;
    }

    PlayerLeaveRequest pollPlayerLeaveRequest() {
        synchronized (this) {
            if (terminalPhase != TerminalPhase.RUNNING || disposed) return null;
            PlayerLeaveRequest request = playerLeaveRequests.poll();
            if (request != null) activePlayerLeaveRequests.add(request);
            return request;
        }
    }

    /** Frozen owner-turn admission count; requeues wait behind this turn's exact batch. */
    int playerLeaveRequestBatchSize() {
        synchronized (this) {
            return terminalPhase == TerminalPhase.RUNNING && !disposed
                    ? playerLeaveRequests.size() : 0;
        }
    }

    /** 퇴장 정산을 시작하기 전, 요청한 소켓이 여전히 이 플레이어 세션을 소유하는지 확인한다. */
    PlayerTickState playerForConnection(String nickname, String connectionId) {
        return connectionId.equals(playerConnections.get(nickname)) ? players.get(nickname) : null;
    }

    /** Captures the exact connection identity only while the supplied player still owns it. */
    String connectionIdForPlayer(PlayerTickState expected) {
        if (expected == null || players.get(expected.nickname()) != expected) return null;
        return playerConnections.get(expected.nickname());
    }

    /** Admits one exact leased inventory/session until its durable callback reaches a terminal path. */
    PlayerInventorySettlementReservation reservePlayerInventorySettlement(
            PlayerTickState player, String connectionId,
            PlayerInventory inventory, PlayerInventory.CompletePersistenceSnapshot source) {
        if (player == null || connectionId == null || inventory == null || source == null) {
            return null;
        }
        // Inventory is checked before the runtime monitor. Disconnect/death callbacks already
        // hold this monitor in the opposite direction while installing a committed snapshot.
        if (!inventory.settlementLeased() || inventory.revision() != source.revision()) return null;
        var reservation = new PlayerInventorySettlementReservation(
                player.nickname(), connectionId, player, inventory, source);
        synchronized (this) {
            if (terminalPhase != TerminalPhase.RUNNING
                    || players.get(player.nickname()) != player
                    || !connectionId.equals(playerConnections.get(player.nickname()))
                    || player.inventory() != inventory
                    || playerInventorySettlementReservations.containsKey(player.nickname())) {
                return null;
            }
            playerInventorySettlementReservations.put(player.nickname(), reservation);
            return reservation;
        }
    }

    /** Identity-only check; callers hold the inventory monitor while validating its exact lease. */
    boolean ownsPlayerInventorySettlement(
            PlayerInventorySettlementReservation reservation) {
        return reservation != null
                && playerInventorySettlementReservations.get(reservation.nickname()) == reservation
                && players.get(reservation.nickname()) == reservation.player()
                && reservation.connectionId().equals(
                        playerConnections.get(reservation.nickname()))
                && reservation.player().inventory() == reservation.inventory();
    }

    void releasePlayerInventorySettlement(
            PlayerInventorySettlementReservation reservation) {
        if (reservation != null) {
            playerInventorySettlementReservations.remove(reservation.nickname(), reservation);
        }
    }

    /**
     * A durable row exists but its exact live CAS authority disappeared. Quarantine the runtime so
     * only recovery can publish that row; continuing would let an older periodic save overwrite it.
     */
    void quarantineCommittedPlayerInventorySettlement(
            PlayerInventorySettlementReservation reservation) {
        if (reservation != null) {
            synchronized (this) {
                playerInventorySettlementReservations.remove(reservation.nickname(), reservation);
            }
            // The inventory callback may already hold the inventory monitor. Never acquire the
            // runtime monitor and then call back into that object, or an owner-side reservation
            // can deadlock against this callback while it is aborting the runtime.
            reservation.inventory().releaseSettlementLease(reservation.source());
        }
        boolean shouldAbort;
        synchronized (this) {
            shouldAbort = terminalPhase != TerminalPhase.DISPOSED;
        }
        if (shouldAbort) {
            abortTerminal();
            log.error("World {} quarantined after committed player inventory authority loss: player={}",
                    worldId, reservation == null ? "unknown" : reservation.nickname());
        }
    }

    /** 비동기 퇴장 정산이 STALE/제출 실패로 끝났을 때 다음 틱에 같은 요청을 다시 시도한다. */
    void requeuePlayerLeaveRequest(PlayerLeaveRequest request) {
        if (request == null) return;
        boolean reject = false;
        synchronized (this) {
            if (request.completion().isDone()) return;
            activePlayerLeaveRequests.remove(request);
            // Phase observation and queue insertion are one transaction. An abort cannot publish
            // ABORTED between this check and add(), leaving a request stranded in a dead queue.
            if (terminalPhase == TerminalPhase.RUNNING
                    && !persistenceCallbackAdmissionClosed
                    && playerLeaveRequests.size() < MAX_RUNTIME_ADMISSIONS) {
                playerLeaveRequests.add(request);
            } else {
                terminalLeaveSettlementRejected = true;
                reject = true;
            }
        }
        if (reject) request.complete(null);
    }

    /** A durable leave callback may mutate live state only while running or in the ordered drain. */
    boolean playerLeaveCompletionMayApply() {
        return terminalPhase == TerminalPhase.RUNNING || terminalPhase == TerminalPhase.DRAINING;
    }

    /**
     * Fails the runtime closed without leaving a caller-owned leave future unresolved.  Both queued
     * and already-owner-admitted requests are preexisting work, so every one is completed with the
     * same null rejection result before the terminal phase becomes observable to later callers.
     */
    private void abortTerminal() {
        List<PlayerInventorySettlementReservation> reservations;
        List<PlayerLeaveRequest> queued = new ArrayList<>();
        List<PlayerLeaveRequest> active;
        synchronized (this) {
            if (terminalPhase == TerminalPhase.DISPOSED) return;
            terminalPhase = TerminalPhase.ABORTED;
            persistenceCallbackAdmissionClosed = true;
            lifecycleEpoch++;
            disposalStalled = true;
            terminalLeaveSettlementRejected = true;
            cancelTickSchedule();
            reservations = new ArrayList<>(playerInventorySettlementReservations.values());
            playerInventorySettlementReservations.clear();
            PlayerLeaveRequest request;
            while ((request = playerLeaveRequests.poll()) != null) {
                queued.add(request);
            }
            active = new ArrayList<>(activePlayerLeaveRequests);
            activePlayerLeaveRequests.clear();
            cancelAllCanonicalLootOpenReservationsLocked();
        }
        // Release external inventory monitors after publishing ABORTED. Completion of a leave
        // future is also outside the runtime monitor so callbacks cannot re-enter this object
        // while the terminal snapshot is being assembled.
        for (PlayerInventorySettlementReservation reservation : reservations) {
            reservation.inventory().releaseSettlementLease(reservation.source());
        }
        for (PlayerLeaveRequest request : queued) request.complete(null);
        for (PlayerLeaveRequest request : active) request.complete(null);
        disposalOwnerCompletion.complete(false);
        disposalFlightCompletion.complete(false);
        disposalCompletion.completeExceptionally(
                new IllegalStateException("world terminal lifecycle aborted"));
        if (isOwnerThread()) {
            abortTerminalOwnerCleanup();
        } else {
            requestTerminalOwnerDrain();
        }
    }

    /** Owner-only cleanup after ABORTED; external callers only flip the lifecycle boundary. */
    private void abortTerminalOwnerCleanup() {
        if (!abortedOwnerCleanupDone.compareAndSet(false, true)) return;
        markOwnerThread();
        failPendingSnapshotEncodes();
        terminalizeResidualPersistenceCompletions();
        synchronized (this) {
            generatedCushionActionEvidence.clear();
            pendingGeneratedCushionRiderCleanups.clear();
            pendingCommittedGeneratedCushionMutations.clear();
            drainingCushionLogoutAdmissions.clear();
            appliedGeneratedCushionUpdates.clear();
            pendingGeneratedEntityMessages.clear();
            pendingFinalCarrierTickPublications.clear();
            finalCarrierTickAdmissionCompletions.clear();
            trialPersistenceInFlight = false;
            for (DepartedSave pending : pendingDepartedSaves.values()) {
                pending.submissionReserved = false;
                pending.retryScheduled = false;
                pending.callbacks.clear();
            }
            pendingDepartedSaves.clear();
            completedDepartedSaveGenerations.clear();
            playerContainerActions.clear();
            playerContainerSettlementInFlight = false;
            cancelAllCanonicalLootOpenReservationsLocked();
        }
        synchronized (generatedCushionPersistencePublications) {
            for (GeneratedCushionPersistencePublication publication
                    : generatedCushionPersistencePublications.values()) {
                publication.state = PublicationState.REVOKED;
            }
            generatedCushionPersistencePublications.clear();
        }
        publishTerminalOutcome(TerminalOutcomeKind.ABORTED);
        if (clusterAuthorityAbandoned.get()) {
            Runnable cleanup;
            ScheduledExecutorService ownedExecutor;
            synchronized (this) {
                cleanup = onDispose;
                onDispose = null;
                ownedExecutor = executor;
            }
            // All subsequent persistence callbacks see ABORTED and the database write fence.
            // No final cached snapshot may be written after another incarnation took ownership.
            if (cleanup != null) cleanup.run();
            if (ownedExecutor != null) ownedExecutor.shutdown();
            ctx.broadcaster().forgetWorld(worldId);
        }
    }

    public boolean isEmpty() {
        return players.isEmpty() && dimensionDepartures.isEmpty() && dimensionArrivalPins.get() == 0
                && fleshColonyReservations.isEmpty()
                && (fleshNether == null || !fleshNether.pending());
    }

    /**
     * Final persistence and manager deregistration barrier used by an explicit world deletion.
     *
     * <p>대기는 상한이 있습니다. 만료하면 월드를 폐기 실패 격리로 표시하고 {@code false}를 돌려주므로,
     * 호출자는 수명주기 락을 잡은 채 무한정 서 있지 않습니다. 백그라운드 저장 재시도는 계속됩니다.</p>
     */
    boolean awaitDisposal() {
        return awaitDisposal(DEFAULT_DISPOSAL_AWAIT_TIMEOUT_MILLIS);
    }

    boolean awaitDisposal(long timeoutMillis) {
        if (disposalOwnerActionStarted.get() && isOwnerThread()
                && !disposalCompletion.isDone()) {
            // An on-dispose or admitted completion callback may join the same flight, but the
            // owner must never block waiting for a future whose final drain it is executing.
            return false;
        }
        try {
            disposalCompletion.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return terminalOutcome.kind() == TerminalOutcomeKind.DRAINED;
        } catch (TimeoutException expired) {
            markDisposalStalled(timeoutMillis);
            return false;
        } catch (InterruptedException interrupted) {
            // 대기 스레드가 인터럽트된 것은 기한 초과가 아니다. 기한 표시를 세우면 결과가
            // DEADLINE으로 뒤바뀌어, 실제로는 중단된 폐기를 기한 만료로 오분류한다.
            abortTerminal();
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException failed) {
            return false;
        }
    }

    /** 폐기 실패 격리: 재입장은 거부하고, 죽은 월드가 주기 틱을 계속 돌지 않도록 스케줄을 해제합니다. */
    private void markDisposalStalled(long timeoutMillis) {
        cancelTickSchedule();
        terminalDeadlineReached = true;
        if (disposalCompletion.isDone() || disposalStalled) return;
        disposalStalled = true;
        log.error("월드 {} 폐기가 {}ms 안에 끝나지 않아 격리합니다. 재입장을 거부하고 최종 저장을 계속 재시도합니다.",
                worldId, timeoutMillis);
    }

    private void markOwnerQuiescenceStalled(long timeoutMillis) {
        cancelTickSchedule();
        terminalDeadlineReached = true;
        boolean alreadyStalled;
        synchronized (this) {
            if (disposalCompletion.isDone() || terminalPhase == TerminalPhase.DISPOSED) return;
            // The owner may still be executing an admitted turn. No later player, publication,
            // or persistence admission may attach to a runtime whose ordering boundary is
            // unknowable.
            alreadyStalled = disposalStalled;
        }
        abortTerminal();
        if (alreadyStalled) return;
        log.error("월드 {} owner turn이 {}ms 안에 끝나지 않아 격리합니다. "
                        + "재입장을 거부하고 폐기 전 런타임 상태를 보존합니다.",
                worldId, timeoutMillis);
    }

    /** 폐기 대기가 만료해 재입장을 거부해야 하는 상태인지. 폐기가 끝나면 자동으로 해제됩니다. */
    boolean isDisposalStalled() {
        return disposalStalled && !disposed;
    }

    /**
     * 폐기된 월드는 100ms 무동작 틱을 계속 돌 이유가 없습니다. 최종 저장 재시도는 같은 executor의
     * 1초 지연 예약을 쓰므로 주기 틱만 끊어도 재시도는 유지됩니다.
     */
    private void cancelTickSchedule() {
        ScheduledFuture<?> current = tickTask;
        if (current != null) current.cancel(false);
    }

    /**
     * 애플리케이션 종료 경계. 주기 틱을 먼저 취소하고 이미 실행 중인 owner turn 뒤에서 폐기를
     * 시작합니다. owner quiescence와 최종 writer 장벽에는 각각 상한이 있으며 실패는 예외로 드러납니다.
     */
    void disposeForShutdown() {
        if (!disposeForShutdownWithin(DEFAULT_OWNER_QUIESCENCE_TIMEOUT_MILLIS,
                DEFAULT_DISPOSAL_AWAIT_TIMEOUT_MILLIS)) {
            throw new IllegalStateException(
                    "world shutdown did not cross its bounded disposal barriers");
        }
    }

    /** Package-visible bounded seam for lifecycle tests and manager-owned shutdown policies. */
    boolean disposeForShutdownWithin(long ownerQuiescenceTimeoutMillis,
            long disposalAwaitTimeoutMillis) {
        if (ownerQuiescenceTimeoutMillis <= 0L || disposalAwaitTimeoutMillis <= 0L) {
            throw new IllegalArgumentException("shutdown timeouts must be positive");
        }
        long deadlineNanos = checkedDeadlineNanos(disposalAwaitTimeoutMillis);
        if (!disposeNowWithin(false, deadlineNanos, true, ownerQuiescenceTimeoutMillis)) {
            return false;
        }
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0L) {
            markDisposalStalled(disposalAwaitTimeoutMillis);
            return false;
        }
        return awaitDisposal(Math.max(1L,
                TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
    }

    private boolean beginDisposalFlight(boolean requireEmpty) {
        synchronized (this) {
            if (disposed) return true;
            if (terminalPhase == TerminalPhase.ABORTED) return false;
            if (!disposalFlightStarted) {
                if (requireEmpty && !isEmpty()) return true;
                disposalFlightStarted = true;
                terminalPhase = TerminalPhase.DRAINING;
                lifecycleEpoch++;
                disposalPlayers = List.copyOf(players.values());
                cancelAllCanonicalLootOpenReservationsLocked();
            }
            return true;
        }
    }

    private boolean awaitDisposalOwner(long timeoutMillis) {
        if (disposalOwnerActionStarted.get() && isOwnerThread()
                && !disposalOwnerCompletion.isDone()) return false;
        try {
            return disposalOwnerCompletion.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException expired) {
            terminalDeadlineReached = true;
            return false;
        } catch (InterruptedException interrupted) {
            terminalDeadlineReached = true;
            abortTerminal();
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException failed) {
            return false;
        }
    }

    private boolean ensureDisposalOwnerAction(boolean forceOwnerQueue, long deadlineNanos) {
        if (disposalOwnerCompletion.isDone()) return disposalOwnerCompletion.getNow(false);
        if (disposalOwnerActionStarted.get()) return true;
        // Before start there is no scheduled owner, so the direct fixture may run inline. Once
        // start() has returned, an unobserved owner is still a real scheduled owner and disposal
        // must enter its queue rather than mutating the world from the caller thread.
        boolean inline = !forceOwnerQueue && !started || isOwnerThread();
        if (forceOwnerQueue && ownerQueueRequired()) inline = false;
        if (inline) {
            runDisposalOnOwner(deadlineNanos);
            if (disposalOwnerCompletion.isDone()) {
                return disposalOwnerCompletion.getNow(false);
            }
            return true;
        }
        if (disposalOwnerActionScheduled.compareAndSet(false, true)) {
            if (!enqueueOwnerTask(() -> {
                try {
                    runDisposalOnOwner(deadlineNanos);
                } finally {
                    disposalOwnerActionScheduled.set(false);
                }
            })) {
                disposalOwnerActionScheduled.set(false);
                disposalOwnerCompletion.complete(false);
                disposalFlightCompletion.complete(false);
                return false;
            }
        }
        return true;
    }

    private boolean disposeNowWithin(boolean requireEmpty, long deadlineNanos,
            boolean forceOwnerQueue, long ownerQuiescenceTimeoutMillis) {
        if (!beginDisposalFlight(requireEmpty)) return false;
        synchronized (this) {
            if (!disposalFlightStarted) return true;
        }
        if (disposalOwnerActionStarted.get() && isOwnerThread()
                && !disposalOwnerCompletion.isDone()) {
            // The in-flight owner action is the join point. Normal dispose() is a non-blocking
            // join; shutdown reports false until the terminal outcome becomes DRAINED.
            return !forceOwnerQueue;
        }
        if (!ensureDisposalOwnerAction(forceOwnerQueue, deadlineNanos)) {
            if (!disposalOwnerCompletion.isDone()) terminalDeadlineReached = true;
            if (terminalPhase == TerminalPhase.DRAINING) abortTerminal();
            return false;
        }
        if (forceOwnerQueue && !disposalOwnerCompletion.isDone()) {
            boolean ownerResult = awaitDisposalOwner(ownerQuiescenceTimeoutMillis);
            if (!ownerResult && !disposalOwnerCompletion.isDone()) {
                markOwnerQuiescenceStalled(ownerQuiescenceTimeoutMillis);
                return false;
            }
        }
        if (!disposalOwnerCompletion.isDone()) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L) {
                markDisposalStalled(Math.max(1L,
                        TimeUnit.NANOSECONDS.toMillis(Math.max(0L, remainingNanos))));
                abortTerminal();
                return false;
            }
            boolean ownerResult = awaitDisposalOwner(Math.max(1L,
                    TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
            if (!ownerResult && !disposalOwnerCompletion.isDone()) {
                markDisposalStalled(Math.max(1L,
                        TimeUnit.NANOSECONDS.toMillis(Math.max(0L, remainingNanos))));
                abortTerminal();
                return false;
            }
        }
        return disposalOwnerCompletion.getNow(false);
    }

    private boolean ownerQuiescesWithin(ScheduledExecutorService current, long timeoutMillis) {
        try {
            if (current.isTerminated()) return true;
            if (current.isShutdown()) {
                return current.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS);
            }
            CompletableFuture<Void> ownerQuiesced = new CompletableFuture<>();
            // The barrier itself performs no mutation. Once it runs, every owner turn admitted
            // before cancellation is complete and the caller can dispose without racing it.
            current.execute(() -> ownerQuiesced.complete(null));
            ownerQuiesced.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException | ExecutionException | RejectedExecutionException failed) {
            return current.isTerminated();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** welcome용: 지정 닉네임을 제외한 접속자 pose 목록(volatile 읽기). */
    public List<PlayerPose> poses(String excludeNickname) {
        List<PlayerPose> list = new ArrayList<>();
        for (PlayerTickState state : players.values()) {
            if (!state.nickname().equals(excludeNickname)) {
                list.add(state.pose());
            }
        }
        return list;
    }

    /** /pos 등 조회용: 지정 닉네임의 현재 pose(없으면 null, volatile 읽기). */
    public PlayerPose pose(String nickname) {
        PlayerTickState state = players.get(nickname);
        return state == null ? null : state.pose();
    }

    public long worldTime() {
        return clock.worldTime();
    }

    /** welcome에 실을 서버 누적 일수(0부터 시작)입니다. */
    public long dayCount() {
        return clock.dayCount();
    }

    public long gameTimeMcTicks() {
        return clock.gameTimeMcTicks();
    }

    /** 틱 중인 음식 모닥불만 welcome에 복사해 클라이언트의 정적 음식 연출을 복원합니다. */
    List<CampfireSnapshot> campfireSnapshots() {
        List<CampfireSnapshot> snapshots = new ArrayList<>();
        for (long key : campfireStorage.tickingKeys()) {
            int[] position = CampfireStorage.unkey(key);
            CampfireInventory inventory = campfireStorage.peekAt(
                    position[0], position[1], position[2]);
            if (inventory == null || !inventory.occupied()) continue;
            List<CampfireSlot> slots = new ArrayList<>(CampfireInventory.SLOTS);
            for (int slot = 0; slot < CampfireInventory.SLOTS; slot++) {
                slots.add(new CampfireSlot(slot, inventory.itemType(slot), inventory.cookTicks(slot)));
            }
            snapshots.add(new CampfireSnapshot(position[0], position[1], position[2], slots));
        }
        return List.copyOf(snapshots);
    }

    /**
     * resident 청크만 최종 composed snapshot으로 복사합니다.
     *
     * <p>이 메서드는 {@link TerrainAccessor#isChunkResident(int, int)} 실패 시 즉시 unavailable을
     * 반환합니다. getBlock/prepareChunkForActivation/generateChunk를 호출하지 않으므로 tick에서 cold
     * 청크 생성·DB read·activation을 일으키지 않습니다.</p>
     */
    ChunkSnapshotResult captureResidentChunkSnapshot(int chunkX, int chunkZ) {
        SnapshotCells cells = copyResidentSnapshotCells(chunkX, chunkZ);
        if (cells == null) return ChunkSnapshotResult.unavailable(chunkX, chunkZ, worldEpoch);
        return ChunkSnapshotResult.available(completeSnapshot(cells));
    }

    /** 입장 resume 위치의 3×3 halo를 큐에 넣습니다. 복사는 world tick owner만 수행합니다. */
    public void enqueueSpawnHaloSnapshots(WebSocketSession session, double x, double z) {
        if (terminalPhase != TerminalPhase.RUNNING) return;
        int centerX = Math.floorDiv((int) Math.floor(x), Blocks.CHUNK_X);
        int centerZ = Math.floorDiv((int) Math.floor(z), Blocks.CHUNK_Z);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (terminalPhase != TerminalPhase.RUNNING) return;
                enqueueChunkSnapshotRequest(session, centerX + dx, centerZ + dz, true);
            }
        }
        // 요청을 넣은 뒤에 등록한다. 먼저 등록하면 그 사이의 점검이 대기 요청 0개를 입장 완료로 본다.
        if (session != null) {
            entryWatches.put(session.getId(),
                    new EntryWatch(session, centerX, centerZ, System.nanoTime()));
        }
    }

    // ── 입장 감시(진단 전용) ──
    // 180초 입장 정체가 ERROR 없이 한 번 났지만 로그로는 어느 단계에서 멈췄는지 가릴 수 없었다.
    // 입장이 늦어지면 그 세션이 기다리는 청크마다 파이프라인 단계를 한 줄로 남긴다. 동작은 바꾸지
    // 않고 owner 턴에서 초당 한 번, 감시 대상이 있을 때만 읽는다.

    /** 클라이언트가 월드를 공개하기 전에 기다리는 입장 범위(ChunkManager ENTRY_READY_CHUNK_RADIUS). */
    private static final int ENTRY_WATCH_RADIUS = 4;
    private static final long ENTRY_WATCHDOG_NANOS = TimeUnit.SECONDS.toNanos(
            Math.max(1L, Long.getLong("webcraft.entryWatchdogSeconds", 20L)));
    private static final long ENTRY_WATCHDOG_REPEAT_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final long ENTRY_WATCHDOG_GIVE_UP_NANOS = TimeUnit.MINUTES.toNanos(5);
    private static final long ENTRY_WATCHDOG_CHECK_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final int ENTRY_WATCH_LISTED_CHUNKS = 24;

    /** welcome 한 번의 입장. 입장 범위의 대기 요청이 모두 배달되면 끝난다. */
    private static final class EntryWatch {
        private final WebSocketSession session;
        private final int centerX;
        private final int centerZ;
        private final long startedNanos;
        /** owner 전용. */
        private long nextReportNanos;
        private int reports;

        private EntryWatch(WebSocketSession session, int centerX, int centerZ, long startedNanos) {
            this.session = session;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.startedNanos = startedNanos;
            this.nextReportNanos = startedNanos + ENTRY_WATCHDOG_NANOS;
        }
    }

    private final ConcurrentHashMap<String, EntryWatch> entryWatches = new ConcurrentHashMap<>();
    private long nextEntryWatchdogCheckNanos;

    /** Owner turn tail: one cheap emptiness test, and at most one real check per second. */
    private void runEntryWatchdog() {
        if (entryWatches.isEmpty()) return;
        long now = System.nanoTime();
        if (now < nextEntryWatchdogCheckNanos) return;
        nextEntryWatchdogCheckNanos = now + ENTRY_WATCHDOG_CHECK_INTERVAL_NANOS;
        try {
            checkStalledEntries(now);
        } catch (RuntimeException failure) {
            // 진단이 owner 턴을 실패로 만들면 안 된다.
            log.warn("입장 감시 진단 실패: world={}", worldId, failure);
        }
    }

    /**
     * Ends watches whose entry range has no undelivered request left, and reports the others once
     * they are {@link #ENTRY_WATCHDOG_NANOS} old, then every {@link #ENTRY_WATCHDOG_REPEAT_NANOS}.
     *
     * @return the WARN lines emitted by this call (tests)
     */
    List<String> checkStalledEntries(long now) {
        List<String> emitted = new ArrayList<>();
        for (EntryWatch watch : entryWatches.values()) {
            String sessionId = watch.session.getId();
            List<ChunkSnapshotRequest> pending = pendingEntryRequests(watch);
            boolean closed = !watch.session.isOpen();
            if (pending.isEmpty() || closed || now - watch.startedNanos > ENTRY_WATCHDOG_GIVE_UP_NANOS) {
                entryWatches.remove(sessionId, watch);
                if (watch.reports > 0) {
                    log.info("입장 감시 종료: world={} session={} nickname={} 경과={}ms 결과={} 남은청크={}",
                            worldId, sessionId, entryNickname(watch.session),
                            (now - watch.startedNanos) / 1_000_000L,
                            closed ? "closed" : pending.isEmpty() ? "delivered" : "gave-up",
                            pending.size());
                }
                continue;
            }
            if (now < watch.nextReportNanos) continue;
            watch.nextReportNanos = now + ENTRY_WATCHDOG_REPEAT_NANOS;
            watch.reports++;
            String line = describeStalledEntry(watch, pending, now);
            log.warn("{}", line);
            emitted.add(line);
        }
        return emitted;
    }

    /** Undelivered requests of this session inside its entry range, halo first then ring order. */
    private List<ChunkSnapshotRequest> pendingEntryRequests(EntryWatch watch) {
        List<ChunkSnapshotRequest> halo = new ArrayList<>();
        List<ChunkSnapshotRequest> rest = new ArrayList<>();
        String sessionId = watch.session.getId();
        for (int dz = -ENTRY_WATCH_RADIUS; dz <= ENTRY_WATCH_RADIUS; dz++) {
            for (int dx = -ENTRY_WATCH_RADIUS; dx <= ENTRY_WATCH_RADIUS; dx++) {
                ChunkSnapshotRequest request = pendingChunkSnapshotRequests.get(
                        new ChunkSnapshotRequestKey(sessionId, watch.centerX + dx, watch.centerZ + dz));
                if (request == null) continue;
                (Math.abs(dx) <= 1 && Math.abs(dz) <= 1 ? halo : rest).add(request);
            }
        }
        rest.sort(java.util.Comparator.comparingInt(request -> Math.max(
                Math.abs(request.chunkX - watch.centerX), Math.abs(request.chunkZ - watch.centerZ))));
        halo.addAll(rest);
        return halo;
    }

    private static String entryNickname(WebSocketSession session) {
        try {
            Object nickname = session.getAttributes() == null ? null
                    : session.getAttributes().get(
                            com.gameexpert.ws.SessionAttributes.ATTR_NICKNAME);
            return nickname == null ? "?" : nickname.toString();
        } catch (RuntimeException unavailable) {
            return "?";
        }
    }

    /**
     * One line that names, for every chunk the entry still waits on, the pipeline stage it sits in,
     * plus every shared bound that could hold it: the activation FIFO head, the welcome gate,
     * deferred recoveries/activations, detached worker slots, snapshot admission and the send lane.
     */
    private String describeStalledEntry(EntryWatch watch, List<ChunkSnapshotRequest> pending,
            long now) {
        Set<Long> welcomeTargets = welcomeCanonicalWorkTargets();
        Map<Long, Integer> fifoIndex = new HashMap<>();
        int fifoParked = 0;
        int index = 0;
        StringBuilder fifoHead = new StringBuilder();
        for (Map.Entry<Long, PreparedChunkActivation> entry
                : pendingChunkActivationApplications.entrySet()) {
            PreparedChunkActivation activation = entry.getValue();
            fifoIndex.put(entry.getKey(), index);
            if (welcomeTargets != null && activation.waitsAtWelcomeGate(welcomeTargets)) fifoParked++;
            if (index < 4) {
                if (index > 0) fifoHead.append(' ');
                fifoHead.append(activation.chunkX).append(',').append(activation.chunkZ)
                        .append(':').append(activation.stepLabel(welcomeTargets));
            }
            index++;
        }
        Map<ChunkSnapshotRequestKey, String> sending = new HashMap<>();
        String laneState = "none";
        SnapshotSendLane lane = snapshotSendLanes.get(watch.session.getId());
        if (lane != null) {
            synchronized (lane) {
                int position = 0;
                for (SnapshotDelivery delivery : lane.queue) {
                    sending.put(delivery.request.key, "sending#" + position++
                            + (delivery.ready ? "" : "(encoding)"));
                }
                SnapshotDelivery head = lane.queue.peekFirst();
                laneState = "queue=" + lane.queue.size()
                        + " headReady=" + (head != null && head.ready)
                        + " senderActive=" + lane.senderActive
                        + " sendingFor=" + (lane.senderActive && lane.sendStartedNanos != 0L
                                ? (now - lane.sendStartedNanos) / 1_000_000L + "ms" : "-")
                        + " stalled=" + lane.stalled;
            }
        }
        StringBuilder chunks = new StringBuilder();
        int listed = 0;
        for (ChunkSnapshotRequest request : pending) {
            if (listed == ENTRY_WATCH_LISTED_CHUNKS) {
                chunks.append(" +").append(pending.size() - listed).append(" more");
                break;
            }
            if (listed++ > 0) chunks.append(' ');
            chunks.append(request.chunkX).append(',').append(request.chunkZ).append('=')
                    .append(entryChunkStage(request, fifoIndex, welcomeTargets, sending));
        }
        StringBuilder haloSent = new StringBuilder();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                ChunkSnapshotRequestKey key = new ChunkSnapshotRequestKey(watch.session.getId(),
                        watch.centerX + dx, watch.centerZ + dz);
                haloSent.append(pendingChunkSnapshotRequests.containsKey(key) ? '.' : 'S');
            }
        }
        int payloadAwaiting = 0;
        for (StructureReadyPass pass : structureReadyPasses.values()) {
            if (pass.payloadAwaiting) payloadAwaiting++;
        }
        return "입장 지연 진단: world=" + worldId
                + " session=" + watch.session.getId()
                + " nickname=" + entryNickname(watch.session)
                + " elapsed=" + (now - watch.startedNanos) / 1_000_000L + "ms"
                + " report=" + watch.reports
                + " center=" + watch.centerX + ',' + watch.centerZ
                + " undelivered=" + pending.size() + '/' + (2 * ENTRY_WATCH_RADIUS + 1)
                        * (2 * ENTRY_WATCH_RADIUS + 1)
                + " haloSent=" + haloSent
                + " players=" + players.size()
                + " chunks=[" + chunks + ']'
                + " welcomeGate=" + (welcomeTargets == null ? "open" : welcomeTargets.size() + "targets")
                + " activation{demands=" + chunkActivationDemands.size()
                + " plans=" + pendingChunkActivationPlans.size()
                + " prepared=" + preparedChunkActivations.size()
                + " fifo=" + pendingChunkActivationApplications.size()
                + " fifoParkedAtGate=" + fifoParked
                + " fifoHead=[" + fifoHead + "]"
                + " decorations=" + pendingStructureDecorations.size()
                + " passes=" + structureReadyPasses.size()
                + " payloadAwaiting=" + payloadAwaiting
                + " deferredPasses=" + deferredStructureReadyPasses.size()
                + " pendingLiveUpdates=" + pendingLiveBlockUpdates.size() + '}'
                + " deferred{recoveries=" + deferredFinalCarrierRecoveries.size()
                + headKeys(deferredFinalCarrierRecoveries.keySet())
                + " recoveryLockBackoff=" + deferredRecoveryLockRetries.size()
                + " tickActivations=" + deferredTickSchedulerActivations.size()
                + headKeys(deferredTickSchedulerActivations.keySet())
                + " activationLockBackoff=" + deferredActivationLockRetries.size()
                + " lockBusyThisTurn=" + finalCarrierWorldLockBusyThisTurn + '}'
                + " detached{payload=" + finalCarrierPayloadWorkCount.get() + "/8"
                + " gameplayPayload=" + finalCarrierGameplayPayloadWorkCount.get() + "/8"
                + " recovery=" + chunkRecoveryWorkCount.get() + '/' + MAX_CHUNK_RECOVERY_WORKS
                + "(tracked=" + chunkRecoveryWorks.size() + ')'
                + " tickActivation=" + tickActivationPreparationCount.get() + '/'
                + MAX_TICK_ACTIVATION_PREPARATIONS
                + "(tracked=" + tickActivationPreparations.size() + ")}"
                + " workers{payload=" + workerState(FINAL_CARRIER_PAYLOAD_WORKERS)
                + " gameplayPayload=" + workerState(FINAL_CARRIER_GAMEPLAY_PAYLOAD_WORKERS)
                + " lanes=" + workerState(FINAL_CARRIER_LANE_WORKERS)
                + " planners=" + workerState(ACTIVATION_PLANNERS)
                + " preparers=" + workerState(SNAPSHOT_PREPARERS)
                + " encoders=" + workerState(SNAPSHOT_ENCODERS)
                + " senders=" + workerState(SNAPSHOT_SENDERS) + '}'
                + " snapshot{requests=" + pendingChunkSnapshotRequests.size()
                + " prepQueue=" + snapshotPreparationRequests.size()
                + " readyQueue=" + readyChunkSnapshotRequests.size()
                + " preparedResults=" + preparedSnapshotChunks.size()
                + " preparations=" + pendingSnapshotChunkPreparations.size() + '/'
                + MAX_PENDING_SNAPSHOT_PREPARATIONS
                + " demandBound=" + pendingSnapshotGenerationPermits.size() + '/'
                + SnapshotPreparationAdmission.CONCURRENT_UNCOMMITTED_CANONICAL_DEMAND
                + " deferredByDemandBound=" + deferredCanonicalDemandAdmissions
                + " orderedAdmissions=" + orderedCanonicalDemandAdmissions
                + " unavailableTreeSources=" + unavailableTreePlanningSources.size() + '}'
                + " sendLane{" + laneState + '}';
    }

    private static String headKeys(Collection<Long> keys) {
        if (keys.isEmpty()) return "";
        StringBuilder head = new StringBuilder("[");
        int listed = 0;
        for (long key : keys) {
            if (listed == 4) {
                head.append(" ..");
                break;
            }
            if (listed++ > 0) head.append(' ');
            head.append((int) (key >> 32)).append(',').append((int) key);
        }
        return head.append(']').toString();
    }

    private static String workerState(ThreadPoolExecutor executor) {
        return executor.getActiveCount() + "a/" + executor.getQueue().size() + "q";
    }

    /** Pipeline stage tokens of one undelivered entry chunk, in pipeline order. */
    private String entryChunkStage(ChunkSnapshotRequest request, Map<Long, Integer> fifoIndex,
            Set<Long> welcomeTargets, Map<ChunkSnapshotRequestKey, String> sending) {
        int chunkX = request.chunkX;
        int chunkZ = request.chunkZ;
        long key = chunkKey(chunkX, chunkZ);
        List<String> stage = new ArrayList<>();
        if (request.entryHalo) stage.add("halo");
        stage.add(accessor.isChunkResident(chunkX, chunkZ) ? "res" : "cold");
        if (started && !players.isEmpty() && !activeSimulationChunks.contains(key)) stage.add("inactive");
        if (!canProcessChunkSnapshotRequest(request)) stage.add("unprocessable");
        if (queuedChunkSnapshotRequests.get(request.key) == request) stage.add("queued");
        if (pendingSnapshotChunkPreparations.contains(key)) {
            stage.add(pendingSnapshotGenerationPermits.containsKey(key) ? "preparing(gen)" : "preparing");
        }
        Integer prepFailures = snapshotPreparationFailures.get(key);
        if (prepFailures != null) stage.add("prepFail=" + prepFailures);
        if (preparedActivationChunks.contains(key)) stage.add("admitted");
        if (chunkActivationDemands.containsKey(key)) {
            stage.add(activationTreeNeighborhoodReady(chunkX, chunkZ) ? "demand" : "demand(treeWait)");
        }
        if (pendingChunkActivationPlans.containsKey(key)) stage.add("planning");
        Integer planFailures = activationPlanningFailures.get(key);
        if (planFailures != null) stage.add("planFail=" + planFailures);
        if (preparedChunkActivations.containsKey(key)) stage.add("prepared");
        Integer position = fifoIndex.get(key);
        if (position != null) {
            stage.add("fifo#" + position + '('
                    + pendingChunkActivationApplications.get(key).stepLabel(welcomeTargets) + ')');
        }
        if (pendingStructureDecorations.containsKey(key)) stage.add("decoration");
        PendingFinalCarrierClaim claim = pendingFinalCarrierClaims.get(key);
        if (claim != null) stage.add("claim" + claim.remaining.size());
        StructureReadyPass pass = structureReadyPasses.get(key);
        if (pass != null) {
            String payload = !pass.payloadAwaiting ? ""
                    : pass.payloadWork == null ? "/payload-unsubmitted"
                    : pass.payloadWork.ready ? "/payload-ready" : "/payload-running";
            stage.add("pass@" + pass.laneIndex + payload + (pass.payloadInvalid ? "/invalid" : "")
                    + (pass.payloadOwnerLane != null ? "/owner=" + pass.payloadOwnerLane : ""));
        }
        if (deferredStructureReadyPasses.containsKey(key)) stage.add("deferredPass");
        if (deferredFinalCarrierRecoveries.containsKey(key)) stage.add("recoveryDeferred");
        ChunkRecoveryWork recovery = chunkRecoveryWorks.get(key);
        if (recovery != null) stage.add(recovery.ready ? "recovery-ready" : "recovery-running");
        if (deferredRecoveryLockRetries.containsKey(key)) stage.add("recoveryLockBackoff");
        if (deferredTickSchedulerActivations.containsKey(key)) stage.add("tickActDeferred");
        TickActivationPreparation tickPreparation = tickActivationPreparations.get(key);
        if (tickPreparation != null) {
            stage.add(tickPreparation.ready ? "tickPrep-ready" : "tickPrep-running");
        }
        if (snapshotStructureReadyChunks.contains(key)) stage.add("structReady");
        if (snapshotBaseReadyChunks.contains(key)) stage.add("baseReady");
        String send = sending.get(request.key);
        if (send != null) stage.add(send);
        return String.join("+", stage);
    }

    /**
     * WS handler는 요청만 넣습니다. 동일 session/chunk의 중복 요청은 하나로 합치되, 그 한 건은
     * successful binary delivery 전까지 유지합니다.
     */
    public void enqueueChunkSnapshotRequest(WebSocketSession session, int chunkX, int chunkZ) {
        enqueueChunkSnapshotRequest(session, chunkX, chunkZ, false);
    }

    private void enqueueChunkSnapshotRequest(WebSocketSession session, int chunkX, int chunkZ,
            boolean entryHalo) {
        if (terminalPhase != TerminalPhase.RUNNING || session == null) return;
        ChunkSnapshotRequest request = new ChunkSnapshotRequest(session, chunkX, chunkZ);
        request.entryHalo = entryHalo;
        ChunkSnapshotRequest existing = pendingChunkSnapshotRequests.putIfAbsent(request.key, request);
        if (existing == null) {
            indexPendingChunkSnapshotRequest(request);
            requeueChunkSnapshotRequest(request);
        } else if (entryHalo) {
            existing.entryHalo = true;
        }
    }

    /** 이미 화면 범위에서 빠진 session/chunk의 준비·송신 대기를 취소합니다. */
    public void cancelChunkSnapshotRequest(WebSocketSession session, int chunkX, int chunkZ) {
        if (session == null) return;
        ChunkSnapshotRequestKey key = new ChunkSnapshotRequestKey(session.getId(), chunkX, chunkZ);
        ChunkSnapshotRequest request = pendingChunkSnapshotRequests.get(key);
        if (request != null) completeChunkSnapshotRequest(request);
        if (ctx.broadcaster() != null) {
            ctx.broadcaster().cancelChunkSnapshotBarrier(session, chunkX, chunkZ);
        }
    }

    private void queueSnapshotPreparationRequest(ChunkSnapshotRequest request) {
        if (terminalPhase != TerminalPhase.RUNNING) return;
        if (pendingChunkSnapshotRequests.get(request.key) == request
                && queuedChunkSnapshotRequests.putIfAbsent(request.key, request) == null) {
            snapshotPreparationRequests.add(request);
        }
    }

    private void queueReadyChunkSnapshotRequest(ChunkSnapshotRequest request) {
        if (SNAPSHOT_STAGE_TRACE && request.traceReadyNanos == 0L) {
            request.traceReadyNanos = System.nanoTime();
        }
        if (terminalPhase != TerminalPhase.RUNNING) return;
        if (pendingChunkSnapshotRequests.get(request.key) == request
                && queuedChunkSnapshotRequests.putIfAbsent(request.key, request) == null) {
            readyChunkSnapshotRequests.add(request);
        }
    }

    private void requeueChunkSnapshotRequest(ChunkSnapshotRequest request) {
        if (SNAPSHOT_STAGE_TRACE) request.traceRequeues++;
        if (terminalPhase != TerminalPhase.RUNNING) {
            completeChunkSnapshotRequest(request);
            return;
        }
        if (pendingChunkSnapshotRequests.get(request.key) == request
                && canProcessChunkSnapshotRequest(request)) {
            queueSnapshotPreparationRequest(request);
        } else {
            queuedChunkSnapshotRequests.remove(request.key, request);
        }
    }

    private boolean canProcessChunkSnapshotRequest(ChunkSnapshotRequest request) {
        if (terminalPhase != TerminalPhase.RUNNING) return false;
        if (!started || players.isEmpty()) return true;
        PlayerTickState player = playerForConnection(
                entryNickname(request.session), request.session.getId());
        if (player == null) return false;
        PlayerPose pose = player.pose();
        int centerX = Math.floorDiv((int) Math.floor(pose.getX()), Blocks.CHUNK_X);
        int centerZ = Math.floorDiv((int) Math.floor(pose.getZ()), Blocks.CHUNK_Z);
        int radius = com.gameexpert.ws.handler.ChunkSnapshotWsHandler.MAX_STREAMING_CHUNK_DISTANCE;
        return Math.abs((long) request.chunkX - centerX) <= radius
                && Math.abs((long) request.chunkZ - centerZ) <= radius;
    }

    private boolean requiresDetachedSnapshotPresentation(ChunkSnapshotRequest request) {
        return started && !players.isEmpty()
                && !activeSimulationChunks.contains(chunkKey(request.chunkX, request.chunkZ));
    }

    private boolean hasActivePendingChunkSnapshotRequest() {
        boolean pending = false;
        for (ChunkSnapshotRequest request : pendingChunkSnapshotRequests.values()) {
            if (!request.session.isOpen()) {
                completeChunkSnapshotRequest(request);
            } else if (!requiresDetachedSnapshotPresentation(request)
                    && canProcessChunkSnapshotRequest(request)) {
                pending = true;
            }
        }
        return pending;
    }

    private void refreshChunkSnapshotRequestActivity() {
        for (ChunkSnapshotRequest request : pendingChunkSnapshotRequests.values()) {
            if (!request.session.isOpen()) {
                completeChunkSnapshotRequest(request);
            } else if (canProcessChunkSnapshotRequest(request)) {
                requeueChunkSnapshotRequest(request);
            } else {
                queuedChunkSnapshotRequests.remove(request.key, request);
                long key = chunkKey(request.chunkX, request.chunkZ);
                if (!hasProcessablePendingSnapshotRequest(key)) preparedPresentationSnapshots.remove(key);
            }
        }
    }

    private void completeChunkSnapshotRequest(ChunkSnapshotRequest request) {
        pendingChunkSnapshotRequests.remove(request.key, request);
        unindexPendingChunkSnapshotRequest(request);
        queuedChunkSnapshotRequests.remove(request.key, request);
        long key = chunkKey(request.chunkX, request.chunkZ);
        if (!pendingChunkSnapshotRequestsByChunk.containsKey(key)) {
            preparedPresentationSnapshots.remove(key);
        }
    }

    /**
     * Adds one request to its chunk's bucket, atomically with the bucket's own lifetime.
     *
     * <p>Both this and {@link #unindexPendingChunkSnapshotRequest} mutate the bucket <i>inside</i>
     * the outer map's per-key update, never around it. Publishing the bucket first and filling it
     * afterwards leaves a window in which the last removal for that chunk sees an empty bucket and
     * drops it, taking this request's index entry with it. The request then exists in
     * {@code pendingChunkSnapshotRequests} while {@link #pendingChunkSnapshotRequestsFor} reports
     * nothing for its coordinate, so the chunk it is waiting on never wakes it.</p>
     */
    private void indexPendingChunkSnapshotRequest(ChunkSnapshotRequest request) {
        pendingChunkSnapshotRequestsByChunk.compute(chunkKey(request.chunkX, request.chunkZ),
                (key, bucket) -> {
                    ConcurrentHashMap<ChunkSnapshotRequestKey, ChunkSnapshotRequest> resident =
                            bucket == null ? new ConcurrentHashMap<>() : bucket;
                    resident.put(request.key, request);
                    return resident;
                });
    }

    /** Removes one request, dropping its chunk's bucket only if it is still empty on return. */
    private void unindexPendingChunkSnapshotRequest(ChunkSnapshotRequest request) {
        pendingChunkSnapshotRequestsByChunk.computeIfPresent(
                chunkKey(request.chunkX, request.chunkZ),
                (key, bucket) -> {
                    bucket.remove(request.key, request);
                    return bucket.isEmpty() ? null : bucket;
                });
    }

    /** Pending requests for exactly one chunk, without scanning every other chunk's requests. */
    private Collection<ChunkSnapshotRequest> pendingChunkSnapshotRequestsFor(int chunkX, int chunkZ) {
        ConcurrentHashMap<ChunkSnapshotRequestKey, ChunkSnapshotRequest> bucket =
                pendingChunkSnapshotRequestsByChunk.get(chunkKey(chunkX, chunkZ));
        return bucket == null ? List.of() : bucket.values();
    }

    int activationWorkCountForTest() {
        return chunkActivationDemands.size() + preparedChunkActivations.size()
                + pendingChunkActivationApplications.size() + pendingChunkActivationPlans.size()
                + preparedActivationChunks.size();
    }

    long snapshotFrameEncodeCountForTest() {
        return snapshotFrameEncodeCount.get();
    }

    int snapshotFrameCacheEntryCountForTest() {
        return snapshotFrameCache.size();
    }

    int pendingChunkSnapshotRequestCountForTest() {
        return pendingChunkSnapshotRequests.size();
    }

    void setSnapshotSendStallMillisForTest(long millis) {
        snapshotSendStallNanos = TimeUnit.MILLISECONDS.toNanos(millis);
    }

    /** world tick owner 전용: resident local array+overlay만 scan하고 generation/DB/activation을 하지 않습니다. */
    private SnapshotCells copyResidentSnapshotCells(int chunkX, int chunkZ) {
        if (!accessor.isChunkResident(chunkX, chunkZ)) return null;
        TerrainAccessor.SnapshotSource source = accessor.snapshotSource(chunkX, chunkZ);
        if (source == null) return null;
        short[] blockTypes = new short[Blocks.CHUNK_BLOCKS];
        byte[] blockStates = new byte[Blocks.CHUNK_BLOCKS];
        short[] surfaceHeights = new short[Blocks.CHUNK_X * Blocks.CHUNK_Z];
        short[] terrainSurfaceHeights = new short[Blocks.CHUNK_X * Blocks.CHUNK_Z];
        java.util.Arrays.fill(surfaceHeights, (short) (Blocks.MIN_Y - 1));
        for (int localZ = 0; localZ < Blocks.CHUNK_Z; localZ++) {
            for (int localX = 0; localX < Blocks.CHUNK_X; localX++) {
                terrainSurfaceHeights[localX + localZ * Blocks.CHUNK_X] =
                        (short) source.terrainSurfaceHeightAt(localX, localZ);
            }
        }
        accessor.scanLoadedChunk(chunkX, chunkZ, (x, y, z, blockType) -> {
            int localX = Math.floorMod(x, Blocks.CHUNK_X);
            int localZ = Math.floorMod(z, Blocks.CHUNK_Z);
            int index = Blocks.blockIndex(localX, y, localZ);
            blockTypes[index] = (short) blockType;
            // [WIRE-STATE] 권위 상태를 그대로 싣는다. 엔진이 직접 쓴 칸(명시적 0 포함)은 엔진
            // 바이트, 나머지 생성 칸은 generatedCarrierState 가 carrier exact state 를 엔진 의미
            // 바이트로 투영한 값이다 — 전송 바이트의 이름공간은 하나뿐이다(CONTRACT §4.1).
            blockStates[index] = (byte) blockState(x, y, z, blockType);
            if (blockType != Blocks.AIR) {
                surfaceHeights[localX + localZ * Blocks.CHUNK_X] = (short) y;
            }
        });
        // scan 직후 같은 tick mutation이 끼어들 수 없으므로 toVersion과 copied cells는 같은 instant다.
        List<GeneratedDecoratedPotRuntime> decoratedPots = generatedTrialDecoratedPotsInChunk(chunkX, chunkZ);
        return new SnapshotCells(chunkX, chunkZ, chunkVersions.current(chunkX, chunkZ),
                surfaceHeights, terrainSurfaceHeights, blockTypes, blockStates, decoratedPots);
    }

    /**
     * Immutable source와 엔진이 명시적으로 쓴 sparse overlay 상태를 owner에서 고정합니다.
     * encoder worker가 TerrainAccessor나 BlockStateStorage를 동시에 읽지 않고 cell scan과 codec을 수행합니다.
     */
    private SnapshotReference captureSnapshotReference(int chunkX, int chunkZ) {
        return captureSnapshotReference(chunkX, chunkZ, false);
    }

    private SnapshotReference captureSnapshotReference(int chunkX, int chunkZ, boolean presentation) {
        WorldWorkEvent work = architectureWork("snapshot-source-capture", true, chunkX, chunkZ,
                activeChunkGeneration(chunkKey(chunkX, chunkZ)));
        if (architectureTurn != null) architectureTurn.snapshotCaptureAttempts++;
        try {
            PreparedSnapshotChunk prepared = presentation
                    ? preparedPresentationSnapshots.get(chunkKey(chunkX, chunkZ)) : null;
            if (presentation && prepared == null) return null;
            TerrainAccessor.SnapshotSource source = prepared == null
                    ? accessor.snapshotSource(chunkX, chunkZ)
                    : accessor.snapshotSourceForPrepared(prepared.chunk, prepared.presentation.patch);
            if (source == null) return null;
            List<GeneratedDecoratedPotRuntime> decoratedPots = prepared == null
                    ? generatedTrialDecoratedPotsInChunk(chunkX, chunkZ)
                    : prepared.presentation.decoratedPots.stream()
                            .filter(pot -> source.blockTypeAt(packedBlockIndex(pot)) == Blocks.DECORATED_POT
                                    && !source.hasExplicitStateAt(packedBlockIndex(pot)))
                            .sorted(java.util.Comparator.comparingInt(this::packedBlockIndex))
                            .toList();
            SnapshotReference reference = new SnapshotReference(source,
                    chunkVersions.current(chunkX, chunkZ), decoratedPots, captureExplicitStates(source),
                    presentation);
            if (architectureTurn != null) architectureTurn.snapshotCaptures++;
            if (work != null) work.workUnits = 1L;
            return reference;
        } catch (RuntimeException | Error failure) {
            WorldWorkEvent.outcome(work, "failed");
            throw failure;
        } finally {
            WorldWorkEvent.finish(work);
        }
    }

    /** resident cells의 biome metadata 계산은 world state를 읽지 않아 sender worker에서도 안전합니다. */
    private ChunkSnapshot completeSnapshot(SnapshotCells cells) {
        byte[] surfaceBiomes = new byte[Blocks.CHUNK_X * Blocks.CHUNK_Z];
        int baseX = cells.chunkX * Blocks.CHUNK_X;
        int baseZ = cells.chunkZ * Blocks.CHUNK_Z;
        for (int localZ = 0; localZ < Blocks.CHUNK_Z; localZ++) {
            for (int localX = 0; localX < Blocks.CHUNK_X; localX++) {
                int column = localX + localZ * Blocks.CHUNK_X;
                // Biome identity is climate/worldgen metadata, not an inferred block material.
                // Reuse the generated terrain height captured with this immutable snapshot.
                surfaceBiomes[column] = (byte) surfaceDecorator.surfaceBiome(
                        baseX + localX, cells.terrainSurfaceHeights[column], baseZ + localZ);
            }
        }
        // Full snapshots replace complete history, so their covered range is [0, toVersion].
        return ChunkSnapshot.takeOwnership(worldEpoch, cells.chunkX, cells.chunkZ, 0, cells.toVersion,
                surfaceBiomes, cells.surfaceHeights, cells.blockTypes, cells.blockStates,
                decoratedPotMotif(cells.decoratedPots, cells.blockTypes));
    }


    /**
     * carrier 가 심은 exact state 를 <b>엔진 의미 state 바이트</b>로 투영한다(CONTRACT §4.1).
     *
     * <p>전송 바이트에는 이름공간이 하나만 있어야 한다. 엔진 규칙이 직접 쓴 칸은 이미 엔진
     * 비트필드이고, 나머지 칸은 코드북 서수라 클라이언트가 읽을 수 없다 — 그 서수를 여기서
     * 형상 비트 + waterlogged 비트 7 로 바꾼다.
     *
     * <p>대상은 carrier 의 sparse state override 뿐이다. 이미 값이 있는 칸(엔진 소유), 엔진이
     * 명시적으로 상태를 쓴 칸(0 포함 — owner 가 고정한 {@code explicitStates})과 블록이 교체된
     * 칸(carrier 의 blockId 와 현재 blockType 이 다른 칸)은 건드리지 않는다.
     */
    private static void projectCarrierStates(TerrainAccessor.SnapshotSource source,
            short[] blockTypes, byte[] blockStates, Map<Integer, Integer> explicitStates) {
        var carrier = source.finalLiveCarrier().orElse(null);
        if (carrier == null) return;
        for (Map.Entry<Integer, NeutralFinalChunk.StateOverride> entry
                : carrier.stateOverrides().entrySet()) {
            int index = entry.getKey();
            if (index < 0 || index >= Blocks.CHUNK_BLOCKS) continue;
            var state = entry.getValue();
            if (Short.toUnsignedInt(blockTypes[index]) != state.blockId()) continue;
            if (com.gameexpert.engine.blocks.CandleRules.isCandle(state.blockId())) continue;
            if (explicitStates.containsKey(index) || source.hasExplicitStateAt(index)) continue;
            if (blockStates[index] != 0
                    && Byte.toUnsignedInt(blockStates[index])
                            != state.stateCode()) {
                // 엔진(패치·override)이 그 칸의 상태를 이미 소유했다.
                continue;
            }
            blockStates[index] = (byte) CarrierStateProjection.projectState(state.blockId(), state);
        }
    }

    /**
     * sparse 평면에 없는 생성 칸(carrier 코드 0)의 엔진 바이트를 심는다. {@link #projectCarrierStates}
     * 의 코드 0 짝이며 {@link #generatedCarrierState} 와 같은 규칙이다 — 엔진이 명시적으로 쓴 칸(0
     * 포함)과 블록이 교체된 칸은 건드리지 않는다. 호출자가 코드 0 투영이 0 이 아닌 블록만 넘긴다.
     */
    private static void projectCarrierDefaultState(TerrainAccessor.SnapshotSource source, int index,
            int id, byte[] blockStates, Map<Integer, Integer> explicitStates) {
        if (source.generatedBlockTypeAt(index) != id || explicitStates.containsKey(index)
                || source.hasExplicitStateAt(index)) return;
        var carrier = source.finalLiveCarrier().orElse(null);
        if (carrier == null || carrier.stateOverrides().containsKey(index)) return;
        blockStates[index] = (byte) CarrierStateProjection.projectState(id, carrier.defaultState(id));
    }

    /** 월드 owner가 넘긴 immutable resident reference를 전용 encoder worker에서 완성합니다. */
    private ChunkSnapshot completeSnapshot(SnapshotReference reference) {
        TerrainAccessor.SnapshotSource source = reference.source;
        short[] blockTypes = new short[Blocks.CHUNK_BLOCKS];
        byte[] blockStates = new byte[Blocks.CHUNK_BLOCKS];
        short[] surfaceHeights = new short[Blocks.CHUNK_X * Blocks.CHUNK_Z];
        java.util.Arrays.fill(surfaceHeights, (short) (Blocks.MIN_Y - 1));
        source.copyCellsTo(blockTypes, blockStates);
        projectCarrierStates(source, blockTypes, blockStates, reference.explicitStates);
        for (int y = Blocks.MIN_Y; y <= Blocks.MAX_Y; y++) {
            for (int localZ = 0; localZ < Blocks.CHUNK_Z; localZ++) {
                for (int localX = 0; localX < Blocks.CHUNK_X; localX++) {
                    int index = Blocks.blockIndex(localX, y, localZ);
                    int id = Short.toUnsignedInt(blockTypes[index]);
                    if (com.gameexpert.engine.blocks.CandleRules.isCandle(id)) {
                        Integer explicit = reference.explicitStates.get(index);
                        if (explicit != null) blockStates[index] = (byte) (int) explicit;
                        else if (source.generatedBlockTypeAt(index) == id
                                && !source.hasExplicitStateAt(index)) {
                            var carrier = source.finalLiveCarrier().orElse(null);
                            if (carrier != null) {
                                var exact = carrier.stateOverrides().getOrDefault(index,
                                        carrier.defaultState(id));
                                blockStates[index] = (byte) CarrierStateProjection.projectState(id, exact);
                            }
                        }
                    } else if (blockStates[index] == 0) {
                        projectCarrierDefaultState(source, index, id, blockStates,
                                reference.explicitStates);
                    }
                    if (id != Blocks.AIR) {
                        surfaceHeights[localX + localZ * Blocks.CHUNK_X] = (short) y;
                    }
                }
            }
        }
        byte[] surfaceBiomes = new byte[Blocks.CHUNK_X * Blocks.CHUNK_Z];
        int baseX = source.chunkX() * Blocks.CHUNK_X;
        int baseZ = source.chunkZ() * Blocks.CHUNK_Z;
        for (int localZ = 0; localZ < Blocks.CHUNK_Z; localZ++) {
            for (int localX = 0; localX < Blocks.CHUNK_X; localX++) {
                int column = localX + localZ * Blocks.CHUNK_X;
                surfaceBiomes[column] = (byte) surfaceDecorator.surfaceBiome(
                        baseX + localX, source.terrainSurfaceHeightAt(localX, localZ),
                        baseZ + localZ);
            }
        }
        return ChunkSnapshot.takeOwnership(worldEpoch, source.chunkX(), source.chunkZ(), 0, reference.toVersion,
                surfaceBiomes, surfaceHeights, blockTypes, blockStates,
                decoratedPotMotif(reference.decoratedPots, blockTypes));
    }

    /** Converts the owner-captured Trial BENT projections to the fixed v3 wire declaration subset. */
    private static ChunkSnapshot.DecoratedPotMotif decoratedPotMotif(
            List<GeneratedDecoratedPotRuntime> decoratedPots, short[] blockTypes) {
        if (decoratedPots.isEmpty()) return ChunkSnapshot.DecoratedPotMotif.empty();
        List<String> declaration = ChunkSnapshot.DecoratedPotMotif.declaration();
        List<String> keys = new ArrayList<>(declaration.size());
        for (String key : declaration) {
            if (decoratedPots.stream().anyMatch(pot -> decoratedPotUses(pot.projection(), key))) keys.add(key);
        }
        int[] packed = new int[decoratedPots.size()];
        byte[] faces = new byte[Math.multiplyExact(decoratedPots.size(), 4)];
        for (int index = 0; index < decoratedPots.size(); index++) {
            GeneratedDecoratedPotRuntime pot = decoratedPots.get(index);
            packed[index] = Blocks.blockIndex(Math.floorMod(pot.x(), Blocks.CHUNK_X), pot.y(),
                    Math.floorMod(pot.z(), Blocks.CHUNK_Z));
            FinalCarrierBlockEntityPlan.DecoratedPotProjection projection = pot.projection();
            String[] values = {projection.back(), projection.left(), projection.right(), projection.front()};
            for (int face = 0; face < values.length; face++) {
                int keyIndex = keys.indexOf(values[face]);
                if (keyIndex < 0) throw new IllegalArgumentException("Trial decorated pot motif key is unsupported");
                faces[index * 4 + face] = (byte) keyIndex;
            }
        }
        return new ChunkSnapshot.DecoratedPotMotif(keys, packed, faces);
    }

    private static boolean decoratedPotUses(FinalCarrierBlockEntityPlan.DecoratedPotProjection projection,
            String key) {
        return key.equals(projection.back()) || key.equals(projection.left())
                || key.equals(projection.right()) || key.equals(projection.front());
    }

    /**
     * WS 스레드가 액션을 큐에 넣습니다. 큐 상한을 넘기면 조용히 버리지 않고
     * {@link ActionQueueOverflowException}을 던져 호출자가 클라이언트에 거부를 알리게 합니다.
     */
    void enqueue(PlayerAction action) {
        if (!ownerTurnMayContinue() || action == null) return;
        if (!actionQueue.enqueue(action)) {
            throw new ActionQueueOverflowException(
                    "액션 큐 상한 초과: nickname=" + action.nickname());
        }
        // 이동은 매 프레임 오므로 틱이 모아 처리한다. 그 밖의 입력은 소유 스레드의 즉시 턴이 맡는다.
        if (!(action instanceof PlayerAction.Move)) requestPromptActionTurn();
    }

    private final AtomicBoolean runtimeFallingContinuationScheduled = new AtomicBoolean();

    /** Resume a completed falling writer immediately on the existing serialized owner queue. */
    void requestRuntimeFallingContinuation() {
        if (!started || terminalPhase != TerminalPhase.RUNNING
                || !runtimeFallingContinuationScheduled.compareAndSet(false, true)) return;
        if (!enqueueOwnerTask(() -> {
            runtimeFallingContinuationScheduled.set(false);
            if (!ownerTurnMayContinue()) return;
            runtimeFallingSpeleothems.retryBeforePrelude();
            tickLoop.flushRuntimeFallingPublication();
            fallingSpeleothems.broadcast();
        })) runtimeFallingContinuationScheduled.set(false);
    }

    private final AtomicBoolean promptActionTurnScheduled = new AtomicBoolean();

    /** Coalesced owner continuation that applies queued input between ticks. */
    private void requestPromptActionTurn() {
        if (!started || terminalPhase != TerminalPhase.RUNNING
                || !promptActionTurnScheduled.compareAndSet(false, true)) return;
        if (!enqueueOwnerTask(() -> {
            // Input arriving while this turn runs must be able to schedule the next one.
            promptActionTurnScheduled.set(false);
            if (!started || terminalPhase != TerminalPhase.RUNNING) return;
            try {
                tickLoop.runPromptActions();
            } catch (RuntimeException failure) {
                // 소유 실행기는 태스크의 결과를 읽지 않아 예외가 조용히 사라진다. 액션 하나의 실패는
                // 이미 액션 단위로 기록되므로, 여기까지 오는 것은 입력 턴 자체의 실패다.
                log.error("틱 사이 입력 처리 실패: world={}", worldId, failure);
            }
        })) {
            promptActionTurnScheduled.set(false);
        }
    }

    // ── 실행기가 호출하는 진입점 ──
    void runTick() {
        markOwnerThread();
        if (!ownerTurnMayContinue()) return;
        final long ownerTurnStartNanos = System.nanoTime();
        final long performanceSchedulerLatenessNanos =
                tickLoop.beginPerformanceOwnerTurn(ownerTurnStartNanos);
        WorldTickLoop.TickTiming timing = tickTiming;
        timing.reset();
        architectureTurn = ArchitectureTurnEvent.begin(worldId, ownerTurnStartNanos,
                architectureScheduleAnchorKnown ? architectureNextTurnDeadlineNanos : 0L);
        if (architectureTurn != null) {
            if (architectureScheduleAnchorKnown) {
                architectureNextTurnDeadlineNanos += WorldTickLoop.TICK_BUDGET_NANOS;
            }
            captureArchitectureQueues(architectureTurn, true);
        } else {
            architectureNextTurnDeadlineNanos = 0L;
            architectureScheduleAnchorKnown = false;
        }
        if (architectureTurn != null || WorldWorkEvent.enabled()) {
            architectureOwnerTurnStartNanos = ownerTurnStartNanos;
        }
        boolean telemetryStarted = false;
        try {
            timing.beginPhase(WorldTickLoop.TickTiming.SAFETY, System.nanoTime());
            try {
                TickSafetyTelemetry.beginTick();
                telemetryStarted = true;
            } finally {
                timing.endPhase(System.nanoTime());
            }
            tickLoop.runTick(timing);
            if (!ownerTurnMayContinue() || runtimeFallingSpeleothems.pausesSimulation()) return;
            TickSafetyTelemetry.phase(TickSafetyTelemetry.Phase.OTHER);
            // Establish snapshot-preparation ownership before proximity scheduling, so a requested visible chunk
            // cannot be admitted to both generation lanes in this same owner turn.
            timing.beginPhase(WorldTickLoop.TickTiming.SNAPSHOT_PREPARATION, System.nanoTime());
            drainSnapshotPreparationRequests();
            timing.endPhase(System.nanoTime());
            if (!ownerTurnMayContinue()) return;
            timing.beginPhase(WorldTickLoop.TickTiming.TREE_PLANNING, System.nanoTime());
            scheduleMissingTreePlanningSources();
            timing.endPhase(System.nanoTime());
            if (!ownerTurnMayContinue()) return;
            // move/respawn/텔레포트가 반영된 바로 그 틱의 현재 좌표를 준비 워커에 전달한다.
            timing.beginPhase(WorldTickLoop.TickTiming.PROXIMITY, System.nanoTime());
            schedulePlayerProximityPreparations();
            timing.endPhase(System.nanoTime());
            if (!ownerTurnMayContinue()) return;
            timing.beginPhase(WorldTickLoop.TickTiming.CHUNK_DEMAND, System.nanoTime());
            drainPreparedChunkDemands();
            timing.endPhase(System.nanoTime());
            if (!ownerTurnMayContinue()) return;
            // 계측 창 안에서도 요구를 worker에 handoff만 한다. 동기 활성화·스캔은 금지한다.
            timing.beginPhase(WorldTickLoop.TickTiming.CHUNK_ADMISSION, System.nanoTime());
            long admissionStartNanos = System.nanoTime();
            drainChunkActivationDemandsWithinBudget();
            long admissionDemandNanos = System.nanoTime();
            drainPreparedChunkActivationsWithinBudget();
            long admissionEndNanos = System.nanoTime();
            // [임시 계측] 청크승인이 2ms 예산 두 개로 묶여 있는데도 유휴 구간에서 최대 344ms 를
            // 먹는다. 어느 쪽이 넘기는지 증거 없이 고치려다 한 번 실패했으므로, 원인을 지목할
            // 때까지만 둘을 갈라 남긴다.
            if (admissionEndNanos - admissionStartNanos > 50_000_000L) {
                log.warn("청크승인 분해: world={} 수요드레인={}ms 준비활성화={}ms 수요큐={} 활성화큐={}",
                        worldId, (admissionDemandNanos - admissionStartNanos) / 1_000_000L,
                        (admissionEndNanos - admissionDemandNanos) / 1_000_000L,
                        chunkActivationDemands.size(),
                        pendingChunkActivationApplications.size());
            }
            timing.endPhase(System.nanoTime());
            if (!ownerTurnMayContinue()) return;
            timing.beginPhase(WorldTickLoop.TickTiming.DECORATION, System.nanoTime());
            runDecorationPhase();
            timing.endPhase(System.nanoTime());
            if (!ownerTurnMayContinue()) return;
            timing.beginPhase(WorldTickLoop.TickTiming.PREPARED_SNAPSHOT, System.nanoTime());
            drainPreparedSnapshotChunks();
            timing.endPhase(System.nanoTime());
            if (!ownerTurnMayContinue()) return;
            timing.beginPhase(WorldTickLoop.TickTiming.SNAPSHOT_DELIVERY, System.nanoTime());
            drainChunkSnapshotRequests();
            timing.endPhase(System.nanoTime());
            if (!ownerTurnMayContinue()) return;
            timing.beginPhase(WorldTickLoop.TickTiming.SNAPSHOT_DEADLINE, System.nanoTime());
            enforceSnapshotSendDeadlines();
            if (ownerTurnMayContinue()) runEntryWatchdog();
            timing.endPhase(System.nanoTime());
        } catch (Throwable error) {
            timing.markExceptionalTurn();
            String failedPhase = timing.activePhaseLabel();
            timing.endPhase(System.nanoTime());
            log.error("월드 {} owner turn 처리 오류: 단계={}", worldId, failedPhase, error);
        } finally {
            timing.beginPhase(WorldTickLoop.TickTiming.SAFETY, System.nanoTime());
            try {
                if (telemetryStarted) verifyTickSafetyInvariant();
            } catch (Throwable error) {
                timing.markExceptionalTurn();
                log.error("월드 {} owner turn 틱 안전 검증 오류", worldId, error);
            } finally {
                try {
                    if (telemetryStarted) TickSafetyTelemetry.endTick();
                } catch (Throwable error) {
                    timing.markExceptionalTurn();
                    log.error("월드 {} owner turn 틱 안전 계측 종료 오류", worldId, error);
                } finally {
                    timing.endPhase(System.nanoTime());
                }
            }
            long ownerTurnEndNanos = System.nanoTime();
            if (architectureTurn != null) architectureTurn.captureCpuEnd();
            long elapsedNanos = ownerTurnEndNanos - ownerTurnStartNanos;
            com.gameexpert.engine.diagnostics.OwnerTurnEvent.commitTurn(
                    worldId, ownerTurnStartNanos, ownerTurnEndNanos, timing.exceptionalTurn());
            if (architectureTurn != null) {
                architectureTurn.tickNo = tickNo;
                timing.writeArchitecturePhases(architectureTurn, elapsedNanos);
                captureArchitectureQueues(architectureTurn, false);
                architectureTurn.finish(ownerTurnEndNanos, timing.exceptionalTurn());
                architectureTurn = null;
            }
            architectureOwnerTurnStartNanos = 0L;
            if (WorldTickLoop.exceedsTickBudget(elapsedNanos)) {
                tickLoop.logSlowOwnerTurn(timing, elapsedNanos);
            }
            tickLoop.completePerformanceOwnerTurn(
                    timing, elapsedNanos, performanceSchedulerLatenessNanos);
        }
    }

    ArchitectureTurnEvent architectureTurn() { return architectureTurn; }

    private WorldWorkEvent architectureSubmitted(String kind, boolean hasChunk,
            int chunkX, int chunkZ, long generation) {
        if (!WorldWorkEvent.enabled()) return null;
        return WorldWorkEvent.submitted(worldId, isOwnerThread() ? architectureOwnerTurnStartNanos : 0L,
                kind, hasChunk, chunkX, chunkZ, generation);
    }

    private WorldWorkEvent architectureWork(String kind, boolean hasChunk,
            int chunkX, int chunkZ, long generation) {
        if (!WorldWorkEvent.enabled()) return null;
        return WorldWorkEvent.begin(worldId, isOwnerThread() ? architectureOwnerTurnStartNanos : 0L,
                kind, hasChunk, chunkX, chunkZ, generation);
    }

    /** Only existing constant-time size counters: never traverse a concurrent completion queue for diagnostics. */
    private void captureArchitectureQueues(ArchitectureTurnEvent event, boolean start) {
        if (start) {
            event.actionQueueStart = actionQueue.size();
            event.snapshotRequestsStart = pendingChunkSnapshotRequests.size();
            event.snapshotPreparationsStart = pendingSnapshotChunkPreparations.size();
            event.activationDemandsStart = chunkActivationDemands.size();
            event.preparedActivationsStart = preparedChunkActivations.size();
            event.activationApplicationsStart = pendingChunkActivationApplications.size();
            event.pendingLiveUpdatesStart = pendingLiveBlockUpdates.size();
            event.scheduledTicksStart = finalCarrierTickScheduler == null ? 0 : finalCarrierTickScheduler.pendingCount();
        } else {
            event.players = players.size();
            event.activeSimulationChunks = activeSimulationChunks.size();
            event.actionQueueEnd = actionQueue.size();
            event.snapshotRequestsEnd = pendingChunkSnapshotRequests.size();
            event.snapshotPreparationsEnd = pendingSnapshotChunkPreparations.size();
            event.activationDemandsEnd = chunkActivationDemands.size();
            event.preparedActivationsEnd = preparedChunkActivations.size();
            event.activationApplicationsEnd = pendingChunkActivationApplications.size();
            event.pendingLiveUpdatesEnd = pendingLiveBlockUpdates.size();
            event.scheduledTicksEnd = finalCarrierTickScheduler == null ? 0 : finalCarrierTickScheduler.pendingCount();
        }
    }

    private void verifyTickSafetyInvariant() {
        long generationDelta = TickSafetyTelemetry.currentTickCount(
                TickSafetyTelemetry.Event.TERRAIN_GENERATION);
        long persistenceDelta = TickSafetyTelemetry.currentTickCount(
                TickSafetyTelemetry.Event.PERSISTENCE_READ);
        long activationDelta = TickSafetyTelemetry.currentTickCount(
                TickSafetyTelemetry.Event.BLOCK_READ_ACTIVATION);
        long planningDelta = TickSafetyTelemetry.currentTickCount(
                TickSafetyTelemetry.Event.STRUCTURE_PLANNING);
        long scanDelta = TickSafetyTelemetry.currentTickCount(
                TickSafetyTelemetry.Event.FULL_CHUNK_SCAN);
        long waitDelta = TickSafetyTelemetry.currentTickCount(
                TickSafetyTelemetry.Event.BLOCKING_WAIT);
        long sendDelta = TickSafetyTelemetry.currentTickCount(
                TickSafetyTelemetry.Event.SYNCHRONOUS_SEND_CALL);
        if ((generationDelta | persistenceDelta | activationDelta | planningDelta
                | scanDelta | waitDelta | sendDelta) != 0) {
            log.error("틱 안전 불변식 위반: world={}, 생성={}, DB읽기={}, 읽기활성화={}, 계획={}, 전체스캔={}, 대기={}, 동기전송={}",
                    worldId, generationDelta, persistenceDelta, activationDelta, planningDelta,
                    scanDelta, waitDelta, sendDelta);
        }
    }

    /** 틱 전체 계측 창 안에서 실행해야 하는 이전 선행 작업입니다. */
    void runTickPrelude() {
        markOwnerThread();
        finalCarrierWorldLockBusyThisTurn = false;
        if (!ownerTurnMayContinue()) return;
        drainCommittedGeneratedCushionMutations();
        if (!ownerTurnMayContinue()) return;
        retryPendingGeneratedCushionRiderCleanups();
        if (!ownerTurnMayContinue()) return;
        drainMapPersistenceCompletions();
        if (!ownerTurnMayContinue()) return;
        drainPersistenceCompletions();
        if (!ownerTurnMayContinue()) return;
        pumpCanonicalLootOpenRetries();
        if (!ownerTurnMayContinue()) return;
        drainFinalCarrierTickAdmissionCompletions();
        if (!ownerTurnMayContinue()) return;
        retryDeferredStructureReadyPasses();
        if (!ownerTurnMayContinue()) return;
        reportFinalCarrierPayloadBacklog();
        if (!ownerTurnMayContinue()) return;
        retryDeferredTickSchedulerActivations();
        if (!ownerTurnMayContinue()) return;
        retryDeferredFinalCarrierRecoveries();
        if (!ownerTurnMayContinue()) return;
        submitPendingFinalCarrierTickAdmissions();
        if (!ownerTurnMayContinue()) return;
        drainFinalCarrierTicks();
        if (!ownerTurnMayContinue()) return;
        if (ctx.persistenceExecutor() != null) {
            tickLoop.pumpAnimalSettlementPersistence();
            if (!ownerTurnMayContinue()) return;
            mobSystem.retryAnimalSettlementPersistenceForDisposal();
            if (!ownerTurnMayContinue()) return;
            // The animal lanes may complete synchronously in a direct fixture or enqueue a
            // writer callback while this prelude is still running. Give those callbacks the same
            // owner turn, while the existing batch freeze still defers callbacks fed by callbacks.
            drainPersistenceCompletions();
            if (!ownerTurnMayContinue()) return;
        }
        retryPendingSignPersistence();
        if (!ownerTurnMayContinue()) return;
        try {
            breakLilyPadsHitByBoats();
        } catch (Throwable collisionError) {
            // 부가 충돌 판정 하나가 실패해도 scheduler까지 예외가 새어 월드 틱 전체가 멈추면 안 된다.
            log.warn("보트-수련잎 충돌 처리 실패: world={}", worldId, collisionError);
        }
    }

    private void drainFinalCarrierTicks() {
        FinalCarrierTickScheduler scheduler = finalCarrierTickScheduler;
        if (scheduler == null) return;
        try {
            // The core owns BLOCK-before-FLUID ordering, per-lane durable order, and the 65,536 lane cap.
            // Each settled row is one durable transaction, and a first-generation near field makes tens of
            // thousands of canonical rows due in the same owner turn, so the owner only lends this phase a
            // fixed wall-clock slice. Deferred rows keep their absolute due tick and their exact order.
            FinalCarrierTickScheduler.DrainReport report = scheduler.drainDueReport(
                    clock.gameTimeMcTicks(), finalCarrierTickLiveTypes,
                    finalCarrierTickSemantics, finalCarrierTickMutationSink,
                    FinalCarrierTickScheduler.MAX_PENDING_TICKS,
                    System.nanoTime() + FINAL_CARRIER_TICK_DRAIN_BUDGET_NANOS);
            synchronized (this) {
                finalCarrierDrainStatuses.add(report.status());
            }
            if (report.status() != FinalCarrierTickScheduler.DrainStatus.DRAINED) {
                log.debug("Final-carrier scheduled-tick drain deferred for world {}: status={}, processed={}",
                        worldId, report.status(), report.processed());
            }
        } catch (FinalCarrierTickPublicationException failure) {
            lastFinalCarrierTickPublicationOutcome = failure.outcome();
            // The scheduler intentionally retains the exact pending row when the committed sink
            // does not return normally. The next owner turn retries the cached mutation or lets
            // recovery consume it; never acknowledge a partially applied block list here.
            log.error("Final-carrier scheduled-tick publication did not commit for world {}",
                    worldId, failure);
        } catch (Throwable failure) {
            // A durable settlement retry/failure must leave the row at the head without aborting unrelated world
            // phases. The transaction owner decides RETRY for unknown commit outcomes.
            // 월드 시계 체크포인트가 같은 행을 자주 잠가 재시도 가능한 실패는 정상적으로 반복된다.
            // 매번 스택을 찍으면 로그가 폭주해 진짜 신호가 묻히므로, 재시도 가능한 실패는 스택 없이
            // 60초에 한 번만 요약하고 그동안 억제한 횟수를 함께 남긴다. 재시도 불가 실패는 그대로 남긴다.
            if (failure instanceof com.gameexpert.engine.persistence.finalcarrier
                    .FinalCarrierPersistenceService.FinalCarrierLaneRetryable) {
                long suppressed = suppressedSettlementFailures;
                if (shouldLogRetryableSettlementFailure()) {
                    suppressedSettlementFailures = 0L;
                    log.warn("Final-carrier scheduled-tick settlement deferred for world {}: {}"
                            + " (지난 60초 동안 같은 사유로 {}건 생략, 다음 틱에 재시도)",
                            worldId, failure.toString(), suppressed);
                } else {
                    suppressedSettlementFailures = suppressed + 1L;
                }
            } else {
                log.error("Final-carrier scheduled-tick settlement failed for world {}",
                        worldId, failure);
            }
        }
    }

    /** 재시도 가능한 정산 실패 로그의 월드별 60초 게이트와 그동안 억제한 건수. */
    private long lastSettlementFailureLogNanos;
    private long suppressedSettlementFailures;

    private boolean shouldLogRetryableSettlementFailure() {
        long now = System.nanoTime();
        if (lastSettlementFailureLogNanos != 0L
                && now - lastSettlementFailureLogNanos < SETTLEMENT_FAILURE_LOG_INTERVAL_NANOS) {
            return false;
        }
        lastSettlementFailureLogNanos = now;
        return true;
    }

    private static final long SETTLEMENT_FAILURE_LOG_INTERVAL_NANOS = 60_000_000_000L;

    private void drainPersistenceCompletions() {
        if (ownerQueueRequired()) {
            requestTerminalOwnerDrain();
            return;
        }
        markOwnerThread();
        // Freeze the batch at admission. A callback may enqueue its own retry, which belongs to
        // the next owner prelude; every completion already in this batch must nevertheless run.
        int remaining = Math.min(PERSISTENCE_DRAIN_BATCH_LIMIT, persistenceCompletions.size());
        PersistenceCompletion completion;
        while (remaining-- > 0
                && persistenceCompletionMayProceed()
                && (completion = persistenceCompletions.poll()) != null) {
            if (architectureTurn != null) architectureTurn.persistenceCompletionsDrained++;
            runPersistenceCompletion(completion, terminalPhase);
        }
        if (generatedCushionOperationMayProceed()) flushGeneratedEntityMessages();
        requestPersistenceOwnerDrainIfNeeded();
    }

    /** A completion burst gets a bounded owner slice; residue is a coalesced owner continuation. */
    private void requestPersistenceOwnerDrainIfNeeded() {
        if (persistenceCompletions.isEmpty() && mapPersistenceCompletions.isEmpty()) return;
        if (terminalPhase != TerminalPhase.RUNNING) {
            requestTerminalOwnerDrain();
            return;
        }
        if (!started || persistenceOwnerDrainScheduled.get()
                || executor == null || executor.isShutdown() || executor.isTerminated()) return;
        if (!persistenceOwnerDrainScheduled.compareAndSet(false, true)) return;
        if (!enqueueOwnerTask(() -> {
            try {
                // Same order as the tick prelude: a committed cushion mutation applies before the
                // publication completion that refers to it.
                drainCommittedGeneratedCushionMutations();
                drainMapPersistenceCompletions();
                drainPersistenceCompletions();
            } finally {
                persistenceOwnerDrainScheduled.set(false);
                if (!persistenceCompletions.isEmpty() || !mapPersistenceCompletions.isEmpty()) {
                    requestPersistenceOwnerDrainIfNeeded();
                }
            }
        })) {
            persistenceOwnerDrainScheduled.set(false);
        }
    }

    /**
     * The disposal writer drains bookkeeping callbacks after live state has crossed DISPOSED.
     * Individual live-authority callbacks retain their own stricter RUNNING/DRAINING guards.
     */
    private boolean persistenceCompletionMayProceed() {
        return terminalPhase == TerminalPhase.RUNNING
                || terminalPhase == TerminalPhase.DRAINING
                || terminalPhase == TerminalPhase.ABORTED
                || terminalPhase == TerminalPhase.DISPOSED;
    }

    private void flushGeneratedEntityMessages() {
        if (pendingGeneratedEntityMessages.isEmpty()
                || !generatedCushionOperationMayProceed()) return;
        if (ctx.broadcaster() == null) {
            pendingGeneratedEntityMessages.clear();
            return;
        }
        while (!pendingGeneratedEntityMessages.isEmpty()) {
            synchronized (this) {
                if (!generatedCushionOperationMayProceed()) return;
                PendingGeneratedEntityMessage pending = pendingGeneratedEntityMessages.peekFirst();
                if (!ctx.broadcaster()
                        .enqueueRetainedGeneratedBroadcastFromTick(
                                worldId, pending.message, pending.sequence)) return;
                if (!generatedCushionOperationMayProceed()) return;
                pendingGeneratedEntityMessages.removeFirst();
            }
        }
    }

    private void retryPendingSignPersistence() {
        retryPendingSignPersistence(false);
    }

    private void retryPendingSignPersistence(boolean disposalRetry) {
        for (Map.Entry<BlockPos, PendingSignPersistence> entry
                : List.copyOf(pendingSignPersistence.entrySet())) {
            if (!disposalRetry && !ownerTurnMayContinue()) return;
            if (signPersistenceInFlight.contains(entry.getKey())
                    || !pendingSignPersistence.remove(entry.getKey(), entry.getValue())) continue;
            persistSign(entry.getKey(), entry.getValue().sign(), entry.getValue().placement(),
                    disposalRetry);
        }
    }

    private void drainMapPersistenceCompletions() {
        drainMapPersistenceCompletions(terminalPhase);
    }

    private void drainMapPersistenceCompletions(TerminalPhase admittedPhase) {
        if (ownerQueueRequired()) {
            requestTerminalOwnerDrain();
            return;
        }
        markOwnerThread();
        int remaining = Math.min(PERSISTENCE_DRAIN_BATCH_LIMIT, mapPersistenceCompletions.size());
        PersistenceCompletion completion;
        while (remaining-- > 0
                && persistenceCompletionMayProceed()
                && (completion = mapPersistenceCompletions.poll()) != null) {
            if (architectureTurn != null) architectureTurn.mapCompletionsDrained++;
            runPersistenceCompletion(completion, admittedPhase);
        }
        requestPersistenceOwnerDrainIfNeeded();
    }

    /**
     * 틱 안전 계측 창 안에서 구조물을 적용·전송 큐로 넘긴다. 평상시에는 2ms를 유지하되, 브라우저가 실제
     * 스냅샷을 기다릴 때만 8ms를 허용해 큰 마을이 여러 틱에 걸쳐 하나씩 나타나는 것을 막는다.
     */
    private void runDecorationPhase() {
        long applyBudget = hasActivePendingChunkSnapshotRequest()
                ? SNAPSHOT_STRUCTURE_APPLY_BUDGET_NANOS
                : STRUCTURE_APPLY_BUDGET_NANOS;
        applyDeferredStructureDecorations(System.nanoTime() + applyBudget);
        // 전송은 적용 예산과 독립적으로 최대 4096개를 모으되 월드 순서 큐에는 512개 이하로 나눈다.
        flushDecorationUpdates(System.nanoTime() + STRUCTURE_SEND_BUDGET_NANOS,
                STREAMED_UPDATE_BATCH_LIMIT);
    }

    private static long blockChunkKey(int x, int z) {
        int chunkX = Math.floorDiv(x, Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv(z, Blocks.CHUNK_Z);
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static int packedBlockCell(short blockType, short blockState) {
        return Short.toUnsignedInt(blockType) << 16 | Short.toUnsignedInt(blockState);
    }

    private static long structurePositionKey(int x, int y, int z) {
        return ((long) x & 0x3ffffffL) << 38 | ((long) (y + 2048) & 0xfffL) << 26
                | ((long) z & 0x3ffffffL);
    }

    /** 틱 루프가 영속 diff로 내보낼 블록 변경을 구조물 보존 집합에도 즉시 반영한다. */
    private final class DecorationAwareBlockChanges extends HashMap<BlockPos, Short> {
        @Override
        public boolean isEmpty() {
            return terminalPhase != TerminalPhase.RUNNING || super.isEmpty();
        }

        @Override
        public int size() {
            return terminalPhase != TerminalPhase.RUNNING ? 0 : super.size();
        }

        @Override
        public Set<Map.Entry<BlockPos, Short>> entrySet() {
            return terminalPhase != TerminalPhase.RUNNING ? Set.of() : super.entrySet();
        }

        @Override
        public Short put(BlockPos pos, Short blockType) {
            if (terminalPhase != TerminalPhase.RUNNING || pos == null || blockType == null
                    || fleshCellReserved(pos.x(), pos.y(), pos.z())) {
                return null;
            }
            if (applyingMobMutation) {
                // 몹 변형은 플레이어 편집 집합에 넣지 않는다. 출처는 저널이 소유하고, 되감기와
                // 구조물 재생성이 그 출처를 정확히 구분해서 본다.
                mobMutationSites.add(pos);
                protectedDecorationEdits.remove(pos);
                if (applyingMobMutationKey == null) tickBlockMutationKeys.remove(pos);
                else tickBlockMutationKeys.put(pos, applyingMobMutationKey);
            } else {
                mobMutationSites.remove(pos);
                protectedDecorationEdits.add(pos);
                tickBlockMutationKeys.remove(pos);
                if (mobMutationJournal != null) {
                    // 플레이어·환경이 좌표를 가져갔다. 그 칸의 몹 출처는 사라지고 되감기 대상에서 빠진다.
                    mobMutationJournal.notePlayerEdit(pos.x(), pos.y(), pos.z());
                }
            }
            long editedChunkKey = blockChunkKey(pos.x(), pos.z());
            protectedDecorationEditChunks.add(editedChunkKey);
            replayableOverlayChunks.remove(editedChunkKey);
            // setIndexedOverlay를 거친 같은 변경은 이미 version을 얻었다. 같은 값에 두 번 version을
            // 올리면 snapshot V 뒤 첫 blockUpdate가 V+2가 되어 가짜 gap이 생긴다.
            int unsignedType = Short.toUnsignedInt(blockType);
            ensureOverlayRevision(pos.x(), pos.y(), pos.z(), unsignedType,
                    (short) blockStates.get(pos.x(), pos.y(), pos.z(), Short.toUnsignedInt(blockType)));
            enqueueCurrentOverlayBlock(pos.x(), pos.y(), pos.z(), unsignedType);
            return super.put(pos, blockType);
        }

        @Override
        public void putAll(Map<? extends BlockPos, ? extends Short> changes) {
            for (Map.Entry<? extends BlockPos, ? extends Short> entry : changes.entrySet()) {
                if (terminalPhase != TerminalPhase.RUNNING) return;
                put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Probe/admission and full resident capture are deliberately separate queues.  A cold batch therefore does
     * not consume the one-full-scan budget before any completed chunk can return from its worker.
     */
    private void drainChunkSnapshotRequests() {
        drainSnapshotPreparationRequests();
        drainReadyChunkSnapshotRequests();
    }

    /**
     * Drains the whole queued batch before admitting any of it, so admission follows canonical ring
     * order instead of the order the client happened to ask in. The batch is bounded so one cold
     * burst cannot make the owner turn sort an unbounded list; whatever is not admitted this turn is
     * requeued and re-ordered on the next one.
     */
    private List<ChunkSnapshotRequest> pollSnapshotPreparationBatch() {
        List<ChunkSnapshotRequest> batch = new ArrayList<>();
        ChunkSnapshotRequest request;
        while (batch.size() < SNAPSHOT_PREPARATION_BATCH_LIMIT
                && ownerTurnMayContinue()
                && (request = snapshotPreparationRequests.poll()) != null) {
            // Ownership of the queued mapping is taken here, exactly as the un-batched drain did, so
            // a request held back for ordering can be requeued without the dedup map dropping it.
            if (!queuedChunkSnapshotRequests.remove(request.key, request)
                    || pendingChunkSnapshotRequests.get(request.key) != request) continue;
            batch.add(request);
        }
        SnapshotPreparationAdmission.orderForAdmission(batch,
                held -> chunkKey(held.chunkX, held.chunkZ), this::distanceToNearestPlayer);
        return batch;
    }

    private void drainSnapshotPreparationRequests() {
        if (!ownerTurnMayContinue()) return;
        List<ChunkSnapshotRequest> batch = pollSnapshotPreparationBatch();
        int admitted = 0;
        int next = 0;
        long deadline = System.nanoTime() + SNAPSHOT_PREPARATION_BUDGET_NANOS;
        while (next < batch.size() && admitted < SNAPSHOT_PREPARATION_ADMISSIONS_PER_TICK
                && ownerTurnMayContinue()) {
            // 첫 한 건은 예산과 무관하게 처리한다. 남은 건은 꼬리 루프가 되돌려 넣는다.
            if (next > 0 && System.nanoTime() >= deadline) break;
            ChunkSnapshotRequest request = batch.get(next++);
            if (architectureTurn != null) architectureTurn.snapshotPreparationRequestsExamined++;
            if (!ownerTurnMayContinue()) break;
            if (!request.session.isOpen()) {
                completeChunkSnapshotRequest(request);
                continue;
            }
            if (!canProcessChunkSnapshotRequest(request)) {
                continue;
            }
            // This O(1) read is owner-side only.  It neither hydrates nor changes the terrain cache.
            boolean presentation = requiresDetachedSnapshotPresentation(request);
            long requestedKey = chunkKey(request.chunkX, request.chunkZ);
            if (!presentation && activeSimulationChunks.contains(requestedKey)) {
                PreparedSnapshotChunk promoted = preparedPresentationSnapshots.remove(requestedKey);
                if (promoted != null) {
                    TerrainAccessor.PreparedActivationClaim activation =
                            accessor.adoptPreparedChunkForExplicitActivation(promoted.chunk);
                    if (activation != null) {
                        retainActivationPlanningSource(activation.chunkX(), activation.chunkZ());
                        enqueueChunkActivation(activation);
                    }
                }
            }
            if (presentation && preparedPresentationSnapshots.containsKey(
                    chunkKey(request.chunkX, request.chunkZ))) {
                queueReadyChunkSnapshotRequest(request);
                admitted++;
                if (architectureTurn != null) architectureTurn.snapshotPreparationAdmissions++;
                continue;
            }
            if (!presentation && accessor.isChunkResident(request.chunkX, request.chunkZ)) {
                // Active render chunks must never expose raw generated water/terrain before cold
                // surfaces and deterministic structures are committed. A one-chunk tree-planning
                // source can already be resident without gameplay activation; once the client
                // admits that coordinate for rendering, explicitly promote the same bytes instead
                // of requeueing the request forever while proximity preparation is held behind it.
                // Cold diagnostic requests outside the player union retain the resident-only contract.
                long chunkKey = chunkKey(request.chunkX, request.chunkZ);
                if (activeSimulationChunks.contains(chunkKey)
                        && !isSnapshotPresentationReady(request.chunkX, request.chunkZ)) {
                    if (!ownerTurnMayContinue()) return;
                    TerrainAccessor.PreparedActivationClaim activation =
                            accessor.claimResidentChunkForExplicitActivation(
                                    request.chunkX, request.chunkZ);
                    if (activation != null) {
                        if (!ownerTurnMayContinue()) return;
                        retainActivationPlanningSource(activation.chunkX(), activation.chunkZ());
                        enqueueChunkActivation(activation);
                    }
                    if (!ownerTurnMayContinue()) return;
                    requeueChunkSnapshotRequest(request);
                    // A chunk already claimed and waiting for its tree halo does no work here. Counting
                    // it against the per-turn admission bound let the nearest waiting requests (one per
                    // session for the same chunk) use the whole bound every turn, so the colder halo
                    // neighbours that would release them were never admitted: several players entering
                    // a new world together stalled on the loading screen. Only real work is counted.
                    if (activation != null) {
                        admitted++;
                        if (architectureTurn != null) architectureTurn.snapshotPreparationAdmissions++;
                    }
                    continue;
                }
                if (!ownerTurnMayContinue()) return;
                queueReadyChunkSnapshotRequest(request);
                admitted++;
                if (architectureTurn != null) architectureTurn.snapshotPreparationAdmissions++;
                continue;
            }
            // Preserve per-coordinate ownership through owner adoption, but do not charge replay,
            // tree-source work or completed results to ordinary snapshot production permits.
            // A negative hint includes unknown and keeps the existing conservative generation bound.
            boolean knownCommitted = chunkProductSource.hasKnownCommittedChunk(
                    request.chunkX, request.chunkZ);
            if (!pendingSnapshotChunkPreparations.contains(chunkKey(request.chunkX, request.chunkZ))
                    && (pendingSnapshotChunkPreparations.size() >= MAX_PENDING_SNAPSHOT_PREPARATIONS
                    || !SnapshotPreparationAdmission.admitsCanonicalPreparation(
                            pendingSnapshotGenerationPermits.size(), knownCommitted))) {
                deferredCanonicalDemandAdmissions++;
                requeueChunkSnapshotRequest(request);
                continue;
            }
            if (!submitSnapshotChunkPreparation(request, !knownCommitted)) {
                requeueChunkSnapshotRequest(request);
                break;
            }
            admitted++;
            if (architectureTurn != null) architectureTurn.snapshotPreparationAdmissions++;
            reportSnapshotAdmissionProgress();
        }
        for (; next < batch.size(); next++) {
            if (!ownerTurnMayContinue()) return;
            requeueChunkSnapshotRequest(batch.get(next));
        }
    }

    /**
     * Entry-window admission telemetry. The production-side split (prefetched vs consumer, memo hit
     * ratio) is reported by CanonicalChunkPrefetchScheduler; this line reports what admission did to
     * produce that split, which is not observable from outside the owner turn.
     */
    private void reportSnapshotAdmissionProgress() {
        long ordered = ++orderedCanonicalDemandAdmissions;
        if (ordered % SNAPSHOT_ADMISSION_PROGRESS_INTERVAL != 0) return;
        log.info("snapshot admission: world={} orderedAdmissions={} deferredByDemandBound={}"
                        + " inFlightUncommitted={} demandBound={} queued={}",
                worldId, ordered, deferredCanonicalDemandAdmissions,
                pendingSnapshotGenerationPermits.size(),
                SnapshotPreparationAdmission.CONCURRENT_UNCOMMITTED_CANONICAL_DEMAND,
                snapshotPreparationRequests.size());
    }

    /**
     * Resolves the welcome requests' one-chunk source halos, followed by existing activation dependencies.
     * Coordinates are sorted before admission so worker completion timing cannot change which dependency is
     * admitted first. Source-only results remain reusable resident material and do not activate structures.
     */
    private void scheduleMissingTreePlanningSources() {
        if (!ownerTurnMayContinue() || !started) return;
        Set<Long> entrySources = new HashSet<>();
        for (ChunkSnapshotRequest request : pendingChunkSnapshotRequests.values()) {
            if (!isLiveEntryHaloRequest(request)) continue;
            addChunkNeighborhood(entrySources, request.chunkX, request.chunkZ);
        }
        Set<Long> unique = new HashSet<>();
        // The nine welcome targets need a 5x5 union of immutable sources. Waiting for each target's
        // owner adoption before requesting its halo staggered otherwise-ready entry plans. Admit
        // only these live requests' dependencies on the existing source lane, including coordinates
        // also requested for rendering; owner adoption below still grants any gameplay activation.
        for (long key : entrySources) {
            if (!ownerTurnMayContinue()) return;
            if (!treePlanningSourceChunks.contains(key)
                    || activationPlanningSources.containsKey(key)
                    || unavailableTreePlanningSources.contains(key)
                    || pendingSnapshotChunkPreparations.contains(key)) continue;
            int sourceX = (int) (key >> 32);
            int sourceZ = (int) key;
            if (accessor.isChunkResident(sourceX, sourceZ)) {
                retainActivationPlanningSource(sourceX, sourceZ);
            } else {
                unique.add(key);
            }
        }
        for (long key : retainedRespawnNeighborhoods()) {
            if (!entrySources.isEmpty() && !entrySources.contains(key)) continue;
            int sourceX = (int) (key >> 32);
            int sourceZ = (int) key;
            if (!treePlanningSourceChunks.contains(key)
                    || activationPlanningSources.containsKey(key)
                    || unavailableTreePlanningSources.contains(key)
                    || pendingSnapshotChunkPreparations.contains(key)
                    || hasProcessablePendingSnapshotRequest(key)) {
                continue;
            }
            if (accessor.isChunkResident(sourceX, sourceZ)) {
                retainActivationPlanningSource(sourceX, sourceZ);
            } else {
                unique.add(key);
            }
        }
        for (ChunkActivationDemand demand : chunkActivationDemands.values()) {
            if (!ownerTurnMayContinue()) return;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (!ownerTurnMayContinue()) return;
                    long key = chunkKey(demand.chunkX + dx, demand.chunkZ + dz);
                    if (!entrySources.isEmpty() && !entrySources.contains(key)) continue;
                    if (!treePlanningSourceChunks.contains(key)
                            || activationPlanningSources.containsKey(key)
                            || unavailableTreePlanningSources.contains(key)
                            || pendingSnapshotChunkPreparations.contains(key)
                            || hasProcessablePendingSnapshotRequest(key)) {
                        continue;
                    }
                    int sourceX = (int) (key >> 32);
                    int sourceZ = (int) key;
                    if (accessor.isChunkResident(sourceX, sourceZ)) {
                        retainActivationPlanningSource(sourceX, sourceZ);
                    } else {
                        unique.add(key);
                    }
                }
            }
        }
        List<Long> missing = new ArrayList<>(unique);
        missing.sort((left, right) -> {
            boolean leftEntry = entrySources.contains(left);
            boolean rightEntry = entrySources.contains(right);
            if (leftEntry != rightEntry) return leftEntry ? -1 : 1;
            return leftEntry ? compareDemandedChunks(left, right) : Long.compare(left, right);
        });
        int admitted = 0;
        for (long key : missing) {
            if (!ownerTurnMayContinue()) return;
            if (admitted >= TREE_SOURCE_PREPARATION_ADMISSIONS_PER_TICK) break;
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            if (!submitTreePlanningSourcePreparation(chunkX, chunkZ, key,
                    entrySources.contains(key))) break;
            admitted++;
        }
    }

    private boolean isLiveEntryHaloRequest(ChunkSnapshotRequest request) {
        return request.entryHalo && request.session.isOpen()
                && pendingChunkSnapshotRequests.get(request.key) == request
                && canProcessChunkSnapshotRequest(request);
    }

    /**
     * Entry priority expires with the actual requests, not a timer or a new client acknowledgement.
     * Multiple joining sessions contribute their union; closed, cancelled or obsolete requests
     * cannot starve ordinary work. Tree planning uses immutable source halos, so it does not need
     * unrelated neighbours to consume their live-carrier lanes first.
     */
    private boolean mayAdvanceWelcomeCanonicalWork(int chunkX, int chunkZ) {
        boolean pendingWelcome = false;
        for (ChunkSnapshotRequest request : pendingChunkSnapshotRequests.values()) {
            if (!isLiveEntryHaloRequest(request)) continue;
            if (request.chunkX == chunkX && request.chunkZ == chunkZ) return true;
            pendingWelcome = true;
        }
        return !pendingWelcome;
    }

    /**
     * One-pass form of {@link #mayAdvanceWelcomeCanonicalWork} for owner loops that ask per entry:
     * null while no live welcome request holds the gate, else the only chunk keys it admits.
     */
    private Set<Long> welcomeCanonicalWorkTargets() {
        Set<Long> targets = null;
        for (ChunkSnapshotRequest request : pendingChunkSnapshotRequests.values()) {
            if (!isLiveEntryHaloRequest(request)) continue;
            if (targets == null) targets = new HashSet<>();
            targets.add(chunkKey(request.chunkX, request.chunkZ));
        }
        return targets;
    }

    /** Rechecked by queued workers: cancellation or movement must not retain an entry-only demand. */
    private boolean hasLiveEntryPlanningSourceDemand(int chunkX, int chunkZ) {
        for (ChunkSnapshotRequest request : pendingChunkSnapshotRequests.values()) {
            if (Math.abs((long) request.chunkX - chunkX) <= 1
                    && Math.abs((long) request.chunkZ - chunkZ) <= 1
                    && isLiveEntryHaloRequest(request)) return true;
        }
        return false;
    }

    private void drainReadyChunkSnapshotRequests() {
        if (!ownerTurnMayContinue()) return;
        int captured = 0;
        Map<Long, PreparedSnapshotChunk> capturedPresentations = new HashMap<>();
        ChunkSnapshotRequest request;
        while (captured < SNAPSHOT_CAPTURES_PER_TICK
                && ownerTurnMayContinue()
                && (request = readyChunkSnapshotRequests.poll()) != null) {
            if (!ownerTurnMayContinue()) break;
            if (!queuedChunkSnapshotRequests.remove(request.key, request)
                    || pendingChunkSnapshotRequests.get(request.key) != request) continue;
            if (!request.session.isOpen()) {
                completeChunkSnapshotRequest(request);
                continue;
            }
            if (!canProcessChunkSnapshotRequest(request)) {
                continue;
            }
            boolean presentation = requiresDetachedSnapshotPresentation(request);
            long key = chunkKey(request.chunkX, request.chunkZ);
            if (!presentation) preparedPresentationSnapshots.remove(key);
            if (!presentation && started && !players.isEmpty()
                    && !isSnapshotPresentationReady(request.chunkX, request.chunkZ)) {
                requeueChunkSnapshotRequest(request);
                continue;
            }
            if (!presentation && accessor.isChunkActivated(request.chunkX, request.chunkZ)
                    && !preparePopulationForSnapshot(request.chunkX, request.chunkZ)) {
                requeueChunkSnapshotRequest(request);
                continue;
            }
            PreparedSnapshotChunk presentationSource = presentation
                    ? preparedPresentationSnapshots.get(key) : null;
            SnapshotReference reference = captureSnapshotReference(
                    request.chunkX, request.chunkZ, presentation);
            if (reference == null) {
                // The bounded LRU may have evicted this between its O(1) probe and ref handoff. Re-enter the
                // normal preparation path; this does not leave a stale dedup key or require a browser retry.
                requeueChunkSnapshotRequest(request);
                continue;
            }
            captured++;
            if (SNAPSHOT_STAGE_TRACE) request.traceCapturedNanos = System.nanoTime();
            WebSocketSession session = request.session;
            var broadcaster = ctx.broadcaster();
            if (broadcaster == null) {
                completeChunkSnapshotRequest(request);
                break;
            }
            CompletableFuture<List<byte[]>> frames = prepareSnapshotFrames(reference);
            if (frames == null) {
                completeOrRequeueChunkSnapshotRequest(request);
                break;
            }
            // V와 immutable source ref가 같은 instant인 직후 barrier를 먼저 설치한다. 이 뒤 blockUpdate V+1은
            // sender worker가 준비된 frame을 모두 보낼 때까지 해당 session·chunk에서만 대기한다.
            ChunkSnapshotBarrier barrier = broadcaster.registerChunkSnapshotBarrier(worldId, session,
                    reference.source.chunkX(), reference.source.chunkZ(), reference.toVersion);
            if (barrier == null) {
                completeOrRequeueChunkSnapshotRequest(request);
                break;
            }
            List<Object> semantics = new ArrayList<>();
            potDecorationSnapshot(reference.source).forEach(semantics::add);
            bannerSnapshot(request.chunkX, request.chunkZ).stream()
                    .map(BannerUpdate::new).forEach(semantics::add);
            signSnapshot(request.chunkX, request.chunkZ).stream()
                    .map(SignUpdate::new).forEach(semantics::add);
            enqueueSnapshotDelivery(new SnapshotDelivery(request, barrier, semantics, presentation), frames);
            if (presentationSource != null) {
                capturedPresentations.put(key, presentationSource);
            }
        }
        // All sessions captured in this bounded batch share the same immutable generated product.
        // An identity check leaves a replacement product untouched if cancellation raced capture.
        capturedPresentations.forEach(preparedPresentationSnapshots::remove);
    }

    /**
     * A cold request obtains one detached diff-snapshot lookup + generation task per world/chunk. The dedicated
     * lane performs no per-chunk database query and keeps
     * snapshot recovery independent from player-proximity preparation and from activation-plan follow-up work.
     */
    private boolean submitSnapshotChunkPreparation(ChunkSnapshotRequest request,
            boolean generationPermit) {
        if (SNAPSHOT_STAGE_TRACE && request.traceAdmittedNanos == 0L) {
            request.traceAdmittedNanos = System.nanoTime();
        }
        long key = chunkKey(request.chunkX, request.chunkZ);
        if (pendingSnapshotChunkPreparations.contains(key)) return true;
        if (pendingSnapshotChunkPreparations.size() >= MAX_PENDING_SNAPSHOT_PREPARATIONS) return false;
        boolean presentation = requiresDetachedSnapshotPresentation(request);
        if (presentation && (pendingPresentationSnapshotPreparations.size()
                >= MAX_PENDING_PRESENTATION_PREPARATIONS
                || preparedPresentationSnapshots.size() >= MAX_READY_PRESENTATION_SNAPSHOTS)) return false;
        if (!pendingSnapshotChunkPreparations.add(key)) return true;
        if (presentation) pendingPresentationSnapshotPreparations.add(key);
        Object generationLease = new Object();
        if (generationPermit) pendingSnapshotGenerationPermits.put(key, generationLease);
        try {
            executeOwned(SNAPSHOT_PREPARERS, () -> {
                try {
                    prepareSnapshotChunk(request.chunkX, request.chunkZ, key, false, false, presentation);
                } finally {
                    pendingSnapshotGenerationPermits.remove(key, generationLease);
                }
            }, () -> {
                pendingSnapshotGenerationPermits.remove(key, generationLease);
                pendingSnapshotChunkPreparations.remove(key);
                pendingPresentationSnapshotPreparations.remove(key);
            }, true, request.chunkX, request.chunkZ, activeChunkGeneration(key));
            return true;
        } catch (RejectedExecutionException saturated) {
            pendingSnapshotGenerationPermits.remove(key, generationLease);
            pendingSnapshotChunkPreparations.remove(key);
            pendingPresentationSnapshotPreparations.remove(key);
            return false;
        }
    }

    private boolean submitTreePlanningSourcePreparation(int chunkX, int chunkZ, long key,
            boolean entrySourceOnly) {
        if (pendingSnapshotChunkPreparations.size() >= MAX_PENDING_SNAPSHOT_PREPARATIONS) return false;
        if (!pendingSnapshotChunkPreparations.add(key)) return false;
        try {
            executeOwned(SNAPSHOT_PREPARERS,
                    () -> prepareSnapshotChunk(chunkX, chunkZ, key, true, entrySourceOnly, false),
                    () -> pendingSnapshotChunkPreparations.remove(key),
                    true, chunkX, chunkZ, activeChunkGeneration(key));
            return true;
        } catch (RejectedExecutionException saturated) {
            pendingSnapshotChunkPreparations.remove(key);
            return false;
        }
    }

    /**
     * The same deterministic custom layers used by activation, completed off-owner for presentation.
     * These plans do not install carriers, acknowledge durable lanes, populate mobs or schedule ticks.
     */
    private SnapshotPresentation prepareSnapshotPresentation(TerrainAccessor.PreparedChunk prepared) {
        int chunkX = prepared.chunkX();
        int chunkZ = prepared.chunkZ();
        boolean heavy = !customDimension()
                && explorationDecorator.requiresUncachedHeavyPlan(chunkX, chunkZ);
        if (heavy && !HEAVY_STRUCTURE_PLAN_ACTIVE.compareAndSet(false, true)) return null;
        try {
            String identity = "snapshot:" + worldEpoch + ":" + chunkX + ":" + chunkZ;
            var carrier = prepared.finalLiveCarrier();
            List<SpawnerAggregate.PlannedSpawner> spawners = carrier == null
                    || carrier.sidecars().spawners().isEmpty() ? List.of()
                    : SpawnerAggregate.prepare(identity, carrier).plannedSpawners();
            List<GeneratedDecoratedPotRuntime> pots = carrier == null
                    || carrier.sidecars().blockEntities().isEmpty() ? List.of()
                    : decoratedPotCandidates(FinalCarrierBlockEntityPlan.prepare(identity, carrier));
            if (pots == null) throw new IllegalArgumentException("unsupported snapshot pot projection");
            if (chunkProductSource instanceof com.gameexpert.world.dimension.DimensionChunkProductSource custom) {
                return new SnapshotPresentation(custom.initialStates(seed, chunkX, chunkZ), pots);
            }
            SurfaceDecorator.BlockView naturalWorld = accessor.detachedPlanningView(prepared);
            SurfaceDecorator.BlockView structureWorld = accessor.detachedGeneratedPlanningView(prepared);
            List<ExplorationDecorator.Placement> base =
                    explorationDecorator.activationBasePlacements(chunkX, chunkZ, structureWorld);
            Set<BlockPos> baseWinners = new HashSet<>();
            for (ExplorationDecorator.Placement placement : base) baseWinners.add(placement.pos());
            List<ExplorationDecorator.Placement> deferred =
                    explorationDecorator.hasDeferredCandidate(chunkX, chunkZ)
                            ? explorationDecorator.deferredPlacements(
                                    chunkX, chunkZ, structureWorld, baseWinners) : List.of();
            Set<BlockPos> persistentEdits = persistentEdits(prepared.persistedDiffs());
            SurfaceDecorator.NaturalWaterPlan ponds = surfaceDecorator.customPondFeatures(
                    chunkX, chunkZ, naturalWorld, persistentEdits::contains);
            List<SurfaceDecorator.Decoration> custom = surfaceDecorator.customSurfaceDecorations(
                    chunkX, chunkZ, ponds.overlay(naturalWorld),
                    pos -> persistentEdits.contains(pos) || ponds.claims(pos));
            try (TerrainAccessor.ReplayableChunkPatch.Builder patch =
                    TerrainAccessor.ReplayableChunkPatch.builder(chunkX, chunkZ)) {
                // Carrier state, natural features, then base/deferred structure winners. The source
                // compositor applies persisted/current edits above this complete generated layer.
                for (SpawnerAggregate.PlannedSpawner spawner : spawners) {
                    patch.put(spawner.x(), spawner.y(), spawner.z(),
                            spawner.carrierBlockId(), spawner.authoritativeState());
                }
                appendNaturalDecorations(patch, ponds.decorations());
                appendNaturalDecorations(patch, custom);
                for (List<ExplorationDecorator.Placement> layer : List.of(base, deferred)) {
                    for (ExplorationDecorator.Placement placement : layer) {
                        BlockPos pos = placement.pos();
                        patch.put(pos.x(), pos.y(), pos.z(),
                                placement.blockType(), placement.blockState());
                    }
                }
                return new SnapshotPresentation(patch.build(), pots);
            }
        } finally {
            if (heavy) HEAVY_STRUCTURE_PLAN_ACTIVE.set(false);
        }
    }

    private void prepareSnapshotChunk(int chunkX, int chunkZ, long key, boolean treeSourceOnly,
            boolean entrySourceOnly, boolean presentation) {
        if (!ownerTurnMayContinue()) return;
        // A rapid teleport can leave already-enqueued cold work behind the player. Do not spend terrain
        // generation on it; a new request for the same coordinate keeps this shared task useful.
        if ((!hasProcessablePendingSnapshotRequest(key) && !treePlanningSourceChunks.contains(key))
                || (entrySourceOnly && !hasProcessablePendingSnapshotRequest(key)
                        && !hasLiveEntryPlanningSourceDemand(chunkX, chunkZ))) {
            pendingSnapshotChunkPreparations.remove(key);
            pendingPresentationSnapshotPreparations.remove(key);
            // A request can arrive between the demand check and dedup release. Put that request back on
            // the owner queue; arrivals after the release submit normally.
            if (hasPendingSnapshotRequest(key)) {
                requeuePendingChunkSnapshotRequests(chunkX, chunkZ);
            }
            return;
        }
        try {
            // This worker has no access to TerrainAccessor's mutable resident cache.  The owner adopts the
            // immutable material on its next turn, then requeues all sessions waiting for this chunk.
            TerrainAccessor.PreparedChunk prepared = prepareChunkOffTick(chunkX, chunkZ);
            SnapshotPresentation completedPresentation = presentation && hasProcessablePendingSnapshotRequest(key)
                    ? prepareSnapshotPresentation(prepared) : null;
            if (ownerTurnMayContinue()) preparedSnapshotChunks.add(
                    new PreparedSnapshotChunk(key,
                            presentation && completedPresentation == null ? null : prepared,
                            false, treeSourceOnly, completedPresentation));
        } catch (ExplorationDecorator.SitePlanPendingException pending) {
            // Shared-site ownership is expected coordination, not a failed generation. Yield the
            // bounded far slot and retry through the same distance-ordered owner admission.
            if (ownerTurnMayContinue()) preparedSnapshotChunks.add(
                    new PreparedSnapshotChunk(key, null, false, treeSourceOnly));
        } catch (RuntimeException preparationFailure) {
            log.warn("청크 snapshot 준비 실패: world={} chunk={},{}", worldId, chunkX, chunkZ,
                    preparationFailure);
            if (ownerTurnMayContinue()) preparedSnapshotChunks.add(
                    PreparedSnapshotChunk.failed(key, treeSourceOnly));
        }
    }

    private void drainPreparedSnapshotChunks() {
        if (!ownerTurnMayContinue()) return;
        int adopted = 0;
        long deadline = System.nanoTime() + PREPARED_SNAPSHOT_ADOPTION_BUDGET_NANOS;
        while (adopted < SNAPSHOT_PREPARED_ADOPTIONS_PER_TICK && ownerTurnMayContinue()) {
            // 첫 한 건은 무조건 처리한다. 그러지 않으면 예산이 이미 소진된 턴이 이어질 때
            // 준비 큐가 영영 줄지 않는다.
            if (adopted > 0 && System.nanoTime() >= deadline) break;
            PreparedSnapshotChunk prepared = preparedSnapshotChunks.poll();
            if (prepared == null) break;
            if (architectureTurn != null) architectureTurn.snapshotResultsPolled++;
            if (!ownerTurnMayContinue()) break;
            pendingSnapshotChunkPreparations.remove(prepared.key);
            pendingPresentationSnapshotPreparations.remove(prepared.key);
            if (prepared.chunk == null) {
                int chunkX = (int) (prepared.key >> 32);
                int chunkZ = (int) prepared.key;
                if (prepared.treeSourceOnly) {
                    if (!treePlanningSourceChunks.contains(prepared.key)
                            && !hasPendingSnapshotRequest(prepared.key)) {
                        treeSourcePreparationFailures.remove(prepared.key);
                        adopted++;
                        continue;
                    }
                    if (prepared.countsAsFailure
                            && treeSourcePreparationFailures.merge(prepared.key, 1, Integer::sum)
                                    >= MAX_SNAPSHOT_PREPARATION_FAILURES) {
                        treeSourcePreparationFailures.remove(prepared.key);
                        unavailableTreePlanningSources.add(prepared.key);
                        log.error("나무 경계 지형 준비를 세 번 실패해 해당 이웃을 제외합니다: world={} chunk={},{}",
                                worldId, chunkX, chunkZ);
                    }
                    requeuePendingChunkSnapshotRequests(chunkX, chunkZ);
                } else {
                    if (prepared.countsAsFailure
                            && snapshotPreparationFailures.merge(prepared.key, 1, Integer::sum)
                                    >= MAX_SNAPSHOT_PREPARATION_FAILURES) {
                        snapshotPreparationFailures.remove(prepared.key);
                        if (treePlanningSourceChunks.contains(prepared.key)) {
                            unavailableTreePlanningSources.add(prepared.key);
                        }
                        rejectPendingChunkSnapshotRequests(chunkX, chunkZ);
                    } else {
                        requeuePendingChunkSnapshotRequests(chunkX, chunkZ);
                    }
                }
                adopted++;
                continue;
            }
            int chunkX = prepared.chunk.chunkX();
            int chunkZ = prepared.chunk.chunkZ();
            long key = chunkKey(chunkX, chunkZ);
            WorldWorkEvent work = architectureWork("snapshot-result-adoption", true,
                    chunkX, chunkZ, activeChunkGeneration(key));
            try {
                boolean requested = hasProcessablePendingSnapshotRequest(key);
                boolean planningSource = treePlanningSourceChunks.contains(key);
                if (requested && !activeSimulationChunks.contains(key)
                        && prepared.presentation != null) {
                    // Keeping this detached source avoids immediate LRU self-eviction when the
                    // protected simulation/planning halo already exceeds the resident cache.
                    preparedPresentationSnapshots.put(key, prepared);
                    // Cancellation can remove the last observer between the first demand check
                    // and publication. Recheck after put so its detached arrays cannot occupy a slot.
                    if (!hasProcessablePendingSnapshotRequest(key)) {
                        preparedPresentationSnapshots.remove(key, prepared);
                    }
                } else if (!requested && !planningSource && !activeSimulationChunks.contains(key)) {
                    // Cancellation can race an already-running generator. Its detached result has no observer
                    // and must not become a resident cache entry after the old neighborhood was evicted.
                } else if (prepared.treeSourceOnly && !requested) {
                    if (treePlanningSourceChunks.contains(key)) {
                        accessor.adoptPreparedChunkForSnapshot(prepared.chunk);
                        retainActivationPlanningSource(chunkX, chunkZ);
                    }
                } else if (activeSimulationChunks.contains(key)) {
                    TerrainAccessor.PreparedActivationClaim activation =
                            accessor.adoptPreparedChunkForExplicitActivation(prepared.chunk);
                    if (activation != null) {
                        retainActivationPlanningSource(activation.chunkX(), activation.chunkZ());
                        enqueueChunkActivation(activation);
                    }
                } else if (planningSource || players.isEmpty()) {
                    accessor.adoptPreparedChunkForSnapshot(prepared.chunk);
                    if (planningSource) retainActivationPlanningSource(chunkX, chunkZ);
                }
                snapshotPreparationFailures.remove(key);
                treeSourcePreparationFailures.remove(key);
                unavailableTreePlanningSources.remove(key);
                requeuePendingChunkSnapshotRequests(chunkX, chunkZ);
            } catch (RuntimeException adoptionFailure) {
                WorldWorkEvent.outcome(work, "failed");
                log.warn("청크 snapshot 준비 결과 적용 실패: world={} chunk={},{}", worldId, chunkX, chunkZ,
                        adoptionFailure);
                Map<Long, Integer> failures = prepared.treeSourceOnly
                        ? treeSourcePreparationFailures : snapshotPreparationFailures;
                boolean terminal = failures.merge(key, 1, Integer::sum)
                        >= MAX_SNAPSHOT_PREPARATION_FAILURES;
                if (terminal) {
                    failures.remove(key);
                    if (prepared.treeSourceOnly || treePlanningSourceChunks.contains(key)) {
                        unavailableTreePlanningSources.add(key);
                    }
                    if (prepared.treeSourceOnly) {
                        requeuePendingChunkSnapshotRequests(chunkX, chunkZ);
                    } else {
                        rejectPendingChunkSnapshotRequests(chunkX, chunkZ);
                    }
                } else {
                    requeuePendingChunkSnapshotRequests(chunkX, chunkZ);
                }
            } catch (Error failure) {
                WorldWorkEvent.outcome(work, "failed");
                throw failure;
            } finally {
                WorldWorkEvent.finish(work);
            }
            adopted++;
        }
    }

    private void rejectPendingChunkSnapshotRequests(int chunkX, int chunkZ) {
        for (ChunkSnapshotRequest request : pendingChunkSnapshotRequestsFor(chunkX, chunkZ)) {
            if (request.session.isOpen() && ctx.broadcaster() != null) {
                ctx.broadcaster().enqueueSendToFromTick(worldId, request.session,
                        new ChunkSnapshotUnavailable(chunkX, chunkZ, worldEpoch));
            }
            completeChunkSnapshotRequest(request);
        }
    }

    private void requeuePendingChunkSnapshotRequests(int chunkX, int chunkZ) {
        for (ChunkSnapshotRequest request : pendingChunkSnapshotRequestsFor(chunkX, chunkZ)) {
            if (request.session.isOpen()) {
                requeueChunkSnapshotRequest(request);
            } else {
                completeChunkSnapshotRequest(request);
            }
        }
    }

    private void completeOrRequeueChunkSnapshotRequest(ChunkSnapshotRequest request) {
        if (request.session.isOpen()) {
            requeueChunkSnapshotRequest(request);
        } else {
            completeChunkSnapshotRequest(request);
        }
    }

    /** 완료 cache와 진행 중 future를 함께 조회해 같은 청크 버전을 한 번만 인코딩합니다. */
    private CompletableFuture<List<byte[]>> prepareSnapshotFrames(SnapshotReference reference) {
        if (terminalPhase != TerminalPhase.RUNNING) return null;
        SnapshotFrameKey key = new SnapshotFrameKey(reference.source.chunkX(),
                reference.source.chunkZ(), reference.toVersion, reference.presentation);
        CompletableFuture<List<byte[]>> created;
        synchronized (snapshotFrameCache) {
            List<byte[]> cached = snapshotFrameCache.get(key);
            if (cached != null) return CompletableFuture.completedFuture(cached);
            CompletableFuture<List<byte[]>> existing = snapshotFrameEncodes.get(key);
            if (existing != null) return existing;
            created = new CompletableFuture<>();
            existing = snapshotFrameEncodes.putIfAbsent(key, created);
            if (existing != null) return existing;
        }
        try {
            executeOwned(SNAPSHOT_ENCODERS, () -> encodeSnapshotFrames(key, reference, created),
                    () -> failSnapshotEncode(key, created), true,
                    reference.source.chunkX(), reference.source.chunkZ(),
                    activeChunkGeneration(chunkKey(reference.source.chunkX(), reference.source.chunkZ())));
            return created;
        } catch (RejectedExecutionException saturated) {
            snapshotFrameEncodes.remove(key, created);
            return null;
        }
    }

    private void encodeSnapshotFrames(SnapshotFrameKey key, SnapshotReference reference,
            CompletableFuture<List<byte[]>> result) {
        try {
            if (terminalPhase != TerminalPhase.RUNNING) {
                snapshotFrameEncodes.remove(key, result);
                result.completeExceptionally(new CancellationException(
                        "청크 snapshot 태스크가 terminal 경계에서 취소되었습니다"));
                return;
            }
            snapshotFrameEncodeCount.incrementAndGet();
            List<byte[]> frames = ChunkSnapshotCodec.encodeFrames(completeSnapshot(reference),
                    chunkProductSource.generationProfile());
            // unload invalidation과 같은 monitor에서 map ownership을 넘겨 stale 완료가 cache를 되살리지 않는다.
            synchronized (snapshotFrameCache) {
                if (snapshotFrameEncodes.remove(key, result)
                        && terminalPhase == TerminalPhase.RUNNING) {
                    snapshotFrameCache.put(key, frames);
                }
            }
            if (terminalPhase == TerminalPhase.RUNNING) {
                result.complete(frames);
            } else {
                result.completeExceptionally(new CancellationException(
                        "청크 snapshot 인코드가 terminal 경계에서 취소되었습니다"));
            }
        } catch (Throwable failed) {
            WorldWorkEvent.currentOutcome("failed");
            snapshotFrameEncodes.remove(key, result);
            result.completeExceptionally(failed);
            if (failed instanceof Error error) throw error;
        }
    }

    private void invalidateSnapshotFrames(int chunkX, int chunkZ) {
        invalidateSnapshotFrames(chunkX, chunkZ, null);
    }

    /** Null invalidates both modes; a transition cancels only the departed presentation mode. */
    private void invalidateSnapshotFrames(int chunkX, int chunkZ, Boolean presentation) {
        synchronized (snapshotFrameCache) {
            snapshotFrameCache.removeChunk(chunkX, chunkZ, presentation);
            for (Map.Entry<SnapshotFrameKey, CompletableFuture<List<byte[]>>> entry
                    : snapshotFrameEncodes.entrySet()) {
                SnapshotFrameKey key = entry.getKey();
                if (key.chunkX != chunkX || key.chunkZ != chunkZ) continue;
                if (presentation != null && key.presentation != presentation.booleanValue()) continue;
                if (snapshotFrameEncodes.remove(key, entry.getValue())) {
                    entry.getValue().completeExceptionally(
                            new CancellationException("청크 snapshot이 축출되었습니다"));
                }
            }
        }
    }

    private void failSnapshotEncode(SnapshotFrameKey key,
            CompletableFuture<List<byte[]>> result) {
        if (snapshotFrameEncodes.remove(key, result)) {
            result.completeExceptionally(new CancellationException("청크 snapshot 태스크가 폐기되었습니다"));
        }
    }

    private void failPendingSnapshotEncodes() {
        for (Map.Entry<SnapshotFrameKey, CompletableFuture<List<byte[]>>> entry
                : snapshotFrameEncodes.entrySet()) {
            if (snapshotFrameEncodes.remove(entry.getKey(), entry.getValue())) {
                entry.getValue().completeExceptionally(
                        new CancellationException("월드 폐기 중 청크 snapshot 태스크가 취소되었습니다"));
            }
        }
    }

    private void enqueueSnapshotDelivery(SnapshotDelivery delivery,
            CompletableFuture<List<byte[]>> frames) {
        if (terminalPhase != TerminalPhase.RUNNING) {
            abandonSnapshotDelivery(delivery);
            return;
        }
        String sessionId = delivery.request.session.getId();
        AtomicBoolean isolated = new AtomicBoolean();
        SnapshotSendLane lane = snapshotSendLanes.compute(sessionId, (ignored, current) -> {
            SnapshotSendLane resolved = current == null ? new SnapshotSendLane(sessionId) : current;
            synchronized (resolved) {
                // 격리된 세션에는 더 쌓지 않는다. 멈춘 피어 하나가 lane 큐와 pending 요청 슬롯을
                // 무한히 차지하면 그 세션만이 아니라 월드 전체의 요청 예산이 마른다.
                if (resolved.stalled) isolated.set(true);
                else resolved.queue.addLast(delivery);
            }
            return resolved;
        });
        if (isolated.get()) {
            abandonSnapshotDelivery(delivery);
            return;
        }
        frames.whenComplete((encoded, failed) -> {
            if (SNAPSHOT_STAGE_TRACE) delivery.request.traceEncodedNanos = System.nanoTime();
            synchronized (lane) {
                delivery.frames = encoded;
                delivery.failure = failed;
                delivery.ready = true;
            }
            scheduleSnapshotSend(lane);
        });
    }

    /**
     * 세션 FIFO의 head만 sender에 제출하므로, 여러 sender 스레드가 돌아도 한 연결의 wire 순서는
     * 요청 FIFO 그대로입니다(in-flight 1). 병렬 인코딩 완료 순서도 이 순서를 바꾸지 않습니다.
     */
    private void scheduleSnapshotSend(SnapshotSendLane lane) {
        SnapshotDelivery delivery;
        List<SnapshotDelivery> isolated = List.of();
        synchronized (lane) {
            if (lane.senderActive) return;
            if (lane.stalled) {
                isolated = List.copyOf(lane.queue);
                lane.queue.clear();
                delivery = null;
            } else {
                delivery = lane.queue.peekFirst();
                if (delivery != null && !delivery.ready) {
                    return;
                }
                if (delivery != null) {
                    lane.senderActive = true;
                    // 실제 전송이 시작될 때 시계를 켠다. 제출 시각부터 재면 sender 풀이 바쁠 때
                    // 멀쩡한 세션이 정체로 오인돼 끊긴다.
                    lane.sendStartedNanos = 0;
                    lane.inFlightSession = delivery.request.session;
                }
            }
        }
        for (SnapshotDelivery abandoned : isolated) {
            abandonSnapshotDelivery(abandoned);
        }
        if (delivery == null) {
            // map compute는 lane monitor 밖에서 수행해 enqueue의 map→lane 순서와 역전되지 않게 합니다.
            retireSnapshotSendLane(lane);
            return;
        }
        if (terminalPhase != TerminalPhase.RUNNING
                || !submitSnapshotSend(() -> sendSnapshotDelivery(lane, delivery))) {
            rejectSnapshotDeliveryBeforeSend(lane, delivery);
        }
    }

    /**
     * 상한을 넘긴 세션만 격리합니다. 블로킹 소켓 쓰기는 전송 측에서 중단할 수 없으므로, 이미 잡힌
     * sender 스레드는 세션을 닫아야만 풀립니다. 남은 대기분은 여기서 즉시 반납해 요청 슬롯을 비웁니다.
     */
    void enforceSnapshotSendDeadlines() {
        if (!ownerTurnMayContinue() || snapshotSendLanes.isEmpty()) return;
        long now = System.nanoTime();
        long stallNanos = snapshotSendStallNanos;
        for (SnapshotSendLane lane : snapshotSendLanes.values()) {
            if (!ownerTurnMayContinue()) return;
            WebSocketSession stuck;
            List<SnapshotDelivery> isolated;
            long elapsed;
            synchronized (lane) {
                // sendStartedNanos == 0은 아직 sender에서 시작되지 않은 제출분이다. 풀이 바쁜 것은
                // 피어 정체가 아니므로 격리 대상이 아니다.
                if (!lane.senderActive || lane.stalled || lane.sendStartedNanos == 0
                        || now - lane.sendStartedNanos < stallNanos) {
                    continue;
                }
                lane.stalled = true;
                stuck = lane.inFlightSession;
                elapsed = now - lane.sendStartedNanos;
                isolated = List.copyOf(lane.queue);
                lane.queue.clear();
            }
            log.warn("청크 snapshot 송신 정체로 세션 격리: world={} session={} 경과={}ms 반납={}",
                    worldId, lane.sessionId, TimeUnit.NANOSECONDS.toMillis(elapsed), isolated.size());
            for (SnapshotDelivery abandoned : isolated) {
                abandonSnapshotDelivery(abandoned);
            }
            if (stuck != null) submitStalledSessionRecovery(stuck);
        }
    }

    /** 격리 세션의 전송 취소·요청 반납. 전송은 하지 않으므로 틱 스레드에서 호출해도 안전합니다. */
    private void abandonSnapshotDelivery(SnapshotDelivery delivery) {
        if (ctx.broadcaster() != null) {
            ctx.broadcaster().cancelChunkSnapshotBarrier(delivery.barrier);
        }
        completeChunkSnapshotRequest(delivery.request);
    }

    private void submitStalledSessionRecovery(WebSocketSession session) {
        if (ctx.broadcaster() == null) return;
        try {
            SNAPSHOT_STALL_RECOVERY.execute(
                    () -> ctx.broadcaster().recoverStalledSnapshotSession(session));
        } catch (RejectedExecutionException saturated) {
            // 회수가 밀려도 lane은 이미 격리돼 새 전송을 쌓지 않는다. 소켓은 broadcaster의 자체
            // 송신 한도가 닫고, 그 전까지 다른 세션은 남은 sender 스레드로 계속 전송된다.
            log.warn("정체 세션 회수 큐 포화: world={} session={}", worldId, session.getId());
        }
    }

    /** 한 청크의 단계별 누적 경과(ms). 켜졌을 때만 호출된다. */
    private void reportSnapshotStageTrace(ChunkSnapshotRequest request, boolean delivered) {
        long requested = request.traceRequestedNanos;
        if (requested == 0L) return;
        long sent = System.nanoTime();
        log.info("snapshot stage: world={} session={} chunk={},{} 요청+0"
                        + " 승인+{}ms 준비+{}ms 캡처+{}ms 인코드+{}ms 송신+{}ms 재큐={} 전달={}",
                worldId, request.session.getId(), request.chunkX, request.chunkZ,
                stageMillis(requested, request.traceAdmittedNanos),
                stageMillis(requested, request.traceReadyNanos),
                stageMillis(requested, request.traceCapturedNanos),
                stageMillis(requested, request.traceEncodedNanos),
                (sent - requested) / 1_000_000L, request.traceRequeues, delivered);
    }

    private static String stageMillis(long from, long stamp) {
        return stamp == 0L ? "-" : Long.toString((stamp - from) / 1_000_000L);
    }

    private void sendSnapshotDelivery(SnapshotSendLane lane, SnapshotDelivery delivery) {
        synchronized (lane) {
            lane.sendStartedNanos = System.nanoTime();
        }
        ChunkSnapshotRequest request = delivery.request;
        boolean delivered = false;
        var broadcaster = ctx.broadcaster();
        try {
            if (terminalPhase != TerminalPhase.RUNNING) {
                if (broadcaster != null) broadcaster.cancelChunkSnapshotBarrier(delivery.barrier);
            } else if (broadcaster == null) {
                completeChunkSnapshotRequest(request);
            } else if (pendingChunkSnapshotRequests.get(request.key) != request
                    || !request.session.isOpen() || !canProcessChunkSnapshotRequest(request)) {
                broadcaster.cancelChunkSnapshotBarrier(delivery.barrier);
            } else if (delivery.presentation != requiresDetachedSnapshotPresentation(request)) {
                // Generation and encoding can finish after a near/far transition. The new mode
                // captures its current source; old cache entries and encodes cannot republish it.
                broadcaster.cancelChunkSnapshotBarrier(delivery.barrier);
                invalidateSnapshotFrames(request.chunkX, request.chunkZ, delivery.presentation);
            } else if (delivery.failure != null) {
                broadcaster.cancelChunkSnapshotBarrier(delivery.barrier);
                log.warn("청크 snapshot 인코드 실패: world={} chunk={},{}",
                        worldId, request.chunkX, request.chunkZ, delivery.failure);
            } else {
                delivered = broadcaster.sendSnapshotThenRelease(
                        request.session, delivery.barrier, delivery.frames, delivery.semantics);
            }
        } catch (RuntimeException failed) {
            if (broadcaster != null) broadcaster.cancelChunkSnapshotBarrier(delivery.barrier);
            log.warn("청크 snapshot 송신 실패: world={} chunk={},{}",
                    worldId, request.chunkX, request.chunkZ, failed);
        }
        if (delivered || broadcaster == null || !request.session.isOpen()
                || terminalPhase != TerminalPhase.RUNNING) {
            completeChunkSnapshotRequest(request);
        } else {
            // sender failure 또는 bounded barrier overflow 뒤에는 최신 V로 다시 capture한다.
            requeueChunkSnapshotRequest(request);
        }
        if (SNAPSHOT_STAGE_TRACE) reportSnapshotStageTrace(request, delivered);
        finishSnapshotDelivery(lane, delivery);
    }

    private void rejectSnapshotDeliveryBeforeSend(SnapshotSendLane lane, SnapshotDelivery delivery) {
        if (ctx.broadcaster() != null) {
            ctx.broadcaster().cancelChunkSnapshotBarrier(delivery.barrier);
        }
        if (terminalPhase != TerminalPhase.RUNNING || !delivery.request.session.isOpen()) {
            completeChunkSnapshotRequest(delivery.request);
        } else {
            completeOrRequeueChunkSnapshotRequest(delivery.request);
        }
        finishSnapshotDelivery(lane, delivery);
    }

    private void finishSnapshotDelivery(SnapshotSendLane lane, SnapshotDelivery delivery) {
        synchronized (lane) {
            if (lane.queue.peekFirst() == delivery) {
                lane.queue.removeFirst();
            } else {
                lane.queue.remove(delivery);
            }
            lane.senderActive = false;
            lane.inFlightSession = null;
            lane.sendStartedNanos = 0;
        }
        scheduleSnapshotSend(lane);
    }

    private void retireSnapshotSendLane(SnapshotSendLane lane) {
        snapshotSendLanes.computeIfPresent(lane.sessionId, (ignored, current) -> {
            if (current != lane) return current;
            synchronized (current) {
                return current.queue.isEmpty() && !current.senderActive ? null : current;
            }
        });
    }

    private boolean submitSnapshotSend(Runnable task) {
        try {
            executeOwned(SNAPSHOT_SENDERS, task);
            return true;
        } catch (RejectedExecutionException saturated) {
            // tick에서 압축/전송 fallback하지 않는다. resident 여부는 다음 tick에 다시 판정한다.
            return false;
        }
    }

    private void indexOverlay(BlockPos pos, short blockType, short state) {
        int chunkX = Math.floorDiv(pos.x(), Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv(pos.z(), Blocks.CHUNK_Z);
        long revision = chunkVersions.advance(chunkX, chunkZ);
        overlayChunk(blockChunkKey(pos.x(), pos.z()), true)
                .put(pos, new OverlayValue(blockType, state, revision));
    }

    /**
     * [CONTAINER-MENUS] A pressure plate leaving its cell still sends
     * {@code BasePressurePlateBlock#updateNeighbours} (the cell and the one below); the neighbour
     * pass needs to know the cell held a plate after the overlay already changed.
     */
    private final java.util.LinkedHashSet<BlockPos> replacedPressurePlates =
            new java.util.LinkedHashSet<>();

    private void notePressurePlateReplacement(int x, int y, int z, int blockType) {
        if (blockType == Blocks.POPLAR_PRESSURE_PLATE) return;
        if (WorldTickLoop.residentBlockType(accessor, x, y, z) == Blocks.POPLAR_PRESSURE_PLATE) {
            replacedPressurePlates.add(new BlockPos(x, y, z));
        }
    }

    /** Cells whose pressure plate was replaced since the last neighbour pass (consumed). */
    java.util.Set<BlockPos> drainReplacedPressurePlates() {
        if (replacedPressurePlates.isEmpty()) return java.util.Set.of();
        java.util.Set<BlockPos> drained = java.util.Set.copyOf(replacedPressurePlates);
        replacedPressurePlates.clear();
        return drained;
    }

    private void setIndexedOverlay(int x, int y, int z, int blockType) {
        if (tickLoop != null) {
            int before = WorldTickLoop.residentBlockType(accessor, x, y, z);
            if (before >= 0) tickLoop.noteRedstoneChange(x, y, z, before, blockStates.get(x, y, z, before));
        }
        if (terminalPhase != TerminalPhase.RUNNING || fleshCellReserved(x, y, z)) return;
        notePressurePlateReplacement(x, y, z, blockType);
        revokeChangedArchaeologyTarget(x, y, z, blockType, 0);
        noteBlockEntityReplacement(x, y, z, blockType);
        accessor.setOverlay(x, y, z, blockType);
        hopperSystem.record(x, y, z, blockType);
        invalidateLightColumnCaches(x, z);
        mobSystem.invalidateSpawnerChunk(Math.floorDiv(x, Blocks.CHUNK_X),
                Math.floorDiv(z, Blocks.CHUNK_Z));
        mobSystem.recordIndexedBlockChange(x, y, z, blockType);
        lightningRods.record(x, y, z, lightningRodIndexType(blockType));
        replayableOverlayChunks.add(blockChunkKey(x, z));
        ensureOverlayRevision(x, y, z, blockType, (short) 0);
    }

    private void setIndexedOverlay(int x, int y, int z, int blockType, int blockState) {
        if (tickLoop != null) {
            int before = WorldTickLoop.residentBlockType(accessor, x, y, z);
            if (before >= 0) tickLoop.noteRedstoneChange(x, y, z, before, blockStates.get(x, y, z, before));
        }
        if (terminalPhase != TerminalPhase.RUNNING || fleshCellReserved(x, y, z)) return;
        notePressurePlateReplacement(x, y, z, blockType);
        revokeChangedArchaeologyTarget(x, y, z, blockType, blockState);
        noteBlockEntityReplacement(x, y, z, blockType);
        accessor.setOverlay(x, y, z, blockType);
        hopperSystem.record(x, y, z, blockType);
        setBlockState(x, y, z, blockType, blockState);
        mobSystem.invalidateSpawnerChunk(Math.floorDiv(x, Blocks.CHUNK_X),
                Math.floorDiv(z, Blocks.CHUNK_Z));
        mobSystem.recordIndexedBlockChange(x, y, z, blockType);
        lightningRods.record(x, y, z, lightningRodIndexType(blockType));
        long key = blockChunkKey(x, z);
        replayableOverlayChunks.add(key);
        if (rehydratingReplayableChunks.contains(key)) {
            long revision = chunkVersions.current(
                    Math.floorDiv(x, Blocks.CHUNK_X), Math.floorDiv(z, Blocks.CHUNK_Z));
            overlayChunk(key, true)
                    .put(new BlockPos(x, y, z),
                            new OverlayValue((short) blockType, (short) blockState, revision));
            return;
        }
        // Runtime overlays are replayed after a chunk leaves and later re-enters the simulation union. Do not
        // advance the authoritative revision when a deterministic replay writes the same cell: snapshot bytes
        // (including their toVersion) must stay identical across deactivate/reactivate.
        ensureOverlayRevision(x, y, z, blockType, (short) blockState);
    }

    /**
     * Keeps the mob and random-tick light views coherent at one mutation boundary. The tick-loop null check is
     * required because TerrainAccessor listeners are installed before that consumer finishes construction.
     */
    private void invalidateLightColumnCaches(int x, int z) {
        mobSystem.invalidateLightColumn(x, z);
        if (tickLoop != null) tickLoop.invalidateRandomTickLightColumn(x, z);
    }

    private void invalidateLightChunkCaches(int chunkX, int chunkZ) {
        mobSystem.invalidateLightChunk(chunkX, chunkZ);
        if (tickLoop != null) tickLoop.invalidateRandomTickLightChunk(chunkX, chunkZ);
    }

    /**
     * Owner-only bootstrap for the synthetic reserved performance world. The plan is already the
     * complete final aggregate, so applying it as thousands of gameplay edits would manufacture
     * fluid/neighbor events and persistence work that are not part of the workload. Sparse sender
     * overrides are filled atomically in this owner action, then the two light families are
     * invalidated once per affected chunk. Normal edits continue through their existing paths.
     */
    void installDensePerformanceFixtureCells(DenseWorldPerformanceFixturePlan plan) {
        if (plan == null) throw new IllegalArgumentException("performance fixture plan is required");
        if (terminalPhase != TerminalPhase.RUNNING) return;
        DenseWorldPerformanceFixturePlan.Bounds bounds = plan.bounds();
        for (DenseWorldPerformanceFixturePlan.Cell cell : plan.cells()) {
            if (terminalPhase != TerminalPhase.RUNNING) return;
            if (cell.blockState() != 0) {
                throw new IllegalArgumentException("performance fixture requires zero block states");
            }
            DenseWorldPerformanceFixturePlan.Point point = cell.point();
            accessor.setOverlay(point.x(), point.y(), point.z(), cell.blockId());
        }
        int minChunkX = Math.floorDiv(bounds.anchorX(), Blocks.CHUNK_X);
        int maxChunkX = Math.floorDiv(bounds.anchorX() + bounds.width() - 1, Blocks.CHUNK_X);
        int minChunkZ = Math.floorDiv(bounds.anchorZ(), Blocks.CHUNK_Z);
        int maxChunkZ = Math.floorDiv(bounds.anchorZ() + bounds.depth() - 1, Blocks.CHUNK_Z);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (terminalPhase != TerminalPhase.RUNNING) return;
                invalidateLightChunkCaches(chunkX, chunkZ);
            }
        }
    }

    static int lightningRodIndexType(int blockType) {
        return CopperAgeRules.isLightningRod(blockType) ? Blocks.LIGHTNING_ROD : blockType;
    }

    private void enqueueCurrentOverlayBlock(int x, int y, int z, int blockType) {
        Map<BlockPos, OverlayValue> chunk = authoritativeOverlay.get(blockChunkKey(x, z));
        OverlayValue value = chunk == null ? null : chunk.get(new BlockPos(x, y, z));
        if (value == null || value.liveUpdateQueued) return;
        if (pendingLiveBlockUpdates.size() >= MAX_PENDING_DECORATION_UPDATES) {
            if (ctx.broadcaster() != null
                    && !ctx.broadcaster().enqueueLiveJournalRecoveryFromTick(worldId)) {
                return;
            }
            // Authoritative overlay/revisions remain intact. Only the undelivered delta history is
            // replaced by the FIFO recovery boundary; affected sessions reconnect from full snapshots.
            pendingLiveBlockUpdates.clear();
        }
        pendingLiveBlockUpdates.addLast(
                new Block(x, y, z, (short) blockType, value.state, value.revision));
        value.liveUpdateQueued = true;
    }

    private Map<BlockPos, OverlayValue> overlayChunk(long key, boolean create) {
        Map<BlockPos, OverlayValue> chunk = authoritativeOverlay.get(key);
        if (chunk == null && create) {
            chunk = new HashMap<>();
            authoritativeOverlay.put(key, chunk);
        }
        return chunk;
    }

    /**
     * 아직 전송되지 않은 같은 값의 오버레이 항목이 이 좌표를 이미 교정하고 있는지 판정합니다.
     * 참이면 새 revision 없이도 클라이언트가 교정을 받으므로 청크 버전을 전진시키지 않습니다.
     */
    private boolean correctionAlreadyQueued(int x, int y, int z, int blockType, short state) {
        Map<BlockPos, OverlayValue> chunk = authoritativeOverlay.get(blockChunkKey(x, z));
        OverlayValue value = chunk == null ? null : chunk.get(new BlockPos(x, y, z));
        return value != null && value.liveUpdateQueued
                && value.blockType == (short) blockType && value.state == state;
    }

    long overlayRevision(int x, int y, int z) {
        Map<BlockPos, OverlayValue> chunk = authoritativeOverlay.get(blockChunkKey(x, z));
        OverlayValue value = chunk == null ? null : chunk.get(new BlockPos(x, y, z));
        return value == null ? 0 : value.revision;
    }

    long ensureOverlayRevision(int x, int y, int z, int blockType, short state) {
        Map<BlockPos, OverlayValue> chunk = authoritativeOverlay.get(blockChunkKey(x, z));
        BlockPos pos = new BlockPos(x, y, z);
        OverlayValue value = chunk == null ? null : chunk.get(pos);
        if (value == null || value.blockType != (short) blockType || value.state != state) {
            indexOverlay(pos, (short) blockType, state);
            return chunkVersions.current(Math.floorDiv(x, Blocks.CHUNK_X),
                    Math.floorDiv(z, Blocks.CHUNK_Z));
        }
        return value.revision;
    }

    /**
     * Rejected optimistic edits need a fresh chunk cursor even when the
     * authoritative bytes are unchanged; otherwise clients treat the
     * correction as an acknowledgement and retain their predicted block.
     */
    long advanceOverlayRevisionForCorrection(int x, int y, int z, int blockType, short state) {
        long revision = advanceOverlayRevisionForCorrectionWithoutQueue(x, y, z, blockType, state);
        enqueueCurrentOverlayBlock(x, y, z, blockType);
        return revision;
    }

    private long advanceOverlayRevisionForCorrectionWithoutQueue(
            int x, int y, int z, int blockType, short state) {
        BlockPos pos = new BlockPos(x, y, z);
        long chunkKey = blockChunkKey(x, z);
        if (!protectedDecorationEditChunks.contains(chunkKey)) {
            replayableOverlayChunks.add(chunkKey);
        }
        indexOverlay(pos, (short) blockType, state);
        return overlayRevision(x, y, z);
    }

    private static final class OverlayValue {
        private final short blockType;
        private final short state;
        private final long revision;
        private boolean liveUpdateQueued;
        private OverlayValue(short blockType, short state, long revision) {
            this.blockType = blockType; this.state = state; this.revision = revision;
        }
    }

    /** access-order LinkedHashMap 하나를 짧게 잠가 정확한 LRU와 byte budget을 함께 유지합니다. */
    private static final class SnapshotFrameCache {
        private final int maxEntries;
        private final long byteBudget;
        private final LinkedHashMap<SnapshotFrameKey, CachedSnapshotFrames> entries =
                new LinkedHashMap<>(16, 0.75f, true);
        private long retainedBytes;

        private SnapshotFrameCache(int maxEntries, long byteBudget) {
            this.maxEntries = maxEntries;
            this.byteBudget = byteBudget;
        }

        private synchronized List<byte[]> get(SnapshotFrameKey key) {
            CachedSnapshotFrames cached = entries.get(key);
            return cached == null ? null : cached.frames;
        }

        private synchronized void put(SnapshotFrameKey key, List<byte[]> frames) {
            long frameBytes = 0;
            for (byte[] frame : frames) frameBytes += frame.length;
            if (frameBytes > byteBudget) return;
            CachedSnapshotFrames replacement = new CachedSnapshotFrames(frames, frameBytes);
            CachedSnapshotFrames previous = entries.put(key, replacement);
            if (previous != null) retainedBytes -= previous.bytes;
            retainedBytes += frameBytes;
            while (entries.size() > maxEntries || retainedBytes > byteBudget) {
                Iterator<Map.Entry<SnapshotFrameKey, CachedSnapshotFrames>> oldest =
                        entries.entrySet().iterator();
                if (!oldest.hasNext()) break;
                retainedBytes -= oldest.next().getValue().bytes;
                oldest.remove();
            }
        }

        private synchronized void removeChunk(int chunkX, int chunkZ) {
            removeChunk(chunkX, chunkZ, null);
        }

        private synchronized void removeChunk(int chunkX, int chunkZ, Boolean presentation) {
            Iterator<Map.Entry<SnapshotFrameKey, CachedSnapshotFrames>> iterator =
                    entries.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<SnapshotFrameKey, CachedSnapshotFrames> entry = iterator.next();
                if (entry.getKey().chunkX != chunkX || entry.getKey().chunkZ != chunkZ) continue;
                if (presentation != null
                        && entry.getKey().presentation != presentation.booleanValue()) continue;
                retainedBytes -= entry.getValue().bytes;
                iterator.remove();
            }
        }

        private synchronized int size() {
            return entries.size();
        }

        private synchronized void clear() {
            entries.clear();
            retainedBytes = 0;
        }
    }

    private static final class CachedSnapshotFrames {
        private final List<byte[]> frames;
        private final long bytes;

        private CachedSnapshotFrames(List<byte[]> frames, long bytes) {
            this.frames = List.copyOf(frames);
            this.bytes = bytes;
        }
    }

    /** 런타임별 cache이므로 world/epoch는 암묵적이고, 청크 권위 revision이 mutation identity입니다. */
    private static final class SnapshotFrameKey {
        private final int chunkX;
        private final int chunkZ;
        private final long mutationVersion;
        private final boolean presentation;

        private SnapshotFrameKey(int chunkX, int chunkZ, long mutationVersion) {
            this(chunkX, chunkZ, mutationVersion, false);
        }

        private SnapshotFrameKey(int chunkX, int chunkZ, long mutationVersion, boolean presentation) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.mutationVersion = mutationVersion;
            this.presentation = presentation;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SnapshotFrameKey key)) return false;
            return chunkX == key.chunkX && chunkZ == key.chunkZ
                    && mutationVersion == key.mutationVersion && presentation == key.presentation;
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(chunkX);
            result = 31 * result + Integer.hashCode(chunkZ);
            result = 31 * result + Long.hashCode(mutationVersion);
            return 31 * result + Boolean.hashCode(presentation);
        }
    }

    private static final class SnapshotSendLane {
        private final String sessionId;
        private final Deque<SnapshotDelivery> queue = new ArrayDeque<>();
        private boolean senderActive;
        /** in-flight 전송 시작 시각과 대상. 상한을 넘긴 세션만 격리하는 판정에 쓰입니다. */
        private long sendStartedNanos;
        private WebSocketSession inFlightSession;
        private boolean stalled;

        private SnapshotSendLane(String sessionId) {
            this.sessionId = sessionId;
        }
    }

    private static final class SnapshotDelivery {
        private final ChunkSnapshotRequest request;
        private final ChunkSnapshotBarrier barrier;
        private final List<Object> semantics;
        private final boolean presentation;
        private List<byte[]> frames;
        private Throwable failure;
        private boolean ready;

        private SnapshotDelivery(ChunkSnapshotRequest request, ChunkSnapshotBarrier barrier,
                List<Object> semantics) {
            this(request, barrier, semantics, false);
        }

        private SnapshotDelivery(ChunkSnapshotRequest request, ChunkSnapshotBarrier barrier,
                List<Object> semantics, boolean presentation) {
            this.request = request;
            this.barrier = barrier;
            this.semantics = semantics;
            this.presentation = presentation;
        }
    }

    /** tick에서 복사한 immutable-by-convention cells. sender worker는 이 배열만 읽습니다. */
    private static final class SnapshotCells {
        private final int chunkX;
        private final int chunkZ;
        private final long toVersion;
        private final short[] surfaceHeights;
        private final short[] terrainSurfaceHeights;
        private final short[] blockTypes;
        private final byte[] blockStates;
        private final List<GeneratedDecoratedPotRuntime> decoratedPots;

        private SnapshotCells(int chunkX, int chunkZ, long toVersion, short[] surfaceHeights,
                short[] terrainSurfaceHeights, short[] blockTypes, byte[] blockStates,
                List<GeneratedDecoratedPotRuntime> decoratedPots) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.toVersion = toVersion;
            this.surfaceHeights = surfaceHeights;
            this.terrainSurfaceHeights = terrainSurfaceHeights;
            this.blockTypes = blockTypes;
            this.blockStates = blockStates;
            this.decoratedPots = List.copyOf(decoratedPots);
        }
    }

    /** Immutable owner-to-sender handoff with explicit sparse states and matching chunk version. */
    private static final class SnapshotReference {
        private final TerrainAccessor.SnapshotSource source;
        private final long toVersion;
        private final List<GeneratedDecoratedPotRuntime> decoratedPots;
        private final Map<Integer, Integer> explicitStates;
        private final boolean presentation;

        private SnapshotReference(TerrainAccessor.SnapshotSource source, long toVersion,
                List<GeneratedDecoratedPotRuntime> decoratedPots, Map<Integer, Integer> explicitStates) {
            this(source, toVersion, decoratedPots, explicitStates, false);
        }

        private SnapshotReference(TerrainAccessor.SnapshotSource source, long toVersion,
                List<GeneratedDecoratedPotRuntime> decoratedPots, Map<Integer, Integer> explicitStates,
                boolean presentation) {
            this.source = source;
            this.toVersion = toVersion;
            this.decoratedPots = List.copyOf(decoratedPots);
            this.explicitStates = Map.copyOf(explicitStates);
            this.presentation = presentation;
        }
    }

    private static final class SnapshotPresentation {
        private final TerrainAccessor.ReplayableChunkPatch patch;
        private final List<GeneratedDecoratedPotRuntime> decoratedPots;

        private SnapshotPresentation(TerrainAccessor.ReplayableChunkPatch patch,
                List<GeneratedDecoratedPotRuntime> decoratedPots) {
            this.patch = java.util.Objects.requireNonNull(patch);
            this.decoratedPots = List.copyOf(decoratedPots);
        }
    }

    /** Detached terrain material crossing from the snapshot preparer to the world-owner adoption phase. */
    private static final class PreparedSnapshotChunk {
        private final long key;
        private final TerrainAccessor.PreparedChunk chunk;
        private final boolean countsAsFailure;
        private final boolean treeSourceOnly;
        private final SnapshotPresentation presentation;

        private PreparedSnapshotChunk(long key, TerrainAccessor.PreparedChunk chunk,
                boolean countsAsFailure, boolean treeSourceOnly) {
            this(key, chunk, countsAsFailure, treeSourceOnly, null);
        }

        private PreparedSnapshotChunk(long key, TerrainAccessor.PreparedChunk chunk,
                boolean countsAsFailure, boolean treeSourceOnly, SnapshotPresentation presentation) {
            this.key = key;
            this.chunk = chunk;
            this.countsAsFailure = countsAsFailure;
            this.treeSourceOnly = treeSourceOnly;
            this.presentation = presentation;
        }

        private static PreparedSnapshotChunk failed(long key, boolean treeSourceOnly) {
            return new PreparedSnapshotChunk(key, null, true, treeSourceOnly);
        }

    }

    /** Detached player-proximity result; owner adoption verifies the player's demand generation first. */
    private static final class PreparedChunkDemand {
        private final String nickname;
        private final long generation;
        private final long neighborhoodGeneration;
        private final long key;
        private final TerrainAccessor.PreparedChunk chunk;

        private PreparedChunkDemand(String nickname, long generation,
                long neighborhoodGeneration, long key, TerrainAccessor.PreparedChunk chunk) {
            this.nickname = nickname;
            this.generation = generation;
            this.neighborhoodGeneration = neighborhoodGeneration;
            this.key = key;
            this.chunk = chunk;
        }
    }

    private static final class ChunkSnapshotRequest {
        private final WebSocketSession session;
        private final int chunkX;
        private final int chunkZ;
        private final ChunkSnapshotRequestKey key;
        /** Lives and expires with the exact welcome request; ordinary movement requests leave it false. */
        private volatile boolean entryHalo;
        /** 임시 단계 계측용 스탬프. SNAPSHOT_STAGE_TRACE 가 꺼져 있으면 아무도 읽지 않는다. */
        private volatile long traceRequestedNanos;
        private volatile long traceAdmittedNanos;
        private volatile long traceReadyNanos;
        private volatile long traceCapturedNanos;
        private volatile long traceEncodedNanos;
        private volatile int traceRequeues;

        private ChunkSnapshotRequest(WebSocketSession session, int chunkX, int chunkZ) {
            this.session = session;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.key = new ChunkSnapshotRequestKey(session.getId(), chunkX, chunkZ);
            this.traceRequestedNanos = SNAPSHOT_STAGE_TRACE ? System.nanoTime() : 0L;
        }
    }

    /** Plain key rather than a record: backend DTO/model conventions forbid records. */
    private static final class ChunkSnapshotRequestKey {
        private final String sessionId;
        private final int chunkX;
        private final int chunkZ;

        private ChunkSnapshotRequestKey(String sessionId, int chunkX, int chunkZ) {
            this.sessionId = sessionId;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ChunkSnapshotRequestKey key)) return false;
            return chunkX == key.chunkX && chunkZ == key.chunkZ && sessionId.equals(key.sessionId);
        }

        @Override
        public int hashCode() {
            int result = sessionId.hashCode();
            result = 31 * result + chunkX;
            return 31 * result + chunkZ;
        }
    }

    /** Shared worker queues retain only live-world tasks; FIFO within each runtime remains unchanged. */
    private static final class RuntimeTask implements Runnable {
        private final WorldRuntime owner;
        private final Runnable task;
        private final Runnable onDiscard;
        private final AtomicBoolean claimed = new AtomicBoolean();
        private final WorldWorkEvent architectureWork;

        private RuntimeTask(WorldRuntime owner, Runnable task, Runnable onDiscard,
                WorldWorkEvent architectureWork) {
            this.owner = owner;
            this.task = task;
            this.onDiscard = onDiscard;
            this.architectureWork = architectureWork;
        }

        private void discard() {
            if (!claimed.compareAndSet(false, true)) return;
            try {
                if (onDiscard != null) onDiscard.run();
            } finally {
                WorldWorkEvent.outcome(architectureWork, "discarded");
                WorldWorkEvent.finish(architectureWork);
            }
        }

        @Override
        public void run() {
            if (!claimed.compareAndSet(false, true)) return;
            WorldWorkEvent.start(architectureWork);
            try {
                if (owner.ownerTurnMayContinue()) {
                    task.run();
                } else {
                    WorldWorkEvent.outcome(architectureWork, "discarded");
                    if (onDiscard != null) onDiscard.run();
                }
            } catch (RuntimeException | Error failure) {
                WorldWorkEvent.outcome(architectureWork, "failed");
                throw failure;
            } finally {
                WorldWorkEvent.finish(architectureWork);
            }
        }
    }

    private static final class ChunkActivationDemand {
        private final long generation;
        private final int chunkX;
        private final int chunkZ;
        private final List<WorldBlockDiff> persistedDiffs;

        private ChunkActivationDemand(long generation, int chunkX, int chunkZ,
                List<WorldBlockDiff> persistedDiffs) {
            this.generation = generation;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.persistedDiffs = persistedDiffs;
        }
    }

    private static final class PendingFinalCarrierClaim {
        private final long generation;
        private final TerrainAccessor.FinalLiveCarrierClaim claim;
        private final EnumSet<TerrainAccessor.FinalLiveCarrierLane> remaining;

        private PendingFinalCarrierClaim(long generation,
                TerrainAccessor.FinalLiveCarrierClaim claim) {
            this.generation = generation;
            this.claim = java.util.Objects.requireNonNull(claim, "claim");
            this.remaining = claim.lanes().isEmpty()
                    ? EnumSet.noneOf(TerrainAccessor.FinalLiveCarrierLane.class)
                    : EnumSet.copyOf(claim.lanes());
        }
    }

    private static final class FinalCarrierTickAdmissionKey {
        private final long chunkKey;
        private final long generation;
        private final TerrainAccessor.FinalLiveCarrierLane lane;

        private FinalCarrierTickAdmissionKey(long chunkKey, long generation,
                TerrainAccessor.FinalLiveCarrierLane lane) {
            this.chunkKey = chunkKey;
            this.generation = generation;
            this.lane = lane;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof FinalCarrierTickAdmissionKey key)) return false;
            return chunkKey == key.chunkKey && generation == key.generation && lane == key.lane;
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(chunkKey);
            result = 31 * result + Long.hashCode(generation);
            return 31 * result + lane.hashCode();
        }
    }

    private enum FinalCarrierTickAdmissionFailure {
        TRANSIENT, TERMINAL_INVALID, TERMINAL_REJECTION
    }

    private static final class FinalCarrierTickAdmissionCompletion {
        private final FinalCarrierTickAdmissionKey key;
        private final PendingFinalCarrierClaim pending;
        private final FinalCarrierTickScheduler.PreparedAdmission prepared;
        private final FinalCarrierTickScheduler.Admission admission;
        private final Throwable failure;
        private final FinalCarrierTickAdmissionFailure failureKind;

        private FinalCarrierTickAdmissionCompletion(FinalCarrierTickAdmissionKey key,
                PendingFinalCarrierClaim pending,
                FinalCarrierTickScheduler.PreparedAdmission prepared,
                FinalCarrierTickScheduler.Admission admission, Throwable failure,
                FinalCarrierTickAdmissionFailure failureKind) {
            this.key = key;
            this.pending = pending;
            this.prepared = prepared;
            this.admission = admission;
            this.failure = failure;
            this.failureKind = failureKind;
        }

        private static FinalCarrierTickAdmissionCompletion completed(
                FinalCarrierTickAdmissionKey key, PendingFinalCarrierClaim pending,
                FinalCarrierTickScheduler.PreparedAdmission prepared,
                FinalCarrierTickScheduler.Admission admission) {
            return new FinalCarrierTickAdmissionCompletion(
                    key, pending, prepared, admission, null, null);
        }

        private static FinalCarrierTickAdmissionCompletion failed(
                FinalCarrierTickAdmissionKey key, PendingFinalCarrierClaim pending,
                Throwable failure, FinalCarrierTickAdmissionFailure failureKind) {
            return new FinalCarrierTickAdmissionCompletion(
                    key, pending, null, null, failure,
                    java.util.Objects.requireNonNull(failureKind, "failure kind"));
        }
    }

    /**
     * Immutable worker output plus tick-owner cursors. One call applies a small fixed slice of already-planned
     * cells; the outer owner deadline remains the hard tick bound. Natural presentation state is committed before
     * persisted-fluid scheduling. Spring sources enter the runtime scheduler only after structure completion.
     */
    private static final class PreparedChunkActivation {
        private final long generation;
        private final int chunkX;
        private final int chunkZ;
        private final List<BlockPos> fluidWakeups;
        private final TerrainAccessor.ReplayableChunkPatch naturalPatch;
        private final List<ExplorationDecorator.Placement> criticalPlacements;
        private final List<AcceptedStructureSite> acceptedSites;
        private final boolean hasDeferredCandidate;
        private final Set<BlockPos> baseStructurePlacements = new HashSet<>();
        private int fluidIndex;
        private int criticalIndex;
        private boolean fluidActivationStarted;
        private boolean naturalPatchInstalled;
        private boolean deferredSubmitted;
        private boolean structureSitesAccepted;
        private boolean snapshotBasePublished;

        private PreparedChunkActivation(long generation, int chunkX, int chunkZ,
                List<BlockPos> fluidWakeups,
                TerrainAccessor.ReplayableChunkPatch naturalPatch,
                List<ExplorationDecorator.Placement> criticalPlacements,
                List<AcceptedStructureSite> acceptedSites,
                boolean hasDeferredCandidate) {
            this.generation = generation;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.fluidWakeups = List.copyOf(fluidWakeups);
            this.naturalPatch = naturalPatch;
            this.criticalPlacements = List.copyOf(criticalPlacements);
            this.acceptedSites = List.copyOf(acceptedSites);
            this.hasDeferredCandidate = hasDeferredCandidate;
        }

        /**
         * True when the next slice could only re-ask the welcome-priority gate: every earlier step
         * is done and {@link #advanceSnapshotStructureReady} refuses this chunk before any work.
         */
        private boolean waitsAtWelcomeGate(Set<Long> welcomeTargets) {
            return fluidActivationStarted && naturalPatchInstalled
                    && criticalIndex >= criticalPlacements.size()
                    && !deferredSubmitted && !hasDeferredCandidate && structureSitesAccepted
                    && !welcomeTargets.contains(chunkKey(chunkX, chunkZ));
        }

        /** Entry watchdog: the step the next slice starts from. */
        private String stepLabel(Set<Long> welcomeTargets) {
            if (!fluidActivationStarted) return "first";
            if (!naturalPatchInstalled) return "patch";
            if (criticalIndex < criticalPlacements.size()) {
                return "critical" + criticalIndex + '/' + criticalPlacements.size();
            }
            if (!deferredSubmitted) {
                if (hasDeferredCandidate) return "handoff";
                return welcomeTargets != null && waitsAtWelcomeGate(welcomeTargets)
                        ? "welcomeGate" : "structure";
            }
            if (!snapshotBasePublished) return "base";
            return "fluid" + fluidIndex + '/' + fluidWakeups.size();
        }

        private boolean applyNext(WorldRuntime runtime) {
            WorldWorkEvent work = runtime.architectureWork("activation-apply-slice", true,
                    chunkX, chunkZ, generation);
            int beforeCells = criticalIndex + fluidIndex;
            try {
                return applyNextObserved(runtime);
            } catch (RuntimeException | Error failure) {
                WorldWorkEvent.outcome(work, "failed");
                throw failure;
            } finally {
                if (work != null) work.workUnits = criticalIndex + fluidIndex - beforeCells;
                WorldWorkEvent.finish(work);
            }
        }

        private boolean applyNextObserved(WorldRuntime runtime) {
            if (!runtime.ownerTurnMayContinue()) return true;
            if (!fluidActivationStarted) {
                // 계측 결과 이 네 통짜 단계(유체·몹·자연패치·스컬크)는 전부 50ms 미만이었다.
                // 활성화 비용은 여기가 아니라 markSnapshotStructureReady 의 레인 소비에 있다.
                runtime.fluidSim.onChunkActivated(chunkX, chunkZ);
                if (!runtime.ownerTurnMayContinue()) return true;
                runtime.mobSystem.onChunkActivated(chunkX, chunkZ);
                fluidActivationStarted = true;
            }
            if (!naturalPatchInstalled) {
                if (!runtime.ownerTurnMayContinue()) return true;
                runtime.installPreparedNaturalPatch(chunkX, chunkZ, naturalPatch);
                naturalPatchInstalled = true;
            }
            int remaining = ACTIVATION_CELLS_PER_SLICE;
            while (remaining-- > 0) {
                if (!runtime.ownerTurnMayContinue()) return true;
                if (criticalIndex < criticalPlacements.size()) {
                    if (!runtime.ownerTurnMayContinue()) return true;
                    ExplorationDecorator.Placement placement =
                            criticalPlacements.get(criticalIndex++);
                    runtime.applyStructurePlacement(placement);
                    if (!runtime.ownerTurnMayContinue()) return true;
                    baseStructurePlacements.add(placement.pos());
                    continue;
                }
                if (!deferredSubmitted) {
                    if (!runtime.ownerTurnMayContinue()) return true;
                    if (hasDeferredCandidate) {
                        DeferredStructureDecoration deferred = new DeferredStructureDecoration(
                                chunkX, chunkZ, baseStructurePlacements, acceptedSites);
                        runtime.pendingStructureDecorations.putIfAbsent(
                                chunkKey(chunkX, chunkZ), deferred);
                    } else {
                        if (!runtime.ownerTurnMayContinue()) return true;
                        if (!structureSitesAccepted) {
                            runtime.mobSystem.acceptStructureSites(chunkX, chunkZ, acceptedSites);
                            structureSitesAccepted = true;
                        }
                        if (!runtime.ownerTurnMayContinue()) return true;
                        // 레인 하나만 진행한다. 남아 있으면 이번 슬라이스를 접고 다음 owner
                        // turn에 이어서 한다. 이때 반드시 false를 돌려야 한다. true는 호출부에
                        // "이 활성화는 끝났다"는 뜻이라 대기열에서 제거되고, 그러면 이 청크는
                        // 스냅샷 기본 준비조차 못 해 영원히 배달되지 않는다.
                        if (!runtime.advanceSnapshotStructureReady(chunkX, chunkZ)) return false;
                    }
                    deferredSubmitted = true;
                    continue;
                }
                if (!snapshotBasePublished) {
                    if (!runtime.ownerTurnMayContinue()) return true;
                    runtime.markSnapshotBaseReady(chunkX, chunkZ);
                    snapshotBasePublished = true;
                    continue;
                }
                if (fluidIndex < fluidWakeups.size()) {
                    if (!runtime.ownerTurnMayContinue()) return true;
                    BlockPos changed = fluidWakeups.get(fluidIndex++);
                    runtime.fluidSim.onBlockChanged(changed.x(), changed.y(), changed.z());
                    continue;
                }
                // 자연 패치와 구조물/영속 diff가 모두 설치된 뒤 한 번만 스컬크 부속을
                // 인덱싱한다. 이후 첫 발걸음은 16³ 전수 조회 대신 캐시만 읽는다.
                if (!runtime.ownerTurnMayContinue()) return true;
                runtime.tickLoop.onChunkActivatedForSculk(chunkX, chunkZ);
                return true;
            }
            return false;
        }
    }

    private static final class PlayerDemandCursor {
        private volatile int centerX;
        private volatile int centerZ;
        private volatile long generation = 1L;
        private int nextOffset;

        private PlayerDemandCursor(int centerX, int centerZ) {
            this.centerX = centerX;
            this.centerZ = centerZ;
        }

        private void moveTo(int centerX, int centerZ, boolean teleported) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            generation++;
            // 이동과 텔레포트 모두 새 중심부터 시작한다. 텔레포트 판정은 세대 폐기의 명시적 경계다.
            nextOffset = 0;
            if (teleported) generation++;
        }
    }

    /** 한 청크의 확정 placement 목록과 다음 적용 위치. 목록 순서를 재정렬하거나 합치지 않는다. */
    private static final class DeferredStructureDecoration {
        private final int chunkX;
        private final int chunkZ;
        private final Set<BlockPos> baseStructurePlacements;
        private final long createdNanos = System.nanoTime();
        private Future<PreparedStructurePlan> future;
        private HeavyPlanLease heavyPlanLease;
        private java.util.concurrent.CompletableFuture<?> planningDependency;
        private List<ExplorationDecorator.Placement> placements;
        private List<ExplorationDecorator.Placement> criticalPlacements = List.of();
        private List<AcceptedStructureSite> acceptedSites;
        private Throwable planningFailure;
        private int planningFailures;
        private int criticalCursor;
        private int cursor;
        private long lastProgressSequence;

        private DeferredStructureDecoration(int chunkX, int chunkZ,
                Set<BlockPos> baseStructurePlacements,
                List<AcceptedStructureSite> acceptedSites) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.baseStructurePlacements = Set.copyOf(baseStructurePlacements);
            this.acceptedSites = List.copyOf(acceptedSites);
        }

        private boolean isSubmitted() {
            if (future != null) return true;
            if (planningDependency == null) return false;
            if (!planningDependency.isDone()) return true;
            planningDependency = null;
            return false;
        }

        private void submit(Future<PreparedStructurePlan> future, HeavyPlanLease heavyPlanLease) {
            this.future = future;
            this.heavyPlanLease = heavyPlanLease;
        }

        private boolean cancelPlanning() {
            if (future == null || !future.cancel(false)) return false;
            if (heavyPlanLease != null) heavyPlanLease.releaseIfNotStarted();
            return true;
        }

        private boolean isReady() {
            if (placements != null) return true;
            if (future == null || !future.isDone()) return false;
            try {
                PreparedStructurePlan prepared = future.get();
                placements = prepared.placements;
                criticalPlacements = prepared.criticalPlacements;
                if (!prepared.acceptedSites.isEmpty()) {
                    List<AcceptedStructureSite> combined = new ArrayList<>(
                            acceptedSites.size() + prepared.acceptedSites.size());
                    combined.addAll(acceptedSites);
                    combined.addAll(prepared.acceptedSites);
                    acceptedSites = List.copyOf(combined);
                }
                heavyPlanLease = null;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                planningFailure = interrupted;
                planningFailures++;
                future = null;
            } catch (java.util.concurrent.ExecutionException planningError) {
                Throwable cause = planningError.getCause();
                if (cause instanceof ExplorationDecorator.SitePlanPendingException pending) {
                    planningDependency = pending.completion();
                } else {
                    planningFailure = cause == null ? planningError : cause;
                    planningFailures++;
                }
                future = null;
            }
            return placements != null;
        }

        private Throwable takePlanningFailure() {
            Throwable failure = planningFailure;
            planningFailure = null;
            return failure;
        }

        private boolean canRetry() {
            return planningFailures < 2;
        }

        private ExplorationDecorator.Placement next() {
            return placements != null && cursor < placements.size() ? placements.get(cursor++) : null;
        }

        private boolean isComplete() {
            return placements != null && cursor >= placements.size();
        }

        private List<AcceptedStructureSite> acceptedSites() {
            return acceptedSites;
        }

        /** 워커가 미리 뽑은 상자·횃불도 동일한 64-cell/deadline 예산 안에서 먼저 확정한다. */
        private boolean applyCriticalPlacements(WorldRuntime runtime, long deadline) {
            int examined = 0;
            while (criticalCursor < criticalPlacements.size()
                    && examined++ < STRUCTURE_PLACEMENTS_PER_SLICE
                    && runtime.pendingLiveBlockUpdates.size() < MAX_PENDING_DECORATION_UPDATES
                    && System.nanoTime() < deadline
                    && runtime.ownerTurnMayContinue()) {
                if (!runtime.ownerTurnMayContinue()) return false;
                ExplorationDecorator.Placement placement = criticalPlacements.get(criticalCursor++);
                runtime.applyStructurePlacement(placement);
            }
            return criticalCursor >= criticalPlacements.size();
        }
    }

    /** Worker-side scan result; the owner never rescans a large plan merely to find critical cells. */
    private static final class PreparedStructurePlan {
        private final List<ExplorationDecorator.Placement> placements;
        private final List<ExplorationDecorator.Placement> criticalPlacements;
        private final List<AcceptedStructureSite> acceptedSites;

        private PreparedStructurePlan(List<ExplorationDecorator.Placement> placements,
                List<ExplorationDecorator.Placement> criticalPlacements,
                List<AcceptedStructureSite> acceptedSites) {
            this.placements = placements;
            this.criticalPlacements = criticalPlacements;
            this.acceptedSites = acceptedSites;
        }
    }

    /** Releases the global heavy-plan slot exactly once, including cancellation before worker start. */
    private static final class HeavyPlanLease {
        private boolean started;
        private boolean released;

        private synchronized boolean start() {
            if (released) return false;
            started = true;
            return true;
        }

        private synchronized void releaseIfNotStarted() {
            if (!started) release();
        }

        private synchronized void release() {
            if (released) return;
            released = true;
            HEAVY_STRUCTURE_PLAN_ACTIVE.set(false);
        }
    }

    /**
     * 직전 BoatSystem publish 스냅샷의 작은 선체 footprint와 겹친 수련잎을 정상 블록 변경으로 파괴한다.
     * 보트 Y는 클라이언트 부유 계산의 기준점이라 정수 경계 오차를 흡수하도록 floor(y)±1을 검사한다.
     */
    void breakLilyPadsHitByBoats() {
        if (!ownerTurnMayContinue()) return;
        for (BoatDto boat : boatSystem.welcomeSnapshot()) {
            if (!ownerTurnMayContinue()) return;
            if (!Double.isFinite(boat.getX()) || !Double.isFinite(boat.getY())
                    || !Double.isFinite(boat.getZ())) {
                continue;
            }
            int minX = (int) Math.floor(boat.getX() - BOAT_HALF_FOOTPRINT);
            int maxX = (int) Math.floor(boat.getX() + BOAT_HALF_FOOTPRINT);
            int minZ = (int) Math.floor(boat.getZ() - BOAT_HALF_FOOTPRINT);
            int maxZ = (int) Math.floor(boat.getZ() + BOAT_HALF_FOOTPRINT);
            int centerY = (int) Math.floor(boat.getY());
            for (int y = Math.max(Blocks.MIN_Y, centerY - 1);
                    y <= Math.min(Blocks.MAX_Y, centerY + 1); y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        if (!ownerTurnMayContinue()) return;
                        if (WorldTickLoop.residentBlockType(accessor, x, y, z) != Blocks.LILY_PAD) {
                            continue;
                        }
                        fluidSim.applyChange(x, y, z, Blocks.AIR);
                        itemSystem.spawnBlockDrop((short) Blocks.LILY_PAD,
                                x + 0.5, y + 0.5, z + 0.5);
                    }
                }
            }
        }
    }

    /** 남은 상태 저장 + 버퍼 flush + 실행기 종료 + 매니저 등록 해제. 여러 번 불려도 안전합니다. */
    void dispose() {
        disposeNow(true);
    }

    /** 애플리케이션 종료용 강제 폐기. */
    void disposeNow() {
        disposeNow(false);
    }

    private void disposeNow(boolean requireEmpty) {
        long deadlineNanos = checkedDeadlineNanos(DEFAULT_DISPOSAL_AWAIT_TIMEOUT_MILLIS);
        if (!disposeNowWithin(requireEmpty, deadlineNanos, false,
                DEFAULT_OWNER_QUIESCENCE_TIMEOUT_MILLIS)) {
            throw new IllegalStateException(
                    "world shutdown did not drain terminal persistence callbacks");
        }
    }

    /** Converts a positive bounded duration without permitting saturation or signed wrap. */
    private static long checkedDeadlineNanos(long timeoutMillis) {
        if (timeoutMillis <= 0L) {
            throw new IllegalArgumentException("shutdown timeout must be positive");
        }
        final long timeoutNanos;
        try {
            timeoutNanos = Math.multiplyExact(timeoutMillis, 1_000_000L);
            return Math.addExact(System.nanoTime(), timeoutNanos);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("shutdown timeout exceeds monotonic deadline range",
                    overflow);
        }
    }

    private void runDisposalOnOwner(long deadlineNanos) {
        if (!disposalOwnerActionStarted.compareAndSet(false, true)) return;
        markOwnerThread();
        boolean finalized = false;
        try {
            if (terminalPhase != TerminalPhase.DRAINING) {
                if (terminalPhase == TerminalPhase.ABORTED) abortTerminalOwnerCleanup();
                else terminalizeResidualPersistenceCompletions();
                disposalOwnerCompletion.complete(false);
                return;
            }
            // Take every live inventory snapshot before any logout or owner-map mutation. The
            // player callback path may already hold an inventory monitor, so the runtime lock is
            // never held while this preflight crosses into PlayerInventory.
            if (!preflightDisposalPlayers()) {
                throw new IllegalStateException("live player disposal preflight changed");
            }
            if (!drainTerminalPersistenceWithin(deadlineNanos)) {
                abortTerminal();
                return;
            }
            commitGeneratedCushionLogoutsBeforeShutdown();
            if (!drainTerminalPersistenceWithin(deadlineNanos)) {
                abortTerminal();
                return;
            }
            if (!closePersistenceCallbackAdmissionAndDrain(deadlineNanos)) {
                abortTerminal();
                return;
            }
            finalizeDisposalOnOwner();
            finalized = true;
            beginDisposalPersistence();
            disposalOwnerCompletion.complete(true);
            disposalFlightCompletion.complete(true);
        } catch (Throwable failure) {
            if (!finalized) {
                recordTerminalCleanupFailure(failure);
                abortTerminal();
            } else {
                markDisposalPersistenceFailed(failure);
                disposalOwnerCompletion.complete(false);
                disposalFlightCompletion.complete(false);
            }
        }
    }

    private boolean preflightDisposalPlayers() {
        List<PlayerTickState> candidates;
        synchronized (this) {
            if (terminalPhase != TerminalPhase.DRAINING) return false;
            candidates = List.copyOf(players.values());
        }
        for (PlayerTickState player : candidates) {
            try {
                player.inventory().completePersistenceSnapshot();
            } catch (Throwable failure) {
                recordTerminalCleanupFailure(failure);
                return false;
            }
            synchronized (this) {
                if (terminalPhase != TerminalPhase.DRAINING
                        || players.get(player.nickname()) != player
                        || !candidates.contains(player)) return false;
            }
        }
        synchronized (this) {
            if (terminalPhase != TerminalPhase.DRAINING || players.size() != candidates.size()) {
                return false;
            }
            for (PlayerTickState player : candidates) {
                if (players.get(player.nickname()) != player) return false;
            }
            disposalPlayers = List.copyOf(candidates);
            return true;
        }
    }

    /** Crosses DISPOSED only after all owner-side logout work has passed the preflight. */
    private void finalizeDisposalOnOwner() {
        List<PlayerInventory> disposalInventories;
        List<PlayerLeaveRequest> supersededLeaves = new ArrayList<>();
        List<DeferredStructureDecoration> deferred;
        com.gameexpert.engine.persistence.finalcarrier.FinalCarrierPersistenceService
                .CommittedEntityActivationPublisher publisher;
        com.gameexpert.engine.persistence.finalcarrier.FinalCarrierPersistenceService
                persistenceService;
        // Forced shutdown can bypass the normal leave queue. Restore queued trial keys while the
        // player objects still exist so the final disposal snapshot contains the refund.
        for (PlayerTickState player : disposalPlayers) {
            refundQueuedVaultUnlocks(player.nickname());
        }
        synchronized (this) {
            if (terminalPhase != TerminalPhase.DRAINING) {
                throw new IllegalStateException("disposal lost its draining owner boundary");
            }
            if (!pendingDepartedSaves.isEmpty() || playerContainerSettlementInFlight
                    || !playerContainerActions.isEmpty()) {
                throw new IllegalStateException("disposal crossed a live persistence admission");
            }
            disposalInventories = players.values().stream()
                    .map(PlayerTickState::inventory)
                    .toList();
            disposalPlayers = List.copyOf(players.values());
            publisher = finalCarrierEntityPublisher;
            persistenceService = finalCarrierPersistence;
            finalCarrierEntityPublisher = null;
            playerInventorySettlementReservations.clear();
            players.clear();
            playerConnections.clear();
            playerDemandCursors.clear();
            activePlayerChunkCenters = null;
            activePlayerBedChunks = null;
            activePlayerOrder = List.of();
            activeSimulationChunks = Set.of();
            activeChunkGenerations = Map.of();
            treePlanningSourceChunks = Set.of();
            pendingChunkPreparations.clear();
            preparedChunkDemands.clear();
            chunkActivationDemands.clear();
            pendingChunkActivationPlans.clear();
            inFlightFinalCarrierTickAdmissions.clear();
            failedFinalCarrierTickAdmissions.clear();
            finalCarrierTickAdmissionRetryAttempts.clear();
            finalCarrierTickAdmissionRetryAfter.clear();
            finalCarrierTickAdmissionCompletions.clear();
            pendingFinalCarrierClaims.clear();
            structureReadyPasses.clear();
            deferredStructureReadyPasses.clear();
            deferredRecoveryLockRetries.clear();
            deferredActivationLockRetries.clear();
            pendingFinalCarrierTickAdmissionOrder.clear();
            queuedFinalCarrierTickAdmissionChunks.clear();
            pendingGeneratedEntityMessages.clear();
            pendingGeneratedCushionRiderCleanups.clear();
            pendingCommittedGeneratedCushionMutations.clear();
            generatedCushionActionEvidence.clear();
            drainingCushionLogoutAdmissions.clear();
            appliedGeneratedCushionUpdates.clear();
            pendingFinalCarrierTickPublications.clear();
            visibleGeneratedEntityIds = Set.of();
            generatedEntityWelcomeView = new GeneratedEntityWelcomeView(List.of(), 0L);
            preparedChunkActivations.clear();
            activationPlanningFailures.clear();
            pendingChunkActivationApplications.clear();
            preparedActivationChunks.clear();
            snapshotBaseReadyChunks.clear();
            snapshotStructureReadyChunks.clear();
            generatedFluidActivation.clear();
            activationPlanningSources.clear();
            pendingSnapshotChunkPreparations.clear();
            pendingPresentationSnapshotPreparations.clear();
            preparedPresentationSnapshots.clear();
            pendingSnapshotGenerationPermits.clear();
            snapshotPreparationFailures.clear();
            treeSourcePreparationFailures.clear();
            unavailableTreePlanningSources.clear();
            preparedSnapshotChunks.clear();
            snapshotPreparationRequests.clear();
            readyChunkSnapshotRequests.clear();
            queuedChunkSnapshotRequests.clear();
            pendingChunkSnapshotRequests.clear();
            pendingChunkSnapshotRequestsByChunk.clear();
            deferred = new ArrayList<>(pendingStructureDecorations.values());
            pendingStructureDecorations.clear();
            pendingLiveBlockUpdates.clear();
            protectedDecorationEdits.clear();
            mobMutationSites.clear();
            tickBlockMutationKeys.clear();
            protectedDecorationEditChunks.clear();
            replayableOverlayChunks.clear();
            compactedReplayableChunks.clear();
            rehydratingReplayableChunks.clear();
            naturalLootChests.clear();
            canonicalLootCandidates.clear();
            finalCarrierDecoratedPotInstallations.clear();
            finalCarrierDecoratedPotsByPosition.clear();
            cancelAllCanonicalLootOpenReservationsLocked();
            canonicalLootFirstOpenCompleted.clear();
            canonicalLootFirstOpenRejected.clear();
            authoritativeOverlay.clear();
            structureOverlaySites.clear();
            chunkVersions.clear();
            snapshotSendLanes.clear();
            playerContainerActions.clear();
            persistenceCallbackAdmissionClosed = true;
            completedDepartedSaveGenerations.clear();
            PlayerLeaveRequest leave;
            while ((leave = playerLeaveRequests.poll()) != null) supersededLeaves.add(leave);
            supersededLeaves.addAll(activePlayerLeaveRequests);
            activePlayerLeaveRequests.clear();
            disposed = true;
            terminalPhase = TerminalPhase.DISPOSED;
            terminalLeaveSettlementRejected = true;
            lifecycleEpoch++;
        }
        // 정산 창에 걸려 보류돼 있던 블록 편집은 여기서 버린다. 되돌림을 받을 세션도, 다시
        // 적용할 월드도 이 지점 이후에는 없다.
        tickLoop.dropDeferredContainerEdits();
        synchronized (generatedCushionPersistencePublications) {
            for (GeneratedCushionPersistencePublication publication
                    : generatedCushionPersistencePublications.values()) {
                publication.state = PublicationState.CONSUMED;
            }
            generatedCushionPersistencePublications.clear();
        }
        cancelTickSchedule();
        discardQueuedWorkerTasks();
        STRUCTURE_PLANNERS.purge();
        for (DeferredStructureDecoration candidate : deferred) {
            try {
                candidate.cancelPlanning();
            } catch (Throwable failure) {
                recordTerminalCleanupFailure(failure);
            }
        }
        if (publisher != null && persistenceService != null) {
            try {
                persistenceService.unregisterWorldEntityPublisher(worldId, publisher);
            } catch (Throwable failure) {
                recordTerminalCleanupFailure(failure);
            }
            try {
                mobSystem.setStructureEntityDeathListener(null);
            } catch (Throwable failure) {
                recordTerminalCleanupFailure(failure);
            }
        }
        try {
            accessor.evictInactiveSimulationChunks(Set.of());
        } catch (Throwable failure) {
            recordTerminalCleanupFailure(failure);
        }
        snapshotFrameCache.clear();
        failPendingSnapshotEncodes();
        if (ctx.broadcaster() != null) {
            try {
                ctx.broadcaster().forgetWorld(worldId);
            } catch (Throwable failure) {
                recordTerminalCleanupFailure(failure);
            }
        }
        // Exact reservation capabilities were released above. Disposed inventory objects are not
        // mutated through an identity-free lease cancellation path.
        for (PlayerLeaveRequest leave : supersededLeaves) leave.complete(null);
    }

    /**
     * Crosses the shared writer once, then applies every callback admitted before that barrier in
     * FIFO order. A timeout quarantines this runtime without clearing players or installing a late
     * publication; durable facts are recovered by the replacement runtime.
     */
    private boolean drainTerminalPersistenceWithin(long deadlineNanos) {
        if (ownerQueueRequired()) {
            requestTerminalOwnerDrain();
            return false;
        }
        markOwnerThread();
        boolean preparedPublicationsTerminalized = false;
        while (true) {
            drainTerminalPersistenceCompletionBatch();
            if (terminalPhase != TerminalPhase.DRAINING || terminalLeaveSettlementRejected) {
                return false;
            }
            if (!persistenceCompletionFailures.isEmpty()) {
                terminalizeResidualPersistenceCompletions();
                return false;
            }
            if (!terminalAuthorityWorkPending()
                    && persistenceCompletions.isEmpty()
                    && mapPersistenceCompletions.isEmpty()) {
                return true;
            }

            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L) {
                terminalDeadlineReached = true;
                terminalizeResidualPersistenceCompletions();
                return false;
            }
            PersistenceExecutor writer = ctx.persistenceExecutor();
            if (writer == null) {
                // Pure owner callbacks and queued container rejections are locally drainable. A
                // reserved player save, active leave, or generated durable transition still needs
                // the writer and cannot be declared durable by an in-memory drain.
                if (!terminalWorkRequiresWriter()) continue;
                recordPersistenceCompletionFailures(List.of(new IllegalStateException(
                        "terminal authority work requires the persistence writer")));
                terminalizeResidualPersistenceCompletions();
                return false;
            }
            try {
                Future<?> barrier = writer.submitFuture(() -> { });
                if (barrier == null) {
                    recordTerminalCleanupFailure(new IllegalStateException(
                            "persistence barrier was unavailable"));
                    terminalizeResidualPersistenceCompletions();
                    return false;
                }
                barrier.get(remainingNanos, TimeUnit.NANOSECONDS);
            } catch (InterruptedException interrupted) {
                terminalDeadlineReached = true;
                terminalizeResidualPersistenceCompletions();
                abortTerminal();
                Thread.currentThread().interrupt();
                return false;
            } catch (TimeoutException timeout) {
                terminalDeadlineReached = true;
                terminalizeResidualPersistenceCompletions();
                return false;
            } catch (ExecutionException | RejectedExecutionException failure) {
                recordPersistenceCompletionFailures(List.of(failure));
                recordTerminalCleanupFailure(failure);
                terminalizeResidualPersistenceCompletions();
                return false;
            }
            if (!preparedPublicationsTerminalized) {
                terminalizePreparedGeneratedCushionPublications();
                preparedPublicationsTerminalized = true;
            }
        }
    }

    private boolean terminalAuthorityWorkPending() {
        if (!activePlayerLeaveRequests.isEmpty()
                || !pendingGeneratedCushionRiderCleanups.isEmpty()
                || !pendingCommittedGeneratedCushionMutations.isEmpty()
                || !drainingCushionLogoutAdmissions.isEmpty()
                || !pendingDepartedSaves.isEmpty()) return true;
        synchronized (this) {
            if (hasPendingGroundSpawns() || groundSettlementInFlight
                    || playerContainerSettlementInFlight || !playerContainerActions.isEmpty()
                    || !canonicalLootOpenReservations.isEmpty()
                    || !canonicalLootOpenRetries.isEmpty()) return true;
        }
        synchronized (generatedCushionPersistencePublications) {
            return !generatedCushionPersistencePublications.isEmpty();
        }
    }

    private boolean terminalWorkRequiresWriter() {
        if (!activePlayerLeaveRequests.isEmpty()
                || !pendingGeneratedCushionRiderCleanups.isEmpty()
                || !pendingCommittedGeneratedCushionMutations.isEmpty()
                || !drainingCushionLogoutAdmissions.isEmpty()
                || !pendingDepartedSaves.isEmpty()) return true;
        synchronized (this) {
            if (hasPendingGroundSpawns() || groundSettlementInFlight
                    || playerContainerSettlementInFlight || !canonicalLootOpenReservations.isEmpty()
                    || !canonicalLootOpenRetries.isEmpty()) return true;
        }
        synchronized (generatedCushionPersistencePublications) {
            return !generatedCushionPersistencePublications.isEmpty();
        }
    }

    private void drainTerminalPersistenceCompletionBatch() {
        if (ownerQueueRequired()) {
            requestTerminalOwnerDrain();
            return;
        }
        markOwnerThread();
        drainPlayerContainerActionsForTerminal();
        drainMapPersistenceCompletions();
        PersistenceCompletion completion;
        int remaining = Math.min(PERSISTENCE_DRAIN_BATCH_LIMIT, persistenceCompletions.size());
        while (remaining-- > 0 && (completion = persistenceCompletions.poll()) != null) {
            runPersistenceCompletion(completion, terminalPhase);
        }
        if (terminalPhase == TerminalPhase.DRAINING) {
            settlePendingGroundSpawns();
            flushGeneratedEntityMessages();
        }
    }

    /** Rejects queued container inputs in bounded owner turns once normal ticking has stopped. */
    private void drainPlayerContainerActionsForTerminal() {
        int remaining = PERSISTENCE_DRAIN_BATCH_LIMIT;
        while (remaining-- > 0) {
            Runnable action;
            synchronized (this) {
                if (terminalPhase != TerminalPhase.DRAINING
                        || playerContainerActions.isEmpty()) return;
                action = playerContainerActions.pollFirst();
            }
            try {
                action.run();
            } catch (Throwable failure) {
                recordTerminalCleanupFailure(failure);
            }
        }
        if (terminalPhase == TerminalPhase.DRAINING && !playerContainerActions.isEmpty()) {
            requestTerminalOwnerDrain();
        }
    }

    private void terminalizeResidualPersistenceCompletions() {
        if (ownerQueueRequired()) {
            requestTerminalOwnerDrain();
            return;
        }
        markOwnerThread();
        int residual = 0;
        PersistenceCompletion completion;
        int mapResidual = 0;
        boolean previousTerminalization;
        synchronized (this) {
            previousTerminalization = terminalizingPersistenceCompletions;
            terminalizingPersistenceCompletions = true;
        }
        try {
            int remaining = Math.min(PERSISTENCE_DRAIN_BATCH_LIMIT, persistenceCompletions.size());
            while (remaining-- > 0 && (completion = persistenceCompletions.poll()) != null) {
                residual++;
                // Every residual callback is settled generically. Its live half is never replayed
                // after the terminal boundary, while release/in-flight bookkeeping still completes.
                runPersistenceCompletion(completion, TerminalPhase.ABORTED);
            }
            int remainingMaps = Math.min(PERSISTENCE_DRAIN_BATCH_LIMIT,
                    mapPersistenceCompletions.size());
            while (remainingMaps-- > 0
                    && (completion = mapPersistenceCompletions.poll()) != null) {
                mapResidual++;
                runPersistenceCompletion(completion, TerminalPhase.ABORTED);
            }
        } finally {
            synchronized (this) {
                terminalizingPersistenceCompletions = previousTerminalization;
            }
        }
        if (residual != 0 || mapResidual != 0) {
            log.warn("Terminalized {} persistence callbacks for recovery: world={}",
                    residual + mapResidual, worldId);
        }
        if (terminalPersistenceQueueHasResidue()) {
            requestTerminalOwnerDrain();
        }
    }

    /** PREPARED means no durable commit callback crossed the writer barrier; revoke it permanently. */
    private void terminalizePreparedGeneratedCushionPublications() {
        synchronized (generatedCushionPersistencePublications) {
            Iterator<Map.Entry<Long, GeneratedCushionPersistencePublication>> iterator =
                    generatedCushionPersistencePublications.entrySet().iterator();
            while (iterator.hasNext()) {
                GeneratedCushionPersistencePublication publication = iterator.next().getValue();
                if (publication.state != PublicationState.PREPARED) continue;
                publication.state = PublicationState.REVOKED;
                iterator.remove();
            }
        }
    }

    /**
     * Final player snapshots still need their live objects, but generated seats must commit the
     * same logout transition before those objects are cleared. A bounded failure leaves the runtime
     * undisposed and all players intact so shutdown cannot publish or save a half-disposed world.
     */
    private void commitGeneratedCushionLogoutsBeforeShutdown() {
        Set<String> departing = Set.copyOf(players.keySet());
        if (departing.isEmpty() && !generatedCushionShutdownPending()) return;
        drainCommittedGeneratedCushionMutations();
        for (String actor : departing) {
            processGeneratedCushionRiderState(actor,
                    GeneratedCushionActionPolicy.Action.RIDER_LOGOUT);
        }
        for (int attempt = 1; attempt < FORCED_CUSHION_LOGOUT_ATTEMPTS
                && generatedCushionShutdownPending(); attempt++) {
            drainCommittedGeneratedCushionMutations();
            retryPendingGeneratedCushionRiderCleanups();
        }
        if (generatedCushionShutdownPending()) {
            throw new IllegalStateException(
                    "generated Cushion rider cleanup did not commit before shutdown");
        }
    }

    private boolean generatedCushionShutdownPending() {
        boolean publicationPending;
        synchronized (generatedCushionPersistencePublications) {
            publicationPending = !generatedCushionPersistencePublications.isEmpty();
        }
        return !pendingGeneratedCushionRiderCleanups.isEmpty()
                || !pendingCommittedGeneratedCushionMutations.isEmpty()
                || !drainingCushionLogoutAdmissions.isEmpty()
                || publicationPending;
    }

    /** Closes live callback admission and drains the already-admitted FIFO before DISPOSED. */
    private boolean closePersistenceCallbackAdmissionAndDrain(long deadlineNanos) {
        synchronized (this) {
            if (terminalPhase != TerminalPhase.DRAINING) return false;
            persistenceCallbackAdmissionClosed = true;
        }
        while (true) {
            drainTerminalPersistenceCompletionBatch();
            if (terminalPhase != TerminalPhase.DRAINING || terminalLeaveSettlementRejected) {
                return false;
            }
            if (!persistenceCompletionFailures.isEmpty()) return false;
            boolean authorityPending;
            synchronized (this) {
                authorityPending = hasPendingGroundSpawns() || groundSettlementInFlight
                        || !pendingDepartedSaves.isEmpty()
                        || playerContainerSettlementInFlight
                        || !playerContainerActions.isEmpty();
            }
            if (!authorityPending && persistenceCompletions.isEmpty()
                    && mapPersistenceCompletions.isEmpty()) {
                return true;
            }
            // The first terminal drain owns all admissions made before this fence. A pending
            // departed save or container transaction here means a concurrent caller crossed the
            // lifecycle boundary without a completed writer callback; do not clear its authority
            // or claim DRAINED. abortTerminal() will settle it generically.
            if (authorityPending) return false;
            if (System.nanoTime() >= deadlineNanos) {
                terminalDeadlineReached = true;
                terminalizeResidualPersistenceCompletions();
                return false;
            }
        }
    }

    /**
     * 최종 저장 작업 뒤에 같은 단일 writer의 장벽을 둡니다. 실패로 dirty가 복원되면 런타임 등록을
     * 유지한 채 1초 뒤 다시 시도하므로 월드 삭제가 늦은 쓰기와 경합하지 않습니다.
     */
    private void beginDisposalPersistence() {
        if (ownerQueueRequired()) {
            if (!enqueueOwnerTask(this::beginDisposalPersistence)) {
                recordTerminalCleanupFailure(new IllegalStateException(
                        "owner queue rejected disposal persistence"));
            }
            return;
        }
        markOwnerThread();
        try {
            beginDisposalPersistenceAttempt();
        } catch (Throwable failure) {
            markDisposalPersistenceFailed(failure);
            ScheduledExecutorService current = executor;
            if (current != null && !current.isShutdown()) {
                try {
                    current.schedule(this::beginDisposalPersistence, 1, TimeUnit.SECONDS);
                } catch (Throwable schedulingFailure) {
                    recordTerminalCleanupFailure(schedulingFailure);
                    finishDisposal();
                }
            } else {
                finishDisposal();
            }
        }
    }

    private void beginDisposalPersistenceAttempt() {
        markOwnerThread();
        disposalPersistenceFailed = false;
        PersistenceExecutor persistence = ctx.persistenceExecutor();
        // Resume only an already-started command; cold/future durable rows belong to restart.
        runtimeFallingSpeleothems.retryBeforePrelude();
        cushionSystem.flushForDisposal();
        boatSystem.flushForDisposal();
        endGateways.flushForDisposal();
        dragonFight.flushForDisposal();
        placedEntities.flushForDisposal();
        // Animal settlements may have been rejected by backpressure on their final owner tick.
        // Re-enter those immutable commands before placing the FIFO disposal barrier. A missing
        // writer is a terminal persistence failure, not a reason to strand disposal forever.
        if (persistence != null) {
            try {
                tickLoop.retryAnimalSettlementPersistenceForDisposal();
            } catch (Throwable failure) {
                markDisposalPersistenceFailed(failure);
            }
            try {
                mobSystem.retryAnimalSettlementPersistenceForDisposal();
            } catch (Throwable failure) {
                markDisposalPersistenceFailed(failure);
            }
        }
        // A victory may be the final owner-tick edge. Re-enter the same durable lane before the
        // disposal writer barrier so a rejected/failed key settlement remains retryable.
        if (trialPersistence != null) mobSystem.syncTrialSpawnerOutcome();
        // 주기 틱은 이미 멈췄다. 기존 map writer가 남긴 승인/거부 콜백을 여기서 적용해야
        // in-flight 표식이나 실패 뒤 복원된 dirty 영역을 마지막 저장 시도에 포함할 수 있다.
        drainMapPersistenceCompletions();
        if (mobMutationJournal != null) mobMutationJournal.flushPendingPersistence();
        if (persistence == null) {
            retryPendingBannerPersistence(true);
            retryPendingSignPersistence(true);
            drainPersistenceCompletions();
            forceGroundCheckpointForDisposal();
            forceProjectileCheckpointForDisposal();
            if (!tickLoop.usesCombinedBlockTntCheckpoint() && ctx.blockDiffFlusher() != null) {
                ctx.blockDiffFlusher().flushWorldAsync(worldId);
            }
            flushDirtyChests();
            flushDirtyFurnaces();
            flushDirtyBrewing();
            flushDirtyCampfires();
            flushPersistentMobs();
            tickLoop.flushPrimedTnt();
            fallingSpeleothems.flushPending();
            if (worldTimePersistence != null) {
                worldTimePersistence.saveClockState(worldId, currentClockState(), tickLoop.redstoneSnapshot());
            }
            finishDisposal();
            return;
        }
        retryPendingBannerPersistence(true);
        retryPendingSignPersistence(true);
        if (!tickLoop.flushEyeblossomSchedulesForDisposal()) {
            markDisposalPersistenceFailed();
        }
        flushDirtyMapsForDisposal();
        forceGroundCheckpointForDisposal();
        forceProjectileCheckpointForDisposal();
        for (PlayerTickState state : disposalPlayers) {
            saveState(state, null,
                    () -> enqueuePersistenceCompletion(
                            this::markDisposalPersistenceFailed,
                            this::markDisposalPersistenceFailed),
                    null, false, true);
        }
        if (!tickLoop.usesCombinedBlockTntCheckpoint() && ctx.blockDiffFlusher() != null) {
            ctx.blockDiffFlusher().flushWorldAsync(worldId);
        }
        flushDirtyChests();
        flushDirtyFurnaces();
        flushDirtyBrewing();
        flushDirtyCampfires();
        tickLoop.flushPrimedTnt();
        fallingSpeleothems.flushPending();
        if (mobPersistence != null && !trialPersistenceInFlight) {
            MobSystem.PopulationPersistenceSnapshot snapshot =
                    mobSystem.populationPersistenceSnapshot();
            if (!persistence.trySubmit(() -> {
                try {
                    persistMobsAndPopulation(snapshot);
                } catch (RuntimeException | Error exception) {
                    enqueuePersistenceCompletion(
                            () -> markDisposalPersistenceFailed(exception),
                            () -> markDisposalPersistenceFailed(exception));
                    throw exception;
                }
            })) {
                markDisposalPersistenceFailed();
            }
        }
        if (worldTimePersistence != null) {
            WorldTimePersistenceService.ClockState clockState = currentClockState();
            String redstoneSnapshot = tickLoop.redstoneSnapshot();
            if (!persistence.trySubmit(() -> {
                try {
                    worldTimePersistence.saveClockState(worldId, clockState, redstoneSnapshot);
                } catch (RuntimeException | Error exception) {
                    enqueuePersistenceCompletion(
                            () -> markDisposalPersistenceFailed(exception),
                            () -> markDisposalPersistenceFailed(exception));
                    throw exception;
                }
            })) {
                markDisposalPersistenceFailed();
            }
        }
        if (!persistence.trySubmit(this::completeDisposalPersistenceAttempt)) {
            markDisposalPersistenceFailed();
            ScheduledExecutorService current = executor;
            if (current != null && !current.isShutdown()) {
                current.schedule(this::beginDisposalPersistence, 1, TimeUnit.SECONDS);
            }
        }
    }

    private WorldTimePersistenceService.ClockState currentClockState() {
        long[] traderWindow = mobSystem.traderWindowState();
        return new WorldTimePersistenceService.ClockState(
                clock.dayCount(), clock.worldTime(), clock.gameTimeMcTicks(),
                new WorldTimePersistenceService.TraderWindow(
                        traderWindow[0], (int) traderWindow[1]));
    }

    /** Final sidecar writes report failures through bookkeeping admitted after the live fence. */
    void submitDisposalPersistence(Runnable write) {
        PersistenceExecutor writer = ctx.persistenceExecutor();
        if (writer == null) {
            write.run();
            return;
        }
        writer.submitFuture(() -> {
            try {
                write.run();
            } catch (RuntimeException | Error failure) {
                Runnable failed = () -> markDisposalPersistenceFailed(failure);
                enqueuePersistenceCompletion(failed, failed);
            }
        });
    }

    private void completeDisposalPersistenceAttempt() {
        if (ownerQueueRequired()) {
            if (!enqueueOwnerTask(this::completeDisposalPersistenceAttempt)) {
                terminalDeadlineReached = true;
                recordTerminalCleanupFailure(new IllegalStateException(
                        "owner queue rejected disposal completion"));
            }
            return;
        }
        markOwnerThread();
        // 단일 writer 장벽보다 앞선 map 작업은 이 시점에 반드시 끝났다. 틱이 없는 폐기 단계에서는
        // 장벽 스레드가 그 완료를 적용한 뒤 dirty/pending 여부를 판정한다.
        drainMapPersistenceCompletions();
        drainPersistenceCompletions();
        boolean blockPending = ctx.blockDiffBuffer() != null
                && ctx.blockDiffBuffer().hasPending(worldId);
        boolean journalPending = mobMutationJournal != null
                && mobMutationJournal.hasPendingPersistence();
        boolean liveOwnerStatePending = terminalPhase != TerminalPhase.DISPOSED
                && (tickLoop.hasPendingAnimalSettlementPersistence()
                        || mobSystem.hasPendingAnimalSettlementPersistence()
                        || mobSystem.hasUnpersistedPopulationChunks());
        if (disposalPersistenceFailed || blockPending || journalPending || chestStorage.hasDirty()
                || shulkerStorage.hasDirty()
                || furnaceStorage.hasDirty()
                || brewingStorage.hasDirty()
                || !dirtyCrafterMasks.isEmpty()
                || xpOrbSystem.furnaceXpCarryDirty()
                || campfireStorage.hasDirty()
                || enchantingPersistence != null && enchantingStorage.hasDirty()
                || worldMaps.hasPendingPersistence()
                || animalBlockTickStore != null && animalBlockTickStore.hasPendingWrites()
                || animalSettlementWriterTasks.get() != 0
                || tickLoop.hasPendingPrimedTntPersistence()
                || runtimeFallingSpeleothems.pausesSimulation()
                || fallingSpeleothems.inFlight || fallingSpeleothems.retrySave != null
                || liveOwnerStatePending
                || groundPersistencePending()
                || projectilePersistencePending()
                || bannerPersistencePending()
                || signPersistencePending()
                || !pendingDepartedSaves.isEmpty()) {
            ScheduledExecutorService current = executor;
            if (current != null && !current.isShutdown()) {
                current.schedule(this::beginDisposalPersistence, 1, TimeUnit.SECONDS);
            }
            return;
        }
        finishDisposal();
    }

    /** flush 간격을 기다리지 않고 폐기 시점의 모든 dirty 지도 revision을 직렬 writer에 제출합니다. */
    private void flushDirtyMapsForDisposal() {
        PersistenceExecutor persistence = ctx.persistenceExecutor();
        if (worldMapPersistence == null || persistence == null) return;
        for (int mapId : worldMaps.mapIdsNeedingPersistence()) {
            WorldMapRuntime.PendingPatch patch = worldMaps.prepareDirtyPatch(mapId);
            // 이미 writer에 제출된 지도는 그 작업 뒤의 장벽에서 완료 콜백을 적용한 다음 재판정한다.
            if (patch == null) continue;
            WorldMapData candidate = patch.candidate();
            boolean submitted = persistence.trySubmit(() -> {
                try {
                    WorldMapData durable = worldMapPersistence.replaceColors(
                            candidate.getWorldId(), candidate.getMapId(),
                            candidate.getColors(), candidate.getRevision());
                    // Disposal has already closed live callback admission. Acknowledging this
                    // exact durable patch is terminal bookkeeping, not a live map publication.
                    enqueueMapPersistenceCompletion(
                            null, () -> worldMaps.acceptPatch(patch, durable));
                } catch (RuntimeException | Error failure) {
                    enqueueMapPersistenceCompletion(
                            () -> {
                                worldMaps.rejectPatch(patch);
                                markDisposalPersistenceFailed(failure);
                            },
                            () -> {
                                worldMaps.rejectPatch(patch);
                                markDisposalPersistenceFailed(failure);
                            });
                    throw failure;
                }
            });
            if (!submitted) {
                worldMaps.rejectPatch(patch);
                markDisposalPersistenceFailed();
            }
        }
    }

    private void finishDisposal() {
        if (ownerQueueRequired()) {
            if (!enqueueOwnerTask(this::finishDisposal)) {
                terminalDeadlineReached = true;
                recordTerminalCleanupFailure(new IllegalStateException(
                        "owner queue rejected final disposal cleanup"));
            }
            return;
        }
        markOwnerThread();
        // DISPOSED admits only generic bookkeeping. Keep the final callback fence FIFO bounded;
        // residue gets another owner task instead of running an unbounded terminal loop.
        drainMapPersistenceCompletions();
        drainPersistenceCompletions();
        if (!persistenceCompletions.isEmpty() || !mapPersistenceCompletions.isEmpty()) {
            if (!enqueueOwnerTask(this::finishDisposal)) {
                terminalDeadlineReached = true;
                recordTerminalCleanupFailure(new IllegalStateException(
                        "owner queue rejected residual disposal callbacks"));
                publishTerminalOutcome(TerminalOutcomeKind.DEADLINE);
                disposalCompletion.complete(null);
            }
            return;
        }
        Runnable disposeCallback;
        ScheduledExecutorService current;
        synchronized (this) {
            if (disposalCompletion.isDone() || !disposed) return;
            disposeCallback = onDispose;
            onDispose = null;
            disposalPlayers = List.of();
            current = executor;
        }
        try {
            if (ctx.blockDiffFlusher() != null) {
                ctx.blockDiffFlusher().releaseCombinedCheckpoint(worldId);
            }
        } catch (Throwable failure) {
            recordTerminalCleanupFailure(failure);
        }
        if (disposeCallback != null) {
            try {
                disposeCallback.run();
            } catch (Throwable failure) {
                recordTerminalCleanupFailure(failure);
            }
        }
        try {
            if (current != null) current.shutdown();
        } catch (Throwable failure) {
            recordTerminalCleanupFailure(failure);
        }
        publishTerminalOutcome(TerminalOutcomeKind.DRAINED);
        disposalCompletion.complete(null);
    }

    /** 틱 스레드에서 스냅샷을 떠 persistence executor로 저장을 넘깁니다(위치·체력·인벤토리). */
    void saveState(PlayerTickState state) {
        saveState(state, null, null, null);
    }

    /** 주기 저장: DB 성공 승인 전에는 dirty를 유지하고, 한 플레이어의 중복 제출은 막습니다. */
    void saveDirtyState(PlayerTickState state) {
        if (terminalPhase != TerminalPhase.RUNNING) return;
        if (!state.beginPersistence()) return;
        long stateRevision = state.stateRevision();
        long inventoryRevision = state.inventory().revision();
        saveState(state,
                () -> enqueuePersistenceCompletion(
                        () -> state.acknowledgePersistence(stateRevision, inventoryRevision),
                        state::rejectPersistence),
                () -> enqueuePersistenceCompletion(state::rejectPersistence,
                        state::rejectPersistence),
                () -> enqueuePersistenceCompletion(state::rejectPersistence,
                        state::rejectPersistence));
    }

    /**
     * Queues the inventory revision consumed by a successful world edit before a later container
     * settlement can enter the same single-writer lane. Runtime-only unit contexts intentionally
     * have no persistence boundary; production contexts always provide both collaborators.
     */
    /**
     * Same baseline, but the edit's own cells travel with it: the drained diffs and the inventory
     * that paid for them commit in one transaction, so an owner crash cannot keep one without the
     * other. Callers use this only for edits with no separate block-entity settlement.
     */
    void queuePlayerInventoryBaselineWithEditCells(PlayerTickState state) {
        if (terminalPhase != TerminalPhase.RUNNING
                || ctx.persistenceExecutor() == null || ctx.stateService() == null) return;
        java.util.Map<BlockPos, com.gameexpert.block.persistence.BlockDiffBuffer.Change> diffs =
                ctx.blockDiffBuffer() == null ? java.util.Map.of()
                        : ctx.blockDiffBuffer().drain(worldId);
        state.beginPersistence();
        long stateRevision = state.stateRevision();
        long inventoryRevision = state.inventory().revision();
        Runnable restore = () -> {
            if (ctx.blockDiffBuffer() != null) ctx.blockDiffBuffer().restore(worldId, diffs);
        };
        saveState(state,
                () -> enqueuePersistenceCompletion(
                        () -> state.acknowledgePersistence(stateRevision, inventoryRevision),
                        state::rejectPersistence),
                () -> { restore.run(); enqueuePersistenceCompletion(state::rejectPersistence,
                        state::rejectPersistence); },
                () -> { restore.run(); enqueuePersistenceCompletion(state::rejectPersistence,
                        state::rejectPersistence); },
                false, false, diffs);
    }

    void queuePlayerInventoryBaseline(PlayerTickState state) {
        if (terminalPhase != TerminalPhase.RUNNING
                || ctx.persistenceExecutor() == null || ctx.stateService() == null) return;
        // Do not skip this snapshot when an older periodic save is already in flight. Both writes
        // enter the same FIFO writer, so the exact latest baseline is committed after the older
        // one and before the subsequently enqueued container CAS.
        state.beginPersistence();
        long stateRevision = state.stateRevision();
        long inventoryRevision = state.inventory().revision();
        saveState(state,
                () -> enqueuePersistenceCompletion(
                        () -> state.acknowledgePersistence(stateRevision, inventoryRevision),
                        state::rejectPersistence),
                () -> enqueuePersistenceCompletion(state::rejectPersistence,
                        state::rejectPersistence),
                () -> enqueuePersistenceCompletion(state::rejectPersistence,
                        state::rejectPersistence));
    }

    /**
     * Admits one exact departed-state generation. The admission is made before crossing to the
     * writer, and later callers for that generation only append an owner callback; they never
     * submit a second database transaction.
     */
    boolean saveDepartedState(PlayerTickState state, Runnable afterSave) {
        if (state == null || ctx.persistenceExecutor() == null || ctx.stateService() == null) {
            return false;
        }
        DepartedSaveKey key = new DepartedSaveKey(state, state.stateRevision(),
                state.inventory().revision());
        DepartedSave pending = null;
        boolean submit = false;
        boolean alreadyCommitted = false;
        synchronized (this) {
            TerminalPhase phase = terminalPhase;
            boolean callbackAdmissionOpen = (phase == TerminalPhase.RUNNING
                    || phase == TerminalPhase.DRAINING) && !persistenceCallbackAdmissionClosed;
            if (!callbackAdmissionOpen) return false;
            pending = pendingDepartedSaves.get(key);
            if (pending != null) {
                if (!pending.addCallback(afterSave)) return false;
            } else if (completedDepartedSaveGenerations.containsKey(key)) {
                alreadyCommitted = true;
            } else {
                // A new generation is admitted only by the live running phase. An already
                // admitted generation may still receive a coalesced callback during DRAINING.
                if (phase != TerminalPhase.RUNNING
                        || pendingDepartedSaves.size() >= MAX_RUNTIME_ADMISSIONS) return false;
                pending = new DepartedSave(key, state, afterSave);
                pendingDepartedSaves.put(key, pending);
                submit = true;
            }
        }
        if (alreadyCommitted) {
            return afterSave == null || enqueuePersistenceCompletion(afterSave, null);
        }
        if (submit) submitDepartedSave(pending);
        return true;
    }

    /** Reserves the exact pending entry before any asynchronous writer submission. */
    private void submitDepartedSave(DepartedSave pending) {
        synchronized (this) {
            if (pending == null || pendingDepartedSaves.get(pending.key) != pending
                    || pending.submissionReserved
                    || (terminalPhase != TerminalPhase.RUNNING
                        && terminalPhase != TerminalPhase.DRAINING)) return;
            pending.retryScheduled = false;
            pending.submissionReserved = true;
        }
        Runnable ownerCompleted = () -> settleDepartedSave(pending, true);
        Runnable ownerRetry = () -> retryDepartedSave(pending);
        Runnable ownerCleanup = () -> discardDepartedSave(pending);
        boolean accepted;
        try {
            accepted = saveState(pending.state,
                    () -> enqueuePersistenceCompletion(ownerCompleted, ownerCleanup),
                    () -> enqueuePersistenceCompletion(ownerRetry, ownerCleanup),
                    () -> enqueuePersistenceCompletion(ownerRetry, ownerCleanup),
                    true, false);
        } catch (RuntimeException | Error failure) {
            synchronized (this) {
                if (pendingDepartedSaves.get(pending.key) == pending) {
                    pending.submissionReserved = false;
                }
            }
            throw failure;
        }
        if (!accepted) {
            synchronized (this) {
                if (pendingDepartedSaves.get(pending.key) == pending) {
                    pending.submissionReserved = false;
                }
            }
        }
    }

    private void retryDepartedSave(DepartedSave pending) {
        boolean remove = false;
        synchronized (this) {
            if (pendingDepartedSaves.get(pending.key) != pending) return;
            if (pending.retryScheduled) return;
            pending.submissionReserved = false;
            if (terminalPhase != TerminalPhase.RUNNING
                    && terminalPhase != TerminalPhase.DRAINING) {
                remove = true;
            } else {
                pending.retryScheduled = true;
            }
        }
        if (remove) {
            discardDepartedSave(pending);
            return;
        }
        ScheduledExecutorService current = executor;
        if (current != null && !current.isShutdown() && !current.isTerminated()) {
            try {
                current.schedule(() -> submitDepartedSave(pending), 1, TimeUnit.SECONDS);
                return;
            } catch (RuntimeException | Error failure) {
                synchronized (this) {
                    if (pendingDepartedSaves.get(pending.key) == pending) {
                        pending.retryScheduled = false;
                    }
                }
                throw failure;
            }
        }
        if (!started) {
            synchronized (this) {
                if (pendingDepartedSaves.get(pending.key) == pending) {
                    pending.retryScheduled = false;
                }
            }
            submitDepartedSave(pending);
            return;
        }
        synchronized (this) {
            if (pendingDepartedSaves.get(pending.key) == pending) {
                pending.retryScheduled = false;
            }
        }
        throw new IllegalStateException("departed save retry owner executor unavailable");
    }

    /** Removes a committed generation once and runs every callback admitted for it exactly once. */
    private void settleDepartedSave(DepartedSave pending, boolean runCallbacks) {
        List<Runnable> callbacks = List.of();
        synchronized (this) {
            if (pending == null || !pendingDepartedSaves.remove(pending.key, pending)) return;
            pending.submissionReserved = false;
            pending.retryScheduled = false;
            completedDepartedSaveGenerations.put(pending.key, Boolean.TRUE);
            while (completedDepartedSaveGenerations.size() > MAX_RUNTIME_ADMISSIONS) {
                completedDepartedSaveGenerations.remove(
                        completedDepartedSaveGenerations.keySet().iterator().next());
            }
            if (runCallbacks && (terminalPhase == TerminalPhase.RUNNING
                    || terminalPhase == TerminalPhase.DRAINING)) {
                callbacks = List.copyOf(pending.callbacks);
            }
            pending.callbacks.clear();
        }
        Throwable firstFailure = null;
        for (Runnable callback : callbacks) {
            if (callback == null) continue;
            try {
                callback.run();
            } catch (Throwable failure) {
                if (firstFailure == null) firstFailure = failure;
                else firstFailure.addSuppressed(failure);
            }
        }
        if (firstFailure instanceof RuntimeException failure) throw failure;
        if (firstFailure instanceof Error failure) throw failure;
        if (firstFailure != null) {
            throw new IllegalStateException("departed save callback failed", firstFailure);
        }
    }

    private void discardDepartedSave(DepartedSave pending) {
        if (pending == null) return;
        synchronized (this) {
            if (pendingDepartedSaves.remove(pending.key, pending)) {
                pending.submissionReserved = false;
                pending.retryScheduled = false;
                pending.callbacks.clear();
            }
        }
    }

    private void saveState(PlayerTickState state, Runnable afterSave, Runnable afterFailure,
            Runnable afterStale) {
        saveState(state, afterSave, afterFailure, afterStale, false, false);
    }

    private boolean saveState(PlayerTickState state, Runnable afterSave, Runnable afterFailure,
            Runnable afterStale, boolean allowDraining, boolean allowDisposed) {
        return saveState(state, afterSave, afterFailure, afterStale, allowDraining, allowDisposed,
                java.util.Map.of());
    }

    private boolean saveState(PlayerTickState state, Runnable afterSave, Runnable afterFailure,
            Runnable afterStale, boolean allowDraining, boolean allowDisposed,
            java.util.Map<BlockPos, com.gameexpert.block.persistence.BlockDiffBuffer.Change>
                    blockDiffs) {
        TerminalPhase phase = terminalPhase;
        if (phase != TerminalPhase.RUNNING
                && !(allowDraining && phase == TerminalPhase.DRAINING)
                && !(allowDisposed && phase == TerminalPhase.DISPOSED)) return false;
        PersistenceExecutor writer = ctx.persistenceExecutor();
        PlayerWorldStateService stateService = ctx.stateService();
        if (writer == null || stateService == null) {
            if (afterFailure != null) afterFailure.run();
            return false;
        }
        Long playerId = state.playerId();
        double x = state.x();
        double y = state.y();
        double z = state.z();
        float yaw = state.yaw();
        float pitch = state.pitch();
        int health = state.health();
        PlayerInventory.CompletePersistenceSnapshot complete =
                state.inventory().completePersistenceSnapshot();
        PlayerInventory.PersistenceSnapshot inventory = complete.persistenceSnapshot();
        long inventoryRevision = complete.revision();
        short[] equippedTypes = complete.equippedTypes();
        int[] equippedDurabilities = complete.equippedDurabilities();
        Integer spawnX = state.hasBedSpawn() ? state.bedSpawnX() : null;
        Integer spawnY = state.hasBedSpawn() ? state.bedSpawnY() : null;
        Integer spawnZ = state.hasBedSpawn() ? state.bedSpawnZ() : null;
        int hunger = state.food();
        int saturationMilli = state.saturationMilli();
        // [SURV-X] 인챈트 마스크·누적 경험치·인챈트 시드도 같은 호출에 실어야 한다.
        // 이 saveRuntime 이 런타임의 유일한 DB 쓰기 경로라, 여기서 빠진 필드는 재접속 때 사라진다.
        long[] equippedEnchantments = complete.equippedEnchantments();
        String[] equippedItemComponentData = complete.equippedItemComponentData();
        int xpTotal = state.xpTotal();
        int enchantSeed = state.enchantSeed();
        // [PHANTOM] 불면 시간도 같은 호출에 실어야 한다. saveRuntime 이 런타임의 유일한 DB 쓰기
        // 경로라, 여기서 빠지면 재접속마다 "방금 잔 것"으로 되감겨 팬텀이 영원히 나오지 않는다.
        long timeSinceRestMcTicks = state.timeSinceRestMcTicks();
        // [ENDER-SHULKER] 엔더 상자 27칸도 같은 호출에 실어야 한다. 이 saveRuntime 이 런타임의
        // 유일한 DB 쓰기 경로이므로, 여기서 빠지면 넣어 둔 아이템이 재접속 때 사라진다.
        ChestInventory.Snapshot enderChest = state.enderChest().snapshot();
        List<StatusEffects.PersistentEffect> statusEffects =
                state.statusEffects().persistenceSnapshot();
        StatusEffects.PersistentPlayerEffectClocks effectClocks = state.effectClocksSnapshot();
        int selectedSlot = complete.selectedSlot();
        int fireTicks = state.fireTicks();
        int fireAccum = state.fireAccum();
        AtomicBoolean failureNotified = new AtomicBoolean();
        Runnable notifyFailure = () -> {
            if (afterFailure != null && failureNotified.compareAndSet(false, true)) {
                afterFailure.run();
            }
        };
        boolean accepted;
        try {
            accepted = writer.trySubmit(() -> {
            try {
                java.util.function.Supplier<PlayerWorldStateService.RuntimeSaveOutcome> save =
                        () -> stateService.saveDimensionRuntime(playerId, worldId, inventoryRevision,
                        x, y, z, yaw, pitch, health,
                        inventory.itemTypes(), inventory.counts(), inventory.durabilities(),
                        inventory.enchantments(), inventory.mapIds(), inventory.shulkerIds(),
                        inventory.bucketMobData(), inventory.itemComponentData(),
                        equippedTypes, equippedDurabilities, equippedEnchantments,
                        equippedItemComponentData,
                        inventory.offhand(),
                        spawnX, spawnY, spawnZ, hunger, saturationMilli, xpTotal, enchantSeed,
                        timeSinceRestMcTicks, enderChest, statusEffects, effectClocks,
                        selectedSlot, fireTicks, fireAccum, state.dimensionIdentity(worldId));
                PlayerWorldStateService.RuntimeSaveOutcome outcome = blockDiffs.isEmpty()
                        ? save.get()
                        : stateService.withBlockDiffs(worldId, blockDiffs, save);
                if (outcome == PlayerWorldStateService.RuntimeSaveOutcome.STALE) {
                    if (afterStale != null) afterStale.run();
                } else if (afterSave != null) {
                    afterSave.run();
                }
            } catch (RuntimeException | Error exception) {
                notifyFailure.run();
                throw exception;
            }
            });
        } catch (RuntimeException | Error failure) {
            notifyFailure.run();
            throw failure;
        }
        if (!accepted) notifyFailure.run();
        return accepted;
    }

    private record DepartedSaveKey(PlayerTickState state, long stateRevision,
            long inventoryRevision) {
        private DepartedSaveKey {
            java.util.Objects.requireNonNull(state, "departed save state");
            if (stateRevision < 0L || inventoryRevision < 0L) {
                throw new IllegalArgumentException("departed save revisions must be non-negative");
            }
        }
    }

    private static final class DepartedSave {
        private final DepartedSaveKey key;
        private final PlayerTickState state;
        private final List<Runnable> callbacks = new ArrayList<>();
        private boolean submissionReserved;
        private boolean retryScheduled;

        private DepartedSave(DepartedSaveKey key, PlayerTickState state, Runnable afterSave) {
            this.key = key;
            this.state = state;
            addCallback(afterSave);
        }

        private boolean addCallback(Runnable callback) {
            if (callback == null) return true;
            if (callbacks.size() >= MAX_RUNTIME_ADMISSIONS) return false;
            callbacks.add(callback);
            return true;
        }
    }

    record PlayerInventorySettlementReservation(String nickname, String connectionId,
            PlayerTickState player, PlayerInventory inventory,
            PlayerInventory.CompletePersistenceSnapshot source) {
        PlayerInventorySettlementReservation {
            java.util.Objects.requireNonNull(nickname, "settlement nickname");
            java.util.Objects.requireNonNull(connectionId, "settlement connection");
            java.util.Objects.requireNonNull(player, "settlement player");
            java.util.Objects.requireNonNull(inventory, "settlement inventory");
            java.util.Objects.requireNonNull(source, "settlement source capability");
            if (!nickname.equals(player.nickname())
                    || player.inventory() != inventory) {
                throw new IllegalArgumentException("invalid player inventory settlement authority");
            }
        }
    }

    static final class PlayerLeaveRequest {
        private final String nickname;
        private final String connectionId;
        private final CompletableFuture<PlayerTickState> completion = new CompletableFuture<>();

        private PlayerLeaveRequest(String nickname, String connectionId) {
            this.nickname = nickname;
            this.connectionId = connectionId;
        }

        String nickname() {
            return nickname;
        }

        String connectionId() {
            return connectionId;
        }

        CompletableFuture<PlayerTickState> completion() {
            return completion;
        }

        void complete(PlayerTickState state) {
            completion.complete(state);
        }

        void fail(Throwable error) {
            completion.completeExceptionally(error);
        }

    }

    private final RuntimeFallingSpeleothems runtimeFallingSpeleothems = new RuntimeFallingSpeleothems(this);

    RuntimeFallingSpeleothems runtimeFallingSpeleothems() { return runtimeFallingSpeleothems; }

    /** Immutable carrier cells remain exact; explicit runtime overlays are decoded as compact. */
    Mc263FinalCarrierTickSemantics.SemanticWorld runtimeFallingSemanticWorld() {
        return finalCarrierTickSemanticWorld();
    }


    void publishRuntimeSpeleothemFall(com.gameexpert.falling.dto.RuntimeSpeleothemFall fall,
            List<FinalCarrierTickScheduler.BlockMutation> cells) {
        if (terminalPhase != TerminalPhase.RUNNING) return;
        for (var cell : cells) {
            setIndexedOverlay(cell.x(), cell.y(), cell.z(), cell.blockId(), cell.blockState());
            tickBlockChanges.put(new BlockPos(cell.x(), cell.y(), cell.z()), (short) cell.blockId());
            fluidSim.onBlockChanged(cell.x(), cell.y(), cell.z());
        }
        fallingSpeleothems.restore(fall.births(cells));
    }

    private final FallingSpeleothems fallingSpeleothems = new FallingSpeleothems();

    void tickFallingSpeleothems() { fallingSpeleothems.tick(); }

    private final class FallingSpeleothems {
        private final Map<String, com.gameexpert.falling.dto.FallingSpeleothemState> states = new LinkedHashMap<>();
        private boolean inFlight;
        private Runnable retrySave;

        void restore(List<com.gameexpert.falling.dto.FallingSpeleothemState> restored) {
            for (var state : restored) states.putIfAbsent(state.id(), state);
        }

        void broadcast() {
            if (ctx.broadcaster() == null) return;
            var visible = states.values().stream().filter(state -> !state.finished()).map(state ->
                    Map.of("id", state.id(), "blockId", state.blockId(), "blockState", state.blockState(),
                            "x", state.x(), "y", state.y(), "z", state.z(), "vy", state.velocityY() * 20)).toList();
            ctx.broadcaster().enqueueBroadcastFromTick(worldId,
                    Map.of("type", "fallingSpeleothems", "entities", visible));
        }

        double clip(com.gameexpert.falling.dto.FallingSpeleothemState state, double dy) {
            int x = (int) Math.floor(state.x()), z = (int) Math.floor(state.z());
            double[] clipped = {dy};
            for (int y = (int) Math.floor(state.y()); y >= Math.floor(state.y() + dy) - 1; y--) {
                if (y < Blocks.MIN_Y) continue;
                int id = WorldTickLoop.residentBlockType(accessor, x, y, z);
                if (id == WorldTickLoop.UNAVAILABLE_BLOCK) return Double.NaN;
                int blockY = y;
                int code = blockStates.get(x, y, z, id);
                BuildingBlockRules.forCollisionBoxes(id, code, x, z, (minX, minY, minZ, maxX, maxY, maxZ) -> {
                    double top = blockY + maxY;
                    if (maxX > .01 && minX < .99 && maxZ > .01 && minZ < .99
                            && top <= state.y() + 1e-7 && top > state.y() + clipped[0]) {
                        clipped[0] = Math.min(0, top - state.y());
                    }
                });
            }
            return clipped[0];
        }

        void flushPending() {
            if (inFlight || retrySave == null) return;
            inFlight = true;
            if (ctx.persistenceExecutor() == null) retrySave.run();
            else if (!ctx.persistenceExecutor().trySubmit(retrySave)) inFlight = false;
        }

        void tick() {
            if (states.isEmpty()) return;
            broadcast();
            if (inFlight || finalCarrierPersistence == null) return;
            if (retrySave != null) {
                inFlight = true;
                if (ctx.persistenceExecutor() == null) retrySave.run();
                else if (!ctx.persistenceExecutor().trySubmit(retrySave)) inFlight = false;
                return;
            }
            var expected = new ArrayList<com.gameexpert.falling.dto.FallingSpeleothemState>();
            var next = new ArrayList<com.gameexpert.falling.dto.FallingSpeleothemState>();
            for (var state : states.values()) {
                if (state.finished()) continue;
                int chunkX = Math.floorDiv((int) Math.floor(state.x()), Blocks.CHUNK_X);
                int chunkZ = Math.floorDiv((int) Math.floor(state.z()), Blocks.CHUNK_Z);
                if (!accessor.isChunkActivated(chunkX, chunkZ)) continue;
                var advanced = state;
                for (int i = 0; i < 2; i++) advanced = FallingSpeleothemPhysics.step(advanced, this::clip);
                if (!advanced.equals(state)) { expected.add(state); next.add(advanced); }
            }
            if (next.isEmpty()) return;
            var landed = next.stream().filter(com.gameexpert.falling.dto.FallingSpeleothemState::finished).toList();
            if (!landed.isEmpty() && !beginGroundSettlement()) return;
            var drops = new ArrayList<com.gameexpert.ground.dto.GroundItemSnapshot>();
            for (var state : landed) {
                drops.add(itemSystem.settlementDropSnapshot(itemSystem.reserveSettlementEntityId(),
                        (short) state.blockId(), state.x(), state.y(), state.z()));
            }
            long beforeRevision = groundRevision();
            long afterRevision = drops.isEmpty() ? beforeRevision : Math.addExact(beforeRevision, 1);
            var command = drops.isEmpty() ? null : new com.gameexpert.ground.dto.GroundMutationCommand(
                    stableGroundMutationId(drops.getFirst().entityId(), 1),
                    com.gameexpert.ground.dto.GroundMutationCommand.Kind.BLOCK_DROP,
                    worldId, beforeRevision, afterRevision, null, null, drops, List.of(), List.of(), List.of());
            inFlight = true;
            Runnable rejected = () -> {
                inFlight = false;
            };
            Runnable staleGround = () -> {
                inFlight = false;
                retrySave = null;
                if (command != null) completeGroundSettlement(beforeRevision, false);
            };
            boolean[] groundStale = {false};
            Runnable save = () -> {
                try {
                    finalCarrierPersistence.checkpointFallingSpeleothems(worldId, expected, next,
                            command == null ? null : () -> {
                                var outcome = groundMutationSettlements.settle(command);
                                if (outcome == com.gameexpert.ground.service.GroundMutationOutcome.STALE) {
                                    groundStale[0] = true;
                                    throw new IllegalStateException("falling drop ground revision is stale");
                                }
                            });
                    enqueuePersistenceCompletion(() -> {
                        inFlight = false;
                        retrySave = null;
                        if (command != null) completeGroundSettlement(afterRevision, true);
                        for (var state : next) states.put(state.id(), state);
                        for (var drop : drops) itemSystem.commitSettlementDrop(drop);
                        for (var state : landed) {
                            int damage = FallingSpeleothemPhysics.landingDamage(state);
                            if (damage > 0) mobSystem.hurtByFallingSpeleothem(state.x(), state.y(), state.z(), damage);
                        }
                        broadcast();
                    }, () -> {
                        inFlight = false;
                        retrySave = null;
                        if (command != null) completeGroundSettlement(beforeRevision, false);
                    });
                } catch (RuntimeException failure) {
                    Runnable completion = groundStale[0] ? staleGround : rejected;
                    enqueuePersistenceCompletion(completion, completion);
                    log.warn("Falling speleothem checkpoint failed for world {}", worldId, failure);
                }
            };
            retrySave = save;
            if (ctx.persistenceExecutor() == null) save.run();
            else if (!ctx.persistenceExecutor().trySubmit(save)) rejected.run();
        }
    }
}
