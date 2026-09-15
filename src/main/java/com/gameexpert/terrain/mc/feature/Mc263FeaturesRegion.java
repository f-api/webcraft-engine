package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.LocatedProductionContext;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.LootProductionContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Writable 26.3 FEATURES view over the exact 5x5 CARVERS dependency region.
 *
 * <p>CARVERS block and three-dimensional biome inputs are defensively copied. Block storage is
 * copied again only when a feature first writes that chunk, so all placements in the nine-source
 * schedule observe one shared, live world while the caller's inputs remain immutable.</p>
 */
public final class Mc263FeaturesRegion {
    public static final int INPUT_RADIUS = 2;
    public static final int ACTIVE_SOURCE_RADIUS = 1;
    public static final int INPUT_CHUNK_COUNT = 25;

    /** Width of the region grid: {@code 2 * INPUT_RADIUS + 1}. */
    private static final int GRID_SPAN = INPUT_RADIUS * 2 + 1;

    /** Width of the source window: {@code 2 * ACTIVE_SOURCE_RADIUS + 1}. */
    private static final int SOURCE_SPAN = ACTIVE_SOURCE_RADIUS * 2 + 1;
    public static final int ACTIVE_SOURCE_COUNT = 9;
    public static final int BIOME_QUART_WIDTH = 4;
    public static final int BIOME_QUART_HEIGHT = Blocks.CHUNK_Y / 4;
    public static final int BIOME_COUNT_PER_CHUNK =
            BIOME_QUART_WIDTH * BIOME_QUART_WIDTH * BIOME_QUART_HEIGHT;
    public static final int SECTION_COUNT = Blocks.CHUNK_Y / 16;
    public static final int LIGHT_SECTION_BYTES = 16 * 16 * 16 / 2;

    /**
     * Every {@code facing} a randomizable container may legally carry. Barrels, dispensers and
     * hoppers use vanilla's six-direction {@code facing} property, so a loot sidecar is not
     * restricted to the four horizontals a chest uses.
     */
    static final Set<String> CONTAINER_FACINGS =
            Set.of("north", "east", "south", "west", "up", "down");

    private static final int MIN_QUART_Y = Math.floorDiv(Blocks.MIN_Y, 4);
    private static final int MAX_QUART_Y = Math.floorDiv(Blocks.MAX_Y, 4);
    private static final Mc263FeatureBlockState OUTSIDE_HEIGHT_STATE =
            Mc263FeatureBlockState.fromExact("minecraft:void_air");

    private final int targetChunkX;
    private final int targetChunkZ;
    /**
     * The region's inputs as a dense {@code (2*INPUT_RADIUS+1)^2} grid, row-major in
     * {@code (chunkX - targetChunkX, chunkZ - targetChunkZ)}.
     *
     * <p>AGENTS rule 10l: the constructor already proves the grid is complete and duplicate-free,
     * so membership is decided by the coordinate offsets alone. The former {@code Map<Long, …>}
     * boxed a {@code Long} key and hashed it on every block read, write and heightmap probe on the
     * warm seam. Indexing the same 25 slots is byte-neutral — the map was never iterated or
     * published, only looked up — and an out-of-grid coordinate still resolves to "absent".</p>
     */
    private final MutableChunk[] chunks;

    /** Lazily filled {@link #biomesForSource} results, row-major over the source window. */
    private final List<Set<String>> biomesBySource =
            new ArrayList<>(Collections.nCopies(SOURCE_SPAN * SOURCE_SPAN, null));
    private final IntPredicate worldSurfaceBlock;
    private final IntPredicate oceanFloorBlock;
    private final IntPredicate motionBlockingBlock;
    private int activeSourceChunkX;
    private int activeSourceChunkZ;
    private boolean sourceActive;
    private int copiedChunkCount;
    private final int initialHeightmapBlockLoads;
    private LootProductionContext productionContextIdentity;
    private final List<AuthenticatedStructureEntity> authenticatedStructureEntities =
            new ArrayList<>();

    public Mc263FeaturesRegion(int targetChunkX, int targetChunkZ,
            List<CarversChunk> carversChunks, IntPredicate worldSurfaceBlock,
            IntPredicate oceanFloorBlock, IntPredicate motionBlockingBlock) {
        this.targetChunkX = targetChunkX;
        this.targetChunkZ = targetChunkZ;
        requireSafeTargetCoordinate(targetChunkX, "X");
        requireSafeTargetCoordinate(targetChunkZ, "Z");
        this.worldSurfaceBlock = Objects.requireNonNull(
                worldSurfaceBlock, "worldSurfaceBlock");
        this.oceanFloorBlock = Objects.requireNonNull(oceanFloorBlock, "oceanFloorBlock");
        this.motionBlockingBlock = Objects.requireNonNull(
                motionBlockingBlock, "motionBlockingBlock");
        Objects.requireNonNull(carversChunks, "carversChunks");
        if (carversChunks.size() != INPUT_CHUNK_COUNT) {
            throw new IllegalArgumentException("FEATURES requires exactly 25 CARVERS chunks: "
                    + carversChunks.size());
        }

        Map<Long, CarversChunk> validated = new LinkedHashMap<>(INPUT_CHUNK_COUNT);
        for (CarversChunk input : carversChunks) {
            Objects.requireNonNull(input, "CARVERS chunk");
            if (!withinRadius(input.chunkX(), input.chunkZ(), targetChunkX, targetChunkZ,
                    INPUT_RADIUS)) {
                throw new IllegalArgumentException("CARVERS chunk lies outside target +/-2: "
                        + input.chunkX() + "," + input.chunkZ());
            }
            long key = chunkKey(input.chunkX(), input.chunkZ());
            if (validated.putIfAbsent(key, input) != null) {
                throw new IllegalArgumentException("duplicate CARVERS chunk: "
                        + input.chunkX() + "," + input.chunkZ());
            }
        }
        for (int chunkX = targetChunkX - INPUT_RADIUS;
                chunkX <= targetChunkX + INPUT_RADIUS; chunkX++) {
            for (int chunkZ = targetChunkZ - INPUT_RADIUS;
                    chunkZ <= targetChunkZ + INPUT_RADIUS; chunkZ++) {
                if (!validated.containsKey(chunkKey(chunkX, chunkZ))) {
                    throw new IllegalArgumentException("missing CARVERS chunk: "
                            + chunkX + "," + chunkZ);
                }
            }
        }
        MutableChunk[] loaded = new MutableChunk[GRID_SPAN * GRID_SPAN];
        int heightmapBlockLoads = 0;
        for (Map.Entry<Long, CarversChunk> entry : validated.entrySet()) {
            CarversChunk input = entry.getValue();
            MutableChunk chunk = new MutableChunk(input, this.worldSurfaceBlock,
                    this.oceanFloorBlock, this.motionBlockingBlock);
            heightmapBlockLoads += chunk.initialHeightmapBlockLoads;
            loaded[chunkSlot(input.chunkX(), input.chunkZ())] = chunk;
        }
        initialHeightmapBlockLoads = heightmapBlockLoads;
        chunks = loaded;
    }

    public int targetChunkX() {
        return targetChunkX;
    }

    public int targetChunkZ() {
        return targetChunkZ;
    }

    public int minGenerationY() {
        return Blocks.MIN_Y;
    }

    public int generationDepth() {
        return Blocks.CHUNK_Y;
    }

    public int blockId(int blockX, int blockY, int blockZ) {
        requireBuildHeight(blockY);
        MutableChunk chunk = requireChunk(Math.floorDiv(blockX, Blocks.CHUNK_X),
                Math.floorDiv(blockZ, Blocks.CHUNK_Z));
        int localX = Math.floorMod(blockX, Blocks.CHUNK_X);
        int localZ = Math.floorMod(blockZ, Blocks.CHUNK_Z);
        return Short.toUnsignedInt(chunk.blocks()[Blocks.blockIndex(localX, blockY, localZ)]);
    }

    /**
     * Returns the exact live state, including air kind, fluid level, and block properties. Reads
     * outside build height mirror {@code ProtoChunk#getBlockState} and return VOID_AIR.
     */
    public Mc263FeatureBlockState blockState(int blockX, int blockY, int blockZ) {
        if (blockY < Blocks.MIN_Y || blockY > Blocks.MAX_Y) return OUTSIDE_HEIGHT_STATE;
        MutableChunk chunk = requireChunk(Math.floorDiv(blockX, Blocks.CHUNK_X),
                Math.floorDiv(blockZ, Blocks.CHUNK_Z));
        int localX = Math.floorMod(blockX, Blocks.CHUNK_X);
        int localZ = Math.floorMod(blockZ, Blocks.CHUNK_Z);
        return chunk.blockState(Blocks.blockIndex(localX, blockY, localZ));
    }

    /**
     * Returns the immutable post-CARVERS light snapshot value used by FEATURES. Missing sky and
     * block DataLayers retain vanilla's fresh-chunk defaults of 15 and 0 respectively.
     */
    public int rawBrightness(int blockX, int blockY, int blockZ, int skyDarken) {
        if (skyDarken < 0 || skyDarken > 15) {
            throw new IllegalArgumentException("sky darken is outside 0..15: " + skyDarken);
        }
        MutableChunk chunk = requireChunk(Math.floorDiv(blockX, Blocks.CHUNK_X),
                Math.floorDiv(blockZ, Blocks.CHUNK_Z));
        if (blockY < Blocks.MIN_Y || blockY > Blocks.MAX_Y) {
            return Math.max(0, 15 - skyDarken);
        }
        int localX = Math.floorMod(blockX, Blocks.CHUNK_X);
        int localZ = Math.floorMod(blockZ, Blocks.CHUNK_Z);
        int sky = chunk.lightSnapshot.sky(localX, blockY, localZ);
        int block = chunk.lightSnapshot.block(localX, blockY, localZ);
        return Math.max(block, Math.max(0, sky - skyDarken));
    }

    /** Exact immutable BLOCK-light layer value consumed by freeze-top-layer placement. */
    public int blockBrightness(int blockX, int blockY, int blockZ) {
        MutableChunk chunk = requireChunk(Math.floorDiv(blockX, Blocks.CHUNK_X),
                Math.floorDiv(blockZ, Blocks.CHUNK_Z));
        if (blockY < Blocks.MIN_Y || blockY > Blocks.MAX_Y) return 0;
        return chunk.lightSnapshot.block(Math.floorMod(blockX, Blocks.CHUNK_X), blockY,
                Math.floorMod(blockZ, Blocks.CHUNK_Z));
    }

    /** Returns the exact stored three-dimensional biome for the containing quart cell. */
    public String biomeKey(int blockX, int blockY, int blockZ) {
        return noiseBiomeKey(Math.floorDiv(blockX, 4), Math.floorDiv(blockY, 4),
                Math.floorDiv(blockZ, 4));
    }

    /** Mirrors {@code ChunkAccess#getNoiseBiome}, including the pinned quart-Y clamp -16..79. */
    public String noiseBiomeKey(int quartX, int quartY, int quartZ) {
        MutableChunk chunk = requireChunk(Math.floorDiv(quartX, BIOME_QUART_WIDTH),
                Math.floorDiv(quartZ, BIOME_QUART_WIDTH));
        int localQuartX = Math.floorMod(quartX, BIOME_QUART_WIDTH);
        int localQuartZ = Math.floorMod(quartZ, BIOME_QUART_WIDTH);
        int clampedQuartY = Math.max(MIN_QUART_Y, Math.min(MAX_QUART_Y, quartY));
        return chunk.biomes()[biomeIndex(localQuartX, clampedQuartY - MIN_QUART_Y,
                localQuartZ)];
    }

    /** Vanilla heightmap convention: the first available Y above the top matching block. */
    public int worldSurfaceWg(int blockX, int blockZ) {
        MutableChunk chunk = requireChunk(Math.floorDiv(blockX, Blocks.CHUNK_X),
                Math.floorDiv(blockZ, Blocks.CHUNK_Z));
        int localX = Math.floorMod(blockX, Blocks.CHUNK_X);
        int localZ = Math.floorMod(blockZ, Blocks.CHUNK_Z);
        return chunk.worldSurface[localX + localZ * Blocks.CHUNK_X];
    }

    /** Vanilla heightmap convention: the first available Y above the top matching block. */
    public int oceanFloorWg(int blockX, int blockZ) {
        MutableChunk chunk = requireChunk(Math.floorDiv(blockX, Blocks.CHUNK_X),
                Math.floorDiv(blockZ, Blocks.CHUNK_Z));
        int localX = Math.floorMod(blockX, Blocks.CHUNK_X);
        int localZ = Math.floorMod(blockZ, Blocks.CHUNK_Z);
        return chunk.oceanFloor[localX + localZ * Blocks.CHUNK_X];
    }

    /** Vanilla heightmap convention: the first available Y above the top matching block. */
    public int motionBlocking(int blockX, int blockZ) {
        MutableChunk chunk = requireChunk(Math.floorDiv(blockX, Blocks.CHUNK_X),
                Math.floorDiv(blockZ, Blocks.CHUNK_Z));
        int localX = Math.floorMod(blockX, Blocks.CHUNK_X);
        int localZ = Math.floorMod(blockZ, Blocks.CHUNK_Z);
        return chunk.motionBlocking[localX + localZ * Blocks.CHUNK_X];
    }

    /** Mirrors WorldGenLevel.ensureCanWrite for the currently executing source chunk. */
    public boolean ensureCanWrite(int blockX, int blockY, int blockZ) {
        if (!sourceActive || blockY < Blocks.MIN_Y || blockY > Blocks.MAX_Y) return false;
        int chunkX = Math.floorDiv(blockX, Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv(blockZ, Blocks.CHUNK_Z);
        return withinRadius(chunkX, chunkZ, activeSourceChunkX, activeSourceChunkZ,
                ACTIVE_SOURCE_RADIUS) && chunkSlot(chunkX, chunkZ) >= 0;
    }

    /**
     * Tries one unsigned-16 block write. A valid coordinate outside the active source radius is a
     * normal feature-placement rejection, not an exception; no state or sidecar is then mutated.
     */
    public boolean setBlockId(int blockX, int blockY, int blockZ, int blockId) {
        if (blockId < 0 || blockId > 0xffff) {
            throw new IllegalArgumentException("block ID is outside unsigned-16 range: " + blockId);
        }
        Mc263FeatureBlockState newState = Mc263FeatureBlockState.defaultForId(blockId);
        requireBuildHeight(blockY);
        if (!ensureCanWrite(blockX, blockY, blockZ)) return false;
        MutableChunk chunk = requireChunk(Math.floorDiv(blockX, Blocks.CHUNK_X),
                Math.floorDiv(blockZ, Blocks.CHUNK_Z));
        int localX = Math.floorMod(blockX, Blocks.CHUNK_X);
        int localZ = Math.floorMod(blockZ, Blocks.CHUNK_Z);
        int blockIndex = Blocks.blockIndex(localX, blockY, localZ);
        int oldId = Short.toUnsignedInt(chunk.blocks()[blockIndex]);
        Mc263FeatureBlockState oldState = chunk.blockStateOrNull(blockIndex);
        boolean semanticChange = oldId != blockId || chunk.hasExactState(blockIndex)
                || chunk.hasLiveCapability(blockIndex) || chunk.hasOwnership(blockIndex)
                || chunk.hasArchaeologyLoot(blockIndex);
        if (!semanticChange) return false;
        if (oldId != blockId) {
            if (chunk.mutableBlocks == null) {
                chunk.mutableBlocks = chunk.carversBlocks.clone();
                copiedChunkCount++;
            }
            chunk.mutableBlocks[blockIndex] = (short) blockId;
        }
        chunk.acceptLegacyIdWrite(blockIndex);
        updateHeightmap(chunk, chunk.worldSurface, worldSurfaceBlock,
                HeightmapKind.WORLD_SURFACE, localX, blockY, localZ, oldId, blockId,
                oldState, newState);
        updateHeightmap(chunk, chunk.oceanFloor, oceanFloorBlock,
                HeightmapKind.OCEAN_FLOOR, localX, blockY, localZ, oldId, blockId,
                oldState, newState);
        updateHeightmap(chunk, chunk.motionBlocking, motionBlockingBlock,
                HeightmapKind.MOTION_BLOCKING, localX, blockY, localZ, oldId, blockId,
                oldState, newState);
        return true;
    }

    /**
     * Attempts one exact typed write. An accepted in-bounds write returns true even when its
     * unsigned-16 ID or full state equals the old value, matching WorldGenRegion rather than the
     * legacy ID-only convenience method above.
     */
    public boolean setBlockState(int blockX, int blockY, int blockZ,
            Mc263FeatureBlockState state) {
        return trySetBlockState(blockX, blockY, blockZ, state, false, 0L);
    }

    /**
     * Atomically attempts one exact typed write and assigns the final procedural owner. Rejected
     * writes leave both the block and the existing owner untouched; an accepted overlay replaces
     * both together, including when the same owner updates its own cell.
     */
    public boolean trySetOwnedBlockState(int blockX, int blockY, int blockZ,
            Mc263FeatureBlockState state, long owner) {
        return trySetBlockState(blockX, blockY, blockZ, state, true, owner);
    }

    /** Parses and atomically attempts one exact namespaced owned-state write. */
    public boolean trySetOwnedBlockState(int blockX, int blockY, int blockZ,
            String exactState, long owner) {
        return trySetOwnedBlockState(blockX, blockY, blockZ,
                Mc263FeatureBlockState.fromExact(exactState), owner);
    }

    private boolean trySetBlockState(int blockX, int blockY, int blockZ,
            Mc263FeatureBlockState state, boolean owned, long owner) {
        Objects.requireNonNull(state, "state");
        if (blockY < Blocks.MIN_Y || blockY > Blocks.MAX_Y
                || !ensureCanWrite(blockX, blockY, blockZ)) return false;
        MutableChunk chunk = requireChunk(Math.floorDiv(blockX, Blocks.CHUNK_X),
                Math.floorDiv(blockZ, Blocks.CHUNK_Z));
        int localX = Math.floorMod(blockX, Blocks.CHUNK_X);
        int localZ = Math.floorMod(blockZ, Blocks.CHUNK_Z);
        int blockIndex = Blocks.blockIndex(localX, blockY, localZ);
        int oldId = Short.toUnsignedInt(chunk.blocks()[blockIndex]);
        Mc263FeatureBlockState oldState = chunk.blockStateOrNull(blockIndex);
        BlockEntityCapabilityReceipt capabilityReceipt = state.capability() ==
                Mc263FeatureBlockState.Capability.NONE ? null
                : new BlockEntityCapabilityReceipt(localX, blockY, localZ,
                        state.capability(), state.exactState());
        if (oldId != state.blockId()) {
            if (chunk.mutableBlocks == null) {
                chunk.mutableBlocks = chunk.carversBlocks.clone();
                copiedChunkCount++;
            }
            chunk.mutableBlocks[blockIndex] = (short) state.blockId();
        }
        chunk.setExactState(blockIndex, state);
        chunk.acceptBlockEntityWrite(blockIndex, state, capabilityReceipt);
        chunk.acceptOwnershipWrite(blockIndex, owned, owner);
        updateHeightmap(chunk, chunk.worldSurface, worldSurfaceBlock,
                HeightmapKind.WORLD_SURFACE, localX, blockY, localZ, oldId, state.blockId(),
                oldState, state);
        updateHeightmap(chunk, chunk.oceanFloor, oceanFloorBlock,
                HeightmapKind.OCEAN_FLOOR, localX, blockY, localZ, oldId, state.blockId(),
                oldState, state);
        updateHeightmap(chunk, chunk.motionBlocking, motionBlockingBlock,
                HeightmapKind.MOTION_BLOCKING, localX, blockY, localZ,
                oldId, state.blockId(), oldState, state);
        return true;
    }

    /** Parses and attempts one exact namespaced state write. */
    public boolean setBlockState(int blockX, int blockY, int blockZ, String exactState) {
        return setBlockState(blockX, blockY, blockZ,
                Mc263FeatureBlockState.fromExact(exactState));
    }

    /**
     * Appends one postprocessing cell to its ProtoChunk section list. Duplicate positions are
     * deliberately preserved in insertion order within that section. This project adapter is
     * bounded to the active source write view; rejected coordinates are a false/no-op.
     */
    public boolean markForPostprocessing(int blockX, int blockY, int blockZ) {
        if (!ensureCanWrite(blockX, blockY, blockZ)) return false;
        MutableChunk chunk = requireChunk(Math.floorDiv(blockX, Blocks.CHUNK_X),
                Math.floorDiv(blockZ, Blocks.CHUNK_Z));
        int localX = Math.floorMod(blockX, Blocks.CHUNK_X);
        int localZ = Math.floorMod(blockZ, Blocks.CHUNK_Z);
        int section = Math.floorDiv(blockY - Blocks.MIN_Y, 16);
        chunk.postprocessBySection.get(section)
                .add(new PostprocessMark(localX, blockY, localZ));
        return true;
    }

    /**
     * Schedules one chunk-local block tick. Vanilla tick identity is block type plus position, so
     * an exact duplicate retains the first insertion position; ProtoChunkTicks stores delay zero.
     * This project adapter is bounded to the active source write view; rejected coordinates are a
     * false/no-op.
     */
    public boolean scheduleBlockTick(int blockX, int blockY, int blockZ, int blockId, int delay) {
        if (blockId < 0 || blockId > 0xffff) {
            throw new IllegalArgumentException("block ID is outside unsigned-16 range: " + blockId);
        }
        if (delay < 0) throw new IllegalArgumentException("block tick delay must be non-negative");
        if (!ensureCanWrite(blockX, blockY, blockZ)) return false;
        MutableChunk chunk = requireChunk(Math.floorDiv(blockX, Blocks.CHUNK_X),
                Math.floorDiv(blockZ, Blocks.CHUNK_Z));
        int localX = Math.floorMod(blockX, Blocks.CHUNK_X);
        int localZ = Math.floorMod(blockZ, Blocks.CHUNK_Z);
        int packed = Blocks.blockIndex(localX, blockY, localZ);
        String blockKey = semanticKeyForLegacyId(blockId);
        TickKey key = new TickKey(packed, blockKey);
        if (chunk.scheduledBlockTicks.containsKey(key)) return false;
        chunk.scheduledBlockTicks.put(key, ScheduledBlockTick.resolved(localX, blockY, localZ,
                blockId, blockKey, 0, 0, chunk.scheduledBlockTicks.size()));
        return true;
    }

    /** Schedules a tick whose identity retains its semantic block type, including CAVE_AIR. */
    public boolean scheduleBlockTick(int blockX, int blockY, int blockZ, String blockKey,
            int delay) {
        String semanticKey = requireNamespacedKey(blockKey, "scheduled block tick type");
        Mc263FeatureBlockState state =
                Mc263FeatureBlockState.forSemanticBlockKey(semanticKey);
        if (delay < 0) throw new IllegalArgumentException("block tick delay must be non-negative");
        if (!ensureCanWrite(blockX, blockY, blockZ)) return false;
        MutableChunk chunk = requireChunk(Math.floorDiv(blockX, Blocks.CHUNK_X),
                Math.floorDiv(blockZ, Blocks.CHUNK_Z));
        int localX = Math.floorMod(blockX, Blocks.CHUNK_X);
        int localZ = Math.floorMod(blockZ, Blocks.CHUNK_Z);
        int packed = Blocks.blockIndex(localX, blockY, localZ);
        TickKey key = new TickKey(packed, semanticKey);
        if (chunk.scheduledBlockTicks.containsKey(key)) return false;
        chunk.scheduledBlockTicks.put(key, ScheduledBlockTick.resolved(localX, blockY, localZ,
                state.blockId(), semanticKey, 0, 0, chunk.scheduledBlockTicks.size()));
        return true;
    }

    /**
     * Schedules one exact fluid type in the loaded decoration view. Unlike block writes, geode
     * crack fluid ticks may target any loaded +/-2 input chunk. Identity is position plus the
     * exact fluid registry key and the first insertion wins with ProtoChunk delay zero.
     */
    public boolean scheduleFluidTick(int blockX, int blockY, int blockZ, String fluidKey,
            int delay) {
        String semanticKey = Mc263FeatureBlockState.requireFluidTickKey(fluidKey);
        if (delay < 0) throw new IllegalArgumentException("fluid tick delay must be non-negative");
        if (!sourceActive || blockY < Blocks.MIN_Y || blockY > Blocks.MAX_Y) return false;
        int chunkX = Math.floorDiv(blockX, Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv(blockZ, Blocks.CHUNK_Z);
        MutableChunk chunk = chunkOrNull(chunkX, chunkZ);
        if (chunk == null) return false;
        int localX = Math.floorMod(blockX, Blocks.CHUNK_X);
        int localZ = Math.floorMod(blockZ, Blocks.CHUNK_Z);
        int packed = Blocks.blockIndex(localX, blockY, localZ);
        TickKey key = new TickKey(packed, semanticKey);
        if (!chunk.scheduledFluidTickPresence.add(key)) return false;
        chunk.scheduledFluidTicks.add(ScheduledFluidTick.resolved(localX, blockY, localZ,
                semanticKey, 0, 0, chunk.scheduledFluidTicks.size()));
        return true;
    }

    boolean hasScheduledBlockTick(int blockX, int blockY, int blockZ, String blockKey) {
        String semanticKey = requireNamespacedKey(blockKey, "scheduled block tick type");
        if (blockY < Blocks.MIN_Y || blockY > Blocks.MAX_Y) return false;
        MutableChunk chunk = chunkOrNull(Math.floorDiv(blockX, Blocks.CHUNK_X),
                Math.floorDiv(blockZ, Blocks.CHUNK_Z));
        if (chunk == null) return false;
        int packed = Blocks.blockIndex(Math.floorMod(blockX, Blocks.CHUNK_X), blockY,
                Math.floorMod(blockZ, Blocks.CHUNK_Z));
        return chunk.scheduledBlockTicks.containsKey(new TickKey(packed, semanticKey));
    }

    boolean hasScheduledFluidTick(int blockX, int blockY, int blockZ, String fluidKey) {
        String semanticKey = Mc263FeatureBlockState.requireFluidTickKey(fluidKey);
        if (blockY < Blocks.MIN_Y || blockY > Blocks.MAX_Y) return false;
        MutableChunk chunk = chunkOrNull(Math.floorDiv(blockX, Blocks.CHUNK_X),
                Math.floorDiv(blockZ, Blocks.CHUNK_Z));
        if (chunk == null) return false;
        int packed = Blocks.blockIndex(Math.floorMod(blockX, Blocks.CHUNK_X), blockY,
                Math.floorMod(blockZ, Blocks.CHUNK_Z));
        return chunk.scheduledFluidTickPresence.contains(new TickKey(packed, semanticKey));
    }

    public boolean hasRandomizableContainer(int blockX, int blockY, int blockZ) {
        return liveCapability(blockX, blockY, blockZ)
                == Mc263FeatureBlockState.Capability.RANDOMIZABLE_CONTAINER;
    }

    public boolean hasSpawnerBlockEntity(int blockX, int blockY, int blockZ) {
        return liveCapability(blockX, blockY, blockZ)
                == Mc263FeatureBlockState.Capability.SPAWNER;
    }

    public boolean hasPotentSulfurBlockEntity(int blockX, int blockY, int blockZ) {
        return liveCapability(blockX, blockY, blockZ)
                == Mc263FeatureBlockState.Capability.POTENT_SULFUR;
    }

    public boolean hasBrushableBlockEntity(int blockX, int blockY, int blockZ) {
        return liveCapability(blockX, blockY, blockZ)
                == Mc263FeatureBlockState.Capability.BRUSHABLE;
    }

    public boolean hasSculkCatalystBlockEntity(int blockX, int blockY, int blockZ) {
        return liveCapability(blockX, blockY, blockZ)
                == Mc263FeatureBlockState.Capability.SCULK_CATALYST;
    }

    public boolean hasSculkSensorBlockEntity(int blockX, int blockY, int blockZ) {
        return liveCapability(blockX, blockY, blockZ)
                == Mc263FeatureBlockState.Capability.SCULK_SENSOR;
    }

    public boolean hasSculkShriekerBlockEntity(int blockX, int blockY, int blockZ) {
        return liveCapability(blockX, blockY, blockZ)
                == Mc263FeatureBlockState.Capability.SCULK_SHRIEKER;
    }

    /** Appends one vanilla world-generation bee to a live bee nest. */
    public boolean storeWorldgenBee(int blockX, int blockY, int blockZ, int ticksInHive) {
        if (ticksInHive < 0 || ticksInHive > 598) {
            throw new IllegalArgumentException("worldgen bee ticks in hive are outside 0..598: "
                    + ticksInHive);
        }
        if (!ensureCanWrite(blockX, blockY, blockZ)) return false;
        MutableChunk chunk = requireChunk(Math.floorDiv(blockX, Blocks.CHUNK_X),
                Math.floorDiv(blockZ, Blocks.CHUNK_Z));
        int localX = Math.floorMod(blockX, Blocks.CHUNK_X);
        int localZ = Math.floorMod(blockZ, Blocks.CHUNK_Z);
        int packed = Blocks.blockIndex(localX, blockY, localZ);
        BeehiveNest nest = chunk.beehiveNests.get(packed);
        if (chunk.liveCapabilities.get(packed)
                != Mc263FeatureBlockState.Capability.BEEHIVE || nest == null) return false;
        nest.addOccupant(new BeehiveOccupant(ticksInHive));
        return true;
    }

    /** Exact live section-palette equivalent used to gate sculk growth-inhibitor scans. */
    public boolean sectionMayContainSculkGrowthInhibitor(
            int sectionX, int sectionY, int sectionZ) {
        int minimumY = sectionY * 16;
        int maximumY = minimumY + 15;
        if (maximumY < Blocks.MIN_Y || minimumY > Blocks.MAX_Y) return false;
        MutableChunk chunk = requireChunk(sectionX, sectionZ);
        int fromY = Math.max(minimumY, Blocks.MIN_Y);
        int toY = Math.min(maximumY, Blocks.MAX_Y);
        for (int blockY = fromY; blockY <= toY; blockY++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    if (chunk.blockState(Blocks.blockIndex(localX, blockY, localZ))
                            .sculkGrowthInhibitor()) return true;
                }
            }
        }
        return false;
    }

    /** Records final loot together with the authority's complete immutable level context. */
    public boolean setChestLoot(int blockX, int blockY, int blockZ, String lootTable,
            long lootSeed, LootProductionContext productionContext) {
        String canonicalTable = requireMinecraftKey(lootTable, "loot table");
        LootProductionContext contextIdentity = requireProductionLootContext(
                canonicalTable, blockX, blockY, blockZ, productionContext,
                productionContextIdentity);
        if (!ensureCanWrite(blockX, blockY, blockZ)) return false;
        MutableChunk chunk = requireChunk(Math.floorDiv(blockX, Blocks.CHUNK_X),
                Math.floorDiv(blockZ, Blocks.CHUNK_Z));
        int localX = Math.floorMod(blockX, Blocks.CHUNK_X);
        int localZ = Math.floorMod(blockZ, Blocks.CHUNK_Z);
        int packed = Blocks.blockIndex(localX, blockY, localZ);
        if (chunk.liveCapabilities.get(packed)
                != Mc263FeatureBlockState.Capability.RANDOMIZABLE_CONTAINER) return false;
        String facing = chunk.blockState(packed).chestFacing();
        productionContextIdentity = contextIdentity;
        chunk.chestLoot.put(packed,
                new ChestLoot(localX, blockY, localZ, facing, canonicalTable, lootSeed,
                        productionContext));
        return true;
    }

    /** Records final spawner contents only while a live accepted spawner capability exists. */
    public boolean setSpawnerMob(int blockX, int blockY, int blockZ, String entityType) {
        requireMinecraftKey(entityType, "entity type");
        if (!ensureCanWrite(blockX, blockY, blockZ)) return false;
        MutableChunk chunk = requireChunk(Math.floorDiv(blockX, Blocks.CHUNK_X),
                Math.floorDiv(blockZ, Blocks.CHUNK_Z));
        int localX = Math.floorMod(blockX, Blocks.CHUNK_X);
        int localZ = Math.floorMod(blockZ, Blocks.CHUNK_Z);
        int packed = Blocks.blockIndex(localX, blockY, localZ);
        if (chunk.liveCapabilities.get(packed)
                != Mc263FeatureBlockState.Capability.SPAWNER) return false;
        chunk.spawnerMobs.put(packed,
                new SpawnerMob(localX, blockY, localZ, entityType));
        return true;
    }

    /** Records final archaeology loot only while a live accepted brushable capability exists. */
    public boolean setArchaeologyLoot(int blockX, int blockY, int blockZ, String lootTable,
            long lootSeed) {
        String canonicalTable = requireMinecraftKey(lootTable, "archaeology loot table");
        if (!isSupportedArchaeologyTable(canonicalTable)) {
            throw new IllegalArgumentException("unsupported 26.3 archaeology loot table: "
                    + lootTable);
        }
        if (!ensureCanWrite(blockX, blockY, blockZ)) return false;
        MutableChunk chunk = requireChunk(Math.floorDiv(blockX, Blocks.CHUNK_X),
                Math.floorDiv(blockZ, Blocks.CHUNK_Z));
        int localX = Math.floorMod(blockX, Blocks.CHUNK_X);
        int localZ = Math.floorMod(blockZ, Blocks.CHUNK_Z);
        int packed = Blocks.blockIndex(localX, blockY, localZ);
        if (chunk.liveCapabilities.get(packed)
                != Mc263FeatureBlockState.Capability.BRUSHABLE) return false;
        chunk.archaeologyLoot.put(packed,
                new ArchaeologyLoot(localX, blockY, localZ, canonicalTable, lootSeed));
        return true;
    }

    /**
     * Atomically settles one canonical hardcoded-structure contribution into the live region.
     * All coordinates, exact states, capabilities, keys, duplicate facts, and BENT/ENTS payloads
     * are validated against the final staged blocks before any chunk is copied or mutated.
     */
    StructureBatchSettlement settleStructureBatch(StructureBatch batch) {
        Objects.requireNonNull(batch, "structure batch");
        if (!sourceActive) {
            throw new IllegalStateException("structure batch requires an active FEATURES source");
        }
        LootProductionContext batchContextIdentity =
                requireBatchProductionContextIdentity(batch);

        List<PreparedStructureBlock> orderedBlocks = new ArrayList<>(batch.blocks().size());
        LinkedHashMap<DestinationPosition, PreparedStructureBlock> finalBlocks =
                new LinkedHashMap<>();
        for (StructureBlockWrite write : batch.blocks()) {
            requireStructureDestination(write.blockX(), write.blockY(), write.blockZ());
            Mc263FeatureBlockState state = Mc263FeatureBlockState.fromExact(write.exactState());
            DestinationPosition destination = destination(
                    write.blockX(), write.blockY(), write.blockZ());
            PreparedStructureBlock prepared = new PreparedStructureBlock(write.blockX(),
                    write.blockY(), write.blockZ(), state, write.owner());
            orderedBlocks.add(prepared);
            finalBlocks.put(destination, prepared);
        }

        LinkedHashMap<DestinationPosition, PreparedStructureLoot> loot = new LinkedHashMap<>();
        for (StructureLoot value : batch.loot()) {
            requireStructureDestination(value.blockX(), value.blockY(), value.blockZ());
            String table = requireMinecraftKey(value.lootTable(), "structure loot table");
            LootProductionContext productionContext = value.productionContext();
            DestinationPosition destination = destination(
                    value.blockX(), value.blockY(), value.blockZ());
            Mc263FeatureBlockState state = stagedStructureState(finalBlocks, destination,
                    value.blockX(), value.blockY(), value.blockZ());
            if (state.capability()
                    != Mc263FeatureBlockState.Capability.RANDOMIZABLE_CONTAINER) {
                throw new IllegalArgumentException("structure LOOT lacks container capability at "
                        + value.blockX() + "," + value.blockY() + "," + value.blockZ());
            }
            PreparedStructureLoot prepared = new PreparedStructureLoot(value.blockX(),
                    value.blockY(), value.blockZ(), table, value.lootSeed(), state.chestFacing(),
                    productionContext);
            PreparedStructureLoot existing = loot.putIfAbsent(destination, prepared);
            if (existing != null && !existing.equals(prepared)) {
                throw new IllegalArgumentException("conflicting structure LOOT at "
                        + value.blockX() + "," + value.blockY() + "," + value.blockZ());
            }
            requireExistingLootEquality(finalBlocks, destination, prepared);
        }

        LinkedHashMap<DestinationPosition, PreparedStructureArchaeology> archaeology =
                new LinkedHashMap<>();
        for (StructureArchaeology value : batch.archaeology()) {
            requireStructureDestination(value.blockX(), value.blockY(), value.blockZ());
            String table = requireMinecraftKey(value.lootTable(),
                    "structure archaeology loot table");
            if (!isSupportedArchaeologyTable(table)) {
                throw new IllegalArgumentException("unsupported 26.3 archaeology loot table: "
                        + value.lootTable());
            }
            DestinationPosition destination = destination(
                    value.blockX(), value.blockY(), value.blockZ());
            Mc263FeatureBlockState state = stagedStructureState(finalBlocks, destination,
                    value.blockX(), value.blockY(), value.blockZ());
            if (state.capability() != Mc263FeatureBlockState.Capability.BRUSHABLE) {
                throw new IllegalArgumentException(
                        "structure ARCH lacks brushable capability at "
                                + value.blockX() + "," + value.blockY() + ","
                                + value.blockZ());
            }
            PreparedStructureArchaeology prepared = new PreparedStructureArchaeology(
                    value.blockX(), value.blockY(), value.blockZ(), table, value.lootSeed());
            PreparedStructureArchaeology existing = archaeology.putIfAbsent(
                    destination, prepared);
            if (existing != null && !existing.equals(prepared)) {
                throw new IllegalArgumentException("conflicting structure ARCH at "
                        + value.blockX() + "," + value.blockY() + "," + value.blockZ());
            }
            requireExistingArchaeologyEquality(finalBlocks, destination, prepared);
        }

        LinkedHashMap<DestinationPosition, Mc263FinalChunkSidecars.BlockEntity> blockEntities =
                new LinkedHashMap<>();
        for (StructureBentEvidence value : batch.blockEntities()) {
            requireStructureDestination(value.blockX(), value.blockY(), value.blockZ());
            DestinationPosition destination = destination(
                    value.blockX(), value.blockY(), value.blockZ());
            Mc263FeatureBlockState state = stagedStructureState(finalBlocks, destination,
                    value.blockX(), value.blockY(), value.blockZ());
            Mc263FinalChunkSidecars.BlockEntity prepared =
                    new Mc263FinalChunkSidecars.BlockEntity(destination.packedPosition(),
                            value.blockIdentity(), value.entityType(), value.canonicalNbt());
            if (!state.blockKey().equals(prepared.blockIdentity())) {
                throw new IllegalArgumentException("structure BENT block identity mismatch at "
                        + value.blockX() + "," + value.blockY() + "," + value.blockZ());
            }
            Mc263FinalChunkSidecars.BlockEntity existing = blockEntities.putIfAbsent(
                    destination, prepared);
            if (existing != null && !existing.equals(prepared)) {
                throw new IllegalArgumentException("conflicting structure BENT at "
                        + value.blockX() + "," + value.blockY() + "," + value.blockZ());
            }
        }

        LinkedHashMap<DestinationPosition,
                List<Mc263FinalChunkSidecars.StructureEntity>> entities = new LinkedHashMap<>();
        LinkedHashSet<Mc263FinalChunkSidecars.StructureEntity> entityRecords =
                new LinkedHashSet<>();
        for (Mc263FinalChunkSidecars.StructureEntity value : batch.entities()) {
            int blockX = structureEntityBlockCoordinate(value.x(), "structure entity X");
            int blockY = structureEntityBlockCoordinate(value.y(), "structure entity Y");
            int blockZ = structureEntityBlockCoordinate(value.z(), "structure entity Z");
            requireStructureDestination(blockX, blockY, blockZ);
            DestinationPosition destination = destination(blockX, blockY, blockZ);
            if (!entityRecords.add(value)) {
                throw new IllegalArgumentException("duplicate structure ENTS at "
                        + entityPosition(value));
            }
            entities.computeIfAbsent(destination, ignored -> new ArrayList<>()).add(value);
        }

        ArrayList<PreparedStructureFluidTick> fluidTicks = new ArrayList<>(
                batch.fluidTicks().size());
        long expectedFluidTickOrder = 0L;
        for (StructureFluidTick value : batch.fluidTicks()) {
            requireStructureFluidTickDestination(
                    value.blockX(), value.blockY(), value.blockZ());
            String fluidKey = Mc263FeatureBlockState.requireFluidTickKey(value.fluidKey());
            if (value.delay() < 0) {
                throw new IllegalArgumentException(
                        "structure fluid tick delay must be non-negative");
            }
            Mc263FinalChunkSidecars.TickPriority.fromValue(value.priority());
            if (value.subTickOrder() < 0) {
                throw new IllegalArgumentException(
                        "structure fluid tick subTickOrder must be non-negative");
            }
            if (value.subTickOrder() != expectedFluidTickOrder) {
                throw new IllegalArgumentException(
                        "structure fluid tick subTickOrder must equal raw encounter index: "
                                + value.subTickOrder() + " != " + expectedFluidTickOrder);
            }
            fluidTicks.add(new PreparedStructureFluidTick(value.blockX(), value.blockY(),
                    value.blockZ(), fluidKey, value.delay(), value.priority(),
                    value.subTickOrder()));
            expectedFluidTickOrder++;
        }

        ArrayList<PreparedStructureBlockTick> blockTicks = new ArrayList<>(
                batch.blockTicks().size());
        long expectedBlockTickOrder = 0L;
        LinkedHashSet<StructureTickIdentity> blockTickIdentities = new LinkedHashSet<>();
        for (StructureBlockTick value : batch.blockTicks()) {
            requireStructureDestination(value.blockX(), value.blockY(), value.blockZ());
            String blockKey = requireNamespacedKey(value.blockKey(),
                    "structure block tick type");
            Mc263FeatureBlockState tickState =
                    Mc263FeatureBlockState.forSemanticBlockKey(blockKey);
            if (value.delay() < 0) {
                throw new IllegalArgumentException(
                        "structure block tick delay must be non-negative");
            }
            Mc263FinalChunkSidecars.TickPriority.fromValue(value.priority());
            if (value.subTickOrder() != expectedBlockTickOrder) {
                throw new IllegalArgumentException(
                        "structure block tick subTickOrder must equal raw encounter index: "
                                + value.subTickOrder() + " != " + expectedBlockTickOrder);
            }
            DestinationPosition destination = destination(
                    value.blockX(), value.blockY(), value.blockZ());
            StructureTickIdentity identity = new StructureTickIdentity(destination, blockKey);
            if (!blockTickIdentities.add(identity)) {
                throw new IllegalArgumentException("duplicate structure BTIK at "
                        + value.blockX() + "," + value.blockY() + "," + value.blockZ());
            }
            MutableChunk chunk = requireChunk(destination.chunkX(), destination.chunkZ());
            if (chunk.scheduledBlockTicks.containsKey(
                    new TickKey(destination.packedPosition(), blockKey))) {
                throw new IllegalArgumentException("structure BTIK conflicts with live tick at "
                        + value.blockX() + "," + value.blockY() + "," + value.blockZ());
            }
            blockTicks.add(new PreparedStructureBlockTick(value.blockX(), value.blockY(),
                    value.blockZ(), tickState.blockId(), blockKey, value.delay(),
                    value.priority(), value.subTickOrder()));
            expectedBlockTickOrder++;
        }

        ArrayList<PreparedStructureBee> bees = new ArrayList<>(batch.bees().size());
        for (StructureBee value : batch.bees()) {
            requireStructureDestination(value.blockX(), value.blockY(), value.blockZ());
            if (value.ticksInHive() < 0 || value.ticksInHive() > 598) {
                throw new IllegalArgumentException(
                        "structure bee ticks in hive are outside 0..598: "
                                + value.ticksInHive());
            }
            DestinationPosition destination = destination(
                    value.blockX(), value.blockY(), value.blockZ());
            Mc263FeatureBlockState state = stagedStructureState(finalBlocks, destination,
                    value.blockX(), value.blockY(), value.blockZ());
            if (state.capability() != Mc263FeatureBlockState.Capability.BEEHIVE) {
                throw new IllegalArgumentException("structure BEES lacks beehive capability at "
                        + value.blockX() + "," + value.blockY() + "," + value.blockZ());
            }
            bees.add(new PreparedStructureBee(value.blockX(), value.blockY(), value.blockZ(),
                    value.ticksInHive()));
        }

        ArrayList<StructurePostprocessMark> postprocessMarks = new ArrayList<>(
                batch.postprocessMarks().size());
        for (StructurePostprocessMark value : batch.postprocessMarks()) {
            requireStructureDestination(value.blockX(), value.blockY(), value.blockZ());
            postprocessMarks.add(value);
        }

        LinkedHashMap<DestinationPosition, PreparedStructureSpawner> spawners =
                new LinkedHashMap<>();
        for (StructureSpawner value : batch.spawners()) {
            requireStructureDestination(value.blockX(), value.blockY(), value.blockZ());
            String entityType = requireMinecraftKey(value.entityType(),
                    "structure spawner entity type");
            DestinationPosition destination = destination(
                    value.blockX(), value.blockY(), value.blockZ());
            Mc263FeatureBlockState state = stagedStructureState(finalBlocks, destination,
                    value.blockX(), value.blockY(), value.blockZ());
            if (state.capability() != Mc263FeatureBlockState.Capability.SPAWNER) {
                throw new IllegalArgumentException("structure SPWN lacks spawner capability at "
                        + value.blockX() + "," + value.blockY() + "," + value.blockZ());
            }
            PreparedStructureSpawner prepared = new PreparedStructureSpawner(value.blockX(),
                    value.blockY(), value.blockZ(), entityType);
            PreparedStructureSpawner existing = spawners.putIfAbsent(destination, prepared);
            if (existing != null && !existing.equals(prepared)) {
                throw new IllegalArgumentException("conflicting structure SPWN at "
                        + value.blockX() + "," + value.blockY() + "," + value.blockZ());
            }
        }

        // Every remaining operation uses values proven above; none has a rejecting path left.
        for (PreparedStructureBlock write : orderedBlocks) {
            if (!trySetOwnedBlockState(write.blockX(), write.blockY(), write.blockZ(),
                    write.state(), write.owner())) {
                throw new IllegalStateException("prevalidated structure block write was rejected");
            }
        }
        for (PreparedStructureLoot value : loot.values()) {
            if (!setChestLoot(value.blockX(), value.blockY(), value.blockZ(), value.lootTable(),
                    value.lootSeed(), value.productionContext())) {
                throw new IllegalStateException("prevalidated structure LOOT was rejected");
            }
        }
        for (PreparedStructureArchaeology value : archaeology.values()) {
            if (!setArchaeologyLoot(value.blockX(), value.blockY(), value.blockZ(),
                    value.lootTable(), value.lootSeed())) {
                throw new IllegalStateException("prevalidated structure ARCH was rejected");
            }
        }
        for (PreparedStructureBlockTick value : blockTicks) {
            applyPrevalidatedStructureBlockTick(value);
        }
        for (PreparedStructureFluidTick value : fluidTicks) {
            applyPrevalidatedStructureFluidTick(value);
        }
        for (StructurePostprocessMark value : postprocessMarks) {
            applyPrevalidatedStructurePostprocessMark(value);
        }
        for (PreparedStructureSpawner value : spawners.values()) {
            applyPrevalidatedStructureSpawner(value);
        }
        for (PreparedStructureBee value : bees) {
            applyPrevalidatedStructureBee(value);
        }
        productionContextIdentity = batchContextIdentity;
        authenticatedStructureEntities.addAll(batch.authenticatedEntities());
        return new StructureBatchSettlement(blockEntities, entities);
    }

    private void applyPrevalidatedStructureBlockTick(PreparedStructureBlockTick value) {
        MutableChunk chunk = requireChunk(Math.floorDiv(value.blockX(), Blocks.CHUNK_X),
                Math.floorDiv(value.blockZ(), Blocks.CHUNK_Z));
        int localX = Math.floorMod(value.blockX(), Blocks.CHUNK_X);
        int localZ = Math.floorMod(value.blockZ(), Blocks.CHUNK_Z);
        int packed = Blocks.blockIndex(localX, value.blockY(), localZ);
        TickKey key = new TickKey(packed, value.blockKey());
        long persistedOrder = chunk.scheduledBlockTicks.size();
        chunk.scheduledBlockTicks.put(key, ScheduledBlockTick.resolved(localX,
                value.blockY(), localZ, value.blockId(), value.blockKey(), value.delay(),
                value.priority(), persistedOrder));
    }

    private void applyPrevalidatedStructureFluidTick(PreparedStructureFluidTick value) {
        MutableChunk chunk = requireChunk(Math.floorDiv(value.blockX(), Blocks.CHUNK_X),
                Math.floorDiv(value.blockZ(), Blocks.CHUNK_Z));
        int localX = Math.floorMod(value.blockX(), Blocks.CHUNK_X);
        int localZ = Math.floorMod(value.blockZ(), Blocks.CHUNK_Z);
        int packed = Blocks.blockIndex(localX, value.blockY(), localZ);
        TickKey key = new TickKey(packed, value.fluidKey());
        chunk.scheduledFluidTickPresence.add(key);
        long persistedOrder = chunk.scheduledFluidTicks.size();
        chunk.scheduledFluidTicks.add(ScheduledFluidTick.resolved(localX,
                value.blockY(), localZ, value.fluidKey(), value.delay(), value.priority(),
                persistedOrder));
    }

    private void applyPrevalidatedStructurePostprocessMark(StructurePostprocessMark value) {
        MutableChunk chunk = requireChunk(Math.floorDiv(value.blockX(), Blocks.CHUNK_X),
                Math.floorDiv(value.blockZ(), Blocks.CHUNK_Z));
        int localX = Math.floorMod(value.blockX(), Blocks.CHUNK_X);
        int localZ = Math.floorMod(value.blockZ(), Blocks.CHUNK_Z);
        int section = Math.floorDiv(value.blockY() - Blocks.MIN_Y, 16);
        chunk.postprocessBySection.get(section).add(
                new PostprocessMark(localX, value.blockY(), localZ));
    }

    private void applyPrevalidatedStructureSpawner(PreparedStructureSpawner value) {
        MutableChunk chunk = requireChunk(Math.floorDiv(value.blockX(), Blocks.CHUNK_X),
                Math.floorDiv(value.blockZ(), Blocks.CHUNK_Z));
        int localX = Math.floorMod(value.blockX(), Blocks.CHUNK_X);
        int localZ = Math.floorMod(value.blockZ(), Blocks.CHUNK_Z);
        int packed = Blocks.blockIndex(localX, value.blockY(), localZ);
        chunk.spawnerMobs.put(packed,
                new SpawnerMob(localX, value.blockY(), localZ, value.entityType()));
    }

    private void applyPrevalidatedStructureBee(PreparedStructureBee value) {
        MutableChunk chunk = requireChunk(Math.floorDiv(value.blockX(), Blocks.CHUNK_X),
                Math.floorDiv(value.blockZ(), Blocks.CHUNK_Z));
        int localX = Math.floorMod(value.blockX(), Blocks.CHUNK_X);
        int localZ = Math.floorMod(value.blockZ(), Blocks.CHUNK_Z);
        int packed = Blocks.blockIndex(localX, value.blockY(), localZ);
        BeehiveNest nest = chunk.beehiveNests.get(packed);
        if (nest == null || chunk.liveCapabilities.get(packed)
                != Mc263FeatureBlockState.Capability.BEEHIVE) {
            throw new IllegalStateException("prevalidated structure BEES capability disappeared");
        }
        nest.addOccupant(new BeehiveOccupant(value.ticksInHive()));
    }

    private void requireStructureDestination(int blockX, int blockY, int blockZ) {
        requireBuildHeight(blockY);
        if (!ensureCanWrite(blockX, blockY, blockZ)) {
            throw new IllegalArgumentException("structure destination escaped source write clip: "
                    + blockX + "," + blockY + "," + blockZ);
        }
    }

    static boolean isMapSensitiveCampLootTable(String lootTable) {
        return "minecraft:chests/abandoned_camp_common_chest".equals(lootTable)
                || "minecraft:chests/abandoned_camp_secret_chest".equals(lootTable);
    }

    private static LootProductionContext requireAuthenticatedContextShape(String table,
            int blockX, int blockY, int blockZ, LootProductionContext productionContext) {
        productionContext = Objects.requireNonNull(
                productionContext, "production loot context");
        productionContext.requireAuthenticatedContext();
        if (productionContext.sourceIdentity() == null
                || !productionContext.sourceIdentity().matches("[0-9a-f]{64}")
                || productionContext.sourceIdentity().equals("0".repeat(64))) {
            throw new IllegalArgumentException(
                    "production loot source identity must be a nonzero SHA-256 receipt");
        }
        if (!table.equals(productionContext.tableIdentity())) {
            throw new IllegalArgumentException("production loot table identity mismatch");
        }
        if (productionContext.originX() != blockX
                || productionContext.originY() != blockY
                || productionContext.originZ() != blockZ) {
            throw new IllegalArgumentException("production loot origin mismatch");
        }
        if (!(productionContext instanceof LocatedProductionContext located)) {
            throw new IllegalArgumentException(
                    "production loot mutation rejects legacy fixture context");
        }
        if (isMapSensitiveCampLootTable(table) && located.maps().isEmpty()) {
            throw new IllegalArgumentException(
                    "map-sensitive Camp loot requires authenticated biome+located maps before "
                            + "mutation: " + table + " at " + blockX + "," + blockY + ","
                            + blockZ);
        }
        return productionContext;
    }

    private LootProductionContext requireBatchProductionContextIdentity(
            StructureBatch batch) {
        LootProductionContext identity = productionContextIdentity;
        for (StructureLoot value : batch.loot()) {
            String table = requireMinecraftKey(value.lootTable(), "structure loot table");
            identity = requireProductionLootContext(table, value.blockX(), value.blockY(),
                    value.blockZ(), value.productionContext(), identity);
        }
        for (AuthenticatedStructureEntity value : batch.authenticatedEntities()) {
            if (authenticatedStructureEntities.stream()
                    .anyMatch(existing -> existing.entity().equals(value.entity()))) {
                throw new IllegalArgumentException(
                        "duplicate authenticated structure ENTS across settlements at "
                                + entityPosition(value.entity()));
            }
            Mc263FinalChunkSidecars.StructureEntity entity = value.entity();
            identity = requireProductionLootContext(entity.lootTable(),
                    structureEntityBlockCoordinate(entity.x(), "structure entity X"),
                    structureEntityBlockCoordinate(entity.y(), "structure entity Y"),
                    structureEntityBlockCoordinate(entity.z(), "structure entity Z"),
                    value.productionContext(), identity);
        }
        return identity;
    }

    private LootProductionContext requireProductionLootContext(String table,
            int blockX, int blockY, int blockZ, LootProductionContext productionContext,
            LootProductionContext expectedIdentity) {
        if (!sourceActive) {
            throw new IllegalStateException(
                    "production loot context requires an active FEATURES source");
        }
        productionContext = requireAuthenticatedContextShape(
                table, blockX, blockY, blockZ, productionContext);
        if (!withinRadius(Math.floorDiv(blockX, Blocks.CHUNK_X),
                Math.floorDiv(blockZ, Blocks.CHUNK_Z), activeSourceChunkX,
                activeSourceChunkZ, ACTIVE_SOURCE_RADIUS)) {
            throw new IllegalArgumentException(
                    "production loot origin is outside the active source write closure");
        }
        if (!productionContext.biomeKey().equals(biomeKey(blockX, blockY, blockZ))) {
            throw new IllegalArgumentException("production loot biome differs from live region");
        }
        if (expectedIdentity != null
                && (!expectedIdentity.worldIdentity().equals(productionContext.worldIdentity())
                || !expectedIdentity.sourceIdentity().equals(
                        productionContext.sourceIdentity()))) {
            throw new IllegalArgumentException(
                    "production loot context differs from the active world/source identity");
        }
        return expectedIdentity == null ? productionContext : expectedIdentity;
    }

    private void requireStructureFluidTickDestination(int blockX, int blockY, int blockZ) {
        if (!sourceActive) {
            throw new IllegalStateException(
                    "structure fluid tick requires an active FEATURES source");
        }
        requireBuildHeight(blockY);
        int chunkX = Math.floorDiv(blockX, Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv(blockZ, Blocks.CHUNK_Z);
        if (chunkSlot(chunkX, chunkZ) < 0) {
            throw new IllegalArgumentException(
                    "structure fluid tick destination lies outside loaded FEATURES region: "
                            + blockX + "," + blockY + "," + blockZ);
        }
    }

    private Mc263FeatureBlockState stagedStructureState(
            Map<DestinationPosition, PreparedStructureBlock> blocks,
            DestinationPosition destination, int blockX, int blockY, int blockZ) {
        PreparedStructureBlock write = blocks.get(destination);
        return write == null ? blockState(blockX, blockY, blockZ) : write.state();
    }

    private void requireExistingLootEquality(
            Map<DestinationPosition, PreparedStructureBlock> blocks,
            DestinationPosition destination, PreparedStructureLoot prepared) {
        if (blocks.containsKey(destination)) return;
        MutableChunk chunk = requireChunk(destination.chunkX(), destination.chunkZ());
        ChestLoot existing = chunk.chestLoot.get(destination.packedPosition());
        if (existing != null && (!existing.lootTable().equals(prepared.lootTable())
                || existing.lootSeed() != prepared.lootSeed()
                || !existing.facing().equals(prepared.facing())
                || !existing.productionContext().equals(prepared.productionContext()))) {
            throw new IllegalArgumentException("structure LOOT conflicts with live sidecar at "
                    + prepared.blockX() + "," + prepared.blockY() + "," + prepared.blockZ());
        }
    }

    private void requireExistingArchaeologyEquality(
            Map<DestinationPosition, PreparedStructureBlock> blocks,
            DestinationPosition destination, PreparedStructureArchaeology prepared) {
        if (blocks.containsKey(destination)) return;
        MutableChunk chunk = requireChunk(destination.chunkX(), destination.chunkZ());
        ArchaeologyLoot existing = chunk.archaeologyLoot.get(destination.packedPosition());
        if (existing != null && (!existing.lootTable().equals(prepared.lootTable())
                || existing.lootSeed() != prepared.lootSeed())) {
            throw new IllegalArgumentException("structure ARCH conflicts with live sidecar at "
                    + prepared.blockX() + "," + prepared.blockY() + "," + prepared.blockZ());
        }
    }

    private static DestinationPosition destination(int blockX, int blockY, int blockZ) {
        int chunkX = Math.floorDiv(blockX, Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv(blockZ, Blocks.CHUNK_Z);
        return new DestinationPosition(chunkX, chunkZ,
                Blocks.blockIndex(Math.floorMod(blockX, Blocks.CHUNK_X), blockY,
                        Math.floorMod(blockZ, Blocks.CHUNK_Z)));
    }

    private static int structureEntityBlockCoordinate(double coordinate, String name) {
        double floor = Math.floor(coordinate);
        if (floor < Integer.MIN_VALUE || floor > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " lies outside integer world coordinates");
        }
        return (int) floor;
    }

    private static String entityPosition(Mc263FinalChunkSidecars.StructureEntity entity) {
        return entity.x() + "," + entity.y() + "," + entity.z();
    }

    /** Returns an isolated snapshot of the target chunk; no neighbor snapshot API is exposed. */
    public CenterSnapshot snapshotCenter() {
        MutableChunk center = requireChunk(targetChunkX, targetChunkZ);
        return new CenterSnapshot(targetChunkX, targetChunkZ, center.blocks(),
                center.exactStateOverrides(), center.biomes(),
                center.worldSurface, center.oceanFloor, center.motionBlocking,
                immutablePostprocessSections(center.postprocessBySection),
                List.copyOf(center.scheduledBlockTicks.values()),
                List.copyOf(center.scheduledFluidTicks),
                List.copyOf(center.capabilityReceipts), List.copyOf(center.chestLoot.values()),
                List.copyOf(center.spawnerMobs.values()), center.ownership,
                List.copyOf(center.archaeologyLoot.values()),
                List.copyOf(center.beehiveNests.values()),
                authenticatedStructureEntities.stream().filter(value -> {
                    Mc263FinalChunkSidecars.StructureEntity entity = value.entity();
                    return Math.floorDiv(structureEntityBlockCoordinate(
                                    entity.x(), "structure entity X"), Blocks.CHUNK_X)
                                    == targetChunkX
                            && Math.floorDiv(structureEntityBlockCoordinate(
                                    entity.z(), "structure entity Z"), Blocks.CHUNK_Z)
                                    == targetChunkZ;
                }).toList());
    }

    public int copiedChunkCount() {
        return copiedChunkCount;
    }

    int initialHeightmapBlockLoads() {
        return initialHeightmapBlockLoads;
    }

    List<SourceChunk> sourceSchedule() {
        List<SourceChunk> schedule = new ArrayList<>(ACTIVE_SOURCE_COUNT);
        for (int chunkX = targetChunkX - ACTIVE_SOURCE_RADIUS;
                chunkX <= targetChunkX + ACTIVE_SOURCE_RADIUS; chunkX++) {
            for (int chunkZ = targetChunkZ - ACTIVE_SOURCE_RADIUS;
                    chunkZ <= targetChunkZ + ACTIVE_SOURCE_RADIUS; chunkZ++) {
                schedule.add(new SourceChunk(chunkX, chunkZ));
            }
        }
        return List.copyOf(schedule);
    }

    /**
     * The distinct biomes visible to one FEATURES source, computed once per source.
     *
     * <p>AGENTS rule 10l: the answer is a pure function of the source coordinate and the region's
     * quart biome arrays, which are final and never rewritten — only blocks and their exact-state
     * overrides mutate during FEATURES. The scan inserts 9 x 64 keys into a hash set, and the
     * dispatcher, the prefix stage and step 9 each ask for it once per source, so a target paid it
     * up to 75 times for at most 9 distinct answers. The returned set is the same unmodifiable
     * instance and the same insertion order, so every caller sees identical iteration.</p>
     */
    Set<String> biomesForSource(int sourceChunkX, int sourceChunkZ) {
        int slot = sourceSlot(sourceChunkX, sourceChunkZ);
        if (slot >= 0) {
            Set<String> resident = biomesBySource.get(slot);
            if (resident != null) {
                return resident;
            }
        }
        Set<String> result = new LinkedHashSet<>();
        for (int chunkX = sourceChunkX - ACTIVE_SOURCE_RADIUS;
                chunkX <= sourceChunkX + ACTIVE_SOURCE_RADIUS; chunkX++) {
            for (int chunkZ = sourceChunkZ - ACTIVE_SOURCE_RADIUS;
                    chunkZ <= sourceChunkZ + ACTIVE_SOURCE_RADIUS; chunkZ++) {
                MutableChunk chunk = requireChunk(chunkX, chunkZ);
                result.addAll(Arrays.asList(chunk.biomes()));
            }
        }
        Set<String> computed = Collections.unmodifiableSet(result);
        if (slot >= 0) {
            biomesBySource.set(slot, computed);
        }
        return computed;
    }

    /** Row-major slot of a source chunk in the target +/-1 window, or {@code -1} when outside. */
    private int sourceSlot(int sourceChunkX, int sourceChunkZ) {
        long offsetX = (long) sourceChunkX - targetChunkX;
        long offsetZ = (long) sourceChunkZ - targetChunkZ;
        if (offsetX < -ACTIVE_SOURCE_RADIUS || offsetX > ACTIVE_SOURCE_RADIUS
                || offsetZ < -ACTIVE_SOURCE_RADIUS || offsetZ > ACTIVE_SOURCE_RADIUS) {
            return -1;
        }
        return (int) ((offsetX + ACTIVE_SOURCE_RADIUS) * SOURCE_SPAN
                + (offsetZ + ACTIVE_SOURCE_RADIUS));
    }

    void beginSource(int sourceChunkX, int sourceChunkZ) {
        if (sourceActive) throw new IllegalStateException("a FEATURES source is already active");
        if (!withinRadius(sourceChunkX, sourceChunkZ, targetChunkX, targetChunkZ,
                ACTIVE_SOURCE_RADIUS)) {
            throw new IllegalArgumentException("source lies outside target +/-1");
        }
        activeSourceChunkX = sourceChunkX;
        activeSourceChunkZ = sourceChunkZ;
        sourceActive = true;
    }

    void endSource() {
        if (!sourceActive) throw new IllegalStateException("no FEATURES source is active");
        sourceActive = false;
    }

    /** Package-private transactional POST commit hook. The resolver is the sole caller. */
    void applyPostprocessState(int blockX, int blockY, int blockZ,
            Mc263FeatureBlockState state) {
        if (sourceActive) {
            throw new IllegalStateException("POST cannot run while a FEATURES source is active");
        }
        if (Math.floorDiv(blockX, Blocks.CHUNK_X) != targetChunkX
                || Math.floorDiv(blockZ, Blocks.CHUNK_Z) != targetChunkZ) {
            throw new IllegalArgumentException("POST center write escaped target chunk");
        }
        if (blockState(blockX, blockY, blockZ).equals(state)) return;
        beginSource(targetChunkX, targetChunkZ);
        try {
            if (!setBlockState(blockX, blockY, blockZ, state)) {
                throw new IllegalStateException("POST center write was rejected");
            }
        } finally {
            endSource();
        }
    }

    /** Package-private POST tick hook retaining live delay, priority, and sub-tick order. */
    void applyPostprocessBlockTick(int blockX, int blockY, int blockZ, String blockKey,
            int delay, int priority, long subTickOrder) {
        MutableChunk chunk = requireChunk(targetChunkX, targetChunkZ);
        int localX = Math.floorMod(blockX, Blocks.CHUNK_X);
        int localZ = Math.floorMod(blockZ, Blocks.CHUNK_Z);
        int packed = Blocks.blockIndex(localX, blockY, localZ);
        Mc263FeatureBlockState state = Mc263FeatureBlockState.forSemanticBlockKey(blockKey);
        TickKey key = new TickKey(packed, blockKey);
        chunk.scheduledBlockTicks.putIfAbsent(key, ScheduledBlockTick.resolved(
                localX, blockY, localZ, state.blockId(), blockKey, delay, priority,
                subTickOrder));
    }

    /** Package-private POST fluid-tick hook retaining live delay, priority and ordering. */
    void applyPostprocessFluidTick(int blockX, int blockY, int blockZ, String fluidKey,
            int delay, int priority, long subTickOrder) {
        MutableChunk chunk = requireChunk(targetChunkX, targetChunkZ);
        int localX = Math.floorMod(blockX, Blocks.CHUNK_X);
        int localZ = Math.floorMod(blockZ, Blocks.CHUNK_Z);
        int packed = Blocks.blockIndex(localX, blockY, localZ);
        TickKey key = new TickKey(packed, fluidKey);
        if (chunk.scheduledFluidTickPresence.add(key)) {
            chunk.scheduledFluidTicks.add(ScheduledFluidTick.resolved(
                    localX, blockY, localZ, fluidKey, delay, priority, subTickOrder));
        }
    }

    void clearPostprocessMarks() {
        MutableChunk center = requireChunk(targetChunkX, targetChunkZ);
        for (List<PostprocessMark> section : center.postprocessBySection) section.clear();
    }

    boolean postprocessCommitReady() { return !sourceActive; }

    private static void updateHeightmap(MutableChunk chunk, int[] heightmap,
            IntPredicate predicate, HeightmapKind kind, int localX, int blockY, int localZ,
            int oldId, int newId, Mc263FeatureBlockState oldState,
            Mc263FeatureBlockState newState) {
        int column = localX + localZ * Blocks.CHUNK_X;
        int firstAvailable = heightmap[column];
        if (blockY <= firstAvailable - 2) return;
        boolean oldMatches = matchesHeightmap(oldId, oldState, predicate, kind);
        boolean newMatches = matchesHeightmap(newId, newState, predicate, kind);
        if (oldMatches == newMatches) return;
        if (newMatches && blockY >= firstAvailable) {
            heightmap[column] = blockY + 1;
        } else if (oldMatches && firstAvailable == blockY + 1) {
            heightmap[column] = scanHeightmap(
                    chunk, localX, localZ, blockY - 1, predicate, kind);
        }
    }

    private MutableChunk requireChunk(int chunkX, int chunkZ) {
        MutableChunk chunk = chunkOrNull(chunkX, chunkZ);
        if (chunk == null) {
            throw new IllegalArgumentException("coordinate lies outside FEATURES region chunk: "
                    + chunkX + "," + chunkZ);
        }
        return chunk;
    }

    private Mc263FeatureBlockState.Capability liveCapability(
            int blockX, int blockY, int blockZ) {
        if (blockY < Blocks.MIN_Y || blockY > Blocks.MAX_Y) {
            return Mc263FeatureBlockState.Capability.NONE;
        }
        MutableChunk chunk = requireChunk(Math.floorDiv(blockX, Blocks.CHUNK_X),
                Math.floorDiv(blockZ, Blocks.CHUNK_Z));
        int localX = Math.floorMod(blockX, Blocks.CHUNK_X);
        int localZ = Math.floorMod(blockZ, Blocks.CHUNK_Z);
        return chunk.liveCapabilities.getOrDefault(
                Blocks.blockIndex(localX, blockY, localZ),
                Mc263FeatureBlockState.Capability.NONE);
    }

    private static int buildHeightmaps(short[] blocks,
            Map<Integer, Mc263FeatureBlockState> exactStateOverrides,
            IntPredicate worldSurfaceBlock, IntPredicate oceanFloorBlock,
            IntPredicate motionBlockingBlock, int[] worldSurface,
            int[] oceanFloor, int[] motionBlocking) {
        Arrays.fill(worldSurface, Blocks.MIN_Y);
        Arrays.fill(oceanFloor, Blocks.MIN_Y);
        Arrays.fill(motionBlocking, Blocks.MIN_Y);
        // Overrides are sparse; when there are none the per-cell lookup would box every one of
        // the 98,304 packed indices only to miss. Same resolved state either way.
        boolean overridden = !exactStateOverrides.isEmpty();
        int blockLoads = 0;
        for (int localZ = 0; localZ < Blocks.CHUNK_Z; localZ++) {
            for (int localX = 0; localX < Blocks.CHUNK_X; localX++) {
                int column = localX + localZ * Blocks.CHUNK_X;
                boolean worldSurfaceFound = false;
                boolean oceanFloorFound = false;
                boolean motionBlockingFound = false;
                for (int blockY = Blocks.MAX_Y; blockY >= Blocks.MIN_Y; blockY--) {
                    int packed = Blocks.blockIndex(localX, blockY, localZ);
                    int id = Short.toUnsignedInt(blocks[packed]);
                    Mc263FeatureBlockState state = overridden
                            ? exactStateOverrides.get(packed) : null;
                    if (state == null) state = Mc263FeatureBlockState.defaultOrNullForId(id);
                    blockLoads++;
                    if (!worldSurfaceFound && matchesHeightmap(id, state, worldSurfaceBlock,
                            HeightmapKind.WORLD_SURFACE)) {
                        worldSurface[column] = blockY + 1;
                        worldSurfaceFound = true;
                    }
                    if (!oceanFloorFound && matchesHeightmap(id, state, oceanFloorBlock,
                            HeightmapKind.OCEAN_FLOOR)) {
                        oceanFloor[column] = blockY + 1;
                        oceanFloorFound = true;
                    }
                    if (!motionBlockingFound && matchesHeightmap(id, state, motionBlockingBlock,
                            HeightmapKind.MOTION_BLOCKING)) {
                        motionBlocking[column] = blockY + 1;
                        motionBlockingFound = true;
                    }
                    if (worldSurfaceFound && oceanFloorFound && motionBlockingFound) break;
                }
            }
        }
        return blockLoads;
    }

    private static int scanHeightmap(MutableChunk chunk, int localX, int localZ, int startY,
            IntPredicate predicate, HeightmapKind kind) {
        for (int blockY = startY; blockY >= Blocks.MIN_Y; blockY--) {
            int packed = Blocks.blockIndex(localX, blockY, localZ);
            int id = Short.toUnsignedInt(chunk.blocks()[packed]);
            if (matchesHeightmap(id, chunk.blockStateOrNull(packed), predicate, kind)) {
                return blockY + 1;
            }
        }
        return Blocks.MIN_Y;
    }

    private static boolean matchesHeightmap(int blockId, Mc263FeatureBlockState state,
            IntPredicate predicate, HeightmapKind kind) {
        boolean base = predicate.test(blockId);
        if (kind != HeightmapKind.MOTION_BLOCKING || state == null) return base;
        int mask = state.heightmapMask(false, false, base);
        return (mask & Mc263FeatureBlockState.HEIGHTMAP_MOTION_BLOCKING) != 0;
    }

    private static List<List<PostprocessMark>> immutablePostprocessSections(
            List<List<PostprocessMark>> sections) {
        List<List<PostprocessMark>> copy = new ArrayList<>(SECTION_COUNT);
        for (List<PostprocessMark> section : sections) {
            copy.add(List.copyOf(section));
        }
        return List.copyOf(copy);
    }

    private static void requireBuildHeight(int blockY) {
        if (blockY < Blocks.MIN_Y || blockY > Blocks.MAX_Y) {
            throw new IllegalArgumentException("Y lies outside generation height: " + blockY);
        }
    }

    private static void requireSafeTargetCoordinate(int chunkCoordinate, String axis) {
        long minimum = (long) chunkCoordinate - INPUT_RADIUS;
        long maximum = (long) chunkCoordinate + INPUT_RADIUS;
        long minimumBlock = minimum * Blocks.CHUNK_X;
        long maximumBlock = maximum * Blocks.CHUNK_X + Blocks.CHUNK_X - 1L;
        if (minimum < Integer.MIN_VALUE || maximum > Integer.MAX_VALUE
                || minimumBlock < Integer.MIN_VALUE || maximumBlock > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("FEATURES target chunk " + axis
                    + " cannot be represented as block coordinates: " + chunkCoordinate);
        }
    }

    private static void requireLocalPosition(int localX, int blockY, int localZ,
            String description) {
        if (localX < 0 || localX >= Blocks.CHUNK_X
                || localZ < 0 || localZ >= Blocks.CHUNK_Z
                || blockY < Blocks.MIN_Y || blockY > Blocks.MAX_Y) {
            throw new IllegalArgumentException(description + " lies outside chunk: "
                    + localX + "," + blockY + "," + localZ);
        }
    }

    private static String requireMinecraftKey(String key, String description) {
        String canonical = Mc263FeatureBlockState.requireCanonicalResourceKey(key, description);
        if (!canonical.startsWith("minecraft:")) {
            throw new IllegalArgumentException("Minecraft namespaced " + description
                    + " is required: " + key);
        }
        return canonical;
    }

    private static String requireNamespacedKey(String key, String description) {
        return Mc263FeatureBlockState.requireCanonicalResourceKey(key, description);
    }

    private static boolean isSupportedArchaeologyTable(String table) {
        return "minecraft:archaeology/desert_well".equals(table)
                || "minecraft:archaeology/desert_pyramid".equals(table)
                || "minecraft:archaeology/ocean_ruin_cold".equals(table)
                || "minecraft:archaeology/ocean_ruin_warm".equals(table)
                || "minecraft:archaeology/trail_ruins_common".equals(table)
                || "minecraft:archaeology/trail_ruins_rare".equals(table);
    }

    private static String semanticKeyForLegacyId(int blockId) {
        if (blockId < 0 || blockId > 0xffff) {
            throw new IllegalArgumentException("block ID is outside unsigned-16 range: " + blockId);
        }
        // An ID-only legacy receipt cannot prove whether ID 0 meant air, cave_air, or void_air.
        // Preserve its opaque ID identity; exact FEATURES callers use the String overload below.
        return "webcraft:block_" + blockId;
    }

    private static int biomeIndex(int quartX, int quartY, int quartZ) {
        return quartX + quartZ * BIOME_QUART_WIDTH
                + quartY * BIOME_QUART_WIDTH * BIOME_QUART_WIDTH;
    }

    private static boolean withinRadius(int chunkX, int chunkZ, int centerX, int centerZ,
            int radius) {
        return Math.abs((long) chunkX - centerX) <= radius
                && Math.abs((long) chunkZ - centerZ) <= radius;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffff_ffffL);
    }

    /** Row-major slot of a chunk in the region grid, or {@code -1} when it lies outside. */
    private int chunkSlot(int chunkX, int chunkZ) {
        long offsetX = (long) chunkX - targetChunkX;
        long offsetZ = (long) chunkZ - targetChunkZ;
        if (offsetX < -INPUT_RADIUS || offsetX > INPUT_RADIUS
                || offsetZ < -INPUT_RADIUS || offsetZ > INPUT_RADIUS) {
            return -1;
        }
        return (int) ((offsetX + INPUT_RADIUS) * GRID_SPAN + (offsetZ + INPUT_RADIUS));
    }

    /** The region input at this chunk coordinate, or {@code null} when it lies outside. */
    private MutableChunk chunkOrNull(int chunkX, int chunkZ) {
        int slot = chunkSlot(chunkX, chunkZ);
        return slot < 0 ? null : chunks[slot];
    }

    public static final class CarversChunk {
        private final int chunkX;
        private final int chunkZ;
        private final short[] blockIds;
        private final String[] biomeKeys;
        private final Map<Integer, Mc263FeatureBlockState> exactStateOverrides;
        private final List<PostprocessMark> postprocessMarks;
        private final List<ScheduledBlockTick> scheduledBlockTicks;
        private final List<ScheduledFluidTick> scheduledFluidTicks;
        private final LightSnapshot lightSnapshot;

        /**
         * The initial heightmaps of this immutable input, computed once.
         *
         * <p>AGENTS rule 10l: {@link #buildHeightmaps} is a pure function of the CARVERS blocks,
         * the exact-state overrides and the three pinned heightmap predicates, but it ran once per
         * {@code MutableChunk} — that is once per FEATURES target that reads this input, and
         * neighbouring targets overlap by 20 of their 25 inputs. The scan touches up to 98,304
         * cells per chunk. Caching the three column arrays and the block-load count on the
         * (immutable) input and handing back copies gives every target the identical arrays and
         * the identical {@code initialHeightmapBlockLoads} receipt value.</p>
         *
         * <p>The predicates are part of the key, not an assumption: a region built with different
         * heightmap predicates recomputes instead of reusing a foreign result.</p>
         */
        private volatile Heightmaps heightmaps;

        private record Heightmaps(IntPredicate worldSurfaceBlock, IntPredicate oceanFloorBlock,
                IntPredicate motionBlockingBlock, int[] worldSurface, int[] oceanFloor,
                int[] motionBlocking, int blockLoads) { }

        private int copyHeightmapsInto(IntPredicate worldSurfaceBlock,
                IntPredicate oceanFloorBlock, IntPredicate motionBlockingBlock,
                int[] worldSurface, int[] oceanFloor, int[] motionBlocking) {
            Heightmaps resident = heightmaps;
            if (resident == null || resident.worldSurfaceBlock() != worldSurfaceBlock
                    || resident.oceanFloorBlock() != oceanFloorBlock
                    || resident.motionBlockingBlock() != motionBlockingBlock) {
                int blockLoads = buildHeightmaps(blockIds, exactStateOverrides, worldSurfaceBlock,
                        oceanFloorBlock, motionBlockingBlock, worldSurface, oceanFloor,
                        motionBlocking);
                heightmaps = new Heightmaps(worldSurfaceBlock, oceanFloorBlock,
                        motionBlockingBlock, worldSurface.clone(), oceanFloor.clone(),
                        motionBlocking.clone(), blockLoads);
                return blockLoads;
            }
            System.arraycopy(resident.worldSurface(), 0, worldSurface, 0, worldSurface.length);
            System.arraycopy(resident.oceanFloor(), 0, oceanFloor, 0, oceanFloor.length);
            System.arraycopy(resident.motionBlocking(), 0, motionBlocking, 0,
                    motionBlocking.length);
            return resident.blockLoads();
        }

        public CarversChunk(int chunkX, int chunkZ, short[] blockIds, String[] biomeKeys,
                List<PostprocessMark> postprocessMarks,
                List<ScheduledBlockTick> scheduledBlockTicks) {
            this(chunkX, chunkZ, blockIds, biomeKeys, List.of(), postprocessMarks,
                    scheduledBlockTicks, List.of());
        }

        public CarversChunk(int chunkX, int chunkZ, short[] blockIds, String[] biomeKeys,
                List<StateOverride> stateOverrides, List<PostprocessMark> postprocessMarks,
                List<ScheduledBlockTick> scheduledBlockTicks) {
            this(chunkX, chunkZ, blockIds, biomeKeys, stateOverrides, postprocessMarks,
                    scheduledBlockTicks, List.of(), LightSnapshot.absent());
        }

        public CarversChunk(int chunkX, int chunkZ, short[] blockIds, String[] biomeKeys,
                List<StateOverride> stateOverrides, List<PostprocessMark> postprocessMarks,
                List<ScheduledBlockTick> scheduledBlockTicks,
                List<ScheduledFluidTick> scheduledFluidTicks) {
            this(chunkX, chunkZ, blockIds, biomeKeys, stateOverrides, postprocessMarks,
                    scheduledBlockTicks, scheduledFluidTicks, LightSnapshot.absent());
        }

        public CarversChunk(int chunkX, int chunkZ, short[] blockIds, String[] biomeKeys,
                List<StateOverride> stateOverrides, List<PostprocessMark> postprocessMarks,
                List<ScheduledBlockTick> scheduledBlockTicks,
                List<ScheduledFluidTick> scheduledFluidTicks, LightSnapshot lightSnapshot) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            Objects.requireNonNull(blockIds, "blockIds");
            Objects.requireNonNull(biomeKeys, "biomeKeys");
            if (blockIds.length != Blocks.CHUNK_BLOCKS) {
                throw new IllegalArgumentException("CARVERS block count must be "
                        + Blocks.CHUNK_BLOCKS + ": " + blockIds.length);
            }
            if (biomeKeys.length != BIOME_COUNT_PER_CHUNK) {
                throw new IllegalArgumentException("CARVERS biome count must be "
                        + BIOME_COUNT_PER_CHUNK + ": " + biomeKeys.length);
            }
            this.blockIds = blockIds.clone();
            this.biomeKeys = biomeKeys.clone();
            Objects.requireNonNull(stateOverrides, "stateOverrides");
            Map<Integer, Mc263FeatureBlockState> overrides = new LinkedHashMap<>();
            for (StateOverride override : stateOverrides) {
                Objects.requireNonNull(override, "exact state override");
                int packed = Blocks.blockIndex(
                        override.localX(), override.blockY(), override.localZ());
                int storedId = Short.toUnsignedInt(this.blockIds[packed]);
                if (storedId != override.state().blockId()) {
                    throw new IllegalArgumentException("exact state override ID mismatch at "
                            + override.localX() + "," + override.blockY() + ","
                            + override.localZ() + ": " + storedId + " != "
                            + override.state().blockId());
                }
                if (override.state().capability()
                        != Mc263FeatureBlockState.Capability.NONE) {
                    throw new IllegalArgumentException(
                            "CARVERS exact states cannot contain block entities at "
                                    + override.localX() + "," + override.blockY() + ","
                                    + override.localZ());
                }
                if (overrides.putIfAbsent(packed, override.state()) != null) {
                    throw new IllegalArgumentException("duplicate exact state override at "
                            + override.localX() + "," + override.blockY() + ","
                            + override.localZ());
                }
            }
            this.exactStateOverrides = Map.copyOf(overrides);
            Objects.requireNonNull(postprocessMarks, "postprocessMarks");
            this.postprocessMarks = List.copyOf(postprocessMarks);
            Objects.requireNonNull(scheduledBlockTicks, "scheduledBlockTicks");
            Map<TickKey, ScheduledBlockTick> uniqueTicks = new LinkedHashMap<>();
            for (ScheduledBlockTick tick : scheduledBlockTicks) {
                Objects.requireNonNull(tick, "scheduled block tick");
                int packed = Blocks.blockIndex(tick.localX(), tick.blockY(), tick.localZ());
                TickKey key = new TickKey(packed, tick.blockKey());
                if (uniqueTicks.containsKey(key)) continue;
                uniqueTicks.put(key, ScheduledBlockTick.resolved(tick.localX(), tick.blockY(),
                        tick.localZ(), tick.blockId(), tick.blockKey(), tick.delay(),
                        tick.priority(), uniqueTicks.size()));
            }
            this.scheduledBlockTicks = List.copyOf(uniqueTicks.values());
            Objects.requireNonNull(scheduledFluidTicks, "scheduled fluid ticks");
            List<ScheduledFluidTick> fluidTicks = new ArrayList<>(scheduledFluidTicks.size());
            for (ScheduledFluidTick tick : scheduledFluidTicks) {
                Objects.requireNonNull(tick, "scheduled fluid tick");
                fluidTicks.add(ScheduledFluidTick.resolved(tick.localX(), tick.blockY(),
                        tick.localZ(), tick.fluidKey(), tick.delay(), tick.priority(),
                        fluidTicks.size()));
            }
            this.scheduledFluidTicks = List.copyOf(fluidTicks);
            this.lightSnapshot = Objects.requireNonNull(lightSnapshot, "CARVERS light snapshot");
            for (String biome : this.biomeKeys) {
                requireNamespacedKey(biome, "CARVERS biome key");
            }
        }

        public static CarversChunk uniformBiome(int chunkX, int chunkZ, short[] blockIds,
                String biomeKey, List<PostprocessMark> postprocessMarks,
                List<ScheduledBlockTick> scheduledBlockTicks) {
            return uniformBiome(chunkX, chunkZ, blockIds, biomeKey, List.of(), postprocessMarks,
                    scheduledBlockTicks);
        }

        public static CarversChunk uniformBiome(int chunkX, int chunkZ, short[] blockIds,
                String biomeKey, List<StateOverride> stateOverrides,
                List<PostprocessMark> postprocessMarks,
                List<ScheduledBlockTick> scheduledBlockTicks) {
            String[] biomes = new String[BIOME_COUNT_PER_CHUNK];
            Arrays.fill(biomes, Objects.requireNonNull(biomeKey, "biomeKey"));
            return new CarversChunk(chunkX, chunkZ, blockIds, biomes, stateOverrides,
                    postprocessMarks, scheduledBlockTicks, List.of());
        }

        public static CarversChunk uniformBiome(int chunkX, int chunkZ, short[] blockIds,
                String biomeKey, List<StateOverride> stateOverrides,
                List<PostprocessMark> postprocessMarks,
                List<ScheduledBlockTick> scheduledBlockTicks,
                List<ScheduledFluidTick> scheduledFluidTicks) {
            String[] biomes = new String[BIOME_COUNT_PER_CHUNK];
            Arrays.fill(biomes, Objects.requireNonNull(biomeKey, "biomeKey"));
            return new CarversChunk(chunkX, chunkZ, blockIds, biomes, stateOverrides,
                    postprocessMarks, scheduledBlockTicks, scheduledFluidTicks);
        }

        public static CarversChunk uniformBiome(int chunkX, int chunkZ, short[] blockIds,
                String biomeKey, List<StateOverride> stateOverrides,
                List<PostprocessMark> postprocessMarks,
                List<ScheduledBlockTick> scheduledBlockTicks,
                List<ScheduledFluidTick> scheduledFluidTicks, LightSnapshot lightSnapshot) {
            String[] biomes = new String[BIOME_COUNT_PER_CHUNK];
            Arrays.fill(biomes, Objects.requireNonNull(biomeKey, "biomeKey"));
            return new CarversChunk(chunkX, chunkZ, blockIds, biomes, stateOverrides,
                    postprocessMarks, scheduledBlockTicks, scheduledFluidTicks, lightSnapshot);
        }

        public int chunkX() {
            return chunkX;
        }

        public int chunkZ() {
            return chunkZ;
        }
    }

    /** Immutable per-section SKY/BLOCK nibble arrays captured at the post-CARVERS boundary. */
    public static final class LightSnapshot {
        private static final LightSnapshot ABSENT = new LightSnapshot(
                new byte[SECTION_COUNT][], new byte[SECTION_COUNT][]);
        private final byte[][] skySections;
        private final byte[][] blockSections;

        public LightSnapshot(byte[][] skySections, byte[][] blockSections) {
            this.skySections = copySections(skySections, "sky");
            this.blockSections = copySections(blockSections, "block");
        }

        public static LightSnapshot absent() {
            return ABSENT;
        }

        public static LightSnapshot uniform(int sky, int block) {
            requireLightValue(sky, "sky");
            requireLightValue(block, "block");
            byte[][] skySections = uniformSections(sky);
            byte[][] blockSections = uniformSections(block);
            return new LightSnapshot(skySections, blockSections);
        }

        private int sky(int localX, int blockY, int localZ) {
            return value(skySections, localX, blockY, localZ, 15);
        }

        private int block(int localX, int blockY, int localZ) {
            return value(blockSections, localX, blockY, localZ, 0);
        }

        private static byte[][] copySections(byte[][] sections, String kind) {
            Objects.requireNonNull(sections, kind + " light sections");
            if (sections.length != SECTION_COUNT) {
                throw new IllegalArgumentException(kind + " light section count must be "
                        + SECTION_COUNT + ": " + sections.length);
            }
            byte[][] copy = new byte[SECTION_COUNT][];
            for (int section = 0; section < SECTION_COUNT; section++) {
                byte[] data = sections[section];
                if (data != null && data.length != LIGHT_SECTION_BYTES) {
                    throw new IllegalArgumentException(kind + " light section byte count must be "
                            + LIGHT_SECTION_BYTES + " at " + section + ": " + data.length);
                }
                copy[section] = data == null ? null : data.clone();
            }
            return copy;
        }

        private static byte[][] uniformSections(int value) {
            byte packed = (byte) (value | value << 4);
            byte[][] sections = new byte[SECTION_COUNT][];
            for (int section = 0; section < SECTION_COUNT; section++) {
                sections[section] = new byte[LIGHT_SECTION_BYTES];
                Arrays.fill(sections[section], packed);
            }
            return sections;
        }

        private static int value(byte[][] sections, int localX, int blockY, int localZ,
                int absent) {
            int section = Math.floorDiv(blockY - Blocks.MIN_Y, 16);
            byte[] data = sections[section];
            if (data == null) return absent;
            int nibble = (Math.floorMod(blockY, 16) << 8) | (localZ << 4) | localX;
            int packed = Byte.toUnsignedInt(data[nibble >>> 1]);
            return (nibble & 1) == 0 ? packed & 15 : packed >>> 4;
        }

        private static void requireLightValue(int value, String kind) {
            if (value < 0 || value > 15) {
                throw new IllegalArgumentException(kind + " light is outside 0..15: " + value);
            }
        }
    }

    /** Chunk-local postprocessing coordinate retained in deterministic first-insertion order. */
    public record PostprocessMark(int localX, int blockY, int localZ) {
        public PostprocessMark {
            if (localX < 0 || localX >= Blocks.CHUNK_X
                    || localZ < 0 || localZ >= Blocks.CHUNK_Z
                    || blockY < Blocks.MIN_Y || blockY > Blocks.MAX_Y) {
                throw new IllegalArgumentException("postprocess mark lies outside chunk: "
                        + localX + "," + blockY + "," + localZ);
            }
        }
    }

    /** Sparse exact-state input for distinctions that the unsigned-16 ID cannot represent. */
    public record StateOverride(int localX, int blockY, int localZ,
                                Mc263FeatureBlockState state) {
        public StateOverride {
            if (localX < 0 || localX >= Blocks.CHUNK_X
                    || localZ < 0 || localZ >= Blocks.CHUNK_Z
                    || blockY < Blocks.MIN_Y || blockY > Blocks.MAX_Y) {
                throw new IllegalArgumentException("state override lies outside chunk: "
                        + localX + "," + blockY + "," + localZ);
            }
            Objects.requireNonNull(state, "state");
        }
    }

    /** Chunk-local scheduled block tick retained in deterministic first-insertion order. */
    public record ScheduledBlockTick(int localX, int blockY, int localZ, int blockId,
                                     String blockKey, int delay, int priority,
                                     long subTickOrder) {
        public ScheduledBlockTick(int localX, int blockY, int localZ, int blockId, int delay) {
            this(localX, blockY, localZ, blockId, semanticKeyForLegacyId(blockId), 0, 0, 0L);
        }

        public ScheduledBlockTick(int localX, int blockY, int localZ, int blockId,
                String blockKey, int delay) {
            this(localX, blockY, localZ, blockId, blockKey, 0, 0, 0L);
        }

        public ScheduledBlockTick {
            if (localX < 0 || localX >= Blocks.CHUNK_X
                    || localZ < 0 || localZ >= Blocks.CHUNK_Z
                    || blockY < Blocks.MIN_Y || blockY > Blocks.MAX_Y) {
                throw new IllegalArgumentException("scheduled block tick lies outside chunk: "
                        + localX + "," + blockY + "," + localZ);
            }
            if (blockId < 0 || blockId > 0xffff) {
                throw new IllegalArgumentException(
                        "scheduled block tick ID is outside unsigned-16 range: " + blockId);
            }
            if (delay < 0) {
                throw new IllegalArgumentException(
                        "scheduled block tick delay must be non-negative");
            }
            String validated = requireNamespacedKey(blockKey, "scheduled block tick type");
            if (validated.startsWith("webcraft:")) {
                if (!Mc263FeatureBlockState.legacyTickKeyMatches(blockId, validated)) {
                    throw new IllegalArgumentException("legacy block tick ID/key mismatch: "
                            + blockId + " != " + validated);
                }
            } else {
                Mc263FeatureBlockState state =
                        Mc263FeatureBlockState.forSemanticBlockKey(validated);
                if (state.blockId() != blockId) {
                    throw new IllegalArgumentException("semantic block tick ID/key mismatch: "
                            + blockId + " != " + validated);
                }
            }
            Mc263FinalChunkSidecars.TickPriority.fromValue(priority);
        }

        static ScheduledBlockTick resolved(int localX, int blockY, int localZ, int blockId,
                String blockKey, int delay, int priority, long subTickOrder) {
            return new ScheduledBlockTick(localX, blockY, localZ, blockId, blockKey, delay,
                    priority, subTickOrder);
        }
    }

    /** Chunk-local scheduled fluid tick retained in deterministic first-insertion order. */
    public record ScheduledFluidTick(int localX, int blockY, int localZ,
                                     String fluidKey, int delay, int priority,
                                     long subTickOrder) {
        public ScheduledFluidTick(int localX, int blockY, int localZ,
                String fluidKey, int delay) {
            this(localX, blockY, localZ, fluidKey, 0, 0, 0L);
        }

        public ScheduledFluidTick {
            requireLocalPosition(localX, blockY, localZ, "scheduled fluid tick");
            fluidKey = Mc263FeatureBlockState.requireFluidTickKey(fluidKey);
            if (delay < 0) {
                throw new IllegalArgumentException(
                        "scheduled fluid tick delay must be non-negative");
            }
            Mc263FinalChunkSidecars.TickPriority.fromValue(priority);
        }

        static ScheduledFluidTick resolved(int localX, int blockY, int localZ,
                String fluidKey, int delay, int priority, long subTickOrder) {
            return new ScheduledFluidTick(localX, blockY, localZ, fluidKey, delay, priority,
                    subTickOrder);
        }
    }

    /** Accepted block-entity capability creation in deterministic insertion order. */
    public record BlockEntityCapabilityReceipt(
            int localX, int blockY, int localZ,
            Mc263FeatureBlockState.Capability capability, String exactState) {
        public BlockEntityCapabilityReceipt {
            requireLocalPosition(localX, blockY, localZ, "block-entity capability receipt");
            if (capability == null || capability == Mc263FeatureBlockState.Capability.NONE) {
                throw new IllegalArgumentException("concrete block-entity capability is required");
            }
            Mc263FeatureBlockState state = Mc263FeatureBlockState.fromExact(exactState);
            if (state.capability() != capability) {
                throw new IllegalArgumentException("state/capability mismatch: " + exactState);
            }
            exactState = state.exactState();
        }
    }

    /**
     * Final unopened randomizable-container loot assignment in accepted placement order.
     *
     * <p>{@code facing} is the container block state's own {@code facing} property, which is the
     * full six-direction set: vanilla {@code BarrelBlock.FACING}, {@code DispenserBlock.FACING}
     * and {@code HopperBlock.FACING} are {@code DirectionProperty}s over every direction and
     * structure rotation preserves vertical facings. The pinned 26.3 closure's non-horizontal
     * randomizable containers are {@code barrel[facing=down|up]},
     * {@code dispenser[facing=up]} and {@code hopper[facing=down]}
     * (Mc263AbandonedCampContainerFacingEvidenceTest).</p>
     */
    public record ChestLoot(int localX, int blockY, int localZ, String facing,
                            String lootTable, long lootSeed,
                            LootProductionContext productionContext) {
        public ChestLoot {
            requireLocalPosition(localX, blockY, localZ, "chest loot");
            if (!CONTAINER_FACINGS.contains(facing)) {
                throw new IllegalArgumentException("invalid chest facing: " + facing);
            }
            lootTable = requireMinecraftKey(lootTable, "loot table");
            productionContext = Objects.requireNonNull(
                    productionContext, "production loot context");
            productionContext = requireAuthenticatedContextShape(lootTable,
                    productionContext.originX(), productionContext.originY(),
                    productionContext.originZ(), productionContext);
            if (Math.floorMod(productionContext.originX(), Blocks.CHUNK_X) != localX
                    || productionContext.originY() != blockY
                    || Math.floorMod(productionContext.originZ(), Blocks.CHUNK_Z) != localZ) {
                throw new IllegalArgumentException("chest loot local/context origin mismatch");
            }
        }
    }

    /** Final spawner entity assignment in accepted placement order. */
    public record SpawnerMob(int localX, int blockY, int localZ, String entityType) {
        public SpawnerMob {
            requireLocalPosition(localX, blockY, localZ, "spawner mob");
            requireMinecraftKey(entityType, "entity type");
        }
    }

    /** Final chunk-local procedural ownership, serialized as the owner's raw signed-64 bits. */
    public record OwnedBlock(int localX, int blockY, int localZ, long owner) {
        public OwnedBlock {
            requireLocalPosition(localX, blockY, localZ, "owned block");
        }
    }

    /** Final unopened brushable-block archaeology assignment in accepted placement order. */
    public record ArchaeologyLoot(int localX, int blockY, int localZ, String lootTable,
                                  long lootSeed) {
        public ArchaeologyLoot {
            requireLocalPosition(localX, blockY, localZ, "archaeology loot");
            if (!isSupportedArchaeologyTable(
                    requireMinecraftKey(lootTable, "archaeology loot table"))) {
                throw new IllegalArgumentException("unsupported 26.3 archaeology loot table: "
                        + lootTable);
            }
        }
    }

    /** One exact absolute block write in canonical structure encounter order. */
    record StructureBlockWrite(int blockX, int blockY, int blockZ, String exactState,
                               long owner) {
        StructureBlockWrite {
            Objects.requireNonNull(exactState, "structure exact state");
            exactState = Mc263FeatureBlockState.fromExact(exactState).exactState();
        }
    }

    /** One absolute randomizable-container loot contribution. */
    record StructureLoot(int blockX, int blockY, int blockZ, String lootTable,
                         long lootSeed, LootProductionContext productionContext) {
        StructureLoot {
            lootTable = requireMinecraftKey(lootTable, "structure loot table");
            productionContext = requireAuthenticatedContextShape(lootTable,
                    blockX, blockY, blockZ, productionContext);
        }
    }

    /**
     * One loot-bearing ENTS row and the exact authority context that caused it.  Keeping the two
     * values in one immutable carrier prevents assembly from reconstructing level facts from an
     * entity position after the placing source transaction has closed.
     */
    public record AuthenticatedStructureEntity(
            Mc263FinalChunkSidecars.StructureEntity entity,
            LootProductionContext productionContext) {
        public AuthenticatedStructureEntity {
            entity = Objects.requireNonNull(entity, "loot-bearing structure entity");
            if (entity.lootTable().isEmpty()) {
                throw new IllegalArgumentException(
                        "authenticated structure entity must carry a loot table");
            }
            productionContext = requireAuthenticatedContextShape(entity.lootTable(),
                    structureEntityBlockCoordinate(entity.x(), "structure entity X"),
                    structureEntityBlockCoordinate(entity.y(), "structure entity Y"),
                    structureEntityBlockCoordinate(entity.z(), "structure entity Z"),
                    productionContext);
        }
    }

    /** One absolute brushable-block archaeology contribution. */
    record StructureArchaeology(int blockX, int blockY, int blockZ, String lootTable,
                                long lootSeed) {
        StructureArchaeology {
            Objects.requireNonNull(lootTable, "structure archaeology loot table");
        }
    }

    /** One absolute scheduled fluid tick in canonical structure encounter order. */
    record StructureFluidTick(int blockX, int blockY, int blockZ, String fluidKey,
                              int delay, int priority, long subTickOrder) {
        StructureFluidTick {
            Objects.requireNonNull(fluidKey, "structure fluid tick type");
        }
    }

    /** One absolute scheduled block tick in canonical structure encounter order. */
    record StructureBlockTick(int blockX, int blockY, int blockZ, String blockKey,
                              int delay, int priority, long subTickOrder) {
        StructureBlockTick {
            Objects.requireNonNull(blockKey, "structure block tick type");
        }
    }

    /** One pinned vanilla bee occupant attached to the final staged beehive. */
    record StructureBee(int blockX, int blockY, int blockZ, int ticksInHive) { }

    /** One absolute ProtoChunk postprocessing mark; duplicates preserve encounter order. */
    record StructurePostprocessMark(int blockX, int blockY, int blockZ) { }

    /** One absolute generated spawner assignment. */
    record StructureSpawner(int blockX, int blockY, int blockZ, String entityType) {
        StructureSpawner {
            Objects.requireNonNull(entityType, "structure spawner entity type");
        }
    }

    /** Canonical block-entity evidence at one absolute destination coordinate. */
    record StructureBentEvidence(int blockX, int blockY, int blockZ, String blockIdentity,
                                 String entityType, byte[] canonicalNbt) {
        StructureBentEvidence {
            Objects.requireNonNull(blockIdentity, "structure BENT block identity");
            Objects.requireNonNull(entityType, "structure BENT entity type");
            canonicalNbt = Objects.requireNonNull(
                    canonicalNbt, "structure BENT canonical NBT").clone();
        }

        @Override public byte[] canonicalNbt() {
            return canonicalNbt.clone();
        }

        @Override public boolean equals(Object other) {
            return other instanceof StructureBentEvidence value
                    && blockX == value.blockX && blockY == value.blockY
                    && blockZ == value.blockZ && blockIdentity.equals(value.blockIdentity)
                    && entityType.equals(value.entityType)
                    && Arrays.equals(canonicalNbt, value.canonicalNbt);
        }

        @Override public int hashCode() {
            int result = Objects.hash(blockX, blockY, blockZ, blockIdentity, entityType);
            return 31 * result + Arrays.hashCode(canonicalNbt);
        }
    }

    /** Immutable complete contribution from one ordered hardcoded-structure settlement. */
    record StructureBatch(List<StructureBlockWrite> blocks, List<StructureLoot> loot,
                          List<StructureArchaeology> archaeology,
                          List<StructureBentEvidence> blockEntities,
                          List<Mc263FinalChunkSidecars.StructureEntity> entities,
                          List<AuthenticatedStructureEntity> authenticatedEntities,
                          List<StructureFluidTick> fluidTicks,
                          List<StructurePostprocessMark> postprocessMarks,
                          List<StructureSpawner> spawners,
                          List<StructureBlockTick> blockTicks,
                          List<StructureBee> bees) {
        StructureBatch(List<StructureBlockWrite> blocks, List<StructureLoot> loot,
                List<StructureArchaeology> archaeology,
                List<StructureBentEvidence> blockEntities) {
            this(blocks, loot, archaeology, blockEntities, List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of());
        }

        StructureBatch(List<StructureBlockWrite> blocks, List<StructureLoot> loot,
                List<StructureArchaeology> archaeology,
                List<StructureBentEvidence> blockEntities,
                List<Mc263FinalChunkSidecars.StructureEntity> entities) {
            this(blocks, loot, archaeology, blockEntities, entities, List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of());
        }

        StructureBatch(List<StructureBlockWrite> blocks, List<StructureLoot> loot,
                List<StructureArchaeology> archaeology,
                List<StructureBentEvidence> blockEntities,
                List<Mc263FinalChunkSidecars.StructureEntity> entities,
                List<StructureFluidTick> fluidTicks,
                List<StructurePostprocessMark> postprocessMarks,
                List<StructureSpawner> spawners) {
            this(blocks, loot, archaeology, blockEntities, entities, List.of(), fluidTicks,
                    postprocessMarks, spawners, List.of(), List.of());
        }

        StructureBatch(List<StructureBlockWrite> blocks, List<StructureLoot> loot,
                List<StructureArchaeology> archaeology,
                List<StructureBentEvidence> blockEntities,
                List<Mc263FinalChunkSidecars.StructureEntity> entities,
                List<StructureFluidTick> fluidTicks,
                List<StructurePostprocessMark> postprocessMarks,
                List<StructureSpawner> spawners,
                List<StructureBlockTick> blockTicks,
                List<StructureBee> bees) {
            this(blocks, loot, archaeology, blockEntities, entities, List.of(), fluidTicks,
                    postprocessMarks, spawners, blockTicks, bees);
        }

        StructureBatch {
            blocks = List.copyOf(Objects.requireNonNull(blocks, "structure blocks"));
            loot = List.copyOf(Objects.requireNonNull(loot, "structure loot"));
            archaeology = List.copyOf(Objects.requireNonNull(
                    archaeology, "structure archaeology"));
            blockEntities = List.copyOf(Objects.requireNonNull(
                    blockEntities, "structure BENT"));
            entities = List.copyOf(Objects.requireNonNull(entities, "structure ENTS"));
            authenticatedEntities = List.copyOf(Objects.requireNonNull(
                    authenticatedEntities, "authenticated structure ENTS"));
            List<Mc263FinalChunkSidecars.StructureEntity> lootBearingEntities = entities.stream()
                    .filter(value -> !value.lootTable().isEmpty()).toList();
            List<Mc263FinalChunkSidecars.StructureEntity> authenticatedRows =
                    authenticatedEntities.stream()
                            .map(AuthenticatedStructureEntity::entity).toList();
            if (!lootBearingEntities.equals(authenticatedRows)) {
                throw new IllegalArgumentException(
                        "loot-bearing structure ENTS require exact ordered authenticated carriers");
            }
            fluidTicks = List.copyOf(Objects.requireNonNull(
                    fluidTicks, "structure fluid ticks"));
            postprocessMarks = List.copyOf(Objects.requireNonNull(
                    postprocessMarks, "structure postprocess marks"));
            spawners = List.copyOf(Objects.requireNonNull(spawners, "structure spawners"));
            blockTicks = List.copyOf(Objects.requireNonNull(
                    blockTicks, "structure block ticks"));
            bees = List.copyOf(Objects.requireNonNull(bees, "structure bees"));
        }
    }

    /** Destination identity retained across the loaded +/-2 region. */
    record DestinationPosition(int chunkX, int chunkZ, int packedPosition) {
        DestinationPosition {
            if (packedPosition < 0 || packedPosition >= Blocks.CHUNK_BLOCKS) {
                throw new IllegalArgumentException(
                        "destination packed position lies outside chunk: " + packedPosition);
            }
        }
    }

    /**
     * Immutable generated BENT/ENTS contributions. Raw payloads remain producer-owned, while the
     * center snapshot retains only loot-bearing ENTS/context carriers needed for final LDEC.
     */
    record StructureBatchSettlement(
            Map<DestinationPosition, Mc263FinalChunkSidecars.BlockEntity> blockEntities,
            Map<DestinationPosition,
                    List<Mc263FinalChunkSidecars.StructureEntity>> entities) {
        StructureBatchSettlement {
            blockEntities = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(blockEntities, "settled structure BENT")));
            LinkedHashMap<DestinationPosition,
                    List<Mc263FinalChunkSidecars.StructureEntity>> entityCopy =
                    new LinkedHashMap<>();
            Objects.requireNonNull(entities, "settled structure ENTS").forEach(
                    (destination, values) -> entityCopy.put(destination,
                            List.copyOf(values)));
            entities = Collections.unmodifiableMap(entityCopy);
        }
    }

    private record PreparedStructureBlock(int blockX, int blockY, int blockZ,
                                          Mc263FeatureBlockState state, long owner) { }

    private record PreparedStructureLoot(int blockX, int blockY, int blockZ, String lootTable,
                                         long lootSeed, String facing,
                                         LootProductionContext productionContext) {
        private PreparedStructureLoot {
            productionContext = Objects.requireNonNull(
                    productionContext, "prepared production loot context");
        }
    }

    private record PreparedStructureArchaeology(
            int blockX, int blockY, int blockZ, String lootTable, long lootSeed) { }

    private record PreparedStructureFluidTick(
            int blockX, int blockY, int blockZ, String fluidKey, int delay, int priority,
            long subTickOrder) { }

    private record PreparedStructureBlockTick(
            int blockX, int blockY, int blockZ, int blockId, String blockKey, int delay,
            int priority, long subTickOrder) { }

    private record PreparedStructureBee(
            int blockX, int blockY, int blockZ, int ticksInHive) { }

    private record StructureTickIdentity(
            DestinationPosition destination, String semanticKey) { }

    private record PreparedStructureSpawner(
            int blockX, int blockY, int blockZ, String entityType) { }

    /** One pinned vanilla world-generation bee occupant (minecraft:bee, empty entity data). */
    public record BeehiveOccupant(int ticksInHive) {
        public static final String ENTITY_TYPE = "minecraft:bee";
        public static final int MIN_TICKS_IN_HIVE = 600;

        public BeehiveOccupant {
            if (ticksInHive < 0 || ticksInHive > 598) {
                throw new IllegalArgumentException(
                        "worldgen bee ticks in hive are outside 0..598: " + ticksInHive);
            }
        }
    }

    /** Final live bee-nest payload retained in block-placement insertion order. */
    public static final class BeehiveNest {
        private final int localX;
        private final int blockY;
        private final int localZ;
        private final List<BeehiveOccupant> occupants;

        private BeehiveNest(int localX, int blockY, int localZ) {
            requireLocalPosition(localX, blockY, localZ, "beehive nest");
            this.localX = localX;
            this.blockY = blockY;
            this.localZ = localZ;
            this.occupants = new ArrayList<>();
        }

        private BeehiveNest(BeehiveNest source) {
            localX = source.localX;
            blockY = source.blockY;
            localZ = source.localZ;
            occupants = new ArrayList<>(source.occupants);
        }

        private void addOccupant(BeehiveOccupant occupant) {
            occupants.add(occupant);
        }

        public int localX() { return localX; }
        public int blockY() { return blockY; }
        public int localZ() { return localZ; }
        public List<BeehiveOccupant> occupants() { return List.copyOf(occupants); }
    }

    public static final class CenterSnapshot {
        private final int chunkX;
        private final int chunkZ;
        private final short[] blockIds;
        private final int[] exactStateOverridePositions;
        private final Mc263FeatureBlockState[] exactStateOverrideStates;
        private final String[] biomeKeys;
        private final int[] worldSurfaceWg;
        private final int[] oceanFloorWg;
        private final int[] motionBlocking;
        private final List<List<PostprocessMark>> postprocessMarksBySection;
        private final List<ScheduledBlockTick> scheduledBlockTicks;
        private final List<ScheduledFluidTick> scheduledFluidTicks;
        private final List<BlockEntityCapabilityReceipt> capabilityReceipts;
        private final List<ChestLoot> chestLoot;
        private final List<SpawnerMob> spawnerMobs;
        private final List<OwnedBlock> ownedBlocks;
        private final List<ArchaeologyLoot> archaeologyLoot;
        private final List<BeehiveNest> beehiveNests;
        private final List<AuthenticatedStructureEntity> authenticatedStructureEntities;

        private CenterSnapshot(int chunkX, int chunkZ, short[] blockIds,
                Map<Integer, Mc263FeatureBlockState> exactStateOverrides,
                String[] biomeKeys,
                int[] worldSurfaceWg, int[] oceanFloorWg, int[] motionBlocking,
                List<List<PostprocessMark>> postprocessMarksBySection,
                List<ScheduledBlockTick> scheduledBlockTicks,
                List<ScheduledFluidTick> scheduledFluidTicks,
                List<BlockEntityCapabilityReceipt> capabilityReceipts,
                List<ChestLoot> chestLoot, List<SpawnerMob> spawnerMobs,
                Map<Integer, Long> ownership, List<ArchaeologyLoot> archaeologyLoot,
                List<BeehiveNest> beehiveNests,
                List<AuthenticatedStructureEntity> authenticatedStructureEntities) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.blockIds = blockIds.clone();
            this.biomeKeys = biomeKeys.clone();
            this.exactStateOverridePositions = new int[exactStateOverrides.size()];
            int overrideIndex = 0;
            for (int position : exactStateOverrides.keySet()) {
                this.exactStateOverridePositions[overrideIndex++] = position;
            }
            Arrays.sort(this.exactStateOverridePositions);
            this.exactStateOverrideStates =
                    new Mc263FeatureBlockState[this.exactStateOverridePositions.length];
            for (int index = 0; index < this.exactStateOverridePositions.length; index++) {
                this.exactStateOverrideStates[index] =
                        exactStateOverrides.get(this.exactStateOverridePositions[index]);
            }
            this.worldSurfaceWg = worldSurfaceWg.clone();
            this.oceanFloorWg = oceanFloorWg.clone();
            this.motionBlocking = motionBlocking.clone();
            this.postprocessMarksBySection = immutablePostprocessSections(
                    postprocessMarksBySection);
            this.scheduledBlockTicks = List.copyOf(scheduledBlockTicks);
            this.scheduledFluidTicks = List.copyOf(scheduledFluidTicks);
            this.capabilityReceipts = List.copyOf(capabilityReceipts);
            this.chestLoot = List.copyOf(chestLoot);
            this.spawnerMobs = List.copyOf(spawnerMobs);
            this.ownedBlocks = ownership.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> {
                        int packed = entry.getKey();
                        int localX = packed & 15;
                        int localZ = (packed >>> 4) & 15;
                        int blockY = (packed >>> 8) + Blocks.MIN_Y;
                        return new OwnedBlock(localX, blockY, localZ, entry.getValue());
                    })
                    .toList();
            this.archaeologyLoot = List.copyOf(archaeologyLoot);
            this.beehiveNests = beehiveNests.stream().map(BeehiveNest::new).toList();
            this.authenticatedStructureEntities = List.copyOf(
                    authenticatedStructureEntities);
        }

        public int chunkX() {
            return chunkX;
        }

        public int chunkZ() {
            return chunkZ;
        }

        public short[] blockIds() {
            return blockIds.clone();
        }

        /** Resolves one exact state without materializing a 98,304-reference state array. */
        public Mc263FeatureBlockState blockState(int localX, int blockY, int localZ) {
            requireLocalPosition(localX, blockY, localZ, "center block state");
            int packed = Blocks.blockIndex(localX, blockY, localZ);
            int overrideIndex = Arrays.binarySearch(exactStateOverridePositions, packed);
            return overrideIndex >= 0 ? exactStateOverrideStates[overrideIndex]
                    : Mc263FeatureBlockState.defaultForId(Short.toUnsignedInt(blockIds[packed]));
        }

        /** Exact three-dimensional biome at a center-chunk block, retained for loot context. */
        public String biomeKey(int localX, int blockY, int localZ) {
            requireLocalPosition(localX, blockY, localZ, "center biome");
            int quartX = localX >>> 2;
            int quartZ = localZ >>> 2;
            int quartY = Math.max(MIN_QUART_Y,
                    Math.min(MAX_QUART_Y, Math.floorDiv(blockY, 4))) - MIN_QUART_Y;
            return biomeKeys[biomeIndex(quartX, quartY, quartZ)];
        }

        public int exactStateOverrideCount() {
            return exactStateOverridePositions.length;
        }

        public int[] worldSurfaceWg() {
            return worldSurfaceWg.clone();
        }

        public int[] oceanFloorWg() {
            return oceanFloorWg.clone();
        }

        public int[] motionBlocking() {
            return motionBlocking.clone();
        }

        public List<PostprocessMark> postprocessMarks() {
            List<PostprocessMark> flattened = new ArrayList<>();
            for (List<PostprocessMark> section : postprocessMarksBySection) {
                flattened.addAll(section);
            }
            return List.copyOf(flattened);
        }

        public List<List<PostprocessMark>> postprocessMarksBySection() {
            return postprocessMarksBySection;
        }

        public List<ScheduledBlockTick> scheduledBlockTicks() {
            return scheduledBlockTicks;
        }

        public List<ScheduledFluidTick> scheduledFluidTicks() {
            return scheduledFluidTicks;
        }

        short[] borrowedBlockIds() {
            return blockIds;
        }

        int[] borrowedExactStateOverridePositions() {
            return exactStateOverridePositions;
        }

        Mc263FeatureBlockState[] borrowedExactStateOverrideStates() {
            return exactStateOverrideStates;
        }

        public List<BlockEntityCapabilityReceipt> capabilityReceipts() {
            return capabilityReceipts;
        }

        public List<ChestLoot> chestLoot() {
            return chestLoot;
        }

        public List<SpawnerMob> spawnerMobs() {
            return spawnerMobs;
        }

        public List<OwnedBlock> ownedBlocks() {
            return ownedBlocks;
        }

        public List<ArchaeologyLoot> archaeologyLoot() {
            return archaeologyLoot;
        }

        public List<BeehiveNest> beehiveNests() {
            return beehiveNests;
        }

        public List<AuthenticatedStructureEntity> authenticatedStructureEntities() {
            return authenticatedStructureEntities;
        }
    }

    record SourceChunk(int chunkX, int chunkZ) {
    }

    private static final class MutableChunk {
        private final short[] carversBlocks;
        private final Map<Integer, Mc263FeatureBlockState> carversStateOverrides;
        private final String[] biomes;
        private final int[] worldSurface;
        private final int[] oceanFloor;
        private final int[] motionBlocking;
        private final List<List<PostprocessMark>> postprocessBySection;
        private final Map<TickKey, ScheduledBlockTick> scheduledBlockTicks;
        private final List<ScheduledFluidTick> scheduledFluidTicks;
        private final Set<TickKey> scheduledFluidTickPresence;
        private final Map<Integer, Mc263FeatureBlockState.Capability> liveCapabilities;
        private final List<BlockEntityCapabilityReceipt> capabilityReceipts;
        private final Map<Integer, ChestLoot> chestLoot;
        private final Map<Integer, SpawnerMob> spawnerMobs;
        private final Map<Integer, Long> ownership;
        private final Map<Integer, ArchaeologyLoot> archaeologyLoot;
        private final Map<Integer, BeehiveNest> beehiveNests;
        private final int initialHeightmapBlockLoads;
        private final LightSnapshot lightSnapshot;
        private short[] mutableBlocks;
        private Map<Integer, Mc263FeatureBlockState> mutableStateOverrides;
        /**
         * One bit per block position, set exactly when the live override map holds that position.
         *
         * <p>AGENTS 10l: the exact-state overrides are sparse — a few hundred positions in
         * 98,304 — but every FEATURES read probed the boxed {@code Map<Integer, ...>} for all of
         * them, once per ore cell, once per air probe, once per heightmap rescan. This bitmap
         * answers the common "no override here" case with one array read and a shift, and the map
         * is consulted only where a bit really is set. It is derived from the same map it guards
         * and is updated on the same two mutation paths, so it cannot drift.</p>
         */
        private final long[] overridePresence;

        private MutableChunk(CarversChunk input, IntPredicate worldSurfaceBlock,
                IntPredicate oceanFloorBlock, IntPredicate motionBlockingBlock) {
            carversBlocks = input.blockIds;
            carversStateOverrides = input.exactStateOverrides;
            overridePresence = new long[(Blocks.CHUNK_BLOCKS + 63) >>> 6];
            for (Integer packed : carversStateOverrides.keySet()) markOverride(packed, true);
            biomes = input.biomeKeys;
            lightSnapshot = input.lightSnapshot;
            worldSurface = new int[Blocks.CHUNK_X * Blocks.CHUNK_Z];
            oceanFloor = new int[Blocks.CHUNK_X * Blocks.CHUNK_Z];
            motionBlocking = new int[Blocks.CHUNK_X * Blocks.CHUNK_Z];
            initialHeightmapBlockLoads = input.copyHeightmapsInto(
                    worldSurfaceBlock, oceanFloorBlock, motionBlockingBlock,
                    worldSurface, oceanFloor, motionBlocking);
            postprocessBySection = new ArrayList<>(SECTION_COUNT);
            for (int section = 0; section < SECTION_COUNT; section++) {
                postprocessBySection.add(new ArrayList<>());
            }
            for (PostprocessMark mark : input.postprocessMarks) {
                int section = Math.floorDiv(mark.blockY() - Blocks.MIN_Y, 16);
                postprocessBySection.get(section).add(mark);
            }
            scheduledBlockTicks = new LinkedHashMap<>();
            for (ScheduledBlockTick tick : input.scheduledBlockTicks) {
                int packed = Blocks.blockIndex(tick.localX(), tick.blockY(), tick.localZ());
                scheduledBlockTicks.putIfAbsent(
                        new TickKey(packed, tick.blockKey()), ScheduledBlockTick.resolved(
                                tick.localX(), tick.blockY(), tick.localZ(), tick.blockId(),
                                tick.blockKey(), tick.delay(), tick.priority(),
                                scheduledBlockTicks.size()));
            }
            scheduledFluidTicks = new ArrayList<>(input.scheduledFluidTicks.size());
            scheduledFluidTickPresence = new LinkedHashSet<>();
            for (ScheduledFluidTick tick : input.scheduledFluidTicks) {
                int packed = Blocks.blockIndex(tick.localX(), tick.blockY(), tick.localZ());
                scheduledFluidTicks.add(ScheduledFluidTick.resolved(
                        tick.localX(), tick.blockY(), tick.localZ(), tick.fluidKey(),
                        tick.delay(), tick.priority(), scheduledFluidTicks.size()));
                scheduledFluidTickPresence.add(new TickKey(packed, tick.fluidKey()));
            }
            liveCapabilities = new LinkedHashMap<>();
            capabilityReceipts = new ArrayList<>();
            chestLoot = new LinkedHashMap<>();
            spawnerMobs = new LinkedHashMap<>();
            ownership = new LinkedHashMap<>();
            archaeologyLoot = new LinkedHashMap<>();
            beehiveNests = new LinkedHashMap<>();
        }

        private short[] blocks() {
            return mutableBlocks == null ? carversBlocks : mutableBlocks;
        }

        private String[] biomes() {
            return biomes;
        }

        private boolean hasOverride(int packed) {
            return (overridePresence[packed >>> 6] & (1L << (packed & 63))) != 0L;
        }

        private void markOverride(int packed, boolean present) {
            if (present) overridePresence[packed >>> 6] |= 1L << (packed & 63);
            else overridePresence[packed >>> 6] &= ~(1L << (packed & 63));
        }

        private Mc263FeatureBlockState blockState(int packed) {
            if (hasOverride(packed)) {
                Map<Integer, Mc263FeatureBlockState> overrides = mutableStateOverrides == null
                        ? carversStateOverrides : mutableStateOverrides;
                Mc263FeatureBlockState override = overrides.get(packed);
                if (override != null) return override;
            }
            return Mc263FeatureBlockState.defaultForId(Short.toUnsignedInt(blocks()[packed]));
        }

        private Mc263FeatureBlockState blockStateOrNull(int packed) {
            if (hasOverride(packed)) {
                Map<Integer, Mc263FeatureBlockState> overrides = mutableStateOverrides == null
                        ? carversStateOverrides : mutableStateOverrides;
                Mc263FeatureBlockState override = overrides.get(packed);
                if (override != null) return override;
            }
            return Mc263FeatureBlockState.defaultOrNullForId(
                    Short.toUnsignedInt(blocks()[packed]));
        }

        private boolean hasExactState(int packed) {
            return hasOverride(packed);
        }

        private boolean hasLiveCapability(int packed) {
            return liveCapabilities.containsKey(packed);
        }

        private boolean hasOwnership(int packed) {
            return ownership.containsKey(packed);
        }

        private boolean hasArchaeologyLoot(int packed) {
            return archaeologyLoot.containsKey(packed);
        }

        private void setExactState(int packed, Mc263FeatureBlockState state) {
            Mc263FeatureBlockState defaultState =
                    Mc263FeatureBlockState.defaultForId(state.blockId());
            Map<Integer, Mc263FeatureBlockState> current = mutableStateOverrides == null
                    ? carversStateOverrides : mutableStateOverrides;
            Mc263FeatureBlockState old = hasOverride(packed) ? current.get(packed) : null;
            boolean needsOverride = !state.equals(defaultState);
            if ((needsOverride && state.equals(old)) || (!needsOverride && old == null)) return;
            if (mutableStateOverrides == null) {
                mutableStateOverrides = new LinkedHashMap<>(carversStateOverrides);
            }
            if (needsOverride) mutableStateOverrides.put(packed, state);
            else mutableStateOverrides.remove(packed);
            markOverride(packed, needsOverride);
        }

        private Map<Integer, Mc263FeatureBlockState> exactStateOverrides() {
            return mutableStateOverrides == null ? carversStateOverrides : mutableStateOverrides;
        }

        private void acceptBlockEntityWrite(int packed, Mc263FeatureBlockState state,
                BlockEntityCapabilityReceipt receipt) {
            liveCapabilities.remove(packed);
            chestLoot.remove(packed);
            spawnerMobs.remove(packed);
            archaeologyLoot.remove(packed);
            beehiveNests.remove(packed);
            if (receipt == null) return;
            liveCapabilities.put(packed, state.capability());
            capabilityReceipts.add(receipt);
            if (state.capability() == Mc263FeatureBlockState.Capability.BEEHIVE) {
                beehiveNests.put(packed, new BeehiveNest(
                        receipt.localX(), receipt.blockY(), receipt.localZ()));
            }
        }

        private void acceptLegacyIdWrite(int packed) {
            if (mutableStateOverrides == null && hasOverride(packed)) {
                mutableStateOverrides = new LinkedHashMap<>(carversStateOverrides);
            }
            if (mutableStateOverrides != null) mutableStateOverrides.remove(packed);
            markOverride(packed, false);
            liveCapabilities.remove(packed);
            chestLoot.remove(packed);
            spawnerMobs.remove(packed);
            archaeologyLoot.remove(packed);
            beehiveNests.remove(packed);
            ownership.remove(packed);
        }

        private void acceptOwnershipWrite(int packed, boolean owned, long owner) {
            if (owned) ownership.put(packed, owner);
            else ownership.remove(packed);
        }
    }

    private record TickKey(int packedPosition, String blockKey) {
    }

    private enum HeightmapKind {
        WORLD_SURFACE,
        OCEAN_FLOOR,
        MOTION_BLOCKING
    }
}
