package com.gameexpert.terrain.mc.feature;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Bounded LRU memo for deterministic per-origin structure starts.
 *
 * <p>AGENTS rule 10l: a canonical executor validates the persisted start of every structure it
 * owns against a freshly generated one, and the FEATURES seam repeats that validation once per
 * source chunk (25 per target) and again on every placement dispatch. Regenerating a jigsaw plan
 * is a pure function of {@code (worldSeed, origin chunk, …)}, so the repeat is byte-neutral work
 * this memo removes: the value handed back is the very object the first generation produced, and
 * the validation it feeds is unchanged.</p>
 *
 * <p>Correctness rests on two properties the callers must keep. The key has to name every input
 * the computation reads, and the value has to be immutable — every memoized start is a record of
 * records with defensive copies, so a consumer cannot corrupt a later hit. The bound keeps a long
 * activation wall from retaining plans for origins that scrolled out of range; a re-decision after
 * an eviction recomputes the identical value.</p>
 *
 * <p>Computation happens outside the monitor so one world never blocks another, and a race stores
 * the first arrival: two threads computing the same pure key produce equal values, and returning
 * the resident one keeps the memo referentially stable.</p>
 */
final class Mc263CanonicalStartMemo<K, V> {

    private final int bound;
    private final Map<K, V> entries;
    private long hits;
    private long misses;

    Mc263CanonicalStartMemo(int bound) {
        if (bound < 1) {
            throw new IllegalArgumentException("start memo bound must be positive: " + bound);
        }
        this.bound = bound;
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > Mc263CanonicalStartMemo.this.bound;
            }
        };
    }

    /** Returns the memoized value for the key, computing it once when absent. */
    V resolve(K key, Supplier<V> computation) {
        Objects.requireNonNull(key, "start memo key");
        Objects.requireNonNull(computation, "start memo computation");
        V resident = lookup(key);
        if (resident != null) {
            return resident;
        }
        return store(key, Objects.requireNonNull(computation.get(), "start memo value"));
    }

    synchronized long hits() {
        return hits;
    }

    synchronized long misses() {
        return misses;
    }

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
