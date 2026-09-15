package com.gameexpert.engine;

import com.gameexpert.engine.persistence.tick.FinalCarrierTickScheduler.BlockMutation;
import com.gameexpert.engine.persistence.tick.FinalCarrierTickScheduler.UnavailableNeighborhood;
import com.gameexpert.falling.dto.RuntimeSpeleothemFall;
import com.gameexpert.falling.service.RuntimeFallingPersistence;
import com.gameexpert.terrain.Blocks;
import java.util.*;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owner-thread admission and bounded settlement of player/environment-origin falling ticks. */
final class RuntimeFallingSpeleothems {
    private static final Logger log = LoggerFactory.getLogger(RuntimeFallingSpeleothems.class);
    private static final int MAX_DUE_PER_TURN = 8;
    private final WorldRuntime rt;
    private RuntimeFallingPersistence persistence;
    private final Map<String, RuntimeSpeleothemFall> pending = new LinkedHashMap<>();
    private final Map<String, RuntimeSpeleothemFall> durable = new LinkedHashMap<>();
    private RuntimeSpeleothemFall retryFall;
    private List<BlockMutation> retryPlan;
    private java.util.concurrent.Future<?> writerFuture;
    private final java.util.concurrent.atomic.AtomicReference<List<BlockMutation>> committedPlan =
            new java.util.concurrent.atomic.AtomicReference<>();
    private List<RuntimeSpeleothemFall> protectedFalls = List.of();
    private int preparationStage;
    private int nextDueIndex;

    RuntimeFallingSpeleothems(WorldRuntime rt) { this.rt = rt; }

    synchronized void install(RuntimeFallingPersistence store) {
        persistence = store;
        if (store != null) for (var fall : store.load(rt.worldId())) durable.put(fall.positionKey(), fall);
    }

    synchronized void admit(int x, int y, int z, int blockId) {
        if (persistence == null) return;
        var fall = new RuntimeSpeleothemFall(UUID.randomUUID().toString(), x, y, z,
                blockId, Math.addExact(rt.clock().gameTimeMcTicks(), 2));
        if (durable.containsKey(fall.positionKey()) || pending.containsKey(fall.positionKey())) return;
        int above = y == Blocks.MAX_Y ? Blocks.AIR : WorldTickLoop.residentBlockType(rt.accessor(), x, y + 1, z);
        if (above == WorldTickLoop.UNAVAILABLE_BLOCK) return;
        int aboveState = y == Blocks.MAX_Y ? 0 : rt.blockState(x, y + 1, z, above);
        boolean supported = above == blockId && (aboveState & 1) == 0
                || BlockFaceSturdiness.isFaceSturdy(above, aboveState, BlockFaceSturdiness.DOWN, BlockFaceSturdiness.FULL);
        if (!supported && (rt.blockState(x, y, z, blockId) & 1) == 0) {
            pending.put(fall.positionKey(), fall);
        }
    }

    /** Recompute compact thickness without discarding the existing waterlogged bit. */
    void refreshRun(int x, int rootY, int z, int blockId, boolean upward) {
        if (protectsCell(x, rootY, z)) return;
        int direction = upward ? 1 : -1;
        List<Integer> states = new ArrayList<>();
        for (int y = rootY; y >= Blocks.MIN_Y && y <= Blocks.MAX_Y; y += direction) {
            if (WorldTickLoop.residentBlockType(rt.accessor(), x, y, z) != blockId) break;
            int compact = rt.blockState(x, y, z, blockId);
            if (((compact & 1) != 0) != upward) break;
            states.add(compact);
        }
        int beyond = rootY + direction * states.size();
        boolean merged = beyond >= Blocks.MIN_Y && beyond <= Blocks.MAX_Y
                && WorldTickLoop.residentBlockType(rt.accessor(), x, beyond, z) == blockId
                && ((rt.blockState(x, beyond, z, blockId) & 1) != 0) != upward;
        for (int i = 0; i < states.size(); i++) {
            int thickness = states.size() >= 3 && i == 0 ? 8
                    : states.size() >= 3 && i < states.size() - 2 ? 6
                    : states.size() >= 2 && i == states.size() - 2 ? 4 : merged ? 2 : 0;
            int next = (states.get(i) & ~14) | thickness;
            if (next == states.get(i)) continue;
            int y = rootY + direction * i;
            rt.setBlockState(x, y, z, blockId, next);
            rt.tickBlockChanges().put(new BlockPos(x, y, z), (short) blockId);
        }
    }

    synchronized List<RuntimeSpeleothemFall> admissions() { return List.copyOf(pending.values()); }

    synchronized void admitted(List<RuntimeSpeleothemFall> admissions) {
        for (var fall : admissions) {
            pending.remove(fall.positionKey(), fall);
            durable.putIfAbsent(fall.positionKey(), fall);
        }
    }

    void checkpoint(String identity, List<RuntimeSpeleothemFall> admissions, Runnable source) {
        persistence.checkpoint(rt.worldId(), identity, admissions, source);
        admitted(admissions);
    }

    /**
     * Runs before actions, while the owner holds its normal turn. On unknown results the exact
     * plan remains parked and is retried before accepting a newer live edit in this world.
     */
    boolean pausesSimulation() {
        return preparationStage != 0 || retryFall != null || writerFuture != null;
    }

    /** Only the falling source column is held; unrelated prompt movement/combat remain usable. */
    boolean protectsCell(int x, int y, int z) {
        if (!pausesSimulation()) return false;
        for (var fall : protectedFalls) {
            if (fall.x() == x && fall.z() == z && y <= fall.y() && y >= Blocks.MIN_Y) return true;
        }
        return retryFall != null && retryFall.x() == x && retryFall.z() == z
                && y <= retryFall.y() && y >= Blocks.MIN_Y;
    }

    boolean retryBeforePrelude() {
        return !pausesSimulation() || beforeActions();
    }

    private boolean parkFailure(RuntimeException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof RuntimeFallingPersistence.StaleBeforeState) {
                // A CAS rejection is known rollback. Unknown commits retain the immutable plan.
                retryFall = null;
                retryPlan = null;
                committedPlan.set(null);
                preparationStage = 0;
                protectedFalls = List.of();
                nextDueIndex = 0;
                break;
            }
        }
        log.warn("World {} runtime fall settlement parked for exact retry", rt.worldId(), failure);
        return false;
    }

    boolean beforeActions() {
        if (persistence == null) return true;
        try {
            if (preparationStage == 0 && retryFall == null) {
                List<RuntimeSpeleothemFall> due;
                synchronized (this) {
                    due = new ArrayList<>(durable.values());
                    due.addAll(pending.values());
                }
                due.removeIf(f -> f.dueTick() > rt.clock().gameTimeMcTicks() || !active(f));
                due.sort(Comparator.comparingLong(RuntimeSpeleothemFall::dueTick));
                if (due.isEmpty()) return true;
                protectedFalls = List.copyOf(due.subList(0, Math.min(MAX_DUE_PER_TURN, due.size())));
                nextDueIndex = 0;
                preparationStage = 1;
            }
            if (preparationStage == 1) {
                if (!pollWriter(() -> { })) return false;
                rt.tickLoop().flushPrimedTnt();
                preparationStage = 2;
            }
            if (preparationStage == 2) {
                if (!pollWriter(() -> { })) return false;
                if (rt.tickLoop().hasPendingPrimedTntPersistence()) {
                    rt.tickLoop().flushPrimedTnt();
                    return false;
                }
                // A retried admission checkpoint can precede newer buffered source edits.
                rt.tickLoop().flushPrimedTnt();
                preparationStage = 3;
            }
            if (preparationStage == 3) {
                if (!pollWriter(() -> { })) return false;
                if (rt.tickLoop().hasPendingPrimedTntPersistence()) {
                    rt.tickLoop().flushPrimedTnt();
                    return false;
                }
                preparationStage = 4;
            }
            while (nextDueIndex < protectedFalls.size()) {
                var fall = protectedFalls.get(nextDueIndex);
                if (retryFall == null) {
                    if (!active(fall)) { nextDueIndex++; continue; }
                    synchronized (this) {
                        if (!durable.containsKey(fall.positionKey())) {
                            preparationStage = 1;
                            return false;
                        }
                    }
                    try { retryPlan = fall.plan(rt.runtimeFallingSemanticWorld()); }
                    catch (UnavailableNeighborhood cold) { nextDueIndex++; continue; }
                    retryFall = fall;
                }
                if (!settleRetry()) return false;
                nextDueIndex++;
            }
            preparationStage = 0;
            protectedFalls = List.of();
            nextDueIndex = 0;
            return true;
        } catch (RuntimeException failure) {
            return parkFailure(failure);
        }
    }

    private boolean active(RuntimeSpeleothemFall fall) {
        return rt.accessor().isChunkActivated(Math.floorDiv(fall.x(), Blocks.CHUNK_X),
                Math.floorDiv(fall.z(), Blocks.CHUNK_Z));
    }

    private boolean settleRetry() {
        var fall = retryFall;
        var plan = retryPlan;
        if (!pollWriter(() -> committedPlan.set(persistence.settle(rt.worldId(), fall, plan)))) return false;
        rt.publishRuntimeSpeleothemFall(fall, committedPlan.get());
        synchronized (this) { durable.remove(fall.positionKey(), fall); }
        retryFall = null;
        retryPlan = null;
        committedPlan.set(null);
        return true;
    }

    /** Never wait on an unfinished future on the world owner. */
    private boolean pollWriter(Runnable operation) {
        if (rt.ctx().persistenceExecutor() == null) { operation.run(); return true; }
        if (writerFuture == null) {
            var task = new java.util.concurrent.FutureTask<Void>(operation, null) {
                @Override protected void done() { rt.requestRuntimeFallingContinuation(); }
            };
            writerFuture = task;
            if (!rt.ctx().persistenceExecutor().trySubmit(task)) {
                writerFuture = null;
                return false;
            }
        }
        if (!writerFuture.isDone()) return false;
        var completed = writerFuture;
        writerFuture = null;
        try { completed.get(); return true; }
        catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("runtime falling persistence interrupted", failure);
        } catch (ExecutionException failure) {
            throw new IllegalStateException("runtime falling persistence failed", failure.getCause());
        }
    }
}
