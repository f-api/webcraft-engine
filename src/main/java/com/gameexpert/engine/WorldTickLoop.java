package com.gameexpert.engine;

import com.gameexpert.engine.blocks.CandleRules;
import com.gameexpert.engine.blocks.P26Rules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntUnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

import com.gameexpert.block.persistence.BlockDiffBuffer;
import com.gameexpert.map.dto.CartographyMapSettlementCommand;
import com.gameexpert.map.dto.EmptyMapSettlementCommand;
import com.gameexpert.engine.mob.villager.VillagerJobSitePolicy;
import com.gameexpert.map.dto.WorldMapData;
import com.gameexpert.map.service.WorldMapPersistenceService.AllocationRequest;
import com.gameexpert.map.service.WorldMapPersistenceService.AllocationReservation;
import com.gameexpert.engine.inventory.InventoryRules;
import com.gameexpert.engine.inventory.CauldronRules;
import com.gameexpert.engine.inventory.ItemComponentCodec;
import com.gameexpert.engine.inventory.ItemComponentData;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.inventory.CartographyRules;
import com.gameexpert.engine.inventory.ArmorSlot;
import com.gameexpert.engine.inventory.SmithingTransformRules;
import com.gameexpert.state.service.inventory.InventoryMutationTarget;
import com.gameexpert.state.service.inventory.PlayerInventoryMutationSnapshot;
import com.gameexpert.state.service.inventory.PlayerContainerSettlementCommand;
import com.gameexpert.engine.blocks.P1Rules;
import com.gameexpert.engine.blocks.P3Rules;
import com.gameexpert.engine.blocks.P4Rules;
import com.gameexpert.engine.blocks.P6Rules;
import com.gameexpert.engine.crop.CropRules;
import com.gameexpert.engine.crop.SweetBerryBushRules;
import com.gameexpert.engine.effect.StatusEffect;
import com.gameexpert.engine.effect.StatusEffects;
import com.gameexpert.engine.mob.Mob;
import com.gameexpert.engine.mob.MobRuntime;
import com.gameexpert.engine.mob.Creeper;
import com.gameexpert.engine.mob.CowSex;
import com.gameexpert.engine.mob.MobType;
import com.gameexpert.engine.mob.PiglinGuardedBlocks;
import com.gameexpert.engine.mob.origin.CopperGolemOriginRules;
import com.gameexpert.engine.qa.DenseWorldPerformanceFixturePlan;
import com.gameexpert.engine.qa.OwnerTurnPerformanceWindow;
import com.gameexpert.engine.persistence.animal.AnimalBlockTickPersistenceService;
import com.gameexpert.engine.diagnostics.ArchitectureTurnEvent;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootContainerKind;
import com.gameexpert.engine.sculk.DriedGhastHydrationSystem;
import com.gameexpert.engine.sculk.SculkCatalystRules;
import com.gameexpert.engine.sculk.SculkVibrationRules;
import com.gameexpert.engine.sculk.SculkVibrationSystem;
import com.gameexpert.engine.sulfur.PotentSulfurRules;
import com.gameexpert.engine.validation.MiningLimits;
import com.gameexpert.engine.validation.MovementLimits;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.TerrainAccessor;
import com.gameexpert.terrain.mc.biome.McClimateSampler;
import com.gameexpert.ws.dto.WsMessages.Block;
import com.gameexpert.ws.dto.WsMessages.CompanionInteractionResult;
import com.gameexpert.ws.dto.WsMessages.ElytraState;
import com.gameexpert.ws.dto.WsMessages.BlockFireUpdate;
import com.gameexpert.ws.dto.WsMessages.FireBlock;
import com.gameexpert.ws.dto.WsMessages.BlockUpdate;
import com.gameexpert.ws.dto.WsMessages.CampfireSlot;
import com.gameexpert.ws.dto.WsMessages.CampfireUpdate;
import com.gameexpert.ws.dto.WsMessages.ShelfSlot;
import com.gameexpert.ws.dto.WsMessages.ShelfUpdate;
import com.gameexpert.ws.dto.WsMessages.FurnaceClosed;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.ws.dto.WsMessages.EnchantingClosed;
import com.gameexpert.ws.dto.WsMessages.EnchantingOpen;
import com.gameexpert.ws.dto.WsMessages.EnchantingUpdate;
import com.gameexpert.ws.dto.WsMessages.EnchantOfferDto;
import com.gameexpert.ws.dto.WsMessages.FurnaceOpen;
import com.gameexpert.ws.dto.WsMessages.XpUpdate;
import com.gameexpert.ws.dto.WsMessages.FurnaceUpdate;
import com.gameexpert.ws.dto.WsMessages.InventorySlot;
import com.gameexpert.ws.dto.WsMessages.InventoryUpdate;
import com.gameexpert.ws.dto.WsMessages.BannerPatternLayer;
import com.gameexpert.ws.dto.WsMessages.BookComponent;
import com.gameexpert.ws.dto.WsMessages.LecternUpdate;
import com.gameexpert.ws.dto.WsMessages.BookUpdate;
import com.gameexpert.ws.dto.WsMessages.MapState;
import com.gameexpert.ws.dto.WsMessages.MapPatch;
import com.gameexpert.ws.dto.WsMessages.CraftingClosed;
import com.gameexpert.ws.dto.WsMessages.BrewingOpen;
import com.gameexpert.ws.dto.WsMessages.BrewingUpdate;
import com.gameexpert.ws.dto.WsMessages.BrewingClosed;
import com.gameexpert.ws.dto.WsMessages.CraftingStack;
import com.gameexpert.ws.dto.WsMessages.CraftingUpdate;
import com.gameexpert.ws.dto.WsMessages.EffectDto;
import com.gameexpert.ws.dto.WsMessages.EffectUpdate;
import com.gameexpert.ws.dto.WsMessages.HealthUpdate;
import com.gameexpert.ws.dto.WsMessages.PlayerDeath;
import com.gameexpert.ws.dto.WsMessages.PlayerHurt;
import com.gameexpert.ws.dto.WsMessages.PlayerMoves;
import com.gameexpert.ws.dto.WsMessages.PlayerPose;
import com.gameexpert.ws.dto.WsMessages.PlayerRespawn;
import com.gameexpert.ws.dto.WsMessages.PlayerTeleportSelf;
import com.gameexpert.ws.dto.WsMessages.PotentSulfurEvent;
import com.gameexpert.ws.dto.WsMessages.RespawnSelf;
import com.gameexpert.ws.dto.WsMessages.TimeSync;
import com.gameexpert.ws.dto.WsMessages.SoundEvent;
import com.gameexpert.ws.dto.WsMessages.WorldSound;
import com.gameexpert.ws.dto.WsMessages.CombatHit;
import com.gameexpert.engine.qa.ContentQaFixturePlan;

import static com.gameexpert.engine.Fluids.*;

/**
 * [제공코드] 월드 틱 루프(100ms 고정 주기, 월드당 단일 스레드).
 *
 * <b>틱 순서(고정)</b>:
 * <ol>
 *   <li>ActionQueue 드레인: move → blockBreak/blockPlace → respawn</li>
 *   <li>월드 시계 +timeScale</li>
 *   <li>유체 시뮬레이션(도래한 예약 칸 처리)</li>
 *   <li>환경 데미지 · 자연 재생 · 사망 판정</li>
 *   <li>배칭 브로드캐스트(playerMoves 변경분 · blockUpdate · healthUpdate/playerHurt/playerDeath · timeSync)</li>
 *   <li>BlockDiffBuffer 병합(이번 틱 변경 블록을 영속 버퍼로)</li>
 * </ol>
 * P6에서 몹/투사체/전투 처리는 ③과 ④ 사이(유체 뒤·환경 데미지 앞)에 끼웁니다. 100ms 초과 시 WARN 로그.
 * 이 메서드는 절대 예외를 던지지 않습니다(scheduleAtFixedRate가 예외 시 이후 실행을 멈추기 때문).
 */
public final class WorldTickLoop {

    static final int COPPER_WATERLOGGED = 0x80;
    private static final int COPPER_LANTERN_STATE_MASK = 0x01;
    private static final int COPPER_ROD_FACING_MASK = 0x07;
    private static final int COPPER_STATUE_STATE_MASK = 0x0f;

    /** 틱 내부 정수 기반 규칙에 전달하는 명시적 비상주 값. 실제 블록 ID와 겹치지 않는다. */
    static final int UNAVAILABLE_BLOCK = -1;

    static int residentBlockType(TerrainAccessor accessor, int x, int y, int z) {
        TerrainAccessor.ResidentBlock resident = accessor.residentBlock(x, y, z);
        return resident.isAvailable() ? resident.blockType() : UNAVAILABLE_BLOCK;
    }

    private static final Logger log = LoggerFactory.getLogger(WorldTickLoop.class);
    private static final Logger dropAudit = LoggerFactory.getLogger("gameexpert.audit.drop");

    static final long TICK_BUDGET_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
    private static final long TIMESYNC_INTERVAL = 100; // 100틱마다
    private static final long SAVE_INTERVAL = 300; // 300틱마다 상태 저장
    private static final int INITIAL_MAP_SAMPLE_RADIUS = 8;
    private static final long MAP_PATCH_FLUSH_INTERVAL = 5;
    private static final int[][] FIRE_NEIGHBORS = {
            {0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    /**
     * Reused primitive timing accumulator for one complete owner turn.  It deliberately stores no
     * formatted labels or collections: an ordinary tick only resets/adds longs, while the slow-path
     * logger below is the sole consumer that boxes values for SLF4J.
     */
    static final class TickTiming {
        static final int NONE = 0;
        static final int SAFETY = 1;
        static final int PRELUDE = 2;
        static final int BEGIN_TICK = 3;
        static final int ACTION = 4;
        static final int SMELT = 5;
        static final int CLOCK = 6;
        static final int FLUID = 7;
        static final int RANDOM_TICK = 8;
        static final int MOB = 9;
        static final int NEIGHBOR = 10;
        static final int ITEM = 11;
        static final int BOAT = 12;
        static final int ENVIRONMENT = 13;
        static final int BROADCAST_SAVE = 14;
        static final int LIFECYCLE = 15;
        static final int SNAPSHOT_PREPARATION = 16;
        static final int TREE_PLANNING = 17;
        static final int PROXIMITY = 18;
        static final int CHUNK_DEMAND = 19;
        static final int CHUNK_ADMISSION = 20;
        static final int DECORATION = 21;
        static final int PREPARED_SNAPSHOT = 22;
        static final int SNAPSHOT_DELIVERY = 23;
        static final int SNAPSHOT_DEADLINE = 24;

        long safetyNanos;
        long preludeNanos;
        long beginTickNanos;
        long actionNanos;
        long smeltNanos;
        long clockNanos;
        long fluidNanos;
        long randomTickNanos;
        long mobNanos;
        long neighborNanos;
        long itemNanos;
        long boatNanos;
        long environmentNanos;
        long broadcastSaveNanos;
        long lifecycleNanos;
        long snapshotPreparationNanos;
        long treePlanningNanos;
        long proximityNanos;
        long chunkDemandNanos;
        long chunkAdmissionNanos;
        long decorationNanos;
        long preparedSnapshotNanos;
        long snapshotDeliveryNanos;
        long snapshotDeadlineNanos;
        private int activePhase;
        private long activePhaseStartNanos;
        private boolean exceptionalTurn;

        void reset() {
            safetyNanos = preludeNanos = beginTickNanos = actionNanos = smeltNanos = 0;
            clockNanos = fluidNanos = randomTickNanos = mobNanos = neighborNanos = 0;
            itemNanos = boatNanos = environmentNanos = broadcastSaveNanos = lifecycleNanos = 0;
            snapshotPreparationNanos = treePlanningNanos = proximityNanos = chunkDemandNanos = 0;
            chunkAdmissionNanos = decorationNanos = preparedSnapshotNanos = snapshotDeliveryNanos = 0;
            snapshotDeadlineNanos = 0;
            activePhase = NONE;
            activePhaseStartNanos = 0;
            exceptionalTurn = false;
        }

        void beginPhase(int phase, long nowNanos) {
            activePhase = phase;
            activePhaseStartNanos = nowNanos;
        }

        void endPhase(long nowNanos) {
            if (activePhase == NONE) return;
            add(activePhase, Math.max(0L, nowNanos - activePhaseStartNanos));
            activePhase = NONE;
        }

        int activePhase() {
            return activePhase;
        }

        String activePhaseLabel() {
            return phaseLabel(activePhase);
        }

        void markExceptionalTurn() {
            exceptionalTurn = true;
        }

        boolean exceptionalTurn() {
            return exceptionalTurn;
        }

        long measuredNanos() {
            return safetyNanos + preludeNanos + beginTickNanos + actionNanos + smeltNanos
                    + clockNanos + fluidNanos + randomTickNanos + mobNanos + neighborNanos
                    + itemNanos + boatNanos + environmentNanos + broadcastSaveNanos
                    + lifecycleNanos + snapshotPreparationNanos + treePlanningNanos
                    + proximityNanos + chunkDemandNanos + chunkAdmissionNanos + decorationNanos
                    + preparedSnapshotNanos + snapshotDeliveryNanos + snapshotDeadlineNanos;
        }

        long otherNanos(long totalNanos) {
            return Math.max(0L, totalNanos - measuredNanos());
        }

        void writeArchitecturePhases(ArchitectureTurnEvent event, long totalNanos) {
            event.safetyNanos = safetyNanos;
            event.preludeNanos = preludeNanos;
            event.beginTickNanos = beginTickNanos;
            event.actionNanos = actionNanos;
            event.smeltNanos = smeltNanos;
            event.clockNanos = clockNanos;
            event.fluidNanos = fluidNanos;
            event.randomTickNanos = randomTickNanos;
            event.mobNanos = mobNanos;
            event.neighborNanos = neighborNanos;
            event.itemNanos = itemNanos;
            event.boatNanos = boatNanos;
            event.environmentNanos = environmentNanos;
            event.broadcastSaveNanos = broadcastSaveNanos;
            event.lifecycleNanos = lifecycleNanos;
            event.snapshotPreparationNanos = snapshotPreparationNanos;
            event.treePlanningNanos = treePlanningNanos;
            event.proximityNanos = proximityNanos;
            event.chunkDemandNanos = chunkDemandNanos;
            event.chunkAdmissionNanos = chunkAdmissionNanos;
            event.decorationNanos = decorationNanos;
            event.preparedSnapshotNanos = preparedSnapshotNanos;
            event.snapshotDeliveryNanos = snapshotDeliveryNanos;
            event.snapshotDeadlineNanos = snapshotDeadlineNanos;
            event.otherNanos = otherNanos(totalNanos);
        }

        private void add(int phase, long elapsedNanos) {
            switch (phase) {
                case SAFETY -> safetyNanos += elapsedNanos;
                case PRELUDE -> preludeNanos += elapsedNanos;
                case BEGIN_TICK -> beginTickNanos += elapsedNanos;
                case ACTION -> actionNanos += elapsedNanos;
                case SMELT -> smeltNanos += elapsedNanos;
                case CLOCK -> clockNanos += elapsedNanos;
                case FLUID -> fluidNanos += elapsedNanos;
                case RANDOM_TICK -> randomTickNanos += elapsedNanos;
                case MOB -> mobNanos += elapsedNanos;
                case NEIGHBOR -> neighborNanos += elapsedNanos;
                case ITEM -> itemNanos += elapsedNanos;
                case BOAT -> boatNanos += elapsedNanos;
                case ENVIRONMENT -> environmentNanos += elapsedNanos;
                case BROADCAST_SAVE -> broadcastSaveNanos += elapsedNanos;
                case LIFECYCLE -> lifecycleNanos += elapsedNanos;
                case SNAPSHOT_PREPARATION -> snapshotPreparationNanos += elapsedNanos;
                case TREE_PLANNING -> treePlanningNanos += elapsedNanos;
                case PROXIMITY -> proximityNanos += elapsedNanos;
                case CHUNK_DEMAND -> chunkDemandNanos += elapsedNanos;
                case CHUNK_ADMISSION -> chunkAdmissionNanos += elapsedNanos;
                case DECORATION -> decorationNanos += elapsedNanos;
                case PREPARED_SNAPSHOT -> preparedSnapshotNanos += elapsedNanos;
                case SNAPSHOT_DELIVERY -> snapshotDeliveryNanos += elapsedNanos;
                case SNAPSHOT_DEADLINE -> snapshotDeadlineNanos += elapsedNanos;
                default -> { }
            }
        }

        private static String phaseLabel(int phase) {
            return switch (phase) {
                case SAFETY -> "틱 안전 계측";
                case PRELUDE -> "선행작업";
                case BEGIN_TICK -> "시작/날씨";
                case ACTION -> "액션";
                case SMELT -> "제련";
                case CLOCK -> "시계";
                case FLUID -> "유체";
                case RANDOM_TICK -> "랜덤틱";
                case MOB -> "몹/투사체/전투";
                case NEIGHBOR -> "이웃갱신";
                case ITEM -> "아이템";
                case BOAT -> "보트";
                case ENVIRONMENT -> "환경";
                case BROADCAST_SAVE -> "브로드캐스트/저장";
                case LIFECYCLE -> "세션 생명주기";
                case SNAPSHOT_PREPARATION -> "스냅샷 준비 요청";
                case TREE_PLANNING -> "트리 계획";
                case PROXIMITY -> "근접 준비";
                case CHUNK_DEMAND -> "청크 수요";
                case CHUNK_ADMISSION -> "청크 승인";
                case DECORATION -> "장식";
                case PREPARED_SNAPSHOT -> "준비 스냅샷";
                case SNAPSHOT_DELIVERY -> "스냅샷 전달";
                case SNAPSHOT_DEADLINE -> "스냅샷 기한";
                default -> "계측외";
            };
        }
    }

    static boolean exceedsTickBudget(long elapsedNanos) {
        return elapsedNanos > TICK_BUDGET_NANOS;
    }

    private final WorldRuntime rt;
    private final RandomTickSystem randomTicks;
    /** Null outside the exact reserved QA fixture; this is the production hot-path gate. */
    private OwnerTurnPerformanceWindow performanceWindow;
    private OwnerTurnPerformanceWindow.MutableTurnObservation performanceObservation;
    private DenseWorldPerformanceFixturePlan installedPerformancePlan;
    private long[] performanceFixtureMobIds;
    private long performanceNextStartNanos;
    private OwnerTurnPerformanceWindow.Result lastPerformanceResult;
    private RandomTickSystem.TickStats lastRandomTickStats;
    /**
     * Random ticks sample up to 4,608 cells per authority tick. Keep only a small access-ordered
     * wrapper cache: each view reads its immutable resident source directly, avoiding the former
     * 98,304-entry type and generation arrays per active chunk.
     */
    private final MaterializedRandomTickChunkCache randomTickChunkViews =
            new MaterializedRandomTickChunkCache();
    /** [SURV-X] 채굴 경험치 범위 추첨용 난수(드랍 난수열과 분리해 기존 결정성을 흔들지 않는다). */
    private final java.util.Random blockXpRandom = new java.util.Random();
    /**
     * [SURV-X] 주민 거래 성사 경험치(3~6) 추첨용 난수. 채굴 난수와 <b>따로</b> 두는 이유는 같다 —
     * 한 수열을 나눠 쓰면 거래 한 번이 다음 채굴 XP 값을 밀어 기존 결정성이 흔들린다.
     */
    private final java.util.Random tradeXpRandom = new java.util.Random();
    /** 식물 랜덤틱도 몹과 같은 15블록 광원 전파 규칙을 사용합니다. */
    private final MobLightEngine randomTickLight;
    /** FireBlock의 increased_fire_burnout 태그를 raw 1.21.4 biome ID로 판정한다. */
    private final McClimateSampler fireBiomeSampler;
    private final Set<String> playersInPortal = new HashSet<>();
    /** 플레이어별 이동 큐 상한만큼만 ID를 보존하고, 넘으면 그 세션 증거를 전부 fail-closed 한다. */
    private final Map<String, FinalSceneLandingActionLedger> finalSceneLandingActionLedgers =
            new HashMap<>();
    private static final long FINAL_SCENE_PREREQUISITE_TIMEOUT_TICKS = 600;
    private record PendingFinalScenePrerequisites(ContentQaFixturePlan plan,
            PlayerTickState player, String scenario, String actionNonce, long deadlineTick,
            int observedRaidRoleMask) { }
    private final Map<String, PendingFinalScenePrerequisites> pendingFinalScenePrerequisites =
            new HashMap<>();
    private final Map<String, Set<String>> finalScenePrerequisiteNonces = new HashMap<>();
    private long finalScenePrerequisiteRevision;
    private record OneShotFeedbackKey(String nickname, String kind, String identity) { }
    private record PendingOneShotFeedback(PlayerTickState player, WebSocketSession session,
            Object message, Runnable accepted) { }
    /** Exact session-bound feedback retained when the outbound queue applies backpressure. */
    private final Map<OneShotFeedbackKey, PendingOneShotFeedback> pendingOneShotFeedback =
            new LinkedHashMap<>();
    /** A stricter global form of the canonical 256-action per-player ledger budget. */
    private static final int MAX_PENDING_ONE_SHOT_FEEDBACK = ActionQueue.DEFAULT_MAX_PER_PLAYER;
    /** DB 커밋을 기다리는 동안 같은 플레이어의 중복 지도 생성 요청을 막습니다. */
    private final Set<String> pendingMapUses = new HashSet<>();
    private final Map<String, PendingEmptyMapSettlement> pendingEmptyMapSettlements =
            new LinkedHashMap<>();
    private final Map<String, PendingCartographySettlement> pendingCartographySettlements =
            new LinkedHashMap<>();

    enum CartographySettlementOutcome { COMMITTED, IDEMPOTENT, REJECTED, UNKNOWN }

    @FunctionalInterface
    interface CartographyResultMapAllocator {
        AllocationReservation reserve(PlayerInventory.CompletePersistenceSnapshot source);
    }

    /** One immutable mutation plan retained across unknown outcomes and coordinator recreation. */
    static final class FrozenCartographyPlan {
        private enum Resolution { PENDING, INSTALLED, REJECTED }

        private final PlayerInventory liveInventory;
        private final PlayerInventory.CompletePersistenceSnapshot sourceInventory;
        private final PlayerInventory.CompletePersistenceSnapshot committedInventory;
        private final PlayerInventory.CartographyMutation inventoryDelta;
        private final WorldMapData sourceMap;
        private final AllocationReservation allocationReservation;
        private final WorldMapRuntime.CartographyReservation mapReservation;
        private Resolution resolution = Resolution.PENDING;

        private FrozenCartographyPlan(PlayerInventory liveInventory,
                PlayerInventory.CompletePersistenceSnapshot sourceInventory,
                PlayerInventory.CompletePersistenceSnapshot committedInventory,
                PlayerInventory.CartographyMutation inventoryDelta, WorldMapData sourceMap,
                AllocationReservation allocationReservation,
                WorldMapRuntime.CartographyReservation mapReservation) {
            this.liveInventory = liveInventory;
            this.sourceInventory = sourceInventory;
            this.committedInventory = committedInventory;
            this.inventoryDelta = inventoryDelta;
            this.sourceMap = sourceMap;
            this.allocationReservation = allocationReservation;
            this.mapReservation = mapReservation;
        }

        static FrozenCartographyPlan stage(PlayerInventory liveInventory, WorldMapData sourceMap,
                CartographyRules.Operation operation, boolean shift,
                CartographyResultMapAllocator allocator) {
            if (liveInventory == null || sourceMap == null || operation == null
                    || allocator == null) return null;
            PlayerInventory.CompletePersistenceSnapshot lease =
                    liveInventory.acquireSettlementLease();
            if (lease == null) return null;
            boolean retained = false;
            try {
                AllocationReservation allocation = allocator.reserve(lease);
                if (allocation == null) return null;
                WorldMapRuntime.CartographyReservation reservation =
                        WorldMapRuntime.reservationForDurableSource(
                                sourceMap, operation, allocation.mapId());
                if (reservation == null) return null;
                PlayerInventory detached = lease.detachedInventory();
                PlayerInventory.CartographyMutation delta = detached.stageCartographyResult(
                        PlayerInventory.CartographyAuthorityWitness.from(
                                reservation.authority()), shift);
                if (delta == null) return null;
                PlayerInventory.CompletePersistenceSnapshot committed =
                        detached.completePersistenceSnapshot();
                if (committed.revision() != Math.addExact(lease.revision(), 1)
                        || delta.beforeInventoryRevision() != lease.revision()
                        || delta.afterInventoryRevision() != committed.revision()) {
                    throw new IllegalStateException(
                            "cartography inventory generation is not exact");
                }
                FrozenCartographyPlan result = new FrozenCartographyPlan(liveInventory, lease,
                        committed, delta, sourceMap, allocation, reservation);
                retained = true;
                return result;
            } finally {
                if (!retained) liveInventory.cancelSettlementLease(lease);
            }
        }

        boolean resolve(CartographySettlementOutcome outcome,
                java.util.function.Consumer<WorldMapData> mapInstaller) {
            if (outcome == null || mapInstaller == null) return false;
            if (resolution == Resolution.INSTALLED) {
                return outcome == CartographySettlementOutcome.COMMITTED
                        || outcome == CartographySettlementOutcome.IDEMPOTENT;
            }
            if (resolution == Resolution.REJECTED) return false;
            if (outcome == CartographySettlementOutcome.UNKNOWN) return false;
            if (outcome == CartographySettlementOutcome.REJECTED) {
                if (!liveInventory.releaseSettlementLease(sourceInventory)) return false;
                resolution = Resolution.REJECTED;
                return true;
            }
            WorldMapData candidate = mapReservation.candidate();
            if (candidate != null) mapInstaller.accept(candidate);
            if (!liveInventory.installCommittedSettlement(
                    sourceInventory, committedInventory)) return false;
            resolution = Resolution.INSTALLED;
            return true;
        }

        PlayerInventory.CompletePersistenceSnapshot sourceInventory() { return sourceInventory; }
        PlayerInventory.CompletePersistenceSnapshot committedInventory() {
            return committedInventory;
        }
        PlayerInventory.CartographyMutation inventoryDelta() { return inventoryDelta; }
        AllocationReservation allocationReservation() { return allocationReservation; }
        WorldMapData sourceMap() { return sourceMap; }
        WorldMapData resultMap() {
            WorldMapData candidate = mapReservation.candidate();
            return candidate == null ? sourceMap : candidate;
        }
        int resultMapId() { return mapReservation.authority().resultMapId(); }
        boolean installed() { return resolution == Resolution.INSTALLED; }
        boolean pending() { return resolution == Resolution.PENDING; }
    }

    private static final class PendingCartographySettlement {
        private final PlayerTickState player;
        private final FrozenCartographyPlan plan;
        private final CartographyMapSettlementCommand command;
        private boolean inFlight;
        private boolean reconciliationRequired;

        private PendingCartographySettlement(PlayerTickState player,
                FrozenCartographyPlan plan, CartographyMapSettlementCommand command) {
            this.player = player;
            this.plan = plan;
            this.command = command;
        }
    }

    private static final class PendingEmptyMapSettlement {
        private final PlayerTickState player;
        private final PlayerInventory.CompletePersistenceSnapshot sourceInventory;
        private final PlayerInventory.CompletePersistenceSnapshot committedInventory;
        private final EmptyMapSettlementCommand command;
        private final WorldMapData map;
        private final com.gameexpert.ground.dto.GroundMutationCommand groundCommand;
        private final com.gameexpert.ground.dto.GroundItemSnapshot dropped;
        private boolean inFlight;
        private boolean reconciliationRequired;
        private boolean groundLaneCompleted;

        private PendingEmptyMapSettlement(PlayerTickState player,
                PlayerInventory.CompletePersistenceSnapshot sourceInventory,
                PlayerInventory.CompletePersistenceSnapshot committedInventory,
                EmptyMapSettlementCommand command, WorldMapData map,
                com.gameexpert.ground.dto.GroundMutationCommand groundCommand,
                com.gameexpert.ground.dto.GroundItemSnapshot dropped) {
            this.player = player;
            this.sourceInventory = sourceInventory;
            this.committedInventory = committedInventory;
            this.command = command;
            this.map = map;
            this.groundCommand = groundCommand;
            this.dropped = dropped;
        }

        boolean ownsGroundLane() { return groundCommand != null; }
    }
    /** 선택 중인 지도는 매 틱 한 컬럼씩 순환하며 상주 권위 지형을 갱신합니다. */
    private final Map<Integer, Integer> mapScanColumns = new LinkedHashMap<>();
    /** 선택 해제·퇴장 뒤에도 마지막 dirty 래스터를 durable 상태까지 밀어냅니다. */
    private final Set<Integer> mapsAwaitingFlush = new LinkedHashSet<>();
    private final WorldMapColorSampler.ColumnView mapColumns;
    /**
     * [MAPNAV] 송신 큐가 거부한 spawnPoint 를 다음 틱에 다시 보낼 대상. 나침반 목표는 다음 사건이
     * 실어 나르지 않으므로(사망·침대 사용은 몇 시간에 한 번이다) 여기서 재시도하지 않으면 클라의
     * 나침반이 세션 내내 옛 지점을 가리킨다.
     */
    private final Set<String> spawnPointResend = new HashSet<>();
    private final PrimedTntSystem primedTnt;
    /** 던진 엔더의 눈 비행체(투사체 프로토콜만 공유, 비영속). */
    private final EnderEyeSystem enderEyes;

    static final class MaterializedRandomTickChunk
            implements RandomTickSystem.ChunkView {
        private TerrainAccessor.SnapshotSource source;

        void refresh(TerrainAccessor.SnapshotSource next) {
            source = next;
        }

        @Override
        public int blockAt(int localX, int y, int localZ) {
            int index = Blocks.blockIndex(localX, y, localZ);
            return source.blockTypeAt(index);
        }
    }

    static final class MaterializedRandomTickChunkCache
            extends LinkedHashMap<Long, MaterializedRandomTickChunk> {
        MaterializedRandomTickChunkCache() {
            super(RandomTickSystem.MAX_ACTIVE_CHUNKS, 0.75f, true);
        }

        MaterializedRandomTickChunk view(long key, TerrainAccessor.SnapshotSource source) {
            MaterializedRandomTickChunk view = computeIfAbsent(
                    key, ignored -> new MaterializedRandomTickChunk());
            view.refresh(source);
            return view;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, MaterializedRandomTickChunk> eldest) {
            return size() > RandomTickSystem.MAX_ACTIVE_CHUNKS;
        }
    }
    /** 블록 diff와 PrimedTnt delta를 캡처한 결합 체크포인트는 월드당 하나만 비행합니다. */
    private volatile boolean primedTntCheckpointInFlight;
    private volatile Runnable runtimeFallCheckpointRetry;
    /**
     * [DEEP-DARK] 진동 → 감지체 → 비명체 → 어둠 사슬. 규칙 정본은
     * {@link SculkVibrationRules} 이고 이 필드는 그 규칙을 이 월드의 틱에 태운다.
     */
    private final SculkVibrationSystem sculkVibrations;
    /**
     * [GLOWING] 울린 종의 블록 엔티티 상태({@link BellResonance}). 흔들림·공명이 끝나고 탐색 간격도 지나면
     * 바닐라 상태와 구별되지 않으므로 버린다. 종이 사라지면 블록 엔티티와 함께 버린다.
     */
    private final Map<String, BellResonance> ringingBells = new java.util.LinkedHashMap<>();
    /**
     * 발소리 진동의 원천. 새 프로토콜을 만들지 않고 <b>서버가 자기 관측으로</b> 판정한다 —
     * 이미 확정한 pose 의 블록 좌표가 바뀌면 그것이 한 걸음이다.
     */
    private final Map<String, BlockPos> sculkLastFootstep = new HashMap<>();
    /** 시작 시 한 번 읽은 미완료 동물 정산. 실패한 것만 메모리에서 다음 틱 재시도한다. */
    private final ArrayDeque<com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService.HatchIntent>
            pendingHatchRecovery = new ArrayDeque<>();
    private final ArrayDeque<com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService.FroglightIntent>
            pendingFroglightRecovery = new ArrayDeque<>();
    private boolean pendingHatchesHydrated;
    private boolean pendingFroglightsHydrated;
    private boolean pendingSniffersHydrated;
    private boolean pendingHatchHydrationInFlight;
    private boolean pendingFroglightHydrationInFlight;
    private boolean pendingSnifferHydrationInFlight;
    /** Exact animal-settlement identities currently owned by the FIFO persistence writer. */
    private final Set<String> animalSettlementInFlight = new HashSet<>();
    /** Drained runtime requests remain here until their durable transaction and owner ack succeed. */
    private final Map<String, FroglightSettlementWork> pendingFroglightWork =
            new LinkedHashMap<>();
    private final Map<String, SnifferSettlementWork> pendingSnifferWork =
            new LinkedHashMap<>();
    /** Due source schedules parked while their hatch transaction owns the durable identity. */
    private final Map<String, ScheduledAnimalBlock> hatchSchedulesOwnedBySettlement =
            new HashMap<>();
    /** Completed receipts are deleted only after their durable result is installed on the owner. */
    private final Set<String> pendingAnimalSettlementRetirements = new LinkedHashSet<>();
    private final Map<String, Runnable> pendingCopperSettlementSubmissions = new LinkedHashMap<>();
    /** Cells whose durable Copper Golem transition has not returned to the owner thread yet. */
    private final CopperSettlementCellReservations copperSettlementCells =
            new CopperSettlementCellReservations();
    /** 컨테이너 정산 창에 걸려 보류할 수 있는 블록 편집의 최대 개수. */
    private static final int MAX_DEFERRED_CONTAINER_EDITS = 64;
    /** 보류 편집이 살아 있는 최대 틱 수(10 TPS 기준 약 4초). */
    private static final long DEFERRED_CONTAINER_EDIT_TICKS = 200L;
    /** 정산 창에 걸린 편집을 즉시 거절하는 대신 순서대로 담아 두는 FIFO. */
    private final ArrayDeque<DeferredContainerEdit> deferredContainerEdits = new ArrayDeque<>();
    /** 보류분을 재적용하는 동안에는 같은 편집을 다시 담지 않는다(중복 적용·무한 재큐 방지). */
    private boolean replayingDeferredContainerEdits;
    /**
     * 재적용 중인 보류 편집이 도착할 때 선택돼 있던 핫바 칸. 재적용 밖에서는 -1 이다. 보류 동안
     * 스크롤해도 설치 아이템·채굴 도구는 도착 당시의 칸을 쓴다(바닐라는 패킷 시점의 손을 쓴다).
     */
    private int replayingDeferredEditMainSlot = -1;

    /** 보류된 편집, 이 편집을 되돌릴 마감 틱, 도착 당시의 선택 핫바 칸. */
    private record DeferredContainerEdit(PlayerAction.BlockEdit edit, long deadlineTick,
            int mainSlot) {
    }

    /**
     * [DEEP-DARK] 말린 가스트 수화 → 가스틀링 소생 사슬. 규칙 정본은
     * {@link com.gameexpert.engine.sculk.DriedGhastHydration} 이고 이 필드가 그 규칙을 이
     * 월드의 틱에 태운다 — 규칙만 있고 드라이버가 없어 <b>사건 자체가 일어나지 않던</b> 자리다.
     */
    private final DriedGhastHydrationSystem driedGhastHydration;
    /**
     * [CREAKING] 크리킹 하트 낮/밤 사슬. 규칙 정본은
     * {@link com.gameexpert.engine.creaking.CreakingHeartRules} 이고 이 필드가 그 규칙을 이
     * 월드의 틱에 태운다 — 말린 가스트 수화 사슬과 같은 자리·같은 모양이다.
     */
    private final com.gameexpert.engine.creaking.CreakingHeartSystem creakingHearts;
    /** TNT constructor and direct-prime angle lane; explosion chains use MobSystem's shared explosion RNG. */
    private final java.util.Random tntRandom;
    /**
     * [COMPOSTER] 퇴비 성공 판정 전용 난수 레인. 다른 레인과 섞지 않는 이유는 TNT·경험치와
     * 같다 — 한 상호작용이 다른 상호작용의 난수열을 밀지 않아야 회귀를 재현할 수 있다.
     */
    private final java.util.Random composterRandom = new java.util.Random();
    /**
     * [CROP-BERRY] 섭취 부작용(독 감자 60% 독 I) 판정 전용 난수 레인. 퇴비·TNT 와 같은 이유로
     * 다른 레인과 섞지 않는다 — 한 상호작용이 다른 상호작용의 난수열을 밀면 회귀를 재현할 수 없다.
     */
    private final java.util.Random foodEffectRandom = new java.util.Random();
    /**
     * [CROP-BERRY] 달콤한 열매 덤불 수확 수량({@code 1 + nextInt(2)}) 전용 난수 레인.
     * 위 섭취 부작용과 같은 이유로 레인을 분리한다 — 열매를 딸 때마다 식품 부작용 난수열이
     * 밀리면 독 감자 확률 회귀를 재현할 수 없다.
     */
    private final java.util.Random berryHarvestRandom = new java.util.Random();
    /**
     * [ARCHAEOLOGY] 붓질 전리품 전용 난수 레인. 기존 채굴·수확 레인과 섞으면 붓질 한 번이
     * 다른 확률(자갈 부싯돌 · 열매 수량)의 난수열을 밀어 회귀를 재현할 수 없게 된다 —
     * 기존 확률·난수 프리픽스를 건드리지 않는 것이 이 트랙의 제약이다.
     */
    private final ArrayDeque<BlockPos> pendingSupportChecks = new ArrayDeque<>();
    /** 열린 보관함만 좌표별로 색인해 재고 변경 때 전체 플레이어를 훑지 않는다. */
    private final Map<Long, LinkedHashSet<PlayerTickState>> chestSubscribersByPosition =
            new HashMap<>();
    private final Map<PlayerTickState, ChestSubscription> chestSubscriptionByPlayer = new HashMap<>();
    /** 화로도 열린 화면만 삽입 순서대로 보존한다. */
    private final Map<Long, LinkedHashSet<PlayerTickState>> furnaceSubscribersByPosition =
            new HashMap<>();
    private final Map<PlayerTickState, Long> furnaceSubscriptionByPlayer = new HashMap<>();
    /**
     * [CONCRETE] 이번 틱 설치가 <b>덮어 없앤</b> 블록. 바닐라
     * {@code ConcretePowderBlock#getStateForPlacement} 는 클릭한 칸의 상태를
     * {@code shouldSolidify} 에 넘기는데, 이 저장소는 설치가 끝난 뒤 합류점에서 경화를 돌아
     * 그 상태가 이미 지워져 있다. 물웅덩이에 놓은 가루가 굳으려면 그 한 칸을 기억해야 한다.
     * {@link #hardenConcretePowder} 가 쓰고 나서 비운다.
     */
    private final Map<BlockPos, Integer> placedOver = new HashMap<>();
    private final Map<BlockPos, Long> p3DelayedTransitions = new LinkedHashMap<>();
    /**
     * [ENCHANT-WIDE] 살얼음 예약 틱(바닐라 {@code scheduleTick}, 권위 틱). 인메모리라 서버 재시작 뒤 남은
     * 살얼음은 랜덤틱 표본이 만나는 순간 {@code frostedIceSampled} 가 다시 예약한다. 난수는 이 레인 전용이다.
     */
    private final Map<BlockPos, Long> frostedIceTicks = new LinkedHashMap<>();
    private final java.util.Random frostedIceRandom = new java.util.Random();
    /** [ENCHANT-WIDE] 차가운 걸음 location_changed 판정용 마지막 블록 위치. */
    private final Map<String, BlockPos> frostWalkerLastCell = new HashMap<>();
    private final Map<BlockPos, Long> poplarButtonReleaseTicks = new LinkedHashMap<>();
    private final Set<BlockPos> activePoplarPressurePlates = new LinkedHashSet<>();
    private final AnimalBlockScheduleIndex scheduledAnimalBlocks = new AnimalBlockScheduleIndex();
    private final Set<BlockPos> animalSchedulesAwaitingPersistence = new LinkedHashSet<>();
    private static final int MAX_SCHEDULED_ANIMAL_BLOCKS_PER_TICK = 64;
    private long activeAnimalBlockDueTick = Long.MIN_VALUE;
    private final Map<Long, CampfireUpdate> pendingCampfireUpdates = new LinkedHashMap<>();
    /** 클라 권위 이동이 서버 pose 로 확정되기 전에 통과해야 하는 속도·좌표 상한. */
    private final MovementLimits movementLimits = new MovementLimits();
    /** 클라 권위 채굴 진행률이 서버 파괴로 확정되기 전에 통과해야 하는 최소 시간·레이트 상한. */
    private final MiningLimits miningLimits = new MiningLimits();
    private long publishedFireRevision;
    private int sleepRosterSize;
    private boolean sleepStatusVisible;

    record ScheduledAnimalBlock(
            AnimalBlockTickPersistenceService.Kind kind, long dueMcTick) { }

    record IndexedAnimalBlockSchedule(
            BlockPos position, ScheduledAnimalBlock scheduled, long insertionOrder) { }

    /**
     * Position lookup and chronological due index for delayed animal-block work. The old
     * {@link LinkedHashMap} made every world tick walk every future entry before it could find the
     * at-most-64 due entries. Keeping the original insertion sequence as an explicit secondary key
     * preserves deterministic ties while the first due entry is now available in O(log n).
     */
    static final class AnimalBlockScheduleIndex {
        private static final java.util.Comparator<IndexedAnimalBlockSchedule> DUE_ORDER =
                java.util.Comparator.comparingLong(
                                (IndexedAnimalBlockSchedule entry) ->
                                        entry.scheduled().dueMcTick())
                        .thenComparingLong(IndexedAnimalBlockSchedule::insertionOrder);

        private final Map<BlockPos, IndexedAnimalBlockSchedule> byPosition = new HashMap<>();
        private final TreeSet<IndexedAnimalBlockSchedule> byDue = new TreeSet<>(DUE_ORDER);
        private long nextInsertionOrder;

        void clear() {
            byPosition.clear();
            byDue.clear();
            nextInsertionOrder = 0L;
        }

        ScheduledAnimalBlock get(BlockPos position) {
            IndexedAnimalBlockSchedule entry = byPosition.get(position);
            return entry == null ? null : entry.scheduled();
        }

        /** Replacing an existing position retains the LinkedHashMap-era tie position. */
        void put(BlockPos position, ScheduledAnimalBlock scheduled) {
            IndexedAnimalBlockSchedule previous = byPosition.remove(position);
            long insertionOrder;
            if (previous == null) {
                insertionOrder = nextInsertionOrder++;
            } else {
                byDue.remove(previous);
                insertionOrder = previous.insertionOrder();
            }
            add(new IndexedAnimalBlockSchedule(position, scheduled, insertionOrder));
        }

        /** A processed retry/replacement moves behind all schedules that were already present. */
        void append(BlockPos position, ScheduledAnimalBlock scheduled) {
            remove(position);
            add(new IndexedAnimalBlockSchedule(position, scheduled, nextInsertionOrder++));
        }

        ScheduledAnimalBlock remove(BlockPos position) {
            IndexedAnimalBlockSchedule removed = byPosition.remove(position);
            if (removed == null) return null;
            byDue.remove(removed);
            return removed.scheduled();
        }

        IndexedAnimalBlockSchedule pollDue(long nowMcTick) {
            IndexedAnimalBlockSchedule first = byDue.isEmpty() ? null : byDue.first();
            if (first == null || first.scheduled().dueMcTick() > nowMcTick) return null;
            byDue.pollFirst();
            byPosition.remove(first.position(), first);
            return first;
        }

        /** Requeues an unavailable resident source without changing its due tick or tie position. */
        void restore(IndexedAnimalBlockSchedule entry) {
            if (byPosition.containsKey(entry.position())) {
                throw new IllegalStateException("animal schedule position already indexed");
            }
            add(entry);
        }

        int size() {
            return byPosition.size();
        }

        private void add(IndexedAnimalBlockSchedule entry) {
            byPosition.put(entry.position(), entry);
            if (!byDue.add(entry)) {
                byPosition.remove(entry.position(), entry);
                throw new IllegalStateException("duplicate animal schedule order");
            }
        }
    }

    private record FroglightSettlementWork(
            com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService
                    .FroglightIntent recovered,
            long frogMobId, long sulfurCubeMobId, long proposedEntityId, short itemType,
            double x, double y, double z) {
        String key() {
            return recovered == null
                    ? "froglight:" + frogMobId + ':' + sulfurCubeMobId : recovered.key();
        }
    }

    private record SnifferSettlementWork(
            MobRuntime.SnifferDigDropRequest request,
            com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService
                    .SnifferIntent recovered,
            long proposedEntityId,
            com.gameexpert.mob.dto.MobPersistenceSnapshot clearedMob) {
        String key() {
            return recovered == null
                    ? "sniffer:" + request.sniffer().id + ':' + request.sequence()
                    : recovered.key();
        }
    }

    private record CommittedHatch(
            com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService.HatchIntent
                    intent,
            MobSystem.AnimalDependencySpawnKind kind) { }

    /** 한 플레이어가 연 단일/이중 상자의 물리 좌표들. */
    private static final class ChestSubscription {
        private final long firstKey;
        private final long secondKey;
        /** [BARREL-STATE] 연 컨테이너가 통이면 그 좌표. 열람자 수로 OPEN 을 맞출 때 쓴다. */
        private final BlockPos barrel;

        private ChestSubscription(long firstKey, long secondKey, BlockPos barrel) {
            this.firstKey = firstKey;
            this.secondKey = secondKey;
            this.barrel = barrel;
        }
    }

    /**
     * 한 파생-state 세대 안에서 좌표의 가장 최신 의존성 revision만 실행한다. 꺼낸 좌표는
     * membership에서 제거하므로 실제 state 변경이 생기면 더 높은 revision으로 다시 넣을 수 있다.
     */
    private static final class RevisionedPositionQueue {
        private final ArrayDeque<BlockPos> order = new ArrayDeque<>();
        private final Map<BlockPos, Long> queued = new HashMap<>();
        private final Map<BlockPos, Long> processed = new HashMap<>();

        private void add(int x, int y, int z, long revision) {
            BlockPos position = new BlockPos(x, y, z);
            Long pending = queued.get(position);
            if (pending == null) {
                queued.put(position, revision);
                order.addLast(position);
            } else if (revision > pending) {
                queued.put(position, revision);
            }
        }

        private RevisionedPosition take() {
            while (!order.isEmpty()) {
                BlockPos position = order.removeFirst();
                Long revision = queued.remove(position);
                if (revision == null) continue;
                Long previous = processed.get(position);
                if (previous != null && previous >= revision) continue;
                processed.put(position, revision);
                return new RevisionedPosition(position, revision);
            }
            return null;
        }
    }

    private static final class RevisionedPosition {
        private final BlockPos position;
        private final long revision;

        private RevisionedPosition(BlockPos position, long revision) {
            this.position = position;
            this.revision = revision;
        }
    }

    /**
     * 한 틱에서 실패한 플레이어 액션의 요약. 같은 입력을 반복해 보내는 클라가 로그 자체를 부하로
     * 만들지 못하도록 첫 실패만 스택과 함께 남기고 나머지는 개수만 셉니다.
     */
    private static final class ActionFailureLog {
        private int failures;

        private void record(PlayerAction action, Exception exception) {
            failures++;
            if (failures == 1) {
                log.warn("플레이어 액션 처리 실패: nickname={} action={}",
                        action.nickname(), action.getClass().getSimpleName(), exception);
            }
        }

        private void summarize(Long worldId, long tickNo) {
            if (failures > 1) {
                log.warn("월드 {} 틱 {}: 실패한 플레이어 액션 {}건(첫 건만 상세 기록)",
                        worldId, tickNo, failures);
            }
        }

        private boolean hasFailures() {
            return failures != 0;
        }
    }

    private final com.gameexpert.engine.redstone.RuntimeRedstoneHost redstone;
    private final List<Runnable> redstoneEffects = new ArrayList<>();
    private final java.util.LinkedHashSet<BlockPos> redstoneConsumers = new java.util.LinkedHashSet<>();
    private boolean applyingRedstone;
    private long redstoneRevision;

    WorldTickLoop(WorldRuntime rt) {
        this.rt = rt;
        this.primedTnt = new PrimedTntSystem(rt);
        this.enderEyes = new EnderEyeSystem(rt,
                new java.util.Random(((long) rt.seed() << 32) ^ rt.worldId() ^ 0x454e444559L),
                player -> sendTo(player, inventoryMessage(player)));
        this.tntRandom = new java.util.Random(((long) rt.seed() << 32) ^ rt.worldId());
        this.mapColumns = new WorldMapColorSampler.ColumnView() {
            @Override
            public boolean isResident(int worldX, int worldZ) {
                return rt.accessor().isChunkResident(
                        Math.floorDiv(worldX, Blocks.CHUNK_X),
                        Math.floorDiv(worldZ, Blocks.CHUNK_Z));
            }

            @Override
            public int blockAt(int worldX, int worldY, int worldZ) {
                return residentBlockType(rt.accessor(), worldX, worldY, worldZ);
            }
        };
        this.randomTickLight = new MobLightEngine(rt.accessor());
        this.fireBiomeSampler = new McClimateSampler(rt.seed());
        this.randomTicks = new RandomTickSystem(new RandomTickSystem.World() {
            @Override
            public int getBlock(int x, int y, int z) {
                return residentBlockType(rt.accessor(), x, y, z);
            }

            @Override
            public RandomTickSystem.ChunkView randomTickChunkView(int chunkX, int chunkZ) {
                return materializedRandomTickChunkView(chunkX, chunkZ);
            }

            @Override
            public boolean isChunkActivated(int chunkX, int chunkZ) {
                return rt.accessor().isChunkActivated(chunkX, chunkZ);
            }

            @Override
            public boolean isChunkResident(int chunkX, int chunkZ) {
                return rt.accessor().isChunkResident(chunkX, chunkZ);
            }

            @Override
            public void setBlock(int x, int y, int z, int blockId) {
                rt.fluidSim().applyChange(x, y, z, blockId);
            }

            @Override
            public int lightLevel(int x, int y, int z) {
                return randomTickLight.rawLightLevel(x, y, z);
            }

            @Override
            public long gameTimeMcTicks() { return rt.clock().gameTimeMcTicks(); }

            @Override
            public Boolean eyeblossomOpen() {
                if (!"overworld".equals(rt.dimensionKey())) return null;
                long phase = Math.floorMod(rt.clock().worldTime() * 2, 24000);
                return phase >= 12600 && phase < 23401;
            }

            @Override
            public int blockLightLevel(int x, int y, int z) {
                return randomTickLight.blockLight(x, y, z);
            }

            @Override
            public void frostedIceSampled(int x, int y, int z) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!frostedIceTicks.containsKey(pos)) {
                    scheduleFrostedIce(pos, rt.tickNo(), EnchantmentRules.FROSTED_ICE_RETRY_DELAY_MIN,
                            EnchantmentRules.FROSTED_ICE_RETRY_DELAY_MAX);
                }
            }

            @Override
            public int getState(int x, int y, int z, int blockId) {
                return rt.blockStates().get(x, y, z, blockId);
            }

            @Override
            public void setState(int x, int y, int z, int blockId, int state) {
                if (residentBlockType(rt.accessor(), x, y, z) != blockId) return;
                rt.setBlockState(x, y, z, blockId, state);
                rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) blockId);
            }

            @Override
            public boolean isRainingAt(int x, int y, int z) {
                if (!rt.weatherSystem().isRaining()) return false;
                for (int scanY = y + 1; scanY <= Blocks.MAX_Y; scanY++) {
                    if (Fluids.isSolid(residentBlockType(rt.accessor(), x, scanY, z))) return false;
                }
                return true;
            }

            @Override
            public boolean isHumid(int x, int y, int z) {
                int biome = fireBiomeSampler.biomeAtBlock(x, y, z);
                return biome == SurfaceDecorator.BAMBOO_JUNGLE
                        || biome == SurfaceDecorator.MUSHROOM_FIELDS
                        || biome == SurfaceDecorator.MANGROVE_SWAMP
                        || biome == SurfaceDecorator.SNOWY_SLOPES
                        || biome == SurfaceDecorator.FROZEN_PEAKS
                        || biome == SurfaceDecorator.JAGGED_PEAKS
                        || biome == SurfaceDecorator.SWAMP
                        || biome == SurfaceDecorator.JUNGLE;
            }

            @Override
            public int difficultyId() {
                return rt.difficulty().id();
            }

            @Override
            public boolean igniteTnt(int x, int y, int z) {
                if (residentBlockType(rt.accessor(), x, y, z) != Blocks.TNT) return false;
                primeTnt(x, y, z);
                return true;
            }

            @Override
            public void dropDecayedLeaf(int leaf, int x, int y, int z) {
                // 단일 드랍 API를 공유해 자연 감쇠와 플레이어 파괴의 확률표가 어긋나지 않게 한다.
                rt.itemSystem().spawnMinedBlockDrop((short) leaf, PlayerInventory.EMPTY,
                        x + 0.5, y + 0.5, z + 0.5);
            }

            @Override
            public void hatchTurtleEggs(int x, int y, int z, int count) {
                rt.mobSystem().hatchTurtleEggs(x, y, z, count);
            }

            @Override
            public boolean requestAnimalBlockSpawn(
                    AnimalDependencyBlockRules.SpawnKind kind, int x, int y, int z) {
                MobSystem.AnimalDependencySpawnKind spawnKind = switch (kind) {
                    case TADPOLE -> MobSystem.AnimalDependencySpawnKind.TADPOLE;
                    case SNIFFER -> MobSystem.AnimalDependencySpawnKind.SNIFFER;
                };
                if (activeAnimalBlockDueTick == Long.MIN_VALUE) {
                    throw new IllegalStateException("animal hatch requested outside scheduled tick");
                }
                return settleAnimalBlockHatch(
                        spawnKind, x, y, z, activeAnimalBlockDueTick);
            }
        }, rt.seed());
        // 실제 FIRE 셀의 bounded scheduler를 아이템·환경 화상 판정에 함께 연결한다.
        rt.itemSystem().setFireLookup(randomTicks::isBurning);
        // [DEEP-DARK] 사슬은 월드에서 딱 이만큼만 요구한다. 워든 소환 훅은 이제 NO_OP 이 아니라
        // 실제 몹 원장에 종을 세우는 `MobSystem#summonWarden` 이다 — 중복 방지·출현 연출·어그로
        // 초기화가 모두 그 한 지점에 있다.
        this.sculkVibrations = new SculkVibrationSystem(new SculkVibrationSystem.World() {
            @Override public void notifyMobileListeners(SculkVibrationSystem.Vibration vibration) {
                rt.mobSystem().notifyWardenVibration(vibration.x(), vibration.y(), vibration.z(), vibration.sourceNickname());
            }

            @Override
            public int blockAt(int x, int y, int z) {
                if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return UNAVAILABLE_BLOCK;
                return residentBlockType(rt.accessor(), x, y, z);
            }

            @Override
            public boolean scanSection(int sectionX, int sectionY, int sectionZ,
                    SculkVibrationSystem.FixtureVisitor visitor) {
                // 비상주도 현재 권위 사실로는 빈 섹션이다. false를 돌려 blockAt fallback을
                // 실행하면 청크 경계의 첫 발걸음마다 16³ 미스 조회가 되므로, 활성화 훅이
                // 나중에 정확히 다시 채우는 production 경로는 항상 처리 완료로 답한다.
                rt.accessor().scanResidentSection(sectionX, sectionY, sectionZ, visitor::visit);
                return true;
            }

            @Override
            public int stateAt(int x, int y, int z, int blockType) {
                return rt.blockStates().get(x, y, z, blockType);
            }

            @Override
            public void setState(int x, int y, int z, int blockType, int state) {
                if (residentBlockType(rt.accessor(), x, y, z) != blockType) return;
                rt.setBlockState(x, y, z, blockType, state);
                rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) blockType);
            }

            @Override
            public List<SculkVibrationSystem.PlayerRef> players() {
                List<SculkVibrationSystem.PlayerRef> refs = new ArrayList<>(rt.players().size());
                for (PlayerTickState player : rt.players().values()) {
                    if (player.isDead()) continue;
                    refs.add(new SculkVibrationSystem.PlayerRef(
                            player.nickname(), player.x(), player.y(), player.z()));
                }
                return refs;
            }

            @Override
            public void grantDarkness(String nickname,
                    SculkVibrationRules.DarknessGrant grant) {
                PlayerTickState player = rt.players().get(nickname);
                if (player == null) return;
                player.applyStatusEffect(StatusEffect.DARKNESS, grant.amplifier(),
                        grant.durationMcTicks() / StatusEffects.MC_TICKS_PER_SERVER_TICK);
            }

            @Override
            public void playSound(String kind, int x, int y, int z, int blockType) {
                // [DEEP-DARK] 스컬크 전용 음향군이 착지해 예고대로 이 한 줄만 바뀌었다.
                // kind 는 SOUND_SENSOR_CLICK/SOUND_SHRIEK 이며 클라 `WorldSoundKind` 의 같은
                // 문자열이다. 비명은 volume 2.0F 라 SoundRules 가 32블록으로 나른다.
                broadcastWorldSound(kind, x, y, z, (short) blockType);
            }
        }, new SculkVibrationRules.WardenSummonSink() {
            @Override
            public void summonWarden(int x, int y, int z) {
                summonWarden(x, y, z, null);
            }

            @Override
            public void summonWarden(int x, int y, int z, String nickname) {
                rt.mobSystem().summonWarden(x, y, z, nickname);
            }
        });
        // [DEEP-DARK] 말린 가스트 수화 사슬. 진동 사슬과 같은 4 메서드짜리 좁은 월드 포트를 쓰고,
        // 소생 훅은 NO_OP 이 아니라 실제 몹 원장에 가스틀링을 세우는 `MobSystem#reviveGhastling`
        // 이다(나이·영속·좌표 규약은 전부 `sculk.GhastlingRevival` 한 지점에 있다).
        this.driedGhastHydration = new DriedGhastHydrationSystem(
                new DriedGhastHydrationSystem.World() {
                    @Override
                    public int blockAt(int x, int y, int z) {
                        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return UNAVAILABLE_BLOCK;
                        return residentBlockType(rt.accessor(), x, y, z);
                    }

                    @Override
                    public int stateAt(int x, int y, int z, int blockType) {
                        return rt.blockStates().get(x, y, z, blockType);
                    }

                    @Override
                    public void setState(int x, int y, int z, int blockType, int state) {
                        if (residentBlockType(rt.accessor(), x, y, z) != blockType) return;
                        rt.setBlockState(x, y, z, blockType, state);
                        rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) blockType);
                    }

                    @Override
                    public void clearBlock(int x, int y, int z) {
                        // 촉매 개화·콘크리트 경화와 같은 단일 변경 경로다 — diff 영속·방송·이웃
                        // 유체 재활성화가 그대로 따라온다.
                        rt.fluidSim().applyChange(x, y, z, Blocks.AIR);
                    }
                },
                (x, y, z) -> rt.mobSystem().reviveGhastling(x, y, z));
        // [CREAKING] 크리킹 하트 사슬. 수화 사슬과 같은 좁은 월드 포트(블록 조회 · 월드 시각 ·
        // 겉모습 스왑)를 쓰고, 소환/소멸 훅은 실제 몹 원장을 건드리는 `MobSystem` 문이다 —
        // 중복 방지·결속 좌표 규약은 전부 `creaking.CreakingSummon` 한 지점에 있다.
        this.creakingHearts = new com.gameexpert.engine.creaking.CreakingHeartSystem(
                new com.gameexpert.engine.creaking.CreakingHeartSystem.World() {
                    @Override
                    public int blockAt(int x, int y, int z) {
                        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return UNAVAILABLE_BLOCK;
                        return residentBlockType(rt.accessor(), x, y, z);
                    }

                    @Override
                    public long worldTime() {
                        return rt.clock().worldTime();
                    }

                    @Override
                    public void swapHeartBlock(int x, int y, int z, int blockId) {
                        // 촉매 개화·수화 소생과 같은 단일 변경 경로다 — diff 영속·방송·이웃
                        // 갱신이 그대로 따라온다. 화로 점화 쌍둥이 스왑과 같은 성격의 편집이다.
                        rt.fluidSim().applyChange(x, y, z, blockId);
                    }
                },
                (x, y, z) -> rt.mobSystem().summonCreaking(x, y, z),
                (x, y, z) -> rt.mobSystem().dismissCreaking(x, y, z));
        rt.environment().setFireLookup(randomTicks::isBurning);
        this.redstone = createRedstoneHost();
    }

    void trackLoadedFire(int x, int y, int z) {
        randomTicks.trackLoadedFire(x, y, z, rt.tickNo());
    }

    /**
     * [DEEP-DARK] 청크 적재가 되살린 말린 가스트를 수화 원장에 넣는다. 화재
     * ({@link #trackLoadedFire})와 같은 자리·같은 이유다 — 말린 가스트는 자연 지형에 없고
     * 영속 diff 로만 돌아오므로, 이 훅이 없으면 재접속 뒤 수화가 영원히 멈춘다.
     */
    void trackLoadedDriedGhast(int x, int y, int z) {
        driedGhastHydration.track(x, y, z);
    }

    /**
     * [CREAKING] 청크 적재가 되살린 크리킹 하트를 사슬 원장에 넣는다. 말린 가스트
     * ({@link #trackLoadedDriedGhast})와 같은 자리·같은 이유다 — 하트는 자연 지형에 없고
     * 영속 diff 로만 돌아오므로, 이 훅이 없으면 재접속 뒤 그 하트는 영원히 깨어나지 않는다.
     */
    void trackLoadedCreakingHeart(int x, int y, int z) {
        creakingHearts.track(x, y, z);
    }

    void trackLoadedPoplarControl(int x, int y, int z, int blockType, int state) {
        trackLoadedRedstone(x, y, z, blockType, state);
    }

    void onFireChunkReleased(int chunkX, int chunkZ) {
        randomTicks.onChunkReleased(chunkX, chunkZ);
        randomTickChunkViews.remove(RandomTickSystem.chunkKey(chunkX, chunkZ));
    }

    private RandomTickSystem.ChunkView materializedRandomTickChunkView(int chunkX, int chunkZ) {
        TerrainAccessor.SnapshotSource source = rt.accessor().snapshotSource(chunkX, chunkZ);
        if (source == null) return null;
        long key = RandomTickSystem.chunkKey(chunkX, chunkZ);
        return randomTickChunkViews.view(key, source);
    }

    /**
     * ① 본문: 이동과 플레이 액션을 도착 순서로 적용한 뒤 부활과 이웃 시뮬레이션 창을 갱신한다.
     * 틱과 {@link #runPromptActions()} 가 함께 쓴다. 액션 실패가 있었으면 true.
     */
    private boolean applyQueuedActions(long tickNo, ArchitectureTurnEvent architecture) {
        ActionQueue.Drained drained = rt.actionQueue().drain();
        if (architecture != null) {
            architecture.moveActions = drained.moves().size();
            architecture.gameplayActions = drained.gameplayActions().size();
            architecture.respawnActions = drained.respawns().size();
        }
        // 액션 하나의 예외가 유체·몹·브로드캐스트까지 남은 틱 전체를 건너뛰게 하면, 특정 입력을
        // 반복해 보내는 것만으로 월드 전체를 멈출 수 있다. 실패는 그 액션에서 끝낸다.
        ActionFailureLog failures = new ActionFailureLog();
        // 컨테이너 정산 창에 걸려 보류된 편집을 이번 틱의 새 액션보다 먼저 되살린다.
        replayDeferredContainerEdits(tickNo, failures);
        for (PlayerAction action : drained.orderedActions()) {
            try {
                if (action instanceof PlayerAction.Move move) applyMove(move, tickNo);
                else applyGameplayAction(action, tickNo);
            } catch (Exception exception) {
                failures.record(action, exception);
            }
        }
        for (PlayerAction.Respawn respawn : drained.respawns()) {
            try {
                applyRespawn(respawn, tickNo);
            } catch (Exception exception) {
                failures.record(respawn, exception);
            }
        }
        rt.pumpPlayerContainerActions();
        pumpCartographySettlements();
        failures.summarize(rt.worldId(), tickNo);
        // Movement/respawn is now authoritative.  Expire any old neighborhood before fluid, growth, mob,
        // projectile, and environment systems can observe it in this same tick.
        rt.refreshActiveSimulationChunks();
        return failures.hasFailures();
    }

    /**
     * 틱 사이에 도착한 플레이 입력을 다음 100ms 틱을 기다리지 않고 소유 스레드에서 처리한다.
     * 바닐라 서버가 틱 사이 대기 시간에 받은 패킷을 곧바로 처리하는 것과 같다. 예전에는 입력이
     * 다음 틱의 ① 까지 기다린 뒤 ⑤ 에서야 결과가 나가서, 블록 파괴·설치·상호작용이 매번 최대 한
     * 틱 늦었다. 이 턴은 다음 틱의 ① 을 앞당겨 실행한 것이다: 틱 번호는 그 틱의 번호로 보이고
     * (채굴 제한처럼 틱으로 재는 규칙이 예전과 같게), 시계는 ② 전이라 그대로다. 틱 전용 단계
     * (시계·유체·몹·주기 저장)는 건드리지 않고, 이번 입력이 만든 변경만 ⑤ 와 같은 경로로 내보낸다.
     * 이동 표시(playerMoves)는 다음 틱 ⑤ 가 그대로 맡는다.
     */
    void runPromptActions() {
        if (!rt.ownerTurnMayContinue() || rt.actionQueue().size() == 0) return;
        TickSafetyTelemetry.beginTick();
        rt.beginPromptActionTurn();
        try {
            TickSafetyTelemetry.phase(TickSafetyTelemetry.Phase.PLAYER);
            applyQueuedActions(rt.tickNo(), null);
            if (!rt.ownerTurnMayContinue()) return;
            broadcastBlocks();
            broadcastFireState();
            broadcastSoundEvents();
            broadcastHealth();
            broadcastXp();
            broadcastEffects();
            // 캔 블록의 드롭은 틱의 아이템 단계에서야 지면 정산에 들어갔다. 지금 제출하면 커밋이
            // 닿는 즉시 itemSpawn 이 나간다(같은 묶음은 틱의 아이템 단계가 다시 제출하지 않는다).
            if (rt.groundMutationSettlements() != null && rt.hasPendingGroundSpawns()) {
                rt.settlePendingGroundSpawns();
            }
        } finally {
            rt.endPromptActionTurn();
            TickSafetyTelemetry.endTick();
        }
    }

    void flushRuntimeFallingPublication() { broadcastBlocks(); }

    private void runFallingPendingActions() {
        if (rt.actionQueue().size() == 0) return;
        applyQueuedActions(rt.tickNo(), rt.architectureTurn());
        broadcastMoves();
        broadcastBlocks();
        broadcastSoundEvents();
        broadcastHealth();
        broadcastXp();
        broadcastEffects();
    }

    void runTick(TickTiming timing) {
        if (!rt.ownerTurnMayContinue()) {
            return;
        }
        try {
            if (!rt.runtimeFallingSpeleothems().retryBeforePrelude()) {
                runFallingPendingActions();
                return;
            }
            timing.beginPhase(TickTiming.PRELUDE, System.nanoTime());
            rt.runTickPrelude();
            timing.endPhase(System.nanoTime());
            if (!rt.ownerTurnMayContinue()) return;
            timing.beginPhase(TickTiming.BEGIN_TICK, System.nanoTime());
            rt.beginTick();
            timing.endPhase(System.nanoTime());
            if (!rt.ownerTurnMayContinue()) return;
            long tickNo = rt.tickNo();
            if (!rt.runtimeFallingSpeleothems().beforeActions()) {
                runFallingPendingActions();
                return;
            }

            // ①.0 연결 ID를 확인해 퇴장시키고 최종 상태를 WS 대기자에게 넘긴다.
            timing.beginPhase(TickTiming.SMELT, System.nanoTime());
            TickSafetyTelemetry.phase(TickSafetyTelemetry.Phase.PLAYER);
            drainPlayerLeaves();
            advanceTimeSinceRest();
            broadcastSleepStatusIfRosterChanged();
            timing.endPhase(System.nanoTime());
            if (!rt.ownerTurnMayContinue()) return;

            // ① ActionQueue 드레인 (이동 → 도착 순서의 플레이 액션 → 부활)
            timing.beginPhase(TickTiming.ACTION, System.nanoTime());
            if (applyQueuedActions(tickNo, rt.architectureTurn())) timing.markExceptionalTurn();
            processP3DelayedTransitions(tickNo);
            processFrostedIce(tickNo);
            flushRedstone();
            processDimensionPortals(tickNo);
            processEndGateways(tickNo);
            timing.endPhase(System.nanoTime());
            if (!rt.ownerTurnMayContinue()) return;

            // ①.9 좌표 화로 진행. 비상주 청크는 정지하며 다시 활성화되면 이어서 처리합니다.
            timing.beginPhase(TickTiming.SMELT, System.nanoTime());
            processFurnaces();
            processBrewingSessions();
            processCampfires();
            timing.endPhase(System.nanoTime());
            if (!rt.ownerTurnMayContinue()) return;

            // ② 월드 시계
            timing.beginPhase(TickTiming.CLOCK, System.nanoTime());
            rt.clock().advance(rt.ctx().properties().timeScale());
            // 접속자 전원 수면 시 아침(worldTime→0) 전환 + wakeUp 방송(S2a).
            handleSleepMorning();
            timing.endPhase(System.nanoTime());
            if (!rt.ownerTurnMayContinue()) return;

            // ③ 유체
            timing.beginPhase(TickTiming.FLUID, System.nanoTime());
            TickSafetyTelemetry.phase(TickSafetyTelemetry.Phase.FLUID);
            rt.fluidSim().drainDue();
            timing.endPhase(System.nanoTime());
            if (!rt.ownerTurnMayContinue()) return;

            // ③.3 접속자 주변 한정 랜덤틱(잎·잔디·성장·자연 화재).
            timing.beginPhase(TickTiming.RANDOM_TICK, System.nanoTime());
            TickSafetyTelemetry.phase(TickSafetyTelemetry.Phase.RANDOM_TICK);
            randomTickLight.beginTick();
            tickRedstone();
            processSignalNeighbourUpdates();
            processScheduledAnimalBlocks(rt.clock().gameTimeMcTicks());
            lastRandomTickStats = randomTicks.tick(
                    nearbyRandomTickChunks(rt.players().values()), tickNo);
            persistEyeblossomSchedules();
            primedTnt.tick();
            rt.tickFallingSpeleothems();
            timing.endPhase(System.nanoTime());
            if (!rt.ownerTurnMayContinue()) return;

            // ③.5 몹·투사체·전투(유체 뒤, 환경 데미지 앞)
            timing.beginPhase(TickTiming.MOB, System.nanoTime());
            TickSafetyTelemetry.phase(TickSafetyTelemetry.Phase.MOB_COMBAT);
            // durable 기록이 확인된 몹 변형만 먼저 적용한다(§39cd write-ahead 저널).
            // 이번 틱 역할 행동이 새로 요청한 항목은 durable 확인 뒤 다음 틱에 적용된다.
            rt.pumpMobMutationJournal();
            // [DRAGON] ServerLevel.tick: 드래곤전 → 개체(수정 불 · 드래곤) 순서. 몹 lane 이 드래곤 이동을 방송한다.
            rt.dragonFight().tick(tickNo);
            rt.mobSystem().tick(tickNo);
            // [SPEAR-KINETIC] 창 돌진(ItemStack.onUseTick → KineticWeapon.damageEntities): 몹이 움직인 뒤 같은 단계.
            rt.mobSystem().combat().retainKineticUses(rt.players().keySet());
            for (PlayerTickState player : rt.players().values()) applySpearKinetic(player, tickNo);
            enderEyes.tick();
            applyTurtleEggPlacementRequests();
            applyFrogspawnPlacementRequests();
            applyBeeHiveEntryRequests();
            applyGoatRamImpactRequests();
            applySnifferDigDropRequests();
            applyFroglightSettlementRequests();
            // 행상인 창은 먹 lane에서 바뀐다. 일수와 같은 행에 원자적으로
            // 저장해 재시작이 절대 시계와 다음 시도를 서로 다른 날로 갈라놓지 않게 한다.
            rt.persistWorldClockIfChanged();
            // 몹 lane 의 주민 재입고로 오퍼가 바뀌었으면 열린 거래 화면을 갱신한다.
            resendRestockedVillagerOffers();
            reconcileVillagerTradeSessions();
            // [MOUNT] 화물 패널의 대상은 스스로 움직이므로 좌표 컨테이너처럼 "블록이 부서질 때"
            // 한 지점에서 닫을 수 없다. 몹 lane 이 끝난 이 자리에서 죽음·소멸·이탈을 함께 본다.
            reconcileMobCargoSessions();
            reconcileGeneratedEntityCargoSessions();
            timing.endPhase(System.nanoTime());
            if (!rt.ownerTurnMayContinue()) return;

            // ③.55 변경 이웃 갱신: 중력 블록을 한 칸 이동한 뒤 부착 블록의 지지 상실을 연쇄 처리한다.
            timing.beginPhase(TickTiming.NEIGHBOR, System.nanoTime());
            processBlockNeighbors();
            retryPendingOneShotFeedback();
            publishReadyFinalScenePrerequisites();
            timing.endPhase(System.nanoTime());
            if (!rt.ownerTurnMayContinue()) return;

            // ③.6 드랍 아이템 엔티티(몹 뒤·환경 앞: 몹 드랍이 같은 틱에 스폰되게, S2a)
            timing.beginPhase(TickTiming.ITEM, System.nanoTime());
            // [CONTAINER-MENUS] ServerLevel runs scheduled block ticks (DispenserBlock#tick ->
            // dispenseFrom) before entities; a dispensed item or projectile moves this same tick.
            rt.dispenserSystem().tick();
            rt.crafterSystem().tick();
            rt.itemSystem().tick(tickNo);
            rt.xpOrbSystem().tick();
            // [BLOCK-ENTITY] Rows of block entities replaced without a spill leave with their block.
            rt.sweepReplacedBlockEntities();
            // [HOPPER] ServerLevel ticks block entities after entities: two game ticks of hoppers.
            rt.hopperSystem().tick();
            // [BEACON] 신호기 블록 엔티티도 같은 자리(개체 뒤)에서 돈다.
            processBeacons(tickNo);
            // [JUKEBOX] 주크박스 곡 틱(JukeboxBlockEntity.tick → JukeboxSongPlayer.tick).
            processJukeboxes();
            // Projectile termination is submitted first because its transaction also includes the
            // resulting ground recovery. A crash can therefore observe neither side or both, never
            // a durable trident item while the old projectile row is still live.
            rt.persistProjectiles();
            // Item and XP identity, motion, age and removals cross the same durable tick boundary.
            rt.persistGroundEntities(tickNo);
            timing.endPhase(System.nanoTime());
            if (!rt.ownerTurnMayContinue()) return;

            // ③.7 보트(클라 권위): 좌석·위치중계·수명만. 파괴 드랍은 다음 아이템 틱에서 정산됨.
            timing.beginPhase(TickTiming.BOAT, System.nanoTime());
            rt.boatSystem().tick(tickNo);
            rt.placedEntities().tick(tickNo);
            rt.cushionSystem().tick(tickNo);
            rt.processGeneratedCushionSupportLoss(tickNo);
            timing.endPhase(System.nanoTime());
            if (!rt.ownerTurnMayContinue()) return;

            // ④ 환경 데미지 · 재생 · 사망
            timing.beginPhase(TickTiming.ENVIRONMENT, System.nanoTime());
            TickSafetyTelemetry.phase(TickSafetyTelemetry.Phase.ENVIRONMENT);
            for (PlayerTickState player : rt.players().values()) {
                int playerChunkX = Math.floorDiv((int) Math.floor(player.x()), Blocks.CHUNK_X);
                int playerChunkZ = Math.floorDiv((int) Math.floor(player.z()), Blocks.CHUNK_Z);
                boolean terrainReady = hasResidentHalo(rt.accessor(), playerChunkX, playerChunkZ);
                // [SULFUR] 유황 간헐천 분출 위상은 월드 틱의 순수 함수라 환경 틱이 틱 번호를
                // 함께 받는다. 다른 환경 피해는 틱 번호를 쓰지 않는다.
                if (terrainReady) settleBurningPlayerInCauldron(player);
                rt.environment().tick(player, tickNo, terrainReady);
                if (player.finishPendingBandage()) sendTo(player, inventoryMessage(player));
                tickElytraWear(player);
            }
            queueEnvironmentSupportChecks(rt.drainEnvironmentSupportChanges());
            timing.endPhase(System.nanoTime());
            if (!rt.ownerTurnMayContinue()) return;

            // ⑤ 배칭 브로드캐스트
            timing.beginPhase(TickTiming.BROADCAST_SAVE, System.nanoTime());
            // [CONTAINER-MENUS] Neighbour updates of this tick's block changes: shelves and
            // dispensers re-read Level.hasNeighborSignal (e.g. a powering button was mined).
            flushRedstone();
            processSignalNeighbourUpdates();
            // [DEEP-DARK] 방송 앞에서 돈다. 감지체 반짝임 비트·비명체 경고 단계가 이 틱의
            // 블록 변경에 합류해 같은 배치로 나가야 한다.
            tickSculkVibrations(tickNo);
            tickBellResonance(tickNo);
            tickDriedGhastHydration(tickNo);
            tickCreakingHearts();
            tickPotentSulfurPresentation(tickNo);
            updateSelectedMaps(tickNo);
            broadcastMoves();
            broadcastBlocks();
            broadcastFireState();
            broadcastSoundEvents();
            broadcastHealth();
            broadcastXp();
            broadcastEffects();
            refreshBedSpawnPoints();
            if (tickNo % TIMESYNC_INTERVAL == 0) {
                rt.ctx().broadcaster().enqueueBroadcastFromTick(rt.worldId(),
                        new TimeSync(rt.clock().worldTime(), rt.clock().dayCount()));
            }
            rt.sweepReplacedBlockEntities();
            long chestFlushTicks = Math.max(1L, rt.ctx().properties().blockFlushMs() / 100L);
            if (tickNo % chestFlushTicks == 0) {
                rt.flushDirtyChests();
                rt.flushDirtyFurnaces();
                rt.flushDirtyBrewing();
                rt.flushDirtyCampfires();
                rt.flushPersistentMobs();
                flushPrimedTnt();
            }
            if (tickNo % SAVE_INTERVAL == 0) {
                // 마지막 저장 이후 변경된 플레이어만 저장(무변경 플레이어의 스냅샷·executor 제출 스킵).
                // 퇴장/폐기/제련 완료 저장은 무조건 저장 경로라 이 가드의 영향을 받지 않는다.
                for (PlayerTickState player : rt.players().values()) {
                    if (player.persistDirty()) {
                        rt.saveDirtyState(player);
                    }
                }
            }
            timing.endPhase(System.nanoTime());
            if (!rt.ownerTurnMayContinue()) return;

            rt.tickFleshNether();

            // ⑥ BlockDiffBuffer 병합은 ⑤ broadcastBlocks 의 단일 순회에서 함께 처리됨.

            // 세션 0 이후 유예 후 폐기
            timing.beginPhase(TickTiming.LIFECYCLE, System.nanoTime());
            if (rt.isEmpty()) {
                rt.incrementEmptyTicks();
                if (rt.emptyTicks() >= rt.graceTicks()) {
                    rt.dispose();
                }
            } else {
                rt.resetEmptyTicks();
            }
            timing.endPhase(System.nanoTime());
        } catch (Throwable error) {
            timing.markExceptionalTurn();
            String failedPhase = timing.activePhaseLabel();
            timing.endPhase(System.nanoTime());
            log.error("월드 {} 틱 처리 오류: 단계={} 경과={}ms", rt.worldId(), failedPhase,
                    timing.measuredNanos() / 1_000_000, error);
        }
    }

    void logSlowOwnerTurn(TickTiming timing, long elapsedNanos) {
        long otherNanos = timing.otherNanos(elapsedNanos);
        // This method is reached only for a real nanosecond-precision overrun. Normal ticks pay only
        // primitive nanoTime/add operations; SLF4J's argument array and boxing stay entirely off the hot path.
        log.warn("월드 {} 틱이 예산 초과: {}ms "
                        + "(틱안전 {}ms, 선행작업 {}ms, 시작/날씨 {}ms, 액션 {}ms, 제련 {}ms, 시계 {}ms, "
                        + "유체 {}ms, 랜덤틱 {}ms, 몹/투사체/전투 {}ms, 이웃갱신 {}ms, 아이템 {}ms, 보트 {}ms, "
                        + "환경 {}ms, 브로드캐스트/저장 {}ms, 세션 생명주기 {}ms, 스냅샷준비 {}ms, 트리계획 {}ms, "
                        + "근접준비 {}ms, 청크수요 {}ms, 청크승인 {}ms, 장식 {}ms, 준비스냅샷 {}ms, "
                        + "스냅샷전달 {}ms, 스냅샷기한 {}ms, 계측외 {}ms; "
                        + "몹광도 후보 {}, 메모 적중 {}, 메모 실패 {}, BFS 방문 {}, 단일최대 {})",
                rt.worldId(), elapsedNanos / 1_000_000,
                timing.safetyNanos / 1_000_000, timing.preludeNanos / 1_000_000,
                timing.beginTickNanos / 1_000_000, timing.actionNanos / 1_000_000,
                timing.smeltNanos / 1_000_000, timing.clockNanos / 1_000_000,
                timing.fluidNanos / 1_000_000, timing.randomTickNanos / 1_000_000,
                timing.mobNanos / 1_000_000, timing.neighborNanos / 1_000_000,
                timing.itemNanos / 1_000_000, timing.boatNanos / 1_000_000,
                timing.environmentNanos / 1_000_000, timing.broadcastSaveNanos / 1_000_000,
                timing.lifecycleNanos / 1_000_000, timing.snapshotPreparationNanos / 1_000_000,
                timing.treePlanningNanos / 1_000_000, timing.proximityNanos / 1_000_000,
                timing.chunkDemandNanos / 1_000_000, timing.chunkAdmissionNanos / 1_000_000,
                timing.decorationNanos / 1_000_000, timing.preparedSnapshotNanos / 1_000_000,
                timing.snapshotDeliveryNanos / 1_000_000, timing.snapshotDeadlineNanos / 1_000_000,
                otherNanos / 1_000_000,
                rt.mobSystem().lightCandidateQueries(), rt.mobSystem().lightMemoHits(),
                rt.mobSystem().lightMemoMisses(), rt.mobSystem().lightBfsVisitedCells(),
                rt.mobSystem().lightMaxBfsVisitCount());
    }

    /**
     * Marks the scheduled start of an armed performance turn. A negative result means no recorder
     * was armed when this owner turn began, so an install performed in its action phase is excluded.
     */
    long beginPerformanceOwnerTurn(long startedNanos) {
        if (performanceWindow == null) return -1L;
        if (performanceNextStartNanos == 0L) performanceNextStartNanos = startedNanos;
        long lateness = Math.max(0L, startedNanos - performanceNextStartNanos);
        performanceNextStartNanos += TICK_BUDGET_NANOS;
        return lateness;
    }

    /** Allocation-free while recording; report construction/JSON serialization happens after finish. */
    void completePerformanceOwnerTurn(TickTiming timing, long elapsedNanos,
            long schedulerLatenessNanos) {
        OwnerTurnPerformanceWindow window = performanceWindow;
        if (window == null || schedulerLatenessNanos < 0L) return;
        RandomTickSystem.TickStats randomStats = lastRandomTickStats;
        int randomChunks = randomStats == null ? 0 : randomStats.activeChunks();
        int randomSamples = randomStats == null ? 0 : randomStats.samples();
        int randomTransitions = randomStats == null ? 0 : randomStats.transitions();
        OwnerTurnPerformanceWindow.MutableTurnObservation observation = performanceObservation.reset()
                .timing(elapsedNanos, schedulerLatenessNanos, exceedsTickBudget(elapsedNanos))
                .phase(OwnerTurnPerformanceWindow.Phase.SAFETY, timing.safetyNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.PRELUDE, timing.preludeNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.BEGIN_TICK, timing.beginTickNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.ACTION, timing.actionNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.SMELT, timing.smeltNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.CLOCK, timing.clockNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.FLUID, timing.fluidNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.RANDOM_TICK, timing.randomTickNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.MOB, timing.mobNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.NEIGHBOR, timing.neighborNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.ITEM, timing.itemNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.BOAT, timing.boatNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.ENVIRONMENT, timing.environmentNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.BROADCAST_SAVE, timing.broadcastSaveNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.LIFECYCLE, timing.lifecycleNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.SNAPSHOT_PREPARATION,
                        timing.snapshotPreparationNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.TREE_PLANNING, timing.treePlanningNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.PROXIMITY, timing.proximityNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.CHUNK_DEMAND, timing.chunkDemandNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.CHUNK_ADMISSION, timing.chunkAdmissionNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.DECORATION, timing.decorationNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.PREPARED_SNAPSHOT,
                        timing.preparedSnapshotNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.SNAPSHOT_DELIVERY,
                        timing.snapshotDeliveryNanos)
                .phase(OwnerTurnPerformanceWindow.Phase.SNAPSHOT_DEADLINE,
                        timing.snapshotDeadlineNanos)
                .workload(rt.mobSystem().currentActiveMobCount(),
                        rt.mobSystem().qaLiveFixtureMobCount(performanceFixtureMobIds),
                        randomChunks, randomSamples, randomTransitions)
                .mobLight(rt.mobSystem().lightCandidateQueries(),
                        rt.mobSystem().lightMemoHits(), rt.mobSystem().lightMemoMisses(),
                        rt.mobSystem().lightBfsVisitedCells(),
                        rt.mobSystem().lightMaxBfsVisitCount())
                .randomTickLight(randomTickLight.candidateLightQueries(),
                        randomTickLight.memoHits(), randomTickLight.memoMisses(),
                        randomTickLight.bfsVisitedCells(), randomTickLight.maxBfsVisitCount());
        if (timing.exceptionalTurn()) observation.exceptionalTurn();
        window.record(observation);
        if (!window.isComplete()) return;
        OwnerTurnPerformanceWindow.Result result = window.finish();
        lastPerformanceResult = result;
        performanceWindow = null;
        performanceObservation = null;
        performanceNextStartNanos = 0L;
        emitPerformanceResult(result);
    }

    private static final class PerformanceResultJson {
        private static final tools.jackson.databind.ObjectMapper MAPPER =
                new tools.jackson.databind.ObjectMapper();
    }

    private void emitPerformanceResult(OwnerTurnPerformanceWindow.Result result) {
        try {
            log.info("DENSE_WORLD_PERFORMANCE_RESULT {}",
                    PerformanceResultJson.MAPPER.writeValueAsString(result));
        } catch (RuntimeException serializationFailure) {
            log.error("DENSE_WORLD_PERFORMANCE_RESULT_SERIALIZATION_FAILED fixture={} checksum={}",
                    result.fixtureId(), Long.toUnsignedString(result.fixtureChecksum()),
                    serializationFailure);
        }
    }

    OwnerTurnPerformanceWindow.Result lastPerformanceResultForTest() {
        return lastPerformanceResult;
    }

    boolean performanceFixtureInstalledForTest(DenseWorldPerformanceFixturePlan plan) {
        return matchesInstalledPerformanceFixture(plan);
    }

    boolean performanceWindowArmedForTest() {
        return performanceWindow != null;
    }

    void setPerformanceReadinessForTest(int activeChunks, int samples, int transitions) {
        lastRandomTickStats = new RandomTickSystem.TickStats(activeChunks, samples, transitions);
    }

    static Set<Long> nearbyRandomTickChunks(Iterable<PlayerTickState> players) {
        Set<Long> chunks = new LinkedHashSet<>();
        for (PlayerTickState player : players) {
            int centerX = Math.floorDiv((int) Math.floor(player.x()), Blocks.CHUNK_X);
            int centerZ = Math.floorDiv((int) Math.floor(player.z()), Blocks.CHUNK_Z);
            for (int dz = -RandomTickSystem.CHUNK_RADIUS; dz <= RandomTickSystem.CHUNK_RADIUS; dz++) {
                for (int dx = -RandomTickSystem.CHUNK_RADIUS; dx <= RandomTickSystem.CHUNK_RADIUS; dx++) {
                    chunks.add(RandomTickSystem.chunkKey(centerX + dx, centerZ + dz));
                    if (chunks.size() >= RandomTickSystem.MAX_ACTIVE_CHUNKS) {
                        return chunks;
                    }
                }
            }
        }
        return chunks;
    }

    private static boolean hasResidentHalo(TerrainAccessor accessor, int chunkX, int chunkZ) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (!accessor.isChunkResident(chunkX + dx, chunkZ + dz)) return false;
            }
        }
        return true;
    }

    /**
     * Container mutations have a second, owner-side FIFO with its own bound.  A rejected handoff
     * is still an input rejection and must use the same player-facing error path as the ingress
     * action queue; silently dropping it would leave the client showing an uncommitted cursor.
     */
    private boolean enqueuePlayerContainerAction(PlayerTickState player, Runnable action) {
        if (rt.enqueuePlayerContainerAction(action)) return true;
        if (player != null) {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error("QUEUE_FULL"));
        }
        return false;
    }

    // ── ① move: pose 반영 ──
    /** 이동·부활을 제외한 입력은 WebSocket 도착 순서를 바꾸지 않고 이 단일 경계에서 처리합니다. */
    private void applyGameplayAction(PlayerAction action, long tickNo) {
        PlayerTickState player = rt.players().get(action.nickname());
        if (player == null) return;
        if (player.isDead()) {
            // CONTRACT §10: a correlated mobInteract is answered exactly once even while dead.
            if (action instanceof PlayerAction.MobInteract interact) {
                sendCompanionInteractionResult(player, interact, "rejected");
            }
            return;
        }
        // A later menu or world-use intent supersedes any crafting open still waiting on a lease.
        if (action instanceof PlayerAction.OpenLectern || action instanceof PlayerAction.OpenBook
                || action instanceof PlayerAction.Interact || action instanceof PlayerAction.MobInteract
                || action instanceof PlayerAction.GeneratedEntityInteract
                || action instanceof PlayerAction.OpenMountedMobInventory
                || action instanceof PlayerAction.BlockEdit
                || action instanceof PlayerAction.PlacedEntityCommand placed
                        && placed.op() == PlayerAction.PlacedEntityCommand.Op.INTERACT) {
            player.cancelPendingCraftingOpen();
        }
        switch (action) {
            case PlayerAction.SelectSlot select -> applySelectSlot(select);
            case PlayerAction.SwapHands swap -> applySwapHands(swap);
            case PlayerAction.SwapOffhand swap -> applySwapOffhand(swap);
            case PlayerAction.MapUse use -> applyMapUse(use);
            case PlayerAction.ShieldBlock shield -> applyShieldBlock(shield);
            case PlayerAction.EquipArmor equipArmor -> applyEquipArmor(equipArmor);
            case PlayerAction.MoveArmor moveArmor -> applyMoveArmor(moveArmor);
            case PlayerAction.BowUse bow -> applyBowUse(bow, tickNo);
            case PlayerAction.EnderPearlThrow pearl -> applyEnderPearlThrow(pearl);
            case PlayerAction.SnowballThrow snowball -> applySnowballThrow(snowball);
            case PlayerAction.EggThrow egg -> applyEggThrow(egg);
            case PlayerAction.PotionThrow potion -> applyPotionThrow(potion);
            case PlayerAction.WindChargeThrow windCharge -> applyWindChargeThrow(windCharge);
            case PlayerAction.EnderEyeThrow enderEye -> applyEnderEyeThrow(enderEye);
            case PlayerAction.FishingRodUse rod -> applyFishingRodUse(rod, tickNo);
            case PlayerAction.FireworkUse firework -> applyFireworkUse(firework);
            case PlayerAction.TrophyItemUse trophy -> applyTrophyItemUse(trophy, tickNo);
            case PlayerAction.GlideImpact impact -> applyGlideImpact(impact);
            case PlayerAction.Attack attack -> applyAttack(attack, tickNo);
            case PlayerAction.SpearStab stab -> applySpearStab(stab, tickNo);
            case PlayerAction.GeneratedEntityInteract interact ->
                    applyGeneratedEntityInteract(interact);
            case PlayerAction.GeneratedEntityAttack attack -> applyGeneratedEntityAttack(attack);
            case PlayerAction.GeneratedEntityCargoTransfer transfer ->
                    enqueuePlayerContainerAction(player,
                            () -> applyGeneratedEntityCargoTransfer(transfer));
            case PlayerAction.CloseGeneratedEntityCargo close ->
                    applyCloseGeneratedEntityCargo(close);
            case PlayerAction.GeneratedEntityEquipmentSettlement settlement ->
                    applyGeneratedEntityEquipmentSettlement(settlement);
            case PlayerAction.MoveSlot moveSlot -> applyMoveSlot(moveSlot);
            case PlayerAction.DropItem dropItem -> applyDropItem(dropItem);
            case PlayerAction.OpenCrafting open -> applyOpenCrafting(open);
            case PlayerAction.CraftingClick click -> applyCraftingClick(click);
            case PlayerAction.CraftingDrag drag -> applyCraftingDrag(drag);
            case PlayerAction.PlaceCraftingRecipe place -> applyPlaceCraftingRecipe(place);
            case PlayerAction.SelectStonecutterRecipe select ->
                    applySelectStonecutterRecipe(select);
            case PlayerAction.AnvilRename rename -> applyAnvilRename(rename);
            case PlayerAction.SelectLoomPattern select -> applySelectLoomPattern(select);
            case PlayerAction.BeaconConfirm confirm -> applyBeaconConfirm(confirm);
            case PlayerAction.OpenLectern open -> applyOpenLectern(open);
            case PlayerAction.LecternPage page -> applyLecternPage(page);
            case PlayerAction.CloseLectern close -> applyCloseLectern(close);
            case PlayerAction.OpenBook open -> applyOpenBook(open);
            case PlayerAction.EditBook edit -> applyEditBook(edit);
            case PlayerAction.SignBook sign -> applySignBook(sign);
            case PlayerAction.CloseBook close -> applyCloseBook(close);
            case PlayerAction.CollectCrafting collect -> applyCollectCrafting(collect);
            case PlayerAction.DropCraftingCursor drop -> applyDropCraftingCursor(drop);
            case PlayerAction.CloseCrafting close -> applyCloseCrafting(close);
            case PlayerAction.BrewingClick click ->
                    enqueuePlayerContainerAction(player, () -> applyBrewingClick(click));
            case PlayerAction.BrewingDrag drag ->
                    enqueuePlayerContainerAction(player, () -> applyBrewingDrag(drag));
            case PlayerAction.CollectBrewing collect ->
                    enqueuePlayerContainerAction(player, () -> applyCollectBrewing(collect));
            case PlayerAction.DropBrewingCursor drop ->
                    enqueuePlayerContainerAction(player, () -> applyDropBrewingCursor(drop));
            case PlayerAction.CloseBrewing close -> {
                player.cancelPendingCraftingOpen(close.requestId());
                enqueuePlayerContainerAction(player, () -> applyCloseBrewing(close));
            }
            case PlayerAction.BlockEdit edit -> applyEdit(edit);
            case PlayerAction.MineHit hit -> applyMineHit(hit);
            case PlayerAction.Interact interact -> applyInteract(interact);
            case PlayerAction.ShelfInteract shelf -> applyShelfInteract(shelf);
            case PlayerAction.BellRing ring -> applyBellRing(ring);
            case PlayerAction.PlaceItemFrame place -> applyPlaceItemFrame(place);
            case PlayerAction.PlaceEndCrystal place -> applyPlaceEndCrystal(place);
            case PlayerAction.EditSign edit -> applyEditSign(edit);
            case PlayerAction.MobInteract interact -> applyMobInteract(interact);
            // [FARM-ANIMAL] 돼지 조종. 좌표 정본이 기수 클라라 검증만 MobSystem 이 한다.
            case PlayerAction.PigPos pos -> applyPigPos(pos);
            case PlayerAction.LeavePig leave -> rt.mobSystem().dismountPig(leave.nickname());
            // [MOUNT] 종 비의존 좌석 계약(돼지 메시지는 하위호환으로 남는다).
            case PlayerAction.MobRiderPos pos -> applyMobRiderPos(pos);
            case PlayerAction.MobDismount leave ->
                    rt.mobSystem().dismountMob(leave.nickname());
            case PlayerAction.MobJump jump -> applyMobJump(jump);
            // 보트·쿠션 설치는 각 시스템 틱이 정산한다. 손은 도착 순서의 이 자리에서 잡아 넘긴다.
            case PlayerAction.PlacedEntityCommand command -> rt.placedEntities().apply(command);
            case PlayerAction.PlaceBoat place -> rt.boatSystem().enqueuePlace(place.nickname(),
                    place.x(), place.y(), place.z(), place.yaw(),
                    player.inventory().capture(inventoryHand(place.hand())));
            case PlayerAction.PlaceCushion place -> rt.cushionSystem().enqueuePlace(
                    place.nickname(), place.x(), place.y(), place.z(),
                    player.inventory().capture(inventoryHand(place.hand())));
            case PlayerAction.VillagerTrade trade -> applyVillagerTrade(trade);
            case PlayerAction.VillagerTradeSelect select -> applyVillagerTradeSelect(select);
            case PlayerAction.VillagerTradeClick click -> applyVillagerTradeClick(click);
            case PlayerAction.VillagerTradeDrag drag -> applyVillagerTradeDrag(drag);
            case PlayerAction.CollectVillagerTrade collect -> applyCollectVillagerTrade(collect);
            case PlayerAction.DropVillagerTradeCursor drop -> applyDropVillagerTradeCursor(drop);
            case PlayerAction.CloseVillagerTrade close -> applyCloseVillagerTrade(close);
            case PlayerAction.MoveMobCargoItem move -> applyMoveMobCargoItem(move);
            case PlayerAction.CloseMobCargo close -> applyCloseMobCargo(close);
            case PlayerAction.OpenMountedMobInventory open -> applyOpenMountedMobInventory(open);
            case PlayerAction.Consume consume -> applyConsume(consume, tickNo);
            case PlayerAction.MoveChestItem move ->
                    enqueuePlayerContainerAction(player, () -> applyMoveChestItem(move));
            case PlayerAction.CloseChest close -> applyCloseChest(close);
            case PlayerAction.ContainerClick click ->
                    enqueuePlayerContainerAction(player, () -> applyContainerClick(click));
            case PlayerAction.ContainerDrag drag ->
                    enqueuePlayerContainerAction(player, () -> applyContainerDrag(drag));
            case PlayerAction.CollectContainer collect ->
                    enqueuePlayerContainerAction(player, () -> applyCollectContainer(collect));
            case PlayerAction.DropContainerCursor drop -> applyDropContainerCursor(drop);
            case PlayerAction.CrafterSlotState state ->
                    enqueuePlayerContainerAction(player, () -> applyCrafterSlotState(state));
            case PlayerAction.FurnaceClick click ->
                    enqueuePlayerContainerAction(player, () -> applyFurnaceClick(click));
            case PlayerAction.FurnaceDrag drag ->
                    enqueuePlayerContainerAction(player, () -> applyFurnaceDrag(drag));
            case PlayerAction.CollectFurnace collect ->
                    enqueuePlayerContainerAction(player, () -> applyCollectFurnace(collect));
            case PlayerAction.PlaceFurnaceRecipe place ->
                    enqueuePlayerContainerAction(player, () -> applyPlaceFurnaceRecipe(place));
            case PlayerAction.DropFurnaceCursor drop -> applyDropFurnaceCursor(drop);
            case PlayerAction.CloseFurnace close -> applyCloseFurnace(close);
            case PlayerAction.EnchantClick click -> applyEnchantClick(click);
            case PlayerAction.EnchantDrag drag -> applyEnchantDrag(drag);
            case PlayerAction.CollectEnchant collect -> applyCollectEnchant(collect);
            case PlayerAction.SelectEnchantOffer select -> applySelectEnchantOffer(select);
            case PlayerAction.DropEnchantCursor drop -> applyDropEnchantCursor(drop);
            case PlayerAction.CloseEnchanting close -> applyCloseEnchanting(close);
            case PlayerAction.QaSeed seed -> applyQaSeed(player, seed, tickNo);
            case PlayerAction.QaMobAuditStage stage -> {
                if (stage.fixtureAuthorized() && rt.ctx().properties().qaSeeding()
                        && rt.seed() == (int) com.gameexpert.config.EngineProperties.CONTENT_QA_WORLD_SEED) {
                    rt.mobSystem().qaStageMobBoundary(stage.mobId(), stage.stage());
                }
            }
            case PlayerAction.QaPerformanceFixture fixture ->
                    applyQaPerformanceFixture(fixture);
            case PlayerAction.Move ignored ->
                    throw new IllegalArgumentException("move must be drained in the movement phase");
            case PlayerAction.Respawn ignored ->
                    throw new IllegalArgumentException("respawn must be drained in the respawn phase");
        }
    }

    private void applyGeneratedEntityInteract(PlayerAction.GeneratedEntityInteract interact) {
        dispatchGeneratedEntityInteract(rt.generatedStructureEntities(), interact,
                this::applyGeneratedEntityInteractLeaf);
    }

    private boolean applyGeneratedEntityInteractLeaf(PlayerAction.GeneratedEntityInteract interact,
            com.gameexpert.engine.persistence.finalcarrier.structureentity
                    .WorldGeneratedStructureEntityState.RuntimeSnapshot target) {
        return switch (interact.kind()) {
            case CUSHION -> {
                var cushion = (com.gameexpert.engine.persistence.finalcarrier.structureentity
                        .WorldGeneratedStructureEntityState.CushionRuntimeSnapshot) target;
                GeneratedCushionActionPolicy.Action action = interact.nickname().equals(
                        cushion.rider()) ? GeneratedCushionActionPolicy.Action.LEAVE
                                : GeneratedCushionActionPolicy.Action.SIT;
                yield submitGeneratedCushionAction(target, action, interact.nickname());
            }
            case ARMOR_STAND -> applyGeneratedEntityEquipmentSettlement(
                    new PlayerAction.GeneratedEntityEquipmentSettlement(interact.nickname(),
                            interact.entityId(), interact.hand()), target);
            case CHEST_MINECART -> openGeneratedMinecartCargo(interact.nickname(), target, interact.requestId());
        };
    }

    private boolean openGeneratedMinecartCargo(String nickname,
            com.gameexpert.engine.persistence.finalcarrier.structureentity
                    .WorldGeneratedStructureEntityState.RuntimeSnapshot target, Long requestId) {
        PlayerTickState player = rt.players().get(nickname);
        if (player == null || !(target instanceof com.gameexpert.engine.persistence.finalcarrier
                .structureentity.WorldGeneratedStructureEntityState.MinecartRuntimeSnapshot cart)
                || !generatedEntityWithinReach(player, cart)) return false;
        try {
            WorldRuntime.GeneratedMinecartFirstOpenResult resolved =
                    rt.resolveGeneratedChestMinecartFirstOpen(
                            cart.binding().entityId(), cart.revision());
            var current = resolved.current();
            if (current.lootStatus() != com.gameexpert.engine.persistence.finalcarrier
                    .structureentity.WorldGeneratedStructureEntityState.LootStatus.RESOLVED
                    || !generatedEntityWithinReach(player, current)) return false;
            closeActiveMenuBeforeOpen(player);
            var cargoTarget = publishGeneratedMinecartCargoOpen(player, current, requestId);
            PendingFinalScenePrerequisites pending =
                    generatedFinalScenePending(player, "H12g");
            if (pending != null) {
                rt.recordGeneratedMinecartOpen(nickname, current, cargoTarget,
                        pending.actionNonce(), pending.deadlineTick());
            }
            return true;
        } catch (RuntimeException failure) {
            log.warn("Generated Chest Minecart first-open not committed: world={}, entity={}, actor={}",
                    rt.worldId(), cart.binding().entityId(), nickname, failure);
            return false;
        }
    }

    private void applyGeneratedEntityAttack(PlayerAction.GeneratedEntityAttack attack) {
        dispatchGeneratedEntityAttack(rt.generatedStructureEntities(), attack,
                this::applyGeneratedEntityAttackLeaf);
    }

    /** One whole-stack transfer committed atomically with the generated cargo revision. */
    private void applyGeneratedEntityCargoTransfer(
            PlayerAction.GeneratedEntityCargoTransfer transfer) {
        PlayerTickState player = rt.players().get(transfer.nickname());
        if (player == null || !player.ownsGeneratedEntityCargoSession(
                transfer.entityId(), transfer.sessionId())) return;
        var session = player.openGeneratedEntityCargo();
        var target = resolveGeneratedActionTarget(rt.generatedStructureEntities(),
                com.gameexpert.engine.persistence.finalcarrier.structureentity
                        .GeneratedStructureEntityFacts.Kind.CHEST_MINECART,
                transfer.entityId());
        if (!(target instanceof com.gameexpert.engine.persistence.finalcarrier.structureentity
                .WorldGeneratedStructureEntityState.MinecartRuntimeSnapshot minecart)
                || minecart.revision() != session.sourceRevision()
                || !generatedEntityWithinReach(player, minecart)) {
            closeGeneratedMinecartCargoSession(player);
            return;
        }
        PlayerInventory.CompletePersistenceSnapshot source =
                player.inventory().acquireSettlementLease();
        if (source == null || source.revision() == Long.MAX_VALUE
                || minecart.revision() == Long.MAX_VALUE) {
            if (source != null) player.inventory().releaseSettlementLease(source);
            return;
        }
        PlayerInventory plannedInventory = source.detachedInventory();
        ChestInventory plannedCargo = generatedMinecartCargoInventory(minecart);
        ChestAccess cargo = new ChestAccess(null, plannedCargo, null, null);
        if (moveLegacyChestStack(plannedInventory, cargo,
                transfer.fromCargo(), transfer.slot()) <= 0) {
            player.inventory().releaseSettlementLease(source);
            publishGeneratedMinecartCargoUpdate(player, minecart);
            return;
        }
        PlayerInventory.CompletePersistenceSnapshot committedInventory =
                plannedInventory.completePersistenceSnapshot();
        long nextRevision = Math.incrementExact(minecart.revision());
        List<com.gameexpert.chest.entity.ChestItem> committedCargo =
                generatedMinecartCargoItems(plannedCargo.snapshot());
        var intendedNext = new com.gameexpert.engine.persistence.finalcarrier.structureentity
                .WorldGeneratedStructureEntityState.MinecartRuntimeSnapshot(
                        minecart.binding(), nextRevision, minecart.lifecycle(),
                        minecart.transform(), minecart.lootTable(), minecart.lootSeed(),
                        minecart.provenance(), minecart.lootStatus(),
                        minecart.lootDefinitionFingerprint(), minecart.lootResultFingerprint(),
                        minecart.lootResolution(), committedCargo);
        var targetMutation = new InventoryMutationTarget.GeneratedChestMinecartCargo(
                minecart.binding().entityId(), nextRevision, committedCargo);
        var command = new PlayerContainerSettlementCommand(
                WorldRuntime.stablePlayerContainerSettlementId(
                        player.playerId(), source.revision()),
                source.revision(), rt.playerInventoryMutationSnapshot(
                        player, committedInventory), targetMutation,
                List.of(minecart.revision()));
        Runnable rejected = () -> {
            player.inventory().releaseSettlementLease(source);
            if (rt.players().get(player.nickname()) == player
                    && player.ownsGeneratedEntityCargoSession(
                            transfer.entityId(), transfer.sessionId())) {
                var latest = rt.generatedStructureEntities().snapshotByEntityId(
                        transfer.entityId());
                if (latest instanceof com.gameexpert.engine.persistence.finalcarrier
                        .structureentity.WorldGeneratedStructureEntityState
                        .MinecartRuntimeSnapshot cart
                        && player.openGeneratedEntityCargo().sourceRevision()
                                == cart.revision()) {
                    publishGeneratedMinecartCargoUpdate(player, cart);
                } else {
                    closeGeneratedMinecartCargoSession(player);
                }
            }
        };
        rt.submitPlayerContainerSettlement(command, () -> {
            if (rt.players().get(player.nickname()) != player
                    || !player.ownsGeneratedEntityCargoSession(
                            transfer.entityId(), transfer.sessionId())
                    || player.openGeneratedEntityCargo().sourceRevision()
                            != minecart.revision()
                    || !rt.installCommittedGeneratedMinecartCargo(minecart, intendedNext)) {
                player.inventory().releaseSettlementLease(source);
                closeGeneratedMinecartCargoSession(player);
                return;
            }
            if (!player.inventory().installCommittedSettlement(
                    source, committedInventory)) {
                throw new IllegalStateException(
                        "committed generated cargo player settlement lost its live lease");
            }
            player.advanceGeneratedEntityCargoSession(transfer.entityId(), transfer.sessionId(),
                    minecart.revision(), nextRevision);
            PlayerTickState.GeneratedEntityCargoSession committedSession =
                    player.openGeneratedEntityCargo();
            var cargoTarget = generatedCargoTarget(committedSession);
            PendingFinalScenePrerequisites pending =
                    generatedFinalScenePending(player, "H12g");
            if (pending != null) {
                rt.recordGeneratedMinecartMutation(player.nickname(), intendedNext, cargoTarget,
                        generatedMinecartCargoSlots(intendedNext), generatedCargoCursor(player),
                        pending.actionNonce(), pending.deadlineTick());
            }
            sendTo(player, inventoryMessage(player));
            publishGeneratedMinecartCargoUpdate(player, intendedNext);
        }, rejected);
    }

    private static ChestInventory generatedMinecartCargoInventory(
            com.gameexpert.engine.persistence.finalcarrier.structureentity
                    .WorldGeneratedStructureEntityState.MinecartRuntimeSnapshot minecart) {
        ChestInventory inventory = new ChestInventory(27);
        for (com.gameexpert.chest.entity.ChestItem item : minecart.cargo()) {
            inventory.restoreSlot(item.getSlot(), item.getItemType(), item.getItemCount(),
                    item.getDurability(), item.getEnchantments(), item.getMapId(),
                    item.getShulkerId(), item.getBucketMobData(), item.getItemComponentData());
        }
        inventory.restorePersistenceRevision(minecart.revision());
        return inventory;
    }

    private static List<com.gameexpert.chest.entity.ChestItem> generatedMinecartCargoItems(
            ChestInventory.Snapshot snapshot) {
        short[] types = snapshot.itemTypes();
        int[] counts = snapshot.counts();
        int[] durabilities = snapshot.durabilities();
        long[] enchantments = snapshot.enchantments();
        int[] mapIds = snapshot.mapIds();
        int[] shulkerIds = snapshot.shulkerIds();
        String[] buckets = snapshot.bucketMobData();
        String[] components = snapshot.itemComponentData();
        List<com.gameexpert.chest.entity.ChestItem> items = new ArrayList<>();
        for (int slot = 0; slot < types.length; slot++) {
            if (types[slot] == PlayerInventory.EMPTY || counts[slot] <= 0) continue;
            items.add(new com.gameexpert.chest.entity.ChestItem(slot, types[slot], counts[slot],
                    PlayerInventory.isDurable(types[slot]) ? durabilities[slot] : null,
                    enchantments[slot] == 0L ? null : enchantments[slot],
                    mapIds[slot] == 0 ? null : mapIds[slot],
                    shulkerIds[slot] == 0 ? null : shulkerIds[slot], buckets[slot],
                    components[slot]));
        }
        return List.copyOf(items);
    }

    private void applyCloseGeneratedEntityCargo(PlayerAction.CloseGeneratedEntityCargo close) {
        PlayerTickState player = rt.players().get(close.nickname());
        if (player == null || !player.ownsGeneratedEntityCargoSession(
                close.entityId(), close.sessionId())) return;
        closeGeneratedMinecartCargoSession(player);
    }

    private void applyGeneratedEntityEquipmentSettlement(
            PlayerAction.GeneratedEntityEquipmentSettlement settlement) {
        var target = resolveGeneratedActionTarget(rt.generatedStructureEntities(),
                com.gameexpert.engine.persistence.finalcarrier.structureentity
                        .GeneratedStructureEntityFacts.Kind.ARMOR_STAND,
                settlement.entityId());
        if (target != null) applyGeneratedEntityEquipmentSettlement(settlement, target);
    }

    /** Commit both durable authorities before installing either live result. */
    private boolean applyGeneratedEntityEquipmentSettlement(
            PlayerAction.GeneratedEntityEquipmentSettlement settlement,
            com.gameexpert.engine.persistence.finalcarrier.structureentity
                    .WorldGeneratedStructureEntityState.RuntimeSnapshot target) {
        PlayerTickState player = rt.players().get(settlement.nickname());
        if (player == null || !(target instanceof com.gameexpert.engine.persistence.finalcarrier
                .structureentity.WorldGeneratedStructureEntityState.ArmorStandRuntimeSnapshot armor)
                || !generatedEntityWithinReach(player, armor)
                || player.generatedEntityEquipmentSettlement() != null) return false;
        var claim = player.beginGeneratedEntityEquipmentSettlement(armor.binding().entityId(),
                armor.revision(), player.inventory().revision(), settlement.hand());
        try {
            if (!player.ownsGeneratedEntityEquipmentSettlement(claim.settlementId(),
                    armor.binding().entityId(), armor.revision(), player.inventory().revision())) {
                return false;
            }
            WorldRuntime.GeneratedArmorStandEquipmentResult result =
                    rt.settleGeneratedArmorStandEquipment(settlement.nickname(),
                            armor.binding().entityId(), armor.revision(),
                            player.inventory().revision(), settlement.hand());
            if (!result.committed()
                    || result.current() == null
                    || result.current().revision() != armor.revision() + 1L
                    || result.inventoryRevision() != claim.expectedPlayerRevision() + 1L
                    || !player.completeGeneratedEntityEquipmentSettlement(claim.settlementId(),
                            armor.binding().entityId(), armor.revision(),
                            claim.expectedPlayerRevision())) {
                return false;
            }
            PendingFinalScenePrerequisites pending =
                    generatedFinalScenePending(player, "H12f");
            if (pending != null) {
                rt.recordGeneratedArmorStandMutation(settlement.nickname(), armor,
                        result.current(), pending.actionNonce(), pending.deadlineTick());
            }
            sendTo(player, inventoryMessage(player));
            return true;
        } finally {
            if (player.generatedEntityEquipmentSettlement() != null) {
                player.cancelGeneratedEntityEquipmentSettlement();
            }
        }
    }

    private boolean applyGeneratedEntityAttackLeaf(PlayerAction.GeneratedEntityAttack attack,
            com.gameexpert.engine.persistence.finalcarrier.structureentity
                    .WorldGeneratedStructureEntityState.RuntimeSnapshot target) {
        return switch (attack.kind()) {
            case CUSHION -> submitGeneratedCushionAction(target,
                    GeneratedCushionActionPolicy.Action.BREAK, attack.nickname());
            case ARMOR_STAND -> submitGeneratedArmorStandAttack(target, attack.nickname());
            case CHEST_MINECART -> rejectGeneratedActionWithoutCoordinator(target,
                    attack.sprinting() ? "SPRINT_ATTACK" : "ATTACK",
                    "CARGO_GROUND_SETTLEMENT_UNAVAILABLE");
        };
    }

    private boolean submitGeneratedArmorStandAttack(
            com.gameexpert.engine.persistence.finalcarrier.structureentity
                    .WorldGeneratedStructureEntityState.RuntimeSnapshot target,
            String actor) {
        WorldRuntime.GeneratedEntityActionResult result =
                rt.submitGeneratedArmorStandAttackResult(target, actor);
        if (!result.accepted()) {
            log.warn("Generated Armor Stand attack not committed: world={}, entity={}, actor={}, retryable={}, failures={}",
                    rt.worldId(), result.entityId(), actor, result.retryable(),
                    result.failureCodes());
        }
        if (result.accepted()) {
            var committed = rt.generatedStructureEntities().snapshotByEntityId(result.entityId());
            PlayerTickState player = rt.players().get(actor);
            PendingFinalScenePrerequisites pending =
                    generatedFinalScenePending(player, "H12f");
            if (committed != null && pending != null) {
                rt.recordGeneratedArmorStandTerminal(actor, committed,
                        pending.actionNonce(), pending.deadlineTick());
            }
        }
        return result.accepted();
    }

    private boolean rejectGeneratedActionWithoutCoordinator(
            com.gameexpert.engine.persistence.finalcarrier.structureentity
                    .WorldGeneratedStructureEntityState.RuntimeSnapshot target,
            String action, String failureCode) {
        var transform = target.transform();
        log.warn("Generated entity action not committed: world={}, entity={}, kind={}, action={}, x={}, y={}, z={}, failure={}",
                rt.worldId(), target.binding().entityId(), target.binding().kind(), action,
                transform.x(), transform.y(), transform.z(), failureCode);
        return false;
    }

    private boolean submitGeneratedCushionAction(
            com.gameexpert.engine.persistence.finalcarrier.structureentity
                    .WorldGeneratedStructureEntityState.RuntimeSnapshot target,
            GeneratedCushionActionPolicy.Action action, String actor) {
        WorldRuntime.GeneratedCushionActionResult result =
                rt.submitGeneratedCushionActionResult(target, action, actor);
        if (!result.accepted()) {
            if (result.retryable()) {
                log.warn("Generated Cushion action not committed: world={}, entity={}, action={}, x={}, y={}, z={}, failures={}",
                        rt.worldId(), result.entityId(), result.action(), result.x(), result.y(),
                        result.z(), result.failureCodes());
            } else {
                log.debug("Generated Cushion action rejected: world={}, entity={}, action={}, x={}, y={}, z={}, failures={}",
                        rt.worldId(), result.entityId(), result.action(), result.x(), result.y(),
                        result.z(), result.failureCodes());
            }
        }
        return result.accepted();
    }

    @FunctionalInterface
    interface GeneratedEntityInteractLeaf {
        boolean apply(PlayerAction.GeneratedEntityInteract action,
                com.gameexpert.engine.persistence.finalcarrier.structureentity
                        .WorldGeneratedStructureEntityState.RuntimeSnapshot target);
    }

    @FunctionalInterface
    interface GeneratedEntityAttackLeaf {
        boolean apply(PlayerAction.GeneratedEntityAttack action,
                com.gameexpert.engine.persistence.finalcarrier.structureentity
                        .WorldGeneratedStructureEntityState.RuntimeSnapshot target);
    }

    static boolean dispatchGeneratedEntityInteract(GeneratedStructureEntitySystem system,
            PlayerAction.GeneratedEntityInteract action, GeneratedEntityInteractLeaf leaf) {
        var target = resolveGeneratedActionTarget(system, action.kind(), action.entityId());
        if (target == null) return false;
        return leaf.apply(action, target);
    }

    static boolean dispatchGeneratedEntityAttack(GeneratedStructureEntitySystem system,
            PlayerAction.GeneratedEntityAttack action, GeneratedEntityAttackLeaf leaf) {
        var target = resolveGeneratedActionTarget(system, action.kind(), action.entityId());
        if (target == null) return false;
        return leaf.apply(action, target);
    }

    /**
     * The sole generated-action resolution boundary. Every non-live status is an inert rejection;
     * only the exact kind+ID LIVE snapshot can proceed to a per-kind leaf.
     */
    static com.gameexpert.engine.persistence.finalcarrier.structureentity
            .WorldGeneratedStructureEntityState.RuntimeSnapshot resolveGeneratedActionTarget(
                    GeneratedStructureEntitySystem system,
                    com.gameexpert.engine.persistence.finalcarrier.structureentity
                            .GeneratedStructureEntityFacts.Kind kind,
                    long entityId) {
        GeneratedStructureEntitySystem.LiveResolution resolved = system.resolveLive(kind, entityId);
        return switch (resolved.status()) {
            case UNKNOWN, KIND_MISMATCH, DEAD -> null;
            case LIVE -> {
                var snapshot = resolved.snapshot();
                if (snapshot == null || snapshot.binding().kind() != kind
                        || snapshot.binding().entityId() != entityId) {
                    throw new IllegalStateException("generated action target identity drift");
                }
                yield snapshot;
            }
        };
    }

    private void applyMove(PlayerAction.Move move, long tickNo) {
        PlayerTickState player = rt.players().get(move.nickname());
        if (player == null || player.isDead()) {
            return; // 사망 상태는 입력 무시
        }
        // 정상 최대 속도를 크게 웃도는 pose 는 서버 권위 위치로 확정하지 않는다. 무시만 하므로
        // 정상 플레이어는 쌓인 예산으로 곧 다시 승인되고, 조작 클라는 리치·청크 스트리밍·몹 인지가
        // 모두 마지막 승인 위치 기준으로 남는다.
        if (!movementLimits.accept(move.nickname(), tickNo,
                player.x(), player.y(), player.z(), move.x(), move.y(), move.z())) {
            return;
        }
        FinalSceneLandingActionLedger finalSceneLedger = move.finalSceneActionId() == null
                ? null : finalSceneLandingActionLedgers.computeIfAbsent(
                        move.nickname(), ignored -> new FinalSceneLandingActionLedger());
        boolean freshFinalSceneActionId = finalSceneLedger != null
                && finalSceneLedger.available(move.finalSceneActionId())
                && !pendingOneShotFeedback.containsKey(new OneShotFeedbackKey(
                        move.nickname(), "final-scene-flight", move.finalSceneActionId()));
        boolean finalSceneFeedbackRetained = false;
        boolean airborneBefore = player.airborne();
        boolean glidingBefore = player.gliding();
        // 이동 exhaustion(SURV-H)은 이 구간의 실제 이동 거리로 계산하므로 pose 갱신 전 좌표를 잡아 둔다.
        double previousX = player.x();
        double previousZ = player.z();
        // 활공은 겉날개가 실제로 착용되어 있고 파손 직전이 아닐 때만 권위 상태로 인정한다.
        player.applyPose(move.x(), move.y(), move.z(), move.yaw(), move.pitch(), move.crouching(),
                move.gliding() && player.inventory().elytraFlyable());
        BlockPos openChest = player.openChest();
        if (openChest != null && !InteractRules.withinContainerReach(
                player.x(), player.y(), player.z(), player.crouching(), openChest.x(), openChest.y(), openChest.z())) {
            closeChestSession(player, true);
        }
        BlockPos openFurnace = player.openFurnace();
        if (openFurnace != null && !InteractRules.withinContainerReach(
                player.x(), player.y(), player.z(), player.crouching(), openFurnace.x(), openFurnace.y(), openFurnace.z())) {
            closeFurnaceSession(player);
        }
        BlockPos openEnchanting = player.openEnchanting();
        if (openEnchanting != null && !InteractRules.withinContainerReach(
                player.x(), player.y(), player.z(), player.crouching(),
                openEnchanting.x(), openEnchanting.y(), openEnchanting.z())) {
            closeEnchantingSession(player);
        }
        BlockPos openCraftingTable = player.openCraftingTable();
        if (openCraftingTable != null && (residentBlockType(
                    rt.accessor(), openCraftingTable.x(), openCraftingTable.y(), openCraftingTable.z())
                        != craftingStationBlock(player.openCraftingStation())
                || !InteractRules.withinContainerReach(player.x(), player.y(), player.z(), player.crouching(),
                        openCraftingTable.x(), openCraftingTable.y(), openCraftingTable.z()))) {
            closeCraftingSession(player, true);
        }
        int bx = (int) Math.floor(move.x());
        int by = (int) Math.floor(move.y() - 0.02);
        int bz = (int) Math.floor(move.z());
        int stoodOn = residentBlockType(rt.accessor(), bx, by, bz);
        int steppedBlock = P1Rules.blockAfterStep(stoodOn);
        if (steppedBlock != stoodOn) {
            rt.fluidSim().applyChange(bx, by, bz, steppedBlock);
            stoodOn = steppedBlock;
        }
        int oldState = rt.blockStates().get(bx, by, bz, stoodOn);
        int steppedState = P3Rules.dripleafStateAfterStep(stoodOn, oldState);
        if (steppedState != oldState) {
            rt.setBlockState(bx, by, bz, stoodOn, steppedState);
            rt.tickBlockChanges().put(new BlockPos(bx, by, bz), (short) stoodOn);
            int delay = P3Rules.dripleafDelayTicks(stoodOn, steppedState);
            if (delay > 0) p3DelayedTransitions.put(new BlockPos(bx, by, bz), tickNo + delay);
        }
        // 포털 체류는 이동 패킷이 아니라 processDimensionPortals의 권위 틱이 센다.
        // 수면 중 이동 액션 수신 시 수면 취소 + 현황 재방송(S2a).
        // [FURNITURE-26.3] "수면 도중 침대를 떠나면" 이 경로다 — endSleep 이 건초 침대를 소모한다.
        if (endSleep(player)) {
            broadcastSleepStatus();
        }
        int playerChunkX = Math.floorDiv((int) Math.floor(player.x()), Blocks.CHUNK_X);
        int playerChunkZ = Math.floorDiv((int) Math.floor(player.z()), Blocks.CHUNK_Z);
        // 이동 종류나 지형 상주 여부와 무관한 단일 저율 exhaustion. 허기 계산이 청크 적재 상태에
        // 따라 달라지지 않도록 낙하·환경 판정 바깥에서 누적한다.
        rt.environment().accrueMovementExhaustion(player, previousX, previousZ);
        player.observeHorizontalStep(Math.hypot(player.x() - previousX, player.z() - previousZ), tickNo);
        if (hasResidentHalo(rt.accessor(), playerChunkX, playerChunkZ)) {
            // [CROP-BERRY] 달콤한 열매 덤불은 "겹쳤을 때"가 아니라 "겹친 채 **움직였을** 때"만
            // 아프므로 이동 구간을 아는 이 자리에서 판정한다(환경 틱은 위치 하나만 안다).
            // 상주 헤일로 안에서만 보는 것은 다른 접촉 피해와 같은 규율이다 — 비상주 청크의
            // 0 으로 읽힌 블록이 피해를 만들거나 없애면 안 된다.
            rt.environment().applySweetBerryBushContact(player, previousX, previousZ);
            // [ENCHANT-WIDE] 차가운 걸음: 블록 위치가 바뀔 때 발밑 원판의 수원을 살얼음으로 바꾼다.
            applyFrostWalker(player, tickNo);
            // 같은 틱에 착지 pose와 다음 점프 pose가 함께 와도 중간 착지를 잃지 않는다.
            rt.environment().observeFallPose(player);
            EnvironmentSystem.LandingObservation landingObservation =
                    rt.environment().consumeLandingObservation();
            boolean exactSafeGlideLanding = move.finalSceneActionId() != null
                    && freshFinalSceneActionId && airborneBefore && glidingBefore && !player.gliding()
                    && landingObservation != null && landingObservation.fallDamage() == 0
                    && landingObservation.healthBefore() == landingObservation.healthAfter();
            if (exactSafeGlideLanding) {
                String actionId = move.finalSceneActionId();
                finalSceneFeedbackRetained = true;
                sendOneShotFeedback(player, "final-scene-flight", actionId,
                        com.gameexpert.ws.dto.WsMessages.FinalSceneFlightOutcome.safeLanding(
                                actionId, landingObservation.healthBefore(),
                                landingObservation.healthAfter()),
                        () -> finalSceneLedger.burn(actionId));
            }
        } else {
            // 같은 배치의 다음 pose가 다시 상주 지형에 닿기 전에 이전 낙하 구간을 폐기한다.
            player.resetFallTracking(player.y());
        }
        if (freshFinalSceneActionId && !finalSceneFeedbackRetained) {
            // A nonce attached to a non-qualifying pose is still a consumed semantic attempt.
            // Only outbound backpressure on an actual receipt defers burning until delivery.
            finalSceneLedger.burn(move.finalSceneActionId());
        }
        rt.authorityEvidencePlayerMoved(move.nickname(), playerChunkX, playerChunkZ);
    }

    /**
     * [ENCHANT-WIDE] 바닐라 {@code frost_walker.json} 의 {@code location_changed → replace_disk}. 블록 위치가
     * 바뀐 틱에, 땅 위이고 탈것에 타지 않았으면 발밑 한 칸 아래 층의 반지름 {@code L+2} 원판에서 위가 공기인
     * 물 수원 칸을(엔티티와 겹치지 않을 때) 나이 0 살얼음으로 바꾸고 {@code nextInt(60, 120)} MC 틱 뒤로
     * 예약한다.
     */
    private void applyFrostWalker(PlayerTickState player, long tickNo) {
        BlockPos cell = new BlockPos((int) Math.floor(player.x()), (int) Math.floor(player.y()),
                (int) Math.floor(player.z()));
        BlockPos previous = frostWalkerLastCell.put(player.nickname(), cell);
        if (cell.equals(previous) || player.isDead() || player.airborne()) return;
        applySoulSpeedWear(player);
        int level = player.inventory().equippedWideEnchantments(
                com.gameexpert.engine.inventory.ArmorSlot.BOOTS).level(EnchantmentRules.FROST_WALKER);
        if (level <= 0) return;
        if (rt.boatSystem().isRiding(player.nickname())
                || rt.cushionSystem().isRiding(player.nickname())
                || rt.mobSystem().isSeated(player.nickname())) {
            return;
        }
        int radius = EnchantmentRules.frostWalkerRadius(level);
        int y = cell.y() - 1;
        for (int bx = cell.x() - radius; bx <= cell.x() + radius; bx++) {
            for (int bz = cell.z() - radius; bz <= cell.z() + radius; bz++) {
                if (!EnchantmentRules.frostWalkerDiskContains(radius, player.x(), player.z(), bx, bz)) {
                    continue;
                }
                if (residentBlockType(rt.accessor(), bx, y + 1, bz) != Blocks.AIR) continue;
                if (residentBlockType(rt.accessor(), bx, y, bz) != Fluids.WATER_SOURCE) continue;
                if (frostWalkerObstructed(bx, y, bz)) continue;
                rt.fluidSim().applyChange(bx, y, bz, Blocks.FROSTED_ICE);
                scheduleFrostedIce(new BlockPos(bx, y, bz), tickNo,
                        EnchantmentRules.FROSTED_ICE_PLACE_DELAY_MIN,
                        EnchantmentRules.FROSTED_ICE_PLACE_DELAY_MAX);
            }
        }
    }

    /**
     * [ENCHANT-WIDE] 영혼 가속 장화 마모(바닐라 {@code soul_speed.json} 두 번째 location_changed 효과): 블록 위치가
     * 바뀐 틱에 땅 위이고 이동에 영향을 주는 블록(발 아래 0.5 칸)이 영혼 모래·흙이면 {@code 0.04·L} 확률로
     * 장화 내구 1(내구성 적용). 이동 속도 효과는 이동 권위인 클라가 적용한다.
     */
    private void applySoulSpeedWear(PlayerTickState player) {
        int level = player.inventory().equippedWideEnchantments(
                com.gameexpert.engine.inventory.ArmorSlot.BOOTS).level(EnchantmentRules.SOUL_SPEED);
        if (level <= 0) return;
        int below = residentBlockType(rt.accessor(), (int) Math.floor(player.x()),
                (int) Math.floor(player.y() - 0.5000001), (int) Math.floor(player.z()));
        if (below != Blocks.SOUL_SAND && below != Blocks.SOUL_SOIL) return;
        if (!EnchantmentRules.soulSpeedWears(level, frostedIceRandom.nextFloat())) return;
        short broken = player.inventory().damageEquippedPiece(
                com.gameexpert.engine.inventory.ArmorSlot.BOOTS, 1);
        if (broken != PlayerInventory.EMPTY) {
            sendTo(player, new SoundEvent("item_break", broken));
        }
        sendTo(player, inventoryMessage(player));
    }

    /** 바닐라 {@code isUnobstructed}: 살아 있는 플레이어·몹의 AABB 가 이 칸과 겹치면 막혔다. */
    private boolean frostWalkerObstructed(int bx, int by, int bz) {
        for (PlayerTickState other : rt.players().values()) {
            if (other.isDead()) continue;
            double half = 0.3; // 플레이어 AABB 폭 0.6
            if (other.x() + half > bx && other.x() - half < bx + 1
                    && other.z() + half > bz && other.z() - half < bz + 1
                    && other.y() + com.gameexpert.engine.mob.ProjectileSim.PLAYER_HEIGHT > by && other.y() < by + 1) {
                return true;
            }
        }
        return rt.mobSystem().anyMobIntersects(bx, by, bz, bx + 1, by + 1, bz + 1);
    }

    /** {@code Mth.nextInt(min, max)} MC 틱 뒤(권위 틱 올림)로 살얼음 틱을 예약한다. */
    private void scheduleFrostedIce(BlockPos pos, long tickNo, int minMcTicks, int maxMcTicks) {
        int mcTicks = minMcTicks + frostedIceRandom.nextInt(maxMcTicks - minMcTicks + 1);
        frostedIceTicks.put(pos, tickNo + Math.max(1, (mcTicks + 1) / 2));
    }

    /** [ENCHANT-WIDE] 만기된 살얼음 예약 틱을 돈다(바닐라 {@code FrostedIceBlock.tick}). */
    private void processFrostedIce(long tickNo) {
        if (frostedIceTicks.isEmpty()) return;
        List<BlockPos> due = new ArrayList<>();
        var iterator = frostedIceTicks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            if (entry.getValue() > tickNo) continue;
            iterator.remove();
            due.add(entry.getKey());
        }
        for (BlockPos pos : due) frostedIceTick(pos, tickNo);
    }

    private void frostedIceTick(BlockPos pos, long tickNo) {
        if (!rt.accessor().isChunkResident(Math.floorDiv(pos.x(), Blocks.CHUNK_X),
                Math.floorDiv(pos.z(), Blocks.CHUNK_Z))) {
            // 바닐라 예약 틱은 청크가 적재될 때까지 기다린다 — 비상주면 다음 재시도 간격으로 미룬다.
            scheduleFrostedIce(pos, tickNo, EnchantmentRules.FROSTED_ICE_RETRY_DELAY_MIN,
                    EnchantmentRules.FROSTED_ICE_RETRY_DELAY_MAX);
            return;
        }
        if (residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z()) != Blocks.FROSTED_ICE) return;
        int age = rt.blockStates().get(pos.x(), pos.y(), pos.z(), Blocks.FROSTED_ICE)
                & EnchantmentRules.FROSTED_ICE_MAX_AGE;
        int roll = frostedIceRandom.nextInt(3);
        int brightness = randomTickLight.lightLevel(pos.x(), pos.y(), pos.z(), rt.worldTime());
        if (EnchantmentRules.frostedIceShouldMelt(roll, frostedIceNeighbours(pos), brightness, age,
                EnchantmentRules.FROSTED_ICE_LIGHT_DAMPENING) && slightlyMeltFrostedIce(pos)) {
            for (int[] d : FROSTED_ICE_DIRECTIONS) {
                BlockPos neighbour = new BlockPos(pos.x() + d[0], pos.y() + d[1], pos.z() + d[2]);
                if (residentBlockType(rt.accessor(), neighbour.x(), neighbour.y(), neighbour.z())
                        == Blocks.FROSTED_ICE && !slightlyMeltFrostedIce(neighbour)) {
                    scheduleFrostedIce(neighbour, tickNo, EnchantmentRules.FROSTED_ICE_RETRY_DELAY_MIN,
                            EnchantmentRules.FROSTED_ICE_RETRY_DELAY_MAX);
                }
            }
            return;
        }
        scheduleFrostedIce(pos, tickNo, EnchantmentRules.FROSTED_ICE_RETRY_DELAY_MIN,
                EnchantmentRules.FROSTED_ICE_RETRY_DELAY_MAX);
    }

    private static final int[][] FROSTED_ICE_DIRECTIONS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    private int frostedIceNeighbours(BlockPos pos) {
        int count = 0;
        for (int[] d : FROSTED_ICE_DIRECTIONS) {
            if (residentBlockType(rt.accessor(), pos.x() + d[0], pos.y() + d[1], pos.z() + d[2])
                    == Blocks.FROSTED_ICE) {
                count++;
            }
        }
        return count;
    }

    /**
     * 바닐라 {@code slightlyMelt}: 나이 3 미만이면 한 살 먹이고(이웃 갱신 없음) false, 3 이면 물 수원으로
     * 녹이고 true. 녹으면 {@code neighborChanged} 로 이웃 살얼음 중 이웃이 2개 미만인 것도 곧바로 녹는다.
     */
    private boolean slightlyMeltFrostedIce(BlockPos pos) {
        int state = rt.blockStates().get(pos.x(), pos.y(), pos.z(), Blocks.FROSTED_ICE);
        int age = state & EnchantmentRules.FROSTED_ICE_MAX_AGE;
        if (age < EnchantmentRules.FROSTED_ICE_MAX_AGE) {
            rt.setBlockState(pos.x(), pos.y(), pos.z(), Blocks.FROSTED_ICE, age + 1);
            rt.tickBlockChanges().put(pos, (short) Blocks.FROSTED_ICE);
            return false;
        }
        meltFrostedIce(pos);
        return true;
    }

    private void meltFrostedIce(BlockPos origin) {
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(origin);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z()) != Blocks.FROSTED_ICE) continue;
            rt.fluidSim().applyChange(pos.x(), pos.y(), pos.z(), Fluids.WATER_SOURCE);
            frostedIceTicks.remove(pos);
            for (int[] d : FROSTED_ICE_DIRECTIONS) {
                BlockPos neighbour = new BlockPos(pos.x() + d[0], pos.y() + d[1], pos.z() + d[2]);
                if (residentBlockType(rt.accessor(), neighbour.x(), neighbour.y(), neighbour.z())
                        == Blocks.FROSTED_ICE
                        && frostedIceNeighbours(neighbour) < EnchantmentRules.FROSTED_ICE_NEIGHBORS_TO_MELT) {
                    queue.add(neighbour);
                }
            }
        }
    }

    private void processP3DelayedTransitions(long tickNo) {
        Map<BlockPos, Long> rescheduled = new LinkedHashMap<>();
        var iterator = p3DelayedTransitions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            if (entry.getValue() > tickNo) continue;
            iterator.remove();
            BlockPos pos = entry.getKey();
            int block = residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z());
            int state = rt.blockStates().get(pos.x(), pos.y(), pos.z(), block);
            int next = P3Rules.dripleafStateAfterDelay(block, state);
            if (next == state) continue;
            rt.setBlockState(pos.x(), pos.y(), pos.z(), block, next);
            rt.tickBlockChanges().put(pos, (short) block);
            int delay = P3Rules.dripleafDelayTicks(block, next);
            if (delay > 0) rescheduled.put(pos, tickNo + delay);
        }
        p3DelayedTransitions.putAll(rescheduled);
    }



    private void setPoplarControlState(BlockPos pos, int blockType, int state) {
        if (rt.blockStates().get(pos.x(), pos.y(), pos.z(), blockType) == state) return;
        rt.setBlockState(pos.x(), pos.y(), pos.z(), blockType, state);
        rt.tickBlockChanges().put(pos, (short) blockType);
        refreshShelfPowerAround(pos.x(), pos.y(), pos.z());
        // [CONTAINER-MENUS] ButtonBlock#press / checkPressed: playSound(BlockSetType click on/off,
        // BLOCKS, 1, 1) at the button.
        if (PoplarControlRules.isButton(blockType)) {
            broadcastWorldSound(PoplarControlRules.powered(state) ? "button_click_on" : "button_click_off",
                    pos.x(), pos.y(), pos.z(), (short) blockType);
        }
    }

    /**
     * [CONTAINER-MENUS] {@code Level.updateNeighborsAt} for every cell changed this tick. The
     * repository has no general neighbour-update graph; the signal readers that must react are
     * the shelf ({@code ShelfBlock#neighborChanged} -> {@code updatePowered}) and the dispenser
     * family ({@code DispenserBlock#neighborChanged}). A removed, exploded or replaced button or
     * pressure plate therefore unpowers its shelves exactly as a released one does.
     */
    void processSignalNeighbourUpdates() {
        java.util.Set<BlockPos> replacedPlates = rt.drainReplacedPressurePlates();
        if (rt.tickBlockChanges().isEmpty() && replacedPlates.isEmpty() && redstoneConsumers.isEmpty()) return;
        List<BlockPos> changed = new ArrayList<>(rt.tickBlockChanges().keySet());
        for (BlockPos plate : replacedPlates) if (!changed.contains(plate)) changed.add(plate);
        // [CONTAINER-MENUS] The exact neighborChanged set: Level.updateNeighborsAt(pos) reaches
        // the six neighbours of every changed cell, and BasePressurePlateBlock#updateNeighbours
        // (a plate there now, or one just replaced) also those of the cell below it.
        java.util.LinkedHashSet<BlockPos> notified = new java.util.LinkedHashSet<>();
        for (BlockPos pos : changed) {
            refreshShelfPowerAround(pos.x(), pos.y(), pos.z());
            addSignalNeighbours(notified, pos.x(), pos.y(), pos.z());
            if (replacedPlates.contains(pos) || residentBlockType(rt.accessor(),
                    pos.x(), pos.y(), pos.z()) == Blocks.POPLAR_PRESSURE_PLATE) {
                addSignalNeighbours(notified, pos.x(), pos.y() - 1, pos.z());
            }
        }
        notified.addAll(redstoneConsumers);
        redstoneConsumers.clear();
        for (BlockPos cell : notified) {
            refreshShelfPowerAround(cell.x(), cell.y() + 1, cell.z());
            rt.dispenserSystem().evaluate(cell.x(), cell.y(), cell.z());
            rt.crafterSystem().evaluate(cell.x(), cell.y(), cell.z());
        }
        // CrafterBlock#getStateForPlacement reads the signal of a freshly placed crafter into
        // TRIGGERED and setPlacedBy then schedules the craft (javap); the changed cell itself is
        // not one of its own neighbours, so it reads here.
        for (BlockPos pos : changed) {
            if (!notified.contains(pos)) rt.crafterSystem().evaluate(pos.x(), pos.y(), pos.z());
        }
    }

    /** The six face neighbours {@code Level.updateNeighborsAt} notifies (never the cell itself). */
    private static final int[][] SIGNAL_NEIGHBOURS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private static void addSignalNeighbours(java.util.Set<BlockPos> into, int x, int y, int z) {
        for (int[] d : SIGNAL_NEIGHBOURS) into.add(new BlockPos(x + d[0], y + d[1], z + d[2]));
    }

    private void refreshShelfPowerAround(int x, int y, int z) {
        for (int[] d : FIRE_NEIGHBORS) {
            int sx = x + d[0], sy = y + d[1], sz = z + d[2];
            int shelf = residentBlockType(rt.accessor(), sx, sy, sz);
            if (!Blocks.isShelf(shelf)) continue;
            int old = rt.blockStates().get(sx, sy, sz, shelf);
            int next = shelfHasNeighborSignal(sx, sy, sz)
                    ? old | com.gameexpert.engine.shelf.ShelfRules.POWERED
                    : old & ~(com.gameexpert.engine.shelf.ShelfRules.POWERED
                            | com.gameexpert.engine.shelf.ShelfRules.CHAIN_MASK);
            if (next != old) {
                rt.setBlockState(sx, sy, sz, shelf, next);
                rt.tickBlockChanges().put(new BlockPos(sx, sy, sz), (short) shelf);
            }
            refreshShelfChain(sx, sy, sz);
        }
    }

    private boolean shelfHasNeighborSignal(int x, int y, int z) {
        return redstone.engine().hasNeighborSignal(x, y, z);
    }

    private void refreshShelfChain(int x, int y, int z) {
        int block = residentBlockType(rt.accessor(), x, y, z);
        if (!Blocks.isShelf(block)) return;
        int state = rt.blockStates().get(x, y, z, block);
        if (!com.gameexpert.engine.shelf.ShelfRules.powered(state)) return;
        java.util.List<BlockPos> run = shelfChain(x, y, z, state);
        for (int index = 0; index < run.size(); index++) {
            BlockPos pos = run.get(index);
            int id = residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z());
            int old = rt.blockStates().get(pos.x(), pos.y(), pos.z(), id);
            int part = com.gameexpert.engine.shelf.ShelfRules.chainPart(index, run.size());
            int next = (old & ~com.gameexpert.engine.shelf.ShelfRules.CHAIN_MASK)
                    | (part << com.gameexpert.engine.shelf.ShelfRules.CHAIN_SHIFT);
            if (next != old) {
                rt.setBlockState(pos.x(), pos.y(), pos.z(), id, next);
                rt.tickBlockChanges().put(pos, (short) id);
            }
        }
    }

    void hydrateAnimalBlockSchedules(
            List<AnimalBlockTickPersistenceService.ScheduledTick> persisted) {
        scheduledAnimalBlocks.clear();
        animalSchedulesAwaitingPersistence.clear();
        var flowers = new ArrayList<RandomTickSystem.EyeblossomSchedule>();
        persistedEyeblossomSchedules.clear();
        for (var tick : persisted) {
            int expected = tick.kind() == AnimalBlockTickPersistenceService.Kind.EYEBLOSSOM_EXPECT_OPEN
                    ? Blocks.OPEN_EYEBLOSSOM
                    : tick.kind() == AnimalBlockTickPersistenceService.Kind.EYEBLOSSOM_EXPECT_CLOSED
                    ? Blocks.CLOSED_EYEBLOSSOM : -1;
            if (expected < 0) continue;
            var row = new RandomTickSystem.EyeblossomSchedule(tick.x(), tick.y(), tick.z(), expected, tick.dueTick());
            flowers.add(row);
            persistedEyeblossomSchedules.put(new BlockPos(tick.x(), tick.y(), tick.z()), row);
        }
        randomTicks.restoreEyeblossomSchedules(flowers);
        for (AnimalBlockTickPersistenceService.ScheduledTick tick : persisted) {
            if (tick.kind() != AnimalBlockTickPersistenceService.Kind.FROGSPAWN_HATCH
                    && tick.kind() != AnimalBlockTickPersistenceService.Kind.SNIFFER_EGG_HATCH) {
                continue;
            }
            scheduledAnimalBlocks.put(new BlockPos(tick.x(), tick.y(), tick.z()),
                    new ScheduledAnimalBlock(tick.kind(), tick.dueTick()));
        }
    }

    private final Map<BlockPos, RandomTickSystem.EyeblossomSchedule> persistedEyeblossomSchedules = new HashMap<>();

    private static AnimalBlockTickPersistenceService.Kind eyeblossomKind(int expected) {
        return expected == Blocks.OPEN_EYEBLOSSOM
                ? AnimalBlockTickPersistenceService.Kind.EYEBLOSSOM_EXPECT_OPEN
                : AnimalBlockTickPersistenceService.Kind.EYEBLOSSOM_EXPECT_CLOSED;
    }

    boolean flushEyeblossomSchedulesForDisposal() {
        var store = rt.animalBlockTickStore();
        if (store == null) return true;
        if (!store.flushForDisposal()) return false;
        persistEyeblossomSchedules();
        if (!store.flushForDisposal()) return false;
        return randomTicks.eyeblossomSchedules().stream().allMatch(row ->
                row.equals(persistedEyeblossomSchedules.get(new BlockPos(row.x(), row.y(), row.z()))));
    }

    private void persistEyeblossomSchedules() {
        var store = rt.animalBlockTickStore();
        if (store == null) return;
        var current = new HashMap<BlockPos, RandomTickSystem.EyeblossomSchedule>();
        for (var row : randomTicks.eyeblossomSchedules()) current.put(new BlockPos(row.x(), row.y(), row.z()), row);
        for (var entry : new ArrayList<>(persistedEyeblossomSchedules.entrySet())) {
            var next = current.get(entry.getKey());
            if (next != null && next.expected() == entry.getValue().expected()) continue;
            var old = entry.getValue();
            store.deleteAfterCommittedHatch(old.x(), old.y(), old.z(), eyeblossomKind(old.expected()));
            persistedEyeblossomSchedules.remove(entry.getKey());
        }
        for (var entry : current.entrySet()) {
            var row = entry.getValue();
            if (row.equals(persistedEyeblossomSchedules.get(entry.getKey()))) continue;
            if (store.upsert(row.x(), row.y(), row.z(), eyeblossomKind(row.expected()), row.dueMcTick())) {
                persistedEyeblossomSchedules.put(entry.getKey(), row);
            }
        }
    }

    private void scheduleAnimalBlockIfNeeded(int x, int y, int z) {
        int block = residentBlockType(rt.accessor(), x, y, z);
        AnimalBlockTickPersistenceService.Kind kind = animalScheduleKind(block);
        if (kind == null) return;
        long now = rt.clock().gameTimeMcTicks();
        int delay = randomTicks.animalBlockNextDelay(x, y, z, now);
        if (delay < 0) return;
        BlockPos pos = new BlockPos(x, y, z);
        ScheduledAnimalBlock scheduled = new ScheduledAnimalBlock(
                kind, now + delay);
        scheduledAnimalBlocks.put(pos, scheduled);
        persistAnimalSchedule(pos, scheduled);
    }

    private void cancelAnimalBlockSchedule(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        ScheduledAnimalBlock removed = scheduledAnimalBlocks.remove(pos);
        animalSchedulesAwaitingPersistence.remove(pos);
        AnimalBlockTickPersistenceService.WorldStore store = rt.animalBlockTickStore();
        if (removed != null && store != null) {
            store.deleteAfterCommittedHatch(x, y, z, removed.kind());
        }
    }

    private void processScheduledAnimalBlocks(long nowMcTick) {
        recoverPendingAnimalHatches();
        for (Runnable retry : List.copyOf(pendingCopperSettlementSubmissions.values())) retry.run();
        AnimalBlockTickPersistenceService.WorldStore store = rt.animalBlockTickStore();
        if (store != null) {
            for (BlockPos pos : List.copyOf(animalSchedulesAwaitingPersistence)) {
                ScheduledAnimalBlock scheduled = scheduledAnimalBlocks.get(pos);
                if (scheduled == null || store.upsert(
                        pos.x(), pos.y(), pos.z(), scheduled.kind(), scheduled.dueMcTick())) {
                    animalSchedulesAwaitingPersistence.remove(pos);
                }
            }
            store.flushPending();
        }
        List<IndexedAnimalBlockSchedule> unavailable = new ArrayList<>();
        List<Map.Entry<BlockPos, ScheduledAnimalBlock>> replacements = new ArrayList<>();
        int processedDue = 0;
        while (processedDue < MAX_SCHEDULED_ANIMAL_BLOCKS_PER_TICK) {
            IndexedAnimalBlockSchedule entry = scheduledAnimalBlocks.pollDue(nowMcTick);
            if (entry == null) break;
            processedDue++;
            BlockPos pos = entry.position();
            int block = residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z());
            if (block == UNAVAILABLE_BLOCK) {
                unavailable.add(entry);
                continue;
            }
            if (animalScheduleKind(block) != entry.scheduled().kind()) {
                animalSchedulesAwaitingPersistence.remove(pos);
                if (store != null) store.deleteAfterCommittedHatch(
                        pos.x(), pos.y(), pos.z(), entry.scheduled().kind());
                continue;
            }
            int changed;
            activeAnimalBlockDueTick = entry.scheduled().dueMcTick();
            try {
                changed = randomTicks.processAnimalBlockScheduledTick(pos.x(), pos.y(), pos.z());
            } finally {
                activeAnimalBlockDueTick = Long.MIN_VALUE;
            }
            int after = residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z());
            if (changed > 0 && animalScheduleKind(after) == null) {
                animalSchedulesAwaitingPersistence.remove(pos);
                if (store != null) store.deleteAfterCommittedHatch(
                        pos.x(), pos.y(), pos.z(), entry.scheduled().kind());
                continue;
            }
            String hatchFlightKey = hatchSourceKey(entry.scheduled().kind(),
                    pos.x(), pos.y(), pos.z());
            if (animalSettlementInFlight.contains(hatchFlightKey)) {
                // The persistence writer now owns this due occurrence. Do not enqueue a newer
                // schedule behind its transaction; failure completion restores this exact entry.
                continue;
            }
            int delay = changed > 0
                    ? randomTicks.animalBlockNextDelay(pos.x(), pos.y(), pos.z(), nowMcTick) : 1;
            ScheduledAnimalBlock next = new ScheduledAnimalBlock(
                    entry.scheduled().kind(), nowMcTick + Math.max(1, delay));
            replacements.add(Map.entry(pos, next));
        }
        for (IndexedAnimalBlockSchedule entry : unavailable) {
            scheduledAnimalBlocks.restore(entry);
        }
        for (Map.Entry<BlockPos, ScheduledAnimalBlock> replacement : replacements) {
            scheduledAnimalBlocks.append(replacement.getKey(), replacement.getValue());
            persistAnimalSchedule(replacement.getKey(), replacement.getValue());
        }
    }

    private boolean settleAnimalBlockHatch(MobSystem.AnimalDependencySpawnKind kind,
            int x, int y, int z, long dueMcTick) {
        var settlements = rt.animalSettlements();
        if (settlements == null) {
            throw new IllegalStateException("animal settlement persistence is not installed");
        }
        if (!rt.mobSystem().animalDependencySpawnFits(kind, x, y, z)) return false;
        AnimalBlockTickPersistenceService.Kind scheduleKind = kind
                == MobSystem.AnimalDependencySpawnKind.TADPOLE
                        ? AnimalBlockTickPersistenceService.Kind.FROGSPAWN_HATCH
                        : AnimalBlockTickPersistenceService.Kind.SNIFFER_EGG_HATCH;
        String sourceKey = hatchSourceKey(scheduleKind, x, y, z);
        if (!animalSettlementInFlight.add(sourceKey)) return false;
        hatchSchedulesOwnedBySettlement.put(sourceKey,
                new ScheduledAnimalBlock(scheduleKind, dueMcTick));
        int count = kind == MobSystem.AnimalDependencySpawnKind.TADPOLE
                ? frogspawnHatchCount(rt.seed(), x, y, z, dueMcTick) : 1;
        List<Long> proposed = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            proposed.add(rt.mobSystem().reserveAnimalDependencyMobId());
        }
        Runnable transaction = () -> {
            List<CommittedHatch> committed = new ArrayList<>(count);
            boolean allCommitted = false;
            try {
                var intents = settlements.beginHatchBatch(
                        rt.worldId(), kind.name(), x, y, z, List.copyOf(proposed));
                allCommitted = intents.size() == count;
                for (var intent : intents) {
                    MobSystem.AnimalDependencySpawnKind durableKind;
                    try {
                        durableKind = MobSystem.AnimalDependencySpawnKind.valueOf(intent.spawnKind());
                    } catch (IllegalArgumentException invalid) {
                        allCommitted = false;
                        continue;
                    }
                    var snapshot = rt.mobSystem().detachedAnimalDependencySnapshot(
                            durableKind, intent.mobId(), intent.x(), intent.y(), intent.z());
                    if (!settlements.commitHatch(rt.worldId(), intent, snapshot)) {
                        allCommitted = false;
                        continue;
                    }
                    committed.add(new CommittedHatch(intent, durableKind));
                }
            } catch (RuntimeException | Error failure) {
                allCommitted = false;
            }
            boolean durableBatch = allCommitted;
            List<CommittedHatch> durableMobs = List.copyOf(committed);
            rt.enqueuePersistenceCompletion(() -> {
                animalSettlementInFlight.remove(sourceKey);
                ScheduledAnimalBlock parked = hatchSchedulesOwnedBySettlement.remove(sourceKey);
                if (!durableBatch) {
                    restoreFailedHatchSchedule(x, y, z, parked);
                    return;
                }
                boolean installed = true;
                for (CommittedHatch hatch : durableMobs) {
                    var intent = hatch.intent();
                    if (rt.mobSystem().installCommittedAnimalDependency(hatch.kind(),
                            intent.mobId(), intent.x(), intent.y(), intent.z()) == null) {
                        installed = false;
                    }
                }
                if (!installed) {
                    restoreFailedHatchSchedule(x, y, z, parked);
                    return;
                }
                rt.fluidSim().applyChange(x, y, z, Blocks.AIR);
                for (CommittedHatch hatch : durableMobs) {
                    pendingAnimalSettlementRetirements.add(hatch.intent().key());
                }
            });
        };
        if (!submitAnimalSettlement(transaction)) {
            animalSettlementInFlight.remove(sourceKey);
            hatchSchedulesOwnedBySettlement.remove(sourceKey);
        }
        // The source block is removed only by the owner completion after the DB transaction.
        return false;
    }

    private static String hatchSourceKey(AnimalBlockTickPersistenceService.Kind kind,
            int x, int y, int z) {
        return "hatch-source:" + kind + ':' + x + ':' + y + ':' + z;
    }

    private void restoreFailedHatchSchedule(
            int x, int y, int z, ScheduledAnimalBlock scheduled) {
        if (scheduled == null) return;
        BlockPos position = new BlockPos(x, y, z);
        if (scheduledAnimalBlocks.get(position) == null) {
            scheduledAnimalBlocks.append(position, scheduled);
        }
    }

    static int frogspawnHatchCount(int worldSeed, int x, int y, int z, long dueMcTick) {
        int mixed = worldSeed * 0x1f123bb5 ^ x * 0x6d2b79f5 ^ y * 0x5bd1e995
                ^ z * 0x27d4eb2d ^ (int) dueMcTick;
        mixed ^= mixed >>> 16;
        mixed *= 0x7feb352d;
        mixed ^= mixed >>> 15;
        return 2 + (mixed & 3);
    }

    private void recoverPendingAnimalHatches() {
        var settlements = rt.animalSettlements();
        if (settlements == null) return;
        pumpAnimalSettlementRetirements(settlements);
        if (!pendingHatchesHydrated && !pendingHatchHydrationInFlight) {
            pendingHatchHydrationInFlight = true;
            if (!submitAnimalSettlement(() -> {
                List<com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService
                        .HatchIntent> pending;
                try {
                    pending = settlements.pendingHatches(rt.worldId());
                } catch (RuntimeException | Error failure) {
                    pending = null;
                }
                List<com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService
                        .HatchIntent> result = pending == null ? null : List.copyOf(pending);
                rt.enqueuePersistenceCompletion(() -> {
                    pendingHatchHydrationInFlight = false;
                    if (result == null) return;
                    pendingHatchRecovery.addAll(result);
                    pendingHatchesHydrated = true;
                });
            })) {
                pendingHatchHydrationInFlight = false;
            }
        }
        int attempts = pendingHatchRecovery.size();
        for (int attempt = 0; attempt < attempts; attempt++) {
            var intent = pendingHatchRecovery.removeFirst();
            MobSystem.AnimalDependencySpawnKind kind;
            try {
                kind = MobSystem.AnimalDependencySpawnKind.valueOf(intent.spawnKind());
            } catch (IllegalArgumentException invalid) {
                continue;
            }
            if (!rt.mobSystem().animalDependencySpawnFits(
                    kind, intent.x(), intent.y(), intent.z())) {
                pendingHatchRecovery.addLast(intent);
                continue;
            }
            if (!animalSettlementInFlight.add(intent.key())) {
                pendingHatchRecovery.addLast(intent);
                continue;
            }
            Runnable transaction = () -> {
                boolean committed = false;
                try {
                    var snapshot = rt.mobSystem().detachedAnimalDependencySnapshot(
                            kind, intent.mobId(), intent.x(), intent.y(), intent.z());
                    committed = settlements.commitHatch(rt.worldId(), intent, snapshot);
                } catch (RuntimeException | Error failure) {
                    committed = false;
                }
                boolean durable = committed;
                rt.enqueuePersistenceCompletion(() -> {
                    animalSettlementInFlight.remove(intent.key());
                    if (!durable || rt.mobSystem().installCommittedAnimalDependency(kind,
                            intent.mobId(), intent.x(), intent.y(), intent.z()) == null) {
                        pendingHatchRecovery.addLast(intent);
                        return;
                    }
                    rt.fluidSim().applyChange(intent.x(), intent.y(), intent.z(), Blocks.AIR);
                    pendingAnimalSettlementRetirements.add(intent.key());
                });
            };
            if (!submitAnimalSettlement(transaction)) {
                animalSettlementInFlight.remove(intent.key());
                pendingHatchRecovery.addLast(intent);
            }
        }
    }

    private boolean submitAnimalSettlement(Runnable transaction) {
        PersistenceExecutor writer = rt.ctx().persistenceExecutor();
        if (writer == null) {
            throw new IllegalStateException(
                    "animal settlements require the persistence writer");
        }
        return writer.trySubmit(transaction);
    }

    private void pumpAnimalSettlementRetirements(
            com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService settlements) {
        for (String key : List.copyOf(pendingAnimalSettlementRetirements)) {
            String flightKey = "retire:" + key;
            if (!animalSettlementInFlight.add(flightKey)) continue;
            Runnable transaction = () -> {
                boolean retired = false;
                try {
                    settlements.retireCompleted(rt.worldId(), key);
                    retired = true;
                } catch (RuntimeException | Error failure) {
                    retired = false;
                }
                boolean success = retired;
                rt.enqueuePersistenceCompletion(() -> {
                    animalSettlementInFlight.remove(flightKey);
                    if (success) pendingAnimalSettlementRetirements.remove(key);
                });
            };
            if (!submitAnimalSettlement(transaction)) animalSettlementInFlight.remove(flightKey);
        }
    }

    /** QA의 기존 칸만 다음 정상 전이 경계로 옮긴다. 전이·소생은 평소 틱이 소유한다. */
    private void applyQaEcologyBoundary(PlayerAction.QaSeed seed) {
        if (!seed.fixtureAuthorized() || !rt.ctx().properties().qaSeeding()
                || rt.seed() != (int) com.gameexpert.config.EngineProperties.CONTENT_QA_WORLD_SEED
                || !Double.isFinite(seed.x()) || !Double.isFinite(seed.y()) || !Double.isFinite(seed.z())
                || seed.x() != Math.rint(seed.x()) || seed.y() != Math.rint(seed.y())
                || seed.z() != Math.rint(seed.z()) || seed.x() < 0 || seed.x() > 31
                || seed.z() < 0 || seed.z() > 31 || seed.y() < 72 || seed.y() > 76) return;
        int x = (int) seed.x(), y = (int) seed.y(), z = (int) seed.z();
        if ("dried-ghast-next".equals(seed.argument())) {
            driedGhastHydration.qaStageNextBoundary(x, y, z, seed.amount());
        } else if ("sniffer-egg-next".equals(seed.argument())) {
            BlockPos pos = new BlockPos(x, y, z);
            ScheduledAnimalBlock current = scheduledAnimalBlocks.get(pos);
            long now = rt.clock().gameTimeMcTicks();
            if (seed.amount() < 0 || seed.amount() > 2
                    || residentBlockType(rt.accessor(), x, y, z) != Blocks.SNIFFER_EGG
                    || residentBlockType(rt.accessor(), x, y - 1, z) != Blocks.MOSS_BLOCK
                    || rt.blockStates().get(x, y, z, Blocks.SNIFFER_EGG) != seed.amount()
                    || current == null || current.kind() != AnimalBlockTickPersistenceService.Kind.SNIFFER_EGG_HATCH
                    || current.dueMcTick() <= now + 1 || animalSchedulesAwaitingPersistence.contains(pos)) return;
            ScheduledAnimalBlock next = new ScheduledAnimalBlock(current.kind(), now + 1);
            scheduledAnimalBlocks.put(pos, next);
            persistAnimalSchedule(pos, next);
        }
    }

    private void persistAnimalSchedule(BlockPos pos, ScheduledAnimalBlock scheduled) {
        AnimalBlockTickPersistenceService.WorldStore store = rt.animalBlockTickStore();
        if (store != null && !store.upsert(
                pos.x(), pos.y(), pos.z(), scheduled.kind(), scheduled.dueMcTick())) {
            animalSchedulesAwaitingPersistence.add(pos);
        }
    }

    private static AnimalBlockTickPersistenceService.Kind animalScheduleKind(int block) {
        return block == Blocks.FROGSPAWN
                ? AnimalBlockTickPersistenceService.Kind.FROGSPAWN_HATCH
                : block == Blocks.SNIFFER_EGG
                        ? AnimalBlockTickPersistenceService.Kind.SNIFFER_EGG_HATCH : null;
    }

    /** 컨테이너 정산 창이 소유하는 편집인지 판정한다(대상 블록 또는 설치하려는 블록 기준). */
    private static boolean isContainerClassEdit(int current, PlayerAction.BlockEdit edit) {
        return InteractRules.isContainer(current) || FurnaceRules.isFurnace(current)
                || current == Blocks.CAMPFIRE
                || InteractRules.isContainer(Short.toUnsignedInt(edit.blockType()))
                || FurnaceRules.isFurnace(Short.toUnsignedInt(edit.blockType()))
                || edit.blockType() == Blocks.CAMPFIRE;
    }

    /**
     * 정산 창이 닫히면 보류된 편집을 들어온 순서대로 {@link #applyEdit}에 다시 태운다. 리치·현재
     * 블록·사망 등 모든 검증은 지금 월드 상태로 새로 실행된다. 창이 아직 열려 있으면 큐를 그대로
     * 두고 즉시 돌아간다(회전 금지). 마감 틱을 넘긴 항목만 예전 계약대로 한 번 되돌린다.
     */
    private void replayDeferredContainerEdits(long tickNo, ActionFailureLog failures) {
        while (!deferredContainerEdits.isEmpty()) {
            DeferredContainerEdit head = deferredContainerEdits.peekFirst();
            boolean expired = tickNo >= head.deadlineTick();
            if (!expired && !deferredEditReady(head.edit())) return;
            deferredContainerEdits.pollFirst();
            try {
                if (expired) {
                    expireDeferredContainerEdit(head.edit());
                } else {
                    replayingDeferredContainerEdits = true;
                    replayingDeferredEditMainSlot = head.mainSlot();
                    try {
                        applyEdit(head.edit());
                    } finally {
                        replayingDeferredContainerEdits = false;
                        replayingDeferredEditMainSlot = -1;
                    }
                }
            } catch (Exception exception) {
                failures.record(head.edit(), exception);
            }
        }
    }

    /**
     * 보류 큐에 담는다. 재적용 중이거나 큐가 가득 찼으면 담지 않고 호출자가 예전 계약대로 되돌린다.
     */
    private boolean deferEditUntilSettlementCloses(
            PlayerTickState player, PlayerAction.BlockEdit edit) {
        if (replayingDeferredContainerEdits
                || deferredContainerEdits.size() >= MAX_DEFERRED_CONTAINER_EDITS) {
            return false;
        }
        deferredContainerEdits.addLast(new DeferredContainerEdit(
                edit, rt.tickNo() + DEFERRED_CONTAINER_EDIT_TICKS,
                player.inventory().selectedSlot()));
        return true;
    }

    /** 이 편집이 쓰는 주손 핫바 칸: 재적용 중이면 도착 당시의 칸, 아니면 지금 선택 칸. */
    private int editMainSlot(PlayerTickState player) {
        return replayingDeferredEditMainSlot >= 0
                ? replayingDeferredEditMainSlot : player.inventory().selectedSlot();
    }

    /**
     * 보류 편집을 지금 다시 태울 수 있는가. 월드 정산 창과 요청자 인벤토리 리스가 모두 닫혀야
     * 한다. 떠난 플레이어의 편집은 준비된 것으로 보고 {@link #applyEdit}가 조용히 버리게 한다.
     */
    private boolean deferredEditReady(PlayerAction.BlockEdit edit) {
        if (rt.playerContainerSettlementInFlight() || rt.fleshCellReserved(edit.x(), edit.y(), edit.z())
                || rt.runtimeFallingSpeleothems().protectsCell(edit.x(), edit.y(), edit.z())) return false;
        PlayerTickState player = rt.players().get(edit.nickname());
        return player == null || !player.inventory().settlementLeased();
    }

    /** 마감을 넘긴 보류 편집을 정확히 한 번 되돌린다. */
    private void expireDeferredContainerEdit(PlayerAction.BlockEdit edit) {
        PlayerTickState player = rt.players().get(edit.nickname());
        if (player == null) return;
        int current = residentBlockType(rt.accessor(), edit.x(), edit.y(), edit.z());
        // 비상주 셀은 applyEdit 와 같이 조용히 버린다. 되돌릴 오버레이 항목이 없다.
        if (current == UNAVAILABLE_BLOCK) return;
        rollbackEdit(player, edit, current);
    }

    /** 런타임 폐기 시 남은 보류 편집을 버린다. 되돌려 줄 소유자도 수신자도 이미 없다. */
    void dropDeferredContainerEdits() {
        deferredContainerEdits.clear();
    }

    /** 테스트 전용: 아직 보류 중인 편집 수. */
    int deferredContainerEditCount() {
        return deferredContainerEdits.size();
    }

    // ── ① blockEdit: 리치/범위/가능 여부 서버 검증 ──
    private void applyEdit(PlayerAction.BlockEdit edit) {
        PlayerTickState player = rt.players().get(edit.nickname());
        if (player == null) {
            return;
        }
        TerrainAccessor accessor = rt.accessor();
        int current = residentBlockType(accessor, edit.x(), edit.y(), edit.z());
        if (current == UNAVAILABLE_BLOCK) return;
        if (rt.runtimeFallingSpeleothems().protectsCell(edit.x(), edit.y(), edit.z())) {
            if (deferEditUntilSettlementCloses(player, edit)) return;
            rollbackEdit(player, edit, current);
            return;
        }
        // A canonical first-open transaction owns both ordered halves until its durable result
        // reaches the owner. Reject every player edit at the fence before consuming a stack or
        // mutating the block overlay.
        if (rt.isCanonicalLootCoordinatePending(edit.x(), edit.y(), edit.z())) {
            rollbackEdit(player, edit, current);
            return;
        }
        // A committed Copper Golem transition owns this exact source cell until its immutable
        // completion returns. Reject before inventory/tool/mining state can be consumed.
        if (copperSettlementCells.contains(edit.x(), edit.y(), edit.z())) {
            rollbackEdit(player, edit, current);
            return;
        }
        // 월드 밖 y(건축 한계 위·기반암 아래)는 저장할 셀이 없다. 요청자에게는 평소처럼 거절과
        // 인벤토리를 돌려주되, 오버레이 항목과 청크 revision은 만들지 않는다. 그러지 않으면 조작
        // 클라가 y만 바꿔 가며 요청하는 것만으로 존재하지 않는 칸의 교정 기록을 무제한으로 쌓는다.
        if (edit.y() < Blocks.MIN_Y || edit.y() > Blocks.MAX_Y) {
            sendEditRejection(player, edit, current);
            return;
        }
        if (rt.fleshCellReserved(edit.x(), edit.y(), edit.z())
                || rt.playerContainerSettlementInFlight() && isContainerClassEdit(current, edit)) {
            // 정산이 끝나기만 기다리면 되는 편집까지 즉시 거절하면, 컨테이너를 만진 직후에 놓은
            // 블록이 클라에서 사라져 보인다. 창이 닫힐 때까지 보류했다가 같은 검증을 그대로 다시
            // 태운다. 큐가 가득 찼거나 창이 마감까지 열려 있으면 예전처럼 되돌린다.
            if (deferEditUntilSettlementCloses(player, edit)) return;
            rollbackEdit(player, edit, current);
            return;
        }
        if (player.isDead()) {
            // 사망 직전 낙관 반영을 조용히 버리면 클라에 유령 구멍이 남는다.
            // 현재 블록을 재방송해 예측을 되돌린다(정적판과 동일 계약).
            rollbackEdit(player, edit, current);
            return;
        }
        if (edit.kind() == PlayerAction.EditKind.PLACE
                && Blocks.isCushion(Short.toUnsignedInt(edit.blockType()))) {
            // 26.3 cushions are decoration entities. Their append-only ids remain item identities,
            // but may never enter the resident block overlay.
            rollbackEdit(player, edit, current);
            return;
        }
        // 이 플레이어의 컨테이너 정산이 인벤토리를 리스한 동안에는 소비(설치)도 적립(파괴)도
        // 막힌다. 그 창에서 온 편집을 즉시 거절하면 컨테이너류가 아닌 블록도 클라에서 사라져
        // 보인다. 컨테이너 관문과 같은 큐·상한·마감으로 보류했다가 리스가 풀리면 다시 태운다.
        if (player.inventory().settlementLeased()
                && (edit.kind() == PlayerAction.EditKind.PLACE
                        || edit.kind() == PlayerAction.EditKind.BREAK)) {
            if (deferEditUntilSettlementCloses(player, edit)) return;
            rollbackEdit(player, edit, current);
            return;
        }
        PlayerInventory.HandRef placeHand = player.inventory().capture(
                inventoryHand(edit.hand()), editMainSlot(player));
        int requestedState = BuildingBlockRules.normalizeState(edit.blockType(), edit.state());
        if (Blocks.isDecoratedPot(Short.toUnsignedInt(edit.blockType()))) {
            requestedState = BuildingBlockRules.lookFacing(player.yaw());
        }
        // 설치가 정할 수 있는 비트만 남긴다(벌집·벌통 꿀 단계 0, 통 닫힘). 조작 클라가 꿀이 가득 찬
        // 벌통이나 열린 통을 바로 놓지 못한다.
        requestedState = BuildingBlockRules.normalizePlacementState(
                Short.toUnsignedInt(edit.blockType()), requestedState);
        requestedState = normalizeCopperPlacementState(edit.blockType(), current, requestedState);
        // EndPortalFrameBlock#getStateForPlacement: 방향만 받고 눈은 언제나 비어 있다.
        if (edit.blockType() == Blocks.END_PORTAL_FRAME) {
            requestedState &= EndPortalFrameRules.FACING_MASK;
        }
        // [VOID-END] EndRodBlock#getStateForPlacement: 같은 방향 엔드 막대에 붙이면 반대로 뒤집는다.
        if (edit.kind() == PlayerAction.EditKind.PLACE && edit.blockType() == Blocks.END_ROD) {
            requestedState = com.gameexpert.engine.blocks.VoidEndBlockRules.endRodPlacementFacing(
                    requestedState, edit.x(), edit.y(), edit.z(),
                    (qx, qy, qz) -> residentBlockType(accessor, qx, qy, qz),
                    (qx, qy, qz, blockId) -> rt.blockStates().get(qx, qy, qz, blockId));
        }
        if (Blocks.isShelf(Short.toUnsignedInt(edit.blockType()))) {
            requestedState = (requestedState & ~(com.gameexpert.engine.shelf.ShelfRules.POWERED
                    | com.gameexpert.engine.shelf.ShelfRules.CHAIN_MASK))
                    | (shelfHasNeighborSignal(edit.x(), edit.y(), edit.z())
                            ? com.gameexpert.engine.shelf.ShelfRules.POWERED : 0);
        }
        // [HOPPER] getStateForPlacement fixes FACING; onPlace -> checkPoweredState fixes ENABLED.
        if (Short.toUnsignedInt(edit.blockType()) == Blocks.HOPPER) {
            requestedState = com.gameexpert.engine.hopper.HopperRules.state(
                    com.gameexpert.engine.hopper.HopperRules.facing(requestedState),
                    !shelfHasNeighborSignal(edit.x(), edit.y(), edit.z()));
        }
        // [PITCHER] DoublePlantBlock.getStateForPlacement places the lower half; setPlacedBy adds the upper.
        if (edit.blockType() == Blocks.PITCHER_PLANT) requestedState = 0;
        if (edit.kind() == PlayerAction.EditKind.PLACE && edit.blockType() == Blocks.CAVE_VINES) {
            requestedState = randomTicks.initialCaveVinesState();
        } else if (edit.kind() == PlayerAction.EditKind.PLACE
                && edit.blockType() == Blocks.VINE) {
            requestedState = vineStateAt(edit.x(), edit.y(), edit.z());
        }

        // 같은 재질의 top/bottom slab을 서로 합칠 때는 새 셀을 차지하지 않고
        // 현재 셀을 double로 바꾸며 아이템은 하나만 소비한다.
        int currentState = rt.blockState(edit.x(), edit.y(), edit.z(), current);
        if (edit.kind() == PlayerAction.EditKind.PLACE) redstone.engine().noteExternalChange(
                edit.x(), edit.y(), edit.z(), current, currentState, true);
        if (edit.kind() == PlayerAction.EditKind.PLACE && edit.blockType() == Blocks.LEAF_LITTER) {
            // 조각 수는 요청 바이트가 아니라 현재 권위 상태와 소비할 아이템 하나로 정한다.
            if (residentBlockType(accessor, edit.x(), edit.y() - 1, edit.z()) == UNAVAILABLE_BLOCK
                    || current == Blocks.LEAF_LITTER && (player.crouching()
                            || !P26Rules.canAddSegment(current, currentState, Blocks.LEAF_LITTER))) {
                rollbackEdit(player, edit, current);
                return;
            }
            requestedState = P26Rules.placementState(current, currentState, Blocks.LEAF_LITTER,
                    Blocks.flowerBedFacing(requestedState));
            if (current == Blocks.LEAF_LITTER) {
                if (BlockEditRules.isTargetRejected(player.x(), player.y(), player.z(), player.crouching(),
                        edit.x(), edit.y(), edit.z())
                        || !BlockEditRules.isPlacementSupported(Blocks.LEAF_LITTER, requestedState,
                                edit.x(), edit.y(), edit.z(),
                                (x, y, z) -> residentBlockType(accessor, x, y, z),
                                (x, y, z, blockId) -> rt.blockStates().get(x, y, z, blockId))
                        || !consumeForPlace(player.inventory(), placeHand, (short) Blocks.LEAF_LITTER)) {
                    rollbackEdit(player, edit, current);
                    return;
                }
                rt.setBlockState(edit.x(), edit.y(), edit.z(), current, requestedState);
                rt.tickBlockChanges().put(new BlockPos(edit.x(), edit.y(), edit.z()), (short) current);
                rt.queuePlayerInventoryBaseline(player);
                sendTo(player, inventoryMessage(player));
                broadcastWorldSound("block_place", edit.x(), edit.y(), edit.z(), edit.blockType());
                return;
            }
        }
        if (edit.kind() == PlayerAction.EditKind.PLACE && CandleRules.isCandle(edit.blockType())) {
            // 한 아이템으로 보낸 임의 수량 state는 새 묶음에 반영하지 않는다.
            requestedState = 0;
            if (CandleRules.canAdd(current, currentState, edit.blockType(), player.crouching())) {
                if (BlockEditRules.isTargetRejected(player.x(), player.y(), player.z(), player.crouching(),
                        edit.x(), edit.y(), edit.z())
                        || !consumeForPlace(player.inventory(), placeHand, edit.blockType())) {
                    rollbackEdit(player, edit, current);
                    return;
                }
                rt.setBlockState(edit.x(), edit.y(), edit.z(), current,
                        CandleRules.withCount(currentState, CandleRules.count(currentState) + 1));
                rt.tickBlockChanges().put(new BlockPos(edit.x(), edit.y(), edit.z()), (short) current);
                rt.queuePlayerInventoryBaseline(player);
                sendTo(player, inventoryMessage(player));
                broadcastWorldSound("block_place", edit.x(), edit.y(), edit.z(), edit.blockType());
                return;
            }
        }
        boolean slabMerge = edit.kind() == PlayerAction.EditKind.PLACE
                && BuildingBlockRules.canMergeSlab(current, currentState,
                        Short.toUnsignedInt(edit.blockType()), requestedState);
        if (slabMerge) {
            if (BlockEditRules.isTargetRejected(player.x(), player.y(), player.z(), player.crouching(),
                    edit.x(), edit.y(), edit.z())
                    || !consumeForPlace(player.inventory(), placeHand, edit.blockType())) {
                rollbackEdit(player, edit, current);
                return;
            }
            rt.setBlockState(edit.x(), edit.y(), edit.z(), current, BuildingBlockRules.SLAB_DOUBLE);
            rt.tickBlockChanges().put(new BlockPos(edit.x(), edit.y(), edit.z()), (short) current);
            // A following container transaction uses the current inventory revision as its exact
            // DB CAS baseline. Queue this consumed placement before any later action can enqueue
            // that transaction on the same persistence writer.
            rt.queuePlayerInventoryBaseline(player);
            sendTo(player, inventoryMessage(player));
            broadcastWorldSound("block_place", edit.x(), edit.y(), edit.z(), edit.blockType());
            return;
        }
        boolean rejected = BlockEditRules.isRejected(player.x(), player.y(), player.z(), player.crouching(),
                edit.kind(), edit.x(), edit.y(), edit.z(), edit.blockType(), current);
        if (rejected) {
            rollbackEdit(player, edit, current);
            return;
        }
        // 채굴 시간은 클라가 진행률을 굴리지만, 소지한 최선의 도구로도 불가능한 속도의 파괴는
        // 서버가 받지 않는다(즉시·틱당 무제한 파괴 차단). 거절은 롤백뿐이라 정상 플레이는 재채굴로 끝난다.
        if (edit.kind() == PlayerAction.EditKind.BREAK
                && (!FleshNetherRules.canBreak(current, player.inventory().itemType(editMainSlot(player)))
                    || !miningLimits.accept(edit.nickname(), rt.tickNo(), current, player.inventory(),
                            player.statusEffects().digSpeedMultiplier()))) {
            rollbackEdit(player, edit, current);
            return;
        }

        if (edit.kind() == PlayerAction.EditKind.PLACE) {
            boolean door = Blocks.isDoor(edit.blockType());
            // [BED-COLOR] 색 침대도 총칭 침대와 같은 두 셀 원자 배치 계약을 쓴다.
            boolean bed = Blocks.isBed(edit.blockType());
            // [CHEST-FAMILY] 좌우 짝 계약은 상자 형상군 전체가 공유한다.
            boolean chest = Blocks.isChestShaped(edit.blockType());
            if (bed) requestedState &= BuildingBlockRules.FACING_MASK;
            int bedHeadX = bed ? BuildingBlockRules.bedHeadX(edit.x(), requestedState) : edit.x();
            int bedHeadZ = bed ? BuildingBlockRules.bedHeadZ(edit.z(), requestedState) : edit.z();
            if (door && (edit.y() >= Blocks.MAX_Y
                    || residentBlockType(accessor, edit.x(), edit.y() + 1, edit.z()) != AIR)) {
                rollbackEdit(player, edit, current);
                return;
            }
            // [PITCHER] DoublePlantBlock.getStateForPlacement: y below the top and a replaceable cell above.
            boolean tallPlant = edit.blockType() == Blocks.PITCHER_PLANT;
            if (tallPlant) {
                int above = edit.y() < Blocks.MAX_Y
                        ? residentBlockType(accessor, edit.x(), edit.y() + 1, edit.z()) : UNAVAILABLE_BLOCK;
                if (above == UNAVAILABLE_BLOCK || !BlockEditRules.isPlacementReplaceable(above)) {
                    rollbackEdit(player, edit, current);
                    return;
                }
            }
            if (bed) {
                int headCurrent = residentBlockType(accessor, bedHeadX, edit.y(), bedHeadZ);
                if (headCurrent == UNAVAILABLE_BLOCK
                        || !BlockEditRules.isPlacementReplaceable(headCurrent)) {
                    rollbackEdit(player, edit, current);
                    return;
                }
                if (!BlockEditRules.isPlacementSupported(edit.blockType(),
                        requestedState | BuildingBlockRules.BED_HEAD,
                        bedHeadX, edit.y(), bedHeadZ,
                        (x, y, z) -> residentBlockType(accessor, x, y, z),
                        (x, y, z, blockId) -> rt.blockStates().get(x, y, z, blockId))) {
                    rollbackEdit(player, edit, current);
                    return;
                }
            }
            BuildingBlockRules.ChestPlacement chestPlacement = chest
                    ? BuildingBlockRules.chestPlacement(edit.blockType(),
                            edit.x(), edit.y(), edit.z(), requestedState, stateLookup())
                    : null;
            if (chest && chestPlacement == null) {
                rollbackEdit(player, edit, current);
                return;
            }
            // 지지 조건은 인벤토리 소비 전에 확정한다. 거부 시 기존 롤백과 무소비 의미를 유지한다.
            if (!BlockEditRules.isPlacementSupported(edit.blockType(), requestedState,
                    edit.x(), edit.y(), edit.z(),
                    (x, y, z) -> residentBlockType(accessor, x, y, z),
                    (x, y, z, blockId) -> rt.blockStates().get(x, y, z, blockId))) {
                rollbackEdit(player, edit, current);
                return;
            }
            // [SHULKER-CONTENTS] 소비하기 **전에** 읽는다 — consumeForPlace 가 그 칸을 비운다.
            int placedShulkerId = Blocks.isShulkerBox(edit.blockType())
                    ? player.inventory().stack(placeHand).shulkerId() : 0;
            // Banner patterns are block-entity data and disappear from the source slot on consumption.
            // Capture the exact immutable component list first; stage it only after consumption commits.
            String placedPotComponents = Blocks.isDecoratedPot(Short.toUnsignedInt(edit.blockType()))
                    ? player.inventory().stack(placeHand).itemComponentData() : null;
            List<com.gameexpert.engine.inventory.ItemComponentData.BannerLayer> placedBannerPatterns =
                    Blocks.isBanner(Short.toUnsignedInt(edit.blockType()))
                            ? player.inventory().stack(placeHand).itemComponents().bannerPatterns()
                            : null;
            if (placedBannerPatterns != null && rt.bannerPlacementSettlements() != null) {
                settleBannerPlacement(player, placeHand, edit, requestedState,
                        placedBannerPatterns);
                return;
            }
            // 선택 슬롯에 해당 blockType 을 들고 있어야 설치 가능(없으면 롤백 + 거부).
            if (!consumeForPlace(player.inventory(), placeHand, edit.blockType())) {
                rollbackEdit(player, edit, current);
                return;
            }
            if (placedBannerPatterns != null) {
                rt.stageBannerPlacement(edit.x(), edit.y(), edit.z(), placedBannerPatterns);
            }
            boolean placedSign = edit.blockType() == Blocks.POPLAR_SIGN
                    || edit.blockType() == Blocks.POPLAR_HANGING_SIGN;
            if (placedSign) rt.stageSignPlacement(edit.x(), edit.y(), edit.z());
            // [CONCRETE] 덮기 전의 칸을 남긴다 — 바닐라 getStateForPlacement 가
            // shouldSolidify 에 넘기는 "클릭한 칸의 상태" 이고, 물웅덩이에 놓은 콘크리트
            // 가루는 이웃 물이 하나도 없어도 이 갈래로 굳는다.
            if (Blocks.isConcretePowder(edit.blockType())) {
                placedOver.put(new BlockPos(edit.x(), edit.y(), edit.z()), current);
            }
            rt.fluidSim().applyChange(edit.x(), edit.y(), edit.z(), edit.blockType());
            if (edit.y() > Blocks.MIN_Y) {
                int below = residentBlockType(accessor, edit.x(), edit.y() - 1, edit.z());
                int changedBelow = SupportRules.blockAfterAboveChange(below, edit.blockType());
                if (changedBelow != below) {
                    rt.fluidSim().applyChange(edit.x(), edit.y() - 1, edit.z(), changedBelow);
                }
            }
            if (door) {
                // 문은 하단/상단을 한 틱에 원자적으로 놓는다. 설치 시에는 항상 닫힌 상태다.
                int facing = requestedState & BuildingBlockRules.FACING_MASK;
                int lowerState = facing | BuildingBlockRules.doorHinge(
                        edit.x(), edit.y(), edit.z(), facing,
                        (x, y, z) -> residentBlockType(accessor, x, y, z));
                int doorId = edit.blockType();
                rt.setBlockState(edit.x(), edit.y(), edit.z(), doorId, lowerState);
                rt.tickBlockChanges().put(new BlockPos(edit.x(), edit.y(), edit.z()),
                        (short) doorId);
                rt.fluidSim().applyChange(edit.x(), edit.y() + 1, edit.z(), doorId);
                rt.setBlockState(edit.x(), edit.y() + 1, edit.z(), doorId,
                        lowerState | BuildingBlockRules.DOOR_UPPER);
                rt.tickBlockChanges().put(new BlockPos(edit.x(), edit.y() + 1, edit.z()),
                        (short) doorId);
            } else if (tallPlant) {
                // [PITCHER] DoublePlantBlock.setPlacedBy: the upper half in the same tick.
                int plant = edit.blockType();
                rt.setBlockState(edit.x(), edit.y(), edit.z(), plant, 0);
                rt.tickBlockChanges().put(new BlockPos(edit.x(), edit.y(), edit.z()), (short) plant);
                rt.fluidSim().applyChange(edit.x(), edit.y() + 1, edit.z(), plant);
                rt.setBlockState(edit.x(), edit.y() + 1, edit.z(), plant, PitcherRules.UPPER);
                rt.tickBlockChanges().put(new BlockPos(edit.x(), edit.y() + 1, edit.z()), (short) plant);
            } else if (bed) {
                // 두 셀 모두 요청된 색 그대로 놓는다. 짝 셀의 색이 다르면 지지 판정이 둘을
                // 하나의 침대로 보지 않는다(SupportRules).
                int bedId = edit.blockType();
                rt.setBlockState(edit.x(), edit.y(), edit.z(), bedId, requestedState);
                rt.tickBlockChanges().put(new BlockPos(edit.x(), edit.y(), edit.z()),
                        (short) bedId);
                rt.fluidSim().applyChange(bedHeadX, edit.y(), bedHeadZ, bedId);
                rt.setBlockState(bedHeadX, edit.y(), bedHeadZ, bedId,
                        requestedState | BuildingBlockRules.BED_HEAD);
                rt.tickBlockChanges().put(new BlockPos(bedHeadX, edit.y(), bedHeadZ),
                        (short) bedId);
                if (edit.y() > Blocks.MIN_Y) {
                    int belowHead = residentBlockType(accessor, bedHeadX, edit.y() - 1, bedHeadZ);
                    int changedBelowHead = SupportRules.blockAfterAboveChange(belowHead, bedId);
                    if (changedBelowHead != belowHead) {
                        rt.fluidSim().applyChange(
                                bedHeadX, edit.y() - 1, bedHeadZ, changedBelowHead);
                    }
                }
            } else {
                int placedState = requestedState;
                if (chest) {
                    placedState = chestPlacement.state();
                    if (chestPlacement.paired()) {
                        int partnerDirection = chestPlacement.partnerDirection();
                        int partnerX = edit.x() + (partnerDirection == 1 ? 1
                                : partnerDirection == 3 ? -1 : 0);
                        int partnerZ = edit.z() + (partnerDirection == 2 ? 1
                                : partnerDirection == 0 ? -1 : 0);
                        // 짝은 같은 ID 끼리만 이루므로(Blocks.chestPairs) 상대의 ID 는
                        // 놓이는 상자 자신의 ID 와 언제나 같다.
                        rt.setBlockState(partnerX, edit.y(), partnerZ, edit.blockType(),
                                chestPlacement.partnerState());
                        rt.tickBlockChanges().put(new BlockPos(partnerX, edit.y(), partnerZ),
                                (short) edit.blockType());
                        // A viewer that opened this cell while it was a single chest owns a
                        // 27-slot session. Pairing changes slot order and capacity immediately;
                        // close that stale session in the same authority tick instead of leaving
                        // it stranded until the viewer's next action discovers the mismatch.
                        closeChestSubscribersAt(partnerX, edit.y(), partnerZ);
                    }
                } else if (Blocks.isFenceGate(edit.blockType())) {
                    placedState &= ~BuildingBlockRules.GATE_OPEN;
                } else if (Blocks.isTrapdoor(edit.blockType())) {
                    placedState &= ~BuildingBlockRules.TRAPDOOR_OPEN;
                } else if (edit.blockType() == Blocks.CAMPFIRE) {
                    placedState |= BuildingBlockRules.CAMPFIRE_LIT;
                }
                placedState = redstone.engine().placementState(edit.blockType(), placedState,
                        edit.x(), edit.y(), edit.z(), rt.clock().gameTimeMcTicks());
                rt.setBlockState(edit.x(), edit.y(), edit.z(), edit.blockType(), placedState);
                rt.tickBlockChanges().put(new BlockPos(edit.x(), edit.y(), edit.z()),
                        edit.blockType());
                // [PRISMARINE] 마른 스펀지는 설치 즉시 주변 물을 빨아들이고 젖은 스펀지가 된다
                // (바닐라 SpongeBlock.onPlace → tryAbsorbWater). 판정 정본은 SpongeAbsorption 이고
                // 여기서는 그 결과를 유체 변경 경로에 흘려 보내기만 한다.
                if (edit.blockType() == Blocks.SPONGE) {
                    absorbWaterAround(edit.x(), edit.y(), edit.z(), accessor);
                }
            }
            trySpawnConstructedGolem(edit.x(), edit.y(), edit.z(), edit.blockType(), accessor);
            scheduleAnimalBlockIfNeeded(edit.x(), edit.y(), edit.z());
            refreshConnectionsAround(edit.x(), edit.y(), edit.z());
            refreshStairsAround(edit.x(), edit.y(), edit.z());
            if (Blocks.isShelf(Short.toUnsignedInt(edit.blockType()))) {
                refreshShelfChain(edit.x(), edit.y(), edit.z());
            }
            // Admit support loss at the edit's game time, before this turn advances the clock.
            refreshSpeleothemRuns(List.of(new BlockPos(edit.x(), edit.y(), edit.z())));
            if (bed) {
                refreshConnectionsAround(bedHeadX, edit.y(), bedHeadZ);
                refreshStairsAround(bedHeadX, edit.y(), bedHeadZ);
            }
            // [SHULKER-CONTENTS] 설치는 아이템이 물고 온 27칸을 그 좌표의 블록 엔티티로
            // 되돌린다. 참조 저장소의 행 제거와 좌표 캐시 삽입이 같은 틱에 일어나고, 두 lane 의
            // dirty 는 ChestPersistenceService 가 **한 트랜잭션**으로 커밋한다 — 그래서 27칸이
            // 두 곳에 동시에 존재하는 순간이 없다(정적판 `release` 의 서버 쌍).
            if (Blocks.isShulkerBox(edit.blockType())) {
                ChestInventory carried = placedShulkerId == 0
                        ? null : rt.shulkerStorage().take(placedShulkerId);
                // 참조가 가리키는 행이 사라진 세이브도 **빈 27칸으로 이어 간다** — 여기서
                // 예외를 던지면 설치 액션이 통째로 죽는다(정적판 `get` 과 같은 계약).
                rt.chestStorage().load(edit.x(), edit.y(), edit.z(),
                        carried == null ? new ChestInventory() : carried);
                rt.chestStorage().markDirty(edit.x(), edit.y(), edit.z());
            }
            if (Blocks.isShelf(Short.toUnsignedInt(edit.blockType()))) {
                rt.chestStorage().openAt(edit.x(), edit.y(), edit.z(),
                        com.gameexpert.engine.shelf.ShelfRules.SLOTS);
                rt.chestStorage().markDirty(edit.x(), edit.y(), edit.z());
            }
            if (Blocks.isDecoratedPot(Short.toUnsignedInt(edit.blockType())) && placedPotComponents != null) {
                ChestInventory pot = rt.chestStorage().openAt(edit.x(), edit.y(), edit.z(), 1);
                pot.setPotItemComponents(placedPotComponents);
                rt.chestStorage().markDirty(edit.x(), edit.y(), edit.z());
                broadcastPotDecorations(edit.x(), edit.y(), edit.z(), placedPotComponents);
            }
            // Container settlements deliberately reject a DB row older than their captured
            // source revision. Placement consumed an item, so persist that complete baseline now;
            // the single writer guarantees an immediately following chest/furnace/campfire
            // settlement runs after it instead of spuriously returning STALE.
            rt.queuePlayerInventoryBaseline(player);
            sendTo(player, inventoryMessage(player));
            broadcastWorldSound("block_place", edit.x(), edit.y(), edit.z(), edit.blockType());
            if (placedSign) {
                sendTo(player, new com.gameexpert.ws.dto.WsMessages.SignOpen(
                        WorldRuntime.toWireSign(rt.signAt(edit.x(), edit.y(), edit.z())), edit.requestId()));
            }
        } else {
            if (Blocks.isBanner(current) && rt.bannerPlacementSettlements() != null) {
                var banner = rt.bannerAt(edit.x(), edit.y(), edit.z());
                if (banner != null) {
                    settleBannerMining(player, edit, current, currentState, banner,
                            editMainSlot(player));
                    return;
                }
            }
            BlockPos colonyPos = new BlockPos(edit.x(), edit.y(), edit.z());
            if (rt.isFleshColonyOrgan(colonyPos, current)) {
                settleFleshColonyMining(player, edit, current, editMainSlot(player));
                return;
            }
            if (current == Blocks.LECTERN && rt.lecternPersistence() != null) {
                var lectern = rt.lecternPersistence().load(
                        rt.worldId(), edit.x(), edit.y(), edit.z());
                if (lectern.isPresent()) {
                    settleLecternMining(player, edit, currentState, lectern.get(),
                            editMainSlot(player));
                    return;
                }
            }
            // 파괴 성공: 오버레이 AIR 반영 후 드랍을 아이템 엔티티로 스폰(S2a — 직접 수납 대체) +
            // 든 도구 내구 마모(§4). 드랍은 근접 획득으로 인벤에 들어간다.
            player.addExhaustion(HungerRules.BREAK_EXHAUSTION_MILLI); // SURV-H: 검증을 통과한 파괴만 소모한다
            // 보류됐다 재적용되는 파괴도 도착 당시에 든 도구로 드랍·마모를 정한다.
            int mineSlot = editMainSlot(player);
            short selectedItemType = player.inventory().itemType(mineSlot);
            // [ENCHANT-WIDE] 손에 든 마법이 부여된 책의 인챈트(행운·섬세한 손길)는 채굴에 효과가 없다.
            long selectedEnchantments = EnchantmentRules.heldEffectEnchantments(
                    selectedItemType, player.inventory().enchantments(mineSlot));
            redstone.engine().playerWillDestroy(edit.x(), edit.y(), edit.z(), selectedItemType == Blocks.SHEARS,
                    rt.clock().gameTimeMcTicks());
            commitRedstone();
            int brokenState = rt.blockState(edit.x(), edit.y(), edit.z(), current);
            var largeCocoon = current == Blocks.FLESH_LARGE_COCOON
                    ? FleshCocoonParts.resolve(edit.x(), edit.y(), edit.z(), brokenState,
                        (x, y, z) -> residentBlockType(accessor, x, y, z),
                        (x, y, z) -> rt.blockState(x, y, z, Blocks.FLESH_LARGE_COCOON)) : null;
            // [PITCHER] State of the lower half whose loot this break drops (-1: none, a lone upper half).
            int pitcherLowerState = PitcherRules.isPitcher(current) && !PitcherRules.isUpper(brokenState)
                    ? brokenState : -1;
            // The projection is live only before the authoritative AIR overlay is written below.
            // Taking it here gives this admitted break exclusive ownership; a retry therefore falls
            // through to the ordinary decorated-pot behavior instead of minting another four faces.
            String brokenPotComponents = decoratedPotComponentsAt(edit.x(), edit.y(), edit.z());
            var generatedTrialPot = Blocks.isDecoratedPot(current)
                    ? rt.takeGeneratedTrialDecoratedPotAt(edit.x(), edit.y(), edit.z())
                    : java.util.Optional.<WorldRuntime.GeneratedDecoratedPotRuntime>empty();
            cancelAnimalBlockSchedule(edit.x(), edit.y(), edit.z());
            if (current == Blocks.BEE_NEST || current == Blocks.BEEHIVE) {
                rt.mobSystem().releaseBeesFromHive(
                        edit.x(), edit.y(), edit.z(), player.nickname());
            }
            rt.fluidSim().applyChange(edit.x(), edit.y(), edit.z(),
                    iceMeltsOnPlayerDestroy(current, selectedEnchantments, edit.x(), edit.y(), edit.z())
                            ? WATER_SOURCE
                            : blockAfterRemovalAt(edit.x(), edit.y(), edit.z(), current, brokenState));
            if (current == Blocks.FROSTED_ICE) frostedIceTicks.remove(new BlockPos(edit.x(), edit.y(), edit.z()));
            if (largeCocoon != null) for (BlockPos part : largeCocoon.cells()) {
                if (part.y() == edit.y()) continue;
                rt.fluidSim().applyChange(part.x(), part.y(), part.z(), AIR);
                refreshConnectionsAround(part.x(), part.y(), part.z());
                refreshStairsAround(part.x(), part.y(), part.z());
            }
            if (Blocks.isDoor(current)) {
                int otherY = (brokenState & BuildingBlockRules.DOOR_UPPER) != 0
                        ? edit.y() - 1 : edit.y() + 1;
                if (otherY >= Blocks.MIN_Y && otherY <= Blocks.MAX_Y
                        && residentBlockType(accessor, edit.x(), otherY, edit.z()) == current) {
                    rt.fluidSim().applyChange(edit.x(), otherY, edit.z(), AIR);
                    refreshConnectionsAround(edit.x(), otherY, edit.z());
                    refreshStairsAround(edit.x(), otherY, edit.z());
                }
            } else if (Blocks.isBed(current)) {
                int otherX = BuildingBlockRules.bedOtherX(edit.x(), brokenState);
                int otherZ = BuildingBlockRules.bedOtherZ(edit.z(), brokenState);
                if (residentBlockType(accessor, otherX, edit.y(), otherZ) == current) {
                    int otherState = rt.blockStates().get(otherX, edit.y(), otherZ, current);
                    if (BuildingBlockRules.matchingBedStates(brokenState, otherState)) {
                        rt.fluidSim().applyChange(otherX, edit.y(), otherZ, AIR);
                        refreshConnectionsAround(otherX, edit.y(), otherZ);
                        refreshStairsAround(otherX, edit.y(), otherZ);
                    }
                }
            } else if (PitcherRules.isPitcher(current)) {
                // [PITCHER] DoublePlantBlock: breaking either half removes the other one too.
                boolean upper = PitcherRules.isUpper(brokenState);
                int otherY = upper ? edit.y() - 1 : edit.y() + 1;
                if (otherY >= Blocks.MIN_Y && otherY <= Blocks.MAX_Y
                        && residentBlockType(accessor, edit.x(), otherY, edit.z()) == current) {
                    int otherState = rt.blockStates().get(edit.x(), otherY, edit.z(), current);
                    if (PitcherRules.isUpper(otherState) != upper) {
                        if (upper) pitcherLowerState = otherState;
                        rt.fluidSim().applyChange(edit.x(), otherY, edit.z(),
                                blockAfterRemovalAt(edit.x(), otherY, edit.z(), current, otherState));
                        refreshConnectionsAround(edit.x(), otherY, edit.z());
                        refreshStairsAround(edit.x(), otherY, edit.z());
                    }
                }
            }
            refreshConnectionsAround(edit.x(), edit.y(), edit.z());
            refreshStairsAround(edit.x(), edit.y(), edit.z());
            refreshSpeleothemRuns(List.of(new BlockPos(edit.x(), edit.y(), edit.z())));
            if (current == Blocks.OBSIDIAN) {
                extinguishPortalsTouching(edit.x(), edit.y(), edit.z());
            }
            // [SHULKER-CONTENTS] 셜커 상자는 **내용을 쏟지 않는다** — 27칸이 떨어지는 아이템
            // 안에 그대로 들어간다([A] 바닐라 ShulkerBoxBlock 전리품표의 block_entity_data).
            // 좌표 캐시에서 떼어 내는 것과 참조 발급이 같은 틱이고, 두 lane 의 dirty 는 한
            // 트랜잭션으로 커밋된다(정적판 `reserve` 의 서버 쌍).
            int minedShulkerId = 0;
            if (Blocks.isShulkerBox(current)) {
                closeChestSubscribersAt(edit.x(), edit.y(), edit.z());
                rt.discardNaturalChestLoot(edit.x(), edit.y(), edit.z());
                ChestInventory taken = rt.chestStorage().detachAt(edit.x(), edit.y(), edit.z());
                // 빈 상자는 행을 만들지 않고 참조 ID 도 발급하지 않는다(정적판과 같은 규칙).
                if (taken != null && !taken.isEmpty()) {
                    minedShulkerId = rt.shulkerStorage().store(taken);
                }
            }
            CropRules.Rule crop = CropRules.forCrop(current);
            int animalCropDrop = AnimalDependencyBlockRules.cropDropItem(current, brokenState);
            var tissueDrops = largeCocoon != null && !largeCocoon.reward() ? java.util.List.<int[]>of()
                    : FleshTissueLoot.drops(current, brokenState, berryHarvestRandom::nextDouble);
            if (tissueDrops != null) {
                for (int[] drop : tissueDrops) rt.itemSystem().spawnDrop((short) drop[0], drop[1],
                        edit.x() + .5, edit.y() + .5, edit.z() + .5);
            } else if (PitcherRules.isPitcher(current)) {
                // [PITCHER] blocks/pitcher_crop.json · pitcher_plant.json: the lower half's loot, once.
                if (pitcherLowerState >= 0) {
                    rt.itemSystem().spawnDrop((short) (current == Blocks.PITCHER_CROP
                            ? PitcherRules.cropLootItem(pitcherLowerState) : Blocks.PITCHER_PLANT), 1,
                            edit.x() + 0.5, edit.y() + 0.5, edit.z() + 0.5);
                }
            } else if (FleshNetherRules.isFlesh(current)) {
                int drop = FleshNetherRules.dropItem(current,
                        FleshNetherRules.requiresLootRoll(current) ? berryHarvestRandom.nextDouble() : 1.0);
                if (drop != Blocks.AIR) rt.itemSystem().spawnDrop((short) drop, 1,
                        edit.x() + 0.5, edit.y() + 0.5, edit.z() + 0.5);
            } else if (animalCropDrop != Blocks.AIR) {
                rt.itemSystem().spawnDrop((short) animalCropDrop, 1,
                        edit.x() + 0.5, edit.y() + 0.5, edit.z() + 0.5);
            } else if (crop != null) {
                rt.itemSystem().spawnCropDrops(crop, brokenState,
                        edit.x() + 0.5, edit.y() + 0.5, edit.z() + 0.5);
            } else if (current == Blocks.SWEET_BERRY_BUSH) {
                // [CROP-BERRY] [A] blocks/sweet_berry_bush 전리품표: 수확과 같은 분포이되
                // 열매가 없는 age 는 1개(심을 열매 하나는 돌려받는다). 행운은 레벨당 +1 이다.
                // 작물 표(CropRules)를 쓰지 않으므로 여기 별도 갈래다.
                int fortune = EnchantmentRules.enchantLevel(
                        selectedEnchantments, EnchantmentRules.FORTUNE);
                int count = SweetBerryBushRules.breakDropCount(
                        SweetBerryBushRules.age(brokenState), fortune,
                        berryHarvestRandom::nextInt);
                rt.itemSystem().spawnDrop(PlayerInventory.SWEET_BERRIES, count,
                        edit.x() + 0.5, edit.y() + 0.5, edit.z() + 0.5);
            } else if (Blocks.isShulkerBox(current)) {
                // 셜커 상자는 도구·행운·섬세한 손길과 무관하게 언제나 자기 자신 하나를 내고
                // 경험치를 주지 않는다([A] 바닐라). 그래서 일반 채굴 드랍 표를 타지 않고,
                // 방금 발급한 27칸 참조를 그대로 실어 보낸다.
                rt.itemSystem().spawnDrop((short) current, 1, 0,
                        EnchantmentRules.EMPTY_ENCHANTMENTS, 0, minedShulkerId,
                        edit.x() + 0.5, edit.y() + 0.5, edit.z() + 0.5);
            } else if (CandleRules.isCandle(current)) {
                rt.itemSystem().spawnDrop((short) current, CandleRules.count(brokenState),
                        edit.x() + 0.5, edit.y() + 0.5, edit.z() + 0.5);
            } else if (current == Blocks.LEAF_LITTER) {
                rt.itemSystem().spawnDrop((short) current, P26Rules.minedDropCount(current, brokenState, 1),
                        edit.x() + 0.5, edit.y() + 0.5, edit.z() + 0.5);
            } else if (current == Blocks.SHELF_MUSHROOM) {
                int count = (brokenState & com.gameexpert.engine.blocks.P29Rules.AGE) != 0 ? 2 : 1;
                rt.itemSystem().spawnDrop((short) Blocks.SHELF_MUSHROOM, count,
                        edit.x() + 0.5, edit.y() + 0.5, edit.z() + 0.5);
            } else if (Blocks.isDecoratedPot(current)) {
                rt.itemSystem().spawnDecoratedPotDrop((short) current,
                        ArchaeologyRules.shattersDecoratedPot(selectedItemType, selectedEnchantments),
                        brokenPotComponents, generatedTrialPot.isPresent() ? generatedTrialPot.get().faceItemTypes() : null,
                        edit.x() + .5, edit.y() + .5, edit.z() + .5);
            } else {
                rt.itemSystem().spawnMinedBlockDrop((short) current, selectedItemType,
                        selectedEnchantments, edit.x() + 0.5, edit.y() + 0.5, edit.z() + 0.5);
                // [INFESTED-26.3] InfestedBlock.spawnAfterBreak is suppressed only by the
                // prevents_infested_spawns enchantment tag (Silk Touch in the pinned data pack).
                if (InventoryRules.spawnsSilverfishWhenMined(current, selectedEnchantments)) {
                    rt.mobSystem().spawnInfestedBlockSilverfish(edit.x(), edit.y(), edit.z());
                }
                // [SURV-X] 섬세한 손길로 캔 블록은 경험치를 주지 않는다(§2).
                if (EnchantmentRules.enchantLevel(
                        selectedEnchantments, EnchantmentRules.SILK_TOUCH) <= 0) {
                    int minedXp = XpRules.xpForMinedBlock(current, blockXpRandom.nextInt(1000));
                    if (minedXp > 0) {
                        rt.xpOrbSystem().spawnOrbs(minedXp,
                                edit.x() + 0.5, edit.y() + 0.5, edit.z() + 0.5);
                    }
                }
            }
            // 상자를 부수면 보관 중이던 아이템도 전부 쏟아진다(MC 동일). 이 처리가 없으면 내용물이
            // 조용히 사라진다 — 상자 블록 자체의 드랍은 위 spawnMinedBlockDrop 이 이미 처리했다.
            // [ENDER-SHULKER] 엔더 상자는 내용이 좌표에 없으므로 부숴도 아무것도 쏟지 않는다
            // ([B] 바닐라도 그대로다 — 내용은 플레이어에게 남는다). 좌표 저장소에 행이 없어
            // dropChestContents 가 빈 목록을 내겠지만, "없어서 안 쏟는다"와 "쏟으면 안 된다"는
            // 다른 계약이라 조건을 명시한다.
            // 열려 있던 화면은 갈래와 무관하게 닫는다 — 엔더 상자를 부순 자리에 남은 세션은
            // 사라진 블록을 계속 가리킨다(정적판 쌍둥이 회귀가 이 갈림을 잡았다).
            dropRemovedBlockContents(rt, current, edit.x(), edit.y(), edit.z(),
                    minedShulkerId, true);
            if (isCraftingStationBlock(current)) {
                closeCraftingSubscribersAt(edit.x(), edit.y(), edit.z());
            }
            // 작업 스테이션이 사라지면 그 칸을 점유하던 주민은 일터를 잃는다(바닐라 동일).
            if (VillagerJobSitePolicy.isJobSiteBlock(current)) {
                rt.mobSystem().releaseVillagerJobSiteAt(edit.x(), edit.y(), edit.z());
            }
            int wearSlot = mineSlot;
            short wearType = player.inventory().itemType(wearSlot);
            int wearBefore = player.inventory().durability(wearSlot);
            if (wearToolOnMine(player.inventory(), wearSlot)) {
                recordBreakIfGone(player, wearSlot, wearType, wearBefore);
                rt.queuePlayerInventoryBaseline(player);
                sendTo(player, inventoryMessage(player));
            }
            // [VANILLA-SOUNDS] 금 간 장식 항아리(SoundType.DECORATED_POT_CRACKED)의 파괴음은 shatter 다.
            String soundKind = Blocks.isDecoratedPot(current)
                    && ArchaeologyRules.shattersDecoratedPot(selectedItemType, selectedEnchantments)
                    ? "decorated_pot_shatter"
                    : crop != null && (PitcherRules.isPitcher(current)
                            ? PitcherRules.age(brokenState) : brokenState) >= crop.maxAge()
                            ? "harvest" : "block_break";
            broadcastWorldSound(soundKind, edit.x(), edit.y(), edit.z(), (short) current);
            // PiglinAi.angerNearbyPiglins(level, player, false): 보호 블록 파괴는 시야를 요구하지 않는다.
            if (PiglinGuardedBlocks.isGuarded(current)) {
                rt.mobSystem().angerPiglinsNearGuardedEvent(
                        player.nickname(), player.x(), player.y(), player.z(), false);
            }
        }
    }

    private void spawnGeneratedTrialDecoratedPotDrops(short[] faceItemTypes, int x, int y, int z) {
        for (short itemType : faceItemTypes) {
            rt.itemSystem().spawnDrop(itemType, 1,
                    x + 0.5, y + 0.5, z + 0.5);
        }
    }

    private void settleFleshColonyMining(PlayerTickState player, PlayerAction.BlockEdit edit,
            int current, int wearSlot) {
        BlockPos pos = new BlockPos(edit.x(), edit.y(), edit.z());
        var colony = rt.fleshColonyState(pos);
        if (colony.coreDestroyed() || current == Blocks.HEART_CORE
                && colony.severedMask() != FleshColonyProgress.ALL_ANCHORS_MASK) {
            rollbackEdit(player, edit, current);
            return;
        }
        PlayerInventory inventory = player.inventory();
        var source = inventory.acquireSettlementLease();
        if (source == null) { rollbackEdit(player, edit, current); return; }
        PlayerInventory planned = source.detachedInventory();
        short wearType = planned.itemType(wearSlot);
        int wearBefore = planned.durability(wearSlot);
        boolean worn = wearToolOnMine(planned, wearSlot);
        if (!worn && !planned.advanceStateSettlementRevision()) {
            inventory.releaseSettlementLease(source);
            rollbackEdit(player, edit, current);
            return;
        }
        var committed = planned.completePersistenceSnapshot();
        long id = rt.itemSystem().reserveSettlementEntityId();
        var drop = rt.itemSystem().settlementDropSnapshot(id,
                (short) (current == Blocks.HEART_CORE ? Blocks.HEART_CORE : Blocks.FLESH_CLOT_SAC),
                current == Blocks.HEART_CORE ? 1 : 2, pos.x() + .5, pos.y() + .5, pos.z() + .5);
        long groundRevision = rt.groundRevision();
        var command = new com.gameexpert.ground.dto.GroundMutationCommand(
                WorldRuntime.stableGroundMutationId(id, 3),
                com.gameexpert.ground.dto.GroundMutationCommand.Kind.BLOCK_DROP,
                rt.worldId(), groundRevision, groundRevision + 1, source.revision(),
                rt.playerInventoryMutationSnapshot(player, committed),
                List.of(drop), List.of(), List.of(), List.of());
        Runnable rejected = () -> {
            inventory.releaseSettlementLease(source);
            rollbackEdit(player, edit, current);
        };
        Runnable installed = () -> {
            rt.itemSystem().commitSettlementDrop(drop);
            rt.fluidSim().onBlockChanged(pos.x(), pos.y(), pos.z());
            refreshConnectionsAround(pos.x(), pos.y(), pos.z());
            refreshStairsAround(pos.x(), pos.y(), pos.z());
            if (rt.players().get(player.nickname()) == player
                    && inventory.installCommittedSettlement(source, committed)) {
                player.addExhaustion(HungerRules.BREAK_EXHAUSTION_MILLI);
                if (worn) recordBreakIfGone(player, wearSlot, wearType, wearBefore);
                sendTo(player, inventoryMessage(player));
            } else inventory.releaseSettlementLease(source);
            broadcastWorldSound("block_break", pos.x(), pos.y(), pos.z(), (short) current);
            for (BlockPos site : rt.notifyFleshColonySettlement(pos)) {
                rt.ctx().broadcaster().enqueueBroadcastFromTick(rt.worldId(),
                        new com.gameexpert.ws.dto.WsMessages.FleshAlarm(site.x(),site.y(),site.z()));
                broadcastWorldSound("block_hit",site.x(),site.y(),site.z(),(short)Blocks.FLESH_COCOON);
            }
        };
        if (!rt.submitFleshColonyMining(pos, colony.revision(), command, installed, rejected)) rejected.run();
    }

    private void settleBannerMining(PlayerTickState player, PlayerAction.BlockEdit edit,
            int current, int brokenState, com.gameexpert.banner.service.BannerBlockData banner,
            int wearSlot) {
        PlayerInventory inventory = player.inventory();
        PlayerInventory.CompletePersistenceSnapshot source = inventory.acquireSettlementLease();
        if (source == null) {
            rollbackEdit(player, edit, current);
            return;
        }
        PlayerInventory planned = source.detachedInventory();
        // 사본의 현재 선택 칸이 아니라 파괴 요청이 캡처한 칸을 마모한다.
        short wearType = planned.itemType(wearSlot);
        int wearBefore = planned.durability(wearSlot);
        boolean worn = wearToolOnMine(planned, wearSlot);
        if (!worn && !planned.advanceStateSettlementRevision()) {
            inventory.releaseSettlementLease(source);
            rollbackEdit(player, edit, current);
            return;
        }
        var committed = planned.completePersistenceSnapshot();
        long entityId = rt.itemSystem().reserveSettlementEntityId();
        var stack = com.gameexpert.banner.dto.BannerMiningSettlementCommand.bannerStack(
                (short) current, banner);
        var drop = rt.itemSystem().settlementDropSnapshot(
                entityId, stack, edit.x() + 0.5, edit.y() + 0.5, edit.z() + 0.5);
        long expectedGroundRevision = rt.groundRevision();
        var ground = new com.gameexpert.ground.dto.GroundMutationCommand(
                WorldRuntime.stableGroundMutationId(entityId, 3),
                com.gameexpert.ground.dto.GroundMutationCommand.Kind.BLOCK_DROP,
                rt.worldId(), expectedGroundRevision, expectedGroundRevision + 1,
                source.revision(), rt.playerInventoryMutationSnapshot(player, committed),
                List.of(drop), List.of(), List.of(), List.of());
        var command = new com.gameexpert.banner.dto.BannerMiningSettlementCommand(
                ground, banner, (short) current, (short) brokenState);
        Runnable rejected = () -> {
            inventory.releaseSettlementLease(source);
            rollbackEdit(player, edit, current);
        };
        Runnable installed = () -> {
            rt.itemSystem().commitSettlementDrop(drop);
            rt.commitSettledBannerMining(edit.x(), edit.y(), edit.z());
            rt.fluidSim().applyChange(edit.x(), edit.y(), edit.z(), Blocks.AIR);
            refreshConnectionsAround(edit.x(), edit.y(), edit.z());
            refreshStairsAround(edit.x(), edit.y(), edit.z());
            if (rt.players().get(player.nickname()) == player
                    && inventory.installCommittedSettlement(source, committed)) {
                player.addExhaustion(HungerRules.BREAK_EXHAUSTION_MILLI);
                if (worn) recordBreakIfGone(player, wearSlot, wearType, wearBefore);
                sendTo(player, inventoryMessage(player));
            } else {
                inventory.releaseSettlementLease(source);
            }
            broadcastWorldSound("block_break", edit.x(), edit.y(), edit.z(), (short) current);
        };
        if (!rt.submitBannerMiningSettlement(command, installed, rejected)) rejected.run();
    }

    private void settleLecternMining(PlayerTickState player, PlayerAction.BlockEdit edit,
            int brokenState, com.gameexpert.lectern.dto.LecternBlockData lectern, int wearSlot) {
        PlayerInventory inventory = player.inventory();
        PlayerInventory.CompletePersistenceSnapshot source = inventory.acquireSettlementLease();
        if (source == null) {
            rollbackEdit(player, edit, Blocks.LECTERN);
            return;
        }
        PlayerInventory planned = source.detachedInventory();
        // 사본의 현재 선택 칸이 아니라 파괴 요청이 캡처한 칸을 마모한다.
        short wearType = planned.itemType(wearSlot);
        int wearBefore = planned.durability(wearSlot);
        boolean worn = wearToolOnMine(planned, wearSlot);
        if (!worn && !planned.advanceStateSettlementRevision()) {
            inventory.releaseSettlementLease(source);
            rollbackEdit(player, edit, Blocks.LECTERN);
            return;
        }
        PlayerInventory.CompletePersistenceSnapshot committed =
                planned.completePersistenceSnapshot();
        long lecternEntityId = rt.itemSystem().reserveSettlementEntityId();
        long bookEntityId = rt.itemSystem().reserveSettlementEntityId();
        double dropX = edit.x() + 0.5;
        double dropY = edit.y() + 0.5;
        double dropZ = edit.z() + 0.5;
        var lecternDrop = rt.itemSystem().settlementDropSnapshot(
                lecternEntityId, (short) Blocks.LECTERN, dropX, dropY, dropZ);
        var bookDrop = rt.itemSystem().settlementDropSnapshot(
                bookEntityId, lectern.book(), dropX, dropY, dropZ);
        long expectedGroundRevision = rt.groundRevision();
        var ground = new com.gameexpert.ground.dto.GroundMutationCommand(
                WorldRuntime.stableGroundMutationId(lecternEntityId, 3),
                com.gameexpert.ground.dto.GroundMutationCommand.Kind.BLOCK_DROP,
                rt.worldId(), expectedGroundRevision, expectedGroundRevision + 1,
                source.revision(), rt.playerInventoryMutationSnapshot(player, committed),
                List.of(lecternDrop, bookDrop), List.of(), List.of(), List.of());
        var command = new com.gameexpert.lectern.dto.LecternMiningSettlementCommand(
                ground, lectern);
        Runnable rejected = () -> {
            inventory.releaseSettlementLease(source);
            rollbackEdit(player, edit, Blocks.LECTERN);
        };
        Runnable installed = () -> {
            rt.itemSystem().commitSettlementDrop(lecternDrop);
            rt.itemSystem().commitSettlementDrop(bookDrop);
            rt.fluidSim().applyChange(edit.x(), edit.y(), edit.z(),
                    blockAfterRemoval(Blocks.LECTERN, brokenState));
            closeLecternSubscribersAt(edit.x(), edit.y(), edit.z());
            refreshConnectionsAround(edit.x(), edit.y(), edit.z());
            refreshStairsAround(edit.x(), edit.y(), edit.z());
            rt.mobSystem().releaseVillagerJobSiteAt(edit.x(), edit.y(), edit.z());
            if (rt.players().get(player.nickname()) == player
                    && inventory.installCommittedSettlement(source, committed)) {
                player.addExhaustion(HungerRules.BREAK_EXHAUSTION_MILLI);
                if (worn) recordBreakIfGone(player, wearSlot, wearType, wearBefore);
                sendTo(player, inventoryMessage(player));
            } else {
                inventory.releaseSettlementLease(source);
            }
            broadcastWorldSound("block_break", edit.x(), edit.y(), edit.z(),
                    (short) Blocks.LECTERN);
        };
        if (!rt.submitLecternMiningSettlement(command, installed, rejected)) rejected.run();
    }

    /**
     * Vanilla CarvedPumpkinBlock's snow/iron golem patterns. Matching runs only when the pumpkin
     * head is placed, clears every matched cell, then spawns above the bottom-center block.
     */
    private void trySpawnConstructedGolem(int x, int y, int z, int placedBlock,
            TerrainAccessor accessor) {
        CopperGolemOriginRules.ConstructionPlan copper =
                CopperGolemOriginRules.constructionPlan(
                        (bx, by, bz) -> residentBlockType(accessor, bx, by, bz),
                        x, y, z, placedBlock);
        if (copper != null) {
            if (rt.animalSettlements() == null) {
                throw new IllegalStateException("Copper Golem construction persistence is required");
            }
            long mobId = rt.mobSystem().reserveMobId();
            String key = "copper-golem:" + copper.headX() + ':' + copper.headY()
                    + ':' + copper.headZ();
            pendingCopperSettlementSubmissions.putIfAbsent(key,
                    () -> submitCopperConstruction(key, copper, mobId));
            pendingCopperSettlementSubmissions.get(key).run();
            return;
        }
        if ((placedBlock != Blocks.CARVED_PUMPKIN && placedBlock != Blocks.JACK_O_LANTERN)
                || y - 2 < Blocks.MIN_Y) return;
        if (residentBlockType(accessor, x, y - 1, z) == Blocks.SNOW_BLOCK
                && residentBlockType(accessor, x, y - 2, z) == Blocks.SNOW_BLOCK) {
            for (int clearY = y; clearY >= y - 2; clearY--) {
                clearConstructedGolemBlock(x, clearY, z);
            }
            rt.mobSystem().spawnConstructedGolem(
                    MobType.SNOW_GOLEM, x + 0.5, y - 2 + 0.05, z + 0.5);
            return;
        }
        boolean armsAlongX = ironGolemPattern(accessor, x, y, z, true);
        boolean armsAlongZ = !armsAlongX && ironGolemPattern(accessor, x, y, z, false);
        if (!armsAlongX && !armsAlongZ) return;
        clearConstructedGolemBlock(x, y, z);
        clearConstructedGolemBlock(x, y - 1, z);
        clearConstructedGolemBlock(x, y - 2, z);
        if (armsAlongX) {
            clearConstructedGolemBlock(x - 1, y - 1, z);
            clearConstructedGolemBlock(x + 1, y - 1, z);
        } else {
            clearConstructedGolemBlock(x, y - 1, z - 1);
            clearConstructedGolemBlock(x, y - 1, z + 1);
        }
        rt.mobSystem().spawnConstructedGolem(
                MobType.IRON_GOLEM, x + 0.5, y - 2 + 0.05, z + 0.5);
    }

    private void submitCopperConstruction(String key,
            CopperGolemOriginRules.ConstructionPlan copper, long proposedMobId) {
        if (!copperSettlementCells.reserve(key, List.of(
                new BlockPos(copper.headX(), copper.headY(), copper.headZ()),
                new BlockPos(copper.bodyX(), copper.bodyY(), copper.bodyZ())))) return;
        if (!animalSettlementInFlight.add(key)) return;
        Runnable transaction = () -> {
            com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService
                    .CopperGolemIntent intent = null;
            boolean committed = false;
            try {
                intent = rt.animalSettlements().beginCopperGolem(rt.worldId(),
                        copper.headX(), copper.headY(), copper.headZ(), proposedMobId);
                var snapshot = rt.mobSystem().detachedCopperGolemSnapshot(intent.mobId(),
                        copper.spawnX(), copper.spawnY(), copper.spawnZ(), null,
                        (short) 0, 0, 0);
                committed = rt.animalSettlements().commitCopperGolem(
                        rt.worldId(), intent, snapshot);
            } catch (RuntimeException | Error failure) {
                committed = false;
            }
            var durableIntent = intent;
            boolean durable = committed;
            rt.enqueuePersistenceCompletion(() -> {
                animalSettlementInFlight.remove(key);
                if (!durable || durableIntent == null) {
                    copperSettlementCells.release(key);
                    return;
                }
                try {
                    rt.mobSystem().installCommittedCopperGolem(durableIntent.mobId(),
                            copper.spawnX(), copper.spawnY(), copper.spawnZ(), null,
                            (short) 0, 0, 0);
                    clearConstructedGolemBlock(copper.headX(), copper.headY(), copper.headZ());
                    clearConstructedGolemBlock(copper.bodyX(), copper.bodyY(), copper.bodyZ());
                    pendingCopperSettlementSubmissions.remove(key);
                    pendingAnimalSettlementRetirements.add(durableIntent.key());
                } finally {
                    copperSettlementCells.release(key);
                }
            });
        };
        if (!rt.submitAnimalSettlementPersistence(transaction,
                () -> animalSettlementInFlight.remove(key))) {
            animalSettlementInFlight.remove(key);
        }
    }

    private boolean ironGolemPattern(TerrainAccessor accessor, int x, int y, int z,
            boolean armsAlongX) {
        int dx = armsAlongX ? 1 : 0;
        int dz = armsAlongX ? 0 : 1;
        return residentBlockType(accessor, x, y - 1, z) == Blocks.IRON_BLOCK
                && residentBlockType(accessor, x - dx, y - 1, z - dz) == Blocks.IRON_BLOCK
                && residentBlockType(accessor, x + dx, y - 1, z + dz) == Blocks.IRON_BLOCK
                && residentBlockType(accessor, x, y - 2, z) == Blocks.IRON_BLOCK
                && residentBlockType(accessor, x - dx, y, z - dz) == AIR
                && residentBlockType(accessor, x + dx, y, z + dz) == AIR
                && residentBlockType(accessor, x - dx, y - 2, z - dz) == AIR
                && residentBlockType(accessor, x + dx, y - 2, z + dz) == AIR;
    }

    private void clearConstructedGolemBlock(int x, int y, int z) {
        rt.fluidSim().applyChange(x, y, z, AIR);
        refreshConnectionsAround(x, y, z);
        refreshStairsAround(x, y, z);
    }

    private void applyMineHit(PlayerAction.MineHit hit) {
        PlayerTickState player = rt.players().get(hit.nickname());
        if (player == null || player.isDead()) return;
        int block = residentBlockType(rt.accessor(), hit.x(), hit.y(), hit.z());
        if (block == UNAVAILABLE_BLOCK) return;
        if (BlockEditRules.isRejected(player.x(), player.y(), player.z(), player.crouching(),
                PlayerAction.EditKind.BREAK, hit.x(), hit.y(), hit.z(), (short) 0, block)) return;
        if (!FleshNetherRules.canBreak(block, player.inventory().itemType(editMainSlot(player)))) return;
        // [DRAGON] 드래곤 알은 때리면 부서지지 않고 순간이동한다(DragonEggBlock.attack).
        if (block == Blocks.DRAGON_EGG && rt.dragonFight().teleportEgg(hit.x(), hit.y(), hit.z())) return;
        redstone.engine().attack(hit.x(), hit.y(), hit.z(), rt.clock().gameTimeMcTicks());
        commitRedstone();
        broadcastWorldSound("block_hit", hit.x(), hit.y(), hit.z(), (short) block);
        var miningParticles = new com.gameexpert.ws.dto.WsMessages.MiningParticles(hit.x(), hit.y(), hit.z(),
                block, rt.blockState(hit.x(), hit.y(), hit.z(), block));
        for (PlayerTickState observer : rt.players().values()) {
            if (observer != player && SoundRules.audible(hit.x(), hit.y(), hit.z(),
                    observer.x(), observer.y(), observer.z(), 32)) sendTo(observer, miningParticles);
        }
    }

    /**
     * [PRISMARINE] 설치된 마른 스펀지 주변 물을 바닐라 BFS 로 비우고, 한 칸이라도 빨아들였으면
     * 스펀지를 젖은 스펀지로 바꾼다. 비운 칸은 전부 유체 변경 경로를 타므로 이웃 유체가
     * 다시 활성화되고(빈 자리로 물이 되흐른다) 브로드캐스트·영속화도 기존 경로 그대로다.
     */
    private void absorbWaterAround(int x, int y, int z, TerrainAccessor accessor) {
        List<BlockPos> absorbed = SpongeAbsorption.absorb(x, y, z, (bx, by, bz) -> {
            int block = residentBlockType(accessor, bx, by, bz);
            return block == UNAVAILABLE_BLOCK ? SpongeAbsorption.UNAVAILABLE : block;
        });
        if (absorbed.isEmpty()) return;
        for (BlockPos pos : absorbed) {
            rt.fluidSim().applyChange(pos.x(), pos.y(), pos.z(), AIR);
            rt.tickBlockChanges().put(pos, (short) AIR);
        }
        rt.fluidSim().applyChange(x, y, z, Blocks.WET_SPONGE);
        rt.setBlockState(x, y, z, Blocks.WET_SPONGE, 0);
        rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) Blocks.WET_SPONGE);
    }

    /** 연결 state는 클라이언트 요청이 아니라 현재 서버 월드에서 유도한다. */
    private void refreshConnectionsAround(int x, int y, int z) {
        refreshConnections(List.of(new BlockPos(x, y, z)));
    }

    private static void enqueueConnectionCandidates(
            RevisionedPositionQueue queue, BlockPos changed, long revision) {
        int x = changed.x();
        int y = changed.y();
        int z = changed.z();
        queue.add(x, y, z, revision);
        queue.add(x, y, z - 1, revision);
        queue.add(x + 1, y, z, revision);
        queue.add(x, y, z + 1, revision);
        queue.add(x - 1, y, z, revision);
        queue.add(x, y - 1, z, revision);
        queue.add(x, y + 1, z, revision);
        // 경사 레일은 수평 이웃의 한 칸 위/아래 레일까지 관찰한다.
        queue.add(x - 1, y - 1, z, revision);
        queue.add(x + 1, y - 1, z, revision);
        queue.add(x, y - 1, z - 1, revision);
        queue.add(x, y - 1, z + 1, revision);
        queue.add(x - 1, y + 1, z, revision);
        queue.add(x + 1, y + 1, z, revision);
        queue.add(x, y + 1, z - 1, revision);
        queue.add(x, y + 1, z + 1, revision);
    }

    private void refreshConnections(List<BlockPos> changes) {
        RevisionedPositionQueue candidates = new RevisionedPositionQueue();
        long revision = 1;
        for (BlockPos changed : changes) {
            enqueueConnectionCandidates(candidates, changed, revision);
        }
        RevisionedPosition candidate;
        while ((candidate = candidates.take()) != null) {
            BlockPos position = candidate.position;
            if (!refreshConnection(position.x(), position.y(), position.z())) continue;
            revision = Math.max(revision, candidate.revision) + 1;
            // 레일·상자처럼 이웃의 파생 state를 보는 블록은 실제 변경 뒤에만 재검사한다.
            enqueueConnectionCandidates(candidates, position, revision);
        }
        refreshVineColumns(changes);
    }

    private void refreshVineColumns(List<BlockPos> changes) {
        Set<BlockPos> indexedCells = new HashSet<>();
        LinkedHashSet<BlockPos> runs = new LinkedHashSet<>();
        for (BlockPos changed : changes) {
            queueVineColumnsAt(runs, indexedCells, changed.x(), changed.z(), changed.y());
            queueVineColumnsAt(runs, indexedCells, changed.x() - 1, changed.z(), changed.y());
            queueVineColumnsAt(runs, indexedCells, changed.x() + 1, changed.z(), changed.y());
            queueVineColumnsAt(runs, indexedCells, changed.x(), changed.z() - 1, changed.y());
            queueVineColumnsAt(runs, indexedCells, changed.x(), changed.z() + 1, changed.y());
        }
        for (BlockPos top : runs) {
            for (int vineY = top.y(); vineY >= Blocks.MIN_Y
                    && residentBlockType(rt.accessor(), top.x(), vineY, top.z()) == Blocks.VINE;
                    vineY--) {
                refreshConnection(top.x(), vineY, top.z());
            }
        }
    }

    private void queueVineColumnsAt(
            LinkedHashSet<BlockPos> runs, Set<BlockPos> indexedCells,
            int x, int z, int changedY) {
        for (int seedY = Math.min(Blocks.MAX_Y, changedY + 1);
                seedY >= Math.max(Blocks.MIN_Y, changedY - 1); seedY--) {
            BlockPos seed = new BlockPos(x, seedY, z);
            if (indexedCells.contains(seed)) continue;
            if (residentBlockType(rt.accessor(), x, seedY, z) != Blocks.VINE) {
                indexedCells.add(seed);
                continue;
            }
            int topY = seedY;
            while (topY < Blocks.MAX_Y
                    && residentBlockType(rt.accessor(), x, topY + 1, z) == Blocks.VINE) {
                topY++;
            }
            int bottomY = seedY;
            while (bottomY > Blocks.MIN_Y
                    && residentBlockType(rt.accessor(), x, bottomY - 1, z) == Blocks.VINE) {
                bottomY--;
            }
            for (int vineY = bottomY; vineY <= topY; vineY++) {
                indexedCells.add(new BlockPos(x, vineY, z));
            }
            runs.add(new BlockPos(x, topY, z));
        }
    }

    private boolean refreshConnection(int x, int y, int z) {
        int block = residentBlockType(rt.accessor(), x, y, z);
        if (block == Blocks.CHORUS_PLANT) {
            // [VOID-END] 후렴 식물 6연결은 이웃이 정하는 파생 상태다(ChorusPlantBlock.updateShape: 살 수
            // 없는 식물은 연결을 고치지 않고 지지 연쇄가 부순다).
            int old = rt.blockStates().get(x, y, z, block);
            int state = com.gameexpert.engine.blocks.VoidEndBlockRules.refreshedChorusPlantState(old, x, y, z,
                    (qx, qy, qz) -> residentBlockType(rt.accessor(), qx, qy, qz));
            if (state == old) return false;
            rt.setBlockState(x, y, z, block, state);
            rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) block);
            return true;
        }
        if (block == Blocks.GLOW_LICHEN) {
            int old = rt.blockStates().get(x, y, z, block);
            int state = SupportRules.supportedGlowLichenFaces(old, x, y, z,
                    (qx, qy, qz) -> residentBlockType(rt.accessor(), qx, qy, qz));
            if (state == old) return false;
            rt.setBlockState(x, y, z, block, state);
            rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) block);
            return true;
        }
        if (block == Blocks.BELL) {
            // [BLOCK-SHAPES] BellBlock#updateShape: 벽 하나를 잃은 두 벽 종은 남은 벽 쪽 한 벽 종이 되고,
            // 반대편에 sturdy 면이 생긴 한 벽 종은 두 벽 종이 된다(정적판 refreshOneDerivedState 와 같다).
            int old = rt.blockStates().get(x, y, z, block);
            BuildingBlockRules.StateLookup world = stateLookup();
            int state = SupportRules.bellStateAfterNeighborChange(old, x, y, z,
                    world::block, (qx, qy, qz, id) -> world.state(qx, qy, qz, id));
            if (state == old) return false;
            rt.setBlockState(x, y, z, block, state);
            rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) block);
            return true;
        }
        if (Blocks.isChestShaped(block)) {
            int old = rt.blockStates().get(x, y, z, block);
            int state = BuildingBlockRules.chestStateAfterNeighborChange(
                    block, x, y, z, old, stateLookup());
            if (state != old) {
                rt.setBlockState(x, y, z, block, state);
                rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) block);
                return true;
            }
            return false;
        }
        // RailState topology (including powered T junctions) is owned by the signal engine.
        if (block == Blocks.RAIL || com.gameexpert.engine.redstone.RedstoneState.isRedstoneRail(block)) return false;

        if (block == Blocks.VINE) {
            int old = rt.blockStates().get(x, y, z, block);
            int state = vineStateAt(x, y, z);
            if (state != old) {
                rt.setBlockState(x, y, z, block, state);
                rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) block);
                return true;
            }
            return false;
        }
        if (Blocks.isFenceGate(block)) {
            int old = rt.blockStates().get(x, y, z, block);
            int facing = old & BuildingBlockRules.FACING_MASK;
            boolean eastWestFacing = (facing & 1) != 0;
            boolean inWall = eastWestFacing
                    ? BuildingBlockRules.isWall(residentBlockType(rt.accessor(), x, y, z - 1))
                            || BuildingBlockRules.isWall(residentBlockType(rt.accessor(), x, y, z + 1))
                    : BuildingBlockRules.isWall(residentBlockType(rt.accessor(), x - 1, y, z))
                            || BuildingBlockRules.isWall(residentBlockType(rt.accessor(), x + 1, y, z));
            int state = inWall ? old | BuildingBlockRules.GATE_IN_WALL
                    : old & ~BuildingBlockRules.GATE_IN_WALL;
            if (state != old) {
                rt.setBlockState(x, y, z, block, state);
                rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) block);
                return true;
            }
            return false;
        }
        if (!Blocks.isFence(block) && !BuildingBlockRules.isWall(block)
                && !BuildingBlockRules.isPane(block)) return false;
        int old = rt.blockStates().get(x, y, z, block);
        int state = BuildingBlockRules.connectionMask(block, x, y, z, stateLookup());
        if (Blocks.isCopperBars(block)) state |= old & COPPER_WATERLOGGED;
        if (state == old) return false;
        rt.setBlockState(x, y, z, block, state);
        rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) block);
        return true;
    }

    private BuildingBlockRules.StateLookup stateLookup() {
        return new BuildingBlockRules.StateLookup() {
            @Override
            public int block(int qx, int qy, int qz) {
                return residentBlockType(rt.accessor(), qx, qy, qz);
            }

            @Override
            public int state(int qx, int qy, int qz, int blockId) {
                return rt.blockStates().get(qx, qy, qz, blockId);
            }
        };
    }

    private int vineStateAt(int x, int y, int z) {
        return BuildingBlockRules.vineState(x, y, z, stateLookup());
    }

    private void refreshStairsAround(int x, int y, int z) {
        refreshStairs(List.of(new BlockPos(x, y, z)));
    }

    private static void enqueueStairCandidates(
            RevisionedPositionQueue queue, BlockPos changed, long revision) {
        int x = changed.x();
        int y = changed.y();
        int z = changed.z();
        queue.add(x, y, z, revision);
        queue.add(x, y, z - 1, revision);
        queue.add(x + 1, y, z, revision);
        queue.add(x, y, z + 1, revision);
        queue.add(x - 1, y, z, revision);
    }

    private void refreshStairs(List<BlockPos> changes) {
        RevisionedPositionQueue candidates = new RevisionedPositionQueue();
        long revision = 1;
        for (BlockPos changed : changes) enqueueStairCandidates(candidates, changed, revision);
        RevisionedPosition candidate;
        while ((candidate = candidates.take()) != null) {
            BlockPos position = candidate.position;
            if (!refreshStair(position.x(), position.y(), position.z())) continue;
            revision = Math.max(revision, candidate.revision) + 1;
            enqueueStairCandidates(candidates, position, revision);
        }
    }

    private boolean refreshStair(int x, int y, int z) {
        int block = residentBlockType(rt.accessor(), x, y, z);
        if (!BuildingBlockRules.isStairs(block)) return false;
        int old = rt.blockStates().get(x, y, z, block);
        int shape = BuildingBlockRules.stairShape(x, y, z, old,
                new BuildingBlockRules.StateLookup() {
                    @Override
                    public int block(int qx, int qy, int qz) {
                        return residentBlockType(rt.accessor(), qx, qy, qz);
                    }

                    @Override
                    public int state(int qx, int qy, int qz, int blockId) {
                        return rt.blockStates().get(qx, qy, qz, blockId);
                    }
                });
        int state = (old & 0x87) | shape << BuildingBlockRules.STAIR_SHAPE_SHIFT;
        if (state == old) return false;
        rt.setBlockState(x, y, z, block, state);
        rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) block);
        return true;
    }

    private void refreshDerivedStatesAround(int x, int y, int z) {
        refreshDerivedStatesAround(List.of(new BlockPos(x, y, z)));
    }

    private void refreshDerivedStatesAround(List<BlockPos> changes) {
        if (changes.isEmpty()) return;
        refreshConnections(changes);
        refreshStairs(changes);
        refreshSpeleothemRuns(changes);
    }

    private void refreshSpeleothemRuns(List<BlockPos> changes) {
        Set<BlockPos> indexedCells = new HashSet<>();
        LinkedHashMap<BlockPos, Boolean> runs = new LinkedHashMap<>();
        for (BlockPos changed : changes) {
            for (int y = changed.y() - 1; y <= changed.y() + 1; y++) {
                queueSpeleothemRunAt(
                        runs, indexedCells, changed.x(), y, changed.z());
            }
        }
        for (Map.Entry<BlockPos, Boolean> run : runs.entrySet()) {
            BlockPos root = run.getKey();
            int blockType = residentBlockType(rt.accessor(), root.x(), root.y(), root.z());
            if (!P6Rules.isSpeleothem(blockType)) continue;
            if (!run.getValue()) rt.runtimeFallingSpeleothems().admit(root.x(), root.y(), root.z(), blockType);
            // 기존 엔진 방향·수분 비트를 보존하며 이 run의 두께만 다시 계산한다.
            rt.runtimeFallingSpeleothems().refreshRun(root.x(), root.y(), root.z(), blockType, run.getValue());
        }
    }

    private void queueSpeleothemRunAt(
            LinkedHashMap<BlockPos, Boolean> runs, Set<BlockPos> indexedCells,
            int x, int y, int z) {
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return;
        BlockPos seed = new BlockPos(x, y, z);
        if (indexedCells.contains(seed)) return;
        int blockType = residentBlockType(rt.accessor(), x, y, z);
        if (!P6Rules.isSpeleothem(blockType)) {
            indexedCells.add(seed);
            return;
        }
        boolean upward = (rt.blockStates().get(x, y, z, blockType) & P6Rules.DRIPSTONE_UP) != 0;
        int direction = upward ? 1 : -1;
        int rootY = y;
        while (rootY - direction >= Blocks.MIN_Y && rootY - direction <= Blocks.MAX_Y
                && speleothemDirectionAt(blockType, x, rootY - direction, z, upward)) {
            rootY -= direction;
        }
        int tipY = y;
        while (tipY + direction >= Blocks.MIN_Y && tipY + direction <= Blocks.MAX_Y
                && speleothemDirectionAt(blockType, x, tipY + direction, z, upward)) {
            tipY += direction;
        }
        for (int runY = rootY; ; runY += direction) {
            indexedCells.add(new BlockPos(x, runY, z));
            if (runY == tipY) break;
        }
        runs.putIfAbsent(new BlockPos(x, rootY, z), upward);
    }

    private boolean speleothemDirectionAt(
            int blockType, int x, int y, int z, boolean upward) {
        return residentBlockType(rt.accessor(), x, y, z) == blockType
                && ((rt.blockStates().get(x, y, z, blockType) & P6Rules.DRIPSTONE_UP) != 0) == upward;
    }

    /** 현재 변경 좌표를 중력/지지 시스템에 연결하고, 지지 연쇄가 만든 변경도 다음 중력 틱에 남긴다. */
    private void processBlockNeighbors() {
        // applyChange가 tickBlockChanges를 추가 변경하므로 비어 있지 않을 때만 단계별 스냅샷을 전달한다.
        Map<BlockPos, Short> changes = rt.tickBlockChanges();
        if (!changes.isEmpty()) {
            rt.fallingBlockSystem().onBlocksChanged(new ArrayList<>(changes.keySet()));
        }
        if (rt.fallingBlockSystem().tick()) {
            log.warn("월드 {} 중력 블록 갱신이 최대 {}칸을 초과해 나머지를 다음 틱으로 이월했습니다.",
                    rt.worldId(), SupportRules.MAX_CASCADE_BLOCKS);
        }
        hardenConcretePowder();
        if (!changes.isEmpty()) {
            List<BlockPos> changed = new ArrayList<>(changes.keySet());
            refreshDerivedStatesAround(changed);
            refreshFireSupportAround(changed);
        }
        cascadeSupportLoss();
        // 낙하와 지지 연쇄가 새로 비운 칸 위의 중력 블록을 다음 틱 후보로 보존한다.
        if (!changes.isEmpty()) {
            rt.fallingBlockSystem().onBlocksChanged(new ArrayList<>(changes.keySet()));
        }
    }

    /** FireBlock#updateShape: 지지/인화성 이웃이 사라진 실제 FIRE 셀을 같은 틱에 제거한다. */
    private void refreshFireSupportAround(List<BlockPos> changes) {
        for (BlockPos changed : changes) {
            for (int[] direction : FIRE_NEIGHBORS) {
                randomTicks.onNeighborChanged(changed.x() + direction[0],
                        changed.y() + direction[1], changed.z() + direction[2]);
            }
        }
    }

    /**
     * [CONCRETE] 이번 틱에 바뀐 칸들이 만든 콘크리트 가루 경화를 적용한다.
     *
     * <p>낙하 착지·손 설치·물이 흘러옴 세 트리거를 각각 갈고리로 다는 대신, 이미 있는 합류점
     * 하나(이번 틱 변경 목록)를 훑는다. 셋 다 결국 이 목록에 칸을 남기기 때문이다 — 낙하는
     * {@code fluidSim::applyChange}, 설치는 blockEdit, 유체 확산은 {@code FluidSimulator} 가
     * 모두 같은 {@code tickBlockChanges} 를 채운다. {@link #processBlockNeighbors} 안에서
     * 중력 틱 <b>직후</b>에 도는 것도 그래서다: 이번 틱에 착지한 가루가 같은 틱에 굳는다.
     *
     * <p>쓰기는 {@code fluidSim.applyChange} 로 한다 — 방송·영속·이웃 유체 재활성화가 붙은
     * 단일 변경 경로이고, 그 쓰기가 다시 {@code tickBlockChanges} 에 들어가 뒤 단계(지지 연쇄·
     * 파생 상태 갱신)가 굳은 콘크리트를 정상적으로 본다. 그래서 반복 중 컬렉션이 커지므로
     * 좌표 목록은 먼저 스냅샷으로 뜬다.
     *
     * <p>합류점 하나로 접었기 때문에 <b>바닐라가 콜백마다 들고 다니던 두 사실</b>은 따로
     * 되돌려 준다({@link ConcreteRules.FallState}). 낙하 중인 칸은 바닐라라면 블록이 아니라
     * 엔티티고, 착지·설치는 "덮은 칸의 상태" 를 {@code shouldSolidify} 에 넘긴다. 낙하 쪽은
     * {@link FallingBlockSystem#fallState()} 가, 손 설치 쪽은 {@link #placedOver} 가 안다.
     */
    private void hardenConcretePowder() {
        Map<BlockPos, Short> changes = rt.tickBlockChanges();
        try {
            if (changes.isEmpty()) return;
            List<BlockPos> snapshot = new ArrayList<>(changes.keySet());
            FluidSimulator fluidSim = rt.fluidSim();
            ConcreteRules.BlockLookup lookup =
                    (x, y, z) -> WorldTickLoop.residentBlockType(rt.accessor(), x, y, z);
            ConcreteRules.FallState fall = combinedFallState();
            for (BlockPos changed : snapshot) {
                ConcreteRules.hardenAround(lookup, fluidSim::applyChange, fall,
                        changed.x(), changed.y(), changed.z());
            }
        } finally {
            // 설치 기록은 이번 틱 한정이다. 조기 반환 경로에서도 반드시 비운다.
            placedOver.clear();
        }
    }

    /** 낙하가 아는 사실과 이번 틱 설치가 아는 사실을 하나의 포트로 합친다. */
    private ConcreteRules.FallState combinedFallState() {
        ConcreteRules.FallState falling = rt.fallingBlockSystem().fallState();
        if (placedOver.isEmpty()) return falling;
        return new ConcreteRules.FallState() {
            @Override
            public boolean airborne(int x, int y, int z) {
                // 손으로 놓은 칸은 엔티티였던 적이 없다 — 낙하 판정만 따른다.
                return falling.airborne(x, y, z);
            }

            @Override
            public int replaced(int x, int y, int z) {
                int placed = placedOver.getOrDefault(new BlockPos(x, y, z), Blocks.AIR);
                return placed != Blocks.AIR ? placed : falling.replaced(x, y, z);
            }
        };
    }

    /** 이번 틱 변경 좌표의 이웃부터 지지 상실 블록을 제한적으로 연쇄 제거한다. */
    private void cascadeSupportLoss() {
        Map<BlockPos, Short> changes = rt.tickBlockChanges();
        List<BlockPos> initialChanges = changes.isEmpty()
                ? List.of()
                : new ArrayList<>(changes.keySet());
        if (initialChanges.isEmpty() && pendingSupportChecks.isEmpty()) {
            return;
        }
        boolean capped = cascadeUnsupportedBlocks(initialChanges,
                (qx, qy, qz) -> residentBlockType(rt.accessor(), qx, qy, qz),
                (x, y, z, blockId) -> rt.blockState(x, y, z, blockId),
                (x, y, z, blockId, state) -> {
                    rt.setBlockState(x, y, z, blockId, state);
                    rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) blockId);
                },
                (x, y, z) -> {
                    int removed = residentBlockType(rt.accessor(), x, y, z);
                    int removedState = rt.blockState(x, y, z, removed);
                    boolean pairedDoor = Blocks.isDoor(removed)
                            && (residentBlockType(rt.accessor(), x, y - 1, z) == removed
                            || residentBlockType(rt.accessor(), x, y + 1, z) == removed);
                    boolean pairedBed = false;
                    if (Blocks.isBed(removed)) {
                        pairedBed = residentBlockType(rt.accessor(),
                                BuildingBlockRules.bedOtherX(x, removedState), y,
                                BuildingBlockRules.bedOtherZ(z, removedState)) == removed;
                    }
                    CropRules.Rule crop = CropRules.forCrop(removed);
                    if (crop != null) {
                        rt.itemSystem().spawnCropDrops(
                                crop, removedState, x + 0.5, y + 0.5, z + 0.5);
                    }
                    rt.fluidSim().applyChange(x, y, z,
                            blockAfterRemovalAt(x, y, z, removed, removedState));
                    refreshDerivedStatesAround(x, y, z);
                    if (removed != Blocks.AIR
                            && (!Blocks.isDoor(removed) || pairedDoor)
                            && (!Blocks.isBed(removed) || pairedBed)) {
                        broadcastWorldSound("block_break", x, y, z, (short) removed);
                    }
                },
                (item, x, y, z) -> {
                    // [VOID-END] 후렴과는 후렴 식물만의 지지 상실 드랍이다(Blocks.classify). 바닐라
                    // loot_table/blocks/chorus_plant 의 50% 를 시드·좌표 결정적 난수로 굴린다(정적판 같은 식).
                    int drop = item == Blocks.CHORUS_FRUIT
                            ? com.gameexpert.engine.blocks.VoidEndBlockRules.chorusPlantSupportLossDrop(
                                    rt.seed(), x, y, z)
                            : item;
                    if (drop == Blocks.AIR) return;
                    rt.itemSystem().spawnDrop((short) drop, 1, x + 0.5, y + 0.5, z + 0.5);
                }, pendingSupportChecks);
        if (capped) {
            log.warn("월드 {} 블록 지지 연쇄가 최대 {}칸을 초과해 나머지를 다음 틱으로 이월했습니다.",
                    rt.worldId(), SupportRules.MAX_CASCADE_BLOCKS);
        }
    }

    /** 환경 단계는 이웃 단계보다 뒤이므로, 기반 변경의 의존 후보를 다음 틱까지 보존한다. */
    private void queueEnvironmentSupportChecks(List<BlockPos> changes) {
        if (changes.isEmpty()) return;
        Set<BlockPos> queued = new HashSet<>(pendingSupportChecks);
        for (BlockPos changed : changes) {
            enqueueDependents(
                    changed.x(), changed.y(), changed.z(),
                    pendingSupportChecks, queued);
        }
    }

    @FunctionalInterface
    interface BlockRemoval {
        void remove(int x, int y, int z);
    }

    @FunctionalInterface
    interface SupportDrop {
        void spawn(int itemType, int x, int y, int z);
    }

    @FunctionalInterface
    interface SupportRestate {
        void set(int x, int y, int z, int blockId, int state);
    }

    /**
     * 변경 좌표의 6방향 이웃과 사탕수수 수원 의존 후보를 시작점으로 하는 순수 연쇄 오케스트레이션.
     * 제거 콜백은 조회 뷰에도 즉시 반영되어야 한다.
     *
     * @return 아직 검사할 고유 후보가 남은 상태에서 상한에 도달했으면 {@code true}
     */
    static boolean cascadeUnsupportedBlocks(Iterable<BlockPos> changedPositions,
            SupportRules.BlockLookup lookup, BlockRemoval removal, SupportDrop drops) {
        return cascadeUnsupportedBlocks(changedPositions, lookup,
                (x, y, z, blockId) -> 0, removal, drops, new ArrayDeque<>());
    }

    static boolean cascadeUnsupportedBlocks(Iterable<BlockPos> changedPositions,
            SupportRules.BlockLookup lookup, BlockRemoval removal, SupportDrop drops,
            ArrayDeque<BlockPos> carryover) {
        return cascadeUnsupportedBlocks(changedPositions, lookup,
                (x, y, z, blockId) -> 0, removal, drops, carryover);
    }

    static boolean cascadeUnsupportedBlocks(Iterable<BlockPos> changedPositions,
            SupportRules.BlockLookup lookup, SupportRules.StateLookup states,
            BlockRemoval removal, SupportDrop drops, ArrayDeque<BlockPos> carryover) {
        return cascadeUnsupportedBlocks(changedPositions, lookup, states,
                (x, y, z, blockId, state) -> { }, removal, drops, carryover);
    }

    static boolean cascadeUnsupportedBlocks(Iterable<BlockPos> changedPositions,
            SupportRules.BlockLookup lookup, SupportRules.StateLookup states,
            SupportRestate restate, BlockRemoval removal, SupportDrop drops,
            ArrayDeque<BlockPos> carryover) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> queued = new HashSet<>();
        while (!carryover.isEmpty()) {
            BlockPos pending = carryover.removeFirst();
            if (queued.add(pending)) queue.addLast(pending);
        }
        for (BlockPos changed : changedPositions) {
            enqueueDependents(changed.x(), changed.y(), changed.z(), queue, queued);
        }

        int examined = 0;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            queued.remove(pos);
            int blockId = lookup.getBlock(pos.x(), pos.y(), pos.z());
            if (!SupportRules.requiresSupport(blockId)) {
                continue;
            }
            if (examined >= SupportRules.MAX_CASCADE_BLOCKS) {
                carryover.addLast(pos);
                carryover.addAll(queue);
                return true;
            }
            examined++;
            int state = states.getState(pos.x(), pos.y(), pos.z(), blockId);
            if (blockId == Blocks.GLOW_LICHEN) {
                int supportedFaces = SupportRules.supportedGlowLichenFaces(
                        state, pos.x(), pos.y(), pos.z(), lookup);
                if (supportedFaces != state) {
                    restate.set(pos.x(), pos.y(), pos.z(), blockId, supportedFaces);
                    state = supportedFaces;
                }
            }
            if (P6Rules.isSpeleothem(blockId)) {
                // Downward support loss belongs exclusively to the durable +2 tick owner.
                if ((state & P6Rules.DRIPSTONE_UP) == 0) continue;
                int below = lookup.getBlock(pos.x(), pos.y() - 1, pos.z());
                if (below == UNAVAILABLE_BLOCK) continue;
                int belowState = states.getState(pos.x(), pos.y() - 1, pos.z(), below);
                if (BlockFaceSturdiness.isFaceSturdy(below, belowState, BlockFaceSturdiness.UP, BlockFaceSturdiness.FULL)
                        || below == blockId && (belowState & P6Rules.DRIPSTONE_UP) != 0) continue;
            } else if (SupportRules.isSupported(blockId, state, pos.x(), pos.y(), pos.z(), lookup, states)) continue;

            removal.remove(pos.x(), pos.y(), pos.z());
            // 2칸 문은 하단 지지를 잃으면 pair를 한 번에 제거하고 문 아이템도 하나만 드랍한다.
            if (Blocks.isDoor(blockId)) {
                int otherY = lookup.getBlock(pos.x(), pos.y() + 1, pos.z()) == blockId
                        ? pos.y() + 1
                        : lookup.getBlock(pos.x(), pos.y() - 1, pos.z()) == blockId
                                ? pos.y() - 1 : Integer.MIN_VALUE;
                if (otherY != Integer.MIN_VALUE) {
                    removal.remove(pos.x(), otherY, pos.z());
                    enqueueDependents(pos.x(), otherY, pos.z(), queue, queued);
                }
                drops.spawn(blockId, pos.x(), pos.y(), pos.z());
                enqueueDependents(pos.x(), pos.y(), pos.z(), queue, queued);
                continue;
            }
            if (Blocks.isBed(blockId)) {
                int otherX = BuildingBlockRules.bedOtherX(pos.x(), state);
                int otherZ = BuildingBlockRules.bedOtherZ(pos.z(), state);
                if (lookup.getBlock(otherX, pos.y(), otherZ) == blockId) {
                    int otherState = states.getState(otherX, pos.y(), otherZ, blockId);
                    if (BuildingBlockRules.matchingBedStates(state, otherState)) {
                        removal.remove(otherX, pos.y(), otherZ);
                        enqueueDependents(otherX, pos.y(), otherZ, queue, queued);
                    }
                }
                drops.spawn(blockId, pos.x(), pos.y(), pos.z());
                enqueueDependents(pos.x(), pos.y(), pos.z(), queue, queued);
                continue;
            }
            // [PITCHER] The two halves leave together (DoublePlantBlock.updateShape) and only the lower half's
            // loot drops, once: pod or pitcher plant by the crop's age, the plant itself.
            if (PitcherRules.isPitcher(blockId)) {
                boolean upper = PitcherRules.isUpper(state);
                int otherY = upper ? pos.y() - 1 : pos.y() + 1;
                int lowerState = upper ? -1 : state;
                if (lookup.getBlock(pos.x(), otherY, pos.z()) == blockId) {
                    int otherState = states.getState(pos.x(), otherY, pos.z(), blockId);
                    if (PitcherRules.isUpper(otherState) != upper) {
                        removal.remove(pos.x(), otherY, pos.z());
                        enqueueDependents(pos.x(), otherY, pos.z(), queue, queued);
                        if (upper) lowerState = otherState;
                    }
                }
                if (lowerState >= 0) {
                    drops.spawn(blockId == Blocks.PITCHER_CROP
                            ? PitcherRules.cropLootItem(lowerState) : Blocks.PITCHER_PLANT,
                            pos.x(), pos.y(), pos.z());
                }
                enqueueDependents(pos.x(), pos.y(), pos.z(), queue, queued);
                continue;
            }
            int drop = SupportRules.dropForSupportLoss(blockId);
            if (drop != Blocks.AIR) {
                int count = CandleRules.isCandle(blockId) ? CandleRules.count(state) : 1;
                for (int index = 0; index < count; index++) {
                    drops.spawn(drop, pos.x(), pos.y(), pos.z());
                }
            }
            // 영구 visited가 아니라 현재 queue만 중복 제거한다. 앞서 지지됨으로 판정한 블록도
            // 같은 연쇄에서 그 지지 블록이 나중에 제거되면 다시 큐에 들어가 재평가되어야 한다.
            enqueueDependents(pos.x(), pos.y(), pos.z(), queue, queued);
        }
        return false;
    }

    private static void enqueueDependents(int x, int y, int z,
            ArrayDeque<BlockPos> queue, Set<BlockPos> queued) {
        enqueue(x - 1, y, z, queue, queued);
        enqueue(x + 1, y, z, queue, queued);
        enqueue(x, y - 1, z, queue, queued);
        enqueue(x, y + 1, z, queue, queued);
        enqueue(x, y, z - 1, queue, queued);
        enqueue(x, y, z + 1, queue, queued);

        // 기반 사탕수수는 한 칸 아래 모래와 같은 높이의 수원을 보므로 수원에서 대각선 위에 있다.
        enqueue(x - 1, y + 1, z, queue, queued);
        enqueue(x + 1, y + 1, z, queue, queued);
        enqueue(x, y + 1, z - 1, queue, queued);
        enqueue(x, y + 1, z + 1, queue, queued);
    }

    private static void enqueue(int x, int y, int z,
            ArrayDeque<BlockPos> queue, Set<BlockPos> queued) {
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) {
            return;
        }
        BlockPos candidate = new BlockPos(x, y, z);
        if (queued.add(candidate)) {
            queue.addLast(candidate);
        }
    }

    // ── ① interact: 뼛가루 → 양동이 → 라이터 → 문/침대. 그 외 무시 ──
    private void applyInteract(PlayerAction.Interact interact) {
        PlayerTickState player = rt.players().get(interact.nickname());
        if (player == null || player.isDead()) {
            return;
        }
        // Interactions may consume tools or container contents, so reject a durable Copper source
        // reservation before even capturing/reading the hand used by this action.
        if (copperSettlementCells.contains(interact.x(), interact.y(), interact.z())
                || rt.runtimeFallingSpeleothems().protectsCell(interact.x(), interact.y(), interact.z())) return;
        PlayerInventory.HandRef hand = player.inventory().capture(inventoryHand(interact.hand()));
        short selected = player.inventory().stack(hand).itemType();
        int current = residentBlockType(rt.accessor(), interact.x(), interact.y(), interact.z());
        if (current == UNAVAILABLE_BLOCK) return;
        if (current == Blocks.END_PORTAL_FRAME && selected == PlayerInventory.EYE_OF_ENDER) {
            applyEnderEyeInsert(player, hand, interact.x(), interact.y(), interact.z());
            return;
        }
        // [DRAGON] 드래곤 알을 쓰면 순간이동한다(DragonEggBlock.useWithoutItem, 손 아이템 무관).
        if (current == Blocks.DRAGON_EGG) {
            if (InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                    interact.x(), interact.y(), interact.z())) {
                rt.dragonFight().teleportEgg(interact.x(), interact.y(), interact.z());
            }
            return;
        }
        // [DRAGON] 빈 병은 먼저 곁의 드래곤 숨결 구름을 뜬다(BottleItem.use, 반지름 −0.5 · 드래곤의 숨결).
        if (selected == (short) Blocks.GLASS_BOTTLE && rt.mobSystem().bottleDragonBreath(player)) {
            if (fillBottleWith(player.inventory(), hand, (short) Blocks.DRAGON_BREATH)) {
                sendTo(player, inventoryMessage(player));
                broadcastWorldSound("bottle_fill_dragonbreath", (int) Math.floor(player.x()),
                        (int) Math.floor(player.y()), (int) Math.floor(player.z()), (short) 0);
            }
            return;
        }
        if (current == Blocks.POPLAR_SIGN || current == Blocks.POPLAR_HANGING_SIGN) {
            if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                    interact.x(), interact.y(), interact.z())) return;
            com.gameexpert.sign.dto.SignBlockData sign = rt.signAt(
                    interact.x(), interact.y(), interact.z());
            if (sign != null) sendTo(player,
                    new com.gameexpert.ws.dto.WsMessages.SignOpen(WorldRuntime.toWireSign(sign), interact.requestId()));
            return;
        }
        if (!player.crouching() && InteractRules.withinReach(player.x(), player.y(), player.z(),
                player.crouching(), interact.x(), interact.y(), interact.z())
                && redstone.engine().use(interact.x(), interact.y(), interact.z(), rt.clock().gameTimeMcTicks())) {
            commitRedstone();
            return;
        }

        if (current == Blocks.CAULDRON) {
            if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                    interact.x(), interact.y(), interact.z())) return;
            int state = rt.blockStates().get(
                    interact.x(), interact.y(), interact.z(), Blocks.CAULDRON);
            PlayerInventory.StackSnapshot held = player.inventory().stack(hand);
            PlayerInventory.StackSnapshot washed = CauldronRules.wash(state, held);
            if (washed != null) {
                int nextState = CauldronRules.lowerOneLevel(state);
                settleCauldronMutation(player, hand, held, washed, nextState,
                        interact.x(), interact.y(), interact.z(), "bottle_empty");
                return;
            }
            CauldronRules.Plan plan = CauldronRules.use(state, selected);
            if (plan == null) return;
            String sound = switch (selected) {
                case PlayerInventory.GLASS_BOTTLE -> "bottle_fill";
                case PlayerInventory.WATER_BOTTLE -> "bottle_empty";
                case PlayerInventory.BUCKET -> CauldronRules.kind(state) == CauldronRules.LAVA
                        ? "bucket_fill_lava" : "bucket_fill_water";
                case PlayerInventory.WATER_BUCKET -> "bucket_empty_water";
                case PlayerInventory.LAVA_BUCKET -> "bucket_empty_lava";
                // The current worldSound contract has no separate powder-snow bucket
                // semantic; keep the event in the registered empty-bucket family until
                // that sound lane is added end-to-end.
                default -> "bucket_empty_water";
            };
            PlayerInventory.StackSnapshot replacement = new PlayerInventory.StackSnapshot(
                    plan.replacement(), 1,
                    PlayerInventory.isDurable(plan.replacement())
                            ? PlayerInventory.initialDurability(plan.replacement()) : 0,
                    EnchantmentRules.EMPTY_ENCHANTMENTS, 0, 0, null, null);
            settleCauldronMutation(player, hand, held, replacement, plan.state(),
                    interact.x(), interact.y(), interact.z(), sound);
            return;
        }
        if ((current == Blocks.BEE_NEST || current == Blocks.BEEHIVE)
                && applyBeeHiveHarvest(player, hand, interact, current, selected)) return;
        if (current == Blocks.LECTERN) {
            applyLecternInteract(player, hand, interact);
            return;
        }
        // [JUKEBOX] 음반 넣기(JukeboxPlayable.tryInsertIntoJukebox) · 꺼내기(useWithoutItem).
        if (current == Blocks.JUKEBOX
                && InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                        interact.x(), interact.y(), interact.z())
                && applyJukeboxInteract(player, hand, interact.x(), interact.y(), interact.z(),
                        selected)) return;
        if (Blocks.isDecoratedPot(current)) {
            // DecoratedPotBlock is used with the main hand only: an unusable stack returns
            // TRY_WITH_EMPTY_HAND, which ServerPlayerGameMode answers with useWithoutItem on the
            // same (main) hand, so the off hand never reaches the pot.
            if (hand.hand() == PlayerInventory.Hand.MAIN) {
                applyDecoratedPotUse(player, hand, interact.x(), interact.y(), interact.z(),
                        current, true);
            }
            return;
        }
        if (applyPrimaryBlockInteract(player, interact, current)) return;
        if (current == Blocks.VAULT) {
            applyVaultInteract(player, interact, selected);
            return;
        }
        if (selected == PlayerInventory.COD_BUCKET
                || selected == PlayerInventory.SALMON_BUCKET
                || selected == PlayerInventory.TROPICAL_FISH_BUCKET
                || selected == PlayerInventory.AXOLOTL_BUCKET
                || selected == PlayerInventory.TADPOLE_BUCKET
                || selected == PlayerInventory.PUFFERFISH_BUCKET
                || selected == PlayerInventory.SULFUR_CUBE_BUCKET) {
            applyFishBucketInteract(player, hand, interact.x(), interact.y(), interact.z(),
                    selected, current);
            return;
        }
        // [ARCHAEOLOGY] 붓질 한 획. 클라가 홀드 중 0.5 초마다 같은 interact 를 보내므로
        // 프로토콜에 새 메시지를 만들지 않는다 — 진행도는 <b>블록 상태</b>에 실려 있고 이
        // 분기는 그 바이트를 하나 올릴 뿐이라, 획이 유실돼도 다음 획이 이어서 진행한다.
        // 열 번째 획에서 블록이 일반 모래·자갈이 되고 전용 전리품표가 한 번 굴러 배출물이
        // 하나 나온다. 붓 내구는 [B] 그대로 <b>블록 하나당 1</b> 이라 완료 시점에만 깎는다
        // (아르마딜로 솔질의 16 은 MobSystem 이 따로 소유한다).
        if (selected == PlayerInventory.BRUSH && Blocks.isBrushable(current)) {
            if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                    interact.x(), interact.y(), interact.z())) {
                return;
            }
            int state = rt.blockStates().get(interact.x(), interact.y(), interact.z(), current);
            ArchaeologyRules.BrushStroke stroke = ArchaeologyRules.brushStroke(current, state);
            if (!stroke.brushed()) return;
            if (!stroke.completed()) {
                if (!rt.advanceArchaeologyBrushTarget(interact.x(), interact.y(), interact.z(),
                        stroke.blockType(), stroke.state())) return;
                rt.tickBlockChanges().put(
                        new BlockPos(interact.x(), interact.y(), interact.z()), (short) current);
                broadcastWorldSound("block_hit", interact.x(), interact.y(), interact.z(),
                        (short) current);
                return;
            }
            WorldRuntime.ArchaeologyLootSettlement archaeology =
                    rt.consumeArchaeologyLoot(interact.x(), interact.y(), interact.z());
            if (archaeology == null) return;
            rt.fluidSim().applyChange(interact.x(), interact.y(), interact.z(), stroke.blockType());
            rt.setBlockState(interact.x(), interact.y(), interact.z(), stroke.blockType(), 0);
            rt.tickBlockChanges().put(
                    new BlockPos(interact.x(), interact.y(), interact.z()), (short) stroke.blockType());
            PlayerInventory.StackSnapshot brushBefore = player.inventory().stack(hand);
            for (int wear = 0; wear < ArchaeologyRules.BRUSH_BLOCK_DURABILITY_COST; wear++) {
                PlayerInventory.HandRef currentHand = player.inventory().capture(hand.hand());
                player.inventory().degrade(currentHand);
            }
            recordBreakIfGone(player, player.inventory().capture(hand.hand()), brushBefore);
            sendTo(player, inventoryMessage(player));
            broadcastWorldSound("block_break", interact.x(), interact.y(), interact.z(),
                    (short) current);
            return;
        }
        if (selected == (short) Blocks.GLASS_BOTTLE) {
            if (current == WATER_SOURCE && InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                    interact.x(), interact.y(), interact.z())
                    && fillGlassBottle(player.inventory(), hand)) {
                sendTo(player, inventoryMessage(player));
                broadcastWorldSound("bottle_fill", interact.x(), interact.y(), interact.z(),
                        (short) current);
            }
            return;
        }
        if (EnchantmentRules.isHoeItem(selected)
                && (current == Blocks.DIRT || current == Blocks.GRASS || current == Blocks.ROOTED_DIRT
                        || current == Blocks.COARSE_DIRT)) {
            if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                    interact.x(), interact.y(), interact.z())
                    || residentBlockType(rt.accessor(), interact.x(), interact.y() + 1, interact.z()) != AIR) return;
            int tilled = current == Blocks.ROOTED_DIRT || current == Blocks.COARSE_DIRT
                    ? Blocks.DIRT : Blocks.FARMLAND;
            rt.fluidSim().applyChange(interact.x(), interact.y(), interact.z(), tilled);
            if (current == Blocks.ROOTED_DIRT) {
                rt.itemSystem().spawnDrop((short) Blocks.HANGING_ROOTS, 1,
                        interact.x() + 0.5, interact.y() + 0.5, interact.z() + 0.5);
            }
            int moisture = randomTicks.initialFarmlandState(interact.x(), interact.y(), interact.z());
            if (tilled == Blocks.FARMLAND) {
                rt.setBlockState(interact.x(), interact.y(), interact.z(), Blocks.FARMLAND, moisture);
            }
            rt.tickBlockChanges().put(new BlockPos(interact.x(), interact.y(), interact.z()), (short) tilled);
            degradeAndRecord(player, hand);
            sendTo(player, inventoryMessage(player));
            broadcastWorldSound("till", interact.x(), interact.y(), interact.z(), (short) current);
            return;
        }
        int shovelResult = isShovel(selected) ? P1Rules.shovelResult(current) : current;
        if (shovelResult != current) {
            if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                    interact.x(), interact.y(), interact.z())
                    || residentBlockType(rt.accessor(), interact.x(), interact.y() + 1, interact.z()) != AIR) return;
            rt.fluidSim().applyChange(interact.x(), interact.y(), interact.z(), shovelResult);
            degradeAndRecord(player, hand);
            sendTo(player, inventoryMessage(player));
            broadcastWorldSound("shovel", interact.x(), interact.y(), interact.z(), (short) current);
            return;
        }
        // [STRIPPED-LOG] 도끼 우클릭 → 통나무 벗기기(MC AxeItem STRIP_BLOCK). 삽·괭이와 같은
        // 블록 변환 상호작용 계약이다: 도달 판정 → 블록 교체 → 도끼 내구도 1 소모 → 인벤 동기화.
        // 삽·괭이와 달리 위 칸이 비어 있을 필요는 없다(바닐라도 지지·상단 조건이 없다).
        // 이미 벗긴 원목·통나무가 아닌 블록은 strippedLogFor 가 입력을 그대로 돌려주므로
        // 여기서 조용히 지나간다.
        if (EnchantmentRules.isAxeItem(selected)) {
            int state = rt.blockStates().get(interact.x(), interact.y(), interact.z(), current);
            int stripped = Blocks.strippedLogFor(current, state);
            if (stripped != current) {
                if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                        interact.x(), interact.y(), interact.z())) return;
                rt.fluidSim().applyChange(interact.x(), interact.y(), interact.z(), stripped);
                // 축이 ID 로 옮겨갔으므로 state 는 0 으로 되돌린다(state 축 수종의 잔여 비트 제거).
                rt.setBlockState(interact.x(), interact.y(), interact.z(), stripped, 0);
                rt.tickBlockChanges().put(new BlockPos(interact.x(), interact.y(), interact.z()),
                        (short) stripped);
                degradeAndRecord(player, hand);
                sendTo(player, inventoryMessage(player));
                // 새 worldSound 종류를 만들지 않는다 — 벗기기는 그 셀에 목재 블록이 새로
                // 놓인 것과 같은 물리 사건이라 기존 block_place 가 벗긴 원목의 WOOD 음향
                // 프로파일을 그대로 낸다(AGENTS 39c 의 "재질/행동 정체성이 같은 비음성 효과 공유").
                broadcastWorldSound("block_place", interact.x(), interact.y(), interact.z(),
                        (short) stripped);
                return;
            }
            // [COPPER] 도끼 우클릭 → 구리 긁기(MC AxeItem: 밀랍 제거 / 산화 -1). 벗기기와 같은
            // 블록 변환 상호작용 계약이다 — 도달 판정 → 블록 교체 → 도끼 내구도 1 소모 →
            // 인벤 동기화. 대상이 아니면 scrapePlan이 null이라 조용히 지나간다.
            int scrapeState = rt.blockStates().get(
                    interact.x(), interact.y(), interact.z(), current);
            CopperGolemOriginRules.RevivalPlan revival = CopperGolemOriginRules.revivalPlan(
                    current, interact.x(), interact.y(), interact.z());
            if (revival != null) {
                if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                        interact.x(), interact.y(), interact.z())) return;
                String key = "copper-golem-revival:" + revival.statueX() + ':'
                        + revival.statueY() + ':' + revival.statueZ();
                pendingCopperSettlementSubmissions.putIfAbsent(key,
                        () -> submitCopperRevival(key, revival, player, hand, current));
                pendingCopperSettlementSubmissions.get(key).run();
                return;
            }
            CopperAgeRules.CopperChange scrape = CopperAgeRules.scrapePlan(current, scrapeState);
            if (scrape != null) {
                if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                        interact.x(), interact.y(), interact.z())) return;
                rt.fluidSim().applyChange(
                        interact.x(), interact.y(), interact.z(), scrape.blockId());
                // 산화·밀랍은 형상을 바꾸지 않으므로 문/다락문/반 블록의 state 를 그대로 옮긴다.
                rt.setBlockState(interact.x(), interact.y(), interact.z(),
                        scrape.blockId(), scrape.state());
                rt.tickBlockChanges().put(new BlockPos(interact.x(), interact.y(), interact.z()),
                        (short) scrape.blockId());
                degradeAndRecord(player, hand);
                sendTo(player, inventoryMessage(player));
                // 새 worldSound 종류를 만들지 않는다 — 긁기는 그 셀의 블록이 같은 재질의 다른
                // 가공으로 바뀐 사건이라 벗기기와 같이 block_place 를 쓴다(AGENTS 39c).
                broadcastWorldSound("block_place", interact.x(), interact.y(), interact.z(),
                        (short) scrape.blockId());
                return;
            }
        }
        // [COPPER] 밀랍 우클릭 → 밀랍 도포(MC HoneycombItem). 산화 단계를 보존한 밀랍 대응으로
        // 바꾸고 밀랍 1 개를 소모한다. 도구가 아니라 소모품이라 내구도 대신 개수를 줄인다.
        if (selected == PlayerInventory.HONEYCOMB) {
            int waxState = rt.blockStates().get(
                    interact.x(), interact.y(), interact.z(), current);
            CopperAgeRules.CopperChange wax = CopperAgeRules.waxPlan(current, waxState);
            if (wax != null) {
                if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                        interact.x(), interact.y(), interact.z())) return;
                if (!InventoryRules.consumeOne(
                        player.inventory(), hand, PlayerInventory.HONEYCOMB)) return;
                rt.fluidSim().applyChange(interact.x(), interact.y(), interact.z(), wax.blockId());
                rt.setBlockState(interact.x(), interact.y(), interact.z(), wax.blockId(), wax.state());
                rt.tickBlockChanges().put(new BlockPos(interact.x(), interact.y(), interact.z()),
                        (short) wax.blockId());
                sendTo(player, inventoryMessage(player));
                broadcastWorldSound("block_place", interact.x(), interact.y(), interact.z(),
                        (short) wax.blockId());
            }
            return;
        }
        if (isCopperStatue(current)) {
            if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                    interact.x(), interact.y(), interact.z())) return;
            int state = rt.blockStates().get(interact.x(), interact.y(), interact.z(), current);
            int pose = ((state >>> 2) + 1) & 3;
            int cycled = (state & ~0x0c) | pose << 2;
            rt.setBlockState(interact.x(), interact.y(), interact.z(), current, cycled);
            rt.tickBlockChanges().put(new BlockPos(interact.x(), interact.y(), interact.z()),
                    (short) current);
            broadcastWorldSound("block_place", interact.x(), interact.y(), interact.z(),
                    (short) current);
            return;
        }
        // [COPPER] 구리 전구 우클릭 토글 — **사용자 확정 divergence**. 바닐라는 레드스톤 신호로
        // 켜지지만 이 저장소에는 레드스톤 회로가 없어 전구가 영영 꺼진 채로 남는다. 그래서
        // 우클릭을 토글 입력으로 쓴다. 광량은 바닐라 CopperBulbBlock 그대로 15/12/8/4 다.
        //
        // 도끼·밀랍 분기보다 **뒤**에 둔다 — 그러지 않으면 전구를 긁거나 밀랍을 바를 수 없다
        // (바닐라 전구에는 use 동작이 없어 이런 우선순위 문제 자체가 없다).
        if (Blocks.isCopperBulb(current)) {
            int toggled = Blocks.toggledCopperBulb(current);
            if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                    interact.x(), interact.y(), interact.z())) return;
            rt.fluidSim().applyChange(interact.x(), interact.y(), interact.z(), toggled);
            rt.tickBlockChanges().put(new BlockPos(interact.x(), interact.y(), interact.z()),
                    (short) toggled);
            broadcastWorldSound("block_place", interact.x(), interact.y(), interact.z(),
                    (short) toggled);
            return;
        }
        int bottleResult = selected == (short) Blocks.WATER_BOTTLE
                ? P1Rules.waterBottleResult(current) : current;
        if (bottleResult != current) {
            if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                    interact.x(), interact.y(), interact.z())
                    || !player.inventory().replaceSingle(hand,
                            (short) Blocks.WATER_BOTTLE, (short) Blocks.GLASS_BOTTLE)) return;
            rt.fluidSim().applyChange(interact.x(), interact.y(), interact.z(), bottleResult);
            sendTo(player, inventoryMessage(player));
            broadcastWorldSound("bottle_empty", interact.x(), interact.y(), interact.z(), (short) current);
            broadcastSplashParticles(interact.x(), interact.y(), interact.z());
            return;
        }
        CropRules.Rule seedRule = CropRules.forSeed(selected);
        if (seedRule != null && current == Blocks.FARMLAND) {
            int cropY = interact.y() + 1;
            if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                    interact.x(), interact.y(), interact.z())
                    || cropY > Blocks.MAX_Y || residentBlockType(rt.accessor(), interact.x(), cropY, interact.z()) != AIR) return;
            if (!InventoryRules.consumeOne(player.inventory(), hand, seedRule.seedItem())) return;
            rt.fluidSim().applyChange(interact.x(), cropY, interact.z(), seedRule.cropBlock());
            sendTo(player, inventoryMessage(player));
            broadcastWorldSound("plant", interact.x(), cropY, interact.z(), (short) seedRule.cropBlock());
            return;
        }
        // [COMPOSTER] 퇴비통 우클릭. 근거 · divergence 는 ComposterRules 와
        // docs/research/mc-composter-dye.md §1 이 소유한다. 뼛가루 살포 분기보다 **앞**에
        // 두는 이유는 없다(뼛가루는 퇴비 대상이 아니라 두 분기가 겹치지 않는다) — 다만
        // 퇴비 대상 아이템 중 상당수가 뒤쪽 설치 분기에도 걸리므로 위치는 여기여야 한다.
        if (current == Blocks.COMPOSTER) {
            if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                    interact.x(), interact.y(), interact.z())) return;
            applyComposterInteract(player, hand, interact.x(), interact.y(), interact.z(), selected);
            return;
        }
        if (selected == PlayerInventory.BONE_MEAL
                && (isBoneMealTarget(current,
                        rt.blockStates().get(interact.x(), interact.y(), interact.z(), current))
                    || randomTicks.isPackBoneMealTarget(interact.x(), interact.y(), interact.z()))) {
            if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                    interact.x(), interact.y(), interact.z())) return;
            // [PITCHER] PitcherCropBlock.isValidBonemealTarget is canGrow: a blocked or dark crop keeps the meal.
            boolean applicable = current != Blocks.BAMBOO && current != Blocks.PITCHER_CROP
                    || randomTicks.canApplyBoneMeal(interact.x(), interact.y(), interact.z());
            // 묘목은 확률 실패·공간 막힘에도 소비하지만, 대나무는 최대 높이/막힌 공간이면 소비하지 않는다.
            if (useBoneMealIfApplicable(player.inventory(), hand, applicable,
                    () -> randomTicks.applyBoneMeal(interact.x(), interact.y(), interact.z()))) {
                sendTo(player, inventoryMessage(player));
                broadcastWorldSound("bonemeal", interact.x(), interact.y(), interact.z(), (short) current);
            }
            return;
        }
        if (selected == (short) Blocks.SHEARS) {
            int state = rt.blockStates().get(interact.x(), interact.y(), interact.z(), current);
            int sheared = P4Rules.shearsResult(current, state);
            if (sheared != current && InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                    interact.x(), interact.y(), interact.z())) {
                rt.fluidSim().applyChange(interact.x(), interact.y(), interact.z(), sheared);
                rt.setBlockState(interact.x(), interact.y(), interact.z(), sheared, state);
                rt.tickBlockChanges().put(new BlockPos(interact.x(), interact.y(), interact.z()), (short) sheared);
                int drop = P4Rules.shearsDrop(current, state);
                int count = P4Rules.shearsDropCount(current, state);
                if (drop != AIR && count > 0) rt.itemSystem().spawnDrop((short) drop, count,
                        interact.x() + 0.5, interact.y() + 0.5, interact.z() + 0.5);
                degradeAndRecord(player, hand);
                sendTo(player, inventoryMessage(player));
                broadcastWorldSound("shears", interact.x(), interact.y(), interact.z(), (short) current);
            }
            if (sheared != current) return;
        }
        if (current == Blocks.CAVE_VINES || current == Blocks.CAVE_VINES_PLANT) {
            int state = rt.blockStates().get(interact.x(), interact.y(), interact.z(), current);
            if ((state & P6Rules.CAVE_VINES_BERRIES) != 0
                    && InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                            interact.x(), interact.y(), interact.z())) {
                rt.setBlockState(interact.x(), interact.y(), interact.z(), current,
                        state & ~P6Rules.CAVE_VINES_BERRIES);
                rt.tickBlockChanges().put(new BlockPos(interact.x(), interact.y(), interact.z()),
                        (short) current);
                rt.itemSystem().spawnDrop(PlayerInventory.GLOW_BERRIES, 1,
                        interact.x() + 0.5, interact.y() + 0.5, interact.z() + 0.5);
                broadcastWorldSound("harvest", interact.x(), interact.y(), interact.z(),
                        (short) current);
            }
            return;
        }
        // [CROP-BERRY] 달콤한 열매 덤불 우클릭 = 열매만 딴다(블록은 남는다). [A]
        // SweetBerryBushBlock.use: age >= 2 라야 성립하고 `1 + random.nextInt(2)` 에 age 3 이면
        // +1 을 더해 떨군 뒤 age 를 **1** 로 되돌린다(0 이 아니다). age 0·1 은 아무 일도 하지
        // 않고 아래 설치 분기로도 내려가지 않는다 — 덤불 위에 대고 열매를 든 채 우클릭했을 때
        // 덤불이 겹쳐 심어지는 것을 막는다.
        if (current == Blocks.SWEET_BERRY_BUSH) {
            int state = rt.blockStates().get(interact.x(), interact.y(), interact.z(), current);
            int age = SweetBerryBushRules.age(state);
            if (age >= SweetBerryBushRules.HARVESTABLE_AGE
                    && InteractRules.withinReach(player.x(), player.y(), player.z(),
                            player.crouching(), interact.x(), interact.y(), interact.z())) {
                int count = SweetBerryBushRules.harvestDropCount(age, berryHarvestRandom::nextInt);
                rt.setBlockState(interact.x(), interact.y(), interact.z(), current,
                        SweetBerryBushRules.state(SweetBerryBushRules.AGE_AFTER_HARVEST));
                rt.tickBlockChanges().put(new BlockPos(interact.x(), interact.y(), interact.z()),
                        (short) current);
                rt.itemSystem().spawnDrop(PlayerInventory.SWEET_BERRIES, count,
                        interact.x() + 0.5, interact.y() + 0.5, interact.z() + 0.5);
                broadcastWorldSound("harvest", interact.x(), interact.y(), interact.z(),
                        (short) current);
            }
            return;
        }
        // [CROP-BERRY] 달콤한 열매를 바닥에 대고 우클릭하면 덤불이 심어진다. 바닐라도 열매
        // 아이템이 곧 덤불의 BlockItem 이다. 작물의 씨앗 심기(위 seedRule)와 같은 꼴이지만
        // 경작지 전용이 아니라 흙 계열 전반 위에 선다(SweetBerryBushRules.isSoil).
        if (selected == PlayerInventory.SWEET_BERRIES
                && SweetBerryBushRules.isSoil(current)) {
            int bushY = interact.y() + 1;
            if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                    interact.x(), interact.y(), interact.z())
                    || bushY > Blocks.MAX_Y
                    || residentBlockType(rt.accessor(), interact.x(), bushY, interact.z()) != AIR) {
                return;
            }
            if (!InventoryRules.consumeOne(
                    player.inventory(), hand, PlayerInventory.SWEET_BERRIES)) return;
            rt.fluidSim().applyChange(interact.x(), bushY, interact.z(), Blocks.SWEET_BERRY_BUSH);
            rt.setBlockState(interact.x(), bushY, interact.z(), Blocks.SWEET_BERRY_BUSH,
                    SweetBerryBushRules.state(0));
            sendTo(player, inventoryMessage(player));
            broadcastWorldSound("plant", interact.x(), bushY, interact.z(),
                    (short) Blocks.SWEET_BERRY_BUSH);
            return;
        }
        // [COOKING] 케이크 우클릭 = 한 조각 취식. 바닐라 CakeBlock 은 사용 애니메이션 없이
        // 즉시 먹으므로 Consume 액션의 32틱 게이트를 타지 않는다(그래서 여기, 설치 분기 앞이다).
        // 손에 든 것이 무엇이든 먹으며, 허기가 가득 차 있으면 아무 일도 하지 않는다.
        if (current == Blocks.CAKE) {
            if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                    interact.x(), interact.y(), interact.z())) return;
            int bites = rt.blockStates().get(interact.x(), interact.y(), interact.z(), current);
            if (!CakeRules.canEat(current, bites, player.food())) return;
            int nextBlock = CakeRules.blockAfterBite(bites);
            int nextBites = CakeRules.bitesAfterBite(bites);
            rt.fluidSim().applyChange(interact.x(), interact.y(), interact.z(), nextBlock);
            rt.setBlockState(interact.x(), interact.y(), interact.z(), nextBlock, nextBites);
            rt.tickBlockChanges().put(new BlockPos(interact.x(), interact.y(), interact.z()),
                    (short) nextBlock);
            player.eat(CakeRules.SLICE_NUTRITION, CakeRules.SLICE_SATURATION_MILLI);
            sendTo(player, new SoundEvent("consume_food", PlayerInventory.CAKE));
            return;
        }
        if (InventoryRules.isBucket(selected)) {
            applyBucketInteract(player, hand, interact.x(), interact.y(), interact.z(), selected);
            return;
        }
        if (current == Blocks.CAMPFIRE && CampfireRules.isCookable(selected)) {
            enqueuePlayerContainerAction(player, () -> applyCampfireFood(
                    player, hand, interact.x(), interact.y(), interact.z(), selected));
            return;
        }
        if (current == Blocks.CAMPFIRE && isShovel(selected)) {
            if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                    interact.x(), interact.y(), interact.z())) return;
            int state = rt.blockStates().get(interact.x(), interact.y(), interact.z(), current);
            if ((state & BuildingBlockRules.CAMPFIRE_LIT) != 0) {
                rt.setBlockState(interact.x(), interact.y(), interact.z(), current,
                        state & ~BuildingBlockRules.CAMPFIRE_LIT);
                rt.tickBlockChanges().put(new BlockPos(interact.x(), interact.y(), interact.z()), (short) current);
                degradeAndRecord(player, hand);
                sendTo(player, inventoryMessage(player));
                CampfireInventory campfire =
                        rt.campfireStorage().peekAt(interact.x(), interact.y(), interact.z());
                if (campfire != null) {
                    if (campfire.needsCooldown()) {
                        rt.campfireStorage().activate(interact.x(), interact.y(), interact.z());
                    } else {
                        rt.campfireStorage().deactivate(
                                CampfireStorage.key(interact.x(), interact.y(), interact.z()));
                    }
                    broadcastCampfireUpdate(interact.x(), interact.y(), interact.z(), campfire);
                }
                broadcastWorldSound("campfire_extinguish", interact.x(), interact.y(), interact.z(),
                        (short) current);
            }
            return;
        }
        if (selected == PlayerInventory.FLINT_AND_STEEL) {
            if (current == Blocks.CAMPFIRE) {
                int state = rt.blockStates().get(interact.x(), interact.y(), interact.z(), current);
                if ((state & BuildingBlockRules.CAMPFIRE_LIT) == 0
                        && InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                                interact.x(), interact.y(), interact.z())) {
                    rt.setBlockState(interact.x(), interact.y(), interact.z(), current,
                            state | BuildingBlockRules.CAMPFIRE_LIT);
                    rt.tickBlockChanges().put(new BlockPos(interact.x(), interact.y(), interact.z()),
                            (short) current);
                    wearFlintAndSteelAndRecord(player, hand);
                    sendTo(player, inventoryMessage(player));
                    CampfireInventory campfire =
                            rt.campfireStorage().peekAt(interact.x(), interact.y(), interact.z());
                    if (campfire != null) {
                        rt.campfireStorage().activate(interact.x(), interact.y(), interact.z());
                        broadcastCampfireUpdate(interact.x(), interact.y(), interact.z(), campfire);
                    }
                    broadcastWorldSound("campfire_light", interact.x(), interact.y(), interact.z(),
                            (short) current);
                }
                return;
            }
            applyFlintAndSteel(player, hand, interact.x(), interact.y(), interact.z());
            return;
        }
        // [TRIAL-GAP] 화염구(FireChargeItem.useOn): 모닥불을 켜거나 맞은 면 바깥 칸에 불을 놓고
        // 한 개를 소비한다. TNT 는 TntBlock.useItemOn 이 화염구로도 점화한다.
        if (selected == PlayerInventory.FIRE_CHARGE) {
            if (current == Blocks.CAMPFIRE) {
                int state = rt.blockStates().get(interact.x(), interact.y(), interact.z(), current);
                if ((state & BuildingBlockRules.CAMPFIRE_LIT) == 0
                        && InteractRules.withinReach(player.x(), player.y(), player.z(),
                                player.crouching(), interact.x(), interact.y(), interact.z())
                        && InventoryRules.consumeOne(
                                player.inventory(), hand, PlayerInventory.FIRE_CHARGE)) {
                    rt.setBlockState(interact.x(), interact.y(), interact.z(), current,
                            state | BuildingBlockRules.CAMPFIRE_LIT);
                    rt.tickBlockChanges().put(
                            new BlockPos(interact.x(), interact.y(), interact.z()), (short) current);
                    sendTo(player, inventoryMessage(player));
                    CampfireInventory campfire =
                            rt.campfireStorage().peekAt(interact.x(), interact.y(), interact.z());
                    if (campfire != null) {
                        rt.campfireStorage().activate(interact.x(), interact.y(), interact.z());
                        broadcastCampfireUpdate(interact.x(), interact.y(), interact.z(), campfire);
                    }
                    broadcastWorldSound("firecharge_use", interact.x(), interact.y(),
                            interact.z(), (short) current);
                }
                return;
            }
            applyFireCharge(player, hand, interact.x(), interact.y(), interact.z());
            return;
        }
    }

    /**
     * [TRIAL-GAP] 화염구 사용. 라이터({@link #applyFlintAndSteel})와 같은 TNT → 차원문 → 빈 칸
     * 불 순서이고 갈리는 것은 소모(내구 대신 한 개)와 소리(item.firecharge.use)뿐이다.
     */
    private void applyFireCharge(PlayerTickState player, PlayerInventory.HandRef hand,
            int x, int y, int z) {
        if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                x, y, z) || y < Blocks.MIN_Y || y > Blocks.MAX_Y) {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error("IGNITE_TOO_FAR"));
            return;
        }
        if (residentBlockType(rt.accessor(), x, y, z) == Blocks.TNT) {
            if (!InventoryRules.consumeOne(player.inventory(), hand, PlayerInventory.FIRE_CHARGE)) {
                return;
            }
            primeTnt(x, y, z);
            sendTo(player, inventoryMessage(player));
            return;
        }
        PortalFrame frame = findPortalFrame(x, y, z,
                (qx, qy, qz) -> residentBlockType(rt.accessor(), qx, qy, qz));
        if (frame != null) {
            if (!InventoryRules.consumeOne(player.inventory(), hand, PlayerInventory.FIRE_CHARGE)) {
                return;
            }
            int axisState = portalAxisState(frame);
            fillPortal(frame, (px, py, pz) -> {
                rt.fluidSim().applyChange(px, py, pz, Blocks.NETHER_PORTAL);
                rt.setBlockState(px, py, pz, Blocks.NETHER_PORTAL, axisState);
                rt.tickBlockChanges().put(new BlockPos(px, py, pz), (short) Blocks.NETHER_PORTAL);
            });
            sendTo(player, inventoryMessage(player));
            broadcastWorldSound("firecharge_use", x, y, z, (short) Blocks.NETHER_PORTAL);
            return;
        }
        if (randomTicks.canPlaceFire(x, y, z)
                && InventoryRules.consumeOne(player.inventory(), hand, PlayerInventory.FIRE_CHARGE)
                && randomTicks.ignite(x, y, z, rt.tickNo())) {
            sendTo(player, inventoryMessage(player));
            broadcastWorldSound("firecharge_use", x, y, z, (short) Blocks.FIRE);
            return;
        }
        sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error("IGNITE_INVALID"));
    }

    /**
     * [TRIAL-GAP] 작은 화염구 블록 명중({@code SmallFireball.onHitBlock}): 칸이 비었으면
     * {@code BaseFireBlock.getState} 불을 놓는다. 살 수 없는 자리의 불은 바닐라도 곧 꺼진다.
     */
    /** [TRIAL-GAP] 이 칸의 윗면이 sturdy 한가(거미줄 효과의 {@code isFaceSturdy(UP)}). */
    boolean sturdyTopAt(int x, int y, int z) {
        return randomTicks.hasSturdyTop(x, y, z);
    }

    void igniteEmptyCell(int x, int y, int z) {
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return;
        if (residentBlockType(rt.accessor(), x, y, z) != AIR) return;
        randomTicks.ignite(x, y, z, rt.tickNo());
    }

    private void submitCopperRevival(String key, CopperGolemOriginRules.RevivalPlan revival,
            PlayerTickState player, PlayerInventory.HandRef hand, int sourceBlock) {
        if (!copperSettlementCells.reserve(key, List.of(new BlockPos(
                revival.statueX(), revival.statueY(), revival.statueZ())))) return;
        if (!animalSettlementInFlight.add(key)) return;
        Runnable transaction = () -> {
            com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService
                    .CopperRevivalIntent intent = null;
            boolean committed = false;
            try {
                intent = rt.animalSettlements().beginCopperGolemRevival(rt.worldId(),
                        revival.statueX(), revival.statueY(), revival.statueZ());
                if (intent != null) {
                    var snapshot = rt.mobSystem().detachedCopperGolemSnapshot(intent.mobId(),
                            revival.spawnX(), revival.spawnY(), revival.spawnZ(),
                            intent.customName(), intent.heldItem(), intent.heldDurability(),
                            intent.pose());
                    committed = rt.animalSettlements().commitCopperGolemRevival(
                            rt.worldId(), intent, snapshot);
                }
            } catch (RuntimeException | Error failure) {
                committed = false;
            }
            var durableIntent = intent;
            boolean durable = committed;
            rt.enqueuePersistenceCompletion(() -> {
                animalSettlementInFlight.remove(key);
                if (!durable || durableIntent == null) {
                    copperSettlementCells.release(key);
                    return;
                }
                try {
                    rt.mobSystem().installCommittedCopperGolem(durableIntent.mobId(),
                            revival.spawnX(), revival.spawnY(), revival.spawnZ(),
                            durableIntent.customName(), durableIntent.heldItem(),
                            durableIntent.heldDurability(), durableIntent.pose());
                    clearConstructedGolemBlock(
                            revival.statueX(), revival.statueY(), revival.statueZ());
                    if (rt.players().get(player.nickname()) == player) {
                        degradeAndRecord(player, hand);
                        sendTo(player, inventoryMessage(player));
                    }
                    broadcastWorldSound("block_place", revival.statueX(), revival.statueY(),
                            revival.statueZ(), (short) sourceBlock);
                    pendingCopperSettlementSubmissions.remove(key);
                    pendingAnimalSettlementRetirements.add(durableIntent.key());
                } finally {
                    copperSettlementCells.release(key);
                }
            });
        };
        if (!rt.submitAnimalSettlementPersistence(transaction,
                () -> animalSettlementInFlight.remove(key))) {
            animalSettlementInFlight.remove(key);
        }
    }

    /** Java 26.3 ShelfBlock direct/front-slot and powered three-shelf hotbar exchange. */
    /**
     * [BLOCK-SHAPES] BellBlock#useWithoutItem → onHit: 리치 안의 종을 isProperHit 면으로 치면
     * BellBlockEntity.onHit 이 흔들림을 시작하고(clickDirection = 친 면) GameEvent.BLOCK_CHANGE 를 낸다.
     * 흔들림은 표시 전용이라 권위는 사건만 방송한다. 정적판 {@code StandaloneWorldRuntime.ringBell} 과 같다.
     */
    private void applyBellRing(PlayerAction.BellRing action) {
        PlayerTickState player = rt.players().get(action.nickname());
        if (player == null || player.isDead() || !Double.isFinite(action.hitY())
                || !InteractRules.withinReach(player.x(), player.y(), player.z(),
                        player.crouching(), action.x(), action.y(), action.z())) return;
        if (residentBlockType(rt.accessor(), action.x(), action.y(), action.z()) != Blocks.BELL) return;
        int state = rt.blockStates().get(action.x(), action.y(), action.z(), Blocks.BELL);
        if (!BlockModelShapes.bellIsProperHit(state, action.face(), action.hitY())) return;
        ringBell(action.x(), action.y(), action.z(), action.face(), player.nickname());
    }

    /**
     * [BLOCK-SHAPES] BellBlock#onProjectileHit → onHit(level, state, hit, owner, true): 투사체가 종의
     * 충돌 상자를 isProperHit 면으로 맞히면 손으로 친 것과 같이 울린다. 소유자가 플레이어면 진동 원천도
     * 그 플레이어다(attemptToRing 의 entity). 정적판 {@code ringBellFromProjectile} 과 같다.
     */
    void ringBellFromProjectile(int x, int y, int z, int face, double hitY, String ownerNickname) {
        if (face < 0 || !Double.isFinite(hitY)) return;
        if (residentBlockType(rt.accessor(), x, y, z) != Blocks.BELL) return;
        int state = rt.blockStates().get(x, y, z, Blocks.BELL);
        if (!BlockModelShapes.bellIsProperHit(state, face, hitY)) return;
        ringBell(x, y, z, face, ownerNickname);
    }

    /**
     * [POT-PROJECTILE] {@code DecoratedPotBlock#onProjectileHit} (26.3-snapshot-7): when
     * {@code projectile.mayInteract} (always here: there is no mob-griefing switch and players may
     * interact everywhere) and {@code projectile.mayBreak} ({@code #impact_projectiles} with the
     * default-true {@code projectilesCanBreakBlocks} rule) the pot is set {@code cracked=true} and
     * destroyed with drops: the loot table's cracked branch drops the four sherds (the generated
     * trial-chamber pot's exact face items), its stored contents spill, and the cracked sound type
     * plays {@code block.decorated_pot.shatter}. Standalone twin {@code shatterDecoratedPotFromProjectile}.
     */
    /** ChorusFlowerBlock.onProjectileHit: mayBreak impact projectiles destroy with one flower drop. */
    void breakChorusFlowerFromProjectile(
            com.gameexpert.engine.mob.ProjectileSim.Kind kind, int x, int y, int z) {
        if (kind == null || !kind.impactProjectile() || y < Blocks.MIN_Y || y > Blocks.MAX_Y) return;
        if (residentBlockType(rt.accessor(), x, y, z) != Blocks.CHORUS_FLOWER) return;
        rt.fluidSim().applyChange(x, y, z, Blocks.AIR);
        onExplosionBlockRemoved(x, y, z, Blocks.CHORUS_FLOWER);
        rt.itemSystem().spawnDeathDrop((short) Blocks.CHORUS_FLOWER, 1, x + .5, y + .5, z + .5);
        broadcastWorldSound("block_break", x, y, z, (short) Blocks.CHORUS_FLOWER);
    }

    void shatterDecoratedPotFromProjectile(
            com.gameexpert.engine.mob.ProjectileSim.Kind kind, int x, int y, int z) {
        if (kind == null || !kind.impactProjectile()) return;
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return;
        int current = residentBlockType(rt.accessor(), x, y, z);
        if (!Blocks.isDecoratedPot(current)) return;
        int state = rt.blockState(x, y, z, current);
        var generatedTrialPot = rt.takeGeneratedTrialDecoratedPotAt(x, y, z);
        String potComponents = decoratedPotComponentsAt(x, y, z);
        rt.fluidSim().applyChange(x, y, z, blockAfterRemovalAt(x, y, z, current, state));
        onExplosionBlockRemoved(x, y, z, current);
        dropRemovedBlockContents(rt, current, x, y, z, 0, false);
        rt.itemSystem().spawnDecoratedPotDrop((short) current, true, potComponents,
                generatedTrialPot.isPresent() ? generatedTrialPot.get().faceItemTypes() : null,
                x + .5, y + .5, z + .5);
        broadcastWorldSound("decorated_pot_shatter", x, y, z, (short) current);
    }

    /**
     * BellBlock#attemptToRing: BellBlockEntity#onHit(흔들림, clickDirection = 친 면) · BELL_BLOCK 소리
     * (volume 2.0, 32 블록) · GameEvent.BLOCK_CHANGE. 다시 치면 흔들림을 처음부터 다시 세고, 공명 중이면
     * 공명 틱도 0 으로 되돌린다(triggerEvent).
     */
    private void ringBell(int x, int y, int z, int face, String sourceNickname) {
        sculkVibrations.emit(SculkVibrationRules.Event.BLOCK_CHANGE, x, y, z, sourceNickname);
        com.gameexpert.ws.dto.WsMessages.BellRing ring =
                new com.gameexpert.ws.dto.WsMessages.BellRing(x, y, z, face);
        for (PlayerTickState observer : rt.players().values()) sendTo(observer, ring);
        broadcastWorldSound("bell_use", x, y, z, (short) Blocks.BELL);
        // [VILLAGER-BELL] BellBlockEntity#triggerEvent 의 updateEntities: 들은 주민에게 HEARD_BELL_TIME(숨기 활동).
        rt.mobSystem().ringBell(x, y, z);
        ringingBells.computeIfAbsent(x + "," + y + "," + z, key -> new BellResonance(x, y, z))
                .ring(rt.tickNo() * StatusEffects.MC_TICKS_PER_SERVER_TICK, rt.mobSystem().bellRaiders());
    }

    /**
     * [GLOWING] BellBlockEntity#serverTick 을 권위 틱마다 MC 2 틱 돌린다({@link BellResonance}). 공명을
     * 시작한 MC 틱에 BELL_RESONATE(volume 1.0, 16 블록)를 내고, 공명이 끝나면 상태 기계가 울림 때 모은
     * 목록의 48 블록 안 습격자에게 발광 60 MC 틱을 건다. 종이 사라지면 원장도 버린다(블록 엔티티 제거).
     */
    private void tickBellResonance(long tickNo) {
        if (ringingBells.isEmpty()) return;
        BellResonance.Raiders raiders = rt.mobSystem().bellRaiders();
        long gameTime = tickNo * StatusEffects.MC_TICKS_PER_SERVER_TICK;
        java.util.Iterator<BellResonance> iterator = ringingBells.values().iterator();
        while (iterator.hasNext()) {
            BellResonance bell = iterator.next();
            if (residentBlockType(rt.accessor(), bell.x, bell.y, bell.z) != Blocks.BELL) {
                iterator.remove();
                continue;
            }
            int events = 0;
            for (int mcTick = 0; mcTick < StatusEffects.MC_TICKS_PER_SERVER_TICK; mcTick++) {
                events |= bell.tickOnce(raiders);
            }
            if ((events & BellResonance.RESONATED) != 0) {
                broadcastWorldSound("bell_resonate", bell.x, bell.y, bell.z, (short) Blocks.BELL);
            }
            if (bell.idle(gameTime + StatusEffects.MC_TICKS_PER_SERVER_TICK)) iterator.remove();
        }
    }

    /** [EC-MOBS] 아이템 액자 한 개를 누른 면 바깥 칸에 건다. */
    private void applyPlaceItemFrame(PlayerAction.PlaceItemFrame action) {
        PlayerTickState player = rt.players().get(action.nickname());
        if (player == null || player.isDead()) return;
        if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                action.x(), action.y(), action.z())) {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error("INTERACT_REJECTED"));
            return;
        }
        PlayerInventory.HandRef hand = player.inventory().capture(inventoryHand(action.hand()));
        if (player.inventory().stack(hand).itemType() != PlayerInventory.ITEM_FRAME) return;
        if (rt.mobSystem().placeItemFrame(player, hand, action.x(), action.y(), action.z(), action.face())) {
            sendTo(player, inventoryMessage(player));
        }
    }

    /**
     * [DRAGON] 엔드 수정 설치({@code EndCrystalItem.useOn}): 누른 칸이 흑요석·기반암이고 윗칸이 비었으며 개체가 없으면
     * 받침을 숨긴 수정을 세우고 손의 한 개를 쓴다. 엔드 차원이면 드래곤 부활을 시도한다.
     */
    private void applyPlaceEndCrystal(PlayerAction.PlaceEndCrystal action) {
        PlayerTickState player = rt.players().get(action.nickname());
        if (player == null || player.isDead()) return;
        if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                action.x(), action.y(), action.z())) {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error("INTERACT_REJECTED"));
            return;
        }
        PlayerInventory.HandRef hand = player.inventory().capture(inventoryHand(action.hand()));
        if (player.inventory().stack(hand).itemType() != PlayerInventory.END_CRYSTAL) return;
        if (!rt.dragonFight().placeEndCrystal(action.x(), action.y(), action.z())) return;
        player.inventory().consumeOne(hand, PlayerInventory.END_CRYSTAL);
        sendTo(player, inventoryMessage(player));
    }

    private void applyShelfInteract(PlayerAction.ShelfInteract action) {
        PlayerTickState player = rt.players().get(action.nickname());
        if (player == null || player.isDead() || action.slot() < 0
                || action.slot() >= com.gameexpert.engine.shelf.ShelfRules.SLOTS
                || !InteractRules.withinReach(player.x(), player.y(), player.z(),
                        player.crouching(), action.x(), action.y(), action.z())) return;
        int block = residentBlockType(rt.accessor(), action.x(), action.y(), action.z());
        if (!Blocks.isShelf(block)) return;
        int state = rt.blockStates().get(action.x(), action.y(), action.z(), block);
        if (!com.gameexpert.engine.shelf.ShelfRules.powered(state)) {
            ChestInventory shelf = rt.chestStorage().openAt(
                    action.x(), action.y(), action.z(), com.gameexpert.engine.shelf.ShelfRules.SLOTS);
            PlayerInventory.HandRef hand = player.inventory().capture(PlayerInventory.Hand.MAIN);
            PlayerInventory.StackSnapshot incoming = player.inventory().stack(hand);
            PlayerInventory.StackSnapshot outgoing = shelf.swapExact(action.slot(), incoming);
            if (!player.inventory().setStack(hand, outgoing)) {
                shelf.swapExact(action.slot(), outgoing);
                return;
            }
            rt.chestStorage().markDirty(action.x(), action.y(), action.z());
        } else {
            java.util.List<BlockPos> chain = shelfChain(action.x(), action.y(), action.z(), state);
            int firstHotbar = 9 - chain.size() * 3;
            for (int index = 0; index < chain.size(); index++) {
                BlockPos pos = chain.get(index);
                ChestInventory shelf = rt.chestStorage().openAt(
                        pos.x(), pos.y(), pos.z(), com.gameexpert.engine.shelf.ShelfRules.SLOTS);
                for (int slot = 0; slot < 3; slot++) {
                    int hotbar = firstHotbar + index * 3 + slot;
                    PlayerInventory.StackSnapshot incoming = player.inventory().hotbarStack(hotbar);
                    PlayerInventory.StackSnapshot outgoing = shelf.swapExact(slot, incoming);
                    if (!player.inventory().setHotbarStack(hotbar, outgoing)) {
                        shelf.swapExact(slot, outgoing);
                        return;
                    }
                }
                rt.chestStorage().markDirty(pos.x(), pos.y(), pos.z());
            }
        }
        rt.queuePlayerInventoryBaseline(player);
        sendTo(player, inventoryMessage(player));
        for (BlockPos pos : com.gameexpert.engine.shelf.ShelfRules.powered(state)
                ? shelfChain(action.x(), action.y(), action.z(), state)
                : java.util.List.of(new BlockPos(action.x(), action.y(), action.z()))) {
            int id = residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z());
            ChestInventory shelf = rt.chestStorage().peekAt(pos.x(), pos.y(), pos.z());
            if (Blocks.isShelf(id) && shelf != null) {
                ShelfUpdate update = shelfUpdate(pos.x(), pos.y(), pos.z(),
                        rt.blockStates().get(pos.x(), pos.y(), pos.z(), id), shelf);
                for (PlayerTickState observer : rt.players().values()) sendTo(observer, update);
            }
        }
    }

    static ShelfUpdate shelfUpdate(int x, int y, int z, int state, ChestInventory shelf) {
        java.util.List<ShelfSlot> slots = new java.util.ArrayList<>(3);
        for (int slot = 0; slot < 3; slot++) {
            // [GLINT] 인챈트된 스택은 선반 위에서도 광택을 입는다.
            slots.add(new ShelfSlot(slot, shelf.itemType(slot),
                    com.gameexpert.engine.inventory.ItemComponentCodec.enchantmentWireOrNull(
                            shelf.enchantments(slot), shelf.itemComponentData(slot))));
        }
        return new ShelfUpdate(x, y, z, state, java.util.List.copyOf(slots));
    }

    private java.util.List<BlockPos> shelfChain(int x, int y, int z, int state) {
        int facing = com.gameexpert.engine.shelf.ShelfRules.facing(state);
        int axisX = facing == 0 || facing == 2 ? 1 : 0;
        int axisZ = axisX == 1 ? 0 : 1;
        java.util.List<BlockPos> connected = new java.util.ArrayList<>(3);
        for (int offset = -2; offset <= 2; offset++) {
            int px = x + axisX * offset;
            int pz = z + axisZ * offset;
            int block = residentBlockType(rt.accessor(), px, y, pz);
            if (!Blocks.isShelf(block)) continue;
            int candidate = rt.blockStates().get(px, y, pz, block);
            if (!com.gameexpert.engine.shelf.ShelfRules.powered(candidate)
                    || com.gameexpert.engine.shelf.ShelfRules.facing(candidate) != facing) continue;
            connected.add(new BlockPos(px, y, pz));
        }
        connected.sort(java.util.Comparator.comparingInt(pos -> axisX == 1 ? pos.x() : pos.z()));
        int own = -1;
        for (int i = 0; i < connected.size(); i++) {
            BlockPos pos = connected.get(i);
            if (pos.x() == x && pos.z() == z) own = i;
        }
        if (own < 0) return java.util.List.of(new BlockPos(x, y, z));
        int from = Math.max(0, Math.min(own, connected.size() - 3));
        int to = Math.min(connected.size(), from + 3);
        return java.util.List.copyOf(connected.subList(from, to));
    }

    private void applyEditSign(PlayerAction.EditSign edit) {
        PlayerTickState player = rt.players().get(edit.nickname());
        if (player == null || player.isDead()
                || !InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                        edit.x(), edit.y(), edit.z())) return;
        int current = residentBlockType(rt.accessor(), edit.x(), edit.y(), edit.z());
        if (current != Blocks.POPLAR_SIGN && current != Blocks.POPLAR_HANGING_SIGN) return;
        if (rt.signAt(edit.x(), edit.y(), edit.z()) == null) return;
        rt.replaceSignText(new com.gameexpert.sign.dto.SignBlockData(
                edit.x(), edit.y(), edit.z(), edit.lines()));
    }

    private void settleCauldronMutation(PlayerTickState player, PlayerInventory.HandRef hand,
            PlayerInventory.StackSnapshot expected, PlayerInventory.StackSnapshot replacement,
            int nextState, int x, int y, int z, String sound) {
        PlayerInventory inventory = player.inventory();
        PlayerInventory.CompletePersistenceSnapshot source = inventory.acquireSettlementLease();
        if (source == null) return;
        PlayerInventory planned = source.detachedInventory();
        if (!planned.transformOne(planned.capture(hand.hand()), expected, replacement)) {
            inventory.releaseSettlementLease(source);
            return;
        }
        PlayerInventory.CompletePersistenceSnapshot committed =
                planned.completePersistenceSnapshot();
        try {
            long settlementId = rt.itemSystem().reserveSettlementEntityId();
            var command = new com.gameexpert.block.dto.PlayerBlockSettlementCommand(
                    settlementId, source.revision(),
                    rt.playerInventoryMutationSnapshot(player, committed),
                    x, y, z, (short) Blocks.CAULDRON, (short) nextState);
            var outcome = rt.playerBlockSettlements().settle(command);
            if (outcome != com.gameexpert.block.service.PlayerBlockSettlementService.Outcome.STALE
                    && inventory.installCommittedSettlement(source, committed)) {
                rt.setBlockState(x, y, z, Blocks.CAULDRON, nextState);
                rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) Blocks.CAULDRON);
                sendTo(player, inventoryMessage(player));
                broadcastWorldSound(sound, x, y, z, (short) Blocks.CAULDRON);
                return;
            }
        } catch (RuntimeException failed) {
            log.warn("월드 {} 가마솥 정산 실패 ({},{},{}): {}",
                    rt.worldId(), x, y, z, failed.toString());
        }
        inventory.releaseSettlementLease(source);
    }

    private void settleBurningPlayerInCauldron(PlayerTickState player) {
        if (!player.onFire()) return;
        int x = (int) Math.floor(player.x());
        int z = (int) Math.floor(player.z());
        int y = (int) Math.floor(player.y());
        if (residentBlockType(rt.accessor(), x, y, z) != Blocks.CAULDRON) {
            y--;
            if (residentBlockType(rt.accessor(), x, y, z) != Blocks.CAULDRON) return;
        }
        int state = rt.blockStates().get(x, y, z, Blocks.CAULDRON);
        int kind = CauldronRules.kind(state);
        if ((kind != CauldronRules.WATER && kind != CauldronRules.POWDER_SNOW)
                || CauldronRules.level(state) == 0) return;
        PlayerInventory inventory = player.inventory();
        PlayerInventory.CompletePersistenceSnapshot source = inventory.acquireSettlementLease();
        if (source == null) return;
        PlayerInventory planned = source.detachedInventory();
        if (!planned.advanceStateSettlementRevision()) {
            inventory.releaseSettlementLease(source);
            return;
        }
        int oldFireTicks = player.fireTicks();
        int oldFireAccum = player.fireAccum();
        player.setFireTicks(0);
        player.setFireAccum(0);
        PlayerInventory.CompletePersistenceSnapshot committed =
                planned.completePersistenceSnapshot();
        int nextState = CauldronRules.lowerOneLevel(state);
        try {
            long settlementId = rt.itemSystem().reserveSettlementEntityId();
            var command = new com.gameexpert.block.dto.PlayerBlockSettlementCommand(
                    settlementId, source.revision(),
                    rt.playerInventoryMutationSnapshot(player, committed), x, y, z,
                    (short) Blocks.CAULDRON, (short) nextState);
            var outcome = rt.playerBlockSettlements().settle(command);
            if (outcome != com.gameexpert.block.service.PlayerBlockSettlementService.Outcome.STALE
                    && inventory.installCommittedSettlement(source, committed)) {
                rt.setBlockState(x, y, z, Blocks.CAULDRON, nextState);
                rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) Blocks.CAULDRON);
                broadcastWorldSound("fire_extinguish", x, y, z, (short) Blocks.CAULDRON);
                return;
            }
        } catch (RuntimeException failed) {
            log.warn("월드 {} 가마솥 플레이어 소화 정산 실패 ({},{},{}): {}",
                    rt.worldId(), x, y, z, failed.toString());
        }
        player.restoreFireState(oldFireTicks, oldFireAccum);
        inventory.releaseSettlementLease(source);
    }

    /**
     * 보관함·개폐 블록·침대는 선택 아이템 동작보다 먼저 처리합니다. 아이템 때문에 표준 블록 사용이
     * 삼켜지지 않으며, 모닥불/TNT처럼 아이템 자체가 의미를 바꾸는 대상은 이 경로에 포함하지 않습니다.
     */
    /**
     * [TRIAL] 금고 우클릭 — 바닐라 {@code VaultBlock.useItemOn} → {@code VaultBlockEntity.Server.tryInsertKey}.
     *
     * <p>손에 무언가를 든 채 ACTIVE 금고를 눌렀을 때만 삽입을 시도한다. 열쇠가 아니면(불길한 금고는
     * 불길한 열쇠만) {@code vault_insert_item_fail}, 이미 보상받은 플레이어면
     * {@code vault_reject_rewarded_player} 를 15 MC 틱 간격으로만 낸다. 유효한 시도만 영속 lane 에
     * 넣고, 실제 열쇠 소비·영수증·배출 outbox 는 그 한 트랜잭션이 소유한다. 정산이 커밋되면 금고가
     * UNLOCKING → EJECTING 으로 전리품을 하나씩 배출한다.</p>
     */
    private void applyVaultInteract(PlayerTickState player, PlayerAction.Interact interact,
            short selected) {
        if (selected == PlayerInventory.EMPTY) return;
        var trials = rt.mobSystem().trialSpawners();
        int x = interact.x(), y = interact.y(), z = interact.z();
        if (!trials.vaultActive(x, y, z)) {
            // 바닐라는 TRY_WITH_EMPTY_HAND 로 조용히 넘긴다. 클라 예측을 되돌리는 오류만 보낸다.
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error("VAULT_INACTIVE"));
            return;
        }
        if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                x, y, z)) {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error("INTERACT_REJECTED"));
            return;
        }
        // [TRIAL] 바닐라 VaultConfig.keyItem: 일반 금고는 트라이얼 열쇠, 불길한 금고는 불길한 열쇠만 받는다.
        short required = trials.vaultOminous(x, y, z) ? PlayerInventory.OMINOUS_TRIAL_KEY
                : PlayerInventory.TRIAL_KEY;
        if (selected != required) {
            trials.vaultInsertFail(x, y, z, "vault_insert_item_fail");
            return;
        }
        if (trials.vaultRewarded(x, y, z, player.nickname())) {
            trials.vaultInsertFail(x, y, z, "vault_reject_rewarded_player");
            return;
        }
        if (!trials.beginVaultUnlock(x, y, z)) return;
        PlayerInventory.HandRef hand = player.inventory().capture(inventoryHand(interact.hand()));
        rt.requestVaultUnlock(player.nickname(), x, y, z, hand, player.inventory().stack(hand));
    }

    private void applyLecternInteract(PlayerTickState player, PlayerInventory.HandRef hand,
            PlayerAction.Interact interact) {
        if (rt.lecternPersistence() == null
                || !InteractRules.withinReach(player.x(), player.y(), player.z(),
                        player.crouching(), interact.x(), interact.y(), interact.z())) return;
        var stored = rt.lecternPersistence().load(
                rt.worldId(), interact.x(), interact.y(), interact.z());
        if (stored.isPresent()) {
            player.openLectern(interact.x(), interact.y(), interact.z(), interact.requestId());
            sendTo(player, lecternMessage(stored.get(), player.openLecternRequestId()));
            return;
        }
        PlayerInventory.StackSnapshot sourceBook = player.inventory().stack(hand);
        if (!com.gameexpert.engine.inventory.LecternRules.canPlace(
                sourceBook, sourceBook.itemComponents())) return;
        PlayerInventory.CompletePersistenceSnapshot source =
                player.inventory().acquireSettlementLease();
        if (source == null) return;
        PlayerInventory planned = source.detachedInventory();
        PlayerInventory.HandRef plannedHand = planned.capture(hand.hand());
        if (!planned.consumeOne(plannedHand, sourceBook.itemType())) {
            player.inventory().releaseSettlementLease(source);
            return;
        }
        PlayerInventory.CompletePersistenceSnapshot committed = planned.completePersistenceSnapshot();
        try {
            var outcome = rt.lecternPersistence().insertWithPlayer(source.revision(),
                    rt.playerInventoryMutationSnapshot(player, committed),
                    interact.x(), interact.y(), interact.z(), sourceBook);
            if (outcome != com.gameexpert.lectern.service.LecternPersistenceService
                    .PlayerSettlementOutcome.COMMITTED
                    || !player.inventory().installCommittedSettlement(source, committed)) {
                player.inventory().releaseSettlementLease(source);
                return;
            }
            player.openLectern(interact.x(), interact.y(), interact.z(), interact.requestId());
            sendTo(player, inventoryMessage(player));
            // [BLOCK-SHAPES] LecternBlock.placeBook 가 HAS_BOOK=true 로 setBlock 한다. 그 비트가 월드의
            // 책 모델(LecternRenderer)을 켠다 — 문 여닫이와 같은 블록 변경 방송·영속 경로를 탄다.
            int lecternState = rt.blockStates().get(interact.x(), interact.y(), interact.z(), Blocks.LECTERN);
            int withBook = lecternState | BlockModelShapes.LECTERN_HAS_BOOK;
            if (withBook != lecternState) {
                rt.setBlockState(interact.x(), interact.y(), interact.z(), Blocks.LECTERN, withBook);
                rt.tickBlockChanges().put(new BlockPos(interact.x(), interact.y(), interact.z()),
                        (short) Blocks.LECTERN);
            }
            rt.lecternPersistence().load(rt.worldId(), interact.x(), interact.y(), interact.z())
                    .ifPresent(state -> sendTo(player, lecternMessage(state, player.openLecternRequestId())));
        } catch (RuntimeException failed) {
            player.inventory().releaseSettlementLease(source);
            log.warn("월드 {} 렉턴 책 설치 정산 실패 ({},{},{}): {}", rt.worldId(),
                    interact.x(), interact.y(), interact.z(), failed.toString());
        }
    }

    private void settleBannerPlacement(PlayerTickState player, PlayerInventory.HandRef hand,
            PlayerAction.BlockEdit edit, int requestedState,
            List<ItemComponentData.BannerLayer> patterns) {
        PlayerInventory inventory = player.inventory();
        PlayerInventory.CompletePersistenceSnapshot source = inventory.acquireSettlementLease();
        if (source == null) {
            rollbackEdit(player, edit, residentBlockType(rt.accessor(), edit.x(), edit.y(), edit.z()));
            return;
        }
        PlayerInventory planned = source.detachedInventory();
        // 사본의 현재 선택 칸이 아니라 편집이 캡처한 칸에서 소비한다(보류 재적용 포함).
        PlayerInventory.HandRef plannedHand = planned.capture(hand.hand(), hand.mainSlot());
        if (!consumeForPlace(planned, plannedHand, edit.blockType())) {
            inventory.releaseSettlementLease(source);
            rollbackEdit(player, edit, residentBlockType(rt.accessor(), edit.x(), edit.y(), edit.z()));
            return;
        }
        PlayerInventory.CompletePersistenceSnapshot committed = planned.completePersistenceSnapshot();
        long settlementId = rt.itemSystem().reserveSettlementEntityId();
        var command = new com.gameexpert.banner.dto.BannerPlacementSettlementCommand(
                settlementId, source.revision(), rt.playerInventoryMutationSnapshot(player, committed),
                edit.x(), edit.y(), edit.z(), edit.blockType(), (short) requestedState, patterns);
        try {
            var outcome = rt.bannerPlacementSettlements().settle(command);
            if (outcome == com.gameexpert.banner.service.BannerPlacementSettlementService.Outcome.STALE
                    || !inventory.installCommittedSettlement(source, committed)) {
                inventory.releaseSettlementLease(source);
                rollbackEdit(player, edit,
                        residentBlockType(rt.accessor(), edit.x(), edit.y(), edit.z()));
                return;
            }
            rt.commitSettledBannerPlacement(edit.x(), edit.y(), edit.z(), patterns);
            rt.fluidSim().applyChange(edit.x(), edit.y(), edit.z(), edit.blockType());
            rt.setBlockState(edit.x(), edit.y(), edit.z(), edit.blockType(), requestedState);
            rt.tickBlockChanges().put(new BlockPos(edit.x(), edit.y(), edit.z()), edit.blockType());
            refreshConnectionsAround(edit.x(), edit.y(), edit.z());
            refreshStairsAround(edit.x(), edit.y(), edit.z());
            sendTo(player, inventoryMessage(player));
            broadcastWorldSound("block_place", edit.x(), edit.y(), edit.z(), edit.blockType());
        } catch (RuntimeException failed) {
            inventory.releaseSettlementLease(source);
            rollbackEdit(player, edit,
                    residentBlockType(rt.accessor(), edit.x(), edit.y(), edit.z()));
            log.warn("월드 {} 배너 설치 정산 실패 ({},{},{}): {}", rt.worldId(),
                    edit.x(), edit.y(), edit.z(), failed.toString());
        }
    }

    private boolean applyPrimaryBlockInteract(
            PlayerTickState player, PlayerAction.Interact interact, int current) {
        boolean openable = InteractRules.isOpenable(current);
        boolean door = InteractRules.isDoor(current);
        boolean bed = InteractRules.isBed(current);
        if (!FurnaceRules.isFurnace(current) && !InteractRules.opensContainerMenu(current)
                && current != Blocks.ENCHANTING_TABLE && !openable && !bed) {
            return false;
        }
        if (current == Blocks.ENCHANTING_TABLE) {
            if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                    interact.x(), interact.y(), interact.z())) {
                sendTo(player, new EnchantingClosed(interact.x(), interact.y(), interact.z()));
                sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error("INTERACT_REJECTED"));
                return true;
            }
            closeActiveMenuBeforeOpen(player);
            player.openEnchanting(interact.x(), interact.y(), interact.z());
            EnchantingInventory table =
                    rt.enchantingStorage().openAt(interact.x(), interact.y(), interact.z());
            sendTo(player, enchantingOpen(
                    player, interact.x(), interact.y(), interact.z(), table));
            return true;
        }
        if (FurnaceRules.isFurnace(current)) {
            if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                    interact.x(), interact.y(), interact.z())) {
                sendTo(player, new FurnaceClosed(interact.x(), interact.y(), interact.z()));
                sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error(
                        "INTERACT_REJECTED"));
                return true;
            }
            closeActiveMenuBeforeOpen(player);
            unregisterFurnaceSubscriber(player);
            player.openFurnace(interact.x(), interact.y(), interact.z());
            FurnaceInventory furnace = rt.furnaceStorage().openAt(
                    interact.x(), interact.y(), interact.z(), FurnaceVariant.of(current));
            rt.furnaceStorage().activate(interact.x(), interact.y(), interact.z());
            registerFurnaceSubscriber(player, interact.x(), interact.y(), interact.z());
            sendTo(player, furnaceOpen(player, interact.x(), interact.y(), interact.z(), furnace));
            return true;
        }
        if (InteractRules.opensContainerMenu(current)) {
            if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                    interact.x(), interact.y(), interact.z())) {
                sendTo(player, new com.gameexpert.ws.dto.WsMessages.ChestClosed(
                        interact.x(), interact.y(), interact.z()));
                sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error(
                        "INTERACT_REJECTED"));
                return true;
            }
            CanonicalLootContainerKind canonicalKind = canonicalLootKind(current);
            ChestTopology topology = canonicalKind == null ? null
                    : chestTopology(interact.x(), interact.y(), interact.z(), current);
            boolean canonicalOpen = topology != null
                    && topology.orderedHalves().stream().anyMatch(half ->
                            rt.canonicalFirstOpenRequired(canonicalKind,
                                    half.x(), half.y(), half.z()));
            if (canonicalOpen) {
                long requestToken = rt.requestCanonicalChestOpen(
                        player, rt.connectionIdForPlayer(player), current, true,
                        new BlockPos(interact.x(), interact.y(), interact.z()),
                        topology.orderedHalves(),
                        this::publishCanonicalChestOpen);
                if (requestToken == 0L) {
                    sendTo(player, new com.gameexpert.ws.dto.WsMessages.ChestClosed(
                            interact.x(), interact.y(), interact.z()));
                    sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error(
                            "INTERACT_REJECTED"));
                }
                return true;
            }
            closeActiveMenuBeforeOpen(player);
            ChestAccess chest = openChestAccess(player, interact.x(), interact.y(), interact.z());
            BlockPos partner = chest.otherHalf(interact.x(), interact.y(), interact.z());
            unregisterChestSubscriber(player);
            player.openChest(interact.x(), interact.y(), interact.z(), partner);
            registerChestSubscriber(player, chest);
            sendTo(player, chestOpenMessage(
                    interact.x(), interact.y(), interact.z(), current, chestSlots(chest)));
            // [HOPPER] HopperBlock#useWithoutItem only opens the menu and awards INSPECT_HOPPER:
            // no container sound and no PiglinAi.angerNearbyPiglins. DispenserBlock (dispenser,
            // dropper) is the same: openMenu plus INSPECT_DISPENSER/INSPECT_DROPPER.
            if (InteractRules.isSilentContainerMenu(current)) return true;
            // [BARREL-SOUND] 통은 첫 열람자일 때만 syncBarrelOpenState 가 barrel_open 을 낸다.
            if (current != Blocks.BARREL) {
                broadcastWorldSound(containerOpenSound(current), interact.x(), interact.y(),
                        interact.z(), (short) current);
            }
            // PiglinAi.angerNearbyPiglins(level, player, true): 컨테이너 개방은 시야가 있는 피글린만 본다.
            rt.mobSystem().angerPiglinsNearGuardedEvent(
                    player.nickname(), player.x(), player.y(), player.z(), true);
            return true;
        }
        if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                interact.x(), interact.y(), interact.z())) {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error(
                    "INTERACT_REJECTED"));
            return true;
        }
        if (openable) {
            int state = rt.blockStates().get(interact.x(), interact.y(), interact.z(), current);
            // [BLOCK-SHAPES] 울타리문은 FenceGateBlock#useWithoutItem 처럼 플레이어에게서 멀어지게 연다.
            int toggled = Blocks.isFenceGate(current)
                    ? BuildingBlockRules.fenceGateStateAfterUse(state, player.yaw())
                    : InteractRules.toggledState(current, state);
            rt.setBlockState(interact.x(), interact.y(), interact.z(), current, toggled);
            rt.tickBlockChanges().put(new BlockPos(interact.x(), interact.y(), interact.z()), (short) current);
            if (door) {
                int otherY = (state & BuildingBlockRules.DOOR_UPPER) != 0
                        ? interact.y() - 1 : interact.y() + 1;
                if (otherY >= Blocks.MIN_Y && otherY <= Blocks.MAX_Y
                        && residentBlockType(rt.accessor(), interact.x(), otherY, interact.z()) == current) {
                    int other = rt.blockStates().get(interact.x(), otherY, interact.z(), current);
                    int synced = (other & ~BuildingBlockRules.DOOR_OPEN)
                            | (toggled & BuildingBlockRules.DOOR_OPEN);
                    rt.setBlockState(interact.x(), otherY, interact.z(), current, synced);
                    rt.tickBlockChanges().put(new BlockPos(interact.x(), otherY, interact.z()), (short) current);
                }
            }
            boolean opening = isOpenState(current, toggled);
            String kind = Blocks.isFenceGate(current)
                    ? (opening ? "fence_gate_open" : "fence_gate_close")
                    : Blocks.isTrapdoor(current)
                            ? (opening ? "trapdoor_open" : "trapdoor_close")
                            : (opening ? "door_open" : "door_close");
            broadcastWorldSound(kind, interact.x(), interact.y(), interact.z(), (short) current);
        } else {
            applyBedInteract(player, interact.x(), interact.y(), interact.z(), current);
        }
        return true;
    }

    private static boolean isOpenState(int block, int state) {
        if (Blocks.isFenceGate(block)) return (state & BuildingBlockRules.GATE_OPEN) != 0;
        if (Blocks.isTrapdoor(block)) return (state & BuildingBlockRules.TRAPDOOR_OPEN) != 0;
        return (state & BuildingBlockRules.DOOR_OPEN) != 0;
    }

    static boolean isBoneMealTarget(int blockId, int state) {
        return blockId == Blocks.OAK_SAPLING || blockId == Blocks.BIRCH_SAPLING
                || blockId == Blocks.BAMBOO
                // [CROP-BERRY] 달콤한 열매 덤불도 다 자라기 전까지 뼛가루 대상이다.
                || blockId == Blocks.SWEET_BERRY_BUSH
                        && SweetBerryBushRules.isBoneMealTarget(
                                SweetBerryBushRules.age(state))
                // [PITCHER] Either half of a pitcher crop takes bone meal until the lower half is mature.
                || blockId == Blocks.PITCHER_CROP && PitcherRules.age(state) < PitcherRules.MAX_AGE
                || blockId != Blocks.PITCHER_CROP && CropRules.forCrop(blockId) != null
                        && state < CropRules.forCrop(blockId).maxAge();
    }

    private static boolean isShovel(short item) {
        return EnchantmentRules.isShovelItem(item);
    }

    static int normalizeCopperPlacementState(int blockType, int replacedBlockType, int state) {
        int normalized = state & 0xff;
        if (isCopperLantern(blockType)) {
            normalized &= COPPER_LANTERN_STATE_MASK;
        } else if (CopperAgeRules.isLightningRod(blockType)) {
            int facing = normalized & COPPER_ROD_FACING_MASK;
            normalized = facing <= 5 ? facing : 0;
        } else if (isCopperStatue(blockType)) {
            normalized &= COPPER_STATUE_STATE_MASK;
        }
        if (!BuildingBlockRules.canAcceptWater(blockType, normalized)) return normalized;
        // 바닐라 getStateForPlacement 는 WATERLOGGED 를 설치 칸의 유체로만 정한다 — 요청 바이트의 비트 7 은
        // 버린다(정적판 standaloneCopperRuntimePlacementState 와 같다).
        return (normalized & ~COPPER_WATERLOGGED)
                | (Fluids.isWater(replacedBlockType) ? COPPER_WATERLOGGED : 0);
    }

    /**
     * [WATERLOG] 바닐라에서 waterlogged 블록을 부수면 그 칸에 레벨 8 수원이 남는다
     * ({@code BlockBehaviour#getFluidState} 가 이미 물이므로 블록만 사라진다).
     */
    /**
     * [ENCHANT-WIDE] 바닐라 {@code IceBlock.playerDestroy}: 얼음·살얼음을 #prevents_ice_melting(섬세한 손길)
     * 없이 부쉈고 아래 칸이 움직임을 막거나 액체이면 물 수원이 된다.
     */
    private boolean iceMeltsOnPlayerDestroy(int blockType, long toolEnchantments, int x, int y, int z) {
        if (blockType != Blocks.ICE && blockType != Blocks.FROSTED_ICE) return false;
        if (EnchantmentRules.enchantLevel(toolEnchantments, EnchantmentRules.SILK_TOUCH) > 0) return false;
        int below = residentBlockType(rt.accessor(), x, y - 1, z);
        return Fluids.isSolid(below) || Fluids.isFluid(below);
    }

    private int blockAfterRemovalAt(int x, int y, int z, int blockType, int state) {
        int removal = blockAfterRemoval(blockType, state);
        if (removal != Blocks.AIR) return removal;
        return WaterloggedStates.isWaterloggedAt(rt.accessor(), blockType, state, x, y, z)
                ? WATER_SOURCE : removal;
    }

    static int blockAfterRemoval(int blockType, int state) {
        if (P6Rules.isSpeleothem(blockType)) {
            return (state & 128) != 0 ? WATER_SOURCE : Blocks.AIR;
        }
        return BuildingBlockRules.canAcceptWater(blockType, state)
                && (state & COPPER_WATERLOGGED) != 0
                ? WATER_SOURCE : Fluids.blockAfterRemoval(blockType);
    }

    private static boolean isCopperLantern(int blockType) {
        return blockType == Blocks.COPPER_LANTERN
                || blockType >= Blocks.EXPOSED_COPPER_LANTERN
                        && blockType <= Blocks.WAXED_OXIDIZED_COPPER_LANTERN;
    }

    private static boolean isCopperStatue(int blockType) {
        return blockType >= Blocks.COPPER_GOLEM_STATUE
                && blockType <= Blocks.WAXED_OXIDIZED_COPPER_GOLEM_STATUE;
    }

    /** 라이터 사용은 TNT/포털을 먼저 시도하고, 아니면 대상 빈 셀에 실제 FIRE를 놓는다. */
    private void applyFlintAndSteel(PlayerTickState player, PlayerInventory.HandRef hand,
            int x, int y, int z) {
        if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(), x, y, z)
                || y < Blocks.MIN_Y || y > Blocks.MAX_Y) {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error(
                    "IGNITE_TOO_FAR"));
            return;
        }
        if (residentBlockType(rt.accessor(), x, y, z) == Blocks.TNT) {
            primeTnt(x, y, z);
            wearFlintAndSteelAndRecord(player, hand);
            sendTo(player, inventoryMessage(player));
            broadcastWorldSound("ignite", x, y, z, (short) Blocks.TNT);
            return;
        }
        PortalFrame frame = findPortalFrame(x, y, z,
                (qx, qy, qz) -> residentBlockType(rt.accessor(), qx, qy, qz));
        if (frame != null) {
            int axisState = portalAxisState(frame);
            fillPortal(frame, (px, py, pz) -> {
                rt.fluidSim().applyChange(px, py, pz, Blocks.NETHER_PORTAL);
                // 바닐라 NetherPortalBlock.AXIS. 메셔가 nether_portal_ns / _ew 판을 이 비트로 고른다.
                rt.setBlockState(px, py, pz, Blocks.NETHER_PORTAL, axisState);
                rt.tickBlockChanges().put(new BlockPos(px, py, pz), (short) Blocks.NETHER_PORTAL);
            });
            wearFlintAndSteelAndRecord(player, hand);
            sendTo(player, inventoryMessage(player));
            broadcastWorldSound("ignite", x, y, z, (short) Blocks.NETHER_PORTAL);
            return;
        }

        if (randomTicks.ignite(x, y, z, rt.tickNo())) {
            wearFlintAndSteelAndRecord(player, hand);
            sendTo(player, inventoryMessage(player));
            broadcastWorldSound("ignite", x, y, z, (short) Blocks.FIRE);
            return;
        }
        sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error(
                "IGNITE_INVALID"));
    }

    /** Ordinary prime: remove the block, spawn the moving entity, then publish the explicit prime sound. */
    private void primeTnt(int x, int y, int z) {
        PrimedTntEntity entity = primedTnt.primeBlock(x, y, z, tntRandom);
        if (entity != null) {
            broadcastWorldSoundExact("tnt_prime", entity.x, entity.y, entity.z, (short) Blocks.TNT);
        }
    }

    /** Explosion chain: constructor angle draw precedes the exact 10..29 fuse draw; no prime sound. */
    void primeTntFromExplosion(int x, int y, int z, com.gameexpert.engine.mob.MobRandom random) {
        if (primedTnt.primeBlockFromExplosion(x, y, z, random) == null) return;
        refreshConnectionsAround(x, y, z);
        refreshStairsAround(x, y, z);
    }

    /** 폭발의 공통 블록 교체 뒤 파생 상태와 좌표별 런타임 메타데이터를 정리한다. */
    String decoratedPotComponentsAt(int x, int y, int z) {
        ChestInventory pot = rt.chestStorage().peekAt(x, y, z);
        return pot == null ? null : pot.potItemComponents();
    }

    void broadcastPotDecorations(int x, int y, int z, String components) {
        var faces = components == null ? null
                : com.gameexpert.engine.inventory.ItemComponentCodec.decode((short) Blocks.DECORATED_POT, components)
                    .potDecorations().stream().mapToInt(face -> Short.toUnsignedInt(face.itemType())).toArray();
        var message = new com.gameexpert.ws.dto.WsMessages.PotDecorationsUpdate(
                x, y, z, faces == null || faces.length == 0 ? null : faces);
        for (PlayerTickState player : rt.players().values()) sendTo(player, message);
    }

    void onExplosionBlockRemoved(int x, int y, int z, int original) {
        if (original == Blocks.OBSIDIAN || original == Blocks.NETHER_PORTAL) {
            extinguishPortalsTouching(x, y, z);
        }
        refreshConnectionsAround(x, y, z);
        refreshStairsAround(x, y, z);
        if (P6Rules.isSpeleothem(original)) {
            refreshSpeleothemRuns(List.of(new BlockPos(x, y, z)));
        }
        if (isCraftingStationBlock(original)) closeCraftingSubscribersAt(x, y, z);
    }

    List<com.gameexpert.ws.dto.WsMessages.PrimedTntDto> primedTntSnapshot() {
        return primedTnt.welcomeSnapshot();
    }

    void attachPrimedTntPersistence(com.gameexpert.tnt.service.PrimedTntPersistenceService service) {
        primedTnt.attachPersistence(service);
    }

    void flushPrimedTnt() {
        if (primedTntCheckpointInFlight) return;
        var persistenceService = primedTnt.persistenceService();
        if (persistenceService == null) return;
        if (runtimeFallCheckpointRetry != null) {
            primedTntCheckpointInFlight = true;
            var executor = rt.ctx().persistenceExecutor();
            if (executor == null) runtimeFallCheckpointRetry.run();
            else if (!executor.trySubmit(runtimeFallCheckpointRetry)) primedTntCheckpointInFlight = false;
            return;
        }
        primedTntCheckpointInFlight = true;
        Map<BlockPos, BlockDiffBuffer.Change> blockChanges = rt.ctx().blockDiffBuffer() == null
                ? Map.of() : rt.ctx().blockDiffBuffer().drain(rt.worldId());
        PrimedTntSystem.PersistenceBatch batch = primedTnt.beginPersistenceBatch();
        var admissions = rt.runtimeFallingSpeleothems().admissions();
        if (blockChanges.isEmpty() && batch == null && admissions.isEmpty()) {
            primedTntCheckpointInFlight = false;
            return;
        }
        String checkpointIdentity = java.util.UUID.randomUUID().toString();
        Runnable save = () -> {
            try {
                Runnable source = () -> persistenceService.applyCheckpoint(rt.worldId(), blockChanges,
                        batch == null ? List.of() : batch.snapshots(),
                        batch == null ? List.of() : batch.removedTntIds());
                if (admissions.isEmpty()) source.run();
                else rt.runtimeFallingSpeleothems().checkpoint(checkpointIdentity, admissions, source);
                if (batch != null) primedTnt.finishPersistenceBatch(batch.revision(), true);
                runtimeFallCheckpointRetry = null;
            } catch (RuntimeException | Error exception) {
                if (admissions.isEmpty()) {
                    if (rt.ctx().blockDiffBuffer() != null) {
                        rt.ctx().blockDiffBuffer().restore(rt.worldId(), blockChanges);
                    }
                    if (batch != null) primedTnt.finishPersistenceBatch(batch.revision(), false);
                }
                throw exception;
            } finally {
                primedTntCheckpointInFlight = false;
            }
        };
        // Retain the exact source snapshot and identity on unknown commit; newer diffs remain
        // separately buffered until this checkpoint's durable receipt has been acknowledged.
        if (!admissions.isEmpty()) runtimeFallCheckpointRetry = save;
        var executor = rt.ctx().persistenceExecutor();
        if (executor == null) save.run();
        else if (!executor.trySubmit(save)) {
            if (admissions.isEmpty()) {
                if (rt.ctx().blockDiffBuffer() != null) rt.ctx().blockDiffBuffer().restore(rt.worldId(), blockChanges);
                if (batch != null) primedTnt.finishPersistenceBatch(batch.revision(), false);
            }
            primedTntCheckpointInFlight = false;
        }
    }

    boolean hasPendingPrimedTntPersistence() {
        return primedTntCheckpointInFlight || runtimeFallCheckpointRetry != null || primedTnt.hasPendingPersistence();
    }

    boolean usesCombinedBlockTntCheckpoint() {
        return primedTnt.persistenceService() != null;
    }

    /** 채굴과 폭발이 공유하는 상자 내용물 드랍 경로. */
    static void dropChestContents(WorldRuntime rt, int x, int y, int z) {
        ChestInventory live = rt.openContainer(x, y, z);
        boolean shelfDisplay = live != null
                && live.slots() == com.gameexpert.engine.shelf.ShelfRules.SLOTS;
        rt.discardNaturalChestLoot(x, y, z);
        rt.tickLoop().closeChestSubscribersAt(x, y, z);
        for (ChestInventory.StoredStack stack : rt.removeContainerAt(x, y, z)) {
            // 상자 내용물은 새 블록 드랍이 아니라 기존 아이템 이동이다. 내구도·인챈트를 그대로 넘긴다.
            rt.itemSystem().spawnDrop(stack.itemType(), stack.count(), stack.durability(),
                    stack.enchantments(), stack.mapId(), stack.shulkerId(),
                    stack.bucketMobData(), stack.itemComponentData(),
                    x + 0.5, y + 0.5, z + 0.5);
        }
        if (shelfDisplay) rt.tickLoop().broadcastShelfRemoval(x, y, z);
    }

    /**
     * Every destructive block-removal caller uses this one family dispatcher after the block has
     * been admitted for removal. Mining transfers a shulker's exact 27-slot identity before this
     * call; callers which cannot yet carry that identity explicitly retain the legacy spill path so
     * contents are never silently destroyed.
     */
    static void dropRemovedBlockContents(WorldRuntime rt, int original, int x, int y, int z,
            int transferredShulkerId, boolean shulkerTransferred) {
        if (Blocks.isDecoratedPot(original)) rt.tickLoop().broadcastPotDecorations(x, y, z, null);
        if (original == Blocks.JUKEBOX) {
            // [JUKEBOX] JukeboxBlock.preRemoveSideEffects → popOutTheItem(성분째). 곡도 멈춘다.
            rt.tickLoop().removeJukeboxAt(x, y, z);
        }
        if (InteractRules.isContainer(original)) {
            if (original == Blocks.ENDER_CHEST) {
                rt.tickLoop().closeChestSubscribersAt(x, y, z);
            } else if (Blocks.isShulkerBox(original) && shulkerTransferred) {
                // The coordinate inventory is already detached and owned by transferredShulkerId.
                // Spilling here would duplicate the same 27 slots in the item and on the ground.
                log.debug("셜커 파괴 27칸 회수 참조={}", transferredShulkerId);
            } else {
                dropChestContents(rt, x, y, z);
            }
        }
        if (Blocks.isShelf(original)) dropChestContents(rt, x, y, z);
        if (FurnaceRules.isFurnace(original)) dropFurnaceContents(rt, x, y, z);
        if (original == Blocks.BREWING_STAND) dropBrewingContents(rt, x, y, z);
        if (original == Blocks.CAMPFIRE) dropCampfireContents(rt, x, y, z);
        if (original == Blocks.ENCHANTING_TABLE) dropEnchantingContents(rt, x, y, z);
        if (original == Blocks.LECTERN && rt.lecternPersistence() != null) {
            rt.lecternPersistence().remove(rt.worldId(), x, y, z).ifPresent(lectern -> {
                PlayerInventory.StackSnapshot book = lectern.book();
                rt.itemSystem().spawnDrop(book.itemType(), book.count(), book.durability(),
                        book.enchantments(), book.mapId(), book.shulkerId(),
                        book.bucketMobData(), book.itemComponentData(),
                        x + 0.5, y + 0.5, z + 0.5);
                rt.tickLoop().closeLecternSubscribersAt(x, y, z);
            });
        }
    }

    private void broadcastShelfRemoval(int x, int y, int z) {
        ShelfUpdate update = new ShelfUpdate(x, y, z, 0, java.util.List.of(
                new ShelfSlot(0, PlayerInventory.EMPTY),
                new ShelfSlot(1, PlayerInventory.EMPTY),
                new ShelfSlot(2, PlayerInventory.EMPTY)));
        for (PlayerTickState observer : rt.players().values()) sendTo(observer, update);
    }

    /** 채굴·폭발·외부 블록 교체가 공유하는 화로 내용물 드롭과 열린 화면 종료 경로입니다. */
    static void dropFurnaceContents(WorldRuntime rt, int x, int y, int z) {
        FurnaceInventory pending = rt.furnaceStorage().peekAt(x, y, z);
        // Do not collect first: that would leave the fractional remainder behind on the object
        // that is about to be discarded. The exact milli value must cross the removal boundary
        // together with the item stacks.
        FurnaceInventory.DrainResult drained = pending == null
                ? null : pending.drainAllWithXp();
        rt.tickLoop().closeFurnaceSubscribersAt(x, y, z);
        List<FurnaceInventory.StoredStack> stacks;
        if (pending == null) {
            stacks = rt.furnaceStorage().removeAt(x, y, z);
        } else {
            stacks = drained.stacks();
            rt.furnaceStorage().removeAt(x, y, z);
        }
        for (FurnaceInventory.StoredStack stack : stacks) {
            PlayerInventory.StackSnapshot value = stack.stack();
            rt.itemSystem().spawnDrop(value.itemType(), value.count(), value.durability(),
                    value.enchantments(), value.mapId(), value.shulkerId(), value.bucketMobData(),
                    value.itemComponentData(), x + 0.5, y + 0.5, z + 0.5);
        }
        if (drained != null) rt.publishRemovedFurnaceXp(drained, x + 0.5, y + 0.5, z + 0.5);
    }

    static void dropBrewingContents(WorldRuntime rt, int x, int y, int z) {
        rt.tickLoop().closeCraftingSubscribersAt(x, y, z);
        for (BrewingInventory.StoredStack stack : rt.brewingStorage().removeAt(x, y, z)) {
            // [BREWING-26.3] 범용 물약의 potionContents 등 칸 성분을 그대로 싣고 떨군다.
            rt.itemSystem().spawnDrop(stack.itemType(), stack.count(),
                    PlayerInventory.initialDurability(stack.itemType()), 0L, 0, 0, null,
                    stack.componentData(), x + 0.5, y + 0.5, z + 0.5);
        }
    }

    /** 채굴·폭발·지지 상실·외부 교체가 공유하는 모닥불 원재료 드롭과 빈 상태 알림입니다. */
    static void dropCampfireContents(WorldRuntime rt, int x, int y, int z) {
        List<CampfireInventory.StoredStack> contents = rt.campfireStorage().removeAt(x, y, z);
        if (contents.isEmpty()) return;
        for (CampfireInventory.StoredStack stack : contents) {
            rt.itemSystem().spawnDrop(
                    stack.itemType(), stack.count(), x + 0.5, y + 1.0, z + 0.5);
        }
        rt.tickLoop().broadcastCampfireUpdate(x, y, z, null);
    }

    static boolean wearFlintAndSteel(PlayerInventory inventory) {
        int slot = inventory.selectedSlot();
        if (inventory.itemType(slot) != PlayerInventory.FLINT_AND_STEEL) return false;
        inventory.degrade(slot);
        return true;
    }

    static boolean wearFlintAndSteel(
            PlayerInventory inventory, PlayerInventory.HandRef hand) {
        if (inventory.stack(hand).itemType() != PlayerInventory.FLINT_AND_STEEL) return false;
        inventory.degrade(hand);
        return true;
    }

    private void wearFlintAndSteelAndRecord(
            PlayerTickState player, PlayerInventory.HandRef hand) {
        PlayerInventory.StackSnapshot before = player.inventory().stack(hand);
        if (wearFlintAndSteel(player.inventory(), hand)) {
            recordBreakIfGone(player, player.inventory().capture(hand.hand()), before);
        }
    }

    private static void degradeAndRecord(
            PlayerTickState player, PlayerInventory.HandRef hand) {
        PlayerInventory.StackSnapshot before = player.inventory().stack(hand);
        player.inventory().degrade(hand);
        recordBreakIfGone(player, player.inventory().capture(hand.hand()), before);
    }

    private static void recordBreakIfGone(PlayerTickState player,
            PlayerInventory.HandRef hand, PlayerInventory.StackSnapshot before) {
        if (before.durability() == 1 && before.itemType() != PlayerInventory.EMPTY
                && player.inventory().stack(hand).itemType() == PlayerInventory.EMPTY) {
            player.recordBrokenItem(before.itemType());
        }
    }

    /** 채굴에 쓴 핫바 칸이 도구면 1 마모한다({@link InventoryRules#wearSelectedOnMine}의 칸 지정판). */
    private static boolean wearToolOnMine(PlayerInventory inventory, int slot) {
        if (!InventoryRules.isTool(inventory.itemType(slot))) return false;
        inventory.degrade(slot);
        return true;
    }

    private static void recordBreakIfGone(PlayerTickState player, int slot, short type, int beforeDurability) {
        if (beforeDurability == 1 && type != PlayerInventory.EMPTY
                && player.inventory().itemType(slot) == PlayerInventory.EMPTY) {
            player.recordBrokenItem(type);
        }
    }

    /**
     * 낙뢰 발화(바닐라 {@code LightningBolt#spawnFire(4)}). 난이도 NORMAL/HARD 에서만 일어나고,
     * 타격 칸 1회 + 그 칸을 둘러싼 3×3 안에서 최대 {@code EXTRA_IGNITIONS} 회 붙는다.
     *
     * @return 실제로 불이 붙은 칸 수
     */
    int igniteFromLightning(LightningStrike strike) {
        if (!LightningStrikeRules.ignitesAt(rt.difficulty())) return 0;
        int x = strike.blockX();
        int y = strike.blockY();
        int z = strike.blockZ();
        IntUnaryOperator rolls = LightningCopperRules.deterministicRolls(rt.seed(), strike);
        int lit = randomTicks.ignite(x, y, z, rt.tickNo()) ? 1 : 0;
        for (int attempt = 0; attempt < LightningStrikeRules.EXTRA_IGNITIONS; attempt++) {
            int dx = rolls.applyAsInt(3) - 1;
            int dz = rolls.applyAsInt(3) - 1;
            if (randomTicks.ignite(x + dx, y, z + dz, rt.tickNo())) lit++;
        }
        return lit;
    }

    @FunctionalInterface
    interface Igniter {
        boolean ignite(int x, int y, int z);
    }

    static boolean igniteAdjacent(int x, int y, int z, Igniter igniter) {
        int[][] directions = {{0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}};
        for (int[] direction : directions) {
            if (igniter.ignite(x + direction[0], y + direction[1], z + direction[2])) return true;
        }
        return false;
    }

    @FunctionalInterface
    interface PortalChange {
        void set(int x, int y, int z);
    }

    /** 단순 직사각형 프레임 결과. axisX=true면 X폭/Z고정, false면 Z폭/X고정이다. */
    static final class PortalFrame {
        final int startX;
        final int startY;
        final int startZ;
        final int width;
        final int height;
        final boolean axisX;

        PortalFrame(int startX, int startY, int startZ, int width, int height, boolean axisX) {
            this.startX = startX;
            this.startY = startY;
            this.startZ = startZ;
            this.width = width;
            this.height = height;
            this.axisX = axisX;
        }
    }

    /**
     * 클릭 칸을 내부 최하단으로 보고 X/Z 두 평면을 검사한다. 내부 2×3~21×21, 모서리는 검사하지 않는다.
     * 프레임 내부에는 공기처럼 치환 가능한 블록만 허용하며 기존 포털은 새 점화 대상으로 보지 않는다.
     */
    static PortalFrame findPortalFrame(int x, int y, int z, Fluids.BlockLookup lookup) {
        if (y <= Blocks.MIN_Y || y + 3 > Blocks.MAX_Y
                || !Fluids.isReplaceable(lookup.get(x, y, z))) return null;
        PortalFrame xFrame = findPortalFrameInPlane(x, y, z, 1, 0, lookup);
        return xFrame != null ? xFrame : findPortalFrameInPlane(x, y, z, 0, 1, lookup);
    }

    private static PortalFrame findPortalFrameInPlane(int x, int y, int z,
            int dx, int dz, Fluids.BlockLookup lookup) {
        int left = 0;
        for (int distance = 1; distance <= 22; distance++) {
            int block = lookup.get(x - dx * distance, y, z - dz * distance);
            if (block == Blocks.OBSIDIAN) {
                left = distance;
                break;
            }
            if (!Fluids.isReplaceable(block)) return null;
        }
        int right = 0;
        for (int distance = 1; distance <= 22; distance++) {
            int block = lookup.get(x + dx * distance, y, z + dz * distance);
            if (block == Blocks.OBSIDIAN) {
                right = distance;
                break;
            }
            if (!Fluids.isReplaceable(block)) return null;
        }
        int width = left + right - 1;
        if (left == 0 || right == 0 || width < 2 || width > 21) return null;
        int startX = x - dx * (left - 1);
        int startZ = z - dz * (left - 1);

        // 아래 테두리는 내부 폭 전부 흑요석이어야 한다. 양끝 모서리는 선택 사항이라 보지 않는다.
        for (int w = 0; w < width; w++) {
            if (lookup.get(startX + dx * w, y - 1, startZ + dz * w) != Blocks.OBSIDIAN) return null;
        }
        for (int h = 0; h <= 21; h++) {
            boolean top = h >= 3;
            for (int w = 0; w < width; w++) {
                int block = lookup.get(startX + dx * w, y + h, startZ + dz * w);
                top &= block == Blocks.OBSIDIAN;
                if (block != Blocks.OBSIDIAN && !Fluids.isReplaceable(block)) return null;
            }
            if (top) return new PortalFrame(startX, y, startZ, width, h, dx != 0);

            int sideX1 = startX - dx;
            int sideZ1 = startZ - dz;
            int sideX2 = startX + dx * width;
            int sideZ2 = startZ + dz * width;
            if (lookup.get(sideX1, y + h, sideZ1) != Blocks.OBSIDIAN
                    || lookup.get(sideX2, y + h, sideZ2) != Blocks.OBSIDIAN) return null;
            for (int w = 0; w < width; w++) {
                if (!Fluids.isReplaceable(lookup.get(startX + dx * w, y + h, startZ + dz * w))) return null;
            }
        }
        return null;
    }

    /** 지옥문 state bit0 = 바닐라 {@code NetherPortalBlock.AXIS}(0=x, 1=z). 정적판 {@code standalonePortalAxisState}. */
    static final int PORTAL_AXIS_Z_STATE = 1;

    static int portalAxisState(PortalFrame frame) {
        return frame.axisX ? 0 : PORTAL_AXIS_Z_STATE;
    }

    static void fillPortal(PortalFrame frame, PortalChange change) {
        int dx = frame.axisX ? 1 : 0;
        int dz = frame.axisX ? 0 : 1;
        for (int h = 0; h < frame.height; h++) {
            for (int w = 0; w < frame.width; w++) {
                change.set(frame.startX + dx * w, frame.startY + h, frame.startZ + dz * w);
            }
        }
    }

    /** 파괴된 필수 프레임 칸과 면으로 맞닿은 연결 포털을 전부 끈다. */
    private void extinguishPortalsTouching(int x, int y, int z) {
        int[][] directions = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] direction : directions) {
            int sx = x + direction[0];
            int sy = y + direction[1];
            int sz = z + direction[2];
            if (residentBlockType(rt.accessor(), sx, sy, sz) == Blocks.NETHER_PORTAL) {
                removeConnectedPortal(sx, sy, sz,
                        (qx, qy, qz) -> residentBlockType(rt.accessor(), qx, qy, qz),
                        (px, py, pz) -> rt.fluidSim().applyChange(px, py, pz, Blocks.AIR));
            }
        }
    }

    static int removeConnectedPortal(int x, int y, int z, Fluids.BlockLookup lookup, PortalChange removal) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> queued = new HashSet<>();
        BlockPos start = new BlockPos(x, y, z);
        queue.add(start);
        queued.add(start);
        int removed = 0;
        int[][] directions = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (lookup.get(pos.x(), pos.y(), pos.z()) != Blocks.NETHER_PORTAL) continue;
            removal.set(pos.x(), pos.y(), pos.z());
            removed++;
            for (int[] direction : directions) {
                int nx = pos.x() + direction[0];
                int ny = pos.y() + direction[1];
                int nz = pos.z() + direction[2];
                if (ny < Blocks.MIN_Y || ny > Blocks.MAX_Y) continue;
                BlockPos next = new BlockPos(nx, ny, nz);
                if (lookup.get(nx, ny, nz) == Blocks.NETHER_PORTAL && queued.add(next)) queue.addLast(next);
            }
        }
        return removed;
    }

    private void processDimensionPortals(long tickNo) {
        for (PlayerTickState player : rt.players().values()) {
            int contact = player.isDead() ? 0 : contactDimensionPortal(player);
            if (player.portalDwell().observe(tickNo, contact)) rt.requestDimensionTravel(player, contact);
        }
    }

    /**
     * [END-GATEWAY] {@code EndGatewayBlock.entityInside} → {@link EndGatewaySystem}: 쿨다운이 아닌 엔드 관문에 닿은
     * 플레이어는 관문을 식히고, 살아 있는 월드에서 푼 출구로 같은 차원 안에서 순간이동한다. 차원 이동이 아니다.
     */
    private void processEndGateways(long tickNo) {
        rt.endGateways().tick(tickNo);
    }

    // ── [BEACON] 신호기 블록 엔티티 틱 ─────────────────────────────────────────
    /**
     * 신호기 좌표 → 바닐라 {@code levels}(마지막으로 센 피라미드 층 수). 바닐라 {@code loadAdditional} 이
     * {@code Levels} 를 읽지 않으므로 이 표도 영속하지 않고 적재 뒤 0 에서 다시 센다. 좌표 목록의 정본은
     * {@link com.gameexpert.terrain.TerrainAccessor#beaconPositions()} 다.
     */
    private final Map<BlockPos, Integer> beaconLevels = new HashMap<>();

    /** 신호기 화면(BeaconMenu data slot 0)에 싣는 현재 층 수. 추적 전이면 0. */
    int beaconLevelsAt(int x, int y, int z) {
        return beaconLevels.getOrDefault(new BlockPos(x, y, z), 0);
    }

    /**
     * 바닐라 {@code BeaconBlockEntity.tick} 의 10 TPS 이식({@link BeaconRules} 머리말). 맥박 틱마다 빔을
     * 끝까지 한 번에 훑고, 빔이 있으면 층을 다시 센 뒤 효과·ambient 소리를 내고, 층 0 ↔ 양수 전이에서
     * activate/deactivate 소리를 낸다. 좌표 색인에서 빠진 신호기는 {@code setRemoved} 처럼
     * deactivate 소리를 낸다(청크 폐기로 빠진 경우는 조용히 잊는다).
     */
    private void processBeacons(long tickNo) {
        TerrainAccessor accessor = rt.accessor();
        List<int[]> indexed = accessor.beaconPositions();
        if (indexed.isEmpty() && beaconLevels.isEmpty()) return;
        Set<BlockPos> live = new HashSet<>();
        for (int[] position : indexed) live.add(new BlockPos(position[0], position[1], position[2]));
        var tracked = beaconLevels.entrySet().iterator();
        while (tracked.hasNext()) {
            BlockPos pos = tracked.next().getKey();
            if (live.contains(pos)) continue;
            tracked.remove();
            closeCraftingSubscribersAt(pos.x(), pos.y(), pos.z());
            if (accessor.residentBlock(pos.x(), pos.y(), pos.z()).isAvailable()) {
                broadcastWorldSound("beacon_deactivate", pos.x(), pos.y(), pos.z(), (short) Blocks.BEACON);
            }
        }
        for (BlockPos pos : live) beaconLevels.putIfAbsent(pos, 0);
        if (!BeaconRules.pulseDue(tickNo)) return;
        BeaconRules.BlockSampler sampler = (qx, qy, qz) -> residentBlockType(accessor, qx, qy, qz);
        BeaconRules.BeamBlocker blocker = (qx, qy, qz, id) -> id != Blocks.BEDROCK
                && id != UNAVAILABLE_BLOCK
                && MobLightEngine.blocksLight(id, rt.blockStates().get(qx, qy, qz, id));
        for (BlockPos pos : live) {
            int x = pos.x();
            int y = pos.y();
            int z = pos.z();
            if (residentBlockType(accessor, x, y, z) != Blocks.BEACON) continue;
            int previous = beaconLevels.getOrDefault(pos, 0);
            boolean beam = !BeaconRules.beamSections(sampler, blocker, x, y, z, Blocks.MAX_Y).isEmpty();
            int levels = beam ? BeaconRules.levels(sampler, x, y, z, Blocks.MIN_Y) : previous;
            beaconLevels.put(pos, levels);
            if (levels > 0 && beam) {
                int state = rt.blockStates().get(x, y, z, Blocks.BEACON);
                applyBeaconEffects(x, y, z, levels, BeaconRules.primary(state), BeaconRules.secondary(state));
                broadcastWorldSound("beacon_ambient", x, y, z, (short) Blocks.BEACON);
            }
            if (previous <= 0 && levels > 0) {
                broadcastWorldSound("beacon_activate", x, y, z, (short) Blocks.BEACON);
            } else if (previous > 0 && levels <= 0) {
                broadcastWorldSound("beacon_deactivate", x, y, z, (short) Blocks.BEACON);
            }
            if (previous != levels) refreshBeaconMenus(x, y, z);
        }
    }

    /** {@code BeaconBlockEntity.applyEffects}: 효과 상자 안 플레이어에게 ambient 효과를 준다. */
    private void applyBeaconEffects(int x, int y, int z, int levels,
            BeaconRules.Power primary, BeaconRules.Power secondary) {
        if (primary == null) return;
        int duration = BeaconRules.durationMcTicks(levels);
        int amplifier = BeaconRules.primaryAmplifier(levels, primary, secondary);
        boolean second = BeaconRules.appliesSecondary(levels, primary, secondary);
        for (PlayerTickState player : rt.players().values()) {
            if (player.isDead()) continue;
            double height = player.crouching() ? 1.5 : 1.8;
            if (!BeaconRules.reaches(levels, x, y, z, Blocks.CHUNK_Y,
                    player.x() - 0.3, player.y(), player.z() - 0.3,
                    player.x() + 0.3, player.y() + height, player.z() + 0.3)) continue;
            player.applyAmbientStatusEffectMcTicks(primary.effect(), amplifier, duration);
            if (second) player.applyAmbientStatusEffectMcTicks(secondary.effect(), 0, duration);
        }
    }

    // ── [JUKEBOX] 주크박스 ────────────────────────────────────────────────────────
    /**
     * 음반이 든 주크박스 블록 엔티티(음반 스택 · 마지막으로 영속한 곡 시계). 빈 주크박스는 행이 없다.
     * {@link WorldRuntime#installJukeboxPersistence} 가 world_jukeboxes 행으로 채운다.
     */
    private final Map<BlockPos, com.gameexpert.jukebox.dto.JukeboxBlockData> jukeboxes = new HashMap<>();
    /**
     * 재생 중인 주크박스 → 살아 있는 {@code ticksSinceSongStarted}(MC 틱). 매 틱 도는 시계이고,
     * {@link com.gameexpert.engine.jukebox.JukeboxRules#CHECKPOINT_SERVER_TICKS} 마다 · 곡 끝 · 음반 교체
     * 때 행으로 영속되어 재시작 뒤 그 자리에서 이어진다(바닐라 {@code ticks_since_song_started}).
     */
    private final Map<BlockPos, Long> jukeboxTicks = new HashMap<>();
    /**
     * 접속자별로 현재 곡을 이미 받은 주크박스. 새 접속자(재시작 뒤 재접속 포함)에게는 재생 중인 곡을
     * 경과 틱과 함께 한 번 보낸다. 칸이 아직 상주하지 않아 곡을 모르는 주크박스는 표시하지 않고 다음
     * 틱에 다시 본다(접속 첫 틱에 청크가 아직 없으면 영영 빠지던 결함).
     */
    private final Map<String, Set<BlockPos>> jukeboxSyncedPlayers = new HashMap<>();
    private final java.util.Random jukeboxRandom = new java.util.Random();
    private int jukeboxCheckpointCountdown = com.gameexpert.engine.jukebox.JukeboxRules.CHECKPOINT_SERVER_TICKS;

    /** 재생 중인가(JukeboxBlock.getSignal 의 isPlaying). */
    boolean jukeboxPlaying(int x, int y, int z) {
        return jukeboxTicks.containsKey(new BlockPos(x, y, z));
    }

    /** 이 칸 주크박스가 든 음반 스택. 비었으면 {@code StackSnapshot.EMPTY}. */
    PlayerInventory.StackSnapshot jukeboxDisc(int x, int y, int z) {
        var state = jukeboxes.get(new BlockPos(x, y, z));
        return state == null ? PlayerInventory.StackSnapshot.EMPTY : state.disc();
    }

    /** 테스트 · 진단용: 살아 있는 곡 시계(조용하면 null). */
    Long jukeboxLiveTicks(int x, int y, int z) {
        return jukeboxTicks.get(new BlockPos(x, y, z));
    }

    /**
     * 적재한 world_jukeboxes 행을 설치한다. 곡 시계가 있는 행은 그 자리에서 곡을 이어 간다
     * ({@code JukeboxBlockEntity.loadAdditional → setSongWithoutPlaying}).
     */
    void installJukeboxes(List<com.gameexpert.jukebox.dto.JukeboxBlockData> stored) {
        jukeboxes.clear();
        jukeboxTicks.clear();
        for (var state : stored) {
            BlockPos pos = new BlockPos(state.x(), state.y(), state.z());
            jukeboxes.put(pos, state);
            if (state.ticksSinceSongStarted() != null) jukeboxTicks.put(pos, state.ticksSinceSongStarted());
        }
    }

    /**
     * 주크박스 우클릭. 음반이 들어 있으면 꺼내고(바닐라 useItemOn 은 HAS_RECORD 면 빈손 사용으로
     * 넘긴다), 비었고 손에 음반이 있으면 한 장을 성분째 넣고 재생한다. 처리했으면 true.
     */
    private boolean applyJukeboxInteract(PlayerTickState player, PlayerInventory.HandRef hand,
            int x, int y, int z, short selected) {
        PlayerInventory.StackSnapshot inside = jukeboxDisc(x, y, z);
        if (!inside.isEmpty()) {
            removeJukeboxRow(x, y, z);
            popOutJukeboxDisc(x, y, z, inside);
            stopJukebox(x, y, z);
            setJukeboxState(x, y, z, false);
            return true;
        }
        if (!com.gameexpert.engine.jukebox.JukeboxRules.isMusicDisc(selected)) return false;
        PlayerInventory.StackSnapshot held = player.inventory().stack(hand);
        if (!com.gameexpert.engine.jukebox.JukeboxRules.canInsert(held)) return true;
        // JukeboxPlayable.tryInsertIntoJukebox: consumeAndReturn(1) — 한 장, 모든 성분 그대로.
        var loaded = new com.gameexpert.jukebox.dto.JukeboxBlockData(x, y, z,
                new PlayerInventory.StackSnapshot(held.itemType(), 1, held.durability(), held.enchantments(),
                        held.mapId(), held.shulkerId(), held.bucketMobData(), held.itemComponentData()), 0L);
        var persistence = rt.jukeboxPersistence();
        if (persistence == null) {
            if (!InventoryRules.consumeOne(player.inventory(), hand, selected)) return true;
        } else {
            PlayerInventory.CompletePersistenceSnapshot source =
                    player.inventory().acquireSettlementLease();
            if (source == null) return true;
            PlayerInventory planned = source.detachedInventory();
            PlayerInventory.HandRef plannedHand = planned.capture(hand.hand());
            if (!planned.consumeOne(plannedHand, selected)) {
                player.inventory().releaseSettlementLease(source);
                return true;
            }
            PlayerInventory.CompletePersistenceSnapshot committed = planned.completePersistenceSnapshot();
            try {
                var outcome = persistence.insertWithPlayer(source.revision(),
                        rt.playerInventoryMutationSnapshot(player, committed), loaded);
                if (outcome != com.gameexpert.jukebox.service.JukeboxPersistenceService
                        .PlayerSettlementOutcome.COMMITTED
                        || !player.inventory().installCommittedSettlement(source, committed)) {
                    player.inventory().releaseSettlementLease(source);
                    return true;
                }
            } catch (RuntimeException failed) {
                player.inventory().releaseSettlementLease(source);
                log.warn("월드 {} 주크박스 음반 설치 정산 실패 ({},{},{}): {}", rt.worldId(), x, y, z,
                        failed.toString());
                return true;
            }
        }
        sendTo(player, inventoryMessage(player));
        jukeboxes.put(new BlockPos(x, y, z), loaded);
        setJukeboxState(x, y, z, true);
        startJukebox(x, y, z, selected);
        return true;
    }

    /** 호퍼 · 테스트용: 빈 주크박스에 음반 한 장을 성분째 넣고 재생한다. 넣었으면 true. */
    boolean insertJukeboxDisc(int x, int y, int z, PlayerInventory.StackSnapshot disc) {
        if (residentBlockType(rt.accessor(), x, y, z) != Blocks.JUKEBOX || disc == null
                || !com.gameexpert.engine.jukebox.JukeboxRules.canInsert(disc)
                || !jukeboxDisc(x, y, z).isEmpty()) return false;
        var loaded = new com.gameexpert.jukebox.dto.JukeboxBlockData(x, y, z,
                new PlayerInventory.StackSnapshot(disc.itemType(), 1, disc.durability(), disc.enchantments(),
                        disc.mapId(), disc.shulkerId(), disc.bucketMobData(), disc.itemComponentData()), 0L);
        if (!persistJukebox(loaded)) return false;
        jukeboxes.put(new BlockPos(x, y, z), loaded);
        setJukeboxState(x, y, z, true);
        startJukebox(x, y, z, disc.itemType());
        return true;
    }

    /** 호퍼용: 주크박스의 음반을 꺼낸다(ContainerSingleItem.removeItem). 없으면 EMPTY. */
    PlayerInventory.StackSnapshot takeJukeboxDisc(int x, int y, int z) {
        if (residentBlockType(rt.accessor(), x, y, z) != Blocks.JUKEBOX) return PlayerInventory.StackSnapshot.EMPTY;
        PlayerInventory.StackSnapshot inside = jukeboxDisc(x, y, z);
        if (inside.isEmpty()) return inside;
        removeJukeboxRow(x, y, z);
        stopJukebox(x, y, z);
        setJukeboxState(x, y, z, false);
        return inside;
    }

    /** 블록이 사라진 주크박스: 행을 지우고 음반을 튀어나오게 하고 곡을 멈춘다. */
    void removeJukeboxAt(int x, int y, int z) {
        PlayerInventory.StackSnapshot inside = jukeboxDisc(x, y, z);
        removeJukeboxRow(x, y, z);
        if (!inside.isEmpty()) popOutJukeboxDisc(x, y, z, inside);
        stopJukebox(x, y, z);
    }

    /** 흘리지 않고 바뀐 주크박스(setBlock 치환): 행만 버린다(바닐라 블록 엔티티 제거와 같다). */
    void discardJukeboxAt(int x, int y, int z) {
        removeJukeboxRow(x, y, z);
        stopJukebox(x, y, z);
    }

    private void removeJukeboxRow(int x, int y, int z) {
        if (jukeboxes.remove(new BlockPos(x, y, z)) == null) return;
        var persistence = rt.jukeboxPersistence();
        if (persistence != null) persistence.remove(rt.worldId(), x, y, z);
    }

    private boolean persistJukebox(com.gameexpert.jukebox.dto.JukeboxBlockData state) {
        var persistence = rt.jukeboxPersistence();
        if (persistence == null) return true;
        try {
            persistence.save(rt.worldId(), state);
            return true;
        } catch (RuntimeException failed) {
            log.warn("월드 {} 주크박스 행 저장 실패 ({},{},{}): {}", rt.worldId(), state.x(), state.y(),
                    state.z(), failed.toString());
            return false;
        }
    }

    /**
     * {@code JukeboxBlockEntity.popOutTheItem}: (0.5, 1.01, 0.5) 에서 XZ ±0.35 흩어 떨군다. 스택의
     * 성분(이름 등)은 그대로 옮긴다.
     */
    private void popOutJukeboxDisc(int x, int y, int z, PlayerInventory.StackSnapshot disc) {
        double spread = com.gameexpert.engine.jukebox.JukeboxRules.POP_OUT_SPREAD;
        double dx = (jukeboxRandom.nextDouble() * 2 - 1) * spread / 2;
        double dz = (jukeboxRandom.nextDouble() * 2 - 1) * spread / 2;
        rt.itemSystem().spawnDrop(disc.itemType(), 1, disc.durability(), disc.enchantments(),
                disc.mapId(), disc.shulkerId(), disc.bucketMobData(), disc.itemComponentData(),
                x + 0.5 + dx, y + com.gameexpert.engine.jukebox.JukeboxRules.POP_OUT_Y, z + 0.5 + dz);
    }

    private void setJukeboxState(int x, int y, int z, boolean hasRecord) {
        int state = com.gameexpert.engine.jukebox.JukeboxRules.stateFor(hasRecord);
        if (rt.blockStates().get(x, y, z, Blocks.JUKEBOX) == state) return;
        rt.setBlockState(x, y, z, Blocks.JUKEBOX, state);
        rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) Blocks.JUKEBOX);
    }

    private void startJukebox(int x, int y, int z, short disc) {
        var song = com.gameexpert.engine.jukebox.JukeboxRules.songFor(disc);
        if (song == null) return;
        BlockPos pos = new BlockPos(x, y, z);
        jukeboxTicks.put(pos, 0L);
        redstone.engine().sourceChanged(x, y, z);
        forgetJukeboxSync(pos);
        for (PlayerTickState player : rt.players().values()) {
            if (sendTo(player, new com.gameexpert.ws.dto.WsMessages.JukeboxSongUpdate(x, y, z, song.key(), 0L, true))) {
                jukeboxSyncedPlayers.computeIfAbsent(player.nickname(), ignored -> new HashSet<>()).add(pos);
            }
        }
    }

    private void forgetJukeboxSync(BlockPos pos) {
        for (Set<BlockPos> synced : jukeboxSyncedPlayers.values()) synced.remove(pos);
    }

    private void stopJukebox(int x, int y, int z) {
        if (jukeboxTicks.remove(new BlockPos(x, y, z)) == null) return;
        redstone.engine().sourceChanged(x, y, z);
        forgetJukeboxSync(new BlockPos(x, y, z));
        // [MOB-LOOK] JukeboxSongPlayer.stop → GameEvent.JUKEBOX_STOP_PLAY.
        rt.mobSystem().jukeboxGameEvent(x, y, z, false);
        broadcastJukeboxSong(new com.gameexpert.ws.dto.WsMessages.JukeboxSongUpdate(x, y, z, null, 0L, false));
    }

    private void broadcastJukeboxSong(com.gameexpert.ws.dto.WsMessages.JukeboxSongUpdate message) {
        for (PlayerTickState player : rt.players().values()) sendTo(player, message);
    }

    /** 상주하는 주크박스 칸의 곡. 블록이 바뀌었거나 비었으면 null. */
    private com.gameexpert.engine.jukebox.JukeboxRules.Song residentJukeboxSong(BlockPos pos) {
        if (residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z()) != Blocks.JUKEBOX) return null;
        return com.gameexpert.engine.jukebox.JukeboxRules.songFor(jukeboxDisc(pos.x(), pos.y(), pos.z()).itemType());
    }

    /**
     * 한 권위 틱의 {@code JukeboxSongPlayer.tick} 두 번. 블록이 바뀌었거나 곡이 끝났으면 멈춘다(끝난
     * 곡은 HAS_RECORD 와 음반을 그대로 두고 시계를 지운 행을 적는다). 주기마다 살아 있는 시계를 행으로
     * 영속하고, 새 접속자에게는 재생 중인 곡을 경과 틱과 함께 한 번 보낸다.
     */
    private void processJukeboxes() {
        jukeboxSyncedPlayers.keySet().retainAll(rt.players().keySet());
        if (jukeboxTicks.isEmpty()) {
            jukeboxCheckpointCountdown = com.gameexpert.engine.jukebox.JukeboxRules.CHECKPOINT_SERVER_TICKS;
            return;
        }
        List<com.gameexpert.jukebox.dto.JukeboxBlockData> writes = new ArrayList<>();
        var iterator = jukeboxTicks.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            BlockPos pos = entry.getKey();
            int block = residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z());
            if (block == UNAVAILABLE_BLOCK) continue;
            var song = residentJukeboxSong(pos);
            int[] playEvents = new int[1];
            long next = song == null ? -1L
                    : com.gameexpert.engine.jukebox.JukeboxRules.advance(song, entry.getValue(), playEvents);
            // [MOB-LOOK] JukeboxSongPlayer.tick: GameEvent.JUKEBOX_PLAY every 20 MC ticks (allays dance).
            if (playEvents[0] > 0) rt.mobSystem().jukeboxGameEvent(pos.x(), pos.y(), pos.z(), true);
            if (next < 0) {
                iterator.remove();
                redstone.engine().sourceChanged(pos.x(), pos.y(), pos.z());
                forgetJukeboxSync(pos);
                rt.mobSystem().jukeboxGameEvent(pos.x(), pos.y(), pos.z(), false);
                broadcastJukeboxSong(new com.gameexpert.ws.dto.WsMessages.JukeboxSongUpdate(
                        pos.x(), pos.y(), pos.z(), null, 0L, false));
                var stored = jukeboxes.get(pos);
                if (song != null && stored != null && stored.ticksSinceSongStarted() != null) {
                    var silent = stored.withTicks(null);
                    jukeboxes.put(pos, silent);
                    writes.add(silent);
                }
                continue;
            }
            entry.setValue(next);
        }
        if (--jukeboxCheckpointCountdown <= 0) {
            jukeboxCheckpointCountdown = com.gameexpert.engine.jukebox.JukeboxRules.CHECKPOINT_SERVER_TICKS;
            for (var entry : jukeboxTicks.entrySet()) {
                var stored = jukeboxes.get(entry.getKey());
                if (stored == null || entry.getValue().equals(stored.ticksSinceSongStarted())
                        || residentJukeboxSong(entry.getKey()) == null) continue;
                var checkpoint = stored.withTicks(entry.getValue());
                jukeboxes.put(entry.getKey(), checkpoint);
                writes.add(checkpoint);
            }
        }
        var persistence = rt.jukeboxPersistence();
        if (!writes.isEmpty() && persistence != null) {
            try {
                persistence.saveAll(rt.worldId(), writes);
            } catch (RuntimeException failed) {
                log.warn("월드 {} 주크박스 곡 시계 체크포인트 실패: {}", rt.worldId(), failed.toString());
            }
        }
        for (PlayerTickState player : rt.players().values()) {
            if (rt.session(player.nickname()) == null) continue;
            Set<BlockPos> synced = jukeboxSyncedPlayers.computeIfAbsent(player.nickname(), ignored -> new HashSet<>());
            for (var entry : jukeboxTicks.entrySet()) {
                BlockPos pos = entry.getKey();
                if (synced.contains(pos)) continue;
                var song = residentJukeboxSong(pos);
                if (song == null) continue;
                if (sendTo(player, new com.gameexpert.ws.dto.WsMessages.JukeboxSongUpdate(pos.x(), pos.y(), pos.z(),
                        song.key(), entry.getValue(), false))) {
                    synced.add(pos);
                }
            }
        }
    }

    /** 층 수가 바뀌면 그 신호기 화면을 연 플레이어에게 갱신을 보낸다(바닐라 data slot 동기화). */
    private void refreshBeaconMenus(int x, int y, int z) {
        for (PlayerTickState player : rt.players().values()) {
            if (player.openCraftingStation() == PlayerAction.CraftStation.BEACON
                    && samePosition(player.openCraftingTable(), x, y, z)) {
                sendTo(player, craftingMessage(player, false));
            }
        }
    }

    boolean touchesDimensionPortal(PlayerTickState player, int block) {
        return block != 0 && contactDimensionPortal(player) == block;
    }

    private int contactDimensionPortal(PlayerTickState player) {
        int minX = (int) Math.floor(player.x() - 0.3);
        int maxX = (int) Math.floor(player.x() + 0.3);
        int minY = (int) Math.floor(player.y());
        int maxY = (int) Math.floor(player.y() + 1.8);
        int minZ = (int) Math.floor(player.z() - 0.3);
        int maxZ = (int) Math.floor(player.z() + 0.3);
        for (int py = minY; py <= maxY; py++) for (int pz = minZ; pz <= maxZ; pz++) {
            for (int px = minX; px <= maxX; px++) {
                int block = residentBlockType(rt.accessor(), px, py, pz);
                if (rt.dimensionPortalEligible(block)) return block;
            }
        }
        return 0;
    }

    // ── [VILLAGER-TRADE] 주민 거래 권위 ────────────────────────────────────────
    private final com.gameexpert.engine.mob.villager.VillagerTradeSessions villagerTrades =
            new com.gameexpert.engine.mob.villager.VillagerTradeSessions();
    private com.gameexpert.engine.mob.villager.VillagerProfessionSource villagerProfessions =
            com.gameexpert.engine.mob.villager.VillagerProfessionSource.NONE;

    /**
     * 직업 조회기를 배선한다. 정본은 {@code MobSystem} 의 POI 점유 원장이고,
     * {@code WorldRuntime} 생성자가 이 메서드로 붙인다. 순수 조회라 거래 경로는 원장을 바꾸지
     * 않는다(잠금만 {@code applyVillagerTrade} 가 원장에 쓴다).
     */
    public void bindVillagerProfessions(
            com.gameexpert.engine.mob.villager.VillagerProfessionSource source) {
        this.villagerProfessions = source == null
                ? com.gameexpert.engine.mob.villager.VillagerProfessionSource.NONE : source;
    }

    /**
     * 평판 원장을 배선한다. 정본은 주민 각자의 gossip 원장이고, 거래 화면 가격·결제와
     * 거래 후 TRADING 적립이 모두 이 원장을 본다.
     */
    public void bindVillagerReputations(
            com.gameexpert.engine.mob.villager.VillagerReputationSource source) {
        villagerTrades.bindReputations(source);
    }

    public com.gameexpert.engine.mob.villager.VillagerTradeSessions villagerTrades() {
        return villagerTrades;
    }

    private void openVillagerTrade(PlayerTickState player, long mobId) {
        Mob merchant = rt.mobSystem().combat().findAlive(mobId);
        var profession = merchant != null && merchant.type == MobType.WANDERING_TRADER
                ? com.gameexpert.engine.mob.villager.VillagerTradeRules.Profession.WANDERING_TRADER
                : villagerProfessions.professionOf(mobId);
        if (!com.gameexpert.engine.mob.villager.VillagerTradeRules.tradesAtAll(profession)) {
            rt.mobSystem().rejectVillagerTradeVisual(mobId);
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error("VILLAGER_NOT_TRADING"));
            return;
        }
        closeActiveMenuBeforeOpen(player);
        var view = villagerTrades.open(player.nickname(), mobId, profession,
                com.gameexpert.engine.mob.villager.VillagerTradeRules.offerDraws(mobId, 1));
        if (view == null) {
            rt.mobSystem().rejectVillagerTradeVisual(mobId);
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error("VILLAGER_NOT_TRADING"));
            return;
        }
        sendTo(player, villagerTradeOpen(player, view));
    }

    private void applyVillagerTradeSelect(PlayerAction.VillagerTradeSelect action) {
        PlayerTickState player = rt.players().get(action.nickname());
        if (player == null || player.isDead()) return;
        villagerTrades.selectAndFill(action.nickname(), action.mobId(), action.offer(),
                player.inventory());
        sendTo(player, inventoryMessage(player));
        var view = villagerTrades.viewFor(action.nickname(), action.mobId());
        if (view != null) sendTo(player, villagerTradeUpdate(player, view));
    }

    private boolean validVillagerTradeSession(PlayerTickState player, long mobId) {
        if (player == null || player.isDead() || !villagerTrades.hasSession(player.nickname(), mobId)) {
            return false;
        }
        Mob mob = rt.mobSystem().combat().findAlive(mobId);
        if (mob != null && (mob.type == MobType.VILLAGER || mob.type == MobType.WANDERING_TRADER) && CombatRules.withinAuthorityReach(
                player.x(), player.y(), player.z(), player.crouching(),
                mob.x, mob.y, mob.z, mob.width(), mob.height())) return true;
        returnVillagerPayments(player);
        dropCraftingOverflow(player, player.inventory().closeContainerCursor());
        villagerTrades.close(player.nickname());
        sendTo(player, inventoryMessage(player));
        sendTo(player, new com.gameexpert.ws.dto.WsMessages.VillagerTradeClosed(mobId));
        return false;
    }

    private void applyVillagerTradeClick(PlayerAction.VillagerTradeClick click) {
        PlayerTickState player = rt.players().get(click.nickname());
        if (!validVillagerTradeSession(player, click.mobId())) return;
        if (click.area() == PlayerAction.VillagerTradeArea.RESULT) {
            if (click.slot() == 0) {
                boolean toCursor = !click.shift();
                if (toCursor) {
                    int selected = villagerTrades.selectedOffer(click.nickname());
                    var view = villagerTrades.viewFor(click.nickname(), click.mobId());
                    if (view == null || selected < 0 || selected >= view.offers().size()) return;
                    var offer = view.offers().get(selected);
                    if (!player.inventory().canAcceptContainerResult(
                            offer.resultPrototype(), offer.resultCount())) return;
                }
                applyVillagerTrade(new PlayerAction.VillagerTrade(
                        click.nickname(), click.mobId(), -1), toCursor);
            }
            return;
        }
        var payment = villagerTrades.paymentAccess(click.nickname());
        if (payment == null) return;
        var area = click.area() == PlayerAction.VillagerTradeArea.INVENTORY
                ? PlayerInventory.ContainerArea.INVENTORY
                : PlayerInventory.ContainerArea.CONTAINER;
        if (!player.inventory().clickContainer(payment, area, click.slot(),
                click.button(), click.shift())) {
            var view = villagerTrades.viewFor(click.nickname(), click.mobId());
            if (view != null) sendTo(player, villagerTradeUpdate(player, view));
            return;
        }
        sendTo(player, inventoryMessage(player));
        var view = villagerTrades.viewFor(click.nickname(), click.mobId());
        if (view != null) sendTo(player, villagerTradeUpdate(player, view));
    }

    private void applyVillagerTradeDrag(PlayerAction.VillagerTradeDrag drag) {
        PlayerTickState player = rt.players().get(drag.nickname());
        if (!validVillagerTradeSession(player, drag.mobId())) return;
        var payment = villagerTrades.paymentAccess(drag.nickname());
        if (payment == null || drag.areas().length != drag.slots().length) return;
        var areas = new PlayerInventory.ContainerArea[drag.areas().length];
        for (int index = 0; index < areas.length; index++) {
            if (drag.areas()[index] == PlayerAction.VillagerTradeArea.RESULT) return;
            areas[index] = drag.areas()[index] == PlayerAction.VillagerTradeArea.INVENTORY
                    ? PlayerInventory.ContainerArea.INVENTORY
                    : PlayerInventory.ContainerArea.CONTAINER;
        }
        if (!player.inventory().dragContainer(payment, areas, drag.slots(), drag.button())) return;
        sendTo(player, inventoryMessage(player));
        var view = villagerTrades.viewFor(drag.nickname(), drag.mobId());
        if (view != null) sendTo(player, villagerTradeUpdate(player, view));
    }

    private void applyCollectVillagerTrade(PlayerAction.CollectVillagerTrade collect) {
        PlayerTickState player = rt.players().get(collect.nickname());
        if (!validVillagerTradeSession(player, collect.mobId())) return;
        var payment = villagerTrades.paymentAccess(collect.nickname());
        if (payment == null || collect.area() == PlayerAction.VillagerTradeArea.RESULT
                || collect.slot() < 0 || collect.slot() >= (collect.area()
                        == PlayerAction.VillagerTradeArea.INVENTORY
                                ? PlayerInventory.SLOTS : payment.slotCount())
                || !player.inventory().collectContainer(payment)) return;
        sendTo(player, inventoryMessage(player));
        var view = villagerTrades.viewFor(collect.nickname(), collect.mobId());
        if (view != null) sendTo(player, villagerTradeUpdate(player, view));
    }

    private void applyDropVillagerTradeCursor(PlayerAction.DropVillagerTradeCursor drop) {
        PlayerTickState player = rt.players().get(drop.nickname());
        if (!validVillagerTradeSession(player, drop.mobId())) return;
        settleCursorGroundDrop(player, false, drop.one(), () -> {
            sendTo(player, inventoryMessage(player));
            var view = villagerTrades.viewFor(drop.nickname(), drop.mobId());
            if (view != null) sendTo(player, villagerTradeUpdate(player, view));
        });
    }

    boolean hasPendingCartographyPersistence() {
        return !pendingEmptyMapSettlements.isEmpty()
                || !pendingCartographySettlements.isEmpty();
    }

    /** Runtime block/state mutations share the same localized light-cache invalidation seam as mobs. */
    void invalidateRandomTickLightColumn(int x, int z) {
        randomTickLight.invalidateColumn(x, z);
    }

    /** Chunk replacement/eviction must release random-tick light source references and dependent memos. */
    void invalidateRandomTickLightChunk(int chunkX, int chunkZ) {
        randomTickLight.invalidateChunk(chunkX, chunkZ);
    }

    private void applyVillagerTrade(PlayerAction.VillagerTrade action) {
        applyVillagerTrade(action, false);
    }

    private void applyVillagerTrade(PlayerAction.VillagerTrade action, boolean toCursor) {
        PlayerTickState player = rt.players().get(action.nickname());
        if (player == null || player.isDead()) return;
        Mob mob = rt.mobSystem().combat().findAlive(action.mobId());
        if (mob == null || (mob.type != MobType.VILLAGER && mob.type != MobType.WANDERING_TRADER) || !CombatRules.withinAuthorityReach(
                player.x(), player.y(), player.z(), player.crouching(),
                mob.x, mob.y, mob.z, mob.width(), mob.height())) {
            returnVillagerPayments(player);
            villagerTrades.close(action.nickname());
            sendTo(player, inventoryMessage(player));
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error("VILLAGER_TRADE_REJECTED"));
            return;
        }
        int selected = action.offer() >= 0
                ? action.offer() : villagerTrades.selectedOffer(action.nickname());
        if (selected < 0) return;
        if (!villagerTrades.foldPayments(action.nickname(), player.inventory())) {
            sendTo(player, inventoryMessage(player));
            com.gameexpert.engine.mob.villager.VillagerTradeSessions.TradeView view =
                    villagerTrades.viewFor(action.nickname(), action.mobId());
            if (view != null) sendTo(player, villagerTradeUpdate(player, view));
            return;
        }
        var result = villagerTrades.trade(action.nickname(), action.mobId(), selected,
                player.inventory(),
                com.gameexpert.engine.mob.villager.VillagerTradeRules.offerDraws(
                        action.mobId(), 1));
        if (!result.accepted()) {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error(
                    "VILLAGER_TRADE_" + result.rejection().name()));
            return;
        }
        // 바닐라: 첫 거래 한 번이 직업을 영구히 잠근다. 이후 직업지를 잃어도 초기화되지 않는다.
        if (mob.type == MobType.VILLAGER) rt.mobSystem().villagerJobClaims().lockByTrade(action.mobId());
        // [SURV-X] 바닐라 AbstractVillager#rewardTradeXp: 성사된 거래마다 3 + rand(4) 의 경험치
        // 구슬이 **주민 자리**(getY() + 0.5)에서 나온다. 구슬 스폰은 채굴·제련과 같은
        // xpOrbSystem().spawnOrbs 경로를 그대로 쓴다.
        rt.xpOrbSystem().spawnOrbs(
                XpRules.xpForVillagerTrade(tradeXpRandom.nextInt(1000)),
                mob.x, mob.y + 0.5, mob.z);
        if (toCursor) {
            int inserted=result.resultCount()-result.overflowCount();
            if(inserted>0 && !player.inventory().moveInventoryResultToCursor(result.resultPrototype(),inserted)) throw new IllegalStateException("validated merchant result did not reach cursor");
            if(result.overflowCount()>0 && !player.inventory().appendContainerResult(result.resultPrototype(),result.overflowCount())) throw new IllegalStateException("validated merchant overflow did not reach cursor");
        } else if (result.overflowCount() > 0) {
            var prototype=result.resultPrototype();
            rt.itemSystem().spawnDrop(result.resultItem(),result.overflowCount(),prototype.durability(),prototype.enchantments(),prototype.mapId(),prototype.shulkerId(),prototype.bucketMobData(),prototype.itemComponentData(),player.x(),player.y()+0.5,player.z());
        }
        sendTo(player, inventoryMessage(player));
        var view = villagerTrades.viewFor(action.nickname(), action.mobId());
        if (view != null) {
            sendTo(player, villagerTradeUpdate(player, view));
        }
    }

    /**
     * [VILLAGER-TRADE] 이번 틱 재입고가 일어난 주민을 보고 있는 화면에 최신 오퍼를 민다.
     * 바닐라 {@code Villager#restock} 끝의 {@code resendOffersToTradingPlayer()} 와 같으며,
     * 새 메시지를 만들지 않고 거래 성사와 같은 {@code villagerTradeUpdate} 를 재사용한다.
     */
    private void resendRestockedVillagerOffers() {
        var resends = villagerTrades.drainOfferResends();
        if (resends.isEmpty()) return;
        for (var resend : resends) {
            PlayerTickState player = rt.players().get(resend.nickname());
            if (player == null) continue;
            var view = villagerTrades.viewFor(resend.nickname(), resend.mobId());
            if (view == null) continue; // 그 사이 화면이 닫혔다
            sendTo(player, villagerTradeUpdate(player, view));
        }
    }

    /** Moving/dead/despawned villagers close the menu before the tick can publish another state. */
    private void reconcileVillagerTradeSessions() {
        for (PlayerTickState player : rt.players().values()) {
            Long mobId = villagerTrades.openMobId(player.nickname());
            if (mobId == null) continue;
            Mob mob = rt.mobSystem().combat().findAlive(mobId);
            if (mob != null && (mob.type == MobType.VILLAGER || mob.type == MobType.WANDERING_TRADER) && CombatRules.withinAuthorityReach(
                    player.x(), player.y(), player.z(), player.crouching(),
                    mob.x, mob.y, mob.z, mob.width(), mob.height())) continue;
            returnVillagerPayments(player);
            dropCraftingOverflow(player, player.inventory().closeContainerCursor());
            villagerTrades.close(player.nickname());
            sendTo(player, inventoryMessage(player));
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.VillagerTradeClosed(mobId));
        }
    }

    void returnVillagerPayments(PlayerTickState player) {
        dropCraftingOverflow(player, villagerTrades.returnPayments(player.nickname(), player.inventory()));
    }

    private void applyCloseVillagerTrade(PlayerAction.CloseVillagerTrade action) {
        PlayerTickState player = rt.players().get(action.nickname());
        if (player == null) return;
        Long openMobId = villagerTrades.openMobId(action.nickname());
        if (openMobId != null && openMobId == action.mobId()) {
            returnVillagerPayments(player);
            dropCraftingOverflow(player, player.inventory().closeContainerCursor());
            villagerTrades.close(action.nickname());
            sendTo(player, inventoryMessage(player));
        }
        sendTo(player, new com.gameexpert.ws.dto.WsMessages.VillagerTradeClosed(action.mobId()));
    }

    private static java.util.List<com.gameexpert.ws.dto.WsMessages.VillagerTradeOfferDto>
            villagerTradeOffers(
                    com.gameexpert.engine.mob.villager.VillagerTradeSessions.TradeView view) {
        return view.offers().stream()
                .map(offer -> new com.gameexpert.ws.dto.WsMessages.VillagerTradeOfferDto(
                        Short.toUnsignedInt(offer.costItem()), offer.costCount(),
                        Short.toUnsignedInt(offer.costBItem()), offer.costBCount(),
                        Short.toUnsignedInt(offer.resultItem()), offer.resultCount(),
                        offer.uses(), offer.maxUses(), offer.locked(),
                        craftingStack(com.gameexpert.engine.mob.villager.VillagerTradeStacks.count(offer.costPrototype(),offer.costCount())),
                        craftingStack(offer.costBItem()==0?PlayerInventory.StackSnapshot.EMPTY:com.gameexpert.engine.mob.villager.VillagerTradeStacks.count(offer.costBPrototype(),offer.costBCount())),
                        craftingStack(com.gameexpert.engine.mob.villager.VillagerTradeStacks.count(offer.resultPrototype(),offer.resultCount()))))
                .toList();
    }

    private com.gameexpert.ws.dto.WsMessages.VillagerTradeOpen villagerTradeOpen(
            PlayerTickState player,
            com.gameexpert.engine.mob.villager.VillagerTradeSessions.TradeView view) {
        var menu = villagerTradeMenu(player, view);
        return new com.gameexpert.ws.dto.WsMessages.VillagerTradeOpen(
                view.mobId(), view.level(), view.tradeXp(), villagerTradeOffers(view),
                menu.selected(), menu.payment(), menu.result(), menu.cursor());
    }

    private com.gameexpert.ws.dto.WsMessages.VillagerTradeUpdate villagerTradeUpdate(
            PlayerTickState player,
            com.gameexpert.engine.mob.villager.VillagerTradeSessions.TradeView view) {
        var menu = villagerTradeMenu(player, view);
        return new com.gameexpert.ws.dto.WsMessages.VillagerTradeUpdate(
                view.mobId(), view.level(), view.tradeXp(), villagerTradeOffers(view),
                menu.selected(), menu.payment(), menu.result(), menu.cursor());
    }

    private record VillagerMenuWire(int selected,
            java.util.List<com.gameexpert.ws.dto.WsMessages.CraftingStack> payment,
            com.gameexpert.ws.dto.WsMessages.CraftingStack result,
            com.gameexpert.ws.dto.WsMessages.CraftingStack cursor) {}

    /**
     * Merchant inputs are an authoritative projection of the selected offer. Execution remains a
     * distinct result-slot action; therefore selecting a row can never increment uses or grant XP.
     */
    private VillagerMenuWire villagerTradeMenu(PlayerTickState player,
            com.gameexpert.engine.mob.villager.VillagerTradeSessions.TradeView view) {
        int selected = villagerTrades.selectedOffer(player.nickname());
        var empty = craftingStack((short) 0, 0, 0, 0L, 0, 0, null);
        var payment = new java.util.ArrayList<com.gameexpert.ws.dto.WsMessages.CraftingStack>(2);
        var result = empty;
        if (selected >= 0 && selected < view.offers().size()) {
            var offer = view.offers().get(selected);
            var held = villagerTrades.paymentAccess(player.nickname());
            payment.add(held == null ? empty : craftingStack(held.itemType(0), held.count(0),
                    held.durability(0), held.enchantments(0), held.mapId(0), held.shulkerId(0),
                    held.bucketMobData(0), held.itemComponentData(0)));
            payment.add(held == null ? empty : craftingStack(held.itemType(1), held.count(1),
                    held.durability(1), held.enchantments(1), held.mapId(1), held.shulkerId(1),
                    held.bucketMobData(1), held.itemComponentData(1)));
            boolean ready = held != null && com.gameexpert.engine.mob.villager.VillagerTradeStacks.matches(offer.costPrototype(),com.gameexpert.engine.mob.villager.VillagerTradeStacks.at(held,0))
                    && held.count(0) >= offer.costCount()
                    && (offer.costBItem() == 0 ? held.itemType(1) == 0
                            : com.gameexpert.engine.mob.villager.VillagerTradeStacks.matches(offer.costBPrototype(),com.gameexpert.engine.mob.villager.VillagerTradeStacks.at(held,1))
                                    && held.count(1) >= offer.costBCount());
            if (ready && !offer.locked() && offer.uses() < offer.maxUses()) {
                result = craftingStack(com.gameexpert.engine.mob.villager.VillagerTradeStacks.count(offer.resultPrototype(),offer.resultCount()));
            }
        } else {
            payment.add(empty);
            payment.add(empty);
        }
        var inv = player.inventory();
        var cursor = craftingStack(inv.cursorType(), inv.cursorCount(), inv.cursorDurability(),
                inv.cursorEnchantments(), inv.cursorMapId(), inv.cursorShulkerId(),
                inv.cursorBucketMobData(), inv.cursorItemComponentData());
        return new VillagerMenuWire(selected, java.util.List.copyOf(payment), result, cursor);
    }

    // ── [MOUNT] 상자 말 계열 화물 세션 ─────────────────────────────────────────
    /**
     * 이 플레이어가 그 몹의 화물을 계속 볼 수 있는가. 주민 거래와 같은 거리 계약
     * ({@link CombatRules#withinAuthorityReach})을 쓰고, 상자를 잃었거나 죽은 개체면 null 이다.
     */
    private Mob reachableCargoMob(PlayerTickState player, long mobId) {
        Mob mob = rt.mobSystem().combat().findAlive(mobId);
        if (mob == null || mob.horseEquipment() == null || !mob.horseTamed()) return null;
        return withinHorseMenuReach(player.x(), player.y(), player.z(), mob.x, mob.y, mob.z)
                ? mob : null;
    }

    static boolean withinHorseMenuReach(
            double playerX, double playerY, double playerZ,
            double mobX, double mobY, double mobZ) {
        double dx = playerX - mobX;
        double dy = playerY - mobY;
        double dz = playerZ - mobZ;
        return dx * dx + dy * dy + dz * dz < 16.0;
    }

    private static int mobCargoColumns(Mob mob) {
        return com.gameexpert.engine.mob.ChestedHorseRules.inventoryColumns(
                mob.type, mob.horseChested(), mob.llamaStrength());
    }

    /** Exact menu order: SADDLE=0, BODY=1, then cargo row-major. */
    private static List<InventorySlot> mobCargoSlots(Mob mob) {
        var menu = new com.gameexpert.engine.inventory.HorseMenuContainerAccess(mob);
        List<InventorySlot> slots = new ArrayList<>(menu.slotCount());
        for (int i = 0; i < menu.slotCount(); i++) {
            short type = menu.itemType(i);
            Integer durability = PlayerInventory.isDurable(type) ? menu.durability(i) : null;
            slots.add(inventorySlot(i, type, menu.count(i), durability,
                    menu.enchantments(i), mapIdOrNull(menu.mapId(i)),
                    shulkerIdOrNull(menu.shulkerId(i)), menu.bucketMobData(i),
                    menu.itemComponentData(i)));
        }
        return slots;
    }

    private void openMobCargo(PlayerTickState player, Mob mob) {
        if (mob.horseEquipment() == null || !mob.horseTamed()) {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error("MOB_INTERACT_REJECTED"));
            return;
        }
        closeActiveMenuBeforeOpen(player);
        player.openMobCargo(mob.id);
        rt.queueMobMenuBaseline(player);
        sendTo(player, new com.gameexpert.ws.dto.WsMessages.MobCargoOpen(
                mob.id, mob.type.name().toLowerCase(java.util.Locale.ROOT), mob.variant(),
                mob.isBaby(), mob.customName(), mobCargoColumns(mob), mobCargoSlots(mob)));
    }

    /** 세션을 끝내고 그 사실을 알린다. 열려 있지 않으면 아무 것도 보내지 않는다(멱등). */
    /** [CONTAINER-MENUS] {@link PlacedEntitySystem} opens a cargo menu or a seat: close the rest. */
    void closeMenusForPlacedEntity(PlayerTickState player) {
        closeActiveMenuBeforeOpen(player);
    }

    /** [CONTAINER-MENUS] A closed entity cargo menu returns the cursor like a chest does. */
    void returnContainerCursor(PlayerTickState player) {
        dropCraftingOverflow(player, player.inventory().closeContainerCursor());
        sendTo(player, inventoryMessage(player));
        sendContainerCursor(player);
    }

    /** [CONTAINER-MENUS] Wire slots of any container view (placed chest and hopper minecarts). */
    static List<InventorySlot> containerSlots(com.gameexpert.engine.inventory.ContainerAccess access) {
        List<InventorySlot> slots = new ArrayList<>(access.slotCount());
        for (int i = 0; i < access.slotCount(); i++) {
            short type = access.itemType(i);
            Integer durability = PlayerInventory.isDurable(type) ? access.durability(i) : null;
            slots.add(inventorySlot(i, type, access.count(i), durability,
                    access.enchantments(i), mapIdOrNull(access.mapId(i)),
                    shulkerIdOrNull(access.shulkerId(i)), access.bucketMobData(i),
                    access.itemComponentData(i)));
        }
        return slots;
    }

    private void closeMobCargoSession(PlayerTickState player) {
        long mobId = player.openMobCargo();
        if (mobId == 0L) return;
        // [CONTAINER-CURSOR] 상자와 같은 반환 규칙. 몹이 죽어 세션이 끊겨도 커서는 잃지 않는다.
        dropCraftingOverflow(player, player.inventory().closeContainerCursor());
        player.closeMobCargo();
        sendTo(player, inventoryMessage(player));
        sendContainerCursor(player);
        sendTo(player, new com.gameexpert.ws.dto.WsMessages.MobCargoClosed(mobId));
    }

    /**
     * A player owns one menu ledger. Every successful open goes through this transition so all
     * transient stacks are folded and every close event is emitted before the next open event.
     */
    private void closeActiveMenuBeforeOpen(PlayerTickState player) {
        player.cancelPendingCraftingOpen();
        Long villagerMobId = villagerTrades.openMobId(player.nickname());
        if (villagerMobId != null) {
            returnVillagerPayments(player);
            dropCraftingOverflow(player, player.inventory().closeContainerCursor());
            villagerTrades.close(player.nickname());
            sendTo(player, inventoryMessage(player));
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.VillagerTradeClosed(villagerMobId));
        }
        closeGeneratedMinecartCargoSession(player);
        player.cancelGeneratedEntityEquipmentSettlement();
        rt.placedEntities().closeCargo(player, true);
        closeMobCargoSession(player);
        closeChestSession(player, true);
        closeFurnaceSession(player);
        closeEnchantingSession(player);
        if (player.openCraftingStation() != null) closeCraftingSession(player, true);
    }

    /**
     * 열린 화물과 인벤토리 사이의 전체 스택 이동. 상자 {@link #applyMoveChestItem} 과 같은
     * 계약이고(한 번에 한 슬롯 전체, 들어간 만큼만 소비) 경계는 개체별 칸 수로 좁아진다.
     */
    private void applyMoveMobCargoItem(PlayerAction.MoveMobCargoItem move) {
        PlayerTickState player = rt.players().get(move.nickname());
        if (player == null || player.openMobCargo() != move.mobId()) return;
        Mob mob = reachableCargoMob(player, move.mobId());
        if (mob == null) {
            closeMobCargoSession(player);
            return;
        }
        var menu = new com.gameexpert.engine.inventory.HorseMenuContainerAccess(mob);
        int moved = moveLegacyMobCargoStack(player.inventory(), mob, menu,
                move.fromCargo(), move.slot());
        if (moved <= 0) {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.MobCargoUpdate(
                    mob.id, mob.type.name().toLowerCase(java.util.Locale.ROOT), mob.variant(),
                    mob.isBaby(), mob.customName(), mobCargoColumns(mob), mobCargoSlots(mob)));
            return;
        }
        rt.mobSystem().refreshCargoPersistence(mob);
        sendTo(player, inventoryMessage(player));
        broadcastMobCargoUpdate(mob);
    }

    private static int moveLegacyMobCargoStack(PlayerInventory inventory, Mob mob,
            com.gameexpert.engine.inventory.HorseMenuContainerAccess menu,
            boolean fromCargo, int slot) {
        if (inventory == null || mob == null || menu == null
                || inventory.settlementLeased()
                || !canAdvancePersistenceRevision(inventory.revision())
                || !canAdvancePersistenceRevision(mob.horseMenuPersistenceRevision())
                || mob.horseEquipment() == null
                || !canAdvancePersistenceRevision(mob.horseEquipment().persistenceRevision())
                || mob.horseCargo() != null
                        && !canAdvancePersistenceRevision(mob.horseCargo().persistenceRevision())) {
            return 0;
        }
        if (fromCargo) {
            if (slot < 0 || slot >= menu.slotCount()
                    || menu.itemType(slot) == PlayerInventory.EMPTY || menu.count(slot) <= 0) {
                return 0;
            }
            PlayerInventory.StackSnapshot source = new PlayerInventory.StackSnapshot(
                    menu.itemType(slot), menu.count(slot), menu.durability(slot),
                    menu.enchantments(slot), menu.mapId(slot), menu.shulkerId(slot),
                    menu.bucketMobData(slot), menu.itemComponentData(slot));
            int added = inventory.addItem(source.itemType(), source.count(), source.durability(),
                    source.enchantments(), source.mapId(), source.shulkerId(),
                    source.bucketMobData(), source.itemComponentData());
            if (added <= 0 || added > source.count()) return 0;
            int removed = menu.take(slot, added);
            return removed == added ? added : 0;
        }
        if (slot < 0 || slot >= PlayerInventory.SLOTS) return 0;
        PlayerInventory.DroppedStack source = inventory.stackAt(slot);
        if (source == null) return 0;
        int added = menu.insert(source.itemType(), source.count(), source.durability(),
                source.enchantments(), source.mapId(), source.shulkerId(),
                source.bucketMobData(), source.itemComponentData());
        if (added <= 0 || added > source.count()) return 0;
        PlayerInventory.DroppedStack removed = inventory.dropFromSlot(slot, added);
        return sameStackIdentity(removed, source, added) ? added : 0;
    }

    private void applyCloseMobCargo(PlayerAction.CloseMobCargo close) {
        PlayerTickState player = rt.players().get(close.nickname());
        if (player == null) return;
        if (player.openMobCargo() == close.mobId()) closeMobCargoSession(player);
        else sendTo(player, new com.gameexpert.ws.dto.WsMessages.MobCargoClosed(close.mobId()));
    }

    private void applyOpenMountedMobInventory(PlayerAction.OpenMountedMobInventory open) {
        PlayerTickState player = rt.players().get(open.nickname());
        if (player == null) return;
        Mob mob = reachableCargoMob(player, open.mobId());
        if (mob == null || !mob.isHorseMenuRider(open.nickname())) return;
        openMobCargo(player, mob);
    }

    /** 같은 몹을 보고 있는 모든 화면에 최신 재고를 민다(상자 broadcastChestUpdate 와 같은 계약). */
    private void broadcastMobCargoUpdate(Mob mob) {
        com.gameexpert.ws.dto.WsMessages.MobCargoUpdate update =
                new com.gameexpert.ws.dto.WsMessages.MobCargoUpdate(
                        mob.id, mob.type.name().toLowerCase(java.util.Locale.ROOT), mob.variant(),
                        mob.isBaby(), mob.customName(), mobCargoColumns(mob), mobCargoSlots(mob));
        for (PlayerTickState viewer : rt.players().values()) {
            if (viewer.openMobCargo() == mob.id) sendTo(viewer, update);
        }
    }

    /** 죽음·소멸·사거리 이탈로 더 볼 수 없게 된 화물 세션을 닫는다. */
    private void reconcileMobCargoSessions() {
        for (PlayerTickState player : rt.players().values()) {
            if (player.openMobCargo() == 0L) continue;
            if (reachableCargoMob(player, player.openMobCargo()) == null) {
                closeMobCargoSession(player);
            }
        }
    }

    /** Canonical H12g wire order: every one of the 27 authority slots, including empties. */
    static List<InventorySlot> generatedMinecartCargoSlots(
            com.gameexpert.engine.persistence.finalcarrier.structureentity
                    .WorldGeneratedStructureEntityState.MinecartRuntimeSnapshot minecart) {
        InventorySlot[] slots = new InventorySlot[27];
        for (int slot = 0; slot < slots.length; slot++) {
            slots[slot] = inventorySlot(slot, PlayerInventory.EMPTY, 0, null,
                    EnchantmentRules.EMPTY_ENCHANTMENTS, null, null, null, null);
        }
        for (com.gameexpert.chest.entity.ChestItem item : minecart.cargo()) {
            int slot = item.getSlot();
            if (slot < 0 || slot >= slots.length
                    || slots[slot].getItemType() != PlayerInventory.EMPTY) {
                throw new IllegalStateException("generated minecart cargo slot identity drift");
            }
            slots[slot] = inventorySlot(slot, item.getItemType(), item.getItemCount(),
                    item.getDurability(), item.enchantmentsOrZero(), item.getMapId(),
                    item.getShulkerId(), item.getBucketMobData(), item.getItemComponentData());
        }
        return List.of(slots);
    }

    private static CraftingStack generatedCargoCursor(PlayerTickState player) {
        PlayerInventory inv = player.inventory();
        return craftingStack(inv.cursorType(), inv.cursorCount(), inv.cursorDurability(),
                inv.cursorEnchantments(), inv.cursorMapId(), inv.cursorShulkerId(),
                inv.cursorBucketMobData(), inv.cursorItemComponentData());
    }

    private static com.gameexpert.ws.dto.WsMessages.MinecartCargoTarget generatedCargoTarget(
            PlayerTickState.GeneratedEntityCargoSession session) {
        return new com.gameexpert.ws.dto.WsMessages.MinecartCargoTarget(
                session.entityId(), session.sessionId());
    }

    /** Called by the Runtime first-open bridge only after durable resolution is installed. */
    private com.gameexpert.ws.dto.WsMessages.MinecartCargoTarget
            publishGeneratedMinecartCargoOpen(PlayerTickState player,
            com.gameexpert.engine.persistence.finalcarrier.structureentity
                    .WorldGeneratedStructureEntityState.MinecartRuntimeSnapshot minecart, Long requestId) {
        long sessionId = player.openGeneratedEntityCargo(
                minecart.binding().entityId(), minecart.revision());
        var target = new com.gameexpert.ws.dto.WsMessages.MinecartCargoTarget(
                minecart.binding().entityId(), sessionId);
        sendTo(player, new com.gameexpert.ws.dto.WsMessages.MinecartCargoOpen(target,
                generatedMinecartCargoSlots(minecart), generatedCargoCursor(player), requestId));
        return target;
    }

    /** Called only after atomic settlement and exact source-revision advancement succeed. */
    private void publishGeneratedMinecartCargoUpdate(PlayerTickState player,
            com.gameexpert.engine.persistence.finalcarrier.structureentity
                    .WorldGeneratedStructureEntityState.MinecartRuntimeSnapshot minecart) {
        PlayerTickState.GeneratedEntityCargoSession session = player.openGeneratedEntityCargo();
        if (session == null || session.entityId() != minecart.binding().entityId()
                || session.sourceRevision() != minecart.revision()) {
            throw new IllegalStateException("generated cargo publication revision is stale");
        }
        sendTo(player, new com.gameexpert.ws.dto.WsMessages.MinecartCargoUpdate(
                generatedCargoTarget(session), generatedMinecartCargoSlots(minecart),
                generatedCargoCursor(player)));
    }

    private void closeGeneratedMinecartCargoSession(PlayerTickState player) {
        PlayerTickState.GeneratedEntityCargoSession session = player.openGeneratedEntityCargo();
        if (session == null) return;
        var target = generatedCargoTarget(session);
        var current = rt.generatedStructureEntities().snapshotByEntityId(session.entityId());
        PendingFinalScenePrerequisites pending = generatedFinalScenePending(player, "H12g");
        if (current != null && pending != null) {
            rt.recordGeneratedMinecartClosed(player.nickname(), current, target,
                    pending.actionNonce(), pending.deadlineTick());
        }
        dropCraftingOverflow(player, player.inventory().closeContainerCursor());
        player.closeGeneratedEntityCargo();
        sendTo(player, inventoryMessage(player));
        sendContainerCursor(player);
        sendTo(player, new com.gameexpert.ws.dto.WsMessages.MinecartCargoClosed(
                target));
    }

    private PendingFinalScenePrerequisites generatedFinalScenePending(
            PlayerTickState player, String scenario) {
        if (player == null) return null;
        PendingFinalScenePrerequisites pending =
                pendingFinalScenePrerequisites.get(player.nickname());
        return pending != null && pending.player() == player
                && scenario.equals(pending.scenario()) ? pending : null;
    }

    private static boolean generatedEntityWithinReach(PlayerTickState player,
            com.gameexpert.engine.persistence.finalcarrier.structureentity
                    .WorldGeneratedStructureEntityState.RuntimeSnapshot target) {
        var transform = target.transform();
        return CombatRules.withinAuthorityReach(player.x(), player.y(), player.z(),
                player.crouching(), transform.x(), transform.y(), transform.z(), 1.0, 1.0);
    }

    /** Removal, unload, revision change and range loss invalidate the exact menu capability. */
    private void reconcileGeneratedEntityCargoSessions() {
        for (PlayerTickState player : rt.players().values()) {
            PlayerTickState.GeneratedEntityCargoSession session =
                    player.openGeneratedEntityCargo();
            if (session == null) continue;
            var current = resolveGeneratedActionTarget(rt.generatedStructureEntities(),
                    com.gameexpert.engine.persistence.finalcarrier.structureentity
                            .GeneratedStructureEntityFacts.Kind.CHEST_MINECART,
                    session.entityId());
            if (!(current instanceof com.gameexpert.engine.persistence.finalcarrier
                    .structureentity.WorldGeneratedStructureEntityState.MinecartRuntimeSnapshot cart)
                    || cart.revision() != session.sourceRevision()
                    || !generatedEntityWithinReach(player, cart)) {
                closeGeneratedMinecartCargoSession(player);
            }
        }
    }

    /** 거북 AI의 산란 요청을 월드 블록 권위에서 검증하고, 실패는 다음 틱 재시도하게 확정한다. */
    private void applyTurtleEggPlacementRequests() {
        for (MobRuntime.TurtleEggPlacementRequest request
                : rt.mobSystem().drainTurtleEggPlacementRequests()) {
            boolean placed = tryPlaceTurtleEgg(
                    request.x(), request.y(), request.z(), request.eggCount());
            rt.mobSystem().confirmTurtleEggPlacement(request.turtleMobId(), placed);
        }
    }

    private void applyFrogspawnPlacementRequests() {
        for (MobRuntime.FrogspawnPlacementRequest request
                : rt.mobSystem().drainFrogspawnPlacementRequests()) {
            boolean placed = false;
            if (rt.animalBlockTickStore() == null) {
                throw new IllegalStateException("frogspawn scheduling persistence is required");
            }
            if (residentBlockType(rt.accessor(), request.x(), request.y(), request.z()) == AIR
                    && request.y() > Blocks.MIN_Y
                    && Fluids.isWaterMedium(
                            residentBlockType(rt.accessor(), request.x(), request.y() - 1,
                                    request.z()))) {
                rt.fluidSim().applyChange(request.x(), request.y(), request.z(), Blocks.FROGSPAWN);
                placed = residentBlockType(rt.accessor(), request.x(), request.y(), request.z())
                        == Blocks.FROGSPAWN;
                if (placed) {
                    BlockPos pos = new BlockPos(request.x(), request.y(), request.z());
                    rt.tickBlockChanges().put(pos, (short) Blocks.FROGSPAWN);
                    scheduleAnimalBlockIfNeeded(request.x(), request.y(), request.z());
                    broadcastWorldSound("block_place", request.x(), request.y(), request.z(),
                            (short) Blocks.FROGSPAWN);
                }
            }
            rt.mobSystem().confirmFrogspawnPlacement(
                    request.sourceMobId(), request.partnerMobId(), placed);
        }
    }

    private static final int BEE_HONEY_LEVEL_SHIFT = 2;
    private static final int BEE_HONEY_LEVEL_MASK = 0x1c;
    private static final int BEE_MAX_HONEY_LEVEL = 5;

    private void applyBeeHiveEntryRequests() {
        for (MobSystem.BeeHiveEntryRequest request : rt.mobSystem().drainBeeHiveEntries()) {
            int block = residentBlockType(rt.accessor(), request.x(), request.y(), request.z());
            if (block != Blocks.BEE_NEST && block != Blocks.BEEHIVE) continue;
            int honeyIncrease = rt.mobSystem().commitBeeHiveEntry(request);
            if (honeyIncrease == 0) continue;
            int state = rt.blockStates().get(request.x(), request.y(), request.z(), block);
            int level = Math.min(BEE_MAX_HONEY_LEVEL,
                    ((state & BEE_HONEY_LEVEL_MASK) >>> BEE_HONEY_LEVEL_SHIFT) + honeyIncrease);
            int next = (state & ~BEE_HONEY_LEVEL_MASK) | level << BEE_HONEY_LEVEL_SHIFT;
            rt.setBlockState(request.x(), request.y(), request.z(), block, next);
            rt.tickBlockChanges().put(
                    new BlockPos(request.x(), request.y(), request.z()), (short) block);
        }
    }

    private void applyFroglightSettlementRequests() {
        var settlements = rt.animalSettlements();
        if (settlements == null) {
            if (rt.mobSystem().hasPendingFroglightSettlements()) {
                throw new IllegalStateException(
                        "animal settlements are required for Froglight drops");
            }
            return;
        }
        List<MobSystem.FroglightSettlementRequest> requests =
                rt.mobSystem().drainFroglightSettlements();
        for (MobSystem.FroglightSettlementRequest request : requests) {
            long entityId = rt.itemSystem().reserveSettlementEntityId();
            FroglightSettlementWork work = new FroglightSettlementWork(null,
                    request.frogMobId(), request.sulfurCubeMobId(), entityId,
                    request.itemType(), request.x(), request.y(), request.z());
            pendingFroglightWork.putIfAbsent(work.key(), work);
        }
        if (!pendingFroglightsHydrated && !pendingFroglightHydrationInFlight) {
            pendingFroglightHydrationInFlight = true;
            if (!submitAnimalSettlement(() -> {
                List<com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService
                        .FroglightIntent> pending;
                try {
                    pending = settlements.pendingFroglights(rt.worldId());
                } catch (RuntimeException | Error failure) {
                    pending = null;
                }
                List<com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService
                        .FroglightIntent> result = pending == null ? null : List.copyOf(pending);
                rt.enqueuePersistenceCompletion(() -> {
                    pendingFroglightHydrationInFlight = false;
                    if (result == null) return;
                    pendingFroglightRecovery.addAll(result);
                    pendingFroglightsHydrated = true;
                });
            })) {
                pendingFroglightHydrationInFlight = false;
            }
        }
        while (!pendingFroglightRecovery.isEmpty()) {
            var intent = pendingFroglightRecovery.removeFirst();
            FroglightSettlementWork work = new FroglightSettlementWork(intent,
                    intent.frogMobId(), intent.sulfurCubeMobId(), intent.entityId(),
                    intent.itemType(), intent.x(), intent.y(), intent.z());
            pendingFroglightWork.putIfAbsent(work.key(), work);
        }
        for (FroglightSettlementWork work : List.copyOf(pendingFroglightWork.values())) {
            submitFroglightSettlement(settlements, work);
        }
    }

    private void submitFroglightSettlement(
            com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService settlements,
            FroglightSettlementWork work) {
        if (!animalSettlementInFlight.add(work.key())) return;
        Runnable transaction = () -> {
            com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService
                    .FroglightIntent intent = work.recovered();
            com.gameexpert.ground.dto.GroundItemSnapshot item = null;
            boolean committed = false;
            try {
                if (intent == null) {
                    intent = settlements.beginFroglight(rt.worldId(), work.frogMobId(),
                            work.sulfurCubeMobId(), work.proposedEntityId(), work.itemType(),
                            work.x(), work.y(), work.z());
                }
                item = animalSettlementDropSnapshot(intent.entityId(), intent.itemType(),
                        intent.x(), intent.y(), intent.z());
                committed = settlements.commitFroglight(rt.worldId(), intent, item);
            } catch (RuntimeException | Error failure) {
                committed = false;
            }
            var durableIntent = intent;
            var durableItem = item;
            boolean durable = committed;
            rt.enqueuePersistenceCompletion(() -> {
                animalSettlementInFlight.remove(work.key());
                if (!durable || durableIntent == null || durableItem == null) return;
                // A later owner action may already have removed the cube; the durable item still
                // exists and must become visible in this runtime exactly once.
                rt.mobSystem().confirmFroglightSettlement(durableIntent.sulfurCubeMobId());
                rt.itemSystem().commitSettlementDrop(durableItem);
                pendingFroglightWork.remove(work.key());
                pendingAnimalSettlementRetirements.add(durableIntent.key());
            });
        };
        if (!submitAnimalSettlement(transaction)) animalSettlementInFlight.remove(work.key());
    }

    private static com.gameexpert.ground.dto.GroundItemSnapshot animalSettlementDropSnapshot(
            long entityId, short itemType, double x, double y, double z) {
        return new com.gameexpert.ground.dto.GroundItemSnapshot(entityId, itemType, 1,
                0, 0, 0, 0, null, null, x, y, z,
                0, ItemEntitySystem.MOB_DROP_UPWARD_SPEED, 0,
                false, 0, ItemEntitySystem.PICKUP_MIN_AGE, 0);
    }

    /** Re-enters rejected/captured animal work before WorldRuntime places its disposal barrier. */
    void retryAnimalSettlementPersistenceForDisposal() {
        // WorldRuntime already invokes this owner hook before its writer barrier. Cartography uses
        // the same immutable-command retry contract and must cross that barrier as well.
        pumpCartographySettlements();
        var settlements = rt.animalSettlements();
        if (settlements == null) return;
        recoverPendingAnimalHatches();
        if (!pendingFroglightsHydrated && !pendingFroglightHydrationInFlight) {
            applyFroglightSettlementRequests();
        }
        while (!pendingFroglightRecovery.isEmpty()) {
            var intent = pendingFroglightRecovery.removeFirst();
            FroglightSettlementWork work = new FroglightSettlementWork(intent,
                    intent.frogMobId(), intent.sulfurCubeMobId(), intent.entityId(),
                    intent.itemType(), intent.x(), intent.y(), intent.z());
            pendingFroglightWork.putIfAbsent(work.key(), work);
        }
        for (FroglightSettlementWork work : List.copyOf(pendingFroglightWork.values())) {
            submitFroglightSettlement(settlements, work);
        }
        for (SnifferSettlementWork work : List.copyOf(pendingSnifferWork.values())) {
            submitSnifferSettlement(settlements, work);
        }
        for (Runnable retry : List.copyOf(pendingCopperSettlementSubmissions.values())) retry.run();
        pumpAnimalSettlementRetirements(settlements);
    }

    boolean hasPendingAnimalSettlementPersistence() {
        boolean cartographyPending = hasPendingCartographyPersistence();
        if (!rt.hasAnimalSettlements()) return cartographyPending;
        return cartographyPending
                || !pendingHatchesHydrated || !pendingFroglightsHydrated
                || pendingHatchHydrationInFlight || pendingFroglightHydrationInFlight
                || pendingSnifferHydrationInFlight
                || !pendingHatchRecovery.isEmpty() || !pendingFroglightRecovery.isEmpty()
                || !hatchSchedulesOwnedBySettlement.isEmpty()
                || !pendingCopperSettlementSubmissions.isEmpty()
                || !pendingFroglightWork.isEmpty() || !pendingSnifferWork.isEmpty()
                || !pendingAnimalSettlementRetirements.isEmpty()
                || !animalSettlementInFlight.isEmpty();
    }

    /** Normal/disposal owner entry used to retry all captured animal settlement commands. */
    void pumpAnimalSettlementPersistence() {
        pumpCartographySettlements();
        if (rt.animalSettlements() == null) return;
        recoverPendingAnimalHatches();
        applyFroglightSettlementRequests();
        applySnifferDigDropRequests();
        for (Runnable retry : List.copyOf(pendingCopperSettlementSubmissions.values())) retry.run();
    }

    private boolean applyBeeHiveHarvest(PlayerTickState player, PlayerInventory.HandRef hand,
            PlayerAction.Interact interact, int block, short selected) {
        if (selected != PlayerInventory.GLASS_BOTTLE && selected != PlayerInventory.SHEARS) {
            return false;
        }
        if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                interact.x(), interact.y(), interact.z())) return true;
        int state = rt.blockStates().get(interact.x(), interact.y(), interact.z(), block);
        int honeyLevel = (state & BEE_HONEY_LEVEL_MASK) >>> BEE_HONEY_LEVEL_SHIFT;
        if (honeyLevel < BEE_MAX_HONEY_LEVEL) return true;
        PlayerInventory inventory = player.inventory();
        if (selected == PlayerInventory.GLASS_BOTTLE) {
            if (!inventory.replaceSingle(hand, PlayerInventory.GLASS_BOTTLE,
                    PlayerInventory.HONEY_BOTTLE)) {
                if (!inventory.consumeOne(hand, PlayerInventory.GLASS_BOTTLE)) return true;
                if (inventory.addItem(PlayerInventory.HONEY_BOTTLE, 1) != 1) {
                    rt.itemSystem().spawnDrop(PlayerInventory.HONEY_BOTTLE, 1,
                            interact.x() + 0.5, interact.y() + 0.5, interact.z() + 0.5);
                }
            }
            broadcastWorldSound("bottle_fill", interact.x(), interact.y(), interact.z(),
                    (short) block);
        } else {
            if (inventory.stack(hand).itemType() != PlayerInventory.SHEARS) return true;
            inventory.degrade(hand);
            rt.itemSystem().spawnDrop(PlayerInventory.HONEYCOMB, 3,
                    interact.x() + 0.5, interact.y() + 0.5, interact.z() + 0.5);
            broadcastWorldSound("shears", interact.x(), interact.y(), interact.z(),
                    (short) block);
        }
        int next = state & ~BEE_HONEY_LEVEL_MASK;
        rt.setBlockState(interact.x(), interact.y(), interact.z(), block, next);
        rt.tickBlockChanges().put(
                new BlockPos(interact.x(), interact.y(), interact.z()), (short) block);
        if (!beeHiveSmoked(interact.x(), interact.y(), interact.z())) {
            rt.mobSystem().releaseBeesFromHive(
                    interact.x(), interact.y(), interact.z(), player.nickname());
        }
        sendTo(player, inventoryMessage(player));
        return true;
    }

    private boolean beeHiveSmoked(int x, int y, int z) {
        for (int distance = 1; distance <= 5 && y - distance >= Blocks.MIN_Y; distance++) {
            int by = y - distance;
            int below = residentBlockType(rt.accessor(), x, by, z);
            if (below == UNAVAILABLE_BLOCK) return false;
            if (below == Blocks.CAMPFIRE) {
                int state = rt.blockStates().get(x, by, z, below);
                return (state & BuildingBlockRules.CAMPFIRE_LIT) != 0;
            }
            int state = rt.blockStates().get(x, by, z, below);
            if (BuildingBlockRules.blocksMotion(below, state)) return false;
        }
        return false;
    }

    private void applyGoatRamImpactRequests() {
        for (MobRuntime.GoatRamImpactRequest request : rt.mobSystem().pendingGoatRamImpacts()) {
            int current = residentBlockType(rt.accessor(), request.x(), request.y(), request.z());
            if (current != request.blockId()) {
                rt.mobSystem().confirmGoatRamImpactWithoutHorn(request);
                continue;
            }
            MobRuntime.GoatHornDropPlan plan = rt.mobSystem().planGoatHornDropAfterRam(request);
            if (plan != null) rt.mobSystem().confirmGoatHornDrop(plan);
            else rt.mobSystem().confirmGoatRamImpactWithoutHorn(request);
        }
    }

    /** Settles stable persisted Sniffer loot tokens in mob-id order; rejected tokens retry. */
    private void applySnifferDigDropRequests() {
        var requests = rt.mobSystem().pendingSnifferDigDrops();
        var settlements = rt.animalSettlements();
        if (settlements == null) {
            if (!requests.isEmpty() || !pendingSnifferWork.isEmpty()) {
                throw new IllegalStateException("animal settlement persistence is not installed");
            }
            return;
        }
        if (!pendingSniffersHydrated && !pendingSnifferHydrationInFlight) {
            pendingSnifferHydrationInFlight = true;
            if (!submitAnimalSettlement(() -> {
                List<com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService
                        .SnifferIntent> pending;
                try {
                    pending = settlements.pendingSniffer(rt.worldId());
                } catch (RuntimeException | Error failure) {
                    pending = null;
                }
                List<com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService
                        .SnifferIntent> result = pending == null ? null : List.copyOf(pending);
                rt.enqueuePersistenceCompletion(() -> {
                    pendingSnifferHydrationInFlight = false;
                    if (result == null) return;
                    for (var intent : result) {
                        MobRuntime.SnifferDigDropRequest request = requests.stream()
                                .filter(candidate -> candidate.sniffer().id == intent.mobId()
                                        && candidate.sequence() == intent.token())
                                .findFirst().orElse(null);
                        if (request == null) continue;
                        var cleared = rt.mobSystem().snifferClearedSettlementSnapshot(request);
                        if (cleared != null) pendingSnifferWork.putIfAbsent(intent.key(),
                                new SnifferSettlementWork(request, intent,
                                        intent.entityId(), cleared));
                    }
                    pendingSniffersHydrated = true;
                });
            })) {
                pendingSnifferHydrationInFlight = false;
            }
        }
        for (MobRuntime.SnifferDigDropRequest request : requests) {
            var cleared = rt.mobSystem().snifferClearedSettlementSnapshot(request);
            if (cleared == null) continue;
            String key = "sniffer:" + request.sniffer().id + ':' + request.sequence();
            pendingSnifferWork.computeIfAbsent(key, ignored -> new SnifferSettlementWork(
                    request, null, rt.itemSystem().reserveSettlementEntityId(), cleared));
        }
        for (SnifferSettlementWork work : List.copyOf(pendingSnifferWork.values())) {
            submitSnifferSettlement(settlements, work);
        }
    }

    private void submitSnifferSettlement(
            com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService settlements,
            SnifferSettlementWork work) {
        if (!animalSettlementInFlight.add(work.key())) return;
        Runnable transaction = () -> {
            com.gameexpert.engine.persistence.animal.AnimalSettlementPersistenceService
                    .SnifferIntent intent = work.recovered();
            com.gameexpert.ground.dto.GroundItemSnapshot item = null;
            boolean committed = false;
            try {
                var request = work.request();
                if (intent == null) {
                    intent = settlements.beginSniffer(rt.worldId(), request.sniffer().id,
                            request.sequence(), work.proposedEntityId(), request.itemType(),
                            request.x(), request.y(), request.z());
                }
                item = animalSettlementDropSnapshot(intent.entityId(), intent.itemType(),
                        intent.x(), intent.y(), intent.z());
                committed = settlements.commitSniffer(
                        rt.worldId(), intent, work.clearedMob(), item);
            } catch (RuntimeException | Error failure) {
                committed = false;
            }
            var durableIntent = intent;
            var durableItem = item;
            boolean durable = committed;
            rt.enqueuePersistenceCompletion(() -> {
                animalSettlementInFlight.remove(work.key());
                if (!durable || durableIntent == null || durableItem == null) return;
                boolean confirmed = rt.mobSystem().confirmSnifferDigDropToken(work.request());
                rt.itemSystem().commitSettlementDrop(durableItem);
                if (confirmed) {
                    rt.mobSystem().emitCommittedMobSound(work.request().sniffer().id, "digging");
                }
                pendingSnifferWork.remove(work.key());
                pendingAnimalSettlementRetirements.add(durableIntent.key());
            });
        };
        if (!submitAnimalSettlement(transaction)) animalSettlementInFlight.remove(work.key());
    }

    boolean tryPlaceTurtleEgg(int x, int y, int z) {
        return tryPlaceTurtleEgg(x, y, z, TurtleEggRules.MIN_EGGS);
    }

    boolean tryPlaceTurtleEgg(int x, int y, int z, int eggCount) {
        int target = residentBlockType(rt.accessor(), x, y, z);
        int below = y <= Blocks.MIN_Y
                ? UNAVAILABLE_BLOCK : residentBlockType(rt.accessor(), x, y - 1, z);
        if (target != AIR || !TurtleEggRules.canPlaceOn(below)) return false;
        rt.fluidSim().applyChange(x, y, z, Blocks.TURTLE_EGG);
        if (residentBlockType(rt.accessor(), x, y, z) != Blocks.TURTLE_EGG) return false;
        rt.setBlockState(x, y, z, Blocks.TURTLE_EGG,
                TurtleEggRules.state(eggCount, 0));
        rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) Blocks.TURTLE_EGG);
        broadcastWorldSound("block_place", x, y, z, (short) Blocks.TURTLE_EGG);
        return true;
    }

    /** 몹 우클릭: 동물 먹이·물고기 포획·암소 젖짜기를 대상과 선택 슬롯 기준으로 서버가 확정한다. */
    /** [FARM-ANIMAL] 기수 좌표 업링크. 플레이어가 없으면 조용히 버린다(보트 업링크와 같다). */
    private void applyPigPos(PlayerAction.PigPos pos) {
        PlayerTickState player = rt.players().get(pos.nickname());
        if (player == null) return;
        rt.mobSystem().ridePigPos(player, pos.mobId(), pos.x(), pos.y(), pos.z(), pos.yaw());
    }

    private void applyMobRiderPos(PlayerAction.MobRiderPos pos) {
        PlayerTickState player = rt.players().get(pos.nickname());
        if (player == null) return;
        rt.mobSystem().rideMobPos(player, pos.mobId(), pos.seatIndex(),
                pos.x(), pos.y(), pos.z(), pos.yaw());
    }

    private void applyMobJump(PlayerAction.MobJump jump) {
        PlayerTickState player = rt.players().get(jump.nickname());
        if (player == null || player.isDead()) return;
        rt.mobSystem().jumpMob(player, jump.mobId(), jump.charge());
    }

    private void applyMobInteract(PlayerAction.MobInteract interact) {
        // QA 관측: 몹 상호작용 요청 도달 자체를 남긴다(실측: 노틸러스 탑승 클릭이 어느 단계에서
        // 사라지는지 구분 불가). 판정은 바꾸지 않는다.
        log.info("mob interact: player={} mob={} hand={}", interact.nickname(), interact.mobId(), interact.hand());
        PlayerTickState player = rt.players().get(interact.nickname());
        if (player == null) return;
        if (player.isDead()) {
            sendCompanionInteractionResult(player, interact, "rejected");
            return;
        }
        PlayerInventory.HandRef hand = player.inventory().capture(inventoryHand(interact.hand()));

        Mob mob = rt.mobSystem().combat().findAlive(interact.mobId());
        if (mob == null || !CombatRules.withinAuthorityReach(
                player.x(), player.y(), player.z(), player.crouching(),
                mob.x, mob.y, mob.z, mob.width(), mob.height())) {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error(
                    "MOB_INTERACT_REJECTED"));
            sendCompanionInteractionResult(player, interact, "rejected");
            return;
        }

        if (com.gameexpert.engine.mob.CompanionRules.isCompanion(mob.type)) {
            try {
                String result = applyCompanionHand(player, hand, mob);
                if ("pass".equals(result) && hand.hand() == PlayerInventory.Hand.MAIN) {
                    result = applyCompanionHand(player,
                            player.inventory().capture(PlayerInventory.Hand.OFFHAND), mob);
                }
                sendCompanionInteractionResult(player, interact, result);
            } catch (RuntimeException failure) {
                sendCompanionInteractionResult(player, interact, "rejected");
                throw failure;
            }
            return;
        }
        try {
            applyOrdinaryMobInteract(player, hand, mob);
        } finally {
            // A correlated request always receives exactly one result. A non-companion target is
            // handled as an ordinary interaction, but it is never a companion use the client may continue.
            sendCompanionInteractionResult(player, interact, "rejected");
        }
    }

    private void applyOrdinaryMobInteract(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        // [EC-MOBS] 아이템 액자: 넣기(한 개) · 회전. 이름표·끈보다 먼저다(ItemFrame.interact 가 손 아이템을 받는다).
        if (mob instanceof com.gameexpert.engine.mob.ItemFrame frame) {
            if (rt.mobSystem().interactItemFrame(player, hand, frame)) sendTo(player, inventoryMessage(player));
            return;
        }
        short heldItem = player.inventory().stack(hand).itemType();
        if (heldItem == PlayerInventory.NAME_TAG) {
            applyNameTag(player, hand, mob);
            return;
        }
        if (mob.type == MobType.COPPER_GOLEM) {
            if (heldItem == PlayerInventory.HONEYCOMB) {
                if (rt.mobSystem().waxCopperGolem(player, hand, mob)) {
                    broadcastWorldSoundExact("block_place", mob.x, mob.y, mob.z,
                            PlayerInventory.HONEYCOMB);
                }
                return;
            }
            if (EnchantmentRules.isAxeItem(heldItem)) {
                if (rt.mobSystem().scrapeCopperGolem(player, hand, mob)) {
                    broadcastWorldSoundExact("block_place", mob.x, mob.y, mob.z, heldItem);
                }
                return;
            }
        }
        if (mob.type == MobType.ALLAY) {
            rt.mobSystem().interactAllay(player, hand, mob);
            return;
        }
        if (heldItem == PlayerInventory.LEAD) {
            com.gameexpert.engine.mob.MobRuntime.LeashPlan plan =
                    rt.mobSystem().planAttachLeash(mob, player.nickname());
            if (plan == null) return;
            PlayerInventory.StackSnapshot before = player.inventory().stack(hand);
            if (!player.inventory().consumeOne(hand, PlayerInventory.LEAD)) return;
            if (!rt.mobSystem().confirmAttachLeash(plan)) {
                player.inventory().setStack(player.inventory().capture(hand.hand()), before);
                return;
            }
            sendTo(player, inventoryMessage(player));
            return;
        }
        if (heldItem == AIR
                && rt.mobSystem().detachLeash(mob.id, player.nickname()) != null) {
            if (player.inventory().addItem(PlayerInventory.LEAD, 1, 0) != 1) {
                rt.itemSystem().spawnDeathDrop(
                        PlayerInventory.LEAD, 1, 0, mob.x, mob.y + 0.5, mob.z);
            }
            sendTo(player, inventoryMessage(player));
            return;
        }
        if (heldItem == PlayerInventory.FLINT_AND_STEEL && mob instanceof Creeper creeper) {
            // Creeper#mobInteract: 실제 엔티티를 먼저 점화하고 라이터 내구도를 1 소모한다.
            // ignite()는 진행 중 fuse를 되감지 않으므로 반복 우클릭도 바닐라와 같다.
            creeper.ignite();
            wearFlintAndSteelAndRecord(player, hand);
            sendTo(player, inventoryMessage(player));
            broadcastWorldSoundExact("ignite", mob.x, mob.y, mob.z, (short) AIR);
            return;
        }
        // [MACE-B1] Creeper#mobInteract 는 #creeper_igniters(부싯돌과 부시 · 화염구)를 받는다. 화염구는
        // 내구가 없어(isDamageableItem 거짓) 하나 줄고, 소리는 FIRECHARGE_USE 다.
        if (heldItem == PlayerInventory.FIRE_CHARGE && mob instanceof Creeper creeper) {
            if (!player.inventory().consumeOne(hand, PlayerInventory.FIRE_CHARGE)) return;
            creeper.ignite();
            sendTo(player, inventoryMessage(player));
            broadcastWorldSoundExact("firecharge_use", mob.x, mob.y, mob.z, (short) AIR);
            return;
        }
        // [MACE-B1] SulfurCube#mobInteract: 폭발 아키타입이면 부싯돌과 부시(내구 1) 또는 화염구(1개 소모)로
        // primeTime 을 부른다. 라이터 소리는 없고 primeTime 의 TNT_PRIMED 가 난다.
        if ((heldItem == PlayerInventory.FLINT_AND_STEEL || heldItem == PlayerInventory.FIRE_CHARGE)
                && mob.type == MobType.SULFUR_CUBE) {
            if (rt.mobSystem().igniteSulfurCube(mob)) {
                if (heldItem == PlayerInventory.FLINT_AND_STEEL) {
                    wearFlintAndSteelAndRecord(player, hand);
                } else {
                    player.inventory().consumeOne(hand, PlayerInventory.FIRE_CHARGE);
                }
                sendTo(player, inventoryMessage(player));
                broadcastWorldSoundExact("tnt_prime", mob.x, mob.y, mob.z, (short) AIR);
            }
            return;
        }
        if (heldItem == AIR && mob.type == MobType.WOLF && mob.ownerNickname() != null) {
            rt.mobSystem().toggleWolfSitting(player, mob);
            return;
        }
        // [VILLAGER-TRADE] 주민 우클릭은 거래 화면을 연다. 직업은 POI 점유 원장이 정본이라
        // 취업하지 않은 주민(미취업·NITWIT)만 VILLAGER_NOT_TRADING 을 받는다.
        if (mob.type == MobType.VILLAGER
                && com.gameexpert.engine.mob.villager.VillagerFoodInventory.index(heldItem) >= 0) {
            rt.mobSystem().feedAnimal(player, hand, mob);
            return;
        }
        if (mob.type == MobType.VILLAGER || mob.type == MobType.WANDERING_TRADER) {
            openVillagerTrade(player, mob.id);
            return;
        }
        if (mob.type == MobType.WOLF) {
            int itemId = Short.toUnsignedInt(heldItem);
            if (itemId >= Short.toUnsignedInt(PlayerInventory.WHITE_DYE)
                    && itemId <= Short.toUnsignedInt(PlayerInventory.BLACK_DYE)) {
                rt.mobSystem().dyeWolfCollar(player, hand, mob);
                return;
            }
            if (heldItem == PlayerInventory.WOLF_ARMOR) {
                rt.mobSystem().equipWolfBodyArmor(player, hand, mob);
                return;
            }
            if (heldItem == PlayerInventory.ARMADILLO_SCUTE) {
                rt.mobSystem().repairWolfBodyArmor(player, hand, mob);
                return;
            }
            if (heldItem == PlayerInventory.SHEARS) {
                rt.mobSystem().removeWolfBodyArmor(player, hand, mob);
                return;
            }
        }
        // [FARM-ANIMAL] 양 전단·염색(MOB.md §4). 가위는 늑대 갑옷 해제와 종으로 갈린다.
        if (mob.type == MobType.SHEEP) {
            if (heldItem == PlayerInventory.SHEARS) {
                if (rt.mobSystem().shearSheep(player, hand, mob)) {
                    broadcastWorldSoundExact("shears", mob.x, mob.y, mob.z, (short) AIR);
                }
                return;
            }
            int itemId = Short.toUnsignedInt(heldItem);
            if (itemId >= Short.toUnsignedInt(PlayerInventory.WHITE_DYE)
                    && itemId <= Short.toUnsignedInt(PlayerInventory.BLACK_DYE)) {
                rt.mobSystem().dyeSheep(player, hand, mob);
                return;
            }
        }
        // [CONTAINER-MENUS] SnowGolem#mobInteract / Bogged#mobInteract with shears.
        if ((mob.type == MobType.SNOW_GOLEM || mob.type == MobType.BOGGED)
                && heldItem == PlayerInventory.SHEARS) {
            if (rt.mobSystem().shearSnowGolemOrBogged(player, hand, mob)) {
                broadcastWorldSoundExact(MobSystem.shearSoundKind(mob), mob.x, mob.y, mob.z, (short) AIR);
            }
            return;
        }
        if (mob.type == MobType.SULFUR_CUBE && heldItem == PlayerInventory.SHEARS) {
            if (rt.mobSystem().shearSulfurCube(player, hand, mob)) {
                broadcastWorldSoundExact("shears", mob.x, mob.y, mob.z, (short) AIR);
            }
            return;
        }
        if (mob.type == MobType.MOOSHROOM && heldItem == PlayerInventory.SHEARS) {
            com.gameexpert.engine.mob.MobRuntime.MooshroomShearPlan plan =
                    rt.mobSystem().planShearMooshroom(mob);
            PlayerInventory.StackSnapshot before = player.inventory().stack(hand);
            if (plan != null && rt.mobSystem().confirmShearMooshroom(plan) != null) {
                player.inventory().degrade(hand);
                recordBreakIfGone(player, player.inventory().capture(hand.hand()), before);
                sendTo(player, inventoryMessage(player));
                broadcastWorldSoundExact("shears", mob.x, mob.y, mob.z, (short) AIR);
            }
            return;
        }
        // [FARM-ANIMAL] 돼지 안장·탑승·부스트(MOB.md §4).
        if (mob.type == MobType.PIG) {
            if (heldItem == PlayerInventory.SADDLE) {
                rt.mobSystem().saddlePig(player, hand, mob);
                return;
            }
            if (heldItem == PlayerInventory.CARROT_ON_A_STICK) {
                rt.mobSystem().boostPig(player, hand, mob);
                return;
            }
            if (mob.pigSaddled() && !isBreedingFood(heldItem)) {
                rt.mobSystem().mountPig(player, mob);
                return;
            }
        }
        // [HARNESS] 해피 가스트: 하네스 장착 → 탑승. 안장 분기와 같은 자리·같은 순서이며
        // 갈리는 지점은 둘이다.
        //   ① 안장 자리에 있는 것이 **하네스 16색**이라 손에 든 아이템 하나가 아니라 색 구간을
        //      본다(`PlayerInventory.isHarness`).
        //   ② 하네스가 없으면 **태우지도 않는다** — 말·낙타는 안장 없이도 `doPlayerRide` 로
        //      떨어지지만 하네스 없는 해피 가스트에는 앉을 자리 자체가 없다([B] «Happy Ghast»).
        //      그 게이트는 `HappyGhast.mount` 안에 있고 여기서는 분기만 연다.
        // 새끼(가스틀링)는 하네스를 받지 못하므로 ②가 성체 게이트까지 함께 지킨다.
        if (mob.type == MobType.HAPPY_GHAST) {
            if (com.gameexpert.engine.inventory.PlayerInventory.isHarness(heldItem)) {
                rt.mobSystem().harnessHappyGhast(player, hand, mob);
                return;
            }
            rt.mobSystem().mountHappyGhast(player, mob);
            return;
        }
        // Every Java AbstractHorse inventory opens from crouching use, even without cargo.
        if (mob.horseEquipment() != null && mob.horseTamed() && player.crouching()) {
            openMobCargo(player, mob);
            return;
        }
        // [MOUNT] 낙타: 먹이(선인장) → 안장 → 탑승. 바닐라 `Camel` 은 `AbstractHorse` 를 상속하지만
        // 길들이기가 없어 안장만이 조종 조건이고, 좌석이 둘이라 앞자리부터 채운다. 먹이는 위쪽
        // 먹이(선인장)는 아래 공통 번식 분기로 흘려보내고 여기서는 안장·탑승만 처리한다.
        if (mob.type == MobType.CAMEL
                && !com.gameexpert.engine.mob.CamelRules.isBreedingFood(heldItem)) {
            if (heldItem == PlayerInventory.SADDLE) {
                rt.mobSystem().saddleCamel(player, hand, mob);
                return;
            }
            // 탑승은 안장을 묻지 않는다 — 바닐라 `AbstractHorse#mobInteract` 는 안장이 없으면
            // 그대로 `doPlayerRide` 로 떨어지고, `isSaddled()` 는 `getControllingPassenger()`
            // 에서만 본다(`Camel extends AbstractHorse`). 조종·대시 게이트는 `MobMountRules
            // .steerable` 이 따로 지키므로 무안장 낙타는 태우되 조종되지 않는다(말과 동형).
            rt.mobSystem().mountCamel(player, mob);
            return;
        }
        // [MOUNT] 말 계열(말·당나귀·노새·라마): 먹이(temper) → 카펫 → 상자 → 안장 → 탑승.
        // 바닐라 AbstractHorse/AbstractChestedHorse/Llama 의 mobInteract 분기 순서 그대로다.
        if (com.gameexpert.engine.mob.HorseRules.isHorseFamily(mob.type)) {
            // 화물 패널은 손에 든 아이템보다 앞선 분기다(바닐라 AbstractChestedHorse#mobInteract
            // 첫머리: `if (this.isVehicle() || bl) { openCustomInventoryScreen(...); return; }`).
            if (com.gameexpert.engine.mob.HorseRules.isFood(heldItem)) {
                rt.mobSystem().feedHorse(player, hand, mob);
                return;
            }
            if (com.gameexpert.engine.mob.HorseRules.isArmorItem(heldItem)) {
                MobSystem.HorseArmorEquipPlan plan = MobSystem.planEquipHorseArmor(mob, heldItem);
                if (plan != null) {
                    PlayerInventory.StackSnapshot before = player.inventory().stack(hand);
                    if (!player.inventory().consumeOne(hand, plan.armorItem())) return;
                    if (!rt.mobSystem().confirmEquipHorseArmor(plan)) {
                        player.inventory().setStack(player.inventory().capture(hand.hand()), before);
                        return;
                    }
                    sendTo(player, inventoryMessage(player));
                    sendTo(player, new SoundEvent("armor_equip", plan.armorItem()));
                    return;
                }
            }
            if (heldItem == PlayerInventory.SHEARS) {
                MobSystem.HorseArmorRemovalPlan plan = MobSystem.planRemoveHorseArmor(mob);
                if (plan != null) {
                    if (!rt.mobSystem().confirmRemoveHorseArmor(plan)) return;
                    player.inventory().degrade(hand);
                    if (player.inventory().addItem(plan.armorItem(), 1, 0) != 1) {
                        rt.itemSystem().spawnDeathDrop(
                                plan.armorItem(), 1, 0, mob.x, mob.y + 0.5, mob.z);
                    }
                    sendTo(player, inventoryMessage(player));
                    broadcastWorldSoundExact("shears", mob.x, mob.y, mob.z, (short) AIR);
                    return;
                }
            }
            // 카펫은 라마 전용 body-armor 이고 길들이기를 묻지 않는다.
            if (com.gameexpert.engine.mob.LlamaRules.isCarpet(heldItem)) {
                rt.mobSystem().decorateLlama(player, hand, mob);
                return;
            }
            if (com.gameexpert.engine.mob.ChestedHorseRules.isChestItem(heldItem)) {
                rt.mobSystem().chestHorse(player, hand, mob);
                return;
            }
            if (heldItem == PlayerInventory.SADDLE) {
                rt.mobSystem().saddleHorse(player, hand, mob);
                return;
            }
            rt.mobSystem().mountMob(player, mob,
                    com.gameexpert.engine.mob.MobMountRules.CONTROLLING_SEAT_INDEX);
            return;
        }
        if (mob.type == MobType.TADPOLE && heldItem == PlayerInventory.SLIME_BALL) {
            rt.mobSystem().feedTadpole(player, hand, mob);
            return;
        }
        if (mob.type == MobType.ZOMBIE_VILLAGER && heldItem == PlayerInventory.GOLDEN_APPLE) {
            rt.mobSystem().cureZombieVillager(player, hand, mob);
            return;
        }
        // [NAUTILUS-BEHAVIOR] 노틸러스 계열 두 종은 손에 든 생선 하나로 길들이기·회복·번식이
        // 갈린다. 아래 아이템 목록은 [B] 의 "any fish or any bucket of fish" 이고,
        // 갈림 자체는 MobSystem#interactNautilusFamily 가 소유한다. 이 분기가 일반 번식
        // lane(isBreedingFood) 보다 앞에 서야 노틸러스의 만피 관문이 우회되지 않는다.
        // [NAUTILUS-TEMPT] 아이템 집합은 유혹 계약과 **같은 문장**이라 열거를 복제하지 않고
        // TemptationRules.isFishOrFishBucket 하나만 지난다 — 두 곳이 각자 열거하면 한쪽만
        // 갱신됐을 때 "먹이는 되는데 유혹은 안 되는" 갈림이 조용히 생긴다.
        if ((mob.type == MobType.ZOMBIE_NAUTILUS || mob.type == MobType.NAUTILUS)
                && com.gameexpert.engine.mob.TemptationRules.isFishOrFishBucket(
                        Short.toUnsignedInt(heldItem))) {
            rt.mobSystem().interactNautilusFamily(player, hand, mob);
            return;
        }
        // [NAUTILUS-MOUNT] 노틸러스: 갑옷 장착 → 안장 장착 → 탑승. 해피 가스트 분기와 같은
        // 자리·같은 순서이고 갈리는 지점은 둘이다.
        //   ① 장비 슬롯이 **둘**이라(안장·갑옷) 손에 든 아이템으로 갈래가 나뉜다([B] §8-2).
        //   ② 길들임이 **탑승 게이트**다 — [B] 가 "tamed adult" 라고 못 박아, 미길들임·새끼
        //      개체에는 앉을 자리 자체가 없다(하네스 없는 해피 가스트와 같은 자리).
        //      안장은 탑승이 아니라 **조종** 게이트다(MobMountRules.steerable — 말·낙타와 동형).
        // 먹이(생선) 갈래는 위 분기가 이미 가로채므로 여기서는 다시 보지 않는다.
        if (mob.type == MobType.NAUTILUS || mob.type == MobType.ZOMBIE_NAUTILUS) {
            // QA 관측: 탑승 요청이 권위에 닿는지(실측: 길들이고 안장까지 얹은 자연 노틸러스가
            // 탑승 응답 없이 조용했다). 판정은 바꾸지 않는다.
            log.info("nautilus interact: player={} mob={} held={} owner={} rider={}",
                    player.nickname(), mob.id, heldItem, mob.ownerNickname(), mob.seatRider(0));
            if (heldItem == PlayerInventory.SHEARS) {
                if (rt.mobSystem().removeNautilusArmor(player, hand, mob)) {
                    broadcastWorldSoundExact("shears", mob.x, mob.y, mob.z, (short) AIR);
                }
                return;
            }
            if (com.gameexpert.engine.mob.NautilusMountRules.isArmorItem(
                    Short.toUnsignedInt(heldItem))) {
                rt.mobSystem().equipNautilusArmor(player, hand, mob);
                return;
            }
            if (heldItem == PlayerInventory.SADDLE) {
                rt.mobSystem().saddleNautilus(player, hand, mob);
                return;
            }
            rt.mobSystem().mountNautilus(player, mob);
            return;
        }
        if (heldItem == PlayerInventory.GOLD_INGOT && mob.type == MobType.PIGLIN) {
            rt.mobSystem().barterPiglin(player, hand, mob);
            return;
        }
        if (heldItem == PlayerInventory.BRUSH && mob.type == MobType.ARMADILLO) {
            rt.mobSystem().brushArmadillo(player, hand, mob);
            return;
        }
        if (mob.type == MobType.DOLPHIN && isDolphinFood(heldItem)) {
            rt.mobSystem().feedDolphin(player, hand, mob);
            return;
        }
        if (heldItem == PlayerInventory.BONE && mob.type == MobType.WOLF) {
            rt.mobSystem().tameWolf(player, hand, mob);
            return;
        }
        if ((heldItem == PlayerInventory.COD_RAW || heldItem == PlayerInventory.SALMON_RAW)
                && mob.type == MobType.OCELOT && !mob.ocelotTrusting()) {
            rt.mobSystem().trustOcelot(player, hand, mob);
            return;
        }
        if (heldItem == PlayerInventory.WATER_BUCKET && isBucketableAquatic(mob.type)) {
            if (rt.mobSystem().captureFish(player, hand, mob)) {
                broadcastWorldSoundExact("bucket_fill_water", mob.x, mob.y, mob.z, (short) AIR);
            }
            return;
        }
        if (heldItem == PlayerInventory.BUCKET && mob.type == MobType.SULFUR_CUBE) {
            if (rt.mobSystem().captureSulfurCube(player, hand, mob)) {
                broadcastWorldSoundExact("bucket_fill_water", mob.x, mob.y, mob.z, (short) AIR);
            }
            return;
        }
        if (mob.type == MobType.MOOSHROOM && SuspiciousStewRules.isStewFlower(
                Short.toUnsignedInt(heldItem))) {
            rt.mobSystem().feedMooshroomFlower(player, hand, mob);
            return;
        }
        if (heldItem == PlayerInventory.BOWL && mob.type == MobType.MOOSHROOM
                && !mob.isBaby()) {
            MobSystem.MooshroomBowlPlan plan = MobSystem.planFillBowlFromMooshroom(mob);
            if (plan != null && takeMooshroomStew(
                    player.inventory(), hand, plan.resultItem(),
                    SuspiciousStewRules.flowerComponents(com.gameexpert.engine.mob.MooshroomRules.storedFlower(mob)))
                    && rt.mobSystem().confirmFillBowlFromMooshroom(plan)) {
                sendTo(player, inventoryMessage(player));
                broadcastWorldSoundExact("cow_milk", mob.x, mob.y, mob.z, (short) AIR);
            }
            return;
        }
        if (isBreedingFood(heldItem)) {
            rt.mobSystem().feedAnimal(player, hand, mob);
            return;
        }
        // 먹이가 아닌 상호작용 중 빈 양동이만 기존 젖짜기 규칙으로 넘긴다.
        if (heldItem != PlayerInventory.BUCKET) return;

        MilkResult result = milkCow(player.inventory(), hand, mob);
        if (result == MilkResult.SUCCESS) {
            sendTo(player, inventoryMessage(player));
            broadcastWorldSoundExact("cow_milk", mob.x, mob.y, mob.z, (short) AIR);
        } else if (result == MilkResult.BABY) {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error(
                    "MILK_BABY_COW"));
        } else if (result == MilkResult.MALE) {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error(
                    "MILK_MALE_COW"));
        } else if (result == MilkResult.NO_EMPTY_BUCKET) {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error(
                    "MILK_NEEDS_BUCKET"));
        } else {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error(
                    "MILK_NOT_COW"));
        }
    }

    private void sendCompanionInteractionResult(PlayerTickState player,
            PlayerAction.MobInteract interact, String result) {
        if (interact.interactionId() == null) return;
        sendTo(player, new CompanionInteractionResult(interact.interactionId(),
                interact.mobId(), interact.hand() == PlayerAction.Hand.MAIN ? "main" : "offhand", result));
    }

    /** Only an actual species PASS permits the next hand or ordinary item use. */
    private String applyCompanionHand(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        short item = player.inventory().stack(hand).itemType();
        var held = player.inventory().stack(hand);
        if (item == PlayerInventory.NAME_TAG
                && com.gameexpert.engine.inventory.NameTagRules.plan(held, held.itemComponents()) != null) {
            int count = player.inventory().stack(hand).count();
            applyNameTag(player, hand, mob);
            return player.inventory().stack(player.inventory().capture(hand.hand())).count() < count
                    ? "consumed" : "rejected";
        }
        if (item == PlayerInventory.LEAD) {
            var plan = rt.mobSystem().planAttachLeash(mob, player.nickname());
            if (plan == null) return "rejected";
            var before = player.inventory().stack(hand);
            if (!player.inventory().consumeOne(hand, item)) return "rejected";
            if (!rt.mobSystem().confirmAttachLeash(plan)) {
                player.inventory().setStack(player.inventory().capture(hand.hand()), before);
                return "rejected";
            }
            sendTo(player, inventoryMessage(player));
            return "consumed";
        }
        if (item == AIR && rt.mobSystem().detachLeash(mob.id, player.nickname()) != null) {
            if (player.inventory().addItem(PlayerInventory.LEAD, 1, 0) != 1) {
                rt.itemSystem().spawnDeathDrop(PlayerInventory.LEAD, 1, 0, mob.x, mob.y + 0.5, mob.z);
            }
            sendTo(player, inventoryMessage(player));
            return "consumed";
        }
        return rt.mobSystem().interactCompanion(player, hand, mob);
    }

    private void applyNameTag(PlayerTickState player, PlayerInventory.HandRef hand, Mob mob) {
        PlayerInventory.StackSnapshot tag = player.inventory().stack(hand);
        var plan = com.gameexpert.engine.inventory.NameTagRules.plan(tag, tag.itemComponents());
        if (plan == null || rt.mobPersistence() == null) return;
        PlayerInventory.CompletePersistenceSnapshot source =
                player.inventory().acquireSettlementLease();
        if (source == null) return;
        PlayerInventory planned = source.detachedInventory();
        if (!planned.consumeOne(planned.capture(hand.hand()), PlayerInventory.NAME_TAG)) {
            player.inventory().releaseSettlementLease(source);
            return;
        }
        var committed = planned.completePersistenceSnapshot();
        var mobSnapshot = rt.mobSystem().persistenceSnapshot(mob);
        try {
            var outcome = rt.mobPersistence().settleNameTag(source.revision(),
                    rt.playerInventoryMutationSnapshot(player, committed), mobSnapshot, plan.name());
            if (outcome != com.gameexpert.mob.service.MobPersistenceService.NameTagOutcome.COMMITTED
                    || !player.inventory().installCommittedSettlement(source, committed)) {
                player.inventory().releaseSettlementLease(source);
                return;
            }
            mob.setCustomName(plan.name());
            mob.setPersistenceRequired(true);
            // 이름은 mobUpdate 에 실리지 않으므로 스냅샷을 다시 보내야 클라이언트가 이름표를 그린다.
            rt.mobSystem().broadcastMobRefresh(mob);
            sendTo(player, inventoryMessage(player));
            broadcastWorldSoundExact("block_place", mob.x, mob.y, mob.z, PlayerInventory.NAME_TAG);
        } catch (RuntimeException failure) {
            player.inventory().releaseSettlementLease(source);
            log.warn("월드 {} 이름표 정산 실패 mob={}: {}", rt.worldId(), mob.id,
                    failure.toString());
        }
    }

    private static boolean isBreedingFood(short itemType) {
        for (MobType type : MobType.values()) {
            if (type.isBreedingFood(itemType)) return true;
        }
        return false;
    }

    static boolean isBucketableAquatic(MobType type) {
        return type == MobType.COD || type == MobType.SALMON || type == MobType.TROPICAL_FISH
                || type == MobType.PUFFERFISH || type == MobType.TADPOLE
                || type == MobType.AXOLOTL;
    }

    private static boolean isDolphinFood(short itemType) {
        return itemType == PlayerInventory.COD_RAW || itemType == PlayerInventory.COD_COOKED
                || itemType == PlayerInventory.SALMON_RAW
                || itemType == PlayerInventory.SALMON_COOKED
                || itemType == PlayerInventory.TROPICAL_FISH
                || itemType == PlayerInventory.PUFFERFISH;
    }

    enum MilkResult { SUCCESS, NOT_COW, BABY, MALE, NO_EMPTY_BUCKET }

    static boolean takeMooshroomStew(PlayerInventory inventory, PlayerInventory.HandRef hand) {
        return takeMooshroomStew(inventory, hand, PlayerInventory.MUSHROOM_STEW);
    }

    static boolean takeMooshroomStew(
            PlayerInventory inventory, PlayerInventory.HandRef hand, short resultItem) {
        return takeMooshroomStew(inventory, hand, resultItem, ItemComponentData.EMPTY);
    }

    static boolean takeMooshroomStew(PlayerInventory inventory, PlayerInventory.HandRef hand,
            short resultItem, ItemComponentData components) {
        PlayerInventory.StackSnapshot stack = inventory.stack(hand);
        if (stack.itemType() != PlayerInventory.BOWL || stack.count() <= 0) return false;
        return inventory.transformOne(hand, stack, new PlayerInventory.StackSnapshot(
                resultItem, 1, 0, EnchantmentRules.EMPTY_ENCHANTMENTS, 0, 0, null,
                ItemComponentCodec.encode(resultItem, components)));
    }

    /** 테스트 가능한 순수 젖짜기 규칙. WebCraft의 영속 성별 사양은 암소만 허용한다. */
    static MilkResult milkCow(PlayerInventory inventory, Mob mob) {
        if (mob.type != MobType.COW) return MilkResult.NOT_COW;
        if (mob.isBaby()) return MilkResult.BABY;
        // [FARM-VARIANT] 기후 접미사가 붙은 소도 성별로만 판정한다(`female_warm` 도 암소다).
        if (!CowSex.isFemale(mob.variant())) return MilkResult.MALE;
        if (inventory.itemType(inventory.selectedSlot()) != PlayerInventory.BUCKET) {
            return MilkResult.NO_EMPTY_BUCKET;
        }
        return InventoryRules.replaceSelectedBucket(
                inventory, PlayerInventory.BUCKET, PlayerInventory.MILK_BUCKET)
                ? MilkResult.SUCCESS : MilkResult.NO_EMPTY_BUCKET;
    }

    static MilkResult milkCow(
            PlayerInventory inventory, PlayerInventory.HandRef hand, Mob mob) {
        if (mob.type != MobType.COW && mob.type != MobType.GOAT
                && mob.type != MobType.MOOSHROOM) return MilkResult.NOT_COW;
        if (mob.isBaby()) return MilkResult.BABY;
        if (mob.type == MobType.COW && !CowSex.isFemale(mob.variant())) return MilkResult.MALE;
        if (inventory.stack(hand).itemType() != PlayerInventory.BUCKET) {
            return MilkResult.NO_EMPTY_BUCKET;
        }
        return InventoryRules.replaceBucket(
                inventory, hand, PlayerInventory.BUCKET, PlayerInventory.MILK_BUCKET)
                ? MilkResult.SUCCESS : MilkResult.NO_EMPTY_BUCKET;
    }

    private void applyMoveChestItem(PlayerAction.MoveChestItem move) {
        PlayerTickState player = rt.players().get(move.nickname());
        if (player == null || player.openChest() == null) return;
        BlockPos pos = player.openChest();
        if (!samePosition(pos, move.x(), move.y(), move.z())) return;
        if (!InteractRules.isContainer(residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z()))
                || !InteractRules.withinContainerReach(
                        player.x(), player.y(), player.z(), player.crouching(),
                        pos.x(), pos.y(), pos.z())) {
            closeChestSession(player, true);
            return;
        }
        ChestAccess chest = openChestAccess(player, pos.x(), pos.y(), pos.z());
        if (!chest.matchesSession(pos, player.openChestPartner())) {
            closeChestSession(player, true);
            return;
        }
        if (!move.fromChest()
                && residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z()) == Blocks.CRAFTER) {
            // [CONTAINER-MENUS] The legacy whole-stack move cannot see CrafterSlot.mayPlace;
            // crafter placement goes through containerClick/containerDrag only.
            sendTo(player, chestUpdateMessage(pos.x(), pos.y(), pos.z(), Blocks.CRAFTER,
                    chestSlots(chest)));
            return;
        }
        if (chest.owner() == null) {
            settleChestMutation(player, chest, (inventory, plannedChest) ->
                    moveLegacyChestStack(inventory, plannedChest, move.fromChest(), move.slot()) > 0);
            return;
        }
        if (!applyOwnedChestMove(player, chest, move.fromChest(), move.slot())) {
            sendTo(player, chestUpdateMessage(
                    pos.x(), pos.y(), pos.z(),
                    residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z()), chestSlots(chest)));
            return;
        }
        chest.markDirty(rt);
        sendTo(player, inventoryMessage(player));
        broadcastChestUpdate(chest);
    }

    private boolean applyOwnedChestMove(PlayerTickState player, ChestAccess live,
            boolean fromChest, int slot) {
        PlayerInventory.CompletePersistenceSnapshot source =
                player.inventory().completePersistenceSnapshot();
        ChestInventory.PersistenceSnapshot firstSource = live.first().persistenceSnapshot();
        ChestInventory.PersistenceSnapshot secondSource = live.second() == null ? null
                : live.second().persistenceSnapshot();
        if (!canAdvancePersistenceRevision(source.revision())
                || !canAdvancePersistenceRevision(firstSource.revision())
                || secondSource != null && !canAdvancePersistenceRevision(secondSource.revision())) {
            return false;
        }
        PlayerInventory plannedInventory = source.detachedInventory();
        ChestInventory plannedFirst = firstSource.detachedInventory();
        ChestInventory plannedSecond = secondSource == null ? null : secondSource.detachedInventory();
        ChestAccess planned = new ChestAccess(
                live.firstPosition(), plannedFirst, live.secondPosition(), plannedSecond);
        if (moveLegacyChestStack(plannedInventory, planned, fromChest, slot) <= 0) return false;
        if (player.inventory().revision() != source.revision()
                || !live.first().persistenceSnapshot().equals(firstSource)
                || secondSource != null && !live.second().persistenceSnapshot().equals(secondSource)) {
            return false;
        }
        return moveLegacyChestStack(player.inventory(), live, fromChest, slot) > 0;
    }

    private static int moveLegacyChestStack(PlayerInventory inventory, ChestAccess chest,
            boolean fromChest, int slot) {
        if (inventory == null || chest == null
                || inventory.settlementLeased()
                || !canAdvancePersistenceRevision(inventory.revision())) return 0;
        if (fromChest) {
            if (slot < 0 || slot >= chest.slotCount()) return 0;
            ChestSlotRef ref = chest.slot(slot);
            if (ref.inventory().itemType(ref.slot()) == PlayerInventory.EMPTY
                    || ref.inventory().count(ref.slot()) <= 0) return 0;
            PlayerInventory.StackSnapshot source = new PlayerInventory.StackSnapshot(
                    ref.inventory().itemType(ref.slot()), ref.inventory().count(ref.slot()),
                    ref.inventory().durability(ref.slot()), ref.inventory().enchantments(ref.slot()),
                    ref.inventory().mapId(ref.slot()), ref.inventory().shulkerId(ref.slot()),
                    ref.inventory().bucketMobData(ref.slot()), ref.inventory().itemComponentData(ref.slot()));
            int added = inventory.addItem(source.itemType(), source.count(), source.durability(),
                    source.enchantments(), source.mapId(), source.shulkerId(),
                    source.bucketMobData(), source.itemComponentData());
            if (added <= 0 || added > source.count()) return 0;
            int removed = ref.inventory().take(ref.slot(), added);
            return removed == added ? added : 0;
        }
        if (slot < 0 || slot >= PlayerInventory.SLOTS) return 0;
        PlayerInventory.DroppedStack source = inventory.stackAt(slot);
        if (source == null) return 0;
        int leftover = chest.add(source);
        if (leftover < 0 || leftover > source.count()) return 0;
        int added = source.count() - leftover;
        if (added <= 0) return 0;
        PlayerInventory.DroppedStack removed = inventory.dropFromSlot(slot, added);
        return sameStackIdentity(removed, source, added) ? added : 0;
    }

    private static boolean sameStackIdentity(PlayerInventory.DroppedStack actual,
            PlayerInventory.DroppedStack expected, int count) {
        return actual != null && expected != null && actual.count() == count
                && actual.itemType() == expected.itemType()
                && actual.durability() == expected.durability()
                && actual.enchantments() == expected.enchantments()
                && actual.mapId() == expected.mapId()
                && actual.shulkerId() == expected.shulkerId()
                && java.util.Objects.equals(actual.bucketMobData(), expected.bucketMobData())
                && java.util.Objects.equals(actual.itemComponentData(), expected.itemComponentData());
    }

    private static boolean canAdvancePersistenceRevision(long revision) {
        return revision >= 0L && revision < Long.MAX_VALUE - 1L;
    }

    private void applyCloseChest(PlayerAction.CloseChest close) {
        PlayerTickState player = rt.players().get(close.nickname());
        if (player == null) return;
        rt.cancelCanonicalChestOpen(player, close.x(), close.y(), close.z());
        if (samePosition(player.openChest(), close.x(), close.y(), close.z())) {
            closeChestSession(player, true);
        } else {
            // Explicit closes acknowledge their requested target even after a rejected open.
            // The client can drain that request before sending another open for the same cell.
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.ChestClosed(close.x(), close.y(), close.z()));
        }
    }

    // ── [CONTAINER-CURSOR] 보관 컨테이너의 바닐라 커서 규약 ────────────────────
    //   상자(좌표)와 몹 화물(mobId)은 키만 다르고 클릭 규칙이 완전히 같다. 규칙 자체는
    //   PlayerInventory.clickContainer/dragContainer 한 곳에 있고, 여기서는 "열려 있는가·
    //   닿는가"를 확인해 컨테이너 시야를 만들고 결과를 방송하는 일만 한다.

    /** 열린 컨테이너 한 개. chest·mob 중 정확히 하나만 채워진다. */
    private record OpenContainer(
            com.gameexpert.engine.inventory.ContainerAccess access,
            ChestAccess chest,
            Mob mob,
            PlacedEntitySystem.Placed placed) {
        OpenContainer(com.gameexpert.engine.inventory.ContainerAccess access, ChestAccess chest,
                Mob mob) {
            this(access, chest, mob, null);
        }
    }

    /**
     * 커서 조작 대상이 지금도 열려 있고 닿는지 확인한다. 아니면 세션을 닫고 null 을 돌려준다
     * (기존 moveChestItem/moveMobCargoItem 과 완전히 같은 신뢰 경계다).
     */
    private OpenContainer resolveOpenContainer(
            PlayerTickState player, PlayerAction.ContainerRef ref) {
        if (ref == null) return null;
        if (ref.placedEntityId() > 0L) {
            // [CONTAINER-MENUS] A placed chest or hopper minecart's cargo (ChestMenu 9x3 /
            // HopperMenu 5) with the chest's click rules.
            PlacedEntitySystem.Placed cart = rt.placedEntities().openCargoOf(player, ref.placedEntityId());
            if (cart == null) return null;
            return new OpenContainer(
                    com.gameexpert.engine.inventory.ContainerAccess.of(cart.cargo), null, null, cart);
        }
        if (ref.mob()) {
            if (player.openMobCargo() != ref.mobId()) return null;
            Mob mob = reachableCargoMob(player, ref.mobId());
            if (mob == null) {
                closeMobCargoSession(player);
                return null;
            }
            return new OpenContainer(
                    new com.gameexpert.engine.inventory.HorseMenuContainerAccess(mob),
                    null, mob);
        }
        BlockPos pos = player.openChest();
        if (pos == null || !samePosition(pos, ref.x(), ref.y(), ref.z())) return null;
        if (!InteractRules.isContainer(residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z()))
                || !InteractRules.withinContainerReach(
                        player.x(), player.y(), player.z(), player.crouching(),
                        pos.x(), pos.y(), pos.z())) {
            closeChestSession(player, true);
            return null;
        }
        ChestAccess chest = openChestAccess(player, pos.x(), pos.y(), pos.z());
        if (!chest.matchesSession(pos, player.openChestPartner())) {
            closeChestSession(player, true);
            return null;
        }
        // [SHULKER-CONTENTS] 놓인 셜커의 27칸은 상자와 저장소·클릭 규칙이 완전히 같고
        // **셜커 상자 아이템만** 받지 않는다([A] 바닐라 셜커-인-셜커 금지). 짝은 이루지
        // 않으므로(isChestShaped 밖) 언제나 단일 27칸이다.
        if (Blocks.isShulkerBox(residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z()))) {
            return new OpenContainer(
                    com.gameexpert.engine.inventory.ContainerAccess.ofShulker(chest.first()),
                    chest, null);
        }
        if (residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z()) == Blocks.CRAFTER) {
            return new OpenContainer(
                    com.gameexpert.engine.inventory.ContainerAccess.ofCrafter(chest.first(),
                            rt.crafterDisabledSlots(pos.x(), pos.y(), pos.z())),
                    chest, null);
        }
        return new OpenContainer(
                com.gameexpert.engine.inventory.ContainerAccess.ofDouble(
                        chest.first(), chest.second()),
                chest, null);
    }

    /** 커서는 방송하지 않는다 — 같은 상자를 보는 다른 플레이어의 커서와 섞이면 안 된다. */
    private void sendContainerCursor(PlayerTickState player) {
        PlayerInventory inv = player.inventory();
        sendTo(player, new com.gameexpert.ws.dto.WsMessages.ContainerCursor(
                craftingStack(inv.cursorType(), inv.cursorCount(), inv.cursorDurability(),
                        inv.cursorEnchantments(), inv.cursorMapId(), inv.cursorShulkerId(),
                        inv.cursorBucketMobData(), inv.cursorItemComponentData())));
    }

    /** 거부된 클릭: 상태를 바꾸지 않았으므로 그 플레이어의 화면만 권위 값으로 되돌린다. */
    private void resendContainer(PlayerTickState player, OpenContainer open) {
        if (open.placed() != null) {
            rt.placedEntities().resendCargo(player, open.placed());
        } else if (open.chest() != null) {
            BlockPos pos = player.openChest();
            if (pos != null) {
                sendTo(player, chestUpdateMessage(
                        pos.x(), pos.y(), pos.z(),
                        residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z()),
                        chestSlots(open.chest())));
            }
        } else {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.MobCargoUpdate(
                    open.mob().id,
                    open.mob().type.name().toLowerCase(java.util.Locale.ROOT),
                    open.mob().variant(), open.mob().isBaby(), open.mob().customName(),
                    mobCargoColumns(open.mob()), mobCargoSlots(open.mob())));
        }
        sendContainerCursor(player);
    }

    /** 확정된 변경: 영속 표시 → 본인 인벤/커서 → 같은 컨테이너를 보는 모두에게 재고 방송. */
    private void commitContainer(PlayerTickState player, OpenContainer open) {
        if (open.placed() != null) {
            sendTo(player, inventoryMessage(player));
            sendContainerCursor(player);
            rt.placedEntities().cargoChanged(open.placed());
        } else if (open.chest() != null) {
            open.chest().markDirty(rt);
            sendTo(player, inventoryMessage(player));
            sendContainerCursor(player);
            broadcastChestUpdate(open.chest());
        } else {
            rt.mobSystem().refreshCargoPersistence(open.mob());
            sendTo(player, inventoryMessage(player));
            sendContainerCursor(player);
            broadcastMobCargoUpdate(open.mob());
        }
    }

    private void applyContainerClick(PlayerAction.ContainerClick click) {
        PlayerTickState player = rt.players().get(click.nickname());
        if (player == null) return;
        OpenContainer open = resolveOpenContainer(player, click.target());
        if (open == null) return;
        if (open.chest() != null && open.chest().owner() == null) {
            settleChestMutation(player, open.chest(), (inventory, plannedChest) ->
                    inventory.clickContainer(
                            containerAccess(plannedChest, click.target()), click.area(),
                            click.slot(), click.button(), click.shift()));
            return;
        }
        if (open.mob() != null) {
            settleMobMenuMutation(player, open.mob(), (inventory, menu) ->
                    inventory.clickContainer(menu, click.area(), click.slot(), click.button(),
                            click.shift()));
            return;
        }
        if (!player.inventory().clickContainer(
                open.access(), click.area(), click.slot(), click.button(), click.shift())) {
            resendContainer(player, open);
            return;
        }
        commitContainer(player, open);
    }

    private void applyContainerDrag(PlayerAction.ContainerDrag drag) {
        PlayerTickState player = rt.players().get(drag.nickname());
        if (player == null) return;
        OpenContainer open = resolveOpenContainer(player, drag.target());
        if (open == null) return;
        if (open.chest() != null && open.chest().owner() == null) {
            settleChestMutation(player, open.chest(), (inventory, plannedChest) ->
                    inventory.dragContainer(containerAccess(plannedChest, drag.target()),
                            drag.areas(), drag.slots(), drag.button()));
            return;
        }
        if (open.mob() != null) {
            settleMobMenuMutation(player, open.mob(), (inventory, menu) ->
                    inventory.dragContainer(menu, drag.areas(), drag.slots(), drag.button()));
            return;
        }
        if (!player.inventory().dragContainer(
                open.access(), drag.areas(), drag.slots(), drag.button())) {
            resendContainer(player, open);
            return;
        }
        commitContainer(player, open);
    }

    private void applyCollectContainer(PlayerAction.CollectContainer collect) {
        PlayerTickState player = rt.players().get(collect.nickname());
        if (player == null) return;
        OpenContainer open = resolveOpenContainer(player, collect.target());
        if (open == null) return;
        if (collect.slot() < 0 || collect.slot() >= (collect.area()
                == PlayerInventory.ContainerArea.INVENTORY
                        ? PlayerInventory.SLOTS : open.access().slotCount())) return;
        if (open.chest() != null && open.chest().owner() == null) {
            settleChestMutation(player, open.chest(), (inventory, plannedChest) ->
                    inventory.collectContainer(containerAccess(plannedChest, collect.target())));
            return;
        }
        if (open.mob() != null) {
            settleMobMenuMutation(player, open.mob(),
                    (inventory, menu) -> inventory.collectContainer(menu));
            return;
        }
        if (!player.inventory().collectContainer(open.access())) {
            resendContainer(player, open);
            return;
        }
        commitContainer(player, open);
    }

    private com.gameexpert.engine.inventory.ContainerAccess containerAccess(
            ChestAccess chest, PlayerAction.ContainerRef ref) {
        int block = residentBlockType(rt.accessor(), ref.x(), ref.y(), ref.z());
        if (block == Blocks.CRAFTER) {
            return com.gameexpert.engine.inventory.ContainerAccess.ofCrafter(chest.first(),
                    rt.crafterDisabledSlots(ref.x(), ref.y(), ref.z()));
        }
        return Blocks.isShulkerBox(block)
                ? com.gameexpert.engine.inventory.ContainerAccess.ofShulker(chest.first())
                : com.gameexpert.engine.inventory.ContainerAccess.ofDouble(
                        chest.first(), chest.second());
    }

    @FunctionalInterface
    private interface ChestMutation {
        boolean apply(PlayerInventory inventory, ChestAccess chest);
    }

    @FunctionalInterface
    private interface MobMenuMutation {
        boolean apply(PlayerInventory inventory,
                com.gameexpert.engine.inventory.HorseMenuContainerAccess menu);
    }

    private void settleMobMenuMutation(
            PlayerTickState player, Mob live, MobMenuMutation mutation) {
        PlayerInventory.CompletePersistenceSnapshot source =
                player.inventory().acquireSettlementLease();
        if (source == null) return;
        if (!canAdvancePersistenceRevision(source.revision())
                || live == null || live.horseEquipment() == null) {
            player.inventory().releaseSettlementLease(source);
            return;
        }
        com.gameexpert.mob.dto.MobPersistenceSnapshot liveSnapshot =
                rt.mobSystem().detachedPersistenceSnapshot(live);
        long expected = live.horseMenuPersistenceRevision();
        if (liveSnapshot == null || !canAdvancePersistenceRevision(expected)
                || liveSnapshot.getHorseMenuPersistenceRevision() != expected) {
            player.inventory().releaseSettlementLease(source);
            return;
        }
        long next = Math.incrementExact(expected);
        if (!canAdvancePersistenceRevision(next)) {
            player.inventory().releaseSettlementLease(source);
            return;
        }
        ChestInventory.PersistenceSnapshot equipmentSource =
                live.horseEquipment().persistenceSnapshot();
        ChestInventory.PersistenceSnapshot cargoSource = live.horseCargo() == null ? null
                : live.horseCargo().persistenceSnapshot();
        if (!canAdvancePersistenceRevision(equipmentSource.revision())
                || cargoSource != null && !canAdvancePersistenceRevision(cargoSource.revision())) {
            player.inventory().releaseSettlementLease(source);
            return;
        }
        PlayerInventory plannedInventory = source.detachedInventory();
        ChestInventory plannedEquipment = equipmentSource.detachedInventory();
        ChestInventory plannedCargo = cargoSource == null ? null : cargoSource.detachedInventory();
        var plannedMenu = new com.gameexpert.engine.inventory.HorseMenuContainerAccess(
                live.type, plannedEquipment, plannedCargo);
        if (!mutation.apply(plannedInventory, plannedMenu)) {
            player.inventory().releaseSettlementLease(source);
            resendContainer(player, new OpenContainer(
                    new com.gameexpert.engine.inventory.HorseMenuContainerAccess(live), null, live));
            return;
        }
        PlayerInventory.CompletePersistenceSnapshot committedInventory =
                plannedInventory.completePersistenceSnapshot();
        if (committedInventory.revision() != source.revision() + 1) {
            player.inventory().releaseSettlementLease(source);
            return;
        }
        String plannedData = com.gameexpert.engine.mob.HorseMenuCodec.encode(plannedMenu);
        String liveData = liveSnapshot.getHorseInventoryData();
        if (plannedData.equals(liveData)) {
            if (player.inventory().installCommittedSettlement(source, committedInventory)) {
                rt.queuePlayerInventoryBaseline(player);
                sendTo(player, inventoryMessage(player));
                sendContainerCursor(player);
            } else player.inventory().releaseSettlementLease(source);
            return;
        }
        var plannedSnapshot = liveSnapshot.withHorseInventoryDataAndRevisions(
                plannedData, next, plannedEquipment.persistenceRevision(),
                plannedCargo == null ? 0L : plannedCargo.persistenceRevision());
        var target = new InventoryMutationTarget.MobCargo(plannedSnapshot, next);
        var command = new PlayerContainerSettlementCommand(
                WorldRuntime.stablePlayerContainerSettlementId(
                        player.playerId(), source.revision()),
                source.revision(), rt.playerInventoryMutationSnapshot(player, committedInventory),
                target, List.of(expected));
        Runnable rejected = () -> {
            player.inventory().releaseSettlementLease(source);
            if (rt.players().get(player.nickname()) == player
                    && player.openMobCargo() == live.id
                    && rt.mobSystem().combat().findAlive(live.id) == live) {
                resendContainer(player, new OpenContainer(
                        new com.gameexpert.engine.inventory.HorseMenuContainerAccess(live),
                        null, live));
            }
        };
        rt.submitPlayerContainerSettlement(command, () -> {
            PlayerTickState currentPlayer = rt.players().get(player.nickname());
            Mob currentMob = rt.mobSystem().combat().findAlive(live.id);
            if (currentPlayer != player || player.openMobCargo() != live.id
                    || currentMob != live
                    || currentMob.horseMenuPersistenceRevision() != expected) {
                player.inventory().releaseSettlementLease(source);
                return;
            }
            com.gameexpert.engine.mob.HorseMenuCodec.EncodedMenu currentMenu =
                    com.gameexpert.engine.mob.HorseMenuCodec.capture(currentMob);
            if (currentMenu.revision() != expected
                    || !java.util.Objects.equals(currentMenu.payload(), liveData)) {
                player.inventory().releaseSettlementLease(source);
                return;
            }
            if (!replaceHorseMenuExact(currentMob, equipmentSource, cargoSource,
                    plannedEquipment, plannedCargo, expected, next, plannedData)) {
                player.inventory().releaseSettlementLease(source);
                return;
            }
            if (!player.inventory().installCommittedSettlement(
                    source, committedInventory)) {
                player.inventory().releaseSettlementLease(source);
                return;
            }
            sendTo(player, inventoryMessage(player));
            sendContainerCursor(player);
            rt.mobSystem().refreshCargoPersistence(currentMob);
            broadcastMobCargoUpdate(currentMob);
        }, rejected);
    }

    /**
     * Installs the committed HMI1 payload without crossing the persistence-load boundary of a
     * live menu. The live inventories may already be baseline-bound, so restore/clear would be a
     * lifecycle violation; exact slot swaps preserve that lifecycle and each local generation.
     */
    private static boolean replaceHorseMenuExact(Mob mob,
            ChestInventory.PersistenceSnapshot expectedEquipment,
            ChestInventory.PersistenceSnapshot expectedCargo,
            ChestInventory plannedEquipment, ChestInventory plannedCargo,
            long expectedSharedRevision, long committedRevision, String committedPayload) {
        if (mob == null || expectedEquipment == null || plannedEquipment == null
                || mob.horseMenuPersistenceRevision() != expectedSharedRevision
                || committedRevision != expectedSharedRevision + 1
                || mob.horseEquipment() == null
                || (mob.horseCargo() == null) != (expectedCargo == null)
                || (mob.horseCargo() == null) != (plannedCargo == null)) return false;
        ChestInventory equipment = mob.horseEquipment();
        ChestInventory cargo = mob.horseCargo();
        synchronized (equipment) {
            if (cargo == null) {
                return replaceHorseMenuExactLocked(mob, equipment, null,
                        expectedEquipment, expectedCargo, plannedEquipment, plannedCargo,
                        committedRevision, committedPayload);
            }
            synchronized (cargo) {
                return replaceHorseMenuExactLocked(mob, equipment, cargo,
                        expectedEquipment, expectedCargo, plannedEquipment, plannedCargo,
                        committedRevision, committedPayload);
            }
        }
    }

    private static boolean replaceHorseMenuExactLocked(Mob mob,
            ChestInventory equipment, ChestInventory cargo,
            ChestInventory.PersistenceSnapshot expectedEquipment,
            ChestInventory.PersistenceSnapshot expectedCargo,
            ChestInventory plannedEquipment, ChestInventory plannedCargo,
            long committedRevision, String committedPayload) {
        if (equipment.slots() != plannedEquipment.slots()
                || !equipment.persistenceSnapshot().equals(expectedEquipment)
                || plannedCargo != null && (cargo == null
                        || cargo.slots() != plannedCargo.slots()
                        || !cargo.persistenceSnapshot().equals(expectedCargo))) {
            return false;
        }
        if (cargo == null && expectedCargo != null) return false;
        int equipmentChanges = changedChestSlots(equipment, plannedEquipment);
        int cargoChanges = cargo == null ? 0 : changedChestSlots(cargo, plannedCargo);
        long equipmentAfter = expectedEquipment.revision() + equipmentChanges;
        long cargoAfter = expectedCargo == null ? 0L : expectedCargo.revision() + cargoChanges;
        if (!canAdvancePersistenceRevisionBy(expectedEquipment.revision(), equipmentChanges)
                || expectedCargo != null
                        && !canAdvancePersistenceRevisionBy(expectedCargo.revision(), cargoChanges)
                || equipmentAfter > committedRevision || cargoAfter > committedRevision
                || plannedEquipment.persistenceRevision() != equipmentAfter
                || plannedCargo != null && plannedCargo.persistenceRevision() != cargoAfter) {
            return false;
        }

        for (int slot = 0; slot < equipment.slots(); slot++) {
            PlayerInventory.StackSnapshot replacement = chestStack(plannedEquipment, slot);
            PlayerInventory.StackSnapshot previous = chestStack(equipment, slot);
            if (previous.equals(replacement)) continue;
            if (!equipment.swapExact(slot, replacement).equals(previous)) return false;
        }
        if (cargo != null) {
            for (int slot = 0; slot < cargo.slots(); slot++) {
                PlayerInventory.StackSnapshot replacement = chestStack(plannedCargo, slot);
                PlayerInventory.StackSnapshot previous = chestStack(cargo, slot);
                if (previous.equals(replacement)) continue;
                if (!cargo.swapExact(slot, replacement).equals(previous)) return false;
            }
        }
        mob.advanceHorseMenuPersistenceRevision();
        if (mob.horseMenuPersistenceRevision() != committedRevision) return false;
        try {
            com.gameexpert.engine.mob.HorseMenuCodec.EncodedMenu committed =
                    com.gameexpert.engine.mob.HorseMenuCodec.capture(mob);
            return committed.sharedRevision() == committedRevision
                    && committed.revision() == committedRevision
                    && java.util.Objects.equals(committed.payload(), committedPayload);
        } catch (RuntimeException invalidated) {
            return false;
        }
    }

    private static int changedChestSlots(ChestInventory actual, ChestInventory planned) {
        int changes = 0;
        for (int slot = 0; slot < actual.slots(); slot++) {
            if (!chestStack(actual, slot).equals(chestStack(planned, slot))) changes++;
        }
        return changes;
    }

    private static PlayerInventory.StackSnapshot chestStack(ChestInventory inventory, int slot) {
        if (inventory.itemType(slot) == PlayerInventory.EMPTY || inventory.count(slot) <= 0) {
            return PlayerInventory.StackSnapshot.EMPTY;
        }
        return new PlayerInventory.StackSnapshot(
                inventory.itemType(slot), inventory.count(slot), inventory.durability(slot),
                inventory.enchantments(slot), inventory.mapId(slot), inventory.shulkerId(slot),
                inventory.bucketMobData(slot), inventory.itemComponentData(slot));
    }

    private static boolean canAdvancePersistenceRevisionBy(long revision, int changes) {
        return revision >= 0L && changes >= 0
                && revision <= Long.MAX_VALUE - 2L - changes;
    }

    private void settleChestMutation(
            PlayerTickState player, ChestAccess live, ChestMutation mutation) {
        PlayerInventory.CompletePersistenceSnapshot source =
                player.inventory().acquireSettlementLease();
        if (source == null) return;
        ChestInventory.PersistenceSnapshot firstSource = live.first().persistenceSnapshot();
        ChestInventory.PersistenceSnapshot secondSource = live.second() == null ? null
                : live.second().persistenceSnapshot();
        if (!canAdvancePersistenceRevision(source.revision())
                || !canAdvancePersistenceRevision(firstSource.revision())
                || secondSource != null && !canAdvancePersistenceRevision(secondSource.revision())) {
            player.inventory().releaseSettlementLease(source);
            return;
        }
        PlayerInventory plannedInventory = source.detachedInventory();
        ChestInventory plannedFirst = firstSource.detachedInventory();
        ChestInventory plannedSecond = secondSource == null ? null : secondSource.detachedInventory();
        ChestAccess planned = new ChestAccess(
                live.firstPosition(), plannedFirst, live.secondPosition(), plannedSecond);
        if (!mutation.apply(plannedInventory, planned)) {
            player.inventory().releaseSettlementLease(source);
            resendContainer(player, new OpenContainer(
                    com.gameexpert.engine.inventory.ContainerAccess.ofDouble(
                            live.first(), live.second()), live, null));
            return;
        }
        PlayerInventory.CompletePersistenceSnapshot committedInventory =
                plannedInventory.completePersistenceSnapshot();
        List<ChestSettlementHalf> changed = new ArrayList<>(2);
        addChangedChestHalf(changed, live.firstPosition(), live.first(), firstSource, plannedFirst);
        if (live.second() != null) {
            addChangedChestHalf(changed, live.secondPosition(), live.second(), secondSource, plannedSecond);
        }
        if (committedInventory.revision() != source.revision() + 1) {
            player.inventory().releaseSettlementLease(source);
            return;
        }
        // Picking up/placing only the player-owned cursor leaves every chest half unchanged.
        // It still must install the detached player plan; otherwise the authoritative click is
        // silently discarded after acquiring the settlement lease.
        if (changed.isEmpty()) {
            if (player.inventory().installCommittedSettlement(
                    source, committedInventory)) {
                rt.queuePlayerInventoryBaseline(player);
                sendTo(player, inventoryMessage(player));
                sendContainerCursor(player);
                broadcastChestUpdate(live);
            } else {
                player.inventory().releaseSettlementLease(source);
            }
            return;
        }
        changed.sort((left, right) -> {
            BlockPos a = left.position();
            BlockPos b = right.position();
            int order = Integer.compare(a.x(), b.x());
            if (order == 0) order = Integer.compare(a.y(), b.y());
            return order == 0 ? Integer.compare(a.z(), b.z()) : order;
        });
        List<InventoryMutationTarget.ChestHalf> halves = new ArrayList<>(changed.size());
        for (ChestSettlementHalf half : changed) {
            ChestInventory.PersistenceSnapshot plannedSnapshot =
                    half.planned().persistenceSnapshot();
            halves.add(new InventoryMutationTarget.ChestHalf(
                    new InventoryMutationTarget.Position(
                            half.position().x(), half.position().y(), half.position().z()),
                    plannedSnapshot.snapshot(), plannedSnapshot.revision()));
        }
        List<Long> expected = changed.stream()
                .map(ChestSettlementHalf::expectedRevision).toList();
        var target = new InventoryMutationTarget.Chests(halves);
        var command = new PlayerContainerSettlementCommand(
                WorldRuntime.stablePlayerContainerSettlementId(
                        player.playerId(), source.revision()),
                source.revision(), rt.playerInventoryMutationSnapshot(player, committedInventory),
                target, expected);
        Runnable rejected = () -> {
            player.inventory().releaseSettlementLease(source);
            if (rt.players().get(player.nickname()) == player) {
                resendContainer(player, new OpenContainer(
                        com.gameexpert.engine.inventory.ContainerAccess.ofDouble(
                                live.first(), live.second()), live, null));
            }
        };
        rt.submitPlayerContainerSettlement(command, () -> {
            if (!chestSettlementStillCurrent(changed)) {
                player.inventory().releaseSettlementLease(source);
                return;
            }
            if (rt.players().get(player.nickname()) == player) {
                if (!player.inventory().installCommittedSettlement(
                        source, committedInventory)) {
                    player.inventory().releaseSettlementLease(source);
                    return;
                }
                sendTo(player, inventoryMessage(player));
                sendContainerCursor(player);
            } else {
                player.inventory().releaseSettlementLease(source);
            }
            for (ChestSettlementHalf half : changed) {
                BlockPos position = half.position();
                rt.chestStorage().load(
                        position.x(), position.y(), position.z(), half.planned());
            }
            broadcastChestUpdate(planned);
        }, rejected);
    }

    private static void addChangedChestHalf(List<ChestSettlementHalf> changed,
            BlockPos position, ChestInventory live,
            ChestInventory.PersistenceSnapshot source, ChestInventory planned) {
        if (planned.persistenceRevision() != source.revision()) {
            changed.add(new ChestSettlementHalf(position, live, source.revision(), planned));
        }
    }

    private boolean chestSettlementStillCurrent(List<ChestSettlementHalf> changed) {
        for (ChestSettlementHalf half : changed) {
            BlockPos position = half.position();
            if (rt.chestStorage().peekAt(position.x(), position.y(), position.z()) != half.live()
                    || half.live().persistenceRevision() != half.expectedRevision()) {
                return false;
            }
        }
        return true;
    }

    private static final class ChestSettlementHalf {
        private final BlockPos position;
        private final ChestInventory live;
        private final long expectedRevision;
        private final ChestInventory planned;

        private ChestSettlementHalf(
                BlockPos position, ChestInventory live, long expectedRevision,
                ChestInventory planned) {
            this.position = position;
            this.live = live;
            this.expectedRevision = expectedRevision;
            this.planned = planned;
        }

        private BlockPos position() { return position; }
        private ChestInventory live() { return live; }
        private long expectedRevision() { return expectedRevision; }
        private ChestInventory planned() { return planned; }
    }

    private void applyDropContainerCursor(PlayerAction.DropContainerCursor drop) {
        PlayerTickState player = rt.players().get(drop.nickname());
        if (player == null) return;
        OpenContainer open = resolveOpenContainer(player, drop.target());
        if (open == null) return;
        if (!settleCursorGroundDrop(player, false, drop.one(), () -> {
            sendTo(player, inventoryMessage(player));
            sendContainerCursor(player);
        })) {
            resendContainer(player, open);
        }
    }

    private static List<InventorySlot> chestSlots(ChestAccess chest) {
        List<InventorySlot> slots = new ArrayList<>(chest.slotCount());
        for (int i = 0; i < chest.slotCount(); i++) {
            ChestSlotRef ref = chest.slot(i);
            short type = ref.inventory().itemType(ref.slot());
            Integer durability = PlayerInventory.isDurable(type)
                    ? ref.inventory().durability(ref.slot()) : null;
            slots.add(inventorySlot(i, type, ref.inventory().count(ref.slot()), durability,
                    ref.inventory().enchantments(ref.slot()),
                    mapIdOrNull(ref.inventory().mapId(ref.slot())),
                    shulkerIdOrNull(ref.inventory().shulkerId(ref.slot())),
                    ref.inventory().bucketMobData(ref.slot()),
                    ref.inventory().itemComponentData(ref.slot())));
        }
        return slots;
    }

    private void closeChestSession(PlayerTickState player, boolean sound) {
        BlockPos pos = player.openChest();
        if (pos == null) return;
        // [CONTAINER-CURSOR] 화면이 닫히면 들고 있던 스택은 인벤토리로 돌아가고, 자리가 없는
        // 나머지만 바닐라처럼 발밑에 떨어진다(화로·인챈트 닫기와 같은 규칙).
        dropCraftingOverflow(player, player.inventory().closeContainerCursor());
        unregisterChestSubscriber(player);
        player.closeChest();
        sendTo(player, inventoryMessage(player));
        sendContainerCursor(player);
        sendTo(player, new com.gameexpert.ws.dto.WsMessages.ChestClosed(pos.x(), pos.y(), pos.z()));
        int soundBlock = residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z());
        // [BARREL-SOUND] 통의 닫는 소리는 위 unregisterChestSubscriber 의 syncBarrelOpenState 가
        // 마지막 열람자가 떠날 때만 barrel_close 로 낸다. 상자 소리를 겹쳐 내지 않는다.
        // Hoppers and dispensers close silently (no opener counter, javap 26.3-snapshot-7).
        if (sound && soundBlock != Blocks.BARREL && !InteractRules.isSilentContainerMenu(soundBlock)) {
            // [CHEST-FAMILY] 음향 힌트는 닫는 순간 그 자리에 실제로 있는 상자 ID 다. 이미
            // 부서졌으면(=상자가 아님) 종전대로 일반 상자 ID 를 보낸다.
            // [CONTAINER-MENUS] ShulkerBoxBlockEntity#stopOpen: block.shulker_box.close.
            broadcastWorldSound(Blocks.isShulkerBox(soundBlock) ? "shulker_box_close" : "chest_close",
                    pos.x(), pos.y(), pos.z(),
                    (short) (Blocks.isChestShaped(soundBlock) || Blocks.isShulkerBox(soundBlock)
                            ? soundBlock : Blocks.CHEST));
        }
    }

    // ── [CONTAINER-MENUS] crafter menu ──────────────────────────────────

    private com.gameexpert.ws.dto.WsMessages.ChestOpen chestOpenMessage(int x, int y, int z,
            int hostBlockId, List<InventorySlot> slots) {
        return new com.gameexpert.ws.dto.WsMessages.ChestOpen(x, y, z, hostBlockId, slots,
                crafterMenuState(x, y, z, hostBlockId));
    }

    private com.gameexpert.ws.dto.WsMessages.ChestUpdate chestUpdateMessage(int x, int y, int z,
            int hostBlockId, List<InventorySlot> slots) {
        return new com.gameexpert.ws.dto.WsMessages.ChestUpdate(x, y, z, hostBlockId, slots,
                crafterMenuState(x, y, z, hostBlockId));
    }

    /**
     * {@code CrafterMenu}'s synced facts for a crafter host: {@code ContainerData} slots 0..8
     * (disabled), slot 9 ({@code triggered}) and the result slot {@code refreshRecipeResult}
     * fills from {@code CrafterBlock.getPotentialResults}. {@code null} for any other host.
     */
    private com.gameexpert.ws.dto.WsMessages.CrafterMenuState crafterMenuState(
            int x, int y, int z, int hostBlockId) {
        if (hostBlockId != Blocks.CRAFTER) return null;
        int mask = rt.crafterDisabledSlots(x, y, z);
        List<Integer> disabled = new ArrayList<>();
        for (int slot = 0; slot < com.gameexpert.engine.dispenser.CrafterRules.SLOTS; slot++) {
            if (com.gameexpert.engine.dispenser.CrafterRules.slotDisabled(mask, slot)) {
                disabled.add(slot);
            }
        }
        boolean powered = com.gameexpert.engine.dispenser.CrafterRules.triggered(
                rt.blockStates().get(x, y, z, Blocks.CRAFTER));
        ChestInventory inventory = rt.chestStorage().peekAt(x, y, z);
        CraftingStack result = null;
        if (inventory != null
                && inventory.slots() == com.gameexpert.engine.dispenser.CrafterRules.SLOTS) {
            PlayerInventory.StackSnapshot[] grid =
                    new PlayerInventory.StackSnapshot[com.gameexpert.engine.dispenser.CrafterRules.SLOTS];
            for (int slot = 0; slot < grid.length; slot++) {
                short type = inventory.itemType(slot);
                int count = inventory.count(slot);
                grid[slot] = type == PlayerInventory.EMPTY || count <= 0
                        ? PlayerInventory.StackSnapshot.EMPTY
                        : new PlayerInventory.StackSnapshot(type, count,
                                inventory.durability(slot), inventory.enchantments(slot),
                                inventory.mapId(slot), inventory.shulkerId(slot),
                                inventory.bucketMobData(slot), inventory.itemComponentData(slot));
            }
            PlayerInventory.CrafterCraft craft = PlayerInventory.crafterCraft(grid);
            if (craft != null) {
                PlayerInventory.StackSnapshot out = craft.result();
                result = craftingStack(out.itemType(), out.count(), out.durability(),
                        out.enchantments(), out.mapId(), out.shulkerId(), out.bucketMobData(),
                        out.itemComponentData());
            }
        }
        return new com.gameexpert.ws.dto.WsMessages.CrafterMenuState(
                List.copyOf(disabled), powered, result);
    }

    /**
     * {@code ServerGamePacketListenerImpl#handleContainerSlotStateChanged} ->
     * {@code CrafterBlockEntity#setSlotState}: only for the crafter this player has open, and
     * only while the slot is empty ({@code slotCanBeDisabled}).
     */
    private void applyCrafterSlotState(PlayerAction.CrafterSlotState action) {
        PlayerTickState player = rt.players().get(action.nickname());
        if (player == null || player.isDead()) return;
        BlockPos pos = player.openChest();
        if (pos == null || !samePosition(pos, action.x(), action.y(), action.z())) return;
        if (residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z()) != Blocks.CRAFTER) return;
        if (!InteractRules.withinContainerReach(player.x(), player.y(), player.z(),
                player.crouching(), pos.x(), pos.y(), pos.z())) {
            closeChestSession(player, true);
            return;
        }
        int slot = action.slot();
        if (slot < 0 || slot >= com.gameexpert.engine.dispenser.CrafterRules.SLOTS) return;
        ChestInventory inventory = rt.openContainer(pos.x(), pos.y(), pos.z());
        if (inventory == null
                || inventory.itemType(slot) != PlayerInventory.EMPTY && inventory.count(slot) > 0) {
            publishHopperChestChange(pos);
            return;
        }
        int mask = rt.crafterDisabledSlots(pos.x(), pos.y(), pos.z());
        int next = action.enabled() ? mask & ~(1 << slot) : mask | 1 << slot;
        rt.setCrafterDisabledSlots(pos.x(), pos.y(), pos.z(), next);
        publishHopperChestChange(pos);
    }

    private void broadcastChestUpdate(ChestAccess chest) {
        redstone.engine().containerChanged(chest.firstPosition().x(), chest.firstPosition().y(), chest.firstPosition().z());
        if (chest.secondPosition() != null) redstone.engine().containerChanged(chest.secondPosition().x(), chest.secondPosition().y(), chest.secondPosition().z());
        long key = ChestStorage.key(
                chest.firstPosition().x(), chest.firstPosition().y(), chest.firstPosition().z());
        LinkedHashSet<PlayerTickState> subscribers = chestSubscribersByPosition.get(key);
        if (subscribers == null || subscribers.isEmpty()) return;
        List<InventorySlot> slots = chestSlots(chest);
        for (PlayerTickState viewer : subscribers) {
            // [ENDER-SHULKER] 엔더 상자는 같은 좌표라도 열람자마다 내용이 다르다. 위치
            // 구독자 집합을 그대로 밀면 남의 27칸이 내 화면에 그려지므로 소유자로 좁힌다.
            if (chest.owner() != null && viewer != chest.owner()) continue;
            BlockPos open = viewer.openChest();
            if (open == null || !chest.matchesSession(open, viewer.openChestPartner())) continue;
            sendTo(viewer, chestUpdateMessage(
                    open.x(), open.y(), open.z(),
                    residentBlockType(rt.accessor(), open.x(), open.y(), open.z()), slots));
        }
    }

    // ── [HOPPER] host seams used by HopperSystem ────────────────────────

    /** Vanilla-ordered halves ({@code DoubleBlockCombiner} FIRST, SECOND) of a chest cell. */
    List<BlockPos> hopperChestHalves(int x, int y, int z, int block) {
        return chestTopology(x, y, z, block).orderedHalves();
    }

    /** Republishes a coordinate container a hopper changed to every open menu on it. */
    void publishHopperChestChange(BlockPos position) {
        redstone.engine().containerChanged(position.x(), position.y(), position.z());
        int block = residentBlockType(rt.accessor(), position.x(), position.y(), position.z());
        if (!InteractRules.isContainer(block) || block == Blocks.ENDER_CHEST) return;
        if (!chestSubscribersByPosition.containsKey(
                ChestStorage.key(position.x(), position.y(), position.z()))) return;
        ChestAccess chest = openChestAccess(null, position.x(), position.y(), position.z());
        if (chest.first() == null) return;
        broadcastChestUpdate(chest);
    }

    // ── [CONTAINER-MENUS] host seams used by DispenseBehaviours ─────────
    // Each seam is the dispenser (ownerless) half of an existing player interaction on the cell in
    // front of the dispenser: the same world writes and sounds, no reach or hand checks.

    /** {@code BoneMealItem.growCrop} on the cell: true when the target took the bone meal. */
    boolean dispenserBoneMeal(int x, int y, int z) {
        int current = residentBlockType(rt.accessor(), x, y, z);
        if (current == UNAVAILABLE_BLOCK) return false;
        int state = rt.blockStates().get(x, y, z, current);
        if (!isBoneMealTarget(current, state) && !randomTicks.isPackBoneMealTarget(x, y, z)) {
            return false;
        }
        // Bamboo at its full height (or boxed in) is not a valid bone-meal target.
        if (current == Blocks.BAMBOO && !randomTicks.canApplyBoneMeal(x, y, z)) return false;
        randomTicks.applyBoneMeal(x, y, z);
        broadcastWorldSound("bonemeal", x, y, z, (short) current);
        return true;
    }

    /**
     * {@code BucketItem.emptyContents(null, level, pos, null)} and, for a mob bucket,
     * {@code MobBucketItem.checkExtraContent}: true when the contents were placed.
     */
    boolean dispenserEmptyBucket(int x, int y, int z, short bucket, String bucketMobData) {
        int current = residentBlockType(rt.accessor(), x, y, z);
        if (current == UNAVAILABLE_BLOCK) return false;
        int state = rt.blockStates().get(x, y, z, current);
        boolean waterloggable = BuildingBlockRules.canAcceptWater(current, state);
        if (bucket == PlayerInventory.WATER_BUCKET && waterloggable
                && (state & COPPER_WATERLOGGED) == 0) {
            rt.setBlockState(x, y, z, current, state | COPPER_WATERLOGGED);
            rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) current);
            broadcastWorldSound("bucket_empty_water", x, y, z, (short) current);
            return true;
        }
        if (bucket == PlayerInventory.COD_BUCKET || bucket == PlayerInventory.SALMON_BUCKET
                || bucket == PlayerInventory.TROPICAL_FISH_BUCKET
                || bucket == PlayerInventory.AXOLOTL_BUCKET
                || bucket == PlayerInventory.TADPOLE_BUCKET
                || bucket == PlayerInventory.PUFFERFISH_BUCKET
                || bucket == PlayerInventory.SULFUR_CUBE_BUCKET) {
            boolean sulfurCube = bucket == PlayerInventory.SULFUR_CUBE_BUCKET;
            if (current != AIR && !(Fluids.isWater(current) && !sulfurCube)) return false;
            if (current == AIR && !sulfurCube) rt.fluidSim().applyChange(x, y, z, WATER_SOURCE);
            rt.mobSystem().releaseBucketFish(bucket, bucketMobData, x + 0.5, y, z + 0.5);
            broadcastWorldSound(sulfurCube ? "bucket_fill_water" : "bucket_empty_water",
                    x, y, z, (short) current);
            return true;
        }
        if (!BlockEditRules.bucketTargetAccepted(bucket, current)) return false;
        if (bucket == PlayerInventory.WATER_BUCKET || bucket == PlayerInventory.LAVA_BUCKET) {
            rt.fluidSim().applyBucketChange(x, y, z,
                    bucket == PlayerInventory.WATER_BUCKET ? WATER_SOURCE : LAVA_SOURCE);
        } else {
            rt.fluidSim().applyChange(x, y, z, Blocks.POWDER_SNOW);
        }
        broadcastWorldSound(bucket == PlayerInventory.LAVA_BUCKET
                ? "bucket_empty_lava" : "bucket_empty_water", x, y, z, (short) current);
        return true;
    }

    /** {@code BucketPickup.pickupBlock}: the filled bucket, or {@code EMPTY} when nothing moved. */
    short dispenserFillBucket(int x, int y, int z) {
        int current = residentBlockType(rt.accessor(), x, y, z);
        if (current == UNAVAILABLE_BLOCK) return PlayerInventory.EMPTY;
        int state = rt.blockStates().get(x, y, z, current);
        boolean waterloggable = BuildingBlockRules.canAcceptWater(current, state);
        if (waterloggable && (state & COPPER_WATERLOGGED) != 0) {
            rt.setBlockState(x, y, z, current, state & ~COPPER_WATERLOGGED);
            rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) current);
            broadcastWorldSound("bucket_fill_water", x, y, z, (short) current);
            return PlayerInventory.WATER_BUCKET;
        }
        if (!BlockEditRules.bucketTargetAccepted(PlayerInventory.BUCKET, current)) {
            return PlayerInventory.EMPTY;
        }
        short filled = current == WATER_SOURCE ? PlayerInventory.WATER_BUCKET
                : current == LAVA_SOURCE ? PlayerInventory.LAVA_BUCKET
                : PlayerInventory.POWDER_SNOW_BUCKET;
        rt.fluidSim().applyChange(x, y, z, AIR);
        broadcastWorldSound(current == LAVA_SOURCE ? "bucket_fill_lava" : "bucket_fill_water",
                x, y, z, (short) current);
        return filled;
    }

    /**
     * {@code DispenseItemBehavior$9} (glass bottle): a full beehive/bee nest gives a honey bottle
     * ({@code releaseBeesAndResetHoneyLevel}), a water fluid cell a water bottle; otherwise EMPTY.
     */
    short dispenserFillBottle(int x, int y, int z) {
        int current = residentBlockType(rt.accessor(), x, y, z);
        if (current == UNAVAILABLE_BLOCK) return PlayerInventory.EMPTY;
        int state = rt.blockStates().get(x, y, z, current);
        if (current == Blocks.BEE_NEST || current == Blocks.BEEHIVE) {
            int honeyLevel = (state & BEE_HONEY_LEVEL_MASK) >>> BEE_HONEY_LEVEL_SHIFT;
            if (honeyLevel < BEE_MAX_HONEY_LEVEL) return PlayerInventory.EMPTY;
            resetHiveHoney(x, y, z, current, state);
            broadcastWorldSound("bottle_fill", x, y, z, (short) current);
            return PlayerInventory.HONEY_BOTTLE;
        }
        boolean waterlogged = (BuildingBlockRules.canAcceptWater(current, state))
                && (state & COPPER_WATERLOGGED) != 0;
        if (!Fluids.isWater(current) && !waterlogged) return PlayerInventory.EMPTY;
        broadcastWorldSound("bottle_fill", x, y, z, (short) current);
        return (short) Blocks.WATER_BOTTLE;
    }

    /** {@code ShearsDispenseItemBehavior.tryShearBeehive}: three honeycomb from a full hive. */
    boolean dispenserShearHive(int x, int y, int z) {
        int current = residentBlockType(rt.accessor(), x, y, z);
        if (current != Blocks.BEE_NEST && current != Blocks.BEEHIVE) return false;
        int state = rt.blockStates().get(x, y, z, current);
        if (((state & BEE_HONEY_LEVEL_MASK) >>> BEE_HONEY_LEVEL_SHIFT) < BEE_MAX_HONEY_LEVEL) {
            return false;
        }
        rt.itemSystem().spawnDrop(PlayerInventory.HONEYCOMB, 3, x + 0.5, y + 0.5, z + 0.5);
        resetHiveHoney(x, y, z, current, state);
        broadcastWorldSound("shears", x, y, z, (short) current);
        return true;
    }

    /** {@code BeehiveBlock.releaseBeesAndResetHoneyLevel(..., BEE_RELEASED)} without a player. */
    private void resetHiveHoney(int x, int y, int z, int block, int state) {
        rt.setBlockState(x, y, z, block, state & ~BEE_HONEY_LEVEL_MASK);
        rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) block);
        if (!beeHiveSmoked(x, y, z)) rt.mobSystem().releaseBeesFromHive(x, y, z, null);
    }

    /** {@code DispenseItemBehavior$6}: a primed TNT entity at the cell, fuse 80. */
    void dispenserPrimeTnt(int x, int y, int z) {
        PrimedTntEntity entity = primedTnt.primeDispensed(x, y, z, tntRandom);
        broadcastWorldSoundExact("tnt_prime", entity.x, entity.y, entity.z, (short) Blocks.TNT);
    }

    /**
     * {@code FlintAndSteelDispenseItemBehavior}: fire where {@code BaseFireBlock.canBePlacedAt},
     * else light an unlit campfire, else prime TNT; false when none applies. Candles carry no
     * lit state in this repository.
     */
    boolean dispenserIgnite(int x, int y, int z) {
        int current = residentBlockType(rt.accessor(), x, y, z);
        if (current == UNAVAILABLE_BLOCK) return false;
        if (current == AIR && randomTicks.ignite(x, y, z, rt.tickNo())) {
            broadcastWorldSound("ignite", x, y, z, (short) Blocks.FIRE);
            return true;
        }
        if (current == Blocks.CAMPFIRE) {
            int state = rt.blockStates().get(x, y, z, current);
            if ((state & BuildingBlockRules.CAMPFIRE_LIT) != 0
                    || (state & COPPER_WATERLOGGED) != 0) return false;
            rt.setBlockState(x, y, z, current, state | BuildingBlockRules.CAMPFIRE_LIT);
            rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) current);
            CampfireInventory campfire = rt.campfireStorage().peekAt(x, y, z);
            if (campfire != null) {
                rt.campfireStorage().activate(x, y, z);
                broadcastCampfireUpdate(x, y, z, campfire);
            }
            broadcastWorldSound("campfire_light", x, y, z, (short) current);
            return true;
        }
        if (current == Blocks.TNT) {
            primeTnt(x, y, z);
            return true;
        }
        return false;
    }

    /**
     * {@code ShulkerBoxDispenseBehavior}: {@code BlockItem.place} of the shulker box into the cell
     * (replaceable and not occupied by a player); the stack's 27-slot reference becomes the placed
     * block entity exactly as a player placement does.
     */
    boolean dispenserPlaceShulker(int x, int y, int z, short type, int shulkerId) {
        int current = residentBlockType(rt.accessor(), x, y, z);
        if (current == UNAVAILABLE_BLOCK || !BlockEditRules.isPlacementReplaceable(current)) {
            return false;
        }
        for (PlayerTickState player : rt.players().values()) {
            if (!player.isDead() && playerOccupiesCell(player, x, y, z)) return false;
        }
        rt.fluidSim().applyChange(x, y, z, Short.toUnsignedInt(type));
        rt.setBlockState(x, y, z, Short.toUnsignedInt(type), 0);
        rt.tickBlockChanges().put(new BlockPos(x, y, z), type);
        ChestInventory carried = shulkerId == 0 ? null : rt.shulkerStorage().take(shulkerId);
        rt.chestStorage().load(x, y, z, carried == null ? new ChestInventory() : carried);
        rt.chestStorage().markDirty(x, y, z);
        refreshConnectionsAround(x, y, z);
        refreshStairsAround(x, y, z);
        broadcastWorldSound("block_place", x, y, z, type);
        return true;
    }

    /**
     * {@code EquipmentDispenseItemBehavior.dispenseEquipment} onto a living player whose box meets
     * the cell and whose slot for the stack is empty; true when one item was equipped.
     */
    boolean dispenserEquip(int x, int y, int z, PlayerInventory.StackSnapshot one) {
        for (PlayerTickState player : rt.players().values()) {
            if (player.isDead() || !playerOccupiesCell(player, x, y, z)) continue;
            if (!player.inventory().equipFromDispenser(one)) continue;
            sendTo(player, inventoryMessage(player));
            sendTo(player, new SoundEvent("armor_equip", one.itemType()));
            return true;
        }
        return false;
    }

    /**
     * {@code ShearsDispenseItemBehavior}: a full beehive first ({@code tryShearBeehive}), then the
     * first mob in the cell that can be unleashed or sheared ({@code tryShearEntity}).
     */
    boolean dispenserShear(int x, int y, int z) {
        if (dispenserShearHive(x, y, z)) return true;
        for (Mob mob : rt.mobSystem().mobsTouchingCell(x, y, z)) {
            // A lead cut plays shears.snip; SnowGolem / Bogged#shear play their own event.
            boolean leashed = rt.mobSystem().leashed(mob);
            if (rt.mobSystem().dispenserShear(mob)) {
                broadcastWorldSoundExact(leashed ? "shears" : MobSystem.shearSoundKind(mob),
                        mob.x, mob.y, mob.z, (short) AIR);
                return true;
            }
        }
        return false;
    }

    /**
     * {@code DispenseItemBehavior$13} block half: a convertible-to-mud block in front becomes mud;
     * {@code bottle_empty} plays at the dispenser (the SPLASH particles have no lane).
     */
    boolean dispenserMakeMud(int dispenserX, int dispenserY, int dispenserZ, int x, int y, int z) {
        int current = residentBlockType(rt.accessor(), x, y, z);
        if (current == UNAVAILABLE_BLOCK) return false;
        int mud = com.gameexpert.engine.blocks.P1Rules.waterBottleResult(current);
        if (mud == current) return false;
        rt.fluidSim().applyChange(x, y, z, mud);
        broadcastWorldSound("bottle_empty", dispenserX, dispenserY, dispenserZ, (short) current);
        broadcastSplashParticles(x, y, z);
        return true;
    }

    /** {@code DispenseItemBehavior$2}: the first tamed chested horse in the cell takes the chest. */
    boolean dispenserChestHorse(int x, int y, int z) {
        for (Mob mob : rt.mobSystem().mobsTouchingCell(x, y, z)) {
            if (rt.mobSystem().dispenserChestHorse(mob)) {
                broadcastWorldSoundExact("block_place", mob.x, mob.y, mob.z, (short) Blocks.CHEST);
                return true;
            }
        }
        return false;
    }

    /** The brush dispense: the first armadillo in the cell that sheds a scute. */
    boolean dispenserBrush(int x, int y, int z) {
        for (Mob mob : rt.mobSystem().mobsTouchingCell(x, y, z)) {
            if (rt.mobSystem().dispenserBrush(mob)) return true;
        }
        return false;
    }

    /** {@code EquipmentDispenseItemBehavior} onto the first mob in the cell that accepts it. */
    boolean dispenserEquipMob(int x, int y, int z, PlayerInventory.StackSnapshot one) {
        for (Mob mob : rt.mobSystem().mobsTouchingCell(x, y, z)) {
            if (rt.mobSystem().dispenserEquip(mob, one)) {
                broadcastWorldSoundExact("block_place", mob.x, mob.y, mob.z, one.itemType());
                return true;
            }
        }
        return false;
    }

    /**
     * The honeycomb dispense ({@code HoneycombItem.getWaxed}): wax the copper block in front, as
     * the player's honeycomb use does. levelEvent 3003 (wax particles) has no lane.
     */
    boolean dispenserWax(int x, int y, int z) {
        int current = residentBlockType(rt.accessor(), x, y, z);
        if (current == UNAVAILABLE_BLOCK) return false;
        CopperAgeRules.CopperChange wax = CopperAgeRules.waxPlan(current,
                rt.blockStates().get(x, y, z, current));
        if (wax == null) return false;
        rt.fluidSim().applyChange(x, y, z, wax.blockId());
        rt.setBlockState(x, y, z, wax.blockId(), wax.state());
        rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) wax.blockId());
        broadcastWorldSound("block_place", x, y, z, (short) wax.blockId());
        return true;
    }

    /**
     * {@code DispenseItemBehavior$8} golem half: an empty cell whose blocks below form a snow,
     * iron or copper golem base ({@code CarvedPumpkinBlock#canSpawnGolem}) takes the carved
     * pumpkin as a block and the construction spawns the golem.
     */
    boolean dispenserBuildGolem(int x, int y, int z) {
        TerrainAccessor accessor = rt.accessor();
        if (residentBlockType(accessor, x, y, z) != AIR || y - 1 < Blocks.MIN_Y) return false;
        boolean snow = y - 2 >= Blocks.MIN_Y
                && residentBlockType(accessor, x, y - 1, z) == Blocks.SNOW_BLOCK
                && residentBlockType(accessor, x, y - 2, z) == Blocks.SNOW_BLOCK;
        boolean iron = y - 2 >= Blocks.MIN_Y && (ironGolemPattern(accessor, x, y, z, true)
                || ironGolemPattern(accessor, x, y, z, false));
        boolean copper = residentBlockType(accessor, x, y - 1, z) == Blocks.COPPER_BLOCK;
        if (!snow && !iron && !copper) return false;
        rt.fluidSim().applyChange(x, y, z, Blocks.CARVED_PUMPKIN);
        rt.setBlockState(x, y, z, Blocks.CARVED_PUMPKIN, 0);
        rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) Blocks.CARVED_PUMPKIN);
        broadcastWorldSound("block_place", x, y, z, (short) Blocks.CARVED_PUMPKIN);
        trySpawnConstructedGolem(x, y, z, Blocks.CARVED_PUMPKIN, accessor);
        return true;
    }

    /**
     * {@code BoatDispenseItemBehavior#execute}: the boat 0.5625 + width / 2 (1.25) out of the face
     * and {@code stepY * 1.125} up; one block higher when the cell in front is water, level with
     * it when that cell is air over water, otherwise the item is dropped instead (false).
     */
    boolean dispenserPlaceBoat(int x, int y, int z, int facing,
            com.gameexpert.engine.hopper.HopperStack stack) {
        int fx = x + com.gameexpert.engine.dispenser.DispenserRules.stepX(facing);
        int fy = y + com.gameexpert.engine.dispenser.DispenserRules.stepY(facing);
        int fz = z + com.gameexpert.engine.dispenser.DispenserRules.stepZ(facing);
        if (fy - 1 < Blocks.MIN_Y || fy > Blocks.MAX_Y) return false;
        int front = residentBlockType(rt.accessor(), fx, fy, fz);
        int below = residentBlockType(rt.accessor(), fx, fy - 1, fz);
        double yOffset;
        if (Fluids.isWaterMedium(front, rt.blockStates().get(fx, fy, fz, front))) {
            yOffset = 1.0;
        } else if (front == AIR
                && Fluids.isWaterMedium(below, rt.blockStates().get(fx, fy - 1, fz, below))) {
            yOffset = 0.0;
        } else {
            return false;
        }
        double reach = 0.5625 + 1.375 / 2.0;
        double bx = x + 0.5 + com.gameexpert.engine.dispenser.DispenserRules.stepX(facing) * reach;
        double by = y + 0.5 + com.gameexpert.engine.dispenser.DispenserRules.stepY(facing) * 1.125;
        double bz = z + 0.5 + com.gameexpert.engine.dispenser.DispenserRules.stepZ(facing) * reach;
        // Direction#toYRot in the repository's yaw convention (forward = (-sin, -cos)).
        double yaw = Math.atan2(-com.gameexpert.engine.dispenser.DispenserRules.stepX(facing),
                -com.gameexpert.engine.dispenser.DispenserRules.stepZ(facing));
        return rt.boatSystem().placeDispensed(bx, by + yOffset, bz, yaw) != 0L;
    }

    /** Test/diagnostic view of the live primed TNT rows. */
    List<com.gameexpert.tnt.dto.PrimedTntSnapshot> primedTntSnapshots() {
        return primedTnt.persistenceSnapshot();
    }

    /** Whether the player's box (0.6 wide, 1.8 tall or 1.5 crouching) meets the cell's cube. */
    private static boolean playerOccupiesCell(PlayerTickState player, int x, int y, int z) {
        double half = 0.3;
        double height = player.crouching() ? 1.5 : 1.8;
        return player.x() + half > x && player.x() - half < x + 1
                && player.y() + height > y && player.y() < y + 1
                && player.z() + half > z && player.z() - half < z + 1;
    }

    void publishHopperShelfChange(BlockPos pos) {
        redstone.engine().containerChanged(pos.x(), pos.y(), pos.z());
        int id = residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z());
        ChestInventory shelf = rt.chestStorage().peekAt(pos.x(), pos.y(), pos.z());
        if (!Blocks.isShelf(id) || shelf == null) return;
        ShelfUpdate update = shelfUpdate(pos.x(), pos.y(), pos.z(),
                rt.blockStates().get(pos.x(), pos.y(), pos.z(), id), shelf);
        for (PlayerTickState observer : rt.players().values()) sendTo(observer, update);
    }

    void publishHopperFurnaceChange(BlockPos pos, FurnaceInventory furnace) {
        broadcastFurnaceUpdate(pos.x(), pos.y(), pos.z(), furnace);
    }

    void publishHopperBrewingChange(BlockPos pos, BrewingInventory stand) {
        redstone.engine().containerChanged(pos.x(), pos.y(), pos.z());
        for (PlayerTickState viewer : rt.players().values()) {
            if (samePosition(viewer.openCraftingTable(), pos.x(), pos.y(), pos.z())
                    && rt.brewingStorage().ownsLease(pos.x(), pos.y(), pos.z(), viewer.nickname())) {
                sendTo(viewer, brewingUpdate(viewer, pos.x(), pos.y(), pos.z(), stand));
                break;
            }
        }
    }

    /** The composter lane's roll ({@code ComposterBlock.addLayer} draws the level random). */
    double hopperComposterRoll() {
        return composterRandom.nextDouble();
    }

    /**
     * {@code levelEvent(1500)} of {@code ComposterBlock$InputContainer.setChanged}. The repository
     * maps the composter fill/ready sounds onto {@code plant}/{@code bonemeal}, as the player
     * composting path does.
     */
    void publishHopperComposterFill(BlockPos pos, int nextState) {
        broadcastWorldSound(ComposterRules.isReady(nextState) ? "bonemeal" : "plant",
                pos.x(), pos.y(), pos.z(), (short) Blocks.COMPOSTER);
    }

    /** {@code Level.hasNeighborSignal} over this repository's poplar signal sources. */
    boolean hopperHasNeighborSignal(int x, int y, int z) {
        return redstone.engine().hasNeighborSignal(x, y, z);
    }

    /**
     * [JUKEBOX] {@code JukeboxBlock.isSignalSource} · {@code getSignal}: 재생 중인 주크박스는 여섯 방향
     * 모두에 15 를 낸다({@link com.gameexpert.engine.jukebox.JukeboxRules#ownSignal}).
     */
    boolean jukeboxSignalsNeighbor(int x, int y, int z) {
        if (jukeboxTicks.isEmpty()) return false;
        for (int[] d : FIRE_NEIGHBORS) {
            if (com.gameexpert.engine.jukebox.JukeboxRules.ownSignal(
                    jukeboxPlaying(x + d[0], y + d[1], z + d[2])) > 0) return true;
        }
        return false;
    }

    private void registerChestSubscriber(PlayerTickState player, ChestAccess chest) {
        long firstKey = ChestStorage.key(
                chest.firstPosition().x(), chest.firstPosition().y(), chest.firstPosition().z());
        long secondKey = chest.secondPosition() == null ? firstKey : ChestStorage.key(
                chest.secondPosition().x(), chest.secondPosition().y(), chest.secondPosition().z());
        chestSubscribersByPosition.computeIfAbsent(firstKey, ignored -> new LinkedHashSet<>())
                .add(player);
        if (secondKey != firstKey) {
            chestSubscribersByPosition.computeIfAbsent(secondKey, ignored -> new LinkedHashSet<>())
                    .add(player);
        }
        BlockPos first = chest.firstPosition();
        BlockPos barrel = chest.secondPosition() == null
                && residentBlockType(rt.accessor(), first.x(), first.y(), first.z()) == Blocks.BARREL
                ? first : null;
        chestSubscriptionByPlayer.put(player, new ChestSubscription(firstKey, secondKey, barrel));
        syncBarrelOpenState(barrel);
        redstone.engine().chestViewersChanged(chest.firstPosition().x(), chest.firstPosition().y(), chest.firstPosition().z());
        if (chest.secondPosition() != null) redstone.engine().chestViewersChanged(chest.secondPosition().x(), chest.secondPosition().y(), chest.secondPosition().z());
    }

    private void unregisterChestSubscriber(PlayerTickState player) {
        ChestSubscription subscription = chestSubscriptionByPlayer.remove(player);
        if (subscription == null) return;
        removeChestSubscriber(subscription.firstKey, player);
        if (subscription.secondKey != subscription.firstKey) {
            removeChestSubscriber(subscription.secondKey, player);
        }
        syncBarrelOpenState(subscription.barrel);
    }

    /**
     * [BARREL-STATE] 바닐라 {@code BarrelBlockEntity} 의 {@code ContainerOpenersCounter}: 첫 열람자가
     * 열면 {@code OPEN=true}, 마지막 열람자가 닫으면 {@code OPEN=false} 로
     * {@code setBlockAndUpdate} 한다. 여기서는 그 좌표의 상자 구독자 집합이 곧 열람자 수다.
     * 상태 변경은 문 여닫이와 같은 블록 변경 방송·영속 경로를 탄다. 통이 이미 부서졌거나 청크가
     * 상주하지 않으면 아무것도 쓰지 않는다.
     *
     * <p>[BARREL-SOUND] 같은 전이에서 소리도 낸다. 26.3-snapshot-7 클라이언트 jar 의
     * {@code BarrelBlockEntity$1#onOpen/onClose} 가 {@code playSound(state, BARREL_OPEN/BARREL_CLOSE)}
     * 를 부르고, {@code BarrelBlockEntity#playSound} 는 블록 중심에서 facing 단위 벡터의 절반만큼
     * 나간 점(=정면 가운데)에서 {@code SoundSource.BLOCKS}, volume 0.5, pitch
     * {@code random.nextFloat() * 0.1 + 0.9} 로 낸다(javap). volume ≤ 1 이라 반경은 기본 16이고
     * pitch 난수는 클라이언트 프로필이 소유한다. 부서진 통은 위에서 이미 걸러지므로 바닐라
     * {@code stopOpen} 의 {@code !this.remove} 가드처럼 닫는 소리가 나지 않는다.
     */
    private void syncBarrelOpenState(BlockPos barrel) {
        if (barrel == null) return;
        if (residentBlockType(rt.accessor(), barrel.x(), barrel.y(), barrel.z()) != Blocks.BARREL) return;
        LinkedHashSet<PlayerTickState> viewers = chestSubscribersByPosition.get(
                ChestStorage.key(barrel.x(), barrel.y(), barrel.z()));
        int state = rt.blockStates().get(barrel.x(), barrel.y(), barrel.z(), Blocks.BARREL);
        boolean open = viewers != null && !viewers.isEmpty();
        int next = BuildingBlockRules.withBarrelOpen(state, open);
        if (next == state) return;
        rt.setBlockState(barrel.x(), barrel.y(), barrel.z(), Blocks.BARREL, next);
        rt.tickBlockChanges().put(barrel, (short) Blocks.BARREL);
        double[] at = BuildingBlockRules.barrelSoundPosition(barrel.x(), barrel.y(), barrel.z(), next);
        broadcastWorldSoundExact(open ? "barrel_open" : "barrel_close", at[0], at[1], at[2],
                (short) Blocks.BARREL);
    }

    private void removeChestSubscriber(long key, PlayerTickState player) {
        LinkedHashSet<PlayerTickState> subscribers = chestSubscribersByPosition.get(key);
        if (subscribers == null) return;
        subscribers.remove(player);
        if (subscribers.isEmpty()) chestSubscribersByPosition.remove(key);
        int[] pos = ChestStorage.unkey(key);
        redstone.engine().chestViewersChanged(pos[0], pos[1], pos[2]);
    }

    void closeChestSubscribersAt(int x, int y, int z) {
        LinkedHashSet<PlayerTickState> subscribers =
                chestSubscribersByPosition.get(ChestStorage.key(x, y, z));
        if (subscribers == null || subscribers.isEmpty()) return;
        for (PlayerTickState viewer : new ArrayList<>(subscribers)) {
            if (samePosition(viewer.openChest(), x, y, z)
                    || samePosition(viewer.openChestPartner(), x, y, z)) {
                closeChestSession(viewer, false);
            } else {
                unregisterChestSubscriber(viewer);
            }
        }
    }

    private static CanonicalLootContainerKind canonicalLootKind(int block) {
        if (block != Blocks.ENDER_CHEST && Blocks.isChestShaped(block)) {
            return CanonicalLootContainerKind.CHEST;
        }
        if (block == Blocks.BARREL) return CanonicalLootContainerKind.BARREL;
        if (block == Blocks.DISPENSER) return CanonicalLootContainerKind.DISPENSER;
        return Blocks.isDecoratedPot(block) ? CanonicalLootContainerKind.DECORATED_POT : null;
    }

    /** Exact vanilla order of the halves used by both persistence and UI publication. */
    private ChestTopology chestTopology(int x, int y, int z, int block) {
        BlockPos clicked = new BlockPos(x, y, z);
        if (!Blocks.isChestShaped(block)) return new ChestTopology(clicked, null);
        int state = rt.blockStates().get(x, y, z, block);
        int px = BuildingBlockRules.chestPartnerX(x, state);
        int pz = BuildingBlockRules.chestPartnerZ(z, state);
        if (px == x && pz == z
                || !Blocks.chestPairs(block, residentBlockType(rt.accessor(), px, y, pz))) {
            return new ChestTopology(clicked, null);
        }
        int partnerState = rt.blockStates().get(px, y, pz, block);
        if (!BuildingBlockRules.matchingChestStates(state, partnerState)) {
            return new ChestTopology(clicked, null);
        }
        BlockPos partner = new BlockPos(px, y, pz);
        BlockPos first = (state & BuildingBlockRules.CHEST_TYPE_MASK)
                == BuildingBlockRules.CHEST_RIGHT ? clicked : partner;
        return new ChestTopology(first, first.equals(clicked) ? partner : clicked);
    }

    private void publishCanonicalChestOpen(WorldRuntime.CanonicalLootOpenCompletion completion) {
        if (completion == null || !completion.ready()) return;
        WorldRuntime.CanonicalLootOpenRequest request = completion.request();
        PlayerTickState player = request.session();
        if (player == null || !rt.canonicalLootOpenRequestCurrent(request)
                || !request.reached() || player.isDead()) return;
        BlockPos clicked = request.clicked();
        int current = residentBlockType(rt.accessor(), clicked.x(), clicked.y(), clicked.z());
        if (current != request.clickedBlock()
                || !InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                        clicked.x(), clicked.y(), clicked.z())) return;
        ChestTopology topology = chestTopology(clicked.x(), clicked.y(), clicked.z(), current);
        if (!topology.orderedHalves().equals(request.orderedHalves())) return;
        closeActiveMenuBeforeOpen(player);
        ChestAccess chest = openChestAccess(player, clicked.x(), clicked.y(), clicked.z());
        BlockPos partner = chest.otherHalf(clicked.x(), clicked.y(), clicked.z());
        if (!chest.matchesSession(clicked, partner)) return;
        unregisterChestSubscriber(player);
        player.openChest(clicked.x(), clicked.y(), clicked.z(), partner);
        registerChestSubscriber(player, chest);
        sendTo(player, chestOpenMessage(
                clicked.x(), clicked.y(), clicked.z(), current, chestSlots(chest)));
        // A generated dispenser's first open is silent and never angers piglins (DispenserBlock).
        if (InteractRules.isSilentContainerMenu(current)) return;
        // [BARREL-SOUND] 통은 첫 열람자일 때만 syncBarrelOpenState 가 barrel_open 을 낸다.
        if (current != Blocks.BARREL) {
            broadcastWorldSound(containerOpenSound(current), clicked.x(), clicked.y(), clicked.z(),
                    (short) current);
        }
        rt.mobSystem().angerPiglinsNearGuardedEvent(
                player.nickname(), player.x(), player.y(), player.z(), true);
    }

    private record ChestTopology(BlockPos first, BlockPos second) {
        List<BlockPos> orderedHalves() {
            return second == null ? List.of(first) : List.of(first, second);
        }
    }

    /**
     * [ENDER-SHULKER] 열람자를 아는 보관함 해결. 엔더 상자만 <b>좌표가 아니라 그 플레이어</b>
     * 의 27칸을 돌려주고, 나머지는 좌표 키 저장소를 그대로 쓴다. 열람자를 인자로 받는 것이
     * 이 계약의 전부이며 — 상자를 여는 세 경로가 모두 열람자를 이미 들고 있다 — 넣기·꺼내기·
     * 병합·커서 규칙은 좌표 상자와 한 줄도 갈리지 않는다.
     */
    private ChestAccess openChestAccess(PlayerTickState viewer, int x, int y, int z) {
        BlockPos clicked = new BlockPos(x, y, z);
        int block = residentBlockType(rt.accessor(), x, y, z);
        if (block == Blocks.ENDER_CHEST) {
            // 짝을 이루지 않으므로(Blocks.chestPairs) 언제나 단일 27칸이다.
            return new ChestAccess(clicked, viewer.enderChest(), null, null, viewer);
        }
        // [CHEST-FAMILY] 큰 상자 합치기는 형상군 전체가 한다. 통(BARREL)은 여기 들어오지
        // 않고 아래 단일 컨테이너 갈래로 빠진다 — 통은 큰 상자를 이루지 않기 때문이다.
        if (!Blocks.isChestShaped(block)) {
            return new ChestAccess(clicked, rt.openContainer(x, y, z), null, null);
        }
        ChestTopology topology = chestTopology(x, y, z, block);
        if (topology.second() == null) {
            return new ChestAccess(clicked, rt.openContainer(x, y, z), null, null);
        }
        // DoubleBlockCombiner maps ChestType.RIGHT to FIRST and LEFT to SECOND.
        // Preserve that order instead of sorting coordinates: the vanilla 6-row inventory keeps
        // the same visual left/right rows for every facing and regardless of the clicked half.
        BlockPos first = topology.first();
        BlockPos second = topology.second();
        return new ChestAccess(first, rt.openContainer(first.x(), first.y(), first.z()),
                second, rt.openContainer(second.x(), second.y(), second.z()));
    }

    private record ChestSlotRef(ChestInventory inventory, int slot) {}

    /**
     * 열려 있는 보관함 한 벌. 좌표 상자는 {@code owner} 가 null 이고, [ENDER-SHULKER] 엔더
     * 상자만 <b>그 27칸을 소유한 플레이어</b>를 함께 들고 다닌다 — 같은 좌표를 서로 다른
     * 플레이어가 열면 내용이 서로 다르므로, 변경 방송을 위치가 아니라 소유자로 좁혀야 한다.
     */
    private record ChestAccess(
            BlockPos firstPosition,
            ChestInventory first,
            BlockPos secondPosition,
            ChestInventory second,
            PlayerTickState owner) {
        ChestAccess(BlockPos firstPosition, ChestInventory first,
                BlockPos secondPosition, ChestInventory second) {
            this(firstPosition, first, secondPosition, second, null);
        }

        int slotCount() {
            return second == null ? first.slots() : first.slots() + second.slots();
        }

        ChestSlotRef slot(int index) {
            return index < first.slots()
                    ? new ChestSlotRef(first, index)
                    : new ChestSlotRef(second, index - first.slots());
        }

        int add(PlayerInventory.DroppedStack source) {
            // [SHULKER-CONTENTS] 넣어도 27칸 참조가 사라지면 안 된다.
            int leftover = first.add(source.itemType(), source.count(), source.durability(),
                    source.enchantments(), source.mapId(), source.shulkerId(),
                    source.bucketMobData(), source.itemComponentData());
            return second == null || leftover <= 0 ? leftover
                    : second.add(source.itemType(), leftover, source.durability(),
                            source.enchantments(), source.mapId(), source.shulkerId(),
                            source.bucketMobData(), source.itemComponentData());
        }

        BlockPos otherHalf(int x, int y, int z) {
            if (secondPosition == null) return null;
            return samePosition(firstPosition, x, y, z) ? secondPosition : firstPosition;
        }

        boolean matchesSession(BlockPos opened, BlockPos openedPartner) {
            if (secondPosition == null) {
                return openedPartner == null && firstPosition.equals(opened);
            }
            return openedPartner != null
                    && (firstPosition.equals(opened) && secondPosition.equals(openedPartner)
                            || secondPosition.equals(opened) && firstPosition.equals(openedPartner));
        }

        void markDirty(WorldRuntime runtime) {
            runtime.chestStorage().markDirty(
                    firstPosition.x(), firstPosition.y(), firstPosition.z());
            if (secondPosition != null) {
                runtime.chestStorage().markDirty(
                        secondPosition.x(), secondPosition.y(), secondPosition.z());
            }
        }
    }

    /**
     * [COMPOSTER] 퇴비통 한 번의 우클릭. 도달 검사는 호출자가 이미 했다.
     *
     * <p>순서는 바닐라 {@code ComposterBlock#useItemOn} 그대로다 — <b>수거가 먼저</b>다.
     * 가득 찬 퇴비통에 퇴비 대상을 들고 우클릭하면 아이템이 들어가는 것이 아니라 뼛가루가
     * 나온다. 산출은 바닐라 {@code extractProduce} 와 같이 <b>인벤토리 직행이 아니라
     * 아이템 엔티티</b>로 튀어나온다({@code Block.popResource}).
     *
     * <p><b>사운드 divergence</b>: 바닐라의 {@code block.composter.fill/ready/empty} 세 종은
     * 이 저장소 프로토콜 사운드 열거에 없다. 새 열거값·새 샘플을 만드는 것은 오디오 표면의
     * 다른 트랙과 충돌하므로, 뜻이 가장 가까운 기존 셋으로 옮긴다:
     * 투입={@code plant} · 완성={@code bonemeal} · 수거={@code harvest}.
     */
    private void applyComposterInteract(PlayerTickState player, PlayerInventory.HandRef hand,
            int x, int y, int z, short selected) {
        int state = rt.blockStates().get(x, y, z, Blocks.COMPOSTER);
        if (ComposterRules.isReady(state)) {
            rt.setBlockState(x, y, z, Blocks.COMPOSTER, 0);
            rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) Blocks.COMPOSTER);
            rt.itemSystem().spawnDrop(PlayerInventory.BONE_MEAL, ComposterRules.PRODUCE_COUNT,
                    x + 0.5, y + 1.0, z + 0.5);
            broadcastWorldSound("harvest", x, y, z, (short) Blocks.COMPOSTER);
            return;
        }
        if (!ComposterRules.isCompostable(selected)) return;
        int next = ComposterRules.levelAfterInsert(state, selected, composterRandom.nextDouble());
        // 실패해도 아이템은 소비된다 — 바닐라와 같다. 소비에 실패하면(재고 없음) 아무 일도 없다.
        if (!InventoryRules.consumeOne(player.inventory(), hand, selected)) return;
        sendTo(player, inventoryMessage(player));
        if (next != ComposterRules.level(state)) {
            rt.setBlockState(x, y, z, Blocks.COMPOSTER, next);
            rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) Blocks.COMPOSTER);
        }
        broadcastWorldSound(ComposterRules.isReady(next) ? "bonemeal" : "plant",
                x, y, z, (short) Blocks.COMPOSTER);
    }

    /** [CONTAINER-MENUS] ShulkerBoxBlockEntity#startOpen plays block.shulker_box.open. */
    private static String containerOpenSound(int block) {
        return Blocks.isShulkerBox(block) ? "shulker_box_open" : "chest_open";
    }

    /**
     * [CONTAINER-MENUS] {@code DecoratedPotBlock#useWithoutItem} (an empty hand, a full pot or a
     * different stack): {@code decorated_pot.insert_fail} at volume 1 pitch 1; the client derives
     * the NEGATIVE wobble from that sound.
     */
    private void decoratedPotInsertFail(int x, int y, int z, int block) {
        broadcastWorldSound("decorated_pot_insert_fail", x, y, z, (short) block);
    }

    /**
     * {@code DecoratedPotBlock#useItemOn} (javap 26.3-snapshot-7): a non-empty held stack is
     * inserted when the pot is empty, or when the pot holds {@code isSameItemSameComponents} with
     * room below its max stack size; one item is consumed ({@code consumeAndReturn(1, player)})
     * and either becomes the pot item or grows it by one. Any other stack, or an empty hand,
     * falls to {@code useWithoutItem}, which only wobbles and plays
     * {@code decorated_pot.insert_fail}. A generated LOOT pot is unpacked first
     * ({@code getTheItem} unpacks the table), here through the canonical first-open writer, and
     * the same use is then replayed once. [CONTAINER-MENUS] The insert sound carries its fill pitch
     * and the client derives the dust plume and the POSITIVE wobble from it; the fail sound is sent
     * too and the client derives the NEGATIVE wobble from it (no separate block event).
     */
    private void applyDecoratedPotUse(PlayerTickState player, PlayerInventory.HandRef hand,
            int x, int y, int z, int block, boolean allowLootRequest) {
        if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                x, y, z)) return;
        BlockPos position = new BlockPos(x, y, z);
        ChestInventory pot = rt.openContainer(x, y, z);
        if (pot == null) {
            if (!allowLootRequest) return;
            rt.requestCanonicalChestOpen(player, rt.connectionIdForPlayer(player), block, true,
                    position, List.of(position), completion -> {
                        if (completion == null || !completion.ready()
                                || rt.players().get(player.nickname()) != player
                                || player.isDead()
                                || residentBlockType(rt.accessor(), x, y, z) != block) return;
                        applyDecoratedPotUse(player,
                                player.inventory().capture(PlayerInventory.Hand.MAIN),
                                x, y, z, block, false);
                    });
            return;
        }
        if (pot.slots() != BlockEntityRules.chestStorageSlots(block)) return;
        PlayerInventory.StackSnapshot held = player.inventory().stack(hand);
        if (held.itemType() == PlayerInventory.EMPTY || held.count() <= 0) {
            decoratedPotInsertFail(x, y, z, block);
            return;
        }
        if (pot.roomForSlot(0, held.itemType(), held.durability(), held.enchantments(),
                held.mapId(), held.shulkerId(), held.bucketMobData(),
                held.itemComponentData()) <= 0) {
            decoratedPotInsertFail(x, y, z, block);
            return;
        }
        // DecoratedPotBlock#useItemOn: decorated_pot.insert at pitch 0.7 + 0.5 * fill (fill = the
        // count after the insert over the max stack) and a DUST_PLUME burst above the pot; the
        // client derives the plume from this sound.
        int filled = (pot.itemType(0) == PlayerInventory.EMPTY ? 0 : pot.count(0)) + 1;
        float pitch = 0.7f + 0.5f * filled / (float) PlayerInventory.stackMax(held.itemType());
        rt.mobSystem().trialWorldSound("decorated_pot_insert", x + 0.5, y + 0.5, z + 0.5,
                (short) block, pitch);
        settleChestMutation(player, new ChestAccess(position, pot, null, null),
                (inventory, chest) -> {
                    PlayerInventory.HandRef planned =
                            inventory.capture(hand.hand(), hand.mainSlot());
                    PlayerInventory.StackSnapshot stack = inventory.stack(planned);
                    if (stack.itemType() != held.itemType() || stack.count() <= 0) return false;
                    if (chest.first().putInSlot(0, stack.itemType(), 1, stack.durability(),
                            stack.enchantments(), stack.mapId(), stack.shulkerId(),
                            stack.bucketMobData(), stack.itemComponentData()) != 1) {
                        return false;
                    }
                    return inventory.consumeOne(planned, stack.itemType());
                });
    }

    /** 선택한 허용 음식 하나를 원자적으로 소비해 좌표 모닥불의 첫 빈 칸에 올립니다. */
    private void applyCampfireFood(PlayerTickState player, PlayerInventory.HandRef hand,
            int x, int y, int z, short itemType) {
        if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(), x, y, z)) return;
        CampfireStorage storage = rt.campfireStorage();
        CampfireInventory existing = storage.peekAt(x, y, z);
        if (existing != null && existing.firstFreeSlot() < 0) return;
        PlayerInventory.CompletePersistenceSnapshot source =
                player.inventory().acquireSettlementLease();
        if (source == null) return;
        PlayerInventory plannedInventory = source.detachedInventory();
        CampfireInventory planned = existing == null
                ? new CampfireInventory() : detachedCampfire(existing);
        // 컨테이너 FIFO 는 같은 턴의 뒤 액션(스크롤 포함)보다 늦게 돈다. 사본의 현재 선택 칸이
        // 아니라 상호작용이 캡처한 칸에서 소비한다.
        if (!plannedInventory.consumeOne(
                plannedInventory.capture(hand.hand(), hand.mainSlot()), itemType)
                || planned.addFirst(itemType) < 0) {
            player.inventory().releaseSettlementLease(source);
            return;
        }
        PlayerInventory.CompletePersistenceSnapshot committedInventory =
                plannedInventory.completePersistenceSnapshot();
        long expectedRevision = existing == null ? 0 : existing.persistenceRevision();
        if (committedInventory.revision() != source.revision() + 1
                || planned.persistenceRevision() != expectedRevision + 1) {
            player.inventory().releaseSettlementLease(source);
            return;
        }
        long key = CampfireStorage.key(x, y, z);
        storage.deactivate(key);
        var target = new InventoryMutationTarget.Campfire(
                new InventoryMutationTarget.Position(x, y, z),
                campfireTypes(planned), campfireCookTicks(planned),
                planned.persistenceRevision());
        var command = new PlayerContainerSettlementCommand(
                WorldRuntime.stablePlayerContainerSettlementId(
                        player.playerId(), source.revision()),
                source.revision(), rt.playerInventoryMutationSnapshot(player, committedInventory),
                target, List.of(expectedRevision));
        Runnable rejected = () -> {
            player.inventory().releaseSettlementLease(source);
            if (existing != null) storage.activate(x, y, z);
            if (rt.players().get(player.nickname()) == player) {
                sendTo(player, inventoryMessage(player));
                broadcastCampfireUpdate(x, y, z, existing);
            }
        };
        try {
            // 조리로 증가한 revision을 같은 FIFO writer에서 음식 투입 CAS보다 먼저 확정한다.
            // 해당 좌표는 위에서 비활성화했으므로 저장 대기 중에도 입력 스냅샷이 유지된다.
            if (existing != null && !rt.flushDirtyCampfire(x, y, z)) {
                rejected.run();
                return;
            }
        } catch (RuntimeException exception) {
            rejected.run();
            throw exception;
        }
        rt.submitPlayerContainerSettlement(command, () -> {
            boolean installed = storage.peekAt(x, y, z) == existing
                    && (existing == null
                            || existing.persistenceRevision() == expectedRevision);
            if (installed) {
                storage.load(x, y, z, planned);
            }
            int state = rt.blockStates().get(x, y, z, Blocks.CAMPFIRE);
            if ((state & BuildingBlockRules.CAMPFIRE_LIT) != 0) storage.activate(x, y, z);
            if (installed) broadcastCampfireUpdate(x, y, z, planned);
            if (rt.players().get(player.nickname()) != player) {
                player.inventory().releaseSettlementLease(source);
                return;
            }
            if (!player.inventory().installCommittedSettlement(
                    source, committedInventory)) {
                player.inventory().releaseSettlementLease(source);
                return;
            }
            sendTo(player, inventoryMessage(player));
        }, rejected);
    }

    private static CampfireInventory detachedCampfire(CampfireInventory source) {
        CampfireInventory copy = new CampfireInventory();
        copy.restore(campfireTypes(source), campfireCookTicks(source));
        copy.restorePersistenceRevision(source.persistenceRevision());
        return copy;
    }

    private static short[] campfireTypes(CampfireInventory campfire) {
        return new short[] {campfire.itemType(0), campfire.itemType(1),
                campfire.itemType(2), campfire.itemType(3)};
    }

    private static int[] campfireCookTicks(CampfireInventory campfire) {
        return new int[] {campfire.cookTicks(0), campfire.cookTicks(1),
                campfire.cookTicks(2), campfire.cookTicks(3)};
    }

    private void applyFurnaceClick(PlayerAction.FurnaceClick click) {
        PlayerTickState player = rt.players().get(click.nickname());
        if (player == null || player.openFurnace() == null) return;
        BlockPos pos = player.openFurnace();
        if (!samePosition(pos, click.x(), click.y(), click.z())) return;
        int block = residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z());
        if (!FurnaceRules.isFurnace(block)
                || !InteractRules.withinContainerReach(
                        player.x(), player.y(), player.z(), player.crouching(), pos.x(), pos.y(), pos.z())) {
            closeFurnaceSession(player);
            return;
        }

        FurnaceInventory furnace = rt.furnaceStorage().openAt(
                pos.x(), pos.y(), pos.z(), FurnaceVariant.of(block));
        settleFurnaceMutation(player, pos, furnace, (inventory, planned) -> {
            int outputBefore = planned.count(FurnaceInventory.OUTPUT_SLOT);
            if (!inventory.clickFurnace(
                    planned, click.area(), click.slot(), click.button(), click.shift())) return -1;
            return planned.count(FurnaceInventory.OUTPUT_SLOT) < outputBefore
                    ? planned.collectSmeltXp() : 0;
        });
    }

    private void applyFurnaceDrag(PlayerAction.FurnaceDrag drag) {
        PlayerTickState player = rt.players().get(drag.nickname());
        if (player == null || !samePosition(player.openFurnace(), drag.x(), drag.y(), drag.z())) return;
        BlockPos pos = player.openFurnace();
        int block = residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z());
        if (!FurnaceRules.isFurnace(block) || !InteractRules.withinContainerReach(
                player.x(), player.y(), player.z(), player.crouching(),
                pos.x(), pos.y(), pos.z())) {
            closeFurnaceSession(player);
            return;
        }
        FurnaceInventory furnace = rt.furnaceStorage().openAt(
                pos.x(), pos.y(), pos.z(), FurnaceVariant.of(block));
        settleFurnaceMutation(player, pos, furnace, (inventory, planned) ->
                inventory.dragFurnace(planned, drag.areas(), drag.slots(), drag.button()) ? 0 : -1);
    }

    private void applyCollectFurnace(PlayerAction.CollectFurnace collect) {
        PlayerTickState player = rt.players().get(collect.nickname());
        if (player == null || !samePosition(
                player.openFurnace(), collect.x(), collect.y(), collect.z())) return;
        BlockPos pos = player.openFurnace();
        int block = residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z());
        if (!FurnaceRules.isFurnace(block) || !InteractRules.withinContainerReach(
                player.x(), player.y(), player.z(), player.crouching(),
                pos.x(), pos.y(), pos.z())) {
            closeFurnaceSession(player);
            return;
        }
        FurnaceInventory furnace = rt.furnaceStorage().openAt(
                pos.x(), pos.y(), pos.z(), FurnaceVariant.of(block));
        if (collect.slot() < 0 || collect.slot() >= (collect.area()
                == PlayerInventory.FurnaceArea.INVENTORY
                        ? PlayerInventory.SLOTS : FurnaceInventory.SLOTS)) return;
        settleFurnaceMutation(player, pos, furnace, (inventory, planned) -> {
            int outputBefore = planned.count(FurnaceInventory.OUTPUT_SLOT);
            if (!inventory.collectFurnace(planned)) return -1;
            return planned.count(FurnaceInventory.OUTPUT_SLOT) < outputBefore
                    ? planned.collectSmeltXp() : 0;
        });
    }

    private void applyPlaceFurnaceRecipe(PlayerAction.PlaceFurnaceRecipe place) {
        PlayerTickState player = rt.players().get(place.nickname());
        if (player == null || !samePosition(
                player.openFurnace(), place.x(), place.y(), place.z())) return;
        BlockPos pos = player.openFurnace();
        int block = residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z());
        if (!FurnaceRules.isFurnace(block)
                || !InteractRules.withinContainerReach(
                        player.x(), player.y(), player.z(), player.crouching(), pos.x(), pos.y(), pos.z())) {
            closeFurnaceSession(player);
            return;
        }
        FurnaceInventory furnace = rt.furnaceStorage().openAt(
                pos.x(), pos.y(), pos.z(), FurnaceVariant.of(block));
        settleFurnaceMutation(player, pos, furnace, (inventory, planned) ->
                inventory.placeFurnaceRecipe(
                        planned, place.recipeId(), place.maximum()) ? 0 : -1);
    }

    @FunctionalInterface
    private interface FurnaceMutation {
        /** -1 means rejected; otherwise the whole XP amount to publish after commit. */
        int apply(PlayerInventory inventory, FurnaceInventory furnace);
    }

    private void settleFurnaceMutation(PlayerTickState player, BlockPos pos,
            FurnaceInventory live, FurnaceMutation mutation) {
        // Smelting advances the live furnace revision every tick, independently of player input.
        // Queue that exact dirty baseline before the player+furnace CAS. Both writes use the same
        // FIFO persistence writer, so the settlement cannot race a merely periodic (older)
        // furnace checkpoint after a slow preceding world shutdown.
        rt.flushDirtyFurnaces();
        PlayerInventory.CompletePersistenceSnapshot source =
                player.inventory().acquireSettlementLease();
        if (source == null) return;
        FurnaceInventory.Snapshot liveBefore = live.snapshot();
        PlayerInventory plannedInventory;
        FurnaceInventory planned;
        int awardedXp;
        try {
            plannedInventory = source.detachedInventory();
            planned = detachedFurnace(live);
            awardedXp = mutation.apply(plannedInventory, planned);
            if (awardedXp < 0) {
                rejectFurnaceSettlement(player, pos, source, live);
                return;
            }
        } catch (RuntimeException failure) {
            rejectFurnaceSettlement(player, pos, source, live);
            log.warn("월드 {} 화로 ({},{},{}) 입력 계획을 거부합니다: {}", rt.worldId(),
                    pos.x(), pos.y(), pos.z(), failure.getMessage());
            return;
        }

        long nextPlayerRevision;
        try {
            nextPlayerRevision = Math.addExact(source.revision(), 1L);
        } catch (ArithmeticException overflow) {
            rejectFurnaceSettlement(player, pos, source, live);
            return;
        }
        PlayerInventory.CompletePersistenceSnapshot committedInventory;
        try {
            committedInventory = plannedInventory.completePersistenceSnapshot();
        } catch (RuntimeException failure) {
            rejectFurnaceSettlement(player, pos, source, live);
            log.warn("월드 {} 화로 ({},{},{}) 플레이어 스냅샷을 거부합니다: {}", rt.worldId(),
                    pos.x(), pos.y(), pos.z(), failure.getMessage());
            return;
        }
        if (committedInventory.revision() != nextPlayerRevision) {
            rejectFurnaceSettlement(player, pos, source, live);
            return;
        }

        FurnaceInventory.Snapshot plannedSnapshot = planned.snapshot();
        // Inventory/cursor/component-only furnace clicks are complete player-local mutations. Do
        // not manufacture a furnace CAS when the detached furnace payload is unchanged, even if
        // a rejected scratch swap consumed revisions on the detached copy.
        if (sameFurnacePayload(liveBefore, plannedSnapshot)) {
            if (player.inventory().installCommittedSettlement(source, committedInventory)) {
                rt.queuePlayerInventoryBaseline(player);
                sendTo(player, inventoryMessage(player));
                broadcastFurnaceUpdate(pos.x(), pos.y(), pos.z(), live);
            } else {
                player.inventory().releaseSettlementLease(source);
            }
            return;
        }

        // The detached plan may be player-local only. Reject an actual furnace advance before
        // creating a live staged command, while preserving those player-local clicks at a
        // terminal furnace revision.
        long expectedFurnaceRevision = liveBefore.revision();
        if (!furnaceRevisionCanAdvanceOnce(expectedFurnaceRevision)) {
            rejectFurnaceSettlement(player, pos, source, live);
            return;
        }
        long targetRevision;
        try {
            targetRevision = Math.addExact(expectedFurnaceRevision, 1L);
        } catch (ArithmeticException overflow) {
            rejectFurnaceSettlement(player, pos, source, live);
            return;
        }

        FurnaceInventory finalPlan;
        FurnaceInventory.StagedCommand staged;
        try {
            finalPlan = detachedFurnace(planned, targetRevision);
            staged = stageFurnaceCommand(live, liveBefore, finalPlan.snapshot());
            if (staged == null) {
                rejectFurnaceSettlement(player, pos, source, live);
                return;
            }
        } catch (RuntimeException failure) {
            rejectFurnaceSettlement(player, pos, source, live);
            log.warn("월드 {} 화로 ({},{},{}) logical command를 거부합니다: {}", rt.worldId(),
                    pos.x(), pos.y(), pos.z(), failure.getMessage());
            return;
        }

        long key = FurnaceStorage.key(pos.x(), pos.y(), pos.z());
        InventoryMutationTarget.Furnace target;
        PlayerContainerSettlementCommand command;
        try {
            target = new InventoryMutationTarget.Furnace(
                    new InventoryMutationTarget.Position(pos.x(), pos.y(), pos.z()),
                    furnaceTypes(finalPlan), furnaceCounts(finalPlan), finalPlan.burnTicks(),
                    finalPlan.burnTotalTicks(), finalPlan.cookTicks(), finalPlan.variant().code(),
                    finalPlan.xpMilli(), targetRevision, finalPlan.snapshot().stacks());
            command = new PlayerContainerSettlementCommand(
                    WorldRuntime.stablePlayerContainerSettlementId(
                            player.playerId(), source.revision()),
                    source.revision(), rt.playerInventoryMutationSnapshot(player, committedInventory),
                    target, List.of(expectedFurnaceRevision));
        } catch (RuntimeException failure) {
            staged.rollback();
            rejectFurnaceSettlement(player, pos, source, live);
            log.warn("월드 {} 화로 ({},{},{}) persistence target을 거부합니다: {}", rt.worldId(),
                    pos.x(), pos.y(), pos.z(), failure.getMessage());
            return;
        }
        AtomicBoolean completionClaimed = new AtomicBoolean();
        Runnable rejected = () -> {
            if (!completionClaimed.compareAndSet(false, true)) return;
            staged.rollback();
            player.inventory().releaseSettlementLease(source);
            if (rt.furnaceStorage().peekAt(pos.x(), pos.y(), pos.z()) == live
                    && live.needsTick()) {
                rt.furnaceStorage().activate(pos.x(), pos.y(), pos.z());
            }
            if (rt.players().get(player.nickname()) == player) {
                sendTo(player, furnaceUpdate(player, pos.x(), pos.y(), pos.z(), live));
            }
        };
        Runnable committed = () -> {
            if (!completionClaimed.compareAndSet(false, true)) return;
            boolean installed = rt.furnaceStorage().peekAt(pos.x(), pos.y(), pos.z()) == live
                    && live.persistenceRevision() == expectedFurnaceRevision;
            if (!installed) {
                staged.rollback();
                player.inventory().releaseSettlementLease(source);
                if (rt.furnaceStorage().peekAt(pos.x(), pos.y(), pos.z()) == live
                        && live.needsTick()) {
                    rt.furnaceStorage().activate(pos.x(), pos.y(), pos.z());
                }
                if (rt.players().get(player.nickname()) == player) {
                    sendTo(player, furnaceUpdate(player, pos.x(), pos.y(), pos.z(), live));
                }
                return;
            }
            try {
                if (!staged.commit()) {
                    throw new IllegalStateException("furnace staged command had no slot change");
                }
                live.restore(finalPlan.snapshot());
            } catch (RuntimeException failure) {
                try {
                    live.restore(liveBefore);
                } catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                player.inventory().releaseSettlementLease(source);
                if (live.needsTick()) rt.furnaceStorage().activate(pos.x(), pos.y(), pos.z());
                if (rt.players().get(player.nickname()) == player) {
                    sendTo(player, furnaceUpdate(player, pos.x(), pos.y(), pos.z(), live));
                }
                log.warn("월드 {} 화로 ({},{},{}) staged commit을 롤백합니다: {}", rt.worldId(),
                        pos.x(), pos.y(), pos.z(), failure.getMessage());
                return;
            }
            if (live.needsTick()) rt.furnaceStorage().activate(pos.x(), pos.y(), pos.z());
            if (awardedXp > 0) {
                rt.xpOrbSystem().spawnOrbs(
                        awardedXp, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5);
            }
            boolean playerInventoryInstalled = rt.players().get(player.nickname()) == player
                    && player.inventory().installCommittedSettlement(source, committedInventory);
            if (!playerInventoryInstalled) {
                player.inventory().releaseSettlementLease(source);
            }
            // Each viewer's furnace snapshot includes its cursor, so install the actor first.
            broadcastFurnaceUpdate(pos.x(), pos.y(), pos.z(), live);
            if (playerInventoryInstalled) sendTo(player, inventoryMessage(player));
        };
        rt.furnaceStorage().deactivate(key);
        try {
            rt.submitPlayerContainerSettlement(command, committed, rejected);
        } catch (RuntimeException | Error failure) {
            rejected.run();
            throw failure;
        }
    }

    private void rejectFurnaceSettlement(PlayerTickState player, BlockPos pos,
            PlayerInventory.CompletePersistenceSnapshot source, FurnaceInventory live) {
        player.inventory().releaseSettlementLease(source);
        if (rt.players().get(player.nickname()) == player) {
            sendTo(player, furnaceUpdate(player, pos.x(), pos.y(), pos.z(), live));
        }
    }

    private static boolean sameFurnacePayload(FurnaceInventory.Snapshot left,
            FurnaceInventory.Snapshot right) {
        return left.variant() == right.variant()
                && left.burnTicks() == right.burnTicks()
                && left.burnTotalTicks() == right.burnTotalTicks()
                && left.cookTicks() == right.cookTicks()
                && left.xpMilli() == right.xpMilli()
                && Arrays.equals(left.itemTypes(), right.itemTypes())
                && Arrays.equals(left.counts(), right.counts())
                && Arrays.equals(left.stacks(), right.stacks());
    }

    private static boolean stageFurnaceSlots(FurnaceInventory.StagedCommand staged,
            FurnaceInventory.Snapshot before, FurnaceInventory.Snapshot after) {
        short[] beforeTypes = before.itemTypes();
        int[] beforeCounts = before.counts();
        short[] afterTypes = after.itemTypes();
        int[] afterCounts = after.counts();
        boolean changed = false;
        for (int slot = 0; slot < FurnaceInventory.SLOTS; slot++) {
            if (before.stacks()[slot].equals(after.stacks()[slot])) continue;
            boolean accepted = afterTypes[slot] == PlayerInventory.EMPTY
                    ? staged.clear(slot)
                    : staged.replace(slot, after.stacks()[slot]);
            if (!accepted) return false;
            changed = true;
        }
        return changed;
    }

    private static FurnaceInventory.StagedCommand stageFurnaceCommand(
            FurnaceInventory live, FurnaceInventory.Snapshot before,
            FurnaceInventory.Snapshot after) {
        FurnaceInventory.StagedCommand staged = live.beginLogicalCommand();
        try {
            if (!stageFurnaceSlots(staged, before, after)) {
                staged.rollback();
                return null;
            }
            return staged;
        } catch (RuntimeException | Error failure) {
            staged.rollback();
            throw failure;
        }
    }

    private static FurnaceInventory detachedFurnace(FurnaceInventory source) {
        FurnaceInventory copy = new FurnaceInventory(source.variant());
        copy.restore(source.snapshot());
        return copy;
    }

    /** Rebinds a validated detached result to the one revision reserved by its CAS target. */
    private static FurnaceInventory detachedFurnace(FurnaceInventory source, long revision) {
        FurnaceInventory.Snapshot snapshot = source.snapshot();
        FurnaceInventory copy = new FurnaceInventory(snapshot.variant());
        copy.restore(snapshot.withRevision(revision));
        return copy;
    }

    private static short[] furnaceTypes(FurnaceInventory furnace) {
        return new short[] {furnace.itemType(0), furnace.itemType(1), furnace.itemType(2)};
    }

    private static int[] furnaceCounts(FurnaceInventory furnace) {
        return new int[] {furnace.count(0), furnace.count(1), furnace.count(2)};
    }

    private void applyDropFurnaceCursor(PlayerAction.DropFurnaceCursor drop) {
        PlayerTickState player = rt.players().get(drop.nickname());
        if (player == null || !samePosition(
                player.openFurnace(), drop.x(), drop.y(), drop.z())) return;
        BlockPos pos = player.openFurnace();
        FurnaceInventory furnace = rt.furnaceStorage().openAt(pos.x(), pos.y(), pos.z(),
                FurnaceVariant.of(residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z())));
        settleCursorGroundDrop(player, false, drop.one(), () ->
                sendTo(player, furnaceUpdate(player, pos.x(), pos.y(), pos.z(), furnace)));
    }

    private void applyCloseFurnace(PlayerAction.CloseFurnace close) {
        PlayerTickState player = rt.players().get(close.nickname());
        if (player != null
                && samePosition(player.openFurnace(), close.x(), close.y(), close.z())) {
            closeFurnaceSession(player);
        } else if (player != null) {
            sendTo(player, new FurnaceClosed(close.x(), close.y(), close.z()));
        }
    }

    private void closeFurnaceSession(PlayerTickState player) {
        BlockPos pos = player.openFurnace();
        if (pos == null) return;
        dropCraftingOverflow(player, player.inventory().closeContainerCursor());
        unregisterFurnaceSubscriber(player);
        player.closeFurnace();
        sendTo(player, inventoryMessage(player));
        sendTo(player, new FurnaceClosed(pos.x(), pos.y(), pos.z()));
    }

    private void registerFurnaceSubscriber(PlayerTickState player, int x, int y, int z) {
        long key = FurnaceStorage.key(x, y, z);
        furnaceSubscribersByPosition.computeIfAbsent(key, ignored -> new LinkedHashSet<>())
                .add(player);
        furnaceSubscriptionByPlayer.put(player, key);
    }

    private void unregisterFurnaceSubscriber(PlayerTickState player) {
        Long key = furnaceSubscriptionByPlayer.remove(player);
        if (key == null) return;
        LinkedHashSet<PlayerTickState> subscribers = furnaceSubscribersByPosition.get(key);
        if (subscribers == null) return;
        subscribers.remove(player);
        if (subscribers.isEmpty()) furnaceSubscribersByPosition.remove(key);
    }

    void closeFurnaceSubscribersAt(int x, int y, int z) {
        long key = FurnaceStorage.key(x, y, z);
        LinkedHashSet<PlayerTickState> subscribers = furnaceSubscribersByPosition.get(key);
        if (subscribers == null || subscribers.isEmpty()) return;
        for (PlayerTickState viewer : new ArrayList<>(subscribers)) {
            if (samePosition(viewer.openFurnace(), x, y, z)) {
                closeFurnaceSession(viewer);
            } else {
                unregisterFurnaceSubscriber(viewer);
            }
        }
    }

    private static boolean furnaceRevisionCanAdvanceOnce(long revision) {
        return revision >= 0 && revision < Long.MAX_VALUE - 1;
    }

    private void processFurnaces() {
        FurnaceStorage storage = rt.furnaceStorage();
        for (long key : storage.tickingKeys()) {
            int[] pos = FurnaceStorage.unkey(key);
            int block = residentBlockType(rt.accessor(), pos[0], pos[1], pos[2]);
            if (block == UNAVAILABLE_BLOCK) {
                storage.deactivate(key);
                continue;
            }
            if (!FurnaceRules.isFurnace(block)) {
                dropFurnaceContents(rt, pos[0], pos[1], pos[2]);
                continue;
            }
            FurnaceInventory furnace = storage.peekAt(pos[0], pos[1], pos[2]);
            if (furnace == null) {
                storage.deactivate(key);
                continue;
            }
            // [FURNACE-VARIANT] 블록은 제련로인데 변형이 다르면(같은 좌표에 다른 제련로가
            // 다시 놓임) 옛 내용물은 새 변형이 받지 않을 수 있다. 블록이 통째로 바뀐 위 경로와
            // 같이 쏟아 내는 것이 유일하게 안전한 처리다.
            if (furnace.variant() != FurnaceVariant.of(block)) {
                dropFurnaceContents(rt, pos[0], pos[1], pos[2]);
                continue;
            }
            // A player/container target cannot represent Long.MAX_VALUE. Reject the final
            // advance before FurnaceInventory mutates any slot, progress, or XP field.
            if (!furnaceRevisionCanAdvanceOnce(furnace.persistenceRevision())) {
                storage.deactivate(key);
                log.warn("월드 {} 화로 ({},{},{}) revision이 terminal 경계라 틱을 거부합니다",
                        rt.worldId(), pos[0], pos[1], pos[2]);
                continue;
            }
            boolean wasBurning = furnace.burning();
            boolean changed;
            try {
                changed = furnace.tick();
            } catch (IllegalStateException overflow) {
                // FurnaceInventory preflights XP and revision capacity before its first write.
                // Keep the exact state intact and stop retrying this terminal/overflow entry.
                storage.deactivate(key);
                log.warn("월드 {} 화로 ({},{},{}) 틱을 거부하고 상태를 보존합니다: {}",
                        rt.worldId(), pos[0], pos[1], pos[2], overflow.getMessage());
                continue;
            }
            boolean burning = furnace.burning();
            // [FURNACE-VARIANT] 점화 스왑 대상은 변형 표가 정한다. 33↔34 를 손으로 쓰면
            // 점화된 용광로가 화로로 바뀌어 사라진다.
            int expected = furnace.variant().blockId(burning);
            if (burning != wasBurning || block != expected) {
                swapFurnaceTypePreservingState(pos[0], pos[1], pos[2], expected);
                changed = true;
            }
            if (changed) {
                storage.markDirty(pos[0], pos[1], pos[2]);
                broadcastFurnaceUpdate(pos[0], pos[1], pos[2], furnace);
            }
            if (!furnace.needsTick()) {
                storage.deactivate(key);
                storage.discardIfEmpty(key);
            }
        }
    }

    private void processBrewingSessions() {
        BrewingStorage storage = rt.brewingStorage();
        for (long key : storage.tickingKeys()) {
            int[] pos = BrewingStorage.unkey(key);
            int block = residentBlockType(rt.accessor(), pos[0], pos[1], pos[2]);
            if (block == UNAVAILABLE_BLOCK) {
                storage.deactivate(key);
                continue;
            }
            if (block != Blocks.BREWING_STAND) {
                dropBrewingContents(rt, pos[0], pos[1], pos[2]);
                continue;
            }
            BrewingInventory live = storage.peekAt(pos[0], pos[1], pos[2]);
            if (live == null) {
                storage.deactivate(key);
                continue;
            }
            BrewingInventory planned = detachedBrewing(live);
            boolean changed = false;
            // The owner loop runs at 10 Hz while brewing counters use Java's 20 TPS units.
            for (int mcTick = 0; mcTick < 2; mcTick++) {
                changed = planned.tick() || changed;
            }
            // [BREWING-26.3] doBrew 산출물: 완료음(레벨 이벤트 1035)과 재료 잔여물(유리병) 드롭.
            int brewEvents = planned.drainBrewEvents();
            List<BrewingInventory.StoredStack> remainders = planned.drainRemainderDrops();
            if (!changed) {
                storage.deactivate(key);
                storage.discardIfEmpty(key);
                continue;
            }
            planned = planned.copyAtPersistenceRevision(live.persistenceRevision() + 1);
            var target = new InventoryMutationTarget.Brewing(
                    new InventoryMutationTarget.Position(pos[0], pos[1], pos[2]),
                    brewingTypes(planned), brewingCounts(planned), planned.componentsSnapshot(),
                    planned.fuel(), planned.brewTicks(), planned.brewingIngredient(),
                    planned.persistenceRevision());
            boolean committed = rt.brewingPersistence() == null
                    || rt.brewingPersistence().persistTick(
                            rt.worldId(), live.persistenceRevision(), target)
                            != com.gameexpert.brewing.service.BrewingPersistenceService.Outcome.STALE;
            if (!committed || storage.peekAt(pos[0], pos[1], pos[2]) != live) {
                storage.deactivate(key);
                continue;
            }
            storage.load(pos[0], pos[1], pos[2], planned);
            // [BREWING-26.3] craftRemainder(드래곤의 숨결 → 유리병)가 재료 칸에 못 들어가면 바닐라
            // Containers.dropItemStack(level, x, y, z) 처럼 양조대 블록 좌표에 떨군다.
            for (BrewingInventory.StoredStack remainder : remainders) {
                rt.itemSystem().spawnDrop(remainder.itemType(), remainder.count(),
                        pos[0], pos[1], pos[2]);
            }
            if (brewEvents > 0) {
                broadcastWorldSound("brewing_stand_brew", pos[0], pos[1], pos[2],
                        (short) Blocks.BREWING_STAND);
            }
            for (PlayerTickState viewer : rt.players().values()) {
                if (samePosition(viewer.openCraftingTable(), pos[0], pos[1], pos[2])
                        && storage.ownsLease(pos[0], pos[1], pos[2], viewer.nickname())) {
                    sendTo(viewer,
                            brewingUpdate(viewer, pos[0], pos[1], pos[2], planned));
                    break;
                }
            }
            if (!planned.needsTick()) {
                storage.deactivate(key);
                storage.discardIfEmpty(key);
            }
        }
    }

    private void processCampfires() {
        CampfireStorage storage = rt.campfireStorage();
        long tickNo = rt.tickNo();

        // 실제 조리 틱은 점화되었거나 로드 직후 상태 확인이 필요한 점유 좌표에만 수행합니다.
        for (long key : storage.tickingKeys()) {
            int[] pos = CampfireStorage.unkey(key);
            int block = residentBlockType(rt.accessor(), pos[0], pos[1], pos[2]);
            if (block == UNAVAILABLE_BLOCK) {
                storage.park(key);
                continue;
            }
            if (block != Blocks.CAMPFIRE) {
                dropCampfireContents(rt, pos[0], pos[1], pos[2]);
                continue;
            }
            CampfireInventory campfire = storage.peekAt(pos[0], pos[1], pos[2]);
            if (campfire == null) {
                storage.deactivate(key);
                continue;
            }

            int state = rt.blockStates().get(pos[0], pos[1], pos[2], Blocks.CAMPFIRE);
            boolean lit = (state & BuildingBlockRules.CAMPFIRE_LIT) != 0;
            CampfireInventory.TickResult result = campfire.tick(lit);
            if (result.changed()) storage.markDirty(pos[0], pos[1], pos[2]);
            for (CampfireInventory.CompletedStack completed : result.completed()) {
                rt.itemSystem().spawnDrop(completed.itemType(), 1,
                        pos[0] + 0.5, pos[1] + 1.0, pos[2] + 0.5);
            }

            boolean forceUpdate = result.finishedCooling()
                    || !result.completed().isEmpty()
                    || storage.needsInitialUpdate(key);
            if (!campfire.occupied()) {
                storage.removeIfEmpty(pos[0], pos[1], pos[2]);
                broadcastCampfireUpdate(pos[0], pos[1], pos[2], null);
                continue;
            }
            if (forceUpdate) {
                broadcastCampfireUpdate(pos[0], pos[1], pos[2], campfire);
            }
            if (!lit && !campfire.needsCooldown()) storage.deactivate(key);
        }

        // 점화되지 않은 음식도 재접속/스트리밍 클라이언트가 복원할 수 있게 예약 좌표만 1 Hz로 보냅니다.
        for (long key : storage.drainDueUpdates(tickNo)) {
            int[] pos = CampfireStorage.unkey(key);
            CampfireInventory campfire = storage.peekAt(pos[0], pos[1], pos[2]);
            if (campfire == null) continue;
            int block = residentBlockType(rt.accessor(), pos[0], pos[1], pos[2]);
            if (block == UNAVAILABLE_BLOCK) {
                storage.park(key);
                continue;
            }
            if (block != Blocks.CAMPFIRE) {
                dropCampfireContents(rt, pos[0], pos[1], pos[2]);
                continue;
            }
            int state = rt.blockStates().get(pos[0], pos[1], pos[2], Blocks.CAMPFIRE);
            if ((state & BuildingBlockRules.CAMPFIRE_LIT) != 0
                    || campfire.needsCooldown()) {
                storage.activate(pos[0], pos[1], pos[2]);
            }
            broadcastCampfireUpdate(pos[0], pos[1], pos[2], campfire);
        }
        retryPendingCampfireUpdates();
    }

    /** 송신 큐 압력 시 좌표별 최신 상태 하나만 보존하고 다음 틱에 순서대로 재시도합니다. */
    private void retryPendingCampfireUpdates() {
        if (pendingCampfireUpdates.isEmpty() || rt.ctx().broadcaster() == null) return;
        var iterator = pendingCampfireUpdates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, CampfireUpdate> entry = iterator.next();
            if (!rt.ctx().broadcaster().enqueueRetainedBroadcastFromTick(
                    rt.worldId(), entry.getValue())) {
                break;
            }
            int[] pos = CampfireStorage.unkey(entry.getKey());
            rt.campfireStorage().recordUpdate(pos[0], pos[1], pos[2], rt.tickNo());
            iterator.remove();
        }
    }

    /** 모닥불 상태는 GUI 구독자가 아니라 월드 전체에 보내 새 스트리밍 클라이언트도 복원하게 합니다. */
    void broadcastCampfireUpdate(int x, int y, int z, CampfireInventory campfire) {
        CampfireUpdate message = campfireUpdate(x, y, z, campfire);
        if (rt.ctx().broadcaster() == null) {
            rt.campfireStorage().recordUpdate(x, y, z, rt.tickNo());
            return;
        }
        long key = CampfireStorage.key(x, y, z);
        if (pendingCampfireUpdates.containsKey(key)) {
            pendingCampfireUpdates.put(key, message);
            return;
        }
        if (rt.ctx().broadcaster().enqueueRetainedBroadcastFromTick(rt.worldId(), message)) {
            rt.campfireStorage().recordUpdate(x, y, z, rt.tickNo());
        } else {
            pendingCampfireUpdates.put(key, message);
        }
    }

    private static CampfireUpdate campfireUpdate(
            int x, int y, int z, CampfireInventory campfire) {
        List<CampfireSlot> slots = new ArrayList<>(CampfireInventory.SLOTS);
        for (int slot = 0; slot < CampfireInventory.SLOTS; slot++) {
            short itemType = campfire == null ? PlayerInventory.EMPTY : campfire.itemType(slot);
            int cookTicks = campfire == null ? 0 : campfire.cookTicks(slot);
            slots.add(new CampfireSlot(slot, itemType, cookTicks));
        }
        return new CampfireUpdate(x, y, z, List.copyOf(slots));
    }

    private void broadcastFurnaceUpdate(int x, int y, int z, FurnaceInventory furnace) {
        redstone.engine().containerChanged(x, y, z);
        LinkedHashSet<PlayerTickState> subscribers =
                furnaceSubscribersByPosition.get(FurnaceStorage.key(x, y, z));
        if (subscribers == null || subscribers.isEmpty()) return;
        List<InventorySlot> slots = furnaceSlots(furnace);
        String variant = furnace.variant().name();
        int burnTicks = furnace.burnTicks();
        int burnTotalTicks = furnace.burnTotalTicks();
        int cookTicks = furnace.cookTicks();
        int cookTotalTicks = furnace.cookTotalTicks();
        for (PlayerTickState viewer : subscribers) {
            if (samePosition(viewer.openFurnace(), x, y, z)) {
                sendTo(viewer, furnaceUpdate(viewer, x, y, z, variant, slots,
                        burnTicks, burnTotalTicks, cookTicks, cookTotalTicks));
            }
        }
    }

    private static FurnaceOpen furnaceOpen(
            PlayerTickState player, int x, int y, int z, FurnaceInventory furnace) {
        return new FurnaceOpen(x, y, z, furnace.variant().name(), furnaceSlots(furnace),
                furnace.burnTicks(), furnace.burnTotalTicks(),
                furnace.cookTicks(), furnace.cookTotalTicks(),
                craftingStack(player.inventory().cursorType(),
                        player.inventory().cursorCount(),
                        player.inventory().cursorDurability(),
                        player.inventory().cursorEnchantments(),
                        player.inventory().cursorMapId(),
                        player.inventory().cursorShulkerId(),
                        player.inventory().cursorBucketMobData(),
                        // [ENCHANT-WIDE] 확장 인챈트·이름 등 성분도 커서 표시에 싣는다.
                        player.inventory().cursorItemComponentData()));
    }

    private static FurnaceUpdate furnaceUpdate(
            PlayerTickState player, int x, int y, int z, FurnaceInventory furnace) {
        return furnaceUpdate(player, x, y, z, furnace.variant().name(), furnaceSlots(furnace),
                furnace.burnTicks(), furnace.burnTotalTicks(),
                furnace.cookTicks(), furnace.cookTotalTicks());
    }

    private static FurnaceUpdate furnaceUpdate(
            PlayerTickState player, int x, int y, int z, String variant,
            List<InventorySlot> slots, int burnTicks, int burnTotalTicks,
            int cookTicks, int cookTotalTicks) {
        return new FurnaceUpdate(x, y, z, variant, slots,
                burnTicks, burnTotalTicks, cookTicks, cookTotalTicks,
                craftingStack(player.inventory().cursorType(),
                        player.inventory().cursorCount(),
                        player.inventory().cursorDurability(),
                        player.inventory().cursorEnchantments(),
                        player.inventory().cursorMapId(),
                        player.inventory().cursorShulkerId(),
                        player.inventory().cursorBucketMobData(),
                        // [ENCHANT-WIDE] 확장 인챈트·이름 등 성분도 커서 표시에 싣는다.
                        player.inventory().cursorItemComponentData()));
    }

    private static List<InventorySlot> furnaceSlots(FurnaceInventory furnace) {
        List<InventorySlot> slots = new ArrayList<>(FurnaceInventory.SLOTS);
        for (int slot = 0; slot < FurnaceInventory.SLOTS; slot++) {
            PlayerInventory.StackSnapshot stack = furnace.stack(slot);
            slots.add(inventorySlot(slot, stack.itemType(), stack.count(),
                    PlayerInventory.isDurable(stack.itemType()) ? stack.durability() : null,
                    stack.enchantments(), stack.mapId() == 0 ? null : stack.mapId(),
                    stack.shulkerId() == 0 ? null : stack.shulkerId(), stack.bucketMobData(),
                    stack.itemComponentData()));
        }
        return slots;
    }

    private static boolean samePosition(BlockPos pos, int x, int y, int z) {
        return pos != null && pos.x() == x && pos.y() == y && pos.z() == z;
    }

    /** 대상·리치·종별 사전 검사가 끝난 사용: 성장 결과와 무관하게 먼저 뼛가루 한 개를 소비한다. */
    static boolean useBoneMeal(PlayerInventory inventory, Runnable growthAttempt) {
        if (!InventoryRules.consumeSelectedBoneMeal(inventory)) return false;
        growthAttempt.run();
        return true;
    }

    static boolean useBoneMeal(PlayerInventory inventory, PlayerInventory.HandRef hand,
            Runnable growthAttempt) {
        if (!InventoryRules.consumeOne(inventory, hand, PlayerInventory.BONE_MEAL)) return false;
        growthAttempt.run();
        return true;
    }

    static boolean useBoneMealIfApplicable(
            PlayerInventory inventory, boolean applicable, Runnable growthAttempt) {
        return applicable && useBoneMeal(inventory, growthAttempt);
    }

    static boolean useBoneMealIfApplicable(PlayerInventory inventory,
            PlayerInventory.HandRef hand, boolean applicable, Runnable growthAttempt) {
        return applicable && useBoneMeal(inventory, hand, growthAttempt);
    }

    /** 유리병 스택의 손 정체성을 보존해 물병 하나로 옮긴다. 공간 부족이면 완전 복원한다. */
    private static boolean fillGlassBottle(
            PlayerInventory inventory, PlayerInventory.HandRef hand) {
        return fillBottleWith(inventory, hand, (short) Blocks.WATER_BOTTLE);
    }

    /** [DRAGON] 빈 병 하나를 {@code product}(물병 · 드래곤의 숨결)로 바꾼다. 공간 부족이면 완전 복원한다. */
    private static boolean fillBottleWith(
            PlayerInventory inventory, PlayerInventory.HandRef hand, short product) {
        PlayerInventory.StackSnapshot before = inventory.stack(hand);
        if (before.itemType() != (short) Blocks.GLASS_BOTTLE || before.count() <= 0) return false;
        if (before.count() == 1) {
            return inventory.replaceSingle(hand, (short) Blocks.GLASS_BOTTLE, product);
        }
        if (!inventory.consumeOne(hand, (short) Blocks.GLASS_BOTTLE)) return false;
        if (inventory.addItem(product, 1) == 1) return true;
        inventory.setStack(inventory.capture(hand.hand()), before);
        return false;
    }

    /** 설치 아이템을 액션이 캡처한 손에서만 소비한다. */
    private static boolean consumeForPlace(
            PlayerInventory inventory, PlayerInventory.HandRef hand, short blockType) {
        return InventoryRules.consumeForPlace(inventory, hand, blockType);
    }

    /** 서버 권위 양동이: 좌표/리치/블록/선택슬롯을 모두 확인한 뒤 인벤과 유체를 한 틱에 반영. */
    private void applyBucketInteract(PlayerTickState player, PlayerInventory.HandRef hand,
            int x, int y, int z, short selected) {
        // Y/리치를 먼저 검증해 범위 밖 지형 조회도 하지 않는다.
        if (BlockEditRules.isTargetRejected(player.x(), player.y(), player.z(), player.crouching(), x, y, z)) {
            return;
        }
        int current = residentBlockType(rt.accessor(), x, y, z);
        if (current == UNAVAILABLE_BLOCK) return;
        int currentState = rt.blockStates().get(x, y, z, current);
        boolean waterloggable = BuildingBlockRules.canAcceptWater(current, currentState);
        boolean fillFromWaterlogged = selected == PlayerInventory.BUCKET && waterloggable
                && (currentState & COPPER_WATERLOGGED) != 0;
        boolean emptyIntoWaterlogged = selected == PlayerInventory.WATER_BUCKET && waterloggable
                && (currentState & COPPER_WATERLOGGED) == 0;
        if (!fillFromWaterlogged && !emptyIntoWaterlogged
                && BlockEditRules.isBucketRejected(
                        player.x(), player.y(), player.z(), player.crouching(),
                        x, y, z, selected, current)) {
            return;
        }

        short replacement;
        int worldReplacement;
        if (fillFromWaterlogged) {
            replacement = PlayerInventory.WATER_BUCKET;
            worldReplacement = current;
        } else if (emptyIntoWaterlogged) {
            replacement = PlayerInventory.BUCKET;
            worldReplacement = current;
        } else if (selected == PlayerInventory.BUCKET) {
            replacement = current == WATER_SOURCE
                    ? PlayerInventory.WATER_BUCKET
                    : current == LAVA_SOURCE
                            ? PlayerInventory.LAVA_BUCKET
                            : PlayerInventory.POWDER_SNOW_BUCKET;
            worldReplacement = AIR;
        } else {
            replacement = PlayerInventory.BUCKET;
            worldReplacement = selected == PlayerInventory.WATER_BUCKET
                    ? WATER_SOURCE
                    : selected == PlayerInventory.LAVA_BUCKET
                            ? LAVA_SOURCE
                            : Blocks.POWDER_SNOW;
        }

        // 선택 슬롯이 예상한 개별 양동이일 때만 교체. 실패 시 월드는 무변경.
        if (!InventoryRules.replaceBucket(player.inventory(), hand, selected, replacement)) {
            return;
        }
        if (fillFromWaterlogged || emptyIntoWaterlogged) {
            int nextState = fillFromWaterlogged
                    ? currentState & ~COPPER_WATERLOGGED
                    : currentState | COPPER_WATERLOGGED;
            rt.setBlockState(x, y, z, current, nextState);
            rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) current);
        } else if (selected == PlayerInventory.WATER_BUCKET
                || selected == PlayerInventory.LAVA_BUCKET) {
            rt.fluidSim().applyBucketChange(x, y, z, worldReplacement);
        } else {
            rt.fluidSim().applyChange(x, y, z, worldReplacement);
        }
        sendTo(player, inventoryMessage(player));
        String kind;
        if (selected == PlayerInventory.BUCKET) {
            kind = current == LAVA_SOURCE ? "bucket_fill_lava" : "bucket_fill_water";
        } else {
            kind = selected == PlayerInventory.LAVA_BUCKET ? "bucket_empty_lava" : "bucket_empty_water";
        }
        broadcastWorldSound(kind, x, y, z, (short) current);
    }

    /** 물고기 양동이는 물을 보존해 방출하고, 생성된 개체를 서버 저장/디스폰 면제로 표시한다. */
    private void applyFishBucketInteract(
            PlayerTickState player, PlayerInventory.HandRef hand,
            int x, int y, int z, short selected, int current) {
        if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(), x, y, z)) return;
        boolean sulfurCube = selected == PlayerInventory.SULFUR_CUBE_BUCKET;
        boolean water = Fluids.isWater(current);
        if (current != AIR && !(water && !sulfurCube)) return;
        PlayerInventory.StackSnapshot bucketStack = player.inventory().stack(hand);
        if (bucketStack.itemType() != selected || bucketStack.count() != 1) return;
        if (!InventoryRules.replaceBucket(
                player.inventory(), hand, selected, PlayerInventory.BUCKET)) return;
        if (current == AIR && !sulfurCube) rt.fluidSim().applyChange(x, y, z, WATER_SOURCE);
        rt.mobSystem().releaseBucketFish(
                selected, bucketStack.bucketMobData(), x + 0.5, y + 0.1, z + 0.5);
        sendTo(player, inventoryMessage(player));
        broadcastWorldSound(sulfurCube ? "bucket_fill_water" : "bucket_empty_water",
                x, y, z, (short) current);
    }

    // 침대 상호작용(S2a): ① 항상 개인 리스폰 지점 설정 ② 밤이면 수면 진입.
    private void applyBedInteract(PlayerTickState player, int x, int y, int z, int bedId) {
        int state = rt.blockStates().get(x, y, z, bedId);
        int headX = BuildingBlockRules.bedHeadX(x, state);
        int headZ = BuildingBlockRules.bedHeadZ(z, state);
        if (!validBedPair(headX, y, headZ)) {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error(
                    "BED_INVALID"));
            return;
        }
        if (isBedOccupiedByOther(player, headX, y, headZ)) {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error(
                    "BED_OCCUPIED"));
            return;
        }
        // [FURNITURE-26.3] 건초 침대는 개인 리스폰 지점을 **설정하지 않는다**(바닐라 [B]).
        // 나머지(쌍 판정·점유·수면 진입)는 일반 침대와 완전히 같다.
        if (!rt.customDimension() && !Blocks.isStrawBed(bedId)) {
            player.setBedSpawn(headX, y, headZ);
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.BedSpawnSet());
            // [MAPNAV] 나침반이 즉시 새 침대를 가리키도록 유효 스폰 지점을 함께 갱신한다.
            if (!sendSpawnPoint(player)) spawnPointResend.add(player.nickname());
        }
        if (rt.clock().isNight()) { // 밤(NIGHT_START~NIGHT_END)
            if (!player.sleeping()) {
                // 어느 침대에서 자는지는 리스폰 지점과 따로 기록한다 — 건초 침대는 리스폰을
                // 남기지 않으므로 bedSpawn 으로는 "자고 있는 침대"를 되찾을 수 없다.
                if (Blocks.isStrawBed(bedId)) {
                    int count = rt.ctx().stateService() == null
                            ? (player.sleepInStrawBed() == Integer.MAX_VALUE
                                    ? Integer.MAX_VALUE : player.sleepInStrawBed() + 1)
                            : rt.ctx().stateService().recordStrawBedSleep(player.playerId(),
                                    player.dimensionIdentity(rt.worldId()));
                    player.restoreStrawBedSleepCount(count);
                    sendTo(player, new com.gameexpert.ws.dto.WsMessages.PlayerStatistics(count));
                }
                player.beginSleep(headX, y, headZ);
                broadcastSleepStatus();
            }
        }
    }

    /**
     * [FURNITURE-26.3] 수면 종료 한 지점. 잠에서 깨는 모든 경로(아침 전환·이동 취소·피격)가
     * 이 하나만 부르므로 "건초 침대는 단회용"이 경로마다 갈리지 않는다.
     *
     * <p>바닐라 건초 침대는 <b>아침에 깨거나 수면 도중 침대를 떠나면 아무것도 떨구지 않고
     * 부서진다</b> [B]. 그래서 여기서 자던 칸을 확인해 건초 침대면 머리·발 두 칸을 지운다.
     * 드랍은 없다 — 채굴로 부순 건초 침대는 평소대로 자기 자신을 떨구므로(지지 분류 참고)
     * "드랍 없음"은 이 자멸 경로에만 걸린다.
     *
     * @return 실제로 자고 있다가 깬 경우 true(호출부가 수면 현황 재방송을 결정한다).
     */
    private boolean endSleep(PlayerTickState player) {
        if (!player.sleeping()) return false;
        player.setSleeping(false);
        int headX = player.sleepBedX();
        int headY = player.sleepBedY();
        int headZ = player.sleepBedZ();
        int bedId = residentBlockType(rt.accessor(), headX, headY, headZ);
        if (!Blocks.isStrawBed(bedId)) return true;
        int headState = rt.blockStates().get(headX, headY, headZ, bedId);
        int footX = BuildingBlockRules.bedOtherX(headX, headState);
        int footZ = BuildingBlockRules.bedOtherZ(headZ, headState);
        if (residentBlockType(rt.accessor(), footX, headY, footZ) == bedId) {
            rt.fluidSim().applyChange(footX, headY, footZ, AIR);
            refreshConnectionsAround(footX, headY, footZ);
            refreshStairsAround(footX, headY, footZ);
        }
        rt.fluidSim().applyChange(headX, headY, headZ, AIR);
        refreshConnectionsAround(headX, headY, headZ);
        refreshStairsAround(headX, headY, headZ);
        broadcastWorldSound("block_break", headX, headY, headZ, (short) bedId);
        return true;
    }

    /**
     * [BED-COLOR] 수면·리스폰이 보는 침대 쌍 판정. 색은 보지 않되(총칭 32 와 851~866 이 모두
     * 침대다) <b>두 셀은 같은 색</b>이어야 한다 — 바닐라도 침대 한 채는 같은 블록 두 셀이다.
     */
    private boolean validBedPair(int headX, int y, int headZ) {
        int bedId = residentBlockType(rt.accessor(), headX, y, headZ);
        if (!Blocks.isBed(bedId)) return false;
        int headState = rt.blockStates().get(headX, y, headZ, bedId);
        if ((headState & BuildingBlockRules.BED_HEAD) == 0) return false;
        int footX = BuildingBlockRules.bedOtherX(headX, headState);
        int footZ = BuildingBlockRules.bedOtherZ(headZ, headState);
        if (residentBlockType(rt.accessor(), footX, y, footZ) != bedId) return false;
        int footState = rt.blockStates().get(footX, y, footZ, bedId);
        SupportRules.BlockLookup lookup =
                (x, by, z) -> residentBlockType(rt.accessor(), x, by, z);
        return BuildingBlockRules.matchingBedStates(headState, footState)
                && SupportRules.isSupported(bedId, headState, headX, y, headZ, lookup)
                && SupportRules.isSupported(bedId, footState, footX, y, footZ, lookup);
    }

    /**
     * [MAPNAV] 나침반이 가리킬 유효 리스폰 지점을 개인에게 보낸다. 좌표 규약은 한 곳뿐이다 —
     * 개인 침대가 있으면 언제나 <b>침대 머리 칸의 중심</b>, 없으면 월드 스폰. 입장(ConnectionEndpoint),
     * 침대 사용, 리스폰, 침대 파괴가 모두 이 규약을 공유해야 나침반 목표가 사건마다 흔들리지 않고
     * 정적판 {@code emitSpawnPoint} 와도 같은 값이 된다.
     *
     * @return 송신 큐가 받아들였으면 true. 거부되면 호출부가 {@link #spawnPointResend} 에 넣어 다음 틱에 재시도한다.
     */
    private boolean sendSpawnPoint(PlayerTickState player) {
        if (!rt.customDimension() && player.hasBedSpawn()) {
            return sendTo(player, new com.gameexpert.ws.dto.WsMessages.SpawnPoint(
                    player.bedSpawnX() + 0.5, player.bedSpawnY(), player.bedSpawnZ() + 0.5, true));
        }
        int[] worldSpawn = rt.worldSpawn();
        return sendTo(player, new com.gameexpert.ws.dto.WsMessages.SpawnPoint(
                worldSpawn[0], worldSpawn[1], worldSpawn[2], false));
    }

    /**
     * [MAPNAV] 침대가 부서지면 그 자리에서 개인 스폰을 해제하고 나침반을 월드 스폰으로 되돌린다.
     * 이전에는 해제가 리스폰 시점에만 일어나서, 침대를 부수고도 죽기 전까지 나침반이 사라진 침대를
     * 가리켰다.
     *
     * <p>머리 칸을 읽을 수 없으면(청크 미상주) 건드리지 않는다. 파괴는 상주 청크에서만 일어나므로,
     * 멀리 떠나 청크가 내려간 사이에 멀쩡한 침대를 무효화하는 일이 없다 — {@link #validBedPair} 는
     * 읽을 수 없는 칸을 "침대 아님"으로 보기 때문에 이 가드가 없으면 그렇게 된다(리스폰 경로는
     * 어차피 자기가 다시 검사한다). 파괴 경로마다 훅을 다는 대신 여기 한 곳에서 보므로 채굴·지지
     * 상실 연쇄·폭발이 모두 같은 결과가 된다.
     */
    private void refreshBedSpawnPoints() {
        if (rt.customDimension()) return; // root 침대를 자식 좌표의 AIR로 무효화하지 않는다.
        for (PlayerTickState player : rt.players().values()) {
            if (spawnPointResend.remove(player.nickname()) && !sendSpawnPoint(player)) {
                spawnPointResend.add(player.nickname());
            }
            if (!player.hasBedSpawn()) continue;
            int bedX = player.bedSpawnX();
            int bedY = player.bedSpawnY();
            int bedZ = player.bedSpawnZ();
            if (residentBlockType(rt.accessor(), bedX, bedY, bedZ) == UNAVAILABLE_BLOCK) continue;
            if (validBedPair(bedX, bedY, bedZ)) continue;
            player.clearBedSpawn();
            if (!sendSpawnPoint(player)) spawnPointResend.add(player.nickname());
        }
    }

    private boolean isBedOccupiedByOther(PlayerTickState player, int headX, int y, int headZ) {
        for (PlayerTickState other : rt.players().values()) {
            // [FURNITURE-26.3] 점유는 리스폰 지점이 아니라 **자고 있는 침대 칸**으로 본다 —
            // 건초 침대는 리스폰을 남기지 않아 bedSpawn 으로 보면 두 사람이 같은 칸에 눕는다.
            if (other != player
                    && other.sleeping()
                    && other.sleepBedX() == headX
                    && other.sleepBedY() == y
                    && other.sleepBedZ() == headZ) {
                return true;
            }
        }
        return false;
    }

    // ── ① consume: 선택 슬롯 음식 소비 → 허기·saturation 회복(SURV-H). 체력은 회복하지 않는다 ──
    private void applyConsume(PlayerAction.Consume consume, long tickNo) {
        PlayerTickState player = rt.players().get(consume.nickname());
        if (player == null || player.isDead()) {
            return;
        }
        if ("cancel".equals(consume.phase())) { player.cancelBandage(); return; }
        PlayerInventory.HandRef bandageHand = player.inventory().capture(inventoryHand(consume.hand()));
        if (player.inventory().stack(bandageHand).itemType() == Blocks.FLESH_BANDAGE) {
            if ("begin".equals(consume.phase())) player.beginBandage(bandageHand);
            else if ("finish".equals(consume.phase()) && player.requestBandageFinish(bandageHand)) {
                sendTo(player, inventoryMessage(player));
            }
            return;
        }
        if (!"finish".equals(consume.phase())) return;
        // 계약의 "32 game ticks 사용 뒤 소비"는 서버가 강제한다. 사용 애니메이션을 건너뛴
        // 연속 consume 은 무시하고, 정상 클라의 1.6초 주기는 그대로 통과한다.
        if (!player.canConsumeAt(tickNo)) {
            return;
        }
        PlayerInventory.HandRef hand = player.inventory().capture(inventoryHand(consume.hand()));
        short heldType = player.inventory().stack(hand).itemType();
        // 우유는 음식이 아니라 만복에서도 마시며, 모든 상태이상을 지우고 빈 양동이를 같은 칸에 돌려준다.
        if (heldType == PlayerInventory.MILK_BUCKET) {
            if (player.consumeMilk(hand)) {
                player.markConsumed(tickNo);
                sendTo(player, inventoryMessage(player));
                sendTo(player, new SoundEvent("consume_milk", PlayerInventory.MILK_BUCKET));
            }
            return;
        }
        if (heldType == PlayerInventory.OMINOUS_BOTTLE) {
            if (player.consumeOminousBottle(hand)) {
                player.markConsumed(tickNo);
                sendTo(player, inventoryMessage(player));
                // The existing drink confirmation drives the held-item animation and liquid sound;
                // itemType keeps the semantic source distinct from milk.
                sendTo(player, new SoundEvent("consume_milk", PlayerInventory.OMINOUS_BOTTLE));
            }
            return;
        }
        // [POTION] 물약은 음식이 아니라 만복에서도 마시며, 빈 유리병을 같은 칸에 돌려준다.
        if (PlayerInventory.isDrinkablePotion(heldType)) {
            short potion = heldType;
            if (player.drinkPotion(hand)) {
                player.markConsumed(tickNo);
                sendTo(player, inventoryMessage(player));
                sendTo(player, new SoundEvent("consume_milk", potion));
            }
            return;
        }
        // 황금사과는 바닐라 alwaysEdible 이라 만복에서도 사용할 수 있다(허기 회복은 소비 경로가 함께 처리).
        if (heldType == PlayerInventory.GOLDEN_APPLE) {
            if (player.consumeGoldenApple(hand)) {
                player.markConsumed(tickNo);
                sendTo(player, inventoryMessage(player));
                sendTo(player, new SoundEvent("consume_food", PlayerInventory.GOLDEN_APPLE));
            }
            return;
        }
        // [GOLD-FOOD] 마법이 부여된 황금 사과도 alwaysEdible 이다. 효과 넷은 소비 경로가 준다.
        if (heldType == PlayerInventory.ENCHANTED_GOLDEN_APPLE) {
            if (player.consumeEnchantedGoldenApple(hand)) {
                player.markConsumed(tickNo);
                sendTo(player, inventoryMessage(player));
                sendTo(player, new SoundEvent(
                        "consume_food", PlayerInventory.ENCHANTED_GOLDEN_APPLE));
            }
            return;
        }
        // [GOLD-FOOD] 꿀이 든 병은 음료지만 <b>허기를 채우는 유일한 음료</b>라 만복에서는
        // 마실 수 없다(바닐라 honey_bottle 은 alwaysEdible 이 아니다). 독만 해제하고 빈
        // 유리병을 돌려준다.
        if (heldType == PlayerInventory.HONEY_BOTTLE) {
            if (player.canEat() && player.consumeHoneyBottle(hand)) {
                player.markConsumed(tickNo);
                sendTo(player, inventoryMessage(player));
                sendTo(player, new SoundEvent("consume_milk", PlayerInventory.HONEY_BOTTLE));
            }
            return;
        }
        // [GOLD-FOOD] 후렴과도 alwaysEdible 이고, 먹으면 ±8 블록 안 안전 지점으로 순간이동한다.
        // 굴림은 좌표·틱만으로 정해지는 ChorusFruitRules 가 소유해 정적판과 결과가 같다.
        if (heldType == PlayerInventory.CHORUS_FRUIT) {
            if (InventoryRules.consumeOne(player.inventory(), hand, PlayerInventory.CHORUS_FRUIT)) {
                player.eatChorusFruit();
                player.markConsumed(tickNo);
                sendTo(player, inventoryMessage(player));
                sendTo(player, new SoundEvent("consume_food", PlayerInventory.CHORUS_FRUIT));
                int feetX = (int) Math.floor(player.x());
                int feetY = (int) Math.floor(player.y());
                int feetZ = (int) Math.floor(player.z());
                ChorusFruitRules.Destination target = ChorusFruitRules.roll(
                        ChorusFruitRules.seed(tickNo, feetX, feetY, feetZ), feetX, feetY, feetZ,
                        (x, y, z) -> residentBlockType(rt.accessor(), x, y, z));
                if (target.found()) {
                    double tx = target.x() + 0.5;
                    double ty = target.y();
                    double tz = target.z() + 0.5;
                    var teleportFrom = new com.gameexpert.ws.dto.WsMessages.PositionDto(player.x(), player.y(), player.z());
                    player.applyPose(tx, ty, tz, player.yaw(), player.pitch(),
                            player.crouching(), false);
                    sendTo(player, new PlayerTeleportSelf(tx, ty, tz, teleportFrom));
                    PlayerRelocation.publish(rt, player);
                    var trail = new com.gameexpert.ws.dto.WsMessages.TeleportParticles(teleportFrom,
                            new com.gameexpert.ws.dto.WsMessages.PositionDto(tx, ty, tz));
                    for (PlayerTickState observer : rt.players().values()) {
                        if (observer != player && (SoundRules.audible(teleportFrom.getX(), teleportFrom.getY(),
                                teleportFrom.getZ(), observer.x(), observer.y(), observer.z(), 32)
                                || SoundRules.audible(tx, ty, tz, observer.x(), observer.y(), observer.z(), 32))) {
                            sendTo(observer, trail);
                        }
                    }
                }
            }
            return;
        }
        // 바닐라 canEat: 만복이면 일반 음식은 소비 자체가 성립하지 않는다.
        if (!player.canEat()) {
            return;
        }
        InventoryRules.ConsumedFood consumed = InventoryRules.consumeFood(player.inventory(), hand);
        if (consumed == null) {
            return; // 음식이 아니면 무시
        }
        // healthUpdate{cause:"hunger"} 는 ⑤ broadcastHealth 에서 개인 송신한다.
        player.markConsumed(tickNo);
        player.eat(consumed.nutrition(), consumed.saturationMilli());
        // [COOKING] 수상한 스튜는 허기 회복 뒤 꽃마다 정해진 효과를 얹는다. 지속은 바닐라
        // MC 틱 그대로라 서버 틱으로 나누지 않는다(민들레 7틱은 3.5 서버 틱이 된다).
        var stewEffect = SuspiciousStewRules.effectOf(consumed.stack());
        if (stewEffect != null) {
            player.applyStatusEffectMcTicks(stewEffect, SuspiciousStewRules.AMPLIFIER,
                    SuspiciousStewRules.effectMcTicks(consumed.stack()));
        }
        // [CROP-BERRY] 바닐라 FoodProperties 의 effect(…, probability) 항. 위 수상한 스튜는
        // **확정** 효과라 별도 경로지만, 이쪽은 확률이 붙는 일반 계약이라 표가 따로 있다.
        // 확률·지속·앰프 정본은 InventoryRules.foodSideEffect 다(정적판 items.ts 짝). 굴림은
        // 퇴비 선례처럼 순수 함수에 주입해 두 권위가 같은 표면을 대조받는다. 굴림은 &&
        // 단락으로 **부작용이 있는 음식에서만** 소비한다 — 표에 없는 음식까지 난수를 당기면
        // 이 난수열의 위상이 식사마다 밀린다.
        InventoryRules.FoodSideEffect sideEffect =
                InventoryRules.foodSideEffect(consumed.itemType());
        if (sideEffect != null
                && InventoryRules.foodSideEffectApplies(
                        sideEffect, foodEffectRandom.nextDouble())) {
            player.applyStatusEffect(sideEffect.effect(), sideEffect.amplifier(),
                    sideEffect.durationTicks());
        }
        sendTo(player, inventoryMessage(player));
        sendTo(player, new SoundEvent("consume_food", consumed.itemType()));
    }

    // ── ① selectSlot: 선택 슬롯 추적 후 개인 인벤토리 갱신 ──
    private void applySelectSlot(PlayerAction.SelectSlot select) {
        PlayerTickState player = rt.players().get(select.nickname());
        if (player == null) {
            return;
        }
        long beforeRevision = player.inventory().revision();
        int beforeSlot = player.inventory().selectedSlot();
        player.inventory().select(select.slot());
        if (player.inventory().selectedSlot() != beforeSlot) {
            // 바닐라 handleSetCarriedItem: 칸이 실제로 바뀌고 주손을 쓰는 중이면 stopUsingItem.
            // 들어 올린 주손 방패와 활·삼지창·석궁 당김이 새 칸으로 넘어가지 않는다.
            player.cancelMainHandUse();
            rt.mobSystem().stopMainHandUse(player.nickname());
        }
        if (player.inventory().revision() != beforeRevision) {
            rt.queuePlayerInventoryBaseline(player);
        }
        sendTo(player, inventoryMessage(player));
        sendHeldMapStates(player);
    }

    private void applySwapHands(PlayerAction.SwapHands swap) {
        PlayerTickState player = rt.players().get(swap.nickname());
        if (player == null || player.isDead()) return;
        if (!player.inventory().swapSelectedWithOffhand()) {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error("MOVE_REJECTED"));
            return;
        }
        cancelActiveHandUse(player);
        sendTo(player, inventoryMessage(player));
        sendHeldMapStates(player);
    }

    private void applySwapOffhand(PlayerAction.SwapOffhand swap) {
        PlayerTickState player = rt.players().get(swap.nickname());
        if (player == null || player.isDead()) return;
        if (swap.slot() < 0 || swap.slot() >= PlayerInventory.SLOTS) {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error("MOVE_REJECTED"));
            return;
        }
        if (!player.inventory().swapSlotWithOffhand(swap.slot())) {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error("MOVE_REJECTED"));
            return;
        }
        cancelActiveHandUse(player);
        sendTo(player, inventoryMessage(player));
        sendHeldMapStates(player);
    }

    private void cancelActiveHandUse(PlayerTickState player) {
        player.cancelHandUse();
        rt.mobSystem().clearPlayerUseState(player.nickname());
    }

    private void applyMapUse(PlayerAction.MapUse use) {
        PlayerTickState player = rt.players().get(use.nickname());
        if (player == null || !pendingMapUses.add(use.nickname())) return;
        PlayerInventory inventory = player.inventory();
        PlayerInventory.CompletePersistenceSnapshot leased = null;
        com.gameexpert.ground.dto.GroundMutationCommand groundCommand = null;
        boolean groundLane = false;
        boolean retained = false;
        try {
            PlayerInventory.HandRef source = inventory.capture(inventoryHand(use.hand()));
            PlayerInventory.StackSnapshot sourceStack = inventory.stack(source);
            if (sourceStack.itemType() != PlayerInventory.MAP || sourceStack.count() <= 0) return;
            var service = rt.emptyMapSettlements();
            leased = inventory.acquireSettlementLease();
            if (service == null || leased == null) return;

            int centerX = WorldMapRuntime.center(player.x());
            int centerZ = WorldMapRuntime.center(player.z());
            WorldMapData sampled = initializeMapRaster(new WorldMapData(
                    rt.worldId(), Integer.MAX_VALUE, centerX, centerZ, 0, false,
                    new byte[WorldMapData.COLOR_COUNT], 0), player.x(), player.z());
            byte[] initialColors = sampled.getColors();
            AllocationRequest allocation = EmptyMapSettlementCommand.allocationRequestForNewUse(
                    player.playerId(), rt.worldId(), leased, source,
                    centerX, centerZ, initialColors);
            AllocationReservation reservation = service.reserveMapId(allocation);
            WorldMapData prepared = new WorldMapData(rt.worldId(), reservation.mapId(),
                    centerX, centerZ, 0, false, initialColors, 0);

            PlayerInventory planned = leased.detachedInventory();
            PlayerInventory.MapUseResult result = planned.completeMapUse(
                    source.hand(), reservation.mapId());
            if (!result.completed()) return;
            PlayerInventory.CompletePersistenceSnapshot committed =
                    planned.completePersistenceSnapshot();
            com.gameexpert.ground.dto.GroundItemSnapshot overflowGround = null;
            if (result.overflow() != null) {
                long entityId = rt.itemSystem().reserveSettlementEntityId();
                overflowGround = rt.itemSystem().settlementDeathDropSnapshot(entityId,
                        result.overflow(), player.x(), player.y(), player.z());
                long expectedGround = rt.groundRevision();
                groundCommand = new com.gameexpert.ground.dto.GroundMutationCommand(
                        WorldRuntime.stableGroundMutationId(entityId, 3),
                        com.gameexpert.ground.dto.GroundMutationCommand.Kind.BLOCK_DROP,
                        rt.worldId(), expectedGround, expectedGround + 1, null, null,
                        List.of(overflowGround), List.of(), List.of(), List.of());
                groundLane = rt.beginGroundSettlement();
                if (!groundLane) return;
            }
            PlayerInventoryMutationSnapshot playerSnapshot =
                    rt.playerInventoryMutationSnapshot(player, committed);
            EmptyMapSettlementCommand command = new EmptyMapSettlementCommand(
                    reservation, leased, source, committed, playerSnapshot,
                    centerX, centerZ, initialColors,
                    overflowGround == null ? List.of() : List.of(overflowGround), groundCommand);
            PendingEmptyMapSettlement pending = new PendingEmptyMapSettlement(
                    player, leased, committed, command, prepared, groundCommand, overflowGround);
            pendingEmptyMapSettlements.put(use.nickname(), pending);
            retained = true;
            rt.queuePlayerInventoryBaseline(player);
            submitEmptyMapSettlement(use.nickname(), pending);
        } finally {
            if (!retained) {
                if (groundLane && groundCommand != null) {
                    rt.completeGroundSettlement(groundCommand.committedGroundRevision(), false);
                }
                if (leased != null) inventory.releaseSettlementLease(leased);
                pendingMapUses.remove(use.nickname());
            }
        }
    }

    private void pumpEmptyMapSettlements() {
        if (rt.emptyMapSettlements() == null) return;
        for (Map.Entry<String, PendingEmptyMapSettlement> entry
                : List.copyOf(pendingEmptyMapSettlements.entrySet())) {
            PendingEmptyMapSettlement pending = entry.getValue();
            if (!pending.inFlight && !pending.reconciliationRequired) {
                submitEmptyMapSettlement(entry.getKey(), pending);
            }
        }
    }

    private void submitEmptyMapSettlement(
            String nickname, PendingEmptyMapSettlement pending) {
        if (pending.inFlight || pending.reconciliationRequired
                || pendingEmptyMapSettlements.get(nickname) != pending) return;
        var service = rt.emptyMapSettlements();
        if (service == null) return;
        pending.inFlight = true;
        Runnable task = () -> {
            CartographySettlementOutcome outcome;
            try {
                outcome = switch (service.settle(pending.command)) {
                    case COMMITTED -> CartographySettlementOutcome.COMMITTED;
                    case IDEMPOTENT -> CartographySettlementOutcome.IDEMPOTENT;
                    case STALE -> CartographySettlementOutcome.REJECTED;
                };
            } catch (RuntimeException | Error unknown) {
                outcome = CartographySettlementOutcome.UNKNOWN;
            }
            CartographySettlementOutcome resolved = outcome;
            rt.enqueuePersistenceCompletion(() -> completeEmptyMapSettlement(
                    nickname, pending, resolved));
        };
        PersistenceExecutor persistence = rt.ctx().persistenceExecutor();
        if (persistence == null) {
            task.run();
            return;
        }
        try {
            if (!persistence.trySubmit(task)) pending.inFlight = false;
        } catch (RuntimeException | Error unavailable) {
            // Submission itself did not yield a terminal answer; retain the frozen command.
            pending.inFlight = false;
        }
    }

    private void completeEmptyMapSettlement(String nickname,
            PendingEmptyMapSettlement pending, CartographySettlementOutcome outcome) {
        if (pendingEmptyMapSettlements.get(nickname) != pending) return;
        pending.inFlight = false;
        if (outcome == CartographySettlementOutcome.UNKNOWN) return;
        if (outcome == CartographySettlementOutcome.REJECTED) {
            if (pending.ownsGroundLane() && !pending.groundLaneCompleted) {
                rt.completeGroundSettlement(
                        pending.groundCommand.committedGroundRevision(), false);
                pending.groundLaneCompleted = true;
            }
            if (!pending.player.inventory().releaseSettlementLease(pending.sourceInventory)) {
                pending.reconciliationRequired = true;
                return;
            }
            pendingEmptyMapSettlements.remove(nickname);
            pendingMapUses.remove(nickname);
            return;
        }
        try {
            WorldMapData installed = rt.mapData(pending.map.getMapId());
            if (installed != null && !WorldMapRuntime.sameDurableMap(installed, pending.map)) {
                pending.reconciliationRequired = true;
                log.error("월드 {} 빈 지도 정산 설치 충돌: nickname={} settlement={}",
                        rt.worldId(), nickname, pending.command.getSettlementId());
                return;
            }
            rt.acceptSettledMap(pending.map);
            if (pending.ownsGroundLane() && !pending.groundLaneCompleted) {
                rt.completeGroundSettlement(
                        pending.groundCommand.committedGroundRevision(), true);
                pending.groundLaneCompleted = true;
            }
            if (pending.dropped != null) rt.itemSystem().commitSettlementDrop(pending.dropped);
            if (!pending.player.inventory().installCommittedSettlement(
                            pending.sourceInventory, pending.committedInventory)) {
                pending.reconciliationRequired = true;
                return;
            }
        } catch (RuntimeException collision) {
            pending.reconciliationRequired = true;
            log.error("월드 {} 빈 지도 정산 설치 충돌: nickname={} settlement={}",
                    rt.worldId(), nickname, pending.command.getSettlementId(), collision);
            return;
        }
        pendingEmptyMapSettlements.remove(nickname);
        pendingMapUses.remove(nickname);
        if (rt.players().get(nickname) == pending.player) {
            sendTo(pending.player, inventoryMessage(pending.player));
            sendTo(pending.player, mapStateMessage(pending.map));
        }
    }

    /** 생성 순간 상주 중인 플레이어 주변 17×17을 채워 정상 사용이 빈 양피지로 시작하지 않게 합니다. */
    private WorldMapData initializeMapRaster(WorldMapData draft, double playerX, double playerZ) {
        return WorldMapColorSampler.initialize(draft, playerX, playerZ,
                INITIAL_MAP_SAMPLE_RADIUS, mapColumns);
    }

    /** 선택된 각 mapId를 한 번만 샘플링하고, DB 대기 중 변경은 다음 revision으로 합칩니다. */
    private void updateSelectedMaps(long tickNo) {
        Map<Integer, PlayerTickState> selected = new LinkedHashMap<>();
        for (PlayerTickState player : rt.players().values()) {
            PlayerInventory inventory = player.inventory();
            PlayerInventory.StackSnapshot main = inventory.stack(PlayerInventory.Hand.MAIN);
            PlayerInventory.StackSnapshot offhand = inventory.stack(PlayerInventory.Hand.OFFHAND);
            if (PlayerInventory.isFilledMapItem(main.itemType()) && main.mapId() > 0) {
                selected.putIfAbsent(main.mapId(), player);
            }
            if (PlayerInventory.isFilledMapItem(offhand.itemType()) && offhand.mapId() > 0) {
                selected.putIfAbsent(offhand.mapId(), player);
            }
        }

        for (Map.Entry<Integer, PlayerTickState> entry : selected.entrySet()) {
            int mapId = entry.getKey();
            WorldMapData map = rt.mapState(mapId);
            if (map == null || map.isLocked()) continue;
            byte[] working = rt.mapWorkingColors(mapId);
            if (working == null) continue;
            int pixelX = mapScanColumns.computeIfAbsent(mapId,
                    ignored -> WorldMapColorSampler.pixel(
                            map.getCenterX(), entry.getValue().x()));
            byte[] column = WorldMapColorSampler.sampleColumn(map, working, pixelX,
                    0, WorldMapData.SIZE - 1, mapColumns);
            if (rt.applyMapSample(mapId, pixelX, 0, 1, WorldMapData.SIZE, column)) {
                mapsAwaitingFlush.add(mapId);
            }
            mapScanColumns.put(mapId, (pixelX + 1) % WorldMapData.SIZE);
        }

        if (tickNo % MAP_PATCH_FLUSH_INTERVAL != 0) return;
        for (int mapId : List.copyOf(mapsAwaitingFlush)) {
            WorldMapRuntime.PendingPatch patch = rt.prepareMapPatch(mapId);
            if (patch == null) {
                if (!rt.mapNeedsPersistence(mapId)) mapsAwaitingFlush.remove(mapId);
                continue;
            }
            rt.persistMapPatch(patch, durablePatch -> {
                broadcastMapPatch(durablePatch);
                if (!rt.mapNeedsPersistence(mapId)) mapsAwaitingFlush.remove(mapId);
            }, () -> {
                // rejectPatch가 dirty 사각형을 복원하므로 다음 flush에서 같은 권위 픽셀을 재시도합니다.
            });
        }
    }

    private void broadcastMapPatch(WorldMapRuntime.PendingPatch patch) {
        MapPatch message = new MapPatch(patch.candidate().getMapId(),
                patch.candidate().getRevision(), patch.x(), patch.z(),
                patch.width(), patch.height(),
                Base64.getEncoder().encodeToString(patch.colors()));
        for (PlayerTickState player : rt.players().values()) {
            PlayerInventory inventory = player.inventory();
            PlayerInventory.StackSnapshot main = inventory.stack(PlayerInventory.Hand.MAIN);
            PlayerInventory.StackSnapshot offhand = inventory.stack(PlayerInventory.Hand.OFFHAND);
            int mapId = patch.candidate().getMapId();
            if (PlayerInventory.isFilledMapItem(main.itemType()) && main.mapId() == mapId
                    || PlayerInventory.isFilledMapItem(offhand.itemType())
                            && offhand.mapId() == mapId) {
                sendTo(player, message);
            }
        }
    }

    private void sendHeldMapStates(PlayerTickState player) {
        PlayerInventory inventory = player.inventory();
        PlayerInventory.StackSnapshot main = inventory.stack(PlayerInventory.Hand.MAIN);
        PlayerInventory.StackSnapshot offhand = inventory.stack(PlayerInventory.Hand.OFFHAND);
        int mainMap = PlayerInventory.isFilledMapItem(main.itemType()) ? main.mapId() : 0;
        int offhandMap = PlayerInventory.isFilledMapItem(offhand.itemType()) ? offhand.mapId() : 0;
        if (mainMap > 0) {
            MapState state = mapStateMessage(rt.mapState(mainMap));
            if (state != null) sendTo(player, state);
        }
        if (offhandMap > 0 && offhandMap != mainMap) {
            MapState state = mapStateMessage(rt.mapState(offhandMap));
            if (state != null) sendTo(player, state);
        }
    }

    private static PlayerInventory.Hand inventoryHand(PlayerAction.Hand hand) {
        if (hand == null) throw new IllegalArgumentException("hand is required");
        return hand == PlayerAction.Hand.OFFHAND
                ? PlayerInventory.Hand.OFFHAND : PlayerInventory.Hand.MAIN;
    }

    static MapState mapStateMessage(WorldMapData map) {
        return map == null ? null : new MapState(
                map.getMapId(), map.getCenterX(), map.getCenterZ(), map.getScale(), map.isLocked(),
                map.getRevision(),
                Base64.getEncoder().encodeToString(map.getColors()), map.getTargetMarker());
    }

    // 방패 입력은 클라이언트의 pressed 값을 그대로 반영하고, 피해 차단 여부만 서버가 판정한다.
    private void applyShieldBlock(PlayerAction.ShieldBlock shield) {
        PlayerTickState player = rt.players().get(shield.nickname());
        if (player == null || player.isDead()) return;
        player.setShieldInput(shield.pressed(),
                player.inventory().capture(inventoryHand(shield.hand())), System.nanoTime());
    }

    private void applyEquipArmor(PlayerAction.EquipArmor equip) {
        PlayerTickState player = rt.players().get(equip.nickname());
        if (player == null || player.isDead()) return;
        PlayerInventory.HandRef hand = player.inventory().capture(inventoryHand(equip.hand()));
        short itemType = player.inventory().stack(hand).itemType();
        boolean equipped = player.inventory().equip(hand);
        if (!equipped) return;
        sendTo(player, inventoryMessage(player));
        sendTo(player, new SoundEvent("armor_equip", itemType));
    }

    private void applyMoveArmor(PlayerAction.MoveArmor move) {
        PlayerTickState player = rt.players().get(move.nickname());
        if (player == null || player.isDead()) return;
        if (player.inventory().moveArmor(move.inventorySlot(), move.armorSlot())) {
            sendTo(player, inventoryMessage(player));
        } else {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error("MOVE_REJECTED"));
        }
    }

    // ── ① moveSlot: 두 슬롯 swap(0~35). 유효하면 갱신, 아니면 거부 ──
    private void applyMoveSlot(PlayerAction.MoveSlot moveSlot) {
        PlayerTickState player = rt.players().get(moveSlot.nickname());
        if (player == null) {
            return;
        }
        if (player.inventory().settlementLeased()) {
            enqueuePlayerContainerAction(player, () -> {
                if (rt.players().get(moveSlot.nickname()) == player) applyMoveSlot(moveSlot);
            });
            return;
        }
        if (player.inventory().moveSlot(moveSlot.from(), moveSlot.to())) {
            sendTo(player, inventoryMessage(player));
        } else {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error("MOVE_REJECTED"));
        }
    }

    // ── ① dropItem: 서버가 실제 슬롯/수량을 검증하고 남은 내구도 그대로 월드 엔티티로 옮긴다 ──
    private static final class ItemDropPose {
        private final double x, y, z;
        private final float yaw, pitch;
        private final boolean crouching;

        private ItemDropPose(PlayerTickState player) {
            x = player.x(); y = player.y(); z = player.z();
            yaw = player.yaw(); pitch = player.pitch(); crouching = player.crouching();
        }
    }

    private void applyDropItem(PlayerAction.DropItem drop) {
        PlayerTickState player = rt.players().get(drop.nickname());
        if (player == null || player.isDead()) return;
        applyDropItem(drop, player, new ItemDropPose(player));
    }

    private void applyDropItem(PlayerAction.DropItem drop, PlayerTickState player, ItemDropPose pose) {
        if (rt.players().get(drop.nickname()) != player || player.isDead()) return;
        dropAudit.debug("phase=apply world={} nickname={} slot={} count={} playerState={}",
                rt.worldId(), drop.nickname(), drop.slot(), drop.count(),
                player == null ? "missing" : player.isDead() ? "dead" : "live");
        PlayerInventory inventory = player.inventory();
        PlayerInventory.CompletePersistenceSnapshot source = inventory.acquireSettlementLease();
        if (dropAudit.isDebugEnabled()) {
            dropAudit.debug("phase=lease world={} nickname={} slot={} count={} acquired={} revision={} leased={}",
                    rt.worldId(), drop.nickname(), drop.slot(), drop.count(), source != null,
                    inventory.revision(), inventory.settlementLeased());
        }
        if (source == null) {
            enqueuePlayerContainerAction(player, () -> applyDropItem(drop, player, pose));
            return;
        }
        PlayerInventory planned = source.detachedInventory();
        PlayerInventory.DroppedStack stack = planned.dropFromSlot(drop.slot(), drop.count());
        dropAudit.debug("phase=plan world={} nickname={} slot={} count={} valid={} sourceRevision={}",
                rt.worldId(), drop.nickname(), drop.slot(), drop.count(), stack != null, source.revision());
        if (stack == null) {
            inventory.releaseSettlementLease(source);
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error(
                    "DROP_REJECTED"));
            return;
        }
        PlayerInventory.CompletePersistenceSnapshot committed =
                planned.completePersistenceSnapshot();
        // Keep the inventory lease and the input-edge pose while the shared ground writer is busy.
        // Reserve an entity ID only when that lane is available: older pending spawns own the
        // durable high-water order and must settle first.
        Runnable submit = new Runnable() {
            @Override
            public void run() {
                if (rt.players().get(drop.nickname()) != player || player.isDead()) {
                    inventory.releaseSettlementLease(source);
                    return;
                }
                if (rt.groundMutationSettlements() != null && !rt.groundSettlementAvailable()) {
                    if (!enqueuePlayerContainerAction(player, this)) inventory.releaseSettlementLease(source);
                    return;
                }
                long entityId = rt.itemSystem().reserveSettlementEntityId();
                var ground = rt.itemSystem().settlementThrownDropSnapshot(entityId, stack,
                        pose.x, pose.y, pose.z, pose.crouching, pose.yaw, pose.pitch);
                Runnable rejected = () -> {
                    dropAudit.debug("phase=inventory-rejected world={} nickname={} slot={} count={} entityId={} sourceRevision={}",
                            rt.worldId(), drop.nickname(), drop.slot(), drop.count(), entityId, source.revision());
                    inventory.releaseSettlementLease(source);
                };
                Runnable installed = () -> {
                    rt.itemSystem().commitSettlementDrop(ground);
                    if (rt.players().get(player.nickname()) == player
                            && inventory.installCommittedSettlement(source, committed)) {
                        dropAudit.debug("phase=inventory-installed world={} nickname={} slot={} count={} entityId={} committedRevision={}",
                                rt.worldId(), drop.nickname(), drop.slot(), drop.count(), entityId, committed.revision());
                        sendTo(player, inventoryMessage(player));
                    } else {
                        rejected.run();
                    }
                };
                if (rt.groundMutationSettlements() == null) {
                    dropAudit.debug("phase=inline-no-ground-service world={} nickname={} slot={} count={} entityId={}",
                            rt.worldId(), drop.nickname(), drop.slot(), drop.count(), entityId);
                    installed.run();
                    return;
                }
                long expectedGroundRevision = rt.groundRevision();
                var command = new com.gameexpert.ground.dto.GroundMutationCommand(
                        WorldRuntime.stableGroundMutationId(entityId, 3),
                        com.gameexpert.ground.dto.GroundMutationCommand.Kind.PLAYER_DROP,
                        rt.worldId(), expectedGroundRevision, expectedGroundRevision + 1,
                        source.revision(), rt.playerInventoryMutationSnapshot(player, committed),
                        List.of(ground), List.of(), List.of(), List.of());
                dropAudit.debug("phase=submit world={} nickname={} slot={} count={} mutationId={} entityId={} sourceRevision={} groundRevision={}",
                        rt.worldId(), drop.nickname(), drop.slot(), drop.count(), command.mutationId(),
                        entityId, source.revision(), expectedGroundRevision);
                if (!rt.submitInventoryDropSettlement(command, installed, rejected)) rejected.run();
            }
        };
        submit.run();
    }

    private void applyOpenCrafting(PlayerAction.OpenCrafting open) {
        PlayerTickState player = rt.players().get(open.nickname());
        if (player == null) return;
        player.beginCraftingOpen(open);
        applyOpenCrafting(player, open);
    }

    private void applyOpenCrafting(PlayerTickState player, PlayerAction.OpenCrafting open) {
        if (rt.players().get(open.nickname()) != player || player.isDead()
                || !player.pendingCraftingOpenMatches(open)) return;
        if (player.inventory().settlementLeased()) {
            if (!enqueuePlayerContainerAction(player, () -> applyOpenCrafting(player, open))) {
                player.cancelPendingCraftingOpen();
            }
            return;
        }
        player.cancelPendingCraftingOpen();
        if (open.station() != PlayerAction.CraftStation.INVENTORY
                && (!craftingStationMatches(open.station(),
                        residentBlockType(rt.accessor(), open.x(), open.y(), open.z()))
                || !InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                        open.x(), open.y(), open.z()))) {
            sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error("INTERACT_REJECTED"));
            return;
        }
        if (open.station() == PlayerAction.CraftStation.BREWING) {
            BrewingStorage storage = rt.brewingStorage();
            if (!storage.acquireLease(open.x(), open.y(), open.z(), player.nickname())) {
                sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error("CONTAINER_IN_USE"));
                return;
            }
            closeActiveMenuBeforeOpen(player);
            if (!storage.acquireLease(open.x(), open.y(), open.z(), player.nickname())) {
                throw new IllegalStateException("validated brewing lease was lost during menu transition");
            }
            player.openCrafting(open.station(), open.x(), open.y(), open.z(), open.requestId());
            // [END-CITY] 엔드 배 양조기처럼 콘텐츠가 선언한 초기 내용물은 첫 열기에 한 번 채운다.
            rt.materializeDimensionBrewingStand(open.x(), open.y(), open.z());
            BrewingInventory brewing = storage.openAt(open.x(), open.y(), open.z());
            if (brewing.needsTick()) storage.activate(open.x(), open.y(), open.z());
            sendTo(player, brewingOpen(player, open.x(), open.y(), open.z(), brewing));
            return;
        }
        // 절단기는 입력 한 칸, 대장장이 작업대는 template/base/addition 세 칸만 사용한다.
        closeActiveMenuBeforeOpen(player);
        dropCraftingOverflow(player, player.inventory().openCrafting(
                open.station() == PlayerAction.CraftStation.INVENTORY
                        || open.station() == PlayerAction.CraftStation.STONECUTTER ? 2 : 3,
                switch (open.station()) {
                    case ANVIL, CARTOGRAPHY, GRINDSTONE -> 2;
                    case LOOM, SMITHING -> 3;
                    case STONECUTTER, BEACON -> 1;
                    case INVENTORY -> 4;
                    case BREWING -> throw new IllegalStateException("brewing is coordinate-owned");
                    default -> 9;
                },
                open.station() == PlayerAction.CraftStation.STONECUTTER));
        player.openCrafting(open.station(), open.x(), open.y(), open.z(), open.requestId());
        sendTo(player, craftingMessage(player, false));
    }

    private void applyCraftingClick(PlayerAction.CraftingClick click) {
        PlayerTickState player = rt.players().get(click.nickname());
        if (!validCraftingSession(player)) return;
        if ((click.area() == PlayerInventory.CraftArea.ARMOR
                || click.area() == PlayerInventory.CraftArea.OFFHAND)
                && player.openCraftingStation() != PlayerAction.CraftStation.INVENTORY) {
            sendTo(player, craftingMessage(player, false));
            return;
        }
        if (player.openCraftingStation() == PlayerAction.CraftStation.BEACON) {
            // [BEACON] 결제 칸(격자 0)과 인벤토리 shift 만 신호기 규칙을 탄다. 결과 칸은 없다.
            boolean changed = switch (click.area()) {
                case GRID -> click.slot() == 0
                        && player.inventory().clickBeaconPayment(click.button(), click.shift());
                case INVENTORY -> click.shift()
                        ? player.inventory().quickMoveToBeaconPayment(click.slot())
                        : player.inventory().clickCrafting(click.area(), click.slot(),
                                click.button(), false).changed();
                default -> false;
            };
            if (changed || click.area() == PlayerInventory.CraftArea.GRID) {
                sendTo(player, craftingMessage(player, false));
            }
            return;
        }
        if (player.openCraftingStation() == PlayerAction.CraftStation.SMITHING) {
            if (click.area() == PlayerInventory.CraftArea.RESULT) {
                boolean crafted = player.inventory().takeSmithingResult(click.shift());
                sendTo(player, craftingMessage(player, crafted));
                return;
            }
            if (click.area() == PlayerInventory.CraftArea.GRID && click.slot() > 2) {
                sendTo(player, craftingMessage(player, false));
                return;
            }
            // [ARMOR-TRIM] 인벤토리 shift 는 ItemCombinerMenu.quickMoveStack(입력 칸 우선)이다.
            if (click.area() == PlayerInventory.CraftArea.INVENTORY && click.shift()) {
                player.inventory().quickMoveToSmithing(click.slot());
                sendTo(player, craftingMessage(player, false));
                return;
            }
        }
        if (click.area() == PlayerInventory.CraftArea.RESULT) {
            boolean crafted = switch (player.openCraftingStation()) {
                case ANVIL -> takeAnvilResult(player, click.shift());
                case CARTOGRAPHY -> takeCartographyResult(player, click.shift());
                case GRINDSTONE -> takeGrindstoneResult(player, click.shift());
                case LOOM -> takeLoomResult(player, click.shift());
                default -> false;
            };
            if (player.openCraftingStation() == PlayerAction.CraftStation.ANVIL
                    || player.openCraftingStation() == PlayerAction.CraftStation.GRINDSTONE
                    || player.openCraftingStation() == PlayerAction.CraftStation.LOOM
                    || player.openCraftingStation() == PlayerAction.CraftStation.CARTOGRAPHY) {
                // Cartography's true return means its immutable settlement was accepted. The
                // owner-visible craft signal is emitted only by the COMMITTED/IDEMPOTENT callback.
                boolean visibleCraft = player.openCraftingStation()
                        != PlayerAction.CraftStation.CARTOGRAPHY && crafted;
                sendTo(player, craftingMessage(player, visibleCraft));
                return;
            }
        }
        PlayerInventory.CraftClickResult result = player.inventory().clickCrafting(
                click.area(), click.slot(), click.button(), click.shift());
        if (result.changed()) sendTo(player, craftingMessage(player, result.crafted()));
    }

    private void applyCraftingDrag(PlayerAction.CraftingDrag drag) {
        PlayerTickState player = rt.players().get(drag.nickname());
        if (!validCraftingSession(player)) return;
        if (drag.areas() == null || drag.slots() == null
                || drag.areas().length != drag.slots().length) return;
        if (player.openCraftingStation() != PlayerAction.CraftStation.INVENTORY) {
            for (PlayerInventory.CraftArea area : drag.areas()) {
                if (area == PlayerInventory.CraftArea.ARMOR
                        || area == PlayerInventory.CraftArea.OFFHAND) {
                    sendTo(player, craftingMessage(player, false));
                    return;
                }
            }
        }
        if (player.openCraftingStation() == PlayerAction.CraftStation.BEACON
                && applyBeaconPaymentDrag(player, drag)) {
            return;
        }
        if (player.openCraftingStation() == PlayerAction.CraftStation.SMITHING) {
            for (int index = 0; index < drag.areas().length; index++) {
                if (drag.areas()[index] == PlayerInventory.CraftArea.GRID
                        && drag.slots()[index] > 2) {
                    sendTo(player, craftingMessage(player, false));
                    return;
                }
            }
        }
        if (player.inventory().dragCrafting(drag.areas(), drag.slots(), drag.button())) {
            sendTo(player, craftingMessage(player, false));
        }
    }

    private void applyPlaceCraftingRecipe(PlayerAction.PlaceCraftingRecipe place) {
        PlayerTickState player = rt.players().get(place.nickname());
        if (!validCraftingSession(player)) return;
        if (isSpecialWorkstation(player.openCraftingStation())) {
            sendTo(player, craftingMessage(player, false));
            return;
        }
        player.inventory().placeCraftingRecipe(place.recipeId(), place.maximum());
        sendTo(player, craftingMessage(player, false));
    }

    /**
     * [STONECUT] 절단 산출 선택. 권위가 {@code CraftRecipe.matchStonecutting} 으로 다시
     * 검증하므로, 입력에서 유도되지 않는 산출을 요청하면 세션 상태가 바뀌지 않는다.
     */
    private void applySelectStonecutterRecipe(PlayerAction.SelectStonecutterRecipe select) {
        PlayerTickState player = rt.players().get(select.nickname());
        if (!validCraftingSession(player)) return;
        if (player.inventory().selectStonecutterRecipe(select.recipeId())) {
            sendTo(player, craftingMessage(player, false));
        }
    }

    private void applyAnvilRename(PlayerAction.AnvilRename rename) {
        PlayerTickState player = rt.players().get(rename.nickname());
        if (!validCraftingSession(player)
                || player.openCraftingStation() != PlayerAction.CraftStation.ANVIL) return;
        try {
            // The pure planner owns the exact UTF-16/name legality; an invalid name is rejected.
            if (player.inventory().planAnvilResult(rename.name(), false) == null
                    && !rename.name().isEmpty()) return;
            player.anvilRename(rename.name().isEmpty() ? null : rename.name());
            sendTo(player, craftingMessage(player, false));
        } catch (IllegalArgumentException invalid) {
            sendTo(player, craftingMessage(player, false));
        }
    }

    /**
     * [BEACON] 결제 칸이 끼인 드래그. 결제 칸 하나뿐이면 그 칸 클릭과 같고(최대 1 개), 여러 칸이면 결제
     * 칸을 빼고 나머지 칸에만 분배한다 — 결제 칸은 한 개만 받으므로 분배 몫이 늘 1 로 잘리는 칸이다.
     *
     * @return 이 드래그를 처리했으면 true(호출자는 일반 드래그로 넘기지 않는다)
     */
    private boolean applyBeaconPaymentDrag(PlayerTickState player, PlayerAction.CraftingDrag drag) {
        int grid = -1;
        for (int index = 0; index < drag.areas().length; index++) {
            if (drag.areas()[index] == PlayerInventory.CraftArea.GRID) grid = index;
        }
        if (grid < 0) return false;
        if (drag.areas().length == 1) {
            if (drag.slots()[0] == 0) player.inventory().clickBeaconPayment(drag.button(), false);
            sendTo(player, craftingMessage(player, false));
            return true;
        }
        int keep = 0;
        for (PlayerInventory.CraftArea area : drag.areas()) {
            if (area != PlayerInventory.CraftArea.GRID) keep++;
        }
        PlayerInventory.CraftArea[] areas = new PlayerInventory.CraftArea[keep];
        int[] slots = new int[keep];
        int at = 0;
        for (int index = 0; index < drag.areas().length; index++) {
            if (drag.areas()[index] == PlayerInventory.CraftArea.GRID) continue;
            areas[at] = drag.areas()[index];
            slots[at++] = drag.slots()[index];
        }
        if (keep > 0) player.inventory().dragCrafting(areas, slots, drag.button());
        sendTo(player, craftingMessage(player, false));
        return true;
    }

    /**
     * [BEACON] {@code ServerboundSetBeaconPacket} → {@code BeaconMenu.updateEffects}. 결제 칸이 차 있고
     * {@code validateEffects(primary, secondary, levels)} 가 참일 때만 두 효과를 신호기 state 에 쓰고 결제
     * 아이템 한 개를 쓴다. 주 효과를 세우는 순간 빔이 있으면 power_select 소리가 난다
     * ({@code BeaconBlockEntity$1.set(1, …)}).
     */
    private void applyBeaconConfirm(PlayerAction.BeaconConfirm confirm) {
        PlayerTickState player = rt.players().get(confirm.nickname());
        if (!validCraftingSession(player)
                || player.openCraftingStation() != PlayerAction.CraftStation.BEACON) return;
        if (player.inventory().settlementLeased()) {
            enqueuePlayerContainerAction(player, () -> applyBeaconConfirm(confirm));
            return;
        }
        BlockPos pos = player.openCraftingTable();
        int levels = beaconLevelsAt(pos.x(), pos.y(), pos.z());
        if (!player.inventory().beaconPaymentPresent()
                || !BeaconRules.validateEffects(confirm.primary(), confirm.secondary(), levels)
                || !player.inventory().consumeBeaconPayment()) {
            sendTo(player, craftingMessage(player, false));
            return;
        }
        int state = BeaconRules.encodeState(confirm.primary(), confirm.secondary());
        setBeaconState(pos.x(), pos.y(), pos.z(), state);
        BeaconRules.BlockSampler sampler = (qx, qy, qz) -> residentBlockType(rt.accessor(), qx, qy, qz);
        BeaconRules.BeamBlocker blocker = (qx, qy, qz, id) -> id != Blocks.BEDROCK
                && id != UNAVAILABLE_BLOCK
                && MobLightEngine.blocksLight(id, rt.blockStates().get(qx, qy, qz, id));
        if (!BeaconRules.beamSections(sampler, blocker, pos.x(), pos.y(), pos.z(), Blocks.MAX_Y).isEmpty()) {
            broadcastWorldSound("beacon_power_select", pos.x(), pos.y(), pos.z(), (short) Blocks.BEACON);
        }
        for (PlayerTickState viewer : rt.players().values()) {
            if (viewer.openCraftingStation() == PlayerAction.CraftStation.BEACON
                    && samePosition(viewer.openCraftingTable(), pos.x(), pos.y(), pos.z())) {
                sendTo(viewer, craftingMessage(viewer, false));
            }
        }
    }

    /** [BEACON] 신호기 state(주/보조 효과)만 바꾼다. 영속·방송은 다른 state 전이와 같은 깔때기다. */
    private void setBeaconState(int x, int y, int z, int state) {
        if (residentBlockType(rt.accessor(), x, y, z) != Blocks.BEACON) return;
        if (rt.blockStates().get(x, y, z, Blocks.BEACON) == state) return;
        rt.setBlockState(x, y, z, Blocks.BEACON, state);
        rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) Blocks.BEACON);
    }

    private void applySelectLoomPattern(PlayerAction.SelectLoomPattern select) {
        PlayerTickState player = rt.players().get(select.nickname());
        if (!validCraftingSession(player)
                || player.openCraftingStation() != PlayerAction.CraftStation.LOOM) return;
        player.loomSelection(select.pattern());
        sendTo(player, craftingMessage(player, false));
    }

    private void applyOpenLectern(PlayerAction.OpenLectern open) {
        PlayerTickState player = rt.players().get(open.nickname());
        if (player == null || rt.lecternPersistence() == null
                || residentBlockType(rt.accessor(), open.x(), open.y(), open.z()) != Blocks.LECTERN
                || !InteractRules.withinContainerReach(player.x(), player.y(), player.z(),
                        player.crouching(), open.x(), open.y(), open.z())) return;
        var state = rt.lecternPersistence().load(rt.worldId(), open.x(), open.y(), open.z())
                .orElse(null);
        if (state == null) return;
        closeChestSession(player, true);
        closeFurnaceSession(player);
        closeEnchantingSession(player);
        if (player.openCraftingStation() != null) closeCraftingSession(player, true);
        player.openLectern(open.x(), open.y(), open.z(), open.requestId());
        sendTo(player, lecternMessage(state, player.openLecternRequestId()));
    }

    private void applyLecternPage(PlayerAction.LecternPage page) {
        PlayerTickState player = rt.players().get(page.nickname());
        BlockPos pos = player == null ? null : player.openLectern();
        if (pos == null || rt.lecternPersistence() == null
                || residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z()) != Blocks.LECTERN
                || !InteractRules.withinContainerReach(player.x(), player.y(), player.z(),
                        player.crouching(), pos.x(), pos.y(), pos.z())) {
            if (player != null) player.closeLectern();
            return;
        }
        try {
            var state = rt.lecternPersistence().turnPage(
                    rt.worldId(), pos.x(), pos.y(), pos.z(), page.page());
            sendTo(player, lecternMessage(state, player.openLecternRequestId()));
        } catch (IllegalArgumentException | IllegalStateException rejected) {
            rt.lecternPersistence().load(rt.worldId(), pos.x(), pos.y(), pos.z())
                    .ifPresent(state -> sendTo(player, lecternMessage(state, player.openLecternRequestId())));
        }
    }

    private void applyCloseLectern(PlayerAction.CloseLectern close) {
        PlayerTickState player = rt.players().get(close.nickname());
        if (player != null && (close.requestId() == null
                || close.requestId().equals(player.openLecternRequestId()))) player.closeLectern();
    }

    private void applyOpenBook(PlayerAction.OpenBook open) {
        PlayerTickState player = rt.players().get(open.nickname());
        if (player == null) return;
        PlayerInventory.HandRef hand = player.inventory().capture(inventoryHand(open.hand()));
        PlayerInventory.StackSnapshot book = player.inventory().stack(hand);
        if ((book.itemType() != PlayerInventory.WRITABLE_BOOK
                && book.itemType() != PlayerInventory.WRITTEN_BOOK)
                || book.itemComponents().book() == null) return;
        player.openBook(hand, book, open.requestId());
        sendTo(player, bookMessage(hand, book, player.openBookRequestId()));
    }

    private void applyEditBook(PlayerAction.EditBook edit) {
        PlayerTickState player = rt.players().get(edit.nickname());
        PlayerInventory.HandRef hand = player == null ? null : player.openBookHand();
        if (hand == null || !player.bookSessionMatchesCurrentStack()) {
            if (player != null) player.closeBook();
            return;
        }
        PlayerInventory.CompletePersistenceSnapshot source =
                player.inventory().acquireSettlementLease();
        if (source == null) return;
        PlayerInventory planned = source.detachedInventory();
        if (!planned.editWritableBook(planned.capture(hand.hand()), edit.pages())) {
            player.inventory().releaseSettlementLease(source);
            return;
        }
        PlayerInventory.CompletePersistenceSnapshot committed =
                planned.completePersistenceSnapshot();
        if (committed.revision() == source.revision()) {
            // An unchanged draft still receives its normal acknowledgement, but has no DB mutation.
            if (!player.inventory().releaseSettlementLease(source)) return;
        } else if (!commitPlayerOnlyMutation(player, source, committed)) return;
        PlayerInventory.HandRef currentHand = player.inventory().capture(hand.hand());
        player.openBook(currentHand, player.inventory().stack(currentHand), player.openBookRequestId());
        sendTo(player, inventoryMessage(player));
        sendTo(player, bookMessage(currentHand, player.inventory().stack(currentHand), player.openBookRequestId()));
    }

    private void applySignBook(PlayerAction.SignBook sign) {
        PlayerTickState player = rt.players().get(sign.nickname());
        PlayerInventory.HandRef hand = player == null ? null : player.openBookHand();
        if (hand == null || !player.bookSessionMatchesCurrentStack()) {
            if (player != null) player.closeBook();
            return;
        }
        PlayerInventory.CompletePersistenceSnapshot source =
                player.inventory().acquireSettlementLease();
        if (source == null) return;
        PlayerInventory planned = source.detachedInventory();
        if (!planned.signWritableBook(planned.capture(hand.hand()), sign.title(), player.nickname())) {
            player.inventory().releaseSettlementLease(source);
            return;
        }
        PlayerInventory.CompletePersistenceSnapshot committed =
                planned.completePersistenceSnapshot();
        if (!commitPlayerOnlyMutation(player, source, committed)) return;
        PlayerInventory.HandRef currentHand = player.inventory().capture(hand.hand());
        PlayerInventory.StackSnapshot book = player.inventory().stack(currentHand);
        sendTo(player, inventoryMessage(player));
        sendTo(player, bookMessage(currentHand, book, player.openBookRequestId()));
        player.closeBook();
    }

    private void applyCloseBook(PlayerAction.CloseBook close) {
        PlayerTickState player = rt.players().get(close.nickname());
        if (player != null && (close.requestId() == null
                || close.requestId().equals(player.openBookRequestId()))) player.closeBook();
    }

    private boolean commitPlayerOnlyMutation(PlayerTickState player,
            PlayerInventory.CompletePersistenceSnapshot source,
            PlayerInventory.CompletePersistenceSnapshot committed) {
        try {
            var outcome = rt.ctx().stateService().replaceRuntimeSnapshot(source.revision(),
                    rt.playerInventoryMutationSnapshot(player, committed));
            if (outcome == com.gameexpert.state.service.PlayerWorldStateService
                    .RuntimeSaveOutcome.COMMITTED
                    && player.inventory().installCommittedSettlement(
                            source, committed)) return true;
        } catch (RuntimeException failed) {
            log.warn("월드 {} 플레이어 전용 인벤토리 정산 실패: {}",
                    rt.worldId(), failed.toString());
        }
        player.inventory().releaseSettlementLease(source);
        return false;
    }

    private static BookUpdate bookMessage(
            PlayerInventory.HandRef hand, PlayerInventory.StackSnapshot book, Long requestId) {
        InventorySlot item = inventorySlot(0, book.itemType(), book.count(), null,
                book.enchantments(), mapIdOrNull(book.mapId()), shulkerIdOrNull(book.shulkerId()),
                book.bucketMobData(), book.itemComponentData());
        return new BookUpdate(hand.hand() == PlayerInventory.Hand.OFFHAND ? "offhand" : "main",
                item, 0, book.itemType() == PlayerInventory.WRITABLE_BOOK, requestId);
    }

    private static LecternUpdate lecternMessage(
            com.gameexpert.lectern.dto.LecternBlockData state, Long requestId) {
        PlayerInventory.StackSnapshot book = state.book();
        InventorySlot slot = inventorySlot(0, book.itemType(), book.count(), null,
                book.enchantments(), mapIdOrNull(book.mapId()), shulkerIdOrNull(book.shulkerId()),
                book.bucketMobData(), book.itemComponentData());
        return new LecternUpdate(state.x(), state.y(), state.z(), slot,
                state.page(), state.pageCount(), requestId);
    }

    private boolean takeAnvilResult(PlayerTickState player, boolean shift) {
        var plan = player.inventory().planAnvilResult(player.anvilRename(), false);
        if (plan == null || plan.tooExpensive() || player.xpLevel() < plan.levelCost()) return false;
        if (!player.inventory().takeAnvilResult(plan, shift)) return false;
        player.setXpTotal(XpRules.totalAfterSpendingLevels(player.xpTotal(), plan.levelCost()));
        BlockPos pos = player.openCraftingTable();
        if (pos != null) {
            int current = residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z());
            int degraded = com.gameexpert.engine.inventory.AnvilRules.degradedBlock(
                    current, blockXpRandom.nextInt(100), false);
            if (degraded != current) rt.fluidSim().applyChange(pos.x(), pos.y(), pos.z(), degraded);
        }
        return true;
    }

    private boolean takeGrindstoneResult(PlayerTickState player, boolean shift) {
        var plan = player.inventory().planGrindstoneResult();
        if (plan == null || !player.inventory().takeGrindstoneResult(plan, shift)) return false;
        int half = plan.minimumXp();
        player.addXp(half + (half == 0 ? 0 : blockXpRandom.nextInt(half)));
        return true;
    }

    private boolean takeCartographyResult(PlayerTickState player, boolean shift) {
        var service = rt.emptyMapSettlements();
        if (service == null
                || pendingCartographySettlements.containsKey(player.nickname())) return false;
        PlayerInventory inventory = player.inventory();
        PlayerInventory.StackSnapshot mapInput = inventory.craftingStackSnapshot(0);
        PlayerInventory.StackSnapshot additionInput = inventory.craftingStackSnapshot(1);
        CartographyRules.Operation operation = CartographyRules.operation(mapInput, additionInput);
        if (operation == null) return false;
        WorldMapData sourceMap = rt.mapData(mapInput.mapId());
        if (sourceMap == null) return false;
        FrozenCartographyPlan plan = FrozenCartographyPlan.stage(inventory, sourceMap,
                operation, shift, lease -> {
                    WorldMapData current = rt.mapData(sourceMap.getMapId());
                    if (!WorldMapRuntime.sameDurableMap(sourceMap, current)) return null;
                    long settlementId = stableCartographySettlementId(player.playerId(),
                            lease.revision(), lease.leaseNonce(), sourceMap.getMapId(),
                            sourceMap.getRevision(), operation, shift);
                    AllocationRequest allocation = CartographyMapSettlementCommand.allocationRequest(
                            settlementId, player.playerId(), rt.worldId(), lease,
                            sourceMap, operation, shift);
                    return service.reserveMapId(allocation);
                });
        if (plan == null) return false;
        boolean retained = false;
        try {
            WorldMapData current = rt.mapData(sourceMap.getMapId());
            if (!WorldMapRuntime.sameDurableMap(sourceMap, current)) {
                plan.resolve(CartographySettlementOutcome.REJECTED, ignored -> { });
                return false;
            }
            PlayerInventoryMutationSnapshot committed =
                    rt.playerInventoryMutationSnapshot(player, plan.committedInventory());
            CartographyMapSettlementCommand command = new CartographyMapSettlementCommand(
                    plan.allocationReservation(), plan.sourceInventory(),
                    plan.committedInventory(), committed, sourceMap, plan.inventoryDelta());
            PendingCartographySettlement pending =
                    new PendingCartographySettlement(player, plan, command);
            pendingCartographySettlements.put(player.nickname(), pending);
            retained = true;
            // Grid clicks advance the live revision before periodic persistence catches up.
            // The lease freezes this exact source; queue it ahead of the strict settlement CAS
            // on the same FIFO writer, as with inventory baselines before container settlement.
            rt.queuePlayerInventoryBaseline(player);
            submitCartographySettlement(player.nickname(), pending);
        } finally {
            if (!retained && plan.pending()) {
                plan.resolve(CartographySettlementOutcome.REJECTED, ignored -> { });
            }
        }
        // Accepted for durable settlement; owner-visible state still waits for its callback.
        return true;
    }

    private void pumpCartographySettlements() {
        pumpEmptyMapSettlements();
        if (rt.emptyMapSettlements() == null) return;
        for (Map.Entry<String, PendingCartographySettlement> entry
                : List.copyOf(pendingCartographySettlements.entrySet())) {
            PendingCartographySettlement pending = entry.getValue();
            if (!pending.inFlight && !pending.reconciliationRequired) {
                submitCartographySettlement(entry.getKey(), pending);
            }
        }
    }

    private void submitCartographySettlement(
            String nickname, PendingCartographySettlement pending) {
        if (pending.inFlight || pending.reconciliationRequired
                || pendingCartographySettlements.get(nickname) != pending) return;
        var service = rt.emptyMapSettlements();
        if (service == null) return;
        pending.inFlight = true;
        Runnable task = () -> {
            CartographySettlementOutcome outcome;
            try {
                outcome = switch (service.settleCartography(pending.command)) {
                    case COMMITTED -> CartographySettlementOutcome.COMMITTED;
                    case IDEMPOTENT -> CartographySettlementOutcome.IDEMPOTENT;
                    case STALE -> CartographySettlementOutcome.REJECTED;
                };
            } catch (RuntimeException | Error unknown) {
                outcome = CartographySettlementOutcome.UNKNOWN;
            }
            CartographySettlementOutcome resolved = outcome;
            rt.enqueuePersistenceCompletion(() -> completeCartographySettlement(
                    nickname, pending, resolved));
        };
        PersistenceExecutor persistence = rt.ctx().persistenceExecutor();
        if (persistence == null) {
            task.run();
            return;
        }
        try {
            if (!persistence.trySubmit(task)) pending.inFlight = false;
        } catch (RuntimeException | Error unavailable) {
            // Submission itself did not yield a terminal answer; retain the frozen command.
            pending.inFlight = false;
        }
    }

    private void completeCartographySettlement(String nickname,
            PendingCartographySettlement pending, CartographySettlementOutcome outcome) {
        if (pendingCartographySettlements.get(nickname) != pending) return;
        pending.inFlight = false;
        if (outcome == CartographySettlementOutcome.UNKNOWN) return;
        try {
            boolean resolved = pending.plan.resolve(outcome, rt::acceptSettledMap);
            if (!resolved) {
                pending.reconciliationRequired = true;
                return;
            }
        } catch (RuntimeException collision) {
            pending.reconciliationRequired = true;
            log.error("월드 {} 지도 제작 정산 설치 충돌: nickname={} settlement={}",
                    rt.worldId(), nickname, pending.command.getSettlementId(), collision);
            return;
        }
        pendingCartographySettlements.remove(nickname);
        if (outcome == CartographySettlementOutcome.REJECTED) {
            if (rt.players().get(nickname) == pending.player) {
                sendTo(pending.player, craftingMessage(pending.player, false));
            }
            return;
        }
        if (rt.players().get(nickname) != pending.player) return;
        sendTo(pending.player, craftingMessage(pending.player, true));
        sendTo(pending.player, mapStateMessage(pending.plan.resultMap()));
    }

    static long stableCartographySettlementId(long playerId, long inventoryRevision,
            long inventoryLeaseNonce, int sourceMapId, long sourceMapRevision,
            CartographyRules.Operation operation, boolean shift) {
        if (playerId <= 0 || inventoryRevision < 0 || inventoryLeaseNonce <= 0
                || inventoryLeaseNonce == Long.MAX_VALUE || sourceMapId <= 0
                || sourceMapRevision < 0 || operation == null) {
            throw new IllegalArgumentException("complete cartography identity is required");
        }
        long value = playerId * 0x9E3779B97F4A7C15L + inventoryRevision;
        value ^= inventoryLeaseNonce * 0xC2B2AE3D27D4EB4FL;
        value ^= Integer.toUnsignedLong(sourceMapId) * 0xD6E8FEB86659FD93L;
        value ^= sourceMapRevision * 0xE7037ED1A0B428DBL;
        value ^= (long) (operation.ordinal() + 1) * 0xA0761D6478BD642FL;
        if (shift) value ^= 0x8EBC6AF09C88C6E3L;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        // Empty-map receipts historically used a positive int map ID as settlement ID. Keep the
        // cartography namespace permanently disjoint while remaining a positive signed long.
        return (value & 0x1FFF_FFFF_FFFF_FFFFL) | 0x4000_0000_0000_0000L;
    }

    private boolean takeLoomResult(PlayerTickState player, boolean shift) {
        var result = player.inventory().planLoomResult(player.loomSelection());
        return result != null && player.inventory().takeLoomResult(result, shift);
    }

    private void applyCollectCrafting(PlayerAction.CollectCrafting collect) {
        PlayerTickState player = rt.players().get(collect.nickname());
        if (!validCraftingSession(player)) return;
        if ((collect.area() == PlayerInventory.CraftArea.ARMOR
                || collect.area() == PlayerInventory.CraftArea.OFFHAND)
                && player.openCraftingStation() != PlayerAction.CraftStation.INVENTORY) return;
        int slots = switch (collect.area()) {
            case INVENTORY -> PlayerInventory.SLOTS;
            case GRID -> player.inventory().craftingSlotCount();
            case ARMOR -> ArmorSlot.values().length;
            case OFFHAND -> 1;
            case RESULT -> 0;
        };
        if (collect.slot() < 0 || collect.slot() >= slots) return;
        if (player.inventory().collectCrafting(
                player.openCraftingStation() == PlayerAction.CraftStation.INVENTORY)) {
            sendTo(player, craftingMessage(player, false));
        }
    }

    private void applyDropCraftingCursor(PlayerAction.DropCraftingCursor drop) {
        PlayerTickState player = rt.players().get(drop.nickname());
        if (!validCraftingSession(player)) return;
        settleCursorGroundDrop(player, true, drop.one(), () ->
                sendTo(player, craftingMessage(player, false)));
    }

    private void applyCloseCrafting(PlayerAction.CloseCrafting close) {
        PlayerTickState player = rt.players().get(close.nickname());
        if (player == null) return;
        player.cancelPendingCraftingOpen(close.requestId());
        if (player.openCraftingStation() != null
                && player.openCraftingSessionId() == close.sessionId()
                && (close.requestId() == null || close.requestId().equals(player.openCraftingRequestId()))) {
            closeCraftingSession(player, true);
        }
    }

    private void applyBrewingClick(PlayerAction.BrewingClick click) {
        PlayerTickState player = rt.players().get(click.nickname());
        if (!validBrewingSession(player) || !samePosition(
                player.openCraftingTable(), click.x(), click.y(), click.z())) return;
        BlockPos pos = player.openCraftingTable();
        BrewingInventory live = rt.brewingStorage().openAt(pos.x(), pos.y(), pos.z());
        settleBrewingMutation(player, pos, live, (inventory, planned) ->
                inventory.clickContainer(
                        planned, click.area(), click.slot(), click.button(), click.shift()));
    }

    private void applyBrewingDrag(PlayerAction.BrewingDrag drag) {
        PlayerTickState player = rt.players().get(drag.nickname());
        if (!validBrewingSession(player) || !samePosition(
                player.openCraftingTable(), drag.x(), drag.y(), drag.z())) return;
        BlockPos pos = player.openCraftingTable();
        BrewingInventory live = rt.brewingStorage().openAt(pos.x(), pos.y(), pos.z());
        settleBrewingMutation(player, pos, live, (inventory, planned) ->
                inventory.dragContainer(planned, drag.areas(), drag.slots(), drag.button()));
    }

    private void applyCollectBrewing(PlayerAction.CollectBrewing collect) {
        PlayerTickState player = rt.players().get(collect.nickname());
        if (!validBrewingSession(player) || !samePosition(
                player.openCraftingTable(), collect.x(), collect.y(), collect.z())) return;
        BlockPos pos = player.openCraftingTable();
        BrewingInventory live = rt.brewingStorage().openAt(pos.x(), pos.y(), pos.z());
        if (collect.slot() < 0 || collect.slot() >= (collect.area()
                == PlayerInventory.ContainerArea.INVENTORY
                        ? PlayerInventory.SLOTS : BrewingInventory.SLOTS)) return;
        settleBrewingMutation(player, pos, live,
                (inventory, planned) -> inventory.collectContainer(planned));
    }

    @FunctionalInterface
    private interface BrewingMutation {
        boolean apply(PlayerInventory inventory, BrewingInventory brewing);
    }

    private void settleBrewingMutation(PlayerTickState player, BlockPos pos,
            BrewingInventory live, BrewingMutation mutation) {
        rt.flushDirtyBrewing();
        PlayerInventory.CompletePersistenceSnapshot source =
                player.inventory().acquireSettlementLease();
        if (source == null) return;
        PlayerInventory plannedInventory = source.detachedInventory();
        BrewingInventory planned = detachedBrewing(live);
        if (!mutation.apply(plannedInventory, planned)) {
            player.inventory().releaseSettlementLease(source);
            sendTo(player, brewingUpdate(player, pos.x(), pos.y(), pos.z(), live));
            return;
        }
        BrewingInventory committedStand = planned.persistenceRevision() > live.persistenceRevision()
                ? planned.copyAtPersistenceRevision(live.persistenceRevision() + 1) : planned;
        PlayerInventory.CompletePersistenceSnapshot committedInventory =
                plannedInventory.completePersistenceSnapshot();
        if (committedStand.persistenceRevision() == live.persistenceRevision()) {
            if (player.inventory().installCommittedSettlement(
                    source, committedInventory)) {
                sendTo(player, brewingUpdate(player, pos.x(), pos.y(), pos.z(), live));
            } else {
                player.inventory().releaseSettlementLease(source);
            }
            return;
        }
        long expectedRevision = live.persistenceRevision();
        rt.brewingStorage().deactivate(BrewingStorage.key(pos.x(), pos.y(), pos.z()));
        var target = new InventoryMutationTarget.Brewing(
                new InventoryMutationTarget.Position(pos.x(), pos.y(), pos.z()),
                brewingTypes(committedStand), brewingCounts(committedStand),
                committedStand.componentsSnapshot(), committedStand.fuel(),
                committedStand.brewTicks(), committedStand.brewingIngredient(),
                committedStand.persistenceRevision());
        var command = new PlayerContainerSettlementCommand(
                WorldRuntime.stablePlayerContainerSettlementId(
                        player.playerId(), source.revision()),
                source.revision(), rt.playerInventoryMutationSnapshot(player, committedInventory),
                target, List.of(expectedRevision));
        Runnable rejected = () -> {
            player.inventory().releaseSettlementLease(source);
            if (live.needsTick()) rt.brewingStorage().activate(pos.x(), pos.y(), pos.z());
            if (rt.players().get(player.nickname()) == player) {
                sendTo(player, brewingUpdate(player, pos.x(), pos.y(), pos.z(), live));
            }
        };
        rt.submitPlayerContainerSettlement(command, () -> {
            boolean installed = rt.brewingStorage().peekAt(pos.x(), pos.y(), pos.z()) == live
                    && live.persistenceRevision() == expectedRevision;
            if (installed) {
                rt.brewingStorage().load(pos.x(), pos.y(), pos.z(), committedStand);
                if (committedStand.needsTick()) {
                    rt.brewingStorage().activate(pos.x(), pos.y(), pos.z());
                }
                syncBrewingBottles(pos.x(), pos.y(), pos.z(), committedStand);
            }
            if (rt.players().get(player.nickname()) == player
                    && player.inventory().installCommittedSettlement(source, committedInventory)) {
                if (installed && samePosition(player.openCraftingTable(),
                        pos.x(), pos.y(), pos.z()) && rt.brewingStorage().ownsLease(
                                pos.x(), pos.y(), pos.z(), player.nickname())) {
                    sendTo(player, brewingUpdate(
                            player, pos.x(), pos.y(), pos.z(), committedStand));
                } else {
                    sendTo(player, inventoryMessage(player));
                }
            } else {
                player.inventory().releaseSettlementLease(source);
            }
        }, rejected);
    }

    private static BrewingInventory detachedBrewing(BrewingInventory source) {
        return BrewingInventory.fromSnapshot(source.snapshot());
    }

    /**
     * [BLOCK-SHAPES] {@code BrewingStandBlockEntity} 는 물약 칸 i 가 비었는지를 {@code has_bottle_i} 로
     * setBlock 한다(병 칸 {@code FIRST_BOTTLE_SLOT..LAST_BOTTLE_SLOT} = has_bottle_0..2). 월드의 병 모델이
     * 그 비트를 읽는다. 정적판 {@code StandaloneWorldRuntime.syncBrewingBottles} 와 같은 전이다.
     */
    /**
     * [BLOCK-SHAPES] has_bottle 비트 이전에 저장된 양조대도 병을 그리도록, 이 청크(또는 chunk 가
     * null 이면 전체)에 저장된 양조대의 블록 state 를 병 칸에 맞춘다. 비트가 이미 같으면 쓰지 않는다
     * (정적판 {@code syncLegacyModelBlockStates} 와 같은 전이).
     */
    void syncStoredBrewingBottles(int[] chunk) {
        List<Long> keys = chunk == null ? rt.brewingStorage().keys()
                : rt.brewingStorage().keysInChunk(chunk[0], chunk[1]);
        for (long key : keys) {
            int[] pos = BrewingStorage.unkey(key);
            BrewingInventory stand = rt.brewingStorage().peekAt(pos[0], pos[1], pos[2]);
            if (stand != null) syncBrewingBottles(pos[0], pos[1], pos[2], stand);
        }
    }

    private void syncBrewingBottles(int x, int y, int z, BrewingInventory brewing) {
        if (residentBlockType(rt.accessor(), x, y, z) != Blocks.BREWING_STAND) return;
        int bits = 0;
        for (int bottle = 0; bottle < 3; bottle++) {
            if (brewing.count(BrewingInventory.FIRST_BOTTLE_SLOT + bottle) > 0) bits |= 1 << bottle;
        }
        int old = rt.blockStates().get(x, y, z, Blocks.BREWING_STAND);
        int next = old & ~BlockModelShapes.BREWING_STAND_BOTTLE_MASK | bits;
        if (next == old) return;
        rt.setBlockState(x, y, z, Blocks.BREWING_STAND, next);
        rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) Blocks.BREWING_STAND);
    }

    private static short[] brewingTypes(BrewingInventory brewing) {
        short[] types = new short[BrewingInventory.SLOTS];
        for (int slot = 0; slot < types.length; slot++) types[slot] = brewing.itemType(slot);
        return types;
    }

    private static int[] brewingCounts(BrewingInventory brewing) {
        int[] counts = new int[BrewingInventory.SLOTS];
        for (int slot = 0; slot < counts.length; slot++) counts[slot] = brewing.count(slot);
        return counts;
    }

    private void applyDropBrewingCursor(PlayerAction.DropBrewingCursor drop) {
        PlayerTickState player = rt.players().get(drop.nickname());
        if (!validBrewingSession(player) || !samePosition(
                player.openCraftingTable(), drop.x(), drop.y(), drop.z())) return;
        BlockPos pos = player.openCraftingTable();
        BrewingInventory brewing = rt.brewingStorage().openAt(pos.x(), pos.y(), pos.z());
        settleCursorGroundDrop(player, false, drop.one(), () ->
                sendTo(player, brewingUpdate(player, pos.x(), pos.y(), pos.z(), brewing)));
    }

    private void applyCloseBrewing(PlayerAction.CloseBrewing close) {
        PlayerTickState player = rt.players().get(close.nickname());
        if (player != null && player.openCraftingStation() == PlayerAction.CraftStation.BREWING
                && (close.requestId() == null || close.requestId().equals(player.openCraftingRequestId()))
                && samePosition(player.openCraftingTable(), close.x(), close.y(), close.z())) {
            closeCraftingSession(player, true);
        }
    }

    /** [POTION] 제작 세션이 요구하는 스테이션 블록. 개인 격자는 블록이 없어 AIR 다. */
    private static int craftingStationBlock(PlayerAction.CraftStation station) {
        if (station == PlayerAction.CraftStation.TABLE) return Blocks.CRAFTING_TABLE;
        if (station == PlayerAction.CraftStation.BREWING) return Blocks.BREWING_STAND;
        if (station == PlayerAction.CraftStation.STONECUTTER) return Blocks.STONECUTTER;
        if (station == PlayerAction.CraftStation.SMITHING) return Blocks.SMITHING_TABLE;
        if (station == PlayerAction.CraftStation.ANVIL) return Blocks.ANVIL;
        if (station == PlayerAction.CraftStation.CARTOGRAPHY) return Blocks.CARTOGRAPHY_TABLE;
        if (station == PlayerAction.CraftStation.GRINDSTONE) return Blocks.GRINDSTONE;
        if (station == PlayerAction.CraftStation.LOOM) return Blocks.LOOM;
        if (station == PlayerAction.CraftStation.BEACON) return Blocks.BEACON;
        return Blocks.AIR;
    }

    private static boolean isCraftingStationBlock(int block) {
        return block == Blocks.CRAFTING_TABLE || block == Blocks.BREWING_STAND
                || block == Blocks.STONECUTTER || block == Blocks.SMITHING_TABLE
                || Blocks.isAnvil(block) || block == Blocks.CARTOGRAPHY_TABLE
                || block == Blocks.GRINDSTONE || block == Blocks.LOOM || block == Blocks.BEACON;
    }

    private static boolean craftingStationMatches(PlayerAction.CraftStation station, int block) {
        return station == PlayerAction.CraftStation.ANVIL
                ? Blocks.isAnvil(block) : block == craftingStationBlock(station);
    }

    private static boolean isSpecialWorkstation(PlayerAction.CraftStation station) {
        return station == PlayerAction.CraftStation.SMITHING
                || station == PlayerAction.CraftStation.ANVIL
                || station == PlayerAction.CraftStation.CARTOGRAPHY
                || station == PlayerAction.CraftStation.GRINDSTONE
                || station == PlayerAction.CraftStation.LOOM
                || station == PlayerAction.CraftStation.BEACON;
    }

    /** 프로토콜에 실리는 스테이션 이름. 양판이 같은 문자열을 쓴다. */
    private static String craftingStationName(PlayerAction.CraftStation station) {
        if (station == PlayerAction.CraftStation.TABLE) return "table";
        if (station == PlayerAction.CraftStation.BREWING) return "brewing";
        if (station == PlayerAction.CraftStation.STONECUTTER) return "stonecutter";
        if (station == PlayerAction.CraftStation.SMITHING) return "smithing";
        if (station == PlayerAction.CraftStation.ANVIL) return "anvil";
        if (station == PlayerAction.CraftStation.CARTOGRAPHY) return "cartography";
        if (station == PlayerAction.CraftStation.GRINDSTONE) return "grindstone";
        if (station == PlayerAction.CraftStation.LOOM) return "loom";
        if (station == PlayerAction.CraftStation.BEACON) return "beacon";
        return "inventory";
    }

    private boolean validCraftingSession(PlayerTickState player) {
        if (player == null || player.openCraftingStation() == null
                || !player.inventory().craftingOpen()) return false;
        if (player.openCraftingStation() == PlayerAction.CraftStation.INVENTORY) return true;
        BlockPos table = player.openCraftingTable();
        if (table != null
                && craftingStationMatches(player.openCraftingStation(),
                        residentBlockType(rt.accessor(), table.x(), table.y(), table.z()))
                && InteractRules.withinContainerReach(
                        player.x(), player.y(), player.z(), player.crouching(),
                        table.x(), table.y(), table.z())) {
            return true;
        }
        closeCraftingSession(player, true);
        return false;
    }

    private boolean validBrewingSession(PlayerTickState player) {
        if (player == null || player.openCraftingStation() != PlayerAction.CraftStation.BREWING) {
            return false;
        }
        BlockPos pos = player.openCraftingTable();
        if (pos != null && residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z())
                    == Blocks.BREWING_STAND
                && InteractRules.withinContainerReach(player.x(), player.y(), player.z(),
                        player.crouching(), pos.x(), pos.y(), pos.z())
                && rt.brewingStorage().ownsLease(
                        pos.x(), pos.y(), pos.z(), player.nickname())) {
            return true;
        }
        closeCraftingSession(player, true);
        return false;
    }

    /** 33↔34 렌더 상태 교체가 설치 방향을 0으로 지우지 않도록 같은 틱에 방향 state를 복원한다. */
    private void swapFurnaceTypePreservingState(int x, int y, int z, int replacement) {
        int current = residentBlockType(rt.accessor(), x, y, z);
        int facing = rt.blockStates().get(x, y, z, current) & BuildingBlockRules.FACING_MASK;
        rt.fluidSim().applyChange(x, y, z, replacement);
        rt.setBlockState(x, y, z, replacement, facing);
        rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) replacement);
    }

    private void requeuePlayerLeaveOrComplete(WorldRuntime.PlayerLeaveRequest request) {
        if (request == null) return;
        try {
            if (rt.ownerTurnMayContinue()) {
                // Runtime owns the atomic active/queued transition and the terminal race.
                rt.requeuePlayerLeaveRequest(request);
            } else {
                request.complete(null);
            }
        } catch (Throwable failure) {
            request.complete(null);
            log.error("월드 {} 플레이어 퇴장 요청 종료 처리 실패: nickname={}",
                    rt.worldId(), request.nickname(), failure);
        }
    }

    /**
     * One owner-transfer gate covers the synchronous planner and every asynchronous settlement
     * callback.  Before the gate is claimed this loop owns the lease/reservation; after it is
     * claimed only the callback path may settle the request, so a late Throwable cannot requeue a
     * request whose durable handoff already owns the resources.
     */
    private final class PlayerLeaveSettlementGuard {
        private final WorldRuntime.PlayerLeaveRequest request;
        private final PlayerInventory inventory;
        private final PlayerInventory.CompletePersistenceSnapshot source;
        private final AtomicBoolean ownershipTransferred = new AtomicBoolean();
        private WorldRuntime.PlayerInventorySettlementReservation reservation;
        private boolean leaseOwned = true;
        private boolean reservationOwned;

        private PlayerLeaveSettlementGuard(WorldRuntime.PlayerLeaveRequest request,
                PlayerInventory inventory,
                PlayerInventory.CompletePersistenceSnapshot source) {
            this.request = request;
            this.inventory = inventory;
            this.source = source;
        }

        private void attachReservation(
                WorldRuntime.PlayerInventorySettlementReservation reservation) {
            this.reservation = reservation;
            this.reservationOwned = reservation != null;
        }

        private boolean claimOwnership() {
            return ownershipTransferred.compareAndSet(false, true);
        }

        private void leaseConsumedByInstall() {
            leaseOwned = false;
        }

        private boolean releaseOwnedResources() {
            boolean released = true;
            if (leaseOwned) {
                leaseOwned = false;
                try {
                    inventory.releaseSettlementLease(source);
                } catch (Throwable failure) {
                    released = false;
                    log.error("월드 {} 플레이어 퇴장 인벤토리 lease 해제 실패: nickname={}",
                            rt.worldId(), request.nickname(), failure);
                }
            }
            if (reservationOwned) {
                reservationOwned = false;
                try {
                    rt.releasePlayerInventorySettlement(reservation);
                } catch (Throwable failure) {
                    released = false;
                    log.error("월드 {} 플레이어 퇴장 reservation 해제 실패: nickname={}",
                            rt.worldId(), request.nickname(), failure);
                }
            }
            return released;
        }

        private void releaseReservationAfterFinish() {
            if (!reservationOwned) return;
            reservationOwned = false;
            try {
                rt.releasePlayerInventorySettlement(reservation);
            } catch (Throwable failure) {
                request.complete(null);
                log.error("월드 {} 플레이어 퇴장 reservation 해제 실패: nickname={}",
                        rt.worldId(), request.nickname(), failure);
            }
        }

        /** The quarantine method owns release when a durable row lost its live CAS authority. */
        private void disarmForQuarantine() {
            leaseOwned = false;
            reservationOwned = false;
        }

        private void reject() {
            if (!claimOwnership()) return;
            if (!releaseOwnedResources()) {
                request.complete(null);
                return;
            }
            requeuePlayerLeaveOrComplete(request);
        }

        private void fail(Throwable failure) {
            if (claimOwnership()) {
                if (releaseOwnedResources()) {
                    requeuePlayerLeaveOrComplete(request);
                } else {
                    request.complete(null);
                }
                return;
            }
            // A callback already owns the handoff.  Its resources are released below, but the
            // leave future must not be sent back through the queue after ownership transferred.
            request.complete(null);
            log.error("월드 {} 플레이어 퇴장 정산 실패 after handoff: nickname={}",
                    rt.worldId(), request.nickname(), failure);
        }

        private void failAfterHandoff(Throwable failure) {
            releaseOwnedResources();
            request.complete(null);
            log.error("월드 {} 플레이어 퇴장 정산 callback 실패: nickname={}",
                    rt.worldId(), request.nickname(), failure);
        }
    }

    // ── ①.0 퇴장 정산: 연결 소유자를 확인한 뒤 최종 상태를 handoff ──
    private void drainPlayerLeaves() {
        // Freeze admission before polling. A rejected settlement requeues at the tail for the
        // next owner turn instead of feeding itself forever in this drain.
        int remaining = rt.playerLeaveRequestBatchSize();
        WorldRuntime.PlayerLeaveRequest request;
        while (remaining-- > 0 && (request = rt.pollPlayerLeaveRequest()) != null) {
            try {
                PlayerTickState player = rt.playerForConnection(
                        request.nickname(), request.connectionId());
                if (player == null) {
                    request.complete(null);
                    continue;
                }
                settleDisconnectInventory(request, player);
            } catch (Throwable error) {
                requeuePlayerLeaveOrComplete(request);
                log.error("월드 {} 플레이어 퇴장 정산 실패: nickname={}",
                        rt.worldId(), request.nickname(), error);
            }
        }
    }

    /**
     * 제작/컨테이너 커서 반환과 필요한 지면 드랍을 플레이어 제거보다 먼저 확정한다. 드랍이 있으면
     * 플레이어 완전 스냅샷과 안정 ID 지면 엔티티가 같은 영속 트랜잭션에 들어간다.
     */
    private void settleDisconnectInventory(
            WorldRuntime.PlayerLeaveRequest request, PlayerTickState player) {
        PlayerInventory inventory = player.inventory();
        PlayerInventory.CompletePersistenceSnapshot source = inventory.acquireSettlementLease();
        if (source == null) {
            requeuePlayerLeaveOrComplete(request);
            return;
        }
        PlayerLeaveSettlementGuard guard = new PlayerLeaveSettlementGuard(
                request, inventory, source);
        try {
            PlayerInventory planned = source.detachedInventory();
            List<PlayerInventory.DroppedStack> overflow = planned.craftingOpen()
                    ? planned.closeCrafting() : planned.closeContainerCursor();
            PlayerInventory.CompletePersistenceSnapshot committed =
                    planned.completePersistenceSnapshot();
            boolean changed = committed.revision() > source.revision();
            if (overflow.isEmpty()) {
                if (!rt.ownerTurnMayContinue()) {
                    guard.reject();
                    return;
                }
                if (changed) {
                    if (!inventory.installCommittedSettlement(source, committed)) {
                        guard.reject();
                        return;
                    }
                    guard.leaseConsumedByInstall();
                } else {
                    if (!guard.releaseOwnedResources()) {
                        request.complete(null);
                        return;
                    }
                }
                if (!rt.ownerTurnMayContinue()) {
                    guard.reject();
                    return;
                }
                if (!guard.claimOwnership()) return;
                try {
                    finishPlayerLeave(request, player);
                } catch (Throwable failure) {
                    guard.failAfterHandoff(failure);
                }
                return;
            }

            if (!rt.ownerTurnMayContinue()) {
                guard.reject();
                return;
            }

            WorldRuntime.PlayerInventorySettlementReservation reservation =
                    rt.reservePlayerInventorySettlement(player, request.connectionId(), inventory,
                            source);
            if (reservation == null) {
                guard.reject();
                return;
            }
            guard.attachReservation(reservation);

            List<com.gameexpert.ground.dto.GroundItemSnapshot> ground =
                    new ArrayList<>(overflow.size());
            long settlementId = 0;
            for (PlayerInventory.DroppedStack stack : overflow) {
                long entityId = rt.itemSystem().reserveSettlementEntityId();
                if (settlementId == 0) settlementId = entityId;
                ground.add(rt.itemSystem().settlementThrownDropSnapshot(entityId, stack,
                        player.x(), player.y(), player.z(), player.crouching(),
                        player.yaw(), player.pitch()));
            }
            var service = rt.groundMutationSettlements();
            Runnable install = () -> {
                if (!guard.claimOwnership()) return;
                try {
                    boolean authorityLost;
                    synchronized (inventory) {
                        authorityLost = !rt.playerLeaveCompletionMayApply()
                                || !rt.ownsPlayerInventorySettlement(reservation)
                                || !inventory.settlementLeased()
                                || inventory.revision() != source.revision();
                        if (!authorityLost) {
                            authorityLost = !inventory.installCommittedSettlement(
                                    source, committed);
                        }
                    }
                    if (authorityLost) {
                        guard.disarmForQuarantine();
                        rt.quarantineCommittedPlayerInventorySettlement(reservation);
                        request.complete(null);
                        return;
                    }
                    guard.leaseConsumedByInstall();
                    for (var item : ground) rt.itemSystem().commitSettlementDrop(item);
                    finishPlayerLeave(request, player, reservation,
                            guard::releaseReservationAfterFinish);
                } catch (Throwable failure) {
                    guard.failAfterHandoff(failure);
                } finally {
                    guard.releaseReservationAfterFinish();
                }
            };
            if (service == null) {
                install.run();
                return;
            }
            long expectedGroundRevision = rt.groundRevision();
            var command = new com.gameexpert.ground.dto.GroundMutationCommand(
                    WorldRuntime.stableGroundMutationId(settlementId, 3),
                    com.gameexpert.ground.dto.GroundMutationCommand.Kind.PLAYER_DROP,
                    rt.worldId(), expectedGroundRevision, expectedGroundRevision + 1,
                    source.revision(), rt.playerInventoryMutationSnapshot(player, committed),
                    ground, List.of(), List.of(), List.of());
            Runnable rejected = guard::reject;
            boolean admissible;
            synchronized (inventory) {
                admissible = rt.ownsPlayerInventorySettlement(reservation)
                        && inventory.settlementLeased()
                        && inventory.revision() == source.revision();
            }
            if (!admissible) {
                rejected.run();
                return;
            }
            boolean accepted = rt.submitGroundSettlement(command, install, rejected);
            if (!accepted) rejected.run();
        } catch (Throwable failure) {
            guard.fail(failure);
        }
    }

    private void finishPlayerLeave(
            WorldRuntime.PlayerLeaveRequest request, PlayerTickState expected) {
        finishPlayerLeave(request, expected, null, () -> { });
    }

    private void finishPlayerLeave(WorldRuntime.PlayerLeaveRequest request,
            PlayerTickState expected,
            WorldRuntime.PlayerInventorySettlementReservation reservation) {
        finishPlayerLeave(request, expected, reservation, () -> { });
    }

    private void finishPlayerLeave(WorldRuntime.PlayerLeaveRequest request,
            PlayerTickState expected,
            WorldRuntime.PlayerInventorySettlementReservation reservation,
            Runnable beforeCompletion) {
        try {
            if (expected.openCraftingStation() == PlayerAction.CraftStation.BREWING
                    && expected.openCraftingTable() != null) {
                BlockPos pos = expected.openCraftingTable();
                rt.brewingStorage().releaseLease(pos.x(), pos.y(), pos.z(), expected.nickname());
            }
            PlayerTickState removed = rt.removePlayerOnOwner(
                    request.nickname(), request.connectionId(), reservation);
            if (removed != expected) {
                request.complete(null);
                return;
            }
            // settleDisconnectInventory already folded the private cursor into its committed
            // snapshot. Logout/reconnect now invalidates the capability without another mutation.
            removed.closeGeneratedEntityCargo();
            removed.cancelGeneratedEntityEquipmentSettlement();
            unregisterChestSubscriber(removed);
            unregisterFurnaceSubscriber(removed);
            removed.closeCrafting();
            movementLimits.forget(removed.nickname());
            miningLimits.forget(removed.nickname());
            finalSceneLandingActionLedgers.remove(removed.nickname());
            PendingFinalScenePrerequisites pending =
                    pendingFinalScenePrerequisites.get(removed.nickname());
            if (pending != null && pending.player() == removed) {
                rt.cancelGeneratedFinalSceneEvidence(removed.nickname(), pending.scenario(),
                        pending.actionNonce());
                pendingFinalScenePrerequisites.remove(removed.nickname());
            }
            finalScenePrerequisiteNonces.remove(removed.nickname());
            var feedbackIterator = pendingOneShotFeedback.entrySet().iterator();
            while (feedbackIterator.hasNext()) {
                var feedback = feedbackIterator.next();
                if (!feedback.getKey().nickname().equals(removed.nickname())) continue;
                feedbackIterator.remove();
                feedback.getValue().accepted().run();
            }
            finalScenePrerequisiteNonces.remove(removed.nickname());
            spawnPointResend.remove(request.nickname());
            Runnable completeAfterDurableSave = () -> {
                beforeCompletion.run();
                request.complete(removed);
            };
            if (!rt.saveDepartedState(removed, completeAfterDurableSave)) {
                // A removed state that was not admitted to Runtime's durable lane is never exposed
                // as a successful leave; the caller gets a terminal null result instead.
                beforeCompletion.run();
                request.complete(null);
            }
        } catch (Throwable failure) {
            request.complete(null);
            log.error("월드 {} 플레이어 퇴장 완료 실패: nickname={}",
                    rt.worldId(), request.nickname(), failure);
        }
    }

    private static final class FinalSceneLandingActionLedger {
        private final Set<String> burned = new HashSet<>();
        private boolean saturated;

        private boolean available(String actionId) {
            if (saturated || burned.contains(actionId)) return false;
            if (burned.size() >= ActionQueue.DEFAULT_MAX_PER_PLAYER) {
                burned.clear();
                saturated = true;
                return false;
            }
            return true;
        }

        private boolean burn(String actionId) {
            if (saturated || burned.contains(actionId)) return false;
            if (burned.size() >= ActionQueue.DEFAULT_MAX_PER_PLAYER) {
                // 포화 뒤에는 메모리를 돌려주되 세션 전체가 fail-closed 라 예전 ID도 재사용할 수 없다.
                burned.clear();
                saturated = true;
                return false;
            }
            burned.add(actionId);
            return true;
        }
    }

    // ── ① attack: 전투 시스템으로 위임(사거리·쿨다운·무기 검증). 검 마모 시 개인 인벤 갱신 ──
    private void applyAttack(PlayerAction.Attack attack, long tickNo) {
        PlayerTickState player = rt.players().get(attack.nickname());
        if (player == null || player.isDead()) {
            return;
        }
        int slot = player.inventory().selectedSlot();
        short type = player.inventory().itemType(slot);
        int before = player.inventory().durability(slot);
        CombatSystem.AttackResult result = rt.mobSystem().combat()
                .handleAttackResult(player, attack.mobId(), tickNo, attack.sprinting());
        if (result.accepted()) {
            // SURV-H: 사거리·쿨다운 검증을 통과한 타격만 소모한다(빗나간 휘두르기는 프로토콜에 없다).
            player.addExhaustion(HungerRules.ATTACK_EXHAUSTION_MILLI);
            if (SpearRules.kinetic(type) != null) {
                // [SPEAR-KINETIC] 창 잽은 stabAttack: strong/weak 대신 창 명중음(PiercingWeapon.makeHitSound, 찌르기
                // 한 번에 한 번)을 공격자 위치에서 낸다.
                sendTo(player, CombatHit.stab(rt.nextEventId(), result.x(), result.y(), result.z(),
                        result.mobId(), result.enchanted()));
                Long previous = spearHitSoundTick.put(player.nickname(), tickNo);
                if (previous == null || previous != tickNo) {
                    broadcastWorldSoundExact(SpearRules.soundKind(type, "hit"), player.x(), player.y(), player.z(),
                            (short) 0);
                }
            } else {
                sendTo(player, new CombatHit(rt.nextEventId(), result.critical(),
                        result.x(), result.y(), result.z(), result.mobId(), result.enchanted(),
                        result.sweep()));
            }
            rt.mobSystem().combat().emitHurt(result);
            // [MACE] 받아들여진 낙하 강타의 hurtEnemy·돌풍·postHurtEnemy(소리·넉백·폭발·낙하 초기화).
            if (result.maceSmash() != null) {
                rt.mobSystem().applyMaceSmash(player, result.maceSmash());
            }
        }
        if (result.inventoryChanged()) {
            recordBreakIfGone(player, slot, type, before);
            sendTo(player, inventoryMessage(player));
        }
    }

    /** [ENCHANT-WIDE] 창 찌르기(명중·헛손질). 돌진 인챈트의 내구·허기를 확정한다. */
    private void applySpearStab(PlayerAction.SpearStab stab, long tickNo) {
        PlayerTickState player = rt.players().get(stab.nickname());
        if (player == null || player.isDead()) return;
        int slot = player.inventory().selectedSlot();
        short type = player.inventory().itemType(slot);
        int before = player.inventory().durability(slot);
        boolean riding = rt.boatSystem().isRiding(player.nickname())
                || rt.cushionSystem().isRiding(player.nickname())
                || rt.mobSystem().isSeated(player.nickname());
        if (rt.mobSystem().combat().handleSpearStab(player, tickNo, riding,
                rt.environment().touchingWater(player))) {
            recordBreakIfGone(player, slot, type, before);
            sendTo(player, inventoryMessage(player));
        }
    }

    /** [SPEAR-KINETIC] 창 잽 명중음을 찌르기(권위 틱) 한 번에 한 번만 낸다. */
    private final java.util.Map<String, Long> spearHitSoundTick = new java.util.HashMap<>();

    /** [SPEAR-KINETIC] 창을 쓰는 플레이어 한 명의 돌진 한 틱. 명중은 공격자에게 combatHit(stab) 로 알린다. */
    private void applySpearKinetic(PlayerTickState player, long tickNo) {
        CombatSystem combat = rt.mobSystem().combat();
        PlayerInventory.Hand hand = combat.kineticHand(player.nickname());
        if (hand == null) return;
        PlayerInventory.StackSnapshot before = player.inventory().stack(player.inventory().capture(hand));
        CombatSystem.KineticTick result = rt.mobSystem().tickSpearKinetic(player, tickNo);
        for (CombatSystem.KineticHitMob hit : result.hits()) {
            sendTo(player, CombatHit.stab(rt.nextEventId(), hit.x(), hit.y(), hit.z(), hit.mobId(), hit.enchanted()));
        }
        if (result.inventoryChanged()) {
            recordBreakIfGone(player, player.inventory().capture(hand), before);
            sendTo(player, inventoryMessage(player));
        }
    }

    private void applyBowUse(PlayerAction.BowUse bow, long tickNo) {
        PlayerTickState player = rt.players().get(bow.nickname());
        if (player == null || player.isDead()) return;
        if (bow.cancel()) {
            rt.mobSystem().cancelBowUse(player);
            return;
        }
        PlayerInventory.HandRef hand = player.inventory().capture(inventoryHand(bow.hand()));
        PlayerInventory.StackSnapshot before = player.inventory().stack(hand);
        if (rt.mobSystem().handleBowUse(player, hand, bow.pressed(), tickNo)) {
            recordBreakIfGone(player, player.inventory().capture(hand.hand()), before);
            sendTo(player, inventoryMessage(player));
        }
    }

    private void applyEnderPearlThrow(PlayerAction.EnderPearlThrow throwAction) {
        PlayerTickState player = rt.players().get(throwAction.nickname());
        if (player == null || player.isDead()) return;
        PlayerInventory.HandRef hand = player.inventory().capture(inventoryHand(throwAction.hand()));
        if (rt.mobSystem().handleEnderPearlThrow(player, hand)) {
            sendTo(player, inventoryMessage(player));
        }
    }

    private void applySnowballThrow(PlayerAction.SnowballThrow throwAction) {
        PlayerTickState player = rt.players().get(throwAction.nickname());
        if (player == null || player.isDead()) return;
        PlayerInventory.HandRef hand = player.inventory().capture(inventoryHand(throwAction.hand()));
        if (rt.mobSystem().handleSnowballThrow(player, hand)) {
            sendTo(player, inventoryMessage(player));
        }
    }

    private void applyEggThrow(PlayerAction.EggThrow throwAction) {
        PlayerTickState player = rt.players().get(throwAction.nickname());
        if (player == null || player.isDead()) return;
        PlayerInventory.HandRef hand = player.inventory().capture(inventoryHand(throwAction.hand()));
        if (rt.mobSystem().handleEggThrow(player, hand)) {
            sendTo(player, inventoryMessage(player));
        }
    }

    private void applyPotionThrow(PlayerAction.PotionThrow throwAction) {
        PlayerTickState player = rt.players().get(throwAction.nickname());
        if (player == null || player.isDead()) return;
        PlayerInventory.HandRef hand = player.inventory().capture(inventoryHand(throwAction.hand()));
        if (rt.mobSystem().handlePotionThrow(player, hand)) {
            sendTo(player, inventoryMessage(player));
        }
    }

    /** {@code EnderEyeItem#use}: 가장 가까운 요새 쪽으로 눈 한 개를 날린다. */
    private void applyEnderEyeThrow(PlayerAction.EnderEyeThrow throwAction) {
        PlayerTickState player = rt.players().get(throwAction.nickname());
        if (player == null || player.isDead()) return;
        PlayerInventory.HandRef hand = player.inventory().capture(inventoryHand(throwAction.hand()));
        if (enderEyes.throwEye(player, hand)) {
            sendTo(player, inventoryMessage(player));
        }
    }

    /**
     * {@code EnderEyeItem#useOn}: 눈이 없는 엔드 차원문 틀에 눈 한 개를 끼운다. 이미 눈이 있으면
     * 아무 일도 하지 않는다(바닐라 PASS — 이어지는 {@code use} 도 틀을 겨누면 PASS 다).
     * 끼운 뒤 {@link EndPortalFrameRules#findPortal} 이 12 틀 고리를 찾으면 가운데 3×3 을
     * 부수고(드랍 포함) 엔드 차원문으로 채우며 전역 개방음을 모든 플레이어에게 보낸다.
     */
    private void applyEnderEyeInsert(PlayerTickState player, PlayerInventory.HandRef hand,
            int x, int y, int z) {
        if (!InteractRules.withinReach(player.x(), player.y(), player.z(), player.crouching(),
                x, y, z)) return;
        int state = rt.blockState(x, y, z, Blocks.END_PORTAL_FRAME);
        if (EndPortalFrameRules.hasEye(state)) return;
        if (!InventoryRules.consumeOne(player.inventory(), hand, PlayerInventory.EYE_OF_ENDER)) return;
        rt.setBlockState(x, y, z, Blocks.END_PORTAL_FRAME, state | EndPortalFrameRules.EYE);
        rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) Blocks.END_PORTAL_FRAME);
        sendTo(player, inventoryMessage(player));
        broadcastWorldSound("end_portal_frame_fill", x, y, z, (short) Blocks.END_PORTAL_FRAME);
        EndPortalFrameRules.PortalMatch match = EndPortalFrameRules.findPortal(
                new EndPortalFrameRules.BlockView() {
                    @Override
                    public int blockType(int qx, int qy, int qz) {
                        return residentBlockType(rt.accessor(), qx, qy, qz);
                    }

                    @Override
                    public int state(int qx, int qy, int qz) {
                        return rt.blockState(qx, qy, qz, Blocks.END_PORTAL_FRAME);
                    }
                }, x, y, z);
        if (match == null) return;
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                int px = match.interiorMinX() + dx;
                int py = match.interiorY();
                int pz = match.interiorMinZ() + dz;
                int removed = residentBlockType(rt.accessor(), px, py, pz);
                if (removed == UNAVAILABLE_BLOCK) continue;
                // destroyBlock(pos, true): 공기·유체가 아니면 그 블록의 드랍을 남긴다.
                if (removed != AIR && !isFluid(removed)) {
                    rt.itemSystem().spawnBlockDrop((short) removed, px + 0.5, py + 0.5, pz + 0.5);
                }
                rt.fluidSim().applyChange(px, py, pz, Blocks.END_PORTAL);
                rt.setBlockState(px, py, pz, Blocks.END_PORTAL, 0);
                rt.tickBlockChanges().put(new BlockPos(px, py, pz), (short) Blocks.END_PORTAL);
            }
        }
        // globalLevelEvent(1038): 거리와 무관하게 이 월드의 모든 플레이어에게 간다.
        WorldSound opened = new WorldSound(rt.nextEventId(), "end_portal_spawn",
                match.soundX() + 0.5, match.interiorY() + 0.5, match.soundZ() + 0.5,
                (short) Blocks.END_PORTAL);
        for (PlayerTickState listener : rt.players().values()) sendTo(listener, opened);
    }

    private void applyWindChargeThrow(PlayerAction.WindChargeThrow throwAction) {
        PlayerTickState player = rt.players().get(throwAction.nickname());
        if (player == null || player.isDead()) return;
        PlayerInventory.HandRef hand = player.inventory().capture(inventoryHand(throwAction.hand()));
        if (rt.mobSystem().handleWindChargeThrow(player, hand)) {
            sendTo(player, inventoryMessage(player));
        }
    }

    /**
     * 활공 1초마다 겉날개 내구 1을 소비한다(바닐라 20틱 = 서버 10틱). 내구가 1이 되면 더는
     * 줄지 않고 활공 자격만 사라지므로, 클라이언트가 계속 gliding 을 보내도 다음 pose 부터
     * 권위 활공 상태가 꺼진다.
     */
    private void tickElytraWear(PlayerTickState player) {
        // 사망 중에는 이동 보고가 멈추므로 마지막 활공 사실이 남아 계속 마모될 수 있다.
        if (player.isDead()) player.setGliding(false);
        if (player.advanceGlideWear()) player.inventory().wearEquippedElytra();
        int durability = player.consumeElytraDurabilityChange();
        // [HUD-VANILLA] 방어 점수·흡수도 같은 개인 장비 상태 메시지로 나른다. 셋 중 하나라도
        // 바뀌면 한 번만 보낸다 — 두 변경 검사를 모두 소비해야 커서가 어긋나지 않는다.
        boolean derivedChanged = player.consumeDerivedVitalsChange();
        if (durability < 0 && !derivedChanged) return;
        sendTo(player, new ElytraState(player.elytraDurability(),
                player.inventory().armorPoints(), player.absorptionPoints()));
    }

    /**
     * 활공 벽 충돌 피해. 바닐라 LivingEntity.travel 의 flyIntoWall 과 같은 식
     * {@code damage = lostSpeed × 10 − 3} 을 쓰며, 0 이하이면 피해가 없다.
     * 잃은 속력은 클라 권위 이동에서만 알 수 있으므로 값 자체는 클라가 보고한다.
     */
    private void applyGlideImpact(PlayerAction.GlideImpact impact) {
        PlayerTickState player = rt.players().get(impact.nickname());
        if (player == null || player.isDead() || !player.gliding()) return;
        double lostSpeed = impact.lostSpeed();
        if (!Double.isFinite(lostSpeed) || lostSpeed <= 0) return;
        int damage = (int) Math.floor(Math.min(lostSpeed, GLIDE_IMPACT_MAX_LOST_SPEED) * 10.0 - 3.0);
        if (damage <= 0) return;
        int healthBefore = player.health();
        if (!player.damage(damage, "fly_into_wall")) return;
        int healthAfter = player.health();
        int appliedDamage = Math.max(0, healthBefore - healthAfter);
        if (impact.finalSceneActionId() != null && appliedDamage > 0) {
            String actionId = impact.finalSceneActionId();
            FinalSceneLandingActionLedger ledger = finalSceneLandingActionLedgers.computeIfAbsent(
                    player.nickname(), ignored -> new FinalSceneLandingActionLedger());
            if (ledger.available(actionId)) {
                sendOneShotFeedback(player, "final-scene-flight", actionId,
                        new com.gameexpert.ws.dto.WsMessages.FinalSceneFlightOutcome(
                                actionId, healthBefore, healthAfter, appliedDamage),
                        () -> ledger.burn(actionId));
            }
        }
    }

    /** 보고 가능한 최대 손실 속력(블록/틱). 활공 최고 속도보다 넉넉하며 피해 상한을 유한하게 만든다. */
    private static final double GLIDE_IMPACT_MAX_LOST_SPEED = 4.0;

    /** 선택 칸의 폭죽 로켓 한 개를 쏜다. 활공 중이면 같은 로켓이 바닐라 부스트를 준다. */
    private void applyFireworkUse(PlayerAction.FireworkUse fireworkAction) {
        PlayerTickState player = rt.players().get(fireworkAction.nickname());
        if (player == null || player.isDead()) return;
        PlayerInventory.HandRef hand = player.inventory().capture(inventoryHand(fireworkAction.hand()));
        if (rt.mobSystem().handleFireworkUse(
                player, hand, fireworkAction.finalSceneActionId())) {
            sendTo(player, inventoryMessage(player));
        }
    }

    /**
     * [RAID-REWARD] 전리품 아이템 우클릭. 뿔피리·오르골은 쿨다운만 두고 소모하지 않으며,
     * 꽃잎 주머니는 한 개를 소모한다. 셋 다 체력·허기·전투·경제 상태를 전혀 건드리지 않고
     * 위치 사실 하나(worldSound)만 방송한다. 파티클은 클라이언트가 그 사실에서 파생한다.
     */
    private void applyTrophyItemUse(PlayerAction.TrophyItemUse trophy, long tickNo) {
        PlayerTickState player = rt.players().get(trophy.nickname());
        if (player == null || player.isDead()) return;
        PlayerInventory.Hand hand = inventoryHand(trophy.hand());
        PlayerInventory.StackSnapshot heldStack = player.inventory().stack(hand);
        short type = heldStack.itemType();
        double eyeY = player.y() + PlayerInteractionRules.eyeHeight(player.crouching());
        if (type == PlayerInventory.BATTERING_HORN
                || type == PlayerInventory.ILLAGER_MUSIC_BOX
                || type == PlayerInventory.GOAT_HORN) {
            if (!player.tryUseTrophyInstrument(tickNo)) return;
            broadcastWorldSoundExact(
                    type == PlayerInventory.BATTERING_HORN ? "battering_horn"
                            : type == PlayerInventory.GOAT_HORN ? goatHornSound(heldStack)
                            : "illager_music_box",
                    player.x(), eyeY, player.z(), (short) 0);
            return;
        }
        if (type != PlayerInventory.PETAL_POUCH) return;
        if (!player.inventory().consumeOne(hand, PlayerInventory.PETAL_POUCH)) return;
        broadcastWorldSoundExact("petal_pouch", player.x(), eyeY, player.z(), (short) 0);
        sendTo(player, inventoryMessage(player));
    }

    static String goatHornSound(PlayerInventory.StackSnapshot heldStack) {
        String instrument = heldStack.itemComponents().instrument();
        if (instrument == null) return "goat_horn_ponder";
        return switch (instrument) {
            case "minecraft:ponder_goat_horn" -> "goat_horn_ponder";
            case "minecraft:sing_goat_horn" -> "goat_horn_sing";
            case "minecraft:seek_goat_horn" -> "goat_horn_seek";
            case "minecraft:feel_goat_horn" -> "goat_horn_feel";
            case "minecraft:admire_goat_horn" -> "goat_horn_admire";
            case "minecraft:call_goat_horn" -> "goat_horn_call";
            case "minecraft:yearn_goat_horn" -> "goat_horn_yearn";
            case "minecraft:dream_goat_horn" -> "goat_horn_dream";
            default -> throw new IllegalArgumentException("unsupported goat horn instrument");
        };
    }

    /**
     * 전리품을 건지면 낚싯대가 1, 엔티티를 걸어 끌어당기면 5 닳는다(바닐라 retrieve 의 i).
     * 캐스팅·헛챔질은 내구를 쓰지 않는다.
     */
    private void applyFishingRodUse(PlayerAction.FishingRodUse rod, long tickNo) {
        PlayerTickState player = rt.players().get(rod.nickname());
        if (player == null || player.isDead()) return;
        PlayerInventory.HandRef hand = player.inventory().capture(inventoryHand(rod.hand()));
        PlayerInventory.StackSnapshot before = player.inventory().stack(hand);
        int cost = rt.mobSystem().handleFishingRodUse(player, hand, tickNo);
        if (cost <= 0) return;
        for (int i = 0; i < cost; i++) {
            PlayerInventory.HandRef current = player.inventory().capture(hand.hand());
            player.inventory().degrade(current);
        }
        recordBreakIfGone(player, player.inventory().capture(hand.hand()), before);
        sendTo(player, inventoryMessage(player));
    }

    // 원래 블록값 blockUpdate(롤백) + error 를 송신자에게.
    private void rollbackEdit(PlayerTickState player, PlayerAction.BlockEdit edit, int current) {
        short state = (short) rt.blockState(edit.x(), edit.y(), edit.z(), current);
        rt.advanceOverlayRevisionForCorrection(edit.x(), edit.y(), edit.z(), current, state);
        sendEditRejection(player, edit, current);
    }

    /** 월드 오버레이를 건드리지 않고 요청자의 낙관 반영만 되돌린다. */
    private void sendEditRejection(PlayerTickState player, PlayerAction.BlockEdit edit, int current) {
        // 클라이언트는 설치를 즉시 예측해 선택 아이템을 한 개 줄인다. 서버 거부 때 권위
        // 인벤토리도 함께 보내지 않으면 다음 인벤토리 사건까지 아이템이 사라진 것처럼 보인다.
        sendTo(player, inventoryMessage(player));
        sendTo(player, new com.gameexpert.ws.dto.WsMessages.Error(
                "EDIT_REJECTED",
                edit.kind() == PlayerAction.EditKind.BREAK ? "blockBreak" : "blockPlace",
                edit.x(), edit.y(), edit.z(), current, Short.toUnsignedInt(edit.blockType())));
    }

    // ── [SURV-X] 인챈트 테이블 세션 ──

    /** 블록 파괴 시 인챈트 테이블에 남아 있던 입력 아이템을 모두 쏟습니다. */
    static void dropEnchantingContents(WorldRuntime rt, int x, int y, int z) {
        EnchantingInventory table = rt.enchantingStorage().peekAt(x, y, z);
        EnchantingInventory.Snapshot snapshot = table == null ? null : table.snapshot();
        List<EnchantingInventory.StoredStack> contents = rt.enchantingStorage().removeAt(x, y, z);
        for (EnchantingInventory.StoredStack drained : contents) {
            PlayerInventory.StackSnapshot stack = snapshot.stack(drained.slot());
            rt.itemSystem().spawnDrop(stack.itemType(), stack.count(), stack.durability(),
                    stack.enchantments(), stack.mapId(), stack.shulkerId(),
                    stack.bucketMobData(), stack.itemComponentData(),
                    x + 0.5, y + 1.0, z + 0.5);
        }
        rt.tickLoop().closeEnchantingViewers(x, y, z);
    }

    /** 좌표가 더 이상 인챈트 테이블이 아니면 열어 둔 모든 세션을 닫습니다. */
    private void closeEnchantingViewers(int x, int y, int z) {
        for (PlayerTickState viewer : rt.players().values()) {
            if (samePosition(viewer.openEnchanting(), x, y, z)) closeEnchantingSession(viewer);
        }
    }

    private void applyEnchantClick(PlayerAction.EnchantClick click) {
        PlayerTickState player = rt.players().get(click.nickname());
        EnchantingInventory table = validatedEnchantingTable(player, click.x(), click.y(), click.z());
        if (table == null) return;
        long tableRevision = table.persistenceRevision();
        PlayerInventory inventory = player.inventory();
        boolean changed = inventory.clickEnchanting(
                table, click.area(), click.slot(), click.button(), click.shift());
        if (!changed) return;
        if (table.persistenceRevision() != tableRevision) {
            rt.enchantingStorage().markDirty(click.x(), click.y(), click.z());
        }
        sendTo(player, inventoryMessage(player));
        broadcastEnchantingUpdate(click.x(), click.y(), click.z(), table);
    }

    private void applyEnchantDrag(PlayerAction.EnchantDrag drag) {
        PlayerTickState player = rt.players().get(drag.nickname());
        EnchantingInventory table = validatedEnchantingTable(
                player, drag.x(), drag.y(), drag.z());
        if (table == null) return;
        long tableRevision = table.persistenceRevision();
        if (!player.inventory().dragEnchanting(
                table, drag.areas(), drag.slots(), drag.button())) return;
        if (table.persistenceRevision() != tableRevision) {
            rt.enchantingStorage().markDirty(drag.x(), drag.y(), drag.z());
        }
        sendTo(player, inventoryMessage(player));
        broadcastEnchantingUpdate(drag.x(), drag.y(), drag.z(), table);
    }

    private void applyCollectEnchant(PlayerAction.CollectEnchant collect) {
        PlayerTickState player = rt.players().get(collect.nickname());
        EnchantingInventory table = validatedEnchantingTable(
                player, collect.x(), collect.y(), collect.z());
        if (table == null) return;
        long tableRevision = table.persistenceRevision();
        if (collect.slot() < 0 || collect.slot() >= (collect.area()
                == PlayerAction.EnchantArea.INVENTORY
                        ? PlayerInventory.SLOTS : EnchantingInventory.SLOTS)
                || !player.inventory().collectEnchanting(table)) return;
        if (table.persistenceRevision() != tableRevision) {
            rt.enchantingStorage().markDirty(collect.x(), collect.y(), collect.z());
        }
        sendTo(player, inventoryMessage(player));
        broadcastEnchantingUpdate(collect.x(), collect.y(), collect.z(), table);
    }

    /** 제안 수락: 지불 가능 여부를 서버가 다시 검증한 뒤에만 레벨·청금석을 깎고 마스크를 씁니다. */
    private void applySelectEnchantOffer(PlayerAction.SelectEnchantOffer select) {
        PlayerTickState player = rt.players().get(select.nickname());
        EnchantingInventory table =
                validatedEnchantingTable(player, select.x(), select.y(), select.z());
        if (table == null) return;
        int slot = select.offer();
        if (slot < 0 || slot >= EnchantmentRules.ENCHANT_OFFER_COUNT) return;
        short itemType = table.itemType(EnchantingInventory.ITEM_SLOT);
        if (!EnchantmentRules.isTableEnchantable(itemType)) return;
        if (!table.wideEnchantments(EnchantingInventory.ITEM_SLOT).isEmpty()) return;
        int lapis = table.count(EnchantingInventory.LAPIS_SLOT);
        int bookshelves = countBookshelvesAt(select.x(), select.y(), select.z());
        EnchantingTableRules.Offer[] offers = EnchantingTableRules.buildOffers(
                itemType, lapis, bookshelves, player.xpLevel(), player.enchantSeed());
        EnchantingTableRules.Offer offer = offers[slot];
        // 클라이언트 표시를 믿지 않는다. 레벨·청금석·마스크를 서버가 다시 확인한다.
        if (!offer.affordable()) return;
        if (!table.applyEnchantments(offer.enchantments())) return;
        table.take(EnchantingInventory.LAPIS_SLOT, offer.lapisCost());
        rt.enchantingStorage().markDirty(select.x(), select.y(), select.z());
        player.setXpTotal(XpRules.totalAfterSpendingLevels(
                player.xpTotal(), EnchantmentRules.offerLevelsSpent(slot)));
        player.reshuffleEnchantSeed(rt.nextEnchantSeed());
        sendTo(player, inventoryMessage(player));
        broadcastEnchantingUpdate(select.x(), select.y(), select.z(), table);
        // [SURV-X] 인챈트 확정음. 새 사운드 키를 만들지 않고 정적판과 같은 기존 키를 재사용한다.
        broadcastWorldSound("block_place", select.x(), select.y(), select.z(),
                (short) Blocks.ENCHANTING_TABLE);
    }

    private void applyDropEnchantCursor(PlayerAction.DropEnchantCursor drop) {
        PlayerTickState player = rt.players().get(drop.nickname());
        if (player == null || !samePosition(
                player.openEnchanting(), drop.x(), drop.y(), drop.z())) return;
        EnchantingInventory table =
                rt.enchantingStorage().openAt(drop.x(), drop.y(), drop.z());
        settleCursorGroundDrop(player, false, drop.one(), () ->
                sendTo(player, enchantingUpdate(player, drop.x(), drop.y(), drop.z(), table)));
    }

    private void applyCloseEnchanting(PlayerAction.CloseEnchanting close) {
        PlayerTickState player = rt.players().get(close.nickname());
        if (player != null
                && samePosition(player.openEnchanting(), close.x(), close.y(), close.z())) {
            closeEnchantingSession(player);
        } else if (player != null) {
            sendTo(player, new EnchantingClosed(close.x(), close.y(), close.z()));
        }
    }

    private void closeEnchantingSession(PlayerTickState player) {
        BlockPos pos = player.openEnchanting();
        if (pos == null) return;
        dropCraftingOverflow(player, player.inventory().closeContainerCursor());
        player.closeEnchanting();
        sendTo(player, inventoryMessage(player));
        sendTo(player, new EnchantingClosed(pos.x(), pos.y(), pos.z()));
        // openAt 은 열기·클릭·커서 드롭마다 항목을 만든다. 빈 채로 닫힌 좌표까지 남겨 두면
        // 한 번이라도 쳐다본 테이블마다 항목이 영구 잔류한다(FurnaceStorage 와 같은 정리 규약).
        rt.enchantingStorage().discardIfEmpty(pos.x(), pos.y(), pos.z());
    }

    /** 열린 좌표·블록 종류·리치를 한 번에 확인합니다. 실패하면 세션을 닫고 null 을 돌려줍니다. */
    private EnchantingInventory validatedEnchantingTable(
            PlayerTickState player, int x, int y, int z) {
        if (player == null || !samePosition(player.openEnchanting(), x, y, z)) return null;
        if (residentBlockType(rt.accessor(), x, y, z) != Blocks.ENCHANTING_TABLE
                || !InteractRules.withinContainerReach(
                        player.x(), player.y(), player.z(), player.crouching(), x, y, z)) {
            closeEnchantingSession(player);
            return null;
        }
        return rt.enchantingStorage().openAt(x, y, z);
    }

    private int countBookshelvesAt(int x, int y, int z) {
        return EnchantingTableRules.countBookshelves(
                (bx, by, bz) -> {
                    int block = residentBlockType(rt.accessor(), bx, by, bz);
                    return block == UNAVAILABLE_BLOCK ? Blocks.AIR : block;
                }, x, y, z);
    }

    private void broadcastEnchantingUpdate(int x, int y, int z, EnchantingInventory table) {
        for (PlayerTickState viewer : rt.players().values()) {
            if (samePosition(viewer.openEnchanting(), x, y, z)) {
                sendTo(viewer, enchantingUpdate(viewer, x, y, z, table));
            }
        }
    }

    private EnchantingOpen enchantingOpen(
            PlayerTickState player, int x, int y, int z, EnchantingInventory table) {
        int bookshelves = countBookshelvesAt(x, y, z);
        return new EnchantingOpen(x, y, z, enchantingSlots(table),
                enchantingCursor(player.inventory()),
                enchantingOffers(player, table, bookshelves), bookshelves, player.xpTotal());
    }

    private EnchantingUpdate enchantingUpdate(
            PlayerTickState player, int x, int y, int z, EnchantingInventory table) {
        int bookshelves = countBookshelvesAt(x, y, z);
        return new EnchantingUpdate(x, y, z, enchantingSlots(table),
                enchantingCursor(player.inventory()),
                enchantingOffers(player, table, bookshelves), bookshelves, player.xpTotal());
    }

    private static CraftingStack enchantingCursor(PlayerInventory inventory) {
        return craftingStack(inventory.cursorType(), inventory.cursorCount(),
                inventory.cursorDurability(), inventory.cursorEnchantments(), inventory.cursorMapId(),
                inventory.cursorShulkerId(), inventory.cursorBucketMobData(),
                inventory.cursorItemComponentData());
    }

    private static List<EnchantOfferDto> enchantingOffers(
            PlayerTickState player, EnchantingInventory table, int bookshelves) {
        short itemType = table.itemType(EnchantingInventory.ITEM_SLOT);
        // 이미 인챈트된 아이템은 다시 제안하지 않는다(바닐라 인챈트 테이블과 동일).
        if (!table.wideEnchantments(EnchantingInventory.ITEM_SLOT).isEmpty()) {
            itemType = PlayerInventory.EMPTY;
        }
        EnchantingTableRules.Offer[] offers = EnchantingTableRules.buildOffers(
                itemType, table.count(EnchantingInventory.LAPIS_SLOT), bookshelves,
                player.xpLevel(), player.enchantSeed());
        List<EnchantOfferDto> dtos = new ArrayList<>(offers.length);
        for (EnchantingTableRules.Offer offer : offers) {
            dtos.add(new EnchantOfferDto(offer.levelCost(), offer.lapisCost(),
                    offer.enchantments(), offer.affordable()));
        }
        return List.copyOf(dtos);
    }

    private static List<InventorySlot> enchantingSlots(EnchantingInventory table) {
        List<InventorySlot> slots = new ArrayList<>(EnchantingInventory.SLOTS);
        for (int slot = 0; slot < EnchantingInventory.SLOTS; slot++) {
            short type = table.itemType(slot);
            Integer durability = PlayerInventory.isDurable(type) ? table.durability(slot) : null;
            PlayerInventory.StackSnapshot stack = table.stack(slot);
            slots.add(inventorySlot(slot, type, stack.count(), durability, stack.enchantments(),
                    mapIdOrNull(stack.mapId()), shulkerIdOrNull(stack.shulkerId()),
                    stack.bucketMobData(), stack.itemComponentData()));
        }
        return slots;
    }

    /**
     * QA 시딩(디버그 클라이언트 + {@code game.qa-seeding=true} 서버 조합에서만 도달한다).
     *
     * <p>권위 상태를 직접 조립하지 않고 실제 게임플레이가 쓰는 API 만 호출한다: 인벤토리는
     * {@link PlayerInventory#addItem}, 몹은 몹 런타임 스폰, 시각은 월드 시계. 그래서 시딩으로
     * 만들어진 상태도 평소 플레이로 만들어진 상태와 구분되지 않으며 QA 결과가 유효하다.</p>
     */
    private void applyQaSeed(PlayerTickState player, PlayerAction.QaSeed seed, long tickNo) {
        switch (seed.command()) {
            case "ecologyAuditStage" -> applyQaEcologyBoundary(seed);
            case "auditStage" -> {
                if (!seed.fixtureAuthorized() || !rt.ctx().properties().qaSeeding()
                        || rt.seed() != (int) com.gameexpert.config.EngineProperties.CONTENT_QA_WORLD_SEED
                        || !"copper-next".equals(seed.argument())
                        || seed.x() != 1 || seed.y() != 72 || seed.z() != 25
                        || copperSettlementCells.contains(1, 72, 25)
                        || copperSettlementCells.contains(2, 72, 25)) return;
                randomTicks.qaAdvanceCopperChestPair(seed.amount());
            }
            case "give" -> {
                // QA 관측용 확장: `id:mask` 면 인챈트 마스크를 함께 싣는다(저주 보존 검증 등).
                // [ENCHANT-WIDE] `id:h<hex>` 는 와이어와 같은 정규 16진 43종 집합이다.
                String[] giveParts = seed.argument().split(":", 2);
                short itemType = (short) Integer.parseInt(giveParts[0]);
                com.gameexpert.engine.enchant.WideEnchantments giveEnchantments =
                        giveParts.length < 2 ? com.gameexpert.engine.enchant.WideEnchantments.EMPTY
                        : giveParts[1].startsWith("h")
                                ? com.gameexpert.engine.enchant.WideEnchantments.fromHex(
                                        giveParts[1].substring(1))
                                : com.gameexpert.engine.enchant.WideEnchantments.legacy(
                                        Long.parseLong(giveParts[1]));
                int amount = Math.max(1, Math.min(seed.amount(), 2304));
                int added = giveEnchantments.isEmpty()
                        ? player.inventory().addItem(itemType, amount)
                        : player.inventory().addItem(itemType, amount,
                                PlayerInventory.initialDurability(itemType),
                                giveEnchantments.word0(), 0, 0, null,
                                ItemComponentCodec.withEnchantments(
                                        itemType, null, giveEnchantments));
                if (added > 0) {
                    rt.queuePlayerInventoryBaseline(player);
                }
                sendTo(player, inventoryMessage(player));
            }
            case "spawn" -> {
                MobType type = MobType.valueOf(seed.argument().toUpperCase(Locale.ROOT));
                int count = Math.max(1, Math.min(seed.amount(), 32));
                for (int i = 0; i < count; i++) {
                    rt.mobSystem().qaSpawnMob(type, seed.x(), seed.y(), seed.z());
                }
            }
            case "time" -> rt.clock().setWorldTime(seed.amount());
            // [QA5-1] 날씨. clear 지속만 6000~36000틱이라 비·번개 경로(젖음 파티클·낙뢰·엔더맨
            // 회피·번개 변신)는 한 회차 QA 로 도달하지 못했다. 권위 상태 머신을 그대로 전이시키고
            // 지속을 다시 뽑으므로, 시딩으로 만든 날씨와 평소 굴러온 날씨는 구분되지 않는다.
            case "weather" -> rt.weatherSystem()
                    .qaForceWeather(seed.argument().toLowerCase(Locale.ROOT), tickNo);
            case "fixture" -> {
                if (!seed.fixtureAuthorized()
                        || !com.gameexpert.engine.qa.ContentQaFixturePlan.FIXTURE_ID
                                .equals(seed.argument())) return;
                int x = exactFixtureAnchor(seed.x());
                int y = exactFixtureAnchor(seed.y());
                int z = exactFixtureAnchor(seed.z());
                var plan = com.gameexpert.engine.qa.ContentQaFixturePlan.create(x, y, z);
                WorldRuntime.ContentQaFixtureOutcome outcome =
                        rt.applyContentQaFixture(plan, player);
                if (outcome != WorldRuntime.ContentQaFixtureOutcome.REJECTED) {
                    refreshFinalSceneDerivedShapes(plan);
                }
                if (outcome == WorldRuntime.ContentQaFixtureOutcome.APPLIED) {
                    sendTo(player, inventoryMessage(player));
                }
                if (seed.finalSceneScenario() == null || seed.finalSceneActionNonce() == null
                        || outcome == WorldRuntime.ContentQaFixtureOutcome.REJECTED
                        || pendingFinalScenePrerequisites.containsKey(player.nickname())) return;
                Set<String> nonces = finalScenePrerequisiteNonces.computeIfAbsent(
                        player.nickname(), ignored -> new HashSet<>());
                OneShotFeedbackKey prerequisiteKey = new OneShotFeedbackKey(player.nickname(),
                        "final-scene-prerequisite", seed.finalSceneActionNonce());
                if (nonces.size() >= 256 || nonces.contains(seed.finalSceneActionNonce())
                        || pendingOneShotFeedback.containsKey(prerequisiteKey)) return;
                if (!restoreFinalScenePrerequisiteInventory(player.inventory())) return;
                if (!rt.mobSystem().qaArmFinalSceneRaid(plan, tickNo, player.nickname(),
                        (int) FINAL_SCENE_PREREQUISITE_TIMEOUT_TICKS)) return;
                selectFinalScenePrerequisiteItem(player, seed.finalSceneScenario());
                pendingFinalScenePrerequisites.put(player.nickname(),
                        new PendingFinalScenePrerequisites(plan, player,
                                seed.finalSceneScenario(), seed.finalSceneActionNonce(),
                                tickNo + FINAL_SCENE_PREREQUISITE_TIMEOUT_TICKS, 0));
                sendTo(player, inventoryMessage(player));
            }
            default -> throw new IllegalArgumentException("알 수 없는 QA 시딩 명령: " + seed.command());
        }
    }

    static boolean restoreFinalScenePrerequisiteInventory(PlayerInventory inventory) {
        PlayerInventory.CompletePersistenceSnapshot source = inventory.acquireSettlementLease();
        if (source == null) return false;
        PlayerInventory planned = source.detachedInventory();
        if (!planned.elytraFlyable()) {
            if (planned.addItem(PlayerInventory.ELYTRA, 1) != 1) {
                inventory.releaseSettlementLease(source);
                return false;
            }
            int elytraSlot = -1;
            for (int slot = 0; slot < PlayerInventory.SLOTS; slot++) {
                if (planned.itemType(slot) == PlayerInventory.ELYTRA
                        && planned.durability(slot) >= PlayerInventory.ELYTRA_MIN_FLYABLE_DURABILITY) {
                    elytraSlot = slot;
                    break;
                }
            }
            if (elytraSlot < 0 || !planned.equip(elytraSlot)) {
                inventory.releaseSettlementLease(source);
                return false;
            }
        }
        for (short required : new short[] { PlayerInventory.FIREWORK_ROCKET_1,
                PlayerInventory.FISHING_ROD, PlayerInventory.DIAMOND_PICKAXE }) {
            int amount = required == PlayerInventory.FIREWORK_ROCKET_1 ? 16 : 1;
            if (!inventoryContains(planned, required)
                    && planned.addItem(required, amount) != amount) {
                inventory.releaseSettlementLease(source);
                return false;
            }
        }
        PlayerInventory.CompletePersistenceSnapshot committed =
                planned.completePersistenceSnapshot();
        if (committed.revision() == source.revision()) {
            return inventory.releaseSettlementLease(source);
        }
        boolean installed = inventory.installCommittedSettlement(source, committed);
        if (!installed) inventory.releaseSettlementLease(source);
        return installed;
    }

    private void refreshFinalSceneDerivedShapes(ContentQaFixturePlan plan) {
        ContentQaFixturePlan.Bounds bounds = plan.bounds();
        int ax = bounds.anchorX();
        int y = bounds.floorY() + 1;
        int z = bounds.anchorZ() + 28;
        refreshDerivedStatesAround(List.of(
                new BlockPos(ax + 1, y, z), new BlockPos(ax + 2, y, z),
                new BlockPos(ax + 4, y, z), new BlockPos(ax + 5, y, z),
                new BlockPos(ax + 7, y, z), new BlockPos(ax + 8, y, z),
                new BlockPos(ax + 10, y, z), new BlockPos(ax + 11, y, z),
                new BlockPos(ax + 10, y, z + 1), new BlockPos(ax + 11, y, z + 1)));
    }

    private void selectFinalScenePrerequisiteItem(PlayerTickState player, String scenario) {
        short required = switch (scenario) {
            case "H12b" -> PlayerInventory.DIAMOND_PICKAXE;
            case "H12c-firework" -> PlayerInventory.FIREWORK_ROCKET_1;
            case "H12d-visual", "H12d-feedback" -> PlayerInventory.FISHING_ROD;
            default -> PlayerInventory.EMPTY;
        };
        if (required != PlayerInventory.EMPTY) {
            for (int slot = 0; slot < PlayerInventory.HOTBAR_SLOTS; slot++) {
                if (player.inventory().itemType(slot) == required) {
                    player.inventory().select(slot);
                    break;
                }
            }
        }
    }

    private void publishReadyFinalScenePrerequisites() {
        if (pendingFinalScenePrerequisites.isEmpty()) return;
        var iterator = pendingFinalScenePrerequisites.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            PendingFinalScenePrerequisites pending = entry.getValue();
            PlayerTickState currentPlayer = rt.players().get(pending.player().nickname());
            if (rt.tickNo() >= pending.deadlineTick() || currentPlayer == null) {
                rt.cancelGeneratedFinalSceneEvidence(pending.player().nickname(),
                        pending.scenario(), pending.actionNonce());
                iterator.remove();
                continue;
            }
            if (currentPlayer != pending.player()) {
                pending = new PendingFinalScenePrerequisites(pending.plan(), currentPlayer,
                        pending.scenario(), pending.actionNonce(), pending.deadlineTick(),
                        pending.observedRaidRoleMask());
                entry.setValue(pending);
            }
            ContentQaFixturePlan plan = pending.plan();
            ContentQaFixturePlan.Bounds bounds = plan.bounds();
            int ax = bounds.anchorX();
            int y = bounds.floorY();
            int az = bounds.anchorZ();
            MobSystem.FinalSceneRaidFacts raid = rt.mobSystem().qaFinalSceneRaidFacts();
            int observedRaidRoleMask = pending.observedRaidRoleMask()
                    | raid.requiredRoleMask();
            if (observedRaidRoleMask != pending.observedRaidRoleMask()) {
                pending = new PendingFinalScenePrerequisites(pending.plan(), pending.player(),
                        pending.scenario(), pending.actionNonce(), pending.deadlineTick(),
                        observedRaidRoleMask);
                entry.setValue(pending);
            }
            boolean rewards = residentBlockType(rt.accessor(), ax + 1, y + 2, az + 16)
                    == Blocks.TATTERED_BANNER
                    && residentBlockType(rt.accessor(), ax + 3, y + 2, az + 16)
                    == Blocks.CHERRY_BONSAI;
            boolean flight = pending.player().inventory().equippedType(
                    com.gameexpert.engine.inventory.ArmorSlot.CHESTPLATE) == PlayerInventory.ELYTRA
                    && inventoryContains(pending.player().inventory(), PlayerInventory.FIREWORK_ROCKET_1);
            boolean fishing = inventoryContains(pending.player().inventory(), PlayerInventory.FISHING_ROD)
                    && residentBlockType(rt.accessor(), ax + 18, y + 2, az + 22)
                            == Blocks.WATER_SOURCE;
            boolean shapes = finalSceneConnectedShapesReady(plan, stateLookup());
            int observedRaidRoleCount = Integer.bitCount(observedRaidRoleMask
                    & MobSystem.FINAL_SCENE_REQUIRED_ROLE_MASK);
            if (!raid.active() || observedRaidRoleCount != 5 || !raid.bossbarVisible()
                    || !rewards || !flight || !fishing || !shapes) continue;
            // H12f/H12g are schema-2 only.  Never downgrade them to the fixture-derived v1
            // receipt: publication waits for committed semantic, terminal, unload/reload and
            // reconnect evidence plus Runtime's authenticated natural binding.
            if ("H12f".equals(pending.scenario()) || "H12g".equals(pending.scenario())) {
                if (finalScenePrerequisiteRevision == Long.MAX_VALUE) {
                    rt.cancelGeneratedFinalSceneEvidence(pending.player().nickname(),
                            pending.scenario(), pending.actionNonce());
                    iterator.remove();
                    continue;
                }
                long nextRevision = finalScenePrerequisiteRevision + 1L;
                var generated = rt.freezeGeneratedFinalSceneReceipt(
                        pending.player().nickname(), pending.scenario(),
                        new GeneratedFinalSceneEvidenceTracker.ReceiptHeader(
                                Long.toUnsignedString(plan.checksum()), "spring",
                                com.gameexpert.config.EngineProperties.CONTENT_QA_WORLD_NAME,
                                pending.player().nickname(), pending.actionNonce(), nextRevision,
                                observedRaidRoleCount));
                if (generated.isEmpty()) continue;
                finalScenePrerequisiteRevision = nextRevision;
                PendingFinalScenePrerequisites exactPending = pending;
                sendOneShotFeedback(pending.player(), "final-scene-prerequisite",
                        pending.actionNonce(), generated.get(),
                        () -> finalScenePrerequisiteNonces.computeIfAbsent(
                                exactPending.player().nickname(), ignored -> new HashSet<>())
                                .add(exactPending.actionNonce()));
                iterator.remove();
                continue;
            }
            if (finalScenePrerequisiteRevision == Long.MAX_VALUE) {
                iterator.remove();
                continue;
            }
            finalScenePrerequisiteRevision++;
            PendingFinalScenePrerequisites exactPending = pending;
            sendOneShotFeedback(pending.player(), "final-scene-prerequisite",
                    pending.actionNonce(),
                    new com.gameexpert.ws.dto.WsMessages.FinalScenePrerequisiteReceipt(
                            Long.toUnsignedString(plan.checksum()), pending.player().nickname(),
                            pending.scenario(), pending.actionNonce(),
                            finalScenePrerequisiteRevision, raid.active(), observedRaidRoleCount,
                            raid.bossbarVisible(), rewards, flight, fishing, shapes),
                    () -> finalScenePrerequisiteNonces.computeIfAbsent(
                            exactPending.player().nickname(), ignored -> new HashSet<>())
                            .add(exactPending.actionNonce()));
            iterator.remove();
        }
    }

    static boolean finalScenePrerequisiteCurrent(
            Object expectedConnection, Object currentConnection, long tickNo, long deadlineTick) {
        return expectedConnection != null && expectedConnection == currentConnection
                && tickNo < deadlineTick;
    }

    static boolean finalSceneConnectedShapesReady(
            ContentQaFixturePlan plan, BuildingBlockRules.StateLookup world) {
        ContentQaFixturePlan.Bounds bounds = plan.bounds();
        int ax = bounds.anchorX();
        int y = bounds.floorY() + 1;
        int z = bounds.anchorZ() + 28;
        if (!derivedReciprocalPairReady(world, Blocks.WOOD_FENCE, ax + 1, ax + 2, y, z)
                || !derivedReciprocalPairReady(world, Blocks.COBBLE_WALL, ax + 4, ax + 5, y, z)
                || !derivedReciprocalPairReady(world, Blocks.GLASS_PANE, ax + 7, ax + 8, y, z)) {
            return false;
        }
        for (int stairZ = z; stairZ <= z + 1; stairZ++) {
            for (int stairX = ax + 10; stairX <= ax + 11; stairX++) {
                if (world.block(stairX, y, stairZ) != Blocks.WOOD_STAIRS) return false;
                int state = world.state(stairX, y, stairZ, Blocks.WOOD_STAIRS);
                int shape = BuildingBlockRules.stairShape(stairX, y, stairZ, state, world);
                if (shape == BuildingBlockRules.STAIR_STRAIGHT
                        || state != ((state & 0x07)
                                | shape << BuildingBlockRules.STAIR_SHAPE_SHIFT)) return false;
            }
        }
        return true;
    }

    private static boolean derivedReciprocalPairReady(BuildingBlockRules.StateLookup world,
            int blockId, int firstX, int secondX, int y, int z) {
        if (world.block(firstX, y, z) != blockId || world.block(secondX, y, z) != blockId) {
            return false;
        }
        int firstExpected = BuildingBlockRules.connectionMask(blockId, firstX, y, z, world);
        int secondExpected = BuildingBlockRules.connectionMask(blockId, secondX, y, z, world);
        int first = world.state(firstX, y, z, blockId);
        int second = world.state(secondX, y, z, blockId);
        return firstExpected != 0 && secondExpected != 0
                && first == firstExpected && second == secondExpected;
    }

    private static boolean inventoryContains(PlayerInventory inventory, short itemType) {
        for (int slot = 0; slot < PlayerInventory.SLOTS; slot++) {
            if (inventory.itemType(slot) == itemType && inventory.count(slot) > 0) return true;
        }
        return false;
    }

    /** Installs and arms only the exact plan already authorized by the reserved-world WS gate. */
    private void applyQaPerformanceFixture(PlayerAction.QaPerformanceFixture request) {
        if (!rt.ctx().properties().qaSeeding()
                || rt.seed() != (int) DenseWorldPerformanceFixturePlan.WORLD_SEED) return;
        DenseWorldPerformanceFixturePlan plan = request.plan();
        if (plan == null) return;
        if (request.operation() == PlayerAction.PerformanceFixtureOperation.INSTALL) {
            installPerformanceFixture(plan);
            return;
        }
        String refusal = performanceWindowStartRefusal(plan);
        if (refusal != null) {
            // A silently refused arm is indistinguishable from a hung measurement: the harness waits for a
            // DENSE_WORLD_PERFORMANCE_RESULT line that can never be emitted. Name the unmet precondition.
            log.info("DENSE_WORLD_PERFORMANCE_START_REFUSED world={} reason={}", rt.worldId(), refusal);
            return;
        }
        performanceObservation = new OwnerTurnPerformanceWindow.MutableTurnObservation();
        performanceWindow = new OwnerTurnPerformanceWindow(
                OwnerTurnPerformanceWindow.ExpectedWorkload.fromPlan(plan));
        performanceNextStartNanos = 0L;
        lastPerformanceResult = null;
    }

    /** Returns the first unmet arming precondition, or null when the window may be armed. */
    private String performanceWindowStartRefusal(DenseWorldPerformanceFixturePlan plan) {
        if (!matchesInstalledPerformanceFixture(plan)) return "fixture-not-installed";
        if (performanceWindow != null) return "window-already-armed";
        if (lastRandomTickStats == null) return "no-random-tick-stats";
        if (lastRandomTickStats.activeChunks()
                != DenseWorldPerformanceFixturePlan.EXPECTED_RANDOM_TICK_CHUNKS) {
            return "random-tick-chunks=" + lastRandomTickStats.activeChunks()
                    + " expected=" + DenseWorldPerformanceFixturePlan.EXPECTED_RANDOM_TICK_CHUNKS;
        }
        if (lastRandomTickStats.samples()
                != DenseWorldPerformanceFixturePlan.EXPECTED_RANDOM_TICK_SAMPLES) {
            return "random-tick-samples=" + lastRandomTickStats.samples()
                    + " expected=" + DenseWorldPerformanceFixturePlan.EXPECTED_RANDOM_TICK_SAMPLES;
        }
        int liveFixtureMobs = rt.mobSystem().qaLiveFixtureMobCount(performanceFixtureMobIds);
        if (liveFixtureMobs != plan.expectedFixtureMobCount()) {
            return "fixture-mobs=" + liveFixtureMobs
                    + " expected=" + plan.expectedFixtureMobCount();
        }
        int activeMobs = rt.mobSystem().currentActiveMobCount();
        if (activeMobs < plan.expectedFixtureMobCount()) {
            return "active-mobs=" + activeMobs + " expected>=" + plan.expectedFixtureMobCount();
        }
        return null;
    }

    /**
     * The reserved world is ephemeral QA state. Repeating the exact request reuses its real runtime
     * mob identities; a different plan is refused so variants can never contaminate one another.
     */
    private boolean installPerformanceFixture(DenseWorldPerformanceFixturePlan plan) {
        if (installedPerformancePlan != null) {
            return matchesInstalledPerformanceFixture(plan);
        }
        rt.installDensePerformanceFixtureCells(plan);
        long[] ids = new long[plan.mobRequests().size()];
        for (DenseWorldPerformanceFixturePlan.MobRequest mob : plan.mobRequests()) {
            ids[mob.ordinal()] = rt.mobSystem().qaSpawnEphemeralFixtureMob(
                    mob.type(), mob.x(), mob.y(), mob.z());
        }
        performanceFixtureMobIds = ids;
        installedPerformancePlan = plan;
        rt.mobSystem().qaPublishCommittedFixtureSnapshot();
        return true;
    }

    private boolean matchesInstalledPerformanceFixture(DenseWorldPerformanceFixturePlan plan) {
        return installedPerformancePlan != null
                && installedPerformancePlan.checksum() == plan.checksum()
                && installedPerformancePlan.variant() == plan.variant()
                && installedPerformancePlan.bounds().equals(plan.bounds());
    }

    private static int exactFixtureAnchor(double value) {
        if (!Double.isFinite(value) || value != Math.rint(value)
                || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("fixture anchor must be an exact int");
        }
        return (int) value;
    }

    static InventoryUpdate inventoryMessage(PlayerTickState player) {
        PlayerInventory inv = player.inventory();
        return new InventoryUpdate(inventorySlots(inv), armorSlots(inv), offhandSlot(inv),
                inv.selectedSlot());
    }

    private static List<InventorySlot> armorSlots(PlayerInventory inv) {
        List<InventorySlot> slots = new ArrayList<>(ArmorSlot.values().length);
        for (ArmorSlot armor : ArmorSlot.values()) {
            PlayerInventory.StackSnapshot stack = inv.equippedStack(armor);
            short type = stack.itemType();
            slots.add(inventorySlot(PlayerInventory.EQUIPPED_SLOT_BASE + armor.ordinal(),
                    type, stack.count(),
                    type == PlayerInventory.EMPTY || !PlayerInventory.isDurable(type)
                            ? null : stack.durability(),
                    stack.enchantments(), null, null, null, stack.itemComponentData()));
        }
        return List.copyOf(slots);
    }

    private static InventorySlot offhandSlot(PlayerInventory inv) {
        PlayerInventory.StackSnapshot offhand = inv.offhand();
        return inventorySlot(PlayerInventory.OFFHAND_INVENTORY_SLOT,
                offhand.itemType(), offhand.count(),
                offhand.isEmpty() || !PlayerInventory.isDurable(offhand.itemType())
                        ? null : offhand.durability(),
                offhand.enchantments(),
                offhand.mapId() == 0 ? null : offhand.mapId(),
                offhand.shulkerId() == 0 ? null : offhand.shulkerId(),
                offhand.bucketMobData(), offhand.itemComponentData());
    }

    private static List<InventorySlot> inventorySlots(PlayerInventory inv) {
        List<InventorySlot> slots = new ArrayList<>(PlayerInventory.SLOTS);
        for (int slot = 0; slot < PlayerInventory.SLOTS; slot++) {
            Integer durability = inv.isDurableSlot(slot) ? inv.durability(slot) : null;
            slots.add(inventorySlot(slot, inv.itemType(slot), inv.count(slot), durability,
                    inv.enchantments(slot), mapIdOrNull(inv.mapId(slot)),
                    // [SHULKER-CONTENTS] 정적판 Protocol.InventorySlot.shulkerId 와 같은 필드다.
                    shulkerIdOrNull(inv.shulkerId(slot)), inv.bucketMobData(slot),
                    inv.itemComponentData(slot)));
        }
        return slots;
    }

    /** [SURV-X] 마스크 0 은 프로토콜에서 생략한다(클라이언트는 없으면 0으로 읽는다). */
    private static Long maskOrNull(long enchantments) {
        return enchantments == EnchantmentRules.EMPTY_ENCHANTMENTS ? null : enchantments;
    }

    private static Integer mapIdOrNull(int mapId) {
        return mapId == 0 ? null : mapId;
    }

    /** [SHULKER-CONTENTS] 참조 0(내용 없음)은 지도와 같이 null 로 싣는다. */
    private static Integer shulkerIdOrNull(int shulkerId) {
        return shulkerId == 0 ? null : shulkerId;
    }

    private static CraftingStack craftingStack(
            short itemType, int count, int durability) {
        return craftingStack(
                itemType, count, durability, EnchantmentRules.EMPTY_ENCHANTMENTS, 0, 0);
    }

    private static CraftingStack craftingStack(
            short itemType, int count, int durability, long enchantments) {
        return craftingStack(itemType, count, durability, enchantments, 0, 0);
    }

    private static CraftingStack craftingStack(
            short itemType, int count, int durability, long enchantments, int mapId) {
        return craftingStack(itemType, count, durability, enchantments, mapId, 0);
    }

    private static CraftingStack craftingStack(
            short itemType, int count, int durability, long enchantments, int mapId,
            int shulkerId) {
        return craftingStack(itemType, count, durability, enchantments, mapId, shulkerId, null);
    }

    private static CraftingStack craftingStack(
            short itemType, int count, int durability, long enchantments, int mapId,
            int shulkerId, String bucketMobData) {
        return craftingStack(itemType, count, durability, enchantments, mapId,
                shulkerId, bucketMobData, null);
    }

    private static CraftingStack craftingStack(
            short itemType, int count, int durability, long enchantments, int mapId,
            int shulkerId, String bucketMobData, String itemComponentData) {
        ItemComponentData components = decodedComponents(itemType, itemComponentData);
        return new CraftingStack(itemType, count,
                PlayerInventory.isDurable(itemType) ? durability : null,
                components.enchantments(enchantments), mapIdOrNull(mapId),
                shulkerIdOrNull(shulkerId), bucketMobData,
                components.customName(), componentBannerPatterns(components),
                componentBook(components), components.anvilUseCount(), components.leatherColor(),
                components.suspiciousStewEffect(), components.suspiciousStewDurationMcTicks(),
                components.ominousBottleAmplifierComponent(), components.potionContents(),
                com.gameexpert.ws.dto.WsMessages.TrimComponent.of(components.trim()), components.potDecorations());
    }

    private static ItemComponentData decodedComponents(short itemType, String encoded) {
        return ItemComponentCodec.decode(itemType, encoded);
    }

    private static List<BannerPatternLayer> componentBannerPatterns(ItemComponentData components) {
        return components.bannerPatterns().stream()
                .map(layer -> new BannerPatternLayer(layer.pattern().wireName(), layer.color()))
                .toList();
    }

    private static BookComponent componentBook(ItemComponentData components) {
        ItemComponentData.BookData book = components.book();
        return book == null ? null : new BookComponent(book.title(), book.author(), book.pages());
    }

    private static InventorySlot inventorySlot(int slot, short itemType, int count,
            Integer durability, long enchantments, Integer mapId, Integer shulkerId,
            String bucketMobData, String itemComponentData) {
        ItemComponentData components = decodedComponents(itemType, itemComponentData);
        return new InventorySlot(slot, itemType, count, durability,
                components.enchantments(enchantments), mapId, shulkerId,
                bucketMobData, components.customName(), componentBannerPatterns(components),
                componentBook(components), components.anvilUseCount(), components.leatherColor(),
                components.suspiciousStewEffect(), components.suspiciousStewDurationMcTicks(),
                components.ominousBottleAmplifierComponent(), components.potionContents(),
                com.gameexpert.ws.dto.WsMessages.TrimComponent.of(components.trim()), components.potDecorations());
    }

    private Object craftingMessage(PlayerTickState player, boolean crafted) {
        PlayerInventory inv = player.inventory();
        int gridSize = inv.craftingGridSize();
        int slotCount = inv.craftingSlotCount();
        List<CraftingStack> grid = new ArrayList<>(slotCount);
        for (int slot = 0; slot < slotCount; slot++) {
            grid.add(craftingStack(inv.craftingItemType(slot),
                    inv.craftingCount(slot), inv.craftingDurability(slot),
                    inv.craftingEnchantments(slot), inv.craftingMapId(slot),
                    inv.craftingShulkerId(slot), inv.craftingBucketMobData(slot),
                    inv.craftingItemComponentData(slot)));
        }
        PlayerAction.CraftStation station = player.openCraftingStation();
        BlockPos table = player.openCraftingTable();
        PlayerInventory.DroppedStack result = inv.craftingResult();
        CraftingStack resultStack;
        int levelCost = 0;
        boolean tooExpensive = false;
        String resultName = null;
        if (station == PlayerAction.CraftStation.SMITHING) {
            SmithingTransformRules.Plan plan = inv.planSmithingResult();
            PlayerInventory.StackSnapshot stack = plan == null ? PlayerInventory.StackSnapshot.EMPTY
                    : plan.result();
            resultStack = craftingStack(stack);
        } else if (station == PlayerAction.CraftStation.ANVIL) {
            var plan = inv.planAnvilResult(player.anvilRename(), false);
            if (plan == null) {
                resultStack = craftingStack(PlayerInventory.EMPTY, 0, 0);
            } else {
                String encoded = ItemComponentCodec.encode(
                        plan.result().itemType(), plan.resultComponents());
                PlayerInventory.StackSnapshot stack = new PlayerInventory.StackSnapshot(
                        plan.result().itemType(), plan.result().count(), plan.result().durability(),
                        plan.result().enchantments(), plan.result().mapId(), plan.result().shulkerId(),
                        plan.result().bucketMobData(), encoded);
                resultStack = craftingStack(stack);
                levelCost = plan.levelCost();
                tooExpensive = plan.tooExpensive();
                resultName = plan.customName();
            }
        } else if (station == PlayerAction.CraftStation.GRINDSTONE) {
            var plan = inv.planGrindstoneResult();
            if (plan == null) resultStack = craftingStack(PlayerInventory.EMPTY, 0, 0);
            else {
                short sourceType = inv.craftingItemType(0);
                ItemComponentData sourceComponents = ItemComponentCodec.decode(sourceType,
                        inv.craftingItemComponentData(0));
                ItemComponentData reset = new ItemComponentData(sourceComponents.customName(),
                        sourceComponents.bannerPatterns(), sourceComponents.book(), 0, null);
                PlayerInventory.StackSnapshot raw = plan.result();
                resultStack = craftingStack(new PlayerInventory.StackSnapshot(raw.itemType(),
                        raw.count(), raw.durability(), raw.enchantments(), raw.mapId(),
                        raw.shulkerId(), raw.bucketMobData(),
                        ItemComponentCodec.encode(raw.itemType(), reset)));
            }
        } else if (station == PlayerAction.CraftStation.CARTOGRAPHY) {
            PendingCartographySettlement pending =
                    pendingCartographySettlements.get(player.nickname());
            PlayerInventory.StackSnapshot stack;
            if (pending != null && pending.player == player) {
                stack = pending.plan.inventoryDelta().result();
            } else {
                CartographyRules.Plan plan = cartographyPreviewPlan(inv);
                stack = plan == null ? PlayerInventory.StackSnapshot.EMPTY : plan.result();
            }
            resultStack = craftingStack(stack);
        } else if (station == PlayerAction.CraftStation.LOOM) {
            PlayerInventory.StackSnapshot stack = inv.planLoomResult(player.loomSelection());
            resultStack = craftingStack(stack == null ? PlayerInventory.StackSnapshot.EMPTY : stack);
        } else if (station == PlayerAction.CraftStation.BEACON) {
            // [BEACON] BeaconMenu 에는 결과 칸이 없다. 한 칸 격자가 제작법과 맞아도 결과를 싣지 않는다.
            resultStack = craftingStack(PlayerInventory.EMPTY, 0, 0);
        } else {
            resultStack = result == null
                    ? craftingStack(PlayerInventory.EMPTY, 0, 0)
                    : craftingStack(result.itemType(), result.count(), result.durability(),
                            result.enchantments(), result.mapId(), result.shulkerId(),
                            result.bucketMobData(), result.itemComponentData());
        }
        return new CraftingUpdate(
                craftingStationName(station),
                gridSize,
                table == null ? 0 : table.x(),
                table == null ? 0 : table.y(),
                table == null ? 0 : table.z(),
                inventorySlots(inv),
                armorSlots(inv),
                offhandSlot(inv),
                inv.selectedSlot(),
                grid,
                craftingStack(inv.cursorType(), inv.cursorCount(), inv.cursorDurability(),
                        inv.cursorEnchantments(), inv.cursorMapId(), inv.cursorShulkerId(),
                        inv.cursorBucketMobData(), inv.cursorItemComponentData()),
                resultStack,
                crafted,
                inv.craftingSelection(), player.loomSelection() == null
                        ? null : player.loomSelection().wireName(),
                levelCost, tooExpensive, resultName, player.openCraftingSessionId(),
                station == PlayerAction.CraftStation.BEACON && table != null
                        ? beaconMenuState(table) : null,
                player.openCraftingRequestId());
    }

    /** [BEACON] BeaconMenu data slot 셋: 현재 층 수와 신호기 state 의 두 효과. */
    private com.gameexpert.ws.dto.WsMessages.BeaconMenuState beaconMenuState(BlockPos table) {
        int state = residentBlockType(rt.accessor(), table.x(), table.y(), table.z()) == Blocks.BEACON
                ? rt.blockStates().get(table.x(), table.y(), table.z(), Blocks.BEACON) : 0;
        BeaconRules.Power primary = BeaconRules.primary(state);
        BeaconRules.Power secondary = BeaconRules.secondary(state);
        return new com.gameexpert.ws.dto.WsMessages.BeaconMenuState(
                beaconLevelsAt(table.x(), table.y(), table.z()),
                primary == null ? null : primary.wireName(),
                secondary == null ? null : secondary.wireName());
    }

    private CartographyRules.Plan cartographyPreviewPlan(PlayerInventory inventory) {
        PlayerInventory.StackSnapshot map = inventory.craftingStackSnapshot(0);
        PlayerInventory.StackSnapshot addition = inventory.craftingStackSnapshot(1);
        CartographyRules.Operation operation = CartographyRules.operation(map, addition);
        if (operation == null) return null;
        WorldMapData source = rt.mapData(map.mapId());
        WorldMapRuntime.CartographyAuthority authority =
                WorldMapRuntime.previewForDurableSource(source, operation);
        return authority == null ? null : inventory.planCartographyResult(
                PlayerInventory.CartographyAuthorityWitness.from(authority));
    }

    private static CraftingStack craftingStack(PlayerInventory.StackSnapshot stack) {
        return craftingStack(stack.itemType(), stack.count(), stack.durability(),
                stack.enchantments(), stack.mapId(), stack.shulkerId(), stack.bucketMobData(),
                stack.itemComponentData());
    }

    private static BrewingUpdate brewingUpdate(PlayerTickState player, int x, int y, int z,
            BrewingInventory brewing) {
        PlayerInventory inv = player.inventory();
        return new BrewingUpdate(x, y, z,
                inventorySlots(inv), offhandSlot(inv), inv.selectedSlot(), brewingSlots(brewing),
                brewingCursor(inv), brewing.fuel(), brewing.brewTicks(), player.openCraftingRequestId());
    }

    private static BrewingOpen brewingOpen(PlayerTickState player, int x, int y, int z,
            BrewingInventory brewing) {
        PlayerInventory inv = player.inventory();
        return new BrewingOpen(x, y, z,
                inventorySlots(inv), offhandSlot(inv), inv.selectedSlot(), brewingSlots(brewing),
                brewingCursor(inv), brewing.fuel(), brewing.brewTicks(), player.openCraftingRequestId());
    }

    private static List<CraftingStack> brewingSlots(BrewingInventory brewing) {
        List<CraftingStack> slots = new ArrayList<>(BrewingInventory.SLOTS);
        for (int slot = 0; slot < BrewingInventory.SLOTS; slot++) {
            // [BREWING-26.3] 범용 물약의 potionContents 도 화면에 싣는다.
            slots.add(craftingStack(brewing.itemType(slot), brewing.count(slot), brewing.durability(slot), 0L, 0, 0,
                    null, brewing.itemComponentData(slot)));
        }
        return slots;
    }

    private static CraftingStack brewingCursor(PlayerInventory inv) {
        return craftingStack(inv.cursorType(), inv.cursorCount(), inv.cursorDurability(),
                inv.cursorEnchantments(), inv.cursorMapId(), inv.cursorShulkerId(),
                inv.cursorBucketMobData(), inv.cursorItemComponentData());
    }

    private void closeCraftingSession(PlayerTickState player, boolean notify) {
        PlayerAction.CraftStation closingStation = player.openCraftingStation();
        BlockPos closingPos = player.openCraftingTable();
        long closingSessionId = player.openCraftingSessionId();
        Long closingRequestId = player.openCraftingRequestId();
        if (closingStation == PlayerAction.CraftStation.BREWING && closingPos != null) {
            if (player.inventory().settlementLeased()) {
                enqueuePlayerContainerAction(player, () -> {
                    if (player.openCraftingStation() == PlayerAction.CraftStation.BREWING
                            && player.openCraftingSessionId() == closingSessionId
                            && samePosition(player.openCraftingTable(),
                                    closingPos.x(), closingPos.y(), closingPos.z())) {
                        closeCraftingSession(player, notify);
                    }
                });
                return;
            }
            rt.brewingStorage().releaseLease(
                    closingPos.x(), closingPos.y(), closingPos.z(), player.nickname());
            dropCraftingOverflow(player, player.inventory().closeContainerCursor());
            player.closeCrafting();
            sendTo(player, inventoryMessage(player));
            if (notify) sendTo(player,
                    new BrewingClosed(closingPos.x(), closingPos.y(), closingPos.z(), closingRequestId));
            return;
        }
        PlayerInventory inventory = player.inventory();
        PlayerInventory.CompletePersistenceSnapshot source = inventory.acquireSettlementLease();
        if (source == null) return;
        PlayerInventory planned = source.detachedInventory();
        // [BEACON] BeaconMenu.removed: 결제 칸 아이템은 인벤토리로 접지 않고 플레이어 앞으로 던진다.
        List<PlayerInventory.DroppedStack> overflow = closingStation == PlayerAction.CraftStation.BEACON
                ? planned.closeBeaconCrafting() : planned.closeCrafting();
        if (overflow.isEmpty()) {
            PlayerInventory.CompletePersistenceSnapshot committed =
                    planned.completePersistenceSnapshot();
            if (!inventory.installCommittedSettlement(source, committed)) {
                inventory.releaseSettlementLease(source);
                return;
            }
            player.closeCrafting();
            // Craft clicks and this lossless close advanced only the runtime inventory. Queue
            // that exact baseline before a newly opened container submits its strict player CAS.
            rt.queuePlayerInventoryBaseline(player);
            sendTo(player, inventoryMessage(player));
            if (notify) sendTo(player, new CraftingClosed(
                    closingSessionId,
                    craftingStationName(closingStation),
                    closingPos == null ? 0 : closingPos.x(),
                    closingPos == null ? 0 : closingPos.y(),
                    closingPos == null ? 0 : closingPos.z()));
            return;
        }
        PlayerInventory.CompletePersistenceSnapshot committed =
                planned.completePersistenceSnapshot();
        long settlementId = rt.itemSystem().reserveSettlementEntityId();
        List<com.gameexpert.ground.dto.GroundItemSnapshot> ground =
                new ArrayList<>(overflow.size());
        for (int index = 0; index < overflow.size(); index++) {
            long entityId = index == 0
                    ? settlementId : rt.itemSystem().reserveSettlementEntityId();
            ground.add(rt.itemSystem().settlementThrownDropSnapshot(entityId,
                    overflow.get(index), player.x(), player.y(), player.z(), player.crouching(),
                    player.yaw(), player.pitch()));
        }
        Runnable install = () -> {
            for (var item : ground) rt.itemSystem().commitSettlementDrop(item);
            if (rt.players().get(player.nickname()) != player) {
                inventory.releaseSettlementLease(source);
                return;
            }
            if (!inventory.installCommittedSettlement(source, committed)) {
                inventory.releaseSettlementLease(source);
                return;
            }
            player.closeCrafting();
            sendTo(player, inventoryMessage(player));
            if (notify) sendTo(player, new CraftingClosed(
                    closingSessionId,
                    craftingStationName(closingStation),
                    closingPos == null ? 0 : closingPos.x(),
                    closingPos == null ? 0 : closingPos.y(),
                    closingPos == null ? 0 : closingPos.z()));
        };
        var service = rt.groundMutationSettlements();
        if (service == null) {
            install.run();
            return;
        }
        long expectedGroundRevision = rt.groundRevision();
        var command = new com.gameexpert.ground.dto.GroundMutationCommand(
                WorldRuntime.stableGroundMutationId(settlementId, 3),
                com.gameexpert.ground.dto.GroundMutationCommand.Kind.PLAYER_DROP,
                rt.worldId(), expectedGroundRevision, expectedGroundRevision + 1,
                source.revision(), rt.playerInventoryMutationSnapshot(player, committed),
                ground, List.of(), List.of(), List.of());
        Runnable rejected = () -> inventory.releaseSettlementLease(source);
        boolean accepted = rt.submitGroundSettlement(command, install, rejected);
        if (!accepted) rejected.run();
    }

    void closeCraftingSubscribersAt(int x, int y, int z) {
        for (PlayerTickState player : rt.players().values()) {
            if (samePosition(player.openCraftingTable(), x, y, z)) {
                closeCraftingSession(player, true);
            }
        }
    }

    void closeLecternSubscribersAt(int x, int y, int z) {
        for (PlayerTickState player : rt.players().values()) {
            if (samePosition(player.openLectern(), x, y, z)) player.closeLectern();
        }
    }

    private void dropCraftingOverflow(
            PlayerTickState player, List<PlayerInventory.DroppedStack> overflow) {
        for (PlayerInventory.DroppedStack stack : overflow) {
                    rt.itemSystem().spawnThrownDrop(stack.itemType(), stack.count(), stack.durability(),
                    stack.enchantments(), stack.mapId(), stack.shulkerId(), stack.bucketMobData(),
                    stack.itemComponentData(),
                    player.x(), player.y(), player.z(),
                    player.crouching(), player.yaw(), player.pitch());
        }
    }

    /** 커서 감소와 안정 ID 드랍을 DB 한 커밋으로 확정한 뒤 같은 완전 스냅샷을 런타임에 설치한다. */
    private boolean settleCursorGroundDrop(PlayerTickState player, boolean craftingCursor,
            boolean one, Runnable afterCommit) {
        PlayerInventory inventory = player.inventory();
        PlayerInventory.CompletePersistenceSnapshot source = inventory.acquireSettlementLease();
        if (source == null) return false;
        PlayerInventory planned = source.detachedInventory();
        PlayerInventory.DroppedStack dropped = craftingCursor
                ? planned.dropCraftingCursor(one) : planned.dropContainerCursor(one);
        if (dropped == null) {
            inventory.releaseSettlementLease(source);
            return false;
        }
        PlayerInventory.CompletePersistenceSnapshot committed =
                planned.completePersistenceSnapshot();
        long entityId = rt.itemSystem().reserveSettlementEntityId();
        var ground = rt.itemSystem().settlementThrownDropSnapshot(entityId, dropped,
                player.x(), player.y(), player.z(), player.crouching(),
                player.yaw(), player.pitch());
        var service = rt.groundMutationSettlements();
        Runnable install = () -> {
            rt.itemSystem().commitSettlementDrop(ground);
            if (rt.players().get(player.nickname()) != player) {
                inventory.releaseSettlementLease(source);
                return;
            }
            if (!inventory.installCommittedSettlement(source, committed)) {
                inventory.releaseSettlementLease(source);
                return;
            }
            afterCommit.run();
        };
        if (service == null) {
            install.run();
            return true;
        }
        long expectedGroundRevision = rt.groundRevision();
        var command = new com.gameexpert.ground.dto.GroundMutationCommand(
                WorldRuntime.stableGroundMutationId(entityId, 3),
                com.gameexpert.ground.dto.GroundMutationCommand.Kind.PLAYER_DROP,
                rt.worldId(), expectedGroundRevision, expectedGroundRevision + 1,
                source.revision(), rt.playerInventoryMutationSnapshot(player, committed),
                List.of(ground), List.of(), List.of(), List.of());
        Runnable rejected = () -> inventory.releaseSettlementLease(source);
        boolean accepted = rt.submitGroundSettlement(command, install, rejected);
        if (!accepted) rejected.run();
        return accepted;
    }

    // ── ① respawn: 침대 지점 우선(침대가 남아 있으면), 없거나 파괴됐으면 월드 스폰(S2a) ──
    private void applyRespawn(PlayerAction.Respawn respawn, long tickNo) {
        PlayerTickState player = rt.players().get(respawn.nickname());
        if (player == null || !player.isDead()) {
            return;
        }
        if (player.justDied()) {
            // 사망 정산과 PlayerDeath 송신이 끝날 때까지 요청을 보관한다.
            // 먼저 부활시키면 비동기 정산 콜백이 살아난 클라이언트를 다시 사망시킨다.
            player.deferRespawn();
            return;
        }
        if ("void_end".equals(rt.dimensionKey())) {
            rt.requestDimensionRespawn(player);
            return;
        }
        RespawnRules.Spawn spawn = resolveRespawn(player);
        if (!rt.customDimension() && player.hasBedSpawn() && !spawn.bed()) player.clearBedSpawn();
        rt.mobSystem().clearPlayerUseState(player.nickname());
        player.respawn(spawn.x(), spawn.y(), spawn.z());
        // 서버가 좌표를 강제했으므로 직전 위치 기준의 이동 예산은 버린다(스폰 복귀는 순간이동이 맞다).
        movementLimits.reset(player.nickname(), tickNo);
        sendTo(player, new RespawnSelf(spawn.x(), spawn.y(), spawn.z(), player.health()));
        // [MAPNAV] 침대가 파괴돼 월드 스폰으로 되돌아간 경우까지 나침반에 반영한다. 좌표는 착지한
        // 리스폰 셀이 아니라 침대 머리다 — 여기서만 이웃 칸을 보내면 죽을 때마다 나침반 목표가
        // 한 칸 움직였다가 재접속하면 되돌아온다(입장·침대 사용 지점은 늘 머리를 보낸다).
        if (!sendSpawnPoint(player)) spawnPointResend.add(player.nickname());
        rt.ctx().broadcaster().enqueueBroadcastFromTick(rt.worldId(),
                new PlayerRespawn(player.nickname(), spawn.x(), spawn.y(), spawn.z()));
    }

    RespawnRules.Spawn resolveRespawn(PlayerTickState player) {
        boolean bedPresent = !rt.customDimension() && player.hasBedSpawn()
                && validBedPair(player.bedSpawnX(), player.bedSpawnY(), player.bedSpawnZ());
        int bedFacing = bedPresent
                ? rt.blockStates().get(player.bedSpawnX(), player.bedSpawnY(),
                        player.bedSpawnZ(),
                        residentBlockType(rt.accessor(), player.bedSpawnX(), player.bedSpawnY(),
                                player.bedSpawnZ())) & BuildingBlockRules.FACING_MASK
                : 0;
        int[] worldSpawn = rt.worldSpawn();
        return RespawnRules.resolve(!rt.customDimension() && player.hasBedSpawn(), bedPresent,
                player.bedSpawnX(), player.bedSpawnY(), player.bedSpawnZ(), bedFacing,
                worldSpawn[0], worldSpawn[1], worldSpawn[2],
                (x, y, z) -> isSafeRespawnSpace(rt.accessor(), x, y, z));
    }

    static boolean isSafeRespawnSpace(TerrainAccessor accessor, int x, int feetY, int z) {
        if (feetY <= Blocks.MIN_Y || feetY >= Blocks.MAX_Y) return false;
        int support = residentBlockType(accessor, x, feetY - 1, z);
        int feet = residentBlockType(accessor, x, feetY, z);
        int head = residentBlockType(accessor, x, feetY + 1, z);
        if (support == UNAVAILABLE_BLOCK || feet == UNAVAILABLE_BLOCK || head == UNAVAILABLE_BLOCK) {
            return false;
        }
        return Fluids.isSolid(support)
                && !Fluids.isFluid(support)
                && !Fluids.isSolid(feet)
                && !Fluids.isFluid(feet)
                && !Fluids.isSolid(head)
                && !Fluids.isFluid(head);
    }

    // ── 수면: 접속자 전원 수면 시 아침 전환. 수면 현황 방송 헬퍼(S2a) ──
    private void handleSleepMorning() {
        int total = rt.players().size();
        if (total == 0) {
            return;
        }
        int sleeping = 0;
        boolean invalidBedCleared = false;
        for (PlayerTickState p : rt.players().values()) {
            if (p.sleeping()) {
                // [FURNITURE-26.3] 유효성은 리스폰 지점이 아니라 **자고 있는 침대 칸**으로 본다 —
                // 건초 침대는 리스폰을 설정하지 않으므로 bedSpawn 으로 보면 첫 틱에 취소된다.
                if (validBedPair(p.sleepBedX(), p.sleepBedY(), p.sleepBedZ())) {
                    sleeping++;
                } else if (endSleep(p)) {
                    invalidBedCleared = true;
                }
            }
        }
        if (invalidBedCleared) broadcastSleepStatus();
        if (sleeping > 0 && !rt.clock().isNight()) {
            for (PlayerTickState p : rt.players().values()) {
                endSleep(p);
            }
            sleepStatusVisible = false;
            rt.ctx().broadcaster().enqueueBroadcastFromTick(rt.worldId(),
                    new com.gameexpert.ws.dto.WsMessages.WakeUp());
            return;
        }
        if (sleeping == 0 || sleeping < total) {
            return; // 전원 수면이 아니면 대기
        }
        rt.clock().skipToNextMorning();
        for (PlayerTickState p : rt.players().values()) {
            endSleep(p);
        }
        sleepStatusVisible = false;
        rt.ctx().broadcaster().enqueueBroadcastFromTick(rt.worldId(),
                new com.gameexpert.ws.dto.WsMessages.WakeUp());
    }

    /**
     * [PHANTOM] 불면 카운터 진행. 바닐라 {@code Player#tick} 이 자고 있지 않은 접속자마다
     * {@code Stats.TIME_SINCE_REST} 를 올리는 자리와 같다(mc-phantom-insomnia.md §3).
     * 리셋은 침대 진입({@code PlayerTickState#beginSleep})과 사망이 소유하므로 여기는 증가만 한다.
     */
    private void advanceTimeSinceRest() {
        for (PlayerTickState p : rt.players().values()) {
            p.advanceTimeSinceRest();
            // [ARROW-GROUND] LivingEntity.tick 의 박힌 화살 빼기도 같은 플레이어 틱 자리에서 돈다.
            p.tickStuckArrows();
        }
    }

    private void broadcastSleepStatusIfRosterChanged() {
        int total = rt.players().size();
        if (total == sleepRosterSize) return;
        sleepRosterSize = total;
        if (sleepStatusVisible || rt.players().values().stream().anyMatch(PlayerTickState::sleeping)) {
            broadcastSleepStatus();
        }
    }

    private void broadcastSleepStatus() {
        int total = rt.players().size();
        int sleeping = 0;
        for (PlayerTickState p : rt.players().values()) {
            if (p.sleeping()) {
                sleeping++;
            }
        }
        sleepRosterSize = total;
        sleepStatusVisible = sleeping > 0;
        rt.ctx().broadcaster().enqueueBroadcastFromTick(rt.worldId(),
                new com.gameexpert.ws.dto.WsMessages.SleepStatus(sleeping, total));
    }

    // ── ⑤ 브로드캐스트 ──
    private void broadcastMoves() {
        List<PlayerPose> moved = null;
        List<PlayerTickState> delivered = null;
        for (PlayerTickState player : rt.players().values()) {
            if (player.poseDirty()) {
                if (moved == null) {
                    moved = new ArrayList<>();
                    delivered = new ArrayList<>();
                }
                moved.add(player.pose());
                delivered.add(player);
            }
        }
        if (moved != null && rt.ctx().broadcaster().enqueueBroadcastFromTick(rt.worldId(),
                new PlayerMoves(moved))) {
            for (PlayerTickState player : delivered) {
                player.consumePoseDirty();
            }
        }
    }

    // 최종 셀은 영속 버퍼에 한 번만 병합하고, 전송은 revision 발생 순서를 보존하는 런타임 큐가 맡는다.
    // 동일 좌표가 한 틱에 여러 번 바뀌거나 롤백돼도 중간 cursor가 사라지지 않는다.
    private void broadcastBlocks() {
        Map<BlockPos, Short> changes = rt.tickBlockChanges();
        if (!changes.isEmpty()) {
            // Patterned banners are block entities: persist their payload together with the final block cell
            // before publishing the semantic banner delta. The generic block diff buffer may repeat the same
            // cell later, but can never create a banner payload without this committed boundary.
            rt.settleBannerBlockChanges(changes);
            rt.settleSignBlockChanges(changes);
            for (Map.Entry<BlockPos, Short> entry : changes.entrySet()) {
                BlockPos pos = entry.getKey();
                short type = entry.getValue();
                short state = (short) rt.blockState(
                        pos.x(), pos.y(), pos.z(), Short.toUnsignedInt(type));
                String mutation = rt.tickBlockMutationKey(pos);
                // Flesh cells were committed with their mob/receipt before owner publication.
                // Re-buffering an old maturation cell could later overwrite a committed hatch AIR.
                if (mutation == null || !mutation.startsWith(
                        com.gameexpert.engine.persistence.animal.FleshNetherPersistenceService.SOURCE)) {
                    rt.ctx().blockDiffBuffer().put(
                            rt.worldId(), pos.x(), pos.y(), pos.z(), type, state, mutation);
                }
            }
            changes.clear();
        }
        rt.flushDecorationUpdates();
    }

    private void broadcastFireState() {
        RandomTickSystem.FireSnapshot snapshot = randomTicks.fireSnapshot();
        if (snapshot.revision() == publishedFireRevision) return;
        List<FireBlock> blocks = snapshot.blocks().stream()
                .map(pos -> new FireBlock(pos.x(), pos.y(), pos.z()))
                .toList();
        if (!rt.ctx().broadcaster().enqueueRetainedBroadcastFromTick(
                rt.worldId(), new BlockFireUpdate(snapshot.revision(), blocks))) {
            return;
        }
        publishedFireRevision = snapshot.revision();
    }

    static final class WelcomeFireSnapshot {
        private final long revision;
        private final List<FireBlock> blocks;

        private WelcomeFireSnapshot(long revision, List<FireBlock> blocks) {
            this.revision = revision;
            this.blocks = blocks;
        }

        long revision() {
            return revision;
        }

        List<FireBlock> blocks() {
            return blocks;
        }
    }

    WelcomeFireSnapshot welcomeFireSnapshot() {
        RandomTickSystem.FireSnapshot snapshot = randomTicks.fireSnapshot();
        List<FireBlock> blocks = snapshot.blocks().stream()
                .map(pos -> new FireBlock(pos.x(), pos.y(), pos.z()))
                .toList();
        return new WelcomeFireSnapshot(snapshot.revision(), blocks);
    }

    /** [SURV-X] 누적 경험치가 바뀐 플레이어에게만 xpUpdate 를 보냅니다(허기 dirty 패턴과 동일). */
    private void broadcastXp() {
        for (PlayerTickState player : rt.players().values()) {
            if (!player.xpDirty()) continue;
            if (sendTo(player, new XpUpdate(player.xpTotal()))) {
                player.consumeXpDirty();
                refreshOpenEnchanting(player);
            }
        }
    }

    /**
     * [SURV-X] 인챈트 화면을 열어 둔 채 경험치가 들어오면 제안의 affordable 과 레벨 표시가 낡는다.
     * 오브 획득·화로 회수·몹 처치 어느 쪽이든 xpUpdate 를 보낸 그 자리에서 화면도 새로 그린다.
     */
    private void refreshOpenEnchanting(PlayerTickState player) {
        BlockPos pos = player.openEnchanting();
        if (pos == null) return;
        EnchantingInventory table =
                validatedEnchantingTable(player, pos.x(), pos.y(), pos.z());
        if (table == null) return;
        sendTo(player, enchantingUpdate(player, pos.x(), pos.y(), pos.z(), table));
    }

    private void broadcastHealth() {
        boolean sleepChanged = false;
        for (PlayerTickState player : rt.players().values()) {
            if (!player.healthDirty() && !player.foodDirty()) {
                continue;
            }
            // 허기만 바뀐 틱은 체력 사유가 없다. 체력이 그대로라 클라는 피격/회복 연출 없이 게이지만 갱신한다.
            String healthCause = player.healthDirty() ? player.healthCause() : "hunger";
            String damageCause = player.damageCause();
            String wireCause = normalizeHealthCause(healthCause);
            boolean handedOff = true;
            if (!player.justRespawned()) {
                handedOff = sendTo(player, new HealthUpdate(player.health(), 20, wireCause,
                        player.food(), player.saturationMilli()));
            }
            if (handedOff) {
                player.consumeFoodDirty();
            }
            if (!player.healthDirty()) {
                continue;
            }
            if (player.justDamaged()) {
                if (endSleep(player)) {
                    sleepChanged = true;
                }
                boolean hurtHandedOff = rt.ctx().broadcaster().enqueueBroadcastFromTick(rt.worldId(), new PlayerHurt(
                        player.nickname(), normalizePlayerHurtCause(damageCause),
                        player.hurtKbX(), player.hurtKbY(), player.hurtKbZ(),
                        player.hurtKbBonusX(), player.hurtKbBonusZ()));
                // 사망·인벤토리 정산이 뒤에서 보류돼도 이미 넘긴 충격을 다시 보내지 않는다.
                if (hurtHandedOff) player.consumeHurtEvent();
                handedOff &= hurtHandedOff;
            }
            if (player.justDied() && player.inventory().settlementLeased()) {
                // 보상 DB 정산의 입력 스냅샷을 사망 드랍과 같은 revision에서 갈라 놓지 않는다.
                // 콜백이 완전한 커밋 스냅샷을 설치한 다음 틱에 사망 정산 전체를 다시 수행한다.
                handedOff = false;
            } else if (player.justDied()) {
                rt.mobSystem().clearPlayerUseState(player.nickname());
                rt.boatSystem().playerDied(player.nickname());
                rt.placedEntities().playerDied(player.nickname());
                rt.cushionSystem().playerDied(player.nickname());
                rt.processGeneratedCushionRiderState(player.nickname(),
                        GeneratedCushionActionPolicy.Action.RIDER_DEATH);
                rt.mobSystem().dismountMob(player.nickname());
                closeChestSession(player, true);
                // [CONTAINER-CURSOR] 화물 세션도 여기서 끝내야 커서가 인벤토리로 돌아가
                // 사망 드랍에 함께 실린다(바닐라: 커서 스택도 죽으면 떨어진다).
                closeMobCargoSession(player);
                closeFurnaceSession(player);
                if (player.openCraftingStation() != null) {
                    if (player.openCraftingStation() == PlayerAction.CraftStation.BREWING) {
                        closeCraftingSession(player, true);
                    } else {
                        PlayerAction.CraftStation closingStation = player.openCraftingStation();
                        BlockPos closingPos = player.openCraftingTable();
                        long closingSessionId = player.openCraftingSessionId();
                        player.closeCrafting();
                        handedOff &= sendTo(player, new CraftingClosed(
                                closingSessionId,
                                craftingStationName(closingStation),
                                closingPos == null ? 0 : closingPos.x(),
                                closingPos == null ? 0 : closingPos.y(),
                                closingPos == null ? 0 : closingPos.z()));
                    }
                }
                closeEnchantingSession(player);
                // [SURV-X] 사망 경험치는 인벤토리 드랍과 같은 지점에 흩뿌리고 누적치를 0으로 되돌립니다.
                if (settlePlayerDeath(player, damageCause)) {
                    handedOff = false;
                } else {
                    handedOff = false;
                }
            }
            if (handedOff) {
                player.consumeHealthFlags();
            }
        }
        if (sleepChanged) broadcastSleepStatus();
    }

    private boolean settlePlayerDeath(PlayerTickState player, String damageCause) {
        PlayerInventory inventory = player.inventory();
        String expectedConnectionId = rt.connectionIdForPlayer(player);
        if (expectedConnectionId == null) return false;
        PlayerInventory.CompletePersistenceSnapshot source = inventory.acquireSettlementLease();
        if (source == null) return false;
        PlayerInventory planned = source.detachedInventory();
        List<PlayerInventory.DroppedStack> drops = planned.drainForDeath();
        int droppedXp = XpRules.xpDroppedOnDeath(player.xpLevel());
        // 소실의 저주만 있으면 드롭은 없어도 인벤토리는 비워진 계획을 저장해야 합니다.
        PlayerInventory.CompletePersistenceSnapshot committed = planned.completePersistenceSnapshot();
        List<com.gameexpert.ground.dto.GroundItemSnapshot> items = new ArrayList<>(drops.size());
        for (PlayerInventory.DroppedStack drop : drops) {
            long entityId = rt.itemSystem().reserveSettlementEntityId();
            items.add(rt.itemSystem().settlementDeathDropSnapshot(entityId, drop,
                    player.x(), player.y(), player.z()));
        }
        List<com.gameexpert.ground.dto.GroundXpOrbSnapshot> xp =
                rt.xpOrbSystem().reserveSettlementOrbs(
                        droppedXp, player.x(), player.y() + 0.5, player.z());
        WorldRuntime.PlayerInventorySettlementReservation reservation =
                rt.reservePlayerInventorySettlement(player, expectedConnectionId, inventory,
                        source);
        if (reservation == null) {
            inventory.releaseSettlementLease(source);
            return false;
        }
        Runnable rejected = () -> {
            inventory.releaseSettlementLease(source);
            rt.releasePlayerInventorySettlement(reservation);
        };
        Runnable installed = () -> {
            synchronized (inventory) {
                if (!rt.ownsPlayerInventorySettlement(reservation)
                        || !inventory.settlementLeased()
                        || inventory.revision() != source.revision()
                        || !inventory.installCommittedSettlement(source, committed)) {
                    rt.quarantineCommittedPlayerInventorySettlement(reservation);
                    return;
                }
            }
            try {
                for (var item : items) rt.itemSystem().commitSettlementDrop(item);
                rt.xpOrbSystem().commitSettlementOrbs(xp);
                player.setXpTotal(0);
                boolean sent = sendTo(player, inventoryMessage(player));
                sent &= rt.ctx().broadcaster().enqueueBroadcastFromTick(rt.worldId(),
                        new PlayerDeath(player.nickname(), normalizeDeathCause(damageCause),
                                player.hurtKiller()));
                if (sent) {
                    player.consumeHealthFlags();
                    if (player.respawnRequested()) {
                        applyRespawn(new PlayerAction.Respawn(player.nickname()), rt.tickNo());
                    }
                }
            } finally {
                rt.releasePlayerInventorySettlement(reservation);
            }
        };
        long sourceEntityId = !items.isEmpty() ? items.getFirst().entityId()
                : !xp.isEmpty() ? xp.getFirst().entityId()
                : rt.itemSystem().reserveSettlementEntityId();
        int namespace = items.isEmpty() ? 7 : 4;
        long expectedGroundRevision = rt.groundRevision();
        var command = new com.gameexpert.ground.dto.GroundMutationCommand(
                WorldRuntime.stableGroundMutationId(sourceEntityId, namespace),
                com.gameexpert.ground.dto.GroundMutationCommand.Kind.PLAYER_DEATH,
                rt.worldId(), expectedGroundRevision, expectedGroundRevision + 1,
                source.revision(), rt.playerInventoryMutationSnapshot(player, committed, 0),
                items, List.of(), xp, List.of());
        synchronized (inventory) {
            if (!rt.ownsPlayerInventorySettlement(reservation)
                    || !inventory.settlementLeased()
                    || inventory.revision() != source.revision()) {
                rejected.run();
                return false;
            }
        }
        if (!rt.submitGroundSettlement(command, installed, rejected)) {
            rejected.run();
            return false;
        }
        return true;
    }

    static String normalizePlayerHurtCause(String cause) {
        if (cause == null) throw new IllegalStateException("Missing player damage cause");
        return switch (cause) {
            case "mob" -> "melee";
            case "ender_pearl", "melee", "arrow", "explosion", "fall_small", "fall_big", "on_fire", "in_fire", "lava", "drown", "cactus", "freeze",
                 "poison", "magic", "starve", "fly_into_wall", "lightning",
                 // [SULFUR] 피해 0 인 간헐천 밀어올림. 기존 knockback-only 경계를 그대로 쓰되
                 // 사인을 따로 두어 클라가 피격 연출(점멸·피격음)을 걸지 않게 한다.
                 "geyser" -> cause;
            default -> throw new IllegalStateException("Unknown player damage cause: " + cause);
        };
    }

    static String normalizeHealthCause(String cause) {
        if (cause == null) throw new IllegalStateException("Missing health update cause");
        return switch (cause) {
            case "food", "regen", "golden_apple_regeneration", "respawn", "hunger" -> cause;
            default -> normalizePlayerHurtCause(cause);
        };
    }

    static String normalizeDeathCause(String cause) {
        if (cause == null) throw new IllegalStateException("Missing player death cause");
        return switch (cause) {
            case "mob" -> "mob";
            case "fall_small", "fall_big" -> "fall";
            case "ender_pearl", "arrow", "explosion", "on_fire", "in_fire", "lava", "drown", "cactus", "freeze",
                 "magic", "starve", "fly_into_wall", "lightning" -> cause;
            default -> throw new IllegalStateException("Unknown player death cause: " + cause);
        };
    }

    /**
     * 상태이상 목록이 바뀐 플레이어에게만 effectUpdate 를 보낸다(남은 시간은 클라가 자체 감소).
     * 송신 큐가 거부하면 dirty 를 그대로 두어 다음 틱에 재시도한다.
     */
    private void broadcastEffects() {
        for (PlayerTickState player : rt.players().values()) {
            StatusEffects effects = player.statusEffects();
            if (!effects.dirty()) continue;
            playerEffectsChanged.add(player.nickname());
            List<EffectDto> payload = new ArrayList<>();
            for (StatusEffects.ActiveEffect active : effects.snapshot()) {
                payload.add(new EffectDto(active.effect().protocolName(),
                        active.amplifier(), active.remainingTicks(),
                        active.ambient() || ambientEffect(active.effect())));
            }
            if (sendTo(player, new EffectUpdate(player.nickname(), payload))) {
                effects.consumeDirty();
            }
        }
        broadcastPlayerEffects();
    }

    /** 은은한(ambient) 효과: 콘딧 파워와 유황 가스 메스꺼움. effectUpdate · playerEffects 가 같이 쓴다. */
    private static boolean ambientEffect(StatusEffect effect) {
        return effect == StatusEffect.CONDUIT_POWER || effect == StatusEffect.NAUSEA;
    }

    /** [GLOWING] 마지막으로 다른 접속자에게 알린 플레이어별 "종류:은은함" 비트셋 서명. */
    private final Map<String, String> playerEffectSignatures = new HashMap<>();
    /** [GLOWING] 이번 틱에 효과 목록이 바뀐(dirty) 플레이어. 서명은 이 플레이어와 새 접속자만 다시 잰다. */
    private final java.util.Set<String> playerEffectsChanged = new java.util.HashSet<>();

    /**
     * [GLOWING] 다른 플레이어가 보는 효과(발광 윤곽선 · 효과 입자)를 복제한다. 종류나 은은함이 바뀐
     * 플레이어는 본인을 뺀 접속자 전원에게 playerEffects 를 보내고, 처음 보는 접속자에게는 다른 플레이어의
     * 비어 있지 않은 목록을 한 번씩 보낸다. 남은 시간은 싣지 않는다(효과가 끝나면 목록이 다시 간다).
     */
    private void broadcastPlayerEffects() {
        playerEffectSignatures.keySet().retainAll(rt.players().keySet());
        List<PlayerTickState> newcomers = null;
        for (PlayerTickState player : rt.players().values()) {
            boolean known = playerEffectSignatures.containsKey(player.nickname());
            if (known && !playerEffectsChanged.contains(player.nickname())) continue;
            String signature = visibleEffectSignature(player.statusEffects());
            String previous = playerEffectSignatures.put(player.nickname(), signature);
            if (previous == null) {
                if (newcomers == null) newcomers = new ArrayList<>();
                newcomers.add(player);
            }
            if (previous == null ? signature.isEmpty() : previous.equals(signature)) continue;
            com.gameexpert.ws.dto.WsMessages.PlayerEffects message = playerEffectsMessage(player);
            for (PlayerTickState observer : rt.players().values()) {
                if (observer != player) sendTo(observer, message);
            }
        }
        playerEffectsChanged.clear();
        if (newcomers == null) return;
        for (PlayerTickState newcomer : newcomers) {
            for (PlayerTickState other : rt.players().values()) {
                if (other == newcomer || other.statusEffects().activeMask() == 0L) continue;
                sendTo(newcomer, playerEffectsMessage(other));
            }
        }
    }

    private static String visibleEffectSignature(StatusEffects effects) {
        long kinds = effects.activeMask();
        if (kinds == 0L) return "";
        long ambient = 0L;
        for (StatusEffects.ActiveEffect active : effects.snapshot()) {
            if (ambientEffect(active.effect())) ambient |= 1L << active.effect().ordinal();
        }
        return kinds + ":" + ambient;
    }

    private static com.gameexpert.ws.dto.WsMessages.PlayerEffects playerEffectsMessage(PlayerTickState player) {
        List<com.gameexpert.ws.dto.WsMessages.PlayerEffectDto> effects = new ArrayList<>();
        for (StatusEffects.ActiveEffect active : player.statusEffects().snapshot()) {
            effects.add(new com.gameexpert.ws.dto.WsMessages.PlayerEffectDto(active.effect().protocolName(),
                    ambientEffect(active.effect())));
        }
        return new com.gameexpert.ws.dto.WsMessages.PlayerEffects(player.nickname(), effects);
    }

    /**
     * [DEEP-DARK] 진동 사슬 한 틱. 진동 원천은 <b>전부 기존 권위 사실에서 파생</b>한다 —
     * 새 프로토콜도, 클라가 보내는 새 액션도 없다.
     *
     * <ul>
     *   <li>블록 변경: 이 틱의 {@code tickBlockChanges} 가 곧 설치·파괴 사실이다. 부속
     *       자신의 상태 변경(감지체 반짝임·비명체 경고)은 진동이 아니므로 제외한다 —
     *       바닐라에서도 감지체는 자기 활성으로 다시 울리지 않는다.</li>
     *   <li>발소리: 확정된 pose 의 블록 좌표가 바뀌었고 웅크리지 않았을 때. 웅크림이 진동을
     *       내지 않는 것은 바닐라와 같다([B] «Sculk Sensor»).</li>
     * </ul>
     */
    void emitDecorationVibration(SculkVibrationRules.Event event, double x, double y, double z,
            String sourceNickname) {
        sculkVibrations.emit(event, (int) Math.floor(x), (int) Math.floor(y),
                (int) Math.floor(z), sourceNickname);
    }

    private void tickSculkVibrations(long tickNo) {
        // 개화가 깐 좌표는 진동 원천이 아니다 — 바닐라 SculkSpreader 도 확산 쓰기에서
        // GameEvent 를 내지 않는다. 커밋 좌표를 돌려받아 아래 emit 에서 건너뛰지 않으면,
        // 몹 처치 하나가 자기 개화를 스스로 되읽어 비명체 경고 단계를 올린다.
        Set<BlockPos> bloomWrites = commitSculkBlooms();
        // 사슬이 setState 로 같은 맵에 쓰므로 먼저 스냅샷을 뜬다.
        List<BlockPos> changed = rt.tickBlockChanges().isEmpty()
                ? List.of() : new ArrayList<>(rt.tickBlockChanges().keySet());
        for (BlockPos pos : changed) {
            // 부속 인덱스는 개화 좌표에서도 갱신해야 한다(새 감지체·비명체가 그 자리에 선다).
            sculkVibrations.invalidate(pos.x(), pos.y(), pos.z());
            if (bloomWrites.contains(pos)) continue;
            int block = residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z());
            if (SculkVibrationRules.isSensor(block) || SculkVibrationRules.isShrieker(block)) {
                continue;
            }
            sculkVibrations.emit(block == Blocks.AIR
                            ? SculkVibrationRules.Event.BLOCK_BREAK
                            : SculkVibrationRules.Event.BLOCK_PLACE,
                    pos.x(), pos.y(), pos.z(), null);
        }
        for (PlayerTickState player : rt.players().values()) {
            if (player.isDead()) {
                sculkLastFootstep.remove(player.nickname());
                continue;
            }
            BlockPos foot = new BlockPos((int) Math.floor(player.x()),
                    (int) Math.floor(player.y()), (int) Math.floor(player.z()));
            BlockPos previous = sculkLastFootstep.put(player.nickname(), foot);
            if (player.crouching() || previous == null || previous.equals(foot)) continue;
            sculkVibrations.emit(SculkVibrationRules.Event.STEP,
                    foot.x(), foot.y(), foot.z(), player.nickname());
        }
        sculkLastFootstep.keySet().retainAll(rt.players().keySet());
        sculkVibrations.tick(tickNo * StatusEffects.MC_TICKS_PER_SERVER_TICK);
    }

    /** 새 상주 청크가 비상주 시점의 빈 스컬크 섹션 캐시를 정확히 폐기한다. */
    void onChunkActivatedForSculk(int chunkX, int chunkZ) {
        sculkVibrations.onChunkActivated(chunkX, chunkZ);
    }

    /**
     * [DEEP-DARK] 말린 가스트 수화 사슬 한 틱. 진동 사슬 <b>바로 뒤</b>에 돈다 — 수화 단계는
     * 블록 상태 바이트(옆면 텍스처)이고 소생은 블록 제거라, 둘 다 이 틱의 블록 변경 배치에
     * 합류해야 같은 방송으로 나간다.
     *
     * <p>추적 원장의 입구는 여기 하나뿐이다: 이번 틱 변경 좌표. 말린 가스트 설치도, 옆에
     * 물이 흘러온 것도, 물이 빠진 것도 전부 이 목록에 칸을 남긴다 — 콘크리트 경화가 같은
     * 합류점을 쓰는 것과 같은 이유이며, 그래서 별도 스캐너를 만들지 않는다. 청크 적재로
     * 돌아온 블록만 {@link #trackLoadedDriedGhast} 가 따로 넣는다.
     */
    private void tickDriedGhastHydration(long tickNo) {
        Map<BlockPos, Short> changes = rt.tickBlockChanges();
        if (!changes.isEmpty()) {
            // 사슬이 setState 로 같은 맵에 쓰므로 먼저 스냅샷을 뜬다(진동 사슬과 같다).
            for (BlockPos pos : new ArrayList<>(changes.keySet())) {
                driedGhastHydration.invalidate(pos.x(), pos.y(), pos.z());
            }
        }
        driedGhastHydration.tick(tickNo * StatusEffects.MC_TICKS_PER_SERVER_TICK);
    }

    /**
     * [CREAKING] 크리킹 하트 사슬 한 틱. 수화 사슬 <b>바로 뒤</b>에 돈다 — 깨어남 스왑은
     * 블록 종류 변경이고 소환/소멸은 몹 원장 변경이라, 둘 다 이 틱의 블록 변경 배치와 몹
     * 방송에 합류해야 같은 프레임으로 나간다.
     *
     * <p>추적 원장의 입구는 여기 하나(이번 틱 변경 좌표)와 청크 적재
     * ({@link #trackLoadedCreakingHeart})뿐이다. 하트는 자연 지형에 없고 제작으로만 생기므로
     * 별도 스캐너를 만들지 않는다 — 말린 가스트가 같은 판단을 이미 냈다.
     *
     * <p>시간 단위는 <b>권위 틱이 아니라 월드 시각</b>이다(밤 판정 하나만 쓴다). 그래서
     * {@code tickNo} 를 받지 않는다.
     */
    private void tickCreakingHearts() {
        Map<BlockPos, Short> changes = rt.tickBlockChanges();
        if (!changes.isEmpty()) {
            // 사슬이 같은 맵에 쓰므로 먼저 스냅샷을 뜬다(수화·진동 사슬과 같다).
            for (BlockPos pos : new ArrayList<>(changes.keySet())) {
                creakingHearts.invalidate(pos.x(), pos.y(), pos.z());
            }
        }
        creakingHearts.tick();
    }

    /**
     * [DEEP-DARK] 몹 lane 이 남긴 촉매 개화를 커밋한다. 블록 쓰기는 랜덤틱과 같은 단일
     * 경로({@code fluidSim().applyChange})를 쓰므로 diff 영속·방송·이웃 갱신이 그대로 따라온다.
     * 개화가 만든 감지체·비명체는 바로 다음 줄의 진동 처리가 자기 인덱스에 넣는다.
     *
     * @return 이번 커밋이 쓴 좌표 전부. 호출자는 이 좌표를 진동 원천에서 뺀다.
     */
    private Set<BlockPos> commitSculkBlooms() {
        List<long[]> blooms = rt.mobSystem().drainSculkBlooms();
        if (blooms.isEmpty()) return Set.of();
        Set<BlockPos> written = new HashSet<>();
        SculkCatalystRules.Terrain terrain = (x, y, z) -> {
            if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return UNAVAILABLE_BLOCK;
            return residentBlockType(rt.accessor(), x, y, z);
        };
        for (long[] bloom : blooms) {
            int x = (int) bloom[0];
            int y = (int) bloom[1];
            int z = (int) bloom[2];
            long seed = SculkCatalystRules.bloomSeed(rt.seed(), x, y, z, bloom[4]);
            for (SculkCatalystRules.Conversion conversion
                    : SculkCatalystRules.bloom(seed, x, y, z, (int) bloom[3], terrain)) {
                rt.fluidSim().applyChange(conversion.x(), conversion.y(), conversion.z(),
                        conversion.blockType());
                written.add(new BlockPos(conversion.x(), conversion.y(), conversion.z()));
            }
        }
        return written;
    }

    private void broadcastSoundEvents() {
        for (PlayerTickState player : rt.players().values()) {
            int totems = player.drainTotemUseSounds();
            if (totems > 0) sendTo(player, inventoryMessage(player));
            for (int i = 0; i < totems; i++) {
                sendTo(player, new SoundEvent("totem_use", PlayerInventory.TOTEM_OF_UNDYING));
            }
            // [DIAMOND-SHIELD] 실제로 막아 낸 방패 종류를 그대로 싣는다. 여기에 기본 방패를
            // 하드코딩하면 다이아 방패가 남의 아이템 소리를 낸다.
            for (short shieldType : player.drainShieldBlockedSounds()) {
                sendTo(player, new SoundEvent("shield_block", shieldType));
            }
            for (short itemType : player.drainBrokenSoundItems()) {
                sendTo(player, new SoundEvent("item_break", itemType));
            }
            for (BlockPos pos : player.drainShelfMushroomBounceSounds()) {
                broadcastWorldSound("shelf_mushroom_bounce", pos.x(), pos.y(), pos.z(),
                        (short) Blocks.SHELF_MUSHROOM);
            }
            // [VANILLA-SOUNDS] 낙하 피해의 블록 낙하음. 바닐라 Entity.playSound 처럼 발 위치에서 난다.
            for (PlayerTickState.BlockFallSound sound : player.drainBlockFallSounds()) {
                broadcastWorldSoundExact("block_fall", sound.x(), sound.y(), sound.z(),
                        (short) sound.blockType());
            }
        }
    }

    /**
     * 분출 중인 강한 유황만 기존 파티클/사운드 전송 반경 안에서 찾고, 표현 cadence 사실을
     * 방송한다. 권위 틱은 10 TPS 이므로 20 TPS 정본 상수와 비교할 때 MC 틱으로 환산한다.
     */
    private void tickPotentSulfurPresentation(long tickNo) {
        long mcTick = tickNo * StatusEffects.MC_TICKS_PER_SERVER_TICK;
        boolean particle = Math.floorMod(mcTick,
                PotentSulfurRules.PARTICLE_FREQUENCY_TICKS) == 0;
        boolean sound = Math.floorMod(mcTick,
                PotentSulfurRules.SOUND_FREQUENCY_TICKS) == 0;
        if ((!particle && !sound) || rt.players().isEmpty()) return;

        Set<BlockPos> vents = new LinkedHashSet<>();
        int range = (int) SoundRules.PARTICLE_RANGE;
        double rangeSquared = SoundRules.PARTICLE_RANGE * SoundRules.PARTICLE_RANGE;
        for (PlayerTickState player : rt.players().values()) {
            int minX = (int) Math.floor(player.x() - range);
            int maxX = (int) Math.floor(player.x() + range);
            int minY = Math.max(Blocks.MIN_Y, (int) Math.floor(player.y() - range));
            int maxY = Math.min(Blocks.MAX_Y, (int) Math.floor(player.y() + range));
            int minZ = (int) Math.floor(player.z() - range);
            int maxZ = (int) Math.floor(player.z() + range);
            for (int chunkZ = Math.floorDiv(minZ, Blocks.CHUNK_X);
                    chunkZ <= Math.floorDiv(maxZ, Blocks.CHUNK_X); chunkZ++) {
                for (int chunkX = Math.floorDiv(minX, Blocks.CHUNK_X);
                        chunkX <= Math.floorDiv(maxX, Blocks.CHUNK_X); chunkX++) {
                    TerrainAccessor.SnapshotSource source =
                            rt.accessor().snapshotSource(chunkX, chunkZ);
                    if (source == null) continue;
                    int worldX0 = chunkX * Blocks.CHUNK_X;
                    int worldZ0 = chunkZ * Blocks.CHUNK_X;
                    for (int y = minY; y <= maxY; y++) {
                        int layer = (y - Blocks.MIN_Y) * Blocks.CHUNK_X * Blocks.CHUNK_X;
                        for (int localZ = 0; localZ < Blocks.CHUNK_X; localZ++) {
                            int row = layer + localZ * Blocks.CHUNK_X;
                            for (int localX = 0; localX < Blocks.CHUNK_X; localX++) {
                                if (source.blockTypeAt(row + localX) != Blocks.POTENT_SULFUR) {
                                    continue;
                                }
                                int x = worldX0 + localX;
                                int z = worldZ0 + localZ;
                                double dx = x + 0.5 - player.x();
                                double dy = y + 0.5 - player.y();
                                double dz = z + 0.5 - player.z();
                                if (dx * dx + dy * dy + dz * dz >= rangeSquared) continue;
                                if (isEruptingPotentSulfur(x, y, z, tickNo)) {
                                    vents.add(new BlockPos(x, y, z));
                                }
                            }
                        }
                    }
                }
            }
        }

        for (BlockPos vent : vents) {
            double x = vent.x() + 0.5;
            double y = vent.y() + 0.5;
            double z = vent.z() + 0.5;
            if (particle) broadcastPotentSulfurEvent("particle", x, y, z,
                    SoundRules.PARTICLE_RANGE);
            if (sound) broadcastPotentSulfurEvent("sound", x, y, z, SoundRules.RANGE);
        }
    }

    private boolean isEruptingPotentSulfur(int x, int y, int z, long tickNo) {
        int waterBlocks = 0;
        while (waterBlocks < PotentSulfurRules.MAX_WATER_BLOCKS_ABOVE
                && residentBlockType(rt.accessor(), x, y + 1 + waterBlocks, z)
                        == Blocks.WATER_SOURCE) {
            waterBlocks++;
        }
        if (waterBlocks == 0
                || residentBlockType(rt.accessor(), x, y + 1 + waterBlocks, z)
                        == Blocks.WATER_SOURCE) return false;
        int below = residentBlockType(rt.accessor(), x, y - 1, z);
        return below == Blocks.LAVA_SOURCE
                || below == Blocks.MAGMA && PotentSulfurRules.periodicErupting(
                        tickNo, waterBlocks, x, y, z);
    }

    private void broadcastPotentSulfurEvent(String phase, double x, double y, double z,
            double range) {
        PotentSulfurEvent message = new PotentSulfurEvent(
                rt.nextEventId(), phase, x, y, z);
        for (PlayerTickState player : rt.players().values()) {
            if (SoundRules.audible(x, y, z, player.x(), player.y(), player.z(), range)) {
                sendTo(player, message);
            }
        }
    }

    private void broadcastWorldSound(String kind, double x, double y, double z,
            short blockType) {
        broadcastWorldSoundExact(kind, x + 0.5, y + 0.5, z + 0.5, blockType);
    }

    /** [CONTAINER-MENUS] Five SPLASH particles over a mud-converted block (32-block send range). */
    private void broadcastSplashParticles(int x, int y, int z) {
        com.gameexpert.ws.dto.WsMessages.SplashParticles message =
                new com.gameexpert.ws.dto.WsMessages.SplashParticles(x, y, z);
        for (PlayerTickState player : rt.players().values()) {
            double dx = player.x() - (x + 0.5), dy = player.y() - (y + 1), dz = player.z() - (z + 0.5);
            if (dx * dx + dy * dy + dz * dz < 32.0 * 32.0) sendTo(player, message);
        }
    }

    /** 바닐라와 같이 전송 반경 밖 플레이어에게는 소리 패킷 자체를 보내지 않는다({@link SoundRules}). */
    private void broadcastWorldSoundExact(String kind, double x, double y, double z,
            short blockType) {
        // eventId 는 수신자 수와 무관하게 한 번만 소비해 월드 사건 수열을 유지한다.
        WorldSound message = new WorldSound(rt.nextEventId(), kind, x, y, z, blockType);
        double range = SoundRules.worldSoundRange(kind);
        for (PlayerTickState player : rt.players().values()) {
            if (SoundRules.audible(x, y, z, player.x(), player.y(), player.z(), range)) {
                sendTo(player, message);
            }
        }
    }

    private boolean sendTo(PlayerTickState player, Object message) {
        WebSocketSession session = rt.session(player.nickname());
        if (session != null) {
            return rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session, message);
        }
        return true;
    }

    private boolean sendOneShotFeedback(PlayerTickState player, String kind, String identity,
            Object message, Runnable accepted) {
        OneShotFeedbackKey key = new OneShotFeedbackKey(player.nickname(), kind, identity);
        if (pendingOneShotFeedback.containsKey(key)) return false;
        WebSocketSession session = rt.session(player.nickname());
        if (session == null || rt.players().get(player.nickname()) != player) {
            accepted.run();
            return false;
        }
        if (rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session, message)) {
            accepted.run();
            return true;
        }
        if (pendingOneShotFeedback.size() >= MAX_PENDING_ONE_SHOT_FEEDBACK) {
            accepted.run();
            return false;
        }
        pendingOneShotFeedback.put(key,
                new PendingOneShotFeedback(player, session, message, accepted));
        return false;
    }

    private void retryPendingOneShotFeedback() {
        var iterator = pendingOneShotFeedback.entrySet().iterator();
        int remaining = MAX_PENDING_ONE_SHOT_FEEDBACK;
        while (remaining-- > 0 && iterator.hasNext()) {
            Map.Entry<OneShotFeedbackKey, PendingOneShotFeedback> entry = iterator.next();
            PendingOneShotFeedback pending = entry.getValue();
            if (rt.players().get(entry.getKey().nickname()) != pending.player()
                    || rt.session(entry.getKey().nickname()) != pending.session()) {
                iterator.remove();
                pending.accepted().run();
                continue;
            }
            if (!rt.ctx().broadcaster().enqueueSendToFromTick(
                    rt.worldId(), pending.session(), pending.message())) continue;
            iterator.remove();
            pending.accepted().run();
        }
    }

    /** Owner-thread index that atomically leases every source cell for one Copper settlement. */
    static final class CopperSettlementCellReservations {
        private final Map<String, Set<BlockPos>> bySettlement = new HashMap<>();
        private final Set<BlockPos> occupied = new HashSet<>();

        boolean reserve(String settlementKey, List<BlockPos> cells) {
            Set<BlockPos> existing = bySettlement.get(settlementKey);
            if (existing != null) return true;
            Set<BlockPos> requested = Set.copyOf(cells);
            if (requested.isEmpty() || requested.stream().anyMatch(occupied::contains)) {
                return false;
            }
            bySettlement.put(settlementKey, requested);
            occupied.addAll(requested);
            return true;
        }

        boolean contains(int x, int y, int z) {
            return occupied.contains(new BlockPos(x, y, z));
        }

        void release(String settlementKey) {
            Set<BlockPos> released = bySettlement.remove(settlementKey);
            if (released != null) occupied.removeAll(released);
        }
    }
    // [REDSTONE] 신호 엔진 쓰기는 기존 권위 셀 깔때기로, 효과는 쓰기 확정 뒤 방송한다.
    private com.gameexpert.engine.redstone.RuntimeRedstoneHost createRedstoneHost() {
        return new com.gameexpert.engine.redstone.RuntimeRedstoneHost(
                new com.gameexpert.engine.redstone.RuntimeRedstoneHost.Port() {
            @Override public int packedAt(int x, int y, int z) {
                int id = residentBlockType(rt.accessor(), x, y, z);
                return id == UNAVAILABLE_BLOCK ? -1 : id | rt.blockStates().get(x, y, z, id) << 16;
            }
            @Override public int analogOutputAt(int x, int y, int z, int direction) {
                if (residentBlockType(rt.accessor(), x, y, z) == Blocks.DETECTOR_RAIL) {
                    return rt.placedEntities().minecartComparatorSignal(x + .2, y, z + .2, x + .8, y + .8, z + .8);
                }
                return rt.analogOutputAt(x, y, z, direction);
            }
            @Override public int fluidAfterRemoval(int x, int y, int z, int block, int state) {
                return Fluids.isWaterMedium(block, state) ? Blocks.WATER_SOURCE : Blocks.AIR;
            }
            @Override public void dropBlock(int x, int y, int z, int block, int state) {
                redstoneEffects.add(() -> rt.itemSystem().spawnBlockDrop((short) block, x + .5, y + .5, z + .5));
            }
            @Override public boolean primeTnt(int x, int y, int z) {
                redstoneEffects.add(() -> primedTnt.primeBlock(x, y, z, tntRandom));
                return true;
            }
            @Override public void ringBell(int x, int y, int z) {
                redstoneEffects.add(() -> WorldTickLoop.this.ringBell(x, y, z, 2, null));
            }
            @Override public void worldSound(String kind, int x, int y, int z, int block, double pitch) {
                redstoneEffects.add(() -> {
                    WorldSound sound = WorldSound.PITCHED_KINDS.contains(kind)
                            ? new WorldSound(rt.nextEventId(), kind, x + .5, y + .5, z + .5, (short) block, (float) pitch)
                            : new WorldSound(rt.nextEventId(), kind, x + .5, y + .5, z + .5, (short) block);
                    double range = kind.startsWith("note_block_") ? 48 : SoundRules.worldSoundRange(kind);
                    for (PlayerTickState player : rt.players().values()) {
                        if (SoundRules.audible(x + .5, y + .5, z + .5, player.x(), player.y(), player.z(), range)) sendTo(player, sound);
                    }
                });
            }
            @Override public void pistonMove(com.gameexpert.engine.redstone.RedstonePistonMove move) {
                redstoneEffects.add(() -> {
                    Map<String, Object> message = new java.util.LinkedHashMap<>();
                    message.put("type", "pistonMove"); message.put("x", move.x); message.put("y", move.y); message.put("z", move.z);
                    message.put("facing", move.facing); message.put("extending", move.extending); message.put("sticky", move.sticky);
                    message.put("moveDir", move.moveDir); message.put("gameTime", move.gameTime); message.put("blocks", move.blocks);
                    for (PlayerTickState player : rt.players().values()) {
                        if (SoundRules.audible(move.x, move.y, move.z, player.x(), player.y(), player.z(), 64)) sendTo(player, message);
                    }
                });
            }
            @Override public void pushEntities(double ax, double ay, double az, double bx, double by, double bz,
                    int direction, double amount, boolean bounce) {
                redstoneEffects.add(() -> pushRedstoneEntities(ax, ay, az, bx, by, bz, direction, amount, bounce));
            }
            @Override public void consumerNeighborChanged(int x, int y, int z, int id) {
                redstoneConsumers.add(new BlockPos(x, y, z));
            }
            @Override public boolean jukeboxPlaying(int x, int y, int z) { return WorldTickLoop.this.jukeboxPlaying(x, y, z); }
            @Override public int countEntities(double ax, double ay, double az, double bx, double by, double bz, boolean livingOnly) {
                int count = 0;
                for (PlayerTickState p : rt.players().values()) {
                    if (!p.isDead() && redstoneIntersects(p.x() - .3, p.y(), p.z() - .3, p.x() + .3, p.y() + (p.crouching() ? 1.5 : 1.8), p.z() + .3, ax, ay, az, bx, by, bz)) count++;
                }
                for (com.gameexpert.engine.mob.Mob m : rt.mobSystem().redstoneMobs()) {
                    double h = m.width() / 2;
                    if (!m.isDead() && !m.removed && redstoneIntersects(m.x - h, m.y, m.z - h, m.x + h, m.y + m.height(), m.z + h, ax, ay, az, bx, by, bz)) count++;
                }
                if (!livingOnly) {
                    count += rt.itemSystem().itemsIntersecting(ax, ay, az, bx, by, bz).size();
                    count += rt.placedEntities().countMinecarts(ax, ay, az, bx, by, bz);
                }
                return count;
            }
            @Override public boolean hasArrow(double ax, double ay, double az, double bx, double by, double bz) {
                for (com.gameexpert.engine.mob.ProjectileSim p : rt.mobSystem().redstoneProjectiles()) {
                    if (p.kind == com.gameexpert.engine.mob.ProjectileSim.Kind.ARROW && p.alive
                            && redstoneIntersects(p.x - .25, p.y, p.z - .25, p.x + .25, p.y + .5, p.z + .25, ax, ay, az, bx, by, bz)) return true;
                }
                return false;
            }
            @Override public int countMinecarts(double ax, double ay, double az, double bx, double by, double bz) {
                return rt.placedEntities().countMinecarts(ax, ay, az, bx, by, bz);
            }
            @Override public int rawSkyLight(int x, int y, int z) { return rt.mobSystem().redstoneRawSkyLight(x, y, z); }
            @Override public long dayTime() { return rt.clock().worldTime() * 2; }
            @Override public double rainLevel() { return rt.weatherSystem().isRaining() ? 1 : 0; }
            @Override public double thunderLevel() { return "thunder".equals(rt.weatherSystem().snapshot().getKind()) ? 1 : 0; }
            @Override public boolean hasSkyLight() { return "overworld".equals(rt.dimensionKey()); }
            @Override public int chestViewers(int x, int y, int z) {
                var viewers = chestSubscribersByPosition.get(ChestStorage.key(x, y, z));
                return viewers == null ? 0 : viewers.size();
            }
        });
    }

    void forRedstoneCollisionBoxes(int x, int y, int z, BuildingBlockRules.CollisionBoxVisitor visitor) {
        int id = residentBlockType(rt.accessor(), x, y, z);
        if (id < 0) { visitor.visit(0,0,0,1,1,1); return; }
        BuildingBlockRules.forCollisionBoxes(id, rt.blockState(x,y,z,id), x,z,visitor);
        redstone.engine().forMovingCollisionBoxes(x,y,z,visitor);
    }

    com.gameexpert.engine.redstone.RedstoneEngine redstoneEngine() { return redstone.engine(); }
    long redstoneRevision() { return redstoneRevision; }
    String redstoneSnapshot() { return com.gameexpert.engine.redstone.RedstoneSnapshotCodec.encode(redstone.engine().snapshot()); }
    void restoreRedstone(String json) {
        var snapshot = com.gameexpert.engine.redstone.RedstoneSnapshotCodec.decode(json);
        if (snapshot != null) redstone.engine().restore(snapshot);
    }
    void noteRedstoneChange(int x, int y, int z, int oldId, int oldState) {
        if (!applyingRedstone && redstone != null) redstone.engine().noteExternalChange(x, y, z, oldId, oldState);
    }
    void trackLoadedRedstone(int x, int y, int z, int id, int state) {
        redstone.engine().trackLoaded(x, y, z, id, state, rt.clock().gameTimeMcTicks());
    }
    private void flushRedstone() {
        redstone.engine().processExternalChanges(rt.clock().gameTimeMcTicks());
        commitRedstone();
    }
    private void tickRedstone() {
        var engine = redstone.engine();
        engine.processExternalChanges(rt.clock().gameTimeMcTicks());
        for (PlayerTickState p : rt.players().values()) if (!p.isDead()) {
            redstoneEntityBox(p.x() - .3, p.y(), p.z() - .3, p.x() + .3, p.y() + (p.crouching() ? 1.5 : 1.8), p.z() + .3);
        }
        for (com.gameexpert.engine.mob.Mob m : rt.mobSystem().redstoneMobs()) if (!m.isDead() && !m.removed) {
            double h = m.width() / 2;
            redstoneEntityBox(m.x - h, m.y, m.z - h, m.x + h, m.y + m.height(), m.z + h);
        }
        for (ItemEntity i : rt.itemSystem().redstoneItems()) if (i.count > 0) redstoneEntityBox(i.x - .125, i.y, i.z - .125, i.x + .125, i.y + .25, i.z + .125);
        for (var p : rt.mobSystem().redstoneProjectiles()) if (p.alive) redstoneEntityBox(p.x - .25, p.y, p.z - .25, p.x + .25, p.y + .5, p.z + .25);
        for (var cart : rt.placedEntities().welcomeSnapshot()) {
            if (!"armor_stand".equals(cart.getKind())) redstoneEntityBox(cart.getX() - .49, cart.getY(), cart.getZ() - .49, cart.getX() + .49, cart.getY() + .7, cart.getZ() + .49);
        }
        long time = rt.clock().gameTimeMcTicks();
        engine.tickGame(time - 1, false);
        engine.tickGame(time, true);
        commitRedstone();
    }
    private void redstoneEntityBox(double ax, double ay, double az, double bx, double by, double bz) {
        for (int x = (int) Math.floor(ax + 1e-5); x <= (int) Math.floor(bx - 1e-5); x++)
            for (int y = (int) Math.floor(ay + 1e-5); y <= (int) Math.floor(by - 1e-5); y++)
                for (int z = (int) Math.floor(az + 1e-5); z <= (int) Math.floor(bz - 1e-5); z++) redstone.engine().noteEntityCell(x, y, z);
    }
    private void commitRedstone() {
        var engine = redstone.engine();
        var writes = engine.drainWrites();
        applyingRedstone = true;
        try {
            for (var cell : writes) {
                rt.fluidSim().applyChange(cell.x, cell.y, cell.z, cell.blockType);
                rt.setBlockState(cell.x, cell.y, cell.z, cell.blockType, cell.state);
                rt.tickBlockChanges().put(new BlockPos(cell.x, cell.y, cell.z), (short) cell.blockType);
            }
            engine.commitApplied();
        } catch (RuntimeException | Error failure) {
            engine.commitRejected();
            throw failure;
        } finally { applyingRedstone = false; }
        if (engine.takeDirty()) redstoneRevision++;
        var effects = new ArrayList<>(redstoneEffects);
        redstoneEffects.clear();
        for (Runnable effect : effects) effect.run();
    }
    private static boolean redstoneIntersects(double ax, double ay, double az, double bx, double by, double bz,
            double cx, double cy, double cz, double dx, double dy, double dz) {
        return ax < dx && bx > cx && ay < dy && by > cy && az < dz && bz > cz;
    }
    private void pushRedstoneEntities(double ax, double ay, double az, double bx, double by, double bz,
            int direction, double amount, boolean bounce) {
        int dx = com.gameexpert.engine.redstone.RedstoneState.DIR_DX[direction];
        int dy = com.gameexpert.engine.redstone.RedstoneState.DIR_DY[direction];
        int dz = com.gameexpert.engine.redstone.RedstoneState.DIR_DZ[direction];
        int axis = dx != 0 ? 0 : dy != 0 ? 1 : 2, sign = dx != 0 ? dx : dy != 0 ? dy : dz;
        double[] limits = {ax,ay,az,bx,by,bz};
        java.util.function.ToDoubleFunction<double[]> clip = box -> {
            double penetration = sign > 0 ? limits[axis + 3] - box[axis] : box[axis + 3] - limits[axis];
            return Math.abs(com.gameexpert.engine.redstone.RedstonePistonCollision.clip(box, axis,
                sign * Math.min(amount, Math.max(0, penetration + .01)), (x,y,z,visitor) -> {
                    int id = residentBlockType(rt.accessor(),x,y,z);
                    if (id == UNAVAILABLE_BLOCK) { visitor.visit(0,0,0,1,1,1); return; }
                    BuildingBlockRules.forCollisionBoxes(id,rt.blockStates().get(x,y,z,id),x,z,visitor);
                }));
        };
        for (PlayerTickState p : rt.players().values()) {
            if (p.isDead() || !redstoneIntersects(p.x() - .3, p.y(), p.z() - .3, p.x() + .3, p.y() + (p.crouching() ? 1.5 : 1.8), p.z() + .3, ax, ay, az, bx, by, bz)) continue;
            double travel = clip.applyAsDouble(new double[]{p.x()-.3,p.y(),p.z()-.3,p.x()+.3,p.y()+(p.crouching()?1.5:1.8),p.z()+.3});
            p.forcePose(p.x() + dx * travel, p.y() + dy * travel, p.z() + dz * travel, p.yaw(), p.pitch());
            sendTo(p, new PlayerTeleportSelf(p.x(), p.y(), p.z()));
        }
        for (com.gameexpert.engine.mob.Mob m : rt.mobSystem().redstoneMobs()) {
            double h = m.width() / 2;
            if (m.isDead() || m.removed || !redstoneIntersects(m.x - h, m.y, m.z - h, m.x + h, m.y + m.height(), m.z + h, ax, ay, az, bx, by, bz)) continue;
            double travel = clip.applyAsDouble(new double[]{m.x-h,m.y,m.z-h,m.x+h,m.y+m.height(),m.z+h});
            m.x += dx * travel; m.y += dy * travel; m.z += dz * travel;
            if (bounce) { if (dx != 0) { m.horizontalVx=dx*2; m.knockbackVx=dx*2; } if (dy != 0) m.vy=dy*2; if (dz != 0) { m.horizontalVz=dz*2; m.knockbackVz=dz*2; } }
        }
        for (ItemEntity i : rt.itemSystem().itemsIntersecting(ax, ay, az, bx, by, bz)) {
            double travel = clip.applyAsDouble(new double[]{i.x-.125,i.y,i.z-.125,i.x+.125,i.y+.25,i.z+.125});
            i.x += dx * travel; i.y += dy * travel; i.z += dz * travel;
            if (bounce) { double velocity=i.playerThrown ? 1 : 2; if (dx != 0) i.vx=dx*velocity; if (dy != 0) i.vy=dy*velocity; if (dz != 0) i.vz=dz*velocity; }
        }
    }
    void redstoneTargetHit(com.gameexpert.engine.mob.ProjectileSim projectile) {
        var e = redstone.engine();
        e.targetHit(projectile.blockHitX, projectile.blockHitY, projectile.blockHitZ, projectile.blockHitFace,
                projectile.x, projectile.y, projectile.z, projectile.kind == com.gameexpert.engine.mob.ProjectileSim.Kind.ARROW,
                rt.clock().gameTimeMcTicks());
        commitRedstone();
    }

}
