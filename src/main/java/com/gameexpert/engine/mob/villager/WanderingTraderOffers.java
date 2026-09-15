package com.gameexpert.engine.mob.villager;

import java.util.ArrayList;
import java.util.function.IntUnaryOperator;
import com.gameexpert.engine.mob.villager.VillagerTradeRules.Offer;

/** Snapshot-1..26.3 RC3 official trade tags, kept in tag order for durable pool indices. */
public final class WanderingTraderOffers {
    private WanderingTraderOffers() {}
    private static final Offer[] OFFERS = {
        new Offer((short) 345, 1, (short) 0, 0, (short) 452, 3, 12, 1, 50), // minecraft:wandering_trader/emerald_white_dye
        new Offer((short) 345, 1, (short) 0, 0, (short) 453, 3, 12, 1, 50), // minecraft:wandering_trader/emerald_orange_dye
        new Offer((short) 345, 1, (short) 0, 0, (short) 454, 3, 12, 1, 50), // minecraft:wandering_trader/emerald_magenta_dye
        new Offer((short) 345, 1, (short) 0, 0, (short) 455, 3, 12, 1, 50), // minecraft:wandering_trader/emerald_light_blue_dye
        new Offer((short) 345, 1, (short) 0, 0, (short) 456, 3, 12, 1, 50), // minecraft:wandering_trader/emerald_yellow_dye
        new Offer((short) 345, 1, (short) 0, 0, (short) 457, 3, 12, 1, 50), // minecraft:wandering_trader/emerald_lime_dye
        new Offer((short) 345, 1, (short) 0, 0, (short) 458, 3, 12, 1, 50), // minecraft:wandering_trader/emerald_pink_dye
        new Offer((short) 345, 1, (short) 0, 0, (short) 459, 3, 12, 1, 50), // minecraft:wandering_trader/emerald_gray_dye
        new Offer((short) 345, 1, (short) 0, 0, (short) 460, 3, 12, 1, 50), // minecraft:wandering_trader/emerald_light_gray_dye
        new Offer((short) 345, 1, (short) 0, 0, (short) 461, 3, 12, 1, 50), // minecraft:wandering_trader/emerald_cyan_dye
        new Offer((short) 345, 1, (short) 0, 0, (short) 462, 3, 12, 1, 50), // minecraft:wandering_trader/emerald_purple_dye
        new Offer((short) 345, 1, (short) 0, 0, (short) 463, 3, 12, 1, 50), // minecraft:wandering_trader/emerald_blue_dye
        new Offer((short) 345, 1, (short) 0, 0, (short) 464, 3, 12, 1, 50), // minecraft:wandering_trader/emerald_brown_dye
        new Offer((short) 345, 1, (short) 0, 0, (short) 465, 3, 12, 1, 50), // minecraft:wandering_trader/emerald_green_dye
        new Offer((short) 345, 1, (short) 0, 0, (short) 466, 3, 12, 1, 50), // minecraft:wandering_trader/emerald_red_dye
        new Offer((short) 345, 1, (short) 0, 0, (short) 467, 3, 12, 1, 50), // minecraft:wandering_trader/emerald_black_dye
        new Offer((short) 345, 3, (short) 0, 0, (short) 418, 1, 4, 1, 50), // minecraft:wandering_trader/emerald_fish_bucket
        new Offer((short) 345, 3, (short) 0, 0, (short) 468, 1, 4, 1, 50), // minecraft:wandering_trader/emerald_pufferfish_bucket
        new Offer((short) 345, 2, (short) 0, 0, (short) 63, 1, 5, 1, 50), // minecraft:wandering_trader/emerald_sea_pickle
        new Offer((short) 345, 4, (short) 0, 0, (short) 420, 1, 5, 1, 50), // minecraft:wandering_trader/emerald_slime_ball
        new Offer((short) 345, 2, (short) 0, 0, (short) 2126, 1, 5, 1, 50), // minecraft:wandering_trader/emerald_glowstone
        new Offer((short) 345, 5, (short) 0, 0, (short) 486, 1, 5, 1, 50), // minecraft:wandering_trader/emerald_nautilus_shell
        new Offer((short) 345, 1, (short) 0, 0, (short) 197, 1, 12, 1, 50), // minecraft:wandering_trader/emerald_fern
        new Offer((short) 345, 1, (short) 0, 0, (short) 18, 1, 8, 1, 50), // minecraft:wandering_trader/emerald_sugar_cane
        new Offer((short) 345, 1, (short) 0, 0, (short) 210, 1, 4, 1, 50), // minecraft:wandering_trader/emerald_pumpkin
        new Offer((short) 345, 3, (short) 0, 0, (short) 57, 1, 12, 1, 50), // minecraft:wandering_trader/emerald_kelp
        new Offer((short) 345, 3, (short) 0, 0, (short) 19, 1, 8, 1, 50), // minecraft:wandering_trader/emerald_cactus
        new Offer((short) 345, 1, (short) 0, 0, (short) 13, 1, 12, 1, 50), // minecraft:wandering_trader/emerald_dandelion
        new Offer((short) 345, 1, (short) 0, 0, (short) 12, 1, 12, 1, 50), // minecraft:wandering_trader/emerald_poppy
        new Offer((short) 345, 1, (short) 0, 0, (short) 2181, 1, 8, 1, 50), // minecraft:wandering_trader/emerald_blue_orchid
        new Offer((short) 345, 1, (short) 0, 0, (short) 2197, 1, 12, 1, 50), // minecraft:wandering_trader/emerald_allium
        new Offer((short) 345, 1, (short) 0, 0, (short) 2198, 1, 12, 1, 50), // minecraft:wandering_trader/emerald_azure_bluet
        new Offer((short) 345, 1, (short) 0, 0, (short) 2199, 1, 12, 1, 50), // minecraft:wandering_trader/emerald_red_tulip
        new Offer((short) 345, 1, (short) 0, 0, (short) 2200, 1, 12, 1, 50), // minecraft:wandering_trader/emerald_orange_tulip
        new Offer((short) 345, 1, (short) 0, 0, (short) 2201, 1, 12, 1, 50), // minecraft:wandering_trader/emerald_white_tulip
        new Offer((short) 345, 1, (short) 0, 0, (short) 2202, 1, 12, 1, 50), // minecraft:wandering_trader/emerald_pink_tulip
        new Offer((short) 345, 1, (short) 0, 0, (short) 2203, 1, 12, 1, 50), // minecraft:wandering_trader/emerald_oxeye_daisy
        new Offer((short) 345, 1, (short) 0, 0, (short) 2204, 1, 12, 1, 50), // minecraft:wandering_trader/emerald_cornflower
        new Offer((short) 345, 1, (short) 0, 0, (short) 2192, 1, 7, 1, 50), // minecraft:wandering_trader/emerald_lily_of_the_valley
        new Offer((short) 345, 1, (short) 0, 0, (short) 2515, 1, 7, 1, 50), // minecraft:wandering_trader/emerald_open_eyeblossom
        new Offer((short) 345, 1, (short) 0, 0, (short) 288, 1, 12, 1, 50), // minecraft:wandering_trader/emerald_wheat_seeds
        new Offer((short) 345, 1, (short) 0, 0, (short) 349, 1, 12, 1, 50), // minecraft:wandering_trader/emerald_beetroot_seeds
        new Offer((short) 345, 1, (short) 0, 0, (short) 343, 1, 12, 1, 50), // minecraft:wandering_trader/emerald_pumpkin_seeds
        new Offer((short) 345, 1, (short) 0, 0, (short) 2230, 1, 12, 1, 50), // minecraft:wandering_trader/emerald_melon_seeds
        new Offer((short) 345, 5, (short) 0, 0, (short) 389, 1, 8, 1, 50), // minecraft:wandering_trader/emerald_acacia_sapling
        new Offer((short) 345, 5, (short) 0, 0, (short) 134, 1, 8, 1, 50), // minecraft:wandering_trader/emerald_birch_sapling
        new Offer((short) 345, 5, (short) 0, 0, (short) 390, 1, 8, 1, 50), // minecraft:wandering_trader/emerald_dark_oak_sapling
        new Offer((short) 345, 5, (short) 0, 0, (short) 388, 1, 8, 1, 50), // minecraft:wandering_trader/emerald_jungle_sapling
        new Offer((short) 345, 5, (short) 0, 0, (short) 133, 1, 8, 1, 50), // minecraft:wandering_trader/emerald_oak_sapling
        new Offer((short) 345, 5, (short) 0, 0, (short) 387, 1, 8, 1, 50), // minecraft:wandering_trader/emerald_spruce_sapling
        new Offer((short) 345, 5, (short) 0, 0, (short) 391, 1, 8, 1, 50), // minecraft:wandering_trader/emerald_cherry_sapling
        new Offer((short) 345, 5, (short) 0, 0, (short) 1504, 1, 8, 1, 50), // minecraft:wandering_trader/emerald_pale_oak_sapling
        new Offer((short) 345, 5, (short) 0, 0, (short) 385, 1, 8, 1, 50), // minecraft:wandering_trader/emerald_mangrove_propagule
        new Offer((short) 345, 5, (short) 0, 0, (short) 1539, 1, 8, 1, 50), // minecraft:wandering_trader/emerald_poplar_sapling
        new Offer((short) 345, 3, (short) 0, 0, (short) 1881, 1, 8, 1, 50), // minecraft:wandering_trader/emerald_brain_coral_block
        new Offer((short) 345, 3, (short) 0, 0, (short) 1882, 1, 8, 1, 50), // minecraft:wandering_trader/emerald_bubble_coral_block
        new Offer((short) 345, 3, (short) 0, 0, (short) 1883, 1, 8, 1, 50), // minecraft:wandering_trader/emerald_fire_coral_block
        new Offer((short) 345, 3, (short) 0, 0, (short) 1884, 1, 8, 1, 50), // minecraft:wandering_trader/emerald_horn_coral_block
        new Offer((short) 345, 3, (short) 0, 0, (short) 1880, 1, 8, 1, 50), // minecraft:wandering_trader/emerald_tube_coral_block
        new Offer((short) 345, 1, (short) 0, 0, (short) 196, 3, 4, 1, 50), // minecraft:wandering_trader/emerald_vine
        new Offer((short) 345, 1, (short) 0, 0, (short) 1507, 3, 4, 1, 50), // minecraft:wandering_trader/emerald_pale_hanging_moss
        new Offer((short) 345, 1, (short) 0, 0, (short) 24, 3, 4, 1, 50), // minecraft:wandering_trader/emerald_brown_mushroom
        new Offer((short) 345, 1, (short) 0, 0, (short) 25, 3, 4, 1, 50), // minecraft:wandering_trader/emerald_red_mushroom
        new Offer((short) 345, 1, (short) 0, 0, (short) 39, 5, 2, 1, 50), // minecraft:wandering_trader/emerald_lily_pad
        new Offer((short) 345, 1, (short) 0, 0, (short) 204, 2, 5, 1, 50), // minecraft:wandering_trader/emerald_small_dripleaf
        new Offer((short) 345, 1, (short) 0, 0, (short) 4, 8, 8, 1, 50), // minecraft:wandering_trader/emerald_sand
        new Offer((short) 345, 1, (short) 0, 0, (short) 193, 4, 6, 1, 50), // minecraft:wandering_trader/emerald_red_sand
        new Offer((short) 345, 1, (short) 0, 0, (short) 380, 2, 5, 1, 50), // minecraft:wandering_trader/emerald_pointed_dripstone
        new Offer((short) 345, 1, (short) 0, 0, (short) 2159, 2, 5, 1, 50), // minecraft:wandering_trader/emerald_sulfur_spike
        new Offer((short) 345, 1, (short) 0, 0, (short) 194, 2, 5, 1, 50), // minecraft:wandering_trader/emerald_rooted_dirt
        new Offer((short) 345, 1, (short) 0, 0, (short) 66, 2, 5, 1, 50), // minecraft:wandering_trader/emerald_moss_block
        new Offer((short) 345, 1, (short) 0, 0, (short) 1505, 2, 5, 1, 50), // minecraft:wandering_trader/emerald_pale_moss_block
        new Offer((short) 345, 1, (short) 0, 0, (short) 1350, 1, 12, 1, 50), // minecraft:wandering_trader/emerald_wildflowers
        new Offer((short) 345, 1, (short) 0, 0, (short) 1355, 1, 12, 1, 50), // minecraft:wandering_trader/emerald_dry_tall_grass
        new Offer((short) 345, 3, (short) 0, 0, (short) 1352, 1, 12, 1, 50), // minecraft:wandering_trader/emerald_firefly_bush
        new Offer((short) 345, 2, (short) 0, 0, (short) 1733, 1, 12, 1, 50), // minecraft:wandering_trader/emerald_golden_dandelion
        new Offer((short) 345, 1, (short) 0, 0, (short) 484, 1, 5, 1, 50), // minecraft:wandering_trader/emerald_name_tag
        new Offer((short) 345, 1, (short) 0, 0, (short) 1576, 3, 12, 1, 50), // minecraft:wandering_trader/emerald_shelf_mushroom
        new Offer((short) 345, 1, (short) 0, 0, (short) 214, 1, 6, 1, 50), // minecraft:wandering_trader/emerald_packed_ice
        new Offer((short) 345, 6, (short) 0, 0, (short) 215, 1, 6, 1, 50), // minecraft:wandering_trader/emerald_blue_ice
        new Offer((short) 345, 1, (short) 0, 0, (short) 324, 4, 2, 1, 50), // minecraft:wandering_trader/emerald_gunpowder
        new Offer((short) 345, 3, (short) 0, 0, (short) 190, 3, 6, 1, 50), // minecraft:wandering_trader/emerald_podzol
        new Offer((short) 345, 1, (short) 0, 0, (short) 249, 8, 4, 1, 50), // minecraft:wandering_trader/emerald_acacia_log
        new Offer((short) 345, 1, (short) 0, 0, (short) 14, 8, 4, 1, 50), // minecraft:wandering_trader/emerald_birch_log
        new Offer((short) 345, 1, (short) 0, 0, (short) 251, 8, 4, 1, 50), // minecraft:wandering_trader/emerald_dark_oak_log
        new Offer((short) 345, 1, (short) 0, 0, (short) 247, 8, 4, 1, 50), // minecraft:wandering_trader/emerald_jungle_log
        new Offer((short) 345, 1, (short) 0, 0, (short) 5, 8, 4, 1, 50), // minecraft:wandering_trader/emerald_oak_log
        new Offer((short) 345, 1, (short) 0, 0, (short) 245, 8, 4, 1, 50), // minecraft:wandering_trader/emerald_spruce_log
        new Offer((short) 345, 1, (short) 0, 0, (short) 253, 8, 4, 1, 50), // minecraft:wandering_trader/emerald_cherry_log
        new Offer((short) 345, 1, (short) 0, 0, (short) 383, 8, 4, 1, 50), // minecraft:wandering_trader/emerald_mangrove_log
        new Offer((short) 345, 1, (short) 0, 0, (short) 1490, 8, 4, 1, 50), // minecraft:wandering_trader/emerald_pale_oak_log
        new Offer((short) 345, 1, (short) 0, 0, (short) 1530, 8, 4, 1, 50), // minecraft:wandering_trader/emerald_poplar_log
        new Offer((short) 345, 1, (short) 0, 0, (short) 274, 1, 1, 1, 200), // minecraft:wandering_trader/emerald_enchanted_iron_pickaxe
        new Offer((short) 345, 5, (short) 0, 0, (short) 2389, 1, 1, 1, 50), // minecraft:wandering_trader/emerald_long_invisibility_potion
        new Offer((short) 341, 1, (short) 0, 0, (short) 345, 1, 2, 1, 50), // minecraft:wandering_trader/water_bottle_emerald
        new Offer((short) 280, 1, (short) 0, 0, (short) 345, 2, 2, 1, 50), // minecraft:wandering_trader/water_bucket_emerald
        new Offer((short) 319, 1, (short) 0, 0, (short) 345, 2, 2, 1, 50), // minecraft:wandering_trader/milk_bucket_emerald
        new Offer((short) 802, 1, (short) 0, 0, (short) 345, 3, 2, 1, 50), // minecraft:wandering_trader/fermented_spider_eye_emerald
        new Offer((short) 408, 4, (short) 0, 0, (short) 345, 1, 2, 1, 50), // minecraft:wandering_trader/baked_potato_emerald
        new Offer((short) 209, 1, (short) 0, 0, (short) 345, 1, 2, 1, 50), // minecraft:wandering_trader/hay_block_emerald
    };
    public static Offer[] pool() { return OFFERS.clone(); }
    public static int[] pick(IntUnaryOperator nextInt) {
        var selected = new ArrayList<Integer>(9);
        int offset = 0;
        int[] sizes = {78, 16, 6}, counts = {5, 2, 2};
        for (int group = 0; group < sizes.length; group++) {
            for (int index : VillagerTradeRules.pickOfferIndices(sizes[group], counts[group], nextInt)) {
                selected.add(offset + index);
            }
            offset += sizes[group];
        }
        return selected.stream().mapToInt(Integer::intValue).toArray();
    }
}
