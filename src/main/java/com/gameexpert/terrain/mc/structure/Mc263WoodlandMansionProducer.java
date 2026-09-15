package com.gameexpert.terrain.mc.structure;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Dormant exact Woodland Mansion start producer for Minecraft Java 26.3-snapshot-7.
 *
 * <p>False-positive boundaries are intentional: production never reads the two bounded layout
 * probes, never calls a legacy project Mansion generator, never loads raw Mojang templates/NBT,
 * never reflects into server classes and never mutates a world. MAN-E0 provenance, the accepted
 * generated grammar, all 73 template command/semantic shapes, the single structure-block ignore
 * processor and every rotation/mirror/template family are closed before caller terrain queries.</p>
 *
 * <p>The successor remains typed and dormant. It retains the lawful template grammar, exact piece
 * encounter order, transformed template origins, rotation/mirror, floor/grid ownership, transformed
 * DATA markers, aggregate bounds, final Legacy48 continuation and the source-authenticated canonical
 * raw piece/start carrier encoding. Production derives carrier bytes procedurally from those typed
 * pieces; bounded official evidence is validation-only and is never selected by coordinate.</p>
 */
public final class Mc263WoodlandMansionProducer {
    public static final String STRUCTURE_KEY = "minecraft:mansion";
    public static final String SERVER_VERSION = "26.3-snapshot-7";
    public static final String MAN_E0_INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String MAN_E0_CLASS_CLOSURE_SHA256 =
            "f1c6884fae503081e2c6ad996db46854d84d77f01814a845abcdb00ed0b46268";
    public static final String MAN_E0_SOURCE_SET_SHA256 =
            "06d31d82ad8291f75e036075853ac625378a3c846c5224f68ebf961b99e5dc4b";
    public static final String PROCESSOR_CLASS =
            "net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor";
    public static final String IGNORED_BLOCK = "minecraft:structure_block";
    public static final int MIN_GENERATION_Y = 60;
    public static final String CARRIER_FORMAT = "mc263-woodland-mansion-persisted-nbt-v1";

    private static final String PIECE_ID = "minecraft:wmp";
    private static final int PIECE_ORIENTATION = 2;

    private static final int MAN_E0_CLASS_COUNT = 12;
    private static final int STATE_COUNT = 277;
    private static final int TEMPLATE_COUNT = 73;
    private static final int BLOCK_COUNT = 66_365;
    private static final int RUN_COUNT = 11_362;
    private static final int DATA_COUNT = 118;
    private static final int CONTINUATION_COUNT = 8;
    private static final String TEMPLATE_PREFIX = "minecraft:woodland_mansion/";

    private Mc263WoodlandMansionProducer() { }

    public enum Heightmap { WORLD_SURFACE_WG }
    public enum FloorOwner { FIRST_FLOOR, SECOND_FLOOR, THIRD_FLOOR, LOWER_ROOF, UPPER_ROOF }
    public enum GridOwner { BASE_GRID, THIRD_FLOOR_GRID }

    /** Read-only terrain capability; the producer owns no world mutation surface. */
    public interface WorldAccess {
        boolean supportsHeightmap(Heightmap heightmap);
        int baseHeight(Heightmap heightmap, int blockX, int blockZ);
    }

    /** Located-start world capability; biome admission belongs to the generic structure gate. */
    public interface LocatedWorldAccess extends WorldAccess {
        boolean supportsValidBiomeTest();
        boolean isValidBiome(String structureKey, int blockX, int blockY, int blockZ);
    }

    public record Request(String structureKey, long worldSeed, int chunkX, int chunkZ) {
        public Request { Objects.requireNonNull(structureKey, "Mansion structure key"); }
    }

    public record HeightQuery(int ordinal, Heightmap heightmap, int x, int z, int result) {
        public HeightQuery {
            if (ordinal < 0) throw new IllegalArgumentException("negative Mansion height ordinal");
            Objects.requireNonNull(heightmap, "Mansion heightmap");
        }
    }

    public record ProcessorSpec(String className, List<String> ignoredBlocks,
            String receiptSha256) {
        public ProcessorSpec {
            Objects.requireNonNull(className, "Mansion processor class");
            ignoredBlocks = List.copyOf(ignoredBlocks);
            Objects.requireNonNull(receiptSha256, "Mansion processor receipt");
        }
    }

    public record Identity(String serverVersion, String innerServerSha1, int classCount,
            String classClosureSha256, String sourceSetSha256, String grammarResource,
            String grammarFileSha256, String grammarSha256, String oracleSha256,
            String oracleSourceSha256, String templateOrderedSha256, String processorSha256,
            int stateCount, int templateCount, int blockCount, int runCount, int dataCount) { }

    public record RandomReceipt(long state48, int worldgenCount, List<Long> continuationNextLong) {
        public RandomReceipt {
            if (state48 < 0 || state48 >= (1L << 48) || worldgenCount < 0) {
                throw new IllegalArgumentException("invalid Mansion Legacy48 receipt");
            }
            continuationNextLong = List.copyOf(continuationNextLong);
            if (continuationNextLong.size() != CONTINUATION_COUNT) {
                throw new IllegalArgumentException("Mansion continuation must contain eight longs");
            }
        }
    }

    public record Ownership(FloorOwner floor, GridOwner grid, int relativeY) {
        public Ownership {
            Objects.requireNonNull(floor, "Mansion floor owner");
            Objects.requireNonNull(grid, "Mansion grid owner");
        }
    }

    public record Piece(int ordinal, String templateKey,
            Mc263WoodlandMansionGrammar.Pos transformedOrigin,
            Mc263WoodlandMansionGrammar.Rotation rotation,
            Mc263WoodlandMansionGrammar.Mirror mirror, Ownership ownership,
            Mc263WoodlandMansionGrammar.Box boundingBox,
            List<Mc263WoodlandMansionGrammar.Marker> markers) {
        public Piece {
            if (ordinal < 0) throw new IllegalArgumentException("negative Mansion piece ordinal");
            Objects.requireNonNull(templateKey, "Mansion template key");
            Objects.requireNonNull(transformedOrigin, "Mansion transformed origin");
            Objects.requireNonNull(rotation, "Mansion piece rotation");
            Objects.requireNonNull(mirror, "Mansion piece mirror");
            Objects.requireNonNull(ownership, "Mansion piece ownership");
            Objects.requireNonNull(boundingBox, "Mansion piece bounds");
            markers = List.copyOf(markers);
        }
        public Mc263WoodlandMansionGrammar.Pos origin() { return transformedOrigin; }
    }

    public record SuccessorPayload(String structureKey, long worldSeed, int chunkX, int chunkZ,
            Mc263WoodlandMansionGrammar.Pos generationStubPosition,
            Mc263WoodlandMansionGrammar.Rotation rootRotation,
            Mc263WoodlandMansionGrammar.Box aggregateBoundingBox, ProcessorSpec processor,
            List<Mc263WoodlandMansionGrammar.Template> templates, List<Piece> pieces,
            PersistedCarrier carrier, RandomReceipt random, String layoutDrawReceiptSha256,
            String layoutStructuralSha256, Identity identity) {
        public SuccessorPayload {
            Objects.requireNonNull(structureKey, "Mansion successor key");
            Objects.requireNonNull(generationStubPosition, "Mansion successor stub");
            Objects.requireNonNull(rootRotation, "Mansion root rotation");
            Objects.requireNonNull(aggregateBoundingBox, "Mansion aggregate bounds");
            Objects.requireNonNull(processor, "Mansion processor");
            templates = List.copyOf(templates);
            pieces = List.copyOf(pieces);
            Objects.requireNonNull(carrier, "Mansion persisted carrier");
            require(carrier.pieces().size() == pieces.size(),
                    "Mansion successor carrier piece count drift");
            Objects.requireNonNull(random, "Mansion successor RNG");
            Objects.requireNonNull(layoutDrawReceiptSha256, "Mansion draw receipt");
            Objects.requireNonNull(layoutStructuralSha256, "Mansion structural receipt");
            Objects.requireNonNull(identity, "Mansion identity");
        }
    }

    public static final class BinaryNbt {
        private final byte[] bytes;
        private final String sha256;
        private BinaryNbt(byte[] bytes) {
            this.bytes = bytes.clone(); this.sha256 = Mc263WoodlandMansionProducer.sha256(this.bytes);
        }
        public int length() { return bytes.length; }
        public String sha256() { return sha256; }
        public byte[] bytes() { return bytes.clone(); }
        @Override public boolean equals(Object value) {
            return value instanceof BinaryNbt other && Arrays.equals(bytes, other.bytes);
        }
        @Override public int hashCode() { return Arrays.hashCode(bytes); }
    }

    public record PersistedCarrier(String format, List<BinaryNbt> pieces, BinaryNbt structureStart,
            BinaryNbt mutableSuccessorAfterOneReference) {
        public PersistedCarrier {
            require(CARRIER_FORMAT.equals(format), "unknown Mansion persisted carrier format");
            pieces = List.copyOf(pieces); Objects.requireNonNull(structureStart);
            Objects.requireNonNull(mutableSuccessorAfterOneReference);
            require(!pieces.isEmpty(), "empty Mansion persisted carrier");
        }
    }

    public sealed interface Result permits Start, RejectedStart, BiomeRejectedStart {
        Request request();
        Mc263WoodlandMansionGrammar.Rotation rotation();
        List<HeightQuery> heightQueries();
        RandomReceipt random();
        Identity identity();
    }

    public record Start(Request request, Mc263WoodlandMansionGrammar.Rotation rotation,
            List<HeightQuery> heightQueries, SuccessorPayload successor) implements Result {
        public Start {
            Objects.requireNonNull(request, "Mansion request");
            Objects.requireNonNull(rotation, "Mansion rotation");
            heightQueries = List.copyOf(heightQueries);
            Objects.requireNonNull(successor, "Mansion successor");
        }
        @Override public RandomReceipt random() { return successor.random(); }
        @Override public Identity identity() { return successor.identity(); }
        public Mc263WoodlandMansionGrammar.Pos generationStubPosition() {
            return successor.generationStubPosition();
        }
        public Mc263WoodlandMansionGrammar.Box aggregateBoundingBox() {
            return successor.aggregateBoundingBox();
        }
        public List<Piece> pieces() { return successor.pieces(); }
    }

    public record RejectedStart(Request request, Mc263WoodlandMansionGrammar.Rotation rotation,
            List<HeightQuery> heightQueries, long rejectedGenerationY,
            RandomReceipt random, Identity identity) implements Result {
        public RejectedStart {
            Objects.requireNonNull(request, "Mansion request");
            Objects.requireNonNull(rotation, "Mansion rotation");
            heightQueries = List.copyOf(heightQueries);
            Objects.requireNonNull(random, "Mansion rejected RNG");
            Objects.requireNonNull(identity, "Mansion identity");
        }
    }

    /** A located candidate whose exact generation stub fails the structure biome predicate. */
    public record BiomeRejectedStart(Request request,
            Mc263WoodlandMansionGrammar.Rotation rotation, List<HeightQuery> heightQueries,
            Mc263WoodlandMansionGrammar.Pos generationStubPosition, RandomReceipt random,
            Identity identity) implements Result {
        public BiomeRejectedStart {
            Objects.requireNonNull(request, "Mansion request");
            Objects.requireNonNull(rotation, "Mansion rotation");
            heightQueries = List.copyOf(heightQueries);
            Objects.requireNonNull(generationStubPosition, "Mansion biome-rejected stub");
            Objects.requireNonNull(random, "Mansion biome-rejected RNG");
            Objects.requireNonNull(identity, "Mansion identity");
        }
    }

    public static Identity identity() {
        return new Identity(SERVER_VERSION, MAN_E0_INNER_SERVER_SHA1, MAN_E0_CLASS_COUNT,
                MAN_E0_CLASS_CLOSURE_SHA256, MAN_E0_SOURCE_SET_SHA256,
                Mc263WoodlandMansionGrammar.RESOURCE, Mc263WoodlandMansionGrammar.FILE_SHA256,
                Mc263WoodlandMansionGrammar.GRAMMAR_SHA256,
                Mc263WoodlandMansionGrammar.ORACLE_SHA256,
                Mc263WoodlandMansionGrammar.ORACLE_SOURCE_SHA256,
                Mc263WoodlandMansionGrammar.TEMPLATE_ORDERED_SHA256,
                Mc263WoodlandMansionGrammar.PROCESSOR_SHA256,
                STATE_COUNT, TEMPLATE_COUNT, BLOCK_COUNT, RUN_COUNT, DATA_COUNT);
    }

    public static Result generate(long worldSeed, int chunkX, int chunkZ, WorldAccess world) {
        return generate(STRUCTURE_KEY, worldSeed, chunkX, chunkZ, world);
    }

    public static Result generate(String structureKey, long worldSeed, int chunkX, int chunkZ,
            WorldAccess world) {
        return generate(structureKey, worldSeed, chunkX, chunkZ, world, null);
    }

    /** Generates a located candidate with the generic structure biome predicate at its stub. */
    public static Result generateLocated(String structureKey, long worldSeed, int chunkX, int chunkZ,
            LocatedWorldAccess world) {
        Objects.requireNonNull(world, "Mansion located world access");
        return generate(structureKey, worldSeed, chunkX, chunkZ, world, world);
    }

    private static Result generate(String structureKey, long worldSeed, int chunkX, int chunkZ,
            WorldAccess world, LocatedWorldAccess locatedWorld) {
        if (!STRUCTURE_KEY.equals(structureKey)) {
            throw new IllegalArgumentException("unsupported Mansion structure key: " + structureKey);
        }
        Objects.requireNonNull(world, "Mansion world access");

        // These checks are pure and close every static capability before caller terrain reads.
        Closure closure = preflightClosure();
        CoordinateFrame frame = preflightCoordinates(chunkX, chunkZ);
        if (!world.supportsHeightmap(Heightmap.WORLD_SURFACE_WG)) {
            throw new UnsupportedOperationException("WORLD_SURFACE_WG is required for Mansion start");
        }
        if (locatedWorld != null && !locatedWorld.supportsValidBiomeTest()) {
            throw new UnsupportedOperationException("valid biome test is required for located Mansion start");
        }

        RootLegacy48 root = RootLegacy48.largeFeature(worldSeed, chunkX, chunkZ);
        Mc263WoodlandMansionGrammar.Rotation rootRotation = rotation(root.nextInt(4));
        RandomReceipt rejectedRandom = root.receipt();

        List<HeightQuery> queries = queryHeights(world, frame, rootRotation);
        int minimumBaseHeight = queries.stream().mapToInt(HeightQuery::result).min().orElseThrow();
        long generationY = (long) minimumBaseHeight - 1L;
        Request request = new Request(STRUCTURE_KEY, worldSeed, chunkX, chunkZ);
        if (generationY < MIN_GENERATION_Y) {
            return new RejectedStart(request, rootRotation, queries, generationY,
                    rejectedRandom, closure.identity());
        }

        int y = Math.toIntExact(generationY);
        Mc263WoodlandMansionGrammar.Pos origin =
                new Mc263WoodlandMansionGrammar.Pos(frame.x(), y, frame.z());
        if (locatedWorld != null && !locatedWorld.isValidBiome(STRUCTURE_KEY,
                origin.x(), origin.y(), origin.z())) {
            return new BiomeRejectedStart(request, rootRotation, queries, origin,
                    rejectedRandom, closure.identity());
        }

        // A pure y=0 graph closes selected-piece/template/transform capability after admission.
        Mc263WoodlandMansionGrammar.Pos dryOrigin =
                new Mc263WoodlandMansionGrammar.Pos(frame.x(), 0, frame.z());
        Mc263WoodlandMansionLayout.Result dry = Mc263WoodlandMansionLayout.generate(
                closure.grammar(), worldSeed, chunkX, chunkZ, dryOrigin, rootRotation);
        validateLayout(dry, closure, dryOrigin, rootRotation);
        Mc263WoodlandMansionLayout.Result layout = Mc263WoodlandMansionLayout.generate(
                closure.grammar(), worldSeed, chunkX, chunkZ, origin, rootRotation);
        validateLayout(layout, closure, origin, rootRotation);
        validateTranslation(dry, layout, y);

        ArrayList<Piece> pieces = new ArrayList<>(layout.pieces().size());
        Mc263WoodlandMansionGrammar.Box aggregate = null;
        for (Mc263WoodlandMansionLayout.LayoutPiece raw : layout.pieces()) {
            Ownership owner = ownership(raw.templateId(),
                    Math.subtractExact(raw.position().y(), origin.y()));
            pieces.add(new Piece(raw.ordinal(), raw.templateId(), raw.position(), raw.rotation(),
                    raw.mirror(), owner, raw.boundingBox(), raw.markers()));
            aggregate = aggregate == null ? raw.boundingBox()
                    : encapsulate(aggregate, raw.boundingBox());
        }
        require(!pieces.isEmpty() && aggregate != null, "empty Mansion procedural piece graph");
        RandomReceipt random = new RandomReceipt(layout.layoutState48(),
                layout.layoutWorldgenCount(), layout.layoutContinuation());
        PersistedCarrier carrier = encodeCarrier(pieces, chunkX, chunkZ);
        SuccessorPayload successor = new SuccessorPayload(STRUCTURE_KEY, worldSeed, chunkX, chunkZ,
                origin, rootRotation, aggregate, closure.processor(), closure.templates(), pieces,
                carrier, random, layout.layoutDrawReceiptSha256(), layout.structuralSha256(),
                closure.identity());
        return new Start(request, rootRotation, queries, successor);
    }

    private static Closure preflightClosure() {
        require(MAN_E0_CLASS_CLOSURE_SHA256.equals(
                        Mc263WoodlandMansionLayout.MAN_E0_CLASS_CLOSURE_SHA256),
                "Mansion MAN-E0 class provenance drift");
        require(MAN_E0_SOURCE_SET_SHA256.equals(
                        Mc263WoodlandMansionLayout.MAN_E0_SOURCE_SET_SHA256),
                "Mansion MAN-E0 source provenance drift");
        require(Mc263WoodlandMansionGrammar.Rotation.values().length == 4
                        && Mc263WoodlandMansionGrammar.Mirror.values().length == 3,
                "Mansion rotation/mirror closure drift");
        ProcessorSpec processor = new ProcessorSpec(PROCESSOR_CLASS, List.of(IGNORED_BLOCK),
                Mc263WoodlandMansionGrammar.PROCESSOR_SHA256);

        Mc263WoodlandMansionGrammar grammar = Mc263WoodlandMansionGrammar.loadAccepted();
        require(grammar.stateCatalog().size() == STATE_COUNT
                        && grammar.templates().size() == TEMPLATE_COUNT,
                "Mansion grammar cardinality drift");
        ArrayList<Mc263WoodlandMansionGrammar.Template> templates =
                new ArrayList<>(grammar.templates().values());
        templates.sort(Comparator.comparing(Mc263WoodlandMansionGrammar.Template::id));

        int blocks = 0;
        int runs = 0;
        int data = 0;
        String previous = null;
        for (Mc263WoodlandMansionGrammar.Template template : templates) {
            require(template.id().startsWith(TEMPLATE_PREFIX), "foreign Mansion template");
            require(previous == null || previous.compareTo(template.id()) < 0,
                    "duplicate Mansion template identity");
            preflightTemplate(grammar, template);
            blocks = Math.addExact(blocks, template.blockCount());
            runs = Math.addExact(runs, template.runCount());
            data = Math.addExact(data, template.dataCount());
            previous = template.id();
        }
        require(blocks == BLOCK_COUNT && runs == RUN_COUNT && data == DATA_COUNT,
                "Mansion command aggregate drift");
        return new Closure(grammar, List.copyOf(templates), processor, identity());
    }

    private static void preflightTemplate(Mc263WoodlandMansionGrammar grammar,
            Mc263WoodlandMansionGrammar.Template template) {
        require(template.size().x() > 0 && template.size().y() > 0 && template.size().z() > 0,
                "invalid Mansion template dimensions");
        templateFamily(template.id());
        int expanded = 0;
        int runs = 0;
        int data = 0;
        for (Mc263WoodlandMansionGrammar.Command command : template.commands()) {
            require(command.ordinal() == expanded, "Mansion command encounter order drift");
            require(command.stateIndex() >= 0 && command.stateIndex() < template.stateTable().size(),
                    "Mansion state-table index drift");
            String state = template.stateTable().get(command.stateIndex());
            require(grammar.stateCatalog().contains(state), "Mansion command state outside catalog");
            String block = blockKey(state);
            if (command instanceof Mc263WoodlandMansionGrammar.Run run) {
                validateRun(template, run);
                require(!IGNORED_BLOCK.equals(block), "untyped structure-block RUN");
                runs = Math.incrementExact(runs);
            } else if (command instanceof Mc263WoodlandMansionGrammar.Data datum) {
                require(containsLocal(template, datum.position()), "Mansion DATA outside template");
                validateSemantic(block, datum.semantic());
                data = Math.incrementExact(data);
            } else {
                throw new IllegalStateException("unsupported Mansion command implementation");
            }
            expanded = Math.addExact(expanded, command.expandedCount());
        }
        require(expanded == template.blockCount() && runs == template.runCount()
                        && data == template.dataCount(),
                "Mansion template command aggregate drift");
    }

    private static void validateRun(Mc263WoodlandMansionGrammar.Template template,
            Mc263WoodlandMansionGrammar.Run run) {
        require(run.count() > 0 && containsLocal(template, run.start()),
                "invalid Mansion RUN start/count");
        Mc263WoodlandMansionGrammar.Pos delta = run.delta();
        int chebyshev = Math.max(Math.max(Math.abs(delta.x()), Math.abs(delta.y())),
                Math.abs(delta.z()));
        require(run.count() == 1 ? chebyshev == 0 : chebyshev == 1,
                "invalid Mansion RUN delta");
        int distance = Math.subtractExact(run.count(), 1);
        Mc263WoodlandMansionGrammar.Pos end = new Mc263WoodlandMansionGrammar.Pos(
                Math.addExact(run.start().x(), Math.multiplyExact(delta.x(), distance)),
                Math.addExact(run.start().y(), Math.multiplyExact(delta.y(), distance)),
                Math.addExact(run.start().z(), Math.multiplyExact(delta.z(), distance)));
        require(containsLocal(template, end), "Mansion RUN exits template dimensions");
    }

    private static boolean containsLocal(Mc263WoodlandMansionGrammar.Template template,
            Mc263WoodlandMansionGrammar.Pos position) {
        return position.x() >= 0 && position.x() < template.size().x()
                && position.y() >= 0 && position.y() < template.size().y()
                && position.z() >= 0 && position.z() < template.size().z();
    }

    private static void validateSemantic(String block,
            Mc263WoodlandMansionGrammar.Semantic semantic) {
        if (semantic instanceof Mc263WoodlandMansionGrammar.StructureMarker) {
            require(IGNORED_BLOCK.equals(block), "Mansion marker processor drift");
        } else if (semantic instanceof Mc263WoodlandMansionGrammar.EmptyContainer container) {
            require(block.equals(container.blockEntityType()), "Mansion empty-container state drift");
        } else if (semantic instanceof Mc263WoodlandMansionGrammar.ContainerItems container) {
            require(block.equals(container.blockEntityType()), "Mansion container state drift");
        } else if (semantic instanceof Mc263WoodlandMansionGrammar.PatternedBanner banner) {
            require(block.endsWith("_wall_banner")
                            && "minecraft:banner".equals(banner.blockEntityType()),
                    "Mansion banner state drift");
        } else if (semantic instanceof Mc263WoodlandMansionGrammar.MobSpawner spawner) {
            require("minecraft:spawner".equals(block)
                            && "minecraft:mob_spawner".equals(spawner.blockEntityType()),
                    "Mansion spawner state drift");
        } else {
            throw new IllegalStateException("unsupported Mansion DATA semantic");
        }
    }

    private static String blockKey(String state) {
        int bracket = state.indexOf('[');
        return bracket < 0 ? state : state.substring(0, bracket);
    }

    private static CoordinateFrame preflightCoordinates(int chunkX, int chunkZ) {
        int x = Math.addExact(Math.multiplyExact(chunkX, 16), 7);
        int z = Math.addExact(Math.multiplyExact(chunkZ, 16), 7);
        Math.addExact(x, 5);
        Math.subtractExact(x, 5);
        Math.addExact(z, 5);
        Math.subtractExact(z, 5);
        return new CoordinateFrame(x, z);
    }

    private static List<HeightQuery> queryHeights(WorldAccess world, CoordinateFrame frame,
            Mc263WoodlandMansionGrammar.Rotation rotation) {
        int dx = 5;
        int dz = 5;
        switch (rotation) {
            case CLOCKWISE_90 -> dx = -5;
            case CLOCKWISE_180 -> { dx = -5; dz = -5; }
            case COUNTERCLOCKWISE_90 -> dz = -5;
            case NONE -> { }
        }
        int x2 = Math.addExact(frame.x(), dx);
        int z2 = Math.addExact(frame.z(), dz);
        ArrayList<HeightQuery> result = new ArrayList<>(4);
        query(result, world, frame.x(), frame.z());
        query(result, world, frame.x(), z2);
        query(result, world, x2, frame.z());
        query(result, world, x2, z2);
        return List.copyOf(result);
    }

    private static void query(List<HeightQuery> result, WorldAccess world, int x, int z) {
        int ordinal = result.size();
        result.add(new HeightQuery(ordinal, Heightmap.WORLD_SURFACE_WG, x, z,
                world.baseHeight(Heightmap.WORLD_SURFACE_WG, x, z)));
    }

    private static void validateLayout(Mc263WoodlandMansionLayout.Result layout, Closure closure,
            Mc263WoodlandMansionGrammar.Pos origin,
            Mc263WoodlandMansionGrammar.Rotation expectedRotation) {
        require(layout.origin().equals(origin) && layout.rotation() == expectedRotation,
                "Mansion layout root drift");
        require(layout.layoutContinuation().size() == CONTINUATION_COUNT,
                "Mansion layout continuation drift");
        require(isSha256(layout.layoutDrawReceiptSha256()) && isSha256(layout.structuralSha256()),
                "Mansion layout receipt malformed");
        require(!layout.pieces().isEmpty(), "empty Mansion layout");
        for (int i = 0; i < layout.pieces().size(); i++) {
            Mc263WoodlandMansionLayout.LayoutPiece piece = layout.pieces().get(i);
            require(piece.ordinal() == i, "Mansion piece encounter order drift");
            Mc263WoodlandMansionGrammar.Template template =
                    closure.grammar().templates().get(piece.templateId());
            require(template != null, "Mansion selected unknown template");
            templateFamily(piece.templateId());
            require(piece.boundingBox().equals(boundingBox(template, piece.position(),
                            piece.rotation(), piece.mirror())),
                    "Mansion piece transformed bounds drift");
            require(piece.markers().equals(markers(template, piece.position(),
                            piece.rotation(), piece.mirror())),
                    "Mansion piece transformed marker drift");
            ownership(piece.templateId(), Math.subtractExact(piece.position().y(), origin.y()));
        }
    }

    private static void validateTranslation(Mc263WoodlandMansionLayout.Result dry,
            Mc263WoodlandMansionLayout.Result actual, int deltaY) {
        require(dry.rotation() == actual.rotation()
                        && dry.layoutState48() == actual.layoutState48()
                        && dry.layoutWorldgenCount() == actual.layoutWorldgenCount()
                        && dry.layoutContinuation().equals(actual.layoutContinuation())
                        && dry.layoutDrawReceiptSha256().equals(actual.layoutDrawReceiptSha256())
                        && dry.pieces().size() == actual.pieces().size(),
                "Mansion layout changed with terrain Y");
        for (int i = 0; i < dry.pieces().size(); i++) {
            Mc263WoodlandMansionLayout.LayoutPiece a = dry.pieces().get(i);
            Mc263WoodlandMansionLayout.LayoutPiece b = actual.pieces().get(i);
            require(a.ordinal() == b.ordinal() && a.templateId().equals(b.templateId())
                            && a.rotation() == b.rotation() && a.mirror() == b.mirror()
                            && shift(a.position(), deltaY).equals(b.position())
                            && shift(a.boundingBox(), deltaY).equals(b.boundingBox())
                            && shiftMarkers(a.markers(), deltaY).equals(b.markers()),
                    "Mansion piece graph is not a pure vertical translation");
        }
    }

    private static List<Mc263WoodlandMansionGrammar.Marker> markers(
            Mc263WoodlandMansionGrammar.Template template,
            Mc263WoodlandMansionGrammar.Pos origin,
            Mc263WoodlandMansionGrammar.Rotation rotation,
            Mc263WoodlandMansionGrammar.Mirror mirror) {
        ArrayList<Mc263WoodlandMansionGrammar.Marker> result = new ArrayList<>();
        for (Mc263WoodlandMansionGrammar.Command command : template.commands()) {
            if (command instanceof Mc263WoodlandMansionGrammar.Data datum
                    && datum.semantic() instanceof Mc263WoodlandMansionGrammar.StructureMarker marker) {
                Mc263WoodlandMansionGrammar.Pos local = transform(datum.position(), mirror, rotation);
                result.add(new Mc263WoodlandMansionGrammar.Marker(result.size(), marker.metadata(),
                        origin.add(local)));
            }
        }
        return List.copyOf(result);
    }

    private static Mc263WoodlandMansionGrammar.Box boundingBox(
            Mc263WoodlandMansionGrammar.Template template,
            Mc263WoodlandMansionGrammar.Pos origin,
            Mc263WoodlandMansionGrammar.Rotation rotation,
            Mc263WoodlandMansionGrammar.Mirror mirror) {
        int maxX = Math.subtractExact(template.size().x(), 1);
        int maxY = Math.subtractExact(template.size().y(), 1);
        int maxZ = Math.subtractExact(template.size().z(), 1);
        int minWorldX = Integer.MAX_VALUE;
        int minWorldZ = Integer.MAX_VALUE;
        int maxWorldX = Integer.MIN_VALUE;
        int maxWorldZ = Integer.MIN_VALUE;
        for (int x : new int[] {0, maxX}) {
            for (int z : new int[] {0, maxZ}) {
                Mc263WoodlandMansionGrammar.Pos local = transform(
                        new Mc263WoodlandMansionGrammar.Pos(x, 0, z), mirror, rotation);
                int worldX = Math.addExact(origin.x(), local.x());
                int worldZ = Math.addExact(origin.z(), local.z());
                minWorldX = Math.min(minWorldX, worldX);
                minWorldZ = Math.min(minWorldZ, worldZ);
                maxWorldX = Math.max(maxWorldX, worldX);
                maxWorldZ = Math.max(maxWorldZ, worldZ);
            }
        }
        return new Mc263WoodlandMansionGrammar.Box(minWorldX, origin.y(), minWorldZ,
                maxWorldX, Math.addExact(origin.y(), maxY), maxWorldZ);
    }

    private static Mc263WoodlandMansionGrammar.Pos transform(
            Mc263WoodlandMansionGrammar.Pos local,
            Mc263WoodlandMansionGrammar.Mirror mirror,
            Mc263WoodlandMansionGrammar.Rotation rotation) {
        int x = local.x();
        int z = local.z();
        if (mirror == Mc263WoodlandMansionGrammar.Mirror.LEFT_RIGHT) {
            z = Math.negateExact(z);
        } else if (mirror == Mc263WoodlandMansionGrammar.Mirror.FRONT_BACK) {
            x = Math.negateExact(x);
        }
        return switch (rotation) {
            case NONE -> new Mc263WoodlandMansionGrammar.Pos(x, local.y(), z);
            case CLOCKWISE_90 -> new Mc263WoodlandMansionGrammar.Pos(
                    Math.negateExact(z), local.y(), x);
            case CLOCKWISE_180 -> new Mc263WoodlandMansionGrammar.Pos(
                    Math.negateExact(x), local.y(), Math.negateExact(z));
            case COUNTERCLOCKWISE_90 -> new Mc263WoodlandMansionGrammar.Pos(
                    z, local.y(), Math.negateExact(x));
        };
    }

    private static Ownership ownership(String template, int y) {
        Family family = templateFamily(template);
        if (family == Family.ROOF) {
            if (y == 16 || y == 19) {
                return new Ownership(FloorOwner.LOWER_ROOF, GridOwner.BASE_GRID, y);
            }
            if (y == 27 || y == 30) {
                return new Ownership(FloorOwner.UPPER_ROOF, GridOwner.THIRD_FLOOR_GRID, y);
            }
            throw new IllegalStateException("unknown Mansion roof owner Y=" + y);
        }
        if (family == Family.SMALL_WALL) {
            if (y == 16) return new Ownership(FloorOwner.LOWER_ROOF, GridOwner.BASE_GRID, y);
            if (y == 27) {
                return new Ownership(FloorOwner.UPPER_ROOF, GridOwner.THIRD_FLOOR_GRID, y);
            }
            throw new IllegalStateException("unknown Mansion small-wall owner Y=" + y);
        }
        if (family == Family.ENTRANCE && y != 0) {
            throw new IllegalStateException("Mansion entrance owner drift");
        }
        if (family != Family.CARPET && y != 0 && y != 8 && y != 19) {
            throw new IllegalStateException("Mansion floor owner drift Y=" + y);
        }
        return switch (y) {
            case 0, 1 -> new Ownership(FloorOwner.FIRST_FLOOR, GridOwner.BASE_GRID, y);
            case 8, 9 -> new Ownership(FloorOwner.SECOND_FLOOR, GridOwner.BASE_GRID, y);
            case 19, 20 -> new Ownership(FloorOwner.THIRD_FLOOR,
                    GridOwner.THIRD_FLOOR_GRID, y);
            default -> throw new IllegalStateException("unknown Mansion floor owner Y=" + y);
        };
    }

    private static Family templateFamily(String template) {
        if (!template.startsWith(TEMPLATE_PREFIX)) {
            throw new IllegalStateException("foreign Mansion template: " + template);
        }
        String name = template.substring(TEMPLATE_PREFIX.length());
        if (name.startsWith("1x1_") || name.startsWith("1x2_") || name.startsWith("2x2_")) {
            return Family.FLOOR;
        }
        if (name.startsWith("carpet_")) return Family.CARPET;
        if (name.equals("corridor_floor")) return Family.FLOOR;
        if (name.equals("entrance")) return Family.ENTRANCE;
        if (name.startsWith("indoors_door_") || name.startsWith("indoors_wall_")) {
            return Family.FLOOR;
        }
        if (name.equals("roof") || name.equals("roof_corner") || name.equals("roof_front")
                || name.equals("roof_inner_corner")) return Family.ROOF;
        if (name.equals("small_wall") || name.equals("small_wall_corner")) {
            return Family.SMALL_WALL;
        }
        if (name.equals("wall_corner") || name.equals("wall_flat") || name.equals("wall_window")) {
            return Family.FLOOR;
        }
        throw new IllegalStateException("unknown Mansion template family: " + template);
    }

    static PersistedCarrier encodeCarrier(List<Piece> pieces, int chunkX, int chunkZ) {
        Objects.requireNonNull(pieces, "Mansion carrier pieces");
        require(!pieces.isEmpty(), "empty Mansion carrier piece list");
        ArrayList<BinaryNbt> encodedPieces = new ArrayList<>(pieces.size());
        for (int index = 0; index < pieces.size(); index++) {
            Piece piece = pieces.get(index);
            require(piece.ordinal() == index, "Mansion carrier piece encounter order drift");
            encodedPieces.add(new BinaryNbt(writeRoot(out -> writePiecePayload(out, piece))));
        }
        BinaryNbt start = new BinaryNbt(writeRoot(out ->
                writeStartPayload(out, pieces, chunkX, chunkZ, 0)));
        BinaryNbt successor = new BinaryNbt(writeRoot(out ->
                writeStartPayload(out, pieces, chunkX, chunkZ, 1)));
        validateSingleReferenceDelta(start.bytes(), successor.bytes());
        return new PersistedCarrier(CARRIER_FORMAT, encodedPieces, start, successor);
    }

    private static void writeStartPayload(DataOutputStream out, List<Piece> pieces,
            int chunkX, int chunkZ, int references) throws IOException {
        require(references == 0 || references == 1, "invalid Mansion carrier reference count");
        intTag(out, "references", references);
        intTag(out, "ChunkZ", chunkZ);
        stringTag(out, "id", STRUCTURE_KEY);
        out.writeByte(9); out.writeUTF("Children"); out.writeByte(10);
        out.writeInt(pieces.size());
        for (Piece piece : pieces) {
            writePiecePayload(out, piece); out.writeByte(0);
        }
        intTag(out, "ChunkX", chunkX);
    }

    private static void writePiecePayload(DataOutputStream out, Piece piece) throws IOException {
        require(piece.templateKey().startsWith(TEMPLATE_PREFIX),
                "foreign Mansion carrier template");
        Mc263WoodlandMansionGrammar.Box box = piece.boundingBox();
        out.writeByte(11); out.writeUTF("BB"); out.writeInt(6);
        out.writeInt(box.minX()); out.writeInt(box.minY()); out.writeInt(box.minZ());
        out.writeInt(box.maxX()); out.writeInt(box.maxY()); out.writeInt(box.maxZ());
        stringTag(out, "Rot", piece.rotation().name());
        stringTag(out, "id", PIECE_ID);
        intTag(out, "TPY", piece.transformedOrigin().y());
        stringTag(out, "Mi", piece.mirror().name());
        intTag(out, "GD", 0);
        intTag(out, "TPX", piece.transformedOrigin().x());
        intTag(out, "O", PIECE_ORIENTATION);
        intTag(out, "TPZ", piece.transformedOrigin().z());
        stringTag(out, "Template", piece.templateKey().substring(TEMPLATE_PREFIX.length()));
    }

    private static byte[] writeRoot(IoWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(10); out.writeUTF(""); writer.write(out); out.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("failed to encode Mansion persisted carrier", impossible);
        }
    }

    private static void intTag(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(3); out.writeUTF(name); out.writeInt(value);
    }

    private static void stringTag(DataOutputStream out, String name, String value) throws IOException {
        out.writeByte(8); out.writeUTF(name); out.writeUTF(value);
    }

    private static void validateSingleReferenceDelta(byte[] start, byte[] successor) {
        require(start.length == successor.length, "Mansion carrier successor length drift");
        int differences = 0; int changed = -1;
        for (int index = 0; index < start.length; index++) {
            if (start[index] != successor[index]) { differences++; changed = index; }
        }
        require(differences == 1 && start[changed] == 0 && successor[changed] == 1,
                "Mansion carrier successor changed outside references 0->1");
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    @FunctionalInterface
    private interface IoWriter { void write(DataOutputStream out) throws IOException; }

    private static Mc263WoodlandMansionGrammar.Box encapsulate(
            Mc263WoodlandMansionGrammar.Box a, Mc263WoodlandMansionGrammar.Box b) {
        return new Mc263WoodlandMansionGrammar.Box(
                Math.min(a.minX(), b.minX()), Math.min(a.minY(), b.minY()),
                Math.min(a.minZ(), b.minZ()), Math.max(a.maxX(), b.maxX()),
                Math.max(a.maxY(), b.maxY()), Math.max(a.maxZ(), b.maxZ()));
    }

    private static Mc263WoodlandMansionGrammar.Pos shift(
            Mc263WoodlandMansionGrammar.Pos position, int y) {
        return new Mc263WoodlandMansionGrammar.Pos(position.x(),
                Math.addExact(position.y(), y), position.z());
    }

    private static Mc263WoodlandMansionGrammar.Box shift(
            Mc263WoodlandMansionGrammar.Box box, int y) {
        return new Mc263WoodlandMansionGrammar.Box(box.minX(), Math.addExact(box.minY(), y),
                box.minZ(), box.maxX(), Math.addExact(box.maxY(), y), box.maxZ());
    }

    private static List<Mc263WoodlandMansionGrammar.Marker> shiftMarkers(
            List<Mc263WoodlandMansionGrammar.Marker> markers, int y) {
        ArrayList<Mc263WoodlandMansionGrammar.Marker> result = new ArrayList<>(markers.size());
        for (Mc263WoodlandMansionGrammar.Marker marker : markers) {
            result.add(new Mc263WoodlandMansionGrammar.Marker(marker.ordinal(), marker.metadata(),
                    shift(marker.position(), y)));
        }
        return List.copyOf(result);
    }

    private static Mc263WoodlandMansionGrammar.Rotation rotation(int index) {
        return switch (index) {
            case 0 -> Mc263WoodlandMansionGrammar.Rotation.NONE;
            case 1 -> Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_90;
            case 2 -> Mc263WoodlandMansionGrammar.Rotation.CLOCKWISE_180;
            case 3 -> Mc263WoodlandMansionGrammar.Rotation.COUNTERCLOCKWISE_90;
            default -> throw new IllegalArgumentException("invalid Mansion rotation index");
        };
    }

    private static boolean isSha256(String value) {
        if (value == null || value.length() != 64) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private enum Family { FLOOR, CARPET, ENTRANCE, ROOF, SMALL_WALL }
    private record CoordinateFrame(int x, int z) { }
    private record Closure(Mc263WoodlandMansionGrammar grammar,
            List<Mc263WoodlandMansionGrammar.Template> templates,
            ProcessorSpec processor, Identity identity) { }

    /** java.util.Random-compatible root used only to preserve rejected-start continuation. */
    private static final class RootLegacy48 {
        private static final long MULTIPLIER = 0x5DEECE66DL;
        private static final long ADDEND = 0xBL;
        private static final long MASK = (1L << 48) - 1;
        private long state;
        private int count;

        private RootLegacy48(long seed) { setSeed(seed); }

        static RootLegacy48 largeFeature(long seed, int chunkX, int chunkZ) {
            RootLegacy48 random = new RootLegacy48(seed);
            long first = random.nextLong();
            long second = random.nextLong();
            random.setSeed((long) chunkX * first ^ (long) chunkZ * second ^ seed);
            return random;
        }

        private void setSeed(long seed) { state = (seed ^ MULTIPLIER) & MASK; }

        private int next(int bits) {
            state = (state * MULTIPLIER + ADDEND) & MASK;
            count = Math.incrementExact(count);
            return (int) (state >>> (48 - bits));
        }

        int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("nonpositive Mansion RNG bound");
            if ((bound & -bound) == bound) return (int) ((bound * (long) next(31)) >> 31);
            int bits;
            int value;
            do {
                bits = next(31);
                value = bits % bound;
            } while (bits - value + (bound - 1) < 0);
            return value;
        }

        long nextLong() { return ((long) next(32) << 32) + next(32); }

        RandomReceipt receipt() {
            RootLegacy48 copy = new RootLegacy48(0L);
            copy.state = state;
            copy.count = count;
            ArrayList<Long> tail = new ArrayList<>(CONTINUATION_COUNT);
            for (int i = 0; i < CONTINUATION_COUNT; i++) tail.add(copy.nextLong());
            return new RandomReceipt(state, count, tail);
        }
    }
}
