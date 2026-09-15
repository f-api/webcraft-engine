package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.McRandom;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import com.gameexpert.terrain.mc.structure.Mc263DesertPyramidProgram;
import com.gameexpert.terrain.mc.structure.Mc263DesertPyramidStartGenerator;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Kind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.PieceKind;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkReferences;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkStarts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.PiecePayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.StartEntry;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Dormant exact adapter for the pinned desert-pyramid carrier and ordered Java program.
 * Production activation remains closed until the complete 52-entry registry is exact.
 */
final class Mc263DesertPyramidCanonicalExecutor implements
        Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor {
    private static final int STEP = 4;
    private static final int INDEX = 19;
    private static final long BIOME_MASK = 0x00000800000000L;
    private final Map<Mc263FeaturesRegion, McRandom> levelRandoms =
            new WeakHashMap<>();

    private Mc263DesertPyramidCanonicalExecutor() { }

    static void register(
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry registry) {
        Objects.requireNonNull(registry, "canonical structure executor registry")
                .register(Mc263DesertPyramidProgram.STRUCTURE,
                        new Mc263DesertPyramidCanonicalExecutor());
    }

    @Override
    public void preflight(
            Mc263CanonicalFeaturesProducerSkeleton.StructurePreflightContext context) {
        Objects.requireNonNull(context, "desert-pyramid preflight context");
        requireSchedule(context.entry());
        requireSourceBinding(context.references(), context.clip());
        requireRegistry(context.carrier().registry());
        for (Mc263StructureCarrier.ValidStart start : context.carrier().resolveStarts(
                context.references(), Mc263DesertPyramidProgram.STRUCTURE)) {
            Mc263DesertPyramidStartGenerator.loadPersisted(context.worldSeed(), start);
        }
    }

    @Override
    public void place(Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context) {
        Objects.requireNonNull(context, "desert-pyramid placement context");
        Mc263FeatureDispatcher.StructurePlacementContext dispatcher = context.dispatcher();
        requireDispatcher(dispatcher, context.references());
        requireSourceBinding(context.references(), context.clip());

        List<String> startKeys = context.carrier().resolveStarts(context.references(),
                Mc263DesertPyramidProgram.STRUCTURE).stream()
                .map(Mc263StructureCarrier.ValidStart::startKey).toList();
        for (String startKey : startKeys) {
            Mc263StructureCarrier predecessorCarrier = context.carrier();
            Mc263StructureCarrier.ValidStart start = predecessorCarrier.resolveStarts(
                    context.references(), Mc263DesertPyramidProgram.STRUCTURE).stream()
                    .filter(candidate -> candidate.startKey().equals(startKey))
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "evolved desert-pyramid carrier lost referenced start"));
            var loaded = Mc263DesertPyramidStartGenerator.loadPersisted(
                    dispatcher.worldSeed(), start);
            CapturingRegionWorld world = new CapturingRegionWorld(dispatcher.region(),
                    levelRandom(dispatcher));
            Mc263DesertPyramidProgram.ExecutionResult execution =
                    loaded.hardcodedCarrier().successor().heightPosition() < 0
                            ? execute(loaded.hardcodedCarrier(), programClip(context.clip()),
                                    world, dispatcher.random(), dispatcher.worldSeed())
                            : executeReloaded(loaded.hardcodedCarrier(),
                                    loaded.adjustedBoundingBox(), programClip(context.clip()),
                                    world, dispatcher.random(), dispatcher.worldSeed());
            Mc263DesertPyramidProgram.AtomicSettlement settlement = world.requireSettlement();
            Mc263StructureCarrier successorCarrier = rebuild(predecessorCarrier, startKey,
                    execution.successor(), execution.adjustedBoundingBox());
            context.commitStructureBatch(predecessorCarrier, successorCarrier,
                    structureBatch(settlement, startKey, context));
        }
    }

    private static void requireSchedule(Mc263StructureIndexReceipt.Entry entry) {
        if (entry.step() != STEP || entry.index() != INDEX
                || !entry.key().equals(Mc263DesertPyramidProgram.STRUCTURE)
                || entry.biomeMask() != BIOME_MASK) {
            throw new IllegalArgumentException("desert-pyramid schedule mismatch");
        }
    }

    private static void requireRegistry(Mc263StructureCarrier.Registry registry) {
        Mc263StructureCarrier.StructureDefinition definition = registry.require(
                Mc263DesertPyramidProgram.STRUCTURE);
        int ordinal = Mc263StructureIndexReceipt.entries().stream()
                .map(Mc263StructureIndexReceipt.Entry::key).toList()
                .indexOf(Mc263DesertPyramidProgram.STRUCTURE);
        if (definition.registryOrdinal() != ordinal || definition.decorationStep() != STEP
                || definition.terrainAdjustment()
                        != Mc263StructureCarrier.TerrainAdjustment.NONE) {
            throw new IllegalArgumentException("desert-pyramid carrier registry mismatch");
        }
    }

    private static void requireSourceBinding(Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        if (!Mc263CanonicalFeaturesProducerSkeleton.SourceClip.chunk(
                references.chunkX(), references.chunkZ()).equals(clip)) {
            throw new IllegalArgumentException("desert-pyramid source clip mismatch");
        }
    }

    private static void requireDispatcher(
            Mc263FeatureDispatcher.StructurePlacementContext dispatcher,
            Mc263StructureCarrier.ChunkReferences references) {
        if (dispatcher.step() != STEP || dispatcher.globalIndex() != INDEX
                || !dispatcher.structureKey().equals(Mc263DesertPyramidProgram.STRUCTURE)
                || dispatcher.sourceChunkX() != references.chunkX()
                || dispatcher.sourceChunkZ() != references.chunkZ()) {
            throw new IllegalArgumentException("desert-pyramid dispatcher/source mismatch");
        }
    }

    private synchronized McRandom levelRandom(
            Mc263FeatureDispatcher.StructurePlacementContext dispatcher) {
        return levelRandoms.computeIfAbsent(dispatcher.region(), region ->
                createLevelRandom(dispatcher.worldSeed(), region.targetChunkX(),
                        region.targetChunkZ()));
    }

    static McRandom createLevelRandom(long worldSeed, int targetChunkX, int targetChunkZ) {
        return new McRandom(worldSeed)
                .forkPositional()
                .fromHashOf("minecraft:worldgen_region_random")
                .forkPositional()
                .at(Math.multiplyExact(targetChunkX, Blocks.CHUNK_X), 0,
                        Math.multiplyExact(targetChunkZ, Blocks.CHUNK_Z));
    }

    private static Mc263DesertPyramidProgram.Clip programClip(
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        return new Mc263DesertPyramidProgram.Clip(clip.minX(), clip.minY(), clip.minZ(),
                clip.maxX(), clip.maxY(), clip.maxZ());
    }

    static Mc263FeaturesRegion.StructureBatch structureBatch(
            Mc263DesertPyramidProgram.AtomicSettlement settlement, String startKey,
            Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput placement) {
        long owner = Mc263StructureOwner.owner(Mc263DesertPyramidProgram.STRUCTURE, startKey);
        List<Mc263FeaturesRegion.StructureBlockWrite> blocks = settlement.blocks().stream()
                .map(value -> new Mc263FeaturesRegion.StructureBlockWrite(value.position().x(),
                        value.position().y(), value.position().z(), value.exactState(), owner))
                .toList();
        List<Mc263FeaturesRegion.StructureLoot> loot = settlement.loot().stream()
                .filter(value -> placement.clip().contains(value.position().x(),
                        value.position().y(), value.position().z()))
                .map(value -> new Mc263FeaturesRegion.StructureLoot(value.position().x(),
                        value.position().y(), value.position().z(), value.table(), value.seed(),
                        placement.productionContext(value.position().x(), value.position().y(),
                                value.position().z(), value.table())))
                .toList();
        List<Mc263FeaturesRegion.StructureArchaeology> archaeology =
                settlement.archaeology().stream()
                        .map(value -> new Mc263FeaturesRegion.StructureArchaeology(
                                value.position().x(), value.position().y(), value.position().z(),
                                value.table(), value.seed()))
                        .toList();
        return new Mc263FeaturesRegion.StructureBatch(
                blocks, loot, archaeology, List.of());
    }

    static Mc263DesertPyramidProgram.ExecutionResult execute(
            Mc263HardcodedStructureCarrier carrier,
            Mc263DesertPyramidProgram.Clip clip,
            Mc263DesertPyramidProgram.WorldAccess world,
            WorldgenRandom structureRandom,
            long worldSeed) {
        requireCanonical(carrier, worldSeed);
        return Mc263DesertPyramidProgram.execute(carrier,
                Objects.requireNonNull(clip, "desert-pyramid clip"),
                Objects.requireNonNull(world, "desert-pyramid world"),
                Objects.requireNonNull(structureRandom, "desert-pyramid structure RNG"),
                worldSeed);
    }

    static Mc263DesertPyramidProgram.ExecutionResult executeReloaded(
            Mc263HardcodedStructureCarrier carrier,
            BoundingBox persistedAdjustedBox,
            Mc263DesertPyramidProgram.Clip clip,
            Mc263DesertPyramidProgram.WorldAccess world,
            WorldgenRandom structureRandom,
            long worldSeed) {
        requireCanonical(carrier, worldSeed);
        return Mc263DesertPyramidProgram.execute(carrier,
                Objects.requireNonNull(persistedAdjustedBox,
                        "persisted desert-pyramid adjusted box"),
                Objects.requireNonNull(clip, "desert-pyramid clip"),
                Objects.requireNonNull(world, "desert-pyramid world"),
                Objects.requireNonNull(structureRandom, "desert-pyramid structure RNG"),
                worldSeed);
    }

    record Settlement(Mc263StructureCarrier structureCarrier,
            Mc263FinalChunkCodec.FinalChunk finalChunk) { }

    @FunctionalInterface
    interface ExactStateSupport {
        boolean supports(String exactState);
    }

    /**
     * Builds the mutable STR263C1 successor and schema-4 LOOT/ARCH successor atomically.
     * The current production state registry is deliberately checked in full before either value
     * is built, so the dormant bridge fails closed while any pyramid state remains unsupported.
     */
    static Settlement settle(Mc263StructureCarrier structureCarrier,
            ChunkReferences references, Mc263HardcodedStructureCarrier predecessor,
            Mc263DesertPyramidProgram.ExecutionResult execution,
            Mc263FinalChunkCodec.FinalChunk finalChunk) {
        return settle(structureCarrier, references, predecessor, execution, finalChunk,
                exactState -> {
                    try {
                        Mc263FeatureBlockState.fromExact(exactState);
                        return true;
                    } catch (IllegalArgumentException unsupported) {
                        return false;
                    }
                });
    }

    static Settlement settle(Mc263StructureCarrier structureCarrier,
            ChunkReferences references, Mc263HardcodedStructureCarrier predecessor,
            Mc263DesertPyramidProgram.ExecutionResult execution,
            Mc263FinalChunkCodec.FinalChunk finalChunk, ExactStateSupport states) {
        Objects.requireNonNull(structureCarrier, "STR263C1 carrier");
        Objects.requireNonNull(references, "desert-pyramid references");
        Objects.requireNonNull(predecessor, "desert-pyramid predecessor");
        Objects.requireNonNull(execution, "desert-pyramid execution");
        Objects.requireNonNull(finalChunk, "MCF263LC final chunk");
        Objects.requireNonNull(states, "desert-pyramid exact-state support");
        Mc263FinalChunkCodec.validate(finalChunk);
        requireCanonical(predecessor, predecessor.worldSeed());
        requireCanonical(execution.successor(), predecessor.worldSeed());
        if (!Arrays.equals(execution.successor().encodeCanonical(), predecessor
                .withSuccessor(execution.successor().successor()).encodeCanonical())) {
            throw new IllegalArgumentException("desert-pyramid execution/predecessor mismatch");
        }
        preflightStates(execution, states);
        if (references.chunkX() != finalChunk.chunkX()
                || references.chunkZ() != finalChunk.chunkZ()) {
            throw new IllegalArgumentException("desert-pyramid settlement chunk mismatch");
        }

        SelectedStart selected = selectStart(
                structureCarrier, references, predecessor, execution);
        Mc263FinalChunkSidecars merged = mergeSidecars(
                finalChunk, execution.loot(), execution.archaeology(), execution.blocks());
        Mc263FinalChunkCodec.FinalChunk successorChunk = applyBlocks(
                finalChunk, execution.blocks(), merged);
        Mc263FinalChunkCodec.encode(successorChunk);

        Mc263StructureCarrier successorCarrier = rebuild(structureCarrier,
                selected.startKey(), execution.successor(), execution.adjustedBoundingBox());
        successorCarrier = successorCarrier.strictlyDecoded();
        return new Settlement(successorCarrier, successorChunk);
    }

    private static Mc263FinalChunkCodec.FinalChunk applyBlocks(
            Mc263FinalChunkCodec.FinalChunk source,
            List<Mc263DesertPyramidProgram.BlockMutation> mutations,
            Mc263FinalChunkSidecars sidecars) {
        short[] ids = source.blockIds();
        Map<Integer, Mc263FeatureBlockState> overrides =
                new HashMap<>(source.stateOverrides());
        boolean[] touchedColumns = new boolean[Blocks.CHUNK_X * Blocks.CHUNK_Z];
        for (var mutation : mutations) {
            int packed = packed(source, mutation.position());
            Mc263FeatureBlockState state = Mc263FeatureBlockState.fromExact(
                    mutation.exactState());
            ids[packed] = (short) state.blockId();
            if (Mc263ExactStateCodec.stateCode(state) == 0) {
                overrides.remove(packed);
            } else {
                overrides.put(packed, state);
            }
            int localX = Math.floorMod(mutation.position().x(), Blocks.CHUNK_X);
            int localZ = Math.floorMod(mutation.position().z(), Blocks.CHUNK_Z);
            touchedColumns[localX + localZ * Blocks.CHUNK_X] = true;
        }

        int[] worldSurface = source.worldSurfaceWg();
        int[] oceanFloor = source.oceanFloorWg();
        int[] motionBlocking = source.motionBlocking();
        for (int localZ = 0; localZ < Blocks.CHUNK_Z; localZ++) {
            for (int localX = 0; localX < Blocks.CHUNK_X; localX++) {
                int column = localX + localZ * Blocks.CHUNK_X;
                if (!touchedColumns[column]) continue;
                worldSurface[column] = scanHeight(ids, overrides, localX, localZ,
                        HeightKind.WORLD_SURFACE);
                oceanFloor[column] = scanHeight(ids, overrides, localX, localZ,
                        HeightKind.OCEAN_FLOOR);
                motionBlocking[column] = scanHeight(ids, overrides, localX, localZ,
                        HeightKind.MOTION_BLOCKING);
            }
        }
        return new Mc263FinalChunkCodec.FinalChunk(source.chunkX(), source.chunkZ(), ids,
                overrides, worldSurface, oceanFloor, motionBlocking, sidecars);
    }

    private static int scanHeight(short[] ids,
            Map<Integer, Mc263FeatureBlockState> overrides, int localX, int localZ,
            HeightKind kind) {
        for (int y = Blocks.MAX_Y; y >= Blocks.MIN_Y; y--) {
            int packed = Blocks.blockIndex(localX, y, localZ);
            Mc263FeatureBlockState state = overrides.get(packed);
            if (state == null) {
                state = Mc263FeatureBlockState.defaultForId(Short.toUnsignedInt(ids[packed]));
            }
            boolean blocksMotion = state.blocksMotionInHeightmapNoLeaves() || state.isLeaves();
            boolean matches = switch (kind) {
                case WORLD_SURFACE -> !state.isAir();
                case OCEAN_FLOOR -> blocksMotion;
                case MOTION_BLOCKING -> blocksMotion
                        || state.fluidKind() != Mc263FeatureBlockState.FluidKind.NONE;
            };
            if (matches) return y + 1;
        }
        return Blocks.MIN_Y;
    }

    private enum HeightKind { WORLD_SURFACE, OCEAN_FLOOR, MOTION_BLOCKING }

    private static void preflightStates(Mc263DesertPyramidProgram.ExecutionResult execution,
            ExactStateSupport states) {
        HashSet<String> checked = new HashSet<>();
        for (String exactState : Mc263DesertPyramidProgram.requiredExactStates()) {
            if (checked.add(exactState) && !states.supports(exactState)) {
                throw new UnsupportedOperationException(
                        "desert-pyramid final-carrier exact state: " + exactState);
            }
        }
        for (var mutation : execution.blocks()) {
            if (checked.add(mutation.exactState()) && !states.supports(mutation.exactState())) {
                throw new UnsupportedOperationException(
                        "desert-pyramid final-carrier exact state: "
                                + mutation.exactState());
            }
        }
    }

    /**
     * The referenced start this execution's predecessor was loaded from.
     *
     * <p>{@code ChunkGenerator#applyBiomeDecoration} binds one feature seed per (chunk, structure,
     * decoration step) and then places every element of {@code
     * StructureManager#startsForStructure(SectionPos, Structure)} -- a {@code List} drained from
     * the chunk's whole {@code LongSet} of references -- through one shared {@code WorldgenRandom}.
     * Two desert pyramids whose adjusted boxes both reach one chunk are therefore authentic, so the
     * closure is not capped at one member; the settlement is bound to the start it was executed
     * from instead. Only a repeated start is rejected, because {@code ChunkStarts} holds at most one
     * start per (origin chunk, structure) and {@code ReferenceSet} forbids a duplicate origin, so a
     * repeat proves a corrupt closure rather than two pyramids.</p>
     */
    private static ValidStart boundStart(Mc263StructureCarrier carrier,
            ChunkReferences references, Mc263HardcodedStructureCarrier predecessor) {
        List<ValidStart> starts = carrier.resolveStarts(
                references, Mc263DesertPyramidProgram.STRUCTURE);
        if (starts.stream().map(ValidStart::startKey).distinct().count() != starts.size()) {
            throw new IllegalArgumentException(
                    "desert-pyramid reference closure repeats a start");
        }
        List<ValidStart> bound = starts.stream()
                .filter(value -> value.originChunkX() == predecessor.chunkX()
                        && value.originChunkZ() == predecessor.chunkZ()).toList();
        if (bound.size() != 1) {
            throw new IllegalArgumentException(
                    "desert-pyramid start evidence set mismatch");
        }
        return bound.getFirst();
    }

    private static SelectedStart selectStart(Mc263StructureCarrier carrier,
            ChunkReferences references, Mc263HardcodedStructureCarrier predecessor,
            Mc263DesertPyramidProgram.ExecutionResult execution) {
        ValidStart start = boundStart(carrier, references, predecessor);
        if (start.originChunkX() != predecessor.chunkX()
                || start.originChunkZ() != predecessor.chunkZ()
                || start.orderedPieces().size() != 1) {
            throw new IllegalArgumentException("desert-pyramid start/carrier mismatch");
        }
        Piece piece = start.orderedPieces().getFirst();
        requirePieceShape(piece);
        byte[] current = piece.persistedPayload().binaryNbtCompound();
        byte[] initial = Mc263DesertPyramidProgram.canonicalInitialPieceNbt(predecessor);
        byte[] successor = Mc263DesertPyramidProgram.canonicalPieceNbt(
                execution.successor(), execution.adjustedBoundingBox());
        if (!Arrays.equals(current, initial) && !Arrays.equals(current, successor)) {
            throw new IllegalArgumentException(
                    "noncanonical desert-pyramid persisted NBT");
        }
        return new SelectedStart(start.startKey());
    }

    private static void requirePieceShape(Piece piece) {
        if (!piece.pieceType().equals(Mc263DesertPyramidProgram.PIECE_TYPE)
                || piece.poolElement()
                || piece.projection() != Mc263StructureCarrier.Projection.NOT_APPLICABLE
                || piece.groundLevelDelta() != 0 || !piece.junctions().isEmpty()) {
            throw new IllegalArgumentException("expected exact minecraft:tedp piece");
        }
    }

    private static Mc263FinalChunkSidecars mergeSidecars(
            Mc263FinalChunkCodec.FinalChunk chunk,
            List<Mc263DesertPyramidProgram.LootMutation> lootEvidence,
            List<Mc263DesertPyramidProgram.ArchaeologyMutation> archaeologyEvidence,
            List<Mc263DesertPyramidProgram.BlockMutation> blocks) {
        requireAuthenticatedLootRows(chunk, lootEvidence, blocks);
        ArrayList<Mc263FinalChunkSidecars.Loot> loot =
                new ArrayList<>(chunk.sidecars().loot());
        ArrayList<Mc263FinalChunkSidecars.Archaeology> archaeology =
                new ArrayList<>(chunk.sidecars().archaeology());
        Map<Integer, Mc263FinalChunkSidecars.Loot> lootByPosition = new HashMap<>();
        for (var value : loot) lootByPosition.put(value.packed(), value);
        Map<Integer, Mc263FinalChunkSidecars.Archaeology> archaeologyByPosition = new HashMap<>();
        for (var value : archaeology) archaeologyByPosition.put(value.packed(), value);

        for (var evidence : lootEvidence) {
            int packed = packed(chunk, evidence.position());
            String facing = chestFacing(blocks, evidence.position());
            var value = new Mc263FinalChunkSidecars.Loot(
                    packed, facing, evidence.table(), evidence.seed());
            mergeExact(loot, lootByPosition, packed, value,
                    "desert-pyramid LOOT conflicts with settled sidecar");
        }
        for (var evidence : archaeologyEvidence) {
            int packed = packed(chunk, evidence.position());
            if (!lastState(blocks, evidence.position())
                    .equals("minecraft:suspicious_sand[dusted=0]")
                    || evidence.seed() != packedBlockPos(evidence.position())) {
                throw new IllegalArgumentException(
                        "noncanonical desert-pyramid ARCH evidence");
            }
            var value = new Mc263FinalChunkSidecars.Archaeology(
                    packed, evidence.table(), evidence.seed());
            mergeExact(archaeology, archaeologyByPosition, packed, value,
                    "desert-pyramid ARCH conflicts with settled sidecar");
        }
        var source = chunk.sidecars();
        return new Mc263FinalChunkSidecars(source.blockTicks(), source.fluidTicks(), loot,
                source.spawners(), source.owners(), archaeology, source.bees(),
                source.blockEntities(), source.entities(), source.containerLootDeclarations());
    }

    private static void requireAuthenticatedLootRows(
            Mc263FinalChunkCodec.FinalChunk chunk,
            List<Mc263DesertPyramidProgram.LootMutation> lootEvidence,
            List<Mc263DesertPyramidProgram.BlockMutation> blocks) {
        for (var evidence : lootEvidence) {
            int packed = packed(chunk, evidence.position());
            String facing = chestFacing(blocks, evidence.position());
            Mc263FinalChunkSidecars.Loot row = new Mc263FinalChunkSidecars.Loot(
                    packed, facing, evidence.table(), evidence.seed());
            if (!chunk.sidecars().loot().contains(row)) {
                throw new IllegalStateException(
                        "desert-pyramid LDEC upstream handoff missing: "
                                + "Mc263FinalChunkAssembler must provide the authenticated "
                                + "LOOT row and declaration before settlement");
            }
        }
    }

    private static <T> void mergeExact(List<T> target, Map<Integer, T> byPosition,
            int packed, T value, String conflict) {
        T existing = byPosition.get(packed);
        if (existing == null) {
            byPosition.put(packed, value);
            target.add(value);
        } else if (!existing.equals(value)) {
            throw new IllegalArgumentException(conflict);
        }
    }

    private static String chestFacing(
            List<Mc263DesertPyramidProgram.BlockMutation> blocks,
            Mc263DesertPyramidProgram.BlockPos position) {
        String state = lastState(blocks, position);
        String prefix = "minecraft:chest[facing=";
        if (!state.startsWith(prefix)) {
            throw new IllegalArgumentException(
                    "desert-pyramid LOOT position lacks staged chest");
        }
        return state.substring(prefix.length(), state.indexOf(',', prefix.length()));
    }

    private static String lastState(List<Mc263DesertPyramidProgram.BlockMutation> blocks,
            Mc263DesertPyramidProgram.BlockPos position) {
        String state = null;
        for (var block : blocks) {
            if (block.position().equals(position)) state = block.exactState();
        }
        if (state == null) {
            throw new IllegalArgumentException(
                    "desert-pyramid sidecar position lacks staged block");
        }
        return state;
    }

    private static int packed(Mc263FinalChunkCodec.FinalChunk chunk,
            Mc263DesertPyramidProgram.BlockPos position) {
        if (Math.floorDiv(position.x(), 16) != chunk.chunkX()
                || Math.floorDiv(position.z(), 16) != chunk.chunkZ()) {
            throw new IllegalArgumentException(
                    "desert-pyramid sidecar outside settlement chunk");
        }
        return com.gameexpert.terrain.Blocks.blockIndex(
                Math.floorMod(position.x(), 16), position.y(), Math.floorMod(position.z(), 16));
    }

    private static long packedBlockPos(Mc263DesertPyramidProgram.BlockPos position) {
        return ((long) position.x() & 0x3FFFFFFL) << 38
                | ((long) position.z() & 0x3FFFFFFL) << 12
                | (long) position.y() & 0xFFFL;
    }

    private static Mc263StructureCarrier rebuild(Mc263StructureCarrier source,
            String selectedStart, Mc263HardcodedStructureCarrier successor,
            BoundingBox adjustedBox) {
        byte[] payload = Mc263DesertPyramidProgram.canonicalPieceNbt(successor, adjustedBox);
        var replacementBox = new Mc263StructureCarrier.BoundingBox(adjustedBox.minX(),
                adjustedBox.minY(), adjustedBox.minZ(), adjustedBox.maxX(), adjustedBox.maxY(),
                adjustedBox.maxZ());
        ArrayList<ChunkStarts> chunks = new ArrayList<>();
        for (ChunkStarts chunk : source.startChunks()) {
            ArrayList<StartEntry> starts = new ArrayList<>();
            for (StartEntry entry : chunk.orderedStarts()) {
                if (!(entry.body() instanceof ValidStart start)
                        || !start.startKey().equals(selectedStart)) {
                    starts.add(entry);
                    continue;
                }
                Piece current = start.orderedPieces().getFirst();
                Piece replacement = new Piece(current.pieceType(), replacementBox, false,
                        Mc263StructureCarrier.Projection.NOT_APPLICABLE, 0, List.of(),
                        new PiecePayload(payload));
                starts.add(new StartEntry(entry.structureId(), new ValidStart(start.startKey(),
                        start.originChunkX(), start.originChunkZ(), start.references(),
                        replacementBox, List.of(replacement))));
            }
            chunks.add(chunk.withStarts(starts));
        }
        return new Mc263StructureCarrier(source.registry(), chunks, source.referenceChunks(),
                source.rawStartPayloads(), source.producerGraphPayloads());
    }

    private record SelectedStart(String startKey) { }

    private static final class CapturingRegionWorld implements
            Mc263DesertPyramidProgram.WorldAccess {
        private final Mc263FeaturesRegion region;
        private final McRandom levelRandom;
        private Mc263DesertPyramidProgram.AtomicSettlement settlement;

        private CapturingRegionWorld(Mc263FeaturesRegion region,
                McRandom levelRandom) {
            this.region = Objects.requireNonNull(region, "desert-pyramid region");
            this.levelRandom = Objects.requireNonNull(
                    levelRandom, "desert-pyramid level RNG");
        }

        @Override public boolean supportsExactState(String state) {
            return Mc263FeatureBlockState.supportsExactState(state);
        }
        @Override public boolean supportsMotionBlockingNoLeavesHeight() { return true; }
        @Override public boolean supportsReplaceableByStructuresQuery() { return true; }
        @Override public boolean supportsSolidRenderQuery() { return true; }
        @Override public boolean supportsLevelRandom() { return true; }
        @Override public boolean supportsLootSidecar(String table) {
            return Mc263DesertPyramidProgram.CHEST_LOOT.equals(table);
        }
        @Override public boolean supportsArchaeologySidecar(String table) {
            return Mc263DesertPyramidProgram.ARCHAEOLOGY_LOOT.equals(table);
        }
        @Override public boolean supportsAtomicSettlement() { return true; }
        @Override public int minY() { return Blocks.MIN_Y; }
        @Override public int maxY() { return Blocks.MAX_Y; }
        @Override public int motionBlockingNoLeaves(int x, int z) {
            return region.motionBlocking(x, z);
        }
        @Override public String blockState(Mc263DesertPyramidProgram.BlockPos position) {
            return region.blockState(position.x(), position.y(), position.z()).exactState();
        }
        @Override public boolean isReplaceableByStructures(
                Mc263DesertPyramidProgram.BlockPos position, String exactState) {
            Mc263FeatureBlockState state = Mc263FeatureBlockState.fromExact(exactState);
            return state.isAir() || state.fluidAmount() != 0
                    || state.blockKey().equals("minecraft:glow_lichen")
                    || state.blockKey().equals("minecraft:seagrass")
                    || state.blockKey().equals("minecraft:tall_seagrass");
        }
        @Override public boolean isSolidRender(
                Mc263DesertPyramidProgram.BlockPos position, String exactState) {
            return Mc263FeatureBlockState.fromExact(exactState).isSolidRender();
        }
        @Override public boolean nextLevelBoolean() { return levelRandom.nextBoolean(); }
        @Override public float nextLevelFloat() { return levelRandom.nextFloat(); }
        @Override public void settle(Mc263DesertPyramidProgram.AtomicSettlement value) {
            if (settlement != null) {
                throw new IllegalStateException("duplicate desert-pyramid atomic settlement");
            }
            settlement = Objects.requireNonNull(value, "desert-pyramid atomic settlement");
        }

        private Mc263DesertPyramidProgram.AtomicSettlement requireSettlement() {
            if (settlement == null) {
                throw new IllegalStateException("missing desert-pyramid atomic settlement");
            }
            return settlement;
        }
    }

    private static void requireCanonical(Mc263HardcodedStructureCarrier carrier,
            long worldSeed) {
        Objects.requireNonNull(carrier, "desert-pyramid carrier");
        if (carrier.kind() != Kind.DESERT_PYRAMID
                || carrier.orderedPieces().size() != 1
                || carrier.orderedPieces().getFirst().kind() != PieceKind.DESERT_PYRAMID
                || carrier.orderedPieces().getFirst().template() != null
                || carrier.worldSeed() != worldSeed) {
            throw new IllegalArgumentException("noncanonical desert-pyramid carrier shape");
        }
    }
}
