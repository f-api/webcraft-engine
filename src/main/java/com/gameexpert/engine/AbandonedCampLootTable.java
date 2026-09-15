package com.gameexpert.engine;

import java.util.List;
import java.util.Set;

/** Exact data-only contract for the 26.3-snapshot-7 abandoned-camp common chest. */
final class AbandonedCampLootTable {
    record ExplorerMapFunction(String excludedBiome, String destination, String decoration,
            String nameTranslation, boolean discardUnlessMapId) {}

    record Entry(String itemId, int weight, int minimum, int maximum,
            ExplorerMapFunction explorerMap) {
        Entry(String itemId, int minimum, int maximum) {
            this(itemId, 1, minimum, maximum, null);
        }
    }

    record Pool(int minimumRolls, int maximumRolls, List<Entry> entries) {
        Pool {
            entries = List.copyOf(entries);
        }
    }

    private static final String CAMP_MAP = "abandoned_campsite_map";
    private static final String CAMP_DECORATION = "minecraft:abandoned_camp";

    private static final List<Pool> COMMON_CHEST = List.of(
            new Pool(4, 6, List.of(
                    entry("arrow", 4, 4),
                    entry("map", 1, 1),
                    entry("bone", 2, 4),
                    entry("cobweb", 1, 1),
                    entry("compass", 1, 1),
                    entry("map", 1, 2),
                    entry("gunpowder", 2, 4),
                    entry("fishing_rod", 1, 1),
                    entry("flint_and_steel", 1, 1),
                    entry("glass_bottle", 1, 4),
                    entry("lead", 1, 3),
                    entry("leather", 1, 4),
                    entry("bundle", 1, 1),
                    entry("rabbit_hide", 1, 4),
                    entry("saddle", 1, 1),
                    entry("white_candle", 1, 3))),
            new Pool(2, 2, List.of(
                    entry("bow", 1, 1),
                    entry("bucket", 1, 1),
                    entry("copper_axe", 1, 1),
                    entry("copper_boots", 1, 1),
                    entry("copper_chestplate", 1, 1),
                    entry("copper_leggings", 1, 1),
                    entry("copper_spear", 1, 1),
                    entry("copper_sword", 1, 1),
                    entry("spyglass", 1, 1),
                    entry("shears", 1, 1))),
            new Pool(1, 1, List.of(
                    map("bamboo_jungle", "#minecraft:on_abandoned_camp_bamboo_jungle"),
                    map("cherry_grove", "#minecraft:on_abandoned_camp_cherry_grove"),
                    map("birch_forest", "#minecraft:on_abandoned_camp_birch_forest"),
                    map("dappled_forest", "#minecraft:on_abandoned_camp_dappled_forest"),
                    map("flower_forest", "#minecraft:on_abandoned_camp_flower_forest"),
                    map("pale_garden", "#minecraft:on_abandoned_camp_pale_garden"),
                    map("swamp", "#minecraft:on_abandoned_camp_swamp"),
                    map("windswept_forest", "#minecraft:on_abandoned_camp_windswept"))));

    private static final Set<String> UNRESOLVED_ITEM_IDS = Set.of(
            CAMP_MAP, "bundle", "spyglass", "white_candle");

    private AbandonedCampLootTable() {}

    static List<Pool> commonChest() {
        return COMMON_CHEST;
    }

    static Set<String> unresolvedItemIds() {
        return UNRESOLVED_ITEM_IDS;
    }

    static boolean isFullyRepresentable() {
        return UNRESOLVED_ITEM_IDS.isEmpty();
    }

    private static Entry entry(String itemId, int minimum, int maximum) {
        return new Entry(itemId, minimum, maximum);
    }

    private static Entry map(String biome, String destination) {
        return new Entry(CAMP_MAP, 1, 1, 1,
                new ExplorerMapFunction("minecraft:" + biome, destination, CAMP_DECORATION,
                        "filled_map." + biome + "_abandoned_camp", true));
    }
}
