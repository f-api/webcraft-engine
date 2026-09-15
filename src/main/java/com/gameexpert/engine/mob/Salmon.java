package com.gameexpert.engine.mob;

/** 연어의 서버 권위 수영/육상 생존 동작. */
public final class Salmon extends AquaticAnimalMob {
    Salmon(long id, double x, double y, double z) {
        super(id, MobType.SALMON, x, y, z);
    }
}
