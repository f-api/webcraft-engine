package com.gameexpert.terrain.mc.feature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Exact dynamic-registry order and Overworld membership receipt for pinned 26.3 structures. */
public final class Mc263StructureIndexReceipt {
    public static final int STRUCTURE_COUNT = 52;
    public static final int STRUCTURE_SET_COUNT = 21;
    public static final int SOURCE_FILE_COUNT = 105;
    public static final String SOURCE_AGGREGATE_SHA256 =
            "b90906b65017511c1e7275ce5bd5a96d66f8460f7b2ff4083e5da3bba46552c8";
    public static final String CHUNK_GENERATOR_CLASS_SHA256 =
            "71bcd9ce3b9de7f5132fad52a481be6b804f1af6f83c02fd1183af4adc583b12";
    public static final String RESOURCE_MANAGER_REGISTRY_LOAD_TASK_CLASS_SHA256 =
            "ff8168ca70c6082838e2c47b60dc7cc5ee91a6029c784d4ad51a9318eb1b6c79";
    public static final String MAPPED_REGISTRY_CLASS_SHA256 =
            "4f3279ff6a2da3650551bf5744640986a936b14f883e807f2d23079d88ecda40";

    public static final List<Integer> COUNTS_BY_STEP =
            List.of(0, 0, 0, 5, 44, 0, 0, 3, 0, 0, 0);

    private static final List<Entry> ENTRIES = List.of(
            new Entry(3, 0, "minecraft:buried_treasure", 0x00000001008000L),
            new Entry(3, 1, "minecraft:mineshaft", 0x7effebffffffffL),
            new Entry(3, 2, "minecraft:mineshaft_mesa", 0x01001400000000L),
            new Entry(3, 3, "minecraft:trail_ruins", 0x00280204280000L),
            new Entry(3, 4, "minecraft:trial_chambers", 0x7fffffffffffffL),
            new Entry(4, 0, "minecraft:abandoned_camp_bamboo_jungle", 0x00800000000000L),
            new Entry(4, 1, "minecraft:abandoned_camp_birch_forest", 0x00000010000000L),
            new Entry(4, 2, "minecraft:abandoned_camp_cherry_grove", 0x04000000000000L),
            new Entry(4, 3, "minecraft:abandoned_camp_dappled_forest", 0x00040000000000L),
            new Entry(4, 4, "minecraft:abandoned_camp_flower_forest", 0x00000008000000L),
            new Entry(4, 5, "minecraft:abandoned_camp_forest", 0x00000002000000L),
            new Entry(4, 6, "minecraft:abandoned_camp_meadow", 0x00000000800000L),
            new Entry(4, 7, "minecraft:abandoned_camp_old_growth_birch_forest", 0x00200000000000L),
            new Entry(4, 8, "minecraft:abandoned_camp_old_growth_pine_taiga", 0x00080000000000L),
            new Entry(4, 9, "minecraft:abandoned_camp_old_growth_spruce_taiga", 0x00000004000000L),
            new Entry(4, 10, "minecraft:abandoned_camp_pale_garden", 0x00000040000000L),
            new Entry(4, 11, "minecraft:abandoned_camp_savanna", 0x00000100000000L),
            new Entry(4, 12, "minecraft:abandoned_camp_snowy_taiga", 0x00000000080000L),
            new Entry(4, 13, "minecraft:abandoned_camp_sparse_jungle", 0x00400000000000L),
            new Entry(4, 14, "minecraft:abandoned_camp_swamp", 0x00000000000800L),
            new Entry(4, 15, "minecraft:abandoned_camp_taiga", 0x00000000200000L),
            new Entry(4, 16, "minecraft:abandoned_camp_windswept_forest", 0x00000000100000L),
            new Entry(4, 17, "minecraft:abandoned_camp_wooded_badlands", 0x00001000000000L),
            new Entry(4, 18, "minecraft:bastion_remnant", 0L),
            new Entry(4, 19, "minecraft:desert_pyramid", 0x00000800000000L),
            new Entry(4, 20, "minecraft:end_city", 0L),
            new Entry(4, 21, "minecraft:igloo", 0x00000000086000L),
            new Entry(4, 22, "minecraft:jungle_pyramid", 0x00800200000000L),
            new Entry(4, 23, "minecraft:mansion", 0x00000060000000L),
            new Entry(4, 24, "minecraft:monument", 0x000000000000aaL),
            new Entry(4, 25, "minecraft:ocean_ruin_cold", 0x0000000000007eL),
            new Entry(4, 26, "minecraft:ocean_ruin_warm", 0x00000000000380L),
            new Entry(4, 27, "minecraft:pillager_outpost", 0x0c006900e26000L),
            new Entry(4, 28, "minecraft:ruined_portal", 0x703f817f6ac001L),
            new Entry(4, 29, "minecraft:ruined_portal_desert", 0x00000800000000L),
            new Entry(4, 30, "minecraft:ruined_portal_jungle", 0x00c00200000000L),
            new Entry(4, 31, "minecraft:ruined_portal_mountain", 0x0f007480952400L),
            new Entry(4, 32, "minecraft:ruined_portal_nether", 0L),
            new Entry(4, 33, "minecraft:ruined_portal_ocean", 0x000000000003feL),
            new Entry(4, 34, "minecraft:ruined_portal_swamp", 0x00000000001800L),
            new Entry(4, 35, "minecraft:shipwreck", 0x000000000003feL),
            new Entry(4, 36, "minecraft:shipwreck_beached", 0x00000001008000L),
            new Entry(4, 37, "minecraft:stronghold", 0xffffffffffffffL),
            new Entry(4, 38, "minecraft:swamp_hut", 0x00000000000800L),
            new Entry(4, 39, "minecraft:village_desert", 0x00000800000000L),
            new Entry(4, 40, "minecraft:village_plains", 0x00000000c00000L),
            new Entry(4, 41, "minecraft:village_savanna", 0x00000100000000L),
            new Entry(4, 42, "minecraft:village_snowy", 0x00000000004000L),
            new Entry(4, 43, "minecraft:village_taiga", 0x00000000200000L),
            new Entry(7, 0, "minecraft:ancient_city", 0x80000000000000L),
            new Entry(7, 1, "minecraft:fortress", 0L),
            new Entry(7, 2, "minecraft:nether_fossil", 0L));

    private static final List<PlacementRecord> PLACEMENTS = List.of(
            random("minecraft:abandoned_camp", 34, 8, 91231127, "linear", 1.0, "default", "", "", -1, "minecraft:abandoned_camp_bamboo_jungle=1,minecraft:abandoned_camp_birch_forest=1,minecraft:abandoned_camp_cherry_grove=1,minecraft:abandoned_camp_dappled_forest=1,minecraft:abandoned_camp_flower_forest=1,minecraft:abandoned_camp_forest=1,minecraft:abandoned_camp_meadow=1,minecraft:abandoned_camp_old_growth_pine_taiga=1,minecraft:abandoned_camp_old_growth_birch_forest=1,minecraft:abandoned_camp_old_growth_spruce_taiga=1,minecraft:abandoned_camp_pale_garden=1,minecraft:abandoned_camp_savanna=1,minecraft:abandoned_camp_snowy_taiga=1,minecraft:abandoned_camp_sparse_jungle=1,minecraft:abandoned_camp_swamp=1,minecraft:abandoned_camp_taiga=1,minecraft:abandoned_camp_windswept_forest=1,minecraft:abandoned_camp_wooded_badlands=1"),
            random("minecraft:ancient_cities", 24, 8, 20083232, "linear", 1.0, "default", "", "", -1, "minecraft:ancient_city=1"),
            random("minecraft:buried_treasures", 1, 0, 0, "linear", 0.01, "legacy_type_2", "9,0,9", "", -1, "minecraft:buried_treasure=1"),
            random("minecraft:desert_pyramids", 32, 8, 14357617, "linear", 1.0, "default", "", "", -1, "minecraft:desert_pyramid=1"),
            random("minecraft:end_cities", 20, 11, 10387313, "triangular", 1.0, "default", "", "", -1, "minecraft:end_city=1"),
            random("minecraft:igloos", 32, 8, 14357618, "linear", 1.0, "default", "", "", -1, "minecraft:igloo=1"),
            random("minecraft:jungle_temples", 32, 8, 14357619, "linear", 1.0, "default", "", "", -1, "minecraft:jungle_pyramid=1"),
            random("minecraft:mineshafts", 1, 0, 0, "linear", 0.004, "legacy_type_3", "", "", -1, "minecraft:mineshaft=1,minecraft:mineshaft_mesa=1"),
            random("minecraft:nether_complexes", 27, 4, 30084232, "linear", 1.0, "default", "", "", -1, "minecraft:fortress=2,minecraft:bastion_remnant=3"),
            random("minecraft:nether_fossils", 2, 1, 14357921, "linear", 1.0, "default", "", "", -1, "minecraft:nether_fossil=1"),
            random("minecraft:ocean_monuments", 32, 5, 10387313, "triangular", 1.0, "default", "", "", -1, "minecraft:monument=1"),
            random("minecraft:ocean_ruins", 20, 8, 14357621, "linear", 1.0, "default", "", "", -1, "minecraft:ocean_ruin_cold=1,minecraft:ocean_ruin_warm=1"),
            random("minecraft:pillager_outposts", 32, 8, 165745296, "linear", 0.2, "legacy_type_1", "", "minecraft:villages", 10, "minecraft:pillager_outpost=1"),
            random("minecraft:ruined_portals", 40, 15, 34222645, "linear", 1.0, "default", "", "", -1, "minecraft:ruined_portal=1,minecraft:ruined_portal_desert=1,minecraft:ruined_portal_jungle=1,minecraft:ruined_portal_swamp=1,minecraft:ruined_portal_mountain=1,minecraft:ruined_portal_ocean=1,minecraft:ruined_portal_nether=1"),
            random("minecraft:shipwrecks", 24, 4, 165745295, "linear", 1.0, "default", "", "", -1, "minecraft:shipwreck=1,minecraft:shipwreck_beached=1"),
            new PlacementRecord("minecraft:strongholds", "minecraft:concentric_rings", -1, -1, 0, "", 1.0, "default", "", "", -1, 32, 3, 128, "#minecraft:stronghold_biased_to", "minecraft:stronghold=1"),
            random("minecraft:swamp_huts", 32, 8, 14357620, "linear", 1.0, "default", "", "", -1, "minecraft:swamp_hut=1"),
            random("minecraft:trail_ruins", 34, 8, 83469867, "linear", 1.0, "default", "", "", -1, "minecraft:trail_ruins=1"),
            random("minecraft:trial_chambers", 34, 12, 94251327, "linear", 1.0, "default", "", "", -1, "minecraft:trial_chambers=1"),
            random("minecraft:villages", 34, 8, 10387312, "linear", 1.0, "default", "", "", -1, "minecraft:village_plains=1,minecraft:village_desert=1,minecraft:village_savanna=1,minecraft:village_snowy=1,minecraft:village_taiga=1"),
            random("minecraft:woodland_mansions", 80, 20, 10387319, "triangular", 1.0, "default", "", "", -1, "minecraft:mansion=1"));

    static {
        verify();
    }

    private Mc263StructureIndexReceipt() { }

    public static List<Entry> entries() { return ENTRIES; }

    public static List<Entry> step(int step) {
        if (step < 0 || step >= Mc263FeatureIndexReceipt.STEP_COUNT) {
            throw new IllegalArgumentException("generation step outside pinned range: " + step);
        }
        return ENTRIES.stream().filter(entry -> entry.step() == step).toList();
    }

    /** All structures stay scheduled; membership is consulted only by structure-start executors. */
    public static List<List<String>> defaultStructureSteps() {
        List<List<String>> result = new ArrayList<>(Mc263FeatureIndexReceipt.STEP_COUNT);
        for (int step = 0; step < Mc263FeatureIndexReceipt.STEP_COUNT; step++) {
            result.add(step(step).stream().map(Entry::key).toList());
        }
        return List.copyOf(result);
    }

    public static long biomeMask(Set<String> biomeKeys) {
        long mask = 0L;
        for (String key : biomeKeys) {
            int index = Mc263FeatureIndexReceipt.biomes().stream()
                    .map(Mc263FeatureIndexReceipt.BiomeFeatureData::biomeKey).toList()
                    .indexOf(key);
            if (index < 0) throw new IllegalArgumentException("unknown pinned biome: " + key);
            mask |= 1L << index;
        }
        return mask;
    }

    public static List<PlacementRecord> placements() { return PLACEMENTS; }

    private static PlacementRecord random(String key, int spacing, int separation, int salt,
            String spreadType, double frequency, String reduction, String locateOffset,
            String exclusionSet, int exclusionChunks, String weightedStructures) {
        return new PlacementRecord(key, "minecraft:random_spread", spacing, separation, salt,
                spreadType, frequency, reduction, locateOffset, exclusionSet, exclusionChunks,
                -1, -1, -1, "", weightedStructures);
    }

    private static void verify() {
        if (ENTRIES.size() != STRUCTURE_COUNT || PLACEMENTS.size() != STRUCTURE_SET_COUNT) {
            throw new ExceptionInInitializerError("26.3 structure receipt count mismatch");
        }
        Set<String> keys = new HashSet<>();
        for (int step = 0; step < COUNTS_BY_STEP.size(); step++) {
            List<Entry> entries = step(step);
            if (entries.size() != COUNTS_BY_STEP.get(step)) {
                throw new ExceptionInInitializerError("26.3 structure step count mismatch: " + step);
            }
            for (int index = 0; index < entries.size(); index++) {
                Entry entry = entries.get(index);
                if (entry.index() != index || !keys.add(entry.key())) {
                    throw new ExceptionInInitializerError("invalid structure registry receipt");
                }
                if (index > 0 && entries.get(index - 1).key().compareTo(entry.key()) >= 0) {
                    throw new ExceptionInInitializerError(
                            "structure registry step is not lexicographic: " + step);
                }
            }
        }
    }

    public record Entry(int step, int index, String key, long biomeMask) {
        public boolean selectedBy(long sourceBiomeMask) {
            return (biomeMask & sourceBiomeMask) != 0L;
        }
    }

    public record PlacementRecord(String key, String type, int spacing, int separation, int salt,
            String spreadType, double frequency, String frequencyReductionMethod,
            String locateOffset, String exclusionSet, int exclusionChunks, int distance,
            int spread, int count, String preferredBiomes, String weightedStructures) { }
}
