package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Bounds;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ConnectorSpec;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Direction;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ElementSpec;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ElementType;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Grammar;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Joint;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.PoolSpec;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Projection;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.AcceptedEdge;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.ExecutionLimits;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.ExecutionPlan;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.HeightResolver;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.PiecePlacement;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.PlannerRngReceipt;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.RandomDraw;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.TracedPlan;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant deterministic Minecraft 26.3 Trial Chambers start producer.
 *
 * <p>The accepted typed grammar is adapted once to the generic Jigsaw D2 executor. The accepted
 * start-graph corpus is consumed only while constructing the pinned singleton to prove closure of
 * template, processor, projection, rotation and alias identities; generation never looks up a
 * probe. Each call performs one traced planner pass, derives a compact collision-resistant receipt
 * from that same ordered LegacyRandomSource operation stream, encodes canonical persisted start
 * NBT, then publishes one immutable start atomically. Template settlement is deliberately outside
 * this boundary.</p>
 */
public final class Mc263TrialChambersProducer {
    public static final String STRUCTURE_KEY = "minecraft:trial_chambers";
    public static final String CARRIER_FORMAT = "minecraft-26.3-trial-chambers-start-nbt-v1";

    private static final int MIN_BUILD_Y = -64;
    private static final int MAX_BUILD_Y = 320;
    private static final int TERRAIN_PADDING = 12;
    private static final long LEGACY_MASK = (1L << 48) - 1L;
    private static final ExecutionLimits LIMITS = new ExecutionLimits(512, 4_096, 2_000_000);
    private static final String EMPTY_POOL = "minecraft:empty";
    private static final String INLINE = "inline";
    private static final String COPPER = "minecraft:trial_chambers_copper_bulb_degradation";
    private static final String SINGLE_TYPE = "minecraft:single_pool_element";
    private static final String LIQUID_SETTINGS = "ignore_waterlogging";

    private final Grammar grammar;
    private final Set<PieceIdentity> pieceClosure;
    private final Set<String> poolClosure;
    private final Map<String, Set<String>> aliasTargets;

    private Mc263TrialChambersProducer(Mc263TrialChambersGrammar.Corpus grammarCorpus,
            Mc263TrialChambersProductionAuthority.StartGraphPins startGraph) {
        Objects.requireNonNull(grammarCorpus, "Trial Chambers grammar");
        Objects.requireNonNull(startGraph, "Trial Chambers start-graph pins");
        Mc263TrialChambersGrammar.Evidence evidence = grammarCorpus.evidence();
        this.poolClosure = poolClosure(evidence);
        this.aliasTargets = aliasTargets(evidence, poolClosure);
        PieceClosure pieces = pieceClosure(evidence);
        this.pieceClosure = pieces.identities();
        this.grammar = adaptGrammar(evidence, poolClosure, aliasTargets);
        validateEvidenceClosure(evidence, startGraph, pieces, poolClosure, aliasTargets);
    }

    public static Mc263TrialChambersProducer pinned() {
        return Holder.PINNED;
    }

    private static final class Holder {
        private static final Mc263TrialChambersProducer PINNED =
                new Mc263TrialChambersProducer(Mc263TrialChambersGrammar.pinned(),
                        Mc263TrialChambersProductionAuthority.startGraphPins());
    }

    /** Generates one complete immutable start and publishes it exactly once after full validation. */
    public Start generate(long worldSeed, int chunkX, int chunkZ, WorldAccess world,
            Publisher publisher) {
        preflight(world, publisher);
        int minBuildY = world.minBuildY();
        int maxBuildY = world.maxBuildY();
        if (minBuildY != MIN_BUILD_Y || maxBuildY != MAX_BUILD_Y) {
            throw new IllegalArgumentException("unsupported Trial Chambers build-height boundary: "
                    + minBuildY + ".." + maxBuildY);
        }

        NoProjectionHeightResolver heights = new NoProjectionHeightResolver(minBuildY, maxBuildY);
        TracedPlan traced = Mc263JigsawStructureExecutor.planWithTrace(
                worldSeed, chunkX, chunkZ, grammar, heights, LIMITS);
        ExecutionPlan plan = traced.executionPlan();
        require(plan.present(), "Trial Chambers start unexpectedly absent");
        require(plan.structureKey().equals(STRUCTURE_KEY), "Trial Chambers structure-key drift");
        require(plan.chunkX() == chunkX && plan.chunkZ() == chunkZ,
                "Trial Chambers chunk identity drift");
        require(!plan.pieces().isEmpty(), "Trial Chambers generated an empty piece graph");
        validateGeneratedPlan(plan, traced.acceptedEdges());

        Box aggregate = aggregate(plan.pieces()).inflate(TERRAIN_PADDING);
        Vec3i stub = new Vec3i(plan.centerX(), plan.centerY(), plan.centerZ());
        GenerationRng generationRng = summarizeRng(traced.generationRng());
        Carrier carrier = encodeCarrier(plan, chunkX, chunkZ);
        Start start = new Start(worldSeed, chunkX, chunkZ, aggregate, stub, plan,
                traced.acceptedEdges(), List.of(), generationRng, carrier);
        publisher.publishAtomically(start);
        return start;
    }

    private static void preflight(WorldAccess world, Publisher publisher) {
        if (world == null || publisher == null) {
            throw new IllegalArgumentException("Trial Chambers world/publisher are required");
        }
        boolean buildHeight = world.supportsBuildHeightBoundary();
        boolean publication = publisher.supports(STRUCTURE_KEY, CARRIER_FORMAT);
        if (!buildHeight || !publication) {
            throw new IllegalArgumentException("Trial Chambers capability closure is incomplete");
        }
    }

    private static Grammar adaptGrammar(Mc263TrialChambersGrammar.Evidence evidence) {
        Set<String> pools = poolClosure(evidence);
        Map<String, Set<String>> aliases = aliasTargets(evidence, pools);
        return adaptGrammar(evidence, pools, aliases);
    }

    private static Grammar adaptGrammar(Mc263TrialChambersGrammar.Evidence evidence,
            Set<String> pools, Map<String, Set<String>> aliases) {
        Objects.requireNonNull(evidence, "Trial Chambers typed evidence");
        require(evidence.structure().registryKey().equals(STRUCTURE_KEY),
                "unknown Trial Chambers structure key");
        require(evidence.structure().startPool().equals("minecraft:trial_chambers/chamber/end"),
                "unknown Trial Chambers start pool");

        ArrayList<PoolSpec> adapted = new ArrayList<>();
        for (Mc263TrialChambersGrammar.Pool pool : evidence.poolsInEncounterOrder()) {
            require(pools.contains(pool.key()), "unknown Trial Chambers pool: " + pool.key());
            require(pool.fallback().equals(EMPTY_POOL) || pools.contains(pool.fallback()),
                    "unknown Trial Chambers fallback pool: " + pool.fallback());
            ArrayList<ElementSpec> elements = new ArrayList<>();
            for (Mc263TrialChambersGrammar.PoolElement element
                    : pool.elementsInDeclaredOrder()) {
                if (element instanceof Mc263TrialChambersGrammar.SingleElement single) {
                    require(single.projection() == Mc263TrialChambersGrammar.Projection.RIGID,
                            "unsupported Trial Chambers single projection");
                    Mc263TrialChambersGrammar.Template template =
                            evidence.requireTemplate(single.template());
                    Bounds bounds = bounds(template);
                    List<ConnectorSpec> connectors = connectors(template, bounds, pools, aliases);
                    elements.add(new ElementSpec(ElementType.SINGLE, single.template(),
                            single.weight(), Projection.RIGID,
                            processor(single.processorList().id()), bounds, connectors, List.of()));
                } else if (element instanceof Mc263TrialChambersGrammar.EmptyElement empty) {
                    require(empty.projection()
                                    == Mc263TrialChambersGrammar.Projection.TERRAIN_MATCHING,
                            "unsupported Trial Chambers empty projection");
                    elements.add(new ElementSpec(ElementType.EMPTY, "", empty.weight(),
                            Projection.TERRAIN_MATCHING, "", null, List.of(), List.of()));
                } else {
                    throw new IllegalArgumentException("unknown Trial Chambers pool element");
                }
            }
            adapted.add(new PoolSpec(pool.key(), pool.fallback(), elements));
        }
        return new Grammar(STRUCTURE_KEY, adapted);
    }

    private static Set<String> poolClosure(Mc263TrialChambersGrammar.Evidence evidence) {
        Objects.requireNonNull(evidence, "Trial Chambers typed evidence");
        LinkedHashSet<String> pools = new LinkedHashSet<>();
        for (Mc263TrialChambersGrammar.Pool pool : evidence.poolsInEncounterOrder()) {
            require(pools.add(pool.key()), "duplicate Trial Chambers pool: " + pool.key());
        }
        require(pools.equals(new LinkedHashSet<>(evidence.registryPoolKeysInEncounterOrder())),
                "Trial Chambers registry/pool order closure drift");
        return Set.copyOf(pools);
    }

    private static Map<String, Set<String>> aliasTargets(
            Mc263TrialChambersGrammar.Evidence evidence, Set<String> pools) {
        LinkedHashMap<String, LinkedHashSet<String>> mutable = new LinkedHashMap<>();
        int ordinal = 0;
        for (Mc263TrialChambersGrammar.AliasBinding binding
                : evidence.aliasesInDeclaredOrder()) {
            require(binding.ordinal() == ordinal++, "Trial Chambers alias ordinal drift");
            if (binding instanceof Mc263TrialChambersGrammar.RandomAlias random) {
                for (Mc263TrialChambersGrammar.WeightedTarget target : random.targets()) {
                    addAlias(mutable, random.alias(), target.data());
                }
            } else if (binding instanceof Mc263TrialChambersGrammar.RandomGroupAlias group) {
                for (Mc263TrialChambersGrammar.AliasGroup choice : group.groups()) {
                    for (Mc263TrialChambersGrammar.DirectAlias direct : choice.data()) {
                        addAlias(mutable, direct.alias(), direct.target());
                    }
                }
            } else {
                throw new IllegalArgumentException("unknown Trial Chambers alias binding");
            }
        }
        LinkedHashMap<String, Set<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> entry : mutable.entrySet()) {
            require(!pools.contains(entry.getKey()),
                    "Trial Chambers alias collides with concrete pool: " + entry.getKey());
            require(!entry.getValue().isEmpty(),
                    "Trial Chambers alias has no targets: " + entry.getKey());
            for (String target : entry.getValue()) {
                require(pools.contains(target),
                        "unknown Trial Chambers alias target pool: " + target);
            }
            result.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    private static void addAlias(Map<String, LinkedHashSet<String>> aliases,
            String alias, String target) {
        require(alias != null && target != null, "null Trial Chambers alias identity");
        aliases.computeIfAbsent(alias, ignored -> new LinkedHashSet<>()).add(target);
    }

    private static PieceClosure pieceClosure(Mc263TrialChambersGrammar.Evidence evidence) {
        HashSet<PieceIdentity> identities = new HashSet<>();
        HashMap<PieceIdentity, List<String>> processorOrders = new HashMap<>();
        for (Mc263TrialChambersGrammar.Pool pool : evidence.poolsInEncounterOrder()) {
            for (Mc263TrialChambersGrammar.PoolElement element
                    : pool.elementsInDeclaredOrder()) {
                if (!(element instanceof Mc263TrialChambersGrammar.SingleElement single)) continue;
                evidence.requireTemplate(single.template());
                PieceIdentity identity = new PieceIdentity(single.template(),
                        processor(single.processorList().id()));
                identities.add(identity);
                List<String> order = processorOrder(evidence, single.processorList());
                List<String> previous = processorOrders.putIfAbsent(identity, order);
                require(previous == null || previous.equals(order),
                        "Trial Chambers template/processor closure is ambiguous: "
                                + single.template());
            }
        }
        return new PieceClosure(Set.copyOf(identities), Map.copyOf(processorOrders));
    }

    private static List<String> processorOrder(Mc263TrialChambersGrammar.Evidence evidence,
            Mc263TrialChambersGrammar.ProcessorListIdentity identity) {
        Mc263TrialChambersGrammar.ProcessorList selected = null;
        for (Mc263TrialChambersGrammar.ProcessorList list
                : evidence.processorListsInEncounterOrder()) {
            if (list.identity() == identity) {
                require(selected == null, "duplicate Trial Chambers processor list identity");
                selected = list;
            }
        }
        require(selected != null, "missing Trial Chambers processor list identity: " + identity);
        ArrayList<String> order = new ArrayList<>();
        for (Mc263TrialChambersGrammar.ProcessorType type : selected.processorsInOrder()) {
            String runtimeClass = null;
            for (Mc263TrialChambersGrammar.Processor candidate
                    : evidence.processorsInEncounterOrder()) {
                if (candidate.type() == type) {
                    require(runtimeClass == null,
                            "duplicate Trial Chambers processor semantic identity: " + type);
                    runtimeClass = candidate.runtimeClass();
                }
            }
            require(runtimeClass != null, "missing Trial Chambers processor semantic identity: " + type);
            order.add(runtimeClass);
        }
        return List.copyOf(order);
    }

    /**
     * Proves the generated grammar closure covers every accepted start-graph piece/alias pin.
     *
     * <p>The pins are the distilled projection of the two accepted G1T start probes carried by
     * {@link Mc263TrialChambersProductionAuthority}; the multi-megabyte start-graph corpus itself
     * stays a test-only oracle and is never read here.</p>
     */
    private static void validateEvidenceClosure(Mc263TrialChambersGrammar.Evidence evidence,
            Mc263TrialChambersProductionAuthority.StartGraphPins startGraph, PieceClosure pieces,
            Set<String> pools, Map<String, Set<String>> aliases) {
        require(startGraph.probeCount() == 2, "Trial Chambers start-graph probe drift");
        for (Mc263TrialChambersProductionAuthority.PiecePin pin : startGraph.pieces()) {
            evidence.requireTemplate(pin.template());
            String planProcessor = processor(pin.processorRegistryKey());
            PieceIdentity identity = new PieceIdentity(pin.template(), planProcessor);
            require(pieces.identities().contains(identity),
                    "unknown Trial Chambers placed template/processor evidence");
            require(pin.processorOrder().equals(pieces.processorOrders().get(identity)),
                    "Trial Chambers placed processor order drift");
            require(pin.projection().equals("rigid"),
                    "unsupported Trial Chambers placed projection");
            rotationNbt(pin.rotation());
        }
        for (Mc263TrialChambersProductionAuthority.AliasPin pin : startGraph.aliases()) {
            validateAlias(pin.selectedPool(),
                    pin.selectedPool().equals(pin.resolvedAlias()) ? "" : pin.resolvedAlias(),
                    pools, aliases);
        }
    }

    private void validateGeneratedPlan(ExecutionPlan plan, List<AcceptedEdge> edges) {
        require(edges.size() == plan.pieces().size() - 1,
                "Trial Chambers generated graph edge cardinality drift");
        for (PiecePlacement piece : plan.pieces()) {
            require(piece.type() == ElementType.SINGLE,
                    "unknown Trial Chambers generated piece type: " + piece.type());
            require(piece.projection() == Projection.RIGID,
                    "unsupported Trial Chambers generated projection");
            require(piece.bounds() != null, "Trial Chambers generated piece has no bounds");
            require(pieceClosure.contains(new PieceIdentity(piece.elementKey(), piece.processor())),
                    "unknown Trial Chambers generated template/processor");
            rotationNbt(piece.rotation().name());
        }
        for (int index = 0; index < edges.size(); index++) {
            AcceptedEdge edge = edges.get(index);
            require(edge.childPieceOrdinal() == index + 1
                            && edge.parentPieceOrdinal() >= 0
                            && edge.parentPieceOrdinal() < edge.childPieceOrdinal(),
                    "Trial Chambers generated parent/child order drift");
            require(edge.sourceConnector().pool().equals(edge.selectedSourcePool()),
                    "Trial Chambers generated source-pool provenance drift");
            validateAlias(edge.selectedSourcePool(), edge.resolvedAliasTarget(),
                    poolClosure, aliasTargets);
        }
    }

    private static void validateAlias(String selectedPool, String resolvedAliasTarget,
            Set<String> pools, Map<String, Set<String>> aliases) {
        Set<String> targets = aliases.get(selectedPool);
        if (targets == null) {
            require(pools.contains(selectedPool),
                    "unknown Trial Chambers selected pool: " + selectedPool);
            require(resolvedAliasTarget.isEmpty(),
                    "unexpected Trial Chambers alias resolution: " + selectedPool);
            return;
        }
        require(!resolvedAliasTarget.isEmpty() && targets.contains(resolvedAliasTarget),
                "unknown Trial Chambers alias resolution: " + selectedPool + " -> "
                        + resolvedAliasTarget);
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

    private static void writePiecePayload(DataOutputStream out, PiecePlacement piece)
            throws IOException {
        require(piece.type() == ElementType.SINGLE,
                "unsupported Trial Chambers carrier piece: " + piece.type());
        Bounds box = piece.bounds();
        out.writeByte(11); out.writeUTF("BB"); out.writeInt(6);
        out.writeInt(box.minX()); out.writeInt(box.minY()); out.writeInt(box.minZ());
        out.writeInt(box.maxX()); out.writeInt(box.maxY()); out.writeInt(box.maxZ());
        intTag(out, "PosZ", piece.originZ());
        intTag(out, "PosX", piece.originX());
        out.writeByte(10); out.writeUTF("pool_element");
        writeSinglePoolElement(out, piece.elementKey(), piece.processor(), piece.projection());
        out.writeByte(0);
        stringTag(out, "liquid_settings", LIQUID_SETTINGS);
        intTag(out, "PosY", piece.originY());
        stringTag(out, "rotation", rotationNbt(piece.rotation().name()));
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

    private static void writeSinglePoolElement(DataOutputStream out, String template,
            String processor, Projection projection) throws IOException {
        stringTag(out, "location", template);
        if (processor.isEmpty()) {
            out.writeByte(10); out.writeUTF("processors");
            out.writeByte(9); out.writeUTF("processors"); out.writeByte(0); out.writeInt(0);
            out.writeByte(0);
        } else if (processor.equals(COPPER)) {
            stringTag(out, "processors", processor);
        } else {
            throw new IllegalArgumentException(
                    "unknown Trial Chambers carrier processor: " + processor);
        }
        stringTag(out, "projection", projectionNbt(projection));
        stringTag(out, "element_type", SINGLE_TYPE);
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
            throw new IllegalStateException("failed to encode Trial Chambers carrier", impossible);
        }
    }

    private static void intTag(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(3); out.writeUTF(name); out.writeInt(value);
    }

    private static void stringTag(DataOutputStream out, String name, String value)
            throws IOException {
        out.writeByte(8); out.writeUTF(name); out.writeUTF(value);
    }

    private static GenerationRng summarizeRng(PlannerRngReceipt receipt) {
        Objects.requireNonNull(receipt, "Trial Chambers planner RNG receipt");
        MessageDigest digest = sha256Digest();
        updateDigest(digest, "[");
        int nextBits = 0;
        for (int index = 0; index < receipt.draws().size(); index++) {
            RandomDraw draw = receipt.draws().get(index);
            require(draw.ordinal() == index, "Trial Chambers RNG ordinal drift");
            if (index > 0) updateDigest(digest, ",");
            if (draw.operation().equals("setSeed")) {
                require(draw.seedArgument() != null && draw.bitsArgument() == null
                                && draw.result() == null && draw.state48After() == null,
                        "malformed Trial Chambers setSeed trace row");
                updateDigest(digest, "{\"argument\":\"" + draw.seedArgument()
                        + "\",\"operation\":\"setSeed\",\"ordinal\":" + index + "}");
            } else if (draw.operation().equals("nextBits")) {
                require(draw.seedArgument() == null && draw.bitsArgument() != null
                                && draw.result() != null && draw.state48After() != null,
                        "malformed Trial Chambers nextBits trace row");
                require(draw.bitsArgument() >= 1 && draw.bitsArgument() <= 32,
                        "unsupported Trial Chambers nextBits width");
                require(draw.state48After() >= 0 && draw.state48After() <= LEGACY_MASK,
                        "Trial Chambers nextBits state outside Legacy48 range");
                nextBits++;
                updateDigest(digest, "{\"argument\":" + draw.bitsArgument()
                        + ",\"operation\":\"nextBits\",\"ordinal\":" + index
                        + ",\"result\":" + draw.result() + ",\"state48After\":\""
                        + draw.state48After() + "\"}");
            } else {
                throw new IllegalArgumentException(
                        "unknown Trial Chambers RNG operation: " + draw.operation());
            }
        }
        updateDigest(digest, "]");
        require(nextBits == receipt.worldgenCount(),
                "Trial Chambers RNG worldgen count drift");
        require(receipt.draws().size() == receipt.worldgenCount() + 2,
                "Trial Chambers RNG operation/count closure drift");
        return new GenerationRng(receipt.state48(), receipt.worldgenCount(),
                receipt.draws().size(), HexFormat.of().formatHex(digest.digest()),
                receipt.continuationNextLongI64());
    }

    private static void updateDigest(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String processor(String registryKey) {
        return switch (registryKey) {
            case INLINE -> "";
            case COPPER -> COPPER;
            default -> throw new IllegalArgumentException(
                    "unknown Trial Chambers processor: " + registryKey);
        };
    }

    private static String projectionNbt(Projection projection) {
        return switch (projection) {
            case RIGID -> "rigid";
            case TERRAIN_MATCHING -> "terrain_matching";
        };
    }

    private static String rotationNbt(String rotation) {
        return switch (rotation) {
            case "NONE", "CLOCKWISE_90", "CLOCKWISE_180", "COUNTERCLOCKWISE_90" -> rotation;
            default -> throw new IllegalArgumentException(
                    "unknown Trial Chambers rotation: " + rotation);
        };
    }

    private static Bounds bounds(Mc263TrialChambersGrammar.Template template) {
        Mc263TrialChambersGrammar.Vec3i size = template.size();
        require(size.x() > 0 && size.y() > 0 && size.z() > 0,
                "invalid Trial Chambers template size: " + template.key());
        return new Bounds(0, 0, 0, size.x() - 1, size.y() - 1, size.z() - 1);
    }

    private static List<ConnectorSpec> connectors(Mc263TrialChambersGrammar.Template template,
            Bounds bounds, Set<String> pools, Map<String, Set<String>> aliases) {
        ArrayList<ConnectorSpec> result = new ArrayList<>();
        for (Mc263TrialChambersGrammar.Connector connector : template.connectors()) {
            require(connector.ordinal() == result.size(),
                    "Trial Chambers connector ordinal drift: " + template.key());
            require(bounds.contains(connector.position().x(), connector.position().y(),
                            connector.position().z()),
                    "Trial Chambers connector outside template bounds: " + template.key());
            require(connector.pool().equals(EMPTY_POOL) || pools.contains(connector.pool())
                            || aliases.containsKey(connector.pool()),
                    "unknown Trial Chambers connector pool: " + connector.pool());
            result.add(new ConnectorSpec(connector.position().x(), connector.position().y(),
                    connector.position().z(), Direction.valueOf(connector.front().name()),
                    Direction.valueOf(connector.top().name()), Joint.valueOf(connector.joint().name()),
                    connector.name(), connector.target(), connector.pool(),
                    connector.placementPriority(), connector.selectionPriority()));
        }
        return List.copyOf(result);
    }

    private static Box aggregate(List<PiecePlacement> pieces) {
        Bounds first = pieces.getFirst().bounds();
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
        return HexFormat.of().formatHex(sha256Digest().digest(bytes));
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
        int minBuildY();
        int maxBuildY();
    }

    public record ProjectionQuery(int ordinal, String heightmap, int x, int z, int result) {
        public ProjectionQuery { Objects.requireNonNull(heightmap); }
    }

    public record Vec3i(int x, int y, int z) { }

    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Box {
            require(minX <= maxX && minY <= maxY && minZ <= maxZ,
                    "inverted Trial Chambers box");
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

    public record GenerationRng(long state48, int worldgenCount, int operationCount,
            String operationTranscriptSha256, List<Long> continuationNextLongI64) {
        public GenerationRng {
            require(state48 >= 0 && state48 <= LEGACY_MASK,
                    "Trial Chambers final RNG state outside Legacy48 range");
            require(worldgenCount >= 0 && operationCount == worldgenCount + 2,
                    "Trial Chambers RNG receipt count drift");
            require(operationTranscriptSha256 != null
                            && operationTranscriptSha256.matches("[0-9a-f]{64}"),
                    "Trial Chambers RNG transcript digest syntax drift");
            continuationNextLongI64 = List.copyOf(continuationNextLongI64);
            require(continuationNextLongI64.size() == 8,
                    "Trial Chambers RNG continuation cardinality drift");
        }
        public String state48Hex() { return String.format("%012x", state48); }
    }

    public static final class BinaryNbt {
        private final byte[] bytes;
        private final String sha256;
        private BinaryNbt(byte[] bytes) {
            this.bytes = bytes.clone();
            this.sha256 = Mc263TrialChambersProducer.sha256(this.bytes);
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
            List<ProjectionQuery> projectionQueries, GenerationRng generationRng, Carrier carrier) {
        public Start {
            Objects.requireNonNull(aggregateBoundingBox); Objects.requireNonNull(stubPosition);
            Objects.requireNonNull(executionPlan); acceptedEdges = List.copyOf(acceptedEdges);
            projectionQueries = List.copyOf(projectionQueries);
            Objects.requireNonNull(generationRng); Objects.requireNonNull(carrier);
        }
    }

    private record PieceIdentity(String template, String processor) {
        private PieceIdentity { Objects.requireNonNull(template); Objects.requireNonNull(processor); }
    }

    private record PieceClosure(Set<PieceIdentity> identities,
            Map<PieceIdentity, List<String>> processorOrders) {
        private PieceClosure {
            identities = Set.copyOf(identities);
            processorOrders = Map.copyOf(processorOrders);
        }
    }

    private interface IoWriter {
        void write(DataOutputStream out) throws IOException;
    }

    private static final class NoProjectionHeightResolver implements HeightResolver {
        private final int minBuildY;
        private final int maxBuildY;

        private NoProjectionHeightResolver(int minBuildY, int maxBuildY) {
            this.minBuildY = minBuildY; this.maxBuildY = maxBuildY;
        }

        @Override
        public int firstFreeY(int x, int z) {
            throw new IllegalArgumentException(
                    "unexpected Trial Chambers terrain projection query: " + x + "," + z);
        }

        @Override public int minBuildY() { return minBuildY; }
        @Override public int maxBuildY() { return maxBuildY; }
    }
}
