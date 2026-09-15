package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsCatalog.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsCatalog.Vec;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsProducer.Box;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsProducer.Junction;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsProducer.Piece;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsProducer.RandomReceipt;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsProducer.Start;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Current-only mutable Trail Ruins piece carrier plus exact official raw NBT emitter. */
public final class Mc263TrailRuinsCarrier {
    private static final byte[] MAGIC = "TR263C1".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_PIECES = 512;
    private static final int MAX_JUNCTIONS = 64;
    private static final int MAX_STRING = 512;

    private Mc263TrailRuinsCarrier() {}

    public static byte[] encode(Start start) {
        if (start == null || start.empty()) throw new IllegalArgumentException("non-empty start required");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.write(MAGIC);
                out.writeLong(start.worldSeed()); out.writeInt(start.chunkX()); out.writeInt(start.chunkZ());
                writeVec(out, start.stubPosition()); writeBox(out, start.aggregateBox());
                out.writeLong(start.random().state48()); out.writeInt(start.random().worldgenCount());
                out.writeInt(start.pieces().size());
                for (Piece piece : start.pieces()) writePiece(out, piece);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    public static Start decode(byte[] bytes, Mc263TrailRuinsCatalog catalog) {
        if (bytes == null || catalog == null) throw new IllegalArgumentException("carrier/catalog required");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            byte[] magic = in.readNBytes(MAGIC.length);
            if (!Arrays.equals(magic, MAGIC)) throw new IllegalArgumentException("Trail carrier version drift");
            long seed = in.readLong(); int chunkX = in.readInt(), chunkZ = in.readInt();
            Vec stub = readVec(in); Box aggregate = readBox(in);
            RandomReceipt random = new RandomReceipt(in.readLong(), in.readInt());
            int count = bounded(in.readInt(), MAX_PIECES, "piece count");
            if (count == 0) throw new IllegalArgumentException("empty Trail carrier");
            List<Piece> pieces = new ArrayList<>(count);
            for (int ordinal = 0; ordinal < count; ordinal++) {
                Piece piece = readPiece(in, ordinal, catalog);
                pieces.add(piece);
            }
            if (in.read() != -1) throw new IllegalArgumentException("trailing Trail carrier bytes");
            Box computed = pieces.stream().map(Piece::box).reduce(Box::encapsulate).orElseThrow();
            if (!sameBox(aggregate, computed)) throw new IllegalArgumentException("aggregate box drift");
            return new Start(seed, chunkX, chunkZ, stub, pieces, aggregate, random, false);
        } catch (EOFException error) {
            throw new IllegalArgumentException("truncated Trail carrier", error);
        } catch (IOException error) {
            throw new IllegalArgumentException("invalid Trail carrier", error);
        }
    }

    public static byte[] rawPieceNbt(Piece piece) {
        if (piece == null) throw new IllegalArgumentException("piece required");
        return nbt(out -> writePieceCompound(out, piece, false));
    }

    public static byte[] rawStructureStartNbt(Start start) {
        if (start == null || start.empty()) throw new IllegalArgumentException("start required");
        return nbt(out -> {
            intTag(out, "references", 0);
            intTag(out, "ChunkZ", start.chunkZ());
            stringTag(out, "id", Mc263TrailRuinsCatalog.STRUCTURE_KEY);
            out.writeByte(9); out.writeUTF("Children"); out.writeByte(10);
            out.writeInt(start.pieces().size());
            for (Piece piece : start.pieces()) writePieceCompound(out, piece, true);
            intTag(out, "ChunkX", start.chunkX());
        });
    }

    private static byte[] nbt(NbtWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(10); out.writeUTF(""); writer.write(out); out.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void writePieceCompound(DataOutputStream out, Piece piece, boolean end)
            throws IOException {
        intArrayTag(out, "BB", new int[] {piece.box().minX(), piece.box().minY(),
                piece.box().minZ(), piece.box().maxX(), piece.box().maxY(), piece.box().maxZ()});
        intTag(out, "PosZ", piece.origin().z());
        intTag(out, "PosX", piece.origin().x());
        out.writeByte(10); out.writeUTF("pool_element");
        stringTag(out, "location", piece.template());
        stringTag(out, "processors", piece.processor());
        stringTag(out, "projection", "rigid");
        stringTag(out, "element_type", "minecraft:single_pool_element");
        out.writeByte(0);
        intTag(out, "PosY", piece.origin().y());
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
            stringTag(out, "dest_proj", junction.destinationProjection());
            out.writeByte(0);
        }
        if (end) out.writeByte(0);
    }

    private static void intTag(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(3); out.writeUTF(name); out.writeInt(value);
    }

    private static void stringTag(DataOutputStream out, String name, String value)
            throws IOException {
        out.writeByte(8); out.writeUTF(name); out.writeUTF(value);
    }

    private static void intArrayTag(DataOutputStream out, String name, int[] values)
            throws IOException {
        out.writeByte(11); out.writeUTF(name); out.writeInt(values.length);
        for (int value : values) out.writeInt(value);
    }

    private static void writePiece(DataOutputStream out, Piece piece) throws IOException {
        out.writeInt(piece.ordinal()); writeString(out, piece.template()); writeString(out, piece.processor());
        writeVec(out, piece.origin()); out.writeByte(piece.rotation().ordinal());
        out.writeInt(piece.groundLevelDelta()); writeBox(out, piece.box());
        out.writeInt(piece.junctions().size());
        for (Junction junction : piece.junctions()) {
            out.writeInt(junction.sourceX()); out.writeInt(junction.sourceGroundY());
            out.writeInt(junction.sourceZ()); out.writeInt(junction.deltaY());
            writeString(out, junction.destinationProjection());
        }
    }

    private static Piece readPiece(DataInputStream in, int expectedOrdinal,
            Mc263TrailRuinsCatalog catalog) throws IOException {
        if (in.readInt() != expectedOrdinal) throw new IllegalArgumentException("piece order drift");
        String template = readString(in), processor = readString(in);
        catalog.template(template);
        if (!catalog.pools().stream().flatMap(pool -> pool.elements().stream()).anyMatch(
                element -> element.template().equals(template) && element.processor().equals(processor))) {
            throw new IllegalArgumentException("unknown Trail template/processor pair");
        }
        Vec origin = readVec(in); int rotation = in.readUnsignedByte();
        if (rotation >= Rotation.values().length) throw new IllegalArgumentException("rotation drift");
        int groundDelta = in.readInt(); Box box = readBox(in);
        int junctionCount = bounded(in.readInt(), MAX_JUNCTIONS, "junction count");
        List<Junction> junctions = new ArrayList<>(junctionCount);
        for (int index = 0; index < junctionCount; index++) {
            int x = in.readInt(), groundY = in.readInt(), z = in.readInt(), deltaY = in.readInt();
            String projection = readString(in);
            if (!"rigid".equals(projection)) throw new IllegalArgumentException("projection drift");
            junctions.add(new Junction(x, groundY, z, deltaY, projection));
        }
        return new Piece(expectedOrdinal, template, processor, origin, Rotation.values()[rotation],
                groundDelta, box, junctions);
    }

    private static void writeVec(DataOutputStream out, Vec value) throws IOException {
        out.writeInt(value.x()); out.writeInt(value.y()); out.writeInt(value.z());
    }
    private static Vec readVec(DataInputStream in) throws IOException {
        return new Vec(in.readInt(), in.readInt(), in.readInt());
    }
    private static void writeBox(DataOutputStream out, Box box) throws IOException {
        out.writeInt(box.minX()); out.writeInt(box.minY()); out.writeInt(box.minZ());
        out.writeInt(box.maxX()); out.writeInt(box.maxY()); out.writeInt(box.maxZ());
    }
    private static Box readBox(DataInputStream in) throws IOException {
        return new Box(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
    }
    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING) throw new IllegalArgumentException("Trail string bound");
        out.writeShort(bytes.length); out.write(bytes);
    }
    private static String readString(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length > MAX_STRING) throw new IllegalArgumentException("Trail string bound");
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new EOFException("Trail string");
        return new String(bytes, StandardCharsets.UTF_8);
    }
    private static int bounded(int value, int maximum, String label) {
        if (value < 0 || value > maximum) throw new IllegalArgumentException(label + " bound");
        return value;
    }
    private static boolean sameBox(Box left, Box right) {
        return left.minX() == right.minX() && left.minY() == right.minY()
                && left.minZ() == right.minZ() && left.maxX() == right.maxX()
                && left.maxY() == right.maxY() && left.maxZ() == right.maxZ();
    }
    @FunctionalInterface private interface NbtWriter { void write(DataOutputStream out) throws IOException; }
}
