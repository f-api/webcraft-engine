package com.gameexpert.engine;

import com.gameexpert.endgateway.dto.EndGatewayData;
import com.gameexpert.endgateway.service.EndGatewayPersistenceService;
import com.gameexpert.engine.blocks.VoidEndBlockRules;
import com.gameexpert.engine.effect.StatusEffects;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.TerrainAccessor;
import com.gameexpert.world.dimension.DimensionRegistry;
import com.gameexpert.world.dimension.voidend.VoidEndGateways;
import com.gameexpert.ws.dto.WsMessages.EndGatewayBeam;
import com.gameexpert.ws.dto.WsMessages.PlayerTeleportSelf;
import com.gameexpert.ws.dto.WsMessages.LevelEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * [END-GATEWAY] 엔드 차원 엔드 관문의 블록 엔티티 권위(바닐라 {@code EndGatewayBlock} +
 * {@code TheEndGatewayBlockEntity}). 정적판 짝은 {@code StandaloneWorldRuntime} 의 같은 절이다.
 *
 * <ul>
 * <li>{@code entityInside}: 쿨다운이 아닌 관문에 닿은 플레이어는 그 순간 관문을 식히고(40 게임 틱, 블록
 * 이벤트 1 → {@code endGatewayBeam cooldown}) 이동 요청을 연다. 바닐라 {@code getPortalTransitionTime} 은
 * 0 이라 다음 개체 틱에 옮겨진다. WebCraft 는 목적지 청크를 틱 밖에서 준비한 뒤 소유자 턴에 옮긴다.</li>
 * <li>{@code getPortalPosition}: 출구가 없으면 살아 있는 월드에서 {@link VoidEndGateways#findSpawn} →
 * (필요하면 섬) → {@code findTallestBlock(16, true).above(10)} 에 출구 관문을 세우고 두 관문의 출구를 기록한다.
 * 비정확 출구는 매 이동마다 {@code findExitPosition} 을 현재 월드에서 다시 푼다.</li>
 * <li>{@code portalTick}: 나이 % 2400 == 0 이면 식히며 빔을 번쩍인다. 관문마다 적재 나이를 동기화하지 않고
 * 월드 일중 시계(권위 틱 1200 = 게임 틱 2400 주기)로 모든 관문·클라가 같은 순간을 쓴다(CONTRACT §11A).</li>
 * </ul>
 */
final class EndGatewaySystem {
    private static final Logger log = LoggerFactory.getLogger(EndGatewaySystem.class);
    /** 권위 틱 한 번 = 게임 틱 2. */
    private static final int MC_TICKS_PER_TICK = StatusEffects.MC_TICKS_PER_SERVER_TICK;
    /** 주의 빔 주기·길이를 권위 틱의 월드 일중 시계로(2400/2, 40/2). */
    static final int ATTENTION_INTERVAL_TICKS = VoidEndGateways.ATTENTION_INTERVAL / MC_TICKS_PER_TICK;
    static final int COOLDOWN_TICKS = VoidEndGateways.COOLDOWN_TIME / MC_TICKS_PER_TICK;
    /** {@code LevelEvent.ANIMATION_END_GATEWAY_SPAWN}. */
    static final int END_GATEWAY_SPAWN_EVENT = 3000;
    /** 바닐라 blockEvent·levelEvent 전송 반경. */
    private static final double EVENT_RANGE = SoundRules.LEVEL_EVENT_RANGE;
    /** 목적지 준비를 기다리는 최대 시간(초과하면 이번 이동을 버린다; 관문은 이미 식었다). */
    private static final long JOB_TIMEOUT_NANOS = 60_000_000_000L;

    private final WorldRuntime rt;
    /** 영속 관문 블록 엔티티(출구가 정해졌거나 런타임에 생긴 관문). */
    private final Map<BlockPos, EndGatewayData> gateways = new HashMap<>();
    /** 관문별 마지막 명시적 쿨다운 시작 틱(월드 tickCount). */
    private final Map<BlockPos, Long> cooldownStart = new HashMap<>();
    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final ConcurrentLinkedQueue<Prepared> prepared = new ConcurrentLinkedQueue<>();
    private final Map<BlockPos, EndGatewayData> dirty = new LinkedHashMap<>();
    private EndGatewayPersistenceService persistence;
    private boolean flushing;

    EndGatewaySystem(WorldRuntime rt) {
        this.rt = rt;
    }

    /** 영속 행을 복원한다(런타임이 공개되기 전, void_end 월드에서만). */
    void install(EndGatewayPersistenceService service) {
        persistence = service;
        gateways.clear();
        if (service == null || !DimensionRegistry.VOID_END.equals(rt.dimensionKey())) return;
        for (EndGatewayData row : service.loadWorld(rt.worldId())) {
            gateways.put(new BlockPos(row.x(), row.y(), row.z()), row);
        }
    }

    EndGatewayData gateway(int x, int y, int z) {
        return gateways.get(new BlockPos(x, y, z));
    }

    int pendingTeleports() {
        return jobs.size();
    }

    /** 테스트: 생성 귀환 관문처럼 행이 없는 관문을 만든다. */
    void forgetForTest(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        gateways.remove(pos);
        dirty.remove(pos);
    }

    /** 마지막 명시적 쿨다운 시작 틱(없으면 null). */
    Long cooldownStartedAt(BlockPos pos) {
        return cooldownStart.get(pos);
    }

    // ── 관문 생성(용 처치 spawnNewGateway · 출구 관문) ─────────────────────────────

    /**
     * 바닐라 {@code EndGatewayFeature} 를 (x, y, z) 에 놓고 새 블록 엔티티(나이 0 → 생성 빔)를 연다.
     * {@code levelEvent} 가 참이면 {@code EnderDragonFight.spawnNewGateway} 처럼 level event 3000
     * (block.end_gateway.spawn 소리 + 폭발 방출 파티클)을 함께 낸다. 출구 관문(첫 사용)은 소리가 없다.
     * 모든 칸이 상주해야 한다.
     */
    void spawnGateway(int x, int y, int z, int state, EndGatewayData row, boolean levelEvent) {
        for (VoidEndGateways.Cell cell : VoidEndGateways.gatewayCells(x, y, z)) {
            setBlock(cell.x(), cell.y(), cell.z(), cell.block(), cell.block() == Blocks.END_GATEWAY ? state : 0);
        }
        BlockPos pos = new BlockPos(x, y, z);
        cooldownStart.remove(pos);
        EndGatewayData data = row == null ? EndGatewayData.withoutExit(x, y, z) : row;
        gateways.put(pos, data);
        dirty.put(pos, data);
        broadcastNear(x + 0.5, y + 0.5, z + 0.5, new EndGatewayBeam("spawn", x, y, z));
        if (levelEvent) {
            // ServerLevel.levelEvent(3000): 블록 좌표(정수)에서 64 블록 미만.
            broadcastNear(x, y, z, new LevelEvent(END_GATEWAY_SPAWN_EVENT, x, y, z, 0));
        }
    }

    /**
     * 바닐라 {@code Feature.setBlock}(블록 드랍 없음). 다른 블록으로 바뀐 칸의 블록 엔티티 행은 두 권위 공용
     * 교체 청소({@code sweepReplacedBlockEntities} / {@code processReplacedBlockEntities})가 거둔다.
     */
    private void setBlock(int x, int y, int z, int block, int state) {
        int current = WorldTickLoop.residentBlockType(rt.accessor(), x, y, z);
        if (current == WorldTickLoop.UNAVAILABLE_BLOCK) {
            throw new IllegalStateException("end gateway cell is not resident");
        }
        int currentState = current == Blocks.AIR ? 0 : rt.blockStates().get(x, y, z, current);
        if (current == block && currentState == state) return;
        if (current == Blocks.END_GATEWAY && block != Blocks.END_GATEWAY) {
            BlockPos removed = new BlockPos(x, y, z);
            if (gateways.remove(removed) != null) dirty.remove(removed);
        }
        rt.fluidSim().applyChange(x, y, z, block);
        rt.setBlockState(x, y, z, block, state);
        rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) block);
    }

    // ── 틱 ───────────────────────────────────────────────────────────────────────

    void tick(long tickNo) {
        if (!DimensionRegistry.VOID_END.equals(rt.dimensionKey())) return;
        drainPrepared();
        long now = rt.clock().tickCount();
        for (PlayerTickState player : rt.players().values()) {
            Job job = jobs.get(player.nickname());
            if (job != null) {
                if (player.isDead() || System.nanoTime() - job.startNanos > JOB_TIMEOUT_NANOS) {
                    jobs.remove(player.nickname());
                    if(job.pearl!=null) job.pearl.gatewayPending=false;
                }
                continue;
            }
            if (player.isDead()) continue;
            int[] contact = VoidEndBlockRules.endGatewayContact(player.x(), player.y(), player.z(),
                    (qx, qy, qz) -> WorldTickLoop.residentBlockType(rt.accessor(), qx, qy, qz));
            if (contact == null) continue;
            BlockPos pos = new BlockPos(contact[0], contact[1], contact[2]);
            if (coolingDown(pos, now)) continue;
            triggerCooldown(pos, now);
            start(player, pos, now);
        }
        jobs.keySet().removeIf(nickname -> !rt.players().containsKey(nickname));
        flushDirty();
    }

    /**
     * {@code isCoolingDown}: 명시적 쿨다운(40 게임 틱) 또는 주의 빔(월드 일중 시계 % 1200 < 20 권위 틱).
     * 바닐라처럼 주의 빔은 그 순간 관문이 이미 식는 중이면 새로 시작하지 않는다.
     */
    boolean coolingDown(BlockPos pos, long now) {
        Long start = cooldownStart.get(pos);
        if (start != null && now - start < COOLDOWN_TICKS) return true;
        long phase = Math.floorMod(rt.clock().worldTime(), (long) ATTENTION_INTERVAL_TICKS);
        if (phase >= COOLDOWN_TICKS) return false;
        long attentionStart = now - phase;
        return start == null || start + COOLDOWN_TICKS <= attentionStart;
    }

    /** {@code triggerCooldown}: 쿨다운 40 게임 틱 + 블록 이벤트 1(64 블록 안 플레이어의 빔 번쩍임). */
    private void triggerCooldown(BlockPos pos, long now) {
        cooldownStart.put(pos, now);
        broadcastNear(pos.x(), pos.y(), pos.z(), new EndGatewayBeam("cooldown", pos.x(), pos.y(), pos.z()));
    }

    private void broadcastNear(double x, double y, double z, Object message) {
        for (PlayerTickState listener : rt.players().values()) {
            if (SoundRules.audible(x, y, z, listener.x(), listener.y(), listener.z(), EVENT_RANGE)) {
                sendTo(listener, message);
            }
        }
    }

    private void sendTo(PlayerTickState player, Object message) {
        var session = rt.session(player.nickname());
        if (session != null) rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session, message);
    }

    // ── 이동 요청 ────────────────────────────────────────────────────────────────

    private enum Stage { RAY, SPAWN_NEIGHBORHOOD, EXIT_POSITION }

    private static final class Job {
        final String nickname;
        final BlockPos gateway;
        com.gameexpert.engine.mob.ProjectileSim pearl;
        final long startNanos = System.nanoTime();
        Stage stage;
        final Set<Long> awaiting = new HashSet<>();
        final List<TerrainAccessor.PreparedChunk> ready = new ArrayList<>();
        VoidEndGateways.SpawnSearch spawn;
        int[] exit;

        Job(String nickname, BlockPos gateway) {
            this.nickname = nickname;
            this.gateway = gateway;
        }
    }

    private record Prepared(Job job, long key, TerrainAccessor.PreparedChunk chunk, RuntimeException failure) {
    }

    void travelPearl(com.gameexpert.engine.mob.ProjectileSim pearl) {
        PlayerTickState player=rt.players().get(pearl.shooterNickname());
        if (player==null || player.isDead()) {pearl.gatewayPending=false;return;}
        if (jobs.containsKey(player.nickname())) return;
        BlockPos pos=new BlockPos(pearl.gatewayX,pearl.gatewayY,pearl.gatewayZ);
        long now=rt.clock().tickCount();
        if (coolingDown(pos,now)) {pearl.gatewayPending=false;return;}
        triggerCooldown(pos,now);
        start(player,pos,now,pearl);
    }

    private void arrive(PlayerTickState player, double[] target, com.gameexpert.engine.mob.ProjectileSim pearl) {
        if (pearl==null) {teleport(player,target);return;}
        // EndGatewayBlock.getPortalDestination moves the pearl, clearing its velocity. The owner follows on impact.
        pearl.x=target[0];pearl.y=target[1];pearl.z=target[2];
        pearl.vx=0;pearl.vy=0;pearl.vz=0;pearl.gatewayPending=false;
        rt.mobSystem().relocatePearl(pearl);
    }

    private void start(PlayerTickState player, BlockPos pos, long now) {start(player,pos,now,null);}
    private void start(PlayerTickState player, BlockPos pos, long now, com.gameexpert.engine.mob.ProjectileSim pearl) {
        EndGatewayData row = gateways.get(pos);
        if (row == null) {
            int state = rt.blockStates().get(pos.x(), pos.y(), pos.z(), Blocks.END_GATEWAY);
            if (state == VoidEndBlockRules.GATEWAY_RETURN_TO_CENTER) {
                // worldgen/feature/end_gateway_return: exit [100, 50, 0], exact.
                row = EndGatewayData.withExit(pos.x(), pos.y(), pos.z(), 100, 50, 0, true);
            }
        }
        if (row != null && row.hasExit() && row.exact()) {
            arrive(player, VoidEndGateways.bottomCenter(row.exitX(), row.exitY(), row.exitZ()),pearl);
            return;
        }
        Job job = new Job(player.nickname(), pos);
        job.pearl=pearl;
        jobs.put(player.nickname(), job);
        if (row != null && row.hasExit()) {
            job.exit = new int[] {row.exitX(), row.exitY(), row.exitZ()};
            request(job, Stage.EXIT_POSITION, box(VoidEndGateways.exitPositionNeighborhood(row.exitX(), row.exitZ())));
        } else {
            request(job, Stage.RAY, VoidEndGateways.rayChunks(pos.x(), pos.z()));
        }
    }

    private static List<int[]> box(int[] bounds) {
        List<int[]> chunks = new ArrayList<>();
        for (int cz = bounds[1]; cz <= bounds[3]; cz++) {
            for (int cx = bounds[0]; cx <= bounds[2]; cx++) chunks.add(new int[] {cx, cz});
        }
        return chunks;
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    /** 비상주 청크를 청크 준비 작업자에게 넘긴다. 모두 상주하면 바로 다음 단계로 간다. */
    private void request(Job job, Stage stage, List<int[]> chunks) {
        job.stage = stage;
        job.awaiting.clear();
        job.ready.clear();
        List<int[]> missing = new ArrayList<>();
        for (int[] chunk : chunks) {
            if (!rt.accessor().isChunkResident(chunk[0], chunk[1]) && job.awaiting.add(key(chunk[0], chunk[1]))) {
                missing.add(chunk);
            }
        }
        if (missing.isEmpty()) {
            advance(job);
            return;
        }
        for (int[] chunk : missing) {
            int cx = chunk[0];
            int cz = chunk[1];
            try {
                rt.submitDetachedChunkPreparation(cx, cz, prepared -> this.prepared.add(
                        new Prepared(job, key(cx, cz), prepared, null)),
                        failure -> this.prepared.add(new Prepared(job, key(cx, cz), null, failure)));
            } catch (RejectedExecutionException saturated) {
                this.prepared.add(new Prepared(job, key(cx, cz), null, saturated));
            }
        }
    }

    private void drainPrepared() {
        for (Prepared done; (done = prepared.poll()) != null; ) {
            Job job = done.job();
            if (jobs.get(job.nickname) != job || !job.awaiting.remove(done.key())) continue;
            if (done.failure() != null) {
                log.warn("엔드 관문 목적지 청크 준비 실패: world={} player={}", rt.worldId(), job.nickname,
                        done.failure());
                jobs.remove(job.nickname);
            if(job.pearl!=null) job.pearl.gatewayPending=false;
                continue;
            }
            job.ready.add(done.chunk());
            if (job.awaiting.isEmpty()) {
                for (TerrainAccessor.PreparedChunk chunk : job.ready) rt.accessor().adoptPreparedChunkForSnapshot(chunk);
                job.ready.clear();
                advance(job);
            }
        }
    }

    /** 현재 단계의 청크가 모두 상주했다. */
    private void advance(Job job) {
        PlayerTickState player = rt.players().get(job.nickname);
        if (player == null || player.isDead() || jobs.get(job.nickname) != job) {
            jobs.remove(job.nickname);
            if(job.pearl!=null) job.pearl.gatewayPending=false;
            return;
        }
        VoidEndGateways.Level level = level();
        switch (job.stage) {
            case RAY -> {
                job.spawn = VoidEndGateways.findSpawn(level, job.gateway.x(), job.gateway.z());
                request(job, Stage.SPAWN_NEIGHBORHOOD,
                        box(VoidEndGateways.exitNeighborhood(job.spawn.x(), job.spawn.z())));
            }
            case SPAWN_NEIGHBORHOOD -> {
                createExit(job, level);
                request(job, Stage.EXIT_POSITION, box(VoidEndGateways.exitPositionNeighborhood(job.exit[0], job.exit[2])));
            }
            case EXIT_POSITION -> {
                jobs.remove(job.nickname);
            if(job.pearl!=null) job.pearl.gatewayPending=false;
                int[] target = VoidEndGateways.findExitPosition(level, job.exit[0], job.exit[1], job.exit[2]);
                arrive(player, VoidEndGateways.bottomCenter(target[0], target[1], target[2]),job.pearl);
            }
        }
    }

    /**
     * {@code findOrCreateValidTeleportPos} 의 뒤 절반과 {@code getPortalPosition} 의 관문 배치. 그 사이 다른
     * 요청이 같은 관문의 출구를 먼저 정했으면 그 출구를 쓴다.
     */
    private void createExit(Job job, VoidEndGateways.Level level) {
        EndGatewayData current = gateways.get(job.gateway);
        if (current != null && current.hasExit()) {
            job.exit = new int[] {current.exitX(), current.exitY(), current.exitZ()};
            return;
        }
        VoidEndGateways.SpawnSearch spawn = job.spawn;
        if (spawn.island()) {
            VoidEndGateways.placeIsland((x, y, z, block) -> setBlock(x, y, z, block, 0), spawn.x(), spawn.y(), spawn.z());
        }
        int[] tallest = VoidEndGateways.findTallestBlock(level, spawn.x(), spawn.y(), spawn.z(), 16, true);
        int[] exit = {tallest[0], tallest[1] + VoidEndGateways.GATEWAY_HEIGHT_ABOVE_SURFACE, tallest[2]};
        BlockPos origin = job.gateway;
        spawnGateway(exit[0], exit[1], exit[2], VoidEndBlockRules.GATEWAY_EXIT_RETURN,
                EndGatewayData.withExit(exit[0], exit[1], exit[2], origin.x(), origin.y(), origin.z(), false), false);
        EndGatewayData updated = EndGatewayData.withExit(origin.x(), origin.y(), origin.z(),
                exit[0], exit[1], exit[2], false);
        gateways.put(origin, updated);
        dirty.put(origin, updated);
        job.exit = exit;
    }

    private void teleport(PlayerTickState player, double[] target) {
        player.forcePose(target[0], target[1], target[2], player.yaw(), player.pitch());
        sendTo(player, new PlayerTeleportSelf(target[0], target[1], target[2]));
        PlayerRelocation.publish(rt, player);
    }

    private VoidEndGateways.Level level() {
        return new VoidEndGateways.Level() {
            @Override
            public int getBlock(int x, int y, int z) {
                int block = WorldTickLoop.residentBlockType(rt.accessor(), x, y, z);
                return block == WorldTickLoop.UNAVAILABLE_BLOCK ? Blocks.AIR : block;
            }

            @Override
            public int getState(int x, int y, int z) {
                int block = getBlock(x, y, z);
                return block == Blocks.AIR ? 0 : rt.blockStates().get(x, y, z, block);
            }

            @Override
            public boolean fullBlock(int block, int state) {
                return BuildingBlockRules.isFullCollisionShape(block, state);
            }
        };
    }

    // ── 영속 ─────────────────────────────────────────────────────────────────────

    private void flushDirty() {
        for (List<EndGatewayData> done; (done = flushed.poll()) != null; ) {
            for (EndGatewayData row : done) {
                BlockPos pos = new BlockPos(row.x(), row.y(), row.z());
                if (row.equals(dirty.get(pos))) dirty.remove(pos);
            }
            flushing = false;
        }
        if (persistence == null) {
            dirty.clear();
            return;
        }
        if (dirty.isEmpty() || flushing || rt.ctx().persistenceExecutor() == null) return;
        List<EndGatewayData> rows = List.copyOf(dirty.values());
        long worldId = rt.worldId();
        EndGatewayPersistenceService service = persistence;
        flushing = true;
        boolean accepted = rt.ctx().persistenceExecutor().trySubmit(() -> {
            try {
                service.upsert(worldId, rows);
                flushed.add(rows);
            } catch (RuntimeException failure) {
                log.warn("엔드 관문 출구 저장 실패: world={}", worldId, failure);
                flushed.add(List.of());
            }
        });
        if (!accepted) flushing = false;
    }

    /** 저장소가 다 쓴 뒤 틱이 비울 dirty 목록(쓰기 실패는 빈 목록 — 다음 틱에 다시 시도한다). */
    private final ConcurrentLinkedQueue<List<EndGatewayData>> flushed = new ConcurrentLinkedQueue<>();

    /** 월드 폐기 직전 남은 출구 행을 영속 실행기의 폐기 장벽 앞에 넣는다({@code BoatSystem.flushForDisposal} 과 같다). */
    void flushForDisposal() {
        if (persistence == null || dirty.isEmpty()) return;
        List<EndGatewayData> rows = List.copyOf(dirty.values());
        long worldId = rt.worldId();
        EndGatewayPersistenceService service = persistence;
        if (rt.ctx().persistenceExecutor() == null) service.upsert(worldId, rows);
        else rt.ctx().persistenceExecutor().submitFuture(() -> service.upsert(worldId, rows));
        dirty.clear();
    }
}
