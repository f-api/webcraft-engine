package com.gameexpert.engine.effect;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 엔티티 1개(플레이어 또는 몹)가 보유한 상태이상 목록.
 *
 * <p>모든 시간은 MC 틱(20 TPS)으로 보관하고 {@link #tick()} 1회가 {@link #MC_TICKS_PER_SERVER_TICK}만큼
 * 진행합니다. 서버 틱(10 TPS)으로 나눠 저장하면 주기 25 MC 틱이 12.5 가 되어 반올림 오차가 누적되므로
 * 저장 단위를 MC 틱으로 고정했습니다.
 *
 * <p>스레딩: 틱 스레드 전용입니다.
 */
public final class StatusEffects {

    /** 10 TPS 서버 틱 1회가 진행하는 MC 틱 수. */
    public static final int MC_TICKS_PER_SERVER_TICK = 2;
    /** 독 기본 피해 주기(MC 틱). 앰프 1레벨마다 절반이 된다. */
    public static final int POISON_BASE_INTERVAL_MC_TICKS = 25;
    /** 독 1회 피해량(방어구 무시). */
    public static final int POISON_DAMAGE = 1;
    /** 플레이어 재생 II 물리 축의 주기(MC 틱). */
    private static final int PLAYER_REGEN_INTERVAL_MC_TICKS = 25;
    /** 감속 1레벨당 이동속도 감소율. */
    public static final double SLOWNESS_PER_LEVEL = 0.15;
    /** 신속 1레벨당 이동속도 증가율(바닐라 movement_speed 0.2 배). */
    public static final double SPEED_PER_LEVEL = 0.2;
    /** 나약 1레벨당 근접 피해 감소량. */
    public static final double WEAKNESS_PER_LEVEL = 4.0;
    /**
     * [POTION-GAP] 힘 1레벨당 근접 피해 증가량. 바닐라 1.21 {@code MobEffects.DAMAGE_BOOST} 의
     * {@code ATTACK_DAMAGE} ADD_VALUE 3.0 이며, 나약과 <b>같은 덧셈 자리</b>에 반대 부호로
     * 들어간다(신속/감속이 이동속도 배율 한 자리를 나눠 쓰는 것과 같은 꼴).
     */
    public static final double STRENGTH_PER_LEVEL = 3.0;
    /** 고통 1레벨 즉시 피해. 레벨마다 2배. */
    public static final int INSTANT_DAMAGE_BASE = 6;
    /** [GOLD-FOOD] 저항 1레벨당 피해 감소율. 바닐라 {@code (25 - level * 5) / 25} 의 5/25 다. */
    public static final double RESISTANCE_PER_LEVEL = 0.20;

    /** 목록에 담긴 효과 1건의 불변 스냅샷(프로토콜 DTO 재료). */
    @Getter
    @Accessors(fluent = true)
    @EqualsAndHashCode
    public static final class ActiveEffect {
        private final StatusEffect effect;
        private final int amplifier;
        private final int remainingTicks;
        /**
         * [BEACON] 바닐라 {@code MobEffectInstance.ambient}. 신호기가 준 효과는 ambient 라 HUD 틀과 입자가
         * 옅다. 효과 목록의 휘발 표식이며 저장하지 않는다(재접속 뒤 다음 신호기 맥박이 다시 세운다).
         */
        private final boolean ambient;

        public ActiveEffect(StatusEffect effect, int amplifier, int remainingTicks) {
            this(effect, amplifier, remainingTicks, false);
        }

        public ActiveEffect(StatusEffect effect, int amplifier, int remainingTicks, boolean ambient) {
            this.effect = effect;
            this.amplifier = amplifier;
            this.remainingTicks = remainingTicks;
            this.ambient = ambient;
        }
    }

    /**
     * DB 저장 전용 스냅샷. 프로토콜의 {@link ActiveEffect} 는 남은 시간을 10 TPS 단위로 올림해
     * 보내므로, 그대로 저장하면 홀수 MC 틱과 독의 다음 피해 시점이 재접속마다 달라진다.
     */
    public record PersistentEffect(
            StatusEffect effect,
            int amplifier,
            int remainingMcTicks,
            int periodAccumMcTicks) {

        public PersistentEffect {
            if (effect == null || effect.instantaneous()) {
                throw new IllegalArgumentException("지속 상태이상 종류가 올바르지 않습니다");
            }
            if (amplifier < 0 || remainingMcTicks <= 0 || periodAccumMcTicks < 0) {
                throw new IllegalArgumentException("상태이상 저장값이 올바르지 않습니다");
            }
            if (effect == StatusEffect.POISON) {
                if (periodAccumMcTicks >= poisonIntervalMcTicks(amplifier)) {
                    throw new IllegalArgumentException("독 주기 누적값이 범위를 벗어났습니다");
                }
            } else if (periodAccumMcTicks != 0) {
                throw new IllegalArgumentException("비주기 상태이상에 주기 누적값이 있습니다");
            }
        }
    }

    /**
     * 플레이어가 기존 물리 축으로 처리하는 재생·흡수·화염 저항의 정확한 저장 시계.
     * 목록 효과와 별도인 이유는 현재 피해/추가 체력 로직이 이 축을 권위 상태로 사용하기 때문이다.
     */
    @Getter
    @Accessors(fluent = true)
    @EqualsAndHashCode
    public static final class PersistentPlayerEffectClocks {
        private final int regenerationMcTicksRemaining;
        private final int regenerationMcTickAccum;
        private final int absorptionTicksRemaining;
        private final int absorptionPoints;
        private final int fireResistanceTicksRemaining;
        private final int bandageHealingTicks;

        public PersistentPlayerEffectClocks(int regenerationMcTicksRemaining, int regenerationMcTickAccum,
                int absorptionTicksRemaining, int absorptionPoints, int fireResistanceTicksRemaining) {
            this(regenerationMcTicksRemaining, regenerationMcTickAccum, absorptionTicksRemaining,
                    absorptionPoints, fireResistanceTicksRemaining, 0);
        }

        public PersistentPlayerEffectClocks(int regenerationMcTicksRemaining, int regenerationMcTickAccum,
                int absorptionTicksRemaining, int absorptionPoints, int fireResistanceTicksRemaining,
                int bandageHealingTicks) {
            if (regenerationMcTicksRemaining < 0
                    || regenerationMcTickAccum < 0
                    || regenerationMcTickAccum >= PLAYER_REGEN_INTERVAL_MC_TICKS
                    || absorptionTicksRemaining < 0
                    || absorptionPoints < 0
                    || fireResistanceTicksRemaining < 0 || bandageHealingTicks < 0 || bandageHealingTicks > 60) {
                throw new IllegalArgumentException("플레이어 상태이상 시계가 올바르지 않습니다");
            }
            this.regenerationMcTicksRemaining = regenerationMcTicksRemaining;
            this.regenerationMcTickAccum = regenerationMcTickAccum;
            this.absorptionTicksRemaining = absorptionTicksRemaining;
            this.absorptionPoints = absorptionPoints;
            this.fireResistanceTicksRemaining = fireResistanceTicksRemaining;
            this.bandageHealingTicks = bandageHealingTicks;
        }

        public static PersistentPlayerEffectClocks empty() {
            return new PersistentPlayerEffectClocks(0, 0, 0, 0, 0);
        }
    }

    private static final class Entry {
        private int amplifier;
        private int remainingMcTicks;
        private int periodAccumMcTicks;
        /** [BEACON] 마지막으로 이 항목을 세운 부여가 ambient 였는가(휘발). */
        private boolean ambient;

        private Entry(int amplifier, int remainingMcTicks) {
            this.amplifier = amplifier;
            this.remainingMcTicks = remainingMcTicks;
        }
    }

    private final EnumMap<StatusEffect, Entry> active = new EnumMap<>(StatusEffect.class);
    private boolean dirty;
    /** [COOKING] 직전 tick() 이 포만감으로 흘려보낸 MC 틱 수. tick() 마다 0 으로 되돌아간다. */
    private int saturationMcTicksElapsed;
    /** [COOKING] 위 값에 짝지어지는 포만감 앰프. */
    private int saturationAmplifier;
    /** 직전 tick()에서 허기가 소비한 MC 틱 수. 마지막 홀수 틱도 보존한다. */
    private int hungerMcTicksElapsed;
    /** [TRIAL-GAP] 직전 tick() 에서 재생이 회복 차례를 맞은 횟수(바닐라 applyEffectTick 호출 수). */
    private int regenerationHeals;
    /** 위 MC 틱 수에 짝지어지는 허기 앰프. */
    private int hungerAmplifier;

    /** [END-CITY] 즉시 회복량(바닐라 {@code HealOrHarmMobEffect}: {@code 4 << amp}). */
    public static int instantHealth(int amplifier) {
        return 4 << Math.max(0, amplifier);
    }

    /** 즉시 피해량(6 &lt;&lt; 앰프). 방어구를 무시한다. */
    public static int instantDamage(int amplifier) {
        return INSTANT_DAMAGE_BASE << Math.max(0, amplifier);
    }

    /** 독 피해 주기(MC 틱). 최소 1틱. */
    public static int poisonIntervalMcTicks(int amplifier) {
        return Math.max(1, POISON_BASE_INTERVAL_MC_TICKS >> Math.max(0, amplifier));
    }

    /**
     * [GOLD-FOOD] 저항이 남기는 피해 배율. 바닐라
     * {@code LivingEntity#getDamageAfterMagicAbsorb} 의 {@code (25 - (amp + 1) * 5) / 25} 이며
     * 레벨 5(앰프 4) 이상이면 0(완전 무효)이다.
     *
     * @param amplifier 앰프(0 = 저항 I)
     */
    public static double resistanceMultiplier(int amplifier) {
        int level = Math.max(0, amplifier) + 1;
        return Math.max(0.0, 1.0 - level * RESISTANCE_PER_LEVEL);
    }

    /** [TRIAL-GAP] 바닐라 RegenerationMobEffect 주기 {@code 50 >> amplifier} MC 틱. */
    public static int regenerationIntervalMcTicks(int amplifier) {
        return amplifier >= 31 ? 0 : 50 >> Math.max(0, amplifier);
    }

    /**
     * [TRIAL-GAP] 직전 {@link #tick()} 에서 재생이 회복 차례를 맞은 횟수. 호출부는 체력이 최대
     * 미만일 때 한 번에 1 씩 회복한다(바닐라 {@code applyEffectTick}).
     */
    public int regenerationHeals() {
        return regenerationHeals;
    }

    /** 서버 틱 지속시간을 MC 틱으로 환산한다. */
    public static int serverTicksToMcTicks(int serverTicks) {
        return Math.max(0, serverTicks) * MC_TICKS_PER_SERVER_TICK;
    }

    /**
     * 효과를 부여합니다. 바닐라 규칙대로 앰프가 더 높으면 무조건 덮어쓰고, 앰프가 같으면 남은 시간이
     * 더 길 때만 갱신합니다. 앰프가 더 낮으면 무시합니다.
     *
     * @param durationServerTicks 10 TPS 서버 틱 기준 지속시간
     * @return 목록이 실제로 바뀌어 브로드캐스트가 필요하면 true
     */
    public boolean apply(StatusEffect effect, int amplifier, int durationServerTicks) {
        return applyMcTicks(effect, amplifier, serverTicksToMcTicks(durationServerTicks));
    }

    /**
     * [COOKING] 지속을 <b>MC 틱 그대로</b> 받는 부여. 홀수 MC 틱(수상한 스튜 민들레의 7틱)은
     * 서버 틱으로 나누면 3.5 가 되어 표현할 수 없으므로, 바닐라 틱수를 그대로 옮겨야 하는
     * 호출부는 이쪽을 쓴다. 갱신 규칙은 {@link #apply} 와 완전히 같다.
     */
    public boolean applyMcTicks(StatusEffect effect, int amplifier, int mcTicks) {
        return applyMcTicks(effect, amplifier, mcTicks, false);
    }

    /**
     * [BEACON] ambient 부여(바닐라 {@code new MobEffectInstance(effect, duration, amplifier, true, true)}).
     * 갱신 규칙은 {@link #apply} 와 같고, 실제로 갱신될 때 그 항목의 ambient 표식도 이 부여를 따른다
     * (바닐라 {@code MobEffectInstance.update} 가 ambient 를 새 인스턴스 값으로 덮는 것과 같다).
     */
    public boolean applyAmbientMcTicks(StatusEffect effect, int amplifier, int mcTicks) {
        return applyMcTicks(effect, amplifier, mcTicks, true);
    }

    private boolean applyMcTicks(StatusEffect effect, int amplifier, int mcTicks, boolean ambient) {
        if (effect == null || effect.instantaneous()) return false;
        if (mcTicks <= 0) return false;
        int level = Math.max(0, amplifier);
        Entry existing = active.get(effect);
        if (existing == null) {
            Entry entry = new Entry(level, mcTicks);
            entry.ambient = ambient;
            active.put(effect, entry);
            dirty = true;
            return true;
        }
        if (level > existing.amplifier) {
            existing.amplifier = level;
            existing.remainingMcTicks = mcTicks;
            existing.periodAccumMcTicks = 0;
            existing.ambient = ambient;
            dirty = true;
            return true;
        }
        if (level == existing.amplifier && mcTicks > existing.remainingMcTicks) {
            existing.remainingMcTicks = mcTicks;
            existing.ambient = ambient;
            dirty = true;
            return true;
        }
        return false;
    }

    /**
     * 서버 틱 1회분(MC 2틱)을 진행합니다. 만료된 효과는 제거합니다.
     *
     * @return 이번 틱에 누적된 독 피해량(방어구 무시). 독이 없으면 0.
     */
    public int tick() {
        saturationMcTicksElapsed = 0;
        hungerMcTicksElapsed = 0;
        regenerationHeals = 0;
        if (active.isEmpty()) return 0;
        int poisonDamage = 0;
        var iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<StatusEffect, Entry> pair = iterator.next();
            Entry entry = pair.getValue();
            int elapsed = Math.min(MC_TICKS_PER_SERVER_TICK, entry.remainingMcTicks);
            if (pair.getKey() == StatusEffect.REGENERATION) {
                // [TRIAL-GAP] 바닐라 MobEffectInstance.tickServer: 줄이기 전 남은 지속으로
                // RegenerationMobEffect.shouldApplyEffectTickThisTick(duration, amp) —
                // 주기 50 >> amp 의 배수이면 회복 차례다(주기 0 이면 매 틱).
                int interval = regenerationIntervalMcTicks(entry.amplifier);
                for (int step = 0; step < elapsed; step++) {
                    int duration = entry.remainingMcTicks - step;
                    if (interval <= 0 || duration % interval == 0) regenerationHeals++;
                }
            }
            entry.remainingMcTicks -= elapsed;
            if (pair.getKey() == StatusEffect.POISON) {
                entry.periodAccumMcTicks += elapsed;
                int interval = poisonIntervalMcTicks(entry.amplifier);
                while (entry.periodAccumMcTicks >= interval) {
                    entry.periodAccumMcTicks -= interval;
                    poisonDamage += POISON_DAMAGE;
                }
            }
            // [COOKING] 포만감은 흘러간 MC 틱 수만큼 허기를 채운다. 여기서 허기를 직접
            // 건드리지 않고 "몇 MC 틱이 흘렀나"만 남기는 이유는, 허기 상태를 소유한 쪽이
            // 플레이어(PlayerTickState)와 정적판 바이탈이라 이 클래스가 그 표를 알면
            // 소유가 뒤집히기 때문이다.
            if (pair.getKey() == StatusEffect.SATURATION) {
                saturationMcTicksElapsed += elapsed;
                saturationAmplifier = entry.amplifier;
            }
            if (pair.getKey() == StatusEffect.HUNGER) {
                hungerMcTicksElapsed = elapsed;
                hungerAmplifier = entry.amplifier;
            }
            if (entry.remainingMcTicks <= 0) {
                iterator.remove();
                dirty = true;
            }
        }
        return poisonDamage;
    }

    /**
     * [COOKING] 직전 {@link #tick()} 에서 포만감이 흘려보낸 MC 틱 수(0~{@link #MC_TICKS_PER_SERVER_TICK}).
     * 호출부는 이 횟수만큼 바닐라 {@code eat(level + 1, 1.0F)} 를 적용한다.
     */
    public int saturationMcTicksElapsed() {
        return saturationMcTicksElapsed;
    }

    /** [COOKING] 위 MC 틱 수에 짝지어지는 0-based 앰프. 흘러간 틱이 없으면 의미가 없다. */
    public int saturationAmplifier() {
        return saturationAmplifier;
    }

    /** 직전 tick()에서 허기가 소비한 MC 틱 수. exhaustion은 플레이어가 적용한다. */
    public int hungerMcTicksElapsed() {
        return hungerMcTicksElapsed;
    }

    /** 위 MC 틱 수에 짝지어지는 0-based 앰프. 흘러간 틱이 없으면 의미가 없다. */
    public int hungerAmplifier() {
        return hungerAmplifier;
    }

    public boolean has(StatusEffect effect) {
        return active.containsKey(effect);
    }

    /** [BEACON] 성급함 1레벨당 채굴 속도 증가율({@code Player.getDestroySpeed} 의 0.2F). */
    public static final double DIG_SPEED_PER_LEVEL = 0.2;

    /**
     * [BEACON] 26.3 {@code Player.getDestroySpeed} 의 채굴 가속 배율. {@code MobEffectUtil.hasDigSpeed}
     * 는 성급함 또는 콘딧 파워이고 {@code getDigSpeedAmplification} 은 둘 중 큰 앰프다 —
     * {@code f *= 1 + (amp + 1) * 0.2}. 효과가 없으면 1 이다. 클라 {@code effectDigSpeedMultiplier} 와 같은 식이다.
     */
    public double digSpeedMultiplier() {
        int amplifier = Math.max(amplifier(StatusEffect.HASTE), amplifier(StatusEffect.CONDUIT_POWER));
        return amplifier < 0 ? 1.0 : 1.0 + (amplifier + 1) * DIG_SPEED_PER_LEVEL;
    }

    /** 보유 중이면 0-based 앰프, 아니면 -1. */
    public int amplifier(StatusEffect effect) {
        Entry entry = active.get(effect);
        return entry == null ? -1 : entry.amplifier;
    }

    /** 남은 시간(10 TPS 서버 틱, 올림). 보유하지 않으면 0. */
    public int remainingServerTicks(StatusEffect effect) {
        Entry entry = active.get(effect);
        if (entry == null) return 0;
        return (entry.remainingMcTicks + MC_TICKS_PER_SERVER_TICK - 1) / MC_TICKS_PER_SERVER_TICK;
    }

    /** Removes one effect without disturbing unrelated effects. */
    public boolean remove(StatusEffect effect) {
        if (effect == null || active.remove(effect) == null) return false;
        dirty = true;
        return true;
    }

    /**
     * 감속·신속을 반영한 이동속도 배율(1.0 = 무효과, 하한 0). 바닐라도 두 효과가 같은
     * movement_speed 속성에 곱해지므로 둘을 함께 지니면 배율이 곱해진다.
     */
    public double speedMultiplier() {
        double multiplier = 1.0;
        Entry slowness = active.get(StatusEffect.SLOWNESS);
        if (slowness != null) {
            multiplier *= Math.max(0.0, 1.0 - SLOWNESS_PER_LEVEL * (slowness.amplifier + 1));
        }
        Entry speed = active.get(StatusEffect.SPEED);
        if (speed != null) multiplier *= 1.0 + SPEED_PER_LEVEL * (speed.amplifier + 1);
        return multiplier;
    }

    /**
     * 근접 피해 보정량. 호출부가 {@code dmg - 이 값} 으로 쓰므로 <b>양수면 감소, 음수면 증가</b>다.
     *
     * <p>[POTION-GAP] 나약(레벨당 −4)과 힘(레벨당 +3)이 <b>같은 덧셈 자리</b>를 나눠 쓰기
     * 때문에 두 효과를 함께 지니면 상쇄된다 — 바닐라도 둘 다 {@code ATTACK_DAMAGE} 속성의
     * ADD_VALUE 수정자라 같은 자리에서 합산된다. 이름은 도입 당시의 나약 전용 이름 그대로
     * 두었다(정적판 {@code standaloneEffectMeleePenalty} 와 짝이고 배선 테스트가 문자열을
     * 못박는다).
     */
    public double meleeDamagePenalty() {
        double penalty = 0.0;
        Entry weakness = active.get(StatusEffect.WEAKNESS);
        if (weakness != null) penalty += WEAKNESS_PER_LEVEL * (weakness.amplifier + 1);
        Entry strength = active.get(StatusEffect.STRENGTH);
        if (strength != null) penalty -= STRENGTH_PER_LEVEL * (strength.amplifier + 1);
        return penalty;
    }

    /**
     * [POTION-GAP] 도약이 낙하 거리에서 빼 주는 블록 수(0 이상). 바닐라
     * {@code LivingEntity.calculateFallDamage} 가 {@code JUMP} 효과의 {@code amplifier + 1} 을
     * {@code fallDistance} 에서 빼는 것과 같다 — 새 피해 공식이 아니라 입력 거리 보정이다.
     */
    public double jumpBoostFallReduction() {
        Entry entry = active.get(StatusEffect.JUMP_BOOST);
        if (entry == null) return 0.0;
        return entry.amplifier + 1;
    }

    public boolean isEmpty() {
        return active.isEmpty();
    }

    /** 모든 효과 제거(우유·사망·리스폰). 실제로 비워졌으면 true. */
    public boolean clear() {
        if (active.isEmpty()) return false;
        active.clear();
        dirty = true;
        return true;
    }

    /** 목록 구성이 바뀌어 재전송이 필요한가. */
    public boolean dirty() {
        return dirty;
    }

    /** 전송을 마친 뒤 dirty 를 내린다. 전송 실패 시에는 호출하지 않아 다음 틱에 재시도한다. */
    public void consumeDirty() {
        dirty = false;
    }

    /** 다음 틱에 강제로 재전송하도록 표시한다(접속 직후 welcome 동봉 등). */
    public void markDirty() {
        dirty = true;
    }

    /**
     * [GLOWING] 활성 효과의 열거 서수 비트셋. 몹 방송의 "바뀌었나" 비교를 할당 없이 하려는 값이다
     * (열거가 64 개를 넘으면 컴파일 대신 여기서 즉시 실패한다).
     */
    public long activeMask() {
        if (active.isEmpty()) return 0L;
        long mask = 0L;
        for (StatusEffect effect : active.keySet()) mask |= 1L << effect.ordinal();
        return mask;
    }

    static {
        if (StatusEffect.values().length > 64) {
            throw new IllegalStateException("StatusEffect 비트셋이 long 을 넘었습니다");
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<Long, List<String>> PROTOCOL_NAMES_BY_MASK =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * [GLOWING] {@link #activeMask()} 비트셋의 프로토콜 이름 목록(열거 순서, 불변). 같은 비트셋은 같은
     * 목록 인스턴스를 돌려주므로 몹 방송이 갱신마다 목록을 새로 만들지 않는다.
     */
    public static List<String> protocolNames(long mask) {
        if (mask == 0L) return List.of();
        return PROTOCOL_NAMES_BY_MASK.computeIfAbsent(mask, bits -> {
            List<String> names = new ArrayList<>(Long.bitCount(bits));
            for (StatusEffect effect : StatusEffect.values()) {
                if ((bits & (1L << effect.ordinal())) != 0) names.add(effect.protocolName());
            }
            return List.copyOf(names);
        });
    }

    /** 프로토콜 전송용 스냅샷. 열거 순서(POISON→SLOWNESS→WEAKNESS)로 고정돼 결정적입니다. */
    public List<ActiveEffect> snapshot() {
        if (active.isEmpty()) return List.of();
        List<ActiveEffect> list = new ArrayList<>(active.size());
        for (Map.Entry<StatusEffect, Entry> pair : active.entrySet()) {
            list.add(new ActiveEffect(pair.getKey(), pair.getValue().amplifier,
                    remainingServerTicks(pair.getKey()), pair.getValue().ambient));
        }
        return list;
    }

    /** MC 틱과 주기 누적을 손실 없이 담는 영속 저장용 스냅샷. */
    public List<PersistentEffect> persistenceSnapshot() {
        if (active.isEmpty()) return List.of();
        List<PersistentEffect> list = new ArrayList<>(active.size());
        for (Map.Entry<StatusEffect, Entry> pair : active.entrySet()) {
            Entry entry = pair.getValue();
            list.add(new PersistentEffect(pair.getKey(), entry.amplifier,
                    entry.remainingMcTicks, entry.periodAccumMcTicks));
        }
        return List.copyOf(list);
    }

    /**
     * DB에서 읽은 효과를 접속 시 한 번 복원한다. 중복 종류는 손상된 저장 상태이므로 조용히
     * 덮어쓰지 않고 거부한다. 복원 직후 클라이언트에도 전송하도록 dirty 를 올린다.
     */
    public void restorePersistence(List<PersistentEffect> snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("상태이상 스냅샷은 null일 수 없습니다");
        }
        EnumMap<StatusEffect, Entry> restored = new EnumMap<>(StatusEffect.class);
        for (PersistentEffect effect : snapshot) {
            if (effect == null) {
                throw new IllegalArgumentException("상태이상 스냅샷 항목은 null일 수 없습니다");
            }
            Entry entry = new Entry(effect.amplifier(), effect.remainingMcTicks());
            entry.periodAccumMcTicks = effect.periodAccumMcTicks();
            if (restored.putIfAbsent(effect.effect(), entry) != null) {
                throw new IllegalArgumentException("중복 상태이상 저장값: " + effect.effect());
            }
        }
        active.clear();
        active.putAll(restored);
        dirty = true;
        saturationMcTicksElapsed = 0;
        saturationAmplifier = 0;
        hungerMcTicksElapsed = 0;
        hungerAmplifier = 0;
        regenerationHeals = 0;
    }
}
