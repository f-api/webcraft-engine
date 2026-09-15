package com.gameexpert.terrain.mc.ore;

import java.util.List;

/**
 * Pure 26.3-snapshot-7 ore-vein density projection and filler decision. Noise sampling and the
 * positional random factory stay with the canonical terrain authority so their seed streams cannot
 * silently diverge.
 */
public final class Mc263OreVeinifier {
    public static final class NoiseSpec {
        private final String key;
        private final double baseAmplitude;
        private final int baseOctave;

        private NoiseSpec(String key, double baseAmplitude, int baseOctave) {
            this.key = key;
            this.baseAmplitude = baseAmplitude;
            this.baseOctave = baseOctave;
        }

        public String key() {
            return key;
        }

        public double baseAmplitude() {
            return baseAmplitude;
        }

        public int baseOctave() {
            return baseOctave;
        }
    }

    public enum Result {
        NONE,
        FILLER,
        ORE,
        RAW_ORE
    }

    @FunctionalInterface
    public interface FloatRandom {
        float nextFloat();
    }

    @FunctionalInterface
    public interface FloatValue {
        float getAsFloat();
    }

    public enum VeinType {
        COPPER(0, 50, 1.0f, "minecraft:copper_ore", "minecraft:raw_copper_block",
                "minecraft:granite"),
        IRON(-60, -8, -1.0f, "minecraft:deepslate_iron_ore", "minecraft:raw_iron_block",
                "minecraft:tuff");

        private final int minYInclusive;
        private final int maxYExclusive;
        private final float toggleSign;
        private final String oreBlock;
        private final String rawOreBlock;
        private final String fillerBlock;

        VeinType(int minYInclusive, int maxYExclusive, float toggleSign, String oreBlock,
                String rawOreBlock, String fillerBlock) {
            this.minYInclusive = minYInclusive;
            this.maxYExclusive = maxYExclusive;
            this.toggleSign = toggleSign;
            this.oreBlock = oreBlock;
            this.rawOreBlock = rawOreBlock;
            this.fillerBlock = fillerBlock;
        }

        public int minYInclusive() {
            return minYInclusive;
        }

        public int maxYExclusive() {
            return maxYExclusive;
        }

        public String oreBlock() {
            return oreBlock;
        }

        public String rawOreBlock() {
            return rawOreBlock;
        }

        public String fillerBlock() {
            return fillerBlock;
        }

        public float rawOreChance() {
            return 0.02f;
        }
    }

    public static final float VEININESS_THRESHOLD = 0.4f;
    public static final int EDGE_ROUNDOFF_BEGIN = 20;
    public static final float MAX_EDGE_ROUNDOFF = 0.2f;
    public static final float VEIN_SOLIDNESS = 0.7f;
    public static final float MIN_RICHNESS = 0.1f;
    public static final float MAX_RICHNESS = 0.3f;
    public static final float MASK_CUTOFF = 0.08f;
    public static final float GAP_OFFSET = -0.3f;
    public static final float TOGGLE_XZ_SCALE = 1.5f;
    public static final float TOGGLE_Y_SCALE = 1.5f;
    public static final int TOGGLE_NOISE_MIN_Y_INCLUSIVE = -64;
    public static final int TOGGLE_NOISE_MAX_Y_EXCLUSIVE = 57;
    public static final float MASK_NOISE_XZ_SCALE = 4.0f;
    public static final float MASK_NOISE_Y_SCALE = 4.0f;
    public static final float GAP_NOISE_XZ_SCALE = 1.0f;
    public static final float GAP_NOISE_Y_SCALE = 1.0f;

    private static final List<NoiseSpec> NOISE_SPECS = List.of(
            noise("ore_gap", -5),
            noise("ore_vein_a", -7),
            noise("ore_vein_b", -7),
            noise("ore_veininess", -8)
    );

    private Mc263OreVeinifier() {
    }

    public static List<NoiseSpec> noiseSpecs() {
        return NOISE_SPECS;
    }

    /** Exact projection of {@code overworld/ore_vein/mask.json}. */
    public static float mask(float toggle, float oreVeinNoiseA, float oreVeinNoiseB) {
        if (toggle >= -VEININESS_THRESHOLD && toggle < VEININESS_THRESHOLD) {
            return -1.0f;
        }
        return MASK_CUTOFF - Math.max(Math.abs(oreVeinNoiseA), Math.abs(oreVeinNoiseB));
    }

    /** Exact projection of the selected copper/iron density function JSON. */
    public static float density(VeinType type, int y, float toggle, float mask) {
        if (y < type.minYInclusive || y >= type.maxYExclusive) {
            return -1.0f;
        }
        if (mask < 0.0f || mask >= 1_000_000.0f) {
            return -1.0f;
        }
        float edgeDistance = Math.min(type.maxYExclusive - y, y - type.minYInclusive);
        float edgeRoundoff = clamp(edgeDistance, 0.0f, EDGE_ROUNDOFF_BEGIN) * 0.01f
                - MAX_EDGE_ROUNDOFF;
        float selector = type.toggleSign * toggle - VEININESS_THRESHOLD + edgeRoundoff;
        return selector >= 0.0f && selector < 1_000_000.0f ? VEIN_SOLIDNESS : -1.0f;
    }

    /** Exact projection of {@code overworld/ore_vein/richness.json}. */
    public static float richness(float toggle) {
        return clamp(Math.abs(toggle), VEININESS_THRESHOLD, 0.6f) - 0.3f;
    }

    /** Exact projection of {@code overworld/ore_vein/gap.json}. */
    public static float fillerGap(float oreGapNoise) {
        return GAP_OFFSET - oreGapNoise;
    }

    /**
     * Reproduces {@code OreVeinifier.createFiller}'s conditional nextFloat order. The first draw is
     * consumed only for positive density, the second only after that draw passes, and the raw-ore
     * draw only on the ore branch.
     */
    public static Result decide(VeinType type, int y, float toggle, float oreVeinNoiseA,
            float oreVeinNoiseB, float oreGapNoise, FloatRandom random) {
        return decide(type, y, toggle, () -> oreVeinNoiseA, () -> oreVeinNoiseB,
                () -> oreGapNoise, random);
    }

    /**
     * Lazy density-function counterpart. Range choices avoid ridge sampling inside the toggle
     * dead zone, and the gap noise is evaluated only after both random gates pass.
     */
    public static Result decide(VeinType type, int y, float toggle, FloatValue oreVeinNoiseA,
            FloatValue oreVeinNoiseB, FloatValue oreGapNoise, FloatRandom random) {
        float veinMask = toggle >= -VEININESS_THRESHOLD && toggle < VEININESS_THRESHOLD
                ? -1.0F
                : MASK_CUTOFF - Math.max(Math.abs(oreVeinNoiseA.getAsFloat()),
                        Math.abs(oreVeinNoiseB.getAsFloat()));
        float veinDensity = density(type, y, toggle, veinMask);
        if (veinDensity <= 0.0f) {
            return Result.NONE;
        }
        if (checkedFloat(random) > veinDensity) {
            return Result.NONE;
        }
        if (checkedFloat(random) < richness(toggle)
                && fillerGap(oreGapNoise.getAsFloat()) < 0.0f) {
            return checkedFloat(random) < type.rawOreChance() ? Result.RAW_ORE : Result.ORE;
        }
        return Result.FILLER;
    }

    private static float checkedFloat(FloatRandom random) {
        float value = random.nextFloat();
        if (!(value >= 0.0f && value < 1.0f)) {
            throw new IllegalArgumentException("random float must be in [0, 1)");
        }
        return value;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static NoiseSpec noise(String key, int baseOctave) {
        return new NoiseSpec(key, 0.955388882960065, baseOctave);
    }
}
