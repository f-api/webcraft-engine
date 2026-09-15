package com.gameexpert.engine.redstone;

/**
 * [REDSTONE] 핀 26.3 {@code data/minecraft/timeline/day.json} 의 두 트랙과 WeatherAttributes 의 하늘빛 층.
 * 햇빛 감지기(DaylightDetectorBlock.updateSignalStrength)가 읽는 두 값만 옮긴다:
 * <ul>
 *   <li>{@code gameplay/sky_light_level}(기본 15, multiply 키프레임 133:1 · 11867:1 · 13670:0.26666668 ·
 *       22330:0.26666668, 주기 24000) → 비·뇌우 ALPHA_BLEND(4.0, 0.3125 / 0.52734375) → skyDarken
 *       = (int)(15 − 값)(Level.updateSkyBrightness).</li>
 *   <li>{@code visual/sun_angle}(키프레임 6000:360 · 6000:0, cubic_bezier [0.362, 0.241, 0.638, 0.759]).</li>
 * </ul>
 * {@code client/src/world/vanillaSkyTimeline.ts} 와 같은 float 산술을 문자 그대로 들고 있다.
 */
public final class VanillaSkyTimeline {
    private VanillaSkyTimeline() {
    }

    private static final int PERIOD = 24000;
    private static final int[] SKY_KEY_TICKS = {133, 11867, 13670, 22330};
    private static final float[] SKY_KEY_VALUES = {1.0F, 1.0F, 0.26666668F, 0.26666668F};

    /** {@code Mth.lerp(float, float, float)}. */
    private static float lerp(float alpha, float a, float b) {
        return a + alpha * (b - a);
    }

    /** 주기 트랙 선형 표본({@code KeyframeTrackSampler.sample}, easing LINEAR). */
    private static float sampleLinear(long ticks) {
        long t = ((ticks % PERIOD) + PERIOD) % PERIOD;
        int n = SKY_KEY_TICKS.length;
        // 구간: [last-PERIOD → first], [k_i → k_{i+1}] …, [last → first+PERIOD].
        int segments = n + 1;
        int seg = segments - 1;
        for (int s = 0; s < segments; s++) {
            if (t < segEndTick(s)) {
                seg = s;
                break;
            }
        }
        long startTick = segStartTick(seg);
        long endTick = segEndTick(seg);
        if (t <= startTick) return segStartValue(seg);
        if (t >= endTick) return segEndValue(seg);
        float alpha = (float) ((double) (t - startTick) / (double) (endTick - startTick));
        return lerp(alpha, segStartValue(seg), segEndValue(seg));
    }

    private static long segStartTick(int s) {
        return s == 0 ? SKY_KEY_TICKS[SKY_KEY_TICKS.length - 1] - PERIOD : SKY_KEY_TICKS[s - 1];
    }

    private static float segStartValue(int s) {
        return s == 0 ? SKY_KEY_VALUES[SKY_KEY_VALUES.length - 1] : SKY_KEY_VALUES[s - 1];
    }

    private static long segEndTick(int s) {
        return s == SKY_KEY_TICKS.length ? SKY_KEY_TICKS[0] + PERIOD : SKY_KEY_TICKS[s];
    }

    private static float segEndValue(int s) {
        return s == SKY_KEY_VALUES.length ? SKY_KEY_VALUES[0] : SKY_KEY_VALUES[s];
    }

    /** {@code Level.updateSkyBrightness} 의 skyDarken(0..15). */
    public static int vanillaSkyDarken(long dayTime, double rainLevel, double thunderLevel) {
        float value = 15.0F * sampleLinear(dayTime);
        float thunder = (float) thunderLevel;
        float rain = (float) rainLevel - thunder;
        if (rain > 0) value = lerp(rain, value, lerp(0.3125F, value, 4.0F));
        if (thunder > 0) value = lerp(thunder, value, lerp(0.52734375F, value, 4.0F));
        return (int) (15.0F - value);
    }

    private static final float[] BX = curve(0.362F, 0.638F);
    private static final float[] BY = curve(0.241F, 0.759F);

    private static float[] curve(float v1, float v2) {
        return new float[] {3.0F * v1 - 3.0F * v2 + 1.0F, -6.0F * v1 + 3.0F * v2, 3.0F * v1};
    }

    private static float sample(float[] c, float t) {
        return ((c[0] * t + c[1]) * t + c[2]) * t;
    }

    private static float gradient(float[] c, float t) {
        return (3.0F * c[0] * t + 2.0F * c[1]) * t + c[2];
    }

    /** {@code EasingType.CubicBezier.apply}. */
    private static float bezier(float x) {
        float t = x;
        for (int i = 0; i < 4; i++) {
            float error = sample(BX, t) - x;
            if (Math.abs(error) < 1.0E-5F) return sample(BY, t);
            float g = gradient(BX, t);
            if (g < 1.0E-5F) break;
            t = t - Math.max(-0.25F, Math.min(0.25F, error / g));
        }
        float t0 = 0.0F;
        float t1 = 1.0F;
        for (; t0 < t1; t = (t1 + t0) / 2.0F) {
            float error = sample(BX, t) - x;
            if (Math.abs(error) < 1.0E-5F) return sample(BY, t);
            if (error < 0) t0 = t;
            else t1 = t;
        }
        return sample(BY, t);
    }

    /** {@code EnvironmentAttributes.SUN_ANGLE}(도). */
    public static float vanillaSunAngleDegrees(long dayTime) {
        long t = ((dayTime % PERIOD) + PERIOD) % PERIOD;
        if (t < 6000) return lerp(bezier((float) ((double) (t + 18000) / PERIOD)), 0.0F, 360.0F);
        return lerp(bezier((float) ((double) (t - 6000) / PERIOD)), 0.0F, 360.0F);
    }
}
