package com.gameexpert.engine.persistence.finalcarrier.loot;

/** Item constraints for MCF263LOOTR2. RC3 map registry values are fixed at 64. */
final class CanonicalLootResultV2Items {
    private CanonicalLootResultV2Items() {}

    static int maximumStackSize(String itemKey) {
        return isMapItem(itemKey) ? 64 : CanonicalLootResultV1Items.maximumStackSize(itemKey);
    }

    static boolean isMapItem(String itemKey) {
        return switch (itemKey) {
            case "minecraft:map", "minecraft:filled_map", "minecraft:abandoned_camp_map",
                    "minecraft:buried_ancient_city_map", "minecraft:buried_mineshaft_map",
                    "minecraft:buried_treasure_map", "minecraft:buried_trial_chambers_map",
                    "minecraft:desert_pyramid_map", "minecraft:desert_village_map",
                    "minecraft:jungle_pyramid_map", "minecraft:ocean_monument_map",
                    "minecraft:plains_village_map", "minecraft:savanna_village_map",
                    "minecraft:snowy_village_map", "minecraft:swamp_hut_map",
                    "minecraft:taiga_village_map", "minecraft:warm_ocean_ruins_map",
                    "minecraft:woodland_mansion_map",
                    // Retained declaration aliases still map to the same stable gameplay IDs.
                    "minecraft:abandoned_campsite_map", "minecraft:ancient_city_map",
                    "minecraft:trial_explorer_map", "minecraft:mineshaft_map",
                    "minecraft:jungle_explorer_map", "minecraft:woodland_explorer_map",
                    "minecraft:ocean_explorer_map" -> true;
            default -> false;
        };
    }
}
