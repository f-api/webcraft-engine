package com.gameexpert.terrain.mc.structure;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant strict typed reader/model for the authenticated Minecraft 26.3 Pillager Outpost
 * start-graph evidence.
 *
 * <p>This class is evidence only. It does not plan, place, settle, register, dispatch, or publish a
 * structure successor. The resource is authenticated byte-for-byte before any model is exposed,
 * and the persisted typed NBT trees are cross-checked against their binary NBT receipts.</p>
 */
final class Mc263PillagerOutpostStartGraph {
    static final String RESOURCE = "/mc263/pillager-outpost-start-graph-v1.json";
    static final int RESOURCE_BYTES = 928_620;
    static final String RESOURCE_SHA256 =
            "7ffa7158f8d6031306600c69e45cdcccc42f66b2545729382b221e1ab07a73e2";
    static final int SCHEMA = 3;
    static final int JAVA_VERSION = 25;
    static final String SERVER_VERSION = "26.3 Snapshot 7";
    static final int PROBE_COUNT = 4;
    static final int PIECE_NBT_COUNT = 44;
    static final int START_NBT_COUNT = 4;
    static final int CONTINUATION_WORD_COUNT = 32;
    static final int DRAW_COUNT = 5_562;

    private static final String STRUCTURE_KEY = "minecraft:pillager_outpost";
    private static final String START_POOL = "minecraft:pillager_outpost/base_plates";
    private static final String TERRAIN_BOUNDARY =
            "official GenerationContext ChunkGenerator.getBaseHeight calls";
    private static final String HEIGHTMAP = "WORLD_SURFACE_WG";
    private static final String BASE_HEIGHT_OPERATION = "getBaseHeight";
    private static final long RNG_MULTIPLIER = 0x5DEECE66DL;
    private static final long RNG_ADDEND = 0xBL;
    private static final long RNG_MASK = (1L << 48) - 1L;
    private static final int AGGREGATE_PADDING = 12;

    private static final int[] PIECE_COUNTS = {12, 7, 11, 14};
    private static final int[] EDGE_COUNTS = {11, 6, 10, 13};
    private static final int[] QUERY_COUNTS = {37, 29, 27, 24};
    private static final int[] WORLDGEN_COUNTS = {1_587, 1_502, 1_334, 1_131};
    private static final int[] PROBE_DRAW_COUNTS = {1_589, 1_504, 1_336, 1_133};
    private static final String[] FINAL_STATE48 = {
            "183468393443311", "1519185254622", "114116004043340", "244460322407363"};
    private static final String[] FINAL_STATE48_HEX = {
            "a6dd106b2fef", "0161b67f58de", "67c9b3e25a4c", "de55daadc3c3"};
    private static final String[] SECOND_SET_SEEDS = {
            "-5079441670220342285", "1916329576588853249",
            "-3280089179490649089", "6602777903070089287"};
    private static final String[] PROBE_SHA256 = {
            "03a1a907944af8fa4d314fbb031c425f828cd33895079ba4744b3e58ca7bbf96",
            "63ecbad02f4bde4f21ec18a3fca058c0f0a78ec9d7ac81d1d9f65cff688ea4d0",
            "b50c5c0fc0df8b8869d6c5cbfccd367417c7362658018aab209d6b5eb6a1c177",
            "3818df0da793192ad3039deebabdfd49e1079f699c7156b86827b85933ec872e"};
    private static final int[] START_NBT_LENGTHS = {6_540, 3_894, 6_046, 7_671};
    private static final String[] START_NBT_SHA256 = {
            "0f510307a33caf049716bfdbf099dd8aad64bb8e5de34dec2eb13fdd4a90d649",
            "5ca95dd461b7f8c512a520f8a19a9fc08c0b99a9b3b3c02ef2458efcf6260a69",
            "552e6c4922fda04e3854aac881586b66b5ca75a67d3151f59f9babb880d1e9de",
            "5d7eb21995d5d355b337a501366fcf65ffba70925f45c7c26c310f86eb199451"};

    private static final List<Request> REQUESTS = List.of(
            new Request(50, 17, STRUCTURE_KEY, "2630007"),
            new Request(-89, 7, STRUCTURE_KEY, "2630007"),
            new Request(4, 134, STRUCTURE_KEY, "2630007"),
            new Request(-16, -153, STRUCTURE_KEY, "2630007"));
    private static final List<Box> AGGREGATE_BOXES = List.of(
            new Box(757, 94, 229, 828, 147, 300),
            new Box(-1452, 58, 84, -1381, 116, 155),
            new Box(21, 57, 2116, 92, 113, 2187),
            new Box(-284, 50, -2491, -213, 103, -2420));
    private static final List<Vec3i> STUB_POSITIONS = List.of(
            new Vec3i(792, 107, 264), new Vec3i(-1416, 76, 119),
            new Vec3i(56, 73, 2151), new Vec3i(-248, 63, -2455));
    private static final List<List<String>> CONTINUATIONS = List.of(
            List.of("-5842086773805120093", "7926989689403713472", "1856824042585217557",
                    "1352148355629142972", "7089134252151627347", "-3199230961773868639",
                    "2207610198945481510", "6376083094430985188"),
            List.of("-3936833579896351966", "-7647891660765180836", "4311784983683600268",
                    "-372420366834492083", "-2150197648900248708", "-7635328125208956665",
                    "-8815339466866910248", "-8526763485088765024"),
            List.of("5400971058312478731", "-8457914642620793249", "-3504689736403901147",
                    "3960346483552701401", "-190700485324076695", "3514737974558802483",
                    "-6822525099594235163", "-7342767309369954914"),
            List.of("-1744920964796214216", "-8871643039057002632", "5009513037920155322",
                    "-3905549916236625466", "8610397631562914072", "7643522490845957851",
                    "6243213444824373533", "889499223780820995"));
    private static final List<List<Integer>> PIECE_NBT_LENGTHS = List.of(
            List.of(726, 792, 686, 707, 440, 448, 450, 440, 451, 447, 451, 442),
            List.of(717, 440, 455, 677, 621, 454, 455),
            List.of(725, 612, 685, 799, 448, 449, 448, 466, 448, 455, 448),
            List.of(732, 707, 692, 713, 706, 459, 450, 466, 459, 455, 439, 449, 440, 450));
    private static final List<List<String>> PIECE_NBT_SHA256 = List.of(
            List.of(
                    "f29117993c419ebca09c14ec0bc10d4219c0f4d3be4c212450d7d0d79651aa03",
                    "02222c988adbe173e2d79967b1cddd15e4204b4f07d129bf66f354869210b9b6",
                    "32a9dce84662a249368fd7a4741120c49460666c2a50eb6788f124107044b9ce",
                    "42990e0998ff62cc7e97ec885738cb3431fec052da2d29f8449f4b3ddff47b6d",
                    "22928b0edd8c99fe57acf8793923faf8590021340e47ef1bf36b24175068fd38",
                    "8ca2da017639ebf7239e9db80018c7e2424fe484f065b09e033432b2a2261544",
                    "681b75b9ec6cbafe5e92bb36658e1285f53e2578ba5c75e0eca98ea9cd1a7e9b",
                    "57db342871a6616b424b43c63e109c3bc873776eedb7580b6a9f0ab9e88e1cd2",
                    "87737c75a4cf52ddaa68f9b5b42a755d6e0436f374c90ea435101b680af2e4ca",
                    "5de811ceddd8834255fdd4b2b17da8644453b5804dde66ed70fcee4133a3af50",
                    "750189b34e1d0f1fad305a63da12e1c848a77f3d4c379ffb5885abd30b4b7f7a",
                    "8aeb260f2015cfcadcbad1ce1e763799172ed337472ef1567bff76322ef8de4f"),
            List.of(
                    "5fd3e52a9b39a97909c98600685d3455b3fef6545ecfb04865687b28244d4744",
                    "d742d2fbc11aa49803b99caec8a716a880ef86b752286f3ac3148a5e3de412c5",
                    "d98cf6a9ed190da18393b4896cbd74b8d1f79a449a07134d4b0f38bc8e5f9c40",
                    "aa80e85926addfd8e557c197e9213046eebb3b9253894a80455ca3acc2652777",
                    "8e50dcc904508b44cb7799f8f14b99d2cf4e13d5247794e28aa0aab9b37b2268",
                    "b5e8c11efdad45eac50163da22dabd17acb698555c33e1c5252ae1655c8019da",
                    "4062e11e19132623218915139115138e151b635db98476ade5cb1190800f84e6"),
            List.of(
                    "c574acc30fe4bf62118e92a1a6d59fdf412c5acb91d19f19c20bdb4aa8d250bc",
                    "70fe7c9fc1a405c23fbd1172301cdbfb55c50a11229928151d68cef54d5fd499",
                    "91d9ead578f296c42a6fac447bb75e09708029acf5c71a82b2772a124c97e7ec",
                    "3ed8861d57c7137145c78cb53b7f5b393e3b18266e1739b1280ecc0003f58d74",
                    "81c932506b6846d789b11b9741233ac298842ec30303ea3aa9a6da0ffdaeca54",
                    "c323c6c293e6aa6fd49b1862a6d287c22ccd9f633a7a5ed571a558392d8a322a",
                    "3491b6af84df5ed702302b38bd3a223fbd02b723f8ac9f01441bd4a0d973a14b",
                    "30693b42d4048bb10f9c10ca64d42c13ad8539d688571f5d704f2c4bcc3976b7",
                    "25a95a94104ea1a612d26230794191ed1d45f55eb9e3da605063da12a285795f",
                    "c159857308274b83adb63469a2b8bb1fe179d193514b2b18b9f2193df78acd82",
                    "4a61280b56a13475af9f28f3851afedb3a1c4f9a0b36c3e2a61aa30d4b47bd7e"),
            List.of(
                    "62b89fcb7e17b05da3f97b4a554e95c4363d5669a122bcd87303890349f7dbe5",
                    "cd811730e8bef5fa9665dcf2927b711adc8fc4a34dc6f7d1ba29c3aa5564e708",
                    "f468afba1219f3a8d39bde5cce9113f6a7abdceaebb65dacec9210474afb5013",
                    "24e2fae009f7663eba9a36a0668deb152cab6370f6d4b70a77b5c26ef58c88f7",
                    "fb616b3955184d55e31644f97439dfef7aa5d08053bad1fbbcb763947b049dc0",
                    "09bdda073af3a6f8f4138ef0ff6463dc513280f64d89e43fa773f1b5cd8aedaf",
                    "6c769d19116283ce47bdfefc40214e4a2b46e1e896fbe47832b78da4a0022e47",
                    "48c102245214dcfad0bf00d60d58a2a96b21a38e34fdccf8af708800d7f94dc6",
                    "552720d7a3c391d517e04231611761ec92c677801aa568735415d3c12841fcf8",
                    "21516f9a3342863b18ca0fb64a300303ab3c59d3959e3ae30e073ff6d3736f4b",
                    "31629e28ec465165d978ccf846d340fe947919b6716aae02423e1e8573c33266",
                    "07fb196340f6437e905bbdc134f503e5eb2224cea0fb9dcae854254d709b104c",
                    "c240010ff9e1829c9e85267eaaa9843c0619517895307497a2d9b834624c6158",
                    "551b14588660705b8fa5e9b4ec5cd806240778d1b8b3fe966bcdf51373104d6d"));

    private static final Set<String> ROTATIONS = Set.of(
            "NONE", "CLOCKWISE_90", "CLOCKWISE_180", "COUNTERCLOCKWISE_90");
    private static final Set<String> PROJECTIONS = Set.of("rigid", "terrain_matching");
    private static final Set<String> TEMPLATE_KEYS = Set.of(
            "minecraft:pillager_outpost/base_plate",
            "minecraft:pillager_outpost/feature_plate",
            "minecraft:pillager_outpost/feature_cage1",
            "minecraft:pillager_outpost/feature_cage2",
            "minecraft:pillager_outpost/feature_cage_with_allays",
            "minecraft:pillager_outpost/feature_logs",
            "minecraft:pillager_outpost/feature_tent1",
            "minecraft:pillager_outpost/feature_tent2",
            "minecraft:pillager_outpost/feature_targets");

    private Mc263PillagerOutpostStartGraph() {
        throw new AssertionError("no instances");
    }

    /**
     * Decodes the authenticated start-graph verification corpus from caller-supplied bytes.
     *
     * <p>The corpus is a fixed-probe oracle, not a production fact: only
     * {@code Mc263PillagerOutpostLegacyProbeOracle} consults it, so it stays a test resource and
     * is never packaged or parsed on the world-join path.</p>
     */
    static Corpus decodeAccepted(byte[] bytes) {
        return decodeAuthenticated(bytes);
    }

    private static Corpus decodeAuthenticated(byte[] bytes) {
        Objects.requireNonNull(bytes, "Pillager Outpost start-graph bytes");
        require(bytes.length == RESOURCE_BYTES,
                "Pillager Outpost start-graph payload byte-count drift");
        require(RESOURCE_SHA256.equals(sha256(bytes)),
                "Pillager Outpost start-graph payload identity drift");
        return parseEvidence(bytes);
    }

    private static Corpus parseEvidence(byte[] bytes) {
        Objects.requireNonNull(bytes, "Pillager Outpost start-graph bytes");
        require(bytes.length <= RESOURCE_BYTES + 4_096,
                "Pillager Outpost start-graph parser bound exceeded");
        JsonReader in = new JsonReader(bytes);
        in.beginObject();
        in.field("javaVersion", 0); int javaVersion = in.readInt();
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
            require(index < 8, "too many Pillager Outpost start-graph probes");
            int start = in.mark();
            Probe probe = readProbe(in);
            int end = in.mark();
            require(index < PROBE_SHA256.length
                            && PROBE_SHA256[index].equals(in.rawSha256(start, end)),
                    "Pillager Outpost probe canonical receipt drift at " + index);
            probes.add(probe);
        }
        in.endArray();
        return List.copyOf(probes);
    }

    private static Probe readProbe(JsonReader in) {
        in.beginObject();
        in.field("acceptedConnectorEdges", 0); List<ConnectorEdge> edges = readEdges(in);
        in.field("generationRng", 1); GenerationRng rng = readGenerationRng(in);
        in.field("generationStart", 2); GenerationStart start = readGenerationStart(in);
        in.field("persistedNbt", 3); PersistedNbt persisted = readPersistedNbt(in);
        in.field("piecesInAcceptedOrder", 4); List<Piece> pieces = readPieces(in);
        in.field("request", 5); Request request = readRequest(in);
        in.field("terrainProjectionQueries", 6); TerrainProjectionQueries terrain = readTerrain(in);
        in.endObject();
        return new Probe(edges, rng, start, persisted, pieces, request, terrain);
    }

    private static List<ConnectorEdge> readEdges(JsonReader in) {
        in.beginArray();
        ArrayList<ConnectorEdge> rows = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 64, "too many Pillager Outpost connector edges");
            in.beginObject();
            in.field("officialCanAttach", 0); boolean canAttach = in.readBoolean();
            in.field("sourceConnectorOrdinal", 1); int sourceConnectorOrdinal = in.readInt();
            in.field("sourceName", 2); String sourceName = in.readString();
            in.field("sourcePiece", 3); int sourcePiece = in.readInt();
            in.field("sourcePool", 4); String sourcePool = in.readString();
            in.field("sourcePosition", 5); Vec3i sourcePosition = readVec3i(in);
            in.field("sourceTarget", 6); String sourceTarget = in.readString();
            in.field("targetConnectorOrdinal", 7); int targetConnectorOrdinal = in.readInt();
            in.field("targetName", 8); String targetName = in.readString();
            in.field("targetPiece", 9); int targetPiece = in.readInt();
            in.field("targetPosition", 10); Vec3i targetPosition = readVec3i(in);
            in.field("targetTarget", 11); String targetTarget = in.readString();
            in.endObject();
            rows.add(new ConnectorEdge(canAttach, sourceConnectorOrdinal, sourceName, sourcePiece,
                    sourcePool, sourcePosition, sourceTarget, targetConnectorOrdinal, targetName,
                    targetPiece, targetPosition, targetTarget));
        }
        in.endArray();
        return List.copyOf(rows);
    }

    private static GenerationRng readGenerationRng(JsonReader in) {
        in.beginObject();
        in.field("continuationNextLongI64", 0);
        List<String> continuation = readStrings(in, 16, "RNG continuation");
        in.field("draws", 1); List<RngDraw> draws = readDraws(in);
        in.field("state48", 2); String state48 = in.readString();
        in.field("state48Hex", 3); String state48Hex = in.readString();
        in.field("worldgenCount", 4); int worldgenCount = in.readInt();
        in.endObject();
        return new GenerationRng(continuation, draws, state48, state48Hex, worldgenCount);
    }

    private static List<RngDraw> readDraws(JsonReader in) {
        in.beginArray();
        ArrayList<RngDraw> draws = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 6_000, "too many Pillager Outpost RNG draws");
            in.beginObject();
            in.field("argument", 0);
            if (in.peekValue() == '"') {
                String argument = in.readString();
                in.field("operation", 1); String operation = in.readString();
                in.field("ordinal", 2); int ordinal = in.readInt();
                in.endObject();
                draws.add(new SetSeedDraw(argument, operation, ordinal));
            } else {
                int argument = in.readInt();
                in.field("operation", 1); String operation = in.readString();
                in.field("ordinal", 2); int ordinal = in.readInt();
                in.field("result", 3); int result = in.readInt();
                in.field("state48After", 4); String state48After = in.readString();
                in.endObject();
                draws.add(new NextBitsDraw(argument, operation, ordinal, result, state48After));
            }
        }
        in.endArray();
        return List.copyOf(draws);
    }

    private static GenerationStart readGenerationStart(JsonReader in) {
        in.beginObject();
        in.field("aggregateBoundingBox", 0); Box box = readBox(in);
        in.field("pieceCount", 1); int pieceCount = in.readInt();
        in.field("startPool", 2); String startPool = in.readString();
        in.field("stubPosition", 3); Vec3i stub = readVec3i(in);
        in.endObject();
        return new GenerationStart(box, pieceCount, startPool, stub);
    }

    private static PersistedNbt readPersistedNbt(JsonReader in) {
        in.beginObject();
        in.field("pieces", 0);
        in.beginArray();
        ArrayList<NbtReceipt> pieces = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 64, "too many persisted Pillager Outpost piece NBT rows");
            pieces.add(readNbtReceipt(in));
        }
        in.endArray();
        in.field("structureStart", 1); NbtReceipt start = readNbtReceipt(in);
        in.endObject();
        return new PersistedNbt(pieces, start);
    }

    private static NbtReceipt readNbtReceipt(JsonReader in) {
        in.beginObject();
        in.field("binaryHex", 0); String binaryHex = in.readString();
        in.field("binaryLength", 1); int binaryLength = in.readInt();
        in.field("binarySha256", 2); String binarySha256 = in.readString();
        in.field("orderedTypedTree", 3); NbtCompound typedTree = readNbtRoot(in);
        in.endObject();
        NbtReceipt receipt = new NbtReceipt(binaryHex, binaryLength, binarySha256, typedTree);
        validateNbtReceipt(receipt);
        return receipt;
    }

    private static NbtCompound readNbtRoot(JsonReader in) {
        NbtBudget budget = new NbtBudget();
        NbtTag tag = readNbtTag(in, budget, 0);
        require(tag instanceof NbtCompound, "persisted NBT typed-tree root is not COMPOUND");
        return (NbtCompound) tag;
    }

    private static NbtTag readNbtTag(JsonReader in, NbtBudget budget, int depth) {
        budget.consume(depth);
        in.beginObject();
        String first = in.readFieldName(0);
        if (first.equals("entries")) {
            List<NbtEntry> entries = readNbtEntries(in, budget, depth + 1);
            in.field("type", 1); String type = in.readString();
            in.field("typeId", 2); int typeId = in.readInt();
            in.endObject();
            require(type.equals("COMPOUND") && typeId == 10,
                    "persisted NBT COMPOUND type closure drift");
            return new NbtCompound(type, typeId, entries);
        }
        if (first.equals("snbtValue")) {
            String snbtValue = in.readString();
            in.field("type", 1); String type = in.readString();
            in.field("typeId", 2); int typeId = in.readInt();
            in.endObject();
            return switch (type) {
                case "INT" -> {
                    require(typeId == 3, "persisted NBT INT type-id drift");
                    yield new NbtInt(snbtValue, type, typeId);
                }
                case "STRING" -> {
                    require(typeId == 8, "persisted NBT STRING type-id drift");
                    yield new NbtString(snbtValue, type, typeId);
                }
                case "INT[]" -> {
                    require(typeId == 11, "persisted NBT INT[] type-id drift");
                    yield new NbtIntArray(snbtValue, type, typeId);
                }
                default -> throw invalid("unsupported persisted NBT scalar type: " + type);
            };
        }
        if (first.equals("type")) {
            String type = in.readString();
            in.field("typeId", 1); int typeId = in.readInt();
            in.field("values", 2);
            in.beginArray();
            ArrayList<NbtTag> values = new ArrayList<>();
            for (int index = 0; in.nextArrayValue(index); index++) {
                require(index < 256, "persisted NBT LIST exceeds bound");
                values.add(readNbtTag(in, budget, depth + 1));
            }
            in.endArray();
            in.endObject();
            require(type.equals("LIST") && typeId == 9,
                    "persisted NBT LIST type closure drift");
            return new NbtList(type, typeId, values);
        }
        throw invalid("persisted NBT typed-tree field/schema drift: " + first);
    }

    private static List<NbtEntry> readNbtEntries(JsonReader in, NbtBudget budget, int depth) {
        in.beginArray();
        ArrayList<NbtEntry> entries = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 256, "persisted NBT COMPOUND exceeds entry bound");
            in.beginObject();
            in.field("name", 0); String name = in.readString();
            in.field("tag", 1); NbtTag tag = readNbtTag(in, budget, depth);
            in.endObject();
            entries.add(new NbtEntry(name, tag));
        }
        in.endArray();
        return List.copyOf(entries);
    }

    private static List<Piece> readPieces(JsonReader in) {
        in.beginArray();
        ArrayList<Piece> pieces = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 64, "too many Pillager Outpost start-graph pieces");
            pieces.add(readPiece(in));
        }
        in.endArray();
        return List.copyOf(pieces);
    }

    private static Piece readPiece(JsonReader in) {
        in.beginObject();
        in.field("boundingBox", 0); Box box = readBox(in);
        String second = in.readFieldName(1);
        if (second.equals("groundLevelDelta")) {
            int groundLevelDelta = in.readInt();
            in.field("junctionsInAcceptedOrder", 2); List<Junction> junctions = readJunctions(in);
            in.field("kind", 3); String kind = in.readString();
            in.field("ordinal", 4); int ordinal = in.readInt();
            in.field("position", 5); Vec3i position = readVec3i(in);
            in.field("processorOrder", 6);
            List<String> processorOrder = readStrings(in, 8, "piece processor order");
            in.field("processorRegistryKey", 7); String processorRegistryKey = in.readString();
            in.field("projection", 8); String projection = in.readString();
            in.field("rotation", 9); String rotation = in.readString();
            in.field("template", 10); String template = in.readString();
            in.endObject();
            require(kind.equals("template"), "Pillager Outpost piece kind/schema drift");
            return new TemplatePiece(box, groundLevelDelta, junctions, ordinal, position,
                    processorOrder, processorRegistryKey, projection, rotation, template);
        }
        if (second.equals("childrenInPlacementOrder")) {
            List<PlacementChild> children = readPlacementChildren(in);
            in.field("groundLevelDelta", 2); int groundLevelDelta = in.readInt();
            in.field("junctionsInAcceptedOrder", 3); List<Junction> junctions = readJunctions(in);
            in.field("kind", 4); String kind = in.readString();
            in.field("ordinal", 5); int ordinal = in.readInt();
            in.field("position", 6); Vec3i position = readVec3i(in);
            in.field("projection", 7); String projection = in.readString();
            in.field("rotation", 8); String rotation = in.readString();
            in.endObject();
            require(kind.equals("list"), "Pillager Outpost list piece kind/schema drift");
            return new ListPiece(box, children, groundLevelDelta, junctions, ordinal, position,
                    projection, rotation);
        }
        throw invalid("Pillager Outpost piece field/schema drift: " + second);
    }

    private static List<PlacementChild> readPlacementChildren(JsonReader in) {
        in.beginArray();
        ArrayList<PlacementChild> children = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 8, "too many Pillager Outpost list-piece children");
            in.beginObject();
            in.field("processorOrder", 0);
            List<String> processorOrder = readStrings(in, 8, "list-child processor order");
            in.field("processorRegistryKey", 1); String processorRegistryKey = in.readString();
            in.field("template", 2); String template = in.readString();
            in.endObject();
            children.add(new PlacementChild(processorOrder, processorRegistryKey, template));
        }
        in.endArray();
        return List.copyOf(children);
    }

    private static List<Junction> readJunctions(JsonReader in) {
        in.beginArray();
        ArrayList<Junction> junctions = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 64, "too many Pillager Outpost piece junctions");
            in.beginObject();
            in.field("deltaY", 0); int deltaY = in.readInt();
            in.field("destinationProjection", 1); String projection = in.readString();
            in.field("sourceGroundY", 2); int sourceGroundY = in.readInt();
            in.field("sourceX", 3); int sourceX = in.readInt();
            in.field("sourceZ", 4); int sourceZ = in.readInt();
            in.endObject();
            junctions.add(new Junction(deltaY, projection, sourceGroundY, sourceX, sourceZ));
        }
        in.endArray();
        return List.copyOf(junctions);
    }

    private static Request readRequest(JsonReader in) {
        in.beginObject();
        in.field("chunkX", 0); int chunkX = in.readInt();
        in.field("chunkZ", 1); int chunkZ = in.readInt();
        in.field("structureKey", 2); String structureKey = in.readString();
        in.field("worldSeedI64", 3); String worldSeed = in.readString();
        in.endObject();
        return new Request(chunkX, chunkZ, structureKey, worldSeed);
    }

    private static TerrainProjectionQueries readTerrain(JsonReader in) {
        in.beginObject();
        in.field("boundary", 0); String boundary = in.readString();
        in.field("queryCount", 1); int queryCount = in.readInt();
        in.field("queryOrder", 2);
        in.beginArray();
        ArrayList<ProjectionQuery> queries = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 256, "too many Pillager Outpost projection queries");
            in.beginObject();
            in.field("heightmap", 0); String heightmap = in.readString();
            in.field("operation", 1); String operation = in.readString();
            in.field("ordinal", 2); int ordinal = in.readInt();
            in.field("result", 3); int result = in.readInt();
            in.field("x", 4); int x = in.readInt();
            in.field("z", 5); int z = in.readInt();
            in.endObject();
            queries.add(new ProjectionQuery(heightmap, operation, ordinal, result, x, z));
        }
        in.endArray();
        in.endObject();
        return new TerrainProjectionQueries(boundary, queryCount, queries);
    }

    private static Vec3i readVec3i(JsonReader in) {
        in.beginArray();
        require(in.nextArrayValue(0), "Pillager Outpost Vec3i is empty"); int x = in.readInt();
        require(in.nextArrayValue(1), "Pillager Outpost Vec3i misses y"); int y = in.readInt();
        require(in.nextArrayValue(2), "Pillager Outpost Vec3i misses z"); int z = in.readInt();
        require(!in.nextArrayValue(3), "Pillager Outpost Vec3i has extra coordinate");
        in.endArray();
        return new Vec3i(x, y, z);
    }

    private static Box readBox(JsonReader in) {
        in.beginArray();
        int[] values = new int[6];
        for (int index = 0; index < values.length; index++) {
            require(in.nextArrayValue(index), "Pillager Outpost bounding box is truncated");
            values[index] = in.readInt();
        }
        require(!in.nextArrayValue(6), "Pillager Outpost bounding box has extra coordinate");
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
        require(corpus.javaVersion() == JAVA_VERSION, "Pillager Outpost start-graph Java version drift");
        require(corpus.schema() == SCHEMA, "Pillager Outpost start-graph schema drift");
        require(corpus.serverVersion().equals(SERVER_VERSION),
                "Pillager Outpost start-graph server version drift");
        require(corpus.probes().size() == PROBE_COUNT,
                "Pillager Outpost start-graph probe cardinality drift");

        int totalPieces = 0;
        int totalEdges = 0;
        int totalQueries = 0;
        int totalPieceNbt = 0;
        int totalStartNbt = 0;
        int totalContinuations = 0;
        int totalDraws = 0;
        for (int probeIndex = 0; probeIndex < corpus.probes().size(); probeIndex++) {
            Probe probe = corpus.probes().get(probeIndex);
            validateProbe(probeIndex, probe);
            totalPieces += probe.piecesInAcceptedOrder().size();
            totalEdges += probe.acceptedConnectorEdges().size();
            totalQueries += probe.terrainProjectionQueries().queryOrder().size();
            totalPieceNbt += probe.persistedNbt().pieces().size();
            totalStartNbt++;
            totalContinuations += probe.generationRng().continuationNextLongI64().size();
            totalDraws += probe.generationRng().draws().size();
        }
        require(totalPieces == 44, "Pillager Outpost total piece count drift");
        require(totalEdges == 40, "Pillager Outpost total connector-edge count drift");
        require(totalQueries == 117, "Pillager Outpost total projection-query count drift");
        require(totalPieceNbt == PIECE_NBT_COUNT, "Pillager Outpost total piece NBT count drift");
        require(totalStartNbt == START_NBT_COUNT, "Pillager Outpost total start NBT count drift");
        require(totalContinuations == CONTINUATION_WORD_COUNT,
                "Pillager Outpost total continuation count drift");
        require(totalDraws == DRAW_COUNT, "Pillager Outpost total RNG draw count drift");
    }

    private static void validateProbe(int probeIndex, Probe probe) {
        require(probe.request().equals(REQUESTS.get(probeIndex)),
                "Pillager Outpost probe request/order drift at " + probeIndex);
        GenerationStart start = probe.generationStart();
        require(start.pieceCount() == PIECE_COUNTS[probeIndex],
                "Pillager Outpost generation piece-count drift at " + probeIndex);
        require(start.startPool().equals(START_POOL),
                "Pillager Outpost generation start-pool drift at " + probeIndex);
        require(start.aggregateBoundingBox().equals(AGGREGATE_BOXES.get(probeIndex)),
                "Pillager Outpost aggregate coordinate drift at " + probeIndex);
        require(start.stubPosition().equals(STUB_POSITIONS.get(probeIndex)),
                "Pillager Outpost stub coordinate drift at " + probeIndex);
        require(start.aggregateBoundingBox().contains(start.stubPosition()),
                "Pillager Outpost stub escaped aggregate box at " + probeIndex);

        List<Piece> pieces = probe.piecesInAcceptedOrder();
        require(pieces.size() == PIECE_COUNTS[probeIndex],
                "Pillager Outpost ordered-piece cardinality drift at " + probeIndex);
        validatePieces(pieces, start.aggregateBoundingBox());

        require(probe.acceptedConnectorEdges().size() == EDGE_COUNTS[probeIndex],
                "Pillager Outpost connector-edge cardinality drift at " + probeIndex);
        validateEdges(pieces, probe.acceptedConnectorEdges());
        validateTerrain(probeIndex, start.aggregateBoundingBox(), probe.terrainProjectionQueries());
        validateRng(probeIndex, probe.request(), probe.generationRng());
        validatePersistedNbt(probeIndex, pieces, probe.request(), probe.persistedNbt());
    }

    private static void validatePieces(List<Piece> pieces, Box aggregate) {
        require(!pieces.isEmpty(), "Pillager Outpost ordered piece graph is empty");
        int listCount = 0;
        Box union = null;
        for (int index = 0; index < pieces.size(); index++) {
            Piece piece = pieces.get(index);
            require(piece.ordinal() == index, "Pillager Outpost piece ordinal drift at " + index);
            require(piece.boundingBox().valid(), "Pillager Outpost invalid piece bounding box at " + index);
            require(piece.boundingBox().contains(piece.position()),
                    "Pillager Outpost piece position escaped its box at " + index);
            require(ROTATIONS.contains(piece.rotation()), "Pillager Outpost rotation drift at " + index);
            require(PROJECTIONS.contains(piece.projection()), "Pillager Outpost projection drift at " + index);
            require(piece.groundLevelDelta() == 0 || piece.groundLevelDelta() == 1,
                    "Pillager Outpost ground-level delta drift at " + index);
            for (Junction junction : piece.junctionsInAcceptedOrder()) {
                require(PROJECTIONS.contains(junction.destinationProjection()),
                        "Pillager Outpost junction projection drift at piece " + index);
                require(junction.deltaY() >= -1 && junction.deltaY() <= 1,
                        "Pillager Outpost junction delta drift at piece " + index);
            }
            if (piece instanceof TemplatePiece template) {
                require(TEMPLATE_KEYS.contains(template.template()),
                        "Pillager Outpost template identity drift at piece " + index);
                require(template.processorRegistryKey().equals("inline")
                                && template.processorOrder().isEmpty(),
                        "Pillager Outpost placed template processor drift at piece " + index);
            } else if (piece instanceof ListPiece list) {
                listCount++;
                require(list.projection().equals("rigid"),
                        "Pillager Outpost list-piece projection drift at " + index);
                require(list.childrenInPlacementOrder().equals(List.of(
                        new PlacementChild(List.of(), "inline",
                                "minecraft:pillager_outpost/watchtower"),
                        new PlacementChild(List.of(
                                "net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor"),
                                "minecraft:outpost_rot",
                                "minecraft:pillager_outpost/watchtower_overgrown"))),
                        "Pillager Outpost list-piece child order drift at " + index);
            } else {
                throw invalid("unsupported Pillager Outpost piece model at " + index);
            }
            union = union == null ? piece.boundingBox() : union.union(piece.boundingBox());
        }
        require(listCount == 1, "Pillager Outpost list-piece cardinality drift");
        require(pieces.getFirst() instanceof TemplatePiece root
                        && root.template().equals("minecraft:pillager_outpost/base_plate")
                        && root.projection().equals("rigid"),
                "Pillager Outpost root piece drift");
        require(aggregate.equals(union.inflate(AGGREGATE_PADDING)),
                "Pillager Outpost aggregate/piece coordinate closure drift");
    }

    private static void validateEdges(List<Piece> pieces, List<ConnectorEdge> edges) {
        int[] parent = new int[pieces.size()];
        parent[0] = -1;
        for (int index = 0; index < edges.size(); index++) {
            ConnectorEdge edge = edges.get(index);
            int target = index + 1;
            require(edge.targetPiece() == target, "Pillager Outpost target edge order drift at " + index);
            require(edge.sourcePiece() >= 0 && edge.sourcePiece() < target,
                    "Pillager Outpost parent-before-child drift at edge " + index);
            parent[target] = edge.sourcePiece();
            require(edge.officialCanAttach(), "Pillager Outpost officialCanAttach drift at edge " + index);
            require(edge.sourceConnectorOrdinal() >= 0 && edge.targetConnectorOrdinal() >= 0,
                    "Pillager Outpost connector ordinal drift at edge " + index);
            require(edge.sourceName().equals(edge.sourceTarget())
                            && edge.sourceName().equals(edge.targetName())
                            && edge.sourceName().equals(edge.targetTarget()),
                    "Pillager Outpost connector name/target closure drift at edge " + index);
            String expectedPool = switch (edge.sourceName()) {
                case "minecraft:entrance" -> "minecraft:pillager_outpost/towers";
                case "minecraft:plate_entry" -> "minecraft:pillager_outpost/feature_plates";
                case "minecraft:feature" -> "minecraft:pillager_outpost/features";
                default -> throw invalid("unknown Pillager Outpost connector name at edge " + index);
            };
            require(edge.sourcePool().equals(expectedPool),
                    "Pillager Outpost source-pool drift at edge " + index);

            Piece source = pieces.get(edge.sourcePiece());
            Piece child = pieces.get(edge.targetPiece());
            require(source.boundingBox().contains(edge.sourcePosition()),
                    "Pillager Outpost source connector coordinate escaped piece at edge " + index);
            require(child.boundingBox().contains(edge.targetPosition()),
                    "Pillager Outpost target connector coordinate escaped piece at edge " + index);
            require(!child.junctionsInAcceptedOrder().isEmpty(),
                    "Pillager Outpost non-root piece has no parent junction at edge " + index);
            Junction targetParent = child.junctionsInAcceptedOrder().getFirst();
            require(targetParent.destinationProjection().equals(source.projection())
                            && targetParent.sourceX() == edge.sourcePosition().x()
                            && targetParent.sourceZ() == edge.sourcePosition().z(),
                    "Pillager Outpost target-parent junction/edge coordinate drift at edge " + index);
            int matches = 0;
            for (Junction sourceJunction : source.junctionsInAcceptedOrder()) {
                if (sourceJunction.destinationProjection().equals(child.projection())
                        && sourceJunction.deltaY() == -targetParent.deltaY()
                        && sourceJunction.sourceX() == edge.targetPosition().x()
                        && sourceJunction.sourceZ() == edge.targetPosition().z()) {
                    matches++;
                }
            }
            require(matches == 1,
                    "Pillager Outpost source junction/edge reconstruction drift at edge " + index);
        }
        require(edges.size() == pieces.size() - 1,
                "Pillager Outpost graph edge/piece closure drift");
        for (int child = 1; child < pieces.size(); child++) {
            int cursor = child;
            int steps = 0;
            while (cursor != 0) {
                require(cursor > 0 && cursor < pieces.size(),
                        "Pillager Outpost graph root connectivity escaped piece range");
                cursor = parent[cursor];
                require(++steps <= pieces.size(), "Pillager Outpost graph cycle detected");
            }
        }
    }

    private static void validateTerrain(int probeIndex, Box aggregate, TerrainProjectionQueries terrain) {
        require(terrain.boundary().equals(TERRAIN_BOUNDARY),
                "Pillager Outpost terrain boundary drift at " + probeIndex);
        require(terrain.queryCount() == QUERY_COUNTS[probeIndex]
                        && terrain.queryOrder().size() == QUERY_COUNTS[probeIndex],
                "Pillager Outpost terrain query-count drift at " + probeIndex);
        for (int index = 0; index < terrain.queryOrder().size(); index++) {
            ProjectionQuery query = terrain.queryOrder().get(index);
            require(query.ordinal() == index, "Pillager Outpost terrain query order drift");
            require(query.heightmap().equals(HEIGHTMAP) && query.operation().equals(BASE_HEIGHT_OPERATION),
                    "Pillager Outpost terrain query operation drift");
            require(query.x() >= aggregate.minX() && query.x() <= aggregate.maxX()
                            && query.z() >= aggregate.minZ() && query.z() <= aggregate.maxZ(),
                    "Pillager Outpost terrain projection coordinate drift");
            require(query.result() >= aggregate.minY() && query.result() <= aggregate.maxY(),
                    "Pillager Outpost terrain projection result drift");
        }
    }

    private static void validateRng(int probeIndex, Request request, GenerationRng rng) {
        require(rng.continuationNextLongI64().equals(CONTINUATIONS.get(probeIndex)),
                "Pillager Outpost RNG continuation receipt drift at " + probeIndex);
        require(rng.draws().size() == PROBE_DRAW_COUNTS[probeIndex],
                "Pillager Outpost RNG draw cardinality drift at " + probeIndex);
        require(rng.worldgenCount() == WORLDGEN_COUNTS[probeIndex],
                "Pillager Outpost worldgen RNG count drift at " + probeIndex);
        require(rng.state48().equals(FINAL_STATE48[probeIndex])
                        && rng.state48Hex().equals(FINAL_STATE48_HEX[probeIndex]),
                "Pillager Outpost final RNG state receipt drift at " + probeIndex);

        long state = 0L;
        int nextBitsCount = 0;
        int setSeedCount = 0;
        for (int index = 0; index < rng.draws().size(); index++) {
            RngDraw draw = rng.draws().get(index);
            require(draw.ordinal() == index, "Pillager Outpost RNG draw order drift at " + index);
            if (draw instanceof SetSeedDraw setSeed) {
                require(setSeed.operation().equals("setSeed"),
                        "Pillager Outpost RNG setSeed operation drift at " + index);
                require(index == 0 || index == 5,
                        "Pillager Outpost RNG setSeed encounter drift at " + index);
                String expected = index == 0 ? request.worldSeedI64() : SECOND_SET_SEEDS[probeIndex];
                require(setSeed.argument().equals(expected),
                        "Pillager Outpost RNG setSeed argument drift at " + index);
                long seed = parseLong(setSeed.argument(), "Pillager Outpost RNG setSeed");
                state = (seed ^ RNG_MULTIPLIER) & RNG_MASK;
                setSeedCount++;
            } else if (draw instanceof NextBitsDraw next) {
                require(next.operation().equals("nextBits"),
                        "Pillager Outpost RNG nextBits operation drift at " + index);
                require(next.argument() >= 1 && next.argument() <= 32,
                        "Pillager Outpost RNG bit-count drift at " + index);
                state = advanceLegacyState(state);
                int expectedResult = (int) (state >>> (48 - next.argument()));
                require(next.result() == expectedResult,
                        "Pillager Outpost RNG result drift at " + index);
                require(next.state48After().equals(Long.toString(state)),
                        "Pillager Outpost RNG state transcript drift at " + index);
                nextBitsCount++;
            } else {
                throw invalid("unsupported Pillager Outpost RNG draw model");
            }
        }
        require(setSeedCount == 2, "Pillager Outpost RNG setSeed count drift");
        require(nextBitsCount == rng.worldgenCount(),
                "Pillager Outpost RNG draw/worldgen count closure drift");
        require(rng.state48().equals(Long.toString(state))
                        && rng.state48Hex().equals(String.format(Locale.ROOT, "%012x", state)),
                "Pillager Outpost RNG final state/transcript closure drift");

        ArrayList<String> derivedContinuation = new ArrayList<>();
        long continuationState = state;
        for (int index = 0; index < 8; index++) {
            continuationState = advanceLegacyState(continuationState);
            int high = (int) (continuationState >>> 16);
            continuationState = advanceLegacyState(continuationState);
            int low = (int) (continuationState >>> 16);
            long value = ((long) high << 32) + (long) low;
            derivedContinuation.add(Long.toString(value));
        }
        require(derivedContinuation.equals(rng.continuationNextLongI64()),
                "Pillager Outpost RNG continuation/state closure drift");
    }

    private static long advanceLegacyState(long state) {
        return (state * RNG_MULTIPLIER + RNG_ADDEND) & RNG_MASK;
    }

    private static void validatePersistedNbt(int probeIndex, List<Piece> pieces, Request request,
            PersistedNbt persisted) {
        require(persisted.pieces().size() == PIECE_COUNTS[probeIndex],
                "Pillager Outpost persisted piece NBT cardinality drift at " + probeIndex);
        for (int index = 0; index < persisted.pieces().size(); index++) {
            NbtReceipt receipt = persisted.pieces().get(index);
            require(receipt.binaryLength() == PIECE_NBT_LENGTHS.get(probeIndex).get(index),
                    "Pillager Outpost persisted piece NBT length drift at " + probeIndex + "/" + index);
            require(receipt.binarySha256().equals(PIECE_NBT_SHA256.get(probeIndex).get(index)),
                    "Pillager Outpost persisted piece NBT hash drift at " + probeIndex + "/" + index);
            validatePieceNbt(pieces.get(index), receipt.orderedTypedTree());
        }
        NbtReceipt start = persisted.structureStart();
        require(start.binaryLength() == START_NBT_LENGTHS[probeIndex]
                        && start.binarySha256().equals(START_NBT_SHA256[probeIndex]),
                "Pillager Outpost persisted start NBT receipt drift at " + probeIndex);
        validateStartNbt(request, persisted);
    }

    private static void validatePieceNbt(Piece piece, NbtCompound tree) {
        require(entryNames(tree).equals(List.of(
                        "BB", "PosZ", "PosX", "pool_element", "PosY", "rotation", "id",
                        "GD", "O", "ground_level_delta", "junctions")),
                "Pillager Outpost persisted piece NBT key/order drift at " + piece.ordinal());
        requireIntArray(entry(tree, "BB"), piece.boundingBox().snbtIntArray(), "piece BB");
        requireInt(entry(tree, "PosX"), piece.position().x(), "piece PosX");
        requireInt(entry(tree, "PosY"), piece.position().y(), "piece PosY");
        requireInt(entry(tree, "PosZ"), piece.position().z(), "piece PosZ");
        requireString(entry(tree, "rotation"), piece.rotation(), "piece rotation");
        requireString(entry(tree, "id"), "minecraft:jigsaw", "piece id");
        requireInt(entry(tree, "GD"), 0, "piece GD");
        requireInt(entry(tree, "O"), -1, "piece O");
        requireInt(entry(tree, "ground_level_delta"), piece.groundLevelDelta(),
                "piece ground_level_delta");
        validateJunctionNbt(piece, entry(tree, "junctions"));
        validatePoolElementNbt(piece, entry(tree, "pool_element"));
    }

    private static void validateJunctionNbt(Piece piece, NbtTag value) {
        NbtList list = requireList(value, "piece junctions");
        require(list.values().size() == piece.junctionsInAcceptedOrder().size(),
                "Pillager Outpost persisted junction cardinality drift at " + piece.ordinal());
        for (int index = 0; index < list.values().size(); index++) {
            NbtCompound row = requireCompound(list.values().get(index), "piece junction");
            require(entryNames(row).equals(List.of(
                            "source_z", "source_x", "delta_y", "source_ground_y", "dest_proj")),
                    "Pillager Outpost persisted junction key/order drift");
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

    private static void validatePoolElementNbt(Piece piece, NbtTag value) {
        NbtCompound pool = requireCompound(value, "piece pool_element");
        if (piece instanceof TemplatePiece template) {
            validateSinglePoolElementNbt(pool, template.template(), template.processorRegistryKey(),
                    template.projection());
            return;
        }
        ListPiece list = (ListPiece) piece;
        require(entryNames(pool).equals(List.of("elements", "projection", "element_type")),
                "Pillager Outpost persisted list-pool element key/order drift");
        requireString(entry(pool, "projection"), list.projection(), "list-pool projection");
        requireString(entry(pool, "element_type"), "minecraft:list_pool_element",
                "list-pool element_type");
        NbtList elements = requireList(entry(pool, "elements"), "list-pool elements");
        require(elements.values().size() == list.childrenInPlacementOrder().size(),
                "Pillager Outpost persisted list-pool child cardinality drift");
        for (int index = 0; index < elements.values().size(); index++) {
            PlacementChild child = list.childrenInPlacementOrder().get(index);
            validateSinglePoolElementNbt(requireCompound(elements.values().get(index), "list child"),
                    child.template(), child.processorRegistryKey(), list.projection());
        }
    }

    private static void validateSinglePoolElementNbt(NbtCompound pool, String template,
            String processorRegistryKey, String projection) {
        require(entryNames(pool).equals(List.of("location", "processors", "projection", "element_type")),
                "Pillager Outpost persisted single-pool element key/order drift");
        requireString(entry(pool, "location"), template, "single-pool location");
        requireString(entry(pool, "projection"), projection, "single-pool projection");
        requireString(entry(pool, "element_type"), "minecraft:legacy_single_pool_element",
                "single-pool element_type");
        NbtTag processors = entry(pool, "processors");
        if (processorRegistryKey.equals("inline")) {
            NbtCompound inline = requireCompound(processors, "inline processors");
            require(entryNames(inline).equals(List.of("processors")),
                    "Pillager Outpost inline processor NBT drift");
            require(requireList(entry(inline, "processors"), "inline processor list").values().isEmpty(),
                    "Pillager Outpost inline processor list is not empty");
        } else {
            requireString(processors, processorRegistryKey, "registered processors");
        }
    }

    private static void validateStartNbt(Request request, PersistedNbt persisted) {
        NbtCompound start = persisted.structureStart().orderedTypedTree();
        require(entryNames(start).equals(List.of("references", "ChunkZ", "id", "Children", "ChunkX")),
                "Pillager Outpost persisted start NBT key/order drift");
        requireInt(entry(start, "references"), 0, "start references");
        requireInt(entry(start, "ChunkX"), request.chunkX(), "start ChunkX");
        requireInt(entry(start, "ChunkZ"), request.chunkZ(), "start ChunkZ");
        requireString(entry(start, "id"), request.structureKey(), "start id");
        NbtList children = requireList(entry(start, "Children"), "start Children");
        require(children.values().size() == persisted.pieces().size(),
                "Pillager Outpost start Children cardinality drift");
        for (int index = 0; index < children.values().size(); index++) {
            require(children.values().get(index).equals(persisted.pieces().get(index).orderedTypedTree()),
                    "Pillager Outpost start/piece typed-tree encounter-order drift at " + index);
        }
    }

    private static void validateNbtReceipt(NbtReceipt receipt) {
        require(receipt.binaryLength() > 0 && receipt.binaryLength() <= 16_384,
                "persisted NBT binary length outside bound");
        require(receipt.binaryHex().length() == Math.multiplyExact(receipt.binaryLength(), 2),
                "persisted NBT binary hex length drift");
        require(isLowerHex(receipt.binaryHex()), "persisted NBT binary hex is not lowercase hex");
        require(receipt.binarySha256().length() == 64 && isLowerHex(receipt.binarySha256()),
                "persisted NBT SHA-256 format drift");
        byte[] binary;
        try {
            binary = HexFormat.of().parseHex(receipt.binaryHex());
        } catch (IllegalArgumentException error) {
            throw invalid("persisted NBT binary hex is malformed", error);
        }
        require(binary.length == receipt.binaryLength(), "persisted NBT decoded length drift");
        require(receipt.binarySha256().equals(sha256(binary)), "persisted NBT binary SHA-256 drift");
        validateBinaryNbt(binary, receipt.orderedTypedTree());
    }

    private static void validateBinaryNbt(byte[] binary, NbtCompound tree) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(binary))) {
            require(input.readUnsignedByte() == 10, "persisted binary NBT root type drift");
            require(input.readUTF().isEmpty(), "persisted binary NBT root name drift");
            validateBinaryPayload(input, tree, 0);
            require(input.read() == -1, "trailing persisted binary NBT data");
        } catch (EOFException error) {
            throw invalid("truncated persisted binary NBT", error);
        } catch (IOException error) {
            throw invalid("malformed persisted binary NBT", error);
        }
    }

    private static void validateBinaryPayload(DataInputStream input, NbtTag expected, int depth)
            throws IOException {
        require(depth <= 32, "persisted binary NBT depth exceeded");
        if (expected instanceof NbtCompound compound) {
            for (NbtEntry entry : compound.entries()) {
                int type = input.readUnsignedByte();
                require(type != 0 && type == entry.tag().typeId(),
                        "persisted binary/typed NBT child type drift");
                require(input.readUTF().equals(entry.name()),
                        "persisted binary/typed NBT child name/order drift");
                validateBinaryPayload(input, entry.tag(), depth + 1);
            }
            require(input.readUnsignedByte() == 0,
                    "persisted binary/typed NBT compound terminator drift");
            return;
        }
        if (expected instanceof NbtList list) {
            int elementType = input.readUnsignedByte();
            int size = input.readInt();
            require(size >= 0 && size == list.values().size(),
                    "persisted binary/typed NBT list length drift");
            for (NbtTag value : list.values()) {
                require(value.typeId() == elementType,
                        "persisted binary/typed NBT list element-type drift");
                validateBinaryPayload(input, value, depth + 1);
            }
            return;
        }
        if (expected instanceof NbtInt integer) {
            require(integer.snbtValue().equals(Integer.toString(input.readInt())),
                    "persisted binary/typed NBT INT value drift");
            return;
        }
        if (expected instanceof NbtString string) {
            String raw = input.readUTF();
            require(string.snbtValue().equals(quoteSnbtString(raw)),
                    "persisted binary/typed NBT STRING value drift");
            return;
        }
        if (expected instanceof NbtIntArray array) {
            int length = input.readInt();
            require(length >= 0 && length <= 256, "persisted binary NBT INT[] length outside bound");
            StringBuilder snbt = new StringBuilder("[I;");
            for (int index = 0; index < length; index++) {
                if (index > 0) snbt.append(',');
                snbt.append(input.readInt());
            }
            snbt.append(']');
            require(array.snbtValue().contentEquals(snbt),
                    "persisted binary/typed NBT INT[] value drift");
            return;
        }
        throw invalid("unsupported persisted binary NBT typed node");
    }

    private static String quoteSnbtString(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            require(current >= 0x20 && current != '"' && current != '\\',
                    "unsupported persisted NBT string escaping in accepted evidence");
            result.append(current);
        }
        return result.append('"').toString();
    }

    private static List<String> entryNames(NbtCompound compound) {
        return compound.entries().stream().map(NbtEntry::name).toList();
    }

    private static NbtTag entry(NbtCompound compound, String name) {
        NbtTag result = null;
        for (NbtEntry entry : compound.entries()) {
            if (!entry.name().equals(name)) continue;
            require(result == null, "duplicate persisted NBT entry: " + name);
            result = entry.tag();
        }
        require(result != null, "missing persisted NBT entry: " + name);
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
        require(value instanceof NbtInt integer && integer.snbtValue().equals(Integer.toString(expected)),
                "Pillager Outpost persisted " + label + " drift");
    }

    private static void requireIntArray(NbtTag value, String expected, String label) {
        require(value instanceof NbtIntArray array && array.snbtValue().equals(expected),
                "Pillager Outpost persisted " + label + " drift");
    }

    private static void requireString(NbtTag value, String expected, String label) {
        require(value instanceof NbtString string
                        && string.snbtValue().equals(quoteSnbtString(expected)),
                "Pillager Outpost persisted " + label + " drift");
    }

    private static long parseLong(String value, String label) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw invalid(label + " is not signed i64", error);
        }
    }

    private static boolean isLowerHex(String value) {
        if (value.isEmpty()) return false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!((current >= '0' && current <= '9') || (current >= 'a' && current <= 'f'))) return false;
        }
        return true;
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

    record Corpus(int javaVersion, List<Probe> probes, int schema, String serverVersion) {
        Corpus {
            Objects.requireNonNull(probes); Objects.requireNonNull(serverVersion);
            probes = List.copyOf(probes);
        }
    }

    record Probe(List<ConnectorEdge> acceptedConnectorEdges, GenerationRng generationRng,
            GenerationStart generationStart, PersistedNbt persistedNbt,
            List<Piece> piecesInAcceptedOrder, Request request,
            TerrainProjectionQueries terrainProjectionQueries) {
        Probe {
            acceptedConnectorEdges = List.copyOf(acceptedConnectorEdges);
            piecesInAcceptedOrder = List.copyOf(piecesInAcceptedOrder);
            Objects.requireNonNull(generationRng); Objects.requireNonNull(generationStart);
            Objects.requireNonNull(persistedNbt); Objects.requireNonNull(request);
            Objects.requireNonNull(terrainProjectionQueries);
        }
    }

    record Request(int chunkX, int chunkZ, String structureKey, String worldSeedI64) {
        Request { Objects.requireNonNull(structureKey); Objects.requireNonNull(worldSeedI64); }
    }

    record GenerationStart(Box aggregateBoundingBox, int pieceCount, String startPool,
            Vec3i stubPosition) {
        GenerationStart {
            Objects.requireNonNull(aggregateBoundingBox); Objects.requireNonNull(startPool);
            Objects.requireNonNull(stubPosition);
        }
    }

    record GenerationRng(List<String> continuationNextLongI64, List<RngDraw> draws,
            String state48, String state48Hex, int worldgenCount) {
        GenerationRng {
            continuationNextLongI64 = List.copyOf(continuationNextLongI64);
            draws = List.copyOf(draws);
            Objects.requireNonNull(state48); Objects.requireNonNull(state48Hex);
        }
    }

    sealed interface RngDraw permits SetSeedDraw, NextBitsDraw {
        String operation();
        int ordinal();
    }

    record SetSeedDraw(String argument, String operation, int ordinal) implements RngDraw {
        SetSeedDraw { Objects.requireNonNull(argument); Objects.requireNonNull(operation); }
    }

    record NextBitsDraw(int argument, String operation, int ordinal, int result,
            String state48After) implements RngDraw {
        NextBitsDraw { Objects.requireNonNull(operation); Objects.requireNonNull(state48After); }
    }

    sealed interface Piece permits TemplatePiece, ListPiece {
        Box boundingBox();
        int groundLevelDelta();
        List<Junction> junctionsInAcceptedOrder();
        int ordinal();
        Vec3i position();
        String projection();
        String rotation();
    }

    record TemplatePiece(Box boundingBox, int groundLevelDelta,
            List<Junction> junctionsInAcceptedOrder, int ordinal, Vec3i position,
            List<String> processorOrder, String processorRegistryKey, String projection,
            String rotation, String template) implements Piece {
        TemplatePiece {
            junctionsInAcceptedOrder = List.copyOf(junctionsInAcceptedOrder);
            processorOrder = List.copyOf(processorOrder);
            Objects.requireNonNull(boundingBox); Objects.requireNonNull(position);
            Objects.requireNonNull(processorRegistryKey); Objects.requireNonNull(projection);
            Objects.requireNonNull(rotation); Objects.requireNonNull(template);
        }
    }

    record ListPiece(Box boundingBox, List<PlacementChild> childrenInPlacementOrder,
            int groundLevelDelta, List<Junction> junctionsInAcceptedOrder, int ordinal,
            Vec3i position, String projection, String rotation) implements Piece {
        ListPiece {
            childrenInPlacementOrder = List.copyOf(childrenInPlacementOrder);
            junctionsInAcceptedOrder = List.copyOf(junctionsInAcceptedOrder);
            Objects.requireNonNull(boundingBox); Objects.requireNonNull(position);
            Objects.requireNonNull(projection); Objects.requireNonNull(rotation);
        }
    }

    record PlacementChild(List<String> processorOrder, String processorRegistryKey, String template) {
        PlacementChild {
            processorOrder = List.copyOf(processorOrder);
            Objects.requireNonNull(processorRegistryKey); Objects.requireNonNull(template);
        }
    }

    record Junction(int deltaY, String destinationProjection, int sourceGroundY, int sourceX,
            int sourceZ) {
        Junction { Objects.requireNonNull(destinationProjection); }
    }

    record ConnectorEdge(boolean officialCanAttach, int sourceConnectorOrdinal, String sourceName,
            int sourcePiece, String sourcePool, Vec3i sourcePosition, String sourceTarget,
            int targetConnectorOrdinal, String targetName, int targetPiece, Vec3i targetPosition,
            String targetTarget) {
        ConnectorEdge {
            Objects.requireNonNull(sourceName); Objects.requireNonNull(sourcePool);
            Objects.requireNonNull(sourcePosition); Objects.requireNonNull(sourceTarget);
            Objects.requireNonNull(targetName); Objects.requireNonNull(targetPosition);
            Objects.requireNonNull(targetTarget);
        }
    }

    record TerrainProjectionQueries(String boundary, int queryCount,
            List<ProjectionQuery> queryOrder) {
        TerrainProjectionQueries {
            Objects.requireNonNull(boundary); queryOrder = List.copyOf(queryOrder);
        }
    }

    record ProjectionQuery(String heightmap, String operation, int ordinal, int result, int x, int z) {
        ProjectionQuery { Objects.requireNonNull(heightmap); Objects.requireNonNull(operation); }
    }

    record PersistedNbt(List<NbtReceipt> pieces, NbtReceipt structureStart) {
        PersistedNbt { pieces = List.copyOf(pieces); Objects.requireNonNull(structureStart); }
    }

    record NbtReceipt(String binaryHex, int binaryLength, String binarySha256,
            NbtCompound orderedTypedTree) {
        NbtReceipt {
            Objects.requireNonNull(binaryHex); Objects.requireNonNull(binarySha256);
            Objects.requireNonNull(orderedTypedTree);
        }
    }

    sealed interface NbtTag permits NbtCompound, NbtList, NbtInt, NbtString, NbtIntArray {
        String type();
        int typeId();
    }

    record NbtCompound(String type, int typeId, List<NbtEntry> entries) implements NbtTag {
        NbtCompound { Objects.requireNonNull(type); entries = List.copyOf(entries); }
    }

    record NbtList(String type, int typeId, List<NbtTag> values) implements NbtTag {
        NbtList { Objects.requireNonNull(type); values = List.copyOf(values); }
    }

    record NbtInt(String snbtValue, String type, int typeId) implements NbtTag {
        NbtInt { Objects.requireNonNull(snbtValue); Objects.requireNonNull(type); }
    }

    record NbtString(String snbtValue, String type, int typeId) implements NbtTag {
        NbtString { Objects.requireNonNull(snbtValue); Objects.requireNonNull(type); }
    }

    record NbtIntArray(String snbtValue, String type, int typeId) implements NbtTag {
        NbtIntArray { Objects.requireNonNull(snbtValue); Objects.requireNonNull(type); }
    }

    record NbtEntry(String name, NbtTag tag) {
        NbtEntry { Objects.requireNonNull(name); Objects.requireNonNull(tag); }
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
        String snbtIntArray() {
            return "[I;" + minX + ',' + minY + ',' + minZ + ',' + maxX + ',' + maxY + ',' + maxZ + ']';
        }
    }

    private static final class NbtBudget {
        private int nodes;
        void consume(int depth) {
            require(depth <= 32, "persisted NBT typed-tree depth exceeded");
            require(++nodes <= 10_000, "persisted NBT typed-tree node bound exceeded");
        }
    }

    private static final class JsonReader {
        private static final int MAX_STRING_CHARS = 65_536;
        private final String text;
        private int offset;

        JsonReader(byte[] bytes) {
            try {
                text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException error) {
                throw invalid("Pillager Outpost start-graph is not valid UTF-8", error);
            }
        }

        int mark() { skipWhitespace(); return offset; }

        String rawSha256(int start, int end) {
            require(start >= 0 && end >= start && end <= text.length(),
                    "Pillager Outpost raw receipt bounds drift");
            return sha256(text.substring(start, end).getBytes(StandardCharsets.UTF_8));
        }

        void beginObject() { expect('{'); }
        void endObject() { expect('}'); }
        void beginArray() { expect('['); }
        void endArray() { expect(']'); }

        void field(String expected, int index) {
            String actual = readFieldName(index);
            if (!expected.equals(actual)) {
                throw invalid("Pillager Outpost start-graph JSON field order/schema drift: expected "
                        + expected + ", got " + actual);
            }
        }

        String readFieldName(int index) {
            skipWhitespace();
            if (index > 0) {
                expectRaw(',');
                skipWhitespace();
                if (peek() == '}') throw invalid("Pillager Outpost JSON trailing object comma");
            }
            String name = readString();
            expect(':');
            return name;
        }

        boolean nextArrayValue(int index) {
            skipWhitespace();
            if (index == 0) return peek() != ']';
            if (peek() == ']') return false;
            expectRaw(',');
            skipWhitespace();
            if (peek() == ']') throw invalid("Pillager Outpost JSON trailing array comma");
            return true;
        }

        char peekValue() { skipWhitespace(); return peek(); }

        String readString() {
            skipWhitespace();
            expectRaw('"');
            StringBuilder value = new StringBuilder();
            while (offset < text.length()) {
                char current = text.charAt(offset++);
                if (current == '"') return value.toString();
                if (current < 0x20) throw invalid("Pillager Outpost JSON control character");
                if (current != '\\') {
                    if (Character.isSurrogate(current)) {
                        require(Character.isHighSurrogate(current) && offset < text.length()
                                        && Character.isLowSurrogate(text.charAt(offset)),
                                "malformed Pillager Outpost JSON surrogate");
                        value.append(current).append(text.charAt(offset++));
                    } else {
                        value.append(current);
                    }
                } else {
                    if (offset >= text.length()) throw invalid("truncated Pillager Outpost JSON escape");
                    char escaped = text.charAt(offset++);
                    switch (escaped) {
                        case '"', '\\', '/' -> value.append(escaped);
                        case 'b' -> value.append('\b');
                        case 'f' -> value.append('\f');
                        case 'n' -> value.append('\n');
                        case 'r' -> value.append('\r');
                        case 't' -> value.append('\t');
                        case 'u' -> appendUnicodeEscape(value);
                        default -> throw invalid("unknown Pillager Outpost JSON escape");
                    }
                }
                require(value.length() <= MAX_STRING_CHARS,
                        "Pillager Outpost JSON string exceeds bound");
            }
            throw invalid("truncated Pillager Outpost JSON string");
        }

        private void appendUnicodeEscape(StringBuilder value) {
            char first = readUnicodeEscape();
            if (!Character.isSurrogate(first)) {
                value.append(first);
                return;
            }
            require(Character.isHighSurrogate(first), "malformed Pillager Outpost Unicode surrogate");
            require(offset + 2 <= text.length() && text.charAt(offset) == '\\'
                            && text.charAt(offset + 1) == 'u',
                    "missing Pillager Outpost low Unicode surrogate");
            offset += 2;
            char second = readUnicodeEscape();
            require(Character.isLowSurrogate(second), "malformed Pillager Outpost low Unicode surrogate");
            value.append(first).append(second);
        }

        private char readUnicodeEscape() {
            require(offset + 4 <= text.length(), "truncated Pillager Outpost Unicode escape");
            int value = 0;
            for (int index = 0; index < 4; index++) {
                int digit = Character.digit(text.charAt(offset++), 16);
                require(digit >= 0, "malformed Pillager Outpost Unicode escape");
                value = value * 16 + digit;
            }
            return (char) value;
        }

        int readInt() {
            String value = readNumber();
            require(value.indexOf('.') < 0 && value.indexOf('e') < 0 && value.indexOf('E') < 0,
                    "Pillager Outpost JSON integer type drift");
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException error) {
                throw invalid("Pillager Outpost JSON integer overflow", error);
            }
        }

        boolean readBoolean() {
            skipWhitespace();
            if (text.startsWith("true", offset)) { offset += 4; return true; }
            if (text.startsWith("false", offset)) { offset += 5; return false; }
            throw invalid("Pillager Outpost JSON boolean type drift");
        }

        private String readNumber() {
            skipWhitespace();
            int start = offset;
            if (peek() == '-') offset++;
            require(offset < text.length(), "truncated Pillager Outpost JSON number");
            char first = text.charAt(offset);
            if (first == '0') {
                offset++;
                require(offset >= text.length() || !Character.isDigit(text.charAt(offset)),
                        "Pillager Outpost JSON leading zero");
            } else if (first >= '1' && first <= '9') {
                do { offset++; } while (offset < text.length() && Character.isDigit(text.charAt(offset)));
            } else {
                throw invalid("malformed Pillager Outpost JSON number");
            }
            if (offset < text.length() && text.charAt(offset) == '.') {
                offset++;
                int fraction = offset;
                while (offset < text.length() && Character.isDigit(text.charAt(offset))) offset++;
                require(offset > fraction, "malformed Pillager Outpost JSON fraction");
            }
            if (offset < text.length() && (text.charAt(offset) == 'e' || text.charAt(offset) == 'E')) {
                offset++;
                if (offset < text.length() && (text.charAt(offset) == '+' || text.charAt(offset) == '-')) offset++;
                int exponent = offset;
                while (offset < text.length() && Character.isDigit(text.charAt(offset))) offset++;
                require(offset > exponent, "malformed Pillager Outpost JSON exponent");
            }
            return text.substring(start, offset);
        }

        private void expect(char expected) {
            skipWhitespace();
            expectRaw(expected);
        }

        private void expectRaw(char expected) {
            if (offset >= text.length() || text.charAt(offset) != expected) {
                throw invalid("Pillager Outpost JSON syntax drift near offset " + offset
                        + ": expected " + expected);
            }
            offset++;
        }

        private char peek() { return offset >= text.length() ? '\0' : text.charAt(offset); }

        private void skipWhitespace() {
            while (offset < text.length()) {
                char value = text.charAt(offset);
                if (value != ' ' && value != '\n' && value != '\r' && value != '\t') break;
                offset++;
            }
        }

        void finish() {
            skipWhitespace();
            require(offset == text.length(), "trailing Pillager Outpost start-graph JSON data");
        }
    }
}
