package com.gameexpert.block.snapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * 런타임 하나가 소유하는 청크별 단조 버전 ledger입니다.
 *
 * <p>버전은 전역 revision이 아니라 (cx, cz)마다 독립적으로 1부터 증가합니다. 수신자는 마지막 적용
 * 버전과 다음 delta의 fromVersion을 비교해 누락을 판별하고 전체 snapshot을 다시 요청합니다.</p>
 */
public final class ChunkVersionLedger {

    private final Map<Long, Long> versions = new HashMap<>();

    public long current(int cx, int cz) {
        return versions.getOrDefault(chunkKey(cx, cz), 0L);
    }

    public long advance(int cx, int cz) {
        long next = Math.addExact(current(cx, cz), 1L);
        versions.put(chunkKey(cx, cz), next);
        return next;
    }

    public void forget(int cx, int cz) {
        versions.remove(chunkKey(cx, cz));
    }

    public void clear() {
        versions.clear();
    }

    /** 수신자가 fromVersion까지 적용했다면 이 delta는 연속이고, 아니면 중간 update가 누락됐다. */
    public static boolean hasGap(long receiverVersion, ChunkVersion delta) {
        return receiverVersion != delta.getFromVersion();
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffff_ffffL);
    }
}
