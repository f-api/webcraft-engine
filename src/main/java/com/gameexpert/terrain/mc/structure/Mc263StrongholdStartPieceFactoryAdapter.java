package com.gameexpert.terrain.mc.structure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Public graph/codec adapter for the pinned hard-coded stronghold piece family. */
public final class Mc263StrongholdStartPieceFactoryAdapter
        implements Mc263StrongholdGraphCarrier.PieceFactory {
    private final Mc263StrongholdGraphCarrier.ExistingLeafFactory leaves =
            new Mc263StrongholdGraphCarrier.ExistingLeafFactory();

    private Mc263StrongholdStartPieceFactoryAdapter() { }

    public static Mc263StrongholdStartPieceFactoryAdapter official() {
        return new Mc263StrongholdStartPieceFactoryAdapter();
    }

    /** Begins through the carrier, then installs the exact persisted StartPiece compound. */
    public static Mc263StrongholdGraphCarrier.State begin(int x, int z, long seed) {
        Mc263StrongholdStartPieceFactoryAdapter factory = official();
        Mc263StrongholdGraphCarrier.RandomContinuation random =
                Mc263StrongholdGraphCarrier.RandomContinuation.fromExternalSeed(seed);
        Mc263StrongholdGraphCarrier.Orientation orientation = switch (random.nextInt(4)) {
            case 0 -> Mc263StrongholdGraphCarrier.Orientation.NORTH;
            case 1 -> Mc263StrongholdGraphCarrier.Orientation.EAST;
            case 2 -> Mc263StrongholdGraphCarrier.Orientation.SOUTH;
            case 3 -> Mc263StrongholdGraphCarrier.Orientation.WEST;
            default -> throw new AssertionError("bounded horizontal direction");
        };
        // StructurePiece.makeBoundingBox grows positively for the square 5x11x5 source piece.
        Mc263StrongholdGraphCarrier.BoundingBox rootBox =
                new Mc263StrongholdGraphCarrier.BoundingBox(x, 64, z, Math.addExact(x, 4),
                        74, Math.addExact(z, 4));
        byte[] rootNbt = encode(new PieceData(Mc263StrongholdGraphCarrier.PieceType.START,
                rootBox, 0, orientation, Door.OPENING, true, false, false, false, false,
                false, false, 0));
        Mc263StrongholdGraphCarrier.PieceNode root = new Mc263StrongholdGraphCarrier.PieceNode(
                Mc263StrongholdGraphCarrier.PieceType.START, rootBox, 0, orientation, rootNbt);
        Mc263StrongholdGraphCarrier.PieceNode first = factory.create(
                Mc263StrongholdGraphCarrier.PieceType.FIVE_CROSSING,
                Mc263StrongholdGraphCarrier.forward(root, 1, 1), 1, random, List.of(root));
        if (first == null) throw new IllegalStateException("canonical stronghold start child rejected");
        return Mc263StrongholdGraphCarrier.decode(initialGraph(root, first, random));
    }

    private static byte[] initialGraph(Mc263StrongholdGraphCarrier.PieceNode root,
            Mc263StrongholdGraphCarrier.PieceNode first,
            Mc263StrongholdGraphCarrier.RandomContinuation random) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(512);
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(0x53483236); out.writeInt(1);
                out.writeLong(random.internalState()); out.writeLong(random.calls());
                out.writeInt(Mc263StrongholdGraphCarrier.PieceType.FIVE_CROSSING.ordinal());
                out.writeInt(-1); out.writeInt(-1); out.writeInt(2);
                writeGraphPiece(out, root); writeGraphPiece(out, first);
                out.writeInt(1); out.writeInt(1);
                List<Mc263StrongholdGraphCarrier.PieceType> weighted = List.of(
                        Mc263StrongholdGraphCarrier.PieceType.STRAIGHT,
                        Mc263StrongholdGraphCarrier.PieceType.PRISON_HALL,
                        Mc263StrongholdGraphCarrier.PieceType.LEFT_TURN,
                        Mc263StrongholdGraphCarrier.PieceType.RIGHT_TURN,
                        Mc263StrongholdGraphCarrier.PieceType.ROOM_CROSSING,
                        Mc263StrongholdGraphCarrier.PieceType.STRAIGHT_STAIRS_DOWN,
                        Mc263StrongholdGraphCarrier.PieceType.STAIRS_DOWN,
                        Mc263StrongholdGraphCarrier.PieceType.FIVE_CROSSING,
                        Mc263StrongholdGraphCarrier.PieceType.CHEST_CORRIDOR,
                        Mc263StrongholdGraphCarrier.PieceType.LIBRARY,
                        Mc263StrongholdGraphCarrier.PieceType.PORTAL_ROOM);
                out.writeInt(weighted.size());
                for (Mc263StrongholdGraphCarrier.PieceType type : weighted) {
                    out.writeUTF(type.id());
                    out.writeInt(type == Mc263StrongholdGraphCarrier.PieceType.FIVE_CROSSING
                            ? 1 : 0);
                }
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory stronghold start graph failed", exception);
        }
    }

    private static void writeGraphPiece(DataOutputStream out,
            Mc263StrongholdGraphCarrier.PieceNode piece) throws IOException {
        out.writeUTF(piece.pieceType());
        Mc263StrongholdGraphCarrier.BoundingBox b = piece.boundingBox();
        out.writeInt(b.minX()); out.writeInt(b.minY()); out.writeInt(b.minZ());
        out.writeInt(b.maxX()); out.writeInt(b.maxY()); out.writeInt(b.maxZ());
        out.writeInt(piece.generationDepth()); out.writeInt(piece.orientation().nbtId());
        byte[] nbt = piece.mutableNbt(); out.writeInt(nbt.length); out.write(nbt);
    }

    @Override
    public boolean supports(Mc263StrongholdGraphCarrier.PieceType type) {
        Objects.requireNonNull(type, "type");
        return leaves.supports(type) || switch (type) {
            case STRAIGHT, PRISON_HALL, ROOM_CROSSING, STRAIGHT_STAIRS_DOWN,
                    STAIRS_DOWN, FIVE_CROSSING, CHEST_CORRIDOR, LIBRARY,
                    PORTAL_ROOM, START -> true;
            default -> false;
        };
    }

    @Override
    public Mc263StrongholdGraphCarrier.PieceNode create(
            Mc263StrongholdGraphCarrier.PieceType type,
            Mc263StrongholdGraphCarrier.Connector connector, int depth,
            Mc263StrongholdGraphCarrier.RandomContinuation random,
            List<Mc263StrongholdGraphCarrier.PieceNode> pieces) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(connector, "connector");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(pieces, "pieces");
        if (leaves.supports(type)) return leaves.create(type, connector, depth, random, pieces);
        if (!supports(type) || type == Mc263StrongholdGraphCarrier.PieceType.START) {
            throw new UnsupportedOperationException("unsupported stronghold factory type: "
                    + type.id());
        }
        Mc263StrongholdGraphCarrier.BoundingBox box = candidateBox(type, connector, pieces);
        if (box == null) return null;
        Door door = type == Mc263StrongholdGraphCarrier.PieceType.PORTAL_ROOM
                ? Door.OPENING : door(random.nextInt(5));
        boolean a = false;
        boolean b = false;
        boolean c = false;
        boolean d = false;
        int variant = 0;
        if (type == Mc263StrongholdGraphCarrier.PieceType.STRAIGHT) {
            a = random.nextInt(2) == 0;
            b = random.nextInt(2) == 0;
        } else if (type == Mc263StrongholdGraphCarrier.PieceType.ROOM_CROSSING) {
            variant = random.nextInt(5);
        } else if (type == Mc263StrongholdGraphCarrier.PieceType.FIVE_CROSSING) {
            a = random.nextInt(2) != 0;
            b = random.nextInt(2) != 0;
            c = random.nextInt(2) != 0;
            d = random.nextInt(3) > 0;
        }
        PieceData data = new PieceData(type, box, depth, connector.orientation(), door,
                false, a, b, c, d, false, type == Mc263StrongholdGraphCarrier.PieceType.LIBRARY
                && ySpan(box) == 11, variant);
        return new Mc263StrongholdGraphCarrier.PieceNode(type, box, depth,
                connector.orientation(), encode(data));
    }

    @Override
    public List<Mc263StrongholdGraphCarrier.Connector> successors(
            Mc263StrongholdGraphCarrier.PieceNode piece,
            Mc263StrongholdGraphCarrier.RandomContinuation random) {
        Objects.requireNonNull(piece, "piece");
        Objects.requireNonNull(random, "random");
        if (leaves.supports(piece.type())) return leaves.successors(piece, random);
        PieceData data = decode(piece.type(), piece.boundingBox(), piece.generationDepth(),
                piece.orientation(), piece.mutableNbt());
        return switch (piece.type()) {
            case STRAIGHT -> connectors(piece,
                    Mc263StrongholdGraphCarrier.forward(piece, 1, 1),
                    data.first() ? Mc263StrongholdGraphCarrier.left(piece, 1, 2) : null,
                    data.second() ? Mc263StrongholdGraphCarrier.right(piece, 1, 2) : null);
            case PRISON_HALL, STRAIGHT_STAIRS_DOWN, STAIRS_DOWN, CHEST_CORRIDOR ->
                    List.of(Mc263StrongholdGraphCarrier.forward(piece, 1, 1));
            case ROOM_CROSSING -> List.of(
                    Mc263StrongholdGraphCarrier.forward(piece, 4, 1),
                    Mc263StrongholdGraphCarrier.left(piece, 1, 4),
                    Mc263StrongholdGraphCarrier.right(piece, 1, 4));
            case FIVE_CROSSING -> fiveSuccessors(piece, data);
            case LIBRARY, PORTAL_ROOM -> List.of();
            default -> throw new UnsupportedOperationException(
                    "unsupported stronghold successor type: " + piece.pieceType());
        };
    }

    private static List<Mc263StrongholdGraphCarrier.Connector> fiveSuccessors(
            Mc263StrongholdGraphCarrier.PieceNode piece, PieceData data) {
        int low = 3;
        int high = 5;
        if (piece.orientation() == Mc263StrongholdGraphCarrier.Orientation.WEST
                || piece.orientation() == Mc263StrongholdGraphCarrier.Orientation.NORTH) {
            low = 5;
            high = 3;
        }
        ArrayList<Mc263StrongholdGraphCarrier.Connector> result = new ArrayList<>();
        result.add(Mc263StrongholdGraphCarrier.forward(piece, 5, 1));
        if (data.first()) result.add(Mc263StrongholdGraphCarrier.left(piece, low, 1));
        if (data.second()) result.add(Mc263StrongholdGraphCarrier.left(piece, high, 7));
        if (data.third()) result.add(Mc263StrongholdGraphCarrier.right(piece, low, 1));
        if (data.fourth()) result.add(Mc263StrongholdGraphCarrier.right(piece, high, 7));
        return List.copyOf(result);
    }

    private static List<Mc263StrongholdGraphCarrier.Connector> connectors(
            Mc263StrongholdGraphCarrier.PieceNode ignored,
            Mc263StrongholdGraphCarrier.Connector... values) {
        ArrayList<Mc263StrongholdGraphCarrier.Connector> result = new ArrayList<>();
        for (Mc263StrongholdGraphCarrier.Connector value : values) if (value != null) result.add(value);
        return List.copyOf(result);
    }

    private static Mc263StrongholdGraphCarrier.BoundingBox candidateBox(
            Mc263StrongholdGraphCarrier.PieceType type,
            Mc263StrongholdGraphCarrier.Connector c,
            List<Mc263StrongholdGraphCarrier.PieceNode> pieces) {
        int[] shape = switch (type) {
            case STRAIGHT, CHEST_CORRIDOR -> new int[]{-1, -1, 0, 5, 5, 7};
            case PRISON_HALL -> new int[]{-1, -1, 0, 9, 5, 11};
            case ROOM_CROSSING -> new int[]{-4, -1, 0, 11, 7, 11};
            case STRAIGHT_STAIRS_DOWN -> new int[]{-1, -7, 0, 5, 11, 8};
            case STAIRS_DOWN -> new int[]{-1, -7, 0, 5, 11, 5};
            case FIVE_CROSSING -> new int[]{-4, -3, 0, 10, 9, 11};
            case PORTAL_ROOM -> new int[]{-4, -1, 0, 11, 8, 16};
            case LIBRARY -> new int[]{-4, -1, 0, 14, 11, 15};
            default -> throw new UnsupportedOperationException("no stronghold box for " + type);
        };
        Mc263StrongholdGraphCarrier.BoundingBox box = orient(c, shape);
        if (valid(box, pieces)) return box;
        if (type != Mc263StrongholdGraphCarrier.PieceType.LIBRARY) return null;
        shape[4] = 6;
        box = orient(c, shape);
        return valid(box, pieces) ? box : null;
    }

    private static Mc263StrongholdGraphCarrier.BoundingBox orient(
            Mc263StrongholdGraphCarrier.Connector c, int[] s) {
        return Mc263StrongholdGraphCarrier.orientBox(c.x(), c.y(), c.z(), s[0], s[1], s[2],
                s[3], s[4], s[5], c.orientation());
    }

    private static boolean valid(Mc263StrongholdGraphCarrier.BoundingBox box,
            List<Mc263StrongholdGraphCarrier.PieceNode> pieces) {
        if (box.minY() <= 1) return false;
        for (Mc263StrongholdGraphCarrier.PieceNode piece : pieces) {
            if (piece.boundingBox().intersects(box)) return false;
        }
        return true;
    }

    private static int ySpan(Mc263StrongholdGraphCarrier.BoundingBox box) {
        return Math.addExact(Math.subtractExact(box.maxY(), box.minY()), 1);
    }

    public enum Door { OPENING, WOOD_DOOR, GRATES, IRON_DOOR }

    private static Door door(int roll) {
        return switch (roll) {
            case 0, 1 -> Door.OPENING;
            case 2 -> Door.WOOD_DOOR;
            case 3 -> Door.GRATES;
            case 4 -> Door.IRON_DOOR;
            default -> throw new IllegalArgumentException("door roll outside 0..4");
        };
    }

    /** Complete immutable persisted facts shared by the piece-specific public adapters. */
    public static final class PieceData {
        private final Mc263StrongholdGraphCarrier.PieceType type;
        private final Mc263StrongholdGraphCarrier.BoundingBox box;
        private final int depth;
        private final Mc263StrongholdGraphCarrier.Orientation orientation;
        private final Door door;
        private final boolean source;
        private final boolean first;
        private final boolean second;
        private final boolean third;
        private final boolean fourth;
        private final boolean placed;
        private final boolean tall;
        private final int variant;

        public PieceData(Mc263StrongholdGraphCarrier.PieceType type,
                Mc263StrongholdGraphCarrier.BoundingBox box, int depth,
                Mc263StrongholdGraphCarrier.Orientation orientation, Door door,
                boolean source, boolean first, boolean second, boolean third, boolean fourth,
                boolean placed, boolean tall, int variant) {
            this.type = Objects.requireNonNull(type, "type");
            this.box = Objects.requireNonNull(box, "box");
            this.depth = depth;
            this.orientation = Objects.requireNonNull(orientation, "orientation");
            this.door = Objects.requireNonNull(door, "door");
            this.source = source;
            this.first = first;
            this.second = second;
            this.third = third;
            this.fourth = fourth;
            this.placed = placed;
            this.tall = tall;
            this.variant = variant;
            validateData(this);
        }

        public Mc263StrongholdGraphCarrier.PieceType type() { return type; }
        public Mc263StrongholdGraphCarrier.BoundingBox box() { return box; }
        public int depth() { return depth; }
        public Mc263StrongholdGraphCarrier.Orientation orientation() { return orientation; }
        public Door door() { return door; }
        public boolean source() { return source; }
        public boolean first() { return first; }
        public boolean second() { return second; }
        public boolean third() { return third; }
        public boolean fourth() { return fourth; }
        public boolean placed() { return placed; }
        public boolean tall() { return tall; }
        public int variant() { return variant; }
    }

    public static byte[] encode(PieceData data) {
        validateData(data);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(160);
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(10); out.writeShort(0);
                intArray(out, "BB", data.box);
                switch (data.type) {
                    case STRAIGHT -> byteTag(out, "Left", data.first);
                    case ROOM_CROSSING -> intTag(out, "Type", data.variant);
                    case FIVE_CROSSING -> byteTag(out, "leftLow", data.first);
                    case CHEST_CORRIDOR -> byteTag(out, "Chest", data.placed);
                    case PORTAL_ROOM -> byteTag(out, "Mob", data.placed);
                    default -> { }
                }
                stringTag(out, "EntryDoor", data.door.name());
                if (data.type == Mc263StrongholdGraphCarrier.PieceType.STRAIGHT) {
                    byteTag(out, "Right", data.second);
                }
                if (data.type == Mc263StrongholdGraphCarrier.PieceType.LIBRARY) {
                    byteTag(out, "Tall", data.tall);
                }
                stringTag(out, "id", data.type.id());
                intTag(out, "GD", data.depth);
                if (data.type == Mc263StrongholdGraphCarrier.PieceType.FIVE_CROSSING) {
                    byteTag(out, "rightHigh", data.fourth);
                    byteTag(out, "leftHigh", data.second);
                    byteTag(out, "rightLow", data.third);
                }
                if (data.type == Mc263StrongholdGraphCarrier.PieceType.STAIRS_DOWN
                        || data.type == Mc263StrongholdGraphCarrier.PieceType.START) {
                    byteTag(out, "Source", data.source);
                }
                intTag(out, "O", data.orientation.nbtId());
                out.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory stronghold NBT failed", exception);
        }
    }

    public static PieceData decode(Mc263StrongholdGraphCarrier.PieceType type,
            Mc263StrongholdGraphCarrier.BoundingBox box, int depth,
            Mc263StrongholdGraphCarrier.Orientation orientation, byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            require(in.readUnsignedByte() == 10 && in.readUnsignedShort() == 0);
            requireTag(in, 11, "BB"); require(in.readInt() == 6);
            int[] actual = new int[6];
            for (int i = 0; i < 6; i++) actual[i] = in.readInt();
            require(Arrays.equals(actual, coordinates(box)));
            boolean source = false, a = false, b = false, c = false, d = false;
            boolean placed = false, tall = false;
            int variant = 0;
            switch (type) {
                case STRAIGHT -> a = readBoolean(in, "Left");
                case ROOM_CROSSING -> variant = readInt(in, "Type");
                case FIVE_CROSSING -> a = readBoolean(in, "leftLow");
                case CHEST_CORRIDOR -> placed = readBoolean(in, "Chest");
                case PORTAL_ROOM -> placed = readBoolean(in, "Mob");
                default -> { }
            }
            Door door = Door.valueOf(readString(in, "EntryDoor"));
            if (type == Mc263StrongholdGraphCarrier.PieceType.STRAIGHT) b = readBoolean(in, "Right");
            if (type == Mc263StrongholdGraphCarrier.PieceType.LIBRARY) tall = readBoolean(in, "Tall");
            require(type.id().equals(readString(in, "id")));
            require(depth == readInt(in, "GD"));
            if (type == Mc263StrongholdGraphCarrier.PieceType.FIVE_CROSSING) {
                d = readBoolean(in, "rightHigh");
                b = readBoolean(in, "leftHigh");
                c = readBoolean(in, "rightLow");
            }
            if (type == Mc263StrongholdGraphCarrier.PieceType.STAIRS_DOWN
                    || type == Mc263StrongholdGraphCarrier.PieceType.START) {
                source = readBoolean(in, "Source");
            }
            require(orientation.nbtId() == readInt(in, "O"));
            require(in.readUnsignedByte() == 0 && in.available() == 0);
            PieceData data = new PieceData(type, box, depth, orientation, door, source,
                    a, b, c, d, placed, tall, variant);
            require(Arrays.equals(payload, encode(data)));
            return data;
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            throw new IllegalArgumentException("noncanonical " + type.id() + " persisted NBT",
                    exception);
        }
    }

    private static void validateData(PieceData d) {
        Objects.requireNonNull(d, "data");
        if (d.depth < 0 || d.depth > 51 || d.variant < 0 || d.variant > 4) {
            throw new IllegalArgumentException("stronghold persisted fact outside range");
        }
        if (d.type == Mc263StrongholdGraphCarrier.PieceType.START) {
            if (d.depth != 0 || !d.source || d.door != Door.OPENING) {
                throw new IllegalArgumentException("noncanonical stronghold start facts");
            }
        } else if (d.source || d.depth == 0) {
            throw new IllegalArgumentException("non-root stronghold source/depth fact");
        }
        if (d.type != Mc263StrongholdGraphCarrier.PieceType.ROOM_CROSSING && d.variant != 0) {
            throw new IllegalArgumentException("variant on non-crossing stronghold piece");
        }
        if (d.type == Mc263StrongholdGraphCarrier.PieceType.PORTAL_ROOM && d.door != Door.OPENING) {
            throw new IllegalArgumentException("portal-room entry-door fact must be opening");
        }
        if (d.type == Mc263StrongholdGraphCarrier.PieceType.LIBRARY
                && d.tall != (ySpan(d.box) == 11)) {
            throw new IllegalArgumentException("library Tall disagrees with box");
        }
    }

    private static int[] coordinates(Mc263StrongholdGraphCarrier.BoundingBox b) {
        return new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()};
    }

    private static void intArray(DataOutputStream out, String name,
            Mc263StrongholdGraphCarrier.BoundingBox b) throws IOException {
        out.writeByte(11); out.writeUTF(name); out.writeInt(6);
        for (int coordinate : coordinates(b)) out.writeInt(coordinate);
    }
    private static void stringTag(DataOutputStream out, String name, String value)
            throws IOException { out.writeByte(8); out.writeUTF(name); out.writeUTF(value); }
    private static void intTag(DataOutputStream out, String name, int value)
            throws IOException { out.writeByte(3); out.writeUTF(name); out.writeInt(value); }
    private static void byteTag(DataOutputStream out, String name, boolean value)
            throws IOException { out.writeByte(1); out.writeUTF(name); out.writeByte(value ? 1 : 0); }
    private static void requireTag(DataInputStream in, int type, String name) throws IOException {
        require(in.readUnsignedByte() == type && name.equals(in.readUTF()));
    }
    private static String readString(DataInputStream in, String name) throws IOException {
        requireTag(in, 8, name); return in.readUTF();
    }
    private static int readInt(DataInputStream in, String name) throws IOException {
        requireTag(in, 3, name); return in.readInt();
    }
    private static boolean readBoolean(DataInputStream in, String name) throws IOException {
        requireTag(in, 1, name); int value = in.readUnsignedByte(); require(value <= 1);
        return value != 0;
    }
    private static void require(boolean condition) {
        if (!condition) throw new IllegalStateException("noncanonical stronghold compound");
    }
}
