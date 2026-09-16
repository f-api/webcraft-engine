package com.gameexpert.engine.hopper;

/**
 * The part of {@code net.minecraft.world.Container}/{@code WorldlyContainer} the hopper transfer
 * reads and writes. Runtime adapters bind it to the repository's coordinate storages.
 *
 * <p>Mutations mirror the vanilla calls one-to-one: {@link #setItem} is {@code Container.setItem}
 * (with its container side effects), {@link #grow} is {@code ItemStack.grow} on the stack already
 * in the slot, {@link #removeOne} is {@code removeItem(slot, 1)}, {@link #setChanged} is
 * {@code Container.setChanged}.
 */
public interface HopperContainer {

    /** A container that exists but cannot be read yet (canonical LOOT still materialising). */
    HopperContainer UNAVAILABLE = new HopperContainer() {
        @Override public HopperRules.ContainerKind kind() { return HopperRules.ContainerKind.GENERIC; }
        @Override public int size() { return 0; }
        @Override public HopperStack get(int slot) { return HopperStack.EMPTY; }
        @Override public boolean canPlaceItem(int slot, HopperStack stack) { return false; }
        @Override public void setItem(int slot, HopperStack stack) {
            throw new IllegalStateException("unavailable container");
        }
        @Override public void grow(int slot, int amount) {
            throw new IllegalStateException("unavailable container");
        }
        @Override public void removeOne(int slot) {
            throw new IllegalStateException("unavailable container");
        }
        @Override public void setChanged() { }
    };

    HopperRules.ContainerKind kind();

    int size();

    HopperStack get(int slot);

    /** {@code Container.getMaxStackSize()}: 99 by default, 1 for the composter containers. */
    default int maxStackSize() {
        return HopperRules.DEFAULT_CONTAINER_MAX_STACK;
    }

    /** {@code Container.canPlaceItem}, narrowed to what the repository storage can persist. */
    boolean canPlaceItem(int slot, HopperStack stack);

    /** {@code Container.canTakeItem}; no repository container overrides the default. */
    default boolean canTakeItem(int slot, HopperStack stack) {
        return true;
    }

    void setItem(int slot, HopperStack stack);

    void grow(int slot, int amount);

    void removeOne(int slot);

    /** Validate an imminent destination write before a source can durably consume an item. */
    default void preflightTransfer(int slot) { }

    /** Runtime inventories override this to roll back only this removal on insertion failure. */
    default void removeOneAndInsert(int slot, Runnable insertion) {
        removeOne(slot);
        insertion.run();
    }

    /**
     * The vanilla failed-take path restores the source count and, for a single item, calls
     * {@code setItem} again. Only {@code SimpleContainer} (the composter output) observes that:
     * its {@code removeItem} already fired {@code setChanged} and emptied the composter.
     */
    default void failedRemoveAttempt(int slot) { }

    void setChanged();

    /** The composter containers' one-shot {@code changed} flag. */
    default boolean composterChanged() {
        return false;
    }

    /** Non-null only for a {@code HopperBlockEntity} destination/source. */
    default HopperCooldown hopper() {
        return null;
    }

    default boolean isEmpty() {
        for (int slot = 0; slot < size(); slot++) {
            if (!get(slot).isEmpty()) return false;
        }
        return true;
    }

    default int[] slotsForFace(int face) {
        return HopperRules.slotsForFace(kind(), size(), face);
    }
}
