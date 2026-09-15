package com.gameexpert.engine.hopper;

import com.gameexpert.engine.inventory.PlayerInventory;
import java.util.Objects;

/**
 * Lossless value of one container slot as the hopper transfer sees it. {@code ItemStack} carries
 * every component the repository persists, so two stacks merge only when all of them match
 * ({@code ItemStack.isSameItemSameComponents}).
 */
public record HopperStack(short itemType, int count, int durability, long enchantments,
        int mapId, int shulkerId, String bucketMobData, String itemComponentData) {

    public static final HopperStack EMPTY =
            new HopperStack(PlayerInventory.EMPTY, 0, 0, 0L, 0, 0, null, null);

    public static HopperStack of(short itemType, int count) {
        if (itemType == PlayerInventory.EMPTY || count <= 0) return EMPTY;
        return new HopperStack(itemType, count, PlayerInventory.isDurable(itemType)
                ? PlayerInventory.initialDurability(itemType) : 0, 0L, 0, 0, null, null);
    }

    public boolean isEmpty() {
        return itemType == PlayerInventory.EMPTY || count <= 0;
    }

    /**
     * {@code ItemStack.getMaxStackSize}. The empty stack is {@code Items.AIR}, whose default
     * property is 64; it is only ever compared against a zero count.
     */
    public int maxStackSize() {
        return isEmpty() ? 64 : PlayerInventory.stackMax(itemType);
    }

    public HopperStack withCount(int next) {
        if (next <= 0) return EMPTY;
        return new HopperStack(itemType, next, durability, enchantments, mapId, shulkerId,
                bucketMobData, itemComponentData);
    }

    /** {@code ItemStack.isSameItemSameComponents}: every persisted identity column is equal. */
    public static boolean isSameItemSameComponents(HopperStack a, HopperStack b) {
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() && b.isEmpty();
        return a.itemType == b.itemType && a.durability == b.durability
                && a.enchantments == b.enchantments && a.mapId == b.mapId
                && a.shulkerId == b.shulkerId
                && Objects.equals(a.bucketMobData, b.bucketMobData)
                && Objects.equals(a.itemComponentData, b.itemComponentData);
    }

    /** True when the stack carries no identity beyond type and count. */
    public boolean componentless() {
        return durability == 0 && enchantments == 0L && mapId == 0 && shulkerId == 0
                && bucketMobData == null && itemComponentData == null;
    }
}
