package com.gameexpert.engine.dispenser;

import com.gameexpert.terrain.Blocks;
import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;

/**
 * Pure rules of the pinned 26.3-snapshot-7 {@code DispenserBlock} / {@code DropperBlock} and
 * {@code DispenserBlockEntity} (javap). The standalone twin is {@code StandaloneDispenserRules.ts};
 * both must return identical values.
 *
 * <p>State byte: bits 0..2 are {@code FACING} in the repository's six-way facing code
 * (0 north, 1 east, 2 south, 3 west, 4 up, 5 down — the {@code Mc263FinalChunkCodec.FACING_CODES}
 * order the generated dispensers already use) and bit 3 is {@code TRIGGERED}.
 */
public final class DispenserRules {

    /** {@code DispenserBlockEntity.CONTAINER_SIZE}. */
    public static final int SLOTS = 9;
    public static final int FACING_MASK = 0x07;
    public static final int NORTH = 0;
    public static final int EAST = 1;
    public static final int SOUTH = 2;
    public static final int WEST = 3;
    public static final int UP = 4;
    public static final int DOWN = 5;
    /** {@code DispenserBlock.TRIGGERED}. */
    public static final int TRIGGERED = 0x08;
    /** {@code DispenserBlock.TRIGGER_DURATION}: the scheduled tick delay in game ticks. */
    public static final int TRIGGER_DURATION = 4;
    /** {@code DefaultDispenseItemBehavior.DEFAULT_ACCURACY}. */
    public static final int DEFAULT_ACCURACY = 6;
    /** {@code DispenserBlock.getDispensePosition(source)}: 0.7 blocks out of the centre. */
    public static final double DISPENSE_DISTANCE = 0.7;

    private static final int[] STEP_X = { 0, 1, 0, -1, 0, 0 };
    private static final int[] STEP_Y = { 0, 0, 0, 0, 1, -1 };
    private static final int[] STEP_Z = { -1, 0, 1, 0, 0, 0 };

    private DispenserRules() {
    }

    /** Dispenser and dropper share {@code DispenserBlock} state, menu and block entity. */
    public static boolean isDispenserFamily(int block) {
        return block == Blocks.DISPENSER || block == Blocks.DROPPER;
    }

    public static int normalizeState(int state) {
        int facing = state & FACING_MASK;
        return (facing <= DOWN ? facing : NORTH) | state & TRIGGERED;
    }

    public static int facing(int state) {
        int facing = state & FACING_MASK;
        return facing <= DOWN ? facing : NORTH;
    }

    public static boolean triggered(int state) {
        return (state & TRIGGERED) != 0;
    }

    public static int withTriggered(int state, boolean triggered) {
        return triggered ? normalizeState(state) | TRIGGERED : normalizeState(state) & ~TRIGGERED;
    }

    /**
     * {@code DispenserBlock#getStateForPlacement}: {@code FACING =
     * getNearestLookingDirection().getOpposite()}, {@code TRIGGERED=false}. The client computes the
     * facing from its look vector; only the facing survives.
     */
    public static int placementState(int requested) {
        return facing(requested);
    }

    public static int stepX(int facing) {
        return STEP_X[facing];
    }

    public static int stepY(int facing) {
        return STEP_Y[facing];
    }

    public static int stepZ(int facing) {
        return STEP_Z[facing];
    }

    /** {@code Direction#getOpposite} in this code. */
    public static int opposite(int facing) {
        return switch (facing) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
            case UP -> DOWN;
            default -> UP;
        };
    }

    /**
     * {@code DispenserBlockEntity#getRandomSlot}: reservoir sampling over the non-empty slots,
     * {@code random.nextInt(j++) == 0} with {@code j} starting at 1. Returns -1 when empty.
     */
    public static int randomSlot(boolean[] occupied, IntUnaryOperator nextInt) {
        int chosen = -1;
        int seen = 1;
        for (int slot = 0; slot < occupied.length; slot++) {
            if (!occupied[slot]) continue;
            if (nextInt.applyAsInt(seen++) == 0) chosen = slot;
        }
        return chosen;
    }

    /** {@code DispenserBlock.getDispensePosition(source, distance, Vec3.ZERO)}. */
    public static double[] dispensePosition(int x, int y, int z, int facing, double distance) {
        return new double[] {
            x + 0.5 + distance * STEP_X[facing],
            y + 0.5 + distance * STEP_Y[facing],
            z + 0.5 + distance * STEP_Z[facing],
        };
    }

    /**
     * {@code DefaultDispenseItemBehavior.spawnItem}: position and motion of the single dispensed
     * item entity. {@code y} drops by 0.125 on the vertical axis and 0.15625 otherwise; the
     * speed {@code nextDouble() * 0.1 + 0.2} is drawn first, then {@code triangle} for x, y (mode
     * 0.2) and z with deviation {@code 0.0172275 * accuracy} ({@code triangle(m, d) = m + d *
     * (nextDouble() - nextDouble())}). Returns {x, y, z, vx, vy, vz} in blocks and blocks/game tick.
     */
    public static double[] spawnItem(double[] position, int facing, int accuracy,
            DoubleSupplier nextDouble) {
        double y = position[1] - (STEP_Y[facing] != 0 ? 0.125 : 0.15625);
        double speed = nextDouble.getAsDouble() * 0.1 + 0.2;
        double deviation = 0.0172275 * accuracy;
        double vx = triangle(STEP_X[facing] * speed, deviation, nextDouble);
        double vy = triangle(0.2, deviation, nextDouble);
        double vz = triangle(STEP_Z[facing] * speed, deviation, nextDouble);
        return new double[] { position[0], y, position[2], vx, vy, vz };
    }

    /** {@code RandomSource#triangle(double, double)}. */
    public static double triangle(double mode, double deviation, DoubleSupplier nextDouble) {
        return mode + deviation * (nextDouble.getAsDouble() - nextDouble.getAsDouble());
    }

    /**
     * {@code DispenserBlock#neighborChanged}: {@code hasNeighborSignal(pos) ||
     * hasNeighborSignal(pos.above())} decides {@code powered}; a rising edge on an untriggered
     * block schedules the dispense tick and sets TRIGGERED, a falling edge clears TRIGGERED.
     */
    public static Edge edge(int state, boolean powered) {
        boolean triggered = triggered(state);
        if (powered && !triggered) return Edge.TRIGGER;
        if (!powered && triggered) return Edge.RELEASE;
        return Edge.NONE;
    }

    public enum Edge { NONE, TRIGGER, RELEASE }
}
