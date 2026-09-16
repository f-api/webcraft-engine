package com.gameexpert.engine.persistence.tick;

import java.util.List;

/** Queue and outbox rows read from one database snapshot before resident recovery starts. */
public record FinalCarrierTickRecoverySnapshot(
        List<FinalCarrierTickScheduler.ScheduledTick> scheduled,
        List<FinalCarrierTickScheduler.DurablePublication> publications) {
    public FinalCarrierTickRecoverySnapshot {
        scheduled = List.copyOf(scheduled);
        publications = List.copyOf(publications);
    }
}
