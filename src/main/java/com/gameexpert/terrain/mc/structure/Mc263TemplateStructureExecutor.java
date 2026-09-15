package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263TemplateStructureBoundary.BlockPos;
import com.gameexpert.terrain.mc.structure.Mc263TemplateStructureBoundary.BoundaryStatus;
import com.gameexpert.terrain.mc.structure.Mc263TemplateStructureBoundary.Draw;
import com.gameexpert.terrain.mc.structure.Mc263TemplateStructureBoundary.DrawKind;
import com.gameexpert.terrain.mc.structure.Mc263TemplateStructureBoundary.DrawTape;
import com.gameexpert.terrain.mc.structure.Mc263TemplateStructureBoundary.Mirror;
import com.gameexpert.terrain.mc.structure.Mc263TemplateStructureBoundary.PieceFact;
import com.gameexpert.terrain.mc.structure.Mc263TemplateStructureBoundary.PrefixPlan;
import com.gameexpert.terrain.mc.structure.Mc263TemplateStructureBoundary.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263TemplateStructureBoundary.StructureId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Atomic execution boundary for independently derived procedural replacements for pinned
 * template-backed structures.
 *
 * <p>The class contains no Mojang template geometry and is not wired into generation. A caller
 * supplies a procedure, the verified prefix produced by {@link Mc263TemplateStructureBoundary},
 * and an atomic sink. The procedure can preserve random draws, world queries, connectors, block
 * writes, loot markers, and entity markers in one trace. No mutation is exposed until the whole
 * procedure and its prefix/processor contract have been validated.</p>
 *
 * <p>Validation proves only that an execution obeys this boundary. It does not certify that a
 * caller-supplied procedural grammar reproduces the pinned game.</p>
 */
public final class Mc263TemplateStructureExecutor {
    private static final String IGNORE_STRUCTURE_AND_AIR =
            "BlockIgnoreProcessor.STRUCTURE_AND_AIR";
    private static final String MANSION_PROCESSOR =
            "ignore_entities=true;BlockIgnoreProcessor.STRUCTURE_BLOCK";
    private static final int MAX_PIECES = 1024;
    private static final int MAX_EVENTS = 1_000_000;
    private static final int MAX_TEXT = 512;
    private static final Pattern RESOURCE_KEY =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Set<StructureId> TARGETS = Collections.unmodifiableSet(EnumSet.of(
            StructureId.SHIPWRECK,
            StructureId.SHIPWRECK_BEACHED,
            StructureId.OCEAN_RUIN_COLD,
            StructureId.OCEAN_RUIN_WARM,
            StructureId.RUINED_PORTAL,
            StructureId.RUINED_PORTAL_DESERT,
            StructureId.RUINED_PORTAL_JUNGLE,
            StructureId.RUINED_PORTAL_MOUNTAIN,
            StructureId.RUINED_PORTAL_OCEAN,
            StructureId.RUINED_PORTAL_SWAMP,
            StructureId.WOODLAND_MANSION));

    private Mc263TemplateStructureExecutor() { }

    public static Set<StructureId> supportedTargets() {
        return TARGETS;
    }

    /**
     * Executes a grammar into an atomic batch. The sink is never invoked on validation,
     * arithmetic, RNG-continuation, query, or grammar failure.
     */
    public static Result execute(PrefixPlan prefix, DrawTape tape, Grammar grammar,
            QuerySource queries, Clip clip, AtomicSink sink) {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(tape, "draw tape");
        Objects.requireNonNull(grammar, "grammar");
        Objects.requireNonNull(queries, "query source");
        Objects.requireNonNull(clip, "clip");
        Objects.requireNonNull(sink, "atomic sink");
        validateHeader(prefix, tape, grammar);

        Context context = new Context(prefix, tape, queries, clip);
        grammar.procedure().emit(context);
        context.finish();
        List<Mutation> mutations = List.copyOf(context.mutations);
        List<TraceEvent> trace = List.copyOf(context.trace);
        sink.commit(mutations);
        return new Result(prefix.structureId(), context.cursor, context.pieceCount,
                mutations, trace);
    }

    private static void validateHeader(PrefixPlan prefix, DrawTape tape, Grammar grammar) {
        if (!TARGETS.contains(prefix.structureId())) {
            throw new IllegalArgumentException("structure is outside template executor targets");
        }
        if (grammar.structureId() != prefix.structureId()) {
            throw new IllegalArgumentException("grammar and prefix structure disagree");
        }
        if (!Mc263TemplateStructureBoundary.BASELINE_ID.equals(grammar.baselineId())) {
            throw new IllegalArgumentException("grammar baseline is not pinned 26.3-snapshot-7");
        }
        if (prefix.nextDrawIndex() < 0 || prefix.nextDrawIndex() > tape.draws().size()) {
            throw new IllegalArgumentException("prefix RNG continuation is outside draw tape");
        }
    }

    public interface Procedure {
        void emit(Context context);
    }

    /** The implementation of commit must publish the supplied ordered batch atomically. */
    public interface AtomicSink {
        void commit(List<Mutation> orderedMutations);
    }

    public interface QuerySource {
        int height(Heightmap heightmap, int blockX, int blockZ, PendingView pending);
        String blockState(BlockPos position, PendingView pending);
        String biome(BlockPos position, PendingView pending);
        boolean cold(BlockPos position, PendingView pending);
    }

    public enum Heightmap {
        OCEAN_FLOOR_WG,
        WORLD_SURFACE_WG
    }

    public enum MutationKind {
        BLOCK,
        LOOT,
        ENTITY
    }

    public enum TraceKind {
        DRAW_INT,
        DRAW_FLOAT,
        HEIGHT_QUERY,
        BLOCK_QUERY,
        BIOME_QUERY,
        COLD_QUERY,
        BEGIN_PIECE,
        PROCESSOR,
        CONNECTOR,
        BLOCK,
        LOOT,
        ENTITY,
        CLIPPED_BLOCK,
        CLIPPED_LOOT,
        CLIPPED_ENTITY,
        END_PIECE
    }

    public static final class Grammar {
        private final StructureId structureId;
        private final String baselineId;
        private final Procedure procedure;

        public Grammar(StructureId structureId, String baselineId, Procedure procedure) {
            this.structureId = Objects.requireNonNull(structureId, "structure id");
            this.baselineId = requireText(baselineId, "baseline id");
            this.procedure = Objects.requireNonNull(procedure, "procedure");
        }

        public StructureId structureId() { return structureId; }
        public String baselineId() { return baselineId; }
        public Procedure procedure() { return procedure; }
    }

    /** One procedural piece; its geometry is emitted through {@link Context}. */
    public static final class Piece {
        private final String pieceType;
        private final String templateId;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final int offsetX;
        private final int offsetY;
        private final int offsetZ;
        private final BlockPos pivot;
        private final Rotation rotation;
        private final Mirror mirror;
        private final float integrity;
        private final List<String> orderedProcessors;

        public Piece(String pieceType, String templateId, int sizeX, int sizeY, int sizeZ,
                int offsetX, int offsetY, int offsetZ, BlockPos pivot, Rotation rotation,
                Mirror mirror, float integrity, List<String> orderedProcessors) {
            this.pieceType = requireResource(pieceType, "piece type");
            this.templateId = requireResource(templateId, "template id");
            if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
                throw new IllegalArgumentException("piece dimensions must be positive");
            }
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.pivot = Objects.requireNonNull(pivot, "pivot");
            this.rotation = Objects.requireNonNull(rotation, "rotation");
            this.mirror = Objects.requireNonNull(mirror, "mirror");
            if (!Float.isNaN(integrity)
                    && (!Float.isFinite(integrity) || integrity < 0.0f || integrity > 1.0f)) {
                throw new IllegalArgumentException("piece integrity is outside [0,1]");
            }
            this.integrity = integrity;
            this.orderedProcessors = List.copyOf(orderedProcessors);
            for (String processor : this.orderedProcessors) {
                requireText(processor, "processor");
            }
        }

        public String pieceType() { return pieceType; }
        public String templateId() { return templateId; }
        public int sizeX() { return sizeX; }
        public int sizeY() { return sizeY; }
        public int sizeZ() { return sizeZ; }
        public int offsetX() { return offsetX; }
        public int offsetY() { return offsetY; }
        public int offsetZ() { return offsetZ; }
        public BlockPos pivot() { return pivot; }
        public Rotation rotation() { return rotation; }
        public Mirror mirror() { return mirror; }
        public float integrity() { return integrity; }
        public List<String> orderedProcessors() { return orderedProcessors; }
    }

    public static final class Clip {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        public Clip(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted execution clip");
            }
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public static Clip all() {
            return new Clip(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
                    Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        public boolean contains(BlockPos position) {
            return position.x() >= minX && position.x() <= maxX
                    && position.y() >= minY && position.y() <= maxY
                    && position.z() >= minZ && position.z() <= maxZ;
        }
    }

    public static final class Mutation {
        private final MutationKind kind;
        private final BlockPos position;
        private final String value;
        private final int pieceIndex;
        private final int ordinal;

        private Mutation(MutationKind kind, BlockPos position, String value, int pieceIndex,
                int ordinal) {
            this.kind = kind;
            this.position = position;
            this.value = value;
            this.pieceIndex = pieceIndex;
            this.ordinal = ordinal;
        }

        public MutationKind kind() { return kind; }
        public BlockPos position() { return position; }
        public String value() { return value; }
        public int pieceIndex() { return pieceIndex; }
        public int ordinal() { return ordinal; }
    }

    /** Read-only mutations emitted before the current query, in their exact write order. */
    public static final class PendingView {
        private final List<Mutation> mutations;

        private PendingView(List<Mutation> mutations) {
            this.mutations = mutations;
        }

        public List<Mutation> mutations() { return mutations; }
    }

    public static final class TraceEvent {
        private final TraceKind kind;
        private final BlockPos position;
        private final String value;
        private final int intValue;
        private final int ordinal;

        private TraceEvent(TraceKind kind, BlockPos position, String value, int intValue,
                int ordinal) {
            this.kind = kind;
            this.position = position;
            this.value = value;
            this.intValue = intValue;
            this.ordinal = ordinal;
        }

        public TraceKind kind() { return kind; }
        public BlockPos position() { return position; }
        public String value() { return value; }
        public int intValue() { return intValue; }
        public int ordinal() { return ordinal; }
    }

    public static final class Result {
        private final StructureId structureId;
        private final int nextDrawIndex;
        private final int pieceCount;
        private final List<Mutation> mutations;
        private final List<TraceEvent> trace;

        private Result(StructureId structureId, int nextDrawIndex, int pieceCount,
                List<Mutation> mutations, List<TraceEvent> trace) {
            this.structureId = structureId;
            this.nextDrawIndex = nextDrawIndex;
            this.pieceCount = pieceCount;
            this.mutations = mutations;
            this.trace = trace;
        }

        public StructureId structureId() { return structureId; }
        public int nextDrawIndex() { return nextDrawIndex; }
        public int pieceCount() { return pieceCount; }
        public List<Mutation> mutations() { return mutations; }
        public List<TraceEvent> trace() { return trace; }
    }

    public static final class Context {
        private final PrefixPlan prefix;
        private final DrawTape tape;
        private final QuerySource queries;
        private final Clip clip;
        private final ArrayList<Mutation> mutations = new ArrayList<>();
        private final ArrayList<TraceEvent> trace = new ArrayList<>();
        private final ArrayList<Piece> pieces = new ArrayList<>();
        private int cursor;
        private int pieceCount;
        private Piece activePiece;

        private Context(PrefixPlan prefix, DrawTape tape, QuerySource queries, Clip clip) {
            this.prefix = prefix;
            this.tape = tape;
            this.queries = queries;
            this.clip = clip;
            this.cursor = prefix.nextDrawIndex();
        }

        public int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("RNG bound must be positive");
            Draw draw = nextDraw(DrawKind.BOUNDED_INT);
            if (draw.bound() != bound || draw.intValue() < 0 || draw.intValue() >= bound) {
                throw new IllegalArgumentException("RNG bounded-int continuation mismatch");
            }
            addTrace(TraceKind.DRAW_INT, null, Integer.toString(bound), draw.intValue());
            return draw.intValue();
        }

        public float nextFloat() {
            Draw draw = nextDraw(DrawKind.UNIT_FLOAT);
            float value = draw.floatValue();
            if (!Float.isFinite(value) || value < 0.0f || value >= 1.0f) {
                throw new IllegalArgumentException("RNG unit-float continuation mismatch");
            }
            addTrace(TraceKind.DRAW_FLOAT, null, Float.toString(value),
                    Float.floatToRawIntBits(value));
            return value;
        }

        public int height(Heightmap heightmap, int blockX, int blockZ) {
            Objects.requireNonNull(heightmap, "heightmap");
            int value = queries.height(heightmap, blockX, blockZ, pendingView());
            addTrace(TraceKind.HEIGHT_QUERY, new BlockPos(blockX, value, blockZ),
                    heightmap.name(), value);
            return value;
        }

        public String blockState(BlockPos position) {
            Objects.requireNonNull(position, "position");
            String value = requireText(queries.blockState(position, pendingView()),
                    "queried block state");
            addTrace(TraceKind.BLOCK_QUERY, position, value, 0);
            return value;
        }

        public String biome(BlockPos position) {
            Objects.requireNonNull(position, "position");
            String value = requireResource(queries.biome(position, pendingView()),
                    "queried biome");
            addTrace(TraceKind.BIOME_QUERY, position, value, 0);
            return value;
        }

        public boolean cold(BlockPos position) {
            Objects.requireNonNull(position, "position");
            boolean value = queries.cold(position, pendingView());
            addTrace(TraceKind.COLD_QUERY, position, Boolean.toString(value), value ? 1 : 0);
            return value;
        }

        public void beginPiece(Piece piece) {
            Objects.requireNonNull(piece, "piece");
            if (activePiece != null) throw new IllegalStateException("piece is already active");
            if (pieceCount >= MAX_PIECES) throw new IllegalArgumentException("too many pieces");
            validatePiece(prefix, piece, pieceCount, pieces);
            pieces.add(piece);
            activePiece = piece;
            addTrace(TraceKind.BEGIN_PIECE, worldPosition(piece.pivot()), piece.templateId(),
                    pieceCount);
            for (String processor : piece.orderedProcessors()) {
                addTrace(TraceKind.PROCESSOR, null, processor, pieceCount);
            }
        }

        /** Records a procedural connector in grammar encounter order without placing a block. */
        public void connector(BlockPos localPosition, String name, String target) {
            requireActivePiece();
            requireInsidePiece(localPosition);
            requireResource(name, "connector name");
            requireResource(target, "connector target");
            addTrace(TraceKind.CONNECTOR, worldPosition(localPosition), name + "->" + target,
                    pieceCount);
        }

        public void block(BlockPos localPosition, String blockState) {
            emit(MutationKind.BLOCK, TraceKind.BLOCK, TraceKind.CLIPPED_BLOCK, localPosition,
                    requireText(blockState, "block state"));
        }

        public void loot(BlockPos localPosition, String lootTable) {
            emit(MutationKind.LOOT, TraceKind.LOOT, TraceKind.CLIPPED_LOOT, localPosition,
                    requireResource(lootTable, "loot table"));
        }

        public void entity(BlockPos localPosition, String entityType) {
            emit(MutationKind.ENTITY, TraceKind.ENTITY, TraceKind.CLIPPED_ENTITY, localPosition,
                    requireResource(entityType, "entity type"));
        }

        public void endPiece() {
            requireActivePiece();
            addTrace(TraceKind.END_PIECE, null, activePiece.templateId(), pieceCount);
            activePiece = null;
            pieceCount++;
        }

        private void emit(MutationKind mutationKind, TraceKind traceKind, TraceKind clippedKind,
                BlockPos localPosition, String value) {
            requireActivePiece();
            requireInsidePiece(localPosition);
            BlockPos world = worldPosition(localPosition);
            if (clip.contains(world)) {
                mutations.add(new Mutation(mutationKind, world, value, pieceCount,
                        mutations.size()));
                addTrace(traceKind, world, value, pieceCount);
            } else {
                addTrace(clippedKind, world, value, pieceCount);
            }
        }

        private BlockPos worldPosition(BlockPos localPosition) {
            BlockPos transformed = Mc263TemplateStructureBoundary.transform(localPosition,
                    activePiece.mirror(), activePiece.rotation(), activePiece.pivot());
            return new BlockPos(Math.addExact(prefix.anchorX(),
                            Math.addExact(activePiece.offsetX(), transformed.x())),
                    Math.addExact(activePiece.offsetY(), transformed.y()),
                    Math.addExact(prefix.anchorZ(),
                            Math.addExact(activePiece.offsetZ(), transformed.z())));
        }

        private Draw nextDraw(DrawKind kind) {
            if (cursor >= tape.draws().size()) {
                throw new IllegalArgumentException("RNG tape exhausted");
            }
            Draw draw = tape.draws().get(cursor++);
            if (draw.kind() != kind) throw new IllegalArgumentException("RNG draw kind mismatch");
            return draw;
        }

        private void requireActivePiece() {
            if (activePiece == null) throw new IllegalStateException("no active piece");
        }

        private PendingView pendingView() {
            return new PendingView(List.copyOf(mutations));
        }

        private void requireInsidePiece(BlockPos position) {
            Objects.requireNonNull(position, "position");
            if (position.x() < 0 || position.x() >= activePiece.sizeX()
                    || position.y() < 0 || position.y() >= activePiece.sizeY()
                    || position.z() < 0 || position.z() >= activePiece.sizeZ()) {
                throw new IllegalArgumentException("procedural coordinate is outside piece bounds");
            }
        }

        private void addTrace(TraceKind kind, BlockPos position, String value, int intValue) {
            if (trace.size() >= MAX_EVENTS) throw new IllegalArgumentException("too many events");
            trace.add(new TraceEvent(kind, position, value, intValue, trace.size()));
        }

        private void finish() {
            if (activePiece != null) throw new IllegalStateException("active piece was not ended");
            int prefixPieces = prefix.orderedPieces().size();
            if (pieceCount < prefixPieces) {
                throw new IllegalArgumentException("procedural grammar omitted a selected piece");
            }
            if (prefix.structureId() == StructureId.WOODLAND_MANSION && pieceCount == 0) {
                throw new IllegalArgumentException("mansion grammar emitted no pieces");
            }
            if (prefix.status() == BoundaryStatus.OCEAN_CLUSTER_GRAMMAR_UNSUPPORTED) {
                if (pieceCount == prefixPieces) {
                    throw new IllegalArgumentException("ocean cluster grammar emitted no cluster");
                }
                int extraPieces = pieceCount - prefixPieces;
                int siteCount = prefix.structureId() == StructureId.OCEAN_RUIN_COLD
                        ? extraPieces / 3 : extraPieces;
                if (prefix.structureId() == StructureId.OCEAN_RUIN_COLD
                        && extraPieces % 3 != 0 || siteCount < 1 || siteCount > 8) {
                    throw new IllegalArgumentException("invalid ocean-cluster site count");
                }
            } else if (pieceCount != prefixPieces
                    && prefix.structureId() != StructureId.WOODLAND_MANSION) {
                throw new IllegalArgumentException("grammar added pieces outside pinned prefix");
            }
        }
    }

    private static void validatePiece(PrefixPlan prefix, Piece piece, int pieceIndex,
            List<Piece> priorPieces) {
        if (pieceIndex < prefix.orderedPieces().size()) {
            PieceFact expected = prefix.orderedPieces().get(pieceIndex);
            if (!expected.pieceType().equals(piece.pieceType())
                    || !expected.templateId().equals(piece.templateId())
                    || expected.rotation() != piece.rotation()
                    || expected.mirror() != piece.mirror()
                    || Float.floatToRawIntBits(expected.integrity())
                            != Float.floatToRawIntBits(piece.integrity())
                    || !expected.orderedProcessors().equals(piece.orderedProcessors())) {
                throw new IllegalArgumentException("piece disagrees with selected prefix at "
                        + pieceIndex);
            }
            validatePivotFact(prefix.structureId(), piece);
            return;
        }
        if (prefix.structureId() == StructureId.WOODLAND_MANSION) {
            if (!piece.pieceType().equals("minecraft:woodland_mansion_piece")
                    || !piece.templateId().startsWith("minecraft:woodland_mansion/")
                    || piece.mirror() != Mirror.NONE || !Float.isNaN(piece.integrity())
                    || !piece.orderedProcessors().equals(List.of(MANSION_PROCESSOR))) {
                throw new IllegalArgumentException("unsupported mansion procedural piece");
            }
            if (pieceIndex == 0 && piece.rotation() != prefix.rootRotation()) {
                throw new IllegalArgumentException("mansion root rotation disagrees with prefix");
            }
            validatePivotFact(prefix.structureId(), piece);
            return;
        }
        if (prefix.status() == BoundaryStatus.OCEAN_CLUSTER_GRAMMAR_UNSUPPORTED
                && (prefix.structureId() == StructureId.OCEAN_RUIN_COLD
                        || prefix.structureId() == StructureId.OCEAN_RUIN_WARM)) {
            validateOceanClusterPiece(prefix, piece, pieceIndex, priorPieces);
            return;
        }
        throw new IllegalArgumentException("structure grammar cannot add another piece");
    }

    private static void validatePivotFact(StructureId structureId, Piece piece) {
        BlockPos pivot = piece.pivot();
        switch (structureId) {
            case SHIPWRECK, SHIPWRECK_BEACHED -> {
                if (!pivot.equals(new BlockPos(4, 0, 15))) {
                    throw new IllegalArgumentException("shipwreck pivot is not pinned 4,0,15");
                }
            }
            case OCEAN_RUIN_COLD, OCEAN_RUIN_WARM, WOODLAND_MANSION -> {
                if (!pivot.equals(new BlockPos(0, 0, 0))) {
                    throw new IllegalArgumentException("structure pivot is not the pinned origin");
                }
            }
            case RUINED_PORTAL, RUINED_PORTAL_DESERT, RUINED_PORTAL_JUNGLE,
                    RUINED_PORTAL_MOUNTAIN, RUINED_PORTAL_OCEAN,
                    RUINED_PORTAL_SWAMP -> {
                if (!pivot.equals(new BlockPos(piece.sizeX() / 2, 0,
                        piece.sizeZ() / 2))) {
                    throw new IllegalArgumentException("portal center pivot is invalid");
                }
            }
        }
    }

    private static void validateOceanClusterPiece(PrefixPlan prefix, Piece piece, int pieceIndex,
            List<Piece> priorPieces) {
        boolean cold = prefix.structureId() == StructureId.OCEAN_RUIN_COLD;
        int relativeIndex = pieceIndex - prefix.orderedPieces().size();
        int overlayIndex = cold ? relativeIndex % 3 : 0;
        String family = cold ? overlayIndex == 0 ? "brick_"
                : overlayIndex == 1 ? "cracked_" : "mossy_" : "warm_";
        String pathPrefix = "minecraft:underwater_ruin/" + family;
        String suffix = piece.templateId().startsWith(pathPrefix)
                ? piece.templateId().substring(pathPrefix.length()) : "";
        float expectedIntegrity = cold ? overlayIndex == 0 ? 0.8f
                : overlayIndex == 1 ? 0.7f : 0.5f : 0.8f;
        if (!piece.pieceType().equals("minecraft:ocean_ruin")
                || suffix.length() != 1 || suffix.charAt(0) < '1' || suffix.charAt(0) > '8'
                || piece.mirror() != Mirror.NONE || piece.integrity() != expectedIntegrity
                || !piece.pivot().equals(new BlockPos(0, 0, 0))) {
            throw new IllegalArgumentException("unsupported ocean-cluster piece");
        }
        if (cold && overlayIndex > 0) {
            Piece primary = priorPieces.get(pieceIndex - overlayIndex);
            if (!primary.templateId().endsWith("_" + suffix)
                    || primary.rotation() != piece.rotation()
                    || primary.offsetX() != piece.offsetX()
                    || primary.offsetY() != piece.offsetY()
                    || primary.offsetZ() != piece.offsetZ()) {
                throw new IllegalArgumentException("cold ocean overlays disagree");
            }
        }
        String suspicious = cold
                ? "CappedProcessor(5):gravel->suspicious_gravel+ocean_ruin_cold_archaeology"
                : "CappedProcessor(5):sand->suspicious_sand+ocean_ruin_warm_archaeology";
        if (!piece.orderedProcessors().equals(List.of(
                "BlockRotProcessor(" + Float.toString(expectedIntegrity) + ")",
                IGNORE_STRUCTURE_AND_AIR, suspicious))) {
            throw new IllegalArgumentException("unsupported ocean-cluster processors");
        }
    }

    private static String requireResource(String value, String label) {
        requireText(value, label);
        if (!RESOURCE_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " is not a namespaced key: " + value);
        }
        return value;
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isEmpty() || value.length() > MAX_TEXT || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("invalid " + label);
        }
        return value;
    }
}
