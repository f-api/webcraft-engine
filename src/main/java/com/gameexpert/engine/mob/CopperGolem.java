package com.gameexpert.engine.mob;

/** Copper golem identity; oxidation and button interaction are authority-owned state. */
public final class CopperGolem extends AnimalMob {
    public static final int MIN_WEATHERING_DELAY_MC_TICKS = 504_000;
    public static final int WEATHERING_DELAY_RANGE_MC_TICKS = 48_000;
    public static final float STATUE_CHANCE_PER_MC_TICK = 0.0058f;
    public static final long WEATHERING_UNSET = -1L;
    public static final long WEATHERING_WAXED = -2L;

    private int oxidationAge;
    private boolean waxed;
    private int pose;
    private long nextWeatheringMcTick = WEATHERING_UNSET;
    private boolean statuePending;

    public CopperGolem(long id, double x, double y, double z) {
        super(MobType.COPPER_GOLEM, id, x, y, z, null);
        pose = Math.floorMod((int) (id ^ id >>> 32), 4);
    }

    public int oxidationAge() { return oxidationAge; }
    public boolean waxed() { return waxed; }
    public int pose() { return pose; }
    public long nextWeatheringMcTick() { return nextWeatheringMcTick; }
    public boolean statuePending() { return statuePending; }
    public int statueState() {
        int facing = Math.floorMod((int) Math.floor((yaw / (Math.PI * 0.5)) + 0.5), 4);
        return facing | pose << 2;
    }

    @Override public int visualFlags() {
        return (oxidationAge << Mob.VISUAL_COPPER_GOLEM_OXIDATION_SHIFT & Mob.VISUAL_COPPER_GOLEM_OXIDATION_MASK)
                | (waxed ? Mob.VISUAL_COPPER_GOLEM_WAXED : 0)
                | (pose << Mob.VISUAL_COPPER_GOLEM_POSE_SHIFT & Mob.VISUAL_COPPER_GOLEM_POSE_MASK);
    }

    public boolean wax() {
        if (waxed) return false;
        waxed = true;
        nextWeatheringMcTick = WEATHERING_WAXED;
        setPersistenceRequired(true);
        return true;
    }

    public boolean scrape() {
        if (waxed) {
            waxed = false;
            nextWeatheringMcTick = WEATHERING_UNSET;
        }
        else if (oxidationAge > 0) oxidationAge--;
        else return false;
        if (!waxed) nextWeatheringMcTick = WEATHERING_UNSET;
        setPersistenceRequired(true);
        return true;
    }

    public boolean advanceOxidationStage() {
        if (waxed || oxidationAge >= 3) return false;
        oxidationAge++;
        setPersistenceRequired(true);
        return true;
    }

    public boolean tickWeathering(long currentMcTick, MobRandom rng) {
        if (waxed || statuePending) return false;
        if (oxidationAge >= 3) return false;
        if (nextWeatheringMcTick == WEATHERING_UNSET) {
            nextWeatheringMcTick = currentMcTick + MIN_WEATHERING_DELAY_MC_TICKS
                    + rng.nextInt(WEATHERING_DELAY_RANGE_MC_TICKS);
            return false;
        }
        if (currentMcTick < nextWeatheringMcTick) return false;
        long previousDue = nextWeatheringMcTick;
        oxidationAge++;
        nextWeatheringMcTick = oxidationAge < 3
                ? previousDue + MIN_WEATHERING_DELAY_MC_TICKS
                        + rng.nextInt(WEATHERING_DELAY_RANGE_MC_TICKS)
                : WEATHERING_UNSET;
        setPersistenceRequired(true);
        return true;
    }

    /** Called twice per 10 TPS authority tick to mirror the official 20 TPS AI draw order. */
    public boolean planStatue(MobRandom rng) {
        if (waxed || oxidationAge != 3 || statuePending) return false;
        for (int mcTick = 0; mcTick < 2; mcTick++) {
            if (rng.nextFloat() <= STATUE_CHANCE_PER_MC_TICK) {
                statuePending = true;
                setPersistenceRequired(true);
                return true;
            }
        }
        return false;
    }

    public void rejectStatue() { statuePending = false; }

    public void restoreCopperState(int age, boolean restoredWaxed, int restoredPose,
            long restoredNextWeatheringMcTick, boolean restoredStatuePending) {
        if (age < 0 || age > 3 || restoredPose < 0 || restoredPose > 3) {
            throw new IllegalArgumentException("invalid copper golem state");
        }
        if (restoredNextWeatheringMcTick < WEATHERING_WAXED
                || restoredWaxed != (restoredNextWeatheringMcTick == WEATHERING_WAXED)
                || restoredStatuePending && (restoredWaxed || age != 3)) {
            throw new IllegalArgumentException("invalid copper golem weathering state");
        }
        oxidationAge = age;
        waxed = restoredWaxed;
        pose = restoredPose;
        nextWeatheringMcTick = restoredNextWeatheringMcTick;
        statuePending = restoredStatuePending;
    }

    public void restoreFromStatue(String customName, short heldItem, int heldDurability,
            int restoredPose) {
        restoreCopperState(0, false, restoredPose, WEATHERING_UNSET, false);
        setCustomName(customName);
        restorePersistentCombatState(maxHp(), false, heldItem, heldDurability,
                (short) 0, 0, (short) 0, 0, (short) 0, 0, (short) 0, 0);
        setPersistenceRequired(true);
    }
}
