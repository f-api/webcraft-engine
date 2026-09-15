package com.gameexpert.world.dimension;

import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;

/** owner 접촉 관측만 비동기 이동 제어로 넘긴다. 클라이언트 지정 목적지는 없다. */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public final class PortalTravelRequest {
    private final long worldId;
    private final String nickname;
    private final String connectionId;
    private final int portalBlock;
    private final boolean respawn;
    public PortalTravelRequest(long worldId, String nickname, String connectionId, int portalBlock) {
        this(worldId, nickname, connectionId, portalBlock, false);
    }
}
