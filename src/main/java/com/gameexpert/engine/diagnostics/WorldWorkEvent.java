package com.gameexpert.engine.diagnostics;

import java.util.concurrent.atomic.AtomicLong;

import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;
import jdk.jfr.Timespan;

/** Bounded named spans. Causal submission parents and same-thread enclosing spans are distinct. */
@Name(WorldWorkEvent.NAME)
@Label("World Architecture Work")
@Category("Game Expert")
@Enabled(false)
@StackTrace(false)
@Threshold("0 ns")
public final class WorldWorkEvent extends Event {
    public static final String NAME = "com.gameexpert.WorldWork";
    private static final EventType TYPE = EventType.getEventType(WorldWorkEvent.class);
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final ThreadLocal<WorldWorkEvent> CURRENT = new ThreadLocal<>();

    public long worldId;
    public long workId;
    public long parentWorkId;
    public long enclosingWorkId;
    public long ownerTurnStartNanos;
    public String workKind;
    public String outcome;
    public boolean queued;
    public boolean hasChunk;
    public int chunkX;
    public int chunkZ;
    public long generation;
    public long submittedNanos;
    public long runStartNanos;
    public long runEndNanos;
    @Timespan(Timespan.NANOSECONDS)
    public long queueWaitNanos;
    @Timespan(Timespan.NANOSECONDS)
    public long runNanos;
    @Timespan(Timespan.NANOSECONDS)
    public long exclusiveRunNanos;
    /** -1 means unsupported or not already enabled; no JVM settings are changed. */
    public long threadCpuNanos;
    public long exclusiveThreadCpuNanos;
    public long workUnits;

    private transient WorldWorkEvent enclosing;
    private transient long cpuStartNanos;
    private transient long childRunNanos;
    private transient long childCpuNanos;
    private transient boolean started;
    private transient boolean closed;

    private WorldWorkEvent() { }

    public static boolean enabled() { return TYPE.isEnabled(); }

    /** Disabled calls allocate nothing and sample neither clocks nor thread CPU. */
    public static WorldWorkEvent submitted(long worldId, long ownerTurnStartNanos,
            String kind, boolean hasChunk, int chunkX, int chunkZ, long generation) {
        if (!TYPE.isEnabled()) return null;
        long now = System.nanoTime();
        if (!ArchitectureJfrSupport.admit(now)) return null;
        WorldWorkEvent parent = CURRENT.get();
        WorldWorkEvent event = new WorldWorkEvent();
        event.worldId = worldId;
        event.workId = SEQUENCE.incrementAndGet();
        event.parentWorkId = parent != null && parent.worldId == worldId ? parent.workId : 0L;
        event.ownerTurnStartNanos = ownerTurnStartNanos != 0L ? ownerTurnStartNanos
                : parent != null && parent.worldId == worldId ? parent.ownerTurnStartNanos : 0L;
        event.workKind = kind;
        event.outcome = "completed";
        event.queued = true;
        event.hasChunk = hasChunk;
        event.chunkX = chunkX;
        event.chunkZ = chunkZ;
        event.generation = generation;
        event.submittedNanos = now;
        event.threadCpuNanos = -1L;
        event.exclusiveThreadCpuNanos = -1L;
        return event;
    }

    public static WorldWorkEvent begin(long worldId, long ownerTurnStartNanos,
            String kind, boolean hasChunk, int chunkX, int chunkZ, long generation) {
        WorldWorkEvent event = submitted(worldId, ownerTurnStartNanos, kind,
                hasChunk, chunkX, chunkZ, generation);
        start(event);
        if (event != null) {
            event.queued = false;
            event.queueWaitNanos = 0L;
        }
        return event;
    }

    public static void start(WorldWorkEvent event) {
        if (event == null || event.started || event.closed) return;
        event.started = true;
        event.enclosing = CURRENT.get();
        event.enclosingWorkId = event.enclosing == null ? 0L : event.enclosing.workId;
        event.runStartNanos = System.nanoTime();
        event.queueWaitNanos = Math.max(0L, event.runStartNanos - event.submittedNanos);
        event.cpuStartNanos = ArchitectureJfrSupport.threadCpuNanos();
        CURRENT.set(event);
        event.begin();
    }

    public static void outcome(WorldWorkEvent event, String outcome) {
        if (event != null) event.outcome = outcome;
    }

    /** A worker may translate an exception into a failed future instead of throwing out of Runnable.run. */
    public static void currentOutcome(String outcome) {
        if (!TYPE.isEnabled()) return;
        WorldWorkEvent event = CURRENT.get();
        if (event != null) event.outcome = outcome;
    }

    public static void finish(WorldWorkEvent event) {
        if (event == null || event.closed) return;
        event.closed = true;
        event.runEndNanos = System.nanoTime();
        if (event.started) {
            event.end();
            event.runNanos = Math.max(0L, event.runEndNanos - event.runStartNanos);
            event.exclusiveRunNanos = Math.max(0L, event.runNanos - event.childRunNanos);
            long cpuEnd = ArchitectureJfrSupport.threadCpuNanos();
            if (cpuEnd >= 0L && event.cpuStartNanos >= 0L) {
                event.threadCpuNanos = Math.max(0L, cpuEnd - event.cpuStartNanos);
                event.exclusiveThreadCpuNanos = Math.max(0L,
                        event.threadCpuNanos - event.childCpuNanos);
            }
            if (event.enclosing == null) CURRENT.remove();
            else {
                event.enclosing.childRunNanos += event.runNanos;
                if (event.threadCpuNanos >= 0L) event.enclosing.childCpuNanos += event.threadCpuNanos;
                CURRENT.set(event.enclosing);
            }
        } else {
            event.runStartNanos = event.runEndNanos;
            event.queueWaitNanos = Math.max(0L, event.runEndNanos - event.submittedNanos);
        }
        ArchitectureJfrSupport.ACTIVE.decrementAndGet();
        if (event.shouldCommit()) event.commit();
        else ArchitectureJfrSupport.UNCOMMITTED.incrementAndGet();
    }
}
