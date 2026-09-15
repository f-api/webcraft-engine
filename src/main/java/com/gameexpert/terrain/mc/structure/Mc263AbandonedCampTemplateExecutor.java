package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.feature.Mc263AbandonedCampConfiguredFeatureExecutor;
import com.gameexpert.terrain.mc.feature.Mc263AbandonedCampConfiguredFeatureExecutor.DirectFinalCell;
import com.gameexpert.terrain.mc.feature.Mc263AbandonedCampConfiguredFeatureExecutor.DirectPlacementResult;
import com.gameexpert.terrain.mc.feature.Mc263AbandonedCampConfiguredFeatureExecutor.DirectWorldAccess;
import com.gameexpert.terrain.mc.feature.Mc263AbandonedCampConfiguredFeatureExecutor.Origin;
import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState;
import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState.OcclusionFace;
import com.gameexpert.terrain.mc.ore.Mc263WorldgenRandomSource;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampCatalog.Command;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampCatalog.CommandOp;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampCatalog.ElementKind;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampCatalog.ProcessorList;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampCatalog.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampCatalog.Template;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampCatalog.Vec;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampProducer.Piece;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampProducer.Start;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Dormant exact clip executor for Minecraft Java 26.3-snapshot-7 Abandoned Camp.
 *
 * <p>The legacy {@link #execute} entrypoint remains template-only. The production seam expands the
 * same accepted arithmetic command grammar and composes intersecting configured-feature pieces on
 * the caller's isolated transaction. Neither path contains copied oracle operations, seed probes,
 * or coordinate lookup tables.</p>
 */
public final class Mc263AbandonedCampTemplateExecutor {
    public static final int TEMPLATE_WRITE_FLAGS = 18;
    public static final int BLOCK_ENTITY_CLEAR_FLAGS = 820;
    public static final int WATER_TICK_DELAY = 5;
    public static final String WATER_FLUID = "minecraft:water";
    public static final String BARRIER_STATE = "minecraft:barrier[waterlogged=false]";

    private static final List<String> FIXED_PROCESSOR_CHAIN = List.of(
            "BlockIgnoreProcessor.STRUCTURE_BLOCK",
            "JigsawReplacementProcessor.INSTANCE",
            "element processor list in codec order",
            "projection processor list in projection order");
    private static final List<Direction> LIQUID_NEIGHBORS = List.of(
            Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    private static final String PREDICATE_NON_SOLID_SURROGATE = "minecraft:spawner";
    private static final String PREDICATE_SOLID_SURROGATE = "minecraft:stone";

    private Mc263AbandonedCampTemplateExecutor() { }

    /**
     * Executes one target clip against an isolated transaction and publishes it atomically once.
     * Capability and grammar preflight completes before the caller random is copied and before any
     * world query or mutation. A failed fork, execution, or publication therefore leaves both
     * caller-owned inputs unchanged.
     */
    public static Settlement execute(Start start, Mc263AbandonedCampCatalog catalog, Clip clip,
            WorldTransaction transaction, PlacementRandom random) {
        Objects.requireNonNull(start, "Abandoned Camp start");
        Objects.requireNonNull(catalog, "Abandoned Camp catalog");
        Objects.requireNonNull(clip, "Abandoned Camp clip");
        Objects.requireNonNull(transaction, "Abandoned Camp transaction");
        Objects.requireNonNull(random, "Abandoned Camp placement random");

        Plan plan = preflight(start, catalog, clip, transaction, null, 0L);
        if (plan.pieces.isEmpty()) return Settlement.empty(clip);

        PlacementRandom candidate = random.copy();
        WorldTransaction isolated = Objects.requireNonNull(
                transaction.fork(), "isolated Abandoned Camp transaction");
        if (isolated == transaction) {
            throw new IllegalStateException("Abandoned Camp transaction fork is not isolated");
        }

        LinkedHashMap<Vec, String> finalStates = new LinkedHashMap<>();
        LinkedHashMap<Vec, LoadedEntity> finalEntities = new LinkedHashMap<>();
        int[] entityEncounter = new int[] {0};
        for (PreparedPiece piece : plan.pieces) {
            if (piece.feature()) {
                throw new IllegalStateException("FEATURE escaped template-only Camp preflight");
            }
            placePiece(piece, plan, isolated, candidate, finalStates, finalEntities,
                    entityEncounter);
        }

        Settlement settlement = settlement(clip, finalStates, finalEntities);
        transaction.publish(isolated, settlement);
        random.commit(candidate);
        return settlement;
    }

    /**
     * Current-only production seam for one already-authenticated Camp start and destination clip.
     * It derives the placement stream from the official 18-key step-local mapping after all
     * intersecting template/configured-feature capabilities have preflighted. The mixed execution
     * runs in accepted piece order on one isolated transaction and publishes exactly once.
     */
    public static ProductionExecution executeProduction(Start start, Clip clip, long owner,
            ProductionWorldTransaction transaction) {
        Objects.requireNonNull(start, "Abandoned Camp production start");
        Objects.requireNonNull(clip, "Abandoned Camp production clip");
        Objects.requireNonNull(transaction, "Abandoned Camp production transaction");
        Mc263AbandonedCampStartGenerator.StructureFact fact =
                Mc263AbandonedCampStartGenerator.require(start.structureKey());
        if (!fact.startPool().equals(start.startPool())) {
            throw new IllegalArgumentException("Abandoned Camp production start-pool drift");
        }

        Mc263AbandonedCampCatalog catalog = Mc263AbandonedCampCatalog.pinned();
        Plan plan = preflight(start, catalog, clip, transaction, transaction, owner);
        PlacementRandom random = PlacementRandom.forClip(
                start.worldSeed(), clip, fact.stepLocalIndex());
        if (plan.pieces.isEmpty()) {
            return new ProductionExecution(fact.stepLocalIndex(), Settlement.empty(clip),
                    List.of(), random);
        }

        PlacementRandom candidate = random.copy();
        ProductionWorldTransaction isolated = Objects.requireNonNull(
                transaction.fork(), "isolated Abandoned Camp production transaction");
        if (isolated == transaction) {
            throw new IllegalStateException("Abandoned Camp production fork is not isolated");
        }

        LinkedHashMap<Vec, String> finalStates = new LinkedHashMap<>();
        LinkedHashMap<Vec, LoadedEntity> finalEntities = new LinkedHashMap<>();
        ArrayList<DirectPlacementResult> configuredResults = new ArrayList<>();
        int[] entityEncounter = new int[] {0};
        for (PreparedPiece piece : plan.pieces) {
            if (!piece.feature()) {
                placePiece(piece, plan, isolated, candidate, finalStates, finalEntities,
                        entityEncounter);
                continue;
            }
            MixedFeatureWorld featureWorld = new MixedFeatureWorld(isolated);
            DirectPlacementResult configured =
                    Mc263AbandonedCampConfiguredFeatureExecutor.placeDirect(
                            piece.featureKey, candidate,
                            new Origin(piece.featureOrigin.x(), piece.featureOrigin.y(),
                                    piece.featureOrigin.z()),
                            owner, featureWorld);
            featureWorld.requireSuccess();
            for (DirectFinalCell cell : configured.finalCells()) {
                Vec position = new Vec(cell.x(), cell.y(), cell.z());
                finalStates.put(position, cell.exactState());
                finalEntities.remove(position);
            }
            configuredResults.add(configured);
        }

        Settlement settlement = settlement(clip, finalStates, finalEntities);
        transaction.publish(isolated, settlement);
        random.commit(candidate);
        return new ProductionExecution(fact.stepLocalIndex(), settlement,
                configuredResults, random);
    }

    private static Plan preflight(Start start, Mc263AbandonedCampCatalog catalog, Clip clip,
            WorldTransaction transaction, ProductionWorldTransaction production, long owner) {
        validateProcessorClosure(catalog);
        ArrayList<PreparedPiece> pieces = new ArrayList<>();
        LinkedHashSet<String> exactStates = new LinkedHashSet<>();
        boolean bent = false;
        boolean loot = false;
        ArrayList<EntitySpec> entitySpecs = new ArrayList<>();

        if (!start.empty()) {
            for (Piece piece : start.piecesInAcceptedOrder()) {
                if (!clip.intersects(piece.boundingBox())) continue;
                if (piece.kind() == ElementKind.FEATURE) {
                    if (production == null) {
                        throw new UnsupportedOperationException(
                                "Abandoned Camp FEATURE piece reached template-only executor: "
                                        + piece.ordinal());
                    }
                    validateFeaturePiece(piece, catalog);
                    Mc263AbandonedCampConfiguredFeatureExecutor.preflightDirect(
                            piece.featureKey(), owner, production);
                    pieces.add(PreparedPiece.feature(piece.ordinal(), piece.featureKey(),
                            piece.position()));
                    continue;
                }
                validateTemplatePiece(piece, catalog);
                Template template = catalog.requireTemplate(piece.templateKey());
                ArrayList<PreparedCell> cells = new ArrayList<>();
                for (Command command : template.commands()) {
                    int occurrences = command.op() == CommandOp.RUN ? command.count() : 1;
                    for (int occurrence = 0; occurrence < occurrences; occurrence++) {
                        Vec position = command.worldPositionAt(
                                occurrence, piece.rotation(), piece.position());
                        if (!clip.contains(position)) continue;
                        String sourceState = command.op() == CommandOp.JIGSAW
                                ? command.replacementState()
                                : template.requireState(command.state());
                        // legacy_single_pool_element omits raw AIR before liquid/query placement;
                        // JIGSAW final_state AIR is deliberately retained because replacement is later.
                        if (command.op() != CommandOp.JIGSAW
                                && ("minecraft:air".equals(blockKey(sourceState))
                                || "minecraft:structure_block".equals(blockKey(sourceState)))) {
                            continue;
                        }
                        String state = rotateState(sourceState, piece.rotation());
                        exactStates.add(state);
                        String wetState = authenticatedWetSuccessor(state);
                        if (wetState != null) exactStates.add(wetState);

                        EntitySpec entity = entitySpec(command, state);
                        if (entity != null) {
                            bent = true;
                            loot |= entity.lootTable != null;
                            entitySpecs.add(entity);
                        }
                        cells.add(new PreparedCell(position, state, wetState, entity));
                    }
                }
                if (!cells.isEmpty()) pieces.add(PreparedPiece.template(piece.ordinal(), cells));
            }
        }

        if (production != null) validateMixedOrdinalMerge(pieces);
        if (pieces.isEmpty()) return new Plan(List.of(), Set.of());
        preflightCapabilities(transaction, exactStates, bent, loot, entitySpecs);
        return new Plan(pieces, exactStates);
    }

    private static void validateProcessorClosure(Mc263AbandonedCampCatalog catalog) {
        if (!FIXED_PROCESSOR_CHAIN.equals(catalog.processorPlacementOrder())) {
            throw new IllegalArgumentException("Abandoned Camp processor placement order drift");
        }
        ProcessorList processor = catalog.requireProcessor(
                Mc263AbandonedCampCatalog.INLINE_PROCESSOR_KEY);
        if (!Mc263AbandonedCampCatalog.INLINE_PROCESSOR_KEY.equals(processor.registryKey())
                || !Mc263AbandonedCampCatalog.INLINE_PROCESSOR_SHA256.equals(
                        processor.codecSha256())
                || !processor.order().isEmpty()) {
            throw new IllegalArgumentException("Abandoned Camp inline processor drift");
        }
    }

    private static void validateTemplatePiece(Piece piece, Mc263AbandonedCampCatalog catalog) {
        if (piece.kind() != ElementKind.TEMPLATE || piece.templateKey() == null) {
            throw new IllegalArgumentException("non-template Abandoned Camp piece");
        }
        if (!Mc263AbandonedCampCatalog.RIGID_PROJECTION.equals(piece.projection())
                || !Mc263AbandonedCampCatalog.INLINE_PROCESSOR_KEY.equals(
                        piece.processorRegistryKey())
                || !piece.processorOrder().isEmpty()) {
            throw new IllegalArgumentException(
                    "Abandoned Camp template piece processor/projection drift: "
                            + piece.ordinal());
        }
        catalog.requireTemplate(piece.templateKey());
    }

    private static void validateFeaturePiece(Piece piece,
            Mc263AbandonedCampCatalog catalog) {
        if (piece.kind() != ElementKind.FEATURE || piece.featureKey() == null) {
            throw new IllegalArgumentException("non-feature Abandoned Camp piece");
        }
        if (!Mc263AbandonedCampCatalog.RIGID_PROJECTION.equals(piece.projection())) {
            throw new IllegalArgumentException(
                    "Abandoned Camp feature projection drift: " + piece.ordinal());
        }
        catalog.requireConfiguredFeature(piece.featureKey());
    }

    /**
     * StructureStart.placeInChunk executes every intersecting piece in the accepted piece order on
     * one shared placement random, and FeaturePoolElement is an ordinary pool element there: the
     * placer never caps how many FEATURE pieces a clip may carry and never segregates them from
     * TEMPLATE pieces. The pinned camp grammar depends on that — 34 of the 297 pinned templates
     * carry two or more jigsaw connectors targeting a single-element trees pool (up to 26 on
     * campsite_bamboo_jungle_4), and 36 carry both a trees connector and a further template
     * connector, so a clip legitimately holds several FEATURE pieces and may hold a TEMPLATE piece
     * after one. The only merge invariant is therefore the accepted order itself: the prepared
     * stream must be the intersecting subsequence of piecesInAcceptedOrder, never reordered and
     * never duplicated, and each prepared piece must carry exactly the payload of its kind.
     */
    private static void validateMixedOrdinalMerge(List<PreparedPiece> pieces) {
        int previous = -1;
        for (PreparedPiece piece : pieces) {
            if (piece.ordinal <= previous) {
                throw new IllegalArgumentException(
                        "Abandoned Camp mixed piece ordinals are not strictly increasing");
            }
            previous = piece.ordinal;
            if (piece.feature()) {
                if (piece.featureOrigin == null || !piece.cells.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Abandoned Camp mixed FEATURE piece payload drift: " + piece.ordinal);
                }
            } else if (piece.cells.isEmpty() || piece.featureOrigin != null) {
                throw new IllegalArgumentException(
                        "Abandoned Camp mixed TEMPLATE piece payload drift: " + piece.ordinal);
            }
        }
    }

    private static void preflightCapabilities(WorldTransaction transaction,
            Set<String> exactStates, boolean bent, boolean loot, List<EntitySpec> entities) {
        if (!transaction.supportsAtomicForkPublish()) {
            throw new UnsupportedOperationException("atomic Abandoned Camp fork/publish required");
        }
        if (!transaction.supportsFluidStateQueries()) {
            throw new UnsupportedOperationException("Abandoned Camp fluid-state queries required");
        }
        if (!transaction.supportsBlockStateQueries()) {
            throw new UnsupportedOperationException("Abandoned Camp block-state queries required");
        }
        if (!transaction.supportsSetBlockAndUpdate()) {
            throw new UnsupportedOperationException("Abandoned Camp setBlockAndUpdate required");
        }
        if (!transaction.supportsWriteFlags(TEMPLATE_WRITE_FLAGS)
                || !transaction.supportsWriteFlags(BLOCK_ENTITY_CLEAR_FLAGS)) {
            throw new UnsupportedOperationException("Abandoned Camp write flags 18/820 required");
        }
        String barrier = exact(BARRIER_STATE);
        if (!transaction.supportsExactState(barrier)) {
            throw new UnsupportedOperationException("Abandoned Camp barrier state required");
        }
        for (String state : exactStates) {
            if (!transaction.supportsExactState(state)) {
                throw new UnsupportedOperationException(
                        "unsupported Abandoned Camp exact state: " + state);
            }
        }
        if (!transaction.supportsFluidTick(WATER_FLUID, WATER_TICK_DELAY)) {
            throw new UnsupportedOperationException("Abandoned Camp Water delay-5 tick required");
        }
        if (bent && !transaction.supportsBentPayloads()) {
            throw new UnsupportedOperationException("Abandoned Camp typed BENT required");
        }
        if (loot && !transaction.supportsLootPayloads()) {
            throw new UnsupportedOperationException("Abandoned Camp typed LOOT required");
        }
        for (EntitySpec entity : entities) {
            if (!transaction.supportsBlockEntity(entity.blockIdentity, entity.entityType)) {
                throw new UnsupportedOperationException(
                        "unsupported Abandoned Camp block entity pair: "
                                + entity.blockIdentity + "/" + entity.entityType);
            }
            if (entity.lootTable != null && !transaction.supportsLootTable(entity.lootTable)) {
                throw new UnsupportedOperationException(
                        "unsupported Abandoned Camp loot table: " + entity.lootTable);
            }
        }
    }

    private static EntitySpec entitySpec(Command command, String exactState) {
        if (command.op() == CommandOp.LOOT_CONTAINER) {
            // The codec call is a pure closure check; the live payload is encoded only after its
            // successful real write and caller-random loot seed draw.
            Mc263AbandonedCampBlockEntityCodec.lootContainer(
                    command.blockEntityType(), 0, 0, 0, command.lootTable(), 0L);
            return new EntitySpec(blockKey(exactState), command.blockEntityType(),
                    command.lootTable());
        }
        if (command.op() == CommandOp.EMPTY_BLOCK_ENTITY) {
            if (!"minecraft:campfire:four_empty_slots".equals(command.blockEntityType())
                    || !"minecraft:campfire".equals(blockKey(exactState))) {
                throw new UnsupportedOperationException(
                        "unauthenticated Abandoned Camp empty block entity: "
                                + command.blockEntityType());
            }
            Mc263AbandonedCampBlockEntityCodec.emptyCampfire(0, 0, 0);
            return new EntitySpec("minecraft:campfire", "minecraft:campfire", null);
        }
        if (command.op() == CommandOp.EMPTY_COMPONENT_ENTITY) {
            if (!"minecraft:copper_golem_statue".equals(command.blockEntityType())) {
                throw new UnsupportedOperationException(
                        "unauthenticated Abandoned Camp component block entity reached: "
                                + command.blockEntityType());
            }
            Mc263AbandonedCampBlockEntityCodec.copperGolemStatue(exactState, 0, 0, 0);
            return new EntitySpec(blockKey(exactState), command.blockEntityType(), null);
        }
        return null;
    }

    private static void placePiece(PreparedPiece piece, Plan plan, WorldTransaction world,
            PlacementRandom random, Map<Vec, String> finalStates,
            Map<Vec, LoadedEntity> finalEntities, int[] entityEncounter) {
        ArrayList<Vec> pendingLiquids = new ArrayList<>();
        ArrayList<LoadedEntity> pieceEntities = new ArrayList<>();

        for (PreparedCell cell : piece.cells) {
            FluidState retained = requireFluid(world.getFluidState(cell.position));
            if (cell.entity != null) {
                boolean cleared = world.setBlock(
                        cell.position, exact(BARRIER_STATE), BLOCK_ENTITY_CLEAR_FLAGS);
                if (cleared) {
                    finalStates.put(cell.position, exact(BARRIER_STATE));
                    finalEntities.remove(cell.position);
                }
            }

            boolean placed = world.setBlock(cell.position, cell.state, TEMPLATE_WRITE_FLAGS);
            if (!placed) continue;
            finalStates.put(cell.position, cell.state);
            if (cell.entity == null) finalEntities.remove(cell.position);

            LoadedEntity loaded = null;
            if (cell.entity != null) {
                BlockEntityAccess handle = requireEntity(
                        world.getBlockEntity(cell.position), cell.entity, cell.position);
                long lootSeed = 0L;
                byte[] nbt;
                if (cell.entity.lootTable != null) {
                    lootSeed = random.nextLong();
                    nbt = Mc263AbandonedCampBlockEntityCodec.lootContainer(
                            cell.entity.entityType,
                            cell.position.x(), cell.position.y(), cell.position.z(),
                            cell.entity.lootTable, lootSeed);
                } else if ("minecraft:campfire".equals(cell.entity.entityType)) {
                    nbt = Mc263AbandonedCampBlockEntityCodec.emptyCampfire(
                            cell.position.x(), cell.position.y(), cell.position.z());
                } else {
                    nbt = Mc263AbandonedCampBlockEntityCodec.copperGolemStatue(
                            cell.state, cell.position.x(), cell.position.y(), cell.position.z());
                }
                handle.loadCanonicalNbt(nbt.clone());
                loaded = new LoadedEntity(cell.position, cell.entity.blockIdentity,
                        cell.entity.entityType, cell.entity.lootTable, lootSeed, nbt,
                        entityEncounter[0]++);
                pieceEntities.add(loaded);
                finalEntities.put(cell.position, loaded);
            }

            if (!retained.empty()) {
                if (retained.source()) {
                    if (WATER_FLUID.equals(retained.fluidKey())
                            && hasDryWaterloggedProperty(cell.state)) {
                        if (cell.wetState == null || !plan.exactStates.contains(cell.wetState)) {
                            throw new UnsupportedOperationException(
                                    "unauthenticated Abandoned Camp wet successor: " + cell.state);
                        }
                        placeWater(cell.position, cell.wetState, world,
                                finalStates, finalEntities);
                    }
                } else {
                    pendingLiquids.add(cell.position);
                }
            }
        }

        restorePendingLiquids(pendingLiquids, plan, world, finalStates, finalEntities);
        for (LoadedEntity loaded : pieceEntities) {
            BlockEntityAccess handle = requireEntity(world.getBlockEntity(loaded.position),
                    new EntitySpec(loaded.blockIdentity, loaded.entityType, loaded.lootTable),
                    loaded.position);
            handle.setChanged();
        }
    }

    private static void restorePendingLiquids(List<Vec> pending, Plan plan,
            WorldTransaction world, Map<Vec, String> finalStates,
            Map<Vec, LoadedEntity> finalEntities) {
        boolean changed = true;
        while (changed && !pending.isEmpty()) {
            changed = false;
            Iterator<Vec> iterator = pending.iterator();
            while (iterator.hasNext()) {
                Vec position = iterator.next();
                FluidState best = requireFluid(world.getFluidState(position));
                for (Direction direction : LIQUID_NEIGHBORS) {
                    if (best.source()) break;
                    Vec neighbor = offset(position, direction);
                    FluidState candidate = requireFluid(world.getFluidState(neighbor));
                    if (candidate.height() > best.height()) best = candidate;
                }
                if (!best.source()) continue;

                String current = exact(world.getBlockState(position));
                if (!WATER_FLUID.equals(best.fluidKey())
                        || !hasDryWaterloggedProperty(current)) {
                    continue;
                }
                String wet = authenticatedWetSuccessor(current);
                if (wet == null || !plan.exactStates.contains(wet)) {
                    throw new UnsupportedOperationException(
                            "unauthenticated Abandoned Camp pending wet successor: " + current);
                }
                placeWater(position, wet, world, finalStates, finalEntities);
                iterator.remove();
                changed = true;
            }
        }
    }

    private static void placeWater(Vec position, String wetState, WorldTransaction world,
            Map<Vec, String> finalStates, Map<Vec, LoadedEntity> finalEntities) {
        boolean written = world.setBlockAndUpdate(position, wetState);
        if (written) {
            finalStates.put(position, wetState);
            LoadedEntity loaded = finalEntities.get(position);
            if (loaded != null && !loaded.blockIdentity.equals(blockKey(wetState))) {
                finalEntities.remove(position);
            }
        }
        // SimpleWaterloggedBlock schedules the Water source tick after accepting the liquid;
        // the underlying setBlock boolean controls final-state evidence, not tick scheduling.
        world.scheduleFluidTick(position, WATER_FLUID, WATER_TICK_DELAY);
    }

    private static BlockEntityAccess requireEntity(BlockEntityAccess handle, EntitySpec expected,
            Vec position) {
        if (handle == null
                || !expected.blockIdentity.equals(handle.blockIdentity())
                || !expected.entityType.equals(handle.entityType())) {
            throw new IllegalStateException(
                    "Abandoned Camp block entity lifecycle mismatch at " + position);
        }
        return handle;
    }

    private static FluidState requireFluid(FluidState fluid) {
        return Objects.requireNonNull(fluid, "Abandoned Camp fluid-state query result");
    }

    private static Settlement settlement(Clip clip, Map<Vec, String> finalStates,
            Map<Vec, LoadedEntity> finalEntities) {
        ArrayList<Map.Entry<Vec, String>> states = new ArrayList<>(finalStates.entrySet());
        states.sort((left, right) -> POSITION_ORDER.compare(left.getKey(), right.getKey()));
        ArrayList<FinalState> frozenStates = new ArrayList<>(states.size());
        for (Map.Entry<Vec, String> state : states) {
            frozenStates.add(new FinalState(state.getKey(), state.getValue()));
        }

        ArrayList<LoadedEntity> entities = new ArrayList<>(finalEntities.values());
        entities.sort((left, right) -> POSITION_ORDER.compare(left.position, right.position));
        ArrayList<BentPayload> bent = new ArrayList<>(entities.size());
        ArrayList<LootPayload> loot = new ArrayList<>();
        for (LoadedEntity entity : entities) {
            String state = finalStates.get(entity.position);
            if (state == null || !entity.blockIdentity.equals(blockKey(state))) {
                throw new IllegalStateException(
                        "Abandoned Camp final BENT state mismatch at " + entity.position);
            }
            int bentOrdinal = bent.size();
            bent.add(new BentPayload(entity.position, state, entity.blockIdentity,
                    entity.entityType, entity.canonicalNbt, entity.encounterOrder));
            if (entity.lootTable != null) {
                loot.add(new LootPayload(entity.position, entity.lootTable,
                        entity.lootSeed, bentOrdinal));
            }
        }
        return new Settlement(clip, frozenStates, bent, loot);
    }

    /** Exact structure-template local transform around the template origin. */
    public static Vec transform(Vec local, Rotation rotation) {
        Objects.requireNonNull(local, "local Abandoned Camp position");
        Objects.requireNonNull(rotation, "Abandoned Camp rotation");
        return local.rotateAroundOrigin(rotation);
    }

    /** Rotates the authenticated exact-state properties used by Camp templates. */
    public static String rotateState(String state, Rotation rotation) {
        Objects.requireNonNull(state, "Abandoned Camp exact state");
        Objects.requireNonNull(rotation, "Abandoned Camp rotation");
        int open = state.indexOf('[');
        if (open < 0) return exact(state);
        if (!state.endsWith("]") || open == 0) {
            throw new IllegalArgumentException("malformed Abandoned Camp exact state: " + state);
        }
        String block = state.substring(0, open).trim();
        TreeMap<String, String> properties = new TreeMap<>();
        String body = state.substring(open + 1, state.length() - 1);
        if (!body.isBlank()) {
            for (String raw : body.split(",")) {
                String item = raw.trim();
                int equals = item.indexOf('=');
                if (equals <= 0 || equals == item.length() - 1) {
                    throw new IllegalArgumentException(
                            "malformed Abandoned Camp state property: " + item);
                }
                String key = item.substring(0, equals).trim();
                String value = item.substring(equals + 1).trim();
                if ("facing".equals(key)) value = rotateHorizontal(value, rotation);
                if ("axis".equals(key) && quarterTurn(rotation)) {
                    if ("x".equals(value)) value = "z";
                    else if ("z".equals(value)) value = "x";
                }
                if (isHorizontal(key)) key = rotateHorizontal(key, rotation);
                if (properties.put(key, value) != null) {
                    throw new IllegalArgumentException(
                            "duplicate Abandoned Camp state property after rotation: " + key);
                }
            }
        }
        StringBuilder result = new StringBuilder(block).append('[');
        boolean first = true;
        for (Map.Entry<String, String> property : properties.entrySet()) {
            if (!first) result.append(',');
            result.append(property.getKey()).append('=').append(property.getValue());
            first = false;
        }
        return exact(result.append(']').toString());
    }

    private static boolean quarterTurn(Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90
                || rotation == Rotation.COUNTERCLOCKWISE_90;
    }

    private static String rotateHorizontal(String value, Rotation rotation) {
        if (!isHorizontal(value)) return value;
        List<String> directions = List.of("north", "east", "south", "west");
        int turns = switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
        return directions.get((directions.indexOf(value) + turns) & 3);
    }

    private static boolean isHorizontal(String value) {
        return "north".equals(value) || "east".equals(value)
                || "south".equals(value) || "west".equals(value);
    }

    private static String authenticatedWetSuccessor(String state) {
        if (!hasDryWaterloggedProperty(state)) return null;
        String candidate = state.replace("waterlogged=false", "waterlogged=true");
        if (!Mc263FeatureBlockState.supportsExactState(candidate)) return null;
        return exact(candidate);
    }

    private static boolean hasDryWaterloggedProperty(String state) {
        return state.contains("waterlogged=false");
    }

    private static String exact(String state) {
        return Mc263FeatureBlockState.fromExact(state).exactState();
    }

    private static String blockKey(String state) {
        int property = state.indexOf('[');
        return property < 0 ? state.trim() : state.substring(0, property).trim();
    }

    private static Vec offset(Vec position, Direction direction) {
        return new Vec(
                Math.addExact(position.x(), direction.dx),
                Math.addExact(position.y(), direction.dy),
                Math.addExact(position.z(), direction.dz));
    }

    private static final Comparator<Vec> POSITION_ORDER = Comparator
            .comparingInt(Vec::y).thenComparingInt(Vec::z).thenComparingInt(Vec::x);

    /** Inclusive world-space clip. */
    public static final class Clip {
        private final int minX, minY, minZ, maxX, maxY, maxZ;

        public Clip(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted Abandoned Camp clip");
            }
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public static Clip chunk(int chunkX, int chunkZ, int minY, int maxY) {
            int x = Math.multiplyExact(chunkX, 16);
            int z = Math.multiplyExact(chunkZ, 16);
            return new Clip(x, minY, z, Math.addExact(x, 15), maxY,
                    Math.addExact(z, 15));
        }

        public int minX() { return minX; }
        public int minY() { return minY; }
        public int minZ() { return minZ; }
        public int maxX() { return maxX; }
        public int maxY() { return maxY; }
        public int maxZ() { return maxZ; }

        public boolean contains(Vec position) {
            return position.x() >= minX && position.x() <= maxX
                    && position.y() >= minY && position.y() <= maxY
                    && position.z() >= minZ && position.z() <= maxZ;
        }

        boolean intersects(Mc263AbandonedCampProducer.Box box) {
            return box.maxX() >= minX && box.minX() <= maxX
                    && box.maxY() >= minY && box.minY() <= maxY
                    && box.maxZ() >= minZ && box.minZ() <= maxZ;
        }
    }

    /** Resolved fluid query result; height is the official position-sensitive fluid height. */
    public static final class FluidState {
        private final String fluidKey;
        private final boolean source;
        private final double height;

        public FluidState(String fluidKey, boolean source, double height) {
            this.fluidKey = Objects.requireNonNull(fluidKey, "fluid key");
            if (!Double.isFinite(height) || height < 0.0D) {
                throw new IllegalArgumentException("invalid Abandoned Camp fluid height");
            }
            if (fluidKey.isEmpty() && (source || height != 0.0D)) {
                throw new IllegalArgumentException("non-empty semantics on empty fluid state");
            }
            this.source = source;
            this.height = height;
        }

        public static FluidState emptyState() { return new FluidState("", false, 0.0D); }
        public String fluidKey() { return fluidKey; }
        public boolean empty() { return fluidKey.isEmpty(); }
        public boolean source() { return source; }
        public double height() { return height; }
    }

    /** Mutable staged block entity returned only by an isolated transaction. */
    public interface BlockEntityAccess {
        String blockIdentity();
        String entityType();
        void loadCanonicalNbt(byte[] canonicalNbt);
        void setChanged();
    }

    /**
     * Caller-owned transaction seam. Capability methods are pure. fork() must be isolated and
     * non-publishing. publish() must atomically expose the fork's blocks/ticks and the supplied
     * BENT/LOOT settlement, or throw while leaving the caller transaction unchanged.
     */
    public interface WorldTransaction {
        boolean supportsAtomicForkPublish();
        boolean supportsFluidStateQueries();
        boolean supportsBlockStateQueries();
        boolean supportsSetBlockAndUpdate();
        boolean supportsWriteFlags(int flags);
        boolean supportsExactState(String exactState);
        boolean supportsBentPayloads();
        boolean supportsLootPayloads();
        boolean supportsBlockEntity(String blockIdentity, String entityType);
        boolean supportsLootTable(String lootTable);
        boolean supportsFluidTick(String fluidKey, int delay);
        WorldTransaction fork();
        FluidState getFluidState(Vec position);
        String getBlockState(Vec position);
        boolean setBlock(Vec position, String exactState, int flags);
        boolean setBlockAndUpdate(Vec position, String exactState);
        BlockEntityAccess getBlockEntity(Vec position);
        void scheduleFluidTick(Vec position, String fluidKey, int delay);
        void publish(WorldTransaction isolated, Settlement settlement);
    }

    /**
     * Production transaction combines the accepted template lifecycle with the current-only direct
     * configured-feature seam. The covariant fork keeps both execution families inside the same
     * isolated transaction.
     */
    public interface ProductionWorldTransaction extends WorldTransaction, DirectWorldAccess {
        @Override ProductionWorldTransaction fork();
    }

    /** Immutable production-seam result, including the exact placement-RNG successor. */
    public static final class ProductionExecution {
        private final int stepLocalIndex;
        private final Settlement settlement;
        private final List<DirectPlacementResult> configuredResults;
        private final PlacementRandom randomSuccessor;

        private ProductionExecution(int stepLocalIndex, Settlement settlement,
                List<DirectPlacementResult> configuredResults, PlacementRandom randomSuccessor) {
            this.stepLocalIndex = stepLocalIndex;
            this.settlement = settlement;
            this.configuredResults = List.copyOf(configuredResults);
            this.randomSuccessor = randomSuccessor.copy();
        }

        public int stepLocalIndex() { return stepLocalIndex; }
        public Settlement settlement() { return settlement; }
        public List<DirectPlacementResult> configuredResults() { return configuredResults; }
        public PlacementRandom randomSuccessor() { return randomSuccessor.copy(); }
    }

    public static final class FinalState {
        private final Vec position;
        private final String exactState;

        private FinalState(Vec position, String exactState) {
            this.position = position;
            this.exactState = exactState;
        }

        public Vec position() { return position; }
        public String exactState() { return exactState; }
    }

    public static final class BentPayload {
        private final Vec position;
        private final String exactState;
        private final String blockIdentity;
        private final String entityType;
        private final byte[] canonicalNbt;
        private final int loadEncounterOrder;

        private BentPayload(Vec position, String exactState, String blockIdentity,
                String entityType, byte[] canonicalNbt, int loadEncounterOrder) {
            this.position = position;
            this.exactState = exactState;
            this.blockIdentity = blockIdentity;
            this.entityType = entityType;
            this.canonicalNbt = canonicalNbt.clone();
            this.loadEncounterOrder = loadEncounterOrder;
        }

        public Vec position() { return position; }
        public String exactState() { return exactState; }
        public String blockIdentity() { return blockIdentity; }
        public String entityType() { return entityType; }
        public byte[] canonicalNbt() { return canonicalNbt.clone(); }
        public int loadEncounterOrder() { return loadEncounterOrder; }
    }

    public static final class LootPayload {
        private final Vec position;
        private final String table;
        private final long signedLootSeed;
        private final int bentOrdinal;

        private LootPayload(Vec position, String table, long signedLootSeed, int bentOrdinal) {
            this.position = position;
            this.table = table;
            this.signedLootSeed = signedLootSeed;
            this.bentOrdinal = bentOrdinal;
        }

        public Vec position() { return position; }
        public String table() { return table; }
        public long signedLootSeed() { return signedLootSeed; }
        public int bentOrdinal() { return bentOrdinal; }
    }

    public static final class Settlement {
        private final Clip clip;
        private final List<FinalState> finalStates;
        private final List<BentPayload> bent;
        private final List<LootPayload> loot;

        private Settlement(Clip clip, List<FinalState> finalStates,
                List<BentPayload> bent, List<LootPayload> loot) {
            this.clip = clip;
            this.finalStates = List.copyOf(finalStates);
            this.bent = List.copyOf(bent);
            this.loot = List.copyOf(loot);
        }

        private static Settlement empty(Clip clip) {
            return new Settlement(clip, List.of(), List.of(), List.of());
        }

        public Clip clip() { return clip; }
        public List<FinalState> finalStates() { return finalStates; }
        public List<BentPayload> bent() { return bent; }
        public List<LootPayload> loot() { return loot; }
    }

    /** Exact WorldgenRandom(Xoroshiro128++) placement stream with caller-owned copy/commit. */
    public static final class PlacementRandom implements Mc263WorldgenRandomSource {
        private Xoroshiro source;
        private int count;

        private PlacementRandom(Xoroshiro source, int count) {
            this.source = source;
            this.count = count;
        }

        public static PlacementRandom forClip(long worldSeed, Clip clip, int stepLocalIndex) {
            Objects.requireNonNull(clip, "Abandoned Camp RNG clip");
            if (stepLocalIndex < 0 || stepLocalIndex >= Mc263AbandonedCampCatalog.STRUCTURE_COUNT) {
                throw new IllegalArgumentException(
                        "Abandoned Camp step-local index outside pinned structure set");
            }
            int chunkX = Math.floorDiv(clip.minX(), 16);
            int chunkZ = Math.floorDiv(clip.minZ(), 16);
            if (Math.floorDiv(clip.maxX(), 16) != chunkX
                    || Math.floorDiv(clip.maxZ(), 16) != chunkZ) {
                throw new IllegalArgumentException("Abandoned Camp RNG clip spans chunks");
            }
            int chunkMinX = Math.multiplyExact(chunkX, 16);
            int chunkMinZ = Math.multiplyExact(chunkZ, 16);

            PlacementRandom result = new PlacementRandom(new Xoroshiro(0L), 0);
            result.reseed(worldSeed);
            long xScale = result.nextLong() | 1L;
            long zScale = result.nextLong() | 1L;
            long decorationSeed = ((long) chunkMinX * xScale
                    + (long) chunkMinZ * zScale) ^ worldSeed;
            result.reseed(decorationSeed + stepLocalIndex + 40_000L);
            return result;
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("nonpositive placement bound");
            if ((bound & -bound) == bound) {
                return (int) (bound * (long) next(31) >> 31);
            }
            int bits;
            int value;
            do {
                bits = next(31);
                value = bits % bound;
            } while (bits - value + bound - 1 < 0);
            return value;
        }

        @Override
        public boolean nextBoolean() { return next(1) != 0; }

        @Override
        public float nextFloat() { return next(24) * 0x1.0p-24F; }

        public long nextLong() { return ((long) next(32) << 32) + next(32); }

        private int next(int bits) {
            count++;
            return (int) (source.nextLong() >>> (64 - bits));
        }

        private void reseed(long seed) { source = new Xoroshiro(seed); }

        public PlacementRandom copy() {
            return new PlacementRandom(source.copy(), count);
        }

        public void commit(PlacementRandom candidate) {
            Objects.requireNonNull(candidate, "Abandoned Camp random candidate");
            source = candidate.source.copy();
            count = candidate.count;
        }

        public long lo() { return source.lo; }
        public long hi() { return source.hi; }
        public int count() { return count; }

        /** Eight raw Xoroshiro words from a copied generator; this method consumes no caller draw. */
        public List<Long> continuationNextLongI64() {
            Xoroshiro copy = source.copy();
            ArrayList<Long> result = new ArrayList<>(8);
            for (int index = 0; index < 8; index++) result.add(copy.nextLong());
            return List.copyOf(result);
        }
    }

    private static final class Xoroshiro {
        private static final long SILVER = 0x6A09E667F3BCC909L;
        private static final long GOLDEN = 0x9E3779B97F4A7C15L;
        private long lo;
        private long hi;

        private Xoroshiro(long seed) {
            long first = seed ^ SILVER;
            set(mix(first), mix(first + GOLDEN));
        }

        private Xoroshiro(long lo, long hi) { set(lo, hi); }

        private void set(long lo, long hi) {
            if ((lo | hi) == 0L) {
                this.lo = GOLDEN;
                this.hi = SILVER;
            } else {
                this.lo = lo;
                this.hi = hi;
            }
        }

        private long nextLong() {
            long first = lo;
            long second = hi;
            long value = Long.rotateLeft(first + second, 17) + first;
            second ^= first;
            lo = Long.rotateLeft(first, 49) ^ second ^ second << 21;
            hi = Long.rotateLeft(second, 28);
            return value;
        }

        private Xoroshiro copy() { return new Xoroshiro(lo, hi); }

        private static long mix(long value) {
            value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
            value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
            return value ^ value >>> 31;
        }
    }

    /** H11C state/query mediation between a template-mutated world and the direct tree kernel. */
    private static final class MixedFeatureWorld implements DirectWorldAccess {
        private final ProductionWorldTransaction inner;
        private final ArrayList<StateRead> stateHistory = new ArrayList<>();
        private final LinkedHashMap<Vec, String> cachedStates = new LinkedHashMap<>();
        private final LinkedHashMap<Vec, String> syntheticStates = new LinkedHashMap<>();
        private PendingStairShape pendingStair;
        private PendingVineNeighbor pendingVineNeighbor;
        private int injectedExternalOperations;
        private int suppressedExternalOperations;
        private String failure;

        private MixedFeatureWorld(ProductionWorldTransaction inner) {
            this.inner = inner;
        }

        private void requireSuccess() {
            if (failure != null) throw new IllegalArgumentException(failure);
        }

        private void clearQueryPair() {
            stateHistory.clear();
            cachedStates.clear();
            syntheticStates.clear();
            pendingStair = null;
            pendingVineNeighbor = null;
        }

        private void recordFailure(String message) {
            if (failure == null) failure = message;
        }

        private String exactPredicateSurrogate(String exact) {
            String block = blockKey(exact);
            if (!Set.of("minecraft:white_wool_stairs", "minecraft:barrel",
                    "minecraft:straw_bed").contains(block)) {
                return null;
            }
            Mc263FeatureBlockState source;
            try {
                source = Mc263FeatureBlockState.fromExact(exact);
            } catch (RuntimeException error) {
                recordFailure("invalid Camp stair predicate exact state " + exact + ": "
                        + error.getMessage());
                return PREDICATE_NON_SOLID_SURROGATE;
            }
            String surrogateExact = "minecraft:barrel".equals(block)
                    ? PREDICATE_SOLID_SURROGATE : PREDICATE_NON_SOLID_SURROGATE;
            Mc263FeatureBlockState surrogate = Mc263FeatureBlockState.fromExact(surrogateExact);
            boolean sameCommonSemantics = source.isAir() == surrogate.isAir()
                    && source.replaceableByTrees() == surrogate.replaceableByTrees()
                    && source.isLogsTag() == surrogate.isLogsTag()
                    && source.isLeavesTag() == surrogate.isLeavesTag()
                    && source.fluidAmount() == surrogate.fluidAmount()
                    && source.isSolidRender() == surrogate.isSolidRender();
            if (!sameCommonSemantics
                    || ("minecraft:white_wool_stairs".equals(block)
                    && !"false".equals(stateProperty(exact, "waterlogged")))) {
                recordFailure("unauthenticated Camp template predicate semantics: " + exact);
            }
            return surrogateExact;
        }

        private boolean pendingOwnerInternalQuery(Vec position) {
            if (pendingStair == null
                    || !"minecraft:vine".equals(blockKey(pendingStair.ownerState))) {
                return false;
            }
            if (position.equals(new Vec(pendingStair.owner.x(), pendingStair.owner.y() + 1,
                    pendingStair.owner.z()))) {
                return true;
            }
            String direction = singleVineHorizontalFace(pendingStair.ownerState);
            return direction != null && position.equals(offsetHorizontal(pendingStair.owner, direction));
        }

        private void finishPendingStair(PendingStairShape pending) {
            String shape = stairShape(pending);
            String current = stateProperty(pending.exactState, "shape");
            if (current == null) current = "straight";
            if (!shape.equals(current)) {
                recordFailure("unauthenticated Camp stair shape successor "
                        + pending.exactState + " -> " + shape);
            }
        }

        private void flushStairBeforeUnrelatedQuery(Vec position) {
            if (pendingStair == null || pendingOwnerInternalQuery(position)) return;
            PendingStairShape pending = pendingStair;
            pendingStair = null;
            stateHistory.clear();
            cachedStates.clear();
            syntheticStates.clear();
            finishPendingStair(pending);
        }

        private String prefetchPendingVineNeighborBeforeAbove(Vec position) {
            PendingVineNeighbor pending = pendingVineNeighbor;
            if (pending == null
                    || !position.equals(new Vec(pending.neighbor.x(), pending.neighbor.y() + 1,
                            pending.neighbor.z()))
                    || "true".equals(stateProperty(pending.neighborState, "up"))) {
                return null;
            }
            String face = singleVineHorizontalFace(pending.neighborState);
            if (face == null) return null;
            pendingVineNeighbor = null;
            Vec support = offsetHorizontal(pending.neighbor, face);
            String exact = inner.exactState(support.x(), support.y(), support.z());
            injectSourceWaterSupportTick(support, exact);
            boolean sturdy = vineSupportFull(exact, face);
            cachedStates.put(support, vineSupportKernelState(exact, sturdy));
            if (sturdy) {
                suppressedExternalOperations++;
                return "minecraft:air";
            }
            return null;
        }

        private String prefetchVineSupportBeforeAbove(Vec position) {
            if (stateHistory.size() < 2) return null;
            StateRead changed = stateHistory.get(stateHistory.size() - 2);
            StateRead neighbor = stateHistory.get(stateHistory.size() - 1);
            if ("minecraft:vine".equals(blockKey(neighbor.state))
                    && position.equals(new Vec(neighbor.position.x(), neighbor.position.y() + 1,
                            neighbor.position.z()))
                    && !"true".equals(stateProperty(neighbor.state, "up"))) {
                if (changed.position.equals(position)) {
                    String face = singleVineHorizontalFace(neighbor.state);
                    if (face != null) {
                        Vec support = offsetHorizontal(neighbor.position, face);
                        String exact = inner.exactState(support.x(), support.y(), support.z());
                        injectSourceWaterSupportTick(support, exact);
                        boolean sturdy = vineSupportFull(exact, face);
                        cachedStates.put(support, vineSupportKernelState(exact, sturdy));
                        if (sturdy) {
                            suppressedExternalOperations++;
                            return changed.state;
                        }
                        String reread = inner.exactState(
                                position.x(), position.y(), position.z());
                        if (!reread.equals(changed.state)) {
                            recordFailure("Camp unsupported-vine above-state drift: expected "
                                    + changed.state + ", got " + reread);
                        }
                        injectedExternalOperations++;
                        return null;
                    }
                }
                String changedDirection = horizontalDirection(neighbor.position, changed.position);
                String face = singleVineHorizontalFace(neighbor.state);
                if (changedDirection != null && face != null && !changedDirection.equals(face)) {
                    syntheticStates.put(offsetHorizontal(neighbor.position, face),
                            "minecraft:jungle_log[axis=y]");
                    suppressedExternalOperations++;
                    return "minecraft:air";
                }
            }
            StateRead vine = stateHistory.get(stateHistory.size() - 2);
            StateRead neighborState = stateHistory.get(stateHistory.size() - 1);
            if (!"minecraft:vine".equals(blockKey(vine.state))
                    || !position.equals(new Vec(vine.position.x(), vine.position.y() + 1,
                            vine.position.z()))
                    || !adjacent(vine.position, neighborState.position)) {
                return null;
            }
            String direction = singleVineHorizontalFace(vine.state);
            if (direction == null) return null;
            Vec support = offsetHorizontal(vine.position, direction);
            if (cachedStates.containsKey(support)) return null;
            String exact = inner.exactState(support.x(), support.y(), support.z());
            injectSourceWaterSupportTick(support, exact);
            boolean sturdy = vineSupportFull(exact, direction);
            cachedStates.put(support, vineSupportKernelState(exact, sturdy));
            if (sturdy) {
                suppressedExternalOperations++;
                return "minecraft:air";
            }
            return null;
        }

        private void prefetchShortGrassSupportAfterInertNeighbor(Vec position, String exact) {
            if (!"minecraft:short_grass".equals(blockKey(exact)) || stateHistory.isEmpty()) return;
            StateRead neighbor = stateHistory.getLast();
            int dx = position.x() - neighbor.position.x();
            int dy = position.y() - neighbor.position.y();
            int dz = position.z() - neighbor.position.z();
            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) return;
            String neighborBlock = blockKey(neighbor.state);
            boolean neighborHasExternalUpdate = neighborBlock.endsWith("_leaves")
                    || "minecraft:leaf_litter".equals(neighborBlock)
                    || "minecraft:pale_hanging_moss".equals(neighborBlock)
                    || ("minecraft:vine".equals(neighborBlock)
                    && !(dx == 0 && dy == -1 && dz == 0));
            if (neighborHasExternalUpdate) return;
            Vec support = new Vec(position.x(), position.y() - 1, position.z());
            String supportState = queryInnerState(support);
            try {
                if (!Mc263FeatureBlockState.fromExact(supportState).isSubstrateOverworld()) {
                    recordFailure("Camp short-grass support is not authenticated substrate: "
                            + supportState);
                }
            } catch (RuntimeException error) {
                recordFailure("invalid Camp short-grass support state " + supportState + ": "
                        + error.getMessage());
            }
        }

        private void prefetchNeighborShortGrassSupportAfterVineAbove(Vec position, String exact) {
            if (!"minecraft:vine".equals(blockKey(exact)) || stateHistory.size() < 2) return;
            StateRead vine = stateHistory.get(stateHistory.size() - 2);
            StateRead grass = stateHistory.get(stateHistory.size() - 1);
            if (!"minecraft:vine".equals(blockKey(vine.state))
                    || !"minecraft:short_grass".equals(blockKey(grass.state))
                    || !position.equals(new Vec(vine.position.x(), vine.position.y() + 1,
                            vine.position.z()))
                    || vine.position.y() != grass.position.y()
                    || Math.abs(vine.position.x() - grass.position.x())
                            + Math.abs(vine.position.z() - grass.position.z()) != 1) {
                return;
            }
            Vec support = new Vec(grass.position.x(), grass.position.y() - 1, grass.position.z());
            String supportState = queryInnerState(support);
            try {
                if (!Mc263FeatureBlockState.fromExact(supportState).isSubstrateOverworld()) {
                    recordFailure("Camp neighbor short-grass support is not authenticated substrate: "
                            + supportState);
                }
            } catch (RuntimeException error) {
                recordFailure("invalid Camp neighbor short-grass support state " + supportState
                        + ": " + error.getMessage());
            }
        }

        private void injectSourceWaterSupportTick(Vec position, String exact) {
            if (!isSourceWaterBlock(exact)) return;
            inner.scheduleFluidTick(position.x(), position.y(), position.z(), WATER_FLUID,
                    WATER_TICK_DELAY);
            injectedExternalOperations++;
        }

        private void injectSourceWaterNeighborTick(Vec position, String exact) {
            if (!isSourceWaterBlock(exact) || stateHistory.isEmpty()) return;
            StateRead neighbor = stateHistory.getLast();
            if (!"minecraft:vine".equals(blockKey(neighbor.state))
                    || neighbor.position.x() != position.x()
                    || neighbor.position.y() != position.y() + 1
                    || neighbor.position.z() != position.z()) {
                return;
            }
            inner.scheduleFluidTick(position.x(), position.y(), position.z(), WATER_FLUID,
                    WATER_TICK_DELAY);
            injectedExternalOperations++;
        }

        private String queryInnerState(Vec position) {
            String exact = inner.exactState(position.x(), position.y(), position.z());
            injectedExternalOperations++;
            return exact;
        }

        private boolean canTakeStairShape(Vec position, String state, String direction) {
            String other = queryInnerState(offsetHorizontal(position, direction));
            return !isStairState(other)
                    || !Objects.equals(stateProperty(other, "facing"),
                            stateProperty(state, "facing"))
                    || !Objects.equals(stateProperty(other, "half"), stateProperty(state, "half"));
        }

        private String stairShape(PendingStairShape pending) {
            String state = pending.exactState;
            String facing = stateProperty(state, "facing");
            if (facing == null) {
                recordFailure("Camp stair facing missing: " + state);
                return "straight";
            }
            String half = stateProperty(state, "half");
            String front = queryInnerState(offsetHorizontal(pending.stair, facing));
            if (isStairState(front) && Objects.equals(stateProperty(front, "half"), half)) {
                String frontFacing = stateProperty(front, "facing");
                if (frontFacing != null && horizontalAxis(frontFacing) != horizontalAxis(facing)
                        && canTakeStairShape(pending.stair, state,
                                oppositeHorizontal(frontFacing))) {
                    return frontFacing.equals(counterClockwiseHorizontal(facing))
                            ? "outer_left" : "outer_right";
                }
            }
            String back = queryInnerState(offsetHorizontal(
                    pending.stair, oppositeHorizontal(facing)));
            if (isStairState(back) && Objects.equals(stateProperty(back, "half"), half)) {
                String backFacing = stateProperty(back, "facing");
                if (backFacing != null && horizontalAxis(backFacing) != horizontalAxis(facing)
                        && canTakeStairShape(pending.stair, state, backFacing)) {
                    return backFacing.equals(counterClockwiseHorizontal(facing))
                            ? "inner_left" : "inner_right";
                }
            }
            return "straight";
        }

        private void flushStairAfterLeafTick(int x, int y, int z, String block, int delay) {
            PendingStairShape pending = pendingStair;
            if (pending == null) return;
            pendingStair = null;
            stateHistory.clear();
            cachedStates.clear();
            syntheticStates.clear();
            if (!pending.owner.equals(new Vec(x, y, z)) || !block.endsWith("_leaves")
                    || delay != 1) {
                return;
            }
            finishPendingStair(pending);
        }

        @Override public int minY() { return inner.minY(); }
        @Override public boolean supportsFeature(String key) { return inner.supportsFeature(key); }
        @Override public boolean supportsExactState(String exact) {
            return inner.supportsExactState(exact);
        }
        @Override public boolean supportsTreeFinalization() {
            return inner.supportsTreeFinalization();
        }
        @Override public boolean supportsWriteFlags(int flags) {
            return inner.supportsWriteFlags(flags);
        }
        @Override public boolean supportsBentPayloads() { return inner.supportsBentPayloads(); }
        @Override public boolean supportsBeePayloads() { return inner.supportsBeePayloads(); }
        @Override public boolean supportsBlockTicks() { return inner.supportsBlockTicks(); }
        @Override public boolean supportsFluidTicks() { return inner.supportsFluidTicks(); }
        @Override public boolean supportsPostprocessing() { return inner.supportsPostprocessing(); }
        @Override public boolean supportsBeneathTreePodzolTag() {
            return inner.supportsBeneathTreePodzolTag();
        }
        @Override public boolean supportsWorldHeightQueries() {
            return inner.supportsWorldHeightQueries();
        }
        @Override public boolean supportsBlockStateQueries() {
            return inner.supportsBlockStateQueries();
        }
        @Override public boolean supportsInternalTreePredicates() {
            return inner.supportsInternalTreePredicates();
        }
        @Override public boolean supportsOwnership(long owner) {
            return inner.supportsOwnership(owner);
        }

        @Override
        public int worldHeight() {
            clearQueryPair();
            return inner.worldHeight();
        }

        @Override
        public String exactState(int x, int y, int z) {
            Vec position = new Vec(x, y, z);
            flushStairBeforeUnrelatedQuery(position);
            String exact = prefetchPendingVineNeighborBeforeAbove(position);
            if (exact == null) exact = prefetchVineSupportBeforeAbove(position);
            if (exact == null && syntheticStates.containsKey(position)) {
                exact = syntheticStates.remove(position);
                suppressedExternalOperations++;
            }
            if (exact == null && cachedStates.containsKey(position)) {
                exact = cachedStates.remove(position);
            }
            if (exact == null) exact = inner.exactState(x, y, z);
            prefetchShortGrassSupportAfterInertNeighbor(position, exact);
            prefetchNeighborShortGrassSupportAfterVineAbove(position, exact);
            injectSourceWaterNeighborTick(position, exact);
            if (!stateHistory.isEmpty()) {
                StateRead vine = stateHistory.getLast();
                if ("minecraft:vine".equals(blockKey(vine.state))
                        && !blockKey(exact).endsWith("_leaves")
                        && position.equals(new Vec(vine.position.x(), vine.position.y() + 1,
                                vine.position.z()))) {
                    cachedStates.put(position, exact);
                    suppressedExternalOperations++;
                }
            }
            if (pendingStair == null && isStairState(exact) && !stateHistory.isEmpty()) {
                StateRead owner = stateHistory.getLast();
                if (adjacent(owner.position, position)) {
                    pendingStair = new PendingStairShape(
                            owner.position, owner.state, position, exact);
                }
            }
            if ("minecraft:vine".equals(blockKey(exact)) && !stateHistory.isEmpty()) {
                StateRead owner = stateHistory.getLast();
                if (adjacent(owner.position, position)) {
                    pendingVineNeighbor = new PendingVineNeighbor(
                            owner.position, position, exact);
                }
            }
            stateHistory.add(new StateRead(position, exact));
            if (stateHistory.size() > 2) stateHistory.removeFirst();
            return exact;
        }

        @Override
        public String internalTreePredicateState(int x, int y, int z) {
            clearQueryPair();
            if (injectedExternalOperations != 0 || suppressedExternalOperations != 0) {
                recordFailure("Camp internal predicate followed mixed external-order adjustments");
            }
            String original = inner.internalTreePredicateState(x, y, z);
            String surrogate = exactPredicateSurrogate(original);
            return surrogate == null ? original : surrogate;
        }

        @Override
        public String internalTreePredicateFluidState(int x, int y, int z) {
            clearQueryPair();
            if (injectedExternalOperations != 0 || suppressedExternalOperations != 0) {
                recordFailure(
                        "Camp internal fluid predicate followed mixed external-order adjustments");
            }
            return inner.internalTreePredicateFluidState(x, y, z);
        }

        @Override
        public boolean setBlock(int x, int y, int z, String exact, int flags, long owner) {
            clearQueryPair();
            return inner.setBlock(x, y, z, exact, flags, owner);
        }

        @Override
        public void storeBent(int x, int y, int z, String blockIdentity, String entityType) {
            clearQueryPair();
            inner.storeBent(x, y, z, blockIdentity, entityType);
        }

        @Override
        public void storeBee(int x, int y, int z, int ticksInHive) {
            clearQueryPair();
            inner.storeBee(x, y, z, ticksInHive);
        }

        @Override
        public void scheduleBlockTick(int x, int y, int z, String block, int delay) {
            inner.scheduleBlockTick(x, y, z, block, delay);
            flushStairAfterLeafTick(x, y, z, block, delay);
        }

        @Override
        public void scheduleFluidTick(int x, int y, int z, String fluidKey, int delay) {
            clearQueryPair();
            inner.scheduleFluidTick(x, y, z, fluidKey, delay);
        }

        @Override
        public void markPostprocess(int x, int y, int z) {
            clearQueryPair();
            inner.markPostprocess(x, y, z);
        }
    }

    private static boolean isSourceWaterBlock(String exact) {
        if (!"minecraft:water".equals(blockKey(exact))) return false;
        try {
            Mc263FeatureBlockState state = Mc263FeatureBlockState.fromExact(exact);
            return "minecraft:water".equals(state.fluidTypeKey()) && state.fluidAmount() == 8;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String stateProperty(String state, String key) {
        int open = state.indexOf('[');
        if (open < 0 || !state.endsWith("]")) return null;
        String body = state.substring(open + 1, state.length() - 1);
        for (String entry : body.split(",")) {
            String[] pair = entry.trim().split("=", 2);
            if (pair.length == 2 && pair[0].trim().equals(key)) return pair[1].trim();
        }
        return null;
    }

    private static boolean isStairState(String state) {
        if (!"minecraft:white_wool_stairs".equals(blockKey(state))) return false;
        return Set.of("straight", "inner_left", "inner_right", "outer_left", "outer_right")
                .contains(stateProperty(state, "shape"));
    }

    private static boolean adjacent(Vec left, Vec right) {
        return left.y() == right.y()
                && Math.abs(left.x() - right.x()) + Math.abs(left.z() - right.z()) == 1;
    }

    private static String singleVineHorizontalFace(String state) {
        String result = null;
        for (String direction : List.of("north", "east", "south", "west")) {
            if (!"true".equals(stateProperty(state, direction))) continue;
            if (result != null) return null;
            result = direction;
        }
        return result;
    }

    private static String vineSupportKernelState(String exact, boolean sturdy) {
        if (sturdy && "minecraft:white_wool_stairs".equals(blockKey(exact))) {
            return "minecraft:jungle_log[axis=y]";
        }
        return exact;
    }

    private static boolean vineSupportFull(String exact, String direction) {
        OcclusionFace face = vineSupportFace(direction);
        if ("minecraft:white_wool_stairs".equals(blockKey(exact))) {
            String facing = stateProperty(exact, "facing");
            return ("north".equals(facing) && face == OcclusionFace.NORTH)
                    || ("south".equals(facing) && face == OcclusionFace.SOUTH)
                    || ("west".equals(facing) && face == OcclusionFace.WEST)
                    || ("east".equals(facing) && face == OcclusionFace.EAST);
        }
        try {
            return Mc263FeatureBlockState.fromExact(exact).isSupportOrCollisionFull(face);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static OcclusionFace vineSupportFace(String direction) {
        return switch (direction) {
            case "north" -> OcclusionFace.SOUTH;
            case "south" -> OcclusionFace.NORTH;
            case "west" -> OcclusionFace.EAST;
            case "east" -> OcclusionFace.WEST;
            default -> OcclusionFace.UP;
        };
    }

    private static String horizontalDirection(Vec from, Vec to) {
        if (from.y() != to.y()) return null;
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();
        if (dx == 0 && dz == -1) return "north";
        if (dx == 0 && dz == 1) return "south";
        if (dx == -1 && dz == 0) return "west";
        if (dx == 1 && dz == 0) return "east";
        return null;
    }

    private static Vec offsetHorizontal(Vec position, String direction) {
        return switch (direction) {
            case "north" -> new Vec(position.x(), position.y(), position.z() - 1);
            case "south" -> new Vec(position.x(), position.y(), position.z() + 1);
            case "west" -> new Vec(position.x() - 1, position.y(), position.z());
            case "east" -> new Vec(position.x() + 1, position.y(), position.z());
            default -> position;
        };
    }

    private static String oppositeHorizontal(String direction) {
        return switch (direction) {
            case "north" -> "south";
            case "south" -> "north";
            case "west" -> "east";
            case "east" -> "west";
            default -> direction;
        };
    }

    private static String counterClockwiseHorizontal(String direction) {
        return switch (direction) {
            case "north" -> "west";
            case "west" -> "south";
            case "south" -> "east";
            case "east" -> "north";
            default -> direction;
        };
    }

    private static int horizontalAxis(String direction) {
        return switch (direction) {
            case "north", "south" -> 0;
            case "west", "east" -> 1;
            default -> 2;
        };
    }

    private static final class StateRead {
        private final Vec position;
        private final String state;
        private StateRead(Vec position, String state) { this.position = position;this.state = state; }
    }

    private static final class PendingStairShape {
        private final Vec owner;
        private final String ownerState;
        private final Vec stair;
        private final String exactState;
        private PendingStairShape(Vec owner, String ownerState, Vec stair, String exactState) {
            this.owner=owner;this.ownerState=ownerState;this.stair=stair;this.exactState=exactState;
        }
    }

    private static final class PendingVineNeighbor {
        private final Vec owner;
        private final Vec neighbor;
        private final String neighborState;
        private PendingVineNeighbor(Vec owner, Vec neighbor, String neighborState) {
            this.owner=owner;this.neighbor=neighbor;this.neighborState=neighborState;
        }
    }

    private static final class Plan {
        private final List<PreparedPiece> pieces;
        private final Set<String> exactStates;

        private Plan(List<PreparedPiece> pieces, Set<String> exactStates) {
            this.pieces = List.copyOf(pieces);
            this.exactStates = Set.copyOf(exactStates);
        }
    }

    private static final class PreparedPiece {
        private final int ordinal;
        private final List<PreparedCell> cells;
        private final String featureKey;
        private final Vec featureOrigin;

        private PreparedPiece(int ordinal, List<PreparedCell> cells, String featureKey,
                Vec featureOrigin) {
            this.ordinal = ordinal;
            this.cells = List.copyOf(cells);
            this.featureKey = featureKey;
            this.featureOrigin = featureOrigin;
        }

        private static PreparedPiece template(int ordinal, List<PreparedCell> cells) {
            return new PreparedPiece(ordinal, cells, null, null);
        }

        private static PreparedPiece feature(int ordinal, String featureKey, Vec origin) {
            return new PreparedPiece(ordinal, List.of(), featureKey, origin);
        }

        private boolean feature() { return featureKey != null; }
    }

    private static final class PreparedCell {
        private final Vec position;
        private final String state;
        private final String wetState;
        private final EntitySpec entity;

        private PreparedCell(Vec position, String state, String wetState, EntitySpec entity) {
            this.position = position;
            this.state = state;
            this.wetState = wetState;
            this.entity = entity;
        }
    }

    private static final class EntitySpec {
        private final String blockIdentity;
        private final String entityType;
        private final String lootTable;

        private EntitySpec(String blockIdentity, String entityType, String lootTable) {
            this.blockIdentity = blockIdentity;
            this.entityType = entityType;
            this.lootTable = lootTable;
        }
    }

    private static final class LoadedEntity {
        private final Vec position;
        private final String blockIdentity;
        private final String entityType;
        private final String lootTable;
        private final long lootSeed;
        private final byte[] canonicalNbt;
        private final int encounterOrder;

        private LoadedEntity(Vec position, String blockIdentity, String entityType,
                String lootTable, long lootSeed, byte[] canonicalNbt, int encounterOrder) {
            this.position = position;
            this.blockIdentity = blockIdentity;
            this.entityType = entityType;
            this.lootTable = lootTable;
            this.lootSeed = lootSeed;
            this.canonicalNbt = canonicalNbt.clone();
            this.encounterOrder = encounterOrder;
        }
    }

    private enum Direction {
        UP(0, 1, 0),
        NORTH(0, 0, -1),
        EAST(1, 0, 0),
        SOUTH(0, 0, 1),
        WEST(-1, 0, 0);

        private final int dx;
        private final int dy;
        private final int dz;

        Direction(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }
}
