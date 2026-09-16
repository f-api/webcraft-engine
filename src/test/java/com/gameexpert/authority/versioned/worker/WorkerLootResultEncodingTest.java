package com.gameexpert.authority.versioned.worker;

import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootStoredResolution;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.*;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WorkerLootResultEncodingTest {
    @Test
    void legacyMapBytesRemainReadableAndUnchanged() throws Exception {
        Resolution source = resolution(Arrays.asList(stack("minecraft:buried_treasure_map", 1,
                Map.of("minecraft:map_id", new MapId(42))), null), Continuation.Kind.LEGACY_48);
        byte[] saved = CanonicalLootStoredResolution.from(source).encode();
        CanonicalLootStoredResolution corrected = WorkerLootResultEncoding.encode(source);
        assertThat(corrected.schemaVersion()).isEqualTo(2);
        assertThat(corrected.slots().getFirst().maximumStackSize()).isEqualTo(64);
        assertThat(corrected.slots().getFirst().components().get("minecraft:map_id").mapId()).isEqualTo(42);
        assertThat(corrected.slots().get(1)).isNull();
        assertThat(corrected.continuationKind()).isEqualTo(Continuation.Kind.LEGACY_48);
        assertThat(corrected.continuationFirst()).isEqualTo(123L);
        CanonicalLootStoredResolution old = CanonicalLootStoredResolution.decode(saved);
        assertThat(old.schemaVersion()).isEqualTo(1);
        assertThat(old.slots().getFirst().maximumStackSize()).isEqualTo(1);
        assertThat(old.encode()).containsExactly(saved);
        assertThat(CanonicalLootStoredResolution.from(source).encode()).containsExactly(saved);
    }

    @Test
    void alreadyValidV2ItemsAndRandomContinuationKeepTheirExactBytes() throws Exception {
        Resolution source = resolution(List.of(stack("minecraft:filled_map", 1,
                Map.of("minecraft:map_id", new MapId(7))), stack("minecraft:gold_ingot", 12, Map.of())),
                Continuation.Kind.XOROSHIRO_128_PLUS_PLUS);
        assertThat(WorkerLootResultEncoding.encode(source).encode())
                .containsExactly(CanonicalLootStoredResolution.fromV2(source).encode());
    }

    @Test
    void mixedLegacyAndV2MapsDoNotForceAllSlotsThroughV1() throws Exception {
        Resolution source = resolution(List.of(stack("minecraft:buried_treasure_map", 1, Map.of()),
                stack("minecraft:filled_map", 1, Map.of())), Continuation.Kind.XOROSHIRO_128_PLUS_PLUS);
        CanonicalLootStoredResolution result = WorkerLootResultEncoding.encode(source);
        assertThat(result.slots().stream().filter(java.util.Objects::nonNull).toList()).allSatisfy(slot -> assertThat(slot.maximumStackSize()).isEqualTo(64));
        assertThat(result.continuationFirst()).isEqualTo(123L);
        assertThat(result.continuationSecond()).isEqualTo(456L);
    }

    @Test
    void invalidCountsRemainRejected() throws Exception {
        for (String item : List.of("minecraft:buried_treasure_map", "minecraft:iron_sword")) {
            Resolution invalid = resolution(List.of(stack(item, 2, Map.of())), Continuation.Kind.LEGACY_48);
            assertThatThrownBy(() -> WorkerLootResultEncoding.encode(invalid))
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        }
        Resolution invalidMap = resolution(List.of(stack("minecraft:filled_map", 2,
                Map.of("minecraft:map_id", new MapId(7)))), Continuation.Kind.LEGACY_48);
        assertThatThrownBy(() -> WorkerLootResultEncoding.encode(invalidMap))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    private static LootStack stack(String item, int count, Map<String, ComponentValue> components) throws Exception {
        Constructor<LootStack> constructor = LootStack.class.getDeclaredConstructor(String.class, int.class, Map.class);
        constructor.setAccessible(true);
        return constructor.newInstance(item, count, components);
    }

    private static Resolution resolution(List<LootStack> slots, Continuation.Kind kind) throws Exception {
        Constructor<Continuation> continuation = Continuation.class.getDeclaredConstructor(Continuation.Kind.class, long.class, long.class);
        continuation.setAccessible(true);
        Constructor<Resolution> constructor = Resolution.class.getDeclaredConstructor(List.class, Continuation.class);
        constructor.setAccessible(true);
        List<LootStack> container = new java.util.ArrayList<>(java.util.Collections.nCopies(27, null));
        for (int index = 0; index < slots.size(); index++) container.set(index, slots.get(index));
        return constructor.newInstance(container, continuation.newInstance(kind, 123L, kind == Continuation.Kind.LEGACY_48 ? 0L : 456L));
    }
}
