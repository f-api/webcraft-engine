package com.gameexpert.engine;

import com.gameexpert.engine.inventory.ItemComponentCodec;
import com.gameexpert.engine.inventory.ItemComponentData;
import com.gameexpert.engine.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GoatHornSoundSelectionTest {
    @Test
    void metadataSelectsEachInstrumentSound() {
        java.util.stream.Stream.of("ponder", "sing", "seek", "feel", "admire", "call", "yearn", "dream")
                .forEach(name -> {
                    String components = ItemComponentCodec.encode(PlayerInventory.GOAT_HORN,
                            ItemComponentData.EMPTY.withInstrument("minecraft:" + name + "_goat_horn"));
                    PlayerInventory.StackSnapshot held = new PlayerInventory.StackSnapshot(
                            PlayerInventory.GOAT_HORN, 1, 0, 0L, 0, 0, null, components);
                    assertEquals("goat_horn_" + name, WorldTickLoop.goatHornSound(held));
                });
    }

    @Test
    void legacyHornWithoutMetadataUsesPonder() {
        PlayerInventory.StackSnapshot held = new PlayerInventory.StackSnapshot(
                PlayerInventory.GOAT_HORN, 1, 0, 0L, 0, 0, null, null);
        assertEquals("goat_horn_ponder", WorldTickLoop.goatHornSound(held));
    }
}
