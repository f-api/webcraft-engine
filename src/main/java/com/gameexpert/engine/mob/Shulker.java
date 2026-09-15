package com.gameexpert.engine.mob;

import java.util.ArrayList;
import java.util.List;

/**
 * [EC-MOBS] 셜커(바닐라 실존 종, stableId 104). 수치·기하는 {@link ShulkerRules}, 탄환은
 * {@link ShulkerBulletRules} 가 소유한다.
 *
 * <p><b>한 권위 틱 = MC 틱 두 번.</b> 바닐라 {@code Mob.serverAiStep} 은 짝수 MC 틱에 목표
 * 선택기 전체({@code GoalSelector.tick})를, 홀수 MC 틱에 매 틱 갱신 목표만
 * ({@code tickRunningGoals(false)}) 돈다. 그래서 여기서는 권위 틱마다 "전체 한 번 + 매 틱 목표 한 번"
 * 을 순서대로 돈다. 목표와 우선순위는 {@code Shulker.registerGoals} 그대로다:
 * <ul>
 *   <li>goal: 1 {@code LookAtPlayerGoal(8, 0.02, 수평만)} [LOOK] · 4 {@code ShulkerAttackGoal}
 *       [MOVE, LOOK, 매 틱] · 7 {@code ShulkerPeekGoal} · 8 {@code RandomLookAroundGoal}
 *       [MOVE, LOOK, 매 틱].</li>
 *   <li>target: 1 {@code HurtByTargetGoal(Shulker 무시, 같은 종 경보)} · 2
 *       {@code ShulkerNearestAttackGoal(Player, mustSee)} · 3 {@code ShulkerDefenseAttackGoal}
 *       (팀이 있어야 동작한다 — 이 저장소에는 스코어보드 팀이 없어 영원히 쓰이지 않는다).</li>
 * </ul>
 *
 * <p><b>움직이지 않는다.</b> {@code getDeltaMovement} 는 언제나 0, {@code setDeltaMovement}·
 * {@code push} 는 아무것도 하지 않으므로 넉백·유체·중력·밀어내기 전부 받지 않는다. 위치는
 * 순간이동으로만 바뀌며 칸 중심 발밑({@code floor(x)+0.5, floor(y+0.5), floor(z)+0.5})에 붙는다.
 */
public final class Shulker extends Mob {

    /** 대상. 플레이어면 {@code targetNickname}, 몹이면 {@code targetMobId}. 둘 다 없으면 대상 없음. */
    private String targetNickname;
    private long targetMobId;

    private int attachFace = ShulkerRules.DEFAULT_ATTACH_FACE;
    private int rawPeek;
    private float currentPeek;
    /** {@code Shulker.DATA_COLOR_ID}. 16 = 무색(기본). 이 저장소에는 색을 바꾸는 경로가 없다. */
    private static final int NO_COLOR = 16;

    // ── 목표 상태(휘발) ──
    private boolean lookAtRunning;
    private String lookAtNickname;
    private int lookAtTime;
    private boolean attackRunning;
    private int attackTimeMcTicks;
    private boolean peekRunning;
    private int peekTime;
    private boolean randomLookRunning;
    private double randomLookX, randomLookZ;
    private int randomLookTime;
    // ── 대상 목표 상태(휘발) ──
    private boolean hurtByRunning;
    private int hurtByTimestamp;
    private String hurtByTargetNickname;
    private long hurtByTargetMobId;
    private int hurtByUnseenTicks;
    private boolean nearestRunning;
    private int nearestUnseenTicks;
    /** {@code getLastHurtByMob} 와 그 타임스탬프. 플레이어 또는 몹 하나. */
    private String lastHurtByNickname;
    private long lastHurtByMobId;
    private int lastHurtByTimestamp;
    // ── LookControl(휘발) ──
    private double wantedX, wantedY, wantedZ;
    private float yMaxRotSpeed;
    private int lookAtCooldown;
    private float headYawDegrees;
    // ── 이번 틱 피격 후처리(같은 권위 틱 안에서 AI 가 소비한다) ──
    private boolean pendingHurtReaction;
    private boolean pendingBulletHit;
    /** 같은 월드의 살아 있는 셜커 목록(런타임이 매 틱 준다). 충돌·복제·경보가 읽는다. */
    private List<Shulker> neighborhood = List.of();
    private List<Mob> mobContext = List.of();

    public Shulker(long id, double x, double y, double z) {
        super(id, MobType.SHULKER, x, y, z);
        snapToCell();
    }

    /** {@code Shulker.setPos}: 탑승 중이 아니면 칸 중심 발밑으로 맞춘다. */
    private void snapToCell() {
        x = Math.floor(x) + 0.5;
        y = Math.floor(y + 0.5);
        z = Math.floor(z) + 0.5;
    }

    public int blockX() { return (int) Math.floor(x); }
    public int blockY() { return (int) Math.floor(y); }
    public int blockZ() { return (int) Math.floor(z); }
    public int attachFace() { return attachFace; }
    public int rawPeek() { return rawPeek; }
    public float currentPeek() { return currentPeek; }
    public boolean closed() { return rawPeek == 0; }
    public int color() { return NO_COLOR; }
    public String shulkerTargetNickname() { return targetNickname; }
    public long shulkerTargetMobId() { return targetMobId; }

    /** 영속 복원: 부착면과 raw peek. 현재 peek 는 바닐라처럼 raw 에서 다시 따라간다. */
    void restoreShulkerState(int face, int peek) {
        if (!ItemFrameRules.validDirection(face) || peek < 0 || peek > 100) {
            throw new IllegalStateException("invalid persisted Shulker state");
        }
        attachFace = face;
        rawPeek = peek;
        currentPeek = peek * 0.01f;
        snapToCell();
    }

    /** 런타임이 매 틱 넘기는 이웃 셜커·몹 목록. */
    void prepareNeighborhood(List<Shulker> shulkers, List<Mob> mobs) {
        neighborhood = shulkers;
        mobContext = mobs;
    }

    @Override public double eyeHeight() { return ShulkerRules.EYE_HEIGHT; }
    @Override public boolean immovable() { return true; }
    @Override public boolean fireImmune() { return true; }
    @Override public int ambientSoundInterval() { return ShulkerRules.AMBIENT_SOUND_INTERVAL_MC_TICKS; }
    @Override public boolean hasStepSound() { return false; }
    /** {@code Shulker.playAmbientSound}: 닫혀 있으면 소리 없이 되감기만 한다. */
    @Override protected boolean ambientSoundAudible() { return !closed(); }

    /** 닫혀 있는 동안 {@code COVERED_ARMOR_MODIFIER} +20. */
    @Override
    public double armor() {
        return super.armor() + ShulkerRules.coveredArmor(rawPeek);
    }

    @Override
    public double[] authorityAabb() {
        return ShulkerRules.boundingBox(attachFace, currentPeek, x, y, z);
    }

    @Override
    public int visualFlags() {
        return ShulkerRules.visualFlags(attachFace, rawPeek);
    }

    @Override
    protected void onDamageSurvived(long tickNo) {
        pendingHurtReaction = true;
    }

    /** 셜커 탄환에 맞아 실제 피해를 받았다({@code DamageTypeTags.IS_PROJECTILE} + SHULKER_BULLET). */
    public void markShulkerBulletHit() {
        if (!pendingHurtReaction) return;
        pendingBulletHit = true;
    }

    /**
     * 화살류({@code AbstractArrow}: 화살·삼지창) 직격은 닫혀 있으면 {@code hurtServer} 가 거짓이다.
     */
    public boolean rejectsArrow() {
        return closed();
    }

    @Override
    public void onHurt(String attackerNickname, double attackerX, double attackerZ) {
        if (attackerNickname == null) return;
        lastHurtByNickname = attackerNickname;
        lastHurtByMobId = 0L;
        lastHurtByTimestamp++;
    }

    /** 몹이 때렸을 때({@code HurtByTargetGoal} 의 입력). 다른 셜커는 무시 대상이다. */
    public void hurtByMob(Mob attacker) {
        if (attacker == null || attacker.type == MobType.SHULKER) return;
        lastHurtByNickname = null;
        lastHurtByMobId = attacker.id;
        lastHurtByTimestamp++;
    }

    private void setRawPeek(int peek, List<MobEvent> events) {
        rawPeek = peek;
        events.add(new MobEvent.Sound(peek == 0 ? "close" : "open"));
    }

    // ── 대상 해석 ──────────────────────────────────────────────────────────────

    private record Resolved(PlayerSnapshot player, Mob mob) {
        double x() { return player != null ? player.x() : mob.x; }
        double y() { return player != null ? player.y() : mob.y; }
        double z() { return player != null ? player.z() : mob.z; }
        double eyeY() {
            return player != null ? player.y() + com.gameexpert.engine.PlayerInteractionRules
                    .eyeHeight(player.crouching()) : mob.y + mob.eyeHeight();
        }
        boolean alive() { return player != null ? player.alive() : !mob.isDead() && !mob.removed; }
    }

    private Resolved resolve(MobWorldView world, String nickname, long mobId) {
        if (nickname != null) {
            for (PlayerSnapshot p : world.players()) {
                if (p.nickname().equals(nickname)) return new Resolved(p, null);
            }
            return null;
        }
        if (mobId != 0L) {
            for (Mob m : mobContext) {
                if (m.id == mobId && !m.removed) return new Resolved(null, m);
            }
        }
        return null;
    }

    private Resolved target(MobWorldView world) {
        return resolve(world, targetNickname, targetMobId);
    }

    private boolean hasTarget() { return targetNickname != null || targetMobId != 0L; }

    private void setTarget(String nickname, long mobId) {
        targetNickname = nickname;
        targetMobId = nickname != null ? 0L : mobId;
    }

    private double distanceSqr(double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private boolean lineOfSight(MobWorldView world, Resolved target) {
        return world.hasLineOfSight(x, y + eyeHeight(), z, target.x(), target.eyeY(), target.z());
    }

    /** {@code TargetingConditions.forCombat().range(16)} 의 플레이어 판정(mustSee 포함). */
    private boolean combatTargetable(MobWorldView world, PlayerSnapshot p, boolean checkSight) {
        if (!p.alive() || !mayTargetPlayer(p)) return false;
        double visibility = p.crouching() ? 0.8 : 1.0;
        double max = Math.max(ShulkerRules.FOLLOW_RANGE * visibility, 2.0);
        if (distanceSqr(p.x(), p.y(), p.z()) > max * max) return false;
        return !checkSight || lineOfSight(world, new Resolved(p, null));
    }

    // ── 틱 ──────────────────────────────────────────────────────────────────

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead()) return List.of();
        List<MobEvent> events = new ArrayList<>(2);
        knockbackVx = 0;
        knockbackVz = 0;
        horizontalVx = 0;
        horizontalVz = 0;
        vy = 0;
        ShulkerRules.AttachWorld attach = attachWorld(world);
        if (pendingHurtReaction) {
            pendingHurtReaction = false;
            boolean bullet = pendingBulletHit;
            pendingBulletHit = false;
            double[] oldBox = authorityAabb();
            double oldX = x, oldY = y, oldZ = z;
            // Shulker.hurtServer: 체력이 절반 미만이면 1/4 확률로 순간이동, 아니면 탄환이면 복제 판정.
            if (exactHealth() < maxHp() * 0.5 && rng.nextInt(4) == 0) {
                teleportSomewhere(world, attach, rng, events);
            } else if (bullet) {
                hitByShulkerBullet(world, attach, rng, events, oldBox, oldX, oldY, oldZ);
            }
        }
        for (int mcTick = 0; mcTick < 2; mcTick++) {
            // LivingEntity.tick → serverAiStep: 목표 선택기와 LookControl 이 먼저다.
            if (mcTick == 0) {
                tickTargetSelector(world, attach, rng);
                tickGoalSelector(world, attach, rng, events);
            } else {
                tickRunningGoals(world, rng, events);
            }
            tickLookControl();
            // 그 뒤 Shulker.tick: 부착이 무효면 새 면을 찾거나 순간이동하고 peek 를 한 걸음 옮긴다.
            if (!ShulkerRules.canStayAt(attach, id, blockX(), blockY(), blockZ(), attachFace)) {
                int face = ShulkerRules.findAttachableSurface(attach, id, blockX(), blockY(), blockZ());
                if (face >= 0) attachFace = face;
                else teleportSomewhere(world, attach, rng, events);
            }
            currentPeek = ShulkerRules.stepPeek(currentPeek, rawPeek);
        }
        state = attackRunning ? MobState.ATTACK : MobState.IDLE;
        yaw = Math.toRadians(headYawDegrees);
        return events;
    }

    private ShulkerRules.AttachWorld attachWorld(MobWorldView world) {
        return new ShulkerRules.AttachWorld() {
            @Override public int minY() { return com.gameexpert.terrain.Blocks.MIN_Y; }
            @Override public boolean air(int bx, int by, int bz) {
                return (world.getBlock(bx, by, bz) & 0xffff) == 0;
            }
            @Override public boolean sturdyFace(int bx, int by, int bz, int face) {
                int block = world.getBlock(bx, by, bz);
                if (block < 0) return false;
                return ShulkerSupport.sturdyFace(block & 0xffff,
                        world.blockState(bx, by, bz, block & 0xffff), face);
            }
            @Override public boolean withinBorder(int bx, int bz) { return TeleportSafety.withinBorder(bx, bz); }
            @Override public boolean teleportSurfaceAllowed(int bx, int by, int bz) {
                return world.getBlock(bx, by, bz) != com.gameexpert.terrain.Blocks.BEDROCK;
            }
            @Override public boolean collides(long selfMobId, double minX, double minY,
                    double minZ, double maxX, double maxY, double maxZ) {
                if (MobPhysics.blockCollision(world, minX, minY, minZ, maxX, maxY, maxZ)) {
                    return true;
                }
                for (Shulker other : neighborhood) {
                    if (other.id == selfMobId || other.isDead() || other.removed) continue;
                    double[] box = other.authorityAabb();
                    if (minX < box[3] && maxX > box[0] && minY < box[4] && maxY > box[1]
                            && minZ < box[5] && maxZ > box[2]) return true;
                }
                return false;
            }
        };
    }

    /** {@code teleportSomewhere}. 성공하면 true. */
    private boolean teleportSomewhere(MobWorldView world, ShulkerRules.AttachWorld attach,
            MobRandom rng, List<MobEvent> events) {
        if (isDead()) return false;
        ShulkerRules.Teleport found = ShulkerRules.teleportSearch(
                attach, id, blockX(), blockY(), blockZ(), rng);
        if (found == null) return false;
        double fromX = x, fromY = y, fromZ = z;
        attachFace = found.attachFace();
        events.add(new MobEvent.Sound("teleport"));
        x = found.x() + 0.5;
        y = found.y();
        z = found.z() + 0.5;
        // setPos 가 칸을 옮기면 DATA_PEEK 를 0 으로(소리 없이) 되돌리고, teleportSomewhere 도 0 을 쓴다.
        rawPeek = 0;
        setTarget(null, 0L);
        events.add(new MobEvent.Teleported(fromX, fromY, fromZ, x, y, z));
        return true;
    }

    private void hitByShulkerBullet(MobWorldView world, ShulkerRules.AttachWorld attach,
            MobRandom rng, List<MobEvent> events, double[] oldBox,
            double oldX, double oldY, double oldZ) {
        if (closed()) return;
        if (!teleportSomewhere(world, attach, rng, events)) return;
        double r = ShulkerRules.OTHER_SHULKER_SCAN_RADIUS;
        int nearby = 0;
        for (Shulker other : neighborhood) {
            if (other.isDead() || other.removed) continue;
            double[] box = other.authorityAabb();
            if (box[0] < oldBox[3] + r && box[3] > oldBox[0] - r
                    && box[1] < oldBox[4] + r && box[4] > oldBox[1] - r
                    && box[2] < oldBox[5] + r && box[5] > oldBox[2] - r) nearby++;
        }
        if (ShulkerRules.duplicates(nearby, rng)) {
            events.add(new MobEvent.ShulkerClone(oldX, oldY, oldZ));
        }
    }

    // ── 대상 선택기 ──────────────────────────────────────────────────────────────

    private void tickTargetSelector(MobWorldView world, ShulkerRules.AttachWorld attach,
            MobRandom rng) {
        // 1) 계속할 수 없는 대상 목표를 멈춘다.
        if (hurtByRunning && !continueTargetGoal(world, hurtByTargetNickname, hurtByTargetMobId,
                true)) {
            hurtByRunning = false;
            setTarget(null, 0L);
            hurtByTargetNickname = null;
            hurtByTargetMobId = 0L;
        }
        if (nearestRunning && !continueTargetGoal(world, null, 0L, false)) {
            nearestRunning = false;
            setTarget(null, 0L);
        }
        // 2) 우선순위 1: HurtByTargetGoal(TARGET). 2 가 돌고 있어도 끼어든다.
        if (!hurtByRunning && lastHurtByTimestamp != hurtByTimestamp
                && (lastHurtByNickname != null || lastHurtByMobId != 0L)) {
            Resolved attacker = resolve(world, lastHurtByNickname, lastHurtByMobId);
            boolean ok = attacker != null && attacker.alive()
                    && (attacker.player() == null || mayTargetPlayer(attacker.player()));
            if (ok) {
                if (nearestRunning) {
                    nearestRunning = false;
                    setTarget(null, 0L);
                }
                hurtByRunning = true;
                setTarget(lastHurtByNickname, lastHurtByMobId);
                hurtByTargetNickname = lastHurtByNickname;
                hurtByTargetMobId = lastHurtByMobId;
                hurtByTimestamp = lastHurtByTimestamp;
                hurtByUnseenTicks = 0;
                alertOthers(world);
            }
        }
        // 3) 우선순위 2: ShulkerNearestAttackGoal(Player, mustSee). HurtBy 가 TARGET 을 쥐면 못 든다.
        if (!nearestRunning && !hurtByRunning) {
            // TargetGoal.randomInterval = reducedTickDelay(10) = 5: nextInt(5) != 0 이면 쉰다.
            if (rng.nextInt(5) == 0) {
                PlayerSnapshot best = null;
                double bestSq = Double.MAX_VALUE;
                for (PlayerSnapshot p : world.players()) {
                    if (!combatTargetable(world, p, true)) continue;
                    double dx = p.x() - x, dy = p.y() - (y + eyeHeight()), dz = p.z() - z;
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d < bestSq) { bestSq = d; best = p; }
                }
                if (best != null) {
                    nearestRunning = true;
                    nearestUnseenTicks = 0;
                    setTarget(best.nickname(), 0L);
                }
            }
        }
    }

    /** {@code TargetGoal.canContinueToUse}. HurtBy 는 기억한 가해자로 대상을 되살린다. */
    private boolean continueTargetGoal(MobWorldView world, String rememberedNickname,
            long rememberedMobId, boolean hurtBy) {
        String nickname = targetNickname;
        long mobId = targetMobId;
        if (nickname == null && mobId == 0L) {
            nickname = rememberedNickname;
            mobId = rememberedMobId;
        }
        Resolved target = resolve(world, nickname, mobId);
        if (target == null || !target.alive()) return false;
        if (target.player() != null && !mayTargetPlayer(target.player())) return false;
        double range = ShulkerRules.FOLLOW_RANGE;
        if (distanceSqr(target.x(), target.y(), target.z()) > range * range) return false;
        if (lineOfSight(world, target)) {
            if (hurtBy) hurtByUnseenTicks = 0; else nearestUnseenTicks = 0;
        } else {
            // unseenMemoryTicks: Nearest 60, HurtBy 300 → reducedTickDelay 30 / 150.
            int limit = hurtBy ? 150 : 30;
            int unseen = hurtBy ? ++hurtByUnseenTicks : ++nearestUnseenTicks;
            if (unseen > limit) return false;
        }
        setTarget(nickname, mobId);
        return true;
    }

    /** {@code HurtByTargetGoal.alertOthers}: 발밑 칸 상자를 (16, 10, 16) 부풀린 범위의 대상 없는 셜커. */
    private void alertOthers(MobWorldView world) {
        double range = ShulkerRules.FOLLOW_RANGE;
        double minX = Math.floor(x) - range, maxX = Math.floor(x) + 1 + range;
        double minY = Math.floor(y) - 10, maxY = Math.floor(y) + 1 + 10;
        double minZ = Math.floor(z) - range, maxZ = Math.floor(z) + 1 + range;
        for (Shulker other : neighborhood) {
            if (other == this || other.isDead() || other.removed || other.hasTarget()) continue;
            double[] box = other.authorityAabb();
            if (box[0] < maxX && box[3] > minX && box[1] < maxY && box[4] > minY
                    && box[2] < maxZ && box[5] > minZ) {
                other.setTarget(lastHurtByNickname, lastHurtByMobId);
            }
        }
    }

    // ── 목표 선택기 ──────────────────────────────────────────────────────────────

    private static final int LOOK_AT_PRIORITY = 1;
    private static final int ATTACK_PRIORITY = 4;
    private static final int RANDOM_LOOK_PRIORITY = 8;

    /** LOOK 플래그를 쥔 목표의 우선순위(없으면 MAX). MOVE 는 공격·배회 시선만 쥔다. */
    private int lookLockPriority() {
        if (lookAtRunning) return LOOK_AT_PRIORITY;
        if (attackRunning) return ATTACK_PRIORITY;
        if (randomLookRunning) return RANDOM_LOOK_PRIORITY;
        return Integer.MAX_VALUE;
    }

    private int moveLockPriority() {
        if (attackRunning) return ATTACK_PRIORITY;
        if (randomLookRunning) return RANDOM_LOOK_PRIORITY;
        return Integer.MAX_VALUE;
    }

    private void tickGoalSelector(MobWorldView world, ShulkerRules.AttachWorld attach,
            MobRandom rng, List<MobEvent> events) {
        // 1) 계속할 수 없는 목표를 멈춘다(등록 순서).
        if (lookAtRunning && !continueLookAt(world)) stopLookAt();
        if (attackRunning && !attackCanUse(world)) stopAttack(events);
        if (peekRunning && !(!hasTarget() && peekTime > 0)) stopPeek(events);
        if (randomLookRunning && !(randomLookTime >= 0)) randomLookRunning = false;
        // 2) 멈춘 목표를 등록 순서대로 시작한다.
        if (!lookAtRunning && LOOK_AT_PRIORITY < lookLockPriority() && lookAtCanUse(world, rng)) {
            if (attackRunning) stopAttack(events);
            if (randomLookRunning) randomLookRunning = false;
            lookAtRunning = true;
            // LookAtPlayerGoal.start: adjustedTickDelay(40 + nextInt(40)) = ceil(/2).
            lookAtTime = (40 + rng.nextInt(40) + 1) / 2;
        }
        if (!attackRunning && ATTACK_PRIORITY < lookLockPriority()
                && ATTACK_PRIORITY < moveLockPriority() && attackCanUse(world)) {
            if (randomLookRunning) randomLookRunning = false;
            attackRunning = true;
            attackTimeMcTicks = ShulkerRules.ATTACK_START_DELAY_MC_TICKS;
            setRawPeek(ShulkerRules.ATTACK_RAW, events);
        }
        if (!peekRunning && !hasTarget() && rng.nextInt(20) == 0
                && ShulkerRules.canStayAt(attach, id, blockX(), blockY(), blockZ(), attachFace)) {
            peekRunning = true;
            // ShulkerPeekGoal.start: adjustedTickDelay(20·(1 + nextInt(3))) 뒤 raw 30.
            peekTime = (20 * (1 + rng.nextInt(3)) + 1) / 2;
            setRawPeek(ShulkerRules.PEEK_GOAL_RAW, events);
        }
        if (!randomLookRunning && RANDOM_LOOK_PRIORITY < lookLockPriority()
                && RANDOM_LOOK_PRIORITY < moveLockPriority() && rng.nextFloat() < 0.02f) {
            randomLookRunning = true;
            double angle = Math.PI * 2 * rng.nextDouble();
            randomLookX = Math.cos(angle);
            randomLookZ = Math.sin(angle);
            randomLookTime = 20 + rng.nextInt(20);
        }
        // 3) 돌고 있는 목표 전부를 한 번 돈다.
        if (lookAtRunning) tickLookAt(world);
        if (attackRunning) tickAttack(world, rng, events);
        if (peekRunning) peekTime--;
        if (randomLookRunning) tickRandomLook();
    }

    /** 홀수 MC 틱: 매 틱 갱신 목표(공격·무작위 시선)만 돈다. */
    private void tickRunningGoals(MobWorldView world, MobRandom rng, List<MobEvent> events) {
        if (attackRunning) tickAttack(world, rng, events);
        if (randomLookRunning) tickRandomLook();
    }

    private boolean lookAtCanUse(MobWorldView world, MobRandom rng) {
        if (rng.nextFloat() >= 0.02f) return false;
        // TargetingConditions.forNonCombat().range(8) + 시야. 가장 가까운 플레이어(눈높이 기준).
        PlayerSnapshot best = null;
        double bestSq = Double.MAX_VALUE;
        for (PlayerSnapshot p : world.players()) {
            if (!p.alive()) continue;
            double visibility = p.crouching() ? 0.8 : 1.0;
            double max = Math.max(8.0 * visibility, 2.0);
            if (distanceSqr(p.x(), p.y(), p.z()) > max * max) continue;
            if (!lineOfSight(world, new Resolved(p, null))) continue;
            double dx = p.x() - x, dy = p.y() - (y + eyeHeight()), dz = p.z() - z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestSq) { bestSq = d; best = p; }
        }
        lookAtNickname = best == null ? null : best.nickname();
        return best != null;
    }

    private boolean continueLookAt(MobWorldView world) {
        Resolved look = resolve(world, lookAtNickname, 0L);
        if (look == null || !look.alive()) return false;
        if (distanceSqr(look.x(), look.y(), look.z()) > 64.0) return false;
        return lookAtTime > 0;
    }

    private void stopLookAt() {
        lookAtRunning = false;
        lookAtNickname = null;
    }

    private void tickLookAt(MobWorldView world) {
        Resolved look = resolve(world, lookAtNickname, 0L);
        if (look != null && look.alive()) {
            // onlyHorizontal: 눈높이는 셜커 자신의 것.
            setLookAt(look.x(), y + eyeHeight(), look.z(), 10.0f);
            lookAtTime--;
        }
    }

    private boolean attackCanUse(MobWorldView world) {
        Resolved target = target(world);
        return target != null && target.alive();
    }

    private void stopAttack(List<MobEvent> events) {
        attackRunning = false;
        setRawPeek(0, events);
    }

    private void stopPeek(List<MobEvent> events) {
        peekRunning = false;
        if (!hasTarget()) setRawPeek(0, events);
    }

    private void tickAttack(MobWorldView world, MobRandom rng, List<MobEvent> events) {
        attackTimeMcTicks--;
        Resolved target = target(world);
        if (target == null) return;
        setLookAt(target.x(), target.eyeY(), target.z(), 180.0f);
        double distance = distanceSqr(target.x(), target.y(), target.z());
        if (distance < ShulkerRules.ATTACK_RANGE_SQR) {
            if (attackTimeMcTicks <= 0) {
                attackTimeMcTicks = ShulkerRules.nextAttackDelayMcTicks(rng);
                double[] box = authorityAabb();
                events.add(new MobEvent.ShootShulkerBullet(
                        (box[0] + box[3]) * 0.5, (box[1] + box[4]) * 0.5, (box[2] + box[5]) * 0.5,
                        targetNickname, targetMobId,
                        ShulkerBulletRules.axisOf(attachFace)));
                // playSound(SHULKER_SHOOT, 2.0, (r.nextFloat() - r.nextFloat())·0.2 + 1): 두 번 소비.
                rng.nextFloat();
                rng.nextFloat();
                events.add(new MobEvent.Sound("shoot"));
            }
        } else {
            setTarget(null, 0L);
        }
    }

    private void tickRandomLook() {
        randomLookTime--;
        setLookAt(x + randomLookX, y + eyeHeight(), z + randomLookZ, 10.0f);
    }

    private void setLookAt(double tx, double ty, double tz, float maxRot) {
        wantedX = tx;
        wantedY = ty;
        wantedZ = tz;
        yMaxRotSpeed = maxRot;
        lookAtCooldown = 2;
    }

    /** {@code LookControl.tick} + {@code ShulkerLookControl}(몸 회전은 언제나 0). */
    private void tickLookControl() {
        if (lookAtCooldown > 0) {
            lookAtCooldown--;
            double yawTarget = ShulkerRules.lookYawDegrees(attachFace,
                    wantedX - x, wantedY - (y + eyeHeight()), wantedZ - z);
            if (!Double.isNaN(yawTarget)) {
                headYawDegrees = rotateTowards(headYawDegrees, (float) yawTarget, yMaxRotSpeed);
            }
        } else {
            headYawDegrees = rotateTowards(headYawDegrees, 0.0f, 10.0f);
        }
    }

    /** {@code LookControl.rotateTowards}: {@code Mth.degreesDifference} 를 ±max 로 자른다. */
    static float rotateTowards(float from, float to, float max) {
        float difference = wrapDegrees(to - from);
        float clamped = Math.max(-max, Math.min(max, difference));
        return from + clamped;
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }
}
