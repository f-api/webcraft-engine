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
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Dormant execution boundary for procedural descriptors of the pinned 26.3 jigsaw structures.
 * The complete plan is validated before a sink is allowed to mutate world state.
 */
public final class Mc263TrailRuinsStartGenerator {
    private static final String EMPTY_POOL = "minecraft:empty";
    private static final int PINNED_MAX_ELEMENT_WEIGHT = 150;

    private Mc263TrailRuinsStartGenerator() {}

    public static ExecutionPlan plan(long worldSeed, int chunkX, int chunkZ, Grammar grammar,
            HeightResolver heights, ExecutionLimits limits) {
        if (heights == null) throw new IllegalArgumentException("height resolver is required");
        if (limits == null) throw new IllegalArgumentException("execution limits are required");
        Grammar resolved = resolvePinnedAliases(worldSeed, chunkX, chunkZ, grammar);
        ValidatedGrammar validated = Mc263JigsawStructureBoundary.validate(resolved);
        Runtime runtime = new Runtime(worldSeed, chunkX, chunkZ, validated, heights, limits);
        return runtime.plan();
    }

    public static ExecutionPlan plan(long worldSeed, int chunkX, int chunkZ, byte[] grammar,
            HeightResolver heights, ExecutionLimits limits) {
        return plan(worldSeed, chunkX, chunkZ, Mc263JigsawStructureCodec.decode(grammar),
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
        private final LegacyRand random;
        private final List<MutablePiece> pieces = new ArrayList<>();
        private final PriorityStates states = new PriorityStates();
        private long attempts;

        private Runtime(long worldSeed, int chunkX, int chunkZ, ValidatedGrammar grammar,
                HeightResolver heights, ExecutionLimits limits) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.structure = grammar.structure();
            this.heights = heights;
            this.limits = limits;
            this.pools = new HashMap<>();
            for (PoolSpec pool : grammar.pools()) {
                pools.put(pool.key(), pool);
                for (ElementSpec element : pool.elements()) preflightElement(element);
            }
            this.random = structureRandom(worldSeed, chunkX, chunkZ);
        }

        private ExecutionPlan plan() {
            int sampledY = structure.minStartHeight();
            if (structure.heightMode() == HeightMode.UNIFORM) {
                sampledY += random.nextInt(structure.maxStartHeight()
                        - structure.minStartHeight() + 1);
            }
            Rotation rotation = Rotation.values()[random.nextInt(Rotation.values().length)];
            ElementSpec start = randomElement(requirePool(structure.startPool()), random);
            if (start.type() == ElementType.EMPTY) return emptyPlan(sampledY, rotation);

            PlacedConnector named = null;
            if (!structure.startJigsawName().isEmpty()) {
                List<PlacedConnector> namedCandidates = shuffledConnectors(start, 0, 0, 0,
                        rotation, random);
                for (PlacedConnector connector : namedCandidates) {
                    if (connector.name().equals(structure.startJigsawName())) {
                        named = connector;
                        break;
                    }
                }
                if (named == null) return emptyPlan(sampledY, rotation);
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
            if (!fitsWorld(startBounds)) return emptyPlan(sampledY, rotation);

            int localAnchorY = named == null ? 0 : named.y();
            int centerY = Math.addExact(bottomY, localAnchorY);
            MutablePiece center = addPiece(start, 0, originX, originY, originZ, rotation,
                    startBounds, 1);
            if (structure.depth() <= 0) return freeze(true, centerX, centerY, centerZ);

            Bounds domain = new Bounds(
                    Math.subtractExact(centerX, structure.maxHorizontalDistance()),
                    Math.max(Math.subtractExact(centerY, structure.maxVerticalDistance()),
                            Math.addExact(heights.minBuildY(), structure.dimensionPadding())),
                    Math.subtractExact(centerZ, structure.maxHorizontalDistance()),
                    Math.addExact(centerX, structure.maxHorizontalDistance()),
                    Math.min(Math.addExact(centerY, structure.maxVerticalDistance()),
                            Math.subtractExact(heights.maxBuildY() - 1,
                                    structure.dimensionPadding())),
                    Math.addExact(centerZ, structure.maxHorizontalDistance()));
            CollisionSpace global = new CollisionSpace(domain);
            global.occupied.add(startBounds);
            placeChildren(center, global, 0);
            while (!states.isEmpty()) {
                PieceState state = states.remove();
                placeChildren(state.piece, state.free, state.depth);
            }
            return freeze(true, centerX, centerY, centerZ);
        }

        private void placeChildren(MutablePiece source, CollisionSpace contextFree, int depth) {
            List<PlacedConnector> connectors = shuffledConnectors(source.element,
                    source.originX, source.originY, source.originZ, source.rotation, random);
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
                                0, 0, 0, targetRotation, random);
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
            List<PlacedConnector> connectors = placedConnectors(target, 0, 0, 0, rotation);
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
            MutablePiece piece = new MutablePiece(element, depth, x, y, z, rotation, bounds,
                    groundLevelDelta);
            pieces.add(piece);
            return piece;
        }

        private void preflightElement(ElementSpec element) {
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

        private List<ElementSpec> shuffledPool(PoolSpec pool, LegacyRand random) {
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

    private static LegacyRand structureRandom(long worldSeed, int chunkX, int chunkZ) {
        LegacyRand seed = new LegacyRand(worldSeed);
        long xScale = seed.nextLong();
        long zScale = seed.nextLong();
        return new LegacyRand((long) chunkX * xScale ^ (long) chunkZ * zScale ^ worldSeed);
    }

    private static Grammar resolvePinnedAliases(long worldSeed, int chunkX, int chunkZ,
            Grammar grammar) {
        if (grammar == null || !"minecraft:trial_chambers".equals(grammar.structureKey())) {
            return grammar;
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
        String[] rangedKinds = {"skeleton", "stray", "poison_skeleton"};
        String[] meleeKinds = {"zombie", "husk", "spider"};
        String[] smallKinds = {"slime", "cave_spider", "silverfish", "baby_zombie"};
        Map<String, String> aliases = Map.of(
                "minecraft:trial_chambers/spawner/contents/ranged",
                "minecraft:trial_chambers/spawner/ranged/" + rangedKinds[ranged],
                "minecraft:trial_chambers/spawner/contents/slow_ranged",
                "minecraft:trial_chambers/spawner/slow_ranged/" + rangedKinds[ranged],
                "minecraft:trial_chambers/spawner/contents/melee",
                "minecraft:trial_chambers/spawner/melee/" + meleeKinds[melee],
                "minecraft:trial_chambers/spawner/contents/small_melee",
                "minecraft:trial_chambers/spawner/small_melee/" + smallKinds[smallMelee]);

        List<PoolSpec> pools = new ArrayList<>(grammar.pools().size());
        for (PoolSpec pool : grammar.pools()) {
            List<ElementSpec> elements = new ArrayList<>(pool.elements().size());
            for (ElementSpec element : pool.elements()) {
                List<ConnectorSpec> connectors = new ArrayList<>(element.connectors().size());
                for (ConnectorSpec connector : element.connectors()) {
                    connectors.add(new ConnectorSpec(connector.x(), connector.y(), connector.z(),
                            connector.front(), connector.top(), connector.joint(),
                            connector.name(), connector.target(),
                            aliases.getOrDefault(connector.pool(), connector.pool()),
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

    private static long positionalSeed(int x, int y, int z) {
        long seed = (long) (x * 3_129_871) ^ (long) z * 116_129_781L ^ (long) y;
        seed = seed * seed * 42_317_861L + seed * 11L;
        return seed >> 16;
    }

    private static ElementSpec randomElement(PoolSpec pool, LegacyRand random) {
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

    private static List<Rotation> shuffledRotations(LegacyRand random) {
        List<Rotation> rotations = new ArrayList<>(List.of(Rotation.values()));
        shuffle(rotations, random);
        return rotations;
    }

    private static List<PlacedConnector> shuffledConnectors(ElementSpec element, int originX,
            int originY, int originZ, Rotation rotation, LegacyRand random) {
        if (element.type() == ElementType.FEATURE) {
            return List.of(featureConnector(originX, originY, originZ));
        }
        List<ConnectorSpec> connectors = new ArrayList<>(element.connectors());
        shuffle(connectors, random);
        connectors.sort((left, right) -> Integer.compare(
                right.selectionPriority(), left.selectionPriority()));
        return placedConnectors(connectors, originX, originY, originZ, rotation);
    }

    private static List<PlacedConnector> placedConnectors(ElementSpec element, int originX,
            int originY, int originZ, Rotation rotation) {
        if (element.type() == ElementType.FEATURE) {
            return List.of(featureConnector(originX, originY, originZ));
        }
        return placedConnectors(element.connectors(), originX, originY, originZ, rotation);
    }

    private static PlacedConnector featureConnector(int originX, int originY, int originZ) {
        return new PlacedConnector(originX, originY, originZ, Direction.DOWN, Direction.SOUTH,
                Mc263JigsawStructureBoundary.Joint.ROLLABLE, "", "minecraft:empty",
                EMPTY_POOL, 0, 0);
    }

    private static List<PlacedConnector> placedConnectors(List<ConnectorSpec> connectors,
            int originX, int originY, int originZ, Rotation rotation) {
        List<PlacedConnector> result = new ArrayList<>(connectors.size());
        for (ConnectorSpec connector : connectors) {
            Vec3 local = Mc263JigsawStructureBoundary.rotate(connector.x(), connector.y(),
                    connector.z(), rotation);
            result.add(new PlacedConnector(Math.addExact(originX, local.x()),
                    Math.addExact(originY, local.y()), Math.addExact(originZ, local.z()),
                    Mc263JigsawStructureBoundary.rotate(connector.front(), rotation),
                    Mc263JigsawStructureBoundary.rotate(connector.top(), rotation),
                    connector.joint(), connector.name(), connector.target(), connector.pool(),
                    connector.placementPriority(), connector.selectionPriority()));
        }
        return result;
    }

    private static boolean canAttach(PlacedConnector source, PlacedConnector target) {
        RotatedConnector sourceView = source.view();
        RotatedConnector targetView = target.view();
        return Mc263JigsawStructureBoundary.canAttach(sourceView, targetView);
    }

    private static <T> void shuffle(List<T> values, LegacyRand random) {
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

    private static final class MutablePiece {
        private final ElementSpec element;
        private final int depth, originX, originY, originZ, groundLevelDelta;
        private final Rotation rotation;
        private final Bounds bounds;
        private final List<Junction> junctions = new ArrayList<>();

        private MutablePiece(ElementSpec element, int depth, int originX, int originY,
                int originZ, Rotation rotation, Bounds bounds, int groundLevelDelta) {
            this.element = element; this.depth = depth; this.originX = originX;
            this.originY = originY; this.originZ = originZ; this.rotation = rotation;
            this.bounds = bounds; this.groundLevelDelta = groundLevelDelta;
        }

        private PiecePlacement freeze() { return new PiecePlacement(this); }
    }

    private static final class PlacedConnector {
        private final int x, y, z;
        private final Direction front, top;
        private final Mc263JigsawStructureBoundary.Joint joint;
        private final String name, target, pool;
        private final int placementPriority, selectionPriority;

        private PlacedConnector(int x, int y, int z, Direction front, Direction top,
                Mc263JigsawStructureBoundary.Joint joint, String name, String target,
                String pool, int placementPriority, int selectionPriority) {
            this.x = x; this.y = y; this.z = z; this.front = front; this.top = top;
            this.joint = joint; this.name = name; this.target = target; this.pool = pool;
            this.placementPriority = placementPriority;
            this.selectionPriority = selectionPriority;
        }

        private int x() { return x; } private int y() { return y; } private int z() { return z; }
        private Direction front() { return front; } private String name() { return name; }
        private String pool() { return pool; } private int placementPriority() { return placementPriority; }
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
