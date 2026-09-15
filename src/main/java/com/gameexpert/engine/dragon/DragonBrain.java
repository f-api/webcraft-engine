package com.gameexpert.engine.dragon;

import com.gameexpert.engine.dragon.DragonWorld.DragonCrystal;
import com.gameexpert.engine.dragon.DragonWorld.DragonPlayer;
import com.gameexpert.engine.dragon.DragonWorld.DragonVictim;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [DRAGON] 바닐라 {@code net.minecraft.world.entity.boss.enderdragon.EnderDragon} 의 서버 쪽 한 틱과 11 개
 * 단계({@code phases/*})의 이식(핀 26.3 javap, 핀 경로 {@code scratchpad/dragon/cls}). 정적판
 * {@code client/src/backend/standalone/dragon/DragonBrain.ts} 와 줄 단위 사본이며, 두 권위는
 * {@link DragonWorld} 만 다르게 구현한다.
 *
 * <p>좌표·속도·각도는 바닐라 단위(블록, 블록/20TPS 틱, 도)다. 권위는 10 TPS 이므로 서버 틱마다
 * {@link #tick} 을 두 번 부른다. {@code noPhysics = true} 라 충돌이 없고 {@code horizontalCollision}·
 * {@code verticalCollision} 은 언제나 거짓이다.
 *
 * <p>WebCraft 차이(문서화): 바닐라 엔티티 난수 원천 대신 {@link DragonRandom}(시드 = 월드 시드 ^ 몹 id)을
 * 쓴다. 밀치기 피해의 {@code getLastHurtByMobTimestamp} 는 이 드래곤이 준 피해만 센다.
 */
public final class DragonBrain {
    public static final float MAX_HEALTH = 200.0F;
    /** {@code EnderDragon.SITTING_ALLOWED_DAMAGE_PERCENTAGE}. */
    public static final float SITTING_ALLOWED_DAMAGE_PERCENTAGE = 0.25F;
    /** {@code EnderDragonFight.DRAGON_SPAWN_Y}. */
    public static final int DRAGON_SPAWN_Y = 128;

    // ── 부위(EnderDragonPart): 이름·폭·높이 ──
    public static final int PART_HEAD = 0;
    public static final int PART_NECK = 1;
    public static final int PART_BODY = 2;
    public static final int PART_TAIL1 = 3; // 4·5 는 꼬리 2·3
    public static final int PART_WING1 = 6;
    public static final int PART_WING2 = 7;
    public static final float[] PART_WIDTH = {1.0F, 3.0F, 5.0F, 2.0F, 2.0F, 2.0F, 4.0F, 4.0F};
    public static final float[] PART_HEIGHT = {1.0F, 3.0F, 3.0F, 2.0F, 2.0F, 2.0F, 2.0F, 2.0F};

    /** 피해 종류(바닐라 DamageSource 중 드래곤이 가르는 사실만). */
    public enum DamageKind {
        /** 플레이어 근접·기타 직접 피해(투사체 아님, 폭발 아님). */
        MELEE,
        /** 화살·삼지창 등 AbstractArrow 직격. 앉은 단계에서는 튕겨 불붙는다. */
        ARROW,
        /** 돌풍구(WindCharge) 직격. 앉은 단계에서는 화살과 같다. */
        WIND_CHARGE,
        /** 폭발({@code DamageTypeTags.IS_EXPLOSION} = ALWAYS_HURTS_ENDER_DRAGONS). */
        EXPLOSION,
        /** 그 밖의 투사체(눈덩이 등). */
        OTHER_PROJECTILE,
        /** 마법·환경 등 플레이어가 원인이 아닌 피해. */
        OTHER
    }

    /** 피해 한 건의 원인. {@code playerCaused} 가 거짓이면 폭발만 드래곤을 다치게 한다. */
    public record DamageSource(DamageKind kind, boolean playerCaused, String playerNickname,
            Double sourceX, Double sourceZ, Double projectileMotionX, Double projectileMotionZ) {
        public static DamageSource player(DamageKind kind, String nickname, double x, double z) {
            return new DamageSource(kind, true, nickname, x, z, null, null);
        }
    }

    /** 피해 한 건의 결과(바닐라 {@code hurt(part, …)} 의 반환과 부수 사실). */
    public record HurtResult(boolean accepted, boolean healthChanged, boolean killingBlow,
            boolean projectileDeflected) {
        static final HurtResult REJECTED = new HurtResult(false, false, false, false);
    }

    // ── 몸 상태 ──
    public double x;
    public double y;
    public double z;
    public double vx;
    public double vy;
    public double vz;
    public float yRot;
    public float xRot;
    public float yRotA;
    private float health = MAX_HEALTH;
    public int dragonDeathTime;
    public float sittingDamageReceived;
    public boolean inWall;
    public int tickCount;
    /** {@code hurtTime}(피격 연출 10틱) — {@code wasHurtRecently} 가 읽는다. */
    int hurtTime;
    /** {@code damageCooldownTime}(피격 무적 20틱). */
    int damageCooldownTime;
    /** {@code lastHurt}. */
    float lastHurt;
    /** 치유 광선을 잇는 수정 몹 id(없으면 0). */
    public long nearestCrystalId;
    /** 사망 연출이 끝나 몹 원장에서 빠져야 한다. */
    public boolean removed;

    // ── 비행 기록(DragonFlightHistory: 64 칸 고리) ──
    private final double[] historyY = new double[64];
    private final float[] historyYRot = new float[64];
    private int historyHead = -1;

    // ── 부위 위치 ──
    public final double[] partX = new double[8];
    public final double[] partY = new double[8];
    public final double[] partZ = new double[8];

    private final DragonPath path = new DragonPath();
    final DragonRandom random;
    /** {@code getLastHurtByMobTimestamp} 대용: 이 드래곤이 마지막으로 다치게 한 개체별 {@code tickCount}. */
    private final Map<String, Integer> lastDragonHitTick = new HashMap<>();

    // ── 단계(EnderDragonPhaseManager) ──
    private final Phase[] phases = new Phase[DragonPhase.values().length];
    private Phase currentPhase;
    /** 마지막으로 방송한 단계 id(권위가 dragonState 차분 송신에 쓴다). */
    private DragonWorld world;

    public DragonBrain(long seed, double x, double y, double z, float yRot) {
        this.random = new DragonRandom(seed);
        this.x = x;
        this.y = y;
        this.z = z;
        this.yRot = yRot;
        for (int i = 0; i < 8; i++) {
            partX[i] = x;
            partY[i] = y;
            partZ[i] = z;
        }
        setPhase(DragonPhase.HOVERING);
    }

    // ═══════════════ 공개 조회 ═══════════════

    public float health() {
        return health;
    }

    public void setHealth(float value) {
        health = Math.max(0.0F, Math.min(MAX_HEALTH, value));
    }

    public boolean isDeadOrDying() {
        return health <= 0.0F;
    }

    public DragonPhase phase() {
        return currentPhase.phase();
    }

    public boolean isSitting() {
        return currentPhase.isSitting();
    }

    public DragonPath path() {
        return path;
    }

    public DragonRandom random() {
        return random;
    }

    /** 몸 AABB(16×8, 발밑 중심). */
    public double[] boundingBox() {
        return new double[] {x - 8.0, y, z - 8.0, x + 8.0, y + 8.0, z + 8.0};
    }

    /** 부위 발밑 중심 24 칸(부위마다 x, y, z; {@code dragonParts} 와이어). */
    public double[] partCenters() {
        double[] out = new double[24];
        for (int part = 0; part < 8; part++) {
            out[part * 3] = partX[part];
            out[part * 3 + 1] = partY[part];
            out[part * 3 + 2] = partZ[part];
        }
        return out;
    }

    /** 부위 AABB {@code (minX, minY, minZ, maxX, maxY, maxZ)}. */
    public double[] partBox(int part) {
        double half = PART_WIDTH[part] / 2.0F;
        return new double[] {partX[part] - half, partY[part], partZ[part] - half,
            partX[part] + half, partY[part] + PART_HEIGHT[part], partZ[part] + half};
    }

    // ═══════════════ 비행 기록 ═══════════════

    void recordHistory(double sampleY, float sampleYRot) {
        if (historyHead < 0) {
            java.util.Arrays.fill(historyY, sampleY);
            java.util.Arrays.fill(historyYRot, sampleYRot);
        }
        if (++historyHead == 64) historyHead = 0;
        historyY[historyHead] = sampleY;
        historyYRot[historyHead] = sampleYRot;
    }

    double historyY(int back) {
        return historyY[historyHead - back & 63];
    }

    float historyYRot(int back) {
        return historyYRot[historyHead - back & 63];
    }

    /** 영속 스냅샷용 비행 기록(가장 최근이 0). */
    public double[] historyYSnapshot() {
        double[] out = new double[64];
        for (int i = 0; i < 64; i++) out[i] = historyHead < 0 ? y : historyY(i);
        return out;
    }

    public float[] historyYRotSnapshot() {
        float[] out = new float[64];
        for (int i = 0; i < 64; i++) out[i] = historyHead < 0 ? yRot : historyYRot(i);
        return out;
    }

    public void restoreHistory(double[] ys, float[] yRots) {
        if (ys == null || yRots == null || ys.length != 64 || yRots.length != 64) return;
        for (int i = 0; i < 64; i++) {
            historyY[63 - i & 63] = ys[i];
            historyYRot[63 - i & 63] = yRots[i];
        }
        // 가장 최근 표본(ys[0])이 칸 63 에 오도록 머리를 둔다.
        historyHead = 63;
        for (int i = 0; i < 64; i++) {
            historyY[historyHead - i & 63] = ys[i];
            historyYRot[historyHead - i & 63] = yRots[i];
        }
    }

    // ═══════════════ 한 틱 ═══════════════

    /**
     * 바닐라 20 TPS 한 틱: {@code LivingEntity.baseTick} 의 피격 타이머 → 사망 중이면 {@code tickDeath} →
     * {@code aiStep}.
     */
    public void tick(DragonWorld world) {
        this.world = world;
        tickCount++;
        if (hurtTime > 0) hurtTime--;
        if (damageCooldownTime > 0) damageCooldownTime--;
        if (isDeadOrDying()) {
            tickDeath(world);
            return;
        }
        aiStep(world);
    }

    private void aiStep(DragonWorld world) {
        checkCrystals(world);
        yRot = DragonMath.wrapDegrees(yRot);
        recordHistory(y, yRot);
        Phase phase = currentPhase;
        phase.doServerTick(world);
        if (currentPhase != phase) {
            phase = currentPhase;
            phase.doServerTick(world);
        }
        double[] target = phase.getFlyTargetLocation();
        if (target != null) {
            double dx = target[0] - x;
            double dy = target[1] - y;
            double dz = target[2] - z;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            float speed = phase.getFlySpeed();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal > 0.0) dy = DragonMath.clamp(dy / horizontal, -speed, speed);
            vy = vy + dy * 0.01;
            yRot = DragonMath.wrapDegrees(yRot);
            double[] toTarget = normalize(target[0] - x, target[1] - y, target[2] - z);
            double[] forward = normalize(DragonMath.sin(yRot * DragonMath.DEG_TO_RAD), vy,
                    -DragonMath.cos(yRot * DragonMath.DEG_TO_RAD));
            float align = Math.max(((float) dot(forward, toTarget) + 0.5F) / 1.5F, 0.0F);
            if (Math.abs(dx) > 9.999999747378752E-6 || Math.abs(dz) > 9.999999747378752E-6) {
                float turn = DragonMath.clamp(DragonMath.wrapDegrees(
                        180.0F - (float) DragonMath.atan2(dx, dz) * DragonMath.RAD_TO_DEG - yRot), -50.0F, 50.0F);
                yRotA *= 0.8F;
                yRotA += turn * phase.getTurnSpeed();
                yRot = yRot + yRotA * 0.1F;
            }
            float closeness = (float) (2.0 / (distanceSq + 1.0));
            moveRelative(0.06F * (align * closeness + (1.0F - closeness)));
            if (inWall) {
                x += vx * 0.800000011920929;
                y += vy * 0.800000011920929;
                z += vz * 0.800000011920929;
            } else {
                x += vx;
                y += vy;
                z += vz;
            }
            double[] motion = normalize(vx, vy, vz);
            double drag = 0.8 + 0.15 * (dot(motion, forward) + 1.0) / 2.0;
            vx *= drag;
            vy *= 0.9100000262260437;
            vz *= drag;
        }
        double[] oldX = partX.clone();
        double[] oldY = partY.clone();
        double[] oldZ = partZ.clone();
        float pitchRad = (float) (historyY(5) - historyY(10)) * 10.0F * DragonMath.DEG_TO_RAD;
        float pitchCos = DragonMath.cos(pitchRad);
        float pitchSin = DragonMath.sin(pitchRad);
        float yawRad = yRot * DragonMath.DEG_TO_RAD;
        float yawSin = DragonMath.sin(yawRad);
        float yawCos = DragonMath.cos(yawRad);
        tickPart(PART_BODY, yawSin * 0.5F, 0.0, -yawCos * 0.5F);
        tickPart(PART_WING1, yawCos * 4.5F, 2.0, yawSin * 4.5F);
        tickPart(PART_WING2, yawCos * -4.5F, 2.0, yawSin * -4.5F);
        if (hurtTime <= 0) {
            double[] wing1 = partBox(PART_WING1);
            knockBack(world, world.livingEntitiesIn(wing1[0] - 4.0, wing1[1] - 2.0 - 2.0, wing1[2] - 4.0,
                    wing1[3] + 4.0, wing1[4] + 2.0 - 2.0, wing1[5] + 4.0));
            double[] wing2 = partBox(PART_WING2);
            knockBack(world, world.livingEntitiesIn(wing2[0] - 4.0, wing2[1] - 2.0 - 2.0, wing2[2] - 4.0,
                    wing2[3] + 4.0, wing2[4] + 2.0 - 2.0, wing2[5] + 4.0));
            double[] head = partBox(PART_HEAD);
            hurtEntities(world, world.livingEntitiesIn(head[0] - 1.0, head[1] - 1.0, head[2] - 1.0,
                    head[3] + 1.0, head[4] + 1.0, head[5] + 1.0));
            double[] neck = partBox(PART_NECK);
            hurtEntities(world, world.livingEntitiesIn(neck[0] - 1.0, neck[1] - 1.0, neck[2] - 1.0,
                    neck[3] + 1.0, neck[4] + 1.0, neck[5] + 1.0));
        }
        float headSin = DragonMath.sin(yRot * DragonMath.DEG_TO_RAD - yRotA * 0.01F);
        float headCos = DragonMath.cos(yRot * DragonMath.DEG_TO_RAD - yRotA * 0.01F);
        float headY = getHeadYOffset();
        tickPart(PART_HEAD, headSin * 6.5F * pitchCos, headY + pitchSin * 6.5F, -headCos * 6.5F * pitchCos);
        tickPart(PART_NECK, headSin * 5.5F * pitchCos, headY + pitchSin * 5.5F, -headCos * 5.5F * pitchCos);
        double sampleY = historyY(5);
        float sampleYRot = historyYRot(5);
        for (int i = 0; i < 3; i++) {
            int part = PART_TAIL1 + i;
            double tailY = historyY(12 + i * 2);
            float tailYRot = historyYRot(12 + i * 2);
            float angle = yRot * DragonMath.DEG_TO_RAD
                    + (float) DragonMath.wrapDegrees((double) (tailYRot - sampleYRot)) * DragonMath.DEG_TO_RAD;
            float tailSin = DragonMath.sin(angle);
            float tailCos = DragonMath.cos(angle);
            float distance = (i + 1) * 2.0F;
            tickPart(part, -(yawSin * 1.5F + tailSin * distance) * pitchCos,
                    tailY - sampleY - (double) ((distance + 1.5F) * pitchSin) + 1.5,
                    (yawCos * 1.5F + tailCos * distance) * pitchCos);
        }
        boolean walls = checkWalls(world, partBox(PART_HEAD));
        walls |= checkWalls(world, partBox(PART_NECK));
        walls |= checkWalls(world, partBox(PART_BODY));
        inWall = walls;
        // 부위의 xo/yo/zo 는 표현용이라 두뇌가 들고 있지 않는다(oldX 등은 보간용으로만 쓰였다).
        if (oldX.length != 8 || oldY.length != 8 || oldZ.length != 8) throw new IllegalStateException();
    }

    private void tickPart(int part, double dx, double dy, double dz) {
        partX[part] = x + dx;
        partY[part] = y + dy;
        partZ[part] = z + dz;
    }

    private float getHeadYOffset() {
        if (currentPhase.isSitting()) return -1.0F;
        return (float) (historyY(5) - historyY(0));
    }

    /** {@code Entity.moveRelative(amount, Vec3(0, 0, -1))}. */
    private void moveRelative(float amount) {
        double inputZ = -1.0 * amount;
        float sin = DragonMath.sin(yRot * DragonMath.DEG_TO_RAD);
        float cos = DragonMath.cos(yRot * DragonMath.DEG_TO_RAD);
        vx += 0.0 * cos - inputZ * sin;
        vz += inputZ * cos + 0.0 * sin;
    }

    private void checkCrystals(DragonWorld world) {
        if (nearestCrystalId != 0) {
            if (!world.crystalAlive(nearestCrystalId)) {
                nearestCrystalId = 0;
            } else if (tickCount % 10 == 0 && health < MAX_HEALTH) {
                setHealth(health + 1.0F);
            }
        }
        if (random.nextInt(10) == 0) {
            double[] box = boundingBox();
            List<DragonCrystal> crystals = world.crystalsNear(box[0] - 32.0, box[1] - 32.0, box[2] - 32.0,
                    box[3] + 32.0, box[4] + 32.0, box[5] + 32.0);
            DragonCrystal best = null;
            double bestDistance = Double.MAX_VALUE;
            for (DragonCrystal crystal : crystals) {
                double dx = crystal.x() - x;
                double dy = crystal.y() - y;
                double dz = crystal.z() - z;
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = crystal;
                }
            }
            nearestCrystalId = best == null ? 0 : best.mobId();
        }
    }

    private void knockBack(DragonWorld world, List<DragonVictim> victims) {
        double[] body = partBox(PART_BODY);
        double centerX = (body[0] + body[3]) / 2.0;
        double centerZ = (body[2] + body[5]) / 2.0;
        for (DragonVictim victim : victims) {
            double dx = victim.x() - centerX;
            double dz = victim.z() - centerZ;
            double distance = Math.max(dx * dx + dz * dz, 0.1);
            world.push(victim, dx / distance * 4.0, 0.20000000298023224, dz / distance * 4.0);
            if (!currentPhase.isSitting()
                    && lastDragonHitTick.getOrDefault(victim.key(), Integer.MIN_VALUE) < tickCount - 2) {
                lastDragonHitTick.put(victim.key(), tickCount);
                world.hurtByDragon(victim, 5.0F);
            }
        }
    }

    private void hurtEntities(DragonWorld world, List<DragonVictim> victims) {
        for (DragonVictim victim : victims) {
            lastDragonHitTick.put(victim.key(), tickCount);
            world.hurtByDragon(victim, 10.0F);
        }
    }

    private boolean checkWalls(DragonWorld world, double[] box) {
        int minX = DragonMath.floor(box[0]);
        int minY = DragonMath.floor(box[1]);
        int minZ = DragonMath.floor(box[2]);
        int maxX = DragonMath.floor(box[3]);
        int maxY = DragonMath.floor(box[4]);
        int maxZ = DragonMath.floor(box[5]);
        boolean blocked = false;
        boolean destroyed = false;
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    int block = world.block(bx, by, bz);
                    if (block <= 0 || DragonBlockRules.dragonTransparent(block)) continue;
                    if (!world.mobGriefing() || DragonBlockRules.dragonImmune(block)) {
                        blocked = true;
                    } else {
                        destroyed = world.removeBlock(bx, by, bz) || destroyed;
                    }
                }
            }
        }
        if (destroyed) {
            int ex = minX + random.nextInt(maxX - minX + 1);
            int ey = minY + random.nextInt(maxY - minY + 1);
            int ez = minZ + random.nextInt(maxZ - minZ + 1);
            world.levelEvent(DragonEvents.PARTICLES_DRAGON_BLOCK_BREAK, ex, ey, ez, 0, false);
        }
        return blocked;
    }

    // ═══════════════ 피해 ═══════════════

    /**
     * {@code EnderDragon.hurt(level, part, source, amount)}. 반환의 {@code accepted} 가 바닐라 반환값이다.
     * 사망 단계면 거부, 단계의 {@code onHurt}(앉음: 화살·돌풍구는 0 과 점화), 머리·목이 아니면
     * {@code amount/4 + min(amount, 1)}, 0.01 미만이면 거부. 원인이 플레이어이거나 폭발일 때만 실제로 깎는다.
     */
    public HurtResult hurt(int part, DamageSource source, float amount) {
        if (currentPhase.phase() == DragonPhase.DYING) return HurtResult.REJECTED;
        boolean deflected = false;
        if (currentPhase.isSitting()
                && (source.kind() == DamageKind.ARROW || source.kind() == DamageKind.WIND_CHARGE)) {
            amount = 0.0F;
            deflected = true;
        }
        if (part != PART_HEAD && part != PART_NECK) amount = amount / 4.0F + Math.min(amount, 1.0F);
        if (amount < 0.01F) return new HurtResult(false, false, false, deflected);
        boolean changed = false;
        boolean killingBlow = false;
        if (source.playerCaused() || source.kind() == DamageKind.EXPLOSION) {
            float before = health;
            int applied = reallyHurt(source, amount);
            changed = applied != 0;
            killingBlow = applied == 2;
            if (currentPhase.isSitting()) {
                sittingDamageReceived = sittingDamageReceived + before - health;
                if (sittingDamageReceived > SITTING_ALLOWED_DAMAGE_PERCENTAGE * MAX_HEALTH) {
                    sittingDamageReceived = 0.0F;
                    setPhase(DragonPhase.TAKEOFF);
                }
            }
        }
        return new HurtResult(true, changed, killingBlow, deflected);
    }

    /**
     * {@code LivingEntity.hurtServer} 의 드래곤 부분: 피격 무적(20틱, 10틱 넘게 남았으면 더 큰 피해의 차만),
     * 피해 적용, 기본 넉백({@code dealDefaultKnockback} 0.4), 치명타면 {@code die → handleKillingBlow}.
     *
     * @return 0 거부 · 1 적용 · 2 적용 + 치명타
     */
    private int reallyHurt(DamageSource source, float amount) {
        if (isDeadOrDying()) return 0;
        boolean fresh = true;
        if ((float) damageCooldownTime > 10.0F) {
            if (amount <= lastHurt) return 0;
            actuallyHurt(amount - lastHurt);
            lastHurt = amount;
            fresh = false;
        } else {
            lastHurt = amount;
            damageCooldownTime = 20;
            actuallyHurt(amount);
            hurtTime = 10;
        }
        if (fresh) dealDefaultKnockback(source);
        if (isDeadOrDying()) {
            handleKillingBlow();
            return 2;
        }
        return 1;
    }

    private void actuallyHurt(float amount) {
        setHealth(health - amount);
    }

    private void dealDefaultKnockback(DamageSource source) {
        double dx = 0.0;
        double dz = 0.0;
        if (source.projectileMotionX() != null && source.projectileMotionZ() != null) {
            dx = -source.projectileMotionX();
            dz = -source.projectileMotionZ();
        } else if (source.sourceX() != null && source.sourceZ() != null) {
            dx = source.sourceX() - x;
            dz = source.sourceZ() - z;
        } else {
            return;
        }
        knockback(0.4000000059604645, dx, dz);
    }

    /** {@code EnderDragon.knockback}: 앉아 있으면 무시, 아니면 {@code LivingEntity.knockback}(저항 0). */
    public void knockback(double strength, double dx, double dz) {
        if (currentPhase.isSitting()) return;
        if (strength <= 0.0) return;
        while (dx * dx + dz * dz < 9.999999747378752E-6) {
            dx = (random.nextDouble() - random.nextDouble()) * 0.01;
            dz = (random.nextDouble() - random.nextDouble()) * 0.01;
        }
        double[] direction = normalize(dx, 0.0, dz);
        double pushX = direction[0] * strength;
        double pushZ = direction[2] * strength;
        vx = vx / 2.0 - pushX;
        vz = vz / 2.0 - pushZ;
    }

    /** {@code EnderDragon.handleKillingBlow}: 앉아 있지 않으면 체력 1 로 버티고 DYING 으로 날아간다. */
    private void handleKillingBlow() {
        if (!currentPhase.isSitting()) {
            setHealth(1.0F);
            setPhase(DragonPhase.DYING);
        }
    }

    /**
     * {@code EnderDragon.onCrystalDestroyed}: 부순 플레이어(없으면 수정 칸에 가장 가까운 공격 가능 플레이어)를
     * 기억하고, 그 수정이 치유 광선 수정이면 머리에 폭발 피해 10 을 준 뒤 단계에 알린다.
     */
    public void onCrystalDestroyed(DragonWorld world, long crystalId, double crystalX, double crystalY,
            double crystalZ, int blockX, int blockY, int blockZ, String destroyerNickname) {
        this.world = world;
        DragonPlayer player = null;
        if (destroyerNickname != null) {
            for (DragonPlayer candidate : world.players()) {
                if (candidate.nickname().equals(destroyerNickname)) {
                    player = candidate;
                    break;
                }
            }
        }
        if (player == null) player = nearestAttackable(world.players(), blockX, blockY, blockZ, -1.0, null);
        if (crystalId == nearestCrystalId) {
            hurt(PART_HEAD, new DamageSource(DamageKind.EXPLOSION, player != null,
                    player == null ? null : player.nickname(), crystalX, crystalZ, null, null), 10.0F);
        }
        currentPhase.onCrystalDestroyed(player);
    }

    // ═══════════════ 사망 ═══════════════

    private void tickDeath(DragonWorld world) {
        dragonDeathTime++;
        if (dragonDeathTime >= 180 && dragonDeathTime <= 200) {
            // 바닐라 EXPLOSION_EMITTER 입자는 클라가 dragonState.deathTicks 로 그린다(난수는 소비한다).
            random.nextFloat();
            random.nextFloat();
            random.nextFloat();
        }
        int xp = world.aliveCrystals() == -1 || world.previouslyKilledDragon() ? 500 : 12000;
        if (dragonDeathTime > 150 && dragonDeathTime % 5 == 0) {
            world.awardExperience(x, y, z, DragonMath.floor((float) xp * 0.08F));
        }
        if (dragonDeathTime == 1) {
            world.levelEvent(DragonEvents.SOUND_DRAGON_DEATH, DragonMath.floor(x), DragonMath.floor(y),
                    DragonMath.floor(z), 0, true);
        }
        y += 0.10000000149011612;
        for (int i = 0; i < 8; i++) partY[i] += 0.10000000149011612;
        if (dragonDeathTime >= 200) {
            world.awardExperience(x, y, z, DragonMath.floor((float) xp * 0.2F));
            removed = true;
            world.dragonKilled();
        }
    }

    // ═══════════════ 단계 관리 ═══════════════

    public void setPhase(DragonPhase phase) {
        if (currentPhase != null && phase == currentPhase.phase()) return;
        if (currentPhase != null) currentPhase.end();
        currentPhase = phaseInstance(phase);
        currentPhase.begin();
    }

    /** 영속 복원: 단계만 되살린다(바닐라 {@code DragonPhase} 저장과 같다 — 단계 내부 상태는 begin 으로 초기화). */
    public void restorePhase(DragonPhase phase) {
        setPhase(phase);
    }

    private Phase phaseInstance(DragonPhase phase) {
        int id = phase.id();
        Phase instance = phases[id];
        if (instance == null) {
            instance = switch (phase) {
                case HOLDING_PATTERN -> new HoldingPattern();
                case STRAFE_PLAYER -> new StrafePlayer();
                case LANDING_APPROACH -> new LandingApproach();
                case LANDING -> new Landing();
                case TAKEOFF -> new Takeoff();
                case SITTING_FLAMING -> new SittingFlaming();
                case SITTING_SCANNING -> new SittingScanning();
                case SITTING_ATTACKING -> new SittingAttacking();
                case CHARGING_PLAYER -> new ChargingPlayer();
                case DYING -> new Dying();
                case HOVERING -> new Hover();
            };
            phases[id] = instance;
        }
        return instance;
    }

    private int aliveCrystals() {
        return world == null ? -1 : world.aliveCrystals();
    }

    private int findClosestNode() {
        if (!path.built()) path.build(world);
        return path.findClosestNode(x, y, z, aliveCrystals());
    }

    private int findClosestNode(double tx, double ty, double tz) {
        if (!path.built()) path.build(world);
        return path.findClosestNode(tx, ty, tz, aliveCrystals());
    }

    private DragonPath.Path findPath(int from, int to, DragonPath.Node finalNode) {
        return path.findPath(from, to, finalNode, aliveCrystals());
    }

    /** 귀환 포털 위치 {@code EnderDragonFight.getPodiumLocation(origin)} = (0, 0, 0). */
    private int[] podiumTop(boolean motionBlocking) {
        int height = motionBlocking ? world.heightMotionBlocking(0, 0) : world.heightNoLeaves(0, 0);
        return new int[] {0, height, 0};
    }

    /** {@code getHeadLookVector(1)}. */
    double[] headLookVector() {
        DragonPhase phase = currentPhase.phase();
        float pitch;
        if (phase == DragonPhase.LANDING || phase == DragonPhase.TAKEOFF) {
            int[] podium = podiumTop(false);
            double cx = podium[0] + 0.5 - x;
            double cy = podium[1] + 0.5 - y;
            double cz = podium[2] + 0.5 - z;
            float scale = Math.max((float) Math.sqrt(cx * cx + cy * cy + cz * cz) / 4.0F, 1.0F);
            float angle = 6.0F / scale;
            pitch = -angle * 1.5F * 5.0F;
        } else if (currentPhase.isSitting()) {
            pitch = -45.0F;
        } else {
            pitch = xRot;
        }
        return viewVector(pitch, yRot);
    }

    static double[] viewVector(float pitch, float yaw) {
        float pitchRad = pitch * DragonMath.DEG_TO_RAD;
        float yawRad = -yaw * DragonMath.DEG_TO_RAD;
        float yawCos = DragonMath.cos(yawRad);
        float yawSin = DragonMath.sin(yawRad);
        float pitchCos = DragonMath.cos(pitchRad);
        float pitchSin = DragonMath.sin(pitchRad);
        return new double[] {yawSin * pitchCos, -pitchSin, yawCos * pitchCos};
    }

    /**
     * {@code ServerLevel.getNearestPlayer(TargetingConditions, source, x, y, z)}: 공격 가능하고(생존 모드·
     * 살아 있음) 범위 안(웅크리면 ×0.8, 최소 2)이며 시야 조건을 만족하는 가장 가까운 플레이어. {@code range}
     * 가 음수이면 범위를 보지 않는다. {@code selector} 가 있으면 먼저 거른다.
     */
    DragonPlayer nearestAttackable(List<DragonPlayer> players, double px, double py, double pz,
            double range, java.util.function.Predicate<DragonPlayer> selector) {
        return nearestAttackable(players, px, py, pz, range, selector, false);
    }

    private DragonPlayer nearestAttackable(List<DragonPlayer> players, double px, double py, double pz,
            double range, java.util.function.Predicate<DragonPlayer> selector, boolean lineOfSight) {
        DragonPlayer best = null;
        double bestDistance = -1.0;
        for (DragonPlayer player : players) {
            if (!player.attackable()) continue;
            if (selector != null && !selector.test(player)) continue;
            if (range > 0.0) {
                double visibility = player.crouching() ? 0.8 : 1.0;
                double limit = Math.max(range * visibility, 2.0);
                double sx = x - player.x();
                double sy = y - player.y();
                double sz = z - player.z();
                if (sx * sx + sy * sy + sz * sz > limit * limit) continue;
            }
            if (lineOfSight && !world.lineOfSight(x, y + 6.8, z, player.x(), player.eyeY(), player.z())) {
                continue;
            }
            double dx = player.x() - px;
            double dy = player.y() - py;
            double dz = player.z() - pz;
            double distance = dx * dx + dy * dy + dz * dz;
            if (bestDistance < 0.0 || distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private static double[] normalize(double ax, double ay, double az) {
        double length = Math.sqrt(ax * ax + ay * ay + az * az);
        if (length < 9.999999747378752E-6) return new double[] {0.0, 0.0, 0.0};
        return new double[] {ax / length, ay / length, az / length};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /** {@code navigateToNextPathNode} 공통부: 다음 노드 x/z, y 는 노드 y 이상이 될 때까지 +20·rand. */
    private double[] nextNodeTarget(DragonPath.Path current, boolean advanceFirst) {
        if (current == null) return null;
        if (advanceFirst) current.advance();
        if (current.isDone()) return null;
        DragonPath.Node node = current.nextNode();
        current.advance();
        double targetY;
        do {
            targetY = (float) node.y + random.nextFloat() * 20.0F;
        } while (targetY < node.y);
        return new double[] {node.x, targetY, node.z};
    }

    // ═══════════════ 단계 인스턴스 ═══════════════

    /** {@code DragonPhaseInstance}. */
    abstract class Phase {
        abstract DragonPhase phase();

        boolean isSitting() {
            return false;
        }

        void doServerTick(DragonWorld world) {
        }

        void onCrystalDestroyed(DragonPlayer player) {
        }

        void begin() {
        }

        void end() {
        }

        float getFlySpeed() {
            return 0.6F;
        }

        double[] getFlyTargetLocation() {
            return null;
        }

        float getTurnSpeed() {
            float horizontal = (float) Math.sqrt(vx * vx + vz * vz) + 1.0F;
            float limited = Math.min(horizontal, 40.0F);
            return 0.7F / limited / horizontal;
        }
    }

    abstract class SittingPhase extends Phase {
        @Override
        boolean isSitting() {
            return true;
        }
    }

    /** {@code DragonHoldingPatternPhase}. */
    final class HoldingPattern extends Phase {
        DragonPath.Path currentPath;
        double[] targetLocation;
        boolean clockwise;

        @Override DragonPhase phase() { return DragonPhase.HOLDING_PATTERN; }

        @Override
        void doServerTick(DragonWorld world) {
            double distance = targetLocation == null ? 0.0 : distanceTo(targetLocation);
            if (distance < 100.0 || distance > 22500.0) findNewTarget(world);
        }

        @Override
        void begin() {
            currentPath = null;
            targetLocation = null;
        }

        @Override double[] getFlyTargetLocation() { return targetLocation; }

        private void findNewTarget(DragonWorld world) {
            if (currentPath != null && currentPath.isDone()) {
                int[] podium = podiumTop(false);
                int crystals = Math.max(0, aliveCrystalsOrZero());
                if (random.nextInt(crystals + 3) == 0) {
                    setPhase(DragonPhase.LANDING_APPROACH);
                    return;
                }
                DragonPlayer player = nearestAttackable(world.players(), podium[0], podium[1], podium[2],
                        -1.0, null);
                double chance;
                if (player != null) {
                    double cx = podium[0] + 0.5 - player.x();
                    double cy = podium[1] + 0.5 - player.y();
                    double cz = podium[2] + 0.5 - player.z();
                    chance = (cx * cx + cy * cy + cz * cz) / 512.0;
                } else {
                    chance = 64.0;
                }
                if (player != null && (random.nextInt((int) (chance + 2.0)) == 0
                        || random.nextInt(crystals + 2) == 0)) {
                    strafePlayer(player);
                    return;
                }
            }
            if (currentPath == null || currentPath.isDone()) {
                int from = findClosestNode();
                int to = from;
                if (random.nextInt(8) == 0) {
                    clockwise = !clockwise;
                    to += 6;
                }
                if (clockwise) to++;
                else to--;
                if (aliveCrystals() < 0) {
                    to -= 12;
                    to &= 7;
                    to += 12;
                } else {
                    to %= 12;
                    if (to < 0) to += 12;
                }
                currentPath = findPath(from, to, null);
                if (currentPath != null) currentPath.advance();
            }
            navigateToNextPathNode();
        }

        private void strafePlayer(DragonPlayer player) {
            setPhase(DragonPhase.STRAFE_PLAYER);
            ((StrafePlayer) phaseInstance(DragonPhase.STRAFE_PLAYER)).setTarget(player);
        }

        private void navigateToNextPathNode() {
            double[] next = nextNodeTarget(currentPath, false);
            if (next != null) targetLocation = next;
        }

        @Override
        void onCrystalDestroyed(DragonPlayer player) {
            if (player != null && player.attackable()) strafePlayer(player);
        }
    }

    private int aliveCrystalsOrZero() {
        int alive = aliveCrystals();
        return alive < 0 ? 0 : alive;
    }

    private double distanceTo(double[] target) {
        double dx = target[0] - x;
        double dy = target[1] - y;
        double dz = target[2] - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** {@code DragonStrafePlayerPhase}. */
    final class StrafePlayer extends Phase {
        int fireballCharge;
        DragonPath.Path currentPath;
        double[] targetLocation;
        /** 표적 닉네임(바닐라 attackTarget; 매 틱 세계에서 다시 찾는다). */
        String attackTarget;
        boolean holdingPatternClockwise;

        @Override DragonPhase phase() { return DragonPhase.STRAFE_PLAYER; }

        private DragonPlayer target(DragonWorld world) {
            if (attackTarget == null) return null;
            for (DragonPlayer player : world.players()) {
                if (player.nickname().equals(attackTarget)) return player;
            }
            return null;
        }

        @Override
        void doServerTick(DragonWorld world) {
            DragonPlayer target = target(world);
            if (target == null) {
                setPhase(DragonPhase.HOLDING_PATTERN);
                return;
            }
            if (currentPath != null && currentPath.isDone()) {
                double tx = target.x();
                double tz = target.z();
                double dx = tx - x;
                double dz = tz - z;
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                double lift = Math.min(0.4000000059604645 + horizontal / 80.0 - 1.0, 10.0);
                targetLocation = new double[] {tx, target.y() + lift, tz};
            }
            double distance = targetLocation == null ? 0.0 : distanceTo(targetLocation);
            if (distance < 100.0 || distance > 22500.0) findNewTarget();
            double tdx = target.x() - x;
            double tdy = target.y() - y;
            double tdz = target.z() - z;
            if (tdx * tdx + tdy * tdy + tdz * tdz < 4096.0) {
                if (world.lineOfSight(x, y + 6.8, z, target.x(), target.eyeY(), target.z())) {
                    fireballCharge++;
                    double[] toTarget = normalize(target.x() - x, 0.0, target.z() - z);
                    double[] forward = normalize(DragonMath.sin(yRot * DragonMath.DEG_TO_RAD), 0.0,
                            -DragonMath.cos(yRot * DragonMath.DEG_TO_RAD));
                    float dot = (float) dot(forward, toTarget);
                    float angle = (float) (Math.acos(dot) * 57.2957763671875);
                    angle += 0.5F;
                    if (fireballCharge >= 5 && angle >= 0.0F && angle < 10.0F) {
                        double[] view = viewVector(xRot, yRot);
                        double fx = partX[PART_HEAD] - view[0] * 1.0;
                        double fy = partY[PART_HEAD] + PART_HEIGHT[PART_HEAD] * 0.5 + 0.5;
                        double fz = partZ[PART_HEAD] - view[2] * 1.0;
                        double ax = target.x() - fx;
                        double ay = target.y() + 1.8 * 0.5 - fy;
                        double az = target.z() - fz;
                        world.levelEvent(DragonEvents.SOUND_DRAGON_FIREBALL, DragonMath.floor(x),
                                DragonMath.floor(y), DragonMath.floor(z), 0, false);
                        double[] direction = normalize(ax, ay, az);
                        world.spawnFireball(fx, fy, fz, direction[0], direction[1], direction[2]);
                        fireballCharge = 0;
                        if (currentPath != null) {
                            while (!currentPath.isDone()) currentPath.advance();
                        }
                        setPhase(DragonPhase.HOLDING_PATTERN);
                    }
                } else if (fireballCharge > 0) {
                    fireballCharge--;
                }
            } else if (fireballCharge > 0) {
                fireballCharge--;
            }
        }

        private void findNewTarget() {
            if (currentPath == null || currentPath.isDone()) {
                int from = findClosestNode();
                int to = from;
                if (random.nextInt(8) == 0) {
                    holdingPatternClockwise = !holdingPatternClockwise;
                    to += 6;
                }
                if (holdingPatternClockwise) to++;
                else to--;
                if (aliveCrystals() <= 0) {
                    to -= 12;
                    to &= 7;
                    to += 12;
                } else {
                    to %= 12;
                    if (to < 0) to += 12;
                }
                currentPath = findPath(from, to, null);
                if (currentPath != null) currentPath.advance();
            }
            navigateToNextPathNode();
        }

        private void navigateToNextPathNode() {
            double[] next = nextNodeTarget(currentPath, false);
            if (next != null) targetLocation = next;
        }

        @Override
        void begin() {
            fireballCharge = 0;
            targetLocation = null;
            currentPath = null;
            attackTarget = null;
        }

        void setTarget(DragonPlayer player) {
            attackTarget = player.nickname();
            int from = findClosestNode();
            int to = findClosestNode(player.x(), player.y(), player.z());
            int blockX = DragonMath.floor(player.x());
            int blockZ = DragonMath.floor(player.z());
            double dx = blockX - x;
            double dz = blockZ - z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            double lift = Math.min(0.4000000059604645 + horizontal / 80.0 - 1.0, 10.0);
            int blockY = DragonMath.floor(player.y() + lift);
            DragonPath.Node finalNode = new DragonPath.Node(blockX, blockY, blockZ);
            currentPath = findPath(from, to, finalNode);
            if (currentPath != null) {
                currentPath.advance();
                navigateToNextPathNode();
            }
        }

        @Override double[] getFlyTargetLocation() { return targetLocation; }
    }

    /** {@code DragonLandingApproachPhase}. */
    final class LandingApproach extends Phase {
        DragonPath.Path currentPath;
        double[] targetLocation;

        @Override DragonPhase phase() { return DragonPhase.LANDING_APPROACH; }

        @Override
        void begin() {
            currentPath = null;
            targetLocation = null;
        }

        @Override
        void doServerTick(DragonWorld world) {
            double distance = targetLocation == null ? 0.0 : distanceTo(targetLocation);
            if (distance < 100.0 || distance > 22500.0) findNewTarget(world);
        }

        @Override double[] getFlyTargetLocation() { return targetLocation; }

        private void findNewTarget(DragonWorld world) {
            if (currentPath == null || currentPath.isDone()) {
                int from = findClosestNode();
                int[] podium = podiumTop(false);
                DragonPlayer player = nearestAttackable(world.players(), podium[0], podium[1], podium[2],
                        -1.0, null);
                int to;
                if (player != null) {
                    double[] direction = normalize(player.x(), 0.0, player.z());
                    to = findClosestNode(-direction[0] * 40.0, 105.0, -direction[2] * 40.0);
                } else {
                    to = findClosestNode(40.0, podium[1], 0.0);
                }
                DragonPath.Node finalNode = new DragonPath.Node(podium[0], podium[1], podium[2]);
                currentPath = findPath(from, to, finalNode);
                if (currentPath != null) currentPath.advance();
            }
            double[] next = nextNodeTarget(currentPath, false);
            if (next != null) targetLocation = next;
            if (currentPath != null && currentPath.isDone()) setPhase(DragonPhase.LANDING);
        }
    }

    /** {@code DragonLandingPhase}. */
    final class Landing extends Phase {
        double[] targetLocation;

        @Override DragonPhase phase() { return DragonPhase.LANDING; }

        @Override
        void doServerTick(DragonWorld world) {
            if (targetLocation == null) {
                int[] podium = podiumTop(false);
                targetLocation = new double[] {podium[0] + 0.5, podium[1], podium[2] + 0.5};
            }
            if (distanceTo(targetLocation) < 1.0) {
                ((SittingFlaming) phaseInstance(DragonPhase.SITTING_FLAMING)).resetFlameCount();
                setPhase(DragonPhase.SITTING_SCANNING);
            }
        }

        @Override float getFlySpeed() { return 1.5F; }

        @Override
        float getTurnSpeed() {
            float horizontal = (float) Math.sqrt(vx * vx + vz * vz) + 1.0F;
            float limited = Math.min(horizontal, 40.0F);
            return limited / horizontal;
        }

        @Override
        void begin() {
            targetLocation = null;
        }

        @Override double[] getFlyTargetLocation() { return targetLocation; }
    }

    /** {@code DragonTakeoffPhase}. */
    final class Takeoff extends Phase {
        boolean firstTick;
        DragonPath.Path currentPath;
        double[] targetLocation;

        @Override DragonPhase phase() { return DragonPhase.TAKEOFF; }

        @Override
        void doServerTick(DragonWorld world) {
            if (firstTick || currentPath == null) {
                firstTick = false;
                findNewTarget();
            } else {
                int[] podium = podiumTop(false);
                double cx = podium[0] + 0.5 - x;
                double cy = podium[1] + 0.5 - y;
                double cz = podium[2] + 0.5 - z;
                if (!(cx * cx + cy * cy + cz * cz < 100.0)) setPhase(DragonPhase.HOLDING_PATTERN);
            }
        }

        @Override
        void begin() {
            firstTick = true;
            currentPath = null;
            targetLocation = null;
        }

        private void findNewTarget() {
            int from = findClosestNode();
            double[] look = headLookVector();
            int to = findClosestNode(-look[0] * 40.0, 105.0, -look[2] * 40.0);
            if (aliveCrystals() <= 0) {
                to -= 12;
                to &= 7;
                to += 12;
            } else {
                to %= 12;
                if (to < 0) to += 12;
            }
            currentPath = findPath(from, to, null);
            double[] next = nextNodeTarget(currentPath, true);
            if (next != null) targetLocation = next;
        }

        @Override double[] getFlyTargetLocation() { return targetLocation; }
    }

    /** {@code DragonSittingFlamingPhase}. */
    final class SittingFlaming extends SittingPhase {
        int flameTicks;
        int flameCount;
        long flameCloud;

        @Override DragonPhase phase() { return DragonPhase.SITTING_FLAMING; }

        @Override
        void doServerTick(DragonWorld world) {
            flameTicks++;
            if (flameTicks >= 200) {
                if (flameCount >= 4) setPhase(DragonPhase.TAKEOFF);
                else setPhase(DragonPhase.SITTING_SCANNING);
            } else if (flameTicks == 10) {
                double[] direction = normalize(partX[PART_HEAD] - x, 0.0, partZ[PART_HEAD] - z);
                double fx = partX[PART_HEAD] + direction[0] * 5.0 / 2.0;
                double fz = partZ[PART_HEAD] + direction[2] * 5.0 / 2.0;
                double headY = partY[PART_HEAD] + PART_HEIGHT[PART_HEAD] * 0.5;
                double fy = headY;
                while (world.block(DragonMath.floor(fx), DragonMath.floor(fy), DragonMath.floor(fz)) == 0) {
                    fy -= 1.0;
                    if (fy < 0.0) {
                        fy = headY;
                        break;
                    }
                }
                fy = DragonMath.floor(fy) + 1;
                flameCloud = world.spawnSittingFlame(fx, fy, fz);
            }
        }

        @Override
        void begin() {
            flameTicks = 0;
            flameCount++;
        }

        @Override
        void end() {
            if (flameCloud != 0 && world != null) {
                world.discardCloud(flameCloud);
            }
            flameCloud = 0;
        }

        void resetFlameCount() {
            flameCount = 0;
        }
    }

    /** {@code DragonSittingScanningPhase}. */
    final class SittingScanning extends SittingPhase {
        int scanningTime;

        @Override DragonPhase phase() { return DragonPhase.SITTING_SCANNING; }

        @Override
        void doServerTick(DragonWorld world) {
            scanningTime++;
            DragonPlayer player = nearestAttackable(world.players(), x, y, z, 20.0,
                    candidate -> Math.abs(candidate.y() - y) <= 10.0, true);
            if (player != null) {
                if (scanningTime > 25) {
                    setPhase(DragonPhase.SITTING_ATTACKING);
                } else {
                    double[] toTarget = normalize(player.x() - x, 0.0, player.z() - z);
                    double[] forward = normalize(DragonMath.sin(yRot * DragonMath.DEG_TO_RAD), 0.0,
                            -DragonMath.cos(yRot * DragonMath.DEG_TO_RAD));
                    float dot = (float) dot(forward, toTarget);
                    float angle = (float) (Math.acos(dot) * 57.2957763671875) + 0.5F;
                    if (angle < 0.0F || angle > 10.0F) {
                        double dx = player.x() - partX[PART_HEAD];
                        double dz = player.z() - partZ[PART_HEAD];
                        double turn = DragonMath.clamp(DragonMath.wrapDegrees(
                                180.0 - DragonMath.atan2(dx, dz) * 57.2957763671875 - (double) yRot), -100.0, 100.0);
                        yRotA *= 0.8F;
                        float horizontal = (float) Math.sqrt(dx * dx + dz * dz) + 1.0F;
                        float unclamped = horizontal;
                        if (horizontal > 40.0F) horizontal = 40.0F;
                        yRotA += (float) turn * (0.7F / horizontal / unclamped);
                        yRot = yRot + yRotA;
                    }
                }
            } else if (scanningTime >= 100) {
                DragonPlayer chargeTarget = nearestAttackable(world.players(), x, y, z, 150.0, null, true);
                setPhase(DragonPhase.TAKEOFF);
                if (chargeTarget != null) {
                    setPhase(DragonPhase.CHARGING_PLAYER);
                    ((ChargingPlayer) phaseInstance(DragonPhase.CHARGING_PLAYER)).setTarget(
                            new double[] {chargeTarget.x(), chargeTarget.y(), chargeTarget.z()});
                }
            }
        }

        @Override
        void begin() {
            scanningTime = 0;
        }
    }

    /** {@code DragonSittingAttackingPhase}. */
    final class SittingAttacking extends SittingPhase {
        int attackingTicks;

        @Override DragonPhase phase() { return DragonPhase.SITTING_ATTACKING; }

        @Override
        void doServerTick(DragonWorld world) {
            if (attackingTicks++ >= 40) setPhase(DragonPhase.SITTING_FLAMING);
        }

        @Override
        void begin() {
            attackingTicks = 0;
        }
    }

    /** {@code DragonChargePlayerPhase}. */
    final class ChargingPlayer extends Phase {
        double[] targetLocation;
        int timeSinceCharge;

        @Override DragonPhase phase() { return DragonPhase.CHARGING_PLAYER; }

        @Override
        void doServerTick(DragonWorld world) {
            if (targetLocation == null) {
                setPhase(DragonPhase.HOLDING_PATTERN);
                return;
            }
            if (timeSinceCharge > 0 && timeSinceCharge++ >= 10) {
                setPhase(DragonPhase.HOLDING_PATTERN);
                return;
            }
            double distance = distanceTo(targetLocation);
            if (distance < 100.0 || distance > 22500.0) timeSinceCharge++;
        }

        @Override
        void begin() {
            targetLocation = null;
            timeSinceCharge = 0;
        }

        void setTarget(double[] target) {
            targetLocation = target;
        }

        @Override float getFlySpeed() { return 3.0F; }

        @Override double[] getFlyTargetLocation() { return targetLocation; }
    }

    /** {@code DragonDeathPhase}. */
    final class Dying extends Phase {
        double[] targetLocation;
        int time;

        @Override DragonPhase phase() { return DragonPhase.DYING; }

        @Override
        void doServerTick(DragonWorld world) {
            time++;
            if (targetLocation == null) {
                int[] podium = podiumTop(true);
                targetLocation = new double[] {podium[0] + 0.5, podium[1], podium[2] + 0.5};
            }
            double distance = distanceTo(targetLocation);
            if (distance < 100.0 || distance > 22500.0) setHealth(0.0F);
            else setHealth(1.0F);
        }

        @Override
        void begin() {
            targetLocation = null;
            time = 0;
        }

        @Override float getFlySpeed() { return 3.0F; }

        @Override double[] getFlyTargetLocation() { return targetLocation; }
    }

    /** {@code DragonHoverPhase}. */
    final class Hover extends Phase {
        double[] targetLocation;

        @Override DragonPhase phase() { return DragonPhase.HOVERING; }

        @Override
        void doServerTick(DragonWorld world) {
            if (targetLocation == null) targetLocation = new double[] {x, y, z};
        }

        @Override boolean isSitting() { return true; }

        @Override
        void begin() {
            targetLocation = null;
        }

        @Override float getFlySpeed() { return 1.0F; }

        @Override double[] getFlyTargetLocation() { return targetLocation; }
    }
}
