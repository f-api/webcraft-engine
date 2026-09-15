package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;

/**
 * [PITCHER] Vanilla 26.3 {@code PitcherCropBlock} (a {@code DoublePlantBlock}) and the two-tall
 * {@code pitcher_plant} flower. Pure rules shared by every authority path; the standalone twin is
 * {@code StandalonePitcherRules.ts} with the same constants and decisions.
 *
 * <ul>
 *   <li>State: bits 0..2 {@code AGE} (0..4), bit 3 {@link #UPPER} ({@code DoubleBlockHalf.UPPER}). The upper
 *       half is the same block ID. {@code PITCHER_PLANT} uses only {@link #UPPER}.</li>
 *   <li>Growth ({@code randomTick}): the lower half rolls {@code nextInt((int)(25 / growthSpeed) + 1) == 0}
 *       first, then {@code grow(+1)} succeeds only if {@code canGrow}: not max age, raw light ≥ 8
 *       ({@code CropBlock.hasSufficientLight}), the cell above inside the build height and, from age 3
 *       ({@code isDouble}), the cell above air or pitcher crop. From age 3 the upper half is written with
 *       the new age. Bone meal is the same {@code grow(+1)} from either half.</li>
 *   <li>Loot ({@code blocks/pitcher_crop.json}, {@code blocks/pitcher_plant.json}): only the lower half has
 *       loot — pitcher pod at age 0..3, pitcher plant at age 4; the plant drops itself. Breaking either half
 *       removes both and drops the lower half's loot once ({@code DoublePlantBlock.playerWillDestroy} plus
 *       the neighbour {@code updateShape} → {@code destroyBlock}).</li>
 *   <li>Shapes (outline and collision per state) live in {@link BlockModelShapes}, pinned by the oracle rows
 *       of block-shape-golden.json.</li>
 *   <li>Save compatibility: crops saved at age 3..4 before the upper half existed grow it back on their next
 *       random tick when the cell above is air.</li>
 * </ul>
 */
public final class PitcherRules {
    public static final int AGE_MASK = 0x07;
    public static final int UPPER = 0x08;
    public static final int MAX_AGE = 4;
    /** {@code DOUBLE_PLANT_AGE_INTERSECTION}: from this age the crop is two blocks tall. */
    public static final int DOUBLE_AGE = 3;
    /** {@code CropBlock.hasSufficientLight}: raw brightness at least 8. */
    public static final int MIN_GROW_LIGHT = 8;

    private PitcherRules() {}

    public static boolean isPitcher(int block) {
        return block == Blocks.PITCHER_CROP || block == Blocks.PITCHER_PLANT;
    }

    public static int age(int state) { return state & AGE_MASK; }

    public static boolean isUpper(int state) { return (state & UPPER) != 0; }

    public static boolean isDouble(int age) { return age >= DOUBLE_AGE; }

    /** {@code PitcherCropBlock.canGrowInto}: the cell above is air or already pitcher crop. */
    public static boolean canGrowInto(int aboveBlock) {
        return aboveBlock == Blocks.AIR || aboveBlock == Blocks.PITCHER_CROP;
    }

    /** {@code PitcherCropBlock.canGrow} for the lower half at {@code (x, y, z)}. */
    public static boolean canGrow(int lowerState, int newAge, int light, int y, int aboveBlock) {
        return age(lowerState) < MAX_AGE
                && light >= MIN_GROW_LIGHT
                && y + 1 <= Blocks.MAX_Y
                && (!isDouble(newAge) || canGrowInto(aboveBlock));
    }

    /**
     * The lower half's loot item: pitcher pod at age 0..3, pitcher plant at age 4
     * ({@code blocks/pitcher_crop.json}).
     */
    public static int cropLootItem(int lowerState) {
        return age(lowerState) >= MAX_AGE ? Blocks.PITCHER_PLANT : Blocks.PITCHER_POD;
    }

    /**
     * The block whose ordinary drop stands for this cell's loot, or {@link Blocks#AIR} for an upper half
     * (whose loot tables have no entry): a mature lower crop drops the pitcher plant, a younger one the pod
     * (the crop's own drop), and the plant's lower half drops itself.
     */
    public static int lootBlock(int block, int state) {
        if (!isPitcher(block) || isUpper(state)) return isPitcher(block) ? Blocks.AIR : block;
        if (block == Blocks.PITCHER_CROP && age(state) >= MAX_AGE) return Blocks.PITCHER_PLANT;
        return block;
    }
}
