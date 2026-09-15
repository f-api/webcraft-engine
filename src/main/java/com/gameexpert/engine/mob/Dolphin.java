package com.gameexpert.engine.mob;

import java.util.List;

/** Java 1.21.4 Dolphin moisture and fish/treasure state. */
public final class Dolphin extends AquaticAnimalMob {
    public static final int MAX_MOISTURE_MC_TICKS = 2_400;
    private int moistureMcTicks = MAX_MOISTURE_MC_TICKS;
    private int dryDamageCooldownMcTicks;
    private boolean gotFish;
    private int treasureX;
    private int treasureY;
    private int treasureZ;

    public Dolphin(long id, double x, double y, double z) {
        super(id, MobType.DOLPHIN, x, y, z);
    }

    @Override protected boolean usesGenericDryAirSupply() { return false; }

    public int moistureMcTicks() { return moistureMcTicks; }
    int dryDamageCooldownMcTicks() { return dryDamageCooldownMcTicks; }
    public boolean gotFish() { return gotFish; }
    public int treasureX() { return treasureX; }
    public int treasureY() { return treasureY; }
    public int treasureZ() { return treasureZ; }

    public boolean acceptFish() {
        if (isDead()) return false;
        gotFish = true;
        return true;
    }

    public void setTreasurePos(int x, int y, int z) {
        treasureX = x;
        treasureY = y;
        treasureZ = z;
    }

    public void restoreDolphinState(int moistureTicks, boolean hasFish,
                                    int x, int y, int z) {
        restoreDolphinState(moistureTicks, 0, hasFish, x, y, z);
    }

    public void restoreDolphinState(int moistureTicks, int dryDamageTicks, boolean hasFish,
                                    int x, int y, int z) {
        if (moistureTicks < 0 || moistureTicks > MAX_MOISTURE_MC_TICKS) {
            throw new IllegalArgumentException("invalid persisted Dolphin moisture");
        }
        if (dryDamageTicks < 0 || dryDamageTicks > 10) {
            throw new IllegalArgumentException("invalid persisted Dolphin dry-damage cursor");
        }
        moistureMcTicks = moistureTicks;
        dryDamageCooldownMcTicks = dryDamageTicks;
        gotFish = hasFish;
        setTreasurePos(x, y, z);
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        List<MobEvent> events = super.tick(world, rng);
        if (isDead()) return events;
        int water = bodyWaterPresence(world);
        if (water == BODY_WATER_UNKNOWN) return events;
        boolean hydrated = water == BODY_WATER_WET || world.isRainingAt(
                (int) Math.floor(x), (int) Math.floor(y + height()), (int) Math.floor(z));
        for (int virtualTick = 0; virtualTick < 2; virtualTick++) {
            if (hydrated) {
                moistureMcTicks = MAX_MOISTURE_MC_TICKS;
                dryDamageCooldownMcTicks = 0;
            } else {
                if (moistureMcTicks > 0) moistureMcTicks--;
                if (dryDamageCooldownMcTicks > 0) dryDamageCooldownMcTicks--;
                if (moistureMcTicks == 0 && dryDamageCooldownMcTicks == 0) {
                    damageBypassesArmor(1.0);
                    dryDamageCooldownMcTicks = 10;
                    events = appendEvent(events, new MobEvent.EnvironmentDamage(isDead()));
                    if (isDead()) break;
                }
            }
        }
        return events;
    }
}
