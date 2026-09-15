package com.gameexpert.block.snapshot;

import lombok.Getter;

/** resident-only snapshot capture 결과. unavailable은 cold 청크를 생성하거나 활성화하지 않았다는 뜻입니다. */
@Getter
public final class ChunkSnapshotResult {

    private final boolean available;
    private final int cx;
    private final int cz;
    private final long worldEpoch;
    private final ChunkSnapshot snapshot;

    private ChunkSnapshotResult(boolean available, int cx, int cz, long worldEpoch,
            ChunkSnapshot snapshot) {
        this.available = available;
        this.cx = cx;
        this.cz = cz;
        this.worldEpoch = worldEpoch;
        this.snapshot = snapshot;
    }

    public static ChunkSnapshotResult available(ChunkSnapshot snapshot) {
        return new ChunkSnapshotResult(true, snapshot.getCx(), snapshot.getCz(),
                snapshot.getWorldEpoch(), snapshot);
    }

    public static ChunkSnapshotResult unavailable(int cx, int cz, long worldEpoch) {
        return new ChunkSnapshotResult(false, cx, cz, worldEpoch, null);
    }
}
