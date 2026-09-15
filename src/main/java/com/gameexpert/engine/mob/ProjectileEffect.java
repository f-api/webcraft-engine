package com.gameexpert.engine.mob;

import com.gameexpert.engine.effect.StatusEffect;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 명중·착탄 시 부여할 상태이상 한 건. 팁 화살(스트레이·보그드)과 투척 물약이 같은 형태로 실어 나릅니다.
 *
 * @param durationTicks 10 TPS 서버 틱 기준 지속시간. 즉발 효과는 0.
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
public final class ProjectileEffect {
    private final StatusEffect effect;
    private final int amplifier;
    private final int durationTicks;

    public ProjectileEffect(StatusEffect effect, int amplifier, int durationTicks) {
        this.effect = effect;
        this.amplifier = amplifier;
        this.durationTicks = durationTicks;
    }

    /** 스플래시 거리 감쇠를 적용한 사본. 감쇠 후 지속이 0이면 null. */
    public ProjectileEffect scaled(double factor) {
        if (effect.instantaneous()) return this;
        int scaledTicks = (int) (durationTicks * factor);
        return scaledTicks <= 0 ? null : new ProjectileEffect(effect, amplifier, scaledTicks);
    }
}
