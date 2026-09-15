package com.gameexpert.common;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.LongFunction;

/**
 * Minimal single-threaded primitive {@code long -> object} map for owner-thread hot paths.
 *
 * <p>Key {@code 0} is stored separately because zero marks an empty table slot. Linear probing and
 * power-of-two capacities match {@link LongOpenHashMap}; this class intentionally exposes only the operations
 * needed by resident chunk lookup.</p>
 */
public final class LongObjectOpenHashMap<V> {
    private static final float LOAD_FACTOR = 0.75f;

    private long[] keys;
    private Object[] values;
    private int mask;
    private int maxFill;
    private int size;
    private boolean hasZero;
    private V zeroValue;

    public LongObjectOpenHashMap(int expected) {
        int capacity = capacityFor(expected);
        keys = new long[capacity];
        values = new Object[capacity];
        mask = capacity - 1;
        maxFill = (int) Math.ceil(capacity * LOAD_FACTOR);
    }

    @SuppressWarnings("unchecked")
    public V get(long key) {
        if (key == 0) return hasZero ? zeroValue : null;
        int position = index(key);
        long current;
        while ((current = keys[position]) != 0) {
            if (current == key) return (V) values[position];
            position = (position + 1) & mask;
        }
        return null;
    }

    public V getOrDefault(long key, V defaultValue) {
        V value = get(key);
        return value == null ? defaultValue : value;
    }

    public V computeIfAbsent(long key, LongFunction<? extends V> factory) {
        V value = get(key);
        if (value != null) return value;
        V created = factory.apply(key);
        put(key, created);
        return created;
    }

    public boolean containsKey(long key) {
        if (key == 0) return hasZero;
        int position = index(key);
        long current;
        while ((current = keys[position]) != 0) {
            if (current == key) return true;
            position = (position + 1) & mask;
        }
        return false;
    }

    public void put(long key, V value) {
        if (value == null) throw new IllegalArgumentException("null values are not supported");
        if (key == 0) {
            if (!hasZero) hasZero = true;
            zeroValue = value;
            return;
        }
        int position = index(key);
        long current;
        while ((current = keys[position]) != 0) {
            if (current == key) {
                values[position] = value;
                return;
            }
            position = (position + 1) & mask;
        }
        keys[position] = key;
        values[position] = value;
        if (++size >= maxFill) resize(keys.length << 1);
    }

    @SuppressWarnings("unchecked")
    public V remove(long key) {
        if (key == 0) {
            if (!hasZero) return null;
            V previous = zeroValue;
            hasZero = false;
            zeroValue = null;
            return previous;
        }
        int position = index(key);
        long current;
        while ((current = keys[position]) != 0) {
            if (current == key) {
                V previous = (V) values[position];
                shiftKeys(position);
                size--;
                return previous;
            }
            position = (position + 1) & mask;
        }
        return null;
    }

    public int size() {
        return size + (hasZero ? 1 : 0);
    }

    public void clear() {
        if (size != 0) {
            Arrays.fill(keys, 0L);
            Arrays.fill(values, null);
        }
        size = 0;
        hasZero = false;
        zeroValue = null;
    }

    /** Visits current values without boxing primitive keys or exposing the backing table. */
    @SuppressWarnings("unchecked")
    public void forEachValue(Consumer<? super V> consumer) {
        if (hasZero) consumer.accept(zeroValue);
        for (int index = 0; index < keys.length; index++) {
            if (keys[index] != 0) consumer.accept((V) values[index]);
        }
    }

    private int index(long key) {
        long hash = key * 0x9E3779B97F4A7C15L;
        hash ^= hash >>> 32;
        return (int) hash & mask;
    }

    private void shiftKeys(int removed) {
        int last;
        int position = removed;
        while (true) {
            last = position;
            position = (position + 1) & mask;
            long current;
            while ((current = keys[position]) != 0) {
                int slot = index(current);
                if (last <= position
                        ? last >= slot || slot > position
                        : last >= slot && slot > position) break;
                position = (position + 1) & mask;
            }
            if (current == 0) {
                keys[last] = 0;
                values[last] = null;
                return;
            }
            keys[last] = current;
            values[last] = values[position];
        }
    }

    private void resize(int capacity) {
        long[] oldKeys = keys;
        Object[] oldValues = values;
        keys = new long[capacity];
        values = new Object[capacity];
        mask = capacity - 1;
        maxFill = (int) Math.ceil(capacity * LOAD_FACTOR);
        for (int i = 0; i < oldKeys.length; i++) {
            long key = oldKeys[i];
            if (key == 0) continue;
            int position = index(key);
            while (keys[position] != 0) position = (position + 1) & mask;
            keys[position] = key;
            values[position] = oldValues[i];
        }
    }

    private static int capacityFor(int expected) {
        long need = (long) Math.ceil(Math.max(1, expected) / LOAD_FACTOR);
        long capacity = Long.highestOneBit(Math.max(1, need - 1)) << 1;
        return (int) Math.max(2, capacity);
    }
}
