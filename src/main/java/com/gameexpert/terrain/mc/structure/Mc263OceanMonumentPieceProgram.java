package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.PieceFact;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.PieceKind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.RoomFact;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Rotation;
import java.util.HashMap;
import java.util.HashSet;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant, exact procedural {@code 26.3-snapshot-7} ocean-monument piece programs.
 *
 * <p>The methods below are a source-order transcription of the pinned piece programs. They do
 * not contain a template payload. The dormant start adapter stages geometry and semantic lanes
 * against a private overlay and exposes one frozen atomic settlement; this class deliberately
 * performs no canonical registration. Full-shell oracle v2 evidence is bound by
 * {@link Mc263OceanMonumentOracleV2}; registry activation remains a separate cross-language gate.</p>
 */
public final class Mc263OceanMonumentPieceProgram {
    public static final String VERSION = Mc263HardcodedStructureCarrier.VERSION;
    public static final String SERVER_SHA1 = Mc263HardcodedStructureCarrier.SERVER_SHA1;

    public static final String AIR = "minecraft:air";
    public static final String WATER = "minecraft:water";
    public static final String PRISMARINE = "minecraft:prismarine";
    public static final String PRISMARINE_BRICKS = "minecraft:prismarine_bricks";
    public static final String DARK_PRISMARINE = "minecraft:dark_prismarine";
    public static final String SEA_LANTERN = "minecraft:sea_lantern";
    public static final String WET_SPONGE = "minecraft:wet_sponge";
    public static final String GOLD_BLOCK = "minecraft:gold_block";
    public static final String ELDER_GUARDIAN = "minecraft:elder_guardian";

    private static final Set<String> STATES = Set.of(AIR, WATER, PRISMARINE,
            PRISMARINE_BRICKS, DARK_PRISMARINE, SEA_LANTERN, WET_SPONGE, GOLD_BLOCK);
    private static final Set<String> KNOWN_CAPABILITIES = Set.of(
            "block_query", "sea_level", "block_write", "replaceable_query",
            "minimum_y", "elder_guardian_emission");
    private static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;

    private Mc263OceanMonumentPieceProgram() { }

    /** Random supplied at the official per-piece postProcess boundary; it is never reseeded. */
    public interface PostProcessRandom {
        int nextInt(int bound);
        boolean nextBoolean();
    }

    /** Fork/commit boundary required so rejected start settlements consume no placement RNG. */
    public interface AtomicPostProcessRandom extends PostProcessRandom {
        AtomicPostProcessRandom fork();
        void commitFrom(AtomicPostProcessRandom completedFork);
        byte[] canonicalContinuation();
    }

    /** Query/write surface suitable for a staging, atomic settlement adapter. */
    public interface WorldAccess {
        /** Pure declarations; neither method may inspect mutable world state. */
        Set<String> capabilities();
        boolean supportsExactState(String exactState);

        int seaLevel();
        int minY();
        String blockState(BlockPos position);
        String fluidState(BlockPos position);
        boolean isReplaceableByStructures(BlockPos position, String exactState);
        boolean predictsSetBlockResult(BlockPos position, String exactState, int flags);
        boolean setBlock(BlockPos position, String exactState, int flags);
        void scheduleFluidTick(BlockPos position, String fluidKey, int delay);
        void emitStructureMob(String entityType, String spawnReason,
                double x, double y, double z, float yaw, float pitch);

        /** The dormant start aggregate requires one all-or-nothing durable boundary. */
        default boolean supportsAtomicSettlement() { return false; }

        /** Accepts the complete start once; rejection must leave every lane unchanged. */
        default void settle(AtomicSettlement settlement) {
            throw new UnsupportedOperationException("missing ocean-monument atomic settlement");
        }
    }

    public static final class BlockPos {
        private final int x, y, z;
        public BlockPos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        @Override public boolean equals(Object value) {
            return value instanceof BlockPos other && x == other.x && y == other.y && z == other.z;
        }
        @Override public int hashCode() { return Objects.hash(x, y, z); }
    }

    public record BlockWrite(BlockPos position, String exactState, int flags) {
        public BlockWrite {
            Objects.requireNonNull(position, "monument write position");
            if (!STATES.contains(exactState) || flags != 2) {
                throw new IllegalArgumentException("noncanonical monument block write");
            }
        }
    }

    /** Exact fluid-tick semantic produced by StructurePiece.placeBlock for placed water. */
    public record FluidTick(BlockPos position, String fluidKey, int delay, String priority,
                            long subTickOrder) {
        public FluidTick {
            Objects.requireNonNull(position, "monument fluid-tick position");
            if (!WATER.equals(fluidKey) || delay != 0 || !"NORMAL".equals(priority)
                    || subTickOrder < 0) {
                throw new IllegalArgumentException("noncanonical monument fluid tick");
            }
        }
    }

    public record EntityEmission(String entityType, String spawnReason,
            double x, double y, double z, float yaw, float pitch, byte[] canonicalPayload) {
        public EntityEmission {
            if (!ELDER_GUARDIAN.equals(entityType)
                    || !"minecraft:structure".equals(spawnReason)
                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Float.floatToRawIntBits(yaw) != 0 || Float.floatToRawIntBits(pitch) != 0
                    || !Arrays.equals(canonicalPayload,
                            "OME263E1".getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("noncanonical monument elder emission");
            }
            canonicalPayload = canonicalPayload.clone();
        }
        @Override public byte[] canonicalPayload() { return canonicalPayload.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof EntityEmission value
                    && entityType.equals(value.entityType)
                    && spawnReason.equals(value.spawnReason)
                    && Double.doubleToRawLongBits(x) == Double.doubleToRawLongBits(value.x)
                    && Double.doubleToRawLongBits(y) == Double.doubleToRawLongBits(value.y)
                    && Double.doubleToRawLongBits(z) == Double.doubleToRawLongBits(value.z)
                    && Float.floatToRawIntBits(yaw) == Float.floatToRawIntBits(value.yaw)
                    && Float.floatToRawIntBits(pitch) == Float.floatToRawIntBits(value.pitch)
                    && Arrays.equals(canonicalPayload, value.canonicalPayload);
        }
        @Override public int hashCode() {
            return 31 * Objects.hash(entityType, spawnReason, x, y, z, yaw, pitch)
                    + Arrays.hashCode(canonicalPayload);
        }
    }

    /** Complete source-order operation stream emitted by the actual procedural execution. */
    public static final class OperationReceipt {
        private final List<String> events;
        private final List<String> segmentSha256;
        private final String orderedSha256;
        private final String segmentDigestSha256;
        private final Map<String, Integer> methodCounts;

        private OperationReceipt(List<String> events) {
            this.events = List.copyOf(events);
            LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
            MessageDigest ordered = digest();
            ArrayList<String> segments = new ArrayList<>();
            for (int start = 0; start < events.size(); start += 64) {
                MessageDigest segment = digest();
                for (int index = start; index < Math.min(start + 64, events.size()); index++) {
                    String event = events.get(index);
                    if (!event.startsWith(index + "|")) {
                        throw new IllegalArgumentException("noncanonical monument operation ordinal");
                    }
                    int methodEnd = event.indexOf('|', event.indexOf('|') + 1);
                    if (methodEnd < 0) throw new IllegalArgumentException("malformed monument operation");
                    String method = event.substring(event.indexOf('|') + 1, methodEnd);
                    counts.merge(method, 1, Integer::sum);
                    byte[] bytes = (event + "\n").getBytes(StandardCharsets.UTF_8);
                    ordered.update(bytes); segment.update(bytes);
                }
                segments.add(java.util.HexFormat.of().formatHex(segment.digest()));
            }
            this.segmentSha256 = List.copyOf(segments);
            this.orderedSha256 = java.util.HexFormat.of().formatHex(ordered.digest());
            MessageDigest segmentDigest = digest();
            for (String value : segments) segmentDigest.update(
                    java.util.HexFormat.of().parseHex(value));
            this.segmentDigestSha256 = java.util.HexFormat.of().formatHex(segmentDigest.digest());
            this.methodCounts = Map.copyOf(counts);
        }
        public List<String> events() { return events; }
        public List<String> segmentSha256() { return segmentSha256; }
        public String orderedSha256() { return orderedSha256; }
        public String segmentDigestSha256() { return segmentDigestSha256; }
        public int operationCount() { return events.size(); }
        public Map<String, Integer> methodCounts() { return methodCounts; }
    }

    /**
     * Frozen all-lane receipt for one complete official MonumentBuilding post-process aggregate.
     * The successor STR payload is byte-identical for monuments because the official building
     * piece has no mutable placement flags; publishing it still belongs to the same transaction.
     */
    public static final class AtomicSettlement {
        private static final byte[] MAGIC = "OMS263F1".getBytes(StandardCharsets.US_ASCII);
        private final String identity;
        private final BoundingBox aggregate;
        private final List<BlockWrite> writes;
        private final List<FluidTick> fluidTicks;
        private final List<EntityEmission> entities;
        private final OperationReceipt operations;
        private final int carrierOwnedElderInstallMask;
        private final byte[] successorStr;
        private final byte[] randomContinuation;
        private final byte[] receipt;

        private AtomicSettlement(String identity, BoundingBox aggregate,
                List<BlockWrite> writes, List<FluidTick> fluidTicks,
                List<EntityEmission> entities, OperationReceipt operations, byte[] successorStr,
                byte[] randomContinuation) {
            this.identity = Objects.requireNonNull(identity, "monument settlement identity");
            this.aggregate = Objects.requireNonNull(aggregate, "monument aggregate");
            this.writes = List.copyOf(writes);
            this.fluidTicks = List.copyOf(fluidTicks);
            this.entities = List.copyOf(entities);
            this.operations = Objects.requireNonNull(operations, "monument operation receipt");
            this.successorStr = successorStr.clone();
            this.randomContinuation = Objects.requireNonNull(randomContinuation,
                    "monument random continuation").clone();
            if (this.randomContinuation.length == 0) {
                throw new IllegalArgumentException("empty monument random continuation");
            }
            if (this.entities.size() != 3) {
                throw new IllegalArgumentException("monument aggregate requires three elders");
            }
            // Official snapshot-7 wing/penthouse NBT has no Elder field. Exactly-once state is
            // therefore explicitly product-carrier-owned and must never be serialized as official
            // piece NBT. Bits follow official elder encounter order.
            this.carrierOwnedElderInstallMask = (1 << this.entities.size()) - 1;
            this.receipt = encodeReceipt();
        }

        public String identity() { return identity; }
        public BoundingBox aggregate() { return aggregate; }
        public List<BlockWrite> writes() { return writes; }
        public List<FluidTick> fluidTicks() { return fluidTicks; }
        public List<EntityEmission> entities() { return entities; }
        public OperationReceipt operations() { return operations; }
        public int carrierOwnedElderInstallMask() { return carrierOwnedElderInstallMask; }
        public byte[] successorStr() { return successorStr.clone(); }
        public byte[] randomContinuation() { return randomContinuation.clone(); }
        public byte[] frozenReceipt() { return receipt.clone(); }
        public String frozenReceiptSha256() {
            return java.util.HexFormat.of().formatHex(sha256(receipt));
        }

        /** Rejects a truncated, foreign, or tampered frozen monument receipt. */
        public static void verifyFrozenReceipt(byte[] receipt) {
            Objects.requireNonNull(receipt, "frozen monument receipt");
            if (receipt.length <= MAGIC.length + 32) {
                throw new IllegalArgumentException("truncated frozen monument receipt");
            }
            for (int index = 0; index < MAGIC.length; index++) {
                if (receipt[index] != MAGIC[index]) {
                    throw new IllegalArgumentException("frozen monument receipt magic mismatch");
                }
            }
            int bodyLength = receipt.length - 32;
            byte[] expected = sha256(Arrays.copyOf(receipt, bodyLength));
            byte[] actual = Arrays.copyOfRange(receipt, bodyLength, receipt.length);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new IllegalArgumentException("frozen monument receipt digest mismatch");
            }
        }

        private byte[] encodeReceipt() {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (DataOutputStream out = new DataOutputStream(bytes)) {
                    out.write(MAGIC); out.writeUTF(identity); writeBox(out, aggregate);
                    out.writeInt(writes.size());
                    for (BlockWrite write : writes) {
                        writePos(out, write.position()); out.writeUTF(write.exactState());
                        out.writeByte(write.flags());
                    }
                    out.writeInt(fluidTicks.size());
                    for (FluidTick tick : fluidTicks) {
                        writePos(out, tick.position()); out.writeUTF(tick.fluidKey());
                        out.writeInt(tick.delay()); out.writeUTF(tick.priority());
                        out.writeLong(tick.subTickOrder());
                    }
                    out.writeInt(entities.size());
                    for (EntityEmission entity : entities) {
                        out.writeUTF(entity.entityType()); out.writeUTF(entity.spawnReason());
                        out.writeDouble(entity.x()); out.writeDouble(entity.y());
                        out.writeDouble(entity.z()); out.writeFloat(entity.yaw());
                        out.writeFloat(entity.pitch()); byte[] payload = entity.canonicalPayload();
                        out.writeInt(payload.length); out.write(payload);
                    }
                    out.writeByte(carrierOwnedElderInstallMask);
                    out.writeInt(operations.operationCount());
                    writeAscii(out, operations.orderedSha256());
                    out.writeInt(operations.segmentSha256().size());
                    for (String segment : operations.segmentSha256()) writeAscii(out, segment);
                    writeAscii(out, operations.segmentDigestSha256());
                    out.writeInt(successorStr.length); out.write(successorStr);
                    out.writeInt(randomContinuation.length); out.write(randomContinuation);
                }
                byte[] body = bytes.toByteArray();
                ByteArrayOutputStream frozen = new ByteArrayOutputStream();
                frozen.write(body); frozen.write(sha256(body));
                return frozen.toByteArray();
            } catch (IOException impossible) {
                throw new IllegalStateException("in-memory monument receipt failed", impossible);
            }
        }
    }

    /**
     * Executes the entire persisted MonumentBuilding aggregate against a private overlay and
     * exposes it only through one atomic callback. This remains dormant until full-shell official
     * oracle evidence is accepted and a production registry explicitly wires the callback.
     */
    public static AtomicSettlement settleStart(Mc263StructureCarrier structureCarrier,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263HardcodedStructureCarrier carrier, Clip clip, WorldAccess world,
            AtomicPostProcessRandom random) {
        Objects.requireNonNull(structureCarrier, "monument STR carrier");
        Objects.requireNonNull(references, "monument references");
        Objects.requireNonNull(carrier, "monument carrier");
        Objects.requireNonNull(clip, "monument aggregate clip");
        Objects.requireNonNull(world, "monument aggregate world");
        Objects.requireNonNull(random, "monument aggregate random");
        if (carrier.kind() != Mc263HardcodedStructureCarrier.Kind.OCEAN_MONUMENT) {
            throw new IllegalArgumentException("non-monument aggregate carrier");
        }
        if (!world.supportsAtomicSettlement()) {
            throw new UnsupportedOperationException("missing ocean-monument atomic settlement");
        }
        requireCompleteClip(carrier, clip);
        byte[] successorStr = Mc263OceanMonumentSettlement.successorStr(
                structureCarrier, references, carrier).receiptBytes();
        AtomicPostProcessRandom candidate = Objects.requireNonNull(random.fork(),
                "monument random fork");
        for (PieceFact piece : carrier.orderedPieces()) {
            preflight(piece, carrier.orderedRooms(), carrier.rotation(), clip,
                    new DeclarationWorld(world), candidate);
        }
        StagingWorld staging = new StagingWorld(world);
        TracingWorld tracing = new TracingWorld(staging);
        for (PieceFact piece : carrier.orderedPieces()) {
            postProcess(piece, carrier.orderedRooms(), carrier.rotation(), clip, tracing, candidate);
        }
        AtomicSettlement settlement = staging.freeze(carrier, successorStr,
                candidate.canonicalContinuation(), tracing.freeze());
        world.settle(settlement);
        random.commitFrom(candidate);
        return settlement;
    }

    /**
     * Executes the accepted oracle-v2 boundary: sixteen 16x16 clips in chunk-Z-major,
     * chunk-X-minor order, with one continued placement RNG and one atomic publication.
     */
    public static AtomicSettlement settleFullBuildingShell(
            Mc263StructureCarrier structureCarrier,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263HardcodedStructureCarrier carrier, List<Clip> clips, WorldAccess world,
            AtomicPostProcessRandom random) {
        Objects.requireNonNull(structureCarrier, "monument STR carrier");
        Objects.requireNonNull(references, "monument references");
        Objects.requireNonNull(carrier, "monument carrier");
        Objects.requireNonNull(clips, "monument shell clips");
        Objects.requireNonNull(world, "monument aggregate world");
        Objects.requireNonNull(random, "monument aggregate random");
        if (carrier.kind() != Mc263HardcodedStructureCarrier.Kind.OCEAN_MONUMENT) {
            throw new IllegalArgumentException("non-monument aggregate carrier");
        }
        if (!world.supportsAtomicSettlement()) {
            throw new UnsupportedOperationException("missing ocean-monument atomic settlement");
        }
        requireOracleClipOrder(clips);
        byte[] successorStr = Mc263OceanMonumentSettlement.successorStr(
                structureCarrier, references, carrier).receiptBytes();
        AtomicPostProcessRandom candidate = Objects.requireNonNull(random.fork(),
                "monument random fork");
        for (Clip clip : clips) for (PieceFact piece : carrier.orderedPieces()) {
            preflight(piece, carrier.orderedRooms(), carrier.rotation(), clip,
                    new DeclarationWorld(world), candidate);
        }
        StagingWorld staging = new StagingWorld(world);
        TracingWorld tracing = new TracingWorld(staging);
        for (Clip clip : clips) for (PieceFact piece : carrier.orderedPieces()) {
            if (piece.kind() != PieceKind.MONUMENT_BUILDING
                    && !pieceIntersects(piece, clip)) continue;
            postProcess(piece, carrier.orderedRooms(), carrier.rotation(), clip, tracing, candidate);
        }
        AtomicSettlement settlement = staging.freeze(carrier, successorStr,
                candidate.canonicalContinuation(), tracing.freeze());
        world.settle(settlement);
        random.commitFrom(candidate);
        return settlement;
    }

    private static void requireOracleClipOrder(List<Clip> clips) {
        if (clips.size() != 16) {
            throw new IllegalArgumentException("ocean-monument v2 shell requires 16 clips");
        }
        int previousChunkX = Integer.MIN_VALUE;
        int previousChunkZ = Integer.MIN_VALUE;
        HashSet<Long> occupied = new HashSet<>();
        for (int index = 0; index < clips.size(); index++) {
            Clip clip = Objects.requireNonNull(clips.get(index), "monument shell clip");
            if (!clip.isWholeChunk()) {
                throw new IllegalArgumentException("noncanonical ocean-monument chunk clip");
            }
            int chunkX = Math.floorDiv(clip.minX, 16);
            int chunkZ = Math.floorDiv(clip.minZ, 16);
            long key = (chunkX & 0xffff_ffffL) | ((long) chunkZ << 32);
            if (!occupied.add(key)) {
                throw new IllegalArgumentException("duplicate ocean-monument chunk clip");
            }
            if (index > 0 && (chunkZ < previousChunkZ
                    || (chunkZ == previousChunkZ && chunkX <= previousChunkX))) {
                throw new IllegalArgumentException("noncanonical ocean-monument clip order");
            }
            previousChunkX = chunkX;
            previousChunkZ = chunkZ;
        }
    }

    private static boolean pieceIntersects(PieceFact piece, Clip clip) {
        BoundingBox box = piece.boundingBox();
        return clip.intersects(box.minX(), box.minZ(), box.maxX(), box.maxZ());
    }

    private static void requireCompleteClip(Mc263HardcodedStructureCarrier carrier, Clip clip) {
        BoundingBox box = carrier.boundingBox();
        int minX = Math.subtractExact(box.minX(), 5);
        int minZ = Math.subtractExact(box.minZ(), 5);
        int maxX = Math.addExact(box.maxX(), 5);
        int maxZ = Math.addExact(box.maxZ(), 5);
        if (!clip.contains(new BlockPos(minX, box.minY(), minZ))
                || !clip.contains(new BlockPos(maxX, box.maxY(), maxZ))) {
            throw new IllegalArgumentException("incomplete ocean-monument aggregate clip");
        }
    }

    private static final class DeclarationWorld implements WorldAccess {
        private final WorldAccess delegate;
        private DeclarationWorld(WorldAccess delegate) { this.delegate = delegate; }
        @Override public Set<String> capabilities() { return delegate.capabilities(); }
        @Override public boolean supportsExactState(String state) {
            return delegate.supportsExactState(state);
        }
        @Override public int seaLevel() { throw new AssertionError("declaration queried sea level"); }
        @Override public int minY() { throw new AssertionError("declaration queried min Y"); }
        @Override public String blockState(BlockPos p) { throw new AssertionError("declaration queried block"); }
        @Override public String fluidState(BlockPos p) { throw new AssertionError("declaration queried fluid"); }
        @Override public boolean isReplaceableByStructures(BlockPos p, String s) {
            throw new AssertionError("declaration queried replaceability");
        }
        @Override public boolean predictsSetBlockResult(BlockPos p, String s, int f) {
            throw new AssertionError("declaration predicted block write");
        }
        @Override public boolean setBlock(BlockPos p, String s, int f) {
            throw new AssertionError("declaration wrote block");
        }
        @Override public void scheduleFluidTick(BlockPos p, String f, int d) {
            throw new AssertionError("declaration scheduled fluid");
        }
        @Override public void emitStructureMob(String t, String r, double x, double y,
                double z, float yaw, float pitch) { throw new AssertionError("declaration emitted entity"); }
    }

    private static final class StagingWorld implements WorldAccess {
        private static final byte[] ELDER_PAYLOAD =
                "OME263E1".getBytes(StandardCharsets.US_ASCII);
        private final WorldAccess delegate;
        private final Map<BlockPos, String> overlay = new HashMap<>();
        private final ArrayList<BlockWrite> writes = new ArrayList<>();
        private final LinkedHashMap<BlockPos, FluidTick> fluidTicks = new LinkedHashMap<>();
        private final ArrayList<EntityEmission> entities = new ArrayList<>();
        private StagingWorld(WorldAccess delegate) { this.delegate = delegate; }
        @Override public Set<String> capabilities() { return delegate.capabilities(); }
        @Override public boolean supportsExactState(String state) { return delegate.supportsExactState(state); }
        @Override public int seaLevel() { return delegate.seaLevel(); }
        @Override public int minY() { return delegate.minY(); }
        @Override public String blockState(BlockPos p) {
            return overlay.containsKey(p) ? overlay.get(p) : delegate.blockState(p);
        }
        @Override public String fluidState(BlockPos p) {
            if (overlay.containsKey(p)) return fluidForState(overlay.get(p));
            return delegate.fluidState(p);
        }
        @Override public boolean isReplaceableByStructures(BlockPos p, String state) {
            if (overlay.containsKey(p)) return AIR.equals(state) || WATER.equals(state);
            return delegate.isReplaceableByStructures(p, state);
        }
        @Override public boolean predictsSetBlockResult(BlockPos p, String state, int flags) {
            return delegate.predictsSetBlockResult(p, state, flags);
        }
        @Override public boolean setBlock(BlockPos p, String state, int flags) {
            boolean accepted = !state.equals(overlay.get(p))
                    && delegate.predictsSetBlockResult(p, state, flags);
            if (!accepted) return false;
            BlockWrite write = new BlockWrite(p, state, flags);
            writes.add(write); overlay.put(p, state);
            return true;
        }
        @Override public void scheduleFluidTick(BlockPos p, String fluidKey, int delay) {
            if (!WATER.equals(fluidKey) || delay != 0) {
                throw new IllegalArgumentException("noncanonical monument fluid schedule");
            }
            fluidTicks.putIfAbsent(p, new FluidTick(p, WATER, 0, "NORMAL", fluidTicks.size()));
        }
        @Override public void emitStructureMob(String type, String reason, double x, double y,
                double z, float yaw, float pitch) {
            entities.add(new EntityEmission(type, reason, x, y, z, yaw, pitch, ELDER_PAYLOAD));
        }
        private AtomicSettlement freeze(Mc263HardcodedStructureCarrier carrier,
                byte[] successorStr, byte[] randomContinuation, OperationReceipt operations) {
            BoundingBox aggregate = aggregate(writes);
            String identity = "minecraft:monument@" + carrier.chunkX() + "," + carrier.chunkZ();
            return new AtomicSettlement(identity, aggregate, writes,
                    List.copyOf(fluidTicks.values()), entities, operations, successorStr,
                    randomContinuation);
        }
    }

    private static final class TracingWorld implements WorldAccess {
        private final StagingWorld delegate;
        private final ArrayList<String> events = new ArrayList<>();
        private TracingWorld(StagingWorld delegate) { this.delegate = delegate; }
        @Override public Set<String> capabilities() { return delegate.capabilities(); }
        @Override public boolean supportsExactState(String state) { return delegate.supportsExactState(state); }
        @Override public int seaLevel() { return delegate.seaLevel(); }
        @Override public int minY() { return delegate.minY(); }
        @Override public String blockState(BlockPos p) {
            String result = delegate.blockState(p); record("getBlockState", posArg(p),
                    "state:" + canonicalState(result)); return result;
        }
        @Override public String fluidState(BlockPos p) {
            String result = delegate.fluidState(p); record("getFluidState", posArg(p),
                    "fluid:" + result); return result;
        }
        @Override public boolean isReplaceableByStructures(BlockPos p, String state) {
            return delegate.isReplaceableByStructures(p, state);
        }
        @Override public boolean predictsSetBlockResult(BlockPos p, String state, int flags) {
            return delegate.predictsSetBlockResult(p, state, flags);
        }
        @Override public boolean setBlock(BlockPos p, String state, int flags) {
            boolean result = delegate.setBlock(p, state, flags);
            record("setBlock", posArg(p) + "|state:" + canonicalState(state) + "|" + flags,
                    Boolean.toString(result)); return result;
        }
        @Override public void scheduleFluidTick(BlockPos p, String fluidKey, int delay) {
            delegate.scheduleFluidTick(p, fluidKey, delay);
            record("scheduleTick", posArg(p)
                    + "|net.minecraft.world.level.material.WaterFluid$Source|" + delay, "null");
        }
        @Override public void emitStructureMob(String type, String reason, double x, double y,
                double z, float yaw, float pitch) {
            delegate.emitStructureMob(type, reason, x, y, z, yaw, pitch);
            record("addFreshEntityWithPassengers", "entity:" + type + "@" + x + "," + y
                    + "," + z, "null");
        }
        private void record(String method, String args, String result) {
            events.add(events.size() + "|" + method + "|" + args + "->" + result);
        }
        private OperationReceipt freeze() { return new OperationReceipt(events); }
    }

    private static String posArg(BlockPos p) {
        return "pos:[" + p.x() + ", " + p.y() + ", " + p.z() + "]";
    }
    private static String canonicalState(String state) {
        return WATER.equals(state) ? "minecraft:water[level=0]" : state;
    }
    private static String fluidForState(String state) {
        return WATER.equals(key(state)) ? "minecraft:water[falling=false]" : "minecraft:empty";
    }

    private static BoundingBox aggregate(List<BlockWrite> writes) {
        if (writes.isEmpty()) throw new IllegalArgumentException("empty monument aggregate");
        BlockPos first = writes.getFirst().position();
        int minX = first.x(), minY = first.y(), minZ = first.z();
        int maxX = minX, maxY = minY, maxZ = minZ;
        for (BlockWrite write : writes) {
            BlockPos p = write.position();
            minX = Math.min(minX, p.x()); minY = Math.min(minY, p.y());
            minZ = Math.min(minZ, p.z()); maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y()); maxZ = Math.max(maxZ, p.z());
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void writePos(DataOutputStream out, BlockPos p) throws IOException {
        out.writeInt(p.x()); out.writeInt(p.y()); out.writeInt(p.z());
    }
    private static void writeAscii(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        out.writeShort(bytes.length); out.write(bytes);
    }
    private static void writeBox(DataOutputStream out, BoundingBox b) throws IOException {
        out.writeInt(b.minX()); out.writeInt(b.minY()); out.writeInt(b.minZ());
        out.writeInt(b.maxX()); out.writeInt(b.maxY()); out.writeInt(b.maxZ());
    }
    private static byte[] sha256(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    public static final class Clip {
        private final int minX, minY, minZ, maxX, maxY, maxZ;
        public Clip(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted ocean-monument clip");
            }
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
        public boolean contains(BlockPos value) {
            return value.x >= minX && value.x <= maxX && value.y >= minY && value.y <= maxY
                    && value.z >= minZ && value.z <= maxZ;
        }
        public int minX() { return minX; }
        public int minZ() { return minZ; }
        public static Clip chunk(int chunkX, int chunkZ) {
            int x = Math.multiplyExact(chunkX, 16);
            int z = Math.multiplyExact(chunkZ, 16);
            return new Clip(x, com.gameexpert.terrain.Blocks.MIN_Y, z, x + 15,
                    com.gameexpert.terrain.Blocks.MAX_Y, z + 15);
        }
        private boolean isWholeChunk() {
            return minX == Math.multiplyExact(Math.floorDiv(minX, 16), 16)
                    && minZ == Math.multiplyExact(Math.floorDiv(minZ, 16), 16)
                    && maxX == minX + 15 && maxZ == minZ + 15
                    && minY == com.gameexpert.terrain.Blocks.MIN_Y
                    && maxY == com.gameexpert.terrain.Blocks.MAX_Y;
        }
        private boolean intersects(int x0, int z0, int x1, int z1) {
            return maxX >= x0 && minX <= x1 && maxZ >= z0 && minZ <= z1;
        }
    }

    /** Executes one official child/building postProcess call with the caller's continued RNG. */
    public static void postProcess(PieceFact piece, List<RoomFact> roomFacts,
            Rotation orientation, Clip clip, WorldAccess world, PostProcessRandom random) {
        Context c = preflight(piece, roomFacts, orientation, clip, world, random);
        switch (piece.kind()) {
            case MONUMENT_BUILDING -> building(c);
            case MONUMENT_ENTRY -> entry(c);
            case MONUMENT_CORE -> core(c);
            case MONUMENT_DOUBLE_X -> doubleX(c);
            case MONUMENT_DOUBLE_XY -> doubleXY(c);
            case MONUMENT_DOUBLE_Y -> doubleY(c);
            case MONUMENT_DOUBLE_YZ -> doubleYZ(c);
            case MONUMENT_DOUBLE_Z -> doubleZ(c);
            case MONUMENT_SIMPLE -> simple(c);
            case MONUMENT_SIMPLE_TOP -> simpleTop(c);
            case MONUMENT_WING -> wing(c);
            case MONUMENT_PENTHOUSE -> penthouse(c);
            default -> throw new UnsupportedOperationException("unknown ocean-monument piece");
        }
    }

    private static Context preflight(PieceFact piece, List<RoomFact> roomFacts,
            Rotation orientation, Clip clip, WorldAccess world, PostProcessRandom random) {
        Objects.requireNonNull(piece, "piece");
        Objects.requireNonNull(roomFacts, "room facts");
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(clip, "clip");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(random, "random");
        if (orientation != Rotation.NORTH && orientation != Rotation.SOUTH
                && orientation != Rotation.WEST && orientation != Rotation.EAST) {
            throw new UnsupportedOperationException("monument orientation is not cardinal");
        }
        if (!piece.kind().name().startsWith("MONUMENT_")) {
            throw new UnsupportedOperationException("unknown ocean-monument piece");
        }
        Map<Integer, RoomFact> rooms = new HashMap<>();
        for (RoomFact room : roomFacts) {
            if (room == null || rooms.put(room.index(), room) != null) {
                throw new IllegalArgumentException("duplicate or null monument room");
            }
        }
        validatePiece(piece, rooms);
        Set<String> capabilities = Set.copyOf(Objects.requireNonNull(
                world.capabilities(), "capabilities"));
        if (!KNOWN_CAPABILITIES.containsAll(capabilities)) {
            throw new UnsupportedOperationException("unknown ocean-monument capability");
        }
        Set<String> required = new HashSet<>(Set.of("block_query", "sea_level", "block_write"));
        if (piece.kind() == PieceKind.MONUMENT_BUILDING) {
            required.add("replaceable_query"); required.add("minimum_y");
        }
        if (piece.kind() == PieceKind.MONUMENT_WING
                || piece.kind() == PieceKind.MONUMENT_PENTHOUSE) {
            required.add("elder_guardian_emission");
        }
        if (!capabilities.containsAll(required)) {
            throw new UnsupportedOperationException("missing ocean-monument capability");
        }
        for (String state : STATES) {
            if (!world.supportsExactState(state)) {
                throw new UnsupportedOperationException("unsupported ocean-monument state: " + state);
            }
        }
        return new Context(piece, rooms, orientation, clip, world, random);
    }

    private static void validatePiece(PieceFact piece, Map<Integer, RoomFact> rooms) {
        int expected = switch (piece.kind()) {
            case MONUMENT_BUILDING -> 0;
            case MONUMENT_ENTRY, MONUMENT_SIMPLE, MONUMENT_SIMPLE_TOP,
                    MONUMENT_WING, MONUMENT_PENTHOUSE -> 1;
            case MONUMENT_DOUBLE_X, MONUMENT_DOUBLE_Y, MONUMENT_DOUBLE_Z -> 2;
            case MONUMENT_DOUBLE_XY, MONUMENT_DOUBLE_YZ -> 4;
            case MONUMENT_CORE -> 8;
            default -> throw new UnsupportedOperationException("unknown ocean-monument piece");
        };
        if (piece.roomIndexes().size() != expected) {
            throw new IllegalArgumentException("invalid ocean-monument piece room count");
        }
        for (int room : piece.roomIndexes()) {
            if (!rooms.containsKey(room)) throw new IllegalArgumentException("missing monument room");
        }
        if (piece.kind() == PieceKind.MONUMENT_SIMPLE
                && (piece.design() < 0 || piece.design() > 2)) {
            throw new IllegalArgumentException("unknown simple-room design");
        }
        if (piece.kind() == PieceKind.MONUMENT_WING
                && (piece.design() < 0 || piece.design() > 1)) {
            throw new IllegalArgumentException("unknown wing-room design");
        }
        if (piece.kind() != PieceKind.MONUMENT_SIMPLE && piece.kind() != PieceKind.MONUMENT_WING
                && piece.design() != -1) {
            throw new IllegalArgumentException("unexpected ocean-monument design");
        }
    }

    private static final class Context {
        private final PieceFact piece;
        private final Map<Integer, RoomFact> rooms;
        private final Rotation rotation;
        private final Clip clip;
        private final WorldAccess world;
        private final PostProcessRandom random;
        private Context(PieceFact piece, Map<Integer, RoomFact> rooms, Rotation rotation,
                Clip clip, WorldAccess world, PostProcessRandom random) {
            this.piece = piece; this.rooms = rooms; this.rotation = rotation;
            this.clip = clip; this.world = world; this.random = random;
        }
        private RoomFact anchor() { return room(piece.roomIndexes().get(0)); }
        private RoomFact room(int index) {
            RoomFact value = rooms.get(index);
            if (value == null) throw new IllegalArgumentException("missing monument room " + index);
            return value;
        }
        private RoomFact neighbor(RoomFact room, int direction) {
            if (room.index() == 52 && direction == UP) return rooms.get(1003);
            if (room.index() == 25 && direction == SOUTH) return rooms.get(1001);
            if (room.index() == 29 && direction == SOUTH) return rooms.get(1002);
            if (room.index() == 1003 && direction == DOWN) return rooms.get(52);
            if (room.index() == 1001 && direction == NORTH) return rooms.get(25);
            if (room.index() == 1002 && direction == NORTH) return rooms.get(29);
            if (room.index() >= 1000) return null;
            int x = room.index() % 5, z = room.index() / 5 % 5, y = room.index() / 25;
            int index = switch (direction) {
                case DOWN -> y == 0 ? -1 : room.index() - 25;
                case UP -> room.index() >= 75 ? -1 : room.index() + 25;
                case NORTH -> z == 4 ? -1 : room.index() + 5;
                case SOUTH -> z == 0 ? -1 : room.index() - 5;
                case WEST -> x == 0 ? -1 : room.index() - 1;
                case EAST -> x == 4 ? -1 : room.index() + 1;
                default -> -1;
            };
            return rooms.get(index);
        }
        private boolean open(RoomFact room, int direction) {
            return (room.openingMask() & (1 << direction)) != 0;
        }
        private boolean noNeighbor(RoomFact room, int direction) {
            return neighbor(room, direction) == null;
        }
    }

    private static BlockPos pos(Context c, int x, int y, int z) {
        BoundingBox box = c.piece.boundingBox();
        return switch (c.rotation) {
            case NORTH -> new BlockPos(Math.addExact(box.minX(), x), Math.addExact(box.minY(), y),
                    Math.subtractExact(box.maxZ(), z));
            case SOUTH -> new BlockPos(Math.addExact(box.minX(), x), Math.addExact(box.minY(), y),
                    Math.addExact(box.minZ(), z));
            case WEST -> new BlockPos(Math.subtractExact(box.maxX(), z), Math.addExact(box.minY(), y),
                    Math.addExact(box.minZ(), x));
            case EAST -> new BlockPos(Math.addExact(box.minX(), z), Math.addExact(box.minY(), y),
                    Math.addExact(box.minZ(), x));
            default -> throw new UnsupportedOperationException("monument orientation is not cardinal");
        };
    }

    private static void block(Context c, String state, int x, int y, int z) {
        BlockPos position = pos(c, x, y, z);
        if (c.clip.contains(position)) place(c, position, state);
    }

    private static void place(Context c, BlockPos position, String state) {
        c.world.setBlock(position, state, 2);
        String fluid = c.world.fluidState(position);
        if (fluid.equals("minecraft:water[falling=false]")) {
            c.world.scheduleFluidTick(position, WATER, 0);
        } else if (!fluid.equals("minecraft:empty")) {
            throw new UnsupportedOperationException("unknown monument fluid state: " + fluid);
        }
    }

    private static void box(Context c, String state,
            int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) block(c, state, x, y, z);
    }

    private static String key(String state) {
        Objects.requireNonNull(state, "exact state");
        int property = state.indexOf('[');
        String result = property < 0 ? state : state.substring(0, property);
        if (!result.startsWith("minecraft:")) throw new IllegalArgumentException("noncanonical state");
        return result;
    }

    private static void water(Context c, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                BlockPos position = pos(c, x, y, z);
                if (!c.clip.contains(position)) continue;
                String existing = key(c.world.blockState(position));
                if (existing.equals(WATER) || existing.equals("minecraft:ice")
                        || existing.equals("minecraft:packed_ice")
                        || existing.equals("minecraft:blue_ice")) continue;
                place(c, position, position.y() >= c.world.seaLevel() ? AIR : WATER);
            }
        }
    }

    private static void fillOnly(Context c, String state,
            int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                BlockPos position = pos(c, x, y, z);
                if (!c.clip.contains(position)) continue;
                if (key(c.world.blockState(position)).equals(WATER)) {
                    place(c, position, state);
                }
            }
        }
    }

    private static void floor(Context c, int x, int z, boolean downOpening) {
        if (!downOpening) { box(c, PRISMARINE, x, 0, z, x + 7, 0, z + 7); return; }
        box(c, PRISMARINE, x, 0, z, x + 2, 0, z + 7);
        box(c, PRISMARINE, x + 5, 0, z, x + 7, 0, z + 7);
        box(c, PRISMARINE, x + 3, 0, z, x + 4, 0, z + 2);
        box(c, PRISMARINE, x + 3, 0, z + 5, x + 4, 0, z + 7);
        box(c, PRISMARINE_BRICKS, x + 3, 0, z + 2, x + 4, 0, z + 2);
        box(c, PRISMARINE_BRICKS, x + 3, 0, z + 5, x + 4, 0, z + 5);
        box(c, PRISMARINE_BRICKS, x + 2, 0, z + 3, x + 2, 0, z + 4);
        box(c, PRISMARINE_BRICKS, x + 5, 0, z + 3, x + 5, 0, z + 4);
    }

    private static void elder(Context c, int x, int y, int z) {
        BlockPos p = pos(c, x, y, z);
        if (c.clip.contains(p)) c.world.emitStructureMob(ELDER_GUARDIAN, "minecraft:structure",
                p.x() + 0.5D, p.y(), p.z() + 0.5D, 0.0F, 0.0F);
    }

    private static void entry(Context c) {
        box(c, PRISMARINE_BRICKS, 0, 3, 0, 2, 3, 7); box(c, PRISMARINE_BRICKS, 5, 3, 0, 7, 3, 7);
        box(c, PRISMARINE_BRICKS, 0, 2, 0, 1, 2, 7); box(c, PRISMARINE_BRICKS, 6, 2, 0, 7, 2, 7);
        box(c, PRISMARINE_BRICKS, 0, 1, 0, 0, 1, 7); box(c, PRISMARINE_BRICKS, 7, 1, 0, 7, 1, 7);
        box(c, PRISMARINE_BRICKS, 0, 1, 7, 7, 3, 7); box(c, PRISMARINE_BRICKS, 1, 1, 0, 2, 3, 0);
        box(c, PRISMARINE_BRICKS, 5, 1, 0, 6, 3, 0);
        RoomFact r = c.anchor();
        if (c.open(r, NORTH)) water(c, 3, 1, 7, 4, 2, 7);
        if (c.open(r, WEST)) water(c, 0, 1, 3, 1, 2, 4);
        if (c.open(r, EAST)) water(c, 6, 1, 3, 7, 2, 4);
    }

    private static void core(Context c) {
        fillOnly(c, PRISMARINE, 1, 8, 0, 14, 8, 14);
        box(c, PRISMARINE_BRICKS, 0, 7, 0, 0, 7, 15); box(c, PRISMARINE_BRICKS, 15, 7, 0, 15, 7, 15);
        box(c, PRISMARINE_BRICKS, 1, 7, 0, 15, 7, 0); box(c, PRISMARINE_BRICKS, 1, 7, 15, 14, 7, 15);
        for (int y = 1; y <= 6; y++) {
            String s = y == 2 || y == 6 ? PRISMARINE : PRISMARINE_BRICKS;
            for (int x = 0; x <= 15; x += 15) {
                box(c, s, x, y, 0, x, y, 1); box(c, s, x, y, 6, x, y, 9); box(c, s, x, y, 14, x, y, 15);
            }
            box(c, s, 1, y, 0, 1, y, 0); box(c, s, 6, y, 0, 9, y, 0);
            box(c, s, 14, y, 0, 14, y, 0); box(c, s, 1, y, 15, 14, y, 15);
        }
        box(c, DARK_PRISMARINE, 6, 3, 6, 9, 6, 9); box(c, GOLD_BLOCK, 7, 4, 7, 8, 5, 8);
        for (int y = 3; y <= 6; y += 3) for (int x = 6; x <= 9; x += 3) {
            block(c, SEA_LANTERN, x, y, 6); block(c, SEA_LANTERN, x, y, 9);
        }
        int[][] columns = {{5,6},{5,9},{10,6},{10,9}};
        for (int[] p : columns) box(c, PRISMARINE_BRICKS, p[0], 1, p[1], p[0], 2, p[1]);
        int[][] cross = {{6,5},{9,5},{6,10},{9,10}};
        for (int[] p : cross) box(c, PRISMARINE_BRICKS, p[0], 1, p[1], p[0], 2, p[1]);
        int[][] tall = {{5,5},{5,10},{10,5},{10,10}};
        for (int[] p : tall) box(c, PRISMARINE_BRICKS, p[0], 2, p[1], p[0], 6, p[1]);
        box(c, PRISMARINE_BRICKS, 5, 7, 1, 5, 7, 6); box(c, PRISMARINE_BRICKS, 10, 7, 1, 10, 7, 6);
        box(c, PRISMARINE_BRICKS, 5, 7, 9, 5, 7, 14); box(c, PRISMARINE_BRICKS, 10, 7, 9, 10, 7, 14);
        box(c, PRISMARINE_BRICKS, 1, 7, 5, 6, 7, 5); box(c, PRISMARINE_BRICKS, 1, 7, 10, 6, 7, 10);
        box(c, PRISMARINE_BRICKS, 9, 7, 5, 14, 7, 5); box(c, PRISMARINE_BRICKS, 9, 7, 10, 14, 7, 10);
        int[][] corners = {{2,2,2,3},{3,2,3,2},{13,2,13,3},{12,2,12,2},
                {2,12,2,13},{3,13,3,13},{13,12,13,13},{12,13,12,13}};
        for (int[] p : corners) box(c, PRISMARINE_BRICKS, p[0], 1, p[1], p[2], 1, p[3]);
    }

    private static void doubleX(Context c) {
        RoomFact west = c.anchor(), east = c.neighbor(west, EAST);
        if (west.index() / 25 > 0) { floor(c, 8, 0, c.open(east, DOWN)); floor(c, 0, 0, c.open(west, DOWN)); }
        if (c.noNeighbor(west, UP)) fillOnly(c, PRISMARINE, 1, 4, 1, 7, 4, 6);
        if (c.noNeighbor(east, UP)) fillOnly(c, PRISMARINE, 8, 4, 1, 14, 4, 6);
        for (int y = 3; y >= 1; y--) {
            String s = y == 2 ? PRISMARINE : PRISMARINE_BRICKS;
            box(c, s, 0, y, 0, 0, y, 7); box(c, s, 15, y, 0, 15, y, 7);
            box(c, s, 1, y, 0, 15, y, 0); box(c, s, 1, y, 7, 14, y, 7);
        }
        box(c, PRISMARINE_BRICKS, 5, 1, 0, 10, 1, 4); box(c, PRISMARINE, 6, 2, 0, 9, 2, 3);
        box(c, PRISMARINE_BRICKS, 5, 3, 0, 10, 3, 4);
        block(c, SEA_LANTERN, 6, 2, 3); block(c, SEA_LANTERN, 9, 2, 3);
        horizontalOpenings(c, west, 0, 3); horizontalOpenings(c, east, 8, 11);
    }

    private static void doubleZ(Context c) {
        RoomFact south = c.anchor(), north = c.neighbor(south, NORTH);
        if (south.index() / 25 > 0) { floor(c, 0, 8, c.open(north, DOWN)); floor(c, 0, 0, c.open(south, DOWN)); }
        if (c.noNeighbor(south, UP)) fillOnly(c, PRISMARINE, 1, 4, 1, 6, 4, 7);
        if (c.noNeighbor(north, UP)) fillOnly(c, PRISMARINE, 1, 4, 8, 6, 4, 14);
        for (int y = 3; y >= 1; y--) {
            String s = y == 2 ? PRISMARINE : PRISMARINE_BRICKS;
            box(c, s, 0, y, 0, 0, y, 15); box(c, s, 7, y, 0, 7, y, 15);
            box(c, s, 1, y, 0, 7, y, 0); box(c, s, 1, y, 15, 6, y, 15);
        }
        box(c,PRISMARINE_BRICKS,1,1,1,1,1,2);box(c,PRISMARINE_BRICKS,6,1,1,6,1,2);
        box(c,PRISMARINE_BRICKS,1,3,1,1,3,2);box(c,PRISMARINE_BRICKS,6,3,1,6,3,2);
        box(c,PRISMARINE_BRICKS,1,1,13,1,1,14);box(c,PRISMARINE_BRICKS,6,1,13,6,1,14);
        box(c,PRISMARINE_BRICKS,1,3,13,1,3,14);box(c,PRISMARINE_BRICKS,6,3,13,6,3,14);
        for (int z : new int[]{6,9}) for (int x : new int[]{2,5}) box(c, PRISMARINE_BRICKS, x, 1, z, x, 3, z);
        box(c, PRISMARINE_BRICKS, 3, 2, 6, 4, 2, 6); box(c, PRISMARINE_BRICKS, 3, 2, 9, 4, 2, 9);
        box(c, PRISMARINE_BRICKS, 2, 2, 7, 2, 2, 8); box(c, PRISMARINE_BRICKS, 5, 2, 7, 5, 2, 8);
        block(c,SEA_LANTERN,2,2,5);block(c,SEA_LANTERN,5,2,5);
        block(c,SEA_LANTERN,2,2,10);block(c,SEA_LANTERN,5,2,10);
        block(c,PRISMARINE_BRICKS,2,3,5);block(c,PRISMARINE_BRICKS,5,3,5);
        block(c,PRISMARINE_BRICKS,2,3,10);block(c,PRISMARINE_BRICKS,5,3,10);
        if(c.open(south,SOUTH))water(c,3,1,0,4,2,0);
        if(c.open(south,EAST))water(c,7,1,3,7,2,4);
        if(c.open(south,WEST))water(c,0,1,3,0,2,4);
        if (c.open(north, NORTH)) water(c, 3, 1, 15, 4, 2, 15);
        if (c.open(north, WEST)) water(c, 0, 1, 11, 0, 2, 12);
        if (c.open(north, EAST)) water(c, 7, 1, 11, 7, 2, 12);
    }

    private static void doubleY(Context c) {
        RoomFact lower = c.anchor(), upper = c.neighbor(lower, UP);
        if (lower.index() / 25 > 0) floor(c, 0, 0, c.open(lower, DOWN));
        if (c.noNeighbor(upper, UP)) fillOnly(c, PRISMARINE, 1, 8, 1, 6, 8, 6);
        box(c, PRISMARINE_BRICKS, 0, 4, 0, 0, 4, 7); box(c, PRISMARINE_BRICKS, 7, 4, 0, 7, 4, 7);
        box(c, PRISMARINE_BRICKS, 1, 4, 0, 6, 4, 0); box(c, PRISMARINE_BRICKS, 1, 4, 7, 6, 4, 7);
        int[][] ledges = {{2,1,2,2},{1,2,1,2},{5,1,5,2},{6,2,6,2},
                {2,5,2,6},{1,5,1,5},{5,5,5,6},{6,5,6,5}};
        for (int[] p : ledges) box(c, PRISMARINE_BRICKS, p[0], 4, p[1], p[2], 4, p[3]);
        RoomFact room = lower;
        for (int y = 1; y <= 5; y += 4) { wallLayer(c, room, y); room = upper; }
    }

    private static void wallLayer(Context c, RoomFact room, int y) {
        if (c.open(room, SOUTH)) { box(c, PRISMARINE_BRICKS, 2,y,0,2,y+2,0); box(c, PRISMARINE_BRICKS,5,y,0,5,y+2,0); box(c,PRISMARINE_BRICKS,3,y+2,0,4,y+2,0); }
        else { box(c,PRISMARINE_BRICKS,0,y,0,7,y+2,0); box(c,PRISMARINE,0,y+1,0,7,y+1,0); }
        if (c.open(room, NORTH)) { box(c,PRISMARINE_BRICKS,2,y,7,2,y+2,7); box(c,PRISMARINE_BRICKS,5,y,7,5,y+2,7); box(c,PRISMARINE_BRICKS,3,y+2,7,4,y+2,7); }
        else { box(c,PRISMARINE_BRICKS,0,y,7,7,y+2,7); box(c,PRISMARINE,0,y+1,7,7,y+1,7); }
        if (c.open(room, WEST)) { box(c,PRISMARINE_BRICKS,0,y,2,0,y+2,2); box(c,PRISMARINE_BRICKS,0,y,5,0,y+2,5); box(c,PRISMARINE_BRICKS,0,y+2,3,0,y+2,4); }
        else { box(c,PRISMARINE_BRICKS,0,y,0,0,y+2,7); box(c,PRISMARINE,0,y+1,0,0,y+1,7); }
        if (c.open(room, EAST)) { box(c,PRISMARINE_BRICKS,7,y,2,7,y+2,2); box(c,PRISMARINE_BRICKS,7,y,5,7,y+2,5); box(c,PRISMARINE_BRICKS,7,y+2,3,7,y+2,4); }
        else { box(c,PRISMARINE_BRICKS,7,y,0,7,y+2,7); box(c,PRISMARINE,7,y+1,0,7,y+1,7); }
    }

    private static void doubleXY(Context c) {
        RoomFact west = c.anchor(), east = c.neighbor(west, EAST);
        RoomFact westUp = c.neighbor(west, UP), eastUp = c.neighbor(east, UP);
        if (west.index() / 25 > 0) { floor(c,8,0,c.open(east,DOWN)); floor(c,0,0,c.open(west,DOWN)); }
        if (c.noNeighbor(westUp, UP)) fillOnly(c,PRISMARINE,1,8,1,7,8,6);
        if (c.noNeighbor(eastUp, UP)) fillOnly(c,PRISMARINE,8,8,1,14,8,6);
        for (int y=1;y<=7;y++) { String s=y==2||y==6?PRISMARINE:PRISMARINE_BRICKS;
            box(c,s,0,y,0,0,y,7); box(c,s,15,y,0,15,y,7); box(c,s,1,y,0,15,y,0); box(c,s,1,y,7,14,y,7); }
        box(c,PRISMARINE_BRICKS,2,1,3,2,7,4); box(c,PRISMARINE_BRICKS,3,1,2,4,7,2); box(c,PRISMARINE_BRICKS,3,1,5,4,7,5);
        box(c,PRISMARINE_BRICKS,13,1,3,13,7,4); box(c,PRISMARINE_BRICKS,11,1,2,12,7,2); box(c,PRISMARINE_BRICKS,11,1,5,12,7,5);
        box(c,PRISMARINE_BRICKS,5,1,3,5,3,4); box(c,PRISMARINE_BRICKS,10,1,3,10,3,4); box(c,PRISMARINE_BRICKS,5,7,2,10,7,5);
        box(c,PRISMARINE_BRICKS,5,5,2,5,7,2);box(c,PRISMARINE_BRICKS,10,5,2,10,7,2);
        box(c,PRISMARINE_BRICKS,5,5,5,5,7,5);box(c,PRISMARINE_BRICKS,10,5,5,10,7,5);
        block(c,PRISMARINE_BRICKS,6,6,2);block(c,PRISMARINE_BRICKS,9,6,2);
        block(c,PRISMARINE_BRICKS,6,6,5);block(c,PRISMARINE_BRICKS,9,6,5);
        box(c,PRISMARINE_BRICKS,5,4,3,6,4,4); box(c,PRISMARINE_BRICKS,9,4,3,10,4,4);
        for(int x:new int[]{5,10}) for(int z:new int[]{2,5}) block(c,SEA_LANTERN,x,4,z);
        horizontalOpenings(c,west,0,3); horizontalOpenings(c,east,8,11);
        upperOpenings(c,westUp,0,3); upperOpenings(c,eastUp,8,11);
    }

    private static void doubleYZ(Context c) {
        RoomFact south=c.anchor(), north=c.neighbor(south,NORTH), southUp=c.neighbor(south,UP), northUp=c.neighbor(north,UP);
        if(south.index()/25>0){floor(c,0,8,c.open(north,DOWN));floor(c,0,0,c.open(south,DOWN));}
        if(c.noNeighbor(southUp,UP))fillOnly(c,PRISMARINE,1,8,1,6,8,7);
        if(c.noNeighbor(northUp,UP))fillOnly(c,PRISMARINE,1,8,8,6,8,14);
        for(int y=1;y<=7;y++){String s=y==2||y==6?PRISMARINE:PRISMARINE_BRICKS;
            box(c,s,0,y,0,0,y,15);box(c,s,7,y,0,7,y,15);box(c,s,1,y,0,6,y,0);box(c,s,1,y,15,6,y,15);}
        for(int y=1;y<=7;y++)box(c,y==2||y==6?SEA_LANTERN:DARK_PRISMARINE,3,y,7,4,y,8);
        if(c.open(south,SOUTH))water(c,3,1,0,4,2,0);
        if(c.open(south,EAST))water(c,7,1,3,7,2,4);
        if(c.open(south,WEST))water(c,0,1,3,0,2,4);
        if(c.open(north,NORTH))water(c,3,1,15,4,2,15);if(c.open(north,WEST))water(c,0,1,11,0,2,12);if(c.open(north,EAST))water(c,7,1,11,7,2,12);
        if(c.open(southUp,SOUTH))water(c,3,5,0,4,6,0);
        if(c.open(southUp,EAST)){water(c,7,5,3,7,6,4);box(c,PRISMARINE_BRICKS,5,4,2,6,4,5);box(c,PRISMARINE_BRICKS,6,1,2,6,3,2);box(c,PRISMARINE_BRICKS,6,1,5,6,3,5);}
        if(c.open(southUp,WEST)){water(c,0,5,3,0,6,4);box(c,PRISMARINE_BRICKS,1,4,2,2,4,5);box(c,PRISMARINE_BRICKS,1,1,2,1,3,2);box(c,PRISMARINE_BRICKS,1,1,5,1,3,5);}
        if(c.open(northUp,NORTH))water(c,3,5,15,4,6,15);
        if(c.open(northUp,WEST)){water(c,0,5,11,0,6,12);box(c,PRISMARINE_BRICKS,1,4,10,2,4,13);box(c,PRISMARINE_BRICKS,1,1,10,1,3,10);box(c,PRISMARINE_BRICKS,1,1,13,1,3,13);}
        if(c.open(northUp,EAST)){water(c,7,5,11,7,6,12);box(c,PRISMARINE_BRICKS,5,4,10,6,4,13);box(c,PRISMARINE_BRICKS,6,1,10,6,3,10);box(c,PRISMARINE_BRICKS,6,1,13,6,3,13);}
    }

    private static void horizontalOpenings(Context c, RoomFact room, int xOffset, int doorX) {
        if(c.open(room,SOUTH))water(c,xOffset+3,1,0,xOffset+4,2,0);
        if(c.open(room,NORTH))water(c,xOffset+3,1,7,xOffset+4,2,7);
        if(c.open(room,WEST)&&xOffset==0)water(c,0,1,3,0,2,4);
        if(c.open(room,EAST)&&xOffset==8)water(c,15,1,3,15,2,4);
    }

    private static void upperOpenings(Context c,RoomFact room,int xOffset,int ignored) {
        if(c.open(room,SOUTH))water(c,xOffset+3,5,0,xOffset+4,6,0);
        if(c.open(room,NORTH))water(c,xOffset+3,5,7,xOffset+4,6,7);
        if(c.open(room,WEST)&&xOffset==0)water(c,0,5,3,0,6,4);
        if(c.open(room,EAST)&&xOffset==8)water(c,15,5,3,15,6,4);
    }

    private static void simpleTop(Context c) {
        RoomFact r=c.anchor();
        if(r.index()/25>0)floor(c,0,0,c.open(r,DOWN));
        if(c.noNeighbor(r,UP))fillOnly(c,PRISMARINE,1,4,1,6,4,6);
        for(int x=1;x<=6;x++)for(int z=1;z<=6;z++){
            if(c.random.nextInt(3)==0)continue;
            int y=2+(c.random.nextInt(4)==0?0:1);box(c,WET_SPONGE,x,y,z,x,3,z);
        }
        simpleFrame(c);
        if(c.open(r,SOUTH))water(c,3,1,0,4,2,0);
    }

    private static void simpleFrame(Context c) {
        for(int y=1;y<=3;y++){String s=y==2?DARK_PRISMARINE:PRISMARINE_BRICKS;
            box(c,s,0,y,0,0,y,7);box(c,s,7,y,0,7,y,7);box(c,s,1,y,0,6,y,0);box(c,s,1,y,7,6,y,7);}
        box(c,DARK_PRISMARINE,0,1,3,0,2,4);box(c,DARK_PRISMARINE,7,1,3,7,2,4);
        box(c,DARK_PRISMARINE,3,1,0,4,2,0);box(c,DARK_PRISMARINE,3,1,7,4,2,7);
    }

    private static void simple(Context c) {
        RoomFact r=c.anchor();
        if(r.index()/25>0)floor(c,0,0,c.open(r,DOWN));
        if(c.noNeighbor(r,UP))fillOnly(c,PRISMARINE,1,4,1,6,4,6);
        boolean pillar=c.piece.design()!=0&&c.random.nextBoolean()&&!c.open(r,DOWN)&&!c.open(r,UP)
                &&Integer.bitCount(r.openingMask())>1;
        if(c.piece.design()==0)simpleZero(c,r);
        else if(c.piece.design()==1)simpleOne(c,r);
        else if(c.piece.design()==2){simpleFrame(c);
            if(c.open(r,SOUTH))water(c,3,1,0,4,2,0);
            if(c.open(r,NORTH))water(c,3,1,7,4,2,7);
            if(c.open(r,WEST))water(c,0,1,3,0,2,4);
            if(c.open(r,EAST))water(c,7,1,3,7,2,4);}
        else throw new UnsupportedOperationException("unknown simple-room design");
        if(pillar){box(c,PRISMARINE_BRICKS,3,1,3,4,1,4);box(c,PRISMARINE,3,2,3,4,2,4);box(c,PRISMARINE_BRICKS,3,3,3,4,3,4);}
    }

    private static void simpleZero(Context c,RoomFact r) {
        box(c,PRISMARINE_BRICKS,0,1,0,2,1,2);box(c,PRISMARINE_BRICKS,0,3,0,2,3,2);
        box(c,PRISMARINE,0,2,0,0,2,2);box(c,PRISMARINE,1,2,0,2,2,0);block(c,SEA_LANTERN,1,2,1);
        box(c,PRISMARINE_BRICKS,5,1,0,7,1,2);box(c,PRISMARINE_BRICKS,5,3,0,7,3,2);
        box(c,PRISMARINE,7,2,0,7,2,2);box(c,PRISMARINE,5,2,0,6,2,0);block(c,SEA_LANTERN,6,2,1);
        box(c,PRISMARINE_BRICKS,0,1,5,2,1,7);box(c,PRISMARINE_BRICKS,0,3,5,2,3,7);
        box(c,PRISMARINE,0,2,5,0,2,7);box(c,PRISMARINE,1,2,7,2,2,7);block(c,SEA_LANTERN,1,2,6);
        box(c,PRISMARINE_BRICKS,5,1,5,7,1,7);box(c,PRISMARINE_BRICKS,5,3,5,7,3,7);
        box(c,PRISMARINE,7,2,5,7,2,7);box(c,PRISMARINE,5,2,7,6,2,7);block(c,SEA_LANTERN,6,2,6);
        simpleZeroWall(c,r,SOUTH);simpleZeroWall(c,r,NORTH);simpleZeroWall(c,r,WEST);simpleZeroWall(c,r,EAST);
    }

    private static void simpleZeroWall(Context c,RoomFact r,int d) {
        if(d==SOUTH){if(c.open(r,d))box(c,PRISMARINE_BRICKS,3,3,0,4,3,0);else{box(c,PRISMARINE_BRICKS,3,3,0,4,3,1);box(c,PRISMARINE,3,2,0,4,2,0);box(c,PRISMARINE_BRICKS,3,1,0,4,1,1);}}
        if(d==NORTH){if(c.open(r,d))box(c,PRISMARINE_BRICKS,3,3,7,4,3,7);else{box(c,PRISMARINE_BRICKS,3,3,6,4,3,7);box(c,PRISMARINE,3,2,7,4,2,7);box(c,PRISMARINE_BRICKS,3,1,6,4,1,7);}}
        if(d==WEST){if(c.open(r,d))box(c,PRISMARINE_BRICKS,0,3,3,0,3,4);else{box(c,PRISMARINE_BRICKS,0,3,3,1,3,4);box(c,PRISMARINE,0,2,3,0,2,4);box(c,PRISMARINE_BRICKS,0,1,3,1,1,4);}}
        if(d==EAST){if(c.open(r,d))box(c,PRISMARINE_BRICKS,7,3,3,7,3,4);else{box(c,PRISMARINE_BRICKS,6,3,3,7,3,4);box(c,PRISMARINE,7,2,3,7,2,4);box(c,PRISMARINE_BRICKS,6,1,3,7,1,4);}}
    }

    private static void simpleOne(Context c,RoomFact r) {
        box(c,PRISMARINE_BRICKS,2,1,2,2,3,2);box(c,PRISMARINE_BRICKS,2,1,5,2,3,5);
        box(c,PRISMARINE_BRICKS,5,1,5,5,3,5);box(c,PRISMARINE_BRICKS,5,1,2,5,3,2);
        block(c,SEA_LANTERN,2,2,2);block(c,SEA_LANTERN,2,2,5);
        block(c,SEA_LANTERN,5,2,5);block(c,SEA_LANTERN,5,2,2);
        box(c,PRISMARINE_BRICKS,0,1,0,1,3,0);box(c,PRISMARINE_BRICKS,0,1,1,0,3,1);
        box(c,PRISMARINE_BRICKS,0,1,7,1,3,7);box(c,PRISMARINE_BRICKS,0,1,6,0,3,6);
        box(c,PRISMARINE_BRICKS,6,1,7,7,3,7);box(c,PRISMARINE_BRICKS,7,1,6,7,3,6);
        box(c,PRISMARINE_BRICKS,6,1,0,7,3,0);box(c,PRISMARINE_BRICKS,7,1,1,7,3,1);
        int[][] dots={{1,0},{0,1},{1,7},{0,6},{6,7},{7,6},{6,0},{7,1}};for(int[]p:dots)block(c,PRISMARINE,p[0],2,p[1]);
        if(!c.open(r,SOUTH)){box(c,PRISMARINE_BRICKS,1,3,0,6,3,0);box(c,PRISMARINE,1,2,0,6,2,0);box(c,PRISMARINE_BRICKS,1,1,0,6,1,0);}
        if(!c.open(r,NORTH)){box(c,PRISMARINE_BRICKS,1,3,7,6,3,7);box(c,PRISMARINE,1,2,7,6,2,7);box(c,PRISMARINE_BRICKS,1,1,7,6,1,7);}
        if(!c.open(r,WEST)){box(c,PRISMARINE_BRICKS,0,3,1,0,3,6);box(c,PRISMARINE,0,2,1,0,2,6);box(c,PRISMARINE_BRICKS,0,1,1,0,1,6);}
        if(!c.open(r,EAST)){box(c,PRISMARINE_BRICKS,7,3,1,7,3,6);box(c,PRISMARINE,7,2,1,7,2,6);box(c,PRISMARINE_BRICKS,7,1,1,7,1,6);}
    }

    private static void wing(Context c) {
        if(c.piece.design()==0){
            for(int i=0;i<4;i++)box(c,PRISMARINE_BRICKS,10-i,3-i,20-i,12+i,3-i,20);
            box(c,PRISMARINE_BRICKS,7,0,6,15,0,16);box(c,PRISMARINE_BRICKS,6,0,6,6,3,20);box(c,PRISMARINE_BRICKS,16,0,6,16,3,20);
            box(c,PRISMARINE_BRICKS,7,1,7,7,1,20);box(c,PRISMARINE_BRICKS,15,1,7,15,1,20);
            box(c,PRISMARINE_BRICKS,7,1,6,9,3,6);box(c,PRISMARINE_BRICKS,13,1,6,15,3,6);
            box(c,PRISMARINE_BRICKS,8,1,7,9,1,7);box(c,PRISMARINE_BRICKS,13,1,7,14,1,7);
            box(c,PRISMARINE_BRICKS,9,0,5,13,0,5);box(c,DARK_PRISMARINE,10,0,7,12,0,7);
            box(c,DARK_PRISMARINE,8,0,10,8,0,12);box(c,DARK_PRISMARINE,14,0,10,14,0,12);
            for(int z=18;z>=7;z-=3){block(c,SEA_LANTERN,6,3,z);block(c,SEA_LANTERN,16,3,z);}
            block(c,SEA_LANTERN,10,0,10);block(c,SEA_LANTERN,12,0,10);
            block(c,SEA_LANTERN,10,0,12);block(c,SEA_LANTERN,12,0,12);
            block(c,SEA_LANTERN,8,3,6);block(c,SEA_LANTERN,14,3,6);
            block(c,PRISMARINE_BRICKS,4,2,4);block(c,SEA_LANTERN,4,1,4);block(c,PRISMARINE_BRICKS,4,0,4);
            block(c,PRISMARINE_BRICKS,18,2,4);block(c,SEA_LANTERN,18,1,4);block(c,PRISMARINE_BRICKS,18,0,4);
            block(c,PRISMARINE_BRICKS,4,2,18);block(c,SEA_LANTERN,4,1,18);block(c,PRISMARINE_BRICKS,4,0,18);
            block(c,PRISMARINE_BRICKS,18,2,18);block(c,SEA_LANTERN,18,1,18);block(c,PRISMARINE_BRICKS,18,0,18);
            block(c,PRISMARINE_BRICKS,9,7,20);block(c,PRISMARINE_BRICKS,13,7,20);
            box(c,PRISMARINE_BRICKS,6,0,21,7,4,21);box(c,PRISMARINE_BRICKS,15,0,21,16,4,21);
            elder(c,11,2,16);
        }else if(c.piece.design()==1){
            box(c,PRISMARINE_BRICKS,9,3,18,13,3,20);box(c,PRISMARINE_BRICKS,9,0,18,9,2,18);box(c,PRISMARINE_BRICKS,13,0,18,13,2,18);
            block(c,PRISMARINE_BRICKS,9,6,20);block(c,SEA_LANTERN,9,5,20);block(c,PRISMARINE_BRICKS,9,4,20);
            block(c,PRISMARINE_BRICKS,13,6,20);block(c,SEA_LANTERN,13,5,20);block(c,PRISMARINE_BRICKS,13,4,20);
            box(c,PRISMARINE_BRICKS,7,3,7,15,3,14);
            box(c,PRISMARINE_BRICKS,10,0,10,10,6,10);box(c,PRISMARINE_BRICKS,10,0,12,10,6,12);
            block(c,SEA_LANTERN,10,0,10);block(c,SEA_LANTERN,10,0,12);block(c,SEA_LANTERN,10,4,10);block(c,SEA_LANTERN,10,4,12);
            box(c,PRISMARINE_BRICKS,12,0,10,12,6,10);box(c,PRISMARINE_BRICKS,12,0,12,12,6,12);
            block(c,SEA_LANTERN,12,0,10);block(c,SEA_LANTERN,12,0,12);block(c,SEA_LANTERN,12,4,10);block(c,SEA_LANTERN,12,4,12);
            box(c,PRISMARINE_BRICKS,8,0,7,8,2,7);box(c,PRISMARINE_BRICKS,8,0,14,8,2,14);
            box(c,PRISMARINE_BRICKS,14,0,7,14,2,7);box(c,PRISMARINE_BRICKS,14,0,14,14,2,14);
            box(c,DARK_PRISMARINE,8,3,8,8,3,13);box(c,DARK_PRISMARINE,14,3,8,14,3,13);elder(c,11,5,13);
        }else throw new UnsupportedOperationException("unknown wing-room design");
    }

    private static void penthouse(Context c) {
        box(c,PRISMARINE_BRICKS,2,-1,2,11,-1,11);box(c,PRISMARINE,0,-1,0,1,-1,11);box(c,PRISMARINE,12,-1,0,13,-1,11);
        box(c,PRISMARINE,2,-1,0,11,-1,1);box(c,PRISMARINE,2,-1,12,11,-1,13);
        box(c,PRISMARINE_BRICKS,0,0,0,0,0,13);box(c,PRISMARINE_BRICKS,13,0,0,13,0,13);box(c,PRISMARINE_BRICKS,1,0,0,12,0,0);box(c,PRISMARINE_BRICKS,1,0,13,12,0,13);
        for(int i=2;i<=11;i+=3){block(c,SEA_LANTERN,0,0,i);block(c,SEA_LANTERN,13,0,i);block(c,SEA_LANTERN,i,0,0);}
        box(c,PRISMARINE_BRICKS,2,0,3,4,0,9);box(c,PRISMARINE_BRICKS,9,0,3,11,0,9);box(c,PRISMARINE_BRICKS,4,0,9,9,0,11);
        block(c,PRISMARINE_BRICKS,5,0,8);block(c,PRISMARINE_BRICKS,8,0,8);block(c,PRISMARINE_BRICKS,10,0,10);block(c,PRISMARINE_BRICKS,3,0,10);
        box(c,DARK_PRISMARINE,3,0,3,3,0,7);box(c,DARK_PRISMARINE,10,0,3,10,0,7);box(c,DARK_PRISMARINE,6,0,10,7,0,10);
        for(int x:new int[]{3,10})for(int z=2;z<=8;z+=3)box(c,PRISMARINE_BRICKS,x,0,z,x,2,z);
        box(c,PRISMARINE_BRICKS,5,0,10,5,2,10);box(c,PRISMARINE_BRICKS,8,0,10,8,2,10);
        box(c,DARK_PRISMARINE,6,-1,7,7,-1,8);water(c,6,-1,3,7,-1,4);elder(c,6,1,6);
    }

    private static void building(Context c) {
        int waterHeight=Math.max(c.world.seaLevel(),64)-c.piece.boundingBox().minY();
        water(c,0,0,0,58,waterHeight,58);
        buildingWing(c,false,0);buildingWing(c,true,33);entranceArches(c);entranceWall(c);
        roof(c);lowerWall(c);middleWall(c);upperWall(c);
        for(int px=0;px<7;px++){
            int pz=0;while(pz<7){if(pz==0&&px==3)pz=6;int bx=px*9,bz=pz*9;
                for(int w=0;w<4;w++)for(int d=0;d<4;d++){block(c,PRISMARINE_BRICKS,bx+w,0,bz+d);fillDown(c,bx+w,-1,bz+d);}
                pz+=(px==0||px==6)?1:6;
            }
        }
        for(int i=0;i<5;i++){water(c,-1-i,i*2,-1-i,-1-i,23,58+i);water(c,58+i,i*2,-1-i,58+i,23,58+i);
            water(c,-i,i*2,-1-i,57+i,23,-1-i);water(c,-i,i*2,58+i,57+i,23,58+i);}
    }

    private static boolean localIntersects(Context c,int x0,int z0,int x1,int z1){
        BlockPos a=pos(c,x0,0,z0),b=pos(c,x1,0,z1);
        return c.clip.intersects(Math.min(a.x(),b.x()),Math.min(a.z(),b.z()),Math.max(a.x(),b.x()),Math.max(a.z(),b.z()));
    }

    private static void fillDown(Context c,int x,int y,int z){
        BlockPos p=pos(c,x,y,z);if(!c.clip.contains(p))return;
        while(p.y()>c.world.minY()+1){String state=key(c.world.blockState(p));if(!c.world.isReplaceableByStructures(p,state))break;
            c.world.setBlock(p,PRISMARINE_BRICKS,2);p=new BlockPos(p.x(),p.y()-1,p.z());}
    }

    private static void buildingWing(Context c,boolean flipped,int off){
        if(!localIntersects(c,off,0,off+23,20))return;
        box(c,PRISMARINE,off,0,0,off+24,0,20);water(c,off,1,0,off+24,10,20);
        for(int i=0;i<4;i++){box(c,PRISMARINE_BRICKS,off+i,i+1,i,off+i,i+1,20);box(c,PRISMARINE_BRICKS,off+i+7,i+5,i+7,off+i+7,i+5,20);
            box(c,PRISMARINE_BRICKS,off+17-i,i+5,i+7,off+17-i,i+5,20);box(c,PRISMARINE_BRICKS,off+24-i,i+1,i,off+24-i,i+1,20);
            box(c,PRISMARINE_BRICKS,off+i+1,i+1,i,off+23-i,i+1,i);box(c,PRISMARINE_BRICKS,off+i+8,i+5,i+7,off+16-i,i+5,i+7);}
        box(c,PRISMARINE,off+4,4,4,off+6,4,20);box(c,PRISMARINE,off+7,4,4,off+17,4,6);box(c,PRISMARINE,off+18,4,4,off+20,4,20);
        box(c,PRISMARINE,off+11,8,11,off+13,8,20);for(int z:new int[]{12,15,18})block(c,PRISMARINE_BRICKS,off+12,9,z);
        int left=off+(flipped?19:5),right=off+(flipped?5:19);for(int z=20;z>=5;z-=3)block(c,PRISMARINE_BRICKS,left,5,z);
        for(int z=19;z>=7;z-=3)block(c,PRISMARINE_BRICKS,right,5,z);
        for(int i=0;i<4;i++)block(c,PRISMARINE_BRICKS,flipped?off+24-(17-i*3):off+17-i*3,5,5);
        block(c,PRISMARINE_BRICKS,right,5,5);box(c,PRISMARINE,off+11,1,12,off+13,7,12);box(c,PRISMARINE,off+12,1,11,off+12,7,13);
    }

    private static void entranceArches(Context c){if(!localIntersects(c,22,5,35,17))return;
        water(c,25,0,0,32,8,20);for(int i=0;i<4;i++){int z=5+i*4;
            box(c,PRISMARINE_BRICKS,24,2,z,24,4,z);box(c,PRISMARINE_BRICKS,22,4,z,23,4,z);block(c,PRISMARINE_BRICKS,25,5,z);block(c,PRISMARINE_BRICKS,26,6,z);block(c,SEA_LANTERN,26,5,z);
            box(c,PRISMARINE_BRICKS,33,2,z,33,4,z);box(c,PRISMARINE_BRICKS,34,4,z,35,4,z);block(c,PRISMARINE_BRICKS,32,5,z);block(c,PRISMARINE_BRICKS,31,6,z);block(c,SEA_LANTERN,31,5,z);
            box(c,PRISMARINE,27,6,z,30,6,z);}}

    private static void entranceWall(Context c){if(!localIntersects(c,15,20,42,21))return;
        box(c,PRISMARINE,15,0,21,42,0,21);water(c,26,1,21,31,3,21);box(c,PRISMARINE,21,12,21,36,12,21);box(c,PRISMARINE,17,11,21,40,11,21);
        box(c,PRISMARINE,16,10,21,41,10,21);box(c,PRISMARINE,15,7,21,42,9,21);box(c,PRISMARINE,16,6,21,41,6,21);box(c,PRISMARINE,17,5,21,40,5,21);
        box(c,PRISMARINE,21,4,21,36,4,21);box(c,PRISMARINE,22,3,21,26,3,21);box(c,PRISMARINE,31,3,21,35,3,21);box(c,PRISMARINE,23,2,21,25,2,21);box(c,PRISMARINE,32,2,21,34,2,21);
        box(c,PRISMARINE_BRICKS,28,4,20,29,4,21);for(int[]p:new int[][]{{27,3},{30,3},{26,2},{31,2},{25,1},{32,1}})block(c,PRISMARINE_BRICKS,p[0],p[1],21);
        for(int i=0;i<7;i++){block(c,DARK_PRISMARINE,28-i,6+i,21);block(c,DARK_PRISMARINE,29+i,6+i,21);}for(int i=0;i<4;i++){block(c,DARK_PRISMARINE,28-i,9+i,21);block(c,DARK_PRISMARINE,29+i,9+i,21);}
        block(c,DARK_PRISMARINE,28,12,21);block(c,DARK_PRISMARINE,29,12,21);for(int i=0;i<3;i++){for(int x:new int[]{22-i*2,35+i*2}){block(c,DARK_PRISMARINE,x,8,21);block(c,DARK_PRISMARINE,x,9,21);}}
        water(c,15,13,21,42,15,21);water(c,15,1,21,15,6,21);water(c,16,1,21,16,5,21);water(c,17,1,21,20,4,21);water(c,21,1,21,21,3,21);water(c,22,1,21,22,2,21);water(c,23,1,21,24,1,21);
        water(c,42,1,21,42,6,21);water(c,41,1,21,41,5,21);water(c,37,1,21,40,4,21);water(c,36,1,21,36,3,21);water(c,33,1,21,34,1,21);water(c,35,1,21,35,2,21);
    }

    private static void roof(Context c){if(!localIntersects(c,21,21,36,36))return;
        box(c,PRISMARINE,21,0,22,36,0,36);water(c,21,1,22,36,23,36);
        for(int i=0;i<4;i++){box(c,PRISMARINE_BRICKS,21+i,13+i,21+i,36-i,13+i,21+i);box(c,PRISMARINE_BRICKS,21+i,13+i,36-i,36-i,13+i,36-i);
            box(c,PRISMARINE_BRICKS,21+i,13+i,22+i,21+i,13+i,35-i);box(c,PRISMARINE_BRICKS,36-i,13+i,22+i,36-i,13+i,35-i);}
        box(c,PRISMARINE,25,16,25,32,16,32);for(int x:new int[]{25,32})for(int z:new int[]{25,32})box(c,PRISMARINE_BRICKS,x,17,z,x,19,z);
        int[][] lamps={{26,20,26,27,21,27},{26,20,31,27,21,30},{31,20,31,30,21,30},{31,20,26,30,21,27}};
        for(int[]p:lamps){block(c,PRISMARINE_BRICKS,p[0],p[1],p[2]);block(c,PRISMARINE_BRICKS,p[3],p[4],p[5]);block(c,SEA_LANTERN,p[3],20,p[5]);}
        box(c,PRISMARINE,28,21,27,29,21,27);box(c,PRISMARINE,27,21,28,27,21,29);box(c,PRISMARINE,28,21,30,29,21,30);box(c,PRISMARINE,30,21,28,30,21,29);
    }

    private static void lowerWall(Context c){
        if(localIntersects(c,0,21,6,58)){box(c,PRISMARINE,0,0,21,6,0,57);water(c,0,1,21,6,7,57);box(c,PRISMARINE,4,4,21,6,4,53);
            for(int i=0;i<4;i++)box(c,PRISMARINE_BRICKS,i,i+1,21,i,i+1,57-i);for(int z=23;z<53;z+=3)block(c,PRISMARINE_BRICKS,5,5,z);block(c,PRISMARINE_BRICKS,5,5,52);
            for(int i=0;i<4;i++)box(c,PRISMARINE_BRICKS,i,i+1,21,i,i+1,57-i);box(c,PRISMARINE,4,1,52,6,3,52);box(c,PRISMARINE,5,1,51,5,3,53);}
        if(localIntersects(c,51,21,58,58)){box(c,PRISMARINE,51,0,21,57,0,57);water(c,51,1,21,57,7,57);box(c,PRISMARINE,51,4,21,53,4,53);
            for(int i=0;i<4;i++)box(c,PRISMARINE_BRICKS,57-i,i+1,21,57-i,i+1,57-i);for(int z=23;z<53;z+=3)block(c,PRISMARINE_BRICKS,52,5,z);block(c,PRISMARINE_BRICKS,52,5,52);
            box(c,PRISMARINE,51,1,52,53,3,52);box(c,PRISMARINE,52,1,51,52,3,53);}
        if(localIntersects(c,0,51,57,57)){box(c,PRISMARINE,7,0,51,50,0,57);water(c,7,1,51,50,10,57);for(int i=0;i<4;i++)box(c,PRISMARINE_BRICKS,1+i,1+i,57-i,56-i,1+i,57-i);}
    }

    private static void middleWall(Context c){
        if(localIntersects(c,7,21,13,50)){box(c,PRISMARINE,7,0,21,13,0,50);water(c,7,1,21,13,10,50);box(c,PRISMARINE,11,8,21,13,8,53);
            for(int i=0;i<4;i++)box(c,PRISMARINE_BRICKS,7+i,5+i,21,7+i,5+i,54);for(int z=21;z<=45;z+=3)block(c,PRISMARINE_BRICKS,12,9,z);}
        if(localIntersects(c,44,21,50,54)){box(c,PRISMARINE,44,0,21,50,0,50);water(c,44,1,21,50,10,50);box(c,PRISMARINE,44,8,21,46,8,53);
            for(int i=0;i<4;i++)box(c,PRISMARINE_BRICKS,50-i,5+i,21,50-i,5+i,54);for(int z=21;z<=45;z+=3)block(c,PRISMARINE_BRICKS,45,9,z);}
        if(localIntersects(c,8,44,49,54)){box(c,PRISMARINE,14,0,44,43,0,50);water(c,14,1,44,43,10,50);
            for(int x=12;x<=45;x+=3){block(c,PRISMARINE_BRICKS,x,9,45);block(c,PRISMARINE_BRICKS,x,9,52);
                if(x==12||x==18||x==24||x==33||x==39||x==45){for(int[]p:new int[][]{{9,47},{9,50},{10,45},{10,46},{10,51},{10,52},{11,47},{11,50},{12,48},{12,49}})block(c,PRISMARINE_BRICKS,x,p[0],p[1]);}}
            for(int i=0;i<3;i++)box(c,PRISMARINE,8+i,5+i,54,49-i,5+i,54);box(c,PRISMARINE_BRICKS,11,8,54,46,8,54);box(c,PRISMARINE,14,8,44,43,8,53);}
    }

    private static void upperWall(Context c){
        if(localIntersects(c,14,21,20,43)){box(c,PRISMARINE,14,0,21,20,0,43);water(c,14,1,22,20,14,43);box(c,PRISMARINE,18,12,22,20,12,39);box(c,PRISMARINE_BRICKS,18,12,21,20,12,21);
            for(int i=0;i<4;i++)box(c,PRISMARINE_BRICKS,14+i,9+i,21,14+i,9+i,43-i);for(int z=23;z<=39;z+=3)block(c,PRISMARINE_BRICKS,19,13,z);}
        if(localIntersects(c,37,21,43,43)){box(c,PRISMARINE,37,0,21,43,0,43);water(c,37,1,22,43,14,43);box(c,PRISMARINE,37,12,22,39,12,39);box(c,PRISMARINE_BRICKS,37,12,21,39,12,21);
            for(int i=0;i<4;i++)box(c,PRISMARINE_BRICKS,43-i,9+i,21,43-i,9+i,43-i);for(int z=23;z<=39;z+=3)block(c,PRISMARINE_BRICKS,38,13,z);}
        if(localIntersects(c,15,37,42,43)){box(c,PRISMARINE,21,0,37,36,0,43);water(c,21,1,37,36,14,43);box(c,PRISMARINE,21,12,37,36,12,39);
            for(int i=0;i<4;i++)box(c,PRISMARINE_BRICKS,15+i,9+i,43-i,42-i,9+i,43-i);for(int x=21;x<=36;x+=3)block(c,PRISMARINE_BRICKS,x,13,38);}
    }
}
