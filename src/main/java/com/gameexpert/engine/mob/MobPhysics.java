package com.gameexpert.engine.mob;

import java.util.Arrays;
import java.util.List;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.engine.BuildingBlockRules;
import com.gameexpert.engine.Fluids;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 몹 공통 물리: 중력 + 축분리 AABB 복셀 충돌 + 1블록 자동 등반(+ 거미 벽 등반).
 * CONTRACT §11.1 의 플레이어 이동 로직(축분리 x→z→y, 서브스텝, 면 스냅)을 몹용으로 간소화했다.
 *
 * 단위는 "블록/틱"(틱 이산 시뮬레이션). 수평 이동량은 몹 최대 속도(<1블록/틱)라 한 번에 해소하고,
 * 낙하는 최대 종단속도(6블록/틱)라 서브스텝(≤0.2)으로 잘라 터널링을 막는다.
 *
 * position 은 발밑 중심(x,z=중심, y=AABB 바닥).
 */
public final class MobPhysics {
    private MobPhysics() {}

    /** 중력(블록/틱): §11.2 GRAVITY 28블록/s² 를 10TPS 로 환산(28*0.1*0.1). */
    public static final double GRAVITY = 0.28;
    /** 낙하 종단속도(블록/틱): §11.2 60블록/s * 0.1. */
    public static final double TERMINAL = 6.0;
    /** [TRIAL-GAP] 느린 낙하 중력 배율: 바닐라 0.01 / 0.08. */
    public static final double SLOW_FALLING_GRAVITY_SCALE = 0.125;
    /** [TRIAL-GAP] 느린 낙하 종단 속도(권위 틱당): 0.01 × 0.98 / 0.02 = 0.49 블록/MC 틱 × 2. */
    public static final double SLOW_FALLING_TERMINAL = 0.98;
    /** 거미 벽 등반 상승 속도(블록/틱). */
    public static final double CLIMB_SPEED = 0.2;
    /** 비행 중 남기는 직전 수직 속도 비율. 입력이 없을 때 완만하게 활공한다. */
    public static final double FLY_VERTICAL_DRAG = 0.8;
    /** 비행 중 적용하는 중력 비율. 지상 몹보다 낙하 가속을 크게 줄인다. */
    public static final double FLY_GRAVITY_SCALE = 0.2;
    /** 수중 동물의 수직 관성 감쇠. 중력은 적용하지 않는다. */
    public static final double SWIM_VERTICAL_DRAG = 0.8;
    /** 평지 기본 넓백(0.8 block/10TPS tick)이 약 2블록 이동하게 하는 수평 감쇠. */
    public static final double KNOCKBACK_FRICTION = 0.6;
    /** WebCraft 한 블록 격자에서 일반 지상 몹이 넘는 턱 높이. */
    public static final double STEP_HEIGHT = 1.0;
    /** 한 쌍이 한 틱에 각 몹을 밀 수 있는 최대 거리. */
    public static final double SEPARATION_PUSH = 0.05;
    /** 여러 이웃이 몰려도 한 몹이 한 틱에 받는 밀림의 합을 제한한다. */
    public static final double MAX_SEPARATION_PER_TICK = 0.2;

    static final int STUCK_TICKS = 3;
    static final int DETOUR_TICKS = 8;

    static final double EPS = 1e-3;
    static final double SUBSTEP = 0.2;
    private static final double SEPARATION_CELL = 2.0;
    private static final ThreadLocal<SeparationScratch> SEPARATION_SCRATCH =
            ThreadLocal.withInitial(SeparationScratch::new);

    /**
     * 한 틱의 이동을 해소하며 mob.x/y/z/vy/onGround/state 를 변이한다.
     * @param dx,dy,dz 이번 틱 목표 변위(AI 산출, 블록). WALK/CLIMB의 dy는 0이다.
     * @param mode WALK은 턱 자동 등반, CLIMB은 수평 충돌 시 벽 등반, FLY는 완만한 중력과 dy 의도를 쓴다.
     */
    /** [ENCHANT-WIDE] 영혼 모래 {@code speedFactor(0.4f)}(핀 26.3 Blocks). */
    public static final double SOUL_SAND_SPEED_FACTOR = (double) 0.4f;

    /**
     * [ENCHANT-WIDE] 바닐라 {@code Entity.getBlockSpeedFactor}: 발 칸 블록의 계수가 1 이 아니면 그 값, 아니면
     * {@code getBlockPosBelowThatAffectsMyMovement}(발 아래 0.5000001) 블록의 계수.
     */
    public static double blockSpeedFactor(MobWorldView world, double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int feet = world.getBlock(bx, (int) Math.floor(y), bz) & 0xffff;
        if (feet == Blocks.SOUL_SAND) return SOUL_SAND_SPEED_FACTOR;
        int below = world.getBlock(bx, (int) Math.floor(y - 0.5000001), bz) & 0xffff;
        return below == Blocks.SOUL_SAND ? SOUL_SAND_SPEED_FACTOR : 1.0;
    }

    public static void tickMove(Mob mob, MobWorldView world,
                                double dx, double dy, double dz, MoveMode mode) {
        if (mode == MoveMode.FLY_NOCLIP) {
            double x0 = mob.x;
            double z0 = mob.z;
            boolean knockbackOverride = mob.consumeKnockbackOverride();
            mob.x += (knockbackOverride ? mob.knockbackVx : dx + mob.knockbackVx);
            mob.y += dy;
            mob.z += (knockbackOverride ? mob.knockbackVz : dz + mob.knockbackVz);
            mob.horizontalVx = mob.x - x0;
            mob.horizontalVz = mob.z - z0;
            mob.vy = 0.0;
            mob.onGround = false;
            mob.knockbackVx *= KNOCKBACK_FRICTION;
            mob.knockbackVz *= KNOCKBACK_FRICTION;
            mob.blockedMoveTicks = 0;
            mob.detourTicks = 0;
            mob.consumeFloatGoalMask();
            return;
        }
        final double hw = mob.width() / 2.0;
        final double h = mob.height();
        final boolean walk = mode == MoveMode.WALK;
        final boolean climb = mode == MoveMode.CLIMB;
        final boolean fly = mode == MoveMode.FLY;
        final boolean swim = mode == MoveMode.SWIM;
        final int floatGoalMask = mob.consumeFloatGoalMask();
        final boolean knockbackOverride = mob.consumeKnockbackOverride();
        final boolean liquid = floatGoalMask >= 0;

        final double x0 = mob.x, y0 = mob.y, z0 = mob.z;
        final double previousVy = mob.vy;

        // 직진 진전이 없었던 일반 몹은 잠시 목표 벡터의 좌/우 접선으로 움직인다.
        // 거미는 기존 벽타기를 우선하며 이 회피를 적용하지 않는다.
        double aiDx = dx, aiDz = dz;
        double aiDistance = Math.hypot(aiDx, aiDz);
        if (walk && mob.detourTicks > 0 && aiDistance > 1e-9) {
            double sign = mob.detourSign;
            dx = -aiDz * sign;
            dz = aiDx * sign;
            mob.detourTicks--;
        }
        // [ENCHANT-WIDE] 바닐라 Entity.move 끝의 getBlockSpeedFactor(): 발 칸, 아니면 발 아래 0.5 칸 블록의
        // speedFactor 를 수평 속도에 곱한다. 이 저장소에서 1 이 아닌 블록은 영혼 모래(0.4)뿐이다. AI 이동에만
        // 걸고 넉백 관성은 건드리지 않는다.
        double speedFactor = blockSpeedFactor(world, x0, y0, z0);
        dx *= speedFactor;
        dz *= speedFactor;

        // 1) 수직 속도. 비행은 AI dy를 직접 의도로 받아 관성을 감쇠하고 중력을 약하게 적용한다.
        if (!liquid) {
            if (swim) mob.vy = mob.vy * SWIM_VERTICAL_DRAG + dy;
            else if (fly) mob.vy = mob.vy * FLY_VERTICAL_DRAG + dy - GRAVITY * FLY_GRAVITY_SCALE;
            else if (mob.statusEffects().has(com.gameexpert.engine.effect.StatusEffect.LEVITATION)) {
                // [EC-MOBS] LivingEntity.travelInAir: 공중 부양은 중력 대신 MC 틱마다 v += (0.05·(amp+1) − v)·0.2,
                // 그 뒤 수직 항력 0.98. 권위 틱(= MC 틱 둘)의 속도를 MC 틱 속도로 나눠 두 번 돈다.
                double target = 0.05 * (mob.statusEffects().amplifier(
                        com.gameexpert.engine.effect.StatusEffect.LEVITATION) + 1);
                double v = mob.vy / 2.0;
                for (int mcTick = 0; mcTick < 2; mcTick++) v = (v + (target - v) * 0.2) * 0.98;
                mob.vy = v * 2.0;
            }
            else if (mob.vy <= 0.0 && mob.statusEffects().has(
                    com.gameexpert.engine.effect.StatusEffect.SLOW_FALLING)) {
                // [TRIAL-GAP] LivingEntity.getEffectiveGravity: 하강 중 느린 낙하는
                // min(중력, 0.01) — 바닐라 0.08 의 1/8 이다. 종단 속도도 바닐라 항력 0.98 로
                // 수렴하는 0.49 블록/MC 틱(= 권위 틱당 0.98)으로 묶는다.
                mob.vy -= GRAVITY * SLOW_FALLING_GRAVITY_SCALE;
                if (mob.vy < -SLOW_FALLING_TERMINAL) mob.vy = -SLOW_FALLING_TERMINAL;
            }
            else mob.vy -= GRAVITY;
            if (mob.vy < -TERMINAL) mob.vy = -TERMINAL;
        }

        final boolean grounded = mob.onGround;

        // 2) 피격 첫 틱은 이미 "직전 실제 운동/2 + 임펄스"로 합성된 속도를 그대로 쓴다.
        // 이 틱에 AI 변위를 다시 더하지 않고, 다음 틱부터 AI+마찰 잔여가 재개된다.
        if (knockbackOverride) {
            dx = mob.knockbackVx;
            dz = mob.knockbackVz;
        } else {
            dx += mob.knockbackVx;
            dz += mob.knockbackVz;
        }
        Horiz moved = slideHoriz(world, x0, y0, z0, hw, h, dx, dz);
        double nx = moved.x;
        double nz = moved.z;
        if (swim && mob instanceof AquaticAnimalMob && mob.type != MobType.DOLPHIN) {
            double limit = aquaticSurfaceDisplacement(world, nx, y0, nz, hw, h, mob.vy);
            if (Double.isNaN(limit)) {
                // Unknown water is not air. Retain the previous motion until this column is resident.
                mob.vy = previousVy;
                return;
            }
            mob.vy = Math.min(mob.vy, Math.max(-SUBSTEP, limit));
        }
        boolean blockedHoriz = moved.hitX || moved.hitZ;
        boolean blockedX = moved.hitX;
        boolean blockedZ = moved.hitZ;

        // 3) 1블록 자동 등반(지면 접촉 + 수평 막힘 + 등반몹 아님).
        boolean stepped = false;
        if (walk && grounded && blockedHoriz) {
            double ly = y0 + STEP_HEIGHT;
            if (!boxOverlaps(world, x0, ly, z0, hw, h)) {   // 머리 위 여유
                Horiz step = slideHoriz(world, x0, ly, z0, hw, h, dx, dz);
                double flatProgress = sq(nx - x0) + sq(nz - z0);
                double stepProgress = sq(step.x - x0) + sq(step.z - z0);
                boolean improved = stepProgress > flatProgress + EPS * EPS;
                if (improved) {
                    nx = step.x; nz = step.z; mob.y = ly; stepped = true;
                    blockedX = step.hitX;
                    blockedZ = step.hitZ;
                }
            }
        }
        mob.x = nx; mob.z = nz;
        mob.horizontalVx = mob.x - x0;
        mob.horizontalVz = mob.z - z0;
        mob.knockbackVx = blockedX ? 0.0 : mob.knockbackVx * KNOCKBACK_FRICTION;
        mob.knockbackVz = blockedZ ? 0.0 : mob.knockbackVz * KNOCKBACK_FRICTION;
        if (Math.abs(mob.knockbackVx) < 1e-4) mob.knockbackVx = 0.0;
        if (Math.abs(mob.knockbackVz) < 1e-4) mob.knockbackVz = 0.0;

        // AI가 수 틱 연속 벽에 눌렸을 때만 짧은 접선 우회를 예약한다.
        // 넉백 중에는 전투 임펄스를 장애물 회피로 오인하지 않는다.
        double progress = Math.hypot(mob.horizontalVx, mob.horizontalVz);
        boolean navigating = aiDistance > 1e-3
                && Math.abs(mob.knockbackVx) + Math.abs(mob.knockbackVz) < 1e-3;
        if (walk && navigating && blockedHoriz && progress < aiDistance * 0.2) {
            mob.blockedMoveTicks++;
            if (mob.blockedMoveTicks >= STUCK_TICKS && mob.detourTicks == 0) {
                mob.detourSign = mob.detourSign == 0
                        ? (((mob.id & 1L) == 0L) ? 1 : -1)
                        : -mob.detourSign;
                mob.detourTicks = DETOUR_TICKS;
                mob.blockedMoveTicks = 0;
            }
        } else if (progress >= aiDistance * 0.2 || !blockedHoriz) {
            mob.blockedMoveTicks = 0;
        }

        // 4) 벽 등반(거미): 수평 막히면 이번 틱 상승.
        if (climb && blockedHoriz && !stepped && !liquid) {
            mob.vy = CLIMB_SPEED;
        }

        // 5) 수직(서브스텝으로 낙하 터널링 방지).
        if (liquid) {
            for (int gameTick = 0; gameTick < Mob.FLOAT_GOAL_DRAWS_PER_TICK; gameTick++) {
                mob.vy = mob.vy * SWIM_VERTICAL_DRAG
                        + ((floatGoalMask & (1 << gameTick)) != 0
                        ? Mob.FLOAT_GOAL_JUMP_IMPULSE : 0.0);
                if (mob.vy < -TERMINAL) mob.vy = -TERMINAL;
                moveVertical(mob, world, hw, h);
            }
        } else {
            moveVertical(mob, world, hw, h);
        }
    }

    /** Bound ordinary swimming by resident water-cell volume, including existing upward inertia. */
    private static double aquaticSurfaceDisplacement(MobWorldView world,
            double x, double y, double z, double halfWidth, double height, double velocity) {
        double limit = Double.POSITIVE_INFINITY;
        int top = (int) Math.floor(y + height + Math.max(0, velocity));
        for (int bx = (int) Math.floor(x - halfWidth + EPS);
                bx <= (int) Math.floor(x + halfWidth - EPS); bx++) {
            for (int bz = (int) Math.floor(z - halfWidth + EPS);
                    bz <= (int) Math.floor(z + halfWidth - EPS); bz++) {
                boolean wet = false;
                for (int by = (int) Math.floor(y + EPS); by <= top; by++) {
                    int block = world.getBlock(bx, by, bz);
                    if (block < 0) return Double.NaN;
                    boolean water = Fluids.isWaterMedium(block,
                            world.blockState(bx, by, bz, block));
                    if (!water) {
                        if (wet) limit = Math.min(limit, by - height - EPS - y);
                        break;
                    }
                    wet = true;
                }
            }
        }
        return limit;
    }

    /**
     * AI 물리 뒤 유체 속도를 충돌 가능한 추가 운동으로 합성한다. 몹 수평 모델은 지속 velocity/drag가
     * 아니라 매 틱 AI 변위를 직접 적분하므로 유체의 이번 틱 delta도 수평 변위에 더한다. 수직은 기존
     * 지속 속도(vy)에 더한다. 확인되지 않은 별도 수평 drag를 만들지 않으며 AI/넉백 상태도 덮어쓰지 않는다.
     */
    public static void addFluidPush(Mob mob, MobWorldView world, double dx, double dy, double dz) {
        double x0 = mob.x;
        double z0 = mob.z;
        Horiz moved = slideHoriz(world, x0, mob.y, z0,
                mob.width() * 0.5, mob.height(), dx, dz);
        mob.x = moved.x;
        mob.z = moved.z;
        mob.horizontalVx += mob.x - x0;
        mob.horizontalVz += mob.z - z0;
        mob.vy += dy;
    }

    /** 수직 AABB가 겹치는 몹만 셀 해시의 3×3 이웃에서 찾아 수평으로 분리한다. */
    static void separateOverlaps(List<Mob> mobs, MobWorldView world) {
        int count = mobs.size();
        if (count < 2) return;
        SeparationScratch scratch = SEPARATION_SCRATCH.get();
        scratch.prepare(mobs);
        for (int i = 0; i < count; i++) {
            Mob a = mobs.get(i);
            if (a.isDead() || a.removed || a.isRidingBoat() || a.isMobPassenger()) continue;
            int found = scratch.collectCandidates(i, scratch.x[i], scratch.z[i], count);
            boolean linear = found < 0;
            int candidateCount = linear ? count - i - 1 : found;
            for (int ordinal = 0; ordinal < candidateCount; ordinal++) {
                int j = linear ? i + ordinal + 1 : scratch.candidates[ordinal];
                Mob b = mobs.get(j);
                if (b.isDead() || b.removed || b.isRidingBoat() || b.isMobPassenger()
                        || !scratch.verticalOverlap(i, j)) continue;

                double dx = scratch.x[j] - scratch.x[i];
                double dz = scratch.z[j] - scratch.z[i];
                double minDistance = (scratch.width[i] + scratch.width[j]) * 0.5;
                double distance = Math.hypot(dx, dz);
                if (distance >= minDistance) continue;

                double ux, uz;
                if (distance < 1e-9) {
                    // 완전히 같은 중심도 실행마다 같은 방향으로 갈라진다.
                    long mixed = a.id * 31L + b.id;
                    if ((mixed & 1L) == 0L) { ux = 1.0; uz = 0.0; }
                    else { ux = 0.0; uz = 1.0; }
                } else {
                    ux = dx / distance;
                    uz = dz / distance;
                }
                double halfPenetration = (minDistance - distance) * 0.5;
                // [EC-MOBS] 셜커·아이템 액자·엔드 수정·드래곤은 밀리지 않는다(Shulker.push/HangingEntity 무시).
                double pushA = a.immovable() ? 0.0 : Math.min(Math.min(SEPARATION_PUSH, halfPenetration),
                        MAX_SEPARATION_PER_TICK - scratch.pushed[i]);
                double pushB = b.immovable() ? 0.0 : Math.min(Math.min(SEPARATION_PUSH, halfPenetration),
                        MAX_SEPARATION_PER_TICK - scratch.pushed[j]);
                if (pushA > 0.0) {
                    double actual = displaceHoriz(a, world, -ux * pushA, -uz * pushA);
                    scratch.pushed[i] += actual;
                    scratch.x[i] = a.x;
                    scratch.z[i] = a.z;
                }
                if (pushB > 0.0) {
                    double actual = displaceHoriz(b, world, ux * pushB, uz * pushB);
                    scratch.pushed[j] += actual;
                    scratch.x[j] = b.x;
                    scratch.z[j] = b.z;
                }
            }
        }
    }

    /** 겹침 해소 틱에서 primitive 배열과 셀 해시를 재사용한다. */
    private static final class SeparationScratch {
        private double[] pushed = new double[16];
        private double[] x = new double[16];
        private double[] z = new double[16];
        private double[] y = new double[16];
        private double[] width = new double[16];
        private double[] height = new double[16];
        private int[] candidates = new int[16];
        private int[] next = new int[16];
        private int[] bucketHead = new int[16];
        private int[] bucketTail = new int[16];
        private long[] cellKeys = new long[32];
        private int[] cellBuckets = new int[32];
        private boolean[] cellUsed = new boolean[32];
        private int cellCount;

        private void prepare(List<Mob> mobs) {
            int count = mobs.size();
            ensureEntityCapacity(count);
            Arrays.fill(pushed, 0, count, 0.0);
            for (int index = 0; index < count; index++) {
                Mob mob = mobs.get(index);
                x[index] = mob.x;
                z[index] = mob.z;
                y[index] = mob.y;
                width[index] = mob.width();
                height[index] = mob.height();
            }
            buildCells(count);
        }

        private void ensureEntityCapacity(int count) {
            if (count <= x.length) return;
            int capacity = x.length;
            while (capacity < count) capacity *= 2;
            pushed = Arrays.copyOf(pushed, capacity);
            x = Arrays.copyOf(x, capacity);
            z = Arrays.copyOf(z, capacity);
            y = Arrays.copyOf(y, capacity);
            width = Arrays.copyOf(width, capacity);
            height = Arrays.copyOf(height, capacity);
            candidates = Arrays.copyOf(candidates, capacity);
            next = Arrays.copyOf(next, capacity);
            bucketHead = Arrays.copyOf(bucketHead, capacity);
            bucketTail = Arrays.copyOf(bucketTail, capacity);
            int tableCapacity = 1;
            while (tableCapacity < Math.max(4, capacity * 2)) tableCapacity <<= 1;
            cellKeys = new long[tableCapacity];
            cellBuckets = new int[tableCapacity];
            cellUsed = new boolean[tableCapacity];
        }

        private void buildCells(int count) {
            Arrays.fill(cellUsed, false);
            cellCount = 0;
            for (int index = 0; index < count; index++) {
                int cellX = (int) Math.floor(x[index] / SEPARATION_CELL);
                int cellZ = (int) Math.floor(z[index] / SEPARATION_CELL);
                int slot = findSlot(cellKey(cellX, cellZ));
                if (!cellUsed[slot]) {
                    cellUsed[slot] = true;
                    cellKeys[slot] = cellKey(cellX, cellZ);
                    cellBuckets[slot] = cellCount;
                    bucketHead[cellCount] = -1;
                    bucketTail[cellCount] = -1;
                    cellCount++;
                }
                int bucket = cellBuckets[slot];
                next[index] = -1;
                if (bucketTail[bucket] < 0) bucketHead[bucket] = index;
                else next[bucketTail[bucket]] = index;
                bucketTail[bucket] = index;
            }
        }

        private int collectCandidates(
                int index, double positionX, double positionZ, int totalCount) {
            int cellX = (int) Math.floor(positionX / SEPARATION_CELL);
            int cellZ = (int) Math.floor(positionZ / SEPARATION_CELL);
            int found = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int bucket = findBucket(cellKey(cellX + dx, cellZ + dz));
                    if (bucket < 0) continue;
                    for (int candidate = bucketHead[bucket]; candidate >= 0;
                            candidate = next[candidate]) {
                        if (candidate <= index) continue;
                        candidates[found++] = candidate;
                    }
                }
            }
            if (preferLinearCandidateTraversal(found, totalCount - index - 1)) return -1;
            if (found > 1) Arrays.sort(candidates, 0, found);
            return found;
        }

        private int findBucket(long key) {
            int slot = findSlot(key);
            return cellUsed[slot] ? cellBuckets[slot] : -1;
        }

        private int findSlot(long key) {
            int mask = cellKeys.length - 1;
            int slot = (int) mixCellKey(key) & mask;
            while (cellUsed[slot] && cellKeys[slot] != key) slot = (slot + 1) & mask;
            return slot;
        }

        private boolean verticalOverlap(int first, int second) {
            return y[first] < y[second] + height[second] - EPS
                    && y[second] < y[first] + height[first] - EPS;
        }

        private static long cellKey(int cellX, int cellZ) {
            return ((long) cellX << 32) ^ (cellZ & 0xffff_ffffL);
        }

        private static long mixCellKey(long value) {
            value ^= value >>> 33;
            value *= 0xff51afd7ed558ccdl;
            value ^= value >>> 33;
            value *= 0xc4ceb9fe1a85ec53L;
            return value ^ (value >>> 33);
        }
    }

    static boolean preferLinearCandidateTraversal(int candidateCount, int remainingCount) {
        return candidateCount >= 64 && (long) candidateCount * 8L >= remainingCount;
    }

    private static double displaceHoriz(Mob mob, MobWorldView world, double dx, double dz) {
        double x0 = mob.x, z0 = mob.z;
        Horiz result = slideHoriz(world, x0, mob.y, z0, mob.width() * 0.5,
                mob.height(), dx, dz);
        mob.x = result.x;
        mob.z = result.z;
        mob.horizontalVx += mob.x - x0;
        mob.horizontalVz += mob.z - z0;
        return Math.hypot(mob.x - x0, mob.z - z0);
    }

    // ── 수직 이동(서브스텝) ─────────────────────────────────────────
    private static void moveVertical(Mob mob, MobWorldView world, double hw, double h) {
        double remaining = mob.vy;
        boolean grounded = false;
        if (remaining == 0) {
            // 정지 상태여도 지지 확인.
            mob.onGround = groundBelow(world, mob.x, mob.y, mob.z, hw);
            return;
        }
        int steps = Math.max(1, (int) Math.ceil(Math.abs(remaining) / SUBSTEP));
        double sy = remaining / steps;
        for (int i = 0; i < steps; i++) {
            VertResult r = moveVertStep(world, mob.x, mob.y, mob.z, hw, h, sy);
            mob.y = r.v;
            if (r.hit) {
                if (sy < Blocks.MIN_Y) grounded = true;  // 바닥 착지
                mob.vy = 0;
                break;
            }
        }
        mob.onGround = grounded || groundBelow(world, mob.x, mob.y, mob.z, hw);
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    private static final class VertResult {
        private final double v;
        private final boolean hit;
    }

    private static VertResult moveVertStep(MobWorldView world, double px, double py, double pz,
                                           double hw, double h, double delta) {
        double face = py + (delta > 0 ? h : 0);
        CollisionQuery query = queryCollision(world,
                px - hw, Math.min(face, face + delta), pz - hw,
                px + hw, Math.max(face, face + delta), pz + hw, 1, face, delta);
        return new VertResult(py + query.delta, query.hit);
    }

    // ── 수평 이동(한 축) ────────────────────────────────────────────
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    private static final class Axis {
        private final double v;
        private final boolean hit;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    private static final class Horiz {
        private final double x;
        private final double z;
        private final boolean hitX;
        private final boolean hitZ;
    }

    /** 양 축 순서를 모두 시험하고 더 멀리 간 결과를 골라 충돌면의 접선 성분을 보존한다. */
    private static Horiz slideHoriz(MobWorldView world, double px, double py, double pz,
                                    double hw, double h, double dx, double dz) {
        Axis xFirst = moveHoriz(world, px, py, pz, hw, h, true, dx);
        Axis zAfterX = moveHoriz(world, xFirst.v, py, pz, hw, h, false, dz);

        Axis zFirst = moveHoriz(world, px, py, pz, hw, h, false, dz);
        Axis xAfterZ = moveHoriz(world, px, py, zFirst.v, hw, h, true, dx);

        double xFirstDistance = sq(xFirst.v - px) + sq(zAfterX.v - pz);
        double zFirstDistance = sq(xAfterZ.v - px) + sq(zFirst.v - pz);
        if (zFirstDistance > xFirstDistance + EPS * EPS) {
            return new Horiz(xAfterZ.v, zFirst.v, xAfterZ.hit, zFirst.hit);
        }
        return new Horiz(xFirst.v, zAfterX.v, xFirst.hit, zAfterX.hit);
    }

    private static Axis moveHoriz(MobWorldView world, double px, double py, double pz,
                                  double hw, double h, boolean xAxis, double delta) {
        double current = xAxis ? px : pz;
        if (delta == 0) return new Axis(current, false);
        double face = current + (delta > 0 ? hw : -hw);
        double low = Math.min(face, face + delta), high = Math.max(face, face + delta);
        CollisionQuery query = queryCollision(world,
                xAxis ? low : px - hw, py, xAxis ? pz - hw : low,
                xAxis ? high : px + hw, py + h, xAxis ? pz + hw : high,
                xAxis ? 0 : 2, face, delta);
        return new Axis(current + query.delta, query.hit);
    }

    /** 발밑 바로 아래(±0.06)에 실제 충돌 형상의 지지가 있는가. */
    static boolean groundBelow(MobWorldView world, double px, double py, double pz, double hw) {
        return queryCollision(world, px - hw, py - .06, pz - hw,
                px + hw, py + EPS, pz + hw, -1, 0, 0).hit;
    }

    /**
     * [EC-MOBS] 임의 AABB 가 블록 충돌 형상과 겹치는가({@code Level.noBlockCollision} 의 부정).
     * 아이템 액자 설치·생존과 셜커 부착·순간이동이 쓴다.
     */
    public static boolean blockCollision(MobWorldView world, double minX, double minY,
            double minZ, double maxX, double maxY, double maxZ) {
        return queryCollision(world, minX, minY, minZ, maxX, maxY, maxZ, -1, 0, 0).hit;
    }

    /** 자동 등반 머리 여유도 셀 전체 대신 각각의 충돌 상자로 판정한다. */
    private static boolean boxOverlaps(MobWorldView world, double px, double py, double pz,
            double hw, double h) {
        return queryCollision(world, px - hw, py, pz - hw,
                px + hw, py + h, pz + hw, -1, 0, 0).hit;
    }

    private static final double HORIZONTAL_OUTSET = 3.5 / 16;
    private static final ThreadLocal<CollisionQuery> COLLISION_QUERY =
            ThreadLocal.withInitial(CollisionQuery::new);

    /** 한 스레드의 동기적인 순회가 재사용하며, 셀마다 배열이나 캡처 람다를 만들지 않는다. */
    private static final class CollisionQuery implements BuildingBlockRules.CollisionBoxVisitor {
        double minX, minY, minZ, maxX, maxY, maxZ;
        int cellX, cellY, cellZ, axis;
        double face, delta;
        boolean hit, positive;

        @Override
        public void visit(double x0, double y0, double z0, double x1, double y1, double z1) {
            x0 += cellX; x1 += cellX;
            y0 += cellY; y1 += cellY;
            z0 += cellZ; z1 += cellZ;
            if (x1 <= minX + EPS || x0 >= maxX - EPS
                    || y1 <= minY + EPS || y0 >= maxY - EPS
                    || z1 <= minZ + EPS || z0 >= maxZ - EPS) return;
            hit = true;
            if (axis < 0) return;
            double near = axis == 0 ? x0 : axis == 1 ? y0 : z0;
            double far = axis == 0 ? x1 : axis == 1 ? y1 : z1;
            delta = positive ? Math.min(delta, near - face - EPS)
                    : Math.max(delta, far - face + EPS);
        }
    }

    private static CollisionQuery queryCollision(MobWorldView world,
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
            int axis, double face, double delta) {
        CollisionQuery query = COLLISION_QUERY.get();
        query.minX = minX; query.minY = minY; query.minZ = minZ;
        query.maxX = maxX; query.maxY = maxY; query.maxZ = maxZ;
        query.axis = axis; query.face = face; query.delta = delta;
        query.hit = false; query.positive = delta > 0;
        // 선반버섯은 옆 셀까지, 울타리·담장은 위 셀까지 돌출된다.
        for (int x = floor(minX - HORIZONTAL_OUTSET + EPS);
                x <= floor(maxX + HORIZONTAL_OUTSET - EPS); x++) {
            for (int y = floor(minY + EPS) - 1; y <= floor(maxY - EPS); y++) {
                for (int z = floor(minZ - HORIZONTAL_OUTSET + EPS);
                        z <= floor(maxZ + HORIZONTAL_OUTSET - EPS); z++) {
                    query.cellX = x; query.cellY = y; query.cellZ = z;
                    world.forBlockCollisionBoxes(x, y, z, query);
                }
            }
        }
        return query;
    }

    private static double sq(double v) { return v * v; }
    private static int floor(double v) { return (int) Math.floor(v); }
}
