package com.gameexpert.engine;

/**
 * 던진 엔더의 눈 한 개의 비행. 바닐라 26.3 {@code EyeOfEnder#signalTo} · {@code #tick} ·
 * {@code #updateDeltaMovement} 를 글자 그대로 옮긴 순수 로직이며 정적판
 * {@code StandaloneEnderEyeFlight}(StandaloneEndPortalRules.ts)가 같은 식의 사본이다.
 *
 * <p>상수(javap): 목표가 수평 12 블록 밖이면 12 블록 앞·8 블록 위를 목표로 삼고
 * ({@code signalTo}), 속력은 {@code lerp(0.0025, 현재 수평 속력, 목표까지 수평 거리)},
 * 목표 1 블록 안에서는 수평·수직 모두 ×0.8, 수직은 목표 높이 쪽 ±1 로 0.015 만큼 끌린다.
 * 서버 {@code life} 가 80 을 넘는 MC 틱에 소리를 내고 사라지며 {@code surviveAfterDeath}
 * ({@code random.nextInt(5) > 0}, 80%)면 그 자리에 아이템으로 떨어진다.
 *
 * <p>권위는 10 TPS 이므로 호출부가 권위 1 틱에 {@link #mcTick()} 을 두 번 부른다(바닐라
 * 20 TPS 궤적을 그대로 재생한다). 이 엔티티는 충돌이 없는 {@code Entity} 라 블록을 통과한다.
 */
public final class EnderEyeFlight {
    /** {@code EyeOfEnder.tick}: {@code life > 80} 에서 종결. */
    public static final int MAX_LIFE_MC_TICKS = 80;
    /** {@code EyeOfEnder.TOO_FAR_DISTANCE}. */
    public static final double TOO_FAR_DISTANCE = 12.0;
    /** {@code EyeOfEnder.TOO_FAR_SIGNAL_HEIGHT}. */
    public static final double TOO_FAR_SIGNAL_HEIGHT = 8.0;
    /** {@code updateDeltaMovement} 의 속력 보간 계수. */
    public static final double SPEED_LERP = 0.0025;
    /** 목표 1 블록 안 감속. */
    public static final double NEAR_DAMPING = 0.8;
    /** 수직 속력이 ±1 쪽으로 끌리는 비율. */
    public static final double VERTICAL_PULL = 0.015;
    /** {@code surviveAfterDeath = random.nextInt(5) > 0}. */
    public static final int SURVIVE_BOUND = 5;

    private double x;
    private double y;
    private double z;
    private double motionX;
    private double motionY;
    private double motionZ;
    private final double targetX;
    private final double targetY;
    private final double targetZ;
    private final boolean surviveAfterDeath;
    private int life;

    private EnderEyeFlight(double x, double y, double z, double targetX, double targetY,
            double targetZ, boolean surviveAfterDeath) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.surviveAfterDeath = surviveAfterDeath;
    }

    /**
     * {@code new EyeOfEnder(level, x, y, z)} 뒤 {@code signalTo(Vec3.atLowerCornerOf(located))}.
     *
     * @param surviveRoll {@code random.nextInt(5)} 의 결과(0..4). 0 이면 깨진다.
     */
    public static EnderEyeFlight signalTo(double x, double y, double z,
            double locatedX, double locatedY, double locatedZ, int surviveRoll) {
        if (surviveRoll < 0 || surviveRoll >= SURVIVE_BOUND) {
            throw new IllegalArgumentException("survive roll must be nextInt(5)");
        }
        double deltaX = locatedX - x;
        double deltaZ = locatedZ - z;
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double tx, ty, tz;
        if (horizontal > TOO_FAR_DISTANCE) {
            tx = x + deltaX / horizontal * TOO_FAR_DISTANCE;
            ty = y + TOO_FAR_SIGNAL_HEIGHT;
            tz = z + deltaZ / horizontal * TOO_FAR_DISTANCE;
        } else {
            tx = locatedX;
            ty = locatedY;
            tz = locatedZ;
        }
        return new EnderEyeFlight(x, y, z, tx, ty, tz, surviveRoll > 0);
    }

    /** 바닐라 서버 한 틱. 이번 틱에 수명이 다해 종결했으면 true 다. */
    public boolean mcTick() {
        double nextX = x + motionX;
        double nextY = y + motionY;
        double nextZ = z + motionZ;
        updateDeltaMovement(nextX, nextY, nextZ);
        x = nextX;
        y = nextY;
        z = nextZ;
        life++;
        return life > MAX_LIFE_MC_TICKS;
    }

    /** {@code EyeOfEnder.updateDeltaMovement(oldMovement, position, target)}. */
    private void updateDeltaMovement(double positionX, double positionY, double positionZ) {
        double horizontalX = targetX - positionX;
        double horizontalZ = targetZ - positionZ;
        double horizontalLength = Math.sqrt(horizontalX * horizontalX + horizontalZ * horizontalZ);
        double oldHorizontal = Math.sqrt(motionX * motionX + motionZ * motionZ);
        double wantedSpeed = oldHorizontal + SPEED_LERP * (horizontalLength - oldHorizontal);
        double movementY = motionY;
        if (horizontalLength < 1.0) {
            wantedSpeed *= NEAR_DAMPING;
            movementY *= NEAR_DAMPING;
        }
        double offsetY = positionY - motionY < targetY ? 1.0 : -1.0;
        double scale = wantedSpeed / horizontalLength;
        motionX = horizontalX * scale;
        motionY = movementY + (offsetY - movementY) * VERTICAL_PULL;
        motionZ = horizontalZ * scale;
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public double motionX() { return motionX; }
    public double motionY() { return motionY; }
    public double motionZ() { return motionZ; }
    public double targetX() { return targetX; }
    public double targetY() { return targetY; }
    public double targetZ() { return targetZ; }
    public int life() { return life; }
    public boolean surviveAfterDeath() { return surviveAfterDeath; }
}
