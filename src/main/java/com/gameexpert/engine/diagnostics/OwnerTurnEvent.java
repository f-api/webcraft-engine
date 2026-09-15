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
import jdk.jfr.Timestamp;
import jdk.jfr.Timespan;

/**
 * Completion observation of the existing complete owner-turn budget interval.
 * The explicit interval, not JFR's instantaneous publication duration, is authoritative.
 * Epoch endpoints are millisecond-resolution mappings through the recorded clock anchor.
 */
@Name(OwnerTurnEvent.NAME)
@Label("Complete World Owner Turn")
@Category("Game Expert")
@Enabled(false)
@StackTrace(false)
@Threshold("0 ns")
public final class OwnerTurnEvent extends Event {
    public static final String NAME = "com.gameexpert.OwnerTurn";
    private static final EventType TYPE = EventType.getEventType(OwnerTurnEvent.class);
    private static final AtomicLong SEQUENCE = new AtomicLong();

    @Label("World ID")
    private long worldId;
    @Label("JVM-wide Observed Turn Sequence")
    private long sequence;
    @Label("Exceptional Turn")
    private boolean failure;
    @Label("Owner Turn Start (monotonic ns)")
    private long ownerTurnStartNanos;
    @Label("Owner Turn End (monotonic ns)")
    private long ownerTurnEndNanos;
    @Label("Complete Owner Turn Duration")
    @Timespan(Timespan.NANOSECONDS)
    private long ownerTurnDurationNanos;
    @Label("Clock Anchor (monotonic ns)")
    private long clockAnchorMonotonicNanos;
    @Label("Clock Anchor (epoch ms)")
    @Timestamp(Timestamp.MILLISECONDS_SINCE_EPOCH)
    private long clockAnchorEpochMillis;
    @Label("Mapped Interval Start (epoch ms)")
    @Timestamp(Timestamp.MILLISECONDS_SINCE_EPOCH)
    private long intervalStartEpochMillis;
    @Label("Mapped Interval End (epoch ms)")
    @Timestamp(Timestamp.MILLISECONDS_SINCE_EPOCH)
    private long intervalEndEpochMillis;

    private OwnerTurnEvent() {
    }

    /** No per-turn allocation, clock sampling, or sequence increment while disabled. */
    public static boolean commitTurn(long worldId, long startNanos, long endNanos, boolean failure) {
        if (!TYPE.isEnabled()) return false;
        OwnerTurnEvent event = new OwnerTurnEvent();
        if (!event.shouldCommit()) return false;
        event.worldId = worldId;
        event.sequence = SEQUENCE.incrementAndGet();
        event.failure = failure;
        event.ownerTurnStartNanos = startNanos;
        event.ownerTurnEndNanos = endNanos;
        event.ownerTurnDurationNanos = endNanos - startNanos;
        event.clockAnchorMonotonicNanos = System.nanoTime();
        event.clockAnchorEpochMillis = System.currentTimeMillis();
        event.intervalStartEpochMillis = event.clockAnchorEpochMillis
                + Math.floorDiv(startNanos - event.clockAnchorMonotonicNanos, 1_000_000L);
        event.intervalEndEpochMillis = event.clockAnchorEpochMillis
                + Math.floorDiv(endNanos - event.clockAnchorMonotonicNanos, 1_000_000L);
        event.commit();
        return true;
    }
}
