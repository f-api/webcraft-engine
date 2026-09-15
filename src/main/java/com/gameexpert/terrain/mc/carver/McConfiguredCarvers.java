package com.gameexpert.terrain.mc.carver;

import static com.gameexpert.terrain.Blocks.*;

import com.gameexpert.terrain.mc.LegacyRand;
import com.gameexpert.terrain.mc.aquifer.McAquifer;
import java.util.Arrays;

/**
 * Minecraft Java 26.3-snapshot-7 Overworld configured cave/canyon carvers.
 *
 * <p>The three default carvers are evaluated for every source chunk in the exact biome-settings
 * order. Geometry is accumulated before the aquifer/material pass, matching the 26.3
 * {@code CarvingMask} lifecycle. The source is the official server JAR
 * {@code 06157fedd67ff4dd6e0e6fa4a9dd0af296f0dd61} (inner JAR
 * {@code 2f1ef79f3cad10138ad18da45b265fe656624026}); the unmodified cave,
 * cave-extra-underground, and canyon JSON SHA-256 values are respectively
 * {@code b7f73ae43ee71e7d5d0aa6334759c20821203822b42d6e504fcc6647fddeb085},
 * {@code 14e5cb9c14e618b11ed50c6695debbc1a635885891ef7d8e810bff3645e18858}, and
 * {@code 0f572dc85d7c83612d9f56a0cf600a4a8cd9575f7473f0ea6355611c04bf620f}.</p>
 */
public final class McConfiguredCarvers {
    private static final int SOURCE_CHUNK_RADIUS = 8;
    private static final int CARVER_RANGE = 4;
    private static final int MAX_TUNNEL_LENGTH = (CARVER_RANGE * 2 - 1) * 16;
    private static final int TOP_CARVE_GUARD = 7;
    private static final float PI = (float) Math.PI;
    private static final float HALF_PI = PI / 2.0F;
    private static final float TWO_PI = PI * 2.0F;
    private static final double SIN_SCALE = 10430.378350470453D;
    private static final float[] SIN = createSinTable();

    private static final CaveConfig CAVE = new CaveConfig(
            0.15F, MIN_Y + 8, 180,
            0.1F, 0.9F,
            0.7F, 1.4F,
            0.8F, 1.3F,
            -1.0F, -0.4F);
    private static final CaveConfig EXTRA_UNDERGROUND = new CaveConfig(
            0.07F, MIN_Y + 8, 47,
            0.1F, 0.9F,
            0.7F, 1.4F,
            0.8F, 1.3F,
            -1.0F, -0.4F);
    private static final CanyonConfig CANYON = new CanyonConfig(
            0.01F, 10, 67,
            -0.125F, 0.125F);

    private McConfiguredCarvers() {
    }

    public static void carve(long worldSeed, int targetChunkX, int targetChunkZ,
            McAquifer aquifer, Target target) {
        CarvingContext context =
                new CarvingContext(targetChunkX, targetChunkZ, aquifer, target);
        LegacyRand random = new LegacyRand(0L);
        for (int offsetX = -SOURCE_CHUNK_RADIUS; offsetX <= SOURCE_CHUNK_RADIUS; offsetX++) {
            for (int offsetZ = -SOURCE_CHUNK_RADIUS; offsetZ <= SOURCE_CHUNK_RADIUS; offsetZ++) {
                int sourceChunkX = targetChunkX + offsetX;
                int sourceChunkZ = targetChunkZ + offsetZ;

                setLargeFeatureSeed(random, worldSeed, 0, sourceChunkX, sourceChunkZ);
                if (random.nextFloat() <= CAVE.probability) {
                    carveCave(context, CAVE, random, sourceChunkX, sourceChunkZ);
                }

                setLargeFeatureSeed(random, worldSeed, 1, sourceChunkX, sourceChunkZ);
                if (random.nextFloat() <= EXTRA_UNDERGROUND.probability) {
                    carveCave(context, EXTRA_UNDERGROUND, random, sourceChunkX, sourceChunkZ);
                }

                setLargeFeatureSeed(random, worldSeed, 2, sourceChunkX, sourceChunkZ);
                if (random.nextFloat() <= CANYON.probability) {
                    carveCanyon(context, CANYON, random, sourceChunkX, sourceChunkZ);
                }
            }
        }
        context.applyMask();
    }

    private static void setLargeFeatureSeed(LegacyRand random, long worldSeed,
            int carverIndex, int sourceChunkX, int sourceChunkZ) {
        long indexedSeed = worldSeed + carverIndex;
        random.setSeed(indexedSeed);
        long xSeed = random.nextLong();
        long zSeed = random.nextLong();
        random.setSeed((long) sourceChunkX * xSeed
                ^ (long) sourceChunkZ * zSeed
                ^ indexedSeed);
    }

    private static void carveCave(CarvingContext context, CaveConfig config,
            LegacyRand random, int sourceChunkX, int sourceChunkZ) {
        int attempts = random.nextInt(
                random.nextInt(random.nextInt(15) + 1) + 1);
        for (int attempt = 0; attempt < attempts; attempt++) {
            double x = sourceChunkX * 16 + random.nextInt(16);
            double y = nextIntInclusive(random, config.minY, config.maxY);
            double z = sourceChunkZ * 16 + random.nextInt(16);
            double horizontalMultiplier = uniform(random,
                    config.horizontalMin, config.horizontalMax);
            double verticalMultiplier = uniform(random,
                    config.verticalMin, config.verticalMax);
            double floorLevel = uniform(random, config.floorMin, config.floorMax);
            int tunnels = 1;
            if (random.nextInt(4) == 0) {
                double roomYScale = uniform(random, config.yScaleMin, config.yScaleMax);
                float roomWidth = 1.0F + random.nextFloat() * 6.0F;
                createRoom(context, x, y, z, roomWidth, roomYScale, floorLevel);
                tunnels += random.nextInt(4);
            }
            for (int tunnel = 0; tunnel < tunnels; tunnel++) {
                float yaw = random.nextFloat() * TWO_PI;
                float pitch = (random.nextFloat() - 0.5F) / 4.0F;
                float width = caveThickness(random);
                int length = MAX_TUNNEL_LENGTH - random.nextInt(MAX_TUNNEL_LENGTH / 4);
                createTunnel(context, random.nextLong(), x, y, z,
                        horizontalMultiplier, verticalMultiplier,
                        width, yaw, pitch, 0, length, 1.0D, floorLevel);
            }
        }
    }

    private static float caveThickness(LegacyRand random) {
        float width = random.nextFloat() * 2.0F + random.nextFloat();
        if (random.nextInt(10) == 0) {
            width *= random.nextFloat() * random.nextFloat() * 3.0F + 1.0F;
        }
        return width;
    }

    private static void createRoom(CarvingContext context,
            double x, double y, double z, float width, double yScale, double floorLevel) {
        double horizontalRadius = (double) 1.5F + (double) (sin(HALF_PI) * width);
        double verticalRadius = horizontalRadius * yScale;
        carveEllipsoid(context, x + 1.0D, y, z,
                horizontalRadius, verticalRadius,
                (normalizedX, normalizedY, normalizedZ, blockY) ->
                        normalizedY <= floorLevel
                                || normalizedX * normalizedX
                                + normalizedY * normalizedY
                                + normalizedZ * normalizedZ >= 1.0D);
    }

    private static void createTunnel(CarvingContext context, long tunnelSeed,
            double x, double y, double z,
            double horizontalMultiplier, double verticalMultiplier,
            float width, float yaw, float pitch,
            int start, int end, double branchVerticalMultiplier, double floorLevel) {
        LegacyRand random = new LegacyRand(tunnelSeed);
        int branchPoint = random.nextInt(end / 2) + end / 4;
        boolean gentlePitch = random.nextInt(6) == 0;
        float yawVelocity = 0.0F;
        float pitchVelocity = 0.0F;

        for (int step = start; step < end; step++) {
            double horizontalRadius =
                    (double) 1.5F + (double) (sin(PI * (float) step / (float) end) * width);
            double verticalRadius = horizontalRadius * branchVerticalMultiplier;
            float pitchCosine = cos(pitch);
            x += (double) (cos(yaw) * pitchCosine);
            y += (double) sin(pitch);
            z += (double) (sin(yaw) * pitchCosine);
            pitch *= gentlePitch ? 0.92F : 0.7F;
            pitch += pitchVelocity * 0.1F;
            yaw += yawVelocity * 0.1F;
            pitchVelocity *= 0.9F;
            yawVelocity *= 0.75F;
            pitchVelocity +=
                    (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0F;
            yawVelocity +=
                    (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0F;

            if (step == branchPoint && width > 1.0F) {
                createTunnel(context, random.nextLong(), x, y, z,
                        horizontalMultiplier, verticalMultiplier,
                        random.nextFloat() * 0.5F + 0.5F,
                        yaw - HALF_PI, pitch / 3.0F,
                        step, end, 1.0D, floorLevel);
                createTunnel(context, random.nextLong(), x, y, z,
                        horizontalMultiplier, verticalMultiplier,
                        random.nextFloat() * 0.5F + 0.5F,
                        yaw + HALF_PI, pitch / 3.0F,
                        step, end, 1.0D, floorLevel);
                return;
            }
            if (random.nextInt(4) == 0) continue;
            if (!canReach(context, x, z, step, end, width)) return;

            carveEllipsoid(context, x, y, z,
                    horizontalRadius * horizontalMultiplier,
                    verticalRadius * verticalMultiplier,
                    (normalizedX, normalizedY, normalizedZ, blockY) ->
                            normalizedY <= floorLevel
                                    || normalizedX * normalizedX
                                    + normalizedY * normalizedY
                                    + normalizedZ * normalizedZ >= 1.0D);
        }
    }

    private static void carveCanyon(CarvingContext context, CanyonConfig config,
            LegacyRand random, int sourceChunkX, int sourceChunkZ) {
        double x = sourceChunkX * 16 + random.nextInt(16);
        double y = nextIntInclusive(random, config.minY, config.maxY);
        double z = sourceChunkZ * 16 + random.nextInt(16);
        float yaw = random.nextFloat() * TWO_PI;
        float pitch = uniform(random, config.rotationMin, config.rotationMax);
        double yScale = 3.0D;
        float thickness = trapezoid(random, 0.0F, 6.0F, 2.0F);
        int length = (int) ((float) MAX_TUNNEL_LENGTH
                * uniform(random, 0.75F, 1.0F));
        LegacyRand shapeRandom = new LegacyRand(random.nextLong());
        float[] widthFactors = canyonWidthFactors(shapeRandom);
        float yawVelocity = 0.0F;
        float pitchVelocity = 0.0F;

        for (int step = 0; step < length; step++) {
            double horizontalRadius =
                    (double) 1.5F
                    + (double) (sin((float) step * PI / (float) length) * thickness);
            double verticalRadius = horizontalRadius * yScale;
            horizontalRadius *= (double) uniform(shapeRandom, 0.75F, 1.0F);
            verticalRadius = updateCanyonVerticalRadius(
                    shapeRandom, verticalRadius, length, step);
            float pitchCosine = cos(pitch);
            x += (double) (cos(yaw) * pitchCosine);
            y += (double) sin(pitch);
            z += (double) (sin(yaw) * pitchCosine);
            pitch *= 0.7F;
            pitch += pitchVelocity * 0.05F;
            yaw += yawVelocity * 0.05F;
            pitchVelocity *= 0.8F;
            yawVelocity *= 0.5F;
            pitchVelocity +=
                    (shapeRandom.nextFloat() - shapeRandom.nextFloat())
                    * shapeRandom.nextFloat() * 2.0F;
            yawVelocity +=
                    (shapeRandom.nextFloat() - shapeRandom.nextFloat())
                    * shapeRandom.nextFloat() * 4.0F;
            if (shapeRandom.nextInt(4) == 0) continue;
            if (!canReach(context, x, z, step, length, thickness)) return;

            carveEllipsoid(context, x, y, z,
                    horizontalRadius, verticalRadius,
                    (normalizedX, normalizedY, normalizedZ, blockY) -> {
                        int factorIndex = blockY - MIN_Y - 1;
                        return (normalizedX * normalizedX + normalizedZ * normalizedZ)
                                * (double) widthFactors[factorIndex]
                                + normalizedY * normalizedY / 6.0D >= 1.0D;
                    });
        }
    }

    private static float[] canyonWidthFactors(LegacyRand random) {
        float[] factors = new float[MAX_Y - MIN_Y + 1];
        float current = 1.0F;
        for (int index = 0; index < factors.length; index++) {
            if (index == 0 || random.nextInt(3) == 0) {
                current = 1.0F + random.nextFloat() * random.nextFloat();
            }
            factors[index] = current * current;
        }
        return factors;
    }

    private static double updateCanyonVerticalRadius(
            LegacyRand random, double verticalRadius, int length, int step) {
        float center = 1.0F
                - Math.abs(0.5F - (float) step / (float) length) * 2.0F;
        float factor = 1.0F + 0.0F * center;
        return (double) factor * verticalRadius
                * (double) uniform(random, 0.75F, 1.0F);
    }

    private static boolean canReach(CarvingContext context,
            double x, double z, int step, int end, float width) {
        double deltaX = x - context.centerX;
        double deltaZ = z - context.centerZ;
        double remaining = end - step;
        double reach = (double) (width + 2.0F + 16.0F);
        return deltaX * deltaX + deltaZ * deltaZ
                - remaining * remaining <= reach * reach;
    }

    private static boolean carveEllipsoid(CarvingContext context,
            double centerX, double centerY, double centerZ,
            double horizontalRadius, double verticalRadius, SkipChecker skip) {
        double reach = 16.0D + horizontalRadius * 2.0D;
        if (Math.abs(centerX - context.centerX) > reach
                || Math.abs(centerZ - context.centerZ) > reach) {
            return false;
        }

        int minX = Math.max(floor(centerX - horizontalRadius)
                - context.minBlockX - 1, 0);
        int maxX = Math.min(floor(centerX + horizontalRadius)
                - context.minBlockX, 15);
        int minY = Math.max(floor(centerY - verticalRadius) - 1, MIN_Y + 1);
        int maxY = Math.min(floor(centerY + verticalRadius) + 1,
                MAX_Y - TOP_CARVE_GUARD);
        int minZ = Math.max(floor(centerZ - horizontalRadius)
                - context.minBlockZ - 1, 0);
        int maxZ = Math.min(floor(centerZ + horizontalRadius)
                - context.minBlockZ, 15);
        boolean carved = false;

        for (int localX = minX; localX <= maxX; localX++) {
            int worldX = context.minBlockX + localX;
            double normalizedX =
                    ((double) worldX + 0.5D - centerX) / horizontalRadius;
            for (int localZ = minZ; localZ <= maxZ; localZ++) {
                double normalizedZ =
                        ((double) context.minBlockZ + localZ + 0.5D - centerZ)
                                / horizontalRadius;
                if (normalizedX * normalizedX
                        + normalizedZ * normalizedZ >= 1.0D) {
                    continue;
                }
                for (int blockY = maxY; blockY > minY; blockY--) {
                    double normalizedY =
                            ((double) blockY - 0.5D - centerY) / verticalRadius;
                    if (skip.shouldSkip(
                            normalizedX, normalizedY, normalizedZ, blockY)) {
                        continue;
                    }
                    if (!context.mark(localX, blockY, localZ)) continue;
                    carved = true;
                }
            }
        }
        return carved;
    }

    private static int blockIndex(int localX, int y, int localZ) {
        return localX + localZ * 16 + (y - MIN_Y) * 256;
    }

    private static int nextIntInclusive(LegacyRand random, int minimum, int maximum) {
        return minimum + random.nextInt(maximum - minimum + 1);
    }

    private static float uniform(LegacyRand random, float minimum, float maximum) {
        return random.nextFloat() * (maximum - minimum) + minimum;
    }

    private static float trapezoid(LegacyRand random,
            float minimum, float maximum, float plateau) {
        float range = maximum - minimum;
        float slope = (range - plateau) / 2.0F;
        float plateauRange = range - slope;
        return minimum
                + random.nextFloat() * plateauRange
                + random.nextFloat() * slope;
    }

    private static int floor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private static float sin(double value) {
        return SIN[(int) ((long) (value * SIN_SCALE) & 65535L)];
    }

    private static float cos(double value) {
        return SIN[(int) ((long) (value * SIN_SCALE + 16384.0D) & 65535L)];
    }

    private static float[] createSinTable() {
        float[] values = new float[65536];
        for (int index = 0; index < values.length; index++) {
            values[index] = (float) Math.sin(
                    (double) index * Math.PI * 2.0D / 65536.0D);
        }
        return values;
    }

    public interface Target {
        int block(int localX, int y, int localZ);
        void setBlock(int localX, int y, int localZ, int block);
    }

    @FunctionalInterface
    private interface SkipChecker {
        boolean shouldSkip(
                double normalizedX, double normalizedY, double normalizedZ, int blockY);
    }

    private static final class CarvingContext {
        private final int minBlockX;
        private final int minBlockZ;
        private final int centerX;
        private final int centerZ;
        private final McAquifer aquifer;
        private final Target target;
        private final boolean[] mask = new boolean[CHUNK_BLOCKS];
        private final int[] minimumMaskedY = new int[CHUNK_X * CHUNK_Z];
        private final int[] maximumMaskedY = new int[CHUNK_X * CHUNK_Z];

        private CarvingContext(int targetChunkX, int targetChunkZ,
                McAquifer aquifer, Target target) {
            minBlockX = targetChunkX * 16;
            minBlockZ = targetChunkZ * 16;
            centerX = minBlockX + 8;
            centerZ = minBlockZ + 8;
            this.aquifer = aquifer;
            this.target = target;
            Arrays.fill(minimumMaskedY, MAX_Y + 1);
            Arrays.fill(maximumMaskedY, MIN_Y - 1);
        }

        private boolean mark(int localX, int blockY, int localZ) {
            int maskIndex = blockIndex(localX, blockY, localZ);
            if (mask[maskIndex]) return false;
            mask[maskIndex] = true;
            int columnIndex = localX * CHUNK_Z + localZ;
            minimumMaskedY[columnIndex] = Math.min(minimumMaskedY[columnIndex], blockY);
            maximumMaskedY[columnIndex] = Math.max(maximumMaskedY[columnIndex], blockY);
            return true;
        }

        private void applyMask() {
            for (int localX = 0; localX < CHUNK_X; localX++) {
                int worldX = minBlockX + localX;
                for (int localZ = 0; localZ < CHUNK_Z; localZ++) {
                    int columnIndex = localX * CHUNK_Z + localZ;
                    int minimumY = minimumMaskedY[columnIndex];
                    int maximumY = maximumMaskedY[columnIndex];
                    if (maximumY < minimumY) continue;
                    int worldZ = minBlockZ + localZ;
                    // Official 26.3 CarvingMask.visit walks CONTIGUOUS set-bit segments and
                    // NoiseBasedChunkGenerator#applyCarvingMask creates its reachedSurface flag
                    // once per visited segment, so an unmasked cell ends the segment and the
                    // remembered surface block must not survive the gap.
                    int surfaceBlock = AIR;
                    for (int blockY = maximumY; blockY >= minimumY; blockY--) {
                        if (!mask[blockIndex(localX, blockY, localZ)]) {
                            surfaceBlock = AIR;
                            continue;
                        }

                        int current = target.block(localX, blockY, localZ);
                        if (current == GRASS || current == MYCELIUM) {
                            surfaceBlock = current;
                        }
                        // Official applyCarvingMask delegates every carved cell to the aquifer;
                        // the global picker itself selects lava below -54.
                        int replacement = aquifer.blockForDensity(
                                worldX, blockY, worldZ, 0.0D);
                        if (replacement == STONE) continue;
                        target.setBlock(localX, blockY, localZ, replacement);

                        if (surfaceBlock != AIR && target.block(
                                localX, blockY - 1, localZ) == DIRT) {
                            target.setBlock(localX, blockY - 1, localZ, surfaceBlock);
                        }
                    }
                }
            }
        }
    }

    private static final class CaveConfig {
        private final float probability;
        private final int minY;
        private final int maxY;
        private final float yScaleMin;
        private final float yScaleMax;
        private final float horizontalMin;
        private final float horizontalMax;
        private final float verticalMin;
        private final float verticalMax;
        private final float floorMin;
        private final float floorMax;

        private CaveConfig(float probability, int minY, int maxY,
                float yScaleMin, float yScaleMax,
                float horizontalMin, float horizontalMax,
                float verticalMin, float verticalMax,
                float floorMin, float floorMax) {
            this.probability = probability;
            this.minY = minY;
            this.maxY = maxY;
            this.yScaleMin = yScaleMin;
            this.yScaleMax = yScaleMax;
            this.horizontalMin = horizontalMin;
            this.horizontalMax = horizontalMax;
            this.verticalMin = verticalMin;
            this.verticalMax = verticalMax;
            this.floorMin = floorMin;
            this.floorMax = floorMax;
        }
    }

    private static final class CanyonConfig {
        private final float probability;
        private final int minY;
        private final int maxY;
        private final float rotationMin;
        private final float rotationMax;

        private CanyonConfig(float probability, int minY, int maxY,
                float rotationMin, float rotationMax) {
            this.probability = probability;
            this.minY = minY;
            this.maxY = maxY;
            this.rotationMin = rotationMin;
            this.rotationMax = rotationMax;
        }
    }
}
