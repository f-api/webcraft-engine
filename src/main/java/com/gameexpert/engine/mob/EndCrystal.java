package com.gameexpert.engine.mob;

import java.util.List;

/**
 * [DRAGON] 엔드 수정(바닐라 {@code EndCrystal}, stableId 103). LivingEntity 가 아니어서 체력·AI 가 없고, 어떤 피해든
 * 받으면 부서져 폭발한다 — 권위가 근접·투사체·폭발을 드래곤전({@code DragonFight.hurtCrystal})으로 보내고, 일반
 * 피해 경로({@link #damage})는 받지 않는다. 불에 면역이다(제 칸에 불을 놓고 그 속에 산다). 변종은
 * {@code DATA_SHOW_BOTTOM}: 가시 기둥의 수정({@code SpikeFeature})은 기본값대로 기반암 받침을 보이고
 * ({@code show_bottom}), 아이템으로 놓은 수정은 {@code EndCrystalItem.useOn} 이 {@code setShowBottom(false)} 로 받침을
 * 숨긴다({@code hide_bottom}).
 */
public final class EndCrystal extends Mob {
    public static final String SHOW_BOTTOM = "show_bottom";
    public static final String HIDE_BOTTOM = "hide_bottom";
    private final String variant;

    public EndCrystal(long id, double x, double y, double z, String requestedVariant) {
        super(id, MobType.END_CRYSTAL, x, y, z);
        this.variant = HIDE_BOTTOM.equals(requestedVariant) ? HIDE_BOTTOM : SHOW_BOTTOM;
    }

    @Override
    public String variant() {
        return variant;
    }

    /** 바닐라 {@code EndCrystal#showsBottom}. */
    public boolean showsBottom() {
        return SHOW_BOTTOM.equals(variant);
    }

    @Override
    public boolean immovable() {
        return true;
    }

    /** [DRAGON] 바닐라 EndCrystal 은 LivingEntity 가 아니라 ambient·발소리가 없다. */
    @Override public boolean hasAmbientSound() { return false; }

    @Override public boolean hasStepSound() { return false; }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean damage(double amount, long tickNo) {
        return false;
    }

    /** 드래곤전이 부쉈다: 체력 0(런타임이 틱 끝에 사망 퇴장시킨다, 드랍 없음). */
    public void destroy() {
        setInitialHealth(0.0);
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        return List.of();
    }
}
