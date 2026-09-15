package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263ShipwreckCatalog.Template;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Immutable one-piece Shipwreck start and its exact mutable persisted-NBT boundary. */
public record Mc263ShipwreckCarrier(String structureKey, long worldSeed, int chunkX, int chunkZ,
        boolean beached, Template template, Rotation rotation, int templateX, int templateY,
        int templateZ, BoundingBox boundingBox, boolean heightAdjusted,
        long generationState48, List<Long> generationContinuation) {
    public static final String PIECE_TYPE = "minecraft:shipwreck";
    public static final String OCEAN = "minecraft:shipwreck";
    public static final String BEACHED = "minecraft:shipwreck_beached";

    public Mc263ShipwreckCarrier {
        if (!structureKey.equals(beached ? BEACHED : OCEAN)) {
            throw new IllegalArgumentException("shipwreck key/isBeached mismatch");
        }
        Objects.requireNonNull(template, "shipwreck template");
        Objects.requireNonNull(rotation, "shipwreck rotation");
        Objects.requireNonNull(boundingBox, "shipwreck bounding box");
        generationContinuation = List.copyOf(generationContinuation);
        if (generationState48 < 0 || generationState48 >= (1L << 48)
                || generationContinuation.size() != 8) {
            throw new IllegalArgumentException("invalid shipwreck generation continuation");
        }
        BoundingBox expected = bounds(template, rotation, templateX, templateY, templateZ);
        if (!expected.equals(boundingBox)) {
            throw new IllegalArgumentException("shipwreck bounds do not match template transform");
        }
        if (!heightAdjusted && templateY != 90) {
            throw new IllegalArgumentException("unadjusted shipwreck anchor must be Y=90");
        }
    }

    public enum Rotation { NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90 }
    public record BlockPos(int x, int y, int z) {
        public BlockPos below() { return new BlockPos(x, Math.subtractExact(y, 1), z); }
    }
    public record BoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public BoundingBox {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted shipwreck bounds");
            }
        }
        public boolean contains(BlockPos p) {
            return p.x >= minX && p.x <= maxX && p.y >= minY && p.y <= maxY
                    && p.z >= minZ && p.z <= maxZ;
        }
    }

    public Mc263ShipwreckCarrier projectTo(int y) {
        return new Mc263ShipwreckCarrier(structureKey, worldSeed, chunkX, chunkZ, beached,
                template, rotation, templateX, y, templateZ,
                bounds(template, rotation, templateX, y, templateZ), true,
                generationState48, generationContinuation);
    }

    public BlockPos worldPosition(int localX, int localY, int localZ) {
        if (localX < 0 || localX >= template.sizeX() || localY < 0
                || localY >= template.sizeY() || localZ < 0 || localZ >= template.sizeZ()) {
            throw new IllegalArgumentException("shipwreck local position outside template");
        }
        int x;
        int z;
        switch (rotation) {
            case NONE -> { x = localX; z = localZ; }
            case CLOCKWISE_90 -> {
                x = Mc263ShipwreckCatalog.PIVOT_X + Mc263ShipwreckCatalog.PIVOT_Z - localZ;
                z = Mc263ShipwreckCatalog.PIVOT_Z - Mc263ShipwreckCatalog.PIVOT_X + localX;
            }
            case CLOCKWISE_180 -> {
                x = 2 * Mc263ShipwreckCatalog.PIVOT_X - localX;
                z = 2 * Mc263ShipwreckCatalog.PIVOT_Z - localZ;
            }
            case COUNTERCLOCKWISE_90 -> {
                x = localZ + Mc263ShipwreckCatalog.PIVOT_X - Mc263ShipwreckCatalog.PIVOT_Z;
                z = Mc263ShipwreckCatalog.PIVOT_Z + Mc263ShipwreckCatalog.PIVOT_X - localX;
            }
            default -> throw new AssertionError(rotation);
        }
        return new BlockPos(Math.addExact(templateX, x), Math.addExact(templateY, localY),
                Math.addExact(templateZ, z));
    }

    public byte[] canonicalPieceNbt() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(10); out.writeShort(0);
                out.writeByte(11); out.writeUTF("BB"); out.writeInt(6);
                out.writeInt(boundingBox.minX); out.writeInt(boundingBox.minY);
                out.writeInt(boundingBox.minZ); out.writeInt(boundingBox.maxX);
                out.writeInt(boundingBox.maxY); out.writeInt(boundingBox.maxZ);
                string(out, "Rot", rotation.name());
                byteTag(out, "isBeached", beached);
                string(out, "id", PIECE_TYPE);
                integer(out, "TPY", templateY);
                integer(out, "GD", 0);
                integer(out, "TPX", templateX);
                integer(out, "O", 2);
                integer(out, "TPZ", templateZ);
                string(out, "Template", template.id());
                byteTag(out, "height_adjusted", heightAdjusted);
                out.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory shipwreck piece NBT failed", impossible);
        }
    }

    public byte[] canonicalStructureStartNbt(int references) {
        if (references < 0) throw new IllegalArgumentException("negative shipwreck references");
        byte[] piece = canonicalPieceNbt();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(piece.length + 96);
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(10); out.writeShort(0);
                integer(out, "references", references);
                integer(out, "ChunkZ", chunkZ);
                string(out, "id", structureKey);
                out.writeByte(9); out.writeUTF("Children"); out.writeByte(10); out.writeInt(1);
                // List compounds omit the root type/name prefix and retain the trailing END.
                out.write(piece, 3, piece.length - 3);
                integer(out, "ChunkX", chunkX);
                out.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory shipwreck start NBT failed", impossible);
        }
    }

    /** Strict current-only decoder for the raw official ShipwreckPiece compound. */
    public static Mc263ShipwreckCarrier decodePieceNbt(String structureKey, long worldSeed,
            int chunkX, int chunkZ, long generationState48,
            List<Long> generationContinuation, byte[] payload) {
        Objects.requireNonNull(payload, "shipwreck piece NBT");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            require(in.readUnsignedByte() == 10 && in.readUnsignedShort() == 0);
            requireTag(in, 11, "BB"); require(in.readInt() == 6);
            int[] box = new int[6]; for (int i = 0; i < 6; i++) box[i] = in.readInt();
            requireTag(in, 8, "Rot"); Rotation rotation = Rotation.valueOf(in.readUTF());
            requireTag(in, 1, "isBeached"); boolean beached = switch (in.readUnsignedByte()) {
                case 0 -> false; case 1 -> true;
                default -> throw new IllegalArgumentException("noncanonical isBeached byte");
            };
            requireTag(in, 8, "id"); require(PIECE_TYPE.equals(in.readUTF()));
            requireTag(in, 3, "TPY"); int templateY = in.readInt();
            requireInt(in, "GD", 0);
            requireTag(in, 3, "TPX"); int templateX = in.readInt();
            requireInt(in, "O", 2);
            requireTag(in, 3, "TPZ"); int templateZ = in.readInt();
            requireTag(in, 8, "Template"); Template template = Mc263ShipwreckCatalog.require(in.readUTF());
            requireTag(in, 1, "height_adjusted"); boolean adjusted = switch (in.readUnsignedByte()) {
                case 0 -> false; case 1 -> true;
                default -> throw new IllegalArgumentException("noncanonical height_adjusted byte");
            };
            require(in.readUnsignedByte() == 0 && in.available() == 0);
            require(templateX == Math.multiplyExact(chunkX, 16)
                    && templateZ == Math.multiplyExact(chunkZ, 16));
            BoundingBox bounds = new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]);
            Mc263ShipwreckCarrier value = new Mc263ShipwreckCarrier(structureKey, worldSeed,
                    chunkX, chunkZ, beached, template, rotation, templateX, templateY, templateZ,
                    bounds, adjusted, generationState48, generationContinuation);
            require(java.util.Arrays.equals(payload, value.canonicalPieceNbt()));
            return value;
        } catch (IOException | IllegalArgumentException error) {
            if (error instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalArgumentException("malformed shipwreck piece NBT", error);
        }
    }

    public static BoundingBox bounds(Template template, Rotation rotation,
            int originX, int originY, int originZ) {
        Mc263ShipwreckCarrier.BlockPos[] corners = {
            transformed(rotation, 0, 0), transformed(rotation, template.sizeX() - 1, 0),
            transformed(rotation, 0, template.sizeZ() - 1),
            transformed(rotation, template.sizeX() - 1, template.sizeZ() - 1)};
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : corners) {
            minX = Math.min(minX, p.x); minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x); maxZ = Math.max(maxZ, p.z);
        }
        return new BoundingBox(Math.addExact(originX, minX), originY,
                Math.addExact(originZ, minZ), Math.addExact(originX, maxX),
                Math.addExact(originY, template.sizeY() - 1), Math.addExact(originZ, maxZ));
    }

    private static BlockPos transformed(Rotation rotation, int x, int z) {
        return switch (rotation) {
            case NONE -> new BlockPos(x, 0, z);
            case CLOCKWISE_90 -> new BlockPos(Mc263ShipwreckCatalog.PIVOT_X
                    + Mc263ShipwreckCatalog.PIVOT_Z - z, 0,
                    Mc263ShipwreckCatalog.PIVOT_Z - Mc263ShipwreckCatalog.PIVOT_X + x);
            case CLOCKWISE_180 -> new BlockPos(2 * Mc263ShipwreckCatalog.PIVOT_X - x, 0,
                    2 * Mc263ShipwreckCatalog.PIVOT_Z - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(z + Mc263ShipwreckCatalog.PIVOT_X
                    - Mc263ShipwreckCatalog.PIVOT_Z, 0,
                    Mc263ShipwreckCatalog.PIVOT_Z + Mc263ShipwreckCatalog.PIVOT_X - x);
        };
    }

    private static void string(DataOutputStream out, String name, String value) throws IOException {
        out.writeByte(8); out.writeUTF(name); out.writeUTF(value);
    }
    private static void integer(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(3); out.writeUTF(name); out.writeInt(value);
    }
    private static void byteTag(DataOutputStream out, String name, boolean value) throws IOException {
        out.writeByte(1); out.writeUTF(name); out.writeByte(value ? 1 : 0);
    }
    private static void requireTag(DataInputStream in, int type, String name) throws IOException {
        require(in.readUnsignedByte() == type && name.equals(in.readUTF()));
    }
    private static void requireInt(DataInputStream in, String name, int value) throws IOException {
        requireTag(in, 3, name); require(in.readInt() == value);
    }
    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("noncanonical shipwreck piece NBT");
    }
}
