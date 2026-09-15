package com.gameexpert.engine.mob;

import com.gameexpert.engine.inventory.PlayerInventory;

/**
 * Modern adult piglin hostility: gold armor suppresses ordinary acquisition while
 * a direct attacker remains an explicit target for the bounded anger window.
 */
public final class Piglin extends Pillager {
    public static final int ANGER_DURATION_AUTHORITY_TICKS = 300;
    /** 10 TPS 권위가 한 틱에 진행하는 MC tick 수. 25·45 같은 홀수 경계를 누산으로 보존한다. */
    public static final int MC_TICKS_PER_SERVER_TICK = 2;
    /**
     * 감탄(ADMIRE_ITEM) 지속. 바닐라 {@code PiglinAi.admireGoldItem} 은
     * {@code ADMIRING_ITEM} 메모리를 119 MC tick 만료로 세운다.
     */
    public static final int ADMIRE_ITEM_MC_TICKS = 119;
    /**
     * 물물교환/감탄 쿨다운(권위 틱). 119 MC tick = 59.5 권위 틱이라 올림한 60 틱이며,
     * 반 틱(+1 MC tick = 0.05초)은 10 TPS 권위 격자가 만드는 divergence 다
     * ({@code docs/MC-REFERENCE.md} 「피글린」 절이 계약을 소유한다).
     */
    public static final int BARTER_COOLDOWN_AUTHORITY_TICKS =
            (ADMIRE_ITEM_MC_TICKS + MC_TICKS_PER_SERVER_TICK - 1) / MC_TICKS_PER_SERVER_TICK;
    /**
     * {@code Piglin.finalizeSpawn}: {@code nextFloat() < 0.2} 면 새끼로 태어난다.
     * 새끼 분기는 무기 굴림을 아예 건너뛰고, 갑옷을 굴리는 {@code populateDefaultEquipmentSlots}
     * 도 {@code isAdult()} 에서 끊긴다 — 즉 새끼는 무기도 갑옷도 받지 않는다.
     */
    public static final double SPAWN_BABY_CHANCE = 0.2;
    /**
     * 새끼 피글린의 성장 시간(권위 틱). 번식 새끼와 같은 {@code MobRuntime.BABY_GROWTH_TICKS} 다.
     * 바닐라는 {@code age} 를 -24000 에서 올리지만 WebCraft 는 종을 가리지 않는 성장 타이머
     * 하나만 쓴다(divergence — MC-REFERENCE 「피글린」 절).
     */
    public static final int BABY_GROWTH_AUTHORITY_TICKS = MobRuntime.BABY_GROWTH_TICKS;
    /** {@code Piglin.createSpawnWeapon}: {@code nextFloat() < 0.5} 면 석궁, 아니면 금 검. */
    public static final double SPAWN_CROSSBOW_CHANCE = 0.5;
    /** {@code Piglin.maybeWearArmor}: 부위마다 {@code nextFloat() < 0.1} 로 금 갑옷을 입는다. */
    public static final double SPAWN_ARMOR_SLOT_CHANCE = 0.1;
    /** 금 검 피글린의 근접 사거리. 스켈레톤 근접 분기와 같은 WebCraft 매핑이다. */
    public static final double MELEE_RANGE = 1.5;
    /** {@code PiglinAi} FIGHT 의 {@code MeleeAttack(20)}. 20 MC tick = 10 권위 틱이다. */
    public static final int MELEE_ATTACK_MC_TICKS = 20;
    public static final int MELEE_ATTACK_AUTHORITY_TICKS =
            MELEE_ATTACK_MC_TICKS / MC_TICKS_PER_SERVER_TICK;
    /** {@code CrossbowItem.getChargeDuration} = 25 - 5*QuickCharge. 자연 피글린은 무마법이다. */
    public static final int CROSSBOW_CHARGE_MC_TICKS = 25;
    /** {@code CrossbowAttack}: 장전 완료 뒤 {@code attackDelay = 20 + nextInt(20)}. */
    public static final int CROSSBOW_DELAY_MIN_MC_TICKS = 20;
    public static final int CROSSBOW_DELAY_SPREAD_MC_TICKS = 20;
    /** {@code CrossbowItem.getDefaultProjectileRange()} = 8. 발사 판정 사거리 그대로다. */
    public static final double CROSSBOW_RANGE = 8.0;
    /** {@code PiglinAi} FIGHT: {@code BackUpIfTooClose.create(5, 0.75F)}. */
    public static final double BACK_UP_DISTANCE = 5.0;
    public static final double BACK_UP_SPEED_MODIFIER = 0.75;
    /** {@code PiglinAi.angerNearbyPiglins}: 플레이어 경계상자를 16 확장한 범위. */
    public static final double GUARDED_ANGER_RADIUS = 16.0;
    /**
     * {@code PiglinAi.wasHurtBy}: 맞은 개체가 새끼면 {@code AVOID_TARGET} 을 <b>100 MC tick</b>
     * 만료로 세우고 자기 표적은 잡지 않는다(대신 주변 피글린에게 분노를 전파한다).
     */
    public static final int BABY_AVOID_MC_TICKS = 100;
    /** 100 MC tick = 10 TPS 권위 50틱. 짝수라 반 틱 오차가 없다. */
    public static final int BABY_AVOID_AUTHORITY_TICKS =
            BABY_AVOID_MC_TICKS / MC_TICKS_PER_SERVER_TICK;
    /** {@code SetWalkTargetAwayFrom.entity(AVOID_TARGET, 1.0F, 12, true)} 의 속도 배율. */
    public static final double AVOID_SPEED_MODIFIER = 1.0;

    /**
     * {@code PiglinAi} 의 활동 우선순위. 바닐라 {@code Brain} 이
     * {@code ADMIRE_ITEM → FIGHT → AVOID → CELEBRATE → RIDE → IDLE} 순서로 첫 유효 활동을 고른다.
     *
     * <p>WebCraft 에서 {@code CELEBRATE} 와 {@code RIDE} 는 <b>도달 불가</b>다: 축하는
     * 호글린 사냥 성공(`PiglinAi.dontKillAnyMoreHoglinsForAWhile`)이 유일한 진입점인데
     * 호글린 종이 없고, 탑승은 스트라이더가 없다. 이 열거형은 순서를 코드로 못박아 두어
     * 호글린/스트라이더가 생기면 그 자리에 채우게 한다(MC-REFERENCE 「피글린」 절).
     */
    public enum Activity { ADMIRE_ITEM, FIGHT, AVOID, CELEBRATE, RIDE, IDLE }

    private String angerTarget;
    private int angerTicks;
    /** 새끼 도주(AVOID)의 남은 권위 틱. 바닐라 AVOID_TARGET 처럼 만료형 행동 상태라 영속하지 않는다. */
    private int babyAvoidTicks;
    private double avoidFromX;
    private double avoidFromZ;
    private int barterCooldown;
    /**
     * 남은 장전+지연 MC tick. 0이면 아직 장전을 시작하지 않았거나 이미 장전이 끝났다.
     * 바닐라 {@code CrossbowState} 와 같이 행동 상태라서 영속하지 않는다.
     */
    private int crossbowMcTicks;
    private boolean crossbowLoaded;
    /** 금 검 피글린의 근접 공격 재사용 대기. 행동 상태라 영속하지 않는다. */
    private int meleeTimer;

    public Piglin(long id, double x, double y, double z) {
        super(id, MobType.PIGLIN, x, y, z);
        setNativeEquipment("crossbow");
    }

    String angerTarget() { return angerTarget; }
    int babyAvoidTicksRemaining() { return babyAvoidTicks; }

    /**
     * 이번 틱 어떤 활동으로 판정되는가. 위 열거형 순서 그대로 첫 유효 활동을 고른다.
     * 회귀 테스트와 정적판 파리티가 이 함수를 직접 본다.
     */
    public Activity activity() {
        if (barterCooldown > 0) return Activity.ADMIRE_ITEM;
        // 새끼는 FIGHT 분기가 전부 isAdult 가드에 막혀 비어 있으므로 도주가 먼저 성립한다.
        if (isBaby()) return babyAvoidTicks > 0 ? Activity.AVOID : Activity.IDLE;
        if (angerTarget != null || hasPlayerTarget()) return Activity.FIGHT;
        if (babyAvoidTicks > 0) return Activity.AVOID;
        return Activity.IDLE;
    }
    int angerTicksRemaining() { return angerTicks; }
    int crossbowMcTicksRemaining() { return crossbowMcTicks; }
    boolean crossbowLoaded() { return crossbowLoaded; }

    /**
     * 바닐라 {@code Piglin.finalizeSpawn} 을 한 번만 돌린다.
     *
     * <p>순서도 바닐라 그대로다: 먼저 {@code nextFloat() < 0.2} 로 새끼인지 정하고, 새끼가
     * 아니면 무기(석궁 50% / 금 검 50%)를 뽑은 뒤 투구·흉갑·바지·신발을 각 10% 로 굴린다.
     * 새끼면 무기·갑옷 굴림을 <b>아예 건너뛴다</b>(난수도 소비하지 않는다).
     *
     * <p><b>"이미 굴렸다" 판정</b>: 성체 피글린은 항상 무기를 드니 {@code heldItem != 0} 이고,
     * 새끼는 영속 필드 {@code babyForm} 이 서 있다. 둘 다 저장되므로 재접속·언로드 복구가
     * 재추첨을 막는다. 성장 타이머를 다 쓴 새끼는 {@link #growOutOfBabyForm()} 이 성체로 바꾸고
     * 바로 그 자리에서 <b>무기 굴림만 한 번</b> 돈다(갑옷은 바닐라도 성장 경계에서 굴리지 않는다).
     */
    private void rollSpawnOnce(MobRandom rng) {
        if (heldItem() != 0) return;
        if (babyForm()) {
            // 성장 경계: 성체가 되는 그 틱에 무기 굴림 1회. 이후에는 heldItem 가드가 막는다.
            if (!growOutOfBabyForm()) return;
            rollSpawnWeapon(rng);
            return;
        }
        if (isBaby()) return;
        if (rng.nextFloat() < SPAWN_BABY_CHANCE) {
            restoreBabyForm(true);
            setAgeTicksRemaining(BABY_GROWTH_AUTHORITY_TICKS);
            return;
        }
        rollSpawnWeapon(rng);
        short[] armor = {PlayerInventory.GOLD_HELMET, PlayerInventory.GOLD_CHESTPLATE,
                PlayerInventory.GOLD_LEGGINGS, PlayerInventory.GOLD_BOOTS};
        for (short piece : armor) {
            if (rng.nextFloat() < SPAWN_ARMOR_SLOT_CHANCE) installGeneratedArmor(piece);
        }
    }

    /** [SPEAR-MOB][A] {@code Piglin.createSpawnWeapon} 의 금 창 굴림 {@code nextInt(10) == 0}. */
    public static final int SPAWN_SPEAR_BOUND = 10;

    /**
     * {@code Piglin.createSpawnWeapon}: {@code nextFloat() < 0.5} 면 석궁, 아니면 {@code nextInt(10) == 0} 이면 금 창,
     * 아니면 금 검(핀 26.3 jar).
     */
    private void rollSpawnWeapon(MobRandom rng) {
        boolean crossbow = rng.nextFloat() < SPAWN_CROSSBOW_CHANCE;
        installGeneratedHeldItem(crossbow ? PlayerInventory.CROSSBOW
                : rng.nextInt(SPAWN_SPEAR_BOUND) == 0 ? PlayerInventory.GOLD_SPEAR : PlayerInventory.GOLD_SWORD);
    }

    /** 금 검 · 금 창을 든 개체인가. 석궁 상태기계 대신 근접 goal 을 쓴다. */
    public boolean usesMeleeWeapon() {
        return heldItem() == PlayerInventory.GOLD_SWORD || usesSpear();
    }

    /** [SPEAR-MOB] {@code KINETIC_WEAPON} 을 쥔 성체: FIGHT 의 Spear* 행동이 돈다. */
    public boolean usesSpear() {
        return !isBaby() && com.gameexpert.engine.SpearRules.kinetic(heldItem()) != null;
    }

    // ── [SPEAR-MOB] 피글린 두뇌의 창 행동(SpearApproach · SpearAttack · SpearRetreat). 저장하지 않는다 ──
    private SpearUseAi.BrainState spearBrain;

    /** [SPEAR-MOB] 창 두뇌 기억(없으면 null). */
    public SpearUseAi.BrainState spearBrainState() { return spearBrain; }

    @Override SpearUseAi.BrainState spearBrainForSync() { return spearBrain; }

    @Override public boolean spearUseEligible() { return true; }
    /** [SPEAR-MOB] {@code ATTACK_DAMAGE} 속성 기본값(5). */
    @Override public double attackDamageAttributeBase() { return 5.0; }

    /**
     * [SPEAR-MOB] 두뇌는 MC 틱마다 돈다 — 권위 틱 하나에 {@code tickBrain} 두 번, 뒤 틱의 탐색 목적지로 걷는다.
     */
    @Override
    protected double[] engagedMovement(MobWorldView world, MobRandom rng, double targetX, double targetY,
            double targetZ, double targetEyeY) {
        if (!usesSpear()) {
            stopPiglinSpear();
            return null;
        }
        if (spearBrain == null) spearBrain = new SpearUseAi.BrainState();
        com.gameexpert.engine.SpearRules.Kinetic kinetic = com.gameexpert.engine.SpearRules.kinetic(heldItem());
        aimSpear(targetX, targetZ, targetEyeY);
        boolean wasUsing = spearBrain.using();
        SpearUseAi.Decision decision = SpearUseAi.Decision.STAY;
        for (int mcTick = 0; mcTick < MC_TICKS_PER_SERVER_TICK; mcTick++) {
            decision = SpearUseAi.tickBrain(spearBrain, kinetic, world.worldTick() * 2L + mcTick, true, x, y, z,
                    targetX, targetY, targetZ, isMobPassenger(), SpearUseAi.chargeSpeedModifier(vehicleType),
                    (min, max, yRange, fx, fy, fz) -> SpearUseAi.posAway(rng, world, x, y, z, min, max, yRange,
                            fx, fy, fz));
        }
        syncSpearUse(wasUsing);
        state = MobState.CHASE;
        if (!decision.move()) return new double[] {0.0, 0.0};
        double[] toward = towardHoriz(decision.toX(), decision.toZ(), type.baseSpeed());
        return new double[] {toward[0] * decision.speedModifier(), toward[1] * decision.speedModifier()};
    }

    /** [SPEAR-MOB] 표적이 없으면 도는 행동의 {@code canStillUse} 가 거짓이라 멈춘다(SPEAR_STATUS 기억은 남는다). */
    private void stopPiglinSpear() {
        if (spearBrain == null) return;
        boolean wasUsing = spearBrain.using();
        if (spearBrain.approachRunning() || spearBrain.attackRunning() || spearBrain.retreatRunning()) {
            SpearUseAi.tickBrain(spearBrain, null, 0L, false, x, y, z, x, y, z, false, 1.0f,
                    (min, max, yRange, fx, fy, fz) -> null);
        }
        syncSpearUse(wasUsing);
    }

    /** 표적을 잃으면 바닐라처럼 UNCHARGED 로 돌아가 다음 교전에서 다시 장전하고, 도는 창 행동도 멈춘다. */
    @Override
    protected void onDisengaged() {
        crossbowMcTicks = 0;
        crossbowLoaded = false;
        stopPiglinSpear();
    }

    /**
     * 장비 렌더 힌트는 실제 주손 아이템에서 파생한다. 영속에는 주손 아이템만 있으므로
     * 재접속·언로드 복구 뒤에도 금 검 개체가 석궁으로 보이지 않는다.
     */
    @Override
    public String nativeEquipment() {
        return usesMeleeWeapon() ? "gold_sword" : "crossbow";
    }

    @Override protected double shootRange() { return CROSSBOW_RANGE; }
    /** 금 검 개체는 {@code MeleeAttack} 이라 후퇴하지 않고 접촉 거리까지 붙는다. */
    @Override protected double minKeepDistance() {
        return usesMeleeWeapon() ? 0.0 : BACK_UP_DISTANCE;
    }
    @Override protected double maxKeepDistance() {
        return usesMeleeWeapon() ? MELEE_RANGE : CROSSBOW_RANGE;
    }
    @Override protected double retreatSpeedMultiplier() { return BACK_UP_SPEED_MODIFIER; }
    /** 발사 주기는 아래 석궁 상태기계가 전부 소유한다. */
    @Override protected int shootIntervalTicks() { return 0; }
    /** {@code CrossbowAttackMob.performCrossbowAttack} 도 활과 같은 난이도 산포를 쓴다. */
    @Override protected boolean spreadArrow() { return true; }
    /** {@code PiglinAi} FIGHT 의 석궁·근접 분기는 모두 {@code RunIf(Piglin::isAdult)} 다. */
    @Override protected boolean mayShoot() {
        return !isBaby() && !usesMeleeWeapon() && crossbowLoaded;
    }

    /**
     * {@code PiglinAi} FIGHT 의 {@code MeleeAttack(20)}. 금 검 · 금 창 개체만 이 분기를 탄다. 창은
     * {@code Mob.isWithinMeleeAttackRange} 가 {@code ATTACK_RANGE}(최소 2 · 최대 4.5, 몹 배율 0.5)를 쓴다.
     */
    @Override
    protected MobEvent meleeAttack(MobWorldView world, PlayerSnapshot target) {
        boolean inReach = usesSpear()
                ? com.gameexpert.engine.SpearRules.mobSpearMeleeReach(x, y, z, width(), height(), target.x(),
                        target.y(), target.z(), 0.6, target.crouching() ? 1.5 : 1.8)
                : dist3d(target) <= MELEE_RANGE;
        if (!usesMeleeWeapon() || meleeTimer > 0 || !inReach || !canSeeTargetNow(world, target)) {
            return null;
        }
        int damage = enchantedMeleeDamage(
                com.gameexpert.engine.CombatRules.meleeDamage(heldItem()), null);
        return new MobEvent.AttackPlayer(
                target.nickname(), contactDamage(world, damage), x, z);
    }

    @Override
    public void commitAcceptedMeleeAttack() {
        meleeTimer = MELEE_ATTACK_AUTHORITY_TICKS;
        super.commitAcceptedMeleeAttack();
    }

    /**
     * {@code CrossbowAttack}: UNCHARGED → CHARGING(25) → CHARGED(20+nextInt(20)) → READY_TO_ATTACK.
     * 두 단계는 연속된 카운트다운이라 하나의 MC tick 커서로 순서대로 진행한다.
     */
    @Override
    protected void onEngagedTick(MobRandom rng) {
        // 새끼는 무기가 없어 장전 상태기계 자체를 돌리지 않는다(FIGHT 는 isAdult 가드다).
        if (isBaby() || usesMeleeWeapon() || crossbowLoaded) return;
        if (crossbowMcTicks == 0) {
            crossbowMcTicks = CROSSBOW_CHARGE_MC_TICKS + CROSSBOW_DELAY_MIN_MC_TICKS
                    + rng.nextInt(CROSSBOW_DELAY_SPREAD_MC_TICKS);
            return;
        }
        for (int mcTick = 0; mcTick < MC_TICKS_PER_SERVER_TICK && crossbowMcTicks > 0; mcTick++) {
            if (--crossbowMcTicks == 0) crossbowLoaded = true;
        }
    }


    @Override
    protected void onArrowReleased() {
        crossbowLoaded = false;
    }

    void restoreAnger(String target, int ticks) {
        if (target == null ? ticks != 0
                : target.isEmpty() || ticks <= 0 || ticks > ANGER_DURATION_AUTHORITY_TICKS) {
            throw new IllegalArgumentException("invalid persisted Piglin anger");
        }
        angerTarget = target;
        angerTicks = ticks;
        forceTarget(target);
    }

    @Override
    protected boolean canTargetPlayer(PlayerSnapshot player) {
        return player != null && (!player.wearingGoldArmor()
                || angerTarget != null && angerTarget.equals(player.nickname()));
    }

    @Override
    public void onHurt(String attackerNickname, double attackerX, double attackerZ) {
        if (attackerNickname == null) return;
        barterCooldown = 0;
        if (isBaby()) {
            // 바닐라 PiglinAi.wasHurtBy: 새끼는 AVOID_TARGET(100 MC tick)만 세우고 자기 표적은
            // 잡지 않는다. 주변 피글린 분노 전파는 호출부(MobSystem)의 기존 경로가 그대로 한다.
            babyAvoidTicks = BABY_AVOID_AUTHORITY_TICKS;
            avoidFromX = attackerX;
            avoidFromZ = attackerZ;
            clearTrackedTarget();
            return;
        }
        setAngerTarget(attackerNickname);
    }

    /**
     * {@code PiglinAi.isIdle} 필터. 이미 분노했거나 감탄(물물교환) 중인 피글린은
     * 보호 블록 사건으로 표적을 갈아타지 않는다.
     */
    public boolean idleForGuardedAnger() {
        return !isDead() && !removed && angerTarget == null && barterCooldown == 0;
    }

    /**
     * {@code PiglinAi.angerNearbyPiglins} 가 부르는 {@code setAngerTarget}. 금 방어구는
     * 새 표적 선정만 막으므로 여기서 확정된 표적은 방어구와 무관하게 유지된다.
     */
    public void angerAtGuardedEvent(String nickname) {
        if (nickname == null || !idleForGuardedAnger()) return;
        setAngerTarget(nickname);
    }

    private void setAngerTarget(String nickname) {
        angerTarget = nickname;
        angerTicks = ANGER_DURATION_AUTHORITY_TICKS;
        forceTarget(nickname);
    }

    public boolean tryBeginBarter() {
        if (isDead() || isBaby() || barterCooldown > 0) return false;
        barterCooldown = BARTER_COOLDOWN_AUTHORITY_TICKS;
        markVisualAction("barter", BARTER_COOLDOWN_AUTHORITY_TICKS);
        clearTrackedTarget();
        onDisengaged();
        return true;
    }

    @Override
    public java.util.List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        rollSpawnOnce(rng);
        if (meleeTimer > 0) meleeTimer--;
        if (barterCooldown > 0) {
            barterCooldown--;
            state = MobState.IDLE;
            MobPhysics.tickMove(this, world, 0.0, 0.0, 0.0, MoveMode.WALK);
            return java.util.List.of();
        }
        // AVOID: 새끼는 표적을 잡는 대신 가해자 반대 방향으로 100 MC tick 동안 달아난다.
        if (babyAvoidTicks > 0) {
            babyAvoidTicks--;
            state = MobState.FLEE;
            double[] away = towardHoriz(avoidFromX, avoidFromZ,
                    type.baseSpeed() * AVOID_SPEED_MODIFIER);
            MobPhysics.tickMove(this, world, -away[0], 0.0, -away[1], MoveMode.WALK);
            return java.util.List.of();
        }
        if (angerTarget != null) forceTarget(angerTarget);
        java.util.List<MobEvent> events = super.tick(world, rng);
        if (angerTicks > 0 && --angerTicks == 0) {
            angerTarget = null;
            clearTrackedTarget();
        }
        return events;
    }
}
