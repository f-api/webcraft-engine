package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;

/** Shared state contract for the wooden Poplar redstone controls. */
public final class PoplarControlRules {
    public static final int UNPOWERED = 0;
    public static final int POWERED = 1;
    public static final int BUTTON_PRESS_MC_TICKS = 30;
    public static final int BUTTON_PRESS_SERVER_TICKS = BUTTON_PRESS_MC_TICKS / 2;

    private PoplarControlRules() {}

    /**
     * [CONTAINER-MENUS] Every button block of the repository: the poplar button and the generated
     * stone / oak buttons (strongholds, jungle temples). All share bit 0 as POWERED.
     */
    public static boolean isButton(int blockType) {
        return blockType == Blocks.POPLAR_BUTTON || blockType == Blocks.STONE_BUTTON
                || blockType == Blocks.OAK_BUTTON;
    }

    /** [CONTAINER-MENUS] {@code ButtonBlock.ticksToStayPressed} of the stone button. */
    public static final int STONE_BUTTON_PRESS_MC_TICKS = 20;

    /** {@code ButtonBlock.ticksToStayPressed}: stone 20, wooden (oak, poplar) 30 game ticks. */
    public static int pressMcTicks(int blockType) {
        return blockType == Blocks.STONE_BUTTON ? STONE_BUTTON_PRESS_MC_TICKS : BUTTON_PRESS_MC_TICKS;
    }

    public static int pressServerTicks(int blockType) {
        return blockType == Blocks.STONE_BUTTON ? STONE_BUTTON_PRESS_MC_TICKS / 2
                : BUTTON_PRESS_SERVER_TICKS;
    }

    public static boolean isPressurePlate(int blockType) {
        return blockType == Blocks.POPLAR_PRESSURE_PLATE;
    }

    public static boolean powered(int state) {
        return (state & POWERED) != 0;
    }

    public static int buttonState(long nowServerTick, long releaseServerTick) {
        return nowServerTick < releaseServerTick ? POWERED : UNPOWERED;
    }

    public static int pressurePlateState(boolean occupied) {
        return occupied ? POWERED : UNPOWERED;
    }
}
