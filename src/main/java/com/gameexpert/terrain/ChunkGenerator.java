package com.gameexpert.terrain;

import static com.gameexpert.terrain.Blocks.*;
import static com.gameexpert.terrain.NoiseSuite.*;

import com.gameexpert.terrain.mc.biome.McClimate;
import com.gameexpert.terrain.mc.biome.McClimateSampler;
import com.gameexpert.terrain.mc.biome.McBiomeRegistry;
import com.gameexpert.terrain.mc.aquifer.McAquifer;
import com.gameexpert.terrain.mc.carver.McConfiguredCarvers;
import com.gameexpert.terrain.mc.df.McDensityFunction;
import com.gameexpert.terrain.mc.df.McDensityFunctionLoader;
import com.gameexpert.terrain.mc.df.McDensityFunctionLoader.FullDensityGraph;
import com.gameexpert.terrain.mc.feature.Mc263FeaturesRegion;
import com.gameexpert.authority.versioned.NeutralFinalChunk;
import com.gameexpert.terrain.mc.structure.Mc263Beardifier;
import com.gameexpert.terrain.mc.ore.Mc263OreMaterialRule;
import com.gameexpert.terrain.mc.surface.McBiomeZoom;
import com.gameexpert.terrain.mc.surface.McFreezeTopLayer;
import com.gameexpert.terrain.mc.surface.McSurfaceContext;
import com.gameexpert.terrain.mc.surface.McSurfaceRuleEngine;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 서버 권위 기본 청크 생성기. 고정 26.3-snapshot-7 density/surface/aquifer 입력으로 기본 지형을 만들고
 * configured carver 까지 적용한 canonical NOISE-to-CARVERS cut 을 만든다. 광맥·폐광·던전·
 * freeze_top_layer 를 포함한 CARVERS 이후 전 단계는 canonical 26.3 FEATURES product 소유다.
 *
 * <p>나무·식생·샘·호수와 절차 구조물은 이 배열을 바꾸는 호환 단계가 아니라
 * {@code SurfaceDecorator}/{@code WorldRuntime}의 비영속 runtime overlay다. 클라이언트는
 * 서버 snapshot을 소비하며 전체 청크를 자체 생성하지 않는다.</p>
 */
public final class ChunkGenerator {
    private static final boolean PROFILE_TERRAIN=Boolean.getBoolean("game.terrain-profile");
    /**
     * Canonical NOISE-to-CARVERS stage checkpoints. The legacy ore/structures/vegetation/bedrock
     * checkpoints were removed with the legacy final raster: everything after CARVERS is owned by
     * the canonical 26.3 FEATURES product, whose final carrier is the only chunk answer.
     */
    public static final String[] CANONICAL_STAGE_NAMES = {
        "fieldDescriptor", "baseMass", "sculpt", "hydro", "surface", "cavesCarvers"
    };

    // 높이 파생 경계. 월드 높이를 바꾸면 생성 단계가 함께 이동해야 한다.
    private static final int BUILD_FLOOR = MIN_Y;
    private static final int BUILD_CEIL = MAX_Y;
    private static final int HEIGHT_PAD = 18;

    private final int seed;
    private final McDensityFunction density;
    private final McDensityFunction noodleDensity;
    private final McDensityFunction.Context densityContext;
    private final Mc263Beardifier beardifier;
    private final McAquifer.Inputs aquiferInputs;
    private McAquifer aquifer;
    private final Mc263OreMaterialRule oreMaterialRule;
    private final McClimateSampler climateSampler;
    private final McSurfaceRuleEngine surfaceRules;
    private static final int DENSITY_RESOURCE_CACHE_LIMIT = 8;
    private static final int DENSITY_COLUMN_CACHE_LIMIT = 8192;
    private static final java.util.Map<Integer,DensityResources> DENSITY_RESOURCES =
            new java.util.LinkedHashMap<>(16, 0.75F, true);
    private static final java.util.concurrent.ConcurrentMap<Integer,ThreadLocal<McSurfaceRuleEngine>> SURFACE_ENGINES=new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentMap<Integer,ThreadLocal<McClimateSampler>> CLIMATE_SAMPLERS=new java.util.concurrent.ConcurrentHashMap<>();
    /** The stored noise-biome grid owns its own climate tree hint; see {@link #quartBiome}. */
    private static final java.util.concurrent.ConcurrentMap<Integer,ThreadLocal<McClimateSampler>> NOISE_BIOME_SAMPLERS=new java.util.concurrent.ConcurrentHashMap<>();
    private final BoundedDensityColumnCache densityColumnCache;
    private final PostCarversEvidenceContext evidenceContext;
    // A chunk repeatedly reads the same four lattice columns for every block Y.
    private final java.util.Map<Long,double[]> localDensityColumnCache=new java.util.HashMap<>();

    // 생성 중 상태
    private short[] blocks;
    private int cx, cz, baseX, baseZ;
    private String[] stageHashes;
    private String[] stageSha256;
    private int stageIndex;
    private long profileCheckpointNanos;
    private long[] profileStageNanos;

    private ChunkGenerator(int seed) {
        this(seed, null);
    }

    private ChunkGenerator(int seed, Mc263Beardifier beardifier) {
        this(seed, beardifier, densityResources(seed), null);
    }

    private ChunkGenerator(int seed, Mc263Beardifier beardifier, DensityResources resources,
            PostCarversEvidenceContext evidenceContext) {
        this.seed = seed;
        this.beardifier = beardifier;
        this.density=resources.graph.mainLatticeInput();
        this.noodleDensity=resources.graph.noodle();
        DensityEvaluationCache evaluationCache =
                new DensityEvaluationCache(resources.graph.cacheSlotCount());
        this.densityContext=new McDensityFunction.Context(0,0,0,evaluationCache);
        this.densityColumnCache=resources.columns;
        this.evidenceContext=evidenceContext;
        this.aquiferInputs=resources.aquiferInputs;
        this.oreMaterialRule=new Mc263OreMaterialRule(seed);
        this.surfaceRules = evidenceContext == null
                ? surfaceEngine(seed) : evidenceContext.surfaceEngine(seed);
        this.climateSampler = evidenceContext == null
                ? climateSampler(seed) : evidenceContext.climateSampler(seed);
        this.noiseBiomeSampler = evidenceContext == null
                ? noiseBiomeSampler(seed) : evidenceContext.noiseBiomeSampler(seed);
    }
    private static DensityResources densityResources(int seed){
        synchronized(DENSITY_RESOURCES){
            DensityResources cached=DENSITY_RESOURCES.get(seed);
            if(cached!=null)return cached;
            DensityResources created=loadDensityResources(seed);
            DENSITY_RESOURCES.put(seed,created);
            if(DENSITY_RESOURCES.size()>DENSITY_RESOURCE_CACHE_LIMIT){
                java.util.Iterator<Integer> iterator=DENSITY_RESOURCES.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
            return created;
        }
    }

    private static DensityResources loadDensityResources(int seed) {
        try {
            McDensityFunctionLoader loader=new McDensityFunctionLoader(Path.of("."),seed);
            FullDensityGraph graph=loader.fullDensityGraph();
            McAquifer.Inputs aquiferInputs=McAquifer.loadInputs(
                    seed,loader.erosion(),loader.depth());
            return new DensityResources(graph,aquiferInputs,
                    new BoundedDensityColumnCache(DENSITY_COLUMN_CACHE_LIMIT));
        } catch (IOException failure) {
            throw new IllegalStateException("근거 없음: 고정 vanilla density datapack",failure);
        }
    }
    private static McSurfaceRuleEngine loadSurfaceEngine(int seed) {
        try {
            return McSurfaceRuleEngine.load26_3(seed);
        } catch (IOException failure) {
            throw new IllegalStateException("근거 없음: 고정 26.3 surface datapack", failure);
        }
    }

    private static ThreadLocal<McSurfaceRuleEngine> surfaceEngineRegistry(int seed) {
        return ThreadLocal.withInitial(() -> loadSurfaceEngine(seed));
    }

    private static ThreadLocal<McClimateSampler> climateSamplerRegistry(int seed) {
        return ThreadLocal.withInitial(() -> new McClimateSampler(seed));
    }

    private static McSurfaceRuleEngine surfaceEngine(int seed){
        return SURFACE_ENGINES.computeIfAbsent(seed,
                key -> surfaceEngineRegistry(key)).get();
    }

    private static McClimateSampler climateSampler(int seed) {
        return CLIMATE_SAMPLERS.computeIfAbsent(seed,
                key -> climateSamplerRegistry(key)).get();
    }

    private static McClimateSampler noiseBiomeSampler(int seed) {
        return NOISE_BIOME_SAMPLERS.computeIfAbsent(seed,
                key -> climateSamplerRegistry(key)).get();
    }

    /**
     * Current-only canonical FEATURES handoff. The caller must provide the complete verified
     * product; this method never constructs or compares a legacy terrain result.
     */
    public static GeneratedChunk generateCanonicalChunkData(int seed, int cx, int cz,
            NeutralFinalChunk product) {
        NeutralFinalChunk finalChunk = Objects.requireNonNull(product,
                "canonical generation product");
        finalChunk.requireGenerationSeed(seed); // Raw replay carriers have no generation provenance.
        return generatedChunkFromCanonicalCarrier(cx, cz, finalChunk);
    }

    /** Package boundary for replaying one already-verified current canonical store carrier. */
    static GeneratedChunk generatedChunkFromCanonicalCarrier(int cx, int cz,
            NeutralFinalChunk finalChunk) {
        Objects.requireNonNull(finalChunk, "canonical final carrier");
        if (finalChunk.chunkX() != cx || finalChunk.chunkZ() != cz) {
            throw new IllegalArgumentException("canonical final carrier coordinates mismatch");
        }
        int[] worldSurface = finalChunk.worldSurfaceWg();
        short[] surfaceHeights = new short[worldSurface.length];
        for (int index = 0; index < worldSurface.length; index++) {
            int top = Math.subtractExact(worldSurface[index], 1);
            if (top < Short.MIN_VALUE || top > Short.MAX_VALUE) {
                throw new IllegalStateException("canonical surface height exceeds signed-16 range");
            }
            surfaceHeights[index] = (short) top;
        }
        return new GeneratedChunk(cx, cz, finalChunk.blockIds(), surfaceHeights, finalChunk);
    }

    /**
     * Canonical NOISE-to-CARVERS cut blocks. This is the pre-FEATURES terrain shape (density,
     * aquifer, surface rules, configured carvers) and is chunk-independent and deterministic.
     * Everything after CARVERS belongs to the canonical 26.3 FEATURES product.
     */
    public static short[] generatePostCarversBlocks(int seed, int cx, int cz) {
        return generatePostCarversChunkData(seed, cx, cz).blockIds();
    }

    /**
     * Per-worker state for a collecting post-CARVERS evidence producer. Mutable density columns
     * and their bounded seed registry are owned by this context, never by process-global state.
     * Construct one context per worker and reuse it for that worker's declared chunk sequence.
     */
    public static final class PostCarversEvidenceContext {
        private final java.util.Map<Integer,DensityResources> densityResources =
                new java.util.LinkedHashMap<>(16, 0.75F, true);
        private final java.util.concurrent.ConcurrentMap<Integer,ThreadLocal<McSurfaceRuleEngine>>
                surfaceEngines = new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.concurrent.ConcurrentMap<Integer,ThreadLocal<McClimateSampler>>
                climateSamplers = new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.concurrent.ConcurrentMap<Integer,ThreadLocal<McClimateSampler>>
                noiseBiomeSamplers = new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.concurrent.atomic.AtomicLong generationInvocations =
                new java.util.concurrent.atomic.AtomicLong();
        private final java.util.concurrent.atomic.AtomicBoolean cancelled =
                new java.util.concurrent.atomic.AtomicBoolean();

        public PostCarversEvidenceContext() {}

        private synchronized DensityResources densityResources(int seed) {
            DensityResources cached=densityResources.get(seed);
            if(cached!=null)return cached;
            DensityResources created=loadDensityResources(seed);
            densityResources.put(seed,created);
            if(densityResources.size()>DENSITY_RESOURCE_CACHE_LIMIT){
                java.util.Iterator<Integer> iterator=densityResources.keySet().iterator();
                iterator.next();
                iterator.remove();
            }
            return created;
        }

        private McSurfaceRuleEngine surfaceEngine(int seed) {
            return surfaceEngines.computeIfAbsent(seed,
                    key -> surfaceEngineRegistry(key)).get();
        }

        private McClimateSampler climateSampler(int seed) {
            return climateSamplers.computeIfAbsent(seed,
                    key -> climateSamplerRegistry(key)).get();
        }

        private McClimateSampler noiseBiomeSampler(int seed) {
            return noiseBiomeSamplers.computeIfAbsent(seed,
                    key -> climateSamplerRegistry(key)).get();
        }

        /** Requests cancellation of the current or next generation using this context. */
        public void cancel() {
            cancelled.set(true);
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        private void recordGenerationInvocation() {
            generationInvocations.incrementAndGet();
        }

        long generationInvocationCount() {
            return generationInvocations.get();
        }

        synchronized int densityColumnCacheSize(int seed) {
            DensityResources resources=densityResources.get(seed);
            return resources==null?0:resources.columns.size();
        }

        synchronized Object densityColumnCacheIdentity(int seed) {
            return densityResources(seed).columns;
        }

        synchronized Object densityResourceIdentity(int seed) {
            return densityResources(seed);
        }

        synchronized Object surfaceEngineIdentity(int seed) {
            return surfaceEngine(seed);
        }

        synchronized Object climateSamplerIdentity(int seed) {
            return climateSampler(seed);
        }

        synchronized Object noiseBiomeSamplerIdentity(int seed) {
            return noiseBiomeSampler(seed);
        }

        int densityColumnCacheLimit() {
            return DENSITY_COLUMN_CACHE_LIMIT;
        }
    }

    /** Immutable evidence from exactly one canonical NOISE-to-CARVERS generation. */
    public static final class PostCarversEvidence {
        private final short[] blocks;
        private final String[] stageSha256;

        private PostCarversEvidence(short[] blocks, String[] stageSha256) {
            this.blocks=blocks.clone();
            this.stageSha256=stageSha256.clone();
        }

        public short[] blocks() {
            return blocks.clone();
        }

        public String[] stageSha256() {
            return stageSha256.clone();
        }
    }

    /**
     * Produces the blocks and all stage SHA-256 checkpoints from one generation invocation. The
     * context is intentionally non-optional so collecting callers cannot share mutable columns.
     */
    public static PostCarversEvidence generatePostCarversEvidence(
            PostCarversEvidenceContext context, int seed, int cx, int cz) {
        Objects.requireNonNull(context, "post-CARVERS evidence context");
        requireRepresentableChunk(cx, cz);
        checkCancellation(context);
        ChunkGenerator generator=new ChunkGenerator(seed, null,
                context.densityResources(seed), context);
        return generator.generatePostCarversEvidence(cx, cz);
    }

    /**
     * FEATURES input cut for the current project generator. This is deliberately
     * package-visible: only the explicit region bridge may promote it into a writable view.
     */
    static PostCarversChunkData generatePostCarversChunkData(int seed, int cx, int cz) {
        requireRepresentableChunk(cx, cz);
        ChunkGenerator generator = new ChunkGenerator(seed);
        return generator.generatePostCarversChunkData(cx, cz);
    }

    /** Package boundary for a post-CARVERS cut with worker-local evidence resources. */
    static PostCarversChunkData generatePostCarversChunkData(
            PostCarversEvidenceContext context, int seed, int cx, int cz) {
        Objects.requireNonNull(context, "post-CARVERS evidence context");
        requireRepresentableChunk(cx, cz);
        checkCancellation(context);
        ChunkGenerator generator = new ChunkGenerator(seed, null,
                context.densityResources(seed), context);
        return generator.generatePostCarversChunkData(cx, cz);
    }

    /**
     * Canonical NOISE-to-CARVERS cut. A verified per-chunk Beardifier is mandatory so a caller
     * cannot silently generate structure-free density and later label it canonical.
     */
    static PostCarversChunkData generatePostCarversChunkData(int seed, int cx, int cz,
            Mc263Beardifier beardifier) {
        requireRepresentableChunk(cx, cz);
        ChunkGenerator generator = new ChunkGenerator(seed,
                Objects.requireNonNull(beardifier, "canonical Beardifier"));
        return generator.generatePostCarversChunkData(cx, cz);
    }

    /** Package boundary for a Beardifier-backed cut with worker-local evidence resources. */
    static PostCarversChunkData generatePostCarversChunkData(
            PostCarversEvidenceContext context, int seed, int cx, int cz,
            Mc263Beardifier beardifier) {
        Objects.requireNonNull(context, "post-CARVERS evidence context");
        requireRepresentableChunk(cx, cz);
        checkCancellation(context);
        ChunkGenerator generator = new ChunkGenerator(seed,
                Objects.requireNonNull(beardifier, "canonical Beardifier"),
                context.densityResources(seed), context);
        return generator.generatePostCarversChunkData(cx, cz);
    }

    public static final class GeneratedChunk {
        /** Custom provider cells have no canonical carrier and never enter canonical receipt creation. */
        public static GeneratedChunk customDimension(int chunkX, int chunkZ, short[] blocks,
                short[] surfaceHeights) {
            requireRepresentableChunk(chunkX, chunkZ);
            if (blocks == null || blocks.length != Blocks.CHUNK_BLOCKS
                    || surfaceHeights == null
                    || surfaceHeights.length != Blocks.CHUNK_X * Blocks.CHUNK_Z) {
                throw new IllegalArgumentException("complete custom dimension chunk required");
            }
            for (short block : blocks) {
                if (!Blocks.isWorldBlockId(Short.toUnsignedInt(block))) {
                    throw new IllegalArgumentException("unregistered custom dimension block");
                }
            }
            for (short height : surfaceHeights) {
                if (height < Blocks.MIN_Y - 1 || height > Blocks.MAX_Y) {
                    throw new IllegalArgumentException("custom dimension surface height out of bounds");
                }
            }
            return new GeneratedChunk(chunkX, chunkZ, blocks.clone(), surfaceHeights.clone(), null);
        }
        private final short[] blocks;
        private final short[] surfaceHeights;
        private final NeutralFinalChunk finalLiveCarrier;

        private GeneratedChunk(int chunkX, int chunkZ, short[] blocks, short[] surfaceHeights,
                NeutralFinalChunk finalLiveCarrier) {
            this.blocks = blocks;
            this.surfaceHeights = surfaceHeights;
            this.finalLiveCarrier = finalLiveCarrier;
        }

        public short[] blocks() {
            return blocks;
        }

        public short[] surfaceHeights() {
            return surfaceHeights;
        }

        public NeutralFinalChunk finalLiveCarrier() {
            return finalLiveCarrier;
        }

        public int surfaceHeightAt(int localX, int localZ) {
            return surfaceHeights[localX + localZ * Blocks.CHUNK_X];
        }
    }

    private static void requireRepresentableChunk(int chunkX, int chunkZ) {
        requireRepresentableChunkCoordinate(chunkX, "X");
        requireRepresentableChunkCoordinate(chunkZ, "Z");
    }

    private static void requireRepresentableChunkCoordinate(int chunkCoordinate, String axis) {
        long minimumBlock = (long) chunkCoordinate * Blocks.CHUNK_X;
        long maximumBlock = minimumBlock + Blocks.CHUNK_X - 1L;
        if (minimumBlock < Integer.MIN_VALUE || maximumBlock > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("chunk " + axis
                    + " cannot be represented as block coordinates: " + chunkCoordinate);
        }
    }

    private String[] sampleThreeDimensionalBiomeKeys(int chunkX, int chunkZ) {
        String[] biomeKeys = new String[Mc263FeaturesRegion.BIOME_COUNT_PER_CHUNK];
        int minimumQuartY = Math.floorDiv(Blocks.MIN_Y, 4);
        climateSampler.resetChunkCache();
        for (int localQuartY = 0;
                localQuartY < Mc263FeaturesRegion.BIOME_QUART_HEIGHT; localQuartY++) {
            if (periodicCancellationCheck(localQuartY)) checkCancellation();
            int worldQuartY = minimumQuartY + localQuartY;
            for (int localQuartZ = 0;
                    localQuartZ < Mc263FeaturesRegion.BIOME_QUART_WIDTH; localQuartZ++) {
                if (periodicCancellationCheck(localQuartZ)) checkCancellation();
                int worldQuartZ = chunkZ * Mc263FeaturesRegion.BIOME_QUART_WIDTH + localQuartZ;
                for (int localQuartX = 0;
                        localQuartX < Mc263FeaturesRegion.BIOME_QUART_WIDTH; localQuartX++) {
                    int worldQuartX = chunkX * Mc263FeaturesRegion.BIOME_QUART_WIDTH
                            + localQuartX;
                    int index = localQuartX
                            + localQuartZ * Mc263FeaturesRegion.BIOME_QUART_WIDTH
                            + localQuartY * Mc263FeaturesRegion.BIOME_QUART_WIDTH
                                    * Mc263FeaturesRegion.BIOME_QUART_WIDTH;
                    biomeKeys[index] = McBiomeRegistry.get(climateSampler.biomeAtQuart(
                            worldQuartX, worldQuartY, worldQuartZ)).name();
                }
            }
        }
        return biomeKeys;
    }

    /** Immutable current-project state at the exact post-CARVERS checkpoint. */
    static final class PostCarversChunkData {
        private final int chunkX;
        private final int chunkZ;
        private final short[] blockIds;
        private final String[] biomeKeys;
        private final java.util.List<Mc263FeaturesRegion.StateOverride> stateOverrides;
        private final java.util.List<Mc263FeaturesRegion.PostprocessMark> postprocessMarks;
        private final java.util.List<Mc263FeaturesRegion.ScheduledBlockTick> scheduledBlockTicks;
        private final java.util.List<Mc263FeaturesRegion.ScheduledFluidTick> scheduledFluidTicks;

        private PostCarversChunkData(int chunkX, int chunkZ, short[] blockIds,
                String[] biomeKeys) {
            if (blockIds.length != CHUNK_BLOCKS) {
                throw new IllegalArgumentException(
                        "post-CARVERS block count must be " + CHUNK_BLOCKS);
            }
            if (biomeKeys.length != Mc263FeaturesRegion.BIOME_COUNT_PER_CHUNK) {
                throw new IllegalArgumentException("post-CARVERS biome count must be "
                        + Mc263FeaturesRegion.BIOME_COUNT_PER_CHUNK);
            }
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.blockIds = blockIds.clone();
            this.biomeKeys = biomeKeys.clone();
            // The current project carver records only numeric material writes. Exact states and
            // ProtoChunk postprocess/block-tick/fluid-tick sidecars are therefore explicitly empty.
            this.stateOverrides = java.util.List.of();
            this.postprocessMarks = java.util.List.of();
            this.scheduledBlockTicks = java.util.List.of();
            this.scheduledFluidTicks = java.util.List.of();
        }

        int chunkX() {
            return chunkX;
        }

        int chunkZ() {
            return chunkZ;
        }

        short[] blockIds() {
            return blockIds.clone();
        }

        String[] biomeKeys() {
            return biomeKeys.clone();
        }

        java.util.List<Mc263FeaturesRegion.StateOverride> stateOverrides() {
            return stateOverrides;
        }

        java.util.List<Mc263FeaturesRegion.PostprocessMark> postprocessMarks() {
            return postprocessMarks;
        }

        java.util.List<Mc263FeaturesRegion.ScheduledBlockTick> scheduledBlockTicks() {
            return scheduledBlockTicks;
        }

        java.util.List<Mc263FeaturesRegion.ScheduledFluidTick> scheduledFluidTicks() {
            return scheduledFluidTicks;
        }
    }

    /**
     * FNV stage descriptors of the canonical NOISE-to-CARVERS cut. Ordinary generation leaves the
     * checkpoint arrays null.
     */
    public static String[] generatePostCarversStageHashes(int seed, int cx, int cz) {
        ChunkGenerator generator = new ChunkGenerator(seed);
        generator.stageHashes = new String[CANONICAL_STAGE_NAMES.length];
        generator.generatePostCarvers(cx, cz);
        generator.requireCompleteStageRun();
        return generator.stageHashes.clone();
    }

    /** SHA-256 counterpart of every canonical NOISE-to-CARVERS checkpoint. */
    public static String[] generatePostCarversStageSha256(int seed, int cx, int cz) {
        ChunkGenerator generator = new ChunkGenerator(seed);
        return generator.generatePostCarversEvidence(cx, cz).stageSha256();
    }

    private PostCarversEvidence generatePostCarversEvidence(int cx, int cz) {
        stageSha256 = new String[CANONICAL_STAGE_NAMES.length];
        short[] result = generatePostCarvers(cx, cz);
        requireCompleteStageRun();
        String cutDigest = sha256U16BigEndian(result);
        if (!cutDigest.equals(stageSha256[CANONICAL_STAGE_NAMES.length - 1])) {
            throw new IllegalStateException("final canonical stage SHA-256 is not the CARVERS cut digest");
        }
        return new PostCarversEvidence(result, stageSha256);
    }

    private PostCarversChunkData generatePostCarversChunkData(int cx, int cz) {
        short[] blocks = generatePostCarvers(cx, cz);
        String[] biomeKeys = sampleThreeDimensionalBiomeKeys(cx, cz);
        return new PostCarversChunkData(cx, cz, blocks, biomeKeys);
    }

    private static void checkCancellation(PostCarversEvidenceContext context) {
        if (Thread.currentThread().isInterrupted()
                || (context != null && context.isCancelled())) {
            throw new java.util.concurrent.CancellationException(
                    "post-CARVERS generation cancelled");
        }
    }

    private void checkCancellation() {
        checkCancellation(evidenceContext);
    }

    private static boolean periodicCancellationCheck(int iteration) {
        return (iteration & 31) == 0;
    }

    private void requireCompleteStageRun() {
        if (stageIndex != CANONICAL_STAGE_NAMES.length) {
            throw new IllegalStateException("missing canonical stage checkpoint " + stageIndex);
        }
    }

    private void checkpoint() {
        profileCheckpoint();
        if (stageHashes != null) stageHashes[stageIndex] = NoiseSuite.fnvHex(blocks);
        if (stageSha256 != null) stageSha256[stageIndex] = sha256U16BigEndian(blocks);
        stageIndex++;
    }

    private void checkpoint(String hash) {
        profileCheckpoint();
        if (stageHashes != null) stageHashes[stageIndex] = hash;
        if (stageSha256 != null) stageSha256[stageIndex] = sha256U16BigEndian(blocks);
        stageIndex++;
    }

    /** Canonical chunk bytes: block-index order, unsigned-u16 big-endian (196,608 bytes). */
    public static byte[] canonicalBytes(short[] blocks) {
        if (blocks.length != CHUNK_BLOCKS) {
            throw new IllegalArgumentException("canonical terrain cell count must be " + CHUNK_BLOCKS);
        }
        byte[] bytes = new byte[CHUNK_BLOCKS * Short.BYTES];
        for (int index = 0, offset = 0; index < blocks.length; index++) {
            int value = Short.toUnsignedInt(blocks[index]);
            bytes[offset++] = (byte) (value >>> 8);
            bytes[offset++] = (byte) value;
        }
        return bytes;
    }

    private static String sha256U16BigEndian(short[] blocks) {
        return sha256(canonicalBytes(blocks));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private void profileCheckpoint(){if(!PROFILE_TERRAIN)return;long now=System.nanoTime();profileStageNanos[stageIndex]=now-profileCheckpointNanos;profileCheckpointNanos=now;
        if(stageIndex==CANONICAL_STAGE_NAMES.length-1){StringBuilder line=new StringBuilder("TERRAIN_PROFILE chunk=").append(cx).append(',').append(cz);
            for(int i=0;i<CANONICAL_STAGE_NAMES.length;i++)line.append(' ').append(CANONICAL_STAGE_NAMES[i]).append("Ms=").append(profileStageNanos[i]/1_000_000.0);System.err.println(line);}}

    /** 계약 §4 스폰 좌표 계산용 표면 높이 조회(청크 생성과 같은 높이 함수 사용). */
    public static int surfaceHeight(int seed, int worldX, int worldZ) {
        return new ChunkGenerator(seed).densitySurfaceHeight(worldX, worldZ);
    }

    // ── 시드 파생 계산(TS Ctx 대응) ─────────────────────────
    private static long columnKey(int wx, int wz) {
        return ((long) wx << 32) ^ (wz & 0xFFFFFFFFL);
    }

    // ── 블록 접근 헬퍼 ────────────────────────────────────
    private void set(int x, int y, int z, int v) {
        blocks[blockIndex(x, y, z)] = checkedBlock(v);
    }
    private int get(int x, int y, int z) {
        return Short.toUnsignedInt(blocks[blockIndex(x, y, z)]);
    }
    private void setW(int wx, int wy, int wz, int v) {
        int lx = wx - baseX;
        int lz = wz - baseZ;
        if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16
                || wy < MIN_Y || wy > MAX_Y) return;
        blocks[blockIndex(lx, wy, lz)] = checkedBlock(v);
    }
    private static short checkedBlock(int value) {
        if ((value & ~0xFFFF) != 0) {
            throw new IllegalArgumentException("block id outside unsigned-u16 range: " + value);
        }
        return (short) value;
    }
    private int densitySurfaceHeight(int wx, int wz) {
        int x0=Math.floorDiv(wx,4)*4,z0=Math.floorDiv(wz,4)*4;
        double tx=Math.floorMod(wx,4)/4.0,tz=Math.floorMod(wz,4)/4.0;
        double[] c00=latticeColumn(x0,z0),c10=latticeColumn(x0+4,z0),c01=latticeColumn(x0,z0+4),c11=latticeColumn(x0+4,z0+4);
        for(int y=BUILD_CEIL,iteration=0;y>=BUILD_FLOOR;y--,iteration++){
            if (periodicCancellationCheck(iteration)) checkCancellation();
            int yi=Math.floorDiv(y-MIN_Y,8);double ty=Math.floorMod(y,8)/8.0;
            if(assembledFullDensity(wx,y,wz,tx,ty,tz,c00,c10,c01,c11,yi)>0.0)return y;}
        return BUILD_FLOOR;
    }
    private double densityAt(int wx,int y,int wz){int x0=Math.floorDiv(wx,4)*4,z0=Math.floorDiv(wz,4)*4;
        int yi=Math.floorDiv(y-MIN_Y,8);double tx=Math.floorMod(wx,4)/4.0,ty=Math.floorMod(y,8)/8.0,tz=Math.floorMod(wz,4)/4.0;
        double[] c00=latticeColumn(x0,z0),c10=latticeColumn(x0+4,z0),c01=latticeColumn(x0,z0+4),c11=latticeColumn(x0+4,z0+4);
        return assembledFullDensity(wx,y,wz,tx,ty,tz,c00,c10,c01,c11,yi);}
    private double assembledFullDensity(int wx,int y,int wz,double tx,double ty,double tz,
            double[] c00,double[] c10,double[] c01,double[] c11,int yi){
        float main=(float) assembledDensity(tx,ty,tz,c00,c10,c01,c11,yi);
        densityContext.set(wx,y,wz);
        float noodle=(float) noodleDensity.compute(densityContext);
        float beard=beardifier == null ? 0.0F : beardifier.compute(wx,y,wz);
        return assembleFinalDensity(main,noodle,beard);}
    /** Exact final_density root order: add(min(squeezed interpolation, noodle), Beardifier). */
    static double assembleFinalDensity(float squeezedMain, float noodle, float beardifier) {
        return (float) Math.min(squeezedMain, noodle) + beardifier;
    }
    /** 26.3 NoiseChunk assembly order: interpolate already-scaled float corners, then squeeze. */
    static double assembledDensity(double tx,double ty,double tz,double[] c00,double[] c10,double[] c01,double[] c11,int yi){
        int next=Math.min(yi+1,c00.length-1);
        return assembleDensityFromCorners(tx,ty,tz,c00[yi],c10[yi],c00[next],c10[next],
                c01[yi],c11[yi],c01[next],c11[next]);}
    /**
     * Public cross-port vector leaf. Corner order is x/y/z binary order:
     * 000, 100, 010, 110, 001, 101, 011, 111; the corners already contain the 0.64 scale.
     */
    public static double assembleDensityFromCorners(double tx,double ty,double tz,
            double c000,double c100,double c010,double c110,
            double c001,double c101,double c011,double c111){
        float fx=(float)tx,fy=(float)ty,fz=(float)tz;
        float x00=lerpExact(fx,(float)c000,(float)c100);
        float x10=lerpExact(fx,(float)c010,(float)c110);
        float x01=lerpExact(fx,(float)c001,(float)c101);
        float x11=lerpExact(fx,(float)c011,(float)c111);
        float interpolated=lerpExact(fz,lerpExact(fy,x00,x10),lerpExact(fy,x01,x11));
        return squeezeDensity(interpolated);}
    static double squeezeDensity(double value){
        float clamped=Math.max(-1.0F,Math.min(1.0F,(float)value));
        return clamped/2.0F-clamped*clamped*clamped/24.0F;}
    private int fillDensityColumn(int lx,int lz,int wx,int wz){int x0=Math.floorDiv(wx,4)*4,z0=Math.floorDiv(wz,4)*4;
        double tx=Math.floorMod(wx,4)/4.0,tz=Math.floorMod(wz,4)/4.0;
        double[] c00=latticeColumn(x0,z0),c10=latticeColumn(x0+4,z0),c01=latticeColumn(x0,z0+4),c11=latticeColumn(x0+4,z0+4);
        int height=BUILD_FLOOR;for(int y=BUILD_FLOOR,iteration=0;y<=BUILD_CEIL;y++,iteration++){
            if (periodicCancellationCheck(iteration)) checkCancellation();
            int yi=Math.floorDiv(y-MIN_Y,8);double ty=Math.floorMod(y,8)/8.0;
            double sampledDensity=assembledFullDensity(wx,y,wz,tx,ty,tz,c00,c10,c01,c11,yi);
            int material=aquifer.blockForDensity(wx,y,wz,sampledDensity);
            if(material==STONE)material=oreMaterialRule.blockForSolid(wx,y,wz);
            if(material!=AIR)set(lx,y,lz,material);
            if(isSolidDensityMaterial(material))height=y;}
        return height;}
    private static boolean isSolidDensityMaterial(int material){
        return material!=AIR&&material!=WATER_SOURCE&&material!=LAVA_SOURCE;}
    private static float lerpExact(float t,float a,float b){return a+t*(b-a);}
    static int densityLatticeSampleY(int index){return MIN_Y+index*8;}
    private double[] latticeColumn(int wx,int wz){long key=columnKey(wx,wz);double[] samples=localDensityColumnCache.get(key);if(samples!=null)return samples;
        samples=densityColumnCache.get(key);
        if(samples==null){
            int count=(MAX_Y+1-MIN_Y)/8+1;
            double[] values=new double[count];
            for(int i=0;i<count;i++){
                if (periodicCancellationCheck(i)) checkCancellation();
                values[i]=density.compute(wx,densityLatticeSampleY(i),wz);
            }
            samples=densityColumnCache.putIfAbsent(key,values);
        }
        localDensityColumnCache.put(key,samples);return samples;}
    private static int fnvLong(int hash,long value){for(int shift=0;shift<64;shift+=8){hash^=(int)((value>>>shift)&255L);hash*=0x01000193;}return hash;}
    private static int blockForState(String s){if(s==null||s.equals("minecraft:stone"))return STONE;return switch(s){
        case "minecraft:dirt"->DIRT;case "minecraft:coarse_dirt"->COARSE_DIRT;
        case "minecraft:grass_block"->GRASS;case "minecraft:podzol"->PODZOL;case "minecraft:mycelium"->MYCELIUM;
        case "minecraft:sand"->SAND;case "minecraft:red_sand"->RED_SAND;case "minecraft:gravel"->GRAVEL;
        case "minecraft:sandstone"->SANDSTONE;case "minecraft:red_sandstone"->RED_SANDSTONE;case "minecraft:calcite"->CALCITE;
        case "minecraft:mud"->MUD;case "minecraft:clay"->CLAY;
        case "minecraft:snow_block"->SNOW_BLOCK;case "minecraft:powder_snow"->POWDER_SNOW;
        case "minecraft:terracotta"->TERRACOTTA;case "minecraft:orange_terracotta"->ORANGE_TERRACOTTA;
        case "minecraft:yellow_terracotta"->YELLOW_TERRACOTTA;case "minecraft:brown_terracotta"->BROWN_TERRACOTTA;
        case "minecraft:red_terracotta"->RED_TERRACOTTA;case "minecraft:white_terracotta"->WHITE_TERRACOTTA;
        case "minecraft:light_gray_terracotta"->LIGHT_GRAY_TERRACOTTA;
        case "minecraft:deepslate"->DEEPSLATE;case "minecraft:cobbled_deepslate"->COBBLED_DEEPSLATE;
        // The pinned tree's frozen-ocean hole branch returns air; it is therefore reachable.
        case "minecraft:air"->AIR;case "minecraft:ice"->ICE;case "minecraft:packed_ice"->PACKED_ICE;
        case "minecraft:water"->WATER_SOURCE;case "minecraft:bedrock"->BEDROCK;
        case "minecraft:sulfur"->SULFUR_BLOCK;case "minecraft:cinnabar"->CINNABAR;
        default->throw new IllegalStateException("근거 없음: surface state "+s);};}
    // ── BiomeManager fuzzy zoom (surface rule 전용) ─────────────────────────
    // SurfaceSystem#buildSurface는 셀마다 BiomeManager#getBiome(BlockPos)를 호출하므로 rule이
    // 보는 바이옴은 열 단위가 아니라 y마다 달라진다. zoom은 quart 격자에서 동작하니 quart
    // 바이옴 소스는 lazy quart 질의가 아니라 vanilla가 실제로 읽는 저장된 noise-biome 배열이다.
    // Climate.RTree#search는 ThreadLocal lastResult를 초기 bestDistance로 쓰고 leaf 채택을 strict
    // `<`로 판정하므로, 정확히 같은 거리의 leaf 두 개가 존재하는 quart에서는 결과가 "그 quart를
    // 언제 계산했는가"에 달린다. Vanilla는 ChunkAccess#fillBiomesFromNoise(sectionY 오름차순,
    // LevelChunkSection#fillBiomesFromNoise의 x→y→z)로 청크 전체를 한 번에 채우고 표면 단계는 그
    // 배열만 읽는다. 우리가 셀 단위로 게으르게 질의하면 그 tie가 다른 leaf로 풀린다.
    private final McClimateSampler noiseBiomeSampler;
    private static final int NOISE_BIOME_QUART_MIN_Y = MIN_Y >> 2;
    private static final int NOISE_BIOME_QUART_HEIGHT = (MAX_Y - MIN_Y + 1) >> 2;
    private static final int NOISE_BIOME_QUART_MAX_Y =
            NOISE_BIOME_QUART_MIN_Y + NOISE_BIOME_QUART_HEIGHT - 1;
    private static final int NOISE_BIOME_MIN_SECTION_Y = MIN_Y >> 4;
    private static final int NOISE_BIOME_MAX_SECTION_Y = MAX_Y >> 4;
    private static final int NOISE_BIOME_GRID_CELLS = 16 * NOISE_BIOME_QUART_HEIGHT;
    /** Fuzzy zoom reaches one chunk out on both axes, so the 3×3 neighbourhood is enough. */
    private static final int NOISE_BIOME_GRID_CACHE = 9;
    private final long[] noiseBiomeGridKeys = new long[NOISE_BIOME_GRID_CACHE];
    private final int[][] noiseBiomeGrids = new int[NOISE_BIOME_GRID_CACHE][];
    private int noiseBiomeGridCursor;
    private final int[] biomeZoomCorners = new int[8];
    private long biomeZoomSeedValue;
    private boolean biomeZoomSeedResolved;

    private long biomeZoomSeed(){
        if(!biomeZoomSeedResolved){
            biomeZoomSeedValue=McBiomeZoom.zoomSeed(seed);
            biomeZoomSeedResolved=true;
        }
        return biomeZoomSeedValue;
    }

    /**
     * {@code ChunkAccess#getNoiseBiome}: the quart y is clamped into the chunk's stored range and
     * the value comes from that chunk's {@code fillBiomesFromNoise} array.
     */
    private int quartBiome(int quartX,int quartY,int quartZ){
        int clampedQuartY=quartY<NOISE_BIOME_QUART_MIN_Y?NOISE_BIOME_QUART_MIN_Y
                :quartY>NOISE_BIOME_QUART_MAX_Y?NOISE_BIOME_QUART_MAX_Y:quartY;
        int[] grid=noiseBiomeGrid(quartX>>2,quartZ>>2);
        return grid[(clampedQuartY-NOISE_BIOME_QUART_MIN_Y)*16+(quartX&3)*4+(quartZ&3)];
    }

    private int[] noiseBiomeGrid(int chunkX,int chunkZ){
        long key=((long)chunkX<<32)^(chunkZ&0xffffffffL);
        for(int slot=0;slot<NOISE_BIOME_GRID_CACHE;slot++){
            if(noiseBiomeGrids[slot]!=null&&noiseBiomeGridKeys[slot]==key){
                return noiseBiomeGrids[slot];
            }
        }
        int[] grid=fillBiomesFromNoise(chunkX,chunkZ);
        noiseBiomeGridKeys[noiseBiomeGridCursor]=key;
        noiseBiomeGrids[noiseBiomeGridCursor]=grid;
        noiseBiomeGridCursor=(noiseBiomeGridCursor+1)%NOISE_BIOME_GRID_CACHE;
        return grid;
    }

    /**
     * {@code ChunkAccess#fillBiomesFromNoise} + {@code LevelChunkSection#fillBiomesFromNoise} of the
     * pinned 26.3-snapshot-7 server: sections ascending from the minimum section, then x, y, z. The
     * climate tree's last-result hint is reset once per chunk so the grid stays a pure function of
     * (seed, chunkX, chunkZ) and the generator remains chunk-independent.
     */
    private int[] fillBiomesFromNoise(int chunkX,int chunkZ){
        int[] grid=new int[NOISE_BIOME_GRID_CELLS];
        noiseBiomeSampler.resetChunkCache();
        for(int sectionY=NOISE_BIOME_MIN_SECTION_Y;sectionY<=NOISE_BIOME_MAX_SECTION_Y;sectionY++){
            checkCancellation();
            int minimumQuartY=sectionY*4;
            for(int x=0;x<4;x++){
                checkCancellation();
                for(int y=0;y<4;y++)for(int z=0;z<4;z++){
                int quartY=minimumQuartY+y;
                grid[(quartY-NOISE_BIOME_QUART_MIN_Y)*16+x*4+z]=
                        noiseBiomeSampler.biomeAtQuart(chunkX*4+x,quartY,chunkZ*4+z);
                }
            }
        }
        return grid;
    }

    /** Vanilla {@code BiomeManager#getBiome(BlockPos)}가 반환하는 바이옴 id다. */
    private int fuzzyBiomeAt(int wx,int by,int wz){
        int quartX=(wx-2)>>2,quartY=(by-2)>>2,quartZ=(wz-2)>>2;
        int[] corners=biomeZoomCorners;
        boolean uniform=true;
        for(int corner=0;corner<8;corner++){
            int value=quartBiome(quartX+((corner&4)==0?0:1),
                    quartY+((corner&2)==0?0:1),
                    quartZ+((corner&1)==0?0:1));
            corners[corner]=value;
            if(value!=corners[0])uniform=false;
        }
        if(uniform)return corners[0];
        return corners[McBiomeZoom.corner(biomeZoomSeed(),wx,by,wz)];
    }

    private void applyVanillaSurface(int lx,int lz,int wx,int wz,int topY,int[] pad){
        final int depth;try{depth=surfaceRules.surfaceDepth(wx,wz);}catch(IOException e){throw new IllegalStateException("근거 없음: surface noise",e);}
        int stoneDepthAbove=0;
        int waterHeight=McSurfaceRuleEngine.NO_WATER;
        int stoneDepthOffset=Integer.MAX_VALUE;
        // SurfaceSystem scans the entire vertical column.  In particular this reaches the
        // absolute bedrock/deepslate tree and preserves the fluid y+1 water-height sentinel.
        for(int y=MAX_Y,iteration=0;y>=MIN_Y;y--,iteration++){
            if (periodicCancellationCheck(iteration)) checkCancellation();
            int oldState=get(lx,y,lz);
            if(oldState==AIR){stoneDepthAbove=0;waterHeight=McSurfaceRuleEngine.NO_WATER;continue;}
            if(oldState==WATER_SOURCE||oldState==LAVA_SOURCE){
                if(waterHeight==McSurfaceRuleEngine.NO_WATER)waterHeight=y+1;
                continue;
            }
            if(stoneDepthOffset>=y){
                stoneDepthOffset=Integer.MIN_VALUE;
                for(int below=y-1,belowIteration=0;below>=MIN_Y;below--,belowIteration++){
                    if (periodicCancellationCheck(belowIteration)) checkCancellation();
                    int belowState=get(lx,below,lz);
                    if(belowState==AIR||belowState==WATER_SOURCE||belowState==LAVA_SOURCE){
                        stoneDepthOffset=below+1;
                        break;
                    }
                }
            }
            stoneDepthAbove++;
            if(oldState!=STONE)continue;
            final int by=y,above=stoneDepthAbove;
            final int below=stoneDepthOffset==Integer.MIN_VALUE?Integer.MAX_VALUE:y-stoneDepthOffset+1;
            final int columnWaterHeight=waterHeight;
            // SurfaceRules.Context#updateY는 셀마다 biome supplier를 다시 만든다.
            final McBiomeRegistry.Biome biomeData=McBiomeRegistry.get(fuzzyBiomeAt(wx,by,wz));
            final String biomeName=biomeData.name();
            McSurfaceContext c=new McSurfaceContext(){public int blockX(){return wx;}public int blockY(){return by;}public int blockZ(){return wz;}
                public int minY(){return MIN_Y;}public int height(){return MAX_Y-MIN_Y+1;}public int seaLevel(){return SEA_LEVEL;}
                public int stoneDepthAbove(){return above;}public int stoneDepthBelow(){return below;}
                public int waterHeight(){return columnWaterHeight;}public int surfaceDepth(){return depth;}
                public double secondarySurfaceNoise(){return surfaceRules.secondarySurfaceNoise(wx,wz);}
                public String biome(){return biomeName;}public float adjustedTemperature(){return adjustedBiomeTemperature(biomeData,wx,by,wz);}
                public int topBlockHeightExclusive(int x,int z){return pad[x+1+(z+1)*18]+1;}};
            String result=surfaceRules.evaluate(c);
            if(result!=null)set(lx,y,lz,blockForState(result));
        }
    }

    private float adjustedBiomeTemperature(McBiomeRegistry.Biome biome,int x,int y,int z){
        return McFreezeTopLayer.computeTemperature(x, y, z, biome.temperature(),
                biome.frozenModifier(), SEA_LEVEL);
    }

    private short[] generatePostCarvers(int cx, int cz) {
        requireRepresentableChunk(cx, cz);
        if (evidenceContext != null) evidenceContext.recordGenerationInvocation();
        checkCancellation();
        climateSampler.resetChunkCache();
        this.cx = cx;
        this.cz = cz;
        this.baseX = cx * 16;
        this.baseZ = cz * 16;
        surfaceRules.resetChunkCache();
        this.aquifer = new McAquifer(aquiferInputs, baseX, baseZ,
                surfaceRules::preliminarySurfaceLevel);
        this.blocks = new short[CHUNK_BLOCKS]; // 전부 AIR(0)
        if(PROFILE_TERRAIN){profileStageNanos=new long[CANONICAL_STAGE_NAMES.length];profileCheckpointNanos=System.nanoTime();}

        int[] heights = new int[256];

        // ── ① 기본 채움(+자갈/해변/탈루스/노출광석) ───────
        // 패딩 높이 그리드 [-1..16]²: 탈루스(비탈 자갈) 판정에 4방 이웃 H 필요(청크 독립).
        final int HP = HEIGHT_PAD;
        int[] heightsPad = new int[HP * HP];
        int fieldDescriptorHash = 0x811C9DC5;
        for (int lz = -1; lz <= 16; lz++) {
            checkCancellation();
            for (int lx = -1; lx <= 16; lx++) {
                checkCancellation();
                if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) {
                    heightsPad[lx + 1 + (lz + 1) * HP] =
                        densitySurfaceHeight(baseX + lx, baseZ + lz);
                }
            }
        }

        for (int lz = 0; lz < 16; lz++) {
            checkCancellation();
            for (int lx = 0; lx < 16; lx++) {
                checkCancellation();
                int wx = baseX + lx;
                int wz = baseZ + lz;
                int H = fillDensityColumn(lx,lz,wx,wz);
                heights[lx + lz * 16] = H;
                heightsPad[lx + 1 + (lz + 1) * HP] = H;
            }
        }

        if(stageHashes!=null){
            for(int lz=-1;lz<=16;lz++)for(int lx=-1;lx<=16;lx++){
                if (periodicCancellationCheck(lx + 1 + (lz + 1) * HP)) checkCancellation();
                int wx=baseX+lx,wz=baseZ+lz,mcHeight=heightsPad[lx+1+(lz+1)*HP];
                McClimate climate=climateSampler.sampleQuart(Math.floorDiv(wx,4),Math.floorDiv(mcHeight,4),Math.floorDiv(wz,4));
                for(long value:climate.values())fieldDescriptorHash=fnvLong(fieldDescriptorHash,value);
                fieldDescriptorHash=fnvLong(fieldDescriptorHash,Double.doubleToRawLongBits(densityAt(wx,mcHeight,wz)));
                }
        }
        checkpoint(String.format("0x%08x",fieldDescriptorHash));
        checkpoint(); // baseMass

        // Vanilla density is already the terrain shape. No synthetic ledges, arches,
        // volcanoes, cliffs, or project-defined canyon set pieces are applied here.
        checkpoint(); // sculpt

        // Aquifer가 기본 채움 단계에서 이미 물과 용암을 결정했다. 이 checkpoint는 기존
        // 10-stage golden schema를 유지하며 별도 project hydrology 변형은 수행하지 않는다.
        checkpoint(); // hydro: first block mutation after baseMass/sculpt
        for (int lz = 0; lz < 16; lz++) {
            checkCancellation();
            for (int lx = 0; lx < 16; lx++) {
                checkCancellation();
                int wx = baseX + lx;
                int wz = baseZ + lz;
                int i = lx + lz * 16;
                int H = heights[i];
                applyVanillaSurface(lx, lz, wx, wz, H, heightsPad);
            }
        }
        checkpoint(); // surface

        // Full final_density already owns spaghetti/noodle/entrance caves. Apply only the
        // separate configured cave/canyon carvers here, before ores and every structure layer.
        checkCancellation();
        final int[] carverIterations = {0};
        McConfiguredCarvers.carve(seed, cx, cz, aquifer,
                new McConfiguredCarvers.Target() {
                    @Override public int block(int localX, int y, int localZ) {
                        if (periodicCancellationCheck(carverIterations[0]++)) {
                            checkCancellation();
                        }
                        return get(localX, y, localZ);
                    }
                    @Override public void setBlock(
                            int localX, int y, int localZ, int block) {
                        if (periodicCancellationCheck(carverIterations[0]++)) {
                            checkCancellation();
                        }
                        set(localX, y, localZ, block);
                    }
                });
        checkCancellation();
        checkpoint(); // cavesCarvers
        return blocks;
    }

    private static final class DensityResources {
        private final FullDensityGraph graph;
        private final McAquifer.Inputs aquiferInputs;
        private final BoundedDensityColumnCache columns;

        private DensityResources(FullDensityGraph graph, McAquifer.Inputs aquiferInputs,
                BoundedDensityColumnCache columns) {
            this.graph = graph;
            this.aquiferInputs = aquiferInputs;
            this.columns = columns;
        }
    }

    /**
     * 인접 청크가 공유하는 density lattice만 작은 FIFO 상한 안에서 재사용한다. 값은 순수 함수라
     * 퇴출 순서가 지형 결과에 영향을 주지 않는다.
     */
    private static final class BoundedDensityColumnCache {
        private final int limit;
        private final java.util.concurrent.ConcurrentMap<Long,double[]> values =
                new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.concurrent.ConcurrentLinkedQueue<Long> insertionOrder =
                new java.util.concurrent.ConcurrentLinkedQueue<>();

        private BoundedDensityColumnCache(int limit) {
            this.limit = limit;
        }

        private double[] get(long key) {
            return values.get(key);
        }

        private double[] putIfAbsent(long key, double[] candidate) {
            double[] existing = values.putIfAbsent(key, candidate);
            if (existing != null) return existing;
            insertionOrder.add(key);
            while (values.size() > limit) {
                Long oldest = insertionOrder.poll();
                if (oldest == null) break;
                values.remove(oldest);
            }
            return candidate;
        }

        private int size() {
            return values.size();
        }
    }

    /**
     * Wrapper별 4096개 direct-mapped 항목이면 한 청크의 7×49×7 보간 모서리를 담는다.
     * 충돌은 재계산만 유발하고 좌표를 확인하므로 결과에는 영향을 주지 않는다.
     */
    static final class DensityEvaluationCache
            implements McDensityFunction.EvaluationCache {
        private static final int ENTRIES_PER_SLOT = 4096;
        private static final int ENTRY_MASK = ENTRIES_PER_SLOT - 1;
        private static final int ENTRY_STRIDE = 4;

        /**
         * Entry keys, interleaved {@code (x, y, z, present)} so that deciding a hit reads one
         * 16-byte run instead of four unrelated arrays.
         *
         * <p>AGENTS rule 10l: this probe is the single hottest leaf of the post-CARVERS stage —
         * every density-function evaluation of every cell asks it first. The former layout kept
         * {@code xs}, {@code ys}, {@code zs} and {@code present} in four separate arrays, so one
         * lookup touched four cache lines to answer a question whose payload is 16 bytes. Nothing
         * about the decision changes: the same index, the same key comparison in the same order,
         * the same {@code NaN} on a miss, and an all-zero array still reads as empty because the
         * presence word is part of the key run.</p>
         */
        private final int[] entries;
        private final double[] values;

        DensityEvaluationCache(int slotCount) {
            int length = Math.max(1, slotCount * ENTRIES_PER_SLOT);
            entries = new int[Math.multiplyExact(length, ENTRY_STRIDE)];
            values = new double[length];
        }

        @Override public double get(int slot, int x, int y, int z) {
            int index = index(slot, x, y, z);
            int key = index * ENTRY_STRIDE;
            if (entries[key + 3] == 0 || entries[key] != x || entries[key + 1] != y
                    || entries[key + 2] != z) {
                return Double.NaN;
            }
            return values[index];
        }

        @Override public void put(int slot, int x, int y, int z, double value) {
            int index = index(slot, x, y, z);
            int key = index * ENTRY_STRIDE;
            entries[key] = x;
            entries[key + 1] = y;
            entries[key + 2] = z;
            entries[key + 3] = 1;
            values[index] = value;
        }

        private int index(int slot, int x, int y, int z) {
            int hash = x * 0x8DA6B343 ^ y * 0xD8163841 ^ z * 0xCB1AB31F;
            hash ^= hash >>> 16;
            return slot * ENTRIES_PER_SLOT + (hash & ENTRY_MASK);
        }
    }

    // ── 디버깅/골든 midpoints 단언용 ────────────────────
    public static final class ColumnDebug {
        public final int surfaceHeight;
        public final long[] climateAxes;
        public final double density;
        public final int biome;
        public final int surfaceDepth, waterLevel;
        ColumnDebug(int surfaceHeight,long[] climateAxes,double density,int biome,int surfaceDepth,int waterLevel) {
            this.surfaceHeight=surfaceHeight;this.climateAxes=climateAxes.clone();this.density=density;
            this.biome=biome;this.surfaceDepth=surfaceDepth;this.waterLevel=waterLevel;
        }
    }

    public static ColumnDebug debugColumn(int seed, int wx, int wz) {
        ChunkGenerator g = new ChunkGenerator(seed);
        int h=g.densitySurfaceHeight(wx,wz);McClimate climate=g.climateSampler.sampleQuart(Math.floorDiv(wx,4),Math.floorDiv(h,4),Math.floorDiv(wz,4));
        int raw=g.climateSampler.biomeAtBlock(wx,h,wz);final int depth;try{depth=g.surfaceRules.surfaceDepth(wx,wz);}catch(IOException e){throw new IllegalStateException("근거 없음: surface noise",e);}
        return new ColumnDebug(h,climate.values(),g.densityAt(wx,h,wz),raw,depth,h<SEA_LEVEL?SEA_LEVEL:McSurfaceRuleEngine.NO_WATER);
    }
}
