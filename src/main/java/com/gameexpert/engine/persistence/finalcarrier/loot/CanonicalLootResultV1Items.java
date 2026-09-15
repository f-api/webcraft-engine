package com.gameexpert.engine.persistence.finalcarrier.loot;

/** Fixed item constraints of persisted MCF263LOOTR1. These bounds validate retained result
 * bytes independently of the currently selected terrain producer or gameplay stack limits.
 * Changing these constraints requires an explicit stored-result schema migration. */
final class CanonicalLootResultV1Items {
    private CanonicalLootResultV1Items() {}

    static int maximumStackSize(String itemKey) {
        return switch (itemKey) {
            case "minecraft:enchanted_book", "minecraft:potion", "minecraft:iron_pickaxe",
                    "minecraft:stone_axe", "minecraft:iron_sword", "minecraft:iron_spear",
                    "minecraft:fishing_rod", "minecraft:flint_and_steel", "minecraft:bundle",
                    "minecraft:saddle", "minecraft:bow", "minecraft:copper_axe",
                    "minecraft:copper_boots", "minecraft:copper_chestplate",
                    "minecraft:copper_leggings", "minecraft:copper_spear",
                    "minecraft:copper_sword", "minecraft:spyglass", "minecraft:shears",
                    "minecraft:abandoned_campsite_map", "minecraft:ancient_city_map",
                    "minecraft:trial_explorer_map", "minecraft:mineshaft_map",
                    "minecraft:desert_pyramid_map", "minecraft:jungle_explorer_map",
                    "minecraft:warm_ocean_ruins_map", "minecraft:woodland_explorer_map",
                    "minecraft:wooden_axe", "minecraft:iron_axe",
                    "minecraft:leather_chestplate", "minecraft:iron_chestplate", "minecraft:iron_helmet",
                    "minecraft:iron_leggings", "minecraft:iron_boots", "minecraft:copper_horse_armor",
                    "minecraft:iron_horse_armor", "minecraft:golden_horse_armor",
                    "minecraft:diamond_horse_armor", "minecraft:copper_nautilus_armor",
                    "minecraft:iron_nautilus_armor", "minecraft:golden_nautilus_armor",
                    "minecraft:diamond_nautilus_armor", "minecraft:music_disc_bounce",
                    "minecraft:music_disc_otherside", "minecraft:music_disc_13",
                    "minecraft:music_disc_cat", "minecraft:music_disc_precipice",
                    "minecraft:music_disc_creator_music_box", "minecraft:buried_treasure_map",
                    "minecraft:suspicious_stew", "minecraft:goat_horn", "minecraft:shield",
                    "minecraft:crossbow", "minecraft:trident", "minecraft:diamond_axe",
                    "minecraft:diamond_pickaxe", "minecraft:golden_axe",
                    "minecraft:golden_pickaxe", "minecraft:stone_pickaxe",
                    "minecraft:golden_sword", "minecraft:golden_hoe",
                    "minecraft:golden_shovel", "minecraft:golden_boots",
                    "minecraft:golden_chestplate", "minecraft:golden_helmet",
                    "minecraft:golden_leggings", "minecraft:leather_helmet",
                    "minecraft:leather_leggings", "minecraft:leather_boots",
                    "minecraft:splash_potion", "minecraft:lingering_potion",
                    "minecraft:water_bucket", "minecraft:milk_bucket",
                    "minecraft:stone_spear", "minecraft:diamond_chestplate" -> 1;
            case "minecraft:bucket", "minecraft:ender_pearl", "minecraft:straw_bed",
                    "minecraft:snowball", "minecraft:egg", "minecraft:honey_bottle",
                    "minecraft:bamboo_hanging_sign" -> 16;
            default -> 64;
        };
    }

    static int maximumDamage(String itemKey) {
        return switch (itemKey) {
            case "minecraft:stone_axe", "minecraft:stone_pickaxe" -> 131;
            case "minecraft:iron_axe" -> 250;
            case "minecraft:diamond_axe", "minecraft:diamond_pickaxe" -> 1561;
            case "minecraft:golden_axe", "minecraft:golden_pickaxe" -> 32;
            case "minecraft:shield" -> 336;
            default -> -1;
        };
    }
}
