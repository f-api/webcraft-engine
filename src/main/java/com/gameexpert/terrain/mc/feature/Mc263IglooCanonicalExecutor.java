package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Kind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.PieceFact;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.PieceKind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Template;
import com.gameexpert.terrain.mc.structure.Mc263IglooStartGenerator;
import com.gameexpert.terrain.mc.structure.Mc263IglooStructureExecutor;
import com.gameexpert.terrain.mc.structure.Mc263IglooStructureExecutor.BlockPos;
import com.gameexpert.terrain.mc.structure.Mc263IglooStructureExecutor.Mutation;
import com.gameexpert.terrain.mc.structure.Mc263IglooStructureExecutor.MutationKind;
import com.gameexpert.terrain.mc.structure.Mc263IglooStructureExecutor.Result;
import com.gameexpert.terrain.mc.structure.Mc263IglooStructureExecutor.TraceEvent;
import com.gameexpert.terrain.mc.structure.Mc263IglooStructureExecutor.TraceKind;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Exact igloo execution and producer-owned STR263C1 + schema-4 structure settlement boundary.
 *
 * <p>It consumes the ordered evidence of the full repository-owned igloo grammar, validates all
 * lanes, and submits geometry, LOOT, BENT, ENTS and mutable STR state as one atomic structure
 * batch. Existing identical evidence is replay-idempotent; partial or conflicting block-entity,
 * entity, or loot evidence fails closed.</p>
 */
final class Mc263IglooCanonicalExecutor implements
        Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor {
    private static final int STEP = 4;
    private static final int INDEX = 21;
    private static final long BIOME_MASK = 0x00000000086000L;
    private static final byte[] VILLAGER_PAYLOAD =
            "IGL263E1\u0001".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] ZOMBIE_VILLAGER_PAYLOAD =
            "IGL263E1\u0002".getBytes(StandardCharsets.ISO_8859_1);

    private Mc263IglooCanonicalExecutor() { }

    static void register(
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry registry) {
        Objects.requireNonNull(registry, "canonical structure executor registry")
                .register(Mc263IglooStartGenerator.STRUCTURE,
                        new Mc263IglooCanonicalExecutor());
    }

    @Override
    public void preflight(
            Mc263CanonicalFeaturesProducerSkeleton.StructurePreflightContext context) {
        Objects.requireNonNull(context, "igloo preflight context");
        requireSchedule(context.entry());
        requireSourceBinding(context.references(), context.clip());
        requireRegistry(context.carrier().registry());
        for (Mc263StructureCarrier.ValidStart start : context.carrier().resolveStarts(
                context.references(), Mc263IglooStartGenerator.STRUCTURE)) {
            loadPersisted(context.worldSeed(), start);
        }
    }

    @Override
    public void place(Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context) {
        Objects.requireNonNull(context, "igloo placement context");
        Mc263FeatureDispatcher.StructurePlacementContext dispatcher = context.dispatcher();
        requireDispatcher(dispatcher, context.references());
        requireSourceBinding(context.references(), context.clip());
        requireRegistry(context.carrier().registry());

        List<String> startKeys = context.carrier().resolveStarts(context.references(),
                Mc263IglooStartGenerator.STRUCTURE).stream()
                .map(Mc263StructureCarrier.ValidStart::startKey).toList();
        int acceptedNextLongs = 0;
        for (String startKey : startKeys) {
            Mc263StructureCarrier predecessorCarrier = context.carrier();
            Mc263StructureCarrier.ChunkReferences latestReferences = context.references();
            Mc263StructureCarrier.ValidStart start = predecessorCarrier.resolveStarts(
                    latestReferences, Mc263IglooStartGenerator.STRUCTURE).stream()
                    .filter(candidate -> candidate.startKey().equals(startKey))
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "evolved igloo carrier lost referenced start"));
            Mc263HardcodedStructureCarrier predecessor = loadPersisted(
                    dispatcher.worldSeed(), start);
            RegionQueries queries = new RegionQueries(dispatcher.region());
            CapturingSink sink = new CapturingSink();
            Mc263DecorationRandom.WorldgenRandom candidate =
                    new Mc263DecorationRandom.WorldgenRandom(dispatcher.featureSeed());
            for (int draw = 0; draw < acceptedNextLongs; draw++) candidate.nextLong();
            int[] attemptedNextLongs = {0};
            Result execution = execute(predecessor, queries, () -> {
                        attemptedNextLongs[0]++;
                        return candidate.nextLong();
                    },
                    programClip(context.clip()), sink);
            if (!sink.mutations.equals(execution.orderedMutations())) {
                throw new IllegalStateException("igloo atomic sink/execution mismatch");
            }
            PreparedSettlement settlement = prepareFeaturesBatch(context, predecessorCarrier,
                    latestReferences, predecessor, execution, startKey);
            context.commitStructureBatch(predecessorCarrier, settlement.structureCarrier(),
                    settlement.batch());
            for (int draw = 0; draw < attemptedNextLongs[0]; draw++) {
                dispatcher.random().nextLong();
            }
            acceptedNextLongs = Math.addExact(acceptedNextLongs, attemptedNextLongs[0]);
        }
    }

    private static void requireSchedule(Mc263StructureIndexReceipt.Entry entry) {
        if (entry.step() != STEP || entry.index() != INDEX
                || !entry.key().equals(Mc263IglooStartGenerator.STRUCTURE)
                || entry.biomeMask() != BIOME_MASK) {
            throw new IllegalArgumentException("igloo schedule mismatch");
        }
    }

    private static void requireRegistry(Mc263StructureCarrier.Registry registry) {
        Mc263StructureCarrier.StructureDefinition definition =
                registry.require(Mc263IglooStartGenerator.STRUCTURE);
        int ordinal = Mc263StructureIndexReceipt.entries().stream()
                .map(Mc263StructureIndexReceipt.Entry::key).toList()
                .indexOf(Mc263IglooStartGenerator.STRUCTURE);
        if (definition.registryOrdinal() != ordinal || definition.decorationStep() != STEP
                || definition.terrainAdjustment()
                        != Mc263StructureCarrier.TerrainAdjustment.NONE) {
            throw new IllegalArgumentException("igloo carrier registry mismatch");
        }
    }

    private static void requireSourceBinding(Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        if (!Mc263CanonicalFeaturesProducerSkeleton.SourceClip.chunk(
                references.chunkX(), references.chunkZ()).equals(clip)) {
            throw new IllegalArgumentException("igloo source clip mismatch");
        }
    }

    private static void requireDispatcher(
            Mc263FeatureDispatcher.StructurePlacementContext dispatcher,
            Mc263StructureCarrier.ChunkReferences references) {
        if (dispatcher.step() != STEP || dispatcher.globalIndex() != INDEX
                || !dispatcher.structureKey().equals(Mc263IglooStartGenerator.STRUCTURE)
                || dispatcher.sourceChunkX() != references.chunkX()
                || dispatcher.sourceChunkZ() != references.chunkZ()) {
            throw new IllegalArgumentException("igloo dispatcher/source mismatch");
        }
    }

    private static Mc263IglooStructureExecutor.Clip programClip(
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        return new Mc263IglooStructureExecutor.Clip(clip.minX(), clip.minY(), clip.minZ(),
                clip.maxX(), clip.maxY(), clip.maxZ());
    }

    private static Mc263HardcodedStructureCarrier loadPersisted(long worldSeed,
            Mc263StructureCarrier.ValidStart start) {
        Objects.requireNonNull(start, "persisted igloo start");
        Mc263HardcodedStructureCarrier hardcoded = Mc263HardcodedStructureCarrier.plan(
                Kind.IGLOO, worldSeed, start.originChunkX(), start.originChunkZ());
        if (!start.startKey().equals(Mc263IglooStartGenerator.STRUCTURE + "@"
                + start.originChunkX() + "," + start.originChunkZ())
                || start.references() < 0
                || start.orderedPieces().size() != hardcoded.orderedPieces().size()) {
            throw new IllegalArgumentException("noncanonical persisted igloo start");
        }
        Mc263StructureCarrier.BoundingBox aggregate = null;
        for (int index = 0; index < start.orderedPieces().size(); index++) {
            Mc263StructureCarrier.Piece piece = start.orderedPieces().get(index);
            PieceFact fact = hardcoded.orderedPieces().get(index);
            requirePieceShape(piece);
            int yDelta = Math.subtractExact(piece.boundingBox().minY(),
                    fact.boundingBox().minY());
            if (!piece.boundingBox().equals(box(fact.boundingBox(), yDelta))
                    || !Arrays.equals(piece.persistedPayload().binaryNbtCompound(),
                            Mc263IglooStartGenerator.canonicalPieceNbt(
                                    hardcoded, index, yDelta))) {
                throw new IllegalArgumentException(
                        "noncanonical persisted igloo piece at " + index);
            }
            aggregate = aggregate == null ? piece.boundingBox()
                    : new Mc263StructureCarrier.BoundingBox(
                            Math.min(aggregate.minX(), piece.boundingBox().minX()),
                            Math.min(aggregate.minY(), piece.boundingBox().minY()),
                            Math.min(aggregate.minZ(), piece.boundingBox().minZ()),
                            Math.max(aggregate.maxX(), piece.boundingBox().maxX()),
                            Math.max(aggregate.maxY(), piece.boundingBox().maxY()),
                            Math.max(aggregate.maxZ(), piece.boundingBox().maxZ()));
        }
        if (!start.adjustedBoundingBox().equals(aggregate)) {
            throw new IllegalArgumentException("persisted igloo aggregate box mismatch");
        }
        return hardcoded;
    }

    /**
     * Provisional FEATURES semantic settlement.  The batch is the sole pre-assembly mutation;
     * LDEC is intentionally deferred to the post-dispatch final assembler.
     */
    static PreparedSettlement prepareFeaturesBatch(
            Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput placement,
            Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263HardcodedStructureCarrier predecessor, Result execution, String startKey) {
        long owner = Mc263StructureOwner.owner(Mc263IglooStartGenerator.STRUCTURE, startKey);
        List<Mc263FinalChunkSidecars.StructureEntity> entities = expectedEntities(
                predecessor, execution, references.chunkX(), references.chunkZ());
        List<Integer> yDeltas = validateTrace(predecessor, execution.trace());
        Binding binding = requireBinding(carrier, references, predecessor);
        MutationEvidence mutations = validateProvisionalMutations(execution.orderedMutations(),
                references.chunkX(), references.chunkZ());
        List<Mc263FeaturesRegion.StructureBlockWrite> blocks = mutations.blocks.stream()
                .map(value -> new Mc263FeaturesRegion.StructureBlockWrite(
                        value.position().x(), value.position().y(), value.position().z(),
                        Mc263FeatureBlockState.canonicalExactState(value.value()), owner)).toList();
        List<Mc263FeaturesRegion.StructureLoot> loot = mutations.loot.stream()
                .filter(value -> placement.clip().contains(value.position().x(),
                        value.position().y(), value.position().z()))
                .map(value -> new Mc263FeaturesRegion.StructureLoot(value.position().x(),
                        value.position().y(), value.position().z(), value.value(), value.seed(),
                        placement.productionContext(value.position().x(), value.position().y(),
                                value.position().z(), value.value())))
                .toList();
        List<Mc263FeaturesRegion.StructureBentEvidence> blockEntities = mutations.blockEntities.stream()
                .map(value -> new Mc263FeaturesRegion.StructureBentEvidence(
                        value.mutation.position().x(), value.mutation.position().y(),
                        value.mutation.position().z(), blockIdentityForSemantic(value.mutation.value()),
                        entityTypeForSemantic(value.mutation.value()), value.canonicalNbt))
                .toList();
        Mc263StructureCarrier successor = rebuild(carrier, binding.startKey, predecessor, yDeltas)
                .strictlyDecoded();
        return new PreparedSettlement(successor,
                new Mc263FeaturesRegion.StructureBatch(blocks, loot, List.of(),
                        blockEntities, entities));
    }

    record PreparedSettlement(Mc263StructureCarrier structureCarrier,
                              Mc263FeaturesRegion.StructureBatch batch) { }

    private static final class CapturingSink implements Mc263IglooStructureExecutor.AtomicSink {
        private boolean committed;
        private List<Mutation> mutations = List.of();
        @Override public void commit(List<Mutation> orderedMutations) {
            if (committed) {
                throw new IllegalStateException("igloo sink committed more than once");
            }
            committed = true;
            mutations = List.copyOf(orderedMutations);
        }
    }

    private static final class RegionQueries
            implements Mc263IglooStructureExecutor.QuerySource {
        private final Mc263FeaturesRegion region;
        private RegionQueries(Mc263FeaturesRegion region) {
            this.region = Objects.requireNonNull(region, "FEATURES region");
        }
        @Override public boolean supportsExactState(String exactState) {
            return Mc263FeatureBlockState.supportsExactState(exactState);
        }
        @Override public boolean supportsBlockEntityData(String semantic) {
            try {
                entityTypeForSemantic(semantic);
                return true;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        @Override public boolean supportsWorldSurfaceWg() { return true; }
        @Override public boolean supportsBlockStateQuery() { return true; }
        @Override public boolean supportsRandomizableContainerQuery() { return true; }
        @Override public boolean supportsLootSidecar() { return true; }
        @Override public int worldSurfaceWg(int blockX, int blockZ) {
            return region.worldSurfaceWg(blockX, blockZ);
        }
        @Override public String blockState(BlockPos position,
                Mc263IglooStructureExecutor.PendingView pending) {
            return region.blockState(position.x(), position.y(), position.z()).exactState();
        }
        @Override public boolean hasRandomizableContainer(BlockPos position,
                Mc263IglooStructureExecutor.PendingView pending) {
            return Mc263FeatureBlockState.fromExact(pending.blockState(position)).capability()
                    == Mc263FeatureBlockState.Capability.RANDOMIZABLE_CONTAINER;
        }
    }

    static final class Settlement {
        private final Mc263StructureCarrier structureCarrier;
        private final Mc263FinalChunkCodec.FinalChunk finalChunk;

        Settlement(Mc263StructureCarrier structureCarrier,
                Mc263FinalChunkCodec.FinalChunk finalChunk) {
            this.structureCarrier = Objects.requireNonNull(structureCarrier,
                    "igloo successor STR263C1 carrier");
            this.finalChunk = Objects.requireNonNull(finalChunk,
                    "igloo successor MCF263LC chunk");
        }

        Mc263StructureCarrier structureCarrier() { return structureCarrier; }
        Mc263FinalChunkCodec.FinalChunk finalChunk() { return finalChunk; }
    }

    static Result execute(Mc263HardcodedStructureCarrier carrier,
            Mc263IglooStructureExecutor.QuerySource queries,
            Mc263IglooStructureExecutor.RandomSource random,
            Mc263IglooStructureExecutor.Clip clip,
            Mc263IglooStructureExecutor.AtomicSink sink) {
        requireCarrier(carrier);
        return Mc263IglooStructureExecutor.execute(carrier,
                Objects.requireNonNull(queries, "igloo query source"),
                Objects.requireNonNull(random, "igloo structure random"),
                Objects.requireNonNull(clip, "igloo placement clip"),
                Objects.requireNonNull(sink, "igloo atomic mutation sink"));
    }

    /**
     * Atomically derives the aligned start and all target-chunk schema-4 mutation lanes from one
     * completed execution. Entity evidence is required in exact template encounter order.
     */
    static Settlement settle(Mc263StructureCarrier structureCarrier,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263HardcodedStructureCarrier predecessor, Result execution,
            List<Mc263FinalChunkSidecars.StructureEntity> orderedEntities,
            Mc263FinalChunkCodec.FinalChunk finalChunk) {
        Objects.requireNonNull(structureCarrier, "STR263C1 carrier");
        Objects.requireNonNull(references, "igloo references");
        requireCarrier(predecessor);
        Objects.requireNonNull(execution, "igloo execution evidence");
        Objects.requireNonNull(orderedEntities, "igloo ordered entity evidence");
        Objects.requireNonNull(finalChunk, "MCF263LC final chunk");
        if (references.chunkX() != finalChunk.chunkX()
                || references.chunkZ() != finalChunk.chunkZ()) {
            throw new IllegalArgumentException("igloo settlement chunk/reference mismatch");
        }
        Mc263FinalChunkCodec.validate(finalChunk);

        List<Integer> yDeltas = validateTrace(predecessor, execution.trace());
        Binding binding = requireBinding(structureCarrier, references, predecessor);
        MutationEvidence mutations = validateMutations(execution.orderedMutations(), finalChunk);
        requireAuthenticatedLootRows(finalChunk, mutations.loot);
        List<Mc263FinalChunkSidecars.StructureEntity> entities = List.copyOf(orderedEntities);
        List<Mc263FinalChunkSidecars.StructureEntity> expectedEntities = expectedEntities(
                predecessor, yDeltas, execution.clip(), finalChunk.chunkX(), finalChunk.chunkZ());
        if (!entities.equals(expectedEntities)) {
            throw new IllegalArgumentException("igloo ordered entity evidence mismatch");
        }

        Mc263FinalChunkCodec.FinalChunk successorChunk = apply(
                finalChunk, mutations, entities);
        Mc263StructureCarrier successorCarrier = rebuild(
                structureCarrier, binding.startKey, predecessor, yDeltas);
        successorCarrier = successorCarrier.strictlyDecoded();
        return new Settlement(successorCarrier, successorChunk);
    }

    static List<Mc263FinalChunkSidecars.StructureEntity> expectedEntities(
            Mc263HardcodedStructureCarrier predecessor, Result execution,
            int targetChunkX, int targetChunkZ) {
        requireCarrier(predecessor);
        return expectedEntities(predecessor, validateTrace(predecessor, execution.trace()),
                execution.clip(), targetChunkX, targetChunkZ);
    }

    private static List<Integer> validateTrace(Mc263HardcodedStructureCarrier predecessor,
            List<TraceEvent> trace) {
        List<TraceEvent> values = List.copyOf(Objects.requireNonNull(trace, "igloo trace"));
        ArrayList<Integer> deltas = new ArrayList<>(predecessor.orderedPieces().size());
        int cursor = 0;
        for (PieceFact piece : predecessor.orderedPieces()) {
            if (cursor >= values.size()
                    || values.get(cursor).kind() != TraceKind.HEIGHT_QUERY
                    || values.get(cursor).template() != piece.template()) {
                throw new IllegalArgumentException("igloo HEIGHT_QUERY trace order mismatch");
            }
            cursor++;
            if (cursor >= values.size() || values.get(cursor).kind() != TraceKind.BEGIN_PIECE
                    || values.get(cursor).template() != piece.template()) {
                throw new IllegalArgumentException("igloo BEGIN_PIECE trace order mismatch");
            }
            cursor++;
            Integer yDelta = null;
            boolean ended = false;
            while (cursor < values.size()) {
                TraceEvent event = values.get(cursor++);
                if (event.template() != piece.template()) {
                    throw new IllegalArgumentException("igloo piece trace template mismatch");
                }
                if (event.kind() == TraceKind.PROCESSOR && yDelta == null) {
                    if (event.position() == null) {
                        throw new IllegalArgumentException("igloo processor trace lacks position");
                    }
                    yDelta = Math.subtractExact(event.position().y(), piece.templateY());
                }
                if (event.kind() == TraceKind.END_PIECE) {
                    ended = true;
                    break;
                }
                if (event.kind() == TraceKind.HEIGHT_QUERY
                        || event.kind() == TraceKind.BEGIN_PIECE) {
                    throw new IllegalArgumentException("nested igloo piece trace");
                }
            }
            if (!ended || yDelta == null) {
                throw new IllegalArgumentException("incomplete igloo piece trace");
            }
            deltas.add(yDelta);
        }
        if (cursor != values.size()) {
            throw new IllegalArgumentException("trailing igloo execution trace");
        }
        return List.copyOf(deltas);
    }

    private static MutationEvidence validateMutations(List<Mutation> ordered,
            Mc263FinalChunkCodec.FinalChunk chunk) {
        List<Mutation> values = List.copyOf(Objects.requireNonNull(
                ordered, "igloo ordered mutations"));
        ArrayList<Mutation> blocks = new ArrayList<>();
        ArrayList<BlockEntityEvidence> blockEntities = new ArrayList<>();
        ArrayList<Mutation> loot = new ArrayList<>();
        Map<BlockPos, String> latestBlocks = new LinkedHashMap<>();
        for (Mutation mutation : values) {
            requireInChunk(chunk, mutation.position());
            switch (mutation.kind()) {
                case BLOCK -> {
                    requireExactStateSyntax(mutation.value());
                    blocks.add(mutation);
                    latestBlocks.put(mutation.position(), mutation.value());
                }
                case BLOCK_ENTITY -> {
                    String state = latestBlocks.get(mutation.position());
                    if (state == null || !blockIdentity(state).equals(
                            blockIdentityForSemantic(mutation.value()))) {
                        throw new IllegalArgumentException(
                                "igloo block-entity evidence lacks ordered block write");
                    }
                    blockEntities.add(new BlockEntityEvidence(mutation,
                            canonicalBlockEntity(mutation)));
                }
                case LOOT -> {
                    if (!mutation.value().equals(Mc263IglooStructureExecutor.LOOT_TABLE)) {
                        throw new IllegalArgumentException("igloo LOOT table mismatch");
                    }
                    loot.add(mutation);
                }
            }
        }
        return new MutationEvidence(List.copyOf(blocks), List.copyOf(blockEntities),
                List.copyOf(loot));
    }

    /** Validates the same execution facts for a pre-assembly StructureBatch without LDEC. */
    private static MutationEvidence validateProvisionalMutations(List<Mutation> ordered,
            int chunkX, int chunkZ) {
        List<Mutation> values = List.copyOf(Objects.requireNonNull(ordered,
                "igloo ordered mutations"));
        ArrayList<Mutation> blocks = new ArrayList<>();
        ArrayList<BlockEntityEvidence> blockEntities = new ArrayList<>();
        ArrayList<Mutation> loot = new ArrayList<>();
        Map<BlockPos, String> latestBlocks = new LinkedHashMap<>();
        for (Mutation mutation : values) {
            if (Math.floorDiv(mutation.position().x(), Blocks.CHUNK_X) != chunkX
                    || Math.floorDiv(mutation.position().z(), Blocks.CHUNK_Z) != chunkZ
                    || mutation.position().y() < Blocks.MIN_Y
                    || mutation.position().y() > Blocks.MAX_Y) {
                throw new IllegalArgumentException("igloo mutation outside settlement chunk");
            }
            switch (mutation.kind()) {
                case BLOCK -> {
                    requireExactStateSyntax(mutation.value());
                    blocks.add(mutation);
                    latestBlocks.put(mutation.position(), mutation.value());
                }
                case BLOCK_ENTITY -> {
                    String state = latestBlocks.get(mutation.position());
                    if (state == null || !blockIdentity(state).equals(
                            blockIdentityForSemantic(mutation.value()))) {
                        throw new IllegalArgumentException(
                                "igloo block-entity evidence lacks ordered block write");
                    }
                    blockEntities.add(new BlockEntityEvidence(mutation,
                            canonicalBlockEntity(mutation)));
                }
                case LOOT -> {
                    if (!mutation.value().equals(Mc263IglooStructureExecutor.LOOT_TABLE)) {
                        throw new IllegalArgumentException("igloo LOOT table mismatch");
                    }
                    String state = latestBlocks.get(mutation.position());
                    if (state == null || !Mc263FeatureBlockState.fromExact(state).blockKey()
                            .equals("minecraft:chest")) {
                        throw new IllegalArgumentException("igloo LOOT ordered chest evidence mismatch");
                    }
                    loot.add(mutation);
                }
            }
        }
        return new MutationEvidence(List.copyOf(blocks), List.copyOf(blockEntities),
                List.copyOf(loot));
    }

    /**
     * The referenced start this settlement's predecessor was loaded from.
     *
     * <p>{@code ChunkGenerator#applyBiomeDecoration} binds one feature seed per (chunk, structure,
     * decoration step) and then places every element of {@code
     * StructureManager#startsForStructure(SectionPos, Structure)} -- a {@code List} drained from
     * the chunk's whole {@code LongSet} of references -- through one shared {@code WorldgenRandom}.
     * Two igloos whose adjusted boxes both reach one chunk are therefore authentic, so the closure
     * is not capped at one member; the binding is taken from the start the execution ran on. Only a
     * repeated start is rejected, because {@code ChunkStarts} holds at most one start per (origin
     * chunk, structure) and {@code ReferenceSet} forbids a duplicate origin, so a repeat proves a
     * corrupt closure rather than two igloos.</p>
     */
    private static Mc263StructureCarrier.ValidStart boundStart(Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263HardcodedStructureCarrier predecessor) {
        List<Mc263StructureCarrier.ValidStart> starts = carrier.resolveStarts(
                references, Mc263IglooStartGenerator.STRUCTURE);
        if (starts.stream().map(Mc263StructureCarrier.ValidStart::startKey)
                .distinct().count() != starts.size()) {
            throw new IllegalArgumentException("igloo reference closure repeats a start");
        }
        List<Mc263StructureCarrier.ValidStart> bound = starts.stream()
                .filter(value -> value.originChunkX() == predecessor.chunkX()
                        && value.originChunkZ() == predecessor.chunkZ()).toList();
        if (bound.size() != 1) {
            throw new IllegalArgumentException("igloo start evidence set mismatch");
        }
        return bound.getFirst();
    }

    private static Binding requireBinding(Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263HardcodedStructureCarrier predecessor) {
        Mc263StructureCarrier.ValidStart start = boundStart(carrier, references, predecessor);
        if (start.originChunkX() != predecessor.chunkX()
                || start.originChunkZ() != predecessor.chunkZ()
                || !start.startKey().equals(Mc263IglooStartGenerator.STRUCTURE + "@"
                        + predecessor.chunkX() + "," + predecessor.chunkZ())
                || start.orderedPieces().size() != predecessor.orderedPieces().size()) {
            throw new IllegalArgumentException("igloo start/carrier mismatch");
        }
        // IglooPieces.IglooPiece.postProcess re-queries WORLD_SURFACE_WG at the entrance column
        // on every chunk the piece intersects, and restores templatePosition afterwards, so the
        // per-piece Y translation is recomputed per intersecting chunk instead of being frozen by
        // the first one. The entrance column lies inside the piece box, so the earlier chunk's own
        // placement can raise that heightmap and legitimately yield a different translation here.
        // The persisted start therefore carries whichever translation the earlier intersecting
        // chunk settled; it is bound by its own translation, never by this chunk's.
        List<Integer> persistedDeltas = persistedDeltas(start, predecessor);
        if (!start.adjustedBoundingBox().equals(aggregate(predecessor, persistedDeltas).box)) {
            throw new IllegalArgumentException("igloo adjusted start box conflict");
        }
        for (int index = 0; index < start.orderedPieces().size(); index++) {
            Mc263StructureCarrier.Piece current = start.orderedPieces().get(index);
            PieceFact fact = predecessor.orderedPieces().get(index);
            requirePieceShape(current);
            int persisted = persistedDeltas.get(index);
            if (!current.boundingBox().equals(box(fact.boundingBox(), persisted))
                    || !Arrays.equals(current.persistedPayload().binaryNbtCompound(),
                            Mc263IglooStartGenerator.canonicalPieceNbt(
                                    predecessor, index, persisted))) {
                throw new IllegalArgumentException(
                        "igloo persisted piece payload conflict at " + index);
            }
        }
        return new Binding(start.startKey());
    }

    /**
     * Derives each persisted piece's Y translation from its own persisted box and fails closed on
     * any translation the pinned generator could never emit, so an unbound box can never be
     * accepted merely because it is self-consistent.
     */
    private static List<Integer> persistedDeltas(Mc263StructureCarrier.ValidStart start,
            Mc263HardcodedStructureCarrier predecessor) {
        ArrayList<Integer> deltas = new ArrayList<>(start.orderedPieces().size());
        for (int index = 0; index < start.orderedPieces().size(); index++) {
            PieceFact fact = predecessor.orderedPieces().get(index);
            Mc263StructureCarrier.BoundingBox persisted =
                    start.orderedPieces().get(index).boundingBox();
            int delta = Math.subtractExact(persisted.minY(), fact.boundingBox().minY());
            int templateY = Math.addExact(fact.templateY(), delta);
            if (templateY < Blocks.MIN_Y || templateY > Blocks.MAX_Y
                    || persisted.minY() < Blocks.MIN_Y || persisted.maxY() > Blocks.MAX_Y) {
                throw new IllegalArgumentException(
                        "igloo persisted piece Y translation outside the world at " + index);
            }
            deltas.add(delta);
        }
        return List.copyOf(deltas);
    }

    private static Mc263FinalChunkCodec.FinalChunk apply(
            Mc263FinalChunkCodec.FinalChunk source, MutationEvidence evidence,
            List<Mc263FinalChunkSidecars.StructureEntity> entities) {
        Mc263FinalChunkSidecars old = source.sidecars();
        ArrayList<Mc263FinalChunkSidecars.BlockEntity> blockEntities = new ArrayList<>();
        for (BlockEntityEvidence value : evidence.blockEntities) {
            int packed = packed(source, value.mutation.position());
            String semantic = value.mutation.value();
            if (!source.stateAt(packed).blockKey().equals(
                    blockIdentityForSemantic(semantic))) {
                throw new IllegalArgumentException(
                        "igloo block-entity settled block evidence mismatch");
            }
            blockEntities.add(new Mc263FinalChunkSidecars.BlockEntity(packed,
                    blockIdentityForSemantic(semantic), entityTypeForSemantic(semantic),
                    value.canonicalNbt));
        }
        ArrayList<Mc263FinalChunkSidecars.Loot> loot = new ArrayList<>();
        for (Mutation value : evidence.loot) {
            int packed = packed(source, value.position());
            Mc263FeatureBlockState state = source.stateAt(packed);
            if (!state.blockKey().equals("minecraft:chest")) {
                throw new IllegalArgumentException("igloo LOOT settled chest evidence mismatch");
            }
            loot.add(new Mc263FinalChunkSidecars.Loot(packed, state.chestFacing(),
                    value.value(), value.seed()));
        }

        List<Mc263FinalChunkSidecars.BlockEntity> mergedBlockEntities = mergeBlockEntities(
                old.blockEntities(), blockEntities);
        List<Mc263FinalChunkSidecars.Loot> mergedLoot = mergeLoot(old.loot(), loot);
        List<Mc263FinalChunkSidecars.StructureEntity> mergedEntities = mergeEntities(
                old.entities(), entities);
        Mc263FinalChunkSidecars sidecars = new Mc263FinalChunkSidecars(old.blockTicks(),
                old.fluidTicks(), mergedLoot, old.spawners(), old.owners(), old.archaeology(),
                old.bees(), mergedBlockEntities, mergedEntities,
                old.containerLootDeclarations());
        Mc263FinalChunkCodec.FinalChunk result = new Mc263FinalChunkCodec.FinalChunk(
                source.chunkX(), source.chunkZ(), source.blockIds(), source.stateOverrides(),
                source.worldSurfaceWg(), source.oceanFloorWg(), source.motionBlocking(), sidecars);
        Mc263FinalChunkCodec.encode(result);
        return result;
    }

    private static void requireAuthenticatedLootRows(
            Mc263FinalChunkCodec.FinalChunk source, List<Mutation> lootEvidence) {
        for (Mutation value : lootEvidence) {
            int packed = packed(source, value.position());
            Mc263FeatureBlockState state = source.stateAt(packed);
            if (!state.blockKey().equals("minecraft:chest")) {
                throw new IllegalArgumentException(
                        "igloo LOOT settled chest evidence mismatch");
            }
            Mc263FinalChunkSidecars.Loot row = new Mc263FinalChunkSidecars.Loot(
                    packed, state.chestFacing(), value.value(), value.seed());
            if (!source.sidecars().loot().contains(row)) {
                throw new IllegalStateException(
                        "igloo LDEC upstream handoff missing: Mc263FinalChunkAssembler "
                                + "must provide the authenticated LOOT row and declaration "
                                + "before settlement");
            }
        }
    }

    private static List<Mc263FinalChunkSidecars.BlockEntity> mergeBlockEntities(
            List<Mc263FinalChunkSidecars.BlockEntity> source,
            List<Mc263FinalChunkSidecars.BlockEntity> additions) {
        List<Mc263FinalChunkSidecars.BlockEntity> orderedSource = source.stream()
                .sorted(Comparator.comparingInt(Mc263FinalChunkSidecars.BlockEntity::packed))
                .toList();
        List<Mc263FinalChunkSidecars.BlockEntity> orderedAdditions = additions.stream()
                .sorted(Comparator.comparingInt(Mc263FinalChunkSidecars.BlockEntity::packed))
                .toList();
        return mergeLane(orderedSource, orderedAdditions,
                (left, right) -> left.packed() == right.packed(), "block entity");
    }

    private static List<Mc263FinalChunkSidecars.Loot> mergeLoot(
            List<Mc263FinalChunkSidecars.Loot> source,
            List<Mc263FinalChunkSidecars.Loot> additions) {
        List<Mc263FinalChunkSidecars.Loot> orderedSource = source.stream()
                .sorted(Comparator.comparingInt(Mc263FinalChunkSidecars.Loot::packed)).toList();
        List<Mc263FinalChunkSidecars.Loot> orderedAdditions = additions.stream()
                .sorted(Comparator.comparingInt(Mc263FinalChunkSidecars.Loot::packed)).toList();
        return mergeLane(orderedSource, orderedAdditions,
                (left, right) -> left.packed() == right.packed(), "LOOT");
    }

    private static List<Mc263FinalChunkSidecars.StructureEntity> mergeEntities(
            List<Mc263FinalChunkSidecars.StructureEntity> source,
            List<Mc263FinalChunkSidecars.StructureEntity> additions) {
        return mergeLane(source, additions, Mc263IglooCanonicalExecutor::sameEntityIdentity,
                "entity");
    }

    private static <T> List<T> mergeLane(List<T> source, List<T> additions,
            Identity<T> identity, String lane) {
        List<T> existing = List.copyOf(source);
        if (additions.isEmpty()) return existing;
        ArrayList<T> matched = new ArrayList<>();
        for (T addition : additions) {
            T hit = existing.stream().filter(value -> identity.same(value, addition))
                    .findFirst().orElse(null);
            if (hit != null) {
                if (!hit.equals(addition)) {
                    throw new IllegalArgumentException(
                            "igloo " + lane + " conflicts with existing sidecar");
                }
                matched.add(hit);
            }
        }
        if (!matched.isEmpty() && matched.size() != additions.size()) {
            throw new IllegalArgumentException("partial igloo " + lane + " settlement");
        }
        if (!matched.isEmpty()) {
            int cursor = 0;
            for (T value : existing) {
                if (cursor < additions.size() && identity.same(value, additions.get(cursor))) {
                    if (!value.equals(additions.get(cursor))) {
                        throw new IllegalArgumentException(
                                "igloo " + lane + " payload conflict");
                    }
                    cursor++;
                }
            }
            if (cursor != additions.size()) {
                throw new IllegalArgumentException("igloo " + lane + " order conflict");
            }
            return existing;
        }
        ArrayList<T> merged = new ArrayList<>(existing);
        merged.addAll(additions);
        return List.copyOf(merged);
    }

    private static boolean sameEntityIdentity(Mc263FinalChunkSidecars.StructureEntity left,
            Mc263FinalChunkSidecars.StructureEntity right) {
        return left.entityKey().equals(right.entityKey())
                && left.spawnReason().equals(right.spawnReason())
                && Double.doubleToRawLongBits(left.x()) == Double.doubleToRawLongBits(right.x())
                && Double.doubleToRawLongBits(left.y()) == Double.doubleToRawLongBits(right.y())
                && Double.doubleToRawLongBits(left.z()) == Double.doubleToRawLongBits(right.z());
    }

    private static Mc263StructureCarrier rebuild(Mc263StructureCarrier carrier,
            String selectedStart, Mc263HardcodedStructureCarrier predecessor,
            List<Integer> yDeltas) {
        BoundingAggregate aggregate = aggregate(predecessor, yDeltas);
        ArrayList<Mc263StructureCarrier.ChunkStarts> chunks = new ArrayList<>();
        for (Mc263StructureCarrier.ChunkStarts chunk : carrier.startChunks()) {
            ArrayList<Mc263StructureCarrier.StartEntry> starts = new ArrayList<>();
            for (Mc263StructureCarrier.StartEntry entry : chunk.orderedStarts()) {
                if (!(entry.body() instanceof Mc263StructureCarrier.ValidStart start)
                        || !start.startKey().equals(selectedStart)) {
                    starts.add(entry);
                    continue;
                }
                ArrayList<Mc263StructureCarrier.Piece> pieces = new ArrayList<>();
                for (int index = 0; index < predecessor.orderedPieces().size(); index++) {
                    PieceFact fact = predecessor.orderedPieces().get(index);
                    int yDelta = yDeltas.get(index);
                    pieces.add(new Mc263StructureCarrier.Piece(
                            Mc263IglooStartGenerator.PIECE_TYPE,
                            box(fact.boundingBox(), yDelta), false,
                            Mc263StructureCarrier.Projection.NOT_APPLICABLE, 0, List.of(),
                            new Mc263StructureCarrier.PiecePayload(
                                    Mc263IglooStartGenerator.canonicalPieceNbt(
                                            predecessor, index, yDelta))));
                }
                starts.add(new Mc263StructureCarrier.StartEntry(entry.structureId(),
                        new Mc263StructureCarrier.ValidStart(start.startKey(),
                                start.originChunkX(), start.originChunkZ(), start.references(),
                                aggregate.box, pieces)));
            }
            chunks.add(chunk.withStarts(starts));
        }
        return new Mc263StructureCarrier(carrier.registry(), chunks, carrier.referenceChunks(),
                carrier.rawStartPayloads(), carrier.producerGraphPayloads());
    }

    private static BoundingAggregate aggregate(Mc263HardcodedStructureCarrier predecessor,
            List<Integer> yDeltas) {
        Mc263StructureCarrier.BoundingBox aggregate = null;
        for (int index = 0; index < predecessor.orderedPieces().size(); index++) {
            Mc263StructureCarrier.BoundingBox next = box(
                    predecessor.orderedPieces().get(index).boundingBox(), yDeltas.get(index));
            aggregate = aggregate == null ? next : new Mc263StructureCarrier.BoundingBox(
                    Math.min(aggregate.minX(), next.minX()),
                    Math.min(aggregate.minY(), next.minY()),
                    Math.min(aggregate.minZ(), next.minZ()),
                    Math.max(aggregate.maxX(), next.maxX()),
                    Math.max(aggregate.maxY(), next.maxY()),
                    Math.max(aggregate.maxZ(), next.maxZ()));
        }
        return new BoundingAggregate(aggregate);
    }

    private static List<Mc263FinalChunkSidecars.StructureEntity> expectedEntities(
            Mc263HardcodedStructureCarrier predecessor, List<Integer> yDeltas,
            Mc263IglooStructureExecutor.Clip clip, int targetChunkX, int targetChunkZ) {
        int bottom = -1;
        for (int index = 0; index < predecessor.orderedPieces().size(); index++) {
            if (predecessor.orderedPieces().get(index).template() == Template.IGLOO_BOTTOM) {
                bottom = index;
                break;
            }
        }
        if (bottom < 0) return List.of();
        PieceFact piece = predecessor.orderedPieces().get(bottom);
        double y = piece.templateY() + 1.0D + yDeltas.get(bottom);
        ArrayList<Mc263FinalChunkSidecars.StructureEntity> result = new ArrayList<>(2);
        addEntity(result, predecessor.rotation(), piece, clip, targetChunkX, targetChunkZ,
                "minecraft:villager", 2, 1, 2.5D, 1.5D, y, -4.3276978F, -3.41655F,
                VILLAGER_PAYLOAD);
        addEntity(result, predecessor.rotation(), piece, clip, targetChunkX, targetChunkZ,
                "minecraft:zombie_villager", 4, 1, 4.5D, 1.300000011920929D, y,
                -95.78888F, 0.0F, ZOMBIE_VILLAGER_PAYLOAD);
        return List.copyOf(result);
    }

    private static void addEntity(ArrayList<Mc263FinalChunkSidecars.StructureEntity> target,
            Rotation rotation, PieceFact piece, Mc263IglooStructureExecutor.Clip clip,
            int targetChunkX, int targetChunkZ, String entityKey,
            int localBlockX, int localBlockZ, double localX, double localZ, double y,
            float yaw, float pitch, byte[] payload) {
        BlockPos transformedBlock = transformBlock(localBlockX, localBlockZ, rotation, 3, 7);
        BlockPos clipPosition = new BlockPos(piece.templateX() + transformedBlock.x(),
                (int) y, piece.templateZ() + transformedBlock.z());
        if (!clip.contains(clipPosition)) return;
        DoublePos transformed = transform(localX, localZ, rotation, 3.0D, 7.0D);
        double x = piece.templateX() + transformed.x;
        double z = piece.templateZ() + transformed.z;
        if (Math.floorDiv((long) Math.floor(x), 16L) != targetChunkX
                || Math.floorDiv((long) Math.floor(z), 16L) != targetChunkZ) return;
        target.add(new Mc263FinalChunkSidecars.StructureEntity(entityKey,
                "minecraft:structure", x, y, z, rotateYaw(yaw, rotation), pitch,
                0.0D, -0.0784000015258789D, 0.0D, payload));
    }

    private static BlockPos transformBlock(int x, int z, Rotation rotation,
            int pivotX, int pivotZ) {
        return switch (rotation) {
            case NONE -> new BlockPos(x, 0, z);
            case CLOCKWISE_180 -> new BlockPos(2 * pivotX - x, 0, 2 * pivotZ - z);
            case COUNTERCLOCKWISE_90 -> new BlockPos(
                    pivotX - pivotZ + z, 0, pivotX + pivotZ - x);
            case CLOCKWISE_90 -> new BlockPos(
                    pivotX + pivotZ - z, 0, pivotZ - pivotX + x);
            default -> throw new IllegalArgumentException("igloo requires quarter rotation");
        };
    }

    private static DoublePos transform(double x, double z, Rotation rotation,
            double pivotX, double pivotZ) {
        return switch (rotation) {
            case NONE -> new DoublePos(x, z);
            case CLOCKWISE_180 -> new DoublePos(2.0D * pivotX - x, 2.0D * pivotZ - z);
            case COUNTERCLOCKWISE_90 -> new DoublePos(
                    pivotX - pivotZ + z, pivotX + pivotZ - x);
            case CLOCKWISE_90 -> new DoublePos(
                    pivotX + pivotZ - z, pivotZ - pivotX + x);
            default -> throw new IllegalArgumentException("igloo requires quarter rotation");
        };
    }

    private static float rotateYaw(float yaw, Rotation rotation) {
        float result = yaw + switch (rotation) {
            case NONE -> 0.0F;
            case CLOCKWISE_90 -> 90.0F;
            case CLOCKWISE_180 -> 180.0F;
            case COUNTERCLOCKWISE_90 -> -90.0F;
            default -> throw new IllegalArgumentException("igloo requires quarter rotation");
        };
        while (result >= 180.0F) result -= 360.0F;
        while (result < -180.0F) result += 360.0F;
        return result;
    }

    private static byte[] canonicalBlockEntity(Mutation mutation) {
        String semantic = mutation.value();
        String type = entityTypeForSemantic(semantic);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(96);
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(10); output.writeShort(0);
                output.writeByte(8); output.writeUTF("id"); output.writeUTF(type);
                output.writeByte(8); output.writeUTF("webcraft:semantic");
                output.writeUTF(semantic);
                if (mutation.hasRandomSeed()) {
                    output.writeByte(4); output.writeUTF("RandomSeed");
                    output.writeLong(mutation.seed());
                }
                output.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory igloo block-entity NBT failed", exception);
        }
    }

    private static String blockIdentityForSemantic(String semantic) {
        if (semantic.equals(Mc263IglooStructureExecutor.EMPTY_FURNACE)) {
            return "minecraft:furnace";
        }
        if (semantic.equals(Mc263IglooStructureExecutor.EMPTY_CHEST)) {
            return "minecraft:chest";
        }
        if (semantic.equals(Mc263IglooStructureExecutor.DIRECTION_SIGN)) {
            return "minecraft:oak_wall_sign";
        }
        if (semantic.equals(Mc263IglooStructureExecutor.WEAKNESS_BREWING_STAND)) {
            return "minecraft:brewing_stand";
        }
        throw new IllegalArgumentException("unknown igloo block-entity semantic");
    }

    private static String entityTypeForSemantic(String semantic) {
        if (semantic.equals(Mc263IglooStructureExecutor.EMPTY_FURNACE)) {
            return "minecraft:furnace";
        }
        if (semantic.equals(Mc263IglooStructureExecutor.EMPTY_CHEST)) {
            return "minecraft:chest";
        }
        if (semantic.equals(Mc263IglooStructureExecutor.DIRECTION_SIGN)) {
            return "minecraft:sign";
        }
        if (semantic.equals(Mc263IglooStructureExecutor.WEAKNESS_BREWING_STAND)) {
            return "minecraft:brewing_stand";
        }
        throw new IllegalArgumentException("unknown igloo block-entity semantic");
    }

    private static String blockIdentity(String exactState) {
        int property = exactState.indexOf('[');
        return property < 0 ? exactState : exactState.substring(0, property);
    }

    private static void requireExactStateSyntax(String exactState) {
        Objects.requireNonNull(exactState, "igloo exact state");
        String key = blockIdentity(exactState);
        if (!key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || exactState.indexOf('[') >= 0 && !exactState.endsWith("]")) {
            throw new IllegalArgumentException("invalid igloo exact state: " + exactState);
        }
    }

    private static void requireCarrier(Mc263HardcodedStructureCarrier carrier) {
        Objects.requireNonNull(carrier, "igloo hardcoded carrier");
        if (carrier.kind() != Kind.IGLOO || carrier.orderedPieces().isEmpty()) {
            throw new IllegalArgumentException("noncanonical igloo carrier shape");
        }
        for (PieceFact piece : carrier.orderedPieces()) {
            if (piece.kind() != PieceKind.IGLOO_TEMPLATE || piece.template() == null) {
                throw new IllegalArgumentException("noncanonical igloo piece shape");
            }
        }
        Mc263HardcodedStructureCarrier canonical = Mc263HardcodedStructureCarrier.plan(
                Kind.IGLOO, carrier.worldSeed(), carrier.chunkX(), carrier.chunkZ());
        if (!Arrays.equals(carrier.encodeCanonical(), canonical.encodeCanonical())) {
            throw new IllegalArgumentException("noncanonical igloo carrier facts");
        }
    }

    private static void requirePieceShape(Mc263StructureCarrier.Piece piece) {
        if (!piece.pieceType().equals(Mc263IglooStartGenerator.PIECE_TYPE)
                || piece.poolElement()
                || piece.projection() != Mc263StructureCarrier.Projection.NOT_APPLICABLE
                || piece.groundLevelDelta() != 0 || !piece.junctions().isEmpty()) {
            throw new IllegalArgumentException("expected exact minecraft:iglu piece");
        }
    }

    private static Mc263StructureCarrier.BoundingBox box(
            Mc263HardcodedStructureCarrier.BoundingBox source, int yDelta) {
        return new Mc263StructureCarrier.BoundingBox(source.minX(),
                Math.addExact(source.minY(), yDelta), source.minZ(), source.maxX(),
                Math.addExact(source.maxY(), yDelta), source.maxZ());
    }

    private static void requireInChunk(Mc263FinalChunkCodec.FinalChunk chunk,
            BlockPos position) {
        packed(chunk, position);
    }

    private static int packed(Mc263FinalChunkCodec.FinalChunk chunk, BlockPos position) {
        if (Math.floorDiv(position.x(), 16) != chunk.chunkX()
                || Math.floorDiv(position.z(), 16) != chunk.chunkZ()
                || position.y() < Blocks.MIN_Y || position.y() > Blocks.MAX_Y) {
            throw new IllegalArgumentException("igloo mutation outside target chunk: "
                    + position.x() + "," + position.y() + "," + position.z()
                    + " target=" + chunk.chunkX() + "," + chunk.chunkZ());
        }
        return Blocks.blockIndex(Math.floorMod(position.x(), 16), position.y(),
                Math.floorMod(position.z(), 16));
    }

    private interface Identity<T> {
        boolean same(T left, T right);
    }

    private static final class Binding {
        private final String startKey;
        private Binding(String startKey) { this.startKey = startKey; }
    }

    private static final class MutationEvidence {
        private final List<Mutation> blocks;
        private final List<BlockEntityEvidence> blockEntities;
        private final List<Mutation> loot;
        private MutationEvidence(List<Mutation> blocks,
                List<BlockEntityEvidence> blockEntities, List<Mutation> loot) {
            this.blocks = blocks; this.blockEntities = blockEntities; this.loot = loot;
        }
    }

    private static final class BlockEntityEvidence {
        private final Mutation mutation;
        private final byte[] canonicalNbt;
        private BlockEntityEvidence(Mutation mutation, byte[] canonicalNbt) {
            this.mutation = mutation; this.canonicalNbt = canonicalNbt.clone();
        }
    }

    private static final class BoundingAggregate {
        private final Mc263StructureCarrier.BoundingBox box;
        private BoundingAggregate(Mc263StructureCarrier.BoundingBox box) {
            this.box = Objects.requireNonNull(box, "igloo aggregate box");
        }
    }

    private static final class DoublePos {
        private final double x;
        private final double z;
        private DoublePos(double x, double z) { this.x = x; this.z = z; }
    }
}
