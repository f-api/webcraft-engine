package com.gameexpert.world.repository;

import com.gameexpert.world.entity.WorldClockState;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 월드 시계 전용 행 저장소. 시계 체크포인트가 {@code worlds} 행 잠금을 건드리지 않게 하려고
 * 분리했습니다({@link WorldClockState} 참고).
 */
public interface WorldClockStateRepository extends JpaRepository<WorldClockState, Long> {
}
