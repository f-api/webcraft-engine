package com.gameexpert.engine.diagnostics;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.atomic.AtomicLong;

/** Diagnostic-only counters and clock reads. Never enables JVM thread-CPU measurement. */
final class ArchitectureJfrSupport {
    static final int SPANS_PER_SECOND = 1024;
    static final int MAX_ACTIVE_SPANS = 4096;
    static final AtomicLong ADMITTED = new AtomicLong();
    static final AtomicLong DROPPED = new AtomicLong();
    static final AtomicLong UNCOMMITTED = new AtomicLong();
    static final AtomicLong ACTIVE = new AtomicLong();
    private static final SpanLimit LIMIT = new SpanLimit(SPANS_PER_SECOND);

    private ArchitectureJfrSupport() { }

    static boolean admit(long nowNanos) {
        ArchitectureDropsEvent.initialize();
        if (!reserveActiveSpan(ACTIVE, MAX_ACTIVE_SPANS)) {
            DROPPED.incrementAndGet();
            return false;
        }
        if (!LIMIT.admit(nowNanos)) {
            ACTIVE.decrementAndGet();
            DROPPED.incrementAndGet();
            return false;
        }
        ADMITTED.incrementAndGet();
        return true;
    }

    static boolean reserveActiveSpan(AtomicLong active, int maximum) {
        long count;
        do {
            count = active.get();
            if (count >= maximum) return false;
        } while (!active.compareAndSet(count, count + 1L));
        return true;
    }

    static long threadCpuNanos() {
        try {
            ThreadMXBean bean = CpuBean.VALUE;
            return bean.isCurrentThreadCpuTimeSupported() && bean.isThreadCpuTimeEnabled()
                    ? bean.getCurrentThreadCpuTime() : -1L;
        } catch (UnsupportedOperationException | SecurityException unavailable) {
            return -1L;
        }
    }

    private static final class CpuBean {
        private static final ThreadMXBean VALUE = ManagementFactory.getThreadMXBean();
    }

    /** Fixed monotonic windows; injectable timestamps keep the bound test independent of wall time. */
    static final class SpanLimit {
        private final int maximum;
        private long windowStart;
        private int admitted;
        private boolean initialized;

        SpanLimit(int maximum) { this.maximum = maximum; }

        synchronized boolean admit(long nowNanos) {
            if (!initialized || nowNanos - windowStart >= 1_000_000_000L) {
                initialized = true;
                windowStart = nowNanos;
                admitted = 0;
            }
            if (admitted >= maximum) return false;
            admitted++;
            return true;
        }
    }
}
