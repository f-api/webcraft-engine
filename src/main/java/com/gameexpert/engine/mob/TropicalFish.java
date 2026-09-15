package com.gameexpert.engine.mob;

/**
 * 열대어의 서버 권위 수영/육상 생존 동작. 물 양동이로 포획해 아홀로틀 먹이로 쓴다.
 *
 * <p>바닐라 1.21.4 처럼 무늬·바탕색·무늬색 변종을 서버 권위로 정하고 영속 스냅샷에 보존한다.
 * WebCraft 어휘는 바닐라가 이름을 붙인 common 변종 22종이다(docs/MC-REFERENCE.md).
 */
public final class TropicalFish extends AquaticAnimalMob {
    private final String variant;

    TropicalFish(long id, double x, double y, double z, String variant) {
        super(id, MobType.TROPICAL_FISH, x, y, z);
        if (!MobType.TROPICAL_FISH.acceptsVariant(variant)) {
            throw new IllegalArgumentException("Invalid tropical fish variant: " + variant);
        }
        this.variant = variant;
    }

    @Override
    public String variant() {
        return variant;
    }
}
