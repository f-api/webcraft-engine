package com.gameexpert.engine.qa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Allocation-free-in-window owner-turn recorder for the exact dense performance QA fixture.
 *
 * <p>The integration contract is intentionally nullable: production keeps no instance and pays one
 * {@code if (window != null)} check. A QA runtime constructs one recorder and one reusable
 * {@link MutableTurnObservation}, fills the observation from existing counters, then calls
 * {@link #record(MutableTurnObservation)}. Arrays are copied and sorted only by {@link #finish()}
 * after measurement has ended.</p>
 */
public final class OwnerTurnPerformanceWindow {

    public static final int WARMUP_TURNS = 100;
    public static final int SAMPLE_TURNS = 600;
    public static final long TICK_BUDGET_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
    public static final long P95_HEADROOM_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    public static final long P99_HEADROOM_NANOS = TimeUnit.MILLISECONDS.toNanos(75);
    public static final long LATENESS_P99_HEADROOM_NANOS = TimeUnit.MILLISECONDS.toNanos(25);

    /** Mirrors the existing WorldTickLoop owner-turn phase order for explicit integration. */
    public enum Phase {
        SAFETY,
        PRELUDE,
        BEGIN_TICK,
        ACTION,
        SMELT,
        CLOCK,
        FLUID,
        RANDOM_TICK,
        MOB,
        NEIGHBOR,
        ITEM,
        BOAT,
        ENVIRONMENT,
        BROADCAST_SAVE,
        LIFECYCLE,
        SNAPSHOT_PREPARATION,
        TREE_PLANNING,
        PROXIMITY,
        CHUNK_DEMAND,
        CHUNK_ADMISSION,
        DECORATION,
        PREPARED_SNAPSHOT,
        SNAPSHOT_DELIVERY,
        SNAPSHOT_DEADLINE
    }

    public enum InvalidReason {
        NONE,
        EXCEPTIONAL_TURN,
        INVALID_OBSERVATION,
        WORKLOAD_MISMATCH,
        TELEMETRY_MISMATCH
    }

    public record ExpectedWorkload(String fixtureId, long fixtureChecksum,
            int fixtureMobCount, int randomTickChunks, int randomTickSamples,
            int maxRandomTickTransitions) {
        public ExpectedWorkload {
            if (fixtureId == null || fixtureId.isBlank() || fixtureMobCount < 0
                    || randomTickChunks < 0 || randomTickSamples < 0
                    || maxRandomTickTransitions < 0) {
                throw new IllegalArgumentException("invalid dense performance workload");
            }
        }

        public static ExpectedWorkload fromPlan(DenseWorldPerformanceFixturePlan plan) {
            if (plan == null) throw new IllegalArgumentException("fixture plan is required");
            return new ExpectedWorkload(plan.id(), plan.checksum(),
                    plan.expectedFixtureMobCount(), plan.expectedRandomTickChunks(),
                    plan.expectedRandomTickSamples(),
                    DenseWorldPerformanceFixturePlan.EXPECTED_MAX_RANDOM_TICK_TRANSITIONS);
        }
    }

    /** Reused mutable carrier; setters return {@code this} so QA wiring stays compact. */
    public static final class MutableTurnObservation {
        private final long[] phaseNanos = new long[Phase.values().length];
        private long totalNanos;
        private long schedulerLatenessNanos;
        private boolean slowWarning;
        private boolean exceptionalTurn;
        private int activeMobCount;
        private int fixtureMobCount;
        private int randomTickChunks;
        private int randomTickSamples;
        private int randomTickTransitions;
        private long mobLightCandidates;
        private long mobLightHits;
        private long mobLightMisses;
        private long mobLightBfsVisited;
        private int mobLightMaxSingleBfs;
        private long randomLightCandidates;
        private long randomLightHits;
        private long randomLightMisses;
        private long randomLightBfsVisited;
        private int randomLightMaxSingleBfs;

        public MutableTurnObservation reset() {
            Arrays.fill(phaseNanos, 0L);
            totalNanos = 0L;
            schedulerLatenessNanos = 0L;
            slowWarning = false;
            exceptionalTurn = false;
            activeMobCount = 0;
            fixtureMobCount = 0;
            randomTickChunks = 0;
            randomTickSamples = 0;
            randomTickTransitions = 0;
            mobLightCandidates = 0L;
            mobLightHits = 0L;
            mobLightMisses = 0L;
            mobLightBfsVisited = 0L;
            mobLightMaxSingleBfs = 0;
            randomLightCandidates = 0L;
            randomLightHits = 0L;
            randomLightMisses = 0L;
            randomLightBfsVisited = 0L;
            randomLightMaxSingleBfs = 0;
            return this;
        }

        public MutableTurnObservation timing(long totalNanos, long schedulerLatenessNanos,
                boolean slowWarning) {
            this.totalNanos = totalNanos;
            this.schedulerLatenessNanos = schedulerLatenessNanos;
            this.slowWarning = slowWarning;
            return this;
        }

        public MutableTurnObservation phase(Phase phase, long nanos) {
            if (phase == null) throw new IllegalArgumentException("phase is required");
            phaseNanos[phase.ordinal()] = nanos;
            return this;
        }

        public MutableTurnObservation workload(int activeMobCount, int fixtureMobCount,
                int randomTickChunks, int randomTickSamples, int randomTickTransitions) {
            this.activeMobCount = activeMobCount;
            this.fixtureMobCount = fixtureMobCount;
            this.randomTickChunks = randomTickChunks;
            this.randomTickSamples = randomTickSamples;
            this.randomTickTransitions = randomTickTransitions;
            return this;
        }

        public MutableTurnObservation mobLight(long candidates, long hits, long misses,
                long bfsVisited, int maxSingleBfs) {
            mobLightCandidates = candidates;
            mobLightHits = hits;
            mobLightMisses = misses;
            mobLightBfsVisited = bfsVisited;
            mobLightMaxSingleBfs = maxSingleBfs;
            return this;
        }

        public MutableTurnObservation randomTickLight(long candidates, long hits, long misses,
                long bfsVisited, int maxSingleBfs) {
            randomLightCandidates = candidates;
            randomLightHits = hits;
            randomLightMisses = misses;
            randomLightBfsVisited = bfsVisited;
            randomLightMaxSingleBfs = maxSingleBfs;
            return this;
        }

        public MutableTurnObservation exceptionalTurn() {
            exceptionalTurn = true;
            return this;
        }
    }

    public record Statistics(long minimum, long p50, long p95, long p99,
                             long maximum, long sum) {
        private static final Statistics EMPTY = new Statistics(0, 0, 0, 0, 0, 0);
    }

    public record PhaseStatistics(Phase phase, Statistics timing) { }

    public record WorkloadSummary(int minimumActiveMobs, int maximumActiveMobs,
            int minimumFixtureMobs, int maximumFixtureMobs,
            int minimumRandomTickChunks, int maximumRandomTickChunks,
            int minimumRandomTickSamples, int maximumRandomTickSamples,
            int minimumRandomTickTransitions, int maximumRandomTickTransitions,
            long totalRandomTickTransitions) { }

    public record LightSummary(long candidateQueries, long memoHits, long memoMisses,
                               long bfsVisitedCells, int maxSingleBfsVisitCount) { }

    public record Result(String fixtureId, long fixtureChecksum,
            int warmupTurns, int sampleTurns, InvalidReason invalidReason, boolean accepted,
            int slowWarnings, Statistics ownerTurn, Statistics schedulerLateness,
            Statistics unattributed, List<PhaseStatistics> phases,
            WorkloadSummary workload, LightSummary mobLight, LightSummary randomTickLight) {
        public Statistics phase(Phase phase) {
            return phases.get(phase.ordinal()).timing();
        }
    }

    private static final int PHASE_COUNT = Phase.values().length;

    private final ExpectedWorkload expected;
    private final long[] ownerTurnNanos = new long[SAMPLE_TURNS];
    private final long[] schedulerLatenessNanos = new long[SAMPLE_TURNS];
    private final long[] unattributedNanos = new long[SAMPLE_TURNS];
    private final long[][] phaseNanos = new long[PHASE_COUNT][SAMPLE_TURNS];

    private int warmupTurns;
    private int sampleTurns;
    private int slowWarnings;
    private InvalidReason invalidReason = InvalidReason.NONE;
    private boolean complete;

    private int minimumActiveMobs = Integer.MAX_VALUE;
    private int maximumActiveMobs;
    private int minimumFixtureMobs = Integer.MAX_VALUE;
    private int maximumFixtureMobs;
    private int minimumRandomTickChunks = Integer.MAX_VALUE;
    private int maximumRandomTickChunks;
    private int minimumRandomTickSamples = Integer.MAX_VALUE;
    private int maximumRandomTickSamples;
    private int minimumRandomTickTransitions = Integer.MAX_VALUE;
    private int maximumRandomTickTransitions;
    private long totalRandomTickTransitions;

    private long mobLightCandidates;
    private long mobLightHits;
    private long mobLightMisses;
    private long mobLightBfsVisited;
    private int mobLightMaxSingleBfs;
    private long randomLightCandidates;
    private long randomLightHits;
    private long randomLightMisses;
    private long randomLightBfsVisited;
    private int randomLightMaxSingleBfs;

    public OwnerTurnPerformanceWindow(ExpectedWorkload expected) {
        if (expected == null) throw new IllegalArgumentException("expected workload is required");
        this.expected = expected;
    }

    /**
     * Records one owner turn without allocating. Returns false only when this window was already
     * complete/invalid; the turn that completes the window still returns true.
     */
    public boolean record(MutableTurnObservation observation) {
        if (complete) return false;
        if (observation == null || !validObservation(observation)) {
            invalidate(InvalidReason.INVALID_OBSERVATION);
            return true;
        }
        if (observation.exceptionalTurn) {
            invalidate(InvalidReason.EXCEPTIONAL_TURN);
            return true;
        }
        if (!workloadMatches(observation)) {
            invalidate(InvalidReason.WORKLOAD_MISMATCH);
            return true;
        }
        boolean expectedWarning = observation.totalNanos > TICK_BUDGET_NANOS;
        if (observation.slowWarning != expectedWarning) {
            invalidate(InvalidReason.TELEMETRY_MISMATCH);
            return true;
        }

        if (warmupTurns < WARMUP_TURNS) {
            warmupTurns++;
            return true;
        }

        int index = sampleTurns;
        ownerTurnNanos[index] = observation.totalNanos;
        schedulerLatenessNanos[index] = observation.schedulerLatenessNanos;
        long attributed = 0L;
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
            long nanos = observation.phaseNanos[phase];
            phaseNanos[phase][index] = nanos;
            attributed += nanos;
        }
        unattributedNanos[index] = observation.totalNanos - attributed;
        if (observation.slowWarning) slowWarnings++;
        recordWorkload(observation);
        recordLight(observation);
        sampleTurns++;
        if (sampleTurns == SAMPLE_TURNS) complete = true;
        return true;
    }

    private boolean validObservation(MutableTurnObservation observation) {
        if (observation.totalNanos < 0 || observation.schedulerLatenessNanos < 0
                || observation.activeMobCount < 0 || observation.fixtureMobCount < 0
                || observation.randomTickChunks < 0 || observation.randomTickSamples < 0
                || observation.randomTickTransitions < 0
                || !validLight(observation.mobLightCandidates, observation.mobLightHits,
                        observation.mobLightMisses, observation.mobLightBfsVisited,
                        observation.mobLightMaxSingleBfs)
                || !validLight(observation.randomLightCandidates, observation.randomLightHits,
                        observation.randomLightMisses, observation.randomLightBfsVisited,
                        observation.randomLightMaxSingleBfs)) {
            return false;
        }
        long attributed = 0L;
        for (long phase : observation.phaseNanos) {
            if (phase < 0 || Long.MAX_VALUE - attributed < phase) return false;
            attributed += phase;
        }
        return attributed <= observation.totalNanos;
    }

    private static boolean validLight(long candidates, long hits, long misses,
            long bfsVisited, int maxSingleBfs) {
        return candidates >= 0 && hits >= 0 && misses >= 0 && bfsVisited >= 0
                && maxSingleBfs >= 0 && hits <= Long.MAX_VALUE - misses
                && candidates == hits + misses;
    }

    private boolean workloadMatches(MutableTurnObservation observation) {
        return observation.fixtureMobCount == expected.fixtureMobCount()
                && observation.activeMobCount >= observation.fixtureMobCount
                && observation.randomTickChunks == expected.randomTickChunks()
                && observation.randomTickSamples == expected.randomTickSamples()
                && observation.randomTickTransitions <= expected.maxRandomTickTransitions();
    }

    private void recordWorkload(MutableTurnObservation observation) {
        minimumActiveMobs = Math.min(minimumActiveMobs, observation.activeMobCount);
        maximumActiveMobs = Math.max(maximumActiveMobs, observation.activeMobCount);
        minimumFixtureMobs = Math.min(minimumFixtureMobs, observation.fixtureMobCount);
        maximumFixtureMobs = Math.max(maximumFixtureMobs, observation.fixtureMobCount);
        minimumRandomTickChunks = Math.min(minimumRandomTickChunks, observation.randomTickChunks);
        maximumRandomTickChunks = Math.max(maximumRandomTickChunks, observation.randomTickChunks);
        minimumRandomTickSamples = Math.min(minimumRandomTickSamples, observation.randomTickSamples);
        maximumRandomTickSamples = Math.max(maximumRandomTickSamples, observation.randomTickSamples);
        minimumRandomTickTransitions = Math.min(
                minimumRandomTickTransitions, observation.randomTickTransitions);
        maximumRandomTickTransitions = Math.max(
                maximumRandomTickTransitions, observation.randomTickTransitions);
        totalRandomTickTransitions += observation.randomTickTransitions;
    }

    private void recordLight(MutableTurnObservation observation) {
        mobLightCandidates += observation.mobLightCandidates;
        mobLightHits += observation.mobLightHits;
        mobLightMisses += observation.mobLightMisses;
        mobLightBfsVisited += observation.mobLightBfsVisited;
        mobLightMaxSingleBfs = Math.max(
                mobLightMaxSingleBfs, observation.mobLightMaxSingleBfs);
        randomLightCandidates += observation.randomLightCandidates;
        randomLightHits += observation.randomLightHits;
        randomLightMisses += observation.randomLightMisses;
        randomLightBfsVisited += observation.randomLightBfsVisited;
        randomLightMaxSingleBfs = Math.max(
                randomLightMaxSingleBfs, observation.randomLightMaxSingleBfs);
    }

    private void invalidate(InvalidReason reason) {
        invalidReason = reason;
        complete = true;
    }

    public boolean isComplete() { return complete; }
    public boolean isValid() { return invalidReason == InvalidReason.NONE; }
    public int warmupTurns() { return warmupTurns; }
    public int sampleTurns() { return sampleTurns; }

    /** Builds the immutable report only after completion; never call this from a measured turn. */
    public Result finish() {
        if (!complete) throw new IllegalStateException("performance window is not complete");
        Statistics owner = statistics(ownerTurnNanos, sampleTurns);
        Statistics lateness = statistics(schedulerLatenessNanos, sampleTurns);
        Statistics unattributed = statistics(unattributedNanos, sampleTurns);
        List<PhaseStatistics> phases = new ArrayList<>(PHASE_COUNT);
        for (Phase phase : Phase.values()) {
            phases.add(new PhaseStatistics(phase,
                    statistics(phaseNanos[phase.ordinal()], sampleTurns)));
        }
        boolean accepted = invalidReason == InvalidReason.NONE
                && warmupTurns == WARMUP_TURNS && sampleTurns == SAMPLE_TURNS
                && slowWarnings == 0
                && owner.maximum() < TICK_BUDGET_NANOS
                && owner.p95() <= P95_HEADROOM_NANOS
                && owner.p99() <= P99_HEADROOM_NANOS
                && lateness.p99() <= LATENESS_P99_HEADROOM_NANOS;
        return new Result(expected.fixtureId(), expected.fixtureChecksum(), warmupTurns,
                sampleTurns, invalidReason, accepted, slowWarnings, owner, lateness,
                unattributed, List.copyOf(phases), workloadSummary(),
                new LightSummary(mobLightCandidates, mobLightHits, mobLightMisses,
                        mobLightBfsVisited, mobLightMaxSingleBfs),
                new LightSummary(randomLightCandidates, randomLightHits, randomLightMisses,
                        randomLightBfsVisited, randomLightMaxSingleBfs));
    }

    private WorkloadSummary workloadSummary() {
        if (sampleTurns == 0) {
            return new WorkloadSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        return new WorkloadSummary(minimumActiveMobs, maximumActiveMobs,
                minimumFixtureMobs, maximumFixtureMobs,
                minimumRandomTickChunks, maximumRandomTickChunks,
                minimumRandomTickSamples, maximumRandomTickSamples,
                minimumRandomTickTransitions, maximumRandomTickTransitions,
                totalRandomTickTransitions);
    }

    private static Statistics statistics(long[] values, int count) {
        if (count == 0) return Statistics.EMPTY;
        long[] sorted = Arrays.copyOf(values, count);
        Arrays.sort(sorted);
        long sum = 0L;
        for (int index = 0; index < count; index++) sum += values[index];
        return new Statistics(sorted[0], percentile(sorted, 0.50),
                percentile(sorted, 0.95), percentile(sorted, 0.99),
                sorted[count - 1], sum);
    }

    /** Nearest-rank percentile: ceil(p*n)-1, matching the fixed 600-turn acceptance window. */
    private static long percentile(long[] sorted, double percentile) {
        int rank = Math.max(1, (int) Math.ceil(percentile * sorted.length));
        return sorted[rank - 1];
    }
}
