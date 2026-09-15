package com.gameexpert.engine.mob;

/** Aquatic identities with shared three-dimensional navigation and dry-out lifecycle. */
final class Pufferfish extends AquaticAnimalMob {
    private PufferfishRules.State puffState = PufferfishRules.State.small();

    Pufferfish(long id, double x, double y, double z) {
        super(id, MobType.PUFFERFISH, x, y, z);
    }

    PufferfishRules.State puffState() { return puffState; }
    int puffStageId() { return puffState.stage().id(); }

    /** Pufferfish 종별 visualFlags 하위 2비트는 PuffState 0/1/2 자체다. */
    @Override public int visualFlags() {
        return (puffStageId() << Mob.VISUAL_PUFFERFISH_PUFF_SHIFT)
                & Mob.VISUAL_PUFFERFISH_PUFF_MASK;
    }

    /** 중앙 공간 조회가 계산한 위협 사실을 한 권위 틱 진행하고 가청 상태 전이를 돌려준다. */
    PufferfishRules.Transition advancePuffState(boolean threatened) {
        PufferfishRules.Step step = PufferfishRules.advanceAuthorityTick(puffState, threatened);
        puffState = step.state();
        return step.transition();
    }

    /** 바닐라 NBT 와 같이 PuffState 만 복원하고 일시 팽창/수축 카운터는 초기화한다. */
    void restorePuffState(int restoredPuffState) {
        puffState = PufferfishRules.State.restored(restoredPuffState);
    }

    java.util.Optional<PufferfishRules.ContactPlan> planContact(
            com.gameexpert.engine.Difficulty difficulty, boolean targetAlive, boolean touching) {
        return PufferfishRules.planContact(puffState.stage(), difficulty, targetAlive, touching);
    }
}

final class Tadpole extends AquaticAnimalMob {
    static final int TICKS_TO_FROG_MC = 24_000;
    static final int MC_TICKS_PER_AUTHORITY_TICK = 2;
    private int ageMcTicks;

    Tadpole(long id, double x, double y, double z) { super(id, MobType.TADPOLE, x, y, z); }
    int ageMcTicks() { return ageMcTicks; }
    boolean advanceLifecycle() {
        ageMcTicks = Math.min(TICKS_TO_FROG_MC, ageMcTicks + MC_TICKS_PER_AUTHORITY_TICK);
        return ageMcTicks >= TICKS_TO_FROG_MC;
    }
    boolean feedGrowth() {
        int ticksRemaining = Math.max(0, TICKS_TO_FROG_MC - ageMcTicks);
        int speedUpSeconds = (int) ((float) (ticksRemaining / 20) * 0.1F);
        ageMcTicks = Math.min(TICKS_TO_FROG_MC, ageMcTicks + speedUpSeconds * 20);
        return true;
    }
    void restoreAge(int restoredAgeMcTicks) {
        if (restoredAgeMcTicks < 0 || restoredAgeMcTicks > TICKS_TO_FROG_MC) {
            throw new IllegalArgumentException("invalid persisted Tadpole Age");
        }
        ageMcTicks = restoredAgeMcTicks;
    }
}

/**
 * [NAUTILUS-BEHAVIOR] 노틸러스 계열 두 종의 <b>공유 전투/길들이기 계약</b>.
 *
 * <p>근거는 {@code docs/research/mc-nautilus-1-21-11.md} §1·§3 이고 등급은 [B]
 * (minecraft.wiki, 조회일 2026-08-10)다. 위키가 좀비 노틸러스를 "behave like regular
 * nautiluses" 라고 못 박고, 아래 다섯 줄을 두 종에 <b>같은 문장으로</b> 적는다:
 * <ul>
 *   <li>"they attack by dashing into their target" — 돌진이 유일한 공격 형태.</li>
 *   <li>"occasionally dash toward and attack nearby pufferfish" — 복어 사냥.</li>
 *   <li>"Each pufferfish or bucket of pufferfish has a 1⁄3 chance of taming" — 길들이기.</li>
 *   <li>Behavior 인포박스 "Neutral (untamed) / Passive (baby or tamed)".</li>
 *   <li>길들이면 표적 선정 자체가 사라진다(passive).</li>
 * </ul>
 * 그래서 이 클래스가 두 종의 공통 몸통이고, 종이 갈리는 자리는 아래 훅 둘뿐이다.
 *
 * <p><b>영속 필드 이름을 바꾸지 않는다.</b> 스냅샷 컬럼은 좀비 노틸러스만 있던 시절의
 * {@code zombieNautilus*} 이름 그대로다 — 이름을 바꾸면 이미 저장된 월드가 읽히지 않는다.
 * 대신 {@code Mob} 의 위임자와 {@code MobRuntime} 의 복원 분기가 {@code instanceof
 * NautilusFamilyMob} 로 넓어져 두 종이 같은 컬럼을 쓴다.
 */
abstract class NautilusFamilyMob extends AquaticAnimalMob {
    enum ChargePhase { IDLE, CHARGING }
    static final int ANGER_DURATION_MC_TICKS = 400;
    static final int CHARGE_COOLDOWN_MC_TICKS = 80;
    static final int NATURAL_TARGET_MIN_MC_TICKS = 2_400;
    static final int NATURAL_TARGET_MAX_MC_TICKS = 3_600;
    static final double CHARGE_SPEED = 0.5;
    static final double CHARGE_MAX_DISTANCE = 12.0;
    static final double CHARGE_TARGET_RANGE = 11.0;
    static final int CHARGE_DAMAGE = 3;
    private static final double CHARGE_HIT_RANGE = 1.2;
    /**
     * [NAUTILUS-TEMPT][C] 유혹 추종이 멈추는 거리(블록). [B] 는 반경 10 만 적고 정지 거리를
     * 적지 않아 지어내지 않고 <b>돌진 명중 거리와 같은 값</b>을 쓴다 — 유혹으로 붙은 개체가
     * 도발되면 그 자리에서 곧바로 명중 판정 거리이므로 두 lane 의 거리 어휘가 갈리지 않는다.
     */
    static final double TEMPT_STOP_DISTANCE = CHARGE_HIT_RANGE;
    /**
     * [NAUTILUS-TEMPT][C] 유혹 목표점의 플레이어 발밑 기준 높이(블록). 돌진이 조준하는 지점
     * ({@code startCharge} 의 {@code player.y() + 0.9})과 <b>같은 값</b>이라 두 lane 이 같은
     * 몸통 중심을 본다.
     */
    static final double TEMPT_TARGET_EYE_OFFSET = 0.9;

    private ChargePhase chargePhase = ChargePhase.IDLE;
    private int chargeCooldownMcTicks;
    private String chargeTargetNickname;
    private long chargeTargetMobId;
    private double chargeVx, chargeVy, chargeVz, chargeDistance;
    private int naturalTargetCooldownMcTicks;
    private String angerTarget;
    private int angerMcTicks;
    private boolean saddled;
    private int armorTier = NautilusMountRules.NO_ARMOR;
    private String riderNickname;

    NautilusFamilyMob(long id, MobType type, double x, double y, double z) {
        super(id, type, x, y, z);
        this.naturalTargetCooldownMcTicks = NATURAL_TARGET_MIN_MC_TICKS
                + Math.floorMod((int) (id ^ id >>> 32), NATURAL_TARGET_MAX_MC_TICKS - NATURAL_TARGET_MIN_MC_TICKS + 1);
    }

    /**
     * 미길들임 개체가 <b>도발 없이</b> 물속 플레이어를 돌진 표적으로 삼는가.
     *
     * <p><b>두 종 모두 거짓이다</b> — 노틸러스 계열은 원문에서 통째로 중립이다.
     * <ul>
     *   <li>[B] «Nautilus» 인포박스 "Neutral (untamed)" · 본문 "retaliate against players and
     *       mobs <b>if provoked</b>".</li>
     *   <li>[B] «Zombie Nautilus» 인포박스 "Neutral (untamed) / Passive (tamed)" · 본문
     *       "Zombie nautiluses are neutral, <b>attacking only when provoked</b>".</li>
     * </ul>
     *
     * <p>[ZOMBIE-NAUTILUS-FIX] 좀비 노틸러스는 예전에 참이었다. 그것이 원문과 어긋난 값이었고
     * {@code docs/research/mc-nautilus-1-21-11.md} §8-4 가 divergence 로 적발한 자리다. 이제
     * 정정돼 훅이 두 종에서 같은 값을 내므로, 남은 종차는 {@link #healingRequiresOwner} 하나뿐이다.
     * 복어 사냥은 이 훅과 무관한 별도 표적 lane 이라 중립이어도 그대로 돈다.
     *
     * <p>훅 자체는 <b>남긴다</b>. 값이 두 종에서 같아졌다고 지우면, 나중에 선공 종이 이 몸통에
     * 붙을 때 {@link #findWaterTarget} 의 관문 자리를 다시 만들어야 한다 — 계약의 이름이
     * 사라지는 쪽이 더 비싸다.
     */
    protected abstract boolean attacksUnprovokedPlayers();

    /**
     * 먹이로 회복시키려면 길들여져 있어야 하는가.
     *
     * <p>노틸러스는 거짓이다 — [B]: "Adult nautiluses can be healed (or bred if at full
     * health) by being fed any fish or any bucket of fish" 에 길들임 조건이 없다.
     * 좀비 노틸러스는 이 저장소 현행 계약대로 참이다.
     */
    protected boolean healingRequiresOwner() { return true; }
    ChargePhase chargePhase() { return chargePhase; }
    int chargeCooldownMcTicks() { return chargeCooldownMcTicks; }
    String chargeTargetNickname() { return chargeTargetNickname; }
    long chargeTargetMobId() { return chargeTargetMobId; }
    double chargeVx() { return chargeVx; }
    double chargeVy() { return chargeVy; }
    double chargeVz() { return chargeVz; }
    double chargeDistance() { return chargeDistance; }
    int naturalTargetCooldownMcTicks() { return naturalTargetCooldownMcTicks; }
    String angerTarget() { return angerTarget; }
    int angerMcTicksRemaining() { return angerMcTicks; }
    boolean saddled() { return saddled; }
    int armorTier() { return armorTier; }
    boolean armored() { return armorTier != NautilusMountRules.NO_ARMOR; }
    String riderNickname() { return riderNickname; }
    boolean occupied() { return riderNickname != null; }
    boolean wantsNaturalPufferfishTarget() { return ownerNickname() == null && naturalTargetCooldownMcTicks <= 0; }

    boolean saddle() {
        if (isDead() || removed || saddled || isBaby() || ownerNickname() == null) return false;
        saddled = true;
        setPersistenceRequired(true);
        return true;
    }

    boolean equipArmor(int tier) {
        if (isDead() || removed || armored() || isBaby() || ownerNickname() == null) return false;
        if (tier == NautilusMountRules.NO_ARMOR || !NautilusMountRules.validArmorTier(tier)) return false;
        armorTier = tier;
        setPersistenceRequired(true);
        return true;
    }

    int removeArmor() {
        if (isDead() || removed || occupied() || !armored()) return 0;
        int item = NautilusMountRules.armorItemForTier(armorTier);
        armorTier = NautilusMountRules.NO_ARMOR;
        setPersistenceRequired(true);
        return item;
    }

    String seatRiderNickname(int seatIndex) {
        return MobMountRules.validSeat(type, seatIndex) ? riderNickname : null;
    }

    boolean mount(String nickname, int seatIndex) {
        if (isDead() || removed || isBaby() || ownerNickname() == null) return false;
        if (!MobMountRules.validSeat(type, seatIndex)) return false;
        if (nickname == null || nickname.isEmpty() || riderNickname != null) return false;
        riderNickname = nickname;
        setPersistenceRequired(true);
        return true;
    }

    boolean dismount(String nickname, int seatIndex) {
        if (!MobMountRules.validSeat(type, seatIndex)) return false;
        if (riderNickname == null || !riderNickname.equals(nickname)) return false;
        riderNickname = null;
        setPersistenceRequired(true);
        return true;
    }

    void clearNautilusSeat(int seatIndex) {
        if (MobMountRules.validSeat(type, seatIndex)) riderNickname = null;
    }

    boolean applyRiderPosition(String nickname, double x, double y, double z, double yaw) {
        if (!MobMountRules.steerable(type, saddled)) return false;
        if (riderNickname == null || !riderNickname.equals(nickname)) return false;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        vy = 0;
        horizontalVx = 0;
        horizontalVz = 0;
        knockbackVx = 0;
        knockbackVz = 0;
        return true;
    }

    @Override public int visualFlags() {
        return (saddled ? Mob.VISUAL_NAUTILUS_SADDLED : 0)
                | (armored() ? Mob.VISUAL_NAUTILUS_ARMORED : 0);
    }

    void restoreMountState(boolean restoredSaddled, int restoredArmorTier) {
        saddled = restoredSaddled;
        armorTier = NautilusMountRules.validArmorTier(restoredArmorTier)
                ? restoredArmorTier : NautilusMountRules.NO_ARMOR;
        riderNickname = null;
    }

    boolean tame(String nickname) {
        if (nickname == null || nickname.isBlank() || ownerNickname() != null || isBaby()) return false;
        setOwnerNickname(nickname);
        setPersistenceRequired(true);
        clearCombatState();
        return true;
    }
    /**
     * 먹이 한 개의 회복. 만피라 회복할 것이 없으면 {@code false} 를 돌려주며, 노틸러스에서는
     * 그 거짓이 곧 "번식 조건 성립"의 신호다({@code MobSystem#interactNautilusFamily}).
     */
    boolean feed(String nickname, double amount) {
        if (nickname == null) return false;
        if (healingRequiresOwner() && ownerNickname() == null) return false;
        return heal(amount);
    }
    @Override public void onHurt(String attackerNickname, double attackerX, double attackerZ) {
        if (attackerNickname == null || ownerNickname() != null) return;
        angerTarget = attackerNickname;
        angerMcTicks = ANGER_DURATION_MC_TICKS;
    }
    void restoreAnger(String target, int ticks) {
        if (target == null ? ticks != 0 : target.isBlank() || ticks <= 0 || ticks > ANGER_DURATION_MC_TICKS) {
            throw new IllegalArgumentException("invalid persisted Zombie Nautilus anger");
        }
        angerTarget = target;
        angerMcTicks = ticks;
    }
    void restoreChargeState(ChargePhase phase, int cooldownMcTicks, String targetNickname, long targetMobId,
            double vx, double vy, double vz, double distance, int naturalCooldownMcTicks) {
        if (phase == null || cooldownMcTicks < 0 || cooldownMcTicks > CHARGE_COOLDOWN_MC_TICKS
                || targetMobId < 0 || !Double.isFinite(vx) || !Double.isFinite(vy) || !Double.isFinite(vz)
                || !Double.isFinite(distance) || distance < 0.0 || distance > CHARGE_MAX_DISTANCE
                || naturalCooldownMcTicks < 0 || naturalCooldownMcTicks > NATURAL_TARGET_MAX_MC_TICKS) {
            throw new IllegalArgumentException("invalid persisted Zombie Nautilus charge state");
        }
        boolean hasNickname = targetNickname != null && !targetNickname.isBlank();
        boolean hasMob = targetMobId != 0;
        if (phase == ChargePhase.IDLE) {
            if (hasNickname || hasMob || vx != 0.0 || vy != 0.0 || vz != 0.0 || distance != 0.0) {
                throw new IllegalArgumentException("idle Zombie Nautilus has active charge checkpoint");
            }
        } else {
            double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (cooldownMcTicks != 0 || hasNickname == hasMob || length < 0.999 || length > 1.001) {
                throw new IllegalArgumentException("charging Zombie Nautilus has invalid target/vector");
            }
        }
        chargePhase = phase;
        chargeCooldownMcTicks = cooldownMcTicks;
        chargeTargetNickname = hasNickname ? targetNickname : null;
        chargeTargetMobId = targetMobId;
        chargeVx = vx; chargeVy = vy; chargeVz = vz; chargeDistance = distance;
        naturalTargetCooldownMcTicks = naturalCooldownMcTicks;
        synchronizeChargeVisual();
    }
    private void synchronizeChargeVisual() {
        if (chargePhase == ChargePhase.CHARGING) synchronizeVisualAction("charge", "active", 1);
        else synchronizeVisualAction("none", "idle", 0);
    }
    @Override public void commitAcceptedMeleeAttack() { markVisualAction("charge", 2); }

    @Override public java.util.List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead()) return java.util.List.of();
        if (occupied()) {
            state = MobState.IDLE;
            return java.util.List.of();
        }
        if (angerMcTicks > 0 && (angerMcTicks = Math.max(0, angerMcTicks - 2)) == 0) angerTarget = null;
        if (chargeCooldownMcTicks > 0) chargeCooldownMcTicks = Math.max(0, chargeCooldownMcTicks - 2);
        if (naturalTargetCooldownMcTicks > 0) naturalTargetCooldownMcTicks = Math.max(0, naturalTargetCooldownMcTicks - 2);
        if (ownerNickname() != null || isBaby() || !bodyTouchesWater(world)) {
            if (chargePhase == ChargePhase.CHARGING) finishCharge();
            // [NAUTILUS-TEMPT] 유혹은 길들임·나이를 묻지 않는다 — [B] 는 "follow players
            // holding any fish or bucket of fish" 를 두 종·모든 개체에 같은 문장으로 적는다.
            if (tickTemptation(world)) return java.util.List.of();
            return super.tick(world, rng);
        }
        if (chargePhase == ChargePhase.CHARGING) return tickCharge(world);
        PlayerSnapshot playerTarget = findWaterTarget(world);
        Mob mobTarget = playerTarget == null ? preparedSocialTarget() : null;
        if (mobTarget != null && (mobTarget.type != MobType.PUFFERFISH || !MobRelationshipPolicy.inTargetRange(this, mobTarget))) {
            mobTarget = null;
        }
        if (chargeCooldownMcTicks == 0 && (playerTarget != null || mobTarget != null)
                && startCharge(world, playerTarget, mobTarget)) {
            // One cue per committed action, using the species' registered attack voice family.
            state = MobState.ATTACK;
            markVisualAction("charge", 2);
            return java.util.List.of(new MobEvent.Sound("attack"));
        }
        if (naturalTargetCooldownMcTicks == 0) {
            naturalTargetCooldownMcTicks = NATURAL_TARGET_MIN_MC_TICKS
                    + rng.nextInt(NATURAL_TARGET_MAX_MC_TICKS - NATURAL_TARGET_MIN_MC_TICKS + 1);
        }
        // [NAUTILUS-TEMPT] 유혹 lane 은 <b>복어 표적 굴림 뒤</b>에 선다 — 그 굴림을 건너뛰면
        // 유혹당한 개체만 난수 수열이 밀려 두 권위가 갈린다. 여기까지 왔다는 것은 이번 틱에
        // 돌진이 성립하지 않았다는 뜻이라, 유혹이 배회를 대신한다.
        if (tickTemptation(world)) return java.util.List.of();
        return super.tick(world, rng);
    }

    /**
     * [NAUTILUS-TEMPT] 유혹 추종 한 틱. 계약 정본은 {@link TemptationRules} 이고 여기는 그
     * 술어가 참일 때의 <b>이동</b>만 소유한다.
     *
     * <p>근거 [B] «Nautilus» · «Zombie Nautilus»: "follow players holding any fish or bucket
     * of fish within a 10-block radius". 두 종이 같은 문장이라 이 몸통에 한 벌만 둔다.
     *
     * <p><b>난수를 하나도 쓰지 않는다.</b> 유혹이 성립한 틱에는 {@code super.tick} 의 배회
     * 굴림을 건너뛰는데(해피 가스트 탑승과 같은 자리·같은 이유 — 좌표 정본이 둘이 되지 않게),
     * 이는 "생선을 든 플레이어가 10블록 안에 있다"는 <b>닫힌 조건</b>이라 그 플레이어가 없는
     * 월드의 난수 수열은 이 웨이브 전과 한 글자도 다르지 않다.
     *
     * <p>물 밖에서는 유혹되지 않는다 — 이 종은 물에서만 헤엄치고(SWIM), 뭍에서 뭍으로 끌려가면
     * 건조 피해 lane 과 좌표가 싸운다.
     *
     * @return 유혹이 이번 틱의 이동을 소유했으면 참(호출자는 배회를 돌리지 않는다)
     */
    private boolean tickTemptation(MobWorldView world) {
        if (!TemptationRules.temptable(type) || !bodyTouchesWater(world)) return false;
        PlayerSnapshot tempter = nearestTemptingPlayer(world);
        if (tempter == null) return false;
        double dx = tempter.x() - x;
        double dy = tempter.y() + TEMPT_TARGET_EYE_OFFSET - (y + height() * 0.5);
        double dz = tempter.z() - z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= TEMPT_STOP_DISTANCE) {
            state = MobState.IDLE;
            return true;
        }
        double step = Math.min(type.baseSpeed(), length - TEMPT_STOP_DISTANCE);
        MobPhysics.tickMove(this, world,
                dx / length * step, dy / length * step, dz / length * step, MoveMode.SWIM);
        state = MobState.CHASE;
        return true;
    }

    /**
     * 유혹 중인 플레이어 중 가장 가까운 하나. 같은 거리가 둘이면 닉네임 사전순으로 갈라
     * <b>두 권위가 같은 플레이어를 고르게</b> 한다(정적판 {@code nearestTemptingPlayer} 사본과
     * 같은 규칙). 순회 순서에 기대면 서버 목록 순서와 정적판 배열 순서가 갈릴 수 있다.
     */
    private PlayerSnapshot nearestTemptingPlayer(MobWorldView world) {
        PlayerSnapshot best = null;
        double bestSq = Double.MAX_VALUE;
        for (PlayerSnapshot player : world.players()) {
            if (!TemptationRules.tempting(type, player, x, y, z)) continue;
            double distanceSq = distanceSquared(player.x(), player.y(), player.z());
            if (best == null || distanceSq < bestSq
                    || (distanceSq == bestSq
                        && player.nickname().compareTo(best.nickname()) < 0)) {
                best = player;
                bestSq = distanceSq;
            }
        }
        return best;
    }

    private java.util.List<MobEvent> tickCharge(MobWorldView world) {
        PlayerSnapshot player = chargeTargetNickname == null ? null : findPlayer(world, chargeTargetNickname);
        Mob mob = chargeTargetMobId == 0 ? null : preparedSocialTarget();
        if (player != null && waterAt(world, player.x(), player.y(), player.z())
                && distanceSquared(player.x(), player.y(), player.z()) <= CHARGE_HIT_RANGE * CHARGE_HIT_RANGE) {
            finishCharge();
            return java.util.List.of(new MobEvent.AttackPlayer(player.nickname(), contactDamage(world, CHARGE_DAMAGE), x, z, null, null));
        }
        if (mob != null && mob.id == chargeTargetMobId && !mob.isDead()
                && MobRelationshipPolicy.distanceSquared(this, mob) <= CHARGE_HIT_RANGE * CHARGE_HIT_RANGE) {
            finishCharge();
            return java.util.List.of(MobEvent.AttackMob.direct(mob.id, CHARGE_DAMAGE, null));
        }
        if (!bodyTouchesWater(world) || chargeDistance >= CHARGE_MAX_DISTANCE) {
            finishCharge();
            return java.util.List.of();
        }
        double step = Math.min(CHARGE_SPEED, CHARGE_MAX_DISTANCE - chargeDistance);
        MobPhysics.tickMove(this, world, chargeVx * step, chargeVy * step, chargeVz * step, MoveMode.SWIM);
        chargeDistance += step;
        state = MobState.CHASE;
        synchronizeChargeVisual();
        return java.util.List.of();
    }

    private boolean startCharge(MobWorldView world, PlayerSnapshot player, Mob mob) {
        double tx = player != null ? player.x() : mob.x;
        double ty = player != null ? player.y() + 0.9 : mob.y + mob.height() * 0.5;
        double tz = player != null ? player.z() : mob.z;
        if (!world.hasLineOfSight(x, y + eyeHeight(), z, tx, ty, tz)) return false;
        double dx = tx - x, dy = ty - (y + height() * 0.5), dz = tz - z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-9 || length > CHARGE_TARGET_RANGE) return false;
        chargePhase = ChargePhase.CHARGING;
        chargeTargetNickname = player == null ? null : player.nickname();
        chargeTargetMobId = mob == null ? 0 : mob.id;
        chargeVx = dx / length; chargeVy = dy / length; chargeVz = dz / length; chargeDistance = 0.0;
        synchronizeChargeVisual();
        return true;
    }
    private void finishCharge() {
        chargePhase = ChargePhase.IDLE;
        chargeCooldownMcTicks = CHARGE_COOLDOWN_MC_TICKS;
        chargeTargetNickname = null; chargeTargetMobId = 0;
        chargeVx = chargeVy = chargeVz = chargeDistance = 0.0;
        synchronizeChargeVisual();
    }
    private void clearCombatState() {
        angerTarget = null; angerMcTicks = 0;
        chargePhase = ChargePhase.IDLE; chargeCooldownMcTicks = 0;
        chargeTargetNickname = null; chargeTargetMobId = 0;
        chargeVx = chargeVy = chargeVz = chargeDistance = 0.0;
        synchronizeChargeVisual();
    }
    /**
     * [ROTTEN-LEATHER] 돌진 표적은 일반 타게팅 경로({@code Mob#acquireTarget})를 타지 않고
     * 여기서 직접 고르므로, 무적대 관문({@link Mob#mayTargetPlayer})을 이 자리에 함께 세운다.
     * 정적판 {@code StandaloneMobRuntime.nearestZombieNautilusPlayer} 도 같은 자리에 같은
     * 술어를 두고 있어 두 권위가 같은 표적을 낸다.
     */
    private PlayerSnapshot findWaterTarget(MobWorldView world) {
        PlayerSnapshot best = null;
        double bestSq = CHARGE_TARGET_RANGE * CHARGE_TARGET_RANGE;
        // [NAUTILUS-BEHAVIOR] 관문 1 — 종 중립. 중립 종은 도발당한 개체 하나만 표적으로 삼는다.
        // 원한이 없으면 플레이어 lane 자체가 닫히고, 복어 사냥 lane 만 남는다. 난수를 쓰지 않고
        // 플레이어를 보지도 않는 순수 술어다.
        //
        // [ZOMBIE-NAUTILUS-FIX] 두 관문의 합성 규약. 관문 1 과 아래 관문 2([ROTTEN-LEATHER]
        // 썩은 가죽 풀세트)는 AND 이고 순서가 있다 — 1 이 lane 전체를 열고, 2 가 그 위에서
        // 플레이어별로 거른다. 두 종이 모두 중립이 된 지금, 관문 2 는 <b>같은 세션 안에서는</b>
        // 결과를 바꾸지 못한다: hurtByPlayer 가 anger 와 undead grudge 를 항상 함께 세우므로
        // 1 이 열리는 순간 2 는 그 닉네임에 대해 이미 뚫려 있다.
        //
        // 그래도 관문 2 를 지운다면 회귀다. 두 상태는 <b>수명이 다르다</b> — anger 는 영속이고
        // (MobRuntime 이 restoreAnger 로 되살린다) undead grudge 는 세션 범위라 저장되지 않는다.
        // 재접속·청크 재활성화를 넘으면 "anger 는 있고 grudge 는 없는" 상태가 실제로 생기고,
        // 그 자리에서 풀세트가 다시 돌진을 막는다. ZombieNautilusNeutralityTest
        // .restoredAngerWithoutGrudgeIsWhereTheFullSetStillMatters 가 그 한 자리를 못 박는다.
        if (!attacksUnprovokedPlayers() && angerTarget == null) return null;
        for (PlayerSnapshot player : world.players()) {
            if (!player.alive() || !waterAt(world, player.x(), player.y(), player.z())) continue;
            // 관문 2 — [ROTTEN-LEATHER] 썩은 가죽 풀세트 무적대(플레이어별).
            if (!mayTargetPlayer(player)) continue;
            if (angerTarget != null && !angerTarget.equals(player.nickname())) continue;
            double distanceSq = distanceSquared(player.x(), player.y(), player.z());
            if (distanceSq <= bestSq) { best = player; bestSq = distanceSq; }
        }
        return best;
    }
    private static PlayerSnapshot findPlayer(MobWorldView world, String nickname) {
        for (PlayerSnapshot player : world.players()) if (player.alive() && nickname.equals(player.nickname())) return player;
        return null;
    }
    private static boolean waterAt(MobWorldView world, double x, double y, double z) {
        int id = world.getBlock((int) Math.floor(x), (int) Math.floor(y + 0.1), (int) Math.floor(z)) & 0xffff;
        return com.gameexpert.engine.Fluids.isWaterMedium(id);
    }
    private double distanceSquared(double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        return dx * dx + dy * dy + dz * dz;
    }
}

/**
 * 노틸러스(Java 1.21.11). 중립 — 도발당했을 때만 돌진으로 반격하고, 그와 별개로 근처 복어를
 * 사냥한다. 물 밖에서는 water creature 공용 건조 피해를 받는다([B]: "take suffocation damage
 * on land"), 그래서 {@code usesGenericDryAirSupply} 를 덮지 않는다.
 */
final class Nautilus extends NautilusFamilyMob {
    Nautilus(long id, double x, double y, double z) {
        super(id, MobType.NAUTILUS, x, y, z);
    }

    @Override protected boolean attacksUnprovokedPlayers() { return false; }

    @Override protected boolean healingRequiresOwner() { return false; }
}

/**
 * 좀비 노틸러스(Java 1.21.11). 언데드라 물 밖에서 질식하지 않는다.
 *
 * <p>[ZOMBIE-NAUTILUS-FIX] 이 종은 원문에서 <b>중립</b>이다 — [B] «Zombie Nautilus»
 * "Zombie nautiluses are neutral, attacking only when provoked". 옛 판이
 * {@code attacksUnprovokedPlayers} 를 참으로 두어 미길들임 개체가 사거리 안 물속 플레이어를
 * 무조건 돌진하던 것을 정정했다({@code docs/research/mc-nautilus-1-21-11.md} §8-4).
 * 그래서 이 클래스에 남는 재정의는 <b>건조 공기</b> 하나뿐이고, 중립 훅은 상위 기본값을 쓴다.
 */
final class ZombieNautilus extends NautilusFamilyMob {
    private final String variant;

    ZombieNautilus(long id, double x, double y, double z) {
        this(id, x, y, z, null);
    }

    ZombieNautilus(long id, double x, double y, double z, String variant) {
        super(id, MobType.ZOMBIE_NAUTILUS, x, y, z);
        this.variant = variant;
    }

    @Override public String variant() { return variant; }

    @Override protected boolean usesGenericDryAirSupply() { return false; }

    @Override protected boolean attacksUnprovokedPlayers() { return false; }
}
