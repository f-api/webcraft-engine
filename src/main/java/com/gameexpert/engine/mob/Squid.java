package com.gameexpert.engine.mob;

/** 일반 오징어. 피격 먹물은 클라이언트가 서버 mobHurt 사건에 맞춰 표시한다. */
public class Squid extends AquaticAnimalMob {
    Squid(long id, double x, double y, double z) {
        this(id, MobType.SQUID, x, y, z);
    }

    protected Squid(long id, MobType type, double x, double y, double z) {
        super(id, type, x, y, z);
    }
}
