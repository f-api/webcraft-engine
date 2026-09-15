package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Public strict codec adapters for all piece types owned by this stronghold batch. */
public final class Mc263StrongholdStartPieceCodec {
    private Mc263StrongholdStartPieceCodec() { }

    public static Mc263StrongholdStartPieceFactoryAdapter.PieceData decode(
            Mc263StrongholdGraphCarrier.PieceNode piece) {
        Objects.requireNonNull(piece, "piece");
        return Mc263StrongholdStartPieceFactoryAdapter.decode(piece.type(), piece.boundingBox(),
                piece.generationDepth(), piece.orientation(), piece.mutableNbt());
    }

    public static Mc263StrongholdStartPieceFactoryAdapter.PieceData decode(Piece piece) {
        Objects.requireNonNull(piece, "piece");
        if (piece.poolElement()
                || piece.projection() != Mc263StructureCarrier.Projection.NOT_APPLICABLE
                || piece.groundLevelDelta() != 0 || !piece.junctions().isEmpty()) {
            throw new IllegalArgumentException("expected exact non-pool stronghold piece");
        }
        Mc263StrongholdGraphCarrier.PieceType type = type(piece.pieceType());
        Mc263StructureCarrier.BoundingBox b = piece.boundingBox();
        byte[] payload = piece.persistedPayload().binaryNbtCompound();
        Header header = header(payload);
        Mc263StrongholdGraphCarrier.BoundingBox box = new Mc263StrongholdGraphCarrier.BoundingBox(
                b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
        if (!Arrays.equals(header.box, new int[]{b.minX(), b.minY(), b.minZ(),
                b.maxX(), b.maxY(), b.maxZ()})) {
            throw new IllegalArgumentException("stronghold BB disagrees with carrier piece");
        }
        return Mc263StrongholdStartPieceFactoryAdapter.decode(type, box, header.depth,
                Mc263StrongholdGraphCarrier.Orientation.fromNbtId(header.orientation), payload);
    }

    public static byte[] encode(Mc263StrongholdStartPieceFactoryAdapter.PieceData data) {
        return Mc263StrongholdStartPieceFactoryAdapter.encode(data);
    }

    public static List<String> ownedPieceTypes() {
        return List.of("minecraft:shs", "minecraft:shph", "minecraft:shrc",
                "minecraft:shssd", "minecraft:shsd", "minecraft:sh5c", "minecraft:shcc",
                "minecraft:shli", "minecraft:shpr", "minecraft:shstart");
    }

    private static Mc263StrongholdGraphCarrier.PieceType type(String id) {
        for (Mc263StrongholdGraphCarrier.PieceType value
                : Mc263StrongholdGraphCarrier.PieceType.values()) {
            if (value.id().equals(id) && ownedPieceTypes().contains(id)) return value;
        }
        throw new IllegalArgumentException("unowned stronghold piece type " + id);
    }

    private static Header header(byte[] payload) {
        // Header facts are located without accepting alternate compounds; the full strict decoder
        // immediately re-reads and byte-compares every tag in canonical order.
        try (java.io.DataInputStream in = new java.io.DataInputStream(
                new java.io.ByteArrayInputStream(payload))) {
            require(in.readUnsignedByte() == 10 && in.readUnsignedShort() == 0);
            require(in.readUnsignedByte() == 11 && "BB".equals(in.readUTF()));
            require(in.readInt() == 6);
            int[] box = new int[6]; for (int i = 0; i < 6; i++) box[i] = in.readInt();
            Integer depth = null;
            Integer orientation = null;
            while (true) {
                int type = in.readUnsignedByte();
                if (type == 0) break;
                String name = in.readUTF();
                if (type == 1) in.readByte();
                else if (type == 3) {
                    int value = in.readInt();
                    if ("GD".equals(name)) { require(depth == null); depth = value; }
                    if ("O".equals(name)) { require(orientation == null); orientation = value; }
                } else if (type == 8) in.readUTF();
                else throw new IllegalStateException("unexpected stronghold header tag type");
            }
            require(depth != null && orientation != null && in.available() == 0);
            return new Header(box, depth, orientation);
        } catch (java.io.IOException | IllegalStateException exception) {
            throw new IllegalArgumentException("invalid stronghold compound header", exception);
        }
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalStateException("invalid stronghold compound header");
    }

    private static final class Header {
        final int[] box; final int depth; final int orientation;
        Header(int[] box, int depth, int orientation) {
            this.box = box; this.depth = depth; this.orientation = orientation;
        }
    }
}
