package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.gameexpert.engine.Fluids.*;

import com.gameexpert.terrain.Blocks;

/** 이벤트 구동 유체 시뮬레이터(월드당 1개, 틱 스레드 전용). */
public final class FluidSimulator {

    /** 물 갱신 간격. 한 서버 틱은 100ms이며 물은 2틱 간격으로 전파합니다. */
    public static final int WATER_TICK_INTERVAL = 2;
    /** 오버월드 용암 갱신 간격. */
    public static final int LAVA_TICK_INTERVAL = 15;
    /**
     * 한 틱이 무조건 처리하는 유체 작업량이자 생성·정착 배치의 고정 단위입니다.
     * 남은 due 셀은 같은 순서로 다음 틱에 이어서 처리합니다.
     */
    public static final int MAX_UPDATES_PER_TICK = 16;
    /**
     * 활성 유체가 많을 때 한 틱이 올라갈 수 있는 최대 작업량입니다.
     * 상한 근거: 유체 갱신 하나는 최대 5칸 읽기 + 최대 5칸 쓰기이고, 10 TPS에서 64건은
     * 초당 640건입니다. 이는 폭포 한 줄기가 한 틱 안에 한 단 내려가는 데 필요한 양을
     * 덮으면서도 유체 위상이 틱 예산을 잠식하지 않는 선입니다.
     */
    public static final int PEAK_UPDATES_PER_TICK = 64;
    /** 활성 셀 수를 작업량으로 바꾸는 제수. 512셀에서 상한에 닿습니다. */
    private static final int UPDATE_BUDGET_SCALE = 8;
    /** 하한을 넘어선 추가 작업에만 적용하는 유체 위상 시간 예산. */
    public static final long EXTRA_UPDATE_BUDGET_NANOS = 2_000_000L;
    /** 바닐라 FlowingFluid의 하강 경로 탐색 깊이: 물 4, 오버월드 용암 2. */
    private static final int WATER_SLOPE_FIND_DISTANCE = 4;
    private static final int LAVA_SLOPE_FIND_DISTANCE = 2;
    /** 첫 수평 후보까지 포함한 물의 최대 읽기 거리. */
    private static final int MAX_HORIZONTAL_READ_DISTANCE = WATER_SLOPE_FIND_DISTANCE + 1;
    private static final int NO_DOWNHILL_PATH = 1000;
    private static final int[] HORIZONTAL_X = {1, -1, 0, 0};
    private static final int[] HORIZONTAL_Z = {0, 0, 1, -1};
    private static final int SLOPE_SCRATCH_RADIUS = MAX_HORIZONTAL_READ_DISTANCE;
    private static final int SLOPE_SCRATCH_WIDTH = SLOPE_SCRATCH_RADIUS * 2 + 1;
    private static final int SLOPE_SCRATCH_CELLS = SLOPE_SCRATCH_WIDTH * SLOPE_SCRATCH_WIDTH;
    private static final int SLOPE_RESULT_STRIDE = (WATER_SLOPE_FIND_DISTANCE + 1) * 4;
    private static final int SLOPE_PASS_CHECKED = 1 << 0;
    private static final int SLOPE_PASSABLE = 1 << 1;
    private static final int SLOPE_FALL_CHECKED = 1 << 2;
    private static final int SLOPE_CAN_FALL = 1 << 3;

    /** 유체 시뮬레이터가 읽고 쓰는 오버레이 월드. */
    public interface FluidWorld {
        int getBlock(int x, int y, int z);

        void setBlock(int x, int y, int z, int blockType);

        default void beforeNaturalFluidReplacement(
                int x, int y, int z, int replacedBlock, boolean dropResources) {
        }

        /** 유체 틱이 cold 청크를 읽어 활성화하지 않도록 하는 비활성 청크 조회. */
        default boolean isChunkActivated(int chunkX, int chunkZ) {
            return true;
        }

        /**
         * carrier 가 심은 {@code waterlogged=true} 칸인가. 바닐라에서 그 칸은 블록이면서
         * 동시에 레벨 8 수원이라({@code BlockBehaviour#getFluidState}) 이웃에 물을 공급한다.
         */
        default boolean isWaterloggedSource(int x, int y, int z) {
            return false;
        }
    }

    private final FluidWorld world;
    // 좌표 객체를 유지하는 단순 버킷 큐. 유체 부트스트랩은 불안정 셀만 넣어 큐 자체를 작게 유지합니다.
    private final TreeMap<Long, LinkedHashSet<BlockPos>> pending = new TreeMap<>();
    // 셀 변경 전 AIR 예약이 변경 후 유체를 같은 틱에 재처리하지 않도록 현재 유효 due tick만 추적합니다.
    private final Map<BlockPos, Long> scheduledAt = new HashMap<>();
    /** Direct chunk index keeps deactivation O(work in that chunk), never O(all historical fluid work). */
    private final Map<Long, LinkedHashSet<BlockPos>> scheduledByChunk = new HashMap<>();
    // cold 경계 때문에 보류된 작업은 청크 활성화 순서와 무관하게 좌표별 named interval을 보존한다.
    private final TreeMap<Long, LinkedHashSet<BlockPos>> deferredByChunk = new TreeMap<>();
    /** A deferred cell touches at most four read-window chunks; remove it through this reverse index, never a global scan. */
    private final Map<BlockPos, LinkedHashSet<Long>> deferredChunksByPosition = new HashMap<>();
    private final Map<BlockPos, Integer> deferredTypes = new HashMap<>();
    private final Map<BlockPos, Integer> scheduledTypes = new HashMap<>();
    private long currentTick;
    private boolean settling;
    private long extraUpdateBudgetNanos = EXTRA_UPDATE_BUDGET_NANOS;
    /** 하나의 slope root에서 네 방향이 공유하는 11×11 고정 메모. */
    private final int[] slopeCellStamps = new int[SLOPE_SCRATCH_CELLS];
    private final byte[] slopeCellFlags = new byte[SLOPE_SCRATCH_CELLS];
    private final int[] slopeResultStamps =
            new int[SLOPE_SCRATCH_CELLS * SLOPE_RESULT_STRIDE];
    private final short[] slopeResults =
            new short[SLOPE_SCRATCH_CELLS * SLOPE_RESULT_STRIDE];
    private int slopeStamp;
    private int slopeRootX;
    private int slopeRootZ;
    private long slopeMemoHits;

    public FluidSimulator(FluidWorld world) {
        this.world = world;
    }

    /** One immutable block write produced by a detached execution of the normal fluid rules. */
    public record PlannedBlockChange(int x, int y, int z, int blockId) {
        public PlannedBlockChange {
            if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) {
                throw new IllegalArgumentException("planned fluid write outside build height");
            }
            if (blockId < 0 || blockId > Blocks.BLOCK_ID_HIGH_WATER) {
                throw new IllegalArgumentException("planned fluid write has invalid block ID");
            }
        }
    }

    /**
     * Executes the complete collision, mixing, source, slope and spread body against a detached
     * overlay and returns its writes without touching the resident world or this simulator's
     * cadence queues. The caller may therefore persist the plan before publishing it.
     */
    public List<PlannedBlockChange> planFluidTick(int x, int y, int z) {
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) {
            throw new IllegalArgumentException("fluid tick outside build height");
        }
        LinkedHashMap<BlockPos, Integer> overlay = new LinkedHashMap<>();
        FluidWorld planningWorld = new FluidWorld() {
            @Override
            public int getBlock(int blockX, int blockY, int blockZ) {
                Integer planned = overlay.get(new BlockPos(blockX, blockY, blockZ));
                return planned != null ? planned : world.getBlock(blockX, blockY, blockZ);
            }

            @Override
            public void setBlock(int blockX, int blockY, int blockZ, int blockType) {
                BlockPos position = new BlockPos(blockX, blockY, blockZ);
                int original = world.getBlock(blockX, blockY, blockZ);
                if (blockType == original) overlay.remove(position);
                else overlay.put(position, blockType);
            }

            @Override
            public boolean isChunkActivated(int chunkX, int chunkZ) {
                return world.isChunkActivated(chunkX, chunkZ);
            }

            @Override
            public boolean isWaterloggedSource(int blockX, int blockY, int blockZ) {
                return !overlay.containsKey(new BlockPos(blockX, blockY, blockZ))
                        && world.isWaterloggedSource(blockX, blockY, blockZ);
            }
        };
        FluidSimulator detached = new FluidSimulator(planningWorld);
        detached.process(new BlockPos(x, y, z));
        return overlay.entrySet().stream()
                .map(entry -> new PlannedBlockChange(entry.getKey().x(), entry.getKey().y(),
                        entry.getKey().z(), entry.getValue()))
                .toList();
    }

    public void setClock(long tickNo) {
        this.currentTick = tickNo;
    }

    /**
     * 유체 위상 시간 예산을 바꿉니다. 처리량 계약만 검증하는 결정적 테스트는
     * {@link Long#MAX_VALUE}를 넣어 벽시계 의존을 뺍니다.
     */
    public void setExtraUpdateBudgetNanos(long nanos) {
        this.extraUpdateBudgetNanos = nanos;
    }

    /**
     * 활성 유체 셀 수에 비례한 한 틱 작업량. 하한은 {@link #MAX_UPDATES_PER_TICK},
     * 상한은 {@link #PEAK_UPDATES_PER_TICK}입니다. 생성·정착은 이 식을 쓰지 않고
     * 언제나 고정 배치라서 지형 산출물이 예산 변경에 흔들리지 않습니다.
     */
    public static int updateBudgetFor(int activeCells) {
        int scaled = (activeCells + UPDATE_BUDGET_SCALE - 1) / UPDATE_BUDGET_SCALE;
        return Math.max(MAX_UPDATES_PER_TICK, Math.min(PEAK_UPDATES_PER_TICK, scaled));
    }

    public void drainDue() {
        // 정착(생성 포함)은 결정적이어야 하므로 고정 배치와 무제한 시간을 유지합니다.
        final int budget = settling ? MAX_UPDATES_PER_TICK : updateBudgetFor(pendingCount());
        final long deadline = settling || extraUpdateBudgetNanos == Long.MAX_VALUE
                ? Long.MAX_VALUE
                : System.nanoTime() + extraUpdateBudgetNanos;
        int processed = 0;
        while (!pending.isEmpty() && pending.firstKey() <= currentTick) {
            Map.Entry<Long, LinkedHashSet<BlockPos>> entry = pending.firstEntry();
            long dueTick = entry.getKey();
            Set<BlockPos> due = entry.getValue();
            while (!due.isEmpty()) {
                BlockPos pos = due.iterator().next();
                due.remove(pos);
                if (due.isEmpty()) pending.remove(dueTick);
                if (!scheduledAt.remove(pos, dueTick)) continue;
                Integer type = scheduledTypes.remove(pos);
                removeScheduledChunkReference(pos);
                if (settling || hasActivatedReadWindow(pos)) {
                    process(pos);
                } else {
                    deferUntilChunkActivation(pos, type == null ? WATER : type);
                }
                processed++;
                if (processed >= budget || overTimeBudget(processed, deadline)) break;
            }
            if (processed >= budget || overTimeBudget(processed, deadline)) return;
        }
    }

    /**
     * 시간 예산은 하한을 넘어선 추가 작업만 되돌립니다. 따라서 어떤 부하에서도 한 틱은
     * 최소 {@link #MAX_UPDATES_PER_TICK}건을 처리하고, 기존 진행 속도가 느려지지 않습니다.
     */
    private static boolean overTimeBudget(int processed, long deadline) {
        return deadline != Long.MAX_VALUE
                && processed >= MAX_UPDATES_PER_TICK
                && System.nanoTime() >= deadline;
    }

    /** 외부 편집이 일어난 좌표와 6방 이웃을 같은 스케줄 경로로 재활성화합니다. */
    public void onBlockChanged(int x, int y, int z) {
        activateAround(x, y, z);
    }

    /** 생성기가 명시적으로 표시한 유체 후처리 셀 하나를 주변 조회 없이 예약합니다. */
    public void activateGeneratedFluid(int x, int y, int z, int fluidId) {
        int type = typeOf(fluidId);
        if (type != NONE && y >= Blocks.MIN_Y && y <= Blocks.MAX_Y) {
            schedule(x, y, z, type);
        }
    }

    /** 정상 청크 활성화에서만 보류된 경계 작업을 원래 유체 간격으로 다시 예약한다. */
    public void onChunkActivated(int chunkX, int chunkZ) {
        long activatedKey = chunkKey(chunkX, chunkZ);
        LinkedHashSet<BlockPos> deferred = deferredByChunk.remove(activatedKey);
        if (deferred == null) return;
        for (BlockPos pos : deferred) {
            LinkedHashSet<Long> dependencies = deferredChunksByPosition.get(pos);
            if (dependencies == null || !dependencies.remove(activatedKey)) continue;
            if (!dependencies.isEmpty()) continue;
            deferredChunksByPosition.remove(pos);
            Integer type = deferredTypes.remove(pos);
            if (type == null) continue;
            schedule(pos.x(), pos.y(), pos.z(), type);
        }
    }

    /**
     * Parks only this chunk's scheduled cells when it leaves every player neighborhood.  They retain their fluid
     * type and resume through the existing deterministic deferred path on reactivation; no stale pending bucket
     * remains for drainDue to revisit.
     */
    public void onChunkDeactivated(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        LinkedHashSet<BlockPos> scheduled = scheduledByChunk.get(key);
        if (scheduled == null || scheduled.isEmpty()) return;
        List<BlockPos> parked = new ArrayList<>(scheduled);
        for (BlockPos pos : parked) {
            Integer type = scheduledTypes.get(pos);
            cancelSchedule(pos);
            deferUntilChunkActivation(pos, type == null ? WATER : type);
        }
    }

    /** 오버레이·방송·영속 변경 기록과 유체 이웃 재활성화의 단일 변경 경로. */
    public void applyChange(int x, int y, int z, int id) {
        if (world.getBlock(x, y, z) == id) {
            return;
        }
        // 변경 전 상태(AIR 등)의 due 예약은 무효화하고 변경 후 타입의 named interval로 다시 잡습니다.
        cancelSchedule(new BlockPos(x, y, z));
        world.setBlock(x, y, z, id);
        activateAround(x, y, z);
    }

    /** 양동이는 물과 용암 모두 replaceable 대상의 자원을 먼저 드랍하고 소스를 설치한다. */
    public void applyBucketChange(int x, int y, int z, int id) {
        int current = world.getBlock(x, y, z);
        if (current != AIR && !isFluid(current)) {
            world.beforeNaturalFluidReplacement(x, y, z, current, true);
        }
        applyChange(x, y, z, id);
    }

    private void applyNaturalFlowChange(int x, int y, int z, int id, int type) {
        int current = world.getBlock(x, y, z);
        if (current != AIR && !isFluid(current)) {
            world.beforeNaturalFluidReplacement(
                    x, y, z, current, type == WATER);
        }
        applyChange(x, y, z, id);
    }

    private void activateAround(int x, int y, int z) {
        activate(x, y, z);
        activate(x + 1, y, z);
        activate(x - 1, y, z);
        activate(x, y, z + 1);
        activate(x, y, z - 1);
        activate(x, y + 1, z);
        activate(x, y - 1, z);
    }

    private void activate(int x, int y, int z) {
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) {
            return;
        }
        int id = world.getBlock(x, y, z);
        if (isSolid(id) && !isSubmergedDecoration(id)
                && !world.isWaterloggedSource(x, y, z)) {
            return;
        }
        int type = activationType(x, y, z, id);
        if (type != NONE) {
            schedule(x, y, z, type);
        }
    }

    /* AIR/장식 칸도 실제 유입 유체의 간격을 사용한다. 특히 용암 옆 AIR를 물의 2틱으로 예약하지 않는다. */
    private int activationType(int x, int y, int z, int id) {
        int own = typeOf(id);
        if (own != NONE) {
            return own;
        }
        if (isSubmergedDecoration(id) || world.isWaterloggedSource(x, y, z)) return WATER;
        // carrier waterlogged 이웃도 수원이라 옆 칸을 물 간격으로 깨워야 한다.
        int type = neighborType(fluidView(x, y + 1, z));
        type = mergeType(type, neighborType(fluidView(x + 1, y, z)));
        type = mergeType(type, neighborType(fluidView(x - 1, y, z)));
        type = mergeType(type, neighborType(fluidView(x, y, z + 1)));
        return mergeType(type, neighborType(fluidView(x, y, z - 1)));
    }

    private static int neighborType(int id) {
        return typeOf(id);
    }

    // 물과 용암 양쪽이 닿으면 빠른 물 갱신이 혼합 반응을 깨우도록 물 우선.
    private static int mergeType(int left, int right) {
        if (left == WATER || right == WATER) {
            return WATER;
        }
        return left == LAVA || right == LAVA ? LAVA : NONE;
    }

    private void schedule(int x, int y, int z, int type) {
        int delay = type == LAVA ? LAVA_TICK_INTERVAL : WATER_TICK_INTERVAL;
        long dueTick = currentTick + delay;
        BlockPos pos = new BlockPos(x, y, z);
        Long existing = scheduledAt.get(pos);
        if (existing != null && existing <= dueTick) {
            return;
        }
        if (existing != null) {
            cancelSchedule(pos);
        }
        removeDeferred(pos);
        scheduledAt.put(pos, dueTick);
        scheduledTypes.put(pos, type);
        scheduledByChunk.computeIfAbsent(chunkKey(Math.floorDiv(x, Blocks.CHUNK_X),
                Math.floorDiv(z, Blocks.CHUNK_Z)), ignored -> new LinkedHashSet<>()).add(pos);
        pending.computeIfAbsent(dueTick, key -> new LinkedHashSet<>()).add(pos);
    }

    /** 좌표 예약을 계측 맵과 실제 버킷 양쪽에서 함께 취소한다. */
    private void cancelSchedule(BlockPos pos) {
        removeDeferred(pos);
        Long dueTick = scheduledAt.remove(pos);
        scheduledTypes.remove(pos);
        removeScheduledChunkReference(pos);
        if (dueTick == null) return;
        LinkedHashSet<BlockPos> bucket = pending.get(dueTick);
        if (bucket == null) return;
        bucket.remove(pos);
        if (bucket.isEmpty()) pending.remove(dueTick);
    }

    /** 최장 물 하강 경로가 읽을 수 있는 5칸 경계를 실행 전에 모두 활성 상태로 확인한다. */
    private boolean hasActivatedReadWindow(BlockPos pos) {
        int minChunkX = Math.floorDiv(pos.x() - MAX_HORIZONTAL_READ_DISTANCE, Blocks.CHUNK_X);
        int maxChunkX = Math.floorDiv(pos.x() + MAX_HORIZONTAL_READ_DISTANCE, Blocks.CHUNK_X);
        int minChunkZ = Math.floorDiv(pos.z() - MAX_HORIZONTAL_READ_DISTANCE, Blocks.CHUNK_Z);
        int maxChunkZ = Math.floorDiv(pos.z() + MAX_HORIZONTAL_READ_DISTANCE, Blocks.CHUNK_Z);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.isChunkActivated(chunkX, chunkZ)) return false;
            }
        }
        return true;
    }

    /** cold 청크를 만난 due 작업은 버리지 않고 해당 청크의 정상 활성화까지 결정적으로 보류한다. */
    private void deferUntilChunkActivation(BlockPos pos, int type) {
        removeDeferred(pos);
        deferredTypes.put(pos, type);
        LinkedHashSet<Long> dependencies = new LinkedHashSet<>();
        int minChunkX = Math.floorDiv(pos.x() - MAX_HORIZONTAL_READ_DISTANCE, Blocks.CHUNK_X);
        int maxChunkX = Math.floorDiv(pos.x() + MAX_HORIZONTAL_READ_DISTANCE, Blocks.CHUNK_X);
        int minChunkZ = Math.floorDiv(pos.z() - MAX_HORIZONTAL_READ_DISTANCE, Blocks.CHUNK_Z);
        int maxChunkZ = Math.floorDiv(pos.z() + MAX_HORIZONTAL_READ_DISTANCE, Blocks.CHUNK_Z);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.isChunkActivated(chunkX, chunkZ)) {
                    long key = chunkKey(chunkX, chunkZ);
                    deferredByChunk.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(pos);
                    dependencies.add(key);
                }
            }
        }
        if (dependencies.isEmpty()) {
            deferredTypes.remove(pos);
            schedule(pos.x(), pos.y(), pos.z(), type);
        } else {
            deferredChunksByPosition.put(pos, dependencies);
        }
    }

    private void removeDeferred(BlockPos pos) {
        deferredTypes.remove(pos);
        removeDeferredReferences(pos);
    }

    private void removeDeferredReferences(BlockPos pos) {
        LinkedHashSet<Long> dependencies = deferredChunksByPosition.remove(pos);
        if (dependencies == null) return;
        for (long key : dependencies) {
            LinkedHashSet<BlockPos> deferred = deferredByChunk.get(key);
            if (deferred == null) continue;
            deferred.remove(pos);
            if (deferred.isEmpty()) deferredByChunk.remove(key);
        }
    }

    private void removeScheduledChunkReference(BlockPos pos) {
        long key = chunkKey(Math.floorDiv(pos.x(), Blocks.CHUNK_X),
                Math.floorDiv(pos.z(), Blocks.CHUNK_Z));
        LinkedHashSet<BlockPos> scheduled = scheduledByChunk.get(key);
        if (scheduled == null) return;
        scheduled.remove(pos);
        if (scheduled.isEmpty()) scheduledByChunk.remove(key);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    /**
     * 유체 의미로 본 칸의 ID. waterlogged 칸은 블록을 그대로 둔 채 수원으로 읽힌다.
     * 치환 가능성(isReplaceable)이나 드랍 판정에는 쓰지 않는다 — 그쪽은 실제 블록 ID 가 필요하다.
     */
    private int fluidView(int x, int y, int z) {
        int id = world.getBlock(x, y, z);
        if (id == AIR || isFluid(id)) return id;
        // 단일-ID 수중 식생도 수원을 품는다. 공급 조회에서만 물로 보고 식생은 보존한다.
        if (isSubmergedDecoration(id)) return WATER_SOURCE;
        return world.isWaterloggedSource(x, y, z) ? WATER_SOURCE : id;
    }

    private void process(BlockPos p) {
        int id = world.getBlock(p.x(), p.y(), p.z());
        if (isFluid(id)) {
            if (resolveMixing(p, id)) {
                return;
            }
            if (isSource(id)) {
                spread(p, id);
                return;
            }
            int type = typeOf(id);
            if (type == WATER && canBecomeInfiniteWater(p, id)) {
                applyChange(p.x(), p.y(), p.z(), WATER_SOURCE);
                return;
            }
            int support = computeSupport(p, type);
            if (support <= 0) {
                applyChange(p.x(), p.y(), p.z(), AIR);
                return;
            }
            int desired = flowId(type, Math.min(support, maxLevel(type)));
            if (desired != id) {
                applyChange(p.x(), p.y(), p.z(), desired);
                return;
            }
            spread(p, id);
            return;
        }

        // 수원을 품은 블록은 보존하고, 그 물의 접촉 반응과 이웃 확산만 처리한다.
        if (isSubmergedDecoration(id) || world.isWaterloggedSource(p.x(), p.y(), p.z())) {
            resolveMixing(p, WATER_SOURCE);
            spread(p, WATER_SOURCE);
            return;
        }
        if (!isReplaceable(id)) {
            return;
        }
        // 바닐라 waterlogged 블록은 물이 들어와도 파괴·드랍되지 않는다(Fluids#isWaterloggable).
        if (Fluids.isWaterloggable(id) && inflowType(p) != LAVA) {
            return;
        }
        if (canBecomeInfiniteWater(p, id)) {
            applyNaturalFlowChange(p.x(), p.y(), p.z(), WATER_SOURCE, WATER);
            return;
        }
        int type = inflowType(p);
        if (type == NONE) {
            return;
        }
        int level = computeSupport(p, type);
        if (level >= 1) {
            applyNaturalFlowChange(
                    p.x(), p.y(), p.z(),
                    flowId(type, Math.min(level, maxLevel(type))), type);
        }
    }

    /** 물/용암 접촉을 모두 applyChange로 처리해 방송·diff·이웃 재활성화를 보장합니다. */
    private boolean resolveMixing(BlockPos p, int id) {
        if (isWater(id)) {
            // 위쪽 용암은 아래로 물에 들어오는 STONE 규칙이 우선한다.
            replaceLavaSourceWithObsidian(p.x() + 1, p.y(), p.z());
            replaceLavaSourceWithObsidian(p.x() - 1, p.y(), p.z());
            replaceLavaSourceWithObsidian(p.x(), p.y(), p.z() + 1);
            replaceLavaSourceWithObsidian(p.x(), p.y(), p.z() - 1);
            replaceLavaSourceWithObsidian(p.x(), p.y() - 1, p.z());
            return false;
        }

        // 아래 방향으로 물에 들어가는 용암은 접촉 면의 물을 STONE으로 굳힌다.
        if (isWater(fluidView(p.x(), p.y() - 1, p.z()))) {
            // FluidState-like source views must not replace their waterlogged or vegetation host.
            if (isWater(world.getBlock(p.x(), p.y() - 1, p.z()))) {
                applyChange(p.x(), p.y() - 1, p.z(), STONE);
            }
            return true;
        }

        boolean touchesWater = isWater(fluidView(p.x() + 1, p.y(), p.z()))
                || isWater(fluidView(p.x() - 1, p.y(), p.z()))
                || isWater(fluidView(p.x(), p.y(), p.z() + 1))
                || isWater(fluidView(p.x(), p.y(), p.z() - 1))
                || isWater(fluidView(p.x(), p.y() + 1, p.z()));
        if (touchesWater) {
            applyChange(p.x(), p.y(), p.z(), isSource(id) ? OBSIDIAN : COBBLE);
            return true;
        }
        return false;
    }

    private void replaceLavaSourceWithObsidian(int x, int y, int z) {
        if (world.getBlock(x, y, z) == LAVA_SOURCE) {
            applyChange(x, y, z, OBSIDIAN);
        }
    }

    private boolean canBecomeInfiniteWater(BlockPos p, int id) {
        if (!(isReplaceable(id) || (isWater(id) && !isSource(id)))) {
            return false;
        }
        int sources = 0;
        sources += fluidView(p.x() + 1, p.y(), p.z()) == WATER_SOURCE ? 1 : 0;
        sources += fluidView(p.x() - 1, p.y(), p.z()) == WATER_SOURCE ? 1 : 0;
        sources += fluidView(p.x(), p.y(), p.z() + 1) == WATER_SOURCE ? 1 : 0;
        sources += fluidView(p.x(), p.y(), p.z() - 1) == WATER_SOURCE ? 1 : 0;
        if (sources < 2) {
            return false;
        }
        int below = world.getBlock(p.x(), p.y() - 1, p.z());
        return isSolid(below) || below == WATER_SOURCE;
    }

    /** 위에서 낙하하면 가득 찬 레벨, 그 외에는 바닥을 짚은 수평 이웃의 feed−1 중 최대. */
    private int computeSupport(BlockPos p, int type) {
        int support = 0;
        if (hasVerticalFeed(p, type)) {
            support = maxLevel(type);
        }
        support = Math.max(support, horizontalSupport(p, type));
        return Math.min(support, maxLevel(type));
    }

    private boolean hasVerticalFeed(BlockPos p, int type) {
        return hasVerticalFeed(p.x(), p.y(), p.z(), type);
    }

    private boolean hasVerticalFeed(int x, int y, int z, int type) {
        return typeOf(fluidView(x, y + 1, z)) == type;
    }

    private int horizontalSupport(BlockPos p, int type) {
        int best = 0;
        best = Math.max(best, selectedFeedFrom(p.x() + 1, p.y(), p.z(), type, 1));
        best = Math.max(best, selectedFeedFrom(p.x() - 1, p.y(), p.z(), type, 0));
        best = Math.max(best, selectedFeedFrom(p.x(), p.y(), p.z() + 1, type, 3));
        best = Math.max(best, selectedFeedFrom(p.x(), p.y(), p.z() - 1, type, 2));
        return best;
    }

    /**
     * AIR 이웃도 변경 시점에 미리 예약되므로 단순히 가장 강한 인접 유체를 받으면 소스가 고른 하강
     * 방향을 무시하고 다시 사방으로 찹니다. 공급 유체의 최단 하강 방향에 이 셀이 포함될 때만 feed를
     * 인정해 소스 처리와 이웃 AIR 처리의 결과를 동일하게 유지합니다.
     */
    private int selectedFeedFrom(int x, int y, int z, int type, int directionTowardTarget) {
        int feed = feedFrom(x, y, z, type);
        if (feed <= 0) return 0;
        int id = fluidView(x, y, z);
        int out = hasVerticalFeed(x, y, z, type) ? maxLevel(type) : feedLevel(id) - 1;
        if (out < 1) return 0;
        int directions = downhillDirectionMask(x, y, z, type, out, true);
        return (directions & (1 << directionTowardTarget)) != 0 ? feed : 0;
    }

    // 절벽에서 아래로 떨어지는 이웃은 같은 높이에 옆으로도 공급하지 않는다(수직 우선).
    private int feedFrom(int x, int y, int z, int type) {
        int id = fluidView(x, y, z);
        if (typeOf(id) != type || canFall(x, y, z, type)) {
            return 0;
        }
        if (hasVerticalFeed(x, y, z, type)) {
            return maxLevel(type);
        }
        return feedLevel(id) - 1;
    }

    private boolean canFall(int x, int y, int z, int type) {
        int below = fluidView(x, y - 1, z);
        return (isReplaceable(below) && !waterKeeps(type, below))
                || (typeOf(below) == type && !isSource(below));
    }

    private int inflowType(BlockPos p) {
        int above = fluidView(p.x(), p.y() + 1, p.z());
        if (isFluid(above)) {
            return typeOf(above);
        }
        int water = horizontalSupport(p, WATER);
        int lava = horizontalSupport(p, LAVA);
        if (water <= 0 && lava <= 0) {
            return NONE;
        }
        return water >= lava ? WATER : LAVA;
    }

    /** 수직 우선: 낙하 칸은 항상 최대 흐름 레벨로 전달하고, 바닥에 닿은 다음 갱신부터 수평 감쇠합니다. */
    private void spread(BlockPos origin, int id) {
        int type = typeOf(id);
        int fallLevel = maxLevel(type);
        int x = origin.x();
        int y = origin.y();
        int z = origin.z();

        int below = fluidView(x, y - 1, z);
        if (type == LAVA && isWater(below)) {
            if (isWater(world.getBlock(x, y - 1, z))) {
                applyChange(x, y - 1, z, STONE);
            }
            return;
        }
        if (isReplaceable(below) && !waterKeeps(type, below)) {
            applyNaturalFlowChange(x, y - 1, z, flowId(type, fallLevel), type);
            return;
        }
        if (typeOf(below) == type && !isSource(below)) {
            if (levelOf(below) < fallLevel) {
                applyChange(x, y - 1, z, flowId(type, fallLevel));
            } else {
                // 이미 채워진 낙하 열도 그 아래쪽 끝을 다음 named interval에 계속 깨웁니다.
                activate(x, y - 1, z);
            }
            return;
        }

        // 낙하 열의 착지 셀은 새 수평 확산의 시작점이다. 저장된 flow ID를 다시 감쇠하면
        // 착지와 동시에 한 레벨을 잃으므로, 위에서 수직 공급될 때는 최대 레벨을 그대로 보낸다.
        int out = hasVerticalFeed(origin, type) ? maxLevel(type) : feedLevel(id) - 1;
        if (out < 1) {
            return;
        }
        int directions = downhillSpreadMask(x, y, z, type, out);
        for (int direction = 0; direction < HORIZONTAL_X.length; direction++) {
            if ((directions & (1 << direction)) == 0) continue;
            spreadInto(x + HORIZONTAL_X[direction], y, z + HORIZONTAL_Z[direction], type, out);
        }
    }

    /**
     * 바닐라 getSpread와 같은 선택 규칙: 바로 아래 구멍은 비용 0, 그 외에는 제한 깊이 안의
     * 최단 하강 경로 비용을 사용하며 최솟값 동률 방향을 모두 남깁니다. 구멍이 전혀 없으면
     * 통과 가능한 모든 방향의 비용이 1000으로 동률이므로 평지에서는 기존처럼 사방 확산합니다.
     */
    private int downhillSpreadMask(int x, int y, int z, int type, int level) {
        return downhillDirectionMask(x, y, z, type, level, false);
    }

    private int downhillDirectionMask(int x, int y, int z, int type, int level,
            boolean includeStableFluid) {
        beginSlopeProbe(x, z);
        int best = NO_DOWNHILL_PATH;
        int mask = 0;
        for (int direction = 0; direction < HORIZONTAL_X.length; direction++) {
            int nextX = x + HORIZONTAL_X[direction];
            int nextZ = z + HORIZONTAL_Z[direction];
            boolean passable = includeStableFluid
                    ? memoizedCanPassForSlope(nextX, y, nextZ, type)
                    : canSpreadInto(nextX, y, nextZ, type, level);
            if (!passable) continue;
            int cost = memoizedCanFall(nextX, y, nextZ, type)
                    ? 0
                    : slopeDistance(nextX, y, nextZ, type, 1, direction ^ 1);
            if (cost < best) {
                best = cost;
                mask = 0;
            }
            if (cost == best) mask |= 1 << direction;
        }
        return mask;
    }

    private int slopeDistance(int x, int y, int z, int type, int distance, int cameFrom) {
        int cell = slopeCell(x, z);
        int memo = cell * SLOPE_RESULT_STRIDE + distance * 4 + cameFrom;
        if (slopeResultStamps[memo] == slopeStamp) {
            slopeMemoHits++;
            return slopeResults[memo];
        }
        int best = NO_DOWNHILL_PATH;
        int limit = type == LAVA ? LAVA_SLOPE_FIND_DISTANCE : WATER_SLOPE_FIND_DISTANCE;
        for (int direction = 0; direction < HORIZONTAL_X.length; direction++) {
            if (direction == cameFrom) continue;
            int nextX = x + HORIZONTAL_X[direction];
            int nextZ = z + HORIZONTAL_Z[direction];
            if (!memoizedCanPassForSlope(nextX, y, nextZ, type)) continue;
            if (memoizedCanFall(nextX, y, nextZ, type)) {
                best = distance;
                break;
            }
            if (distance < limit) {
                best = Math.min(best,
                        slopeDistance(nextX, y, nextZ, type, distance + 1, direction ^ 1));
            }
        }
        slopeResultStamps[memo] = slopeStamp;
        slopeResults[memo] = (short) best;
        return best;
    }

    private void beginSlopeProbe(int rootX, int rootZ) {
        slopeStamp++;
        if (slopeStamp == 0) {
            Arrays.fill(slopeCellStamps, 0);
            Arrays.fill(slopeResultStamps, 0);
            slopeStamp = 1;
        }
        slopeRootX = rootX;
        slopeRootZ = rootZ;
    }

    private int slopeCell(int x, int z) {
        int localX = x - slopeRootX + SLOPE_SCRATCH_RADIUS;
        int localZ = z - slopeRootZ + SLOPE_SCRATCH_RADIUS;
        return localX + localZ * SLOPE_SCRATCH_WIDTH;
    }

    private boolean memoizedCanPassForSlope(int x, int y, int z, int type) {
        int cell = slopeCell(x, z);
        int flags = slopeCellFlags[cell] & 0xff;
        if (slopeCellStamps[cell] != slopeStamp) {
            slopeCellStamps[cell] = slopeStamp;
            flags = 0;
        }
        if ((flags & SLOPE_PASS_CHECKED) == 0) {
            flags |= SLOPE_PASS_CHECKED;
            if (canPassForSlope(x, y, z, type)) flags |= SLOPE_PASSABLE;
            slopeCellFlags[cell] = (byte) flags;
        } else {
            slopeMemoHits++;
        }
        return (flags & SLOPE_PASSABLE) != 0;
    }

    private boolean memoizedCanFall(int x, int y, int z, int type) {
        int cell = slopeCell(x, z);
        int flags = slopeCellFlags[cell] & 0xff;
        if (slopeCellStamps[cell] != slopeStamp) {
            slopeCellStamps[cell] = slopeStamp;
            flags = 0;
        }
        if ((flags & SLOPE_FALL_CHECKED) == 0) {
            flags |= SLOPE_FALL_CHECKED;
            if (canFall(x, y, z, type)) flags |= SLOPE_CAN_FALL;
            slopeCellFlags[cell] = (byte) flags;
        } else {
            slopeMemoHits++;
        }
        return (flags & SLOPE_CAN_FALL) != 0;
    }

    long slopeMemoHits() {
        return slopeMemoHits;
    }

    private boolean canPassForSlope(int x, int y, int z, int type) {
        int id = fluidView(x, y, z);
        return (isReplaceable(id) && !waterKeeps(type, id))
                || (typeOf(id) == type && !isSource(id));
    }

    private boolean canSpreadInto(int x, int y, int z, int type, int level) {
        int id = fluidView(x, y, z);
        return (isReplaceable(id) && !waterKeeps(type, id))
                || (typeOf(id) == type && !isSource(id) && levelOf(id) < level);
    }

    /**
     * 물이 waterlogged 블록 칸을 치환·파괴하지 못하게 하는 판정. 바닐라
     * {@code FlowingFluid#spreadTo} 는 {@code LiquidBlockContainer} 칸에 물을 블록 안으로 넣을 뿐
     * {@code beforeDestroyingBlock}(물의 drop 경로)을 타지 않는다. 용암은 waterlog 대상이 아니므로
     * 기존 치환 규칙 그대로다.
     */
    private static boolean waterKeeps(int type, int id) {
        return type == WATER && Fluids.isWaterloggable(id);
    }

    private void spreadInto(int x, int y, int z, int type, int level) {
        if (canSpreadInto(x, y, z, type, level)) {
            applyNaturalFlowChange(x, y, z, flowId(type, level), type);
        }
    }

    public int pendingCount() {
        return scheduledAt.size();
    }

    public List<Long> settle(int maxTicks) {
        List<Long> processedTicks = new ArrayList<>();
        int steps = 0;
        settling = true;
        try {
            while ((!pending.isEmpty() || !deferredTypes.isEmpty()) && steps < maxTicks) {
                if (pending.isEmpty()) {
                    releaseDeferredForSettlement();
                    continue;
                }
                long next = pending.firstKey();
                setClock(next);
                drainDue();
                processedTicks.add(next);
                steps++;
            }
        } finally {
            settling = false;
        }
        return processedTicks;
    }

    /**
     * Detached generation planning uses the production fluid rules without retaining a boxed tick trace.
     * The caller owns this simulator instance and must reject the plan when the bounded run does not settle.
     */
    boolean settleForGeneration(int maxSteps) {
        int steps = 0;
        settling = true;
        try {
            while ((!pending.isEmpty() || !deferredTypes.isEmpty()) && steps < maxSteps) {
                if (pending.isEmpty()) {
                    releaseDeferredForSettlement();
                    continue;
                }
                setClock(pending.firstKey());
                drainDue();
                steps++;
            }
            return pending.isEmpty() && deferredTypes.isEmpty();
        } finally {
            settling = false;
        }
    }

    /** 테스트 전용 정착은 실제 청크 활성화 없이 유한 큐를 끝까지 계산하는 기존 의미를 유지한다. */
    private void releaseDeferredForSettlement() {
        List<BlockPos> positions = new ArrayList<>(deferredTypes.keySet());
        deferredByChunk.clear();
        deferredChunksByPosition.clear();
        for (BlockPos pos : positions) {
            Integer type = deferredTypes.remove(pos);
            if (type != null) schedule(pos.x(), pos.y(), pos.z(), type);
        }
    }
}
