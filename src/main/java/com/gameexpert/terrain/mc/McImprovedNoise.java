package com.gameexpert.terrain.mc;

/** Minecraft의 단일 ImprovedNoise 옥타브를 구현한다. */
public final class McImprovedNoise {
    private final byte[] permutation = new byte[256];
    private final double xo;
    private final double yo;
    private final double zo;

    public McImprovedNoise(McRandom random) {
        xo = random.nextDouble() * 256.0;
        yo = random.nextDouble() * 256.0;
        zo = random.nextDouble() * 256.0;
        for (int i = 0; i < 256; i++) {
            permutation[i] = (byte) i;
        }
        for (int i = 0; i < 256; i++) {
            int offset = random.nextInt(256 - i);
            byte value = permutation[i];
            permutation[i] = permutation[i + offset];
            permutation[i + offset] = value;
        }
    }

    public double noise(double x, double y, double z) {
        return noise(x, y, z, 0.0, 0.0);
    }

    public double noise(double x, double y, double z, double yScale, double yMax) {
        double shiftedX = x + xo;
        double shiftedY = y + yo;
        double shiftedZ = z + zo;
        int cellX = floor(shiftedX);
        int cellY = floor(shiftedY);
        int cellZ = floor(shiftedZ);
        double localX = shiftedX - cellX;
        double localY = shiftedY - cellY;
        double localZ = shiftedZ - cellZ;
        double yOffset = 0.0;
        if (yScale != 0.0) {
            double clampedY = yMax >= 0.0 && yMax < localY ? yMax : localY;
            yOffset = Math.floor(clampedY / yScale + 1.0E-7) * yScale;
        }
        return sampleAndLerp(cellX, cellY, cellZ, localX, localY - yOffset, localZ, localY);
    }

    public double xo() {
        return xo;
    }

    public double yo() {
        return yo;
    }

    public double zo() {
        return zo;
    }

    private double sampleAndLerp(int cellX, int cellY, int cellZ,
                                 double x, double y, double z, double originalY) {
        int px = lookup(cellX);
        int px1 = lookup(cellX + 1);
        int pxy = lookup(px + cellY);
        int pxy1 = lookup(px + cellY + 1);
        int px1y = lookup(px1 + cellY);
        int px1y1 = lookup(px1 + cellY + 1);
        double g000 = gradient(lookup(pxy + cellZ), x, y, z);
        double g100 = gradient(lookup(px1y + cellZ), x - 1.0, y, z);
        double g010 = gradient(lookup(pxy1 + cellZ), x, y - 1.0, z);
        double g110 = gradient(lookup(px1y1 + cellZ), x - 1.0, y - 1.0, z);
        double g001 = gradient(lookup(pxy + cellZ + 1), x, y, z - 1.0);
        double g101 = gradient(lookup(px1y + cellZ + 1), x - 1.0, y, z - 1.0);
        double g011 = gradient(lookup(pxy1 + cellZ + 1), x, y - 1.0, z - 1.0);
        double g111 = gradient(lookup(px1y1 + cellZ + 1), x - 1.0, y - 1.0, z - 1.0);
        double u = fade(x);
        double v = fade(originalY);
        double w = fade(z);
        double x00 = lerp(u, g000, g100);
        double x10 = lerp(u, g010, g110);
        double x01 = lerp(u, g001, g101);
        double x11 = lerp(u, g011, g111);
        double y0 = lerp(v, x00, x10);
        double y1 = lerp(v, x01, x11);
        return lerp(w, y0, y1);
    }

    private int lookup(int index) {
        return permutation[index & 255] & 255;
    }

    private static int floor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static double gradient(int hash, double x, double y, double z) {
        // cubiomes의 분기별 두 항 덧셈 순서는 불필요한 0 곱셈의 반올림 차이를 피한다.
        return switch (hash & 15) {
            case 0, 12 -> x + y;
            case 1, 14 -> -x + y;
            case 2 -> x - y;
            case 3 -> -x - y;
            case 4 -> x + z;
            case 5 -> -x + z;
            case 6 -> x - z;
            case 7 -> -x - z;
            case 8 -> y + z;
            case 9, 13 -> -y + z;
            case 10 -> y - z;
            case 11, 15 -> -y - z;
            default -> throw new AssertionError();
        };
    }

    private static double fade(double value) {
        return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double weight, double first, double second) {
        return first + weight * (second - first);
    }
}
