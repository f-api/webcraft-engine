package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;

/** 26.3 BushBlock / supports_vegetation; spread never overwrites a non-air neighbor. */
public final class RedShrubRules {
    private RedShrubRules() {}
    private static final int[][] DIRECTIONS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    public static boolean isSoil(int block) {
        return block == Blocks.DIRT || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT
                || block == Blocks.MUD || block == Blocks.MUDDY_MANGROVE_ROOTS
                || block == Blocks.MOSS_BLOCK || block == Blocks.PALE_MOSS_BLOCK
                || block == Blocks.GRASS || block == Blocks.PODZOL || block == Blocks.MYCELIUM
                || block == Blocks.FARMLAND;
    }
    static int[] spreadPosition(SupportRules.BlockLookup lookup, int x, int y, int z,
            java.util.function.IntUnaryOperator random) {
        int[] order = {0, 1, 2, 3};
        if (random != null) for (int size = 4; size > 1; size--) {
            int selected = random.applyAsInt(size);
            int swap = order[size - 1]; order[size - 1] = order[selected]; order[selected] = swap;
        }
        for (int index : order) {
            int tx = x + DIRECTIONS[index][0], tz = z + DIRECTIONS[index][1];
            if (lookup.getBlock(tx, y, tz) == Blocks.AIR && isSoil(lookup.getBlock(tx, y - 1, tz))) {
                return new int[] {tx, y, tz};
            }
        }
        return null;
    }
}
