package com.gameexpert.engine.structure;

import java.util.List;

import com.gameexpert.engine.SurfaceDecorator;
import com.gameexpert.terrain.Blocks;

/**
 * Overlay-only generator contract. Surface implementations place into existing AIR/water only.
 * UNDERGROUND implementations MAY excavate: carve walkable AIR rooms/corridors into surrounding stone
 * via runtime overlay (setOverlay AIR) and build walls. All remain golden-neutral (generateChunk is
 * never touched, SHA unchanged) and must respect player diffs (never override an existing player edit).
 */
public interface StructureOverlayGenerator {
    List<RuinGenerator.Voxel> plan(StructureSiteDescriptor site, SurfaceDecorator.BlockView world);

    /**
     * Terrain-independent inclusive envelope of every X/Z cell this site may write. Callers may
     * reject a chunk which does not intersect it, but must not use it to accept or clip a plan.
     */
    default StructureAabb possibleHorizontalBounds(StructureSiteDescriptor site) {
        int reach = site.kind().maxReach();
        return new StructureAabb(site.anchorX() - reach, Blocks.MIN_Y, site.anchorZ() - reach,
                site.anchorX() + reach, Blocks.MAX_Y - 1, site.anchorZ() + reach);
    }

    /**
     * Terrain-free rejection hook for sparse layouts. Returning true may be a conservative false
     * positive, but returning false must prove that this chunk cannot receive a write.
     */
    default boolean possibleHorizontalWriteIntersects(StructureSiteDescriptor site,
            StructureAabb chunk) {
        return possibleHorizontalBounds(site).intersects(chunk);
    }
}
