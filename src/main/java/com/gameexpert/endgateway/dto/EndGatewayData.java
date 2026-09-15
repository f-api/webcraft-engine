package com.gameexpert.endgateway.dto;

/**
 * [END-GATEWAY] 엔드 관문 블록 엔티티 하나({@code TheEndGatewayBlockEntity} 의 {@code exit_portal} ·
 * {@code ExactTeleport}). 출구가 아직 없으면 {@code hasExit} 가 거짓이고 세 좌표는 0 이다.
 * 나이({@code Age})·쿨다운은 저장하지 않는다 — 생성 빔은 사건으로, 주의 빔은 월드 시계로 푼다(CONTRACT §11A).
 */
public record EndGatewayData(int x, int y, int z, boolean hasExit, int exitX, int exitY, int exitZ,
        boolean exact) {
    public EndGatewayData {
        if (!hasExit && (exitX != 0 || exitY != 0 || exitZ != 0 || exact)) {
            throw new IllegalArgumentException("gateway without exit carries no exit coordinates");
        }
    }

    public static EndGatewayData withoutExit(int x, int y, int z) {
        return new EndGatewayData(x, y, z, false, 0, 0, 0, false);
    }

    public static EndGatewayData withExit(int x, int y, int z, int exitX, int exitY, int exitZ, boolean exact) {
        return new EndGatewayData(x, y, z, true, exitX, exitY, exitZ, exact);
    }
}
