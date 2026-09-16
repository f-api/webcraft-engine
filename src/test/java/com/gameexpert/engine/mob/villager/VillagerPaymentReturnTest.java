package com.gameexpert.engine.mob.villager;

import com.gameexpert.engine.inventory.ContainerAccess;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VillagerPaymentReturnTest {
    @Test
    void partialReturnKeepsTotalAndRetryDoesNotDuplicate() {
        VillagerTradeSessions sessions = new VillagerTradeSessions();
        assertNotNull(sessions.open("Audit", 1L, VillagerTradeRules.Profession.FARMER, bound -> 0));
        PlayerInventory inventory = new PlayerInventory();
        inventory.dropFromSlot(0, 1);
        assertEquals(10, inventory.addItem(PlayerInventory.EMERALD, 10));
        ContainerAccess payment = sessions.paymentAccess("Audit");
        assertTrue(inventory.clickContainer(payment, PlayerInventory.ContainerArea.INVENTORY, 0, PlayerInventory.CraftButton.LEFT, true));
        assertEquals(10, payment.count(0));
        // Pickups while the merchant menu is open fill the vacated inventory space.
        assertEquals(63, inventory.addItem(PlayerInventory.EMERALD, 63));
        assertEquals((PlayerInventory.SLOTS - 1) * 64,
                inventory.addItem((short) Blocks.STONE, (PlayerInventory.SLOTS - 1) * 64));
        assertEquals(73, inventory.count(0) + payment.count(0));
        assertFalse(sessions.foldPayments("Audit", inventory));
        assertEquals(64, inventory.count(0));
        assertEquals(9, payment.count(0));
        assertEquals(73, inventory.count(0) + payment.count(0));
        assertFalse(sessions.foldPayments("Audit", inventory));
        assertEquals(73, inventory.count(0) + payment.count(0));
        java.util.List<PlayerInventory.DroppedStack> overflow = sessions.returnPayments("Audit", inventory);
        assertEquals(1, overflow.size());
        assertEquals(PlayerInventory.EMERALD, overflow.getFirst().itemType());
        assertEquals(9, overflow.getFirst().count());
        assertEquals(0, payment.count(0));
        assertTrue(sessions.returnPayments("Audit", inventory).isEmpty());
        assertEquals(73, inventory.count(0) + overflow.getFirst().count());
    }

    @Test
    void enoughSpaceReturnsPaymentWithoutChangingTotal() {
        VillagerTradeSessions sessions = new VillagerTradeSessions();
        assertNotNull(sessions.open("Audit", 1L, VillagerTradeRules.Profession.FARMER, bound -> 0));
        PlayerInventory inventory = new PlayerInventory();
        inventory.dropFromSlot(0, 1);
        assertEquals(10, inventory.addItem(PlayerInventory.EMERALD, 10));
        ContainerAccess payment = sessions.paymentAccess("Audit");
        assertTrue(inventory.clickContainer(payment, PlayerInventory.ContainerArea.INVENTORY, 0, PlayerInventory.CraftButton.LEFT, true));
        assertTrue(sessions.foldPayments("Audit", inventory));
        assertEquals(10, inventory.count(0));
        assertEquals(0, payment.count(0));
    }

    @Test
    void removedVillagerKeepsFundedSessionReachableUntilAutomaticReturn() {
        VillagerTradeSessions sessions = new VillagerTradeSessions();
        sessions.open("Audit", 1L, VillagerTradeRules.Profession.FARMER, bound -> 0);
        ContainerAccess payment = sessions.paymentAccess("Audit");
        assertEquals(10, payment.put(0, PlayerInventory.EMERALD, 10, 0, 0L, 0));
        sessions.forget(1L);
        assertEquals(Long.valueOf(1L), sessions.openMobId("Audit"));
        PlayerInventory inventory = new PlayerInventory();
        inventory.dropFromSlot(0, 1);
        assertTrue(sessions.returnPayments("Audit", inventory).isEmpty());
        assertEquals(10, inventory.count(0));
        sessions.close("Audit");
        assertNull(sessions.openMobId("Audit"));
        assertNull(sessions.paymentAccess("Audit"));
    }

    @Test
    void completelyFullInventoryReturnsBothPaymentsAsOverflow() {
        VillagerTradeSessions sessions = new VillagerTradeSessions();
        sessions.open("Audit", 1L, VillagerTradeRules.Profession.FARMER, bound -> 0);
        ContainerAccess payment = sessions.paymentAccess("Audit");
        assertEquals(10, payment.put(0, PlayerInventory.EMERALD, 10, 0, 0L, 0));
        assertEquals(2, payment.put(1, (short) Blocks.DIRT, 2, 0, 0L, 0));
        PlayerInventory inventory = new PlayerInventory();
        inventory.dropFromSlot(0, 1);
        assertEquals(PlayerInventory.SLOTS * 64,
                inventory.addItem((short) Blocks.STONE, PlayerInventory.SLOTS * 64));
        java.util.List<PlayerInventory.DroppedStack> overflow = sessions.returnPayments("Audit", inventory);
        assertEquals(2, overflow.size());
        assertEquals(10, overflow.get(0).count());
        assertEquals(2, overflow.get(1).count());
        assertEquals(0, payment.count(0));
        assertEquals(0, payment.count(1));
        sessions.close("Audit");
        assertNull(sessions.paymentAccess("Audit"));
    }

    @Test
    void selectingAnotherOfferDoesNotOverwriteUnreturnedPayments() {
        VillagerTradeSessions sessions = new VillagerTradeSessions();
        sessions.open("Audit", 1L, VillagerTradeRules.Profession.FARMER, bound -> 0);
        ContainerAccess payment = sessions.paymentAccess("Audit");
        assertEquals(10, payment.put(0, PlayerInventory.EMERALD, 10, 0, 0L, 0));
        PlayerInventory inventory = new PlayerInventory();
        inventory.dropFromSlot(0, 1);
        inventory.addItem((short) Blocks.STONE, PlayerInventory.SLOTS * 64);
        assertFalse(sessions.selectAndFill("Audit", 1L, 0, inventory));
        assertEquals(10, payment.count(0));
        assertEquals(PlayerInventory.EMERALD, payment.itemType(0));
        assertEquals(0, payment.count(1));
    }

    @Test
    void fullInventoryReturnPreservesToolDurabilityAndEnchantments() {
        VillagerTradeSessions sessions = new VillagerTradeSessions();
        sessions.open("Audit", 1L, VillagerTradeRules.Profession.FARMER, bound -> 0);
        ContainerAccess payment = sessions.paymentAccess("Audit");
        long enchantments = EnchantmentRules.withEnchantLevel(0L, EnchantmentRules.SHARPNESS, 2);
        assertEquals(1, payment.put(0, PlayerInventory.SWORD_ITEM, 1, 37, enchantments, 0));
        PlayerInventory inventory = new PlayerInventory();
        inventory.dropFromSlot(0, 1);
        inventory.addItem((short) Blocks.STONE, PlayerInventory.SLOTS * 64);
        java.util.List<PlayerInventory.DroppedStack> overflow = sessions.returnPayments("Audit", inventory);
        assertEquals(1, overflow.size());
        PlayerInventory.DroppedStack returned = overflow.getFirst();
        assertEquals(PlayerInventory.SWORD_ITEM, returned.itemType());
        assertEquals(1, returned.count());
        assertEquals(37, returned.durability());
        assertEquals(enchantments, returned.enchantments());
        assertEquals(0, payment.count(0));
        assertTrue(sessions.returnPayments("Audit", inventory).isEmpty());
    }
}
