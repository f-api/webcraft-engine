package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.LegacyRand;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Bounds;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ConnectorSpec;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Direction;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ElementSpec;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ElementType;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Grammar;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Joint;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.PoolSpec;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Projection;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureCatalog.AliasMode;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureCatalog.StructureSpec;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.AcceptedEdge;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.ConnectorIdentity;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.ExecutionLimits;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.ExecutionPlan;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.HeightResolver;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.PiecePlacement;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.PlannerRngReceipt;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.RootStage;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.StagedRequest;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.TracedPlan;
import com.gameexpert.terrain.mc.structure.Mc263VillageProducerGrammarAccess.ProducerConnector;
import com.gameexpert.terrain.mc.structure.Mc263VillageProducerGrammarAccess.ProducerElement;
import com.gameexpert.terrain.mc.structure.Mc263VillageProducerGrammarAccess.ProducerElementKind;
import com.gameexpert.terrain.mc.structure.Mc263VillageProducerGrammarAccess.ProducerPool;
import com.gameexpert.terrain.mc.structure.Mc263VillageProducerGrammarAccess.ProducerSpec;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant five-family Village start-attempt producer for pinned Minecraft 26.3-snapshot-7.
 *
 * <p>Each call consumes one caller-owned Legacy48 stream. Typed capability closure and generic
 * Jigsaw preflight complete before terrain, biome, RNG or publication effects. Root selection and
 * start projection run first; the caller's actual-biome admission is invoked at that exact staged
 * boundary. Rejection consumes the one-shot continuation without child expansion, while acceptance
 * resumes the same runtime and same random state. The bounded accepted probes are never retained or
 * consulted by production generation.</p>
 */
public final class Mc263VillageProducer {
    public static final String CARRIER_FORMAT = "minecraft-26.3-village-start-nbt-v1";
    public static final String HEIGHTMAP = "WORLD_SURFACE_WG";

    private static final String INLINE = "inline";
    private static final String LEGACY_SINGLE_TYPE = "minecraft:legacy_single_pool_element";
    private static final String FEATURE_TYPE = "minecraft:feature_pool_element";
    private static final int MIN_BUILD_Y = -64;
    private static final int MAX_BUILD_Y = 320;
    private static final int TERRAIN_PADDING = 12;
    private static final ExecutionLimits LIMITS = new ExecutionLimits(512, 4_096, 2_000_000L);

    private final List<FamilyRuntime> families;

    private Mc263VillageProducer() {
        ArrayList<FamilyRuntime> result = new ArrayList<>();
        for (ProducerSpec spec : Mc263VillageProducerGrammarAccess.specs()) {
            Grammar grammar = adaptGrammar(spec);
            validateStaticClosure(spec, grammar);
            result.add(new FamilyRuntime(spec, grammar));
        }
        require(result.size() == 5, "Village family cardinality drift");
        this.families = List.copyOf(result);
    }

    /** Lazily constructs the dormant producer without registering any Village key. */
    public static Mc263VillageProducer pinned() { return Holder.VALUE; }

    private static final class Holder {
        private static final Mc263VillageProducer VALUE = new Mc263VillageProducer();
    }

    /**
     * Executes one already-selected lawful Village family attempt.
     *
     * <p>The caller owns {@code callerRandom}; its incoming state is irrelevant because vanilla
     * setLargeFeatureSeed resets that same object at root initialization. No bounded probe lookup,
     * coordinate table or replay is used.</p>
     */
    public Attempt attempt(String structureKey, long worldSeed, int chunkX, int chunkZ,
            LegacyRand callerRandom, WorldAccess world, BiomeAdmission biomes,
            Publisher publisher) {
        FamilyRuntime family = requireFamily(structureKey);
        if (callerRandom == null || world == null || biomes == null || publisher == null) {
            throw new IllegalArgumentException("complete Village attempt boundary is required");
        }
        Math.multiplyExact(chunkX, 16);
        Math.multiplyExact(chunkZ, 16);
        preflightCapabilities(family.spec(), world, biomes, publisher);
        QueryingHeightResolver heights = new QueryingHeightResolver(world);
        StagedRequest request = Mc263JigsawStructureExecutor.preflight(worldSeed, chunkX, chunkZ,
                family.grammar(), heights, LIMITS);
        int minBuildY = world.minBuildY();
        int maxBuildY = world.maxBuildY();
        require(minBuildY == MIN_BUILD_Y && maxBuildY == MAX_BUILD_Y,
                "unsupported Village build-height boundary: " + minBuildY + ".." + maxBuildY);
        heights.initializeBuildBounds(minBuildY, maxBuildY);
        RootStage stage = Mc263JigsawStructureExecutor.initializeRoot(request, callerRandom);
        TracedPlan root = stage.root();
        validateRoot(family.spec(), root.executionPlan(), root.acceptedEdges(), chunkX, chunkZ);
        BiomeSample biome = Objects.requireNonNull(biomes.actualBiome(structureKey,
                family.spec().biomeTag(), root.executionPlan().centerX(),
                root.executionPlan().centerY(), root.executionPlan().centerZ()),
                "Village biome admission result");
        require(biome.biomeKey() != null && !biome.biomeKey().isBlank(),
                "Village actual biome identity is absent");

        if (!biome.accepted()) {
            TracedPlan rejected = stage.continuation().reject(request, callerRandom);
            require(rejected.equals(root), "Village rejected root-stage receipt drift");
            RootReceipt receipt = new RootReceipt(structureKey, worldSeed, chunkX, chunkZ,
                    rejected.executionPlan(), List.of(), heights.receipt(),
                    rejected.generationRng(), biome);
            return new Rejected(receipt);
        }

        TracedPlan traced = stage.continuation().resume(request, callerRandom);
        ExecutionPlan plan = traced.executionPlan();
        validateGeneratedPlan(family.spec(), plan, traced.acceptedEdges(), chunkX, chunkZ);
        List<Edge> edges = normalizeEdges(plan, traced.acceptedEdges());
        Box aggregate = aggregate(plan.pieces()).inflate(TERRAIN_PADDING);
        Carrier carrier = encodeCarrier(plan, chunkX, chunkZ);
        Start start = new Start(structureKey, worldSeed, chunkX, chunkZ, aggregate,
                new Vec3i(plan.centerX(), plan.centerY(), plan.centerZ()), biome, plan, edges,
                heights.receipt(), traced.generationRng(), carrier);
        publisher.publishAtomically(start);
        return new Accepted(start);
    }

    private FamilyRuntime requireFamily(String structureKey) {
        if (structureKey == null) throw new IllegalArgumentException("Village structure key is required");
        for (FamilyRuntime family : families) {
            if (family.spec().structureKey().equals(structureKey)) return family;
        }
        throw new IllegalArgumentException("unknown Village family: " + structureKey);
    }

    private static void preflightCapabilities(ProducerSpec spec, WorldAccess world,
            BiomeAdmission biomes, Publisher publisher) {
        require(world.supportsHeightmap(HEIGHTMAP), "Village heightmap capability is absent");
        require(world.supportsBuildHeightBoundary(), "Village build-height capability is absent");
        require(biomes.supports(spec.structureKey(), spec.biomeTag()),
                "Village biome-admission capability is absent");
        require(publisher.supports(spec.structureKey(), CARRIER_FORMAT),
                "Village publication capability is absent");
        for (String pool : spec.poolCapabilities()) {
            require(world.supportsPool(pool), "Village pool capability is absent: " + pool);
        }
        for (String template : spec.templateCapabilities()) {
            require(world.supportsTemplate(template),
                    "Village template capability is absent: " + template);
        }
        for (String processor : spec.processorCapabilities()) {
            require(world.supportsProcessorList(processor),
                    "Village processor capability is absent: " + processor);
        }
        for (String feature : spec.featureCapabilities()) {
            require(world.supportsConfiguredFeature(feature),
                    "Village feature capability is absent: " + feature);
        }
    }

    private static Grammar adaptGrammar(ProducerSpec spec) {
        ArrayList<PoolSpec> pools = new ArrayList<>();
        for (ProducerPool pool : spec.pools()) {
            ArrayList<ElementSpec> elements = new ArrayList<>();
            for (ProducerElement element : pool.elements()) {
                require(element.ordinal() == elements.size(),
                        "Village pool element ordinal drift: " + pool.key());
                Projection projection = Projection.valueOf(element.projection());
                if (element.kind() == ProducerElementKind.TEMPLATE) {
                    require(element.sizeX() > 0 && element.sizeY() > 0 && element.sizeZ() > 0,
                            "invalid Village template bounds: " + element.key());
                    ArrayList<ConnectorSpec> connectors = new ArrayList<>();
                    for (ProducerConnector connector : element.connectors()) {
                        require(connector.ordinal() == connectors.size(),
                                "Village connector ordinal drift: " + element.key());
                        connectors.add(new ConnectorSpec(connector.x(), connector.y(), connector.z(),
                                Direction.valueOf(connector.front()),
                                Direction.valueOf(connector.top()), Joint.valueOf(connector.joint()),
                                connector.name(), connector.target(), connector.pool(),
                                connector.placementPriority(), connector.selectionPriority()));
                    }
                    elements.add(new ElementSpec(ElementType.LEGACY_SINGLE, element.key(),
                            element.weight(), projection, processor(element.processor()),
                            new Bounds(0, 0, 0, element.sizeX() - 1, element.sizeY() - 1,
                                    element.sizeZ() - 1), connectors, List.of()));
                } else if (element.kind() == ProducerElementKind.FEATURE) {
                    require(element.sizeX() == 1 && element.sizeY() == 1 && element.sizeZ() == 1
                                    && element.processor().isEmpty()
                                    && element.connectors().isEmpty(),
                            "unsupported Village feature geometry");
                    elements.add(new ElementSpec(ElementType.FEATURE, element.key(),
                            element.weight(), projection, "", new Bounds(0, 0, 0, 0, 0, 0),
                            List.of(), List.of()));
                } else if (element.kind() == ProducerElementKind.EMPTY) {
                    require(element.key().isEmpty() && element.processor().isEmpty()
                                    && element.connectors().isEmpty(),
                            "Village empty pool element carries data");
                    elements.add(new ElementSpec(ElementType.EMPTY, "", element.weight(),
                            projection, "", null, List.of(), List.of()));
                } else {
                    throw new IllegalArgumentException("unknown Village producer element kind");
                }
            }
            pools.add(new PoolSpec(pool.key(), pool.fallback(), elements));
        }
        Grammar grammar = new Grammar(spec.structureKey(), pools);
        Mc263JigsawStructureBoundary.validate(grammar);
        return grammar;
    }

    private static String processor(String value) {
        if (INLINE.equals(value)) return "";
        if (value != null && value.matches("minecraft:[a-z0-9_./-]+")) return value;
        throw new IllegalArgumentException("unknown Village processor: " + value);
    }

    private static void validateStaticClosure(ProducerSpec evidence, Grammar grammar) {
        StructureSpec structure = Mc263JigsawStructureCatalog.require(evidence.structureKey());
        require(structure.startPool().equals(evidence.startPool()), "Village start-pool drift");
        require(structure.depth() == evidence.size(), "Village size/depth drift");
        require(structure.minStartHeight() == evidence.startHeightAbsolute()
                        && structure.maxStartHeight() == evidence.startHeightAbsolute(),
                "Village start-height drift");
        require(structure.expansionHack() == evidence.useExpansionHack(),
                "Village expansion-hack drift");
        require(structure.projectStartToHeightmap().equals(evidence.projectStartToHeightmap())
                        && HEIGHTMAP.equals(evidence.projectStartToHeightmap()),
                "Village start projection drift");
        require(structure.maxHorizontalDistance() == evidence.maxDistanceFromCenter()
                        && structure.maxVerticalDistance() == evidence.maxDistanceFromCenter(),
                "Village max-distance drift");
        require(structure.terrainAdaptation().name().equals(evidence.terrainAdaptation()
                        .toUpperCase(java.util.Locale.ROOT)), "Village terrain-adaptation drift");
        require(structure.aliasMode() == AliasMode.NONE, "Village unexpectedly acquired aliases");
        require(structure.startJigsawName().isEmpty(), "Village unexpectedly acquired start jigsaw");
        require(grammar.structureKey().equals(evidence.structureKey()), "Village grammar-key drift");
        require(grammar.pools().size() == evidence.poolCapabilities().size(),
                "Village family pool closure drift");
    }

    private static void validateRoot(ProducerSpec family, ExecutionPlan plan,
            List<AcceptedEdge> edges, int chunkX, int chunkZ) {
        require(plan.present(), "Village root stage unexpectedly absent");
        require(plan.structureKey().equals(family.structureKey()), "Village root structure drift");
        require(plan.chunkX() == chunkX && plan.chunkZ() == chunkZ, "Village root chunk drift");
        require(plan.pieces().size() == 1 && edges.isEmpty(),
                "Village staged boundary expanded children before biome admission");
        PiecePlacement root = plan.pieces().getFirst();
        require(root.type() == ElementType.LEGACY_SINGLE,
                "Village root is not a legacy template element");
        require(family.templateCapabilities().contains(root.elementKey()),
                "unknown Village root template: " + root.elementKey());
    }

    private static void validateGeneratedPlan(ProducerSpec family, ExecutionPlan plan,
            List<AcceptedEdge> edges, int chunkX, int chunkZ) {
        require(plan.present() && plan.structureKey().equals(family.structureKey()),
                "Village generated family drift");
        require(plan.chunkX() == chunkX && plan.chunkZ() == chunkZ,
                "Village generated chunk drift");
        require(!plan.pieces().isEmpty(), "Village generated empty piece graph");
        require(edges.size() == plan.pieces().size() - 1,
                "Village generated edge cardinality drift");
        Set<String> templates = new HashSet<>(family.templateCapabilities());
        Set<String> processors = new HashSet<>(family.processorCapabilities());
        Set<String> features = new HashSet<>(family.featureCapabilities());
        for (PiecePlacement piece : plan.pieces()) {
            require(piece.bounds() != null, "Village generated piece has no bounds");
            if (piece.type() == ElementType.LEGACY_SINGLE) {
                require(templates.contains(piece.elementKey()),
                        "unknown Village generated template: " + piece.elementKey());
                require(piece.processor().isEmpty() || processors.contains(piece.processor()),
                        "unknown Village generated processor: " + piece.processor());
            } else if (piece.type() == ElementType.FEATURE) {
                require(features.contains(piece.elementKey()) && piece.processor().isEmpty(),
                        "unknown Village generated feature: " + piece.elementKey());
            } else {
                throw new IllegalArgumentException("unsupported Village generated piece: "
                        + piece.type());
            }
        }
        Set<String> pools = new HashSet<>(family.poolCapabilities());
        for (int index = 0; index < edges.size(); index++) {
            AcceptedEdge edge = edges.get(index);
            require(edge.childPieceOrdinal() == index + 1
                            && edge.parentPieceOrdinal() >= 0
                            && edge.parentPieceOrdinal() < edge.childPieceOrdinal(),
                    "Village generated parent/child order drift");
            require(pools.contains(edge.selectedSourcePool()),
                    "unknown Village selected pool: " + edge.selectedSourcePool());
            require(edge.resolvedAliasTarget().isEmpty(),
                    "Village unexpectedly resolved a pool alias");
        }
    }

    private static List<Edge> normalizeEdges(ExecutionPlan plan, List<AcceptedEdge> edges) {
        ArrayList<Edge> result = new ArrayList<>();
        for (AcceptedEdge edge : edges) {
            ConnectorIdentity source = edge.sourceConnector();
            ConnectorIdentity target = edge.targetConnector();
            PiecePlacement child = plan.pieces().get(edge.childPieceOrdinal());
            boolean feature = child.type() == ElementType.FEATURE;
            result.add(new Edge(edge.parentPieceOrdinal(), source.ordinal(),
                    edge.childPieceOrdinal(), target.ordinal(), edge.selectedSourcePool(),
                    source.selectionPriority(), source.placementPriority(),
                    new Vec3i(source.x(), source.y(), source.z()),
                    new Vec3i(Math.addExact(child.originX(), target.x()),
                            Math.addExact(child.originY(), target.y()),
                            Math.addExact(child.originZ(), target.z())),
                    source.name(), source.target(), feature ? "<null>" : target.name(),
                    target.target(), true));
        }
        return List.copyOf(result);
    }

    private static Carrier encodeCarrier(ExecutionPlan plan, int chunkX, int chunkZ) {
        ArrayList<BinaryNbt> pieces = new ArrayList<>();
        for (PiecePlacement piece : plan.pieces()) {
            pieces.add(new BinaryNbt(writeRoot(out -> writePiecePayload(out, piece))));
        }
        BinaryNbt start = new BinaryNbt(writeRoot(out -> writeStartPayload(out, plan, chunkX,
                chunkZ, 0)));
        BinaryNbt successor = new BinaryNbt(writeRoot(out -> writeStartPayload(out, plan, chunkX,
                chunkZ, 1)));
        return new Carrier(CARRIER_FORMAT, pieces, start, successor);
    }

    private static void writeStartPayload(DataOutputStream out, ExecutionPlan plan,
            int chunkX, int chunkZ, int references) throws IOException {
        intTag(out, "references", references);
        intTag(out, "ChunkZ", chunkZ);
        stringTag(out, "id", plan.structureKey());
        out.writeByte(9); out.writeUTF("Children"); out.writeByte(10);
        out.writeInt(plan.pieces().size());
        for (PiecePlacement piece : plan.pieces()) {
            writePiecePayload(out, piece);
            out.writeByte(0);
        }
        intTag(out, "ChunkX", chunkX);
    }

    private static void writePiecePayload(DataOutputStream out, PiecePlacement piece)
            throws IOException {
        Bounds box = piece.bounds();
        out.writeByte(11); out.writeUTF("BB"); out.writeInt(6);
        out.writeInt(box.minX()); out.writeInt(box.minY()); out.writeInt(box.minZ());
        out.writeInt(box.maxX()); out.writeInt(box.maxY()); out.writeInt(box.maxZ());
        intTag(out, "PosZ", piece.originZ());
        intTag(out, "PosX", piece.originX());
        out.writeByte(10); out.writeUTF("pool_element");
        if (piece.type() == ElementType.LEGACY_SINGLE) {
            stringTag(out, "location", piece.elementKey());
            if (piece.processor().isEmpty()) {
                out.writeByte(10); out.writeUTF("processors");
                out.writeByte(9); out.writeUTF("processors"); out.writeByte(0); out.writeInt(0);
                out.writeByte(0);
            } else {
                stringTag(out, "processors", piece.processor());
            }
            stringTag(out, "projection", projectionNbt(piece.projection()));
            stringTag(out, "element_type", LEGACY_SINGLE_TYPE);
        } else if (piece.type() == ElementType.FEATURE) {
            stringTag(out, "feature", piece.elementKey());
            stringTag(out, "projection", projectionNbt(piece.projection()));
            stringTag(out, "element_type", FEATURE_TYPE);
        } else {
            throw new IllegalArgumentException("unsupported Village carrier piece: " + piece.type());
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
            stringTag(out, "dest_proj", projectionNbt(junction.destinationProjection()));
            out.writeByte(0);
        }
    }

    private static String projectionNbt(Projection projection) {
        return projection == Projection.RIGID ? "rigid" : "terrain_matching";
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
            throw new IllegalStateException("failed to encode Village carrier", impossible);
        }
    }

    private static void intTag(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(3); out.writeUTF(name); out.writeInt(value);
    }

    private static void stringTag(DataOutputStream out, String name, String value)
            throws IOException {
        out.writeByte(8); out.writeUTF(name); out.writeUTF(value);
    }

    private static Box aggregate(List<PiecePlacement> pieces) {
        Bounds first = pieces.getFirst().bounds();
        Box result = new Box(first.minX(), first.minY(), first.minZ(), first.maxX(), first.maxY(),
                first.maxZ());
        for (int index = 1; index < pieces.size(); index++) {
            Bounds next = pieces.get(index).bounds();
            result = result.union(new Box(next.minX(), next.minY(), next.minZ(), next.maxX(),
                    next.maxY(), next.maxZ()));
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

    public interface WorldAccess {
        boolean supportsHeightmap(String heightmap);
        boolean supportsBuildHeightBoundary();
        boolean supportsPool(String poolKey);
        boolean supportsTemplate(String templateKey);
        boolean supportsProcessorList(String processorKey);
        boolean supportsConfiguredFeature(String featureKey);
        int minBuildY();
        int maxBuildY();
        int baseHeight(String heightmap, int blockX, int blockZ);
    }

    public interface BiomeAdmission {
        boolean supports(String structureKey, String biomeTag);
        BiomeSample actualBiome(String structureKey, String biomeTag,
                int blockX, int blockY, int blockZ);
    }

    public interface Publisher {
        boolean supports(String structureKey, String carrierFormat);
        void publishAtomically(Start start);
    }

    public sealed interface Attempt permits Accepted, Rejected {
        String structureKey();
        long worldSeed();
        int chunkX();
        int chunkZ();
        boolean accepted();
    }

    public record Accepted(Start start) implements Attempt {
        public Accepted { Objects.requireNonNull(start); }
        @Override public String structureKey() { return start.structureKey(); }
        @Override public long worldSeed() { return start.worldSeed(); }
        @Override public int chunkX() { return start.chunkX(); }
        @Override public int chunkZ() { return start.chunkZ(); }
        @Override public boolean accepted() { return true; }
    }

    public record Rejected(RootReceipt root) implements Attempt {
        public Rejected { Objects.requireNonNull(root); }
        @Override public String structureKey() { return root.structureKey(); }
        @Override public long worldSeed() { return root.worldSeed(); }
        @Override public int chunkX() { return root.chunkX(); }
        @Override public int chunkZ() { return root.chunkZ(); }
        @Override public boolean accepted() { return false; }
    }

    public record BiomeSample(String biomeKey, boolean accepted) {
        public BiomeSample { Objects.requireNonNull(biomeKey); }
    }

    public record ProjectionQuery(int ordinal, String heightmap, int x, int z, int result) {
        public ProjectionQuery { Objects.requireNonNull(heightmap); }
    }

    public record Vec3i(int x, int y, int z) { }

    public record Edge(int sourcePiece, int sourceConnectorOrdinal, int targetPiece,
            int targetConnectorOrdinal, String selectedPool, int selectionPriority,
            int placementPriority, Vec3i sourcePosition, Vec3i targetPosition, String sourceName,
            String sourceTarget, String targetName, String targetTarget, boolean officialCanAttach) {
        public Edge {
            require(sourcePiece >= 0 && targetPiece > sourcePiece, "invalid Village edge order");
            Objects.requireNonNull(selectedPool); Objects.requireNonNull(sourcePosition);
            Objects.requireNonNull(targetPosition); Objects.requireNonNull(sourceName);
            Objects.requireNonNull(sourceTarget); Objects.requireNonNull(targetName);
            Objects.requireNonNull(targetTarget);
        }
    }

    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Box {
            require(minX <= maxX && minY <= maxY && minZ <= maxZ, "inverted Village box");
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
        private BinaryNbt(byte[] bytes) {
            this.bytes = bytes.clone();
            this.sha256 = Mc263VillageProducer.sha256(this.bytes);
        }
        public int length() { return bytes.length; }
        public String sha256() { return sha256; }
        public byte[] bytes() { return bytes.clone(); }
    }

    public record Carrier(String format, List<BinaryNbt> pieces, BinaryNbt structureStart,
            BinaryNbt mutableSuccessorAfterOneReference) {
        public Carrier {
            require(CARRIER_FORMAT.equals(format), "unknown Village carrier format");
            pieces = List.copyOf(pieces); Objects.requireNonNull(structureStart);
            Objects.requireNonNull(mutableSuccessorAfterOneReference);
        }
    }

    public record RootReceipt(String structureKey, long worldSeed, int chunkX, int chunkZ,
            ExecutionPlan executionPlan, List<Edge> acceptedEdges,
            List<ProjectionQuery> projectionQueries, PlannerRngReceipt generationRng,
            BiomeSample biome) {
        public RootReceipt {
            Objects.requireNonNull(structureKey); Objects.requireNonNull(executionPlan);
            acceptedEdges = List.copyOf(acceptedEdges); projectionQueries = List.copyOf(projectionQueries);
            Objects.requireNonNull(generationRng); Objects.requireNonNull(biome);
            require(executionPlan.pieces().size() == 1 && acceptedEdges.isEmpty(),
                    "Village rejected receipt is not root-only");
        }
    }

    public record Start(String structureKey, long worldSeed, int chunkX, int chunkZ,
            Box aggregateBoundingBox, Vec3i stubPosition, BiomeSample biome,
            ExecutionPlan executionPlan, List<Edge> acceptedEdges,
            List<ProjectionQuery> projectionQueries, PlannerRngReceipt generationRng,
            Carrier carrier) {
        public Start {
            Objects.requireNonNull(structureKey); Objects.requireNonNull(aggregateBoundingBox);
            Objects.requireNonNull(stubPosition); Objects.requireNonNull(biome);
            Objects.requireNonNull(executionPlan); acceptedEdges = List.copyOf(acceptedEdges);
            projectionQueries = List.copyOf(projectionQueries);
            Objects.requireNonNull(generationRng); Objects.requireNonNull(carrier);
        }
    }

    private record FamilyRuntime(ProducerSpec spec, Grammar grammar) {
        private FamilyRuntime { Objects.requireNonNull(spec); Objects.requireNonNull(grammar); }
    }

    private interface IoWriter { void write(DataOutputStream out) throws IOException; }

    /**
     * The generic executor asks twice in immediate succession for the same non-rigid attachment
     * height (placement then junction); vanilla computes that height once and reuses it. Suppress
     * only that immediate duplicate. Later official calls to the same coordinate remain observable.
     */
    private static final class QueryingHeightResolver implements HeightResolver {
        private final WorldAccess world;
        private int minBuildY;
        private int maxBuildY;
        private boolean boundsInitialized;
        private final ArrayList<ProjectionQuery> queries = new ArrayList<>();
        private boolean hasLast;
        private int lastX, lastZ, lastValue;

        private QueryingHeightResolver(WorldAccess world) { this.world = world; }

        private void initializeBuildBounds(int minBuildY, int maxBuildY) {
            require(!boundsInitialized, "Village build-height boundary initialized twice");
            this.minBuildY = minBuildY; this.maxBuildY = maxBuildY;
            this.boundsInitialized = true;
        }

        @Override public int firstFreeY(int x, int z) {
            if (hasLast && x == lastX && z == lastZ) return lastValue;
            int result = world.baseHeight(HEIGHTMAP, x, z);
            queries.add(new ProjectionQuery(queries.size(), HEIGHTMAP, x, z, result));
            hasLast = true; lastX = x; lastZ = z; lastValue = result;
            return result;
        }
        @Override public int minBuildY() {
            require(boundsInitialized, "Village build-height boundary used before preflight");
            return minBuildY;
        }
        @Override public int maxBuildY() {
            require(boundsInitialized, "Village build-height boundary used before preflight");
            return maxBuildY;
        }
        List<ProjectionQuery> receipt() { return List.copyOf(queries); }
    }
}
