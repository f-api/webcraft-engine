package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampCatalog.Connector;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampCatalog.Element;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampCatalog.ElementKind;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampCatalog.Pool;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampCatalog.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampCatalog.Template;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampCatalog.Vec;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampStartGenerator.TraceRandom;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic, dormant Abandoned Camp Jigsaw piece-graph producer for 26.3-snapshot-7. */
public final class Mc263AbandonedCampProducer {
    private static final int MIN_BUILD_Y = -64;
    private static final int MAX_BUILD_Y = 320;
    private static final int MAX_DEPTH = Mc263AbandonedCampStartGenerator.SIZE;
    private static final int MAX_DISTANCE = Mc263AbandonedCampStartGenerator.MAX_HORIZONTAL_DISTANCE;
    private static final String FEATURE_CLASS =
            "net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement";

    private final Mc263AbandonedCampCatalog catalog;
    private final Map<String, Pool> pools;
    private final Map<String, Template> templates;

    public Mc263AbandonedCampProducer(Mc263AbandonedCampCatalog catalog) {
        if (catalog == null) throw new IllegalArgumentException("Abandoned Camp catalog is required");
        if (catalog.structureKeys().size() != 18 || catalog.pools().size() != 57
                || catalog.templates().size() != 297
                || catalog.configuredFeatureElements().size() != 21) {
            throw new IllegalArgumentException("incomplete Abandoned Camp catalog");
        }
        this.catalog = catalog;
        Map<String, Pool> poolIndex = new LinkedHashMap<>();
        for (Pool pool : catalog.pools()) poolIndex.put(pool.key(), pool);
        this.pools = java.util.Collections.unmodifiableMap(poolIndex);
        Map<String, Template> templateIndex = new LinkedHashMap<>();
        for (Template template : catalog.templates()) templateIndex.put(template.key(), template);
        this.templates = java.util.Collections.unmodifiableMap(templateIndex);
        validateCatalogClosure();
    }

    public static Mc263AbandonedCampProducer pinned() {
        return new Mc263AbandonedCampProducer(Mc263AbandonedCampCatalog.pinned());
    }

    /**
     * Encodes the exact persisted {@code StructureStart} wrapper around producer-owned piece NBT.
     * The accepted piece compounds are copied byte-for-byte; this method only writes the pinned
     * root fields and the authenticated {@code references=0 -> 1} transition.
     */
    public static byte[] persistedStartNbt(String structureKey,
            Mc263StructureCarrier.ValidStart start, int references) {
        Objects.requireNonNull(start, "Abandoned Camp valid start");
        Mc263AbandonedCampStartGenerator.require(structureKey);
        if (references != 0 && references != 1) {
            throw new IllegalArgumentException(
                    "Abandoned Camp references outside authenticated transition");
        }
        String expectedStartKey = structureKey + "@" + start.originChunkX() + ","
                + start.originChunkZ();
        if (!expectedStartKey.equals(start.startKey()) || start.orderedPieces().isEmpty()) {
            throw new IllegalArgumentException("Abandoned Camp raw start identity mismatch");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(10); out.writeUTF("");
                nbtInt(out, "references", references);
                nbtInt(out, "ChunkZ", start.originChunkZ());
                nbtString(out, "id", structureKey);
                out.writeByte(9); out.writeUTF("Children"); out.writeByte(10);
                out.writeInt(start.orderedPieces().size());
                for (Mc263StructureCarrier.Piece piece : start.orderedPieces()) {
                    byte[] encoded = piece.persistedPayload().binaryNbtCompound();
                    if (encoded.length < 4 || encoded[0] != 10 || encoded[1] != 0
                            || encoded[2] != 0) {
                        throw new IllegalArgumentException(
                                "Abandoned Camp piece is not an unnamed NBT compound");
                    }
                    out.write(encoded, 3, encoded.length - 3);
                }
                nbtInt(out, "ChunkX", start.originChunkX());
                out.writeByte(0);
            }
            byte[] encoded = bytes.toByteArray();
            PersistedNbt.decode(encoded, pinned());
            return encoded;
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory Abandoned Camp start NBT failed",
                    impossible);
        }
    }

    private static void nbtInt(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(3); out.writeUTF(name); out.writeInt(value);
    }

    private static void nbtString(DataOutputStream out, String name, String value)
            throws IOException {
        out.writeByte(8); out.writeUTF(name); out.writeUTF(value);
    }

    /**
     * Restores the execution-visible portion of an already persisted Camp start without rerunning
     * jigsaw generation. The raw predecessor/successor pair remains the authority for all facts
     * that are absent from the typed carrier.
     */
    public Start restorePersistedStart(Mc263StructureCarrier.ValidStart typed,
            Mc263StructureCarrier.RawStartPayload raw, long worldSeed) {
        Objects.requireNonNull(typed, "typed persisted Camp start");
        Objects.requireNonNull(raw, "raw persisted Camp start");

        int separator = typed.startKey().lastIndexOf('@');
        if (separator <= 0) {
            throw new IllegalArgumentException("malformed persisted Abandoned Camp start key");
        }
        String structureKey = typed.startKey().substring(0, separator);
        Mc263AbandonedCampStartGenerator.StructureFact structure =
                Mc263AbandonedCampStartGenerator.require(structureKey);
        String expectedStartKey = structureKey + "@" + typed.originChunkX() + ","
                + typed.originChunkZ();
        if (!expectedStartKey.equals(typed.startKey())
                || !structureKey.equals(raw.structureId())
                || !expectedStartKey.equals(raw.startKey())
                || typed.originChunkX() != raw.originChunkX()
                || typed.originChunkZ() != raw.originChunkZ()) {
            throw new IllegalArgumentException("persisted Abandoned Camp typed/raw identity mismatch");
        }

        byte[] predecessorBytes = raw.predecessorBinaryNbtCompound();
        byte[] successorBytes = raw.successorBinaryNbtCompound();
        if (!raw.predecessorSha256().equals(sha256(predecessorBytes))
                || !raw.successorSha256().equals(sha256(successorBytes))) {
            throw new IllegalArgumentException("persisted Abandoned Camp raw hash mismatch");
        }
        PersistedStart predecessor = PersistedNbt.decode(predecessorBytes, this);
        PersistedStart successor = PersistedNbt.decode(successorBytes, this);
        predecessor.requireIdentity(structureKey, typed.originChunkX(), typed.originChunkZ());
        successor.requireIdentity(structureKey, typed.originChunkX(), typed.originChunkZ());
        if (predecessor.references != 0 || successor.references != 1
                || (typed.references() != 0 && typed.references() != 1)) {
            throw new IllegalArgumentException(
                    "persisted Abandoned Camp references are not canonical 0->1");
        }
        requireReferenceOnlySuccessor(predecessor, successor);
        requireTypedRawPieces(typed, predecessor, predecessorBytes);
        if (!samePieces(predecessor.pieces, successor.pieces)) {
            throw new IllegalArgumentException("persisted Abandoned Camp successor piece drift");
        }

        Box aggregate = aggregateBoundingBox(predecessor.pieces).inflate(12);
        if (!sameBox(aggregate, typed.adjustedBoundingBox())) {
            throw new IllegalArgumentException("persisted Abandoned Camp adjusted bbox mismatch");
        }
        requireStartPool(structure.startPool(), predecessor.pieces.getFirst());

        ArrayList<Piece> pieces = new ArrayList<>(predecessor.pieces.size());
        for (int index = 0; index < predecessor.pieces.size(); index++) {
            PersistedPiece piece = predecessor.pieces.get(index);
            pieces.add(new Piece(index, piece.kind, piece.templateKey, piece.featureKey,
                    piece.position, piece.rotation, piece.projection, piece.groundLevelDelta,
                    piece.boundingBox, piece.junctions));
        }
        return Start.restored(structureKey, worldSeed, typed.originChunkX(), typed.originChunkZ(),
                structure.startPool(), aggregate, pieces);
    }

    private void requireTypedRawPieces(Mc263StructureCarrier.ValidStart typed,
            PersistedStart persisted, byte[] startBytes) {
        if (typed.orderedPieces().size() != persisted.pieces.size()) {
            throw new IllegalArgumentException("persisted Abandoned Camp Children/piece count mismatch");
        }
        for (int index = 0; index < persisted.pieces.size(); index++) {
            Mc263StructureCarrier.Piece typedPiece = typed.orderedPieces().get(index);
            PersistedPiece rawPiece = persisted.pieces.get(index);
            if (!"minecraft:jigsaw".equals(typedPiece.pieceType()) || !typedPiece.poolElement()
                    || typedPiece.projection() != Mc263StructureCarrier.Projection.RIGID
                    || typedPiece.groundLevelDelta() != rawPiece.groundLevelDelta
                    || !sameBox(rawPiece.boundingBox, typedPiece.boundingBox())
                    || typedPiece.junctions().size() != rawPiece.junctions.size()) {
                throw new IllegalArgumentException("persisted Abandoned Camp typed/raw piece mismatch");
            }
            for (int junctionIndex = 0; junctionIndex < rawPiece.junctions.size(); junctionIndex++) {
                Mc263StructureCarrier.Junction typedJunction = typedPiece.junctions().get(junctionIndex);
                Junction rawJunction = rawPiece.junctions.get(junctionIndex);
                if (typedJunction.sourceX() != rawJunction.sourceX
                        || typedJunction.sourceGroundY() != rawJunction.sourceGroundY
                        || typedJunction.sourceZ() != rawJunction.sourceZ
                        || typedJunction.deltaY() != rawJunction.deltaY
                        || typedJunction.destinationProjection()
                                != Mc263StructureCarrier.Projection.RIGID) {
                    throw new IllegalArgumentException(
                            "persisted Abandoned Camp typed/raw junction mismatch");
                }
            }
            byte[] pieceBytes = typedPiece.persistedPayload().binaryNbtCompound();
            int rawLength = rawPiece.compoundEnd - rawPiece.compoundStart;
            if (pieceBytes.length != rawLength + 3 || pieceBytes[0] != 10
                    || pieceBytes[1] != 0 || pieceBytes[2] != 0
                    || !Arrays.equals(pieceBytes, 3, pieceBytes.length,
                            startBytes, rawPiece.compoundStart, rawPiece.compoundEnd)) {
                throw new IllegalArgumentException(
                        "persisted Abandoned Camp typed/raw piece payload mismatch");
            }
        }
    }

    private static void requireReferenceOnlySuccessor(PersistedStart predecessor,
            PersistedStart successor) {
        if (predecessor.bytes.length != successor.bytes.length
                || predecessor.referenceOffset != successor.referenceOffset) {
            throw new IllegalArgumentException("persisted Abandoned Camp successor framing drift");
        }
        for (int index = 0; index < predecessor.bytes.length; index++) {
            if (index >= predecessor.referenceOffset
                    && index < predecessor.referenceOffset + Integer.BYTES) continue;
            if (predecessor.bytes[index] != successor.bytes[index]) {
                throw new IllegalArgumentException(
                        "persisted Abandoned Camp successor changed outside references");
            }
        }
    }

    private static boolean samePieces(List<PersistedPiece> left, List<PersistedPiece> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            PersistedPiece a = left.get(index);
            PersistedPiece b = right.get(index);
            if (a.kind != b.kind || !Objects.equals(a.templateKey, b.templateKey)
                    || !Objects.equals(a.featureKey, b.featureKey)
                    || !a.position.equals(b.position) || a.rotation != b.rotation
                    || !a.projection.equals(b.projection)
                    || a.groundLevelDelta != b.groundLevelDelta
                    || !sameBox(a.boundingBox, b.boundingBox)
                    || !a.junctions.equals(b.junctions)) return false;
        }
        return true;
    }

    private static Box aggregateBoundingBox(List<PersistedPiece> pieces) {
        Box aggregate = pieces.getFirst().boundingBox;
        for (int index = 1; index < pieces.size(); index++) {
            aggregate = aggregate.encapsulate(pieces.get(index).boundingBox);
        }
        return aggregate;
    }

    private void requireStartPool(String startPool, PersistedPiece first) {
        if (first.kind != ElementKind.TEMPLATE) {
            throw new IllegalArgumentException("persisted Abandoned Camp start piece is not a template");
        }
        boolean found = false;
        for (Element element : requirePool(startPool).expandedElements()) {
            if (element.kind() == ElementKind.TEMPLATE
                    && first.templateKey.equals(element.templateKey())
                    && first.projection.equals(element.projection())
                    && Mc263AbandonedCampCatalog.INLINE_PROCESSOR_KEY
                            .equals(element.processorListKey())) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("persisted Abandoned Camp start-pool mismatch");
        }
    }

    private static boolean sameBox(Box left, Mc263StructureCarrier.BoundingBox right) {
        return left.minX == right.minX() && left.minY == right.minY()
                && left.minZ == right.minZ() && left.maxX == right.maxX()
                && left.maxY == right.maxY() && left.maxZ == right.maxZ();
    }

    private static boolean sameBox(Box left, Box right) {
        return left.minX == right.minX && left.minY == right.minY
                && left.minZ == right.minZ && left.maxX == right.maxX
                && left.maxY == right.maxY && left.maxZ == right.maxZ;
    }

    private static String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Produces only the immutable start/piece graph. No world mutation or feature execution occurs. */
    public Start generate(String structureKey, long worldSeed, int chunkX, int chunkZ,
            HeightAccess heights) {
        Mc263AbandonedCampStartGenerator.StructureFact structure =
                Mc263AbandonedCampStartGenerator.require(structureKey);
        preflight(structure, heights);

        TraceRandom random = Mc263AbandonedCampStartGenerator.generationRandom(worldSeed,
                chunkX, chunkZ);
        Rotation startRotation = Rotation.values()[random.nextInt(4)];
        Element startElement = randomElement(requirePool(structure.startPool()), random);
        if (startElement.kind() != ElementKind.TEMPLATE) {
            throw new IllegalStateException("Abandoned Camp start pool selected non-template");
        }
        Template startTemplate = requireTemplate(startElement.templateKey());
        int originX = Math.multiplyExact(chunkX, 16);
        int originZ = Math.multiplyExact(chunkZ, 16);
        Box initialBox = box(startTemplate, new Vec(originX, 0, originZ), startRotation);
        int centerX = floorAverage(initialBox.minX, initialBox.maxX);
        int centerZ = floorAverage(initialBox.minZ, initialBox.maxZ);
        int bottomY = heights.worldSurfaceHeight(centerX, centerZ);
        int moveY = Math.subtractExact(bottomY, Math.addExact(initialBox.minY, 1));
        Vec startOrigin = new Vec(originX, moveY, originZ);
        Box startBox = initialBox.move(0, moveY, 0);
        if (!fitsWorld(startBox)) {
            return Start.empty(structureKey, worldSeed, chunkX, chunkZ, randomReceipt(random));
        }

        int centerY = bottomY;
        MutablePiece root = MutablePiece.template(0, startElement, startTemplate, startOrigin,
                startRotation, startBox, 1);
        List<MutablePiece> mutablePieces = new ArrayList<>();
        mutablePieces.add(root);
        CollisionSpace global = new CollisionSpace(new Box(centerX - MAX_DISTANCE,
                Math.max(centerY - Mc263AbandonedCampStartGenerator.MAX_VERTICAL_DISTANCE,
                        MIN_BUILD_Y),
                centerZ - MAX_DISTANCE,
                centerX + MAX_DISTANCE,
                Math.min(centerY + Mc263AbandonedCampStartGenerator.MAX_VERTICAL_DISTANCE,
                        MAX_BUILD_Y - 1),
                centerZ + MAX_DISTANCE));
        global.occupied.add(startBox);
        Deque<Pending> queue = new ArrayDeque<>();
        placeChildren(root, global, 0, random, mutablePieces, queue);
        while (!queue.isEmpty()) {
            Pending pending = queue.removeFirst();
            placeChildren(pending.piece, pending.free, pending.depth, random, mutablePieces, queue);
        }

        List<Piece> pieces = freezePieces(mutablePieces);
        Box aggregate = pieces.stream().map(Piece::boundingBox).reduce(Box::encapsulate)
                .orElseThrow().inflate(12);
        List<ConnectorEdge> edges = acceptedEdges(pieces);
        Vec stub = new Vec(centerX, bottomY, centerZ);
        RandomReceipt receipt = randomReceipt(random);
        return new Start(structureKey, worldSeed, chunkX, chunkZ, structure.startPool(), stub,
                aggregate, pieces, edges, receipt, graphReceipt(structureKey, stub, aggregate,
                        pieces, edges, receipt), false);
    }

    private void placeChildren(MutablePiece source, CollisionSpace contextFree, int depth,
            TraceRandom random, List<MutablePiece> pieces, Deque<Pending> queue) {
        List<WorldConnector> sources = shuffledConnectors(source, random);
        CollisionSpace sourceFree = null;
        sourceLoop:
        for (WorldConnector sourceConnector : sources) {
            Vec targetPosition = sourceConnector.position.add(step(sourceConnector.front));
            Pool pool = pools.get(sourceConnector.connector.poolKey());
            if (pool == null) continue;
            CollisionSpace childrenFree;
            if (source.boundingBox.contains(targetPosition)) {
                if (sourceFree == null) sourceFree = new CollisionSpace(source.boundingBox);
                childrenFree = sourceFree;
            } else {
                childrenFree = contextFree;
            }

            List<Element> candidates = new ArrayList<>();
            if (depth != MAX_DEPTH) candidates.addAll(shuffledPool(pool, random));
            Pool fallback = pools.get(pool.fallback());
            if (fallback != null) candidates.addAll(shuffledPool(fallback, random));

            for (Element targetElement : candidates) {
                for (Rotation rotation : shuffledRotations(random)) {
                    List<WorldConnector> targets = shuffledConnectors(targetElement,
                            Vec.ZERO, rotation, random);
                    for (WorldConnector targetConnector : targets) {
                        if (!canAttach(sourceConnector, targetConnector)) continue;
                        Vec rawOrigin = new Vec(Math.subtractExact(targetPosition.x(), targetConnector.position.x()),
                                Math.subtractExact(targetPosition.y(), targetConnector.position.y()),
                                Math.subtractExact(targetPosition.z(), targetConnector.position.z()));
                        Box rawBox = elementBox(targetElement, rawOrigin, rotation);
                        int sourceLocalY = sourceConnector.position.y() - source.boundingBox.minY;
                        int targetLocalY = targetConnector.position.y();
                        int deltaY = sourceLocalY - targetLocalY + step(sourceConnector.front).y();
                        int targetBoxY = source.boundingBox.minY + deltaY;
                        int yOffset = targetBoxY - rawBox.minY;
                        Vec targetOrigin = rawOrigin.add(new Vec(0, yOffset, 0));
                        Box targetBox = rawBox.move(0, yOffset, 0);
                        Box collisionBox = expansionBox(targetElement, targetBox, rotation);
                        if (!childrenFree.accept(collisionBox)) continue;

                        int targetGround = source.groundLevelDelta - deltaY;
                        MutablePiece child = targetElement.kind() == ElementKind.TEMPLATE
                                ? MutablePiece.template(pieces.size(), targetElement,
                                        requireTemplate(targetElement.templateKey()), targetOrigin,
                                        rotation, targetBox, targetGround)
                                : MutablePiece.feature(pieces.size(), targetElement, targetOrigin,
                                        rotation, targetBox, targetGround);
                        int junctionY = source.boundingBox.minY + sourceLocalY;
                        source.junctions.add(new Junction(targetPosition.x(),
                                junctionY - sourceLocalY + source.groundLevelDelta,
                                targetPosition.z(), deltaY,
                                Mc263AbandonedCampCatalog.RIGID_PROJECTION));
                        child.junctions.add(new Junction(sourceConnector.position.x(),
                                junctionY - targetLocalY + targetGround,
                                sourceConnector.position.z(), -deltaY,
                                Mc263AbandonedCampCatalog.RIGID_PROJECTION));
                        pieces.add(child);
                        if (depth + 1 <= MAX_DEPTH) {
                            queue.addLast(new Pending(child, childrenFree, depth + 1));
                        }
                        continue sourceLoop;
                    }
                }
            }
        }
    }

    private Box expansionBox(Element element, Box actual, Rotation rotation) {
        if (element.kind() != ElementKind.TEMPLATE || actual.height() > 16) return actual;
        int maximum = 0;
        Template template = requireTemplate(element.templateKey());
        for (WorldConnector connector : placedConnectors(template, Vec.ZERO, rotation)) {
            Vec adjacent = connector.position.add(step(connector.front));
            Box localBox = box(template, Vec.ZERO, rotation);
            if (!localBox.contains(adjacent)) continue;
            Pool child = pools.get(connector.connector.poolKey());
            if (child != null) maximum = Math.max(maximum, maxPoolHeight(child));
            if (child != null) {
                Pool fallback = pools.get(child.fallback());
                if (fallback != null) maximum = Math.max(maximum, maxPoolHeight(fallback));
            }
        }
        if (maximum <= 0) return actual;
        int newSize = Math.max(maximum + 1, actual.maxY - actual.minY);
        return new Box(actual.minX, actual.minY, actual.minZ, actual.maxX,
                actual.minY + newSize, actual.maxZ);
    }

    private int maxPoolHeight(Pool pool) {
        int maximum = 0;
        for (Element element : pool.expandedElements()) {
            if (element.kind() == ElementKind.TEMPLATE) {
                maximum = Math.max(maximum, requireTemplate(element.templateKey()).size().y());
            }
        }
        return maximum;
    }

    private List<Piece> freezePieces(List<MutablePiece> source) {
        List<Piece> result = new ArrayList<>(source.size());
        for (MutablePiece piece : source) result.add(piece.freeze());
        return List.copyOf(result);
    }

    private List<ConnectorEdge> acceptedEdges(List<Piece> pieces) {
        List<List<WorldConnector>> connectors = new ArrayList<>(pieces.size());
        for (Piece piece : pieces) connectors.add(pieceConnectors(piece));
        List<ConnectorEdge> result = new ArrayList<>();
        for (int sourcePiece = 0; sourcePiece < pieces.size(); sourcePiece++) {
            for (int targetPiece = sourcePiece + 1; targetPiece < pieces.size(); targetPiece++) {
                List<WorldConnector> sources = connectors.get(sourcePiece);
                List<WorldConnector> targets = connectors.get(targetPiece);
                for (int s = 0; s < sources.size(); s++) {
                    WorldConnector source = sources.get(s);
                    for (int t = 0; t < targets.size(); t++) {
                        WorldConnector target = targets.get(t);
                        boolean adjacent = source.position.add(step(source.front)).equals(target.position);
                        boolean reverse = target.position.add(step(target.front)).equals(source.position);
                        if ((!adjacent && !reverse) || !canAttach(source, target)) continue;
                        result.add(new ConnectorEdge(sourcePiece, s, source.position,
                                displayName(source.connector.name()), source.connector.target(),
                                source.connector.poolKey(), targetPiece, t, target.position,
                                displayName(target.connector.name()), target.connector.target(), true));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private List<WorldConnector> pieceConnectors(Piece piece) {
        if (piece.kind == ElementKind.FEATURE) {
            return List.of(featureConnector(piece.position, piece.rotation));
        }
        return placedConnectors(requireTemplate(piece.templateKey), piece.position, piece.rotation);
    }

    private static String displayName(String name) {
        return name.isEmpty() ? "<null>" : name;
    }

    private List<WorldConnector> shuffledConnectors(MutablePiece piece, TraceRandom random) {
        if (piece.element.kind() == ElementKind.FEATURE) {
            return List.of(featureConnector(piece.position, piece.rotation));
        }
        List<Connector> values = new ArrayList<>(piece.template.connectors());
        shuffle(values, random);
        values.sort(Comparator.comparingInt(Connector::selectionPriority).reversed());
        return placedConnectors(values, piece.position, piece.rotation);
    }

    private List<WorldConnector> shuffledConnectors(Element element, Vec origin, Rotation rotation,
            TraceRandom random) {
        if (element.kind() == ElementKind.FEATURE) {
            return List.of(featureConnector(origin, rotation));
        }
        List<Connector> values = new ArrayList<>(requireTemplate(element.templateKey()).connectors());
        shuffle(values, random);
        values.sort(Comparator.comparingInt(Connector::selectionPriority).reversed());
        return placedConnectors(values, origin, rotation);
    }

    private List<WorldConnector> placedConnectors(Template template, Vec origin, Rotation rotation) {
        return placedConnectors(template.connectors(), origin, rotation);
    }

    private List<WorldConnector> placedConnectors(List<Connector> source, Vec origin,
            Rotation rotation) {
        List<WorldConnector> result = new ArrayList<>(source.size());
        for (Connector connector : source) {
            Orientation orientation = orientation(connector.state());
            result.add(new WorldConnector(connector,
                    origin.add(connector.position().rotateAroundOrigin(rotation)),
                    rotate(orientation.front, rotation), rotate(orientation.top, rotation)));
        }
        return result;
    }

    private static WorldConnector featureConnector(Vec origin, Rotation rotation) {
        Connector synthetic = SyntheticConnector.VALUE;
        return new WorldConnector(synthetic, origin, Direction.DOWN,
                rotate(Direction.SOUTH, rotation));
    }

    private static boolean canAttach(WorldConnector source, WorldConnector target) {
        if (source.front != opposite(target.front)) return false;
        if ("ALIGNED".equals(source.connector.joint()) && source.top != target.top) return false;
        return target.connector.name().isEmpty()
                || source.connector.target().equals(target.connector.name());
    }

    private List<Element> shuffledPool(Pool pool, TraceRandom random) {
        List<Element> values = new ArrayList<>(pool.expandedElements());
        shuffle(values, random);
        return values;
    }

    private static List<Rotation> shuffledRotations(TraceRandom random) {
        List<Rotation> values = new ArrayList<>(List.of(Rotation.values()));
        shuffle(values, random);
        return values;
    }

    private static Element randomElement(Pool pool, TraceRandom random) {
        List<Element> expanded = pool.expandedElements();
        return expanded.get(random.nextInt(expanded.size()));
    }

    private static <T> void shuffle(List<T> values, TraceRandom random) {
        for (int size = values.size(); size > 1; size--) {
            int selected = random.nextInt(size);
            T displaced = values.set(size - 1, values.get(selected));
            values.set(selected, displaced);
        }
    }

    private void preflight(Mc263AbandonedCampStartGenerator.StructureFact structure,
            HeightAccess heights) {
        if (heights == null || !heights.supportsWorldSurfaceHeight()) {
            throw new IllegalArgumentException("WORLD_SURFACE_WG height capability is required");
        }
        requirePool(structure.startPool());
        // Catalog construction already validates keys. Repeat direct closure checks before RNG/output.
        for (Pool pool : catalog.pools()) {
            if (!Mc263AbandonedCampCatalog.EMPTY_POOL_KEY.equals(pool.fallback())
                    && !pools.containsKey(pool.fallback())) {
                throw new IllegalArgumentException("unknown Abandoned Camp fallback: " + pool.fallback());
            }
            for (Element element : pool.elements()) {
                if (element.kind() == ElementKind.TEMPLATE) {
                    requireTemplate(element.templateKey());
                    catalog.requireProcessor(element.processorListKey());
                } else {
                    catalog.requireConfiguredFeature(element.featureKey());
                }
            }
        }
        for (Template template : catalog.templates()) {
            for (Connector connector : template.connectors()) {
                orientation(connector.state());
                if (!"ROLLABLE".equals(connector.joint()) && !"ALIGNED".equals(connector.joint())) {
                    throw new IllegalArgumentException("unknown Abandoned Camp connector joint: "
                            + connector.joint());
                }
                if (connector.name() == null || connector.target() == null
                        || connector.poolKey() == null) {
                    throw new IllegalArgumentException("incomplete Abandoned Camp connector");
                }
                if (!Mc263AbandonedCampCatalog.EMPTY_POOL_KEY.equals(connector.poolKey())) {
                    requirePool(connector.poolKey());
                }
            }
            for (Mc263AbandonedCampCatalog.Command command : template.commands()) {
                if (command.op() == Mc263AbandonedCampCatalog.CommandOp.JIGSAW) {
                    catalog.requireReplacementState(command.replacementState());
                }
            }
        }
    }

    private void validateCatalogClosure() {
        if (catalog.structureSet().spacing() != 34 || catalog.structureSet().separation() != 8
                || catalog.structureSet().salt() != 91_231_127
                || !"LINEAR".equals(catalog.structureSet().spreadType())) {
            throw new IllegalArgumentException("Abandoned Camp placement catalog drift");
        }
        int expanded = catalog.pools().stream().mapToInt(Pool::expandedWeight).sum();
        if (expanded != Mc263AbandonedCampCatalog.WEIGHTED_ELEMENT_COUNT) {
            throw new IllegalArgumentException("Abandoned Camp weighted element drift");
        }
    }

    private Pool requirePool(String key) {
        Pool pool = pools.get(key);
        if (pool == null) throw new IllegalArgumentException("unknown Abandoned Camp pool: " + key);
        return pool;
    }

    private Template requireTemplate(String key) {
        Template template = templates.get(key);
        if (template == null) {
            throw new IllegalArgumentException("unknown Abandoned Camp template: " + key);
        }
        return template;
    }

    private Box elementBox(Element element, Vec origin, Rotation rotation) {
        if (element.kind() == ElementKind.FEATURE) {
            return new Box(origin.x(), origin.y(), origin.z(), origin.x(), origin.y(), origin.z());
        }
        return box(requireTemplate(element.templateKey()), origin, rotation);
    }

    private static Box box(Template template, Vec origin, Rotation rotation) {
        Vec far = new Vec(template.size().x() - 1, template.size().y() - 1,
                template.size().z() - 1).rotateAroundOrigin(rotation);
        return new Box(Math.min(origin.x(), origin.x() + far.x()), origin.y(),
                Math.min(origin.z(), origin.z() + far.z()),
                Math.max(origin.x(), origin.x() + far.x()), origin.y() + far.y(),
                Math.max(origin.z(), origin.z() + far.z()));
    }

    private static int floorAverage(int a, int b) {
        return (int) (((long) a + (long) b) / 2L);
    }

    private static boolean fitsWorld(Box box) {
        return box.minY >= MIN_BUILD_Y && box.maxY < MAX_BUILD_Y;
    }

    private static Direction rotate(Direction direction, Rotation rotation) {
        if (direction == Direction.UP || direction == Direction.DOWN) return direction;
        Direction result = direction;
        int turns = switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
        for (int i = 0; i < turns; i++) result = clockwise(result);
        return result;
    }

    private static Direction clockwise(Direction direction) {
        return switch (direction) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> direction;
        };
    }

    private static Direction opposite(Direction direction) {
        return switch (direction) {
            case DOWN -> Direction.UP;
            case UP -> Direction.DOWN;
            case NORTH -> Direction.SOUTH;
            case SOUTH -> Direction.NORTH;
            case WEST -> Direction.EAST;
            case EAST -> Direction.WEST;
        };
    }

    private static Vec step(Direction direction) {
        return switch (direction) {
            case DOWN -> new Vec(0, -1, 0);
            case UP -> new Vec(0, 1, 0);
            case NORTH -> new Vec(0, 0, -1);
            case SOUTH -> new Vec(0, 0, 1);
            case WEST -> new Vec(-1, 0, 0);
            case EAST -> new Vec(1, 0, 0);
        };
    }

    private static Orientation orientation(String state) {
        int marker = state.indexOf("orientation=");
        if (marker < 0 || !state.endsWith("]")) {
            throw new IllegalArgumentException("unsupported Abandoned Camp jigsaw state: " + state);
        }
        String value = state.substring(marker + "orientation=".length(), state.length() - 1);
        String[] parts = value.split("_", -1);
        if (parts.length != 2) throw new IllegalArgumentException("invalid jigsaw orientation: " + state);
        return new Orientation(Direction.valueOf(parts[0].toUpperCase()),
                Direction.valueOf(parts[1].toUpperCase()));
    }

    private static RandomReceipt randomReceipt(TraceRandom random) {
        return new RandomReceipt(random.state48(), random.count(), random.draws(),
                toList(random.continuation()));
    }

    private static List<Long> toList(long[] values) {
        List<Long> result = new ArrayList<>(values.length);
        for (long value : values) result.add(value);
        return List.copyOf(result);
    }

    private static String graphReceipt(String structureKey, Vec stub, Box aggregate,
            List<Piece> pieces, List<ConnectorEdge> edges, RandomReceipt random) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                writeString(out, structureKey); writeVec(out, stub); writeBox(out, aggregate);
                out.writeInt(pieces.size());
                for (Piece piece : pieces) {
                    out.writeInt(piece.ordinal); out.writeByte(piece.kind.ordinal());
                    writeString(out, piece.templateKey == null ? "" : piece.templateKey);
                    writeString(out, piece.featureKey == null ? "" : piece.featureKey);
                    writeVec(out, piece.position); out.writeByte(piece.rotation.ordinal());
                    out.writeInt(piece.groundLevelDelta); writeBox(out, piece.boundingBox);
                    out.writeInt(piece.junctions.size());
                    for (Junction junction : piece.junctions) {
                        out.writeInt(junction.sourceX); out.writeInt(junction.sourceGroundY);
                        out.writeInt(junction.sourceZ); out.writeInt(junction.deltaY);
                        writeString(out, junction.destinationProjection);
                    }
                }
                out.writeInt(edges.size());
                for (ConnectorEdge edge : edges) {
                    out.writeInt(edge.sourcePiece); out.writeInt(edge.sourceConnectorOrdinal);
                    writeVec(out, edge.sourcePosition); writeString(out, edge.sourceName);
                    writeString(out, edge.sourceTarget); writeString(out, edge.sourcePool);
                    out.writeInt(edge.targetPiece); out.writeInt(edge.targetConnectorOrdinal);
                    writeVec(out, edge.targetPosition); writeString(out, edge.targetName);
                    writeString(out, edge.targetTarget); out.writeBoolean(edge.officialCanAttach);
                }
                out.writeLong(random.state48); out.writeInt(random.worldgenCount);
                for (long value : random.continuationNextLongI64) out.writeLong(value);
            }
            byte[] hash = digest.digest(bytes.toByteArray());
            return java.util.HexFormat.of().formatHex(hash);
        } catch (IOException | NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void writeVec(DataOutputStream out, Vec value) throws IOException {
        out.writeInt(value.x()); out.writeInt(value.y()); out.writeInt(value.z());
    }

    private static void writeBox(DataOutputStream out, Box value) throws IOException {
        out.writeInt(value.minX); out.writeInt(value.minY); out.writeInt(value.minZ);
        out.writeInt(value.maxX); out.writeInt(value.maxY); out.writeInt(value.maxZ);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(encoded.length); out.write(encoded);
    }

    private static final class PersistedNbt {
        private static final int TAG_END = 0;
        private static final int TAG_INT = 3;
        private static final int TAG_STRING = 8;
        private static final int TAG_LIST = 9;
        private static final int TAG_COMPOUND = 10;
        private static final int TAG_INT_ARRAY = 11;

        static PersistedStart decode(byte[] bytes, Mc263AbandonedCampProducer producer) {
            Reader input = new Reader(Objects.requireNonNull(bytes, "persisted Camp NBT"));
            input.requireType(TAG_COMPOUND, "root");
            if (!input.utf("root name", true).isEmpty()) {
                throw malformed("persisted Abandoned Camp root must be unnamed");
            }

            input.requireNamed(TAG_INT, "references");
            int referenceOffset = input.offset;
            int references = input.signedInt();
            input.requireNamed(TAG_INT, "ChunkZ");
            int chunkZ = input.signedInt();
            input.requireNamed(TAG_STRING, "id");
            String structureKey = input.utf("structure id", false);
            input.requireNamed(TAG_LIST, "Children");
            input.requireType(TAG_COMPOUND, "Children element");
            int childCount = input.count(46, "Children");
            if (childCount == 0) {
                throw malformed("persisted Abandoned Camp has no Children");
            }
            ArrayList<PersistedPiece> pieces = new ArrayList<>(childCount);
            for (int index = 0; index < childCount; index++) {
                pieces.add(parsePiece(input, producer));
            }
            input.requireNamed(TAG_INT, "ChunkX");
            int chunkX = input.signedInt();
            input.requireEnd();
            input.requireFullyConsumed();
            return new PersistedStart(bytes.clone(), referenceOffset, references, structureKey,
                    chunkX, chunkZ, List.copyOf(pieces));
        }

        private static PersistedPiece parsePiece(Reader input,
                Mc263AbandonedCampProducer producer) {
            int compoundStart = input.offset;
            input.requireNamed(TAG_INT_ARRAY, "BB");
            if (input.count(6, "BB") != 6) {
                throw malformed("persisted Abandoned Camp BB length is not 6");
            }
            Box boundingBox = new Box(input.signedInt(), input.signedInt(), input.signedInt(),
                    input.signedInt(), input.signedInt(), input.signedInt());
            input.requireNamed(TAG_INT, "PosZ");
            int posZ = input.signedInt();
            input.requireNamed(TAG_INT, "PosX");
            int posX = input.signedInt();
            input.requireNamed(TAG_COMPOUND, "pool_element");
            PoolElementFact element = parsePoolElement(input, producer);
            input.requireNamed(TAG_INT, "PosY");
            int posY = input.signedInt();
            input.requireNamed(TAG_STRING, "rotation");
            Rotation rotation = rotation(input.utf("rotation", false));
            input.requireNamed(TAG_STRING, "id");
            if (!"minecraft:jigsaw".equals(input.utf("piece id", false))) {
                throw malformed("persisted Abandoned Camp piece id is not minecraft:jigsaw");
            }
            input.requireNamed(TAG_INT, "GD");
            if (input.signedInt() != 0) {
                throw malformed("persisted Abandoned Camp GD is not canonical");
            }
            input.requireNamed(TAG_INT, "O");
            if (input.signedInt() != -1) {
                throw malformed("persisted Abandoned Camp O is not canonical");
            }
            input.requireNamed(TAG_INT, "ground_level_delta");
            int groundLevelDelta = input.signedInt();
            input.requireNamed(TAG_LIST, "junctions");
            input.requireType(TAG_COMPOUND, "junction element");
            // Vanilla JigsawPlacement records one junction on the source piece per jigsaw
            // connector that accepted a child, plus exactly one junction contributed by the
            // parent when the piece itself was attached. The exact per-element ceiling is
            // therefore the element's connector count plus one; a feature pool element has no
            // connectors and can only carry the parent's junction.
            int junctionCount = input.count(element.kind == ElementKind.TEMPLATE
                    ? producer.requireTemplate(element.templateKey).connectors().size() + 1
                    : 1, "junctions");
            ArrayList<Junction> junctions = new ArrayList<>(junctionCount);
            for (int index = 0; index < junctionCount; index++) {
                junctions.add(parseJunction(input));
            }
            input.requireEnd();
            int compoundEnd = input.offset;

            Vec position = new Vec(posX, posY, posZ);
            Box expected = element.kind == ElementKind.TEMPLATE
                    ? box(producer.requireTemplate(element.templateKey), position, rotation)
                    : new Box(posX, posY, posZ, posX, posY, posZ);
            if (!sameBox(boundingBox, expected)) {
                throw malformed("persisted Abandoned Camp piece bbox/origin/rotation mismatch");
            }
            return new PersistedPiece(element.kind, element.templateKey, element.featureKey,
                    position, rotation, element.projection, groundLevelDelta, boundingBox,
                    List.copyOf(junctions), compoundStart, compoundEnd);
        }

        private static PoolElementFact parsePoolElement(Reader input,
                Mc263AbandonedCampProducer producer) {
            int firstType = input.unsignedByte();
            if (firstType != TAG_STRING) {
                throw malformed("persisted Abandoned Camp pool_element first tag type");
            }
            String firstName = input.utf("pool_element first name", false);
            String firstValue = input.utf("pool_element key", false);
            ElementKind kind;
            String templateKey = null;
            String featureKey = null;
            if ("location".equals(firstName)) {
                kind = ElementKind.TEMPLATE;
                templateKey = firstValue;
                producer.requireTemplate(templateKey);
                input.requireNamed(TAG_COMPOUND, "processors");
                input.requireNamed(TAG_LIST, "processors");
                input.requireType(TAG_END, "empty processor-list element");
                if (input.count(0, "processor list") != 0) {
                    throw malformed("persisted Abandoned Camp processor list is not empty");
                }
                input.requireEnd();
                Mc263AbandonedCampCatalog.ProcessorList processor =
                        producer.catalog.requireProcessor(
                                Mc263AbandonedCampCatalog.INLINE_PROCESSOR_KEY);
                if (!processor.order().isEmpty()) {
                    throw malformed("persisted Abandoned Camp inline processor drift");
                }
            } else if ("feature".equals(firstName)) {
                kind = ElementKind.FEATURE;
                featureKey = producer.catalog.requireConfiguredFeature(firstValue);
            } else {
                throw malformed("unknown persisted Abandoned Camp pool_element selector");
            }
            input.requireNamed(TAG_STRING, "projection");
            String projection = input.utf("projection", false);
            if (!Mc263AbandonedCampCatalog.RIGID_PROJECTION.equals(projection)) {
                throw malformed("unknown persisted Abandoned Camp projection");
            }
            input.requireNamed(TAG_STRING, "element_type");
            String elementType = input.utf("element_type", false);
            String expectedType = kind == ElementKind.TEMPLATE
                    ? Mc263AbandonedCampCatalog.TEMPLATE_ELEMENT_TYPE
                    : Mc263AbandonedCampCatalog.FEATURE_ELEMENT_TYPE;
            if (!expectedType.equals(elementType)) {
                throw malformed("unknown persisted Abandoned Camp element_type");
            }
            input.requireEnd();
            return new PoolElementFact(kind, templateKey, featureKey, projection);
        }

        private static Junction parseJunction(Reader input) {
            input.requireNamed(TAG_INT, "source_z");
            int sourceZ = input.signedInt();
            input.requireNamed(TAG_INT, "source_x");
            int sourceX = input.signedInt();
            input.requireNamed(TAG_INT, "delta_y");
            int deltaY = input.signedInt();
            input.requireNamed(TAG_INT, "source_ground_y");
            int sourceGroundY = input.signedInt();
            input.requireNamed(TAG_STRING, "dest_proj");
            String projection = input.utf("junction projection", false);
            if (!Mc263AbandonedCampCatalog.RIGID_PROJECTION.equals(projection)) {
                throw malformed("unknown persisted Abandoned Camp junction projection");
            }
            input.requireEnd();
            return new Junction(sourceX, sourceGroundY, sourceZ, deltaY, projection);
        }

        private static Rotation rotation(String value) {
            try {
                return Rotation.valueOf(value);
            } catch (IllegalArgumentException error) {
                throw malformed("unknown persisted Abandoned Camp rotation");
            }
        }

        private static boolean sameBox(Box left, Box right) {
            return left.minX == right.minX && left.minY == right.minY
                    && left.minZ == right.minZ && left.maxX == right.maxX
                    && left.maxY == right.maxY && left.maxZ == right.maxZ;
        }

        private static IllegalArgumentException malformed(String message) {
            return new IllegalArgumentException(message);
        }

        private static final class Reader {
            private final byte[] bytes;
            private int offset;

            private Reader(byte[] bytes) {
                this.bytes = bytes;
            }

            private void requireNamed(int type, String name) {
                requireType(type, name);
                String actual = utf(name + " tag name", false);
                if (!name.equals(actual)) {
                    throw malformed("unexpected persisted Abandoned Camp tag: " + actual
                            + " (expected " + name + ")");
                }
            }

            private void requireType(int type, String label) {
                if (unsignedByte() != type) {
                    throw malformed("unexpected persisted Abandoned Camp " + label + " tag type");
                }
            }

            private void requireEnd() {
                requireType(TAG_END, "compound end");
            }

            private void requireFullyConsumed() {
                if (offset != bytes.length) {
                    throw malformed("persisted Abandoned Camp NBT has trailing bytes");
                }
            }

            private int count(int maximum, String label) {
                int value = signedInt();
                if (value < 0 || value > maximum) {
                    throw malformed("persisted Abandoned Camp " + label + " count is out of bounds");
                }
                return value;
            }

            private String utf(String label, boolean allowEmpty) {
                int length = unsignedShort();
                if (length > bytes.length - offset) {
                    throw malformed("truncated persisted Abandoned Camp " + label);
                }
                int start = offset;
                offset += length;
                if (!allowEmpty && length == 0) {
                    throw malformed("empty persisted Abandoned Camp " + label);
                }
                for (int index = start; index < offset; index++) {
                    int value = Byte.toUnsignedInt(bytes[index]);
                    if (value < 1 || value > 0x7f) {
                        throw malformed("non-ASCII persisted Abandoned Camp " + label);
                    }
                }
                return new String(bytes, start, length, StandardCharsets.US_ASCII);
            }

            private int signedInt() {
                return (unsignedByte() << 24) | (unsignedByte() << 16)
                        | (unsignedByte() << 8) | unsignedByte();
            }

            private int unsignedShort() {
                return (unsignedByte() << 8) | unsignedByte();
            }

            private int unsignedByte() {
                if (offset >= bytes.length) {
                    throw malformed("truncated persisted Abandoned Camp NBT");
                }
                return Byte.toUnsignedInt(bytes[offset++]);
            }
        }
    }

    private static final class PersistedStart {
        private final byte[] bytes;
        private final int referenceOffset;
        private final int references;
        private final String structureKey;
        private final int chunkX, chunkZ;
        private final List<PersistedPiece> pieces;

        private PersistedStart(byte[] bytes, int referenceOffset, int references,
                String structureKey, int chunkX, int chunkZ, List<PersistedPiece> pieces) {
            this.bytes = bytes;
            this.referenceOffset = referenceOffset;
            this.references = references;
            this.structureKey = structureKey;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.pieces = pieces;
        }

        private void requireIdentity(String expectedKey, int expectedChunkX, int expectedChunkZ) {
            if (!expectedKey.equals(structureKey)
                    || expectedChunkX != chunkX || expectedChunkZ != chunkZ) {
                throw new IllegalArgumentException(
                        "persisted Abandoned Camp raw start identity mismatch");
            }
        }
    }

    private static final class PersistedPiece {
        private final ElementKind kind;
        private final String templateKey, featureKey;
        private final Vec position;
        private final Rotation rotation;
        private final String projection;
        private final int groundLevelDelta;
        private final Box boundingBox;
        private final List<Junction> junctions;
        private final int compoundStart, compoundEnd;

        private PersistedPiece(ElementKind kind, String templateKey, String featureKey,
                Vec position, Rotation rotation, String projection, int groundLevelDelta,
                Box boundingBox, List<Junction> junctions, int compoundStart, int compoundEnd) {
            this.kind = kind;
            this.templateKey = templateKey;
            this.featureKey = featureKey;
            this.position = position;
            this.rotation = rotation;
            this.projection = projection;
            this.groundLevelDelta = groundLevelDelta;
            this.boundingBox = boundingBox;
            this.junctions = junctions;
            this.compoundStart = compoundStart;
            this.compoundEnd = compoundEnd;
        }
    }

    private record PoolElementFact(ElementKind kind, String templateKey, String featureKey,
            String projection) {}

    public interface HeightAccess {
        boolean supportsWorldSurfaceHeight();
        int worldSurfaceHeight(int blockX, int blockZ);
    }

    public static final class Start {
        private final String structureKey;
        private final long worldSeed;
        private final int chunkX, chunkZ;
        private final String startPool;
        private final Vec stubPosition;
        private final Box aggregateBoundingBox;
        private final List<Piece> piecesInAcceptedOrder;
        private final List<ConnectorEdge> acceptedConnectorEdges;
        private final RandomReceipt generationRng;
        private final String graphSha256;
        private final boolean empty;
        private final boolean generationFactsAvailable;

        private Start(String structureKey, long worldSeed, int chunkX, int chunkZ,
                String startPool, Vec stubPosition, Box aggregateBoundingBox,
                List<Piece> piecesInAcceptedOrder, List<ConnectorEdge> acceptedConnectorEdges,
                RandomReceipt generationRng, String graphSha256, boolean empty) {
            this(structureKey, worldSeed, chunkX, chunkZ, startPool, stubPosition,
                    aggregateBoundingBox, piecesInAcceptedOrder, acceptedConnectorEdges,
                    generationRng, graphSha256, empty, true);
        }

        private Start(String structureKey, long worldSeed, int chunkX, int chunkZ,
                String startPool, Vec stubPosition, Box aggregateBoundingBox,
                List<Piece> piecesInAcceptedOrder, List<ConnectorEdge> acceptedConnectorEdges,
                RandomReceipt generationRng, String graphSha256, boolean empty,
                boolean generationFactsAvailable) {
            this.structureKey = structureKey; this.worldSeed = worldSeed;
            this.chunkX = chunkX; this.chunkZ = chunkZ; this.startPool = startPool;
            this.stubPosition = stubPosition; this.aggregateBoundingBox = aggregateBoundingBox;
            this.piecesInAcceptedOrder = List.copyOf(piecesInAcceptedOrder);
            this.acceptedConnectorEdges = List.copyOf(acceptedConnectorEdges);
            this.generationRng = generationRng; this.graphSha256 = graphSha256; this.empty = empty;
            this.generationFactsAvailable = generationFactsAvailable;
        }

        static Start empty(String structureKey, long seed, int x, int z, RandomReceipt random) {
            return new Start(structureKey, seed, x, z, null, Vec.ZERO, null, List.of(), List.of(),
                    random, null, true);
        }

        private static Start restored(String structureKey, long seed, int x, int z,
                String startPool, Box aggregate, List<Piece> pieces) {
            return new Start(structureKey, seed, x, z, startPool, Vec.ZERO, aggregate, pieces,
                    List.of(), null, null, false, false);
        }

        public String structureKey() { return structureKey; }
        public long worldSeed() { return worldSeed; }
        public int chunkX() { return chunkX; }
        public int chunkZ() { return chunkZ; }
        public String startPool() { return startPool; }
        public Vec stubPosition() {
            requireGenerationFacts();
            return stubPosition;
        }
        public Box aggregateBoundingBox() { return aggregateBoundingBox; }
        public List<Piece> piecesInAcceptedOrder() { return piecesInAcceptedOrder; }
        public List<ConnectorEdge> acceptedConnectorEdges() {
            requireGenerationFacts();
            return acceptedConnectorEdges;
        }
        public RandomReceipt generationRng() {
            requireGenerationFacts();
            return generationRng;
        }
        public String graphSha256() {
            requireGenerationFacts();
            return graphSha256;
        }
        public boolean empty() { return empty; }

        private void requireGenerationFacts() {
            if (!generationFactsAvailable) {
                throw new IllegalStateException(
                        "generation-only Abandoned Camp facts are unavailable after persisted restore");
            }
        }
    }

    public static final class Piece {
        private final int ordinal;
        private final ElementKind kind;
        private final String templateKey, featureKey;
        private final Vec position;
        private final Rotation rotation;
        private final String projection;
        private final int groundLevelDelta;
        private final Box boundingBox;
        private final List<Junction> junctions;

        private Piece(int ordinal, ElementKind kind, String templateKey, String featureKey,
                Vec position, Rotation rotation, String projection, int groundLevelDelta,
                Box boundingBox, List<Junction> junctions) {
            this.ordinal = ordinal; this.kind = kind; this.templateKey = templateKey;
            this.featureKey = featureKey; this.position = position; this.rotation = rotation;
            this.projection = projection; this.groundLevelDelta = groundLevelDelta;
            this.boundingBox = boundingBox; this.junctions = List.copyOf(junctions);
        }

        public int ordinal() { return ordinal; }
        public ElementKind kind() { return kind; }
        public String templateKey() { return templateKey; }
        public String featureKey() { return featureKey; }
        public String elementClass() { return kind == ElementKind.FEATURE ? FEATURE_CLASS : null; }
        public String processorRegistryKey() {
            return kind == ElementKind.TEMPLATE ? Mc263AbandonedCampCatalog.INLINE_PROCESSOR_KEY : null;
        }
        public List<String> processorOrder() { return List.of(); }
        public Vec position() { return position; }
        public Rotation rotation() { return rotation; }
        public String projection() { return projection; }
        public int groundLevelDelta() { return groundLevelDelta; }
        public Box boundingBox() { return boundingBox; }
        public List<Junction> junctionsInAcceptedOrder() { return junctions; }
    }

    public record Junction(int sourceX, int sourceGroundY, int sourceZ, int deltaY,
            String destinationProjection) {}

    public record ConnectorEdge(int sourcePiece, int sourceConnectorOrdinal, Vec sourcePosition,
            String sourceName, String sourceTarget, String sourcePool, int targetPiece,
            int targetConnectorOrdinal, Vec targetPosition, String targetName,
            String targetTarget, boolean officialCanAttach) {}

    public static final class RandomReceipt {
        private final long state48;
        private final int worldgenCount;
        private final List<Mc263AbandonedCampStartGenerator.Draw> draws;
        private final List<Long> continuationNextLongI64;

        private RandomReceipt(long state48, int worldgenCount,
                List<Mc263AbandonedCampStartGenerator.Draw> draws,
                List<Long> continuationNextLongI64) {
            this.state48 = state48; this.worldgenCount = worldgenCount;
            this.draws = List.copyOf(draws);
            this.continuationNextLongI64 = List.copyOf(continuationNextLongI64);
        }

        public long state48() { return state48; }
        public String state48Hex() { return String.format("%012x", state48); }
        public int worldgenCount() { return worldgenCount; }
        public List<Mc263AbandonedCampStartGenerator.Draw> draws() { return draws; }
        public List<Long> continuationNextLongI64() { return continuationNextLongI64; }
    }

    public static final class Box {
        private final int minX, minY, minZ, maxX, maxY, maxZ;
        public Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted Abandoned Camp box");
            }
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
        public int minX() { return minX; } public int minY() { return minY; }
        public int minZ() { return minZ; } public int maxX() { return maxX; }
        public int maxY() { return maxY; } public int maxZ() { return maxZ; }
        int height() { return maxY - minY + 1; }
        boolean contains(Vec p) {
            return p.x() >= minX && p.x() <= maxX && p.y() >= minY && p.y() <= maxY
                    && p.z() >= minZ && p.z() <= maxZ;
        }
        Box move(int x, int y, int z) {
            return new Box(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
        }
        Box encapsulate(Box other) {
            return new Box(Math.min(minX, other.minX), Math.min(minY, other.minY),
                    Math.min(minZ, other.minZ), Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
        }
        Box inflate(int amount) {
            return new Box(Math.subtractExact(minX, amount), Math.subtractExact(minY, amount),
                    Math.subtractExact(minZ, amount), Math.addExact(maxX, amount),
                    Math.addExact(maxY, amount), Math.addExact(maxZ, amount));
        }
    }

    private static final class MutablePiece {
        final int ordinal;
        final Element element;
        final Template template;
        final Vec position;
        final Rotation rotation;
        final Box boundingBox;
        final int groundLevelDelta;
        final List<Junction> junctions = new ArrayList<>();

        private MutablePiece(int ordinal, Element element, Template template, Vec position,
                Rotation rotation, Box boundingBox, int groundLevelDelta) {
            this.ordinal = ordinal; this.element = element; this.template = template;
            this.position = position; this.rotation = rotation; this.boundingBox = boundingBox;
            this.groundLevelDelta = groundLevelDelta;
        }
        static MutablePiece template(int ordinal, Element element, Template template, Vec position,
                Rotation rotation, Box box, int ground) {
            return new MutablePiece(ordinal, element, template, position, rotation, box, ground);
        }
        static MutablePiece feature(int ordinal, Element element, Vec position, Rotation rotation,
                Box box, int ground) {
            return new MutablePiece(ordinal, element, null, position, rotation, box, ground);
        }
        Piece freeze() {
            return new Piece(ordinal, element.kind(), element.templateKey(), element.featureKey(),
                    position, rotation, element.projection(), groundLevelDelta, boundingBox,
                    junctions);
        }
    }

    private static final class Pending {
        final MutablePiece piece; final CollisionSpace free; final int depth;
        Pending(MutablePiece piece, CollisionSpace free, int depth) {
            this.piece = piece; this.free = free; this.depth = depth;
        }
    }

    private static final class CollisionSpace {
        final Box domain;
        final List<Box> occupied = new ArrayList<>();
        CollisionSpace(Box domain) { this.domain = domain; }
        boolean accept(Box candidate) {
            if (!deflatedInside(candidate, domain)) return false;
            for (Box box : occupied) if (deflatedIntersects(candidate, box)) return false;
            occupied.add(candidate);
            return true;
        }
        static boolean deflatedInside(Box inner, Box outer) {
            return inner.minX + .25 >= outer.minX && inner.minY + .25 >= outer.minY
                    && inner.minZ + .25 >= outer.minZ && inner.maxX + .75 <= outer.maxX + 1.0
                    && inner.maxY + .75 <= outer.maxY + 1.0
                    && inner.maxZ + .75 <= outer.maxZ + 1.0;
        }
        static boolean deflatedIntersects(Box a, Box b) {
            return a.minX + .25 < b.maxX + 1.0 && a.maxX + .75 > b.minX
                    && a.minY + .25 < b.maxY + 1.0 && a.maxY + .75 > b.minY
                    && a.minZ + .25 < b.maxZ + 1.0 && a.maxZ + .75 > b.minZ;
        }
    }

    private record Orientation(Direction front, Direction top) {}
    private enum Direction { DOWN, UP, NORTH, SOUTH, WEST, EAST }
    private record WorldConnector(Connector connector, Vec position, Direction front, Direction top) {}

    /** Synthetic official FeaturePoolElement jigsaw endpoint. */
    private static final class SyntheticConnector {
        static final Connector VALUE;
        static {
            try {
                var constructor = Connector.class.getDeclaredConstructor(int.class, Vec.class,
                        String.class, String.class, String.class, String.class, String.class,
                        int.class, int.class);
                constructor.setAccessible(true);
                VALUE = constructor.newInstance(0, Vec.ZERO,
                        "minecraft:jigsaw[orientation=down_south]", "ROLLABLE", "",
                        "minecraft:empty", Mc263AbandonedCampCatalog.EMPTY_POOL_KEY, 0, 0);
            } catch (ReflectiveOperationException error) {
                throw new ExceptionInInitializerError(error);
            }
        }
    }
}
