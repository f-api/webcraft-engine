package com.gameexpert.engine.diagnostics;

import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Period;
import jdk.jfr.StackTrace;

/** JVM-lifetime counters emitted at chunk end, including recording stop without a subsequent tick. */
@Name(ArchitectureDropsEvent.NAME)
@Label("Architecture Diagnostic Span Accounting")
@Category("Game Expert")
@Enabled(false)
@StackTrace(false)
@Period("endChunk")
public final class ArchitectureDropsEvent extends Event {
    public static final String NAME = "com.gameexpert.ArchitectureDrops";
    private static final EventType TYPE = EventType.getEventType(ArchitectureDropsEvent.class);

    static {
        FlightRecorder.addPeriodicEvent(ArchitectureDropsEvent.class, ArchitectureDropsEvent::publish);
    }

    public int spansPerSecond;
    public int maxActiveSpans;
    public long admittedSpans;
    public long droppedSpans;
    public long uncommittedSpans;
    public long activeSpans;

    private ArchitectureDropsEvent() { }

    static void initialize() { }

    private static void publish() {
        if (!TYPE.isEnabled()) return;
        ArchitectureDropsEvent event = new ArchitectureDropsEvent();
        if (!event.shouldCommit()) return;
        event.spansPerSecond = ArchitectureJfrSupport.SPANS_PER_SECOND;
        event.maxActiveSpans = ArchitectureJfrSupport.MAX_ACTIVE_SPANS;
        event.admittedSpans = ArchitectureJfrSupport.ADMITTED.get();
        event.droppedSpans = ArchitectureJfrSupport.DROPPED.get();
        event.uncommittedSpans = ArchitectureJfrSupport.UNCOMMITTED.get();
        event.activeSpans = ArchitectureJfrSupport.ACTIVE.get();
        event.commit();
    }
}
