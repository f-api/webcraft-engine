package com.gameexpert.engine.hopper;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Line-by-line port of the pinned {@code HopperBlockEntity} transfer statics. Container adapters
 * supply the storage; this class owns only the order of reads and writes, so the Spring and
 * standalone ports ({@code StandaloneHopperRules.ts}) can be compared step by step.
 *
 * <p>The vanilla code removes one item before it knows whether the destination accepts it and
 * restores the count on failure. Here the acceptance test runs first; the only observable
 * difference of the vanilla order is {@code SimpleContainer.removeItem}'s {@code setChanged},
 * which the composter output reports through {@link HopperContainer#failedRemoveAttempt}.
 */
public final class HopperTransfer {

    private HopperTransfer() {}

    /** Resolves the hopper's neighbours at the moment vanilla reads them. */
    public interface World {
        /** {@code getAttachedContainer}: the container at {@code pos.relative(facing)}. */
        HopperContainer attachedContainer();

        /** {@code getSourceContainer}: block container above, else an entity container. */
        HopperContainer sourceContainer();

        /**
         * {@code isGridAligned && above.isCollisionShapeFullBlock && !above.is(DOES_NOT_BLOCK_HOPPERS)};
         * consulted only when {@link #sourceContainer()} is null.
         */
        boolean aboveBlocksSuction();

        /** {@code getItemsAtAndAbove}: live item entities intersecting the suck box. */
        List<? extends Item> itemsInSuckArea();
    }

    /** One live {@code ItemEntity}. */
    public interface Item {
        HopperStack stack();

        /** {@code setItem(remaining)}; an empty remainder discards the entity. */
        void setStack(HopperStack remaining);
    }

    /** {@code HopperBlockEntity.pushItemsTick}. */
    public static boolean pushItemsTick(HopperContainer hopper, int state, long gameTime,
            World world) {
        HopperCooldown cooldown = requireHopper(hopper);
        cooldown.beginTick(gameTime);
        if (cooldown.isOnCooldown()) return false;
        cooldown.setCooldown(0);
        return tryMoveItems(hopper, state, world, () -> suckInItems(hopper, world));
    }

    /** {@code HopperBlockEntity.entityInside} for an item already known to be inside the cell. */
    public static boolean entityInside(HopperContainer hopper, int state, World world, Item item) {
        if (item.stack().isEmpty()) return false;
        return tryMoveItems(hopper, state, world, () -> addItem(hopper, item));
    }

    /** {@code HopperBlockEntity.tryMoveItems}. */
    static boolean tryMoveItems(HopperContainer hopper, int state, World world,
            BooleanSupplier suck) {
        HopperCooldown cooldown = requireHopper(hopper);
        if (cooldown.isOnCooldown() || !HopperRules.enabled(state)) return false;
        boolean moved = false;
        if (!hopper.isEmpty()) moved = ejectItems(hopper, state, world);
        // Vanilla uses a non-short-circuit OR: a push does not skip the pull.
        if (!inventoryFull(hopper)) moved |= suck.getAsBoolean();
        if (!moved) return false;
        cooldown.setCooldown(HopperRules.MOVE_ITEM_SPEED);
        hopper.setChanged();
        return true;
    }

    /** {@code HopperBlockEntity.inventoryFull}. */
    public static boolean inventoryFull(HopperContainer hopper) {
        for (int slot = 0; slot < hopper.size(); slot++) {
            HopperStack stack = hopper.get(slot);
            if (stack.isEmpty() || stack.count() != stack.maxStackSize()) return false;
        }
        return true;
    }

    /** {@code HopperBlockEntity.ejectItems}. */
    static boolean ejectItems(HopperContainer hopper, int state, World world) {
        HopperContainer destination = world.attachedContainer();
        if (destination == null) return false;
        int face = HopperRules.opposite(HopperRules.facing(state));
        if (isFullContainer(destination, face)) return false;
        for (int slot = 0; slot < hopper.size(); slot++) {
            HopperStack stack = hopper.get(slot);
            if (stack.isEmpty()) continue;
            HopperStack one = stack.withCount(1);
            if (firstAcceptingSlot(destination, one, face) < 0) continue;
            hopper.removeOne(slot);
            HopperStack remainder = addItem(hopper, destination, one, face);
            if (!remainder.isEmpty()) throw new IllegalStateException("hopper eject diverged");
            destination.setChanged();
            return true;
        }
        return false;
    }

    /** {@code HopperBlockEntity.isFullContainer}. */
    public static boolean isFullContainer(HopperContainer container, int face) {
        for (int slot : slots(container, face)) {
            HopperStack stack = container.get(slot);
            if (stack.count() < stack.maxStackSize()) return false;
        }
        return true;
    }

    /** {@code HopperBlockEntity.suckInItems}. */
    public static boolean suckInItems(HopperContainer hopper, World world) {
        HopperContainer source = world.sourceContainer();
        if (source != null) {
            int face = HopperRules.DOWN;
            for (int slot : slots(source, face)) {
                if (tryTakeInItemFromSlot(hopper, source, slot, face)) return true;
            }
            return false;
        }
        if (world.aboveBlocksSuction()) return false;
        for (Item item : world.itemsInSuckArea()) {
            if (addItem(hopper, item)) return true;
        }
        return false;
    }

    /** {@code HopperBlockEntity.tryTakeInItemFromSlot}. */
    static boolean tryTakeInItemFromSlot(HopperContainer hopper, HopperContainer source,
            int slot, int face) {
        HopperStack stack = source.get(slot);
        if (stack.isEmpty() || !canTakeItemFromContainer(source, stack, slot, face)) {
            return false;
        }
        HopperStack one = stack.withCount(1);
        if (firstAcceptingSlot(hopper, one, HopperRules.NO_FACE) < 0) {
            source.failedRemoveAttempt(slot);
            return false;
        }
        source.removeOne(slot);
        HopperStack remainder = addItem(source, hopper, one, HopperRules.NO_FACE);
        if (!remainder.isEmpty()) throw new IllegalStateException("hopper take diverged");
        source.setChanged();
        return true;
    }

    /** {@code HopperBlockEntity.addItem(Container, ItemEntity)}. */
    public static boolean addItem(HopperContainer destination, Item item) {
        HopperStack remainder = addItem(null, destination, item.stack(), HopperRules.NO_FACE);
        item.setStack(remainder);
        return remainder.isEmpty();
    }

    /** {@code HopperBlockEntity.addItem(Container, Container, ItemStack, Direction)}. */
    public static HopperStack addItem(HopperContainer source, HopperContainer destination,
            HopperStack stack, int face) {
        if (destination.kind().worldly() && face != HopperRules.NO_FACE) {
            int[] slots = destination.slotsForFace(face);
            for (int index = 0; index < slots.length && !stack.isEmpty(); index++) {
                stack = tryMoveInItem(source, destination, stack, slots[index], face);
            }
            return stack;
        }
        int size = destination.size();
        for (int slot = 0; slot < size && !stack.isEmpty(); slot++) {
            stack = tryMoveInItem(source, destination, stack, slot, face);
        }
        return stack;
    }

    /** {@code HopperBlockEntity.tryMoveInItem}. */
    static HopperStack tryMoveInItem(HopperContainer source, HopperContainer destination,
            HopperStack stack, int slot, int face) {
        HopperStack current = destination.get(slot);
        if (!canPlaceItemInContainer(destination, stack, slot, face)) return stack;
        boolean changed = false;
        boolean wasEmpty = destination.isEmpty();
        if (current.isEmpty()) {
            int limit = Math.min(destination.maxStackSize(), stack.maxStackSize());
            destination.setItem(slot, stack.withCount(Math.min(stack.count(), limit)));
            stack = HopperStack.EMPTY;
            changed = true;
        } else if (canMergeItems(current, stack)) {
            int space = stack.maxStackSize() - current.count();
            int moved = Math.min(stack.count(), space);
            if (moved > 0) {
                destination.grow(slot, moved);
                stack = stack.withCount(stack.count() - moved);
                changed = true;
            }
        }
        if (changed) {
            HopperCooldown destinationHopper = destination.hopper();
            if (wasEmpty && destinationHopper != null && !destinationHopper.isOnCustomCooldown()) {
                int earlier = 0;
                HopperCooldown sourceHopper = source == null ? null : source.hopper();
                if (sourceHopper != null
                        && destinationHopper.tickedGameTime() >= sourceHopper.tickedGameTime()) {
                    earlier = 1;
                }
                destinationHopper.setCooldown(HopperRules.MOVE_ITEM_SPEED - earlier);
            }
            destination.setChanged();
        }
        return stack;
    }

    /**
     * The slot {@link #addItem} would fill first for a one-item stack, or -1. The conditions are
     * exactly {@link #tryMoveInItem}'s so a positive answer is always followed by a full move.
     */
    static int firstAcceptingSlot(HopperContainer destination, HopperStack one, int face) {
        int[] order = destination.kind().worldly() && face != HopperRules.NO_FACE
                ? destination.slotsForFace(face) : HopperRules.flatSlots(destination.size());
        for (int slot : order) {
            if (!canPlaceItemInContainer(destination, one, slot, face)) continue;
            HopperStack current = destination.get(slot);
            if (current.isEmpty()) return slot;
            if (canMergeItems(current, one) && one.maxStackSize() - current.count() > 0) {
                return slot;
            }
        }
        return -1;
    }

    /** {@code HopperBlockEntity.canPlaceItemInContainer}. */
    static boolean canPlaceItemInContainer(HopperContainer container, HopperStack stack,
            int slot, int face) {
        if (!container.canPlaceItem(slot, stack)) return false;
        return !container.kind().worldly()
                || HopperRules.canPlaceItemThroughFace(container.kind(),
                        container.composterChanged(), true, stack, face);
    }

    /** {@code HopperBlockEntity.canTakeItemFromContainer}. */
    static boolean canTakeItemFromContainer(HopperContainer source, HopperStack stack,
            int slot, int face) {
        if (!source.canTakeItem(slot, stack)) return false;
        return !source.kind().worldly()
                || HopperRules.canTakeItemThroughFace(source.kind(), source.composterChanged(),
                        slot, stack, face);
    }

    /** {@code HopperBlockEntity.canMergeItems}. */
    static boolean canMergeItems(HopperStack current, HopperStack incoming) {
        return current.count() <= current.maxStackSize()
                && HopperStack.isSameItemSameComponents(current, incoming);
    }

    private static int[] slots(HopperContainer container, int face) {
        return container.kind().worldly()
                ? container.slotsForFace(face) : HopperRules.flatSlots(container.size());
    }

    private static HopperCooldown requireHopper(HopperContainer hopper) {
        HopperCooldown cooldown = hopper.hopper();
        if (cooldown == null) throw new IllegalArgumentException("hopper block entity required");
        return cooldown;
    }
}
