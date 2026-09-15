package com.gameexpert.engine.diagnostics;

import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;
import jdk.jfr.Timespan;

/** Complete, nonoverlapping phase counters sharing OwnerTurnEvent's existing monotonic interval. */
@Name(ArchitectureTurnEvent.NAME)
@Label("World Architecture Turn")
@Category("Game Expert")
@Enabled(false)
@StackTrace(false)
@Threshold("0 ns")
public final class ArchitectureTurnEvent extends Event {
    public static final String NAME = "com.gameexpert.ArchitectureTurn";
    private static final EventType TYPE = EventType.getEventType(ArchitectureTurnEvent.class);

    public long worldId;
    public long tickNo;
    public long ownerTurnStartNanos;
    public long ownerTurnEndNanos;
    @Timespan(Timespan.NANOSECONDS)
    public long ownerTurnDurationNanos;
    public boolean failure;
    public boolean schedulerDeadlineKnown;
    public long expectedStartNanos;
    @Timespan(Timespan.NANOSECONDS)
    public long schedulerLatenessNanos;
    public long threadCpuNanos;
    @Timespan(Timespan.NANOSECONDS)
    public long safetyNanos, preludeNanos, beginTickNanos, actionNanos, smeltNanos, clockNanos,
            fluidNanos, randomTickNanos, mobNanos, neighborNanos, itemNanos, boatNanos,
            environmentNanos, broadcastSaveNanos, lifecycleNanos, snapshotPreparationNanos,
            treePlanningNanos, proximityNanos, chunkDemandNanos, chunkAdmissionNanos,
            decorationNanos, preparedSnapshotNanos, snapshotDeliveryNanos, snapshotDeadlineNanos,
            otherNanos;
    public int players;
    public int activeSimulationChunks;
    public int actionQueueStart, actionQueueEnd;
    public int moveActions, gameplayActions, respawnActions;
    public int snapshotRequestsStart, snapshotRequestsEnd;
    public int snapshotPreparationsStart, snapshotPreparationsEnd;
    public int activationDemandsStart, activationDemandsEnd;
    public int preparedActivationsStart, preparedActivationsEnd;
    public int activationApplicationsStart, activationApplicationsEnd;
    public int pendingLiveUpdatesStart, pendingLiveUpdatesEnd;
    public int scheduledTicksStart, scheduledTicksEnd;
    public int persistenceCompletionsDrained;
    public int mapCompletionsDrained;
    public int finalCarrierAdmissionCompletions;
    public int finalCarrierAdmissionsApplied;
    public int neighborhoodRebuilds;
    public int snapshotPreparationRequestsExamined;
    public int snapshotPreparationAdmissions;
    public int snapshotResultsPolled;
    public int snapshotCaptureAttempts;
    public int snapshotCaptures;
    public int activationPlansAdopted;
    public int activationSlicesApplied;
    public long droppedWorkSpans;
    private transient long cpuStartNanos;
    private transient long cpuEndNanos;
    private transient boolean cpuEnded;

    private ArchitectureTurnEvent() { }

    public static boolean enabled() { return TYPE.isEnabled(); }

    public static ArchitectureTurnEvent begin(long worldId, long startNanos, long expectedStartNanos) {
        if (!TYPE.isEnabled()) return null;
        ArchitectureTurnEvent event = new ArchitectureTurnEvent();
        event.worldId = worldId;
        event.ownerTurnStartNanos = startNanos;
        event.expectedStartNanos = expectedStartNanos;
        event.schedulerDeadlineKnown = expectedStartNanos != 0L;
        event.schedulerLatenessNanos = expectedStartNanos == 0L ? 0L
                : Math.max(0L, startNanos - expectedStartNanos);
        event.cpuStartNanos = ArchitectureJfrSupport.threadCpuNanos();
        ArchitectureDropsEvent.initialize();
        return event;
    }

    public void captureCpuEnd() {
        if (cpuEnded) return;
        cpuEndNanos = ArchitectureJfrSupport.threadCpuNanos();
        cpuEnded = true;
    }

    public void finish(long endNanos, boolean failure) {
        ownerTurnEndNanos = endNanos;
        ownerTurnDurationNanos = Math.max(0L, endNanos - ownerTurnStartNanos);
        this.failure = failure;
        captureCpuEnd();
        threadCpuNanos = cpuStartNanos < 0L || cpuEndNanos < 0L ? -1L
                : Math.max(0L, cpuEndNanos - cpuStartNanos);
        droppedWorkSpans = ArchitectureJfrSupport.DROPPED.get();
        if (shouldCommit()) commit();
    }
}
