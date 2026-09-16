package com.gameexpert.audit;

import com.gameexpert.engine.inventory.ItemComponentCodec;
import com.gameexpert.engine.inventory.ItemComponentData;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.enchant.WideEnchantments;
import com.gameexpert.terrain.Blocks;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ItemComponentV7CompatibilityTest {
    @Test void oldComponentVersionsRetainExactBytes() {
        roundTrip(PlayerInventory.GOAT_HORN, "WCIC2|0|SG9ybg||~|~|~|~|~|~");
        roundTrip(PlayerInventory.OMINOUS_BOTTLE, "WCIC3|0|~||~|~|~|~|~|~|2");
        roundTrip(PlayerInventory.ENCHANTED_BOOK, "WCIC4|0|~||~|~|~|~|~|~|~|1");
        roundTrip(PlayerInventory.CONTENTS_POTION, "WCIC5|0|~||~|~|~|~|~|~|~|~|water|~");
        roundTrip((short) Blocks.DECORATED_POT,
                "WCIC6|0|~||~|~|~|~|~|~|~|~|~|~|765:0:~,765:0:~,765:0:~,765:0:~");
    }
    @Test void allHornInstrumentsRoundTripAndSurviveCopyMethods() {
        for (String name : List.of("ponder", "sing", "seek", "feel", "admire", "call", "yearn", "dream")) {
            String instrument = "minecraft:" + name + "_goat_horn";
            ItemComponentData data = ItemComponentData.EMPTY.withInstrument(instrument);
            String encoded = ItemComponentCodec.encode(PlayerInventory.GOAT_HORN, data);
            assertThat(encoded).isEqualTo("WCIC7|0|~||~|~|~|~|~|~|~|~|~|~|~|" + instrument);
            roundTrip(PlayerInventory.GOAT_HORN, encoded);
            for (ItemComponentData copy : List.of(data.withCustomName("Horn"), data.afterAnvilUse(),
                    data.withBannerPatterns(List.of()), data.withBook(null), data.withLeatherColor(null),
                    data.withSuspiciousStewEffect(null, null), data.withOminousBottleAmplifier(0),
                    data.withEnchantments(WideEnchantments.EMPTY), data.withPotionContents(null),
                    data.withTrim(null), data.withPotDecorations(List.of()))) {
                assertThat(copy.instrument()).isEqualTo(instrument);
            }
        }
    }
    private static void roundTrip(short item, String encoded) {
        assertThat(ItemComponentCodec.encode(item, ItemComponentCodec.decode(item, encoded))).isEqualTo(encoded);
    }
}
