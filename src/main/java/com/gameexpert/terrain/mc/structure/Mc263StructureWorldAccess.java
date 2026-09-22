package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.Mc263BaseHeightSampler;
import com.gameexpert.terrain.mc.LegacyRand;
import com.gameexpert.terrain.mc.biome.McBiomeRegistry;
import com.gameexpert.terrain.mc.biome.McClimateSampler;
import com.gameexpert.terrain.mc.feature.Mc263FeatureIndexReceipt;
import com.gameexpert.terrain.mc.feature.Mc263StructureIndexReceipt;
import com.gameexpert.terrain.mc.structure.Mc263StructureSetStartPlanner.BlockPos;
import com.gameexpert.terrain.mc.structure.Mc263StructureSetStartPlanner.PlanningState;
import com.gameexpert.terrain.mc.surface.McFreezeTopLayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Production {@link Mc263StructureSetStartPlanner.WorldAccess} over the repository's real
 * Overworld samplers.
 *
 * <p>Biome candidacy comes from {@link McClimateSampler} (the canonical 26.3 climate sampler) and
 * the authenticated per-structure biome masks in {@link Mc263StructureIndexReceipt}. Nothing here
 * reads a recorded receipt table: the {@code world-sampler-receipts-v1} corpus is the verification
 * oracle in the bound tests only. The base-height lane is described in {@link BaseHeightSampler}.
 *
 * <p>{@link #opaqueInBaseColumn} and {@link #coldEnoughToSnow} are the two extra lanes the pinned
 * Ruined Portal family consults ({@code RuinedPortalStructure#findSuitableY} and
 * {@code #isCold}); both are bound to the authenticated {@code ruined-portal-column-receipts-v1}
 * corpus by {@code Mc263RuinedPortalColumnReceiptTest}.
 *
 * <p>Not thread safe: the climate sampler keeps a mutable biome-table search hint.
 */
public final class Mc263StructureWorldAccess implements Mc263StructureSetStartPlanner.WorldAccess {
    /** The only preferred-biome tag any pinned 26.3 structure set references. */
    public static final String STRONGHOLD_BIASED_TO_TAG = "#minecraft:stronghold_biased_to";

    /**
     * Exact values of {@code data/minecraft/tags/worldgen/biome/stronghold_biased_to.json} in the
     * pinned 26.3 registry evidence (sha256
     * {@code dc3aa440f6c6e2a2766be3df4770f4cc80c01f351adb8718720866d0786f0b10}).
     */
    public static final List<String> STRONGHOLD_BIASED_TO = List.of(
            "minecraft:plains", "minecraft:sunflower_plains", "minecraft:snowy_plains",
            "minecraft:ice_spikes", "minecraft:desert", "minecraft:forest",
            "minecraft:flower_forest", "minecraft:birch_forest", "minecraft:dark_forest",
            "minecraft:pale_garden", "minecraft:old_growth_birch_forest",
            "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga",
            "minecraft:taiga", "minecraft:snowy_taiga", "minecraft:savanna",
            "minecraft:savanna_plateau", "minecraft:windswept_hills",
            "minecraft:windswept_gravelly_hills", "minecraft:windswept_forest",
            "minecraft:windswept_savanna", "minecraft:jungle", "minecraft:sparse_jungle",
            "minecraft:bamboo_jungle", "minecraft:badlands", "minecraft:eroded_badlands",
            "minecraft:wooded_badlands", "minecraft:meadow", "minecraft:cherry_grove",
            "minecraft:grove", "minecraft:snowy_slopes", "minecraft:frozen_peaks",
            "minecraft:jagged_peaks", "minecraft:stony_peaks", "minecraft:mushroom_fields",
            "minecraft:dripstone_caves", "minecraft:lush_caves", "minecraft:sulfur_caves");

    /** {@code Biome#warmEnoughToRain} threshold: below it the position is cold enough to snow. */
    private static final float COLD_ENOUGH_TO_SNOW_TEMPERATURE = 0.15F;

    private static final Set<String> STRONGHOLD_BIASED_TO_SET = Set.copyOf(STRONGHOLD_BIASED_TO);
    private static final int BIOME_QUART_MIN_Y = Blocks.MIN_Y >> 2;
    private static final int BIOME_QUART_HEIGHT = (Blocks.MAX_Y - Blocks.MIN_Y + 1) >> 2;
    private static final int BIOME_QUART_MAX_Y = BIOME_QUART_MIN_Y + BIOME_QUART_HEIGHT - 1;
    private static final int BIOME_GRID_CELLS = 16 * BIOME_QUART_HEIGHT;
    /**
     * The same tag membership, resolved once against the pinned biome registry and indexed by
     * biome ID.
     *
     * <p>AGENTS rule 10l/10m: the ring builder asks this question once per quart of a 57x57
     * square for each of the pinned ring positions, and the answer is a property of the biome ID
     * the climate sampler already produced. Deriving the table from {@link McBiomeRegistry#ids()}
     * — never from a hand-written ID list beside it — makes the per-quart question an array read
     * instead of an ID-to-name lookup plus a string hash, with exactly the same membership.</p>
     */
    private static final boolean[] STRONGHOLD_BIASED_TO_BY_ID = strongholdBiasedToById();

    private static boolean[] strongholdBiasedToById() {
        int highest = 0;
        for (int id : McBiomeRegistry.ids()) highest = Math.max(highest, id);
        boolean[] table = new boolean[highest + 1];
        for (int id : McBiomeRegistry.ids()) {
            table[id] = STRONGHOLD_BIASED_TO_SET.contains(McBiomeRegistry.get(id).name());
        }
        return table;
    }

    private static boolean strongholdBiasedTo(int biomeId) {
        return biomeId >= 0 && biomeId < STRONGHOLD_BIASED_TO_BY_ID.length
                && STRONGHOLD_BIASED_TO_BY_ID[biomeId];
    }
    /** {@code BiomeSource#possibleBiomes} for the pinned Overworld multi-noise source. */
    private static final long OVERWORLD_POSSIBLE_BIOME_MASK = overworldPossibleBiomeMask();
    private static final Map<String, Integer> BIOME_BIT_INDEX = biomeBitIndex();

    private final long worldSeed;
    private final McClimateSampler climate;
    /** Worker-local sampler used only to materialize immutable FULL chunk biome grids. */
    private final McClimateSampler fullBiomeClimate;
    private final Mc263StructureSetStartPlanner planner;
    private final QuartBiomeCache biomeCache = new QuartBiomeCache();
    /** Full chunks retain their immutable {@code ChunkAccess#fillBiomesFromNoise} results. */
    private final Map<Long, LoadedBiomeGrid> loadedBiomeGrids = new LinkedHashMap<>();
    private PlanningState boundState;
    private BaseHeightSampler baseHeight;
    private Mc263BaseHeightSampler baseColumns;

    private Mc263StructureWorldAccess(long worldSeed, Mc263StructureSetStartPlanner planner) {
        this.worldSeed = worldSeed;
        this.planner = planner;
        this.climate = new McClimateSampler(worldSeed);
        this.fullBiomeClimate = new McClimateSampler(worldSeed);
    }

    public static Mc263StructureWorldAccess overworld(long worldSeed) {
        return new Mc263StructureWorldAccess(worldSeed, Mc263StructureSetStartPlanner.pinned());
    }

    public long worldSeed() {
        return worldSeed;
    }

    public Mc263StructureSetStartPlanner planner() {
        return planner;
    }

    /**
     * Starts one independent structure-chunk decision with no retained biome search state.
     *
     * <p>The climate R-tree intentionally retains query order <em>within</em> a vanilla chunk
     * evaluation. It must not inherit a hint from another decided chunk, however, because a
     * {@link Mc263StructureCarrierOrigin.RegionMemo} may skip any already-decided chunk. Clearing
     * both caches at this boundary makes an uncached decision independent of the memo hit pattern
     * without disturbing query order inside that decision.</p>
     */
    void beginStructureChunkDecision() {
        climate.resetChunkCache();
        biomeCache.clear();
    }

    /**
     * Ends one start-chunk decision. The next decision clears the biome cache anyway, so a table that
     * one wide decision grew is released now instead of staying in the long-lived generation context.
     */
    void endStructureChunkDecision() {
        biomeCache.releaseIfLarge();
    }

    /**
     * Mirrors the biome-sampler part of {@code ServerLevel#getChunk}: a full chunk has every
     * section populated through {@code ChunkAccess#fillBiomesFromNoise} before its callers keep
     * using the same level.  The section loop is ascending and each section visits quart
     * {@code x}, then {@code y}, then {@code z}, exactly as {@code LevelChunkSection} does.
     *
     * <p>The caller owns the next logical-thread boundary. Official FULL generation may fill
     * these grids on worldgen workers whose thread-local climate hint is not inherited by the
     * following server-thread locate.</p>
     */
    public void activateFullChunk(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        if (loadedBiomeGrids.containsKey(key)) return;
        int[] cells = new int[BIOME_GRID_CELLS];
        for (int sectionY = Blocks.MIN_Y >> 4; sectionY <= Blocks.MAX_Y >> 4; sectionY++) {
            int minimumQuartY = sectionY << 2;
            for (int localQuartX = 0; localQuartX < 4; localQuartX++) {
                int quartX = Math.addExact(Math.multiplyExact(chunkX, 4), localQuartX);
                for (int localQuartY = 0; localQuartY < 4; localQuartY++) {
                    int quartY = minimumQuartY + localQuartY;
                    for (int localQuartZ = 0; localQuartZ < 4; localQuartZ++) {
                        int quartZ = Math.addExact(Math.multiplyExact(chunkZ, 4), localQuartZ);
                        cells[biomeGridIndex(localQuartX, quartY, localQuartZ)] =
                                fullBiomeClimate.biomeAtQuart(quartX, quartY, quartZ);
                    }
                }
            }
        }
        loadedBiomeGrids.put(key, new LoadedBiomeGrid(cells));
    }

    /**
     * Activates the full biome grids of an origin-centred chunk square in deterministic x-major,
     * then z-major order. Already loaded chunks retain their existing immutable grids.
     */
    public void activateFullBiomeNeighborhood(int originChunkX, int originChunkZ, int radius) {
        if (radius < 0) throw new IllegalArgumentException("negative biome-neighborhood radius");
        long firstX = (long) originChunkX - radius;
        long lastX = (long) originChunkX + radius;
        long firstZ = (long) originChunkZ - radius;
        long lastZ = (long) originChunkZ + radius;
        if (firstX < Integer.MIN_VALUE || lastX > Integer.MAX_VALUE
                || firstZ < Integer.MIN_VALUE || lastZ > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("biome-neighborhood chunk coordinate overflow");
        }
        for (long chunkX = firstX; chunkX <= lastX; chunkX++) {
            for (long chunkZ = firstZ; chunkZ <= lastZ; chunkZ++) {
                activateFullChunk((int) chunkX, (int) chunkZ);
            }
        }
    }

    /**
     * Builds and binds the immutable dimension planning state. Binding is required before any
     * exclusion-zone query, because a zone is defined by the other set's placement predicate.
     */
    public PlanningState buildState() {
        PlanningState state = planner.buildState(worldSeed, this);
        boundState = state;
        return state;
    }

    public PlanningState boundState() {
        if (boundState == null) throw new IllegalStateException("planning state is not bound");
        return boundState;
    }

    @Override public boolean hasPossibleSetFacts() { return true; }
    @Override public boolean hasExclusionStartFacts() { return true; }
    @Override public boolean hasPreferredBiomeSearch() { return true; }

    /** Mirrors {@code ChunkGeneratorStructureState#hasBiomesForStructureSet}. */
    @Override
    public boolean isStructureSetPossible(String setKey, List<String> memberKeys) {
        Objects.requireNonNull(setKey, "structure set key");
        Objects.requireNonNull(memberKeys, "structure set members");
        if (memberKeys.isEmpty()) throw new IllegalArgumentException("empty structure set members");
        for (String member : memberKeys) {
            if ((structureBiomeMask(member) & OVERWORLD_POSSIBLE_BIOME_MASK) != 0L) return true;
        }
        return false;
    }

    /** Mirrors {@code ChunkGeneratorStructureState#hasStructureChunkInRange}. */
    @Override
    public boolean hasStartCandidateInRange(String setKey, int chunkX, int chunkZ, int range) {
        Objects.requireNonNull(setKey, "exclusion set key");
        if (range < 0) throw new IllegalArgumentException("negative exclusion range");
        PlanningState state = boundState();
        if (!state.possibleSetKeys().contains(setKey)) return false;
        for (int x = chunkX - range; x <= chunkX + range; x++) {
            for (int z = chunkZ - range; z <= chunkZ + range; z++) {
                var candidate = planner.candidateAt(worldSeed, x, z, setKey, state, this);
                if (candidate.placementChunk() && candidate.frequencyAccepted()
                        && !candidate.excluded()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Mirrors {@code BiomeSource#findBiomeHorizontal(x, y, z, radius, predicate, random, sampler)}:
     * increment 1, {@code findClosest = false}, so the whole {@code radius} quart square is scanned
     * and reservoir-sampled with the forked legacy random.
     */
    @Override
    public Optional<BlockPos> findPreferredBiome(String tag, int x, int y, int z, int radius,
            long legacyForkSeed) {
        if (!STRONGHOLD_BIASED_TO_TAG.equals(tag)) {
            throw new IllegalArgumentException("unpinned preferred-biome tag: " + tag);
        }
        if (radius < 0) throw new IllegalArgumentException("negative preferred-biome radius");
        LegacyRand random = new LegacyRand(legacyForkSeed);
        int centerQuartX = x >> 2;
        int centerQuartZ = z >> 2;
        int quartY = y >> 2;
        int quartRadius = radius >> 2;
        BlockPos selected = null;
        int seen = 0;
        for (int dz = -quartRadius; dz <= quartRadius; dz++) {
            for (int dx = -quartRadius; dx <= quartRadius; dx++) {
                int quartX = centerQuartX + dx;
                int quartZ = centerQuartZ + dz;
                if (!strongholdBiasedTo(biomeIdAtQuart(quartX, quartY, quartZ))) {
                    continue;
                }
                if (selected == null || random.nextInt(seen + 1) == 0) {
                    selected = new BlockPos(quartX << 2, y, quartZ << 2);
                }
                seen++;
            }
        }
        return Optional.ofNullable(selected);
    }

    // ── real samplers ────────────────────────────────────────────────────────

    /** {@code BiomeSource#getNoiseBiome} at quart resolution. */
    public String biomeAtQuart(int quartX, int quartY, int quartZ) {
        return McBiomeRegistry.get(biomeIdAtQuart(quartX, quartY, quartZ)).name();
    }

    /** The same sampler answer as {@link #biomeAtQuart}, before it is spelled as a name. */
    private int biomeIdAtQuart(int quartX, int quartY, int quartZ) {
        return getLoadedOrNoiseBiome(quartX, quartY, quartZ);
    }

    /**
     * {@code ChunkAccess#getNoiseBiome}: loaded chunk grids clamp Y to their stored quart range;
     * unloaded chunks retain the pre-existing direct climate path and its y-zero cache.
     */
    private int getLoadedOrNoiseBiome(int quartX, int quartY, int quartZ) {
        int chunkX = Math.floorDiv(quartX, 4);
        int chunkZ = Math.floorDiv(quartZ, 4);
        LoadedBiomeGrid loaded = loadedBiomeGrids.get(chunkKey(chunkX, chunkZ));
        if (loaded != null) {
            int clampedQuartY = Math.max(BIOME_QUART_MIN_Y, Math.min(BIOME_QUART_MAX_Y, quartY));
            return loaded.cells[biomeGridIndex(Math.floorMod(quartX, 4), clampedQuartY,
                    Math.floorMod(quartZ, 4))];
        }
        if (quartY != 0) return climate.biomeAtQuart(quartX, quartY, quartZ);
        long key = (((long) quartX & 0xFFFFFFFFL) << 32) | ((long) quartZ & 0xFFFFFFFFL);
        int cached = biomeCache.get(key);
        if (cached != QuartBiomeCache.ABSENT) return cached;
        int id = climate.biomeAtQuart(quartX, 0, quartZ);
        biomeCache.put(key, id);
        return id;
    }

    /** Test-only package surface; the returned value is an immutable grid cell, never its array. */
    int loadedBiomeGridCellCount(int chunkX, int chunkZ) {
        LoadedBiomeGrid grid = loadedBiomeGrids.get(chunkKey(chunkX, chunkZ));
        return grid == null ? 0 : grid.cells.length;
    }

    /** Test-only package surface for the stored vanilla section-biome array layout. */
    int loadedBiomeGridIdAt(int chunkX, int chunkZ, int index) {
        LoadedBiomeGrid grid = loadedBiomeGrids.get(chunkKey(chunkX, chunkZ));
        if (grid == null) throw new IllegalArgumentException("biome grid is not loaded");
        return grid.cells[index];
    }

    private static int biomeGridIndex(int localQuartX, int quartY, int localQuartZ) {
        int relativeQuartY = quartY - BIOME_QUART_MIN_Y;
        return relativeQuartY * 16 + localQuartX * 4 + localQuartZ;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private static final class LoadedBiomeGrid {
        private final int[] cells;

        private LoadedBiomeGrid(int[] cells) {
            if (cells.length != BIOME_GRID_CELLS) {
                throw new IllegalArgumentException("incomplete loaded biome grid");
            }
            this.cells = cells;
        }
    }

    /**
     * Open-addressed {@code long -> int} cache for the {@code y == 0} quart lane.
     *
     * <p>AGENTS rule 10l: the ring builder samples hundreds of thousands of quarts, and the boxed
     * {@code HashMap<Long, Integer>} this replaces spent that budget on {@code Long}/{@code
     * Integer} allocation, hash-bucket treeification and resizes. The answers, their order and
     * their count are unchanged — this is the same memo with the boxing removed.</p>
     */
    private static final class QuartBiomeCache {
        static final int ABSENT = Integer.MIN_VALUE;
        private long[] keys;
        private int[] values;
        private int mask;
        private int size;
        private int limit;

        QuartBiomeCache() { allocate(1 << 12); }

        private void allocate(int capacity) {
            keys = new long[capacity];
            values = new int[capacity];
            java.util.Arrays.fill(values, ABSENT);
            mask = capacity - 1;
            limit = capacity >>> 1;
        }

        int get(long key) {
            int index = index(key);
            while (values[index] != ABSENT) {
                if (keys[index] == key) return values[index];
                index = (index + 1) & mask;
            }
            return ABSENT;
        }

        void put(long key, int value) {
            if (value == ABSENT) throw new IllegalArgumentException("unrepresentable biome ID");
            int index = index(key);
            while (values[index] != ABSENT) {
                if (keys[index] == key) { values[index] = value; return; }
                index = (index + 1) & mask;
            }
            insert(key, value);
            if (++size > limit) grow();
        }

        void clear() {
            // One wide structure decision can grow this to a million slots; do not keep that for the
            // next, ordinary decision.
            if (keys.length > 1 << 16) allocate(1 << 12);
            else java.util.Arrays.fill(values, ABSENT);
            size = 0;
        }

        void releaseIfLarge() {
            if (keys.length > 1 << 16) clear();
        }

        private void insert(long key, int value) {
            int index = index(key);
            while (values[index] != ABSENT) index = (index + 1) & mask;
            keys[index] = key;
            values[index] = value;
        }

        private void grow() {
            long[] oldKeys = keys;
            int[] oldValues = values;
            allocate(oldKeys.length << 1);
            for (int slot = 0; slot < oldKeys.length; slot++) {
                if (oldValues[slot] != ABSENT) insert(oldKeys[slot], oldValues[slot]);
            }
        }

        private int index(long key) {
            long spread = key * 0x9E3779B97F4A7C15L;
            return (int) (spread >>> 40) & mask;
        }
    }

    /** {@code BiomeSource#getNoiseBiome(QuartPos.fromBlock(...))} at block resolution. */
    public String biomeAt(int blockX, int blockY, int blockZ) {
        return biomeAtQuart(Math.floorDiv(blockX, 4), Math.floorDiv(blockY, 4),
                Math.floorDiv(blockZ, 4));
    }

    /** {@code Structure#isValidBiome}: the structure's biome tag contains the sampled biome. */
    public boolean isValidBiome(String structureKey, int blockX, int blockY, int blockZ) {
        long mask = structureBiomeMask(structureKey);
        Integer bit = BIOME_BIT_INDEX.get(biomeAt(blockX, blockY, blockZ));
        return bit != null && (mask & (1L << bit)) != 0L;
    }

    /** Every pinned structure whose biome tag contains the biome at this position, in registry order. */
    public List<String> biomeMemberStructures(int blockX, int blockY, int blockZ) {
        Integer bit = BIOME_BIT_INDEX.get(biomeAt(blockX, blockY, blockZ));
        if (bit == null) return List.of();
        long biomeMask = 1L << bit;
        return Mc263StructureIndexReceipt.entries().stream()
                .filter(entry -> (entry.biomeMask() & biomeMask) != 0L)
                .map(Mc263StructureIndexReceipt.Entry::key)
                .sorted()
                .toList();
    }

    /**
     * The pinned {@code ChunkGenerator#getBaseHeight} lane.
     *
     * <p>Vanilla walks a {@code NoiseChunk} column whose block states come from the final density
     * function <em>and</em> the aquifer-backed global fluid picker, so a column's
     * {@code WORLD_SURFACE_WG} answer below sea level depends on the local
     * {@code Aquifer.FluidStatus} fluid level. {@link com.gameexpert.terrain.Mc263BaseHeightSampler}
     * is that walk and reproduces all 21,194 recorded {@code world-sampler-receipts-v1} columns
     * over the three recorded seeds, so it is the default lane bound here. A caller may still bind
     * its own {@link BaseHeightSampler}, for example a test-scope recorded-height table.</p>
     */
    public interface BaseHeightSampler {
        /** First non-air block above the column, water included: {@code WORLD_SURFACE_WG}. */
        int worldSurfaceWg(int blockX, int blockZ);

        /** First non-air, non-fluid block above the column: {@code OCEAN_FLOOR_WG}. */
        int oceanFloorWg(int blockX, int blockZ);
    }

    /** Binds the base-height lane. Required before any start generator that probes a height. */
    public Mc263StructureWorldAccess bindBaseHeightSampler(BaseHeightSampler sampler) {
        this.baseHeight = Objects.requireNonNull(sampler, "base-height sampler");
        return this;
    }

    /** Whether a base-height lane is available; the landed sampler is the default, so always. */
    public boolean hasBaseHeightSampler() {
        return true;
    }

    /** Whether a caller replaced the default landed lane with its own sampler. */
    public boolean hasExplicitlyBoundBaseHeightSampler() {
        return baseHeight != null;
    }

    public int worldSurfaceWg(int blockX, int blockZ) {
        return requireBaseHeight().worldSurfaceWg(blockX, blockZ);
    }

    public int oceanFloorWg(int blockX, int blockZ) {
        return requireBaseHeight().oceanFloorWg(blockX, blockZ);
    }

    public int seaLevel() {
        return Blocks.SEA_LEVEL;
    }

    /**
     * The pinned {@code RuinedPortalStructure#findSuitableY} opaque lane:
     * {@code Heightmap.Types#isOpaque} applied to the block a
     * {@code ChunkGenerator#getBaseColumn} {@code NoiseColumn} holds at {@code y}.
     *
     * <p>This is a real-noise-only lane. A caller may replace the {@link BaseHeightSampler} with a
     * recorded-height fixture, but a recorded height cannot answer "is <em>this</em> y opaque" —
     * a column below its {@code WORLD_SURFACE_WG} answer is full of caves — so the column lane
     * always comes from the landed {@link Mc263BaseHeightSampler} for this world seed, never from
     * the injected height sampler. {@code ruined-portal-column-receipts-v1} authenticates it
     * against the official columns.</p>
     *
     * @param motionBlockingOnly {@code true} for {@code OCEAN_FLOOR_WG}, {@code false} for
     *     {@code WORLD_SURFACE_WG}
     */
    public boolean opaqueInBaseColumn(int blockX, int y, int blockZ, boolean motionBlockingOnly) {
        return baseColumns().opaqueInBaseColumn(blockX, y, blockZ, motionBlockingOnly);
    }

    /**
     * {@code Biome#coldEnoughToSnow(pos, seaLevel)}: the height-adjusted biome temperature at the
     * position is below {@code 0.15}. Vanilla applies no precipitation gate here — the
     * {@code RuinedPortalStructure#isCold} lane calls {@code coldEnoughToSnow} directly — and the
     * temperature itself is {@link McFreezeTopLayer#computeTemperature}, the landed port of
     * {@code Biome#getHeightAdjustedTemperature}.
     */
    public boolean coldEnoughToSnow(int blockX, int blockY, int blockZ, int seaLevel) {
        McBiomeRegistry.Biome biome = McBiomeRegistry.get(biomeIdAt(blockX, blockY, blockZ));
        return McFreezeTopLayer.computeTemperature(blockX, blockY, blockZ, biome.temperature(),
                biome.frozenModifier(), seaLevel) < COLD_ENOUGH_TO_SNOW_TEMPERATURE;
    }

    private Mc263BaseHeightSampler baseColumns() {
        if (baseColumns == null) baseColumns = Mc263BaseHeightSampler.overworld(worldSeed);
        return baseColumns;
    }

    private int biomeIdAt(int blockX, int blockY, int blockZ) {
        return climate.biomeAtQuart(Math.floorDiv(blockX, 4), Math.floorDiv(blockY, 4),
                Math.floorDiv(blockZ, 4));
    }

    private BaseHeightSampler requireBaseHeight() {
        if (baseHeight == null) baseHeight = Mc263BaseHeightSampler.overworld(worldSeed);
        return baseHeight;
    }

    private static long structureBiomeMask(String structureKey) {
        for (Mc263StructureIndexReceipt.Entry entry : Mc263StructureIndexReceipt.entries()) {
            if (entry.key().equals(structureKey)) return entry.biomeMask();
        }
        throw new IllegalArgumentException("unknown pinned structure: " + structureKey);
    }

    private static Map<String, Integer> biomeBitIndex() {
        Map<String, Integer> indexed = new LinkedHashMap<>();
        List<Mc263FeatureIndexReceipt.BiomeFeatureData> biomes = Mc263FeatureIndexReceipt.biomes();
        for (int index = 0; index < biomes.size(); index++) {
            indexed.put(biomes.get(index).biomeKey(), index);
        }
        return Map.copyOf(indexed);
    }

    private static long overworldPossibleBiomeMask() {
        int count = Mc263FeatureIndexReceipt.biomes().size();
        if (count <= 0 || count > 64) {
            throw new ExceptionInInitializerError("pinned Overworld biome count is not maskable");
        }
        return count == 64 ? -1L : (1L << count) - 1L;
    }
}
