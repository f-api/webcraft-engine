package com.gameexpert.engine.hopper;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;

/**
 * Pure pinned-26.3 hopper, container-face and comparator rules shared by both authorities. The
 * standalone twin is {@code client/src/backend/standalone/StandaloneHopperRules.ts}; every
 * constant below is read from the pinned client jar with {@code javap -c -p}:
 *
 * <ul>
 *   <li>{@code HopperBlockEntity.MOVE_ITEM_SPEED = 8}, {@code HOPPER_CONTAINER_SIZE = 5},
 *       {@code NO_COOLDOWN_TIME = -1}, the constructor stores {@code cooldownTime = -1}.</li>
 *   <li>{@code Hopper.SUCK_AABB = Block.column(16, 11, 32).toAabbs().get(0)} — the full column
 *       from {@code y + 11/16} to {@code y + 2} in block space.</li>
 *   <li>{@code HopperBlock.getStateForPlacement}: {@code facing = clickedFace.getOpposite()},
 *       and a vertical result becomes {@code DOWN}; {@code ENABLED = true}.</li>
 *   <li>{@code HopperBlock.checkPoweredState}: {@code enabled = !level.hasNeighborSignal(pos)}.</li>
 *   <li>{@code AbstractFurnaceBlockEntity.getSlotsForFace}: UP {0}, DOWN {2, 1}, sides {1}.</li>
 *   <li>{@code BrewingStandBlockEntity.getSlotsForFace}: UP {3}, DOWN {0, 1, 2, 3},
 *       sides {0, 1, 2, 4}.</li>
 *   <li>{@code ShulkerBoxBlockEntity.getSlotsForFace}: every face sees slots 0..26 and a shulker
 *       box item is refused through any face.</li>
 *   <li>{@code ComposterBlock.getContainer}: level 8 exposes the one-bone-meal
 *       {@code OutputContainer} (DOWN only), a level below 7 exposes {@code InputContainer}
 *       (UP only, compostable items, stack size 1), level 7 exposes {@code EmptyContainer}.</li>
 *   <li>{@code AbstractContainerMenu.getRedstoneSignalFromContainer} and
 *       {@code Mth.lerpDiscrete(f, 0, 15) = floor(f * 14) + (f > 0 ? 1 : 0)} in float.</li>
 * </ul>
 */
public final class HopperRules {

    public static final int MOVE_ITEM_SPEED = 8;
    public static final int CONTAINER_SIZE = 5;
    public static final int NO_COOLDOWN_TIME = -1;
    /** {@code Container.getMaxStackSize()} default. */
    public static final int DEFAULT_CONTAINER_MAX_STACK = 99;

    // Direction.get3DDataValue order.
    public static final int DOWN = 0;
    public static final int UP = 1;
    public static final int NORTH = 2;
    public static final int SOUTH = 3;
    public static final int WEST = 4;
    public static final int EAST = 5;
    /** Face argument used by {@code addItem(source, hopper, stack, null)}. */
    public static final int NO_FACE = -1;

    /** Hopper state byte: bits 0..2 carry FACING, bit 3 is set while ENABLED is false. */
    public static final int FACING_MASK = 0x07;
    public static final int LOCKED = 0x08;

    /** Local SUCK_AABB (block-space) bounds. */
    public static final double SUCK_MIN_Y = 11.0 / 16.0;
    public static final double SUCK_MAX_Y = 2.0;

    /** {@code EntityType.ITEM} is 0.25 x 0.25; the repository stores the bottom-centre. */
    public static final double ITEM_HALF_WIDTH = 0.125;
    public static final double ITEM_HEIGHT = 0.25;

    private HopperRules() {}

    /** The repository's hopper container shapes (the WorldlyContainer families plus generic). */
    public enum ContainerKind {
        GENERIC, HOPPER, FURNACE, BREWING_STAND, SHULKER_BOX,
        COMPOSTER_INPUT, COMPOSTER_OUTPUT, COMPOSTER_EMPTY;

        public boolean worldly() {
            return this == FURNACE || this == BREWING_STAND || this == SHULKER_BOX
                    || this == COMPOSTER_INPUT || this == COMPOSTER_OUTPUT
                    || this == COMPOSTER_EMPTY;
        }
    }

    // ── state ────────────────────────────────────────────────────────────

    public static int facing(int state) {
        int facing = state & FACING_MASK;
        return facing == UP || facing > EAST ? DOWN : facing;
    }

    public static boolean enabled(int state) {
        return (state & LOCKED) == 0;
    }

    public static int state(int facing, boolean enabled) {
        int normalized = facing == UP || facing < 0 || facing > EAST ? DOWN : facing;
        return normalized | (enabled ? 0 : LOCKED);
    }

    public static int withEnabled(int state, boolean enabled) {
        return state(facing(state), enabled);
    }

    /** Stored-state vocabulary; an UP or out-of-range facing folds to DOWN. */
    public static int normalizeState(int state) {
        return state(facing(state), enabled(state));
    }

    /**
     * {@code HopperBlock.getStateForPlacement}. The argument is the clicked face as the normal
     * from the clicked block toward the new cell.
     */
    public static int placementFacing(int normalX, int normalY, int normalZ) {
        if (normalY != 0 || normalX == 0 && normalZ == 0) return DOWN;
        if (normalX != 0) return normalX > 0 ? WEST : EAST;
        return normalZ > 0 ? NORTH : SOUTH;
    }

    public static int opposite(int direction) {
        return switch (direction) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
            default -> NO_FACE;
        };
    }

    public static int stepX(int direction) {
        return direction == EAST ? 1 : direction == WEST ? -1 : 0;
    }

    public static int stepY(int direction) {
        return direction == UP ? 1 : direction == DOWN ? -1 : 0;
    }

    public static int stepZ(int direction) {
        return direction == SOUTH ? 1 : direction == NORTH ? -1 : 0;
    }

    // ── suck area ────────────────────────────────────────────────────────

    /** {@code getItemsAtAndAbove}: the item box intersects SUCK_AABB moved to the hopper. */
    public static boolean itemInSuckArea(int hopperX, int hopperY, int hopperZ,
            double itemX, double itemY, double itemZ) {
        return intersects(hopperX, hopperY + SUCK_MIN_Y, hopperZ,
                hopperX + 1.0, hopperY + SUCK_MAX_Y, hopperZ + 1.0,
                itemX - ITEM_HALF_WIDTH, itemY, itemZ - ITEM_HALF_WIDTH,
                itemX + ITEM_HALF_WIDTH, itemY + ITEM_HEIGHT, itemZ + ITEM_HALF_WIDTH);
    }

    /**
     * {@code HopperBlock.entityInside} runs only for an entity overlapping the hopper cell
     * ({@code Entity.checkInsideBlocks} deflates the box by 1.0E-5) and then requires the local
     * box to intersect SUCK_AABB.
     */
    public static boolean itemInsideHopper(int hopperX, int hopperY, int hopperZ,
            double itemX, double itemY, double itemZ) {
        double minX = itemX - ITEM_HALF_WIDTH + 1.0E-5, maxX = itemX + ITEM_HALF_WIDTH - 1.0E-5;
        double minY = itemY + 1.0E-5, maxY = itemY + ITEM_HEIGHT - 1.0E-5;
        double minZ = itemZ - ITEM_HALF_WIDTH + 1.0E-5, maxZ = itemZ + ITEM_HALF_WIDTH - 1.0E-5;
        boolean insideCell = Math.floor(minX) <= hopperX && Math.floor(maxX) >= hopperX
                && Math.floor(minY) <= hopperY && Math.floor(maxY) >= hopperY
                && Math.floor(minZ) <= hopperZ && Math.floor(maxZ) >= hopperZ;
        return insideCell && itemInSuckArea(hopperX, hopperY, hopperZ, itemX, itemY, itemZ);
    }

    /** {@code AABB.intersects}: strict overlap on all three axes. */
    static boolean intersects(double aMinX, double aMinY, double aMinZ,
            double aMaxX, double aMaxY, double aMaxZ,
            double bMinX, double bMinY, double bMinZ,
            double bMaxX, double bMaxY, double bMaxZ) {
        return aMinX < bMaxX && aMaxX > bMinX && aMinY < bMaxY && aMaxY > bMinY
                && aMinZ < bMaxZ && aMaxZ > bMinZ;
    }

    /** {@code BlockTags.DOES_NOT_BLOCK_HOPPERS} = {@code #minecraft:beehives}. */
    public static boolean doesNotBlockHoppers(int blockId) {
        return blockId == Blocks.BEE_NEST || blockId == Blocks.BEEHIVE;
    }

    // ── container faces ──────────────────────────────────────────────────

    private static final int[] EMPTY_SLOTS = new int[0];
    private static final int[] SINGLE_SLOT = {0};
    private static final int[] FURNACE_UP = {0};
    private static final int[] FURNACE_DOWN = {2, 1};
    private static final int[] FURNACE_SIDES = {1};
    private static final int[] BREWING_UP = {3};
    private static final int[] BREWING_DOWN = {0, 1, 2, 3};
    private static final int[] BREWING_SIDES = {0, 1, 2, 4};

    /** {@code HopperBlockEntity.getSlots}: WorldlyContainer faces, otherwise 0..size-1. */
    public static int[] slotsForFace(ContainerKind kind, int size, int face) {
        return switch (kind) {
            case FURNACE -> (face == UP ? FURNACE_UP : face == DOWN ? FURNACE_DOWN
                    : FURNACE_SIDES).clone();
            case BREWING_STAND -> (face == UP ? BREWING_UP : face == DOWN ? BREWING_DOWN
                    : BREWING_SIDES).clone();
            case COMPOSTER_INPUT -> face == UP ? SINGLE_SLOT.clone() : EMPTY_SLOTS;
            case COMPOSTER_OUTPUT -> face == DOWN ? SINGLE_SLOT.clone() : EMPTY_SLOTS;
            case COMPOSTER_EMPTY -> EMPTY_SLOTS;
            default -> flatSlots(size);
        };
    }

    public static int[] flatSlots(int size) {
        int[] slots = new int[Math.max(0, size)];
        for (int i = 0; i < slots.length; i++) slots[i] = i;
        return slots;
    }

    /** {@code WorldlyContainer.canPlaceItemThroughFace} for the worldly kinds. */
    public static boolean canPlaceItemThroughFace(ContainerKind kind, boolean composterChanged,
            boolean canPlaceItem, HopperStack stack, int face) {
        return switch (kind) {
            case FURNACE, BREWING_STAND -> canPlaceItem;
            case SHULKER_BOX -> !Blocks.isShulkerBox(Short.toUnsignedInt(stack.itemType()));
            case COMPOSTER_INPUT -> !composterChanged && face == UP
                    && com.gameexpert.engine.ComposterRules.isCompostable(stack.itemType());
            case COMPOSTER_OUTPUT, COMPOSTER_EMPTY -> false;
            default -> true;
        };
    }

    /** {@code WorldlyContainer.canTakeItemThroughFace} for the worldly kinds. */
    public static boolean canTakeItemThroughFace(ContainerKind kind, boolean composterChanged,
            int slot, HopperStack stack, int face) {
        return switch (kind) {
            case FURNACE -> face != DOWN || slot != 1
                    || stack.itemType() == PlayerInventory.WATER_BUCKET
                    || stack.itemType() == PlayerInventory.BUCKET;
            case BREWING_STAND -> slot != 3 || stack.itemType() == PlayerInventory.GLASS_BOTTLE;
            case COMPOSTER_INPUT, COMPOSTER_EMPTY -> false;
            case COMPOSTER_OUTPUT -> !composterChanged && face == DOWN
                    && stack.itemType() == PlayerInventory.BONE_MEAL;
            default -> true;
        };
    }

    /**
     * {@code AbstractFurnaceBlockEntity.canPlaceItem}: never the result slot; the fuel slot takes
     * a {@code COOKING_FUEL} stack, or a bucket while the fuel slot does not already hold one;
     * the input slot accepts any stack.
     */
    public static boolean furnaceCanPlaceItem(int slot, HopperStack stack, HopperStack fuelSlot) {
        if (slot == 2) return false;
        if (slot == 1) {
            return com.gameexpert.engine.FurnaceRules.fuelTicks(stack.itemType()) > 0
                    || stack.itemType() == PlayerInventory.BUCKET
                            && fuelSlot.itemType() != PlayerInventory.BUCKET;
        }
        return true;
    }

    /**
     * {@code BrewingStandBlockEntity.canPlaceItem} in vanilla slot numbering (0..2 bottles,
     * 3 ingredient, 4 fuel): fuel takes {@code BREWING_FUEL}, the ingredient takes a brewing
     * reagent, a bottle slot takes a brewing input only while empty.
     */
    public static boolean brewingCanPlaceItem(int vanillaSlot, HopperStack stack,
            HopperStack current) {
        short type = stack.itemType();
        if (vanillaSlot == 4) return com.gameexpert.engine.inventory.BrewingRules.isFuel(type);
        if (vanillaSlot == 3) {
            return com.gameexpert.engine.inventory.BrewingRules.isIngredient(type);
        }
        return com.gameexpert.engine.inventory.BrewingRules.isBottle(type) && current.isEmpty();
    }

    /** Vanilla brewing slot (0..2 bottles, 3 ingredient, 4 fuel) to the repository layout. */
    public static int brewingStorageSlot(int vanillaSlot) {
        return switch (vanillaSlot) {
            case 0 -> 2;
            case 1 -> 3;
            case 2 -> 4;
            case 3 -> 1;
            case 4 -> 0;
            default -> throw new IllegalArgumentException("brewing slot " + vanillaSlot);
        };
    }

    // ── comparator ───────────────────────────────────────────────────────

    /**
     * {@code AbstractContainerMenu.getRedstoneSignalFromContainer}: the float mean of
     * {@code count / container.getMaxStackSize(stack)} passed through
     * {@code Mth.lerpDiscrete(f, 0, 15)}. A null container is 0.
     */
    public static int redstoneSignalFromContainer(HopperStack[] slots, int containerMaxStack) {
        if (slots == null || slots.length == 0) return 0;
        float f = 0.0F;
        for (HopperStack stack : slots) {
            if (stack == null || stack.isEmpty()) continue;
            int max = Math.min(containerMaxStack, stack.maxStackSize());
            f += (float) stack.count() / (float) max;
        }
        f /= (float) slots.length;
        return lerpDiscrete(f, 0, 15);
    }

    /** {@code Mth.lerpDiscrete}. */
    public static int lerpDiscrete(float value, int min, int max) {
        int range = max - min;
        return min + floor(value * (float) (range - 1)) + (value > 0.0F ? 1 : 0);
    }

    /** {@code Mth.floor(float)}. */
    public static int floor(float value) {
        int truncated = (int) value;
        return value < (float) truncated ? truncated - 1 : truncated;
    }

    /** {@code CakeBlock.getOutputSignal(bites) = (7 - bites) * 2}. */
    public static int cakeSignal(int bites) {
        return (7 - bites) * 2;
    }

    /**
     * {@code LecternBlock.getAnalogOutputSignal}: 0 without a book, otherwise
     * {@code LecternBlockEntity.getRedstoneSignal()}.
     */
    public static int lecternSignal(boolean hasBook, int page, int pageCount) {
        if (!hasBook) return 0;
        float f = pageCount > 1 ? (float) page / ((float) pageCount - 1.0F) : 1.0F;
        return floor(f * 14.0F) + 1;
    }

    /** {@code CopperGolemStatueBlock.getAnalogOutputSignal}: {@code POSE.ordinal() + 1}. */
    public static int copperGolemStatueSignal(int pose) {
        return (pose & 3) + 1;
    }

    /**
     * Cauldron family: {@code AbstractCauldronBlock} (empty) keeps the default 0,
     * {@code LayeredCauldronBlock} (water, powder snow) returns LEVEL, {@code LavaCauldronBlock} 3.
     */
    public static int cauldronSignal(int kind, int level) {
        return switch (kind) {
            case com.gameexpert.engine.inventory.CauldronRules.LAVA -> 3;
            case com.gameexpert.engine.inventory.CauldronRules.WATER,
                 com.gameexpert.engine.inventory.CauldronRules.POWDER_SNOW -> level;
            default -> 0;
        };
    }
}
