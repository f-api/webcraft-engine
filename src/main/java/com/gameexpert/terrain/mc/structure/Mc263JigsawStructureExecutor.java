package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.LegacyRand;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Bounds;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ConnectorSpec;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Direction;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ElementSpec;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ElementType;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Grammar;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.PoolSpec;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Projection;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.RotatedConnector;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ValidatedGrammar;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Vec3;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureCatalog.HeightMode;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureCatalog.StructureSpec;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * Dormant execution boundary for procedural descriptors of the pinned 26.3 jigsaw structures.
 * The complete plan is validated before a sink is allowed to mutate world state.
 */
public final class Mc263JigsawStructureExecutor {
    private static final String EMPTY_POOL = "minecraft:empty";
    private static final int PINNED_MAX_ELEMENT_WEIGHT = 150;
    private static final int CONTINUATION_WORDS = 8;
    private static final Map<String, List<String>> TRIAL_ALIAS_TARGETS = Map.of(
            "minecraft:trial_chambers/spawner/contents/ranged", List.of(
                    "minecraft:trial_chambers/spawner/ranged/skeleton",
                    "minecraft:trial_chambers/spawner/ranged/stray",
                    "minecraft:trial_chambers/spawner/ranged/poison_skeleton"),
            "minecraft:trial_chambers/spawner/contents/slow_ranged", List.of(
                    "minecraft:trial_chambers/spawner/slow_ranged/skeleton",
                    "minecraft:trial_chambers/spawner/slow_ranged/stray",
                    "minecraft:trial_chambers/spawner/slow_ranged/poison_skeleton"),
            "minecraft:trial_chambers/spawner/contents/melee", List.of(
                    "minecraft:trial_chambers/spawner/melee/zombie",
                    "minecraft:trial_chambers/spawner/melee/husk",
                    "minecraft:trial_chambers/spawner/melee/spider"),
            "minecraft:trial_chambers/spawner/contents/small_melee", List.of(
                    "minecraft:trial_chambers/spawner/small_melee/slime",
                    "minecraft:trial_chambers/spawner/small_melee/cave_spider",
                    "minecraft:trial_chambers/spawner/small_melee/silverfish",
                    "minecraft:trial_chambers/spawner/small_melee/baby_zombie"));

    private Mc263JigsawStructureExecutor() {}

    public static ExecutionPlan plan(long worldSeed, int chunkX, int chunkZ, Grammar grammar,
            HeightResolver heights, ExecutionLimits limits) {
        return planWithTrace(worldSeed, chunkX, chunkZ, grammar, heights, limits)
                .executionPlan();
    }

    /**
     * Plans once and returns immutable provenance captured by that exact pass. No selection RNG is
     * replayed to derive the trace, and all grammar/capability preflight precedes the observed
     * generation stream.
     */
    public static TracedPlan planWithTrace(long worldSeed, int chunkX, int chunkZ, Grammar grammar,
            HeightResolver heights, ExecutionLimits limits) {
        StagedRequest request = preflight(worldSeed, chunkX, chunkZ, grammar, heights, limits);
        LegacyRand random = new LegacyRand(0L);
        RootStage root = initializeRoot(request, random);
        return root.continuation().resume(request, random);
    }

    /**
     * Validates one staged planner request without querying terrain or mutating the generation RNG.
     * The returned token binds a later root stage and continuation to this exact request.
     */
    public static StagedRequest preflight(long worldSeed, int chunkX, int chunkZ, Grammar grammar,
            HeightResolver heights, ExecutionLimits limits) {
        if (heights == null) throw new IllegalArgumentException("height resolver is required");
        if (limits == null) throw new IllegalArgumentException("execution limits are required");
        Math.multiplyExact(chunkX, 16);
        Math.multiplyExact(chunkZ, 16);
        PreparedGrammar prepared = prepareGrammar(worldSeed, chunkX, chunkZ, grammar, limits);
        return new StagedRequest(worldSeed, chunkX, chunkZ, prepared, heights, limits);
    }

    /**
     * Initializes the caller-owned legacy stream and executes only root selection, named connector
     * selection and start projection. Child expansion remains dormant behind the continuation.
     */
    public static RootStage initializeRoot(StagedRequest request, LegacyRand random) {
        if (request == null) throw new IllegalArgumentException("staged request is required");
        if (random == null) throw new IllegalArgumentException("caller random is required");
        RngTrace trace = new RngTrace();
        PlannerRandom plannerRandom = initializeStructureRandom(request.worldSeed, request.chunkX,
                request.chunkZ, random, trace);
        Runtime runtime = new Runtime(request.chunkX, request.chunkZ,
                request.prepared.grammar(), request.heights, request.limits, plannerRandom,
                request.prepared.aliasSourcePools());
        ExecutionPlan plan = runtime.planRoot();
        TracedPlan root = new TracedPlan(plan, runtime.acceptedEdges(), trace.receipt(random));
        return new RootStage(root, new StagedContinuation(request, runtime, random, trace, root));
    }

    public static ExecutionPlan plan(long worldSeed, int chunkX, int chunkZ, byte[] grammar,
            HeightResolver heights, ExecutionLimits limits) {
        return plan(worldSeed, chunkX, chunkZ, Mc263JigsawStructureCodec.decode(grammar),
                heights, limits);
    }

    public static TracedPlan planWithTrace(long worldSeed, int chunkX, int chunkZ, byte[] grammar,
            HeightResolver heights, ExecutionLimits limits) {
        return planWithTrace(worldSeed, chunkX, chunkZ, Mc263JigsawStructureCodec.decode(grammar),
                heights, limits);
    }

    public static ExecutionPlan execute(long worldSeed, int chunkX, int chunkZ, Grammar grammar,
            HeightResolver heights, ExecutionLimits limits, PieceSink sink) {
        if (sink == null) throw new IllegalArgumentException("piece sink is required");
        ExecutionPlan plan = plan(worldSeed, chunkX, chunkZ, grammar, heights, limits);
        for (PiecePlacement piece : plan.pieces()) {
            if (!sink.supports(piece)) {
                throw new IllegalArgumentException("unsupported procedural jigsaw element: "
                        + piece.elementKey());
            }
        }
        for (PiecePlacement piece : plan.pieces()) sink.place(piece);
        return plan;
    }

    public static ExecutionPlan execute(long worldSeed, int chunkX, int chunkZ, byte[] grammar,
            HeightResolver heights, ExecutionLimits limits, PieceSink sink) {
        if (sink == null) throw new IllegalArgumentException("piece sink is required");
        Grammar decoded = Mc263JigsawStructureCodec.decode(grammar);
        return execute(worldSeed, chunkX, chunkZ, decoded, heights, limits, sink);
    }

    private static final class Runtime {
        private final int chunkX;
        private final int chunkZ;
        private final StructureSpec structure;
        private final Map<String, PoolSpec> pools;
        private final HeightResolver heights;
        private final ExecutionLimits limits;
        private final PlannerRandom random;
        private final Map<ConnectorSpec, String> aliasSourcePools;
        private final List<MutablePiece> pieces = new ArrayList<>();
        private final List<AcceptedEdge> acceptedEdges = new ArrayList<>();
        private final PriorityStates states = new PriorityStates();
        private long attempts;
        private boolean rootInitialized;
        private boolean resumed;
        private ExecutionPlan rootSnapshot;
        private MutablePiece rootPiece;
        private int rootCenterX;
        private int rootCenterY;
        private int rootCenterZ;

        private Runtime(int chunkX, int chunkZ, ValidatedGrammar grammar, HeightResolver heights,
                ExecutionLimits limits, PlannerRandom random,
                Map<ConnectorSpec, String> aliasSourcePools) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.structure = grammar.structure();
            this.heights = heights;
            this.limits = limits;
            this.random = random;
            this.aliasSourcePools = aliasSourcePools;
            this.pools = new HashMap<>();
            for (PoolSpec pool : grammar.pools()) pools.put(pool.key(), pool);
        }

        private ExecutionPlan planRoot() {
            if (rootInitialized) throw new IllegalStateException("jigsaw root already initialized");
            rootInitialized = true;
            int sampledY = structure.minStartHeight();
            if (structure.heightMode() == HeightMode.UNIFORM) {
                sampledY += random.nextInt(structure.maxStartHeight()
                        - structure.minStartHeight() + 1);
            }
            Rotation rotation = Rotation.values()[random.nextInt(Rotation.values().length)];
            ElementSpec start = randomElement(requirePool(structure.startPool()), random);
            if (start.type() == ElementType.EMPTY) {
                return rememberRoot(emptyPlan(sampledY, rotation), null);
            }

            PlacedConnector named = null;
            if (!structure.startJigsawName().isEmpty()) {
                List<PlacedConnector> namedCandidates = shuffledConnectors(start, 0, 0, 0,
                        rotation, random, aliasSourcePools);
                for (PlacedConnector connector : namedCandidates) {
                    if (connector.name().equals(structure.startJigsawName())) {
                        named = connector;
                        break;
                    }
                }
                if (named == null) return rememberRoot(emptyPlan(sampledY, rotation), null);
            }

            int requestedX = Math.multiplyExact(chunkX, 16);
            int requestedZ = Math.multiplyExact(chunkZ, 16);
            int originX = requestedX - (named == null ? 0 : named.x());
            int originY = sampledY - (named == null ? 0 : named.y());
            int originZ = requestedZ - (named == null ? 0 : named.z());
            Bounds startBounds = worldBounds(start.bounds(), originX, originY, originZ, rotation);
            int centerX = (startBounds.maxX() + startBounds.minX()) / 2;
            int centerZ = (startBounds.maxZ() + startBounds.minZ()) / 2;
            int bottomY = sampledY;
            if (!structure.projectStartToHeightmap().isEmpty()) {
                bottomY += heights.firstFreeY(centerX, centerZ);
            }
            int moveY = bottomY - (startBounds.minY() + 1);
            originY = Math.addExact(originY, moveY);
            startBounds = move(startBounds, 0, moveY, 0);
            if (!fitsWorld(startBounds)) return rememberRoot(emptyPlan(sampledY, rotation), null);

            int localAnchorY = named == null ? 0 : named.y();
            int centerY = Math.addExact(bottomY, localAnchorY);
            MutablePiece center = addPiece(start, 0, originX, originY, originZ, rotation,
                    startBounds, 1);
            return rememberRoot(freeze(true, centerX, centerY, centerZ), center);
        }

        private ExecutionPlan resumePlan() {
            if (!rootInitialized) throw new IllegalStateException("jigsaw root is not initialized");
            if (resumed) throw new IllegalStateException("jigsaw runtime already resumed");
            resumed = true;
            if (rootPiece == null || structure.depth() <= 0) return rootSnapshot;

            Bounds domain = new Bounds(
                    Math.subtractExact(rootCenterX, structure.maxHorizontalDistance()),
                    Math.max(Math.subtractExact(rootCenterY, structure.maxVerticalDistance()),
                            Math.addExact(heights.minBuildY(), structure.dimensionPadding())),
                    Math.subtractExact(rootCenterZ, structure.maxHorizontalDistance()),
                    Math.addExact(rootCenterX, structure.maxHorizontalDistance()),
                    Math.min(Math.addExact(rootCenterY, structure.maxVerticalDistance()),
                            Math.subtractExact(heights.maxBuildY() - 1,
                                    structure.dimensionPadding())),
                    Math.addExact(rootCenterZ, structure.maxHorizontalDistance()));
            CollisionSpace global = new CollisionSpace(domain);
            global.occupied.add(rootPiece.bounds);
            placeChildren(rootPiece, global, 0);
            while (!states.isEmpty()) {
                PieceState state = states.remove();
                placeChildren(state.piece, state.free, state.depth);
            }
            return freeze(true, rootCenterX, rootCenterY, rootCenterZ);
        }

        private ExecutionPlan rememberRoot(ExecutionPlan plan, MutablePiece piece) {
            rootSnapshot = plan;
            rootPiece = piece;
            rootCenterX = plan.centerX();
            rootCenterY = plan.centerY();
            rootCenterZ = plan.centerZ();
            return plan;
        }

        private void placeChildren(MutablePiece source, CollisionSpace contextFree, int depth) {
            List<PlacedConnector> connectors = shuffledConnectors(source.element,
                    source.originX, source.originY, source.originZ, source.rotation, random,
                    aliasSourcePools);
            CollisionSpace sourceFree = null;
            connectorLoop: for (PlacedConnector sourceConnector : connectors) {
                countAttempt();
                int targetX = Math.addExact(sourceConnector.x(), stepX(sourceConnector.front()));
                int targetY = Math.addExact(sourceConnector.y(), stepY(sourceConnector.front()));
                int targetZ = Math.addExact(sourceConnector.z(), stepZ(sourceConnector.front()));
                PoolSpec pool = pools.get(sourceConnector.pool());
                if (pool == null && !sourceConnector.pool().equals(EMPTY_POOL)) continue;
                if (pool == null) continue;
                PoolSpec fallback = pools.get(pool.fallback());
                if (fallback == null && !pool.fallback().equals(EMPTY_POOL)) continue;

                CollisionSpace childrenFree;
                if (contains(source.bounds, targetX, targetY, targetZ)) {
                    if (sourceFree == null) sourceFree = new CollisionSpace(source.bounds);
                    childrenFree = sourceFree;
                } else {
                    childrenFree = contextFree;
                }

                List<ElementSpec> candidates = new ArrayList<>();
                if (depth != structure.depth()) {
                    candidates.addAll(shuffledPool(pool, random));
                }
                if (fallback != null) candidates.addAll(shuffledPool(fallback, random));
                for (ElementSpec target : candidates) {
                    countAttempt();
                    if (target.type() == ElementType.EMPTY) break;
                    for (Rotation targetRotation : shuffledRotations(random)) {
                        List<PlacedConnector> targetConnectors = shuffledConnectors(target,
                                0, 0, 0, targetRotation, random, aliasSourcePools);
                        int expandTo = expansionHeight(target, targetRotation);
                        for (PlacedConnector targetConnector : targetConnectors) {
                            countAttempt();
                            if (!canAttach(sourceConnector, targetConnector)) continue;
                            int rawOriginX = Math.subtractExact(targetX, targetConnector.x());
                            int rawOriginY = Math.subtractExact(targetY, targetConnector.y());
                            int rawOriginZ = Math.subtractExact(targetZ, targetConnector.z());
                            Bounds rawBounds = worldBounds(target.bounds(), rawOriginX,
                                    rawOriginY, rawOriginZ, targetRotation);
                            int sourceLocalY = sourceConnector.y() - source.bounds.minY();
                            int targetLocalY = targetConnector.y();
                            int deltaY = sourceLocalY - targetLocalY
                                    + stepY(sourceConnector.front());
                            boolean sourceRigid = source.element.projection() == Projection.RIGID;
                            boolean targetRigid = target.projection() == Projection.RIGID;
                            int targetBoxY;
                            if (sourceRigid && targetRigid) {
                                targetBoxY = Math.addExact(source.bounds.minY(), deltaY);
                            } else {
                                targetBoxY = Math.subtractExact(
                                        heights.firstFreeY(sourceConnector.x(),
                                                sourceConnector.z()), targetLocalY);
                            }
                            int yOffset = Math.subtractExact(targetBoxY, rawBounds.minY());
                            Bounds targetBounds = move(rawBounds, 0, yOffset, 0);
                            int targetOriginY = Math.addExact(rawOriginY, yOffset);
                            if (expandTo > 0) {
                                int newSize = Math.max(expandTo + 1,
                                        targetBounds.maxY() - targetBounds.minY());
                                targetBounds = new Bounds(targetBounds.minX(),
                                        targetBounds.minY(), targetBounds.minZ(),
                                        targetBounds.maxX(),
                                        Math.addExact(targetBounds.minY(), newSize),
                                        targetBounds.maxZ());
                            }
                            if (!childrenFree.accept(targetBounds)) continue;

                            int sourceGround = source.groundLevelDelta;
                            int targetGround = targetRigid ? sourceGround - deltaY : 1;
                            MutablePiece child = addPiece(target, depth + 1, rawOriginX,
                                    targetOriginY, rawOriginZ, targetRotation, targetBounds,
                                    targetGround);
                            int junctionY;
                            if (sourceRigid) {
                                junctionY = source.bounds.minY() + sourceLocalY;
                            } else if (targetRigid) {
                                junctionY = targetBoxY + targetLocalY;
                            } else {
                                junctionY = heights.firstFreeY(sourceConnector.x(),
                                        sourceConnector.z()) + deltaY / 2;
                            }
                            source.junctions.add(new Junction(targetX,
                                    junctionY - sourceLocalY + sourceGround, targetZ, deltaY,
                                    target.projection()));
                            child.junctions.add(new Junction(sourceConnector.x(),
                                    junctionY - targetLocalY + targetGround,
                                    sourceConnector.z(), -deltaY, source.element.projection()));
                            acceptedEdges.add(new AcceptedEdge(source.ordinal,
                                    sourceConnector.freeze(), child.ordinal,
                                    targetConnector.freeze(), sourceConnector.sourcePool(),
                                    sourceConnector.resolvedAliasTarget()));
                            if (depth + 1 <= structure.depth()) {
                                states.add(new PieceState(child, childrenFree, depth + 1),
                                        sourceConnector.placementPriority());
                            }
                            continue connectorLoop;
                        }
                    }
                }
            }
        }

        private int expansionHeight(ElementSpec target, Rotation rotation) {
            if (!structure.expansionHack()) return 0;
            Bounds targetBounds = rotatedBounds(target.bounds(), rotation);
            if (spanY(targetBounds) > 16) return 0;
            int maximum = 0;
            List<PlacedConnector> connectors = placedConnectors(target, 0, 0, 0, rotation,
                    aliasSourcePools);
            for (PlacedConnector connector : connectors) {
                int adjacentX = connector.x() + stepX(connector.front());
                int adjacentY = connector.y() + stepY(connector.front());
                int adjacentZ = connector.z() + stepZ(connector.front());
                if (!contains(targetBounds, adjacentX, adjacentY, adjacentZ)) continue;
                PoolSpec child = pools.get(connector.pool());
                if (child == null) continue;
                maximum = Math.max(maximum, maxPoolHeight(child));
                PoolSpec fallback = pools.get(child.fallback());
                if (fallback != null) maximum = Math.max(maximum, maxPoolHeight(fallback));
            }
            return maximum;
        }

        private int maxPoolHeight(PoolSpec pool) {
            int maximum = 0;
            for (ElementSpec element : pool.elements()) {
                if (element.type() != ElementType.EMPTY) {
                    maximum = Math.max(maximum, spanY(element.bounds()));
                }
            }
            return maximum;
        }

        private MutablePiece addPiece(ElementSpec element, int depth, int x, int y, int z,
                Rotation rotation, Bounds bounds, int groundLevelDelta) {
            if (pieces.size() >= limits.maxPieces()) {
                throw new IllegalArgumentException("jigsaw piece limit exceeded: "
                        + limits.maxPieces());
            }
            MutablePiece piece = new MutablePiece(pieces.size(), element, depth, x, y, z,
                    rotation, bounds, groundLevelDelta);
            pieces.add(piece);
            return piece;
        }

        private static void preflightElement(ElementSpec element) {
            if (element.weight() > PINNED_MAX_ELEMENT_WEIGHT) {
                throw new IllegalArgumentException("element weight exceeds pinned JSON bound: "
                        + element.weight());
            }
            if (element.type() == ElementType.LIST && element.components().isEmpty()) {
                throw new IllegalArgumentException("unsupported empty list element");
            }
            if (element.type() == ElementType.FEATURE && (element.bounds().minX() != 0
                    || element.bounds().minY() != 0 || element.bounds().minZ() != 0
                    || element.bounds().maxX() != 0 || element.bounds().maxY() != 0
                    || element.bounds().maxZ() != 0 || !element.processor().isEmpty())) {
                throw new IllegalArgumentException("unsupported feature element geometry");
            }
        }

        private PoolSpec requirePool(String key) {
            PoolSpec pool = pools.get(key);
            if (pool == null) throw new IllegalArgumentException("missing pool: " + key);
            return pool;
        }

        private boolean fitsWorld(Bounds bounds) {
            return bounds.minY() >= heights.minBuildY() + structure.dimensionPadding()
                    && bounds.maxY() <= heights.maxBuildY() - 1
                            - structure.dimensionPadding();
        }

        private void countAttempt() {
            attempts++;
            if (attempts > limits.maxAttempts()) {
                throw new IllegalArgumentException("jigsaw attempt limit exceeded: "
                        + limits.maxAttempts());
            }
        }

        private ExecutionPlan emptyPlan(int y, Rotation rotation) {
            return new ExecutionPlan(structure.key(), chunkX, chunkZ,
                    Math.multiplyExact(chunkX, 16), y, Math.multiplyExact(chunkZ, 16),
                    rotation, false, List.of());
        }

        private ExecutionPlan freeze(boolean present, int centerX, int centerY, int centerZ) {
            List<PiecePlacement> frozen = new ArrayList<>(pieces.size());
            for (MutablePiece piece : pieces) frozen.add(piece.freeze());
            return new ExecutionPlan(structure.key(), chunkX, chunkZ, centerX, centerY,
                    centerZ, pieces.get(0).rotation, present, List.copyOf(frozen));
        }

        private List<AcceptedEdge> acceptedEdges() {
            return List.copyOf(acceptedEdges);
        }

        private List<ElementSpec> shuffledPool(PoolSpec pool, PlannerRandom random) {
            int size = 0;
            for (ElementSpec element : pool.elements()) {
                size = Math.addExact(size, element.weight());
            }
            if (size > limits.maxExpandedPoolEntries()) {
                throw new IllegalArgumentException("expanded pool limit exceeded: " + size);
            }
            List<ElementSpec> expanded = new ArrayList<>(size);
            for (ElementSpec element : pool.elements()) {
                for (int index = 0; index < element.weight(); index++) expanded.add(element);
            }
            shuffle(expanded, random);
            return expanded;
        }
    }

    private static PreparedGrammar prepareGrammar(long worldSeed, int chunkX, int chunkZ,
            Grammar grammar, ExecutionLimits limits) {
        Grammar validationGrammar = aliasValidationGrammar(grammar);
        ValidatedGrammar preflight = Mc263JigsawStructureBoundary.validate(validationGrammar);
        preflightExecutionGrammar(preflight, limits);
        if (preflight.structure().aliasMode()
                != Mc263JigsawStructureCatalog.AliasMode.TRIAL_CHAMBERS_PINNED) {
            return new PreparedGrammar(preflight, Map.of());
        }
        ResolvedAliases resolved = resolvePinnedAliases(worldSeed, chunkX, chunkZ, grammar);
        ValidatedGrammar validated = Mc263JigsawStructureBoundary.validate(resolved.grammar());
        return new PreparedGrammar(validated, resolved.aliasSourcePools());
    }

    private static Grammar aliasValidationGrammar(Grammar grammar) {
        if (grammar == null) return null;
        StructureSpec structure = Mc263JigsawStructureCatalog.require(grammar.structureKey());
        if (structure.aliasMode()
                != Mc263JigsawStructureCatalog.AliasMode.TRIAL_CHAMBERS_PINNED) {
            return grammar;
        }
        Set<String> poolKeys = new HashSet<>();
        for (PoolSpec pool : grammar.pools()) poolKeys.add(pool.key());
        List<PoolSpec> pools = new ArrayList<>(grammar.pools().size());
        for (PoolSpec pool : grammar.pools()) {
            List<ElementSpec> elements = new ArrayList<>(pool.elements().size());
            for (ElementSpec element : pool.elements()) {
                List<ConnectorSpec> connectors = new ArrayList<>(element.connectors().size());
                for (ConnectorSpec connector : element.connectors()) {
                    String sourcePool = connector.pool();
                    List<String> targets = sourcePool == null
                            ? null : TRIAL_ALIAS_TARGETS.get(sourcePool);
                    String validationPool = sourcePool;
                    if (targets != null) {
                        for (String target : targets) {
                            if (!poolKeys.contains(target)) {
                                throw new IllegalArgumentException(
                                        "missing pinned alias target pool: " + target);
                            }
                        }
                        validationPool = targets.get(0);
                    }
                    connectors.add(new ConnectorSpec(connector.x(), connector.y(), connector.z(),
                            connector.front(), connector.top(), connector.joint(),
                            connector.name(), connector.target(), validationPool,
                            connector.placementPriority(), connector.selectionPriority()));
                }
                elements.add(new ElementSpec(element.type(), element.key(), element.weight(),
                        element.projection(), element.processor(), element.bounds(), connectors,
                        element.components()));
            }
            pools.add(new PoolSpec(pool.key(), pool.fallback(), elements));
        }
        return new Grammar(grammar.structureKey(), pools);
    }

    private static void preflightExecutionGrammar(ValidatedGrammar grammar,
            ExecutionLimits limits) {
        for (PoolSpec pool : grammar.pools()) {
            int expanded = 0;
            for (ElementSpec element : pool.elements()) {
                Runtime.preflightElement(element);
                expanded = Math.addExact(expanded, element.weight());
            }
            if (expanded > limits.maxExpandedPoolEntries()) {
                throw new IllegalArgumentException("expanded pool limit exceeded: " + expanded);
            }
        }
    }

    private static PlannerRandom initializeStructureRandom(long worldSeed, int chunkX,
            int chunkZ, LegacyRand random, RngTrace trace) {
        PlannerRandom planner = new PlannerRandom(random, trace);
        planner.setSeed(worldSeed);
        long xScale = planner.nextLong();
        long zScale = planner.nextLong();
        long mixed = (long) chunkX * xScale ^ (long) chunkZ * zScale ^ worldSeed;
        planner.setSeed(mixed);
        return planner;
    }

    private static LegacyRand structureRandom(long worldSeed, int chunkX, int chunkZ) {
        LegacyRand seed = new LegacyRand(worldSeed);
        long xScale = seed.nextLong();
        long zScale = seed.nextLong();
        long mixed = (long) chunkX * xScale ^ (long) chunkZ * zScale ^ worldSeed;
        return new LegacyRand(mixed);
    }

    private static ResolvedAliases resolvePinnedAliases(long worldSeed, int chunkX, int chunkZ,
            Grammar grammar) {
        if (grammar == null || !"minecraft:trial_chambers".equals(grammar.structureKey())) {
            return new ResolvedAliases(grammar, Map.of());
        }
        StructureSpec structure = Mc263JigsawStructureCatalog.require(grammar.structureKey());
        LegacyRand structureRandom = structureRandom(worldSeed, chunkX, chunkZ);
        int sampledY = structure.minStartHeight();
        if (structure.heightMode() == HeightMode.UNIFORM) {
            sampledY += structureRandom.nextInt(structure.maxStartHeight()
                    - structure.minStartHeight() + 1);
        }

        LegacyRand seedRandom = new LegacyRand(worldSeed);
        long positionalFactorySeed = seedRandom.nextLong();
        int blockX = Math.multiplyExact(chunkX, 16);
        int blockZ = Math.multiplyExact(chunkZ, 16);
        LegacyRand aliasRandom = new LegacyRand(positionalFactorySeed
                ^ positionalSeed(blockX, sampledY, blockZ));
        int ranged = aliasRandom.nextInt(3);
        int melee = aliasRandom.nextInt(3);
        int smallMelee = aliasRandom.nextInt(4);
        Map<String, String> aliases = Map.of(
                "minecraft:trial_chambers/spawner/contents/ranged",
                TRIAL_ALIAS_TARGETS.get(
                        "minecraft:trial_chambers/spawner/contents/ranged").get(ranged),
                "minecraft:trial_chambers/spawner/contents/slow_ranged",
                TRIAL_ALIAS_TARGETS.get(
                        "minecraft:trial_chambers/spawner/contents/slow_ranged").get(ranged),
                "minecraft:trial_chambers/spawner/contents/melee",
                TRIAL_ALIAS_TARGETS.get(
                        "minecraft:trial_chambers/spawner/contents/melee").get(melee),
                "minecraft:trial_chambers/spawner/contents/small_melee",
                TRIAL_ALIAS_TARGETS.get(
                        "minecraft:trial_chambers/spawner/contents/small_melee").get(smallMelee));

        Map<ConnectorSpec, String> aliasSourcePools = new HashMap<>();
        List<PoolSpec> pools = new ArrayList<>(grammar.pools().size());
        for (PoolSpec pool : grammar.pools()) {
            List<ElementSpec> elements = new ArrayList<>(pool.elements().size());
            for (ElementSpec element : pool.elements()) {
                List<ConnectorSpec> connectors = new ArrayList<>(element.connectors().size());
                for (ConnectorSpec connector : element.connectors()) {
                    String resolvedPool = aliases.getOrDefault(connector.pool(), connector.pool());
                    ConnectorSpec resolved = new ConnectorSpec(connector.x(), connector.y(),
                            connector.z(), connector.front(), connector.top(), connector.joint(),
                            connector.name(), connector.target(), resolvedPool,
                            connector.placementPriority(), connector.selectionPriority());
                    connectors.add(resolved);
                    if (!resolvedPool.equals(connector.pool())) {
                        aliasSourcePools.put(resolved, connector.pool());
                    }
                }
                elements.add(new ElementSpec(element.type(), element.key(), element.weight(),
                        element.projection(), element.processor(), element.bounds(), connectors,
                        element.components()));
            }
            pools.add(new PoolSpec(pool.key(), pool.fallback(), elements));
        }
        return new ResolvedAliases(new Grammar(grammar.structureKey(), pools),
                Map.copyOf(aliasSourcePools));
    }

    private static long positionalSeed(int x, int y, int z) {
        long seed = (long) (x * 3_129_871) ^ (long) z * 116_129_781L ^ (long) y;
        seed = seed * seed * 42_317_861L + seed * 11L;
        return seed >> 16;
    }

    private static ElementSpec randomElement(PoolSpec pool, PlannerRandom random) {
        int total = 0;
        for (ElementSpec element : pool.elements()) {
            total = Math.addExact(total, element.weight());
        }
        int selected = random.nextInt(total);
        for (ElementSpec element : pool.elements()) {
            selected -= element.weight();
            if (selected < 0) return element;
        }
        throw new IllegalStateException("weighted selection escaped pool");
    }

    private static List<Rotation> shuffledRotations(PlannerRandom random) {
        List<Rotation> rotations = new ArrayList<>(List.of(Rotation.values()));
        shuffle(rotations, random);
        return rotations;
    }

    private static List<PlacedConnector> shuffledConnectors(ElementSpec element, int originX,
            int originY, int originZ, Rotation rotation, PlannerRandom random,
            Map<ConnectorSpec, String> aliasSourcePools) {
        if (element.type() == ElementType.FEATURE) {
            return List.of(featureConnector(originX, originY, originZ));
        }
        List<IndexedConnector> connectors = indexedConnectors(element.connectors());
        shuffle(connectors, random);
        connectors.sort((left, right) -> Integer.compare(
                right.connector().selectionPriority(), left.connector().selectionPriority()));
        return placedConnectors(connectors, originX, originY, originZ, rotation,
                aliasSourcePools);
    }

    private static List<PlacedConnector> placedConnectors(ElementSpec element, int originX,
            int originY, int originZ, Rotation rotation,
            Map<ConnectorSpec, String> aliasSourcePools) {
        if (element.type() == ElementType.FEATURE) {
            return List.of(featureConnector(originX, originY, originZ));
        }
        return placedConnectors(indexedConnectors(element.connectors()), originX, originY,
                originZ, rotation, aliasSourcePools);
    }

    private static List<IndexedConnector> indexedConnectors(List<ConnectorSpec> connectors) {
        List<IndexedConnector> result = new ArrayList<>(connectors.size());
        for (int ordinal = 0; ordinal < connectors.size(); ordinal++) {
            result.add(new IndexedConnector(ordinal, connectors.get(ordinal)));
        }
        return result;
    }

    private static PlacedConnector featureConnector(int originX, int originY, int originZ) {
        return new PlacedConnector(0, originX, originY, originZ, Direction.DOWN, Direction.SOUTH,
                Mc263JigsawStructureBoundary.Joint.ROLLABLE, "", "minecraft:empty",
                EMPTY_POOL, EMPTY_POOL, 0, 0);
    }

    private static List<PlacedConnector> placedConnectors(List<IndexedConnector> connectors,
            int originX, int originY, int originZ, Rotation rotation,
            Map<ConnectorSpec, String> aliasSourcePools) {
        List<PlacedConnector> result = new ArrayList<>(connectors.size());
        for (IndexedConnector indexed : connectors) {
            ConnectorSpec connector = indexed.connector();
            Vec3 local = Mc263JigsawStructureBoundary.rotate(connector.x(), connector.y(),
                    connector.z(), rotation);
            String sourcePool = aliasSourcePools.getOrDefault(connector, connector.pool());
            result.add(new PlacedConnector(indexed.ordinal(), Math.addExact(originX, local.x()),
                    Math.addExact(originY, local.y()), Math.addExact(originZ, local.z()),
                    Mc263JigsawStructureBoundary.rotate(connector.front(), rotation),
                    Mc263JigsawStructureBoundary.rotate(connector.top(), rotation),
                    connector.joint(), connector.name(), connector.target(), sourcePool,
                    connector.pool(), connector.placementPriority(), connector.selectionPriority()));
        }
        return result;
    }

    private static boolean canAttach(PlacedConnector source, PlacedConnector target) {
        RotatedConnector sourceView = source.view();
        RotatedConnector targetView = target.view();
        return Mc263JigsawStructureBoundary.canAttach(sourceView, targetView);
    }

    private static <T> void shuffle(List<T> values, PlannerRandom random) {
        for (int size = values.size(); size > 1; size--) {
            int selected = random.nextInt(size);
            T displaced = values.set(size - 1, values.get(selected));
            values.set(selected, displaced);
        }
    }

    private static Bounds worldBounds(Bounds local, int x, int y, int z, Rotation rotation) {
        Bounds rotated = rotatedBounds(local, rotation);
        return move(rotated, x, y, z);
    }

    private static Bounds rotatedBounds(Bounds bounds, Rotation rotation) {
        Vec3 first = Mc263JigsawStructureBoundary.rotate(bounds.minX(), bounds.minY(),
                bounds.minZ(), rotation);
        int minX = first.x(), minY = first.y(), minZ = first.z();
        int maxX = first.x(), maxY = first.y(), maxZ = first.z();
        int[] xs = {bounds.minX(), bounds.maxX()};
        int[] ys = {bounds.minY(), bounds.maxY()};
        int[] zs = {bounds.minZ(), bounds.maxZ()};
        for (int x : xs) for (int y : ys) for (int z : zs) {
            Vec3 point = Mc263JigsawStructureBoundary.rotate(x, y, z, rotation);
            minX = Math.min(minX, point.x()); maxX = Math.max(maxX, point.x());
            minY = Math.min(minY, point.y()); maxY = Math.max(maxY, point.y());
            minZ = Math.min(minZ, point.z()); maxZ = Math.max(maxZ, point.z());
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Bounds move(Bounds bounds, int x, int y, int z) {
        return new Bounds(Math.addExact(bounds.minX(), x), Math.addExact(bounds.minY(), y),
                Math.addExact(bounds.minZ(), z), Math.addExact(bounds.maxX(), x),
                Math.addExact(bounds.maxY(), y), Math.addExact(bounds.maxZ(), z));
    }

    private static boolean contains(Bounds bounds, int x, int y, int z) {
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY()
                && z >= bounds.minZ() && z <= bounds.maxZ();
    }

    private static int spanY(Bounds bounds) {
        return Math.addExact(Math.subtractExact(bounds.maxY(), bounds.minY()), 1);
    }

    private static int stepX(Direction direction) {
        return direction == Direction.EAST ? 1 : direction == Direction.WEST ? -1 : 0;
    }

    private static int stepY(Direction direction) {
        return direction == Direction.UP ? 1 : direction == Direction.DOWN ? -1 : 0;
    }

    private static int stepZ(Direction direction) {
        return direction == Direction.SOUTH ? 1 : direction == Direction.NORTH ? -1 : 0;
    }

    public interface HeightResolver {
        int firstFreeY(int blockX, int blockZ);
        int minBuildY();
        int maxBuildY();
    }

    public interface PieceSink {
        boolean supports(PiecePlacement piece);
        void place(PiecePlacement piece);
    }

    /** Opaque immutable request produced only after complete staged-planner preflight. */
    public static final class StagedRequest {
        private final long worldSeed;
        private final int chunkX;
        private final int chunkZ;
        private final PreparedGrammar prepared;
        private final HeightResolver heights;
        private final ExecutionLimits limits;

        private StagedRequest(long worldSeed, int chunkX, int chunkZ, PreparedGrammar prepared,
                HeightResolver heights, ExecutionLimits limits) {
            this.worldSeed = worldSeed;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.prepared = prepared;
            this.heights = heights;
            this.limits = limits;
        }
    }

    /** Immutable root-stage data plus the only handle that may accept or reject that live pass. */
    public record RootStage(TracedPlan root, StagedContinuation continuation) {
        public RootStage {
            if (root == null || continuation == null) {
                throw new IllegalArgumentException("complete jigsaw root stage is required");
            }
        }
    }

    /** One-shot gate bound to the exact staged request, runtime and caller-owned legacy stream. */
    public static final class StagedContinuation {
        private final StagedRequest owner;
        private final Runtime runtime;
        private final LegacyRand random;
        private final RngTrace trace;
        private final TracedPlan root;
        private final long rootState48;
        private boolean consumed;

        private StagedContinuation(StagedRequest owner, Runtime runtime, LegacyRand random,
                RngTrace trace, TracedPlan root) {
            this.owner = owner;
            this.runtime = runtime;
            this.random = random;
            this.trace = trace;
            this.root = root;
            this.rootState48 = root.generationRng().state48();
        }

        /** Consumes the stage without child expansion and returns its exact rejection receipt. */
        public synchronized TracedPlan reject(StagedRequest request, LegacyRand callerRandom) {
            validateOwner(request, callerRandom);
            consumed = true;
            return root;
        }

        /** Resumes child expansion on the same runtime and same caller-owned random state. */
        public synchronized TracedPlan resume(StagedRequest request, LegacyRand callerRandom) {
            validateOwner(request, callerRandom);
            consumed = true;
            ExecutionPlan plan = runtime.resumePlan();
            return new TracedPlan(plan, runtime.acceptedEdges(), trace.receipt(random));
        }

        private void validateOwner(StagedRequest request, LegacyRand callerRandom) {
            if (consumed) throw new IllegalStateException("staged jigsaw continuation already used");
            if (request != owner) {
                throw new IllegalArgumentException("staged jigsaw request mismatch");
            }
            if (callerRandom != random) {
                throw new IllegalArgumentException("staged jigsaw random mismatch");
            }
            if (random.state48() != rootState48) {
                throw new IllegalArgumentException("staged jigsaw random state drift");
            }
        }
    }

    /** Immutable result of one exact planner pass plus its same-pass provenance. */
    public record TracedPlan(ExecutionPlan executionPlan, List<AcceptedEdge> acceptedEdges,
            PlannerRngReceipt generationRng) {
        public TracedPlan {
            if (executionPlan == null || acceptedEdges == null || generationRng == null) {
                throw new IllegalArgumentException("complete jigsaw trace is required");
            }
            acceptedEdges = List.copyOf(acceptedEdges);
        }
    }

    /** One accepted parent-to-child edge in exact child acceptance order. */
    public record AcceptedEdge(int parentPieceOrdinal, ConnectorIdentity sourceConnector,
            int childPieceOrdinal, ConnectorIdentity targetConnector, String selectedSourcePool,
            String resolvedAliasTarget) {
        public AcceptedEdge {
            if (sourceConnector == null || targetConnector == null || selectedSourcePool == null
                    || resolvedAliasTarget == null) {
                throw new IllegalArgumentException("complete accepted-edge provenance is required");
            }
        }
    }

    /** Identity and official priority facts of one connector before shuffle-order loss. */
    public record ConnectorIdentity(int ordinal, int x, int y, int z, Direction front,
            Direction top, Mc263JigsawStructureBoundary.Joint joint, String name, String target,
            String pool, int placementPriority, int selectionPriority) {}

    /** Exact final legacy planner state and a non-mutating eight-word continuation. */
    public record PlannerRngReceipt(long state48, int worldgenCount, List<RandomDraw> draws,
            List<Long> continuationNextLongI64) {
        public PlannerRngReceipt {
            if (state48 < 0 || state48 >= (1L << 48) || worldgenCount < 0
                    || draws == null || continuationNextLongI64 == null
                    || continuationNextLongI64.size() != CONTINUATION_WORDS) {
                throw new IllegalArgumentException("invalid jigsaw planner RNG receipt");
            }
            draws = List.copyOf(draws);
            continuationNextLongI64 = List.copyOf(continuationNextLongI64);
        }

        public String state48Hex() { return String.format("%012x", state48); }
    }

    /** One observed native LegacyRandomSource transition. */
    public record RandomDraw(int ordinal, String operation, Long seedArgument,
            Integer bitsArgument, Integer result, Long state48After) {}

    public static final class ExecutionLimits {
        private final int maxPieces;
        private final int maxExpandedPoolEntries;
        private final long maxAttempts;

        public ExecutionLimits(int maxPieces, int maxExpandedPoolEntries, long maxAttempts) {
            if (maxPieces <= 0 || maxExpandedPoolEntries <= 0 || maxAttempts <= 0) {
                throw new IllegalArgumentException("execution limits must be positive");
            }
            this.maxPieces = maxPieces;
            this.maxExpandedPoolEntries = maxExpandedPoolEntries;
            this.maxAttempts = maxAttempts;
        }

        public int maxPieces() { return maxPieces; }
        public int maxExpandedPoolEntries() { return maxExpandedPoolEntries; }
        public long maxAttempts() { return maxAttempts; }
    }

    public static final class ExecutionPlan {
        private final String structureKey;
        private final int chunkX, chunkZ, centerX, centerY, centerZ;
        private final Rotation rotation;
        private final boolean present;
        private final List<PiecePlacement> pieces;

        private ExecutionPlan(String structureKey, int chunkX, int chunkZ, int centerX,
                int centerY, int centerZ, Rotation rotation, boolean present,
                List<PiecePlacement> pieces) {
            this.structureKey = structureKey; this.chunkX = chunkX; this.chunkZ = chunkZ;
            this.centerX = centerX; this.centerY = centerY; this.centerZ = centerZ;
            this.rotation = rotation; this.present = present; this.pieces = pieces;
        }

        public String structureKey() { return structureKey; }
        public int chunkX() { return chunkX; } public int chunkZ() { return chunkZ; }
        public int centerX() { return centerX; } public int centerY() { return centerY; }
        public int centerZ() { return centerZ; } public Rotation rotation() { return rotation; }
        public boolean present() { return present; }
        public List<PiecePlacement> pieces() { return pieces; }

        public String fingerprint() {
            StringBuilder value = new StringBuilder(structureKey).append('|').append(centerX)
                    .append(',').append(centerY).append(',').append(centerZ).append('|')
                    .append(rotation).append('|').append(present);
            for (PiecePlacement piece : pieces) value.append('\n').append(piece.fingerprint());
            return value.toString();
        }
    }

    public static final class PiecePlacement {
        private final ElementType type;
        private final String elementKey;
        private final List<String> components;
        private final int depth, originX, originY, originZ, groundLevelDelta;
        private final Rotation rotation;
        private final Projection projection;
        private final String processor;
        private final Bounds bounds;
        private final List<Junction> junctions;

        private PiecePlacement(MutablePiece source) {
            this.type = source.element.type(); this.elementKey = source.element.key();
            this.components = source.element.components(); this.depth = source.depth;
            this.originX = source.originX; this.originY = source.originY;
            this.originZ = source.originZ; this.groundLevelDelta = source.groundLevelDelta;
            this.rotation = source.rotation; this.projection = source.element.projection();
            this.processor = source.element.processor(); this.bounds = source.bounds;
            this.junctions = List.copyOf(source.junctions);
        }

        public ElementType type() { return type; } public String elementKey() { return elementKey; }
        public List<String> components() { return components; } public int depth() { return depth; }
        public int originX() { return originX; } public int originY() { return originY; }
        public int originZ() { return originZ; } public Rotation rotation() { return rotation; }
        public Projection projection() { return projection; } public String processor() { return processor; }
        public Bounds bounds() { return bounds; } public int groundLevelDelta() { return groundLevelDelta; }
        public List<Junction> junctions() { return junctions; }

        private String fingerprint() {
            return depth + ":" + type + ":" + elementKey + ":" + originX + "," + originY
                    + "," + originZ + ":" + rotation + ":" + projection + ":" + processor
                    + ":" + bounds.minX() + "," + bounds.minY() + "," + bounds.minZ()
                    + "," + bounds.maxX() + "," + bounds.maxY() + "," + bounds.maxZ()
                    + ":" + components + ":" + junctions.stream()
                            .map(Junction::fingerprint).toList();
        }
    }

    public static final class Junction {
        private final int sourceX, sourceGroundY, sourceZ, deltaY;
        private final Projection destinationProjection;

        private Junction(int sourceX, int sourceGroundY, int sourceZ, int deltaY,
                Projection destinationProjection) {
            this.sourceX = sourceX; this.sourceGroundY = sourceGroundY;
            this.sourceZ = sourceZ; this.deltaY = deltaY;
            this.destinationProjection = destinationProjection;
        }

        public int sourceX() { return sourceX; }
        public int sourceGroundY() { return sourceGroundY; }
        public int sourceZ() { return sourceZ; }
        public int deltaY() { return deltaY; }
        public Projection destinationProjection() { return destinationProjection; }

        private String fingerprint() {
            return sourceX + "," + sourceGroundY + "," + sourceZ + "," + deltaY + ","
                    + destinationProjection;
        }
    }

    private record PreparedGrammar(ValidatedGrammar grammar,
            Map<ConnectorSpec, String> aliasSourcePools) {
        private PreparedGrammar { aliasSourcePools = Map.copyOf(aliasSourcePools); }
    }

    private record ResolvedAliases(Grammar grammar, Map<ConnectorSpec, String> aliasSourcePools) {
        private ResolvedAliases { aliasSourcePools = Map.copyOf(aliasSourcePools); }
    }

    private record IndexedConnector(int ordinal, ConnectorSpec connector) {}

    private static final class PlannerRandom {
        private static final long MULTIPLIER = 0x5DEECE66DL;
        private static final long ADDEND = 0xBL;
        private static final long MASK = (1L << 48) - 1L;

        private final LegacyRand random;
        private final RngTrace trace;

        private PlannerRandom(LegacyRand random, RngTrace trace) {
            this.random = random;
            this.trace = trace;
        }

        private void setSeed(long seed) {
            random.setSeed(seed);
            trace.onSetSeed(seed);
        }

        private int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
            long expectedState = random.state48();
            int expected;
            if ((bound & -bound) == bound) {
                expectedState = nextState(expectedState);
                int bits = (int) (expectedState >>> 17);
                trace.onNextBits(31, bits, expectedState);
                expected = (int) ((bound * (long) bits) >> 31);
            } else {
                int bits;
                int value;
                do {
                    expectedState = nextState(expectedState);
                    bits = (int) (expectedState >>> 17);
                    trace.onNextBits(31, bits, expectedState);
                    value = bits % bound;
                } while (bits - value + (bound - 1) < 0);
                expected = value;
            }
            int actual = random.nextInt(bound);
            requireExact(actual == expected && random.state48() == expectedState);
            return actual;
        }

        private long nextLong() {
            long expectedState = nextState(random.state48());
            int high = (int) (expectedState >>> 16);
            trace.onNextBits(32, high, expectedState);
            expectedState = nextState(expectedState);
            int low = (int) (expectedState >>> 16);
            trace.onNextBits(32, low, expectedState);
            long expected = ((long) high << 32) + low;
            long actual = random.nextLong();
            requireExact(actual == expected && random.state48() == expectedState);
            return actual;
        }

        private static long nextState(long state) {
            return (state * MULTIPLIER + ADDEND) & MASK;
        }

        private static void requireExact(boolean exact) {
            if (!exact) throw new IllegalStateException("legacy jigsaw random semantics drift");
        }
    }

    private static final class RngTrace {
        private final List<RandomDraw> draws = new ArrayList<>();
        private int worldgenCount;

        private void onSetSeed(long seed) {
            draws.add(new RandomDraw(draws.size(), "setSeed", seed, null, null, null));
        }

        private void onNextBits(int bits, int result, long state48After) {
            worldgenCount++;
            draws.add(new RandomDraw(draws.size(), "nextBits", null, bits, result,
                    state48After));
        }

        private PlannerRngReceipt receipt(LegacyRand random) {
            long[] continuation = random.continuationNextLong(CONTINUATION_WORDS);
            List<Long> tail = new ArrayList<>(continuation.length);
            for (long value : continuation) tail.add(value);
            return new PlannerRngReceipt(random.state48(), worldgenCount, draws, tail);
        }
    }

    private static final class MutablePiece {
        private final int ordinal;
        private final ElementSpec element;
        private final int depth, originX, originY, originZ, groundLevelDelta;
        private final Rotation rotation;
        private final Bounds bounds;
        private final List<Junction> junctions = new ArrayList<>();

        private MutablePiece(int ordinal, ElementSpec element, int depth, int originX,
                int originY, int originZ, Rotation rotation, Bounds bounds,
                int groundLevelDelta) {
            this.ordinal = ordinal; this.element = element; this.depth = depth;
            this.originX = originX; this.originY = originY; this.originZ = originZ;
            this.rotation = rotation; this.bounds = bounds;
            this.groundLevelDelta = groundLevelDelta;
        }

        private PiecePlacement freeze() { return new PiecePlacement(this); }
    }

    private static final class PlacedConnector {
        private final int ordinal, x, y, z;
        private final Direction front, top;
        private final Mc263JigsawStructureBoundary.Joint joint;
        private final String name, target, sourcePool, pool;
        private final int placementPriority, selectionPriority;

        private PlacedConnector(int ordinal, int x, int y, int z, Direction front, Direction top,
                Mc263JigsawStructureBoundary.Joint joint, String name, String target,
                String sourcePool, String pool, int placementPriority, int selectionPriority) {
            this.ordinal = ordinal; this.x = x; this.y = y; this.z = z; this.front = front;
            this.top = top; this.joint = joint; this.name = name; this.target = target;
            this.sourcePool = sourcePool; this.pool = pool;
            this.placementPriority = placementPriority;
            this.selectionPriority = selectionPriority;
        }

        private int x() { return x; } private int y() { return y; } private int z() { return z; }
        private Direction front() { return front; } private String name() { return name; }
        private String pool() { return pool; } private int placementPriority() { return placementPriority; }
        private String sourcePool() { return sourcePool; }
        private String resolvedAliasTarget() {
            return sourcePool.equals(pool) ? "" : pool;
        }
        private ConnectorIdentity freeze() {
            return new ConnectorIdentity(ordinal, x, y, z, front, top, joint, name, target,
                    sourcePool, placementPriority, selectionPriority);
        }
        private RotatedConnector view() {
            return new RotatedConnector(x, y, z, front, top, joint, name, target, pool,
                    placementPriority, selectionPriority);
        }
    }

    private static final class CollisionSpace {
        private final Bounds domain;
        private final List<Bounds> occupied = new ArrayList<>();

        private CollisionSpace(Bounds domain) { this.domain = domain; }

        private boolean accept(Bounds candidate) {
            if (!deflatedInside(candidate, domain)) return false;
            for (Bounds box : occupied) if (deflatedIntersects(candidate, box)) return false;
            occupied.add(candidate);
            return true;
        }

        private static boolean deflatedInside(Bounds inner, Bounds outer) {
            return inner.minX() + 0.25 >= outer.minX()
                    && inner.minY() + 0.25 >= outer.minY()
                    && inner.minZ() + 0.25 >= outer.minZ()
                    && inner.maxX() + 0.75 <= outer.maxX() + 1.0
                    && inner.maxY() + 0.75 <= outer.maxY() + 1.0
                    && inner.maxZ() + 0.75 <= outer.maxZ() + 1.0;
        }

        private static boolean deflatedIntersects(Bounds candidate, Bounds occupied) {
            return candidate.minX() + 0.25 < occupied.maxX() + 1.0
                    && candidate.maxX() + 0.75 > occupied.minX()
                    && candidate.minY() + 0.25 < occupied.maxY() + 1.0
                    && candidate.maxY() + 0.75 > occupied.minY()
                    && candidate.minZ() + 0.25 < occupied.maxZ() + 1.0
                    && candidate.maxZ() + 0.75 > occupied.minZ();
        }
    }

    private static final class PieceState {
        private final MutablePiece piece;
        private final CollisionSpace free;
        private final int depth;
        private PieceState(MutablePiece piece, CollisionSpace free, int depth) {
            this.piece = piece; this.free = free; this.depth = depth;
        }
    }

    private static final class PriorityStates {
        private final NavigableMap<Integer, ArrayDeque<PieceState>> queues =
                new TreeMap<>(Comparator.reverseOrder());
        private void add(PieceState state, int priority) {
            queues.computeIfAbsent(priority, ignored -> new ArrayDeque<>()).addLast(state);
        }
        private PieceState remove() {
            Map.Entry<Integer, ArrayDeque<PieceState>> entry = queues.firstEntry();
            PieceState result = entry.getValue().removeFirst();
            if (entry.getValue().isEmpty()) queues.remove(entry.getKey());
            return result;
        }
        private boolean isEmpty() { return queues.isEmpty(); }
    }
}
