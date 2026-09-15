package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.PiecePayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Projection;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Procedural layout, persistence and seabed-projection program for the pinned
 * {@code 26.3-snapshot-7} cold and warm ocean-ruin family.
 *
 * <p>No Mojang template payload is embedded here. Template geometry is accepted only through a
 * descriptor-bound procedural grammar whose key, dimensions, binary-template SHA-256 and marker
 * order agree with the official oracle catalog. Registration in the shared canonical caller stays
 * outside this structure-owned boundary.</p>
 */
public final class Mc263OceanRuinProgram {
    public static final String VERSION = "26.3-snapshot-7";
    public static final String OUTER_SERVER_SHA1 =
            "06157fedd67ff4dd6e0e6fa4a9dd0af296f0dd61";
    public static final String ORACLE_SHA256 =
            "c5a268efd28eb381307aa2dff927a22f0bf3ff2fbf4a61085f9edad7c2c65fd6";
    public static final String ORACLE_CONTRACT_SHA256 =
            "0a72307410fa8550770a34e1fd6d5d6535b6b28114dca15c1d60e9c442593f75";
    public static final String ORACLE_SOURCE_SHA256 =
            "2a6bc2cbb8510ba27bd89e0037a2b9128313e91ea462cb8973c6bf8d9a136de9";
    public static final String COLD_STRUCTURE = "minecraft:ocean_ruin_cold";
    public static final String WARM_STRUCTURE = "minecraft:ocean_ruin_warm";
    public static final String PIECE_TYPE = "minecraft:orp";
    public static final float LARGE_PROBABILITY = 0.3F;
    public static final float CLUSTER_PROBABILITY = 0.9F;
    public static final int INITIAL_Y = 90;

    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long ADDEND = 0xBL;
    private static final long MASK = (1L << 48) - 1L;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final List<String> WARM_SMALL = numbered("warm_", 1, 8);
    private static final List<String> COLD_BRICK_SMALL = numbered("brick_", 1, 8);
    private static final List<String> COLD_CRACKED_SMALL = numbered("cracked_", 1, 8);
    private static final List<String> COLD_MOSSY_SMALL = numbered("mossy_", 1, 8);
    private static final List<String> WARM_BIG = numbered("big_warm_", 4, 7);
    private static final List<String> COLD_BRICK_BIG = named("big_brick_", 1, 2, 3, 8);
    private static final List<String> COLD_CRACKED_BIG = named("big_cracked_", 1, 2, 3, 8);
    private static final List<String> COLD_MOSSY_BIG = named("big_mossy_", 1, 2, 3, 8);
    private static final Set<String> PROCESSORS = Set.of(
            "minecraft:block_rot", "minecraft:ignore_structure_and_air",
            "minecraft:capped_archaeology");

    private Mc263OceanRuinProgram() { }

    public enum Type { COLD, WARM }
    public enum Rotation { NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90 }
    public enum MarkerKind { CHEST, DROWNED }

    public record BlockPos(int x, int y, int z) {
        BlockPos offset(int dx, int dy, int dz) {
            return new BlockPos(Math.addExact(x, dx), Math.addExact(y, dy),
                    Math.addExact(z, dz));
        }
    }

    public record Marker(BlockPos localPosition, MarkerKind kind) {
        public Marker {
            Objects.requireNonNull(localPosition, "ocean-ruin marker position");
            Objects.requireNonNull(kind, "ocean-ruin marker kind");
        }
    }

    /** Hash-only identity and procedural marker grammar; no template block palette is retained. */
    public record TemplateDescriptor(String key, int sizeX, int sizeY, int sizeZ,
            int binaryLength, String binarySha256, List<Marker> markers) {
        public TemplateDescriptor {
            if (!knownTemplateKey(key) || sizeX <= 0 || sizeY <= 0 || sizeZ <= 0
                    || binaryLength <= 0 || !SHA256.matcher(binarySha256).matches()) {
                throw new IllegalArgumentException("invalid ocean-ruin template descriptor");
            }
            markers = List.copyOf(markers);
            for (Marker marker : markers) {
                Objects.requireNonNull(marker, "ocean-ruin marker");
                BlockPos p = marker.localPosition();
                if (p.x() < 0 || p.y() < 0 || p.z() < 0 || p.x() >= sizeX
                        || p.y() >= sizeY || p.z() >= sizeZ) {
                    throw new IllegalArgumentException("ocean-ruin marker outside template");
                }
            }
        }
    }

    @FunctionalInterface
    public interface TemplateCatalog {
        TemplateDescriptor require(String templateKey);
    }

    public record Processor(String key, float integrity, String inputBlock,
            String outputBlock, String lootTable, int cap) {
        public Processor {
            if (!PROCESSORS.contains(key)) {
                throw new IllegalArgumentException("unknown ocean-ruin processor: " + key);
            }
        }
    }

    public record PiecePlan(int ordinal, TemplateDescriptor template, BlockPos templatePosition,
            Rotation rotation, float integrity, boolean large, Type type,
            BoundingBox boundingBox, List<Processor> processors, byte[] persistedNbt) {
        public PiecePlan {
            if (ordinal < 0 || !List.of(0.5F, 0.7F, 0.8F, 0.9F).contains(integrity)) {
                throw new IllegalArgumentException("invalid ocean-ruin piece facts");
            }
            Objects.requireNonNull(template); Objects.requireNonNull(templatePosition);
            Objects.requireNonNull(rotation); Objects.requireNonNull(type);
            Objects.requireNonNull(boundingBox);
            processors = List.copyOf(processors);
            requireProcessorOrder(processors, integrity, type);
            persistedNbt = persistedNbt.clone();
        }
        @Override public byte[] persistedNbt() { return persistedNbt.clone(); }
    }

    public record ClusterAttempt(int ordinal, int candidateIndex, BlockPos position,
            Rotation rotation, BoundingBox collisionBox, boolean rejectedByCentralCollision,
            List<String> selectedTemplates) {
        public ClusterAttempt {
            if (ordinal < 0 || candidateIndex < 0) {
                throw new IllegalArgumentException("invalid ocean-ruin cluster attempt");
            }
            selectedTemplates = List.copyOf(selectedTemplates);
            if (rejectedByCentralCollision != selectedTemplates.isEmpty()) {
                throw new IllegalArgumentException("ocean-ruin collision/template mismatch");
            }
        }
    }

    public record RngContinuation(long state48, long[] nextLongs) {
        public RngContinuation {
            if (state48 < 0 || state48 > MASK || nextLongs.length != 8) {
                throw new IllegalArgumentException("invalid ocean-ruin RNG continuation");
            }
            nextLongs = nextLongs.clone();
        }
        @Override public long[] nextLongs() { return nextLongs.clone(); }
    }

    public record Plan(Type type, int originX, int originZ, Rotation mainRotation,
            boolean large, boolean clusterAccepted, List<PiecePlan> pieces,
            List<ClusterAttempt> clusterAttempts, BoundingBox boundingBox,
            byte[] persistedStartNbt, RngContinuation continuation) {
        public Plan {
            pieces = List.copyOf(pieces); clusterAttempts = List.copyOf(clusterAttempts);
            if (pieces.isEmpty() || pieces.getFirst().ordinal() != 0) {
                throw new IllegalArgumentException("empty/noncanonical ocean-ruin plan");
            }
            for (int index = 0; index < pieces.size(); index++) {
                if (pieces.get(index).ordinal() != index || pieces.get(index).type() != type) {
                    throw new IllegalArgumentException("ocean-ruin piece order/type drift");
                }
            }
            persistedStartNbt = persistedStartNbt.clone();
        }
        @Override public byte[] persistedStartNbt() { return persistedStartNbt.clone(); }
        public String structureKey() { return Mc263OceanRuinProgram.structureKey(type); }
    }

    /** Exact {@code LegacyRandomSource} implementation, including rejection sampling. */
    public static final class LegacyRandom {
        private long state;
        private int draws;

        public LegacyRandom(long seed) { state = (seed ^ MULTIPLIER) & MASK; }
        private LegacyRandom(long state48, int draws) {
            if (state48 < 0 || state48 > MASK || draws < 0) throw new IllegalArgumentException();
            state = state48; this.draws = draws;
        }
        public LegacyRandom fork() { return new LegacyRandom(state, draws); }
        public void commitFrom(LegacyRandom completed) {
            Objects.requireNonNull(completed); state = completed.state; draws = completed.draws;
        }
        private int next(int bits) {
            state = (state * MULTIPLIER + ADDEND) & MASK; draws++;
            return (int) (state >>> (48 - bits));
        }
        public float nextFloat() { return next(24) / ((float) (1 << 24)); }
        public int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
            if ((bound & -bound) == bound) return (int) ((bound * (long) next(31)) >> 31);
            int bits, value;
            do { bits = next(31); value = bits % bound; }
            while (bits - value + (bound - 1) < 0);
            return value;
        }
        public long nextLong() { return ((long) next(32) << 32) + next(32); }
        public long state48() { return state; }
        public int drawCount() { return draws; }
        public RngContinuation continuation() {
            LegacyRandom copy = fork(); long[] words = new long[8];
            for (int index = 0; index < words.length; index++) words[index] = copy.nextLong();
            return new RngContinuation(state, words);
        }
    }

    /** Plans the complete official main/cluster piece graph and consumes the supplied RNG. */
    public static Plan plan(long layoutSeed, int originX, int originZ, Rotation rotation,
            Type type, TemplateCatalog catalog) {
        return plan(new LegacyRandom(layoutSeed), originX, originZ, rotation, type, catalog);
    }

    public static Plan plan(LegacyRandom random, int originX, int originZ, Rotation rotation,
            Type type, TemplateCatalog catalog) {
        Objects.requireNonNull(random, "ocean-ruin layout RNG");
        Objects.requireNonNull(rotation, "ocean-ruin rotation");
        Objects.requireNonNull(type, "ocean-ruin type");
        Objects.requireNonNull(catalog, "ocean-ruin template catalog");
        LegacyRandom staged = random.fork();
        boolean large = staged.nextFloat() <= LARGE_PROBABILITY;
        ArrayList<PiecePlan> pieces = new ArrayList<>();
        addLocation(pieces, new BlockPos(originX, INITIAL_Y, originZ), rotation, type, large,
                large ? 0.9F : 0.8F, staged, catalog);
        boolean cluster = large && staged.nextFloat() <= CLUSTER_PROBABILITY;
        ArrayList<ClusterAttempt> attempts = new ArrayList<>();
        if (cluster) addCluster(pieces, attempts, originX, originZ, rotation, type, staged, catalog);
        BoundingBox aggregate = aggregate(pieces);
        byte[] startNbt = persistedStartNbt(type, Math.floorDiv(originX, 16),
                Math.floorDiv(originZ, 16), pieces, 0);
        Plan result = new Plan(type, originX, originZ, rotation, large, cluster, pieces, attempts,
                aggregate, startNbt, staged.continuation());
        random.commitFrom(staged);
        return result;
    }

    private static void addCluster(List<PiecePlan> pieces, List<ClusterAttempt> attempts,
            int originX, int originZ, Rotation mainRotation, Type type, LegacyRandom random,
            TemplateCatalog catalog) {
        BlockPos origin = new BlockPos(originX, INITIAL_Y, originZ);
        BlockPos mainFar = transform(origin, new BlockPos(15, 0, 15), mainRotation);
        BoundingBox central = corners(origin, mainFar);
        BlockPos corner = new BlockPos(Math.min(origin.x(), mainFar.x()), INITIAL_Y,
                Math.min(origin.z(), mainFar.z()));
        ArrayList<BlockPos> candidates = allPositions(random, corner);
        int count = inclusive(random, 4, 8);
        for (int ordinal = 0; ordinal < count && !candidates.isEmpty(); ordinal++) {
            int candidateIndex = random.nextInt(candidates.size());
            BlockPos candidate = candidates.remove(candidateIndex);
            Rotation rotation = Rotation.values()[random.nextInt(4)];
            BoundingBox collision = corners(candidate,
                    transform(candidate, new BlockPos(5, 0, 6), rotation));
            boolean rejected = intersects(collision, central);
            int before = pieces.size();
            if (!rejected) addLocation(pieces, candidate, rotation, type, false, 0.8F,
                    random, catalog);
            List<String> templates = pieces.subList(before, pieces.size()).stream()
                    .map(piece -> piece.template().key()).toList();
            attempts.add(new ClusterAttempt(ordinal, candidateIndex, candidate, rotation,
                    collision, rejected, templates));
        }
    }

    private static ArrayList<BlockPos> allPositions(LegacyRandom random, BlockPos p) {
        ArrayList<BlockPos> result = new ArrayList<>(8);
        result.add(p.offset(-16 + inclusive(random, 1, 8), 0, 16 + inclusive(random, 1, 7)));
        result.add(p.offset(-16 + inclusive(random, 1, 8), 0, inclusive(random, 1, 7)));
        result.add(p.offset(-16 + inclusive(random, 1, 8), 0, -16 + inclusive(random, 4, 8)));
        result.add(p.offset(inclusive(random, 1, 7), 0, 16 + inclusive(random, 1, 7)));
        result.add(p.offset(inclusive(random, 1, 7), 0, -16 + inclusive(random, 4, 6)));
        result.add(p.offset(16 + inclusive(random, 1, 7), 0, 16 + inclusive(random, 3, 8)));
        result.add(p.offset(16 + inclusive(random, 1, 7), 0, inclusive(random, 1, 7)));
        result.add(p.offset(16 + inclusive(random, 1, 7), 0, -16 + inclusive(random, 4, 8)));
        return result;
    }

    private static void addLocation(List<PiecePlan> pieces, BlockPos position, Rotation rotation,
            Type type, boolean large, float integrity, LegacyRandom random,
            TemplateCatalog catalog) {
        if (type == Type.WARM) {
            List<String> keys = large ? WARM_BIG : WARM_SMALL;
            addPiece(pieces, position, rotation, type, large, integrity,
                    catalog.require(keys.get(random.nextInt(keys.size()))));
            return;
        }
        List<String> brick = large ? COLD_BRICK_BIG : COLD_BRICK_SMALL;
        List<String> cracked = large ? COLD_CRACKED_BIG : COLD_CRACKED_SMALL;
        List<String> mossy = large ? COLD_MOSSY_BIG : COLD_MOSSY_SMALL;
        int index = random.nextInt(brick.size());
        addPiece(pieces, position, rotation, type, large, integrity,
                catalog.require(brick.get(index)));
        addPiece(pieces, position, rotation, type, large, 0.7F,
                catalog.require(cracked.get(index)));
        addPiece(pieces, position, rotation, type, large, 0.5F,
                catalog.require(mossy.get(index)));
    }

    private static void addPiece(List<PiecePlan> pieces, BlockPos position, Rotation rotation,
            Type type, boolean large, float integrity, TemplateDescriptor descriptor) {
        if (!knownTemplateFor(descriptor.key(), type, large)) {
            throw new IllegalArgumentException("template outside selected ocean-ruin family");
        }
        BoundingBox box = pieceBox(position, rotation, descriptor);
        byte[] nbt = persistedPieceNbt(box, position, rotation, descriptor.key(), integrity,
                type, large);
        pieces.add(new PiecePlan(pieces.size(), descriptor, position, rotation, integrity, large,
                type, box, processorStack(integrity, type), nbt));
    }

    /** Exact private getHeight loop; query order is x/y/z BlockPos iteration order. */
    public static Plan projectToSeabed(Plan source, Terrain terrain) {
        Objects.requireNonNull(source, "ocean-ruin source plan");
        Objects.requireNonNull(terrain, "ocean-ruin projection terrain");
        ArrayList<PiecePlan> projected = new ArrayList<>();
        for (PiecePlan piece : source.pieces()) {
            int oceanFloor = terrain.oceanFloorWg(piece.templatePosition().x(),
                    piece.templatePosition().z());
            BlockPos start = new BlockPos(piece.templatePosition().x(), oceanFloor,
                    piece.templatePosition().z());
            BlockPos opposite = transform(start, new BlockPos(piece.template().sizeX() - 1, 0,
                    piece.template().sizeZ() - 1), piece.rotation());
            int y = projectedY(start, opposite, terrain);
            BlockPos position = new BlockPos(start.x(), y, start.z());
            BoundingBox box = pieceBox(position, piece.rotation(), piece.template());
            projected.add(new PiecePlan(piece.ordinal(), piece.template(), position,
                    piece.rotation(), piece.integrity(), piece.large(), piece.type(), box,
                    piece.processors(), persistedPieceNbt(box, position, piece.rotation(),
                            piece.template().key(), piece.integrity(), piece.type(), piece.large())));
        }
        BoundingBox aggregate = aggregate(projected);
        int chunkX = Math.floorDiv(source.originX(), 16);
        int chunkZ = Math.floorDiv(source.originZ(), 16);
        return new Plan(source.type(), source.originX(), source.originZ(), source.mainRotation(),
                source.large(), source.clusterAccepted(), projected, source.clusterAttempts(),
                aggregate, persistedStartNbt(source.type(), chunkX, chunkZ, projected, 0),
                source.continuation());
    }

    public interface Terrain {
        int minY();
        int oceanFloorWg(int blockX, int blockZ);
        TerrainCell cell(int blockX, int blockY, int blockZ);
    }

    public record TerrainCell(boolean air, boolean waterFluid, boolean ice) { }

    private static int projectedY(BlockPos start, BlockPos opposite, Terrain terrain) {
        int result = start.y(); int minimum = 512; int nominal = start.y() - 1; int low = 0;
        int minX = Math.min(start.x(), opposite.x()), maxX = Math.max(start.x(), opposite.x());
        int minZ = Math.min(start.z(), opposite.z()), maxZ = Math.max(start.z(), opposite.z());
        for (int y = Math.min(start.y(), opposite.y()); y <= Math.max(start.y(), opposite.y()); y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    int cursor = start.y() - 1;
                    TerrainCell cell = terrain.cell(x, cursor, z);
                    while ((cell.air() || cell.waterFluid() || cell.ice())
                            && cursor > terrain.minY() + 1) {
                        cursor--; cell = terrain.cell(x, cursor, z);
                    }
                    minimum = Math.min(minimum, cursor);
                    if (cursor < nominal - 2) low++;
                }
            }
        }
        int width = Math.abs(start.x() - opposite.x());
        if (nominal - minimum > 2 && low > width - 2) result = minimum + 1;
        return result;
    }

    public static ValidStart validStart(Plan plan, int references) {
        Objects.requireNonNull(plan, "ocean-ruin plan");
        int chunkX = Math.floorDiv(plan.originX(), 16), chunkZ = Math.floorDiv(plan.originZ(), 16);
        ArrayList<Piece> pieces = new ArrayList<>();
        for (PiecePlan piece : plan.pieces()) {
            pieces.add(new Piece(PIECE_TYPE, piece.boundingBox(), false,
                    Projection.NOT_APPLICABLE, 0, List.of(),
                    new PiecePayload(piece.persistedNbt())));
        }
        return new ValidStart(plan.structureKey() + "@" + chunkX + "," + chunkZ,
                chunkX, chunkZ, references, plan.boundingBox(), pieces);
    }

    public static byte[] persistedStartNbt(Type type, int chunkX, int chunkZ,
            List<PiecePlan> pieces, int references) {
        if (references < 0 || pieces.isEmpty()) throw new IllegalArgumentException();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(10); out.writeShort(0);
                tagInt(out, "references", references);
                tagInt(out, "ChunkZ", chunkZ);
                tagString(out, "id", structureKey(type));
                out.writeByte(9); utf(out, "Children"); out.writeByte(10);
                out.writeInt(pieces.size());
                for (PiecePlan piece : pieces) {
                    byte[] nbt = piece.persistedNbt();
                    out.write(nbt, 3, nbt.length - 3);
                }
                tagInt(out, "ChunkX", chunkX); out.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }

    public static byte[] persistedPieceNbt(BoundingBox box, BlockPos position,
            Rotation rotation, String template, float integrity, Type type, boolean large) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(10); out.writeShort(0);
                out.writeByte(11); utf(out, "BB"); out.writeInt(6);
                out.writeInt(box.minX()); out.writeInt(box.minY()); out.writeInt(box.minZ());
                out.writeInt(box.maxX()); out.writeInt(box.maxY()); out.writeInt(box.maxZ());
                tagByte(out, "IsLarge", large ? 1 : 0);
                tagString(out, "BiomeType", type.name());
                tagString(out, "Rot", rotation.name());
                tagString(out, "id", PIECE_TYPE);
                tagInt(out, "TPY", position.y()); tagInt(out, "GD", 0);
                tagInt(out, "TPX", position.x()); tagInt(out, "O", 2);
                tagInt(out, "TPZ", position.z()); tagString(out, "Template", template);
                out.writeByte(5); utf(out, "Integrity"); out.writeFloat(integrity);
                out.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }

    public static String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static List<Processor> processorStack(float integrity, Type type) {
        String material = type == Type.COLD ? "gravel" : "sand";
        return List.of(new Processor("minecraft:block_rot", integrity, "", "", "", 0),
                new Processor("minecraft:ignore_structure_and_air", Float.NaN,
                        "minecraft:air,minecraft:structure_block", "", "", 0),
                new Processor("minecraft:capped_archaeology", Float.NaN,
                        "minecraft:" + material, "minecraft:suspicious_" + material,
                        "minecraft:archaeology/ocean_ruin_" + type.name().toLowerCase(), 5));
    }

    private static void requireProcessorOrder(List<Processor> values, float integrity, Type type) {
        List<Processor> expected = processorStack(integrity, type);
        if (values.size() != expected.size()) throw new IllegalArgumentException("processor order");
        for (int index = 0; index < values.size(); index++) {
            Processor a = values.get(index), b = expected.get(index);
            if (!a.key().equals(b.key()) || Float.floatToRawIntBits(a.integrity())
                    != Float.floatToRawIntBits(b.integrity()) || !a.inputBlock().equals(b.inputBlock())
                    || !a.outputBlock().equals(b.outputBlock()) || !a.lootTable().equals(b.lootTable())
                    || a.cap() != b.cap()) throw new IllegalArgumentException("processor order");
        }
    }

    private static BoundingBox pieceBox(BlockPos position, Rotation rotation,
            TemplateDescriptor descriptor) {
        BlockPos far = transform(position, new BlockPos(descriptor.sizeX() - 1,
                descriptor.sizeY() - 1, descriptor.sizeZ() - 1), rotation);
        return corners(position, far);
    }

    private static BlockPos transform(BlockPos origin, BlockPos local, Rotation rotation) {
        int x, z;
        switch (rotation) {
            case NONE -> { x = local.x(); z = local.z(); }
            case CLOCKWISE_90 -> { x = -local.z(); z = local.x(); }
            case CLOCKWISE_180 -> { x = -local.x(); z = -local.z(); }
            case COUNTERCLOCKWISE_90 -> { x = local.z(); z = -local.x(); }
            default -> throw new AssertionError(rotation);
        }
        return origin.offset(x, local.y(), z);
    }

    private static BoundingBox corners(BlockPos a, BlockPos b) {
        return new BoundingBox(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()),
                Math.min(a.z(), b.z()), Math.max(a.x(), b.x()), Math.max(a.y(), b.y()),
                Math.max(a.z(), b.z()));
    }

    private static BoundingBox aggregate(List<PiecePlan> pieces) {
        BoundingBox result = pieces.getFirst().boundingBox();
        for (int index = 1; index < pieces.size(); index++) {
            BoundingBox b = pieces.get(index).boundingBox();
            result = new BoundingBox(Math.min(result.minX(), b.minX()),
                    Math.min(result.minY(), b.minY()), Math.min(result.minZ(), b.minZ()),
                    Math.max(result.maxX(), b.maxX()), Math.max(result.maxY(), b.maxY()),
                    Math.max(result.maxZ(), b.maxZ()));
        }
        return result;
    }

    private static boolean intersects(BoundingBox a, BoundingBox b) {
        return a.maxX() >= b.minX() && a.minX() <= b.maxX()
                && a.maxY() >= b.minY() && a.minY() <= b.maxY()
                && a.maxZ() >= b.minZ() && a.minZ() <= b.maxZ();
    }

    private static int inclusive(LegacyRandom random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private static String structureKey(Type type) {
        return type == Type.COLD ? COLD_STRUCTURE : WARM_STRUCTURE;
    }

    private static boolean knownTemplateFor(String key, Type type, boolean large) {
        if (type == Type.WARM) return (large ? WARM_BIG : WARM_SMALL).contains(key);
        return (large ? COLD_BRICK_BIG : COLD_BRICK_SMALL).contains(key)
                || (large ? COLD_CRACKED_BIG : COLD_CRACKED_SMALL).contains(key)
                || (large ? COLD_MOSSY_BIG : COLD_MOSSY_SMALL).contains(key);
    }

    private static boolean knownTemplateKey(String key) {
        return WARM_SMALL.contains(key) || WARM_BIG.contains(key)
                || COLD_BRICK_SMALL.contains(key) || COLD_CRACKED_SMALL.contains(key)
                || COLD_MOSSY_SMALL.contains(key) || COLD_BRICK_BIG.contains(key)
                || COLD_CRACKED_BIG.contains(key) || COLD_MOSSY_BIG.contains(key);
    }

    private static List<String> numbered(String prefix, int first, int last) {
        ArrayList<String> values = new ArrayList<>();
        for (int value = first; value <= last; value++) {
            values.add("minecraft:underwater_ruin/" + prefix + value);
        }
        return Collections.unmodifiableList(values);
    }

    private static List<String> named(String prefix, int... suffixes) {
        ArrayList<String> values = new ArrayList<>();
        for (int suffix : suffixes) values.add("minecraft:underwater_ruin/" + prefix + suffix);
        return Collections.unmodifiableList(values);
    }

    private static void tagByte(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(1); utf(out, name); out.writeByte(value);
    }
    private static void tagInt(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(3); utf(out, name); out.writeInt(value);
    }
    private static void tagString(DataOutputStream out, String name, String value)
            throws IOException {
        out.writeByte(8); utf(out, name); utf(out, value);
    }
    private static void utf(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 0xffff) throw new IllegalArgumentException("NBT string too long");
        out.writeShort(bytes.length); out.write(bytes);
    }
}
