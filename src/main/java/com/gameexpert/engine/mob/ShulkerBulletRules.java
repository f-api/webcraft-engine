package com.gameexpert.engine.mob;

/**
 * [EC-MOBS] 셜커 탄환({@code ShulkerBullet})의 조향 정본. 권위 틱(10 TPS)마다 MC 틱 두 번을 돈다.
 *
 * <p>근거는 핀 26.3-snapshot-7 jar 의 {@code net.minecraft.world.entity.projectile.ShulkerBullet}:
 * <ul>
 *   <li>{@code SPEED = 0.15}, {@code getDefaultGravity() = 0.04}, {@code sized(0.3125, 0.3125)},
 *       {@code noPhysics = true}(수명 제한 없음).</li>
 *   <li>생성자: 주인 상자 중심에서 출발, {@code currentMoveDirection = UP} 뒤
 *       {@code selectNextMoveDirection(주인 부착축, 대상)}.</li>
 *   <li>{@code selectNextMoveDirection}: 대상 칸({@code containing(x, y + h/2, z)}) 중심에서 2 블록
 *       안이면 그 점을 향하고, 아니면 부착축이 아닌 두 축에서 대상 쪽 빈 칸 방향을 후보로 모은 뒤
 *       <b>무조건</b> {@code Direction.getRandom} 을 한 번 뽑고, 후보가 없으면 그 방향부터 빈 칸이 나올
 *       때까지 최대 5번 다시 뽑고, 후보가 있으면 {@code nextInt(후보 수)} 로 고른다. 목표 변위를 길이
 *       0.15 로 정규화하고 {@code flightSteps = 10 + nextInt(5)·10}.</li>
 *   <li>{@code tick}(서버): 대상이 살아 있으면 목표 변위를 ×1.025 해 [-1, 1] 로 자르고 속도를 목표
 *       쪽으로 0.2 비율 당긴다. 대상이 없으면 중력 0.04. 이동 후 {@code flightSteps} 가 0 이 되면
 *       다시 고르고, 진행 방향 앞 칸이 막혔거나 진행 축에서 대상과 같은 칸 좌표에 오면 다시 고른다.</li>
 * </ul>
 *
 * <p>정적판 짝은 {@code StandaloneShulkerBulletRules.ts} 이고, 두 권위는 같은 시드에서 같은 궤적을
 * 내는지 테스트로 대조한다.
 */
public final class ShulkerBulletRules {

    private ShulkerBulletRules() {
    }

    public static final double SPEED = 0.15;
    public static final double GRAVITY = 0.04;
    public static final double SIZE = 0.3125;
    public static final int DAMAGE = 4;
    /** {@code onHitEntity} 의 {@code MobEffectInstance(LEVITATION, 200)}(MC 틱). */
    public static final int LEVITATION_MC_TICKS = 200;
    /** 축 부호: -1 없음(대상 소실 뒤의 null 방향), 0 X, 1 Y, 2 Z. */
    public static final int AXIS_NONE = -1;
    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;
    public static final int AXIS_Z = 2;

    /**
     * 탄환 전용 {@code java.util.Random}(같은 LCG · 같은 nextInt/nextFloat/nextDouble). 상태(48 비트)를 꺼내고 되돌릴
     * 수 있어 탄환 조향이 저장·재기동을 넘는다. 정적판 {@code shulkerBulletRandom} 이 같은 원시 상태를 든다.
     */
    public static final class BulletRandom implements MobRandom {
        private static final long MULTIPLIER = 0x5DEECE66DL;
        private static final long MASK = (1L << 48) - 1;
        private long state;

        public BulletRandom(long seed) {
            state = (seed ^ MULTIPLIER) & MASK;
        }

        /** 저장된 원시 상태로 되살린다. */
        public static BulletRandom ofState(long state) {
            if (state < 0 || state > MASK) throw new IllegalArgumentException("invalid bullet random state");
            BulletRandom random = new BulletRandom(0L);
            random.state = state;
            return random;
        }

        public long state() {
            return state;
        }

        private int next(int bits) {
            state = state * MULTIPLIER + 0xBL & MASK;
            return (int) (state >>> 48 - bits);
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) return 0;
            int r = next(31);
            int m = bound - 1;
            if ((bound & m) == 0) return (int) ((long) bound * (long) r >> 31);
            for (int u = r; u - (r = u % bound) + m < 0; u = next(31)) {
                // java.util.Random.nextInt(bound) 의 거절 표본.
            }
            return r;
        }

        @Override
        public float nextFloat() {
            return next(24) / (float) (1 << 24);
        }

        @Override
        public double nextDouble() {
            return (((long) next(26) << 27) + next(27)) * 0x1.0p-53;
        }
    }

    /** 방향의 축({@code Direction.getAxis}). */
    public static int axisOf(int direction) {
        return direction < 0 ? AXIS_NONE : direction <= ItemFrameRules.UP ? AXIS_Y
                : direction <= ItemFrameRules.SOUTH ? AXIS_Z : AXIS_X;
    }

    /** 조향이 보는 월드: 칸이 비었는가({@code isEmptyBlock}), 앞 칸이 딛을 수 있는가. */
    public interface SteerWorld {
        boolean empty(int x, int y, int z);

        /** {@code loadedAndEntityCanStandOn(pos, bullet)}: 칸 윗면이 엔티티가 설 수 있는 면인가. */
        boolean canStandOn(int x, int y, int z);
    }

    /** 탄환의 가변 조향 상태. 위치·속도는 MC 틱 단위(블록/MC 틱)다. */
    public static final class State {
        public double x, y, z;
        public double vx, vy, vz;
        /** {@code currentMoveDirection}. -1 은 null. */
        public int moveDirection;
        public int flightSteps;
        public double targetDeltaX, targetDeltaY, targetDeltaZ;

        public State(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.moveDirection = ItemFrameRules.UP;
        }
    }

    /** 대상 사실. 위치는 발밑, 높이는 {@code getBbHeight}. */
    public record Target(double x, double y, double z, double height) { }

    private static int floor(double v) { return (int) Math.floor(v); }

    /** {@code selectNextMoveDirection(axis, target)}. {@code target} 이 null 이면 발밑 아래 칸. */
    public static void selectNextMoveDirection(State s, int axis, Target target,
            SteerWorld world, MobRandom rng) {
        double yOffset = 0.5;
        int tx, ty, tz;
        if (target == null) {
            tx = floor(s.x);
            ty = floor(s.y) - 1;
            tz = floor(s.z);
        } else {
            yOffset = target.height() * 0.5;
            tx = floor(target.x());
            ty = floor(target.y() + yOffset);
            tz = floor(target.z());
        }
        double wantX = tx + 0.5;
        double wantY = ty + yOffset;
        double wantZ = tz + 0.5;
        int chosen = -1;
        // BlockPos.closerToCenterThan(position, 2.0): 칸 중심과 위치의 거리 < 2.
        double cdx = tx + 0.5 - s.x, cdy = ty + 0.5 - s.y, cdz = tz + 0.5 - s.z;
        if (!(cdx * cdx + cdy * cdy + cdz * cdz < 4.0)) {
            int bx = floor(s.x), by = floor(s.y), bz = floor(s.z);
            int[] candidates = new int[6];
            int count = 0;
            if (axis != AXIS_X) {
                if (bx < tx && world.empty(bx + 1, by, bz)) candidates[count++] = ItemFrameRules.EAST;
                else if (bx > tx && world.empty(bx - 1, by, bz)) candidates[count++] = ItemFrameRules.WEST;
            }
            if (axis != AXIS_Y) {
                if (by < ty && world.empty(bx, by + 1, bz)) candidates[count++] = ItemFrameRules.UP;
                else if (by > ty && world.empty(bx, by - 1, bz)) candidates[count++] = ItemFrameRules.DOWN;
            }
            if (axis != AXIS_Z) {
                if (bz < tz && world.empty(bx, by, bz + 1)) candidates[count++] = ItemFrameRules.SOUTH;
                else if (bz > tz && world.empty(bx, by, bz - 1)) candidates[count++] = ItemFrameRules.NORTH;
            }
            // Direction.getRandom(random): 후보 유무와 무관하게 먼저 한 번 소비한다.
            chosen = rng.nextInt(6);
            if (count == 0) {
                for (int retries = 5; !world.empty(bx + ItemFrameRules.stepX(chosen),
                        by + ItemFrameRules.stepY(chosen), bz + ItemFrameRules.stepZ(chosen))
                        && retries > 0; retries--) {
                    chosen = rng.nextInt(6);
                }
            } else {
                chosen = candidates[rng.nextInt(count)];
            }
            wantX = s.x + ItemFrameRules.stepX(chosen);
            wantY = s.y + ItemFrameRules.stepY(chosen);
            wantZ = s.z + ItemFrameRules.stepZ(chosen);
        }
        s.moveDirection = chosen;
        double dx = wantX - s.x, dy = wantY - s.y, dz = wantZ - s.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length == 0.0) {
            s.targetDeltaX = 0.0;
            s.targetDeltaY = 0.0;
            s.targetDeltaZ = 0.0;
        } else {
            s.targetDeltaX = dx / length * SPEED;
            s.targetDeltaY = dy / length * SPEED;
            s.targetDeltaZ = dz / length * SPEED;
        }
        s.flightSteps = 10 + rng.nextInt(5) * 10;
    }

    private static double clampUnit(double v) {
        return v < -1.0 ? -1.0 : Math.min(v, 1.0);
    }

    /** 이동 전 속도 갱신(대상이 있으면 조향, 없으면 중력). */
    public static void steerVelocity(State s, boolean targetAlive) {
        if (targetAlive) {
            s.targetDeltaX = clampUnit(s.targetDeltaX * 1.025);
            s.targetDeltaY = clampUnit(s.targetDeltaY * 1.025);
            s.targetDeltaZ = clampUnit(s.targetDeltaZ * 1.025);
            s.vx += (s.targetDeltaX - s.vx) * 0.2;
            s.vy += (s.targetDeltaY - s.vy) * 0.2;
            s.vz += (s.targetDeltaZ - s.vz) * 0.2;
        } else {
            s.vy -= GRAVITY;
        }
    }

    /** 이동 뒤 방향 재선택({@code tick} 말미, 대상이 있을 때만). */
    public static void afterMove(State s, Target target, SteerWorld world, MobRandom rng) {
        if (target == null) return;
        if (s.flightSteps > 0) {
            s.flightSteps--;
            if (s.flightSteps == 0) {
                selectNextMoveDirection(s, axisOf(s.moveDirection), target, world, rng);
            }
        }
        if (s.moveDirection < 0) return;
        int bx = floor(s.x), by = floor(s.y), bz = floor(s.z);
        int axis = axisOf(s.moveDirection);
        if (world.canStandOn(bx + ItemFrameRules.stepX(s.moveDirection),
                by + ItemFrameRules.stepY(s.moveDirection),
                bz + ItemFrameRules.stepZ(s.moveDirection))) {
            selectNextMoveDirection(s, axis, target, world, rng);
            return;
        }
        int tx = floor(target.x()), ty = floor(target.y()), tz = floor(target.z());
        if (axis == AXIS_X && bx == tx || axis == AXIS_Z && bz == tz
                || axis == AXIS_Y && by == ty) {
            selectNextMoveDirection(s, axis, target, world, rng);
        }
    }

    /**
     * {@code ProjectileUtil.computeMargin}: {@code max(0, min(0.3, (tickCount - 2) / 20))}. 엔티티
     * 명중 상자를 이만큼 부풀린다.
     */
    public static float hitMargin(int tickCount) {
        return Math.max(0.0f, Math.min(0.3f, (tickCount - 2) / 20.0f));
    }

    /**
     * {@code AABB.clip(from, to)} 의 매개변수 t. 들어가는 면만 보고({@code getDirection} 의 부호별
     * 한 면), {@code 0 < t < 1} 이며 면 범위를 1e-7 여유로 통과해야 한다. 안에서 출발하면 빗나간다.
     * 빗나가면 NaN.
     */
    public static double clipT(double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        double dx = toX - fromX, dy = toY - fromY, dz = toZ - fromZ;
        double[] best = {1.0};
        boolean hit = false;
        if (dx > 1.0E-7) hit |= clipPoint(best, dx, dy, dz, minX, minY, maxY, minZ, maxZ, fromX, fromY, fromZ);
        else if (dx < -1.0E-7) hit |= clipPoint(best, dx, dy, dz, maxX, minY, maxY, minZ, maxZ, fromX, fromY, fromZ);
        if (dy > 1.0E-7) hit |= clipPoint(best, dy, dz, dx, minY, minZ, maxZ, minX, maxX, fromY, fromZ, fromX);
        else if (dy < -1.0E-7) hit |= clipPoint(best, dy, dz, dx, maxY, minZ, maxZ, minX, maxX, fromY, fromZ, fromX);
        if (dz > 1.0E-7) hit |= clipPoint(best, dz, dx, dy, minZ, minX, maxX, minY, maxY, fromZ, fromX, fromY);
        else if (dz < -1.0E-7) hit |= clipPoint(best, dz, dx, dy, maxZ, minX, maxX, minY, maxY, fromZ, fromX, fromY);
        return hit ? best[0] : Double.NaN;
    }

    private static boolean clipPoint(double[] best, double da, double db, double dc,
            double planeA, double minB, double maxB, double minC, double maxC,
            double fromA, double fromB, double fromC) {
        double t = (planeA - fromA) / da;
        double b = fromB + t * db;
        double c = fromC + t * dc;
        if (0.0 < t && t < best[0] && minB - 1.0E-7 < b && b < maxB + 1.0E-7
                && minC - 1.0E-7 < c && c < maxC + 1.0E-7) {
            best[0] = t;
            return true;
        }
        return false;
    }
}
