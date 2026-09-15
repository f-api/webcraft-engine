package com.gameexpert.terrain.mc.structure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Exact pinned-26.3 retry, start construction, and vertical relocation for strongholds. */
public final class Mc263StrongholdStartGenerator {
    private static final int SEA_LEVEL = 63;
    private static final int MIN_Y = -64;
    private static final int VERTICAL_OFFSET = 10;
    private static final int MAX_ALLOWED_Y = SEA_LEVEL - VERTICAL_OFFSET;
    private static final int GRAPH_MAGIC = 0x53483236;
    private static final int GRAPH_VERSION = 1;

    private Mc263StrongholdStartGenerator() { }

    /** Generates until the official graph contains its portal room. */
    public static Result generate(long worldSeed, int chunkX, int chunkZ) {
        return generate(worldSeed, chunkX, chunkZ, 0);
    }

    /** Generates and materializes the current canonical STR start in one immutable result. */
    public static Result generate(long worldSeed, int chunkX, int chunkZ, int references) {
        if (references < 0) throw new IllegalArgumentException("negative reference count");
        Mc263StrongholdStartPieceFactoryAdapter factory =
                Mc263StrongholdStartPieceFactoryAdapter.official();
        long attempt = 0;
        while (true) {
            long mixedSeed = largeFeatureSeed(worldSeed + attempt, chunkX, chunkZ);
            int rootX = chunkX * 16 + 2;
            int rootZ = chunkZ * 16 + 2;
            Mc263StrongholdGraphCarrier.State graph =
                    Mc263StrongholdStartPieceFactoryAdapter.begin(rootX, rootZ, mixedSeed);
            graph = Mc263StrongholdGraphCarrier.expandFully(graph, factory);
            Relocation relocation = moveBelowSeaLevel(graph);
            if (graph.portalRoomIndex() >= 0) {
                return new Result(relocation.state(), attempt, mixedSeed,
                        relocation.verticalOffset(), materialize(relocation.state(), chunkX,
                                chunkZ, references));
            }
            attempt++;
        }
    }

    private static Mc263StructureCarrier.ValidStart materialize(
            Mc263StrongholdGraphCarrier.State state, int chunkX, int chunkZ, int references) {
        java.util.ArrayList<Mc263StructureCarrier.Piece> pieces = new java.util.ArrayList<>();
        for (Mc263StrongholdGraphCarrier.PieceNode node : state.orderedPieces()) {
            Mc263StrongholdGraphCarrier.BoundingBox box = node.boundingBox();
            pieces.add(new Mc263StructureCarrier.Piece(node.pieceType(),
                    new Mc263StructureCarrier.BoundingBox(box.minX(), box.minY(), box.minZ(),
                            box.maxX(), box.maxY(), box.maxZ()), false,
                    Mc263StructureCarrier.Projection.NOT_APPLICABLE, 0, List.of(),
                    new Mc263StructureCarrier.PiecePayload(node.mutableNbt())));
        }
        Mc263StructureCarrier.BoundingBox bounds = pieces.getFirst().boundingBox();
        int minX = bounds.minX(), minY = bounds.minY(), minZ = bounds.minZ();
        int maxX = bounds.maxX(), maxY = bounds.maxY(), maxZ = bounds.maxZ();
        for (int index = 1; index < pieces.size(); index++) {
            Mc263StructureCarrier.BoundingBox box = pieces.get(index).boundingBox();
            minX = Math.min(minX, box.minX()); minY = Math.min(minY, box.minY());
            minZ = Math.min(minZ, box.minZ()); maxX = Math.max(maxX, box.maxX());
            maxY = Math.max(maxY, box.maxY()); maxZ = Math.max(maxZ, box.maxZ());
        }
        return new Mc263StructureCarrier.ValidStart("minecraft:stronghold@" + chunkX + ","
                + chunkZ, chunkX, chunkZ, references,
                new Mc263StructureCarrier.BoundingBox(minX, minY, minZ, maxX, maxY, maxZ)
                        .inflatedBy(12),
                pieces);
    }

    /** Applies StructurePiecesBuilder.moveBelowSeaLevel(63, -64, random, 10). */
    public static Relocation moveBelowSeaLevel(Mc263StrongholdGraphCarrier.State state) {
        Objects.requireNonNull(state, "state");
        List<Mc263StrongholdGraphCarrier.PieceNode> pieces = state.orderedPieces();
        if (pieces.isEmpty()) throw new IllegalArgumentException("empty stronghold graph");

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Mc263StrongholdGraphCarrier.PieceNode piece : pieces) {
            minY = Math.min(minY, piece.boundingBox().minY());
            maxY = Math.max(maxY, piece.boundingBox().maxY());
        }
        int height = Math.addExact(Math.subtractExact(maxY, minY), 1);
        int target = Math.addExact(height, Math.addExact(MIN_Y, 1));
        Mc263StrongholdGraphCarrier.RandomContinuation random =
                Mc263StrongholdGraphCarrier.RandomContinuation.resume(
                        state.rngInternalState(), state.rngCalls());
        if (target < MAX_ALLOWED_Y) target += random.nextInt(MAX_ALLOWED_Y - target);
        int dy = Math.subtractExact(target, maxY);
        return new Relocation(translate(state, dy, random), dy);
    }

    private static long largeFeatureSeed(long seed, int chunkX, int chunkZ) {
        Random random = new Random();
        random.setSeed(seed);
        long xScale = random.nextLong();
        long zScale = random.nextLong();
        long mixed = (long) chunkX * xScale ^ (long) chunkZ * zScale ^ seed;
        random.setSeed(mixed);
        return mixed;
    }

    private static Mc263StrongholdGraphCarrier.State translate(
            Mc263StrongholdGraphCarrier.State state, int dy,
            Mc263StrongholdGraphCarrier.RandomContinuation random) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(
                Mc263StrongholdGraphCarrier.encode(state)))) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                require(in.readInt() == GRAPH_MAGIC && in.readInt() == GRAPH_VERSION);
                out.writeInt(GRAPH_MAGIC);
                out.writeInt(GRAPH_VERSION);
                in.readLong();
                in.readLong();
                out.writeLong(random.internalState());
                out.writeLong(random.calls());
                out.writeInt(in.readInt()); // previous piece
                out.writeInt(in.readInt()); // imposed piece
                out.writeInt(in.readInt()); // portal index
                int pieceCount = in.readInt();
                out.writeInt(pieceCount);
                for (int index = 0; index < pieceCount; index++) translatePiece(in, out, dy);
                in.transferTo(out);
            }
            require(in.available() == 0);
            return Mc263StrongholdGraphCarrier.decode(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory stronghold relocation failed", exception);
        }
    }

    private static void translatePiece(DataInputStream in, DataOutputStream out, int dy)
            throws IOException {
        out.writeUTF(in.readUTF());
        int minX = in.readInt();
        int minY = in.readInt();
        int minZ = in.readInt();
        int maxX = in.readInt();
        int maxY = in.readInt();
        int maxZ = in.readInt();
        out.writeInt(minX);
        out.writeInt(Math.addExact(minY, dy));
        out.writeInt(minZ);
        out.writeInt(maxX);
        out.writeInt(Math.addExact(maxY, dy));
        out.writeInt(maxZ);
        out.writeInt(in.readInt()); // generation depth
        out.writeInt(in.readInt()); // orientation
        int payloadLength = in.readInt();
        byte[] payload = in.readNBytes(payloadLength);
        require(payload.length == payloadLength);
        translateBoundingBoxTag(payload, dy);
        out.writeInt(payload.length);
        out.write(payload);
    }

    private static void translateBoundingBoxTag(byte[] payload, int dy) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            require(in.readUnsignedByte() == 10 && in.readUnsignedShort() == 0);
            require(in.readUnsignedByte() == 11 && "BB".equals(in.readUTF()));
            require(in.readInt() == 6);
            int valuesOffset = payload.length - in.available();
            readInt(payload, valuesOffset);
            writeInt(payload, valuesOffset + 4,
                    Math.addExact(readInt(payload, valuesOffset + 4), dy));
            readInt(payload, valuesOffset + 8);
            readInt(payload, valuesOffset + 12);
            writeInt(payload, valuesOffset + 16,
                    Math.addExact(readInt(payload, valuesOffset + 16), dy));
            readInt(payload, valuesOffset + 20);
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        require(offset >= 0 && offset + 4 <= bytes.length);
        return (bytes[offset] & 0xff) << 24 | (bytes[offset + 1] & 0xff) << 16
                | (bytes[offset + 2] & 0xff) << 8 | bytes[offset + 3] & 0xff;
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("noncanonical stronghold graph");
    }

    public record Result(Mc263StrongholdGraphCarrier.State state, long attempt,
            long mixedSeed, int verticalOffset, Mc263StructureCarrier.ValidStart start) {
        public Result {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(start, "stronghold STR start");
            if (attempt < 0 || state.portalRoomIndex() < 0
                    || !state.pendingChildren().isEmpty()) {
                throw new IllegalArgumentException("incomplete stronghold start result");
            }
        }
    }

    public record Relocation(Mc263StrongholdGraphCarrier.State state, int verticalOffset) {
        public Relocation {
            Objects.requireNonNull(state, "state");
        }

        public int dy() {
            return verticalOffset;
        }
    }
}
