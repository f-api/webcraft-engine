package com.gameexpert.terrain.mc;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.aquifer.McAquifer;
import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Dormant one-chunk carrier for the exact state produced at the end of 26.3 CARVERS.
 *
 * <p>The dense plane retains the append-only unsigned-16 protocol IDs. Only states which differ
 * from an ID's canonical default occupy the sparse sidecar. Post-processing marks use the exact
 * {@code ProtoChunk} section-local packing and intentionally retain duplicates and insertion
 * order within each of the 24 build-height sections.</p>
 */
public final class Mc263PostCarversAccumulator {
    private static final byte[] RECEIPT_MAGIC = "PCA263E1".getBytes(StandardCharsets.US_ASCII);
    private static final Mc263FeatureBlockState VOID_AIR =
            Mc263FeatureBlockState.fromExact("minecraft:void_air");

    private final int chunkX;
    private final int chunkZ;
    private final short[] blocks;
    private final TreeMap<Integer, Mc263FeatureBlockState> exactStates = new TreeMap<>();
    private final List<List<Integer>> postprocessBySection;

    public Mc263PostCarversAccumulator(int chunkX, int chunkZ, short[] baseBlockIds) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        Objects.requireNonNull(baseBlockIds, "baseBlockIds");
        if (baseBlockIds.length != Blocks.CHUNK_BLOCKS) {
            throw new IllegalArgumentException("post-CARVERS block count must be "
                    + Blocks.CHUNK_BLOCKS + ": " + baseBlockIds.length);
        }
        blocks = baseBlockIds.clone();
        // Fail immediately if the dense carrier contains an ID without an exact-state default.
        for (short block : blocks) {
            Mc263FeatureBlockState.defaultForId(Short.toUnsignedInt(block));
        }
        List<List<Integer>> sections = new ArrayList<>(Blocks.CHUNK_Y / 16);
        for (int section = 0; section < Blocks.CHUNK_Y / 16; section++) {
            sections.add(new ArrayList<>());
        }
        postprocessBySection = sections;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    /** ProtoChunk-style read: local X/Z are bounded while outside build height is VOID_AIR. */
    public Mc263FeatureBlockState blockState(int localX, int blockY, int localZ) {
        requireLocalColumn(localX, localZ);
        if (!insideHeight(blockY)) return VOID_AIR;
        int index = Blocks.blockIndex(localX, blockY, localZ);
        Mc263FeatureBlockState exact = exactStates.get(index);
        return exact != null ? exact
                : Mc263FeatureBlockState.defaultForId(Short.toUnsignedInt(blocks[index]));
    }

    public int blockId(int localX, int blockY, int localZ) {
        requireLocalColumn(localX, localZ);
        if (!insideHeight(blockY)) {
            throw new IndexOutOfBoundsException("block Y outside build height: " + blockY);
        }
        return Short.toUnsignedInt(blocks[Blocks.blockIndex(localX, blockY, localZ)]);
    }

    /**
     * Writes one exact state. Invalid local or vertical coordinates are ordinary bounded-write
     * rejection and leave both dense and sparse storage untouched.
     */
    public boolean setExactState(int localX, int blockY, int localZ,
            Mc263FeatureBlockState state) {
        Objects.requireNonNull(state, "state");
        if (!inside(localX, blockY, localZ)) return false;
        int index = Blocks.blockIndex(localX, blockY, localZ);
        blocks[index] = (short) state.blockId();
        Mc263FeatureBlockState defaultState =
                Mc263FeatureBlockState.defaultForId(state.blockId());
        if (state.exactState().equals(defaultState.exactState())) {
            exactStates.remove(index);
        } else {
            exactStates.put(index, state);
        }
        return true;
    }

    /**
     * Consumes the typed base-density/aquifer decision without losing fluid identity. A requested
     * update is retained only for a non-empty source fluid, matching NoiseBasedAquifer's caller.
     */
    public boolean applySubstance(int localX, int blockY, int localZ,
            McAquifer.SubstanceResult result) {
        Objects.requireNonNull(result, "result");
        Mc263FeatureBlockState state = switch (result.material()) {
            case SOLID -> Mc263FeatureBlockState.defaultForId(Blocks.STONE);
            case AIR -> Mc263FeatureBlockState.fromExact("minecraft:air");
            case WATER -> Mc263FeatureBlockState.fromExact("minecraft:water");
            case LAVA -> Mc263FeatureBlockState.fromExact("minecraft:lava");
        };
        if (!setExactState(localX, blockY, localZ, state)) return false;
        if (result.shouldScheduleFluidUpdate()
                && (result.material() == McAquifer.Material.WATER
                || result.material() == McAquifer.Material.LAVA)) {
            markPosForPostprocessing(localX, blockY, localZ);
        }
        return true;
    }

    /** Writes the distinct air state used by configured cave/canyon carving. */
    public boolean setCarvedCaveAir(int localX, int blockY, int localZ) {
        return setExactState(localX, blockY, localZ,
                Mc263FeatureBlockState.fromExact("minecraft:cave_air"));
    }

    /** Adds one duplicate-preserving ProtoChunk mark using x|(sectionY&lt;&lt;4)|(z&lt;&lt;8). */
    public boolean markPosForPostprocessing(int localX, int blockY, int localZ) {
        if (!inside(localX, blockY, localZ)) return false;
        int section = Math.floorDiv(blockY - Blocks.MIN_Y, 16);
        int packed = localX | (Math.floorMod(blockY, 16) << 4) | (localZ << 8);
        postprocessBySection.get(section).add(packed);
        return true;
    }

    public Snapshot snapshot() {
        List<ExactStateOverride> overrides = exactStates.entrySet().stream()
                .map(entry -> new ExactStateOverride(entry.getKey(), entry.getValue().blockId(),
                        entry.getValue().exactState()))
                .toList();
        List<List<Integer>> marks = postprocessBySection.stream()
                .map(List::copyOf)
                .toList();
        return new Snapshot(chunkX, chunkZ, blocks, overrides, marks);
    }

    /** Stable cross-language evidence; this is deliberately separate from the MCF263FR schema. */
    public byte[] receipt() {
        Snapshot snapshot = snapshot();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.write(RECEIPT_MAGIC);
            output.writeInt(chunkX);
            output.writeInt(chunkZ);
            output.writeInt(Blocks.MIN_Y);
            output.writeInt(Blocks.CHUNK_Y);
            output.writeInt(Blocks.CHUNK_BLOCKS);
            for (short block : blocks) output.writeShort(Short.toUnsignedInt(block));
            output.writeInt(snapshot.exactStateOverrides().size());
            for (ExactStateOverride override : snapshot.exactStateOverrides()) {
                byte[] exact = override.exactState().getBytes(StandardCharsets.US_ASCII);
                if (exact.length > 0xffff) {
                    throw new IllegalStateException("exact state is too long for receipt");
                }
                output.writeInt(override.blockIndex());
                output.writeShort(exact.length);
                output.write(exact);
            }
            output.writeByte(snapshot.postprocessMarksBySection().size());
            for (List<Integer> section : snapshot.postprocessMarksBySection()) {
                output.writeInt(section.size());
                for (int packed : section) output.writeShort(packed);
            }
            output.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory post-CARVERS receipt failed", impossible);
        }
    }

    private static boolean inside(int x, int y, int z) {
        return x >= 0 && x < Blocks.CHUNK_X && z >= 0 && z < Blocks.CHUNK_Z
                && insideHeight(y);
    }

    private static boolean insideHeight(int y) {
        return y >= Blocks.MIN_Y && y <= Blocks.MAX_Y;
    }

    private static void requireLocalColumn(int x, int z) {
        if (x < 0 || x >= Blocks.CHUNK_X || z < 0 || z >= Blocks.CHUNK_Z) {
            throw new IndexOutOfBoundsException("local column outside chunk: " + x + "," + z);
        }
    }

    public record ExactStateOverride(int blockIndex, int blockId, String exactState) {
        public ExactStateOverride {
            if (blockIndex < 0 || blockIndex >= Blocks.CHUNK_BLOCKS) {
                throw new IllegalArgumentException("override block index outside chunk: "
                        + blockIndex);
            }
            if (blockId < 0 || blockId > 0xffff) {
                throw new IllegalArgumentException("override ID outside unsigned-16: " + blockId);
            }
            Objects.requireNonNull(exactState, "exactState");
        }
    }

    /** Immutable snapshot: dense storage is cloned both on construction and on access. */
    public static final class Snapshot {
        private final int chunkX;
        private final int chunkZ;
        private final short[] blocks;
        private final List<ExactStateOverride> exactStateOverrides;
        private final List<List<Integer>> postprocessMarksBySection;

        private Snapshot(int chunkX, int chunkZ, short[] blocks,
                List<ExactStateOverride> overrides, List<List<Integer>> marks) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.blocks = blocks.clone();
            exactStateOverrides = List.copyOf(overrides);
            postprocessMarksBySection = Collections.unmodifiableList(marks.stream()
                    .map(List::copyOf).toList());
        }

        public int chunkX() { return chunkX; }
        public int chunkZ() { return chunkZ; }
        public short[] blocks() { return blocks.clone(); }
        public List<ExactStateOverride> exactStateOverrides() { return exactStateOverrides; }
        public List<List<Integer>> postprocessMarksBySection() {
            return postprocessMarksBySection;
        }
    }
}
