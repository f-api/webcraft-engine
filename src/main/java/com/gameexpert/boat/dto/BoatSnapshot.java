package com.gameexpert.boat.dto;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 설치된 보트 하나의 영속 상태. 운전자와 몹 승객은 접속 세션 상태라 저장하지 않는다
 * (재입장 시 운전자는 비어 있고, 몹은 근접 자동 탑승으로 다시 태워진다).
 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public final class BoatSnapshot {
    private final long boatId;
    private final double x;
    private final double y;
    private final double z;
    private final double yaw;
}
