package com.gameexpert.authority.versioned.worker;

import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootStoredResolution;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootStoredResolution.Slot;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts legacy producer map stacks to V2 without replacing pinned producer classes. */
final class WorkerLootResultEncoding {
    private static final Method SLOT_FROM;
    private static final Method IS_MAP;
    private static final Method MAXIMUM_STACK_SIZE;
    private static final Constructor<Slot> SLOT;
    private static final Constructor<CanonicalLootStoredResolution> RESULT;

    static {
        try {
            SLOT_FROM = Slot.class.getDeclaredMethod("from", Mc263ContainerLootResolver.LootStack.class, int.class);
            SLOT_FROM.setAccessible(true);
            Class<?> items = Class.forName("com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootResultV2Items");
            IS_MAP = items.getDeclaredMethod("isMapItem", String.class);
            IS_MAP.setAccessible(true);
            MAXIMUM_STACK_SIZE = items.getDeclaredMethod("maximumStackSize", String.class);
            MAXIMUM_STACK_SIZE.setAccessible(true);
            SLOT = Slot.class.getDeclaredConstructor(String.class, int.class, int.class, Map.class, int.class);
            SLOT.setAccessible(true);
            RESULT = CanonicalLootStoredResolution.class.getDeclaredConstructor(List.class,
                    Mc263ContainerLootResolver.Continuation.Kind.class, long.class, long.class, int.class);
            RESULT.setAccessible(true);
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private WorkerLootResultEncoding() { }

    static CanonicalLootStoredResolution encode(Mc263ContainerLootResolver.Resolution resolution)
            throws ReflectiveOperationException {
        List<Slot> slots = new ArrayList<>(resolution.slots().size());
        for (Mc263ContainerLootResolver.LootStack stack : resolution.slots()) {
            if (stack == null) {
                slots.add(null);
            } else if (stack.maximumStackSize() == 1
                    && (boolean) IS_MAP.invoke(null, stack.itemKey())
                    && (int) MAXIMUM_STACK_SIZE.invoke(null, stack.itemKey()) == 64) {
                // Validate legacy components first, then apply all V2 slot constraints.
                Slot legacy = (Slot) SLOT_FROM.invoke(null, stack, 1);
                slots.add(SLOT.newInstance(legacy.itemKey(), legacy.count(), 64, legacy.components(), 2));
            } else {
                slots.add((Slot) SLOT_FROM.invoke(null, stack, 2));
            }
        }
        Mc263ContainerLootResolver.Continuation continuation = resolution.continuation();
        boolean legacyRandom = continuation.kind() == Mc263ContainerLootResolver.Continuation.Kind.LEGACY_48;
        return RESULT.newInstance(slots, continuation.kind(),
                legacyRandom ? continuation.legacy48State() : continuation.xoroshiroSeedLo(),
                legacyRandom ? 0L : continuation.xoroshiroSeedHi(), 2);
    }
}
