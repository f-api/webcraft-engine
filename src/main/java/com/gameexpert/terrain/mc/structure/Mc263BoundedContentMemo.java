package com.gameexpert.terrain.mc.structure;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Bounded LRU memo for a pure function of an immutable content key.
 *
 * <p>AGENTS rule 10l: a port carries the source's amortization contract, not only its bytes. This
 * memo exists for derivations the canonical seam repeats verbatim — the same immutable input
 * validated or parsed again on every commit — and it is byte-neutral by construction: the value
 * handed back is the one the first computation produced, and only computations that completed
 * without throwing are stored, so a rejected input is rejected identically every time.</p>
 *
 * <p>Two properties the callers must keep. The key has to name every input the computation reads
 * and must be immutable once stored, and the value has to be immutable too, so that a consumer
 * cannot corrupt a later hit. Computation happens outside the monitor so one caller never blocks
 * another; a race stores the first arrival, and two threads computing the same pure key produce
 * equal values, so returning the resident one keeps the memo referentially stable.</p>
 */
final class Mc263BoundedContentMemo<K, V> {

    private final int bound;
    private final Map<K, V> entries;
    private long hits;
    private long misses;

    Mc263BoundedContentMemo(int bound) {
        if (bound < 1) {
            throw new IllegalArgumentException("content memo bound must be positive: " + bound);
        }
        this.bound = bound;
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > Mc263BoundedContentMemo.this.bound;
            }
        };
    }

    /** Returns the memoized value for the key, computing it once when absent. */
    V resolve(K key, Supplier<V> computation) {
        Objects.requireNonNull(key, "content memo key");
        Objects.requireNonNull(computation, "content memo computation");
        V resident = lookup(key);
        if (resident != null) {
            return resident;
        }
        return store(key, Objects.requireNonNull(computation.get(), "content memo value"));
    }

    /** Resident-answer count, for the throughput gate's amortization assertions. */
    synchronized long hits() { return hits; }

    /** Computed-answer count, for the throughput gate's amortization assertions. */
    synchronized long misses() { return misses; }

    private synchronized V lookup(K key) {
        V resident = entries.get(key);
        if (resident != null) {
            hits++;
        } else {
            misses++;
        }
        return resident;
    }

    private synchronized V store(K key, V computed) {
        V raced = entries.putIfAbsent(key, computed);
        return raced != null ? raced : computed;
    }
}
