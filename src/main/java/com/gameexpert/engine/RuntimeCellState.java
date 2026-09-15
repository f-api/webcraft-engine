package com.gameexpert.engine;

import com.gameexpert.authority.versioned.NeutralFinalChunk;
import com.gameexpert.terrain.Blocks;

/** Keeps player compact bytes distinct from selected-producer exact-state codes. */
final class RuntimeCellState {
    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow",
            "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final String[] FACINGS = {"north", "east", "south", "west"};
    private static final String[] SHAPES = {"straight", "inner_left", "inner_right", "outer_left", "outer_right"};
    private static final String[] SLABS = {"bottom", "top", "double"};

    private RuntimeCellState() {}

    static boolean supportsPlayerOverlay(int id) {
        return id >= Blocks.ORANGE_WOOL_STAIRS && id <= Blocks.BLACK_CONCRETE_SLAB
                || id == Blocks.OPEN_EYEBLOSSOM;
    }

    static Cell generated(NeutralFinalChunk.StateOverride state) {
        return new Cell(state.blockId(), state.stateCode(), state.exactState(), state.fluidTypeKey(), false);
    }

    static Cell playerOverlay(int id, int compact) {
        if (!supportsPlayerOverlay(id) || compact < 0 || compact > 255) {
            throw new IllegalArgumentException("unsupported runtime overlay state");
        }
        String exact;
        boolean waterlogged = (compact & BuildingBlockRules.WATERLOGGED) != 0;
        if (id == Blocks.OPEN_EYEBLOSSOM) {
            if (compact != 0) throw new IllegalArgumentException("open eyeblossom has no state properties");
            exact = "minecraft:open_eyeblossom";
        } else if (Blocks.isConcreteSlab(id)) {
            int type = compact & ~BuildingBlockRules.WATERLOGGED;
            if (type >= SLABS.length) throw new IllegalArgumentException("invalid runtime slab state");
            exact = "minecraft:" + COLORS[id - Blocks.WHITE_CONCRETE_SLAB]
                    + "_concrete_slab[type=" + SLABS[type] + ",waterlogged=" + waterlogged + "]";
        } else {
            int shape = (compact & ~BuildingBlockRules.WATERLOGGED) >>> BuildingBlockRules.STAIR_SHAPE_SHIFT;
            if (shape >= SHAPES.length) throw new IllegalArgumentException("invalid runtime stairs state");
            String key = Blocks.isConcreteStairs(id)
                    ? COLORS[id - Blocks.WHITE_CONCRETE_STAIRS] + "_concrete_stairs"
                    : COLORS[id - Blocks.ORANGE_WOOL_STAIRS + 1] + "_wool_stairs";
            exact = "minecraft:" + key + "[facing=" + FACINGS[compact & BuildingBlockRules.FACING_MASK]
                    + ",half=" + ((compact & BuildingBlockRules.STAIR_TOP) == 0 ? "bottom" : "top")
                    + ",shape=" + SHAPES[shape] + ",waterlogged=" + waterlogged + "]";
        }
        return new Cell(id, compact, exact, waterlogged ? "minecraft:water" : "minecraft:empty", true);
    }

    static Cell playerExact(int id, String exact) {
        // The small app-owned compact vocabulary is independent of every producer codebook.
        // Exact equality rejects unknown properties and invalid combinations without aliasing them.
        for (int compact = 0; compact <= 255; compact++) {
            try {
                Cell candidate = playerOverlay(id, compact);
                if (candidate.exactState().equals(exact)) return candidate;
            } catch (IllegalArgumentException invalidCompact) {
                // Reserved compact bytes are not part of this runtime vocabulary.
            }
        }
        throw new IllegalArgumentException("exact state outside runtime overlay vocabulary: " + exact);
    }

    static final class Cell {
        private final int blockId;
        private final int persistedState;
        private final String exactState;
        private final String fluidTypeKey;
        private final boolean runtimeOverlay;

        private Cell(int blockId, int persistedState, String exactState, String fluidTypeKey, boolean runtimeOverlay) {
            this.blockId = blockId;
            this.persistedState = persistedState;
            this.exactState = exactState;
            this.fluidTypeKey = fluidTypeKey;
            this.runtimeOverlay = runtimeOverlay;
        }
        int blockId() { return blockId; }
        int persistedState() { return persistedState; }
        String exactState() { return exactState; }
        String fluidTypeKey() { return fluidTypeKey; }
        String blockKey() { int i = exactState.indexOf('['); return i < 0 ? exactState : exactState.substring(0, i); }
        boolean isRuntimeOverlay() { return runtimeOverlay; }
    }
}
