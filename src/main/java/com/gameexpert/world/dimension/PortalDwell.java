package com.gameexpert.world.dimension;

/** 서버 틱 기반 접촉 계수. 이동 패킷 빈도는 체류 시간에 영향을 주지 않는다. */
public final class PortalDwell {
    private int portal;
    private int ticks;
    private long previousTick = Long.MIN_VALUE;
    private long cooldownUntil;
    private boolean exitRequired;

    public boolean observe(long tick, int eligiblePortal) {
        if (eligiblePortal == 0) {
            portal = 0; ticks = 0; exitRequired = false; previousTick = tick;
            return false;
        }
        if (exitRequired || tick < cooldownUntil) { previousTick = tick; return false; }
        if (portal != eligiblePortal || previousTick != tick - 1) ticks = 0;
        portal = eligiblePortal; previousTick = tick;
        if (++ticks < DimensionRegistry.PORTAL_DWELL_TICKS) return false;
        dimensionChanged(tick);
        return true;
    }

    public void dimensionChanged(long tick) {
        portal = 0; ticks = 0; previousTick = tick;
        cooldownUntil = Math.addExact(tick, DimensionRegistry.PORTAL_COOLDOWN_TICKS);
        exitRequired = true;
    }
}
