package com.gameexpert.engine.mob.villager;

import com.gameexpert.engine.inventory.ContainerAccess;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.terrain.Blocks;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VillagerPaymentReturnTest {
    private static final class Fixture {
        final PlayerInventory inventory = new PlayerInventory();
        final VillagerTradeSessions sessions = new VillagerTradeSessions();
        final ContainerAccess payment;
        Fixture(int emeralds) {
            inventory.dropFromSlot(0, 1);
            assertEquals(emeralds, inventory.addItem(PlayerInventory.EMERALD, emeralds));
            assertNotNull(sessions.open("Audit", 1L, VillagerTradeRules.Profession.FARMER,
                    bound -> 0, inventory));
            payment = sessions.paymentAccess("Audit");
            assertTrue(inventory.clickContainer(payment, PlayerInventory.ContainerArea.INVENTORY,
                    0, PlayerInventory.CraftButton.LEFT, true));
        }
    }

    @Test void successfulCheckpointIncludesPaymentWithoutChangingVisibleSlots() {
        Fixture fixture = new Fixture(10);
        PlayerInventory.CompletePersistenceSnapshot snapshot = fixture.inventory.completePersistenceSnapshot();
        assertEquals(0, fixture.inventory.count(0));
        assertEquals(10, fixture.payment.count(0));
        assertEquals(10, emeralds(snapshot));
        PlayerInventory detached = snapshot.detachedInventory();
        assertEquals(0, detached.count(0));
        assertEquals(10, detached.merchantPayments().count(0));
        assertEquals(10, emeralds(detached.completePersistenceSnapshot()));
    }

    @Test void pickupReservesPaymentReturnCapacityAndRepeatedReturnDoesNotDuplicate() {
        Fixture fixture = new Fixture(10);
        assertEquals((PlayerInventory.SLOTS - 1) * 64,
                fixture.inventory.addItem((short) Blocks.STONE, PlayerInventory.SLOTS * 64));
        assertEquals(54, fixture.inventory.addItem(PlayerInventory.EMERALD, 63));
        assertEquals(0, fixture.inventory.addItem(PlayerInventory.EMERALD, 1));
        assertTrue(fixture.sessions.foldPayments("Audit", fixture.inventory));
        assertEquals(PlayerInventory.EMERALD, fixture.inventory.itemType(PlayerInventory.SLOTS - 1));
        assertEquals(64, fixture.inventory.count(PlayerInventory.SLOTS - 1));
        assertEquals(0, fixture.payment.count(0));
        assertTrue(fixture.sessions.returnPayments("Audit", fixture.inventory).isEmpty());
        assertEquals(64, emeralds(fixture.inventory.completePersistenceSnapshot()));
    }

    @Test void orderlyCloseReturnsExactlyOnce() {
        Fixture fixture = new Fixture(10);
        assertTrue(fixture.sessions.returnPayments("Audit", fixture.inventory).isEmpty());
        fixture.sessions.close("Audit");
        assertEquals(10, emeralds(fixture.inventory.completePersistenceSnapshot()));
        assertNull(fixture.sessions.paymentAccess("Audit"));
        assertTrue(fixture.sessions.returnPayments("Audit", fixture.inventory).isEmpty());
    }

    @Test void removedVillagerKeepsFundedSessionReachableUntilAutomaticReturn() {
        Fixture fixture = new Fixture(10);
        fixture.sessions.forget(1L);
        assertEquals(Long.valueOf(1L), fixture.sessions.openMobId("Audit"));
        assertTrue(fixture.sessions.returnPayments("Audit", fixture.inventory).isEmpty());
        fixture.sessions.close("Audit");
        assertEquals(10, emeralds(fixture.inventory.completePersistenceSnapshot()));
    }

    @Test void leaseBlocksPaymentMutationAndSettlementPreservesRawPaymentExactlyOnce() {
        Fixture fixture = new Fixture(10);
        PlayerInventory.CompletePersistenceSnapshot source = fixture.inventory.acquireSettlementLease();
        assertNotNull(source);
        assertEquals(0, fixture.payment.take(0, 1));
        assertEquals(0, fixture.payment.put(1, PlayerInventory.EMERALD, 1, 0, 0L, 0));
        PlayerInventory.CompletePersistenceSnapshot committed = source.nextSettlementRevision();
        assertTrue(fixture.inventory.installCommittedSettlement(source, committed));
        assertEquals(0, fixture.inventory.count(0));
        assertEquals(10, fixture.payment.count(0));
        assertEquals(10, emeralds(fixture.inventory.completePersistenceSnapshot()));
        assertTrue(fixture.sessions.returnPayments("Audit", fixture.inventory).isEmpty());
        assertEquals(10, emeralds(fixture.inventory.completePersistenceSnapshot()));
    }

    @Test void cursorCloseDoesNotInstallNormalizedPaymentsTwice() {
        Fixture fixture = new Fixture(20);
        assertTrue(fixture.inventory.clickContainer(fixture.payment, PlayerInventory.ContainerArea.CONTAINER,
                0, PlayerInventory.CraftButton.RIGHT, false));
        assertEquals(10, fixture.payment.count(0));
        assertEquals(10, fixture.inventory.cursorCount());
        assertEquals(20, emeralds(fixture.inventory.completePersistenceSnapshot()));
        assertTrue(fixture.inventory.closeContainerCursor().isEmpty());
        assertEquals(0, fixture.payment.count(0));
        assertEquals(0, fixture.inventory.cursorCount());
        assertEquals(20, emeralds(fixture.inventory.completePersistenceSnapshot()));
        assertTrue(fixture.sessions.returnPayments("Audit", fixture.inventory).isEmpty());
        assertEquals(20, emeralds(fixture.inventory.completePersistenceSnapshot()));
    }

    @Test void craftingCloseAndReopenFoldPaymentsOnce() {
        Fixture fixture = new Fixture(10);
        fixture.inventory.openCrafting(2);
        fixture.inventory.openCrafting(2);
        fixture.inventory.closeCrafting();
        assertEquals(0, fixture.payment.count(0));
        assertEquals(10, emeralds(fixture.inventory.completePersistenceSnapshot()));
    }

    @Test void failedMultiSlotMutationRollsBackSlotsAndRevision() {
        Fixture fixture = new Fixture(10);
        long revision = fixture.inventory.revision();
        assertThrows(IllegalStateException.class, () -> fixture.inventory.mutateMerchantPayments(() -> {
            fixture.payment.take(0, 3);
            fixture.payment.put(1, PlayerInventory.EMERALD, 3, 0, 0L, 0);
            throw new IllegalStateException("second payment failed");
        }));
        assertEquals(revision, fixture.inventory.revision());
        assertEquals(10, fixture.payment.count(0));
        assertEquals(0, fixture.payment.count(1));
        assertEquals(10, emeralds(fixture.inventory.completePersistenceSnapshot()));
    }

    @Test void deathDrainsPaymentsOnce() {
        Fixture fixture = new Fixture(10);
        List<PlayerInventory.DroppedStack> drops = fixture.inventory.drainForDeath();
        assertEquals(10, drops.stream().filter(stack -> stack.itemType() == PlayerInventory.EMERALD)
                .mapToInt(PlayerInventory.DroppedStack::count).sum());
        assertEquals(0, fixture.payment.count(0));
        assertEquals(0, emeralds(fixture.inventory.completePersistenceSnapshot()));
        assertTrue(fixture.inventory.drainForDeath().isEmpty());
    }

    @Test void toolDurabilityAndEnchantmentsSurviveSnapshotAndClose() {
        PlayerInventory inventory = new PlayerInventory();
        inventory.dropFromSlot(0, 1);
        long enchantments = EnchantmentRules.withEnchantLevel(0L, EnchantmentRules.SHARPNESS, 2);
        assertEquals(1, inventory.addItem(PlayerInventory.SWORD_ITEM, 1, 37, enchantments));
        VillagerTradeSessions sessions = new VillagerTradeSessions();
        sessions.open("Audit", 1L, VillagerTradeRules.Profession.FARMER, bound -> 0, inventory);
        ContainerAccess payment = sessions.paymentAccess("Audit");
        assertTrue(inventory.clickContainer(payment, PlayerInventory.ContainerArea.INVENTORY,
                0, PlayerInventory.CraftButton.LEFT, true));
        PlayerInventory.PersistenceSnapshot snapshot = inventory.completePersistenceSnapshot().persistenceSnapshot();
        assertEquals(37, snapshot.durabilities()[0]);
        assertEquals(enchantments, snapshot.enchantments()[0]);
        assertTrue(sessions.returnPayments("Audit", inventory).isEmpty());
        assertEquals(37, inventory.durability(0));
        assertEquals(enchantments, inventory.enchantments(0));
    }

    private static int emeralds(PlayerInventory.CompletePersistenceSnapshot complete) {
        PlayerInventory.PersistenceSnapshot snapshot = complete.persistenceSnapshot();
        short[] types = snapshot.itemTypes();
        int[] counts = snapshot.counts();
        return IntStream.range(0, types.length).filter(index -> types[index] == PlayerInventory.EMERALD)
                .map(index -> counts[index]).sum();
    }
}
