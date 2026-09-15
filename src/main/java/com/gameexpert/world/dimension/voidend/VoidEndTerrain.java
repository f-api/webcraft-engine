package com.gameexpert.world.dimension.voidend;

import com.gameexpert.terrain.mc.LegacyRand;
import com.gameexpert.terrain.mc.McGradientNoise;
import java.util.HashMap;
import java.util.Map;

/**
 * [VOID-END] 바닐라 26.3 End 기본 지형(밀도 함수)의 정확한 이식. 정적판
 * {@code client/src/world/dimensions/voidEnd/VoidEndTerrain.ts} 와 비트 단위로 같은 값을 낸다 — 두 파일은 같은
 * 순서·같은 float/double 연산을 줄 단위로 옮긴 사본이다(TS 는 float 연산마다 {@code Math.fround}).
 *
 * <p>근거(핀 26.3 client jar, javap):
 * <ul>
 * <li>{@code worldgen/noise_settings/end.json}: {@code legacy_random_source}, noise min_y 0 · height 128 ·
 * size_horizontal 2 · size_vertical 1(셀 8×4×8), final_density =
 * squeeze(interpolated(0.64 · lerp(gradient_y(4→0, 32→1), −0.234375,
 * lerp(gradient_y(56→1, 312→0), −23.4375, end/sloped_cheese)))) + beardifier(구조물 없음 = 0).</li>
 * <li>{@code density_function/end/sloped_cheese.json} = end/islands + end/base_3d_noise.</li>
 * <li>{@code density_function/end/islands.json} = max(slice_y0((clamp(100 − |p|, −100, 80) − 8) · 1/128),
 * end_outer_islands). |p| 는 {@code DistanceToPointFunction}(EUCLIDEAN, float {@code Mth.length}).</li>
 * <li>{@code EndIslandFunction(seed)}: {@code LegacyRandomSource(seed)} 에서 17292 회 소비 뒤
 * {@code SimplexNoise(random, true)}(오프셋 0). 높이는 25×25 이웃 섬 격자의 float 식.</li>
 * <li>{@code base_3d_noise.json} = {@code BlendedNoise}(xz_scale 0.25, y_scale 0.25, xz_factor 80,
 * y_factor 160, smear 4), {@code RandomState.NoiseWiringHelper} 가 {@code LegacyRandomSource(seed)} 로
 * 만든다: 최소/최대 한계 16옥타브 + 주 8옥타브의 float {@code SmearedPerlinNoise}.</li>
 * <li>{@code NoiseChunk.NoiseInterpolator}: 셀 모서리 값을 Y → X → Z 순서의 float {@code Mth.lerp} 로
 * 보간한다(델타 = 셀 안 좌표 / 셀 크기). 밀도 &gt; 0 이면 기본 블록(엔드 돌), 아니면 공기.</li>
 * </ul>
 *
 * <p>{@code SmearedPerlinNoise} 는 이미 핀 26.3 과 대조된 {@link McGradientNoise#getSmeared} 를,
 * 레거시 난수는 {@link LegacyRand} 를 그대로 쓴다. 시드는 WebCraft 의 signed int 시드를 long 으로 부호
 * 확장한 값이다 — 바닐라 월드 시드가 같은 값이면 같은 지형이다.
 */
public final class VoidEndTerrain {
    public static final int CELL_WIDTH = 8;
    public static final int CELL_HEIGHT = 4;
    public static final int NOISE_MIN_Y = 0;
    public static final int NOISE_HEIGHT = 128;
    public static final int CORNERS_Y = NOISE_HEIGHT / CELL_HEIGHT + 1;
    /** TheEndBiomeSource 결과(청크 단위). */
    public static final int BIOME_THE_END = 0;
    public static final int BIOME_HIGHLANDS = 1;
    public static final int BIOME_MIDLANDS = 2;
    public static final int BIOME_SMALL_ISLANDS = 3;
    public static final int BIOME_BARRENS = 4;

    private static final double BASE_SCALE = 684.412D;
    private static final double XZ_SCALE = 0.25D;
    private static final double Y_SCALE = 0.25D;
    private static final double XZ_FACTOR = 80.0D;
    private static final double Y_FACTOR = 160.0D;
    private static final double SMEAR = 4.0D;
    private static final double XZ_MULTIPLIER = BASE_SCALE * XZ_SCALE;
    private static final double Y_MULTIPLIER = BASE_SCALE * Y_SCALE;
    private static final double SQRT_3 = Math.sqrt(3.0D);
    private static final double F2 = 0.5D * (SQRT_3 - 1.0D);
    private static final double G2 = (3.0D - SQRT_3) / 6.0D;
    /** GradientNoise.GRADIENT 의 앞 12개(SimplexNoise 는 %12). */
    private static final int[][] GRADIENT = {
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
        {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
        {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
    };
    private static final float MUL_064 = 0.64F;

    private static volatile VoidEndTerrain cached;

    private final int seed;
    private final byte[] islandPerm = new byte[256];
    private final Stack minLimit;
    private final Stack maxLimit;
    private final Stack main;

    private VoidEndTerrain(int seed) {
        this.seed = seed;
        LegacyRand islandRandom = new LegacyRand(seed);
        islandRandom.consume(17292);
        // SimplexNoise(random, true): 오프셋 배율 0 이지만 nextDouble 세 번은 그대로 소비한다.
        islandRandom.nextDouble();
        islandRandom.nextDouble();
        islandRandom.nextDouble();
        for (int i = 0; i < 256; i++) islandPerm[i] = (byte) i;
        for (int i = 0; i < 256; i++) {
            int other = i + islandRandom.nextInt(256 - i);
            byte value = islandPerm[i];
            islandPerm[i] = islandPerm[other];
            islandPerm[other] = value;
        }
        LegacyRand blended = new LegacyRand(seed);
        double smearScale = BASE_SCALE * Y_SCALE * SMEAR;
        minLimit = new Stack(blended, -15, smearScale, 0.9999847412109375D);
        maxLimit = new Stack(blended, -15, smearScale, 0.9999847412109375D);
        main = new Stack(blended, -7, smearScale / Y_FACTOR, 12.75D);
    }

    /** 시드별 불변 노이즈 상태. 마지막 시드 하나를 공유한다(생성은 순수 함수라 스레드 안전하다). */
    public static VoidEndTerrain forSeed(int seed) {
        VoidEndTerrain current = cached;
        if (current != null && current.seed == seed) return current;
        VoidEndTerrain created = new VoidEndTerrain(seed);
        cached = created;
        return created;
    }

    /** {@code BlendedNoise.createFbm}: 주파수 1 부터 절반씩, 진폭은 base/(2^n − 1) 부터 두 배씩. */
    private static final class Stack {
        final McGradientNoise[] noise;
        final double[] frequency;
        final float[] amplitude;
        final double fudge;

        Stack(LegacyRand random, int firstOctave, double fudge, double baseAmplitude) {
            int count = -firstOctave + 1;
            noise = new McGradientNoise[count];
            frequency = new double[count];
            amplitude = new float[count];
            double layerFrequency = 1.0D;
            double layerAmplitude = baseAmplitude / (Math.pow(2.0D, count) - 1.0D);
            for (int layer = 0; layer < count; layer++) {
                noise[layer] = new McGradientNoise(random);
                frequency[layer] = layerFrequency;
                amplitude[layer] = (float) layerAmplitude;
                layerFrequency /= 2.0D;
                layerAmplitude *= 2.0D;
            }
            this.fudge = fudge;
        }

        float get(double x, double y, double z) {
            float result = 0.0F;
            for (int layer = 0; layer < noise.length; layer++) {
                double f = frequency[layer];
                result += amplitude[layer] * noise[layer].getSmeared(x * f, y * f, z * f, fudge * f);
            }
            return result;
        }
    }

    // ── SimplexNoise.get(double, double) (오프셋 0) ──
    private int permute(int index) {
        return islandPerm[index & 255] & 255;
    }

    private static double corner(int gradient, double x, double y) {
        double t = 0.5D - x * x - y * y;
        if (t < 0.0D) return 0.0D;
        t *= t;
        int[] g = GRADIENT[gradient];
        return t * t * (g[0] * x + g[1] * y + g[2] * 0.0D);
    }

    float simplex(double x, double y) {
        double s = (x + y) * F2;
        int i = floor(x + s);
        int j = floor(y + s);
        double t = (i + j) * G2;
        double x0 = x - (i - t);
        double y0 = y - (j - t);
        int i1;
        int j1;
        if (x0 > y0) {
            i1 = 1;
            j1 = 0;
        } else {
            i1 = 0;
            j1 = 1;
        }
        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0D + 2.0D * G2;
        double y2 = y0 - 1.0D + 2.0D * G2;
        int ii = i & 255;
        int jj = j & 255;
        int g0 = permute(ii + permute(jj)) % 12;
        int g1 = permute(ii + i1 + permute(jj + j1)) % 12;
        int g2 = permute(ii + 1 + permute(jj + 1)) % 12;
        double n0 = corner(g0, x0, y0);
        double n1 = corner(g1, x1, y1);
        double n2 = corner(g2, x2, y2);
        return (float) (70.0D * (n0 + n1 + n2));
    }

    private static int floor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    // ── EndIslandFunction ──
    /** {@code EndIslandFunction.getHeightValue(noise, blockX / 8, blockZ / 8)} (Java int 나눗셈은 0 쪽 절단). */
    float outerIslandHeight(int x, int z) {
        int i = x / 2;
        int j = z / 2;
        int k = x % 2;
        int l = z % 2;
        float f = -100.0F;
        for (int m = -12; m <= 12; m++) {
            for (int n = -12; n <= 12; n++) {
                long o = i + m;
                long p = j + n;
                if (o * o + p * p > 4096L && simplex((double) o, (double) p) < -0.9F) {
                    float g = (Math.abs((float) o) * 3439.0F + Math.abs((float) p) * 147.0F) % 13.0F + 9.0F;
                    float h = (float) (k - m * 2);
                    float q = (float) (l - n * 2);
                    float r = 100.0F - (float) Math.sqrt(h * h + q * q) * g;
                    r = clamp(r, -100.0F, 80.0F);
                    f = Math.max(f, r);
                }
            }
        }
        return f;
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    /** {@code end/islands}(= 침식 입력, TheEndBiomeSource 도 읽는다). */
    public float islands(int blockX, int blockZ) {
        float dx = (float) (0 - blockX);
        float dz = (float) (0 - blockZ);
        float distance = (float) Math.sqrt(dx * dx + 0.0F * 0.0F + dz * dz);
        float main = clamp(100.0F - distance, -100.0F, 80.0F);
        main = main - 8.0F;
        main = main == 0.0F ? 0.0F : main * 0.0078125F;
        float outer = (outerIslandHeight(blockX / 8, blockZ / 8) - 8.0F) / 128.0F;
        return Math.max(main, outer);
    }

    /** BlendedNoise.compute (float). */
    float base3d(int blockX, int blockY, int blockZ) {
        double x = blockX * XZ_MULTIPLIER;
        double y = blockY * Y_MULTIPLIER;
        double z = blockZ * XZ_MULTIPLIER;
        float blend = main.get(x / XZ_FACTOR, y / Y_FACTOR, z / XZ_FACTOR) + 0.5F;
        if (blend <= 0.0F) return minLimit.get(x, y, z);
        if (blend >= 1.0F) return maxLimit.get(x, y, z);
        float min = minLimit.get(x, y, z);
        float max = maxLimit.get(x, y, z);
        return min + blend * (max - min);
    }

    private static float gradient(int y, int from, int to, float fromValue, float toValue) {
        float t = (float) (y - from) / (float) (to - from);
        if (t < 0.0F) return fromValue;
        if (t > 1.0F) return toValue;
        return fromValue + t * (toValue - fromValue);
    }

    private static float lerp(float alpha, float first, float second) {
        return first + alpha * (second - first);
    }

    /** 보간 전 모서리 값: 0.64 · lerp(…) ({@code islands} 는 기둥마다 한 번 계산해 넘긴다). */
    float cornerDensity(int blockX, int blockY, int blockZ, float islands) {
        float outerAlpha = gradient(blockY, 4, 32, 0.0F, 1.0F);
        float value;
        if (outerAlpha == 0.0F) {
            value = -0.234375F;
        } else {
            float innerAlpha = gradient(blockY, 56, 312, 1.0F, 0.0F);
            float inner;
            if (innerAlpha == 0.0F) {
                inner = -23.4375F;
            } else {
                float sloped = islands + base3d(blockX, blockY, blockZ);
                inner = innerAlpha == 1.0F ? sloped : lerp(innerAlpha, -23.4375F, sloped);
            }
            value = outerAlpha == 1.0F ? inner : lerp(outerAlpha, -0.234375F, inner);
        }
        return value == 0.0F ? 0.0F : value * MUL_064;
    }

    /** 모서리 기둥(x, z 는 8의 배수) 33칸. */
    float[] cornerColumn(int blockX, int blockZ) {
        float islands = islands(blockX, blockZ);
        float[] column = new float[CORNERS_Y];
        for (int cy = 0; cy < CORNERS_Y; cy++) {
            column[cy] = cornerDensity(blockX, NOISE_MIN_Y + cy * CELL_HEIGHT, blockZ, islands);
        }
        return column;
    }

    private static float squeeze(float value) {
        float c = clamp(value, -1.0F, 1.0F);
        return c / 2.0F - c * c * c / 24.0F;
    }

    /** 셀 보간 + squeeze 후 밀도 &gt; 0 여부. dx, dz 는 셀 안 0..7, dy 는 0..3. */
    static boolean solid(float[] c00, float[] c10, float[] c01, float[] c11, int cy, int dx, int dy, int dz) {
        float ty = (float) dy / (float) CELL_HEIGHT;
        float tx = (float) dx / (float) CELL_WIDTH;
        float tz = (float) dz / (float) CELL_WIDTH;
        float xz00 = lerp(ty, c00[cy], c00[cy + 1]);
        float xz10 = lerp(ty, c10[cy], c10[cy + 1]);
        float xz01 = lerp(ty, c01[cy], c01[cy + 1]);
        float xz11 = lerp(ty, c11[cy], c11[cy + 1]);
        float z0 = lerp(tx, xz00, xz10);
        float z1 = lerp(tx, xz01, xz11);
        float value = lerp(tz, z0, z1);
        return squeeze(value) + 0.0F > 0.0F;
    }

    /**
     * 모서리 기둥 캐시를 가진 조회기. 한 청크 생성(또는 한 번의 탐색) 동안만 쓰고 버린다.
     * 같은 좌표는 언제나 같은 값을 낸다(캐시는 성능만 바꾼다).
     */
    public final class Sampler {
        private final Map<Long, float[]> corners = new HashMap<>();
        private final Map<Long, int[]> columns = new HashMap<>();

        private float[] corner(int cellX, int cellZ) {
            long key = (long) cellX << 32 ^ cellZ & 0xffffffffL;
            float[] column = corners.get(key);
            if (column == null) {
                column = cornerColumn(cellX * CELL_WIDTH, cellZ * CELL_WIDTH);
                corners.put(key, column);
            }
            return column;
        }

        /**
         * 한 블록 기둥의 엔드 돌 칸을 아래에서 위로 [bottom0, top0, bottom1, top1, …] 로 낸다.
         * 빈 기둥은 길이 0 이다.
         */
        public int[] runs(int x, int z) {
            long key = (long) x << 32 ^ z & 0xffffffffL;
            int[] cached = columns.get(key);
            if (cached != null) return cached;
            int cellX = Math.floorDiv(x, CELL_WIDTH);
            int cellZ = Math.floorDiv(z, CELL_WIDTH);
            int dx = x - cellX * CELL_WIDTH;
            int dz = z - cellZ * CELL_WIDTH;
            float[] c00 = corner(cellX, cellZ);
            float[] c10 = corner(cellX + 1, cellZ);
            float[] c01 = corner(cellX, cellZ + 1);
            float[] c11 = corner(cellX + 1, cellZ + 1);
            int[] buffer = new int[NOISE_HEIGHT + 2];
            int count = 0;
            int start = -1;
            for (int y = NOISE_MIN_Y; y <= NOISE_MIN_Y + NOISE_HEIGHT; y++) {
                boolean filled = y < NOISE_MIN_Y + NOISE_HEIGHT
                        && solid(c00, c10, c01, c11, (y - NOISE_MIN_Y) / CELL_HEIGHT, dx,
                                (y - NOISE_MIN_Y) % CELL_HEIGHT, dz);
                if (filled && start < 0) start = y;
                if (!filled && start >= 0) {
                    buffer[count++] = start;
                    buffer[count++] = y - 1;
                    start = -1;
                }
            }
            int[] result = java.util.Arrays.copyOf(buffer, count);
            columns.put(key, result);
            return result;
        }

        public boolean isSolid(int x, int y, int z) {
            if (y < NOISE_MIN_Y || y >= NOISE_MIN_Y + NOISE_HEIGHT) return false;
            int[] runs = runs(x, z);
            for (int i = 0; i < runs.length; i += 2) {
                if (y >= runs[i] && y <= runs[i + 1]) return true;
            }
            return false;
        }

        /** 가장 높은 엔드 돌 칸(없으면 NOISE_MIN_Y − 1). */
        public int top(int x, int z) {
            int[] runs = runs(x, z);
            return runs.length == 0 ? NOISE_MIN_Y - 1 : runs[runs.length - 1];
        }
    }

    public Sampler sampler() {
        return new Sampler();
    }

    /** {@code TheEndBiomeSource.getNoiseBiome}: 청크(섹션) 좌표 기준. */
    public int biome(int chunkX, int chunkZ) {
        if ((long) chunkX * chunkX + (long) chunkZ * chunkZ <= 4096L) return BIOME_THE_END;
        double erosion = islands((chunkX * 2 + 1) * 8, (chunkZ * 2 + 1) * 8);
        if (erosion > 0.25D) return BIOME_HIGHLANDS;
        if (erosion >= -0.0625D) return BIOME_MIDLANDS;
        return erosion < -0.21875D ? BIOME_SMALL_ISLANDS : BIOME_BARRENS;
    }
}
