package com.gameexpert.engine.mob;

/** 몹 이동 물리 모드. 걷기·벽 등반·비행·수영은 같은 충돌 경로에서 종별 가속을 적용한다. */
public enum MoveMode {
    WALK,
    CLIMB,
    FLY,
    FLY_NOCLIP,
    SWIM
}
