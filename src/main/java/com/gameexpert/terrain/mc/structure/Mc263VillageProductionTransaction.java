package com.gameexpert.terrain.mc.structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.gameexpert.terrain.mc.feature.Mc263WorldGenRegionRandom;
import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.BlockEntitySynthesizer;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Cell;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.CellProcessor;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.CellProgram;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.IgnorePolicy;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Mirror;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Piece;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.PlacementWorld;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.ProcessedCell;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Result;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Vec;
import com.gameexpert.terrain.mc.structure.Mc263VillageProductionAdapters.ProductionWorldTransaction;
import com.gameexpert.terrain.mc.structure.Mc263VillageProductionAdapters.RuleProcessorExecutor;
import com.gameexpert.terrain.mc.structure.Mc263VillageProductionAdapters.RuleResult;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.BentPayload;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.CellPlacement;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.Chunk;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.EffectKind;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.EntityPlacement;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.ExecutionResult;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.FeaturePlacement;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.LootPayload;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.Operation;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.Position;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.RawBlockTick;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.Write;

/**
 * Village production transaction: the settlement-facing adapter that runs the family-neutral
 * {@link Mc263TemplatePlacementExecutor} as the Village template-cell engine and routes the Village
 * rule, pile/non-pile configured-feature and structure-entity authorities into the transaction
 * primitives {@link ProductionWorldTransaction} declares.
 *
 * <p>Only the Village-specific adaptation lives here; the vanilla {@code placeInWorld} loop, the
 * mirror-before-rotation transform, {@code keepLiquids} restoration, the block-entity clear write
 * and the loot {@code nextLong()} derivation all stay in the shared executor.</p>
 *
 * <h2>Rule lane</h2>
 * <p>{@link Mc263VillageProductionAuthority.Corpus#executeRuleProcessor} is adapted into a
 * {@link CellProcessor}. The executor hands the processor its recording world, and
 * {@link Host#ruleBlockStateAt(Position)} is bound to exactly that world for the duration of the
 * cell, so a {@code minecraft:rule} location predicate's {@code getBlockState} probe is transcribed
 * at its real position in the operation transcript instead of being an untranscribed side query.
 * The rule semantics of one cell run in the placement's processor order, which is the order
 * {@link Mc263VillageProductionAdapters}' ordered executor enforces.</p>
 *
 * <h2>Projection lane</h2>
 * <p>{@code SinglePoolElement.getSettings} appends the pool projection's processors last, after the
 * element's own list, so {@link Mc263GravityProcessorAuthority} is installed unconditionally at the
 * end of every cell's chain. It is the identity for a {@code rigid} piece and issues no world query
 * there, and it is the {@code GravityProcessor(WORLD_SURFACE_WG, -1)} of a
 * {@code terrain_matching} piece. Because the Village seam collapses a cell's local coordinates
 * into its final position, the gravity link is addressed through
 * {@link Mc263GravityProcessorAuthority#processRelative} with the cell's real template-local Y.</p>
 *
 * <h2>Cell granularity</h2>
 * <p>{@link Mc263VillageSettlement} presents template placement one cell at a time, but
 * {@code placeInWorld} is a two-phase loop over a whole piece: {@code processBlockInfos} first, the
 * write loop second. {@link #executeTemplatePiece} is therefore the real seam and
 * {@link #executeTemplateCell} its single-cell case. A piece's cells carry state and position the
 * settlement already transformed, so the {@link Piece} is placed at a zero origin with
 * {@link Rotation#NONE}/{@link Mirror#NONE} and the executor's own transform is the identity.</p>
 */
public final class Mc263VillageProductionTransaction {
    /** {@code minecraft:rule} runtime class the Village start graph names for a rule processor. */
    public static final String RULE_PROCESSOR_CLASS =
            "net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor";
    /** Prefix of every promoted Village rule semantic id, {@code minecraft:rule#<row sha256>}. */
    public static final String RULE_SEMANTIC_PREFIX = "minecraft:rule#";

    private Mc263VillageProductionTransaction() { }

    /**
     * One template cell as the settlement presents it: local template coordinates, the already
     * rotated exact state and the already transformed world position.
     */
    public static final class TemplateCell {
        private final int pieceOrdinal, cellOrdinal, localX, localY, localZ;
        private final String templateKey, exactState, rotation, projection;
        private final List<String> processors;
        private final Position transformedPosition;
        private final String markerOp, markerSemanticJson, blockEntityType, lootTable;

        public TemplateCell(int pieceOrdinal, String templateKey, int cellOrdinal, int localX,
                int localY, int localZ, Position transformedPosition, String exactState,
                String rotation, String projection, List<String> processors, String markerOp,
                String markerSemanticJson, String blockEntityType, String lootTable) {
            if (pieceOrdinal < 0 || cellOrdinal < 0) {
                throw new IllegalArgumentException("negative Village template cell ordinal");
            }
            this.pieceOrdinal = pieceOrdinal;
            this.templateKey = Objects.requireNonNull(templateKey, "Village template key");
            this.cellOrdinal = cellOrdinal;
            this.localX = localX; this.localY = localY; this.localZ = localZ;
            this.transformedPosition = Objects.requireNonNull(transformedPosition,
                    "Village template cell position");
            this.exactState = Objects.requireNonNull(exactState, "Village template cell state");
            this.rotation = Objects.requireNonNull(rotation, "Village template cell rotation");
            this.projection = Objects.requireNonNull(projection, "Village template cell projection");
            this.processors = List.copyOf(processors);
            this.markerOp = markerOp; this.markerSemanticJson = markerSemanticJson;
            this.blockEntityType = blockEntityType; this.lootTable = lootTable;
        }

        public static TemplateCell from(CellPlacement placement) {
            Objects.requireNonNull(placement, "Village cell placement");
            return new TemplateCell(placement.pieceOrdinal(), placement.templateKey(),
                    placement.cellOrdinal(), placement.localX(), placement.localY(),
                    placement.localZ(), placement.transformedPosition(), placement.exactState(),
                    placement.rotation(), placement.projection(), placement.processors(),
                    placement.markerOp(), placement.markerSemanticJson(),
                    placement.blockEntityType(), placement.lootTable());
        }

        public int pieceOrdinal() { return pieceOrdinal; }
        public String templateKey() { return templateKey; }
        public int cellOrdinal() { return cellOrdinal; }
        public int localX() { return localX; }
        public int localY() { return localY; }
        public int localZ() { return localZ; }
        public Position transformedPosition() { return transformedPosition; }
        public String exactState() { return exactState; }
        public String rotation() { return rotation; }
        public String projection() { return projection; }
        public List<String> processors() { return processors; }
        public String markerOp() { return markerOp; }
        public String markerSemanticJson() { return markerSemanticJson; }
        public String blockEntityType() { return blockEntityType; }
        public String lootTable() { return lootTable; }
    }

    /**
     * Rule invocation seam. The recording world is supplied so the host can bind its rule
     * location-predicate probe to the transcribing world for exactly this cell.
     */
    public interface RuleInvoker {
        RuleResult execute(String semanticId, String inputState, byte[] inputNbt,
                Position templateRelativePosition, Position processedWorldPosition,
                Position referencePosition, PlacementWorld recordingWorld);
    }

    /** Executes one Village template cell through the shared placement executor. */
    public static Result executeTemplateCell(TemplateCell cell,
            Mc263TemplatePlacementExecutor.Clip clip, PlacementWorld world, RuleInvoker rules,
            BlockEntitySynthesizer synthesizer,
            Mc263TemplatePlacementExecutor.PlacementRandom random) {
        Objects.requireNonNull(cell, "Village template cell");
        return executeTemplatePiece(List.of(cell), clip, world, rules, synthesizer, random);
    }

    /**
     * Executes the in-clip cells of exactly one Village template piece, in the caller's cell order.
     *
     * <p>This is the piece-granular seam. {@code StructureTemplate.placeInWorld} runs
     * {@code processBlockInfos} over the whole piece and only then walks the processed list writing
     * blocks, so a piece's every processor query precedes its every write and its {@code keepLiquids}
     * relaxation sees the complete piece. Driving one cell at a time interleaves those two phases,
     * which is invisible for a processor-free {@code rigid} piece but reorders the transcript of any
     * piece whose chain queries the world — every {@code terrain_matching} piece, whose projection
     * installs {@link Mc263GravityProcessorAuthority}, and every rule piece with a location
     * predicate. {@link #executeTemplateCell} stays the single-cell case of this same call.</p>
     */
    public static Result executeTemplatePiece(List<TemplateCell> cells,
            Mc263TemplatePlacementExecutor.Clip clip, PlacementWorld world, RuleInvoker rules,
            BlockEntitySynthesizer synthesizer,
            Mc263TemplatePlacementExecutor.PlacementRandom random) {
        Objects.requireNonNull(cells, "Village template piece cells");
        Objects.requireNonNull(clip, "Village template clip");
        Objects.requireNonNull(world, "Village template world");
        if (cells.isEmpty()) throw new IllegalArgumentException("empty Village template piece");
        TemplateCell first = cells.get(0);
        for (TemplateCell cell : cells) {
            if (cell.pieceOrdinal() != first.pieceOrdinal()
                    || !cell.templateKey().equals(first.templateKey())
                    || !cell.projection().equals(first.projection())
                    || !cell.processors().equals(first.processors())) {
                throw new IllegalArgumentException(
                        "Village template piece mixes cells of different pieces");
            }
        }
        Piece piece = new Piece(first.pieceOrdinal(), new PieceProgram(first, cells),
                new Vec(0, 0, 0), Rotation.NONE, Mirror.NONE, first.projection(),
                first.processors(), IgnorePolicy.STRUCTURE_BLOCK, true);
        return Mc263TemplatePlacementExecutor.execute(List.of(piece), clip, world,
                new VillageCellChain(rules), synthesizer, random);
    }

    /** Converts an executor result into the settlement's typed primitive receipt. */
    public static ExecutionResult toExecutionResult(Result result, int tickPriority) {
        Objects.requireNonNull(result, "Village executor result");
        ArrayList<Operation> operations = new ArrayList<>(result.operations().size());
        for (int index = 0; index < result.operations().size(); index++) {
            String row = result.operations().get(index);
            String detail = row.substring(row.indexOf('|') + 1);
            operations.add(new Operation(index, kindOf(detail), null, detail));
        }
        ArrayList<Write> writes = new ArrayList<>(result.finalCells().size());
        for (Mc263TemplatePlacementExecutor.FinalCell cell : result.finalCells()) {
            writes.add(new Write(position(cell.position()), cell.exactState(),
                    Mc263TemplatePlacementExecutor.TEMPLATE_WRITE_FLAGS, true));
        }
        ArrayList<BentPayload> bent = new ArrayList<>(result.bent().size());
        for (Mc263TemplatePlacementExecutor.BentRow row : result.bent()) {
            bent.add(new BentPayload(position(row.position()), row.blockEntityType(),
                    row.canonicalNbt()));
        }
        ArrayList<LootPayload> loot = new ArrayList<>(result.loot().size());
        for (Mc263TemplatePlacementExecutor.LootRow row : result.loot()) {
            loot.add(new LootPayload(position(row.position()), row.table(), row.signedSeed()));
        }
        ArrayList<RawBlockTick> ticks = new ArrayList<>(result.blockTicks().size());
        for (int index = 0; index < result.blockTicks().size(); index++) {
            Mc263TemplatePlacementExecutor.TickRow row = result.blockTicks().get(index);
            Position position = position(row.position());
            ticks.add(new RawBlockTick(index, Chunk.from(position), position, row.key(),
                    row.delay(), tickPriority, row.subTickOrder()));
        }
        return new ExecutionResult(operations, writes, bent, loot, List.of(), ticks);
    }

    private static EffectKind kindOf(String detail) {
        int bar = detail.indexOf('|');
        String name = bar < 0 ? detail : detail.substring(0, bar);
        return switch (name) {
            case "setBlock" -> EffectKind.WRITE;
            case "scheduleTick" -> EffectKind.BLOCK_TICK;
            case "addFreshEntityWithPassengers" -> EffectKind.ENTITY;
            default -> EffectKind.QUERY;
        };
    }

    private static Position position(Vec value) {
        return new Position(value.x(), value.y(), value.z());
    }

    private static Vec vec(Position value) {
        return new Vec(value.x(), value.y(), value.z());
    }

    /**
     * The settlement's cells of one piece as a neutral {@link CellProgram}. The settlement already
     * applied the piece transform, so the program is placed at a zero origin with the identity
     * transform and each {@link Cell}'s "local" coordinates are its final world position; the raw
     * template-local coordinates travel alongside on {@link PlacedCell#source()} for the processors
     * that need them.
     */
    private static final class PieceProgram implements CellProgram {
        private final TemplateCell first;
        private final List<Cell> cells;

        private PieceProgram(TemplateCell first, List<TemplateCell> sources) {
            this.first = first;
            ArrayList<Cell> placed = new ArrayList<>(sources.size());
            for (TemplateCell source : sources) placed.add(new PlacedCell(source));
            this.cells = List.copyOf(placed);
        }

        @Override public String templateKey() { return first.templateKey(); }
        @Override public List<Cell> cellsInPlacementOrder() { return cells; }
    }

    /** One already-transformed settlement cell presented to the neutral executor. */
    private static final class PlacedCell implements Cell {
        private final TemplateCell source;

        private PlacedCell(TemplateCell source) { this.source = source; }

        TemplateCell source() { return source; }

        @Override public int localX() { return source.transformedPosition().x(); }
        @Override public int localY() { return source.transformedPosition().y(); }
        @Override public int localZ() { return source.transformedPosition().z(); }
        @Override public String exactState() { return source.exactState(); }
        @Override public String blockEntityType() { return source.blockEntityType(); }
        @Override public String lootTable() { return source.lootTable(); }
        @Override public String markerOp() { return source.markerOp(); }
        @Override public String markerSemanticJson() { return source.markerSemanticJson(); }
        /**
         * {@code JigsawReplacementProcessor} substitutes the connector's raw {@code final_state}
         * string, which {@code StructureTemplate.placeInWorld} then mirrors and rotates with every
         * other processed state. The Village seam hands the executor cells the settlement already
         * transformed, so the piece transform is the identity there and the substituted state would
         * otherwise stay in template space; the piece rotation is therefore applied here, exactly
         * once, to the parsed {@code final_state}. Village jigsaw placement never mirrors.
         */
        @Override public String jigsawFinalState() {
            String finalState = connectorFinalState(source.markerSemanticJson());
            if (finalState == null) return null;
            return Mc263TemplatePlacementExecutor.transformState(finalState, Mirror.NONE,
                    Rotation.valueOf(source.rotation()));
        }
    }

    /**
     * Whether one template cell still reaches the element/projection processor chain.
     *
     * <p>{@code SinglePoolElement.getSettings} installs {@code JigsawReplacementProcessor} and
     * {@code BlockIgnoreProcessor.STRUCTURE_BLOCK} ahead of every element and projection
     * processor, and {@code StructureTemplate.processBlockInfos} drops a cell those two remove
     * before any later processor sees it: a {@code minecraft:jigsaw} cell whose block entity's
     * {@code final_state} is {@code minecraft:structure_void} disappears outright, as does a
     * {@code minecraft:structure_block} or {@code minecraft:structure_void} cell. A dropped cell
     * therefore never executes the element's rule processors, and demanding that it did is a
     * false fail-closed. A jigsaw cell with any other {@code final_state} keeps its position and
     * runs the whole chain on the substituted state.</p>
     */
    public static boolean reachesProcessorChain(CellPlacement placement) {
        Objects.requireNonNull(placement, "Village cell placement");
        return reachesProcessorChain(placement.exactState(), placement.markerSemanticJson());
    }

    /** {@link #reachesProcessorChain(CellPlacement)} over one cell's raw state and semantics. */
    public static boolean reachesProcessorChain(String exactState, String markerSemanticJson) {
        Objects.requireNonNull(exactState, "Village cell state");
        String block = blockKey(exactState);
        if (Mc263TemplatePlacementExecutor.STRUCTURE_BLOCK.equals(block)
                || Mc263TemplatePlacementExecutor.STRUCTURE_VOID.equals(block)) {
            return false;
        }
        if (!Mc263TemplatePlacementExecutor.JIGSAW_BLOCK.equals(block)) return true;
        String finalState = connectorFinalState(markerSemanticJson);
        if (finalState == null) return true;
        return !Mc263TemplatePlacementExecutor.STRUCTURE_VOID.equals(blockKey(finalState));
    }

    /** Block key of an exact state, i.e. the identifier ahead of its property list. */
    private static String blockKey(String exactState) {
        int open = exactState.indexOf('[');
        return open < 0 ? exactState : exactState.substring(0, open);
    }

    /** {@code final_state} field of the authenticated connector semantics, or {@code null}. */
    static String connectorFinalState(String markerSemanticJson) {
        if (markerSemanticJson == null) return null;
        int key = markerSemanticJson.indexOf(FINAL_STATE_FIELD);
        if (key < 0) return null;
        int open = key + FINAL_STATE_FIELD.length();
        int close = markerSemanticJson.indexOf('"', open);
        if (close < 0) {
            throw new IllegalArgumentException(
                    "unterminated Village connector finalState: " + markerSemanticJson);
        }
        return markerSemanticJson.substring(open, close);
    }

    private static final String FINAL_STATE_FIELD = "\"finalState\":\"";
    /** Marker op of the Village cell program's block-entity semantics. */
    public static final String DATA_MARKER_OP = "DATA";
    private static final String BLOCK_ENTITY_TYPE_FIELD = "\"blockEntityType\":\"";
    private static final String LOOT_TABLE_FIELD = "\"lootTable\":\"";

    /**
     * Block-entity type of a {@code DATA} marker cell's authenticated semantics, or {@code null}.
     *
     * <p>Both promoted DATA kinds name their type: {@code EMPTY_BLOCK_ENTITY} creates the bare block
     * entity {@code placeInWorld} clears with its {@code minecraft:barrier} write, and
     * {@code LOOT_CONTAINER} additionally names the table whose seed
     * {@code RandomizableContainer.setLootTable} draws with one placement {@code nextLong()}.</p>
     */
    public static String dataBlockEntityType(String markerOp, String markerSemanticJson) {
        return dataField(markerOp, markerSemanticJson, BLOCK_ENTITY_TYPE_FIELD);
    }

    /** Loot table of a {@code DATA} {@code LOOT_CONTAINER} marker cell, or {@code null}. */
    public static String dataLootTable(String markerOp, String markerSemanticJson) {
        return dataField(markerOp, markerSemanticJson, LOOT_TABLE_FIELD);
    }

    private static String dataField(String markerOp, String markerSemanticJson, String field) {
        if (!DATA_MARKER_OP.equals(markerOp) || markerSemanticJson == null) return null;
        int key = markerSemanticJson.indexOf(field);
        if (key < 0) return null;
        int open = key + field.length();
        int close = markerSemanticJson.indexOf('"', open);
        if (close < 0) {
            throw new IllegalArgumentException(
                    "unterminated Village DATA marker field " + field + ": " + markerSemanticJson);
        }
        return markerSemanticJson.substring(open, close);
    }

    /**
     * The cell's complete {@code SinglePoolElement.getSettings} processor chain: the element's own
     * promoted processors first, then the pool projection's processors last. {@code RIGID}
     * contributes none, so the gravity link is the identity for a rigid cell and issues no world
     * query; {@code TERRAIN_MATCHING} contributes exactly
     * {@code new GravityProcessor(WORLD_SURFACE_WG, -1)}.
     *
     * <p>The Village seam collapses one cell into a one-cell {@link Piece} whose
     * {@link Cell} local coordinates are the origin, so the gravity link is addressed through
     * {@link Mc263GravityProcessorAuthority#processRelative} with the template cell's real
     * template-local Y — {@code blockInfo.pos().getY()} in the pinned bytecode.</p>
     */
    private static final class VillageCellChain implements CellProcessor {
        private final RuleInvoker rules;

        private VillageCellChain(RuleInvoker rules) { this.rules = rules; }

        @Override
        public ProcessedCell process(Piece piece, Cell source, ProcessedCell input,
                PlacementWorld world) {
            TemplateCell cell = ((PlacedCell) source).source();
            ProcessedCell current = input;
            if (rules != null) {
                Position relative = new Position(cell.localX(), cell.localY(), cell.localZ());
                for (String semantic : promotedRuleSemantics(piece.processors())) {
                    Position processed = position(current.position());
                    RuleResult result = Objects.requireNonNull(rules.execute(semantic,
                            current.exactState(), current.inputNbt(), relative, processed,
                            processed, world), "Village rule execution result");
                    current = new ProcessedCell(current.position(), result.outputState(),
                            result.outputNbt());
                }
            }
            return Mc263GravityProcessorAuthority.terrainMatching()
                    .processRelative(piece.projection(), cell.localY(), current, world);
        }
    }

    /**
     * Settlement-facing {@link ProductionWorldTransaction} base. The host supplies the world seam,
     * the source clip and the sidecar authorities' world views; every template cell, configured
     * feature and structure entity then runs through the landed Village authorities.
     */
    public abstract static class Host implements ProductionWorldTransaction {
        private static final Set<String> PILE_FEATURES =
                Set.copyOf(Mc263VillagePileFeatureAuthority.featureKeys());

        private PlacementWorld activeRuleWorld;

        /** Transcribing-safe world seam for template placement. */
        protected abstract PlacementWorld placementWorld();

        /** Inclusive clip of the source chunk currently being produced. */
        protected abstract Mc263TemplatePlacementExecutor.Clip placementClip();

        /** Canonical block-entity NBT authority derived only from authenticated DATA facts. */
        protected BlockEntitySynthesizer blockEntitySynthesizer() {
            return (piece, cell, position, exactState, blockEntityType, lootTable, lootSeed) ->
                    Mc263VillageBlockEntityAuthority.synthesize(position.x(), position.y(),
                            position.z(), blockEntityType, lootTable, lootSeed);
        }

        /** Non-pile configured-feature world view. */
        protected abstract com.gameexpert.terrain.mc.feature.Mc263FeatureWorldAdapter featureWorld();

        @Override
        public ExecutionResult executeTemplateCellWithRuleAuthority(CellPlacement placement,
                Mc263VillageSettlement.PlacementRandom random,
                RuleProcessorExecutor ruleProcessorExecutor) {
            return runTemplateCell(placement, ruleProcessorExecutor, random::nextLong);
        }

        @Override
        public ExecutionResult executePersistedTemplateCellWithRuleAuthority(CellPlacement placement,
                Mc263WorldGenRegionRandom random, RuleProcessorExecutor ruleProcessorExecutor) {
            Objects.requireNonNull(random, "Village persisted template WGR");
            return runTemplateCell(placement, ruleProcessorExecutor, random::nextLong);
        }

        private ExecutionResult runTemplateCell(CellPlacement placement,
                RuleProcessorExecutor ruleProcessorExecutor,
                Mc263TemplatePlacementExecutor.PlacementRandom random) {
            Objects.requireNonNull(placement, "Village cell placement");
            Objects.requireNonNull(ruleProcessorExecutor, "Village rule executor");
            TemplateCell cell = TemplateCell.from(placement);
            Result result = Mc263VillageProductionTransaction.executeTemplateCell(
                    cell, placementClip(), placementWorld(),
                    (semantic, state, nbt, relative, processed, reference, world) -> {
                        PlacementWorld previous = activeRuleWorld;
                        activeRuleWorld = world;
                        try {
                            return ruleProcessorExecutor.execute(semantic, state, nbt, relative,
                                    processed, reference);
                        } finally {
                            activeRuleWorld = previous;
                        }
                    },
                    blockEntitySynthesizer(), random);
            return toExecutionResult(result, 0);
        }

        /**
         * Rule location predicate probe. While a cell is being processed this is the executor's
         * recording world, so the probe is transcribed in corpus order.
         */
        @Override
        public String ruleBlockStateAt(Position processedWorldPosition) {
            Objects.requireNonNull(processedWorldPosition, "Village rule position");
            PlacementWorld world = activeRuleWorld == null ? placementWorld() : activeRuleWorld;
            return world.getBlockState(vec(processedWorldPosition));
        }

        @Override public boolean pileIsEmptyBlock(Position position) {
            return Mc263TemplatePlacementExecutor.AIR_BLOCK.equals(
                    blockKey(placementWorld().getBlockState(vec(position))));
        }

        @Override public String pileSupportBlockState(Position position) {
            return placementWorld().getBlockState(vec(position));
        }

        @Override public boolean pileIsFaceSturdyUp(Position position, String exactState) {
            return Mc263FeatureBlockState.fromExact(exactState).isFaceSturdy(
                    Mc263FeatureBlockState.OcclusionFace.UP);
        }

        @Override public boolean pileSetBlock(Position position, String exactState, int flags) {
            return placementWorld().setBlock(vec(position), exactState, flags);
        }

        @Override
        public ExecutionResult executeAuthenticatedConfiguredFeature(FeaturePlacement placement,
                Mc263WorldGenRegionRandom random) {
            Objects.requireNonNull(placement, "Village configured-feature placement");
            Objects.requireNonNull(random, "Village configured-feature WGR");
            if (PILE_FEATURES.contains(placement.featureKey())) {
                throw new IllegalStateException(
                        "Village pile feature must run through the pile authority: "
                                + placement.featureKey());
            }
            Mc263VillageNonPileFeatureAuthority.Receipt receipt = Objects.requireNonNull(
                    Mc263VillageNonPileFeatureAuthority.execute(placement, random, featureWorld()),
                    "Village non-pile feature receipt");
            ExecutionResult effects = Objects.requireNonNull(drainFeatureEffects(),
                    "Village non-pile feature effects");
            if (!receipt.placed() && !effects.writes().isEmpty()) {
                throw new IllegalStateException(
                        "Village non-pile feature wrote without placing: " + placement.featureKey());
            }
            return effects;
        }

        /**
         * Transcript and writes the non-pile configured-feature authority produced through the
         * host's recording {@link com.gameexpert.terrain.mc.feature.Mc263FeatureWorldAdapter}; the
         * authority itself returns only its statistical receipt.
         */
        protected abstract ExecutionResult drainFeatureEffects();

        /**
         * Authenticated finalize request for one template entity. The Village template semantic
         * JSON to {@link Mc263VillageEntityAuthority.TemplateFacts} binding is host evidence, so it
         * is supplied rather than guessed here.
         */
        protected Mc263VillageEntityAuthority.EntityRequest entityRequest(
                EntityPlacement placement) {
            Mc263VillageTemplateEntityFacts.Entry facts =
                    Mc263VillageTemplateEntityFacts.pinned().require(placement.templateKey(),
                            placement.entityOrdinal(), placement.semanticJson());
            long[] positionBits = {
                    Double.doubleToRawLongBits(placement.position().x()),
                    Double.doubleToRawLongBits(placement.position().y()),
                    Double.doubleToRawLongBits(placement.position().z())
            };
            int[] rotationBits = facts.templateRotationBits();
            float yaw = Float.intBitsToFloat(rotationBits[0]);
            yaw += switch (Rotation.valueOf(placement.rotation())) {
                case NONE -> 0.0F;
                case CLOCKWISE_90 -> 90.0F;
                case CLOCKWISE_180 -> 180.0F;
                case COUNTERCLOCKWISE_90 -> 270.0F;
            };
            rotationBits[0] = Float.floatToRawIntBits(yaw);
            return new Mc263VillageEntityAuthority.EntityRequest(placement.templateKey(),
                    placement.entityOrdinal(), facts.entityKey(), positionBits, rotationBits,
                    facts.templateMotionBits(), facts.facts());
        }

        /**
         * {@code StructureTemplate.loadEntities} tail seam: the transcript descriptor of
         * {@code ServerLevel.getCurrentDifficultyAt(blockPosition)}, the query
         * {@code Mob.finalizeSpawn} issues before the entity is added. The authenticated corpus row
         * form is {@code <name>:effectiveBits=<int>:specialBits=<int>}, and
         * {@link Mc263VillageEntityAuthority.Difficulty} authenticates only {@code easy}.
         */
        protected String currentDifficultyAt(Position blockPosition) {
            Objects.requireNonNull(blockPosition, "Village difficulty position");
            // Fresh world-generation chunks have zero inhabited time. On EASY the pinned
            // DifficultyInstance baseline is 0.75F and its special multiplier is zero.
            return "easy:effectiveBits=" + Float.floatToRawIntBits(0.75F) + ":specialBits=0";
        }

        /**
         * The authenticated structure-entity tail, in the corpus row order: the difficulty query at
         * the entity's block position, then the generated entity's
         * {@code addFreshEntityWithPassengers}. The entity itself is derived by
         * {@link Mc263VillageEntityAuthority}, which consumes the caller WGR between the two rows.
         */
        @Override
        public ExecutionResult executeStructureEntity(EntityPlacement placement,
                Mc263WorldGenRegionRandom random) {
            Objects.requireNonNull(placement, "Village structure-entity placement");
            Objects.requireNonNull(random, "Village structure-entity WGR");
            Mc263VillageEntityAuthority.EntityRequest request = Objects.requireNonNull(
                    entityRequest(placement), "Village structure-entity request");
            // Mob.finalizeSpawn queries getCurrentDifficultyAt(this.blockPosition()) — the floor of
            // the entity's own transformed position, which a rotated template can place in a
            // different block than the template's stored entity block position.
            Position spawnBlock = blockPositionOf(placement.position());
            String difficulty = Objects.requireNonNull(
                    currentDifficultyAt(spawnBlock), "Village entity difficulty");
            ArrayList<Operation> operations = new ArrayList<>(2);
            operations.add(new Operation(0, EffectKind.QUERY, null,
                    "getCurrentDifficultyAt|pos:" + vec(spawnBlock) + "->difficulty:" + difficulty));
            Mc263VillageEntityAuthority.GeneratedEntity generated =
                    Mc263VillageProductionAdapters.spawnStructureEntity(structureKey(), request,
                            random);
            generatedStructureEntity(generated);
            operations.add(new Operation(1, EffectKind.ENTITY, null,
                    "addFreshEntityWithPassengers|entity:" + generated.entityKey() + "->null"));
            long[] bits = generated.positionBits();
            Mc263VillageSettlement.DoublePosition position = new Mc263VillageSettlement.DoublePosition(
                    Double.longBitsToDouble(bits[0]), Double.longBitsToDouble(bits[1]),
                    Double.longBitsToDouble(bits[2]));
            return new ExecutionResult(List.copyOf(operations), List.of(), List.of(), List.of(),
                    List.of(new Mc263VillageSettlement.EntityPayload(generated.entityKey(), position,
                            generated.canonicalNbt())),
                    List.of());
        }

        /** Publication hook for a production host that carries typed ENTS sidecars atomically. */
        protected void generatedStructureEntity(
                Mc263VillageEntityAuthority.GeneratedEntity generated) { }

        /** Village family key this transaction produces. */
        protected abstract String structureKey();

        private static String blockKey(String state) {
            int property = state.indexOf('[');
            return property < 0 ? state.trim() : state.substring(0, property).trim();
        }
    }

    /** {@code BlockPos.containing}: the block an entity at this exact position occupies. */
    public static Position blockPositionOf(Mc263VillageSettlement.DoublePosition position) {
        Objects.requireNonNull(position, "Village entity position");
        return new Position((int) Math.floor(position.x()), (int) Math.floor(position.y()),
                (int) Math.floor(position.z()));
    }

    /** The promoted rule semantics of a placement's processor list, in placement order. */
    public static List<String> promotedRuleSemantics(List<String> processors) {
        HashSet<String> promoted = new HashSet<>();
        for (Mc263VillageProductionAuthority.RuleProcessorBody body
                : Mc263VillageProductionAuthority.pinned().ruleProcessorsInOrder()) {
            promoted.add(body.semanticId());
        }
        ArrayList<String> ordered = new ArrayList<>();
        for (String semantic : processors) if (promoted.contains(semantic)) ordered.add(semantic);
        return List.copyOf(ordered);
    }
}
