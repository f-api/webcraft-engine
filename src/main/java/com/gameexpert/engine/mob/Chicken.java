package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.engine.inventory.PlayerInventory;

/**
 * 닭의 산란. AI 는 {@link AnimalMob} 그대로이고 여기에는 바닐라 {@code Chicken.aiStep} 의
 * {@code eggTime} 커서만 얹는다. 성체만 낳고, 낳는 순간 {@code entity.chicken.egg} 소리가 난다.
 */
public final class Chicken extends AnimalMob {
    /** 다음 산란까지 남은 MC 틱. 스폰/부화 시 6,000~11,999 로 초기화된다. */
    private int eggMcTicks;

    /** [FARM-VARIANT] {@code variant} 는 1.21.5 기후 변종(temperate/warm/cold). */
    Chicken(long id, double x, double y, double z, String variant) {
        super(MobType.CHICKEN, id, x, y, z, variant);
        this.eggMcTicks = FarmAnimalRules.initialChickenEggMcTicks(id);
    }

    public int eggMcTicks() { return eggMcTicks; }

    void restoreChickenState(int restoredEggMcTicks) {
        if (restoredEggMcTicks < 0
                || restoredEggMcTicks > FarmAnimalRules.CHICKEN_EGG_MIN_MC_TICKS
                        + FarmAnimalRules.CHICKEN_EGG_ROLL_BOUND) {
            throw new IllegalArgumentException("invalid persisted Chicken egg cursor");
        }
        eggMcTicks = restoredEggMcTicks;
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        List<MobEvent> events = super.tick(world, rng);
        if (isDead() || isBaby()) return events;
        // 서버 10 TPS 틱마다 바닐라 20 TPS 커서를 두 칸 전진시킨다.
        for (int step = 0; step < 2; step++) {
            if (--eggMcTicks > 0) continue;
            eggMcTicks = FarmAnimalRules.nextChickenEggMcTicks(rng);
            setPersistenceRequired(true);
            short egg = switch (FarmAnimalRules.chickenLayEgg(variant())) {
                case EGG -> PlayerInventory.EGG;
                case BLUE_EGG -> PlayerInventory.BLUE_EGG;
                case BROWN_EGG -> PlayerInventory.BROWN_EGG;
            };
            events = appendEvent(events,
                    new MobEvent.DropItem(egg, 1, x, y, z));
            events = appendEvent(events, new MobEvent.Sound("egg"));
        }
        return events;
    }
}
