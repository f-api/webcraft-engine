package com.gameexpert.engine.dragon;

/**
 * [DRAGON] 드래곤전이 쓰는 바닐라 {@code net.minecraft.util.Mth} 의 부분 이식(핀 26.3 javap).
 * 정적판 {@code client/src/backend/standalone/dragon/DragonMath.ts} 와 줄 단위 사본이다 — float 연산은
 * 정적판에서 {@code Math.fround} 로 같은 자리에서 반올림한다.
 *
 * <ul>
 * <li>{@code Mth.sin/cos}: 65536 칸 표 {@code SIN[i] = (float) Math.sin(i / 10430.378350470453)},
 * 색인 {@code (int)((long)(d * 10430.378350470453 [+ 16384]) & 65535)}.</li>
 * <li>{@code Mth.atan2}: {@code fastInvSqrt} 와 257 칸 {@code ASIN_TAB/COS_TAB} 표(바닐라와 같은 근사).</li>
 * </ul>
 */
public final class DragonMath {
    private static final double SIN_SCALE = 10430.378350470453;
    private static final float[] SIN = new float[65536];
    private static final double FRAC_BIAS = Double.longBitsToDouble(4805340802404319232L);
    private static final double[] ASIN_TAB = new double[257];
    private static final double[] COS_TAB = new double[257];

    static {
        for (int i = 0; i < SIN.length; i++) SIN[i] = (float) Math.sin(i / SIN_SCALE);
        for (int i = 0; i < 257; i++) {
            double value = i / 256.0;
            double asin = Math.asin(value);
            COS_TAB[i] = Math.cos(asin);
            ASIN_TAB[i] = asin;
        }
    }

    /** {@code (float) Math.PI / 180} 와 같은 바닐라 float 상수 0.017453292F. */
    public static final float DEG_TO_RAD = 0.017453292F;
    /** 바닐라 float 상수 57.295776F. */
    public static final float RAD_TO_DEG = 57.295776F;

    private DragonMath() {
    }

    public static float sin(double value) {
        return SIN[(int) ((long) (value * SIN_SCALE) & 65535L)];
    }

    public static float cos(double value) {
        return SIN[(int) ((long) (value * SIN_SCALE + 16384.0) & 65535L)];
    }

    public static float wrapDegrees(float value) {
        float wrapped = value % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }

    public static double wrapDegrees(double value) {
        double wrapped = value % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    public static int floor(double value) {
        return (int) Math.floor(value);
    }

    /** 바닐라 {@code Mth.fastInvSqrt}(뉴턴 1회). */
    public static double fastInvSqrt(double value) {
        double half = 0.5 * value;
        long bits = Double.doubleToRawLongBits(value);
        bits = 6910469410427058090L - (bits >> 1);
        double x = Double.longBitsToDouble(bits);
        return x * (1.5 - half * x * x);
    }

    /** 바닐라 {@code Mth.atan2(y, x)}. */
    public static double atan2(double y, double x) {
        double lengthSq = x * x + y * y;
        if (Double.isNaN(lengthSq)) return Double.NaN;
        boolean negY = y < 0.0;
        if (negY) y = -y;
        boolean negX = x < 0.0;
        if (negX) x = -x;
        boolean steep = y > x;
        if (steep) {
            double swap = x;
            x = y;
            y = swap;
        }
        double inv = fastInvSqrt(lengthSq);
        x *= inv;
        y *= inv;
        double biased = FRAC_BIAS + y;
        int index = (int) Double.doubleToRawLongBits(biased);
        double asin = ASIN_TAB[index];
        double cos = COS_TAB[index];
        double frac = biased - FRAC_BIAS;
        double delta = y * cos - x * frac;
        double correction = (6.0 + delta * delta) * delta * 0.16666666666666666;
        double angle = asin + correction;
        if (steep) angle = 1.5707963267948966 - angle;
        if (negX) angle = 3.141592653589793 - angle;
        if (negY) angle = -angle;
        return angle;
    }

    /** {@code Mth.lerp}. */
    public static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }
}
