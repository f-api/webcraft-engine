package com.gameexpert.engine.mob;

/** 몹 행동 상태(연출/디버깅용). 몹 종류마다 사용하는 부분집합이 다르다. */
public enum MobState {
    IDLE,     // 대기
    WANDER,   // 무작위 배회
    CHASE,    // 플레이어 추적
    ATTACK,   // 근접 공격 사거리 내
    FLEE,     // 후퇴(스켈레톤: 너무 가까울 때)
    FUSE,     // 점화(크리퍼)
    DEAD      // 사망
}
