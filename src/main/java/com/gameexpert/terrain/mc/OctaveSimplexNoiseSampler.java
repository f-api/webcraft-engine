package com.gameexpert.terrain.mc;

import java.util.Arrays;

/** Minecraft's legacy {@code OctaveSimplexNoiseSampler}, backed by {@link LegacyRand}. */
public final class OctaveSimplexNoiseSampler {
    private final SimplexNoiseSampler[] octaveSamplers;
    private final double lacunarity;
    private final double persistence;

    /**
     * Creates the sampler for a set of integer octaves, such as {@code [-2, -1, 0]}.
     * Missing octaves consume their exact legacy random budget.
     */
    public OctaveSimplexNoiseSampler(LegacyRand random, int[] octaves) {
        if (octaves == null || octaves.length == 0) {
            throw new IllegalArgumentException("octaves must not be empty");
        }
        int minOctave = Arrays.stream(octaves).min().orElseThrow();
        int maxOctave = Arrays.stream(octaves).max().orElseThrow();
        int negativeOctaves = -minOctave;
        int octaveCount = negativeOctaves + maxOctave + 1;
        boolean[] present = new boolean[octaveCount];
        for (int octave : octaves) {
            present[maxOctave - octave] = true;
        }

        octaveSamplers = new SimplexNoiseSampler[octaveCount];
        SimplexNoiseSampler initialSampler = new SimplexNoiseSampler(random);
        for (int index = maxOctave + 1; index < octaveCount; index++) {
            if (index >= 0 && present[index]) {
                octaveSamplers[index] = new SimplexNoiseSampler(random);
            } else {
                random.consume(262);
            }
        }
        if (maxOctave > 0) {
            long seed = (long) (initialSampler.sample3d(initialSampler.originX,
                    initialSampler.originY, initialSampler.originZ)
                    * (double) 9.223372E18F);
            random.setSeed(seed);
            for (int index = maxOctave - 1; index >= 0; index--) {
                if (present[index]) {
                    octaveSamplers[index] = new SimplexNoiseSampler(random);
                } else {
                    random.consume(262);
                }
            }
        }
        if (maxOctave >= 0 && maxOctave < octaveCount && present[maxOctave]) {
            octaveSamplers[maxOctave] = initialSampler;
        }
        lacunarity = Math.pow(2.0, maxOctave);
        persistence = 1.0 / (Math.pow(2.0, octaveCount) - 1.0);
    }

    public double sample(double x, double z) {
        return sample(x, z, false);
    }

    /** Samples the 2D legacy simplex stack; {@code useOrigin} matches vanilla's offset switch. */
    public double sample(double x, double z, boolean useOrigin) {
        double result = 0.0;
        double frequency = lacunarity;
        double amplitude = persistence;
        for (SimplexNoiseSampler sampler : octaveSamplers) {
            if (sampler != null) {
                result += sampler.sample(x * frequency + (useOrigin ? sampler.originX : 0.0),
                        z * frequency + (useOrigin ? sampler.originY : 0.0)) * amplitude;
            }
            frequency /= 2.0;
            amplitude *= 2.0;
        }
        return result;
    }

    private static final class SimplexNoiseSampler {
        private static final double SKEW = 0.5 * (Math.sqrt(3.0) - 1.0);
        private static final double UNSKEW = (3.0 - Math.sqrt(3.0)) / 6.0;
        private static final int[][] GRADIENTS = {
                {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
                {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
                {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}
        };

        private final byte[] permutation = new byte[256];
        private final double originX;
        private final double originY;
        private final double originZ;

        private SimplexNoiseSampler(LegacyRand random) {
            originX = random.nextDouble() * 256.0;
            originY = random.nextDouble() * 256.0;
            originZ = random.nextDouble() * 256.0;
            for (int index = 0; index < permutation.length; index++) {
                permutation[index] = (byte) index;
            }
            for (int index = 0; index < permutation.length; index++) {
                int offset = random.nextInt(256 - index);
                byte value = permutation[index];
                permutation[index] = permutation[index + offset];
                permutation[index + offset] = value;
            }
        }

        private double sample(double x, double z) {
            double skew = (x + z) * SKEW;
            int cellX = floor(x + skew);
            int cellZ = floor(z + skew);
            double unskew = (cellX + cellZ) * UNSKEW;
            double localX = x - (cellX - unskew);
            double localZ = z - (cellZ - unskew);
            int xOffset = localX > localZ ? 1 : 0;
            int zOffset = localX > localZ ? 0 : 1;
            double localX1 = localX - xOffset + UNSKEW;
            double localZ1 = localZ - zOffset + UNSKEW;
            double localX2 = localX - 1.0 + 2.0 * UNSKEW;
            double localZ2 = localZ - 1.0 + 2.0 * UNSKEW;
            int gradient0 = gradientIndex(cellZ, cellX);
            int gradient1 = gradientIndex(cellZ + zOffset, cellX + xOffset);
            int gradient2 = gradientIndex(cellZ + 1, cellX + 1);
            return 70.0 * (corner(gradient0, localX, localZ)
                    + corner(gradient1, localX1, localZ1)
                    + corner(gradient2, localX2, localZ2));
        }

        private double sample3d(double x, double y, double z) {
            double skew = (x + y + z) * 0.3333333333333333;
            int cellX = floor(x + skew);
            int cellY = floor(y + skew);
            int cellZ = floor(z + skew);
            double unskew = (cellX + cellY + cellZ) * 0.16666666666666666;
            double localX = x - (cellX - unskew);
            double localY = y - (cellY - unskew);
            double localZ = z - (cellZ - unskew);
            int x1;
            int y1;
            int z1;
            int x2;
            int y2;
            int z2;
            if (localX >= localY) {
                if (localY >= localZ) {
                    x1 = 1; y1 = 0; z1 = 0;
                    x2 = 1; y2 = 1; z2 = 0;
                } else if (localX >= localZ) {
                    x1 = 1; y1 = 0; z1 = 0;
                    x2 = 1; y2 = 0; z2 = 1;
                } else {
                    x1 = 0; y1 = 0; z1 = 1;
                    x2 = 1; y2 = 0; z2 = 1;
                }
            } else if (localY < localZ) {
                x1 = 0; y1 = 0; z1 = 1;
                x2 = 0; y2 = 1; z2 = 1;
            } else if (localX < localZ) {
                x1 = 0; y1 = 1; z1 = 0;
                x2 = 0; y2 = 1; z2 = 1;
            } else {
                x1 = 0; y1 = 1; z1 = 0;
                x2 = 1; y2 = 1; z2 = 0;
            }
            double localX1 = localX - x1 + 0.16666666666666666;
            double localY1 = localY - y1 + 0.16666666666666666;
            double localZ1 = localZ - z1 + 0.16666666666666666;
            double localX2 = localX - x2 + 0.3333333333333333;
            double localY2 = localY - y2 + 0.3333333333333333;
            double localZ2 = localZ - z2 + 0.3333333333333333;
            double localX3 = localX - 1.0 + 0.5;
            double localY3 = localY - 1.0 + 0.5;
            double localZ3 = localZ - 1.0 + 0.5;
            int gradient0 = gradientIndex3d(cellX, cellY, cellZ);
            int gradient1 = gradientIndex3d(cellX + x1, cellY + y1, cellZ + z1);
            int gradient2 = gradientIndex3d(cellX + x2, cellY + y2, cellZ + z2);
            int gradient3 = gradientIndex3d(cellX + 1, cellY + 1, cellZ + 1);
            return 32.0 * (corner3d(gradient0, localX, localY, localZ)
                    + corner3d(gradient1, localX1, localY1, localZ1)
                    + corner3d(gradient2, localX2, localY2, localZ2)
                    + corner3d(gradient3, localX3, localY3, localZ3));
        }

        private int gradientIndex(int z, int x) {
            return lookup(lookup(z) + x) % GRADIENTS.length;
        }

        private int gradientIndex3d(int x, int y, int z) {
            return lookup(x + lookup(y + lookup(z))) % GRADIENTS.length;
        }

        private int lookup(int index) {
            return permutation[index & 255] & 255;
        }

        private static double corner(int gradientIndex, double x, double z) {
            double distance = 0.5 - x * x - z * z;
            if (distance < 0.0) {
                return 0.0;
            }
            distance *= distance;
            int[] gradient = GRADIENTS[gradientIndex];
            return distance * distance * (gradient[0] * x + gradient[1] * z);
        }

        private static double corner3d(int gradientIndex, double x, double y, double z) {
            double distance = 0.6 - x * x - y * y - z * z;
            if (distance < 0.0) {
                return 0.0;
            }
            distance *= distance;
            int[] gradient = GRADIENTS[gradientIndex];
            return distance * distance
                    * (gradient[0] * x + gradient[1] * y + gradient[2] * z);
        }

        private static int floor(double value) {
            int integer = (int) value;
            return value < integer ? integer - 1 : integer;
        }
    }
}
