package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.Mc263PostCarversAccumulator;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * Dormant exact boundary from twenty-five immutable post-CARVERS snapshots to FEATURES.
 *
 * <p>The input order is part of the contract: chunk X is the outer axis and chunk Z is the
 * inner axis across target +/-2. No density, biome, light, or sidecar fact is synthesized here.
 * The caller must supply the exact three-dimensional quart biomes and any available tick/light
 * snapshots; absent light is represented explicitly and retains fresh-chunk defaults.</p>
 */
public final class Mc263PostCarversFeaturesRegionBuilder {
    private static final byte[] RECEIPT_MAGIC =
            "PCR263F1".getBytes(StandardCharsets.US_ASCII);
    /**
     * Pinned quart-biome key shape. AGENTS rule 10l: {@code String.matches} compiles its regex on
     * every call and every region input validates 64 keys per chunk across 25 chunks, so the
     * compilation is hoisted; the accepted set is unchanged.
     */
    private static final java.util.regex.Pattern BIOME_KEY_PATTERN =
            java.util.regex.Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private Mc263PostCarversFeaturesRegionBuilder() {
    }

    public static Mc263FeaturesRegion build(RegionInput input,
            HeightmapPredicates predicates) {
        Objects.requireNonNull(input, "FEATURES region input");
        Objects.requireNonNull(predicates, "heightmap predicates");
        List<Mc263FeaturesRegion.CarversChunk> chunks = new ArrayList<>(
                Mc263FeaturesRegion.INPUT_CHUNK_COUNT);
        for (ChunkInput chunk : input.chunks()) {
            chunks.add(chunk.carversChunk());
        }
        return new Mc263FeaturesRegion(input.target().chunkX(), input.target().chunkZ(), chunks,
                predicates.worldSurface(), predicates.oceanFloor(), predicates.motionBlocking());
    }

    /** Stable big-endian Java/Rust evidence for the complete immutable builder boundary. */
    public static byte[] receipt(RegionInput input) {
        Objects.requireNonNull(input, "FEATURES region input");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.write(RECEIPT_MAGIC);
            output.writeLong(input.target().worldSeed());
            output.writeInt(input.target().chunkX());
            output.writeInt(input.target().chunkZ());
            output.writeInt(Blocks.MIN_Y);
            output.writeInt(Blocks.CHUNK_Y);
            output.writeByte(Mc263FeaturesRegion.INPUT_RADIUS);
            output.writeByte(Mc263FeaturesRegion.INPUT_CHUNK_COUNT);
            for (ChunkInput chunk : input.chunks()) output.write(chunk.receiptFragment());
            output.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory post-CARVERS region receipt failed",
                    impossible);
        }
    }

    private static Mc263FeaturesRegion.CarversChunk toCarversChunk(ChunkInput input) {
        Mc263PostCarversAccumulator.Snapshot snapshot = input.snapshot();
        short[] blocks = snapshot.blocks();
        List<Mc263FeaturesRegion.StateOverride> overrides = new ArrayList<>();
        for (Mc263PostCarversAccumulator.ExactStateOverride override
                : snapshot.exactStateOverrides()) {
            int blockIndex = override.blockIndex();
            int localY = blockIndex / (Blocks.CHUNK_X * Blocks.CHUNK_Z);
            int column = blockIndex % (Blocks.CHUNK_X * Blocks.CHUNK_Z);
            int localX = column % Blocks.CHUNK_X;
            int localZ = column / Blocks.CHUNK_X;
            Mc263FeatureBlockState state = Mc263FeatureBlockState.fromExact(
                    override.exactState());
            if (state.blockId() != override.blockId()
                    || Short.toUnsignedInt(blocks[blockIndex]) != override.blockId()) {
                throw new IllegalArgumentException("post-CARVERS override ID mismatch at index "
                        + blockIndex);
            }
            overrides.add(new Mc263FeaturesRegion.StateOverride(localX,
                    Blocks.MIN_Y + localY, localZ, state));
        }
        List<Mc263FeaturesRegion.PostprocessMark> post = new ArrayList<>();
        List<List<Integer>> sections = snapshot.postprocessMarksBySection();
        if (sections.size() != Mc263FeaturesRegion.SECTION_COUNT) {
            throw new IllegalArgumentException("post-CARVERS section count must be "
                    + Mc263FeaturesRegion.SECTION_COUNT + ": " + sections.size());
        }
        for (int section = 0; section < sections.size(); section++) {
            for (int packed : sections.get(section)) {
                if ((packed & ~0xffff) != 0 || (packed & 0xf000) != 0) {
                    throw new IllegalArgumentException(
                            "invalid ProtoChunk postprocess coordinate: " + packed);
                }
                int localX = packed & 15;
                int localY = packed >>> 4 & 15;
                int localZ = packed >>> 8 & 15;
                post.add(new Mc263FeaturesRegion.PostprocessMark(localX,
                        Blocks.MIN_Y + section * 16 + localY, localZ));
            }
        }
        return new Mc263FeaturesRegion.CarversChunk(snapshot.chunkX(), snapshot.chunkZ(), blocks,
                input.biomeKeys(), overrides, post, input.scheduledBlockTicks(),
                input.scheduledFluidTicks(), input.light().toSnapshot());
    }

    private static void encodeChunk(DataOutputStream output, ChunkInput chunk) throws IOException {
        Mc263PostCarversAccumulator.Snapshot snapshot = chunk.snapshot();
        output.writeInt(snapshot.chunkX());
        output.writeInt(snapshot.chunkZ());
        short[] blocks = snapshot.blocks();
        output.writeInt(blocks.length);
        // Identical big-endian bytes to a writeShort per block, without 98,304 stream calls per
        // input chunk (AGENTS rule 10l: the receipt is the same, only the encoding walk is cheap).
        byte[] packedBlocks = new byte[blocks.length * 2];
        for (int index = 0; index < blocks.length; index++) {
            int value = Short.toUnsignedInt(blocks[index]);
            packedBlocks[index * 2] = (byte) (value >>> 8);
            packedBlocks[index * 2 + 1] = (byte) value;
        }
        output.write(packedBlocks);
        String[] biomes = chunk.biomeKeys();
        output.writeInt(biomes.length);
        for (String biome : biomes) writeAscii(output, biome, "biome key");
        output.writeInt(snapshot.exactStateOverrides().size());
        for (Mc263PostCarversAccumulator.ExactStateOverride override
                : snapshot.exactStateOverrides()) {
            output.writeInt(override.blockIndex());
            output.writeShort(override.blockId());
            writeAscii(output, override.exactState(), "exact state");
        }
        List<List<Integer>> post = snapshot.postprocessMarksBySection();
        output.writeByte(post.size());
        for (List<Integer> section : post) {
            output.writeInt(section.size());
            for (int packed : section) output.writeShort(packed);
        }
        output.writeInt(chunk.scheduledBlockTicks().size());
        for (Mc263FeaturesRegion.ScheduledBlockTick tick : chunk.scheduledBlockTicks()) {
            output.writeByte(tick.localX());
            output.writeInt(tick.blockY());
            output.writeByte(tick.localZ());
            output.writeShort(tick.blockId());
            writeAscii(output, tick.blockKey(), "block tick key");
            output.writeInt(tick.delay());
        }
        output.writeInt(chunk.scheduledFluidTicks().size());
        for (Mc263FeaturesRegion.ScheduledFluidTick tick : chunk.scheduledFluidTicks()) {
            output.writeByte(tick.localX());
            output.writeInt(tick.blockY());
            output.writeByte(tick.localZ());
            writeAscii(output, tick.fluidKey(), "fluid tick key");
            output.writeInt(tick.delay());
        }
        chunk.light().write(output);
    }

    private static void writeAscii(DataOutputStream output, String value, String label)
            throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.US_ASCII);
        if (!value.equals(new String(encoded, StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException(label + " must be ASCII: " + value);
        }
        if (encoded.length > 0xffff) {
            throw new IllegalArgumentException(label + " exceeds unsigned-16 receipt length");
        }
        output.writeShort(encoded.length);
        output.write(encoded);
    }

    public record TargetMetadata(long worldSeed, int chunkX, int chunkZ) {
        public TargetMetadata {
            requireRepresentableTarget(chunkX, "X");
            requireRepresentableTarget(chunkZ, "Z");
        }
    }

    public record HeightmapPredicates(IntPredicate worldSurface, IntPredicate oceanFloor,
                                      IntPredicate motionBlocking) {
        public HeightmapPredicates {
            Objects.requireNonNull(worldSurface, "worldSurface");
            Objects.requireNonNull(oceanFloor, "oceanFloor");
            Objects.requireNonNull(motionBlocking, "motionBlocking");
        }
    }

    public static final class RegionInput {
        private final TargetMetadata target;
        private final List<ChunkInput> chunks;

        public RegionInput(TargetMetadata target, List<ChunkInput> chunks) {
            this.target = Objects.requireNonNull(target, "target metadata");
            Objects.requireNonNull(chunks, "post-CARVERS chunks");
            if (chunks.size() != Mc263FeaturesRegion.INPUT_CHUNK_COUNT) {
                throw new IllegalArgumentException("FEATURES requires exactly 25 post-CARVERS "
                        + "snapshots: " + chunks.size());
            }
            List<ChunkInput> copy = List.copyOf(chunks);
            int index = 0;
            for (int chunkX = target.chunkX() - Mc263FeaturesRegion.INPUT_RADIUS;
                    chunkX <= target.chunkX() + Mc263FeaturesRegion.INPUT_RADIUS; chunkX++) {
                for (int chunkZ = target.chunkZ() - Mc263FeaturesRegion.INPUT_RADIUS;
                        chunkZ <= target.chunkZ() + Mc263FeaturesRegion.INPUT_RADIUS; chunkZ++) {
                    ChunkInput chunk = Objects.requireNonNull(copy.get(index),
                            "post-CARVERS chunk");
                    if (chunk.snapshot().chunkX() != chunkX
                            || chunk.snapshot().chunkZ() != chunkZ) {
                        throw new IllegalArgumentException("post-CARVERS chunks must be X-major/"
                                + "Z-minor; index " + index + " expected " + chunkX + ","
                                + chunkZ + " but was " + chunk.snapshot().chunkX() + ","
                                + chunk.snapshot().chunkZ());
                    }
                    index++;
                }
            }
            this.chunks = copy;
        }

        public TargetMetadata target() { return target; }
        public List<ChunkInput> chunks() { return chunks; }
    }

    public static final class ChunkInput {
        private final Mc263PostCarversAccumulator.Snapshot snapshot;
        private final String[] biomeKeys;
        private final List<Mc263FeaturesRegion.ScheduledBlockTick> scheduledBlockTicks;
        private final List<Mc263FeaturesRegion.ScheduledFluidTick> scheduledFluidTicks;
        private final LightInput light;

        public ChunkInput(Mc263PostCarversAccumulator.Snapshot snapshot, String[] biomeKeys,
                List<Mc263FeaturesRegion.ScheduledBlockTick> scheduledBlockTicks,
                List<Mc263FeaturesRegion.ScheduledFluidTick> scheduledFluidTicks,
                LightInput light) {
            this.snapshot = Objects.requireNonNull(snapshot, "post-CARVERS snapshot");
            Objects.requireNonNull(biomeKeys, "quart biome keys");
            if (biomeKeys.length != Mc263FeaturesRegion.BIOME_COUNT_PER_CHUNK) {
                throw new IllegalArgumentException("quart biome count must be "
                        + Mc263FeaturesRegion.BIOME_COUNT_PER_CHUNK + ": " + biomeKeys.length);
            }
            this.biomeKeys = biomeKeys.clone();
            for (String biome : this.biomeKeys) {
                Objects.requireNonNull(biome, "quart biome key");
                if (!BIOME_KEY_PATTERN.matcher(biome).matches()) {
                    throw new IllegalArgumentException("invalid quart biome key: " + biome);
                }
            }
            this.scheduledBlockTicks = List.copyOf(Objects.requireNonNull(
                    scheduledBlockTicks, "scheduled block ticks"));
            this.scheduledFluidTicks = List.copyOf(Objects.requireNonNull(
                    scheduledFluidTicks, "scheduled fluid ticks"));
            this.light = Objects.requireNonNull(light, "post-CARVERS light");
        }

        /**
         * This chunk's slice of the region receipt, encoded once.
         *
         * <p>AGENTS rule 10l: the receipt is per FEATURES target, but its 25 input chunks are the
         * memoized {@link Mc263FeaturesRegionBridge.RegionMemo} entries that neighbouring targets
         * share, and one slice serializes 98,304 block words. A {@code ChunkInput} is immutable
         * once constructed, so its slice is a constant of the instance and re-encoding it for
         * every target that overlaps this chunk is byte-neutral work. The stream write is
         * absolute, so appending the cached slice produces the identical receipt.</p>
         */
        private volatile byte[] receiptFragment;

        /**
         * This chunk's immutable CARVERS projection, built once.
         *
         * <p>AGENTS rule 10l, same argument as {@link #receiptFragment()}: {@code ChunkInput} is
         * the memoized region entry that the 25 slots of neighbouring FEATURES targets share, and
         * {@link #toCarversChunk} is a pure function of it — it decodes the exact-state overrides,
         * unpacks the postprocess marks and clones the 98,304-word block array once per target
         * that overlaps this chunk. A {@link Mc263FeaturesRegion.CarversChunk} is immutable (its
         * constructor clones the block array, and {@code MutableChunk} copies on first write), so
         * one instance is safely shared by every region that reads this input, and the region it
         * feeds is byte-for-byte the same.</p>
         */
        private volatile Mc263FeaturesRegion.CarversChunk carversChunk;

        Mc263FeaturesRegion.CarversChunk carversChunk() {
            Mc263FeaturesRegion.CarversChunk built = carversChunk;
            if (built == null) {
                built = toCarversChunk(this);
                carversChunk = built;
            }
            return built;
        }

        byte[] receiptFragment() {
            byte[] encoded = receiptFragment;
            if (encoded == null) {
                try {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    DataOutputStream fragment = new DataOutputStream(bytes);
                    encodeChunk(fragment, this);
                    fragment.flush();
                    encoded = bytes.toByteArray();
                } catch (IOException impossible) {
                    throw new IllegalStateException(
                            "in-memory post-CARVERS chunk receipt failed", impossible);
                }
                receiptFragment = encoded;
            }
            return encoded;
        }

        public Mc263PostCarversAccumulator.Snapshot snapshot() { return snapshot; }
        public String[] biomeKeys() { return biomeKeys.clone(); }
        public List<Mc263FeaturesRegion.ScheduledBlockTick> scheduledBlockTicks() {
            return scheduledBlockTicks;
        }
        public List<Mc263FeaturesRegion.ScheduledFluidTick> scheduledFluidTicks() {
            return scheduledFluidTicks;
        }
        public LightInput light() { return light; }
    }

    /** Optional per-section SKY/BLOCK DataLayers. Null sections mean absent. */
    public static final class LightInput {
        private final byte[][] skySections;
        private final byte[][] blockSections;

        public LightInput(byte[][] skySections, byte[][] blockSections) {
            this.skySections = copyLightSections(skySections, "sky");
            this.blockSections = copyLightSections(blockSections, "block");
        }

        public static LightInput absent() {
            return new LightInput(new byte[Mc263FeaturesRegion.SECTION_COUNT][],
                    new byte[Mc263FeaturesRegion.SECTION_COUNT][]);
        }

        public static LightInput uniform(int sky, int block) {
            if (sky < 0 || sky > 15 || block < 0 || block > 15) {
                throw new IllegalArgumentException("light values must be within 0..15");
            }
            byte[][] skySections = uniformLightSections(sky);
            byte[][] blockSections = uniformLightSections(block);
            return new LightInput(skySections, blockSections);
        }

        private Mc263FeaturesRegion.LightSnapshot toSnapshot() {
            return new Mc263FeaturesRegion.LightSnapshot(skySections, blockSections);
        }

        private void write(DataOutputStream output) throws IOException {
            writeLightSections(output, skySections);
            writeLightSections(output, blockSections);
        }

        private static byte[][] copyLightSections(byte[][] input, String kind) {
            Objects.requireNonNull(input, kind + " light sections");
            if (input.length != Mc263FeaturesRegion.SECTION_COUNT) {
                throw new IllegalArgumentException(kind + " light section count must be "
                        + Mc263FeaturesRegion.SECTION_COUNT + ": " + input.length);
            }
            byte[][] copy = new byte[input.length][];
            for (int section = 0; section < input.length; section++) {
                byte[] data = input[section];
                if (data != null && data.length != Mc263FeaturesRegion.LIGHT_SECTION_BYTES) {
                    throw new IllegalArgumentException(kind + " light section byte count must be "
                            + Mc263FeaturesRegion.LIGHT_SECTION_BYTES + " at " + section + ": "
                            + data.length);
                }
                copy[section] = data == null ? null : data.clone();
            }
            return copy;
        }

        private static byte[][] uniformLightSections(int value) {
            byte[][] sections = new byte[Mc263FeaturesRegion.SECTION_COUNT][];
            byte packed = (byte) (value | value << 4);
            for (int section = 0; section < sections.length; section++) {
                sections[section] = new byte[Mc263FeaturesRegion.LIGHT_SECTION_BYTES];
                Arrays.fill(sections[section], packed);
            }
            return sections;
        }

        private static void writeLightSections(DataOutputStream output, byte[][] sections)
                throws IOException {
            output.writeByte(sections.length);
            for (byte[] data : sections) {
                output.writeBoolean(data != null);
                if (data != null) output.write(data);
            }
        }
    }

    private static void requireRepresentableTarget(int coordinate, String axis) {
        long minimumChunk = (long) coordinate - Mc263FeaturesRegion.INPUT_RADIUS;
        long maximumChunk = (long) coordinate + Mc263FeaturesRegion.INPUT_RADIUS;
        long minimumBlock = minimumChunk * Blocks.CHUNK_X;
        long maximumBlock = maximumChunk * Blocks.CHUNK_X + Blocks.CHUNK_X - 1L;
        if (minimumChunk < Integer.MIN_VALUE || maximumChunk > Integer.MAX_VALUE
                || minimumBlock < Integer.MIN_VALUE || maximumBlock > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("FEATURES target chunk " + axis
                    + " cannot be represented as block coordinates: " + coordinate);
        }
    }
}
