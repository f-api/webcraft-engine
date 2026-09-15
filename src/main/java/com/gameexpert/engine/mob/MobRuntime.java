package com.gameexpert.engine.mob;

import com.gameexpert.common.LongObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.gameexpert.mob.dto.MobPersistenceSnapshot;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.StructureEntityAggregate;
import com.gameexpert.engine.Fluids;
import com.gameexpert.engine.inventory.ArmorSlot;
import com.gameexpert.engine.inventory.HorseMenuContainerAccess;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.TurtleEggRules;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.engine.raid.ProceduralRaidSchedule;
import com.gameexpert.engine.mob.villager.VillagerGossipRules;
import com.gameexpert.engine.mob.villager.VillagerPoiIndex;
import com.gameexpert.engine.mob.villager.VillagerSociety;
import com.gameexpert.engine.BlockFamilies;
import com.gameexpert.engine.blocks.FireflyBushEcology;
import com.gameexpert.frog.persistence.dto.FrogColonyCooldown;
import com.gameexpert.frog.persistence.dto.FrogColonyHydration;
import com.gameexpert.frog.persistence.dto.FrogConversionIntent;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 몹/투사체/스폰을 한 곳에서 틱하는 참조 오케스트레이터. P6 본대는 이 클래스를
 * 그대로 쓰거나(월드당 1개), 어댑터 안에서 동일 순서로 재구성하면 된다.
 *
 * 틱 순서: 몹 AI → 몹 겹침 해소 → ShootArrow 로부터 화살 생성 → 화살 진행
 * → 스폰 → 디스폰 → 죽은/제거 몹 정리.
 * 반환 {@link TickResult} 를 WS 메시지로 매핑:
 *  spawned→mobSpawn, despawned→mobDespawn, mobEvents(AttackPlayer/Explode)→피해·explosion,
 *  projectileHits→명중 판정, 투사체 목록 변화→projectileSpawn/Update/Remove.
 */
public final class MobRuntime implements Mob.VehicleBindingListener {
    private boolean naturalSpawningEnabled = true;

    /** 사용자 차원은 콘텐츠 플러그인이 인구를 소유한다. 기존 개체 AI는 계속 실행한다. */
    public void setNaturalSpawningEnabled(boolean enabled) { naturalSpawningEnabled = enabled; }

    /** Last identity whose successor is still representable by the durable allocator. */
    public static final long MAX_MOB_ID = Long.MAX_VALUE - 1L;

    private final MobSpawner spawner;
    /** Null in pure mob tests; production supplies the resident snapshot-backed shared POI index. */
    private final VillagerPoiIndex villagerPoiIndex;
    /** 주민 번식·철 골렘 소환의 상태·영속 소유자. 침대 claim 은 직업 POI lane 과 별개다. */
    private final VillagerSociety villagerSociety = new VillagerSociety();
    private final ProceduralRaidSchedule raidSchedule = new ProceduralRaidSchedule();
    private final com.gameexpert.engine.raid.RaidLedger raidLedger =
            new com.gameexpert.engine.raid.RaidLedger();
    /**
     * [TRIAL] 트라이얼 스포너 실동작. 레이드와 같은 자리(스폰 배치 → 명단 브랜딩 → 틱 끝
     * 정합)를 쓰되 원장 인스턴스만 따로 둔다 — 스포너는 여러 자리가 동시에 무장된다.
     */
    private final com.gameexpert.engine.trial.TrialSpawnerRuntime trialSpawners =
            new com.gameexpert.engine.trial.TrialSpawnerRuntime();
    // 청크 생성 시 동물 배치 요청(틱 스레드 전용) — spawner 캡과 독립으로 같은 스폰 배치에 합류한다.
    private final java.util.ArrayDeque<SpawnRequest> pendingExternalSpawns = new java.util.ArrayDeque<>();
    /** [TRIAL-GAP] 다음 틱에 세울 불길한 아이템 소환기(트라이얼 스포너가 요청한다). */
    private record PendingOminousItemSpawner(double x, double y, double z, short itemType,
            int count) {}
    private final java.util.ArrayDeque<PendingOminousItemSpawner> pendingOminousItemSpawners =
            new java.util.ArrayDeque<>();
    private final List<Mob> mobs = new ArrayList<>();
    private final List<ProjectileSim> arrows = new ArrayList<>();
    /** Runtime entities hibernate by chunk outside the connected-player simulation union. */
    /** 매 틱 활성 청크 조회에서 Long 키 박싱/HashMap tree-bin 충돌을 피하는 공간 인덱스. */
    private final LongObjectOpenHashMap<LinkedHashSet<Mob>> mobsByChunk =
            new LongObjectOpenHashMap<>(256);
    private final Map<Long, Long> mobChunkById = new HashMap<>();
    private final Map<Long, Mob> mobsById = new HashMap<>();
    /** Retryable animal dependency requests in deterministic mob-id order. */
    private final TreeMap<Long, GoatRamImpactRequest> pendingGoatRamImpactsById = new TreeMap<>();
    private final TreeMap<Long, SnifferDigDropRequest> pendingSnifferDigDropsById = new TreeMap<>();
    /** Rebuilt only when one request is created, changed, confirmed, restored or removed. */
    private List<GoatRamImpactRequest> cachedPendingGoatRamImpacts = List.of();
    private List<SnifferDigDropRequest> cachedPendingSnifferDigDrops = List.of();
    private boolean pendingGoatRamImpactListDirty;
    private boolean pendingSnifferDigDropListDirty;
    /** Valid mob-passenger edges in deterministic passenger-id order. */
    private final TreeMap<Long, Mob> mobPassengersById = new TreeMap<>();
    /** Vanilla's single first-passenger mob seat, indexed by vehicle identity. */
    private final Map<Long, Mob> mobPassengerByVehicleId = new HashMap<>();
    /** Direct package mutations and restored edges rebuild the two indexes only at a semantic boundary. */
    private boolean mobPassengerIndexDirty;
    private boolean suppressVehicleBindingNotification;
    /** Focused regression seam: ordinary clean ticks must not enter the full repair path. */
    private long mobVehicleFullRepairCount;
    /** Focused regression seam: companion-free active ticks must not scan global mob history. */
    private long illagerCompanionGlobalHistoryScanCount;
    /** Durable player-held lead identities, keyed by mob id. */
    private final Map<Long, String> leashHolderByMobId = new HashMap<>();
    private final Map<Long, LinkedHashSet<ProjectileSim>> arrowsByChunk = new HashMap<>();
    private final Map<Long, Long> arrowChunkById = new HashMap<>();
    private final Map<Long, ProjectileSim> arrowsById = new HashMap<>();
    /** 투사체 원장의 실제 생성·제거·활성 물리 변경 세대. 비활성 청크는 세대를 올리지 않는다. */
    private long projectileRevision;
    /** Newly-created entities are also bucketed so an old inactive structure cannot make a later tick scan history. */
    private final Map<Long, LinkedHashSet<Mob>> externallySpawnedByChunk = new HashMap<>();
    /** Original announcement bucket, retained separately because an unannounced mob can move before it dies. */
    private final Map<Long, Long> externallySpawnedChunkById = new HashMap<>();
    /** Immutable rows are updated only at targeted mutations or aggregate persistence boundaries. */
    private final Map<Long, MobPersistenceSnapshot> persistentMobsById = new ConcurrentHashMap<>();
    private final Map<Long, MobPersistenceSnapshot> finalCarrierFactsById = new HashMap<>();
    private StructureEntityDeathListener structureEntityDeathListener = mobId -> { };
    /**
     * Active rows whose live aggregate may have changed since the last persistence boundary.  The
     * owner stores the already-indexed Mob reference rather than allocating one immutable row per
     * tick; a flush materializes these candidates in id order before anything reaches the writer.
     */
    private final LongObjectOpenHashMap<Mob> pendingPersistenceMobs =
            new LongObjectOpenHashMap<>(256);
    /**
     * Synthetic dense-QA residents exercise the ordinary AI/spatial runtime but never become world
     * persistence rows.  The set is allocated only when that exact reserved fixture is installed.
     */
    private Set<Long> ephemeralQaMobIds;
    /** Package-private allocation seam used by focused regression tests. */
    private long persistenceSnapshotConstructionCount;
    private final LoveModeSpatialBuckets loveModeMobs = new LoveModeSpatialBuckets(8.0);
    private final Map<Long, Integer> breedingProgress = new HashMap<>();
    private final Map<Long, Long> breedingPartners = new HashMap<>();
    private final Map<Long, Long> frogColonyReadyMcTick = new HashMap<>();
    private final int[] frogColonySearch = new int[6];
    /** Immutable conversion plans waiting for the FIFO persistence writer. Owner thread only. */
    private final Map<Long, PendingFrogConversion> pendingFrogConversions =
            new java.util.LinkedHashMap<>();
    private boolean frogColonyPersistenceInstalled;
    private final java.util.ArrayDeque<TurtleEggPlacementRequest> turtleEggPlacements =
            new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<FrogspawnPlacementRequest> frogspawnPlacements =
            new java.util.ArrayDeque<>();
    private final Map<Long, FrogspawnPlacementRequest> pendingFrogspawnBySource = new HashMap<>();
    private static final int OWNER_COMBAT_MEMORY_TICKS = 100;
    private record OwnerCombatMemory(long targetMobId, long expiresAtTick) {}
    private final Map<String, OwnerCombatMemory> ownerHurtByMob = new HashMap<>();
    private final Map<String, OwnerCombatMemory> ownerAttackedMob = new HashMap<>();
    /** 이미 자식을 만든 사망 슬라임 ID. 같은 사망을 여러 이벤트 경계에서 중복 처리하지 않는다. */
    private final Set<Long> splitSlimeParents = new HashSet<>();
    /** Permanent death settlement receipt; retained after entity removal to reject duplicate callers. */
    private final Set<Long> permanentDeathSettledMobIds = new HashSet<>();
    /** Cubes reserved by a Froglight settlement remain live until its durable completion arrives. */
    private final Set<Long> froglightSettlementPendingMobIds = new HashSet<>();
    private final int worldSeed;
    private long worldTick;
    /**
     * Shared with durable ENTS reservation, which may claim a range from a persistence worker.
     * The owner still allocates alone; the atomic only orders it against that claim.
     */
    private final java.util.concurrent.atomic.AtomicLong nextMobId =
            new java.util.concurrent.atomic.AtomicLong(1L);
    private long nextArrowId = 1;
    /** 직전 틱에 실제로 활성 처리된 엔티티. 이동으로 활성 union을 벗어난 경우도 다음 틱에 정리한다. */
    private Set<Long> previousActiveMobIds = new HashSet<>();
    private Set<Long> activeMobIdsScratch = new HashSet<>();
    /** 틱 동안 반복되는 활성 union 조회를 공유한다. 구조·청크 이동 변이 때만 dirty로 되돌린다. */
    private boolean mobChunkQueryDirty = true;
    private Set<Long> cachedMobQueryChunks = Set.of();
    private List<Mob> cachedMobsInChunks = List.of();
    private List<Mob> cachedEquipmentMobSource = List.of();
    private List<Mob> cachedEquipmentMobsInChunks = List.of();
    /**
     * 좀비 주민 치료 완료 훅(MOB.md §2). 직업 승계·gossip 부여의 실제 배선은 주민 트랙이
     * 소유하므로 기본값은 NOOP 이고 몹 권위는 완료 사실만 통지한다.
     */
    private ZombieVillagerCureRules.CuredVillagerHook curedVillagerHook =
            ZombieVillagerCureRules.CuredVillagerHook.NOOP;
    private boolean arrowChunkQueryDirty = true;
    private Set<Long> cachedArrowQueryChunks = Set.of();
    private List<ProjectileSim> cachedArrowsInChunks = List.of();

    public MobRuntime() {
        this(players -> List.of(), 0);
    }

    /** 스포너 인덱스 주입 생성자(서버 어댑터용). */
    public MobRuntime(MobSpawner.SpawnerScan spawnerScan) {
        this(spawnerScan, 0);
    }

    /** 월드 시드는 소 성별 해시에만 사용하며 스폰 RNG 소비 순서를 바꾸지 않는다. */
    public MobRuntime(MobSpawner.SpawnerScan spawnerScan, int worldSeed) {
        this(spawnerScan, worldSeed, null);
    }

    /** Server integration constructor sharing one resident POI index with HOME and job-site lanes. */
    public MobRuntime(MobSpawner.SpawnerScan spawnerScan, int worldSeed,
            VillagerPoiIndex villagerPoiIndex) {
        this.spawner = new MobSpawner(spawnerScan);
        this.worldSeed = worldSeed;
        this.villagerPoiIndex = villagerPoiIndex;
    }

    public List<Mob> mobs() { return mobs; }

    public VillagerPoiIndex villagerPoiIndex() { return villagerPoiIndex; }

    /** World persistence adapter for vanilla's wandering-trader delay/chance fields. */
    public long[] traderWindowState() { return spawner.traderWindowState(); }

    public void restoreTraderWindow(long nextAttemptTick, int chancePercent) {
        spawner.restoreTraderWindow(nextAttemptTick, chancePercent);
    }

    /** Resident activation, eviction and replacement all invalidate the old immutable identity. */
    public void invalidateVillagerPoiChunk(int chunkX, int chunkZ) {
        if (villagerPoiIndex != null) villagerPoiIndex.invalidateChunk(chunkX, chunkZ);
    }

    /** 주민 트랙이 직업 승계·gossip 부여를 배선할 자리. null 이면 NOOP 으로 되돌린다. */
    public void setCuredVillagerHook(ZombieVillagerCureRules.CuredVillagerHook hook) {
        this.curedVillagerHook = hook == null
                ? ZombieVillagerCureRules.CuredVillagerHook.NOOP : hook;
    }

    /**
     * [TRIAL-GAP] 불길한 트라이얼 스포너의 아이템 소환기 요청(바닐라 {@code TrialSpawnerState
     * .spawnOminousOminousItemSpawner} → {@code OminousItemSpawner.create}). 다음 틱 스폰 단계에서
     * {@code spawn_item_after_ticks = nextIntBetweenInclusive(60, 120)} 을 굴려 투사체 원장에
     * 세운다. 방송은 투사체 목록 방송이 새 id 로 알린다(틱 스레드 전용).
     */
    public void spawnOminousItemSpawner(double x, double y, double z, short itemType, int count) {
        if (itemType <= 0 || count <= 0) return;
        pendingOminousItemSpawners.add(new PendingOminousItemSpawner(x, y, z, itemType, count));
    }

    /** [TRIAL-GAP] 권위가 만든 투사체(구름·소환기·발사형 아이템)를 원장에 세운다. */
    public ProjectileSim addWorldProjectile(ProjectileSim projectile) {
        projectile.id = nextArrowId++;
        arrows.add(projectile);
        indexArrow(projectile);
        return projectile;
    }

    /** 청크 생성 시 동물 배치 요청을 다음 틱 스폰 배치에 합류시킨다(틱 스레드 전용). */
    public void enqueueExternalSpawns(List<SpawnRequest> requests) {
        pendingExternalSpawns.addAll(requests);
    }

    /** 생성 시 배치 판정에 쓰는 좌표 결정론 배치 계산(스폰은 다음 틱 배치에서 수행). */
    public List<SpawnRequest> planChunkPopulation(MobWorldView world, int chunkX, int chunkZ,
                                                  MobRandom populationRng) {
        return spawner.populateChunk(world, chunkX, chunkZ, populationRng);
    }

    /** Reserve the ordinary initial herd without inserting it into any live or AI index. */
    public List<MobPersistenceSnapshot> prepareChunkPopulationRows(MobWorldView world,
            int chunkX, int chunkZ, MobRandom populationRng) {
        List<MobPersistenceSnapshot> rows = new ArrayList<>();
        for (SpawnRequest request : planChunkPopulation(world, chunkX, chunkZ, populationRng)) {
            Mob mob = MobFactory.create(request.type(), allocateMobId(), request.x(), request.y(),
                    request.z(), worldSeed, request.firstCowInHerd(), request.variant());
            rows.add(snapshotOf(mob, null));
        }
        return List.copyOf(rows);
    }
    public List<ProjectileSim> arrows() { return arrows; }
    public long worldTick() { return worldTick; }

    public void installFrogColonyPersistence(Long worldId, FrogColonyHydration hydration) {
        if (worldId == null || worldId <= 0 || hydration == null) {
            throw new IllegalArgumentException("frog colony persistence is required");
        }
        frogColonyPersistenceInstalled = true;
        frogColonyReadyMcTick.clear();
        pendingFrogConversions.clear();
        for (FrogColonyCooldown cooldown : hydration.getCooldowns()) {
            frogColonyReadyMcTick.put(colonyKey(cooldown.getRafflesiaX(),
                    cooldown.getRafflesiaY(), cooldown.getRafflesiaZ()),
                    cooldown.getNextReadyMcTick());
        }
        for (FrogConversionIntent intent : hydration.getPendingConversions()) {
            prepareRecoveredFrogConversion(intent);
        }
    }

    /** Immutable writer input; no live entity reference crosses the owner boundary. */
    public record FrogConversionWork(
            long sourceMobId, long sequence,
            int colonyX, int colonyY, int colonyZ,
            int hostX, int hostY, int hostZ,
            String deterministicVariant, long currentMcTick,
            MobPersistenceSnapshot replacementSnapshot,
            FrogConversionIntent recoveredIntent) { }

    private record PendingFrogConversion(
            FrogConversionWork work, Mob expectedSource, Mob preparedReplacement) { }

    public enum FrogConversionInstallResult {
        INSTALLED,
        ALREADY_INSTALLED,
        STALE
    }

    /** Stable pending order makes writer admission independent of map/hash iteration. */
    public List<FrogConversionWork> pendingFrogConversions() {
        if (pendingFrogConversions.isEmpty()) return List.of();
        return pendingFrogConversions.values().stream()
                .map(PendingFrogConversion::work)
                .sorted(Comparator.comparingLong(FrogConversionWork::sourceMobId)
                        .thenComparingLong(FrogConversionWork::sequence))
                .toList();
    }

    public boolean hasPendingFrogConversions() {
        return !pendingFrogConversions.isEmpty();
    }

    public boolean frogColonyReady(int x, int y, int z, long currentMcTick) {
        return currentMcTick >= frogColonyReadyMcTick.getOrDefault(colonyKey(x, y, z), 0L);
    }

    public long absoluteMcTick(MobWorldView world) {
        return Math.addExact(Math.multiplyExact(world.dayCount(),
                FrogPoisonConversionRules.DAY_MC_TICKS),
                Math.floorMod(world.worldTime() * 2L,
                        FrogPoisonConversionRules.DAY_MC_TICKS));
    }

    private static long colonyKey(int x, int y, int z) {
        long key = Integer.toUnsignedLong(x);
        key = key * 0x9e3779b97f4a7c15L + Integer.toUnsignedLong(y);
        return key * 0x9e3779b97f4a7c15L + Integer.toUnsignedLong(z);
    }

    public static final class TurtleEggPlacementRequest {
        private final long turtleMobId;
        private final int x;
        private final int y;
        private final int z;
        private final int eggCount;

        TurtleEggPlacementRequest(long turtleMobId, int x, int y, int z, int eggCount) {
            if (eggCount < 1 || eggCount > 4) {
                throw new IllegalArgumentException("invalid Turtle clutch size");
            }
            this.turtleMobId = turtleMobId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.eggCount = eggCount;
        }

        public long turtleMobId() { return turtleMobId; }
        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        public int eggCount() { return eggCount; }
    }

    public static final class FrogspawnPlacementRequest {
        private final long sourceMobId;
        private final long partnerMobId;
        private final int x;
        private final int y;
        private final int z;

        FrogspawnPlacementRequest(long sourceMobId, long partnerMobId, int x, int y, int z) {
            this.sourceMobId = sourceMobId;
            this.partnerMobId = partnerMobId;
            this.x = x; this.y = y; this.z = z;
        }

        public long sourceMobId() { return sourceMobId; }
        public long partnerMobId() { return partnerMobId; }
        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
    }

    public List<FrogspawnPlacementRequest> drainFrogspawnPlacementRequests() {
        if (frogspawnPlacements.isEmpty()) return List.of();
        List<FrogspawnPlacementRequest> drained = new ArrayList<>(frogspawnPlacements);
        frogspawnPlacements.clear();
        return drained;
    }

    public void confirmFrogspawnPlacement(long sourceMobId, long partnerMobId, boolean placed) {
        FrogspawnPlacementRequest pending = pendingFrogspawnBySource.get(sourceMobId);
        if (pending == null || pending.partnerMobId() != partnerMobId) return;
        pendingFrogspawnBySource.remove(sourceMobId);
        Mob first = mobsById.get(sourceMobId);
        Mob second = mobsById.get(partnerMobId);
        if (!placed || first == null || second == null || first.type != MobType.FROG
                || second.type != MobType.FROG) return;
        completeBreedingPair(first, second, sourceMobId);
        snapshotMob(first);
        snapshotMob(second);
    }

    /** World authority drains and atomically validates/places the egg block, then confirms. */
    public List<TurtleEggPlacementRequest> drainTurtleEggPlacementRequests() {
        if (turtleEggPlacements.isEmpty()) return List.of();
        List<TurtleEggPlacementRequest> drained = new ArrayList<>(turtleEggPlacements);
        turtleEggPlacements.clear();
        return drained;
    }

    public void confirmTurtleEggPlacement(long turtleMobId, boolean placed) {
        Mob mob = mobsById.get(turtleMobId);
        if (!(mob instanceof Turtle turtle)) return;
        turtle.confirmEggPlacement(placed);
        snapshotMob(turtle);
    }

    /** Reserve a durable identity even when its stale/dead aggregate is not restored. */
    public void reserveMobIdThrough(long mobId) {
        // Zero is the absence sentinel used by empty relationship ledgers, not an identity.
        if (mobId == 0L) return;
        if (mobId == Long.MAX_VALUE) {
            throw new IllegalStateException("mob id space is exhausted");
        }
        requireValidMobId(mobId);
        advanceMobIdHighWater(mobId);
    }

    @FunctionalInterface
    public interface StructureEntityDeathListener { void onPermanentDeath(long mobId); }

    public void setStructureEntityDeathListener(StructureEntityDeathListener listener) {
        structureEntityDeathListener = listener == null ? mobId -> { } : listener;
    }

    /** Publishes exact ENTS mobs only after their database transaction commits. */
    public void installCommittedStructureEntities(List<MobPersistenceSnapshot> snapshots) {
        Objects.requireNonNull(snapshots, "committed ENTS snapshots");
        // The whole committed batch must pass the detached row gates before the first live mob
        // enters any index. A later malformed HMI1 row must not leave an earlier row half-published.
        for (MobPersistenceSnapshot snapshot : snapshots) {
            if (snapshot == null) throw new IllegalArgumentException("committed ENTS row required");
            requireValidMobId(snapshot.getMobId());
            validateHorseInventoryPayload(snapshot);
        }
        for (MobPersistenceSnapshot snapshot : snapshots) {
            Mob existing = mobsById.get(snapshot.getMobId());
            if (existing != null) {
                if (!snapshot.equals(persistenceSnapshot(snapshot.getMobId()))) {
                    throw new IllegalStateException("conflicting live ENTS mob " + snapshot.getMobId());
                }
            } else {
                restore(snapshot);
            }
        }
    }

    public static MobPersistenceSnapshot finalCarrierSnapshot(
            StructureEntityAggregate.PlannedEntity planned, int worldSeed,
            String variant, String villagerBiomeType, String villagerProfession,
            int villagerLevel) {
        Objects.requireNonNull(planned, "planned ENTS mob");
        MobType type = switch (planned.entityKey()) {
            case "minecraft:witch" -> MobType.WITCH;
            case "minecraft:cat" -> MobType.CAT;
            case "minecraft:villager" -> MobType.VILLAGER;
            case "minecraft:zombie_villager" -> MobType.ZOMBIE_VILLAGER;
            case "minecraft:vindicator" -> MobType.VINDICATOR;
            case "minecraft:evoker" -> MobType.EVOKER;
            case "minecraft:allay" -> MobType.ALLAY;
            default -> throw new IllegalArgumentException(
                    "unsupported live ENTS entity: " + planned.entityKey());
        };
        MobRuntime detached = new MobRuntime(players -> List.of(), worldSeed);
        Mob mob = detached.addMobWithReservedId(type, planned.authoritativeEntityId(),
                planned.x(), planned.y(), planned.z(), true);
        mob.yaw = Math.toRadians(planned.yaw());
        mob.horizontalVx = planned.velocityX();
        mob.vy = planned.velocityY();
        mob.horizontalVz = planned.velocityZ();
        return detached.persistenceSnapshot(mob.id).withFinalCarrierFacts(
                variant, planned.yaw(), planned.pitch(), planned.velocityX(), planned.velocityY(),
                planned.velocityZ(), villagerBiomeType, villagerProfession, villagerLevel);
    }

    /** Exact immutable identity/provenance projection of one final-carrier mob binding. */
    public static boolean sameFinalCarrierBinding(MobPersistenceSnapshot expected,
            MobPersistenceSnapshot actual) {
        return expected != null && actual != null
                && expected.getMobId() == actual.getMobId()
                && expected.isFinalCarrierBinding() && actual.isFinalCarrierBinding()
                && expected.isPersistenceRequired() == actual.isPersistenceRequired()
                && Objects.equals(expected.getType(), actual.getType())
                && Objects.equals(expected.getVariant(), actual.getVariant())
                // 자세(yaw/pitch)와 속도, 위치는 플레이 중 당연히 변한다. 여기서 비교하면 몹이
                // 한 번 돌기만 해도 재입장 때 청크 승인이 전부 실패한다. 결속 동일성은 정체성
                // 사실(식별자, 종류, 변종, 주민 정보)로만 판정한다.
                && Objects.equals(expected.getVillagerBiomeType(), actual.getVillagerBiomeType())
                && Objects.equals(expected.getVillagerProfession(), actual.getVillagerProfession())
                && expected.getVillagerLevel() == actual.getVillagerLevel();
    }

    /** 생성 출처의 직업은 결속 사실이다. 현재 직업 변경은 주민 거래/직업 원장이 소유한다. */
    public String intrinsicVillagerProfession(long mobId) {
        Mob live = mobsById.get(mobId);
        if (!(live instanceof Villager) || live.isDead() || live.removed) return null;
        MobPersistenceSnapshot carrier = finalCarrierFactsById.get(mobId);
        return carrier == null ? null : carrier.getVillagerProfession();
    }

    /**
     * Structure-spawn VillagerData facts (biome type, profession, level) of a villager or zombie
     * villager, inherited across conversions; null for mobs without final-carrier facts.
     */
    public MobPersistenceSnapshot villagerCarrierFacts(long mobId) {
        return finalCarrierFactsById.get(mobId);
    }

    /** Restores a live projectile before the runtime starts, preserving its durable identity. */
    public void restoreProjectile(ProjectileSim projectile) {
        if (projectile == null || projectile.id <= 0 || arrowsById.containsKey(projectile.id)) {
            throw new IllegalArgumentException("invalid or duplicate projectile restore");
        }
        arrows.add(projectile);
        indexArrow(projectile);
        if (projectile.id == Long.MAX_VALUE) {
            throw new IllegalStateException("projectile id space is exhausted");
        }
        nextArrowId = Math.max(nextArrowId, projectile.id + 1L);
    }

    public long projectileRevision() {
        return projectileRevision;
    }

    /** Starts the one raid already authorized by the player Raid Omen lifecycle. */
    public boolean armRaid(long villageAnchorMobId, long tick) {
        return raidSchedule.arm(villageAnchorMobId, tick, null);
    }

    public boolean armRaid(long villageAnchorMobId, long tick, String heroNickname) {
        return raidSchedule.arm(villageAnchorMobId, tick, heroNickname);
    }

    /** [RAID-OMEN] 습격의 징조 레벨(amplifier + 1)을 싣고 arm 한다. */
    public boolean armRaid(long villageAnchorMobId, long tick, String heroNickname, int omenLevel) {
        return raidSchedule.arm(villageAnchorMobId, tick, heroNickname, omenLevel);
    }

    /**
     * [RAID-OMEN] 바닐라 {@code Raids.createOrExtendRaid}: 그 자리의 활성 레이드({@code getRaidAt})가 이미 있으면 새로
     * 만들지 않고 습격의 징조를 흡수한다.
     *
     * @return 흡수했으면 true
     */
    public boolean absorbRaidOmen(double x, double y, double z, int amplifier) {
        com.gameexpert.engine.raid.RaidLedger.Instance raid = raidLedger.raidAt(
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), worldTick);
        if (raid == null || !raid.ongoing()) return false;
        return raidLedger.absorbOmen(raid.raidId(), amplifier);
    }

    /** [RAID-OMEN] 이번 틱까지 승리로 끝난 레이드(영웅 효과 부여용). 부른 쪽이 비운다. */
    private final List<com.gameexpert.engine.raid.RaidLedger.Instance> raidVictories = new ArrayList<>();

    public List<com.gameexpert.engine.raid.RaidLedger.Instance> drainRaidVictories() {
        if (raidVictories.isEmpty()) return List.of();
        List<com.gameexpert.engine.raid.RaidLedger.Instance> drained = List.copyOf(raidVictories);
        raidVictories.clear();
        return drained;
    }

    /** [RAID-OMEN] 합류 때 굳힐 레이드 강화(마법 부여) 레벨. */
    private int raidBuffOnJoin(Mob member) {
        com.gameexpert.engine.raid.RaidLedger.Instance raid = raidLedger.instance(member.raidId());
        int omen = raid == null ? 1 : raid.omenLevel();
        return com.gameexpert.engine.raid.RaidLedger.raidBuffEnchantLevel(member.type, member.raidWave(), omen,
                member.raidId(), member.id);
    }

    /** Reserved content-QA entry into the same procedural raid schedule. */
    public boolean armQaRaid(long villageAnchorMobId, long tick, String heroNickname,
            int requiredRoleMask, int releaseWindowTicks) {
        return raidSchedule.armQa(villageAnchorMobId, tick, heroNickname,
                requiredRoleMask, releaseWindowTicks);
    }

    public boolean qaRaidConstraintMatches(long villageAnchorMobId, int requiredRoleMask,
            int releaseWindowTicks) {
        return raidSchedule.qaConstraintMatches(villageAnchorMobId, requiredRoleMask,
                releaseWindowTicks);
    }

    /** Membership/outcome/reward ledger for the armed raids of this world. */
    public com.gameexpert.engine.raid.RaidLedger raidLedger() { return raidLedger; }

    /** [TRIAL] 트라이얼 스포너·금고 권위. 인덱스가 찾아 준 좌표만 본다. */
    public com.gameexpert.engine.trial.TrialSpawnerRuntime trialSpawners() {
        return trialSpawners;
    }

    /**
     * Reattaches restored raiders to their persisted ledger rows. Health comes from the mob row so
     * the boss bar resumes at the same number instead of snapping back to full.
     */
    public void restoreRaidMembership() {
        // 명부/anchor 행은 mob table보다 늦게 커밋될 수 있다. 그 행이 참조한 숫자 ID까지
        // 할당 상한에 포함해야, 사라진 anchor/member ID를 첫 자연 스폰이 재사용하지 않는다.
        reserveMobIdThrough(raidLedger.highestReferencedMobId());
        for (Mob mob : mobs) {
            // A committed straight-charge checkpoint may outlive the pufferfish it targeted. Its
            // numeric reference remains authoritative until the dash ends, so a newly allocated
            // pufferfish must not inherit that id after restart.
            long chargeTargetId = mob.zombieNautilusChargeTargetMobId();
            if (chargeTargetId > 0L && chargeTargetId < Long.MAX_VALUE) {
                reserveMobIdThrough(chargeTargetId);
            }
            if (mob.raidId() == 0L || mob.isDead() || mob.removed) continue;
            if (raidLedger.rejoin(mob.raidId(), mob.id, mob.exactHealth())) {
                com.gameexpert.engine.raid.RaidLedger.Instance raid = raidLedger.instance(mob.raidId());
                if (raid != null) mob.setRaidBuffLevel(raid.memberEnchantLevel(mob.id));
                continue;
            }
            // Standalone commits the mob aggregate before its separate raid store. A process death
            // between them leaves a correctly branded living raider ahead of the older roster.
            // Rebuild only into an already-existing ongoing raid; never guess a missing instance.
            int rebuiltBuff = raidBuffOnJoin(mob);
            if (raidLedger.join(mob.raidId(), mob.id, mob.raidWave(), mob.maxHp(), rebuiltBuff)) {
                raidLedger.observeMember(mob.id, mob.exactHealth());
                mob.setRaidBuffLevel(rebuiltBuff);
                continue;
            }
            mob.clearRaid();
            refreshPersistenceSnapshot(mob);
        }
        // 모든 mob 행을 읽은 뒤에도 짝이 없는 live roster 행은 crash residue다. 첫 틱의 신규
        // 스폰이 그 숫자 ID를 재사용하기 전에 즉시 은퇴시켜 엉뚱한 레이더 승계를 막는다.
        raidLedger.retireMissingMembers((raidId, mobId) -> {
            Mob mob = mobsById.get(mobId);
            return mob != null && !mob.isDead() && !mob.removed && mob.raidId() == raidId;
        });
    }

    /** O(1) membership check for the server integration; avoids a historical-mob list scan after active ticking. */
    public boolean containsMob(long mobId) { return mobChunkById.containsKey(mobId); }

    private record HiveCell(int x, int y, int z) { }
    /** Live and hibernated hive residents, retained in stable mob-id order per hive. */
    private final Map<HiveCell, TreeMap<Long, Bee>> beeHiveResidents = new HashMap<>();
    /** Reverse edge makes automatic exit and permanent removal O(log residents) without a history scan. */
    private final Map<Long, HiveCell> beeHiveCellByMobId = new HashMap<>();

    private static HiveCell beeHiveCell(Bee bee) {
        return new HiveCell((int) Math.floor(bee.homeX()),
                (int) Math.floor(bee.homeY()), (int) Math.floor(bee.homeZ()));
    }

    private void indexBeeHiveResident(Bee bee) {
        if (!bee.inHive() || bee.isDead() || bee.removed || mobsById.get(bee.id) != bee) {
            unindexBeeHiveResident(bee);
            return;
        }
        HiveCell cell = beeHiveCell(bee);
        HiveCell previous = beeHiveCellByMobId.put(bee.id, cell);
        if (previous != null && !previous.equals(cell)) {
            removeBeeHiveResident(previous, bee.id);
        }
        beeHiveResidents.computeIfAbsent(cell, ignored -> new TreeMap<>()).put(bee.id, bee);
    }

    private void unindexBeeHiveResident(Bee bee) {
        HiveCell cell = beeHiveCellByMobId.remove(bee.id);
        if (cell != null) removeBeeHiveResident(cell, bee.id);
    }

    private void removeBeeHiveResident(HiveCell cell, long mobId) {
        TreeMap<Long, Bee> residents = beeHiveResidents.get(cell);
        if (residents == null) return;
        residents.remove(mobId);
        if (residents.isEmpty()) beeHiveResidents.remove(cell);
    }

    /** Prunes only one hive's at-most-three normal residents; ordinary ticks never scan mob history. */
    private TreeMap<Long, Bee> validatedBeeHiveResidents(HiveCell cell) {
        TreeMap<Long, Bee> residents = beeHiveResidents.get(cell);
        if (residents == null) return null;
        for (Iterator<Map.Entry<Long, Bee>> iterator = residents.entrySet().iterator();
                iterator.hasNext();) {
            Map.Entry<Long, Bee> entry = iterator.next();
            Bee bee = entry.getValue();
            HiveCell reverse = beeHiveCellByMobId.get(entry.getKey());
            if (reverse != null && reverse.equals(cell) && mobsById.get(entry.getKey()) == bee
                    && bee.inHive() && !bee.isDead() && !bee.removed
                    && beeHiveCell(bee).equals(cell)) continue;
            iterator.remove();
            if (cell.equals(reverse)) beeHiveCellByMobId.remove(entry.getKey());
        }
        if (residents.isEmpty()) {
            beeHiveResidents.remove(cell);
            return null;
        }
        return residents;
    }

    /** O(1) authoritative lookup for events emitted by active ticking. */
    public Mob mobById(long mobId) { return mobsById.get(mobId); }

    /** 예약 QA 호출은 시작된 치료의 시간만 줄이고, 실제 변환은 다음 정상 몹 틱에 맡긴다. */
    public boolean qaStageZombieVillagerCure(long mobId) {
        if (mobId <= 0 || mobId > 9_007_199_254_740_991L) return false;
        Mob mob = mobsById.get(mobId);
        if (!(mob instanceof ZombieVillager zombie) || mob.isDead() || mob.removed
                || zombie.conversionMcTicks() <= 2) return false;
        String starter = zombie.conversionStarter();
        if (starter == null || starter.isBlank()) return false;
        zombie.restoreConversion(2, starter);
        return true;
    }

    public boolean confirmFroglightSettlement(long sulfurCubeMobId) {
        Mob cube = mobsById.get(sulfurCubeMobId);
        froglightSettlementPendingMobIds.remove(sulfurCubeMobId);
        if (cube == null) return true;
        if (cube.type != MobType.SULFUR_CUBE) return false;
        cube.removed = true;
        removeMob(cube);
        return true;
    }

    public boolean confirmBeeHiveEntry(long mobId, int x, int y, int z) {
        Mob candidate = mobsById.get(mobId);
        if (!(candidate instanceof Bee bee) || bee.inHive() || candidate.isDead()
                || candidate.removed) return false;
        HiveCell cell = new HiveCell(x, y, z);
        TreeMap<Long, Bee> residents = validatedBeeHiveResidents(cell);
        int occupants = residents == null ? 0 : residents.size();
        if (occupants >= 3 || !bee.confirmHiveEntry(x, y, z)) {
            bee.rejectHiveEntry();
            return false;
        }
        indexBeeHiveResident(bee);
        snapshotMob(bee);
        return true;
    }

    public int releaseBeesFromHive(int x, int y, int z, String angerTargetNickname) {
        HiveCell cell = new HiveCell(x, y, z);
        TreeMap<Long, Bee> residents = validatedBeeHiveResidents(cell);
        // Explicit block release is rare. Repair any externally-corrupted missing reverse edge here
        // instead of paying a full historical scan on every world tick.
        for (Mob mob : mobs) {
            if (!(mob instanceof Bee bee) || !bee.inHive() || mob.isDead() || mob.removed
                    || !beeHiveCell(bee).equals(cell)) continue;
            indexBeeHiveResident(bee);
        }
        residents = validatedBeeHiveResidents(cell);
        if (residents == null) return 0;
        int released = 0;
        for (Bee bee : new ArrayList<>(residents.values())) {
            bee.leaveHive(angerTargetNickname);
            unindexBeeHiveResident(bee);
            reindexMob(bee);
            snapshotMob(bee);
            released++;
        }
        return released;
    }

    public List<Mob> addInitialBeeNestOccupants(int x, int y, int z, int count) {
        if (count < 2 || count > 3) {
            throw new IllegalArgumentException("natural bee nest resident count must be 2..3");
        }
        return addExactBeeNestOccupants(x, y, z,
                java.util.Collections.nCopies(count, 0));
    }

    /** Adds the ordered inhabitants carried by an exact final-chunk BEES sidecar. */
    public List<Mob> addExactBeeNestOccupants(
            int x, int y, int z, List<Integer> ticksInHive) {
        ticksInHive = List.copyOf(java.util.Objects.requireNonNull(
                ticksInHive, "ticksInHive"));
        if (ticksInHive.isEmpty() || ticksInHive.size() > 3) {
            throw new IllegalArgumentException("bee nest resident count must be 1..3");
        }
        for (int ticks : ticksInHive) {
            if (ticks < 0 || ticks > 598) {
                throw new IllegalArgumentException("bee ticksInHive must be 0..598");
            }
        }
        List<Mob> added = new ArrayList<>(ticksInHive.size());
        for (int ticks : ticksInHive) {
            Mob mob = addMob(MobType.BEE, x + 0.5, y + 0.5, z + 0.5, true);
            Bee bee = (Bee) mob;
            bee.restorePersistentBeeState(false, 0, false,
                    x + 0.5, y + 0.5, z + 0.5, ticks + 1);
            indexBeeHiveResident(bee);
            snapshotMob(bee);
            added.add(bee);
        }
        return List.copyOf(added);
    }

    /** Player interaction boundary for giving one filter item to an Allay. */
    public boolean giveAllayItem(long mobId, String nickname, short itemType, int durability) {
        Mob mob = mobsById.get(mobId);
        if (!(mob instanceof Allay allay) || mob.isDead() || mob.removed
                || !allay.giveItem(nickname, itemType, durability)) return false;
        snapshotMob(allay);
        return true;
    }

    /** Empty-hand interaction boundary. The caller inserts the returned item into player inventory. */
    public Mob.EquipmentDrop takeAllayItem(long mobId, String nickname) {
        Mob mob = mobsById.get(mobId);
        if (!(mob instanceof Allay allay) || mob.isDead() || mob.removed) return null;
        Mob.EquipmentDrop item = allay.takeGivenItem(nickname);
        if (item != null) snapshotMob(allay);
        return item;
    }

    /** Item-system search hook; no persistence write is needed until a pickup is acknowledged. */
    public boolean offerAllayDroppedItem(long mobId, long itemEntityId, short itemType,
            int durability, int count, double x, double y, double z) {
        Mob mob = mobsById.get(mobId);
        return mob instanceof Allay allay && !mob.isDead() && !mob.removed
                && allay.offerMatchingDroppedItem(
                        itemEntityId, itemType, durability, count, x, y, z);
    }

    /** Item-system atomic pickup acknowledgement. */
    public boolean confirmAllayPickup(long mobId, long itemEntityId, int count) {
        Mob mob = mobsById.get(mobId);
        if (!(mob instanceof Allay allay) || !allay.confirmPickup(itemEntityId, count)) {
            return false;
        }
        snapshotMob(allay);
        return true;
    }

    /** Player-inventory atomic return acknowledgement. */
    public boolean confirmAllayReturn(long mobId, int count) {
        Mob mob = mobsById.get(mobId);
        if (!(mob instanceof Allay allay) || !allay.confirmReturnedItems(count)) return false;
        snapshotMob(allay);
        return true;
    }

    public void rememberOwnerHurtByMob(String ownerNickname, long attackerMobId) {
        if (ownerNickname == null || ownerNickname.isBlank() || !mobsById.containsKey(attackerMobId)) return;
        ownerHurtByMob.put(ownerNickname,
                new OwnerCombatMemory(attackerMobId, worldTick + OWNER_COMBAT_MEMORY_TICKS));
    }

    public void rememberOwnerAttackedMob(String ownerNickname, long targetMobId) {
        if (ownerNickname == null || ownerNickname.isBlank() || !mobsById.containsKey(targetMobId)) return;
        ownerAttackedMob.put(ownerNickname,
                new OwnerCombatMemory(targetMobId, worldTick + OWNER_COMBAT_MEMORY_TICKS));
    }

    /**
     * [TRIAL-GAP] 점액 효과({@code OozingMobEffect.spawnSlimeOffspring}): 크기 2 슬라임 하나를
     * 이 좌표에 세운다.
     */
    public Mob spawnOozingSlime(double x, double y, double z) {
        Slime child = new Slime(allocateMobId(), x, y, z, 2);
        mobs.add(child);
        indexMob(child);
        snapshotMob(child);
        return child;
    }

    /** 사망한 슬라임을 바닐라 분열 규칙으로 교체한다. */
    public List<Mob> splitSlimeOnDeath(Mob parent, MobRandom rng) {
        if (!parent.isDead() || !splitSlimeParents.add(parent.id)) return List.of();
        if (!(parent instanceof Slime slime) || slime.size() <= 1) {
            // Non-splitting deaths must not consume the exactly-once ledger.
            splitSlimeParents.remove(parent.id);
            return List.of();
        }
        int childSize = slime.size() / 2;
        int childCount = 2 + rng.nextInt(3);
        List<Mob> children = new ArrayList<>(childCount);
        for (int index = 0; index < childCount; index++) {
            Slime child = new Slime(allocateMobId(),
                    parent.x + (rng.nextDouble() - 0.5) * 0.5,
                    parent.y,
                    parent.z + (rng.nextDouble() - 0.5) * 0.5,
                    childSize);
            mobs.add(child);
            indexMob(child);
            snapshotMob(child);
            children.add(child);
        }
        return children;
    }

    /** External vehicle movement must update the same chunk bucket used by active-union ticking. */
    public void refreshSpatialIndex(Mob mob) {
        if (mob != null && mobsById.get(mob.id) == mob) reindexMob(mob);
    }

    /** WebCraft 10TPS에서 Java Edition의 실시간 번식 수치를 보존한다. */
    public static final int LOVE_MODE_TICKS = 30 * 10;
    public static final int BREEDING_COOLDOWN_TICKS = 5 * 60 * 10;
    public static final int BABY_GROWTH_TICKS = 20 * 60 * 10;
    static final int COURTSHIP_TICKS = 3 * 10;
    static final double PARTNER_SEARCH_RANGE = 8.0;
    static final double BIRTH_DISTANCE_SQUARED = 9.0;

    /** 먹이·리치 검증을 끝낸 종별 구현이 호출하는 love-mode 진입 위임점. */
    public boolean enterLoveMode(Mob mob, int ticks) {
        if (!mob.enterLoveMode(ticks)) return false;
        loveModeMobs.refresh(mob);
        return true;
    }

    public void clearLoveMode(Mob mob) {
        mob.clearLoveMode();
        loveModeMobs.refresh(mob);
    }

    public void setBabyAge(Mob mob, int ticks) {
        mob.setAgeTicksRemaining(ticks);
        if (ticks > 0 && mob.type == MobType.GOAT) mob.restoreGoatHornMask(0);
        loveModeMobs.refresh(mob);
    }

    /** 성체는 love mode, 새끼는 성장 가속. 성공한 경우에만 호출자가 먹이를 소비한다. */
    public FeedResult feedBreedingFood(Mob mob, short itemType) {
        if (mob == null || mob.isDead() || !mob.type.isBreedingFood(itemType)) return FeedResult.NOT_FOOD;
        if (mob.type == MobType.CAT && mob.ownerNickname() == null) return FeedResult.NOT_READY;
        if (mob.isBaby()) {
            mob.accelerateBabyGrowth(10);
            return FeedResult.BABY_GROWTH;
        }
        return enterLoveMode(mob, LOVE_MODE_TICKS) ? FeedResult.LOVE_MODE : FeedResult.NOT_READY;
    }

    public enum FeedResult { LOVE_MODE, BABY_GROWTH, NOT_READY, NOT_FOOD }

    /** 지정 범위의 같은 종 partner를 love-mode 전용 버킷에서만 찾는다. */
    public Mob findBreedingPartner(Mob mob, double range) {
        loveModeMobs.refresh(mob);
        return loveModeMobs.nearestPartner(mob, range);
    }

    int indexedLoveModeMobCount() { return loveModeMobs.indexedCount(); }

    public Mob addMob(MobType type, double x, double y, double z) {
        return addMob(type, x, y, z, false);
    }

    public Mob addMob(MobType type, double x, double y, double z,
                      boolean persistenceRequired) {
        return addMob(type, x, y, z, persistenceRequired, null);
    }

    /** [DRAGON] 변종을 지정해 세운다(엔드 수정 받침 show_bottom/hide_bottom). */
    public Mob addMobWithVariant(MobType type, double x, double y, double z,
                                 boolean persistenceRequired, String variant) {
        return addMob(type, x, y, z, persistenceRequired, variant);
    }

    /** Registers one reserved-fixture resident without constructing or publishing a persistence DTO. */
    public Mob addEphemeralQaMob(MobType type, double x, double y, double z) {
        long id = allocateMobId();
        Mob mob = MobFactory.create(type, id, x, y, z, worldSeed, false, null);
        if (ephemeralQaMobIds == null) ephemeralQaMobIds = new HashSet<>();
        ephemeralQaMobIds.add(id);
        mobs.add(mob);
        indexMob(mob);
        long chunk = entityChunkKey(mob.x, mob.z);
        externallySpawnedByChunk.computeIfAbsent(chunk, ignored -> new LinkedHashSet<>()).add(mob);
        externallySpawnedChunkById.put(mob.id, chunk);
        return mob;
    }

    /** Recreates the exact identity carried by a validated WCMB1 aquatic-mob bucket. */
    public Mob addBucketMob(BucketMobPayloadCodec.Payload payload,
            double x, double y, double z) {
        if (payload == null) throw new IllegalArgumentException("bucket mob payload is required");
        // Encode is the single strict validator shared with capture/decode.
        BucketMobPayloadCodec.encode(payload.type(), payload.variant(),
                payload.tadpoleAgeMcTicks(), payload.customName());
        Mob mob = addMob(payload.type(), x, y, z, true, payload.variant());
        mob.restoreTadpoleAge(payload.tadpoleAgeMcTicks());
        mob.setCustomName(payload.customName());
        snapshotMob(mob);
        return mob;
    }

    public long reserveMobId() {
        return allocateMobId();
    }

    /** Non-consuming view used to align a joining durable identity sequence; safe on any thread. */
    public long currentMobIdFloor() { return nextMobId.get(); }

    /**
     * 워커의 ENTS 예약이 [minimum, through] 구간을 가져간다. 소유 스레드가 그 사이 minimum 이상을
     * 이미 발급했으면 구간이 겹칠 수 있으므로 거절한다. 성공하면 이후 발급은 through 뒤에서 시작한다.
     */
    public boolean claimMobIdsFrom(long minimum, long through) {
        requireValidMobId(minimum);
        requireValidMobId(through);
        if (through < minimum) throw new IllegalArgumentException("empty mob id claim");
        long successor = through == MAX_MOB_ID ? Long.MAX_VALUE : through + 1L;
        while (true) {
            long current = nextMobId.get();
            if (current <= 0L || current > minimum) return false;
            if (nextMobId.compareAndSet(current, Math.max(current, successor))) return true;
        }
    }

    /** Validates a persisted or externally reserved identity before it can enter any live index. */
    public static long requireValidMobId(long mobId) {
        if (mobId <= 0L || mobId > MAX_MOB_ID) {
            throw new IllegalArgumentException("mob id must be positive and less than Long.MAX_VALUE");
        }
        return mobId;
    }

    /** Allocates one identity and leaves Long.MAX_VALUE as the terminal, non-allocatable sentinel. */
    private long allocateMobId() {
        while (true) {
            long allocated = nextMobId.get();
            if (allocated <= 0L || allocated > MAX_MOB_ID) {
                throw new IllegalStateException("mob id space is exhausted");
            }
            long successor = allocated == MAX_MOB_ID ? Long.MAX_VALUE : allocated + 1L;
            if (nextMobId.compareAndSet(allocated, successor)) return allocated;
        }
    }

    /** Advances the high-water mark without ever evaluating an overflowing successor. */
    private void advanceMobIdHighWater(long mobId) {
        requireValidMobId(mobId);
        if (nextMobId.get() <= 0L) {
            throw new IllegalStateException("mob id sequence is invalid");
        }
        long successor = mobId == MAX_MOB_ID ? Long.MAX_VALUE : mobId + 1L;
        nextMobId.accumulateAndGet(successor, Math::max);
    }

    public void rollbackExternalMob(long mobId, MobType expectedType) {
        Mob mob = mobsById.get(mobId);
        if (mob != null && mob.type == expectedType) removeMob(mob);
    }

    public void rejectCopperGolemStatue(long mobId) {
        Mob mob = mobsById.get(mobId);
        if (mob instanceof CopperGolem golem) {
            golem.rejectStatue();
            snapshotMob(golem);
        }
    }

    public void confirmCopperGolemStatue(long mobId) {
        Mob mob = mobsById.get(mobId);
        if (mob instanceof CopperGolem) removeMob(mob);
    }

    public Mob addMobWithReservedId(MobType type, long id, double x, double y, double z,
            boolean persistenceRequired) {
        requireValidMobId(id);
        Mob existing = mobsById.get(id);
        if (existing != null) return existing;
        Mob mob = MobFactory.create(type, id, x, y, z, worldSeed, false, null);
        if (persistenceRequired) mob.setPersistenceRequired(true);
        advanceMobIdHighWater(id);
        mobs.add(mob); indexMob(mob); snapshotMob(mob);
        long chunk = entityChunkKey(mob.x, mob.z);
        externallySpawnedByChunk.computeIfAbsent(chunk, ignored -> new LinkedHashSet<>()).add(mob);
        externallySpawnedChunkById.put(mob.id, chunk);
        return mob;
    }

    /**
     * Builds the exact immutable persistence row for an externally reserved animal identity
     * without publishing that mob into any live runtime index. Animal hatch settlements use this
     * while their database transaction is on the persistence writer; the owner installs the same
     * deterministic identity only after that transaction commits.
     */
    public MobPersistenceSnapshot detachedAnimalDependencySnapshot(
            MobType type, long id, double x, double y, double z) {
        if (type != MobType.TADPOLE && type != MobType.SNIFFER) {
            throw new IllegalArgumentException("unsupported animal dependency type: " + type);
        }
        requireValidMobId(id);
        Mob detached = MobFactory.create(type, id, x, y, z, worldSeed, false, null);
        detached.setPersistenceRequired(true);
        return snapshotOf(detached, null);
    }

    public MobPersistenceSnapshot detachedCopperGolemSnapshot(long id,
            double x, double y, double z, String customName, short heldItem,
            int heldDurability, int pose) {
        requireValidMobId(id);
        Mob detached = MobFactory.create(MobType.COPPER_GOLEM, id, x, y, z,
                worldSeed, false, null);
        detached.setPersistenceRequired(true);
        if (detached instanceof CopperGolem golem && customName != null) {
            golem.restoreFromStatue(customName, heldItem, heldDurability, pose);
        }
        return snapshotOf(detached, null);
    }

    private Mob addMob(MobType type, double x, double y, double z,
                       boolean persistenceRequired, String variant) {
        long id = allocateMobId();
        Mob m = MobFactory.create(type, id, x, y, z, worldSeed, false, variant);
        if (persistenceRequired) m.setPersistenceRequired(true);
        mobs.add(m);
        indexMob(m);
        long chunk = entityChunkKey(m.x, m.z);
        externallySpawnedByChunk.computeIfAbsent(chunk, ignored -> new LinkedHashSet<>()).add(m);
        externallySpawnedChunkById.put(m.id, chunk);
        snapshotMob(m);
        return m;
    }

    /**
     * 번식 새끼의 변종. 아홀로틀만 Java Axolotl.getBreedOffspring 처럼 먼저 {@code nextInt(1200)}
     * 을 소비해 0이면 rare 목록(blue 한 종)에서 {@code nextInt(1)} 로 고르고, 아니면 두 부모 중
     * 하나를 고른다. 따라서 blue 부모가 B마리면 새끼 blue 확률은 1/1200 + 1199/1200 × B/2 다.
     *
     * <p>package-private 인 이유는 <b>난수 소비 순서 자체가 계약</b>이기 때문이다. 이 lane 의
     * 굴림 순서는 {@code tick()} 을 통째로 돌려서는 관측할 수 없다 — 같은 틱의 스폰 lane 이
     * {@code nextInt(alive.size())} 처럼 <b>같은 bound</b> 를 쓰는 굴림을 섞어 넣기 때문에
     * bound 만 보고 기록하면 남의 굴림이 딸려 들어온다. 정적판
     * {@code standaloneBreedingChildVariant} 와 <b>같은 결</b>로 이 함수만 직접 부른다.
     */
    static String breedingChildVariant(MobType childType, Mob first, Mob second,
                                       MobRandom rng, int worldSeed) {
        if (childType != first.type) return null;
        if (first.type == MobType.AXOLOTL) {
            if (rng.nextInt(1200) == 0) {
                rng.nextInt(1);
                return "blue";
            }
            return rng.nextInt(2) == 0 ? first.variant() : second.variant();
        }
        // [FARM-VARIANT] 소·돼지·닭의 1.21.5 기후 변종. «Pig»: <i>"the variant of the baby is
        // randomly selected from one of the parents' variants"</i>. 바닐라는 여기서 난수를
        // 하나 더 뽑지만 이 저장소는 **좌표·부모 id 해시**로 같은 균등 1/2 을 만든다
        // (divergence [C]): 세 종은 지금까지 번식 lane 에서 난수를 소비한 적이 없어, 새 굴림을
        // 끼우면 그 뒤의 모든 굴림이 밀려 기존 종의 난수 프리픽스가 깨진다. 해시는 RNG 를
        // 한 칸도 소비하지 않으므로 프리픽스가 글자 그대로 보존되고, 두 권위는 같은 해시를
        // 쓰므로 파리티도 유지된다.
        //
        // 소는 **기후 낱말만** 돌려준다. 성별은 MobFactory 가 기존 결정론 가중표로 뽑고
        // FarmAnimalVariantRules.resolveCowVariant 가 그 뒤에 이 기후를 합성한다.
        if (first.type == MobType.COW) {
            return inheritedHashedVariant(worldSeed,
                    FarmAnimalVariantRules.cowClimateOf(first.variant()),
                    FarmAnimalVariantRules.cowClimateOf(second.variant()), first, second);
        }
        if (first.type == MobType.PIG || first.type == MobType.CHICKEN) {
            return inheritedHashedVariant(worldSeed,
                    first.variant(), second.variant(), first, second);
        }
        if (first.type == MobType.POISON_DART_FROG) {
            return inheritedHashedVariant(worldSeed,
                    first.variant(), second.variant(), first, second);
        }
        if (first.type == MobType.HORSE) {
            int firstIndex = horseVariantIndex(first.variant());
            int secondIndex = horseVariantIndex(second.variant());
            int colorRoll = rng.nextInt(9);
            int color = colorRoll < 4 ? firstIndex / 5
                    : colorRoll < 8 ? secondIndex / 5 : rng.nextInt(7);
            int markingRoll = rng.nextInt(5);
            int marking = markingRoll < 2 ? firstIndex % 5
                    : markingRoll < 4 ? secondIndex % 5 : rng.nextInt(5);
            return MobType.HORSE.variantAt(color * 5 + marking);
        }
        boolean inherits = first.type == MobType.RABBIT || first.type == MobType.FOX
                || first.type == MobType.LLAMA || first.type == MobType.MOOSHROOM
                || first.type == MobType.WOLF || first.type == MobType.CAT;
        return inherits ? (rng.nextInt(2) == 0 ? first.variant() : second.variant()) : null;
    }

    private static int horseVariantIndex(String variant) {
        for (int i = 0; i < MobType.HORSE.variantCount(); i++) {
            if (MobType.HORSE.variantAt(i).equals(variant)) return i;
        }
        throw new IllegalArgumentException("Invalid Horse variant: " + variant);
    }

    /** 두 부모 기후 중 하나를 RNG 소비 없이 균등하게 고른다(§[FARM-VARIANT] divergence [C]). */
    private static String inheritedHashedVariant(int worldSeed, String firstClimate,
                                                     String secondClimate, Mob first, Mob second) {
        if (firstClimate == null || firstClimate.equals(secondClimate)) {
            return firstClimate != null ? firstClimate : secondClimate;
        }
        if (secondClimate == null) return firstClimate;
        return MobVariant.deterministicIndex(worldSeed, first.id ^ (second.id << 1),
                first.x, first.z, 2) == 0 ? firstClimate : secondClimate;
    }

    /**
     * [PARCHED-FAMILY] 종의 최대 체력이 <b>줄어든</b> 트랙 뒤에 남은 옛 행의 체력 마이그레이션.
     *
     * <p>파치드는 계열 정정으로 최대 체력이 <b>20 → 16</b> 이 됐다. 정정 이전에 저장된 파치드
     * 행은 17~20 을 들고 있을 수 있고, 그대로 두면
     * {@code Mob#restorePersistentCombatState} 의 {@code exactHealth > maxHp()} 검증이
     * {@code IllegalStateException} 을 던져 <b>그 개체가 통째로 사라진다</b>(사용자에게는 세이브
     * 손상으로 보인다). 열대어·돼지·닭의 {@code variant == null} 승격
     * ({@code MobFactory.restore})과 같은 자리·같은 성격의 마이그레이션 계약이다.
     *
     * <p><b>상한을 넘는 값만</b> 새 상한으로 내린다. 상한 이하는 한 톨도 건드리지 않으므로
     * 정정 이후에 저장된 행과 다른 모든 종에는 아무 영향이 없고, 상한 미만/0 이하 같은 진짜
     * 손상은 여전히 검증에 걸린다. 상한을 줄이는 트랙이 또 생기면 이 명단에 종만 더한다.
     */
    private static double migratedPersistedHealth(Mob mob, double persistedHealth) {
        if (mob.type != MobType.PARCHED) return persistedHealth;
        return Math.min(persistedHealth, mob.maxHp());
    }

    /** 저장된 ID와 외형/소유 상태를 그대로 복원하고 다음 신규 ID를 충돌하지 않게 전진시킨다. */
    public Mob restore(MobPersistenceSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("mob snapshot required");
        requireValidMobId(snapshot.getMobId());
        // Validate the complete HMI1/scalar pair before constructing or publishing any live state.
        validateHorseInventoryPayload(snapshot);
        MobType type = MobType.valueOf(snapshot.getType());
        if (mobsById.containsKey(snapshot.getMobId())) {
            throw new IllegalStateException("duplicate mob id " + snapshot.getMobId());
        }
        int persistedSlimeSize = snapshot.getSlimeSize();
        if (type != MobType.SLIME && persistedSlimeSize != 0) {
            throw new IllegalStateException("non-Slime mob has persisted slime size");
        }
        Mob mob = MobFactory.restore(type, snapshot.getMobId(), snapshot.getX(), snapshot.getY(),
                snapshot.getZ(), snapshot.getVariant(), worldSeed, persistedSlimeSize);
        mob.yaw = Math.toRadians(snapshot.getYaw());
        mob.horizontalVx = snapshot.getVelocityX();
        mob.vy = snapshot.getVelocityY();
        mob.horizontalVz = snapshot.getVelocityZ();
        boolean restoredFinalCarrier = snapshot.isFinalCarrierBinding();
        MooshroomRules.restoreStoredFlower(mob, snapshot.getMooshroomStoredFlower());
        mob.setOwnerNickname(snapshot.getOwnerNickname());
        mob.restoreCompanionState(snapshot.isCompanionSitting(), snapshot.getCatCollarColor() == null
                ? type == MobType.CAT ? 14 : -1 : snapshot.getCatCollarColor());
        mob.restoreOcelotTrusting(snapshot.isOcelotTrusting());
        mob.restoreWolfSitting(snapshot.isWolfSitting());
        int wolfCollarColor = snapshot.getWolfCollarColor();
        if (type == MobType.WOLF && wolfCollarColor == -1) {
            wolfCollarColor = Wolf.DEFAULT_COLLAR_COLOR;
        }
        mob.restoreWolfSpeciesState(wolfCollarColor, snapshot.getWolfArmorDurability());
        mob.restoreTadpoleAge(snapshot.getTadpoleAgeMcTicks());
        if (mob instanceof Pufferfish pufferfish) {
            pufferfish.restorePuffState(snapshot.getPufferPuffStage());
        } else if (snapshot.getPufferPuffStage() != 0) {
            throw new IllegalStateException("non-Pufferfish mob has persisted PuffState");
        }
        if (mob instanceof PoisonDartFrog frog) {
            frog.restoreSpeciesState(
                    snapshot.getPoisonDartFrogFeedCooldownMcTicks(),
                    snapshot.getPoisonDartFrogDefensiveTicks(),
                    snapshot.getPoisonDartFrogContactCooldownTicks(),
                    snapshot.getPoisonDartFrogFeedSequence(),
                    snapshot.getPoisonDartFrogPendingFeedSequence(),
                    snapshot.getPoisonDartFrogPendingFeedX(),
                    snapshot.getPoisonDartFrogPendingFeedY(),
                    snapshot.getPoisonDartFrogPendingFeedZ());
        } else if (snapshot.getPoisonDartFrogFeedCooldownMcTicks() != 0
                || snapshot.getPoisonDartFrogDefensiveTicks() != 0
                || snapshot.getPoisonDartFrogContactCooldownTicks() != 0
                || snapshot.getPoisonDartFrogFeedSequence() != 0
                || snapshot.getPoisonDartFrogPendingFeedSequence() != 0
                || snapshot.getPoisonDartFrogPendingFeedX() != 0
                || snapshot.getPoisonDartFrogPendingFeedY() != 0
                || snapshot.getPoisonDartFrogPendingFeedZ() != 0) {
            throw new IllegalStateException("non-poison-dart-frog has persisted species state");
        }
        if (mob instanceof SkeletonHorse skeletonHorse) {
            skeletonHorse.restoreTrapState(snapshot.isSkeletonHorseTrapActive(),
                    snapshot.getSkeletonHorseTrapAgeMcTicks());
        } else if (snapshot.isSkeletonHorseTrapActive()
                || snapshot.getSkeletonHorseTrapAgeMcTicks() != 0) {
            throw new IllegalStateException("non-Skeleton-Horse has persisted trap state");
        }
        if (mob instanceof CopperGolem copperGolem) {
            copperGolem.restoreCopperState(snapshot.getCopperGolemOxidationAge(),
                    snapshot.isCopperGolemWaxed(), snapshot.getCopperGolemPose(),
                    snapshot.getCopperGolemNextWeatheringMcTick(),
                    snapshot.isCopperGolemStatuePending());
        } else if (snapshot.getCopperGolemOxidationAge() != 0
                || snapshot.isCopperGolemWaxed() || snapshot.getCopperGolemPose() != 0
                || snapshot.getCopperGolemNextWeatheringMcTick() != CopperGolem.WEATHERING_UNSET
                || snapshot.isCopperGolemStatuePending()) {
            throw new IllegalStateException("non-Copper-Golem has persisted copper state");
        }
        int sheepColor = snapshot.getSheepColor();
        // 양 상태가 없던 옛 행은 색을 잃었으므로 스폰과 같은 결정론적 기본색으로 되살린다.
        if (type == MobType.SHEEP && sheepColor == -1) sheepColor = mob.sheepColor();
        int chickenEggMcTicks = snapshot.getChickenEggMcTicks();
        // 산란 커서가 없던 옛 행이 복원 직후 알을 떨구지 않도록 새 커서를 그대로 둔다.
        if (type == MobType.CHICKEN && chickenEggMcTicks == 0) {
            chickenEggMcTicks = mob.chickenEggMcTicks();
        }
        mob.restoreFarmAnimalState(sheepColor, snapshot.isSheepSheared(),
                snapshot.getSheepEatMcTicks(), chickenEggMcTicks,
                snapshot.isPigSaddled(), snapshot.getPigBoostMcTicks(),
                snapshot.getPigBoostTotalMcTicks());
        // [MOUNT] 말 계열 길들이기·안장·개체 스탯과 상자·카펫·힘. 스탯·힘이 없던 옛 행은 생성
        // 롤을 그대로 둔다. 힘은 화물 칸 수를 정하므로 상자보다 먼저 들어간다(9-arg 오버로드).
        mob.restoreHorseState(snapshot.isHorseTamed(), snapshot.getHorseTemper(),
                snapshot.isHorseSaddled(), snapshot.getHorseMaxHealth(),
                snapshot.getHorseSpeed(), snapshot.getHorseJumpStrength(),
                snapshot.isHorseChested(), snapshot.getLlamaCarpetColor(),
                snapshot.getLlamaStrength());
        mob.restoreHorseArmor(snapshot.getHorseArmorItem());
        mob.restoreGoatHornMask(snapshot.getGoatHornMask());
        mob.restoreGoatRamState(snapshot.getGoatRamPhase(),
                snapshot.getGoatRamCooldownMcTicks(), snapshot.getGoatRamTargetNickname(),
                snapshot.getGoatRamTargetX(), snapshot.getGoatRamTargetZ(),
                snapshot.getGoatRamRunUpX(), snapshot.getGoatRamRunUpZ(),
                snapshot.getGoatRamDirectionX(), snapshot.getGoatRamDirectionZ(),
                snapshot.getGoatRamPrepareMcTicks(), snapshot.getGoatRamDistance(),
                snapshot.getGoatRamSequence(), snapshot.getGoatRamPendingBlockId(),
                snapshot.getGoatRamPendingX(), snapshot.getGoatRamPendingY(),
                snapshot.getGoatRamPendingZ(), snapshot.isGoatRamPendingPreferLeft());
        mob.restoreSnifferDigState(snapshot.getSnifferDigPhase(),
                snapshot.getSnifferDigCooldownMcTicks(), snapshot.getSnifferDigPhaseMcTicks(),
                snapshot.getSnifferDigDropDelayMcTicks(), snapshot.getSnifferDigTargetX(),
                snapshot.getSnifferDigTargetY(), snapshot.getSnifferDigTargetZ(),
                snapshot.getSnifferDigSearchEpoch(), snapshot.getSnifferDigSequence(),
                snapshot.getSnifferPendingDropSequence(), snapshot.getSnifferPendingDropItem());
        String restoredLeashHolder = snapshot.getLeashHolderNickname();
        if (restoredLeashHolder != null) {
            if (!leashable(mob.type)) {
                throw new IllegalStateException("non-leashable mob has persisted lead holder");
            }
        }
        // [MOUNT] 낙타 안장·자세. 좌석과 대시 쿨다운은 휘발 상태라 복원하지 않는다.
        mob.restoreCamelState(snapshot.isCamelSaddled(), snapshot.isCamelSitting());
        if (mob.horseEquipment() != null) {
            HorseMenuCodec.restoreForLoad(mob, snapshot.getHorseInventoryData(),
                    new HorseMenuCodec.RevisionSnapshot(
                            snapshot.getHorseMenuPersistenceRevision(),
                            snapshot.getHorseEquipmentPersistenceRevision(),
                            snapshot.getCargoPersistenceRevision()));
        }
        // [HARNESS] 해피 가스트 하네스 장착·색. 좌석은 휘발 상태라 복원하지 않는다(낙타와 같다).
        mob.restoreHappyGhastState(
                snapshot.isHappyGhastHarnessed(), snapshot.getHappyGhastHarnessColor());
        // [NAUTILUS-MOUNT] 안장·갑옷. 좌석은 휘발 상태라 복원하지 않는다(낙타·하네스와 같다).
        // 이 줄은 refreshPersistenceSnapshot 의 withNautilusMountState 와 **복원 게이트 쌍**이다 —
        // 한쪽만 있으면 안장을 얹은 개체가 재시작 뒤 맨몸으로 돌아온다.
        mob.restoreNautilusMountState(
                snapshot.isNautilusSaddled(), snapshot.getNautilusArmorTier());
        // 낙뢰로 켜진 차지드 표식은 재시작을 건너 살아남는다(바닐라 Creeper NBT powered).
        mob.restoreCreeperPowered(snapshot.isCreeperPowered());
        mob.restoreCreeperIgnited(snapshot.isCreeperIgnited());
        mob.restoreTrialEquipmentNoDrop(snapshot.isTrialEquipmentNoDrop());
        // [GLOWING] 활성 상태이상(바닐라 active_effects). 손상된 목록은 효과 없이 복원한다.
        try {
            mob.statusEffects().restorePersistence(snapshot.getStatusEffects());
        } catch (IllegalArgumentException invalid) {
            mob.statusEffects().clear();
        }
        try {
            mob.restoreZombieVillagerConversion(
                    snapshot.getZombieVillagerConversionMcTicks(),
                    snapshot.getZombieVillagerConversionStarter());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException(
                    "invalid persisted Zombie Villager conversion state", invalid);
        }
        String villagerSocial = snapshot.getVillagerSocial();
        if (mob instanceof Villager villager) {
            villager.restoreSocialState(villagerSocial);
        } else if (villagerSocial != null) {
            throw new IllegalStateException("non-Villager mob has persisted villager social state");
        }
        mob.setCustomName(snapshot.getCustomName());
        mob.restoreBreedingState(snapshot.getAgeTicksRemaining(), snapshot.getBreedingCooldownTicks(),
                snapshot.getLoveTicksRemaining());
        mob.restoreBabyForm(snapshot.isBabyForm());
        mob.restoreWaterLifecycleState(
                snapshot.getAirSupplyTicks(), snapshot.getUnderwaterConversionTicks());
        mob.restorePersistentCombatState(
                migratedPersistedHealth(mob, snapshot.getHealthPoints()),
                snapshot.isPickedUpEquipment(),
                snapshot.getHeldItem(), snapshot.getHeldItemDurability(),
                snapshot.getHelmetItem(), snapshot.getHelmetDurability(),
                snapshot.getChestplateItem(), snapshot.getChestplateDurability(),
                snapshot.getLeggingsItem(), snapshot.getLeggingsDurability(),
                snapshot.getBootsItem(), snapshot.getBootsDurability());
        // [MOB-EQUIP] 칸별 성분·드랍 계층. 옛 행(null)은 위 전투 복원과 트라이얼 표식이 파생한 값 그대로다.
        mob.restoreEquipmentComponents(snapshot.getEquipmentComponents());
        if (mob instanceof Allay allay) {
            try {
                allay.restoreDelivery(snapshot.getAllayDeliveryCount(),
                        snapshot.getAllayDeliveryDurability());
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("invalid persisted Allay delivery", invalid);
            }
        } else if (snapshot.getAllayDeliveryCount() != 0
                || snapshot.getAllayDeliveryDurability() != 0) {
            throw new IllegalStateException("non-Allay mob has persisted delivery state");
        }
        if (mob instanceof Turtle turtle) {
            turtle.restoreEggState(snapshot.isTurtleGravid(), snapshot.getTurtleHomeX(),
                    snapshot.getTurtleHomeY(), snapshot.getTurtleHomeZ(),
                    snapshot.isTurtleTravelingHome(), snapshot.getTurtleEggDigMcTicks(),
                    snapshot.getTurtleEggCount());
        } else if (snapshot.isTurtleGravid() || snapshot.getTurtleHomeX() != 0
                || snapshot.getTurtleHomeY() != 0 || snapshot.getTurtleHomeZ() != 0
                || snapshot.isTurtleTravelingHome() || snapshot.getTurtleEggDigMcTicks() != 0
                || snapshot.getTurtleEggCount() != 0) {
            throw new IllegalStateException("non-Turtle mob has persisted egg state");
        }
        // [EC-MOBS] 셜커 부착면·peek, 아이템 액자 방향·회전·넣은 아이템의 인챈트/성분.
        if (mob instanceof HangingMaw maw) {
            maw.restoreAttachment(snapshot.getAttachFace(), snapshot.getAttachState());
        } else if (mob instanceof Shulker shulker) {
            shulker.restoreShulkerState(snapshot.getAttachFace(), snapshot.getAttachState());
        } else if (mob instanceof ItemFrame frame) {
            frame.restoreFrameState(snapshot.getAttachFace(), snapshot.getAttachState(),
                    snapshot.getHeldItemEnchantments(), snapshot.getHeldItemComponents());
        } else if (snapshot.getAttachFace() != 0 || snapshot.getAttachState() != 0
                || snapshot.getHeldItemEnchantments() != 0L || snapshot.getHeldItemComponents() != null) {
            throw new IllegalStateException("non-attached mob has persisted attach state");
        }
        if (mob instanceof Enderman enderman) {
            enderman.restoreCarriedBlock(snapshot.getCarriedBlock());
        } else if (mob instanceof SulfurCube cube) {
            cube.restoreSulfurCubeState(snapshot.getCarriedBlock(),
                    snapshot.getSulfurCubePickupCooldownTicks(),
                    snapshot.getSulfurCubeFuseTicks(), snapshot.getSulfurCubeMaxFuseTicks(),
                    snapshot.isSulfurCubeFromBucket());
        } else if (snapshot.getCarriedBlock() != 0) {
            throw new IllegalStateException("non-Enderman mob carries a persisted block");
        }
        if (mob instanceof Drowned drowned) {
            drowned.restoreCarrierState(
                    snapshot.isDrownedCarrierRolled(), snapshot.isDrownedTridentCarrier(),
                    snapshot.isDrownedShellCarrier());
            try {
                drowned.restoreJockeyDecision(
                        snapshot.isDrownedJockeyDecisionArmed(),
                        snapshot.isDrownedJockeyDecisionSettled(),
                        snapshot.isDrownedJockeyDecisionWinner());
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("invalid persisted Drowned jockey state", invalid);
            }
        } else if (snapshot.isDrownedCarrierRolled() || snapshot.isDrownedTridentCarrier()
                || snapshot.isDrownedShellCarrier()) {
            throw new IllegalStateException("non-Drowned mob has persisted Drowned state");
        }
        try {
            mob.restoreMobVehicle(snapshot.getVehicleMobId());
        } catch (IllegalArgumentException invalid) {
            // Corrupt/self links are safely cleared and never turn into a fresh jockey roll.
            mob.restoreMobVehicle(0L);
        }
        if (mob instanceof Bee bee) {
            bee.restorePersistentBeeState(
                    snapshot.isBeeHasStung(), snapshot.getBeeDeathAfterStingTicks(),
                    snapshot.isBeeHasNectar(),
                    snapshot.getBeeHomeX(), snapshot.getBeeHomeY(), snapshot.getBeeHomeZ(),
                    snapshot.getBeeHiveTicks());
        } else if (snapshot.isBeeHasStung() || snapshot.getBeeDeathAfterStingTicks() != 0
                || snapshot.isBeeHasNectar() || snapshot.getBeeHiveTicks() != 0
                || snapshot.getBeeHomeX() != 0.0
                || snapshot.getBeeHomeY() != 0.0 || snapshot.getBeeHomeZ() != 0.0) {
            throw new IllegalStateException("non-Bee mob has persisted Bee state");
        }
        if (mob instanceof Vex vex) {
            vex.bindSummoner(snapshot.getVexSummonerMobId(), snapshot.getVexLimitedLifeTicks());
        } else if (snapshot.getVexSummonerMobId() != 0
                || snapshot.getVexLimitedLifeTicks() != 0) {
            throw new IllegalStateException("non-Vex mob has persisted Vex state");
        }
        String angerTarget = snapshot.getAngerTargetNickname();
        int angerTicks = snapshot.getAngerTicks();
        if (mob instanceof Piglin piglin) {
            piglin.restoreAnger(angerTarget, angerTicks);
        } else if (mob instanceof ZombifiedPiglin piglin) {
            piglin.restoreAnger(angerTarget, angerTicks);
        } else if (mob instanceof Pigman pigman) {
            pigman.restoreAnger(angerTarget, angerTicks);
        } else if (mob instanceof ZombiePigman pigman) {
            pigman.restoreAnger(angerTarget, angerTicks);
        } else if (mob instanceof NautilusFamilyMob nautilus) {
            nautilus.restoreAnger(angerTarget, angerTicks);
        } else if (angerTarget != null || angerTicks != 0) {
            throw new IllegalStateException("unrelated mob has persisted anger");
        }
        if (mob instanceof Ravager ravager) {
            ravager.restoreActionTicks(
                    snapshot.getRavagerAttackTicks(),
                    snapshot.getRavagerStunnedTicks(),
                    snapshot.getRavagerRoarTicks());
        } else if (snapshot.getRavagerAttackTicks() != 0
                || snapshot.getRavagerStunnedTicks() != 0
                || snapshot.getRavagerRoarTicks() != 0) {
            throw new IllegalStateException("non-Ravager mob has persisted Ravager action state");
        }
        String armadilloShellState = snapshot.getArmadilloShellState();
        int armadilloStateTicks = snapshot.getArmadilloStateMcTicks();
        int armadilloDangerTicks = snapshot.getArmadilloDangerMcTicks();
        int armadilloScuteTicks = snapshot.getArmadilloScuteTicks();
        if (mob instanceof Armadillo armadillo) {
            if (armadilloShellState == null) {
                throw new IllegalStateException("Armadillo is missing persisted shell state");
            }
            try {
                armadillo.restoreArmadilloState(
                        Armadillo.ShellState.valueOf(armadilloShellState),
                        armadilloStateTicks, armadilloDangerTicks,
                        armadilloScuteTicks);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("invalid persisted Armadillo state", invalid);
            }
        } else if (armadilloShellState != null || armadilloStateTicks != 0
                || armadilloDangerTicks != 0 || armadilloScuteTicks != 0) {
            throw new IllegalStateException("non-Armadillo mob has persisted Armadillo state");
        }
        if (mob instanceof Dolphin dolphin) {
            dolphin.restoreDolphinState(
                    snapshot.getDolphinMoistureTicks(), snapshot.getDolphinDryDamageMcTicks(),
                    snapshot.isDolphinGotFish(),
                    snapshot.getDolphinTreasureX(), snapshot.getDolphinTreasureY(),
                    snapshot.getDolphinTreasureZ());
        } else if (snapshot.getDolphinMoistureTicks() != 0
                || snapshot.getDolphinDryDamageMcTicks() != 0
                || snapshot.isDolphinGotFish()
                || snapshot.getDolphinTreasureX() != 0
                || snapshot.getDolphinTreasureY() != 0
                || snapshot.getDolphinTreasureZ() != 0) {
            throw new IllegalStateException("non-Dolphin mob has persisted Dolphin state");
        }
        if (mob instanceof NautilusFamilyMob nautilus) {
            try {
                nautilus.restoreChargeState(
                        NautilusFamilyMob.ChargePhase.valueOf(
                                snapshot.getZombieNautilusChargePhase()),
                        snapshot.getZombieNautilusChargeCooldownMcTicks(),
                        snapshot.getZombieNautilusChargeTargetNickname(),
                        snapshot.getZombieNautilusChargeTargetMobId(),
                        snapshot.getZombieNautilusChargeVx(),
                        snapshot.getZombieNautilusChargeVy(),
                        snapshot.getZombieNautilusChargeVz(),
                        snapshot.getZombieNautilusChargeDistance(),
                        snapshot.getZombieNautilusNaturalTargetCooldownMcTicks());
            } catch (IllegalArgumentException | NullPointerException invalid) {
                throw new IllegalStateException("invalid persisted Zombie Nautilus state", invalid);
            }
        } else if (!"IDLE".equals(snapshot.getZombieNautilusChargePhase())
                || snapshot.getZombieNautilusChargeCooldownMcTicks() != 0
                || snapshot.getZombieNautilusChargeTargetNickname() != null
                || snapshot.getZombieNautilusChargeTargetMobId() != 0
                || snapshot.getZombieNautilusChargeVx() != 0.0
                || snapshot.getZombieNautilusChargeVy() != 0.0
                || snapshot.getZombieNautilusChargeVz() != 0.0
                || snapshot.getZombieNautilusChargeDistance() != 0.0
                || snapshot.getZombieNautilusNaturalTargetCooldownMcTicks() != 0) {
            throw new IllegalStateException(
                    "non-Zombie-Nautilus mob has persisted charge state");
        }
        if (mob instanceof Illusioner illusioner) {
            try {
                illusioner.restoreIllusionerState(
                        snapshot.getIllusionerInvisibilityMcTicks(),
                        snapshot.getIllusionerMirrorCooldownMcTicks(),
                        snapshot.getIllusionerBlindnessCooldownMcTicks(),
                        snapshot.getIllusionerCastMcTicks(),
                        snapshot.getIllusionerBlindTargetKey());
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("invalid persisted Illusioner state", invalid);
            }
        } else if (snapshot.getIllusionerInvisibilityMcTicks() != 0
                || snapshot.getIllusionerMirrorCooldownMcTicks() != 0
                || snapshot.getIllusionerBlindnessCooldownMcTicks() != 0
                || snapshot.getIllusionerCastMcTicks() != 0
                || snapshot.getIllusionerBlindTargetKey() != null) {
            throw new IllegalStateException("non-Illusioner mob has persisted spell state");
        }
        // 개체별 1/128 판정은 자격 있는 handler 역할에만 남는다. 재기동 뒤에도 재굴림하지 않는다.
        if (snapshot.isCompanionDecisionSettled()
                && !IllagerCompanionPolicy.isEligibleRole(
                        IllagerCompanionPolicy.roleFor(mob.type))) {
            throw new IllegalStateException("ineligible mob has a settled companion decision");
        }
        try {
            mob.restoreCompanionDecision(snapshot.isCompanionDecisionSettled(),
                    snapshot.isCompanionDecisionWinner());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("invalid persisted companion decision", invalid);
        }
        String illagerContextName = snapshot.getIllagerContext();
        if (illagerContextName != null) {
            IllagerCompanionPolicy.Context context;
            try {
                context = IllagerCompanionPolicy.Context.valueOf(illagerContextName);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("invalid persisted illager context", invalid);
            }
            if (mob instanceof IllagerCompanionMob companion) {
                companion.bindHandler(snapshot.getCompanionHandlerMobId(), context,
                        snapshot.getIllagerContextIdentity(),
                        snapshot.getCompanionAppearanceLane(),
                        snapshot.getIllagerPolicyVersion(),
                        snapshot.getCompanionRebindCount());
                companion.restoreEncounterState(
                        new IllagerCompanionMob.CompanionEncounterState(
                                snapshot.getCompanionAbilityPhase(),
                                snapshot.getCompanionAbilityTicks(),
                                snapshot.getCompanionCooldownTicks(),
                                snapshot.getCompanionTargetNickname(),
                                snapshot.getCompanionMarkTicks(),
                                snapshot.getCompanionChargeDistance(),
                                snapshot.getCompanionOrphanTicks()));
            } else if (IllagerCompanionPolicy.isEligibleRole(
                    IllagerCompanionPolicy.roleFor(mob.type))
                    && snapshot.getCompanionHandlerMobId() == 0
                    && snapshot.getCompanionAppearanceLane() == 0
                    && snapshot.getCompanionRebindCount() == 0) {
                mob.assignIllagerContext(context, snapshot.getIllagerContextIdentity(),
                        snapshot.getIllagerPolicyVersion());
            } else {
                throw new IllegalStateException("invalid persisted illager encounter member");
            }
        } else if (snapshot.getIllagerContextIdentity() != 0
                || snapshot.getIllagerPolicyVersion() != 0
                || snapshot.getCompanionHandlerMobId() != 0
                || snapshot.getCompanionAppearanceLane() != 0
                || snapshot.getCompanionRebindCount() != 0) {
            throw new IllegalStateException("orphan persisted illager encounter state");
        }
        if (!(mob instanceof IllagerCompanionMob)
                && !("IDLE".equals(snapshot.getCompanionAbilityPhase())
                && snapshot.getCompanionAbilityTicks() == 0
                && snapshot.getCompanionCooldownTicks() == 0
                && snapshot.getCompanionTargetNickname() == null
                && snapshot.getCompanionMarkTicks() == 0
                && snapshot.getCompanionChargeDistance() == 0.0
                && snapshot.getCompanionOrphanTicks() == 0)) {
            throw new IllegalStateException("non-companion mob has encounter checkpoint state");
        }
        long raidId = snapshot.getRaidId();
        int raidWave = snapshot.getRaidWave();
        if (raidId != 0L) {
            if (!MobRelationshipPolicy.isRaidMembershipType(mob.type)) {
                throw new IllegalStateException("non-raider mob has a persisted raid membership");
            }
            mob.assignRaid(raidId, raidWave);
        } else if (raidWave != 0) {
            throw new IllegalStateException("orphan persisted raid wave");
        }
        if (snapshot.isPersistenceRequired()) mob.setPersistenceRequired(true);
        // All decoding and species/state validation above is performed on the detached mob. Only
        // after it is known-good do auxiliary facts and the live indexes become observable.
        if (restoredFinalCarrier) finalCarrierFactsById.put(mob.id, snapshot);
        if (restoredLeashHolder != null) leashHolderByMobId.put(mob.id, restoredLeashHolder);
        mobs.add(mob);
        indexMob(mob);
        advanceMobIdHighWater(snapshot.getMobId());
        snapshotMob(mob);
        return mob;
    }

    /**
     * 현재 말 메뉴 payload의 저장 로드 경계 검증. 엔티티 계층에는
     * {@link HorseMenuCodec}에 넘길 생존 몹이 없으므로 같은 저장 형태의 분리 몹으로 검증한 뒤
     * 행을 로드 캐시에 넣는다. 구 화물 입력은 일부러 받지 않으며 생존 런타임은 바꾸지 않는다.
     */
    public static void validateHorseInventoryPayload(MobPersistenceSnapshot snapshot) {
        validateSnapshotEnvelope(snapshot);
        validateHorseRevisionTuple(snapshot);
        MobType type = MobType.valueOf(snapshot.getType());
        if (!horseMenuType(type)) {
            if (snapshot.getHorseInventoryData() != null) {
                throw new IllegalArgumentException("non-horse mob has horse inventory payload");
            }
            if (snapshot.getCargoPersistenceRevision() != 0L) {
                throw new IllegalArgumentException(
                        "non-horse mob has horse inventory persistence revision");
            }
            if (snapshot.getHorseMenuPersistenceRevision() != 0L
                    || snapshot.getHorseEquipmentPersistenceRevision() != 0L) {
                throw new IllegalArgumentException(
                        "non-horse mob has horse menu revision tuple");
            }
            if (snapshot.isCamelSaddled() || snapshot.isCamelSitting()) {
                throw new IllegalArgumentException("non-Camel mob has camel state");
            }
            return;
        }
        Mob detached = detachedHorseMenuMob(snapshot);
        detached.restoreHorseState(snapshot.isHorseTamed(), snapshot.getHorseTemper(),
                snapshot.isHorseSaddled(), snapshot.getHorseMaxHealth(),
                snapshot.getHorseSpeed(), snapshot.getHorseJumpStrength(),
                snapshot.isHorseChested(), snapshot.getLlamaCarpetColor(),
                snapshot.getLlamaStrength());
        detached.restoreHorseArmor(snapshot.getHorseArmorItem());
        detached.restoreCamelState(snapshot.isCamelSaddled(), snapshot.isCamelSitting());
        HorseMenuCodec.restoreForLoad(detached, snapshot.getHorseInventoryData(),
                new HorseMenuCodec.RevisionSnapshot(
                        snapshot.getHorseMenuPersistenceRevision(),
                        snapshot.getHorseEquipmentPersistenceRevision(),
                        snapshot.getCargoPersistenceRevision()));
        validateHorseMenuScalars(snapshot, detached);
    }

    /**
     * Validates only HMI1 framing, shape, and the scalar-derived cargo shape. This is the partial
     * setter gate used while a persistence row is still being assembled; the full gate above runs
     * after the late Camel scalar is available.
     */
    public static void validateHorseInventoryPayloadShape(MobPersistenceSnapshot snapshot) {
        validateSnapshotEnvelope(snapshot);
        validateHorseRevisionTuple(snapshot);
        MobType type = MobType.valueOf(snapshot.getType());
        if (!horseMenuType(type)) {
            if (snapshot.getHorseInventoryData() != null) {
                throw new IllegalArgumentException("non-horse mob has horse inventory payload");
            }
            if (snapshot.getHorseMenuPersistenceRevision() != 0L
                    || snapshot.getHorseEquipmentPersistenceRevision() != 0L
                    || snapshot.getCargoPersistenceRevision() != 0L) {
                throw new IllegalArgumentException(
                        "non-horse mob has horse menu revision tuple");
            }
            return;
        }
        Mob detached = detachedHorseMenuMob(snapshot);
        detached.restoreHorseState(snapshot.isHorseTamed(), snapshot.getHorseTemper(),
                snapshot.isHorseSaddled(), snapshot.getHorseMaxHealth(),
                snapshot.getHorseSpeed(), snapshot.getHorseJumpStrength(),
                snapshot.isHorseChested(), snapshot.getLlamaCarpetColor(),
                snapshot.getLlamaStrength());
        detached.restoreHorseArmor(snapshot.getHorseArmorItem());
        HorseMenuCodec.validatePayload(detached, snapshot.getHorseInventoryData());
    }

    private static void validateSnapshotEnvelope(MobPersistenceSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("mob snapshot required");
        requireValidMobId(snapshot.getMobId());
        if (snapshot.getSchemaVersion() != MobPersistenceSnapshot.SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported mob persistence schema");
        }
    }

    private static void validateHorseRevisionTuple(MobPersistenceSnapshot snapshot) {
        validateHorsePersistenceRevision(snapshot.getHorseMenuPersistenceRevision(), "horse menu");
        validateHorsePersistenceRevision(snapshot.getHorseEquipmentPersistenceRevision(),
                "horse equipment");
        validateHorsePersistenceRevision(snapshot.getCargoPersistenceRevision(), "horse cargo");
    }

    private static void validateHorsePersistenceRevision(long revision, String name) {
        if (revision < 0L || revision >= Long.MAX_VALUE - 1L) {
            throw new IllegalArgumentException(name
                    + " persistence revision must be non-negative and publishable");
        }
    }

    private static Mob detachedHorseMenuMob(MobPersistenceSnapshot snapshot) {
        MobType type = MobType.valueOf(snapshot.getType());
        return MobFactory.restore(type, snapshot.getMobId(), snapshot.getX(), snapshot.getY(),
                snapshot.getZ(), snapshot.getVariant(), 0, snapshot.getSlimeSize());
    }

    private static void validateHorseMenuScalars(MobPersistenceSnapshot snapshot, Mob detached) {
        HorseMenuContainerAccess menu = new HorseMenuContainerAccess(detached);
        short expectedSaddle = switch (detached.type) {
            case HORSE, DONKEY, MULE, ZOMBIE_HORSE ->
                    snapshot.isHorseSaddled() ? PlayerInventory.SADDLE : PlayerInventory.EMPTY;
            case CAMEL -> snapshot.isCamelSaddled() ? PlayerInventory.SADDLE : PlayerInventory.EMPTY;
            default -> PlayerInventory.EMPTY;
        };
        requireEquipmentScalar(menu, HorseMenuContainerAccess.SADDLE_SLOT, expectedSaddle,
                "saddle");

        short expectedBody = switch (detached.type) {
            case HORSE -> snapshot.getHorseArmorItem();
            case LLAMA, TRADER_LLAMA -> snapshot.getLlamaCarpetColor()
                    == LlamaRules.NO_CARPET ? PlayerInventory.EMPTY
                            : LlamaRules.carpetItem(snapshot.getLlamaCarpetColor());
            default -> PlayerInventory.EMPTY;
        };
        requireEquipmentScalar(menu, HorseMenuContainerAccess.BODY_SLOT, expectedBody, "body");

        if (detached.horseChested() != snapshot.isHorseChested()) {
            throw new IllegalArgumentException("horse chested scalar does not match HMI1 shape");
        }
        if (snapshot.getLlamaStrength() != 0
                && detached.llamaStrength() != snapshot.getLlamaStrength()) {
            throw new IllegalArgumentException("llama strength scalar does not match HMI1 shape");
        }
        if (detached.llamaCarpetColor() != snapshot.getLlamaCarpetColor()) {
            throw new IllegalArgumentException("llama carpet scalar does not match HMI1 body");
        }
    }

    private static void requireEquipmentScalar(HorseMenuContainerAccess menu, int slot,
            short expected, String name) {
        PlayerInventory.StackSnapshot actual = menu.stack(slot);
        int expectedCount = expected == PlayerInventory.EMPTY ? 0 : 1;
        if (actual.itemType() != expected || actual.count() != expectedCount) {
            throw new IllegalArgumentException(
                    "horse " + name + " scalar does not match HMI1 payload");
        }
    }

    private static boolean horseMenuType(MobType type) {
        return switch (type) {
            case HORSE, DONKEY, MULE, LLAMA, ZOMBIE_HORSE, CAMEL, SKELETON_HORSE,
                    TRADER_LLAMA -> true;
            default -> false;
        };
    }

    private static boolean horseStateType(MobType type) {
        return switch (type) {
            case HORSE, DONKEY, MULE, LLAMA, ZOMBIE_HORSE -> true;
            default -> false;
        };
    }

    /** Resolves restored passenger edges only after every persisted mob row has materialized. */
    public void reconcileRestoredMobVehicles() {
        reconcileMobVehicles();
        syncMobPassengersToVehicles();
        for (Mob mob : mobs) snapshotMob(mob);
    }

    /**
     * Owner-side persistence boundary. Only candidates touched by active ticks are materialized;
     * the returned list is detached, immutable, and deterministically ordered for the FIFO writer.
     */
    public List<MobPersistenceSnapshot> persistenceSnapshot() {
        materializePersistenceCandidates();
        List<MobPersistenceSnapshot> rows = new ArrayList<>(persistentMobsById.values());
        rows.sort(Comparator.comparingLong(MobPersistenceSnapshot::getMobId));
        return List.copyOf(rows);
    }

    /**
     * Marks final post-fluid/post-event aggregates without constructing persistence DTOs on the
     * tick hot path. Invalid identities are ignored; removal owns deletion of their durable row.
     */
    public void deferPersistenceSnapshots(List<Mob> changedMobs) {
        for (Mob mob : changedMobs) deferPersistenceSnapshot(mob);
    }

    /** 아이템 획득처럼 MobRuntime.tick 이후 발생한 단일 변경을 같은 서버 틱에 게시합니다. */
    public void refreshPersistenceSnapshot(Mob mob) {
        if (mob == null) return;
        Mob live = mobsById.get(mob.id);
        if (live != mob) {
            if (live == null) {
                pendingPersistenceMobs.remove(mob.id);
                persistentMobsById.remove(mob.id);
            }
            return;
        }
        if (ephemeralQaMobIds != null && ephemeralQaMobIds.contains(mob.id)) {
            pendingPersistenceMobs.remove(mob.id);
            persistentMobsById.remove(mob.id);
            return;
        }
        refreshPendingAnimalDependencyRequests(mob);
        pendingPersistenceMobs.remove(mob.id);
        if (mob.isDead() || mob.removed) {
            persistentMobsById.remove(mob.id);
            return;
        }
        snapshotMob(mob);
    }

    /** A fresh current row which is never installed into the published persistence map. */
    public MobPersistenceSnapshot detachedPersistenceSnapshot(Mob mob) {
        if (mob == null || mobsById.get(mob.id) != mob || mob.isDead() || mob.removed) return null;
        return snapshotOf(mob, leashHolderByMobId.get(mob.id));
    }

    public MobPersistenceSnapshot persistenceSnapshot(long mobId) {
        Mob live = mobsById.get(mobId);
        if (ephemeralQaMobIds != null && ephemeralQaMobIds.contains(mobId)) return null;
        if (live != null) refreshPersistenceSnapshot(live);
        else pendingPersistenceMobs.remove(mobId);
        return persistentMobsById.get(mobId);
    }

    int pendingPersistenceCandidateCount() {
        return pendingPersistenceMobs.size();
    }

    long persistenceSnapshotConstructionCount() {
        return persistenceSnapshotConstructionCount;
    }

    /** 플레이어 활 발사를 기존 ProjectileSim 목록에 넣는 유일한 실행 경로. */
    public ProjectileSim shootPlayerArrow(String nickname, double x, double y, double z,
                                           double vx, double vy, double vz, int damage) {
        return shootPlayerArrow(nickname, x, y, z, vx, vy, vz, damage,
                com.gameexpert.engine.enchant.WideEnchantments.EMPTY);
    }

    /** [ENCHANT-WIDE] 활·석궁의 인챈트(밀어내기·화염·관통)를 화살에 싣는 발사 경로. */
    public ProjectileSim shootPlayerArrow(String nickname, double x, double y, double z,
                                           double vx, double vy, double vz, int damage,
                                           com.gameexpert.engine.enchant.WideEnchantments weapon) {
        ProjectileSim arrow = ProjectileSim.fromPlayer(nickname, x, y, z, vx, vy, vz, damage);
        arrow.weaponEnchantments = weapon;
        arrow.id = nextArrowId++;
        arrows.add(arrow);
        indexArrow(arrow);
        return arrow;
    }

    /**
     * [TRIDENT] 플레이어가 던진 삼지창을 화살과 같은 청크 인덱스·틱·브로드캐스트 경로에 넣습니다.
     * 드라운드 투척과 같은 {@link ProjectileSim.Kind#TRIDENT} 물리를 그대로 쓰고, 회수될 내구만
     * 함께 싣습니다.
     */
    public ProjectileSim throwPlayerTrident(String nickname, double x, double y, double z,
                                            double vx, double vy, double vz,
                                            int damage, int recoverableDurability) {
        return throwPlayerTrident(nickname, x, y, z, vx, vy, vz, damage, recoverableDurability,
                com.gameexpert.engine.enchant.WideEnchantments.EMPTY, null);
    }

    /**
     * [ENCHANT-WIDE] 던진 삼지창의 인챈트 집합과 성분 문자열까지 싣는다. 명중 시 찌르기를 읽고, 회수물은
     * 워드 0 과 성분(확장 인챈트·이름 등)을 그대로 되돌려준다.
     */
    public ProjectileSim throwPlayerTrident(String nickname, double x, double y, double z,
                                            double vx, double vy, double vz,
                                            int damage, int recoverableDurability,
                                            com.gameexpert.engine.enchant.WideEnchantments enchantments,
                                            String componentData) {
        ProjectileSim trident = ProjectileSim.fromPlayerTrident(
                nickname, x, y, z, vx, vy, vz, damage, recoverableDurability);
        trident.weaponEnchantments = enchantments;
        trident.recoverEnchantments = enchantments.word0();
        trident.recoverComponentData = componentData;
        trident.id = nextArrowId++;
        arrows.add(trident);
        indexArrow(trident);
        return trident;
    }

    /** 플레이어 낚시 캐스팅을 화살과 같은 청크 인덱스·틱·브로드캐스트 경로에 넣습니다. */
    public ProjectileSim castFishingBobber(String nickname, double x, double y, double z,
                                            double vx, double vy, double vz) {
        ProjectileSim bobber = ProjectileSim.fromFishingCast(nickname, x, y, z, vx, vy, vz);
        bobber.id = nextArrowId++;
        arrows.add(bobber);
        indexArrow(bobber);
        return bobber;
    }

    /** id 로 살아 있는 투사체를 찾는다. 낚시 시스템이 자기 찌를 되짚을 때만 쓴다. */
    public ProjectileSim projectile(long id) {
        return arrowsById.get(id);
    }

    /**
     * [ARROW-GROUND] 코어 패스가 종결시킨 화살을 같은 id 로 되살린다(거절된 명중의 되튐). 이미 원장에
     * 있으면 아무것도 하지 않는다.
     */
    public void reviveProjectile(ProjectileSim projectile) {
        if (projectile == null || !projectile.alive || projectile.id <= 0
                || arrowsById.containsKey(projectile.id)) return;
        arrows.add(projectile);
        indexArrow(projectile);
    }

    /** 수명이 다하기 전에 투사체를 거둬들인다(찌 회수). 제거되면 true. */
    public boolean removeProjectile(long id) {
        ProjectileSim projectile = projectile(id);
        if (projectile == null) return false;
        removeArrow(projectile);
        return true;
    }

    /**
     * 이 런타임 밖에서 투사체 프로토콜을 쓰는 비행체(던진 엔더의 눈)에 투사체 id 하나를 내준다.
     * 같은 id 공간을 써야 클라이언트 투사체 표가 화살·눈덩이와 겹치지 않는다.
     */
    public long reserveProjectileId() {
        return nextArrowId++;
    }

    /** 플레이어 눈덩이를 화살과 같은 청크 인덱스·틱·브로드캐스트 경로에 넣습니다. */
    public ProjectileSim throwPlayerEnderPearl(String nickname, double x, double y, double z,
                                              double vx, double vy, double vz) {
        ProjectileSim snowball = ProjectileSim.fromEnderPearl(nickname, x, y, z, vx, vy, vz);
        snowball.id = nextArrowId++;
        arrows.add(snowball);
        indexArrow(snowball);
        return snowball;
    }

    public ProjectileSim throwPlayerSnowball(String nickname, double x, double y, double z,
                                              double vx, double vy, double vz) {
        ProjectileSim snowball = ProjectileSim.fromSnowball(nickname, x, y, z, vx, vy, vz);
        snowball.id = nextArrowId++;
        arrows.add(snowball);
        indexArrow(snowball);
        return snowball;
    }

    public ProjectileSim throwPlayerWindCharge(String nickname, double x, double y, double z,
                                                double vx, double vy, double vz) {
        ProjectileSim charge = ProjectileSim.fromPlayerWindCharge(
                nickname, x, y, z, vx, vy, vz);
        charge.id = nextArrowId++;
        arrows.add(charge);
        indexArrow(charge);
        return charge;
    }

    /** 플레이어 달걀을 눈덩이와 같은 청크 인덱스·틱·브로드캐스트 경로에 넣습니다. */
    /**
     * [ZOMBIE-ANIMAL] 상한 달걀까지 받는 형태. {@code spoiled} 는 부화 굴림 하나만 가른다.
     */
    public ProjectileSim throwPlayerEgg(String nickname, double x, double y, double z,
                                        double vx, double vy, double vz,
                                        String eggVariant, boolean spoiled) {
        ProjectileSim egg = spoiled
                ? ProjectileSim.fromSpoiledEgg(nickname, x, y, z, vx, vy, vz)
                : ProjectileSim.fromEgg(nickname, x, y, z, vx, vy, vz, eggVariant);
        egg.id = nextArrowId++;
        arrows.add(egg);
        indexArrow(egg);
        return egg;
    }

    /**
     * [POTION] 플레이어 투척 물약을 마녀 물약과 같은 청크 인덱스·틱·브로드캐스트 경로에 넣습니다.
     * 물리·착탄 스플래시는 {@link ProjectileSim.Kind#SPLASH_POTION} 한 구현을 그대로 씁니다.
     */
    public ProjectileSim throwPlayerPotion(String nickname, double x, double y, double z,
                                           double vx, double vy, double vz,
                                           ProjectileEffect effect) {
        return throwPlayerPotion(nickname, x, y, z, vx, vy, vz, effect, (short) 0);
    }

    /** [POTION-COLOR] 던진 물약 아이템을 실어(projectileSpawn.itemType) 병 색을 알린다. */
    public ProjectileSim throwPlayerPotion(String nickname, double x, double y, double z,
                                           double vx, double vy, double vz,
                                           ProjectileEffect effect, short itemType) {
        ProjectileSim potion = ProjectileSim.fromPlayerPotion(nickname, x, y, z, vx, vy, vz,
                effect, itemType);
        potion.id = nextArrowId++;
        arrows.add(potion);
        indexArrow(potion);
        return potion;
    }

    /**
     * 바닐라 {@code ThrownEgg.onHit}: 명중 지점에서 1/8 로 부화하고 그 안에서 1/32 면 4마리다.
     * 나온 병아리는 번식 새끼와 같은 {@link #BABY_GROWTH_TICKS} 성장 타이머를 들고 같은
     * {@link #addMob} 경로를 타므로 스냅샷·영속·브로드캐스트가 모두 그 경로를 그대로 쓴다.
     * 굴림은 부화 여부가 결정된 뒤에만 소비되므로 두 권위의 난수열이 같은 자리에서 갈린다.
     */
    public List<Mob> hatchEggChicks(double x, double y, double z, String variant, MobRandom rng) {
        int chicks = FarmAnimalRules.eggHatchChickCount(rng);
        if (chicks == 0) return List.of();
        List<Mob> hatched = new ArrayList<>(chicks);
        for (int index = 0; index < chicks; index++) {
            Mob chick = addMob(MobType.CHICKEN, x, y, z, false, variant);
            setBabyAge(chick, BABY_GROWTH_TICKS);
            hatched.add(chick);
        }
        return hatched;
    }

    /** 플레이어 폭죽 로켓을 같은 청크 인덱스·틱·브로드캐스트 경로에 넣습니다. */
    public ProjectileSim launchPlayerFirework(String nickname, double x, double y, double z,
                                              double vx, double vy, double vz, int lifetimeTicks) {
        ProjectileSim rocket = ProjectileSim.fromFireworkRocket(
                nickname, x, y, z, vx, vy, vz, lifetimeTicks);
        rocket.id = nextArrowId++;
        arrows.add(rocket);
        indexArrow(rocket);
        return rocket;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    public static class MobEmit {
        private final long mobId;
        private final MobEvent event;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    public static class ProjectileHit {
        private final long projectileId;
        private final ProjectileSim.Kind kind;
        private final MobEvent event;
        /** [DRAGON] 명중 순간 투사체 위치(드래곤 부위 판정). */
        private final double x;
        private final double y;
        private final double z;
        /** [MOB-EQUIP] 쏜 무기의 인챈트(몹 활의 밀어내기·화염이 플레이어 명중에서 읽힌다). */
        private final com.gameexpert.engine.enchant.WideEnchantments weaponEnchantments;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    public static class TickResult {
        private final List<Mob> spawned;
        private final List<MobDespawn> despawned;
        private final List<MobEmit> mobEvents;
        private final List<ProjectileHit> projectileHits;
        private final List<ProjectileSim> terminatedProjectiles;
    }

    /**
     * Ticks only entities whose chunk belongs to the current connected-player union. Hibernated entities stay in
     * the authoritative list/index and resume from exactly the same state when their chunk becomes active again.
     */
    public TickResult tick(MobWorldView world, MobRandom rng, Set<Long> activeChunkKeys) {
        return tick(world, rng, activeChunkKeys, true);
    }

    /**
     * Spring authority settles fluid displacement and combat events after the core mob pass. Its caller publishes
     * the final persistence rows once after those settlements, instead of materializing an intermediate aggregate.
     */
    public TickResult tickWithDeferredPersistenceSnapshots(
            MobWorldView world, MobRandom rng, Set<Long> activeChunkKeys) {
        return tick(world, rng, activeChunkKeys, false);
    }

    private TickResult tick(MobWorldView world, MobRandom rng, Set<Long> activeChunkKeys,
            boolean publishPersistenceSnapshots) {
        Objects.requireNonNull(activeChunkKeys, "activeChunkKeys");
        worldTick++;
        // 청크가 hibernate되면(활성 union 이탈) 그 안의 비영속 몹은 더 이상 틱·소멸 평가를 받지 못한다.
        // 바닐라의 "청크 언로드 시 비영속 몹 제거"와 동일하게, 이번 틱 hibernate된 청크의 소멸 대상 몹을
        // 즉시 정리해 탐험 중 서버 몹 목록이 무한히 누적되는 것을 막는다(재활성화 시 O(n²) 폭발 방지).
        cullHibernatedMobs(activeChunkKeys);
        List<ProjectileSim> terminatedProjectiles = cullHibernatedProjectiles(activeChunkKeys);
        List<Mob> tickMobs = mobsInChunks(activeChunkKeys);
        List<ProjectileSim> tickArrows = arrowsInChunks(activeChunkKeys);
        boolean projectileStateMutated = false;
        // 이벤트/스폰이 없는 틱(대부분)에는 리스트를 만들지 않고 빈 리스트를 공유해 상시 할당을 줄인다.
        List<MobEmit> mobEvents = null;
        List<ProjectileHit> projectileHits = null;
        List<Mob> spawned = null;

        // A removed/corrupt vehicle never leaves a stale passenger edge alive for another AI tick.
        validateMobPassengerEdges();

        // 1) 몹 AI
        prepareBreedingMovement(tickMobs);
        prepareAnimalGoalTargets(world, tickMobs);
        prepareSchoolingMovement(tickMobs);
        prepareSocialTargets(tickMobs);
        List<Mob> orphanedCompanions = prepareIllagerCompanions(tickMobs);
        if (orphanedCompanions != null) {
            mobEvents = new ArrayList<>(orphanedCompanions.size());
            for (Mob companion : orphanedCompanions) {
                companion.removed = true;
                mobEvents.add(new MobEmit(companion.id, new MobEvent.Despawned("orphan")));
            }
        }
        for (Mob m : tickMobs) {
            if (m.isDead() || m.removed) continue;
            if (m instanceof SkeletonHorse skeletonHorse && skeletonHorse.trapActive()) {
                if (skeletonHorse.advanceTrapAge()) {
                    skeletonHorse.removed = true;
                    if (mobEvents == null) mobEvents = new ArrayList<>();
                    mobEvents.add(new MobEmit(m.id, new MobEvent.Despawned("trap_expired")));
                    continue;
                }
                if (playerWithinSkeletonTrapRange(skeletonHorse, world.players())
                        && skeletonHorse.triggerTrap()) {
                    Mob rider = addMob(MobType.SKELETON, skeletonHorse.x,
                            skeletonHorse.y + skeletonHorse.height() * 0.75,
                            skeletonHorse.z, true);
                    rider.mountMobVehicle(skeletonHorse.id, skeletonHorse.x,
                            skeletonHorse.y + skeletonHorse.height() * 0.75,
                            skeletonHorse.z, skeletonHorse.yaw);
                    snapshotMob(skeletonHorse);
                    snapshotMob(rider);
                }
            }
            if (m instanceof CopperGolem copperGolem) {
                if (copperGolem.tickWeathering(world.gameTimeMcTicks(), rng)) {
                    snapshotMob(copperGolem);
                }
                int blockX = (int) Math.floor(copperGolem.x);
                int blockY = (int) Math.floor(copperGolem.y);
                int blockZ = (int) Math.floor(copperGolem.z);
                boolean clear = world.getBlock(blockX, blockY, blockZ) == 0;
                if ((copperGolem.statuePending()
                        || clear && copperGolem.planStatue(rng)) && clear) {
                    if (mobEvents == null) mobEvents = new ArrayList<>();
                    mobEvents.add(new MobEmit(m.id, new MobEvent.CopperGolemStatue(
                            blockX, blockY, blockZ, copperGolem.statueState())));
                }
            }
            m.tickVisualAction();
            if (m instanceof Pufferfish pufferfish) {
                PufferfishRules.Transition transition = pufferfish.advancePuffState(
                        pufferfishThreatened(pufferfish, tickMobs, world.players()));
                if (transition != PufferfishRules.Transition.NONE) {
                    if (mobEvents == null) mobEvents = new ArrayList<>();
                    String action = transition == PufferfishRules.Transition.INFLATE
                            ? "inflate" : "deflate";
                    pufferfish.synchronizeVisualAction("puff", action, 1);
                    mobEvents.add(new MobEmit(m.id, new MobEvent.Sound(action)));
                }
                for (PlayerSnapshot player : world.players()) {
                    var contact = pufferfish.planContact(world.difficulty(), player.alive(),
                            touchesPlayer(pufferfish, player));
                    if (contact.isEmpty()) continue;
                    PufferfishRules.ContactPlan plan = contact.get();
                    if (mobEvents == null) mobEvents = new ArrayList<>();
                    mobEvents.add(new MobEmit(m.id, new MobEvent.AttackPlayer(
                            player.nickname(), plan.damage(), m.x, m.z, null, m.id, m.type,
                            new ProjectileEffect(com.gameexpert.engine.effect.StatusEffect.POISON,
                                    plan.poisonAmplifier(), plan.poisonTicks()))));
                }
                for (Mob target : tickMobs) {
                    if (target == m || target.isDead() || target.removed
                            || PufferfishRules.notScaryForPufferfish(target.type)) continue;
                    var contact = pufferfish.planContact(world.difficulty(), true,
                            touchesMobInflated(pufferfish, target,
                                    PufferfishRules.MOB_CONTACT_BOX_INFLATION));
                    if (contact.isEmpty()) continue;
                    PufferfishRules.ContactPlan plan = contact.get();
                    boolean damaged = target.damage(plan.damage(), worldTick);
                    if (!damaged) continue;
                    target.applyStatusEffect(com.gameexpert.engine.effect.StatusEffect.POISON,
                            plan.poisonAmplifier(), plan.poisonTicks());
                    if (mobEvents == null) mobEvents = new ArrayList<>();
                    mobEvents.add(new MobEmit(target.id,
                            new MobEvent.EnvironmentDamage(target.isDead())));
                }
            }
            boolean wasBaby = m.isBaby();
            boolean updateLoveModeBucket = m.tickBreedingState();
            // [TURTLE] [A] Turtle.ageBoundaryReached — 새끼가 **성체가 되는 그 순간** 등딱지
            // 조각 한 개를 떨군다. 성체 거북은 죽어도 아무것도 떨구지 않으므로 이것이 조각의
            // 유일한 경로다. 성장 자체는 위의 공통 타이머 감소이고 이 갈래는 그 경계를 한 번만
            // 잡는다(wasBaby 가 그 한 번을 보장한다). 정적판 StandaloneMobRuntime 의 같은
            // 성장 경계 갈래와 짝이며 두 권위가 TurtleEggRules 의 같은 상수를 읽는다.
            if (wasBaby && !m.isBaby() && m.type == MobType.TURTLE) {
                if (mobEvents == null) mobEvents = new ArrayList<>();
                mobEvents.add(new MobEmit(m.id, new MobEvent.DropItem(
                        PlayerInventory.TURTLE_SCUTE, TurtleEggRules.GROWTH_SCUTE_COUNT,
                        m.x, m.y, m.z)));
            }
            Mob.WaterLifecycleResult water = m.tickWaterLifecycle(world);
            if (water == Mob.WaterLifecycleResult.DROWN_DAMAGE) {
                if (mobEvents == null) mobEvents = new ArrayList<>();
                mobEvents.add(new MobEmit(m.id, new MobEvent.EnvironmentDamage(m.isDead())));
                if (m.isDead()) {
                    if (spawned == null) spawned = new ArrayList<>();
                    spawned.addAll(splitSlimeOnDeath(m, rng));
                    continue;
                }
            } else if (water.conversionType() != null) {
                Mob converted = convertMob(m, water.conversionType());
                if (spawned == null) spawned = new ArrayList<>();
                spawned.add(converted);
                m.removed = true;
                if (mobEvents == null) mobEvents = new ArrayList<>();
                mobEvents.add(new MobEmit(m.id, new MobEvent.Converted(converted.id)));
                continue;
            }
            if (m instanceof Tadpole tadpole && tadpole.advanceLifecycle()) {
                String frogVariant = frogVariantForBiome(world.biomeAt(
                        (int) Math.floor(m.x), (int) Math.floor(m.y), (int) Math.floor(m.z)));
                Mob converted = convertMob(m, MobType.FROG, frogVariant);
                converted.setPersistenceRequired(true);
                snapshotMob(converted);
                if (spawned == null) spawned = new ArrayList<>();
                spawned.add(converted);
                m.removed = true;
                if (mobEvents == null) mobEvents = new ArrayList<>();
                // Converted already carries the species swap; no fabricated sound kind is emitted
                // because the shared client has no registered `grow_up` voice family.
                mobEvents.add(new MobEmit(m.id, new MobEvent.Converted(converted.id)));
                continue;
            }
            long effectMaskBefore = m.statusEffects().activeMask();
            if (m.tickStatusEffects()) {
                if (mobEvents == null) mobEvents = new ArrayList<>();
                mobEvents.add(new MobEmit(m.id, new MobEvent.EnvironmentDamage(m.isDead())));
            }
            // [GLOWING] 효과가 끝나 목록이 바뀐 몹은 저장 행도 다시 쓴다(남은 시간만 줄면 쓰지 않는다).
            if (m.statusEffects().activeMask() != effectMaskBefore) deferPersistenceSnapshot(m);
            // 좀비 주민 치료 카운트다운(MOB.md §2). 약함 상태에서 황금 사과를 받은 개체만
            // 진행하며, 0 이 되는 즉시 Villager 로 전환한다.
            if (!m.isDead() && m instanceof ZombieVillager curing
                    && curing.advanceConversion(world, rng)) {
                String curedBy = curing.conversionStarter();
                Mob converted = convertMob(m, MobType.VILLAGER);
                converted.setPersistenceRequired(true);
                snapshotMob(converted);
                if (spawned == null) spawned = new ArrayList<>();
                spawned.add(converted);
                m.removed = true;
                if (mobEvents == null) mobEvents = new ArrayList<>();
                mobEvents.add(new MobEmit(m.id, new MobEvent.Converted(converted.id)));
                // 바닐라는 치료자에게 ZOMBIE_VILLAGER_CURED 평판을 주고 새 주민이 좀비 주민이
                // 갖고 있던 직업을 그대로 잇는다. 훅은 주민 트랙 배선용이며, 여기서 두 사실을
                // 직접 확정해 훅이 없어도 계약이 성립하게 둔다.
                // 치료자는 바닐라대로 ZOMBIE_VILLAGER_CURED 평판을 얻는다(할인은 gossip 이 만든다).
                // 직업 승계는 하지 않는다 — 이 게임의 좀비 주민은 직업을 들고 있지 않으므로
                // 승계할 값이 없다(MC-REFERENCE 의 divergence 항목).
                if (converted instanceof Villager villager && curedBy != null) {
                    villager.social().gossips().applyReputationEvent(
                            curedBy, VillagerGossipRules.ReputationEvent.ZOMBIE_VILLAGER_CURED);
                }
                curedVillagerHook.onCuredVillager(converted.id, m.id, curedBy, null,
                        ZombieVillagerCureRules.CURED_REPUTATION_DISCOUNT);
                continue;
            }
            double healthBeforeFireTick = m.exactHealth();
            m.tickFireEnvironment(world, rng);
            if (m.exactHealth() < healthBeforeFireTick) {
                if (mobEvents == null) mobEvents = new ArrayList<>();
                mobEvents.add(new MobEmit(m.id, new MobEvent.EnvironmentDamage(m.isDead())));
                if (m.isDead()) {
                    if (spawned == null) spawned = new ArrayList<>();
                    spawned.addAll(splitSlimeOnDeath(m, rng));
                    continue;
                }
            }
            m.prepareRandomWanderActivity(world);
            m.prepareFloatGoal(world, rng);
            m.prepareRafflesiaOdor(world);
            if (m instanceof Frog frog) prepareFrogConversionTarget(frog, world);
            if (m instanceof PoisonDartFrog frog && frog.shouldSearchFireflyHost()) {
                preparePoisonDartFrogHost(frog, world);
            }
            PoisonDartFrog.FireflyFeedRequest poisonFrogFeedBeforeTick =
                    m instanceof PoisonDartFrog frog ? frog.pendingFireflyFeed() : null;
            HiveCell beeHiveCellBeforeTick = m instanceof Bee bee && bee.inHive()
                    ? beeHiveCellByMobId.get(bee.id) : null;
            double healthBeforeAiTick = m.exactHealth();
            if (m instanceof CompanionMob companion) {
                companion.prepareLeashed(leashHolderByMobId.containsKey(m.id));
            }
            // [EC-MOBS] 셜커는 이웃 셜커(충돌·복제·경보)와 몹 문맥을, 아이템 액자는 같은 방향 액자(canCoexist)를 본다.
            if (m instanceof Shulker shulker) shulker.prepareNeighborhood(tickShulkers(tickMobs), tickMobs);
            if (m instanceof ItemFrame frame) frame.prepareFrames(tickFrames(tickMobs));
            List<MobEvent> tickEvents = m.tick(world, rng);
            if (m instanceof ItemFrame frame && frame.supportLost() && !frame.isDead()) {
                // BlockAttachedEntity.tick: survives() 가 거짓이면 dropItem(null) → kill. 부서지는 소리
                // (ITEM_FRAME_BREAK)는 클라가 액자의 사망 퇴장에서 낸다(두 권위 공통 계약).
                frame.damage(1000.0, worldTick);
            }
            if (m instanceof Bee bee) {
                if (bee.inHive()) {
                    if (beeHiveCellBeforeTick == null) indexBeeHiveResident(bee);
                } else if (beeHiveCellBeforeTick != null) {
                    unindexBeeHiveResident(bee);
                }
            }
            if (m instanceof PoisonDartFrog frog) {
                PoisonDartFrog.FireflyFeedRequest feed = poisonFrogFeedBeforeTick;
                if (feed != null) {
                    int block = world.getBlock(feed.x(), feed.y(), feed.z());
                    boolean committed = block >= 0 && FireflyBushEcology.isConsumableHost(
                            block & 0xffff,
                            world.localBrightness(feed.x(), feed.y(), feed.z()));
                    if (frog.confirmFireflyFeed(feed.sequence(), committed) && committed) {
                        if (mobEvents == null) mobEvents = new ArrayList<>();
                        mobEvents.add(new MobEmit(m.id, new MobEvent.Sound("eat")));
                    }
                }
                boolean playerContactPlanned = false;
                for (PlayerSnapshot player : world.players()) {
                    PoisonDartFrogRules.PoisonPlan poison = frog.planDefensiveContact(
                            world.difficulty(), player.alive()
                                    && touchesPoisonDartFrog(frog, player.x(), player.y(),
                                            player.z(), 0.6, 1.8));
                    if (poison == null) continue;
                    if (mobEvents == null) mobEvents = new ArrayList<>();
                    mobEvents.add(new MobEmit(m.id, new MobEvent.AttackPlayer(
                            player.nickname(), (int) poison.damage(), frog.x, frog.z,
                            null, frog.id, frog.type,
                            new ProjectileEffect(
                                    com.gameexpert.engine.effect.StatusEffect.POISON,
                                    poison.amplifier(), poison.durationTicks()))));
                    playerContactPlanned = true;
                    break;
                }
                if (!playerContactPlanned && frog.contactCooldownTicks() == 0) {
                    for (Mob target : tickMobs) {
                        if (target == frog || target.isDead() || target.removed) continue;
                        PoisonDartFrogRules.PoisonPlan poison = frog.planDefensiveContact(
                                world.difficulty(), touchesPoisonDartFrog(
                                        frog, target.x, target.y, target.z,
                                        target.width(), target.height()));
                        if (poison == null || !target.damage(poison.damage(), worldTick)) continue;
                        target.applyStatusEffect(com.gameexpert.engine.effect.StatusEffect.POISON,
                                poison.amplifier(), poison.durationTicks());
                        frog.confirmDefensiveContact();
                        if (mobEvents == null) mobEvents = new ArrayList<>();
                        mobEvents.add(new MobEmit(target.id,
                                new MobEvent.EnvironmentDamage(target.isDead())));
                        break;
                    }
                }
            }
            synchronizeSpeciesVisualState(m);
            if (m.type == MobType.GOAT && m.goatRamPhase() == GoatRamRules.Phase.CHARGING) {
                boolean entityImpact = false;
                for (PlayerSnapshot player : world.players()) {
                    if (!player.alive() || !touchesRamTarget(m, player.x(), player.y(), player.z(),
                            0.6, 1.8)) continue;
                    if (mobEvents == null) mobEvents = new ArrayList<>();
                    mobEvents.add(new MobEmit(m.id, new MobEvent.GoatRamPlayer(
                            player.nickname(), world.difficulty().scaleContactDamage(
                                    GoatRamRules.IMPACT_DAMAGE),
                            m.goatRamDirectionX(), m.goatRamDirectionZ(),
                            GoatRamRules.IMPACT_KNOCKBACK)));
                    entityImpact = m.finishGoatEntityRam(rng);
                    break;
                }
                if (!entityImpact) {
                    for (Mob target : tickMobs) {
                        if (target == m || target.isDead() || target.removed
                                || !touchesRamTarget(m, target.x, target.y, target.z,
                                        target.width(), target.height())) continue;
                        if (!target.damage(GoatRamRules.IMPACT_DAMAGE, worldTick)) continue;
                        double resistance = Math.max(0.0, 1.0 - target.knockbackResistance());
                        target.applyKnockback(
                                m.goatRamDirectionX() * GoatRamRules.IMPACT_KNOCKBACK * resistance,
                                m.goatRamDirectionZ() * GoatRamRules.IMPACT_KNOCKBACK * resistance,
                                target.onGround, 0.0, 0.0);
                        if (mobEvents == null) mobEvents = new ArrayList<>();
                        mobEvents.add(new MobEmit(target.id,
                                new MobEvent.EnvironmentDamage(target.isDead())));
                        m.finishGoatEntityRam(rng);
                        break;
                    }
                }
            }
            m.clearPreparedFloatGoal();
            boolean teleported = false;
            boolean environmentDamageReported = false;
            for (MobEvent e : tickEvents) {
                if (e instanceof MobEvent.SummonVex summon) {
                    summonOwnedVex(m, summon, rng);
                    continue;
                }
                if (e instanceof MobEvent.ShulkerClone clone) {
                    // Shulker.hitByShulkerBullet: 옛 자리에 새 셜커(원본 색 = 무색).
                    Mob copy = addMob(MobType.SHULKER, clone.x(), clone.y(), clone.z(), true);
                    snapshotMob(copy);
                    continue;
                }
                if (e instanceof MobEvent.ShootShulkerBullet shot) {
                    ProjectileSim bullet = ProjectileSim.shulkerBullet(shot, m,
                            shulkerBulletTarget(world, shot.targetNickname(), shot.targetMobId()),
                            ProjectileSim.steerWorld(world),
                            new ShulkerBulletRules.BulletRandom(
                                    (long) rng.nextInt(Integer.MAX_VALUE) << 16 ^ nextArrowId));
                    bullet.id = nextArrowId++;
                    arrows.add(bullet);
                    indexArrow(bullet);
                    continue;
                }
                if (e instanceof MobEvent.Teleported) teleported = true;
                if (e instanceof MobEvent.EnvironmentDamage) environmentDamageReported = true;
                if (e instanceof MobEvent.ShootArrow) {
                    m.markVisualAction("ranged_release", 1);
                }
                if (mobEvents == null) mobEvents = new ArrayList<>();
                mobEvents.add(new MobEmit(m.id, e));
                if (e instanceof MobEvent.ShootArrow s) {
                    ProjectileSim arrow = ProjectileSim.fromShot(s, m);
                    // [MOB-EQUIP] 바닐라 ProjectileUtil.getMobArrow(mob, ammo, velocity, weapon): 화살이 쏜
                    // 활/석궁을 기억해 명중 때 밀어내기·화염·관통을 읽는다(고유 활의 성분 포함).
                    if (s.kind() == ProjectileSim.Kind.ARROW) {
                        arrow.weaponEnchantments = m.heldWeaponEnchantments();
                    }
                    arrow.id = nextArrowId++;
                    arrows.add(arrow);
                    indexArrow(arrow);
                    if (m.type == MobType.PIGLIN) {
                        mobEvents.add(new MobEmit(m.id, new MobEvent.Sound("attack")));
                    }
                }
            }
            String leashHolder = leashHolderByMobId.get(m.id);
            if (leashHolder != null) {
                PlayerSnapshot holder = world.players().stream()
                        .filter(player -> leashHolder.equals(player.nickname()) && player.alive())
                        .findFirst().orElse(null);
                if (holder != null) {
                    double dx = holder.x() - m.x;
                    double dy = holder.y() - m.y;
                    double dz = holder.z() - m.z;
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance > 10.0) {
                        leashHolderByMobId.remove(m.id);
                        snapshotMob(m);
                        if (mobEvents == null) mobEvents = new ArrayList<>();
                        mobEvents.add(new MobEmit(m.id, new MobEvent.LeashBroken(
                                leashHolder, m.x, m.y, m.z)));
                    } else if (distance > 6.0 && distance > 1e-9) {
                        double strength = (distance - 6.0) / distance * 0.4;
                        m.applyKnockback(dx * strength, dz * strength, m.onGround,
                                0.0, 0.0);
                        m.vy += dy * strength * 0.2;
                    } else if (distance > 2.0 && distance > 1e-9) {
                        double speed = Math.min(m.type.baseSpeed(), (distance - 2.0) * 0.1);
                        MobPhysics.tickMove(m, world, dx / distance * speed,
                                dy / distance * speed, dz / distance * speed,
                                "fly".equals(m.movementMedium()) ? MoveMode.FLY : MoveMode.WALK);
                    }
                }
            }
            // 햇빛 화상·엔더맨의 물 피해처럼 Mob.tick 내부에서 직접 적용된 환경 피해도
            // 일반 피격과 같은 mobHurt 경로로 내보낸다. 이벤트를 직접 낸 종은 중복하지 않는다.
            if (!environmentDamageReported && m.exactHealth() < healthBeforeAiTick) {
                if (mobEvents == null) mobEvents = new ArrayList<>();
                mobEvents.add(new MobEmit(m.id, new MobEvent.EnvironmentDamage(m.isDead())));
            }
            if (m.isDead()) {
                if (spawned == null) spawned = new ArrayList<>();
                spawned.addAll(splitSlimeOnDeath(m, rng));
            }
            // 탑승 중에도 공격·햇빛·화상 틱은 살리되 AI/내비게이션 변위는 좌석으로 되돌린다.
            m.lockToVehicle();
            m.advancePlacedVehicleCooldown();
            for (MobEvent e : m.tickSoundEvents(teleported)) {
                if (mobEvents == null) mobEvents = new ArrayList<>();
                mobEvents.add(new MobEmit(m.id, e));
            }
            if (updateLoveModeBucket) loveModeMobs.refresh(m);
            reindexMob(m);
        }

        // Vehicles have now completed their AI pass. Passenger AI/timers remain authoritative, but
        // its movement and knockback are discarded in favor of the final vehicle seat.
        validateMobPassengerEdges();
        for (Mob mob : tickMobs) {
            mob.clearPreparedSocialTarget();
            mob.prepareRaidSpeedMultiplier(1.0);
        }

        List<MobEmit> froglightEvents = consumeSmallSulfurCubes(tickMobs);
        if (!froglightEvents.isEmpty()) {
            if (mobEvents == null) mobEvents = new ArrayList<>(froglightEvents.size());
            mobEvents.addAll(froglightEvents);
        }

        if (frogColonyPersistenceInstalled
                && FrogPoisonConversionRules.conversionNight(world.worldTime())
                && tickMobs.stream().anyMatch(mob -> mob instanceof Frog frog
                        && !frog.isBaby() && frog.reachedConversionTarget())) {
            tickMobs = new ArrayList<>(tickMobs);
        }
        List<Mob> convertedFrogs = settleReachedFrogConversions(world, tickMobs);
        if (!convertedFrogs.isEmpty()) {
            if (mobEvents == null) mobEvents = new ArrayList<>();
            for (Mob converted : convertedFrogs) {
                mobEvents.add(new MobEmit(converted.id, new MobEvent.Converted(converted.id)));
            }
        }

        List<MobEmit> breedingEvents = tickBreeding(world, tickMobs, rng);
        if (!breedingEvents.isEmpty()) {
            if (mobEvents == null) mobEvents = new ArrayList<>(breedingEvents.size());
            mobEvents.addAll(breedingEvents);
        }
        for (Mob mob : tickMobs) {
            if (mob instanceof Turtle turtle && turtle.armEggPlacementRequest()) {
                turtleEggPlacements.add(new TurtleEggPlacementRequest(turtle.id,
                        turtle.homeX(), turtle.homeY(), turtle.homeZ(), turtle.eggCount()));
            }
        }

        List<Mob> villagerBorn = tickVillagerSociety(world, tickMobs, rng);
        if (villagerBorn != null) {
            if (spawned == null) spawned = new ArrayList<>();
            spawned.addAll(villagerBorn);
        }

        // 2) 살아 있는 몹끼리 수평 겹침 해소. 전역 상한 70이므로 최대 2,415쌍이다.
        MobPhysics.separateOverlaps(tickMobs, world);
        // Overlap separation may have moved the vehicle after its own AI tick. Attach to that final
        // authoritative position, never to the pre-separation position.
        syncMobPassengersToVehicles();
        for (Mob mob : tickMobs) {
            reindexMob(mob);
            refreshPendingAnimalDependencyRequests(mob);
        }

        // 3) 투사체 진행
        for (ProjectileSim a : tickArrows) {
            // Landed or hooked bobbers are owned by the fishing lane and ProjectileSim.tick is an exact no-op.
            // Every other live projectile advances age, physics, expiry or collision state in this call.
            boolean tickMutatesState = a.alive && !a.landed && !a.hooked();
            boolean wasInGround = a.inGround;
            MobEvent hit = a.tick(world, tickMobs);
            // [ARROW-GROUND] 박힌 채 흔들림·수명만 센 틱은 원장을 다시 쓰지 않는다. 박힘·떨어짐·소멸은
            // 전이 자체가, 긴 수명은 체크포인트 경계가 revision 을 올린다.
            if (wasInGround && a.alive && a.inGround && !a.groundCheckpointDue()) {
                tickMutatesState = false;
            }
            projectileStateMutated |= tickMutatesState;
            if (hit != null) {
                if (projectileHits == null) projectileHits = new ArrayList<>();
                projectileHits.add(new ProjectileHit(a.id, a.kind, hit, a.x, a.y, a.z, a.weaponEnchantments));
            }
            if (!a.alive) {
                if (terminatedProjectiles == null) terminatedProjectiles = new ArrayList<>();
                terminatedProjectiles.add(a);
                removeArrow(a);
            } else {
                reindexArrow(a);
            }
        }
        if (projectileStateMutated) projectileRevision++;

        // 4) 스폰
        // Natural/spawner cap evaluation belongs to the same current-player simulation union as AI.  Passing the
        // historical authoritative list here made the ostensibly bounded tick still grow with every hibernated mob.
        // A jockey vehicle created above is already a live monster and must count toward this
        // same tick's global/local caps; refresh the active view only on that rare path.
        List<SpawnRequest> spawnRequests = naturalSpawningEnabled
                ? spawner.tick(world, rng, tickMobs, worldTick) : List.of();
        for (SpawnRequest r : spawnRequests) {
            List<Mob> materialized = materializeSpawnRequest(world, r);
            if (spawned == null) spawned = new ArrayList<>();
            spawned.addAll(materialized);
        }
        for (SpawnRequest r : raidSchedule.releases(world, tickMobs, worldTick, raidLedger)) {
            List<Mob> materialized = materializeSpawnRequest(world, r);
            for (Mob member : materialized) {
                if (member.raidId() == 0L) continue;
                // [RAID-OMEN] Raider#applyRaidBuffs: 합류 순간의 징조 레벨로 무기 마법 부여를 굳힌다.
                int buff = raidBuffOnJoin(member);
                if (raidLedger.join(member.raidId(), member.id, member.raidWave(), member.maxHp(), buff)) {
                    member.setRaidBuffLevel(buff);
                }
            }
            if (spawned == null) spawned = new ArrayList<>();
            spawned.addAll(materialized);
        }
        // [TRIAL] 트라이얼 스포너 웨이브. 레이드와 같은 자리·같은 스폰 경로이며, 명단은
        // 실제 몹 id 로만 성립한다(qaSpawn 이 아니라 권위 스폰 계약 그대로다).
        for (com.gameexpert.engine.trial.TrialSpawnerRuntime.Release release
                : trialSpawners.releases(world, this::trialMemberView, worldTick)) {
            List<Mob> materialized = materializeSpawnRequest(world, release.request());
            for (Mob member : materialized) {
                trialSpawners.join(release.trialId(), member.id, release.wave(), member.maxHp());
            }
            if (spawned == null) spawned = new ArrayList<>();
            spawned.addAll(materialized);
        }
        // [TRIAL-GAP] 불길한 스포너의 아이템 소환기(바닐라 spawnOminousOminousItemSpawner).
        for (var itemSpawner : trialSpawners.drainItemSpawners()) {
            spawnOminousItemSpawner(itemSpawner.x(), itemSpawner.y(), itemSpawner.z(),
                    itemSpawner.itemType(), itemSpawner.count());
        }
        // [TRIAL] 불길해진 스포너는 바닐라 resetAfterBecomingOminous 처럼 현재 몹을 치운다
        // (remove(DISCARDED) — 드랍 없음). 명단은 이미 원장에서 지웠다.
        for (long discarded : trialSpawners.drainDiscardedMembers()) {
            Mob member = mobsById.get(discarded);
            if (member == null || member.isDead() || member.removed) continue;
            member.removed = true;
            if (mobEvents == null) mobEvents = new ArrayList<>();
            mobEvents.add(new MobEmit(member.id, new MobEvent.Despawned("random")));
        }
        while (!pendingOminousItemSpawners.isEmpty()) {
            PendingOminousItemSpawner request = pendingOminousItemSpawners.poll();
            // OminousItemSpawner.create: nextIntBetweenInclusive(SPAWN_ITEM_DELAY_MIN 60, MAX 120).
            int delay = 60 + rng.nextInt(61);
            addWorldProjectile(ProjectileSim.ominousItemSpawner(request.x(), request.y(),
                    request.z(), request.itemType(), request.count(), delay));
        }
        while (!pendingExternalSpawns.isEmpty()) {
            SpawnRequest r = pendingExternalSpawns.poll();
            List<Mob> materialized = materializeSpawnRequest(world, r);
            if (spawned == null) spawned = new ArrayList<>();
            spawned.addAll(materialized);
        }

        // 5) 디스폰
        List<MobDespawn> despawned = spawner.despawns(tickMobs, world, rng);
        for (MobDespawn d : despawned) {
            d.mob().removed = true;
            if (mobEvents == null) mobEvents = new ArrayList<>();
            mobEvents.add(new MobEmit(d.mob().id, new MobEvent.Despawned(d.reason())));
        }

        // 6) 정리(죽거나 제거 표시된 몹)
        for (Mob mob : tickMobs) {
            if (!mob.isDead() && !mob.removed) continue;
            if (mob.isDead() && !permanentDeathSettledMobIds.contains(mob.id)
                    && !permanentDeathSettlement(mob).isEmpty()) {
                continue;
            }
            loveModeMobs.refresh(mob);
            removeMob(mob);
        }
        if (publishPersistenceSnapshots) {
            for (Mob mob : tickMobs) {
                if (mobsById.containsKey(mob.id)) snapshotMob(mob);
            }
        } else {
            // Spring applies fluids and emitted combat after this core pass. Mark candidates now
            // and again after settlement; both operations are allocation-free and the later live
            // state is the one materialized at the next aggregate persistence boundary.
            for (Mob mob : tickMobs) deferPersistenceSnapshot(mob);
        }

        // Externally created entities may have died (or crossed a chunk) before their first announcement.  Consume
        // their bucket only after cleanup so a dead hibernated entity cannot reappear as a later spawn event.
        List<Mob> externallySpawned = collectExternallySpawned(activeChunkKeys);
        if (externallySpawned != null) {
            if (spawned == null) spawned = new ArrayList<>();
            spawned.addAll(externallySpawned);
        }

        reconcileRaidLedger(activeChunkKeys);
        reconcileTrialLedger(world, activeChunkKeys);

        rotateActiveEntityIds(tickMobs, spawned);

        return new TickResult(
                spawned == null ? List.of() : spawned,
                despawned,
                mobEvents == null ? List.of() : mobEvents,
                projectileHits == null ? List.of() : projectileHits,
                terminatedProjectiles == null ? List.of() : terminatedProjectiles);
    }

    private List<MobEmit> consumeSmallSulfurCubes(List<Mob> tickMobs) {
        List<MobEmit> events = null;
        Set<Long> consumed = null;
        for (Mob frog : tickMobs) {
            if (frog.type != MobType.FROG || frog.isBaby() || frog.isDead() || frog.removed) continue;
            Mob nearest = null;
            double nearestDistance = Double.POSITIVE_INFINITY;
            for (Mob cube : tickMobs) {
                if (consumed != null && consumed.contains(cube.id)) continue;
                if (froglightSettlementPendingMobIds.contains(cube.id)) continue;
                if (!FrogSulfurCubeRules.canConsume(frog, cube)) continue;
                double dx = frog.x - cube.x, dy = frog.y - cube.y, dz = frog.z - cube.z;
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance < nearestDistance
                        || distance == nearestDistance && (nearest == null || cube.id < nearest.id)) {
                    nearest = cube;
                    nearestDistance = distance;
                }
            }
            if (nearest == null) continue;
            if (consumed == null) consumed = new HashSet<>();
            consumed.add(nearest.id);
            froglightSettlementPendingMobIds.add(nearest.id);
            frog.synchronizeVisualAction("eat", "active", 8);
            frog.setPersistenceRequired(true);
            if (events == null) events = new ArrayList<>();
            events.add(new MobEmit(frog.id, new MobEvent.FroglightDrop(
                    frog.id, nearest.id,
                    (short) FrogSulfurCubeRules.froglightForVariant(frog.variant()),
                    nearest.x, nearest.y, nearest.z)));
        }
        return events == null ? List.of() : events;
    }

    private static boolean pufferfishThreatened(Pufferfish pufferfish, List<Mob> mobs,
                                                 List<PlayerSnapshot> players) {
        for (PlayerSnapshot player : players) {
            if (PufferfishRules.isScaryTarget(player.alive(), false, false, false)
                    && withinInflatedBox(pufferfish, player.x(), player.y(), player.z(),
                            0.6, 1.8, PufferfishRules.THREAT_BOX_INFLATION)) {
                return true;
            }
        }
        for (Mob target : mobs) {
            if (target == pufferfish || target.isDead() || target.removed) continue;
            if (PufferfishRules.isScaryTarget(true, false, false,
                    PufferfishRules.notScaryForPufferfish(target.type))
                    && withinInflatedBox(pufferfish, target.x, target.y, target.z,
                            target.width(), target.height(), PufferfishRules.THREAT_BOX_INFLATION)) {
                return true;
            }
        }
        return false;
    }

    private static void preparePoisonDartFrogHost(
            PoisonDartFrog frog, MobWorldView world) {
        int originX = (int) Math.floor(frog.x);
        int originY = (int) Math.floor(frog.y);
        int originZ = (int) Math.floor(frog.z);
        int bestX = 0, bestY = 0, bestZ = 0;
        boolean found = false;
        int radius = PoisonDartFrogRules.HOST_SEARCH_RADIUS;
        long indexed = world.nearestFireflyBush(frog.x, frog.y, frog.z, radius, 3);
        if (indexed != Long.MIN_VALUE) {
            if (indexed != 0L) {
                bestX = com.gameexpert.engine.RafflesiaRules.unpackX(indexed);
                bestY = com.gameexpert.engine.RafflesiaRules.unpackY(indexed);
                bestZ = com.gameexpert.engine.RafflesiaRules.unpackZ(indexed);
                found = true;
            }
        } else for (int y = Math.max(Blocks.MIN_Y, originY - 3);
                y <= Math.min(Blocks.MAX_Y, originY + 3); y++) {
            for (int x = originX - radius; x <= originX + radius; x++) {
                for (int z = originZ - radius; z <= originZ + radius; z++) {
                    short block = world.getBlock(x, y, z);
                    if (block < 0 || !FireflyBushEcology.isConsumableHost(
                            block & 0xffff, world.localBrightness(x, y, z))) continue;
                    if (!found || PoisonDartFrogRules.compareHost(
                            frog.x, frog.y, frog.z, x, y, z, bestX, bestY, bestZ) < 0) {
                        found = true;
                        bestX = x;
                        bestY = y;
                        bestZ = z;
                    }
                }
            }
        }
        if (found) frog.setFireflyHost(bestX, bestY, bestZ);
        else frog.noFireflyHostFound();
    }

    private void prepareFrogConversionTarget(Frog frog, MobWorldView world) {
        if (!frogColonyPersistenceInstalled || !frog.shouldFindConversionTarget()
                || !FrogPoisonConversionRules.conversionNight(world.worldTime())) return;
        if (world.nearestPoisonFrogColony(frog.x, frog.y, frog.z, frogColonySearch)) {
            frog.setConversionTarget(frogColonySearch[0], frogColonySearch[1],
                    frogColonySearch[2], frogColonySearch[3], frogColonySearch[4],
                    frogColonySearch[5]);
        } else {
            frog.clearConversionTarget();
        }
    }

    private List<Mob> settleReachedFrogConversions(MobWorldView world, List<Mob> tickMobs) {
        if (!frogColonyPersistenceInstalled
                || !FrogPoisonConversionRules.conversionNight(world.worldTime())) return List.of();
        long now = absoluteMcTick(world);
        for (Mob mob : tickMobs) {
            if (!(mob instanceof Frog frog) || frog.isBaby()
                    || !frog.reachedConversionTarget()) continue;
            if (pendingFrogConversions.containsKey(frog.id)) continue;
            if (!validConversionTarget(world, frog)
                    || !frogColonyReady(frog.colonyX(), frog.colonyY(), frog.colonyZ(), now)) {
                frog.clearConversionTarget();
                continue;
            }
            String variant = PoisonDartFrogRules.variant(worldSeed, frog.id,
                    frog.x, frog.z);
            Mob replacement = prepareFrogReplacement(frog, variant);
            FrogConversionWork work = new FrogConversionWork(frog.id, 1L,
                    frog.colonyX(), frog.colonyY(), frog.colonyZ(),
                    frog.conversionHostX(), frog.conversionHostY(), frog.conversionHostZ(),
                    variant, now, snapshotOf(replacement, leashHolderByMobId.get(frog.id)), null);
            pendingFrogConversions.put(frog.id,
                    new PendingFrogConversion(work, frog, replacement));
        }
        // The source remains live until the writer commits APPLIED and its owner completion runs.
        return List.of();
    }

    private boolean validConversionTarget(MobWorldView world, Frog frog) {
        return FrogPoisonConversionRules.validPair(
                world.getBlock(frog.colonyX(), frog.colonyY(), frog.colonyZ()) & 0xffff,
                world.getBlock(frog.conversionHostX(), frog.conversionHostY(),
                        frog.conversionHostZ()) & 0xffff,
                world.localBrightness(frog.conversionHostX(), frog.conversionHostY(),
                        frog.conversionHostZ()),
                frog.colonyX(), frog.colonyY(), frog.colonyZ(),
                frog.conversionHostX(), frog.conversionHostY(), frog.conversionHostZ());
    }

    private void prepareRecoveredFrogConversion(FrogConversionIntent intent) {
        if (intent == null || intent.isCommitted()) return;
        Mob source = mobsById.get(intent.getSourceMobId());
        if (source == null) return;
        Mob replacement = source.type == MobType.POISON_DART_FROG
                ? source : source instanceof Frog
                        ? prepareFrogReplacement(source, intent.getDeterministicVariant()) : null;
        MobPersistenceSnapshot snapshot = replacement == null ? null
                : snapshotOf(replacement, leashHolderByMobId.get(source.id));
        FrogConversionWork work = new FrogConversionWork(intent.getSourceMobId(),
                intent.getSequence(), intent.getColonyX(), intent.getColonyY(), intent.getColonyZ(),
                intent.getHostX(), intent.getHostY(), intent.getHostZ(),
                intent.getDeterministicVariant(), 0L, snapshot, intent);
        pendingFrogConversions.put(source.id,
                new PendingFrogConversion(work, source, replacement));
    }

    private Mob prepareFrogReplacement(Mob source, String variant) {
        Mob replacement = MobFactory.create(MobType.POISON_DART_FROG, source.id,
                source.x, source.y, source.z, worldSeed, false,
                variant);
        replacement.inheritConversionState(source);
        replacement.setPersistenceRequired(true);
        return replacement;
    }

    /** Owner completion after the APPLIED transaction committed. */
    public FrogConversionInstallResult installAppliedFrogConversion(
            FrogConversionWork work, FrogConversionIntent durableIntent) {
        if (work == null || durableIntent == null
                || durableIntent.getSourceMobId() != work.sourceMobId()
                || durableIntent.getSequence() != work.sequence()
                || durableIntent.getPhase() == FrogConversionIntent.Phase.PENDING) {
            throw new IllegalArgumentException("frog conversion completion is not durably applied");
        }
        PendingFrogConversion pending = pendingFrogConversions.get(work.sourceMobId());
        if (pending == null || pending.work() != work) return FrogConversionInstallResult.STALE;
        if (work.recoveredIntent() == null) {
            frogColonyReadyMcTick.put(colonyKey(work.colonyX(), work.colonyY(), work.colonyZ()),
                    Math.addExact(work.currentMcTick(),
                            FrogPoisonConversionRules.COLONY_COOLDOWN_MC_TICKS));
        }
        Mob live = mobsById.get(work.sourceMobId());
        if (live == pending.expectedSource()
                && live.type == MobType.POISON_DART_FROG) {
            pendingFrogConversions.remove(work.sourceMobId());
            return FrogConversionInstallResult.ALREADY_INSTALLED;
        }
        if (live != pending.expectedSource() || !(live instanceof Frog)
                || pending.preparedReplacement() == null) {
            pendingFrogConversions.remove(work.sourceMobId());
            return FrogConversionInstallResult.STALE;
        }
        Mob replacement = pending.preparedReplacement();
        replaceMobIdentity(live, replacement);
        snapshotMob(replacement);
        pendingFrogConversions.remove(work.sourceMobId());
        return FrogConversionInstallResult.INSTALLED;
    }

    /** Colony contention denied this exact request; only the still-identical source is retargeted. */
    public void rejectFrogConversion(FrogConversionWork work) {
        PendingFrogConversion pending = pendingFrogConversions.get(work.sourceMobId());
        if (pending == null || pending.work() != work) return;
        pendingFrogConversions.remove(work.sourceMobId());
        if (mobsById.get(work.sourceMobId()) == pending.expectedSource()
                && pending.expectedSource() instanceof Frog frog) {
            frog.clearConversionTarget();
        }
    }

    private void replaceMobIdentity(Mob source, Mob replacement) {
        invalidateChunkQueries();
        int listIndex = mobs.indexOf(source);
        if (listIndex >= 0) mobs.set(listIndex, replacement);
        Long chunk = mobChunkById.get(source.id);
        if (chunk != null) {
            LinkedHashSet<Mob> bucket = mobsByChunk.get(chunk);
            if (bucket != null) {
                bucket.remove(source);
                bucket.add(replacement);
            }
        }
        LinkedHashSet<Mob> external = externallySpawnedByChunk.get(chunk);
        if (external != null && external.remove(source)) external.add(replacement);
        mobsById.put(source.id, replacement);
        source.removed = true;
    }

    private static boolean touchesPoisonDartFrog(PoisonDartFrog frog,
            double targetX, double targetY, double targetZ,
            double targetWidth, double targetHeight) {
        double horizontal = (frog.width() + targetWidth) * 0.5;
        return Math.abs(frog.x - targetX) <= horizontal
                && Math.abs(frog.z - targetZ) <= horizontal
                && frog.y < targetY + targetHeight
                && frog.y + frog.height() > targetY;
    }

    private static void synchronizeSpeciesVisualState(Mob mob) {
        if (mob.type == MobType.GOAT) {
            switch (mob.goatRamPhase()) {
                case PREPARING -> mob.synchronizeVisualAction("charge", "windup", 1);
                case CHARGING -> mob.synchronizeVisualAction("charge", "active", 1);
                default -> {
                    if ("charge".equals(mob.actionKind())) {
                        mob.synchronizeVisualAction("none", "idle", 0);
                    }
                }
            }
            return;
        }
        if (mob.type != MobType.SNIFFER) return;
        switch (mob.snifferDigPhase()) {
            case SNIFFING -> mob.synchronizeVisualAction("work", "lowering", 1);
            case DIGGING -> mob.synchronizeVisualAction("work", "digging", 1);
            case RISING -> mob.synchronizeVisualAction("work", "rising", 1);
            default -> {
                if ("work".equals(mob.actionKind())) {
                    mob.synchronizeVisualAction("none", "idle", 0);
                }
            }
        }
    }

    private static boolean touchesPlayer(Pufferfish pufferfish, PlayerSnapshot player) {
        return withinInflatedBox(pufferfish, player.x(), player.y(), player.z(), 0.6, 1.8, 0.0);
    }

    private static boolean playerWithinSkeletonTrapRange(
            SkeletonHorse horse, List<PlayerSnapshot> players) {
        double rangeSquared = SkeletonHorse.TRAP_TRIGGER_RANGE
                * SkeletonHorse.TRAP_TRIGGER_RANGE;
        for (PlayerSnapshot player : players) {
            if (!player.alive()) continue;
            double dx = player.x() - horse.x;
            double dy = player.y() - horse.y;
            double dz = player.z() - horse.z;
            if (dx * dx + dy * dy + dz * dz <= rangeSquared) return true;
        }
        return false;
    }

    private static boolean touchesMobInflated(Pufferfish pufferfish, Mob target,
                                               double inflation) {
        return withinInflatedBox(pufferfish, target.x, target.y, target.z,
                target.width(), target.height(), inflation);
    }

    private static boolean withinInflatedBox(Pufferfish pufferfish,
                                             double targetX, double targetY, double targetZ,
                                             double targetWidth, double targetHeight,
                                             double inflation) {
        double scale = pufferfish.puffState().stage().dimensionScale();
        double pufferHalf = pufferfish.width() * scale * 0.5 + inflation;
        double targetHalf = targetWidth * 0.5;
        return Math.abs(pufferfish.x - targetX) <= pufferHalf + targetHalf
                && Math.abs(pufferfish.z - targetZ) <= pufferHalf + targetHalf
                && pufferfish.y - inflation < targetY + targetHeight
                && pufferfish.y + pufferfish.height() * scale + inflation > targetY;
    }

    private static boolean touchesRamTarget(Mob goat, double targetX, double targetY,
                                            double targetZ, double targetWidth,
                                            double targetHeight) {
        double horizontal = (goat.width() + targetWidth) * 0.5 + 0.15;
        return Math.abs(goat.x - targetX) <= horizontal
                && Math.abs(goat.z - targetZ) <= horizontal
                && goat.y < targetY + targetHeight && goat.y + goat.height() > targetY;
    }

    /**
     * Brings the raid ledger back in step with the authority after cleanup.
     *
     * <p>A member that merely left the active chunk set is still owned by the runtime and keeps its
     * ledger row, so an unload can never be mistaken for a kill. Only a member the runtime no longer
     * holds at all — death or despawn — is retired, which is exactly the victory condition.</p>
     */
    private void reconcileRaidLedger(Set<Long> activeChunkKeys) {
        com.gameexpert.engine.raid.RaidLedger.Instance instance = raidLedger.ongoing();
        if (instance == null) return;
        for (Long memberId : instance.memberIds()) {
            Mob member = mobsById.get(memberId);
            if (member == null || member.isDead() || member.removed) {
                // [RAID-OMEN] Raider#die: 플레이어가 죽인 레이더는 그 플레이어를 마을의 영웅으로 올린다.
                if (member != null && member.isDead()) {
                    raidLedger.addHero(memberId, member.recentPlayerKillCredit());
                }
                raidLedger.retireMember(memberId);
            } else {
                raidLedger.observeMember(memberId, member.exactHealth());
            }
        }
        Mob anchor = mobsById.get(instance.anchorMobId());
        boolean anchorPresent = anchor != null && !anchor.isDead() && !anchor.removed;
        Long anchorChunk = anchorPresent ? mobChunkById.get(instance.anchorMobId()) : null;
        boolean anchorTicking = anchorChunk != null && activeChunkKeys.contains(anchorChunk);
        com.gameexpert.engine.raid.RaidLedger.Transition transition =
                raidLedger.advance(instance.raidId(), worldTick, anchorPresent, anchorTicking);
        // The village becomes raidable again in the same tick its raid ends. A Raid Omen whose last
        // tick happens to be this one must still be able to arm, exactly as vanilla lets the next
        // omen create a new raid at a center whose previous raid was just removed.
        if (transition == com.gameexpert.engine.raid.RaidLedger.Transition.VICTORY) {
            raidVictories.add(instance);
        }
        if (transition != com.gameexpert.engine.raid.RaidLedger.Transition.NONE) {
            for (long memberId : instance.memberIds()) {
                Mob member = mobsById.get(memberId);
                if (member == null || member.raidId() != instance.raidId()) continue;
                member.clearRaid();
                refreshPersistenceSnapshot(member);
            }
            raidSchedule.releaseResolved(raidLedger);
        }
    }

    /**
     * [TRIAL] 시련 명단·상태 기계를 틱 끝에서 권위와 맞춘다.
     *
     * <p>anchor 는 몹이 아니라 스포너 블록이다. 비활성 청크의 부재 판정을 걸러내는 일은
     * {@code TrialSpawnerRuntime.advance} 가 소유하므로 여기서는 두 사실만 넘긴다.</p>
     */
    private void reconcileTrialLedger(MobWorldView world, Set<Long> activeChunkKeys) {
        trialSpawners.reconcileMembers(new com.gameexpert.engine.trial.TrialSpawnerRuntime
                .MemberObservation() {
            @Override public Double health(long mobId) {
                Mob member = mobsById.get(mobId);
                if (member == null || member.isDead() || member.removed) return null;
                return member.exactHealth();
            }

            // [TRIAL-GAP] 바닐라 shouldMobBeUntracked 의 47 블록 추적 한계가 읽는 좌표.
            @Override public double[] position(long mobId) {
                Mob member = mobsById.get(mobId);
                return member == null ? null : new double[] {member.x, member.y, member.z};
            }
        });
        trialSpawners.advance(worldTick,
                (x, y, z) -> world.getBlock(x, y, z) == (short) Blocks.TRIAL_SPAWNER,
                (chunkX, chunkZ) -> chunkTicking(activeChunkKeys, chunkX, chunkZ));
    }

    /** [TRIAL-GAP] 트라이얼 명단원의 {x, y, z, 키}. 죽었거나 제거된 몹은 null. */
    private double[] trialMemberView(long mobId) {
        Mob member = mobsById.get(mobId);
        if (member == null || member.isDead() || member.removed) return null;
        return new double[] {member.x, member.y, member.z, member.height()};
    }

    private static boolean chunkTicking(Set<Long> activeChunkKeys, int chunkX, int chunkZ) {
        return activeChunkKeys.contains(((long) chunkX << 32) ^ (chunkZ & 0xffff_ffffL));
    }

    /**
     * Explicit restore/corruption repair seam. Ordinary ticks use the indexed O(P) validator below;
     * this full scan retains lowest-passenger-id duplicate ownership independent of restore order.
     */
    void reconcileMobVehicles() {
        mobVehicleFullRepairCount++;
        mobPassengersById.clear();
        mobPassengerByVehicleId.clear();
        List<Mob> ordered = new ArrayList<>(mobs);
        ordered.sort(Comparator.comparingLong(mob -> mob.id));
        for (Mob passenger : ordered) {
            long vehicleId = passenger.vehicleMobId();
            if (vehicleId == 0) continue;
            Mob vehicle = mobsById.get(vehicleId);
            boolean valid = validMobPassengerEdge(passenger, vehicle)
                    && !mobPassengerByVehicleId.containsKey(vehicleId);
            if (!valid) {
                clearMobVehicleWithoutNotification(passenger);
                snapshotMob(passenger);
                continue;
            }
            mobPassengersById.put(passenger.id, passenger);
            mobPassengerByVehicleId.put(vehicleId, passenger);
        }
        mobPassengerIndexDirty = false;
    }

    /** Validates only live passenger edges; no full mob copy, sort or normal-tick allocation. */
    void validateMobPassengerEdges() {
        if (mobPassengerIndexDirty) {
            reconcileMobVehicles();
            return;
        }
        if (mobPassengersById.size() != mobPassengerByVehicleId.size()) {
            reconcileMobVehicles();
            return;
        }
        Iterator<Map.Entry<Long, Mob>> iterator = mobPassengersById.entrySet().iterator();
        while (iterator.hasNext()) {
            Mob passenger = iterator.next().getValue();
            long vehicleId = passenger.vehicleMobId();
            Mob vehicle = mobsById.get(vehicleId);
            if (vehicleId != 0 && validMobPassengerEdge(passenger, vehicle)
                    && mobPassengerByVehicleId.get(vehicleId) == passenger) continue;
            iterator.remove();
            if (mobPassengerByVehicleId.get(vehicleId) == passenger) {
                mobPassengerByVehicleId.remove(vehicleId);
            }
            clearMobVehicleWithoutNotification(passenger);
            snapshotMob(passenger);
        }
    }

    private boolean validMobPassengerEdge(Mob passenger, Mob vehicle) {
        boolean drownedJockey = passenger instanceof Drowned drowned
                && drowned.jockeyDecisionSettled() && drowned.jockeyDecisionWinner()
                && vehicle != null && vehicle.type == MobType.ZOMBIE_NAUTILUS;
        boolean skeletonTrapRider = passenger.type == MobType.SKELETON
                && vehicle instanceof SkeletonHorse skeletonHorse
                && !skeletonHorse.trapActive();
        return (drownedJockey || skeletonTrapRider)
                && vehicle != passenger && !passenger.isDead() && !passenger.removed
                && !vehicle.isDead() && !vehicle.removed;
    }

    private void ensureMobPassengerIndex() {
        if (!mobPassengerIndexDirty) return;
        mobPassengersById.clear();
        mobPassengerByVehicleId.clear();
        for (Mob passenger : mobs) {
            long vehicleId = passenger.vehicleMobId();
            if (vehicleId == 0) continue;
            mobPassengersById.put(passenger.id, passenger);
            Mob incumbent = mobPassengerByVehicleId.get(vehicleId);
            if (incumbent == null || passenger.id < incumbent.id) {
                mobPassengerByVehicleId.put(vehicleId, passenger);
            }
        }
        mobPassengerIndexDirty = false;
    }

    private void clearMobVehicleWithoutNotification(Mob passenger) {
        suppressVehicleBindingNotification = true;
        try {
            passenger.clearMobVehicle();
        } finally {
            suppressVehicleBindingNotification = false;
        }
    }

    @Override
    public void onVehicleBindingChanged(Mob passenger, long previousVehicleId, long vehicleId) {
        if (!suppressVehicleBindingNotification && mobsById.get(passenger.id) == passenger) {
            mobPassengerIndexDirty = true;
        }
    }

    public void syncMobPassengersToVehicles() {
        ensureMobPassengerIndex();
        for (Mob passenger : mobPassengersById.values()) {
            Mob vehicle = mobsById.get(passenger.vehicleMobId());
            if (vehicle == null) continue; // reconcileMobVehicles already cleared this case.
            passenger.vehicleType = vehicle.type; // [SPEAR-MOB] chargeSpeedModifier 의 뿌리 탈것.
            passenger.syncMobVehicle(vehicle.x,
                    vehicle.y + vehicle.height() * 0.75, vehicle.z, vehicle.yaw);
            reindexMob(passenger);
        }
    }

    /** A mob passenger occupies the vehicle's sole first-passenger seat before player mounting. */
    public boolean hasMobPassenger(long vehicleMobId) {
        if (vehicleMobId <= 0) return false;
        ensureMobPassengerIndex();
        Mob passenger = mobPassengerByVehicleId.get(vehicleMobId);
        return passenger != null && !passenger.isDead() && !passenger.removed;
    }

    int indexedMobPassengerCount() { return mobPassengersById.size(); }
    long mobVehicleFullRepairCount() { return mobVehicleFullRepairCount; }
    long illagerCompanionGlobalHistoryScanCount() {
        return illagerCompanionGlobalHistoryScanCount;
    }

    List<Mob> materializeSpawnRequest(MobWorldView world, SpawnRequest request) {
        if (request.naturalZombieHorseJockey() && request.type() != MobType.ZOMBIE_HORSE) {
            return List.of();
        }
        Drowned jockeyPassenger = null;
        if (request.drownedJockeyPassengerMobId() != 0L) {
            Mob candidate = mobsById.get(request.drownedJockeyPassengerMobId());
            if (request.type() != MobType.ZOMBIE_NAUTILUS
                    || !(candidate instanceof Drowned drowned)
                    || drowned.isDead() || drowned.removed || drowned.isMobPassenger()
                    || !drowned.jockeyDecisionSettled()
                    || !drowned.jockeyDecisionWinner()) {
                return List.of();
            }
            jockeyPassenger = drowned;
        }
        // Zombie Nautilus coral variant is fixed at spawn in warm_ocean (raw biome 44).
        String spawnVariant = request.variant();
        if (request.type() == MobType.ZOMBIE_NAUTILUS && spawnVariant == null
                && world.biomeAt((int) Math.floor(request.x()), (int) Math.floor(request.y()),
                        (int) Math.floor(request.z())) == 44) spawnVariant = "warm";
        Mob handler = request.slimeSize() > 0
                // [TRIAL-GAP] spawn_potentials 의 Size 태그가 정한 크기. NBT 가 id 만이 아니어서 바닐라는
                // finalizeSpawn 을 부르지 않고 이 크기를 그대로 둔다.
                ? new Slime(allocateMobId(), request.x(), request.y(), request.z(),
                        request.slimeSize())
                : MobFactory.create(request.type(), allocateMobId(), request.x(), request.y(),
                        request.z(), worldSeed, request.firstCowInHerd(), spawnVariant);
        // [TRIAL-GAP] 바닐라 TrialSpawner.spawnMob: setPersistenceRequired() 뒤 SpawnData.equipment 를
        // 입힌다(slot_drop_chances 0.0).
        if (request.persistenceRequired()) handler.setPersistenceRequired(true);
        if (!request.trialEquipment().isEmpty()) handler.installTrialEquipment(request.trialEquipment());
        mobs.add(handler);
        indexMob(handler);
        if (request.naturalZombieHorseJockey()) {
            Mob rider = MobFactory.create(MobType.ZOMBIE, allocateMobId(), request.x(), request.y(),
                    request.z(), worldSeed, false, null);
            rider.installGeneratedHeldItem(
                    com.gameexpert.engine.inventory.PlayerInventory.IRON_SPEAR);
            rider.mountMobVehicle(handler.id, handler.x,
                    handler.y + handler.height() * 0.75, handler.z, handler.yaw);
            mobs.add(rider);
            indexMob(rider);
            snapshotMob(handler);
            snapshotMob(rider);
            return List.of(handler, rider);
        }
        if (jockeyPassenger != null) {
            jockeyPassenger.setPersistenceRequired(true);
            handler.setPersistenceRequired(true);
            jockeyPassenger.mountMobVehicle(handler.id, handler.x,
                    handler.y + handler.height() * 0.75, handler.z, handler.yaw);
            snapshotMob(jockeyPassenger);
        }
        if (request.naturalDrownedJockeyEligible() && handler instanceof Drowned drowned
                && !handler.isBaby()) {
            drowned.armJockeyDecision();
        }
        // Membership is branded before any early return so a raider whose species is not companion
        // eligible (Witch, Ravager, Illusioner…) still belongs to the raid ledger and survives a
        // restart: an active raider is distance-despawn protected in vanilla.
        long raidId = request.raidId();
        if (raidId != 0L && MobRelationshipPolicy.isRaidMembershipType(handler.type)) {
            handler.assignRaid(raidId, request.raidWave());
            handler.setPersistenceRequired(true);
        }
        IllagerCompanionPolicy.Context context = request.companionContext();
        if (context == null) {
            snapshotMob(handler);
            return List.of(handler);
        }
        IllagerCompanionPolicy.HandlerRole role = IllagerCompanionPolicy.roleFor(handler.type);
        if (!IllagerCompanionPolicy.isEligible(context, role)) {
            snapshotMob(handler);
            return List.of(handler);
        }
        handler.assignIllagerContext(context, request.companionContextIdentity(),
                IllagerCompanionPolicy.POLICY_VERSION);
        if (handler instanceof Demolisher demolisher) {
            demolisher.configurePickaxe(worldSeed, request.companionContextIdentity());
        }
        // 개체별 판정은 정확히 한 번만 확정된다. 이미 확정된 개체는 재굴림도 두 번째 동행 동물도 없다.
        if (handler.companionDecisionSettled()) {
            snapshotMob(handler);
            return List.of(handler);
        }
        IllagerCompanionPolicy.Decision decision = IllagerCompanionPolicy.evaluate(
                context, role, worldSeed, request.companionContextIdentity(), handler.id);
        if (decision == null || !decision.winner()) {
            handler.settleCompanionDecision(false);
            snapshotMob(handler);
            return List.of(handler);
        }
        MobType companionType = decision.species() == IllagerCompanionPolicy.Species.BRIARBACK
                ? MobType.BRIARBACK : MobType.GLOAMKITE;
        double[] position = companionSpawnPosition(world, handler, companionType,
                decision.appearanceLane());
        // 자리를 못 찾은 승리 개체는 확정하지 않는다. 굴림은 순수 함수라 다음 시도도 같은 결과다.
        if (position == null) {
            snapshotMob(handler);
            return List.of(handler);
        }
        handler.setPersistenceRequired(true);
        handler.settleCompanionDecision(true);
        snapshotMob(handler);
        Mob companion = MobFactory.create(companionType, allocateMobId(), position[0], position[1],
                position[2], worldSeed, false, null);
        ((IllagerCompanionMob) companion).bindHandler(handler.id, context,
                request.companionContextIdentity(), decision.appearanceLane(),
                IllagerCompanionPolicy.POLICY_VERSION, 0);
        if (raidId != 0L) companion.assignRaid(raidId, request.raidWave());
        mobs.add(companion);
        indexMob(companion);
        snapshotMob(companion);
        return List.of(handler, companion);
    }

    /** Site occupants are already materialized outside SpawnRequest; use the same selection contract. */
    public Mob maybeAddIllagerCompanion(MobWorldView world, Mob handler,
            IllagerCompanionPolicy.Context context, long contextIdentity) {
        if (handler == null || handler.isDead() || handler.removed) return null;
        IllagerCompanionPolicy.HandlerRole role = IllagerCompanionPolicy.roleFor(handler.type);
        if (!IllagerCompanionPolicy.isEligible(context, role)) return null;
        handler.assignIllagerContext(context, contextIdentity,
                IllagerCompanionPolicy.POLICY_VERSION);
        if (handler instanceof Demolisher demolisher) {
            demolisher.configurePickaxe(worldSeed, contextIdentity);
        }
        if (handler.companionDecisionSettled()) {
            snapshotMob(handler);
            return null;
        }
        IllagerCompanionPolicy.Decision decision = IllagerCompanionPolicy.evaluate(
                context, role, worldSeed, contextIdentity, handler.id);
        if (decision == null || !decision.winner()) {
            handler.settleCompanionDecision(false);
            snapshotMob(handler);
            return null;
        }
        MobType type = decision.species() == IllagerCompanionPolicy.Species.BRIARBACK
                ? MobType.BRIARBACK : MobType.GLOAMKITE;
        double[] position = companionSpawnPosition(world, handler, type, decision.appearanceLane());
        if (position == null) {
            snapshotMob(handler);
            return null;
        }
        handler.setPersistenceRequired(true);
        handler.settleCompanionDecision(true);
        snapshotMob(handler);
        Mob companion = addMob(type, position[0], position[1], position[2], true);
        ((IllagerCompanionMob) companion).bindHandler(handler.id, context, contextIdentity,
                decision.appearanceLane(), IllagerCompanionPolicy.POLICY_VERSION, 0);
        snapshotMob(companion);
        return companion;
    }

    private double[] companionSpawnPosition(MobWorldView world, Mob handler, MobType type,
            long appearanceLane) {
        int start = (int) (appearanceLane & 7L);
        int[][] offsets = {{2, 0}, {2, 2}, {0, 2}, {-2, 2}, {-2, 0}, {-2, -2},
                {0, -2}, {2, -2}};
        for (int ordinal = 0; ordinal < offsets.length; ordinal++) {
            int[] offset = offsets[(start + ordinal) & 7];
            double x = Math.floor(handler.x) + offset[0] + 0.5;
            double z = Math.floor(handler.z) + offset[1] + 0.5;
            int blockX = (int) Math.floor(x);
            int blockZ = (int) Math.floor(z);
            if (!world.isChunkActive(Math.floorDiv(blockX, Blocks.CHUNK_X),
                    Math.floorDiv(blockZ, Blocks.CHUNK_Z))) continue;
            int y = type == MobType.GLOAMKITE
                    ? Math.min(Blocks.MAX_Y - 2, (int) Math.floor(handler.y) + 2)
                    : world.motionBlockingNoLeavesHeight(blockX, blockZ) + 1;
            if (!validCompanionCell(world, type, x, y, z)) continue;
            boolean collision = false;
            for (Mob existing : mobs) {
                if (existing.isDead() || existing.removed) continue;
                if (Math.abs(existing.x - x) < (existing.width() + type.width()) * 0.5
                        && Math.abs(existing.z - z) < (existing.width() + type.width()) * 0.5
                        && y < existing.y + existing.height()
                        && y + type.height() > existing.y) {
                    collision = true;
                    break;
                }
            }
            if (!collision) return new double[]{x, y, z};
        }
        return null;
    }

    private static boolean validCompanionCell(MobWorldView world, MobType type,
            double x, int y, double z) {
        if (y <= Blocks.MIN_Y || y + type.height() >= Blocks.MAX_Y) return false;
        double half = type.width() * 0.5;
        int minX = (int) Math.floor(x - half);
        int maxX = (int) Math.floor(x + half - 1e-9);
        int minZ = (int) Math.floor(z - half);
        int maxZ = (int) Math.floor(z + half - 1e-9);
        int maxY = (int) Math.ceil(y + type.height()) - 1;
        for (int bx = minX; bx <= maxX; bx++) for (int bz = minZ; bz <= maxZ; bz++) {
            if (type != MobType.GLOAMKITE) {
                short support = world.getBlock(bx, y - 1, bz);
                if (support < 0 || !world.isSolid(support)) return false;
            }
            for (int by = y; by <= maxY; by++) {
                short block = world.getBlock(bx, by, bz);
                if (block < 0 || world.isSolid(block) || Fluids.isFluid(block)) return false;
            }
        }
        return true;
    }

    private List<Shulker> cachedTickShulkers;
    private List<Mob> cachedTickShulkersSource;
    private List<ItemFrameRules.Hanging> cachedTickFrames;
    private List<Mob> cachedTickFramesSource;

    /** [EC-MOBS] 이번 틱 활성 셜커(같은 목록이면 한 번만 모은다). */
    private List<Shulker> tickShulkers(List<Mob> tickMobs) {
        if (cachedTickShulkersSource != tickMobs) {
            List<Shulker> found = new ArrayList<>();
            for (Mob mob : tickMobs) if (mob instanceof Shulker shulker && !shulker.isDead()) found.add(shulker);
            cachedTickShulkers = found;
            cachedTickShulkersSource = tickMobs;
        }
        return cachedTickShulkers;
    }

    /** [EC-MOBS] 이번 틱 활성 아이템 액자의 걸린 자리. */
    private List<ItemFrameRules.Hanging> tickFrames(List<Mob> tickMobs) {
        if (cachedTickFramesSource != tickMobs) {
            List<ItemFrameRules.Hanging> found = new ArrayList<>();
            for (Mob mob : tickMobs) if (mob instanceof ItemFrame frame && !frame.isDead()) found.add(frame.hanging());
            cachedTickFrames = found;
            cachedTickFramesSource = tickMobs;
        }
        return cachedTickFrames;
    }

    /** [EC-MOBS] 셜커 탄환의 대상 사실(발밑 위치 · 높이). 없으면 null. */
    private ShulkerBulletRules.Target shulkerBulletTarget(MobWorldView world, String nickname, long mobId) {
        if (nickname != null) {
            for (PlayerSnapshot p : world.players()) {
                if (p.alive() && nickname.equals(p.nickname())) {
                    return new ShulkerBulletRules.Target(p.x(), p.y(), p.z(), ProjectileSim.PLAYER_HEIGHT);
                }
            }
            return null;
        }
        Mob target = mobId == 0L ? null : mobsById.get(mobId);
        return target == null || target.isDead() || target.removed ? null
                : new ShulkerBulletRules.Target(target.x, target.y, target.z, target.height());
    }

    private void summonOwnedVex(Mob summoner, MobEvent.SummonVex request, MobRandom rng) {
        int owned = 0;
        for (Mob candidate : mobs) {
            if (candidate instanceof Vex vex && !vex.isDead()
                    && vex.summonerMobId() == summoner.id) owned++;
        }
        if (owned >= 3) return;
        Mob spawned = addMob(MobType.VEX, request.x(), request.y(), request.z());
        // Vanilla 20 * (30 + nextInt(90)) MC ticks, converted to the 10 TPS authority.
        ((Vex) spawned).bindSummoner(summoner.id, 300 + rng.nextInt(90) * 10);
        snapshotMob(spawned);
    }

    private void prepareBreedingMovement(List<Mob> tickMobs) {
        for (Mob mob : tickMobs) {
            if (!mob.isInLoveMode()) continue;
            Mob partner = findBreedingPartner(mob, PARTNER_SEARCH_RANGE);
            if (partner != null) mob.seekBreedingPartner(partner);
        }
    }

    /** Prepares Tempt and FollowParent targets without consuming mob RNG. */
    private static void prepareAnimalGoalTargets(MobWorldView world, List<Mob> tickMobs) {
        for (Mob mob : tickMobs) {
            if (!(mob instanceof AnimalMob animal) || mob.isDead()) continue;
            if (AnimalGoalRules.hasPinnedTemptGoal(mob.type)) {
                PlayerSnapshot nearestPlayer = null;
                double nearestPlayerDistance = AnimalGoalRules.TEMPT_RADIUS_SQUARED;
                for (PlayerSnapshot player : world.players()) {
                    if (!player.alive() || !mob.type.isBreedingFood((short) player.heldItemType())
                            && !mob.type.isBreedingFood((short) player.offhandItemType())) continue;
                    double dx = player.x() - mob.x;
                    double dy = player.y() - mob.y;
                    double dz = player.z() - mob.z;
                    double squared = dx * dx + dy * dy + dz * dz;
                    if (squared < nearestPlayerDistance) {
                        nearestPlayer = player;
                        nearestPlayerDistance = squared;
                    }
                }
                if (nearestPlayer != null) animal.seekTemptingPlayer(nearestPlayer);
            }

            if (!mob.isBaby() || !AnimalGoalRules.followsParent(mob.type)) continue;
            Mob nearestParent = null;
            double nearestParentDistance = Double.POSITIVE_INFINITY;
            for (Mob candidate : tickMobs) {
                if (candidate == mob || candidate.isDead() || candidate.isBaby()
                        || candidate.type != mob.type) continue;
                double dx = candidate.x - mob.x;
                double dy = candidate.y - mob.y;
                double dz = candidate.z - mob.z;
                if (Math.abs(dx) > AnimalGoalRules.FOLLOW_PARENT_HORIZONTAL
                        || Math.abs(dy) > AnimalGoalRules.FOLLOW_PARENT_VERTICAL
                        || Math.abs(dz) > AnimalGoalRules.FOLLOW_PARENT_HORIZONTAL) continue;
                double squared = dx * dx + dy * dy + dz * dz;
                if (squared < AnimalGoalRules.FOLLOW_PARENT_STOP_DISTANCE_SQUARED) continue;
                if (squared < nearestParentDistance
                        || squared == nearestParentDistance
                        && (nearestParent == null || candidate.id < nearestParent.id)) {
                    nearestParent = candidate;
                    nearestParentDistance = squared;
                }
            }
            if (nearestParent != null) animal.followParent(nearestParent);
        }
    }

    /**
     * 수중 ambient 캡이 작으므로 정렬된 활성 목록에서 종별 첫 개체만 리더로 삼는다.
     * 별도 객체·공간 인덱스 없이 최대 두 번의 짧은 선형 통과로 무리 이동을 만든다.
     */
    private static void prepareSchoolingMovement(List<Mob> tickMobs) {
        Mob codLeader = null;
        Mob salmonLeader = null;
        Mob tropicalLeader = null;
        for (Mob mob : tickMobs) {
            if (mob.type == MobType.COD && codLeader == null) codLeader = mob;
            else if (mob.type == MobType.SALMON && salmonLeader == null) salmonLeader = mob;
            else if (mob.type == MobType.TROPICAL_FISH && tropicalLeader == null) tropicalLeader = mob;
        }
        for (Mob mob : tickMobs) {
            Mob leader = mob.type == MobType.COD ? codLeader
                    : mob.type == MobType.SALMON ? salmonLeader
                    : mob.type == MobType.TROPICAL_FISH ? tropicalLeader : null;
            if (leader == null || leader == mob) continue;
            double dx = leader.x - mob.x;
            double dy = leader.y - mob.y;
            double dz = leader.z - mob.z;
            if (dx * dx + dy * dy + dz * dz <= 144.0) mob.seekSchoolLeader(leader);
        }
    }

    /**
     * Selects one directed social target per eligible attacker from the already ID-sorted active list.
     * The list is bounded by the existing active-mob cap, so no per-attacker collection or index is needed.
     */
    private void prepareSocialTargets(List<Mob> tickMobs) {
        for (Mob mob : tickMobs) mob.clearPreparedSocialTarget();
        for (Mob attacker : tickMobs) {
            if (attacker.isDead() || attacker.removed) continue;
            if (attacker instanceof TraderLlama) {
                double bestDistanceSquared = TraderLlama.FOLLOW_RANGE * TraderLlama.FOLLOW_RANGE;
                Mob best = null;
                for (Mob candidate : tickMobs) {
                    if (candidate.type != MobType.WANDERING_TRADER
                            || candidate.isDead() || candidate.removed) continue;
                    double distanceSquared = MobRelationshipPolicy.distanceSquared(attacker, candidate);
                    if (distanceSquared > bestDistanceSquared) continue;
                    if (best == null || distanceSquared < bestDistanceSquared
                            || distanceSquared == bestDistanceSquared && candidate.id < best.id) {
                        best = candidate;
                        bestDistanceSquared = distanceSquared;
                    }
                }
                attacker.prepareSocialTarget(best);
                continue;
            }
            if (attacker instanceof Wolf wolf && wolf.ownerNickname() != null
                    && !wolf.wolfSitting()) {
                Mob target = ownerCombatTarget(ownerHurtByMob, wolf.ownerNickname(), wolf, tickMobs);
                if (target == null) {
                    target = ownerCombatTarget(
                            ownerAttackedMob, wolf.ownerNickname(), wolf, tickMobs);
                }
                wolf.prepareSocialTarget(target);
                continue;
            }
            if (attacker instanceof NautilusFamilyMob nautilus
                    && nautilus.wantsNaturalPufferfishTarget()) {
                double bestDistanceSquared = NautilusFamilyMob.CHARGE_TARGET_RANGE
                        * NautilusFamilyMob.CHARGE_TARGET_RANGE;
                Mob best = null;
                for (Mob candidate : tickMobs) {
                    if (!MobRelationshipPolicy.mayDirectlyTarget(nautilus, candidate)) continue;
                    double distanceSquared = MobRelationshipPolicy.distanceSquared(nautilus, candidate);
                    if (distanceSquared > bestDistanceSquared) continue;
                    if (best == null || distanceSquared < bestDistanceSquared
                            || distanceSquared == bestDistanceSquared && candidate.id < best.id) {
                        best = candidate;
                        bestDistanceSquared = distanceSquared;
                    }
                }
                nautilus.prepareSocialTarget(best);
                continue;
            }
            if (!MobRelationshipPolicy.isSocialAttacker(attacker)) continue;
            double range = MobRelationshipPolicy.targetRange(attacker);
            double bestDistanceSquared = range * range;
            Mob best = null;
            for (Mob candidate : tickMobs) {
                if (MobRelationshipPolicy.isRaiderFaction(attacker.type)
                        && candidate.type == MobType.STANDARD_BEARER
                        && !candidate.isDead() && !candidate.removed
                        && attacker.illagerContext() == IllagerCompanionPolicy.Context.RAID
                        && candidate.illagerContext() == IllagerCompanionPolicy.Context.RAID
                        && attacker.illagerContextIdentity()
                                == candidate.illagerContextIdentity()
                        && MobRelationshipPolicy.distanceSquared(attacker, candidate) <= 144.0) {
                    attacker.prepareRaidSpeedMultiplier(1.15);
                }
                if (!MobRelationshipPolicy.mayDirectlyTarget(attacker, candidate)) continue;
                double distanceSquared = MobRelationshipPolicy.distanceSquared(attacker, candidate);
                if (distanceSquared > bestDistanceSquared) continue;
                if (best == null || distanceSquared < bestDistanceSquared
                        || distanceSquared == bestDistanceSquared && candidate.id < best.id) {
                    best = candidate;
                    bestDistanceSquared = distanceSquared;
                }
            }
            attacker.prepareSocialTarget(best);
        }
    }

    private Mob ownerCombatTarget(Map<String, OwnerCombatMemory> memories,
            String ownerNickname, Wolf wolf, List<Mob> tickMobs) {
        OwnerCombatMemory memory = memories.get(ownerNickname);
        if (memory == null) return null;
        if (memory.expiresAtTick() < worldTick) {
            memories.remove(ownerNickname);
            return null;
        }
        Mob target = mobsById.get(memory.targetMobId());
        if (target == null || !tickMobs.contains(target)
                || !wolf.mayDefendOwnerAgainst(target)
                || !MobRelationshipPolicy.inTargetRange(wolf, target)) {
            return null;
        }
        return target;
    }

    private List<Mob> prepareIllagerCompanions(List<Mob> tickMobs) {
        boolean hasActiveCompanion = false;
        for (int i = 0, size = tickMobs.size(); i < size; i++) {
            Mob mob = tickMobs.get(i);
            if (mob instanceof IllagerCompanionMob && !mob.isDead() && !mob.removed) {
                hasActiveCompanion = true;
                break;
            }
        }
        if (!hasActiveCompanion) return null;

        illagerCompanionGlobalHistoryScanCount++;
        Set<Long> alreadyBound = new HashSet<>();
        for (Mob mob : mobs) {
            if (mob instanceof IllagerCompanionMob companion
                    && !mob.isDead() && !mob.removed) {
                alreadyBound.add(companion.handlerMobId());
            }
        }
        List<Mob> orphaned = null;
        for (Mob mob : tickMobs) {
            if (!(mob instanceof IllagerCompanionMob companion)
                    || mob.isDead() || mob.removed) continue;
            Mob handler = null;
            Mob boundHandler = mobsById.get(companion.handlerMobId());
            if (eligibleCompanionHandler(companion, boundHandler)) handler = boundHandler;
            if (handler == null && companion.mayRebind()) {
                long bestDistance = Long.MAX_VALUE;
                Mob best = null;
                for (Mob candidate : mobs) {
                    if (alreadyBound.contains(candidate.id)
                            || !eligibleCompanionHandler(companion, candidate)) continue;
                    double dx = candidate.x - companion.x;
                    double dy = candidate.y - companion.y;
                    double dz = candidate.z - companion.z;
                    long distance = companionDistanceKey(dx, dy, dz);
                    if (best == null || distance < bestDistance
                            || distance == bestDistance
                            && Long.compareUnsigned(candidate.id, best.id) < 0) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
                if (best != null) {
                    alreadyBound.remove(companion.handlerMobId());
                    companion.commitRebind(best.id);
                    alreadyBound.add(best.id);
                    handler = best;
                }
            }
            if (handler != null) {
                companion.clearOrphanTicks();
                companion.prepareHandler(tickMobs.contains(handler) ? handler : null);
            } else {
                companion.prepareHandler(null);
                if (companion.advanceOrphanTick()) {
                    if (orphaned == null) orphaned = new ArrayList<>();
                    orphaned.add(companion);
                }
            }
        }
        return orphaned;
    }

    private boolean eligibleCompanionHandler(
            IllagerCompanionMob companion, Mob candidate) {
        if (candidate == null || candidate.isDead() || candidate.removed
                || candidate.illagerContext() != companion.companionContext()
                || candidate.illagerContextIdentity() != companion.contextIdentity()
                || candidate.illagerPolicyVersion() != companion.policyVersion()) return false;
        IllagerCompanionPolicy.HandlerRole role = IllagerCompanionPolicy.roleFor(candidate.type);
        if (!IllagerCompanionPolicy.isEligibleRole(role)) return false;
        IllagerCompanionPolicy.Species expected = IllagerCompanionPolicy.speciesForRole(
                role, IllagerCompanionPolicy.stableAppearanceLane(
                        worldSeed, companion.companionContext(), companion.contextIdentity(),
                        candidate.id, companion.policyVersion()));
        return expected == (companion.type == MobType.BRIARBACK
                ? IllagerCompanionPolicy.Species.BRIARBACK
                : IllagerCompanionPolicy.Species.GLOAMKITE);
    }

    static long companionDistanceKey(double dx, double dy, double dz) {
        return Math.round((dx * dx + dy * dy + dz * dz) * 1_000.0);
    }

    /** 주민 사회(번식·철 골렘) 상태 소유자. 영속 flush/복구는 이 접근자로 이뤄진다. */
    public VillagerSociety villagerSociety() { return villagerSociety; }

    /**
     * 플레이어가 준 주민 음식 한 개를 willing 재고에 넣는다. 소비 판단은 호출자가 한다.
     * 근거는 {@code Villager#countFoodPointsInInventory} 의 빵4/감자·당근·비트1 표다.
     */
    public boolean offerVillagerFood(Mob mob, short itemType) {
        if (mob == null || mob.isDead() || mob.type != MobType.VILLAGER || mob.isBaby()) {
            return false;
        }
        return villagerSociety.offerItem(mob.id, itemType);
    }

    /**
     * 활성 청크의 살아 있는 주민만 사회 계층에 넘긴다. 새끼는 일반 몹으로 만들어져 같은 스폰
     * 경로를 타고, 골렘은 마을 골렘과 같은 IRON_GOLEM 으로 배치된다.
     */
    private List<Mob> tickVillagerSociety(MobWorldView world, List<Mob> tickMobs, MobRandom rng) {
        List<VillagerHandle> villagers = null;
        for (Mob mob : tickMobs) {
            if (mob.type != MobType.VILLAGER || mob.isDead() || mob.removed) continue;
            if (villagers == null) villagers = new ArrayList<>();
            villagers.add(new VillagerHandle(mob, world));
        }
        if (villagers == null) return null;
        List<Mob> born = new ArrayList<>(1);
        VillagerSociety.Outcome outcome = villagerSociety.tick(
                new SocietyWorldAdapter(world, rng, villagerPoiIndex), villagers,
                (x, y, z) -> {
                    Mob child = addMob(MobType.VILLAGER, x, y, z);
                    child.setPersistenceRequired(true);
                    setBabyAge(child, BABY_GROWTH_TICKS);
                    snapshotMob(child);
                    born.add(child);
                    return child.id;
                },
                (x, y, z) -> {
                    Mob golem = addMob(MobType.IRON_GOLEM, x + 0.5, y, z + 0.5);
                    golem.setPersistenceRequired(true);
                    snapshotMob(golem);
                    born.add(golem);
                    return true;
                });
        // 성공한 부모는 바닐라 age +6000(권위 3000틱) 만큼 다시 번식하지 못한다.
        for (VillagerSociety.Birth birth : outcome.births()) {
            Mob first = mobsById.get(birth.firstParentId());
            if (first != null) first.beginBreedingCooldown(BREEDING_COOLDOWN_TICKS);
            Mob second = mobsById.get(birth.secondParentId());
            if (second != null) second.beginBreedingCooldown(BREEDING_COOLDOWN_TICKS);
        }
        for (Mob mob : tickMobs) {
            if (mob.type != MobType.VILLAGER || mob.isDead() || mob.removed) continue;
            applyVillagerSocialVisualAction(mob);
            if (villagerSociety.mateOf(mob.id) == 0L) continue;
            Mob mate = mobsById.get(villagerSociety.mateOf(mob.id));
            if (mate != null) mob.seekBreedingPartner(mate);
        }
        return born.isEmpty() ? null : born;
    }

    /**
     * 사회 계층이 이번 틱에 유도한 표현을 몹에 반영한다. 지속되는 동안 매 틱 다시 채우고
     * (종류·위상이 그대로면 시퀀스가 오르지 않아 연출이 재시작되지 않는다) 상태가 끝나면
     * 이 lane 이 켠 종류만 되돌린다. 저장되는 상태는 하나도 늘지 않는다.
     */
    private void applyVillagerSocialVisualAction(Mob mob) {
        String action = villagerSociety.visualActionOf(mob.id);
        if (!VillagerSociety.NO_VISUAL_ACTION.equals(action)) {
            mob.synchronizeVisualAction(action, "active",
                    VillagerSociety.SOCIAL_VISUAL_ACTION_TICKS);
            return;
        }
        if (VillagerSociety.SLEEP_VISUAL_ACTION.equals(mob.actionKind())
                || VillagerSociety.SOCIALIZE_VISUAL_ACTION.equals(mob.actionKind())) {
            mob.synchronizeVisualAction(VillagerSociety.NO_VISUAL_ACTION, "idle", 0);
        }
    }

    /** {@link Mob} 를 사회 계층이 보는 최소 표면으로 감싼다. */
    private static final class VillagerHandle implements VillagerSociety.SocietyVillager {
        private final Mob mob;
        private final MobWorldView world;

        private VillagerHandle(Mob mob, MobWorldView world) {
            this.mob = mob;
            this.world = world;
        }

        /** 현재 활동의 정본은 활동 원장이다(종·습격의 HIDE/PRE_RAID/RAID 포함). */
        @Override public com.gameexpert.engine.mob.villager.VillagerBrainRules.Activity activity(
                long dayTime) {
            var decision = world.villagerActivity(mob.id);
            return decision != null ? decision.activity()
                    : VillagerSociety.SocietyVillager.super.activity(dayTime);
        }

        @Override public long id() { return mob.id; }

        @Override public double x() { return mob.x; }

        @Override public double y() { return mob.y; }

        @Override public double z() { return mob.z; }

        @Override public boolean baby() { return mob.isBaby(); }

        @Override public boolean farmer() {
            int index = (mob.visualFlags() & Mob.VISUAL_VILLAGER_PROFESSION_MASK)
                    >>> Mob.VISUAL_VILLAGER_PROFESSION_SHIFT;
            return index == com.gameexpert.engine.mob.villager.VillagerAppearance.PROFESSIONS.indexOf("farmer");
        }

        @Override public boolean breedingCooldown() { return mob.breedingCooldownTicks() > 0; }

        /** 공용 FLEE 상태가 주민 panic 의 권위 신호다(hostile sensor 는 별도 트랙). */
        @Override public boolean panicking() { return mob.state == MobState.FLEE; }
    }

    /** {@link MobWorldView} 를 사회 계층·골렘 배치 탐색이 보는 블록 분류로 옮긴다. */
    private record SocietyWorldAdapter(MobWorldView world, MobRandom rng,
            VillagerPoiIndex poiIndex)
            implements VillagerSociety.SocietyWorld {
        @Override public long dayCount() { return world.dayCount(); }

        @Override public long worldTime() { return world.worldTime(); }

        @Override public boolean bedBlockAt(int x, int y, int z) {
            return Blocks.isBed(world.getBlock(x, y, z) & 0xffff);
        }

        @Override public int[] nearestVacantBed(int originX, int originY, int originZ,
                com.gameexpert.engine.mob.villager.VillagerSocietyRules.VacantBedProbe probe) {
            if (poiIndex != null) {
                VillagerPoiIndex.SearchResult indexed = poiIndex.nearestVacantHome(
                        originX, originY, originZ,
                        (stationCode, x, y, z) -> probe.isVacantBed(x, y, z));
                if (indexed.covered()) return indexed.position();
            }
            return com.gameexpert.engine.mob.villager.VillagerSocietyRules.nearestVacantBed(
                    originX, originY, originZ, probe);
        }

        @Override public int nextInt(int bound) { return rng.nextInt(bound); }

        @Override public int blockAt(int x, int y, int z) {
            short block = world.getBlock(x, y, z);
            return block < 0 ? Blocks.STONE : block & 0xffff;
        }

        @Override public boolean solid(int blockId) { return world.isSolid((short) blockId); }

        @Override public boolean liquid(int blockId) {
            return Fluids.isWaterMedium(blockId) || Fluids.isLava(blockId);
        }

        @Override public boolean leaves(int blockId) { return BlockFamilies.isLeaves(blockId); }
    }

    private List<MobEmit> tickBreeding(MobWorldView world, List<Mob> tickMobs, MobRandom rng) {
        int eligible = 0;
        for (Mob mob : tickMobs) {
            if (mob.isInLoveMode() && ++eligible == 2) break;
        }
        if (eligible < 2) {
            breedingProgress.clear();
            breedingPartners.clear();
            return List.of();
        }
        List<MobEmit> completed = null;
        Set<Long> paired = new HashSet<>();
        Set<Long> activeSources = new HashSet<>();
        int mobCountAtStart = tickMobs.size();
        for (int index = 0; index < mobCountAtStart; index++) {
            Mob first = tickMobs.get(index);
            if (!first.isInLoveMode() || paired.contains(first.id)) continue;
            Mob second = findBreedingPartner(first, PARTNER_SEARCH_RANGE);
            if (second == null || paired.contains(second.id)) continue;
            if (first.type == MobType.PANDA
                    && (!hasNearbyBamboo(world, first) || !hasNearbyBamboo(world, second))) {
                continue;
            }
            long sourceId = Math.min(first.id, second.id);
            long partnerId = Math.max(first.id, second.id);
            activeSources.add(sourceId);
            paired.add(first.id);
            paired.add(second.id);
            double dx = first.x - second.x;
            double dy = first.y - second.y;
            double dz = first.z - second.z;
            if (dx * dx + dy * dy + dz * dz >= BIRTH_DISTANCE_SQUARED) {
                breedingProgress.remove(sourceId);
                breedingPartners.remove(sourceId);
                continue;
            }
            if (!Long.valueOf(partnerId).equals(breedingPartners.get(sourceId))) {
                breedingProgress.remove(sourceId);
                breedingPartners.put(sourceId, partnerId);
            }
            int progress = breedingProgress.getOrDefault(sourceId, 0) + 1;
            if (progress < COURTSHIP_TICKS) {
                breedingProgress.put(sourceId, progress);
                continue;
            }
            if (first.type == MobType.FROG) {
                breedingProgress.put(sourceId, COURTSHIP_TICKS);
                if (pendingFrogspawnBySource.containsKey(sourceId)) continue;
                int[] placement = frogspawnPlacement(world, first, second);
                if (placement == null) continue;
                FrogspawnPlacementRequest request = new FrogspawnPlacementRequest(
                        sourceId, partnerId, placement[0], placement[1], placement[2]);
                pendingFrogspawnBySource.put(sourceId, request);
                frogspawnPlacements.add(request);
                continue;
            }
            if (first.type == MobType.TURTLE) {
                Turtle layer = (Turtle) (first.id <= second.id ? first : second);
                if (layer.beginEggPlacement() && layer.armEggPlacementRequest()) {
                    turtleEggPlacements.add(new TurtleEggPlacementRequest(layer.id,
                            layer.homeX(), layer.homeY(), layer.homeZ(), layer.eggCount()));
                    snapshotMob(layer);
                }
                completeBreedingPair(first, second, sourceId);
                continue;
            }
            if (first.type == MobType.SNIFFER) {
                if (completed == null) completed = new ArrayList<>();
                completed.add(new MobEmit(first.id, new MobEvent.DropItem(
                        (short) Blocks.SNIFFER_EGG, 1,
                        (first.x + second.x) * 0.5, Math.min(first.y, second.y),
                        (first.z + second.z) * 0.5)));
                completed.add(new MobEmit(first.id,
                        new MobEvent.BreedingComplete(first.x, first.y, first.z)));
                completeBreedingPair(first, second, sourceId);
                continue;
            }
            MobType childType = first.type.breedingOffspringType(second.type);
            String childVariant = breedingChildVariant(childType, first, second, rng, worldSeed);
            Mob child = addMob(childType,
                    (first.x + second.x) * 0.5,
                    Math.min(first.y, second.y),
                    (first.z + second.z) * 0.5,
                    false, childVariant);
            setBabyAge(child, BABY_GROWTH_TICKS);
            if (child.type == MobType.CAT && first.ownerNickname() != null) {
                child.setOwnerNickname(first.ownerNickname());
                child.setPersistenceRequired(true);
                child.restoreCompanionState(false, FarmAnimalRules.offspringSheepColor(
                        first.catCollarColor(), second.catCollarColor(), rng));
                snapshotMob(child);
            }
            // [FARM-ANIMAL] 바닐라 Sheep.getOffspringColor: 동색 상속, 염료 조합식이 있으면 혼합색,
            // 없으면 부모 중 하나(굴림 1회). 부모 순서는 first=father, second=mother 로 고정한다.
            if (child instanceof Sheep childSheep && first instanceof Sheep father
                    && second instanceof Sheep mother) {
                childSheep.setOffspringColor(FarmAnimalRules.offspringSheepColor(
                        father.color(), mother.color(), rng));
            }
            // [MOUNT] 바닐라 AbstractHorse.setOffspringAttributes: 체력→점프→속도 순으로
            // 부모 평균 ± 폭 × (r+r+r)/3-0.5 를 굴려 계승한다(HorseRules.breedStats).
            HorseRules.applyOffspringStats(child, first, second, rng);
            if (completed == null) completed = new ArrayList<>();
            completed.add(new MobEmit(first.id,
                    new MobEvent.BreedingComplete(child.x, child.y, child.z)));
            first.beginBreedingCooldown(BREEDING_COOLDOWN_TICKS);
            second.beginBreedingCooldown(BREEDING_COOLDOWN_TICKS);
            loveModeMobs.refresh(first);
            loveModeMobs.refresh(second);
            breedingProgress.remove(sourceId);
            breedingPartners.remove(sourceId);
        }
        breedingProgress.keySet().removeIf(id -> !activeSources.contains(id));
        breedingPartners.keySet().removeIf(id -> !activeSources.contains(id));
        return completed == null ? List.of() : completed;
    }

    private static int[] frogspawnPlacement(MobWorldView world, Mob first, Mob second) {
        int centerX = (int) Math.floor((first.x + second.x) * 0.5);
        int centerY = (int) Math.floor(Math.min(first.y, second.y));
        int centerZ = (int) Math.floor((first.z + second.z) * 0.5);
        for (int radius = 0; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius != 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = centerX + dx, z = centerZ + dz;
                    for (int dy = -1; dy <= 1; dy++) {
                        int y = centerY + dy;
                        if (world.getBlock(x, y, z) == Blocks.AIR
                                && Fluids.isWaterMedium(world.getBlock(x, y - 1, z) & 0xffff)) {
                            return new int[] {x, y, z};
                        }
                    }
                }
            }
        }
        return null;
    }

    private void completeBreedingPair(Mob first, Mob second, long sourceId) {
        first.beginBreedingCooldown(BREEDING_COOLDOWN_TICKS);
        second.beginBreedingCooldown(BREEDING_COOLDOWN_TICKS);
        loveModeMobs.refresh(first);
        loveModeMobs.refresh(second);
        breedingProgress.remove(sourceId);
        breedingPartners.remove(sourceId);
    }

    /** PandaBreedGoal searches Y+0..2 and the complete horizontal radius 0..7 for one bamboo. */
    private static boolean hasNearbyBamboo(MobWorldView world, Mob panda) {
        int originX = (int) Math.floor(panda.x);
        int originY = (int) Math.floor(panda.y);
        int originZ = (int) Math.floor(panda.z);
        for (int dy = 0; dy <= 2; dy++) {
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    if ((world.getBlock(originX + dx, originY + dy, originZ + dz) & 0xffff)
                            == Blocks.BAMBOO) return true;
                }
            }
        }
        return false;
    }

    /**
     * 직전 틱엔 활성이었으나 이번 틱 hibernate된 청크의 소멸 대상 몹을 제거한다. 매 틱 hibernate되는
     * 청크 수는 플레이어 이동에 비례해 작으며, 대부분의 틱은 활성 집합이 그대로라 아무 것도 하지 않는다.
     */
    private void cullHibernatedMobs(Set<Long> activeChunkKeys) {
        if (previousActiveMobIds.isEmpty()) return;
        List<Mob> culled = null;
        for (long id : previousActiveMobIds) {
            Mob mob = mobsById.get(id);
            Long chunk = mobChunkById.get(id);
            if (mob == null || chunk == null || activeChunkKeys.contains(chunk)
                    || MobSpawner.exemptFromNaturalDespawn(mob)) continue;
            if (culled == null) culled = new ArrayList<>();
            culled.add(mob);
        }
        if (culled != null) for (Mob mob : culled) removeMob(mob);
    }

    private List<ProjectileSim> cullHibernatedProjectiles(Set<Long> activeChunkKeys) {
        // Vanilla unloads the entity with its chunk; it does not convert an in-flight trident into
        // a recovery item merely because the last observing player walked away. The indexed live
        // projectile remains frozen and durable until its chunk becomes active again.
        return null;
    }

    private void rotateActiveEntityIds(List<Mob> ticked, List<Mob> spawned) {
        activeMobIdsScratch.clear();
        for (Mob mob : ticked) {
            if (mobsById.containsKey(mob.id)) activeMobIdsScratch.add(mob.id);
        }
        if (spawned != null) {
            for (Mob mob : spawned) {
                if (mobsById.containsKey(mob.id)) activeMobIdsScratch.add(mob.id);
            }
        }
        Set<Long> reusable = previousActiveMobIds;
        previousActiveMobIds = activeMobIdsScratch;
        activeMobIdsScratch = reusable;
    }

    /** 활성 청크 버킷으로 만든 틱 범위의 제한된 조회 결과를 반환한다. */
    public List<Mob> mobsInChunks(Set<Long> activeChunkKeys) {
        if (!mobChunkQueryDirty && cachedMobQueryChunks.equals(activeChunkKeys)) {
            return cachedMobsInChunks;
        }
        List<Mob> active = new ArrayList<>();
        for (long key : activeChunkKeys) {
            LinkedHashSet<Mob> bucket = mobsByChunk.get(key);
            if (bucket != null) active.addAll(bucket);
        }
        active.sort(Comparator.comparingLong(mob -> mob.id));
        cachedMobQueryChunks = Set.copyOf(activeChunkKeys);
        cachedMobsInChunks = active.isEmpty() ? List.of() : List.copyOf(active);
        mobChunkQueryDirty = false;
        return cachedMobsInChunks;
    }

    /**
     * Exact ID-ordered subset that can ever pick up equipment. The active query list is already
     * generation-cached, so ordinary villagers/farm animals never re-enter each equipment-item scan.
     */
    public List<Mob> equipmentMobsInChunks(Set<Long> activeChunkKeys) {
        List<Mob> active = mobsInChunks(activeChunkKeys);
        if (active == cachedEquipmentMobSource) return cachedEquipmentMobsInChunks;
        List<Mob> candidates = new ArrayList<>();
        for (Mob mob : active) {
            if (mob.canEverPickupEquipment()) candidates.add(mob);
        }
        cachedEquipmentMobSource = active;
        cachedEquipmentMobsInChunks = candidates.isEmpty() ? List.of() : List.copyOf(candidates);
        return cachedEquipmentMobsInChunks;
    }

    /** 활성 청크 버킷으로 만든 틱 범위의 제한된 투사체 조회 결과를 반환한다. */
    public List<ProjectileSim> arrowsInChunks(Set<Long> activeChunkKeys) {
        if (!arrowChunkQueryDirty && cachedArrowQueryChunks.equals(activeChunkKeys)) {
            return cachedArrowsInChunks;
        }
        List<ProjectileSim> active = new ArrayList<>();
        for (long key : activeChunkKeys) {
            LinkedHashSet<ProjectileSim> bucket = arrowsByChunk.get(key);
            if (bucket != null) active.addAll(bucket);
        }
        for(ProjectileSim pearl:arrows) if(pearl.kind==ProjectileSim.Kind.ENDER_PEARL && !active.contains(pearl)) active.add(pearl);
        active.sort(Comparator.comparingLong(arrow -> arrow.id));
        cachedArrowQueryChunks = Set.copyOf(activeChunkKeys);
        cachedArrowsInChunks = active.isEmpty() ? List.of() : List.copyOf(active);
        arrowChunkQueryDirty = false;
        return cachedArrowsInChunks;
    }

    private void invalidateChunkQueries() {
        mobChunkQueryDirty = true;
        arrowChunkQueryDirty = true;
    }

    private void indexMob(Mob mob) {
        requireValidMobId(mob.id);
        invalidateChunkQueries();
        long key = entityChunkKey(mob.x, mob.z);
        mobChunkById.put(mob.id, key);
        mobsById.put(mob.id, mob);
        mob.installVehicleBindingListener(this);
        if (mob.vehicleMobId() != 0) mobPassengerIndexDirty = true;
        mobsByChunk.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(mob);
        if (mob instanceof Bee bee && bee.inHive()) indexBeeHiveResident(bee);
        refreshPendingAnimalDependencyRequests(mob);
    }

    private void reindexMob(Mob mob) {
        long next = entityChunkKey(mob.x, mob.z);
        Long previous = mobChunkById.get(mob.id);
        if (previous != null && previous == next) return;
        invalidateChunkQueries();
        if (previous != null) {
            LinkedHashSet<Mob> bucket = mobsByChunk.get(previous);
            if (bucket != null) {
                bucket.remove(mob);
                if (bucket.isEmpty()) mobsByChunk.remove(previous);
            }
        }
        mobChunkById.put(mob.id, next);
        mobsByChunk.computeIfAbsent(next, ignored -> new LinkedHashSet<>()).add(mob);
        Long unannouncedPrevious = externallySpawnedChunkById.get(mob.id);
        if (unannouncedPrevious != null && unannouncedPrevious != next) {
            LinkedHashSet<Mob> unannounced = externallySpawnedByChunk.get(unannouncedPrevious);
            if (unannounced != null) {
                unannounced.remove(mob);
                if (unannounced.isEmpty()) externallySpawnedByChunk.remove(unannouncedPrevious);
            }
            externallySpawnedByChunk.computeIfAbsent(next, ignored -> new LinkedHashSet<>()).add(mob);
            externallySpawnedChunkById.put(mob.id, next);
        }
    }

    private void removeMob(Mob mob) {
        invalidateChunkQueries();
        // [RAID-OMEN] Raider#die: 플레이어가 죽인 레이더는 그 플레이어를 마을의 영웅으로 올린다. 죽은 몹은 원장
        // 대조 전에 이미 치워지므로 제거 경계에서 기록한다.
        if (mob.isDead() && mob.raidId() != 0L) raidLedger.addHero(mob.id, mob.recentPlayerKillCredit());
        if (mob instanceof Bee bee) unindexBeeHiveResident(bee);
        ensureMobPassengerIndex();
        Mob passenger = mobPassengerByVehicleId.remove(mob.id);
        if (passenger != null && passenger != mob) {
            mobPassengersById.remove(passenger.id);
            clearMobVehicleWithoutNotification(passenger);
            snapshotMob(passenger);
        }
        Mob removedPassenger = mobPassengersById.remove(mob.id);
        if (removedPassenger != null
                && mobPassengerByVehicleId.get(mob.vehicleMobId()) == mob) {
            mobPassengerByVehicleId.remove(mob.vehicleMobId());
        }
        mob.installVehicleBindingListener(null);
        if (mob.type == MobType.VILLAGER) villagerSociety.forget(mob.id);
        mobs.remove(mob);
        Long key = mobChunkById.remove(mob.id);
        mobsById.remove(mob.id);
        removePendingAnimalDependencyRequests(mob.id);
        pendingPersistenceMobs.remove(mob.id);
        persistentMobsById.remove(mob.id);
        if (finalCarrierFactsById.remove(mob.id) != null) {
            structureEntityDeathListener.onPermanentDeath(mob.id);
        }
        if (ephemeralQaMobIds != null) ephemeralQaMobIds.remove(mob.id);
        leashHolderByMobId.remove(mob.id);
        splitSlimeParents.remove(mob.id);
        Long announcedChunk = externallySpawnedChunkById.remove(mob.id);
        LinkedHashSet<Mob> unannounced = announcedChunk == null ? null
                : externallySpawnedByChunk.get(announcedChunk);
        if (unannounced != null) {
            unannounced.remove(mob);
            if (unannounced.isEmpty()) externallySpawnedByChunk.remove(announcedChunk);
        }
        if (key == null) return;
        LinkedHashSet<Mob> bucket = mobsByChunk.get(key);
        if (bucket != null) {
            bucket.remove(mob);
            if (bucket.isEmpty()) mobsByChunk.remove(key);
        }
    }

    /** Removes a death resolved after the main AI pass, such as Ravager roar damage. */
    public void removeResolvedDeadMob(Mob mob) {
        if (mob == null || !mob.isDead()) {
            throw new IllegalArgumentException("only a resolved dead mob can be removed");
        }
        if (mobsById.get(mob.id) == mob) removeMob(mob);
    }

    /**
     * Exact equipped mount-body items returned at the permanent death boundary. Hibernation never
     * calls this method and an unarmored/non-dead entity returns an empty immutable list. The caller
     * owns exactly-once death settlement; this method deliberately does not mutate persisted identity.
     */
    public static List<Short> permanentMountArmorDrops(Mob mob) {
        if (mob == null || !mob.isDead()) return List.of();
        short horseArmor = mob.horseArmorItem();
        int nautilusArmor = mob.nautilusArmorItemId();
        if (horseArmor != 0 && nautilusArmor != 0) {
            throw new IllegalStateException("one mob cannot carry Horse and Nautilus body armor");
        }
        if (horseArmor != 0) return List.of(horseArmor);
        return nautilusArmor == 0 ? List.of() : List.of((short) nautilusArmor);
    }

    /** Exact non-random stack identity owned by a mount at its permanent death boundary. */
    public record PermanentDeathStack(short itemType, int count, int durability,
            long enchantments, int mapId, int shulkerId,
            String bucketMobData, String itemComponentData) {
        public PermanentDeathStack(short itemType) {
            this(itemType, 1, PlayerInventory.initialDurability(itemType), 0, 0, 0, null, null);
        }
    }

    /**
     * Claims every persisted mount/lead stack exactly once. Chunk hibernation cannot call this
     * because the entity must already be dead; manual detach/removal is naturally reflected by the
     * current authoritative slots and lead ledger.
     */
    public List<PermanentDeathStack> claimPermanentDeathSettlement(Mob mob) {
        if (mob == null || !mob.isDead() || mobsById.get(mob.id) != mob
                || !permanentDeathSettledMobIds.add(mob.id)) return List.of();
        return permanentDeathSettlement(mob);
    }

    /** Plans exact death-owned stacks without consuming the retry token. */
    public List<PermanentDeathStack> planPermanentDeathSettlement(Mob mob) {
        if (mob == null || !mob.isDead() || mobsById.get(mob.id) != mob
                || permanentDeathSettledMobIds.contains(mob.id)) return List.of();
        return permanentDeathSettlement(mob);
    }

    /** Consumes the runtime token only after the durable receipt and its effects commit. */
    public void confirmPermanentDeathSettlement(long mobId) {
        permanentDeathSettledMobIds.add(mobId);
        Mob mob = mobsById.get(mobId);
        if (mob != null && mob.isDead()) removeMob(mob);
    }

    private List<PermanentDeathStack> permanentDeathSettlement(Mob mob) {
        List<PermanentDeathStack> drops = new ArrayList<>();
        if (leashHolderByMobId.containsKey(mob.id)) {
            drops.add(new PermanentDeathStack(PlayerInventory.LEAD));
        }
        if (mob.pigSaddled() || mob.nautilusSaddled()) {
            drops.add(new PermanentDeathStack(PlayerInventory.SADDLE));
        }
        if (mob.happyGhastHarnessed()) {
            drops.add(new PermanentDeathStack((short) (Short.toUnsignedInt(
                    PlayerInventory.WHITE_HARNESS) + mob.happyGhastHarnessColor())));
        }
        com.gameexpert.engine.ChestInventory equipment = mob.horseEquipment();
        if (equipment != null) {
            for (int slot = 0; slot < 2; slot++) {
                if (equipment.itemType(slot) == PlayerInventory.EMPTY) continue;
                drops.add(new PermanentDeathStack(equipment.itemType(slot),
                        equipment.count(slot), equipment.durability(slot),
                        equipment.enchantments(slot), equipment.mapId(slot),
                        equipment.shulkerId(slot), equipment.bucketMobData(slot),
                        equipment.itemComponentData(slot)));
            }
        }
        int nautilusArmor = mob.nautilusArmorItemId();
        if (nautilusArmor != 0) drops.add(new PermanentDeathStack((short) nautilusArmor));
        if (mob.wolfArmorDurability() > 0) {
            drops.add(new PermanentDeathStack(PlayerInventory.WOLF_ARMOR, 1,
                    mob.wolfArmorDurability(), 0, 0, 0, null, null));
        }
        if (mob.horseChested()) drops.add(new PermanentDeathStack((short) Blocks.CHEST));
        // [EC-MOBS] 부서진 아이템 액자의 넣은 아이템(인챈트·성분 그대로, 바닐라 dropItem 의 getItem().copy()).
        if (mob instanceof ItemFrame frame && frame.hasItem()) {
            drops.add(new PermanentDeathStack(frame.heldItem(), 1, frame.heldItemDurability(),
                    frame.itemEnchantments(), 0, 0, null, frame.itemComponents()));
        }
        com.gameexpert.engine.ChestInventory cargo = mob.horseCargo();
        if (cargo != null) {
            com.gameexpert.engine.ChestInventory.Snapshot snapshot = cargo.snapshot();
            for (int slot = 0; slot < snapshot.itemTypes().length; slot++) {
                short itemType = snapshot.itemTypes()[slot];
                int count = snapshot.counts()[slot];
                if (itemType == PlayerInventory.EMPTY || count <= 0) continue;
                drops.add(new PermanentDeathStack(itemType, count,
                        snapshot.durabilities()[slot], snapshot.enchantments()[slot],
                        snapshot.mapIds()[slot], snapshot.shulkerIds()[slot],
                        snapshot.bucketMobData()[slot], snapshot.itemComponentData()[slot]));
            }
        }
        return List.copyOf(drops);
    }

    private void indexArrow(ProjectileSim arrow) {
        projectileRevision++;
        invalidateChunkQueries();
        long key = entityChunkKey(arrow.x, arrow.z);
        arrowChunkById.put(arrow.id, key);
        arrowsById.put(arrow.id, arrow);
        arrowsByChunk.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(arrow);
    }

    private void reindexArrow(ProjectileSim arrow) {
        long next = entityChunkKey(arrow.x, arrow.z);
        Long previous = arrowChunkById.get(arrow.id);
        if (previous != null && previous == next) return;
        invalidateChunkQueries();
        if (previous != null) {
            LinkedHashSet<ProjectileSim> bucket = arrowsByChunk.get(previous);
            if (bucket != null) {
                bucket.remove(arrow);
                if (bucket.isEmpty()) arrowsByChunk.remove(previous);
            }
        }
        arrowChunkById.put(arrow.id, next);
        arrowsByChunk.computeIfAbsent(next, ignored -> new LinkedHashSet<>()).add(arrow);
    }

    private void removeArrow(ProjectileSim arrow) {
        projectileRevision++;
        invalidateChunkQueries();
        arrows.remove(arrow);
        Long key = arrowChunkById.remove(arrow.id);
        arrowsById.remove(arrow.id);
        if (key == null) return;
        LinkedHashSet<ProjectileSim> bucket = arrowsByChunk.get(key);
        if (bucket != null) {
            bucket.remove(arrow);
            if (bucket.isEmpty()) arrowsByChunk.remove(key);
        }
    }

    /** Records a fishing-lane mutation which occurs after the core projectile physics pass. */
    public void markProjectileMutation(ProjectileSim projectile) {
        if (projectile == null || arrowsById.get(projectile.id) != projectile) return;
        projectileRevision++;
        reindexArrow(projectile);
    }

    private static long entityChunkKey(double x, double z) {
        int chunkX = Math.floorDiv((int) Math.floor(x), Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv((int) Math.floor(z), Blocks.CHUNK_Z);
        return ((long) chunkX << 32) ^ (chunkZ & 0xffff_ffffL);
    }

    private List<Mob> collectExternallySpawned(Set<Long> activeChunkKeys) {
        if (externallySpawnedByChunk.isEmpty()) return null;
        List<Mob> spawned = new ArrayList<>();
        if (activeChunkKeys == null) {
            for (long key : externallySpawnedByChunk.keySet()) {
                LinkedHashSet<Mob> bucket = externallySpawnedByChunk.get(key);
                if (bucket == null) continue;
                spawned.addAll(bucket);
                for (Mob mob : bucket) externallySpawnedChunkById.remove(mob.id);
            }
            externallySpawnedByChunk.clear();
        } else {
            for (long key : activeChunkKeys) {
                LinkedHashSet<Mob> bucket = externallySpawnedByChunk.remove(key);
                if (bucket == null) continue;
                spawned.addAll(bucket);
                for (Mob mob : bucket) externallySpawnedChunkById.remove(mob.id);
            }
        }
        spawned.sort(Comparator.comparingLong(mob -> mob.id));
        return spawned.isEmpty() ? null : spawned;
    }

    private void deferPersistenceSnapshot(Mob mob) {
        if (mob == null || mobsById.get(mob.id) != mob || mob.isDead() || mob.removed) return;
        if (ephemeralQaMobIds != null && ephemeralQaMobIds.contains(mob.id)) return;
        pendingPersistenceMobs.put(mob.id, mob);
    }

    private void materializePersistenceCandidates() {
        if (pendingPersistenceMobs.size() == 0) return;
        List<Mob> candidates = new ArrayList<>(pendingPersistenceMobs.size());
        pendingPersistenceMobs.forEachValue(candidates::add);
        candidates.sort(Comparator.comparingLong(mob -> mob.id));
        Map<Long, MobPersistenceSnapshot> updates = new TreeMap<>();
        Set<Long> removals = new HashSet<>();
        for (Mob candidate : candidates) {
            Mob live = mobsById.get(candidate.id);
            if (live != candidate || candidate.isDead() || candidate.removed) {
                removals.add(candidate.id);
                continue;
            }
            updates.put(candidate.id, snapshotOf(candidate, leashHolderByMobId.get(candidate.id)));
        }
        pendingPersistenceMobs.clear();
        for (Long mobId : removals) persistentMobsById.remove(mobId);
        for (Map.Entry<Long, MobPersistenceSnapshot> update : updates.entrySet()) {
            MobPersistenceSnapshot previous = persistentMobsById.get(update.getKey());
            if (!update.getValue().equals(previous)) {
                persistentMobsById.put(update.getKey(), update.getValue());
            }
        }
    }

    private void snapshotMob(Mob mob) {
        if (ephemeralQaMobIds != null && ephemeralQaMobIds.contains(mob.id)) {
            pendingPersistenceMobs.remove(mob.id);
            persistentMobsById.remove(mob.id);
            return;
        }
        MobPersistenceSnapshot snapshot = snapshotOf(mob, leashHolderByMobId.get(mob.id));
        pendingPersistenceMobs.remove(mob.id);
        persistentMobsById.put(mob.id, snapshot);
    }

    private MobPersistenceSnapshot snapshotOf(Mob mob, String leashHolderNickname) {
        requireValidMobId(mob.id);
        persistenceSnapshotConstructionCount++;
        HorseMenuCodec.EncodedMenu horseMenuCapture = HorseMenuCodec.capture(mob);
        boolean drownedCarrierRolled = false;
        boolean drownedTridentCarrier = false;
        boolean drownedShellCarrier = false;
        if (mob instanceof Drowned drowned) {
            drownedCarrierRolled = drowned.carrierRolled();
            drownedTridentCarrier = drowned.tridentCarrier();
            drownedShellCarrier = drowned.shellCarrier();
        }
        boolean drownedJockeyDecisionArmed = mob instanceof Drowned drowned
                && drowned.jockeyDecisionArmed();
        boolean drownedJockeyDecisionSettled = !(mob instanceof Drowned drowned)
                || drowned.jockeyDecisionSettled();
        boolean drownedJockeyDecisionWinner = mob instanceof Drowned drowned
                && drowned.jockeyDecisionWinner();
        boolean skeletonHorseTrapActive = mob instanceof SkeletonHorse skeletonHorse
                && skeletonHorse.trapActive();
        int skeletonHorseTrapAgeMcTicks = mob instanceof SkeletonHorse skeletonHorse
                ? skeletonHorse.trapAgeMcTicks() : 0;
        int copperGolemOxidationAge = mob instanceof CopperGolem copperGolem
                ? copperGolem.oxidationAge() : 0;
        boolean copperGolemWaxed = mob instanceof CopperGolem copperGolem
                && copperGolem.waxed();
        int copperGolemPose = mob instanceof CopperGolem copperGolem ? copperGolem.pose() : 0;
        long copperGolemNextWeatheringMcTick = mob instanceof CopperGolem copperGolem
                ? copperGolem.nextWeatheringMcTick() : CopperGolem.WEATHERING_UNSET;
        boolean copperGolemStatuePending = mob instanceof CopperGolem copperGolem
                && copperGolem.statuePending();
        PoisonDartFrog poisonFrog = mob instanceof PoisonDartFrog frog ? frog : null;
        boolean beeHasStung = false;
        int beeDeathAfterStingTicks = 0;
        boolean beeHasNectar = false;
        double beeHomeX = 0.0;
        double beeHomeY = 0.0;
        double beeHomeZ = 0.0;
        if (mob instanceof Bee bee) {
            beeHasStung = bee.hasStung();
            beeDeathAfterStingTicks = bee.deathAfterStingTicks();
            beeHasNectar = bee.hasNectar();
            beeHomeX = bee.homeX();
            beeHomeY = bee.homeY();
            beeHomeZ = bee.homeZ();
        }
        long vexSummonerMobId = 0;
        int vexLimitedLifeTicks = 0;
        if (mob instanceof Vex vex) {
            vexSummonerMobId = vex.summonerMobId();
            vexLimitedLifeTicks = vex.limitedLifeTicks();
        }
        String angerTarget = null;
        int angerTicks = 0;
        if (mob instanceof Piglin piglin) {
            angerTarget = piglin.angerTarget();
            angerTicks = piglin.angerTicksRemaining();
        } else if (mob instanceof ZombifiedPiglin piglin) {
            angerTarget = piglin.angerTarget();
            angerTicks = piglin.angerMcTicksRemaining();
        } else if (mob instanceof Pigman pigman) {
            angerTarget = pigman.angerTarget();
            angerTicks = pigman.angerMcTicksRemaining();
        } else if (mob instanceof ZombiePigman pigman) {
            angerTarget = pigman.angerTarget();
            angerTicks = pigman.angerTicksRemaining();
        } else if (mob instanceof NautilusFamilyMob nautilus) {
            angerTarget = nautilus.angerTarget();
            angerTicks = nautilus.angerMcTicksRemaining();
        }
        int ravagerAttackTicks = 0;
        int ravagerStunnedTicks = 0;
        int ravagerRoarTicks = 0;
        if (mob instanceof Ravager ravager) {
            ravagerAttackTicks = ravager.attackMcTicks();
            ravagerStunnedTicks = ravager.stunnedMcTicks();
            ravagerRoarTicks = ravager.roarMcTicks();
        }
        String armadilloShellState = null;
        int armadilloStateTicks = 0;
        int armadilloDangerTicks = 0;
        int armadilloScuteTicks = 0;
        if (mob instanceof Armadillo armadillo) {
            armadilloShellState = armadillo.shellState().name();
            armadilloStateTicks = armadillo.stateMcTicks();
            armadilloDangerTicks = armadillo.dangerMcTicks();
            armadilloScuteTicks = armadillo.scuteMcTicks();
        }
        int dolphinMoistureTicks = 0;
        int dolphinDryDamageTicks = 0;
        boolean dolphinGotFish = false;
        int dolphinTreasureX = 0;
        int dolphinTreasureY = 0;
        int dolphinTreasureZ = 0;
        if (mob instanceof Dolphin dolphin) {
            dolphinMoistureTicks = dolphin.moistureMcTicks();
            dolphinDryDamageTicks = dolphin.dryDamageCooldownMcTicks();
            dolphinGotFish = dolphin.gotFish();
            dolphinTreasureX = dolphin.treasureX();
            dolphinTreasureY = dolphin.treasureY();
            dolphinTreasureZ = dolphin.treasureZ();
        }
        String illagerContext = mob.illagerContext() == null
                ? null : mob.illagerContext().name();
        long illagerContextIdentity = mob.illagerContextIdentity();
        int illagerPolicyVersion = mob.illagerPolicyVersion();
        long companionHandlerMobId = 0;
        long companionAppearanceLane = 0;
        int companionRebindCount = 0;
        String companionAbilityPhase = "IDLE";
        int companionAbilityTicks = 0;
        int companionCooldownTicks = 0;
        String companionTargetNickname = null;
        int companionMarkTicks = 0;
        double companionChargeDistance = 0.0;
        int companionOrphanTicks = 0;
        if (mob instanceof IllagerCompanionMob companion) {
            companionHandlerMobId = companion.handlerMobId();
            companionAppearanceLane = companion.appearanceLane();
            companionRebindCount = companion.rebindCount();
            IllagerCompanionMob.CompanionEncounterState encounter = companion.encounterState();
            companionAbilityPhase = encounter.abilityPhase();
            companionAbilityTicks = encounter.abilityTicks();
            companionCooldownTicks = encounter.cooldownTicks();
            companionTargetNickname = encounter.targetNickname();
            companionMarkTicks = encounter.markTicks();
            companionChargeDistance = encounter.chargeDistance();
            companionOrphanTicks = encounter.orphanTicks();
        }
        MobPersistenceSnapshot result = new MobPersistenceSnapshot(mob.id, mob.type.name(), mob.variant(),
                mob.x, mob.y, mob.z,
                mob.exactHealth(), mob.hasPickedUpEquipment(),
                mob.heldItem(), mob.heldItemDurability(),
                mob.equippedItem(ArmorSlot.HELMET), mob.equippedItemDurability(ArmorSlot.HELMET),
                mob.equippedItem(ArmorSlot.CHESTPLATE),
                mob.equippedItemDurability(ArmorSlot.CHESTPLATE),
                mob.equippedItem(ArmorSlot.LEGGINGS),
                mob.equippedItemDurability(ArmorSlot.LEGGINGS),
                mob.equippedItem(ArmorSlot.BOOTS), mob.equippedItemDurability(ArmorSlot.BOOTS),
                (short) mob.carriedBlockId(),
                mob.ownerNickname(), mob.customName(),
                mob.ageTicksRemaining(), mob.breedingCooldownTicks(), mob.babyForm(),
                mob.airSupplyTicks(), mob.underwaterConversionTicks(),
                drownedCarrierRolled, drownedTridentCarrier,
                beeHasStung, beeDeathAfterStingTicks, beeHasNectar,
                beeHomeX, beeHomeY, beeHomeZ,
                mob.persistenceRequired(), mob.slimeSize(), mob.loveTicksRemaining(),
                vexSummonerMobId, vexLimitedLifeTicks,
                angerTarget, angerTicks,
                ravagerAttackTicks, ravagerStunnedTicks, ravagerRoarTicks,
                armadilloShellState, armadilloStateTicks, armadilloDangerTicks,
                armadilloScuteTicks,
                dolphinMoistureTicks, dolphinDryDamageTicks, dolphinGotFish,
                dolphinTreasureX, dolphinTreasureY, dolphinTreasureZ,
                illagerContext, illagerContextIdentity, illagerPolicyVersion,
                companionHandlerMobId, companionAppearanceLane, companionRebindCount,
                companionAbilityPhase, companionAbilityTicks, companionCooldownTicks,
                companionTargetNickname, companionMarkTicks, companionChargeDistance,
                companionOrphanTicks, mob.ocelotTrusting(), mob.wolfSitting(),
                mob.wolfCollarColor(), mob.wolfArmorDurability(), mob.tadpoleAgeMcTicks(),
                mob.zombieNautilusChargePhase(),
                mob.zombieNautilusChargeCooldownMcTicks(),
                mob.zombieNautilusChargeTargetNickname(),
                mob.zombieNautilusChargeTargetMobId(),
                mob.zombieNautilusChargeVx(), mob.zombieNautilusChargeVy(),
                mob.zombieNautilusChargeVz(), mob.zombieNautilusChargeDistance(),
                mob.zombieNautilusNaturalTargetCooldownMcTicks(),
                mob.raidId(), mob.raidWave())
                .withCompanionState(mob.companionSitting(), mob.catCollarColor())
                .withBeeHiveTicks(mob instanceof Bee bee ? bee.hiveTicks() : 0)
                .withPufferPuffStage(
                        mob instanceof Pufferfish pufferfish ? pufferfish.puffStageId() : 0)
                .withMooshroomStoredFlower(MooshroomRules.storedFlower(mob))
                .withIllusionerAndCompanionDecision(
                        mob.illusionerInvisibilityMcTicks(),
                        mob.illusionerMirrorCooldownMcTicks(),
                        mob.illusionerBlindnessCooldownMcTicks(),
                        mob.illusionerCastMcTicks(),
                        mob.illusionerBlindTargetKey(),
                        mob.companionDecisionSettled(),
                        mob.companionDecisionWinner())
                .withZombieVillagerConversion(
                        mob.zombieVillagerConversionMcTicks(),
                        mob.zombieVillagerConversionStarter())
                .withVillagerSocial(
                        mob instanceof Villager villager ? villager.encodeSocialState() : null)
                .withFarmAnimalState(
                        mob.sheepColor(), mob.sheepSheared(), mob.sheepEatMcTicks(),
                        mob.chickenEggMcTicks(),
                        mob.pigSaddled(), mob.pigBoostMcTicks(), mob.pigBoostTotalMcTicks())
                .withCreeperPowered(mob.creeperPowered())
                .withCreeperIgnited(mob.creeperIgnited())
                .withTrialEquipmentNoDrop(mob.trialEquipmentNoDrop())
                .withStatusEffects(mob.statusEffects().persistenceSnapshot())
                .withEquipmentComponents(mob.encodeEquipmentComponents())
                .withNautilusMountState(mob.nautilusSaddled(), mob.nautilusArmorTier())
                .withDrownedShellCarrier(drownedShellCarrier)
                .withPoisonDartFrogState(
                        poisonFrog == null ? 0 : poisonFrog.feedCooldownMcTicks(),
                        poisonFrog == null ? 0 : poisonFrog.defensiveTicks(),
                        poisonFrog == null ? 0 : poisonFrog.contactCooldownTicks(),
                        poisonFrog == null ? 0 : poisonFrog.feedSequence(),
                        poisonFrog == null ? 0 : poisonFrog.pendingFeedSequence(),
                        poisonFrog == null ? 0 : poisonFrog.pendingFeedX(),
                        poisonFrog == null ? 0 : poisonFrog.pendingFeedY(),
                        poisonFrog == null ? 0 : poisonFrog.pendingFeedZ())
                .withDrownedJockeyState(
                        drownedJockeyDecisionArmed, drownedJockeyDecisionSettled,
                        drownedJockeyDecisionWinner,
                        mob instanceof Drowned ? mob.vehicleMobId() : 0L)
                .withSkeletonTrapRiderVehicle(
                        mob.type == MobType.SKELETON ? mob.vehicleMobId() : 0L)
                .withSkeletonHorseTrapState(
                        skeletonHorseTrapActive, skeletonHorseTrapAgeMcTicks)
                .withCopperGolemState(
                        copperGolemOxidationAge, copperGolemWaxed, copperGolemPose,
                        copperGolemNextWeatheringMcTick, copperGolemStatuePending)
                .withHorseState(horseStateType(mob.type) && mob.horseTamed(),
                        horseStateType(mob.type) ? mob.horseTemper() : 0,
                        horseStateType(mob.type) && mob.horseSaddled(),
                        horseStateType(mob.type) ? mob.horseMaxHealthStat() : 0.0,
                        horseStateType(mob.type) ? mob.horseSpeedStat() : 0.0,
                        horseStateType(mob.type) ? mob.horseJumpStrengthStat() : 0.0)
                .withHorseArmorItem(mob.horseArmorItem())
                .withGoatHornMask(mob.type == MobType.GOAT ? mob.goatHornMask() : null)
                .withGoatRamState(mob.goatRamPhase().name(), mob.goatRamCooldownMcTicks(),
                        mob.goatRamTargetNickname(), mob.goatRamTargetX(), mob.goatRamTargetZ(),
                        mob.goatRamRunUpX(), mob.goatRamRunUpZ(),
                        mob.goatRamDirectionX(), mob.goatRamDirectionZ(),
                        mob.goatRamPrepareMcTicks(), mob.goatRamDistance(),
                        mob.goatRamSequence(),
                        mob.pendingGoatRamImpact() == null ? 0
                                : mob.pendingGoatRamImpact().blockId(),
                        mob.pendingGoatRamImpact() == null ? 0 : mob.pendingGoatRamImpact().x(),
                        mob.pendingGoatRamImpact() == null ? 0 : mob.pendingGoatRamImpact().y(),
                        mob.pendingGoatRamImpact() == null ? 0 : mob.pendingGoatRamImpact().z(),
                        mob.pendingGoatRamImpact() != null
                                && mob.pendingGoatRamImpact().preferLeft())
                .withSnifferDigState(mob.snifferDigPhase().name(),
                        mob.snifferDigCooldownMcTicks(), mob.snifferDigPhaseMcTicks(),
                        mob.snifferDigDropDelayMcTicks(), mob.snifferDigTargetX(),
                        mob.snifferDigTargetY(), mob.snifferDigTargetZ(),
                        mob.snifferDigSearchEpoch(), mob.snifferDigSequence(),
                        mob.snifferPendingDropSequence(), mob.snifferPendingDropItem())
                .withLeashHolderNickname(leashHolderNickname)
                .withHorseEquipment(mob.horseChested(),
                        mob.llamaStrength(), mob.llamaCarpetColor())
                .withCamelState(mob.camelSaddled(), mob.camelSitting())
                .withHorseInventoryDataAndRevisions(horseMenuCapture.payload(),
                        horseMenuCapture.sharedRevision(), horseMenuCapture.equipmentRevision(),
                        horseMenuCapture.cargoRevision())
                .withHappyGhastState(
                        mob.happyGhastHarnessed(), mob.happyGhastHarnessColor())
                .withAllayDelivery(
                        mob instanceof Allay allay ? allay.deliveryCount() : 0,
                        mob instanceof Allay allay ? allay.deliveryDurability() : 0)
                .withTurtleEggState(
                        mob instanceof Turtle turtle && turtle.gravid(),
                        mob instanceof Turtle turtle ? turtle.homeX() : 0,
                        mob instanceof Turtle turtle ? turtle.homeY() : 0,
                        mob instanceof Turtle turtle ? turtle.homeZ() : 0,
                        mob.turtleTravelingHome(), mob.turtleEggDigMcTicks(),
                        mob.turtleEggCount())
                .withSulfurCubeState(
                        mob instanceof SulfurCube cube ? cube.pickupCooldownTicks() : 0,
                        mob instanceof SulfurCube cube ? cube.fuseTicks() : -1,
                        mob instanceof SulfurCube cube ? cube.maxFuseTicks() : -1,
                        mob instanceof SulfurCube cube && cube.fromBucket())
                .withAttachedState(
                        mob instanceof HangingMaw maw ? maw.attachFace() : mob instanceof Shulker shulker ? shulker.attachFace()
                                : mob instanceof ItemFrame frame ? frame.direction() : 0,
                        mob instanceof Shulker shulker ? shulker.rawPeek()
                                : mob instanceof ItemFrame frame ? frame.rotation() : 0,
                        mob instanceof ItemFrame frame ? frame.itemEnchantments() : 0L,
                        mob instanceof ItemFrame frame ? frame.itemComponents() : null);
        MobPersistenceSnapshot carrier = finalCarrierFactsById.get(mob.id);
        return carrier == null ? result : result.withFinalCarrierFacts(
                carrier.getVariant(), carrier.getYaw(), carrier.getPitch(),
                carrier.getVelocityX(), carrier.getVelocityY(), carrier.getVelocityZ(),
                carrier.getVillagerBiomeType(), carrier.getVillagerProfession(),
                carrier.getVillagerLevel());
    }

    /**
     * 낙뢰 변신({@code Villager#thunderHit} → 마녀, {@code Pig#thunderHit} → 좀비화 피글린).
     * 몹 틱 밖(날씨 권위)에서 호출되며, 원본은 {@code removed} 로 닫고 새 개체를 연다.
     * 호출부가 {@code converted} 디스폰 사유와 브로드캐스트를 책임진다.
     *
     * <p>바닐라 {@code Villager#thunderHit} 은 새 마녀에 {@code setPersistenceRequired()} 를 건다.
     */
    public Mob convertByLightning(Mob source, MobType targetType) {
        Mob converted = convertMob(source, targetType);
        if (targetType == MobType.WITCH) converted.setPersistenceRequired(true);
        source.removed = true;
        return converted;
    }

    public record MooshroomShearPlan(Mob source, int mushroomBlock, int mushroomCount) { }

    public static MooshroomShearPlan planShearMooshroom(Mob source) {
        if (source == null || source.type != MobType.MOOSHROOM || source.isDead()
                || source.removed || source.isBaby()) return null;
        int mushroom = "brown".equals(source.variant()) ? Blocks.MUSHROOM_BROWN : Blocks.MUSHROOM_RED;
        return new MooshroomShearPlan(source, mushroom, 5);
    }

    /** Converts the exact planned Mooshroom into a Cow while preserving current health and age. */
    public Mob confirmShearMooshroom(MooshroomShearPlan plan) {
        if (plan == null || plan.source().removed || plan.source().isDead()
                || plan.source().type != MobType.MOOSHROOM || plan.source().isBaby()) return null;
        Mob source = plan.source();
        Mob converted = convertMob(source, MobType.COW);
        converted.restoreBreedingState(source.ageTicksRemaining(), source.breedingCooldownTicks(),
                source.loveTicksRemaining());
        converted.restorePersistentCombatState(source.exactHealth(), source.hasPickedUpEquipment(),
                source.heldItem(), source.heldItemDurability(),
                source.equippedItem(com.gameexpert.engine.inventory.ArmorSlot.HELMET),
                source.equippedItemDurability(com.gameexpert.engine.inventory.ArmorSlot.HELMET),
                source.equippedItem(com.gameexpert.engine.inventory.ArmorSlot.CHESTPLATE),
                source.equippedItemDurability(com.gameexpert.engine.inventory.ArmorSlot.CHESTPLATE),
                source.equippedItem(com.gameexpert.engine.inventory.ArmorSlot.LEGGINGS),
                source.equippedItemDurability(com.gameexpert.engine.inventory.ArmorSlot.LEGGINGS),
                source.equippedItem(com.gameexpert.engine.inventory.ArmorSlot.BOOTS),
                source.equippedItemDurability(com.gameexpert.engine.inventory.ArmorSlot.BOOTS));
        source.removed = true;
        converted.setPersistenceRequired(true);
        snapshotMob(converted);
        return converted;
    }

    public record GoatRamImpactRequest(Mob goat, long sequence, int blockId,
            int x, int y, int z, boolean preferLeft) { }

    public record GoatHornDropPlan(GoatRamImpactRequest impact) {
        public Mob goat() { return impact.goat(); }
        public boolean preferLeft() { return impact.preferLeft(); }
    }

    public List<GoatRamImpactRequest> pendingGoatRamImpacts() {
        if (pendingGoatRamImpactListDirty) {
            cachedPendingGoatRamImpacts = pendingGoatRamImpactsById.isEmpty()
                    ? List.of() : List.copyOf(pendingGoatRamImpactsById.values());
            pendingGoatRamImpactListDirty = false;
        }
        return cachedPendingGoatRamImpacts;
    }

    public static GoatHornDropPlan planGoatHornDropAfterRam(GoatRamImpactRequest impact) {
        if (impact == null || impact.goat() == null || impact.goat().type != MobType.GOAT
                || impact.goat().isDead() || impact.goat().removed || impact.goat().isBaby()
                || impact.goat().goatHornMask() == 0
                || !GoatRamRules.snapsGoatHorn(impact.blockId())) return null;
        Goat.RamImpact pending = impact.goat().pendingGoatRamImpact();
        if (pending == null || pending.sequence() != impact.sequence()
                || pending.blockId() != impact.blockId()) return null;
        return new GoatHornDropPlan(impact);
    }

    public short confirmGoatHornDrop(GoatHornDropPlan plan) {
        if (plan == null) return 0;
        short item = plan.goat().dropGoatHornAfterCommittedRam(plan.preferLeft());
        if (item != 0 && plan.goat().consumeGoatRamImpact(plan.impact().sequence())) {
            refreshPendingAnimalDependencyRequests(plan.goat());
            refreshPersistenceSnapshot(plan.goat());
        } else {
            item = 0;
        }
        return item;
    }

    /** Acknowledges a committed non-snapping block impact without creating an item. */
    public boolean confirmGoatRamImpactWithoutHorn(GoatRamImpactRequest request) {
        if (request == null || !request.goat().consumeGoatRamImpact(request.sequence())) return false;
        refreshPendingAnimalDependencyRequests(request.goat());
        refreshPersistenceSnapshot(request.goat());
        return true;
    }

    public record SnifferDigDropRequest(Mob sniffer, long sequence, short itemType,
            double x, double y, double z) { }

    /** Retryable requests remain in the persisted Sniffer until central settlement confirms one. */
    public List<SnifferDigDropRequest> pendingSnifferDigDrops() {
        if (pendingSnifferDigDropListDirty) {
            cachedPendingSnifferDigDrops = pendingSnifferDigDropsById.isEmpty()
                    ? List.of() : List.copyOf(pendingSnifferDigDropsById.values());
            pendingSnifferDigDropListDirty = false;
        }
        return cachedPendingSnifferDigDrops;
    }

    public boolean confirmSnifferDigDrop(SnifferDigDropRequest request) {
        if (request == null || request.sniffer().type != MobType.SNIFFER
                || request.sniffer().isDead() || request.sniffer().removed
                || request.sniffer().snifferPendingDropSequence() != request.sequence()
                || request.sniffer().snifferPendingDropItem() != request.itemType()
                || !request.sniffer().confirmSnifferDigDrop(request.sequence())) return false;
        refreshPendingAnimalDependencyRequests(request.sniffer());
        refreshPersistenceSnapshot(request.sniffer());
        return true;
    }

    /**
     * Synchronizes the two sparse retry indexes at semantic mutation boundaries. Quiet mobs only
     * take primitive/type checks; request records and immutable query lists are allocated solely
     * when a pending request is first created or its exact retry facts change.
     */
    private void refreshPendingAnimalDependencyRequests(Mob mob) {
        if (mob.type == MobType.GOAT) {
            Goat.RamImpact impact = mob.pendingGoatRamImpact();
            GoatRamImpactRequest previous = pendingGoatRamImpactsById.get(mob.id);
            if (impact == null) {
                if (previous != null) {
                    pendingGoatRamImpactsById.remove(mob.id);
                    pendingGoatRamImpactListDirty = true;
                }
            } else if (previous == null
                    || previous.goat() != mob
                    || previous.sequence() != impact.sequence()
                    || previous.blockId() != impact.blockId()
                    || previous.x() != impact.x() || previous.y() != impact.y()
                    || previous.z() != impact.z()
                    || previous.preferLeft() != impact.preferLeft()) {
                pendingGoatRamImpactsById.put(mob.id, new GoatRamImpactRequest(
                        mob, impact.sequence(), impact.blockId(), impact.x(), impact.y(),
                        impact.z(), impact.preferLeft()));
                pendingGoatRamImpactListDirty = true;
            }
        }
        if (mob.type == MobType.SNIFFER) {
            long sequence = mob.snifferPendingDropSequence();
            SnifferDigDropRequest previous = pendingSnifferDigDropsById.get(mob.id);
            if (sequence == 0) {
                if (previous != null) {
                    pendingSnifferDigDropsById.remove(mob.id);
                    pendingSnifferDigDropListDirty = true;
                }
            } else {
                short itemType = mob.snifferPendingDropItem();
                double x = mob.snifferDigTargetX() + 0.5;
                double y = mob.snifferDigTargetY() + 1.0;
                double z = mob.snifferDigTargetZ() + 0.5;
                if (previous == null
                        || previous.sniffer() != mob || previous.sequence() != sequence
                        || previous.itemType() != itemType
                        || previous.x() != x || previous.y() != y || previous.z() != z) {
                    pendingSnifferDigDropsById.put(mob.id, new SnifferDigDropRequest(
                            mob, sequence, itemType, x, y, z));
                    pendingSnifferDigDropListDirty = true;
                }
            }
        }
    }

    private void removePendingAnimalDependencyRequests(long mobId) {
        if (pendingGoatRamImpactsById.remove(mobId) != null) {
            pendingGoatRamImpactListDirty = true;
        }
        if (pendingSnifferDigDropsById.remove(mobId) != null) {
            pendingSnifferDigDropListDirty = true;
        }
    }

    public record LeashPlan(Mob mob, String holderNickname) { }

    public static boolean leashable(MobType type) {
        return switch (type) {
            case COW, PIG, SHEEP, CHICKEN, RABBIT, SQUID, GLOW_SQUID, COD, SALMON,
                    AXOLOTL, IRON_GOLEM, TROPICAL_FISH, ARMADILLO, DOLPHIN, DONKEY,
                    FOX, FROG, GOAT, HORSE, LLAMA, MOOSHROOM, OCELOT, PANDA, PARROT,
                    POLAR_BEAR, PUFFERFISH, TURTLE, WOLF, MULE, TADPOLE, COPPER_GOLEM,
                    NAUTILUS, ZOMBIE_NAUTILUS, ALLAY, CAMEL, ZOMBIE_HORSE, BROWN_BEAR,
                    CAT, TRADER_LLAMA, SKELETON_HORSE, SNIFFER, SNOW_GOLEM, HAPPY_GHAST -> true;
            default -> false;
        };
    }

    public LeashPlan planAttachLeash(Mob mob, String holderNickname) {
        if (mob == null || mob.isDead() || mob.removed || !leashable(mob.type)
                || holderNickname == null || holderNickname.isBlank()
                || leashHolderByMobId.containsKey(mob.id)) return null;
        return new LeashPlan(mob, holderNickname);
    }

    public boolean confirmAttachLeash(LeashPlan plan) {
        if (plan == null || plan.mob().isDead() || plan.mob().removed
                || leashHolderByMobId.putIfAbsent(plan.mob().id, plan.holderNickname()) != null) {
            return false;
        }
        plan.mob().setPersistenceRequired(true);
        refreshPersistenceSnapshot(plan.mob());
        return true;
    }

    public String leashHolderNickname(long mobId) { return leashHolderByMobId.get(mobId); }

    public String detachLeash(long mobId, String expectedHolder) {
        String current = leashHolderByMobId.get(mobId);
        if (current == null || expectedHolder != null && !current.equals(expectedHolder)) return null;
        leashHolderByMobId.remove(mobId);
        Mob mob = mobsById.get(mobId);
        if (mob != null && !mob.isDead()) refreshPersistenceSnapshot(mob);
        return current;
    }

    /** Disconnect cleanup: detach every lead held by this player and return affected mob IDs. */
    public List<Long> detachLeashesHeldBy(String nickname) {
        if (nickname == null) return List.of();
        List<Long> detached = new ArrayList<>();
        for (Map.Entry<Long, String> entry : new ArrayList<>(leashHolderByMobId.entrySet())) {
            if (!nickname.equals(entry.getValue())) continue;
            leashHolderByMobId.remove(entry.getKey());
            Mob mob = mobsById.get(entry.getKey());
            if (mob != null && !mob.isDead()) refreshPersistenceSnapshot(mob);
            detached.add(entry.getKey());
        }
        return List.copyOf(detached);
    }

    private Mob convertMob(Mob source, MobType targetType) {
        return convertMob(source, targetType, null);
    }

    private Mob convertMob(Mob source, MobType targetType, String variant) {
        Mob converted = MobFactory.create(targetType, allocateMobId(), source.x, source.y, source.z,
                worldSeed, false, variant);
        converted.inheritConversionState(source);
        mobs.add(converted);
        indexMob(converted);
        MobPersistenceSnapshot inheritedCarrier = finalCarrierFactsById.get(source.id);
        if (inheritedCarrier != null
                && (targetType == MobType.VILLAGER || targetType == MobType.ZOMBIE_VILLAGER)) {
            MobPersistenceSnapshot convertedFacts = snapshotOf(converted, null)
                    .withFinalCarrierFacts(variant, inheritedCarrier.getYaw(),
                            inheritedCarrier.getPitch(), inheritedCarrier.getVelocityX(),
                            inheritedCarrier.getVelocityY(), inheritedCarrier.getVelocityZ(),
                            inheritedCarrier.getVillagerBiomeType(),
                            inheritedCarrier.getVillagerProfession(),
                            inheritedCarrier.getVillagerLevel());
            finalCarrierFactsById.put(converted.id, convertedFacts);
        }
        snapshotMob(converted);
        return converted;
    }

    static String frogVariantForBiome(int biome) {
        return switch (biome) {
            case 10, 11, 12, 26, 30, 50, 140, 178, 179, 180, 181, 183 -> "cold";
            case 2, 21, 23, 35, 36, 37, 38, 44, 163, 165, 168, 184 -> "warm";
            default -> "temperate";
        };
    }
}
