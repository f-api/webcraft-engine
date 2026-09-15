package com.gameexpert.engine;

import static com.gameexpert.engine.Fluids.AIR;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.engine.mob.MobRandom;
import com.gameexpert.tnt.dto.PrimedTntSnapshot;
import com.gameexpert.tnt.service.PrimedTntPersistenceService;
import com.gameexpert.ws.dto.WsMessages.PrimedTntDto;
import com.gameexpert.ws.dto.WsMessages.PrimedTntRemove;
import com.gameexpert.ws.dto.WsMessages.PrimedTntSpawn;
import com.gameexpert.ws.dto.WsMessages.PrimedTntUpdate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Owner-thread implementation of Minecraft Java 1.21.4 {@code PrimedTnt}.
 * The authority runs at 10 TPS, so each call performs two unscaled 20 TPS entity steps.
 */
final class PrimedTntSystem {
    static final int DEFAULT_FUSE = 80;
    static final int EXPLOSION_POWER = 4;
    static final double WIDTH = 0.98;
    static final double HEIGHT = 0.98;
    static final double EXPLOSION_Y_OFFSET = HEIGHT * 0.0625;
    static final double GRAVITY = 0.04;
    static final double DRAG = 0.98;
    static final double GROUND_HORIZONTAL = 0.7;
    static final double GROUND_BOUNCE = -0.5;
    private static final double HALF_WIDTH = WIDTH / 2.0;
    private static final double COLLISION_EPSILON = 1.0e-7;
    private static final double WATER_PUSH = 0.014;
    private static final double OVERWORLD_LAVA_PUSH = 0.0023333333333333335;

    private final WorldRuntime rt;
    private final Map<Long, PrimedTntEntity> entities = new LinkedHashMap<>();
    /** 같은 틱 중 연쇄 생성된 TNT는 다음 틱부터 움직이게 하는 재사용 순회 스냅샷. */
    private final List<PrimedTntEntity> tickSnapshot = new ArrayList<>();
    private final double[] fluidFlow = new double[3];
    /** ID별 마지막 변경 세대. 성공 승인 때 캡처 세대 이하만 지워 in-flight 중 새 변경을 보존합니다. */
    private final Map<Long, Long> changedPersistenceVersions = new LinkedHashMap<>();
    private final Map<Long, Long> removedPersistenceVersions = new LinkedHashMap<>();
    private long nextId = 1;
    private PrimedTntPersistenceService persistence;
    private long persistenceRevision;
    private boolean persistenceInFlight;
    private ExplosionMutationPlan activeExplosionMutationPlan;

    PrimedTntSystem(WorldRuntime rt) {
        this.rt = rt;
    }

    void attachPersistence(PrimedTntPersistenceService service) {
        if (persistence != null || !entities.isEmpty()) {
            throw new IllegalStateException("primed TNT persistence already attached or simulation started");
        }
        if (service == null) {
            persistence = null;
            return;
        }

        // Receipts reserve IDs even after their live TNT rows have been consumed. Stage every
        // restored row and the successor before publishing either one to the owner-thread state.
        long reservedHighWater = service.highestReservedTntId(rt.worldId());
        long stagedNextId = nextTntIdAfter(reservedHighWater);
        Map<Long, PrimedTntEntity> restoredEntities = new LinkedHashMap<>();
        List<PrimedTntSnapshot> restoredSnapshots = service.loadWorld(rt.worldId()).stream()
                .sorted(Comparator.comparingLong(PrimedTntSnapshot::tntId)).toList();
        for (PrimedTntSnapshot snapshot : restoredSnapshots) {
            requireUsableTntId(snapshot.tntId());
            PrimedTntEntity restored = PrimedTntEntity.restore(snapshot);
            if (restoredEntities.put(restored.id, restored) != null) {
                throw new IllegalStateException("duplicate restored primed TNT id " + restored.id);
            }
            stagedNextId = Math.max(stagedNextId, nextTntIdAfter(restored.id));
        }
        entities.putAll(restoredEntities);
        nextId = stagedNextId;
        persistence = service;
    }

    PrimedTntEntity primeBlock(int x, int y, int z, Random random) {
        return primeBlock(x, y, z, random, DEFAULT_FUSE);
    }

    PrimedTntEntity primeBlock(int x, int y, int z, Random random, int fuse) {
        if (WorldTickLoop.residentBlockType(rt.accessor(), x, y, z) != Blocks.TNT) return null;
        long tntId = reserveNextTntId();
        double angle = random.nextDouble() * Math.PI * 2.0;
        PrimedTntEntity entity = new PrimedTntEntity(tntId, x + 0.5, y, z + 0.5,
                -Math.sin(angle) * 0.02, 0.20000000298023224, -Math.cos(angle) * 0.02,
                Math.max(1, Math.min(DEFAULT_FUSE, fuse)));
        // The block ceases to exist at the same owner-thread boundary at which the entity becomes live.
        rt.fluidSim().applyChange(x, y, z, AIR);
        entities.put(entity.id, entity);
        markPersistenceChanged(entity.id);
        broadcast(new PrimedTntSpawn(dto(entity)));
        return entity;
    }

    /**
     * [CONTAINER-MENUS] {@code DispenseItemBehavior$6} (TNT): {@code new PrimedTnt(level, x + 0.5,
     * y, z + 0.5, null)} at the cell in front of the dispenser. No block is removed; the
     * constructor's angle draw and fuse 80 are those of {@link #primeBlock}.
     */
    PrimedTntEntity primeDispensed(int x, int y, int z, Random random) {
        long tntId = reserveNextTntId();
        double angle = random.nextDouble() * Math.PI * 2.0;
        PrimedTntEntity entity = new PrimedTntEntity(tntId, x + 0.5, y, z + 0.5,
                -Math.sin(angle) * 0.02, 0.20000000298023224, -Math.cos(angle) * 0.02,
                DEFAULT_FUSE);
        entities.put(entity.id, entity);
        markPersistenceChanged(entity.id);
        broadcast(new PrimedTntSpawn(dto(entity)));
        return entity;
    }

    /** TntBlock.wasExploded: constructor consumes the angle draw before the shortened-fuse draw. */
    PrimedTntEntity primeBlockFromExplosion(int x, int y, int z, MobRandom random) {
        if (WorldTickLoop.residentBlockType(rt.accessor(), x, y, z) != Blocks.TNT) return null;
        requireUsableTntId(nextId);
        double angle = random.nextDouble() * Math.PI * 2.0;
        int fuse = DEFAULT_FUSE / 8 + random.nextInt(DEFAULT_FUSE / 4);
        PrimedTntSnapshot snapshot = new PrimedTntSnapshot(nextId, x + 0.5, y, z + 0.5,
                -Math.sin(angle) * 0.02, 0.20000000298023224, -Math.cos(angle) * 0.02, fuse);
        ExplosionMutationPlan plan = planExplosionMutations(null, List.of(snapshot));
        rt.fluidSim().applyChange(x, y, z, AIR);
        commitExplosionMutations(plan);
        return entities.get(snapshot.tntId());
    }

    void tick() {
        if (entities.isEmpty()) return;
        List<PrimedTntDto> updates = null;
        tickSnapshot.addAll(entities.values());
        try {
            for (PrimedTntEntity entity : tickSnapshot) {
                if (!isTickingChunk(entity)) continue;
                boolean removed = false;
                boolean stepped = false;
                for (int step = 0; step < 2; step++) {
                    if (!hasResidentMovementArea(entity)) break;
                    if (entity.fuse > 1) {
                        step(entity);
                        stepped = true;
                        continue;
                    }
                    double previousX = entity.x, previousY = entity.y, previousZ = entity.z;
                    double previousVx = entity.vx, previousVy = entity.vy, previousVz = entity.vz;
                    int previousFuse = entity.fuse;
                    step(entity);
                    if (!rt.mobSystem().explode(entity.x, entity.y + EXPLOSION_Y_OFFSET,
                            entity.z, EXPLOSION_POWER)) {
                        // 비상주 폭발 영역은 실패한 20 Hz 단계 전체를 재시도한다. 그렇지 않으면
                        // fuse 0 TNT가 청크를 기다리는 동안 매 틱 중력과 이동을 한 번 더 받는다.
                        entity.x = previousX;
                        entity.y = previousY;
                        entity.z = previousZ;
                        entity.vx = previousVx;
                        entity.vy = previousVy;
                        entity.vz = previousVz;
                        entity.fuse = previousFuse;
                        break;
                    }
                    stepped = true;
                    ExplosionMutationPlan plan = planExplosionMutations(
                            entity.snapshot(), List.of());
                    commitExplosionMutations(plan);
                    removed = true;
                    break;
                }
                if (!removed && stepped) {
                    markPersistenceChanged(entity.id);
                    if (updates == null) updates = new ArrayList<>();
                    updates.add(dto(entity));
                }
            }
        } finally {
            tickSnapshot.clear();
        }
        if (updates != null) broadcast(new PrimedTntUpdate(List.copyOf(updates)));
    }

    private void step(PrimedTntEntity entity) {
        entity.vy -= GRAVITY;
        boolean onGround = move(entity, entity.vx, entity.vy, entity.vz);
        entity.vx *= DRAG;
        entity.vy *= DRAG;
        entity.vz *= DRAG;
        if (onGround) {
            entity.vx *= GROUND_HORIZONTAL;
            entity.vy *= GROUND_BOUNCE;
            entity.vz *= GROUND_HORIZONTAL;
        }
        entity.fuse--;
        if (entity.fuse > 0) applyFluidPush(entity);
    }

    private boolean move(PrimedTntEntity entity, double dx, double dy, double dz) {
        double clippedY = clipY(entity, dy);
        entity.y += clippedY;
        double clippedX = clipX(entity, dx);
        entity.x += clippedX;
        double clippedZ = clipZ(entity, dz);
        entity.z += clippedZ;
        return dy < 0.0 && Math.abs(dy - clippedY) > COLLISION_EPSILON;
    }

    private double clipY(PrimedTntEntity e, double dy) {
        if (dy == 0.0) return 0.0;
        int minX = floor(e.x - HALF_WIDTH + COLLISION_EPSILON);
        int maxX = floor(e.x + HALF_WIDTH - COLLISION_EPSILON);
        int minZ = floor(e.z - HALF_WIDTH + COLLISION_EPSILON);
        int maxZ = floor(e.z + HALF_WIDTH - COLLISION_EPSILON);
        if (dy > 0.0) {
            double top = e.y + HEIGHT;
            int start = floor(top - COLLISION_EPSILON) + 1;
            int end = floor(top + dy - COLLISION_EPSILON);
            for (int y = start; y <= end; y++) {
                for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) {
                    if (solid(x, y, z)) return Math.min(dy, y - top);
                }
            }
        } else {
            int start = floor(e.y + COLLISION_EPSILON) - 1;
            int end = floor(e.y + dy + COLLISION_EPSILON);
            for (int y = start; y >= end; y--) {
                for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) {
                    if (solid(x, y, z)) return Math.max(dy, y + 1.0 - e.y);
                }
            }
        }
        return dy;
    }

    private double clipX(PrimedTntEntity e, double dx) {
        if (dx == 0.0) return 0.0;
        int minY = floor(e.y + COLLISION_EPSILON);
        int maxY = floor(e.y + HEIGHT - COLLISION_EPSILON);
        int minZ = floor(e.z - HALF_WIDTH + COLLISION_EPSILON);
        int maxZ = floor(e.z + HALF_WIDTH - COLLISION_EPSILON);
        if (dx > 0.0) {
            double edge = e.x + HALF_WIDTH;
            int end = floor(edge + dx - COLLISION_EPSILON);
            for (int x = floor(edge - COLLISION_EPSILON) + 1; x <= end; x++) {
                for (int z = minZ; z <= maxZ; z++) for (int y = minY; y <= maxY; y++) {
                    if (solid(x, y, z)) return Math.min(dx, x - edge);
                }
            }
        } else {
            double edge = e.x - HALF_WIDTH;
            int end = floor(edge + dx + COLLISION_EPSILON);
            for (int x = floor(edge + COLLISION_EPSILON) - 1; x >= end; x--) {
                for (int z = minZ; z <= maxZ; z++) for (int y = minY; y <= maxY; y++) {
                    if (solid(x, y, z)) return Math.max(dx, x + 1.0 - edge);
                }
            }
        }
        return dx;
    }

    private double clipZ(PrimedTntEntity e, double dz) {
        if (dz == 0.0) return 0.0;
        int minY = floor(e.y + COLLISION_EPSILON);
        int maxY = floor(e.y + HEIGHT - COLLISION_EPSILON);
        int minX = floor(e.x - HALF_WIDTH + COLLISION_EPSILON);
        int maxX = floor(e.x + HALF_WIDTH - COLLISION_EPSILON);
        if (dz > 0.0) {
            double edge = e.z + HALF_WIDTH;
            int end = floor(edge + dz - COLLISION_EPSILON);
            for (int z = floor(edge - COLLISION_EPSILON) + 1; z <= end; z++) {
                for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) {
                    if (solid(x, y, z)) return Math.min(dz, z - edge);
                }
            }
        } else {
            double edge = e.z - HALF_WIDTH;
            int end = floor(edge + dz + COLLISION_EPSILON);
            for (int z = floor(edge + COLLISION_EPSILON) - 1; z >= end; z--) {
                for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) {
                    if (solid(x, y, z)) return Math.max(dz, z + 1.0 - edge);
                }
            }
        }
        return dz;
    }

    private void applyFluidPush(PrimedTntEntity entity) {
        int x = floor(entity.x);
        int y = floor(entity.y);
        int z = floor(entity.z);
        int type = Fluids.typeOf(block(x, y, z));
        if (type == Fluids.NONE || !Fluids.flowVector(this::block, x, y, z, fluidFlow)) return;
        double push = type == Fluids.WATER ? WATER_PUSH : OVERWORLD_LAVA_PUSH;
        entity.vx += fluidFlow[0] * push;
        entity.vy += fluidFlow[1] * push;
        entity.vz += fluidFlow[2] * push;
    }

    private boolean isTickingChunk(PrimedTntEntity entity) {
        int chunkX = Math.floorDiv(floor(entity.x), Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv(floor(entity.z), Blocks.CHUNK_Z);
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        return rt.activeSimulationChunksForMobTick().contains(key);
    }

    private boolean hasResidentMovementArea(PrimedTntEntity entity) {
        double nextX = entity.x + entity.vx;
        double nextZ = entity.z + entity.vz;
        int minChunkX = Math.floorDiv(floor(Math.min(entity.x, nextX) - HALF_WIDTH), Blocks.CHUNK_X);
        int maxChunkX = Math.floorDiv(floor(Math.max(entity.x, nextX) + HALF_WIDTH), Blocks.CHUNK_X);
        int minChunkZ = Math.floorDiv(floor(Math.min(entity.z, nextZ) - HALF_WIDTH), Blocks.CHUNK_Z);
        int maxChunkZ = Math.floorDiv(floor(Math.max(entity.z, nextZ) + HALF_WIDTH), Blocks.CHUNK_Z);
        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                if (!rt.accessor().isChunkResident(cx, cz)) return false;
            }
        }
        return true;
    }

    private boolean solid(int x, int y, int z) {
        return Fluids.isSolid(block(x, y, z));
    }

    private int block(int x, int y, int z) {
        return WorldTickLoop.residentBlockType(rt.accessor(), x, y, z);
    }

    List<PrimedTntDto> welcomeSnapshot() {
        return entities.values().stream().map(this::dto).toList();
    }

    List<PrimedTntSnapshot> persistenceSnapshot() {
        return entities.values().stream().map(PrimedTntEntity::snapshot).toList();
    }

    /**
     * Freezes one exact exploding source removal and every chained TNT insertion. Planning is
     * observational: entity state, the ID allocator, persistence dirtiness and broadcasts remain
     * unchanged until the returned owner token commits. Chained snapshots are canonicalized by ID.
     */
    ExplosionMutationPlan planExplosionMutations(PrimedTntSnapshot explodingSource,
            List<PrimedTntSnapshot> chainedTnt) {
        if (activeExplosionMutationPlan != null) {
            throw new IllegalStateException("다른 TNT 폭발 변경 계획이 아직 열려 있습니다");
        }
        if (explodingSource == null && (chainedTnt == null || chainedTnt.isEmpty())) {
            throw new IllegalArgumentException("TNT 폭발 변경 계획이 비어 있습니다");
        }
        List<PrimedTntSnapshot> chained = chainedTnt == null
                ? List.of()
                : chainedTnt.stream().sorted(Comparator.comparingLong(PrimedTntSnapshot::tntId))
                        .toList();
        requireUsableTntId(nextId);
        long nextIdAfterBatch = Math.addExact(nextId, chained.size());
        if (nextIdAfterBatch >= Long.MAX_VALUE) {
            throw new IllegalStateException("TNT identity allocator is exhausted");
        }
        ExplosionMutationPlan plan = new ExplosionMutationPlan(this, nextId,
                nextIdAfterBatch, explodingSource, chained);
        preflightExplosionMutations(plan);
        activeExplosionMutationPlan = plan;
        return plan;
    }

    /** Rechecks the complete exact-state/ID window without changing any live state. */
    List<TntMutation> preflightExplosionMutations(ExplosionMutationPlan plan) {
        requireExplosionPlanOwner(plan);
        if (plan.state != ExplosionPlanState.OPEN
                || (activeExplosionMutationPlan != null && activeExplosionMutationPlan != plan)) {
            throw new IllegalStateException("TNT 폭발 변경 계획 소유권이 만료되었습니다");
        }
        if (nextId != plan.expectedNextId) {
            throw new IllegalStateException("TNT 폭발 ID 예약이 오래되었습니다");
        }
        Set<Long> ids = new HashSet<>();
        List<TntMutation> mutations = new ArrayList<>(plan.chainedTnt.size() + 1);
        if (plan.explodingSource != null) {
            PrimedTntEntity source = entities.get(plan.explodingSource.tntId());
            if (source == null || !source.snapshot().equals(plan.explodingSource)) {
                throw new IllegalStateException("폭발 원본 TNT 스냅샷이 오래되었거나 다른 권위입니다");
            }
            ids.add(plan.explodingSource.tntId());
            mutations.add(new TntMutation(plan.explodingSource, null));
        }
        long expectedId = plan.expectedNextId;
        for (PrimedTntSnapshot snapshot : plan.chainedTnt) {
            if (snapshot == null || !isUsableTntId(snapshot.tntId())
                    || snapshot.tntId() != expectedId
                    || !ids.add(snapshot.tntId()) || entities.containsKey(snapshot.tntId())) {
                throw new IllegalStateException("연쇄 TNT ID가 중복되었거나 예약 창과 다릅니다");
            }
            mutations.add(new TntMutation(null, snapshot));
            expectedId = Math.addExact(expectedId, 1L);
        }
        if (expectedId != plan.nextIdAfterBatch) {
            throw new IllegalStateException("연쇄 TNT ID 예약 끝이 다릅니다");
        }
        return List.copyOf(mutations);
    }

    /** Installs every planned TNT mutation or none; terminal-token replay is rejected. */
    boolean commitExplosionMutations(ExplosionMutationPlan plan) {
        requireExplosionPlanOwner(plan);
        if (plan.state != ExplosionPlanState.OPEN) {
            throw new IllegalStateException("종료된 TNT 폭발 변경 계획은 다시 커밋할 수 없습니다");
        }
        List<TntMutation> mutations = preflightExplosionMutations(plan);
        if (activeExplosionMutationPlan != plan) {
            throw new IllegalStateException("TNT 폭발 변경 계획 소유권이 만료되었습니다");
        }
        if (plan.explodingSource != null) {
            entities.remove(plan.explodingSource.tntId());
        }
        for (PrimedTntSnapshot snapshot : plan.chainedTnt) {
            entities.put(snapshot.tntId(), PrimedTntEntity.restore(snapshot));
        }
        nextId = plan.nextIdAfterBatch;
        if (plan.explodingSource != null) markPersistenceRemoved(plan.explodingSource.tntId());
        for (PrimedTntSnapshot snapshot : plan.chainedTnt) markPersistenceChanged(snapshot.tntId());
        plan.state = ExplosionPlanState.COMMITTED;
        activeExplosionMutationPlan = null;
        if (plan.explodingSource != null) {
            broadcast(new PrimedTntRemove(List.of(plan.explodingSource.tntId())));
        }
        for (PrimedTntSnapshot snapshot : plan.chainedTnt) {
            broadcast(new PrimedTntSpawn(dto(entities.get(snapshot.tntId()))));
        }
        return !mutations.isEmpty();
    }

    /** Releases an open plan without consuming IDs or changing entity/RNG/persistence state. */
    boolean rollbackExplosionMutations(ExplosionMutationPlan plan) {
        requireExplosionPlanOwner(plan);
        if (plan.state != ExplosionPlanState.OPEN || activeExplosionMutationPlan != plan) {
            throw new IllegalStateException("TNT 폭발 변경 계획 소유권이 만료되었습니다");
        }
        plan.state = ExplosionPlanState.ROLLED_BACK;
        activeExplosionMutationPlan = null;
        return true;
    }

    private void requireExplosionPlanOwner(ExplosionMutationPlan plan) {
        if (plan == null || plan.owner != this) {
            throw new IllegalArgumentException("다른 TNT 시스템의 폭발 변경 계획입니다");
        }
    }

    private long reserveNextTntId() {
        requireUsableTntId(nextId);
        long allocated = nextId;
        long successor = Math.addExact(nextId, 1L);
        if (successor >= Long.MAX_VALUE) {
            throw new IllegalStateException("TNT identity allocator is exhausted");
        }
        nextId = successor;
        return allocated;
    }

    private static long nextTntIdAfter(long highWater) {
        if (highWater < 0 || highWater >= Long.MAX_VALUE) {
            throw new IllegalStateException("TNT high-water is invalid or exhausted");
        }
        long successor = Math.addExact(highWater, 1L);
        if (successor >= Long.MAX_VALUE) {
            throw new IllegalStateException("TNT identity allocator is exhausted");
        }
        return successor;
    }

    private static void requireUsableTntId(long tntId) {
        if (!isUsableTntId(tntId)) {
            throw new IllegalStateException("TNT identity is invalid or exhausted");
        }
    }

    private static boolean isUsableTntId(long tntId) {
        return tntId > 0 && tntId < Long.MAX_VALUE;
    }

    synchronized PersistenceBatch beginPersistenceBatch() {
        if (persistence == null || persistenceInFlight
                || (changedPersistenceVersions.isEmpty() && removedPersistenceVersions.isEmpty())) {
            return null;
        }
        persistenceInFlight = true;
        long revision = persistenceRevision;
        List<PrimedTntSnapshot> changed = changedPersistenceVersions.entrySet().stream()
                .filter(entry -> entry.getValue() <= revision)
                .map(entry -> entities.get(entry.getKey()))
                .filter(java.util.Objects::nonNull)
                .map(PrimedTntEntity::snapshot)
                .toList();
        List<Long> removed = removedPersistenceVersions.entrySet().stream()
                .filter(entry -> entry.getValue() <= revision)
                .map(Map.Entry::getKey)
                .toList();
        return new PersistenceBatch(revision, changed, removed, persistence);
    }

    synchronized void finishPersistenceBatch(long revision, boolean success) {
        if (success) {
            changedPersistenceVersions.entrySet().removeIf(entry -> entry.getValue() <= revision);
            removedPersistenceVersions.entrySet().removeIf(entry -> entry.getValue() <= revision);
        }
        persistenceInFlight = false;
    }

    synchronized boolean hasPendingPersistence() {
        return persistenceInFlight || !changedPersistenceVersions.isEmpty()
                || !removedPersistenceVersions.isEmpty();
    }

    synchronized PrimedTntPersistenceService persistenceService() {
        return persistence;
    }

    private synchronized void markPersistenceChanged(long id) {
        long revision = ++persistenceRevision;
        changedPersistenceVersions.put(id, revision);
        removedPersistenceVersions.remove(id);
    }

    private synchronized void markPersistenceRemoved(long id) {
        long revision = ++persistenceRevision;
        changedPersistenceVersions.remove(id);
        removedPersistenceVersions.put(id, revision);
    }

    private PrimedTntDto dto(PrimedTntEntity entity) {
        return new PrimedTntDto(entity.id, entity.x, entity.y, entity.z,
                entity.vx * 20.0, entity.vy * 20.0, entity.vz * 20.0, entity.fuse);
    }

    private void broadcast(Object message) {
        if (rt.ctx().broadcaster() != null) {
            rt.ctx().broadcaster().enqueueBroadcastFromTick(rt.worldId(), message);
        }
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    record PersistenceBatch(long revision, List<PrimedTntSnapshot> snapshots,
            List<Long> removedTntIds, PrimedTntPersistenceService persistence) {}

    record TntMutation(PrimedTntSnapshot expected, PrimedTntSnapshot committed) {
        TntMutation {
            if ((expected == null) == (committed == null)) {
                throw new IllegalArgumentException("TNT 변경은 제거 또는 추가 중 하나여야 합니다");
            }
        }
    }

    private enum ExplosionPlanState { OPEN, COMMITTED, ROLLED_BACK }

    static final class ExplosionMutationPlan {
        private final PrimedTntSystem owner;
        private final long expectedNextId;
        private final long nextIdAfterBatch;
        private final PrimedTntSnapshot explodingSource;
        private final List<PrimedTntSnapshot> chainedTnt;
        private ExplosionPlanState state = ExplosionPlanState.OPEN;

        private ExplosionMutationPlan(PrimedTntSystem owner, long expectedNextId,
                long nextIdAfterBatch, PrimedTntSnapshot explodingSource,
                List<PrimedTntSnapshot> chainedTnt) {
            this.owner = owner;
            this.expectedNextId = expectedNextId;
            this.nextIdAfterBatch = nextIdAfterBatch;
            this.explodingSource = explodingSource;
            this.chainedTnt = chainedTnt;
        }

        List<TntMutation> mutations() {
            List<TntMutation> result = new ArrayList<>(chainedTnt.size() + 1);
            if (explodingSource != null) result.add(new TntMutation(explodingSource, null));
            for (PrimedTntSnapshot snapshot : chainedTnt) {
                result.add(new TntMutation(null, snapshot));
            }
            return List.copyOf(result);
        }
    }

}
