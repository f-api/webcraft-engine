package com.gameexpert.engine.mob;

/** 피격 뒤 5초간 발광 무늬가 어두워지는 발광 오징어. */
public final class GlowSquid extends Squid {
    private static final int DARKEN_TICKS = 50;
    private int darkenTicks;

    GlowSquid(long id, double x, double y, double z) {
        super(id, MobType.GLOW_SQUID, x, y, z);
    }

    @Override
    public void onHurt(String attackerNickname, double attackerX, double attackerZ) {
        darkenTicks = DARKEN_TICKS;
    }

    @Override
    public int visualFlags() {
        return darkenTicks > 0 ? Mob.VISUAL_GLOW_SQUID_DARKENED : 0;
    }

    @Override
    public java.util.List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (darkenTicks > 0) darkenTicks--;
        return super.tick(world, rng);
    }
}
