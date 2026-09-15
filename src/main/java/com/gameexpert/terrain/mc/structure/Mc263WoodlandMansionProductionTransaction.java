package com.gameexpert.terrain.mc.structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Cell;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.CellProgram;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.IgnorePolicy;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Piece;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.PlacementWorld;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Result;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Vec;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionProductionAuthority.Command;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionProductionAuthority.DataKind;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionProductionAuthority.Local;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionProductionAuthority.Template;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.BentPayload;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.CanonicalNbt;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.CellEffects;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.CellPlacement;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.EntityPayload;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.FluidState;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.LootPayload;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.MarkerEffects;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.MarkerPlacement;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.PlacementRandom;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.Position;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.PublishStatus;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.ServerLevelRandom;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.ServerRandomAuthority;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.ServerRandomState;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.Settlement;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.WorldTransaction;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.WorldWrite;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement.WriteCause;

/**
 * Woodland Mansion production transaction: the settlement-facing
 * {@link Mc263WoodlandMansionSettlement.WorldTransaction} that runs the family-neutral
 * {@link Mc263TemplatePlacementExecutor} as the Mansion template-cell engine and answers every
 * capability and authority question out of the landed resource readers.
 *
 * <p>Nothing here is transcribed. Every accepted fact comes from a pinned resource:</p>
 * <ul>
 *   <li>{@link Mc263WoodlandMansionProductionAuthority} ({@code WMN263P1}) supplies the template
 *       closure, the 417-state exact closure, the sole {@code BlockIgnoreProcessor:STRUCTURE_BLOCK}
 *       processor, the placement settings {@code W} rows ({@code writeFlags=2},
 *       {@code nbtBarrierFlags=820}, {@code keepLiquids=true}), the per-template {@code R}/{@code D}
 *       command program, the canonical {@code B} block-entity preimages, the {@code C} marker-chest
 *       cells, the {@code N} marker-chest facing table, the {@code Y} official
 *       {@code StairBlock#mirror} closure, the {@code Z} official door-hinge/rail-shape
 *       mirror-before-rotation closure and the {@code K} marker-chest rules.</li>
 *   <li>{@link Mc263WoodlandMansionGrammar} supplies the accepted marker vocabulary and the
 *       per-template marker order the entity lane addresses.</li>
 *   <li>{@link Mc263WoodlandMansionEntityAuthority} owns every {@code Mage}/{@code Warrior}/
 *       {@code Group of Allays} entity, including its ServerLevel RNG consumption.</li>
 * </ul>
 *
 * <h2>Template-cell lane</h2>
 * <p>{@link Mc263WoodlandMansionSettlement} presents template placement one already-transformed cell
 * at a time, so — exactly as the Village transaction does — each cell becomes a one-cell
 * {@link Piece} placed at a zero origin with the identity transform, and the executor's own
 * mirror/rotation arithmetic is the identity. The piece carries the Mansion's authenticated
 * {@code writeFlags=2}; the shared loop's block-entity barrier clear stays at the authenticated
 * {@code 820}. The cell is bound back to its own {@code R} or {@code D} command before it runs, so a
 * cell whose ordinal, template-local position or source state is not in the pinned program is
 * rejected rather than placed.</p>
 *
 * <h2>Marker lane</h2>
 * <p>A {@code Chest*} marker is not a template cell: {@code TemplateStructurePiece.postProcess} runs
 * {@code handleDataMarker} after the whole template is placed and {@code StructurePiece.createChest}
 * writes the chest with {@code setBlock} flags {@code 2}. The exact chest state is the {@code N} row
 * of {@code (marker, rotation)} — the placement mirror is deliberately not applied — and the loot
 * lane carries the {@code K} row table with the settlement's single placement {@code nextLong()}
 * seed. Entity markers are delegated whole to {@link Mc263WoodlandMansionEntityAuthority}.</p>
 *
 * <h2>Tick lanes</h2>
 * <p>{@code BTIK} is the raw {@code minecraft:dark_oak_leaves} scheduler transcript at delay
 * {@code 1} NORMAL, one row per surviving leaf write. The live chunk carrier separately applies
 * {@code LevelChunkTicks}' first-wins {@code (position, block)} identity. {@code FTIK} is the
 * {@code LiquidBlock#updateShape} scheduler at {@code minecraft:water[level=0]} delay {@code 5}
 * NORMAL, first-wins per position: the first encounter is an insertion with a strictly increasing
 * sub-tick order and every later encounter at the same position is a duplicate-unchanged row.</p>
 */
public final class Mc263WoodlandMansionProductionTransaction implements WorldTransaction {
    /** Vanilla {@code WorldGenLevel} seam this transaction drives, plus its isolation contract. */
    public interface World extends PlacementWorld {
        /** Isolated fork; mutations stay invisible to this world until {@link #commitFrom}. */
        World forkIsolated();
        /** Applies an isolated fork's mutations to this world in their recorded order. */
        void commitFrom(World isolated);
    }

    /** Exactly-once publication journal keyed by the settlement's transaction key. */
    public static final class Journal {
        private final Map<String, Stored> committed = new HashMap<>();

        public int size() { return committed.size(); }

        private static final class Stored {
            private final String fingerprint;
            private final byte[] payload;
            private Stored(String fingerprint, byte[] payload) {
                this.fingerprint = fingerprint; this.payload = payload.clone();
            }
        }
    }

    private static final String HEIGHTMAP = "WORLD_SURFACE_WG";
    private static final String LEAVES = Mc263WoodlandMansionSettlement.LEAVES;
    private static final int LEAVES_DELAY = Mc263WoodlandMansionSettlement.LEAVES_DELAY;
    private static final String WATER = Mc263WoodlandMansionSettlement.WATER;
    private static final String WATER_SOURCE_STATE =
            Mc263WoodlandMansionSettlement.WATER_SOURCE_STATE;
    private static final int WATER_DELAY = Mc263WoodlandMansionSettlement.WATER_DELAY;
    private static final int NORMAL_PRIORITY = Mc263WoodlandMansionSettlement.NORMAL_PRIORITY;
    private static final String BARRIER = Mc263TemplatePlacementExecutor.BARRIER_STATE;
    private static final String AIR = Mc263TemplatePlacementExecutor.AIR_BLOCK;
    private static final Vec ZERO = new Vec(0, 0, 0);

    /**
     * The operation vocabulary this transaction performs, i.e. the closure the settlement gates
     * every stage against. It is the transaction's own self-description of the vanilla stages it
     * implements, not evidence: the authenticated stage closure is the P2R/P2T settlement authority
     * whose digest {@link Mc263WoodlandMansionProductionAuthority#SOURCE_SETTLEMENT_SHA256} pins,
     * and it is that digest, not this list, that the settlement's authority gate compares.
     */
    private static final Set<String> OPERATIONS = Set.of(
            "EXPAND_GRAMMAR", "TRANSFORM_POSITION", "CLIP_BEFORE_PROCESSORS",
            "PROCESSOR_IGNORE_STRUCTURE_BLOCK", "QUERY_FLUID_PREIMAGE", "NBT_BARRIER_WRITE",
            "PRIMARY_WRITE", "RESTORE_SOURCE_FLUID", "EDGE_INSIDE_UPDATE", "EDGE_OUTSIDE_UPDATE",
            "UPDATE_FROM_NEIGHBOR_SHAPES", "NEIGHBOR_CORRECTION_WRITE", "UPDATE_NEIGHBORS_AT",
            "BLOCK_ENTITY_LOAD", "BLOCK_ENTITY_DIRTY", "LOOT_SEED_DRAW", "MARKER_CHEST",
            "MARKER_ILLAGER", "MARKER_ALLAY", "MARKER_CLEAR", "TEMPLATE_ENTITY_SUPPRESSED",
            "SCHEDULE_BLOCK_TICK", "SCHEDULE_FLUID_TICK", "AFTER_PLACE_CELL_TEST",
            "AFTER_PLACE_DESCEND_QUERY", "AFTER_PLACE_FOUNDATION_WRITE");

    /** The three entity keys {@link Mc263WoodlandMansionEntityAuthority} can generate. */
    private static final Set<String> ENTITY_KEYS =
            Set.of("minecraft:evoker", "minecraft:vindicator", "minecraft:allay");

    private final Mc263WoodlandMansionProductionAuthority authority;
    private final Map<String, Template> templatesById;
    private final Set<String> nbtSemantics;
    private final Set<Integer> writeFlagClosure;
    private final int writeFlags;
    private final int barrierFlags;
    private final boolean keepLiquids;
    private final World world;
    private final Journal journal;
    private final boolean isolated;
    private long leafSubTickOrder;
    private long fluidSubTickOrder;
    private final Set<Position> insertedFluidTicks = new LinkedHashSet<>();

    private Mc263WoodlandMansionProductionTransaction(World world, Journal journal,
            boolean isolated) {
        this.world = Objects.requireNonNull(world, "Mansion production world");
        this.journal = Objects.requireNonNull(journal, "Mansion production journal");
        this.isolated = isolated;
        this.authority = Mc263WoodlandMansionProductionAuthority.pinned();
        this.writeFlags = settingInt("writeFlags");
        this.barrierFlags = settingInt("nbtBarrierFlags");
        this.keepLiquids = Boolean.parseBoolean(
                Objects.requireNonNull(authority.setting("keepLiquids"), "Mansion keepLiquids row"));
        require(barrierFlags == Mc263TemplatePlacementExecutor.BLOCK_ENTITY_CLEAR_FLAGS,
                "Mansion nbtBarrierFlags row disagrees with the shared executor barrier clear");
        require(keepLiquids, "Mansion keepLiquids row disagrees with the shared executor lane");
        require(authority.templates().size()
                        == Mc263WoodlandMansionSettlement.AUTHORITY_TEMPLATE_COUNT
                        && authority.exactStates().size()
                                == Mc263WoodlandMansionSettlement.AUTHORITY_EXACT_STATE_COUNT,
                "Mansion production authority cardinality drift");
        require(Mc263WoodlandMansionProductionAuthority.SOURCE_SETTLEMENT_SHA256.equals(
                        Mc263WoodlandMansionSettlement.P2R_P2T_AUTHORITY_SHA256),
                "Mansion P2R/P2T authority receipt drift");

        HashMap<String, Template> byId = new HashMap<>();
        for (Template template : authority.templates()) {
            require(byId.put(template.id(), template) == null,
                    "duplicate Mansion production template identity: " + template.id());
        }
        this.templatesById = Map.copyOf(byId);

        LinkedHashSet<String> semantics = new LinkedHashSet<>();
        for (Template template : authority.templates()) {
            for (Command command : template.commands()) {
                if (command.isData() && command.kind().isBlockEntity()) {
                    semantics.add(command.kind().name() + "/" + command.primary());
                }
            }
        }
        semantics.add("MARKER_CHEST/minecraft:chest");
        this.nbtSemantics = Set.copyOf(semantics);
        this.writeFlagClosure = Set.of(writeFlags, barrierFlags,
                Mc263WoodlandMansionSettlement.EDGE_UPDATE_FLAGS,
                Mc263WoodlandMansionSettlement.NEIGHBOR_CORRECTION_FLAGS);
    }

    /** Root (publishing) transaction over one world and one publication journal. */
    public static Mc263WoodlandMansionProductionTransaction root(World world, Journal journal) {
        return new Mc263WoodlandMansionProductionTransaction(world, journal, false);
    }

    /** {@code W} row {@code writeFlags} this transaction places every template cell at. */
    public int writeFlags() { return writeFlags; }

    // ------------------------------------------------------------------ capability closure

    @Override public boolean supportsP2rP2tAuthority(String authoritySha256,
            String liquidBlockSha256, int liquidBlockBytes, int templateCount, int exactStateCount) {
        return Mc263WoodlandMansionProductionAuthority.SOURCE_SETTLEMENT_SHA256.equals(authoritySha256)
                && Mc263WoodlandMansionSettlement.P2T_LIQUID_BLOCK_SHA256.equals(liquidBlockSha256)
                && liquidBlockBytes == Mc263WoodlandMansionSettlement.P2T_LIQUID_BLOCK_BYTES
                && templateCount == authority.templates().size()
                && exactStateCount == authority.exactStates().size();
    }

    @Override public int authorityTemplateCount() { return authority.templates().size(); }

    @Override public int authorityExactStateCount() { return authority.exactStates().size(); }

    @Override public boolean authorityAdmitsTemplate(String templateId) {
        return authority.template(templateId) != null;
    }

    @Override public boolean authorityAdmitsExactState(String exactState) {
        return authority.admitsExactState(canonical(exactState));
    }

    @Override public boolean authorityAdmitsProcessor(String processorAuthority) {
        return authority.processors().contains(processorAuthority);
    }

    @Override public boolean authorityAdmitsOperation(String operation) {
        return OPERATIONS.contains(operation);
    }

    @Override public boolean supportsPlacementRandom(long lo, long hi, int count) {
        return count >= 0;
    }

    @Override public boolean supportsServerLevelRandom(ServerRandomAuthority randomAuthority,
            ServerRandomState state) {
        return Mc263WoodlandMansionSettlement.SERVER_LEVEL_RANDOM_AUTHORITY.equals(randomAuthority)
                && state != null;
    }

    @Override public boolean supportsAtomicForkPublishWithRandom() { return true; }

    @Override public boolean supportsHeightmap(Mc263WoodlandMansionProducer.Heightmap heightmap) {
        return heightmap == Mc263WoodlandMansionProducer.Heightmap.WORLD_SURFACE_WG;
    }

    @Override public boolean supportsBuildHeightBoundary() { return true; }

    @Override public int minBuildY() { return Mc263WoodlandMansionSettlement.MIN_BUILD_Y; }

    @Override public int maxBuildY() { return Mc263WoodlandMansionSettlement.MAX_BUILD_Y; }

    @Override public boolean supportsTemplateCellExecution() { return true; }

    @Override public boolean supportsMarkerExecution() { return true; }

    @Override public boolean supportsStateTransform() { return true; }

    @Override public boolean supportsKeepLiquids() { return keepLiquids; }

    @Override public boolean supportsNeighborResolution() { return true; }

    @Override public boolean supportsAfterPlaceExecution() { return true; }

    @Override public boolean supportsCanonicalBlockEntityNbt() { return true; }

    @Override public boolean supportsTypedBentLane() { return true; }

    @Override public boolean supportsLootLane() { return true; }

    @Override public boolean supportsStructureEntityLane() { return true; }

    @Override public boolean supportsOwnerLane() { return true; }

    @Override public boolean supportsSuccessorLane() { return true; }

    @Override public boolean supportsFingerprintLane() { return true; }

    @Override public boolean supportsBlockTickLane() { return true; }

    @Override public boolean supportsFluidTickLane() { return true; }

    @Override public boolean supportsEmptyBlockQueries() { return true; }

    @Override public boolean supportsFluidStateQueries() { return true; }

    @Override public boolean supportsSetBlock() { return true; }

    @Override public boolean supportsWriteFlags(int flags) {
        return writeFlagClosure.contains(flags);
    }

    @Override public boolean supportsTransientState(String exactState) {
        return BARRIER.equals(canonical(exactState));
    }

    @Override public boolean supportsTemplate(String templateId) {
        return authorityAdmitsTemplate(templateId);
    }

    @Override public boolean supportsExactState(String exactState) {
        String state = canonical(exactState);
        return BARRIER.equals(state) || authority.admitsExactState(state);
    }

    @Override public boolean supportsNbtSemantic(String semanticKind, String blockEntityType) {
        return nbtSemantics.contains(semanticKind + "/" + blockEntityType);
    }

    /** The accepted marker vocabulary is the grammar's own; an unknown name fails its gate. */
    @Override public boolean supportsMarker(String metadata) {
        try {
            new Mc263WoodlandMansionGrammar.StructureMarker(metadata);
            return true;
        } catch (RuntimeException rejected) {
            return false;
        }
    }

    @Override public boolean supportsStructureEntity(String entityKey) {
        return ENTITY_KEYS.contains(entityKey);
    }

    @Override public boolean supportsLootTable(String table) {
        return Objects.equals(authority.rule("markerChestLootTable"), table);
    }

    @Override public boolean supportsBlockTick(String blockKey, int delay, int priority) {
        return LEAVES.equals(blockKey) && delay == LEAVES_DELAY && priority == NORMAL_PRIORITY;
    }

    @Override public boolean supportsFluidTick(String fluidKey, String sourceState, int delay,
            int priority) {
        return WATER.equals(fluidKey) && WATER_SOURCE_STATE.equals(canonical(sourceState))
                && delay == WATER_DELAY && priority == NORMAL_PRIORITY;
    }

    // ------------------------------------------------------------------ state transform

    /**
     * Official mirror-before-rotation state transform. The horizontal, axis, rotation and hinge
     * families are the shared executor's. Door hinge and rail shape successors come directly from
     * the authority's full {@code Z} mirror-before-rotation closure. A mirrored stair comes from the
     * {@code Y} mirror closure and rotation is then applied to that successor —
     * {@code StairBlock#rotate} is {@code FACING}-only.
     */
    @Override public String transformExactState(String sourceState,
            Mc263WoodlandMansionGrammar.Mirror mirror,
            Mc263WoodlandMansionGrammar.Rotation rotation) {
        String state = canonical(sourceState);
        Mc263TemplatePlacementExecutor.Mirror executorMirror =
                Mc263TemplatePlacementExecutor.Mirror.valueOf(mirror.name());
        Mc263TemplatePlacementExecutor.Rotation executorRotation =
                Mc263TemplatePlacementExecutor.Rotation.valueOf(rotation.name());
        String directional = authority.doorRailTransformedState(
                state, mirror.name(), rotation.name());
        if (directional != null) return directional;
        String mirrored = authority.mirroredStairState(state, mirror.name());
        if (mirrored != null) {
            return Mc263TemplatePlacementExecutor.transformState(mirrored,
                    Mc263TemplatePlacementExecutor.Mirror.NONE, executorRotation);
        }
        return Mc263TemplatePlacementExecutor.transformState(state, executorMirror, executorRotation);
    }

    // ------------------------------------------------------------------ world seam

    @Override public int baseHeight(Mc263WoodlandMansionProducer.Heightmap heightmap, int blockX,
            int blockZ) {
        require(heightmap == Mc263WoodlandMansionProducer.Heightmap.WORLD_SURFACE_WG,
                "Mansion production heightmap outside the authenticated projection");
        return world.getHeight(HEIGHTMAP, blockX, blockZ);
    }

    @Override public WorldTransaction fork() {
        require(!isolated, "Mansion production fork of an isolated transaction");
        return new Mc263WoodlandMansionProductionTransaction(world.forkIsolated(), journal, true);
    }

    @Override public boolean isEmptyBlock(Position position) {
        return AIR.equals(blockKey(world.getBlockState(vec(position))));
    }

    @Override public FluidState getFluidState(Position position) {
        Vec target = vec(position);
        String fluid = world.getFluidKey(target);
        boolean empty = Mc263TemplatePlacementExecutor.EMPTY_FLUID.equals(fluid);
        return new FluidState(fluid, !empty && world.isFluidSource(target), empty);
    }

    @Override public boolean setBlock(Position position, String exactState, int flags) {
        return world.setBlock(vec(position), canonical(exactState), flags);
    }

    // ------------------------------------------------------------------ template-cell lane

    @Override public CellEffects executeTemplateCell(CellPlacement placement) {
        Objects.requireNonNull(placement, "Mansion cell placement");
        require(isolated, "Mansion template cell executed outside an isolated fork");
        Template template = authority.requireTemplate(placement.templateId());
        Command command = bind(template, placement);
        String blockEntityType = command.isData() ? command.primary() : null;

        Piece piece = new Piece(placement.pieceOrdinal(),
                new SingleCellProgram(template.id(), new PlacedCell(placement, blockEntityType)),
                ZERO, Mc263TemplatePlacementExecutor.Rotation.NONE,
                Mc263TemplatePlacementExecutor.Mirror.NONE, "rigid", authority.processors(),
                IgnorePolicy.STRUCTURE_BLOCK, keepLiquids, writeFlags);
        Result result = Mc263TemplatePlacementExecutor.execute(List.of(piece), clip(placement.clip()),
                world, null, (unusedPiece, cell, position, state, type, table, seed) ->
                        preimage(template, command, position, state, type), null);

        ArrayList<WorldWrite> writes = new ArrayList<>();
        ArrayList<Mc263WoodlandMansionSettlement.BlockTick> blockTicks = new ArrayList<>();
        boolean primary = false;
        for (String row : result.operations()) {
            TranscribedWrite write = TranscribedWrite.parse(row);
            if (write == null) continue;
            WriteCause cause;
            if (BARRIER.equals(write.state) && write.flags == barrierFlags) {
                cause = WriteCause.NBT_BARRIER;
            } else if (write.position.equals(placement.worldPosition())
                    && write.state.equals(placement.transformedState())
                    && write.flags == writeFlags) {
                cause = WriteCause.PRIMARY;
                primary = true;
            } else {
                cause = WriteCause.FLUID_RESTORE;
            }
            writes.add(new WorldWrite(cause, write.position, write.state, write.flags));
            if (LEAVES.equals(blockKey(write.state))) {
                blockTicks.add(new Mc263WoodlandMansionSettlement.BlockTick(write.position, LEAVES,
                        LEAVES_DELAY, NORMAL_PRIORITY, leafSubTickOrder++));
            }
        }

        ArrayList<BentPayload> bent = new ArrayList<>();
        for (Mc263TemplatePlacementExecutor.BentRow row : result.bent()) {
            bent.add(new BentPayload(position(row.position()), row.exactState(),
                    row.blockEntityType(), new CanonicalNbt(row.canonicalNbt())));
        }

        ArrayList<Mc263WoodlandMansionSettlement.FluidTick> fluidTicks = new ArrayList<>();
        for (Mc263TemplatePlacementExecutor.TickRow row : result.blockTicks()) {
            Position target = position(row.position());
            require(WATER.equals(row.key()) && row.delay() == WATER_DELAY,
                    "Mansion fluid tick outside the LiquidBlock scheduler authority");
            boolean inserted = insertedFluidTicks.add(target);
            fluidTicks.add(new Mc263WoodlandMansionSettlement.FluidTick(target, WATER,
                    WATER_SOURCE_STATE, WATER_DELAY, NORMAL_PRIORITY, inserted,
                    inserted ? fluidSubTickOrder++ : -1L));
        }
        return new CellEffects(primary, writes, bent, blockTicks, fluidTicks);
    }

    /**
     * Binds one settlement cell back to its own pinned command. A {@code DATA} cell must be the
     * {@code D} row of its ordinal; a {@code RUN} cell must fall inside the expansion of the
     * {@code R} row that owns its ordinal, at the run's own arithmetic position.
     */
    private Command bind(Template template, CellPlacement placement) {
        Command owner = null;
        for (Command candidate : template.commands()) {
            if (candidate.ordinal() > placement.sourceOrdinal()) break;
            owner = candidate;
        }
        require(owner != null, "Mansion cell ordinal precedes the pinned template program");
        Local local = new Local(placement.localPosition().x(), placement.localPosition().y(),
                placement.localPosition().z());
        if (owner.isData()) {
            require(owner.ordinal() == placement.sourceOrdinal() && owner.position().equals(local),
                    "Mansion DATA cell does not bind its pinned D row");
            require(owner.kind() == DataKind.parse(semanticKind(placement.semantic()))
                            && owner.primary().equals(blockEntityType(placement.semantic())),
                    "Mansion DATA cell semantic drift against its pinned D row");
        } else {
            int step = Math.subtractExact(placement.sourceOrdinal(), owner.ordinal());
            require(step >= 0 && step < owner.count(),
                    "Mansion RUN cell ordinal escapes its pinned R row");
            require(local.equals(new Local(
                            owner.position().x() + owner.delta().x() * step,
                            owner.position().y() + owner.delta().y() * step,
                            owner.position().z() + owner.delta().z() * step)),
                    "Mansion RUN cell position escapes its pinned R row expansion");
            require(placement.semantic() == null, "Mansion RUN cell carries a DATA semantic");
        }
        require(canonical(template.state(owner.state())).equals(placement.sourceState()),
                "Mansion cell source state escapes its pinned palette entry");
        require(authority.admitsExactState(placement.transformedState()),
                "Mansion transformed cell state escapes the exact-state closure");
        return owner;
    }

    /** Canonical post-placement {@code B} preimage of one block-entity {@code DATA} cell. */
    private byte[] preimage(Template template, Command command, Vec position, String state,
            String blockEntityType) {
        Mc263WoodlandMansionProductionAuthority.BlockEntityPreimage row =
                template.blockEntityPreimage(command.ordinal());
        require(row != null, "Mansion DATA cell has no canonical B preimage");
        require(row.blockEntityType().equals(blockEntityType),
                "Mansion B preimage block-entity type drift");
        require(authority.admitsExactState(state) && position != null,
                "Mansion BENT cell state escapes the exact-state closure");
        return row.binary();
    }

    // ------------------------------------------------------------------ marker lane

    @Override public MarkerEffects executeMarker(MarkerPlacement placement,
            ServerLevelRandom serverRandom) {
        Objects.requireNonNull(placement, "Mansion marker placement");
        Objects.requireNonNull(serverRandom, "Mansion marker ServerLevel RNG");
        require(isolated, "Mansion marker executed outside an isolated fork");
        return placement.metadata().startsWith("Chest")
                ? chestMarker(placement)
                : entityMarker(placement, serverRandom);
    }

    private MarkerEffects chestMarker(MarkerPlacement placement) {
        String table = Objects.requireNonNull(authority.rule("markerChestLootTable"),
                "Mansion marker-chest loot-table rule");
        int flags = Integer.parseInt(Objects.requireNonNull(
                authority.rule("markerChestWriteFlags"), "Mansion marker-chest write-flag rule"));
        String state = authority.markerChestState(placement.metadata(),
                placement.rotation().name());
        require(state != null, "Mansion marker chest outside the pinned N facing table");
        require(placement.lootSeed() != null,
                "Mansion marker chest lacks its single placement nextLong() seed");
        require(markerChestCell(placement) != null,
                "Mansion marker chest is not a pinned C cell of its template");

        ArrayList<WorldWrite> writes = new ArrayList<>();
        require(world.setBlock(vec(placement.position()), state, flags),
                "Mansion marker chest write rejected");
        writes.add(new WorldWrite(WriteCause.MARKER_CHEST, placement.position(), state, flags));
        BentPayload bent = new BentPayload(placement.position(), state, "minecraft:chest",
                new CanonicalNbt(lootContainerNbt(table, placement.lootSeed())));
        LootPayload loot = new LootPayload(placement.position(), table, placement.lootSeed());
        return new MarkerEffects(writes, List.of(bent), List.of(loot), List.of());
    }

    private Mc263WoodlandMansionProductionAuthority.MarkerChestCell markerChestCell(
            MarkerPlacement placement) {
        for (Mc263WoodlandMansionProductionAuthority.MarkerChestCell cell
                : authority.markerChestCellsOf(placement.templateId())) {
            if (cell.marker().equals(placement.metadata())) return cell;
        }
        return null;
    }

    private MarkerEffects entityMarker(MarkerPlacement placement, ServerLevelRandom serverRandom) {
        Position origin = templateOrigin(placement);
        Mc263WoodlandMansionEntityAuthority.Receipt receipt =
                Mc263WoodlandMansionEntityAuthority.generate(
                        new Mc263WoodlandMansionEntityAuthority.MarkerRequest(
                                placement.templateId(), placement.markerOrdinal(),
                                placement.metadata(), origin, placement.mirror(),
                                placement.rotation(), placement.position()),
                        serverRandom);
        ArrayList<EntityPayload> entities = new ArrayList<>(receipt.entities().size());
        for (Mc263WoodlandMansionEntityAuthority.GeneratedEntity entity : receipt.entities()) {
            entities.add(entity.payload());
        }
        ArrayList<WorldWrite> writes = new ArrayList<>();
        // handleDataMarker clears the structure-marker cell to air with the same setBlock flags.
        world.setBlock(vec(placement.position()), AIR, writeFlags);
        writes.add(new WorldWrite(WriteCause.MARKER_CLEAR, placement.position(), AIR, writeFlags));
        return new MarkerEffects(writes, List.of(), List.of(), entities);
    }

    /**
     * Template origin of a marker placement, recovered from the grammar's own marker order: the
     * settlement presents the transformed world position, and the origin is that position minus the
     * transformed template-local marker cell.
     */
    private Position templateOrigin(MarkerPlacement placement) {
        Template template = templatesById.get(placement.templateId());
        require(template != null, "unknown Mansion marker template: " + placement.templateId());
        int ordinal = 0;
        for (Command command : template.commands()) {
            if (!command.isData() || command.kind() != DataKind.STRUCTURE_MARKER) continue;
            if (ordinal++ != placement.markerOrdinal()) continue;
            require(command.primary().equals(placement.metadata()),
                    "Mansion marker metadata drift against the accepted authority");
            Vec local = Mc263TemplatePlacementExecutor.transform(
                    new Vec(command.position().x(), command.position().y(), command.position().z()),
                    Mc263TemplatePlacementExecutor.Mirror.valueOf(placement.mirror().name()),
                    Mc263TemplatePlacementExecutor.Rotation.valueOf(placement.rotation().name()));
            return new Position(Math.subtractExact(placement.position().x(), local.x()),
                    Math.subtractExact(placement.position().y(), local.y()),
                    Math.subtractExact(placement.position().z(), local.z()));
        }
        require(ordinal == template.markerCount(),
                "Mansion marker enumeration disagrees with the authority marker count");
        throw new IllegalArgumentException("Mansion marker ordinal outside its accepted template");
    }

    /**
     * Exact {@code RandomizableContainerBlockEntity.trySaveLootTable} custom payload.
     *
     * <p>A marker chest is created by {@code StructurePiece.createChest}, which hands the world the
     * loot custom data rather than a saved block entity, so this is the shared authority's
     * custom-data program and not the full save-with-metadata container program.</p>
     */
    private static byte[] lootContainerNbt(String table, long seed) {
        return Mc263StructureBlockEntityNbtAuthority.render(
                new Mc263StructureBlockEntityNbtAuthority.LootTableCustomData(
                        "minecraft:chest", table, seed));
    }

    // ------------------------------------------------------------------ publication

    @Override public PublishStatus publishAtomically(WorldTransaction isolatedTransaction,
            Settlement settlement, PlacementRandom callerRandom, PlacementRandom acceptedRandom,
            ServerLevelRandom callerServerRandom, ServerLevelRandom acceptedServerRandom) {
        require(!isolated, "Mansion publication from an isolated transaction");
        Objects.requireNonNull(settlement, "Mansion settlement");
        Mc263WoodlandMansionProductionTransaction child =
                (Mc263WoodlandMansionProductionTransaction) Objects.requireNonNull(
                        isolatedTransaction, "Mansion isolated transaction");
        require(child.isolated && child.journal == journal,
                "Mansion publication of a foreign isolated transaction");
        Journal.Stored previous = journal.committed.get(settlement.transactionKey());
        byte[] payload = settlement.canonicalPayload();
        if (previous != null && (!previous.fingerprint.equals(settlement.fingerprint())
                || !Arrays.equals(previous.payload, payload))) {
            throw new IllegalStateException("Mansion replay conflict for "
                    + settlement.transactionKey());
        }
        if (previous == null) {
            world.commitFrom(child.world);
            journal.committed.put(settlement.transactionKey(),
                    new Journal.Stored(settlement.fingerprint(), payload));
        }
        callerRandom.replaceWith(acceptedRandom);
        callerServerRandom.replaceWith(acceptedServerRandom);
        return previous == null ? PublishStatus.COMMITTED : PublishStatus.REPLAYED;
    }

    // ------------------------------------------------------------------ adapters and helpers

    /** The settlement's already-transformed cell as a one-cell neutral program. */
    private static final class SingleCellProgram implements CellProgram {
        private final String templateKey;
        private final List<Cell> cells;
        private SingleCellProgram(String templateKey, Cell cell) {
            this.templateKey = templateKey; this.cells = List.of(cell);
        }
        @Override public String templateKey() { return templateKey; }
        @Override public List<Cell> cellsInPlacementOrder() { return cells; }
    }

    /** One transformed Mansion cell; the executor's own transform is the identity here. */
    private static final class PlacedCell implements Cell {
        private final CellPlacement placement;
        private final String blockEntityType;
        private PlacedCell(CellPlacement placement, String blockEntityType) {
            this.placement = placement; this.blockEntityType = blockEntityType;
        }
        @Override public int localX() { return placement.worldPosition().x(); }
        @Override public int localY() { return placement.worldPosition().y(); }
        @Override public int localZ() { return placement.worldPosition().z(); }
        @Override public String exactState() { return placement.transformedState(); }
        @Override public String blockEntityType() { return blockEntityType; }
    }

    /** One {@code setBlock} row of the shared executor's operation transcript. */
    private static final class TranscribedWrite {
        private final Position position;
        private final String state;
        private final int flags;
        private TranscribedWrite(Position position, String state, int flags) {
            this.position = position; this.state = state; this.flags = flags;
        }

        private static TranscribedWrite parse(String row) {
            String[] fields = row.split("\\|");
            if (fields.length != 5 || !"setBlock".equals(fields[1])) return null;
            if (!fields[4].endsWith("->true")) return null;
            String positionField = fields[2].substring("pos:[".length(), fields[2].length() - 1);
            String[] coordinates = positionField.split(",");
            Position position = new Position(Integer.parseInt(coordinates[0].trim()),
                    Integer.parseInt(coordinates[1].trim()), Integer.parseInt(coordinates[2].trim()));
            String state = fields[3].substring("state:".length());
            int flags = Integer.parseInt(fields[4].substring(0, fields[4].length() - "->true".length()));
            return new TranscribedWrite(position, state, flags);
        }
    }

    private int settingInt(String key) {
        String value = Objects.requireNonNull(authority.setting(key),
                "missing Mansion placement setting row: " + key);
        return Integer.parseInt(value);
    }

    private static Mc263TemplatePlacementExecutor.Clip clip(
            Mc263WoodlandMansionSettlement.Clip clip) {
        return new Mc263TemplatePlacementExecutor.Clip(clip.minX(), clip.minY(), clip.minZ(),
                clip.maxX(), clip.maxY(), clip.maxZ());
    }

    private static String semanticKind(Mc263WoodlandMansionGrammar.Semantic semantic) {
        if (semantic instanceof Mc263WoodlandMansionGrammar.EmptyContainer) return "EMPTY_CONTAINER";
        if (semantic instanceof Mc263WoodlandMansionGrammar.ContainerItems) return "CONTAINER_ITEMS";
        if (semantic instanceof Mc263WoodlandMansionGrammar.PatternedBanner) {
            return "PATTERNED_BANNER";
        }
        if (semantic instanceof Mc263WoodlandMansionGrammar.MobSpawner) return "MOB_SPAWNER";
        if (semantic instanceof Mc263WoodlandMansionGrammar.StructureMarker) {
            return "STRUCTURE_MARKER";
        }
        throw new IllegalArgumentException("unknown Mansion cell semantic");
    }

    private static String blockEntityType(Mc263WoodlandMansionGrammar.Semantic semantic) {
        if (semantic instanceof Mc263WoodlandMansionGrammar.EmptyContainer value) {
            return value.blockEntityType();
        }
        if (semantic instanceof Mc263WoodlandMansionGrammar.ContainerItems value) {
            return value.blockEntityType();
        }
        if (semantic instanceof Mc263WoodlandMansionGrammar.PatternedBanner value) {
            return value.blockEntityType();
        }
        if (semantic instanceof Mc263WoodlandMansionGrammar.MobSpawner value) {
            return value.blockEntityType();
        }
        throw new IllegalArgumentException("Mansion cell semantic has no block-entity type");
    }

    private static Vec vec(Position value) { return new Vec(value.x(), value.y(), value.z()); }

    private static Position position(Vec value) {
        return new Position(value.x(), value.y(), value.z());
    }

    private static String canonical(String state) {
        return Objects.requireNonNull(state, "Mansion exact state").replace(", ", ",").trim();
    }

    private static String blockKey(String state) {
        String value = canonical(state);
        int bracket = value.indexOf('[');
        return bracket < 0 ? value : value.substring(0, bracket);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
