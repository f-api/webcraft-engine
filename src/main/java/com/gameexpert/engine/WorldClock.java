package com.gameexpert.engine;

import com.gameexpert.engine.effect.StatusEffects;

/**
 * [제공코드] 월드 시간(CONTRACT §3). 0~11999 순환, 6500~11499가 밤.
 *
 * {@code worldTime}은 매 틱 {@code timeScale}만큼 증가합니다(기본 1). QA용 {@code game.time-scale}로
 * 배수를 올리면 낮/밤이 빠르게 흐릅니다. {@code tickCount}는 시간 스케일과 무관한 실제 틱 수로,
 * 유체 스케줄·주기 저장·timeSync 주기의 기준입니다.
 */
public final class WorldClock {

    public static final int DAY_LENGTH = 12_000;
    public static final int NIGHT_START = 6_500;
    public static final int NIGHT_END = 11_500;
    private static final float[] MOON_BRIGHTNESS = {
            1.0f, 0.75f, 0.5f, 0.25f, 0.0f, 0.25f, 0.5f, 0.75f
    };

    private volatile long worldTime;
    private volatile long dayCount;
    private volatile long gameTimeMcTicks;
    private long tickCount;
    private final boolean frozen;

    public WorldClock(long initialWorldTime) {
        this(initialWorldTime, 0, false);
    }

    /** QA용 고정 시계도 실제 틱 수는 계속 세어 기존 스케줄 기준을 보존합니다. */
    public WorldClock(long initialWorldTime, boolean frozen) {
        this(initialWorldTime, 0, frozen);
    }

    public WorldClock(long initialWorldTime, long initialDayCount) {
        this(initialWorldTime, initialDayCount, false);
    }

    /** 저장된 절대 일수와 현재 일중 시각을 복원합니다. */
    public WorldClock(long initialWorldTime, long initialDayCount, boolean frozen) {
        this(initialWorldTime, initialDayCount,
                initialDayCount * DAY_LENGTH * StatusEffects.MC_TICKS_PER_SERVER_TICK
                        + Math.floorMod(initialWorldTime, DAY_LENGTH)
                                * StatusEffects.MC_TICKS_PER_SERVER_TICK,
                frozen);
    }

    /** Restores daylight and vanilla absolute game time as independent clocks. */
    public WorldClock(long initialWorldTime, long initialDayCount,
            long initialGameTimeMcTicks, boolean frozen) {
        if (initialDayCount < 0) {
            throw new IllegalArgumentException("dayCount must be non-negative");
        }
        if (initialGameTimeMcTicks < 0) {
            throw new IllegalArgumentException("gameTimeMcTicks must be non-negative");
        }
        this.worldTime = Math.floorMod(initialWorldTime, DAY_LENGTH);
        this.dayCount = initialDayCount;
        this.gameTimeMcTicks = initialGameTimeMcTicks;
        this.frozen = frozen;
    }

    /** 매 틱 호출: 실제 틱 수를 올리고 월드 시간을 timeScale만큼 순환 증가시킵니다. */
    public void advance(int timeScale) {
        tickCount++;
        gameTimeMcTicks += StatusEffects.MC_TICKS_PER_SERVER_TICK;
        if (frozen) return;
        long advancedTime = worldTime + timeScale;
        dayCount += Math.floorDiv(advancedTime, DAY_LENGTH);
        worldTime = Math.floorMod(advancedTime, DAY_LENGTH);
    }

    public long worldTime() {
        return worldTime;
    }

    /** 일수를 바꾸지 않고 일중 시각만 강제 설정합니다. 0~11999 로 순환 보정. */
    public void setWorldTime(long value) {
        this.worldTime = Math.floorMod(value, DAY_LENGTH);
    }

    /** 전원 수면 성공 시 다음 날 아침으로 정확히 한 번 진행합니다. */
    public void skipToNextMorning() {
        dayCount++;
        worldTime = 0;
    }

    public long dayCount() {
        return dayCount;
    }

    /** Vanilla absolute game time in 20-TPS Minecraft ticks; sleep/daylight scale never changes it. */
    public long gameTimeMcTicks() {
        return gameTimeMcTicks;
    }

    public int moonPhase() {
        return moonPhase(dayCount);
    }

    public float moonBrightness() {
        return moonBrightness(dayCount);
    }

    public static int moonPhase(long dayCount) {
        return Math.floorMod(dayCount, MOON_BRIGHTNESS.length);
    }

    public static float moonBrightness(long dayCount) {
        return MOON_BRIGHTNESS[moonPhase(dayCount)];
    }

    public long tickCount() {
        return tickCount;
    }

    public boolean isNight() {
        return isNight(worldTime);
    }

    /** 인스턴스 시계가 없는 스폰/월드 뷰 어댑터용 동일한 시간 경계 판정. */
    public static boolean isNight(long worldTime) {
        long time = Math.floorMod(worldTime, DAY_LENGTH);
        return time >= NIGHT_START && time < NIGHT_END;
    }
}
