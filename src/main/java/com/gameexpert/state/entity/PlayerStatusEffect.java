package com.gameexpert.state.entity;

import com.gameexpert.engine.effect.StatusEffect;
import com.gameexpert.engine.effect.StatusEffects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 플레이어의 지속 상태이상 한 건을 MC 틱 정밀도로 저장하는 값 객체. */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerStatusEffect {

    @Enumerated(EnumType.STRING)
    @Column(name = "effect", nullable = false, length = 32)
    private StatusEffect effect;

    @Column(nullable = false)
    private int amplifier;

    @Column(name = "remaining_mc_ticks", nullable = false)
    private int remainingMcTicks;

    @Column(name = "period_accum_mc_ticks", nullable = false)
    private int periodAccumMcTicks;

    public PlayerStatusEffect(StatusEffects.PersistentEffect effect) {
        this.effect = effect.effect();
        this.amplifier = effect.amplifier();
        this.remainingMcTicks = effect.remainingMcTicks();
        this.periodAccumMcTicks = effect.periodAccumMcTicks();
    }

    public StatusEffects.PersistentEffect toSnapshot() {
        return new StatusEffects.PersistentEffect(
                effect, amplifier, remainingMcTicks, periodAccumMcTicks);
    }
}
