package com.gameexpert.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.gameexpert.ground.dto.GroundMutationCommand;
import com.gameexpert.ground.dto.GroundXpOrbSnapshot;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.ws.dto.WsMessages.XpOrbDto;
import com.gameexpert.ws.dto.WsMessages.XpOrbPos;
import com.gameexpert.ws.dto.WsMessages.XpOrbRemove;
import com.gameexpert.ws.dto.WsMessages.XpOrbSpawn;
import com.gameexpert.ws.dto.WsMessages.XpOrbUpdates;

/**
 * [SURV-X] 서버 권위 경험치 구슬 시스템(월드당 1개, 틱 스레드 전용). {@link ItemEntitySystem} 과 같은
 * 틱 단계에서 호출되며 스폰 정산 → 자석 흡인 → 물리 → 병합 → 근접 획득 → 수명 소멸 → 변경분 브로드캐스트
 * 순서로 동작한다.
 *
 * <p>드랍 아이템과 다른 점은 두 가지뿐이다: 내구/종류 대신 경험치 양만 들고 다니고, 반경
 * {@link XpRules#XP_ORB_MAGNET_RADIUS} 안의 가장 가까운 플레이어에게 끌려간다. 획득 사운드는
 * 새 키를 만들지 않고 기존 아이템 획득 사운드를 재사용하도록 클라이언트가 xpOrbRemove(pickup) 을 쓴다.
 */
final class XpOrbSystem {

    /** 아이템 드랍과 같은 중력·종단 속도를 쓴다. */
    static final double GRAVITY = ItemEntitySystem.GRAVITY;
    static final double TERMINAL = ItemEntitySystem.TERMINAL;
    /** XP 오브의 수명은 XpRules 가 단독으로 소유한다(10분). */
    static final int DESPAWN_AGE = XpRules.XP_ORB_DESPAWN_AGE;
    /** 스폰 직후 몇 틱은 줍지 못한다(아이템 드랍과 동일). */
    static final int PICKUP_MIN_AGE = ItemEntitySystem.PICKUP_MIN_AGE;
    /** 같은 자리의 구슬을 합칠 반경. 정적판과 같은 값을 쓰도록 {@link XpRules} 가 정본이다. */
    static final double MERGE_RANGE_SQUARED = XpRules.XP_ORB_MERGE_RADIUS_SQUARED;
    static final double MERGE_RANGE = XpRules.XP_ORB_MERGE_RADIUS;
    static final double MERGE_CELL = 1.0;
    static final int MERGE_GRID_THRESHOLD = 32;
    /** 자석 흡인 가속도(블록/틱²). 정적판과 같은 값을 쓰도록 {@link XpRules} 가 정본이다. */
    static final double MAGNET_PULL = XpRules.XP_ORB_MAGNET_PULL;
    /** 한 구슬이 담을 수 있는 경험치 상한(바닐라 큰 구슬 기준). */
    static final int MAX_ORB_AMOUNT = XpRules.XP_ORB_MAX_AMOUNT;
    private static final double POS_EPS = 1e-3;
    private static final double SERVER_TICKS_PER_SECOND = 10.0;

    private final WorldRuntime rt;
    private final Random rng;
    private final WorldRuntime.GroundEntityIdAllocator entityIds;

    private final List<XpOrb> orbs = new ArrayList<>();
    private final List<XpOrb> pending = new ArrayList<>();
    /** 실패한 스폰 계획은 소비한 값을 재생해 재시도 시 같은 RNG 상태를 관찰하게 한다. */
    private final ArrayDeque<Double> replayRandomDoubles = new ArrayDeque<>();
    private ExplosionXpPlan activeExplosionXpPlan;
    private final Set<Long> pendingPlayerSettlements = new HashSet<>();
    /**
     * Exact sub-orb furnace XP retained until later furnace publications make a whole orb. This
     * value is never rounded or discarded; the typed furnace publication API is its sole writer.
     */
    private int furnaceXpCarryMilli;
    /** Restore/publication is one lifecycle lane; a second hydration attempt is always stale. */
    private boolean furnaceXpCarryInitialized;
    /** Independent durable generation for the world-level fractional carry. */
    private long furnaceXpCarryRevision;
    private long persistedFurnaceXpCarryRevision;
    /** 닉네임별 흡수 유예(바닐라 {@code Player.takeXpDelay}). 0 이 되면 항목을 지운다. */
    private final Map<String, Integer> takeDelay = new HashMap<>();
    /** Set by identity/amount lifecycle changes; motion/age are checkpointed at a bounded cadence. */
    private long persistenceRevision;
    private long persistedRevision;

    // WS 스레드가 welcome 구성 시 읽는 현재 스냅샷(불변).
    private volatile List<XpOrbDto> welcomeSnapshot = List.of();

    XpOrbSystem(WorldRuntime rt) {
        this(rt, new Random(), rt == null
                ? new WorldRuntime.GroundEntityIdAllocator()
                : rt.groundEntityIdAllocator());
    }

    /** 테스트에서만 난수 시드를 고정하기 위한 패키지 생성자. */
    XpOrbSystem(WorldRuntime rt, Random rng) {
        this(rt, rng, rt == null
                ? new WorldRuntime.GroundEntityIdAllocator()
                : rt.groundEntityIdAllocator());
    }

    XpOrbSystem(WorldRuntime rt, WorldRuntime.GroundEntityIdAllocator entityIds) {
        this(rt, new Random(), entityIds);
    }

    XpOrbSystem(WorldRuntime rt, Random rng,
            WorldRuntime.GroundEntityIdAllocator entityIds) {
        if (entityIds == null) throw new IllegalArgumentException("ground entity allocator is required");
        if (rt != null && entityIds != rt.groundEntityIdAllocator()) {
            throw new IllegalArgumentException("world ground entity allocator cannot be bypassed");
        }
        this.rt = rt;
        this.rng = rng;
        this.entityIds = entityIds;
    }

    /** One declared XP output source in an atomic explosion plan. */
    record ExplosionXpOrbRequest(int amount, double x, double y, double z) {
        ExplosionXpOrbRequest {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("폭발 XP 위치가 올바르지 않습니다");
            }
        }
    }

    /** Exact fixed-point XP transferred out of one removed furnace. */
    record FurnaceXpMilli(int amount, double x, double y, double z) {
        FurnaceXpMilli {
            if (amount < 0) {
                throw new IllegalArgumentException("furnace XP milli must be nonnegative");
            }
            requireFiniteSourceCoordinates(x, y, z);
        }
    }

    /** Deterministic publication result; retainedMilli is always in [0, 1000). */
    record FurnaceXpPublication(int publishedWholeXp, int retainedMilli) {
        FurnaceXpPublication {
            if (publishedWholeXp < 0 || retainedMilli < 0
                    || retainedMilli >= XpRules.MILLI) {
                throw new IllegalArgumentException("invalid furnace XP publication result");
            }
        }
    }

    /** Immutable persistence payload for the exact sub-orb furnace XP carry. */
    record FurnaceXpCarrySnapshot(int amount, long revision) {
        FurnaceXpCarrySnapshot {
            if (amount < 0 || amount >= XpRules.MILLI) {
                throw new IllegalArgumentException("furnace XP carry must be in [0, 1000)");
            }
            if (revision < 0 || revision == Long.MAX_VALUE) {
                throw new IllegalArgumentException("invalid furnace XP carry revision");
            }
        }
    }

    boolean usesAllocator(WorldRuntime.GroundEntityIdAllocator allocator) {
        return entityIds == allocator;
    }

    boolean explosionPlanningAvailable() {
        return activeExplosionXpPlan == null;
    }

    boolean explosionPlanIsActive(ExplosionXpPlan plan) {
        return activeExplosionXpPlan == plan;
    }

    List<XpOrbDto> welcomeSnapshot() {
        return welcomeSnapshot;
    }

    /** Restores the last complete durable tick before the runtime is exposed to a joining client. */
    void restore(List<GroundXpOrbSnapshot> snapshots) {
        List<XpOrb> restored = prepareRestore(snapshots);
        entityIds.restoreIdentities(restored.stream().map(orb -> orb.id).toList());
        installPreparedRestore(restored);
    }

    /** Validates and detaches a restore without mutating either the system or the shared allocator. */
    List<XpOrb> prepareRestore(List<GroundXpOrbSnapshot> snapshots) {
        if (!orbs.isEmpty() || !pending.isEmpty()) {
            throw new IllegalStateException("XP orbs can only be restored into an empty runtime");
        }
        List<GroundXpOrbSnapshot> restored = immutableSettlementSnapshots(snapshots);
        Set<Long> ids = new HashSet<>();
        for (GroundXpOrbSnapshot snapshot : restored) {
            validateSettlementSnapshot(snapshot);
            if (!ids.add(snapshot.entityId())) {
                throw new IllegalArgumentException("duplicate persisted XP orb id");
            }
        }
        List<XpOrb> staged = new ArrayList<>(restored.size());
        for (GroundXpOrbSnapshot snapshot : restored) {
            XpOrb orb = new XpOrb(snapshot.amount(), snapshot.x(), snapshot.y(), snapshot.z(),
                    snapshot.velocityX(), snapshot.velocityY(), snapshot.velocityZ());
            orb.id = snapshot.entityId();
            orb.age = snapshot.age();
            staged.add(orb);
        }
        return List.copyOf(staged);
    }

    /** Installs a batch already validated together with the world's item restore. */
    void installPreparedRestore(List<XpOrb> restored) {
        orbs.addAll(restored);
        publishSnapshot();
        persistenceRevision = 0;
        persistedRevision = 0;
    }

    List<GroundXpOrbSnapshot> persistenceSnapshot() {
        return orbs.stream().filter(orb -> orb.amount > 0)
                .map(orb -> new GroundXpOrbSnapshot(orb.id, orb.amount, orb.x, orb.y, orb.z,
                        orb.vx, orb.vy, orb.vz, orb.age))
                .toList();
    }

    synchronized long persistenceRevision() {
        return persistenceRevision;
    }

    synchronized boolean persistenceDirty() {
        return persistenceRevision != persistedRevision;
    }

    synchronized void acknowledgePersistence(long revision) {
        persistedRevision = Math.max(persistedRevision, Math.min(revision, persistenceRevision));
    }

    void reserveEntityIdThrough(long entityId) {
        requireNoActiveExplosionPlan();
        entityIds.reserveThrough(entityId);
    }

    private static long nextIdAfter(long entityId) {
        if (entityId <= 0
                || entityId > WorldRuntime.GroundEntityIdAllocator.MAX_ALLOCATABLE_ID) {
            throw new IllegalArgumentException("invalid ground XP identity high-water");
        }
        return Math.addExact(entityId, 1L);
    }

    private long preflightSplitBatch(int amount) {
        if (amount <= 0) return entityIds.nextId();
        long count = splitOrbCount(amount);
        return entityIds.preflightBatch(count);
    }

    private static long splitOrbCount(int amount) {
        long count = 0;
        int remaining = amount;
        while (remaining > 0) {
            int chunk = XpRules.xpOrbSplit(remaining);
            if (chunk <= 0 || chunk > remaining) {
                throw new IllegalStateException("XP orb split rule did not make progress");
            }
            remaining -= chunk;
            count++;
        }
        return count;
    }

    List<ExplosionXpOrbRequest> immutableExplosionXpRequests(
            List<ExplosionXpOrbRequest> requests) {
        if (requests == null) {
            throw new IllegalArgumentException("폭발 XP 요청 묶음이 없습니다");
        }
        for (ExplosionXpOrbRequest request : requests) {
            if (request == null) {
                throw new IllegalArgumentException("폭발 XP 요청에 빈 행이 있습니다");
            }
        }
        return List.copyOf(requests);
    }

    long preflightExplosionXpCount(List<ExplosionXpOrbRequest> requests) {
        List<ExplosionXpOrbRequest> immutable = immutableExplosionXpRequests(requests);
        long count = 0;
        for (ExplosionXpOrbRequest request : immutable) {
            if (request.amount() > 0) {
                count = Math.addExact(count, splitOrbCount(request.amount()));
            }
        }
        return count;
    }

    /** Plans XP snapshots against the typed range owned by ItemEntitySystem's reservation. */
    ExplosionXpPlan planExplodedXpOrbs(List<ExplosionXpOrbRequest> requests,
            ItemEntitySystem.ExplosionGroundReservation.XpReservationView assignedRange) {
        if (assignedRange == null || !assignedRange.belongsTo(this)) {
            throw new IllegalArgumentException("다른 XP 시스템의 폭발 ID 범위입니다");
        }
        if (activeExplosionXpPlan != null) {
            throw new IllegalStateException("다른 폭발 XP 계획이 아직 열려 있습니다");
        }
        List<ExplosionXpOrbRequest> immutable = immutableExplosionXpRequests(requests);
        long count = preflightExplosionXpCount(immutable);
        if (count != assignedRange.count()) {
            throw new IllegalArgumentException("폭발 XP ID 범위와 계획 수량이 다릅니다");
        }
        ExplosionXpCandidate candidate = planExplosionXpCandidate(immutable);
        List<GroundXpOrbSnapshot> snapshots = new ArrayList<>(candidate.orbs().size());
        long candidateId = assignedRange.firstId();
        for (PlannedExplosionOrb orb : candidate.orbs()) {
            SpawnState spawn = orb.spawn();
            snapshots.add(new GroundXpOrbSnapshot(candidateId, orb.amount(), spawn.x(),
                    spawn.y(), spawn.z(), spawn.velocityX(), spawn.velocityY(),
                    spawn.velocityZ(), 0));
            candidateId = nextIdAfter(candidateId);
        }
        if (candidateId != assignedRange.nextIdAfterBatch()) {
            throw new IllegalStateException("폭발 XP ID 일괄 사전검증이 달라졌습니다");
        }
        ExplosionXpPlan plan = new ExplosionXpPlan(this, assignedRange,
                List.copyOf(snapshots), candidate.randomDoubles());
        activeExplosionXpPlan = plan;
        return plan;
    }

    /** Alias used by integration callers that name the subplan after its output kind. */
    ExplosionXpPlan planExplosionXpOrbs(List<ExplosionXpOrbRequest> requests,
            ItemEntitySystem.ExplosionGroundReservation.XpReservationView assignedRange) {
        return planExplodedXpOrbs(requests, assignedRange);
    }

    private ExplosionXpCandidate planExplosionXpCandidate(
            List<ExplosionXpOrbRequest> requests) {
        RandomTransaction randomTransaction = new RandomTransaction();
        try {
            List<PlannedExplosionOrb> planned = new ArrayList<>();
            for (ExplosionXpOrbRequest request : requests) {
                int remaining = request.amount();
                while (remaining > 0) {
                    int chunk = XpRules.xpOrbSplit(remaining);
                    if (chunk <= 0 || chunk > remaining) {
                        throw new IllegalStateException("XP orb split rule did not make progress");
                    }
                    remaining -= chunk;
                    planned.add(new PlannedExplosionOrb(chunk,
                            randomSpawn(randomTransaction, request.x(), request.y(), request.z())));
                }
            }
            List<Double> randomDoubles = List.copyOf(randomTransaction.consumed);
            randomTransaction.rollback();
            return new ExplosionXpCandidate(List.copyOf(planned), randomDoubles);
        } catch (RuntimeException | Error failure) {
            randomTransaction.rollback();
            throw failure;
        }
    }

    /** 살아 있는 구슬 수(테스트·진단용). */
    int orbCount() {
        return orbs.size();
    }

    /** 대기 중인 구슬 승격 수(테스트·진단용). */
    int pendingOrbCount() {
        return pending.size();
    }

    /** 다음 경험치 구슬 ID(테스트·진단용). 인코딩 상한 다음 값은 소진 sentinel이다. */
    long nextEntityIdForTest() {
        return entityIds.nextId();
    }

    /** 살아 있는 구슬이 담고 있는 경험치 총합(테스트·진단용). */
    int totalAmount() {
        int total = 0;
        for (XpOrb orb : orbs) total += orb.amount;
        return total;
    }

    /** Exact fractional furnace XP currently owned by this world runtime. */
    int furnaceXpCarryMilli() {
        return furnaceXpCarryMilli;
    }

    FurnaceXpCarrySnapshot furnaceXpCarrySnapshot() {
        return new FurnaceXpCarrySnapshot(furnaceXpCarryMilli, furnaceXpCarryRevision);
    }

    synchronized boolean furnaceXpCarryDirty() {
        return furnaceXpCarryRevision != persistedFurnaceXpCarryRevision;
    }

    synchronized void acknowledgeFurnaceXpCarryPersistence(long revision) {
        persistedFurnaceXpCarryRevision = Math.max(persistedFurnaceXpCarryRevision,
                Math.min(revision, furnaceXpCarryRevision));
    }

    /** Hydrates the durable carry once, before any live orb or furnace-XP publication exists. */
    void restoreFurnaceXpCarryMilli(int amount) {
        restoreFurnaceXpCarryMilli(amount, 0L);
    }

    /** Hydrates both value and durable generation from the one-per-world row. */
    void restoreFurnaceXpCarryMilli(int amount, long revision) {
        FurnaceXpCarrySnapshot restored = new FurnaceXpCarrySnapshot(amount, revision);
        if (furnaceXpCarryInitialized || !orbs.isEmpty() || !pending.isEmpty()
                || persistenceRevision != 0 || persistedRevision != 0) {
            throw new IllegalStateException("furnace XP carry restore is stale");
        }
        furnaceXpCarryMilli = restored.amount();
        furnaceXpCarryRevision = restored.revision();
        persistedFurnaceXpCarryRevision = restored.revision();
        furnaceXpCarryInitialized = true;
    }

    /**
     * Accepts the exact milli-XP removed with a furnace, emits only deterministic whole XP orbs,
     * and retains the fractional suffix for the next publication. Orb reservation happens before
     * the carry is changed, so invalid coordinates, identity exhaustion, and allocation failures
     * leave the prior exact carry untouched.
     */
    FurnaceXpPublication publishFurnaceXp(FurnaceXpMilli source) {
        if (source == null) {
            throw new IllegalArgumentException("furnace XP publication is required");
        }
        long combined = Math.addExact((long) furnaceXpCarryMilli, (long) source.amount());
        long wholeLong = combined / XpRules.MILLI;
        if (wholeLong > Integer.MAX_VALUE) {
            throw new IllegalStateException("furnace XP publication exceeds the orb API bound");
        }
        int whole = (int) wholeLong;
        int retained = (int) (combined % XpRules.MILLI);
        if (retained != furnaceXpCarryMilli
                && furnaceXpCarryRevision == Long.MAX_VALUE - 1) {
            throw new IllegalStateException("furnace XP carry revision exhausted");
        }
        if (whole > 0) {
            spawnOrbs(whole, source.x(), source.y(), source.z());
        }
        if (retained != furnaceXpCarryMilli) {
            furnaceXpCarryRevision++;
        }
        furnaceXpCarryMilli = retained;
        furnaceXpCarryInitialized = true;
        return new FurnaceXpPublication(whole, retained);
    }

    /**
     * 경험치 구슬 하나를 예약 스폰한다. 바닐라처럼 큰 값은 여러 구슬로 쪼개고, 0 이하는 무시한다.
     * 실제 등록과 xpOrbSpawn 방송은 다음 {@link #tick} 에서 이뤄진다.
     */
    void spawnOrbs(int amount, double x, double y, double z) {
        requireNoActiveExplosionPlan();
        if (amount <= 0) return;
        requireFiniteSourceCoordinates(x, y, z);
        long nextIdAfterBatch = preflightSplitBatch(amount);

        List<XpOrb> staged = new ArrayList<>();
        long candidateId = entityIds.nextId();
        RandomTransaction randomTransaction = new RandomTransaction();
        try {
            int remaining = amount;
            while (remaining > 0) {
                // 분할은 XpRules 의 바닐라 사다리가 정본이다(정적판이 같은 순서로 같은 폭을 뽑는다).
                int chunk = XpRules.xpOrbSplit(remaining);
                remaining -= chunk;
                // 흩뿌림·초기 속도도 XpRules 가 정본이다(정적판이 같은 순서로 같은 폭을 뽑는다).
                SpawnState spawn = randomSpawn(randomTransaction, x, y, z);
                XpOrb orb = new XpOrb(chunk, spawn.x(), spawn.y(), spawn.z(),
                        spawn.velocityX(), spawn.velocityY(), spawn.velocityZ());
                orb.id = candidateId;
                staged.add(orb);
                candidateId = nextIdAfter(candidateId);
            }
            List<XpOrb> immutable = List.copyOf(staged);
            if (candidateId != nextIdAfterBatch) {
                throw new IllegalStateException("XP orb identity preflight drifted");
            }
            entityIds.commitBatch(immutable.getFirst().id, nextIdAfterBatch);
            pending.addAll(immutable);
            for (int index = 0; index < immutable.size(); index++) {
                markPersistenceDirty();
            }
        } catch (RuntimeException | Error failure) {
            randomTransaction.rollback();
            throw failure;
        }
    }

    List<GroundXpOrbSnapshot> reserveSettlementOrbs(
            int amount, double x, double y, double z) {
        requireNoActiveExplosionPlan();
        if (amount <= 0) return List.of();
        requireFiniteSourceCoordinates(x, y, z);
        long nextIdAfterBatch = preflightSplitBatch(amount);

        List<GroundXpOrbSnapshot> snapshots = new ArrayList<>();
        long candidateId = entityIds.nextId();
        RandomTransaction randomTransaction = new RandomTransaction();
        try {
            int remaining = amount;
            while (remaining > 0) {
                int chunk = XpRules.xpOrbSplit(remaining);
                remaining -= chunk;
                SpawnState spawn = randomSpawn(randomTransaction, x, y, z);
                snapshots.add(new GroundXpOrbSnapshot(candidateId, chunk, spawn.x(), spawn.y(),
                        spawn.z(), spawn.velocityX(), spawn.velocityY(), spawn.velocityZ(), 0));
                candidateId = nextIdAfter(candidateId);
            }
            List<GroundXpOrbSnapshot> immutable = List.copyOf(snapshots);
            if (candidateId != nextIdAfterBatch) {
                throw new IllegalStateException("XP orb identity preflight drifted");
            }
            entityIds.commitBatch(immutable.getFirst().entityId(), nextIdAfterBatch);
            return immutable;
        } catch (RuntimeException | Error failure) {
            randomTransaction.rollback();
            throw failure;
        }
    }

    void commitSettlementOrbs(List<GroundXpOrbSnapshot> snapshots) {
        requireNoActiveExplosionPlan();
        List<GroundXpOrbSnapshot> batch = immutableSettlementSnapshots(snapshots);
        if (batch.isEmpty()) return;

        Set<Long> batchIds = new HashSet<>();
        long nextIdAfterBatch = entityIds.nextId();
        for (GroundXpOrbSnapshot snapshot : batch) {
            validateSettlementSnapshot(snapshot);
            if (!batchIds.add(snapshot.entityId())) {
                throw new IllegalArgumentException("duplicate XP orb settlement id");
            }
            if (rt != null) rt.requireGroundXpIdentityAvailable(snapshot.entityId());
            XpOrb installed = orb(snapshot.entityId());
            if (installed != null) {
                if (!exactSnapshot(installed, snapshot)) {
                    throw new IllegalStateException("XP orb identity collision");
                }
                continue;
            }
            if (pendingOrb(snapshot.entityId()) != null) {
                throw new IllegalStateException("XP orb identity is already reserved");
            }
            if (snapshot.entityId() >= nextIdAfterBatch) {
                nextIdAfterBatch = nextIdAfter(snapshot.entityId());
            }
        }

        List<XpOrb> staged = new ArrayList<>();
        for (GroundXpOrbSnapshot snapshot : batch) {
            if (orb(snapshot.entityId()) != null) continue;
            XpOrb installed = new XpOrb(snapshot.amount(), snapshot.x(), snapshot.y(), snapshot.z(),
                    snapshot.velocityX(), snapshot.velocityY(), snapshot.velocityZ());
            installed.id = snapshot.entityId();
            installed.age = snapshot.age();
            staged.add(installed);
        }
        if (staged.isEmpty()) return;

        List<XpOrb> immutable = List.copyOf(staged);
        entityIds.reserveThrough(nextIdAfterBatch - 1);
        orbs.addAll(immutable);
        for (XpOrb installed : immutable) {
            broadcast(new XpOrbSpawn(installed.id, installed.amount, installed.x, installed.y,
                    installed.z, installed.vx * SERVER_TICKS_PER_SECOND,
                    installed.vy * SERVER_TICKS_PER_SECOND,
                    installed.vz * SERVER_TICKS_PER_SECOND));
        }
        markPersistenceDirty();
        publishSnapshot();
    }

    /** 최종 clamp 전에 음이 아닌 상태를 유지하며 XP를 합산한다. */
    static int checkedXpTotalAfterPickup(int currentXpTotal, int orbAmount) {
        if (currentXpTotal < 0 || orbAmount < 0) {
            throw new IllegalArgumentException("XP totals must be nonnegative");
        }
        long accumulated = Math.addExact((long) currentXpTotal, (long) orbAmount);
        if (accumulated < 0) {
            throw new IllegalStateException("XP total overflowed its nonnegative long range");
        }
        return (int) Math.min((long) Integer.MAX_VALUE, accumulated);
    }

    private static void requireFiniteSourceCoordinates(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("XP orb source coordinates must be finite");
        }
    }

    private SpawnState randomSpawn(RandomTransaction randomTransaction,
            double x, double y, double z) {
        double sx = x + (randomTransaction.nextDouble() - 0.5)
                * XpRules.XP_ORB_SPAWN_JITTER;
        double sz = z + (randomTransaction.nextDouble() - 0.5)
                * XpRules.XP_ORB_SPAWN_JITTER;
        double vx = (randomTransaction.nextDouble() - 0.5)
                * XpRules.XP_ORB_SPAWN_SPEED;
        double vz = (randomTransaction.nextDouble() - 0.5)
                * XpRules.XP_ORB_SPAWN_SPEED;
        return new SpawnState(sx, y, sz, vx, XpRules.XP_ORB_SPAWN_LIFT, vz);
    }

    private static List<GroundXpOrbSnapshot> immutableSettlementSnapshots(
            List<GroundXpOrbSnapshot> snapshots) {
        if (snapshots == null) {
            throw new IllegalArgumentException("XP orb settlement snapshots are required");
        }
        try {
            return List.copyOf(snapshots);
        } catch (NullPointerException invalidSnapshot) {
            throw new IllegalArgumentException("XP orb settlement snapshot is required",
                    invalidSnapshot);
        }
    }

    private static void validateSettlementSnapshot(GroundXpOrbSnapshot snapshot) {
        if (snapshot == null || snapshot.entityId() <= 0
                || snapshot.entityId() > WorldRuntime.GroundEntityIdAllocator.MAX_ALLOCATABLE_ID
                || snapshot.amount() <= 0 || snapshot.amount() > MAX_ORB_AMOUNT
                || snapshot.age() < 0 || snapshot.age() > DESPAWN_AGE
                || !Double.isFinite(snapshot.x()) || !Double.isFinite(snapshot.y())
                || !Double.isFinite(snapshot.z()) || !Double.isFinite(snapshot.velocityX())
                || !Double.isFinite(snapshot.velocityY())
                || !Double.isFinite(snapshot.velocityZ())) {
            throw new IllegalArgumentException("invalid XP orb settlement snapshot");
        }
    }

    /** 안정된 entity identity를 포함한 모든 durable 필드를 bit-exact로 비교한다. */
    private static boolean exactSnapshot(XpOrb orb, GroundXpOrbSnapshot snapshot) {
        return orb.id == snapshot.entityId()
                && orb.amount == snapshot.amount()
                && Double.doubleToLongBits(orb.x) == Double.doubleToLongBits(snapshot.x())
                && Double.doubleToLongBits(orb.y) == Double.doubleToLongBits(snapshot.y())
                && Double.doubleToLongBits(orb.z) == Double.doubleToLongBits(snapshot.z())
                && Double.doubleToLongBits(orb.vx)
                        == Double.doubleToLongBits(snapshot.velocityX())
                && Double.doubleToLongBits(orb.vy)
                        == Double.doubleToLongBits(snapshot.velocityY())
                && Double.doubleToLongBits(orb.vz)
                        == Double.doubleToLongBits(snapshot.velocityZ())
                && orb.age == snapshot.age();
    }

    private XpOrb pendingOrb(long entityId) {
        for (XpOrb orb : pending) if (orb.id == entityId) return orb;
        return null;
    }

    boolean containsIdentity(long entityId) {
        return orb(entityId) != null || pendingOrb(entityId) != null;
    }

    /** Rechecks an XP subplan without reserving or claiming the shared allocator. */
    List<XpOrb> preflightExplodedXpPlan(ExplosionXpPlan plan) {
        if (plan == null || plan.owner != this) {
            throw new IllegalArgumentException("다른 XP 시스템의 폭발 계획입니다");
        }
        if (plan.state != ExplosionXpPlanState.OPEN || activeExplosionXpPlan != plan
                || !plan.assignedRange.belongsTo(this)
                || !plan.assignedRange.allocatorWindowIsOpen()) {
            throw new IllegalStateException("폭발 XP 계획 소유권이 만료되었습니다");
        }
        Set<Long> ids = new HashSet<>();
        List<XpOrb> staged = new ArrayList<>(plan.snapshots.size());
        for (GroundXpOrbSnapshot snapshot : plan.snapshots) {
            validateSettlementSnapshot(snapshot);
            if (!ids.add(snapshot.entityId())) {
                throw new IllegalStateException("폭발 XP 계획에 중복 ID가 있습니다");
            }
            if (containsIdentity(snapshot.entityId())) {
                throw new IllegalStateException("폭발 XP ID가 이미 사용 중입니다");
            }
            XpOrb installed = new XpOrb(snapshot.amount(), snapshot.x(), snapshot.y(), snapshot.z(),
                    snapshot.velocityX(), snapshot.velocityY(), snapshot.velocityZ());
            installed.id = snapshot.entityId();
            installed.age = snapshot.age();
            staged.add(installed);
        }
        requireReservedRandomPrefix(plan.randomDoubles);
        return List.copyOf(staged);
    }

    void installExplosionXpPlan(ExplosionXpPlan plan, List<XpOrb> staged) {
        if (plan == null || plan.owner != this || plan.state != ExplosionXpPlanState.OPEN
                || activeExplosionXpPlan != plan) {
            throw new IllegalStateException("폭발 XP 계획 소유권이 만료되었습니다");
        }
        if (!staged.isEmpty()) {
            pending.addAll(staged);
            for (int index = 0; index < staged.size(); index++) markPersistenceDirty();
        }
        consumeReservedRandomDoubles(plan.randomDoubles);
        plan.state = ExplosionXpPlanState.COMMITTED;
        activeExplosionXpPlan = null;
    }

    void rollbackExplosionXpPlan(ExplosionXpPlan plan) {
        if (plan == null || plan.owner != this || plan.state != ExplosionXpPlanState.OPEN
                || activeExplosionXpPlan != plan) {
            throw new IllegalStateException("폭발 XP 계획 소유권이 만료되었습니다");
        }
        plan.state = ExplosionXpPlanState.ROLLED_BACK;
        activeExplosionXpPlan = null;
    }

    private void requireReservedRandomPrefix(List<Double> reserved) {
        if (replayRandomDoubles.size() < reserved.size()) {
            throw new IllegalStateException("폭발 XP 난수 예약이 사라졌습니다");
        }
        Iterator<Double> actual = replayRandomDoubles.iterator();
        for (Double expected : reserved) {
            if (!actual.hasNext()
                    || Double.doubleToLongBits(actual.next())
                            != Double.doubleToLongBits(expected)) {
                throw new IllegalStateException("폭발 XP 난수 예약이 오래되었습니다");
            }
        }
    }

    private void consumeReservedRandomDoubles(List<Double> reserved) {
        requireReservedRandomPrefix(reserved);
        for (int index = 0; index < reserved.size(); index++) {
            replayRandomDoubles.removeFirst();
        }
    }

    private void requireNoActiveExplosionPlan() {
        if (activeExplosionXpPlan != null) {
            throw new IllegalStateException("폭발 XP 계획이 난수·ID 순서를 예약하고 있습니다");
        }
    }

    private final class RandomTransaction {
        private final List<Double> consumed = new ArrayList<>();

        private double nextDouble() {
            double value = replayRandomDoubles.isEmpty()
                    ? rng.nextDouble() : replayRandomDoubles.removeFirst();
            consumed.add(value);
            return value;
        }

        private void rollback() {
            for (int index = consumed.size() - 1; index >= 0; index--) {
                replayRandomDoubles.addFirst(consumed.get(index));
            }
        }
    }

    private record PlannedExplosionOrb(int amount, SpawnState spawn) { }

    private record ExplosionXpCandidate(List<PlannedExplosionOrb> orbs,
            List<Double> randomDoubles) { }

    private enum ExplosionXpPlanState { OPEN, COMMITTED, ROLLED_BACK }

    static final class ExplosionXpPlan {
        private final XpOrbSystem owner;
        private final ItemEntitySystem.ExplosionGroundReservation.XpReservationView assignedRange;
        private final List<GroundXpOrbSnapshot> snapshots;
        private final List<Double> randomDoubles;
        private ExplosionXpPlanState state = ExplosionXpPlanState.OPEN;

        private ExplosionXpPlan(XpOrbSystem owner,
                ItemEntitySystem.ExplosionGroundReservation.XpReservationView assignedRange,
                List<GroundXpOrbSnapshot> snapshots, List<Double> randomDoubles) {
            this.owner = owner;
            this.assignedRange = assignedRange;
            this.snapshots = snapshots;
            this.randomDoubles = randomDoubles;
        }

        List<GroundXpOrbSnapshot> snapshots() {
            return snapshots;
        }
    }

    private record SpawnState(double x, double y, double z,
            double velocityX, double velocityY, double velocityZ) {
        private SpawnState {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Double.isFinite(velocityX) || !Double.isFinite(velocityY)
                    || !Double.isFinite(velocityZ)) {
                throw new IllegalArgumentException("generated XP orb state is not finite");
            }
        }
    }

    void tick() {
        boolean changed = false;

        // 1) 신규 스폰 정산 + xpOrbSpawn 방송(스폰 틱은 이동/획득 없음: age 0).
        if (!pending.isEmpty() && rt.groundMutationSettlements() != null) {
            rt.settlePendingGroundSpawns();
        } else if (!pending.isEmpty()) {
            for (XpOrb orb : pending) {
                orbs.add(orb);
                broadcast(new XpOrbSpawn(orb.id, orb.amount, orb.x, orb.y, orb.z,
                        orb.vx * SERVER_TICKS_PER_SECOND,
                        orb.vy * SERVER_TICKS_PER_SECOND,
                        orb.vz * SERVER_TICKS_PER_SECOND));
            }
            pending.clear();
            changed = true;
        }

        if (orbs.isEmpty()) {
            return;
        }

        // 2) 자석 흡인 + 물리(살아온 지 1틱 이상인 것만 이동).
        ItemEntitySystem.CollisionLookup solid = ItemEntitySystem.collisionLookup(rt);
        for (XpOrb orb : orbs) {
            if (pendingPlayerSettlements.contains(orb.id)) continue;
            if (orb.age < 1) continue;
            applyMagnet(orb);
            integrate(orb, solid);
        }

        // 3) 병합(같은 자리의 구슬은 한 덩어리로 합친다).
        List<Long> merged = merge(orbs, pendingPlayerSettlements);
        if (!merged.isEmpty()) changed = true;

        // 4) 근접 획득(age>=PICKUP_MIN_AGE). 획득자에게 경험치를 주고 xpUpdate 는 틱 루프가 방송한다.
        //    바닐라와 같이 한 번 흡수한 플레이어는 유예가 끝날 때까지 다음 오브를 줍지 못한다.
        List<Long> picked = null;
        for (XpOrb orb : orbs) {
            if (orb.amount <= 0 || orb.age < PICKUP_MIN_AGE) continue;
            PlayerTickState player = nearestPickup(orb);
            if (player == null) continue;
            if (rt.groundMutationSettlements() != null) {
                if (rt.groundSettlementAvailable() && submitPlayerPickup(orb, player)) break;
                continue;
            }
            // [ENCHANT-WIDE] 수선: 바닐라 ExperienceOrb.playerTouch 는 repairPlayerItems 가 남긴 XP 만 준다.
            int leftover = player.inventory().applyMending(orb.amount, mendingRandom::nextInt);
            if (leftover != orb.amount) sendInventory(player);
            int nextXpTotal = checkedXpTotalAfterPickup(player.xpTotal(), leftover);
            player.setXpTotal(nextXpTotal);
            takeDelay.put(player.nickname(), XpRules.XP_ORB_TAKE_DELAY_TICKS);
            orb.amount = 0;
            markPersistenceDirty();
            if (picked == null) picked = new ArrayList<>();
            picked.add(orb.id);
            changed = true;
        }

        // 5) 수명 소멸.
        List<Long> despawned = null;
        for (XpOrb orb : orbs) {
            if (!pendingPlayerSettlements.contains(orb.id)
                    && orb.age >= DESPAWN_AGE && orb.amount > 0) {
                if (despawned == null) despawned = new ArrayList<>();
                despawned.add(orb.id);
            }
        }

        changed |= removeDead(picked, despawned, merged);
        if (!merged.isEmpty() || picked != null && !picked.isEmpty()
                || despawned != null && !despawned.isEmpty()) {
            markPersistenceDirty();
        }

        for (XpOrb orb : orbs) {
            if (!pendingPlayerSettlements.contains(orb.id)) orb.age++;
        }
        tickTakeDelay();

        changed |= broadcastUpdates();

        if (changed) publishSnapshot();
    }

    boolean hasPendingSpawns() {
        return !pending.isEmpty();
    }

    List<GroundXpOrbSnapshot> pendingSpawnSnapshots() {
        return pending.stream().map(this::snapshot).toList();
    }

    void validatePendingSpawns(List<GroundXpOrbSnapshot> snapshots) {
        Map<Long, GroundXpOrbSnapshot> current = pendingSpawnSnapshots().stream()
                .collect(java.util.stream.Collectors.toMap(GroundXpOrbSnapshot::entityId,
                        java.util.function.Function.identity()));
        for (GroundXpOrbSnapshot snapshot : snapshots) {
            if (!snapshot.equals(current.get(snapshot.entityId()))) {
                throw new IllegalStateException("committed XP spawn diverged from frozen batch");
            }
        }
    }

    /** The shared item/XP transaction has committed; publish only its frozen members. */
    void commitPendingSpawns(List<GroundXpOrbSnapshot> snapshots) {
        if (snapshots.isEmpty()) return;
        Set<Long> ids = snapshots.stream().map(GroundXpOrbSnapshot::entityId)
                .collect(java.util.stream.Collectors.toSet());
        List<XpOrb> batch = pending.stream().filter(orb -> ids.contains(orb.id)).toList();
        pending.removeAll(batch);
        for (XpOrb orb : batch) {
            orbs.add(orb);
            broadcast(new XpOrbSpawn(orb.id, orb.amount, orb.x, orb.y, orb.z,
                    orb.vx * SERVER_TICKS_PER_SECOND,
                    orb.vy * SERVER_TICKS_PER_SECOND,
                    orb.vz * SERVER_TICKS_PER_SECOND));
        }
        publishSnapshot();
    }

    private GroundXpOrbSnapshot snapshot(XpOrb orb) {
        return new GroundXpOrbSnapshot(orb.id, orb.amount, orb.x, orb.y, orb.z,
                orb.vx, orb.vy, orb.vz, orb.age);
    }

    private boolean submitPlayerPickup(XpOrb orb, PlayerTickState player) {
        PlayerInventory inventory = player.inventory();
        int amount = orb.amount;
        PlayerInventory.CompletePersistenceSnapshot source = inventory.acquireSettlementLease();
        if (source == null) return false;
        // [ENCHANT-WIDE] 수선은 같은 정산 세대 안에서 내구를 고치고, 남은 XP 만 경험치로 넣는다.
        PlayerInventory.MendingSettlement mending =
                source.nextSettlementRevisionWithMending(amount, mendingRandom::nextInt);
        PlayerInventory.CompletePersistenceSnapshot committed = mending.committed();
        int nextXpTotal = checkedXpTotalAfterPickup(player.xpTotal(), mending.leftoverXp());
        long expectedGroundRevision = rt.groundRevision();
        GroundMutationCommand command = new GroundMutationCommand(
                WorldRuntime.stableGroundMutationId(orb.id, 2,
                        expectedGroundRevision, source.revision()),
                GroundMutationCommand.Kind.XP_PICKUP, rt.worldId(),
                expectedGroundRevision, expectedGroundRevision + 1, source.revision(),
                rt.playerInventoryMutationSnapshot(player, committed, nextXpTotal),
                List.of(), List.of(), List.of(), List.of(orb.id));
        pendingPlayerSettlements.add(orb.id);
        Runnable rejected = () -> {
            pendingPlayerSettlements.remove(orb.id);
            inventory.releaseSettlementLease(source);
        };
        Runnable installed = () -> {
            pendingPlayerSettlements.remove(orb.id);
            XpOrb live = orb(orb.id);
            if (live == null || live.amount != amount) {
                throw new IllegalStateException("committed XP pickup diverged from frozen orb");
            }
            orbs.remove(live);
            markPersistenceDirty();
            if (rt.players().get(player.nickname()) == player
                    && inventory.installCommittedSettlement(source, committed)) {
                player.setXpTotal(nextXpTotal);
                takeDelay.put(player.nickname(), XpRules.XP_ORB_TAKE_DELAY_TICKS);
                if (mending.repaired()) sendInventory(player);
            } else {
                inventory.releaseSettlementLease(source);
            }
            broadcast(new XpOrbRemove(List.of(live.id), "pickup"));
            publishSnapshot();
        };
        if (!rt.submitGroundSettlement(command, installed, rejected)) {
            rejected.run();
            return false;
        }
        return true;
    }

    /** [ENCHANT-WIDE] 수선 대상 선택 굴림. 구슬 스폰·재생 난수열과 섞이지 않게 따로 둔다. */
    private java.util.Random mendingRandom = new java.util.Random();

    void setMendingRandomForTest(java.util.Random random) {
        this.mendingRandom = random;
    }

    /** 수선으로 바뀐 내구를 그 플레이어에게 보낸다(이 시스템은 경험치만 틱 루프가 방송한다). */
    private void sendInventory(PlayerTickState player) {
        if (rt == null) return;
        var session = rt.session(player.nickname());
        if (session != null) {
            rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session,
                    WorldTickLoop.inventoryMessage(player));
        }
    }

    private XpOrb orb(long entityId) {
        for (XpOrb orb : orbs) if (orb.id == entityId) return orb;
        return null;
    }

    private synchronized void markPersistenceDirty() {
        persistenceRevision++;
    }

    /** 반경 안 가장 가까운 살아 있는 플레이어 쪽으로 속도를 더한다(바닐라 흡인 근사). */
    private void applyMagnet(XpOrb orb) {
        PlayerTickState best = null;
        double bestDistanceSq = XpRules.XP_ORB_MAGNET_RADIUS * XpRules.XP_ORB_MAGNET_RADIUS;
        for (PlayerTickState player : rt.players().values()) {
            if (player.isDead()) continue;
            double dx = player.x() - orb.x;
            double dy = player.y() + XpRules.XP_ORB_MAGNET_AIM_HEIGHT - orb.y;
            double dz = player.z() - orb.z;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (distanceSq > bestDistanceSq) continue;
            // players() 는 ConcurrentHashMap 이라 순회 순서가 해시 버킷 순이다. 동점에서
            // "마지막 방문자가 이긴다"로 두면 승자가 비결정적이라 정적판과 갈린다.
            if (best != null && distanceSq == bestDistanceSq
                    && player.nickname().compareTo(best.nickname()) >= 0) {
                continue;
            }
            bestDistanceSq = distanceSq;
            best = player;
        }
        if (best == null) return;
        double dx = best.x() - orb.x;
        double dy = best.y() + XpRules.XP_ORB_MAGNET_AIM_HEIGHT - orb.y;
        double dz = best.z() - orb.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1e-9) return;
        orb.vx += dx / distance * MAGNET_PULL;
        orb.vy += dy / distance * MAGNET_PULL;
        orb.vz += dz / distance * MAGNET_PULL;
    }

    /** 아이템 드랍과 동일한 중력·지면 안착·수평 마찰(순수 함수, 테스트 주입 가능). */
    static void integrate(XpOrb e, ItemEntitySystem.CollisionLookup solid) {
        e.vy -= GRAVITY;
        if (e.vy < -TERMINAL) e.vy = -TERMINAL;
        var vertical = ItemEntitySystem.sweepPoint(solid, e.x, e.y, e.z, e.vy, 1);
        boolean grounded = e.vy <= 0 && vertical.hit;
        e.y = vertical.value;
        if (vertical.hit) e.vy = 0;
        var horizontal = ItemEntitySystem.sweepPoint(solid, e.x, e.y, e.z, e.vx, 0);
        e.x = horizontal.value;
        if (horizontal.hit) e.vx = 0;
        var depth = ItemEntitySystem.sweepPoint(solid, e.x, e.y, e.z, e.vz, 2);
        e.z = depth.value;
        if (depth.hit) e.vz = 0;

        double friction = grounded ? 0.5 : 0.92;
        e.vx *= friction;
        e.vz *= friction;
        if (Math.abs(e.vx) < POS_EPS) e.vx = 0;
        if (Math.abs(e.vz) < POS_EPS) e.vz = 0;
    }

    /**
     * 반경 안의 <b>같은 크기</b> 구슬을 앞쪽 구슬로 흡수한다. 흡수돼 amount 0 이 된 id 목록을 돌려준다.
     *
     * <p>크기가 같아야 합친다는 것이 바닐라 {@code ExperienceOrb.canMerge} 다. 크기를 안 보면
     * 분할 사다리로 갓 쪼갠 서로 다른 크기의 구슬이 같은 틱에 도로 한 덩어리가 되어 사다리가 죽는다.
     */
    static List<Long> merge(List<XpOrb> orbs) {
        return merge(orbs, Set.of());
    }

    private static List<Long> merge(List<XpOrb> orbs, Set<Long> excludedIds) {
        int size = orbs.size();
        if (size < 2) return List.of();
        return size < MERGE_GRID_THRESHOLD
                ? mergeLinear(orbs, excludedIds) : mergeGrid(orbs, excludedIds);
    }

    static List<Long> mergeLinear(List<XpOrb> orbs) {
        return mergeLinear(orbs, Set.of());
    }

    private static List<Long> mergeLinear(List<XpOrb> orbs, Set<Long> excludedIds) {
        List<Long> mergedIds = null;
        for (int i = 0; i < orbs.size(); i++) {
            XpOrb a = orbs.get(i);
            if (excludedIds.contains(a.id)
                    || a.amount <= 0 || a.amount >= MAX_ORB_AMOUNT) continue;
            for (int j = i + 1; j < orbs.size(); j++) {
                XpOrb b = orbs.get(j);
                if (excludedIds.contains(b.id)
                        || b.amount <= 0 || a.amount >= MAX_ORB_AMOUNT) continue;
                if (a.amount != b.amount) continue;
                double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
                if (dx * dx + dy * dy + dz * dz > MERGE_RANGE_SQUARED) continue;
                int move = Math.min(MAX_ORB_AMOUNT - a.amount, b.amount);
                a.amount += move;
                b.amount -= move;
                if (b.amount <= 0) {
                    if (mergedIds == null) mergedIds = new ArrayList<>();
                    mergedIds.add(b.id);
                }
                if (a.amount >= MAX_ORB_AMOUNT) break;
            }
        }
        return mergedIds == null ? List.of() : mergedIds;
    }

    static List<Long> mergeGrid(List<XpOrb> orbs) {
        return mergeGrid(orbs, Set.of());
    }

    private static List<Long> mergeGrid(List<XpOrb> orbs, Set<Long> excludedIds) {
        SpatialGrid grid = new SpatialGrid(MERGE_CELL);
        grid.reset(orbs.size());
        for (int index = 0; index < orbs.size(); index++) {
            XpOrb orb = orbs.get(index);
            grid.add(index, orb.x, orb.y, orb.z);
        }
        IntCandidates candidates = new IntCandidates();
        List<Long> mergedIds = null;
        for (int left = 0; left < orbs.size(); left++) {
            XpOrb target = orbs.get(left);
            if (excludedIds.contains(target.id)
                    || target.amount <= 0 || target.amount >= MAX_ORB_AMOUNT) continue;
            candidates.size = 0;
            grid.forEachNear(target.x, target.y, target.z, MERGE_RANGE, candidates);
            boolean linear = SpatialGrid.preferLinearTraversal(candidates.size, orbs.size());
            if (!linear) {
                java.util.Arrays.sort(candidates.values, 0, candidates.size);
            }
            int candidateCount = linear ? orbs.size() : candidates.size;
            for (int candidate = 0; candidate < candidateCount; candidate++) {
                int right = linear ? candidate : candidates.values[candidate];
                if (right <= left) continue;
                XpOrb source = orbs.get(right);
                if (excludedIds.contains(source.id)
                        || source.amount <= 0 || target.amount >= MAX_ORB_AMOUNT) continue;
                if (target.amount != source.amount) continue;
                double dx = target.x - source.x;
                double dy = target.y - source.y;
                double dz = target.z - source.z;
                if (dx * dx + dy * dy + dz * dz > MERGE_RANGE_SQUARED) continue;
                int move = Math.min(MAX_ORB_AMOUNT - target.amount, source.amount);
                target.amount += move;
                source.amount -= move;
                if (source.amount <= 0) {
                    if (mergedIds == null) mergedIds = new ArrayList<>();
                    mergedIds.add(source.id);
                }
                if (target.amount >= MAX_ORB_AMOUNT) break;
            }
        }
        return mergedIds == null ? List.of() : mergedIds;
    }

    private static final class IntCandidates implements SpatialGrid.IntSink {
        int[] values = new int[16];
        int size;

        @Override
        public void accept(int index) {
            if (size == values.length) {
                values = java.util.Arrays.copyOf(values, size * 2);
            }
            values[size++] = index;
        }
    }

    private PlayerTickState nearestPickup(XpOrb orb) {
        PlayerTickState best = null;
        double bd = Double.POSITIVE_INFINITY;
        for (PlayerTickState p : rt.players().values()) {
            if (p.isDead() || takeDelay.containsKey(p.nickname())) continue;
            if (Math.abs(orb.x - p.x()) > XpRules.XP_ORB_PICKUP_HORIZONTAL
                    || Math.abs(orb.z - p.z()) > XpRules.XP_ORB_PICKUP_HORIZONTAL
                    || orb.y < p.y() - XpRules.XP_ORB_PICKUP_BELOW
                    || orb.y > p.y() + XpRules.XP_ORB_PICKUP_ABOVE) {
                continue;
            }
            double dx = p.x() - orb.x;
            double dy = p.y() - orb.y;
            double dz = p.z() - orb.z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d > bd) continue;
            // applyMagnet 과 같은 타이브레이크. 등거리 두 플레이어에서 승자가 갈리면 안 된다.
            if (best != null && d == bd && p.nickname().compareTo(best.nickname()) >= 0) {
                continue;
            }
            bd = d;
            best = p;
        }
        return best;
    }

    /** 흡수 유예를 한 틱 줄인다. 0 이 된 항목은 지워 맵이 자라지 않게 한다. */
    private void tickTakeDelay() {
        takeDelay.values().removeIf(remaining -> remaining <= 1);
        takeDelay.replaceAll((nickname, remaining) -> remaining - 1);
    }

    private boolean removeDead(List<Long> picked, List<Long> despawned, List<Long> merged) {
        java.util.Set<Long> despawnIds = despawned == null ? null : new java.util.HashSet<>(despawned);
        boolean removedAny = false;
        for (Iterator<XpOrb> it = orbs.iterator(); it.hasNext(); ) {
            XpOrb orb = it.next();
            boolean isDespawn = despawnIds != null && despawnIds.contains(orb.id);
            if (orb.amount > 0 && !isDespawn) continue;
            it.remove();
            removedAny = true;
        }
        if (picked != null && !picked.isEmpty()) broadcast(new XpOrbRemove(picked, "pickup"));
        if (despawned != null && !despawned.isEmpty()) {
            broadcast(new XpOrbRemove(despawned, "despawn"));
        }
        if (merged != null && !merged.isEmpty()) broadcast(new XpOrbRemove(merged, "merge"));
        return removedAny;
    }

    private boolean broadcastUpdates() {
        List<XpOrbPos> moved = null;
        for (XpOrb orb : orbs) {
            if (Math.abs(orb.x - orb.lastX) > POS_EPS
                    || Math.abs(orb.y - orb.lastY) > POS_EPS
                    || Math.abs(orb.z - orb.lastZ) > POS_EPS) {
                if (moved == null) moved = new ArrayList<>();
                moved.add(new XpOrbPos(orb.id, orb.x, orb.y, orb.z));
                orb.lastX = orb.x; orb.lastY = orb.y; orb.lastZ = orb.z;
            }
        }
        if (moved != null) {
            broadcast(new XpOrbUpdates(moved));
            return true;
        }
        return false;
    }

    private void publishSnapshot() {
        List<XpOrbDto> snapshot = new ArrayList<>(orbs.size());
        for (XpOrb orb : orbs) {
            snapshot.add(new XpOrbDto(orb.id, orb.amount, orb.x, orb.y, orb.z));
        }
        welcomeSnapshot = List.copyOf(snapshot);
    }

    private void broadcast(Object message) {
        rt.ctx().broadcaster().enqueueBroadcastFromTick(rt.worldId(), message);
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }
}
