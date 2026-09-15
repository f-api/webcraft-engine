package com.gameexpert.engine;

import java.util.Comparator;
import java.util.List;
import java.util.function.LongToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * Admission order and concurrency bound for canonical chunk demand raised by snapshot preparation.
 *
 * <p>Canonical production is serialized behind one lock, so admitting several uncommitted
 * coordinates at once buys no throughput; it only interleaves independent request streams through
 * the shared post-CARVERS input memo. Measured cold entry with four interleaved preparer streams
 * held that memo at ~48% hits with about one eviction per miss, and mean production rose from the
 * warm 52 ms/chunk contract to 200-250 ms/chunk. Admitting the batch in the same ring order the
 * canonical prefetch spiral walks, and keeping only a bounded number of uncommitted coordinates in
 * flight, keeps consecutive productions inside one memo window.</p>
 *
 * <p>Order is the existing demand order used for activation drains: nearest player first, then a
 * total tie-break on x and z so worker completion timing can never change which coordinate is
 * admitted first.</p>
 */
final class SnapshotPreparationAdmission {

    /**
     * Uncommitted canonical coordinates allowed in flight at once. One would remove interleaving
     * entirely but re-quantizes every production to the owner turn period; two keeps the producer
     * lock handed straight to the next ring neighbour, whose input window overlaps the one just
     * produced, so the memo stays hot without a tick of dead time between chunks.
     */
    static final int CONCURRENT_UNCOMMITTED_CANONICAL_DEMAND = Math.max(1,
            Integer.getInteger("gameexpert.snapshot.uncommittedCanonicalDemand", 2));

    private SnapshotPreparationAdmission() {
    }

    /** Canonical ring order over packed chunk keys. */
    static int compareDemandedChunks(long leftKey, long rightKey,
            LongToDoubleFunction nearestPlayerDistanceSquared) {
        int distance = Double.compare(nearestPlayerDistanceSquared.applyAsDouble(leftKey),
                nearestPlayerDistanceSquared.applyAsDouble(rightKey));
        if (distance != 0) return distance;
        int x = Integer.compare((int) (leftKey >> 32), (int) (rightKey >> 32));
        return x != 0 ? x : Integer.compare((int) leftKey, (int) rightKey);
    }

    static Comparator<Long> canonicalRingOrder(LongToDoubleFunction nearestPlayerDistanceSquared) {
        return (left, right) -> compareDemandedChunks(left, right, nearestPlayerDistanceSquared);
    }

    /**
     * Sorts one drained admission batch into canonical ring order. The batch is whatever the owner
     * turn drained, so this never reorders work already handed to a producer.
     */
    static <T> void orderForAdmission(List<T> batch, ToLongFunction<T> keyOf,
            LongToDoubleFunction nearestPlayerDistanceSquared) {
        if (batch.size() < 2) return;
        batch.sort((left, right) -> compareDemandedChunks(
                keyOf.applyAsLong(left), keyOf.applyAsLong(right), nearestPlayerDistanceSquared));
    }

    /** Whether one more uncommitted canonical coordinate may be admitted right now. */
    static boolean admitsUncommittedCanonicalDemand(int inFlightUncommittedDemand) {
        return inFlightUncommittedDemand < CONCURRENT_UNCOMMITTED_CANONICAL_DEMAND;
    }

    static boolean admitsCanonicalPreparation(int generating, boolean knownCommitted) {
        return knownCommitted || admitsUncommittedCanonicalDemand(generating);
    }
}
