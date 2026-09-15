package com.gameexpert.block.snapshot;

import lombok.Getter;

/** 한 청크의 순서 있는 변경 범위입니다. fromVersion은 수신자가 이미 가진 마지막 버전입니다. */
@Getter
public final class ChunkVersion {

    private final int cx;
    private final int cz;
    private final long fromVersion;
    private final long toVersion;

    public ChunkVersion(int cx, int cz, long fromVersion, long toVersion) {
        if (fromVersion < 0 || toVersion <= fromVersion) {
            throw new IllegalArgumentException("청크 delta 버전 범위가 올바르지 않습니다.");
        }
        this.cx = cx;
        this.cz = cz;
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
    }
}
