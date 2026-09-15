package com.gameexpert.engine.mob;

import java.util.List;

/**
 * 근접 몹 공통 AI: IDLE→WANDER(랜덤 목표)→CHASE(감지 반경 내 최근접 생존 플레이어)
 * →ATTACK(사거리 내, 쿨다운). 좀비/거미가 파라미터만 바꿔 재사용한다.
 */
public abstract class MeleeMob extends Mob {
    /** 기존 근접몹 공통 20 MC tick 공격 간격을 10TPS 서버 틱으로 환산한다. */
    protected final int commonAttackCooldownTicks() { return 10; }
    protected int attackCd;      // 남은 공격 쿨다운(틱)

    protected MeleeMob(long id, MobType type, double x, double y, double z) {
        super(id, type, x, y, z);
    }

    protected abstract double detectRange();      // 추적 감지 반경(블록)
    protected abstract double attackRange();       // 공격 사거리(블록)
    protected abstract int attackDamage();
    /** Per-hit variant; fixed-damage mobs retain the existing implementation. */
    protected int attackDamage(MobRandom rng) { return attackDamage(); }
    protected abstract int attackCooldownTicks();
    protected abstract boolean climbWalls();

    int directMobAttackDamage(MobRandom rng) {
        return directMobAttackDamage(rng, null);
    }

    /** [MOB-EQUIP] 몹 대상 근접 피해: 무기 인챈트의 대상별 추가 피해(강타·살충·찌르기 포함)를 더한다. */
    int directMobAttackDamage(MobRandom rng, MobType target) {
        int damage = attackDamage(rng);
        if (heldItem() != 0) {
            return enchantedMeleeDamage(Math.max(damage,
                    com.gameexpert.engine.CombatRules.meleeDamage(heldItem())), target);
        }
        return damage;
    }

    @Override
    public void commitAcceptedMeleeAttack() {
        attackCd = Math.max(1, (int) Math.ceil(
                attackCooldownTicks() * preparedRaidCooldownMultiplier()));
        super.commitAcceptedMeleeAttack();
    }

    /** 현재 환경에서 플레이어를 적대로 인식하는가. 기본 근접 몹은 항상 적대. */
    protected boolean hostile(MobWorldView world) { return true; }

    /** 시야만으로 새 표적을 획득하는가. 워든처럼 외부 감각 원장만 쓰는 종이 이 문을 닫는다. */
    protected boolean mayAcquireVisualTarget(MobWorldView world) { return hostile(world); }

    /**
     * 추적/공격 판정 앞에서 이번 틱 행동을 가로챈다. true 를 돌려주면 이동·공격 없이 물리만 진행한다
     * (좀비의 문 부수기). target 은 없을 수도 있으며, 그때는 진행 상태를 되돌릴 기회로 쓴다.
     */
    protected TargetInterception interceptTarget(MobWorldView world, PlayerSnapshot target) {
        return TargetInterception.NONE;
    }

    /** Rare pre-combat action result; shared constants keep the ordinary path allocation-free. */
    protected static final class TargetInterception {
        static final TargetInterception NONE = new TargetInterception(false, List.of());
        static final TargetInterception HANDLED = new TargetInterception(true, List.of());

        private final boolean handled;
        private final List<MobEvent> events;
        private final double moveX, moveZ;

        private TargetInterception(boolean handled, List<MobEvent> events) {
            this(handled, events, 0, 0);
        }

        private TargetInterception(boolean handled, List<MobEvent> events, double moveX, double moveZ) {
            this.handled = handled;
            this.events = events;
            this.moveX = moveX; this.moveZ = moveZ;
        }

        static TargetInterception handledMovement(List<MobEvent> events, double moveX, double moveZ) {
            return new TargetInterception(true, events, moveX, moveZ);
        }

        static TargetInterception handled(List<MobEvent> events) {
            return events.isEmpty() ? HANDLED : new TargetInterception(true, events);
        }

        /**
         * [CREAKING] 이번 틱을 <b>가로채지 않으면서</b> 사건만 얹는다. 크리킹의 정지 해제가
         * 그 자리다 — 소리는 이 틱에 나가야 하지만 이동은 평소처럼 아래 근접 본체가 맡아야
         * 한다. {@link #tick} 이 이미 {@code handled} 와 무관하게 이벤트를 실어 보내므로
         * 본체는 한 글자도 바뀌지 않는다.
         */
        static TargetInterception unhandled(List<MobEvent> events) {
            return events.isEmpty() ? NONE : new TargetInterception(false, events);
        }
    }

    /**
     * [TURTLE] 좀비류 알 사냥 goal 의 상태. 해당 종만 갖고 나머지는 {@code null} 이라
     * 다른 근접 몹의 틱은 필드 하나를 읽는 것 말고는 한 글자도 바뀌지 않는다.
     */
    private final TurtleEggHunt eggHunt =
            TurtleEggHunt.hunts(type) ? new TurtleEggHunt() : null;

    /**
     * 알 사냥 goal 한 틱. 표적이 없는 좀비류만 실제로 굴러가고, 그 밖의 종은 그대로 돌아온다.
     * 이 goal 이 몹을 소유했는지는 {@link #turtleEggHuntActive()} 가 답한다.
     */
    protected final List<MobEvent> tickTurtleEggHunt(MobWorldView world, List<MobEvent> events) {
        if (eggHunt == null) return events;
        for (MobEvent event : eggHunt.tick(this, world)) events = appendEvent(events, event);
        return events;
    }

    /** 직전 {@link #tickTurtleEggHunt} 가 이번 틱의 이동·행동을 가져갔는가. */
    protected final boolean turtleEggHuntActive() {
        return eggHunt != null && eggHunt.active();
    }

    /** 알 사냥 goal(없는 종은 {@code null}). 테스트와 드라운드 배선이 진행을 들여다본다. */
    final TurtleEggHunt turtleEggHunt() { return eggHunt; }

    protected double moveSpeed() { return type.baseSpeed(); }

    /** 추적 중 종별 중단 조건. 기본 근접 몹은 목표를 임의로 버리지 않는다. */
    protected boolean abandonTrackedTarget(MobWorldView world, MobRandom rng) { return false; }

    /** 추적 이동 벡터. 거미의 LeapAtTargetGoal처럼 종별 이동이 필요할 때 재정의한다. */
    protected double[] chaseMovement(MobWorldView world, PlayerSnapshot target,
                                     MobRandom rng) {
        return towardHoriz(target.x(), target.z(), moveSpeed());
    }

    /**
     * 추격 중 세로 변위. 지상 종은 0이고, 팬텀처럼 3차원으로 표적을 쫓는 종만 재정의한다.
     * 수평·세로를 분리해 기존 지상 종의 이동 순서와 수치를 바꾸지 않는다.
     */
    protected double chaseVerticalMovement(MobWorldView world, PlayerSnapshot target,
                                            MobRandom rng) {
        return 0.0;
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        List<MobEvent> events = List.of();
        if (isDead()) return events;
        resolvePendingSpawnWeapon(world);
        if (attackCd > 0) attackCd--;
        if (hasTrackedTarget() && abandonTrackedTarget(world, rng)) clearTrackedTarget();

        double dx = 0, dy = 0, dz = 0;
        PlayerSnapshot target = trackedTarget(world, detectRange(), mayAcquireVisualTarget(world));
        // [SPEAR-MOB] SpearUseGoal.canContinueToUse: 표적과 KINETIC_WEAPON 이 남아 있어야 한다.
        if (!spearUseActive()) stopSpearUse();
        TargetInterception interception = interceptTarget(world, target);
        if (!interception.events.isEmpty()) events = interception.events;
        if (interception.handled) {
            // 기존 정지 행동은 0이고, 명시적인 종별 도약도 공용 충돌 물리를 한 번만 거친다.
            dx = interception.moveX; dz = interception.moveZ;
        } else if (target != null && spearUseActive()) {
            // [SPEAR-MOB] SpearUseGoal(우선순위 2)이 먼저 등록되어 같은 우선순위의 근접 공격 goal 은 돌지 않는다.
            final PlayerSnapshot spearTarget = target;
            double[] mv = tickSpearUse(world, rng, target.x(), target.y(), target.z(),
                    target.y() + (target.crouching() ? com.gameexpert.engine.PlayerInteractionRules.CROUCHING_EYE_HEIGHT
                            : com.gameexpert.engine.PlayerInteractionRules.STANDING_EYE_HEIGHT),
                    () -> chaseMovement(world, spearTarget, rng));
            dx = mv[0];
            dz = mv[1];
        } else if (target != null) {
            faceToward(target.x(), target.z());
            if (dist3d(target) <= attackRange() && canSeeTargetNow(world, target)) {
                state = MobState.ATTACK;
                if (attackCd == 0) {
                    int damage = attackDamage(rng);
                    if (heldItem() != 0) {
                        // [MOB-EQUIP] 날카로움은 난이도 배율(contactDamage) 앞의 공격 피해에 더한다.
                        damage = enchantedMeleeDamage(Math.max(damage,
                                com.gameexpert.engine.CombatRules.meleeDamage(heldItem())), null);
                    }
                    events = appendEvent(events, new MobEvent.AttackPlayer(
                            target.nickname(), contactDamage(world, damage), x, z,
                            null, meleeEffect(world)));
                }
            } else {
                state = MobState.CHASE;
                double[] mv = chaseMovement(world, target, rng);
                dx = mv[0]; dz = mv[1];
                dy = chaseVerticalMovement(world, target, rng);
            }
        } else {
            Mob socialTarget = preparedSocialTarget();
            boolean hasSocialTarget = !hasTrackedTarget()
                    && MobRelationshipPolicy.mayDirectlyTarget(this, socialTarget)
                    && MobRelationshipPolicy.inTargetRange(this, socialTarget);
            if (hasSocialTarget && spearUseActive()) {
                // [SPEAR-MOB] 사회적 표적(주민 · 철 골렘 등)에도 같은 goal 이 돈다.
                final Mob spearTarget = socialTarget;
                double[] mv = tickSpearUse(world, rng, socialTarget.x, socialTarget.y, socialTarget.z,
                        socialTarget.y + socialTarget.eyeHeight(),
                        () -> towardHoriz(spearTarget.x, spearTarget.z, moveSpeed()));
                dx = mv[0];
                dz = mv[1];
            } else if (hasSocialTarget) {
                faceToward(socialTarget.x, socialTarget.z);
                if (MobRelationshipPolicy.inAttackReach(this, socialTarget)
                        && MobRelationshipPolicy.hasLineOfSight(world, this, socialTarget)) {
                    state = MobState.ATTACK;
                    if (attackCd == 0) {
                        events = appendEvent(events, MobEvent.AttackMob.direct(
                                socialTarget.id, directMobAttackDamage(rng, socialTarget.type),
                                meleeEffect(world)));
                    }
                } else {
                    state = MobState.CHASE;
                    double[] mv = towardHoriz(socialTarget.x, socialTarget.z, moveSpeed());
                    dx = mv[0]; dz = mv[1];
                }
            } else {
                stopSpearUse();
                // [TURTLE] 좀비류 알 사냥은 배회 바로 위다 — [A] 우선순위 4 는 공격 goal(2)
                // 아래이고 배회(7) 위이며, 표적·사회적 표적이 없을 때에만 몹을 소유한다.
                events = tickTurtleEggHunt(world, events);
                if (turtleEggHuntActive()) {
                    if (eggHunt.breaking()) {
                        faceToward(eggHunt.targetX(), eggHunt.targetZ());
                        state = MobState.ATTACK;
                    } else {
                        state = MobState.CHASE;
                        double[] mv = towardHoriz(eggHunt.targetX(), eggHunt.targetZ(), moveSpeed());
                        dx = mv[0]; dz = mv[1];
                    }
                } else {
                    double[] mv = wander(rng, moveSpeed());
                    dx = mv[0]; dz = mv[1];
                    state = (dx != 0 || dz != 0) ? MobState.WANDER : MobState.IDLE;
                }
            }
        }

        // 비행 종(팬텀 등)은 movementMedium() 만 "fly" 로 갈아도 물리가 따라와야 한다.
        // 정적판은 profile.movementMedium === "fly" 하나로 비행 경로를 고르므로 같은 축을 쓴다.
        MobPhysics.tickMove(this, world, dx, dy, dz,
                "fly".equals(movementMedium()) ? MoveMode.FLY
                        : climbWalls() ? MoveMode.CLIMB : MoveMode.WALK);
        return events;
    }
}
