package com.gameexpert.terrain.mc.structure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dormant, pure data boundary for template-backed 26.3-snapshot-7 Overworld structures.
 *
 * <p>This boundary intentionally contains no template blocks and has no registry or generation
 * wiring. It can preserve the verified decision prefix and exact RNG continuation, but it refuses
 * to claim a complete structure when the pinned implementation next needs template geometry,
 * terrain, biome state, or the unported woodland-mansion/cluster grammar.</p>
 */
public final class Mc263TemplateStructureBoundary {
    public static final String BASELINE_ID =
            "minecraft-java-26.3-snapshot-7+wv5009+dp115+rp95";
    public static final String OUTER_SERVER_SHA1 =
            "06157fedd67ff4dd6e0e6fa4a9dd0af296f0dd61";
    public static final String INNER_SERVER_SHA256 =
            "e5efad859e05767b507f43cf5adb28b6c8944ef7c0b612527f3e6ebdd2c4ace1";
    public static final String STRUCTURE_TEMPLATE_CLASS_SHA256 =
            "6730a621dd6b800a769e2c2a5bec797b83c9fd90cf8448245940b7b546234e67";

    private static final byte[] RECEIPT_MAGIC =
            "M263TSB1".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_PIECES = 24;
    private static final int MAX_PROCESSORS = 12;
    private static final int MAX_STRING_BYTES = 160;

    private static final String IGNORE_STRUCTURE_AND_AIR =
            "BlockIgnoreProcessor.STRUCTURE_AND_AIR";
    private static final String IGNORE_STRUCTURE_BLOCK =
            "BlockIgnoreProcessor.STRUCTURE_BLOCK";
    private static final String IGNORE_MANSION_STRUCTURE_BLOCK =
            "ignore_entities=true;BlockIgnoreProcessor.STRUCTURE_BLOCK";

    private static final List<String> SHIP_BEACHED = List.of(
            "minecraft:shipwreck/with_mast",
            "minecraft:shipwreck/sideways_full",
            "minecraft:shipwreck/sideways_fronthalf",
            "minecraft:shipwreck/sideways_backhalf",
            "minecraft:shipwreck/rightsideup_full",
            "minecraft:shipwreck/rightsideup_fronthalf",
            "minecraft:shipwreck/rightsideup_backhalf",
            "minecraft:shipwreck/with_mast_degraded",
            "minecraft:shipwreck/rightsideup_full_degraded",
            "minecraft:shipwreck/rightsideup_fronthalf_degraded",
            "minecraft:shipwreck/rightsideup_backhalf_degraded");
    private static final List<String> SHIP_OCEAN = List.of(
            "minecraft:shipwreck/with_mast",
            "minecraft:shipwreck/upsidedown_full",
            "minecraft:shipwreck/upsidedown_fronthalf",
            "minecraft:shipwreck/upsidedown_backhalf",
            "minecraft:shipwreck/sideways_full",
            "minecraft:shipwreck/sideways_fronthalf",
            "minecraft:shipwreck/sideways_backhalf",
            "minecraft:shipwreck/rightsideup_full",
            "minecraft:shipwreck/rightsideup_fronthalf",
            "minecraft:shipwreck/rightsideup_backhalf",
            "minecraft:shipwreck/with_mast_degraded",
            "minecraft:shipwreck/upsidedown_full_degraded",
            "minecraft:shipwreck/upsidedown_fronthalf_degraded",
            "minecraft:shipwreck/upsidedown_backhalf_degraded",
            "minecraft:shipwreck/sideways_full_degraded",
            "minecraft:shipwreck/sideways_fronthalf_degraded",
            "minecraft:shipwreck/sideways_backhalf_degraded",
            "minecraft:shipwreck/rightsideup_full_degraded",
            "minecraft:shipwreck/rightsideup_fronthalf_degraded",
            "minecraft:shipwreck/rightsideup_backhalf_degraded");

    private static final List<String> WARM_SMALL = numbered("underwater_ruin/warm_", 1, 8);
    private static final List<String> COLD_BRICK_SMALL = numbered("underwater_ruin/brick_", 1, 8);
    private static final List<String> COLD_CRACKED_SMALL = numbered("underwater_ruin/cracked_", 1, 8);
    private static final List<String> COLD_MOSSY_SMALL = numbered("underwater_ruin/mossy_", 1, 8);
    private static final List<String> WARM_BIG = named("underwater_ruin/big_warm_", 4, 5, 6, 7);
    private static final List<String> COLD_BRICK_BIG = named("underwater_ruin/big_brick_", 1, 2, 3, 8);
    private static final List<String> COLD_CRACKED_BIG = named("underwater_ruin/big_cracked_", 1, 2, 3, 8);
    private static final List<String> COLD_MOSSY_BIG = named("underwater_ruin/big_mossy_", 1, 2, 3, 8);
    private static final List<String> PORTALS = numbered("ruined_portal/portal_", 1, 10);
    private static final List<String> GIANT_PORTALS = numbered("ruined_portal/giant_portal_", 1, 3);

    private static final EnumMap<StructureId, Definition> DEFINITIONS = buildDefinitions();

    private Mc263TemplateStructureBoundary() { }

    public enum StructureId {
        SHIPWRECK,
        SHIPWRECK_BEACHED,
        OCEAN_RUIN_COLD,
        OCEAN_RUIN_WARM,
        RUINED_PORTAL,
        RUINED_PORTAL_DESERT,
        RUINED_PORTAL_JUNGLE,
        RUINED_PORTAL_MOUNTAIN,
        RUINED_PORTAL_OCEAN,
        RUINED_PORTAL_SWAMP,
        WOODLAND_MANSION
    }

    public enum Rotation {
        NONE,
        CLOCKWISE_90,
        CLOCKWISE_180,
        COUNTERCLOCKWISE_90
    }

    public enum Mirror { NONE, FRONT_BACK, LEFT_RIGHT }

    public enum ConnectorFact {
        DIRECT_TEMPLATE_PIECE_NO_JIGSAW,
        DIRECT_ORDERED_OVERLAY_PIECES_NO_JIGSAW,
        PROCEDURAL_GRID_ADJACENCY_UNSUPPORTED_NO_JIGSAW
    }

    public enum RotationFact {
        RANDOM_FOUR_IN_ENUM_ORDER,
        ROOT_RANDOM_FOUR_WITH_DERIVED_PIECE_ROTATIONS
    }

    public enum ProjectionFact {
        OCEAN_FLOOR_WG_MEAN,
        WORLD_SURFACE_WG_BEACHED_MINIMUM,
        OCEAN_FLOOR_WG_FOOTPRINT,
        SETUP_DEPENDENT_PORTAL_VERTICAL_PLACEMENT,
        LOWEST_Y_IN_5_BY_5_OFFSET_7_REJECT_BELOW_60
    }

    public enum PivotFact {
        FIXED_4_0_15,
        ORIGIN,
        TEMPLATE_CENTER_XZ,
        DEFAULT_ORIGIN
    }

    public enum BoundaryStatus {
        TEMPLATE_GEOMETRY_AND_TERRAIN_REQUIRED,
        OCEAN_CLUSTER_GRAMMAR_UNSUPPORTED,
        PORTAL_PROJECTION_AND_COLDNESS_REQUIRED,
        WOODLAND_MANSION_GRAMMAR_UNSUPPORTED
    }

    public enum DrawKind { BOUNDED_INT, UNIT_FLOAT }

    /** One caller-owned random result, including the bound that produced an integer. */
    public static final class Draw {
        private final DrawKind kind;
        private final int bound;
        private final int valueBits;

        private Draw(DrawKind kind, int bound, int valueBits) {
            this.kind = Objects.requireNonNull(kind, "draw kind");
            this.bound = bound;
            this.valueBits = valueBits;
        }

        public static Draw boundedInt(int bound, int value) {
            if (bound <= 0 || value < 0 || value >= bound) {
                throw new IllegalArgumentException("invalid bounded RNG draw");
            }
            return new Draw(DrawKind.BOUNDED_INT, bound, value);
        }

        public static Draw unitFloat(float value) {
            if (!(value >= 0.0f && value < 1.0f) || !Float.isFinite(value)) {
                throw new IllegalArgumentException("invalid unit-float RNG draw");
            }
            return new Draw(DrawKind.UNIT_FLOAT, 0, Float.floatToRawIntBits(value));
        }

        public DrawKind kind() { return kind; }
        public int bound() { return bound; }
        public int intValue() { return valueBits; }
        public float floatValue() { return Float.intBitsToFloat(valueBits); }
    }

    /** Immutable tape; a plan reports the first draw it did not consume. */
    public static final class DrawTape {
        private final List<Draw> draws;

        public DrawTape(List<Draw> draws) {
            this.draws = List.copyOf(draws);
            for (Draw draw : this.draws) Objects.requireNonNull(draw, "draw");
        }

        public List<Draw> draws() { return draws; }
    }

    public static final class Definition {
        private final StructureId structureId;
        private final String registryId;
        private final String jsonSha256;
        private final List<String> classSha256;
        private final ConnectorFact connector;
        private final RotationFact rotation;
        private final ProjectionFact projection;
        private final PivotFact pivot;
        private final String pieceType;
        private final List<String> processorFacts;

        private Definition(StructureId structureId, String registryId, String jsonSha256,
                List<String> classSha256, ConnectorFact connector, RotationFact rotation,
                ProjectionFact projection, PivotFact pivot, String pieceType,
                List<String> processorFacts) {
            this.structureId = structureId;
            this.registryId = registryId;
            this.jsonSha256 = jsonSha256;
            this.classSha256 = List.copyOf(classSha256);
            this.connector = connector;
            this.rotation = rotation;
            this.projection = projection;
            this.pivot = pivot;
            this.pieceType = pieceType;
            this.processorFacts = List.copyOf(processorFacts);
        }

        public StructureId structureId() { return structureId; }
        public String registryId() { return registryId; }
        public String jsonSha256() { return jsonSha256; }
        public List<String> classSha256() { return classSha256; }
        public ConnectorFact connector() { return connector; }
        public RotationFact rotation() { return rotation; }
        public ProjectionFact projection() { return projection; }
        public PivotFact pivot() { return pivot; }
        public String pieceType() { return pieceType; }
        public List<String> processorFacts() { return processorFacts; }
    }

    public static final class PieceFact {
        private final String pieceType;
        private final String templateId;
        private final Rotation rotation;
        private final Mirror mirror;
        private final float integrity;
        private final List<String> orderedProcessors;

        private PieceFact(String pieceType, String templateId, Rotation rotation, Mirror mirror,
                float integrity, List<String> orderedProcessors) {
            this.pieceType = requireNamespaced(pieceType, "piece type");
            this.templateId = requireNamespaced(templateId, "template id");
            this.rotation = Objects.requireNonNull(rotation, "rotation");
            this.mirror = Objects.requireNonNull(mirror, "mirror");
            if (!Float.isNaN(integrity)
                    && (!(integrity >= 0.0f && integrity <= 1.0f) || !Float.isFinite(integrity))) {
                throw new IllegalArgumentException("invalid piece integrity");
            }
            if (Float.isNaN(integrity) && Float.floatToRawIntBits(integrity)
                    != Float.floatToRawIntBits(Float.NaN)) {
                throw new IllegalArgumentException("noncanonical NaN integrity");
            }
            this.integrity = integrity;
            this.orderedProcessors = List.copyOf(orderedProcessors);
            if (this.orderedProcessors.size() > MAX_PROCESSORS) {
                throw new IllegalArgumentException("too many processors");
            }
            for (String processor : this.orderedProcessors) requireText(processor, "processor");
        }

        public String pieceType() { return pieceType; }
        public String templateId() { return templateId; }
        public Rotation rotation() { return rotation; }
        public Mirror mirror() { return mirror; }
        public float integrity() { return integrity; }
        public List<String> orderedProcessors() { return orderedProcessors; }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof PieceFact value)) return false;
            return pieceType.equals(value.pieceType) && templateId.equals(value.templateId)
                    && rotation == value.rotation && mirror == value.mirror
                    && Float.floatToRawIntBits(integrity)
                            == Float.floatToRawIntBits(value.integrity)
                    && orderedProcessors.equals(value.orderedProcessors);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pieceType, templateId, rotation, mirror,
                    Float.floatToRawIntBits(integrity), orderedProcessors);
        }
    }

    public static final class PrefixPlan {
        private final StructureId structureId;
        private final int chunkX;
        private final int chunkZ;
        private final int anchorX;
        private final int anchorZ;
        private final Rotation rootRotation;
        private final Mirror rootMirror;
        private final int nextDrawIndex;
        private final BoundaryStatus status;
        private final List<PieceFact> orderedPieces;

        private PrefixPlan(StructureId structureId, int chunkX, int chunkZ, int anchorX,
                int anchorZ, Rotation rootRotation, Mirror rootMirror, int nextDrawIndex,
                BoundaryStatus status, List<PieceFact> orderedPieces) {
            this.structureId = Objects.requireNonNull(structureId, "structure id");
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.anchorX = anchorX;
            this.anchorZ = anchorZ;
            this.rootRotation = Objects.requireNonNull(rootRotation, "root rotation");
            this.rootMirror = Objects.requireNonNull(rootMirror, "root mirror");
            if (nextDrawIndex < 0) throw new IllegalArgumentException("negative RNG continuation");
            this.nextDrawIndex = nextDrawIndex;
            this.status = Objects.requireNonNull(status, "status");
            this.orderedPieces = List.copyOf(orderedPieces);
            validatePlan(this);
        }

        public StructureId structureId() { return structureId; }
        public int chunkX() { return chunkX; }
        public int chunkZ() { return chunkZ; }
        public int anchorX() { return anchorX; }
        public int anchorZ() { return anchorZ; }
        public Rotation rootRotation() { return rootRotation; }
        public Mirror rootMirror() { return rootMirror; }
        public int nextDrawIndex() { return nextDrawIndex; }
        public BoundaryStatus status() { return status; }
        public List<PieceFact> orderedPieces() { return orderedPieces; }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof PrefixPlan value)) return false;
            return structureId == value.structureId && chunkX == value.chunkX
                    && chunkZ == value.chunkZ && anchorX == value.anchorX
                    && anchorZ == value.anchorZ && rootRotation == value.rootRotation
                    && rootMirror == value.rootMirror && nextDrawIndex == value.nextDrawIndex
                    && status == value.status && orderedPieces.equals(value.orderedPieces);
        }

        @Override
        public int hashCode() {
            return Objects.hash(structureId, chunkX, chunkZ, anchorX, anchorZ, rootRotation,
                    rootMirror, nextDrawIndex, status, orderedPieces);
        }
    }

    public static Map<StructureId, Definition> definitions() {
        return Collections.unmodifiableMap(DEFINITIONS);
    }

    public static Definition definition(StructureId structureId) {
        Definition definition = DEFINITIONS.get(Objects.requireNonNull(structureId, "structure id"));
        if (definition == null) throw new IllegalArgumentException("unsupported structure");
        return definition;
    }

    /** Exact JSON setup encounter order for one of the six Overworld portal variants. */
    public static List<PortalSetupFact> portalSetupFacts(StructureId structureId) {
        return portalSetups(Objects.requireNonNull(structureId, "structure id"));
    }

    /** Resolves only the verified prefix. No returned value is a claim of complete parity. */
    public static PrefixPlan planPrefix(StructureId structureId, int chunkX, int chunkZ,
            DrawTape tape, int startDrawIndex) {
        Objects.requireNonNull(structureId, "structure id");
        Objects.requireNonNull(tape, "draw tape");
        Cursor cursor = new Cursor(tape, startDrawIndex);
        int anchorX = chunkCoordinate(chunkX);
        int anchorZ = chunkCoordinate(chunkZ);
        return switch (structureId) {
            case SHIPWRECK -> shipwreck(structureId, chunkX, chunkZ, anchorX, anchorZ,
                    SHIP_OCEAN, cursor);
            case SHIPWRECK_BEACHED -> shipwreck(structureId, chunkX, chunkZ, anchorX, anchorZ,
                    SHIP_BEACHED, cursor);
            case OCEAN_RUIN_COLD -> oceanRuin(structureId, chunkX, chunkZ, anchorX, anchorZ,
                    true, cursor);
            case OCEAN_RUIN_WARM -> oceanRuin(structureId, chunkX, chunkZ, anchorX, anchorZ,
                    false, cursor);
            case RUINED_PORTAL, RUINED_PORTAL_DESERT, RUINED_PORTAL_JUNGLE,
                    RUINED_PORTAL_MOUNTAIN, RUINED_PORTAL_OCEAN,
                    RUINED_PORTAL_SWAMP -> portal(structureId, chunkX, chunkZ, anchorX,
                            anchorZ, cursor);
            case WOODLAND_MANSION -> mansion(structureId, chunkX, chunkZ, anchorX,
                    anchorZ, cursor);
        };
    }

    /** Every current prefix is deliberately incomplete and therefore rejected here. */
    public static void requireComplete(PrefixPlan plan) {
        Objects.requireNonNull(plan, "plan");
        throw new UnsupportedOperationException("pinned template grammar is incomplete: "
                + plan.status());
    }

    /** Exact pinned StructureTemplate integer transform, useful without template block data. */
    public static BlockPos transform(BlockPos position, Mirror mirror, Rotation rotation,
            BlockPos pivot) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(mirror, "mirror");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(pivot, "pivot");
        int x = position.x();
        int z = position.z();
        if (mirror == Mirror.LEFT_RIGHT) z = Math.negateExact(z);
        if (mirror == Mirror.FRONT_BACK) x = Math.negateExact(x);
        return switch (rotation) {
            case NONE -> new BlockPos(x, position.y(), z);
            case CLOCKWISE_180 -> new BlockPos(
                    Math.subtractExact(Math.multiplyExact(2, pivot.x()), x), position.y(),
                    Math.subtractExact(Math.multiplyExact(2, pivot.z()), z));
            case COUNTERCLOCKWISE_90 -> new BlockPos(
                    Math.addExact(Math.subtractExact(pivot.x(), pivot.z()), z), position.y(),
                    Math.subtractExact(Math.addExact(pivot.x(), pivot.z()), x));
            case CLOCKWISE_90 -> new BlockPos(
                    Math.subtractExact(Math.addExact(pivot.x(), pivot.z()), z), position.y(),
                    Math.addExact(Math.subtractExact(pivot.z(), pivot.x()), x));
        };
    }

    public static byte[] encode(PrefixPlan plan) {
        Objects.requireNonNull(plan, "plan");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.write(RECEIPT_MAGIC);
            output.writeByte(plan.structureId.ordinal());
            output.writeInt(plan.chunkX);
            output.writeInt(plan.chunkZ);
            output.writeInt(plan.anchorX);
            output.writeInt(plan.anchorZ);
            output.writeByte(plan.rootRotation.ordinal());
            output.writeByte(plan.rootMirror.ordinal());
            output.writeInt(plan.nextDrawIndex);
            output.writeByte(plan.status.ordinal());
            output.writeByte(plan.orderedPieces.size());
            for (PieceFact piece : plan.orderedPieces) {
                writeString(output, piece.pieceType);
                writeString(output, piece.templateId);
                output.writeByte(piece.rotation.ordinal());
                output.writeByte(piece.mirror.ordinal());
                output.writeInt(Float.floatToRawIntBits(piece.integrity));
                output.writeByte(piece.orderedProcessors.size());
                for (String processor : piece.orderedProcessors) writeString(output, processor);
            }
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory receipt failure", exception);
        }
    }

    public static PrefixPlan decode(byte[] receipt) {
        Objects.requireNonNull(receipt, "receipt");
        try {
            ByteArrayInputStream bytes = new ByteArrayInputStream(receipt);
            DataInputStream input = new DataInputStream(bytes);
            byte[] magic = input.readNBytes(RECEIPT_MAGIC.length);
            if (!Arrays.equals(RECEIPT_MAGIC, magic)) fail("invalid template boundary magic");
            StructureId structureId = enumValue(StructureId.values(), input.readUnsignedByte(),
                    "structure id");
            int chunkX = input.readInt();
            int chunkZ = input.readInt();
            int anchorX = input.readInt();
            int anchorZ = input.readInt();
            Rotation rotation = enumValue(Rotation.values(), input.readUnsignedByte(), "rotation");
            Mirror mirror = enumValue(Mirror.values(), input.readUnsignedByte(), "mirror");
            int nextDrawIndex = input.readInt();
            BoundaryStatus status = enumValue(BoundaryStatus.values(), input.readUnsignedByte(),
                    "boundary status");
            int pieceCount = input.readUnsignedByte();
            if (pieceCount > MAX_PIECES) fail("too many pieces");
            ArrayList<PieceFact> pieces = new ArrayList<>(pieceCount);
            for (int pieceIndex = 0; pieceIndex < pieceCount; pieceIndex++) {
                String pieceType = readString(input);
                String templateId = readString(input);
                Rotation pieceRotation = enumValue(Rotation.values(), input.readUnsignedByte(),
                        "piece rotation");
                Mirror pieceMirror = enumValue(Mirror.values(), input.readUnsignedByte(),
                        "piece mirror");
                float integrity = Float.intBitsToFloat(input.readInt());
                int processorCount = input.readUnsignedByte();
                if (processorCount > MAX_PROCESSORS) fail("too many processors");
                ArrayList<String> processors = new ArrayList<>(processorCount);
                for (int processorIndex = 0; processorIndex < processorCount; processorIndex++) {
                    processors.add(readString(input));
                }
                pieces.add(new PieceFact(pieceType, templateId, pieceRotation, pieceMirror,
                        integrity, processors));
            }
            if (bytes.available() != 0) fail("trailing template boundary bytes");
            PrefixPlan plan = new PrefixPlan(structureId, chunkX, chunkZ, anchorX, anchorZ,
                    rotation, mirror, nextDrawIndex, status, pieces);
            if (!Arrays.equals(receipt, encode(plan))) fail("noncanonical template receipt");
            return plan;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("truncated template boundary receipt", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("invalid template boundary receipt", exception);
        }
    }

    public static final class BlockPos {
        private final int x;
        private final int y;
        private final int z;

        public BlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }

        @Override
        public boolean equals(Object other) {
            return other instanceof BlockPos value && x == value.x && y == value.y && z == value.z;
        }

        @Override
        public int hashCode() { return Objects.hash(x, y, z); }
    }

    private static PrefixPlan shipwreck(StructureId structureId, int chunkX, int chunkZ,
            int anchorX, int anchorZ, List<String> templates, Cursor cursor) {
        Rotation rotation = Rotation.values()[cursor.nextInt(4)];
        String template = templates.get(cursor.nextInt(templates.size()));
        PieceFact piece = new PieceFact("minecraft:shipwreck_piece", template, rotation,
                Mirror.NONE, Float.NaN, List.of(IGNORE_STRUCTURE_AND_AIR));
        return new PrefixPlan(structureId, chunkX, chunkZ, anchorX, anchorZ, rotation,
                Mirror.NONE, cursor.index, BoundaryStatus.TEMPLATE_GEOMETRY_AND_TERRAIN_REQUIRED,
                List.of(piece));
    }

    private static PrefixPlan oceanRuin(StructureId structureId, int chunkX, int chunkZ,
            int anchorX, int anchorZ, boolean cold, Cursor cursor) {
        Rotation rotation = Rotation.values()[cursor.nextInt(4)];
        boolean large = cursor.nextFloat() <= 0.3f;
        float baseIntegrity = large ? 0.9f : 0.8f;
        ArrayList<PieceFact> pieces = new ArrayList<>();
        if (cold) {
            List<String> brick = large ? COLD_BRICK_BIG : COLD_BRICK_SMALL;
            List<String> cracked = large ? COLD_CRACKED_BIG : COLD_CRACKED_SMALL;
            List<String> mossy = large ? COLD_MOSSY_BIG : COLD_MOSSY_SMALL;
            int index = cursor.nextInt(brick.size());
            pieces.add(oceanPiece(brick.get(index), rotation, baseIntegrity, true));
            pieces.add(oceanPiece(cracked.get(index), rotation, 0.7f, true));
            pieces.add(oceanPiece(mossy.get(index), rotation, 0.5f, true));
        } else {
            List<String> templates = large ? WARM_BIG : WARM_SMALL;
            pieces.add(oceanPiece(templates.get(cursor.nextInt(templates.size())), rotation,
                    baseIntegrity, false));
        }
        BoundaryStatus status = BoundaryStatus.TEMPLATE_GEOMETRY_AND_TERRAIN_REQUIRED;
        if (large && cursor.nextFloat() <= 0.9f) {
            status = BoundaryStatus.OCEAN_CLUSTER_GRAMMAR_UNSUPPORTED;
        }
        return new PrefixPlan(structureId, chunkX, chunkZ, anchorX, anchorZ, rotation,
                Mirror.NONE, cursor.index, status, pieces);
    }

    private static PieceFact oceanPiece(String template, Rotation rotation, float integrity,
            boolean cold) {
        String suspicious = cold
                ? "CappedProcessor(5):gravel->suspicious_gravel+ocean_ruin_cold_archaeology"
                : "CappedProcessor(5):sand->suspicious_sand+ocean_ruin_warm_archaeology";
        return new PieceFact("minecraft:ocean_ruin", template, rotation, Mirror.NONE, integrity,
                List.of("BlockRotProcessor(" + Float.toString(integrity) + ")",
                        IGNORE_STRUCTURE_AND_AIR, suspicious));
    }

    private static PrefixPlan portal(StructureId structureId, int chunkX, int chunkZ,
            int anchorX, int anchorZ, Cursor cursor) {
        List<PortalSetupFact> setups = portalSetups(structureId);
        PortalSetupFact setup = setups.size() == 1 ? setups.get(0)
                : weightedSetup(setups, cursor.nextFloat());
        boolean airPocket = setup.airPocketProbability == 1.0f
                || setup.airPocketProbability != 0.0f
                        && cursor.nextFloat() < setup.airPocketProbability;
        boolean giant = cursor.nextFloat() < 0.05f;
        List<String> templates = giant ? GIANT_PORTALS : PORTALS;
        String template = templates.get(cursor.nextInt(templates.size()));
        Rotation rotation = Rotation.values()[cursor.nextInt(4)];
        Mirror mirror = cursor.nextFloat() < 0.5f ? Mirror.NONE : Mirror.FRONT_BACK;
        ArrayList<String> processors = portalProcessors(setup, airPocket);
        PieceFact piece = new PieceFact("minecraft:ruined_portal", template, rotation, mirror,
                Float.NaN, processors);
        return new PrefixPlan(structureId, chunkX, chunkZ, anchorX, anchorZ, rotation, mirror,
                cursor.index, BoundaryStatus.PORTAL_PROJECTION_AND_COLDNESS_REQUIRED,
                List.of(piece));
    }

    private static PrefixPlan mansion(StructureId structureId, int chunkX, int chunkZ,
            int anchorX, int anchorZ, Cursor cursor) {
        Rotation rotation = Rotation.values()[cursor.nextInt(4)];
        return new PrefixPlan(structureId, chunkX, chunkZ, anchorX, anchorZ, rotation,
                Mirror.NONE, cursor.index, BoundaryStatus.WOODLAND_MANSION_GRAMMAR_UNSUPPORTED,
                List.of());
    }

    private static void validatePlan(PrefixPlan plan) {
        if (plan.anchorX != chunkCoordinate(plan.chunkX)
                || plan.anchorZ != chunkCoordinate(plan.chunkZ)) {
            fail("anchor disagrees with chunk coordinates");
        }
        if (plan.orderedPieces.size() > MAX_PIECES) fail("too many pieces");
        Definition definition = definition(plan.structureId);
        for (PieceFact piece : plan.orderedPieces) {
            Objects.requireNonNull(piece, "piece");
            if (!definition.pieceType.equals(piece.pieceType)) fail("piece type disagrees");
            if (!templateAllowed(plan.structureId, piece.templateId)) {
                fail("template is outside pinned vocabulary");
            }
        }
        int expected = switch (plan.structureId) {
            case OCEAN_RUIN_COLD -> 3;
            case WOODLAND_MANSION -> 0;
            default -> 1;
        };
        if (plan.orderedPieces.size() != expected) fail("unexpected ordered piece count");
        if (plan.structureId != StructureId.WOODLAND_MANSION
                && plan.orderedPieces.get(0).rotation != plan.rootRotation) {
            fail("root rotation disagrees with first piece");
        }
        validateCanonicalFacts(plan);
    }

    private static void validateCanonicalFacts(PrefixPlan plan) {
        switch (plan.structureId) {
            case SHIPWRECK, SHIPWRECK_BEACHED -> {
                if (plan.rootMirror != Mirror.NONE
                        || plan.status != BoundaryStatus.TEMPLATE_GEOMETRY_AND_TERRAIN_REQUIRED
                        || !plan.orderedPieces.get(0).orderedProcessors
                                .equals(List.of(IGNORE_STRUCTURE_AND_AIR))
                        || !Float.isNaN(plan.orderedPieces.get(0).integrity)) {
                    fail("noncanonical shipwreck facts");
                }
            }
            case OCEAN_RUIN_COLD, OCEAN_RUIN_WARM -> validateOceanFacts(plan);
            case RUINED_PORTAL, RUINED_PORTAL_DESERT, RUINED_PORTAL_JUNGLE,
                    RUINED_PORTAL_MOUNTAIN, RUINED_PORTAL_OCEAN,
                    RUINED_PORTAL_SWAMP -> {
                if (plan.rootMirror != Mirror.NONE && plan.rootMirror != Mirror.FRONT_BACK) {
                    fail("noncanonical portal mirror");
                }
                if (plan.status != BoundaryStatus.PORTAL_PROJECTION_AND_COLDNESS_REQUIRED
                        || !Float.isNaN(plan.orderedPieces.get(0).integrity)
                        || !portalProcessorsAllowed(plan.structureId,
                                plan.orderedPieces.get(0).orderedProcessors)) {
                    fail("noncanonical portal facts");
                }
            }
            case WOODLAND_MANSION -> {
                if (plan.rootMirror != Mirror.NONE
                        || plan.status != BoundaryStatus.WOODLAND_MANSION_GRAMMAR_UNSUPPORTED) {
                    fail("noncanonical mansion prefix");
                }
            }
        }
    }

    private static void validateOceanFacts(PrefixPlan plan) {
        if (plan.rootMirror != Mirror.NONE
                || plan.status != BoundaryStatus.TEMPLATE_GEOMETRY_AND_TERRAIN_REQUIRED
                        && plan.status != BoundaryStatus.OCEAN_CLUSTER_GRAMMAR_UNSUPPORTED) {
            fail("noncanonical ocean ruin prefix");
        }
        boolean cold = plan.structureId == StructureId.OCEAN_RUIN_COLD;
        boolean large = plan.orderedPieces.get(0).integrity == 0.9f;
        if (plan.status == BoundaryStatus.OCEAN_CLUSTER_GRAMMAR_UNSUPPORTED && !large) {
            fail("small ocean ruin cannot enter cluster grammar");
        }
        String suffix = null;
        for (int index = 0; index < plan.orderedPieces.size(); index++) {
            PieceFact piece = plan.orderedPieces.get(index);
            float expectedIntegrity = index == 0 ? piece.integrity
                    : index == 1 ? 0.7f : 0.5f;
            if (index == 0 && piece.integrity != 0.8f && piece.integrity != 0.9f) {
                fail("noncanonical ocean primary integrity");
            }
            if (index > 0 && piece.integrity != expectedIntegrity) {
                fail("noncanonical ocean overlay integrity");
            }
            if (!piece.orderedProcessors.equals(oceanPiece(piece.templateId, piece.rotation,
                    piece.integrity, cold).orderedProcessors)) {
                fail("noncanonical ocean processors");
            }
            if (cold) {
                String requiredFamily = index == 0 ? (large ? "/big_brick_" : "/brick_")
                        : index == 1 ? (large ? "/big_cracked_" : "/cracked_")
                                : (large ? "/big_mossy_" : "/mossy_");
                if (!piece.templateId.contains(requiredFamily)) {
                    fail("cold overlay family or size mismatch");
                }
                String nextSuffix = piece.templateId.substring(piece.templateId.lastIndexOf('_') + 1);
                if (suffix == null) suffix = nextSuffix;
                else if (!suffix.equals(nextSuffix)) fail("cold overlay index mismatch");
            } else if (large != WARM_BIG.contains(piece.templateId)) {
                fail("warm ruin size and template mismatch");
            }
        }
    }

    private static boolean portalProcessorsAllowed(StructureId structureId,
            List<String> processors) {
        for (PortalSetupFact setup : portalSetups(structureId)) {
            for (boolean airPocket : setup.airPocketProbability == 0.0f
                    ? new boolean[] {false} : setup.airPocketProbability == 1.0f
                            ? new boolean[] {true} : new boolean[] {false, true}) {
                ArrayList<String> expected = portalProcessors(setup, airPocket);
                if (processors.equals(expected)) return true;
            }
        }
        return false;
    }

    private static ArrayList<String> portalProcessors(PortalSetupFact setup,
            boolean airPocket) {
        ArrayList<String> processors = new ArrayList<>();
        processors.add(airPocket ? IGNORE_STRUCTURE_BLOCK : IGNORE_STRUCTURE_AND_AIR);
        processors.add("RuleProcessor(gold_block:0.3->air;lava:" + setup.placement
                + ";netherrack:0.07->magma_if_not_cold)");
        processors.add("BlockAgeProcessor(" + Float.toString(setup.mossiness) + ")");
        processors.add("ProtectedBlockProcessor(#minecraft:features_cannot_replace)");
        processors.add("LavaSubmergedBlockProcessor");
        if (setup.replaceWithBlackstone) processors.add("BlackstoneReplaceProcessor");
        return processors;
    }

    private static boolean templateAllowed(StructureId structureId, String template) {
        return switch (structureId) {
            case SHIPWRECK -> SHIP_OCEAN.contains(template);
            case SHIPWRECK_BEACHED -> SHIP_BEACHED.contains(template);
            case OCEAN_RUIN_COLD -> COLD_BRICK_SMALL.contains(template)
                    || COLD_CRACKED_SMALL.contains(template) || COLD_MOSSY_SMALL.contains(template)
                    || COLD_BRICK_BIG.contains(template) || COLD_CRACKED_BIG.contains(template)
                    || COLD_MOSSY_BIG.contains(template);
            case OCEAN_RUIN_WARM -> WARM_SMALL.contains(template) || WARM_BIG.contains(template);
            case RUINED_PORTAL, RUINED_PORTAL_DESERT, RUINED_PORTAL_JUNGLE,
                    RUINED_PORTAL_MOUNTAIN, RUINED_PORTAL_OCEAN,
                    RUINED_PORTAL_SWAMP -> PORTALS.contains(template)
                            || GIANT_PORTALS.contains(template);
            case WOODLAND_MANSION -> false;
        };
    }

    private static EnumMap<StructureId, Definition> buildDefinitions() {
        EnumMap<StructureId, Definition> values = new EnumMap<>(StructureId.class);
        List<String> shipClasses = List.of(
                "e1b6eef3a33367ea2da10a05fdb20a25d9e117a5cf6714fbe9334447a48f1f15",
                "a6f6c54ba8244cfe7c297ae4ebf50e46a3ddf841edf1a4385b4c068b08d2f365");
        add(values, StructureId.SHIPWRECK, "minecraft:shipwreck",
                "2bdd115d7b33195efa86564c8c60570552c922c14dff68d972146c5a26fc8f0a",
                shipClasses, ConnectorFact.DIRECT_TEMPLATE_PIECE_NO_JIGSAW,
                RotationFact.RANDOM_FOUR_IN_ENUM_ORDER, ProjectionFact.OCEAN_FLOOR_WG_MEAN,
                PivotFact.FIXED_4_0_15, "minecraft:shipwreck_piece",
                List.of(IGNORE_STRUCTURE_AND_AIR));
        add(values, StructureId.SHIPWRECK_BEACHED, "minecraft:shipwreck_beached",
                "1c038a8319b0c53f9b8db49eb7e9ab825728acc82ad69757081388cd2f6d11e4",
                shipClasses, ConnectorFact.DIRECT_TEMPLATE_PIECE_NO_JIGSAW,
                RotationFact.RANDOM_FOUR_IN_ENUM_ORDER,
                ProjectionFact.WORLD_SURFACE_WG_BEACHED_MINIMUM,
                PivotFact.FIXED_4_0_15, "minecraft:shipwreck_piece",
                List.of(IGNORE_STRUCTURE_AND_AIR));
        List<String> oceanClasses = List.of(
                "03a27033b8bb5a0c6ea52c9bc01accf2a9ce81a19c26c2347072029e0c2798f3",
                "c8a3e4ef91f411339f83949b8e849a52471b0f374706f9aa262481c68800c300");
        add(values, StructureId.OCEAN_RUIN_COLD, "minecraft:ocean_ruin_cold",
                "a30893dd35a7c93439f67644a94d591a6608bdddf283ed7dadb62dcc7eeeca4f",
                oceanClasses, ConnectorFact.DIRECT_ORDERED_OVERLAY_PIECES_NO_JIGSAW,
                RotationFact.RANDOM_FOUR_IN_ENUM_ORDER,
                ProjectionFact.OCEAN_FLOOR_WG_FOOTPRINT, PivotFact.ORIGIN,
                "minecraft:ocean_ruin",
                List.of("BlockRotProcessor(integrity)", IGNORE_STRUCTURE_AND_AIR,
                        "CappedProcessor(5):cold_archaeology"));
        add(values, StructureId.OCEAN_RUIN_WARM, "minecraft:ocean_ruin_warm",
                "60d139b7c4b6ef999b429b048fc930ca7a34c7f6ffcad657848abc8f2b079c34",
                oceanClasses, ConnectorFact.DIRECT_TEMPLATE_PIECE_NO_JIGSAW,
                RotationFact.RANDOM_FOUR_IN_ENUM_ORDER,
                ProjectionFact.OCEAN_FLOOR_WG_FOOTPRINT, PivotFact.ORIGIN,
                "minecraft:ocean_ruin",
                List.of("BlockRotProcessor(integrity)", IGNORE_STRUCTURE_AND_AIR,
                        "CappedProcessor(5):warm_archaeology"));
        List<String> portalClasses = List.of(
                "aa18a9d0c784fa22110d120d128f377b9c05ed2175306f4d244b50fb056053c9",
                "7061ab9f10b689888a6e67b53bff80ea14b9debd83b2089ce5b69a3601206929");
        portalDefinition(values, StructureId.RUINED_PORTAL, "minecraft:ruined_portal",
                "e8151a138c44a5c75c5dc4b84883ebb596ae90eca15f44e77149f710bf9a61b7",
                portalClasses);
        portalDefinition(values, StructureId.RUINED_PORTAL_DESERT,
                "minecraft:ruined_portal_desert",
                "3e635eb9c50196cd332ff5cd1783b3bea0f908c3a187a9dead0b330efd456af2",
                portalClasses);
        portalDefinition(values, StructureId.RUINED_PORTAL_JUNGLE,
                "minecraft:ruined_portal_jungle",
                "257ef8e3ab68fd0bb2176c2e0c420ef547a1f2b2e915a215ec74341362f759e5",
                portalClasses);
        portalDefinition(values, StructureId.RUINED_PORTAL_MOUNTAIN,
                "minecraft:ruined_portal_mountain",
                "b0eff0c4a476090c5e6fb099457856cdeff582dd26b864f44d254284ae9d75be",
                portalClasses);
        portalDefinition(values, StructureId.RUINED_PORTAL_OCEAN,
                "minecraft:ruined_portal_ocean",
                "048d15c54b4ea2a517492da1e8b8d339a474bc81311303675f211b717b2bd4a9",
                portalClasses);
        portalDefinition(values, StructureId.RUINED_PORTAL_SWAMP,
                "minecraft:ruined_portal_swamp",
                "45c63cd9db34f4cba3b27817a763556b4bfdf29f6500425b47477ec50d60aa13",
                portalClasses);
        add(values, StructureId.WOODLAND_MANSION, "minecraft:mansion",
                "7ab5844fcd3dbb67a2056228d6ad367a100ea7505b639fa67cf9d72ae56e407d",
                List.of("2ecd8faad13cf944b748e2116ecc51490acb8fcd9c820d5098003777f2cfc82a",
                        "28b220239843ef43887dcd96bdb61b66c0773b9efdf1215b50700e4cd83e23cd"),
                ConnectorFact.PROCEDURAL_GRID_ADJACENCY_UNSUPPORTED_NO_JIGSAW,
                RotationFact.ROOT_RANDOM_FOUR_WITH_DERIVED_PIECE_ROTATIONS,
                ProjectionFact.LOWEST_Y_IN_5_BY_5_OFFSET_7_REJECT_BELOW_60,
                PivotFact.DEFAULT_ORIGIN, "minecraft:woodland_mansion_piece",
                List.of(IGNORE_MANSION_STRUCTURE_BLOCK));
        if (values.size() != StructureId.values().length) fail("incomplete definition catalog");
        return values;
    }

    private static void portalDefinition(EnumMap<StructureId, Definition> values,
            StructureId structureId, String registryId, String jsonSha256,
            List<String> classSha256) {
        add(values, structureId, registryId, jsonSha256, classSha256,
                ConnectorFact.DIRECT_TEMPLATE_PIECE_NO_JIGSAW,
                RotationFact.RANDOM_FOUR_IN_ENUM_ORDER,
                ProjectionFact.SETUP_DEPENDENT_PORTAL_VERTICAL_PLACEMENT,
                PivotFact.TEMPLATE_CENTER_XZ, "minecraft:ruined_portal",
                List.of("air-dependent BlockIgnoreProcessor",
                        "RuleProcessor", "BlockAgeProcessor(mossiness)",
                        "ProtectedBlockProcessor(#minecraft:features_cannot_replace)",
                        "LavaSubmergedBlockProcessor", "optional BlackstoneReplaceProcessor"));
    }

    private static void add(EnumMap<StructureId, Definition> values, StructureId structureId,
            String registryId, String jsonSha256, List<String> classSha256,
            ConnectorFact connector, RotationFact rotation, ProjectionFact projection,
            PivotFact pivot, String pieceType, List<String> processorFacts) {
        Definition previous = values.put(structureId, new Definition(structureId, registryId,
                jsonSha256, classSha256, connector, rotation, projection, pivot, pieceType,
                processorFacts));
        if (previous != null) fail("duplicate definition");
    }

    private static List<PortalSetupFact> portalSetups(StructureId structureId) {
        return switch (structureId) {
            case RUINED_PORTAL -> List.of(
                    new PortalSetupFact("underground", 1.0f, 0.2f, false, false, true, false, 0.5f),
                    new PortalSetupFact("on_land_surface", 0.0f, 0.2f, false, false, true,
                            false, 0.5f));
            case RUINED_PORTAL_DESERT -> List.of(new PortalSetupFact("partly_buried", 0.0f,
                    0.0f, false, false, false, false, 1.0f));
            case RUINED_PORTAL_JUNGLE -> List.of(new PortalSetupFact("on_land_surface", 0.0f,
                    0.8f, true, true, false, false, 1.0f));
            case RUINED_PORTAL_MOUNTAIN -> List.of(
                    new PortalSetupFact("in_mountain", 1.0f, 0.2f, false, false, true, false, 0.5f),
                    new PortalSetupFact("on_land_surface", 0.0f, 0.2f, false, false, true,
                            false, 0.5f));
            case RUINED_PORTAL_OCEAN -> List.of(new PortalSetupFact("on_ocean_floor", 0.0f,
                    0.8f, false, false, true, false, 1.0f));
            case RUINED_PORTAL_SWAMP -> List.of(new PortalSetupFact("on_ocean_floor", 0.0f,
                    0.5f, false, true, false, false, 1.0f));
            default -> throw new IllegalArgumentException("not an Overworld ruined portal");
        };
    }

    private static PortalSetupFact weightedSetup(List<PortalSetupFact> setups, float pick) {
        float total = 0.0f;
        for (PortalSetupFact setup : setups) total += setup.weight;
        for (PortalSetupFact setup : setups) {
            pick -= setup.weight / total;
            if (pick < 0.0f) return setup;
        }
        throw new IllegalStateException("verified setup weights did not select");
    }

    public static final class PortalSetupFact {
        private final String placement;
        private final float airPocketProbability;
        private final float mossiness;
        private final boolean overgrown;
        private final boolean vines;
        private final boolean canBeCold;
        private final boolean replaceWithBlackstone;
        private final float weight;

        private PortalSetupFact(String placement, float airPocketProbability, float mossiness,
                boolean overgrown, boolean vines, boolean canBeCold,
                boolean replaceWithBlackstone, float weight) {
            this.placement = placement;
            this.airPocketProbability = airPocketProbability;
            this.mossiness = mossiness;
            this.overgrown = overgrown;
            this.vines = vines;
            this.canBeCold = canBeCold;
            this.replaceWithBlackstone = replaceWithBlackstone;
            this.weight = weight;
        }

        public String placement() { return placement; }
        public float airPocketProbability() { return airPocketProbability; }
        public float mossiness() { return mossiness; }
        public boolean overgrown() { return overgrown; }
        public boolean vines() { return vines; }
        public boolean canBeCold() { return canBeCold; }
        public boolean replaceWithBlackstone() { return replaceWithBlackstone; }
        public float weight() { return weight; }
    }

    private static final class Cursor {
        private final DrawTape tape;
        private int index;

        private Cursor(DrawTape tape, int startIndex) {
            if (startIndex < 0 || startIndex > tape.draws.size()) {
                throw new IllegalArgumentException("invalid RNG start index");
            }
            this.tape = tape;
            this.index = startIndex;
        }

        private int nextInt(int bound) {
            Draw draw = next(DrawKind.BOUNDED_INT);
            if (draw.bound != bound || draw.valueBits < 0 || draw.valueBits >= bound) {
                fail("RNG bounded-int continuation mismatch");
            }
            return draw.valueBits;
        }

        private float nextFloat() {
            Draw draw = next(DrawKind.UNIT_FLOAT);
            float value = Float.intBitsToFloat(draw.valueBits);
            if (!(value >= 0.0f && value < 1.0f) || !Float.isFinite(value)) {
                fail("RNG unit-float continuation mismatch");
            }
            return value;
        }

        private Draw next(DrawKind expected) {
            if (index >= tape.draws.size()) fail("RNG tape exhausted");
            Draw draw = tape.draws.get(index++);
            if (draw.kind != expected) fail("RNG draw kind mismatch");
            return draw;
        }
    }

    private static List<String> numbered(String prefix, int first, int last) {
        ArrayList<String> values = new ArrayList<>();
        for (int value = first; value <= last; value++) {
            values.add("minecraft:" + prefix + value);
        }
        return List.copyOf(values);
    }

    private static List<String> named(String prefix, int... suffixes) {
        ArrayList<String> values = new ArrayList<>();
        for (int suffix : suffixes) values.add("minecraft:" + prefix + suffix);
        return List.copyOf(values);
    }

    private static int chunkCoordinate(int chunk) {
        return Math.multiplyExact(chunk, 16);
    }

    private static String requireNamespaced(String value, String field) {
        requireText(value, field);
        int colon = value.indexOf(':');
        if (colon <= 0 || colon == value.length() - 1 || value.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException("invalid namespaced " + field);
        }
        return value;
    }

    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (value.isEmpty() || bytes > MAX_STRING_BYTES || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("invalid " + field);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) fail("oversized string");
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        if (length == 0 || length > MAX_STRING_BYTES) fail("invalid string length");
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new EOFException();
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (!Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8))) {
            fail("noncanonical UTF-8");
        }
        return value;
    }

    private static <T> T enumValue(T[] values, int ordinal, String field) {
        if (ordinal < 0 || ordinal >= values.length) fail("invalid " + field);
        return values[ordinal];
    }

    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
