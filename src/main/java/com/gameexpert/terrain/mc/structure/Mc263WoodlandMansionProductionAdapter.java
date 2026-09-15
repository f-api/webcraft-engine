package com.gameexpert.terrain.mc.structure;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Current-only persisted-graph adapter for one Minecraft 26.3 Woodland Mansion start.
 *
 * <p>The adapter consumes an already authenticated {@link Mc263StructureCarrier}; it never runs
 * the Mansion producer. Piece placement facts come from the persisted {@code minecraft:wmp} NBT,
 * accepted template semantics come from the pinned grammar, and accepted parent/encounter facts
 * come from the carrier's STRGRF01 section. Only the existing persisted settlement seam can query,
 * fork, mutate, or advance either caller RNG.</p>
 */
public final class Mc263WoodlandMansionProductionAdapter {
    private static final String STRUCTURE_KEY = Mc263WoodlandMansionSettlement.STRUCTURE_KEY;
    private static final String PIECE_TYPE = "minecraft:wmp";
    private static final String TEMPLATE_PREFIX = "minecraft:woodland_mansion/";

    public Preflight preflightPersisted(Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ValidStart start,
            Mc263WoodlandMansionSettlement.Request request) {
        Objects.requireNonNull(carrier, "Mansion persisted structure carrier");
        Objects.requireNonNull(start, "Mansion persisted start");
        Objects.requireNonNull(request, "Mansion persisted settlement request");

        Mc263StructureCarrier.StructureDefinition definition =
                carrier.registry().require(STRUCTURE_KEY);
        require(request.structureKey().equals(STRUCTURE_KEY),
                "Mansion persisted request key drift");
        require(start.startKey().equals(STRUCTURE_KEY + "@" + request.startChunkX()
                        + "," + request.startChunkZ())
                        && start.originChunkX() == request.startChunkX()
                        && start.originChunkZ() == request.startChunkZ(),
                "Mansion persisted request/start identity drift");
        require(start.references() == 0 || start.references() == 1,
                "Mansion persisted references must be 0 or 1");

        Mc263StructureCarrier.ChunkReferences references = carrier.referenceChunk(
                request.targetChunkX(), request.targetChunkZ()).orElseThrow(() ->
                        new IllegalArgumentException("missing Mansion destination references"));
        long matches = carrier.resolveStarts(references, STRUCTURE_KEY).stream()
                .filter(start::equals).count();
        require(matches == 1, "Mansion persisted destination does not resolve the supplied start");

        Mc263StructureCarrier.RawStartPayload raw =
                carrier.requireRawStartPayload(STRUCTURE_KEY, start);
        Mc263StructureCarrier.ProducerGraphPayload graph =
                carrier.requireProducerGraphPayload(STRUCTURE_KEY, start);
        require(graph.structureId().equals(STRUCTURE_KEY)
                        && graph.startKey().equals(start.startKey())
                        && graph.originChunkX() == start.originChunkX()
                        && graph.originChunkZ() == start.originChunkZ(),
                "Mansion persisted graph identity drift");

        Mc263WoodlandMansionGrammar grammar = Mc263WoodlandMansionGrammar.loadAccepted();
        Map<String, Mc263WoodlandMansionGrammar.Template> templates = grammar.templates();
        ArrayList<Mc263WoodlandMansionProducer.Piece> pieces = new ArrayList<>();
        Mc263WoodlandMansionGrammar.Box aggregate = null;
        int rootY = 0;
        for (int ordinal = 0; ordinal < start.orderedPieces().size(); ordinal++) {
            Mc263StructureCarrier.Piece typed = start.orderedPieces().get(ordinal);
            require(PIECE_TYPE.equals(typed.pieceType()) && !typed.poolElement(),
                    "unknown Mansion persisted piece type");
            DecodedPiece decoded = decodePiece(typed.persistedPayload().binaryNbtCompound());
            require(decoded.boundingBox().equals(box(typed.boundingBox())),
                    "Mansion persisted typed/raw piece bounds drift");
            Mc263WoodlandMansionGrammar.Template template = templates.get(decoded.templateKey());
            require(template != null, "unknown Mansion persisted template fact");
            require(decoded.boundingBox().equals(bounds(template, decoded.origin(),
                            decoded.rotation(), decoded.mirror())),
                    "Mansion persisted template bounds drift");
            if (ordinal == 0) rootY = decoded.origin().y();
            Mc263WoodlandMansionProducer.Ownership ownership = ownership(
                    decoded.templateKey(), Math.subtractExact(decoded.origin().y(), rootY));
            Mc263WoodlandMansionProducer.Piece piece = new Mc263WoodlandMansionProducer.Piece(
                    ordinal, decoded.templateKey(), decoded.origin(), decoded.rotation(),
                    decoded.mirror(), ownership, decoded.boundingBox(),
                    markers(template, decoded.origin(), decoded.rotation(), decoded.mirror()));
            pieces.add(piece);
            aggregate = aggregate == null ? decoded.boundingBox()
                    : encapsulate(aggregate, decoded.boundingBox());
        }
        require(!pieces.isEmpty() && aggregate != null, "empty Mansion persisted piece graph");
        require(graph.orderedEdges().size() == pieces.size() - 1,
                "Mansion persisted graph cardinality drift");
        for (int index = 0; index < graph.orderedEdges().size(); index++) {
            Mc263StructureCarrier.ProducerEdge edge = graph.orderedEdges().get(index);
            Mc263WoodlandMansionProducer.Piece target = pieces.get(index + 1);
            require(edge.targetPieceOrdinal() == target.ordinal()
                            && edge.sourcePieceOrdinal() >= 0
                            && edge.sourcePieceOrdinal() < edge.targetPieceOrdinal(),
                    "Mansion persisted graph encounter fact drift");
            require(edge.selectedPool().equals(target.templateKey())
                            && edge.resolvedAlias().equals(target.templateKey()),
                    "unknown Mansion persisted graph fact");
        }

        Mc263WoodlandMansionProducer.PersistedCarrier persisted =
                Mc263WoodlandMansionProducer.encodeCarrier(pieces,
                        start.originChunkX(), start.originChunkZ());
        require(Arrays.equals(raw.predecessorBinaryNbtCompound(),
                        persisted.structureStart().bytes())
                        && Arrays.equals(raw.successorBinaryNbtCompound(),
                                persisted.mutableSuccessorAfterOneReference().bytes()),
                "Mansion persisted raw start/successor drift");
        require(start.references() == referenceValue(start.references() == 0
                        ? raw.predecessorBinaryNbtCompound()
                        : raw.successorBinaryNbtCompound()),
                "Mansion persisted typed/raw references drift");
        for (int index = 0; index < pieces.size(); index++) {
            require(Arrays.equals(start.orderedPieces().get(index).persistedPayload()
                            .binaryNbtCompound(), persisted.pieces().get(index).bytes()),
                    "Mansion persisted piece payload drift at " + index);
        }
        return new Preflight(definition.registryOrdinal(), start.references(), pieces,
                aggregate, persisted);
    }

    public Execution executePersisted(Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ValidStart start,
            Mc263WoodlandMansionSettlement.Request request,
            Mc263WoodlandMansionSettlement.WorldTransaction transaction,
            Mc263WoodlandMansionSettlement.PlacementRandom placementRandom,
            Mc263WoodlandMansionSettlement.ServerLevelRandom worldGenRegionRandom) {
        Objects.requireNonNull(transaction, "Mansion persisted transaction");
        Objects.requireNonNull(placementRandom, "Mansion persisted placement RNG");
        Objects.requireNonNull(worldGenRegionRandom, "Mansion persisted WorldGenRegion RNG");
        Preflight preflight = preflightPersisted(carrier, start, request);
        Mc263WoodlandMansionSettlement.Settlement result =
                Mc263WoodlandMansionSettlement.executePersisted(request, preflight.pieces(),
                        preflight.aggregateBoundingBox(), preflight.persistedCarrier(), transaction,
                        placementRandom, worldGenRegionRandom);
        require(Arrays.equals(result.successor().predecessor(),
                        preflight.persistedCarrier().structureStart().bytes())
                        && Arrays.equals(result.successor().mutableSuccessor(),
                                preflight.persistedCarrier()
                                        .mutableSuccessorAfterOneReference().bytes()),
                "Mansion settlement returned a foreign successor carrier");
        return new Execution(preflight.registryOrdinal(), preflight.referencesBefore(),
                successorCarrier(carrier, start), result.serverRandom().state(), result);
    }

    private static Mc263StructureCarrier successorCarrier(Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ValidStart accepted) {
        if (accepted.references() == 1) return carrier;
        ArrayList<Mc263StructureCarrier.ChunkStarts> chunks = new ArrayList<>();
        int replacements = 0;
        for (Mc263StructureCarrier.ChunkStarts chunk : carrier.startChunks()) {
            ArrayList<Mc263StructureCarrier.StartEntry> entries = new ArrayList<>();
            for (Mc263StructureCarrier.StartEntry entry : chunk.orderedStarts()) {
                if (entry.body().equals(accepted)) {
                    Mc263StructureCarrier.ValidStart successor = new Mc263StructureCarrier.ValidStart(
                            accepted.startKey(), accepted.originChunkX(), accepted.originChunkZ(), 1,
                            accepted.adjustedBoundingBox(), accepted.orderedPieces());
                    entries.add(new Mc263StructureCarrier.StartEntry(entry.structureId(), successor));
                    replacements++;
                } else {
                    entries.add(entry);
                }
            }
            chunks.add(chunk.withStarts(entries));
        }
        require(replacements == 1, "Mansion successor start replacement cardinality drift");
        return new Mc263StructureCarrier(carrier.registry(), chunks, carrier.referenceChunks(),
                carrier.rawStartPayloads(), carrier.producerGraphPayloads());
    }

    private static DecodedPiece decodePiece(byte[] bytes) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            require(input.readUnsignedByte() == 10 && input.readUTF().isEmpty(),
                    "Mansion persisted piece root drift");
            requireTag(input, 11, "BB");
            require(input.readInt() == 6, "Mansion persisted BB length drift");
            Mc263WoodlandMansionGrammar.Box box = new Mc263WoodlandMansionGrammar.Box(
                    input.readInt(), input.readInt(), input.readInt(), input.readInt(),
                    input.readInt(), input.readInt());
            requireTag(input, 8, "Rot");
            Mc263WoodlandMansionGrammar.Rotation rotation = enumValue(
                    Mc263WoodlandMansionGrammar.Rotation.class, input.readUTF(), "rotation");
            requireTag(input, 8, "id");
            require(PIECE_TYPE.equals(input.readUTF()), "Mansion persisted piece id drift");
            requireTag(input, 3, "TPY"); int y = input.readInt();
            requireTag(input, 8, "Mi");
            Mc263WoodlandMansionGrammar.Mirror mirror = enumValue(
                    Mc263WoodlandMansionGrammar.Mirror.class, input.readUTF(), "mirror");
            requireTag(input, 3, "GD");
            require(input.readInt() == 0, "Mansion persisted ground delta drift");
            requireTag(input, 3, "TPX"); int x = input.readInt();
            requireTag(input, 3, "O");
            require(input.readInt() == 2, "Mansion persisted orientation drift");
            requireTag(input, 3, "TPZ"); int z = input.readInt();
            requireTag(input, 8, "Template");
            String template = TEMPLATE_PREFIX + input.readUTF();
            require(input.readUnsignedByte() == 0 && input.read() == -1,
                    "Mansion persisted piece framing drift");
            return new DecodedPiece(template,
                    new Mc263WoodlandMansionGrammar.Pos(x, y, z), rotation, mirror, box);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("malformed Mansion persisted piece NBT", exception);
        }
    }

    private static int referenceValue(byte[] bytes) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            require(input.readUnsignedByte() == 10 && input.readUTF().isEmpty(),
                    "Mansion persisted start root drift");
            requireTag(input, 3, "references");
            return input.readInt();
        } catch (IOException exception) {
            throw new IllegalArgumentException("malformed Mansion persisted start NBT", exception);
        }
    }

    private static void requireTag(DataInputStream input, int type, String name) throws IOException {
        require(input.readUnsignedByte() == type && input.readUTF().equals(name),
                "Mansion persisted tag/order drift at " + name);
    }

    private static <T extends Enum<T>> T enumValue(
            Class<T> type, String value, String label) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown Mansion persisted " + label, exception);
        }
    }

    private static Mc263WoodlandMansionGrammar.Box box(
            Mc263StructureCarrier.BoundingBox value) {
        return new Mc263WoodlandMansionGrammar.Box(value.minX(), value.minY(), value.minZ(),
                value.maxX(), value.maxY(), value.maxZ());
    }

    private static Mc263WoodlandMansionGrammar.Box bounds(
            Mc263WoodlandMansionGrammar.Template template,
            Mc263WoodlandMansionGrammar.Pos origin,
            Mc263WoodlandMansionGrammar.Rotation rotation,
            Mc263WoodlandMansionGrammar.Mirror mirror) {
        int maxX = template.size().x() - 1;
        int maxY = template.size().y() - 1;
        int maxZ = template.size().z() - 1;
        int minWorldX = Integer.MAX_VALUE, minWorldZ = Integer.MAX_VALUE;
        int maxWorldX = Integer.MIN_VALUE, maxWorldZ = Integer.MIN_VALUE;
        for (int x : new int[] {0, maxX}) {
            for (int z : new int[] {0, maxZ}) {
                Mc263WoodlandMansionGrammar.Pos transformed = transform(
                        new Mc263WoodlandMansionGrammar.Pos(x, 0, z), mirror, rotation);
                int worldX = Math.addExact(origin.x(), transformed.x());
                int worldZ = Math.addExact(origin.z(), transformed.z());
                minWorldX = Math.min(minWorldX, worldX);
                minWorldZ = Math.min(minWorldZ, worldZ);
                maxWorldX = Math.max(maxWorldX, worldX);
                maxWorldZ = Math.max(maxWorldZ, worldZ);
            }
        }
        return new Mc263WoodlandMansionGrammar.Box(minWorldX, origin.y(), minWorldZ,
                maxWorldX, Math.addExact(origin.y(), maxY), maxWorldZ);
    }

    private static List<Mc263WoodlandMansionGrammar.Marker> markers(
            Mc263WoodlandMansionGrammar.Template template,
            Mc263WoodlandMansionGrammar.Pos origin,
            Mc263WoodlandMansionGrammar.Rotation rotation,
            Mc263WoodlandMansionGrammar.Mirror mirror) {
        ArrayList<Mc263WoodlandMansionGrammar.Marker> markers = new ArrayList<>();
        for (Mc263WoodlandMansionGrammar.Command command : template.commands()) {
            if (command instanceof Mc263WoodlandMansionGrammar.Data data
                    && data.semantic() instanceof Mc263WoodlandMansionGrammar.StructureMarker marker) {
                Mc263WoodlandMansionGrammar.Pos local = transform(data.position(), mirror, rotation);
                markers.add(new Mc263WoodlandMansionGrammar.Marker(markers.size(), marker.metadata(),
                        origin.add(local)));
            }
        }
        return List.copyOf(markers);
    }

    private static Mc263WoodlandMansionGrammar.Pos transform(
            Mc263WoodlandMansionGrammar.Pos local,
            Mc263WoodlandMansionGrammar.Mirror mirror,
            Mc263WoodlandMansionGrammar.Rotation rotation) {
        int x = local.x(), z = local.z();
        if (mirror == Mc263WoodlandMansionGrammar.Mirror.LEFT_RIGHT) z = Math.negateExact(z);
        else if (mirror == Mc263WoodlandMansionGrammar.Mirror.FRONT_BACK) x = Math.negateExact(x);
        return switch (rotation) {
            case NONE -> new Mc263WoodlandMansionGrammar.Pos(x, local.y(), z);
            case CLOCKWISE_90 -> new Mc263WoodlandMansionGrammar.Pos(-z, local.y(), x);
            case CLOCKWISE_180 -> new Mc263WoodlandMansionGrammar.Pos(-x, local.y(), -z);
            case COUNTERCLOCKWISE_90 -> new Mc263WoodlandMansionGrammar.Pos(z, local.y(), -x);
        };
    }

    private static Mc263WoodlandMansionProducer.Ownership ownership(String template, int y) {
        String name = template.substring(TEMPLATE_PREFIX.length());
        if (name.equals("roof") || name.equals("roof_corner") || name.equals("roof_front")
                || name.equals("roof_inner_corner")) {
            if (y == 16 || y == 19) return new Mc263WoodlandMansionProducer.Ownership(
                    Mc263WoodlandMansionProducer.FloorOwner.LOWER_ROOF,
                    Mc263WoodlandMansionProducer.GridOwner.BASE_GRID, y);
            if (y == 27 || y == 30) return new Mc263WoodlandMansionProducer.Ownership(
                    Mc263WoodlandMansionProducer.FloorOwner.UPPER_ROOF,
                    Mc263WoodlandMansionProducer.GridOwner.THIRD_FLOOR_GRID, y);
        } else if (name.equals("small_wall") || name.equals("small_wall_corner")) {
            if (y == 16) return new Mc263WoodlandMansionProducer.Ownership(
                    Mc263WoodlandMansionProducer.FloorOwner.LOWER_ROOF,
                    Mc263WoodlandMansionProducer.GridOwner.BASE_GRID, y);
            if (y == 27) return new Mc263WoodlandMansionProducer.Ownership(
                    Mc263WoodlandMansionProducer.FloorOwner.UPPER_ROOF,
                    Mc263WoodlandMansionProducer.GridOwner.THIRD_FLOOR_GRID, y);
        } else {
            if (y == 0 || y == 1) return new Mc263WoodlandMansionProducer.Ownership(
                    Mc263WoodlandMansionProducer.FloorOwner.FIRST_FLOOR,
                    Mc263WoodlandMansionProducer.GridOwner.BASE_GRID, y);
            if (y == 8 || y == 9) return new Mc263WoodlandMansionProducer.Ownership(
                    Mc263WoodlandMansionProducer.FloorOwner.SECOND_FLOOR,
                    Mc263WoodlandMansionProducer.GridOwner.BASE_GRID, y);
            if (y == 19 || y == 20) return new Mc263WoodlandMansionProducer.Ownership(
                    Mc263WoodlandMansionProducer.FloorOwner.THIRD_FLOOR,
                    Mc263WoodlandMansionProducer.GridOwner.THIRD_FLOOR_GRID, y);
        }
        throw new IllegalArgumentException("unknown Mansion persisted ownership fact");
    }

    private static Mc263WoodlandMansionGrammar.Box encapsulate(
            Mc263WoodlandMansionGrammar.Box first,
            Mc263WoodlandMansionGrammar.Box second) {
        return new Mc263WoodlandMansionGrammar.Box(
                Math.min(first.minX(), second.minX()), Math.min(first.minY(), second.minY()),
                Math.min(first.minZ(), second.minZ()), Math.max(first.maxX(), second.maxX()),
                Math.max(first.maxY(), second.maxY()), Math.max(first.maxZ(), second.maxZ()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static final class DecodedPiece {
        private final String templateKey;
        private final Mc263WoodlandMansionGrammar.Pos origin;
        private final Mc263WoodlandMansionGrammar.Rotation rotation;
        private final Mc263WoodlandMansionGrammar.Mirror mirror;
        private final Mc263WoodlandMansionGrammar.Box boundingBox;

        private DecodedPiece(String templateKey, Mc263WoodlandMansionGrammar.Pos origin,
                Mc263WoodlandMansionGrammar.Rotation rotation,
                Mc263WoodlandMansionGrammar.Mirror mirror,
                Mc263WoodlandMansionGrammar.Box boundingBox) {
            this.templateKey = templateKey; this.origin = origin; this.rotation = rotation;
            this.mirror = mirror; this.boundingBox = boundingBox;
        }
        private String templateKey() { return templateKey; }
        private Mc263WoodlandMansionGrammar.Pos origin() { return origin; }
        private Mc263WoodlandMansionGrammar.Rotation rotation() { return rotation; }
        private Mc263WoodlandMansionGrammar.Mirror mirror() { return mirror; }
        private Mc263WoodlandMansionGrammar.Box boundingBox() { return boundingBox; }
    }

    public static final class Preflight {
        private final int registryOrdinal;
        private final int referencesBefore;
        private final List<Mc263WoodlandMansionProducer.Piece> pieces;
        private final Mc263WoodlandMansionGrammar.Box aggregateBoundingBox;
        private final Mc263WoodlandMansionProducer.PersistedCarrier persistedCarrier;

        private Preflight(int registryOrdinal, int referencesBefore,
                List<Mc263WoodlandMansionProducer.Piece> pieces,
                Mc263WoodlandMansionGrammar.Box aggregateBoundingBox,
                Mc263WoodlandMansionProducer.PersistedCarrier persistedCarrier) {
            this.registryOrdinal = registryOrdinal; this.referencesBefore = referencesBefore;
            this.pieces = List.copyOf(pieces); this.aggregateBoundingBox = aggregateBoundingBox;
            this.persistedCarrier = persistedCarrier;
        }
        public int registryOrdinal() { return registryOrdinal; }
        public int referencesBefore() { return referencesBefore; }
        public List<Mc263WoodlandMansionProducer.Piece> pieces() { return pieces; }
        public Mc263WoodlandMansionGrammar.Box aggregateBoundingBox() { return aggregateBoundingBox; }
        public Mc263WoodlandMansionProducer.PersistedCarrier persistedCarrier() {
            return persistedCarrier;
        }
    }

    public static final class Execution {
        private final int registryOrdinal;
        private final int referencesBefore;
        private final Mc263StructureCarrier successorCarrier;
        private final Mc263WoodlandMansionSettlement.ServerRandomState worldGenRegionRandomState;
        private final Mc263WoodlandMansionSettlement.Settlement result;

        private Execution(int registryOrdinal, int referencesBefore,
                Mc263StructureCarrier successorCarrier,
                Mc263WoodlandMansionSettlement.ServerRandomState worldGenRegionRandomState,
                Mc263WoodlandMansionSettlement.Settlement result) {
            this.registryOrdinal = registryOrdinal; this.referencesBefore = referencesBefore;
            this.successorCarrier = successorCarrier;
            this.worldGenRegionRandomState = worldGenRegionRandomState; this.result = result;
        }
        public int registryOrdinal() { return registryOrdinal; }
        public int referencesBefore() { return referencesBefore; }
        public int referencesAfter() { return 1; }
        public Mc263StructureCarrier successorCarrier() { return successorCarrier; }
        public Mc263WoodlandMansionSettlement.ServerRandomState worldGenRegionRandomState() {
            return worldGenRegionRandomState;
        }
        public Mc263WoodlandMansionSettlement.Settlement result() { return result; }
    }
}
