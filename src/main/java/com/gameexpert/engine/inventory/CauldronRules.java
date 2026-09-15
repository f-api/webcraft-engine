package com.gameexpert.engine.inventory;

import java.util.ArrayList;
import com.gameexpert.terrain.Blocks;

/** Pure cauldron state and held-item transition rules. */
public final class CauldronRules {
    public static final int LEVEL_MASK = 0b11;
    public static final int KIND_SHIFT = 2;
    public static final int KIND_MASK = 0b11 << KIND_SHIFT;
    public static final int EMPTY = 0;
    public static final int WATER = 1;
    public static final int LAVA = 2;
    public static final int POWDER_SNOW = 3;
    public static final int FULL_LEVEL = 3;

    private CauldronRules() {}

    public static final class Plan {
        private final int state;
        private final short replacement;
        private Plan(int state, short replacement) {
            this.state = state;
            this.replacement = replacement;
        }
        public int state() { return state; }
        public short replacement() { return replacement; }
    }

    public static int state(int kind, int level) {
        if (kind < EMPTY || kind > POWDER_SNOW || level < 0 || level > FULL_LEVEL
                || kind == EMPTY && level != 0 || kind != EMPTY && level == 0) {
            throw new IllegalArgumentException("invalid cauldron state");
        }
        return kind << KIND_SHIFT | level;
    }

    public static int kind(int state) { return (state & KIND_MASK) >>> KIND_SHIFT; }
    public static int level(int state) { return state & LEVEL_MASK; }

    public static Plan use(int currentState, short held) {
        int kind = kind(currentState);
        int level = level(currentState);
        if (held == PlayerInventory.WATER_BUCKET && (kind == EMPTY || kind == WATER)) {
            return new Plan(state(WATER, FULL_LEVEL), PlayerInventory.BUCKET);
        }
        if (held == PlayerInventory.LAVA_BUCKET && kind == EMPTY) {
            return new Plan(state(LAVA, FULL_LEVEL), PlayerInventory.BUCKET);
        }
        if (held == PlayerInventory.POWDER_SNOW_BUCKET && kind == EMPTY) {
            return new Plan(state(POWDER_SNOW, FULL_LEVEL), PlayerInventory.BUCKET);
        }
        if (held == PlayerInventory.BUCKET && level == FULL_LEVEL && kind != EMPTY) {
            short filled = kind == WATER ? PlayerInventory.WATER_BUCKET
                    : kind == LAVA ? PlayerInventory.LAVA_BUCKET
                    : PlayerInventory.POWDER_SNOW_BUCKET;
            return new Plan(state(EMPTY, 0), filled);
        }
        if (held == PlayerInventory.WATER_BOTTLE && (kind == EMPTY || kind == WATER)
                && level < FULL_LEVEL) {
            return new Plan(state(WATER, level + 1), PlayerInventory.GLASS_BOTTLE);
        }
        if (held == PlayerInventory.GLASS_BOTTLE && kind == WATER && level > 0) {
            int next = level - 1;
            return new Plan(next == 0 ? state(EMPTY, 0) : state(WATER, next),
                    PlayerInventory.WATER_BOTTLE);
        }
        return null;
    }

    public static PlayerInventory.StackSnapshot wash(
            int currentState, PlayerInventory.StackSnapshot held) {
        if (kind(currentState) != WATER || level(currentState) == 0
                || held == null || held.isEmpty()) return null;
        ItemComponentData components = held.itemComponents();
        short nextType = held.itemType();
        ItemComponentData nextComponents = components;
        if (Blocks.isBanner(Short.toUnsignedInt(held.itemType()))
                && !components.bannerPatterns().isEmpty()) {
            ArrayList<ItemComponentData.BannerLayer> layers =
                    new ArrayList<>(components.bannerPatterns());
            layers.removeLast();
            nextComponents = components.withBannerPatterns(layers);
        } else if (components.leatherColor() != null) {
            nextComponents = components.withLeatherColor(null);
        } else if (Short.toUnsignedInt(held.itemType()) >= Blocks.WHITE_SHULKER_BOX
                && Short.toUnsignedInt(held.itemType()) <= Blocks.BLACK_SHULKER_BOX) {
            nextType = (short) Blocks.SHULKER_BOX;
        } else return null;
        return new PlayerInventory.StackSnapshot(nextType, 1, held.durability(),
                held.enchantments(), held.mapId(), held.shulkerId(), held.bucketMobData(),
                ItemComponentCodec.encode(nextType, nextComponents));
    }

    public static int lowerOneLevel(int currentState) {
        int next = level(currentState) - 1;
        return next == 0 ? state(EMPTY, 0) : state(kind(currentState), next);
    }
}
