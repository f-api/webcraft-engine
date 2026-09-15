package com.gameexpert.engine.mob;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;

/** Available-item projection of the vanilla piglin barter table. */
public final class PiglinBarterRules {
    public static final int TOTAL_WEIGHT = 208;

    private PiglinBarterRules() {}

    public static Drop select(int selectionRoll, int countRoll) {
        int roll = Math.floorMod(selectionRoll, TOTAL_WEIGHT);
        int count = Math.floorMod(countRoll, 9);
        if (roll < 8) return new Drop(PlayerInventory.IRON_BOOTS, 1);
        if ((roll -= 8) < 10) return new Drop(PlayerInventory.WATER_BOTTLE, 1);
        if ((roll -= 10) < 10) return new Drop(PlayerInventory.ENDER_PEARL, 2 + count % 3);
        if ((roll -= 10) < 20) return new Drop(PlayerInventory.STRING, 3 + count % 7);
        if ((roll -= 20) < 40) return new Drop((short) Blocks.OBSIDIAN, 1);
        if ((roll -= 40) < 40) return new Drop(PlayerInventory.LEATHER, 2 + count % 3);
        if ((roll -= 40) < 40) return new Drop(PlayerInventory.ARROW, 6 + count % 7);
        return new Drop((short) Blocks.GRAVEL, 8 + count);
    }

    public static final class Drop {
        private final short itemType;
        private final int count;

        private Drop(short itemType, int count) {
            this.itemType = itemType;
            this.count = count;
        }

        public short itemType() { return itemType; }
        public int count() { return count; }
    }
}
