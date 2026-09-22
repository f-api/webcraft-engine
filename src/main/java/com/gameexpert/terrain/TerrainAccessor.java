package com.gameexpert.terrain;

import com.gameexpert.authority.versioned.NeutralFinalChunk;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import com.gameexpert.engine.BlockFamilies;
import com.gameexpert.engine.Fluids;
import com.gameexpert.engine.SurfaceDecorator;
import com.gameexpert.engine.TickSafetyTelemetry;

import com.gameexpert.block.entity.WorldBlockDiff;
import com.gameexpert.block.repository.WorldBlockDiffRepository;
import com.gameexpert.common.LongOpenHashMap;
import com.gameexpert.common.LongObjectOpenHashMap;

/**
 * [제공코드] 월드당 블록 읽기/쓰기 뷰(틱 스레드 전용).
 *
 * 읽기 우선순위: 런타임 오버레이 → 런타임 시작 시 고정한 DB diff → {@link ChunkGenerator}(월드별
 * LRU 512 캐시). 독립 fixture는 초기 스냅샷을 주입하지 않은 경우에만 청크별 diff를 지연 조회합니다.
 * 유체·환경 데미지·블록 편집 검증이 이 뷰로 현재 블록을 조회합니다.
 *
 * <b>스레딩</b>: 오버레이/캐시/로드 셋은 동기화하지 않습니다. 반드시 단일 틱 스레드에서만 호출하세요.
 * DB는 청크 최초 접근 시 <i>읽기</i>만 하며, 쓰기(diff 영속화)는 persistence executor가 담당합니다.
 */
public final class TerrainAccessor {

    /**
     * Immutable, target-chunk-local natural decoration patch.
     *
     * <p>The activation worker folds the established decoration order into primitive arrays. Runtime reads keep
     * later player/structure edits above this layer, while snapshots merge this patch without retaining one
     * {@code BlockPos}, revision object, and boxed map entry per ore or geology cell.</p>
     */
    public static final class ReplayableChunkPatch {
        private static final ReplayableChunkPatch EMPTY =
                new ReplayableChunkPatch(new int[0], new short[0], new byte[0]);
        private static final ThreadLocal<BuilderScratch> BUILDER_SCRATCH =
                ThreadLocal.withInitial(BuilderScratch::new);

        private final int[] blockIndices;
        private final short[] blockTypes;
        private final byte[] blockStates;
        /**
         * Exact point reads dominate random ticks and light propagation. A chunk has a fixed 98,304-cell
         * address space, so direct ordinal lookup is both bounded and collision-free; values are ordinal+1.
         */
        private final int[] ordinalTable;

        private ReplayableChunkPatch(int[] blockIndices, short[] blockTypes, byte[] blockStates) {
            this.blockIndices = blockIndices;
            this.blockTypes = blockTypes;
            this.blockStates = blockStates;
            this.ordinalTable = blockIndices.length == 0
                    ? new int[0] : new int[Blocks.CHUNK_BLOCKS];
            for (int ordinal = 0; ordinal < blockIndices.length; ordinal++) {
                ordinalTable[blockIndices[ordinal]] = ordinal + 1;
            }
        }

        public static Builder builder(int chunkX, int chunkZ) {
            return new Builder(chunkX, chunkZ);
        }

        public int size() {
            return blockIndices.length;
        }

        public boolean isEmpty() {
            return blockIndices.length == 0;
        }

        public int blockIndexAt(int ordinal) {
            return blockIndices[ordinal];
        }

        public int blockTypeAtOrdinal(int ordinal) {
            return Short.toUnsignedInt(blockTypes[ordinal]);
        }

        public int blockStateAtOrdinal(int ordinal) {
            return Byte.toUnsignedInt(blockStates[ordinal]);
        }

        private int findOrdinal(int blockIndex) {
            return Arrays.binarySearch(blockIndices, blockIndex);
        }

        private int exactOrdinal(int blockIndex) {
            if (ordinalTable.length == 0) return -1;
            int encoded = ordinalTable[blockIndex];
            return encoded == 0 ? -1 : encoded - 1;
        }

        private int firstOrdinalAtOrAfter(int blockIndex) {
            int ordinal = findOrdinal(blockIndex);
            return ordinal >= 0 ? ordinal : -ordinal - 1;
        }

        private int blockTypeAt(int blockIndex) {
            int ordinal = exactOrdinal(blockIndex);
            return ordinal < 0 ? -1 : Short.toUnsignedInt(blockTypes[ordinal]);
        }

        private int blockStateAt(int blockIndex) {
            int ordinal = exactOrdinal(blockIndex);
            return ordinal < 0 ? 0 : Byte.toUnsignedInt(blockStates[ordinal]);
        }

        private void applyTo(short[] types, byte[] states) {
            for (int ordinal = 0; ordinal < blockIndices.length; ordinal++) {
                int index = blockIndices[ordinal];
                types[index] = blockTypes[ordinal];
                states[index] = blockStates[ordinal];
            }
        }

        private static final class BuilderScratch {
            private final int[] blockTypes = new int[Blocks.CHUNK_BLOCKS];
            private final byte[] blockStates = new byte[Blocks.CHUNK_BLOCKS];
            private int[] changedIndices = new int[1024];
            private int changedCount;
            private boolean inUse;

            private BuilderScratch() {
                Arrays.fill(blockTypes, -1);
            }

            private void acquire() {
                if (inUse) {
                    throw new IllegalStateException("같은 worker에서 자연 패치 builder를 중첩 사용할 수 없습니다");
                }
                inUse = true;
            }

            private void addChangedIndex(int index) {
                if (changedCount == changedIndices.length) {
                    changedIndices = Arrays.copyOf(changedIndices, changedIndices.length << 1);
                }
                changedIndices[changedCount++] = index;
            }

            private void release() {
                for (int ordinal = 0; ordinal < changedCount; ordinal++) {
                    int index = changedIndices[ordinal];
                    blockTypes[index] = -1;
                    blockStates[index] = 0;
                }
                changedCount = 0;
                inUse = false;
            }
        }

        /** Single-use worker-side builder. Later additions at the same cell win, matching the old apply loop. */
        public static final class Builder implements AutoCloseable {
            private final int chunkX;
            private final int chunkZ;
            private final BuilderScratch scratch;
            private boolean closed;

            private Builder(int chunkX, int chunkZ) {
                this.chunkX = chunkX;
                this.chunkZ = chunkZ;
                this.scratch = BUILDER_SCRATCH.get();
                this.scratch.acquire();
            }

            public void put(int x, int y, int z, int blockType, int blockState) {
                ensureOpen();
                if (Math.floorDiv(x, Blocks.CHUNK_X) != chunkX
                        || Math.floorDiv(z, Blocks.CHUNK_Z) != chunkZ) {
                    throw new IllegalArgumentException("자연 패치가 대상 청크를 벗어났습니다: "
                            + chunkX + "," + chunkZ + " <- " + x + "," + z);
                }
                if (y < Blocks.MIN_Y || y > Blocks.MAX_Y
                        || blockType < 0 || blockType > 0xffff
                        || blockState < 0 || blockState > 0xff) {
                    throw new IllegalArgumentException("자연 패치 셀 값이 올바르지 않습니다");
                }
                int index = Blocks.blockIndex(Math.floorMod(x, Blocks.CHUNK_X), y,
                        Math.floorMod(z, Blocks.CHUNK_Z));
                if (scratch.blockTypes[index] == -1) {
                    scratch.addChangedIndex(index);
                }
                scratch.blockTypes[index] = blockType;
                scratch.blockStates[index] = (byte) blockState;
            }

            public ReplayableChunkPatch build() {
                ensureOpen();
                try {
                    if (scratch.changedCount == 0) return EMPTY;
                    Arrays.sort(scratch.changedIndices, 0, scratch.changedCount);
                    int[] indices = Arrays.copyOf(scratch.changedIndices, scratch.changedCount);
                    short[] types = new short[scratch.changedCount];
                    byte[] states = new byte[scratch.changedCount];
                    for (int ordinal = 0; ordinal < scratch.changedCount; ordinal++) {
                        int index = indices[ordinal];
                        types[ordinal] = (short) scratch.blockTypes[index];
                        states[ordinal] = scratch.blockStates[index];
                    }
                    return new ReplayableChunkPatch(indices, types, states);
                } finally {
                    close();
                }
            }

            @Override
            public void close() {
                if (closed) return;
                closed = true;
                scratch.release();
            }

            private void ensureOpen() {
                if (closed) throw new IllegalStateException("이미 완료된 자연 패치 builder입니다");
            }
        }
    }

    /** 부재를 가짜 공기로 바꾸지 않는 상주 블록 조회 결과입니다. */
    public static final class ResidentBlock {
        private static final ResidentBlock UNAVAILABLE = new ResidentBlock(false, Blocks.AIR);
        private static final ResidentBlock[] BYTE_BLOCKS = byteBlocks();
        private final boolean available;
        private final int blockType;

        private ResidentBlock(boolean available, int blockType) {
            this.available = available;
            this.blockType = blockType;
        }

        public static ResidentBlock unavailable() {
            return UNAVAILABLE;
        }

        public static ResidentBlock available(int blockType) {
            return blockType >= 0 && blockType < BYTE_BLOCKS.length
                    ? BYTE_BLOCKS[blockType]
                    : new ResidentBlock(true, blockType);
        }

        public boolean isAvailable() {
            return available;
        }

        public int blockType() {
            if (!available) throw new IllegalStateException("상주하지 않는 블록입니다");
            return blockType;
        }

        private static ResidentBlock[] byteBlocks() {
            ResidentBlock[] blocks = new ResidentBlock[256];
            for (int i = 0; i < blocks.length; i++) blocks[i] = new ResidentBlock(true, i);
            return blocks;
        }
    }

    /** 최초 런타임 접근 청크 알림. 콜백은 동일 틱 스레드에서 동기 실행됩니다. */
    @FunctionalInterface
    public interface ChunkAccessListener {
        void onFirstAccess(int chunkX, int chunkZ, List<WorldBlockDiff> persistedDiffs);
    }

    /** Carrier-aware form used by the world owner; legacy diagnostics may keep the three-argument listener. */
    @FunctionalInterface
    public interface PreparedChunkAccessListener {
        void onFirstAccess(PreparedActivationClaim activation);
    }

    /** LRU에서 빠진 청크의 게임 활성화 bookkeeping도 owner가 함께 비울 수 있게 하는 알림입니다. */
    @FunctionalInterface
    public interface ChunkEvictionListener {
        void onEvicted(int chunkX, int chunkZ);
    }

    /** True generated-cache capacity eviction completed after its resident cleanup callback. */
    @FunctionalInterface
    public interface CapacityEvictionListener {
        void onCapacityEvicted(int chunkX, int chunkZ);
    }

    /** 이미 로드된 한 청크의 오버레이 포함 블록 스냅샷 순회 콜백. */
    @FunctionalInterface
    public interface ChunkBlockVisitor {
        void visit(int x, int y, int z, int blockType);
    }

    /**
     * Immutable-by-convention cold chunk material prepared away from the world owner.  The worker never writes
     * the accessor caches; the owner chooses either activation or snapshot-only adoption.
     */
    public static final class PreparedChunk {
        private final int chunkX;
        private final int chunkZ;
        private final List<WorldBlockDiff> persistedDiffs;
        /** Packed on the preparing worker, so adoption on the world owner does no per-cell work. */
        private final PalettedBlocks packedBlocks;
        private final short[] terrainSurfaceHeights;
        private final NeutralFinalChunk finalLiveCarrier;
        private final long diffHydrationNanos;
        private final long terrainGenerationNanos;

        private PreparedChunk(int chunkX, int chunkZ, List<WorldBlockDiff> persistedDiffs,
                short[] generatedBlocks, short[] terrainSurfaceHeights,
                NeutralFinalChunk finalLiveCarrier,
                long diffHydrationNanos, long terrainGenerationNanos) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.persistedDiffs = persistedDiffs;
            this.packedBlocks = PalettedBlocks.pack(generatedBlocks);
            this.terrainSurfaceHeights = terrainSurfaceHeights;
            this.finalLiveCarrier = finalLiveCarrier;
            this.diffHydrationNanos = diffHydrationNanos;
            this.terrainGenerationNanos = terrainGenerationNanos;
        }

        public int chunkX() {
            return chunkX;
        }

        public int chunkZ() {
            return chunkZ;
        }

        public List<WorldBlockDiff> persistedDiffs() {
            return persistedDiffs;
        }

        private PalettedBlocks packedBlocks() {
            return packedBlocks;
        }

        private short[] terrainSurfaceHeights() {
            return terrainSurfaceHeights;
        }

        public NeutralFinalChunk finalLiveCarrier() {
            return finalLiveCarrier;
        }

        public long diffHydrationNanos() {
            return diffHydrationNanos;
        }

        public long terrainGenerationNanos() {
            return terrainGenerationNanos;
        }
    }

    /** Immutable explicit gameplay-activation handoff; it is intentionally separate from a block read callback. */
    public static final class PreparedActivationClaim {
        private final int chunkX;
        private final int chunkZ;
        private final List<WorldBlockDiff> persistedDiffs;
        private final FinalLiveCarrierClaim finalLiveCarrierClaim;

        private PreparedActivationClaim(int chunkX, int chunkZ, List<WorldBlockDiff> persistedDiffs,
                FinalLiveCarrierClaim finalLiveCarrierClaim) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.persistedDiffs = List.copyOf(persistedDiffs);
            this.finalLiveCarrierClaim = finalLiveCarrierClaim;
        }

        public int chunkX() {
            return chunkX;
        }

        public int chunkZ() {
            return chunkZ;
        }

        public List<WorldBlockDiff> persistedDiffs() {
            return persistedDiffs;
        }

        /** Delivered once per residency; unacknowledged lanes are redelivered after reactivation. */
        public java.util.Optional<NeutralFinalChunk> finalLiveCarrier() {
            return finalLiveCarrierClaim().map(FinalLiveCarrierClaim::carrier);
        }

        public java.util.Optional<FinalLiveCarrierClaim> finalLiveCarrierClaim() {
            return java.util.Optional.ofNullable(finalLiveCarrierClaim);
        }
    }

    /** Independently acknowledged semantic lanes in the immutable final carrier. */
    public enum FinalLiveCarrierLane {
        BLOCK_TICKS, FLUID_TICKS, LOOT, SPAWNERS, OWNERS, ARCHAEOLOGY, BEES,
        BLOCK_ENTITIES, ENTITIES
    }

    /** Generation-bound delivery token. A stale token can never acknowledge a replacement carrier. */
    public record FinalLiveCarrierClaim(
            int chunkX, int chunkZ, long carrierRevision, long deliveryRevision,
            Set<FinalLiveCarrierLane> lanes,
            NeutralFinalChunk carrier) {
        public FinalLiveCarrierClaim {
            lanes = Set.copyOf(lanes);
            java.util.Objects.requireNonNull(carrier, "carrier");
        }
    }

    private static final class FinalLiveCarrierLedger {
        private final long revision;
        private final EnumSet<FinalLiveCarrierLane> unacknowledged;
        private long activeDeliveryRevision;

        private FinalLiveCarrierLedger(long revision,
                EnumSet<FinalLiveCarrierLane> unacknowledged) {
            this.revision = revision;
            this.unacknowledged = unacknowledged;
        }
    }

    /**
     * Immutable resident source handed to the snapshot sender. Its base terrain short[] is never mutated and its
     * sparse override map is frozen at handoff, so a sender can scan it without touching owner state.
     */
    public static final class SnapshotSource {
        private static final java.util.concurrent.atomic.AtomicLong SERIALS =
                new java.util.concurrent.atomic.AtomicLong();
        /** Unique per instance (never 0), for caches that must compare identity without retaining it. */
        private final long serial = SERIALS.incrementAndGet();
        private final int chunkX;
        private final int chunkZ;
        private final PalettedBlocks generatedBlocks;
        /** Lazily built block-ID membership of this immutable source; see {@link #mayContain}. */
        private volatile long[] presentBlockIds;
        private final ReplayableChunkPatch replayablePatch;
        private final int[] overrideKeys;
        private final short[] overrideTypes;
        private final byte[] overrideStates;
        private final short[] terrainSurfaceHeights;
        private final NeutralFinalChunk finalLiveCarrier;
        private final int[] finalStateKeys;
        private final byte[] finalStateCodes;

        private SnapshotSource(int chunkX, int chunkZ, PalettedBlocks generatedBlocks,
                ReplayableChunkPatch replayablePatch, Map<Integer, SnapshotCell> overrides,
                short[] terrainSurfaceHeights,
                NeutralFinalChunk finalLiveCarrier) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.generatedBlocks = generatedBlocks;
            this.replayablePatch = replayablePatch;
            this.finalLiveCarrier = finalLiveCarrier;
            Map<Integer, NeutralFinalChunk.StateOverride> finalStates =
                    finalLiveCarrier == null ? Map.of() : finalLiveCarrier.stateOverrides();
            int finalCapacity = 2;
            while (finalCapacity < finalStates.size() * 2) finalCapacity <<= 1;
            this.finalStateKeys = new int[finalCapacity];
            this.finalStateCodes = new byte[finalCapacity];
            int finalMask = finalCapacity - 1;
            for (Map.Entry<Integer, NeutralFinalChunk.StateOverride> entry : finalStates.entrySet()) {
                int blockIndex = entry.getKey();
                int slot = blockIndex * 0x9E3779B9 & finalMask;
                while (finalStateKeys[slot] != 0) slot = (slot + 1) & finalMask;
                finalStateKeys[slot] = blockIndex + 1;
                finalStateCodes[slot] = (byte) entry.getValue().stateCode();
            }
            int capacity = 2;
            while (capacity < overrides.size() * 2) capacity <<= 1;
            this.overrideKeys = new int[capacity];
            this.overrideTypes = new short[capacity];
            this.overrideStates = new byte[capacity];
            int mask = capacity - 1;
            for (Map.Entry<Integer, SnapshotCell> entry : overrides.entrySet()) {
                int blockIndex = entry.getKey();
                int slot = blockIndex * 0x9E3779B9 & mask;
                while (overrideKeys[slot] != 0) slot = (slot + 1) & mask;
                overrideKeys[slot] = blockIndex + 1;
                overrideTypes[slot] = (short) entry.getValue().blockType;
                overrideStates[slot] = (byte) entry.getValue().blockState;
            }
            this.terrainSurfaceHeights = terrainSurfaceHeights;
        }

        public long serial() {
            return serial;
        }

        /**
         * False when no cell of this chunk holds {@code blockId}. Scans of a whole neighbourhood use it to skip
         * a chunk without reading its 98,304 cells; the answer comes from the stored palettes plus the patch and
         * override cells layered on them, so a true answer may still be a superset.
         */
        public boolean mayContain(int blockId) {
            if (blockId < 0 || blockId >= Blocks.BLOCK_ID_TABLE_CAPACITY) return true;
            long[] present = presentBlockIds;
            if (present == null) {
                present = new long[(Blocks.BLOCK_ID_TABLE_CAPACITY + 63) >>> 6];
                long[] bits = present;
                generatedBlocks.collectBlockIds(id -> {
                    if (id < Blocks.BLOCK_ID_TABLE_CAPACITY) bits[id >>> 6] |= 1L << id;
                });
                for (int ordinal = 0; ordinal < replayablePatch.size(); ordinal++) {
                    int id = replayablePatch.blockTypeAtOrdinal(ordinal);
                    if (id >= 0 && id < Blocks.BLOCK_ID_TABLE_CAPACITY) bits[id >>> 6] |= 1L << id;
                }
                for (int slot = 0; slot < overrideKeys.length; slot++) {
                    if (overrideKeys[slot] == 0) continue;
                    int id = Short.toUnsignedInt(overrideTypes[slot]);
                    if (id < Blocks.BLOCK_ID_TABLE_CAPACITY) bits[id >>> 6] |= 1L << id;
                }
                presentBlockIds = present;
            }
            return (present[blockId >>> 6] & (1L << blockId)) != 0;
        }

        public int chunkX() {
            return chunkX;
        }

        public int chunkZ() {
            return chunkZ;
        }

        public int blockTypeAt(int blockIndex) {
            int overrideSlot = overrideSlot(blockIndex);
            if (overrideSlot >= 0) return Short.toUnsignedInt(overrideTypes[overrideSlot]);
            int replayable = replayablePatch.blockTypeAt(blockIndex);
            return replayable < 0 ? generatedBlocks.get(blockIndex) : replayable;
        }

        public int generatedBlockTypeAt(int blockIndex) {
            return generatedBlocks.get(blockIndex);
        }

        /** Runtime seed scans must not treat an explicit edit as freshly generated terrain. */
        public boolean hasDynamicOverrideAt(int blockIndex) {
            return overrideSlot(blockIndex) >= 0;
        }

        /** An authored zero state is as explicit as a nonzero state. */
        public boolean hasExplicitStateAt(int blockIndex) {
            return overrideSlot(blockIndex) >= 0 || replayablePatch.exactOrdinal(blockIndex) >= 0;
        }

        public int blockStateAt(int blockIndex) {
            int overrideSlot = overrideSlot(blockIndex);
            if (overrideSlot >= 0) return Byte.toUnsignedInt(overrideStates[overrideSlot]);
            int replayableOrdinal = replayablePatch.exactOrdinal(blockIndex);
            if (replayableOrdinal >= 0) {
                return replayablePatch.blockStateAtOrdinal(replayableOrdinal);
            }
            int finalSlot = finalStateSlot(blockIndex);
            return finalSlot < 0 ? 0 : Byte.toUnsignedInt(finalStateCodes[finalSlot]);
        }

        public java.util.Optional<NeutralFinalChunk> finalLiveCarrier() {
            return java.util.Optional.ofNullable(finalLiveCarrier);
        }

        private int finalStateSlot(int blockIndex) {
            int mask = finalStateKeys.length - 1;
            int slot = blockIndex * 0x9E3779B9 & mask;
            int encoded;
            while ((encoded = finalStateKeys[slot]) != 0) {
                if (encoded == blockIndex + 1) return slot;
                slot = (slot + 1) & mask;
            }
            return -1;
        }

        private int overrideSlot(int blockIndex) {
            int mask = overrideKeys.length - 1;
            int slot = blockIndex * 0x9E3779B9 & mask;
            int encoded;
            while ((encoded = overrideKeys[slot]) != 0) {
                if (encoded == blockIndex + 1) return slot;
                slot = (slot + 1) & mask;
            }
            return -1;
        }

        public int terrainSurfaceHeightAt(int localX, int localZ) {
            return terrainSurfaceHeights[localX + localZ * Blocks.CHUNK_X];
        }

        /** Materializes one sender-owned snapshot without two boxed-map lookups for every chunk cell. */
        public void copyCellsTo(short[] types, byte[] states) {
            if (types.length != Blocks.CHUNK_BLOCKS || states.length != Blocks.CHUNK_BLOCKS) {
                throw new IllegalArgumentException("청크 snapshot 배열 크기가 올바르지 않습니다");
            }
            generatedBlocks.copyTo(0, types, 0, types.length);
            Arrays.fill(states, (byte) 0);
            applyFinalStates(states, 0, states.length);
            replayablePatch.applyTo(types, states);
            for (int slot = 0; slot < overrideKeys.length; slot++) {
                int encoded = overrideKeys[slot];
                if (encoded == 0) continue;
                int index = encoded - 1;
                types[index] = overrideTypes[slot];
                states[index] = overrideStates[slot];
            }
        }

        /**
         * Copies one 16-block-high section into full-chunk destination arrays. Light/spawner cold paths
         * use this bulk merge instead of repeating sparse hash/ordinal lookups for every cell.
         */
        public void copySectionTo(int section, short[] types, byte[] states) {
            if (types.length != Blocks.CHUNK_BLOCKS || states.length != Blocks.CHUNK_BLOCKS) {
                throw new IllegalArgumentException("청크 section snapshot 범위가 올바르지 않습니다");
            }
            copySection(section, types, states, section * SECTION_BLOCKS);
        }

        /**
         * Copies one section into section-sized arrays (index 0 is the section's lowest cell). Either
         * array may be null when the caller does not need that half.
         */
        public void copySectionInto(int section, short[] types, byte[] states) {
            if ((types != null && types.length != SECTION_BLOCKS)
                    || (states != null && states.length != SECTION_BLOCKS)) {
                throw new IllegalArgumentException("section snapshot 배열 크기가 올바르지 않습니다");
            }
            copySection(section, types, states, 0);
        }

        /**
         * The immutable generated blocks when no patch or override changes a block type in this section, else
         * null; read the section through {@link PalettedBlocks#get} with chunk block indices.
         */
        public PalettedBlocks unchangedSectionTypes(int section) {
            int start = section * SECTION_BLOCKS;
            int end = start + SECTION_BLOCKS;
            int ordinal = replayablePatch.firstOrdinalAtOrAfter(start);
            if (ordinal < replayablePatch.size() && replayablePatch.blockIndexAt(ordinal) < end) return null;
            for (int encoded : overrideKeys) {
                if (encoded != 0 && encoded - 1 >= start && encoded - 1 < end) return null;
            }
            return generatedBlocks;
        }

        /** True when every cell of the section has block state 0. */
        public boolean sectionHasNoStates(int section) {
            int start = section * SECTION_BLOCKS;
            int end = start + SECTION_BLOCKS;
            for (int slot = 0; slot < finalStateKeys.length; slot++) {
                int encoded = finalStateKeys[slot];
                if (encoded != 0 && encoded - 1 >= start && encoded - 1 < end
                        && finalStateCodes[slot] != 0) return false;
            }
            int ordinal = replayablePatch.firstOrdinalAtOrAfter(start);
            while (ordinal < replayablePatch.size()) {
                if (replayablePatch.blockIndexAt(ordinal) >= end) break;
                if (replayablePatch.blockStateAtOrdinal(ordinal) != 0) return false;
                ordinal++;
            }
            for (int slot = 0; slot < overrideKeys.length; slot++) {
                int encoded = overrideKeys[slot];
                if (encoded != 0 && encoded - 1 >= start && encoded - 1 < end
                        && overrideStates[slot] != 0) return false;
            }
            return true;
        }

        private static final int SECTION_BLOCKS = 16 * Blocks.CHUNK_X * Blocks.CHUNK_Z;

        private void copySection(int section, short[] types, byte[] states, int offset) {
            if (section < 0 || section >= Blocks.CHUNK_Y / 16) {
                throw new IllegalArgumentException("청크 section snapshot 범위가 올바르지 않습니다");
            }
            int start = section * SECTION_BLOCKS;
            int end = start + SECTION_BLOCKS;
            int shift = offset - start;
            if (types != null) generatedBlocks.copyTo(start, types, offset, SECTION_BLOCKS);
            if (states != null) {
                Arrays.fill(states, offset, offset + SECTION_BLOCKS, (byte) 0);
                for (int slot = 0; slot < finalStateKeys.length; slot++) {
                    int encoded = finalStateKeys[slot];
                    if (encoded == 0) continue;
                    int index = encoded - 1;
                    if (index >= start && index < end) states[index + shift] = finalStateCodes[slot];
                }
            }
            int ordinal = replayablePatch.firstOrdinalAtOrAfter(start);
            while (ordinal < replayablePatch.size()) {
                int index = replayablePatch.blockIndexAt(ordinal);
                if (index >= end) break;
                if (types != null) types[index + shift] = (short) replayablePatch.blockTypeAtOrdinal(ordinal);
                if (states != null) states[index + shift] = (byte) replayablePatch.blockStateAtOrdinal(ordinal);
                ordinal++;
            }
            for (int slot = 0; slot < overrideKeys.length; slot++) {
                int encoded = overrideKeys[slot];
                if (encoded == 0) continue;
                int index = encoded - 1;
                if (index < start || index >= end) continue;
                if (types != null) types[index + shift] = overrideTypes[slot];
                if (states != null) states[index + shift] = overrideStates[slot];
            }
        }

        private void applyFinalStates(byte[] states, int start, int end) {
            for (int slot = 0; slot < finalStateKeys.length; slot++) {
                int encoded = finalStateKeys[slot];
                if (encoded == 0) continue;
                int index = encoded - 1;
                if (index >= start && index < end) states[index] = finalStateCodes[slot];
            }
        }
    }

    private static final class SnapshotCell {
        private final int blockType;
        private final int blockState;

        private SnapshotCell(int blockType, int blockState) {
            this.blockType = blockType;
            this.blockState = blockState;
        }
    }

    /**
     * 512 chunks once the heap has room (900MB+). The JVM's default heap is a quarter of RAM, so a 2GB host
     * starts with ~500MB; keeping 512 chunks there spends most of it on terrain the players already left.
     * Current players' neighborhoods are never evicted, so a smaller target only shortens travel history.
     */
    private static final int GEN_CACHE_LIMIT = generatedCacheLimit(Runtime.getRuntime().maxMemory());

    static int generatedCacheLimit(long maxHeapBytes) {
        long heapMb = maxHeapBytes >> 20;
        return (int) Math.max(192, Math.min(512, heapMb - 250));
    }

    private final int seed;
    private final Long worldId;
    private final WorldBlockDiffRepository diffRepository;
    private final ChunkProductSource chunkProductSource;

    // 오버레이 조회 sentinel: 저장 값은 short 범위(부호확장)이므로 Long.MIN_VALUE 와 절대 겹치지 않는다.
    private static final long OVERLAY_ABSENT = Long.MIN_VALUE;

    // 오버레이: 이 세션에서 바뀐 블록 + 최초 접근 시 로드한 이전 diff(가장 최근 값 승리).
    // 값(short)은 long 으로 무손실 확장 저장 — 매 getBlock 의 posKey→Long 박싱/값 박싱 제거.
    private final LongOpenHashMap overlay = new LongOpenHashMap();
    // 청크 스캔 시작 시 오버레이 조회 필요 여부를 한 번만 판단하기 위한 최소 인덱스.
    private final java.util.Set<Long> overlayChunks = new java.util.HashSet<>();
    /** Enables O(cells-in-chunk) removal without scanning the primitive world-wide overlay table. */
    private final Map<Long, Set<Long>> overlayPositionsByChunk = new HashMap<>();
    /**
     * [BEACON] 오버레이에 올라온 신호기 좌표(posKey). 신호기는 자연 생성되지 않으므로 플레이어 편집
     * 오버레이(이 세션의 쓰기 + 청크 첫 접근 때 적재한 diff)가 곧 전체 목록이다. 틱 루프가 이 색인으로
     * 신호기 블록 엔티티 틱을 돌린다.
     */
    private final Set<Long> beaconPositions = new java.util.LinkedHashSet<>();
    private final java.util.Set<Long> loadedChunks = new java.util.HashSet<>();
    private final java.util.Set<Long> notifiedChunks = new java.util.HashSet<>();
    private final Map<Long, List<WorldBlockDiff>> loadedChunkDiffs = new java.util.HashMap<>();
    private PreparedChunkAccessListener chunkAccessListener;
    private ChunkEvictionListener chunkEvictionListener;
    private CapacityEvictionListener capacityEvictionListener;
    private boolean notifyingChunkAccess;
    private static final ThreadLocal<Integer> ACTIVATION_SUPPRESSION_DEPTH =
            ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Boolean> INACTIVE_CHUNK_INSPECTED =
            ThreadLocal.withInitial(() -> false);

    // 생성 청크 캐시(순수 결정론 캐시: 값은 seed·cx·cz 로만 결정되므로 축출 정책은 값에 무관).
    // 틱 스레드 전용 access-order LRU로, 상한에서는 가장 오래 안 쓴 하나만 축출해 재생성 절벽을 막는다.
    private final Map<Long, PalettedBlocks> genCache = new LinkedHashMap<>(1024, 0.75f, true);
    private final Map<Long, short[]> genSurfaceHeights = new HashMap<>(1024);
    /** Optional immutable dormant FEATURES carrier retained with the generated LRU entry. */
    private final Map<Long, NeutralFinalChunk> generatedFinalLiveCarriers =
            new HashMap<>(1024);
    /** Per-lane acknowledgements survive residency churn while the generated carrier remains cached. */
    private final Map<Long, FinalLiveCarrierLedger> finalLiveCarrierLedgers = new HashMap<>();
    private long finalLiveCarrierRevisionSequence;
    private long finalLiveCarrierDeliveryRevisionSequence;
    // 생성 pre-carver 높이는 snapshot biome 메타데이터로도 쓰이므로 편집 가능한 높이와 분리한다.
    /** 틱/청크 활성화 경로에서 키 오토박싱 없이 읽는 최종 표면 높이. */
    private final LongObjectOpenHashMap<short[]> runtimeSurfaceHeights =
            new LongObjectOpenHashMap<>(1024);
    // access-order LRU를 건드리지 않는 O(1) 상주 조회용 미러. 두 맵은 틱 스레드에서 함께 갱신합니다.
    private final LongObjectOpenHashMap<PalettedBlocks> residentChunks = new LongObjectOpenHashMap<>(1024);
    /**
     * Chunks in the current connected-player simulation union.  The world owner refreshes this before its
     * simulation phases; the terrain LRU may grow past its ordinary cache target rather than evicting one of
     * these chunks.  Its size is therefore bounded by current players' view neighborhoods, never by travel
     * history.
     */
    private Set<Long> simulationChunks = Set.of();
    // Snapshot senders receive one immutable source reference. Owner mutations accumulate in the sparse map
    // and snapshotSource publishes one frozen replacement, never exposing mutable overlay/LRU state to a worker.
    // Primitive keys: a chunk key's Long.hashCode is x ^ z, so a boxed HashMap put every diagonal of chunks in
    // one bin and walked tree nodes on the hottest per-block lookup.
    private final LongObjectOpenHashMap<SnapshotSource> snapshotSources = new LongObjectOpenHashMap<>(1024);
    /** Large deterministic geology/vegetation output, stored once per chunk instead of per-cell object graphs. */
    /** 상주 블록 조회의 매 셀 키 오토박싱을 피하는 자연 패치 인덱스. */
    private final LongObjectOpenHashMap<ReplayableChunkPatch> replayablePatches =
            new LongObjectOpenHashMap<>(1024);
    private final Map<Long, Map<Integer, SnapshotCell>> snapshotOverrides = new HashMap<>();
    // Owner mutations stay mutable and O(1). An immutable sender view is rebuilt once, only when a snapshot
    // actually captures that chunk; copying the whole sparse map for every structure voxel is quadratic.
    private final Set<Long> dirtySnapshotSources = new HashSet<>();
    private Map<Long, Map<Long, Short>> planningDiffSnapshot = Map.of();
    private Map<Long, List<WorldBlockDiff>> persistedDiffSnapshot = Map.of();
    private boolean persistedDiffSnapshotInitialized;

    // 마지막으로 접근한 청크 좌표·short[](AABB 스캔은 같은 청크를 연속 조회 → floorDiv/loadedChunks/genCache 조회 스킵).
    private int lastCx = Integer.MIN_VALUE;
    private int lastCz = Integer.MIN_VALUE;
    private PalettedBlocks lastChunk;

    // residentBlock is the dominant mob/random-tick read path. Neighbour and collision probes normally stay
    // inside one chunk for several consecutive reads, so retain the already-resolved primitive source instead
    // of boxing the same long key through loadedChunks/residentChunks/replayablePatches each time.
    private long lastResidentKey = Long.MIN_VALUE;
    private PalettedBlocks lastResidentChunk;
    private ReplayableChunkPatch lastResidentPatch;

    // 마지막 scanLoadedChunk 계측값. 틱 스레드 전용이며 핫 루프에서는 primitive 증가만 수행한다.
    private long lastScanProcessedCells;
    private long lastScanOverlayLookupsSkipped;

    /**
     * Injects the single generated-chunk seam used by planning, detached preparation, and resident cache misses.
     * Every caller must choose the source explicitly; there is no implicit legacy terrain fallback.
     */
    public TerrainAccessor(int seed, Long worldId, WorldBlockDiffRepository diffRepository,
            ChunkProductSource chunkProductSource) {
        this.seed = seed;
        this.worldId = worldId;
        this.diffRepository = diffRepository;
        this.chunkProductSource = Objects.requireNonNull(chunkProductSource, "chunk product source");
    }

    public void setChunkAccessListener(ChunkAccessListener listener) {
        this.chunkAccessListener = listener == null ? null : activation -> listener.onFirstAccess(
                activation.chunkX(), activation.chunkZ(), activation.persistedDiffs());
    }

    public void setChunkAccessListener(PreparedChunkAccessListener listener) {
        this.chunkAccessListener = listener;
    }

    public void setChunkEvictionListener(ChunkEvictionListener listener) {
        this.chunkEvictionListener = listener;
    }

    public void setCapacityEvictionListener(CapacityEvictionListener listener) {
        this.capacityEvictionListener = listener;
    }

    /**
     * 실패할 가능성이 높은 후보는 이미 활성화된 청크에서만 검사합니다. cold 청크를 만나면 검사를
     * 실패시켜 생성·diff 조회와 전체 청크 활성화를 모두 실제 플레이 접근까지 미룹니다.
     */
    public static boolean inspectWithoutChunkActivation(BooleanSupplier inspection) {
        int depth = ACTIVATION_SUPPRESSION_DEPTH.get();
        ACTIVATION_SUPPRESSION_DEPTH.set(depth + 1);
        boolean previousInactive = INACTIVE_CHUNK_INSPECTED.get();
        if (depth == 0) INACTIVE_CHUNK_INSPECTED.set(false);
        try {
            boolean result = inspection.getAsBoolean();
            return result && !INACTIVE_CHUNK_INSPECTED.get();
        } finally {
            if (depth == 0) {
                ACTIVATION_SUPPRESSION_DEPTH.remove();
                INACTIVE_CHUNK_INSPECTED.remove();
            } else {
                ACTIVATION_SUPPRESSION_DEPTH.set(depth);
                INACTIVE_CHUNK_INSPECTED.set(previousInactive || INACTIVE_CHUNK_INSPECTED.get());
            }
        }
    }

    /**
     * 몹 AI처럼 틱 안에서 넓은 범위를 읽는 작업은 이미 활성화된 청크만 관찰합니다.
     * 반환형 작업에도 같은 억제 규칙을 적용해, 블록 조회가 동기 청크 활성화와 장식 계획을
     * 뜻밖에 실행하지 않게 합니다.
     */
    public static <T> T inspectValueWithoutChunkActivation(Supplier<T> inspection) {
        int depth = ACTIVATION_SUPPRESSION_DEPTH.get();
        ACTIVATION_SUPPRESSION_DEPTH.set(depth + 1);
        boolean previousInactive = INACTIVE_CHUNK_INSPECTED.get();
        if (depth == 0) INACTIVE_CHUNK_INSPECTED.set(false);
        try {
            return inspection.get();
        } finally {
            if (depth == 0) {
                ACTIVATION_SUPPRESSION_DEPTH.remove();
                INACTIVE_CHUNK_INSPECTED.remove();
            } else {
                ACTIVATION_SUPPRESSION_DEPTH.set(depth);
                INACTIVE_CHUNK_INSPECTED.set(previousInactive || INACTIVE_CHUNK_INSPECTED.get());
            }
        }
    }

    /** 워커 계획 입력을 런타임 생성 시점의 영속 diff로 고정합니다. */
    public void setPlanningDiffSnapshot(List<WorldBlockDiff> persistedDiffs) {
        Map<Long, Map<Long, Short>> byChunk = new java.util.HashMap<>();
        Map<Long, List<WorldBlockDiff>> hydrationByChunk = new java.util.HashMap<>();
        for (WorldBlockDiff diff : persistedDiffs) {
            long chunk = chunkKey(Math.floorDiv(diff.getX(), Blocks.CHUNK_X),
                    Math.floorDiv(diff.getZ(), Blocks.CHUNK_Z));
            byChunk.computeIfAbsent(chunk, ignored -> new java.util.HashMap<>())
                    .put(posKey(diff.getX(), diff.getY(), diff.getZ()), diff.getBlockType());
            hydrationByChunk.computeIfAbsent(chunk, ignored -> new java.util.ArrayList<>()).add(diff);
        }
        Map<Long, Map<Long, Short>> frozen = new java.util.HashMap<>();
        for (Map.Entry<Long, Map<Long, Short>> entry : byChunk.entrySet()) {
            frozen.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        Map<Long, List<WorldBlockDiff>> frozenHydration = new java.util.HashMap<>();
        for (Map.Entry<Long, List<WorldBlockDiff>> entry : hydrationByChunk.entrySet()) {
            frozenHydration.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        planningDiffSnapshot = Map.copyOf(frozen);
        persistedDiffSnapshot = Map.copyOf(frozenHydration);
        persistedDiffSnapshotInitialized = true;
    }

    /** 장식 계획 워커용 비공유 결정론 블록 뷰입니다. 런타임 편집은 적용 시 다시 검사합니다. */
    public SurfaceDecorator.BlockView detachedPlanningView() {
        return new DetachedPlanningView(seed, planningDiffSnapshot, chunkProductSource);
    }

    /** Reuses the requested immutable product instead of regenerating its central planning chunk. */
    public SurfaceDecorator.BlockView detachedPlanningView(PreparedChunk prepared) {
        return new DetachedPlanningView(seed, planningDiffSnapshot, chunkProductSource, prepared);
    }

    /** 구조물 형상을 플레이어 편집과 무관하게 결정하는 생성 원본 전용 뷰입니다. */
    public SurfaceDecorator.BlockView detachedGeneratedPlanningView() {
        return new DetachedPlanningView(seed, Map.of(), chunkProductSource);
    }

    public SurfaceDecorator.BlockView detachedGeneratedPlanningView(PreparedChunk prepared) {
        return new DetachedPlanningView(seed, Map.of(), chunkProductSource, prepared);
    }

    private static final class DetachedPlanningView implements SurfaceDecorator.BlockView {
        private final int seed;
        private final ChunkProductSource chunkProductSource;
        private final java.util.concurrent.ConcurrentHashMap<Long, ChunkGenerator.GeneratedChunk> chunks =
                new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<Long, Map<Long, Short>> diffs;
        private final PreparedChunk prepared;
        /**
         * The sampler carries a mutable biome-table search hint, so planning threads must not share
         * one. A dedicated thread-local also keeps the generator's own sampler hint untouched.
         */
        private final ThreadLocal<com.gameexpert.terrain.mc.biome.McClimateSampler> biomeSampler;

        private DetachedPlanningView(int seed, Map<Long, Map<Long, Short>> diffs,
                ChunkProductSource chunkProductSource) {
            this(seed, diffs, chunkProductSource, null);
        }

        private DetachedPlanningView(int seed, Map<Long, Map<Long, Short>> diffs,
                ChunkProductSource chunkProductSource, PreparedChunk prepared) {
            this.seed = seed;
            this.diffs = diffs;
            this.chunkProductSource = chunkProductSource;
            this.prepared = prepared;
            this.biomeSampler = ThreadLocal.withInitial(
                    () -> new com.gameexpert.terrain.mc.biome.McClimateSampler(seed));
        }

        @Override
        public int noiseBiomeAt(int x, int y, int z) {
            return biomeSampler.get().biomeAtBlock(x, y, z);
        }

        @Override
        public int getBlock(int x, int y, int z) {
            if (y < Blocks.MIN_Y) return Blocks.BEDROCK;
            if (y > Blocks.MAX_Y) return Blocks.AIR;
            int cx = Math.floorDiv(x, Blocks.CHUNK_X);
            int cz = Math.floorDiv(z, Blocks.CHUNK_Z);
            long ck = chunkKey(cx, cz);
            Map<Long, Short> chunkDiffs = diffs.getOrDefault(ck, Map.of());
            Short changed = chunkDiffs.get(posKey(x, y, z));
            if (changed != null) return Short.toUnsignedInt(changed);
            if (prepared != null && prepared.chunkX() == cx && prepared.chunkZ() == cz) {
                return prepared.packedBlocks().get(Blocks.blockIndex(
                        Math.floorMod(x, Blocks.CHUNK_X), y, Math.floorMod(z, Blocks.CHUNK_Z)));
            }
            ChunkGenerator.GeneratedChunk generated = chunks.computeIfAbsent(ck,
                    ignored -> requireGeneratedChunk(
                            chunkProductSource.generate(seed, cx, cz), cx, cz));
            return Short.toUnsignedInt(generated.blocks()[Blocks.blockIndex(
                    Math.floorMod(x, Blocks.CHUNK_X), y, Math.floorMod(z, Blocks.CHUNK_Z))]);
        }

        @Override
        public boolean isProtectedEdit(int x, int y, int z) {
            long chunk = chunkKey(Math.floorDiv(x, Blocks.CHUNK_X),
                    Math.floorDiv(z, Blocks.CHUNK_Z));
            return diffs.getOrDefault(chunk, Map.of()).containsKey(posKey(x, y, z));
        }

        @Override
        public int terrainSurfaceHeight(int x, int z) {
            int chunkX = Math.floorDiv(x, Blocks.CHUNK_X);
            int chunkZ = Math.floorDiv(z, Blocks.CHUNK_Z);
            if (prepared != null && prepared.chunkX() == chunkX && prepared.chunkZ() == chunkZ) {
                return prepared.terrainSurfaceHeights()[Math.floorMod(x, Blocks.CHUNK_X)
                        + Math.floorMod(z, Blocks.CHUNK_Z) * Blocks.CHUNK_X];
            }
            long key = chunkKey(chunkX, chunkZ);
            ChunkGenerator.GeneratedChunk generated = chunks.computeIfAbsent(key,
                    ignored -> requireGeneratedChunk(
                            chunkProductSource.generate(seed, chunkX, chunkZ), chunkX, chunkZ));
            return generated.surfaceHeightAt(
                    Math.floorMod(x, Blocks.CHUNK_X),
                    Math.floorMod(z, Blocks.CHUNK_Z));
        }
    }

    /** 랜덤틱처럼 이미 활성화된 청크에서만 돌아야 하는 시스템의 가벼운 범위 검사. */
    public boolean isChunkActivated(int chunkX, int chunkZ) {
        return notifiedChunks.contains(chunkKey(chunkX, chunkZ));
    }

    /** DB·생성·알림·LRU 순서 변경 없이 상주 여부만 O(1)로 확인합니다. */
    public boolean isChunkResident(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        return loadedChunks.contains(key) && residentChunks.containsKey(key);
    }

    /**
     * 상주 청크의 생성 높이를 기준으로 편집·자연 패치를 반영한 현재 표면 높이를 반환합니다.
     * 비상주 컬럼은 틱 시스템이 하늘을 노출하거나 밀도 노이즈를 실행하지 않도록 닫힌 천장으로 취급합니다.
     */
    public int residentTerrainSurfaceHeight(int x, int z) {
        long key = chunkKey(Math.floorDiv(x, Blocks.CHUNK_X),
                Math.floorDiv(z, Blocks.CHUNK_Z));
        if (!loadedChunks.contains(key) || !residentChunks.containsKey(key)) {
            return Blocks.MAX_Y;
        }
        short[] heights = runtimeSurfaceHeights.get(key);
        if (heights == null) heights = genSurfaceHeights.get(key);
        if (heights == null) return Blocks.MAX_Y;
        return heights[Math.floorMod(x, Blocks.CHUNK_X)
                + Math.floorMod(z, Blocks.CHUNK_Z) * Blocks.CHUNK_X];
    }

    /** Drops gameplay activation and resident protection outside the same active chunk set. */
    public void evictInactiveSimulationChunks(Set<Long> activeChunkKeys) {
        evictInactiveSimulationChunks(activeChunkKeys, activeChunkKeys);
    }

    /**
     * Drops gameplay activation outside the connected-player union while retaining snapshot-only residents in
     * a small planning halo. The halo never enters {@code notifiedChunks}, so it cannot run gameplay simulation
     * or suppress a later real activation notification.
     */
    public void evictInactiveSimulationChunks(Set<Long> activeChunkKeys, Set<Long> residentRetentionKeys) {
        simulationChunks = Set.copyOf(residentRetentionKeys);
        if (notifiedChunks.isEmpty()) return;
        // Copy only the activated keys.  residentChunks is otherwise capped by the normal LRU (or the current
        // player union when that union is larger), so this cannot scale with total-ever-visited chunks.
        List<Long> stale = new java.util.ArrayList<>();
        for (long key : notifiedChunks) {
            if (!activeChunkKeys.contains(key)) stale.add(key);
        }
        for (long key : stale) {
            if (residentRetentionKeys.contains(key)) deactivateResidentChunk(key);
            else evictResidentChunk(key);
        }
    }

    /** 비틱 런타임 생명주기에서 청크를 준비하고 최초 접근 요구를 전달합니다. */
    public void prepareChunkForActivation(int chunkX, int chunkZ) {
        if (TickSafetyTelemetry.isTickThread()) {
            throw new IllegalStateException("틱 스레드에서는 청크를 동기 준비할 수 없습니다");
        }
        ensureChunkLoaded(chunkX, chunkZ);
        generatedChunkForScan(chunkX, chunkZ);
        notifyChunkAccess(chunkX, chunkZ);
    }

    /**
     * Builds one cold chunk without touching the mutable runtime cache, overlay, LRU, or access listener.
     * Snapshot preparation uses this from its own worker so activation planning cannot block terrain hydration
     * and the owner remains the sole mutator of resident state.
     */
    public PreparedChunk prepareDetachedChunkForActivation(int chunkX, int chunkZ) {
        if (TickSafetyTelemetry.isTickThread()) {
            throw new IllegalStateException("틱 스레드에서는 청크를 비동기 준비할 수 없습니다");
        }
        long hydrationStarted = System.nanoTime();
        List<WorldBlockDiff> persistedDiffs = persistedDiffsForChunk(chunkX, chunkZ);
        long hydrationNanos = System.nanoTime() - hydrationStarted;
        long generationStarted = System.nanoTime();
        ChunkGenerator.GeneratedChunk generated =
                generateChunkProduct(chunkX, chunkZ);
        long generationNanos = System.nanoTime() - generationStarted;
        return new PreparedChunk(chunkX, chunkZ, persistedDiffs,
                generated.blocks(), generated.surfaceHeights(), generated.finalLiveCarrier(),
                hydrationNanos, generationNanos);
    }

    /**
     * Current-only canonical preparation. The caller supplies one complete schema-4 product;
     * invalid or missing canonical facts fail before hydration or residency, and no legacy
     * block-array fallback is consulted.
     */
    public PreparedChunk prepareDetachedCanonicalChunkForActivation(int chunkX, int chunkZ,
            NeutralFinalChunk product) {
        java.util.Objects.requireNonNull(product, "canonical generation product");
        if (TickSafetyTelemetry.isTickThread()) {
            throw new IllegalStateException("틱 스레드에서는 청크를 동기 준비할 수 없습니다");
        }
        long generationStarted = System.nanoTime();
        ChunkGenerator.GeneratedChunk generated = ChunkGenerator.generateCanonicalChunkData(
                seed, chunkX, chunkZ, product);
        long generationNanos = System.nanoTime() - generationStarted;
        long hydrationStarted = System.nanoTime();
        List<WorldBlockDiff> persistedDiffs = persistedDiffsForChunk(chunkX, chunkZ);
        long hydrationNanos = System.nanoTime() - hydrationStarted;
        return new PreparedChunk(chunkX, chunkZ, persistedDiffs,
                generated.blocks(), generated.surfaceHeights(), generated.finalLiveCarrier(),
                hydrationNanos, generationNanos);
    }

    /**
     * World-owner handoff for gameplay preparation. This is the only adoption path allowed to publish a first
     * access callback, therefore it may enqueue fluid/decorator/structure activation work.
     */
    public void adoptPreparedChunkForActivation(PreparedChunk prepared) {
        adoptPreparedChunk(prepared);
        if (prepared != null) notifyChunkAccess(prepared.chunkX(), prepared.chunkZ());
    }

    /**
     * World-owner handoff for an activation demand which was already accepted and prepared away from the tick.
     * Unlike {@link #adoptPreparedChunkForActivation(PreparedChunk)}, this is not a block read: it publishes
     * resident material and claims the one-time gameplay activation explicitly, without invoking the access
     * listener or recording a read-activation telemetry event.  The caller must hand the returned immutable
     * claim to its detached activation planner.
     */
    public PreparedActivationClaim adoptPreparedChunkForExplicitActivation(PreparedChunk prepared) {
        if (prepared == null) return null;
        adoptPreparedChunk(prepared);
        return claimResidentChunkForExplicitActivation(prepared.chunkX(), prepared.chunkZ());
    }

    /**
     * Claims an already resident snapshot/planning source for gameplay activation without regenerating it.
     * The world owner uses this when a render-neighbour prefetch later becomes an actual simulation demand.
     */
    public PreparedActivationClaim claimResidentChunkForExplicitActivation(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        if (!residentChunks.containsKey(key)) return null;
        return claimResidentChunkForActivation(chunkX, chunkZ, key);
    }

    private PreparedActivationClaim claimResidentChunkForActivation(
            int chunkX, int chunkZ, long key) {
        if (!notifiedChunks.add(key)) return null;
        List<WorldBlockDiff> persistedDiffs = loadedChunkDiffs.remove(key);
        FinalLiveCarrierClaim claimedCarrier = finalLiveCarrierClaim(chunkX, chunkZ, key);
        return new PreparedActivationClaim(chunkX, chunkZ,
                persistedDiffs == null ? List.of() : persistedDiffs, claimedCarrier);
    }

    private FinalLiveCarrierClaim finalLiveCarrierClaim(int chunkX, int chunkZ, long key) {
        NeutralFinalChunk carrier = generatedFinalLiveCarriers.get(key);
        FinalLiveCarrierLedger ledger = finalLiveCarrierLedgers.get(key);
        if (carrier == null || ledger == null || ledger.unacknowledged.isEmpty()) return null;
        EnumSet<FinalLiveCarrierLane> delivered = EnumSet.copyOf(ledger.unacknowledged);
        NeutralFinalChunk selected = carrierWithLanes(carrier, delivered);
        ledger.activeDeliveryRevision = ++finalLiveCarrierDeliveryRevisionSequence;
        return new FinalLiveCarrierClaim(chunkX, chunkZ, ledger.revision,
                ledger.activeDeliveryRevision, delivered,
                carrierWithVisibleSidecars(chunkX, chunkZ, selected, true));
    }

    /**
     * Acknowledges exactly one semantic lane after its authority-owned durable consumer succeeds.
     * Dormant lanes are deliberately never acknowledged. Replacement-carrier and cross-chunk tokens fail closed.
     */
    public boolean acknowledgeFinalLiveCarrierLane(
            FinalLiveCarrierClaim claim, FinalLiveCarrierLane lane) {
        java.util.Objects.requireNonNull(claim, "claim");
        java.util.Objects.requireNonNull(lane, "lane");
        long key = chunkKey(claim.chunkX(), claim.chunkZ());
        FinalLiveCarrierLedger ledger = finalLiveCarrierLedgers.get(key);
        if (ledger == null || ledger.revision != claim.carrierRevision()
                || ledger.activeDeliveryRevision != claim.deliveryRevision()
                || !notifiedChunks.contains(key)
                || !claim.lanes().contains(lane)) return false;
        return ledger.unacknowledged.remove(lane);
    }

    private static NeutralFinalChunk carrierWithLanes(
            NeutralFinalChunk carrier, Set<FinalLiveCarrierLane> lanes) {
        NeutralFinalChunk.Sidecars source = carrier.sidecars();
        List<NeutralFinalChunk.Loot> loot = lanes.contains(FinalLiveCarrierLane.LOOT)
                ? source.loot() : List.of();
        List<NeutralFinalChunk.StructureEntity> entities =
                lanes.contains(FinalLiveCarrierLane.ENTITIES)
                        ? source.entities() : List.of();
        NeutralFinalChunk.Sidecars selected = new NeutralFinalChunk.Sidecars(
                lanes.contains(FinalLiveCarrierLane.BLOCK_TICKS) ? source.blockTicks() : List.of(),
                lanes.contains(FinalLiveCarrierLane.FLUID_TICKS) ? source.fluidTicks() : List.of(),
                loot,
                lanes.contains(FinalLiveCarrierLane.SPAWNERS) ? source.spawners() : List.of(),
                lanes.contains(FinalLiveCarrierLane.OWNERS) ? source.owners() : List.of(),
                lanes.contains(FinalLiveCarrierLane.ARCHAEOLOGY) ? source.archaeology() : List.of(),
                lanes.contains(FinalLiveCarrierLane.BEES) ? source.bees() : List.of(),
                lanes.contains(FinalLiveCarrierLane.BLOCK_ENTITIES)
                        ? source.blockEntities() : List.of(),
                entities,
                List.of());
        return carrier.withSidecars(selected);
    }

    private static EnumSet<FinalLiveCarrierLane> populatedCarrierLanes(
            NeutralFinalChunk.Sidecars sidecars) {
        EnumSet<FinalLiveCarrierLane> lanes = EnumSet.noneOf(FinalLiveCarrierLane.class);
        if (!sidecars.blockTicks().isEmpty()) lanes.add(FinalLiveCarrierLane.BLOCK_TICKS);
        if (!sidecars.fluidTicks().isEmpty()) lanes.add(FinalLiveCarrierLane.FLUID_TICKS);
        if (!sidecars.loot().isEmpty()) lanes.add(FinalLiveCarrierLane.LOOT);
        if (!sidecars.spawners().isEmpty()) lanes.add(FinalLiveCarrierLane.SPAWNERS);
        if (!sidecars.owners().isEmpty()) lanes.add(FinalLiveCarrierLane.OWNERS);
        if (!sidecars.archaeology().isEmpty()) lanes.add(FinalLiveCarrierLane.ARCHAEOLOGY);
        if (!sidecars.bees().isEmpty()) lanes.add(FinalLiveCarrierLane.BEES);
        if (!sidecars.blockEntities().isEmpty()) lanes.add(FinalLiveCarrierLane.BLOCK_ENTITIES);
        if (!sidecars.entities().isEmpty()) lanes.add(FinalLiveCarrierLane.ENTITIES);
        return lanes;
    }

    /**
     * Revalidates a residency-bound carrier delivery after replayable and runtime structure overlays have
     * reached their final activation boundary. The carrier's sparse states remain available below
     * those overlays; only block-owned sidecars are removed when a later owner took the cell.
     */
    public NeutralFinalChunk carrierWithVisibleSidecars(
            int chunkX, int chunkZ,
            NeutralFinalChunk carrier) {
        return carrierWithVisibleSidecars(chunkX, chunkZ, carrier, false);
    }

    private NeutralFinalChunk carrierWithVisibleSidecars(
            int chunkX, int chunkZ,
            NeutralFinalChunk carrier, boolean preserveCanonicalTicks) {
        java.util.Objects.requireNonNull(carrier, "carrier");
        if (carrier.chunkX() != chunkX || carrier.chunkZ() != chunkZ) {
            throw new IllegalArgumentException("final-live carrier coordinates do not match claim");
        }
        long key = chunkKey(chunkX, chunkZ);
        NeutralFinalChunk.Sidecars source = carrier.sidecars();
        List<NeutralFinalChunk.Loot> loot = source.loot().stream()
                .filter(value -> finalCarrierCellVisible(key, value.packed())).toList();
        List<NeutralFinalChunk.StructureEntity> entities = source.entities().stream()
                .filter(value -> finalCarrierCellVisible(key,
                        packedCarrierEntityCell(chunkX, chunkZ, value)))
                .toList();
        NeutralFinalChunk.Sidecars visible = new NeutralFinalChunk.Sidecars(
                // Tick admission authenticates the complete canonical lane. Persisted edits can
                // predate this claim; live-type checks discard obsolete ticks when they become due.
                preserveCanonicalTicks ? source.blockTicks() : source.blockTicks().stream()
                        .filter(value -> finalCarrierCellVisible(key, value.packed())).toList(),
                preserveCanonicalTicks ? source.fluidTicks() : source.fluidTicks().stream()
                        .filter(value -> finalCarrierCellVisible(key, value.packed())).toList(),
                loot,
                source.spawners().stream()
                        .filter(value -> finalCarrierCellVisible(key, value.packed())).toList(),
                source.owners().stream()
                        .filter(value -> finalCarrierCellVisible(key, value.packed())).toList(),
                source.archaeology().stream()
                        .filter(value -> finalCarrierCellVisible(key, value.packed())).toList(),
                source.bees().stream()
                        .filter(value -> finalCarrierCellVisible(key, value.packed())).toList(),
                source.blockEntities().stream()
                        .filter(value -> finalCarrierCellVisible(key, value.packed())).toList(),
                entities,
                List.of());
        if (visible.equals(source)) return carrier;
        return carrier.withSidecars(visible);
    }

    private static int packedCarrierEntityCell(int chunkX, int chunkZ,
            NeutralFinalChunk.StructureEntity entity) {
        int localX = Math.floorMod((int) Math.floor(entity.x()), Blocks.CHUNK_X);
        int localZ = Math.floorMod((int) Math.floor(entity.z()), Blocks.CHUNK_Z);
        int localY = (int) Math.floor(entity.y()) - Blocks.MIN_Y;
        if (Math.floorDiv((int) Math.floor(entity.x()), Blocks.CHUNK_X) != chunkX
                || Math.floorDiv((int) Math.floor(entity.z()), Blocks.CHUNK_Z) != chunkZ
                || localY < 0 || localY >= Blocks.CHUNK_Y) {
            throw new IllegalArgumentException("final-live entity is outside carrier chunk");
        }
        return localX + Blocks.CHUNK_X * (localZ + Blocks.CHUNK_Z * localY);
    }

    /** A later deterministic or persisted/player cell write invalidates carrier-owned payloads. */
    private boolean finalCarrierCellVisible(long key, int blockIndex) {
        ReplayableChunkPatch patch = replayablePatches.get(key);
        if (patch != null && patch.exactOrdinal(blockIndex) >= 0) return false;
        return !snapshotOverrides.getOrDefault(key, Map.of()).containsKey(blockIndex);
    }

    /**
     * World-owner handoff for a full render snapshot. It publishes only immutable resident material; notably it
     * never invokes {@link #notifyChunkAccess(int, int)} and cannot create gameplay activation demand.
     */
    public void adoptPreparedChunkForSnapshot(PreparedChunk prepared) {
        adoptPreparedChunk(prepared);
    }

    /**
     * Owner-captured distant presentation. The immutable prepared product and deterministic patch
     * stay outside the resident LRU and never claim a gameplay activation or final-carrier lane.
     * Current session edits override the persisted baseline even if preparation started earlier.
     */
    public SnapshotSource snapshotSourceForPrepared(PreparedChunk prepared,
            ReplayableChunkPatch presentationPatch) {
        java.util.Objects.requireNonNull(prepared, "prepared snapshot");
        java.util.Objects.requireNonNull(presentationPatch, "presentation patch");
        int chunkX = prepared.chunkX();
        int chunkZ = prepared.chunkZ();
        long key = chunkKey(chunkX, chunkZ);
        Map<Integer, SnapshotCell> overrides = new HashMap<>();
        for (WorldBlockDiff diff : prepared.persistedDiffs()) {
            int index = Blocks.blockIndex(Math.floorMod(diff.getX(), Blocks.CHUNK_X),
                    diff.getY(), Math.floorMod(diff.getZ(), Blocks.CHUNK_Z));
            overrides.put(index, new SnapshotCell(Short.toUnsignedInt(diff.getBlockType()),
                    Short.toUnsignedInt(diff.getBlockState())));
        }
        overrides.putAll(snapshotOverrides.getOrDefault(key, Map.of()));
        return new SnapshotSource(chunkX, chunkZ, prepared.packedBlocks(), presentationPatch,
                overrides, prepared.terrainSurfaceHeights(), prepared.finalLiveCarrier());
    }

    private void adoptPreparedChunk(PreparedChunk prepared) {
        if (prepared == null) return;
        int chunkX = prepared.chunkX();
        int chunkZ = prepared.chunkZ();
        long key = chunkKey(chunkX, chunkZ);
        if (!loadedChunks.contains(key)) {
            loadedChunks.add(key);
            List<WorldBlockDiff> persistedDiffs = prepared.persistedDiffs();
            loadedChunkDiffs.put(key, persistedDiffs);
            installPersistedDiffs(key, persistedDiffs);
        }
        if (!residentChunks.containsKey(key)) {
            putGeneratedChunk(key, prepared.packedBlocks(), prepared.terrainSurfaceHeights(),
                    prepared.finalLiveCarrier());
        }
    }

    /** Publishes pending sparse changes once and returns an immutable sender view without cache/LRU access. */
    public SnapshotSource snapshotSource(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        SnapshotSource source = snapshotSources.get(key);
        if (source == null || !dirtySnapshotSources.remove(key)) return source;
        Map<Integer, SnapshotCell> frozen = Map.copyOf(
                snapshotOverrides.getOrDefault(key, Map.of()));
        SnapshotSource published = new SnapshotSource(chunkX, chunkZ, source.generatedBlocks,
                replayablePatches.getOrDefault(key, ReplayableChunkPatch.EMPTY),
                frozen, source.terrainSurfaceHeights, source.finalLiveCarrier);
        snapshotSources.put(key, published);
        return published;
    }

    /**
     * Installs the deterministic natural layer in one owner operation. Dynamic/persisted overrides remain above
     * it, and the immutable sender source is republished once rather than once per natural cell.
     */
    public void installReplayablePatch(int chunkX, int chunkZ, ReplayableChunkPatch patch) {
        if (patch == null) throw new IllegalArgumentException("자연 패치가 없습니다");
        long key = chunkKey(chunkX, chunkZ);
        PalettedBlocks resident = residentChunks.get(key);
        if (resident == null) {
            throw new IllegalStateException("상주하지 않은 청크에 자연 패치를 설치할 수 없습니다: "
                    + chunkX + "," + chunkZ);
        }
        if (patch.isEmpty()) replayablePatches.remove(key);
        else replayablePatches.put(key, patch);
        invalidateResidentLookupCache(key);
        rebuildRuntimeSurfaceHeights(key);
        snapshotSources.put(key, new SnapshotSource(chunkX, chunkZ, resident, patch,
                Map.copyOf(snapshotOverrides.getOrDefault(key, Map.of())),
                genSurfaceHeights.get(key), generatedFinalLiveCarriers.get(key)));
        dirtySnapshotSources.remove(key);
    }

    /**
     * 틱 시스템용 부작용 없는 O(1) 블록 조회입니다. 미스는 명시적 부재로 반환하며 DB 조회,
     * 지형 생성, 최초 접근 알림 또는 워밍 결과 승격을 실행하지 않습니다.
     */
    public ResidentBlock residentBlock(int x, int y, int z) {
        if (y < Blocks.MIN_Y) return ResidentBlock.available(Blocks.BEDROCK);
        if (y > Blocks.MAX_Y) return ResidentBlock.available(Blocks.AIR);
        int cx = Math.floorDiv(x, Blocks.CHUNK_X);
        int cz = Math.floorDiv(z, Blocks.CHUNK_Z);
        long key = chunkKey(cx, cz);
        PalettedBlocks generated;
        ReplayableChunkPatch patch;
        if (key == lastResidentKey) {
            generated = lastResidentChunk;
            patch = lastResidentPatch;
        } else {
            generated = residentChunks.get(key);
            patch = generated == null ? null : replayablePatches.get(key);
            lastResidentKey = key;
            lastResidentChunk = generated;
            lastResidentPatch = patch;
        }
        if (generated == null) return ResidentBlock.unavailable();
        long overlaid = overlay.get(posKey(x, y, z), OVERLAY_ABSENT);
        int index = Blocks.blockIndex(Math.floorMod(x, Blocks.CHUNK_X), y,
                Math.floorMod(z, Blocks.CHUNK_Z));
        int block;
        if (overlaid != OVERLAY_ABSENT) {
            block = (int) overlaid & 0xFFFF;
        } else {
            int replayable = patch == null ? -1 : patch.blockTypeAt(index);
            block = replayable < 0 ? generated.get(index) : replayable;
        }
        return ResidentBlock.available(block);
    }

    /** 현재 블록 ID(동적/DB diff → 자연 패치 → 생성). y 범위 밖은 아래=기반암, 위=공기로 간주. */
    public int getBlock(int x, int y, int z) {
        if (y < Blocks.MIN_Y) {
            return Blocks.BEDROCK;
        }
        if (y > Blocks.MAX_Y) {
            return Blocks.AIR;
        }
        int cx = Math.floorDiv(x, 16);
        int cz = Math.floorDiv(z, 16);
        if (ACTIVATION_SUPPRESSION_DEPTH.get() > 0 && !isChunkActivated(cx, cz)) {
            INACTIVE_CHUNK_INSPECTED.set(true);
            return Blocks.AIR;
        }
        // 같은 청크 연속 접근이면 로드 보장·short[] 캐시가 이미 유효 → 재확인 스킵.
        if (cx != lastCx || cz != lastCz) {
            ensureChunkLoaded(cx, cz);
            lastCx = cx;
            lastCz = cz;
            lastChunk = null; // 청크 전환: short[] 캐시 무효화(생성 조회 시 generatedBlock 이 재적재)
        }
        long overlaid = overlay.get(posKey(x, y, z), OVERLAY_ABSENT);
        int block;
        if (overlaid != OVERLAY_ABSENT) {
            block = (int) overlaid & 0xFFFF;
        } else {
            int index = Blocks.blockIndex(Math.floorMod(x, Blocks.CHUNK_X), y,
                    Math.floorMod(z, Blocks.CHUNK_Z));
            ReplayableChunkPatch patch = replayablePatches.get(chunkKey(cx, cz));
            int replayable = patch == null ? -1 : patch.blockTypeAt(index);
            block = replayable < 0 ? generatedBlock(cx, cz, x, y, z) : replayable;
        }
        notifyChunkAccess(cx, cz);
        return block;
    }

    /** 런타임 오버레이에 현재 블록 값을 기록(브로드캐스트/영속 버퍼는 호출부가 별도로 처리). */
    public void setOverlay(int x, int y, int z, int blockType) {
        long positionKey = posKey(x, y, z);
        overlay.put(positionKey, (short) blockType);
        if (blockType == Blocks.BEACON) beaconPositions.add(positionKey);
        else beaconPositions.remove(positionKey);
        int chunkX = Math.floorDiv(x, Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv(z, Blocks.CHUNK_Z);
        long chunkKey = chunkKey(chunkX, chunkZ);
        overlayChunks.add(chunkKey);
        overlayPositionsByChunk.computeIfAbsent(chunkKey, ignored -> new HashSet<>()).add(positionKey);
        updateSnapshotCell(chunkX, chunkZ, Blocks.blockIndex(Math.floorMod(x, Blocks.CHUNK_X), y,
                Math.floorMod(z, Blocks.CHUNK_Z)), blockType, 0);
    }

    /**
     * Discards replayable runtime decoration for one inactive, unedited chunk. The owner must never call this
     * for a chunk with an unflushed or persisted player edit.
     */
    public ReplayableChunkPatch discardChunkOverlay(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        ReplayableChunkPatch removedPatch = replayablePatches.remove(key);
        invalidateResidentLookupCache(key);
        Set<Long> positions = overlayPositionsByChunk.remove(key);
        if (positions != null) {
            for (long position : positions) {
                overlay.remove(position, OVERLAY_ABSENT);
                beaconPositions.remove(position);
            }
        }
        overlayChunks.remove(key);
        snapshotOverrides.remove(key);
        dirtySnapshotSources.remove(key);
        rebuildRuntimeSurfaceHeights(key);
        PalettedBlocks resident = residentChunks.get(key);
        if (resident == null) {
            snapshotSources.remove(key);
        } else {
            snapshotSources.put(key, new SnapshotSource(chunkX, chunkZ, resident,
                    ReplayableChunkPatch.EMPTY, Map.of(), genSurfaceHeights.get(key),
                    generatedFinalLiveCarriers.get(key)));
        }
        return removedPatch == null ? ReplayableChunkPatch.EMPTY : removedPatch;
    }

    /** Publishes a block-state change into the immutable sender view after the owner updates BlockStateStorage. */
    public void setSnapshotBlockState(int x, int y, int z, int blockType, int blockState) {
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return;
        int chunkX = Math.floorDiv(x, Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv(z, Blocks.CHUNK_Z);
        updateSnapshotCell(chunkX, chunkZ, Blocks.blockIndex(Math.floorMod(x, Blocks.CHUNK_X), y,
                Math.floorMod(z, Blocks.CHUNK_Z)), blockType, blockState);
    }

    /**
     * 유체 청크 스캔의 경계 판정용 비알림 조회. 필요한 생성/diff는 읽지만 최초 접근 콜백은 실행하지 않아
     * 경계 이웃을 검사하는 동안 재진입 스캔이 연쇄되는 것을 막습니다. 이후 정상 getBlock 접근은 알림을 받습니다.
     */
    public int peekBlockWithoutNotification(int x, int y, int z) {
        if (y < Blocks.MIN_Y) {
            return Blocks.BEDROCK;
        }
        if (y > Blocks.MAX_Y) {
            return Blocks.AIR;
        }
        int cx = Math.floorDiv(x, Blocks.CHUNK_X);
        int cz = Math.floorDiv(z, Blocks.CHUNK_Z);
        ensureChunkLoaded(cx, cz);
        long overlaid = overlay.get(posKey(x, y, z), OVERLAY_ABSENT);
        if (overlaid != OVERLAY_ABSENT) {
            return (int) overlaid & 0xFFFF;
        }
        PalettedBlocks chunk = generatedChunk(cx, cz);
        int index = Blocks.blockIndex(Math.floorMod(x, Blocks.CHUNK_X), y,
                Math.floorMod(z, Blocks.CHUNK_Z));
        ReplayableChunkPatch patch = replayablePatches.get(chunkKey(cx, cz));
        int replayable = patch == null ? -1 : patch.blockTypeAt(index);
        return replayable < 0 ? chunk.get(index) : replayable;
    }

    /**
     * 이미 접근된 한 청크를 오버레이 포함 상태로 순회합니다. 이 메서드는 getBlock을 호출하지 않고
     * 해당 청크의 로컬 배열만 읽으므로 청크 경계에서 이웃 청크 로드/알림을 재진입시키지 않습니다.
     */
    public void scanLoadedChunk(int cx, int cz, ChunkBlockVisitor visitor) {
        scanLoadedChunk(cx, cz, Blocks.MIN_Y, Blocks.MAX_Y, visitor);
    }

    /**
     * 이미 접근된 한 청크에서 지정한 Y 범위(양 끝 포함)만 순회합니다. Y가 주 인덱스인 청크
     * 배열에서 각 높이는 연속된 256바이트이므로, 불필요한 위쪽 레이어를 읽지 않습니다.
     */
    public void scanLoadedChunk(int cx, int cz, int minY, int maxY, ChunkBlockVisitor visitor) {
        TickSafetyTelemetry.record(TickSafetyTelemetry.Event.FULL_CHUNK_SCAN);
        if (minY < Blocks.MIN_Y || maxY > Blocks.MAX_Y || minY > maxY) {
            throw new IllegalArgumentException("잘못된 청크 스캔 Y 범위: " + minY + ".." + maxY);
        }
        long key = chunkKey(cx, cz);
        if (!loadedChunks.contains(key)) {
            throw new IllegalStateException("청크가 아직 로드되지 않았습니다: " + cx + "," + cz);
        }
        PalettedBlocks generated = generatedChunk(cx, cz);
        boolean hasOverlay = overlayChunks.contains(key);
        ReplayableChunkPatch patch = replayablePatches.getOrDefault(key, ReplayableChunkPatch.EMPTY);
        int patchOrdinal = patch.firstOrdinalAtOrAfter(Blocks.blockIndex(0, minY, 0));
        int nextPatchIndex = patchOrdinal < patch.size() ? patch.blockIndexAt(patchOrdinal) : -1;
        int baseX = cx * 16;
        int baseZ = cz * 16;
        long processedCells = 0;
        long overlayLookupsSkipped = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int lz = 0; lz < Blocks.CHUNK_Z; lz++) {
                for (int lx = 0; lx < Blocks.CHUNK_X; lx++) {
                    int x = baseX + lx;
                    int z = baseZ + lz;
                    long overlaid = hasOverlay
                            ? overlay.get(posKey(x, y, z), OVERLAY_ABSENT)
                            : OVERLAY_ABSENT;
                    int index = Blocks.blockIndex(lx, y, lz);
                    int replayable = nextPatchIndex == index
                            ? patch.blockTypeAtOrdinal(patchOrdinal)
                            : -1;
                    if (nextPatchIndex == index) {
                        patchOrdinal++;
                        nextPatchIndex = patchOrdinal < patch.size()
                                ? patch.blockIndexAt(patchOrdinal)
                                : -1;
                    }
                    int id = overlaid != OVERLAY_ABSENT
                            ? (int) overlaid & 0xFFFF
                            : replayable >= 0 ? replayable : generated.get(index);
                    processedCells++;
                    if (!hasOverlay) overlayLookupsSkipped++;
                    visitor.visit(x, y, z, id);
                }
            }
        }
        lastScanProcessedCells = processedCells;
        lastScanOverlayLookupsSkipped = overlayLookupsSkipped;
    }

    /**
     * 이미 상주한 한 16³ 섹션을 부작용 없이 순회합니다. 틱 시스템의 희소 인덱스가
     * {@link #residentBlock(int, int, int)} 을 4,096번 호출하면 매 셀마다 청크 HashMap 조회가
     * 반복되므로, 청크 배열과 sparse layer를 한 번만 잡아 같은 최종 블록 사실을 읽습니다.
     *
     * @return 청크가 상주해 실제로 순회했으면 {@code true}, 아직 상주하지 않았으면 {@code false}
     */
    public boolean scanResidentSection(int sectionX, int sectionY, int sectionZ,
            ChunkBlockVisitor visitor) {
        long key = chunkKey(sectionX, sectionZ);
        PalettedBlocks generated = residentChunks.get(key);
        if (generated == null) return false;
        int minY = Math.max(Blocks.MIN_Y, sectionY * 16);
        int maxY = Math.min(Blocks.MAX_Y, sectionY * 16 + 15);
        if (minY > maxY) return true;
        ReplayableChunkPatch patch = replayablePatches.getOrDefault(
                key, ReplayableChunkPatch.EMPTY);
        boolean hasOverlay = overlayChunks.contains(key);
        int baseX = sectionX * Blocks.CHUNK_X;
        int baseZ = sectionZ * Blocks.CHUNK_Z;
        // SculkVibrationSystem의 기존 x -> y -> z 열거 순서를 보존한다. 여러 감지체가
        // 한 진동을 듣는 경우에도 최적화 전후 처리/사운드 순서가 달라지지 않는다.
        for (int lx = 0; lx < Blocks.CHUNK_X; lx++) {
            int x = baseX + lx;
            for (int y = minY; y <= maxY; y++) {
                for (int lz = 0; lz < Blocks.CHUNK_Z; lz++) {
                    int z = baseZ + lz;
                    long overlaid = hasOverlay
                            ? overlay.get(posKey(x, y, z), OVERLAY_ABSENT)
                            : OVERLAY_ABSENT;
                    int index = Blocks.blockIndex(lx, y, lz);
                    int replayable = patch.blockTypeAt(index);
                    int block = overlaid != OVERLAY_ABSENT
                            ? (int) overlaid & 0xFFFF
                            : replayable >= 0 ? replayable : generated.get(index);
                    visitor.visit(x, y, z, block);
                }
            }
        }
        return true;
    }

    public long lastScanProcessedCells() {
        return lastScanProcessedCells;
    }

    public long lastScanOverlayLookupsSkipped() {
        return lastScanOverlayLookupsSkipped;
    }

    private void notifyChunkAccess(int cx, int cz) {
        PreparedChunkAccessListener listener = chunkAccessListener;
        if (listener == null || notifyingChunkAccess || ACTIVATION_SUPPRESSION_DEPTH.get() > 0) {
            return;
        }
        long key = chunkKey(cx, cz);
        // Persisted/player overlays can satisfy getBlock without materializing terrain. Such a
        // read (including welcome container snapshots) must not consume activation ownership:
        // the natural-patch installer requires the resident chunk published by a later handoff.
        if (!residentChunks.containsKey(key)) return;
        PreparedActivationClaim activation = claimResidentChunkForActivation(cx, cz, key);
        if (activation == null) return;
        TickSafetyTelemetry.record(TickSafetyTelemetry.Event.BLOCK_READ_ACTIVATION);
        notifyingChunkAccess = true;
        try {
            listener.onFirstAccess(activation);
        } finally {
            notifyingChunkAccess = false;
        }
    }

    private void ensureChunkLoaded(int cx, int cz) {
        long key = chunkKey(cx, cz);
        if (!loadedChunks.add(key)) {
            return;
        }
        try {
            List<WorldBlockDiff> persistedDiffs = persistedDiffsForChunk(cx, cz);
            loadedChunkDiffs.put(key, persistedDiffs);
            installPersistedDiffs(key, persistedDiffs);
        } catch (RuntimeException failed) {
            // A failed hydration must be retriable. Leaving only loadedChunks behind would suppress a later
            // snapshot lookup (or the standalone fixture's lazy repository retry).
            loadedChunks.remove(key);
            loadedChunkDiffs.remove(key);
            throw failed;
        }
    }

    private List<WorldBlockDiff> persistedDiffsForChunk(int chunkX, int chunkZ) {
        if (persistedDiffSnapshotInitialized) {
            return persistedDiffSnapshot.getOrDefault(chunkKey(chunkX, chunkZ), List.of());
        }
        TickSafetyTelemetry.record(TickSafetyTelemetry.Event.PERSISTENCE_READ);
        return List.copyOf(diffRepository.findByWorldIdAndChunkXAndChunkZ(
                worldId, chunkX, chunkZ));
    }

    private void installPersistedDiffs(long key, List<WorldBlockDiff> persistedDiffs) {
        for (WorldBlockDiff diff : persistedDiffs) {
            // 세션 편집이 이미 있으면 그 값을 유지(putIfAbsent 의미).
            long posKey = posKey(diff.getX(), diff.getY(), diff.getZ());
            if (overlay.get(posKey, OVERLAY_ABSENT) == OVERLAY_ABSENT) {
                overlay.put(posKey, diff.getBlockType());
                if (Short.toUnsignedInt(diff.getBlockType()) == Blocks.BEACON) beaconPositions.add(posKey);
                setSnapshotBlockState(diff.getX(), diff.getY(), diff.getZ(),
                        Short.toUnsignedInt(diff.getBlockType()), Short.toUnsignedInt(diff.getBlockState()));
            }
            overlayChunks.add(key);
            overlayPositionsByChunk.computeIfAbsent(key, ignored -> new HashSet<>()).add(posKey);
        }
    }

    int overlayCellCountForTest() {
        return overlay.size();
    }

    int replayablePatchCellCountForTest() {
        int[] count = {0};
        replayablePatches.forEachValue(patch -> count[0] += patch.size());
        return count[0];
    }

    /** Runtime registration tests include compact natural blocks without exposing patch internals. */
    public void addReplayablePatchBlockTypes(Set<Integer> target) {
        replayablePatches.forEachValue(patch -> {
            for (int ordinal = 0; ordinal < patch.size(); ordinal++) {
                target.add(patch.blockTypeAtOrdinal(ordinal));
            }
        });
    }

    private int generatedBlock(int cx, int cz, int x, int y, int z) {
        PalettedBlocks chunk = lastChunk;
        if (chunk == null) {
            chunk = generatedChunk(cx, cz);
            lastChunk = chunk; // getBlock 이 lastCx/lastCz 를 이미 이 청크로 설정함
        }
        int lx = Math.floorMod(x, 16);
        int lz = Math.floorMod(z, 16);
        return chunk.get(Blocks.blockIndex(lx, y, lz));
    }

    /**
     * 생성 지형 바이트를 캐시에서 읽습니다. 스포너처럼 생성 지형만 검사하는 틱 스레드 시스템이
     * 같은 청크를 다시 생성하지 않도록 공유하는 읽기 전용 경로이며, 반환 배열을 수정하면 안 됩니다.
     */
    public PalettedBlocks generatedChunkForScan(int cx, int cz) {
        long key = chunkKey(cx, cz);
        PalettedBlocks chunk = genCache.get(key);
        if (chunk == null) {
            TickSafetyTelemetry.record(TickSafetyTelemetry.Event.TERRAIN_GENERATION);
            ChunkGenerator.GeneratedChunk generated =
                    generateChunkProduct(cx, cz);
            chunk = PalettedBlocks.pack(generated.blocks());
            putGeneratedChunk(key, chunk, generated.surfaceHeights(),
                    generated.finalLiveCarrier());
        } else if (!residentChunks.containsKey(key)) {
            // 비활성화 축출은 생성 LRU를 유지할 수 있다. 이후 실제 재접근 시 같은 바이트를 다시
            // 상주·스냅샷 소스로 공개하지 않으면 활성화 알림만 복구되고 resident 조회는 영구 미스가 된다.
            putGeneratedChunk(key, chunk, genSurfaceHeights.get(key),
                    generatedFinalLiveCarriers.get(key));
        }
        return chunk;
    }

    /** 이미 틱 스레드 캐시에 있는 생성 지형만 O(1)로 비파괴적으로 읽습니다. */
    public PalettedBlocks cachedChunkForScan(int cx, int cz) {
        return residentChunks.get(chunkKey(cx, cz));
    }

    private void putGeneratedChunk(long key, PalettedBlocks chunk, short[] terrainSurfaceHeights,
            NeutralFinalChunk finalLiveCarrier) {
        NeutralFinalChunk previousCarrier =
                generatedFinalLiveCarriers.get(key);
        if (previousCarrier != null && finalLiveCarrier != null
                && !sameFinalLiveCarrier(previousCarrier, finalLiveCarrier)) {
            throw new IllegalStateException(
                    "resident final-live carrier cannot be replaced before cache eviction");
        }
        PalettedBlocks replacedChunk = genCache.put(key, chunk);
        boolean insertedIntoGeneratedCache = replacedChunk == null;
        genSurfaceHeights.put(key, terrainSurfaceHeights);
        if (finalLiveCarrier == null) {
            if (previousCarrier == null) finalLiveCarrierLedgers.remove(key);
        } else if (previousCarrier == null) {
            generatedFinalLiveCarriers.put(key, finalLiveCarrier);
            finalLiveCarrierLedgers.put(key, new FinalLiveCarrierLedger(
                    ++finalLiveCarrierRevisionSequence,
                    populatedCarrierLanes(finalLiveCarrier.sidecars())));
        }
        residentChunks.put(key, chunk);
        invalidateResidentLookupCache(key);
        rebuildRuntimeSurfaceHeights(key);
        snapshotSources.put(key, new SnapshotSource(chunkX(key), chunkZ(key), chunk,
                replayablePatches.getOrDefault(key, ReplayableChunkPatch.EMPTY),
                Map.copyOf(snapshotOverrides.getOrDefault(key, Map.of())),
                terrainSurfaceHeights, generatedFinalLiveCarriers.get(key)));
        dirtySnapshotSources.remove(key);
        if (genCache.size() > GEN_CACHE_LIMIT) {
            // access-order의 첫 non-simulation entry = 가장 오래 안 쓴 축출 가능 청크.  현재 플레이어
            // 주변 union 전체가 512를 넘으면 그것을 보존한다; 그 경우의 크기는 connected players에만
            // 비례하며 과거 탐험 거리에는 비례하지 않는다.
            Long eldest = null;
            for (Long candidate : genCache.keySet()) {
                if (!simulationChunks.contains(candidate)) {
                    eldest = candidate;
                    break;
                }
            }
            if (eldest != null) {
                boolean removedFromGeneratedCache = genCache.remove(eldest) != null;
                boolean removedSurfaceHeights = genSurfaceHeights.remove(eldest) != null;
                boolean hadGeneratedCarrier = generatedFinalLiveCarriers.containsKey(eldest);
                boolean hadCarrierLedger = finalLiveCarrierLedgers.containsKey(eldest);
                generatedFinalLiveCarriers.remove(eldest);
                finalLiveCarrierLedgers.remove(eldest);
                evictResidentChunk(eldest);
                boolean removalCommitted = removedFromGeneratedCache
                        && removedSurfaceHeights
                        && !genCache.containsKey(eldest)
                        && !genSurfaceHeights.containsKey(eldest)
                        && (!hadGeneratedCarrier || !generatedFinalLiveCarriers.containsKey(eldest))
                        && (!hadCarrierLedger || !finalLiveCarrierLedgers.containsKey(eldest))
                        && !residentChunks.containsKey(eldest)
                        && !runtimeSurfaceHeights.containsKey(eldest)
                        && !snapshotSources.containsKey(eldest)
                        && !loadedChunks.contains(eldest)
                        && !loadedChunkDiffs.containsKey(eldest)
                        && !notifiedChunks.contains(eldest);
                CapacityEvictionListener capacityListener = capacityEvictionListener;
                if (insertedIntoGeneratedCache && removalCommitted && capacityListener != null) {
                    capacityListener.onCapacityEvicted(chunkX(eldest), chunkZ(eldest));
                }
            }
        }
    }

    private ChunkGenerator.GeneratedChunk generateChunkProduct(int chunkX, int chunkZ) {
        return requireGeneratedChunk(chunkProductSource.generate(seed, chunkX, chunkZ), chunkX, chunkZ);
    }

    private static ChunkGenerator.GeneratedChunk requireGeneratedChunk(
            ChunkGenerator.GeneratedChunk generated, int chunkX, int chunkZ) {
        Objects.requireNonNull(generated, "chunk product source returned null");
        if (generated.blocks() == null || generated.blocks().length != Blocks.CHUNK_BLOCKS) {
            throw new IllegalStateException("chunk product block count must be " + Blocks.CHUNK_BLOCKS);
        }
        if (generated.surfaceHeights() == null
                || generated.surfaceHeights().length != Blocks.CHUNK_X * Blocks.CHUNK_Z) {
            throw new IllegalStateException("chunk product surface-height count must be "
                    + (Blocks.CHUNK_X * Blocks.CHUNK_Z));
        }
        NeutralFinalChunk carrier = generated.finalLiveCarrier();
        if (carrier != null && (carrier.chunkX() != chunkX || carrier.chunkZ() != chunkZ)) {
            throw new IllegalStateException("chunk product carrier coordinates do not match request");
        }
        return generated;
    }

    private static boolean sameFinalLiveCarrier(NeutralFinalChunk first,
            NeutralFinalChunk second) {
        return first.chunkX() == second.chunkX() && first.chunkZ() == second.chunkZ()
                && Arrays.equals(first.blockIds(), second.blockIds())
                && first.stateOverrides().equals(second.stateOverrides())
                && Arrays.equals(first.worldSurfaceWg(), second.worldSurfaceWg())
                && Arrays.equals(first.oceanFloorWg(), second.oceanFloorWg())
                && Arrays.equals(first.motionBlocking(), second.motionBlocking())
                && first.sidecars().equals(second.sidecars());
    }

    private void updateSnapshotCell(int chunkX, int chunkZ, int blockIndex, int blockType, int blockState) {
        long key = chunkKey(chunkX, chunkZ);
        Map<Integer, SnapshotCell> overrides =
                snapshotOverrides.computeIfAbsent(key, ignored -> new HashMap<>());
        SnapshotCell previous = overrides.get(blockIndex);
        if (previous != null && previous.blockType == blockType && previous.blockState == blockState) return;
        overrides.put(blockIndex, new SnapshotCell(blockType, blockState));
        int localX = blockIndex & 15;
        int localZ = (blockIndex >>> 4) & 15;
        int y = Blocks.MIN_Y + (blockIndex >>> 8);
        updateRuntimeSurfaceHeight(key, chunkX * Blocks.CHUNK_X + localX, y,
                chunkZ * Blocks.CHUNK_Z + localZ, blockType, blockState);
        dirtySnapshotSources.add(key);
    }

    /** 블록 변이가 모두 통과하는 경계에서 해당 컬럼만 증분 갱신합니다. */
    private void updateRuntimeSurfaceHeight(long key, int x, int y, int z,
            int blockType, int blockState) {
        short[] heights = runtimeSurfaceHeights.get(key);
        if (heights == null || y < Blocks.MIN_Y || y > Blocks.MAX_Y) return;
        int column = Math.floorMod(x, Blocks.CHUNK_X)
                + Math.floorMod(z, Blocks.CHUNK_Z) * Blocks.CHUNK_X;
        int current = heights[column];
        if (surfaceBlocksLight(blockType, blockState)) {
            if (y > current) heights[column] = (short) y;
            return;
        }
        if (y != current) return;
        for (int scanY = y - 1; scanY >= Blocks.MIN_Y; scanY--) {
            if (columnSurfaceCell(key, x, scanY, z)) {
                heights[column] = (short) scanY;
                return;
            }
        }
        heights[column] = (short) (Blocks.MIN_Y - 1);
    }

    /**
     * 상단 셀이 사라진 컬럼을 다시 훑을 때 한 셀이 표면인지 판정합니다. 생성 셀은 시드가 된
     * 정본 WORLD_SURFACE 하이트맵과 같은 술어(비공기)로 세고, 편집 셀만 MobLightEngine의
     * 불투명 술어로 셉니다. 두 술어를 섞지 않으면 물기둥처럼 비고체 정본 표면을 가진 컬럼이
     * 편집 제거 후 시드 높이로 복귀하지 못합니다.
     */
    private boolean columnSurfaceCell(long key, int x, int y, int z) {
        int block = surfaceBlockAt(x, y, z);
        if (generatedSurfaceCell(key, x, y, z)) return block != Blocks.AIR;
        return surfaceBlocksLight(block, surfaceStateAt(x, y, z));
    }

    /** 오버레이·스냅샷 상태·자연 패치 어느 층도 덮지 않은 순수 생성 셀인지 확인합니다. */
    private boolean generatedSurfaceCell(long key, int x, int y, int z) {
        if (overlay.get(posKey(x, y, z), OVERLAY_ABSENT) != OVERLAY_ABSENT) return false;
        int index = Blocks.blockIndex(Math.floorMod(x, Blocks.CHUNK_X), y,
                Math.floorMod(z, Blocks.CHUNK_Z));
        if (snapshotOverrides.getOrDefault(key, Map.of()).containsKey(index)) return false;
        ReplayableChunkPatch patch = replayablePatches.get(key);
        return patch == null || patch.blockTypeAt(index) < 0;
    }

    /** 자연 패치 교체·청크 재상주처럼 sparse layer 전체가 바뀌는 경계의 재구성입니다. */
    private void rebuildRuntimeSurfaceHeights(long key) {
        short[] terrain = genSurfaceHeights.get(key);
        if (terrain == null || !residentChunks.containsKey(key)) {
            runtimeSurfaceHeights.remove(key);
            return;
        }
        short[] heights = terrain.clone();
        runtimeSurfaceHeights.put(key, heights);
        int baseX = chunkX(key) * Blocks.CHUNK_X;
        int baseZ = chunkZ(key) * Blocks.CHUNK_Z;
        ReplayableChunkPatch patch = replayablePatches.getOrDefault(
                key, ReplayableChunkPatch.EMPTY);
        for (int ordinal = 0; ordinal < patch.size(); ordinal++) {
            int index = patch.blockIndexAt(ordinal);
            updateRuntimeSurfaceHeight(key, baseX + (index & 15),
                    Blocks.MIN_Y + (index >>> 8),
                    baseZ + ((index >>> 4) & 15),
                    patch.blockTypeAtOrdinal(ordinal), patch.blockStateAtOrdinal(ordinal));
        }
        for (Map.Entry<Integer, SnapshotCell> entry
                : snapshotOverrides.getOrDefault(key, Map.of()).entrySet()) {
            int index = entry.getKey();
            SnapshotCell cell = entry.getValue();
            updateRuntimeSurfaceHeight(key, baseX + (index & 15),
                    Blocks.MIN_Y + (index >>> 8),
                    baseZ + ((index >>> 4) & 15), cell.blockType, cell.blockState);
        }
    }

    private int surfaceBlockAt(int x, int y, int z) {
        if (y < Blocks.MIN_Y) return Blocks.BEDROCK;
        if (y > Blocks.MAX_Y) return Blocks.AIR;
        int chunkX = Math.floorDiv(x, Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv(z, Blocks.CHUNK_Z);
        long key = chunkKey(chunkX, chunkZ);
        PalettedBlocks generated = residentChunks.get(key);
        if (generated == null) return Blocks.AIR;
        long overlaid = overlay.get(posKey(x, y, z), OVERLAY_ABSENT);
        if (overlaid != OVERLAY_ABSENT) return (int) overlaid & 0xFFFF;
        int index = Blocks.blockIndex(Math.floorMod(x, Blocks.CHUNK_X), y,
                Math.floorMod(z, Blocks.CHUNK_Z));
        ReplayableChunkPatch patch = replayablePatches.get(key);
        int replayable = patch == null ? -1 : patch.blockTypeAt(index);
        return replayable < 0 ? generated.get(index) : replayable;
    }

    private int surfaceStateAt(int x, int y, int z) {
        int chunkX = Math.floorDiv(x, Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv(z, Blocks.CHUNK_Z);
        long key = chunkKey(chunkX, chunkZ);
        int index = Blocks.blockIndex(Math.floorMod(x, Blocks.CHUNK_X), y,
                Math.floorMod(z, Blocks.CHUNK_Z));
        SnapshotCell override = snapshotOverrides.getOrDefault(key, Map.of()).get(index);
        if (override != null) return override.blockState;
        ReplayableChunkPatch patch = replayablePatches.get(key);
        if (patch != null && patch.exactOrdinal(index) >= 0) return patch.blockStateAt(index);
        NeutralFinalChunk carrier = generatedFinalLiveCarriers.get(key);
        if (carrier == null) return 0;
        NeutralFinalChunk.StateOverride exact = carrier.stateOverrides().get(index);
        return exact == null ? 0 : exact.stateCode();
    }

    /** MobLightEngine의 불투명 셀 정의와 같은 술어를 사용해 유리·잎은 표면 높이에 포함하지 않습니다. */
    private static boolean surfaceBlocksLight(int blockType, int blockState) {
        if (!Fluids.isSolid(blockType)) return false;
        if (Blocks.isGlassBlock(blockType) || blockType == Blocks.ICE || blockType == Blocks.POWDER_SNOW
                || blockType == Blocks.FROSTED_ICE
                || Blocks.isGlassPane(blockType) || blockType == Blocks.IRON_BARS
                || Blocks.isDoor(blockType) || Blocks.isBed(blockType)
                // [CHEST-FAMILY] 상자 형상군 전체가 14/16 인셋 박스라 빛을 막지 않는다.
                || Blocks.isChestShaped(blockType)
                || blockType == Blocks.FARMLAND || Blocks.isTrapdoor(blockType)
                || blockType == Blocks.CAMPFIRE || blockType == Blocks.SNOW
                || blockType == Blocks.MOSS_CARPET || blockType == Blocks.BIG_DRIPLEAF
                || blockType == Blocks.MANGROVE_ROOTS
                || Blocks.isFence(blockType) || Blocks.isFenceGate(blockType)
                || com.gameexpert.engine.BuildingBlockRules.isWall(blockType)
                || com.gameexpert.engine.BuildingBlockRules.isStairs(blockType)
                || (blockType >= Blocks.SPAWNER_BASE && blockType <= Blocks.SPAWNER_BASE + 2)
                || BlockFamilies.isLeaves(blockType)) return false;
        // 반 블록은 double 일 때만 빛을 막는다(종별 목재 반 블록 포함).
        if (com.gameexpert.engine.BuildingBlockRules.isSlab(blockType)) {
            return blockState == com.gameexpert.engine.BuildingBlockRules.SLAB_DOUBLE;
        }
        return true;
    }

    private void evictResidentChunk(long key) {
        invalidateResidentLookupCache(key);
        residentChunks.remove(key);
        runtimeSurfaceHeights.remove(key);
        snapshotSources.remove(key);
        dirtySnapshotSources.remove(key);
        loadedChunks.remove(key);
        loadedChunkDiffs.remove(key);
        int chunkX = chunkX(key);
        int chunkZ = chunkZ(key);
        if (lastCx == chunkX && lastCz == chunkZ) {
            lastCx = Integer.MIN_VALUE;
            lastCz = Integer.MIN_VALUE;
            lastChunk = null;
        }
        deactivateResidentChunk(key);
    }

    private void invalidateResidentLookupCache(long key) {
        if (lastResidentKey != key) return;
        lastResidentKey = Long.MIN_VALUE;
        lastResidentChunk = null;
        lastResidentPatch = null;
    }

    private void deactivateResidentChunk(long key) {
        notifiedChunks.remove(key);
        FinalLiveCarrierLedger carrierLedger = finalLiveCarrierLedgers.get(key);
        if (carrierLedger != null) carrierLedger.activeDeliveryRevision = 0;
        int chunkX = chunkX(key);
        int chunkZ = chunkZ(key);
        ChunkEvictionListener listener = chunkEvictionListener;
        if (listener != null) listener.onEvicted(chunkX, chunkZ);
    }

    int residentChunkCountForTest() {
        return residentChunks.size();
    }

    int loadedChunkCountForTest() {
        return loadedChunks.size();
    }

    int activatedChunkCountForTest() {
        return notifiedChunks.size();
    }

    static int generatedCacheLimitForTest() {
        return GEN_CACHE_LIMIT;
    }

    private PalettedBlocks generatedChunk(int cx, int cz) {
        return generatedChunkForScan(cx, cz);
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private static int chunkX(long key) {
        return (int) (key >> 32);
    }

    private static int chunkZ(long key) {
        return (int) key;
    }

    // 월드 좌표를 단일 long 키로 압축(x,z: 26비트, y-MIN_Y: 9비트).
    /**
     * [BEACON] 오버레이에 있는 신호기 좌표 사본({x, y, z} 배열 목록, 삽입 순서). 틱 스레드 전용이다.
     */
    public List<int[]> beaconPositions() {
        if (beaconPositions.isEmpty()) return List.of();
        List<int[]> positions = new java.util.ArrayList<>(beaconPositions.size());
        for (long key : beaconPositions) {
            int x = (int) (key >> 35) << 6 >> 6;
            int z = (int) ((key >> 9) & 0x3FFFFFFL) << 6 >> 6;
            int y = (int) (key & 0x1FFL) + Blocks.MIN_Y;
            positions.add(new int[] { x, y, z });
        }
        return positions;
    }

    private static long posKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 35)
                | ((long) (z & 0x3FFFFFF) << 9)
                | ((y - Blocks.MIN_Y) & 0x1FFL);
    }
}
