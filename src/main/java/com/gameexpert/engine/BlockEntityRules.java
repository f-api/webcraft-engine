package com.gameexpert.engine;

import com.gameexpert.engine.hopper.HopperRules;
import com.gameexpert.terrain.Blocks;

/**
 * [BLOCK-ENTITY] Which coordinate rows belong to which block, for the pinned
 * {@code LevelChunk.setBlockState} rule: when the block at a cell changes to a different block the
 * old block entity is removed, unless {@code shouldChangedStateKeepBlockEntity} keeps it
 * ({@code CopperChestBlock}: the new state is in {@code #minecraft:copper_chests}). The
 * repository's lit furnace twins are the same vanilla block with {@code LIT} and share a family.
 * The standalone twin is {@code StandaloneBlockEntityRules.ts}.
 */
public final class BlockEntityRules {

    /** Not a block that owns a coordinate row. */
    public static final int NONE = -1;

    /** {@code DispenserBlockEntity.CONTAINER_SIZE} (dispenser and dropper); the crafter's 3x3. */
    public static final int DISPENSER_SLOTS = 9;

    private BlockEntityRules() {}

    /** The block-entity identity a cell keeps across a block change; {@link #NONE} if none. */
    public static int family(int block) {
        if (block >= Blocks.COPPER_CHEST && block <= Blocks.WAXED_OXIDIZED_COPPER_CHEST) {
            return Blocks.COPPER_CHEST;
        }
        FurnaceVariant variant = FurnaceVariant.of(block);
        if (variant != null) return variant.blockId(false);
        if (chestStorageSlots(block) > 0 || block == Blocks.BREWING_STAND
                || block == Blocks.CAMPFIRE || block == Blocks.LECTERN || block == Blocks.JUKEBOX) {
            return block;
        }
        return NONE;
    }

    /** Capacity of the {@code ChestStorage} row a block owns (0: none). */
    public static int chestStorageSlots(int block) {
        if (block != Blocks.ENDER_CHEST && Blocks.isChestShaped(block)) return ChestInventory.SLOTS;
        if (block == Blocks.BARREL || Blocks.isShulkerBox(block)) return ChestInventory.SLOTS;
        if (Blocks.isDecoratedPot(block)) return 1;
        if (Blocks.isShelf(block)) return com.gameexpert.engine.shelf.ShelfRules.SLOTS;
        if (block == Blocks.HOPPER) return HopperRules.CONTAINER_SIZE;
        if (block == Blocks.DISPENSER || block == Blocks.DROPPER) return DISPENSER_SLOTS;
        // CrafterBlockEntity: a nine-slot CraftingContainer (its disabled-slot mask is separate).
        if (block == Blocks.CRAFTER) return DISPENSER_SLOTS;
        return 0;
    }

    /**
     * Whether {@code slots} is the capacity of some {@link #chestStorageSlots} row. The
     * persistence entity accepts exactly this domain so no resident row can be unpersistable.
     */
    public static boolean isChestStorageCapacity(int slots) {
        return slots == ChestInventory.SLOTS || slots == 1
                || slots == com.gameexpert.engine.shelf.ShelfRules.SLOTS
                || slots == HopperRules.CONTAINER_SIZE || slots == DISPENSER_SLOTS;
    }

    /** Blocks that may carry a generated LOOT table ({@code RandomizableContainer}). */
    public static boolean isLootContainer(int block) {
        return block != Blocks.ENDER_CHEST && Blocks.isChestShaped(block)
                || block == Blocks.BARREL || block == Blocks.DISPENSER
                || Blocks.isDecoratedPot(block);
    }
}
