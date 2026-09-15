package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Bounds;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ConnectorSpec;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Direction;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ElementSpec;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ElementType;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Grammar;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Joint;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.PoolSpec;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.AcceptedEdge;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.ExecutionLimits;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.ExecutionPlan;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.HeightResolver;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.PiecePlacement;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.PlannerRngReceipt;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.TracedPlan;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dormant deterministic Minecraft 26.3 Pillager Outpost start producer.
 *
 * <p>The accepted typed grammar is adapted once to the generic Jigsaw D2 executor. Generation uses
 * exactly one traced executor pass. Repeated internal terrain projections at the same coordinate are
 * memoized so the caller observes the exact vanilla ChunkGenerator#getBaseHeight boundary rather
 * than generic collision retries. No template settlement or world mutation occurs here.</p>
 */
public final class Mc263PillagerOutpostProducer {
    public static final String STRUCTURE_KEY = "minecraft:pillager_outpost";
    public static final String HEIGHTMAP = "WORLD_SURFACE_WG";
    public static final String CARRIER_FORMAT = "minecraft-26.3-pillager-outpost-start-nbt-v1";

    private static final int MIN_BUILD_Y = -64;
    private static final int MAX_BUILD_Y = 320;
    private static final int TERRAIN_PADDING = 12;
    private static final ExecutionLimits LIMITS = new ExecutionLimits(256, 4_096, 100_000);
    private static final String INLINE = "inline";
    private static final String OUTPOST_ROT = "minecraft:outpost_rot";
    private static final String LIST_TYPE = "minecraft:list_pool_element";
    private static final String LEGACY_SINGLE_TYPE = "minecraft:legacy_single_pool_element";

    private final Grammar grammar;
    private final List<CarrierChild> towerChildren;

    private Mc263PillagerOutpostProducer(Mc263PillagerOutpostGrammar.Corpus grammarCorpus) {
        Objects.requireNonNull(grammarCorpus, "Pillager Outpost grammar");
        Mc263PillagerOutpostGrammar.Evidence evidence = grammarCorpus.evidence();
        Mc263PillagerOutpostStartAuthority.authenticateProductionFacts(authorityFacts(evidence));
        this.grammar = adaptGrammar(evidence);
        this.towerChildren = towerChildren(evidence);
    }

    public static Mc263PillagerOutpostProducer pinned() {
        return Holder.PINNED;
    }

    private static final class Holder {
        private static final Mc263PillagerOutpostProducer PINNED =
                new Mc263PillagerOutpostProducer(Mc263PillagerOutpostGrammar.pinned());
    }

    /** Generates one complete immutable start and publishes it exactly once after full validation. */
    public Start generate(long worldSeed, int chunkX, int chunkZ, WorldAccess world,
            Publisher publisher) {
        preflight(world, publisher);
        int minBuildY = world.minBuildY();
        int maxBuildY = world.maxBuildY();
        if (minBuildY != MIN_BUILD_Y || maxBuildY != MAX_BUILD_Y) {
            throw new IllegalArgumentException("unsupported Pillager Outpost build-height boundary: "
                    + minBuildY + ".." + maxBuildY);
        }

        QueryingHeightResolver heights = new QueryingHeightResolver(world, minBuildY, maxBuildY);
        TracedPlan traced = Mc263JigsawStructureExecutor.planWithTrace(
                worldSeed, chunkX, chunkZ, grammar, heights, LIMITS);
        ExecutionPlan plan = traced.executionPlan();
        require(plan.present(), "Pillager Outpost start unexpectedly absent");
        require(plan.structureKey().equals(STRUCTURE_KEY), "Pillager Outpost structure-key drift");
        require(plan.chunkX() == chunkX && plan.chunkZ() == chunkZ,
                "Pillager Outpost chunk identity drift");
        require(!plan.pieces().isEmpty(), "Pillager Outpost generated an empty piece graph");

        validateGeneratedPlan(plan, traced.acceptedEdges());
        Box aggregate = aggregate(plan.pieces()).inflate(TERRAIN_PADDING);
        Vec3i stub = new Vec3i(plan.centerX(), plan.centerY(), plan.centerZ());
        Carrier predecessorCarrier = encodeCarrier(plan, chunkX, chunkZ);
        Start generated = new Start(worldSeed, chunkX, chunkZ, aggregate, stub, plan,
                traced.acceptedEdges(), heights.receipt(), traced.generationRng(), predecessorCarrier);
        Carrier persistedCarrier = Mc263PillagerOutpostPersistedAuthority.bind(generated);
        Start start = new Start(worldSeed, chunkX, chunkZ, aggregate, stub, plan,
                traced.acceptedEdges(), heights.receipt(), traced.generationRng(), persistedCarrier);
        Mc263PillagerOutpostPersistedAuthority.authenticate(start);
        publisher.publishAtomically(start);
        return start;
    }

    private static void preflight(WorldAccess world, Publisher publisher) {
        if (world == null || publisher == null) {
            throw new IllegalArgumentException("Pillager Outpost world/publisher are required");
        }
        boolean heightmap = world.supportsHeightmap(HEIGHTMAP);
        boolean buildHeight = world.supportsBuildHeightBoundary();
        boolean publication = publisher.supports(STRUCTURE_KEY, CARRIER_FORMAT);
        if (!heightmap || !buildHeight || !publication) {
            throw new IllegalArgumentException(
                    "Pillager Outpost capability closure is incomplete");
        }
    }

    private static Mc263PillagerOutpostStartAuthority.ProductionFacts authorityFacts(
            Mc263PillagerOutpostGrammar.Evidence evidence) {
        ArrayList<String> pools = new ArrayList<>();
        ArrayList<String> processors = new ArrayList<>();
        for (Mc263PillagerOutpostGrammar.Pool pool : evidence.poolsInExecutionOrder()) {
            pools.add(pool.key());
            pools.add(pool.fallback());
            for (Mc263PillagerOutpostGrammar.PoolElement element : pool.elementsInDeclaredOrder()) {
                if (element instanceof Mc263PillagerOutpostGrammar.SingleElement single) {
                    processors.add(authorityProcessor(single.processorList()));
                } else if (element instanceof Mc263PillagerOutpostGrammar.ListElement list) {
                    for (Mc263PillagerOutpostGrammar.SingleChild child
                            : list.childrenInDeclaredOrder()) {
                        processors.add(authorityProcessor(child.processorList()));
                    }
                }
            }
        }
        List<String> templates = evidence.templatesInEncounterOrder().stream()
                .map(Mc263PillagerOutpostGrammar.Template::key).distinct().sorted().toList();
        return new Mc263PillagerOutpostStartAuthority.ProductionFacts(
                evidence.structure().key(), pools.stream().distinct().sorted().toList(), templates,
                processors.stream().distinct().sorted().toList());
    }

    private static String authorityProcessor(String processor) {
        if (processor.equals(INLINE)) return "inline:empty";
        if (processor.equals(OUTPOST_ROT)) return OUTPOST_ROT;
        throw new IllegalArgumentException(
                "unknown Pillager Outpost production-authority processor: " + processor);
    }

    private static Grammar adaptGrammar(Mc263PillagerOutpostGrammar.Evidence evidence) {
        Objects.requireNonNull(evidence, "Pillager Outpost typed evidence");
        require(evidence.structure().key().equals(STRUCTURE_KEY),
                "unknown Pillager Outpost structure key");
        ArrayList<PoolSpec> pools = new ArrayList<>();
        for (Mc263PillagerOutpostGrammar.Pool pool : evidence.poolsInExecutionOrder()) {
            evidence.requirePool(pool.key());
            ArrayList<ElementSpec> elements = new ArrayList<>();
            for (Mc263PillagerOutpostGrammar.PoolElement element : pool.elementsInDeclaredOrder()) {
                if (element instanceof Mc263PillagerOutpostGrammar.SingleElement single) {
                    elements.add(adaptSingle(evidence, single.template(), single.weight(),
                            single.projection(), single.processorList()));
                } else if (element instanceof Mc263PillagerOutpostGrammar.ListElement list) {
                    elements.add(adaptList(evidence, list));
                } else if (element instanceof Mc263PillagerOutpostGrammar.EmptyElement empty) {
                    elements.add(ElementSpec.empty(empty.weight()));
                } else {
                    throw new IllegalArgumentException("unknown Pillager Outpost pool element");
                }
            }
            pools.add(new PoolSpec(pool.key(), pool.fallback(), elements));
        }
        Grammar grammar = new Grammar(STRUCTURE_KEY, pools);
        Mc263JigsawStructureBoundary.validate(grammar);
        return grammar;
    }

    private static ElementSpec adaptSingle(Mc263PillagerOutpostGrammar.Evidence evidence,
            String templateKey, int weight, Mc263PillagerOutpostGrammar.Projection projection,
            String processor) {
        Mc263PillagerOutpostGrammar.Template template = evidence.requireTemplate(templateKey);
        return ElementSpec.single(templateKey, weight, projection(projection),
                processor(processor), bounds(template), connectors(template));
    }

    private static ElementSpec adaptList(Mc263PillagerOutpostGrammar.Evidence evidence,
            Mc263PillagerOutpostGrammar.ListElement list) {
        require(list.projection() == Mc263PillagerOutpostGrammar.Projection.RIGID,
                "unsupported Pillager Outpost list projection");
        require(list.childrenInDeclaredOrder().size() == 2,
                "unknown Pillager Outpost list composition");
        Mc263PillagerOutpostGrammar.SingleChild first = list.childrenInDeclaredOrder().get(0);
        Mc263PillagerOutpostGrammar.Template firstTemplate = evidence.requireTemplate(first.template());
        Bounds union = null;
        ArrayList<String> components = new ArrayList<>();
        List<ConnectorSpec> connectorSurface = connectors(firstTemplate);
        for (Mc263PillagerOutpostGrammar.SingleChild child : list.childrenInDeclaredOrder()) {
            require(child.projection() == list.projection(),
                    "Pillager Outpost list child projection drift");
            Mc263PillagerOutpostGrammar.Template template = evidence.requireTemplate(child.template());
            require(sameConnectors(connectors(template), connectorSurface),
                    "Pillager Outpost list connector surface drift");
            processor(child.processorList());
            components.add(child.template());
            union = union == null ? bounds(template) : union(union, bounds(template));
        }
        return new ElementSpec(ElementType.LIST, first.template(), list.weight(),
                projection(list.projection()), "", union, connectorSurface, components);
    }

    private static List<CarrierChild> towerChildren(Mc263PillagerOutpostGrammar.Evidence evidence) {
        Mc263PillagerOutpostGrammar.Pool towers =
                evidence.requirePool("minecraft:pillager_outpost/towers");
        require(towers.elementsInDeclaredOrder().size() == 1
                        && towers.elementsInDeclaredOrder().get(0)
                                instanceof Mc263PillagerOutpostGrammar.ListElement,
                "unknown Pillager Outpost tower pool");
        Mc263PillagerOutpostGrammar.ListElement list =
                (Mc263PillagerOutpostGrammar.ListElement) towers.elementsInDeclaredOrder().get(0);
        return list.childrenInDeclaredOrder().stream()
                .map(child -> new CarrierChild(child.template(), child.processorList()))
                .toList();
    }

    private static void validateGeneratedPlan(ExecutionPlan plan, List<AcceptedEdge> edges) {
        require(edges.size() == plan.pieces().size() - 1,
                "Pillager Outpost generated graph edge cardinality drift");
        for (int index = 0; index < plan.pieces().size(); index++) {
            PiecePlacement piece = plan.pieces().get(index);
            if (piece.type() == ElementType.LEGACY_SINGLE) {
                require(!piece.elementKey().isEmpty() && piece.components().isEmpty(),
                        "unknown Pillager Outpost generated single piece");
                require(piece.processor().isEmpty(),
                        "unknown Pillager Outpost generated single processor");
            } else if (piece.type() == ElementType.LIST) {
                require(piece.components().equals(List.of(
                                "minecraft:pillager_outpost/watchtower",
                                "minecraft:pillager_outpost/watchtower_overgrown")),
                        "unknown Pillager Outpost generated list piece");
                require(piece.processor().isEmpty(),
                        "unsupported Pillager Outpost list processor");
            } else {
                throw new IllegalArgumentException("unknown Pillager Outpost generated piece type: "
                        + piece.type());
            }
            require(piece.bounds() != null, "Pillager Outpost generated piece has no bounds");
        }
        for (int index = 0; index < edges.size(); index++) {
            AcceptedEdge edge = edges.get(index);
            require(edge.childPieceOrdinal() == index + 1
                            && edge.parentPieceOrdinal() >= 0
                            && edge.parentPieceOrdinal() < edge.childPieceOrdinal(),
                    "Pillager Outpost generated parent/child order drift");
            require(edge.resolvedAliasTarget().isEmpty(),
                    "unexpected Pillager Outpost alias resolution");
        }
    }

    private Carrier encodeCarrier(ExecutionPlan plan, int chunkX, int chunkZ) {
        ArrayList<BinaryNbt> pieces = new ArrayList<>();
        for (PiecePlacement piece : plan.pieces()) {
            pieces.add(new BinaryNbt(writeRoot(out -> writePiecePayload(out, piece))));
        }
        BinaryNbt start = new BinaryNbt(writeRoot(out -> {
            intTag(out, "references", 0);
            intTag(out, "ChunkZ", chunkZ);
            stringTag(out, "id", STRUCTURE_KEY);
            out.writeByte(9); out.writeUTF("Children"); out.writeByte(10);
            out.writeInt(plan.pieces().size());
            for (PiecePlacement piece : plan.pieces()) {
                writePiecePayload(out, piece);
                out.writeByte(0);
            }
            intTag(out, "ChunkX", chunkX);
        }));
        return new Carrier(CARRIER_FORMAT, pieces, start);
    }

    private void writePiecePayload(DataOutputStream out, PiecePlacement piece) throws IOException {
        Bounds box = piece.bounds();
        out.writeByte(11); out.writeUTF("BB"); out.writeInt(6);
        out.writeInt(box.minX()); out.writeInt(box.minY()); out.writeInt(box.minZ());
        out.writeInt(box.maxX()); out.writeInt(box.maxY()); out.writeInt(box.maxZ());
        intTag(out, "PosZ", piece.originZ());
        intTag(out, "PosX", piece.originX());
        out.writeByte(10); out.writeUTF("pool_element");
        if (piece.type() == ElementType.LEGACY_SINGLE) {
            writeSinglePoolElement(out, piece.elementKey(),
                    piece.processor().isEmpty() ? INLINE : piece.processor(), piece.projection().name());
        } else if (piece.type() == ElementType.LIST) {
            out.writeByte(9); out.writeUTF("elements"); out.writeByte(10);
            out.writeInt(towerChildren.size());
            for (CarrierChild child : towerChildren) {
                writeSinglePoolElement(out, child.template(), child.processor(), piece.projection().name());
                out.writeByte(0);
            }
            stringTag(out, "projection", projectionNbt(piece.projection().name()));
            stringTag(out, "element_type", LIST_TYPE);
        } else {
            throw new IllegalArgumentException("unsupported Pillager Outpost carrier piece: "
                    + piece.type());
        }
        out.writeByte(0);
        intTag(out, "PosY", piece.originY());
        stringTag(out, "rotation", piece.rotation().name());
        stringTag(out, "id", "minecraft:jigsaw");
        intTag(out, "GD", 0);
        intTag(out, "O", -1);
        intTag(out, "ground_level_delta", piece.groundLevelDelta());
        out.writeByte(9); out.writeUTF("junctions"); out.writeByte(10);
        out.writeInt(piece.junctions().size());
        for (Mc263JigsawStructureExecutor.Junction junction : piece.junctions()) {
            intTag(out, "source_z", junction.sourceZ());
            intTag(out, "source_x", junction.sourceX());
            intTag(out, "delta_y", junction.deltaY());
            intTag(out, "source_ground_y", junction.sourceGroundY());
            stringTag(out, "dest_proj", projectionNbt(junction.destinationProjection().name()));
            out.writeByte(0);
        }
    }

    private static void writeSinglePoolElement(DataOutputStream out, String template,
            String processor, String projection) throws IOException {
        stringTag(out, "location", template);
        if (processor.equals(INLINE)) {
            out.writeByte(10); out.writeUTF("processors");
            out.writeByte(9); out.writeUTF("processors"); out.writeByte(0); out.writeInt(0);
            out.writeByte(0);
        } else if (processor.equals(OUTPOST_ROT)) {
            stringTag(out, "processors", processor);
        } else {
            throw new IllegalArgumentException("unknown Pillager Outpost carrier processor: "
                    + processor);
        }
        stringTag(out, "projection", projectionNbt(projection));
        stringTag(out, "element_type", LEGACY_SINGLE_TYPE);
    }

    private static byte[] writeRoot(IoWriter payload) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(10); out.writeUTF("");
                payload.write(out);
                out.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("failed to encode Pillager Outpost carrier", impossible);
        }
    }

    private static void intTag(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(3); out.writeUTF(name); out.writeInt(value);
    }

    private static void stringTag(DataOutputStream out, String name, String value)
            throws IOException {
        out.writeByte(8); out.writeUTF(name); out.writeUTF(value);
    }

    private static String projectionNbt(String value) {
        return switch (value) {
            case "RIGID" -> "rigid";
            case "TERRAIN_MATCHING" -> "terrain_matching";
            default -> throw new IllegalArgumentException(
                    "unknown Pillager Outpost carrier projection: " + value);
        };
    }

    private static Mc263JigsawStructureBoundary.Projection projection(
            Mc263PillagerOutpostGrammar.Projection projection) {
        return Mc263JigsawStructureBoundary.Projection.valueOf(projection.name());
    }

    private static String processor(String processor) {
        if (processor.equals(INLINE)) return "";
        if (processor.equals(OUTPOST_ROT)) return OUTPOST_ROT;
        throw new IllegalArgumentException("unknown Pillager Outpost processor: " + processor);
    }

    private static Bounds bounds(Mc263PillagerOutpostGrammar.Template template) {
        Mc263PillagerOutpostGrammar.Vec3i size = template.size();
        require(size.x() > 0 && size.y() > 0 && size.z() > 0,
                "invalid Pillager Outpost template size: " + template.key());
        return new Bounds(0, 0, 0, size.x() - 1, size.y() - 1, size.z() - 1);
    }

    private static Bounds union(Bounds left, Bounds right) {
        return new Bounds(Math.min(left.minX(), right.minX()), Math.min(left.minY(), right.minY()),
                Math.min(left.minZ(), right.minZ()), Math.max(left.maxX(), right.maxX()),
                Math.max(left.maxY(), right.maxY()), Math.max(left.maxZ(), right.maxZ()));
    }

    private static boolean sameConnectors(List<ConnectorSpec> left, List<ConnectorSpec> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            ConnectorSpec a = left.get(index), b = right.get(index);
            if (a.x() != b.x() || a.y() != b.y() || a.z() != b.z()
                    || a.front() != b.front() || a.top() != b.top() || a.joint() != b.joint()
                    || !a.name().equals(b.name()) || !a.target().equals(b.target())
                    || !a.pool().equals(b.pool())
                    || a.placementPriority() != b.placementPriority()
                    || a.selectionPriority() != b.selectionPriority()) return false;
        }
        return true;
    }

    private static List<ConnectorSpec> connectors(Mc263PillagerOutpostGrammar.Template template) {
        ArrayList<ConnectorSpec> result = new ArrayList<>();
        for (Mc263PillagerOutpostGrammar.Connector connector : template.connectors()) {
            require(connector.ordinal() == result.size(),
                    "Pillager Outpost connector ordinal drift: " + template.key());
            result.add(new ConnectorSpec(connector.position().x(), connector.position().y(),
                    connector.position().z(), Direction.valueOf(connector.front().name()),
                    Direction.valueOf(connector.top().name()), Joint.valueOf(connector.joint().name()),
                    connector.name(), connector.target(), connector.pool(),
                    connector.placementPriority(), connector.selectionPriority()));
        }
        return List.copyOf(result);
    }

    private static Box aggregate(List<PiecePlacement> pieces) {
        Bounds first = pieces.get(0).bounds();
        Box result = new Box(first.minX(), first.minY(), first.minZ(),
                first.maxX(), first.maxY(), first.maxZ());
        for (int index = 1; index < pieces.size(); index++) {
            Bounds next = pieces.get(index).bounds();
            result = result.union(new Box(next.minX(), next.minY(), next.minZ(),
                    next.maxX(), next.maxY(), next.maxZ()));
        }
        return result;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public interface Publisher {
        boolean supports(String structureKey, String carrierFormat);
        void publishAtomically(Start start);
    }

    public interface WorldAccess {
        boolean supportsHeightmap(String heightmap);
        boolean supportsBuildHeightBoundary();
        int minBuildY();
        int maxBuildY();
        int baseHeight(String heightmap, int blockX, int blockZ);
    }

    public record ProjectionQuery(int ordinal, String heightmap, int x, int z, int result) {
        public ProjectionQuery { Objects.requireNonNull(heightmap); }
    }

    public record Vec3i(int x, int y, int z) { }

    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Box {
            require(minX <= maxX && minY <= maxY && minZ <= maxZ,
                    "inverted Pillager Outpost box");
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
    }

    public static final class BinaryNbt {
        private final byte[] bytes;
        private final String sha256;
        BinaryNbt(byte[] bytes) {
            this.bytes = bytes.clone();
            this.sha256 = Mc263PillagerOutpostProducer.sha256(this.bytes);
        }
        public int length() { return bytes.length; }
        public String sha256() { return sha256; }
        public byte[] bytes() { return bytes.clone(); }
    }

    public record Carrier(String format, List<BinaryNbt> pieces, BinaryNbt structureStart) {
        public Carrier {
            Objects.requireNonNull(format); Objects.requireNonNull(structureStart);
            pieces = List.copyOf(pieces);
        }
    }

    public record Start(long worldSeed, int chunkX, int chunkZ, Box aggregateBoundingBox,
            Vec3i stubPosition, ExecutionPlan executionPlan, List<AcceptedEdge> acceptedEdges,
            List<ProjectionQuery> projectionQueries, PlannerRngReceipt generationRng,
            Carrier carrier) {
        public Start {
            Objects.requireNonNull(aggregateBoundingBox); Objects.requireNonNull(stubPosition);
            Objects.requireNonNull(executionPlan); acceptedEdges = List.copyOf(acceptedEdges);
            projectionQueries = List.copyOf(projectionQueries);
            Objects.requireNonNull(generationRng); Objects.requireNonNull(carrier);
        }
    }

    private record CarrierChild(String template, String processor) {
        private CarrierChild { Objects.requireNonNull(template); Objects.requireNonNull(processor); }
    }

    private interface IoWriter {
        void write(DataOutputStream out) throws IOException;
    }

    private static final class QueryingHeightResolver implements HeightResolver {
        private final WorldAccess world;
        private final int minBuildY;
        private final int maxBuildY;
        private final Map<Long, Integer> cache = new HashMap<>();
        private final ArrayList<ProjectionQuery> queries = new ArrayList<>();

        private QueryingHeightResolver(WorldAccess world, int minBuildY, int maxBuildY) {
            this.world = world; this.minBuildY = minBuildY; this.maxBuildY = maxBuildY;
        }

        @Override
        public int firstFreeY(int x, int z) {
            long key = ((long) x << 32) ^ (z & 0xffff_ffffL);
            Integer cached = cache.get(key);
            if (cached != null) return cached;
            int result = world.baseHeight(HEIGHTMAP, x, z);
            cache.put(key, result);
            queries.add(new ProjectionQuery(queries.size(), HEIGHTMAP, x, z, result));
            return result;
        }

        @Override public int minBuildY() { return minBuildY; }
        @Override public int maxBuildY() { return maxBuildY; }

        List<ProjectionQuery> receipt() { return List.copyOf(queries); }
    }
}
