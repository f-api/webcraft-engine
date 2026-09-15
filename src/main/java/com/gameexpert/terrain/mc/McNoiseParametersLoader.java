package com.gameexpert.terrain.mc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 고정된 vanilla noise JSON에서 옥타브 파라미터를 읽는다. */
public final class McNoiseParametersLoader {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private McNoiseParametersLoader() {
    }

    public static Parameters load(Path noiseJson) throws IOException {
        JsonNode root = MAPPER.readTree(Files.readAllBytes(noiseJson));
        JsonNode firstOctaveNode = root.get("firstOctave");
        JsonNode amplitudesNode = root.get("amplitudes");
        if (firstOctaveNode == null || !firstOctaveNode.isIntegralNumber()) {
            throw new IOException("firstOctave가 없거나 정수가 아닙니다: " + noiseJson);
        }
        if (amplitudesNode == null || !amplitudesNode.isArray() || amplitudesNode.isEmpty()) {
            throw new IOException("amplitudes가 없거나 빈 배열입니다: " + noiseJson);
        }
        double[] amplitudes = new double[amplitudesNode.size()];
        for (int i = 0; i < amplitudes.length; i++) {
            JsonNode amplitude = amplitudesNode.get(i);
            if (!amplitude.isNumber()) {
                throw new IOException("amplitudes 원소가 숫자가 아닙니다: " + noiseJson);
            }
            amplitudes[i] = amplitude.doubleValue();
        }
        return new Parameters(firstOctaveNode.intValue(), amplitudes);
    }

    public static final class Parameters {
        private final int firstOctave;
        private final double[] amplitudes;

        private Parameters(int firstOctave, double[] amplitudes) {
            this.firstOctave = firstOctave;
            this.amplitudes = Arrays.copyOf(amplitudes, amplitudes.length);
        }

        public int firstOctave() {
            return firstOctave;
        }

        public double[] amplitudes() {
            return Arrays.copyOf(amplitudes, amplitudes.length);
        }
    }
}
