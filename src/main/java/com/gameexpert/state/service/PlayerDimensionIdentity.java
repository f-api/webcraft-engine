package com.gameexpert.state.service;

import lombok.Getter;
import lombok.experimental.Accessors;

/** 별도 공간 aggregate와 하나의 root 플레이어 aggregate를 결박한 서버 내부 세대. */
@Getter
@Accessors(fluent = true)
public final class PlayerDimensionIdentity {
    private final long rootWorldId;
    private final long runtimeWorldId;
    private final long travelRevision;
    private final String dimension;

    public PlayerDimensionIdentity(long rootWorldId, long runtimeWorldId, long travelRevision) {
        this(rootWorldId, runtimeWorldId, "overworld", travelRevision);
    }

    public PlayerDimensionIdentity(long rootWorldId, long runtimeWorldId, String dimension, long travelRevision) {
        if (dimension == null || !dimension.matches("[a-z][a-z0-9_]{0,31}")
                || ("overworld".equals(dimension) != (rootWorldId == runtimeWorldId))) {
            throw new IllegalArgumentException("dimension/spatial identity mismatch");
        }
        this.dimension = dimension;
        if (rootWorldId <= 0 || runtimeWorldId <= 0 || travelRevision < 0) {
            throw new IllegalArgumentException("positive world identities and travel revision required");
        }
        this.rootWorldId = rootWorldId;
        this.runtimeWorldId = runtimeWorldId;
        this.travelRevision = travelRevision;
    }

}
