package com.gameexpert.terrain.mc;

import java.util.Arrays;

/** Minecraft의 modern positional 옥타브 PerlinNoise를 구현한다. */
public final class McPerlinNoise {
    private static final double WRAP = 33554432.0;

    private final int firstOctave;
    private final double[] amplitudes;
    private final McImprovedNoise[] levels;
    private final double lowestFreqInputFactor;
    private final double lowestFreqValueFactor;
    private final double maxValue;

    public McPerlinNoise(McRandom random, int firstOctave, double[] amplitudes) {
        if (amplitudes.length == 0) {
            throw new IllegalArgumentException("amplitudes must not be empty");
        }
        int zeroOctaveIndex = -firstOctave;
        int last = zeroOctaveIndex + amplitudes.length - 1;
        if (last < 0) {
            throw new IllegalArgumentException("positive first octave is not supported");
        }
        this.firstOctave = firstOctave;
        this.amplitudes = Arrays.copyOf(amplitudes, amplitudes.length);
        this.levels = new McImprovedNoise[amplitudes.length];
        McRandom.PositionalFactory factory = random.forkPositional();
        for (int i = 0; i < amplitudes.length; i++) {
            if (amplitudes[i] != 0.0) {
                levels[i] = new McImprovedNoise(factory.fromHashOf("octave_" + (firstOctave + i)));
            }
        }
        lowestFreqInputFactor = Math.pow(2.0, firstOctave);
        lowestFreqValueFactor = Math.pow(2.0, amplitudes.length - 1)
                / (Math.pow(2.0, amplitudes.length) - 1.0);
        maxValue = edgeValue(2.0);
    }

    public double getValue(double x, double y, double z) {
        return getValue(x, y, z, 0.0, 0.0, false);
    }

    public double getValue(double x, double y, double z,
                           double yScale, double yMax, boolean useOrigin) {
        double result = 0.0;
        double inputFactor = lowestFreqInputFactor;
        double valueFactor = lowestFreqValueFactor;
        for (int i = 0; i < levels.length; i++) {
            McImprovedNoise level = levels[i];
            if (level != null) {
                double sampleY = useOrigin ? -level.yo() : wrap(y * inputFactor);
                result += level.noise(
                        wrap(x * inputFactor), sampleY, wrap(z * inputFactor),
                        yScale * inputFactor, yMax * inputFactor)
                        * amplitudes[i] * valueFactor;
            }
            inputFactor *= 2.0;
            valueFactor /= 2.0;
        }
        return result;
    }

    public double maxValue() {
        return maxValue;
    }

    public int firstOctave() {
        return firstOctave;
    }

    public double[] amplitudes() {
        return Arrays.copyOf(amplitudes, amplitudes.length);
    }

    public static double wrap(double value) {
        return value - Math.floor(value / WRAP + 0.5) * WRAP;
    }

    private double edgeValue(double value) {
        double result = 0.0;
        double factor = lowestFreqValueFactor;
        for (double amplitude : amplitudes) {
            result += amplitude * value * factor;
            factor /= 2.0;
        }
        return result;
    }
}
