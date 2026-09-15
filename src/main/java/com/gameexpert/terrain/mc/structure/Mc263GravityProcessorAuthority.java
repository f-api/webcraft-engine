package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Cell;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.CellProcessor;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Piece;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.PlacementWorld;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.ProcessedCell;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Vec;

import java.util.Objects;

/**
 * Minecraft Java 26.3-snapshot-7 {@code GravityProcessor} as a neutral
 * {@link Mc263TemplatePlacementExecutor.CellProcessor}.
 *
 * <p>Bytecode authority is the pinned inner jar class
 * {@code net/minecraft/world/level/levelgen/structure/templatesystem/GravityProcessor.class}
 * (server SHA-1 {@code 06157fedd67ff4dd6e0e6fa4a9dd0af296f0dd61}). Its
 * {@code processBlock(LevelReader, BlockPos offset, BlockPos pos, BlockPos relativePos,
 * StructureBlockInfo blockInfo, StructurePlaceSettings)} body is exactly:</p>
 * <ol>
 *   <li>Resolve the queried heightmap: on a {@code ServerLevel},
 *       {@code WORLD_SURFACE_WG -> WORLD_SURFACE} and {@code OCEAN_FLOOR_WG -> OCEAN_FLOOR};
 *       any other configured value, and any non-server reader, queries the configured value
 *       unchanged.</li>
 *   <li>{@code i = level.getHeight(resolved, blockInfo.pos().getX(), blockInfo.pos().getZ())
 *       + this.offset} — exactly one heightmap query per reached cell, at the <em>already
 *       processed</em> world column, never the template-local column.</li>
 *   <li>{@code j = relativePos.getY()} — the raw template-local Y of the source cell, which
 *       {@code StructureTemplate.processBlockInfos} passes as the fourth argument.</li>
 *   <li>Emit {@code new BlockPos(blockInfo.pos().getX(), i + j, blockInfo.pos().getZ())} with the
 *       incoming state and NBT untouched; X and Z never move and the cell is never dropped.</li>
 * </ol>
 *
 * <p>{@code StructureTemplatePool$Projection.TERRAIN_MATCHING} is constructed with the single
 * processor {@code new GravityProcessor(Heightmap.Types.WORLD_SURFACE_WG, -1)}, and
 * {@code SinglePoolElement.getSettings} appends the projection's processors <em>last</em>, after
 * {@code BlockIgnoreProcessor.STRUCTURE_BLOCK}, {@code JigsawReplacementProcessor.INSTANCE} and the
 * element's own processor list. {@code RIGID} contributes no processors, so this authority is a
 * no-op for every rigid piece and must stay installed unconditionally in a shared chain.</p>
 *
 * <p>The transcript row this produces is {@code getHeight|<configured heightmap>|<x>|<z>-><height>}
 * with the <em>configured</em> {@code WORLD_SURFACE_WG} name, which is what the authenticated
 * Village settlement corpus records; the server-side {@code WORLD_SURFACE} resolution is a query
 * detail of the level, not of the processor's identity, and is exposed here as
 * {@link #resolveServerHeightmap(String)}.</p>
 */
public final class Mc263GravityProcessorAuthority implements CellProcessor {
    /** {@code StructureTemplatePool.Projection.TERRAIN_MATCHING} identifier. */
    public static final String TERRAIN_MATCHING = "terrain_matching";
    /** {@code StructureTemplatePool.Projection.RIGID} identifier. */
    public static final String RIGID = "rigid";
    /** Heightmap the {@code TERRAIN_MATCHING} projection configures. */
    public static final String WORLD_SURFACE_WG = "WORLD_SURFACE_WG";
    /** {@code ServerLevel} substitution for {@link #WORLD_SURFACE_WG}. */
    public static final String WORLD_SURFACE = "WORLD_SURFACE";
    public static final String OCEAN_FLOOR_WG = "OCEAN_FLOOR_WG";
    public static final String OCEAN_FLOOR = "OCEAN_FLOOR";
    /** Y offset the {@code TERRAIN_MATCHING} projection configures. */
    public static final int TERRAIN_MATCHING_OFFSET = -1;

    private static final Mc263GravityProcessorAuthority TERRAIN_MATCHING_PROCESSOR =
            new Mc263GravityProcessorAuthority(WORLD_SURFACE_WG, TERRAIN_MATCHING_OFFSET);

    private final String heightmap;
    private final int offset;

    public Mc263GravityProcessorAuthority(String heightmap, int offset) {
        this.heightmap = Objects.requireNonNull(heightmap, "gravity heightmap");
        if (heightmap.isBlank()) throw new IllegalArgumentException("blank gravity heightmap");
        this.offset = offset;
    }

    /** The exact processor {@code Projection.TERRAIN_MATCHING} installs. */
    public static Mc263GravityProcessorAuthority terrainMatching() {
        return TERRAIN_MATCHING_PROCESSOR;
    }

    public String heightmap() { return heightmap; }

    public int offset() { return offset; }

    /** {@code ServerLevel} heightmap substitution performed inside {@code processBlock}. */
    public static String resolveServerHeightmap(String configured) {
        Objects.requireNonNull(configured, "configured heightmap");
        if (WORLD_SURFACE_WG.equals(configured)) return WORLD_SURFACE;
        if (OCEAN_FLOOR_WG.equals(configured)) return OCEAN_FLOOR;
        return configured;
    }

    /**
     * Runs the projection-gated gravity step. A {@code rigid} piece keeps its incoming cell
     * untouched and issues no world query, exactly as the empty {@code RIGID} processor list does.
     */
    @Override
    public ProcessedCell process(Piece piece, Cell cell, ProcessedCell input, PlacementWorld world) {
        Objects.requireNonNull(piece, "gravity piece");
        Objects.requireNonNull(cell, "gravity cell");
        Objects.requireNonNull(input, "gravity input cell");
        Objects.requireNonNull(world, "gravity placement world");
        return processRelative(piece.projection(), cell.localY(), input, world);
    }

    /**
     * Same {@code processBlock} body addressed by its two real inputs: the piece's projection and
     * the source cell's raw template-local Y, which is {@code blockInfo.pos().getY()} in the
     * pinned bytecode. A cell-granular caller whose {@link Cell} carries collapsed local
     * coordinates supplies the template-local Y directly instead of through the {@link Cell}.
     */
    public ProcessedCell processRelative(String projection, int templateRelativeY,
            ProcessedCell input, PlacementWorld world) {
        Objects.requireNonNull(projection, "gravity projection");
        Objects.requireNonNull(input, "gravity input cell");
        Objects.requireNonNull(world, "gravity placement world");
        if (!TERRAIN_MATCHING.equals(projection)) {
            if (!RIGID.equals(projection)) {
                throw new IllegalArgumentException(
                        "unauthenticated template pool projection: " + projection);
            }
            return input;
        }
        Vec position = input.position();
        int height = world.getHeight(heightmap, position.x(), position.z());
        int y = Math.addExact(Math.addExact(height, offset), templateRelativeY);
        return new ProcessedCell(new Vec(position.x(), y, position.z()), input.exactState(),
                input.inputNbt());
    }
}
