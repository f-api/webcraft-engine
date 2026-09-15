package com.gameexpert.state.service.inventory;

import com.gameexpert.engine.inventory.ArmorSlot;
import com.gameexpert.engine.inventory.PlayerInventory;
import java.util.List;

/**
 * A detached, exact player inventory baseline read while the durable player row is locked.
 * Slot lists are dense and index-addressable, including canonical empty stack values.
 */
public record PlayerGroundSettlementBaseline(
        long inventoryPersistenceRevision,
        List<PlayerInventory.StackSnapshot> mainSlots,
        List<PlayerInventory.StackSnapshot> equippedSlots,
        PlayerInventory.StackSnapshot offhand,
        int xpTotal) {

    public PlayerGroundSettlementBaseline {
        if (inventoryPersistenceRevision < 0 || xpTotal < 0
                || mainSlots == null || mainSlots.size() != PlayerInventory.SLOTS
                || equippedSlots == null || equippedSlots.size() != ArmorSlot.values().length
                || offhand == null) {
            throw new IllegalArgumentException("invalid player ground settlement baseline");
        }
        mainSlots = List.copyOf(mainSlots);
        equippedSlots = List.copyOf(equippedSlots);
    }
}
