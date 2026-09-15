package com.gameexpert.terrain.mc.structure;

import static com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ElementType;
import static com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.AcceptedEdge;
import static com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.ConnectorIdentity;
import static com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.PiecePlacement;
import static com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.PlannerRngReceipt;
import static com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.RandomDraw;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Canonical persisted authority for procedurally generated 26.3 Pillager Outpost starts.
 *
 * <p>The authority is repository-generated data, not Mojang template NBT. It is appended as one
 * final byte-array tag to the root persisted jigsaw piece, which means the global STR263C1 schema
 * remains byte-for-byte unchanged while an Outpost start can carry its complete traced planner
 * provenance through the existing opaque piece-NBT boundary. The raw structure-start predecessor
 * remains untouched so its exact references=0 to references=1 transition stays authoritative.</p>
 */
final class Mc263PillagerOutpostPersistedAuthority {
    static final String NBT_TAG = "gameexpert:outpost_authority";
    static final String FORMAT = "OUT263A1";
    static final int SCHEMA = 1;

    private static final byte[] MAGIC = FORMAT.getBytes(StandardCharsets.US_ASCII);
    private static final int DIGEST_BYTES = 32;
    private static final int MAX_PIECES = 128;
    private static final int MAX_EDGES = 127;
    private static final int MAX_QUERIES = 4_096;
    private static final int MAX_DRAWS = 100_000;
    private static final int MAX_JUNCTIONS = 128;
    private static final int MAX_COMPONENTS = 8;
    private static final int CONTINUATION_WORDS = 8;
    private static final long LEGACY_MULTIPLIER = 0x5DEECE66DL;
    private static final long LEGACY_ADDEND = 0xBL;
    private static final long LEGACY_MASK = (1L << 48) - 1;

    private Mc263PillagerOutpostPersistedAuthority() { }

    /** Binds a freshly generated start without changing its raw structure-start predecessor. */
    static Mc263PillagerOutpostProducer.Carrier bind(
            Mc263PillagerOutpostProducer.Start generated) {
        Objects.requireNonNull(generated, "generated Pillager Outpost start");
        Mc263PillagerOutpostProducer.Carrier carrier = generated.carrier();
        require(Mc263PillagerOutpostProducer.CARRIER_FORMAT.equals(carrier.format()),
                "unknown Pillager Outpost carrier format");
        require(!carrier.pieces().isEmpty(), "Pillager Outpost carrier has no persisted pieces");
        byte[] payload = encode(generated);
        ArrayList<Mc263PillagerOutpostProducer.BinaryNbt> pieces =
                new ArrayList<>(carrier.pieces());
        byte[] root = pieces.getFirst().bytes();
        require(extract(root, false) == null,
                "Pillager Outpost root piece already carries authority");
        pieces.set(0, new Mc263PillagerOutpostProducer.BinaryNbt(attach(root, payload)));
        return new Mc263PillagerOutpostProducer.Carrier(carrier.format(), pieces,
                carrier.structureStart());
    }

    /**
     * Reconstructs the E3M15 settlement authority from persisted root-piece NBT plus the exact raw
     * structure-start predecessor. No coordinate lookup, terrain query, or RNG call occurs here.
     */
    static Reloaded reload(byte[] persistedRootPieceNbt, byte[] predecessor) {
        Objects.requireNonNull(persistedRootPieceNbt, "persisted Pillager Outpost root piece");
        Objects.requireNonNull(predecessor, "Pillager Outpost predecessor");
        Extraction extraction = extract(persistedRootPieceNbt, true);
        Parsed parsed = decode(extraction.payload(), predecessor);
        return new Reloaded(parsed.worldSeed(), parsed.chunkX(), parsed.chunkZ(),
                extraction.stripped(), extraction.payload(), parsed.startIdentitySha256(),
                parsed.predecessorSha256(), parsed.predecessorLength(),
                parsed.successorSha256());
    }

    /** Full equality against a live procedural start; used before any settlement side effects. */
    static void authenticate(Mc263PillagerOutpostProducer.Start start) {
        Objects.requireNonNull(start, "Pillager Outpost start");
        require(!start.carrier().pieces().isEmpty(), "Pillager Outpost carrier has no pieces");
        Reloaded reloaded = reload(start.carrier().pieces().getFirst().bytes(),
                start.carrier().structureStart().bytes());
        require(reloaded.worldSeed() == start.worldSeed()
                        && reloaded.chunkX() == start.chunkX()
                        && reloaded.chunkZ() == start.chunkZ(),
                "Pillager Outpost persisted authority request drift");
        require(Arrays.equals(reloaded.authorityPayload(), encode(start)),
                "Pillager Outpost persisted authority facts drift");
    }

    static byte[] strippedRootPiece(byte[] persistedRootPieceNbt) {
        return extract(Objects.requireNonNull(persistedRootPieceNbt), true).stripped();
    }

    static String startIdentitySha256(Mc263PillagerOutpostProducer.Start start) {
        return sha256Hex(startIdentityBytes(start));
    }

    /** Existing OUT263S1 identity encoding retained exactly for E3M15 compatibility. */
    static byte[] startIdentityBytes(Mc263PillagerOutpostProducer.Start start) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                writeIdentity(out, start);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static byte[] encode(Mc263PillagerOutpostProducer.Start start) {
        sourcePreflight();
        try {
            ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bodyBytes)) {
                out.write(MAGIC);
                out.writeInt(SCHEMA);
                writeString(out, Mc263JigsawStructureCatalog.VERSION);
                writeString(out, Mc263JigsawStructureCatalog.SERVER_SHA1);
                writeString(out, Mc263PillagerOutpostGrammarData.PAYLOAD_SHA256);
                writeString(out, Mc263PillagerOutpostGrammarData.GZIP_SHA256);
                writeString(out, Mc263PillagerOutpostGrammar.RECEIPT_ID);
                out.writeInt(Mc263PillagerOutpostGrammar.SCHEMA);
                out.writeInt(Mc263PillagerOutpostGrammar.JAVA_VERSION);
                writeString(out, Mc263PillagerOutpostGrammar.SERVER_VERSION);
                writeString(out, Mc263PillagerOutpostProducer.CARRIER_FORMAT);

                out.writeLong(start.worldSeed());
                out.writeInt(start.chunkX());
                out.writeInt(start.chunkZ());
                writeProducerBox(out, start.aggregateBoundingBox());
                writeProducerVec(out, start.stubPosition());
                var plan = start.executionPlan();
                out.writeInt(plan.centerX()); out.writeInt(plan.centerY()); out.writeInt(plan.centerZ());
                writeString(out, plan.rotation().name());

                List<PiecePlacement> pieces = plan.pieces();
                out.writeInt(pieces.size());
                for (int ordinal = 0; ordinal < pieces.size(); ordinal++) {
                    PiecePlacement piece = pieces.get(ordinal);
                    out.writeInt(ordinal);
                    out.writeInt(piece.depth());
                    writeString(out, piece.type().name());
                    writeString(out, piece.elementKey());
                    out.writeInt(piece.components().size());
                    for (String component : piece.components()) writeString(out, component);
                    writeString(out, piece.processor());
                    out.writeInt(piece.originX()); out.writeInt(piece.originY()); out.writeInt(piece.originZ());
                    out.writeInt(piece.groundLevelDelta());
                    writeString(out, piece.rotation().name());
                    writeString(out, piece.projection().name());
                    writeBounds(out, piece.bounds());
                    List<String> templates = templates(piece);
                    out.writeInt(templates.size());
                    for (String template : templates) {
                        writeString(out, template);
                        writeString(out, processorForTemplate(template));
                    }
                    out.writeInt(piece.junctions().size());
                    for (var junction : piece.junctions()) {
                        out.writeInt(junction.sourceX()); out.writeInt(junction.sourceGroundY());
                        out.writeInt(junction.sourceZ()); out.writeInt(junction.deltaY());
                        writeString(out, junction.destinationProjection().name());
                    }
                }

                out.writeInt(start.acceptedEdges().size());
                for (AcceptedEdge edge : start.acceptedEdges()) {
                    out.writeInt(edge.parentPieceOrdinal());
                    out.writeInt(edge.childPieceOrdinal());
                    writeConnector(out, edge.sourceConnector());
                    writeConnector(out, edge.targetConnector());
                    writeString(out, edge.selectedSourcePool());
                    writeString(out, edge.resolvedAliasTarget());
                }

                out.writeInt(start.projectionQueries().size());
                for (Mc263PillagerOutpostProducer.ProjectionQuery query : start.projectionQueries()) {
                    out.writeInt(query.ordinal()); out.writeInt(query.x()); out.writeInt(query.z());
                    out.writeInt(query.result()); writeString(out, query.heightmap());
                }

                PlannerRngReceipt rng = start.generationRng();
                out.writeLong(rng.state48()); out.writeInt(rng.worldgenCount());
                out.writeInt(rng.draws().size());
                for (RandomDraw draw : rng.draws()) {
                    out.writeInt(draw.ordinal());
                    if ("setSeed".equals(draw.operation())) {
                        out.writeByte(0); out.writeLong(Objects.requireNonNull(draw.seedArgument()));
                    } else if ("nextBits".equals(draw.operation())) {
                        out.writeByte(1); out.writeInt(Objects.requireNonNull(draw.bitsArgument()));
                        out.writeInt(Objects.requireNonNull(draw.result()));
                        out.writeLong(Objects.requireNonNull(draw.state48After()));
                    } else {
                        throw new IllegalArgumentException(
                                "unknown Pillager Outpost planner RNG operation: " + draw.operation());
                    }
                }
                out.writeInt(rng.continuationNextLongI64().size());
                for (long value : rng.continuationNextLongI64()) out.writeLong(value);

                byte[] predecessor = start.carrier().structureStart().bytes();
                ReferenceTransition transition = referenceTransition(predecessor);
                out.writeInt(predecessor.length);
                out.write(hexBytes(sha256Hex(predecessor)));
                out.write(hexBytes(transition.successorSha256()));
                out.write(hexBytes(startIdentitySha256(start)));
            }
            byte[] body = bodyBytes.toByteArray();
            ByteArrayOutputStream complete = new ByteArrayOutputStream(body.length + DIGEST_BYTES);
            complete.write(body);
            complete.write(sha256(body));
            return complete.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Parsed decode(byte[] payload, byte[] predecessor) {
        sourcePreflight();
        require(payload.length > MAGIC.length + 4 + DIGEST_BYTES,
                "truncated Pillager Outpost persisted authority");
        int bodyLength = payload.length - DIGEST_BYTES;
        byte[] body = Arrays.copyOf(payload, bodyLength);
        byte[] trailer = Arrays.copyOfRange(payload, bodyLength, payload.length);
        require(MessageDigest.isEqual(sha256(body), trailer),
                "Pillager Outpost persisted authority digest drift");

        Cursor in = new Cursor(body);
        in.expect(MAGIC, "authority magic");
        require(in.i32() == SCHEMA, "Pillager Outpost persisted authority schema drift");
        require(in.string().equals(Mc263JigsawStructureCatalog.VERSION), "jigsaw version drift");
        require(in.string().equals(Mc263JigsawStructureCatalog.SERVER_SHA1), "server source hash drift");
        require(in.string().equals(Mc263PillagerOutpostGrammarData.PAYLOAD_SHA256),
                "grammar payload source hash drift");
        require(in.string().equals(Mc263PillagerOutpostGrammarData.GZIP_SHA256),
                "grammar gzip source hash drift");
        require(in.string().equals(Mc263PillagerOutpostGrammar.RECEIPT_ID), "grammar receipt drift");
        require(in.i32() == Mc263PillagerOutpostGrammar.SCHEMA, "grammar schema drift");
        require(in.i32() == Mc263PillagerOutpostGrammar.JAVA_VERSION, "grammar Java version drift");
        require(in.string().equals(Mc263PillagerOutpostGrammar.SERVER_VERSION), "grammar server version drift");
        require(in.string().equals(Mc263PillagerOutpostProducer.CARRIER_FORMAT), "carrier format drift");

        long worldSeed = in.i64();
        int chunkX = in.i32(), chunkZ = in.i32();
        int[] aggregate = in.box();
        int[] stub = in.vec();
        int centerX = in.i32(), centerY = in.i32(), centerZ = in.i32();
        String planRotation = rotation(in.string());

        ByteArrayOutputStream identityBytes = new ByteArrayOutputStream();
        try (DataOutputStream identity = new DataOutputStream(identityBytes)) {
            identity.write("OUT263S1".getBytes(StandardCharsets.US_ASCII));
            identity.writeLong(worldSeed); identity.writeInt(chunkX); identity.writeInt(chunkZ);
            writeBox(identity, aggregate); writeVec(identity, stub);

            int pieceCount = in.count(MAX_PIECES, "piece");
            require(pieceCount > 0, "Pillager Outpost persisted authority has no pieces");
            identity.writeInt(pieceCount);
            for (int ordinal = 0; ordinal < pieceCount; ordinal++) {
                require(in.i32() == ordinal, "Pillager Outpost persisted piece order drift");
                int depth = in.i32();
                require(depth >= 0 && depth <= 32, "Pillager Outpost persisted piece depth drift");
                String type = in.string();
                require(type.equals(ElementType.LEGACY_SINGLE.name()) || type.equals(ElementType.LIST.name()),
                        "Pillager Outpost persisted piece type drift");
                String element = key(in.string(), "piece element");
                int componentCount = in.count(MAX_COMPONENTS, "component");
                ArrayList<String> components = new ArrayList<>(componentCount);
                for (int i = 0; i < componentCount; i++) components.add(key(in.string(), "component"));
                require((type.equals(ElementType.LEGACY_SINGLE.name()) && componentCount == 0)
                                || (type.equals(ElementType.LIST.name()) && componentCount > 0),
                        "Pillager Outpost persisted component cardinality drift");
                String declaredProcessor = in.string();
                require(declaredProcessor.isEmpty() || declaredProcessor.equals("minecraft:outpost_rot"),
                        "Pillager Outpost persisted declared processor drift");
                int originX = in.i32(), originY = in.i32(), originZ = in.i32();
                int groundDelta = in.i32();
                String pieceRotation = rotation(in.string());
                String projection = projection(in.string());
                int[] bounds = in.box();
                int templateCount = in.count(MAX_COMPONENTS, "template processor");
                List<String> expectedTemplates = type.equals(ElementType.LIST.name())
                        ? components : List.of(element);
                require(templateCount == expectedTemplates.size(),
                        "Pillager Outpost persisted template cardinality drift");
                for (int i = 0; i < templateCount; i++) {
                    String template = key(in.string(), "template");
                    require(template.equals(expectedTemplates.get(i)),
                            "Pillager Outpost persisted template order drift");
                    String processor = in.string();
                    require(processor.equals(processorForTemplate(template)),
                            "Pillager Outpost persisted template processor drift");
                }
                int junctionCount = in.count(MAX_JUNCTIONS, "junction");

                identity.writeInt(ordinal);
                identity.writeInt(originX); identity.writeInt(originY); identity.writeInt(originZ);
                identity.writeInt(groundDelta);
                identity.writeByte(rotationCode(pieceRotation));
                identity.writeByte(projectionCode(projection));
                writeBox(identity, bounds);
                boolean list = type.equals(ElementType.LIST.name());
                identity.writeByte(list ? 1 : 0);
                identity.writeInt(expectedTemplates.size());
                for (String template : expectedTemplates) writeString(identity, template);
                identity.writeInt(junctionCount);
                for (int j = 0; j < junctionCount; j++) {
                    int sourceX = in.i32(), sourceGroundY = in.i32(), sourceZ = in.i32();
                    int deltaY = in.i32();
                    String destinationProjection = projection(in.string());
                    identity.writeInt(sourceX); identity.writeInt(sourceGroundY);
                    identity.writeInt(sourceZ); identity.writeInt(deltaY);
                    identity.writeByte(projectionCode(destinationProjection));
                }
            }

            int edgeCount = in.count(MAX_EDGES, "edge");
            require(edgeCount == pieceCount - 1,
                    "Pillager Outpost persisted edge cardinality drift");
            identity.writeInt(edgeCount);
            for (int ordinal = 0; ordinal < edgeCount; ordinal++) {
                int parent = in.i32(), child = in.i32();
                require(parent >= 0 && parent < child && child == ordinal + 1 && child < pieceCount,
                        "Pillager Outpost persisted edge order drift");
                Connector source = in.connector();
                Connector target = in.connector();
                String selectedPool = key(in.string(), "selected pool");
                String resolvedAlias = in.string();
                require(resolvedAlias.isEmpty(), "unexpected Pillager Outpost persisted alias target");
                identity.writeInt(parent); identity.writeInt(child);
                identity.writeInt(source.ordinal()); identity.writeInt(target.ordinal());
                writeString(identity, selectedPool); writeString(identity, source.name());
                writeString(identity, source.target()); writeString(identity, target.name());
                writeString(identity, target.target());
            }

            int queryCount = in.count(MAX_QUERIES, "projection query");
            identity.writeInt(queryCount);
            for (int ordinal = 0; ordinal < queryCount; ordinal++) {
                require(in.i32() == ordinal, "Pillager Outpost projection query order drift");
                int x = in.i32(), z = in.i32(), result = in.i32();
                String heightmap = in.string();
                require(heightmap.equals(Mc263PillagerOutpostProducer.HEIGHTMAP),
                        "Pillager Outpost projection heightmap drift");
                identity.writeInt(ordinal); identity.writeInt(x); identity.writeInt(z);
                identity.writeInt(result); writeString(identity, heightmap);
            }

            long finalState = in.i64();
            int worldgenCount = in.i32();
            require(finalState >= 0 && finalState <= LEGACY_MASK && worldgenCount >= 0,
                    "Pillager Outpost persisted generation RNG header drift");
            int drawCount = in.count(MAX_DRAWS, "generation RNG draw");
            long currentState = -1L;
            int nextBitsCount = 0;
            for (int ordinal = 0; ordinal < drawCount; ordinal++) {
                require(in.i32() == ordinal, "Pillager Outpost generation RNG draw order drift");
                int operation = in.u8();
                if (operation == 0) {
                    long seed = in.i64();
                    currentState = (seed ^ LEGACY_MULTIPLIER) & LEGACY_MASK;
                } else if (operation == 1) {
                    int bits = in.i32(), result = in.i32(); long stateAfter = in.i64();
                    require(currentState >= 0 && bits >= 1 && bits <= 32,
                            "Pillager Outpost nextBits input drift");
                    long expectedState = nextState(currentState);
                    int expectedResult = (int) (expectedState >>> (48 - bits));
                    require(stateAfter == expectedState && result == expectedResult,
                            "Pillager Outpost generation RNG transition drift");
                    currentState = stateAfter; nextBitsCount++;
                } else {
                    throw invalid("unknown Pillager Outpost generation RNG operation");
                }
            }
            require(nextBitsCount == worldgenCount && currentState == finalState,
                    "Pillager Outpost generation RNG final-state drift");
            int continuationCount = in.count(CONTINUATION_WORDS, "generation RNG continuation");
            require(continuationCount == CONTINUATION_WORDS,
                    "Pillager Outpost generation RNG continuation width drift");
            ArrayList<Long> continuation = new ArrayList<>(CONTINUATION_WORDS);
            for (int i = 0; i < CONTINUATION_WORDS; i++) continuation.add(in.i64());
            require(continuation.equals(continuation(finalState)),
                    "Pillager Outpost generation RNG continuation drift");
            identity.writeLong(finalState); identity.writeInt(worldgenCount);
            identity.writeInt(CONTINUATION_WORDS);
            for (long value : continuation) identity.writeLong(value);

            int predecessorLength = in.i32();
            require(predecessorLength == predecessor.length,
                    "Pillager Outpost predecessor length drift");
            byte[] predecessorSha = in.fixed(DIGEST_BYTES);
            byte[] successorSha = in.fixed(DIGEST_BYTES);
            byte[] identitySha = in.fixed(DIGEST_BYTES);
            in.end();

            ReferenceTransition transition = referenceTransition(predecessor);
            require(MessageDigest.isEqual(predecessorSha, sha256(predecessor)),
                    "Pillager Outpost predecessor authority drift");
            require(HexFormat.of().formatHex(successorSha).equals(transition.successorSha256()),
                    "Pillager Outpost successor authority drift");
            writeBytes(identity, predecessor);
            identity.flush();
            byte[] computedIdentity = sha256(identityBytes.toByteArray());
            require(MessageDigest.isEqual(identitySha, computedIdentity),
                    "Pillager Outpost persisted start identity drift");

            return new Parsed(worldSeed, chunkX, chunkZ, predecessorLength,
                    HexFormat.of().formatHex(predecessorSha),
                    HexFormat.of().formatHex(successorSha),
                    HexFormat.of().formatHex(identitySha));
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void writeIdentity(DataOutputStream out,
            Mc263PillagerOutpostProducer.Start start) throws IOException {
        out.write("OUT263S1".getBytes(StandardCharsets.US_ASCII));
        out.writeLong(start.worldSeed()); out.writeInt(start.chunkX()); out.writeInt(start.chunkZ());
        writeProducerBox(out, start.aggregateBoundingBox());
        writeProducerVec(out, start.stubPosition());
        List<PiecePlacement> pieces = start.executionPlan().pieces();
        out.writeInt(pieces.size());
        for (int ordinal = 0; ordinal < pieces.size(); ordinal++) {
            PiecePlacement piece = pieces.get(ordinal);
            out.writeInt(ordinal);
            out.writeInt(piece.originX()); out.writeInt(piece.originY()); out.writeInt(piece.originZ());
            out.writeInt(piece.groundLevelDelta());
            out.writeByte(rotationCode(piece.rotation().name()));
            out.writeByte(projectionCode(piece.projection().name()));
            writeBounds(out, piece.bounds());
            boolean list = piece.type() == ElementType.LIST;
            out.writeByte(list ? 1 : 0);
            List<String> templates = templates(piece);
            out.writeInt(templates.size());
            for (String template : templates) writeString(out, template);
            out.writeInt(piece.junctions().size());
            for (var junction : piece.junctions()) {
                out.writeInt(junction.sourceX()); out.writeInt(junction.sourceGroundY());
                out.writeInt(junction.sourceZ()); out.writeInt(junction.deltaY());
                out.writeByte(projectionCode(junction.destinationProjection().name()));
            }
        }
        out.writeInt(start.acceptedEdges().size());
        for (AcceptedEdge edge : start.acceptedEdges()) {
            out.writeInt(edge.parentPieceOrdinal()); out.writeInt(edge.childPieceOrdinal());
            out.writeInt(edge.sourceConnector().ordinal()); out.writeInt(edge.targetConnector().ordinal());
            writeString(out, edge.selectedSourcePool()); writeString(out, edge.sourceConnector().name());
            writeString(out, edge.sourceConnector().target()); writeString(out, edge.targetConnector().name());
            writeString(out, edge.targetConnector().target());
        }
        out.writeInt(start.projectionQueries().size());
        for (Mc263PillagerOutpostProducer.ProjectionQuery query : start.projectionQueries()) {
            out.writeInt(query.ordinal()); out.writeInt(query.x()); out.writeInt(query.z());
            out.writeInt(query.result()); writeString(out, query.heightmap());
        }
        PlannerRngReceipt rng = start.generationRng();
        out.writeLong(rng.state48()); out.writeInt(rng.worldgenCount());
        out.writeInt(rng.continuationNextLongI64().size());
        for (long value : rng.continuationNextLongI64()) out.writeLong(value);
        writeBytes(out, start.carrier().structureStart().bytes());
    }

    private static void sourcePreflight() {
        require(Mc263PillagerOutpostGrammarData.PAYLOAD_SHA256.length() == 64
                        && Mc263PillagerOutpostGrammarData.GZIP_SHA256.length() == 64,
                "Pillager Outpost grammar source hash pin malformed");
        require(Mc263JigsawStructureCatalog.SERVER_SHA1.length() == 40,
                "Pillager Outpost server source hash pin malformed");
        Mc263PillagerOutpostGrammar.pinned();
    }

    private static String processorForTemplate(String templateKey) {
        String found = null;
        Mc263PillagerOutpostGrammar.Evidence evidence = Mc263PillagerOutpostGrammar.pinned().evidence();
        for (Mc263PillagerOutpostGrammar.Pool pool : evidence.poolsInExecutionOrder()) {
            for (Mc263PillagerOutpostGrammar.PoolElement element : pool.elementsInDeclaredOrder()) {
                if (element instanceof Mc263PillagerOutpostGrammar.SingleElement single
                        && single.template().equals(templateKey)) {
                    found = uniqueProcessor(found, single.processorList(), templateKey);
                } else if (element instanceof Mc263PillagerOutpostGrammar.ListElement list) {
                    for (Mc263PillagerOutpostGrammar.SingleChild child : list.childrenInDeclaredOrder()) {
                        if (child.template().equals(templateKey)) {
                            found = uniqueProcessor(found, child.processorList(), templateKey);
                        }
                    }
                }
            }
        }
        require(found != null, "Pillager Outpost template absent from pinned grammar: " + templateKey);
        return found;
    }

    private static String uniqueProcessor(String prior, String current, String template) {
        require(current.equals("inline") || current.equals("minecraft:outpost_rot"),
                "unknown Pillager Outpost processor for " + template);
        require(prior == null || prior.equals(current),
                "ambiguous Pillager Outpost processor for " + template);
        return current;
    }

    private static List<String> templates(PiecePlacement piece) {
        return piece.type() == ElementType.LIST ? piece.components() : List.of(piece.elementKey());
    }

    private static void writeConnector(DataOutputStream out, ConnectorIdentity connector)
            throws IOException {
        out.writeInt(connector.ordinal()); out.writeInt(connector.x()); out.writeInt(connector.y());
        out.writeInt(connector.z()); writeString(out, connector.front().name());
        writeString(out, connector.top().name()); writeString(out, connector.joint().name());
        writeString(out, connector.name()); writeString(out, connector.target());
        writeString(out, connector.pool()); out.writeInt(connector.placementPriority());
        out.writeInt(connector.selectionPriority());
    }

    private static ReferenceTransition referenceTransition(byte[] predecessor) {
        byte[] successor = referenceSuccessor(predecessor);
        return new ReferenceTransition(sha256Hex(successor));
    }

    /** Exact pinned StructureStart reference mutation authenticated by the official oracle. */
    static byte[] referenceSuccessor(byte[] predecessor) {
        require(predecessor.length > 19 && predecessor[16] == 0 && predecessor[17] == 0
                        && predecessor[18] == 0 && predecessor[19] == 0,
                "Pillager Outpost predecessor references is not exact zero");
        byte[] successor = predecessor.clone();
        successor[19] = 1;
        int changed = 0;
        for (int i = 0; i < predecessor.length; i++) if (predecessor[i] != successor[i]) changed++;
        require(changed == 1, "Pillager Outpost successor changed outside references");
        return successor;
    }

    private static List<Long> continuation(long state) {
        ArrayList<Long> values = new ArrayList<>(CONTINUATION_WORDS);
        long current = state;
        for (int i = 0; i < CONTINUATION_WORDS; i++) {
            current = nextState(current); int high = (int) (current >>> 16);
            current = nextState(current); int low = (int) (current >>> 16);
            values.add(((long) high << 32) + low);
        }
        return List.copyOf(values);
    }

    private static long nextState(long state) {
        return (state * LEGACY_MULTIPLIER + LEGACY_ADDEND) & LEGACY_MASK;
    }

    private static byte[] attach(byte[] root, byte[] payload) {
        require(root.length >= 4 && root[0] == 10 && root[root.length - 1] == 0,
                "malformed Pillager Outpost root piece NBT");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(root.length + payload.length + 64);
            bytes.write(root, 0, root.length - 1);
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(7); out.writeUTF(NBT_TAG); out.writeInt(payload.length); out.write(payload);
                out.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Extraction extract(byte[] root, boolean required) {
        NbtCursor in = new NbtCursor(root);
        require(in.u8() == 10, "Pillager Outpost persisted root is not compound NBT");
        in.utf();
        int authorityStart = -1;
        byte[] payload = null;
        while (true) {
            int fieldStart = in.offset();
            int type = in.u8();
            if (type == 0) {
                require(in.offset() == root.length, "Pillager Outpost persisted root has trailing NBT");
                break;
            }
            require(type >= 1 && type <= 12, "unknown Pillager Outpost persisted NBT type");
            String name = in.utf();
            if (name.equals(NBT_TAG)) {
                require(payload == null && type == 7, "invalid or duplicate Pillager Outpost authority tag");
                authorityStart = fieldStart;
                payload = in.byteArray();
                require(in.peek() == 0,
                        "Pillager Outpost authority tag is not final in root piece NBT");
            } else {
                in.skipPayload(type, 1);
            }
        }
        if (payload == null) {
            if (required) throw invalid("missing Pillager Outpost persisted authority");
            return null;
        }
        byte[] stripped = new byte[authorityStart + 1];
        System.arraycopy(root, 0, stripped, 0, authorityStart);
        stripped[authorityStart] = 0;
        return new Extraction(payload, stripped);
    }

    private static void writeProducerBox(DataOutputStream out, Mc263PillagerOutpostProducer.Box box)
            throws IOException {
        out.writeInt(box.minX()); out.writeInt(box.minY()); out.writeInt(box.minZ());
        out.writeInt(box.maxX()); out.writeInt(box.maxY()); out.writeInt(box.maxZ());
    }

    private static void writeProducerVec(DataOutputStream out, Mc263PillagerOutpostProducer.Vec3i vec)
            throws IOException {
        out.writeInt(vec.x()); out.writeInt(vec.y()); out.writeInt(vec.z());
    }

    private static void writeBounds(DataOutputStream out, Mc263JigsawStructureBoundary.Bounds box)
            throws IOException {
        out.writeInt(box.minX()); out.writeInt(box.minY()); out.writeInt(box.minZ());
        out.writeInt(box.maxX()); out.writeInt(box.maxY()); out.writeInt(box.maxZ());
    }

    private static void writeBox(DataOutputStream out, int[] box) throws IOException {
        for (int value : box) out.writeInt(value);
    }

    private static void writeVec(DataOutputStream out, int[] vec) throws IOException {
        for (int value : vec) out.writeInt(value);
    }

    private static int rotationCode(String rotation) {
        return switch (rotation) {
            case "NONE" -> 0; case "CLOCKWISE_90" -> 1; case "CLOCKWISE_180" -> 2;
            case "COUNTERCLOCKWISE_90" -> 3;
            default -> throw invalid("unknown Pillager Outpost rotation: " + rotation);
        };
    }

    private static int projectionCode(String projection) {
        return switch (projection) {
            case "RIGID" -> 0; case "TERRAIN_MATCHING" -> 1;
            default -> throw invalid("unknown Pillager Outpost projection: " + projection);
        };
    }

    private static String rotation(String value) { rotationCode(value); return value; }
    private static String projection(String value) { projectionCode(value); return value; }

    private static String key(String value, String label) {
        require(value.startsWith("minecraft:") && value.length() > "minecraft:".length(),
                "invalid Pillager Outpost " + label);
        return value;
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] encoded = Objects.requireNonNull(value).getBytes(StandardCharsets.UTF_8);
        out.writeInt(encoded.length); out.write(encoded);
    }

    private static void writeBytes(DataOutputStream out, byte[] bytes) throws IOException {
        out.writeInt(bytes.length); out.write(bytes);
    }

    private static byte[] hexBytes(String value) {
        require(value.length() == DIGEST_BYTES * 2, "invalid Pillager Outpost SHA-256 width");
        try { return HexFormat.of().parseHex(value); }
        catch (IllegalArgumentException error) { throw invalid("invalid Pillager Outpost SHA-256"); }
    }

    private static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static String sha256Hex(byte[] bytes) { return HexFormat.of().formatHex(sha256(bytes)); }

    private static void require(boolean condition, String message) {
        if (!condition) throw invalid(message);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    record Reloaded(long worldSeed, int chunkX, int chunkZ, byte[] strippedRootPiece,
            byte[] authorityPayload, String startIdentitySha256, String predecessorSha256,
            int predecessorLength, String successorSha256) {
        Reloaded {
            strippedRootPiece = strippedRootPiece.clone(); authorityPayload = authorityPayload.clone();
        }
        @Override public byte[] strippedRootPiece() { return strippedRootPiece.clone(); }
        @Override public byte[] authorityPayload() { return authorityPayload.clone(); }

        Mc263PillagerOutpostSettlement.StartAuthority startAuthority() {
            return new Mc263PillagerOutpostSettlement.StartAuthority(startIdentitySha256,
                    predecessorSha256, predecessorLength, successorSha256);
        }
    }

    private record Parsed(long worldSeed, int chunkX, int chunkZ, int predecessorLength,
            String predecessorSha256, String successorSha256, String startIdentitySha256) { }
    private record ReferenceTransition(String successorSha256) { }
    private record Extraction(byte[] payload, byte[] stripped) {
        Extraction { payload = payload.clone(); stripped = stripped.clone(); }
        @Override public byte[] payload() { return payload.clone(); }
        @Override public byte[] stripped() { return stripped.clone(); }
    }
    private record Connector(int ordinal, String name, String target) { }

    private static final class Cursor {
        private final byte[] bytes; private int at;
        Cursor(byte[] bytes) { this.bytes = bytes; }
        void expect(byte[] expected, String label) {
            require(at + expected.length <= bytes.length, "truncated Pillager Outpost " + label);
            for (byte value : expected) require(bytes[at++] == value, "Pillager Outpost " + label + " drift");
        }
        int count(int max, String label) {
            int value = i32(); require(value >= 0 && value <= max,
                    "Pillager Outpost " + label + " count drift"); return value;
        }
        int[] box() {
            int[] b = {i32(),i32(),i32(),i32(),i32(),i32()};
            require(b[0] <= b[3] && b[1] <= b[4] && b[2] <= b[5],
                    "inverted Pillager Outpost persisted box"); return b;
        }
        int[] vec() { return new int[]{i32(),i32(),i32()}; }
        Connector connector() {
            int ordinal = i32(); require(ordinal >= 0, "negative Pillager Outpost connector ordinal");
            i32(); i32(); i32();
            direction(string()); direction(string()); joint(string());
            String name = key(string(), "connector name");
            String target = key(string(), "connector target");
            key(string(), "connector pool"); i32(); i32();
            return new Connector(ordinal, name, target);
        }
        String string() {
            int length = i32(); require(length >= 0 && length <= 1_000_000 && length <= bytes.length - at,
                    "invalid or truncated Pillager Outpost authority string");
            String value = new String(bytes, at, length, StandardCharsets.UTF_8);
            require(Arrays.equals(value.getBytes(StandardCharsets.UTF_8),
                    Arrays.copyOfRange(bytes, at, at + length)),
                    "noncanonical Pillager Outpost UTF-8 string");
            at += length; return value;
        }
        byte[] fixed(int count) {
            require(count >= 0 && count <= bytes.length - at, "truncated Pillager Outpost authority");
            byte[] value = Arrays.copyOfRange(bytes, at, at + count); at += count; return value;
        }
        long i64() {
            return ((long)u8()<<56)|((long)u8()<<48)|((long)u8()<<40)|((long)u8()<<32)
                    |((long)u8()<<24)|((long)u8()<<16)|((long)u8()<<8)|u8();
        }
        int i32() { return (u8()<<24)|(u8()<<16)|(u8()<<8)|u8(); }
        int u8() { require(at < bytes.length, "truncated Pillager Outpost authority"); return Byte.toUnsignedInt(bytes[at++]); }
        void end() { require(at == bytes.length, "trailing Pillager Outpost authority bytes"); }
        private static void direction(String value) {
            require(List.of("DOWN","UP","NORTH","SOUTH","WEST","EAST").contains(value),
                    "unknown Pillager Outpost connector direction");
        }
        private static void joint(String value) {
            require(value.equals("ROLLABLE") || value.equals("ALIGNED"),
                    "unknown Pillager Outpost connector joint");
        }
    }

    private static final class NbtCursor {
        private static final int MAX_DEPTH = 512;
        private final byte[] bytes; private int at;
        NbtCursor(byte[] bytes) { this.bytes = bytes; }
        int offset() { return at; }
        int peek() { require(at < bytes.length, "truncated Pillager Outpost persisted NBT"); return Byte.toUnsignedInt(bytes[at]); }
        int u8() { require(at < bytes.length, "truncated Pillager Outpost persisted NBT"); return Byte.toUnsignedInt(bytes[at++]); }
        int i32() { return (u8()<<24)|(u8()<<16)|(u8()<<8)|u8(); }
        int u16() { return (u8()<<8)|u8(); }
        String utf() {
            int length = u16(); int start = at; skip(length);
            byte[] raw = Arrays.copyOfRange(bytes, start, at);
            for (byte value : raw) require(Byte.toUnsignedInt(value) > 0 && Byte.toUnsignedInt(value) <= 0x7f,
                    "non-ASCII Pillager Outpost NBT field name");
            return new String(raw, StandardCharsets.US_ASCII);
        }
        byte[] byteArray() {
            int length = i32(); require(length >= 0 && length <= bytes.length - at,
                    "invalid Pillager Outpost persisted byte-array length");
            byte[] value = Arrays.copyOfRange(bytes, at, at + length); at += length; return value;
        }
        void skipPayload(int type, int depth) {
            require(depth <= MAX_DEPTH, "Pillager Outpost persisted NBT depth drift");
            switch (type) {
                case 1 -> skip(1); case 2 -> skip(2); case 3,5 -> skip(4); case 4,6 -> skip(8);
                case 7 -> { int n=i32(); require(n>=0, "negative NBT byte array"); skip(n); }
                case 8 -> skip(u16());
                case 9 -> { int element=u8(); require(element>=0&&element<=12,"unknown NBT list type");
                    int n=i32(); require(n>=0,"negative NBT list length");
                    require(element!=0 || n==0,"nonempty TAG_End list");
                    for(int i=0;i<n;i++) skipPayload(element,depth+1); }
                case 10 -> { while(true){ int child=u8(); if(child==0)break; require(child<=12,"unknown NBT type"); utf(); skipPayload(child,depth+1);} }
                case 11 -> { int n=i32(); require(n>=0,"negative NBT int array"); skip(Math.multiplyExact(n,4)); }
                case 12 -> { int n=i32(); require(n>=0,"negative NBT long array"); skip(Math.multiplyExact(n,8)); }
                default -> throw invalid("unknown Pillager Outpost persisted NBT payload type");
            }
        }
        void skip(int count) { require(count >= 0 && count <= bytes.length - at, "truncated Pillager Outpost persisted NBT"); at += count; }
    }
}
