package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.LegacyRand;
import com.gameexpert.terrain.mc.feature.Mc263StructureIndexReceipt;
import com.gameexpert.terrain.mc.ore.Mc263WorldgenRandomSource;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampCatalog.ElementKind;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkStarts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.PiecePayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Projection;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.StartEntry;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import com.gameexpert.terrain.mc.structure.Mc263StructureSetStartPlanner.BlockPos;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator.AttemptContext;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pinned Minecraft Java 26.3-snapshot-7 Abandoned Camp placement/start facts. */
public final class Mc263AbandonedCampStartGenerator {
    public static final String STRUCTURE_SET_KEY = "minecraft:abandoned_camp";
    public static final int SPACING = 34;
    public static final int SEPARATION = 8;
    public static final int SALT = 91_231_127;
    public static final String SPREAD_TYPE = "LINEAR";
    public static final int SIZE = 2;
    public static final int MAX_HORIZONTAL_DISTANCE = 80;
    public static final int MAX_VERTICAL_DISTANCE = 80;
    public static final String HEIGHTMAP = "WORLD_SURFACE_WG";
    public static final String TERRAIN_ADAPTATION = "BEARD_THIN";

    /** Persisted piece id for every jigsaw piece in the family. */
    public static final String PIECE_TYPE = "minecraft:jigsaw";

    private static final int TAG_END = 0;
    private static final int TAG_INT = 3;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;
    private static final long LARGE_FEATURE_X = 341_873_128_712L;
    private static final long LARGE_FEATURE_Z = 132_897_987_541L;
    private static final String PREFIX = "minecraft:abandoned_camp_";
    private static final List<StructureFact> FACTS = buildFacts();
    private static final Map<String, StructureFact> BY_KEY = index(FACTS);

    private Mc263AbandonedCampStartGenerator() {}

    public static List<StructureFact> structureFacts() {
        return FACTS;
    }

    public static StructureFact require(String structureKey) {
        StructureFact fact = BY_KEY.get(structureKey);
        if (fact == null) {
            throw new IllegalArgumentException("unknown pinned Abandoned Camp structure: "
                    + structureKey);
        }
        return fact;
    }

    /** Exact RandomSpreadStructurePlacement candidate test for the pinned Camp placement. */
    public static boolean isPlacementChunk(long worldSeed, int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, SPACING);
        int regionZ = Math.floorDiv(chunkZ, SPACING);
        LegacyRand random = new LegacyRand((long) regionX * LARGE_FEATURE_X
                + (long) regionZ * LARGE_FEATURE_Z + worldSeed + SALT);
        int bound = SPACING - SEPARATION;
        int candidateX = Math.addExact(Math.multiplyExact(regionX, SPACING), random.nextInt(bound));
        int candidateZ = Math.addExact(Math.multiplyExact(regionZ, SPACING), random.nextInt(bound));
        return candidateX == chunkX && candidateZ == chunkZ;
    }

    /** Exact candidate for the random-spread region containing the requested chunk. */
    public static ChunkPos placementCandidate(long worldSeed, int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, SPACING);
        int regionZ = Math.floorDiv(chunkZ, SPACING);
        LegacyRand random = new LegacyRand((long) regionX * LARGE_FEATURE_X
                + (long) regionZ * LARGE_FEATURE_Z + worldSeed + SALT);
        int bound = SPACING - SEPARATION;
        return new ChunkPos(Math.addExact(Math.multiplyExact(regionX, SPACING), random.nextInt(bound)),
                Math.addExact(Math.multiplyExact(regionZ, SPACING), random.nextInt(bound)));
    }

    /**
     * Official generation RNG boundary: WorldgenRandom(LegacyRandomSource) followed by
     * setLargeFeatureSeed(worldSeed, chunkX, chunkZ).
     */
    public static TraceRandom generationRandom(long worldSeed, int chunkX, int chunkZ) {
        TraceRandom random = new TraceRandom(0L);
        random.setSeed(worldSeed);
        long xScale = random.nextLong();
        long zScale = random.nextLong();
        random.setSeed((long) chunkX * xScale ^ (long) chunkZ * zScale ^ worldSeed);
        return random;
    }

    /**
     * Single carrier-start origin for every one of the 18 pinned Abandoned Camp family members.
     * The concrete member is selected only by {@link AttemptContext#structureKey()}; there is no
     * per-biome generator. Height capability is the producer's WORLD_SURFACE_WG contract; the
     * biome gate is pinned to the generation stub position, which is exactly where the
     * {@code abandoned-camp-start-graph-v1} receipt records the official
     * {@code Structure.isValidBiome} {@code BiomeSource.getNoiseBiome} query
     * ({@code lawfulSelection.biomeValidityQueries}, whose quart position equals
     * {@code QuartPos.fromBlock(rootSample.stubPosition)} on every probe).
     */
    public interface WorldAccess extends Mc263AbandonedCampProducer.HeightAccess {
        boolean supportsValidBiomeTest();

        /** {@code Structure.isValidBiome} at the recorded generation-stub query position. */
        boolean isValidBiome(int blockX, int blockY, int blockZ);
    }

    /** Immutable output; an invalid attempt retains neither a start graph nor a carrier start. */
    public static final class GenerationResult {
        private final boolean valid;
        private final Mc263AbandonedCampProducer.Start start;
        private final ChunkStarts startChunk;

        private GenerationResult(boolean valid, Mc263AbandonedCampProducer.Start start,
                ChunkStarts startChunk) {
            if (valid != (startChunk != null)) {
                throw new IllegalArgumentException("valid Abandoned Camp result/start mismatch");
            }
            this.valid = valid;
            this.start = start;
            this.startChunk = startChunk;
        }

        public boolean valid() { return valid; }
        public Mc263AbandonedCampProducer.Start start() { return start; }
        public ChunkStarts startChunk() { return startChunk; }

        static GenerationResult invalid(Mc263AbandonedCampProducer.Start start) {
            return new GenerationResult(false, start, null);
        }
    }

    private static final class ProducerHolder {
        private static final Mc263AbandonedCampProducer INSTANCE =
                Mc263AbandonedCampProducer.pinned();
    }

    /**
     * Runs the pinned jigsaw start for the requested family member and projects its accepted piece
     * order into one strict STR263C1 valid start whose piece payloads are exact binary NBT.
     * Vanilla draws the start rotation and start element before the biome gate, so the gate is
     * evaluated after the start graph is produced; an invalid biome publishes no start.
     */
    public static GenerationResult generate(AttemptContext context, WorldAccess world) {
        Objects.requireNonNull(context, "Abandoned Camp attempt context");
        Objects.requireNonNull(world, "Abandoned Camp start world");
        validateAttempt(context);
        if (!world.supportsWorldSurfaceHeight() || !world.supportsValidBiomeTest()) {
            throw new UnsupportedOperationException(
                    "complete Abandoned Camp start world capabilities are required");
        }

        Mc263AbandonedCampProducer.Start start = ProducerHolder.INSTANCE.generate(
                context.structureKey(), context.worldSeed(), context.chunkX(), context.chunkZ(),
                world);
        if (start.empty()) return GenerationResult.invalid(start);
        Mc263AbandonedCampCatalog.Vec query = start.stubPosition();
        if (!world.isValidBiome(query.x(), query.y(), query.z())) {
            return GenerationResult.invalid(start);
        }

        ValidStart valid = new ValidStart(startKey(context.structureKey(), context.chunkX(),
                context.chunkZ()), context.chunkX(), context.chunkZ(), context.priorReferences(),
                box(start.aggregateBoundingBox()), carrierPieces(start));
        ChunkStarts starts = new ChunkStarts(context.chunkX(), context.chunkZ(),
                List.of(new StartEntry(context.structureKey(), valid)));
        return new GenerationResult(true, start, starts);
    }

    /** Validates an unplaced persisted start against exact current-version generator output. */
    public static void validatePersisted(ValidStart persisted,
            Mc263AbandonedCampProducer.Start start) {
        Objects.requireNonNull(persisted, "persisted Abandoned Camp start");
        Objects.requireNonNull(start, "Abandoned Camp start graph");
        if (start.empty()) throw new IllegalArgumentException("empty Abandoned Camp start graph");
        if (persisted.originChunkX() != start.chunkX() || persisted.originChunkZ() != start.chunkZ()
                || !persisted.startKey().equals(startKey(start.structureKey(), start.chunkX(),
                        start.chunkZ()))
                || !persisted.adjustedBoundingBox().equals(box(start.aggregateBoundingBox()))
                || persisted.orderedPieces().size() != start.piecesInAcceptedOrder().size()) {
            throw new IllegalArgumentException("noncanonical Abandoned Camp persisted start");
        }
        List<Mc263StructureCarrier.Piece> expected = carrierPieces(start);
        for (int index = 0; index < expected.size(); index++) {
            if (!samePiece(persisted.orderedPieces().get(index), expected.get(index))) {
                throw new IllegalArgumentException(
                        "noncanonical Abandoned Camp persisted piece at " + index);
            }
        }
    }

    /** Exact binary-NBT value persisted for one accepted Abandoned Camp piece. */
    public static byte[] canonicalPieceNbt(Mc263AbandonedCampProducer.Start start, int pieceIndex) {
        Objects.requireNonNull(start, "Abandoned Camp start graph");
        if (pieceIndex < 0 || pieceIndex >= start.piecesInAcceptedOrder().size()) {
            throw new IllegalArgumentException("Abandoned Camp piece index outside start");
        }
        Mc263AbandonedCampProducer.Piece piece = start.piecesInAcceptedOrder().get(pieceIndex);
        Mc263AbandonedCampProducer.Box source = piece.boundingBox();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(512);
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(TAG_COMPOUND); out.writeUTF("");
                intArray(out, "BB", source.minX(), source.minY(), source.minZ(),
                        source.maxX(), source.maxY(), source.maxZ());
                integer(out, "PosZ", piece.position().z());
                integer(out, "PosX", piece.position().x());
                poolElement(out, piece);
                integer(out, "PosY", piece.position().y());
                string(out, "rotation", piece.rotation().name());
                string(out, "id", PIECE_TYPE);
                integer(out, "GD", 0);
                integer(out, "O", -1);
                integer(out, "ground_level_delta", piece.groundLevelDelta());
                junctions(out, piece.junctionsInAcceptedOrder());
                out.writeByte(TAG_END);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory Abandoned Camp piece NBT failed", exception);
        }
    }

    private static List<Mc263StructureCarrier.Piece> carrierPieces(
            Mc263AbandonedCampProducer.Start start) {
        List<Mc263AbandonedCampProducer.Piece> source = start.piecesInAcceptedOrder();
        ArrayList<Mc263StructureCarrier.Piece> pieces = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            Mc263AbandonedCampProducer.Piece piece = source.get(index);
            ArrayList<Mc263StructureCarrier.Junction> junctions =
                    new ArrayList<>(piece.junctionsInAcceptedOrder().size());
            for (Mc263AbandonedCampProducer.Junction junction : piece.junctionsInAcceptedOrder()) {
                junctions.add(new Mc263StructureCarrier.Junction(junction.sourceX(),
                        junction.sourceGroundY(), junction.sourceZ(), junction.deltaY(),
                        projection(junction.destinationProjection())));
            }
            pieces.add(new Mc263StructureCarrier.Piece(PIECE_TYPE, box(piece.boundingBox()), true,
                    projection(piece.projection()), piece.groundLevelDelta(), junctions,
                    new PiecePayload(canonicalPieceNbt(start, index))));
        }
        return List.copyOf(pieces);
    }

    private static boolean samePiece(Mc263StructureCarrier.Piece actual,
            Mc263StructureCarrier.Piece expected) {
        return actual.pieceType().equals(expected.pieceType())
                && actual.boundingBox().equals(expected.boundingBox())
                && actual.poolElement() == expected.poolElement()
                && actual.projection() == expected.projection()
                && actual.groundLevelDelta() == expected.groundLevelDelta()
                && actual.junctions().equals(expected.junctions())
                && Arrays.equals(actual.persistedPayload().binaryNbtCompound(),
                        expected.persistedPayload().binaryNbtCompound());
    }

    private static void validateAttempt(AttemptContext context) {
        BlockPos locate = context.locatePos();
        if (!STRUCTURE_SET_KEY.equals(context.setKey()) || !BY_KEY.containsKey(context.structureKey())
                || context.attemptIndex() < 0
                || context.attemptIndex() >= Mc263AbandonedCampCatalog.STRUCTURE_COUNT
                || context.weight() != 1 || context.priorReferences() < 0 || locate == null
                || locate.x() != Math.multiplyExact(context.chunkX(), 16) || locate.y() != 0
                || locate.z() != Math.multiplyExact(context.chunkZ(), 16)) {
            throw new IllegalArgumentException("Abandoned Camp attempt is not pinned 26.3");
        }
    }

    private static String startKey(String structureKey, int chunkX, int chunkZ) {
        return structureKey + "@" + chunkX + "," + chunkZ;
    }

    private static Mc263StructureCarrier.BoundingBox box(Mc263AbandonedCampProducer.Box source) {
        return new Mc263StructureCarrier.BoundingBox(source.minX(), source.minY(), source.minZ(),
                source.maxX(), source.maxY(), source.maxZ());
    }

    private static Projection projection(String value) {
        if (Mc263AbandonedCampCatalog.RIGID_PROJECTION.equals(value)) return Projection.RIGID;
        throw new IllegalArgumentException("unknown Abandoned Camp projection: " + value);
    }

    private static void poolElement(DataOutputStream out,
            Mc263AbandonedCampProducer.Piece piece) throws IOException {
        out.writeByte(TAG_COMPOUND); out.writeUTF("pool_element");
        if (piece.kind() == ElementKind.TEMPLATE) {
            string(out, "location", piece.templateKey());
            out.writeByte(TAG_COMPOUND); out.writeUTF("processors");
            out.writeByte(TAG_LIST); out.writeUTF("processors");
            out.writeByte(TAG_END); out.writeInt(0);
            out.writeByte(TAG_END);
            string(out, "projection", piece.projection());
            string(out, "element_type", Mc263AbandonedCampCatalog.TEMPLATE_ELEMENT_TYPE);
        } else {
            string(out, "feature", piece.featureKey());
            string(out, "projection", piece.projection());
            string(out, "element_type", Mc263AbandonedCampCatalog.FEATURE_ELEMENT_TYPE);
        }
        out.writeByte(TAG_END);
    }

    private static void junctions(DataOutputStream out,
            List<Mc263AbandonedCampProducer.Junction> values) throws IOException {
        out.writeByte(TAG_LIST); out.writeUTF("junctions");
        out.writeByte(values.isEmpty() ? TAG_END : TAG_COMPOUND); out.writeInt(values.size());
        for (Mc263AbandonedCampProducer.Junction junction : values) {
            integer(out, "source_z", junction.sourceZ());
            integer(out, "source_x", junction.sourceX());
            integer(out, "delta_y", junction.deltaY());
            integer(out, "source_ground_y", junction.sourceGroundY());
            string(out, "dest_proj", junction.destinationProjection());
            out.writeByte(TAG_END);
        }
    }

    private static void intArray(DataOutputStream out, String key, int... values)
            throws IOException {
        out.writeByte(TAG_INT_ARRAY); out.writeUTF(key); out.writeInt(values.length);
        for (int value : values) out.writeInt(value);
    }

    private static void integer(DataOutputStream out, String key, int value) throws IOException {
        out.writeByte(TAG_INT); out.writeUTF(key); out.writeInt(value);
    }

    private static void string(DataOutputStream out, String key, String value) throws IOException {
        out.writeByte(TAG_STRING); out.writeUTF(key); out.writeUTF(value);
    }

    private static List<StructureFact> buildFacts() {
        List<StructureFact> result = new ArrayList<>();
        Mc263AbandonedCampCatalog.StructureSet set = Mc263AbandonedCampCatalog.pinned().structureSet();
        Map<String, Mc263StructureIndexReceipt.Entry> receipt = new LinkedHashMap<>();
        for (Mc263StructureIndexReceipt.Entry entry : Mc263StructureIndexReceipt.step(4)) {
            if (entry.key().startsWith(PREFIX)) receipt.put(entry.key(), entry);
        }
        for (String key : set.structureKeys()) {
            Mc263StructureIndexReceipt.Entry entry = receipt.get(key);
            if (entry == null) throw new ExceptionInInitializerError("missing Camp structure receipt: " + key);
            String biome = key.substring(PREFIX.length());
            result.add(new StructureFact(key, biome, entry.biomeMask(),
                    "minecraft:abandoned_camp/tent/" + biome, entry.index()));
        }
        if (result.size() != Mc263AbandonedCampCatalog.STRUCTURE_COUNT) {
            throw new ExceptionInInitializerError("pinned Abandoned Camp structure fact count");
        }
        if (!STRUCTURE_SET_KEY.equals(set.key()) || set.spacing() != SPACING
                || set.separation() != SEPARATION || set.salt() != SALT
                || !SPREAD_TYPE.equals(set.spreadType())
                || !set.structureKeys().equals(result.stream().map(StructureFact::structureKey).toList())) {
            throw new ExceptionInInitializerError("pinned Abandoned Camp placement fact drift");
        }
        return List.copyOf(result);
    }

    private static Map<String, StructureFact> index(List<StructureFact> facts) {
        Map<String, StructureFact> result = new LinkedHashMap<>();
        for (StructureFact fact : facts) {
            if (result.put(fact.structureKey(), fact) != null) {
                throw new ExceptionInInitializerError("duplicate Abandoned Camp structure fact");
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    public record StructureFact(String structureKey, String biomeKey, long possibleBiomeMask,
            String startPool, int stepLocalIndex) {}

    public record ChunkPos(int x, int z) {}

    /** Package-visible traced legacy RNG shared by the specialized producer and tests. */
    public static final class TraceRandom implements Mc263WorldgenRandomSource {
        private static final long MULTIPLIER = 0x5DEECE66DL;
        private static final long ADDEND = 0xBL;
        private static final long MASK = (1L << 48) - 1L;
        private long state;
        private int count;
        private final List<Draw> draws = new ArrayList<>();

        TraceRandom(long seed) {
            setSeed(seed);
            draws.clear();
            count = 0;
        }

        static TraceRandom fromState(long state, int count) {
            TraceRandom random = new TraceRandom(0L);
            random.state = state & MASK;
            random.count = count;
            random.draws.clear();
            return random;
        }

        void setSeed(long seed) {
            state = (seed ^ MULTIPLIER) & MASK;
            draws.add(Draw.setSeed(draws.size(), seed));
        }

        public int next(int bits) {
            state = (state * MULTIPLIER + ADDEND) & MASK;
            count++;
            int value = (int) (state >>> (48 - bits));
            draws.add(Draw.nextBits(draws.size(), bits, value, state));
            return value;
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("positive random bound required");
            if ((bound & -bound) == bound) return (int) ((bound * (long) next(31)) >> 31);
            int bits, value;
            do {
                bits = next(31);
                value = bits % bound;
            } while (bits - value + bound - 1 < 0);
            return value;
        }

        public int nextInt() {
            return next(32);
        }

        @Override
        public boolean nextBoolean() {
            return next(1) != 0;
        }

        @Override
        public float nextFloat() {
            return (float) next(24) * 0x1.0p-24f;
        }

        public long nextLong() {
            return ((long) next(32) << 32) + next(32);
        }

        long state48() { return state; }
        int count() { return count; }
        List<Draw> draws() { return List.copyOf(draws); }

        long[] continuation() {
            TraceRandom copy = fromState(state, count);
            long[] values = new long[8];
            for (int index = 0; index < values.length; index++) values[index] = copy.nextLong();
            return values;
        }
    }

    public record Draw(int ordinal, String operation, Long seedArgument, Integer bitsArgument,
            Integer result, Long state48After) {
        static Draw setSeed(int ordinal, long seed) {
            return new Draw(ordinal, "setSeed", seed, null, null, null);
        }
        static Draw nextBits(int ordinal, int bits, int result, long state) {
            return new Draw(ordinal, "nextBits", null, bits, result, state);
        }
    }
}
