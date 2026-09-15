package com.gameexpert.engine.dragon;

/**
 * [DRAGON] 바닐라 {@code EnderDragonPhase} 의 11 단계와 그 id(핀 26.3 javap {@code EnderDragonPhase.<clinit>} 순서).
 * id 는 저장·방송 계약이다(정적판 {@code DragonPhase} 와 같다).
 */
public enum DragonPhase {
    HOLDING_PATTERN(0),
    STRAFE_PLAYER(1),
    LANDING_APPROACH(2),
    LANDING(3),
    TAKEOFF(4),
    SITTING_FLAMING(5),
    SITTING_SCANNING(6),
    SITTING_ATTACKING(7),
    CHARGING_PLAYER(8),
    DYING(9),
    HOVERING(10);

    private final int id;

    DragonPhase(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static DragonPhase byId(int id) {
        for (DragonPhase phase : values()) {
            if (phase.id == id) return phase;
        }
        return HOVERING;
    }
}
