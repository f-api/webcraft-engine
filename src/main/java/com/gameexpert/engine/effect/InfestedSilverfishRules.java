package com.gameexpert.engine.effect;

/**
 * [MACE-B2] 벌레 먹음(infested) 효과가 세우는 좀벌레의 초속도. 정본은 핀 26.3-snapshot-7
 * {@code net.minecraft.world.effect.InfestedMobEffect.spawnSilverfish}(javap):
 * <pre>
 *   angle = Mth.randomBetween(random, -π/2 f, π/2 f)          // nextFloat()·(max − min) + min
 *   v     = getLookAngle().toVector3f().mul(0.3f).mul(1, 1.5f, 1).rotateY(angle)
 *   silverfish.snapTo(x, y, z, level.random.nextFloat() · 360, 0); setDeltaMovement(v)
 *   silverfish.playSound(SILVERFISH_HURT)
 * </pre>
 * JOML {@code Vector3f.rotateY} 는 {@code x' = x·cos + z·sin}, {@code z' = −x·sin + z·cos} 이고
 * {@code cos} 는 {@code Math.cosFromSin(sin, angle)}(비 FASTMATH: {@code sqrt(1 − sin²)} 에 사분면 부호)이다.
 * 모두 float 산술이다. client {@code StandaloneInfestedRules.ts} 가 같은 식의 사본이다.
 */
public final class InfestedSilverfishRules {

    private InfestedSilverfishRules() {}

    private static final float PI_HALF_F = (float) (Math.PI * 0.5);
    private static final float PI_F = (float) Math.PI;
    private static final float PI2_F = (float) (Math.PI * 2.0);
    /** {@code InfestedMobEffect.spawnSilverfish} 의 {@code 1.5707964f}. */
    public static final float HALF_TURN = 1.5707964f;

    /** {@code Mth.randomBetween(random, -1.5707964f, 1.5707964f)}. */
    public static float angle(float roll) {
        return roll * (HALF_TURN - -HALF_TURN) + -HALF_TURN;
    }

    /** {@code level.random.nextFloat() * 360.0f}(도, 바닐라 yRot). */
    public static float yawDegrees(float roll) {
        return roll * 360.0f;
    }

    /**
     * 초속도(블록/MC틱). {@code look} 은 가해 개체의 시선 단위 벡터다(몸통 시선; 이 저장소 몹은 머리
     * 기울기가 없어 수평이다).
     *
     * @return {vx, vy, vz}
     */
    public static double[] velocity(double lookX, double lookY, double lookZ, float angle) {
        float x = (float) lookX * 0.3f;
        float y = (float) lookY * 0.3f * 1.5f;
        float z = (float) lookZ * 0.3f;
        float sin = (float) Math.sin(angle);
        float cos = cosFromSin(sin, angle);
        float rx = x * cos + z * sin;
        float rz = -x * sin + z * cos;
        return new double[] {rx, y, rz};
    }

    /** JOML {@code Math.cosFromSinInternal}(비 FASTMATH). */
    static float cosFromSin(float sin, float angle) {
        float cos = (float) Math.sqrt(1.0f - sin * sin);
        float a = angle + PI_HALF_F;
        float b = a - (int) (a / PI2_F) * PI2_F;
        if (b < 0.0) b = PI2_F + b;
        if (b >= PI_F) return -cos;
        return cos;
    }
}
