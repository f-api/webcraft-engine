package com.gameexpert.terrain.mc.df;

import com.gameexpert.terrain.mc.McNormalNoise;
import com.gameexpert.terrain.mc.McGradientNoise;
import com.gameexpert.terrain.mc.McRandom;

/** 바닐라 데이터팩에서 실제로 쓰는 밀도 함수 노드 모음이다. */
public final class McDensityFunctions {
    private McDensityFunctions() {
    }

    public static final class Constant implements McDensityFunction {
        private final float value;
        public Constant(double value) { this.value = (float) value; }
        @Override public double compute(Context context) { return value; }
    }

    public static final class Noise implements McDensityFunction {
        private final McNormalNoise noise;
        private final double xzScale;
        private final double yScale;
        public Noise(McNormalNoise noise, double xzScale, double yScale) {
            this.noise = noise; this.xzScale = xzScale; this.yScale = yScale;
        }
        @Override public double compute(Context c) {
            return (float) noise.getValue(c.x * xzScale, c.y * yScale, c.z * xzScale);
        }
    }

    public static final class ShiftedNoise implements McDensityFunction {
        private final McDensityFunction shiftX, shiftY, shiftZ;
        private final McNormalNoise noise;
        private final double xzScale, yScale;
        public ShiftedNoise(McDensityFunction shiftX, McDensityFunction shiftY,
                McDensityFunction shiftZ, McNormalNoise noise, double xzScale, double yScale) {
            this.shiftX = shiftX; this.shiftY = shiftY; this.shiftZ = shiftZ;
            this.noise = noise; this.xzScale = xzScale; this.yScale = yScale;
        }
        @Override public double compute(Context c) {
            double x = c.x * xzScale + shiftX.compute(c);
            double y = c.y * yScale + shiftY.compute(c);
            double z = c.z * xzScale + shiftZ.compute(c);
            return (float) noise.getValue(x, y, z);
        }
    }

    public static final class Shift implements McDensityFunction {
        public enum Kind { SHIFT, A, B }
        private final McNormalNoise noise;
        private final Kind kind;
        public Shift(McNormalNoise noise, Kind kind) { this.noise = noise; this.kind = kind; }
        @Override public double compute(Context c) {
            if (kind == Kind.A) return (float) ((float) noise.getValue(c.x * 0.25, 0.0, c.z * 0.25) * 4.0F);
            if (kind == Kind.B) return (float) ((float) noise.getValue(c.z * 0.25, c.x * 0.25, 0.0) * 4.0F);
            return (float) ((float) noise.getValue(c.x * 0.25, c.y * 0.25, c.z * 0.25) * 4.0F);
        }
    }

    public static final class YClampedGradient implements McDensityFunction {
        private final int fromY, toY;
        private final double fromValue, toValue;
        public YClampedGradient(int fromY, int toY, double fromValue, double toValue) {
            this.fromY = fromY; this.toY = toY; this.fromValue = fromValue; this.toValue = toValue;
        }
        @Override public double compute(Context c) {
            float delta = Math.max(0.0F, Math.min(1.0F,
                    (float) (c.y - fromY) / (float) (toY - fromY)));
            return (float) fromValue + delta * ((float) toValue - (float) fromValue);
        }
    }

    public static final class RangeChoice implements McDensityFunction {
        private final McDensityFunction input, in, out;
        private final double min, max;
        public RangeChoice(McDensityFunction input, double min, double max,
                McDensityFunction in, McDensityFunction out) {
            this.input = input; this.min = min; this.max = max; this.in = in; this.out = out;
        }
        @Override public double compute(Context c) {
            float value = (float) input.compute(c);
            return min <= value && value < max ? in.compute(c) : out.compute(c);
        }
    }

    /** 26.3 interval_select: 첫 번째 strictly-greater threshold의 함수를 선택한다. */
    public static final class IntervalSelect implements McDensityFunction {
        private final McDensityFunction input;
        private final float[] thresholds;
        private final McDensityFunction[] functions;

        public IntervalSelect(McDensityFunction input, float[] thresholds,
                McDensityFunction[] functions) {
            if (thresholds.length != functions.length - 1) {
                throw new IllegalArgumentException("threshold/function arity mismatch");
            }
            for (int i = 1; i < thresholds.length; i++) {
                if (thresholds[i] < thresholds[i - 1]) {
                    throw new IllegalArgumentException("thresholds are not ordered");
                }
            }
            this.input = input;
            this.thresholds = thresholds.clone();
            this.functions = functions.clone();
        }

        @Override public double compute(Context context) {
            float value = (float) input.compute(context);
            for (int i = 0; i < thresholds.length; i++) {
                if (value < thresholds[i]) return (float) functions[i].compute(context);
            }
            return (float) functions[functions.length - 1].compute(context);
        }
    }

    /** 26.3 lerp의 0/1 단락과 float 연산 순서를 보존한다. */
    public static final class Lerp implements McDensityFunction {
        private final McDensityFunction alpha, first, second;
        public Lerp(McDensityFunction alpha, McDensityFunction first, McDensityFunction second) {
            this.alpha = alpha; this.first = first; this.second = second;
        }
        @Override public double compute(Context context) {
            float value = (float) alpha.compute(context);
            if (value == 0.0F) return (float) first.compute(context);
            if (value == 1.0F) return (float) second.compute(context);
            float start = (float) first.compute(context);
            return start + value * ((float) second.compute(context) - start);
        }
    }

    /** 26.3 find_top_surface의 cell-aligned 하향 탐색이다. */
    public static final class FindTopSurface implements McDensityFunction {
        private final McDensityFunction density, upperBound;
        private final int lowerBound, cellHeight;
        public FindTopSurface(McDensityFunction density, McDensityFunction upperBound,
                int lowerBound, int cellHeight) {
            if (cellHeight <= 0) throw new IllegalArgumentException("cellHeight must be positive");
            this.density = density; this.upperBound = upperBound;
            this.lowerBound = lowerBound; this.cellHeight = cellHeight;
        }
        @Override public double compute(Context context) {
            int upper = (int) Math.floor((float) upperBound.compute(context) / cellHeight)
                    * cellHeight;
            if (upper <= lowerBound) return (float) lowerBound;
            int oldY = context.y;
            try {
                for (int y = upper; y >= lowerBound; y -= cellHeight) {
                    context.set(context.x, y, context.z);
                    if ((float) density.compute(context) > 0.0F) return (float) y;
                }
                return (float) lowerBound;
            } finally {
                context.set(context.x, oldY, context.z);
            }
        }
    }

    public static final class Binary implements McDensityFunction {
        public enum Kind { ADD, SUB, MUL, DIV, MIN, MAX }
        private final Kind kind;
        private final McDensityFunction first, second;
        public Binary(Kind kind, McDensityFunction first, McDensityFunction second) {
            this.kind = kind; this.first = first; this.second = second;
        }
        @Override public double compute(Context c) {
            float a = (float) first.compute(c);
            if (kind == Kind.ADD) return a + (float) second.compute(c);
            if (kind == Kind.SUB) return a - (float) second.compute(c);
            if (kind == Kind.MUL) return a == 0.0F ? 0.0F : a * (float) second.compute(c);
            if (kind == Kind.DIV) return a == 0.0F ? 0.0F : a / (float) second.compute(c);
            float b = (float) second.compute(c);
            return kind == Kind.MIN ? Math.min(a, b) : Math.max(a, b);
        }
    }

    public static final class Mapped implements McDensityFunction {
        public enum Kind { ABS, SQUARE, CUBE, HALF_NEGATIVE, QUARTER_NEGATIVE, SQUEEZE, INVERT }
        private final Kind kind;
        private final McDensityFunction input;
        public Mapped(Kind kind, McDensityFunction input) { this.kind = kind; this.input = input; }
        @Override public double compute(Context c) {
            float d = (float) input.compute(c);
            switch (kind) {
                case ABS: return Math.abs(d);
                case SQUARE: return d * d;
                case CUBE: return d * d * d;
                case HALF_NEGATIVE: return d > 0.0 ? d : d * 0.5;
                case QUARTER_NEGATIVE: return d > 0.0 ? d : d * 0.25;
                case INVERT: return 1.0 / d;
                case SQUEEZE:
                    float value = Math.max(-1.0F, Math.min(1.0F, d));
                    return value / 2.0F - value * value * value / 24.0F;
                default: throw new AssertionError(kind);
            }
        }
    }

    public static final class Clamp implements McDensityFunction {
        private final McDensityFunction input;
        private final double min, max;
        public Clamp(McDensityFunction input, double min, double max) {
            this.input = input; this.min = min; this.max = max;
        }
        @Override public double compute(Context c) {
            return Math.max((float) min, Math.min((float) max, (float) input.compute(c)));
        }
    }

    /** Vanilla weird_scaled_sampler와 같은 희귀도 스케일 순서로 3D noise를 샘플한다. */
    public static final class WeirdScaledSampler implements McDensityFunction {
        public enum Mapper { TYPE_1, TYPE_2 }

        private final McDensityFunction input;
        private final McNormalNoise noise;
        private final Mapper mapper;

        public WeirdScaledSampler(McDensityFunction input, McNormalNoise noise, Mapper mapper) {
            this.input = input;
            this.noise = noise;
            this.mapper = mapper;
        }

        @Override public double compute(Context c) {
            double rarity = rarity(input.compute(c));
            return rarity * Math.abs(noise.getValue(c.x / rarity, c.y / rarity, c.z / rarity));
        }

        public double rarity(double value) {
            if (mapper == Mapper.TYPE_1) {
                if (value < -0.5D) return 0.75D;
                if (value < 0.0D) return 1.0D;
                if (value < 0.5D) return 1.5D;
                return 2.0D;
            }
            if (value < -0.75D) return 0.5D;
            if (value < -0.5D) return 0.75D;
            if (value < 0.5D) return 1.0D;
            if (value < 0.75D) return 2.0D;
            return 3.0D;
        }
    }

    /** 블렌더가 없는 새 월드에서는 항등 함수이다. */
    public static final class Wrapper implements McDensityFunction {
        public enum Kind { BLEND_DENSITY, CACHE_ONCE, CACHE_2D, FLAT_CACHE, INTERPOLATED }
        private final Kind kind;
        private final McDensityFunction input;
        private final int cacheSlot;

        public Wrapper(Kind kind, McDensityFunction input) {
            this(kind, input, -1);
        }

        public Wrapper(Kind kind, McDensityFunction input, int cacheSlot) {
            this.kind = kind;
            this.input = input;
            this.cacheSlot = cacheSlot;
        }

        @Override public double compute(Context c) {
            if (kind == Kind.BLEND_DENSITY) return input.compute(c);
            if (kind == Kind.FLAT_CACHE) {
                int quartX = Math.floorDiv(c.x, 4);
                int quartZ = Math.floorDiv(c.z, 4);
                return cachedAt(c, quartX << 2, 0, quartZ << 2);
            }
            if (kind == Kind.CACHE_2D) {
                double cached = cacheGet(c, c.x, 0, c.z);
                if (!Double.isNaN(cached)) return cached;
                double value = input.compute(c);
                cachePut(c, c.x, 0, c.z, value);
                return value;
            }
            if (kind == Kind.CACHE_ONCE) return cachedAt(c, c.x, c.y, c.z);
            return interpolate(c);
        }

        private double cachedAt(Context c, int x, int y, int z) {
            double cached = cacheGet(c, x, y, z);
            if (!Double.isNaN(cached)) return cached;
            int oldX = c.x, oldY = c.y, oldZ = c.z;
            c.set(x, y, z);
            double value = input.compute(c);
            c.set(oldX, oldY, oldZ);
            cachePut(c, x, y, z, value);
            return value;
        }

        private double cacheGet(Context c, int x, int y, int z) {
            return c.cache == null || cacheSlot < 0
                    ? Double.NaN : c.cache.get(cacheSlot, x, y, z);
        }

        private void cachePut(Context c, int x, int y, int z, double value) {
            if (c.cache != null && cacheSlot >= 0) c.cache.put(cacheSlot, x, y, z, value);
        }

        private double interpolate(Context c) {
            int x0 = Math.floorDiv(c.x, 4) * 4, y0 = Math.floorDiv(c.y, 8) * 8, z0 = Math.floorDiv(c.z, 4) * 4;
            float tx = Math.floorMod(c.x, 4) / 4.0F, ty = Math.floorMod(c.y, 8) / 8.0F, tz = Math.floorMod(c.z, 4) / 4.0F;
            float x00 = lerp(tx, (float) corner(c,x0,y0,z0), (float) corner(c,x0+4,y0,z0));
            float x10 = lerp(tx, (float) corner(c,x0,y0+8,z0), (float) corner(c,x0+4,y0+8,z0));
            float x01 = lerp(tx, (float) corner(c,x0,y0,z0+4), (float) corner(c,x0+4,y0,z0+4));
            float x11 = lerp(tx, (float) corner(c,x0,y0+8,z0+4), (float) corner(c,x0+4,y0+8,z0+4));
            return lerp(tz, lerp(ty, x00, x10), lerp(ty, x01, x11));
        }
        private double corner(Context c, int x, int y, int z) {
            return cachedAt(c, x, y, z);
        }
        private static float lerp(float t,float a,float b) { return a + t * (b - a); }
    }

    public static final class Spline implements McDensityFunction {
        private final McDensityFunction coordinate;
        private final float[] locations, derivatives;
        private final McDensityFunction[] values;
        public Spline(McDensityFunction coordinate, float[] locations, float[] derivatives, McDensityFunction[] values) {
            this.coordinate=coordinate; this.locations=locations; this.derivatives=derivatives; this.values=values;
        }
        @Override public double compute(Context c) {
            float point=(float)coordinate.compute(c); int last=locations.length-1;
            if (point<locations[0]) return (float)((float)values[0].compute(c)+derivatives[0]*(point-locations[0]));
            if (point>=locations[last]) return (float)((float)values[last].compute(c)+derivatives[last]*(point-locations[last]));
            int i=0; while (point>=locations[i+1]) i++;
            float width=locations[i+1]-locations[i], t=(point-locations[i])/width;
            float a=(float)values[i].compute(c), b=(float)values[i+1].compute(c), delta=b-a;
            float slope0=derivatives[i]*width-delta, slope1=-derivatives[i+1]*width+delta;
            return (float)(lerp(t,a,b)+t*(1.0f-t)*lerp(t,slope0,slope1));
        }
        private static float lerp(float t,float a,float b) { return a+t*(b-a); }
    }

    /** Minecraft 26.3의 float {@code BlendedNoise}/{@code SmearedPerlinNoise} 구현이다. */
    public static final class OldBlendedNoise implements McDensityFunction {
        private final NoiseStack minLimit, maxLimit, main;
        private final double xzMultiplier, yMultiplier, xzFactor, yFactor;
        public OldBlendedNoise(McRandom random,double xzScale,double yScale,double xzFactor,double yFactor,double smearMultiplier){
            double smearScale = 684.412D * yScale * smearMultiplier;
            minLimit = new NoiseStack(random, -15, smearScale, 0.9999847412109375D);
            maxLimit = new NoiseStack(random, -15, smearScale, 0.9999847412109375D);
            main = new NoiseStack(random, -7, smearScale / yFactor, 12.75D);
            xzMultiplier=684.412*xzScale; yMultiplier=684.412*yScale;
            this.xzFactor=xzFactor;this.yFactor=yFactor;
        }
        @Override public double compute(Context c){
            double sx=c.x*xzMultiplier,sy=c.y*yMultiplier,sz=c.z*xzMultiplier;
            float blend = main.get(sx / xzFactor, sy / yFactor, sz / xzFactor) + 0.5F;
            if (blend <= 0.0F) return minLimit.get(sx, sy, sz);
            if (blend >= 1.0F) return maxLimit.get(sx, sy, sz);
            float min = minLimit.get(sx, sy, sz);
            float max = maxLimit.get(sx, sy, sz);
            return min + blend * (max - min);
        }
        private static final class NoiseStack {
            private final McGradientNoise[] noise;
            private final double[] frequency;
            private final float[] amplitude;

            NoiseStack(McRandom random, int firstOctave, double fudgeScale,
                    double baseAmplitude) {
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
                for (int layer = 0; layer < count; layer++) {
                    frequency[layer] = layerFrequencyFor(layer);
                }
                this.fudgeScale = fudgeScale;
            }

            private final double fudgeScale;

            private static double layerFrequencyFor(int layer) {
                return Math.scalb(1.0D, -layer);
            }

            float get(double x, double y, double z) {
                float result = 0.0F;
                for (int layer = 0; layer < noise.length; layer++) {
                    double f = frequency[layer];
                    result += amplitude[layer] * noise[layer].getSmeared(
                            x * f, y * f, z * f, fudgeScale * f);
                }
                return result;
            }
        }
    }

}
