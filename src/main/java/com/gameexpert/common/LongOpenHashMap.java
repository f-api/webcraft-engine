package com.gameexpert.common;

import java.util.Arrays;

/**
 * 원시 {@code long → long} 오픈 어드레싱 해시맵(단일 스레드 전용, 무동기화).
 *
 * <p>서버 틱 핫패스에서 {@code Map<Long, ...>} 사용 시 매 조회/삽입마다 발생하던 키/값
 * 오토박싱을 제거하기 위한 최소 구현입니다. 값이 {@code short}/{@code int}인 사용처는
 * long 으로 무손실 확장해 저장하고, 조회부에서 다시 좁혀 읽습니다.</p>
 *
 * <p>키 {@code 0} 은 빈 슬롯 표식과 겹치므로 별도 필드({@link #hasZero}/{@link #zeroValue})로
 * 특수 처리합니다. 선형 프로빙 + power-of-two 용량으로 마스킹합니다.</p>
 *
 * <p><b>스레딩</b>: 동기화하지 않습니다. 소유 자료구조와 동일하게 단일 틱 스레드에서만 쓰세요.</p>
 */
public final class LongOpenHashMap {

    private final float loadFactor;
    private long[] keys;
    private long[] values;
    private int mask;
    private int maxFill;
    private int size;        // 0 이 아닌 키의 개수(제로 키는 hasZero 로 별도 카운트)
    private boolean hasZero;
    private long zeroValue;

    public LongOpenHashMap() {
        this(16, 0.75f);
    }

    public LongOpenHashMap(int expected) {
        this(expected, 0.75f);
    }

    public LongOpenHashMap(int expected, float loadFactor) {
        if (loadFactor <= 0 || loadFactor >= 1) {
            throw new IllegalArgumentException("loadFactor must be in (0,1): " + loadFactor);
        }
        this.loadFactor = loadFactor;
        int cap = capacityFor(expected, loadFactor);
        this.keys = new long[cap];
        this.values = new long[cap];
        this.mask = cap - 1;
        this.maxFill = (int) Math.ceil(cap * loadFactor);
    }

    /** 키에 매핑된 값, 없으면 {@code absent}. 호출부는 실제 값과 겹치지 않는 sentinel 을 전달해야 합니다. */
    public long get(long key, long absent) {
        if (key == 0) {
            return hasZero ? zeroValue : absent;
        }
        int pos = index(key);
        long curr;
        while ((curr = keys[pos]) != 0) {
            if (curr == key) {
                return values[pos];
            }
            pos = (pos + 1) & mask;
        }
        return absent;
    }

    /** 값을 덮어씁니다(기존 키면 갱신). */
    public void put(long key, long value) {
        if (key == 0) {
            hasZero = true;
            zeroValue = value;
            return;
        }
        int pos = index(key);
        long curr;
        while ((curr = keys[pos]) != 0) {
            if (curr == key) {
                values[pos] = value;
                return;
            }
            pos = (pos + 1) & mask;
        }
        keys[pos] = key;
        values[pos] = value;
        if (++size >= maxFill) {
            resize(keys.length << 1);
        }
    }

    /** 키가 없을 때만 삽입(기존 값 유지). {@link java.util.Map#putIfAbsent} 시맨틱. */
    public void putIfAbsent(long key, long value) {
        if (key == 0) {
            if (!hasZero) {
                hasZero = true;
                zeroValue = value;
            }
            return;
        }
        int pos = index(key);
        long curr;
        while ((curr = keys[pos]) != 0) {
            if (curr == key) {
                return;
            }
            pos = (pos + 1) & mask;
        }
        keys[pos] = key;
        values[pos] = value;
        if (++size >= maxFill) {
            resize(keys.length << 1);
        }
    }

    /** Removes one key and returns its previous value, or {@code absent} when it was not present. */
    public long remove(long key, long absent) {
        if (key == 0) {
            if (!hasZero) return absent;
            long previous = zeroValue;
            hasZero = false;
            zeroValue = 0L;
            return previous;
        }
        int pos = index(key);
        long current;
        while ((current = keys[pos]) != 0) {
            if (current == key) {
                long previous = values[pos];
                shiftKeys(pos);
                size--;
                return previous;
            }
            pos = (pos + 1) & mask;
        }
        return absent;
    }

    /** 저장된 항목 수(제로 키 포함). */
    public int size() {
        return size + (hasZero ? 1 : 0);
    }

    /** 전부 비웁니다(용량은 유지). */
    public void clear() {
        if (size != 0) {
            Arrays.fill(keys, 0L);
        }
        size = 0;
        hasZero = false;
        zeroValue = 0L;
    }

    private int index(long key) {
        long h = key * 0x9E3779B97F4A7C15L; // Fibonacci hashing mix
        h ^= h >>> 32;
        return (int) h & mask;
    }

    private void shiftKeys(int removed) {
        int last;
        int pos = removed;
        while (true) {
            last = pos;
            pos = (pos + 1) & mask;
            long current;
            while ((current = keys[pos]) != 0) {
                int slot = index(current);
                if (last <= pos ? last >= slot || slot > pos : last >= slot && slot > pos) break;
                pos = (pos + 1) & mask;
            }
            if (current == 0) {
                keys[last] = 0;
                values[last] = 0;
                return;
            }
            keys[last] = current;
            values[last] = values[pos];
        }
    }

    private void resize(int newCap) {
        long[] oldKeys = keys;
        long[] oldValues = values;
        keys = new long[newCap];
        values = new long[newCap];
        mask = newCap - 1;
        maxFill = (int) Math.ceil(newCap * loadFactor);
        for (int i = 0; i < oldKeys.length; i++) {
            long k = oldKeys[i];
            if (k != 0) {
                int pos = index(k);
                while (keys[pos] != 0) {
                    pos = (pos + 1) & mask;
                }
                keys[pos] = k;
                values[pos] = oldValues[i];
            }
        }
    }

    private static int capacityFor(int expected, float loadFactor) {
        long need = (long) Math.ceil(Math.max(1, expected) / loadFactor);
        long cap = Long.highestOneBit(Math.max(1, need - 1)) << 1;
        return (int) Math.max(2, cap);
    }
}
