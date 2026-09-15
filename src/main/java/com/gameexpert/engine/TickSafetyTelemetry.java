package com.gameexpert.engine;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * 틱 스레드에서 절대 실행되면 안 되는 느린 경로를 단계별로 집계합니다.
 * 정상 블록 조회에서는 스레드 로컬 확인 한 번만 수행하며 스택 추적이나 할당을 하지 않습니다.
 */
public final class TickSafetyTelemetry {

    public static final class Snapshot {
        private final long[] counts;

        private Snapshot(long[] counts) {
            this.counts = counts;
        }

        public long count(Event event) {
            long total = 0;
            for (Phase phase : Phase.values()) total += count(event, phase);
            return total;
        }

        public long count(Event event, Phase phase) {
            return counts[index(event, phase)];
        }
    }

    public enum Phase {
        NONE,
        PRELUDE,
        PLAYER,
        ENVIRONMENT,
        RANDOM_TICK,
        FLUID,
        FALLING_BLOCK,
        MOB_COMBAT,
        OTHER
    }

    public enum Event {
        TERRAIN_GENERATION,
        PERSISTENCE_READ,
        BLOCK_READ_ACTIVATION,
        STRUCTURE_PLANNING,
        FULL_CHUNK_SCAN,
        BLOCKING_WAIT,
        SYNCHRONOUS_SEND_CALL
    }

    private static final int PHASE_COUNT = Phase.values().length;
    private static final AtomicLongArray COUNTS =
            new AtomicLongArray(Event.values().length * PHASE_COUNT);
    private static final ThreadLocal<Boolean> IN_TICK = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Phase> CURRENT_PHASE =
            ThreadLocal.withInitial(() -> Phase.NONE);
    private static final ThreadLocal<long[]> CURRENT_TICK_COUNTS =
            ThreadLocal.withInitial(() -> new long[Event.values().length * PHASE_COUNT]);

    private TickSafetyTelemetry() {
    }

    public static void beginTick() {
        Arrays.fill(CURRENT_TICK_COUNTS.get(), 0L);
        IN_TICK.set(true);
        CURRENT_PHASE.set(Phase.PRELUDE);
    }

    public static void phase(Phase phase) {
        if (IN_TICK.get()) CURRENT_PHASE.set(phase == null ? Phase.OTHER : phase);
    }

    public static void endTick() {
        CURRENT_PHASE.remove();
        IN_TICK.remove();
    }

    public static boolean isTickThread() {
        return IN_TICK.get();
    }

    public static void record(Event event) {
        if (!IN_TICK.get()) return;
        Phase phase = CURRENT_PHASE.get();
        int index = index(event, phase);
        CURRENT_TICK_COUNTS.get()[index]++;
        COUNTS.incrementAndGet(index);
    }

    public static long count(Event event) {
        long total = 0;
        for (Phase phase : Phase.values()) total += count(event, phase);
        return total;
    }

    public static long count(Event event, Phase phase) {
        return COUNTS.get(index(event, phase));
    }

    /** 현재 owner invocation에서 이 스레드가 직접 기록한 횟수입니다. 다른 월드 틱은 섞이지 않습니다. */
    public static long currentTickCount(Event event) {
        if (!IN_TICK.get()) return 0L;
        long total = 0L;
        long[] counts = CURRENT_TICK_COUNTS.get();
        for (Phase phase : Phase.values()) total += counts[index(event, phase)];
        return total;
    }

    public static Snapshot snapshot() {
        long[] values = new long[COUNTS.length()];
        for (int i = 0; i < values.length; i++) values[i] = COUNTS.get(i);
        return new Snapshot(values);
    }

    /** 테스트와 월드 교체 시 사용할 수 있는 원자적이지 않은 진단 초기화입니다. */
    public static void reset() {
        for (int i = 0; i < COUNTS.length(); i++) COUNTS.set(i, 0L);
    }

    private static int index(Event event, Phase phase) {
        return event.ordinal() * PHASE_COUNT + phase.ordinal();
    }
}
