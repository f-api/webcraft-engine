package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.PiecePayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Projection;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Exact pinned 26.3 normal/mesa mineshaft start and ordered-piece constructor. */
public final class Mc263MineshaftStartGenerator {
    public static final int MAX_DEPTH = 8;
    public static final int MAX_DISTANCE = 80;
    public static final int INITIAL_Y = 50;

    private static final int SEA_LEVEL = 63;
    private static final int MIN_Y = -64;
    private static final int EAST = 0;
    private static final int WEST = 1;
    private static final int SOUTH = 2;
    private static final int NORTH = 3;

    private Mc263MineshaftStartGenerator() { }

    public enum Type {
        NORMAL(Mc263MineshaftOrderedAggregate.NORMAL_STRUCTURE, 0),
        MESA(Mc263MineshaftOrderedAggregate.MESA_STRUCTURE, 1);

        private final String structureId;
        private final int nbtId;

        Type(String structureId, int nbtId) {
            this.structureId = structureId;
            this.nbtId = nbtId;
        }

        public String structureId() { return structureId; }
        public int nbtId() { return nbtId; }

        public static Type fromStructureId(String structureId) {
            for (Type value : values()) {
                if (value.structureId.equals(structureId)) return value;
            }
            throw new IllegalArgumentException("unknown pinned mineshaft structure: " + structureId);
        }
    }

    /** Supplies the pinned WORLD_SURFACE_WG base height used only by mesa starts. */
    @FunctionalInterface
    public interface SurfaceHeight {
        int worldSurfaceWg(int blockX, int blockZ);
    }

    public static final class RngContinuation {
        private final long internalSeed;
        private final long rawDraws;

        private RngContinuation(long internalSeed, long rawDraws) {
            this.internalSeed = internalSeed;
            this.rawDraws = rawDraws;
        }

        public long internalSeed() { return internalSeed; }
        public long rawDraws() { return rawDraws; }

        @Override public boolean equals(Object other) {
            return other instanceof RngContinuation value
                    && internalSeed == value.internalSeed && rawDraws == value.rawDraws;
        }

        @Override public int hashCode() {
            return Long.hashCode(internalSeed) * 31 + Long.hashCode(rawDraws);
        }
    }

    public static final class GeneratedStart {
        private final Type type;
        private final ValidStart start;
        private final RngContinuation rngContinuation;

        private GeneratedStart(Type type, ValidStart start, RngContinuation rngContinuation) {
            this.type = type;
            this.start = start;
            this.rngContinuation = rngContinuation;
        }

        public Type type() { return type; }
        public ValidStart start() { return start; }
        public RngContinuation rngContinuation() { return rngContinuation; }
    }

    public static GeneratedStart generate(long worldSeed, int chunkX, int chunkZ,
            String structureId, int references, SurfaceHeight surfaceHeight) {
        return generate(worldSeed, chunkX, chunkZ, Type.fromStructureId(structureId),
                references, surfaceHeight);
    }

    public static GeneratedStart generate(long worldSeed, int chunkX, int chunkZ,
            Type type, int references, SurfaceHeight surfaceHeight) {
        Objects.requireNonNull(type, "type");
        if (references < 0) throw new IllegalArgumentException("negative reference count");
        if (type == Type.MESA) Objects.requireNonNull(surfaceHeight, "mesa surface height");

        LegacyRandom random = new LegacyRandom(largeFeatureSeed(worldSeed, chunkX, chunkZ));
        random.nextDouble();
        int x0 = Math.addExact(Math.multiplyExact(chunkX, 16), 2);
        int z0 = Math.addExact(Math.multiplyExact(chunkZ, 16), 2);
        MutablePiece room = MutablePiece.room(new Box(x0, INITIAL_Y, z0,
                Math.addExact(x0, 7 + random.nextInt(6)),
                54 + random.nextInt(6),
                Math.addExact(z0, 7 + random.nextInt(6))));
        ArrayList<MutablePiece> pieces = new ArrayList<>();
        pieces.add(room);
        addRoomChildren(room, room, pieces, random);

        Box whole = bounds(pieces);
        int shiftY;
        if (type == Type.MESA) {
            int centerX = whole.minX + (whole.maxX - whole.minX + 1) / 2;
            int centerZ = whole.minZ + (whole.maxZ - whole.minZ + 1) / 2;
            int centerY = whole.minY + (whole.maxY - whole.minY + 1) / 2;
            shiftY = Math.subtractExact(surfaceHeight.worldSurfaceWg(centerX, centerZ), centerY);
        } else {
            int candidateMax = whole.ySpan() + MIN_Y + 1;
            int targetMax = SEA_LEVEL - 10;
            if (candidateMax < targetMax) candidateMax += random.nextInt(targetMax - candidateMax);
            shiftY = candidateMax - whole.maxY;
        }

        ArrayList<Piece> persisted = new ArrayList<>(pieces.size());
        for (MutablePiece piece : pieces) persisted.add(piece.persist(type, shiftY));
        BoundingBox adjusted = boundsPersisted(persisted);
        String key = type.structureId + "@" + chunkX + "," + chunkZ;
        ValidStart start = new ValidStart(key, chunkX, chunkZ, references, adjusted, persisted);
        return new GeneratedStart(type, start, random.continuation());
    }

    private static void addRoomChildren(MutablePiece room, MutablePiece root,
            List<MutablePiece> pieces, LegacyRandom random) {
        Box box = room.box;
        int heightSpace = Math.max(1, box.ySpan() - 4);
        int pos;
        for (pos = 0; pos < box.xSpan()
                && (pos += random.nextInt(box.xSpan())) + 3 <= box.xSpan(); pos += 4) {
            MutablePiece child = generateAndAdd(root, pieces, random, box.minX + pos,
                    box.minY + random.nextInt(heightSpace) + 1, box.minZ - 1, NORTH, 0);
            if (child != null) room.entrances.add(new Box(child.box.minX, child.box.minY,
                    box.minZ, child.box.maxX, child.box.maxY, box.minZ + 1));
        }
        for (pos = 0; pos < box.xSpan()
                && (pos += random.nextInt(box.xSpan())) + 3 <= box.xSpan(); pos += 4) {
            MutablePiece child = generateAndAdd(root, pieces, random, box.minX + pos,
                    box.minY + random.nextInt(heightSpace) + 1, box.maxZ + 1, SOUTH, 0);
            if (child != null) room.entrances.add(new Box(child.box.minX, child.box.minY,
                    box.maxZ - 1, child.box.maxX, child.box.maxY, box.maxZ));
        }
        for (pos = 0; pos < box.zSpan()
                && (pos += random.nextInt(box.zSpan())) + 3 <= box.zSpan(); pos += 4) {
            MutablePiece child = generateAndAdd(root, pieces, random, box.minX - 1,
                    box.minY + random.nextInt(heightSpace) + 1, box.minZ + pos, WEST, 0);
            if (child != null) room.entrances.add(new Box(box.minX, child.box.minY,
                    child.box.minZ, box.minX + 1, child.box.maxY, child.box.maxZ));
        }
        for (pos = 0; pos < box.zSpan()
                && (pos += random.nextInt(box.zSpan())) + 3 <= box.zSpan(); pos += 4) {
            MutablePiece child = generateAndAdd(root, pieces, random, box.maxX + 1,
                    box.minY + random.nextInt(heightSpace) + 1, box.minZ + pos, EAST, 0);
            if (child != null) room.entrances.add(new Box(box.maxX - 1, child.box.minY,
                    child.box.minZ, box.maxX, child.box.maxY, child.box.maxZ));
        }
    }

    private static MutablePiece generateAndAdd(MutablePiece root, List<MutablePiece> pieces,
            LegacyRandom random, int x, int y, int z, int direction, int parentDepth) {
        if (parentDepth > MAX_DEPTH
                || Math.abs((long) x - root.box.minX) > MAX_DISTANCE
                || Math.abs((long) z - root.box.minZ) > MAX_DISTANCE) return null;
        MutablePiece child = createRandomPiece(pieces, random, x, y, z,
                direction, parentDepth + 1);
        if (child == null) return null;
        pieces.add(child);
        addChildren(child, root, pieces, random);
        return child;
    }

    private static MutablePiece createRandomPiece(List<MutablePiece> pieces, LegacyRandom random,
            int x, int y, int z, int direction, int depth) {
        int selection = random.nextInt(100);
        if (selection >= 80) return crossing(pieces, random, x, y, z, direction, depth);
        if (selection >= 70) return stairs(pieces, x, y, z, direction, depth);
        return corridor(pieces, random, x, y, z, direction, depth);
    }

    private static MutablePiece corridor(List<MutablePiece> pieces, LegacyRandom random,
            int x, int y, int z, int direction, int depth) {
        int sections = random.nextInt(3) + 2;
        while (sections > 0) {
            int length = sections * 5;
            Box box = switch (direction) {
                case SOUTH -> new Box(x, y, z, x + 2, y + 2, z + length - 1);
                case WEST -> new Box(x - length + 1, y, z, x, y + 2, z + 2);
                case EAST -> new Box(x, y, z, x + length - 1, y + 2, z + 2);
                default -> new Box(x, y, z - length + 1, x + 2, y + 2, z);
            };
            if (!collides(pieces, box)) {
                boolean rails = random.nextInt(3) == 0;
                boolean spider = !rails && random.nextInt(23) == 0;
                return MutablePiece.corridor(box, direction, sections, depth, rails, spider);
            }
            sections--;
        }
        return null;
    }

    private static MutablePiece stairs(List<MutablePiece> pieces,
            int x, int y, int z, int direction, int depth) {
        Box box = switch (direction) {
            case SOUTH -> new Box(x, y - 5, z, x + 2, y + 2, z + 8);
            case WEST -> new Box(x - 8, y - 5, z, x, y + 2, z + 2);
            case EAST -> new Box(x, y - 5, z, x + 8, y + 2, z + 2);
            default -> new Box(x, y - 5, z - 8, x + 2, y + 2, z);
        };
        return collides(pieces, box) ? null : MutablePiece.stairs(box, direction, depth);
    }

    private static MutablePiece crossing(List<MutablePiece> pieces, LegacyRandom random,
            int x, int y, int z, int direction, int depth) {
        boolean tall = random.nextInt(4) == 0;
        int height = tall ? 6 : 2;
        Box box = switch (direction) {
            case SOUTH -> new Box(x - 1, y, z, x + 3, y + height, z + 4);
            case WEST -> new Box(x - 4, y, z - 1, x, y + height, z + 3);
            case EAST -> new Box(x, y, z - 1, x + 4, y + height, z + 3);
            default -> new Box(x - 1, y, z - 4, x + 3, y + height, z);
        };
        return collides(pieces, box) ? null : MutablePiece.crossing(box, direction, depth, tall);
    }

    private static void addChildren(MutablePiece piece, MutablePiece root,
            List<MutablePiece> pieces, LegacyRandom random) {
        switch (piece.kind) {
            case CORRIDOR -> addCorridorChildren(piece, root, pieces, random);
            case CROSSING -> addCrossingChildren(piece, root, pieces, random);
            case STAIRS -> addStairsChild(piece, root, pieces, random);
            case ROOM -> throw new IllegalStateException("only the root may be a room");
        }
    }

    private static void addCorridorChildren(MutablePiece piece, MutablePiece root,
            List<MutablePiece> pieces, LegacyRandom random) {
        Box box = piece.box;
        int depth = piece.depth;
        int endSelection = random.nextInt(4);
        int y = box.minY - 1 + random.nextInt(3);
        switch (piece.direction) {
            case NORTH -> {
                if (endSelection <= 1) generateAndAdd(root, pieces, random, box.minX, y, box.minZ - 1, NORTH, depth);
                else if (endSelection == 2) generateAndAdd(root, pieces, random, box.minX - 1, y, box.minZ, WEST, depth);
                else generateAndAdd(root, pieces, random, box.maxX + 1, y, box.minZ, EAST, depth);
            }
            case SOUTH -> {
                if (endSelection <= 1) generateAndAdd(root, pieces, random, box.minX, y, box.maxZ + 1, SOUTH, depth);
                else if (endSelection == 2) generateAndAdd(root, pieces, random, box.minX - 1, y, box.maxZ - 3, WEST, depth);
                else generateAndAdd(root, pieces, random, box.maxX + 1, y, box.maxZ - 3, EAST, depth);
            }
            case WEST -> {
                if (endSelection <= 1) generateAndAdd(root, pieces, random, box.minX - 1, y, box.minZ, WEST, depth);
                else if (endSelection == 2) generateAndAdd(root, pieces, random, box.minX, y, box.minZ - 1, NORTH, depth);
                else generateAndAdd(root, pieces, random, box.minX, y, box.maxZ + 1, SOUTH, depth);
            }
            case EAST -> {
                if (endSelection <= 1) generateAndAdd(root, pieces, random, box.maxX + 1, y, box.minZ, EAST, depth);
                else if (endSelection == 2) generateAndAdd(root, pieces, random, box.maxX - 3, y, box.minZ - 1, NORTH, depth);
                else generateAndAdd(root, pieces, random, box.maxX - 3, y, box.maxZ + 1, SOUTH, depth);
            }
            default -> throw new IllegalStateException("horizontal direction required");
        }
        if (depth >= MAX_DEPTH) return;
        if (piece.direction == NORTH || piece.direction == SOUTH) {
            for (int z = box.minZ + 3; z + 3 <= box.maxZ; z += 5) {
                int branch = random.nextInt(5);
                if (branch == 0) generateAndAdd(root, pieces, random, box.minX - 1, box.minY, z, WEST, depth + 1);
                else if (branch == 1) generateAndAdd(root, pieces, random, box.maxX + 1, box.minY, z, EAST, depth + 1);
            }
        } else {
            for (int x = box.minX + 3; x + 3 <= box.maxX; x += 5) {
                int branch = random.nextInt(5);
                if (branch == 0) generateAndAdd(root, pieces, random, x, box.minY, box.minZ - 1, NORTH, depth + 1);
                else if (branch == 1) generateAndAdd(root, pieces, random, x, box.minY, box.maxZ + 1, SOUTH, depth + 1);
            }
        }
    }

    private static void addStairsChild(MutablePiece piece, MutablePiece root,
            List<MutablePiece> pieces, LegacyRandom random) {
        Box box = piece.box;
        switch (piece.direction) {
            case NORTH -> generateAndAdd(root, pieces, random, box.minX, box.minY, box.minZ - 1, NORTH, piece.depth);
            case SOUTH -> generateAndAdd(root, pieces, random, box.minX, box.minY, box.maxZ + 1, SOUTH, piece.depth);
            case WEST -> generateAndAdd(root, pieces, random, box.minX - 1, box.minY, box.minZ, WEST, piece.depth);
            case EAST -> generateAndAdd(root, pieces, random, box.maxX + 1, box.minY, box.minZ, EAST, piece.depth);
            default -> throw new IllegalStateException("horizontal direction required");
        }
    }

    private static void addCrossingChildren(MutablePiece piece, MutablePiece root,
            List<MutablePiece> pieces, LegacyRandom random) {
        Box box = piece.box;
        int depth = piece.depth;
        switch (piece.direction) {
            case NORTH -> {
                generateAndAdd(root, pieces, random, box.minX + 1, box.minY, box.minZ - 1, NORTH, depth);
                generateAndAdd(root, pieces, random, box.minX - 1, box.minY, box.minZ + 1, WEST, depth);
                generateAndAdd(root, pieces, random, box.maxX + 1, box.minY, box.minZ + 1, EAST, depth);
            }
            case SOUTH -> {
                generateAndAdd(root, pieces, random, box.minX + 1, box.minY, box.maxZ + 1, SOUTH, depth);
                generateAndAdd(root, pieces, random, box.minX - 1, box.minY, box.minZ + 1, WEST, depth);
                generateAndAdd(root, pieces, random, box.maxX + 1, box.minY, box.minZ + 1, EAST, depth);
            }
            case WEST -> {
                generateAndAdd(root, pieces, random, box.minX + 1, box.minY, box.minZ - 1, NORTH, depth);
                generateAndAdd(root, pieces, random, box.minX + 1, box.minY, box.maxZ + 1, SOUTH, depth);
                generateAndAdd(root, pieces, random, box.minX - 1, box.minY, box.minZ + 1, WEST, depth);
            }
            case EAST -> {
                generateAndAdd(root, pieces, random, box.minX + 1, box.minY, box.minZ - 1, NORTH, depth);
                generateAndAdd(root, pieces, random, box.minX + 1, box.minY, box.maxZ + 1, SOUTH, depth);
                generateAndAdd(root, pieces, random, box.maxX + 1, box.minY, box.minZ + 1, EAST, depth);
            }
            default -> throw new IllegalStateException("horizontal direction required");
        }
        if (!piece.tall) return;
        int upperY = box.minY + 4;
        if (random.nextBoolean()) generateAndAdd(root, pieces, random, box.minX + 1, upperY, box.minZ - 1, NORTH, depth);
        if (random.nextBoolean()) generateAndAdd(root, pieces, random, box.minX - 1, upperY, box.minZ + 1, WEST, depth);
        if (random.nextBoolean()) generateAndAdd(root, pieces, random, box.maxX + 1, upperY, box.minZ + 1, EAST, depth);
        if (random.nextBoolean()) generateAndAdd(root, pieces, random, box.minX + 1, upperY, box.maxZ + 1, SOUTH, depth);
    }

    private static boolean collides(List<MutablePiece> pieces, Box candidate) {
        for (MutablePiece piece : pieces) if (piece.box.intersects(candidate)) return true;
        return false;
    }

    private static Box bounds(List<MutablePiece> pieces) {
        Box result = pieces.getFirst().box;
        for (int index = 1; index < pieces.size(); index++) result = result.encapsulate(pieces.get(index).box);
        return result;
    }

    private static BoundingBox boundsPersisted(List<Piece> pieces) {
        BoundingBox first = pieces.getFirst().boundingBox();
        int minX = first.minX(), minY = first.minY(), minZ = first.minZ();
        int maxX = first.maxX(), maxY = first.maxY(), maxZ = first.maxZ();
        for (int index = 1; index < pieces.size(); index++) {
            BoundingBox box = pieces.get(index).boundingBox();
            minX = Math.min(minX, box.minX()); minY = Math.min(minY, box.minY());
            minZ = Math.min(minZ, box.minZ()); maxX = Math.max(maxX, box.maxX());
            maxY = Math.max(maxY, box.maxY()); maxZ = Math.max(maxZ, box.maxZ());
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static long largeFeatureSeed(long worldSeed, int chunkX, int chunkZ) {
        LegacyRandom seedRandom = new LegacyRandom(worldSeed);
        long a = seedRandom.nextLong();
        long b = seedRandom.nextLong();
        return chunkX * a ^ chunkZ * b ^ worldSeed;
    }

    private enum Kind { ROOM, CORRIDOR, CROSSING, STAIRS }

    private static final class MutablePiece {
        private final Kind kind;
        private final Box box;
        private final int direction;
        private final int sections;
        private final int depth;
        private final boolean tall;
        private final boolean rails;
        private final boolean spider;
        private final ArrayList<Box> entrances = new ArrayList<>();

        private MutablePiece(Kind kind, Box box, int direction, int sections, int depth,
                boolean tall, boolean rails, boolean spider) {
            this.kind = kind; this.box = box; this.direction = direction;
            this.sections = sections; this.depth = depth; this.tall = tall;
            this.rails = rails; this.spider = spider;
        }

        static MutablePiece room(Box box) {
            return new MutablePiece(Kind.ROOM, box, -1, 0, 0, false, false, false);
        }
        static MutablePiece corridor(Box box, int direction, int sections, int depth,
                boolean rails, boolean spider) {
            return new MutablePiece(Kind.CORRIDOR, box, direction, sections, depth,
                    false, rails, spider);
        }
        static MutablePiece crossing(Box box, int direction, int depth, boolean tall) {
            return new MutablePiece(Kind.CROSSING, box, direction, 0, depth,
                    tall, false, false);
        }
        static MutablePiece stairs(Box box, int direction, int depth) {
            return new MutablePiece(Kind.STAIRS, box, direction, 0, depth,
                    false, false, false);
        }

        Piece persist(Type type, int dy) {
            BoundingBox shifted = box.shifted(dy);
            byte[] nbt = switch (kind) {
                case ROOM -> {
                    ArrayList<Mc263MineshaftRoomPieceExecutor.BoundingBox> shiftedEntrances =
                            new ArrayList<>(entrances.size());
                    for (Box entrance : entrances) {
                        BoundingBox b = entrance.shifted(dy);
                        shiftedEntrances.add(new Mc263MineshaftRoomPieceExecutor.BoundingBox(
                                b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
                    }
                    yield Mc263MineshaftRoomCarrierBridge.officialNbt(shifted, shiftedEntrances,
                            0, -1, Mc263MineshaftRoomPieceExecutor.MineshaftType.fromNbtId(type.nbtId));
                }
                case CORRIDOR -> {
                    var facts = new Mc263MineshaftCorridorPieceExecutor.PieceFacts(
                            Mc263MineshaftCorridorPieceExecutor.PIECE_TYPE,
                            corridorBox(shifted), depth, corridorDirection(direction),
                            Mc263MineshaftCorridorPieceExecutor.MineshaftType.fromNbtId(type.nbtId),
                            sections, rails, spider, false);
                    yield Mc263MineshaftCorridorCarrierBridge.officialNbt(facts, false);
                }
                case CROSSING -> Mc263MineshaftCrossingCarrierBridge.officialNbt(shifted, tall,
                        crossingDirection(direction), depth, -1,
                        Mc263MineshaftCrossingPieceExecutor.MineshaftType.fromNbtId(type.nbtId));
                case STAIRS -> Mc263MineshaftStairsCarrierBridge.officialNbt(shifted, depth,
                        stairsDirection(direction),
                        Mc263MineshaftStairsPieceExecutor.MineshaftType.fromNbtId(type.nbtId));
            };
            String pieceType = switch (kind) {
                case ROOM -> Mc263MineshaftRoomPieceExecutor.PIECE_TYPE;
                case CORRIDOR -> Mc263MineshaftCorridorPieceExecutor.PIECE_TYPE;
                case CROSSING -> Mc263MineshaftCrossingPieceExecutor.PIECE_TYPE;
                case STAIRS -> Mc263MineshaftStairsPieceExecutor.PIECE_TYPE;
            };
            return new Piece(pieceType, shifted, false, Projection.NOT_APPLICABLE,
                    0, List.of(), new PiecePayload(nbt));
        }
    }

    private static Mc263MineshaftCorridorPieceExecutor.BoundingBox corridorBox(BoundingBox box) {
        return new Mc263MineshaftCorridorPieceExecutor.BoundingBox(box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ());
    }

    private static Mc263MineshaftCorridorPieceExecutor.Direction corridorDirection(int direction) {
        return switch (direction) {
            case NORTH -> Mc263MineshaftCorridorPieceExecutor.Direction.NORTH;
            case SOUTH -> Mc263MineshaftCorridorPieceExecutor.Direction.SOUTH;
            case WEST -> Mc263MineshaftCorridorPieceExecutor.Direction.WEST;
            case EAST -> Mc263MineshaftCorridorPieceExecutor.Direction.EAST;
            default -> throw new IllegalArgumentException("unknown mineshaft direction");
        };
    }

    private static Mc263MineshaftCrossingPieceExecutor.Direction crossingDirection(int direction) {
        return switch (direction) {
            case NORTH -> Mc263MineshaftCrossingPieceExecutor.Direction.NORTH;
            case SOUTH -> Mc263MineshaftCrossingPieceExecutor.Direction.SOUTH;
            case WEST -> Mc263MineshaftCrossingPieceExecutor.Direction.WEST;
            case EAST -> Mc263MineshaftCrossingPieceExecutor.Direction.EAST;
            default -> throw new IllegalArgumentException("unknown mineshaft direction");
        };
    }

    private static Mc263MineshaftStairsPieceExecutor.Orientation stairsDirection(int direction) {
        return switch (direction) {
            case NORTH -> Mc263MineshaftStairsPieceExecutor.Orientation.NORTH;
            case SOUTH -> Mc263MineshaftStairsPieceExecutor.Orientation.SOUTH;
            case WEST -> Mc263MineshaftStairsPieceExecutor.Orientation.WEST;
            case EAST -> Mc263MineshaftStairsPieceExecutor.Orientation.EAST;
            default -> throw new IllegalArgumentException("unknown mineshaft direction");
        };
    }

    private static final class Box {
        private final int minX, minY, minZ, maxX, maxY, maxZ;
        private Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted mineshaft start box");
            }
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
        int xSpan() { return maxX - minX + 1; }
        int ySpan() { return maxY - minY + 1; }
        int zSpan() { return maxZ - minZ + 1; }
        boolean intersects(Box other) {
            return minX <= other.maxX && maxX >= other.minX
                    && minY <= other.maxY && maxY >= other.minY
                    && minZ <= other.maxZ && maxZ >= other.minZ;
        }
        Box encapsulate(Box other) {
            return new Box(Math.min(minX, other.minX), Math.min(minY, other.minY),
                    Math.min(minZ, other.minZ), Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
        }
        BoundingBox shifted(int dy) {
            return new BoundingBox(minX, Math.addExact(minY, dy), minZ,
                    maxX, Math.addExact(maxY, dy), maxZ);
        }
    }

    private static final class LegacyRandom {
        private static final long MULTIPLIER = 0x5DEECE66DL;
        private static final long ADDEND = 0xBL;
        private static final long MASK = (1L << 48) - 1;
        private long seed;
        private long rawDraws;

        private LegacyRandom(long seed) { this.seed = (seed ^ MULTIPLIER) & MASK; }
        private int next(int bits) {
            seed = (seed * MULTIPLIER + ADDEND) & MASK;
            rawDraws++;
            return (int) (seed >>> (48 - bits));
        }
        private int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
            if ((bound & -bound) == bound) return (int) ((bound * (long) next(31)) >> 31);
            int bits, value;
            do { bits = next(31); value = bits % bound; }
            while (bits - value + bound - 1 < 0);
            return value;
        }
        private long nextLong() { return ((long) next(32) << 32) + next(32); }
        private double nextDouble() { return (((long) next(26) << 27) + next(27)) * 0x1.0p-53; }
        private boolean nextBoolean() { return next(1) != 0; }
        private RngContinuation continuation() { return new RngContinuation(seed, rawDraws); }
    }
}
