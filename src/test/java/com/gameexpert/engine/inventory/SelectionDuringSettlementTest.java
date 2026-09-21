package com.gameexpert.engine.inventory;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** A hotbar change sent while a pickup or container settlement holds the inventory is applied, not dropped. */
class SelectionDuringSettlementTest {
    @Test
    void selectionMadeDuringASettlementAppliesWhenItEnds() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.select(1);
        PlayerInventory.CompletePersistenceSnapshot lease = inventory.acquireSettlementLease();
        assertNotNull(lease);

        inventory.select(4);
        assertEquals(1, inventory.selectedSlot(), "the settlement's snapshot keeps its revision while leased");

        assertTrue(inventory.releaseSettlementLease(lease));
        assertEquals(4, inventory.selectedSlot());
    }

    @Test
    void latestSelectionDuringASettlementWins() {
        PlayerInventory inventory = new PlayerInventory();
        PlayerInventory.CompletePersistenceSnapshot lease = inventory.acquireSettlementLease();
        inventory.select(2);
        inventory.select(7);
        assertTrue(inventory.releaseSettlementLease(lease));
        assertEquals(7, inventory.selectedSlot());
    }

    @Test
    void selectionOutsideASettlementIsImmediate() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.select(5);
        assertEquals(5, inventory.selectedSlot());
        inventory.select(99);
        assertEquals(5, inventory.selectedSlot());
    }
}
