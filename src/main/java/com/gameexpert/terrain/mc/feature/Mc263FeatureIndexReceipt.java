package com.gameexpert.terrain.mc.feature;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Exact global per-step feature indices for all 56 pinned 26.3 Overworld biome definitions.
 * The payload is self-checking and is independently reproducible from the source manifest.
 */
public final class Mc263FeatureIndexReceipt {
    public static final int BIOME_COUNT = 56;
    public static final int STEP_COUNT = 11;
    public static final int GLOBAL_FEATURE_COUNT = 171;
    public static final int BIOME_FEATURE_REFERENCE_COUNT = 2629;
    public static final String RAW_SHA256 =
            "c32a12ce6a6efbc873a1e4a20986bb27106cdf9d03f8ccfaeade7010b8a724cf";

    private static final int MAGIC = 0x46323633;
    private static final int PAYLOAD_VERSION = 1;
    private static final Receipt RECEIPT = load();

    private Mc263FeatureIndexReceipt() {
    }

    public record FeatureReference(String featureKey, int globalIndex) {
        public FeatureReference {
            if (featureKey == null || featureKey.isBlank() || globalIndex < 0) {
                throw new IllegalArgumentException("invalid feature reference");
            }
        }
    }

    public record StepFeatureData(int step, List<String> featureKeys) {
        public StepFeatureData {
            if (step < 0) throw new IllegalArgumentException("negative generation step");
            featureKeys = List.copyOf(featureKeys);
        }

        public String featureAt(int globalIndex) {
            return featureKeys.get(globalIndex);
        }

        public int indexOf(String featureKey) {
            int index = featureKeys.indexOf(requireMinecraftKey(featureKey));
            if (index < 0) {
                throw new IllegalArgumentException("feature is absent from step " + step + ": "
                        + featureKey);
            }
            return index;
        }
    }

    public record BiomeFeatureData(String biomeKey, List<List<FeatureReference>> featuresByStep) {
        public BiomeFeatureData {
            biomeKey = requireMinecraftKey(biomeKey);
            List<List<FeatureReference>> copy = new ArrayList<>(featuresByStep.size());
            for (List<FeatureReference> step : featuresByStep) copy.add(List.copyOf(step));
            featuresByStep = List.copyOf(copy);
        }

        public List<FeatureReference> featuresAtStep(int step) {
            if (step < 0 || step >= featuresByStep.size()) {
                throw new IllegalArgumentException("generation step outside pinned range: " + step);
            }
            return featuresByStep.get(step);
        }

        public int globalIndex(int step, String featureKey) {
            String key = requireMinecraftKey(featureKey);
            for (FeatureReference reference : featuresAtStep(step)) {
                if (reference.featureKey().equals(key)) return reference.globalIndex();
            }
            throw new IllegalArgumentException(key + " is absent from " + biomeKey
                    + " step " + step);
        }
    }

    public static List<StepFeatureData> steps() {
        return RECEIPT.steps();
    }

    public static StepFeatureData step(int step) {
        if (step < 0 || step >= RECEIPT.steps().size()) {
            throw new IllegalArgumentException("generation step outside pinned range: " + step);
        }
        return RECEIPT.steps().get(step);
    }

    public static List<BiomeFeatureData> biomes() {
        return RECEIPT.biomes();
    }

    public static BiomeFeatureData biome(String biomeKey) {
        BiomeFeatureData biome = RECEIPT.byBiome().get(requireMinecraftKey(biomeKey));
        if (biome == null) throw new IllegalArgumentException("unknown pinned biome: " + biomeKey);
        return biome;
    }

    private static Receipt load() {
        try {
            byte[] raw;
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(
                    Base64.getDecoder().decode(Payload.GZIP_BASE64)))) {
                raw = gzip.readAllBytes();
            }
            String hash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw));
            if (!RAW_SHA256.equals(hash)) {
                throw new IOException("26.3 feature-index payload SHA-256 mismatch: " + hash);
            }
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(raw))) {
                return readAndVerify(input);
            }
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static Receipt readAndVerify(DataInputStream input) throws IOException {
        if (input.readInt() != MAGIC || input.readUnsignedShort() != PAYLOAD_VERSION) {
            throw new IOException("unsupported 26.3 feature-index payload header");
        }
        int biomeCount = input.readUnsignedShort();
        int stepCount = input.readUnsignedShort();
        int globalCount = input.readInt();
        int referenceCount = input.readInt();
        if (biomeCount != BIOME_COUNT || stepCount != STEP_COUNT
                || globalCount != GLOBAL_FEATURE_COUNT
                || referenceCount != BIOME_FEATURE_REFERENCE_COUNT) {
            throw new IOException("26.3 feature-index payload count mismatch");
        }

        List<StepFeatureData> steps = new ArrayList<>(stepCount);
        int decodedGlobalCount = 0;
        for (int step = 0; step < stepCount; step++) {
            int count = input.readUnsignedShort();
            List<String> keys = new ArrayList<>(count);
            Set<String> unique = new HashSet<>();
            for (int i = 0; i < count; i++) {
                String key = readMinecraftKey(input);
                if (!unique.add(key)) {
                    throw new IOException("duplicate global feature in step " + step + ": " + key);
                }
                keys.add(key);
            }
            decodedGlobalCount += count;
            steps.add(new StepFeatureData(step, keys));
        }
        if (decodedGlobalCount != globalCount) {
            throw new IOException("decoded global feature count mismatch: " + decodedGlobalCount);
        }

        List<BiomeFeatureData> biomes = new ArrayList<>(biomeCount);
        Map<String, BiomeFeatureData> byBiome = new LinkedHashMap<>();
        List<McFeatureSorter.BiomeFeatures> sorterInput = new ArrayList<>(biomeCount);
        int decodedReferenceCount = 0;
        for (int b = 0; b < biomeCount; b++) {
            String biomeKey = readMinecraftKey(input);
            int biomeStepCount = input.readUnsignedShort();
            if (biomeStepCount != stepCount) {
                throw new IOException("missing feature steps for " + biomeKey + ": " + biomeStepCount);
            }
            List<List<FeatureReference>> referencesByStep = new ArrayList<>(stepCount);
            List<List<String>> keysByStep = new ArrayList<>(stepCount);
            for (int step = 0; step < stepCount; step++) {
                int count = input.readUnsignedShort();
                List<FeatureReference> references = new ArrayList<>(count);
                List<String> keys = new ArrayList<>(count);
                Set<Integer> uniqueIndices = new HashSet<>();
                for (int i = 0; i < count; i++) {
                    int index = input.readUnsignedShort();
                    if (index >= steps.get(step).featureKeys().size()) {
                        throw new IOException("missing global feature index " + index + " for "
                                + biomeKey + " step " + step);
                    }
                    if (!uniqueIndices.add(index)) {
                        throw new IOException("duplicate feature-index ambiguity in " + biomeKey
                                + " step " + step + ": " + index);
                    }
                    String key = steps.get(step).featureAt(index);
                    references.add(new FeatureReference(key, index));
                    keys.add(key);
                }
                decodedReferenceCount += count;
                referencesByStep.add(references);
                keysByStep.add(keys);
            }
            BiomeFeatureData biome = new BiomeFeatureData(biomeKey, referencesByStep);
            if (byBiome.putIfAbsent(biomeKey, biome) != null) {
                throw new IOException("duplicate biome feature definition: " + biomeKey);
            }
            biomes.add(biome);
            sorterInput.add(new McFeatureSorter.BiomeFeatures(biomeKey, keysByStep));
        }
        if (decodedReferenceCount != referenceCount) {
            throw new IOException("decoded biome feature reference count mismatch: "
                    + decodedReferenceCount);
        }
        if (input.read() != -1) throw new IOException("trailing 26.3 feature-index payload bytes");

        verifySorterReceipt(sorterInput, steps);
        return new Receipt(List.copyOf(steps), List.copyOf(biomes), Map.copyOf(byBiome));
    }

    private static void verifySorterReceipt(List<McFeatureSorter.BiomeFeatures> input,
            List<StepFeatureData> expected) throws IOException {
        try {
            McFeatureSorter.requireUnambiguous(input);
            List<McFeatureSorter.StepFeatures> actual = McFeatureSorter.sort(input);
            if (actual.size() != expected.size()) throw new IOException("missing sorted steps");
            for (int step = 0; step < actual.size(); step++) {
                if (!actual.get(step).featureKeys().equals(expected.get(step).featureKeys())) {
                    throw new IOException("global FeatureSorter order mismatch at step " + step);
                }
            }
        } catch (IllegalArgumentException | IllegalStateException failure) {
            throw new IOException("invalid 26.3 feature-index graph", failure);
        }
    }

    private static String readMinecraftKey(DataInputStream input) throws IOException {
        String key = input.readUTF();
        try {
            return requireMinecraftKey(key);
        } catch (IllegalArgumentException failure) {
            throw new IOException("invalid feature-index key", failure);
        }
    }

    private static String requireMinecraftKey(String key) {
        if (key == null || !key.startsWith("minecraft:")
                || key.length() == "minecraft:".length()) {
            throw new IllegalArgumentException("exact minecraft namespaced key required: " + key);
        }
        return key;
    }

    private record Receipt(List<StepFeatureData> steps, List<BiomeFeatureData> biomes,
                           Map<String, BiomeFeatureData> byBiome) {
    }

    private static final class Payload {
        private static final String GZIP_BASE64 =
            "H4sIAAAAAAAC/81aCXfcthEeyUdsK42dWLLOtWQ5aZq26eE0bpqmTnokaZu0TXo3PVgsCe7SCx4PJLWV/1L/ZAfHcocEKL+XiLu1" +
            "nnffzmCADzODOUB+9OjxW7AB78AWAPwX4NaH+H0VjtMk46FkcfWuYDMeCHbGgjqLuJzIHL/h0DegrGXMQg73l0yZ5xWPkCPiWgZl" +
            "IZNsAjtLvmUUeS7gGuwtGUnIx7haULBwxiO453LGouZUgqW8mp6XVTDhecRhnyKUEx5EMinKKs84XT/OJUcRmYcz3PY9yijLRAR1" +
            "UXDpoYt83qaneVZWXOJMeUq1Q+lBxHmBy9xtbQa1ksx4l1iwKpxSpBEvuayCORcCXlmSlRYCFECznVI67iuIElnBdps2keyMC4rQ" +
            "UrOk4na7PUyz58PuGrnslVwwjeRRm8nQncqlaB/XyHZ2VtVxTG2vaGHOFvbyccw8HU4i88zK7Hs4aRJFgnuFypS1DaE1lYuoO1rR" +
            "XHdRHMkj449HfrqV2unqlKFPRV0hSw5SHiV16hrCcPVJ6BMd1zLBo3a3zRWsSEo48BAXAgddbSt92qW2fTwqoYPKnKkjkrJJyroq" +
            "DQU796qU/6eSjM4fJeVM+WpZ0i1oaone5BD1zDuuvDoeHZ3zlEuGpn2BrleGtZgFZzzJaLg0VH169XEPIiZnNCI2oRAPPWKoVXSg" +
            "nkH51IxN+GqECLPIk0zF2WWM6/haksUY59BYV1pr6XgcaPW3orKhq6BOnYWQg1jmz3gGKRWboMMGIgmnyHiwJFeS8zKYJ1lUznlR" +
            "oTXOWJYxuEOiGEvHeU53ZCio3gmvWJXkGYy6M5YFkyUPntbZRLSSjmFHrCgE6sSEeHidzC3zOR7uupyaqNweSAKBMaNUCWwq6zH1" +
            "Q7PGmEUCfaukSrXIRTKZVpRuJCzag+4iFYYT6727zkatwmje0rEBLSdTaiEzl55msdLIz13MeULZAs8WkxHPqN73OyNSzIA2PY38" +
            "wgZcSYFZvGQUXVmdEat+uvKxI2/HLBZwXKI1jLrEgtHi7zl1wGLi0yVnnojIkoNxIlGBVpqEAkNXJqQ2si6iePCGl6yyXJCzWSA4" +
            "i9FjqqqdCLuuETyiboxhYBqEGK/KIOSJUEeTKI/AU2OCM/xZtqoiIq5C4bGXRWY8cso69gztyQK1I+q1ZaFCzligo2Ax9CoBInAT" +
            "SWiw6AWCmLOqltw9dWWdxU7dQD045SzK53Rda13LcI6epR/5bWu5J/1xy9rdObqmvIy5zOC1rrTKVFgyz6tpUCALbZlMGDWiMwyD" +
            "bB0uBjre1ENG2TNOLdjFPk2EKF25Msvn525YS1lmZnQF5iwtWvWw0bmhO8NNZtnuamyM/uXapxAsyUqPQS3j0O8HmuuRwjQk5bm7" +
            "jKXvOQIRj1ktqr51jOp7nNH6xugCUTy8PcJZLrGadI9AhC5pdPWwN3ktPYemXJWyfEOcJbSBRCLOabDsLGFse9Qzu+He9++syY8n" +
            "zt7kuR1jGhtX683uUXGn/eLNEqf9EzRjjns3aW0w6tmlZT9woxRmM4xkeLTtPh5eMKTB4WyWJoCTC2Yw2iYhtUGYlGpqGq/7Leq4" +
            "cmuvhrvbRVHUaTFLMtfHQxZWdWPHk152mEumSlDHUHEieSzOja3MDt+8cEzGmTSua4eTw1xyZisczTnoVyYNwxcu4p1eJmfIub3k" +
            "mATrHPGUC9Uu6lqVBkjCdYXGKkRht5RiY+ZuwXAlk62Qr2rBIA85a1Vvux7oumrcbjEwO4Uz0WoNm+G6jdEypIWacWGJ+x4Re1x2" +
            "e2bzMkLVPPeur7nd9TVxgyooxhD/DLNnXmBaPm+39Y2DxwkXeAjVldcm/t/Av039dwXUv5GmqV9X4Rpcx67vBtyEWzj+RfgavAS3" +
            "4Q68DK/AXdiGHbgHu7APB3AfjuGkmfE6fv4e/gB/hL/DP/A3tFo5tSHTQBmDEShX7OIGjvl9GYBu4ufb8B58DJ/Cb+BPDSyayv8/" +
            "EO13FKWM7IC6fItteeDMQGhItJZeH5qnLhqtoPWgSSwaEgHXA2RqgYw6ahH1jC9j4sphxTDRsPZoW7VWRJFFtO3LGYOiedGDJkQ8" +
            "XOOhF09Vnp0H5RSr6UEB3cBPN/IQVzbFQxvC1WZh9e/wS8E4bma8jZ9vwQ/gA/gp/AI+hI80mM8Rzl/hb/BF13cWLVnwfGRHXwrZ" +
            "iGB7AT8fWWRfuIlCd41BKfKC92fR+1/BTA90Bl0mHkCteDHYznBIV1Fp6vseZ9npYhlzFk7X4LWtu6pFv2/usbGUNY3/cEYys97C" +
            "z+/B44vPlLlSGN5f1Pd3rcfs+5Szbp04rmM6rqHzwbfgOwjmfV2VGjBjDefApyN7obEKJX3zeY6zLvUwDYQ8plhBuNnS8zyGH8I7" +
            "8KOOSggSe1s5tHmuazRfh9fhG/BG1yzrCnh3utf2gxtkD3Oq8tEnHSTkOqjvBncJbVMn6ssF95L22zcR2k8wWf8Mfk4AGu/d7XmG" +
            "MqjO1Kk2vAvazdbDlKGrzz2kH3ptuON9DDW4S20jpAWYz/xw6MOyoSucxagn/kbcPi1U9VbFWT2485jxtzumetkBtCYgJALZp6xD" +
            "+6+SUeOXOP4F/9ZY6LtAi+vly00LRx69fAy/hk80mt8hnr+4erG3wX19yoamXpaVVI74JfxK4/kt4vmzU/vlecSjYEUKUrPf6FER" +
            "CYBP2WSCoArOZqtoozb0t6fdHhrAYlbouXA0N+lDn+S3n1dlDg9jywvjn91LmeadQL9RNq0ih2xu92hSbL0dM6R+ruGnkX2iURx7" +
            "yyvyHH0VxdW3LyiuDuhrW1n7afXwTcJr3ibBX5OuvNg68BZbrZdE6NtbQ2vrqpM9gy4g+97WihK6msGf0Ena4nKVaas/rx/6bghW" +
            "UYLd1J/vwQf9bYR5nSMY+nJpGaFGeOTehR/3JLNVZ/N97xui6tWtTnS88pVMs9fA2NQyy/581IpCnVS2fJHsklVy6HGUUaOoU3gI" +
            "rzo3xubF2iWadjV6OY5LU7SpcBWKu51HU/qd4C292GUubTzCa5T/AaAfnpXoMQAA";

        private Payload() {
        }
    }
}
