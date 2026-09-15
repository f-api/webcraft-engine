package com.gameexpert.engine.inventory;

import java.util.ArrayList;

import com.gameexpert.terrain.Blocks;

/**
 * Pure loom planner. Pattern-item patterns are reachable only through the item in the pattern slot;
 * [TRIAL-GAP] guster_banner_pattern and flow_banner_pattern are the two such items this repository
 * registers, so GLOBE · CREEPER · SKULL · FLOWER · MOJANG · PIGLIN stay unreachable.
 */
public final class LoomRules {
    public enum Pattern {
        BASE, SQUARE_BOTTOM_LEFT, SQUARE_BOTTOM_RIGHT, SQUARE_TOP_LEFT, SQUARE_TOP_RIGHT,
        STRIPE_BOTTOM, STRIPE_TOP, STRIPE_LEFT, STRIPE_RIGHT, STRIPE_CENTER, STRIPE_MIDDLE,
        STRIPE_DOWNRIGHT, STRIPE_DOWNLEFT, SMALL_STRIPES, CROSS, STRAIGHT_CROSS,
        TRIANGLE_BOTTOM, TRIANGLE_TOP, TRIANGLES_BOTTOM, TRIANGLES_TOP, DIAGONAL_LEFT,
        DIAGONAL_UP_RIGHT, DIAGONAL_UP_LEFT, DIAGONAL_RIGHT, CIRCLE, RHOMBUS,
        HALF_VERTICAL, HALF_HORIZONTAL, HALF_VERTICAL_RIGHT, HALF_HORIZONTAL_BOTTOM,
        BORDER, CURLY_BORDER, GRADIENT, GRADIENT_UP, BRICKS,
        GLOBE, CREEPER, SKULL, FLOWER, MOJANG, PIGLIN, FLOW, GUSTER;

        public String wireName() { return name().toLowerCase(java.util.Locale.ROOT); }
        public boolean requiresPatternItem() { return ordinal() >= GLOBE.ordinal(); }
    }

    private LoomRules() {}

    /** Plan with an empty pattern slot (only #no_item_required patterns are selectable). */
    public static ItemComponentData plan(short bannerItem, short dyeItem,
            Pattern pattern, ItemComponentData components) {
        return plan(bannerItem, dyeItem, PlayerInventory.EMPTY, pattern, components);
    }

    /**
     * Vanilla {@code LoomMenu}: an empty pattern slot offers the {@code #no_item_required} patterns,
     * a pattern item offers exactly its {@code provides_banner_patterns} (tag
     * {@code banner_pattern/pattern_item/<name>}); any other item offers none. The pattern item is
     * never consumed ({@code LoomMenu$6.onTake} removes one banner and one dye only).
     */
    public static ItemComponentData plan(short bannerItem, short dyeItem, short patternItem,
            Pattern pattern, ItemComponentData components) {
        int banner = Short.toUnsignedInt(bannerItem);
        if (!Blocks.isBanner(banner) || pattern == null || pattern == Pattern.BASE
                || !selectable(patternItem, pattern) || components == null
                || components.book() != null
                || components.bannerPatterns().size() >= ItemComponentData.MAX_BANNER_LAYERS) {
            return null;
        }
        int color = dyeColor(dyeItem);
        if (color < 0) return null;
        ArrayList<ItemComponentData.BannerLayer> layers =
                new ArrayList<>(components.bannerPatterns());
        layers.add(new ItemComponentData.BannerLayer(pattern, color));
        return components.withBannerPatterns(layers);
    }

    /** Whether {@code pattern} is on offer for the item in the pattern slot. */
    public static boolean selectable(short patternItem, Pattern pattern) {
        if (pattern == null || pattern == Pattern.BASE) return false;
        if (patternItem == PlayerInventory.EMPTY) return !pattern.requiresPatternItem();
        return providedPattern(patternItem) == pattern;
    }

    /**
     * [TRIAL-GAP] {@code provides_banner_patterns} of the registered pattern items
     * (data/minecraft/tags/banner_pattern/pattern_item/guster.json → minecraft:guster,
     * flow.json → minecraft:flow). Null for every other item.
     */
    public static Pattern providedPattern(short item) {
        if (item == PlayerInventory.GUSTER_BANNER_PATTERN) return Pattern.GUSTER;
        if (item == PlayerInventory.FLOW_BANNER_PATTERN) return Pattern.FLOW;
        return null;
    }

    public static int dyeColor(short dye) {
        short[] dyes = {
            PlayerInventory.WHITE_DYE, PlayerInventory.ORANGE_DYE, PlayerInventory.MAGENTA_DYE,
            PlayerInventory.LIGHT_BLUE_DYE, PlayerInventory.YELLOW_DYE, PlayerInventory.LIME_DYE,
            PlayerInventory.PINK_DYE, PlayerInventory.GRAY_DYE, PlayerInventory.LIGHT_GRAY_DYE,
            PlayerInventory.CYAN_DYE, PlayerInventory.PURPLE_DYE, PlayerInventory.BLUE_DYE,
            PlayerInventory.BROWN_DYE, PlayerInventory.GREEN_DYE, PlayerInventory.RED_DYE,
            PlayerInventory.BLACK_DYE,
        };
        for (int color = 0; color < dyes.length; color++) if (dyes[color] == dye) return color;
        return -1;
    }
}
