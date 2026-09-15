package com.gameexpert.engine.persistence.tick;

import com.gameexpert.falling.dto.FallingSpeleothemState;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.feature.Mc263ExactStateCodec;
import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Typed projection of immutable before/after cells; the v1 publication bytes stay unchanged. */
public final class SpeleothemFallIntents {
    private SpeleothemFallIntents() {}

    public static boolean isSpeleothem(int blockId) {
        return blockId == Blocks.POINTED_DRIPSTONE || blockId == Blocks.SULFUR_SPIKE;
    }

    public static List<FallingSpeleothemState> from(
            FinalCarrierTickScheduler.ScheduledTick tick,
            FinalCarrierTickScheduler.TickMutation mutation) {
        if (tick.lane() != FinalCarrierTickScheduler.Lane.BLOCK
                || !isSpeleothem(tick.expectedBlockId())) return List.of();
        List<FinalCarrierTickScheduler.BlockMutation> cells = new ArrayList<>(mutation.blocks());
        if (cells.isEmpty()) return List.of();
        // TickMutation canonicalizes cells by coordinates, while vanilla spawns the falling column
        // from the scheduled block downward. Re-establish that encounter order explicitly so Java
        // and standalone derive the same durable entity order from the same immutable publication.
        cells.sort(Comparator.comparingInt(FinalCarrierTickScheduler.BlockMutation::y).reversed());

        // The same scheduled speleothem lane also handles upward stalagmites. A supported upward
        // tick is empty; an unsupported one is a single fluid-preserving removal and must not emit
        // a falling entity. Validate that removal rather than silently treating arbitrary cells as
        // a non-fall publication.
        if (cells.size() == 1) {
            var cell = cells.get(0);
            if (cell.x() == tick.x() && cell.y() == tick.y() && cell.z() == tick.z()
                    && isSpeleothem(cell.beforeBlockId())) {
                String exact = exactState(cell);
                if (exact.contains("vertical_direction=up")) {
                    validateReplacement(cell, exact.contains("waterlogged=true"));
                    return List.of();
                }
            }
        }

        String identity = FinalCarrierTickPublicationCodec.publicationKey(tick,
                FinalCarrierTickScheduler.DueDisposition.EXECUTE);
        List<FallingSpeleothemState> result = new ArrayList<>(cells.size());
        int expectedY = tick.y();
        for (int index = 0; index < cells.size(); index++) {
            var cell = cells.get(index);
            if (cell.x() != tick.x() || cell.z() != tick.z() || cell.y() != expectedY
                    || !isSpeleothem(cell.beforeBlockId())) {
                throw new IllegalArgumentException("invalid falling speleothem publication");
            }
            String exact = exactState(cell);
            if (!exact.contains("vertical_direction=down")) {
                throw new IllegalArgumentException("invalid falling speleothem publication");
            }
            boolean wet = exact.contains("waterlogged=true");
            validateReplacement(cell, wet);
            boolean tip = exact.contains("thickness=tip,") || exact.contains("thickness=tip_merge,");
            // spawnFallingStalactite returns immediately after the first damaging tip. A publication
            // that contains cells below it cannot correspond to the pinned encounter order.
            if (tip && index != cells.size() - 1) {
                throw new IllegalArgumentException("invalid falling speleothem publication");
            }
            int dryCode = Mc263ExactStateCodec.stateCode(Mc263FeatureBlockState.fromExact(
                    exact.replace("waterlogged=true", "waterlogged=false")));
            result.add(new FallingSpeleothemState(identity + ":" + cell.y(),
                    cell.beforeBlockId(), dryCode, cell.x() + 0.5, cell.y(), cell.z() + 0.5,
                    0, 0, 0, tip ? Math.max(6, 1 + tick.y() - cell.y()) : 0, false));
            expectedY--;
        }
        return List.copyOf(result);
    }

    private static String exactState(FinalCarrierTickScheduler.BlockMutation cell) {
        try {
            return Mc263ExactStateCodec.decode(cell.beforeBlockId(), cell.beforeBlockState())
                    .exactState();
        } catch (IllegalArgumentException invalidState) {
            throw new IllegalArgumentException("invalid speleothem publication", invalidState);
        }
    }

    private static void validateReplacement(
            FinalCarrierTickScheduler.BlockMutation cell, boolean wet) {
        Mc263FeatureBlockState expected = Mc263FeatureBlockState.fromExact(
                wet ? "minecraft:water" : "minecraft:air");
        int expectedState = Mc263ExactStateCodec.stateCode(expected);
        if (cell.blockId() != expected.blockId() || cell.blockState() != expectedState) {
            throw new IllegalArgumentException("invalid speleothem fluid-preserving replacement");
        }
    }
}
