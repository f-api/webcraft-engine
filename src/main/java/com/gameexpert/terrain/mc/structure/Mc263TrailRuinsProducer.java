package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsCatalog.Connector;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsCatalog.Direction;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsCatalog.Element;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsCatalog.Orientation;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsCatalog.Pool;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsCatalog.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsCatalog.Template;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsCatalog.Vec;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/** Dormant pinned Trail Ruins start and accepted-piece graph producer. */
public final class Mc263TrailRuinsProducer {
    private static final int MIN_WORLD_Y = -64;
    private static final int MAX_WORLD_Y = 319;

    private final Mc263TrailRuinsCatalog catalog;

    public Mc263TrailRuinsProducer(Mc263TrailRuinsCatalog catalog) {
        if (catalog == null) throw new IllegalArgumentException("Trail Ruins catalog is required");
        // Force complete closure before a caller can obtain a producer.
        if (catalog.pools().size() != 7 || catalog.templateCount() != 84) {
            throw new IllegalArgumentException("incomplete Trail Ruins catalog");
        }
        this.catalog = catalog;
    }

    public static Mc263TrailRuinsProducer pinned() {
        return new Mc263TrailRuinsProducer(Mc263TrailRuinsCatalog.pinned());
    }

    public Start generate(long worldSeed, int chunkX, int chunkZ, HeightAccess heightAccess) {
        preflight(heightAccess);
        TraceRandom random = new TraceRandom(0L);
        random.setSeed(worldSeed);
        long xScale = random.nextLong();
        long zScale = random.nextLong();
        random.setSeed((long) chunkX * xScale ^ (long) chunkZ * zScale ^ worldSeed);

        Rotation rotation = Rotation.values()[random.nextInt(4)];
        Pool startPool = catalog.pool(Mc263TrailRuinsCatalog.START_POOL);
        Element selected = startPool.elements().get(random.nextInt(startPool.elements().size()));
        Template centerTemplate = catalog.template(selected.template());
        int baseX = Math.multiplyExact(chunkX, 16);
        int baseZ = Math.multiplyExact(chunkZ, 16);
        Vec initialOrigin = new Vec(baseX, Mc263TrailRuinsCatalog.START_HEIGHT, baseZ);
        Box initialBox = box(centerTemplate, initialOrigin, rotation);
        int centerX = Math.floorDiv(initialBox.minX() + initialBox.maxX(), 2);
        int centerZ = Math.floorDiv(initialBox.minZ() + initialBox.maxZ(), 2);
        int bottomY = Math.addExact(Mc263TrailRuinsCatalog.START_HEIGHT,
                heightAccess.worldSurfaceHeight(centerX, centerZ));
        int centerGroundDelta = 1;
        int moveY = bottomY - (initialBox.minY() + centerGroundDelta);
        Vec centerOrigin = initialOrigin.add(new Vec(0, moveY, 0));
        Box centerBox = initialBox.move(0, moveY, 0);
        if (centerBox.minY() < MIN_WORLD_Y || centerBox.maxY() > MAX_WORLD_Y) {
            return Start.empty(worldSeed, chunkX, chunkZ, random.receipt());
        }

        List<Piece> pieces = new ArrayList<>();
        Piece center = new Piece(0, centerTemplate.key(), selected.processor(), centerOrigin,
                rotation, centerGroundDelta, centerBox, new ArrayList<>());
        pieces.add(center);
        List<Box> occupied = new ArrayList<>();
        occupied.add(centerBox);
        Deque<Pending> queue = new ArrayDeque<>();
        placeChildren(center, 0, worldSeed, centerX, bottomY, centerZ, random, pieces,
                occupied, queue);
        while (!queue.isEmpty()) {
            Pending pending = queue.removeFirst();
            placeChildren(pending.piece(), pending.depth(), worldSeed, centerX, bottomY,
                    centerZ, random, pieces, occupied, queue);
        }
        Box aggregate = pieces.stream().map(Piece::box).reduce(Box::encapsulate)
                .orElseThrow();
        return new Start(worldSeed, chunkX, chunkZ, new Vec(centerX, bottomY, centerZ),
                pieces, aggregate, random.receipt(), false);
    }

    private void placeChildren(Piece source, int depth, long worldSeed, int centerX, int centerY,
            int centerZ, TraceRandom random, List<Piece> pieces, List<Box> occupied,
            Deque<Pending> queue) {
        Template sourceTemplate = catalog.template(source.template());
        List<WorldConnector> sources = transformedConnectors(sourceTemplate, source.origin(),
                source.rotation());
        shuffle(sources, random);
        sourceLoop:
        for (WorldConnector sourceConnector : sources) {
            Vec targetJigsawPosition = sourceConnector.position().add(step(sourceConnector.front()));
            Pool pool = catalog.pool(sourceConnector.connector().pool());
            List<Element> candidates = new ArrayList<>();
            if (depth != Mc263TrailRuinsCatalog.MAX_DEPTH) {
                candidates.addAll(pool.elements());
                shuffle(candidates, random);
            }
            for (Element targetElement : candidates) {
                List<Rotation> rotations = new ArrayList<>(List.of(Rotation.values()));
                shuffle(rotations, random);
                Template targetTemplate = catalog.template(targetElement.template());
                for (Rotation targetRotation : rotations) {
                    List<WorldConnector> targets = transformedConnectors(targetTemplate,
                            new Vec(0, 0, 0), targetRotation);
                    shuffle(targets, random);
                    for (WorldConnector targetConnector : targets) {
                        if (!canAttach(sourceConnector, targetConnector)) continue;
                        Vec rawOrigin = targetJigsawPosition.subtract(targetConnector.position());
                        int sourceLocalY = sourceConnector.position().y() - source.box().minY();
                        int targetLocalY = targetConnector.position().y();
                        int deltaY = sourceLocalY - targetLocalY + step(sourceConnector.front()).y();
                        int targetBoxY = source.box().minY() + deltaY;
                        Box rawBox = box(targetTemplate, rawOrigin, targetRotation);
                        int yOffset = targetBoxY - rawBox.minY();
                        Vec targetOrigin = rawOrigin.add(new Vec(0, yOffset, 0));
                        Box targetBox = rawBox.move(0, yOffset, 0);
                        if (!withinStartBounds(targetBox, centerX, centerY, centerZ)
                                || intersectsAny(targetBox, occupied)) continue;

                        int targetGroundDelta = source.groundLevelDelta() - deltaY;
                        List<Junction> targetJunctions = new ArrayList<>();
                        Piece target = new Piece(pieces.size(), targetTemplate.key(),
                                targetElement.processor(), targetOrigin, targetRotation,
                                targetGroundDelta, targetBox, targetJunctions);
                        int junctionY = source.box().minY() + sourceLocalY;
                        source.mutableJunctions().add(new Junction(targetJigsawPosition.x(),
                                junctionY - sourceLocalY + source.groundLevelDelta(),
                                targetJigsawPosition.z(), deltaY, "rigid"));
                        targetJunctions.add(new Junction(sourceConnector.position().x(),
                                junctionY - targetLocalY + targetGroundDelta,
                                sourceConnector.position().z(), -deltaY, "rigid"));
                        pieces.add(target);
                        occupied.add(targetBox);
                        if (depth + 1 <= Mc263TrailRuinsCatalog.MAX_DEPTH) {
                            queue.addLast(new Pending(target, depth + 1));
                        }
                        continue sourceLoop;
                    }
                }
            }
        }
    }

    private static boolean withinStartBounds(Box box, int x, int y, int z) {
        return box.minX() >= x - Mc263TrailRuinsCatalog.MAX_DISTANCE
                && box.maxX() <= x + Mc263TrailRuinsCatalog.MAX_DISTANCE
                && box.minY() >= Math.max(y - Mc263TrailRuinsCatalog.MAX_DISTANCE, MIN_WORLD_Y)
                && box.maxY() <= Math.min(y + Mc263TrailRuinsCatalog.MAX_DISTANCE, MAX_WORLD_Y)
                && box.minZ() >= z - Mc263TrailRuinsCatalog.MAX_DISTANCE
                && box.maxZ() <= z + Mc263TrailRuinsCatalog.MAX_DISTANCE;
    }

    private static boolean intersectsAny(Box candidate, List<Box> occupied) {
        for (Box box : occupied) if (candidate.intersectsCells(box)) return true;
        return false;
    }

    private static boolean canAttach(WorldConnector source, WorldConnector target) {
        if (source.front() != opposite(target.front())) return false;
        if ("ALIGNED".equals(source.connector().joint()) && source.top() != target.top()) {
            return false;
        }
        return source.connector().target().equals(target.connector().name());
    }

    private static List<WorldConnector> transformedConnectors(Template template, Vec origin,
            Rotation rotation) {
        List<WorldConnector> result = new ArrayList<>();
        for (Connector connector : template.connectors()) {
            try {
                Orientation orientation = Mc263TrailRuinsCatalog.orientation(connector.state());
                result.add(new WorldConnector(connector,
                        origin.add(rotate(connector.position(), rotation)),
                        rotate(orientation.front(), rotation), rotate(orientation.top(), rotation)));
            } catch (IOException error) {
                throw new IllegalArgumentException("invalid preflighted connector", error);
            }
        }
        result.sort(Comparator.comparingInt(value -> -value.connector().selectionPriority()));
        return result;
    }

    static Vec rotate(Vec value, Rotation rotation) {
        return switch (rotation) {
            case NONE -> value;
            case CLOCKWISE_90 -> new Vec(-value.z(), value.y(), value.x());
            case CLOCKWISE_180 -> new Vec(-value.x(), value.y(), -value.z());
            case COUNTERCLOCKWISE_90 -> new Vec(value.z(), value.y(), -value.x());
        };
    }

    static Direction rotate(Direction direction, Rotation rotation) {
        if (direction == Direction.UP || direction == Direction.DOWN) return direction;
        Direction value = direction;
        int turns = switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
        for (int index = 0; index < turns; index++) {
            value = switch (value) {
                case NORTH -> Direction.EAST;
                case EAST -> Direction.SOUTH;
                case SOUTH -> Direction.WEST;
                case WEST -> Direction.NORTH;
                default -> value;
            };
        }
        return value;
    }

    static Box box(Template template, Vec origin, Rotation rotation) {
        Vec far = rotate(new Vec(template.size().x() - 1, template.size().y() - 1,
                template.size().z() - 1), rotation);
        return new Box(Math.min(origin.x(), origin.x() + far.x()), origin.y(),
                Math.min(origin.z(), origin.z() + far.z()),
                Math.max(origin.x(), origin.x() + far.x()), origin.y() + far.y(),
                Math.max(origin.z(), origin.z() + far.z()));
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

    private static Direction opposite(Direction value) {
        return switch (value) {
            case DOWN -> Direction.UP;
            case UP -> Direction.DOWN;
            case NORTH -> Direction.SOUTH;
            case SOUTH -> Direction.NORTH;
            case WEST -> Direction.EAST;
            case EAST -> Direction.WEST;
        };
    }

    private static <T> void shuffle(List<T> values, TraceRandom random) {
        for (int index = values.size(); index > 1; index--) {
            int swap = random.nextInt(index);
            T value = values.get(index - 1);
            values.set(index - 1, values.get(swap));
            values.set(swap, value);
        }
    }

    private static void preflight(HeightAccess access) {
        if (access == null || !access.supportsWorldSurfaceHeight()) {
            throw new IllegalArgumentException("WORLD_SURFACE_WG height capability is required");
        }
    }

    public interface HeightAccess {
        boolean supportsWorldSurfaceHeight();
        int worldSurfaceHeight(int blockX, int blockZ);
    }

    public static final class Start {
        private final long worldSeed;
        private final int chunkX, chunkZ;
        private final Vec stubPosition;
        private final List<Piece> pieces;
        private final Box aggregateBox;
        private final RandomReceipt random;
        private final boolean empty;
        Start(long worldSeed, int chunkX, int chunkZ, Vec stubPosition,
                List<Piece> pieces, Box aggregateBox, RandomReceipt random, boolean empty) {
            this.worldSeed = worldSeed; this.chunkX = chunkX; this.chunkZ = chunkZ;
            this.stubPosition = stubPosition; this.pieces = List.copyOf(pieces);
            this.aggregateBox = aggregateBox; this.random = random; this.empty = empty;
        }
        static Start empty(long seed, int x, int z, RandomReceipt random) {
            return new Start(seed, x, z, new Vec(0, 0, 0), List.of(), null, random, true);
        }
        public long worldSeed() { return worldSeed; }
        public int chunkX() { return chunkX; }
        public int chunkZ() { return chunkZ; }
        public Vec stubPosition() { return stubPosition; }
        public List<Piece> pieces() { return pieces; }
        public Box aggregateBox() { return aggregateBox; }
        public RandomReceipt random() { return random; }
        public boolean empty() { return empty; }
    }

    public static final class Piece {
        private final int ordinal;
        private final String template, processor;
        private final Vec origin;
        private final Rotation rotation;
        private final int groundLevelDelta;
        private final Box box;
        private final List<Junction> junctions;
        Piece(int ordinal, String template, String processor, Vec origin, Rotation rotation,
                int groundLevelDelta, Box box, List<Junction> junctions) {
            this.ordinal = ordinal; this.template = template; this.processor = processor;
            this.origin = origin; this.rotation = rotation; this.groundLevelDelta = groundLevelDelta;
            this.box = box; this.junctions = junctions;
        }
        public int ordinal() { return ordinal; }
        public String template() { return template; }
        public String processor() { return processor; }
        public Vec origin() { return origin; }
        public Rotation rotation() { return rotation; }
        public int groundLevelDelta() { return groundLevelDelta; }
        public Box box() { return box; }
        public List<Junction> junctions() { return List.copyOf(junctions); }
        List<Junction> mutableJunctions() { return junctions; }
    }

    public static final class Junction {
        private final int sourceX, sourceGroundY, sourceZ, deltaY;
        private final String destinationProjection;
        public Junction(int sourceX, int sourceGroundY, int sourceZ, int deltaY,
                String destinationProjection) {
            this.sourceX = sourceX; this.sourceGroundY = sourceGroundY; this.sourceZ = sourceZ;
            this.deltaY = deltaY; this.destinationProjection = destinationProjection;
        }
        public int sourceX() { return sourceX; }
        public int sourceGroundY() { return sourceGroundY; }
        public int sourceZ() { return sourceZ; }
        public int deltaY() { return deltaY; }
        public String destinationProjection() { return destinationProjection; }
    }

    public static final class Box {
        private final int minX, minY, minZ, maxX, maxY, maxZ;
        public Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted Trail Ruins box");
            }
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
        public int minX() { return minX; }
        public int minY() { return minY; }
        public int minZ() { return minZ; }
        public int maxX() { return maxX; }
        public int maxY() { return maxY; }
        public int maxZ() { return maxZ; }
        public Box move(int x, int y, int z) {
            return new Box(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
        }
        public Box encapsulate(Box other) {
            return new Box(Math.min(minX, other.minX), Math.min(minY, other.minY),
                    Math.min(minZ, other.minZ), Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
        }
        boolean intersectsCells(Box other) {
            return minX <= other.maxX && maxX >= other.minX && minY <= other.maxY
                    && maxY >= other.minY && minZ <= other.maxZ && maxZ >= other.minZ;
        }
    }

    public static final class RandomReceipt {
        private final long state48;
        private final int worldgenCount;
        RandomReceipt(long state48, int worldgenCount) {
            this.state48 = state48; this.worldgenCount = worldgenCount;
        }
        public long state48() { return state48; }
        public int worldgenCount() { return worldgenCount; }
        public long[] continuation() {
            TraceRandom copy = TraceRandom.fromInternalState(state48, worldgenCount);
            long[] result = new long[8];
            for (int index = 0; index < result.length; index++) result[index] = copy.nextLong();
            return result;
        }
    }

    private static final class WorldConnector {
        private final Connector connector;
        private final Vec position;
        private final Direction front, top;
        private WorldConnector(Connector connector, Vec position, Direction front, Direction top) {
            this.connector = connector; this.position = position; this.front = front; this.top = top;
        }
        Connector connector() { return connector; }
        Vec position() { return position; }
        Direction front() { return front; }
        Direction top() { return top; }
    }

    private static final class Pending {
        private final Piece piece;
        private final int depth;
        private Pending(Piece piece, int depth) { this.piece = piece; this.depth = depth; }
        Piece piece() { return piece; }
        int depth() { return depth; }
    }

    private static final class TraceRandom {
        private static final long MULTIPLIER = 0x5DEECE66DL;
        private static final long MASK = (1L << 48) - 1L;
        private long state;
        private int count;
        private TraceRandom(long seed) { setSeed(seed); count = 0; }
        static TraceRandom fromInternalState(long state, int count) {
            TraceRandom result = new TraceRandom(0L);
            result.state = state & MASK; result.count = count; return result;
        }
        void setSeed(long seed) { state = (seed ^ MULTIPLIER) & MASK; }
        int next(int bits) {
            state = (state * MULTIPLIER + 0xBL) & MASK;
            count++;
            return (int) (state >>> (48 - bits));
        }
        int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("positive random bound required");
            if ((bound & -bound) == bound) return (int) ((bound * (long) next(31)) >> 31);
            int bits, value;
            do { bits = next(31); value = bits % bound; }
            while (bits - value + bound - 1 < 0);
            return value;
        }
        long nextLong() { return ((long) next(32) << 32) + next(32); }
        RandomReceipt receipt() { return new RandomReceipt(state, count); }
    }
}
