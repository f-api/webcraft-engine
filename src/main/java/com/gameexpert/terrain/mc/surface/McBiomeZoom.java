package com.gameexpert.terrain.mc.surface;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Vanilla {@code BiomeManager}의 fuzzy zoom이다. {@code SurfaceSystem#buildSurface}는 셀마다
 * {@code BiomeManager#getBiome(BlockPos)}로 바이옴을 다시 읽으므로, surface rule은 quart 격자
 * 자체가 아니라 이 zoom이 고른 corner의 noise 바이옴을 본다.
 *
 * <p>선택은 8개 quart corner 중 최소 fiddled distance이며 y offset이 블록마다 달라지므로 같은
 * quart 안에서도 corner가 바뀔 수 있다. 8개 corner 바이옴이 모두 같으면 결과가 corner와
 * 무관하므로 호출자는 거리 계산을 건너뛸 수 있다.</p>
 */
public final class McBiomeZoom {
    private McBiomeZoom() {}

    /** {@code BiomeManager#obfuscateSeed}다. */
    public static long zoomSeed(long worldSeed) {
        byte[] input = new byte[Long.BYTES];
        for (int index = 0; index < input.length; index++) {
            input[index] = (byte) (worldSeed >>> (index * 8));
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input);
            long result = 0;
            for (int index = Long.BYTES - 1; index >= 0; index--) {
                result = result << 8 | hash[index] & 0xffL;
            }
            return result;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /** 선택된 quart corner index다. bit 4 = +x, bit 2 = +y, bit 1 = +z. */
    public static int corner(long zoomSeed, int blockX, int blockY, int blockZ) {
        int shiftedX = blockX - 2;
        int shiftedY = blockY - 2;
        int shiftedZ = blockZ - 2;
        int quartX = shiftedX >> 2;
        int quartY = shiftedY >> 2;
        int quartZ = shiftedZ >> 2;
        double offsetX = (shiftedX & 3) / 4.0;
        double offsetY = (shiftedY & 3) / 4.0;
        double offsetZ = (shiftedZ & 3) / 4.0;
        int bestCorner = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            boolean lowX = (corner & 4) == 0;
            boolean lowY = (corner & 2) == 0;
            boolean lowZ = (corner & 1) == 0;
            double distance = fiddledDistance(zoomSeed,
                    lowX ? quartX : quartX + 1,
                    lowY ? quartY : quartY + 1,
                    lowZ ? quartZ : quartZ + 1,
                    lowX ? offsetX : offsetX - 1.0,
                    lowY ? offsetY : offsetY - 1.0,
                    lowZ ? offsetZ : offsetZ - 1.0);
            if (bestDistance > distance) {
                bestCorner = corner;
                bestDistance = distance;
            }
        }
        return bestCorner;
    }

    private static double fiddledDistance(long seed, int x, int y, int z,
            double offsetX, double offsetY, double offsetZ) {
        long mixed = lcgNext(seed, x);
        mixed = lcgNext(mixed, y);
        mixed = lcgNext(mixed, z);
        mixed = lcgNext(mixed, x);
        mixed = lcgNext(mixed, y);
        mixed = lcgNext(mixed, z);
        double dx = offsetX + fiddle(mixed);
        mixed = lcgNext(mixed, seed);
        double dy = offsetY + fiddle(mixed);
        mixed = lcgNext(mixed, seed);
        double dz = offsetZ + fiddle(mixed);
        return dx * dx + dy * dy + dz * dz;
    }

    private static long lcgNext(long state, long salt) {
        return state * (state * 6364136223846793005L + 1442695040888963407L) + salt;
    }

    private static double fiddle(long state) {
        return (Math.floorMod(state >> 24, 1024L) / 1024.0 - 0.5) * 0.9;
    }
}
