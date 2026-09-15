package com.gameexpert.terrain.mc.structure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Dormant strict typed reader/model for the authenticated Minecraft 26.3 Trial Chambers
 * start-graph evidence.
 *
 * <p>This class is evidence only. It does not plan, place, settle, register, dispatch, or publish a
 * structure successor. The resource is authenticated byte-for-byte before any model is exposed,
 * and every persisted gzip/binary-NBT receipt is decoded, losslessly re-encoded, and cross-checked
 * against the accepted ordered piece graph.</p>
 */
final class Mc263TrialChambersStartGraph {
    static final String RESOURCE = "/mc263/trial-chambers-start-graph-v1.json";
    static final int RESOURCE_BYTES = 824_547;
    static final String RESOURCE_SHA256 =
            "a4c13582e3817975371b8bdb4ff69820738cd57f782867f729a07372b8c85b4d";
    static final int SCHEMA = 1;
    static final String JAVA_VERSION = "25.0.1+8-LTS";
    static final String SERVER_VERSION = "26.3 Snapshot 7";
    static final int PROBE_COUNT = 2;

    private static final String STRUCTURE_KEY = "minecraft:trial_chambers";
    private static final String TERRAIN_BOUNDARY =
            "official GenerationContext terrain projection queries in encounter order";
    private static final long RNG_MULTIPLIER = 0x5DEECE66DL;
    private static final long RNG_ADDEND = 0xBL;
    private static final long RNG_MASK = (1L << 48) - 1L;
    private static final int AGGREGATE_PADDING = 12;
    private static final int MAX_NBT_BINARY_BYTES = 262_144;
    private static final int MAX_NBT_NODES = 100_000;

    private static final int[] PIECE_COUNTS = {248, 239};
    private static final int[] EDGE_COUNTS = {247, 238};
    private static final int[] ALIAS_COUNTS = {18, 14};
    private static final int[] MAX_DEPTHS = {8, 9};
    private static final int[] OPERATION_COUNTS = {707_336, 850_413};
    private static final int[] WORLDGEN_COUNTS = {707_334, 850_411};
    private static final String[] OPERATION_TRANSCRIPT_SHA256 = {
            "08f1a3bc21a7fb48bd026f91ec6b9044704cd21d6e0c28abc163ab678028b17a",
            "c1214d55e3ac222bdecc1528681aa623a5e69abe4db00c5593bdccc8a6ed077f"};
    private static final String[] FINAL_STATE48 = {"98290436772463", "80152077725784"};
    private static final int[] START_NBT_LENGTHS = {139_239, 134_056};
    private static final String[] START_NBT_SHA256 = {
            "9d66e8bb586cbf39cca4dca2c4e89a58ddca9577d5996dcbb73599343efa700a",
            "baf22e997f942d87ab40b28a8bca9369da63fa396381755b6f067190b2ec902b"};
    private static final int[] START_GZIP_LENGTHS = {9_413, 9_250};
    private static final String[] START_GZIP_SHA256 = {
            "faa9b4e06592d2a21b9dfd12fcce845ed0db7bb3555d27db301b24e44ac4547e",
            "1aba100bb20ea1ade050d173263dc01015402db069b5e384ef4f8893767d1ba5"};

    private static final List<Request> REQUESTS = List.of(
            new Request(0, 0, "-9223372036854775808"),
            new Request(3, 1, "-9223372036854775808"));
    private static final List<RootSample> ROOTS = List.of(
            new RootSample(new Vec3i(0, -29, 0), "COUNTERCLOCKWISE_90", -28,
                    new Vec3i(0, -28, 0), "minecraft:trial_chambers/chamber/end",
                    new Vec3i(9, -28, -9), "minecraft:trial_chambers/corridor/end_1"),
            new RootSample(new Vec3i(48, -31, 16), "COUNTERCLOCKWISE_90", -30,
                    new Vec3i(48, -30, 16), "minecraft:trial_chambers/chamber/end",
                    new Vec3i(57, -30, 7), "minecraft:trial_chambers/corridor/end_2"));
    private static final List<Box> AGGREGATE_BOXES = List.of(
            new Box(-89, -50, -93, 59, 9, 42),
            new Box(-30, -65, -76, 107, 24, 57));
    private static final List<List<String>> CONTINUATIONS = List.of(
            List.of("-340912518801200325", "3517619913119870170", "-3381355429217999316",
                    "-8422223648017243556", "3141999197059207169", "-6370126918628383998",
                    "-3364467835658197911", "-7815074047427536280"),
            List.of("21504499683758913", "-7594474554135821277", "9214822122464619361",
                    "-5609875719139999594", "1192432720265547596", "7061574240039838294",
                    "-538678908218567109", "-3884236505569404003"));
    private static final List<List<AliasResolution>> ALIAS_RESOLUTIONS = List.of(
            List.of(
                    new AliasResolution("minecraft:trial_chambers/spawner/contents/melee",
                            "minecraft:trial_chambers/spawner/melee/spider"),
                    new AliasResolution("minecraft:trial_chambers/spawner/contents/ranged",
                            "minecraft:trial_chambers/spawner/ranged/poison_skeleton"),
                    new AliasResolution("minecraft:trial_chambers/spawner/contents/small_melee",
                            "minecraft:trial_chambers/spawner/small_melee/baby_zombie")),
            List.of(
                    new AliasResolution("minecraft:trial_chambers/spawner/contents/melee",
                            "minecraft:trial_chambers/spawner/melee/husk"),
                    new AliasResolution("minecraft:trial_chambers/spawner/contents/ranged",
                            "minecraft:trial_chambers/spawner/ranged/stray"),
                    new AliasResolution("minecraft:trial_chambers/spawner/contents/slow_ranged",
                            "minecraft:trial_chambers/spawner/slow_ranged/stray"),
                    new AliasResolution("minecraft:trial_chambers/spawner/contents/small_melee",
                            "minecraft:trial_chambers/spawner/small_melee/baby_zombie")));

    private static final Set<String> ROTATIONS = Set.of(
            "NONE", "CLOCKWISE_90", "CLOCKWISE_180", "COUNTERCLOCKWISE_90");
    private static final List<String> COPPER_PROCESSOR_ORDER = List.of(
            "net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor",
            "net.minecraft.world.level.levelgen.structure.templatesystem.ProtectedBlockProcessor");

    private Mc263TrialChambersStartGraph() {
        throw new AssertionError("no instances");
    }

    /**
     * Decodes the authenticated start-graph verification corpus from caller-supplied bytes.
     *
     * <p>The corpus is a test-only oracle: production consumes only the distilled closure pins of
     * {@link Mc263TrialChambersProductionAuthority}, so these bytes are never packaged as a main
     * resource nor parsed on the world-join path.</p>
     */
    static Corpus decodeAccepted(byte[] bytes) {
        return decodeAuthenticated(bytes);
    }

    private static Corpus decodeAuthenticated(byte[] bytes) {
        Objects.requireNonNull(bytes, "Trial Chambers start-graph bytes");
        require(bytes.length == RESOURCE_BYTES, "Trial Chambers start-graph payload byte-count drift");
        require(RESOURCE_SHA256.equals(sha256(bytes)), "Trial Chambers start-graph payload identity drift");
        return parseEvidence(bytes);
    }

    private static Corpus parseEvidence(byte[] bytes) {
        Objects.requireNonNull(bytes, "Trial Chambers start-graph bytes");
        require(bytes.length <= RESOURCE_BYTES + 4_096, "Trial Chambers start-graph parser bound exceeded");
        JsonReader in = new JsonReader(bytes);
        in.beginObject();
        in.field("javaVersion", 0); String javaVersion = in.readString();
        in.field("probes", 1); List<Probe> probes = readProbes(in);
        in.field("schema", 2); int schema = in.readInt();
        in.field("serverVersion", 3); String serverVersion = in.readString();
        in.endObject();
        in.finish();
        Corpus corpus = new Corpus(javaVersion, probes, schema, serverVersion);
        validateCorpus(corpus);
        return corpus;
    }

    private static List<Probe> readProbes(JsonReader in) {
        in.beginArray();
        ArrayList<Probe> probes = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 4, "too many Trial Chambers start-graph probes");
            probes.add(readProbe(in));
        }
        in.endArray();
        return List.copyOf(probes);
    }

    private static Probe readProbe(JsonReader in) {
        in.beginObject();
        in.field("acceptedConnectorEdges", 0); List<ConnectorEdge> edges = readEdges(in);
        in.field("aggregateBoundingBox", 1); Box aggregate = readBox(in);
        in.field("generationRng", 2); GenerationRng rng = readGenerationRng(in);
        in.field("persistedNbt", 3); PersistedNbt persistedNbt = readPersistedNbt(in);
        in.field("piecesInAcceptedOrder", 4); List<Piece> pieces = readPieces(in);
        in.field("request", 5); Request request = readRequest(in);
        in.field("rootSample", 6); RootSample root = readRootSample(in);
        in.field("terrainProjectionQueries", 7); TerrainProjectionQueries terrain = readTerrain(in);
        in.endObject();
        return new Probe(edges, aggregate, rng, persistedNbt, pieces, request, root, terrain);
    }

    private static List<ConnectorEdge> readEdges(JsonReader in) {
        in.beginArray();
        ArrayList<ConnectorEdge> rows = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 512, "too many Trial Chambers connector edges");
            in.beginObject();
            in.field("officialCanAttach", 0); boolean officialCanAttach = in.readBoolean();
            in.field("placementPriority", 1); int placementPriority = in.readInt();
            in.field("resolvedAlias", 2); String resolvedAlias = in.readString();
            in.field("selectedPool", 3); String selectedPool = in.readString();
            in.field("selectionPriority", 4); int selectionPriority = in.readInt();
            in.field("sourceConnectorOrdinal", 5); int sourceConnectorOrdinal = in.readInt();
            in.field("sourceName", 6); String sourceName = in.readString();
            in.field("sourcePiece", 7); int sourcePiece = in.readInt();
            in.field("sourcePosition", 8); Vec3i sourcePosition = readVec3i(in);
            in.field("sourceTarget", 9); String sourceTarget = in.readString();
            in.field("targetConnectorOrdinal", 10); int targetConnectorOrdinal = in.readInt();
            in.field("targetName", 11); String targetName = in.readString();
            in.field("targetPiece", 12); int targetPiece = in.readInt();
            in.field("targetPosition", 13); Vec3i targetPosition = readVec3i(in);
            in.field("targetTarget", 14); String targetTarget = in.readString();
            in.endObject();
            rows.add(new ConnectorEdge(officialCanAttach, placementPriority, resolvedAlias,
                    selectedPool, selectionPriority, sourceConnectorOrdinal, sourceName,
                    sourcePiece, sourcePosition, sourceTarget, targetConnectorOrdinal,
                    targetName, targetPiece, targetPosition, targetTarget));
        }
        in.endArray();
        return List.copyOf(rows);
    }

    private static GenerationRng readGenerationRng(JsonReader in) {
        in.beginObject();
        in.field("continuationNextLongI64", 0);
        List<String> continuation = readStrings(in, 16, "Trial Chambers RNG continuation");
        in.field("finalLegacy48State", 1); String finalLegacy48State = in.readString();
        in.field("operationCount", 2); int operationCount = in.readInt();
        in.field("operationTranscriptSha256", 3); String operationTranscriptSha256 = in.readString();
        in.field("worldgenCount", 4); int worldgenCount = in.readInt();
        in.endObject();
        return new GenerationRng(continuation, finalLegacy48State, operationCount,
                operationTranscriptSha256, worldgenCount);
    }

    private static PersistedNbt readPersistedNbt(JsonReader in) {
        in.beginObject();
        in.field("pieces", 0);
        in.beginArray();
        ArrayList<NbtReceipt> pieces = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 512, "too many persisted Trial Chambers piece NBT rows");
            pieces.add(readNbtReceipt(in));
        }
        in.endArray();
        in.field("structureStart", 1); NbtReceipt start = readNbtReceipt(in);
        in.endObject();
        return new PersistedNbt(pieces, start);
    }

    private static NbtReceipt readNbtReceipt(JsonReader in) {
        in.beginObject();
        in.field("binaryLength", 0); int binaryLength = in.readInt();
        in.field("binarySha256", 1); String binarySha256 = in.readString();
        in.field("gzipBase64", 2); String gzipBase64 = in.readString();
        in.field("gzipLength", 3); int gzipLength = in.readInt();
        in.field("gzipSha256", 4); String gzipSha256 = in.readString();
        in.endObject();
        return decodeNbtReceipt(binaryLength, binarySha256, gzipBase64, gzipLength, gzipSha256);
    }

    private static List<Piece> readPieces(JsonReader in) {
        in.beginArray();
        ArrayList<Piece> pieces = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 512, "too many Trial Chambers start-graph pieces");
            in.beginObject();
            in.field("boundingBox", 0); Box boundingBox = readBox(in);
            in.field("depth", 1); int depth = in.readInt();
            in.field("groundLevelDelta", 2); int groundLevelDelta = in.readInt();
            in.field("junctionsInAcceptedOrder", 3); List<Junction> junctions = readJunctions(in);
            in.field("ordinal", 4); int ordinal = in.readInt();
            in.field("origin", 5); Vec3i origin = readVec3i(in);
            in.field("processorOrder", 6);
            List<String> processorOrder = readStrings(in, 8, "Trial Chambers processor order");
            in.field("processorRegistryKey", 7); String processorRegistryKey = in.readString();
            in.field("projection", 8); String projection = in.readString();
            in.field("rotation", 9); String rotation = in.readString();
            in.field("template", 10); String template = in.readString();
            in.endObject();
            pieces.add(new Piece(boundingBox, depth, groundLevelDelta, junctions, ordinal, origin,
                    processorOrder, processorRegistryKey, projection, rotation, template));
        }
        in.endArray();
        return List.copyOf(pieces);
    }

    private static List<Junction> readJunctions(JsonReader in) {
        in.beginArray();
        ArrayList<Junction> rows = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 64, "too many Trial Chambers piece junctions");
            in.beginObject();
            in.field("deltaY", 0); int deltaY = in.readInt();
            in.field("destinationProjection", 1); String destinationProjection = in.readString();
            in.field("sourceGroundY", 2); int sourceGroundY = in.readInt();
            in.field("sourceX", 3); int sourceX = in.readInt();
            in.field("sourceZ", 4); int sourceZ = in.readInt();
            in.endObject();
            rows.add(new Junction(deltaY, destinationProjection, sourceGroundY, sourceX, sourceZ));
        }
        in.endArray();
        return List.copyOf(rows);
    }

    private static Request readRequest(JsonReader in) {
        in.beginObject();
        in.field("chunkX", 0); int chunkX = in.readInt();
        in.field("chunkZ", 1); int chunkZ = in.readInt();
        in.field("worldSeedI64", 2); String worldSeedI64 = in.readString();
        in.endObject();
        return new Request(chunkX, chunkZ, worldSeedI64);
    }

    private static RootSample readRootSample(JsonReader in) {
        in.beginObject();
        in.field("origin", 0); Vec3i origin = readVec3i(in);
        in.field("rotation", 1); String rotation = in.readString();
        in.field("sampledStartHeight", 2); int sampledStartHeight = in.readInt();
        in.field("sampledStartPosition", 3); Vec3i sampledStartPosition = readVec3i(in);
        in.field("startPool", 4); String startPool = in.readString();
        in.field("stubPosition", 5); Vec3i stubPosition = readVec3i(in);
        in.field("template", 6); String template = in.readString();
        in.endObject();
        return new RootSample(origin, rotation, sampledStartHeight, sampledStartPosition,
                startPool, stubPosition, template);
    }

    private static TerrainProjectionQueries readTerrain(JsonReader in) {
        in.beginObject();
        in.field("boundary", 0); String boundary = in.readString();
        in.field("queryCount", 1); int queryCount = in.readInt();
        in.field("queryOrder", 2);
        in.beginArray();
        ArrayList<ProjectionQuery> rows = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 256, "too many Trial Chambers terrain projection queries");
            in.beginObject();
            in.field("heightmap", 0); String heightmap = in.readString();
            in.field("operation", 1); String operation = in.readString();
            in.field("ordinal", 2); int ordinal = in.readInt();
            in.field("result", 3); int result = in.readInt();
            in.field("x", 4); int x = in.readInt();
            in.field("z", 5); int z = in.readInt();
            in.endObject();
            rows.add(new ProjectionQuery(heightmap, operation, ordinal, result, x, z));
        }
        in.endArray();
        in.endObject();
        return new TerrainProjectionQueries(boundary, queryCount, rows);
    }

    private static Vec3i readVec3i(JsonReader in) {
        in.beginArray();
        require(in.nextArrayValue(0), "Trial Chambers Vec3i is empty"); int x = in.readInt();
        require(in.nextArrayValue(1), "Trial Chambers Vec3i misses y"); int y = in.readInt();
        require(in.nextArrayValue(2), "Trial Chambers Vec3i misses z"); int z = in.readInt();
        require(!in.nextArrayValue(3), "Trial Chambers Vec3i has extra coordinate");
        in.endArray();
        return new Vec3i(x, y, z);
    }

    private static Box readBox(JsonReader in) {
        in.beginArray();
        int[] values = new int[6];
        for (int index = 0; index < values.length; index++) {
            require(in.nextArrayValue(index), "Trial Chambers bounding box is truncated");
            values[index] = in.readInt();
        }
        require(!in.nextArrayValue(6), "Trial Chambers bounding box has extra coordinate");
        in.endArray();
        return new Box(values[0], values[1], values[2], values[3], values[4], values[5]);
    }

    private static List<String> readStrings(JsonReader in, int maximum, String label) {
        in.beginArray();
        ArrayList<String> values = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < maximum, "too many " + label);
            values.add(in.readString());
        }
        in.endArray();
        return List.copyOf(values);
    }

    private static void validateCorpus(Corpus corpus) {
        require(corpus.javaVersion().equals(JAVA_VERSION), "Trial Chambers Java version drift");
        require(corpus.schema() == SCHEMA, "Trial Chambers start-graph schema drift");
        require(corpus.serverVersion().equals(SERVER_VERSION), "Trial Chambers server version drift");
        require(corpus.probes().size() == PROBE_COUNT, "Trial Chambers probe cardinality drift");
        for (int index = 0; index < corpus.probes().size(); index++) {
            validateProbe(index, corpus.probes().get(index));
        }
    }

    private static void validateProbe(int probeIndex, Probe probe) {
        require(probe.request().equals(REQUESTS.get(probeIndex)),
                "Trial Chambers probe request/order drift at " + probeIndex);
        require(probe.rootSample().equals(ROOTS.get(probeIndex)),
                "Trial Chambers exact root sample drift at " + probeIndex);
        require(probe.aggregateBoundingBox().equals(AGGREGATE_BOXES.get(probeIndex)),
                "Trial Chambers aggregate bounding-box drift at " + probeIndex);
        require(probe.piecesInAcceptedOrder().size() == PIECE_COUNTS[probeIndex],
                "Trial Chambers piece cardinality drift at " + probeIndex);
        require(probe.acceptedConnectorEdges().size() == EDGE_COUNTS[probeIndex],
                "Trial Chambers edge cardinality drift at " + probeIndex);
        validatePieces(probeIndex, probe.piecesInAcceptedOrder(), probe.aggregateBoundingBox(),
                probe.rootSample());
        validateEdges(probeIndex, probe.piecesInAcceptedOrder(), probe.acceptedConnectorEdges());
        validateTerrain(probe.terrainProjectionQueries());
        validateRng(probeIndex, probe.generationRng());
        validatePersistedNbt(probeIndex, probe.request(), probe.piecesInAcceptedOrder(),
                probe.persistedNbt());
    }

    private static void validatePieces(int probeIndex, List<Piece> pieces, Box aggregate,
            RootSample rootSample) {
        require(!pieces.isEmpty(), "Trial Chambers ordered piece graph is empty");
        Mc263TrialChambersGrammar.Evidence grammar = Mc263TrialChambersGrammar.pinned().evidence();
        Box union = null;
        int maxDepth = 0;
        for (int index = 0; index < pieces.size(); index++) {
            Piece piece = pieces.get(index);
            require(piece.ordinal() == index, "Trial Chambers piece encounter-order drift at " + index);
            require(piece.depth() >= 0, "Trial Chambers negative piece depth at " + index);
            maxDepth = Math.max(maxDepth, piece.depth());
            require(piece.boundingBox().valid(), "Trial Chambers invalid piece bounding box at " + index);
            require(piece.boundingBox().contains(piece.origin()),
                    "Trial Chambers piece origin escaped bounding box at " + index);
            require(piece.projection().equals("rigid"),
                    "unknown Trial Chambers piece projection at " + index + ": " + piece.projection());
            require(ROTATIONS.contains(piece.rotation()),
                    "unknown Trial Chambers rotation at " + index + ": " + piece.rotation());
            grammar.requireTemplate(piece.template());
            Mc263TrialChambersGrammar.ProcessorListIdentity identity =
                    Mc263TrialChambersGrammar.ProcessorListIdentity.from(piece.processorRegistryKey());
            List<String> expectedProcessors = switch (identity) {
                case INLINE -> List.of();
                case COPPER_BULB_DEGRADATION -> COPPER_PROCESSOR_ORDER;
            };
            require(piece.processorOrder().equals(expectedProcessors),
                    "Trial Chambers processor order drift at piece " + index);
            for (Junction junction : piece.junctionsInAcceptedOrder()) {
                require(junction.destinationProjection().equals("rigid"),
                        "unknown Trial Chambers junction projection at piece " + index);
            }
            union = union == null ? piece.boundingBox() : union.union(piece.boundingBox());
        }
        Piece root = pieces.getFirst();
        require(root.depth() == 0 && root.origin().equals(rootSample.origin())
                        && root.rotation().equals(rootSample.rotation())
                        && root.template().equals(rootSample.template()),
                "Trial Chambers root piece/root-sample mismatch");
        require(maxDepth == MAX_DEPTHS[probeIndex], "Trial Chambers maximum depth drift");
        require(aggregate.equals(union.inflate(AGGREGATE_PADDING)),
                "Trial Chambers aggregate/piece coordinate closure drift");
    }

    private static void validateEdges(int probeIndex, List<Piece> pieces,
            List<ConnectorEdge> edges) {
        Mc263TrialChambersGrammar.Evidence grammar = Mc263TrialChambersGrammar.pinned().evidence();
        int aliasCount = 0;
        int[] parent = new int[pieces.size()];
        parent[0] = -1;
        for (int index = 0; index < edges.size(); index++) {
            ConnectorEdge edge = edges.get(index);
            int target = index + 1;
            require(edge.officialCanAttach(), "Trial Chambers officialCanAttach drift at edge " + index);
            require(edge.targetPiece() == target, "Trial Chambers target edge order drift at " + index);
            require(edge.sourcePiece() >= 0 && edge.sourcePiece() < target,
                    "Trial Chambers parent-before-child drift at edge " + index);
            parent[target] = edge.sourcePiece();
            Piece source = pieces.get(edge.sourcePiece());
            Piece child = pieces.get(target);
            require(child.depth() == source.depth() + 1,
                    "Trial Chambers edge/depth drift at edge " + index);
            require(edge.sourceConnectorOrdinal() >= 0
                            && edge.sourceConnectorOrdinal() < grammar.requireTemplate(source.template())
                                    .connectors().size(),
                    "Trial Chambers source connector ordinal drift at edge " + index);
            require(edge.targetConnectorOrdinal() >= 0
                            && edge.targetConnectorOrdinal() < grammar.requireTemplate(child.template())
                                    .connectors().size(),
                    "Trial Chambers target connector ordinal drift at edge " + index);
            require(edge.selectionPriority() >= 0 && edge.placementPriority() >= 0,
                    "Trial Chambers connector priority drift at edge " + index);
            require(edge.sourceTarget().equals(edge.targetName()),
                    "Trial Chambers source-target/target-name attachment drift at edge " + index);
            require(isIdentifier(edge.sourceName()) && isIdentifier(edge.sourceTarget())
                            && isIdentifier(edge.targetName()) && isIdentifier(edge.targetTarget()),
                    "Trial Chambers connector identifier drift at edge " + index);
            require(source.boundingBox().contains(edge.sourcePosition()),
                    "Trial Chambers source connector escaped source piece at edge " + index);
            require(child.boundingBox().contains(edge.targetPosition()),
                    "Trial Chambers target connector escaped child piece at edge " + index);
            require(!child.junctionsInAcceptedOrder().isEmpty(),
                    "Trial Chambers non-root piece has no parent junction at edge " + index);
            Junction targetParent = child.junctionsInAcceptedOrder().getFirst();
            require(targetParent.destinationProjection().equals(source.projection())
                            && targetParent.sourceX() == edge.sourcePosition().x()
                            && targetParent.sourceZ() == edge.sourcePosition().z(),
                    "Trial Chambers target-parent junction/edge drift at edge " + index);
            int matches = 0;
            for (Junction sourceJunction : source.junctionsInAcceptedOrder()) {
                if (sourceJunction.destinationProjection().equals(child.projection())
                        && sourceJunction.deltaY() == -targetParent.deltaY()
                        && sourceJunction.sourceX() == edge.targetPosition().x()
                        && sourceJunction.sourceZ() == edge.targetPosition().z()) {
                    matches++;
                }
            }
            require(matches == 1, "Trial Chambers source junction/edge reconstruction drift at edge " + index);
            validateAlias(probeIndex, edge, grammar);
            if (!edge.selectedPool().equals(edge.resolvedAlias())) aliasCount++;
        }
        require(edges.size() == pieces.size() - 1, "Trial Chambers graph edge/piece closure drift");
        require(aliasCount == ALIAS_COUNTS[probeIndex], "Trial Chambers alias encounter count drift");
        for (int child = 1; child < pieces.size(); child++) {
            int cursor = child;
            int steps = 0;
            while (cursor != 0) {
                require(cursor > 0 && cursor < pieces.size(),
                        "Trial Chambers graph root connectivity escaped piece range");
                cursor = parent[cursor];
                require(++steps <= pieces.size(), "Trial Chambers graph cycle detected");
            }
        }
    }

    private static void validateAlias(int probeIndex, ConnectorEdge edge,
            Mc263TrialChambersGrammar.Evidence grammar) {
        grammar.requirePool(edge.resolvedAlias());
        if (edge.selectedPool().equals(edge.resolvedAlias())) {
            grammar.requirePool(edge.selectedPool());
            return;
        }
        AliasResolution actual = new AliasResolution(edge.selectedPool(), edge.resolvedAlias());
        require(ALIAS_RESOLUTIONS.get(probeIndex).contains(actual),
                "Trial Chambers alias resolution drift: " + actual);
        boolean declared = false;
        for (Mc263TrialChambersGrammar.AliasBinding binding : grammar.aliasesInDeclaredOrder()) {
            if (binding instanceof Mc263TrialChambersGrammar.RandomAlias random
                    && random.alias().equals(edge.selectedPool())) {
                declared = random.targets().stream().anyMatch(value -> value.data().equals(edge.resolvedAlias()));
            } else if (binding instanceof Mc263TrialChambersGrammar.RandomGroupAlias group) {
                for (Mc263TrialChambersGrammar.AliasGroup choice : group.groups()) {
                    for (Mc263TrialChambersGrammar.DirectAlias direct : choice.data()) {
                        if (direct.alias().equals(edge.selectedPool())
                                && direct.target().equals(edge.resolvedAlias())) declared = true;
                    }
                }
            }
        }
        require(declared, "Trial Chambers alias pair is absent from accepted grammar");
    }

    private static void validateTerrain(TerrainProjectionQueries terrain) {
        require(terrain.boundary().equals(TERRAIN_BOUNDARY), "Trial Chambers terrain boundary drift");
        require(terrain.queryCount() == 0 && terrain.queryOrder().isEmpty(),
                "Trial Chambers terrain projection query order/result drift");
    }

    private static void validateRng(int probeIndex, GenerationRng rng) {
        require(rng.operationCount() == OPERATION_COUNTS[probeIndex],
                "Trial Chambers ordered RNG operation count drift");
        require(rng.worldgenCount() == WORLDGEN_COUNTS[probeIndex]
                        && rng.operationCount() == rng.worldgenCount() + 2,
                "Trial Chambers ordered RNG worldgen/count closure drift");
        require(rng.operationTranscriptSha256().equals(OPERATION_TRANSCRIPT_SHA256[probeIndex])
                        && isLowerHex64(rng.operationTranscriptSha256()),
                "Trial Chambers ordered RNG transcript digest drift");
        require(rng.finalLegacy48State().equals(FINAL_STATE48[probeIndex]),
                "Trial Chambers final Legacy48 state drift");
        long state = parseLong(rng.finalLegacy48State(), "Trial Chambers final Legacy48 state");
        require(state >= 0 && state <= RNG_MASK, "Trial Chambers final Legacy48 state out of range");
        require(rng.continuationNextLongI64().equals(CONTINUATIONS.get(probeIndex)),
                "Trial Chambers RNG continuation receipt drift");
        ArrayList<String> derived = new ArrayList<>();
        long continuationState = state;
        for (int index = 0; index < 8; index++) {
            continuationState = advanceLegacyState(continuationState);
            int high = (int) (continuationState >>> 16);
            continuationState = advanceLegacyState(continuationState);
            int low = (int) (continuationState >>> 16);
            derived.add(Long.toString(((long) high << 32) + (long) low));
        }
        require(derived.equals(rng.continuationNextLongI64()),
                "Trial Chambers RNG continuation/final-state closure drift");
    }

    private static long advanceLegacyState(long state) {
        return (state * RNG_MULTIPLIER + RNG_ADDEND) & RNG_MASK;
    }

    private static void validatePersistedNbt(int probeIndex, Request request, List<Piece> pieces,
            PersistedNbt persisted) {
        require(persisted.pieces().size() == pieces.size(),
                "Trial Chambers persisted piece NBT cardinality drift");
        for (int index = 0; index < pieces.size(); index++) {
            validatePieceNbt(pieces.get(index), persisted.pieces().get(index).orderedTree());
        }
        NbtReceipt start = persisted.structureStart();
        require(start.binaryLength() == START_NBT_LENGTHS[probeIndex]
                        && start.binarySha256().equals(START_NBT_SHA256[probeIndex])
                        && start.gzipLength() == START_GZIP_LENGTHS[probeIndex]
                        && start.gzipSha256().equals(START_GZIP_SHA256[probeIndex]),
                "Trial Chambers persisted StructureStart NBT receipt drift");
        validateStartNbt(request, persisted);
    }

    private static void validatePieceNbt(Piece piece, NbtCompound tree) {
        require(entryNames(tree).equals(List.of(
                        "BB", "PosZ", "PosX", "pool_element", "liquid_settings", "PosY",
                        "rotation", "id", "GD", "O", "ground_level_delta", "junctions")),
                "Trial Chambers persisted piece NBT key/order drift at " + piece.ordinal());
        requireIntArray(entry(tree, "BB"), piece.boundingBox().asList(), "piece BB");
        requireInt(entry(tree, "PosX"), piece.origin().x(), "piece PosX");
        requireInt(entry(tree, "PosY"), piece.origin().y(), "piece PosY");
        requireInt(entry(tree, "PosZ"), piece.origin().z(), "piece PosZ");
        requireString(entry(tree, "rotation"), piece.rotation(), "piece rotation");
        requireString(entry(tree, "id"), "minecraft:jigsaw", "piece id");
        requireString(entry(tree, "liquid_settings"), "ignore_waterlogging", "piece liquid_settings");
        requireInt(entry(tree, "GD"), 0, "piece GD");
        requireInt(entry(tree, "O"), -1, "piece O");
        requireInt(entry(tree, "ground_level_delta"), piece.groundLevelDelta(),
                "piece ground_level_delta");
        validatePoolElementNbt(piece, entry(tree, "pool_element"));
        validateJunctionNbt(piece, entry(tree, "junctions"));
    }

    private static void validatePoolElementNbt(Piece piece, NbtTag value) {
        NbtCompound pool = requireCompound(value, "Trial Chambers piece pool_element");
        require(entryNames(pool).equals(List.of("location", "processors", "projection", "element_type")),
                "Trial Chambers persisted pool-element key/order drift");
        requireString(entry(pool, "location"), piece.template(), "pool location");
        requireString(entry(pool, "projection"), piece.projection(), "pool projection");
        requireString(entry(pool, "element_type"), "minecraft:single_pool_element", "pool element_type");
        NbtTag processors = entry(pool, "processors");
        if (piece.processorRegistryKey().equals("inline")) {
            NbtCompound inline = requireCompound(processors, "Trial Chambers inline processors");
            require(entryNames(inline).equals(List.of("processors")),
                    "Trial Chambers inline processor NBT key/order drift");
            require(requireList(entry(inline, "processors"), "Trial Chambers inline processor list")
                            .values().isEmpty(),
                    "Trial Chambers inline processor list is not empty");
        } else {
            requireString(processors, piece.processorRegistryKey(), "registered processors");
        }
    }

    private static void validateJunctionNbt(Piece piece, NbtTag value) {
        NbtList list = requireList(value, "Trial Chambers piece junctions");
        require(list.values().size() == piece.junctionsInAcceptedOrder().size(),
                "Trial Chambers persisted junction cardinality drift at " + piece.ordinal());
        for (int index = 0; index < list.values().size(); index++) {
            NbtCompound row = requireCompound(list.values().get(index), "Trial Chambers piece junction");
            require(entryNames(row).equals(List.of(
                            "source_z", "source_x", "delta_y", "source_ground_y", "dest_proj")),
                    "Trial Chambers persisted junction key/order drift");
            Junction expected = piece.junctionsInAcceptedOrder().get(index);
            requireInt(entry(row, "source_z"), expected.sourceZ(), "junction source_z");
            requireInt(entry(row, "source_x"), expected.sourceX(), "junction source_x");
            requireInt(entry(row, "delta_y"), expected.deltaY(), "junction delta_y");
            requireInt(entry(row, "source_ground_y"), expected.sourceGroundY(),
                    "junction source_ground_y");
            requireString(entry(row, "dest_proj"), expected.destinationProjection(),
                    "junction dest_proj");
        }
    }

    private static void validateStartNbt(Request request, PersistedNbt persisted) {
        NbtCompound start = persisted.structureStart().orderedTree();
        require(entryNames(start).equals(List.of("references", "ChunkZ", "id", "Children", "ChunkX")),
                "Trial Chambers persisted start NBT key/order drift");
        requireInt(entry(start, "references"), 0, "start references");
        requireInt(entry(start, "ChunkX"), request.chunkX(), "start ChunkX");
        requireInt(entry(start, "ChunkZ"), request.chunkZ(), "start ChunkZ");
        requireString(entry(start, "id"), STRUCTURE_KEY, "start id");
        NbtList children = requireList(entry(start, "Children"), "Trial Chambers start Children");
        require(children.values().size() == persisted.pieces().size(),
                "Trial Chambers start Children cardinality drift");
        for (int index = 0; index < children.values().size(); index++) {
            require(children.values().get(index).equals(persisted.pieces().get(index).orderedTree()),
                    "Trial Chambers start-child/piece mismatch at " + index);
        }
    }

    private static NbtReceipt decodeNbtReceipt(int binaryLength, String binarySha256,
            String gzipBase64, int gzipLength, String gzipSha256) {
        require(binaryLength > 0 && binaryLength <= MAX_NBT_BINARY_BYTES,
                "Trial Chambers persisted NBT binary length outside bound");
        require(gzipLength > 0 && gzipLength <= MAX_NBT_BINARY_BYTES,
                "Trial Chambers persisted NBT gzip length outside bound");
        require(isLowerHex64(binarySha256) && isLowerHex64(gzipSha256),
                "Trial Chambers persisted NBT SHA-256 format drift");
        byte[] gzip;
        try {
            gzip = Base64.getDecoder().decode(gzipBase64);
        } catch (IllegalArgumentException error) {
            throw invalid("Trial Chambers persisted NBT base64 is malformed", error);
        }
        require(Base64.getEncoder().encodeToString(gzip).equals(gzipBase64),
                "Trial Chambers persisted NBT base64 is not canonical");
        require(gzip.length == gzipLength, "Trial Chambers persisted NBT gzip length drift");
        require(sha256(gzip).equals(gzipSha256), "Trial Chambers persisted NBT gzip SHA-256 drift");
        byte[] binary = gunzip(gzip, binaryLength);
        require(binary.length == binaryLength, "Trial Chambers persisted NBT binary length drift");
        require(sha256(binary).equals(binarySha256), "Trial Chambers persisted NBT binary SHA-256 drift");
        require(Arrays.equals(gzip(binary), gzip), "Trial Chambers persisted NBT gzip is not canonical");
        NbtCompound tree = readNbtRoot(binary);
        require(Arrays.equals(writeNbtRoot(tree), binary),
                "Trial Chambers persisted binary NBT is not canonical/lossless");
        return new NbtReceipt(binaryLength, binarySha256, gzipLength, gzipSha256, tree);
    }

    private static byte[] gunzip(byte[] gzip, int expectedLength) {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(gzip))) {
            byte[] binary = input.readNBytes(expectedLength + 1);
            require(binary.length == expectedLength, "Trial Chambers gzip expands to unexpected length");
            require(input.read() == -1, "Trial Chambers gzip expands beyond declared binary length");
            return binary;
        } catch (IOException error) {
            throw invalid("Trial Chambers persisted NBT gzip is malformed", error);
        }
    }

    private static byte[] gzip(byte[] binary) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream output = new GZIPOutputStream(bytes)) {
                output.write(binary);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static NbtCompound readNbtRoot(byte[] binary) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(binary))) {
            require(input.readUnsignedByte() == 10, "Trial Chambers persisted binary NBT root type drift");
            require(input.readUTF().isEmpty(), "Trial Chambers persisted binary NBT root name drift");
            NbtBudget budget = new NbtBudget();
            NbtTag root = readNbtPayload(input, 10, budget, 0);
            require(root instanceof NbtCompound, "Trial Chambers persisted NBT root is not compound");
            require(input.read() == -1, "trailing Trial Chambers persisted binary NBT data");
            return (NbtCompound) root;
        } catch (EOFException error) {
            throw invalid("truncated Trial Chambers persisted binary NBT", error);
        } catch (IOException error) {
            throw invalid("malformed Trial Chambers persisted binary NBT", error);
        }
    }

    private static NbtTag readNbtPayload(DataInputStream input, int type, NbtBudget budget, int depth)
            throws IOException {
        budget.consume(depth);
        return switch (type) {
            case 3 -> new NbtInt(input.readInt());
            case 8 -> new NbtString(input.readUTF());
            case 9 -> {
                int elementType = input.readUnsignedByte();
                int size = input.readInt();
                require(size >= 0 && size <= 4_096, "Trial Chambers persisted NBT LIST length outside bound");
                require(size == 0 || isSupportedNbtType(elementType),
                        "Trial Chambers persisted NBT LIST element type drift");
                if (size == 0) require(elementType == 0 || isSupportedNbtType(elementType),
                        "Trial Chambers empty persisted NBT LIST element type drift");
                ArrayList<NbtTag> values = new ArrayList<>();
                for (int index = 0; index < size; index++) {
                    values.add(readNbtPayload(input, elementType, budget, depth + 1));
                }
                yield new NbtList(elementType, values);
            }
            case 10 -> {
                ArrayList<NbtEntry> entries = new ArrayList<>();
                while (true) {
                    int childType = input.readUnsignedByte();
                    if (childType == 0) break;
                    require(isSupportedNbtType(childType),
                            "unsupported Trial Chambers persisted NBT child type: " + childType);
                    require(entries.size() < 256, "Trial Chambers persisted NBT compound entry bound exceeded");
                    String name = input.readUTF();
                    for (NbtEntry entry : entries) {
                        require(!entry.name().equals(name),
                                "duplicate Trial Chambers persisted NBT entry: " + name);
                    }
                    entries.add(new NbtEntry(name, readNbtPayload(input, childType, budget, depth + 1)));
                }
                yield new NbtCompound(entries);
            }
            case 11 -> {
                int length = input.readInt();
                require(length >= 0 && length <= 1_024,
                        "Trial Chambers persisted NBT INT[] length outside bound");
                ArrayList<Integer> values = new ArrayList<>();
                for (int index = 0; index < length; index++) values.add(input.readInt());
                yield new NbtIntArray(values);
            }
            default -> throw invalid("unsupported Trial Chambers persisted NBT type: " + type);
        };
    }

    private static boolean isSupportedNbtType(int type) {
        return type == 3 || type == 8 || type == 9 || type == 10 || type == 11;
    }

    private static byte[] writeNbtRoot(NbtCompound tree) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(10);
                output.writeUTF("");
                writeNbtPayload(output, tree);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void writeNbtPayload(DataOutputStream output, NbtTag tag) throws IOException {
        if (tag instanceof NbtInt value) {
            output.writeInt(value.value());
            return;
        }
        if (tag instanceof NbtString value) {
            output.writeUTF(value.value());
            return;
        }
        if (tag instanceof NbtList list) {
            output.writeByte(list.elementType());
            output.writeInt(list.values().size());
            for (NbtTag value : list.values()) {
                require(value.typeId() == list.elementType(),
                        "Trial Chambers persisted NBT LIST element-type/model drift");
                writeNbtPayload(output, value);
            }
            return;
        }
        if (tag instanceof NbtCompound compound) {
            for (NbtEntry entry : compound.entries()) {
                output.writeByte(entry.tag().typeId());
                output.writeUTF(entry.name());
                writeNbtPayload(output, entry.tag());
            }
            output.writeByte(0);
            return;
        }
        if (tag instanceof NbtIntArray array) {
            output.writeInt(array.values().size());
            for (int value : array.values()) output.writeInt(value);
            return;
        }
        throw invalid("unsupported Trial Chambers persisted NBT model");
    }

    private static List<String> entryNames(NbtCompound compound) {
        return compound.entries().stream().map(NbtEntry::name).toList();
    }

    private static NbtTag entry(NbtCompound compound, String name) {
        NbtTag result = null;
        for (NbtEntry entry : compound.entries()) {
            if (!entry.name().equals(name)) continue;
            require(result == null, "duplicate Trial Chambers persisted NBT entry: " + name);
            result = entry.tag();
        }
        require(result != null, "missing Trial Chambers persisted NBT entry: " + name);
        return result;
    }

    private static NbtCompound requireCompound(NbtTag value, String label) {
        require(value instanceof NbtCompound, label + " is not COMPOUND");
        return (NbtCompound) value;
    }

    private static NbtList requireList(NbtTag value, String label) {
        require(value instanceof NbtList, label + " is not LIST");
        return (NbtList) value;
    }

    private static void requireInt(NbtTag value, int expected, String label) {
        require(value instanceof NbtInt integer && integer.value() == expected,
                "Trial Chambers persisted " + label + " drift");
    }

    private static void requireString(NbtTag value, String expected, String label) {
        require(value instanceof NbtString string && string.value().equals(expected),
                "Trial Chambers persisted " + label + " drift");
    }

    private static void requireIntArray(NbtTag value, List<Integer> expected, String label) {
        require(value instanceof NbtIntArray array && array.values().equals(expected),
                "Trial Chambers persisted " + label + " drift");
    }

    private static boolean isIdentifier(String value) {
        if (!value.startsWith("minecraft:") || value.length() == "minecraft:".length()) return false;
        for (int index = "minecraft:".length(); index < value.length(); index++) {
            char current = value.charAt(index);
            if (!((current >= 'a' && current <= 'z') || (current >= '0' && current <= '9')
                    || current == '_' || current == '.' || current == '/' || current == '-')) return false;
        }
        return true;
    }

    private static boolean isLowerHex64(String value) {
        if (value.length() != 64) return false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!((current >= '0' && current <= '9') || (current >= 'a' && current <= 'f'))) return false;
        }
        return true;
    }

    private static long parseLong(String value, String label) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw invalid(label + " is not signed i64", error);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static IllegalArgumentException invalid(String message, Throwable cause) {
        return new IllegalArgumentException(message, cause);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw invalid(message);
    }

    record Corpus(String javaVersion, List<Probe> probes, int schema, String serverVersion) {
        Corpus {
            Objects.requireNonNull(javaVersion); probes = List.copyOf(probes); Objects.requireNonNull(serverVersion);
        }
    }

    record Probe(List<ConnectorEdge> acceptedConnectorEdges, Box aggregateBoundingBox,
            GenerationRng generationRng, PersistedNbt persistedNbt, List<Piece> piecesInAcceptedOrder,
            Request request, RootSample rootSample, TerrainProjectionQueries terrainProjectionQueries) {
        Probe {
            acceptedConnectorEdges = List.copyOf(acceptedConnectorEdges);
            piecesInAcceptedOrder = List.copyOf(piecesInAcceptedOrder);
            Objects.requireNonNull(aggregateBoundingBox); Objects.requireNonNull(generationRng);
            Objects.requireNonNull(persistedNbt); Objects.requireNonNull(request);
            Objects.requireNonNull(rootSample); Objects.requireNonNull(terrainProjectionQueries);
        }
    }

    record Request(int chunkX, int chunkZ, String worldSeedI64) {
        Request { Objects.requireNonNull(worldSeedI64); }
    }

    record RootSample(Vec3i origin, String rotation, int sampledStartHeight,
            Vec3i sampledStartPosition, String startPool, Vec3i stubPosition, String template) {
        RootSample {
            Objects.requireNonNull(origin); Objects.requireNonNull(rotation);
            Objects.requireNonNull(sampledStartPosition); Objects.requireNonNull(startPool);
            Objects.requireNonNull(stubPosition); Objects.requireNonNull(template);
        }
    }

    record Piece(Box boundingBox, int depth, int groundLevelDelta,
            List<Junction> junctionsInAcceptedOrder, int ordinal, Vec3i origin,
            List<String> processorOrder, String processorRegistryKey, String projection,
            String rotation, String template) {
        Piece {
            junctionsInAcceptedOrder = List.copyOf(junctionsInAcceptedOrder);
            processorOrder = List.copyOf(processorOrder);
            Objects.requireNonNull(boundingBox); Objects.requireNonNull(origin);
            Objects.requireNonNull(processorRegistryKey); Objects.requireNonNull(projection);
            Objects.requireNonNull(rotation); Objects.requireNonNull(template);
        }
    }

    record Junction(int deltaY, String destinationProjection, int sourceGroundY, int sourceX,
            int sourceZ) {
        Junction { Objects.requireNonNull(destinationProjection); }
    }

    record ConnectorEdge(boolean officialCanAttach, int placementPriority, String resolvedAlias,
            String selectedPool, int selectionPriority, int sourceConnectorOrdinal, String sourceName,
            int sourcePiece, Vec3i sourcePosition, String sourceTarget, int targetConnectorOrdinal,
            String targetName, int targetPiece, Vec3i targetPosition, String targetTarget) {
        ConnectorEdge {
            Objects.requireNonNull(resolvedAlias); Objects.requireNonNull(selectedPool);
            Objects.requireNonNull(sourceName); Objects.requireNonNull(sourcePosition);
            Objects.requireNonNull(sourceTarget); Objects.requireNonNull(targetName);
            Objects.requireNonNull(targetPosition); Objects.requireNonNull(targetTarget);
        }
    }

    record GenerationRng(List<String> continuationNextLongI64, String finalLegacy48State,
            int operationCount, String operationTranscriptSha256, int worldgenCount) {
        GenerationRng {
            continuationNextLongI64 = List.copyOf(continuationNextLongI64);
            Objects.requireNonNull(finalLegacy48State); Objects.requireNonNull(operationTranscriptSha256);
        }
    }

    record PersistedNbt(List<NbtReceipt> pieces, NbtReceipt structureStart) {
        PersistedNbt { pieces = List.copyOf(pieces); Objects.requireNonNull(structureStart); }
    }

    record NbtReceipt(int binaryLength, String binarySha256, int gzipLength, String gzipSha256,
            NbtCompound orderedTree) {
        NbtReceipt {
            Objects.requireNonNull(binarySha256); Objects.requireNonNull(gzipSha256);
            Objects.requireNonNull(orderedTree);
        }
    }

    record TerrainProjectionQueries(String boundary, int queryCount, List<ProjectionQuery> queryOrder) {
        TerrainProjectionQueries { Objects.requireNonNull(boundary); queryOrder = List.copyOf(queryOrder); }
    }

    record ProjectionQuery(String heightmap, String operation, int ordinal, int result, int x, int z) {
        ProjectionQuery { Objects.requireNonNull(heightmap); Objects.requireNonNull(operation); }
    }

    record Vec3i(int x, int y, int z) { }

    record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean valid() { return minX <= maxX && minY <= maxY && minZ <= maxZ; }
        boolean contains(Vec3i value) {
            return value.x() >= minX && value.x() <= maxX
                    && value.y() >= minY && value.y() <= maxY
                    && value.z() >= minZ && value.z() <= maxZ;
        }
        Box union(Box other) {
            return new Box(Math.min(minX, other.minX), Math.min(minY, other.minY),
                    Math.min(minZ, other.minZ), Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
        }
        Box inflate(int amount) {
            return new Box(Math.subtractExact(minX, amount), Math.subtractExact(minY, amount),
                    Math.subtractExact(minZ, amount), Math.addExact(maxX, amount),
                    Math.addExact(maxY, amount), Math.addExact(maxZ, amount));
        }
        List<Integer> asList() { return List.of(minX, minY, minZ, maxX, maxY, maxZ); }
    }

    record AliasResolution(String selectedPool, String resolvedPool) {
        AliasResolution { Objects.requireNonNull(selectedPool); Objects.requireNonNull(resolvedPool); }
    }

    sealed interface NbtTag permits NbtCompound, NbtList, NbtInt, NbtString, NbtIntArray {
        int typeId();
    }

    record NbtCompound(List<NbtEntry> entries) implements NbtTag {
        NbtCompound { entries = List.copyOf(entries); }
        @Override public int typeId() { return 10; }
    }

    record NbtList(int elementType, List<NbtTag> values) implements NbtTag {
        NbtList { values = List.copyOf(values); }
        @Override public int typeId() { return 9; }
    }

    record NbtInt(int value) implements NbtTag {
        @Override public int typeId() { return 3; }
    }

    record NbtString(String value) implements NbtTag {
        NbtString { Objects.requireNonNull(value); }
        @Override public int typeId() { return 8; }
    }

    record NbtIntArray(List<Integer> values) implements NbtTag {
        NbtIntArray { values = List.copyOf(values); }
        @Override public int typeId() { return 11; }
    }

    record NbtEntry(String name, NbtTag tag) {
        NbtEntry { Objects.requireNonNull(name); Objects.requireNonNull(tag); }
    }

    private static final class NbtBudget {
        private int nodes;
        void consume(int depth) {
            require(depth <= 32, "Trial Chambers persisted NBT depth exceeded");
            require(++nodes <= MAX_NBT_NODES, "Trial Chambers persisted NBT node bound exceeded");
        }
    }

    private static final class JsonReader {
        private static final int MAX_STRING_CHARS = 196_608;
        private final String text;
        private int offset;

        JsonReader(byte[] bytes) {
            try {
                text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException error) {
                throw invalid("Trial Chambers start-graph is not valid UTF-8", error);
            }
        }

        void beginObject() { expect('{'); }
        void endObject() { expect('}'); }
        void beginArray() { expect('['); }
        void endArray() { expect(']'); }

        void field(String expected, int index) {
            String actual = readFieldName(index);
            if (!expected.equals(actual)) {
                throw invalid("Trial Chambers start-graph JSON field order/schema drift: expected "
                        + expected + ", got " + actual);
            }
        }

        String readFieldName(int index) {
            rejectWhitespace();
            if (index > 0) {
                expectRaw(',');
                if (peek() == '}') throw invalid("Trial Chambers JSON trailing object comma");
            }
            String name = readString();
            expectRaw(':');
            return name;
        }

        boolean nextArrayValue(int index) {
            rejectWhitespace();
            if (index == 0) return peek() != ']';
            if (peek() == ']') return false;
            expectRaw(',');
            if (peek() == ']') throw invalid("Trial Chambers JSON trailing array comma");
            return true;
        }

        String readString() {
            rejectWhitespace();
            expectRaw('"');
            StringBuilder value = new StringBuilder();
            while (offset < text.length()) {
                char current = text.charAt(offset++);
                if (current == '"') return value.toString();
                if (current < 0x20) throw invalid("Trial Chambers JSON control character");
                if (current != '\\') {
                    if (Character.isSurrogate(current)) {
                        require(Character.isHighSurrogate(current) && offset < text.length()
                                        && Character.isLowSurrogate(text.charAt(offset)),
                                "malformed Trial Chambers JSON surrogate");
                        value.append(current).append(text.charAt(offset++));
                    } else {
                        value.append(current);
                    }
                } else {
                    if (offset >= text.length()) throw invalid("truncated Trial Chambers JSON escape");
                    char escaped = text.charAt(offset++);
                    switch (escaped) {
                        case '"', '\\', '/' -> value.append(escaped);
                        case 'b' -> value.append('\b');
                        case 'f' -> value.append('\f');
                        case 'n' -> value.append('\n');
                        case 'r' -> value.append('\r');
                        case 't' -> value.append('\t');
                        case 'u' -> appendUnicodeEscape(value);
                        default -> throw invalid("unknown Trial Chambers JSON escape");
                    }
                }
                require(value.length() <= MAX_STRING_CHARS,
                        "Trial Chambers JSON string exceeds bound");
            }
            throw invalid("truncated Trial Chambers JSON string");
        }

        private void appendUnicodeEscape(StringBuilder value) {
            char first = readUnicodeEscape();
            if (!Character.isSurrogate(first)) {
                value.append(first);
                return;
            }
            require(Character.isHighSurrogate(first), "malformed Trial Chambers Unicode surrogate");
            require(offset + 2 <= text.length() && text.charAt(offset) == '\\'
                            && text.charAt(offset + 1) == 'u',
                    "missing Trial Chambers low Unicode surrogate");
            offset += 2;
            char second = readUnicodeEscape();
            require(Character.isLowSurrogate(second), "malformed Trial Chambers low Unicode surrogate");
            value.append(first).append(second);
        }

        private char readUnicodeEscape() {
            require(offset + 4 <= text.length(), "truncated Trial Chambers Unicode escape");
            int value = 0;
            for (int index = 0; index < 4; index++) {
                int digit = Character.digit(text.charAt(offset++), 16);
                require(digit >= 0, "malformed Trial Chambers Unicode escape");
                value = value * 16 + digit;
            }
            return (char) value;
        }

        int readInt() {
            String value = readNumber();
            require(value.indexOf('.') < 0 && value.indexOf('e') < 0 && value.indexOf('E') < 0,
                    "Trial Chambers JSON integer type drift");
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException error) {
                throw invalid("Trial Chambers JSON integer overflow", error);
            }
        }

        boolean readBoolean() {
            rejectWhitespace();
            if (text.startsWith("true", offset)) { offset += 4; return true; }
            if (text.startsWith("false", offset)) { offset += 5; return false; }
            throw invalid("Trial Chambers JSON boolean type drift");
        }

        private String readNumber() {
            rejectWhitespace();
            int start = offset;
            if (peek() == '-') offset++;
            require(offset < text.length(), "truncated Trial Chambers JSON number");
            char first = text.charAt(offset);
            if (first == '0') {
                offset++;
                require(offset >= text.length() || !Character.isDigit(text.charAt(offset)),
                        "Trial Chambers JSON leading zero");
            } else if (first >= '1' && first <= '9') {
                do { offset++; } while (offset < text.length() && Character.isDigit(text.charAt(offset)));
            } else {
                throw invalid("malformed Trial Chambers JSON number");
            }
            if (offset < text.length() && text.charAt(offset) == '.') {
                offset++;
                int fraction = offset;
                while (offset < text.length() && Character.isDigit(text.charAt(offset))) offset++;
                require(offset > fraction, "malformed Trial Chambers JSON fraction");
            }
            if (offset < text.length() && (text.charAt(offset) == 'e' || text.charAt(offset) == 'E')) {
                offset++;
                if (offset < text.length() && (text.charAt(offset) == '+' || text.charAt(offset) == '-')) offset++;
                int exponent = offset;
                while (offset < text.length() && Character.isDigit(text.charAt(offset))) offset++;
                require(offset > exponent, "malformed Trial Chambers JSON exponent");
            }
            return text.substring(start, offset);
        }

        private void expect(char expected) {
            rejectWhitespace();
            expectRaw(expected);
        }

        private void expectRaw(char expected) {
            if (offset >= text.length() || text.charAt(offset) != expected) {
                throw invalid("Trial Chambers JSON syntax drift near offset " + offset
                        + ": expected " + expected);
            }
            offset++;
        }

        private char peek() { return offset >= text.length() ? '\0' : text.charAt(offset); }

        private void rejectWhitespace() {
            if (offset < text.length()) {
                char value = text.charAt(offset);
                require(value != ' ' && value != '\n' && value != '\r' && value != '\t',
                        "Trial Chambers start-graph JSON is not canonical compact form");
            }
        }

        void finish() {
            rejectWhitespace();
            require(offset == text.length(), "trailing Trial Chambers start-graph JSON data");
        }
    }
}
