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
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.AcceptedEdge;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.ConnectorIdentity;
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
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Dormant exact Java Ancient City start producer for the pinned Minecraft 26.3-snapshot-7 grammar.
 *
 * <p>The producer never consults the bounded start-graph probes. They remain test witnesses only.
 * Live starts are derived from the authenticated typed execution grammar and one generic same-pass
 * Jigsaw trace. Nothing registers or publishes this producer unless a caller opts in explicitly.</p>
 */
public final class Mc263AncientCityProducer {
    public static final String STRUCTURE_KEY = "minecraft:ancient_city";
    public static final String STRUCTURE_SET_KEY = "minecraft:ancient_cities";
    public static final String CARRIER_FORMAT = "ANC263J1";
    public static final int SPACING = 24;
    public static final int SEPARATION = 8;
    public static final int SALT = 20_083_232;
    public static final int START_Y = -27;
    public static final int TERRAIN_PADDING = 12;

    private static final long LARGE_FEATURE_X = 341_873_128_712L;
    private static final long LARGE_FEATURE_Z = 132_897_987_541L;
    private static final int ROOT_ANCHOR_Y = 24;
    private static final int ROOT_NORMALIZATION_Y = -ROOT_ANCHOR_Y;
    private static final String START_POOL = "minecraft:ancient_city/city_center";
    private static final String START_JIGSAW = "minecraft:city_anchor";
    private static final String SCULK_FEATURE = "minecraft:sculk_patch_ancient_city";
    private static final int SUCCESSOR_CHANGED_BYTE_OFFSET = 19;
    private static final String EXPECTED_ABSENT =
            "minecraft:ancient_city/walls/intact_horizontal_wall_stairs_5";
    private static final String INLINE = "inline";
    private static final String SINGLE_TYPE = "minecraft:single_pool_element";
    private static final String LIST_TYPE = "minecraft:list_pool_element";
    private static final String FEATURE_TYPE = "minecraft:feature_pool_element";
    private static final ExecutionLimits LIMITS = new ExecutionLimits(512, 4_096, 2_000_000L);

    private final Mc263AncientCityGrammar evidence;
    private final Grammar grammar;
    private final List<TemplateCapability> templateCapabilities;
    private final List<String> processorCapabilities;
    private final Map<List<String>, List<ListChild>> listChildren;

    private Mc263AncientCityProducer(Mc263AncientCityGrammar evidence) {
        this.evidence = Objects.requireNonNull(evidence, "Ancient City evidence");
        this.grammar = adaptGrammar(evidence);
        this.templateCapabilities = templateCapabilities(evidence);
        this.processorCapabilities = processorCapabilities(evidence);
        this.listChildren = listChildren(evidence);
        validateStaticClosure();
    }

    /** Lazily loads the accepted typed grammar without touching any production registry. */
    public static Mc263AncientCityProducer pinned() {
        return Holder.VALUE;
    }

    private static final class Holder {
        private static final Mc263AncientCityProducer VALUE =
                new Mc263AncientCityProducer(Mc263AncientCityGrammar.loadAccepted());
    }

    /** Exact random-spread candidate for one placement region, including negative regions. */
    public static ChunkPos placementCandidate(long worldSeed, int regionX, int regionZ) {
        long seed = (long) regionX * LARGE_FEATURE_X + (long) regionZ * LARGE_FEATURE_Z
                + worldSeed + SALT;
        LegacyRand random = new LegacyRand(seed);
        int bound = SPACING - SEPARATION;
        return new ChunkPos(Math.addExact(Math.multiplyExact(regionX, SPACING), random.nextInt(bound)),
                Math.addExact(Math.multiplyExact(regionZ, SPACING), random.nextInt(bound)));
    }

    /** Exact lawful-placement predicate for the supplied owning chunk. */
    public static boolean isPlacementChunk(long worldSeed, int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, SPACING);
        int regionZ = Math.floorDiv(chunkZ, SPACING);
        ChunkPos candidate = placementCandidate(worldSeed, regionX, regionZ);
        return candidate.x() == chunkX && candidate.z() == chunkZ;
    }

    /**
     * Generates and atomically publishes one exact Ancient City start.
     *
     * <p>Capability/template/processor closure and lawful placement are checked before the traced
     * generation RNG or any biome query. The one biome membership query is necessarily performed
     * after root selection because vanilla's stub X/Z are the selected root bounding-box center.</p>
     */
    public Start generate(long worldSeed, int chunkX, int chunkZ,
            WorldAccess world, Publisher publisher) {
        Preflight preflight = preflight(world, publisher);
        require(isPlacementChunk(worldSeed, chunkX, chunkZ),
                "Ancient City chunk is not the lawful random-spread candidate");

        ShiftedHeightResolver heights = new ShiftedHeightResolver(
                preflight.minBuildY(), preflight.maxBuildY());
        TracedPlan traced = Mc263JigsawStructureExecutor.planWithTrace(
                worldSeed, chunkX, chunkZ, grammar, heights, LIMITS);
        ExecutionPlan generic = traced.executionPlan();
        require(generic.present(), "lawful Ancient City candidate produced no start");
        require(generic.structureKey().equals(STRUCTURE_KEY), "Ancient City structure-key drift");
        require(generic.chunkX() == chunkX && generic.chunkZ() == chunkZ,
                "Ancient City owning-chunk drift");
        require(heights.queryCount() == 0, "Ancient City unexpectedly queried terrain projection");

        Plan plan = normalize(generic);
        validateGeneratedPlan(plan, traced.acceptedEdges());
        BiomeSample biome = Objects.requireNonNull(
                world.biomeAt(plan.centerX(), plan.centerY(), plan.centerZ()),
                "Ancient City biome sample");
        require(biome.validForStructure(), "Ancient City stub biome is not valid for the structure");

        List<Edge> edges = normalizeEdges(traced.acceptedEdges(), plan.pieces());
        Box aggregate = aggregate(plan.pieces()).inflate(TERRAIN_PADDING);
        Vec3i stub = new Vec3i(plan.centerX(), plan.centerY(), plan.centerZ());
        Carrier carrier = encodeCarrier(plan, chunkX, chunkZ);
        Start start = new Start(worldSeed, chunkX, chunkZ, aggregate, stub, biome, plan,
                edges, traced.generationRng(), carrier);
        publisher.publishAtomically(start);
        return start;
    }

    private Preflight preflight(WorldAccess world, Publisher publisher) {
        if (world == null || publisher == null) {
            throw new IllegalArgumentException("Ancient City world/publisher are required");
        }
        require(world.supportsBuildHeightBoundary(),
                "Ancient City build-height capability is absent");
        require(world.supportsBiomeMembership(STRUCTURE_KEY),
                "Ancient City biome-membership capability is absent");
        require(publisher.supports(STRUCTURE_KEY, CARRIER_FORMAT),
                "Ancient City publication capability is absent");
        for (TemplateCapability template : templateCapabilities) {
            require(world.supportsTemplate(template.template(), template.status()),
                    "Ancient City template capability is absent: " + template.template());
        }
        for (String processor : processorCapabilities) {
            require(world.supportsProcessorList(processor),
                    "Ancient City processor capability is absent: " + processor);
        }
        require(world.supportsConfiguredFeature(SCULK_FEATURE),
                "Ancient City configured-feature capability is absent");
        int minBuildY = world.minBuildY();
        int maxBuildY = world.maxBuildY();
        require(minBuildY < maxBuildY, "Ancient City build-height boundary is inverted");
        Math.addExact(minBuildY, ROOT_ANCHOR_Y);
        Math.addExact(maxBuildY, ROOT_ANCHOR_Y);
        return new Preflight(minBuildY, maxBuildY);
    }

    private void validateStaticClosure() {
        Mc263JigsawStructureCatalog.StructureSpec structure =
                Mc263JigsawStructureCatalog.require(STRUCTURE_KEY);
        require(structure.startPool().equals(START_POOL), "Ancient City start pool drift");
        require(structure.startJigsawName().equals(START_JIGSAW), "Ancient City start jigsaw drift");
        require(structure.minStartHeight() == START_Y && structure.maxStartHeight() == START_Y,
                "Ancient City start-height drift");
        require(structure.depth() == 7 && structure.maxHorizontalDistance() == 116
                        && structure.maxVerticalDistance() == 116,
                "Ancient City Jigsaw distance/depth drift");
        require(structure.terrainAdaptation()
                        == Mc263JigsawStructureCatalog.TerrainAdaptation.BEARD_BOX,
                "Ancient City terrain adaptation drift");
        require(structure.aliasMode() == Mc263JigsawStructureCatalog.AliasMode.NONE,
                "Ancient City unexpectedly acquired aliases");
        require(evidence.configuredFeature().registryKey().equals(SCULK_FEATURE),
                "Ancient City configured-feature drift");

        PoolSpec start = grammar.pools().stream().filter(pool -> pool.key().equals(START_POOL)).findFirst().orElseThrow();
        require(start.elements().size() == 3, "Ancient City center-pool cardinality drift");
        for (ElementSpec element : start.elements()) {
            require(element.type() == ElementType.SINGLE,
                    "Ancient City center root is not a single template");
            List<ConnectorSpec> anchors = element.connectors().stream()
                    .filter(connector -> connector.name().equals(START_JIGSAW)).toList();
            require(anchors.size() == 1 && anchors.get(0).y() == ROOT_ANCHOR_Y,
                    "Ancient City root anchor Y drift");
        }
        Mc263AncientCityGrammar.Template absent = evidence.requireTemplate(EXPECTED_ABSENT);
        require(absent.status() == Mc263AncientCityGrammar.SourceStatus.EXPECTED_ABSENT
                        && absent.size().isEmpty() && absent.connectors().isEmpty()
                        && absent.commands().isEmpty(),
                "Ancient City expected-absent template semantics drift");
    }

    private static Grammar adaptGrammar(Mc263AncientCityGrammar evidence) {
        ArrayList<PoolSpec> pools = new ArrayList<>();
        for (Mc263AncientCityGrammar.Pool pool : evidence.poolsInExecutionOrder()) {
            ArrayList<ElementSpec> elements = new ArrayList<>();
            for (Mc263AncientCityGrammar.PoolElement element : pool.elementsInDeclaredOrder()) {
                if (element instanceof Mc263AncientCityGrammar.SingleElement single) {
                    elements.add(adaptSingle(evidence, single.template(), single.weight(),
                            single.processorList()));
                } else if (element instanceof Mc263AncientCityGrammar.ListElement list) {
                    elements.add(adaptList(evidence, list));
                } else if (element instanceof Mc263AncientCityGrammar.FeatureElement feature) {
                    require(feature.feature().equals(SCULK_FEATURE),
                            "unknown Ancient City feature element");
                    elements.add(new ElementSpec(ElementType.FEATURE, feature.feature(),
                            feature.weight(), Projection.RIGID, "",
                            new Bounds(0, 0, 0, 0, 0, 0), List.of(), List.of()));
                } else if (element instanceof Mc263AncientCityGrammar.EmptyElement empty) {
                    elements.add(ElementSpec.empty(empty.weight()));
                } else {
                    throw new IllegalArgumentException("unknown Ancient City pool element");
                }
            }
            pools.add(new PoolSpec(pool.key(), "minecraft:empty", elements));
        }
        Grammar grammar = new Grammar(STRUCTURE_KEY, pools);
        Mc263JigsawStructureBoundary.validate(grammar);
        return grammar;
    }

    private static ElementSpec adaptSingle(Mc263AncientCityGrammar evidence,
            String templateKey, int weight, String processor) {
        Mc263AncientCityGrammar.Template template = evidence.requireTemplate(templateKey);
        return new ElementSpec(ElementType.SINGLE, templateKey, weight, Projection.RIGID,
                genericProcessor(processor), templateBounds(template), connectors(template), List.of());
    }

    private static ElementSpec adaptList(Mc263AncientCityGrammar evidence,
            Mc263AncientCityGrammar.ListElement list) {
        Bounds union = null;
        List<ConnectorSpec> surface = null;
        ArrayList<String> components = new ArrayList<>();
        String first = null;
        for (Mc263AncientCityGrammar.SingleChild child : list.childrenInDeclaredOrder()) {
            Mc263AncientCityGrammar.Template template = evidence.requireTemplate(child.template());
            genericProcessor(child.processorList());
            if (first == null) first = child.template();
            components.add(child.template());
            Bounds childBounds = templateBounds(template);
            union = union == null ? childBounds : union(union, childBounds);
            List<ConnectorSpec> childSurface = connectors(template);
            if (surface == null) surface = childSurface;
            else require(sameConnectors(surface, childSurface),
                    "Ancient City list connector surface drift");
        }
        require(first != null && union != null && surface != null,
                "Ancient City list element is empty");
        return new ElementSpec(ElementType.LIST, first, list.weight(), Projection.RIGID, "",
                union, surface, components);
    }

    private static Bounds templateBounds(Mc263AncientCityGrammar.Template template) {
        if (template.status() == Mc263AncientCityGrammar.SourceStatus.EXPECTED_ABSENT) {
            require(template.id().equals(EXPECTED_ABSENT),
                    "unknown expected-absent Ancient City template");
            return new Bounds(-1, -1, -1, 0, 0, 0);
        }
        Mc263AncientCityGrammar.Size size = template.size().orElseThrow(() ->
                new IllegalArgumentException("present Ancient City template lacks size: "
                        + template.id()));
        return new Bounds(0, 0, 0, size.x() - 1, size.y() - 1, size.z() - 1);
    }

    private static List<ConnectorSpec> connectors(Mc263AncientCityGrammar.Template template) {
        ArrayList<ConnectorSpec> result = new ArrayList<>();
        for (Mc263AncientCityGrammar.Connector connector : template.connectors()) {
            result.add(new ConnectorSpec(connector.position().x(), connector.position().y(),
                    connector.position().z(), direction(connector.front()),
                    direction(connector.top()), Joint.ROLLABLE, connector.name(), connector.target(),
                    connector.pool(), connector.placementPriority(), connector.selectionPriority()));
        }
        return List.copyOf(result);
    }

    private static Direction direction(Mc263AncientCityGrammar.Direction direction) {
        return Direction.valueOf(direction.name());
    }

    private static String genericProcessor(String processor) {
        return switch (processor) {
            case INLINE -> "";
            case "minecraft:ancient_city_start_degradation",
                    "minecraft:ancient_city_generic_degradation",
                    "minecraft:ancient_city_walls_degradation" -> processor;
            default -> throw new IllegalArgumentException(
                    "unknown Ancient City processor list: " + processor);
        };
    }

    private static List<TemplateCapability> templateCapabilities(
            Mc263AncientCityGrammar evidence) {
        ArrayList<TemplateCapability> result = new ArrayList<>();
        for (Mc263AncientCityGrammar.Template template : evidence.templatesInEncounterOrder()) {
            TemplateStatus status = switch (template.status()) {
                case PRESENT -> TemplateStatus.PRESENT;
                case EXPECTED_ABSENT -> TemplateStatus.EXPECTED_ABSENT;
            };
            result.add(new TemplateCapability(template.id(), status));
        }
        return List.copyOf(result);
    }

    private static List<String> processorCapabilities(Mc263AncientCityGrammar evidence) {
        ArrayList<String> result = new ArrayList<>();
        for (Mc263AncientCityGrammar.ProcessorListSpec processor
                : evidence.processorListsInEncounterOrder()) {
            genericProcessor(processor.identity());
            result.add(processor.identity());
        }
        return List.copyOf(result);
    }

    private static Map<List<String>, List<ListChild>> listChildren(
            Mc263AncientCityGrammar evidence) {
        LinkedHashMap<List<String>, List<ListChild>> result = new LinkedHashMap<>();
        for (Mc263AncientCityGrammar.Pool pool : evidence.poolsInExecutionOrder()) {
            for (Mc263AncientCityGrammar.PoolElement element : pool.elementsInDeclaredOrder()) {
                if (!(element instanceof Mc263AncientCityGrammar.ListElement list)) continue;
                ArrayList<ListChild> children = new ArrayList<>();
                for (Mc263AncientCityGrammar.SingleChild child : list.childrenInDeclaredOrder()) {
                    children.add(new ListChild(child.template(), child.processorList()));
                }
                List<String> key = children.stream().map(ListChild::template).toList();
                require(result.putIfAbsent(key, List.copyOf(children)) == null,
                        "duplicate Ancient City list composition");
            }
        }
        return Map.copyOf(result);
    }

    private Plan normalize(ExecutionPlan generic) {
        ArrayList<Piece> pieces = new ArrayList<>();
        for (int ordinal = 0; ordinal < generic.pieces().size(); ordinal++) {
            PiecePlacement raw = generic.pieces().get(ordinal);
            PieceKind kind;
            List<ListChild> children = List.of();
            String processor = raw.processor().isEmpty() ? INLINE : raw.processor();
            if (raw.type() == ElementType.SINGLE) {
                kind = PieceKind.TEMPLATE;
                evidence.requireTemplate(raw.elementKey());
                genericProcessor(processor);
            } else if (raw.type() == ElementType.LIST) {
                kind = PieceKind.LIST;
                children = listChildren.get(raw.components());
                require(children != null, "unknown generated Ancient City list composition");
                processor = "";
            } else if (raw.type() == ElementType.FEATURE) {
                kind = PieceKind.FEATURE;
                require(raw.elementKey().equals(SCULK_FEATURE),
                        "unknown generated Ancient City feature");
                processor = "";
            } else {
                throw new IllegalArgumentException("unsupported generated Ancient City element: "
                        + raw.type());
            }
            ArrayList<Junction> junctions = new ArrayList<>();
            for (Mc263JigsawStructureExecutor.Junction junction : raw.junctions()) {
                junctions.add(new Junction(junction.sourceX(),
                        Math.addExact(junction.sourceGroundY(), ROOT_NORMALIZATION_Y),
                        junction.sourceZ(), junction.deltaY(), junction.destinationProjection()));
            }
            pieces.add(new Piece(ordinal, raw.depth(), kind, raw.elementKey(), processor,
                    children, raw.originX(),
                    Math.addExact(raw.originY(), ROOT_NORMALIZATION_Y), raw.originZ(),
                    raw.rotation(), raw.projection(), raw.groundLevelDelta(),
                    box(shiftY(raw.bounds(), ROOT_NORMALIZATION_Y)), junctions));
        }
        return new Plan(generic.structureKey(), generic.chunkX(), generic.chunkZ(),
                generic.centerX(), Math.addExact(generic.centerY(), ROOT_NORMALIZATION_Y),
                generic.centerZ(), generic.rotation(), pieces);
    }

    private static List<Edge> normalizeEdges(List<AcceptedEdge> raw, List<Piece> pieces) {
        ArrayList<Edge> result = new ArrayList<>();
        for (AcceptedEdge edge : raw) {
            ConnectorIdentity source = edge.sourceConnector();
            ConnectorIdentity target = edge.targetConnector();
            Piece child = pieces.get(edge.childPieceOrdinal());
            Connector sourceConnector = new Connector(source.ordinal(),
                    new Vec3i(source.x(), Math.addExact(source.y(), ROOT_NORMALIZATION_Y), source.z()),
                    source.front(), source.top(), source.joint(), source.name(), source.target(),
                    source.pool(), source.placementPriority(), source.selectionPriority());
            Connector targetConnector = new Connector(target.ordinal(),
                    new Vec3i(Math.addExact(child.originX(), target.x()),
                            Math.addExact(child.originY(), target.y()),
                            Math.addExact(child.originZ(), target.z())),
                    target.front(), target.top(), target.joint(),
                    child.kind() == PieceKind.FEATURE ? "<null>" : target.name(), target.target(),
                    target.pool(), target.placementPriority(), target.selectionPriority());
            require(edge.resolvedAliasTarget().isEmpty(),
                    "Ancient City unexpectedly resolved a pool alias");
            result.add(new Edge(edge.parentPieceOrdinal(), sourceConnector,
                    edge.childPieceOrdinal(), targetConnector, edge.selectedSourcePool(),
                    edge.selectedSourcePool()));
        }
        return List.copyOf(result);
    }

    private static void validateGeneratedPlan(Plan plan, List<AcceptedEdge> edges) {
        require(plan.structureKey().equals(STRUCTURE_KEY), "Ancient City plan key drift");
        require(!plan.pieces().isEmpty(), "Ancient City generated an empty piece graph");
        require(plan.centerY() == START_Y, "Ancient City normalized stub Y drift");
        require(edges.size() == plan.pieces().size() - 1,
                "Ancient City connector graph cardinality drift");
        for (int ordinal = 0; ordinal < plan.pieces().size(); ordinal++) {
            Piece piece = plan.pieces().get(ordinal);
            require(piece.ordinal() == ordinal, "Ancient City piece ordinal drift");
            require(piece.projection() == Projection.RIGID,
                    "Ancient City generated non-rigid piece");
        }
        for (AcceptedEdge edge : edges) {
            require(edge.parentPieceOrdinal() >= 0
                            && edge.parentPieceOrdinal() < edge.childPieceOrdinal()
                            && edge.childPieceOrdinal() < plan.pieces().size(),
                    "Ancient City generated parent/child order drift");
            require(edge.resolvedAliasTarget().isEmpty(),
                    "Ancient City generated unexpected alias resolution");
        }
    }

    private Carrier encodeCarrier(Plan plan, int chunkX, int chunkZ) {
        ArrayList<BinaryNbt> pieces = new ArrayList<>();
        for (Piece piece : plan.pieces()) {
            pieces.add(new BinaryNbt(writeRoot(out -> writePiecePayload(out, piece))));
        }
        byte[] predecessor = encodeStructureStart(plan, chunkX, chunkZ, 0);
        byte[] successor = encodeStructureStart(plan, chunkX, chunkZ, 1);
        validateMutableSuccessorPair(predecessor, successor);
        return new Carrier(CARRIER_FORMAT, pieces, new BinaryNbt(predecessor, successor));
    }

    private static byte[] encodeStructureStart(Plan plan, int chunkX, int chunkZ, int references) {
        require(references == 0 || references == 1,
                "Ancient City carrier reference count is outside authenticated transition");
        return writeRoot(out -> {
            intTag(out, "references", references);
            intTag(out, "ChunkZ", chunkZ);
            stringTag(out, "id", STRUCTURE_KEY);
            out.writeByte(9); out.writeUTF("Children"); out.writeByte(10);
            out.writeInt(plan.pieces().size());
            for (Piece piece : plan.pieces()) {
                writePiecePayload(out, piece);
                out.writeByte(0);
            }
            intTag(out, "ChunkX", chunkX);
        });
    }

    private static void validateMutableSuccessorPair(byte[] predecessor, byte[] successor) {
        require(predecessor.length == successor.length,
                "Ancient City mutable successor length drift");
        int differences = 0;
        int changedOffset = -1;
        for (int index = 0; index < predecessor.length; index++) {
            if (predecessor[index] == successor[index]) continue;
            differences++;
            changedOffset = index;
        }
        require(differences == 1 && changedOffset == SUCCESSOR_CHANGED_BYTE_OFFSET
                        && predecessor[changedOffset] == 0 && successor[changedOffset] == 1,
                "Ancient City mutable successor escaped references-only 0->1 delta");
    }

    private static void writePiecePayload(DataOutputStream out, Piece piece) throws IOException {
        Box box = piece.boundingBox();
        out.writeByte(11); out.writeUTF("BB"); out.writeInt(6);
        out.writeInt(box.minX()); out.writeInt(box.minY()); out.writeInt(box.minZ());
        out.writeInt(box.maxX()); out.writeInt(box.maxY()); out.writeInt(box.maxZ());
        intTag(out, "PosZ", piece.originZ());
        intTag(out, "PosX", piece.originX());
        out.writeByte(10); out.writeUTF("pool_element");
        if (piece.kind() == PieceKind.TEMPLATE) {
            writeSinglePoolElement(out, piece.elementKey(), piece.processor(), piece.projection());
        } else if (piece.kind() == PieceKind.LIST) {
            out.writeByte(9); out.writeUTF("elements"); out.writeByte(10);
            out.writeInt(piece.children().size());
            for (ListChild child : piece.children()) {
                writeSinglePoolElement(out, child.template(), child.processor(), piece.projection());
                out.writeByte(0);
            }
            stringTag(out, "projection", projectionNbt(piece.projection()));
            stringTag(out, "element_type", LIST_TYPE);
        } else if (piece.kind() == PieceKind.FEATURE) {
            stringTag(out, "feature", piece.elementKey());
            stringTag(out, "projection", projectionNbt(piece.projection()));
            stringTag(out, "element_type", FEATURE_TYPE);
        } else {
            throw new IllegalArgumentException("unsupported Ancient City carrier piece kind");
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
        for (Junction junction : piece.junctions()) {
            intTag(out, "source_z", junction.sourceZ());
            intTag(out, "source_x", junction.sourceX());
            intTag(out, "delta_y", junction.deltaY());
            intTag(out, "source_ground_y", junction.sourceGroundY());
            stringTag(out, "dest_proj", projectionNbt(junction.destinationProjection()));
            out.writeByte(0);
        }
    }

    private static void writeSinglePoolElement(DataOutputStream out, String template,
            String processor, Projection projection) throws IOException {
        stringTag(out, "location", template);
        if (processor.equals(INLINE)) {
            out.writeByte(10); out.writeUTF("processors");
            out.writeByte(9); out.writeUTF("processors"); out.writeByte(0); out.writeInt(0);
            out.writeByte(0);
        } else {
            genericProcessor(processor);
            stringTag(out, "processors", processor);
        }
        stringTag(out, "projection", projectionNbt(projection));
        stringTag(out, "element_type", SINGLE_TYPE);
    }

    private static String projectionNbt(Projection projection) {
        return projection.name().toLowerCase(Locale.ROOT);
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
            throw new IllegalStateException("failed to encode Ancient City carrier", impossible);
        }
    }

    private static void intTag(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(3); out.writeUTF(name); out.writeInt(value);
    }

    private static void stringTag(DataOutputStream out, String name, String value)
            throws IOException {
        out.writeByte(8); out.writeUTF(name); out.writeUTF(value);
    }

    private static Box aggregate(List<Piece> pieces) {
        Box result = pieces.get(0).boundingBox();
        for (int index = 1; index < pieces.size(); index++) {
            result = result.union(pieces.get(index).boundingBox());
        }
        return result;
    }

    private static Box box(Bounds box) {
        return new Box(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    private static Bounds shiftY(Bounds box, int delta) {
        return new Bounds(box.minX(), Math.addExact(box.minY(), delta), box.minZ(),
                box.maxX(), Math.addExact(box.maxY(), delta), box.maxZ());
    }

    private static Bounds union(Bounds first, Bounds second) {
        return new Bounds(Math.min(first.minX(), second.minX()),
                Math.min(first.minY(), second.minY()), Math.min(first.minZ(), second.minZ()),
                Math.max(first.maxX(), second.maxX()), Math.max(first.maxY(), second.maxY()),
                Math.max(first.maxZ(), second.maxZ()));
    }

    private static boolean sameConnectors(List<ConnectorSpec> first, List<ConnectorSpec> second) {
        if (first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) {
            ConnectorSpec a = first.get(index);
            ConnectorSpec b = second.get(index);
            if (a.x() != b.x() || a.y() != b.y() || a.z() != b.z()
                    || a.front() != b.front() || a.top() != b.top() || a.joint() != b.joint()
                    || !a.name().equals(b.name()) || !a.target().equals(b.target())
                    || !a.pool().equals(b.pool())
                    || a.placementPriority() != b.placementPriority()
                    || a.selectionPriority() != b.selectionPriority()) return false;
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

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public interface Publisher {
        boolean supports(String structureKey, String carrierFormat);
        void publishAtomically(Start start);
    }

    public interface WorldAccess {
        boolean supportsBuildHeightBoundary();
        boolean supportsBiomeMembership(String structureKey);
        boolean supportsTemplate(String template, TemplateStatus status);
        boolean supportsProcessorList(String processorIdentity);
        boolean supportsConfiguredFeature(String featureKey);
        int minBuildY();
        int maxBuildY();
        BiomeSample biomeAt(int blockX, int blockY, int blockZ);
    }

    public enum TemplateStatus { PRESENT, EXPECTED_ABSENT }
    public enum PieceKind { TEMPLATE, LIST, FEATURE }

    public record ChunkPos(int x, int z) {}
    public record Vec3i(int x, int y, int z) {}

    public record BiomeSample(String biomeKey, boolean validForStructure) {
        public BiomeSample { requireRegistryKey(biomeKey, "biome key"); }
    }

    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Box {
            require(minX <= maxX && minY <= maxY && minZ <= maxZ,
                    "inverted Ancient City box");
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
        public boolean intersectsChunk(int chunkX, int chunkZ) {
            long chunkMinX = Math.multiplyExact((long) chunkX, 16L);
            long chunkMinZ = Math.multiplyExact((long) chunkZ, 16L);
            long chunkMaxX = chunkMinX + 15L;
            long chunkMaxZ = chunkMinZ + 15L;
            return maxX >= chunkMinX && minX <= chunkMaxX
                    && maxZ >= chunkMinZ && minZ <= chunkMaxZ;
        }
    }

    public record ListChild(String template, String processor) {
        public ListChild {
            requireRegistryKey(template, "list child template");
            require(processor.equals(INLINE)
                            || processor.equals("minecraft:ancient_city_start_degradation")
                            || processor.equals("minecraft:ancient_city_generic_degradation")
                            || processor.equals("minecraft:ancient_city_walls_degradation"),
                    "unknown Ancient City list-child processor");
        }
    }

    public record Junction(int sourceX, int sourceGroundY, int sourceZ, int deltaY,
            Projection destinationProjection) {
        public Junction { Objects.requireNonNull(destinationProjection); }
    }

    public record Piece(int ordinal, int depth, PieceKind kind, String elementKey,
            String processor, List<ListChild> children, int originX, int originY, int originZ,
            Rotation rotation, Projection projection, int groundLevelDelta, Box boundingBox,
            List<Junction> junctions) {
        public Piece {
            require(ordinal >= 0 && depth >= 0, "negative Ancient City piece identity");
            Objects.requireNonNull(kind); requireRegistryKey(elementKey, "piece element key");
            Objects.requireNonNull(processor); children = List.copyOf(children);
            Objects.requireNonNull(rotation); Objects.requireNonNull(projection);
            Objects.requireNonNull(boundingBox); junctions = List.copyOf(junctions);
        }
    }

    public record Connector(int ordinal, Vec3i position, Direction front, Direction top,
            Joint joint, String name, String target, String pool,
            int placementPriority, int selectionPriority) {
        public Connector {
            require(ordinal >= 0, "negative Ancient City connector ordinal");
            Objects.requireNonNull(position); Objects.requireNonNull(front); Objects.requireNonNull(top);
            Objects.requireNonNull(joint); requireOptionalRegistryKey(name, "connector name");
            requireRegistryKey(target, "connector target"); requireRegistryKey(pool, "connector pool");
        }
    }

    public record Edge(int sourcePiece, Connector sourceConnector, int targetPiece,
            Connector targetConnector, String selectedPool, String resolvedAlias) {
        public Edge {
            require(sourcePiece >= 0 && targetPiece > sourcePiece,
                    "invalid Ancient City edge order");
            Objects.requireNonNull(sourceConnector); Objects.requireNonNull(targetConnector);
            requireRegistryKey(selectedPool, "selected pool");
            requireRegistryKey(resolvedAlias, "resolved alias");
        }
    }

    public record Plan(String structureKey, int chunkX, int chunkZ, int centerX, int centerY,
            int centerZ, Rotation rotation, List<Piece> pieces) {
        public Plan {
            require(STRUCTURE_KEY.equals(structureKey), "unknown Ancient City plan key");
            Objects.requireNonNull(rotation); pieces = List.copyOf(pieces);
            require(!pieces.isEmpty(), "empty Ancient City plan");
        }
    }

    public static final class BinaryNbt {
        private final byte[] bytes;
        private final byte[] mutableSuccessor;
        private final String sha256;
        private BinaryNbt(byte[] bytes) { this(bytes, bytes); }
        private BinaryNbt(byte[] bytes, byte[] mutableSuccessor) {
            this.bytes = bytes.clone();
            this.mutableSuccessor = mutableSuccessor.clone();
            this.sha256 = Mc263AncientCityProducer.sha256(this.bytes);
        }
        public int length() { return bytes.length; }
        public String sha256() { return sha256; }
        public byte[] bytes() { return bytes.clone(); }
        public byte[] mutableSuccessor() { return mutableSuccessor.clone(); }
    }

    public record Carrier(String format, List<BinaryNbt> pieces, BinaryNbt structureStart) {
        public Carrier {
            require(CARRIER_FORMAT.equals(format), "unknown Ancient City carrier format");
            pieces = List.copyOf(pieces); Objects.requireNonNull(structureStart);
        }
    }

    public record Start(long worldSeed, int chunkX, int chunkZ, Box aggregateBoundingBox,
            Vec3i stubPosition, BiomeSample biome, Plan plan, List<Edge> acceptedEdges,
            PlannerRngReceipt generationRng, Carrier carrier) {
        public Start {
            Objects.requireNonNull(aggregateBoundingBox); Objects.requireNonNull(stubPosition);
            Objects.requireNonNull(biome); Objects.requireNonNull(plan);
            acceptedEdges = List.copyOf(acceptedEdges);
            Objects.requireNonNull(generationRng); Objects.requireNonNull(carrier);
        }
    }

    private record TemplateCapability(String template, TemplateStatus status) {}
    private record Preflight(int minBuildY, int maxBuildY) {}

    private interface IoWriter { void write(DataOutputStream out) throws IOException; }

    /** Keeps generic collision/build-height space translation-equivalent to vanilla Ancient Y. */
    private static final class ShiftedHeightResolver implements HeightResolver {
        private final int minBuildY;
        private final int maxBuildY;
        private int queryCount;
        private ShiftedHeightResolver(int minBuildY, int maxBuildY) {
            this.minBuildY = Math.addExact(minBuildY, ROOT_ANCHOR_Y);
            this.maxBuildY = Math.addExact(maxBuildY, ROOT_ANCHOR_Y);
        }
        @Override public int firstFreeY(int blockX, int blockZ) {
            queryCount++;
            throw new IllegalArgumentException(
                    "Ancient City rigid graph unexpectedly requested terrain projection");
        }
        @Override public int minBuildY() { return minBuildY; }
        @Override public int maxBuildY() { return maxBuildY; }
        int queryCount() { return queryCount; }
    }

    private static void requireRegistryKey(String value, String label) {
        require(value != null && value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"),
                label + " is not a registry key");
    }

    private static void requireOptionalRegistryKey(String value, String label) {
        require(value != null && (value.isEmpty() || value.equals("<null>")
                        || value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")),
                label + " is not an optional registry key");
    }
}
