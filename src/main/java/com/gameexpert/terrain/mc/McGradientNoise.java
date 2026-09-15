package com.gameexpert.terrain.mc;

import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;

/** Minecraft 26.3의 float 기반 단일 Perlin gradient noise다. */
public final class McGradientNoise {
    private static final double WRAP = 33_554_432.0D;
    private static final int[][] GRADIENTS = {
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
        {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
        {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
        {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1}
    };

    private final byte[] permutations = new byte[256];
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;

    public McGradientNoise(McRandom random) {
        this(random::nextDouble, random::nextInt);
    }

    /** Constructs the same gradient table from a legacy 48-bit random source. */
    public McGradientNoise(LegacyRand random) {
        this(random::nextDouble, random::nextInt);
    }

    private McGradientNoise(DoubleSupplier doubles, IntUnaryOperator boundedInts) {
        offsetX = doubles.getAsDouble() * 256.0D;
        offsetY = doubles.getAsDouble() * 256.0D;
        offsetZ = doubles.getAsDouble() * 256.0D;
        for (int i = 0; i < permutations.length; i++) permutations[i] = (byte) i;
        for (int i = 0; i < permutations.length; i++) {
            int other = i + boundedInts.applyAsInt(256 - i);
            byte value = permutations[i];
            permutations[i] = permutations[other];
            permutations[other] = value;
        }
    }

    float get(double x, double y, double z) {
        double shiftedX = wrap(x) + offsetX;
        double shiftedY = wrap(y) + offsetY;
        double shiftedZ = wrap(z) + offsetZ;
        int cellX = floor(shiftedX);
        int cellY = floor(shiftedY);
        int cellZ = floor(shiftedZ);
        float localX = (float) (shiftedX - cellX);
        float localY = (float) (shiftedY - cellY);
        float localZ = (float) (shiftedZ - cellZ);
        return sampleAndLerp(cellX, cellY, cellZ, localX, localY, localZ, localY);
    }

    /** 26.3 {@code SmearedPerlinNoise.get}: quantizes Y by the layer-specific fudge scale. */
    public float getSmeared(double x, double y, double z, double fudgeYScale) {
        double shiftedX = wrap(x) + offsetX;
        double shiftedY = wrap(y) + offsetY;
        double shiftedZ = wrap(z) + offsetZ;
        int cellX = floor(shiftedX);
        int cellY = floor(shiftedY);
        int cellZ = floor(shiftedZ);
        float localX = (float) (shiftedX - cellX);
        double localY = shiftedY - cellY;
        float localZ = (float) (shiftedZ - cellZ);
        double clampedY = y >= 0.0D && y < localY ? y : localY;
        double fudgeY = Math.floor(clampedY / fudgeYScale + 1.0000000116860974E-7D)
                * fudgeYScale;
        return sampleAndLerp(cellX, cellY, cellZ, localX,
                (float) (localY - fudgeY), localZ, (float) localY);
    }

    private float sampleAndLerp(int cellX, int cellY, int cellZ,
            float x, float y, float z, float originalY) {
        int px = permute(cellX);
        int px1 = permute(cellX + 1);
        int pxy = permute(px + cellY);
        int pxy1 = permute(px + cellY + 1);
        int px1y = permute(px1 + cellY);
        int px1y1 = permute(px1 + cellY + 1);
        float g000 = gradient(permute(pxy + cellZ), x, y, z);
        float g100 = gradient(permute(px1y + cellZ), x - 1.0F, y, z);
        float g010 = gradient(permute(pxy1 + cellZ), x, y - 1.0F, z);
        float g110 = gradient(permute(px1y1 + cellZ), x - 1.0F, y - 1.0F, z);
        float g001 = gradient(permute(pxy + cellZ + 1), x, y, z - 1.0F);
        float g101 = gradient(permute(px1y + cellZ + 1), x - 1.0F, y, z - 1.0F);
        float g011 = gradient(permute(pxy1 + cellZ + 1), x, y - 1.0F, z - 1.0F);
        float g111 = gradient(permute(px1y1 + cellZ + 1), x - 1.0F, y - 1.0F, z - 1.0F);
        float sx = smoothstep(x);
        float sy = smoothstep(originalY);
        float sz = smoothstep(z);
        return lerp(sz,
                lerp(sy, lerp(sx, g000, g100), lerp(sx, g010, g110)),
                lerp(sy, lerp(sx, g001, g101), lerp(sx, g011, g111)));
    }

    private int permute(int index) {
        return permutations[index & 255] & 255;
    }

    private static float gradient(int index, float x, float y, float z) {
        int[] gradient = GRADIENTS[index & 15];
        return gradient[0] * x + gradient[1] * y + gradient[2] * z;
    }

    private static float smoothstep(float value) {
        return value * value * value * (value * (value * 6.0F - 15.0F) + 10.0F);
    }

    private static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    private static int floor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static double wrap(double value) {
        return value - Math.floor(value / WRAP + 0.5D) * WRAP;
    }
}
