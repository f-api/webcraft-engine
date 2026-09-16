package com.gameexpert.state.entity;

import com.gameexpert.chest.entity.ChestItem;
import com.gameexpert.engine.inventory.ItemComponentCodec;
import com.gameexpert.engine.inventory.ItemComponentData;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.mob.BucketMobPayloadCodec;
import com.gameexpert.engine.mob.MobType;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoragePayloadCapacityTest {
    @Test
    void sixFullPagesRoundTripThroughBothItemEntities() {
        verifyBook(6);
    }

    @Test
    void hundredFullPagesRoundTripThroughBothItemEntities() {
        verifyBook(100);
    }

    @Test
    void fiftyFiveKoreanCharacterBucketNameExceedsOldColumnButRemainsValid() {
        verifyBucket(55);
    }

    @Test
    void sixtyFourKoreanCharacterBucketNameRoundTripsThroughBothItemEntities() {
        verifyBucket(64);
    }

    @Test
    void largerColumnsDoNotBypassBookGameplayBounds() {
        assertThrows(IllegalArgumentException.class, () -> new ItemComponentData.BookData(
                null, null, Collections.nCopies(101, "page")));
        assertThrows(IllegalArgumentException.class, () -> new ItemComponentData.BookData(
                null, null, java.util.List.of("한".repeat(1025))));
    }

    @Test
    void largerColumnsDoNotBypassBucketCodecIntegrity() {
        assertThrows(IllegalArgumentException.class, () -> new ChestItem(
                0, PlayerInventory.AXOLOTL_BUCKET, 1, null, null, null, null,
                "WCMB1|axolotl|invalid_variant|0|name", null));
        assertThrows(IllegalArgumentException.class, () -> BucketMobPayloadCodec.encode(
                MobType.AXOLOTL, "lucy", 0, "한".repeat(257)));
    }

    private static void verifyBook(int pageCount) {
        ItemComponentData.BookData book = new ItemComponentData.BookData(
                null, null, Collections.nCopies(pageCount, "한".repeat(1024)));
        String encoded = ItemComponentCodec.encode(PlayerInventory.WRITABLE_BOOK,
                ItemComponentData.EMPTY.withBook(book));
        assertTrue(encoded.length() > 8192, "Exercise the old inventory VARCHAR bound");
        if (pageCount == 100) assertTrue(encoded.length() > 65535, "Exercise the old TEXT byte bound");
        InventoryItem inventory = new InventoryItem(0, PlayerInventory.WRITABLE_BOOK, 1,
                null, null, null, null, null, encoded);
        ChestItem chest = new ChestItem(0, PlayerInventory.WRITABLE_BOOK, 1,
                null, null, null, null, null, encoded);
        assertEquals(encoded, inventory.getItemComponentData());
        assertEquals(encoded, chest.getItemComponentData());
        ItemComponentData decoded = ItemComponentCodec.decode(PlayerInventory.WRITABLE_BOOK,
                chest.getItemComponentData());
        assertEquals(book.pages(), decoded.book().pages());
        assertEquals(encoded, ItemComponentCodec.encode(PlayerInventory.WRITABLE_BOOK, decoded));
    }

    private static void verifyBucket(int nameLength) {
        String name = "한".repeat(nameLength);
        String encoded = BucketMobPayloadCodec.encode(MobType.AXOLOTL, "lucy", 0, name);
        assertTrue(encoded.length() > 512, "Exercise the old chest column/constructor bound");
        InventoryItem inventory = new InventoryItem(0, PlayerInventory.AXOLOTL_BUCKET, 1,
                null, null, null, null, encoded, null);
        ChestItem chest = new ChestItem(0, PlayerInventory.AXOLOTL_BUCKET, 1,
                null, null, null, null, encoded, null);
        assertEquals(encoded, inventory.getBucketMobData());
        assertEquals(encoded, chest.getBucketMobData());
        assertEquals(name, BucketMobPayloadCodec.decode(chest.getBucketMobData(), MobType.AXOLOTL).customName());
    }
}
