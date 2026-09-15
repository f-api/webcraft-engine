package com.gameexpert.terrain;

import com.gameexpert.terrain.mc.aquifer.McAquifer;
import com.gameexpert.terrain.mc.df.McDensityFunction;
import com.gameexpert.terrain.mc.df.McDensityFunctionLoader;
import com.gameexpert.terrain.mc.structure.Mc263StructureWorldAccess;
import com.gameexpert.terrain.mc.surface.McSurfaceRuleEngine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Exact {@code net.minecraft.world.level.chunk.ChunkGenerator#getBaseHeight} port for the pinned
 * 26.3-snapshot-7 Overworld, over a long world seed.
 *
 * <p>Vanilla answers a base height by walking one {@code NoiseChunk} column
 * ({@code NoiseBasedChunkGenerator#iterateNoiseColumn}) rather than by probing the density field
 * directly. Three column-only facts make that walk different from
 * {@link ChunkGenerator#surfaceHeight}, and all three are reproduced here:</p>
 * <ol>
 *   <li>The column {@code NoiseChunk} is opened at the <em>cell corner</em>
 *       {@code floorDiv(x, cellWidth) * cellWidth} and each sample is taken with
 *       {@code updateForX(x, floorMod(x, cellWidth) / cellWidth)} and the matching z/y weights, so
 *       the interpolated lane is the ordinary four-corner lattice interpolation while the
 *       un-interpolated lane sees the true block coordinates.</li>
 *   <li>The state at each y comes from the chunk generator's material rule, i.e. the
 *       aquifer-backed decision over the final density — not from the sign of the density. Below
 *       sea level that yields the local aquifer water table, and near it an aquifer pressure
 *       barrier can return stone where the density alone is non-positive.</li>
 *   <li>{@code Beardifier} is {@code BeardifierMarker.INSTANCE} (structure-free, contribution
 *       {@code 0}), and the walk runs top-down, returning {@code y + 1} at the first state the
 *       heightmap type stops on, else the minimum build height.</li>
 * </ol>
 *
 * <p>The ore-vein rule is deliberately not evaluated: it only ever substitutes one solid block for
 * another inside rock, so it cannot move a {@code WORLD_SURFACE_WG} or {@code OCEAN_FLOOR_WG}
 * answer.</p>
 *
 * <p>Stop rules are the pinned {@code Heightmap.Types}: {@code WORLD_SURFACE_WG} stops on
 * {@code NOT_AIR}; {@code OCEAN_FLOOR_WG} stops on {@code MATERIAL_MOTION_BLOCKING}, which at the
 * noise stage means the solid material only (water and lava do not block motion).</p>
 *
 * <p>Not thread safe: the density evaluation cache, the lattice- and base-column caches, the
 * surface engine and the per-chunk aquifer are all mutable per-instance state.</p>
 */
public final class Mc263BaseHeightSampler implements Mc263StructureWorldAccess.BaseHeightSampler {
    /** {@code NoiseSettings#getCellHeight()} for the pinned Overworld noise settings. */
    private static final int CELL_HEIGHT = 8;
    /** {@code NoiseSettings#getCellWidth()} for the pinned Overworld noise settings. */
    private static final int CELL_WIDTH = 4;
    private static final int LATTICE_COLUMN_LIMIT = 4096;
    private static final int BASE_COLUMN_LIMIT = 256;

    private final long worldSeed;
    private final McDensityFunction mainLattice;
    private final McDensityFunction noodle;
    private final McDensityFunction.Context context;
    private final McAquifer.Inputs aquiferInputs;
    private final McSurfaceRuleEngine surfaceRules;
    private final Map<Long, double[]> latticeColumns = new HashMap<>();
    private final Map<Long, McAquifer.Material[]> baseColumns = new HashMap<>();

    private McAquifer aquifer;
    private long aquiferChunk = Long.MIN_VALUE;

    private Mc263BaseHeightSampler(long worldSeed) {
        this.worldSeed = worldSeed;
        try {
            McDensityFunctionLoader loader = new McDensityFunctionLoader(Path.of("."), worldSeed);
            McDensityFunctionLoader.FullDensityGraph graph = loader.fullDensityGraph();
            this.mainLattice = graph.mainLatticeInput();
            this.noodle = graph.noodle();
            this.context = new McDensityFunction.Context(0, 0, 0,
                    new ChunkGenerator.DensityEvaluationCache(graph.cacheSlotCount()));
            this.aquiferInputs = McAquifer.loadInputs(worldSeed, loader.erosion(), loader.depth());
            this.surfaceRules = McSurfaceRuleEngine.load26_3(worldSeed);
        } catch (IOException failure) {
            throw new IllegalStateException("근거 없음: 고정 vanilla density/surface datapack", failure);
        }
    }

    /** The pinned Overworld base-height lane for one world seed. */
    public static Mc263BaseHeightSampler overworld(long worldSeed) {
        return new Mc263BaseHeightSampler(worldSeed);
    }

    public long worldSeed() {
        return worldSeed;
    }

    /** {@code getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ...)}. */
    @Override
    public int worldSurfaceWg(int blockX, int blockZ) {
        return baseHeight(blockX, blockZ, false);
    }

    /** {@code getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ...)}. */
    @Override
    public int oceanFloorWg(int blockX, int blockZ) {
        return baseHeight(blockX, blockZ, true);
    }

    /**
     * The pinned {@code ChunkGenerator#getBaseColumn} column for one column of blocks: the same
     * {@code NoiseChunk} walk {@link #worldSurfaceWg} runs, materialized for every {@code y}
     * instead of stopped at the first hit.
     *
     * <p>{@code getBaseColumn} fills its {@code NoiseColumn} from the same aquifer-backed material
     * rule, so a state there is only the default block, the local aquifer fluid, or air; the
     * {@code Heightmap.Types} predicates the vanilla ruined-portal
     * {@code findSuitableY} lane tests against that column therefore reduce to
     * {@link #opaqueInBaseColumn}'s two material tests, which
     * {@code ruined-portal-column-receipts-v1} authenticates per recorded {@code y}.</p>
     *
     * @return one entry per {@code y}, index {@code y - Blocks.MIN_Y}
     */
    public McAquifer.Material[] baseColumn(int blockX, int blockZ) {
        long key = (((long) blockX & 0xFFFFFFFFL) << 32) | ((long) blockZ & 0xFFFFFFFFL);
        McAquifer.Material[] cached = baseColumns.get(key);
        if (cached != null) return cached;
        int cornerX = cellCorner(blockX);
        int cornerZ = cellCorner(blockZ);
        double[][] cell = {
            latticeColumn(cornerX, cornerZ),
            latticeColumn(cornerX + CELL_WIDTH, cornerZ),
            latticeColumn(cornerX, cornerZ + CELL_WIDTH),
            latticeColumn(cornerX + CELL_WIDTH, cornerZ + CELL_WIDTH),
        };
        McAquifer columnAquifer = aquiferFor(blockX, blockZ);
        McAquifer.Material[] column = new McAquifer.Material[Blocks.MAX_Y + 1 - Blocks.MIN_Y];
        for (int y = Blocks.MAX_Y; y >= Blocks.MIN_Y; y--) {
            column[y - Blocks.MIN_Y] = columnAquifer
                    .substance(blockX, y, blockZ, density(cell, blockX, y, blockZ))
                    .material();
        }
        if (baseColumns.size() >= BASE_COLUMN_LIMIT) baseColumns.clear();
        baseColumns.put(key, column);
        return column;
    }

    /**
     * The exact {@code Heightmap.Types#isOpaque} test the vanilla ruined-portal
     * {@code findSuitableY} lane applies to a {@code getBaseColumn} state:
     * {@code WORLD_SURFACE_WG} stops on {@code NOT_AIR}, {@code OCEAN_FLOOR_WG} on the
     * {@code #minecraft:blocks_motion_in_heightmap} tag, which at the noise stage contains the
     * solid material only.
     */
    public boolean opaqueInBaseColumn(int blockX, int y, int blockZ, boolean motionBlockingOnly) {
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return false;
        McAquifer.Material material = baseColumn(blockX, blockZ)[y - Blocks.MIN_Y];
        return motionBlockingOnly ? material == McAquifer.Material.SOLID
                : material != McAquifer.Material.AIR;
    }

    private int baseHeight(int blockX, int blockZ, boolean motionBlockingOnly) {
        int cornerX = cellCorner(blockX);
        int cornerZ = cellCorner(blockZ);
        double[][] cell = {
            latticeColumn(cornerX, cornerZ),
            latticeColumn(cornerX + CELL_WIDTH, cornerZ),
            latticeColumn(cornerX, cornerZ + CELL_WIDTH),
            latticeColumn(cornerX + CELL_WIDTH, cornerZ + CELL_WIDTH),
        };
        McAquifer columnAquifer = aquiferFor(blockX, blockZ);
        for (int y = Blocks.MAX_Y; y >= Blocks.MIN_Y; y--) {
            McAquifer.Material material = columnAquifer
                    .substance(blockX, y, blockZ, density(cell, blockX, y, blockZ))
                    .material();
            if (material == McAquifer.Material.AIR) continue;
            if (motionBlockingOnly && material != McAquifer.Material.SOLID) continue;
            return y + 1;
        }
        return Blocks.MIN_Y;
    }

    /**
     * The column {@code final_density}: the cell's four lattice columns interpolated at the true
     * in-cell weights and squeezed, min'd with the noodle lane at the true block coordinates, with
     * a zero (marker) Beardifier.
     */
    private double density(double[][] cell, int blockX, int y, int blockZ) {
        int cellIndex = Math.floorDiv(y - Blocks.MIN_Y, CELL_HEIGHT);
        int nextIndex = Math.min(cellIndex + 1, cell[0].length - 1);
        double inCellX = Math.floorMod(blockX, CELL_WIDTH) / (double) CELL_WIDTH;
        double inCellZ = Math.floorMod(blockZ, CELL_WIDTH) / (double) CELL_WIDTH;
        double inCellY = Math.floorMod(y, CELL_HEIGHT) / (double) CELL_HEIGHT;
        double main = ChunkGenerator.assembleDensityFromCorners(inCellX, inCellY, inCellZ,
                cell[0][cellIndex], cell[1][cellIndex], cell[0][nextIndex], cell[1][nextIndex],
                cell[2][cellIndex], cell[3][cellIndex], cell[2][nextIndex], cell[3][nextIndex]);
        context.set(blockX, y, blockZ);
        return ChunkGenerator.assembleFinalDensity(
                (float) main, (float) noodle.compute(context), 0.0F);
    }

    private static int cellCorner(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, CELL_WIDTH) * CELL_WIDTH;
    }

    private double[] latticeColumn(int cornerX, int cornerZ) {
        long key = (((long) cornerX & 0xFFFFFFFFL) << 32) | ((long) cornerZ & 0xFFFFFFFFL);
        double[] cached = latticeColumns.get(key);
        if (cached != null) return cached;
        int count = (Blocks.MAX_Y + 1 - Blocks.MIN_Y) / CELL_HEIGHT + 1;
        double[] samples = new double[count];
        for (int index = 0; index < count; index++) {
            samples[index] = mainLattice.compute(
                    cornerX, ChunkGenerator.densityLatticeSampleY(index), cornerZ);
        }
        if (latticeColumns.size() >= LATTICE_COLUMN_LIMIT) latticeColumns.clear();
        latticeColumns.put(key, samples);
        return samples;
    }

    /**
     * The column {@code NoiseChunk}'s aquifer covers the chunk containing the column, exactly as
     * {@code NoiseChunk}'s {@code ChunkPos} does; every aquifer site is an absolute-grid function,
     * so the per-chunk cache extent never changes a decision.
     */
    private McAquifer aquiferFor(int blockX, int blockZ) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        long key = (((long) chunkX & 0xFFFFFFFFL) << 32) | ((long) chunkZ & 0xFFFFFFFFL);
        if (aquifer == null || aquiferChunk != key) {
            aquifer = new McAquifer(aquiferInputs, chunkX << 4, chunkZ << 4,
                    surfaceRules::preliminarySurfaceLevel);
            aquiferChunk = key;
        }
        return aquifer;
    }
}
