package com.gameexpert.terrain.mc;

import java.util.Map;

/** 26.3-snapshot-7의 64개 noise JSON에서 파생한 정규화 파라미터 테이블이다. */
public final class Mc263NoiseRegistry {
    private static final double[] NONE = {};
    private static final Map<String, Parameters> PARAMETERS = Map.ofEntries(
            e("aquifer_barrier", .955388882960065, -3, 1),
            e("aquifer_fluid_level_floodedness", .955388882960065, -7, 1),
            e("aquifer_fluid_level_spread", .955388882960065, -5, 1),
            e("aquifer_lava", .955388882960065, -1, 1),
            e("badlands_pillar", .939546696581729, -2, 4),
            e("badlands_pillar_roof", .955388882960065, -8, 1),
            e("badlands_surface", .9381732587751008, -6, 3),
            e("calcite", .939546696581729, -9, 4),
            e("cave_cheese", .8361300524356068, -8, 9, .5, 1, 2, 1, 2, 1, 0, 2, 0),
            e("cave_entrance", .8500634887071167, -7, 3, .4, .5, 1),
            e("cave_layer", .955388882960065, -8, 1),
            e("clay_bands_offset", .955388882960065, -8, 1),
            e("continentalness", .8880832896205223, -9, 9, 1, 1, 2, 2, 2, 1, 1, 1, 1),
            e("continentalness_large", .8880832896205223, -11, 9, 1, 1, 2, 2, 2, 1, 1, 1, 1),
            e("erosion", 1.063180125160734, -9, 5, 1, 1, 0, 1, 1),
            e("erosion_large", 1.063180125160734, -11, 5, 1, 1, 0, 1, 1),
            e("gravel", .939546696581729, -8, 4),
            e("gravel_layer", 1.0569606747151457, -8, 9, 1, 1, 1, 1, 0, 0, 0, 0, .013333333333333334),
            e("ice", .939546696581729, -4, 4),
            e("iceberg_pillar", .939546696581729, -6, 4),
            e("iceberg_pillar_roof", .955388882960065, -3, 1),
            e("iceberg_surface", .9381732587751008, -6, 3),
            e("jagged", 1.0383104856073737, -16, 16),
            e("nether/temperature", .9494731054427981, -7, 2),
            e("nether/vegetation", .9494731054427981, -7, 2),
            e("nether_state_selector", .955388882960065, -4, 1),
            e("nether_wart", 1.3827102115748344, -3, 4, 1, 0, 0, .9),
            e("netherrack", 1.4659491761370222, -3, 4, 1, 0, 0, .35),
            e("noodle", .955388882960065, -8, 1),
            e("noodle_ridge_a", .955388882960065, -7, 1),
            e("noodle_ridge_b", .955388882960065, -7, 1),
            e("noodle_thickness", .955388882960065, -8, 1),
            e("offset", .9381732587751005, -3, 4, 1, 1, 1, 0),
            e("ore_gap", .955388882960065, -5, 1),
            e("ore_vein_a", .955388882960065, -7, 1),
            e("ore_vein_b", .955388882960065, -7, 1),
            e("ore_veininess", .955388882960065, -8, 1),
            e("packed_ice", .939546696581729, -7, 4),
            e("patch", 1.637127519350388, -5, 6, 1, 0, 0, 0, 0, .013333333333333334),
            e("pillar", .9494731054427981, -7, 2),
            e("pillar_rareness", .955388882960065, -8, 1),
            e("pillar_thickness", .955388882960065, -8, 1),
            e("powder_snow", .939546696581729, -6, 4),
            e("ridge", .9147152149950137, -7, 6, 1, 2, 1, 0, 0, 0),
            e("small_patch", .955388882960065, -3, 1, 3),
            e("soul_sand_layer", 1.0569606747151457, -8, 9, 1, 1, 1, 1, 0, 0, 0, 0, .013333333333333334),
            e("spaghetti_2d", .955388882960065, -7, 1),
            e("spaghetti_2d_elevation", .955388882960065, -8, 1),
            e("spaghetti_2d_modulator", .955388882960065, -11, 1),
            e("spaghetti_2d_thickness", .955388882960065, -11, 1),
            e("spaghetti_3d_1", .955388882960065, -7, 1),
            e("spaghetti_3d_2", .955388882960065, -7, 1),
            e("spaghetti_3d_rarity", .955388882960065, -11, 1),
            e("spaghetti_3d_thickness", .955388882960065, -8, 1),
            e("spaghetti_roughness", .955388882960065, -5, 1),
            e("spaghetti_roughness_modulator", .955388882960065, -8, 1),
            e("sulfur_cave_gradient", 1.1817507833955654, -5, 3, 1, 0, 1),
            e("surface", .9381732587751008, -6, 3),
            e("surface_secondary", 1.0582769165096106, -6, 4, 1, 1, 0, 1),
            e("surface_swamp", .955388882960065, -2, 1),
            e("temperature", 1.2453007926713473, -10, 6, 1.5, 0, 1, 0, 0, 0),
            e("temperature_large", 1.2453007926713473, -12, 6, 1.5, 0, 1, 0, 0, 0),
            e("vegetation", .9494731054427978, -8, 6, 1, 1, 0, 0, 0, 0),
            e("vegetation_large", .9494731054427978, -10, 6, 1, 1, 0, 0, 0, 0));

    static {
        if (PARAMETERS.size() != McTerrainDataPin.NOISE_PARAMETER_COUNT) {
            throw new ExceptionInInitializerError("26.3 noise table count mismatch: "
                    + PARAMETERS.size());
        }
    }

    private Mc263NoiseRegistry() { }

    public static McNormalNoise create(McRandom.PositionalFactory root, String identifier) {
        String id = normalize(identifier);
        Parameters parameters = PARAMETERS.get(id.substring("minecraft:".length()));
        if (parameters == null) {
            throw new IllegalArgumentException("unsupported 26.3 noise: " + id);
        }
        return new McNormalNoise(root.fromHashOf(id), parameters.baseAmplitude,
                parameters.baseOctave, parameters.octaveCount, parameters.modifiers);
    }

    public static Parameters parameters(String identifier) {
        String id = normalize(identifier);
        Parameters value = PARAMETERS.get(id.substring("minecraft:".length()));
        if (value == null) throw new IllegalArgumentException("unsupported 26.3 noise: " + id);
        return value.copy();
    }

    public static int size() {
        return PARAMETERS.size();
    }

    private static Map.Entry<String, Parameters> e(String id, double baseAmplitude,
            int baseOctave, int octaveCount, double... modifiers) {
        return Map.entry(id, new Parameters(baseAmplitude, baseOctave, octaveCount,
                modifiers.length == 0 ? NONE : modifiers.clone()));
    }

    private static String normalize(String identifier) {
        return identifier.indexOf(':') < 0 ? "minecraft:" + identifier : identifier;
    }

    public record Parameters(double baseAmplitude, int baseOctave, int octaveCount,
            double[] modifiers) {
        private Parameters copy() {
            return new Parameters(baseAmplitude, baseOctave, octaveCount, modifiers.clone());
        }

        @Override public double[] modifiers() {
            return modifiers.clone();
        }
    }
}
