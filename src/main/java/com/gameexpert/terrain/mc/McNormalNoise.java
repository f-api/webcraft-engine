package com.gameexpert.terrain.mc;

import java.util.ArrayList;
import java.util.List;

/** Minecraft 26.3의 독립 octave seed와 float 누산을 사용하는 NormalNoise다. */
public final class McNormalNoise {
    public static final double INPUT_FACTOR = 1.0181268882175227D;
    private static final double PERLIN_STANDARD_DEVIATION = 0.2702247831245211D;
    private static final double TARGET_DEVIATION = 1.0D / 3.0D;

    private final Layer[] layers;
    private final double normalizationFactor;
    private final double maxValue;

    /**
     * 1.21 계열의 first-octave/amplitudes 입력을 26.3의 공식 parity builder와 같은
     * base-amplitude로 변환한다. 샘플러 자체는 항상 26.3 알고리즘이다.
     */
    public McNormalNoise(McRandom random, int firstOctave, double[] amplitudes) {
        this(() -> modernSources(random), parityBaseAmplitude(firstOctave, amplitudes), firstOctave,
                amplitudes.length, amplitudes);
    }

    /** 26.3 noise 데이터의 정규화가 활성화된 기본 schema를 직접 구성한다. */
    public McNormalNoise(McRandom random, double baseAmplitude, int baseOctave,
            int octaveCount, double[] amplitudeModifiers) {
        this(() -> modernSources(random), baseAmplitude, baseOctave, octaveCount,
                amplitudeModifiers);
    }

    /**
     * Builds a parity-schema normal noise from the world-seeded legacy positional factory used by
     * the pinned 26.3 amethyst-geode feature. This deliberately does not use the modern MD5-based
     * Xoroshiro positional path.
     */
    public static McNormalNoise legacyParity(long worldSeed, int firstOctave,
            double[] amplitudes) {
        return new McNormalNoise(() -> legacySources(new LegacyRand(worldSeed)),
                parityBaseAmplitude(firstOctave, amplitudes), firstOctave,
                amplitudes.length, amplitudes);
    }

    private McNormalNoise(GradientSourcesFactory sourceFactory, double baseAmplitude, int baseOctave,
            int octaveCount, double[] amplitudeModifiers) {
        if (octaveCount < 1 || octaveCount > 32) {
            throw new IllegalArgumentException("octaveCount must be in [1, 32]");
        }
        if (amplitudeModifiers.length != 0 && amplitudeModifiers.length != octaveCount) {
            throw new IllegalArgumentException(
                    "amplitudeModifiers must be empty or match octaveCount");
        }
        List<Octave> octaves = buildOctaves(baseAmplitude, baseOctave, octaveCount,
                amplitudeModifiers);
        if (octaves.isEmpty()) throw new IllegalArgumentException("all octave amplitudes are zero");
        double amplitudeSum = 0.0D;
        double deviationSquared = 0.0D;
        for (Octave octave : octaves) {
            amplitudeSum += octave.amplitude;
            double deviation = PERLIN_STANDARD_DEVIATION * Math.abs(octave.amplitude);
            deviationSquared += deviation * deviation;
        }
        normalizationFactor = amplitudeSum * TARGET_DEVIATION
                / (Math.sqrt(deviationSquared) * Math.sqrt(2.0D));
        maxValue = (double) (float) (amplitudeSum * TARGET_DEVIATION * 6.0D);

        GradientSources sources = sourceFactory.create();
        layers = new Layer[octaves.size() * 2];
        int index = 0;
        for (Octave octave : octaves) {
            String key = "octave_" + octave.index;
            float amplitude = (float) (normalizationFactor * octave.amplitude);
            layers[index++] = new Layer(sources.first().create(key),
                    octave.frequency, amplitude);
            layers[index++] = new Layer(sources.second().create(key),
                    octave.frequency * INPUT_FACTOR, amplitude);
        }
    }

    public double getValue(double x, double y, double z) {
        float result = 0.0F;
        for (Layer layer : layers) {
            result += layer.amplitude * layer.noise.get(
                    x * layer.frequency, y * layer.frequency, z * layer.frequency);
        }
        return result;
    }

    public double maxValue() {
        return maxValue;
    }

    public double valueFactor() {
        return normalizationFactor;
    }

    private static List<Octave> buildOctaves(double baseAmplitude, int baseOctave,
            int octaveCount, double[] modifiers) {
        double frequency = Math.pow(2.0D, baseOctave);
        double amplitude = baseAmplitude;
        amplitude *= Math.pow(0.5D, -(octaveCount - 1))
                / (Math.pow(0.5D, -octaveCount) - 1.0D);
        List<Octave> result = new ArrayList<>(octaveCount);
        for (int i = 0; i < octaveCount; i++) {
            double modifier = modifiers.length == 0 ? 1.0D : modifiers[i];
            if (modifier != 0.0D) {
                result.add(new Octave(baseOctave + i, frequency, amplitude * modifier));
            }
            frequency *= 2.0D;
            amplitude *= 0.5D;
        }
        return result;
    }

    private static double parityBaseAmplitude(int firstOctave, double[] amplitudes) {
        if (amplitudes.length == 0) throw new IllegalArgumentException("amplitudes are empty");
        List<Octave> unit = buildOctaves(1.0D, firstOctave, amplitudes.length, amplitudes);
        double sum = 0.0D;
        double deviationSquared = 0.0D;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Octave octave : unit) {
            sum += octave.amplitude;
            double deviation = PERLIN_STANDARD_DEVIATION * Math.abs(octave.amplitude);
            deviationSquared += deviation * deviation;
            int offset = octave.index - firstOctave;
            min = Math.min(min, offset);
            max = Math.max(max, offset);
        }
        if (min == Integer.MAX_VALUE) throw new IllegalArgumentException("all amplitudes are zero");
        double normalized = sum * TARGET_DEVIATION
                / (Math.sqrt(deviationSquared) * Math.sqrt(2.0D));
        double parity = 0.5D * TARGET_DEVIATION
                / (0.1D * (1.0D + 1.0D / (max - min + 1)));
        return parity / normalized;
    }

    private static GradientSources modernSources(McRandom random) {
        if (random == null) throw new IllegalArgumentException("random is required");
        McRandom.PositionalFactory first = random.forkPositional();
        McRandom.PositionalFactory second = random.forkPositional();
        return new GradientSources(key -> new McGradientNoise(first.fromHashOf(key)),
                key -> new McGradientNoise(second.fromHashOf(key)));
    }

    private static GradientSources legacySources(LegacyRand random) {
        if (random == null) throw new IllegalArgumentException("random is required");
        LegacyRand.PositionalFactory first = random.forkPositional();
        LegacyRand.PositionalFactory second = random.forkPositional();
        return new GradientSources(key -> new McGradientNoise(first.fromHashOf(key)),
                key -> new McGradientNoise(second.fromHashOf(key)));
    }

    private record Octave(int index, double frequency, double amplitude) { }
    private record Layer(McGradientNoise noise, double frequency, float amplitude) { }
    private record GradientSources(GradientSource first, GradientSource second) { }

    @FunctionalInterface
    private interface GradientSource {
        McGradientNoise create(String key);
    }

    @FunctionalInterface
    private interface GradientSourcesFactory {
        GradientSources create();
    }
}
