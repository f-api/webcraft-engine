package com.gameexpert.terrain.mc.biome;

import com.gameexpert.terrain.mc.McNormalNoise;
import com.gameexpert.terrain.mc.Mc263NoiseRegistry;
import com.gameexpert.terrain.mc.McRandom;

/** 26.3이 parity 모드로 유지한 1.21 계열 noise/spline 연산 순서의 Overworld 기후 sampler다. */
public final class McClimateSampler {
    private final McNormalNoise shift;
    private final McNormalNoise temperature;
    private final McNormalNoise humidity;
    private final McNormalNoise continentalness;
    private final McNormalNoise erosion;
    private final McNormalNoise weirdness;
    private final Spline offsetSpline;
    private final McBiomeTable table = new McBiomeTable();

    public McClimateSampler(long seed) {
        McRandom.PositionalFactory factory = new McRandom(seed).forkPositional();
        shift = noise(factory, "minecraft:offset");
        temperature = noise(factory, "minecraft:temperature");
        humidity = noise(factory, "minecraft:vegetation");
        continentalness = noise(factory, "minecraft:continentalness");
        erosion = noise(factory, "minecraft:erosion");
        weirdness = noise(factory, "minecraft:ridge");
        offsetSpline = createOffsetSpline();
    }

    private static McNormalNoise noise(McRandom.PositionalFactory factory, String key) {
        return Mc263NoiseRegistry.create(factory, key);
    }

    /**
     * 입력은 cubiomes sampleBiomeNoise와 같은 quart 좌표다.
     *
     * <p>AGENTS rule 10l — vanilla's own amortization stage, mirrored. Every term this sampler
     * reads except {@code depth} is evaluated at {@code (px, 0.0, pz)}: the offset shift, the six
     * climate noises and the offset spline do not depend on {@code quartY} at all. Vanilla does
     * not recompute them per Y either — its noise router wraps exactly these functions in
     * {@code DensityFunctions.FlatCache} (a {@code Cache2D} over the quart column), so a column's
     * climate is derived once and reused down the whole height. The port evaluated all six noises
     * and the spline on every call, and {@code fillBiomesFromNoise} alone asks for 96 heights of
     * each of a chunk's 16 quart columns — 1,536 evaluations of 16 distinct answers.</p>
     *
     * <p>The cache below is byte-neutral, not an approximation: {@link McNormalNoise} and the
     * spline are pure functions of position (every field of both is final), so a resident column
     * carries the identical {@code float} bits the recomputation would produce, and {@code depth}
     * — the one Y-dependent term — is still computed per call from the same expression. Entries
     * are keyed by absolute quart coordinates, so a survivor from an earlier chunk answers its own
     * coordinate and nothing else; the grid stays a pure function of {@code (seed, chunkX,
     * chunkZ)}. The table is direct-mapped and fixed-size, so it cannot grow on a long activation
     * wall, and a collision simply recomputes.</p>
     */
    public McClimate sampleQuart(int quartX, int quartY, int quartZ) {
        Column column = column(quartX, quartZ);
        // Vanilla's DensityFunctions graph evaluates yClampedGradient first, then its float
        // offset, and finally adds those float results. Keep those stages rather than folding
        // them into a double expression: the R-tree's depth quantization is boundary-sensitive.
        float vertical = 1.0F - (quartY * 4) / 128.0F;
        float offset = (float) column.offset - 83.0F / 160.0F;
        float d = vertical + offset;
        return McClimate.fromFloats(column.temperature, column.humidity, column.continentalness,
                column.erosion, d, column.weirdness);
    }

    /** The Y-independent climate of one quart column. */
    private static final class Column {
        private final long key;
        private final float temperature;
        private final float humidity;
        private final float continentalness;
        private final float erosion;
        private final float weirdness;
        private final double offset;

        private Column(long key, float temperature, float humidity, float continentalness,
                float erosion, float weirdness, double offset) {
            this.key = key;
            this.temperature = temperature;
            this.humidity = humidity;
            this.continentalness = continentalness;
            this.erosion = erosion;
            this.weirdness = weirdness;
            this.offset = offset;
        }
    }

    private static final int COLUMN_SLOTS = 512;
    private final Column[] columns = new Column[COLUMN_SLOTS];

    private Column column(int quartX, int quartZ) {
        long key = ((long) quartX << 32) ^ (quartZ & 0xffff_ffffL);
        int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> 55) & (COLUMN_SLOTS - 1);
        Column resident = columns[slot];
        if (resident != null && resident.key == key) {
            return resident;
        }
        double px = quartX;
        double pz = quartZ;
        px += shift.getValue(quartX, 0.0, quartZ) * 4.0;
        pz += shift.getValue(quartZ, quartX, 0.0) * 4.0;

        float c = (float) continentalness.getValue(px, 0.0, pz);
        float e = (float) erosion.getValue(px, 0.0, pz);
        float w = (float) weirdness.getValue(px, 0.0, pz);
        float ridges = -3.0F * (Math.abs(Math.abs(w) - 0.6666667F) - 0.33333334F);
        float[] parameters = {c, e, ridges, w};
        double offset = offsetSpline.value(parameters) + 0.015F;
        float t = (float) temperature.getValue(px, 0.0, pz);
        float h = (float) humidity.getValue(px, 0.0, pz);
        Column computed = new Column(key, t, h, c, e, w, offset);
        columns[slot] = computed;
        return computed;
    }

    public int biomeAtQuart(int quartX, int quartY, int quartZ) {
        return table.select(sampleQuart(quartX, quartY, quartZ));
    }

    public int biomeAtBlock(int blockX, int blockY, int blockZ) {
        return biomeAtQuart(Math.floorDiv(blockX, 4), Math.floorDiv(blockY, 4),
                Math.floorDiv(blockZ, 4));
    }

    /** Clear the mutable biome-table search hint before an independent chunk evaluation. */
    public void resetChunkCache() {
        table.clearCache();
    }

    private static Spline createOffsetSpline() {
        Spline root = new Spline(0);
        Spline first = createLandSpline(-0.15F, 0.00F, 0.0F, 0.1F, 0.00F, -0.03F, false);
        Spline second = createLandSpline(-0.10F, 0.03F, 0.1F, 0.1F, 0.01F, -0.03F, false);
        Spline third = createLandSpline(-0.10F, 0.03F, 0.1F, 0.7F, 0.01F, -0.03F, true);
        Spline fourth = createLandSpline(-0.05F, 0.03F, 0.1F, 1.0F, 0.01F, 0.01F, true);
        root.add(-1.10F, fixed(0.044F), 0.0F);
        root.add(-1.02F, fixed(-0.2222F), 0.0F);
        root.add(-0.51F, fixed(-0.2222F), 0.0F);
        root.add(-0.44F, fixed(-0.12F), 0.0F);
        root.add(-0.18F, fixed(-0.12F), 0.0F);
        root.add(-0.16F, first, 0.0F);
        root.add(-0.15F, first, 0.0F);
        root.add(-0.10F, second, 0.0F);
        root.add(0.25F, third, 0.0F);
        root.add(1.00F, fourth, 0.0F);
        return root;
    }

    private static Spline createLandSpline(float f, float g, float h, float i,
            float j, float k, boolean amplified) {
        Spline sp1 = createRidgeSpline(lerp(i, 0.6F, 1.5F), amplified);
        Spline sp2 = createRidgeSpline(lerp(i, 0.6F, 1.0F), amplified);
        Spline sp3 = createRidgeSpline(i, amplified);
        float half = 0.5F * i;
        Spline sp4 = createFlatSpline(f - 0.15F, half, half, half, i * 0.6F, 0.5F);
        Spline sp5 = createFlatSpline(f, j * i, g * i, half, i * 0.6F, 0.5F);
        Spline sp6 = createFlatSpline(f, j, j, g, h, 0.5F);
        Spline sp7 = createFlatSpline(f, j, j, g, h, 0.5F);
        Spline sp8 = new Spline(2);
        sp8.add(-1.0F, fixed(f), 0.0F);
        sp8.add(-0.4F, sp6, 0.0F);
        sp8.add(0.0F, fixed(h + 0.07F), 0.0F);
        Spline sp9 = createFlatSpline(-0.02F, k, k, g, h, 0.0F);
        Spline result = new Spline(1);
        result.add(-0.85F, sp1, 0.0F);
        result.add(-0.7F, sp2, 0.0F);
        result.add(-0.4F, sp3, 0.0F);
        result.add(-0.35F, sp4, 0.0F);
        result.add(-0.1F, sp5, 0.0F);
        result.add(0.2F, sp6, 0.0F);
        if (amplified) {
            result.add(0.4F, sp7, 0.0F);
            result.add(0.45F, sp8, 0.0F);
            result.add(0.55F, sp8, 0.0F);
            result.add(0.58F, sp7, 0.0F);
        }
        result.add(0.7F, sp9, 0.0F);
        return result;
    }

    private static Spline createRidgeSpline(float continentalness, boolean amplified) {
        Spline result = new Spline(2);
        float low = offsetValue(-1.0F, continentalness);
        float high = offsetValue(1.0F, continentalness);
        float l = 1.0F - (1.0F - continentalness) * 0.5F;
        float u = 0.5F * (1.0F - continentalness);
        l = u / (0.46082947F * l) - 1.17F;
        if (-0.65F < l && l < 1.0F) {
            u = offsetValue(-0.65F, continentalness);
            float p = offsetValue(-0.75F, continentalness);
            float q = (p - low) * 4.0F;
            float r = offsetValue(l, continentalness);
            float s = (high - r) / (1.0F - l);
            result.add(-1.0F, fixed(low), q);
            result.add(-0.75F, fixed(p), 0.0F);
            result.add(-0.65F, fixed(u), 0.0F);
            result.add(l - 0.01F, fixed(r), 0.0F);
            result.add(l, fixed(r), s);
            result.add(1.0F, fixed(high), s);
        } else {
            u = (high - low) * 0.5F;
            if (amplified) {
                result.add(-1.0F, fixed(low > 0.2F ? low : 0.2F), 0.0F);
                result.add(0.0F, fixed(lerp(0.5F, low, high)), u);
            } else {
                result.add(-1.0F, fixed(low), u);
            }
            result.add(1.0F, fixed(high), u);
        }
        return result;
    }

    private static float offsetValue(float weirdness, float continentalness) {
        float f0 = 1.0F - (1.0F - continentalness) * 0.5F;
        float f1 = 0.5F * (1.0F - continentalness);
        float f2 = (weirdness + 1.17F) * 0.46082947F;
        float offset = f2 * f0 - f1;
        if (weirdness < -0.7F) {
            return offset > -0.2222F ? offset : -0.2222F;
        }
        return offset > 0.0F ? offset : 0.0F;
    }

    private static Spline createFlatSpline(float f, float g, float h, float i,
            float j, float minimumDerivative) {
        Spline result = new Spline(2);
        float l = 0.5F * (g - f);
        if (l < minimumDerivative) {
            l = minimumDerivative;
        }
        float m = 5.0F * (h - g);
        result.add(-1.0F, fixed(f), l);
        result.add(-0.4F, fixed(g), l < m ? l : m);
        result.add(0.0F, fixed(h), m);
        result.add(0.4F, fixed(i), 2.0F * (i - h));
        result.add(1.0F, fixed(j), 0.7F * (j - i));
        return result;
    }

    private static Spline fixed(float value) {
        return new FixedSpline(value);
    }

    private static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    private static class Spline {
        private final int axis;
        private final float[] locations = new float[12];
        private final float[] derivatives = new float[12];
        private final Spline[] values = new Spline[12];
        private int size;

        private Spline(int axis) {
            this.axis = axis;
        }

        private void add(float location, Spline value, float derivative) {
            locations[size] = location;
            values[size] = value;
            derivatives[size] = derivative;
            size++;
        }

        protected float value(float[] parameters) {
            float coordinate = parameters[axis];
            int index = 0;
            while (index < size && locations[index] < coordinate) {
                index++;
            }
            if (index == 0 || index == size) {
                if (index != 0) {
                    index--;
                }
                return values[index].value(parameters)
                        + derivatives[index] * (coordinate - locations[index]);
            }
            int previous = index - 1;
            float low = locations[previous];
            float high = locations[index];
            float delta = (coordinate - low) / (high - low);
            float leftDerivative = derivatives[previous];
            float rightDerivative = derivatives[index];
            float left = values[previous].value(parameters);
            float right = values[index].value(parameters);
            float p = leftDerivative * (high - low) - (right - left);
            float q = -rightDerivative * (high - low) + (right - left);
            return lerp(delta, left, right)
                    + delta * (1.0F - delta) * lerp(delta, p, q);
        }
    }

    private static final class FixedSpline extends Spline {
        private final float fixedValue;

        private FixedSpline(float fixedValue) {
            super(0);
            this.fixedValue = fixedValue;
        }

        @Override
        protected float value(float[] parameters) {
            return fixedValue;
        }
    }
}
