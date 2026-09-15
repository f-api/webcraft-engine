package com.gameexpert.engine.mob;

/** 실버피시: 16블록 내 플레이어를 추적하고 근접 공격한다. HP 8. */
public final class Silverfish extends MeleeMob {
    private static final int MAX_TOTAL_SUMMONS = 6;

    private int pendingSummons;
    private int totalSummoned;

    public Silverfish(long id, double x, double y, double z) {
        super(id, MobType.SILVERFISH, x, y, z);
    }

    @Override protected double detectRange() { return 16.0; }
    @Override protected double attackRange() { return 1.5; }
    @Override protected int attackDamage() { return 1; }
    @Override protected int attackCooldownTicks() { return commonAttackCooldownTicks(); }
    @Override protected boolean climbWalls() { return false; }

    @Override
    protected void onDamageSurvived(long tickNo) {
        if (totalSummoned >= MAX_TOTAL_SUMMONS) return;
        int hash = mix32((int) (id ^ (id >>> 32) ^ tickNo));
        pendingSummons = Math.min(MAX_TOTAL_SUMMONS - totalSummoned,
                1 + Math.floorMod(hash, 3));
    }

    /** MobSystem이 다음 틱의 안전한 스폰 단계에서 한 번 소비한다. */
    public int consumePendingSummons() {
        int requested = pendingSummons;
        pendingSummons = 0;
        return requested;
    }

    public void recordSummons(int count) {
        totalSummoned = Math.min(MAX_TOTAL_SUMMONS, totalSummoned + Math.max(0, count));
    }

    private static int mix32(int value) {
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        return value ^ value >>> 16;
    }
}
