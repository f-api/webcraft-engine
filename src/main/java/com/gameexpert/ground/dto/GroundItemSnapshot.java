package com.gameexpert.ground.dto;

import com.gameexpert.engine.inventory.PlayerInventory;
import java.util.Objects;

/** Exact durable state of one server-authoritative dropped item. */
public record GroundItemSnapshot(
        long entityId,
        short itemType,
        int count,
        int durability,
        long enchantments,
        int mapId,
        int shulkerId,
        String bucketMobData,
        String itemComponentData,
        double x,
        double y,
        double z,
        double velocityX,
        double velocityY,
        double velocityZ,
        boolean playerThrown,
        int age,
        int pickupDelay,
        long excludedAllayId) {
    private static final int MAX_AGE = 6000;
    private static final int MAX_PICKUP_DELAY = 20;

    public GroundItemSnapshot {
        if (entityId <= 0 || entityId > GroundMutationCommand.MAX_GROUND_ENTITY_ID) {
            throw new IllegalArgumentException("ground item entity id is invalid");
        }
        // Ground entities carry the exact same stack identity as inventories and containers.
        // Constructing the canonical value object here keeps durability, enchantment, map,
        // shulker, bucket and item-component validation in one authority.
        PlayerInventory.StackSnapshot stack = new PlayerInventory.StackSnapshot(
                itemType, count, durability, enchantments,
                mapId, shulkerId, bucketMobData, itemComponentData);
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("ground item stack cannot be empty");
        }
        if (age < 0 || age > MAX_AGE) {
            throw new IllegalArgumentException("ground item age is out of range");
        }
        if (pickupDelay < 0 || pickupDelay > MAX_PICKUP_DELAY) {
            throw new IllegalArgumentException("ground item pickup delay is out of range");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(velocityX) || !Double.isFinite(velocityY)
                || !Double.isFinite(velocityZ)) {
            throw new IllegalArgumentException("ground item position or velocity is not finite");
        }
        if (excludedAllayId < 0 || excludedAllayId == Long.MAX_VALUE) {
            throw new IllegalArgumentException("excluded allay id is invalid");
        }
    }

    /** Exact entity, stack and pickup-owner identity; motion and timers advance between saves. */
    public boolean matchesPickupIdentity(GroundItemSnapshot other) {
        return other != null && entityId == other.entityId
                && itemType == other.itemType && durability == other.durability
                && enchantments == other.enchantments && mapId == other.mapId
                && shulkerId == other.shulkerId
                && Objects.equals(bucketMobData, other.bucketMobData)
                && Objects.equals(itemComponentData, other.itemComponentData)
                && excludedAllayId == other.excludedAllayId;
    }
}
