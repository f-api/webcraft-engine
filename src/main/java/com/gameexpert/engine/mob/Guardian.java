package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.engine.effect.StatusEffect;

/**
 * [GUARDIAN] 가디언·엘더 가디언. 수치·순서는 MC Java 1.21.4 {@code Guardian} /
 * {@code ElderGuardian} 원본이며 근거는 {@code docs/MC-REFERENCE.md} 「가디언 2종」 절이다.
 *
 * <p>세 축이 이 종을 정의한다.
 * <ul>
 *   <li><b>레이저</b> — {@code GuardianAttackGoal}. {@code start()} 가 {@code attackTime = -10}
 *       으로 시작해 매 MC 틱 1씩 오르고, 0 에 도달한 틱에 빔이 켜지며
 *       ({@code setActiveAttackTarget} + 이벤트 21), {@code getAttackDuration()}
 *       (가디언 80 · 엘더 60) 에 도달한 틱에 피해가 들어간다.</li>
 *   <li><b>가시(thorns)</b> — {@code Guardian.hurt}. <b>가시가 세워져 있을 때만</b>
 *       ({@code !isMoving()}) 직접 가해자에게 2.0 반사 피해.</li>
 *   <li><b>수영/퍼덕임</b> — 물속에서만 {@code GuardianMoveControl} 로 3차원 유영하고,
 *       물 밖 접지 상태에서는 매 틱 무작위 도약 + 무작위 yaw 로 퍼덕인다
 *       ({@code Guardian.aiStep}). 가디언은 지상에서 <b>말라 죽지 않는다</b>
 *       (Monster 라 공기 호흡을 하며 {@code WaterAnimal} 의 Air 고갈이 없다).</li>
 * </ul>
 *
 * <p>엘더는 여기에 채굴 피로 오라를 더한다({@code ElderGuardian.customServerAiStep}).
 */
class Guardian extends Mob {
    // ── 레이저(GuardianAttackGoal) ───────────────────────────────────────────
    /** {@code GuardianAttackGoal.start()} 의 {@code this.attackTime = -10}. */
    static final int ATTACK_START_MC_TICKS = -10;
    /** {@code Guardian.getAttackDuration()} = 80. */
    static final int ATTACK_DURATION_MC_TICKS = 80;
    /** {@code ElderGuardian.getAttackDuration()} = 60. */
    static final int ELDER_ATTACK_DURATION_MC_TICKS = 60;
    /** 10 TPS 권위 틱 하나가 소비하는 MC 틱 수. */
    static final int MC_TICKS_PER_AUTHORITY_TICK = 2;
    /** {@code Attributes.FOLLOW_RANGE} = 16. 표적 획득 반경이다. */
    static final double FOLLOW_RANGE = 16.0;
    /**
     * {@code Guardian.GuardianAttackSelector} 의 {@code guardian.distanceToSqr(entity) > 9.0}.
     * 3블록 안쪽 플레이어는 레이저 표적이 되지 않는다.
     */
    static final double MIN_TARGET_DISTANCE_SQ = 9.0;
    /**
     * 레이저 총 피해. 바닐라는 {@code indirectMagic} f(보통 1 · 어려움 3 · 엘더 +2) 를 먼저
     * 넣고 곧바로 {@code mobAttack} ATTACK_DAMAGE 를 넣는데, 앞의 한 방이 무적 프레임을
     * 세워 뒤의 한 방은 차액만 들어간다. ATTACK_DAMAGE 가 언제나 f 이상이라
     * <b>순 피해는 정확히 ATTACK_DAMAGE</b> 이므로 이벤트 한 건으로 접는다
     * (가디언 6 · 엘더 8, 난이도 배율은 {@link #contactDamage} 가 적용한다).
     */
    static final int ATTACK_DAMAGE = 6;
    /** {@code ElderGuardian} 의 {@code Attributes.ATTACK_DAMAGE} = 8. */
    static final int ELDER_ATTACK_DAMAGE = 8;

    // ── 가시(Guardian.hurt) ──────────────────────────────────────────────────
    /** {@code livingEntity.hurt(damageSources().thorns(this), 2.0F)}. */
    static final int THORNS_DAMAGE = 2;

    // ── 퍼덕임(Guardian.aiStep) ─────────────────────────────────────────────
    /** {@code (random.nextFloat() * 2 - 1) * 0.4F} 의 수평 임펄스 폭(블록/MC 틱). */
    static final double FLOP_HORIZONTAL_IMPULSE = 0.4;
    /** {@code setDeltaMovement(..., 0.5, ...)} 의 수직 임펄스(블록/MC 틱). */
    static final double FLOP_VERTICAL_IMPULSE = 0.5;

    // ── 엘더 오라(ElderGuardian.customServerAiStep) ──────────────────────────
    /** {@code (tickCount + getId()) % 1200 == 0} — 1200 MC 틱 = 60초. */
    static final int ELDER_EFFECT_INTERVAL_MC_TICKS = 1200;
    /** {@code MobEffectUtil.addEffectToPlayersAround(..., 50.0, ...)}. */
    static final double ELDER_EFFECT_RADIUS = 50.0;
    /** {@code new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 6000, 2)} 의 6000 MC 틱 = 5분. */
    static final int ELDER_EFFECT_DURATION_MC_TICKS = 6_000;
    /** 같은 생성자의 앰프 2 — 표기상 채굴 피로 III 다. */
    static final int ELDER_EFFECT_AMPLIFIER = 2;

    /** 유영 이동의 수직 성분 배율. 다른 수중 종({@link AquaticAnimalMob})과 같은 형태다. */
    private static final double SWIM_VERTICAL_FACTOR = 0.55;
    /** 표적 추적 중 유지하는 거리. 이 안쪽이면 접근하지 않는다(레이저 사거리 유지). */
    private static final double APPROACH_STOP_DISTANCE = 4.0;

    private int attackMcTicks = ATTACK_START_MC_TICKS;
    private String attackTargetNickname;
    private boolean spikesRetracted;
    private int elderEffectMcTicks;
    private boolean swimming;
    private int swimTicks;
    private double swimDx, swimDy, swimDz;

    Guardian(long id, double x, double y, double z) {
        this(id, MobType.GUARDIAN, x, y, z);
    }

    protected Guardian(long id, MobType type, double x, double y, double z) {
        super(id, type, x, y, z);
        // 바닐라 오라 위상은 (tickCount + getId()) % 1200 이라 개체마다 다르다. 권위 몹 ID로
        // 같은 분산을 만들되 절대 틱 수에 의존하지 않도록 커서를 ID 로 초기화한다.
        this.elderEffectMcTicks = Math.floorMod((int) (id ^ id >>> 32), ELDER_EFFECT_INTERVAL_MC_TICKS);
    }

    boolean elder() { return type == MobType.ELDER_GUARDIAN; }

    int attackDurationMcTicks() {
        return elder() ? ELDER_ATTACK_DURATION_MC_TICKS : ATTACK_DURATION_MC_TICKS;
    }

    int laserDamage() { return elder() ? ELDER_ATTACK_DAMAGE : ATTACK_DAMAGE; }

    private double beamTargetX;
    private double beamTargetY;
    private double beamTargetZ;

    @Override
    public double[] guardianBeamTarget() {
        return attackTargetNickname != null && attackMcTicks >= 0
                ? new double[] { beamTargetX, beamTargetY, beamTargetZ } : null;
    }

    int attackMcTicks() { return attackMcTicks; }
    String attackTargetNickname() { return attackTargetNickname; }
    boolean spikesRetracted() { return spikesRetracted; }
    int elderEffectMcTicks() { return elderEffectMcTicks; }

    @Override
    public String movementMedium() { return swimming ? "swim" : "land"; }

    /** 3블록 안쪽 플레이어는 레이저 표적이 되지 않는다({@code GuardianAttackSelector}). */
    @Override
    protected boolean canTargetPlayer(PlayerSnapshot player) {
        double dx = player.x() - x, dy = player.y() - y, dz = player.z() - z;
        return dx * dx + dy * dy + dz * dz > MIN_TARGET_DISTANCE_SQ;
    }

    /**
     * 가시가 세워진 동안(=정지 중) 직접 근접 가해자에게 되돌리는 반사 피해. 0 이면 반격이 없다.
     * 바닐라 {@code Guardian.hurt} 는 {@code !isMoving()} 일 때만 가시를 세운다.
     */
    @Override
    public int thornsDamage() { return spikesRetracted ? 0 : THORNS_DAMAGE; }

    // 조준 커서·오라 위상은 **영속하지 않는다**. 바닐라도 Guardian 의 attackTime 을 NBT 에
    // 저장하지 않고 ElderGuardian 의 오라 위상은 tickCount 파생이라 재기동마다 다시 시작한다.
    // 여기서도 재접속·언로드 복구는 몹 ID 파생 위상으로 되살아난다.

    private void synchronizeBeamVisual() {
        if (attackTargetNickname == null) {
            synchronizeVisualAction("none", "idle", 0);
        } else if (attackMcTicks < 0) {
            // 조준 전 준비 구간. 빔은 아직 보이지 않는다.
            synchronizeVisualAction("beam", "anticipation", (-attackMcTicks + 1) / 2);
        } else {
            synchronizeVisualAction("beam", "active",
                    (attackDurationMcTicks() - attackMcTicks + 1) / 2);
        }
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        List<MobEvent> events = List.of();
        if (isDead()) return events;

        if (elder()) {
            elderEffectMcTicks += MC_TICKS_PER_AUTHORITY_TICK;
            if (elderEffectMcTicks >= ELDER_EFFECT_INTERVAL_MC_TICKS) {
                elderEffectMcTicks -= ELDER_EFFECT_INTERVAL_MC_TICKS;
                events = appendEvent(events, new MobEvent.AreaStatusEffect(
                        x, y + eyeHeight(), z, ELDER_EFFECT_RADIUS,
                        new ProjectileEffect(StatusEffect.MINING_FATIGUE, ELDER_EFFECT_AMPLIFIER,
                                ELDER_EFFECT_DURATION_MC_TICKS / MC_TICKS_PER_AUTHORITY_TICK)));
            }
        }

        int waterPresence = bodyWaterPresence(world);
        if (waterPresence == BODY_WATER_UNKNOWN) return events;
        swimming = waterPresence == BODY_WATER_WET;
        if (!swimming) return appendEvents(events, tickFlop(world, rng));

        PlayerSnapshot target = trackedTarget(world, FOLLOW_RANGE, true);
        if (target == null || !canSeeTargetNow(world, target)) return abortLaser(world, rng, events);
        beamTargetX = target.x();
        beamTargetY = target.y() + 0.9;
        beamTargetZ = target.z();
        // 빔 조준은 클라이언트 +Z 모델의 yaw + PI 보정과 같은 정면 규약을 쓴다.
        yaw = Math.atan2(x - target.x(), z - target.z());
        state = MobState.CHASE;

        if (attackTargetNickname == null || !attackTargetNickname.equals(target.nickname())) {
            attackTargetNickname = target.nickname();
            attackMcTicks = ATTACK_START_MC_TICKS;
        }
        int previous = attackMcTicks;
        attackMcTicks += MC_TICKS_PER_AUTHORITY_TICK;
        // 빔 점등: attackTime 이 0 을 지나는 그 틱에 한 번(바닐라 이벤트 21 = 공격 음성).
        if (previous < 0 && attackMcTicks >= 0) {
            events = appendEvent(events, new MobEvent.Sound("attack"));
        }
        if (attackMcTicks >= attackDurationMcTicks()) {
            state = MobState.ATTACK;
            attackMcTicks = ATTACK_START_MC_TICKS;
            attackTargetNickname = null;
            spikesRetracted = false;
            synchronizeBeamVisual();
            return appendEvent(events, new MobEvent.AttackPlayer(target.nickname(),
                    contactDamage(world, laserDamage()), x, z, null, id, type, null));
        }
        synchronizeBeamVisual();
        // 조준 중에는 이동을 멈춘다(바닐라도 goal.start 에서 navigation 을 멈춘다) — 가시가 선다.
        spikesRetracted = false;
        double distance = dist3d(target);
        if (distance > APPROACH_STOP_DISTANCE) {
            spikesRetracted = true;
            swimToward(target.x(), target.y() + 0.9, target.z());
        } else {
            MobPhysics.tickMove(this, world, 0.0, 0.0, 0.0, MoveMode.SWIM);
        }
        return events;
    }

    /** 표적을 잃으면 조준을 버리고({@code stop()}) 물속 배회로 돌아간다. */
    private List<MobEvent> abortLaser(MobWorldView world, MobRandom rng, List<MobEvent> events) {
        attackMcTicks = ATTACK_START_MC_TICKS;
        attackTargetNickname = null;
        spikesRetracted = true;
        synchronizeBeamVisual();
        if (swimTicks-- <= 0) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            swimDx = Math.cos(angle) * type.baseSpeed();
            swimDz = Math.sin(angle) * type.baseSpeed();
            swimDy = (rng.nextDouble() - 0.5) * type.baseSpeed() * SWIM_VERTICAL_FACTOR;
            swimTicks = 12 + rng.nextInt(24);
        }
        yaw = Math.atan2(swimDz, swimDx);
        state = MobState.WANDER;
        MobPhysics.tickMove(this, world, swimDx, swimDy, swimDz, MoveMode.SWIM);
        return events;
    }

    private void swimToward(double tx, double ty, double tz) {
        double ax = tx - x, ay = ty - y, az = tz - z;
        double distance = Math.sqrt(ax * ax + ay * ay + az * az);
        if (distance <= 1e-9) return;
        double speed = type.baseSpeed() * effectSpeedMultiplier();
        swimDx = ax / distance * speed;
        swimDy = ay / distance * speed * SWIM_VERTICAL_FACTOR;
        swimDz = az / distance * speed;
    }

    /**
     * {@code Guardian.aiStep} 의 물 밖 분기. 접지한 틱마다 무작위 수평 임펄스와 0.5 도약,
     * 무작위 yaw, {@code entity.guardian.flop} 발성이 함께 일어난다.
     */
    private List<MobEvent> tickFlop(MobWorldView world, MobRandom rng) {
        List<MobEvent> events = List.of();
        state = MobState.IDLE;
        attackMcTicks = ATTACK_START_MC_TICKS;
        attackTargetNickname = null;
        spikesRetracted = true;
        synchronizeBeamVisual();
        double dx = 0.0;
        double dz = 0.0;
        if (onGround) {
            dx = (rng.nextFloat() * 2.0 - 1.0) * FLOP_HORIZONTAL_IMPULSE;
            dz = (rng.nextFloat() * 2.0 - 1.0) * FLOP_HORIZONTAL_IMPULSE;
            // MobPhysics 가 이동 직전에 권위 중력을 먼저 적용하므로 원본 임펄스만 남긴다.
            vy = FLOP_VERTICAL_IMPULSE + MobPhysics.GRAVITY;
            yaw = rng.nextFloat() * Math.PI * 2.0;
            onGround = false;
            events = appendEvent(events, new MobEvent.Sound("flop"));
        }
        MobPhysics.tickMove(this, world, dx, 0.0, dz, MoveMode.WALK);
        return events;
    }

    private static List<MobEvent> appendEvents(List<MobEvent> base, List<MobEvent> extra) {
        List<MobEvent> merged = base;
        for (MobEvent event : extra) merged = appendEvent(merged, event);
        return merged;
    }
}

/** [GUARDIAN] 엘더 가디언. 상태·규칙은 전부 {@link Guardian} 이 소유하고 종류만 다르다. */
final class ElderGuardian extends Guardian {
    ElderGuardian(long id, double x, double y, double z) {
        super(id, MobType.ELDER_GUARDIAN, x, y, z);
    }
}
