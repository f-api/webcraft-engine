package com.gameexpert.engine.mob;

/**
 * [SULFUR-KB] 몸통 블록을 삼킨 유황 큐브의 넉백. 핀 26.3-snapshot-7 {@code SulfurCube.knockback(double,
 * double, double, DamageSource, float, boolean)}(javap) 를 그대로 옮긴다. 가해 개체가 있고
 * {@code hasBodyItem()} 일 때만 이 식을 쓰고, 아니면 일반 {@code LivingEntity.knockback} 이다.
 * <pre>
 *   h, v   = archetype.knockback_modifiers(horizontal_power, vertical_power)   // float
 *   dir    = Vec2(xRatio, zRatio) → applyHorizontalHitAngleScale(1.6f, eye, look, center)
 *   (h, v) = applyVerticalHitAnglePowerTransfer(0.5f, h, v, eye, look, center, bbHeight)
 *   (h, v) = applyVerticalPositionAnglePowerRotation(0.8f, h, v, h0, v0, attacker.position, position)
 *   f      = Mth.sqrt(damage) · (extra ? (float) strength · 0.25f : 1)
 *   h, v  ·= f; h, v ·= (float)(1 − KNOCKBACK_RESISTANCE); h ·= 0.4f; clamp(±128)
 *   delta  = (old.x − n.x·h, old.y + v·1.2, old.z − n.z·h),  n = Vec3(dir.x, 0, dir.y).normalize()
 * </pre>
 * {@code Vec2.rotate} 는 {@code Mth.sin/cos} 표(65536칸)를 쓴다. 결과 단위는 블록/MC틱이며 호출부가
 * 10 TPS 로 옮긴다(×2). 정적판 사본은 {@code StandaloneSulfurCubeKnockback.ts} 다.
 */
public final class SulfurCubeKnockback {

    private SulfurCubeKnockback() {}

    public static final float HORIZONTAL_HIT_ANGLE_SCALE = 1.6f;
    public static final float VERTICAL_HIT_ANGLE_TRANSFER = 0.5f;
    public static final float VERTICAL_POSITION_ANGLE_ROTATION = 0.8f;
    public static final float HORIZONTAL_SCALE = 0.4f;
    public static final double VERTICAL_SCALE = 1.2;
    public static final float LIMIT = 128.0f;

    private static final float[] SIN = new float[65536];
    static {
        for (int i = 0; i < SIN.length; i++) SIN[i] = (float) Math.sin(i / 10430.378350470453);
    }

    /** {@code Mth.sin(double)}. */
    public static float sin(double value) {
        return SIN[(int) ((long) (value * 10430.378350470453) & 65535L)];
    }

    /** {@code Mth.cos(double)}. */
    public static float cos(double value) {
        return SIN[(int) ((long) (value * 10430.378350470453 + 16384.0) & 65535L)];
    }

    /** 속도 변화(블록/MC틱). {@code dvx, dvz} 는 기존 속도에 더하고 {@code dvy} 도 더한다. */
    public record Impulse(double dvx, double dvy, double dvz) {}

    /**
     * @param horizontalPower archetype {@code horizontal_power}(float)
     * @param verticalPower   archetype {@code vertical_power}(float)
     * @param strength        {@code knockback} 의 첫 인수(피격 0.4, 추가 넉백은 밀치기·질주 세기)
     * @param xRatio          가해자 − 대상(피격) 또는 {@code sin(yRot)}(추가 넉백)
     * @param zRatio          가해자 − 대상(피격) 또는 {@code −cos(yRot)}(추가 넉백)
     * @param damage          피해량(float)
     * @param extra           {@code causeExtraKnockback} 호출(참이면 세기 × 0.25 를 곱한다)
     * @param knockbackResistance 대상의 {@code KNOCKBACK_RESISTANCE} 속성값([-2, 1])
     */
    public static Impulse impulse(float horizontalPower, float verticalPower, double strength,
            double xRatio, double zRatio, float damage, boolean extra, double knockbackResistance,
            double eyeX, double eyeY, double eyeZ, double lookX, double lookY, double lookZ,
            double attackerX, double attackerY, double attackerZ,
            double cubeX, double cubeY, double cubeZ, float bbHeight) {
        float h0 = horizontalPower;
        float v0 = verticalPower;
        double[] look = normalize(lookX, lookY, lookZ);
        double centerY = cubeY + bbHeight / 2.0;
        // applyHorizontalHitAngleScale(1.6f, dir, eye, look, center)
        double[] toCenter = normalize(cubeX - eyeX, centerY - eyeY, cubeZ - eyeZ);
        float angle = (float) Math.atan2(look[0] * toCenter[2] - look[2] * toCenter[0],
                look[0] * toCenter[0] + look[2] * toCenter[2]);
        float[] dir = rotate((float) xRatio, (float) zRatio, angle * HORIZONTAL_HIT_ANGLE_SCALE);
        // applyVerticalHitAnglePowerTransfer(0.5f, h, v, eye, look, center, bbHeight)
        float half = 0.5f * bbHeight;
        double[] toTop = normalize(cubeX - eyeX, centerY + half - eyeY, cubeZ - eyeZ);
        double[] toBottom = normalize(cubeX - eyeX, centerY + (double) -half - eyeY, cubeZ - eyeZ);
        float t = (float) clampedMap(look[1], toTop[1], toBottom[1], -1.0, 1.0);
        float m = Math.abs(t * VERTICAL_HIT_ANGLE_TRANSFER);
        if (t < 0.0f) m = -m;
        float h = h0 * (1.0f - m);
        float v = v0 * (1.0f + m);
        // applyVerticalPositionAnglePowerRotation(0.8f, h, v, h0, v0, attacker.position, position)
        double dy = cubeY - attackerY;
        double horizontal = Math.sqrt((cubeX - attackerX) * (cubeX - attackerX)
                + (cubeZ - attackerZ) * (cubeZ - attackerZ));
        float elevation = (float) Math.atan2(-dy, horizontal);
        float[] rotated = rotate(h, v, -elevation * VERTICAL_POSITION_ANGLE_ROTATION);
        float sx = h0 > 0.0f ? Math.abs(rotated[0]) / h0 : 0.0f;
        float sy = v0 > 0.0f ? Math.abs(rotated[1]) / v0 : 0.0f;
        float s = Math.max(sx, sy);
        if (s > 1.0f) {
            float inverse = 1.0f / s;
            rotated = new float[] {rotated[0] * inverse, rotated[1] * inverse};
        }
        h = rotated[0];
        v = rotated[1];
        float factor = (float) Math.sqrt(damage) * (extra ? (float) strength * 0.25f : 1.0f);
        h *= factor;
        v *= factor;
        h *= (float) (1.0 - knockbackResistance);
        v *= (float) (1.0 - knockbackResistance);
        h *= HORIZONTAL_SCALE;
        h = Math.max(-LIMIT, Math.min(LIMIT, h));
        v = Math.max(-LIMIT, Math.min(LIMIT, v));
        double[] n = normalize(dir[0], 0.0, dir[1]);
        return new Impulse(-(n[0] * h), v * VERTICAL_SCALE, -(n[2] * h));
    }

    /** {@code Vec2.rotate(double)}. */
    static float[] rotate(float x, float y, double radians) {
        float cos = cos(radians);
        float sin = sin(radians);
        return new float[] {x * cos - y * sin, y * cos + x * sin};
    }

    /** {@code Vec3.normalize()}: 길이 1e-5 미만이면 영벡터. */
    static double[] normalize(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 9.999999747378752E-6) return new double[] {0.0, 0.0, 0.0};
        return new double[] {x / length, y / length, z / length};
    }

    /** {@code Mth.clampedMap}. */
    static double clampedMap(double value, double fromMin, double fromMax, double toMin, double toMax) {
        double t = (value - fromMin) / (fromMax - fromMin);
        if (t < 0.0) return toMin;
        if (t > 1.0) return toMax;
        return toMin + t * (toMax - toMin);
    }

    /**
     * 이 저장소 좌표계의 시선 단위 벡터. 앞 방향은 {@code (−sin yaw, −cos yaw)} 이고 피치는 위가 양수다
     * (바닐라 {@code Entity.calculateViewVector} 의 {@code (−sin yRot·cos xRot, −sin xRot, cos yRot·cos xRot)}
     * 와 같은 벡터).
     */
    public static double[] lookVector(double yaw, double pitch) {
        double cosPitch = Math.cos(pitch);
        return new double[] {-Math.sin(yaw) * cosPitch, Math.sin(pitch), -Math.cos(yaw) * cosPitch};
    }
}
