package com.gameexpert.terrain.mc.structure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure, resumable carrier for the pinned 26.3 stronghold recursive piece graph.
 *
 * <p>This class deliberately does not invent implementations for stronghold piece factories that
 * are not present in this repository. A caller supplies those factories, and an unsupported
 * selected type fails before the input state is changed. The carrier owns the official weighted
 * selection, recursion ordering, caps, orientation transforms, and continuation state.</p>
 */
public final class Mc263StrongholdGraphCarrier {
    private static final int FORMAT_MAGIC = 0x53483236;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_PARENT_DEPTH = 50;
    private static final int MAX_DISTANCE = 112;
    private static final int SELECTION_ATTEMPTS = 5;
    private static final long RNG_MASK = (1L << 48) - 1;
    private static final long RNG_MULTIPLIER = 0x5DEECE66DL;
    private static final long RNG_ADDEND = 0xBL;

    private Mc263StrongholdGraphCarrier() { }

    public enum Orientation {
        SOUTH(0), WEST(1), NORTH(2), EAST(3);

        private final int nbtId;

        Orientation(int nbtId) {
            this.nbtId = nbtId;
        }

        public int nbtId() {
            return nbtId;
        }

        public static Orientation fromNbtId(int id) {
            for (Orientation orientation : values()) {
                if (orientation.nbtId == id) return orientation;
            }
            throw new IllegalArgumentException("stronghold orientation outside 0..3");
        }
    }

    /** Encounter order is the official weighted-selection order. */
    public enum PieceType {
        STRAIGHT("minecraft:shs", 40, 0, -1),
        PRISON_HALL("minecraft:shph", 5, 5, -1),
        LEFT_TURN("minecraft:shlt", 20, 0, -1),
        RIGHT_TURN("minecraft:shrt", 20, 0, -1),
        ROOM_CROSSING("minecraft:shrc", 10, 6, -1),
        STRAIGHT_STAIRS_DOWN("minecraft:shssd", 5, 5, -1),
        STAIRS_DOWN("minecraft:shsd", 5, 5, -1),
        FIVE_CROSSING("minecraft:sh5c", 5, 4, -1),
        CHEST_CORRIDOR("minecraft:shcc", 5, 4, -1),
        LIBRARY("minecraft:shli", 10, 2, 4),
        PORTAL_ROOM("minecraft:shpr", 20, 1, 5),
        FILLER_CORRIDOR("minecraft:shfc", 0, 0, -1),
        START("minecraft:shstart", 0, 0, -1);

        private final String id;
        private final int weight;
        private final int maxCount;
        private final int minimumExclusiveDepth;

        PieceType(String id, int weight, int maxCount, int minimumExclusiveDepth) {
            this.id = id;
            this.weight = weight;
            this.maxCount = maxCount;
            this.minimumExclusiveDepth = minimumExclusiveDepth;
        }

        public String id() { return id; }
        public int weight() { return weight; }
        public int maxCount() { return maxCount; }

        boolean eligibleAt(int depth, int count) {
            return (maxCount == 0 || count < maxCount) && depth > minimumExclusiveDepth;
        }

        boolean exhausted(int count) {
            return maxCount > 0 && count >= maxCount;
        }

        static PieceType byId(String id) {
            for (PieceType type : values()) if (type.id.equals(id)) return type;
            throw new IllegalArgumentException("unknown pinned stronghold piece type: " + id);
        }
    }

    public enum Termination {
        ACTIVE, COMPLETE_WITH_PORTAL, EXHAUSTED_WITHOUT_PORTAL
    }

    public static final class BoundingBox {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        public BoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted stronghold graph bounding box");
            }
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public int minX() { return minX; }
        public int minY() { return minY; }
        public int minZ() { return minZ; }
        public int maxX() { return maxX; }
        public int maxY() { return maxY; }
        public int maxZ() { return maxZ; }

        public boolean intersects(BoundingBox other) {
            return maxX >= other.minX && minX <= other.maxX
                    && maxY >= other.minY && minY <= other.maxY
                    && maxZ >= other.minZ && minZ <= other.maxZ;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof BoundingBox box)) return false;
            return minX == box.minX && minY == box.minY && minZ == box.minZ
                    && maxX == box.maxX && maxY == box.maxY && maxZ == box.maxZ;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(new int[]{minX, minY, minZ, maxX, maxY, maxZ});
        }
    }

    /** Exact child-factory anchor passed by the official small-door transform. */
    public static final class Connector {
        private final int x;
        private final int y;
        private final int z;
        private final Orientation orientation;
        private final int parentDepth;

        public Connector(int x, int y, int z, Orientation orientation, int parentDepth) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.orientation = Objects.requireNonNull(orientation, "orientation");
            if (parentDepth < 0 || parentDepth > 51) {
                throw new IllegalArgumentException("stronghold connector parent depth outside 0..51");
            }
            this.parentDepth = parentDepth;
        }

        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        public Orientation orientation() { return orientation; }
        public int parentDepth() { return parentDepth; }
    }

    /** Piece payload is copied so mutable successor NBT cannot alias carrier snapshots. */
    public static final class PieceNode {
        private final PieceType type;
        private final BoundingBox boundingBox;
        private final int generationDepth;
        private final Orientation orientation;
        private final byte[] mutableNbt;

        public PieceNode(String pieceType, BoundingBox boundingBox, int generationDepth,
                Orientation orientation, byte[] mutableNbt) {
            this(PieceType.byId(Objects.requireNonNull(pieceType, "pieceType")), boundingBox,
                    generationDepth, orientation, mutableNbt);
        }

        public PieceNode(PieceType type, BoundingBox boundingBox, int generationDepth,
                Orientation orientation, byte[] mutableNbt) {
            this.type = Objects.requireNonNull(type, "type");
            this.boundingBox = Objects.requireNonNull(boundingBox, "boundingBox");
            if (generationDepth < 0 || generationDepth > 51) {
                throw new IllegalArgumentException("stronghold generation depth outside 0..51");
            }
            this.generationDepth = generationDepth;
            this.orientation = Objects.requireNonNull(orientation, "orientation");
            this.mutableNbt = Objects.requireNonNull(mutableNbt, "mutableNbt").clone();
        }

        public PieceType type() { return type; }
        public String pieceType() { return type.id; }
        public BoundingBox boundingBox() { return boundingBox; }
        public int generationDepth() { return generationDepth; }
        public Orientation orientation() { return orientation; }
        public byte[] mutableNbt() { return mutableNbt.clone(); }

        PieceNode withMutableNbt(byte[] replacement) {
            return new PieceNode(type, boundingBox, generationDepth, orientation, replacement);
        }
    }

    /** A factory must be pure: the carrier can call it speculatively during weighted attempts. */
    public interface PieceFactory {
        boolean supports(PieceType type);

        /** Return null only for an official geometric rejection such as a collision or low box. */
        PieceNode create(PieceType type, Connector connector, int generationDepth,
                RandomContinuation random, List<PieceNode> orderedPieces);

        /** Return child connectors in the piece's official addChildren encounter order. */
        List<Connector> successors(PieceNode piece, RandomContinuation random);
    }

    /**
     * Exact graph factories for the three leaf executors currently present in the repository.
     * Compose this with future official piece factories; it intentionally rejects every other
     * type instead of substituting a generic room.
     */
    public static final class ExistingLeafFactory implements PieceFactory {
        @Override
        public boolean supports(PieceType type) {
            return type == PieceType.LEFT_TURN || type == PieceType.RIGHT_TURN
                    || type == PieceType.FILLER_CORRIDOR;
        }

        @Override
        public PieceNode create(PieceType type, Connector connector, int generationDepth,
                RandomContinuation random, List<PieceNode> orderedPieces) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(connector, "connector");
            Objects.requireNonNull(random, "random");
            Objects.requireNonNull(orderedPieces, "orderedPieces");
            if (!supports(type)) {
                throw new UnsupportedOperationException("unsupported existing stronghold leaf: "
                        + type.id);
            }
            if (type == PieceType.FILLER_CORRIDOR) {
                return createFiller(connector, generationDepth, orderedPieces);
            }
            BoundingBox box = orientBox(connector.x, connector.y, connector.z,
                    -1, -1, 0, 5, 5, 5, connector.orientation);
            if (box.minY <= 1 || collision(box, orderedPieces) != null) return null;
            int doorRoll = random.nextInt(5);
            if (type == PieceType.LEFT_TURN) {
                Mc263StrongholdLeftTurnPieceExecutor.EntryDoor door = leftDoor(doorRoll);
                byte[] nbt = Mc263StrongholdLeftTurnCarrierBridge.officialNbt(
                        carrierBox(box), door, generationDepth, leftOrientation(
                                connector.orientation));
                return new PieceNode(type, box, generationDepth, connector.orientation, nbt);
            }
            Mc263StrongholdRightTurnPieceExecutor.EntryDoor door = rightDoor(doorRoll);
            byte[] nbt = Mc263StrongholdRightTurnCarrierBridge.officialNbt(
                    carrierBox(box), door, generationDepth, rightOrientation(
                            connector.orientation));
            return new PieceNode(type, box, generationDepth, connector.orientation, nbt);
        }

        @Override
        public List<Connector> successors(PieceNode piece, RandomContinuation random) {
            Objects.requireNonNull(piece, "piece");
            Objects.requireNonNull(random, "random");
            if (piece.type == PieceType.FILLER_CORRIDOR) return List.of();
            if (piece.type == PieceType.LEFT_TURN) {
                return List.of(piece.orientation == Orientation.NORTH
                        || piece.orientation == Orientation.EAST
                        ? left(piece, 1, 1) : right(piece, 1, 1));
            }
            if (piece.type == PieceType.RIGHT_TURN) {
                return List.of(piece.orientation == Orientation.NORTH
                        || piece.orientation == Orientation.EAST
                        ? right(piece, 1, 1) : left(piece, 1, 1));
            }
            throw new UnsupportedOperationException("unsupported existing stronghold leaf: "
                    + piece.type.id);
        }

        private static PieceNode createFiller(Connector connector, int generationDepth,
                List<PieceNode> orderedPieces) {
            BoundingBox full = orientBox(connector.x, connector.y, connector.z,
                    -1, -1, 0, 5, 5, 4, connector.orientation);
            PieceNode collision = collision(full, orderedPieces);
            if (collision == null || collision.boundingBox.minY != full.minY) return null;
            for (int shortenedDepth = 2; shortenedDepth >= 1; shortenedDepth--) {
                BoundingBox shortened = orientBox(connector.x, connector.y, connector.z,
                        -1, -1, 0, 5, 5, shortenedDepth, connector.orientation);
                if (!collision.boundingBox.intersects(shortened)) {
                    int steps = shortenedDepth + 1;
                    BoundingBox box = orientBox(connector.x, connector.y, connector.z,
                            -1, -1, 0, 5, 5, steps, connector.orientation);
                    if (box.minY <= 1) return null;
                    byte[] nbt = Mc263StrongholdFillerCorridorCarrierBridge.officialNbt(
                            carrierBox(box), steps, generationDepth,
                            fillerOrientation(connector.orientation));
                    return new PieceNode(PieceType.FILLER_CORRIDOR, box, generationDepth,
                            connector.orientation, nbt);
                }
            }
            return null;
        }

        private static PieceNode collision(BoundingBox box, List<PieceNode> pieces) {
            for (PieceNode piece : pieces) {
                if (piece.boundingBox.intersects(box)) return piece;
            }
            return null;
        }

        private static Mc263StructureCarrier.BoundingBox carrierBox(BoundingBox box) {
            return new Mc263StructureCarrier.BoundingBox(box.minX, box.minY, box.minZ,
                    box.maxX, box.maxY, box.maxZ);
        }

        private static Mc263StrongholdLeftTurnPieceExecutor.EntryDoor leftDoor(int roll) {
            return switch (roll) {
                case 0, 1 -> Mc263StrongholdLeftTurnPieceExecutor.EntryDoor.OPENING;
                case 2 -> Mc263StrongholdLeftTurnPieceExecutor.EntryDoor.WOOD_DOOR;
                case 3 -> Mc263StrongholdLeftTurnPieceExecutor.EntryDoor.GRATES;
                case 4 -> Mc263StrongholdLeftTurnPieceExecutor.EntryDoor.IRON_DOOR;
                default -> throw new IllegalArgumentException("stronghold door roll outside 0..4");
            };
        }

        private static Mc263StrongholdRightTurnPieceExecutor.EntryDoor rightDoor(int roll) {
            return switch (roll) {
                case 0, 1 -> Mc263StrongholdRightTurnPieceExecutor.EntryDoor.OPENING;
                case 2 -> Mc263StrongholdRightTurnPieceExecutor.EntryDoor.WOOD_DOOR;
                case 3 -> Mc263StrongholdRightTurnPieceExecutor.EntryDoor.GRATES;
                case 4 -> Mc263StrongholdRightTurnPieceExecutor.EntryDoor.IRON_DOOR;
                default -> throw new IllegalArgumentException("stronghold door roll outside 0..4");
            };
        }

        private static Mc263StrongholdLeftTurnPieceExecutor.Orientation leftOrientation(
                Orientation orientation) {
            return Mc263StrongholdLeftTurnPieceExecutor.Orientation.fromNbtId(orientation.nbtId);
        }

        private static Mc263StrongholdRightTurnPieceExecutor.Orientation rightOrientation(
                Orientation orientation) {
            return Mc263StrongholdRightTurnPieceExecutor.Orientation.fromNbtId(orientation.nbtId);
        }

        private static Mc263StrongholdFillerCorridorPieceExecutor.Orientation fillerOrientation(
                Orientation orientation) {
            return Mc263StrongholdFillerCorridorPieceExecutor.Orientation.fromNbtId(
                    orientation.nbtId);
        }
    }

    /** Java/legacy 48-bit continuation with unbiased bounded nextInt behavior. */
    public static final class RandomContinuation {
        private long internalState;
        private long calls;

        private RandomContinuation(long internalState, long calls) {
            if ((internalState & ~RNG_MASK) != 0 || calls < 0) {
                throw new IllegalArgumentException("invalid stronghold RNG continuation");
            }
            this.internalState = internalState;
            this.calls = calls;
        }

        public static RandomContinuation fromExternalSeed(long seed) {
            return new RandomContinuation((seed ^ RNG_MULTIPLIER) & RNG_MASK, 0);
        }

        public static RandomContinuation resume(long internalState, long calls) {
            return new RandomContinuation(internalState, calls);
        }

        public int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
            if ((bound & -bound) == bound) return (int) ((bound * (long) next(31)) >> 31);
            int bits;
            int value;
            do {
                bits = next(31);
                value = bits % bound;
            } while (bits - value + (bound - 1) < 0);
            return value;
        }

        private int next(int bits) {
            internalState = (internalState * RNG_MULTIPLIER + RNG_ADDEND) & RNG_MASK;
            calls++;
            return (int) (internalState >>> (48 - bits));
        }

        public long internalState() { return internalState; }
        public long calls() { return calls; }

        RandomContinuation copy() {
            return new RandomContinuation(internalState, calls);
        }
    }

    public static final class State {
        private final List<PieceNode> orderedPieces;
        private final List<Integer> pendingChildren;
        private final EnumMap<PieceType, Integer> placeCounts;
        private final PieceType previousPiece;
        private final PieceType imposedPiece;
        private final int portalRoomIndex;
        private final long rngInternalState;
        private final long rngCalls;

        private State(List<PieceNode> orderedPieces, List<Integer> pendingChildren,
                Map<PieceType, Integer> placeCounts, PieceType previousPiece,
                PieceType imposedPiece, int portalRoomIndex, long rngInternalState,
                long rngCalls) {
            this.orderedPieces = List.copyOf(orderedPieces);
            this.pendingChildren = List.copyOf(pendingChildren);
            this.placeCounts = new EnumMap<>(PieceType.class);
            this.placeCounts.putAll(placeCounts);
            this.previousPiece = previousPiece;
            this.imposedPiece = imposedPiece;
            this.portalRoomIndex = portalRoomIndex;
            this.rngInternalState = rngInternalState;
            this.rngCalls = rngCalls;
            validate();
        }

        public List<PieceNode> orderedPieces() { return orderedPieces; }
        public List<Integer> pendingChildren() { return pendingChildren; }
        public int placeCount(PieceType type) { return placeCounts.getOrDefault(type, 0); }
        public PieceType previousPiece() { return previousPiece; }
        public PieceType imposedPiece() { return imposedPiece; }
        public int portalRoomIndex() { return portalRoomIndex; }
        public long rngInternalState() { return rngInternalState; }
        public long rngCalls() { return rngCalls; }

        public Termination termination() {
            if (!pendingChildren.isEmpty()) return Termination.ACTIVE;
            return portalRoomIndex >= 0 ? Termination.COMPLETE_WITH_PORTAL
                    : Termination.EXHAUSTED_WITHOUT_PORTAL;
        }

        private void validate() {
            if (orderedPieces.isEmpty() || orderedPieces.getFirst().type != PieceType.START) {
                throw new IllegalArgumentException("stronghold graph must begin with start piece");
            }
            if ((rngInternalState & ~RNG_MASK) != 0 || rngCalls < 0) {
                throw new IllegalArgumentException("invalid persisted stronghold RNG state");
            }
            boolean[] pending = new boolean[orderedPieces.size()];
            for (int index : pendingChildren) {
                if (index <= 0 || index >= orderedPieces.size() || pending[index]) {
                    throw new IllegalArgumentException("invalid stronghold pending-child order");
                }
                pending[index] = true;
            }
            if (portalRoomIndex >= orderedPieces.size()
                    || (portalRoomIndex >= 0
                    && orderedPieces.get(portalRoomIndex).type != PieceType.PORTAL_ROOM)) {
                throw new IllegalArgumentException("invalid stronghold portal-room fact");
            }
            int observedPortalIndex = -1;
            EnumMap<PieceType, Integer> observedCounts = new EnumMap<>(PieceType.class);
            for (int index = 0; index < orderedPieces.size(); index++) {
                PieceNode piece = orderedPieces.get(index);
                if (index > 0 && (piece.type == PieceType.START || piece.generationDepth == 0)) {
                    throw new IllegalArgumentException("invalid non-root stronghold piece");
                }
                if (piece.type == PieceType.PORTAL_ROOM) {
                    if (observedPortalIndex >= 0) {
                        throw new IllegalArgumentException("duplicate stronghold portal room");
                    }
                    observedPortalIndex = index;
                }
                if (piece.type.weight > 0) {
                    observedCounts.put(piece.type,
                            observedCounts.getOrDefault(piece.type, 0) + 1);
                }
            }
            if (observedPortalIndex != portalRoomIndex) {
                throw new IllegalArgumentException("stronghold portal-room fact disagrees with graph");
            }
            for (PieceType type : PieceType.values()) {
                int count = placeCounts.getOrDefault(type, 0);
                if (count < 0 || (type.maxCount > 0 && count > type.maxCount)) {
                    throw new IllegalArgumentException("invalid stronghold piece count");
                }
                if (type.weight > 0 && count != observedCounts.getOrDefault(type, 0)) {
                    throw new IllegalArgumentException("stronghold piece count disagrees with graph");
                }
            }
            if (previousPiece != null && previousPiece.weight == 0) {
                throw new IllegalArgumentException("invalid previous stronghold weighted piece");
            }
            if (imposedPiece != null && imposedPiece.weight == 0) {
                throw new IllegalArgumentException("invalid imposed stronghold weighted piece");
            }
        }
    }

    /** Starts at the official magic Y and immediately imposes the first five-crossing child. */
    public static State begin(int startX, int startZ, long randomSeed, PieceFactory factory) {
        Objects.requireNonNull(factory, "factory");
        RandomContinuation random = RandomContinuation.fromExternalSeed(randomSeed);
        Orientation orientation = Orientation.values()[random.nextInt(4)];
        BoundingBox rootBox = makeBoundingBox(startX, 64, startZ, orientation, 5, 11, 5);
        PieceNode root = new PieceNode(PieceType.START, rootBox, 0, orientation, new byte[0]);
        MutableState mutable = new MutableState(root, random, PieceType.FIVE_CROSSING);
        addChild(mutable, forward(root, 1, 1), factory);
        return mutable.freeze();
    }

    /** Expands the same randomly selected pending child that vanilla removes from its list. */
    public static State expandNext(State state, PieceFactory factory) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(factory, "factory");
        if (state.pendingChildren.isEmpty()) return state;
        MutableState mutable = new MutableState(state);
        int pendingOffset = mutable.random.nextInt(mutable.pendingChildren.size());
        int pieceIndex = mutable.pendingChildren.remove(pendingOffset);
        PieceNode piece = mutable.orderedPieces.get(pieceIndex);
        List<Connector> successors = Objects.requireNonNull(
                factory.successors(piece, mutable.random), "successors");
        for (Connector connector : successors) {
            Objects.requireNonNull(connector, "successor connector");
            if (connector.parentDepth != piece.generationDepth) {
                throw new IllegalArgumentException("successor parent depth disagrees with piece");
            }
            addChild(mutable, connector, factory);
        }
        return mutable.freeze();
    }

    public static State expandFully(State state, PieceFactory factory) {
        State current = Objects.requireNonNull(state, "state");
        while (current.termination() == Termination.ACTIVE) {
            current = expandNext(current, factory);
        }
        return current;
    }

    /** Replaces mutable official piece NBT without changing graph/RNG ordering facts. */
    public static State replaceMutableNbt(State state, int pieceIndex,
            String expectedPieceType, byte[] replacement) {
        Objects.requireNonNull(state, "state");
        PieceType expected = PieceType.byId(expectedPieceType);
        if (pieceIndex < 0 || pieceIndex >= state.orderedPieces.size()
                || state.orderedPieces.get(pieceIndex).type != expected) {
            throw new IllegalArgumentException("stronghold mutable-NBT piece identity mismatch");
        }
        ArrayList<PieceNode> pieces = new ArrayList<>(state.orderedPieces);
        pieces.set(pieceIndex, pieces.get(pieceIndex).withMutableNbt(replacement));
        return new State(pieces, state.pendingChildren, state.placeCounts, state.previousPiece,
                state.imposedPiece, state.portalRoomIndex, state.rngInternalState, state.rngCalls);
    }

    private static PieceNode addChild(MutableState state, Connector connector,
            PieceFactory factory) {
        if (connector.parentDepth > MAX_PARENT_DEPTH
                || Math.abs((long) connector.x - state.rootBox.minX) > MAX_DISTANCE
                || Math.abs((long) connector.z - state.rootBox.minZ) > MAX_DISTANCE) {
            return null;
        }
        int depth = connector.parentDepth + 1;
        if (!hasLimitedPieceRemaining(state)) return null;

        if (state.imposedPiece != null) {
            PieceType imposed = state.imposedPiece;
            state.imposedPiece = null;
            PieceNode imposedNode = createKnown(factory, imposed, connector, depth, state);
            if (imposedNode != null) return commitPiece(state, imposed, imposedNode);
        }

        int totalWeight = totalWeight(state);
        for (int attempt = 0; attempt < SELECTION_ATTEMPTS; attempt++) {
            int target = state.random.nextInt(totalWeight);
            for (PieceType type : weightedTypes()) {
                if (type.exhausted(state.count(type))) continue;
                target -= type.weight;
                if (target >= 0) continue;
                if (!type.eligibleAt(depth, state.count(type)) || type == state.previousPiece) {
                    break;
                }
                PieceNode node = createKnown(factory, type, connector, depth, state);
                if (node != null) return commitPiece(state, type, node);
            }
        }
        if (!factory.supports(PieceType.FILLER_CORRIDOR)) {
            throw new UnsupportedOperationException("unsupported stronghold piece factory: "
                    + PieceType.FILLER_CORRIDOR.id);
        }
        PieceNode filler = factory.create(PieceType.FILLER_CORRIDOR, connector, depth,
                state.random, Collections.unmodifiableList(state.orderedPieces));
        validateFactoryResult(PieceType.FILLER_CORRIDOR, connector, depth, filler);
        return filler == null ? null
                : commitCollisionDerivedFiller(state, connector, depth, filler);
    }

    private static PieceNode createKnown(PieceFactory factory, PieceType type,
            Connector connector, int depth, MutableState state) {
        if (!factory.supports(type)) {
            throw new UnsupportedOperationException("unsupported stronghold piece factory: "
                    + type.id);
        }
        PieceNode node = factory.create(type, connector, depth, state.random,
                Collections.unmodifiableList(state.orderedPieces));
        validateFactoryResult(type, connector, depth, node);
        return node;
    }

    private static void validateFactoryResult(PieceType type, Connector connector, int depth,
            PieceNode node) {
        if (node == null) return;
        if (node.type != type || node.generationDepth != depth
                || node.orientation != connector.orientation || node.boundingBox.minY <= 1) {
            throw new IllegalArgumentException("noncanonical stronghold piece factory result");
        }
    }

    private static PieceNode commitPiece(MutableState state, PieceType type, PieceNode node) {
        state.placeCounts.put(type, state.count(type) + 1);
        state.previousPiece = type;
        return commitUnweightedPiece(state, node);
    }

    private static PieceNode commitUnweightedPiece(MutableState state, PieceNode node) {
        for (PieceNode existing : state.orderedPieces) {
            if (existing.boundingBox.intersects(node.boundingBox)) {
                throw new IllegalArgumentException("stronghold factory returned colliding piece");
            }
        }
        int index = state.orderedPieces.size();
        state.orderedPieces.add(node);
        state.pendingChildren.add(index);
        if (node.type == PieceType.PORTAL_ROOM) state.portalRoomIndex = index;
        return node;
    }

    private static PieceNode commitCollisionDerivedFiller(MutableState state,
            Connector connector, int depth, PieceNode node) {
        // The official filler closes the exact gap to the collision witness that created it.
        // Re-derive that result so this exception cannot admit an arbitrary overlapping box.
        PieceNode derived = ExistingLeafFactory.createFiller(connector, depth,
                state.orderedPieces);
        if (!samePiece(derived, node)) {
            throw new IllegalArgumentException("stronghold factory returned colliding piece");
        }
        int index = state.orderedPieces.size();
        state.orderedPieces.add(node);
        state.pendingChildren.add(index);
        return node;
    }

    private static boolean samePiece(PieceNode first, PieceNode second) {
        return first == second || first != null && second != null
                && first.type == second.type
                && first.boundingBox.equals(second.boundingBox)
                && first.generationDepth == second.generationDepth
                && first.orientation == second.orientation
                && Arrays.equals(first.mutableNbt, second.mutableNbt);
    }

    private static boolean hasLimitedPieceRemaining(MutableState state) {
        for (PieceType type : weightedTypes()) {
            if (type.maxCount > 0 && state.count(type) < type.maxCount) return true;
        }
        return false;
    }

    private static int totalWeight(MutableState state) {
        int result = 0;
        for (PieceType type : weightedTypes()) {
            if (!type.exhausted(state.count(type))) result += type.weight;
        }
        return result;
    }

    private static List<PieceType> weightedTypes() {
        return List.of(PieceType.STRAIGHT, PieceType.PRISON_HALL, PieceType.LEFT_TURN,
                PieceType.RIGHT_TURN, PieceType.ROOM_CROSSING,
                PieceType.STRAIGHT_STAIRS_DOWN, PieceType.STAIRS_DOWN,
                PieceType.FIVE_CROSSING, PieceType.CHEST_CORRIDOR,
                PieceType.LIBRARY, PieceType.PORTAL_ROOM);
    }

    public static Connector forward(PieceNode piece, int horizontalOffset, int yOffset) {
        BoundingBox box = piece.boundingBox;
        return switch (piece.orientation) {
            case NORTH -> new Connector(Math.addExact(box.minX, horizontalOffset),
                    Math.addExact(box.minY, yOffset), Math.subtractExact(box.minZ, 1),
                    Orientation.NORTH, piece.generationDepth);
            case SOUTH -> new Connector(Math.addExact(box.minX, horizontalOffset),
                    Math.addExact(box.minY, yOffset), Math.addExact(box.maxZ, 1),
                    Orientation.SOUTH, piece.generationDepth);
            case WEST -> new Connector(Math.subtractExact(box.minX, 1),
                    Math.addExact(box.minY, yOffset), Math.addExact(box.minZ, horizontalOffset),
                    Orientation.WEST, piece.generationDepth);
            case EAST -> new Connector(Math.addExact(box.maxX, 1),
                    Math.addExact(box.minY, yOffset), Math.addExact(box.minZ, horizontalOffset),
                    Orientation.EAST, piece.generationDepth);
        };
    }

    public static Connector left(PieceNode piece, int yOffset, int horizontalOffset) {
        BoundingBox box = piece.boundingBox;
        return switch (piece.orientation) {
            case NORTH, SOUTH -> new Connector(Math.subtractExact(box.minX, 1),
                    Math.addExact(box.minY, yOffset), Math.addExact(box.minZ, horizontalOffset),
                    Orientation.WEST, piece.generationDepth);
            case WEST, EAST -> new Connector(Math.addExact(box.minX, horizontalOffset),
                    Math.addExact(box.minY, yOffset), Math.subtractExact(box.minZ, 1),
                    Orientation.NORTH, piece.generationDepth);
        };
    }

    public static Connector right(PieceNode piece, int yOffset, int horizontalOffset) {
        BoundingBox box = piece.boundingBox;
        return switch (piece.orientation) {
            case NORTH, SOUTH -> new Connector(Math.addExact(box.maxX, 1),
                    Math.addExact(box.minY, yOffset), Math.addExact(box.minZ, horizontalOffset),
                    Orientation.EAST, piece.generationDepth);
            case WEST, EAST -> new Connector(Math.addExact(box.minX, horizontalOffset),
                    Math.addExact(box.minY, yOffset), Math.addExact(box.maxZ, 1),
                    Orientation.SOUTH, piece.generationDepth);
        };
    }

    public static BoundingBox orientBox(int x, int y, int z, int offsetX, int offsetY,
            int offsetZ, int width, int height, int depth, Orientation orientation) {
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("stronghold oriented dimensions must be positive");
        }
        return switch (orientation) {
            case NORTH -> new BoundingBox(Math.addExact(x, offsetX), Math.addExact(y, offsetY),
                    Math.addExact(Math.subtractExact(z, depth - 1), offsetZ),
                    Math.addExact(Math.addExact(x, offsetX), width - 1),
                    Math.addExact(Math.addExact(y, offsetY), height - 1),
                    Math.addExact(z, offsetZ));
            case SOUTH -> new BoundingBox(Math.addExact(x, offsetX), Math.addExact(y, offsetY),
                    Math.addExact(z, offsetZ),
                    Math.addExact(Math.addExact(x, offsetX), width - 1),
                    Math.addExact(Math.addExact(y, offsetY), height - 1),
                    Math.addExact(Math.addExact(z, offsetZ), depth - 1));
            case WEST -> new BoundingBox(Math.addExact(Math.subtractExact(x, depth - 1), offsetZ),
                    Math.addExact(y, offsetY), Math.addExact(z, offsetX),
                    Math.addExact(x, offsetZ),
                    Math.addExact(Math.addExact(y, offsetY), height - 1),
                    Math.addExact(Math.addExact(z, offsetX), width - 1));
            case EAST -> new BoundingBox(Math.addExact(x, offsetZ), Math.addExact(y, offsetY),
                    Math.addExact(z, offsetX),
                    Math.addExact(Math.addExact(x, offsetZ), depth - 1),
                    Math.addExact(Math.addExact(y, offsetY), height - 1),
                    Math.addExact(Math.addExact(z, offsetX), width - 1));
        };
    }

    private static BoundingBox makeBoundingBox(int x, int y, int z,
            Orientation orientation, int width, int height, int depth) {
        return orientBox(x, y, z, 0, 0, 0, width, height, depth, orientation);
    }

    public static byte[] encode(State state) {
        Objects.requireNonNull(state, "state");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(FORMAT_MAGIC);
                output.writeInt(FORMAT_VERSION);
                output.writeLong(state.rngInternalState);
                output.writeLong(state.rngCalls);
                output.writeInt(state.previousPiece == null ? -1 : state.previousPiece.ordinal());
                output.writeInt(state.imposedPiece == null ? -1 : state.imposedPiece.ordinal());
                output.writeInt(state.portalRoomIndex);
                output.writeInt(state.orderedPieces.size());
                for (PieceNode piece : state.orderedPieces) writePiece(output, piece);
                output.writeInt(state.pendingChildren.size());
                for (int index : state.pendingChildren) output.writeInt(index);
                List<PieceType> weighted = weightedTypes();
                output.writeInt(weighted.size());
                for (PieceType type : weighted) {
                    output.writeUTF(type.id);
                    output.writeInt(state.placeCount(type));
                }
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory stronghold graph encoding failed", exception);
        }
    }

    public static State decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            require(input.readInt() == FORMAT_MAGIC && input.readInt() == FORMAT_VERSION);
            long rngState = input.readLong();
            long rngCalls = input.readLong();
            PieceType previous = optionalType(input.readInt());
            PieceType imposed = optionalType(input.readInt());
            int portalIndex = input.readInt();
            int pieceCount = boundedSize(input.readInt(), 4096);
            ArrayList<PieceNode> pieces = new ArrayList<>(pieceCount);
            for (int index = 0; index < pieceCount; index++) pieces.add(readPiece(input));
            int pendingCount = boundedSize(input.readInt(), pieceCount);
            ArrayList<Integer> pending = new ArrayList<>(pendingCount);
            for (int index = 0; index < pendingCount; index++) pending.add(input.readInt());
            List<PieceType> weighted = weightedTypes();
            require(input.readInt() == weighted.size());
            EnumMap<PieceType, Integer> counts = new EnumMap<>(PieceType.class);
            for (PieceType expected : weighted) {
                require(expected.id.equals(input.readUTF()));
                counts.put(expected, input.readInt());
            }
            require(input.available() == 0);
            return new State(pieces, pending, counts, previous, imposed, portalIndex,
                    rngState, rngCalls);
        } catch (IOException | IllegalStateException exception) {
            throw new IllegalArgumentException("noncanonical stronghold graph carrier", exception);
        }
    }

    private static void writePiece(DataOutputStream output, PieceNode piece) throws IOException {
        output.writeUTF(piece.type.id);
        BoundingBox box = piece.boundingBox;
        output.writeInt(box.minX); output.writeInt(box.minY); output.writeInt(box.minZ);
        output.writeInt(box.maxX); output.writeInt(box.maxY); output.writeInt(box.maxZ);
        output.writeInt(piece.generationDepth);
        output.writeInt(piece.orientation.nbtId);
        output.writeInt(piece.mutableNbt.length);
        output.write(piece.mutableNbt);
    }

    private static PieceNode readPiece(DataInputStream input) throws IOException {
        PieceType type = PieceType.byId(input.readUTF());
        BoundingBox box = new BoundingBox(input.readInt(), input.readInt(), input.readInt(),
                input.readInt(), input.readInt(), input.readInt());
        int depth = input.readInt();
        Orientation orientation = Orientation.fromNbtId(input.readInt());
        int nbtLength = boundedSize(input.readInt(), 1 << 20);
        return new PieceNode(type, box, depth, orientation, input.readNBytes(nbtLength));
    }

    private static PieceType optionalType(int ordinal) {
        if (ordinal == -1) return null;
        if (ordinal < 0 || ordinal >= PieceType.values().length) {
            throw new IllegalStateException("invalid stronghold piece ordinal");
        }
        return PieceType.values()[ordinal];
    }

    private static int boundedSize(int value, int maximum) {
        if (value < 0 || value > maximum) throw new IllegalStateException("invalid carrier size");
        return value;
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalStateException("noncanonical stronghold graph carrier");
    }

    private static final class MutableState {
        private final ArrayList<PieceNode> orderedPieces;
        private final ArrayList<Integer> pendingChildren;
        private final EnumMap<PieceType, Integer> placeCounts;
        private final BoundingBox rootBox;
        private final RandomContinuation random;
        private PieceType previousPiece;
        private PieceType imposedPiece;
        private int portalRoomIndex;

        private MutableState(PieceNode root, RandomContinuation random, PieceType imposedPiece) {
            orderedPieces = new ArrayList<>(List.of(root));
            pendingChildren = new ArrayList<>();
            placeCounts = new EnumMap<>(PieceType.class);
            rootBox = root.boundingBox;
            this.random = random;
            this.imposedPiece = imposedPiece;
            portalRoomIndex = -1;
        }

        private MutableState(State state) {
            orderedPieces = new ArrayList<>(state.orderedPieces);
            pendingChildren = new ArrayList<>(state.pendingChildren);
            placeCounts = new EnumMap<>(state.placeCounts);
            rootBox = orderedPieces.getFirst().boundingBox;
            random = RandomContinuation.resume(state.rngInternalState, state.rngCalls);
            previousPiece = state.previousPiece;
            imposedPiece = state.imposedPiece;
            portalRoomIndex = state.portalRoomIndex;
        }

        private int count(PieceType type) {
            return placeCounts.getOrDefault(type, 0);
        }

        private State freeze() {
            return new State(orderedPieces, pendingChildren, placeCounts, previousPiece,
                    imposedPiece, portalRoomIndex, random.internalState, random.calls);
        }
    }
}
